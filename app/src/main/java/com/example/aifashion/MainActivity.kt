package com.example.aifashion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.aifashion.service.CaptureDetectionService
import com.example.aifashion.ui.crop.CropActivity
import com.example.aifashion.ui.theme.AIFashionTheme
import com.example.aifashion.viewmodel.FittingUiState
import com.example.aifashion.viewmodel.FittingViewModel

/**
 * 메인 액티비티
 *
 * 역할:
 * 1. 필요한 권한 요청 (오버레이, 미디어, 알림)
 * 2. CaptureDetectionService 실행 관리
 * 3. CropActivity에서 전달된 크롭 이미지 수신 (onNewIntent)
 * 4. FittingViewModel 상태 관찰 및 Compose UI 렌더링
 */
class MainActivity : ComponentActivity() {

    private val fittingViewModel: FittingViewModel by viewModels {
        FittingViewModel.Factory()
    }

    // 크롭된 이미지 URI를 Compose 상태로 관리
    private var croppedImageUri by mutableStateOf<Uri?>(null)

    // ──────────────────────────────────────────────
    // 권한 요청 Launchers
    // ──────────────────────────────────────────────

    /** READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE 권한 요청 */
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkAndRequestOverlayPermission()
        } else {
            Toast.makeText(this, "미디어 권한이 필요합니다", Toast.LENGTH_LONG).show()
        }
    }

    /** POST_NOTIFICATIONS 권한 요청 (API 33+) */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 알림 권한은 선택적 - 거부해도 계속 진행
        checkAndRequestMediaPermission()
    }

    /** SYSTEM_ALERT_WINDOW 권한 요청 (설정 화면으로 이동) */
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            startCaptureService()
        } else {
            Toast.makeText(this, "오버레이 권한이 없어 플로팅 버튼이 표시되지 않습니다", Toast.LENGTH_LONG).show()
            startCaptureService() // 권한 없이도 서비스 시작 (감지만)
        }
    }

    // ──────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // CropActivity에서 전달된 초기 URI 처리
        handleIncomingIntent(intent)

        setContent {
            AIFashionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FittingScreen(
                        croppedImageUri = croppedImageUri,
                        viewModel = fittingViewModel,
                        onStartFitting = {
                            // 피팅 시작: ViewModel에 크롭 이미지 URI 전달
                            croppedImageUri?.let { uri ->
                                fittingViewModel.submitFittingJob(uri, contentResolver)
                            }
                        },
                        onReset = {
                            fittingViewModel.reset()
                            croppedImageUri = null
                        }
                    )
                }
            }
        }

        // 순차적 권한 요청 시작
        requestPermissionsSequentially()
    }

    /**
     * singleTop 모드에서 새 Intent 수신
     * CropActivity가 FLAG_ACTIVITY_SINGLE_TOP으로 전달하는 크롭 결과 처리
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Intent에서 크롭된 이미지 URI 추출
     */
    private fun handleIncomingIntent(intent: Intent) {
        val croppedUriString = intent.getStringExtra(CropActivity.EXTRA_CROPPED_URI)
        if (croppedUriString != null) {
            croppedImageUri = Uri.parse(croppedUriString)
            // 새 이미지가 들어오면 ViewModel 상태 초기화
            fittingViewModel.reset()
        }
    }

    /**
     * 권한 요청 순서:
     * POST_NOTIFICATIONS (선택) → READ_MEDIA_IMAGES → SYSTEM_ALERT_WINDOW → 서비스 시작
     */
    private fun requestPermissionsSequentially() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        checkAndRequestMediaPermission()
    }

    private fun checkAndRequestMediaPermission() {
        val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, mediaPermission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            mediaPermissionLauncher.launch(mediaPermission)
        } else {
            checkAndRequestOverlayPermission()
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "플로팅 버튼을 위해 오버레이 권한이 필요합니다", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            startCaptureService()
        }
    }

    /**
     * 스크린샷 감지 포그라운드 서비스 시작
     */
    private fun startCaptureService() {
        val serviceIntent = Intent(this, CaptureDetectionService::class.java)
        startForegroundService(serviceIntent)
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Compose UI
// ──────────────────────────────────────────────────────────────────────────────

/**
 * 메인 피팅 화면
 * FittingUiState에 따라 동적으로 UI를 렌더링
 */
@Composable
fun FittingScreen(
    croppedImageUri: Uri?,
    viewModel: FittingViewModel,
    onStartFitting: () -> Unit,
    onReset: () -> Unit
) {
    // StateFlow를 Compose 상태로 수집 (생명주기 인식)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "AI 가상 피팅",
            style = MaterialTheme.typography.headlineMedium
        )

        when (val state = uiState) {

            // ── 대기 상태: 크롭된 이미지 표시 또는 안내 메시지 ──
            is FittingUiState.Idle -> {
                if (croppedImageUri != null) {
                    // 크롭된 이미지 미리보기
                    Text("선택된 의류 이미지", style = MaterialTheme.typography.titleMedium)

                    AsyncImage(
                        model = croppedImageUri,
                        contentDescription = "크롭된 의류 이미지",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f),
                        contentScale = ContentScale.Crop
                    )

                    Button(
                        onClick = onStartFitting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("👗 AI 피팅 시작")
                    }

                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("다시 선택")
                    }
                } else {
                    // 이미지 미선택 안내
                    Spacer(modifier = Modifier.height(60.dp))
                    Text(
                        text = "📱 다른 앱에서 의류 이미지를\n스크린샷으로 캡처해보세요!",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "캡처 후 화면 우측 하단의\n\"이 옷 입어보기 👕\" 버튼을 눌러주세요",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 로딩 상태: 프로그레스 인디케이터 ──
            is FittingUiState.Loading -> {
                Spacer(modifier = Modifier.height(60.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            // ── 성공 상태: 피팅 결과 이미지 표시 ──
            is FittingUiState.Success -> {
                Text("✅ 피팅 완료!", style = MaterialTheme.typography.titleMedium)

                AsyncImage(
                    model = state.resultImageUrl,
                    contentDescription = "AI 피팅 결과",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f),
                    contentScale = ContentScale.Crop
                )

                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("다른 옷 피팅하기")
                }
            }

            // ── 오류 상태: 에러 메시지 및 재시도 ──
            is FittingUiState.Error -> {
                Spacer(modifier = Modifier.height(60.dp))
                Text(
                    text = "❌ 오류 발생",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = onStartFitting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("재시도")
                }
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("처음으로")
                }
            }
        }
    }
}
