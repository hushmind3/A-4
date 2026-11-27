// Account-scoped authenticated browser sessions. Cookie values are encrypted
// with an operator key and never returned to the model or client metadata.
const now=()=>Math.floor(Date.now()/1000);
const reply=(x,status=200)=>new Response(JSON.stringify(x),{status,
  headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}});
const fail=(error,status=400)=>reply({error},status);
const enc=s=>btoa(String.fromCharCode(...new TextEncoder().encode(s)))
  .replace(/=/g,'').replace(/\+/g,'-').replace(/\//g,'_');
const allowedHost=host=>typeof host==='string'&&
  (host==='gov.kr'||host.endsWith('.gov.kr')||host==='go.kr'||host.endsWith('.go.kr')||
   host.endsWith('.neis.go.kr')||host.endsWith('.law.go.kr')||host==='law.go.kr'||
   host.endsWith('.nhis.or.kr')||host.endsWith('.kca.or.kr'));
const key=async env=>{
  const secret=env.BROWSER_SESSION_KEY||env.AGENT_ENCRYPTION_KEY;
  if(!secret)throw Error('Browser session encryption not configured');
  const bytes=Uint8Array.from(atob(secret),x=>x.charCodeAt(0));
  if(bytes.length!==32)throw Error('Browser session key invalid');
  return crypto.subtle.importKey('raw',bytes,'AES-GCM',false,['encrypt','decrypt']);
};
async function seal(env,account,host,plain){
  const iv=crypto.getRandomValues(new Uint8Array(12));
  const bytes=new Uint8Array(await crypto.subtle.encrypt({name:'AES-GCM',iv,
    additionalData:new TextEncoder().encode('browser:'+account+':'+host)},
    await key(env),new TextEncoder().encode(plain)));
  return enc(String.fromCharCode(...iv))+'.'+enc(String.fromCharCode(...bytes));
}
async function unseal(env,account,host,value){
  const [a,b]=String(value||'').split('.');
  if(!a||!b)throw Error('Invalid cookie record');
  const iv=Uint8Array.from(atob(a.replace(/-/g,'+').replace(/_/g,'/')),x=>x.charCodeAt(0));
  const payload=Uint8Array.from(atob(b.replace(/-/g,'+').replace(/_/g,'/')),x=>x.charCodeAt(0));
  if(iv.length!==12)throw Error('Invalid cookie nonce');
  return new TextDecoder().decode(await crypto.subtle.decrypt({name:'AES-GCM',iv,
    additionalData:new TextEncoder().encode('browser:'+account+':'+host)},await key(env),payload));
}
function cookies(host,header){
  return header.split(';').map(part=>{
    const i=part.indexOf('=');if(i<=0)return null;
    const name=part.slice(0,i).trim(),value=part.slice(i+1).trim();
    if(!/^[A-Za-z0-9_\-.]{1,80}$/.test(name)||value.length>4096)return null;
    return {name,value,domain:host,path:'/',httpOnly:true,secure:true,sameSite:'Lax'};
  }).filter(Boolean).slice(0,120);
}
async function save(env,account,host,header){
  const sealed=await seal(env,account,host,header);
  await env.DB.prepare(
    'INSERT INTO browser_sessions(account_id,host,cookie_enc,updated_at) VALUES(?,?,?,?) '+
    'ON CONFLICT(account_id,host) DO UPDATE SET cookie_enc=excluded.cookie_enc,updated_at=excluded.updated_at')
    .bind(account,host,sealed,now()).run();
}
async function read(env,account,url){
  if(!env.BROWSER)return fail('Cloud browser not configured',503);
  let target;try{target=new URL(url)}catch{return fail('Invalid URL',400)}
  if(target.protocol!=='https:'||!allowedHost(target.hostname))return fail('Official HTTPS host required',400);
  const row=await env.DB.prepare(
    'SELECT cookie_enc FROM browser_sessions WHERE account_id=? AND host=?').bind(account,target.hostname).first();
  if(!row)return fail('No saved login session for this host',409);
  const cookieHeader=await unseal(env,account,target.hostname,row.cookie_enc);
  const quotaKey=account+':browser-read:'+new Date().toISOString().slice(0,10);
  const quota=await env.DB.prepare(
    'INSERT INTO free_quota(account_day,used) VALUES(?,1) ON CONFLICT(account_day) DO UPDATE SET used=used+1 WHERE used<? RETURNING used')
    .bind(quotaKey,20).first();
  if(!quota)return fail('Daily browser session quota exhausted',429);
  const puppeteer=await import('@cloudflare/puppeteer');
  const browser=await puppeteer.default.launch(env.BROWSER);
  try{
    const page=await browser.newPage();
    await page.setCookie(...cookies(target.hostname,cookieHeader));
    await page.goto(target.href,{waitUntil:'networkidle2',timeout:25000});
    const title=await page.title();
    const text=await page.evaluate(()=>document.body?document.body.innerText:'');
    const exported=await page.cookies();
    const header=exported.map(x=>x.name+'='+x.value).join('; ');
    if(header.length<=16000)await save(env,account,target.hostname,header);
    return reply({url:target.href,host:target.hostname,title:String(title).slice(0,300),
      text:String(text).slice(0,12000)});
  }catch{return fail('Cloud browser navigation failed',502)}
  finally{runCatching: try{await browser.close()}catch{}}
}
export async function browserSessionRoute(request,env,user){
  if(!user?.id)return fail('Unauthorized',401);
  const path=new URL(request.url).pathname;
  if(path==='/v1/browser/sessions'&&request.method==='GET'){
    const rows=await env.DB.prepare(
      'SELECT host,updated_at AS updatedAt FROM browser_sessions WHERE account_id=? ORDER BY updated_at DESC')
      .bind(user.id).all();
    return reply({sessions:rows.results||[]});
  }
  if(path==='/v1/browser/session'&&request.method==='POST'){
    const raw=await request.text();if(raw.length>17000)return fail('Cookie data too large',413);
    let body;try{body=JSON.parse(raw)}catch{return fail('Invalid JSON',400)}
    let target;try{target=new URL(body?.url)}catch{return fail('Invalid URL',400)}
    if(target.protocol!=='https:'||!allowedHost(target.hostname))return fail('Official HTTPS host required',400);
    const cookie=typeof body?.cookie==='string'?body.cookie.trim():'';
    if(!cookie||cookie.length>16000||/[\u0000-\u0008\u000b\u000c\u000e-\u001f]/.test(cookie))
      return fail('Invalid cookie header',400);
    await save(env,user.id,target.hostname,cookie);
    return reply({host:target.hostname,updated_at:now()});
  }
  if(path==='/v1/browser/read'&&request.method==='POST'){
    const raw=await request.text();if(raw.length>4096)return fail('Request too large',413);
    let body;try{body=JSON.parse(raw)}catch{return fail('Invalid JSON',400)}
    return read(env,user.id,body?.url);
  }
  return fail('Not found',404);
}
