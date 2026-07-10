package com.example.aifashion.data.model

/**
 * POST/GET /api/v1/profile/photos 응답 모델
 * 등록된 몸사진 한 장을 나타냄
 */
data class BodyPhotoResponse(
    val photo_id: String,
    val image_url: String,
    val created_at: String
)

/**
 * GET /api/v1/profile/photos 목록 응답 모델
 */
data class BodyPhotoListResponse(
    val photos: List<BodyPhotoResponse>
)
