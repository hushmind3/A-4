import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import {workRoute} from './work_sessions.js';

const fixture=()=>{
  const sqlite=new DatabaseSync(':memory:');
  sqlite.exec('CREATE TABLE accounts(id TEXT PRIMARY KEY,email TEXT,credits INTEGER DEFAULT 0,created_at INTEGER);'+
    'CREATE TABLE app_sessions(id TEXT PRIMARY KEY,account_id TEXT,status TEXT,expires_at INTEGER,capability TEXT,token_hash TEXT UNIQUE);'+
    'CREATE TABLE free_quota(account_day TEXT PRIMARY KEY,used INTEGER NOT NULL);');
  sqlite.exec(readFileSync(new URL('./migrations/0007_work_sessions.sql',import.meta.url),'utf8'));
  const DB={prepare(sql){
    const st=sqlite.prepare(sql);let args=[];
    const b={bind(...v){args=v;return b},async first(){return st.get(...args)??null},
      async all(){return {results:st.all(...args)}},async run(){return st.run(...args)}};
    return b;
  }};
  sqlite.prepare('INSERT INTO accounts(id,email,credits,created_at) VALUES(?,?,0,?)')
    .run('google:alice','a@example.test',Math.floor(Date.now()/1000));
  const user={id:'google:alice',scope:'app',capability:'inference'};
  const pending=[];
  const ctx={waitUntil(p){pending.push(p)}};
  return {sqlite,DB,user,ctx,pending};
};
const req=(path,method='GET',body)=>new Request('https://dominion.invalid'+path,{
  method,headers:body?{'content-type':'application/json'}:{},...(body?{body:JSON.stringify(body)}:{})});

test('work persists a question and resumes from a user answer',async()=>{
  const {sqlite,DB,user,ctx,pending}=fixture();
  try{
    let calls=0;
    const infer=async()=>++calls===1?'{"question":"생년월일을 알려주세요"}':'{"done":"입력한 정보로 접수했습니다."}';
    const created=await workRoute(req('/v1/work','POST',{
      client_id:'11111111-1111-4111-8111-111111111111',kind:'admin',
      title:'민원 신청',request:'주민등록등본 신청'}),{DB},user,ctx,infer);
    assert.equal(created.status,202);
    const {id}=await created.json();
    await Promise.all(pending);
    let work=await(await workRoute(req('/v1/work/'+id),{DB},user,ctx,infer)).json();
    assert.equal(work.status,'waiting');
    assert.ok(work.events.some(e=>/생년월일/.test(e.text)));
    await workRoute(req('/v1/work/'+id+'/message','POST',{
      client_id:'22222222-2222-4222-8222-222222222222',text:'1990-01-01'}),{DB},user,ctx,infer);
    await Promise.all(pending);
    work=await(await workRoute(req('/v1/work/'+id),{DB},user,ctx,infer)).json();
    assert.equal(work.status,'done');
    assert.match(work.result,/접수했습니다/);
    assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM cloud_work_events').get().n,4);
    assert.equal((await workRoute(req('/v1/work/'+id),{DB},{id:'google:bob'},ctx,infer)).status,404);
    assert.equal((await workRoute(req('/v1/work/'+id+'/cancel','POST'),{DB},user,ctx,infer)).status,200);
  }finally{sqlite.close()}
});
