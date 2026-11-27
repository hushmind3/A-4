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
