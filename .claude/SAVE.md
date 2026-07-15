# AIFashion 프로젝트 진행 상황

> 최종 확인일: 2026-07-14 (미커밋 상태 — 아래 "커밋 안 된 변경사항" 참고)

## 프로젝트 개요

- **이름**: AIFashion (Android 네이티브 앱, Kotlin + Jetpack Compose)
- **패키지**: `com.example.aifashion`
- **컨셉**: 다른 앱을 사용하다가 마음에 드는 옷 사진을 스크린샷으로 찍으면,
  자동으로 감지해서 플로팅 버튼("이 옷 입어보기 👕")을 띄우고,
  이미지를 크롭한 뒤 백엔드 AI 서버(rf-ai-server, VTON 미들웨어)에 전송해
  가상 피팅(virtual try-on) 결과 이미지를 받아오는 앱.
- Gradle 프로젝트명: `AIFashion` (settings.gradle.kts), 모듈은 `:app` 하나.
- **백엔드 API 명세서**: `.claude/API.md` — 서버팀이 준 실제 명세. 클라이언트 설계는
  반드시 이 문서 기준으로 맞춰야 함 (예전에는 클라이언트가 먼저 설계하고 서버가
  맞추기로 했었는데, 실제로 온 API는 그 설계와 완전히 다름 — 아래 "핵심 아키텍처 변경" 참고).

## 빌드 설정

- `compileSdk` / `targetSdk`: 36, `minSdk`: 26
- Java 11 호환성, Kotlin Compose 플러그인 사용
- 주요 의존성: Retrofit2 + OkHttp(로깅 인터셉터), Coroutines, Coil(이미지 로딩),
  uCrop(이미지 크롭 라이브러리), Compose Material3
- 버전: versionCode 1 / versionName "1.0"
- **리포지토리에 `gradlew`/`gradlew.bat` 래퍼가 없음**. 커맨드라인 빌드가 필요하면
  `~/.gradle/wrapper/dists/gradle-9.4.0-bin/*/gradle-9.4.0/bin/gradle.bat`를
  직접 호출해서 빌드 (이 세션에서 이 방식으로 `:app:installDebug`까지 확인함).
  adb 연결된 실기기(SM-S947N, Android 16)에 바로 설치해서 테스트 중.

## 아키텍처 & 핵심 흐름

1. **CaptureDetectionService** (`service/CaptureDetectionService.kt`)
   - 포그라운드 서비스로 상시 실행, `ContentObserver`로 MediaStore를 감시
   - "Screenshots" 폴더에 새 이미지가 생기면 감지 → 화면 우측 하단에
     플로팅 오버레이 버튼 표시(10초 후 자동 제거)
   - 버튼 클릭 시 `CropActivity` 실행

2. **CropActivity** (`ui/crop/CropActivity.kt`)
   - uCrop 라이브러리로 3:4 비율 고정 크롭 (VRAM 보호 목적), 최대 해상도 1080x1440
   - uCrop이 만든 Intent를 그대로 쓰지 않고 `AIFashionUCropActivity`로 리다이렉트
     (`setClass()`) — 아래 4번 참고
   - 크롭 완료 후 결과 URI를 `MainActivity`로 전달 (singleTop + onNewIntent)

