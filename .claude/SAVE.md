# AIFashion 프로젝트 진행 상황

> 최종 확인일: 2026-07-15 (아래 "커밋 상태" 참고 — 이번 세션 UI 수정분은 미커밋)

## 프로젝트 개요

- **이름**: AIFashion (Android 네이티브 앱, Kotlin + Jetpack Compose)
- **패키지**: `com.example.aifashion`
- **컨셉**: 다른 앱을 사용하다가 마음에 드는 옷 사진을 스크린샷으로 찍으면,
  자동으로 감지해서 플로팅 버튼("이 옷 입어보기 👕")을 띄우고,
  이미지를 크롭한 뒤 백엔드 AI 서버(rf-ai-server, VTON 미들웨어)에 전송해
  가상 피팅(virtual try-on) 결과 이미지를 받아오는 앱.
- Gradle 프로젝트명: `AIFashion` (settings.gradle.kts), 모듈은 `:app` 하나.
- **백엔드 API 명세서**: `.claude/API.md` — 서버팀이 준 실제 명세. 클라이언트 설계는
  반드시 이 문서 기준으로 맞춰야 함.

## 빌드 설정

- `compileSdk` / `targetSdk`: 36, `minSdk`: 26
- Java 11 호환성, Kotlin Compose 플러그인 사용
- 주요 의존성: Retrofit2 + OkHttp(로깅 인터셉터), Coroutines, Coil(이미지 로딩),
  uCrop(이미지 크롭 라이브러리), Compose Material3
- 버전: versionCode 1 / versionName "1.0"
- **리포지토리에 `gradlew`/`gradlew.bat` 래퍼가 없음**. 커맨드라인 빌드가 필요하면
  `~/.gradle/wrapper/dists/gradle-9.4.0-bin/*/gradle-9.4.0/bin/gradle.bat`를
  직접 호출해서 빌드. adb 연결된 실기기(SM-S947N, Android 16)에 바로
  설치해서 테스트 중 (`:app:installDebug`로 이번 세션에도 재확인함).

## 아키텍처 & 핵심 흐름

1. **CaptureDetectionService** (`service/CaptureDetectionService.kt`)
   - 포그라운드 서비스로 상시 실행, `ContentObserver`로 MediaStore를 감시
   - "Screenshots" 폴더에 새 이미지가 생기면 감지 → 화면 우측 하단에
     플로팅 오버레이 버튼 표시(10초 후 자동 제거)
   - 버튼 클릭 시 `CropActivity` 실행
   - **플로팅 버튼 디자인 (2026-07-15 수정)**: 기존엔 그냥 사각형 `Button` +
     단색 배경이라 다른 앱 화면 위에서 잘 안 보인다는 피드백 → 알약(pill) 모양
     (`GradientDrawable`, `cornerRadius = 999f`) + 흰색 테두리(`setStroke`) +
     그림자(`elevation = 16f`) + 볼드체로 시인성 개선. 색상은 오렌지(`#FF6B35`)
     → 블루(`#2979FF`)로 변경. 위치도 화면 하단에서 좀 더 위로 올림
     (`x = 50, y = 300`, `Gravity.BOTTOM or END` 기준 여백)

2. **CropActivity** (`ui/crop/CropActivity.kt`)
   - uCrop 라이브러리로 3:4 비율 고정 크롭 (VRAM 보호 목적), 최대 해상도 1080x1440
   - uCrop이 만든 Intent를 그대로 쓰지 않고 `AIFashionUCropActivity`로 리다이렉트
     (`setClass()`) — 아래 4번 참고
   - 크롭 완료 후 결과 URI를 `MainActivity`로 전달 (singleTop + onNewIntent)

3. **AIFashionUCropActivity** (`ui/crop/AIFashionUCropActivity.kt`)
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
     - `Application` 클래스에서 액티비티 콜백으로 `android.R.id.content`에 시스템 바
       인셋만큼 패딩 주기 → 상단 툴바는 내려왔지만 그 뒤 크롭 이미지 영역이 여전히
       툴바 뒤로 걸쳐 보이는 문제가 남음 (uCrop이 툴바를 이미지 위에 얹는 overlay
       구조라 패딩만으로는 안 됨)
     - `ucrop_frame`에 강제로 `topMargin` 주기 → 위와 동일한 이유로 근본 해결 안 됨
   - `AndroidManifest.xml`에 `com.yalantis.ucrop.UCropActivity` 대신
     `.ui.crop.AIFashionUCropActivity`로 등록되어 있음 (테마는 기존
     `Theme.AIFashion.UCrop` 그대로 유지)

4. **MainActivity** (`MainActivity.kt`)
   - 권한 순차 요청: POST_NOTIFICATIONS(선택) → READ_MEDIA_IMAGES →
     SYSTEM_ALERT_WINDOW → `CaptureDetectionService` 시작
   - Compose UI(`FittingScreen` + `BodyPhotoSection`, 세로 분할)로 상태별 렌더링.
     상단(옷 캡처/피팅) `weight(3f)` : 하단(내 몸 사진 카드) `weight(1.2f)`
     (몸 사진이 잘 안 보인다는 피드백으로 기존 `1f`→`1.5f`로 키웠다가, 이번
     세션에 다시 `1.2f`로 살짝 낮춤)
   - "AI 피팅 시작" 버튼 클릭 시, 선택된 몸사진 ID로 로컬 파일 경로를 찾아
     `FittingViewModel.submitFittingJob()`에 전달

