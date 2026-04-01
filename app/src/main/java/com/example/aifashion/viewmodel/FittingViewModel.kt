package com.example.aifashion.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aifashion.data.api.RetrofitClient
import com.example.aifashion.data.repository.FittingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 피팅 UI 상태를 나타내는 Sealed Class
 * StateFlow로 관리되어 UI에서 collect하여 상태별 화면을 표시
 */
sealed class FittingUiState {
    /** 초기 상태 또는 결과 확인 후 상태 */
    object Idle : FittingUiState()

    /** API 요청 중 또는 폴링 중 */
    data class Loading(val message: String = "AI 피팅을 처리 중입니다...") : FittingUiState()

    /** 피팅 완료 - result_image_url을 포함 */
    data class Success(val resultImageUrl: String) : FittingUiState()

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
     * 피팅 작업 제출 메인 함수
     * 이미지 URI → Multipart 변환 → POST 요청 → 폴링 시작
     *
     * @param imageUri 크롭 완료된 이미지의 Uri
     * @param contentResolver 이미지 바이트를 읽기 위한 ContentResolver
     */
    fun submitFittingJob(imageUri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.value = FittingUiState.Loading("서버에 이미지를 전송 중...")

            try {
                // 1단계: Uri에서 이미지 바이트 배열 읽기
                val imageBytes = contentResolver.openInputStream(imageUri)?.use {
                    it.readBytes()
                } ?: throw IllegalStateException("이미지 파일을 열 수 없습니다")

                // 2단계: Multipart 파트 생성
                val requestBody = imageBytes.toRequestBody("image/jpeg".toMediaType())
                val imagePart = MultipartBody.Part.createFormData(
                    name = "target_image",
                    filename = "fitting_image.jpg",
                    body = requestBody
                )
                val profileIdBody = "default_user_1"
                    .toRequestBody("text/plain".toMediaType())

                // 3단계: POST /api/v1/fitting/jobs 요청 (HTTP 202 기대)
                val response = repository.submitFittingJob(imagePart, profileIdBody)

                if (response.isSuccessful && response.code() == 202) {
                    val jobId = response.body()?.job_id
                        ?: throw IllegalStateException("서버에서 job_id를 반환하지 않았습니다")

                    // 4단계: 폴링 루프 시작
                    startPolling(jobId)
                } else {
                    _uiState.value = FittingUiState.Error(
                        "작업 생성 실패 (HTTP ${response.code()})"
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
     * 3초 간격 폴링 루프
     * status가 "completed" 또는 "failed"가 될 때까지 반복 조회
     *
     * @param jobId 추적할 작업 ID
     */
    private suspend fun startPolling(jobId: String) {
        _uiState.value = FittingUiState.Loading("AI가 의상을 분석 중입니다... (job: $jobId)")

        // 최대 폴링 횟수: 무한 루프 방지 (3초 × 60회 = 최대 3분)
        var pollCount = 0
        val maxPollCount = 60

        while (pollCount < maxPollCount) {
            // 3초 대기 후 상태 조회
            delay(3000L)
            pollCount++

            try {
                val response = repository.getJobStatus(jobId)

                if (!response.isSuccessful) {
                    _uiState.value = FittingUiState.Error(
                        "상태 조회 실패 (HTTP ${response.code()})"
                    )
                    return
                }

                val jobStatus = response.body() ?: continue

                when (jobStatus.status) {
                    "completed" -> {
                        // 성공: result_image_url이 반드시 존재해야 함
                        val resultUrl = jobStatus.result_image_url
                            ?: run {
                                _uiState.value = FittingUiState.Error("결과 이미지 URL이 없습니다")
                                return
                            }
                        _uiState.value = FittingUiState.Success(resultUrl)
                        return // 폴링 종료
                    }

                    "failed" -> {
                        _uiState.value = FittingUiState.Error("AI 처리에 실패했습니다")
                        return // 폴링 종료
                    }

                    "processing" -> {
                        // 아직 처리 중 - 계속 폴링
                        _uiState.value = FittingUiState.Loading(
                            "처리 중... ($pollCount/${maxPollCount}회 확인)"
                        )
                    }
                }

            } catch (e: Exception) {
                _uiState.value = FittingUiState.Error(
                    "폴링 오류: ${e.message}"
                )
                return
            }
        }

        // 타임아웃
        _uiState.value = FittingUiState.Error("처리 시간이 초과되었습니다 (3분)")
    }

    /**
     * 상태를 초기화 (재시도 버튼 클릭 시)
     */
    fun reset() {
        _uiState.value = FittingUiState.Idle
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
