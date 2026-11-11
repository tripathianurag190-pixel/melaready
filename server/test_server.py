import base64, json, re, sqlite3, tempfile, threading, unittest, urllib.error, urllib.request
from unittest.mock import patch
from pathlib import Path
import server
from http.server import ThreadingHTTPServer

class ApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory();server.DB=Path(cls.tmp.name)/"test.sqlite3";server.init()
        cls.http=ThreadingHTTPServer(("127.0.0.1",0),server.Handler);cls.port=cls.http.server_port
        threading.Thread(target=cls.http.serve_forever,daemon=True).start()
    @classmethod
    def tearDownClass(cls):cls.http.shutdown();cls.http.server_close();cls.tmp.cleanup()
    def call(self,path,data=None,token=None):
        headers={"Content-Type":"application/json"};
        if token:headers["Authorization"]="Bearer "+token
        req=urllib.request.Request(f"http://127.0.0.1:{self.port}{path}",data=json.dumps(data).encode() if data is not None else None,headers=headers)
        with urllib.request.urlopen(req) as r:return r.status,json.load(r)
    def register(self,email,name):return self.call("/api/register",{"email":email,"name":name,"password":"StrongPass9"})[1]["token"]
    def test_two_accounts_join_and_share_workflow(self):
        lead=self.register("lead@example.test","Lead");member=self.register("member@example.test","Member")
        _,created=self.call("/api/event/create",{"name":"Demo mela","venue":"Ambala"},lead)
        _,joined=self.call("/api/event/join",{"invite":created["invite"]},member);eid=joined["id"]
        _,snap=self.call("/api/snapshot?event="+eid,None,lead);self.assertEqual(2,len(snap["members"]))
        target=[m for m in snap["members"] if m["name"]=="Member"][0]["id"]
        self.call("/api/task/create",{"event":eid,"title":"Check help point","assignee":target,"client_id":"client-task-001"},lead)
        _,snap=self.call("/api/snapshot?event="+eid,None,member);self.assertEqual("OPEN",snap["tasks"][0]["status"])
        self.call("/api/task/advance",{"event":eid,"id":snap["tasks"][0]["id"]},member)
        _,snap=self.call("/api/snapshot?event="+eid,None,lead);self.assertEqual("ACKNOWLEDGED",snap["tasks"][0]["status"])

    def error(self,path,data=None,token=None):
        with self.assertRaises(urllib.error.HTTPError) as cm:
            self.call(path,data,token)
        return cm.exception.code,json.load(cm.exception)

    def test_invite_format_uniqueness_and_persistence(self):
        lead=self.register("format@example.test","Format Lead")
        codes=set()
        for i in range(60):
            status,e=self.call("/api/event/create",{"name":f"Event {i}","venue":"Ambala"},lead)
            self.assertEqual(status,201)
            self.assertRegex(e["invite"],r"^[A-Z]{3}[0-9]{3}$")
            self.assertNotIn(e["invite"],codes)
            codes.add(e["invite"])
            with server.connect() as db:
                saved=db.execute("SELECT name,venue,invite FROM events WHERE id=?",(e["id"],)).fetchone()
                self.assertIsNotNone(saved)
                self.assertEqual(saved["invite"],e["invite"])
        _,listed=self.call("/api/events",token=lead)
        self.assertEqual(len(listed["events"]),60)

    def test_retries_code_collision_instead_of_reusing_existing_code(self):
        lead=self.register("retry@example.test","Retry Lead")
        with patch.object(server,"invite_code",side_effect=["ABC123","ABC123","DEF456"]):
            _,first=self.call("/api/event/create",{"name":"First","venue":"Ambala"},lead)
            _,second=self.call("/api/event/create",{"name":"Second","venue":"Delhi"},lead)
        self.assertEqual(first["invite"],"ABC123")
        self.assertEqual(second["invite"],"DEF456")

    def test_join_rejects_invalid_unknown_and_requires_auth(self):
        lead=self.register("checklead@example.test","Check Lead")
        member=self.register("checkmember@example.test","Check Member")
        _,e=self.call("/api/event/create",{"name":"Check event","venue":"Ambala"},lead)
        self.assertEqual(self.error("/api/event/join",{"invite":"WRONG9"},member)[0],404)
        self.assertEqual(self.error("/api/event/join",{"invite":"ABC-12"},member)[0],400)
        self.assertEqual(self.error("/api/event/create",{"name":"Check","venue":"Ambala"})[0],401)
        self.call("/api/event/join",{"invite":e["invite"].lower()},member)
        self.call("/api/event/join",{"invite":e["invite"]},member)
        _,snap=self.call("/api/snapshot?event="+e["id"],token=member)
        self.assertEqual(len(snap["members"]),2)
        self.assertEqual(snap["event"]["invite"],e["invite"])

    def test_account_session_is_immediately_usable(self):
        token=self.register("instant@example.test","Instant User")
        _,events=self.call("/api/events",token=token)
        self.assertEqual(events["events"],[])
        _,created=self.call("/api/event/create",{"name":"Saved event","venue":"Hodal"},token)
        _,events=self.call("/api/events",token=token)
        self.assertEqual(events["events"][0]["id"],created["id"])

    def test_voice_upload_download_and_member_access(self):
        lead=self.register("voicelead@example.test","Voice Lead")
        member=self.register("voicemember@example.test","Voice Member")
        outsider=self.register("voiceoutsider@example.test","Outsider")
        _,event=self.call("/api/event/create",{"name":"Voice event","venue":"Haryana"},lead)
        event_id=event["id"]
        self.call("/api/event/join",{"invite":event["invite"]},member)
        audio=b"\x00\x00\x00\x14ftypM4A "+b"X"*250
        encoded=base64.b64encode(audio).decode("ascii")
        code,posted=self.call("/api/voice/upload",{"event":event_id,"mime":"audio/mp4","duration":9,"audio":encoded},lead)
        self.assertEqual(code,201)
        _,listing=self.call("/api/voice?event="+event_id,token=member)
        self.assertEqual(len(listing["clips"]),1)
        self.assertEqual(listing["clips"][0]["sender"],"Voice Lead")
        self.assertNotIn("content",listing["clips"][0])
        request=urllib.request.Request(f"http://127.0.0.1:{self.port}/api/voice/file?id="+posted["id"],headers={"Authorization":"Bearer "+member})
        with urllib.request.urlopen(request) as response:
            self.assertEqual(response.read(),audio)
            self.assertEqual(response.headers.get("Content-Type"),"audio/mp4")
        self.assertEqual(self.error("/api/voice?event="+event_id,token=outsider)[0],403)
        with self.assertRaises(urllib.error.HTTPError) as cm:
            urllib.request.urlopen(urllib.request.Request(f"http://127.0.0.1:{self.port}/api/voice/file?id="+posted["id"],headers={"Authorization":"Bearer "+outsider}))
        self.assertEqual(cm.exception.code,403)
        self.assertEqual(self.error("/api/voice/delete",{"id":posted["id"]},outsider)[0],403)
        self.call("/api/voice/delete",{"id":posted["id"]},lead)
        _,listing=self.call("/api/voice?event="+event_id,token=member)
        self.assertEqual(listing["clips"],[])

    def test_voice_rejects_invalid_media_and_nonmembers(self):
        lead=self.register("voiceformat@example.test","Clip Owner")
        outsider=self.register("voicenonmember@example.test","Clip Outsider")
        _,event=self.call("/api/event/create",{"name":"Voice rules","venue":"Haryana"},lead)
        data={"event":event["id"],"mime":"audio/mp4","duration":15,"audio":base64.b64encode(b"garbage"*50).decode("ascii")}
        self.assertEqual(self.error("/api/voice/upload",data,lead)[0],400)
        self.assertEqual(self.error("/api/voice/upload",data,outsider)[0],403)
        data["audio"]=base64.b64encode(b"\x00\x00\x00\x14ftypM4A "+b"X"*200).decode("ascii")
        data["duration"]=31
        self.assertEqual(self.error("/api/voice/upload",data,lead)[0],400)

    def test_existing_email_is_rejected_and_password_not_plaintext(self):
        email="existing+voice@example.test"
        self.register(email,"Existing")
        status,err=self.error("/api/register",{"name":"Existing","email":email,"password":"StrongPass9"})
        self.assertEqual(status,409)
        self.assertIn("sign in",err["error"].lower())
        with server.connect() as db:
            row=db.execute("SELECT password FROM users WHERE email=?",(email,)).fetchone()
            self.assertNotIn("StrongPass9",row["password"])
            self.assertEqual(len(row["password"].split(":")),2)

    def test_location_expiry_and_removal(self):
        token=self.register("loc@example.test","Locator");_,e=self.call("/api/event/create",{"name":"Test","venue":"Haryana"},token);eid=e["id"]
        self.call("/api/location/share",{"event":eid,"lat":30.3,"lng":76.8,"accuracy":15,"captured":server.now()},token)
        _,snap=self.call("/api/snapshot?event="+eid,None,token);self.assertEqual(1,len(snap["locations"]))
        self.call("/api/location/remove",{"event":eid},token);_,snap=self.call("/api/snapshot?event="+eid,None,token);self.assertEqual([],snap["locations"])


class PostgreSQLAdapterTranslationTest(unittest.TestCase):
    def test_parameter_markers_and_task_conflict(self):
        q = server.PostgreSQLAdapter.translate("INSERT OR IGNORE INTO tasks VALUES(?,?,?)")
        self.assertEqual(q, "INSERT INTO tasks VALUES(%s,%s,%s) ON CONFLICT DO NOTHING")

    def test_location_upsert(self):
        q = server.PostgreSQLAdapter.translate("INSERT OR REPLACE INTO locations VALUES(?,?,?,?,?,?,?)")
        self.assertIn("ON CONFLICT(event_id,user_id) DO UPDATE", q)
        self.assertIn("expires=EXCLUDED.expires", q)
        self.assertEqual(q.count("%s"), 7)

    def test_schema_has_unique_invite(self):
        self.assertIn("invite TEXT UNIQUE NOT NULL", server.POSTGRES_SCHEMA)
        self.assertIn("BIGINT GENERATED BY DEFAULT AS IDENTITY", server.POSTGRES_SCHEMA)

if __name__=="__main__":unittest.main()
