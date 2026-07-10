package com.example.aifashion.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aifashion.data.AppConfig
import com.example.aifashion.data.api.RetrofitClient
import com.example.aifashion.data.model.BodyPhotoResponse
import com.example.aifashion.data.repository.BodyPhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/** 몸사진 카드 리스트 최대 개수 */
const val MAX_BODY_PHOTOS = 5

data class BodyPhotoUiState(
    val photos: List<BodyPhotoResponse> = emptyList(),
    val selectedPhotoId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 몸사진 등록/조회/삭제/선택을 담당하는 ViewModel
 * 선택된 photo_id는 SharedPreferences에 저장되어 앱 재실행 후에도 유지됨
 */
class BodyPhotoViewModel(
    application: Application,
    private val repository: BodyPhotoRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(BodyPhotoUiState())
    val uiState: StateFlow<BodyPhotoUiState> = _uiState.asStateFlow()

    init {
        loadPhotos()
    }

    /** 서버에서 몸사진 목록을 불러오고, 로컬에 저장된 선택 상태를 복원 */
    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = repository.getPhotos(AppConfig.DEFAULT_USER_ID)
                if (response.isSuccessful) {
                    val photos = response.body()?.photos.orEmpty()
                    val savedSelectedId = prefs.getString(KEY_SELECTED_PHOTO_ID, null)
                    val selectedId = savedSelectedId
                        ?.takeIf { id -> photos.any { it.photo_id == id } }
                        ?: photos.firstOrNull()?.photo_id
                    _uiState.value = _uiState.value.copy(
                        photos = photos,
                        selectedPhotoId = selectedId,
                        isLoading = false
                    )
                    persistSelectedPhotoId(selectedId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "몸사진 목록을 불러오지 못했습니다 (HTTP ${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "몸사진 목록 조회 중 오류가 발생했습니다"
                )
            }
        }
    }

    /** 사진첩에서 고른 이미지를 서버에 등록 (최대 5장 제한) */
    fun uploadPhoto(imageUri: Uri, contentResolver: ContentResolver) {
        val current = _uiState.value
        if (current.photos.size >= MAX_BODY_PHOTOS) {
            _uiState.value = current.copy(
                errorMessage = "최대 ${MAX_BODY_PHOTOS}장까지 등록 가능합니다. 삭제 후 추가해주세요"
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val bytes = contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("이미지 파일을 열 수 없습니다")

                val photoPart = MultipartBody.Part.createFormData(
                    name = "photo",
                    filename = "body_${System.currentTimeMillis()}.jpg",
                    body = bytes.toRequestBody("image/jpeg".toMediaType())
                )
                val userIdBody = AppConfig.DEFAULT_USER_ID.toRequestBody("text/plain".toMediaType())

                val response = repository.registerPhoto(photoPart, userIdBody)
                val newPhoto = response.body()
                if (response.isSuccessful && newPhoto != null) {
                    val updatedPhotos = _uiState.value.photos + newPhoto
                    _uiState.value = _uiState.value.copy(
                        photos = updatedPhotos,
                        selectedPhotoId = newPhoto.photo_id,
                        isLoading = false
                    )
                    persistSelectedPhotoId(newPhoto.photo_id)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "몸사진 등록 실패 (HTTP ${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "몸사진 등록 중 오류가 발생했습니다"
                )
            }
        }
    }

    /** 몸사진 삭제. 선택된 사진이 삭제되면 다음 사진으로 선택 갱신 */
    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val response = repository.deletePhoto(photoId)
                if (response.isSuccessful) {
                    val updatedPhotos = _uiState.value.photos.filterNot { it.photo_id == photoId }
                    val updatedSelectedId = if (_uiState.value.selectedPhotoId == photoId) {
                        updatedPhotos.firstOrNull()?.photo_id
                    } else {
                        _uiState.value.selectedPhotoId
                    }
                    _uiState.value = _uiState.value.copy(
                        photos = updatedPhotos,
                        selectedPhotoId = updatedSelectedId,
                        isLoading = false
                    )
                    persistSelectedPhotoId(updatedSelectedId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "몸사진 삭제 실패 (HTTP ${response.code()})"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "몸사진 삭제 중 오류가 발생했습니다"
                )
            }
        }
    }

    /** 피팅에 사용할 몸사진 선택 (카드 탭) */
    fun selectPhoto(photoId: String) {
        _uiState.value = _uiState.value.copy(selectedPhotoId = photoId)
        persistSelectedPhotoId(photoId)
    }

    /** 에러 메시지 배너 닫기 */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    private fun persistSelectedPhotoId(photoId: String?) {
        prefs.edit().putString(KEY_SELECTED_PHOTO_ID, photoId).apply()
    }

    companion object {
        private const val PREFS_NAME = "ai_fashion_prefs"
        private const val KEY_SELECTED_PHOTO_ID = "selected_body_photo_id"
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = BodyPhotoRepository(RetrofitClient.apiService)
            return BodyPhotoViewModel(application, repository) as T
        }
    }
}
