package com.tunix.nazar.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.tunix.nazar.MainActivity
import com.tunix.nazar.R
import com.tunix.nazar.domain.UnderlayEvidence
import com.tunix.nazar.ml.BinaryNsfwInterpreter
import com.tunix.nazar.ml.UnderlayFrameClassifier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android 14+ advanced recovery path.
 *
 * While the user still sees an opaque TYPE_ACCESSIBILITY_OVERLAY, this service
 * can capture the underlying application window via takeScreenshotOfWindow().
 * It never performs clicks, gestures, global navigation or text entry.
 */
class MuhafizAccessibilityService : AccessibilityService() {

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private val analysisExecutor: ExecutorService =
        Executors.newSingleThreadExecutor()

    private val modelReady =
        AtomicBoolean(false)

    private val screenshotInFlight =
        AtomicBoolean(false)

    private val tornDown =
        AtomicBoolean(false)

    private var interpreter: BinaryNsfwInterpreter? = null
    private var frameClassifier: UnderlayFrameClassifier? = null

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var overlayMessageView: TextView? = null
    private var fallbackButton: Button? = null
    private var overlayAttached = false

    @Volatile
    private var blockedSessionGeneration: Long = NO_SESSION

    @Volatile
    private var verificationGeneration: Long = 0L

    private var currentCleanSamples = 0

