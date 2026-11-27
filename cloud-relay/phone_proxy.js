// Operator-configured phone bridge proxy. Consumers never enter a phone server,
// token, SIP trunk or provider key in the app.
const reply=(x,status=200)=>new Response(JSON.stringify(x),{status,
  headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store'}});
const fail=(error,status=400)=>reply({error},status);
export async function phoneProxyRoute(request,env,user){
  if(!user?.id)return fail('Unauthorized',401);
  if(!env.PHONE_BRIDGE_URL||!env.PHONE_BRIDGE_TOKEN)
    return fail('Phone service not configured',503);
  const source=new URL(request.url);
  const path=source.pathname.replace(/^\/v1\/phone/,'');
  if(!/^(\/health|\/calls(\/[a-f0-9-]{36}(\/cancel|\/instruction)?)?)$/.test(path))return fail('Not found',404);
  const target=env.PHONE_BRIDGE_URL.replace(/\/$/,'')+'/v1'+path;
  const headers={authorization:'Bearer '+env.PHONE_BRIDGE_TOKEN,
    'content-type':request.headers.get('content-type')||'application/json'};
  const response=await fetch(target,{method:request.method,headers,
    body:['GET','HEAD'].includes(request.method)?undefined:await request.text(),
    signal:AbortSignal.timeout(30000)});
  const body=await response.text();
  return new Response(body,{status:response.status,headers:{
    'content-type':response.headers.get('content-type')||'application/json',
    'cache-control':'no-store'}});
}
