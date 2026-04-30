package com.tunix.nazar.data.local

object AppPrefs {

    /*
     * DataStore dosya adı.
     *
     * Not:
     * Package adını şimdilik değiştirmiyoruz; dışarıdan görünen ürün adı Muhafız.
     * Bu nedenle eski cihazlarda veri kaybı yaşamamak için DataStore adını da
     * mecbur kalmadıkça değiştirmemek daha güvenlidir.
     */
    const val DATASTORE_NAME = "nazar_prefs"

    /*
     * Koruma durumu.
     *
     * true  -> Muhafız koruması açık kabul edilir.
     * false -> Muhafız koruması kapalı kabul edilir.
     */
    const val KEY_IS_PROTECTION_ENABLED = "key_is_protection_enabled"

    /*
     * Algılama eşikleri.
     *
     * LOW:
     * Daha erken tepki vermek için kullanılan düşük risk eşiğidir.
     *
     * HIGH:
     * Daha güçlü / kesin risk durumları için kullanılan yüksek risk eşiğidir.
     *
     * Yeni mimaride blur yoktur; bu eşikler yalnızca ekranın tamamen
     * siyah engelleme ekranıyla kapatılıp kapatılmayacağını belirler.
     */
    const val KEY_LOW_THRESHOLD = "key_low_threshold"
    const val KEY_HIGH_THRESHOLD = "key_high_threshold"

    /*
     * Analiz aralığı.
     *
     * Ekran görüntüsünün modele ne sıklıkla gönderileceğini belirler.
     * Değer milisaniye cinsindendir.
     *
     * Daha düşük değer:
     * - Daha hızlı tepki
     * - Daha fazla işlemci/batarya kullanımı
     *
     * Daha yüksek değer:
     * - Daha düşük yük
     * - Daha geç tepki
     */
    const val KEY_ANALYSIS_INTERVAL_MS = "key_analysis_interval_ms"

    /*
     * Tam ekran engelleme ayarı.
     *
     * Yeni Muhafız mimarisinde riskli içerik algılandığında bölgesel blur değil,
     * tam ekran siyah koruma ekranı gösterilir.
     */
    const val KEY_USE_FULL_SCREEN_BLOCK = "key_use_full_screen_block"

    /*
     * Varsayılan algılama değerleri.
     *
     * Bu değerler müşteri tesliminden önce gerçek cihaz testleriyle kalibre edilebilir.
     * Şimdilik fazla gevşek bırakmadan, yetişkin içerik yakalamaya öncelik veren
     * dengeli bir başlangıç seviyesi sunar.
     */
    const val DEFAULT_LOW_THRESHOLD = 0.12f
    const val DEFAULT_HIGH_THRESHOLD = 0.28f

    /*
     * Varsayılan analiz aralığı.
     *
     * 140 ms yaklaşık saniyede 7 analiz anlamına gelir.
     * Bu, gerçek zamanlı tepki ile cihaz yükü arasında makul bir dengedir.
     */
    const val DEFAULT_ANALYSIS_INTERVAL_MS = 140L

    /*
     * Muhafız artık bölgesel sansür uygulamaz.
     * Risk varsa tüm ekranı siyah koruma ekranıyla kapatır.
     */
    const val DEFAULT_USE_FULL_SCREEN_BLOCK = true
}