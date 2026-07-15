package com.example.aifashion.viewmodel

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aifashion.data.api.RetrofitClient
import com.example.aifashion.data.repository.FittingRepository
import com.example.aifashion.util.GallerySaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URL

/**
 * 피팅 UI 상태를 나타내는 Sealed Class
 * StateFlow로 관리되어 UI에서 collect하여 상태별 화면을 표시
 */
sealed class FittingUiState {
    /** 초기 상태 또는 결과 확인 후 상태 */
    object Idle : FittingUiState()

    /** API 요청 중 (서버가 동기 방식으로 응답하므로 최대 120초까지 소요될 수 있음) */
    data class Loading(val message: String = "AI 피팅을 처리 중입니다... (최대 120초 소요)") : FittingUiState()

    /** 피팅 완료 - result_image_url을 포함. savedUri는 결과 이미지를 갤러리에 저장한 뒤 캐시됨 */
    data class Success(val resultImageUrl: String, val savedUri: Uri? = null) : FittingUiState()

    /** 오류 발생 */
    data class Error(val message: String) : FittingUiState()
}

/**
 * 피팅 화면의 비즈니스 로직을 담당하는 ViewModel
 * Retrofit + Coroutines + StateFlow 조합으로 비동기 상태 관리
 */
class FittingViewModel(
    private val repository: FittingRepository
) : ViewModel() {

    // UI 상태를 외부에서는 읽기 전용(StateFlow), 내부에서는 쓰기 가능(MutableStateFlow)
    private val _uiState = MutableStateFlow<FittingUiState>(FittingUiState.Idle)
    val uiState: StateFlow<FittingUiState> = _uiState.asStateFlow()

    /**
     * 피팅 요청. 서버(rf-ai-server)가 동기 방식으로 응답하므로(.claude/API.md)
     * job 생성/폴링 없이 한 번의 요청으로 완료된다.
     *
     * @param garmentImageUri 크롭 완료된 의류 이미지의 Uri
     * @param bodyPhotoFilePath 로컬에 저장된 몸사진 파일 경로
     * @param garmentType 선택한 옷 종류 한국어 라벨 (미선택 시 null)
     * @param contentResolver 의류 이미지 바이트를 읽기 위한 ContentResolver
     */
    fun submitFittingJob(
        garmentImageUri: Uri,
        bodyPhotoFilePath: String,
        garmentType: String?,
        contentResolver: ContentResolver
    ) {
        viewModelScope.launch {
            _uiState.value = FittingUiState.Loading()

            try {
                val garmentBytes = contentResolver.openInputStream(garmentImageUri)?.use {
                    it.readBytes()
                } ?: throw IllegalStateException("의류 이미지를 열 수 없습니다")

                val personFile = File(bodyPhotoFilePath)
                if (!personFile.exists()) {
                    throw IllegalStateException("몸사진 파일을 찾을 수 없습니다")
                }
                val personBytes = withContext(Dispatchers.IO) { personFile.readBytes() }

                val garmentPart = MultipartBody.Part.createFormData(
                    name = "garment_image",
                    filename = "garment.jpg",
                    body = garmentBytes.toRequestBody("image/jpeg".toMediaType())
                )
                val personPart = MultipartBody.Part.createFormData(
                    name = "person_image",
                    filename = "person.jpg",
                    body = personBytes.toRequestBody("image/jpeg".toMediaType())
                )
                val garmentTypeBody = garmentType?.toRequestBody("text/plain".toMediaType())

                val response = repository.generateFitting(personPart, garmentPart, garmentTypeBody)

                if (response.isSuccessful) {
                    val body = response.body()
                        ?: throw IllegalStateException("서버 응답이 비어 있습니다")
                    // result_image_url은 상대경로이므로 Base URL을 붙여야 실제 이미지에 접근 가능
                    val absoluteUrl = RetrofitClient.BASE_URL.trimEnd('/') + body.result_image_url
                    _uiState.value = FittingUiState.Success(absoluteUrl)
                } else {
                    _uiState.value = FittingUiState.Error(
                        "피팅 요청 실패 (HTTP ${response.code()})"
                    )
                }

            } catch (e: Exception) {
                _uiState.value = FittingUiState.Error(
                    e.message ?: "알 수 없는 오류가 발생했습니다"
                )
            }
        }
    }

    /**
     * 상태를 초기화 (재시도 버튼 클릭 시)
     */
    fun reset() {
        _uiState.value = FittingUiState.Idle
    }

    /**
     * 결과 이미지를 기기 갤러리에 저장하고 저장된 Uri를 반환
     * 이미 저장된 적이 있으면(savedUri 존재) 재다운로드 없이 캐시된 값을 그대로 반환
     * 저장/공유 버튼이 공통으로 사용
     */
    suspend fun ensureSavedImageUri(context: Context): Uri? {
        val current = _uiState.value
        if (current !is FittingUiState.Success) return null
        current.savedUri?.let { return it }

        return try {
            val bytes = withContext(Dispatchers.IO) {
                URL(current.resultImageUrl).openStream().use { it.readBytes() }
            }
            val uri = withContext(Dispatchers.IO) {
                GallerySaver.saveImage(context, bytes, "ai_fitting_${System.currentTimeMillis()}.jpg")
            }
            if (uri != null && _uiState.value == current) {
                _uiState.value = current.copy(savedUri = uri)
            }
            uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * ViewModelFactory: Repository 의존성 주입
     */
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = FittingRepository(RetrofitClient.apiService)
            return FittingViewModel(repository) as T
        }
    }
}
