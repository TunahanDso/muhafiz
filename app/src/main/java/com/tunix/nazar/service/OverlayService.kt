package com.tunix.nazar.service

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayLayoutParams: WindowManager.LayoutParams? = null
    private var isOverlayAttached: Boolean = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val showOverlay = intent?.getBooleanExtra(EXTRA_SHOW_OVERLAY, true) ?: false

        if (showOverlay) {
            showOverlay()
        } else {
            hideOverlay()
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hideOverlay()
        overlayLayoutParams = null
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        val wm = windowManager ?: return

        val view = overlayView ?: createOverlayView().also {
            overlayView = it
        }

        val params = overlayLayoutParams ?: createLayoutParams().also {
            overlayLayoutParams = it
        }

        try {
            when {
                !isOverlayAttached -> {
                    wm.addView(view, params)
                    isOverlayAttached = true
                }

                view.parent != null -> {
                    wm.updateViewLayout(view, params)
                }

                else -> {
                    isOverlayAttached = false
                    wm.addView(view, params)
                    isOverlayAttached = true
                }
            }

            view.alpha = 1f
            view.visibility = View.VISIBLE
            view.bringToFront()
        } catch (_: Exception) {
            resetOverlayState()
        }
    }

    private fun hideOverlay() {
        val wm = windowManager
        val view = overlayView

        if (wm == null || view == null) {
            resetOverlayState()
            return
        }

        try {
            if (isOverlayAttached && view.parent != null) {
                wm.removeView(view)
            } else if (view.isAttachedToWindow) {
                wm.removeView(view)
            }
        } catch (_: Exception) {
            // Overlay zaten kaldırılmış olabilir.
        } finally {
            resetOverlayState()
        }
    }

    private fun resetOverlayState() {
        isOverlayAttached = false
        overlayView = null
    }

    private fun createOverlayView(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 1f

            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            isClickable = true
            isFocusable = false
            isFocusableInTouchMode = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                dpToPx(32),
                dpToPx(32),
                dpToPx(32),
                dpToPx(32)
            )
        }

        val titleView = TextView(this).apply {
            text = getString(com.tunix.nazar.R.string.block_title)
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
            includeFontPadding = true
        }

        val messageView = TextView(this).apply {
            text = getString(com.tunix.nazar.R.string.block_message)
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            includeFontPadding = true
            setPadding(0, dpToPx(16), 0, 0)
        }

        content.addView(
            titleView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(
            messageView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        return root
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            title = "MuhafizOverlay"
            alpha = 1f
            dimAmount = 0f
        }
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    companion object {
        const val EXTRA_SHOW_OVERLAY = "extra_show_overlay"
    }
}