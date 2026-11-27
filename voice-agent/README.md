# DOMINION Cloud voice worker

Full-duplex LiveKit Agents worker used by the Android `realtime_audio` path.
The Worker in `cloud-relay/realtime.js` creates a private room token and
explicitly dispatches `LIVEKIT_AGENT_NAME` (default `dominion-voice`). The
Android client waits for the exact `dominion-voice` participant before it
publishes the microphone.

Required hosted configuration:

- `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`
- `DOMINION_VOICE_MODEL_URL`, `DOMINION_VOICE_MODEL_KEY`, `DOMINION_VOICE_MODEL`
  (an OpenAI-compatible model endpoint; use the existing DOMINION Cloud model
  gateway with an operator-scoped credential, never a user pairing token)
- `DOMINION_VOICE_STT_MODEL` / `DOMINION_VOICE_TTS_MODEL` and the corresponding
  provider key (`OPENAI_API_KEY` for the default OpenAI plugins)
- `DOMINION_VOICE_TTS_VOICE` (default `alloy`)

Run locally for transport testing with a LiveKit dev server, or deploy this
container to LiveKit Cloud / any container host. The Android product does not
depend on a local worker: `LIVEKIT_URL` in the Cloud Worker must be the hosted
`wss://` endpoint. Actual Korean STT/TTS quality and PSTN media require
provider credentials and real-device testing; a successful room join alone is
not proof of conversational accuracy.

The worker publishes finalized transcript items as reliable LiveKit data
packets with `{"type":"transcript","text":"user: ..."}`. Raw audio is not
persisted by this repository.
