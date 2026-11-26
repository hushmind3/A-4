# 백엔드 연결 가이드

이 클라이언트는 **서버 없이도 모든 화면이 동작하는 데모 모드**와 **실제 REST 서버에 붙는 원격 모드**를 모두 가집니다. UI 코드는 두 모드를 구분하지 않습니다.

## 1. 연결 스위치

`client/build.gradle.kts`:

```kotlin
defaultConfig {
    buildConfigField("String", "API_BASE_URL", "\"https://api.your-domain.com/\"")
    buildConfigField("boolean", "USE_DEMO_DATA", "false")
}
```

- `API_BASE_URL`은 반드시 `/`로 끝나야 합니다(Retrofit 규칙).
- `USE_DEMO_DATA = false`이면 `AppContainer`가 `RemoteBackedRepository`를 선택합니다.
- 앱 시작 시 `AppViewModel.init`이 `refresh()`를 호출해 계정·캐릭터·대화·할 일·일정·기억·친구·연결·탐색·금고를 한 번에 가져옵니다. 실패한 항목은 로컬 스냅샷을 유지합니다.

## 2. 인증

모든 요청에 다음 헤더가 붙습니다(`ApiFactory`).

```
Accept: application/json
Authorization: Bearer <token>
X-Client-Version: <versionName>
X-Client-Platform: android
```

로그인 API가 성공하면 받은 `token`을 `LocalStore.authToken`에 저장하세요. 데모에서는 저장하지 않습니다.

| Method | Path | Body | Response |
| --- | --- | --- | --- |
| POST | `v1/auth/{provider}` | `SignInRequest` | `AuthResponse` |
| POST | `v1/auth/phone/start` | `PhoneStartRequest` | `DataResponse<String>` |
| POST | `v1/auth/phone/verify` | `PhoneVerifyRequest` | `AuthResponse` |

`provider`: `kakao`, `google`, `apple`, `line`, `naver`, `facebook`

원본은 Stytch SDK와 카카오/구글/애플/라인 SDK를 사용했습니다. 이 소스는 SDK 의존성을 넣지 않고 버튼과 흐름을 재현했습니다. 실제 로그인을 붙일 때는 각 SDK의 콜백에서 받은 토큰을 `SignInRequest.provider_token`으로 보내면 됩니다.

## 3. API 계약

기본 응답 형태:

```json
{ "data": { ... } }
```

목록:

```json
{ "data": [ ... ] }
```

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `v1/me` | 내 계정, 플랜, 젤리·에너지·티켓·크레딧 |
| PATCH | `v1/me` | 이름·생일 등 프로필 수정 |
| GET | `v1/characters` | 내 피클과 친구의 피클 |
| GET | `v1/conversations` | 대화 목록(마지막 메시지 포함) |
| GET | `v1/conversations/{id}` | 대화 상세 |
| GET | `v1/conversations/{id}/messages?before=` | 메시지 페이지 |
| POST | `v1/conversations/{id}/messages` | 메시지 전송 |
| POST | `v1/conversations/{id}/calls` | 통화방 토큰 발급 |
| GET | `v1/tasks` | 할 일 |
| GET | `v1/schedules` | 일정 |
| GET | `v1/memories?character_id=` | 기억 |
| GET | `v1/friends` | 친구 |
| GET | `v1/friends/requests` | 친구 요청 |
| POST | `v1/friends/requests/{id}/accept` | 수락 |
| GET | `v1/connections` | 연결 도구 |
| POST | `v1/connections/{id}/connect` | OAuth 연결 시작/콜백 |
| POST | `v1/connections/{id}/disconnect` | 연결 해제 |
| GET | `v1/explore?category=` | 탐색 템플릿 |
| GET | `v1/vault` | 금고 항목(서버에서 암호화) |
| GET | `v1/credits/transactions` | 젤리 이용 내역 |
| GET | `v1/inbox` | 받은 소식 |

DTO 필드는 [`client/src/main/java/com/pickle/client/data/remote/NetworkModels.kt`](../client/src/main/java/com/pickle/client/data/remote/NetworkModels.kt)에 있습니다. 서버 응답이 다르면 DTO와 `toDomain()`만 고치면 화면은 그대로 동작합니다.

### 메시지 예시

```json
{
  "id": "msg_123",
  "conversation_id": "conv_momo",
  "sender": "character",
  "text": "오후 3시 디자인 리뷰가 있네요.",
  "kind": "text",
  "created_at": 1795700000000,
  "card": {
    "kind": "task",
    "title": "오늘 리뷰 준비",
    "subtitle": "발표 자료와 질문을 미리 정리해요",
    "meta": "2 / 3 완료",
    "steps": [
      { "text": "발표 자료 확인", "done": true },
      { "text": "질문 3개 준비", "done": true },
      { "text": "30분 전 알림 받기", "done": false }
    ]
  }
}
```

`sender`: `me` | `character` | `system`
`kind`: `text` | `task` | `call` | `file` | `link` | `schedule` | `image` | `voice` | `system`
`card.kind`: `none` | `task` | `call` | `file` | `link` | `schedule` | `connection` | `image`

`created_at`은 epoch milliseconds입니다.

