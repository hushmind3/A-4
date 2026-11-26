# Pickle Client — Kotlin + Jetpack Compose 재구성

기존 `pickle-client-1.1.20.apk`의 클라이언트 기능을 **Kotlin + Jetpack Compose 기반 안드로이드 네이티브 프로젝트**로 재구성한 소스입니다.
Rust, Tauri, WebView를 사용하지 않습니다. 서버는 포함하지 않으며, 앱은 데모 데이터로 완전히 동작하고 실제 백엔드에 연결할 수 있는 REST 계층을 함께 제공합니다.

> 이 저장소의 목표는 APK 제작이 아니라 **자유롭게 수정·확장할 수 있는 전체 클라이언트 소스코드**를 남기는 것입니다.
> 캐릭터 이미지, 로고, 원본 UI 번들은 복제하지 않았습니다. 색상·간격·타이포그래피 토큰은 APK에 포함된 디자인 번들에서 읽어 재구성했고, 캐릭터는 Compose로 그린 중립적인 도형입니다.

## 빠르게 실행

필요한 것:

- JDK 17 이상 (Android Studio 내장 JBR 사용 가능)
- Android SDK 35 (`ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`)
- Android 기기 또는 에뮬레이터 (API 32+)

```bash
./gradlew :client:assembleDebug
adb install -r client/build/outputs/apk/debug/client-debug.apk
```

Android Studio에서는 이 폴더를 열고 `client` 실행 구성을 선택하면 됩니다.

기본 실행은 **데모 모드**입니다. 로그인 화면의 `데모 모드로 둘러보기`를 누르면 서버 없이 모든 화면을 둘러볼 수 있습니다. 소셜 로그인·전화 인증·친구 초대·결제는 데모 동작으로 표시됩니다.

## 현재 구현된 클라이언트 기능

| 영역 | 구현 내용 |
| --- | --- |
| 온보딩 | 로그인(카카오·Google·Apple·LINE·네이버·전화), 전화번호 인증(데모), 이름, 생일, 동의, 가이드, 캐릭터 이름·목소리·색·모양, 주소록 안내, 완료 |
| 채팅 | 대화 목록, 고정/음소거, 읽지 않음, 대화 검색, 날짜 구분, 텍스트/카드 메시지, 입력 중 표시, 답장·복사·기억 저장, 대화 되감기, 비우기, 삭제, 탭(주제별 대화) |
| 통화 | 음성/영상 통화 화면, 발신·종료, 음소거·스피커·영상 전환, 통화 시간, 파형, 최소화, 통화 기록 카드, 포그라운드 서비스 |
| 캐릭터 | 프로필, 레벨/XP, 목소리, 기억, 할 일, 일정, 꾸미기(색·모양·이름·목소리), 차단 |
| 탐색 | 카테고리 칩, 추천 템플릿, 단계 미리보기, 캐릭터 선택 후 대화에 추가 |
| 친구 | 내 프로필, 친구 목록, 즐겨찾기, 친구 요청 수락/거절, 아이디·QR·주소록·초대 링크, 친구 프로필 |
| 보관함 | 사진·파일·링크 필터, 그리드, 상세 미리보기, 대화로 이동 |
| 프로필 | 캐릭터/계정 카드, 젤리·에너지·티켓·크레딧, 내 피클 목록, 바로가기, 받은 소식, 플랜 |
| 개인 기능 | 기억, 할 일(단계 체크), 일정(반복·알림), 금고(로그인·카드·주소·전화), 연결(OAuth/기본 도구) |
| 결제 UI | 젤리·에너지·티켓 상품, 플랜 비교, 구매 확인, 이용 내역, 출석 보상 |
| 설정 | 계정, 알림, 화면(라이트/다크/시스템), 언어(한국어/영어), 개인정보·앱 잠금, 차단 사용자, 탭 구성, 데이터 내보내기, 약관, 로그아웃, 계정 삭제 |
| 플랫폼 | 홈 화면 위젯 2종(4x2, 2x1), 통화 포그라운드 서비스, 공유 인텐트, 딥링크(`pickle://`, `https://pickle.com/u/`), 앱 잠금 오버레이, 알림 권한 요청 |

구현 상태와 원본 대비 차이는 [`docs/FEATURES.md`](docs/FEATURES.md)에 정리했습니다.

## 프로젝트 구조

