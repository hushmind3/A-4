# Optional DOMINION telephone bridge

This is a real SIP outbound inquiry adapter, not an Android cellular audio
interceptor. It is a separate, single-account, self-hosted process pair; it
does not modify DOMINION Cloud signup or deploy any infrastructure.

Configure both processes with the same private working directory/database
and environment:

- LIVEKIT_URL, LIVEKIT_API_KEY, LIVEKIT_API_SECRET: your existing or separate
  LiveKit SIP-enabled project, with a purchased/authorized outbound trunk.
- DOMINION_SIP_TRUNK: stored outbound trunk ID, configured for Korea.
- DOMINION_PHONE_ALLOWED_NUMBERS: comma-separated explicitly approved E.164
  business phone numbers (+82...), no short/emergency/premium numbers.
- DOMINION_PHONE_TOKEN: random bearer token at least 40 characters; one
  deployment per account. Do not expose the port without HTTPS and a rate limit.
- DOMINION_PHONE_DB: absolute path to a protected local SQLite database.
- DOMINION_PHONE_MODEL_URL, DOMINION_PHONE_MODEL_KEY, DOMINION_PHONE_MODEL:
  existing OpenAI-compatible DOMINION/local/configured inference endpoint.
- DOMINION_PHONE_STT, DOMINION_PHONE_TTS: LiveKit-supported Korean speech
  model descriptors enabled on the project (choose and verify with your provider).

Install with `python -m pip install -r requirements.txt`.
Download VAD assets with `python worker.py download-files`. Run
`python worker.py start` and `uvicorn gateway:app --host 127.0.0.1 --port 8765`
behind your authenticated HTTPS reverse proxy. Configure the exact HTTPS
origin and bearer token in the Android Korean services telephone dialog.
Keep transport/provider logs private, disable audio recording and review
speech-provider retention and Korean telecommunications obligations.

The gateway also exposes authenticated `GET /v1/health`, which returns only
`status` and the count of configured approved numbers. The Android dialog uses
this check before a billable call approval; it never reveals numbers, goals or
call records.

The worker uses a bounded single approved destination, unique persistent
request ID, no auto-redial, operator allowlist, one active call, daily cap
and SIP duration cap. It discloses AI and requires explicit spoken consent
before receiving the inquiry goal. Only a result summary and actual STT
quote are persisted after consent. A report still requires owner review;
answering a call alone does not prove a business result or appointment.
No charges, deployment, live calls, or provider compatibility have been
tested as part of this change. The SIP account and compatible speech
providers are required; no fake success if they are absent.

References:
https://docs.livekit.io/telephony/making-calls/outbound-calls/
https://docs.livekit.io/reference/telephony/sip-api/
https://docs.livekit.io/agents/models/llm/openai-compatible-llms/

## Hosted deployment (no local server)

`Dockerfile` runs both the LiveKit SIP worker and the FastAPI gateway in one
container. Deploy it to any container host that can reach LiveKit Cloud and
your SIP trunk provider, then set the public HTTPS origin and bearer token in
the Android telephone dialog. The app never requires a laptop, PC or local
server. `DOMINION_PHONE_DB` must point at a persistent volume; the worker
registers with LiveKit under `dominion-phone` and the gateway dispatches the
call job. Configure provider secrets only on the container host.
