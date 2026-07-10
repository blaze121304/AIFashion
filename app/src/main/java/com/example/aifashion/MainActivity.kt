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
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.aifashion.service.CaptureDetectionService
import com.example.aifashion.ui.bodyphoto.BodyPhotoSection
import com.example.aifashion.ui.crop.CropActivity
import com.example.aifashion.ui.theme.AIFashionTheme
import com.example.aifashion.viewmodel.BodyPhotoViewModel
import com.example.aifashion.viewmodel.FittingUiState
import com.example.aifashion.viewmodel.FittingViewModel
import kotlinx.coroutines.launch

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

    private val bodyPhotoViewModel: BodyPhotoViewModel by viewModels {
        BodyPhotoViewModel.Factory(application)
    }

    // 크롭된 이미지 URI를 Compose 상태로 관리
    private var croppedImageUri by mutableStateOf<Uri?>(null)

    /** 사진첩에서 몸사진을 고르기 위한 Photo Picker (별도 미디어 권한 불필요) */
    private val pickBodyPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { bodyPhotoViewModel.uploadPhoto(it, contentResolver) }
    }

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
                    MainScreen(
                        croppedImageUri = croppedImageUri,
                        fittingViewModel = fittingViewModel,
                        bodyPhotoViewModel = bodyPhotoViewModel,
                        onStartFitting = { bodyPhotoId ->
                            // 피팅 시작: ViewModel에 크롭 이미지 URI + 선택된 몸사진 ID 전달
                            croppedImageUri?.let { uri ->
                                fittingViewModel.submitFittingJob(uri, bodyPhotoId, contentResolver)
                            }
                        },
                        onReset = {
                            fittingViewModel.reset()
                            croppedImageUri = null
                        },
                        onPickBodyPhoto = {
                            pickBodyPhotoLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
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
 * 메인 화면: 세로 4:1 분할
 * 상단(4) = 옷 캡처/피팅 영역, 하단(1) = 내 몸 사진 카드 섹션
 */
@Composable
fun MainScreen(
    croppedImageUri: Uri?,
    fittingViewModel: FittingViewModel,
    bodyPhotoViewModel: BodyPhotoViewModel,
    onStartFitting: (String) -> Unit,
    onReset: () -> Unit,
    onPickBodyPhoto: () -> Unit
) {
    val bodyPhotoState by bodyPhotoViewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(4f)) {
            FittingScreen(
                croppedImageUri = croppedImageUri,
                viewModel = fittingViewModel,
                selectedBodyPhotoId = bodyPhotoState.selectedPhotoId,
                onStartFitting = onStartFitting,
                onReset = onReset
            )
        }

        HorizontalDivider()

        Box(modifier = Modifier.weight(1f)) {
            BodyPhotoSection(
                state = bodyPhotoState,
                onSelect = bodyPhotoViewModel::selectPhoto,
                onDelete = bodyPhotoViewModel::deletePhoto,
                onAddClick = onPickBodyPhoto
            )
        }
    }
}

/**
 * 옷 캡처/피팅 화면 (상단 영역)
 * FittingUiState에 따라 동적으로 UI를 렌더링
 */
@Composable
fun FittingScreen(
    croppedImageUri: Uri?,
    viewModel: FittingViewModel,
    selectedBodyPhotoId: String?,
    onStartFitting: (String) -> Unit,
    onReset: () -> Unit
) {
    // StateFlow를 Compose 상태로 수집 (생명주기 인식)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                        onClick = { selectedBodyPhotoId?.let(onStartFitting) },
                        enabled = selectedBodyPhotoId != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("👗 AI 피팅 시작")
                    }

                    if (selectedBodyPhotoId == null) {
                        Text(
                            text = "아래에서 내 몸 사진을 먼저 등록/선택해주세요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("다시 선택")
                    }
                } else {
                    // 이미지 미선택 안내
                    Spacer(modifier = Modifier.height(24.dp))
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
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }

            // ── 성공 상태: 피팅 결과 이미지 표시 + 저장/공유 ──
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val uri = viewModel.ensureSavedImageUri(context)
                                Toast.makeText(
                                    context,
                                    if (uri != null) "갤러리에 저장했습니다" else "저장에 실패했습니다",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("💾 저장")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val uri = viewModel.ensureSavedImageUri(context)
                                if (uri != null) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "공유하기"))
                                } else {
                                    Toast.makeText(context, "공유용 이미지를 준비하지 못했습니다", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📤 공유")
                    }
                }

                Button(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("다른 옷 피팅하기")
                }
            }

            // ── 오류 상태: 에러 메시지 및 재시도 ──
            is FittingUiState.Error -> {
                Spacer(modifier = Modifier.height(24.dp))
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
                    onClick = { selectedBodyPhotoId?.let(onStartFitting) },
                    enabled = selectedBodyPhotoId != null,
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
