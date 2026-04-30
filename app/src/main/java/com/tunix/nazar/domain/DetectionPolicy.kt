package com.tunix.nazar.domain

data class DetectionPolicy(

    /*
     * Düşük risk eşiği.
     *
     * Modelin ürettiği risk skoru bu değeri geçtiğinde içerik şüpheli kabul edilir.
     * Muhafız tarafında bu eşik, hızlı tepki vermek için kullanılır.
     */
    val lowThreshold: Float = 0.12f,

    /*
     * Yüksek risk eşiği.
     *
     * Modelin ürettiği risk skoru bu değeri geçtiğinde içerik güçlü risk olarak kabul edilir.
     * Yeni mimaride güçlü risk durumunda da bölgesel blur değil, tam ekran siyah koruma uygulanır.
     */
    val highThreshold: Float = 0.28f,

    /*
     * Art arda kaç riskli frame görüldüğünde engellemenin aktif olacağını belirler.
     *
     * 1 değeri daha hızlı tepki verir.
     * Daha yüksek değerler false positive ihtimalini azaltabilir ama tepkiyi geciktirir.
     */
    val requiredHighFrames: Int = 1,

    /*
     * Art arda kaç temiz frame görüldüğünde engellemenin kaldırılacağını belirler.
     *
     * 3 değeri, ekranın anlık dalgalanmalarda sürekli açılıp kapanmasını azaltır.
     */
    val requiredClearFrames: Int = 3,

    /*
     * Saniyede hedeflenen analiz sayısı.
     *
     * 7 FPS civarı, mobil cihazlarda gerçek zamanlı tepki ile performans arasında
     * makul bir denge sunar.
     */
    val analysisFps: Int = 7,

    /*
     * Modele gönderilecek giriş çözünürlüğü.
     *
     * TFLite modelinin beklediği giriş genellikle 224x224 olduğu için burada da
     * varsayılanı 224 tuttuk. Daha büyük değerler her zaman daha iyi sonuç vermez;
     * çoğu model zaten sabit giriş boyutu bekler.
     */
    val inputWidth: Int = 224,
    val inputHeight: Int = 224,

    /*
     * Muhafız'ın ana davranışı.
     *
     * true olduğunda riskli içerik algılandığında ekran tamamen siyah koruma ekranıyla kapatılır.
     * Yeni ürün yönünde varsayılan ve önerilen davranış budur.
     */
    val useFullScreenBlockOnRisk: Boolean = true
) {
    /*
     * Analiz FPS değerinden milisaniye cinsinden frame aralığı üretir.
     *
     * En düşük değer 1 FPS'e sabitlenir; böylece sıfıra bölme veya geçersiz
     * zamanlama hatası oluşmaz.
     */
    val analysisIntervalMs: Long
        get() = 1000L / analysisFps.coerceAtLeast(1)
}