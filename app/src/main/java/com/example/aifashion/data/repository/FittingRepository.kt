package com.example.aifashion.data.repository

import com.example.aifashion.data.api.ApiService
import com.example.aifashion.data.model.VtonGenerateResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response

/**
 * 피팅 관련 데이터 처리 Repository
 * ViewModel과 API 사이의 중간 레이어 역할
 */
class FittingRepository(private val apiService: ApiService) {

    /**
     * 가상 피팅 생성 요청 위임 (동기 방식)
     */
    suspend fun generateFitting(
        personImage: MultipartBody.Part,
        garmentImage: MultipartBody.Part,
        garmentType: RequestBody?
    ): Response<VtonGenerateResponse> {
        return apiService.generateFitting(personImage, garmentImage, garmentType)
    }
}
