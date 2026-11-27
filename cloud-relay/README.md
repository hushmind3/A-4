# DOMINION Cloud A-4 source overlay

These files extend the existing production DOMINION Cloud Worker. **Do not replace the deployed Worker directly with this directory.** Preserve its current Google app pairing, D1, Workers AI, account quotas, billing and native assistant relay.

## Server contracts — initial production version activated 2026-11-27

| API | Method | Requirement |
|---|---|---|
| /v1/voice/capabilities | GET | Approved mobile app session |
| /v1/voice/transcriptions | POST | Account session, mono 16kHz PCM WAV, up to 20s; per-user STT allowance |
| /v1/voice/speech | POST | Account session, bounded text, per-user TTS allowance; Korean requires Google Cloud TTS key |
| /v1/agent/jobs | POST/GET | Account session, idempotent client UUID, 3 active jobs and 20 submissions per UTC day |
| /v1/agent/jobs/{id} | GET | Same account may inspect its own state/result only |
| /v1/agent/jobs/{id}/cancel | POST | Same account may cancel queued/running tasks |

The hosted job loop currently offers **read-only** clock and allowlisted HTTPS lookup tools, at most 5 model steps. The next source revision adds public Korean/English Wikipedia and GitHub repository discovery, bounded streamed reads and checkpoint context preservation; this revision requires separate deployment and live verification. It is not a general web search engine or browser automation. D1 prompt, checkpoint, and result fields are encrypted with an operator-managed AES-GCM key. This is not end-to-end encryption or an attested credential enclave.

## Deployment and verification checklist

1. Compare/merge this overlay into the **actual deployed** Worker source; never remove existing auth, D1 or account routes.
2. Apply the schema in migrations/0004_agent_jobs.sql on the correct D1 database, after backup and schema review.
3. Configure AGENT_ENCRYPTION_KEY as a 32-byte random secret in base64 and verify secret rotation/recovery strategy.
4. Configure an every-minute Cron trigger to resume queued work; verify requests that completed while the phone was closed.
5. Confirm Workers AI bindings, speech model availability and limits. Korean Cloud TTS requires a separate GOOGLE_CLOUD_TTS_API_KEY secret; do not put this secret into the Android app.
6. Run node tests, actual signed-account isolation tests, source/production route comparison and an Android login -> voice -> job -> app restart -> receipt end-to-end test.

No automatic operator credentials, unrestricted external browser automation or implicit payment/email calls are part of this overlay.

## Deployment record (2026-11-27)

Initial production version 103902fa-a889-4985-bdb9-ffcd308dd4ec was uploaded from commit 683056836bbfa34d241cad96b85a5d8749fb8c7b; existing Worker modules and bindings were compared before deployment. D1 migration 0004, operator-held encryption secret, and one-minute trigger were applied. The prior Worker version 166fa1d1-44c7-4123-b1d3-653678bfd04e remains the rollback target. Do not reset the encryption secret during future uploads. Korean TTS provider is still not configured. Actual signed-account Android/voice/job roundtrip has not been verified; public HTTP access from the development environment was unavailable. The minute trigger is reserved for agent-job recovery, not legacy assistant message retention.

The final active version is **61dbc468-2b36-4d9a-bb3e-cd4370160bf6** (source commit ef1c2a5fc3f315bf1e4057d991ef666e6c8af221, deployment 8a988fa5-078f-476d-8610-7be17e514814, 100% traffic). It preserves the encryption secret, isolates minute recovery from legacy cleanup and enforces the active-job cap atomically. Cloud contract tests for this source passed. Control-plane deployment verification does not establish live client roundtrip success.

## Follow-up source revision — execution and evidence

The Cron runner may continue up to three checkpointed rounds per invocation, with a 45-second start budget and a longer lease for scheduled execution. HTTP submission still starts one step only. A final-step instruction and terminal step-limit status avoid futile retries. Per-tool receipts (including failed observations) are encrypted inside the existing checkpoint and returned only by the account-scoped job detail endpoint. Android exposes those records for completed outputs; old jobs may have no receipts. Observations show actual tool responses, not proof that all model claims are correct. No new database migration, binding or deployment is required to prepare this source revision; deployment remains separate. See Cloudflare Workers duration limits: https://developers.cloudflare.com/workers/platform/limits/.

## Browser Run integration (source revision)

`GET /v1/agent/jobs/capabilities` exposes the actual configured tools to authenticated mobile inference accounts. A Worker Browser Run binding named `BROWSER` enables `browser.read`: a fresh public-page Markdown render using the official `quickAction` API, limited to the existing HTTPS allowlist and same-origin requests. The model cannot provide credentials, cookies, scripts, form submissions or arbitrary browser commands. Tool responses flow through the encrypted job checkpoint and existing Android receipts. Without the binding, the original read/search tools remain available. This is rendered public research, not authenticated interactive browser automation. Before rollout, add the binding through the actual Worker configuration, preserve existing secrets, test a real dynamic page and check request filtering and costs. No production binding or deployment was changed by this revision. Reference: https://developers.cloudflare.com/browser-run/quick-actions/markdown-endpoint/.

Android cloud voice dialog now supports explicit continuous turn-taking after successful playback and manual interruption of the response before recording a new turn. It stops on failed work, permission/session change, missing synthesis, or silence. This uses the existing WAV/MP3 cloud endpoints and remains turn-based, not full-duplex or a background microphone service. Physical-device audio and session checks are required before claiming end-to-end voice success.

