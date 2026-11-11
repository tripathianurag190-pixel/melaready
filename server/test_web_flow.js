// End-to-end JavaScript logic smoke test against the running Python server.
// No Node packages needed: node server/test_web_flow.js 127.0.0.1 8080
// Uses a minimal DOM shim because the browser runtime isn't needed to exercise
// the event API calls and the UI's state / code display logic.
'use strict';
const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const path=require('node:path');
const port=process.argv[3] || '8080';
const host=process.argv[2] || '127.0.0.1';
const html=fs.readFileSync(path.join(__dirname,'web','index.html'),'utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
const instances=[];
function newClient(){
  const elements={};
  const el=id=>elements[id] || (elements[id]={textContent:'',value:'',hidden:(id==='#app'||id==='#workspace'||id==='#eventSuccess'||id==='#notice'),
    innerHTML:'',className:'',disabled:false,dataset:{},replaceChildren(){},scrollIntoView(){},focus(){},select(){}});
  // Event listeners are not required: direct JS click handlers are invoked.
  const storage=new Map();
  const sessionStorage={getItem:key=>storage.get(key)||null,setItem:(key,value)=>storage.set(key,value),removeItem:key=>storage.delete(key)};
  const localStorage={getItem:()=>null,setItem:()=>{}};
  const sandbox={document:{querySelector:el,querySelectorAll:()=>[],documentElement:{lang:'en'},execCommand:()=>true},sessionStorage,localStorage,
    fetch:(url,opts)=>fetch(`http://${host}:${port}${url}`,opts),
    window:{isSecureContext:false},navigator:{},Date,Math,Number,String,Error,RegExp,
    setInterval:()=>{},console,crypto:globalThis.crypto||require('node:crypto').webcrypto};
  vm.createContext(sandbox);vm.runInContext(script,sandbox);
  instances.push({elements,sandbox,el,storage});
  return instances.at(-1);
}
async function run(c,expression){return await vm.runInContext(expression,c.sandbox)}
async function test(){
  const first=newClient();
  const uniq=Date.now()+'-'+Math.floor(Math.random()*1e6);
  first.el('#name').value='Test Lead';first.el('#email').value=`lead${uniq}@example.test`;
  first.el('#password').value='StrongPass123';
  await run(first,`auth('register')`);
  assert.equal(first.el('#auth').hidden,true,'Lead should be signed in');
  assert.match(html,/window\.createEvent\(\)/,'Create button must avoid document.createEvent name collision');
  assert.match(html,/id=\"voiceCard\"/,'Voice clip interface must be present');
  await run(first,'createEvent()');
  assert.match(first.el('#notice').textContent,/Enter an event name/,'Blank event name should display an error');
  first.el('#eventName').value='Ambala Test Event';first.el('#venue').value='Ambala';
  await run(first,'createEvent()');
  assert.equal(first.el('#eventSuccess').hidden,false,'Success code panel must be visible');
  const code=first.el('#createdInvite').textContent;
  assert.match(code,/^[A-Z]{3}[0-9]{3}$/);
  await run(first,'openCreatedEvent()');
  assert.equal(first.el('#workspace').hidden,false,'Workspace must open');
  assert.equal(first.el('#workspaceInvite').textContent,code,'Workspace must show identical code');
  const second=newClient();
  second.el('#name').value='Test Member';second.el('#email').value=`member${uniq}@example.test`;
  second.el('#password').value='StrongPass123';
  await run(second,`auth('register')`);
  second.el('#invite').value=code.toLowerCase();
  await run(second,'joinEvent()');
  assert.equal(second.el('#workspace').hidden,false,'Joiner must see workspace');
  assert.equal(second.el('#eventTitle').textContent,'Ambala Test Event');
  assert.equal(second.el('#workspaceInvite').textContent,code,'Joined members get same code');
  await run(first,'loadSnapshot()');
  assert.match(first.el('#members').innerHTML,/Test Member/,'Lead must see second member on server');
  const third=newClient();
  third.el('#name').value='Wrong Code';third.el('#email').value=`wrong${uniq}@example.test`;
  third.el('#password').value='StrongPass123';
  await run(third,`auth('register')`);
  third.el('#invite').value='AAA';await run(third,'joinEvent()');
  assert.match(third.el('#notice').textContent,/six-character/,'Invalid code error must be visible in event gate');
  console.log('PASS: UI create button, validation, code display, workspace, second-account join, member sync, invalid-code message');
  console.log('Isolated test invite code:',code);
}
test().catch(e=>{console.error(e);process.exitCode=1});
