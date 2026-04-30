package com.tunix.nazar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Muhafız tipografi ayarları.
 *
 * Amaç:
 * - Başlıklarda net ve güven veren bir görünüm
 * - Açıklama metinlerinde okunabilirlik
 * - Butonlarda sade ve anlaşılır metin ağırlığı
 *
 * FontFamily.Default bilinçli olarak korunmuştur.
 * Özel font eklenirse ileride burada merkezi olarak değiştirilebilir.
 */
val Typography = Typography(

    /*
     * Ana ürün başlığı.
     * Örnek kullanım: "Muhafız"
     */
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),

    /*
     * İkincil ekran başlıkları.
     * Örnek kullanım: "Muhafız Ayarları", "Ebeveyn PIN Kurulumu"
     */
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),

    /*
     * Kart içi bölüm başlıkları.
     * Örnek kullanım: "Koruma bilgisi", "Ebeveyn kilidi"
     */
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),

    /*
     * Ana açıklama metinleri.
     * Kullanıcıya ürünün ne yaptığını anlatan metinlerde kullanılır.
     */
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp
    ),

    /*
     * Yardımcı açıklama metinleri.
     * Daha kısa bilgilendirme ve notlarda kullanılır.
     */
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.2.sp
    ),

    /*
     * Buton ve küçük etiket metinleri.
     */
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
)