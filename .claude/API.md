# rf-ai-server API 명세서

VTON(가상 피팅) 미들웨어 API. 인물 이미지 + 의상 이미지를 전송하면 피팅 결과 이미지를 반환한다.

- Base URL:
    - **내부 테스트**: `http://192.168.0.105:8001` (Docker 컨테이너)
    - **외부 PRD**: `https://vfs.rusty.ai.kr`
- 모든 응답은 rf-ai-server 자신의 도메인만 가리킨다 (ComfyUI 등 외부 서버 주소를 클라이언트에 노출하지 않음)

---

## 1. POST /api/v1/vton/generate

인물 이미지와 의상 이미지를 업로드해서 피팅 이미지를 생성한다.

### Request2

`Content-Type: multipart/form-data`

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `person_image` | File (jpg/png) | O | 인물 사진 |
| `garment_image` | File (jpg/png) | O | 의상 사진 |
| `garment_type` | String | X | 옷 종류 한국어 라벨 (아래 표 참고). 미지정 시 종류 특정 없이 처리 |
| `retain_images` | String (`keep` \| `delete`) | X (기본 `delete`) | 이미지 서버 보관 여부. 인물/의상 사진, 피팅 결과 이미지 모두 프라이버시에 민감한 데이터이므로 **명시적으로 `keep`을 요청하지 않는 한 기본값은 삭제**된다 (아래 5번 참고) |

파일 크기 제한: 각 이미지 **10MB 이하**.

#### 옷 종류(`garment_type`) 값 목록

| 한국어 라벨 |
|---|
| 수영복 |
| 원피스 |
| 티셔츠 |
| 셔츠 |
| 블라우스 |
| 니트 |
| 자켓 |
| 코트 |
| 후드티 |
| 청바지 |
| 슬랙스 |
| 반바지 |
| 스커트 |
| 레깅스 |
| 정장 |
| 조끼 |
| 가디건 |
| 트레이닝복 |
| 한복 |
| 잠옷 |
| 아동복 |

목록에 없는 값을 보내면 옷 종류 미지정과 동일하게 처리된다 (에러 아님).

### Response — `200 OK`

`Content-Type: application/json`

```json
{
  "status": "success",
  "result_image_url": "/api/v1/vton/image?filename=ComfyUI_00001_.png&subfolder=&retain_images=delete",
  "garment_description": "swimsuit, high quality, photorealistic, detailed fabric",
  "negative_prompt": "monochrome, lowres, bad anatomy, worst quality, low quality",
  "segmentation_prompt": "clothes"
}
```

| 필드 | 설명 |
|---|---|
| `status` | 항상 `"success"` (실패 시 아래 에러 응답 참고) |
| `result_image_url` | **상대경로**. 반드시 `Base URL`을 붙여서 GET 요청해야 실제 이미지를 받을 수 있음 (2번 항목 참고) |
| `garment_description` | 실제로 생성에 사용된 옷 설명 프롬프트 (디버그용) |
| `negative_prompt` | 실제로 생성에 사용된 네거티브 프롬프트 (디버그용) |
| `segmentation_prompt` | 세그멘테이션(옷 영역 검출)에 사용된 프롬프트, 항상 `"clothes"` (디버그용) |

### Error Responses

| 상태 코드 | 상황 |
|---|---|
| `422 Unprocessable Entity` | `retain_images`가 `keep`/`delete`가 아닌 값 |
| `413 Request Entity Too Large` | 이미지가 10MB 초과 |
| `502 Bad Gateway` | ComfyUI 처리 중 오류 |
| `504 Gateway Timeout` | ComfyUI 응답 시간 초과 (기본 120초) |

에러 응답 바디는 FastAPI 기본 형식:
```json
{ "detail": "에러 메시지" }
```

---

## 2. GET /api/v1/vton/image

`generate` 응답의 `result_image_url`로 실제 이미지 바이트를 받아오는 엔드포인트.

### Request

Query String:

| 파라미터 | 필수 | 설명 |
|---|---|---|
| `filename` | O | `generate` 응답에서 받은 값 그대로 사용 |
| `subfolder` | X | `generate` 응답에서 받은 값 그대로 사용 (보통 빈 문자열) |
| `retain_images` | X (기본 `keep`) | `generate` 응답에서 받은 값 그대로 사용. `delete`인 경우 **이 요청으로 이미지를 서빙한 직후 서버에서 파일이 삭제됨** (한 번만 조회 가능) |

### Response

- `200 OK`, `Content-Type: image/*` — 이미지 바이너리
- `404 Not Found` — 파일을 찾을 수 없음 (이미 삭제된 경우 포함)

**주의**:
- `filename`/`subfolder`/`retain_images` 값을 직접 조합하지 말고, 반드시 `generate` 응답의 `result_image_url`을 그대로 (쿼리스트링 포함) 사용할 것.
- `retain_images=delete`로 받은 결과 이미지는 **한 번만 조회 가능**하다. 같은 URL로 재요청하면 `404`가 반환된다. 앱에서 이미지를 다운로드/캐시해서 화면에 표시하고, 서버 쪽 URL을 다시 호출하지 않도록 할 것.

---

## 3. GET /health

서버 상태 확인용.

### Response — `200 OK`
```json
{ "status": "ok" }
```

---

## 4. 사용 흐름 예시

```
1. POST /api/v1/vton/generate  (person_image, garment_image, garment_type 첨부)
   → { result_image_url: "/api/v1/vton/image?filename=xxx.png&subfolder=" }

2. GET {Base URL}/api/v1/vton/image?filename=xxx.png&subfolder=
   → 피팅 결과 이미지 바이너리
```

### curl 예시

```bash
curl -X POST "http://<host>:<port>/api/v1/vton/generate" \
  -F "person_image=@person.jpg" \
  -F "garment_image=@dress.jpg" \
  -F "garment_type=원피스" \
  -F "retain_images=delete"
```

---

## 5. 프라이버시 / 이미지 보관 정책

인물 사진, 피팅 결과 이미지는 모두 민감한 개인 이미지이므로 **기본 동작은 삭제**다.

- `retain_images=delete` (기본값):
    - 업로드된 인물/의상 원본 사진은 생성 완료 직후 서버(ComfyUI)에서 삭제됨
    - 피팅 결과 이미지는 `GET /api/v1/vton/image`로 **한 번 조회되는 즉시** 서버에서 삭제됨
- `retain_images=keep`: 위 삭제가 전혀 일어나지 않음. 사용자가 재사용 목적으로 명시적으로 선택한 경우에만 사용할 것.
- 웹 테스터(`/tester`)의 "이미지 보관" 버튼도 동일한 `retain_images` 파라미터를 그대로 사용하므로 API와 동작이 같다.

## 6. 그 외 제약사항

- 처리 시간은 ComfyUI 생성 속도에 따라 수 초~120초(타임아웃) 소요될 수 있음. 클라이언트는 충분한 타임아웃을 설정할 것.
- 동시 요청 처리 능력은 ComfyUI 백엔드 처리량에 의존한다 (별도 큐/동시성 제어는 미들웨어에 없음).
- rf-ai-server와 ComfyUI가 같은 서버(내부 테스트: 192.168.0.105)에 있어야 삭제 로직이 실제로 동작한다. 로컬 dev처럼 분리된 환경에서는 input 삭제가 조용히 무시된다.