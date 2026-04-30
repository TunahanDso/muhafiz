package com.tunix.nazar.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.util.Locale
import kotlin.math.max

class NsfwInterpreter(
    private val context: Context,
    private val modelFileName: String = "nsfw.tflite",
    private val labelsFileName: String = "class_labels.txt",
    private val inputWidth: Int = 224,
    private val inputHeight: Int = 224,

    /*
     * Muhafız'ın ana hedefi:
     * Sadece belirgin pornografik / +18 içerikleri engellemek.
     *
     * Futbol maçı, spor videosu, YouTube Shorts, dans, yüz/gövde içeren normal içerikler
     * mümkün olduğunca engellenmemelidir.
     */
    private val lowThreshold: Float = 0.22f,
    private val highThreshold: Float = 0.42f
) {

    private var interpreter: Interpreter? = null
    private var isModelLoaded: Boolean = false
    private var labels: List<String> = emptyList()

    private var inputBuffer: ByteBuffer? = null
    private var outputBuffer: Array<FloatArray>? = null
    private var pixelBuffer: IntArray? = null

    @Volatile
    var lastErrorMessage: String? = null
        private set

    @Synchronized
    fun loadModel(): Boolean {
        close()
        lastErrorMessage = null

        return try {
            // Model dosyası assets klasöründen memory-mapped olarak yüklenir.
            val modelBuffer = loadModelFile(modelFileName)

            // CPU inference için dengeli thread sayısı.
            val options = Interpreter.Options().apply {
                setNumThreads(INFERENCE_THREAD_COUNT)
            }

            interpreter = Interpreter(modelBuffer, options)
            labels = loadLabels(labelsFileName)

            // Model RGB float input beklediği için native order direct buffer kullanılır.
            inputBuffer = ByteBuffer.allocateDirect(
                FLOAT_SIZE_BYTES * inputWidth * inputHeight * RGB_CHANNEL_COUNT
            ).apply {
                order(ByteOrder.nativeOrder())
            }

            // Beklenen label sırası:
            // drawings, hentai, neutral, porn, sexy
            outputBuffer = Array(1) { FloatArray(EXPECTED_CLASS_COUNT) }
            pixelBuffer = IntArray(inputWidth * inputHeight)

            isModelLoaded = true
            true
        } catch (e: Exception) {
            isModelLoaded = false
            lastErrorMessage = "${e.javaClass.simpleName}: ${e.message ?: "Model yüklenemedi"}"
            false
        }
    }

    fun isReady(): Boolean {
        return isModelLoaded && interpreter != null
    }

    @Synchronized
    fun classify(bitmap: Bitmap): NsfwResult {
        val currentInterpreter = interpreter ?: return emptyResult()
        val currentInputBuffer = inputBuffer ?: return emptyResult()
        val currentOutputBuffer = outputBuffer ?: return emptyResult()

        // Recycle edilmiş bitmap üzerinde işlem yapmak crash sebebidir.
        if (bitmap.isRecycled) {
            return emptyResult()
        }

        return try {
            preprocess(bitmap, currentInputBuffer)

            // Önceki inference sonucu buffer içinde kalmasın.
            currentOutputBuffer[0].fill(0f)

            currentInterpreter.run(currentInputBuffer, currentOutputBuffer)

            val rawScores = currentOutputBuffer[0]
            val classScores = buildClassScoreMap(rawScores)

            val pornScore = classScores[LABEL_PORN] ?: 0f
            val hentaiScore = classScores[LABEL_HENTAI] ?: 0f
            val sexyScore = classScores[LABEL_SEXY] ?: 0f
            val neutralScore = classScores[LABEL_NEUTRAL] ?: 0f
            val drawingsScore = classScores[LABEL_DRAWINGS] ?: 0f

            /*
             * Genel risk hesabı.
             *
             * Porn ve hentai doğrudan daha güçlü risk sayılır.
             * Sexy skoru tek başına güvenilir karar değildir; spor, dans, kısa video,
             * reklam ve normal insan görüntülerinde hatalı yükselebilir.
             */
            val sexualContentScore = maxOf(
                pornScore,
                hentaiScore,
                sexyScore
            ).coerceIn(0f, 1f)

            val weightedRiskScore = (
                    pornScore * PORN_WEIGHT +
                            hentaiScore * HENTAI_WEIGHT +
                            sexyScore * SEXY_WEIGHT
                    ).coerceIn(0f, 1f)

            val confidenceAdjustedRisk = max(
                sexualContentScore,
                (
                        weightedRiskScore -
                                neutralScore * NEUTRAL_SUPPRESSION_WEIGHT -
                                drawingsScore * DRAWINGS_SUPPRESSION_WEIGHT
                        ).coerceIn(0f, 1f)
            ).coerceIn(0f, 1f)

            /*
             * Ana engelleme kararı.
             *
             * Yeni denge:
             * - Porn skoru anlamlıysa engelle.
             * - Hentai skoru anlamlıysa engelle.
             * - Sexy tek başına ancak çok yüksekse engelle.
             * - Genel risk ancak yüksek eşiği geçerse engelle.
             * - Porn + sexy birlikte anlamlıysa engelle.
             *
             * Bu yapı futbol/shorts/spor videolarındaki yanlış aç-kapa davranışını azaltır.
             */
            val shouldBlock =
                pornScore >= lowThreshold ||
                        hentaiScore >= lowThreshold ||
                        sexyScore >= SEXY_STRONG_BLOCK_THRESHOLD ||
                        confidenceAdjustedRisk >= highThreshold ||
                        isPornSexyComboRisky(
                            pornScore = pornScore,
                            sexyScore = sexyScore
                        )

            /*
             * Muhafız'ın davranışı tam ekran siyah engellemedir.
             * Bu alan daha yüksek güvenli risklerde true olur.
             */
            val useFullScreenBlock =
                pornScore >= highThreshold ||
                        hentaiScore >= highThreshold ||
                        sexyScore >= SEXY_FULLSCREEN_THRESHOLD ||
                        confidenceAdjustedRisk >= highThreshold ||
                        isPornSexyComboRisky(
                            pornScore = pornScore,
                            sexyScore = sexyScore
                        )

            val dominantLabel = classScores.maxByOrNull { it.value }?.key ?: LABEL_UNKNOWN

            NsfwResult(
                score = confidenceAdjustedRisk,
                shouldBlock = shouldBlock,
                useFullScreenBlock = useFullScreenBlock,
                dominantLabel = dominantLabel,
                pornScore = pornScore,
                hentaiScore = hentaiScore,
                sexyScore = sexyScore,
                neutralScore = neutralScore,
                drawingsScore = drawingsScore
            )
        } catch (e: Exception) {
            lastErrorMessage = "${e.javaClass.simpleName}: ${e.message ?: "Sınıflandırma hatası"}"
            emptyResult()
        }
    }

    @Synchronized
    fun close() {
        try {
            interpreter?.close()
        } catch (_: Exception) {
            // Interpreter zaten kapanmış olabilir.
        }

        interpreter = null
        labels = emptyList()
        inputBuffer = null
        outputBuffer = null
        pixelBuffer = null
        isModelLoaded = false
        lastErrorMessage = null
    }

    private fun preprocess(bitmap: Bitmap, targetBuffer: ByteBuffer) {
        /*
         * Model sabit giriş boyutu beklediği için ekran görüntüsünü 224x224 boyutuna indiriyoruz.
         * Bitmap zaten doğru boyuttaysa gereksiz kopya üretmiyoruz.
         */
        val resizedBitmap =
            if (bitmap.width == inputWidth && bitmap.height == inputHeight) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
            }

        try {
            val pixels = pixelBuffer ?: IntArray(inputWidth * inputHeight).also {
                pixelBuffer = it
            }

            resizedBitmap.getPixels(
                pixels,
                0,
                inputWidth,
                0,
                0,
                inputWidth,
                inputHeight
            )

            targetBuffer.rewind()

            var pixelIndex = 0
            while (pixelIndex < pixels.size) {
                val pixel = pixels[pixelIndex]

                // ARGB int içinden RGB kanalları çıkarılır ve 0..1 aralığına normalize edilir.
                val r = (pixel shr 16 and 0xFF) / 255f
                val g = (pixel shr 8 and 0xFF) / 255f
                val b = (pixel and 0xFF) / 255f

                targetBuffer.putFloat(r)
                targetBuffer.putFloat(g)
                targetBuffer.putFloat(b)

                pixelIndex++
            }

            // TFLite run çağrısı buffer'ı baştan okuyabilsin diye pozisyon sıfırlanır.
            targetBuffer.rewind()
        } finally {
            // Bu method içinde oluşturulan geçici bitmap temizlenir.
            // Dışarıdan gelen orijinal bitmap'in lifecycle'ı ScreenCaptureService'e aittir.
            if (resizedBitmap !== bitmap && !resizedBitmap.isRecycled) {
                resizedBitmap.recycle()
            }
        }
    }

    private fun isPornSexyComboRisky(
        pornScore: Float,
        sexyScore: Float
    ): Boolean {
        /*
         * Sexy tek başına güvenilir değildir.
         *
         * Ancak porn skoru da destek veriyorsa ve sexy belirgin seviyedeyse
         * +18 riskini kabul ediyoruz.
         */
        return pornScore >= PORN_SEXY_COMBO_PORN_THRESHOLD &&
                sexyScore >= PORN_SEXY_COMBO_SEXY_THRESHOLD
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        return FileUtil.loadMappedFile(context, fileName)
    }

    private fun loadLabels(fileName: String): List<String> {
        return try {
            FileUtil.loadLabels(context, fileName)
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            // Label dosyası okunamazsa bilinen model sırasına düşülür.
            DEFAULT_LABELS
        }
    }

    private fun buildClassScoreMap(rawScores: FloatArray): Map<String, Float> {
        /*
         * Label sayısı model çıktısıyla uyuşmazsa güvenli varsayılan sıraya geçilir.
         */
        val safeLabels = if (labels.size == rawScores.size) {
            labels
        } else {
            DEFAULT_LABELS
        }

        return safeLabels.mapIndexed { index, label ->
            label to rawScores.getOrElse(index) { 0f }.coerceIn(0f, 1f)
        }.toMap()
    }

    private fun emptyResult(): NsfwResult {
        return NsfwResult(
            score = 0f,
            shouldBlock = false,
            useFullScreenBlock = false,
            dominantLabel = LABEL_UNKNOWN,
            pornScore = 0f,
            hentaiScore = 0f,
            sexyScore = 0f,
            neutralScore = 0f,
            drawingsScore = 0f
        )
    }

    companion object {
        private const val INFERENCE_THREAD_COUNT = 4
        private const val FLOAT_SIZE_BYTES = 4
        private const val RGB_CHANNEL_COUNT = 3
        private const val EXPECTED_CLASS_COUNT = 5

        private const val LABEL_DRAWINGS = "drawings"
        private const val LABEL_HENTAI = "hentai"
        private const val LABEL_NEUTRAL = "neutral"
        private const val LABEL_PORN = "porn"
        private const val LABEL_SEXY = "sexy"
        private const val LABEL_UNKNOWN = "unknown"

        private val DEFAULT_LABELS = listOf(
            LABEL_DRAWINGS,
            LABEL_HENTAI,
            LABEL_NEUTRAL,
            LABEL_PORN,
            LABEL_SEXY
        )

        /*
         * Risk ağırlıkları.
         *
         * Sexy ağırlığı düşürüldü.
         * Çünkü futbol/spor/shorts gibi normal içeriklerde sexy skoru hatalı yükselebiliyor.
         */
        private const val PORN_WEIGHT = 1.00f
        private const val HENTAI_WEIGHT = 0.95f
        private const val SEXY_WEIGHT = 0.40f

        /*
         * Neutral ve drawings skorları false positive'i bastırır.
         */
        private const val NEUTRAL_SUPPRESSION_WEIGHT = 0.25f
        private const val DRAWINGS_SUPPRESSION_WEIGHT = 0.12f

        /*
         * Sexy tek başına artık ancak çok yüksekse bloklatır.
         *
         * Bu değer özellikle futbol / spor / shorts false positive'lerini azaltmak için yükseltildi.
         */
        private const val SEXY_STRONG_BLOCK_THRESHOLD = 0.75f
        private const val SEXY_FULLSCREEN_THRESHOLD = 0.85f

        /*
         * Porn + sexy kombinasyonu.
         *
         * Porn tarafında destek sinyali varsa ve sexy de belirginse engelle.
         * Böylece sadece "insan var / ten rengi var" diye ekran kapatma azalır.
         */
        private const val PORN_SEXY_COMBO_PORN_THRESHOLD = 0.18f
        private const val PORN_SEXY_COMBO_SEXY_THRESHOLD = 0.45f
    }
}