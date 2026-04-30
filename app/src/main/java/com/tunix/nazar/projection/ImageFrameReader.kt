package com.tunix.nazar.projection

import android.graphics.Bitmap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class ImageFrameReader(
    private val width: Int,
    private val height: Int
) {

    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    /*
     * Reader serbest bırakıldıktan sonra yeni frame işlenmesini engeller.
     * MediaProjection kapanırken hâlâ callback gelebileceği için AtomicBoolean kullanıyoruz.
     */
    private val isReleased = AtomicBoolean(false)

    /*
     * Aynı anda birden fazla frame işlenmesini engeller.
     * Model zaten tek tek çalışacağı için üst üste frame biriktirmek performansı bozar.
     */
    private val isFrameDispatching = AtomicBoolean(false)

    fun initialize(onFrameAvailable: (Bitmap) -> Unit) {
        require(width > 0) { "ImageFrameReader width 0'dan büyük olmalıdır." }
        require(height > 0) { "ImageFrameReader height 0'dan büyük olmalıdır." }

        /*
         * Aynı nesne yeniden initialize edilirse eski ImageReader ve thread temizlenir.
         * Bu, izin yenileme veya servis yeniden başlatma senaryolarında sızıntıyı önler.
         */
        release()

        isReleased.set(false)
        isFrameDispatching.set(false)

        handlerThread = HandlerThread(THREAD_NAME).also { it.start() }

        val localThread = handlerThread
            ?: throw IllegalStateException("ImageFrameReader thread başlatılamadı.")

        val localHandler = Handler(localThread.looper)
        handler = localHandler

        imageReader = ImageReader.newInstance(
            width,
            height,
            android.graphics.PixelFormat.RGBA_8888,
            MAX_IMAGES
        ).apply {
            setOnImageAvailableListener({ reader ->
                if (isReleased.get()) {
                    closeLatestImage(reader)
                    return@setOnImageAvailableListener
                }

                /*
                 * Bir frame işlenirken yeni frame gelirse son frame kapatılır.
                 * Bu sayede gecikmiş frame kuyruğu oluşmaz; uygulama canlı görüntüye yakın kalır.
                 */
                if (!isFrameDispatching.compareAndSet(false, true)) {
                    closeLatestImage(reader)
                    return@setOnImageAvailableListener
                }

                var image: Image? = null

                try {
                    image = reader.acquireLatestImage()
                    if (image == null || isReleased.get()) {
                        return@setOnImageAvailableListener
                    }

                    val bitmap = image.toBitmapFast(width, height)
                    onFrameAvailable(bitmap)
                } catch (_: Exception) {
                    /*
                     * Frame dönüştürme hatası uygulamayı düşürmemeli.
                     * Bir sonraki frame ile analiz devam eder.
                     */
                } finally {
                    try {
                        image?.close()
                    } catch (_: Exception) {
                    }

                    isFrameDispatching.set(false)
                }
            }, localHandler)
        }
    }

    fun getSurface(): Surface? {
        return imageReader?.surface
    }

    fun release() {
        isReleased.set(true)
        isFrameDispatching.set(false)

        val reader = imageReader
        imageReader = null

        try {
            reader?.setOnImageAvailableListener(null, null)
        } catch (_: Exception) {
        }

        try {
            reader?.close()
        } catch (_: Exception) {
        }

        val thread = handlerThread
        handlerThread = null
        handler = null

        if (thread != null) {
            try {
                thread.quitSafely()
                thread.join(THREAD_JOIN_TIMEOUT_MS)
            } catch (_: Exception) {
                /*
                 * Thread join başarısız olsa bile servis kapanışı engellenmemeli.
                 * Android süreç yaşam döngüsü kalan temizliği kendi yönetecektir.
                 */
            }
        }
    }

    private fun closeLatestImage(reader: ImageReader) {
        try {
            reader.acquireLatestImage()?.close()
        } catch (_: Exception) {
            // Reader kapanmış olabilir.
        }
    }

    private fun Image.toBitmapFast(targetWidth: Int, targetHeight: Int): Bitmap {
        val plane = planes.firstOrNull()
            ?: throw IllegalStateException("Image frame plane bulunamadı.")

        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        val bitmap = Bitmap.createBitmap(
            targetWidth,
            targetHeight,
            Bitmap.Config.ARGB_8888
        )

        /*
         * En hızlı yol:
         * Satırda padding yoksa buffer doğrudan bitmap'e kopyalanır.
         */
        if (pixelStride == BYTES_PER_PIXEL && rowStride == targetWidth * BYTES_PER_PIXEL) {
            buffer.rewind()
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        }

        /*
         * Bazı cihazlarda ImageReader satır sonuna padding ekler.
         * Bu durumda buffer'ı satır satır okuyup padding kısmını bitmap'e taşımıyoruz.
         */
        val rowBytes = targetWidth * BYTES_PER_PIXEL
        val rowBuffer = ByteArray(rowBytes)
        val bitmapBuffer = ByteBuffer.allocate(targetHeight * rowBytes)

        buffer.rewind()

        for (row in 0 until targetHeight) {
            val rowStart = row * rowStride
            if (rowStart >= buffer.limit()) break

            rowBuffer.fill(0)

            buffer.position(rowStart)

            /*
             * readable değeri buffer sonuna yaklaşırken taşmayı engeller.
             * Eksik kalan kısımlar rowBuffer içinde 0 olarak kalır.
             */
            val readable = minOf(rowBytes, buffer.remaining())
            buffer.get(rowBuffer, 0, readable)
            bitmapBuffer.put(rowBuffer)
        }

        bitmapBuffer.rewind()
        bitmap.copyPixelsFromBuffer(bitmapBuffer)
        return bitmap
    }

    companion object {
        private const val THREAD_NAME = "MuhafizImageReaderThread"

        /*
         * MAX_IMAGES = 2:
         * Bir frame işlenirken en güncel frame'i alabilmek için yeterli,
         * gereksiz bellek kullanımını da düşük tutar.
         */
        private const val MAX_IMAGES = 2

        /*
         * RGBA_8888 formatında her piksel 4 byte kabul edilir.
         */
        private const val BYTES_PER_PIXEL = 4

        private const val THREAD_JOIN_TIMEOUT_MS = 500L
    }
}