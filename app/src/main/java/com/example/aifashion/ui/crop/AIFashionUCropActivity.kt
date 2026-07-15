package com.example.aifashion.ui.crop

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.yalantis.ucrop.UCropActivity

/**
 * uCrop 기본 상단 툴바는 Android 15+ 강제 edge-to-edge 인셋을 자체 처리하지 못해
 * X/확인 버튼이 상태바에 가려지는 문제가 있음(github.com/Yalantis/uCrop/issues/913).
 * 상단 툴바를 아예 숨기고, 하단 Crop/Rotate/Scale 아이콘 줄(wrapper_states)
 * 양 끝에 X(취소)/확인 아이콘을 끼워 넣어 한 줄로 합친다.
 */
class AIFashionUCropActivity : UCropActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        findViewById<Toolbar>(com.yalantis.ucrop.R.id.toolbar)?.visibility = View.GONE

        val statesRow = findViewById<LinearLayout>(com.yalantis.ucrop.R.id.wrapper_states)
        statesRow?.addView(buildIconButton("✕") { onBackPressed() }, 0)
        statesRow?.addView(buildIconButton("✓") { cropAndSaveImage() })

        val content = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private fun buildIconButton(icon: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = icon
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }
}