    private val verificationRunnable =
        Runnable {
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                beginWindowVerification()
            }
        }

    override fun onServiceConnected() {
        super.onServiceConnected()

        activeInstance = this

        windowManager =
            getSystemService(WINDOW_SERVICE) as WindowManager

        /*
         * XML already requests interactive windows. Keeping the flag here makes
         * the runtime contract explicit if an OEM rewrites ServiceInfo fields.
         */
        val updatedServiceInfo =
            serviceInfo

        updatedServiceInfo.flags =
            updatedServiceInfo.flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

        serviceInfo =
            updatedServiceInfo

        analysisExecutor.execute {
            val localInterpreter =
                BinaryNsfwInterpreter(this)

            val loaded =
                localInterpreter.loadModel()

            if (
                tornDown.get()
            ) {
                localInterpreter.close()
                return@execute
            }

            if (loaded) {
                interpreter = localInterpreter
                frameClassifier =
                    UnderlayFrameClassifier(
                        localInterpreter
                    )
                modelReady.set(true)

                if (
                    blockedSessionGeneration !=
                    NO_SESSION
                ) {
                    mainHandler.post {
                        scheduleVerification()
                    }
                }
            } else {
                localInterpreter.close()
                modelReady.set(false)
            }
        }
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            blockedSessionGeneration ==
            NO_SESSION
        ) {
            return
        }

        val sourcePackage =
            event?.packageName
                ?.toString()

        /*
         * Our own overlay/activity and System UI are never evidence that the
         * underlying third-party application became safe.
         */
        if (
            sourcePackage == packageName ||
            sourcePackage ==
            SYSTEM_UI_PACKAGE
        ) {
            return
        }

        if (
            event == null ||
            !isVerificationTrigger(
                event.eventType
            )
        ) {
            return
        }

        scheduleVerification()
    }

    override fun onInterrupt() {
        notifyAccessibilityUnavailable()
    }

    override fun onUnbind(
        intent: Intent?
    ): Boolean {
        notifyAccessibilityUnavailable()
        teardownLocalState()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        notifyAccessibilityUnavailable()
        teardownLocalState()
        super.onDestroy()
    }

    private fun teardownLocalState() {
        if (
            !tornDown.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        if (activeInstance === this) {
            activeInstance = null
        }

        blockedSessionGeneration =
            NO_SESSION

        verificationGeneration++

        mainHandler.removeCallbacks(
            verificationRunnable
        )

        screenshotInFlight.set(false)

        hideOverlayInternal()

        modelReady.set(false)

        analysisExecutor.execute {
            interpreter?.close()
            interpreter = null
            frameClassifier = null
        }

        analysisExecutor.shutdown()
    }

    private fun scheduleVerification() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            blockedSessionGeneration ==
            NO_SESSION
        ) {
            return
        }

        verificationGeneration++

        val session =
            blockedSessionGeneration

        val verification =
            verificationGeneration

        currentCleanSamples = 0

        sendCommandToCaptureService(
            action =
                ScreenCaptureService.ACTION_UNDERLAY_VERIFY_PENDING
        ) {
            putExtra(
                ScreenCaptureService.EXTRA_SESSION_GENERATION,
                session
            )
            putExtra(
                ScreenCaptureService.EXTRA_VERIFICATION_GENERATION,
                verification
            )
        }

        showWaitingMessage()

        mainHandler.removeCallbacks(
            verificationRunnable
        )

        mainHandler.postDelayed(
            verificationRunnable,
            VERIFICATION_DEBOUNCE_MS
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun beginWindowVerification() {
        val session =
            blockedSessionGeneration

        if (
            session == NO_SESSION ||
            !modelReady.get()
        ) {
            showManualFallback()
            return
        }

        if (
            screenshotInFlight.get()
        ) {
            return
        }

        val verification =
            verificationGeneration

        val target =
            selectSingleRelevantApplicationWindow()

        if (target == null) {
            showManualFallback()
            return
        }

        sendCommandToCaptureService(
            action =
                ScreenCaptureService.ACTION_UNDERLAY_VERIFY_STARTED
        ) {
            putExtra(
                ScreenCaptureService.EXTRA_SESSION_GENERATION,
                session
            )
            putExtra(
                ScreenCaptureService.EXTRA_VERIFICATION_GENERATION,
                verification
            )
            putExtra(
                ScreenCaptureService.EXTRA_WINDOW_ID,
                target.windowId
            )
            putExtra(
                ScreenCaptureService.EXTRA_WINDOW_PACKAGE,
                target.packageName
            )
        }

        showVerifyingMessage()

        takeVerificationScreenshot(
            session = session,
            verification = verification,
            target = target
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun takeVerificationScreenshot(
        session: Long,
        verification: Long,
        target: TargetWindow
    ) {
        if (
            blockedSessionGeneration != session ||
            verificationGeneration != verification ||
            !screenshotInFlight.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        try {
            takeScreenshotOfWindow(
                target.windowId,
                analysisExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(
                        screenshotResult: AccessibilityService.ScreenshotResult
                    ) {
                        screenshotInFlight.set(false)

                        handleScreenshotSuccess(
                            session = session,
                            verification = verification,
                            target = target,
                            screenshotResult = screenshotResult
                        )
                    }

                    override fun onFailure(
                        errorCode: Int
                    ) {
                        screenshotInFlight.set(false)

                        handleScreenshotFailure(
                            session = session,
                            verification = verification,
                            target = target,
                            errorCode = errorCode
                        )
                    }
                }
            )
        } catch (_: Exception) {
            screenshotInFlight.set(false)

            sendEvidence(
                session = session,
                verification = verification,
                target = target,
                evidence = UnderlayEvidence.ERROR
            )

            mainHandler.post {
                showManualFallback()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun handleScreenshotSuccess(
        session: Long,
        verification: Long,
        target: TargetWindow,
        screenshotResult: AccessibilityService.ScreenshotResult
    ) {
        val hardwareBuffer =
            screenshotResult.hardwareBuffer

        var hardwareBitmap: Bitmap? = null
        var softwareBitmap: Bitmap? = null

        try {
            if (
                !isVerificationCurrent(
                    session,
                    verification,
                    target
                )
            ) {
                return
            }

            hardwareBitmap =
                Bitmap.wrapHardwareBuffer(
                    hardwareBuffer,
                    screenshotResult.colorSpace
                )

            val wrapped =
                hardwareBitmap
                    ?: throw IllegalStateException(
                        "Accessibility screenshot bitmap oluşturulamadı."
                    )

            softwareBitmap =
                wrapped.copy(
                    Bitmap.Config.ARGB_8888,
                    false
                )

            val bitmap =
                softwareBitmap
                    ?: throw IllegalStateException(
                        "Accessibility screenshot software bitmap oluşturulamadı."
                    )

            val evidence =
                frameClassifier
                    ?.classify(bitmap)
                    ?: UnderlayEvidence.ERROR

            if (
                !isVerificationCurrent(
                    session,
                    verification,
                    target
                )
            ) {
                return
            }

            sendEvidence(
                session = session,
                verification = verification,
                target = target,
                evidence = evidence
            )

            when (evidence) {
                UnderlayEvidence.CLEAN -> {
                    currentCleanSamples++

                    /*
                     * ScreenCaptureService is the final authority. We continue
                     * providing current clean evidence until it hides the
                     * overlay after its own clean/hold requirements are met.
                     */
                    if (
                        blockedSessionGeneration ==
                        session &&
                        verificationGeneration ==
                        verification
                    ) {
                        mainHandler.postDelayed(
                            {
                                if (
                                    Build.VERSION.SDK_INT >=
                                    Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                                    isVerificationCurrent(
                                        session,
                                        verification,
                                        target
                                    )
                                ) {
                                    takeVerificationScreenshot(
                                        session,
                                        verification,
                                        target
                                    )
                                }
                            },
                            VERIFICATION_SAMPLE_INTERVAL_MS
                        )
                    }
                }

                UnderlayEvidence.RISKY -> {
                    currentCleanSamples = 0
                    mainHandler.post {
                        showWaitingMessage()
                    }
                }

                UnderlayEvidence.UNCERTAIN,
                UnderlayEvidence.SECURE,
                UnderlayEvidence.ERROR -> {
                    currentCleanSamples = 0
                    mainHandler.post {
                        showManualFallback()
                    }
                }
            }
        } catch (_: Exception) {
            sendEvidence(
                session = session,
                verification = verification,
                target = target,
                evidence = UnderlayEvidence.ERROR
            )

            mainHandler.post {
                showManualFallback()
            }
        } finally {
            try {
                softwareBitmap
                    ?.takeIf {
                        !it.isRecycled
                    }
                    ?.recycle()
            } catch (_: Exception) {
            }

            try {
                hardwareBitmap
                    ?.takeIf {
                        !it.isRecycled
                    }
                    ?.recycle()
            } catch (_: Exception) {
            }

            try {
                hardwareBuffer.close()
            } catch (_: Exception) {
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun handleScreenshotFailure(
        session: Long,
        verification: Long,
        target: TargetWindow,
        errorCode: Int
    ) {
        if (
            !isVerificationCurrent(
                session,
                verification,
                target
            )
        ) {
            return
        }

        if (
            errorCode ==
            ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
        ) {
            mainHandler.postDelayed(
                {
                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        isVerificationCurrent(
                            session,
                            verification,
                            target
                        )
                    ) {
                        takeVerificationScreenshot(
                            session,
                            verification,
                            target
                        )
                    }
                },
                RATE_LIMIT_BACKOFF_MS
            )

            return
        }

        val evidence =
            if (
                errorCode ==
                ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
            ) {
                UnderlayEvidence.SECURE
            } else {
                UnderlayEvidence.ERROR
            }

        sendEvidence(
            session = session,
            verification = verification,
            target = target,
            evidence = evidence
        )

        mainHandler.post {
            showManualFallback()
        }
    }

    private fun sendEvidence(
        session: Long,
        verification: Long,
        target: TargetWindow,
        evidence: UnderlayEvidence
    ) {
        sendCommandToCaptureService(
            action =
                ScreenCaptureService.ACTION_UNDERLAY_RESULT
        ) {
            putExtra(
                ScreenCaptureService.EXTRA_SESSION_GENERATION,
                session
            )
            putExtra(
                ScreenCaptureService.EXTRA_VERIFICATION_GENERATION,
                verification
            )
            putExtra(
                ScreenCaptureService.EXTRA_WINDOW_ID,
                target.windowId
            )
            putExtra(
                ScreenCaptureService.EXTRA_WINDOW_PACKAGE,
                target.packageName
            )
            putExtra(
                ScreenCaptureService.EXTRA_UNDERLAY_EVIDENCE,
                evidence.name
            )
        }
    }

    private fun sendCommandToCaptureService(
        action: String,
        extras: Intent.() -> Unit = {}
    ) {
        try {
            startService(
                Intent(
                    this,
                    ScreenCaptureService::class.java
                ).apply {
                    this.action = action
                    extras()
                }
            )
        } catch (_: Exception) {
            // Capture service may already be stopping. Never reveal content here.
        }
    }

    private fun notifyAccessibilityUnavailable() {
        if (
            blockedSessionGeneration ==
            NO_SESSION
        ) {
            return
        }

        sendCommandToCaptureService(
            action =
                ScreenCaptureService.ACTION_ACCESSIBILITY_UNAVAILABLE
        ) {
            putExtra(
                ScreenCaptureService.EXTRA_SESSION_GENERATION,
                blockedSessionGeneration
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun selectSingleRelevantApplicationWindow():
            TargetWindow? {
        val candidates =
            windows
                .filter { window ->
                    window.type ==
                            AccessibilityWindowInfo.TYPE_APPLICATION
                }
                .mapNotNull { window ->
                    val packageName =
                        readWindowPackageName(window)
                            ?: return@mapNotNull null

                    if (
                        packageName ==
                        this.packageName ||
                        packageName ==
                        SYSTEM_UI_PACKAGE
                    ) {
                        return@mapNotNull null
                    }

                    TargetWindow(
                        windowId = window.id,
                        packageName = packageName,
                        layer = window.layer,
                        active = window.isActive,
                        focused = window.isFocused
                    )
                }

        if (candidates.isEmpty()) {
            return null
        }

        /*
         * Multiple distinct visible application packages (split screen / PiP)
         * cannot be safely proven clean by checking only one of them.
         */
        val packages =
            candidates
                .map {
                    it.packageName
                }
                .distinct()

        if (packages.size != 1) {
            return null
        }

        return candidates
            .sortedWith(
                compareByDescending<TargetWindow> {
                    it.focused
                }.thenByDescending {
                    it.active
                }.thenByDescending {
                    it.layer
                }
            )
            .firstOrNull()
    }

    private fun readWindowPackageName(
        window: AccessibilityWindowInfo
    ): String? {
        val root =
            try {
                window.root
            } catch (_: Exception) {
                null
            } ?: return null

        return try {
            root.packageName
                ?.toString()
                ?.takeIf {
                    it.isNotBlank()
                }
        } finally {
            @Suppress("DEPRECATION")
            try {
                root.recycle()
            } catch (_: Exception) {
            }
        }
    }

    private fun isVerificationCurrent(
        session: Long,
        verification: Long,
        target: TargetWindow
    ): Boolean {
        if (
            blockedSessionGeneration != session ||
            verificationGeneration != verification
        ) {
            return false
        }

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            return false
        }

        val currentTarget =
            selectSingleRelevantApplicationWindow()
                ?: return false

        return currentTarget.windowId ==
                target.windowId &&
                currentTarget.packageName ==
                target.packageName
    }

    private fun isVerificationTrigger(
        eventType: Int
    ): Boolean {
        return eventType ==
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType ==
                AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
                eventType ==
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                eventType ==
                AccessibilityEvent.TYPE_VIEW_SCROLLED
    }

    private fun showOverlayInternal(
        sessionGeneration: Long
    ) {
        blockedSessionGeneration =
            sessionGeneration

        verificationGeneration++
        currentCleanSamples = 0

        mainHandler.removeCallbacks(
            verificationRunnable
        )

        val wm =
            windowManager
                ?: return

        val view =
            overlayView
                ?: createOverlayView()
                    .also {
                        overlayView = it
                    }

        try {
            if (
                !overlayAttached ||
                view.parent == null
            ) {
                wm.addView(
                    view,
                    createOverlayLayoutParams()
                )
                overlayAttached = true
            }

            view.visibility =
                View.VISIBLE

            view.bringToFront()

            showWaitingMessage()
        } catch (_: Exception) {
            overlayAttached = false
            notifyAccessibilityUnavailable()
        }
    }

    private fun hideOverlayInternal() {
        mainHandler.removeCallbacks(
            verificationRunnable
        )

        blockedSessionGeneration =
            NO_SESSION

        verificationGeneration++
        currentCleanSamples = 0

        val wm =
            windowManager

        val view =
            overlayView

        if (
            wm != null &&
            view != null
        ) {
            try {
                if (
                    overlayAttached &&
                    view.parent != null
                ) {
                    wm.removeView(view)
                }
            } catch (_: Exception) {
            }
        }

        overlayAttached = false
        overlayView = null
        overlayMessageView = null
        fallbackButton = null
    }

    private fun createOverlayView(): View {
        val root =
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                isClickable = true
                isFocusable = false
                isFocusableInTouchMode = false
            }

        val content =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER

                setPadding(
                    dpToPx(32),
                    dpToPx(32),
                    dpToPx(32),
                    dpToPx(32)
                )
            }

        val title =
            TextView(this).apply {
                text =
                    getString(
                        R.string.block_title
                    )

                setTextColor(Color.WHITE)
                textSize = 28f
                gravity = Gravity.CENTER
            }

        val message =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(
                    0,
                    dpToPx(16),
                    0,
                    0
                )
            }

        overlayMessageView =
            message

        val button =
            Button(this).apply {
                text =
                    getString(
                        R.string.block_return_to_muhafiz
                    )

                isAllCaps = false
                visibility = View.GONE

                setOnClickListener {
                    openMuhafiz()
                }
            }

        fallbackButton =
            button

        content.addView(
            title,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(
            message,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(
            button,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dpToPx(24)
            }
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

    private fun createOverlayLayoutParams():
            WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE
        ).apply {
            gravity =
                Gravity.TOP or
                        Gravity.START

            title =
                "MuhafizAccessibilityOverlay"

            alpha = 1f
            dimAmount = 0f
        }
    }

    private fun showWaitingMessage() {
        overlayMessageView?.text =
            getString(
                R.string.block_message_auto
            )

        fallbackButton?.visibility =
            View.GONE
    }

    private fun showVerifyingMessage() {
        overlayMessageView?.text =
            getString(
                R.string.block_message_verifying
            )

        fallbackButton?.visibility =
            View.GONE
    }

    private fun showManualFallback() {
        overlayMessageView?.text =
            getString(
                R.string.block_message_manual
            )

        fallbackButton?.visibility =
            View.VISIBLE
    }

    private fun openMuhafiz() {
        try {
            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                ).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
            )
        } catch (_: Exception) {
            // Fail closed: keep the overlay visible.
        }
    }

    private fun dpToPx(
        value: Int
    ): Int {
        return (
                value *
                        resources.displayMetrics.density
                ).toInt()
    }

    private data class TargetWindow(
        val windowId: Int,
        val packageName: String,
        val layer: Int,
        val active: Boolean,
        val focused: Boolean
    )

    companion object {
        private const val NO_SESSION =
            -1L

        private const val SYSTEM_UI_PACKAGE =
            "com.android.systemui"

        private const val VERIFICATION_DEBOUNCE_MS =
            500L

        private const val VERIFICATION_SAMPLE_INTERVAL_MS =
            750L

        private const val RATE_LIMIT_BACKOFF_MS =
            1000L

        @Volatile
        private var activeInstance:
                MuhafizAccessibilityService? =
            null

        fun isConnected(): Boolean {
            return activeInstance != null
        }

        fun showProtectionOverlay(
            sessionGeneration: Long
        ): Boolean {
            val service =
                activeInstance
                    ?: return false

            service.mainHandler.post {
                service.showOverlayInternal(
                    sessionGeneration
                )
            }

            return true
        }

        fun hideProtectionOverlay() {
            val service =
                activeInstance
                    ?: return

            service.mainHandler.post {
                service.hideOverlayInternal()
            }
        }

        fun showManualFallbackMessage() {
            val service =
                activeInstance
                    ?: return

            service.mainHandler.post {
                service.showManualFallback()
            }
        }

        fun isEnabled(
            context: Context
        ): Boolean {
            if (
                Build.VERSION.SDK_INT <
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                return false
            }

            val manager =
                context.getSystemService(
                    Context.ACCESSIBILITY_SERVICE
                ) as AccessibilityManager

            val enabled =
                manager
                    .getEnabledAccessibilityServiceList(
                        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                    )

            return enabled.any { info ->
                val serviceInfo =
                    info.resolveInfo
                        ?.serviceInfo
                        ?: return@any false

                serviceInfo.packageName ==
                        context.packageName &&
                        serviceInfo.name ==
                        MuhafizAccessibilityService::class.java.name
            }
        }

        fun openSettingsIntent(): Intent {
            return Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            )
        }
    }
}
