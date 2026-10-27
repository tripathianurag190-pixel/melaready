#!/usr/bin/env python3
"""MelaReady v0.3 demo API. Standard library only; run on a trusted test LAN."""
import argparse, base64, hashlib, hmac, json, os, re, secrets, sqlite3, time, uuid
from collections import defaultdict, deque
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from threading import Lock
from urllib.parse import parse_qs, urlparse

ROOT=Path(__file__).resolve().parent
DB=ROOT/"melaready_demo.sqlite3"
MAX_BODY=420_000
RATE=defaultdict(deque);RATE_LOCK=Lock()

def now(): return int(time.time())
def uid(): return str(uuid.uuid4())
def hash_token(v): return hashlib.sha256(v.encode()).hexdigest()
def password_hash(password, salt=None):
    salt=salt or secrets.token_bytes(16)
    digest=hashlib.pbkdf2_hmac("sha256",password.encode(),salt,600_000)
    return salt.hex()+":"+digest.hex()
def password_ok(password, saved):
    try:
        salt,digest=saved.split(":",1)
        return hmac.compare_digest(password_hash(password,bytes.fromhex(salt)).split(":",1)[1],digest)
    except Exception:return False
def invite_code(): return secrets.token_hex(3).upper()
def allowed(key,limit,window=60):
    cutoff=time.monotonic()-window
    with RATE_LOCK:
        q=RATE[key]
        while q and q[0]<cutoff:q.popleft()
        if len(q)>=limit:return False
        q.append(time.monotonic());return True
def connect():
    c=sqlite3.connect(DB,timeout=10)
    c.row_factory=sqlite3.Row;c.execute("PRAGMA foreign_keys=ON");return c
def init():
    with connect() as c:
        c.executescript("""
        CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY,email TEXT UNIQUE NOT NULL,password TEXT NOT NULL,name TEXT NOT NULL,phone TEXT NOT NULL DEFAULT '',contact_visible INTEGER NOT NULL DEFAULT 0,photo TEXT NOT NULL DEFAULT '');
        CREATE TABLE IF NOT EXISTS sessions(token_hash TEXT PRIMARY KEY,user_id TEXT NOT NULL,expires INTEGER NOT NULL,FOREIGN KEY(user_id) REFERENCES users(id));
        CREATE TABLE IF NOT EXISTS events(id TEXT PRIMARY KEY,name TEXT NOT NULL,venue TEXT NOT NULL,invite TEXT UNIQUE NOT NULL,invite_expires INTEGER NOT NULL,lead_id TEXT NOT NULL,created INTEGER NOT NULL,FOREIGN KEY(lead_id) REFERENCES users(id));
        CREATE TABLE IF NOT EXISTS members(event_id TEXT NOT NULL,user_id TEXT NOT NULL,role TEXT NOT NULL,zone TEXT NOT NULL,joined INTEGER NOT NULL,PRIMARY KEY(event_id,user_id),FOREIGN KEY(event_id) REFERENCES events(id),FOREIGN KEY(user_id) REFERENCES users(id));
        CREATE TABLE IF NOT EXISTS tasks(id TEXT PRIMARY KEY,event_id TEXT NOT NULL,title TEXT NOT NULL,assignee TEXT NOT NULL,status TEXT NOT NULL,version INTEGER NOT NULL,client_id TEXT NOT NULL,created INTEGER NOT NULL,UNIQUE(event_id,client_id));
        CREATE TABLE IF NOT EXISTS reports(id TEXT PRIMARY KEY,event_id TEXT NOT NULL,details TEXT NOT NULL,zone TEXT NOT NULL,assignee TEXT,status TEXT NOT NULL,version INTEGER NOT NULL,client_id TEXT NOT NULL,created INTEGER NOT NULL,UNIQUE(event_id,client_id));
        CREATE TABLE IF NOT EXISTS locations(event_id TEXT NOT NULL,user_id TEXT NOT NULL,lat REAL NOT NULL,lng REAL NOT NULL,accuracy REAL NOT NULL,captured INTEGER NOT NULL,expires INTEGER NOT NULL,PRIMARY KEY(event_id,user_id));
        CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY AUTOINCREMENT,event_id TEXT,actor TEXT NOT NULL,action TEXT NOT NULL,created INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS voice(id TEXT PRIMARY KEY,event_id TEXT NOT NULL,user_id TEXT NOT NULL,mime TEXT NOT NULL,audio BLOB NOT NULL,created INTEGER NOT NULL);
        """)
        user_columns={r[1] for r in c.execute("PRAGMA table_info(users)")}
        if "photo" not in user_columns:c.execute("ALTER TABLE users ADD COLUMN photo TEXT NOT NULL DEFAULT ''")
        event_columns={r[1] for r in c.execute("PRAGMA table_info(events)")}
        if "invite_expires" not in event_columns:
            c.execute("ALTER TABLE events ADD COLUMN invite_expires INTEGER NOT NULL DEFAULT 0")
            c.execute("UPDATE events SET invite_expires=? WHERE invite_expires=0",(now()+86_400,))

