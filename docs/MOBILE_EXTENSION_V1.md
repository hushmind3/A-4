# Mobile extension v1 — proposed server contract

A-4 main implements the actual Android client for the following **proposed** endpoints. Existing DOMINION branches have not implemented this contract. Do not advertise a feature until it has real account-scoped storage and execution. Existing /v1/friends compatibility responses are not a capability signal.

All endpoints use the existing engine authentication and origin; never accept an account ID as authentication. The server must derive the account from authentication, authorize every peer and target, and return the authenticated account ID. No Cloud changes are required for the client foundation.

| Method and path | Request / response |
|---|---|
| GET /v1/mobile/capabilities | protocol: 1, accountId, features: friends, peer_messages, account_delete, institution_actions (only implemented features) |
| GET /v1/mobile/peers | accountId, peers[{id,handle,displayName,relationship: friend/incoming/outgoing/blocked}] |
| GET /v1/mobile/peers/{id}/messages | accountId, peerId, messages[{id,from,to,text,createdAt,nonce}] (latest 200; server must authorize relationship) |
| POST /v1/mobile/operations | {accountId,nonce,action,target,text?}; idempotency key = authenticated account + nonce |
| GET /v1/mobile/operations/{nonce} | same authoritative receipt; query must not execute |
| POST /v1/mobile/operations/{nonce}/decision | {digest,allow}; atomic compare-and-set against current review, idempotent |

Receipt fields: accountId, nonce, action, target, status (accepted/review/running/completed/failed/cancelled), review? {digest: SHA256 hex, summary, expiresAt ms, requiresFreshAuth}, evidenceId?, evidenceUrl?, detail?. Completed requires a real durable evidenceId; status and evidence must be immutable after terminal completion. Review-required operations must not complete before approval. For account deletion the server must authenticate recently and retain a privacy-preserving receipt query route after deletion, without reactivating the account. Authentication is completed through the existing server account UI; do not request passwords or one-time codes in A-4.

Actions: friend.invite (handle or account id), friend.accept/decline/remove/block (peer id), peer.message (peer id, text), account.delete (own authenticated account ID), institution.prepare (service id, reviewed request text). Institution work must go through DOMINION's existing tool/approval harness; actual submission/payment requires explicit scope, institution, amount in KRW if applicable, and final review digest, then verified official receipt. No payment details or identity secrets are sent by the mobile extension. The preparation operation may fail explicitly or require further authentication; never represent a draft or an AI answer as a completed external transaction.

Android persists the operation before network writes in an engine-scoped encrypted journal. Ambiguous writes are looked up by original nonce, not automatically repeated. A failed/missing query must not invent a receipt. The client shows only the authenticated account's entries. No synthetic friend data is merged into the device's local contacts.

## Real-time media (proposed)

Capabilities additionally advertise `realtime_audio` and optionally `realtime_video`. POST /v1/mobile/realtime/calls accepts {accountId,roomId,sessionId,callId,video}; returns the same IDs plus agentIdentity, videoAllowed and media:{room,token,url,expires_at}. The engine must verify current native room ownership/session, create a private media room restricted to the authenticated mobile user and specific agent, and join the AI participant before mobile publication. The agent streams STT/model/TTS or multimodal inference through the existing DOMINION harness, persists each finalized turn and job in that native room, and honors tool approvals. A signed media token alone does not prove inference is operational. Android checks the returned binding and exact participant identity, waits before publication, and stops on participant/session mismatch. The existing call and turn-based assistant modes remain available separately. This client contract has not been implemented on DOMINION itself.

## Aligned speech (proposed)

`aligned_speech` advertises POST /v1/mobile/voice/aligned with {accountId,text}. Return {accountId,wavBase64,cues:[{startMs,endMs,viseme,openness}]}. WAV must be actual PCM16 audio; cue timestamps must be ordered, non-overlapping, and within measured audio duration. Supported labels a/e/i/o/u/closed, openness 0..1. The server must derive alignment from the synthesized audio, not guess a timeline. Android drives authored targets by MediaPlayer position. When the capability is absent, existing Piper WAV amplitude animation remains available.

After account deletion the server may advertise only `account_delete_receipts` in an authenticated, receipt-only capability response retaining the prior opaque account ID. This must authorize only the deletion receipt lookup, not account reactivation or new operations.

## Messenger v2 (proposed)
Feature `messenger_v2` provides GET /v1/mobile/messenger/threads (accountId, threads [{id,title,kind,members}]), GET /v1/mobile/messenger/threads/{id}/messages?q=&before= (accountId,threadId,messages[{id,senderId,text,createdAt,readBy,attachments}],nextBefore), POST /v1/mobile/messenger/threads/{id}/attachments ({accountId,name,mime,size,sha256,base64}) and GET /v1/mobile/messenger/threads/{id}/attachments/{attachment} (raw bytes). Attachment metadata: id,accountId (owner viewing),threadId,name,mime,size,sha256. The server must authorize every thread member, search and attachment, derive account identity from credentials, reject unauthorized uploads and bound resource size. No user-visible upload success until size, hash, account and thread match. An upload alone is not a delivered message.

Operations on existing /v1/mobile/operations use messenger payload: messenger.group.create target "group" payload {title,members}; messenger.thread.send target thread ID payload {text,attachments}; messenger.thread.read target thread ID payload {messageId}. The server records authoritative member and message IDs, returns completion evidence and readBy from actual receipts, and deduplicates by account+nonce. The Android implementation uses the existing encrypted operation journal, not a separate harness. Server-side functionality is still required.
