# DOMINION Mobile — backend integration

## Service boundaries

The mobile client sends requests to the user's DOMINION execution server. The DOMINION Cloud Worker is a separate identity, pairing, quota and inference service. Do not use its origin as the execution-server base URL.

Source baseline inspected: [DOMINION branch1](https://github.com/hushmind3/dominion/tree/0ebc2d1667982fae6a8396d3bb39c46af361c36a), particularly `src/api/mobile.rs`, `src/api/mod.rs` and `cloud/README.md`. Other branches and deployed instances can differ.

## Configure the client

```bash
./gradlew :client:assembleDebug -PdominionServerUrl=https://your-dominion-server.example
```

Only an HTTPS origin is accepted; the final slash is normalized. No server URL selects demo mode. The app isolates demo storage from each server origin and does not seed a remote session with fictional demo conversations. A configured URL is not proof of reachability or authentication. While the app is visible, it refreshes the account, agent and room collections concurrently, every 30 seconds after completion. The UI distinguishes connection loss, authorization errors and other server errors. The seven prototype collection reads are not part of the foreground heartbeat. The client does not automatically retry failed network submissions.

The client must be used against an authorized, operator-controlled deployment. The current local server's mobile auth handlers are development stubs; do not expose them as production login.

## Existing mobile REST contract

| Method | Route | Behavior in inspected branch1 |
| --- | --- | --- |
| GET | /v1/me | Local owner projection |
| GET | /v1/characters | Agent list |
| GET | /v1/conversations | Room list |
| GET | /v1/conversations/{id}/messages | Room-session history |
| POST | /v1/conversations/{id}/messages | Submit text to room execution |
| POST | /v1/conversations/{id}/calls | Issue LiveKit room token |
| GET | /v1/tasks, /v1/schedules, /v1/memories | Empty placeholder collections |
| GET | /v1/friends, /v1/connections, /v1/explore, /v1/vault, /v1/inbox | Empty placeholder collections |
| POST | /v1/auth/{provider} | Local stub, not Google authentication |

Message submission body: `{"text":"...","client_message_id":"..."}`. The current client maintains a stable client message ID. The inspected server reads text but does not implement durable deduplication of client IDs and may return a synthetic acknowledgement ID. Retries after an ambiguous network failure can submit duplicate work. Production must reconcile with a persisted server request ID and guarantee idempotency. The client polls message history after acknowledgement; there is no verified streaming or durable background subscription.

Response envelope: `{"data": ...}`. The client friend DTO uses `is_assistant` (optional, defaults to false) to identify assistant accounts. The inspected server currently returns an empty friend collection; this field is a client contract, not a verified server capability.

The current client still includes some prototype endpoints not provided by branch1 (for example individual conversation detail). Do not treat them as live.

## Cloud identity and device authorization

The existing Cloud documentation describes Google identity and app pairing:

- POST /v1/apps/start: start pairing with a hash of a generated app bearer.
- GET /v1/apps/status: poll for approval.
- POST /v1/apps/disconnect: revoke app access.

The documented pairing grant covers shared account quota and free inference; it does not grant access to the private execution engine or synchronize local history. Android integration must separately implement secure token storage, device authorization and an authenticated route to the selected DOMINION server. Never reuse an owner bearer as an external app credential. Do not embed model API keys.

Official references: [Android Google sign-in](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Android network security](https://developer.android.com/privacy-and-security/security-config).

## Production integration gates

1. Shared Google account and revocable mobile session.
2. Device-specific authorization and authenticated Cloudflare/private-server route.
3. Server health/reconnect state; model availability and selection through the engine.
4. Durable submission ID, history/event stream, cancellation, approval and job recovery.
5. Voice/video media, microphone/camera permission, audio focus and lifecycle integration.
6. Secure storage and notification delivery, followed by real device/server/cloud integration testing.

The current source does not implement these gates end-to-end. The REST client is not a second agent harness.

## Voice media connection

The mobile client requests POST /v1/conversations/{id}/calls with video=false. It requires a nonempty room/token, future expiry and a WSS endpoint, then connects to LiveKit and publishes the granted microphone. A connected media room without a remote peer is displayed as waiting, not as a successful AI conversation. The service owns the room and releases it on termination; live call state is not persisted to disk. Demo calls and simulated call records are disabled. Camera and video controls are not offered until video rendering is connected.

The inspected server may return a plain ws:// fallback; a deployed secure WSS endpoint is required. Issuing a room token does not dispatch a voice agent. Actual bidirectional AI speech, interruption and task execution depend on a real agent participant using the DOMINION harness. No server or Cloudflare change was made here.

SDK: [LiveKit Android 2.30.0](https://github.com/livekit/client-sdk-android/releases/tag/v2.30.0), Apache-2.0. See the [official Android integration guide](https://docs.livekit.io/transport/sdk-platforms/android/) and [foreground service restrictions](https://developer.android.com/develop/background-work/services/fgs/service-types).
