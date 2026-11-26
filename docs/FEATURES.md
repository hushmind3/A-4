# DOMINION Mobile — feature matrix

Status means source implementation, not a claim of production readiness. Server behavior refers to the inspected DOMINION branch1 snapshot. No live Android/cloud/server end-to-end run is claimed here.

| Area | Client source | Real integration / gap |
| --- | --- | --- |
| App identity, navigation, theme | Native Kotlin/Compose | DOMINION namespace, resources and app identity |
| Chats, search, draft, conversation actions | Native UI and local state | Real rooms/history via REST; creation and mutation need server contracts |
| Sending and retry | REST client, sending/failed status, stable local IDs | Server acknowledges text; durable idempotency still missing |
| Streaming | Polling fallback | Streaming events and reconnect cursors not implemented |
| Models | Server-driven conversation execution | Model catalog/selection UI not connected |
| Login | Prototype onboarding | Google cloud account and device authorization pending |
| Server connection | Build-time HTTPS origin and isolated cache | Runtime pairing, reachability indicator and tunnel authorization pending |
| Calls | UI, controls, timer, foreground service | Actual LiveKit/WebRTC media, interruption and voice agent pending |
| Attachments | Cards, share intent | Real file selection/upload and result viewer incomplete |
| Tasks, memory, schedules | Local demo screens | Mobile server endpoints currently placeholder collections |
| Job progress/approval/cancel/resume | Not integrated | Needs real engine event and job contracts |
| Notifications | Permission/UI and call notification | FCM and durable job updates pending |
| Friends, connections, credits | Local prototype | Not authoritative server-backed operations |
| Vault, app lock | Prototype UI | Not a secure vault; Keystore and actual authentication pending |
| Widgets | Android widget providers | Deep-link routing requires further integration |
| Background recovery | Local snapshot | Server task restoration across app restarts not integrated |

Demo purchases, fake account actions and simulated calls must not be presented as production operations. Future improvements should replace or remove these flows rather than treating renamed screens as finished capabilities.
