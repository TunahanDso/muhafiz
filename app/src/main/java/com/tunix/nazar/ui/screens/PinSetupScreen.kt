package com.tunix.nazar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PinSetupScreen(
    onSavePin: (String) -> Unit
) {
    /*
     * PIN kurulum ekranı:
     * - Ebeveyn ilk açılışta PIN belirler.
     * - Bu PIN daha sonra korumayı durdurma ve ayarlara erişim için kullanılır.
     */
    var pin by remember { mutableStateOf("") }
    var pinRepeat by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Muhafız Ebeveyn PIN Kurulumu",
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
                    .padding(CardPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Bu PIN, Muhafız korumasını durdurmak ve uygulama ayarlarını yönetmek için kullanılacaktır. Güvenlik için en az 4 haneli bir PIN belirleyin.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(FormGap))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        /*
                         * PIN alanı yalnızca rakam kabul eder.
                         * Maksimum uzunluk burada sınırlandırılır.
                         */
                        pin = value.onlyDigits(maxLength = MAX_PIN_LENGTH)
                        errorText = null
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(FieldGap))

                OutlinedTextField(
                    value = pinRepeat,
                    onValueChange = { value ->
                        pinRepeat = value.onlyDigits(maxLength = MAX_PIN_LENGTH)
                        errorText = null
                    },
                    label = { Text("PIN Tekrar") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(FieldGap))
                    Text(
                        text = errorText.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(FormGap))

                Button(
                    onClick = {
                        val validationError = validatePinInput(pin, pinRepeat)

                        if (validationError != null) {
                            errorText = validationError
                        } else {
                            onSavePin(pin)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PIN Kaydet")
                }
            }
        }
    }
}

private fun validatePinInput(pin: String, pinRepeat: String): String? {
    return when {
        pin.length < MIN_PIN_LENGTH -> {
            "PIN en az 4 haneli olmalıdır."
        }

        pin != pinRepeat -> {
            "Girilen PIN'ler eşleşmiyor."
        }

        else -> null
    }
}

private fun String.onlyDigits(maxLength: Int): String {
    return filter { ch -> ch.isDigit() }.take(maxLength)
}

private val ScreenPadding = 24.dp
private val CardPadding = 20.dp
private val CardCornerRadius = 20.dp

private val TitleGap = 20.dp
private val FormGap = 20.dp
private val FieldGap = 12.dp

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 6