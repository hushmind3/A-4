"""Explicit user-approved outbound call API; run behind HTTPS, no signup changes."""
import asyncio
import hmac
import os
import re
import uuid
from fastapi import FastAPI, Header, HTTPException, Depends
from pydantic import BaseModel, ConfigDict, Field
from livekit import api
import journal

app=FastAPI(docs_url=None,redoc_url=None,openapi_url=None)
TOKEN=os.environ["DOMINION_PHONE_TOKEN"]
if len(TOKEN)<40:
    raise RuntimeError("Use a randomly generated token of at least 40 characters")
ALLOWED={s.strip() for s in os.environ["DOMINION_PHONE_ALLOWED_NUMBERS"].split(",") if s.strip()}
if not ALLOWED or any(not re.fullmatch(r"\+82[1-9][0-9]{7,10}", n) for n in ALLOWED):
    raise RuntimeError("Configure explicitly authorized Korean business phone numbers in E.164")
AGENT="dominion-phone"

async def auth(authorization:str=Header(default="")):
    if not hmac.compare_digest(authorization, "Bearer "+TOKEN):
        raise HTTPException(401,"unauthorized")

class CallRequest(BaseModel):
    model_config=ConfigDict(extra="forbid")
    id:uuid.UUID
    number:str=Field(min_length=10,max_length=14)
    goal:str=Field(min_length=3,max_length=800)
    approved:bool

def public(row):
    if not row: raise HTTPException(404,"call_not_found")
    return {k:row[k] for k in ("id","status","created","updated","answered","consent","summary","evidence","reason")}

@app.get("/v1/calls/{call_id}", dependencies=[Depends(auth)])
async def status(call_id:uuid.UUID):
    return public(journal.get(str(call_id)))

@app.post("/v1/calls", dependencies=[Depends(auth)])
async def start(request:CallRequest):
    call_id=str(request.id)
    if not request.approved or request.number not in ALLOWED or not request.goal.strip():
        raise HTTPException(400,"unapproved_destination_or_goal")
    if any(ord(c)<32 and c!="\n" for c in request.goal):
        raise HTTPException(400,"invalid_goal")
    try:
        fresh=journal.create(call_id,request.number,request.goal.strip())
    except ValueError as exc:
        raise HTTPException(409,str(exc)) from None
    if fresh:
        # Dispatch contains only a journal ID. Worker verifies room and claims
        # the row atomically before making any billable outbound call.
        lk=api.LiveKitAPI()
        try:
            await asyncio.wait_for(lk.agent_dispatch.create_dispatch(api.CreateAgentDispatchRequest(
                agent_name=AGENT,room=journal.room(call_id),metadata=call_id)),15)
        except Exception:
            journal.transition(call_id,("queued",),"unknown",reason="dispatch_unconfirmed_no_replay")
        finally:
            await lk.aclose()
    return public(journal.get(call_id))

@app.post("/v1/calls/{call_id}/cancel",dependencies=[Depends(auth)])
async def cancel(call_id:uuid.UUID):
    key=str(call_id)
    row=journal.get(key)
    if not row: raise HTTPException(404,"call_not_found")
    if row["status"] in journal.TERMINAL:return public(row)
    if journal.transition(key,("queued",),"cancelled",reason="cancelled_before_claim"):
        return public(journal.get(key))
    journal.transition(key,("dialing","connected"),"cancel_requested",reason="owner_cancelled")
    lk=api.LiveKitAPI()
    try:
        await asyncio.wait_for(lk.room.delete_room(api.DeleteRoomRequest(room=journal.room(key))),10)
    except Exception:
        pass  # Cancellation stays requested; do not claim successful hangup.
    finally:
        await lk.aclose()
    return public(journal.get(key))
