package com.example.aifashion.data.model

/**
 * POST /api/v1/vton/generate 응답 모델 (.claude/API.md 참고)
 * 서버가 동기 방식으로 한 번에 결과를 반환한다 (job 생성/폴링 없음)
 */
data class VtonGenerateResponse(
    val status: String,
    val result_image_url: String, // 상대경로. Base URL을 붙여서 GET 요청해야 실제 이미지를 받을 수 있음
    val garment_description: String? = null,
    val negative_prompt: String? = null,
    val segmentation_prompt: String? = null
)
