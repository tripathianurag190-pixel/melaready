"""v0.7 integration tests: owner-only writes, activity, privacy, SOS and migrations."""
import base64, json, tempfile, threading, unittest, urllib.error, urllib.request
from pathlib import Path
from http.server import ThreadingHTTPServer
import server

class V07Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory()
        cls.previous_db=server.DB
        server.DB=Path(cls.tmp.name)/'v07.sqlite3'
        server.init()
        cls.http=ThreadingHTTPServer(('127.0.0.1',0),server.Handler)
        cls.url=f'http://127.0.0.1:{cls.http.server_port}'
        threading.Thread(target=cls.http.serve_forever,daemon=True).start()
    @classmethod
    def tearDownClass(cls):
        cls.http.shutdown();cls.http.server_close();server.DB=cls.previous_db;cls.tmp.cleanup()
    def req(self,path,obj=None,token=None,expected=200):
        headers={'Content-Type':'application/json'}
        if token: headers['Authorization']='Bearer '+token
        request=urllib.request.Request(self.url+path,data=json.dumps(obj).encode() if obj is not None else None,headers=headers)
        try:
            with urllib.request.urlopen(request) as r: code=r.status;payload=json.load(r)
        except urllib.error.HTTPError as e:code=e.code;payload=json.load(e)
        self.assertEqual(expected,code,payload)
        return payload
    def make(self,suffix):
        return self.req('/api/register',{'name':suffix,'email':suffix+'@example.test','password':'StrongPass123'})['token']
    def event(self,token):
        return self.req('/api/event/create',{'name':'Test Workshop','venue':'Ambala'},token,201)
    def test_creator_controls_and_member_overview(self):
        creator=self.make('owner07');participant=self.make('member07');outsider=self.make('stranger07');ev=self.event(creator);eid=ev['id']
        self.req('/api/event/join',{'invite':ev['invite']},participant)
        me=self.req('/api/events',token=participant)['user']['id']
        self.req('/api/event/update',{'event':eid,'name':'Wrong owner','venue':'Ambala'},participant,403)
        self.req('/api/checklist/add',{'event':eid,'label':'Verify exits'},participant,403)
        self.req('/api/member/overview?event='+eid+'&user='+me,token=participant,expected=403)
        self.req('/api/member/overview?event='+eid+'&user='+me,token=outsider,expected=403)
        self.req('/api/checklist/add',{'event':eid,'label':'Verify exits'},creator,201)
        before=self.req('/api/snapshot?event='+eid,token=participant)
        self.assertEqual(len(before['checklist']),1)
        self.req('/api/checklist/toggle',{'event':eid,'id':before['checklist'][0]['id']},creator)
        self.req('/api/drill/create',{'event':eid,'description':'Simulated crowd drill','zone':'Gate A'},creator,201)
        self.req('/api/task/create',{'event':eid,'title':'Staff information desk','assignee':me,'client_id':'task-member-07'},creator,201)
        self.req('/api/report/create',{'event':eid,'details':'Volunteer observed a bottleneck','zone':'Gate A','client_id':'report-member-07'},participant,201)
        overview=self.req('/api/member/overview?event='+eid+'&user='+me,token=creator)
        self.assertEqual(len(overview['tasks']),1)
        self.assertEqual(len(overview['reports']),1)
        self.assertGreaterEqual(len(overview['activity']),2)
        self.assertTrue(all('created' in i for i in overview['activity']))
        whole=self.req('/api/activity?event='+eid,token=participant)
        self.assertTrue(any(x['action']=='checklist.completed' for x in whole['activity']))
        self.req('/api/event/update',{'event':eid,'name':'Updated Event','venue':'Revised Venue'},creator)
        after=self.req('/api/snapshot?event='+eid,token=participant)
        self.assertEqual(after['event']['name'],'Updated Event')
        self.assertTrue(after['checklist'][0]['completed'])
        self.assertEqual(len(after['drills']),1)
        self.req('/api/event/lock',{'event':eid,'locked':True},creator)
        another=self.make('newjoiner07')
        self.req('/api/event/join',{'invite':ev['invite']},another,409)
        self.req('/api/event/lock',{'event':eid,'locked':False},creator)
        self.req('/api/event/join',{'invite':ev['invite']},another)
        self.req('/api/event/archive',{'event':eid,'archived':True},participant,403)
        self.req('/api/event/archive',{'event':eid,'archived':True},creator)
        self.req('/api/checklist/add',{'event':eid,'label':'New item'},creator,409)
        self.req('/api/event/archive',{'event':eid,'archived':False},creator)
    def test_sos_permissions_transitions_and_timestamps(self):
        creator=self.make('sosowner07');member=self.make('sosmember07');outsider=self.make('sosoutsider07');ev=self.event(creator);eid=ev['id']
        self.req('/api/event/join',{'invite':ev['invite']},member)
        alert=self.req('/api/sos/send',{'event':eid,'summary':'Please send someone to Gate A','zone':'Gate A'},member,201)
        self.assertEqual(alert['status'],'OPEN')
        self.assertIn('does not contact',alert['warning'])
        self.req('/api/sos/status',{'event':eid,'id':alert['id'],'status':'ACKNOWLEDGED'},member,403)
        self.req('/api/sos/status',{'event':eid,'id':alert['id'],'status':'RESOLVED'},creator,409)
        self.req('/api/sos/status',{'event':eid,'id':alert['id'],'status':'ACKNOWLEDGED'},creator)
        self.req('/api/sos/status',{'event':eid,'id':alert['id'],'status':'RESOLVED'},creator)
        self.req('/api/sos/status',{'event':eid,'id':alert['id'],'status':'CANCELLED'},member,409)
        snap=self.req('/api/snapshot?event='+eid,token=member)
        self.assertEqual(snap['sos'][0]['status'],'RESOLVED')
        self.assertTrue(snap['sos'][0]['created']>0)
        self.assertTrue(snap['sos'][0]['updated']>=snap['sos'][0]['created'])
        self.req('/api/snapshot?event='+eid,token=outsider,expected=403)
        self.req('/api/sos/send',{'event':eid,'summary':'Secret alert','zone':'Gate B'},outsider,403)
        self.req('/api/sos/send',{'event':eid,'summary':'Duplicate alert too soon','zone':'Gate B'},member,429)
    def test_profile_photo_privacy_and_migration(self):
        server.init() # migration must be safe on existing database
        creator=self.make('photoowner07');other=self.make('photomember07');out=self.make('photoout07');ev=self.event(creator)
        self.req('/api/event/join',{'invite':ev['invite']},other)
        user=self.req('/api/events',token=other)['user']['id']
        fake=b'\xff\xd8\xff'+b'x'*100+b'\xff\xd9'
        self.req('/api/profile/photo',{'jpeg':base64.b64encode(fake).decode('ascii')},other)
        request=urllib.request.Request(self.url+'/api/profile/photo?event='+ev['id']+'&user='+user,headers={'Authorization':'Bearer '+creator})
        with urllib.request.urlopen(request) as result:
            self.assertEqual(result.read(),fake)
        request=urllib.request.Request(self.url+'/api/profile/photo?event='+ev['id']+'&user='+user,headers={'Authorization':'Bearer '+out})
        with self.assertRaises(urllib.error.HTTPError) as cm:urllib.request.urlopen(request)
        self.assertEqual(cm.exception.code,403)
        self.req('/api/profile',{'event':ev['id'],'phone':'9000000000','contact_visible':False},other)
        snap=self.req('/api/snapshot?event='+ev['id'],token=creator)
        self.assertFalse(any(m['phone'] for m in snap['members']))

