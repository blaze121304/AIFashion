package com.example.aifashion.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aifashion.data.model.LocalBodyPhoto
import com.example.aifashion.data.repository.BodyPhotoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 몸사진 카드 리스트 최대 개수 */
const val MAX_BODY_PHOTOS = 5

data class BodyPhotoUiState(
    val photos: List<LocalBodyPhoto> = emptyList(),
    val selectedPhotoId: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 몸사진 등록/조회/삭제/선택을 담당하는 ViewModel.
 * 서버에는 몸사진을 등록하는 API가 없어(.claude/API.md 참고) 기기 로컬 저장소에만 보관하고,
 * 피팅 요청 시 매번 파일로 첨부해서 보낸다.
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

    /** 로컬 저장소에서 몸사진 목록을 불러오고, 저장된 선택 상태를 복원 */
    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val photos = withContext(Dispatchers.IO) { repository.getPhotos() }
            val savedSelectedId = prefs.getString(KEY_SELECTED_PHOTO_ID, null)
            val selectedId = savedSelectedId
                ?.takeIf { id -> photos.any { it.photoId == id } }
                ?: photos.firstOrNull()?.photoId
            _uiState.value = _uiState.value.copy(
                photos = photos,
                selectedPhotoId = selectedId,
                isLoading = false
            )
            persistSelectedPhotoId(selectedId)
        }
    }

    /** 사진첩에서 고른 이미지를 로컬 저장소에 등록 (최대 5장 제한) */
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
                val newPhoto = withContext(Dispatchers.IO) {
                    repository.addPhoto(imageUri, contentResolver)
                }
                val updatedPhotos = _uiState.value.photos + newPhoto
                _uiState.value = _uiState.value.copy(
                    photos = updatedPhotos,
                    selectedPhotoId = newPhoto.photoId,
                    isLoading = false
                )
                persistSelectedPhotoId(newPhoto.photoId)
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
            withContext(Dispatchers.IO) { repository.deletePhoto(photoId) }
            val updatedPhotos = _uiState.value.photos.filterNot { it.photoId == photoId }
            val updatedSelectedId = if (_uiState.value.selectedPhotoId == photoId) {
                updatedPhotos.firstOrNull()?.photoId
            } else {
                _uiState.value.selectedPhotoId
            }
            _uiState.value = _uiState.value.copy(
                photos = updatedPhotos,
                selectedPhotoId = updatedSelectedId
            )
            persistSelectedPhotoId(updatedSelectedId)
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
            val repository = BodyPhotoRepository(application)
            return BodyPhotoViewModel(application, repository) as T
        }
    }
}
