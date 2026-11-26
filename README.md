# DOMINION Mobile

**Your AI assistant, connected to your own DOMINION.**

Native Android companion for [DOMINION](https://github.com/hushmind3/dominion). Talk to your assistant, delegate work, review its progress and return to the same conversations across devices. The Android client is the interaction layer; DOMINION owns model selection, agent execution, tools, memory and verification.

> Development preview · 0.1.0-dev. The native interface and server-mode REST messaging are implemented in source; shared cloud account approval, remote job recovery and real-time media still require end-to-end testing or additional integration. The Android voice path uses LiveKit 2.30.0; video is not yet offered.

## Architecture

```text
DOMINION Mobile (Kotlin / Jetpack Compose)
  ├─ Conversation UI, attachments, call controls, task views
  ├─ Repository + REST transport + isolated Room/SQLite databases
  └─ Intended: common Cloud identity, notifications, media
                     │
           Authorized HTTPS connection
                     │
DOMINION (Rust) — rooms, agents, model routing, execution, memory
  ├─ Local models
  ├─ Configured external model APIs
  └─ DOMINION Cloud model provider
```

The cloud account gateway and the private DOMINION execution server are distinct services. A successful cloud sign-in does not by itself authorize access to a local engine.

## Getting started

Requirements: JDK 17, Android SDK 35, Android 12L (API 32) or newer, Android Studio or the included Gradle wrapper.

### Interface preview

```bash
./gradlew :client:assembleDebug
```

Without a server URL, the app opens its local demonstration environment. Sample conversations, purchases and account actions in this mode are simulations, not live services. Calls require a connected engine.

### Connect to your DOMINION server

Use an HTTPS origin belonging to your own authenticated DOMINION deployment, not the cloud billing/inference endpoint:

```bash
./gradlew :client:assembleDebug -PdominionServerUrl=https://your-dominion-server.example
```

An explicitly configured origin selects server mode. The app uses the existing DOMINION mobile REST contract and keeps server caches separate from demo data and other origins. Foreground resynchronization and a connection-status banner expose offline, authorization and server errors. Never expose an unauthenticated local engine to the internet. Cloud-to-device authorization is not complete in this release.

For the actual server routes and limitations, see [Backend integration](docs/BACKEND.md).

## Feature status

| Capability | Current state |
| --- | --- |
| Native chat, history, search, navigation | Room SQLite storage and SQL message search; Android validation pending |
| REST message submission and history retrieval | Implemented client; live integration not verified |
| Failed-send state and retry | Implemented client; server idempotency not yet guaranteed |
| Server response polling | Implemented; not streaming |
| Profile, tasks, schedules, memory, attachments | Demo/interface or partial read contracts |
| Voice calling | LiveKit native transport, permissions and foreground service implemented; live peer/agent integration unverified |
| Shared Google account | Existing Cloud browser approval, encrypted app session, quota/model list; live verification pending |
| Cloudflare execution-server routing | Integration pending |
| Background execution, approval, reconnect recovery | Integration pending |
| Widgets and share intent | Native interface; deep links not fully routed |

See [Feature matrix](docs/FEATURES.md) for details.

## Source layout

```text
client/
  src/main/java/com/dominion/mobile/
    AppContainer.kt             # Repository selection and cache scope
    data/                       # Models, repositories, REST, local persistence
    ui/                         # ViewModel, navigation, Compose components/screens
    platform/                   # Android call service and widgets
  src/main/res/                 # Android resources
  src/test/java/com/dominion/mobile/
docs/
  BACKEND.md
  FEATURES.md
```

Application ID and namespace: `com.dominion.mobile`. App links currently use `dominion://`; verified HTTPS links require an operator-owned domain and Android App Links association.

## Development

```bash
./gradlew :client:testDebugUnitTest
```

Keep platform UI separate from the server execution harness. Do not embed provider keys or duplicate the DOMINION agent runtime. Build and test changes against real API contracts; distinguish demonstrations from validated integrations. APKs and private configuration are not source deliverables.

Primary development branch: `main`. Server project: [hushmind3/dominion](https://github.com/hushmind3/dominion).

### Optional DOMINION Cloud account

Open Settings → DOMINION Cloud → Connect shared account. Approve the existing Google account in the system browser or copy the pairing code to the desktop Cloud account screen. Return to the app to verify approval, view the shared free quota and list available Cloud models. Credentials are encrypted using Android Keystore. This is optional, and does not authorize access to the local execution server or change its selected model.

### Local data storage

Each execution origin has its own private Room/SQLite database with WAL, separate conversation/message rows and local task, schedule, memory, vault, archive and activity records. Legacy preference snapshots migrate transactionally and are removed only after commit. Authentication credentials remain separate. Database reads/writes run off the UI thread. The UI currently hydrates the existing conversation state at startup; pagination, FTS and durable background execution synchronization remain follow-up work. A disconnected engine does not prevent reading the local history. Standalone Cloud text chat uses the existing free-inference gateway; runtime engine configuration remains pending.

### Cloud chat without the engine

Settings → DOMINION Cloud → Cloud chat uses the existing approved account's free inference API with the server's `dominion/free-auto` default or a listed free model. History is stored in an account-specific local Room database. It does not execute engine tools or tasks. The server currently grants app sessions for seven days, without a refresh endpoint. Live Google approval and inference remain unverified.