class ApiError(Exception):
    def __init__(self,status,message):self.status=status;self.message=message
def clean(v,minlen,maxlen,label):
    v=str(v or "").strip()
    if not minlen<=len(v)<=maxlen:raise ApiError(400,f"Check {label}")
    return v
def member(c,event,user):
    row=c.execute("SELECT m.*,e.lead_id FROM members m JOIN events e ON e.id=m.event_id WHERE m.event_id=? AND m.user_id=?",(event,user)).fetchone()
    if not row:raise ApiError(403,"Not a member of this event")
    return row
def audit(c,event,user,action):c.execute("INSERT INTO audit(event_id,actor,action,created) VALUES(?,?,?,?)",(event,user,action,now()))

class Handler(BaseHTTPRequestHandler):
    server_version="MelaReadyDemo/0.4.0"
    def log_message(self,fmt,*args): print("%s - %s"%(self.address_string(),fmt%args))
    def send_json(self,status,payload):
        raw=json.dumps(payload,separators=(",",":"),ensure_ascii=False).encode()
        self.send_response(status);self.send_header("Content-Type","application/json; charset=utf-8");self.send_header("Cache-Control","no-store");self.send_header("X-Content-Type-Options","nosniff");self.send_header("Content-Length",str(len(raw)));self.end_headers();self.wfile.write(raw)
    def read_json(self):
        try:n=int(self.headers.get("Content-Length","0"))
        except ValueError:raise ApiError(400,"Invalid body")
        if n<0 or n>MAX_BODY:raise ApiError(413,"Body too large")
        try:return json.loads(self.rfile.read(n) or b"{}")
        except Exception:raise ApiError(400,"Invalid JSON")
    def auth(self,c):
        value=self.headers.get("Authorization","")
        if not value.startswith("Bearer "):raise ApiError(401,"Sign in required")
        row=c.execute("SELECT u.* FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires>?",(hash_token(value[7:]),now())).fetchone()
        if not row:raise ApiError(401,"Session expired")
        return row
    def do_GET(self):
        p=urlparse(self.path)
        try:
            if p.path=="/":
                raw=(ROOT/"web"/"index.html").read_bytes();self.send_response(200);self.send_header("Content-Type","text/html; charset=utf-8");self.send_header("Cache-Control","no-store");self.send_header("X-Frame-Options","SAMEORIGIN");self.send_header("Referrer-Policy","no-referrer");self.send_header("Content-Length",str(len(raw)));self.end_headers();self.wfile.write(raw);return
            if p.path in ("/health", "/api/health"):
                return self.send_json(200,{"ok":True,"service":"MelaReady","version":"0.4.0"})
            if p.path=="/api/voice/file":
                voice_id=parse_qs(p.query).get("id",[""])[0]
                with connect() as c:
                    u=self.auth(c);row=c.execute("SELECT * FROM voice WHERE id=?",(voice_id,)).fetchone()
                    if not row:raise ApiError(404,"Voice clip not found")
                    member(c,row["event_id"],u["id"]);raw=bytes(row["audio"])
                    self.send_response(200);self.send_header("Content-Type",row["mime"]);self.send_header("Cache-Control","no-store");self.send_header("Content-Length",str(len(raw)));self.end_headers();self.wfile.write(raw);return
            if p.path=="/api/events":
                with connect() as c:
                    u=self.auth(c);rows=c.execute("SELECT e.id,e.name,e.venue,e.invite,e.lead_id,m.role,m.zone FROM events e JOIN members m ON m.event_id=e.id WHERE m.user_id=? ORDER BY e.created DESC",(u["id"],)).fetchall()
                    return self.send_json(200,{"events":[dict(r) for r in rows],"user":{"id":u["id"],"name":u["name"],"email":u["email"]}})
            if p.path=="/api/snapshot":
                event=parse_qs(p.query).get("event",[""])[0]
                with connect() as c:
                    u=self.auth(c);member(c,event,u["id"]);c.execute("DELETE FROM locations WHERE expires<=?",(now(),));c.execute("DELETE FROM voice WHERE created<?",(now()-86_400,))
                    e=c.execute("SELECT id,name,venue,invite,invite_expires,lead_id FROM events WHERE id=?",(event,)).fetchone()
                    people=c.execute("SELECT u.id,u.name,u.photo,u.contact_visible,CASE WHEN u.contact_visible=1 OR u.id=? THEN u.phone ELSE '' END phone,m.role,m.zone FROM members m JOIN users u ON u.id=m.user_id WHERE m.event_id=? ORDER BY m.joined",(u["id"],event)).fetchall()
                    tasks=c.execute("SELECT * FROM tasks WHERE event_id=? ORDER BY created DESC",(event,)).fetchall()
                    reports=c.execute("SELECT * FROM reports WHERE event_id=? ORDER BY created DESC",(event,)).fetchall()
                    locs=c.execute("SELECT l.*,u.name FROM locations l JOIN users u ON u.id=l.user_id WHERE event_id=?",(event,)).fetchall()
                    voice=c.execute("SELECT v.id,v.user_id,v.mime,v.created,u.name FROM voice v JOIN users u ON u.id=v.user_id WHERE event_id=? ORDER BY created DESC LIMIT 20",(event,)).fetchall()
                    return self.send_json(200,{"event":dict(e),"me":u["id"],"members":[dict(r) for r in people],"tasks":[dict(r) for r in tasks],"reports":[dict(r) for r in reports],"locations":[dict(r) for r in locs],"voice":[dict(r) for r in voice],"server_time":now()})
            raise ApiError(404,"Not found")
        except ApiError as e:self.send_json(e.status,{"error":e.message})
        except Exception as e:print(type(e).__name__,e);self.send_json(500,{"error":"Server error"})
    def do_POST(self):
        p=urlparse(self.path).path
        try:
            if not allowed("ip:"+self.client_address[0],180):raise ApiError(429,"Too many requests. Wait a minute and retry.")
            data=self.read_json()
            with connect() as c:
                if p=="/api/register":
                    if not allowed("auth:"+self.client_address[0],20):raise ApiError(429,"Too many sign-in attempts")
                    email=clean(data.get("email"),5,120,"email").lower();name=clean(data.get("name"),2,60,"name");pw=clean(data.get("password"),8,128,"password")
                    if not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+",email):raise ApiError(400,"Check email")
                    try:c.execute("INSERT INTO users(id,email,password,name,phone,contact_visible,photo) VALUES(?,?,?,?,?,?,?)",(uid(),email,password_hash(pw),name,"",0,""))
                    except sqlite3.IntegrityError:raise ApiError(409,"Account already exists")
                    return self.login(c,email,pw)
                if p=="/api/login":
                    if not allowed("auth:"+self.client_address[0],20):raise ApiError(429,"Too many sign-in attempts")
                    return self.login(c,clean(data.get("email"),5,120,"email").lower(),clean(data.get("password"),8,128,"password"))
                u=self.auth(c)
                if p=="/api/logout":
                    c.execute("DELETE FROM sessions WHERE token_hash=?",(hash_token(self.headers.get("Authorization","")[7:]),));return self.send_json(200,{"ok":True})
                if p=="/api/event/create":
                    name=clean(data.get("name"),2,80,"event name");venue=clean(data.get("venue"),2,120,"venue");eid=uid();code=invite_code()
                    expiry=now()+86_400;c.execute("INSERT INTO events(id,name,venue,invite,invite_expires,lead_id,created) VALUES(?,?,?,?,?,?,?)",(eid,name,venue,code,expiry,u["id"],now()));c.execute("INSERT INTO members VALUES(?,?,?,?,?)",(eid,u["id"],"Team lead","Control",now()));audit(c,eid,u["id"],"event.created")
                    return self.send_json(201,{"id":eid,"invite":code})
                if p=="/api/event/join":
                    code=clean(data.get("invite"),6,6,"invite").upper();e=c.execute("SELECT id,invite_expires FROM events WHERE invite=?",(code,)).fetchone()
                    if not e:raise ApiError(404,"Invite not found")
                    if e["invite_expires"]<=now():raise ApiError(410,"Invite expired. Ask the lead for a new event invite.")
                    count=c.execute("SELECT COUNT(*) FROM members WHERE event_id=?",(e["id"],)).fetchone()[0]
                    if count>=250:raise ApiError(409,"Pilot member limit reached")
                    c.execute("INSERT OR IGNORE INTO members VALUES(?,?,?,?,?)",(e["id"],u["id"],"Volunteer","Unassigned",now()));audit(c,e["id"],u["id"],"member.joined")
                    return self.send_json(200,{"id":e["id"]})
                event=clean(data.get("event"),10,60,"event");m=member(c,event,u["id"]);lead=m["lead_id"]==u["id"]
                if p=="/api/event/invite/refresh":
                    if not lead:raise ApiError(403,"Lead only")
                    code=invite_code();expiry=now()+86_400;c.execute("UPDATE events SET invite=?,invite_expires=? WHERE id=?",(code,expiry,event));audit(c,event,u["id"],"invite.refreshed");return self.send_json(200,{"invite":code,"invite_expires":expiry})
                if p=="/api/profile":
                    phone=re.sub(r"[^0-9+]","",str(data.get("phone","") or ""))[:15];visible=1 if data.get("contact_visible") else 0
                    photo=str(data.get("photo","") or "")
                    if photo and (not photo.startswith("data:image/jpeg;base64,") or len(photo)>140_000):raise ApiError(400,"Use a JPEG profile photo under 100 KB")
                    c.execute("UPDATE users SET phone=?,contact_visible=?,photo=? WHERE id=?",(phone,visible,photo,u["id"]));return self.send_json(200,{"ok":True})
                if p=="/api/member/assign":
                    if not lead:raise ApiError(403,"Lead only")
                    target=clean(data.get("user"),10,60,"member");role=clean(data.get("role"),2,60,"role");zone=clean(data.get("zone"),1,60,"zone")
                    if not c.execute("SELECT 1 FROM members WHERE event_id=? AND user_id=?",(event,target)).fetchone():raise ApiError(404,"Member not found")
                    c.execute("UPDATE members SET role=?,zone=? WHERE event_id=? AND user_id=?",(role,zone,event,target));audit(c,event,u["id"],"member.assigned");return self.send_json(200,{"ok":True})
                if p=="/api/task/create":
                    if not lead:raise ApiError(403,"Lead only")
                    title=clean(data.get("title"),3,160,"task");assignee=clean(data.get("assignee"),10,60,"assignee");client=clean(data.get("client_id"),8,80,"client id")
                    if not c.execute("SELECT 1 FROM members WHERE event_id=? AND user_id=?",(event,assignee)).fetchone():raise ApiError(400,"Assignee is not a member")
                    c.execute("INSERT OR IGNORE INTO tasks VALUES(?,?,?,?,?,?,?,?)",(uid(),event,title,assignee,"OPEN",1,client,now()));audit(c,event,u["id"],"task.created");return self.send_json(201,{"ok":True})
                if p=="/api/task/advance":
                    row=c.execute("SELECT * FROM tasks WHERE id=? AND event_id=?",(data.get("id"),event)).fetchone()
                    if not row:raise ApiError(404,"Task not found")
                    if row["assignee"]!=u["id"] and not lead:raise ApiError(403,"Assignee or lead only")
                    nxt={"OPEN":"ACKNOWLEDGED","ACKNOWLEDGED":"DONE","DONE":"DONE"}[row["status"]]
                    c.execute("UPDATE tasks SET status=?,version=version+1 WHERE id=?",(nxt,row["id"]));audit(c,event,u["id"],"task."+nxt.lower());return self.send_json(200,{"status":nxt})
                if p=="/api/report/create":
                    details=clean(data.get("details"),5,240,"report");zone=clean(data.get("zone"),1,60,"zone");client=clean(data.get("client_id"),8,80,"client id")
                    c.execute("INSERT OR IGNORE INTO reports VALUES(?,?,?,?,?,?,?,?,?)",(uid(),event,details,zone,None,"SAVED",1,client,now()));audit(c,event,u["id"],"report.saved");return self.send_json(201,{"ok":True})
                if p=="/api/report/advance":
                    if not lead:raise ApiError(403,"Lead only")
                    row=c.execute("SELECT * FROM reports WHERE id=? AND event_id=?",(data.get("id"),event)).fetchone()
                    if not row:raise ApiError(404,"Report not found")
                    nxt={"SAVED":"ACKNOWLEDGED","ACKNOWLEDGED":"ASSIGNED","ASSIGNED":"RESOLVED","RESOLVED":"RESOLVED"}[row["status"]]
                    assignee=data.get("assignee") or row["assignee"]
                    if nxt=="ASSIGNED" and not c.execute("SELECT 1 FROM members WHERE event_id=? AND user_id=?",(event,assignee)).fetchone():raise ApiError(400,"Choose a joined member")
                    c.execute("UPDATE reports SET status=?,assignee=?,version=version+1 WHERE id=?",(nxt,assignee,row["id"]));audit(c,event,u["id"],"report."+nxt.lower());return self.send_json(200,{"status":nxt})
                if p=="/api/location/share":
                    lat=float(data.get("lat"));lng=float(data.get("lng"));acc=float(data.get("accuracy"));captured=int(data.get("captured",0))
                    if not(-90<=lat<=90 and -180<=lng<=180 and 0<=acc<=10_000):raise ApiError(400,"Invalid location")
                    if abs(now()-captured)>120:raise ApiError(400,"Refresh stale location")
                    c.execute("INSERT OR REPLACE INTO locations VALUES(?,?,?,?,?,?,?)",(event,u["id"],lat,lng,acc,captured,now()+300));audit(c,event,u["id"],"location.shared");return self.send_json(200,{"expires":now()+300})
                if p=="/api/location/remove":
                    c.execute("DELETE FROM locations WHERE event_id=? AND user_id=?",(event,u["id"]));audit(c,event,u["id"],"location.removed");return self.send_json(200,{"ok":True})
                if p=="/api/voice/upload":
                    mime=str(data.get("mime","")).split(";",1)[0]
                    if mime not in {"audio/webm","audio/ogg","audio/mp4"}:raise ApiError(400,"Unsupported audio format")
                    try:raw=base64.b64decode(str(data.get("audio","") or ""),validate=True)
                    except Exception:raise ApiError(400,"Invalid audio")
                    if not 100<=len(raw)<=250_000:raise ApiError(400,"Voice clip must be under 250 KB")
                    vid=uid();c.execute("INSERT INTO voice VALUES(?,?,?,?,?,?)",(vid,event,u["id"],mime,raw,now()));audit(c,event,u["id"],"voice.shared")
                    old=c.execute("SELECT id FROM voice WHERE event_id=? ORDER BY created DESC LIMIT -1 OFFSET 50",(event,)).fetchall()
                    if old:c.executemany("DELETE FROM voice WHERE id=?",[(r["id"],) for r in old])
                    return self.send_json(201,{"id":vid})
                raise ApiError(404,"Not found")
        except (ValueError,TypeError):self.send_json(400,{"error":"Check numeric values"})
        except ApiError as e:self.send_json(e.status,{"error":e.message})
        except Exception as e:print(type(e).__name__,e);self.send_json(500,{"error":"Server error"})
    def login(self,c,email,pw):
        u=c.execute("SELECT * FROM users WHERE email=?",(email,)).fetchone()
        if not u or not password_ok(pw,u["password"]):raise ApiError(401,"Email or password is incorrect")
        token=secrets.token_urlsafe(32);c.execute("DELETE FROM sessions WHERE expires<=?",(now(),));c.execute("INSERT INTO sessions VALUES(?,?,?)",(hash_token(token),u["id"],now()+43_200))
        self.send_json(200,{"token":token,"user":{"id":u["id"],"name":u["name"]}})

if __name__=="__main__":
    ap=argparse.ArgumentParser();ap.add_argument("--host",default="127.0.0.1");ap.add_argument("--port",type=int,default=8080);args=ap.parse_args();init()
    print(f"MelaReady demo server: http://{args.host}:{args.port}")
    ThreadingHTTPServer((args.host,args.port),Handler).serve_forever()
