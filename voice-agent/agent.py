"""DOMINION Cloud full-duplex voice worker (LiveKit Agents).

Media is carried by LiveKit. The worker streams microphone audio into STT,
runs turn detection/LLM/TTS, and publishes synthesized audio back into the
same room. It is intended for a hosted worker (LiveKit Cloud or a container);
the Android app never needs a local server.
"""
import asyncio
import json
import os

from livekit import agents
from livekit.agents import Agent, AgentSession
from livekit.plugins import openai, silero

MODEL = os.getenv("DOMINION_VOICE_MODEL", "gpt-4o-mini")
MODEL_URL = os.getenv("DOMINION_VOICE_MODEL_URL")
MODEL_KEY = os.getenv("DOMINION_VOICE_MODEL_KEY")
STT_MODEL = os.getenv("DOMINION_VOICE_STT_MODEL", "whisper-1")
TTS_MODEL = os.getenv("DOMINION_VOICE_TTS_MODEL", "tts-1")
TTS_VOICE = os.getenv("DOMINION_VOICE_TTS_VOICE", "alloy")
AGENT_NAME = os.getenv("LIVEKIT_AGENT_NAME", "dominion-voice")
INSTRUCTIONS = os.getenv(
    "DOMINION_VOICE_INSTRUCTIONS",
    "당신은 DOMINION의 실시간 음성 비서입니다. 한국어로 짧고 자연스럽게 답하세요. "
    "모르는 사실이나 실행하지 않은 업무를 완료했다고 말하지 마세요. "
    "결제·계약·개인정보 입력은 사용자가 직접 확인해야 합니다.",
)

server = agents.AgentServer()


async def on_request(req: agents.JobRequest):
    # The Android client verifies this exact participant identity before it
    # publishes the microphone, so the agent must not silently change it.
    await req.accept(name="DOMINION Voice", identity="dominion-voice",
                     attributes={"dominion.role": "voice", "dominion.protocol": "realtime/1"})


@server.rtc_session(agent_name=AGENT_NAME, on_request=on_request)
async def voice_entry(ctx: agents.JobContext):
    await ctx.connect()
    llm = openai.LLM(model=MODEL, api_key=MODEL_KEY,
                     base_url=MODEL_URL) if MODEL_URL else openai.LLM(model=MODEL)
    session = AgentSession(
        stt=openai.STT(model=STT_MODEL, language="ko"),
        llm=llm,
        tts=openai.TTS(model=TTS_MODEL, voice=TTS_VOICE),
        vad=silero.VAD.load(),
        preemptive_generation=True,
    )
    agent = Agent(instructions=INSTRUCTIONS)
    closed = asyncio.Event()

    @ctx.room.on("disconnected")
    def _on_disconnected(*_args):
        closed.set()

    async def publish(kind: str, text: str):
        if not text:
            return
        try:
            await ctx.room.local_participant.publish_data(
                json.dumps({"type": kind, "text": text[:2000]}, ensure_ascii=False).encode("utf-8"),
                reliable=True,
            )
        except Exception:
            pass

    @session.on("conversation_item_added")
    def _conversation_item(ev):
        item = getattr(ev, "item", None)
        role = getattr(item, "role", "")
        text = getattr(item, "text_content", None) or ""
        if role in {"user", "assistant"} and text:
            asyncio.create_task(publish("transcript", f"{role}: {text}"))

    await session.start(room=ctx.room, agent=agent)
    await session.generate_reply(
        instructions="짧게 자신을 DOMINION 음성 비서라고 소개하고 무엇을 도와드릴지 물어보세요."
    )
    try:
        await asyncio.wait_for(closed.wait(), timeout=3600)
    except asyncio.TimeoutError:
        pass
    finally:
        try:
            await asyncio.wait_for(session.aclose(), timeout=8)
        except Exception:
            pass


if __name__ == "__main__":
    agents.cli.run_app(server)
