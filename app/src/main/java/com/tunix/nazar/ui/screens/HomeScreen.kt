package com.tunix.nazar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
    isBillingReady: Boolean,
    onSetupPinClick: () -> Unit,
    onProtectionToggleClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    text = "Muhafız, ekrandaki görüntüyü kullanıcı izniyle analiz eder. Pornografik veya +18 içerik riski algılandığında ekranı siyah koruma ekranıyla gizler.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(MediumGap))

                Text(
                    text = when {
                        !isSubscribed -> "Muhafız korumasını kullanmak için aktif aylık abonelik gereklidir."
                        isProtectionRunning -> "Abonelik aktif. Koruma şu anda çalışıyor ve Muhafız bildirim alanında görünür."
                        else -> "Abonelik aktif. Koruma başlatıldığında Muhafız bildirim alanında görünür ve arka planda çalışır."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(MediumGap))

                Text(
                    text = "Telefon yeniden başlatıldığında korumayı tekrar başlatmayı ve çocuğunuz cihazı kullanırken korumanın aktif olduğunu kontrol etmeyi unutmayın.",
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

        if (!isSubscribed) {
            Button(
                onClick = onSubscribeClick,
                enabled = isBillingReady,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isBillingReady) {
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
            } else {
                Button(
                    onClick = onProtectionToggleClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isProtectionRunning) {
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
    }
}

private val ScreenPadding = 24.dp
private val CardPadding = 20.dp
private val CardCornerRadius = 20.dp

private val SmallGap = 10.dp
private val MediumGap = 12.dp
private val LargeGap = 24.dp
private val ActionGap = 28.dp