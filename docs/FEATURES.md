# Feature status

Source implementation, not production certification.

| Area | Current state |
|---|---|
| Identity and UI | Kotlin/Compose; original Korean UI and character presentation retained |
| Authentication | Shared Google approval and independent encrypted engine credential; token renewal/device pairing pending |
| Local storage | Room/SQLite and existing migration/search |
| Chat | Native room submission, server message/job IDs and history polling |
| New rooms | Actual engine creation; existing agents and default tools |
| Models | Existing provider and model catalogue; room model pool and server rotation |
| Tools | Existing tool inventory, room enablement, user-confirmed Korean skill import |
| Cloudflare | Can select configured engine providers; actual deployment and inference untested |
| Job execution | Existing harness; accepted job ID stored; complete progress/approval/cancel/recovery UI pending |
| Voice | LiveKit transport; live AI peer unverified |
| Video, files, camera, notification | Further integration required |
| Other existing screens | Local records or incomplete prototype UI; not evidence of live execution |

Removed hardcoded reply generator, fictional seed data, mock onboarding, separate direct-cloud chat, and build-time server-mode selection. Unconnected means unconnected. Remaining UI-only operations must be implemented or removed, not reported as completed work.

## Preserved original design

[Original feature inventory](ORIGINAL_FEATURES.md) and [original backend specification](ORIGINAL_BACKEND.md) are the exact historical documents from commit 1708acfcbbc287c9627d0dd30e3f379e26830935. They describe the original prototype and are not claims of current live functionality. Original UI examples and checks are restored in the test source set. The original onboarding UI is accessible from Settings → Initial personalization; Google authorization routes to the existing Cloud account screen. Unconnected authentication methods and contacts are not simulated. The screen only saves local personalization for an existing agent; it does not create a server agent or implement a new login contract.

The original gradient call UI, avatar animation, controls and video layout have been restored; current microphone permission, LiveKit service lifecycle and actual connection phase remain authoritative. The waveform is inactive until real audio-level data is wired. Speaker and video controls are retained as unavailable rather than faking success. Account management entries are restored; actual Cloud sign-out uses the existing account screen, while server-side account deletion is not connected. No data deletion is performed.

The original scripted reply examples are preserved in the test source set, not wired to the production assistant.