3. **AIFashionUCropActivity** (`ui/crop/AIFashionUCropActivity.kt`, 신규)
   - uCrop 라이브러리의 `UCropActivity`를 상속한 커스텀 서브클래스
   - **왜 필요한가**: uCrop이 Android 15+ 강제 edge-to-edge 인셋을 자체 처리 못 해서
     상단 툴바(제목/X/체크)가 상태바에 가려지는 문제가 있음
     ([Yalantis/uCrop#913](https://github.com/Yalantis/uCrop/issues/913)).
     `targetSdk 36` + Android 16 기기에서는 매니페스트의
     `windowOptOutEdgeToEdgeEnforcement`도 무시되어(Android 16부터 이 opt-out 자체가
     폐지됨) 매니페스트 테마 수정만으로는 해결 불가.
   - **최종 해결 방식**: 상단 툴바를 아예 `GONE` 처리하고, 원래 잘 동작하던 하단
     Crop/Rotate/Scale 아이콘 줄(`wrapper_states`, uCrop 내부 뷰 ID) 양 끝에
     X(취소)/✓(확인) 아이콘을 직접 `addView`로 끼워 넣어 한 줄로 합침. 취소는
     `onBackPressed()`, 확인은 `cropAndSaveImage()`(uCrop의 `protected` 메서드라
     상속해야 호출 가능) 호출.
   - 여기 오기까지 시도했다가 실패한 방법들(다시 시도하지 말 것):
     - 매니페스트 테마에 `windowOptOutEdgeToEdgeEnforcement` 추가 → Android 15에선
       되는데 Android 16에선 무시됨
     - `Application` 클래스(`AIFashionApp`, 삭제됨)에서 액티비티 콜백으로
       `android.R.id.content`에 시스템 바 인셋만큼 패딩 주기 → 상단 툴바 자체는
       상태바 밑으로 내려왔지만, 그 뒤의 크롭 이미지 영역이 여전히 툴바 뒤로
       걸쳐 보이는 문제가 남음 (uCrop이 툴바를 이미지 위에 얹는 overlay 구조라
       패딩만으로는 안 됨)
     - `ucrop_frame`에 강제로 `topMargin` 주기 → 위와 동일한 이유로 근본 해결 안 됨
   - `AndroidManifest.xml`에 `com.yalantis.ucrop.UCropActivity` 대신
     `.ui.crop.AIFashionUCropActivity`로 등록되어 있음 (테마는 기존
     `Theme.AIFashion.UCrop` 그대로 유지)

4. **MainActivity** (`MainActivity.kt`)
   - 권한 순차 요청: POST_NOTIFICATIONS(선택) → READ_MEDIA_IMAGES →
     SYSTEM_ALERT_WINDOW → `CaptureDetectionService` 시작
   - Compose UI(`FittingScreen` + `BodyPhotoSection`, 세로 4:1 분할)로 상태별 렌더링
   - "AI 피팅 시작" 버튼 클릭 시, 선택된 몸사진 ID로 로컬 파일 경로를 찾아
     `FittingViewModel.submitFittingJob()`에 전달

5. **데이터 레이어 — 핵심 아키텍처 변경 (2026-07-14, API.md 반영)**

   기존에는 클라이언트가 먼저 설계한 job 생성+폴링 구조, 서버 측 몸사진 등록 API를
   가정하고 구현되어 있었는데, 실제로 서버팀이 준 `.claude/API.md`를 보니 완전히
   다른 설계였음. 전면 리팩터링함:

   | 항목 | 이전 (틀렸던 가정) | 현재 (API.md 기준) |
   |---|---|---|
   | 피팅 요청 | Job 생성 후 3초 간격 폴링 | **동기 방식** 단일 요청 (`POST /api/v1/vton/generate`, 최대 120초) |
   | 몸사진 저장 | 서버에 등록 API 있음 (`/profile/photos`) | **서버에 없음**. 클라이언트가 기기 로컬에 직접 보관 |
   | 결과 이미지 | `result_image_url`을 그대로 사용 | 상대경로라 `BASE_URL`을 붙여서 절대경로로 변환 필요 |
   | 옷 종류 | 없음 | `garment_type` 필드 있음(선택, 한국어 21종) — **아직 UI 미구현, 항상 null로 전송** |

   - `ApiService`: `generateFitting()` 단일 엔드포인트만 존재. `VtonGenerateResponse`
     모델(`FittingModels.kt`)로 응답 받음
   - `data/local/BodyPhotoStore.kt` (신규): 몸사진 파일을 `filesDir/body_photos/`에
     저장하고, 메타데이터(`LocalBodyPhoto`: photoId/filePath/createdAt)는
     SharedPreferences에 JSON으로 저장. 서버 왕복 없음
   - `BodyPhotoRepository`/`BodyPhotoViewModel`: 서버 호출 전부 제거, `BodyPhotoStore`
     기반 로컬 CRUD로 전환. 카드 UI(최대 5장, 선택/삭제)는 그대로 유지
   - `FittingViewModel`: 폴링 로직(`startPolling`, `maxPollCount` 등) 전부 제거.
     단일 요청 후 `result_image_url`에 `RetrofitClient.BASE_URL`을 붙여 절대경로로
     변환해서 `FittingUiState.Success`에 저장 (Coil이 그 URL로 바로 이미지 로드)
   - `AppConfig.kt` 삭제됨 (서버 `user_id` 더 이상 불필요 — 로컬 저장이라 기기별로
     알아서 분리됨)
   - `RetrofitClient.BASE_URL` = `http://192.168.0.105:8001/` (Docker 배포 서버,
     실제 LAN IP). PC와 테스트 기기가 같은 Wi-Fi 대역에 있어야 접속 가능
   - `network_security_config.xml` (신규): `targetSdk 28+`부터 cleartext(HTTP) 트래픽이
     기본 차단되는데 서버가 아직 HTTPS가 아니라서, `192.168.0.105`/`10.0.2.2` 두
     호스트만 명시적으로 cleartext 허용. 서버가 HTTPS로 전환되면 이 파일 제거 가능

## 현재 상태 요약

- 안드로이드 클라이언트: 스크린샷 감지 → 크롭 → 몸사진 로컬 등록/선택 →
  AI 피팅(동기 API) → 결과 표시/저장/공유까지 전체 플로우 구현되어 있고
  실기기(Android 16)에서 크롭 화면 UI까지 확인 완료
- **백엔드는 별도 프로젝트(rf-ai-server)로 이미 구축되어 실행 중** — API 명세는
  `.claude/API.md` 참고. Base URL은 배포 환경별로 다름(로컬 dev `:8000`,
  Docker `:8001`) — 지금은 Docker(`192.168.0.105:8001`)로 맞춰둔 상태
- 테스트 코드는 Android Studio 기본 생성 보일러플레이트뿐 (실질적 테스트 없음)
- **이 세션에서 만든 변경사항이 전부 커밋 안 된 상태** (`git status` 참고).
  다음 세션 시작하면 먼저 커밋할지 확인할 것

## 다음에 이어서 할 만한 작업 후보

- [ ] **커밋**: 이번 세션 변경사항(API.md 반영 리팩터링 + uCrop edge-to-edge 대응)
      전부 미커밋 상태 — 리뷰 후 커밋 필요
- [ ] `garment_type`(옷 종류 21종) 선택 UI 추가 — 지금은 항상 null로 전송 중.
      API.md 기준 결과 품질에 도움됨, 선택 필드라 필수는 아님
- [ ] 실제 기능 테스트 코드 작성 (BodyPhotoStore, FittingViewModel 등)
- [ ] 서버가 HTTPS로 전환되면 `RetrofitClient.BASE_URL`을 `https://`로 바꾸고
      `network_security_config.xml` 제거
- [ ] `AIFashionUCropActivity`의 X/✓ 아이콘 버튼 터치 영역/디자인 다듬기
      (지금은 최소 기능 구현 수준 — 텍스트 없이 ✕/✓ 유니코드 문자만 사용 중)
