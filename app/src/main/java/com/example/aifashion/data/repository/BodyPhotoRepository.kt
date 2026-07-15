package com.example.aifashion.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.aifashion.data.local.BodyPhotoStore
import com.example.aifashion.data.model.LocalBodyPhoto

/**
 * 몸사진 로컬 저장/조회/삭제를 담당.
 * 서버에는 몸사진을 등록하는 API가 없어(.claude/API.md 참고) 기기 내부 저장소에만 보관한다.
 */
class BodyPhotoRepository(private val context: Context) {

    fun getPhotos(): List<LocalBodyPhoto> = BodyPhotoStore.list(context)

    fun addPhoto(sourceUri: Uri, contentResolver: ContentResolver): LocalBodyPhoto =
        BodyPhotoStore.add(context, sourceUri, contentResolver)

    fun deletePhoto(photoId: String) = BodyPhotoStore.delete(context, photoId)
}
