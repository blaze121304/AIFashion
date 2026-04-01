package com.example.aifashion.data.api

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
     * @param profileId 사용자 프로필 ID
     * @return HTTP 202 Accepted + { job_id }
     */
    @Multipart
    @POST("api/v1/fitting/jobs")
    suspend fun submitFittingJob(
        @Part targetImage: MultipartBody.Part,
        @Part("profile_id") profileId: RequestBody
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
}
