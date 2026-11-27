# DOMINION Mobile — A-4

Native Kotlin / Jetpack Compose Android AI assistant. Designed for a user **without a PC**. The desktop DOMINION Rust application is optional.

## Current runtime structure

| Component | Responsibility | Mandatory |
|---|---|---|
| A-4 on Android | Conversation UI, microphone and playback, personal records, permission approval, Android app and device actions | Yes |
| DOMINION Cloud | Account pairing, per-user model allowance, cloud model inference, planned hosted speech and durable jobs | Yes for current PC-free mode |
| Android MobileHarness | Runs phone-specific tools and mobile task/reply loop, with Cloud inference | Included |
| PC DOMINION / Rust engine | Optional remote models, files and PC tools | No |

The mobile execution path in RemoteBackedRepository.kt activates when no PC engine endpoint is configured and a Cloud account connection is present. It uses MobileHarness and CloudAccountRepository.completeForMobile. This code path supersedes the older desktop-only baseline described in this repository's historical integration docs.

## Source implementation and provisioning (2026-11-27)

Implemented in Android source: independent cloud model requests, on-device tool/approval handling, cloud STT/TTS API client, 16-kHz microphone capture and MP3 playback, cloud job creation and status/result queries.

Implemented in the cloud-relay source overlay: account-scoped speech endpoints, separate speech quotas, encrypted D1 job checkpoints, bounded read-only research tool loop, cron-based retry/resume code and user-scoped job routes.

**Deployed to the existing production Cloudflare Worker (2026-11-27):** live Worker version 61dbc468-2b36-4d9a-bb3e-cd4370160bf6, built from ef1c2a5fc3f315bf1e4057d991ef666e6c8af221. D1 migration 0004, the server-held job encryption secret and dedicated one-minute recovery trigger are configured. Existing login, model and relay bindings were retained. Korean TTS provider configuration and actual signed-account end-to-end verification remain outstanding. See [deployment record](cloud-relay/README.md).

**Not yet finished:** realtime full-duplex speech and interruption, general browser/account automation, full external service OAuth permission delegation, user-key-controlled encrypted cloud memory, robust cloud push to Android, and verified live service operation.

Android speech is currently turn-based: record, Cloud STT, assistant task processing, Cloud TTS, play. The app does not bundle large speech models. Korean Cloud TTS needs a configured Korean-capable provider; do not report that it works before a deployed round-trip test.

## Credential protection

Locally stored login pairing, owner-engine connections and vault records use Android Keystore-backed encryption. The current cloud-job encryption uses an operator-held AGENT_ENCRYPTION_KEY, which prevents a raw D1 database dump from directly revealing task contents, but **is not end-to-end encryption and does not prevent the operator from decrypting server-side records**.

A future external-account automation service requires a separate credential protection layer: user-controlled master keys, bounded, revocable grants, independently attested secure execution, and user-visible credential-use audit logs. See [Secure credential architecture](docs/SECURE_CREDENTIAL_ARCHITECTURE.md).

## Build and tests

Android JDK 17 / SDK 35: Gradle tasks client:assembleDebug and client:testDebugUnitTest. Cloud tests: npm --prefix cloud-relay test. A passing build or contract test is not proof of live Cloudflare deployment, account sign-in, real device end-to-end speech or successful external tasks.

The PC and A-4 repositories remain separate products. PC integration is an optional connector, never a prerequisite for installing or using A-4.
