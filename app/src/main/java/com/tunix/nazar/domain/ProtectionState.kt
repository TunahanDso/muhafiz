package com.tunix.nazar.domain

data class ProtectionState(

    /*
     * Muhafız koruma servisinin çalışıp çalışmadığını belirtir.
     *
     * true  -> Ekran yakalama ve içerik analizi aktif.
     * false -> Koruma servisi çalışmıyor.
     */
    val isProtectionRunning: Boolean = false,

    /*
     * Tam ekran siyah engelleme ekranının aktif olup olmadığını belirtir.
     *
     * Eski mimaride bu alan blur durumunu temsil ediyordu.
     * Yeni Muhafız mimarisinde riskli içerik algılandığında ekran tamamen siyah
     * koruma ekranıyla kapatıldığı için bu alan block durumunu temsil eder.
     */
    val isBlockScreenActive: Boolean = false,

    /*
     * Modelin en son ürettiği genel risk skoru.
     *
     * Bu değer UI, loglama veya ebeveyn bilgilendirme ekranlarında kullanılabilir.
     */
    val lastDetectionScore: Float = 0f,

    /*
     * Ebeveyn kilidinin aktif olup olmadığını belirtir.
     *
     * true olduğunda ayarlar, korumayı durdurma veya PIN sıfırlama gibi işlemler
     * ebeveyn doğrulaması gerektirmelidir.
     */
    val isParentLocked: Boolean = true,

    /*
     * Android overlay izninin verilip verilmediğini belirtir.
     *
     * Bu izin olmadan Muhafız tam ekran siyah koruma ekranını diğer uygulamaların
     * üstünde gösteremez.
     */
    val isOverlayPermissionGranted: Boolean = false,

    /*
     * MediaProjection ekran yakalama izninin alınıp alınmadığını belirtir.
     *
     * Bu izin olmadan ekran içeriği analiz edilemez.
     */
    val isProjectionPermissionGranted: Boolean = false
)