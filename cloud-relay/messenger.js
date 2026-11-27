import {deliverPush} from './assistant_push_delivery.js';
// Account-scoped consumer messenger: friends, direct/group threads, durable
// history and an SSE tail for near-realtime delivery. D1 is authoritative.
const now=()=>Math.floor(Date.now()/1000);
const reply=(x,status=200)=>new Response(JSON.stringify(x),{status,
  headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}});
const fail=(error,status=400)=>reply({error},status);
const isId=v=>typeof v==='string'&&/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(v);
const handleRe=/^[a-z0-9_]{3,24}$/;
const account=v=>typeof v==='string'&&v.length>0&&v.length<=200;
const text=v=>typeof v==='string'?v.trim():'';
const cleanText=v=>typeof v==='string'&&v.length<=4000&&v.trim().length>0&&
  !/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(v);
const cleanName=v=>typeof v==='string'&&v.trim().length>=1&&v.trim().length<=40&&
  !/[\u0000-\u001f\u007f]/.test(v);
const body=async(request,max=16000)=>{
  if(Number(request.headers.get('content-length'))>max)return null;
  const raw=await request.text();if(raw.length>max)return null;
  try{const value=JSON.parse(raw);return value&&typeof value==='object'&&!Array.isArray(value)?value:null}
  catch{return null}
};
const profileRow=async(env,id)=>env.DB.prepare(
  'SELECT account_id AS accountId,handle,display_name AS displayName FROM messenger_profiles WHERE account_id=?')
  .bind(id).first();
const membership=async(env,thread,accountId)=>env.DB.prepare(
  'SELECT thread_id AS threadId,last_read_at AS lastReadAt FROM messenger_members WHERE thread_id=? AND account_id=?')
  .bind(thread,accountId).first();
const friend=async(env,owner,peer)=>env.DB.prepare(
  'SELECT status FROM messenger_friends WHERE owner_id=? AND peer_id=?').bind(owner,peer).first();
