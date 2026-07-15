package com.example.aifashion.data.api

import com.example.aifashion.data.model.VtonGenerateResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit API 인터페이스
 * rf-ai-server(VTON 미들웨어)와 통신하는 엔드포인트 정의 (.claude/API.md 참고)
 */
interface ApiService {

    /**
     * 가상 피팅 생성 요청 (동기 방식 - job/폴링 없이 한 번의 요청으로 완료됨)
     * POST /api/v1/vton/generate
     * @param personImage 몸사진 (Multipart)
     * @param garmentImage 크롭된 의류 이미지 (Multipart)
     * @param garmentType 옷 종류 한국어 라벨 (선택, 미지정 시 종류 특정 없이 처리)
     * @return HTTP 200 + { status, result_image_url(상대경로), ... }
     */
    @Multipart
    @POST("api/v1/vton/generate")
    suspend fun generateFitting(
        @Part personImage: MultipartBody.Part,
        @Part garmentImage: MultipartBody.Part,
        @Part("garment_type") garmentType: RequestBody?
    ): Response<VtonGenerateResponse>
}
