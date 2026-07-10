package com.example.aifashion.ui.bodyphoto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.aifashion.data.model.BodyPhotoResponse
import com.example.aifashion.viewmodel.BodyPhotoUiState
import com.example.aifashion.viewmodel.MAX_BODY_PHOTOS

/**
 * 화면 하단 1/5 영역: 내 몸 사진 카드 리스트 (최대 5장, 가로 스크롤)
 */
@Composable
fun BodyPhotoSection(
    state: BodyPhotoUiState,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAddClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "내 몸 사진 (${state.photos.size}/$MAX_BODY_PHOTOS)",
            style = MaterialTheme.typography.labelLarge
        )

        state.errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            items(state.photos, key = { it.photo_id }) { photo ->
                BodyPhotoCard(
                    photo = photo,
                    isSelected = photo.photo_id == state.selectedPhotoId,
                    onSelect = { onSelect(photo.photo_id) },
                    onDelete = { onDelete(photo.photo_id) }
                )
            }

            if (state.photos.size < MAX_BODY_PHOTOS) {
                item { AddPhotoCard(onClick = onAddClick) }
            }
        }
    }
}

@Composable
private fun BodyPhotoCard(
    photo: BodyPhotoResponse,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(2.dp)
    ) {
        AsyncImage(
            model = photo.image_url,
            contentDescription = "내 몸 사진",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSelect)
        )

        Text(
            text = "✕",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50))
                .clickable(onClick = onDelete)
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun AddPhotoCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "+", style = MaterialTheme.typography.headlineSmall)
    }
}
