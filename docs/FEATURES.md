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

Removed hardcoded reply generator, fictional seed data, mock onboarding, separate direct-cloud chat, and build-time server-mode selection. Unconnected means unconnected. Remaining UI-only operations must be made functional and preserved, not removed or reported as completed work.

## Preserved original design

[Original feature inventory](ORIGINAL_FEATURES.md) and [original backend specification](ORIGINAL_BACKEND.md) are the exact historical documents from commit 1708acfcbbc287c9627d0dd30e3f379e26830935. They describe the original prototype and are not claims of current live functionality. Original UI examples and checks are restored in the test source set. The original onboarding UI is accessible from Settings → Initial personalization; Google authorization routes to the existing Cloud account screen. Unconnected authentication methods and contacts are not simulated. The screen only saves local personalization for an existing agent; it does not create a server agent or implement a new login contract.

The original gradient call UI, avatar animation, controls and video layout have been restored; current microphone permission, LiveKit service lifecycle and actual connection phase remain authoritative. The waveform is inactive until real audio-level data is wired. Speaker and video controls are retained as unavailable rather than faking success. Account management entries are restored; actual Cloud sign-out uses the existing account screen, while server-side account deletion is not connected. No data deletion is performed.

The original scripted reply examples are preserved in the test source set, not wired to the production assistant.

## Existing tool integration

Existing connections and task templates remain unchanged. The connections screen now reads the engine's real tool registry, including approval flags and schemas. Chat reads the native room execution snapshot, live room-scoped approvals, job states and operation results. Explicit approval/denial and job cancellation use existing engine endpoints; approval arguments and digest are rechecked before submission. Photo/file pickers and camera preview upload actual selected bytes (8 MiB limit) to the existing room-bound attachment endpoint; native transcript metadata preserves attachment IDs across reloads. File cards save the original attachment through the system document picker. The microphone button uses Android's installed speech recognizer with Korean language selection and inserts editable text, not automatic sending. These are source integrations; device build and live endpoint testing are pending. Existing service OAuth, device-wide agent actions, full-resolution camera, video and other unconnected features remain in the product design and are not claimed to work.

## 외부 서비스와 기존 통화 기능

원래 17종 연결 목록과 12종 생활 템플릿을 보존한다. 실제 본체 OAuth 제공자와 승인 범위 조회, 본체 브라우저 인증 시작/해제, HTTPS 외부 도구 서버 수동 등록 및 토큰 전달, 등록 확인/도구 검색/사용 중지, 임의 서비스 웹 로그인 화면을 연결했다. 본체 계정 인증은 등록된 제공자와 실제 grant를 구분한다. 수동 웹 로그인은 본체 PC에서 완료해야 한다. 자체 OAuth 동적 등록/갱신과 개별 서비스별 권한 강제는 본체에서 지원할 때까지 완료로 표시하지 않는다. 앱에는 입력한 외부 토큰을 영구 저장하지 않는다. 기존 LiveKit 통화의 영상 트랙 송수신, 실제 출력 장치 변경, 음량 파형, 통화 기록과 기기 TTS 미리 듣기 코드도 반영했다. 실제 AI 미디어 참가자 및 기기 실행 검증은 별도 필요하다.


## Background job notifications

After a native room job is accepted, a unique periodic WorkManager check reads the existing engine job record and pending approvals. The mobile worker never executes the job: DOMINION remains responsible for all actions, memory and approval decisions. The worker derives its origin from the encrypted current engine connection and never stores the owner token in WorkManager input. Notification text omits prompts, arguments and credentials. Android 13+ permission is requested from the room execution controls.

This is best-effort background polling (15-minute minimum interval, possibly delayed by the OS), NOT realtime push. Tap a notification to open its conversation (once it exists in the server-synced room list) for authoritative details. Engine origin switches invalidate old watchers. Physical device testing, FCM push and job-specific deep links remain pending.


## Native security and data portability

The two existing data-export menus now open Android's CreateDocument picker and write a real UTF-8 JSON export of locally available chat history, tasks, schedules, memories, account display data and settings. Vault contents, credentials, connection tokens and ephemeral state are intentionally excluded. This is not a complete server backup or an encrypted export.

App lock and vault unlock now use Android's system biometric/PIN authentication, not a local demo flag. Vault access is gated independently of app lock and is cleared on backgrounding or leaving its screen. Vault records are sealed using per-origin Android Keystore AES-GCM with record-bound associated data; existing plaintext rows are migrated without discarding the logical records. Ordinary chat history remains in the local SQLite database. Existing historical SQLite/WAL pages and old backups are not claimed to have been securely erased. Device authentication and database migration require Android runtime testing.

Job alerts now honor the existing app notifications switch, per-room mute and lock-screen privacy preferences. The notification destination includes its original engine scope so stale alerts cannot navigate into a different engine's rooms. Turning the notification switch off clears existing job alerts without stopping active call notifications.

Account and onboarding name saves use the real shared owner profile endpoint when configured, with a server read-back check before showing success. Birthday remains an explicitly local field. The unconfigured app retains its local personalization capability; it does not claim that server data was updated.

Memory saves now attempt native agent-scoped memory storage; offline and legacy entries remain on-device and have a retry/sync control. The screen reads actual engine memories and shows the engine retrieval eligibility flag. Explicit deletion verifies the native collection; original transcripts are unaffected. Room rename uses a name-only native update. Actual retrieval in inference and device integration still require runtime validation.

