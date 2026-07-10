# AIFashion 프로젝트 진행 상황

> 최종 확인일: 2026-07-10

## 프로젝트 개요

- **이름**: AIFashion (Android 네이티브 앱, Kotlin + Jetpack Compose)
- **패키지**: `com.example.aifashion`
- **컨셉**: 다른 앱을 사용하다가 마음에 드는 옷 사진을 스크린샷으로 찍으면,
  자동으로 감지해서 플로팅 버튼("이 옷 입어보기 👕")을 띄우고,
  이미지를 크롭한 뒤 백엔드 AI 서버(IDM-VTON 추정)에 전송해
  가상 피팅(virtual try-on) 결과 이미지를 받아오는 앱.
- Gradle 프로젝트명: `AIFashion` (settings.gradle.kts), 모듈은 `:app` 하나.

## 빌드 설정

- `compileSdk` / `targetSdk`: 36, `minSdk`: 26
- Java 11 호환성, Kotlin Compose 플러그인 사용
- 주요 의존성: Retrofit2 + OkHttp(로깅 인터셉터), Coroutines, Coil(이미지 로딩),
  uCrop(이미지 크롭 라이브러리), Compose Material3
- 버전: versionCode 1 / versionName "1.0" — 아직 초기 단계

## 아키텍처 & 핵심 흐름

1. **CaptureDetectionService** (`service/CaptureDetectionService.kt`)
   - 포그라운드 서비스로 상시 실행, `ContentObserver`로 MediaStore를 감시
   - "Screenshots" 폴더에 새 이미지가 생기면 감지 → 화면 우측 하단에
     플로팅 오버레이 버튼 표시(10초 후 자동 제거)
   - 버튼 클릭 시 `CropActivity` 실행

2. **CropActivity** (`ui/crop/CropActivity.kt`)
   - uCrop 라이브러리로 3:4 비율 고정 크롭 (IDM-VTON 모델 VRAM 보호 목적)
   - 최대 해상도 1080x1440으로 제한
   - 크롭 완료 후 결과 URI를 `MainActivity`로 전달 (singleTop + onNewIntent)

3. **MainActivity** (`MainActivity.kt`)
   - 권한 순차 요청: POST_NOTIFICATIONS(선택) → READ_MEDIA_IMAGES →
     SYSTEM_ALERT_WINDOW → `CaptureDetectionService` 시작
   - Compose UI(`FittingScreen`)로 `FittingUiState`(Idle/Loading/Success/Error)에
     따라 화면 렌더링
   - "AI 피팅 시작" 버튼 클릭 시 `FittingViewModel.submitFittingJob()` 호출

4. **데이터 레이어**
   - `ApiService` (Retrofit 인터페이스): `POST /api/v1/fitting/jobs`(멀티파트 업로드,
     202 + job_id 응답), `GET /api/v1/fitting/jobs/{job_id}`(폴링, status/result_image_url)
   - `RetrofitClient`: `BASE_URL = http://10.0.2.2:8000/` (에뮬레이터용 localhost,
     **TODO 주석으로 실제 서버 IP 교체 필요 명시되어 있음**)
   - `FittingRepository`: ApiService를 감싸는 단순 위임 레이어
   - `FittingViewModel`: 이미지 업로드 → job 생성 → 3초 간격 폴링(최대 60회,
     3분 타임아웃) → 완료/실패 상태 반영

## 현재 상태 요약

- 커밋 2개만 존재: `first-commit`(2026.03.31) → `테스트 포함`(2026.04.01)
- 안드로이드 클라이언트 코드는 **기능적으로 완성된 상태**로 보임
  (권한 처리, 스크린샷 감지, 크롭, 업로드, 폴링, 결과 표시까지 전체 플로우 구현됨)
- **백엔드 서버 코드는 저장소에 없음** — 별도 프로젝트/미구현 상태로 추정
  (Retrofit `BASE_URL`이 로컬 에뮬레이터 주소로 하드코딩된 TODO 상태)
- 테스트 코드는 Android Studio 기본 생성 보일러플레이트뿐
  (`ExampleUnitTest`, `ExampleInstrumentedTest`) — 실질적 테스트 없음
- 작업 트리에 `.idea/misc.xml` 수정 사항이 스테이징 안 된 상태로 남아 있음(IDE 설정 파일)

## 다음에 이어서 할 만한 작업 후보

- [ ] `RetrofitClient.BASE_URL`을 실제 서버 주소로 교체 (백엔드는 이미 구축 완료됨)
- [ ] 실제 기능 테스트 코드 작성 (ViewModel 폴링 로직, Repository 등)
- [ ] 스크린샷 감지 로직이 Android 13+ Scoped Storage 환경에서 정상 동작하는지 검증

## 설계 중: 몸사진 + 옷사진 2장 업로드 기능 (2026-07-10 설계 논의)

**배경**: 백엔드는 이미 구축되어 있음. 현재 클라이언트는 옷 이미지 1장 + `profile_id`(문자열)만
보내는 구조인데, 실제 **내 몸 사진(이미지 파일)** 을 함께 보내야 피팅이 가능하도록 개편 필요.

### 확정된 UX 설계