## Pickle-style mobile research path

Foreground chat and voice-transcribed messages can now invoke the same account-scoped public search/rendered browsing tools as cloud background jobs. The app discovers `direct_tools` capability before advertising them; older production Worker versions continue to use existing device tools. `browser.links` discovers visible links and constrains them to the current allowed origin. Direct tools have a separate 40/day account quota, count attempted calls, and return explicit failed observations. No user cookies or credentials are passed to the browser. Reference product: https://www.pickle.com/ko (feature behavior, not source code); Browser Run API: https://developers.cloudflare.com/browser-run/quick-actions/links-endpoint/. This is public navigation, not stateful form automation or phone calls. Deployment remains separate.

Chat-to-background delegation: the mobile harness can propose `task.delegate` for explicitly requested background public research. Approval creates one deterministic local task with the exact persisted request before any network submission; the existing cloud queue and recovery mechanism submit/reconcile it. The original chat records local acceptance only, and the task detail shows server acceptance and execution evidence. This is not permission to perform bookings or payments.

## Free-model auto assignment source revision

`dominion/free-auto` ranks configured free models with recently failing models last and may try up to 12 candidates before returning a temporary-unavailable error. A daily or monthly 429 remains authoritative and is returned immediately; quota is refunded for provider/model failures inside `freeProxy`. The Android model picker keeps auto selection visible instead of substituting the first catalog entry. Node contract tests cover ranking only; this source revision has not been deployed to the production Worker and does not prove live model availability.

## Core feature source revision (2026-11-27)

Added but **not deployed** by this revision:

| Area | Source | Required operator configuration |
|---|---|---|
| Messenger | `migrations/0005_messenger.sql`, `messenger.js` | Apply migration 0005; no new binding |
| Realtime voice | `realtime.js`, `../voice-agent/` | `LIVEKIT_URL` (`wss://`), `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `LIVEKIT_AGENT_NAME` (default `dominion-voice`), optional `LIVEKIT_AUTO_DISPATCH=1`, `LIVEKIT_VIDEO=1`; deploy the voice worker separately |
| FCM job push | `assistant_messages.js` (`pushConfig`, `registerAppPush`), `agent_jobs.js` (`notifyJob`) | `ASSISTANT_PUSH_ENABLED=1`, existing `ASSISTANT_FCM_PROJECT_ID/SERVICE_EMAIL/PRIVATE_KEY`, plus public `ASSISTANT_ANDROID_APP_ID`, `ASSISTANT_ANDROID_API_KEY`, `ASSISTANT_FCM_SENDER_ID` |

Messenger identity is always derived from the authenticated app session. Thread creation requires accepted friendships; message sends are idempotent per `(sender_id, client_id)`. The SSE endpoint tails D1 for 25 seconds per connection and the Android client reconnects; it is near-realtime, not a Durable Object push. FCM payloads intentionally omit task prompts, results and message text.

Realtime token issuance fails closed if LiveKit is not configured or explicit dispatch fails. The Android client verifies the exact `dominion-voice` participant before publishing the microphone. A room token alone is not evidence that STT/LLM/TTS or PSTN connectivity works.

## Work-session and browser source revision

Migration 0007 adds `cloud_work_sessions`/`cloud_work_events`. `work_sessions.js` implements durable research, browser, phone and admin work with user questions, answers, resume, parallel queues, events and generic push. The phone adapter uses operator `PHONE_BRIDGE_URL/TOKEN`; the Android app never receives those credentials. `browser_session.js` adds encrypted per-account official-site cookie sessions and Browser Rendering reads; migration 0006 is required. `LIVEKIT_*` and `PHONE_BRIDGE_*` remain operator configuration. This source revision is not deployed; no live Cloud work/browser/phone roundtrip is claimed.

## Production deployment (2026-11-27, A-4 main)

Deployed to the existing `dominion-cloud` Worker as version **77511c72-a446-419b-828d-5c28cd025c30** on 2026-11-27. The previous stable version/rollback target is **61dbc468-2b36-4d9a-bb3e-cd4370160bf6**.

Applied D1 migrations 0005 (messenger), 0006 (encrypted browser sessions), 0007 (work sessions), and 0008 (schedule/retry/deadline). Existing bindings were retained: D1 `DB`, Workers AI `AI`, `ASSISTANT_SOCKET` Durable Object and `ASSISTANT_LINK_ENABLED=1`. Existing secrets were preserved.

Live checks after deployment:

- `/health` returned online with login and models configured.
- A temporary signed app grant returned `/v1/me` identity, the free-model catalog, messenger profile creation, browser-session metadata and Cloud work creation.
- `/v1/phone/health` returned `Phone service not configured`; no phone bridge/SIP secret is present.
- `/v1/realtime/capabilities` returned `enabled:false`; no LiveKit URL/key is present.
- `/v1/push/config` returned `Push not configured`; no Firebase Android/FCM config is present.

The temporary test account and its work/messenger/browser rows were deleted. Live external phone, LiveKit voice, FCM delivery and Browser Rendering remain blocked until their operator credentials/bindings are configured and tested. Work scheduling fields (`scheduled_at`, `repeat`, `condition`, `deadline`, `attempts`, `next_run`) are deployed in the work-session API; document/image generation and arbitrary service adapters are not yet implemented.
