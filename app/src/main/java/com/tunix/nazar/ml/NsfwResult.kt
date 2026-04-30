package com.tunix.nazar.ml

data class NsfwResult(

    /*
     * Genel risk skoru.
     *
     * 0.0 -> güvenli / risk yok
     * 1.0 -> çok yüksek risk
     *
     * Bu değer NsfwInterpreter içinde porn, hentai, sexy, neutral ve drawings
     * skorları birlikte değerlendirilerek hesaplanır.
     */
    val score: Float = 0f,

    /*
     * İçeriğin engellenip engellenmeyeceğini belirtir.
     *
     * true olduğunda ScreenCaptureService, OverlayService üzerinden
     * tam ekran siyah Muhafız koruma ekranını açar.
     */
    val shouldBlock: Boolean = false,

    /*
     * Riskin tam ekran koruma gerektirip gerektirmediğini belirtir.
     *
     * Yeni Muhafız mimarisinde temel davranış zaten tam ekran siyah korumadır.
     * Bu alan ileride farklı koruma seviyeleri eklenirse karar vermek için
     * kullanılabilir.
     */
    val useFullScreenBlock: Boolean = false,

    /*
     * Modelin en yüksek güvenle tahmin ettiği sınıf.
     *
     * Beklenen sınıflar:
     * drawings, hentai, neutral, porn, sexy
     */
    val dominantLabel: String = "unknown",

    /*
     * Modelin sınıf bazlı skorları.
     *
     * Bu değerler 0.0 - 1.0 aralığında tutulur.
     * Karar verme, loglama veya ebeveyn raporu gibi alanlarda kullanılabilir.
     */
    val pornScore: Float = 0f,
    val hentaiScore: Float = 0f,
    val sexyScore: Float = 0f,
    val neutralScore: Float = 0f,
    val drawingsScore: Float = 0f
) {

    /*
     * UI veya servis katmanında daha okunabilir kontrol için yardımcı alan.
     */
    val isRisky: Boolean
        get() = shouldBlock

    /*
     * Tam ekran koruma gerekip gerekmediğini okunabilir şekilde döndürür.
     *
     * Şu an shouldBlock true ise pratikte tam ekran siyah koruma uygulanır.
     */
    val requiresFullScreenProtection: Boolean
        get() = shouldBlock || useFullScreenBlock
}