package com.tunix.nazar

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tunix.nazar.billing.BillingManager
import com.tunix.nazar.receiver.MuhafizDeviceAdminReceiver
import com.tunix.nazar.security.ParentLockManager
import com.tunix.nazar.service.OverlayService
import com.tunix.nazar.service.ScreenCaptureService
import com.tunix.nazar.ui.screens.HomeScreen
import com.tunix.nazar.ui.screens.PinSetupScreen
import com.tunix.nazar.ui.screens.SettingsScreen
import com.tunix.nazar.ui.theme.MuhafizTheme

class MainActivity : ComponentActivity() {

    private lateinit var parentLockManager: ParentLockManager
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var deviceAdminComponent: ComponentName
    private lateinit var billingManager: BillingManager

    private val protectionRunningState = mutableStateOf(false)
    private val isSubscribedState = mutableStateOf(false)
    private val isBillingReadyState = mutableStateOf(false)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                showToast(
                    "Bildirim izni verilmedi. Muhafız çalışırken kalıcı bildirim görünmeyebilir.",
                    long = true
                )
            }
        }

    private val deviceAdminLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (isDeviceAdminActive()) {
                showToast("Cihaz yöneticisi koruması etkinleştirildi.")
                continueProtectionFlowAfterDeviceAdmin()
            } else {
                showToast(
                    "Muhafız'ın kolayca kaldırılmasını zorlaştırmak için cihaz yöneticisi izni önerilir.",
                    long = true
                )
            }
        }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (hasOverlayPermission()) {
                requestScreenCapture()
            } else {
                showToast("Koruma için uygulama üstü gösterim izni gerekli.")
            }
        }

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                startScreenCaptureService(
                    resultCode = result.resultCode,
                    data = result.data!!
                )

                protectionRunningState.value = true
                showToast("Muhafız koruması başlatıldı.")
            } else {
                protectionRunningState.value = false
                showToast("Ekran yakalama izni verilmedi.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        parentLockManager = ParentLockManager(this)
        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        deviceAdminComponent = ComponentName(
            this,
            MuhafizDeviceAdminReceiver::class.java
        )

        billingManager = BillingManager(this) { isSubscribed ->
            isSubscribedState.value = isSubscribed
        }

        billingManager.startConnection()
        isBillingReadyState.value = true

        ensureNotificationPermission()

        setContent {
            MuhafizTheme {
                MuhafizApp(
                    parentLockManager = parentLockManager,
                    isProtectionRunning = protectionRunningState.value,
                    isSubscribed = isSubscribedState.value,
                    isBillingReady = isBillingReadyState.value,
                    onSubscribeClick = {
                        billingManager.purchase(this)
                    },
                    onShowMessage = { message ->
                        showToast(message)
                    },
                    onRequestProtectionStart = {
                        if (isSubscribedState.value) {
                            startProtectionFlow()
                        } else {
                            showToast("Koruma için aktif abonelik gerekli.")
                        }
                    },
                    onRequestProtectionStop = {
                        stopProtectionServices()
                        protectionRunningState.value = false
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startProtectionFlow() {
        if (!isDeviceAdminActive()) {
            requestDeviceAdminPermission()
            return
        }

        continueProtectionFlowAfterDeviceAdmin()
    }

    private fun continueProtectionFlowAfterDeviceAdmin() {
        if (!hasOverlayPermission()) {
            openOverlayPermissionScreen()
            return
        }

        requestScreenCapture()
    }

    private fun isDeviceAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(deviceAdminComponent)
    }

    private fun requestDeviceAdminPermission() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Muhafız'ın çocuk tarafından kolayca kaldırılmasını veya devre dışı bırakılmasını zorlaştırmak için bu izin gereklidir."
            )
        }

        deviceAdminLauncher.launch(intent)
    }

    private fun requestScreenCapture() {
        stopProtectionServices()
        protectionRunningState.value = false

        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun openOverlayPermissionScreen() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )

        overlayPermissionLauncher.launch(intent)
    }

    private fun stopProtectionServices() {
        try {
            stopService(Intent(this, ScreenCaptureService::class.java))
        } catch (_: Exception) {
        }

        try {
            stopService(Intent(this, OverlayService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun startScreenCaptureService(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun showToast(message: String, long: Boolean = false) {
        Toast.makeText(
            this,
            message,
            if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }
}

private enum class MuhafizScreen {
    PIN_SETUP,
    HOME,
    SETTINGS
}

@Composable
private fun MuhafizApp(
    parentLockManager: ParentLockManager,
    isProtectionRunning: Boolean,
    isSubscribed: Boolean,
    isBillingReady: Boolean,
    onSubscribeClick: () -> Unit,
    onShowMessage: (String) -> Unit,
    onRequestProtectionStart: () -> Unit,
    onRequestProtectionStop: () -> Unit
) {
    var hasPin by remember { mutableStateOf(parentLockManager.hasPin()) }
    var currentScreen by remember {
        mutableStateOf(
            if (hasPin) MuhafizScreen.HOME else MuhafizScreen.PIN_SETUP
        )
    }

    var showStopProtectionDialog by remember { mutableStateOf(false) }
    var showSettingsPinDialog by remember { mutableStateOf(false) }
    var showResetPinDialog by remember { mutableStateOf(false) }

    when (currentScreen) {
        MuhafizScreen.PIN_SETUP -> {
            PinSetupScreen(
                onSavePin = { pin ->
                    val saved = parentLockManager.savePin(pin)
                    if (saved) {
                        hasPin = true
                        currentScreen = MuhafizScreen.HOME
                        onShowMessage("Ebeveyn PIN'i kaydedildi.")
                    } else {
                        onShowMessage("PIN kaydedilemedi.")
                    }
                }
            )
        }

        MuhafizScreen.HOME -> {
            HomeScreen(
                hasPin = hasPin,
                isProtectionRunning = isProtectionRunning,
                isSubscribed = isSubscribed,
                isBillingReady = isBillingReady,
                onSetupPinClick = {
                    currentScreen = MuhafizScreen.PIN_SETUP
                },
                onProtectionToggleClick = {
                    if (!isProtectionRunning) {
                        onRequestProtectionStart()
                    } else {
                        showStopProtectionDialog = true
                    }
                },
                onSubscribeClick = onSubscribeClick,
                onSettingsClick = {
                    showSettingsPinDialog = true
                }
            )

            if (showStopProtectionDialog) {
                PinVerificationDialog(
                    title = "Korumayı Durdur",
                    message = "Muhafız korumasını durdurmak için ebeveyn PIN'ini girin.",
                    onDismiss = {
                        showStopProtectionDialog = false
                    },
                    onVerify = { pin ->
                        if (parentLockManager.verifyPin(pin)) {
                            showStopProtectionDialog = false
                            onRequestProtectionStop()
                            onShowMessage("Muhafız koruması durduruldu.")
                        } else {
                            onShowMessage("PIN doğrulanamadı.")
                        }
                    }
                )
            }

            if (showSettingsPinDialog) {
                PinVerificationDialog(
                    title = "Ayarlar",
                    message = "Muhafız ayarlarına girmek için ebeveyn PIN'ini girin.",
                    onDismiss = {
                        showSettingsPinDialog = false
                    },
                    onVerify = { pin ->
                        if (parentLockManager.verifyPin(pin)) {
                            showSettingsPinDialog = false
                            currentScreen = MuhafizScreen.SETTINGS
                        } else {
                            onShowMessage("PIN doğrulanamadı.")
                        }
                    }
                )
            }
        }

        MuhafizScreen.SETTINGS -> {
            SettingsScreen(
                onBackClick = {
                    currentScreen = MuhafizScreen.HOME
                },
                onResetPinClick = {
                    showResetPinDialog = true
                }
            )

            if (showResetPinDialog) {
                PinVerificationDialog(
                    title = "PIN'i Sıfırla",
                    message = "Ebeveyn PIN'ini sıfırlamak için mevcut PIN'i girin.",
                    onDismiss = {
                        showResetPinDialog = false
                    },
                    onVerify = { pin ->
                        if (parentLockManager.verifyPin(pin)) {
                            val cleared = parentLockManager.clearPin()
                            if (cleared) {
                                showResetPinDialog = false
                                hasPin = false
                                currentScreen = MuhafizScreen.PIN_SETUP
                                onShowMessage("PIN sıfırlandı.")
                            } else {
                                onShowMessage("PIN sıfırlanamadı.")
                            }
                        } else {
                            onShowMessage("PIN doğrulanamadı.")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PinVerificationDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(DialogContentGap)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        pin = value.onlyDigits(maxLength = MAX_PIN_LENGTH)
                        errorText = null
                    },
                    label = { Text("Ebeveyn PIN'i") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Text(
                        text = errorText.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(DialogBottomGap))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pin.length < MIN_PIN_LENGTH) {
                        errorText = "Geçerli bir PIN girin."
                    } else {
                        onVerify(pin)
                    }
                }
            ) {
                Text("Doğrula")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

private fun String.onlyDigits(maxLength: Int): String {
    return filter { ch -> ch.isDigit() }.take(maxLength)
}

private val DialogContentGap = 12.dp
private val DialogBottomGap = 2.dp

private const val MIN_PIN_LENGTH = 4
private const val MAX_PIN_LENGTH = 6