const publicProfile=(row)=>{
  if(!row)return null;
  return {accountId:row.accountId,handle:row.handle,displayName:row.displayName};
};
const publicMessage=(row)=>({
  id:row.id,threadId:row.threadId,senderId:row.senderId,senderName:row.senderName??null,
  text:row.text,createdAt:Number(row.createdAt)
});
const threadView=async(env,row,accountId)=>{
  const members=await env.DB.prepare(
    'SELECT m.account_id AS accountId,p.handle,p.display_name AS displayName FROM messenger_members m '+
    'LEFT JOIN messenger_profiles p ON p.account_id=m.account_id WHERE m.thread_id=? ORDER BY m.joined_at')
    .bind(row.id).all();
  return {
    id:row.id,kind:row.kind,title:row.title,createdBy:row.createdBy,
    createdAt:Number(row.createdAt),updatedAt:Number(row.updatedAt),
    lastText:row.lastText??null,lastSender:row.lastSender??null,
    lastAt:row.lastAt==null?null:Number(row.lastAt),unread:Number(row.unread||0),
    members:(members.results||[]).map(x=>({accountId:x.accountId,handle:x.handle,displayName:x.displayName}))
  };
};
const ensureDirect=async(env,owner,peer)=>{
  const existing=await env.DB.prepare(
    "SELECT t.id,t.kind,t.title,t.created_by AS createdBy,t.created_at AS createdAt,t.updated_at AS updatedAt FROM messenger_threads t "+
    "JOIN messenger_members a ON a.thread_id=t.id AND a.account_id=? "+
    "JOIN messenger_members b ON b.thread_id=t.id AND b.account_id=? "+
    "WHERE t.kind='direct' LIMIT 1").bind(owner,peer).first();
  if(existing)return existing;
  const peerProfile=await profileRow(env,peer);
  const id=crypto.randomUUID(),t=now(),title=publicProfile(peerProfile)?.displayName||'DOMINION 사용자';
  await env.DB.prepare('INSERT INTO messenger_threads(id,kind,title,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?)')
    .bind(id,'direct',title,owner,t,t).run();
  await env.DB.prepare('INSERT INTO messenger_members(thread_id,account_id,joined_at,last_read_at) VALUES(?,?,?,?)')
    .bind(id,owner,t,0).run();
  await env.DB.prepare('INSERT INTO messenger_members(thread_id,account_id,joined_at,last_read_at) VALUES(?,?,?,?)')
    .bind(id,peer,t,0).run();
  return {id,kind:'direct',title,createdBy:owner,createdAt:t,updatedAt:t};
};
async function listFriends(env,user){
  const rows=await env.DB.prepare(
    'SELECT f.owner_id AS ownerId,f.peer_id AS peerId,f.status,'+
    'p.handle,p.display_name AS displayName FROM messenger_friends f '+
    'LEFT JOIN messenger_profiles p ON p.account_id=f.peer_id WHERE f.owner_id=?').bind(user.id).all();
  const incoming=await env.DB.prepare(
    "SELECT f.owner_id AS ownerId,f.status,p.handle,p.display_name AS displayName FROM messenger_friends f "+
    "LEFT JOIN messenger_profiles p ON p.account_id=f.owner_id WHERE f.peer_id=? AND f.status='pending'")
    .bind(user.id).all();
  return {
    accepted:(rows.results||[]).filter(x=>x.status==='accepted').map(x=>({
      accountId:x.peerId,handle:x.handle,displayName:x.displayName,status:x.status})),
    outgoing:(rows.results||[]).filter(x=>x.status==='pending').map(x=>({
      accountId:x.peerId,handle:x.handle,displayName:x.displayName,status:x.status})),
    incoming:(incoming.results||[]).map(x=>({
      accountId:x.ownerId,handle:x.handle,displayName:x.displayName,status:x.status}))
  };
}
async function listThreads(env,user){
  const rows=await env.DB.prepare(
    "SELECT t.id,t.kind,t.title,t.created_by AS createdBy,t.created_at AS createdAt,t.updated_at AS updatedAt,"+
    "(SELECT m.text FROM messenger_messages m WHERE m.thread_id=t.id ORDER BY m.created_at DESC,m.id DESC LIMIT 1) AS lastText,"+
    "(SELECT m.sender_id FROM messenger_messages m WHERE m.thread_id=t.id ORDER BY m.created_at DESC,m.id DESC LIMIT 1) AS lastSender,"+
    "(SELECT m.created_at FROM messenger_messages m WHERE m.thread_id=t.id ORDER BY m.created_at DESC,m.id DESC LIMIT 1) AS lastAt,"+
    "(SELECT COUNT(*) FROM messenger_messages m WHERE m.thread_id=t.id AND m.sender_id<>? AND m.created_at>mem.last_read_at) AS unread "+
    "FROM messenger_threads t JOIN messenger_members mem ON mem.thread_id=t.id "+
    "WHERE mem.account_id=? ORDER BY t.updated_at DESC LIMIT 100").bind(user.id,user.id).all();
  const out=[];
  for(const row of rows.results||[])out.push(await threadView(env,row,user.id));
  return out;
}
async function messages(env,user,thread,before){
  if(!await membership(env,thread,user.id))return null;
  let cursor=null;
  if(before){
    if(!isId(before))return null;
    cursor=await env.DB.prepare('SELECT created_at,id FROM messenger_messages WHERE id=? AND thread_id=?')
      .bind(before,thread).first();
    if(!cursor)return null;
  }
  const rows=cursor?
    await env.DB.prepare(
      'SELECT m.id,m.thread_id AS threadId,m.sender_id AS senderId,m.text,m.created_at AS createdAt,'+
      'p.display_name AS senderName FROM messenger_messages m LEFT JOIN messenger_profiles p ON p.account_id=m.sender_id '+
      'WHERE m.thread_id=? AND (m.created_at<? OR (m.created_at=? AND m.id<?)) ORDER BY m.created_at DESC,m.id DESC LIMIT 100')
      .bind(thread,cursor.created_at,cursor.created_at,cursor.id).all():
    await env.DB.prepare(
      'SELECT m.id,m.thread_id AS threadId,m.sender_id AS senderId,m.text,m.created_at AS createdAt,'+
      'p.display_name AS senderName FROM messenger_messages m LEFT JOIN messenger_profiles p ON p.account_id=m.sender_id '+
      'WHERE m.thread_id=? ORDER BY m.created_at DESC,m.id DESC LIMIT 100').bind(thread).all();
  const list=(rows.results||[]).map(publicMessage).reverse();
  return {threadId:thread,accountId:user.id,messages:list,
    nextBefore:list.length===100?list.first().id:null};
}
async function notifyThread(env,thread,sender,text){
  try{
    const rows=await env.DB.prepare(
      "SELECT p.token FROM assistant_push p JOIN app_sessions s ON s.id=p.app_session_id "+
      "JOIN messenger_members mem ON mem.account_id=p.account_id "+
      "WHERE mem.thread_id=? AND p.account_id<>? AND s.status='approved' AND s.expires_at>? LIMIT 20")
      .bind(thread,sender,now()).all();
    if(rows.results?.length)await deliverPush(env,sender,
      rows.results.map(x=>x.token),'새 DOMINION 메시지가 도착했습니다.','messenger',{});
  }catch{/* push is best effort; D1 history is authoritative */}
}
async function sendMessage(env,user,thread,value){
  if(!await membership(env,thread,user.id))return null;
  if(!cleanText(value.text))return fail('Invalid message',400);
  if(typeof value.client_id!=='string'||!isId(value.client_id))return fail('Invalid client message id',400);
  const existing=await env.DB.prepare(
    'SELECT id,thread_id AS threadId,sender_id AS senderId,text,created_at AS createdAt FROM messenger_messages WHERE sender_id=? AND client_id=?')
    .bind(user.id,value.client_id).first();
  if(existing)return reply(publicMessage(existing));
  const id=crypto.randomUUID(),t=now();
  try{
    await env.DB.prepare('INSERT INTO messenger_messages(id,thread_id,sender_id,client_id,text,created_at) VALUES(?,?,?,?,?,?)')
      .bind(id,thread,user.id,value.client_id,value.text.trim(),t).run();
    await env.DB.prepare('UPDATE messenger_threads SET updated_at=? WHERE id=?').bind(t,thread).run();
  }catch{
    const duplicate=await env.DB.prepare(
      'SELECT id,thread_id AS threadId,sender_id AS senderId,text,created_at AS createdAt FROM messenger_messages WHERE sender_id=? AND client_id=?')
      .bind(user.id,value.client_id).first();
    return duplicate?reply(publicMessage(duplicate)):fail('Message not stored',409);
  }
  await notifyThread(env,thread,user.id,value.text.trim());
  return reply({id,threadId:thread,senderId:user.id,text:value.text.trim(),createdAt:t},201);
}
export async function collectMessengerEvents(env,user,after,limit=100){
  const rows=await env.DB.prepare(
    'SELECT m.id,m.thread_id AS threadId,m.sender_id AS senderId,m.text,m.created_at AS createdAt,'+
    'p.display_name AS senderName FROM messenger_messages m '+
    'JOIN messenger_members mem ON mem.thread_id=m.thread_id AND mem.account_id=? '+
    'LEFT JOIN messenger_profiles p ON p.account_id=m.sender_id '+
    'WHERE m.created_at>=? ORDER BY m.created_at,m.id LIMIT ?').bind(user.id,after,limit).all();
  return (rows.results||[]).map(publicMessage);
}
async function eventStream(env,user,request){
  const afterRaw=new URL(request.url).searchParams.get('after')||'0';
  const after=Number(afterRaw);
  if(!Number.isSafeInteger(after)||after<0)return fail('Invalid cursor',400);
  let cursor=after;
  const encoder=new TextEncoder();
  const stream=new ReadableStream({
    async start(controller){
      const send=x=>controller.enqueue(encoder.encode(x));
      send('retry: 2000\n\n');
      const deadline=Date.now()+25_000;
      try{
        while(Date.now()<deadline){
          const events=await collectMessengerEvents(env,user,cursor);
          for(const event of events){
            cursor=Math.max(cursor,event.createdAt);
            send('event: message\ndata: '+JSON.stringify(event)+'\n\n');
          }
          if(!events.length)send(': ping\n\n');
          await new Promise(r=>setTimeout(r,2000));
        }
      }catch{
        send('event: error\ndata: {"error":"stream_interrupted"}\n\n');
      }finally{controller.close()}
    }
  });
  return new Response(stream,{headers:{
    'content-type':'text/event-stream; charset=utf-8','cache-control':'no-store',
    'x-accel-buffering':'no','connection':'keep-alive'
  }});
}
export async function messengerRoute(request,env,user){
  if(!user?.id)return fail('Unauthorized',401);
  const path=new URL(request.url).pathname;
  if(path==='/v1/messenger/profile'&&request.method==='GET'){
    return reply({profile:publicProfile(await profileRow(env,user.id))});
  }
  if(path==='/v1/messenger/profile'&&request.method==='PUT'){
    const value=await body(request,2048);if(!value)return fail('Invalid JSON',400);
    const handle=text(value.handle).toLowerCase(),display=text(value.display_name);
    if(!handleRe.test(handle)||!cleanName(display))return fail('Invalid handle or display name',400);
    try{
      await env.DB.prepare(
        'INSERT INTO messenger_profiles(account_id,handle,display_name,updated_at) VALUES(?,?,?,?) '+
        'ON CONFLICT(account_id) DO UPDATE SET handle=excluded.handle,display_name=excluded.display_name,updated_at=excluded.updated_at')
        .bind(user.id,handle,display,now()).run();
    }catch{return fail('Handle already in use',409)}
    return reply({profile:{accountId:user.id,handle,displayName:display}});
  }
  if(path==='/v1/messenger/directory'&&request.method==='GET'){
    const q=text(new URL(request.url).searchParams.get('q')).toLowerCase();
    if(q.length>40)return fail('Invalid query',400);
    const rows=await env.DB.prepare(
      'SELECT account_id AS accountId,handle,display_name AS displayName FROM messenger_profiles '+
      'WHERE account_id<>? AND (handle LIKE ? OR lower(display_name) LIKE ?) ORDER BY handle LIMIT 20')
      .bind(user.id,q?q+'%':'%',q?q+'%':'%').all();
    return reply({profiles:(rows.results||[]).map(publicProfile)});
  }
  if(path==='/v1/messenger/friends'&&request.method==='GET')
    return reply(await listFriends(env,user));
  if(path==='/v1/messenger/friends/request'&&request.method==='POST'){
    const value=await body(request,2048);if(!value)return fail('Invalid JSON',400);
    const peer=text(value.account_id);
    if(!account(peer)||peer===user.id)return fail('Invalid account',400);
    if(!await profileRow(env,peer))return fail('Profile not found',404);
    const blocked=await friend(env,peer,user.id);
    if(blocked?.status==='blocked')return fail('Friend request blocked',403);
    const reverse=await friend(env,user.id,peer);
    const t=now();
    if(reverse?.status==='accepted')return reply({status:'accepted'});
    if(reverse?.status==='pending'){
      await env.DB.prepare("UPDATE messenger_friends SET status='accepted',updated_at=? WHERE owner_id=? AND peer_id=?")
        .bind(t,user.id,peer).run();
      await env.DB.prepare("UPDATE messenger_friends SET status='accepted',updated_at=? WHERE owner_id=? AND peer_id=?")
        .bind(t,peer,user.id).run();
      const thread=await ensureDirect(env,user.id,peer);
      return reply({status:'accepted',threadId:thread.id});
    }
    await env.DB.prepare(
      "INSERT INTO messenger_friends(owner_id,peer_id,status,created_at,updated_at) VALUES(?,?,'pending',?,?) "+
      "ON CONFLICT(owner_id,peer_id) DO UPDATE SET status='pending',updated_at=excluded.updated_at")
      .bind(user.id,peer,t,t).run();
    return reply({status:'pending'});
  }
  if(path==='/v1/messenger/friends/respond'&&request.method==='POST'){
    const value=await body(request,2048);if(!value)return fail('Invalid JSON',400);
    const peer=text(value.account_id),accept=value.accept===true;
    if(!account(peer)||peer===user.id)return fail('Invalid account',400);
    const incoming=await env.DB.prepare(
      "SELECT status FROM messenger_friends WHERE owner_id=? AND peer_id=? AND status='pending'").bind(peer,user.id).first();
    if(!incoming)return fail('No pending request',404);
    const t=now();
    if(accept){
      await env.DB.prepare("UPDATE messenger_friends SET status='accepted',updated_at=? WHERE owner_id=? AND peer_id=?")
        .bind(t,peer,user.id).run();
      await env.DB.prepare("UPDATE messenger_friends SET status='accepted',updated_at=? WHERE owner_id=? AND peer_id=?")
        .bind(t,user.id,peer).run();
      const thread=await ensureDirect(env,user.id,peer);
      return reply({status:'accepted',threadId:thread.id});
    }
    await env.DB.prepare('DELETE FROM messenger_friends WHERE owner_id=? AND peer_id=? AND status=\'pending\'')
      .bind(peer,user.id).run();
    return reply({status:'declined'});
  }
  if(path==='/v1/messenger/threads'&&request.method==='GET')
    return reply({threads:await listThreads(env,user)});
  if(path==='/v1/messenger/threads'&&request.method==='POST'){
    const value=await body(request,4096);if(!value)return fail('Invalid JSON',400);
    const kind=value.kind;if(kind!=='direct'&&kind!=='group')return fail('Invalid thread kind',400);
    const members=[...new Set((Array.isArray(value.members)?value.members:[]).filter(account))];
    if(kind==='direct'&&members.length!==1)return fail('Direct thread needs one peer',400);
    if(kind==='group'&&(members.length<1||members.length>29))return fail('Group needs 1-29 peers',400);
    for(const peer of members){
      if(peer===user.id)return fail('Invalid member',400);
      const relation=await friend(env,user.id,peer);
      if(relation?.status!=='accepted')return fail('All members must be accepted friends',403);
    }
    if(kind==='direct')return reply({thread:await threadView(env,await ensureDirect(env,user.id,members[0]),user.id)});
    const title=text(value.title).slice(0,80);
    if(!title)return fail('Group title required',400);
    const id=crypto.randomUUID(),t=now();
    await env.DB.prepare('INSERT INTO messenger_threads(id,kind,title,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?)')
      .bind(id,'group',title,user.id,t,t).run();
    for(const member of [user.id,...members]){
      await env.DB.prepare('INSERT INTO messenger_members(thread_id,account_id,joined_at,last_read_at) VALUES(?,?,?,0)')
        .bind(id,member,t).run();
    }
    return reply({thread:await threadView(env,{id,kind:'group',title,createdBy:user.id,createdAt:t,
      updatedAt:t,lastText:null,lastSender:null,lastAt:null,unread:0},user.id)},201);
  }
  const thread=/^\/v1\/messenger\/threads\/([a-f0-9-]{36})\/(messages|read|members)$/.exec(path);
  if(thread&&isId(thread[1])){
    const id=thread[1],action=thread[2];
    if(action==='messages'&&request.method==='GET'){
      const result=await messages(env,user,id,new URL(request.url).searchParams.get('before'));
      return result?reply(result):fail('Thread not found',404);
    }
    if(action==='messages'&&request.method==='POST'){
      const value=await body(request,8192);if(!value)return fail('Invalid JSON',400);
      return sendMessage(env,user,id,value)||fail('Thread not found',404);
    }
    if(action==='read'&&request.method==='POST'){
      if(!await membership(env,id,user.id))return fail('Thread not found',404);
      await env.DB.prepare('UPDATE messenger_members SET last_read_at=? WHERE thread_id=? AND account_id=?')
        .bind(now(),id,user.id).run();
      return reply({ok:true});
    }
    if(action==='members'&&request.method==='GET'){
      const row=await env.DB.prepare(
        'SELECT id,kind,title,created_by AS createdBy,created_at AS createdAt,updated_at AS updatedAt FROM messenger_threads WHERE id=?')
        .bind(id).first();
      if(!row||!await membership(env,id,user.id))return fail('Thread not found',404);
      const view=await threadView(env,row,user.id);
      return reply({members:view.members||[]});
    }
  }
  if(path==='/v1/messenger/events'&&request.method==='GET')
    return eventStream(env,user,request);
  return fail('Not found',404);
}