5. **BodyPhotoSection** (`ui/bodyphoto/BodyPhotoSection.kt`)
   - 화면 하단 영역: 내 몸 사진 카드 리스트 (최대 5장, 가로 스크롤, 선택/삭제)
   - 상단 안내 문구를 "피팅룸(본인 사진 선택)" → **"피팅해볼 사진을 선택하세요."**로
     변경 (2026-07-15, 사용자에게 더 명확한 지시문으로)

6. **앱 아이콘** (`res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`,
   2026-07-15 신규 디자인)
   - 기존: 오렌지 배경 + 흰색 쇼핑백 벡터 아이콘
   - 변경: 배경은 진한 블루(`#0D47A1`), 전경은 흰색 픽셀(도트) 블록을 쌓아 만든
     8비트 게임 스프라이트 느낌의 티셔츠 모양. 목선 칼라 노치 부분에 연한 블루
     (`#64B5F6`) 포인트 블록 추가. adaptive icon(`mipmap-anydpi-v26`) 구조 그대로,
     drawable 두 개만 교체

7. **데이터 레이어 — 핵심 아키텍처 (API.md 반영, 커밋 완료)**

   서버팀이 준 `.claude/API.md` 기준으로 전면 리팩터링된 상태:

   | 항목 | 내용 |
   |---|---|
   | 피팅 요청 | **동기 방식** 단일 요청 (`POST /api/v1/vton/generate`, 최대 120초) |
   | 몸사진 저장 | 서버에 없음. 클라이언트가 기기 로컬(`BodyPhotoStore`)에 직접 보관 |
   | 결과 이미지 | `result_image_url`(상대경로)에 `BASE_URL`을 붙여 절대경로로 변환 |
   | 옷 종류 | `garment_type` 필드 있음(선택, 한국어 21종) — **아직 UI 미구현, 항상 null로 전송** |

   - `ApiService`: `generateFitting()` 단일 엔드포인트만 존재. `VtonGenerateResponse`
     모델(`FittingModels.kt`)로 응답 받음
   - `data/local/BodyPhotoStore.kt`: 몸사진 파일을 `filesDir/body_photos/`에 저장,
     메타데이터(`LocalBodyPhoto`: photoId/filePath/createdAt)는 SharedPreferences에
     JSON으로 저장. 서버 왕복 없음
   - `BodyPhotoRepository`/`BodyPhotoViewModel`: `BodyPhotoStore` 기반 로컬 CRUD
   - `FittingViewModel`: 폴링 없이 단일 요청 후 `result_image_url`을
     `RetrofitClient.BASE_URL` 붙여서 절대경로로 변환해 `FittingUiState.Success`에 저장
   - **`RetrofitClient.BASE_URL` = `https://vfs.rusty.ai.kr/`** (HTTPS로 전환 완료).
     예전엔 `http://192.168.0.105:8001/`(Docker LAN IP, cleartext)이었는데 서버가
     HTTPS 도메인으로 옮겨가면서 `network_security_config.xml`(cleartext 허용용)도
     더 이상 매니페스트에서 참조하지 않음 — 관련 TODO 완료된 상태

## 커밋 상태 / 주의할 점

- `44ab2c7` → `5abbf51`(2026.07.15 "1단계 완료") 커밋으로 API.md 리팩터링 +
  uCrop edge-to-edge 대응 등 이전 세션의 미커밋 변경사항이 전부 커밋됨
- **이번 세션(2026-07-15)에서 추가로 수정한 것들은 아직 커밋 안 됨**:
  `BodyPhotoSection.kt`(안내 문구), `MainActivity.kt`(weight 1.5f→1.2f),
  `CaptureDetectionService.kt`(플로팅 버튼 디자인/색상/위치),
  `ic_launcher_background.xml`/`ic_launcher_foreground.xml`(아이콘 교체)
- 실기기(SM-S947N)에 `installDebug`로 설치해서 아이콘/버튼 변경 확인 완료

## 다음에 이어서 할 만한 작업 후보

- [ ] **커밋**: 이번 세션 UI 수정분(버튼/아이콘/문구/레이아웃) 리뷰 후 커밋 필요
- [ ] `garment_type`(옷 종류 21종) 선택 UI 추가 — 지금은 항상 null로 전송 중.
      API.md 기준 결과 품질에 도움됨, 선택 필드라 필수는 아님
- [ ] 실제 기능 테스트 코드 작성 (BodyPhotoStore, FittingViewModel 등)
- [ ] `AIFashionUCropActivity`의 X/✓ 아이콘 버튼 터치 영역/디자인 다듬기
      (지금은 최소 기능 구현 수준 — 텍스트 없이 ✕/✓ 유니코드 문자만 사용 중)
