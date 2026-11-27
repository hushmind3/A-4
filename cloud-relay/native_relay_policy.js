// Native A-4 transport. No URLs, arbitrary headers or desktop OAuth/admin endpoints.
const ID='[A-Za-z0-9_.:@-]{1,256}';
const routes={
 GET:['/v1/(me|characters|conversations|tasks|schedules|memories|friends|friends/requests|connections|explore|vault|credits/transactions|inbox)','/v1/conversations/'+ID+'(/messages)?','/v1/mobile/(capabilities|peers|messenger/threads)','/v1/mobile/peers/'+ID+'/messages','/v1/mobile/messenger/threads/'+ID+'/(messages|attachments/'+ID+')','/v1/mobile/operations/'+ID,'/api/(memories|settings/owner-profile|oauth/providers|mcp|rooms|approvals|providers|tools|skills|voice/status)','/api/rooms/'+ID+'(/state|/messages)?','/api/(jobs|executions|skills)/'+ID,'/api/executions/'+ID+'/operations','/api/sessions/'+ID+'/messages','/api/attachments/'+ID+'/content','/api/providers/'+ID+'/models'],
 POST:['/api/(memories|settings/owner-profile|mcp|browser/action|approvals|attachments|rooms|providers|skills/import-repo|voice/transcribe|voice/speak)','/api/rooms/'+ID+'(/reset|/messages)?','/api/(oauth/'+ID+'/open|jobs/'+ID+'/cancel|skills/'+ID+')','/v1/mobile/(realtime/calls|operations|voice/aligned)','/v1/mobile/operations/'+ID+'/decision','/v1/mobile/messenger/threads/'+ID+'/attachments','/v1/conversations/'+ID+'/(messages|calls)','/v1/friends/requests/'+ID+'/accept','/v1/connections/'+ID+'/(connect|disconnect)'],
 PATCH:['/v1/me'],DELETE:['/api/(memories|oauth|rooms)/'+ID]
};
const compiled=Object.fromEntries(Object.entries(routes).map(([m,ps])=>[m,ps.map(p=>new RegExp('^'+p+'$'))]));
export function validNative(b){
 if(!b||typeof b!=='object'||Array.isArray(b)||!Object.hasOwn(compiled,b.method)||typeof b.path!=='string'||b.path.length>8192||!b.path.startsWith('/')||/[^\x20-\x7e]/.test(b.path))return false;
 const path=b.path.split('?')[0];
 if(path.includes('%')||path.includes('\\')||path.includes('//')||path.includes('#')||path.split('/').some(x=>x==='.'||x==='..')||!compiled[b.method].some(r=>r.test(path)))return false;
 if(typeof b.contentType!=='string'||b.contentType.length>256||/[^\x20-\x7e]/.test(b.contentType)||typeof b.base64!=='string'||b.base64.length>780000||! /^[A-Za-z0-9+/]*={0,2}$/.test(b.base64)||b.base64.length%4!==0)return false;
 return !(['GET','DELETE'].includes(b.method)&&b.base64.length);
}
