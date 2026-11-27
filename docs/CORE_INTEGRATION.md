# Mobile core integration status

A-4 uses the native DOMINION room, job, attachment, approval, memory and SSE endpoints. Google pairing uses the deployed DOMINION Cloud app-session endpoint, whose credential scope is free inference. A cloud app session is not an engine owner credential. The Cloudflare inference worker is not a native room server.

Implemented in A-4:
- Explicit local account-to-engine association, Android Keystore encrypted, same-account restore after verified approval; manual disconnect pauses auto-restore.
- Existing HTTPS/Cloudflare Access credentials, no credential transmission to an unverified origin.
- Native transcript and job synchronization, session reset protection, SSE invalidation, periodic recovery and credential-scoped job monitoring. WorkManager's 15-minute minimum interval and OS scheduling apply; it is not FCM push.
- User-started microphone foreground service, Whisper/Piper, durable native room messages, notifications to stop, skip or review. Stops recording after three silent intervals or user stop; process death does not auto-restart the microphone. Long-running jobs remain with DOMINION.
- Cloudflare free models registered through the existing native provider endpoint; room selection controls actual native model execution.

Deployment dependencies (not available merely because the existing Google login works):
1. A running Cloudflare Tunnel or independently configured HTTPS route to the user's running DOMINION server. Tunnel creation alone does not connect a local machine: cloudflared must run on that host. Existing Cloud app credentials do not provide a registry of engines. A new-device automatic discovery protocol would require an authenticated server-side engine registry, desktop device enrollment and revocable mobile grants, not copying the owner token into D1.
2. True duplex AI call: a native room-bound media grant, private LiveKit room/participant authorization, server agent bridging utterances into the same native DOMINION jobs, and verified speech/video capabilities. Existing branch1 voice-agent directly calls a model and is not that room-bound harness.
3. Push delivery when the app is terminated requires an authenticated push registration, server event dispatch and revocation. Background polling is implemented meanwhile.
4. Multi-account friends/messages, account removal, institution actions and fresh identity verification require the previously documented server contracts; legacy single-owner /v1 endpoints must not be treated as real multi-account operations.

No DOMINION branch or Cloudflare deployment is modified by this A-4 commit.
