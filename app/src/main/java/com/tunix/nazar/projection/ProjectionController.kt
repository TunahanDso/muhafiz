package com.tunix.nazar.projection

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

class ProjectionController(
    context: Context,
    private val onProjectionStoppedBySystem: () -> Unit = {}
) {
    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private val mainHandler =
        Handler(Looper.getMainLooper())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    /*
     * Explicit stop ile sistem kaynaklı stop'u birbirinden ayırır.
     * Explicit stop'ta callback önce unregister edildiği için servis
     * yanlışlıkla ikinci kez "projection sonlandı" akışına girmez.
     */
    private val isStopping =
        AtomicBoolean(false)

    private val projectionCallback =
        object : MediaProjection.Callback() {

            override fun onStop() {
                /*
                 * Android 15 QPR1+ ekran kilidinde veya kullanıcı
                 * sistemdeki projection chip'inden paylaşımı durdurduğunda
                 * bu callback gelir. Bu durumda capture session artık
                 * geçerli değildir ve aynı izin Intent'i yeniden kullanılamaz.
                 */
                releaseVirtualDisplayOnly()
                mediaProjection = null

                val stoppedExplicitly =
                    isStopping.getAndSet(false)

                if (!stoppedExplicitly) {
                    onProjectionStoppedBySystem()
                }
            }
        }

    fun createScreenCaptureIntent(): Intent {
        return projectionManager.createScreenCaptureIntent()
    }

    fun startProjection(
        resultCode: Int,
        data: Intent
    ): MediaProjection {

        stopProjection()

        val projection =
            try {
                projectionManager.getMediaProjection(
                    resultCode,
                    data
                )
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Muhafız ekran yakalama izni başlatılamadı: " +
                            (e.message ?: e.javaClass.simpleName),
                    e
                )
            } ?: throw IllegalStateException(
                "Muhafız ekran yakalama oturumu oluşturulamadı."
            )

        try {
            projection.registerCallback(
                projectionCallback,
                mainHandler
            )
        } catch (e: Exception) {
            try {
                projection.stop()
            } catch (_: Exception) {
            }

            throw IllegalStateException(
                "Muhafız ekran yakalama callback kaydı başarısız: " +
                        (e.message ?: e.javaClass.simpleName),
                e
            )
        }

        mediaProjection = projection
        isStopping.set(false)

        return projection
    }

    /**
     * Bir MediaProjection instance'ında createVirtualDisplay yalnızca
     * bir kez çağrılır. Android 14+ bu kuralı zorunlu tutar.
     */
    fun createVirtualDisplay(
        name: String,
        width: Int,
        height: Int,
        densityDpi: Int,
        surface: Surface
    ): VirtualDisplay {

        require(name.isNotBlank()) {
            "VirtualDisplay adı boş olamaz."
        }
        require(width > 0) {
            "VirtualDisplay genişliği 0'dan büyük olmalıdır."
        }
        require(height > 0) {
            "VirtualDisplay yüksekliği 0'dan büyük olmalıdır."
        }
        require(densityDpi > 0) {
            "VirtualDisplay densityDpi 0'dan büyük olmalıdır."
        }

        val projection =
            mediaProjection
                ?: throw IllegalStateException(
                    "Ekran yakalama oturumu başlatılmadan VirtualDisplay oluşturulamaz."
                )

        check(virtualDisplay == null) {
            "Aynı MediaProjection oturumunda ikinci VirtualDisplay oluşturulamaz."
        }

        val display =
            try {
                projection.createVirtualDisplay(
                    name,
                    width,
                    height,
                    densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null
                )
            } catch (e: Exception) {
                throw IllegalStateException(
                    "VirtualDisplay oluşturulamadı: " +
                            (e.message ?: e.javaClass.simpleName),
                    e
                )
            } ?: throw IllegalStateException(
                "VirtualDisplay oluşturulamadı."
            )

        virtualDisplay = display

        return display
    }

    /**
     * Ekran yönü/boyutu değiştiğinde Android 14+ için yeni
     * createVirtualDisplay çağrısı yapmak yerine mevcut display'i
     * resize eder ve yeni ImageReader Surface'ine bağlar.
     */
    fun resizeVirtualDisplay(
        width: Int,
        height: Int,
        densityDpi: Int,
        surface: Surface
    ) {
        require(width > 0)
        require(height > 0)
        require(densityDpi > 0)

        if (mediaProjection == null) {
            throw IllegalStateException(
                "MediaProjection artık aktif değil."
            )
        }

        val display =
            virtualDisplay
                ?: throw IllegalStateException(
                    "Yeniden boyutlandırılacak VirtualDisplay bulunamadı."
                )

        try {
            display.resize(
                width,
                height,
                densityDpi
            )

            display.setSurface(
                surface
            )
        } catch (e: Exception) {
            throw IllegalStateException(
                "VirtualDisplay yeniden yapılandırılamadı: " +
                        (e.message ?: e.javaClass.simpleName),
                e
            )
        }
    }

    fun stopProjection() {
        if (!isStopping.compareAndSet(false, true)) {
            return
        }

        try {
            releaseVirtualDisplayOnly()

            val projection =
                mediaProjection

            mediaProjection = null

            if (projection != null) {
                try {
                    projection.unregisterCallback(
                        projectionCallback
                    )
                } catch (_: Exception) {
                }

                try {
                    projection.stop()
                } catch (_: Exception) {
                }
            }
        } finally {
            isStopping.set(false)
        }
    }

    fun isProjectionRunning(): Boolean {
        return mediaProjection != null &&
                virtualDisplay != null
    }

    private fun releaseVirtualDisplayOnly() {
        val display =
            virtualDisplay

        virtualDisplay = null

        try {
            display?.release()
        } catch (_: Exception) {
        }
    }
}
