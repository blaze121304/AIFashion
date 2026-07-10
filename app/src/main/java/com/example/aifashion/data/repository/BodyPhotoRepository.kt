package com.example.aifashion.data.repository

import com.example.aifashion.data.api.ApiService
import com.example.aifashion.data.model.BodyPhotoListResponse
import com.example.aifashion.data.model.BodyPhotoResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response

/**
 * 몸사진(프로필 사진) 등록/조회/삭제를 담당하는 Repository
 */
class BodyPhotoRepository(private val apiService: ApiService) {

    suspend fun registerPhoto(
        photo: MultipartBody.Part,
        userId: RequestBody
    ): Response<BodyPhotoResponse> {
        return apiService.registerBodyPhoto(photo, userId)
    }

    suspend fun getPhotos(userId: String): Response<BodyPhotoListResponse> {
        return apiService.getBodyPhotos(userId)
    }

    suspend fun deletePhoto(photoId: String): Response<Unit> {
        return apiService.deleteBodyPhoto(photoId)
    }
}