Native owner message acknowledgements and subsequent room job snapshots now correlate by actual job ID, room, session and source message. Concurrent assistant replies no longer terminate the old one-minute message watcher. Individual outgoing messages show server job status; native replies retain actual author agent and reply-to identity. Foreground room polling and existing background WorkManager continue to reconcile server work. Token-by-token streaming remains separate from this committed-message synchronization.

Foreground room state now listens to the native authenticated SSE event stream, refreshing messages, jobs and approvals on matching committed server events. Reconnect replays the known cursor, with independent periodic polling for recovery; no token-by-token generation or push delivery is claimed. Runtime server and Android testing remain pending.

Archive now indexes actual message attachment IDs, native names, MIME types and explicit HTTP(S) links. Long-press saves references; room info shows real counts and a scoped shared-file browser. Existing archive entries are retained. Original attachment download uses the existing room-scoped engine API; device save uses CreateDocument, and share uses a restricted temporary FileProvider directory with read-only grants. Archive references require original server data to remain accessible; they are not independent offline backups. Android runtime verification is pending.

Existing personal schedules now retain a concrete date/time and timezone, with native date/time pickers and a real WorkManager notification worker. One-shot and calendar-anchored daily, weekly and monthly reminders work without network; a periodic repair checks due work after restart. App notification permission and privacy settings are honored, and legacy free-text schedules remain editable rather than being deleted or assigned invented dates. Android may delay work; this is not exact-alarm delivery or server-executed scheduled agent work. Android runtime testing remains pending.

Manual task cards retain their checklists and can explicitly assign an actual native execution to the selected agent in a deterministic single-agent room. The request and receipt are persisted with stable IDs; status comes from the exact native job, not a manual completion flag. The task chat opens the existing approval, cancellation and result UI. No server deadline scheduling is implied. Device and engine integration tests remain pending.

Room clear uses native POST /api/rooms/{id}/reset and verifies the new session; previous server transcripts remain archived. Rewind explicitly restores the last owner request as a draft in a fresh session, not arbitrary historical context or a paid ticket. Native room removal is confirmed by GET list; the auto-recreated default assistant is instead hidden locally with a restore action. Session-aware refresh rejects changing snapshots and prevents archived messages reappearing after an observed reset. Actual runtime verification remains pending.

Android incoming SEND/SEND_MULTIPLE shares accept granted content URIs and preview bounded files (up to five, each at most 8 MiB) before user-selected room submission. Only the selected destination receives uploaded attachments, and failed preparation retains the imported files in the ViewModel. Uploaded messages use the existing retry flow. File staging is in memory and does not survive process death; oversized files require a smaller file. Android end-to-end verification remains pending.

Report actions now open an editable issue report and the native Android sharesheet. No report receipt is fabricated and no conversation transcript, account data, engine origin or token is attached automatically. There is no server report intake endpoint in the inspected engine; the user chooses the recipient and sends the report separately.

## Korean public-service workspace

Explore now includes a Korean daily/public-service hub: Government24, Bokjiro, National Secretary, e-People, national legislation and NEIS parents. Official HTTPS links and user-initiated dial/map actions are direct device capabilities. User-entered region and request are passed to a durable native task with the selected agent and official source, verified receipt, approval/result chat and retry from Tasks. No personal record access or automatic administrative submission is claimed. Schedules can open a prefilled native calendar editor; the user confirms saving in the calendar app. Sources: https://www.gov.kr/ , https://www.bokjiro.go.kr/ , https://www.ips.go.kr/ , https://www.epeople.go.kr/ , https://law.go.kr/ , https://parents.neis.go.kr/ , https://developer.android.com/guide/components/intents-common . Runtime integration remains to be tested.

Korean workspace also covers employment, national/local tax, insurance, consumer issues, transit, housing, shopping, weather, waste and parcel tracking. For k-skill backed workflows, the screen reads the actual /api/skills collection and distinguishes missing, registered draft and active records. Activation alone does not prove CLI or credentials work; the existing engine determines tool availability and the job must report evidence. The UI links to upstream skill documentation where the catalog does not have an authoritative government portal. The mobile app never installs a second agent runtime or runs unreviewed third-party scripts itself. Upstream: https://github.com/NomaDamas/k-skill .

Related registered skills can now be reviewed in full from the Korean service sheet and explicitly enabled or disabled through the native skill record API. The client checks the inspected record revision before sending the complete updated record and reads it back. This changes the engine-wide activation flag; no automatic activation or unreviewed import is performed. The upstream API does not provide atomic compare-and-swap, so concurrent desktop changes between read and write remain a limitation.

The daily-service and schedule screens now open a real device calendar reader. Android READ_CALENDAR is requested only on explicit read; CalendarContract.Instances queries visible occurrences over a chosen 1/7/30-day period. Users select up to 50 occurrence titles/times and explicitly submit a native agent task. Account identifiers, attendees, descriptions and unselected events are never included. The client does not silently modify the calendar or upload the full agenda. Android device testing remains pending.

Native voice input now optionally records bounded mono PCM WAV on explicit microphone permission, uses the real engine /api/voice/status and /api/voice/transcribe endpoints, retains failed recordings for retry, and inserts reviewed transcript into the existing chat draft. Message read-aloud calls /api/voice/speak, chunks long text, and uses cancellable native playback. Backgrounding cancels recording/playback and releases device media resources. Device recognition and existing LiveKit calls remain. These are turn-based voice operations, not a replacement for duplex live AI calls; actual Whisper/Piper installations and device audio tests are required.
