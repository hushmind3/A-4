# DOMINION Cloud and mobile agent integration

DOMINION Cloud is the account and inference gateway. Per-account quota enforcement
already exists in the Worker and is not a desktop-server requirement. Worker
operator keys stay on the backend; Android stores scoped pairing credentials
using Android Keystore AES-GCM.

The native phone executes Android tools, local memories, approved navigation
actions, and scheduled work. Its Kotlin mobile harness is a partial port of
selected DOMINION concepts, NOT the complete Rust SDK or hosted agent executor.
The optional PC engine keeps separate authentication/session handling.

Implemented in mobile mode: cloud text inference, mobile encrypted attachments,
text document content, Korean/Latin image OCR, approved phone UI semantic
replanning, local schedules and memory, short device tasks, and turn-based voice.

Still missing: independent realtime audio/video AI participant, durable cloud
background orchestration, richer visual image/video model support, synchronized
private memories and generic provider switching. Migrating to Google Cloud or
another host requires changing cloud account and API service contracts, not
installing the desktop application.
