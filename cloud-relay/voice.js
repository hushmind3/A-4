// Account-bound speech inference only. Never store or log raw microphone samples.
const json=(payload,status=200)=>new Response(JSON.stringify(payload),{
  status,headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}
});
const fail=(reason,status)=>json({error:reason},status);
const nowDay=()=>new Date().toISOString().slice(0,10);
const limited=(value,defaultValue,max)=>Number.isSafeInteger(Number(value))?
  Math.max(1,Math.min(max,Number(value))):defaultValue;
const voiceAllowed=user=>Boolean(user?.id && (user.scope!=='app'||user.capability==='inference'));
const voiceCap=env=>({
  stt:Boolean(env.AI),
  tts_en:Boolean(env.AI),
  tts_ko:Boolean(env.GOOGLE_CLOUD_TTS_API_KEY),
  mode:'cloud_only',
  retention:'no_audio_persistence',
  max_audio_seconds:20
});
async function reserve(env,account,kind,limit){
  const key=account+':voice-'+kind+':'+nowDay();
  const row=await env.DB.prepare('INSERT INTO free_quota(account_day,used) VALUES(?,1) ON CONFLICT(account_day) DO UPDATE SET used=used+1 WHERE used<? RETURNING used')
    .bind(key,limit).first();
  return row?{remaining:Math.max(0,limit-row.used),key}:null;
}
async function refund(env,quota){
  await env.DB.prepare('UPDATE free_quota SET used=MAX(0,used-1) WHERE account_day=?')
    .bind(quota.key).run();
}
function validWave(bytes){
  if(bytes.length<44||bytes.length>640044)return false;
  const d=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
  const ascii=(offset,n)=>String.fromCharCode(...bytes.slice(offset,offset+n));
  return ascii(0,4)==='RIFF'&&ascii(8,4)==='WAVE'&&
    ascii(12,4)==='fmt '&&d.getUint32(16,true)===16&&
    d.getUint16(20,true)===1&&d.getUint16(22,true)===1&&
    d.getUint32(24,true)===16000&&d.getUint16(34,true)===16&&
    ascii(36,4)==='data'&&d.getUint32(40,true)===bytes.length-44&&
    d.getUint32(4,true)===bytes.length-8&&bytes.length>=8044;
}
function base64(bytes){
  let binary='';
  for(let offset=0;offset<bytes.length;offset+=8192)
    binary+=String.fromCharCode(...bytes.subarray(offset,offset+8192));
  return btoa(binary);
}
function audioReply(data,remaining){
  return new Response(data,{status:200,headers:{
    'content-type':'audio/mpeg','cache-control':'no-store',
    'x-dominion-voice-remaining':String(remaining),
    'x-content-type-options':'nosniff'
  }});
}
export async function voiceRoute(request,env,user,path){
  if(!voiceAllowed(user))return fail('Voice permission required',403);
  if(path==='/v1/voice/capabilities'&&request.method==='GET')
    return json(voiceCap(env));
  if(request.method!=='POST')return fail('Method not allowed',405);
  if(!env.AI)return fail('Cloud speech service unavailable',503);
  if(path==='/v1/voice/transcriptions'){
    if(request.headers.get('content-type')?.split(';')[0].trim()!=='audio/wav')
      return fail('16 kHz PCM WAV required',415);
    const length=Number(request.headers.get('content-length'));
    if(length>640044)return fail('Speech clip too large',413);
    const buffer=await request.arrayBuffer();
    const audio=new Uint8Array(buffer);
    if(!validWave(audio))return fail('Invalid or oversized mono PCM WAV',400);
    const language=request.headers.get('x-dominion-language')==='en'?'en':'ko';
    const quota=await reserve(env,user.id,'stt',
      limited(env.FREE_VOICE_STT_DAILY,12,200));
    if(!quota)return fail('Daily voice transcription quota exhausted',429);
    try{
      const result=await env.AI.run('@cf/openai/whisper-large-v3-turbo',
        {audio:base64(audio),language,task:'transcribe',vad_filter:true});
      const text=typeof result?.text==='string'?result.text.trim():'';
      if(!text||text.length>5000)throw Error('No valid transcript');
      return json({text,language,remaining:quota.remaining});
    }catch{
      await refund(env,quota);
      return fail('Cloud speech transcription failed',502);
    }
  }
  if(path==='/v1/voice/speech'){
    const limit=Number(request.headers.get('content-length'));
    if(limit>4096)return fail('Speech text too large',413);
    const raw=await request.text();
    if(raw.length>4096)return fail('Speech text too large',413);
    let body;try{body=JSON.parse(raw)}catch{return fail('Invalid JSON',400)}
    const text=typeof body?.text==='string'?body.text.trim():'';
    const language=body?.language;
    if(!text||text.length>700||/[\u0000-\u0008\u000b\u000e-\u001f]/.test(text)||
      !['ko','en'].includes(language))return fail('Invalid speech request',400);
    if(language==='ko'&&!env.GOOGLE_CLOUD_TTS_API_KEY)
      return fail('Korean cloud voice not configured',503);
    const quota=await reserve(env,user.id,'tts',
      limited(env.FREE_VOICE_TTS_DAILY,12,200));
    if(!quota)return fail('Daily voice synthesis quota exhausted',429);
    try{
      if(language==='ko'){
        const endpoint='https://texttospeech.googleapis.com/v1/text:synthesize';
        const response=await fetch(endpoint,{
          method:'POST',
          headers:{'content-type':'application/json',
            'x-goog-api-key':env.GOOGLE_CLOUD_TTS_API_KEY},
          body:JSON.stringify({
            input:{text},
            voice:{languageCode:'ko-KR',ssmlGender:'NEUTRAL'},
            audioConfig:{audioEncoding:'MP3'}
          }),signal:AbortSignal.timeout(15000)
        });
        if(!response.ok)throw Error('Korean voice upstream error');
        const data=await response.json();
        if(typeof data?.audioContent!=='string'||data.audioContent.length>4000000)
          throw Error('Invalid audio');
        const decoded=Uint8Array.from(atob(data.audioContent),x=>x.charCodeAt(0));
        if(decoded.length<64||decoded.length>3000000)throw Error('Invalid audio length');
        return audioReply(decoded,quota.remaining);
      }
      const audio=await env.AI.run('@cf/deepgram/aura-1',
        {text,encoding:'mp3'},{returnRawResponse:true});
      if(!(audio instanceof Response)||!audio.ok)throw Error('Cloud TTS failed');
      const buffer=await audio.arrayBuffer();
      if(buffer.byteLength<64||buffer.byteLength>3000000)throw Error('Audio too large');
      return audioReply(buffer,quota.remaining);
    }catch{
      await refund(env,quota);
      return fail('Cloud voice synthesis failed',502);
    }
  }
  return fail('Voice route not found',404);
}
export const voiceInternals={validWave,voiceCap,voiceAllowed};
