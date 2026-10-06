package com.tunix.nazar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    isDeveloperAccessEnabled: Boolean,
    onBackClick: () -> Unit,
    onResetPinClick: () -> Unit,
    onManageSubscriptionClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onDeveloperAccessSubmit: (String) -> Boolean,
    onDeveloperAccessDisable: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showDeveloperAccessInput by rememberSaveable { mutableStateOf(false) }
    var developerAccessCode by rememberSaveable { mutableStateOf("") }
    var developerAccessMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var developerAccessError by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .imePadding()
            .navigationBarsPadding()
            .padding(
                horizontal = ScreenPadding,
                vertical = VerticalScreenPadding
            ),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Muhafız Ayarları",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(TitleGap))

        InfoCard(
            title = "Koruma bilgisi",
            body = "Muhafız, ekran içeriğini kullanıcı izniyle analiz eder. " +
                    "Ekran görüntüleri yalnızca cihaz üzerinde anlık olarak işlenir; " +
                    "kaydedilmez, sunucuya gönderilmez ve üçüncü taraflarla paylaşılmaz."
        )

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
                Text("Abonelik", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(SmallGap))
                Text(
                    text = "Aylık abonelik otomatik olarak yenilenir. " +
                            "Aboneliğinizi Google Play üzerinden yönetebilir veya iptal edebilirsiniz.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(ButtonGap))
                OutlinedButton(
                    onClick = onManageSubscriptionClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Aboneliği Yönet / İptal Et")
                }
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
                Text("Gizlilik", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(SmallGap))
                Text(
                    text = "Ekran analizi cihaz üzerinde gerçekleştirilir. " +
                            "Gizlilik ve kullanılan izinler hakkında ayrıntılı bilgi için politikayı görüntüleyebilirsiniz.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(ButtonGap))
                OutlinedButton(
                    onClick = onPrivacyPolicyClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Gizlilik Politikasını Görüntüle")
                }
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
                Text("Ebeveyn kilidi", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(SmallGap))
                Text(
                    text = "PIN sıfırlama işlemi mevcut ebeveyn kilidini kaldırır. " +
                            "Bu işlemden sonra yeni bir PIN oluşturulması gerekir.",
                    style = MaterialTheme.typography.bodyMedium
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

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CardCornerRadius)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CardPadding)
            ) {
                Text("Geliştirici erişimi", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(SmallGap))

                if (isDeveloperAccessEnabled) {
                    Text(
                        text = "Geliştirici / inceleme erişimi aktif. " +
                                "Koruma özellikleri aktif abonelik olmadan kullanılabilir.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(ButtonGap))
                    OutlinedButton(
                        onClick = {
                            onDeveloperAccessDisable()
                            developerAccessCode = ""
                            developerAccessMessage = null
                            developerAccessError = false
                            showDeveloperAccessInput = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Geliştirici Erişimini Kapat")
                    }
                } else {
                    Text(
                        text = "Bu alan yalnızca yetkili geliştirici ve uygulama inceleme erişimi içindir.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(ButtonGap))

                    if (!showDeveloperAccessInput) {
                        OutlinedButton(
                            onClick = {
                                showDeveloperAccessInput = true
                                developerAccessMessage = null
                                developerAccessError = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Geliştirici Erişimini Aç")
                        }
                    } else {
                        OutlinedTextField(
                            value = developerAccessCode,
                            onValueChange = { newValue ->
                                developerAccessCode = newValue
                                developerAccessMessage = null
                                developerAccessError = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Erişim kodu") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            isError = developerAccessError,
                            supportingText = {
                                developerAccessMessage?.let { Text(it) }
                            }
                        )
                        Spacer(modifier = Modifier.height(ButtonGap))
                        Button(
                            onClick = {
                                val code = developerAccessCode.trim()
                                if (code.isBlank()) {
                                    developerAccessError = true
                                    developerAccessMessage = "Geliştirici erişim kodunu girin."
                                    return@Button
                                }

                                val accessGranted = onDeveloperAccessSubmit(code)
                                if (accessGranted) {
                                    developerAccessError = false
                                    developerAccessMessage = "Geliştirici erişimi etkinleştirildi."
                                    developerAccessCode = ""
                                    showDeveloperAccessInput = false
                                } else {
                                    developerAccessError = true
                                    developerAccessMessage = "Erişim kodu geçersiz."
                                }
                            },
                            enabled = developerAccessCode.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Erişimi Doğrula")
                        }
                        Spacer(modifier = Modifier.height(SmallGap))
                        OutlinedButton(
                            onClick = {
                                developerAccessCode = ""
                                developerAccessMessage = null
                                developerAccessError = false
                                showDeveloperAccessInput = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("İptal")
                        }
                    }
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

        Spacer(modifier = Modifier.height(BottomGap))
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardPadding)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(SmallGap))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val ScreenPadding = 24.dp
private val VerticalScreenPadding = 24.dp
private val CardPadding = 20.dp
private val CardCornerRadius = 20.dp
private val TitleGap = 20.dp
private val SmallGap = 10.dp
private val CardGap = 16.dp
private val ButtonGap = 16.dp
private val BottomGap = 24.dp
