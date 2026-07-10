package com.example.aifashion.data.api

import com.example.aifashion.data.model.BodyPhotoListResponse
import com.example.aifashion.data.model.BodyPhotoResponse
import com.example.aifashion.data.model.JobStatusResponse
import com.example.aifashion.data.model.JobSubmitResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API 인터페이스
 * 백엔드 FastAPI 서버와 통신하는 엔드포인트 정의
 */
interface ApiService {

    /**
     * AI 피팅 작업 생성 요청
     * POST /api/v1/fitting/jobs
     * @param targetImage 크롭된 의류 이미지 (Multipart)
     * @param bodyPhotoId 등록된 몸사진의 photo_id (POST /api/v1/profile/photos로 발급받음)
     * @return HTTP 202 Accepted + { job_id }
     */
    @Multipart
    @POST("api/v1/fitting/jobs")
    suspend fun submitFittingJob(
        @Part targetImage: MultipartBody.Part,
        @Part("body_photo_id") bodyPhotoId: RequestBody
    ): Response<JobSubmitResponse>

    /**
     * AI 피팅 작업 상태 조회 (폴링용)
     * GET /api/v1/fitting/jobs/{job_id}
     * @param jobId 작업 ID
     * @return { status: "processing"|"completed"|"failed", result_image_url? }
     */
    @GET("api/v1/fitting/jobs/{job_id}")
    suspend fun getFittingJobStatus(
        @Path("job_id") jobId: String
    ): Response<JobStatusResponse>

    /**
     * 몸사진 등록
     * POST /api/v1/profile/photos
     * @param photo 몸사진 이미지 (Multipart)
     * @param userId 사용자 ID
     * @return HTTP 201 Created + { photo_id, image_url, created_at }
     */
    @Multipart
    @POST("api/v1/profile/photos")
    suspend fun registerBodyPhoto(
        @Part photo: MultipartBody.Part,
        @Part("user_id") userId: RequestBody
    ): Response<BodyPhotoResponse>

    /**
     * 등록된 몸사진 목록 조회
     * GET /api/v1/profile/photos
     */
    @GET("api/v1/profile/photos")
    suspend fun getBodyPhotos(
        @Query("user_id") userId: String
    ): Response<BodyPhotoListResponse>

    /**
     * 몸사진 삭제
     * DELETE /api/v1/profile/photos/{photo_id}
     */
    @DELETE("api/v1/profile/photos/{photo_id}")
    suspend fun deleteBodyPhoto(
        @Path("photo_id") photoId: String
    ): Response<Unit>
}
