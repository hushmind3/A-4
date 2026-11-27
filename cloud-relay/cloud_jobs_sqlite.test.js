import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import worker from './index.js';
import {hash} from './token_crypto.js';
import {tickJobs} from './agent_jobs.js';

// Execute real SQLite SQL, including D1's CHECK, RETURNING and conflict clauses.
const fixture=async()=>{
 const sqlite=new DatabaseSync(':memory:');
 sqlite.exec([
  'CREATE TABLE accounts(id TEXT PRIMARY KEY, email TEXT, credits INTEGER DEFAULT 0,created_at INTEGER)',
  'CREATE TABLE app_sessions(id TEXT PRIMARY KEY,account_id TEXT,status TEXT,expires_at INTEGER,capability TEXT,token_hash TEXT UNIQUE)',
  'CREATE TABLE subscriptions(account_id TEXT PRIMARY KEY,status TEXT,current_period_end INTEGER)',
  'CREATE TABLE free_quota(account_day TEXT PRIMARY KEY,used INTEGER NOT NULL)'
 ].join(';')+';');
 sqlite.exec(readFileSync(new URL('./migrations/0004_agent_jobs.sql',import.meta.url),'utf8'));
 const DB={prepare(sql){
   const statement=sqlite.prepare(sql);
   let args=[];
   const bound={bind(...v){args=v;return bound},
     async first(){return statement.get(...args)??null},
     async all(){return {results:statement.all(...args)}},
     async run(){return statement.run(...args)}};
   return bound;
 }};
 const now=Math.floor(Date.now()/1000);
 const token=x=>'dapp_'+x.repeat(43);
 for(const [letter,account] of [['a','google:alice'],['b','google:bob']]){
   sqlite.prepare('INSERT INTO accounts(id,credits) VALUES(?,0)').run(account);
   sqlite.prepare("INSERT INTO app_sessions(id,account_id,status,expires_at,capability,token_hash) VALUES(?,?,'approved',?,'inference',?)")
     .run('app-'+letter,account,now+86400,await hash(token(letter)));
 }
 const calls=[];
 const env={DB,AI:{async run(model,body){
   calls.push({model,messages:body.messages});
   return {response:'{"tool":"clock","args":{}}'};
 }},ALLOWED_ORIGIN:'https://dominion.invalid',
  AGENT_ENCRYPTION_KEY:Buffer.alloc(32,53).toString('base64')};
 const pending=[];
 const ctx={waitUntil(p){pending.push(p)}};
 const send=(letter,path,method='GET',body)=>
   worker.fetch(new Request('https://dominion.invalid'+path,{
     method,headers:{authorization:'Bearer '+token(letter),
       ...(body?{'content-type':'application/json'}:{})},
     ...(body?{body:JSON.stringify(body)}:{})
   }),env,ctx);
 return {sqlite,send,env,pending,calls};
};
test('real Worker + SQL: cloud job, cross-account denial, checkpoint, encrypted result',async()=>{
 const {sqlite,send,env,pending,calls}=await fixture();
 try{
  const request={prompt:'Tell me the UTC time',client_id:'11111111-1111-4111-8111-111111111111'};
  const first=await send('a','/v1/agent/jobs','POST',request);
  assert.equal(first.status,202);
  const {id}=await first.json();
  assert.match(id,/^[0-9a-f-]{36}$/);
  await Promise.all(pending);
  const middle=await (await send('a','/v1/agent/jobs/'+id)).json();
  assert.equal(middle.status,'queued');
  assert.equal((await send('b','/v1/agent/jobs/'+id)).status,404);
  const again=await send('a','/v1/agent/jobs','POST',request);
  assert.equal(again.status,200);
  assert.equal((await again.json()).id,id);
  await tickJobs(env,async messages=>
    messages.at(-1)?.content?.includes('Verified tool observation')?
      '{"final":"UTC time confirmed from clock."}':'{"tool":"clock","args":{}}');
  const result=await (await send('a','/v1/agent/jobs/'+id)).json();
  assert.equal(result.status,'done');
  assert.match(result.result,/UTC time confirmed/);
  assert.equal(result.evidence.length,1);
  assert.equal(result.evidence[0].tool,'clock');
  assert.equal(result.evidence[0].status,'ok');
  assert.match(result.evidence[0].observation,/Current UTC:/);
  const stored=sqlite.prepare('SELECT prompt_enc,result_enc FROM cloud_agent_jobs WHERE id=?').get(id);
  assert.ok(!stored.prompt_enc.includes(request.prompt));
  assert.ok(!stored.result_enc.includes('UTC time confirmed'));
  assert.ok(calls.length>=1);
 }finally{sqlite.close()}
});
test('invalid submissions cannot reserve quota or create jobs',async()=>{
 const {sqlite,send}=await fixture();
 try{
  const response=await send('a','/v1/agent/jobs','POST',
    {prompt:'Research something',client_id:'bad'});
  assert.equal(response.status,400);
  assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM cloud_agent_jobs').get().n,0);
  assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM free_quota').get().n,0);
 }finally{sqlite.close()}
});

test('simultaneous submissions cannot exceed the active account limit',async()=>{
 const {sqlite,send,pending}=await fixture();
 try{
  const responses=await Promise.all(Array.from({length:6},(_,i)=>send('a','/v1/agent/jobs','POST',{
    prompt:'Research task number '+i,
    client_id:'11111111-1111-4111-8111-'+String(i+1).padStart(12,'0')
  })));
  await Promise.all(pending);
  assert.equal(responses.filter(r=>r.status===202).length,3);
  assert.ok(responses.every(r=>[202,429].includes(r.status)));
  assert.equal(sqlite.prepare("SELECT COUNT(*) AS n FROM cloud_agent_jobs WHERE status IN ('queued','running')").get().n,3);
 }finally{sqlite.close()}
});