- **메인 화면 레이아웃**: 세로 4:1 분할
  - **상단(4)**: "옷 캡처 영역" — 기존 `CaptureDetectionService`의 스크린샷 감지 로직을
    재사용. 다른 앱에서 스크린샷을 찍고 이 앱으로 돌아오면, 감지된 최신 스크린샷을
    상단 영역에 실시간 미리보기로 표시 + 크롭 버튼(기존 `CropActivity`, 3:4 uCrop 그대로 사용).
    앱이 백그라운드일 때는 기존처럼 플로팅 오버레이 버튼 방식 유지.
  - **하단(1)**: "내 몸 사진" 카드 섹션 — 가로 스크롤 카드 리스트, **최대 5장**.
    - `+` 카드로 사진첩에서 새 사진 추가 (Android Photo Picker `PickVisualMedia` 사용 예정 —
      이 경우 몸사진 선택 자체에는 READ_MEDIA_IMAGES 권한 불필요)
    - 카드 탭 = 피팅에 사용할 몸사진으로 선택(하이라이트 표시)
    - **5장 초과 정책**: 가득 찼을 때는 사용자가 기존 사진을 직접 삭제해야 새 사진 추가 가능
      (자동 FIFO 교체 아님 — 사용자가 명시적으로 통제)
  - **저장 방식**: 몸사진은 한 번 등록하면 로컬/서버에 저장되어 재사용됨 (앱 켤 때마다 새로
    고르지 않아도 됨). "다시 고르기"는 카드 리스트에서 다른 카드를 선택하거나 새로 추가하는 방식.

### 데이터 레이어 변경 (API 스펙 확정 — 클라이언트 측에서 설계, 백엔드가 맞추기로 함)

몸사진은 **ID 참조 방식** 채택: 등록은 한 번만 하고, 이후 피팅 요청 시엔 파일 재업로드 없이
`photo_id`만 전달 (파일 재업로드 방식 대비 매 요청마다 이미지 전송 안 해도 돼서 빠르고 데이터 절약).
이미 "저장해서 재사용 + 카드 5장 관리 + 삭제 필요"로 설계했으므로 자연스럽게 이 방식과 맞음.

1. **몸사진 등록**
   ```
   POST /api/v1/profile/photos
   Multipart: photo (file), user_id (string, 기본값 "default_user_1")
   → 201 { photo_id, image_url, created_at }
   ```
2. **몸사진 목록 조회** (앱 재실행 시 카드 리스트 복원용)
   ```
   GET /api/v1/profile/photos?user_id=default_user_1
   → 200 { photos: [ { photo_id, image_url, created_at }, ... ] }
   ```
   서버도 5장 제한 검증 권장 (6번째 등록 시 `409 Conflict`) — 클라이언트 제한과 이중 방어.
3. **몸사진 삭제**
   ```
   DELETE /api/v1/profile/photos/{photo_id}
   → 204 No Content
   ```
4. **피팅 작업 생성** (기존 엔드포인트 필드 변경: `profile_id` → `body_photo_id`)
   ```
   POST /api/v1/fitting/jobs
   Multipart: target_image (file, 옷 사진), body_photo_id (string, 등록 시 받은 photo_id)
   → 202 { job_id }
   ```
5. **상태 조회**: 기존 그대로 유지 (`GET /api/v1/fitting/jobs/{job_id}`)

### 결과 이미지 처리 방식 (확정)

- 기존처럼 앱 안에서 바로 표시(`FittingUiState.Success` + AsyncImage)하는 건 유지.
- **저장(갤러리 저장) + 공유 버튼만 추가**. 저장은 `CaptureDetectionService`에서 이미 쓰던 것과
  유사하게 `MediaStore`에 다운로드해서 넣는 방식, 공유는 Android 기본 Sharesheet(Intent)로 처리.
- **히스토리/보관함(과거 피팅 결과 모아보기) 기능은 만들지 않기로 결정** — 필요성이 불확실하고,
  서버가 `result_image_url`을 얼마나 오래 유지하는지도 불확실해서 지금 범위에서 제외.

### 보안 고려사항 (추후 구현 — 지금 구현 범위에서는 제외)

- **이미지 암호화**: 몸사진/옷사진/결과이미지는 민감한 개인 이미지이므로, 추후 아래 지점에
  암호화를 추가하는 것을 고려:
  - 전송 구간: 현재 `RetrofitClient`가 평문 HTTP(`http://10.0.2.2:8000/`)를 쓰고 있음 —
    실 서버 연동 시 HTTPS(TLS)로 전환 필요 (최소한의 전송 구간 보호, 사실상 필수)
  - 로컬 저장 구간: 몸사진 카드/크롭 이미지가 앱 캐시나 `MediaStore`에 평문으로 저장됨 —
    추후 필요 시 `EncryptedFile`/`Jetpack Security Crypto` 라이브러리로 로컬 캐시 파일을
    암호화하는 방안 검토 가능
  - 서버 저장 구간: 백엔드가 몸사진을 어떻게 저장하는지(암호화 여부)는 클라이언트 설계 범위
    밖이라 백엔드 쪽 확인 필요
- 지금 구현 단계에서는 위 항목들을 적용하지 않고, **추후 별도 작업으로 진행** — 현재는 기능
  구현(2장 업로드 + 결과 저장/공유)에만 집중.

### 아직 코딩 시작 안 함

API 스펙까지 확정됐으므로 다음 세션에서는 바로 구현 착수 가능. 구현 순서 제안:
1. `ApiService`/`FittingModels`에 신규 엔드포인트(등록/목록/삭제) 추가, `profile_id` → `body_photo_id`로 필드명 변경
2. 몸사진 카드 리스트 UI + Photo Picker 연동 + 로컬 캐싱(선택된 photo_id 등 DataStore/SharedPreferences)
3. `MainActivity` 레이아웃을 세로 4:1 분할로 개편
4. `FittingViewModel.submitFittingJob`이 `body_photo_id`도 함께 넘기도록 수정
5. 결과 화면에 저장(MediaStore) + 공유(Sharesheet) 버튼 추가