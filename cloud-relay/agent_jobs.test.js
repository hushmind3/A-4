import test from 'node:test';
import assert from 'node:assert/strict';
import {safeSite,decision,seal,unseal,jobRoute,boundedText,observation,checkpointMessages} from './agent_jobs.js';
const env={AGENT_ENCRYPTION_KEY:Buffer.alloc(32,17).toString('base64')};
test('no localhost or lookalike hosts',()=>{
 for(const url of ['http://ko.wikipedia.org','https://localhost/',
  'https://127.0.0.1/','https://ko.wikipedia.org.evil.com/',
  'https://user@ko.wikipedia.org/','https://ko.wikipedia.org:8443/'])
   assert.equal(safeSite(url),false,url);
 assert.equal(safeSite('https://ko.wikipedia.org/wiki/A-4'),true);
});
test('only read-only cloud model tools accepted',()=>{
 assert.deepEqual(decision('{"tool":"clock","args":{}}'),{kind:'clock'});
 assert.deepEqual(decision('{"final":"Completed read"}'),{kind:'final',content:'Completed read'});
 assert.equal(decision('{"tool":"send_email","args":{}}'),null);
});
test('encrypted job contents cannot cross account or record',async()=>{
 const blob=await seal(env,'A','1','prompt','private record');
 assert.equal(await unseal(env,'A','1','prompt',blob),'private record');
 await assert.rejects(unseal(env,'B','1','prompt',blob));
 await assert.rejects(unseal(env,'A','2','prompt',blob));
});
test('a PC relay credential cannot launch cloud jobs',async()=>{
 const result=await jobRoute(new Request('https://cloud/v1/agent/jobs',{method:'POST'}),
   env,{id:'A',scope:'app',capability:'assistant_pc'},{waitUntil(){}},async()=>null);
 assert.equal(result.status,403);
});

test('cloud daily quota keys are scoped to the exact account',async()=>{
 const {quotaKey}=await import('./agent_jobs.js');
 assert.notEqual(quotaKey('google:alpha'),quotaKey('google:beta'));
 assert.match(quotaKey('google:alpha',new Date('2026-11-27T00:00:00Z')),/^google:alpha:cloud-jobs:2026-11-27$/);
});
test('read-only route enforces account ownership before decrypting results',async()=>{
 let queryArgs;
 const store={prepare(sql){return{
   bind(...args){queryArgs=args;return this},
   async first(){return null}
 }}};
 const user={id:'google:other-user',scope:'app',capability:'inference'};
 const request=new Request('https://cloud/v1/agent/jobs/11111111-1111-4111-8111-111111111111');
 const result=await jobRoute(request,{...env,DB:store},user,{waitUntil(){}},async()=>null);
 assert.equal(result.status,404);
 assert.equal(queryArgs[1],'google:other-user');
});
test('create rejects invalid client IDs without spending quota',async()=>{
 const account={id:'google:alpha',scope:'app',capability:'inference'};
 const res=await jobRoute(new Request('https://cloud/v1/agent/jobs',{
   method:'POST',headers:{'content-type':'application/json'},
   body:JSON.stringify({client_id:'not-a-uuid',prompt:'Research this'})
 }),{...env,DB:{prepare(){throw Error('Must not touch DB')}}},account,{waitUntil(){}},async()=>null);
 assert.equal(res.status,400);
});

test('search tools require a supported public source and bounded query',()=>{
 assert.deepEqual(decision(JSON.stringify({tool:'web.search',args:{source:'wiki-ko',query:'한국 역사'}})),
   {kind:'web.search',source:'wiki-ko',query:'한국 역사'});
 for(const args of [{source:'private',query:'abc'},{source:'github',query:'x'.repeat(161)},
  {source:'github',query:'abc',url:'https://evil.test'},{source:'github',query:'a\nb'}])
   assert.equal(decision(JSON.stringify({tool:'web.search',args})),null);
});
test('search uses fixed endpoints and filters returned lookalike links',async()=>{
 const seen=[];
 const fake=async(url,opts)=>{
  seen.push(new URL(url));assert.equal(opts.redirect,'manual');
  return Response.json(['history',['Korea','fake'],['',''],['https://ko.wikipedia.org/wiki/Korea','https://ko.wikipedia.org.evil.test/']]);
 };
 const result=await observation({kind:'web.search',source:'wiki-ko',query:'한국 역사'},fake);
 assert.equal(seen[0].hostname,'ko.wikipedia.org');
 assert.equal(seen[0].searchParams.get('search'),'한국 역사');
 assert.match(result,/wiki\/Korea/);
 assert.doesNotMatch(result,/evil\.test/);
});
test('GitHub search returns public repository API URLs for subsequent reading',async()=>{
 const result=await observation({kind:'web.search',source:'github',query:'agent'},async(url)=>{
  const u=new URL(url);assert.equal(u.pathname,'/search/repositories');assert.equal(u.searchParams.get('per_page'),'3');
  return Response.json({items:[{full_name:'owner/repo',url:'https://api.github.com/repos/owner/repo',description:'Agent'}]});
 });
 assert.match(result,/api\.github\.com\/repos\/owner\/repo/);
});
test('chunked responses are cancelled as soon as actual byte cap is crossed',async()=>{
 let cancelled=false;
 const stream=new ReadableStream({pull(c){c.enqueue(new Uint8Array(8))},cancel(){cancelled=true}});
 await assert.rejects(boundedText(new Response(stream,{headers:{'content-type':'text/plain'}}),10),/Oversized/);
 assert.equal(cancelled,true);
});
test('checkpoint preserves system and original user request across many steps',()=>{
 let messages=checkpointMessages([],'original question');
 for(let i=0;i<12;i++)messages=checkpointMessages([...messages,{role:'assistant',content:'step '+i},{role:'user',content:'obs '+i}],'original question');
 assert.equal(messages.length,10);assert.equal(messages[0].role,'system');
 assert.equal(messages[1].content,'original question');assert.equal(messages.at(-1).content,'obs 11');
});
