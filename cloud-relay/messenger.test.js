import test from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync} from 'node:fs';
import worker from './index.js';
import {hash} from './token_crypto.js';

const token=letter=>'dapp_'+letter.repeat(43);
const fixture=async()=>{
  const sqlite=new DatabaseSync(':memory:');
  sqlite.exec([
    'CREATE TABLE accounts(id TEXT PRIMARY KEY,email TEXT,credits INTEGER DEFAULT 0,created_at INTEGER)',
    'CREATE TABLE app_sessions(id TEXT PRIMARY KEY,account_id TEXT,status TEXT,expires_at INTEGER,capability TEXT,token_hash TEXT UNIQUE)',
    'CREATE TABLE subscriptions(account_id TEXT PRIMARY KEY,status TEXT,current_period_end INTEGER)',
    'CREATE TABLE free_quota(account_day TEXT PRIMARY KEY,used INTEGER NOT NULL)'
  ].join(';')+';');
  sqlite.exec(readFileSync(new URL('./migrations/0004_agent_jobs.sql',import.meta.url),'utf8'));
  sqlite.exec(readFileSync(new URL('./migrations/0005_messenger.sql',import.meta.url),'utf8'));
  const DB={prepare(sql){
    const statement=sqlite.prepare(sql);let args=[];
    const bound={bind(...v){args=v;return bound},
      async first(){return statement.get(...args)??null},
      async all(){return {results:statement.all(...args)}},
      async run(){return statement.run(...args)}};
    return bound;
  }};
  const now=Math.floor(Date.now()/1000);
  for(const [letter,id] of [['a','google:alice'],['b','google:bob'],['c','google:charlie']]){
    sqlite.prepare('INSERT INTO accounts(id,email,credits,created_at) VALUES(?,?,0,?)').run(id,letter+'@example.test',now);
    sqlite.prepare("INSERT INTO app_sessions(id,account_id,status,expires_at,capability,token_hash) VALUES(?,?,'approved',?,'inference',?)")
      .run('app-'+letter,id,now+86400,await hash(token(letter)));
  }
  const send=(letter,path,method='GET',value)=>worker.fetch(new Request('https://dominion.invalid'+path,{
    method,headers:{authorization:'Bearer '+token(letter),...(value?{'content-type':'application/json'}:{})},
    ...(value?{body:JSON.stringify(value)}:{})
  }),{DB,ALLOWED_ORIGIN:'https://dominion.invalid'},{waitUntil(){}});
  return {sqlite,send};
};

test('friends, direct messages and read state are account scoped',async()=>{
  const {sqlite,send}=await fixture();
  try{
    assert.equal((await send('a','/v1/messenger/profile','PUT',
      {handle:'alice',display_name:'Alice'})).status,200);
    assert.equal((await send('b','/v1/messenger/profile','PUT',
      {handle:'bob',display_name:'Bob'})).status,200);
    const found=await(await send('a','/v1/messenger/directory?q=bob')).json();
    assert.deepEqual(found.profiles.map(x=>x.handle),['bob']);
    assert.equal((await send('a','/v1/messenger/friends/request','POST',
      {account_id:'google:bob'})).status,200);
    const pending=await(await send('b','/v1/messenger/friends')).json();
    assert.equal(pending.incoming[0].accountId,'google:alice');
    const accepted=await(await send('b','/v1/messenger/friends/respond','POST',
      {account_id:'google:alice',accept:true})).json();
    assert.equal(accepted.status,'accepted');
    assert.match(accepted.threadId,/^[a-f0-9-]{36}$/);
    const sent=await send('a','/v1/messenger/threads/'+accepted.threadId+'/messages','POST',
      {text:'안녕하세요. 실제 메시지입니다.',client_id:'11111111-1111-4111-8111-111111111111'});
    assert.equal(sent.status,201);
    const duplicate=await send('a','/v1/messenger/threads/'+accepted.threadId+'/messages','POST',
      {text:'안녕하세요. 실제 메시지입니다.',client_id:'11111111-1111-4111-8111-111111111111'});
    assert.equal(duplicate.status,200);
    assert.equal(sqlite.prepare('SELECT COUNT(*) AS n FROM messenger_messages').get().n,1);
    const history=await(await send('b','/v1/messenger/threads/'+accepted.threadId+'/messages')).json();
    assert.equal(history.messages.length,1);
    assert.equal(history.messages[0].senderId,'google:alice');
    const list=await(await send('b','/v1/messenger/threads')).json();
    assert.equal(list.threads.length,1);
    assert.equal(list.threads[0].unread,1);
    await send('b','/v1/messenger/threads/'+accepted.threadId+'/read','POST',{});
    const after=await(await send('b','/v1/messenger/threads')).json();
    assert.equal(after.threads[0].unread,0);
    assert.equal((await send('c','/v1/messenger/threads/'+accepted.threadId+'/messages')).status,404);
  }finally{sqlite.close()}
});

test('groups require accepted friends and preserve membership',async()=>{
  const {sqlite,send}=await fixture();
  try{
    for(const [letter,handle,name] of [['a','alice','Alice'],['b','bob','Bob'],['c','charlie','Charlie']])
      await send(letter,'/v1/messenger/profile','PUT',{handle,display_name:name});
    for(const peer of ['google:bob','google:charlie']){
      await send('a','/v1/messenger/friends/request','POST',{account_id:peer});
      await send(peer==='google:bob'?'b':'c','/v1/messenger/friends/respond','POST',
        {account_id:'google:alice',accept:true});
    }
    const group=await send('a','/v1/messenger/threads','POST',{
      kind:'group',title:'행정 업무방',members:['google:bob','google:charlie']
    });
    assert.equal(group.status,201);
    const row=await group.json();
    assert.equal(row.thread.members.length,3);
    const msg=await send('b','/v1/messenger/threads/'+row.thread.id+'/messages','POST',{
      text:'그룹 메시지',client_id:'22222222-2222-4222-8222-222222222222'
    });
    assert.equal(msg.status,201);
    const c=await(await send('c','/v1/messenger/threads/'+row.thread.id+'/messages')).json();
    assert.equal(c.messages[0].text,'그룹 메시지');
    assert.equal((await send('a','/v1/messenger/threads','POST',{
      kind:'group',title:'비허용',members:['google:nobody']
    })).status,403);
  }finally{sqlite.close()}
});
