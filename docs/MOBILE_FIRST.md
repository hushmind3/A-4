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
- Device speech recognition, Cloud model chat, and Android TTS in explicit turns.
  It is not a full-duplex realtime phone or video call.
- WorkManager drives scheduled notifications and bounded AI submissions with no
  desktop gate; background execution follows OS network/energy constraints.
- Account-scoped local databases are derived from the Google-linked DOMINION
  account. App pairing tokens and file vault are encrypted. Room conversation
  rows are not yet encrypted as a whole SQLite database.

## Missing, in progress, or requiring real-device verification

- Full DOMINION Rust harness parity, long-range autonomous plan/verify/retry,
  recovery of running jobs after process termination, and post-approval steps
- Independent realtime voice/video calls with interruption and AI participant
- General visual screen understanding and multimodal model file support
- Per-account opt-in cross-device memory sync and arbitrary cloud provider routing
- End-to-end test for login -> device operations -> restart -> verified result

Do not regress to PC-as-gateway. Do not represent draft intents, OCR or model
text as completion of an external transaction. This replaces the obsolete
single-tool description from the original mobile-first implementation.
