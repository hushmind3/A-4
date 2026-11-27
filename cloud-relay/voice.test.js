import test from 'node:test';
import assert from 'node:assert/strict';
import {voiceRoute,voiceInternals} from './voice.js';
const env={AI:{},DB:{}};
const signed={id:'google:alpha',scope:'app',capability:'inference'};
function wav(seconds=1){
 const count=16000*seconds*2;
 const raw=new Uint8Array(44+count);
 const view=new DataView(raw.buffer);
 const part=(start,t)=>{for(let i=0;i<t.length;i++)raw[start+i]=t.charCodeAt(i)};
 part(0,'RIFF');view.setUint32(4,raw.length-8,true);part(8,'WAVE');
 part(12,'fmt ');view.setUint32(16,16,true);view.setUint16(20,1,true);
 view.setUint16(22,1,true);view.setUint32(24,16000,true);
 view.setUint32(28,32000,true);view.setUint16(32,2,true);
 view.setUint16(34,16,true);part(36,'data');view.setUint32(40,count,true);
 return raw;
}
test('rejects unsigned and non-inference app accounts',async()=>{
 for(const user of [null,{id:'a',scope:'app',capability:'assistant_pc'}]){
  const res=await voiceRoute(new Request('https://app/v1/voice/capabilities'),env,user,'/v1/voice/capabilities');
  assert.equal(res.status,403);
 }
});
test('capabilities never pretend Korean voice is available without TTS credentials',async()=>{
 const r=await voiceRoute(new Request('https://app/v1/voice/capabilities'),env,signed,'/v1/voice/capabilities');
 const body=await r.json();
 assert.equal(body.stt,true);assert.equal(body.tts_ko,false);
});
test('WAV input bound to mono PCM, 16 kHz and 20 s',()=>{
 assert.equal(voiceInternals.validWave(wav(1)),true);
 assert.equal(voiceInternals.validWave(wav(20)),true);
 assert.equal(voiceInternals.validWave(wav(21)),false);
 const invalid=wav(1);new DataView(invalid.buffer).setUint32(24,44100,true);
 assert.equal(voiceInternals.validWave(invalid),false);
});
test('invalid voice request never charges inference quota',async()=>{
 const r=await voiceRoute(new Request('https://app/v1/voice/transcriptions',{
   method:'POST',headers:{'content-type':'audio/wav'},body:new Uint8Array(10)}),env,signed,'/v1/voice/transcriptions');
 assert.equal(r.status,400);
 const speech=await voiceRoute(new Request('https://app/v1/voice/speech',{
   method:'POST',headers:{'content-type':'application/json'},
   body:JSON.stringify({text:'안녕하세요',language:'ko'})}),env,signed,'/v1/voice/speech');
 assert.equal(speech.status,503);
});
