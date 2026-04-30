package com.tunix.nazar.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tunix.nazar.R
import com.tunix.nazar.ml.BinaryNsfwInterpreter
import com.tunix.nazar.ml.BinaryNsfwResult
import com.tunix.nazar.projection.ImageFrameReader
import com.tunix.nazar.projection.ProjectionController
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    private lateinit var projectionController: ProjectionController

    private var imageFrameReader: ImageFrameReader? = null
    private var binaryNsfwInterpreter: BinaryNsfwInterpreter? = null
    private var notificationManager: NotificationManager? = null

    private val isProcessingFrame = AtomicBoolean(false)
    private val serviceRunning = AtomicBoolean(false)
    private val isReconfiguringCapture = AtomicBoolean(false)

    private var lastAnalysisTimestamp: Long = 0L
    private var lastNotificationTimestamp: Long = 0L
    private var lastDisplayCheckTimestamp: Long = 0L

    private var blankFrameStartedTimestamp: Long = 0L

    private var overlayVisibleSinceTimestamp: Long = 0L
    private var lastRiskDetectedTimestamp: Long = 0L

    private var highRiskFrameCount: Int = 0
    private var clearFrameCount: Int = 0
    private var isOverlayVisible: Boolean = false

    /*
     * Binary model için skor geçmişi tutulur.
     * Tek karelik yanlış pozitifleri bastırmak ve ancak tekrarlı/güçlü kanıtta filtre açmak için kullanılır.
     */
    private val recentBinaryResults = mutableListOf<BinaryNsfwResult>()

    private var screenWidthPx: Int = 0
    private var screenHeightPx: Int = 0
    private var analysisWidthPx: Int = 0
    private var analysisHeightPx: Int = 0

    private var currentNotificationTitle: String = ""
    private var currentNotificationText: String = ""

    override fun onCreate() {
        super.onCreate()

        projectionController = ProjectionController(this)
        binaryNsfwInterpreter = BinaryNsfwInterpreter(this)
        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (serviceRunning.get()) {
            return START_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(
                title = "Muhafız koruması aktif",
                text = "Koruma başlatılıyor"
            )
        )

        if (resultCode != Activity.RESULT_OK || data == null) {
            updateUserNotification(
                title = "Muhafız koruması durdu",
                text = "Ekran izni alınamadı"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val modelLoaded = binaryNsfwInterpreter?.loadModel() == true
            if (!modelLoaded) {
                val reason = binaryNsfwInterpreter?.lastErrorMessage ?: "Binary NSFW modeli yüklenemedi"
                updateUserNotification(
                    title = "Muhafız koruması durdu",
                    text = reason.take(MAX_NOTIFICATION_TEXT_LENGTH)
                )
                stopSelf()
                return START_NOT_STICKY
            }

            projectionController.startProjection(resultCode, data)

            if (!configureCaptureForCurrentDisplay()) {
                updateUserNotification(
                    title = "Muhafız koruması durdu",
                    text = "Ekran yakalama başlatılamadı"
                )
                stopSelf()
                return START_NOT_STICKY
            }

            resetDetectionState()
            serviceRunning.set(true)

            updateUserNotification(
                title = "Muhafız koruması aktif",
                text = "Ekran içeriği izleniyor"
            )
        } catch (e: Exception) {
            updateUserNotification(
                title = "Muhafız koruması durdu",
                text = "${e.javaClass.simpleName}: ${e.message ?: "başlatma hatası"}"
                    .take(MAX_NOTIFICATION_TEXT_LENGTH)
            )
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceRunning.set(false)

        hideOverlay(force = true)

        releaseFrameReader()

        binaryNsfwInterpreter?.close()
        binaryNsfwInterpreter = null

        projectionController.stopProjection()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleIncomingFrame(bitmap: Bitmap) {
        val now = System.currentTimeMillis()

        if (!serviceRunning.get()) {
            bitmap.recycleSafely()
            return
        }

        if (maybeReconfigureCaptureForRotation(now)) {
            bitmap.recycleSafely()
            return
        }

        if (now - lastAnalysisTimestamp < ANALYSIS_INTERVAL_MS) {
            bitmap.recycleSafely()
            return
        }

        if (!isProcessingFrame.compareAndSet(false, true)) {
            bitmap.recycleSafely()
            return
        }

        lastAnalysisTimestamp = now

        try {
            val interpreter = binaryNsfwInterpreter
            if (interpreter == null || !interpreter.isReady()) {
                hideOverlay()
                return
            }

            /*
             * Siyah/boş frame hemen risk sayılmaz.
             * Uzun süreli siyah frame gizli sekme/korumalı içerik olabilir.
             */
            if (handleBlankFrameIfNeeded(bitmap, now)) {
                return
            }

            val classifiedFrame = classifyWithPatches(
                bitmap = bitmap,
                interpreter = interpreter
            )

            val result = classifiedFrame.result

            pushRecentResult(result)

            val averages = calculateBinaryAverages()
            val riskLevel = evaluateRiskLevel(
                current = result,
                averages = averages
            )

            handleDetectionResult(
                riskLevel = riskLevel,
                now = now
            )
        } catch (_: Exception) {
            keepOrHideOverlayAfterError(now)
        } finally {
            bitmap.recycleSafely()
            isProcessingFrame.set(false)
        }
    }

    private fun configureCaptureForCurrentDisplay(): Boolean {
        val displayMetrics = resources.displayMetrics

        screenWidthPx = displayMetrics.widthPixels
        screenHeightPx = displayMetrics.heightPixels

        val analysisSize = calculateAnalysisSize(
            screenWidth = screenWidthPx,
            screenHeight = screenHeightPx
        )

        analysisWidthPx = analysisSize.width
        analysisHeightPx = analysisSize.height

        releaseFrameReader()

        imageFrameReader = ImageFrameReader(
            width = analysisWidthPx,
            height = analysisHeightPx
        ).also { reader ->
            reader.initialize { bitmap ->
                handleIncomingFrame(bitmap)
            }
        }

        val surface = imageFrameReader?.getSurface() ?: return false

        projectionController.createVirtualDisplay(
            name = "MuhafizScreenCapture",
            width = analysisWidthPx,
            height = analysisHeightPx,
            densityDpi = displayMetrics.densityDpi,
            surface = surface
        )

        resetDetectionState()
        return true
    }

    private fun maybeReconfigureCaptureForRotation(now: Long): Boolean {
        if (now - lastDisplayCheckTimestamp < DISPLAY_CHECK_INTERVAL_MS) {
            return false
        }

        lastDisplayCheckTimestamp = now

        val displayMetrics = resources.displayMetrics
        val currentScreenWidth = displayMetrics.widthPixels
        val currentScreenHeight = displayMetrics.heightPixels

        val screenChanged =
            currentScreenWidth != screenWidthPx ||
                    currentScreenHeight != screenHeightPx

        if (!screenChanged) {
            return false
        }

        if (!isReconfiguringCapture.compareAndSet(false, true)) {
            return true
        }

        return try {
            configureCaptureForCurrentDisplay()
            true
        } catch (_: Exception) {
            false
        } finally {
            isReconfiguringCapture.set(false)
        }
    }

    private fun calculateAnalysisSize(
        screenWidth: Int,
        screenHeight: Int
    ): AnalysisSize {
        val safeScreenWidth = screenWidth.coerceAtLeast(1)
        val safeScreenHeight = screenHeight.coerceAtLeast(1)

        val isLandscape = safeScreenWidth >= safeScreenHeight
        val ratio = safeScreenWidth.toFloat() / safeScreenHeight.toFloat()

        return if (isLandscape) {
            /*
             * Yatay kullanımda yüksekliği sabit tutup genişliği orana göre büyütüyoruz.
             * Böylece 360x202 gibi çok basık analiz frame'i oluşmaz.
             */
            val height = LANDSCAPE_ANALYSIS_HEIGHT
            val width = (height * ratio)
                .toInt()
                .coerceIn(MIN_ANALYSIS_WIDTH, MAX_ANALYSIS_WIDTH)

            AnalysisSize(
                width = width,
                height = height
            )
        } else {
            /*
             * Dikey kullanımda mevcut davranış korunur:
             * genişlik sabit, yükseklik orana göre hesaplanır.
             */
            val width = PORTRAIT_ANALYSIS_WIDTH
            val height = (width / ratio)
                .toInt()
                .coerceIn(MIN_ANALYSIS_HEIGHT, MAX_ANALYSIS_HEIGHT)

            AnalysisSize(
                width = width,
                height = height
            )
        }
    }

    private fun releaseFrameReader() {
        try {
            imageFrameReader?.release()
        } catch (_: Exception) {
        }

        imageFrameReader = null
    }

    private fun handleDetectionResult(
        riskLevel: RiskLevel,
        now: Long
    ) {
        when (riskLevel) {
            RiskLevel.CRITICAL -> {
                highRiskFrameCount = REQUIRED_HIGH_RISK_FRAMES
                clearFrameCount = 0
                lastRiskDetectedTimestamp = now

                showOverlay(now)

                throttledNotification(
                    title = "Muhafız koruması aktif",
                    text = "İçerik gizlendi",
                    now = now
                )
            }

            RiskLevel.SUSTAINED -> {
                highRiskFrameCount++
                clearFrameCount = 0
                lastRiskDetectedTimestamp = now

                /*
                 * Kesin emin olmadan filtre basmamak için birkaç ardışık riskli karar beklenir.
                 */
                if (highRiskFrameCount >= REQUIRED_HIGH_RISK_FRAMES) {
                    showOverlay(now)

                    throttledNotification(
                        title = "Muhafız koruması aktif",
                        text = "İçerik gizlendi",
                        now = now
                    )
                }
            }

            RiskLevel.NONE -> {
                clearFrameCount++
                highRiskFrameCount = 0

                val shouldKeepVisible = shouldKeepOverlayVisible(now)

                /*
                 * Filtre açıldıysa, görüntünün gerçekten temiz olduğundan emin olmadan inmez.
                 */
                if (clearFrameCount >= REQUIRED_CLEAR_FRAMES && !shouldKeepVisible) {
                    hideOverlay()

                    throttledNotification(
                        title = "Muhafız koruması aktif",
                        text = "Ekran içeriği izleniyor",
                        now = now
                    )
                }
            }
        }
    }

    private fun evaluateRiskLevel(
        current: BinaryNsfwResult,
        averages: BinaryRiskAverages
    ): RiskLevel {
        /*
         * Tek frame ile CRITICAL sadece çok güçlü binary NSFW kanıtında verilir.
         */
        if (isImmediateCriticalRisk(current)) {
            return RiskLevel.CRITICAL
        }

        if (recentBinaryResults.size < MIN_HISTORY_FOR_DECISION) {
            return RiskLevel.NONE
        }

        val adultLikeFrameCount = countAdultLikeFrames()

        val sustainedRisk =
            averages.avgNsfw >= AVG_NSFW_BLOCK_THRESHOLD &&
                    averages.avgSfw <= AVG_SFW_MAX_FOR_BLOCK &&
                    adultLikeFrameCount >= MIN_ADULT_LIKE_FRAMES_FOR_BLOCK

        val veryConsistentRisk =
            averages.avgNsfw >= AVG_NSFW_STRONG_BLOCK_THRESHOLD &&
                    adultLikeFrameCount >= MIN_STRONG_ADULT_FRAMES_FOR_BLOCK

        return if (sustainedRisk || veryConsistentRisk) {
            RiskLevel.SUSTAINED
        } else {
            RiskLevel.NONE
        }
    }

    private fun isImmediateCriticalRisk(result: BinaryNsfwResult): Boolean {
        if (!result.isReady) {
            return false
        }

        return result.nsfwScore >= NSFW_CRITICAL_THRESHOLD &&
                result.sfwScore <= SFW_CRITICAL_MAX
    }

    private fun classifyWithPatches(
        bitmap: Bitmap,
        interpreter: BinaryNsfwInterpreter
    ): BinaryClassifiedFrame {
        /*
         * Full frame her zaman baz alınır.
         * Patch ancak güvenilir NSFW kanıtı taşırsa full frame'i geçebilir.
         */
        val fullResult = interpreter.classify(bitmap)

        var bestResult = fullResult
        var bestPatchName = "full"
        var bestPatchScore = fullResult.nsfwScore
        var trustedPatch = false

        val patchSpecs = getPatchSpecsForBitmap(bitmap)

        for (patch in patchSpecs) {
            if (patch.isFullFrame) {
                continue
            }

            val patchBitmap = createPatchBitmap(bitmap, patch) ?: continue

            try {
                val result = interpreter.classify(patchBitmap)

                if (!isReliablePatchAdultEvidence(result)) {
                    continue
                }

                if (result.nsfwScore > bestPatchScore) {
                    bestPatchScore = result.nsfwScore
                    bestPatchName = patch.name
                    bestResult = result
                    trustedPatch = true
                }
            } finally {
                if (!patchBitmap.isRecycled) {
                    patchBitmap.recycle()
                }
            }
        }

        return BinaryClassifiedFrame(
            result = bestResult,
            patchName = bestPatchName,
            patchAdultScore = bestPatchScore.coerceIn(0f, 1f),
            trustedPatch = trustedPatch
        )
    }

    private fun getPatchSpecsForBitmap(bitmap: Bitmap): List<PatchSpec> {
        return if (bitmap.width >= bitmap.height) {
            LANDSCAPE_PATCH_SPECS
        } else {
            PORTRAIT_PATCH_SPECS
        }
    }

    private fun createPatchBitmap(
        bitmap: Bitmap,
        patch: PatchSpec
    ): Bitmap? {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return null
        }

        if (patch.isFullFrame) {
            return bitmap
        }

        val left = (bitmap.width * patch.leftRatio).toInt()
            .coerceIn(0, bitmap.width - 1)

        val top = (bitmap.height * patch.topRatio).toInt()
            .coerceIn(0, bitmap.height - 1)

        val right = (bitmap.width * patch.rightRatio).toInt()
            .coerceIn(left + 1, bitmap.width)

        val bottom = (bitmap.height * patch.bottomRatio).toInt()
            .coerceIn(top + 1, bitmap.height)

        val width = right - left
        val height = bottom - top

        if (width <= 0 || height <= 0) {
            return null
        }

        return try {
            Bitmap.createBitmap(bitmap, left, top, width, height)
        } catch (_: Exception) {
            null
        }
    }

    private fun isReliablePatchAdultEvidence(result: BinaryNsfwResult): Boolean {
        if (!result.isReady) {
            return false
        }

        return result.nsfwScore >= PATCH_TRUSTED_NSFW_THRESHOLD &&
                result.sfwScore <= PATCH_MAX_SFW_FOR_TRUSTED_NSFW
    }

    private fun handleBlankFrameIfNeeded(
        bitmap: Bitmap,
        now: Long
    ): Boolean {
        val suspiciousBlank = isSuspiciousBlankFrame(bitmap)

        if (!suspiciousBlank) {
            blankFrameStartedTimestamp = 0L
            return false
        }

        /*
         * Overlay açıksa görülen siyah görüntü bizim kendi koruma perdemiz olabilir.
         */
        if (isOverlayVisible) {
            handleDetectionResult(
                riskLevel = RiskLevel.NONE,
                now = now
            )
            return true
        }

        if (blankFrameStartedTimestamp == 0L) {
            blankFrameStartedTimestamp = now
        }

        val blankDuration = now - blankFrameStartedTimestamp

        if (blankDuration >= REQUIRED_BLANK_FRAME_DURATION_MS) {
            handleDetectionResult(
                riskLevel = RiskLevel.CRITICAL,
                now = now
            )
        } else {
            handleDetectionResult(
                riskLevel = RiskLevel.NONE,
                now = now
            )
        }

        return true
    }

    private fun isSuspiciousBlankFrame(bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return false
        }

        val sampleStepX = (bitmap.width / BLANK_FRAME_SAMPLE_GRID).coerceAtLeast(1)
        val sampleStepY = (bitmap.height / BLANK_FRAME_SAMPLE_GRID).coerceAtLeast(1)

        var sampleCount = 0
        var veryDarkCount = 0
        var brightnessSum = 0f
        var minBrightness = 1f
        var maxBrightness = 0f

        var y = sampleStepY / 2
        while (y < bitmap.height) {
            var x = sampleStepX / 2
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)

                val r = (pixel shr 16 and 0xFF) / 255f
                val g = (pixel shr 8 and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f

                val brightness = (r + g + b) / 3f

                brightnessSum += brightness
                minBrightness = minOf(minBrightness, brightness)
                maxBrightness = maxOf(maxBrightness, brightness)

                if (brightness <= BLANK_FRAME_DARK_PIXEL_THRESHOLD) {
                    veryDarkCount++
                }

                sampleCount++
                x += sampleStepX
            }
            y += sampleStepY
        }

        if (sampleCount <= 0) {
            return false
        }

        val avgBrightness = brightnessSum / sampleCount
        val darkRatio = veryDarkCount.toFloat() / sampleCount.toFloat()
        val brightnessRange = maxBrightness - minBrightness

        val isMostlyBlack =
            avgBrightness <= BLANK_FRAME_AVG_BRIGHTNESS_THRESHOLD &&
                    darkRatio >= BLANK_FRAME_DARK_RATIO_THRESHOLD

        val isAlmostFlatDark =
            avgBrightness <= BLANK_FRAME_FLAT_AVG_BRIGHTNESS_THRESHOLD &&
                    brightnessRange <= BLANK_FRAME_FLAT_RANGE_THRESHOLD

        return isMostlyBlack || isAlmostFlatDark
    }

    private fun pushRecentResult(result: BinaryNsfwResult) {
        recentBinaryResults.add(result)

        if (recentBinaryResults.size > RISK_HISTORY_SIZE) {
            recentBinaryResults.removeAt(0)
        }
    }

    private fun calculateBinaryAverages(): BinaryRiskAverages {
        if (recentBinaryResults.isEmpty()) {
            return BinaryRiskAverages()
        }

        val size = recentBinaryResults.size.toFloat()

        return BinaryRiskAverages(
            avgNsfw = recentBinaryResults.sumOf { it.nsfwScore.toDouble() }.toFloat() / size,
            avgSfw = recentBinaryResults.sumOf { it.sfwScore.toDouble() }.toFloat() / size
        )
    }

    private fun countAdultLikeFrames(): Int {
        return recentBinaryResults.count { result ->
            result.isReady &&
                    result.nsfwScore >= FRAME_NSFW_RISK_THRESHOLD &&
                    result.sfwScore <= FRAME_SFW_MAX_FOR_RISK
        }
    }

    private fun shouldKeepOverlayVisible(now: Long): Boolean {
        if (!isOverlayVisible) {
            return false
        }

        val withinMinVisibleDuration =
            overlayVisibleSinceTimestamp > 0L &&
                    now - overlayVisibleSinceTimestamp < MIN_OVERLAY_VISIBLE_MS

        val withinRiskHoldDuration =
            lastRiskDetectedTimestamp > 0L &&
                    now - lastRiskDetectedTimestamp < OVERLAY_HOLD_AFTER_RISK_MS

        return withinMinVisibleDuration || withinRiskHoldDuration
    }

    private fun showOverlay(now: Long) {
        if (!isOverlayVisible) {
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_SHOW_OVERLAY, true)
            }
            startServiceCompat(intent)
            overlayVisibleSinceTimestamp = now
        }

        isOverlayVisible = true
    }

    private fun hideOverlay(force: Boolean = false) {
        if (!force && !isOverlayVisible) {
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_SHOW_OVERLAY, false)
        }

        startServiceCompat(intent)

        isOverlayVisible = false
        overlayVisibleSinceTimestamp = 0L
        lastRiskDetectedTimestamp = 0L
        blankFrameStartedTimestamp = 0L

        resetDetectionState()
    }

    private fun resetDetectionState() {
        highRiskFrameCount = 0
        clearFrameCount = 0
        recentBinaryResults.clear()
        blankFrameStartedTimestamp = 0L
    }

    private fun startServiceCompat(intent: Intent) {
        startService(intent)
    }

    private fun buildNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateUserNotification(title: String, text: String) {
        if (currentNotificationTitle == title && currentNotificationText == text) {
            return
        }

        currentNotificationTitle = title
        currentNotificationText = text

        notificationManager?.notify(
            NOTIFICATION_ID,
            buildNotification(title, text)
        )
    }

    private fun throttledNotification(title: String, text: String, now: Long) {
        val unchanged = currentNotificationTitle == title && currentNotificationText == text
        val tooSoon = now - lastNotificationTimestamp < NOTIFICATION_THROTTLE_MS

        if (unchanged || tooSoon) {
            return
        }

        lastNotificationTimestamp = now
        updateUserNotification(title, text)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Muhafız koruma bildirimi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muhafız ekran koruma hizmeti bildirimi"
                setShowBadge(false)
            }

            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun keepOrHideOverlayAfterError(now: Long) {
        if (!shouldKeepOverlayVisible(now)) {
            hideOverlay()
        }
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) {
            recycle()
        }
    }

    private data class AnalysisSize(
        val width: Int,
        val height: Int
    )

    private data class BinaryRiskAverages(
        val avgNsfw: Float = 0f,
        val avgSfw: Float = 1f
    )

    private data class BinaryClassifiedFrame(
        val result: BinaryNsfwResult,
        val patchName: String,
        val patchAdultScore: Float,
        val trustedPatch: Boolean
    )

    private data class PatchSpec(
        val name: String,
        val leftRatio: Float,
        val topRatio: Float,
        val rightRatio: Float,
        val bottomRatio: Float
    ) {
        val isFullFrame: Boolean
            get() = leftRatio == 0f &&
                    topRatio == 0f &&
                    rightRatio == 1f &&
                    bottomRatio == 1f
    }

    private enum class RiskLevel {
        NONE,
        SUSTAINED,
        CRITICAL
    }

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MAX_NOTIFICATION_TEXT_LENGTH = 120

        private const val PORTRAIT_ANALYSIS_WIDTH = 360
        private const val LANDSCAPE_ANALYSIS_HEIGHT = 360

        private const val MIN_ANALYSIS_WIDTH = 360
        private const val MAX_ANALYSIS_WIDTH = 960
        private const val MIN_ANALYSIS_HEIGHT = 240
        private const val MAX_ANALYSIS_HEIGHT = 960

        /*
         * Binary model + patch scan eski modele göre biraz daha ağırdır.
         */
        private const val ANALYSIS_INTERVAL_MS = 250L
        private const val DISPLAY_CHECK_INTERVAL_MS = 1000L

        /*
         * Filtre açıldıysa hemen kapanmaz.
         */
        private const val MIN_OVERLAY_VISIBLE_MS = 7000L
        private const val OVERLAY_HOLD_AFTER_RISK_MS = 5000L
        private const val NOTIFICATION_THROTTLE_MS = 1500L

        /*
         * Kesin emin olunca kapatma modu.
         */
        private const val REQUIRED_HIGH_RISK_FRAMES = 3
        private const val REQUIRED_CLEAR_FRAMES = 14

        private const val RISK_HISTORY_SIZE = 10
        private const val MIN_HISTORY_FOR_DECISION = 6

        private const val MIN_ADULT_LIKE_FRAMES_FOR_BLOCK = 5
        private const val MIN_STRONG_ADULT_FRAMES_FOR_BLOCK = 4

        /*
         * Tek frame CRITICAL eşiği.
         */
        private const val NSFW_CRITICAL_THRESHOLD = 0.97f
        private const val SFW_CRITICAL_MAX = 0.08f

        /*
         * Frame geçmişi eşikleri.
         */
        private const val FRAME_NSFW_RISK_THRESHOLD = 0.86f
        private const val FRAME_SFW_MAX_FOR_RISK = 0.24f

        private const val AVG_NSFW_BLOCK_THRESHOLD = 0.86f
        private const val AVG_NSFW_STRONG_BLOCK_THRESHOLD = 0.91f
        private const val AVG_SFW_MAX_FOR_BLOCK = 0.24f

        /*
         * Siyah frame ancak uzun sürerse risk.
         */
        private const val REQUIRED_BLANK_FRAME_DURATION_MS = 10000L

        private const val BLANK_FRAME_SAMPLE_GRID = 12
        private const val BLANK_FRAME_DARK_PIXEL_THRESHOLD = 0.06f
        private const val BLANK_FRAME_AVG_BRIGHTNESS_THRESHOLD = 0.08f
        private const val BLANK_FRAME_DARK_RATIO_THRESHOLD = 0.92f
        private const val BLANK_FRAME_FLAT_AVG_BRIGHTNESS_THRESHOLD = 0.12f
        private const val BLANK_FRAME_FLAT_RANGE_THRESHOLD = 0.04f

        /*
         * Patch scan sadece güvenilir binary NSFW skorunda kabul edilir.
         */
        private const val PATCH_TRUSTED_NSFW_THRESHOLD = 0.88f
        private const val PATCH_MAX_SFW_FOR_TRUSTED_NSFW = 0.22f

        private val PORTRAIT_PATCH_SPECS = listOf(
            PatchSpec("full", 0f, 0f, 1f, 1f),
            PatchSpec("content_middle_large", 0.04f, 0.22f, 0.96f, 0.78f),
            PatchSpec("content_lower_large", 0.04f, 0.34f, 0.96f, 0.92f),
            PatchSpec("center_image_area", 0.08f, 0.30f, 0.92f, 0.74f),
            PatchSpec("lower_image_area", 0.08f, 0.42f, 0.92f, 0.90f),
            PatchSpec("middle_square", 0.14f, 0.25f, 0.86f, 0.76f),
            PatchSpec("lower_square", 0.14f, 0.42f, 0.86f, 0.96f)
        )

        private val LANDSCAPE_PATCH_SPECS = listOf(
            PatchSpec("full", 0f, 0f, 1f, 1f),
            PatchSpec("center_wide", 0.16f, 0.08f, 0.84f, 0.92f),
            PatchSpec("center_large", 0.22f, 0.12f, 0.78f, 0.88f),
            PatchSpec("left_center", 0.02f, 0.12f, 0.52f, 0.88f),
            PatchSpec("right_center", 0.48f, 0.12f, 0.98f, 0.88f),
            PatchSpec("upper_center", 0.18f, 0.02f, 0.82f, 0.58f),
            PatchSpec("lower_center", 0.18f, 0.42f, 0.82f, 0.98f),
            PatchSpec("middle_square", 0.30f, 0.08f, 0.70f, 0.92f)
        )
    }
}