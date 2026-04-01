package com.example.aifashion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import com.example.aifashion.R
import com.example.aifashion.ui.crop.CropActivity
import android.provider.Settings

/**
 * 글로벌 스크린샷 감지 포그라운드 서비스
 *
 * 동작 원리:
 * 1. 포그라운드 서비스로 실행되어 앱이 백그라운드에 있어도 지속 동작
 * 2. ContentObserver로 MediaStore.Images.Media.EXTERNAL_CONTENT_URI를 감시
 * 3. 새 이미지가 "Screenshots" 폴더에 추가되면 플로팅 버튼 표시
 * 4. 버튼 클릭 시 CropActivity로 해당 이미지 URI 전달
 */
class CaptureDetectionService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var screenshotObserver: ContentObserver
    private var floatingButton: Button? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // 플로팅 버튼 자동 제거 Runnable (10초 후)
    private val autoRemoveRunnable = Runnable { removeFloatingButton() }

    companion object {
        private const val CHANNEL_ID = "capture_detection_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 포그라운드 서비스 시작 (알림 필수)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())

        // ContentObserver 등록: 외부 스토리지의 이미지 변화 감시 시작
        registerScreenshotObserver()
    }

    /**
     * MediaStore 이미지 URI를 감시하는 ContentObserver 등록
     * 새 이미지 삽입 시 onChange() 콜백 호출됨
     */
    private fun registerScreenshotObserver() {
        screenshotObserver = object : ContentObserver(mainHandler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                // URI가 null이면 기본 테이블 URI를 기준으로 최근 이미지 검색
                checkIfRecentScreenshot()
            }
        }

        // EXTERNAL_CONTENT_URI에 true(descendant URI 포함)로 등록
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            screenshotObserver
        )
    }

    /**
     * 최근 5초 이내에 추가된 이미지 중 Screenshots 폴더의 파일인지 확인
     * onChange에서 전달되는 URI가 테이블 레벨일 수 있으므로 직접 쿼리
     */
    private fun checkIfRecentScreenshot() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )

        // 현재 시각 기준 최근 5초 이내 생성된 이미지만 필터링
        val fiveSecondsAgo = (System.currentTimeMillis() / 1000L) - 5
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(fiveSecondsAgo.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val relativePath = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                )

                // "Screenshots" 폴더에 있는 파일인지 확인
                if (relativePath.contains("Screenshots", ignoreCase = true)) {
                    val id = cursor.getLong(
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    )
                    // 특정 이미지의 Content URI 생성
                    val screenshotUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                    // 메인 스레드에서 플로팅 버튼 표시
                    mainHandler.post { showFloatingButton(screenshotUri) }
                }
            }
        }
    }

    /**
     * 화면 우측 하단에 플로팅 오버레이 버튼 표시
     * SYSTEM_ALERT_WINDOW 권한 필요 (Settings.canDrawOverlays 확인)
     *
     * @param screenshotUri 감지된 스크린샷 이미지의 Content URI
     */
    private fun showFloatingButton(screenshotUri: Uri) {
        // SYSTEM_ALERT_WINDOW 권한 확인
        if (!Settings.canDrawOverlays(this)) return

        // 기존 버튼이 있으면 먼저 제거
        removeFloatingButton()
        mainHandler.removeCallbacks(autoRemoveRunnable)

        // WindowManager 레이아웃 파라미터 설정
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // API 26+ 필수
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 48   // 우측 여백
            y = 120  // 하단 여백
        }

        // 플로팅 버튼 생성 및 스타일 설정
        val button = Button(this).apply {
            text = "이 옷 입어보기 👕"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#FF6B35"))
            setTextColor(Color.WHITE)
            setPadding(40, 24, 40, 24)

            setOnClickListener {
                // 클릭 시: 버튼 제거 후 CropActivity 실행
                removeFloatingButton()
                openCropActivity(screenshotUri)
            }
        }

        floatingButton = button
        windowManager.addView(button, params)

        // 10초 후 자동으로 버튼 제거
        mainHandler.postDelayed(autoRemoveRunnable, 10_000L)
    }

    /**
     * 플로팅 버튼을 WindowManager에서 안전하게 제거
     */
    private fun removeFloatingButton() {
        floatingButton?.let {
            try {
                windowManager.removeView(it)
            } catch (e: IllegalArgumentException) {
                // 이미 제거된 뷰 - 무시
            }
            floatingButton = null
        }
    }

    /**
     * CropActivity를 새 태스크로 실행
     * 서비스에서 Activity를 시작할 때는 FLAG_ACTIVITY_NEW_TASK 필수
     *
     * @param imageUri 크롭할 원본 스크린샷 URI
     */
    private fun openCropActivity(imageUri: Uri) {
        val intent = Intent(this, CropActivity::class.java).apply {
            putExtra(CropActivity.EXTRA_IMAGE_URI, imageUri.toString())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // ContentObserver 해제 (메모리 누수 방지)
        contentResolver.unregisterContentObserver(screenshotObserver)
        // 플로팅 버튼 제거
        mainHandler.removeCallbacks(autoRemoveRunnable)
        removeFloatingButton()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 포그라운드 서비스용 알림 채널 생성 (API 26+ 필수)
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "스크린샷 감지 서비스",
            NotificationManager.IMPORTANCE_LOW // 소리 없는 조용한 알림
        ).apply {
            description = "AI Fashion 스크린샷 감지 서비스가 실행 중입니다"
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /**
     * 포그라운드 서비스 유지를 위한 상시 알림 생성
     */
    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Fashion")
            .setContentText("스크린샷을 감지하면 가상 피팅을 제안합니다")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true) // 사용자가 스와이프로 제거 불가
            .setSilent(true)  // 알림음 없음
            .build()
    }
}
