# DOMINION Cloud A-4 source overlay

These files extend the existing production DOMINION Cloud Worker. **Do not replace the deployed Worker directly with this directory.** Preserve its current Google app pairing, D1, Workers AI, account quotas, billing and native assistant relay.

## New server contracts (source, not yet deployed)

| API | Method | Requirement |
|---|---|---|
| /v1/voice/capabilities | GET | Approved mobile app session |
| /v1/voice/transcriptions | POST | Account session, mono 16kHz PCM WAV, up to 20s; per-user STT allowance |
| /v1/voice/speech | POST | Account session, bounded text, per-user TTS allowance; Korean requires Google Cloud TTS key |
| /v1/agent/jobs | POST/GET | Account session, idempotent client UUID, 3 active jobs and 20 submissions per UTC day |
| /v1/agent/jobs/{id} | GET | Same account may inspect its own state/result only |
| /v1/agent/jobs/{id}/cancel | POST | Same account may cancel queued/running tasks |

The hosted job loop currently offers **read-only** clock and allowlisted HTTPS lookup tools, at most 5 model steps. D1 prompt, checkpoint, and result fields are encrypted with an operator-managed AES-GCM key. This is not end-to-end encryption or an attested credential enclave.

## Activation checklist

1. Compare/merge this overlay into the **actual deployed** Worker source; never remove existing auth, D1 or account routes.
2. Apply the schema in migrations/0004_agent_jobs.sql on the correct D1 database, after backup and schema review.
3. Configure AGENT_ENCRYPTION_KEY as a 32-byte random secret in base64 and verify secret rotation/recovery strategy.
4. Configure an every-minute Cron trigger to resume queued work; verify requests that completed while the phone was closed.
5. Confirm Workers AI bindings, speech model availability and limits. Korean Cloud TTS requires a separate GOOGLE_CLOUD_TTS_API_KEY secret; do not put this secret into the Android app.
6. Run node tests, actual signed-account isolation tests, source/production route comparison and an Android login -> voice -> job -> app restart -> receipt end-to-end test.

No automatic operator credentials, unrestricted external browser automation or implicit payment/email calls are part of this overlay.
