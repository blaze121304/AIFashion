package com.example.aifashion.data.model

/**
 * 로컬(기기 내부 저장소)에 보관된 몸사진 한 장의 메타데이터.
 * 서버에는 몸사진을 등록/보관하는 API가 없어(.claude/API.md 참고) 클라이언트가 직접 관리하고,
 * 피팅 요청 시마다 이 파일을 person_image로 첨부해서 보낸다.
 */
data class LocalBodyPhoto(
    val photoId: String,
    val filePath: String,
    val createdAt: Long
)
