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
