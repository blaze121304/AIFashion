package com.example.aifashion.ui.crop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.aifashion.MainActivity
import com.yalantis.ucrop.UCrop
import java.io.File

/**
 * 이미지 크롭 전용 액티비티
 *
 * 역할:
 * 1. CaptureDetectionService로부터 스크린샷 URI 수신
 * 2. uCrop 라이브러리로 3:4 고정 비율 크롭 실행
 * 3. 크롭 완료 후 크롭된 이미지 URI를 MainActivity로 전달
 *
 * 비율 고정 이유: IDM-VTON 모델의 VRAM 초과 방지 및 입력 정규화
 */
class CropActivity : AppCompatActivity() {

    companion object {
        /** CaptureDetectionService에서 전달하는 원본 이미지 URI 키 */
        const val EXTRA_IMAGE_URI = "extra_image_uri"

        /** MainActivity로 크롭 결과를 전달하는 키 */
        const val EXTRA_CROPPED_URI = "extra_cropped_uri"
    }

    /**
     * uCrop Activity 결과 처리를 위한 ActivityResultLauncher
     * uCrop은 Activity 기반으로 동작하므로 이 방식 사용
     */
    private val uCropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        when (result.resultCode) {
            RESULT_OK -> {
                // 크롭 성공: 결과 URI를 추출하여 MainActivity로 전달
                val croppedUri = result.data?.let { UCrop.getOutput(it) }
                if (croppedUri != null) {
                    navigateToMainActivity(croppedUri)
                } else {
                    Toast.makeText(this, "크롭 결과를 가져오지 못했습니다", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            UCrop.RESULT_ERROR -> {
                // uCrop 내부 오류 처리
                val error = result.data?.let { UCrop.getError(it) }
                Toast.makeText(this, "크롭 오류: ${error?.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
            else -> {
                // 사용자가 취소
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Intent에서 원본 이미지 URI 추출
        val imageUriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        if (imageUriString == null) {
            Toast.makeText(this, "이미지 정보가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val sourceUri = Uri.parse(imageUriString)
        launchUCrop(sourceUri)
    }

    /**
     * uCrop 크롭 화면 실행
     * 3:4 비율 고정, 자유 비율 비활성화
     *
     * @param sourceUri 크롭할 원본 이미지 URI
     */
    private fun launchUCrop(sourceUri: Uri) {
        // 크롭 결과물을 저장할 임시 파일 생성
        val destinationFile = File(cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
        val destinationUri = Uri.fromFile(destinationFile)

        // uCrop 옵션 설정
        val options = UCrop.Options().apply {
            // 자유 비율 크롭 비활성화 (반드시 3:4 고정)
            setFreeStyleCropEnabled(false)
            // UI 커스터마이징
            setToolbarTitle("의류 영역 선택 (3:4 비율)")
            setToolbarColor(0xFF212121.toInt())
            setStatusBarColor(0xFF212121.toInt())
            setActiveControlsWidgetColor(0xFFFF6B35.toInt())
        }

        // uCrop 실행: 3:4 비율 강제 고정
        val uCropIntent = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(3f, 4f)   // 가로:세로 = 3:4 고정
            .withMaxResultSize(1080, 1440) // 최대 해상도 제한 (VRAM 보호)
            .withOptions(options)
            .getIntent(this)

        uCropLauncher.launch(uCropIntent)
    }

    /**
     * 크롭 완료 후 MainActivity로 이동
     * singleTop 모드이므로 onNewIntent로 전달됨
     *
     * @param croppedUri 크롭 완료된 이미지 URI
     */
    private fun navigateToMainActivity(croppedUri: Uri) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_CROPPED_URI, croppedUri.toString())
            // singleTop + CLEAR_TOP으로 기존 MainActivity 인스턴스 재사용
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }
}
