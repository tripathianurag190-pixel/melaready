import base64, json, tempfile, threading, unittest, urllib.error, urllib.request
from pathlib import Path
import server.server as server
from http.server import ThreadingHTTPServer

class ApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory();server.DB=Path(cls.tmp.name)/"test.sqlite3";server.init()
        cls.http=ThreadingHTTPServer(("127.0.0.1",0),server.Handler);cls.port=cls.http.server_port
        threading.Thread(target=cls.http.serve_forever,daemon=True).start()
    @classmethod
    def tearDownClass(cls):cls.http.shutdown();cls.tmp.cleanup()
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
    def test_location_expiry_and_removal(self):
        token=self.register("loc@example.test","Locator");_,e=self.call("/api/event/create",{"name":"Test","venue":"Haryana"},token);eid=e["id"]
        self.call("/api/location/share",{"event":eid,"lat":30.3,"lng":76.8,"accuracy":15,"captured":server.now()},token)
        _,snap=self.call("/api/snapshot?event="+eid,None,token);self.assertEqual(1,len(snap["locations"]))
        self.call("/api/location/remove",{"event":eid},token);_,snap=self.call("/api/snapshot?event="+eid,None,token);self.assertEqual([],snap["locations"])
    def test_private_contact_and_voice_clip(self):
        lead=self.register("privacy-lead@example.test","Private Lead");member=self.register("privacy-member@example.test","Viewer")
        _,e=self.call("/api/event/create",{"name":"Privacy","venue":"Haryana"},lead);eid=e["id"]
        self.call("/api/event/join",{"invite":e["invite"]},member)
        self.call("/api/profile",{"event":eid,"phone":"9999999999","contact_visible":False,"photo":""},lead)
        _,snap=self.call("/api/snapshot?event="+eid,None,member);owner=[m for m in snap["members"] if m["name"]=="Private Lead"][0];self.assertEqual("",owner["phone"])
        audio=base64.b64encode(b"a"*120).decode();self.call("/api/voice/upload",{"event":eid,"mime":"audio/webm","audio":audio},member)
        _,snap=self.call("/api/snapshot?event="+eid,None,lead);self.assertEqual(1,len(snap["voice"]));self.assertEqual("Viewer",snap["voice"][0]["name"])
    def test_health_endpoint(self):
        status, res = self.call("/api/health")
        self.assertEqual(200, status)
        self.assertTrue(res["ok"])
        self.assertEqual("MelaReady", res["service"])
        status, res = self.call("/health")
        self.assertEqual(200, status)
        self.assertTrue(res["ok"])

    def test_auth_registration_login_logout(self):
        # Valid registration
        token = self.register("user1@example.test", "User One")
        self.assertTrue(len(token) > 10)

        # Duplicate registration fails
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/register", {"email": "user1@example.test", "name": "User Dup", "password": "StrongPass9"})
        self.assertEqual(409, caught.exception.code)

        # Short password fails
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/register", {"email": "short@example.test", "name": "Short", "password": "123"})
        self.assertEqual(400, caught.exception.code)

        # Invalid email fails
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/register", {"email": "invalidemail", "name": "Invalid", "password": "StrongPass9"})
        self.assertEqual(400, caught.exception.code)

        # Login valid
        status, res = self.call("/api/login", {"email": "user1@example.test", "password": "StrongPass9"})
        self.assertEqual(200, status)
        new_token = res["token"]

        # Login invalid password
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/login", {"email": "user1@example.test", "password": "WrongPassword"})
        self.assertEqual(401, caught.exception.code)

        # Logout revokes token
        self.call("/api/logout", {}, new_token)
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/events", None, new_token)
        self.assertEqual(401, caught.exception.code)

    def test_expired_session_is_rejected(self):
        token = self.register("expired@example.test", "Expired User")
        with server.connect() as c:
            c.execute("UPDATE sessions SET expires=? WHERE user_id=(SELECT id FROM users WHERE email=?)", (server.now() - 10, "expired@example.test"))
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/events", None, token)
        self.assertEqual(401, caught.exception.code)

    def test_voice_clip_validation_and_access(self):
        lead = self.register("vlead@example.test", "Voice Lead")
        nonmember = self.register("vnon@example.test", "Non Member")
        _, e = self.call("/api/event/create", {"name": "Voice Event", "venue": "Venue"}, lead)
        eid = e["id"]

        # Oversized voice fails (> 250 KB)
        huge_audio = base64.b64encode(b"a" * 260_000).decode()
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/voice/upload", {"event": eid, "mime": "audio/webm", "audio": huge_audio}, lead)
        self.assertEqual(400, caught.exception.code)

        # Unsupported MIME fails
        small_audio = base64.b64encode(b"a" * 200).decode()
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/voice/upload", {"event": eid, "mime": "audio/wav", "audio": small_audio}, lead)
        self.assertEqual(400, caught.exception.code)

        # Non-member upload fails
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/voice/upload", {"event": eid, "mime": "audio/webm", "audio": small_audio}, nonmember)
        self.assertEqual(403, caught.exception.code)

    def test_create_event_success_and_validation(self):
        token = self.register("creator@example.test", "Creator")
        # Missing event name
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/event/create", {"name": " ", "venue": "Ambala"}, token)
        self.assertEqual(400, caught.exception.code)

        # Missing venue
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/event/create", {"name": "Mela Event", "venue": " "}, token)
        self.assertEqual(400, caught.exception.code)

        # Valid creation
        status, res = self.call("/api/event/create", {"name": "Kumbh Volunteer Zone A", "venue": "Haridwar Gate 2"}, token)
        self.assertEqual(201, status)
        self.assertTrue("id" in res)
        self.assertEqual(6, len(res["invite"]))

    def test_join_event_validation_and_errors(self):
        token = self.register("joiner@example.test", "Joiner")
        # Invalid code length -> 400
        with self.assertRaises(urllib.error.HTTPError) as caught1:
            self.call("/api/event/join", {"invite": "ABC"}, token)
        self.assertEqual(400, caught1.exception.code)

        # Non-existent 6-char code -> 404
        with self.assertRaises(urllib.error.HTTPError) as caught2:
            self.call("/api/event/join", {"invite": "NOEXIS"}, token)
        self.assertEqual(404, caught2.exception.code)

    def test_location_share_membership_rules(self):
        lead = self.register("loclead@example.test", "Loc Lead")
        nonmember = self.register("locnon@example.test", "Loc NonMember")
        _, e = self.call("/api/event/create", {"name": "Loc Event", "venue": "Venue"}, lead)
        eid = e["id"]

        # Share location non-member fails
        with self.assertRaises(urllib.error.HTTPError) as caught:
            self.call("/api/location/share", {"event": eid, "lat": 30.1, "lng": 76.8, "accuracy": 10, "captured": server.now()}, nonmember)
        self.assertEqual(403, caught.exception.code)

        # Share location member succeeds
        status, res = self.call("/api/location/share", {"event": eid, "lat": 30.1, "lng": 76.8, "accuracy": 10, "captured": server.now()}, lead)
        self.assertEqual(200, status)
        self.assertTrue("expires" in res)

    def test_expired_invite_is_rejected(self):
        lead=self.register("expiry-lead@example.test","Lead");member=self.register("expiry-member@example.test","Member")
        _,e=self.call("/api/event/create",{"name":"Expiry","venue":"Haryana"},lead)
        with server.connect() as c:c.execute("UPDATE events SET invite_expires=? WHERE id=?",(server.now()-1,e["id"]))
        with self.assertRaises(urllib.error.HTTPError) as caught:self.call("/api/event/join",{"invite":e["invite"]},member)
        self.assertEqual(410,caught.exception.code)

if __name__=="__main__":unittest.main()
