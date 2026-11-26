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