class UpgradeMigrationTest(unittest.TestCase):
    def test_v06_style_existing_event_survives_v07_migration(self):
        import sqlite3
        with tempfile.TemporaryDirectory() as folder:
            old_db=Path(folder)/'existing.sqlite3'
            raw=sqlite3.connect(old_db)
            raw.execute("CREATE TABLE events(id TEXT PRIMARY KEY,name TEXT NOT NULL,venue TEXT NOT NULL,invite TEXT UNIQUE NOT NULL,lead_id TEXT NOT NULL,created INTEGER NOT NULL)")
            raw.execute("INSERT INTO events VALUES(?,?,?,?,?,?)",('e1','Existing Event','Ambala','ABC987','lead1',1780000000))
            raw.commit();raw.close()
            previous=server.DB
            try:
                server.DB=old_db
                server.init()
                server.init()  # idempotent
                with server.connect() as connection:
                    event=connection.execute("SELECT invite,locked,archived,updated FROM events WHERE id='e1'").fetchone()
                    self.assertEqual('ABC987',event['invite'])
                    self.assertEqual(0,event['locked'])
                    self.assertEqual(0,event['archived'])
                    self.assertEqual(0,event['updated'])
                    self.assertIsNotNone(connection.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='sos_alerts'").fetchone())
            finally:
                server.DB=previous
