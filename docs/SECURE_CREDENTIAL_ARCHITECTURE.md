# A-4 secure credentials and delegated server execution

Status: target architecture, not a completed service claim. Reviewed 2026-11-27.

## Goal

Users without a PC can delegate approved tasks to DOMINION Cloud while the phone is offline. The general-purpose Worker, LLM and ordinary tool runner must not receive reusable end-user passwords, card information, or unrestricted OAuth tokens.

Separate three systems: **task orchestrator**, **credential custody**, and **user/device consent**.

## Data and permissions

| Item | Default custody | Cloud handling |
|---|---|---|
| Login ID, plan, usage counters, job identifiers/status | D1 with account-based authorization | Normal server processing |
| Private memory and conversation | Device; encrypted sync only by explicit design | Not automatically uploaded |
| Voice | Device capture/playback; transient Cloud STT/TTS | No raw recording persisted by voice.js |
| User passwords, card numbers, MFA secrets | Device secure storage | Never plain task inputs |
| Delegated provider OAuth access | Future secure credential executor | Scoped, expires, revocable, audited |
| Phone app actions | Android | Device must be available/approved |
| Optional desktop computation | PC engine, if configured | Not required |

## Public reference: Pickle credential-enclave (Apache-2.0)

https://github.com/pickle-com/credential-enclave

The published README, protocol and secure-program source describe:
1. Account master key created on-device; separately derived signing, log and credential user keys.
2. Remote verification of AWS Nitro Enclave measurements and enclave public keys.
3. A bounded grant sealed to a verified enclave node (at most 30 days per grant), with revocation.
4. Ciphertext outside the enclave, plaintext credential use within the attested program for permitted service requests.
5. Restricted allowed provider destinations, segregated credential custody and forwarding roles.
6. User-readable sealed audit records, written to a protected log store before credential use.

The secure-program README describes Android QuickJS and iOS JavaScriptCore clients. The reference expressly acknowledges limits: a value released into a browser field can be exposed to the invoking worker; some providers without PKCE allow the ordinary operator callback to exchange an authorization code. Enclave protections must not be claimed for an arbitrary browser or external account login workflow.

Apache-2.0 permits adaptation subject to its terms and correct notices; this document does **not** copy upstream implementation code.

## A-4 implementation reality

- VaultRecordCipher.kt uses Android Keystore AES-GCM for supported local records.
- CloudAccountRepository.kt stores mobile pairing credentials locally and invokes Cloud text, voice and job APIs.
- cloud-relay/agent_jobs.js encrypts D1 task data using a **server-configured** AGENT_ENCRYPTION_KEY, not a user-owned or attested key.
- No deployed confidential computing executor, verified remote attestation or restricted per-provider token delegation.
- Current server jobs are **read-only** research tasks with a narrow allowlist; no external account writes, credentials or payments.
- Worker job/voice changes, D1 migration, server-held encryption key and recovery trigger were deployed on 2026-11-27; live signed-account isolation and runtime verification remain outstanding. This does not deploy confidential credential delegation.

## Implementation order

1. Add strict account and device identities, per-device revocation, session rotation, account data export/delete, cross-account negative tests.
2. Design device-generated protocol master key separately from Android Keystore's nonexportable wrapping key. Distinct data, signing and delegation subkeys.
3. Implement explicit signed grants (provider, scopes, host, purpose, expiry, step budget) and immediate revocation. Write a user-visible audit receipt for each secret use.
4. Operate an attested isolated credential service (e.g. AWS Nitro Enclaves) distinct from Cloudflare Workers. Worker orchestration cannot decrypt private credential records or receive the master key.
5. Restrict all secret-bearing network requests to approved hosts, request positions and user scopes. Never forward credential plaintext to the LLM or generic browser runner.
6. Guarantee that offline cloud tasks run only while a grant is active; phone-only actions remain queued until Android reconnects.
7. Verify deployment, threat model, audit log, recovery, quotas and real-device account isolation before releasing to users.

## Non-goals

No PC dependency. No plaintext credential vault on a shared Worker. No marketing claim that server-managed AES-GCM equals end-to-end or attested confidentiality. No silent execution of payments, message sending, authentication or external writes without correctly scoped authorization and result evidence.
