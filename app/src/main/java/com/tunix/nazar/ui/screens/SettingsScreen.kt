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
fun SettingsScreen(
    onBackClick: () -> Unit,
    onResetPinClick: () -> Unit
) {
    /*
     * Ayarlar ekranı:
     * - Muhafız'ın nasıl çalıştığını kullanıcıya açıklar.
     * - Ebeveyn PIN sıfırlama işlemini sunar.
     *
     * Not:
     * Bu ekran şu an sade tutuldu. İleride hassasiyet seviyesi,
     * analiz sıklığı veya raporlama ayarları buraya eklenebilir.
     */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Muhafız Ayarları",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(TitleGap))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardCornerRadius)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CardPadding)
            ) {
                Text(
                    text = "Koruma bilgisi",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(SmallGap))

                Text(
                    text = "Muhafız, ekran içeriğini kullanıcı izniyle analiz eder. Pornografik veya +18 içerik riski algılandığında ekranı siyah koruma ekranıyla gizler.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(ParagraphGap))

                Text(
                    text = "Uygulama çalışırken bildirim alanında görünür ve koruma arka planda aktif kalır.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(CardGap))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardCornerRadius)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CardPadding)
            ) {
                Text(
                    text = "Ebeveyn kilidi",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(SmallGap))

                Text(
                    text = "PIN sıfırlama işlemi mevcut ebeveyn kilidini kaldırır. Bu işlemden sonra yeni bir PIN oluşturulması gerekir.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(ButtonGap))

                Button(
                    onClick = onResetPinClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PIN'i Sıfırla")
                }
            }
        }

        Spacer(modifier = Modifier.height(CardGap))

        OutlinedButton(
            onClick = onBackClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Geri Dön")
        }
    }
}

private val ScreenPadding = 24.dp
private val CardPadding = 20.dp
private val CardCornerRadius = 20.dp

private val TitleGap = 20.dp
private val SmallGap = 10.dp
private val ParagraphGap = 8.dp
private val CardGap = 16.dp
private val ButtonGap = 16.dp