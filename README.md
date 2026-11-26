# DOMINION Mobile

Android native companion for [DOMINION](https://github.com/hushmind3/dominion). A single conversation interface delegates real work to the existing Rust execution harness. The mobile app manages interaction, device integration and private local history; it does not implement another agent runtime.

## Connect

Build with JDK 17 and Android SDK 35. Android 12L (API 32) or newer is required.

```sh
./gradlew :client:assembleDebug
```

Open Settings → DOMINION Cloud to approve the shared Google account. In Settings → Engine, models and tools, enter your authorized HTTPS engine origin and owner token. The two credentials are independent and protected by Android Keystore. The engine must already be reachable and securely configured; Google approval alone cannot authorize the private engine. No fictional conversations or simulated AI replies are generated when disconnected.

The existing room interface submits to the native job-producing API with a stable client message ID. New rooms are created on the engine, with tools enabled. The dedicated mobile assistant room enables tools on first submission if needed; other existing rooms keep their explicit tool settings. The engine enforces tool approval policies.

Select models from the engine's existing registered providers and apply a room model pool. Cloudflare, DOMINION Cloud free models, external APIs and local LLMs can be selected when already configured on that engine. Model rotation, persona, tools, memory and execution stay in DOMINION. There is no separate Cloud general-chat mode or direct inference bypass.

The Korean skill import control calls the engine's existing repository importer with explicit user confirmation. It does not install or configure the external services automatically during development. Korean UI language, Korean reasoning selection, character presentation and date/currency formatting remain in use.

## Data and validation

Room/SQLite databases are isolated by engine origin. Existing storage migration, message search and offline history are retained. Old direct-cloud and prototype data files are not deleted by this source update.

```sh
./gradlew :client:testDebugUnitTest
```

See [backend integration](docs/BACKEND.md) and [feature status](docs/FEATURES.md). Source integration is not a claim of successful Android build, real device login, live inference or completed job execution. The app is still under development. Only this repository's main branch is modified; no server deployment or Cloudflare configuration is created here.
