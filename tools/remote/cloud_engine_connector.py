#!/usr/bin/env python3
"""Outbound DOMINION 4317 connector. No inbound port; same Google account approval."""
import argparse
import asyncio
import base64
import hashlib
import http.client
import json
import os
from pathlib import Path
import secrets
import socket
import time
import urllib.request
import urllib.error
import webbrowser
from native_route_policy import allowed

BASE = "https://dominion-cloud.dominion-hushmind.workers.dev"
MAX = 585000
NAME_PREFIX = "DOMINION 4317 · "
STATE = Path.home() / ".dominion" / "a4-cloud-device.json"

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None

def cloud(path, payload=None, auth=None):
    assert path.startswith("/v1/") and not path.startswith("//")
    headers={"Accept":"application/json"}
    if auth: headers["Authorization"]=auth
    if payload is not None: headers["Content-Type"]="application/json"
    req=urllib.request.Request(BASE+path,headers=headers,
        data=json.dumps(payload).encode() if payload is not None else None)
    with urllib.request.build_opener(NoRedirect()).open(req,timeout=20) as r:
        data=r.read(1000001)
        if len(data)>1000000: raise ValueError("Cloud response too large")
        return json.loads(data)

def save(value):
    STATE.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
    # Exclusive temporary file; never write raw tokens into logs or Git.
    tmp=STATE.with_name(STATE.name+"."+secrets.token_hex(8))
    fd=os.open(tmp,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    try:
        with os.fdopen(fd,"w") as f: json.dump(value,f)
        os.replace(tmp,STATE)
    finally:
        if tmp.exists(): tmp.unlink()

def load():
    value=json.loads(STATE.read_text())
    if os.name!="nt" and STATE.stat().st_mode & 0o077:
        raise RuntimeError("Device credential file must be private (chmod 600)")
    return value

def local(method,path,body=None,content_type="",port=4317,limit=MAX):
    if not 1<=port<=65535: raise ValueError("Bad port")
    headers={"Accept":"application/json"}
    token=os.environ.get("DOMINION_ADMIN_TOKEN","")
    if token: headers["X-Dominion-Token"]=token
    if content_type: headers["Content-Type"]=content_type
    c=http.client.HTTPConnection("127.0.0.1",port,timeout=155)
    try:
        c.request(method,path,body=body,headers=headers)
        r=c.getresponse()
        if 300<=r.status<400: raise RuntimeError("Native redirect refused")
        size=r.getheader("Content-Length")
        if size is not None and int(size)>limit: return 413,b'{"error":"Relay response too large"}',"application/json"
        data=r.read(limit+1)
        if len(data)>limit: return 413,b'{"error":"Relay response too large"}',"application/json"
        return r.status,data,r.getheader("Content-Type") or "application/octet-stream"
    finally: c.close()

def probe(port):
    status,raw,_=local("GET","/v1/me",port=port)
    if status!=200 or not json.loads(raw).get("data",{}).get("id"):
        raise RuntimeError("Native DOMINION owner API is not available")
    status,raw,_=local("GET","/api/rooms",port=port)
    if status!=200 or not isinstance(json.loads(raw),list):
        raise RuntimeError("Native room API is not available")
    print("Native DOMINION API verified at 127.0.0.1:"+str(port),flush=True)

def register(name):
    if STATE.exists():
        raise RuntimeError("Existing device registered. Run without --register or revoke first.")
    token="dapp_"+secrets.token_urlsafe(32)
    h=base64.urlsafe_b64encode(hashlib.sha256(token.encode()).digest()).decode().rstrip("=")
    data=cloud("/v1/apps/start",{"app_name":"DOMINION 4317 PC","token_hash":h,"capability":"assistant_pc"})
    ident=data["id"]
    expected=BASE+"/connect?code="+ident
    if data.get("verification_uri")!=expected or data.get("status")!="pending": raise RuntimeError("Invalid approval URL")
    print("Approve this PC using the same Google account:",expected,flush=True)
    webbrowser.open(expected)
    deadline=time.monotonic()+min(600,int(data["expires_in"]))
    auth="Bearer "+token
    while time.monotonic()<deadline:
        r=cloud("/v1/apps/status",auth=auth)
        if r.get("id")!=ident: raise RuntimeError("Pairing mismatch")
        if r.get("status")=="approved": break
        if r.get("status")!="pending": raise RuntimeError("Approval ended")
        time.sleep(3)
    else: raise RuntimeError("Approval expired")
    me=cloud("/v1/me",auth=auth)
    if me.get("app_session_id")!=ident or me.get("capability")!="assistant_pc" or not me.get("account_id"):
        raise RuntimeError("Wrong permission")
    device=secrets.token_urlsafe(32)
    reply=cloud("/v1/assistant/register",{"name":NAME_PREFIX+name[:60],"deviceToken":device},auth)
    if reply.get("account_id")!=me["account_id"] or not reply.get("id"): raise RuntimeError("Registration mismatch")
    save({"id":reply["id"],"token":device,"account":me["account_id"]})
    print("PC registered. Starting outbound connection.",flush=True)

def invoke(request,port):
    b=request.get("body")
    if request.get("route")!="native" or not isinstance(b,dict):
        return 403,{"error":"Only native A-4 routes are enabled"}
    method=b.get("method");path=b.get("path");encoded=b.get("base64");ctype=b.get("contentType")
    if not isinstance(method,str) or not isinstance(path,str) or path.split("?")[0]=="/api/events" or not allowed(method,path):
        return 403,{"error":"Native route not allowed"}
    if not isinstance(ctype,str) or len(ctype)>256 or any(ord(c)<32 or ord(c)>126 for c in ctype):
        return 400,{"error":"Invalid content type"}
    if not isinstance(encoded,str) or len(encoded)>780000:
        return 413,{"error":"Relay request exceeds size limit"}
    try: raw=base64.b64decode(encoded,validate=True)
    except ValueError: return 400,{"error":"Invalid request body"}
    if len(raw)>MAX or (method in ("GET","DELETE") and raw):
        return 400,{"error":"Unexpected request body"}
    try:
        status,content,mime=local(method,path,raw or None,ctype,port)
        return status,{"base64":base64.b64encode(content).decode(),"contentType":mime}
    except Exception:
        return 502,{"error":"Native engine interrupted; check room history before retry"}

async def notifications(config,port):
    # Job completion notices contain no user prompts or task content. Only known
    # transitions since this process started; no historical completion flood.
    known={}
    while True:
        try:
            status,raw,_=await asyncio.to_thread(local,"GET","/api/jobs",None,"",port,2_000_000)
            if status==200:
                rows=json.loads(raw)
                if isinstance(rows,list):
                    for job in rows:
                        ident=job.get("id");st=job.get("status")
                        if not isinstance(ident,str) or len(ident)>120: continue
                        old=known.get(ident)
                        if old in ("queued","running","paused","needs_review") and st in ("completed","failed","cancelled"):
                            try:
                                await asyncio.to_thread(cloud,"/v1/assistant/device/message",
                                    {"id":"job:"+hashlib.sha256((ident+":"+st).encode()).hexdigest(),
                                     "text":"DOMINION 작업 "+{"completed":"완료","failed":"실패","cancelled":"취소"}[st]},
                                    "Device "+config["token"])
                            except Exception:
                                continue
                        known[ident]=st
                    if len(known)>5000:known={j.get("id"):j.get("status") for j in rows[-3000:] if isinstance(j,dict)}
        except Exception: pass
        await asyncio.sleep(15)

async def run(config,port):
    from websockets.asyncio.client import connect
    url=BASE.replace("https://","wss://")+"/v1/assistant/socket"
    delay=1
    notice=asyncio.create_task(notifications(config,port))
    try:
        while True:
            try:
                async with connect(url,additional_headers={"Authorization":"Device "+config["token"]},
                    open_timeout=15,max_size=900000,ping_interval=20,ping_timeout=20,proxy=None) as ws:
                    delay=1
                    print("Cloud relay connected",flush=True)
                    pending=set()
                    semaphore=asyncio.Semaphore(4)
                    async def handle(req):
                        async with semaphore:
                            code,data=await asyncio.to_thread(invoke,req,port)
                            # A disconnected request might already have executed; never replay it.
                            await ws.send(json.dumps({"type":"reply","id":req["id"],"status":code,"data":data},separators=(",",":")))
                    try:
                        async for message in ws:
                            if message=="pong":continue
                            req=json.loads(message)
                            if req.get("type")!="request":continue
                            ident=req.get("id")
                            import uuid
                            if not isinstance(ident,str) or str(uuid.UUID(ident))!=ident:continue
                            if len(pending)>=8:
                                await ws.send(json.dumps({"type":"reply","id":ident,"status":429,"data":{"error":"PC queue full"}}))
                                continue
                            task=asyncio.create_task(handle(req));pending.add(task)
                            def done(t):
                                pending.discard(t)
                                if not t.cancelled():t.exception()
                            task.add_done_callback(done)
                    finally:
                        # Threads may finish local side effects, but no response is retried.
                        for task in pending.copy():task.cancel()
                        await asyncio.gather(*pending,return_exceptions=True)
            except asyncio.CancelledError: raise
            except Exception as exc:
                print("Cloud relay disconnected; reconnecting:",type(exc).__name__,flush=True)
            await asyncio.sleep(delay);delay=min(delay*2,30)
    finally:
        notice.cancel()
        await asyncio.gather(notice,return_exceptions=True)

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--port",type=int,default=4317)
    parser.add_argument("--name",default=socket.gethostname())
    parser.add_argument("--register",action="store_true")
    parser.add_argument("--revoke",action="store_true")
    args=parser.parse_args()
    if args.revoke:
        cfg=load()
        cloud("/v1/assistant/device/revoke",{}, "Device "+cfg["token"])
        STATE.unlink()
        print("PC remote grant revoked")
        return
    probe(args.port)
    if args.register:register(args.name)
    if not STATE.exists():parser.error("Run with --register once")
    asyncio.run(run(load(),args.port))

if __name__=="__main__":main()
