<!-- 2. FULL UPDATED: DodgeAccessibilityService.kt -->
package com.example.brawldodger  // Your package

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.content.Intent
import kotlin.math.min
import kotlin.math.max
import kotlin.random.Random

class DodgeAccessibilityService : AccessibilityService() {

    companion object {
        var instance: DodgeAccessibilityService? = null
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isActive = false
    private var isAutoAim = false
    private var screenWidth = 0f
    private var screenHeight = 0f

    override fun onServiceConnected() {
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels.toFloat()
        screenHeight = metrics.heightPixels.toFloat()
        createOverlay()
    }

    private fun createOverlay() {
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_toggle, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 100
        }

        windowManager.addView(overlayView, params)

        val btnToggle = overlayView!!.findViewById<ImageButton>(R.id.btn_toggle)
        btnToggle.setOnClickListener { toggleCapture() }

        val tvStatus = overlayView!!.findViewById<TextView>(R.id.tv_status)
        val switchAim = overlayView!!.findViewById<Switch>(R.id.switch_aim)
        switchAim.setOnCheckedChangeListener { _, checked ->
            isAutoAim = checked
        }
    }

    private fun toggleCapture() {
        if (isActive) {
            val intent = Intent(this, DodgeCaptureService::class.java).apply { action = "STOP" }
            startForegroundService(intent)
            val tvStatus = overlayView!!.findViewById<TextView>(R.id.tv_status)
            tvStatus.text = "OFF"
            tvStatus.setTextColor(Color.RED)
            isActive = false
        } else {
            val intent = Intent(this, TransparentCaptureActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            val tvStatus = overlayView!!.findViewById<TextView>(R.id.tv_status)
            tvStatus.text = "ON"
            tvStatus.setTextColor(Color.GREEN)
            isActive = true
        }
    }

    fun dispatchDodge(dir: String) {
        val path = Path()
        val joyCenterX = screenWidth * 0.2f
        val joyCenterY = screenHeight * 0.8f
        when (dir) {
            "left" -> {
                path.moveTo(joyCenterX + 120, joyCenterY)
                path.lineTo(joyCenterX - 180, joyCenterY)
            }
            "right" -> {
                path.moveTo(joyCenterX - 120, joyCenterY)
                path.lineTo(joyCenterX + 180, joyCenterY)
            }
            "jump" -> {
                path.moveTo(joyCenterX, joyCenterY + 100)
                path.lineTo(joyCenterX, joyCenterY - 150)
            }
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 120))
            .build()
        dispatchGesture(gesture, null, null)
    }

    fun dispatchAim(targetNormX: Float, targetNormY: Float) {
        var aimX = targetNormX + Random.nextFloat(-0.03f, 0.03f)
        var aimY = targetNormY + Random.nextFloat(-0.02f, 0.02f)
        aimX = max(0.1f, min(0.9f, aimX))
        aimY = max(0.1f, min(0.8f, aimY))

        val startX = screenWidth * 0.75f  // Right joystick start
        val startY = screenHeight * 0.6f
        val endX = screenWidth * aimX
        val endY = screenHeight * aimY

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 150))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        overlayView?.let { windowManager.removeView(it) }
    }
}
