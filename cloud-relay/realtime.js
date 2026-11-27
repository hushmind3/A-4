// Cloud realtime voice room. LiveKit carries full-duplex media; the hosted
// LiveKit Agents worker does streaming STT/turn detection/LLM/TTS. No PCM is
// stored by this Worker and no local server is required by the Android app.
import {b64url} from './token_crypto.js';
const now=()=>Math.floor(Date.now()/1000);
const reply=(x,status=200)=>new Response(JSON.stringify(x),{status,
  headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}});
const fail=(error,status=400)=>reply({error},status);
const enc=s=>b64url(new TextEncoder().encode(s));
async function jwt(env,payload){
  const header=enc(JSON.stringify({alg:'HS256',typ:'JWT'}));
  const body=enc(JSON.stringify(payload));
  const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(env.LIVEKIT_API_SECRET),
    {name:'HMAC',hash:'SHA-256'},false,['sign']);
  const signature=b64url(await crypto.subtle.sign('HMAC',key,
    new TextEncoder().encode(header+'.'+body)));
  return header+'.'+body+'.'+signature;
}
const configured=env=>typeof env.LIVEKIT_URL==='string'&&/^wss:\/\//.test(env.LIVEKIT_URL)&&
  typeof env.LIVEKIT_API_KEY==='string'&&env.LIVEKIT_API_KEY.length>=8&&
  typeof env.LIVEKIT_API_SECRET==='string'&&env.LIVEKIT_API_SECRET.length>=16;
export async function realtimeRoute(request,env,user){
  if(!user?.id)return fail('Unauthorized',401);
  const path=new URL(request.url).pathname;
  if(path==='/v1/realtime/capabilities'&&request.method==='GET'){
    return reply({enabled:configured(env),video:env.LIVEKIT_VIDEO==='1',
      agent:env.LIVEKIT_AGENT_NAME||'dominion-voice',full_duplex:true});
  }
  if(path!=='/v1/realtime/rooms'||request.method!=='POST')return fail('Not found',404);
  if(!configured(env))return fail('Realtime voice not configured',503);
  const raw=await request.text();if(raw.length>4096)return fail('Request too large',413);
  let value;try{value=JSON.parse(raw)}catch{return fail('Invalid JSON',400)}
  const conversationId=typeof value?.roomId==='string'?value.roomId:'';
  const sessionId=typeof value?.sessionId==='string'?value.sessionId:'';
  const callId=typeof value?.callId==='string'?value.callId:'';
  if(!conversationId||conversationId.length>160||!sessionId||sessionId.length>160||
    !callId||callId.length>160)return fail('Invalid call identity',400);
  const video=value?.video===true&&env.LIVEKIT_VIDEO==='1';
  const room='dominion-voice-'+crypto.randomUUID();
  const identity='mobile-'+crypto.randomUUID().slice(0,18);
  const agentIdentity=env.LIVEKIT_AGENT_NAME||'dominion-voice';
  const exp=now()+3600;
  const token=await jwt(env,{
    iss:env.LIVEKIT_API_KEY,sub:identity,exp,nbf:now(),name:'DOMINION Mobile',
    metadata:JSON.stringify({accountId:user.id,conversationId,sessionId,callId}),
    video:{roomJoin:true,room,canPublish:true,canSubscribe:true,canPublishData:true,
      canSubscribeMetrics:false}
  });
  if(env.LIVEKIT_AUTO_DISPATCH!=='1'){
    const serverToken=await jwt(env,{iss:env.LIVEKIT_API_KEY,sub:env.LIVEKIT_API_KEY,
      exp:now()+60,video:{roomAdmin:true,room,roomCreate:true}});
    const http=env.LIVEKIT_URL.replace(/^wss:/,'https:').replace(/^ws:/,'http:').replace(/\/$/,'');
    let dispatched=false;
    try{
      const response=await fetch(http+'/twirp/livekit.AgentDispatchService/CreateDispatch',{
        method:'POST',headers:{authorization:'Bearer '+serverToken,'content-type':'application/json'},
        body:JSON.stringify({room,agent_name:agentIdentity,
          metadata:JSON.stringify({accountId:user.id,conversationId,sessionId,callId})}),
        signal:AbortSignal.timeout(12000)
      });
      dispatched=response.ok;
    }catch{/* explicit failure below */}
    if(!dispatched)return fail('Voice agent dispatch unavailable',503);
  }
  return reply({accountId:user.id,roomId:conversationId,sessionId,callId,agentIdentity,
    videoAllowed:video,media:{room,token,url:env.LIVEKIT_URL,expires_at:exp*1000}},201);
}
