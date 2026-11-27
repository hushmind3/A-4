# A-4 mobile-first assistant runtime

The Android native app is an independent assistant. No PC installation, port
forwarding, or desktop DOMINION server is required for default assistant usage.
DOMINION Cloud provides the account, Google login, model API, and account-specific
free inference quota. A PC with local models is an OPTIONAL execution node.

## Real Android execution

- Kotlin MobileHarness: multi-step read-only tool calling, local memory retrieval,
  durable observations, validated user-approved writes and app launch.
  Up to 3 read tools per turn; each write or launch currently stops for approval.
- Tools: time, battery, local schedules/memory, Korean portal catalog,
  installed app list, and approved dialer, SMS/email draft, calendar draft,
  maps, browser link, installed-app launch and local schedule/memory writes.
- Scoped native accessibility navigation with per-action confirmation. Mobile
  cloud replan receives only approved accessible control labels, not image
  screenshots. Image-based coordinate replan requires a separately connected
  multimodal capability.
- User-selected attachment bytes encrypted on the phone using Android Keystore
  backed authenticated file storage. Text documents may be sent to the language
  model. Selected photos can be read by bundled Korean/Latin on-device OCR;
  ONLY recognized text is passed to text inference. This is not full visual AI.
- Current main-path speech: microphone WAV (up to 20 seconds) -> DOMINION
  Cloud STT -> Cloud model/harness -> Cloud TTS MP3 -> device playback.
  No large local speech model is required. This is turn-based, not full duplex.
  Cloud speech endpoints are deployed as of 2026-11-27; Korean TTS provider configuration and live signed-account tests remain outstanding.
- WorkManager drives scheduled notifications and bounded AI submissions with no
  desktop gate; background execution follows OS network/energy constraints.
- Account-scoped local databases are derived from the Google-linked DOMINION
  account. App pairing tokens, vault and the JSON payload of conversations,
  memories and related local rows are encrypted with Android Keystore-backed
  AES-GCM. This is application-level row encryption, not a fully encrypted
  SQLite page/WAL/backups guarantee.

## Missing, in progress, or requiring real-device verification

- Live DOMINION Cloud signed-account/Android roundtrip and Korean speech provider configuration (Worker deployment, D1 migration, encrypted-job key and Cron configuration are applied)
- Full DOMINION Rust harness parity, long-range autonomous plan/verify/retry,
  recovery of running jobs after process termination, and post-approval steps
- Independent realtime voice/video calls with interruption and AI participant
- General visual screen understanding and multimodal model file support
- Per-account opt-in cross-device memory sync and arbitrary cloud provider routing
- End-to-end test for login -> device operations -> restart -> verified result

Do not regress to PC-as-gateway. Do not represent draft intents, OCR or model
text as completion of an external transaction. This replaces the obsolete
single-tool description from the original mobile-first implementation.

Cloud jobs: when configured, account-specific read-only research tasks can run
on the server, with bounded model steps and encrypted checkpoints. A mobile
WorkManager watcher periodically refreshes task status after the app exits,
but does not supply realtime push or external account automation. The Cloud
login state must be validated against the server before marking Connected.

### Native Korean private-service queries

The original Korean service workspace and the existing MobileHarness task records are reused for direct Kakao place, Naver news, Coupang product and Danawa price lookups. Shopping and healthcare workflows also link to the same adapters. Form-selected public search queries execute on the Android device, and structured results remain available without model inference. Chat may request `korea.lookup` with public words explicitly present in the current request. The first three use the upstream documented hosted k-skill public proxy; Danawa reads its public search page. No Cloudflare or PC deployment is necessary for these adapters. Provider availability and live Android network verification are separate from parsing and task routing tests. No reservations, order submission or private account access is claimed. Upstream pinned reference: https://github.com/NomaDamas/k-skill/tree/c41a11703b0ea25b8c5c3c62bd0f06f31997f7f8 .