```
.
├─ client/                         # 안드로이드 앱 모듈 (application)
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/pickle/client/
│     │  ├─ MainActivity.kt        # 단일 Activity + Compose
│     │  ├─ PickleApplication.kt
│     │  ├─ AppContainer.kt        # 데모/원격 저장소 선택
│     │  ├─ data/
│     │  │  ├─ model/Models.kt     # 도메인 모델
│     │  │  ├─ DemoData.kt         # 데모 시드
│     │  │  ├─ DemoReplies.kt      # 오프라인 응답 생성
│     │  │  ├─ PickleRepository.kt # UI가 의존하는 경계
│     │  │  ├─ local/LocalStore.kt # 마지막 상태 캐시
│     │  │  └─ remote/            # Retrofit API, DTO, 원격 저장소
│     │  ├─ ui/
│     │  │  ├─ AppViewModel.kt
│     │  │  ├─ PickleAppRoot.kt    # 테마 + NavHost
│     │  │  ├─ theme/              # 색·타이포·모양
│     │  │  ├─ components/         # 공통 UI, Jaum 아바타, 카드
│     │  │  └─ screens/            # 온보딩·채팅·탐색·친구·보관함·프로필·설정
│     │  └─ platform/              # 위젯, 통화 서비스
│     └─ res/
├─ docs/
│  ├─ BACKEND.md                   # 백엔드 연결 방법과 API 계약
│  └─ FEATURES.md                  # 기능별 구현 상태
└─ .github/workflows/android.yml   # GitHub Actions 빌드
```

## 백엔드 연결

1. `client/build.gradle.kts`의 `API_BASE_URL`을 내 서버 주소로 바꿉니다.
2. `USE_DEMO_DATA`를 `false`로 바꿉니다.
3. [`client/src/main/java/com/pickle/client/data/remote/PickleApi.kt`](client/src/main/java/com/pickle/client/data/remote/PickleApi.kt)의 경로를 실제 API에 맞춥니다.
4. `RemoteBackedRepository`가 서버 응답을 도메인 모델로 매핑합니다. 필요한 화면부터 연결하면 됩니다.
5. 로그인 토큰은 `LocalStore.authToken`에 저장되고 Retrofit 인터셉터가 `Authorization: Bearer …`로 전송합니다.

자세한 내용은 [`docs/BACKEND.md`](docs/BACKEND.md)를 참고하세요.

## 데이터와 상태

- `AppViewModel`이 단일 `StateFlow<AppState>`를 노출합니다.
- 모든 화면은 `AppState`만 보고 그리며, 사용자 동작은 ViewModel의 함수로 전달됩니다.
- 데모 저장소는 변경된 상태를 `SharedPreferences`에 JSON으로 저장해 앱을 다시 열어도 유지합니다.
- 네트워크 계층은 데모와 분리되어 있어 UI를 바꾸지 않고 실제 서버로 교체할 수 있습니다.
- 원격 채팅은 실제 HTTP 전송, 전송 상태·실패 재시도, 서버 메시지 동기화, 지연된 답변 확인을 지원합니다. 서버 측 API·인증·멱등 처리 구현이 필요하며 실시간 토큰 스트리밍은 별도 작업입니다.

## 소스만 GitHub에 올리기

`.gitignore`가 APK, 빌드 산출물, 서명 키, Craft Anything 기록, 확인용 캡처를 제외합니다. 원본 APK도 커밋하지 않도록 `*.apk`를 제외했습니다.

```bash
git init
git add .
git commit -m "Rebuild Pickle client as Kotlin + Jetpack Compose"
git branch -M main
git remote add origin https://github.com/<your-id>/<repo>.git
git push -u origin main
```

## 알려진 제한

원본 앱의 메인 화면은 서버가 내려주는 UI 번들로 그려집니다. APK 안에는 온보딩/법무 화면만 포함되어 있어, 메인 화면의 픽셀 단위 배치는 APK의 디자인 토큰·아이콘·기능 목록을 근거로 재구성한 것입니다. LiveKit 미디어 전송, 실제 OAuth SDK, FCM 푸시, 인앱 결제, AR/3D 스테이지, 지도/장소 검색, 어시스턴트 음성 서비스는 클라이언트 UI와 연결 지점만 두었고 실제 서비스 연동은 백엔드/키가 필요합니다.

원본의 브랜드·캐릭터 아트는 포함하지 않았습니다. 앱 이름과 화면 문구는 재구성용이며, 배포 전 자신의 브랜드로 바꾸는 것을 권장합니다.
