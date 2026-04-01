package com.example.aifashion.data.model

/**
 * POST /api/v1/fitting/jobs 응답 모델
 * AI 피팅 작업 생성 성공 시 job_id를 반환받음
 */
data class JobSubmitResponse(
    val job_id: String
)

/**
 * GET /api/v1/fitting/jobs/{job_id} 응답 모델
 * 폴링으로 작업 상태를 확인할 때 사용
 */
data class JobStatusResponse(
    val job_id: String,
    val status: String,          // "processing" | "completed" | "failed"
    val result_image_url: String? // status가 "completed"일 때만 존재
)
