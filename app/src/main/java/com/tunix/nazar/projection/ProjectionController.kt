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
    private val context: Context
) {
    /*
     * Android'in ekran yakalama izni ve MediaProjection oturumlarını yöneten sistem servisi.
     * Kullanıcıdan ekran yakalama izni almak için bu manager üzerinden intent oluşturulur.
     */
    private val projectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    /*
     * MediaProjection callback'leri ana thread üzerinde kayıtlanır.
     * Bu, Android yaşam döngüsüyle daha güvenli çalışır.
     */
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    /*
     * stopProjection aynı anda birden fazla kez çağrılırsa çift release/stop riskini engeller.
     */
    private val isStopping = AtomicBoolean(false)

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            /*
             * Bu callback sistem tarafından tetiklenebilir:
             * - Kullanıcı ekran yakalama iznini sonlandırırsa
             * - Sistem MediaProjection oturumunu kapatırsa
             * - Servis lifecycle dışından durdurulursa
             *
             * Bu yüzden sadece VirtualDisplay temizlenir ve state sıfırlanır.
             */
            releaseVirtualDisplayOnly()
            mediaProjection = null
            isStopping.set(false)
        }
    }

    fun createScreenCaptureIntent(): Intent {
        /*
         * Bu intent MainActivity tarafından launch edilir.
         * Kullanıcı izin verirse resultCode + data ScreenCaptureService'e gönderilir.
         */
        return projectionManager.createScreenCaptureIntent()
    }

    fun startProjection(resultCode: Int, data: Intent): MediaProjection {
        /*
         * Yeni bir projection başlatmadan önce eski oturum varsa kapatılır.
         * Böylece aynı anda iki VirtualDisplay/MediaProjection çakışması önlenir.
         */
        stopProjection()

        val projection = try {
            projectionManager.getMediaProjection(resultCode, data)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Muhafız ekran yakalama izni başlatılamadı: ${e.message ?: e.javaClass.simpleName}",
                e
            )
        } ?: throw IllegalStateException("Muhafız ekran yakalama oturumu oluşturulamadı.")

        try {
            projection.registerCallback(projectionCallback, mainHandler)
        } catch (e: Exception) {
            /*
             * Callback kaydı başarısızsa projection açık bırakılmamalı.
             * Aksi halde sistem kaynağı sızıntısı oluşabilir.
             */
            try {
                projection.stop()
            } catch (_: Exception) {
            }

            throw IllegalStateException(
                "Muhafız ekran yakalama callback kaydı başarısız: ${e.message ?: e.javaClass.simpleName}",
                e
            )
        }

        mediaProjection = projection
        isStopping.set(false)
        return projection
    }

    fun createVirtualDisplay(
        name: String,
        width: Int,
        height: Int,
        densityDpi: Int,
        surface: Surface
    ): VirtualDisplay {
        /*
         * VirtualDisplay için geçersiz boyutlar Android tarafında crash veya
         * boş frame üretimi gibi sorunlara sebep olabilir.
         */
        require(name.isNotBlank()) { "VirtualDisplay adı boş olamaz." }
        require(width > 0) { "VirtualDisplay genişliği 0'dan büyük olmalıdır." }
        require(height > 0) { "VirtualDisplay yüksekliği 0'dan büyük olmalıdır." }
        require(densityDpi > 0) { "VirtualDisplay densityDpi 0'dan büyük olmalıdır." }

        val projection = mediaProjection
            ?: throw IllegalStateException("Ekran yakalama oturumu başlatılmadan VirtualDisplay oluşturulamaz.")

        /*
         * Aynı projection altında yeni display açmadan önce eskisi kapatılır.
         * Bu, servis yeniden başlatıldığında eski Surface'e frame akmasını engeller.
         */
        releaseVirtualDisplayOnly()

        val display = try {
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
                "VirtualDisplay oluşturulamadı: ${e.message ?: e.javaClass.simpleName}",
                e
            )
        } ?: throw IllegalStateException("VirtualDisplay oluşturulamadı.")

        virtualDisplay = display
        return display
    }

    fun stopProjection() {
        if (!isStopping.compareAndSet(false, true)) {
            return
        }

        try {
            releaseVirtualDisplayOnly()

            val projection = mediaProjection
            mediaProjection = null

            if (projection != null) {
                try {
                    projection.unregisterCallback(projectionCallback)
                } catch (_: Exception) {
                    /*
                     * Callback daha önce sistem tarafından kaldırılmış olabilir.
                     * Bu durum kapanışı engellememeli.
                     */
                }

                try {
                    projection.stop()
                } catch (_: Exception) {
                    /*
                     * Projection zaten durmuş olabilir.
                     */
                }
            }
        } finally {
            isStopping.set(false)
        }
    }

    fun isProjectionRunning(): Boolean {
        return mediaProjection != null
    }

    private fun releaseVirtualDisplayOnly() {
        val display = virtualDisplay
        virtualDisplay = null

        try {
            /*
             * Surface burada release edilmez.
             * Surface lifecycle ImageFrameReader tarafından yönetilir.
             */
            display?.release()
        } catch (_: Exception) {
            /*
             * Display zaten kapanmış olabilir.
             */
        }
    }
}