import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './index.js';

test('real Worker entry point parses and serves public health',async()=>{
 const res=await worker.fetch(new Request('https://dominion.invalid/health'),
   {GOOGLE_CLIENT_ID:'public-id',AI:{}},{waitUntil(){}});
 assert.equal(res.status,200);
 const body=await res.json();
 assert.equal(body.service,'DOMINION Cloud');
});
test('real Worker entry point denies untrusted browser origins',async()=>{
 const res=await worker.fetch(new Request('https://dominion.invalid/v1/agent/jobs',{
   method:'GET',headers:{Origin:'https://hostile.example'}
 }),{DB:{},ALLOWED_ORIGIN:'https://dominion.example'},{waitUntil(){}});
 assert.equal(res.status,403);
});
test('missing D1 binding fails closed before agent/voice handling',async()=>{
 const res=await worker.fetch(new Request('https://dominion.invalid/v1/agent/jobs',{
   method:'GET'
 }),{ALLOWED_ORIGIN:'https://dominion.example'},{waitUntil(){}});
 assert.equal(res.status,503);
});

test('minute recovery trigger does not run older message retention or model maintenance',async()=>{
 const queries=[];
 const env={AGENT_ENCRYPTION_KEY:'configured',DB:{prepare(sql){queries.push(sql);return{
   bind(){return this},
   async all(){return {results:[]}},
   async run(){return {}}
 };}}};
 const pending=[];
 await worker.scheduled({cron:'* * * * *'},env,{waitUntil:p=>pending.push(p)});
 await Promise.all(pending);
 assert.equal(queries.length,2);
 assert.ok(queries.every(sql=>sql.includes('cloud_agent_jobs')));
});
