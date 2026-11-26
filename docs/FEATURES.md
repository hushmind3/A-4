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

## Existing tool integration

Existing connections and task templates remain unchanged. The connections screen now reads the engine's real tool registry, including approval flags and schemas. Chat reads the native room execution snapshot, live room-scoped approvals, job states and operation results. Explicit approval/denial and job cancellation use existing engine endpoints; approval arguments and digest are rechecked before submission. Photo/file pickers and camera preview upload actual selected bytes (8 MiB limit) to the existing room-bound attachment endpoint; native transcript metadata preserves attachment IDs across reloads. File cards save the original attachment through the system document picker. The microphone button uses Android's installed speech recognizer with Korean language selection and inserts editable text, not automatic sending. These are source integrations; device build and live endpoint testing are pending. Existing service OAuth, device-wide agent actions, full-resolution camera, video and other unconnected features remain in the product design and are not claimed to work.

## 외부 서비스와 기존 통화 기능

원래 17종 연결 목록과 12종 생활 템플릿을 보존한다. 실제 본체 OAuth 제공자와 승인 범위 조회, 본체 브라우저 인증 시작/해제, HTTPS 외부 도구 서버 수동 등록 및 토큰 전달, 등록 확인/도구 검색/사용 중지, 임의 서비스 웹 로그인 화면을 연결했다. 본체 계정 인증은 등록된 제공자와 실제 grant를 구분한다. 수동 웹 로그인은 본체 PC에서 완료해야 한다. 자체 OAuth 동적 등록/갱신과 개별 서비스별 권한 강제는 본체에서 지원할 때까지 완료로 표시하지 않는다. 앱에는 입력한 외부 토큰을 영구 저장하지 않는다. 기존 LiveKit 통화의 영상 트랙 송수신, 실제 출력 장치 변경, 음량 파형, 통화 기록과 기기 TTS 미리 듣기 코드도 반영했다. 실제 AI 미디어 참가자 및 기기 실행 검증은 별도 필요하다.


## Background job notifications

After a native room job is accepted, a unique periodic WorkManager check reads the existing engine job record and pending approvals. The mobile worker never executes the job: DOMINION remains responsible for all actions, memory and approval decisions. The worker derives its origin from the encrypted current engine connection and never stores the owner token in WorkManager input. Notification text omits prompts, arguments and credentials. Android 13+ permission is requested from the room execution controls.

This is best-effort background polling (15-minute minimum interval, possibly delayed by the OS), NOT realtime push. Tap a notification to open its conversation (once it exists in the server-synced room list) for authoritative details. Engine origin switches invalidate old watchers. Physical device testing, FCM push and job-specific deep links remain pending.
