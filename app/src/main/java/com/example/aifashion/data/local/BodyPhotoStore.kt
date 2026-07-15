package com.example.aifashion.data.local

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.aifashion.data.model.LocalBodyPhoto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * 몸사진 파일과 메타데이터를 기기 내부 저장소(filesDir)에 저장/조회/삭제.
 * 서버에 몸사진을 저장하는 API가 없어(.claude/API.md 참고) 클라이언트가 직접 보관하고,
 * 피팅 요청 시마다 person_image로 매번 실제 파일을 첨부해서 보낸다.
 */
object BodyPhotoStore {

    private const val PREFS_NAME = "ai_fashion_prefs"
    private const val KEY_PHOTOS_JSON = "local_body_photos"
    private const val DIR_NAME = "body_photos"

    fun list(context: Context): List<LocalBodyPhoto> {
        val json = prefs(context).getString(KEY_PHOTOS_JSON, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            LocalBodyPhoto(
                photoId = obj.getString("photoId"),
                filePath = obj.getString("filePath"),
                createdAt = obj.getLong("createdAt")
            )
        }
    }

    fun add(context: Context, sourceUri: Uri, contentResolver: ContentResolver): LocalBodyPhoto {
        val dir = File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }
        val photoId = UUID.randomUUID().toString()
        val file = File(dir, "$photoId.jpg")
        contentResolver.openInputStream(sourceUri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("이미지 파일을 열 수 없습니다")

        val photo = LocalBodyPhoto(photoId, file.absolutePath, System.currentTimeMillis())
        save(context, list(context) + photo)
        return photo
    }

    fun delete(context: Context, photoId: String) {
        val photos = list(context)
        photos.find { it.photoId == photoId }?.let { File(it.filePath).delete() }
        save(context, photos.filterNot { it.photoId == photoId })
    }

    private fun save(context: Context, photos: List<LocalBodyPhoto>) {
        val array = JSONArray()
        photos.forEach { photo ->
            array.put(
                JSONObject().apply {
                    put("photoId", photo.photoId)
                    put("filePath", photo.filePath)
                    put("createdAt", photo.createdAt)
                }
            )
        }
        prefs(context).edit().putString(KEY_PHOTOS_JSON, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
