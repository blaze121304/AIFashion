package com.example.aifashion.data.repository

import com.example.aifashion.data.api.ApiService
import com.example.aifashion.data.model.JobStatusResponse
import com.example.aifashion.data.model.JobSubmitResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response

/**
 * 피팅 관련 데이터 처리 Repository
 * ViewModel과 API 사이의 중간 레이어 역할
 */
class FittingRepository(private val apiService: ApiService) {

    /**
     * 피팅 작업 생성 요청 위임
     */
    suspend fun submitFittingJob(
        targetImage: MultipartBody.Part,
        profileId: RequestBody
    ): Response<JobSubmitResponse> {
        return apiService.submitFittingJob(targetImage, profileId)
    }

    /**
     * 피팅 작업 상태 조회 위임
     */
    suspend fun getJobStatus(jobId: String): Response<JobStatusResponse> {
        return apiService.getFittingJobStatus(jobId)
    }
}