test('one Cron invocation continues multiple steps with encrypted receipts',async()=>{
 const {sqlite,send,env,pending}=await fixture();
 try{
  const response=await send('a','/v1/agent/jobs','POST',{prompt:'Check time twice and summarize',client_id:'22222222-2222-4222-8222-222222222222'});
  const {id}=await response.json();await Promise.all(pending);
  let calls=0;
  await tickJobs(env,async()=>++calls===2?'{"final":"Two clock observations."}':'{"tool":"clock","args":{}}');
  const result=await(await send('a','/v1/agent/jobs/'+id)).json();
  assert.equal(calls,2);assert.equal(result.status,'done');
  assert.equal(result.steps,3);assert.deepEqual(result.evidence.map(x=>x.step),[1,2]);
  assert.ok(result.evidence.every(x=>x.status==='ok'));
  const stored=sqlite.prepare('SELECT checkpoint_enc,error_code FROM cloud_agent_jobs WHERE id=?').get(id);
  assert.ok(!stored.checkpoint_enc.includes('Current UTC:'));assert.equal(stored.error_code,null);
 }finally{sqlite.close()}
});
test('step exhaustion becomes terminal without retrying an impossible step',async()=>{
 const {sqlite,send,env,pending}=await fixture();
 try{
  const response=await send('a','/v1/agent/jobs','POST',{prompt:'Keep checking time',client_id:'33333333-3333-4333-8333-333333333333'});
  const {id}=await response.json();await Promise.all(pending);
  const clock=async()=> '{"tool":"clock","args":{}}';
  await tickJobs(env,clock);await tickJobs(env,clock);
  const result=await(await send('a','/v1/agent/jobs/'+id)).json();
  assert.equal(result.status,'failed');assert.equal(result.error,'step_limit');
  assert.equal(result.steps,5);assert.equal(result.evidence.length,5);
  await tickJobs(env,async()=>{throw Error('terminal job must not execute again')});
  assert.equal(sqlite.prepare('SELECT failures FROM cloud_agent_jobs WHERE id=?').get(id).failures,0);
 }finally{sqlite.close()}
});
test('cancellation while inference is running cannot publish a stale completion',async()=>{
 const {sqlite,send,env,pending}=await fixture();
 try{
  const response=await send('a','/v1/agent/jobs','POST',{prompt:'Check the time',client_id:'44444444-4444-4444-8444-444444444444'});
  const {id}=await response.json();await Promise.all(pending);
  await tickJobs(env,async()=>{
   assert.equal((await send('a','/v1/agent/jobs/'+id+'/cancel','POST')).status,200);
   return '{"final":"Must not be published"}';
  });
  const result=await(await send('a','/v1/agent/jobs/'+id)).json();
  assert.equal(result.status,'cancelled');assert.equal(result.result,null);
 }finally{sqlite.close()}
});

test('authenticated Worker exposes only configured browser capabilities and records rendered evidence',async()=>{
 const {sqlite,send,env,pending}=await fixture();
 try{
  let cap=await(await send('a','/v1/agent/jobs/capabilities')).json();
  assert.ok(!cap.tools.includes('browser.read'));
  env.BROWSER={async quickAction(action,options){
   assert.equal(action,'markdown');assert.equal(options.url,'https://developers.cloudflare.com/');
   return Response.json({success:true,result:'Rendered browser content'});
  }};
  cap=await(await send('a','/v1/agent/jobs/capabilities')).json();
  assert.ok(cap.tools.includes('browser.read'));
  const response=await send('a','/v1/agent/jobs','POST',{prompt:'Read the rendered documentation',client_id:'55555555-5555-4555-8555-555555555555'});
  const {id}=await response.json();await Promise.all(pending);
  let calls=0;
  await tickJobs(env,async messages=>{
   assert.match(messages[0].content,/browser.read/);
   if(++calls===1)return '{"tool":"browser.read","args":{"url":"https://developers.cloudflare.com/"}}';
   assert.match(messages.at(-1).content,/Rendered browser content/);
   return '{"final":"Rendered documentation was read."}';
  });
  const job=await(await send('a','/v1/agent/jobs/'+id)).json();
  assert.equal(job.status,'done');
  assert.equal(job.evidence[1].tool,'browser.read');
  assert.match(job.evidence[1].observation,/Rendered browser content/);
  assert.equal((await send('b','/v1/agent/jobs/'+id)).status,404);
 }finally{sqlite.close()}
});

test('direct chat tools share account-authenticated execution and reject unsupported calls',async()=>{
 const {sqlite,send,env}=await fixture();
 try{
  env.BROWSER={async quickAction(action,opts){
   assert.equal(action,'links');assert.equal(opts.visibleLinksOnly,true);
   return Response.json({success:true,result:['https://developers.cloudflare.com/workers/','https://evil.test/']});
  }};
  const invalid=await send('a','/v1/agent/jobs/tools','POST',{tool:'browser.links',args:{url:'https://evil.test/'}});
  assert.equal(invalid.status,400);
  const good=await send('a','/v1/agent/jobs/tools','POST',{tool:'browser.links',args:{url:'https://developers.cloudflare.com/'}});
  assert.equal(good.status,200);
  const obs=await good.json();
  assert.equal(obs.status,'ok');assert.match(obs.observation,/workers/);assert.doesNotMatch(obs.observation,/evil/);
  const key=sqlite.prepare("SELECT account_day,used FROM free_quota WHERE account_day LIKE '%cloud-direct-tools%'").get();
  assert.match(key.account_day,/^google:alice:/);assert.equal(key.used,1);
 }finally{sqlite.close()}
});
