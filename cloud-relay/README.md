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