## 4. 실시간 채팅

현재 클라이언트는 전송 후 850ms 뒤 데모 응답을 넣습니다. 실제 서버에서는 다음 중 하나를 붙이세요.

- **WebSocket**: `wss://api.../v1/stream?conversation_id=...`
- **SSE**: `GET v1/conversations/{id}/events`

이벤트 예:

```json
{ "type": "message.delta", "message_id": "msg_124", "delta": "안녕" }
{ "type": "message.completed", "message": { ...MessageDto... } }
{ "type": "typing", "conversation_id": "conv_momo", "active": true }
```

`AppViewModel.sendMessage`의 지연 응답 부분을 스트림 수신으로 교체하고, `state.typingConversationId`를 이벤트에 맞춰 갱신하면 됩니다.

## 5. 음성/영상 통화

`POST v1/conversations/{id}/calls`는 다음을 반환합니다.

```json
{
  "data": {
    "room": "call_abc",
    "token": "livekit-jwt",
    "url": "wss://livekit.your-domain.com",
    "expires_at": 1795700600000
  }
}
```

원본은 LiveKit(`livekit.org.webrtc`)를 사용했습니다. 이 소스에는 통화 UI, 시간, 음소거·스피커·영상 토글, 최소화, 통화 기록 저장, 포그라운드 서비스가 구현되어 있습니다. 실제 미디어는 다음 순서로 붙이면 됩니다.

1. `client/build.gradle.kts`에 LiveKit Android SDK를 추가합니다.
2. `CallForegroundService`에서 통화 세션을 시작합니다.
3. `CallScreen`의 토글을 LiveKit `Room`/`LocalParticipant`에 연결합니다.
4. 종료 시 `POST v1/conversations/{id}/calls/{callId}/end`로 기록을 남깁니다.

서버가 LiveKit을 쓰지 않는다면 같은 UI에 WebRTC·Agora·Twilio를 연결해도 됩니다.

## 6. OAuth 연결(Connections)

`connect`는 실제 OAuth 동의 화면을 여는 URL을 반환하는 방식이 좋습니다.

```json
{ "data": { "authorize_url": "https://accounts.google.com/o/oauth2/v2/auth?..." } }
```

앱에서는 Custom Tabs로 열고, 콜백 딥링크(`pickle://oauth/...`)를 받아 서버에 코드를 전달합니다. 이 소스는 연결 상태와 권한 범위를 서버에서 받아 표시하고, 연결/해제를 데모로 처리합니다.

## 7. 금고(Vault)

원본은 기기에서 암호화하는 금고를 제공합니다. 이 소스는 `VaultItem`과 잠금 화면, 추가·삭제·복사·보기 UI를 구현했습니다. 실제 보안을 위해서는:

- Android Keystore로 AES 키를 만들고
- `EncryptedSharedPreferences` 또는 SQLCipher에 암호문을 저장하며
- 생체 인증(`BiometricPrompt`) 성공 후에만 복호화하세요.

서버에 보관할 경우에도 평문을 보내지 말고 클라이언트에서 암호화한 값만 전송하는 구성을 권장합니다.

## 8. 푸시 알림

현재 `POST_NOTIFICATIONS` 권한 요청과 알림 설정 UI만 있습니다. FCM을 쓰려면:

1. Firebase 프로젝트를 만들고 `google-services.json`을 넣습니다.
2. `com.google.gms.google-services` 플러그인과 Firebase Messaging 의존성을 추가합니다.
3. `FirebaseMessagingService`를 만들어 `conversation_id`, `type`을 읽고 `MainActivity`로 딥링크합니다.
4. `TaskProgressDismissReceiver` 같은 작업 알림 액션을 추가합니다.

## 9. 결제

`purchase`와 `StoreProduct`는 UI와 상태 변경만 구현되어 있습니다. Google Play Billing을 붙일 때는 상품 ID를 `StoreProduct.id`에 맞추고, 구매 토큰을 서버에서 검증한 뒤 `Account`를 갱신하세요. 데모 결제는 실제 청구를 하지 않습니다.

## 10. 오류·오프라인 처리

- `RemoteBackedRepository.refresh()`는 각 요청을 `runCatching`으로 격리해 일부 실패가 전체 화면을 망가뜨리지 않게 합니다.
- 마지막 성공 상태는 `LocalStore`에 저장됩니다.
- 401 응답 시 토큰을 지우고 온보딩으로 보내는 인터셉터를 추가하는 것을 권장합니다.
- 메시지 전송은 현재 전송 성공을 가정합니다. 실제 서버에서는 `MessageStatus.SENDING`으로 먼저 추가하고 실패 시 `FAILED`로 바꾸세요.

## 11. 서버 개발 체크리스트

- [ ] `/v1/me`가 계정·플랜·젤리·에너지를 반환
- [ ] 대화 목록에 마지막 메시지와 unread 포함
- [ ] 메시지 카드(task/file/link/call) 직렬화
- [ ] 통화 토큰 발급과 종료 기록
- [ ] OAuth authorize URL과 콜백
- [ ] 금고 암호화 저장
- [ ] 친구 요청·수락
- [ ] FCM 토큰 등록
- [ ] 결제 영수증 검증
