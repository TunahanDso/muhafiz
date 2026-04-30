package com.tunix.nazar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/*
 * Muhafız Material 3 tema yapılandırması.
 *
 * Not:
 * Renk değişkenlerinin adı Color.kt içinde şimdilik NazarPrimary vb. kalıyor.
 * Bunun nedeni mevcut dosyalarda bu isimlere bağlı referansların build kırmamasıdır.
 * Dışarıdan görünen ürün adı ve UI metinleri Muhafız olarak güncellenmiştir.
 */

private val DarkColorScheme = darkColorScheme(
    primary = NazarPrimaryDark,
    onPrimary = NazarOnPrimaryDark,
    primaryContainer = NazarPrimaryContainerDark,
    onPrimaryContainer = NazarOnPrimaryContainerDark,

    secondary = NazarSecondaryDark,
    onSecondary = NazarOnSecondaryDark,
    secondaryContainer = NazarSecondaryContainerDark,
    onSecondaryContainer = NazarOnSecondaryContainerDark,

    tertiary = NazarTertiaryDark,
    onTertiary = NazarOnTertiaryDark,
    tertiaryContainer = NazarTertiaryContainerDark,
    onTertiaryContainer = NazarOnTertiaryContainerDark,

    error = NazarErrorDark,
    onError = NazarOnErrorDark,
    errorContainer = NazarErrorContainerDark,
    onErrorContainer = NazarOnErrorContainerDark,

    background = NazarBackgroundDark,
    onBackground = NazarOnBackgroundDark,

    surface = NazarSurfaceDark,
    onSurface = NazarOnSurfaceDark,
    surfaceVariant = NazarSurfaceVariantDark,
    onSurfaceVariant = NazarOnSurfaceVariantDark,

    outline = NazarOutlineDark,
    outlineVariant = NazarOutlineVariantDark,
    scrim = NazarScrimDark
)

private val LightColorScheme = lightColorScheme(
    primary = NazarPrimary,
    onPrimary = NazarOnPrimary,
    primaryContainer = NazarPrimaryContainer,
    onPrimaryContainer = NazarOnPrimaryContainer,

    secondary = NazarSecondary,
    onSecondary = NazarOnSecondary,
    secondaryContainer = NazarSecondaryContainer,
    onSecondaryContainer = NazarOnSecondaryContainer,

    tertiary = NazarTertiary,
    onTertiary = NazarOnTertiary,
    tertiaryContainer = NazarTertiaryContainer,
    onTertiaryContainer = NazarOnTertiaryContainer,

    error = NazarError,
    onError = NazarOnError,
    errorContainer = NazarErrorContainer,
    onErrorContainer = NazarOnErrorContainer,

    background = NazarBackground,
    onBackground = NazarOnBackground,

    surface = NazarSurface,
    onSurface = NazarOnSurface,
    surfaceVariant = NazarSurfaceVariant,
    onSurfaceVariant = NazarOnSurfaceVariant,

    outline = NazarOutline,
    outlineVariant = NazarOutlineVariant,
    scrim = NazarScrim
)

@Composable
fun MuhafizTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    /*
     * dynamicColor parametresi şimdilik bilinçli olarak kullanılmıyor.
     *
     * Sebep:
     * Muhafız güvenlik/koruma ürünü olduğu için marka renklerinin cihaz temasına göre
     * değişmesini istemiyoruz. Daha tutarlı bir ürün kimliği için sabit renk paleti kullanılır.
     */
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun NazarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    /*
     * Eski çağrılar için uyumluluk katmanı.
     *
     * MainActivity veya başka dosyalar hâlâ NazarTheme çağırıyorsa build kırılmaz.
     * Yeni kullanımda MuhafizTheme tercih edilmelidir.
     */
    MuhafizTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}