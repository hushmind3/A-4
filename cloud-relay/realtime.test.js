import test from 'node:test';
import assert from 'node:assert/strict';
import {realtimeRoute} from './realtime.js';

const env={LIVEKIT_URL:'wss://voice.example.test',LIVEKIT_API_KEY:'key_12345678',
  LIVEKIT_API_SECRET:'secret_1234567890',LIVEKIT_AUTO_DISPATCH:'1',
  LIVEKIT_AGENT_NAME:'dominion-voice',LIVEKIT_VIDEO:'0'};

test('creates a bounded full-duplex LiveKit room token',async()=>{
  const request=new Request('https://dominion.invalid/v1/realtime/rooms',{
    method:'POST',headers:{'content-type':'application/json'},
    body:JSON.stringify({roomId:'conv-1',sessionId:'session-1',callId:'call-1',video:false})
  });
  const response=await realtimeRoute(request,env,{id:'google:alice',scope:'app',capability:'inference'});
  assert.equal(response.status,201);
  const body=await response.json();
  assert.equal(body.agentIdentity,'dominion-voice');
  assert.equal(body.videoAllowed,false);
  assert.equal(body.media.url,'wss://voice.example.test');
  assert.ok(body.media.expires_at>Date.now());
  const payload=JSON.parse(Buffer.from(body.media.token.split('.')[1],'base64url').toString());
  assert.equal(payload.iss,'key_12345678');
  assert.equal(payload.video.roomJoin,true);
  assert.equal(payload.video.canPublish,true);
  assert.equal(payload.video.canSubscribe,true);
  assert.ok(payload.exp*1000>Date.now());
});

test('unconfigured realtime room fails closed',async()=>{
  const request=new Request('https://dominion.invalid/v1/realtime/rooms',{method:'POST',
    body:JSON.stringify({roomId:'c',sessionId:'s',callId:'x',video:false})});
  const response=await realtimeRoute(request,{LIVEKIT_URL:'',LIVEKIT_API_KEY:'',LIVEKIT_API_SECRET:''},
    {id:'google:alice'});
  assert.equal(response.status,503);
});
