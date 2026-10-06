package com.tunix.nazar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    hasPin: Boolean,
    isProtectionRunning: Boolean,
    isSubscribed: Boolean,
    hasDeveloperAccess: Boolean,
    isBillingReady: Boolean,
    subscriptionPrice: String?,
    onSetupPinClick: () -> Unit,
    onProtectionToggleClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val hasProtectionAccess =
        isSubscribed || hasDeveloperAccess

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Muhafız",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(SmallGap))

        Text(
            text = "Gerçek zamanlı ebeveyn koruması",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(LargeGap))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardCornerRadius)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CardPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Muhafız, ekrandaki görüntüyü kullanıcı izniyle analiz eder. " +
                            "Pornografik veya +18 içerik riski algılandığında ekranı " +
                            "siyah koruma ekranıyla gizler.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(MediumGap))

                Text(
                    text = when {
                        hasDeveloperAccess && isProtectionRunning ->
                            "İnceleme erişimi aktif. Koruma şu anda çalışıyor ve Muhafız bildirim alanında görünür."
                        hasDeveloperAccess ->
                            "İnceleme erişimi aktif. Koruma abonelik gerektirmeden test edilebilir."
                        !isSubscribed ->
                            "Muhafız korumasını kullanmak için aktif aylık abonelik gereklidir."
                        isProtectionRunning ->
                            "Abonelik aktif. Koruma şu anda çalışıyor ve Muhafız bildirim alanında görünür."
                        else ->
                            "Abonelik aktif. Koruma başlatıldığında Muhafız bildirim alanında görünür ve arka planda çalışır."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(MediumGap))

                Text(
                    text = "Telefon yeniden başlatıldığında korumayı tekrar başlatmayı " +
                            "ve çocuğunuz cihazı kullanırken korumanın aktif olduğunu " +
                            "kontrol etmeyi unutmayın.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(SmallGap))

                Text(
                    text = "Tunix © 2026",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(ActionGap))

        if (!hasProtectionAccess) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CardCornerRadius)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Muhafız Aylık",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(SmallGap))

                    Text(
                        text = subscriptionPrice?.let { price ->
                            "$price / ay"
                        } ?: if (isBillingReady) {
                            "Fiyat bilgisi Google Play'den alınıyor."
                        } else {
                            "Google Play abonelik sistemi hazırlanıyor."
                        },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(SmallGap))

                    Text(
                        text = "Abonelik her ay otomatik yenilenir. " +
                                "İstediğiniz zaman Google Play üzerinden yönetebilir veya iptal edebilirsiniz. " +
                                "Koruma özelliğini kullanmak için aktif abonelik gerekir.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(MediumGap))

            Button(
                onClick = onSubscribeClick,
                enabled = isBillingReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isBillingReady) {
                        "Aylık Abonelik Başlat"
                    } else {
                        "Abonelik Sistemi Hazırlanıyor"
                    }
                )
            }

            Spacer(modifier = Modifier.height(MediumGap))

            OutlinedButton(
                onClick = onSettingsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ayarlar")
            }
        } else {
            if (!hasPin) {
                Button(
                    onClick = onSetupPinClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ebeveyn PIN'i Oluştur")
                }

                Spacer(modifier = Modifier.height(MediumGap))

                OutlinedButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ayarlar")
                }
            } else {
                Button(
                    onClick = onProtectionToggleClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isProtectionRunning) {
                            "Korumayı Durdur"
                        } else {
                            "Koruma Başlat"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(MediumGap))

                OutlinedButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ayarlar")
                }
            }
        }

        Spacer(modifier = Modifier.height(BottomGap))
    }
}

private val ScreenPadding = 24.dp
private val CardPadding = 20.dp
private val CardCornerRadius = 20.dp
private val SmallGap = 10.dp
private val MediumGap = 12.dp
private val LargeGap = 24.dp
private val ActionGap = 28.dp
private val BottomGap = 24.dp
