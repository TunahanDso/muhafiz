package com.tunix.nazar

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import com.tunix.nazar.security.DeveloperAccessManager
import com.tunix.nazar.security.ParentLockManager
import com.tunix.nazar.service.OverlayService
import com.tunix.nazar.service.ScreenCaptureService
import com.tunix.nazar.ui.screens.HomeScreen
import com.tunix.nazar.ui.screens.PinSetupScreen
import com.tunix.nazar.ui.screens.SettingsScreen
import com.tunix.nazar.ui.theme.MuhafizTheme

class MainActivity : ComponentActivity() {

    /*
     * =============================================================
     * MANAGERS
     * =============================================================
     */

    private lateinit var parentLockManager: ParentLockManager
    private lateinit var developerAccessManager: DeveloperAccessManager

    private lateinit var mediaProjectionManager: MediaProjectionManager

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var deviceAdminComponent: ComponentName

    private lateinit var billingManager: BillingManager


    /*
     * =============================================================
     * UI / APPLICATION STATE
     * =============================================================
     */

    private val protectionRunningState =
        mutableStateOf(false)

    private val isSubscribedState =
        mutableStateOf(false)

    /*
     * Bu state artık Billing bağlantısı kurulmadan true yapılmaz.
     *
     * Gerçek BillingManager callback'i tarafından yönetilir.
     */
    private val isBillingReadyState =
        mutableStateOf(false)

    private val subscriptionPriceState =
        mutableStateOf<String?>(null)

    /*
     * Abonelik durumunun en az bir kez başarıyla sorgulanıp
     * sorgulanmadığını ayırıyoruz.
     *
     * Böylece uygulama açılır açılmaz gerçek aboneliği olan
     * kullanıcıya yanlışlıkla "abonelik gerekli" demiyoruz.
     */
    private val subscriptionStatusResolvedState =
        mutableStateOf(false)

    private val developerAccessState =
        mutableStateOf(false)


    /*
     * =============================================================
     * PROTECTION STATE RECEIVER
     * =============================================================
     */

    private var protectionStateReceiverRegistered =
        false

    /*
     * ScreenCaptureService gerçek çalışma durumunu yayınlar.
     *
     * Activity'nin kendi tahmini yerine servisin gerçek state'i
     * kullanılır.
     */
    private val protectionStateReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (
                    intent?.action ==
                    ScreenCaptureService.ACTION_PROTECTION_STATE_CHANGED
                ) {
                    protectionRunningState.value =
                        intent.getBooleanExtra(
                            ScreenCaptureService.EXTRA_PROTECTION_RUNNING,
                            false
                        )
                }
            }
        }


    /*
     * =============================================================
     * NOTIFICATION PERMISSION
     * =============================================================
     */

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (!granted) {
                showToast(
                    message =
                        "Bildirim izni verilmedi. " +
                                "Muhafız çalışırken koruma bildirimi " +
                                "beklendiği şekilde görünmeyebilir.",
                    long = true
                )
            }
        }


    /*
     * =============================================================
     * DEVICE ADMIN
     * =============================================================
     */

    private val deviceAdminLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {

            if (isDeviceAdminActive()) {

                showToast(
                    "Cihaz yöneticisi koruması etkinleştirildi."
                )

                continueProtectionFlowAfterDeviceAdmin()

            } else {

                showToast(
                    message =
                        "Cihaz yöneticisi izni verilmedi. " +
                                "Koruma başlatılamadı.",
                    long = true
                )
            }
        }


    /*
     * =============================================================
     * OVERLAY PERMISSION
     * =============================================================
     */

    private val overlayPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {

            if (hasOverlayPermission()) {

                requestScreenCapture()

            } else {

                showToast(
                    message =
                        "Koruma için uygulama üstü gösterim izni gerekli.",
                    long = true
                )
            }
        }


    /*
     * =============================================================
     * MEDIA PROJECTION
     * =============================================================
     */

    private val screenCaptureLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val resultData =
                result.data

            if (
                result.resultCode == Activity.RESULT_OK &&
                resultData != null
            ) {

                /*
                 * Burada protectionRunningState = true yapmıyoruz.
                 *
                 * ScreenCaptureService gerçekten ayağa kalktıktan sonra
                 * ACTION_PROTECTION_STATE_CHANGED broadcast'i gönderir.
                 */
                startScreenCaptureService(
                    resultCode = result.resultCode,
                    data = resultData
                )

                showToast(
                    "Muhafız koruması başlatılıyor."
                )

            } else {

                protectionRunningState.value =
                    false

                showToast(
                    "Ekran yakalama izni verilmedi."
                )
            }
        }


    /*
     * =============================================================
     * ACTIVITY CREATE
     * =============================================================
     */

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)


        /*
         * ---------------------------------------------------------
         * LOCAL SECURITY
         * ---------------------------------------------------------
         */

        parentLockManager =
            ParentLockManager(this)

        developerAccessManager =
            DeveloperAccessManager(this)

        developerAccessState.value =
            developerAccessManager.isUnlocked()


        /*
         * Activity yeniden oluşturulmuş olabilir fakat
         * ScreenCaptureService hâlâ arka planda çalışıyor olabilir.
         */
        syncProtectionStateFromService()


        /*
         * ---------------------------------------------------------
         * MEDIA PROJECTION
         * ---------------------------------------------------------
         */

        mediaProjectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager


        /*
         * ---------------------------------------------------------
         * DEVICE ADMIN
         * ---------------------------------------------------------
         */

        devicePolicyManager =
            getSystemService(
                Context.DEVICE_POLICY_SERVICE
            ) as DevicePolicyManager

        deviceAdminComponent =
            ComponentName(
                this,
                MuhafizDeviceAdminReceiver::class.java
            )


        /*
         * ---------------------------------------------------------
         * GOOGLE PLAY BILLING
         * ---------------------------------------------------------
         */

        billingManager =
            BillingManager(

                context = this,

                /*
                 * queryPurchases başarılı şekilde sonuçlandığında
                 * gerçek entitlement burada güncellenir.
                 */
                onSubscriptionStatusChanged = { isSubscribed ->

                    subscriptionStatusResolvedState.value =
                        true

                    isSubscribedState.value =
                        isSubscribed

                    /*
                     * Kullanıcı artık abonelik hakkına sahip değilse
                     * ve reviewer/developer access de kapalıysa
                     * aktif korumayı çalışır bırakmıyoruz.
                     */
                    if (
                        !isSubscribed &&
                        !developerAccessState.value
                    ) {
                        stopProtectionServices()

                        protectionRunningState.value =
                            false
                    }
                },

                /*
                 * BillingClient gerçekten READY olduğunda true.
                 *
                 * Artık Activity bunu tahmin etmiyor.
                 */
                onBillingReadyChanged = { isReady ->

                    isBillingReadyState.value =
                        isReady
                },

                onSubscriptionPriceChanged = { formattedPrice ->

                    subscriptionPriceState.value =
                        formattedPrice
                },

                /*
                 * BillingManager'daki hiçbir önemli hata artık
                 * kullanıcıdan gizlenmez.
                 */
                onUserMessage = { message ->

                    showToast(
                        message = message,
                        long = true
                    )
                }
            )

        /*
         * Gerçek bağlantı başlatılır.
         */
        billingManager.startConnection()


        /*
         * ---------------------------------------------------------
         * NOTIFICATION
         * ---------------------------------------------------------
         */

        ensureNotificationPermission()


        /*
         * ---------------------------------------------------------
         * COMPOSE
         * ---------------------------------------------------------
         */

        setContent {

            MuhafizTheme {

                MuhafizApp(

                    parentLockManager =
                        parentLockManager,

                    isProtectionRunning =
                        protectionRunningState.value,

                    isSubscribed =
                        isSubscribedState.value,

                    hasDeveloperAccess =
                        developerAccessState.value,

                    isBillingReady =
                        isBillingReadyState.value,

                    subscriptionPrice =
                        subscriptionPriceState.value,


                    /*
                     * -------------------------------------------------
                     * SUBSCRIPTION
                     * -------------------------------------------------
                     */

                    onSubscribeClick = {

                        /*
                         * BillingManager bağlantı hazır değilse
                         * bağlantıyı kendisi kurup satın alma işlemini
                         * ardından devam ettirir.
                         */
                        billingManager.purchase(this)
                    },

                    onManageSubscriptionClick = {
                        openSubscriptionManagement()
                    },

                    onPrivacyPolicyClick = {
                        openPrivacyPolicy()
                    },


                    /*
                     * -------------------------------------------------
                     * UI MESSAGE
                     * -------------------------------------------------
                     */

                    onShowMessage = { message ->

                        showToast(
                            message
                        )
                    },


                    /*
                     * -------------------------------------------------
                     * START PROTECTION
                     * -------------------------------------------------
                     */

                    onRequestProtectionStart = {

                        val hasProtectionAccess =
                            isSubscribedState.value ||
                                    developerAccessState.value

                        when {

                            /*
                             * Abonelik veya reviewer erişimi varsa
                             * koruma başlatılabilir.
                             */
                            hasProtectionAccess -> {

                                startProtectionFlow()
                            }


                            /*
                             * Abonelik sorgusu henüz tamamlanmadı.
                             *
                             * Kullanıcı gerçekten aboneyse yanlışlıkla
                             * erişimini reddetmeyelim.
                             */
                            !subscriptionStatusResolvedState.value -> {

                                showToast(
                                    message =
                                        "Abonelik durumu Google Play üzerinden " +
                                                "doğrulanıyor.",
                                    long = true
                                )

                                billingManager.refreshPurchases()
                            }


                            /*
                             * Sorgu tamamlandı ve hak yok.
                             */
                            else -> {

                                showToast(
                                    message =
                                        "Koruma için aktif abonelik gerekli.",
                                    long = true
                                )
                            }
                        }
                    },


                    /*
                     * -------------------------------------------------
                     * STOP PROTECTION
                     * -------------------------------------------------
                     */

                    onRequestProtectionStop = {

                        stopProtectionServices()

                        /*
                         * Service de broadcast gönderecek.
                         *
                         * Ancak UI'nin kullanıcıya anında cevap
                         * vermesi için state burada da kapatılır.
                         */
                        protectionRunningState.value =
                            false
                    },


                    /*
                     * -------------------------------------------------
                     * REVIEWER / DEVELOPER ACCESS
                     * -------------------------------------------------
                     */

                    onDeveloperAccessSubmit = { code ->

                        val granted =
                            developerAccessManager.unlock(
                                code
                            )

                        if (granted) {

                            developerAccessState.value =
                                true

                            showToast(
                                "Geliştirici erişimi etkinleştirildi."
                            )
                        }

                        granted
                    },


                    onDeveloperAccessDisable = {

                        developerAccessManager.lock()

                        developerAccessState.value =
                            false

                        /*
                         * Abonelik hakkı yoksa reviewer erişimi
                         * kapandığında koruma da çalışmaya devam edemez.
                         */
                        if (!isSubscribedState.value) {

                            stopProtectionServices()

                            protectionRunningState.value =
                                false
                        }

                        showToast(
                            "Geliştirici erişimi kapatıldı."
                        )
                    }
                )
            }
        }
    }


    /*
     * =============================================================
     * ACTIVITY LIFECYCLE
     * =============================================================
     */

    override fun onStart() {
        super.onStart()

        registerProtectionStateReceiver()

        syncProtectionStateFromService()
    }


    /*
     * Activity tekrar ön plana geldiğinde Play tarafındaki
     * entitlement yeniden doğrulanır.
     *
     * Örneğin:
     * - satın alma tamamlandı,
     * - Play ekranından geri gelindi,
     * - abonelik başka yerde değişti.
     */
    override fun onResume() {
        super.onResume()

        if (::billingManager.isInitialized) {
            billingManager.refreshPurchases()
        }
    }


    /*
     * Activity görünür değilken BroadcastReceiver'a ihtiyaç yok.
     *
     * ScreenCaptureService bundan etkilenmez ve arka planda
     * çalışmaya devam eder.
     */
    override fun onStop() {

        unregisterProtectionStateReceiver()

        super.onStop()
    }


    override fun onDestroy() {

        /*
         * Normalde onStop içinde kaldırılır.
         * Buradaki çağrı ek güvenliktir.
         */
        unregisterProtectionStateReceiver()

        /*
         * Activity'nin kapanması ScreenCaptureService'i DURDURMAZ.
         *
         * Yalnızca Activity'ye bağlı BillingClient bağlantısını
         * kapatıyoruz.
         */
        if (::billingManager.isInitialized) {
            billingManager.close()
        }

        super.onDestroy()
    }


    /*
     * =============================================================
     * PROTECTION STATE
     * =============================================================
     */

    private fun syncProtectionStateFromService() {

        protectionRunningState.value =
            ScreenCaptureService.isRunning()
    }


    private fun registerProtectionStateReceiver() {

        if (protectionStateReceiverRegistered) {
            return
        }

        val filter =
            IntentFilter(
                ScreenCaptureService.ACTION_PROTECTION_STATE_CHANGED
            )

        /*
         * Receiver yalnızca uygulama içi state iletişimi için.
         *
         * Dışarıdan broadcast kabul etmiyoruz.
         */
        ContextCompat.registerReceiver(
            this,
            protectionStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        protectionStateReceiverRegistered =
            true
    }


    private fun unregisterProtectionStateReceiver() {

        if (!protectionStateReceiverRegistered) {
            return
        }

        try {

            unregisterReceiver(
                protectionStateReceiver
            )

        } catch (_: Exception) {

            /*
             * Receiver sistem tarafından zaten kaldırılmış olabilir.
             */

        } finally {

            protectionStateReceiverRegistered =
                false
        }
    }


    /*
     * =============================================================
     * NOTIFICATION PERMISSION
     * =============================================================
     */

    private fun ensureNotificationPermission() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }

        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {

            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }


    /*
     * =============================================================
     * PROTECTION START FLOW
     * =============================================================
     */

    private fun startProtectionFlow() {

        /*
         * Device Admin kontrolü.
         */
        if (!isDeviceAdminActive()) {

            requestDeviceAdminPermission()

            return
        }

        continueProtectionFlowAfterDeviceAdmin()
    }


    private fun continueProtectionFlowAfterDeviceAdmin() {

        /*
         * Overlay kontrolü.
         */
        if (!hasOverlayPermission()) {

            openOverlayPermissionScreen()

            return
        }

        /*
         * Son adım:
         * MediaProjection izni.
         */
        requestScreenCapture()
    }


    /*
     * =============================================================
     * DEVICE ADMIN
     * =============================================================
     */

    private fun isDeviceAdminActive(): Boolean {

        return devicePolicyManager.isAdminActive(
            deviceAdminComponent
        )
    }


    private fun requestDeviceAdminPermission() {

        val intent =
            Intent(
                DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
            ).apply {

                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    deviceAdminComponent
                )

                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Muhafız'ın çocuk tarafından kolayca kaldırılmasını " +
                            "veya devre dışı bırakılmasını zorlaştırmak " +
                            "için bu izin gereklidir."
                )
            }

        deviceAdminLauncher.launch(
            intent
        )
    }


    /*
     * =============================================================
     * OVERLAY
     * =============================================================
     */

    private fun hasOverlayPermission(): Boolean {

        return Settings.canDrawOverlays(
            this
        )
    }


    private fun openOverlayPermissionScreen() {

        val intent =
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse(
                    "package:$packageName"
                )
            )

        overlayPermissionLauncher.launch(
            intent
        )
    }


    /*
     * =============================================================
     * MEDIA PROJECTION
     * =============================================================
     */

    private fun requestScreenCapture() {

        /*
         * Yeni bir MediaProjection session açmadan önce
         * eski koruma oturumunu temizliyoruz.
         */
        stopProtectionServices()

        protectionRunningState.value =
            false

        val captureIntent =
            mediaProjectionManager
                .createScreenCaptureIntent()

        screenCaptureLauncher.launch(
            captureIntent
        )
    }


    /*
     * =============================================================
     * SERVICES
     * =============================================================
     */

    private fun startScreenCaptureService(
        resultCode: Int,
        data: Intent
    ) {

        val serviceIntent =
            Intent(
                this,
                ScreenCaptureService::class.java
            ).apply {

                putExtra(
                    ScreenCaptureService.EXTRA_RESULT_CODE,
                    resultCode
                )

                putExtra(
                    ScreenCaptureService.EXTRA_RESULT_DATA,
                    data
                )
            }

        /*
         * minSdk 30 olduğu için foreground service kullanılabilir.
         *
         * ContextCompat çağrısı API seviyesini kendisi yönetir.
         */
        ContextCompat.startForegroundService(
            this,
            serviceIntent
        )
    }


    private fun stopProtectionServices() {

        try {

            stopService(
                Intent(
                    this,
                    ScreenCaptureService::class.java
                )
            )

        } catch (_: Exception) {
        }

        try {

            stopService(
                Intent(
                    this,
                    OverlayService::class.java
                )
            )

        } catch (_: Exception) {
        }
    }


    /*
     * =============================================================
     * UI MESSAGE
     * =============================================================
     */

    private fun openSubscriptionManagement() {
        val url =
            "https://play.google.com/store/account/subscriptions" +
                    "?sku=muhafiz_monthly&package=$packageName"

        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        } catch (_: Exception) {
            showToast(
                message = "Google Play abonelik yönetimi açılamadı.",
                long = true
            )
        }
    }


    private fun openPrivacyPolicy() {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(PRIVACY_POLICY_URL)
                )
            )
        } catch (_: Exception) {
            showToast(
                message = "Gizlilik politikası açılamadı.",
                long = true
            )
        }
    }


    private fun showToast(
        message: String,
        long: Boolean = false
    ) {

        if (message.isBlank()) {
            return
        }

        Toast.makeText(
            this,
            message,
            if (long) {
                Toast.LENGTH_LONG
            } else {
                Toast.LENGTH_SHORT
            }
        ).show()
    }
}


/*
 * =============================================================
 * MUHAFIZ UI
 * =============================================================
 */

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
    hasDeveloperAccess: Boolean,
    isBillingReady: Boolean,
    subscriptionPrice: String?,

    onSubscribeClick: () -> Unit,
    onManageSubscriptionClick: () -> Unit,
    onPrivacyPolicyClick: () -> Unit,
    onShowMessage: (String) -> Unit,

    onRequestProtectionStart: () -> Unit,
    onRequestProtectionStop: () -> Unit,

    onDeveloperAccessSubmit: (String) -> Boolean,
    onDeveloperAccessDisable: () -> Unit
) {

    var hasPin by remember {
        mutableStateOf(
            parentLockManager.hasPin()
        )
    }

    var currentScreen by remember {
        mutableStateOf(
            if (hasPin) {
                MuhafizScreen.HOME
            } else {
                MuhafizScreen.PIN_SETUP
            }
        )
    }


    /*
     * Dialog state'leri.
     */
    var showStopProtectionDialog by remember {
        mutableStateOf(false)
    }

    var showSettingsPinDialog by remember {
        mutableStateOf(false)
    }

    var showResetPinDialog by remember {
        mutableStateOf(false)
    }

    var showScreenAnalysisDisclosure by remember {
        mutableStateOf(false)
    }


    when (currentScreen) {

        /*
         * =========================================================
         * PIN SETUP
         * =========================================================
         */

        MuhafizScreen.PIN_SETUP -> {

            PinSetupScreen(

                onSavePin = { pin ->

                    val saved =
                        parentLockManager.savePin(
                            pin
                        )

                    if (saved) {

                        hasPin = true

                        currentScreen =
                            MuhafizScreen.HOME

                        onShowMessage(
                            "Ebeveyn PIN'i kaydedildi."
                        )

                    } else {

                        onShowMessage(
                            "PIN kaydedilemedi."
                        )
                    }
                }
            )
        }


        /*
         * =========================================================
         * HOME
         * =========================================================
         */

        MuhafizScreen.HOME -> {

            HomeScreen(

                hasPin =
                    hasPin,

                isProtectionRunning =
                    isProtectionRunning,

                isSubscribed =
                    isSubscribed,

                hasDeveloperAccess =
                    hasDeveloperAccess,

                isBillingReady =
                    isBillingReady,

                subscriptionPrice =
                    subscriptionPrice,


                /*
                 * PIN kurulumu.
                 */
                onSetupPinClick = {

                    currentScreen =
                        MuhafizScreen.PIN_SETUP
                },


                /*
                 * Koruma aç / kapa.
                 */
                onProtectionToggleClick = {

                    if (!isProtectionRunning) {

                        showScreenAnalysisDisclosure =
                            true

                    } else {

                        showStopProtectionDialog =
                            true
                    }
                },


                /*
                 * Abonelik.
                 */
                onSubscribeClick =
                    onSubscribeClick,


                /*
                 * Ayarlar.
                 */
                onSettingsClick = {

                    showSettingsPinDialog =
                        true
                }
            )


            if (showScreenAnalysisDisclosure) {

                AlertDialog(
                    onDismissRequest = {
                        showScreenAnalysisDisclosure = false
                    },
                    title = {
                        Text("Ekran Analizi Hakkında")
                    },
                    text = {
                        Text(
                            "Muhafız, koruma açıkken ekranda görüntülenen içeriği analiz eder. " +
                                    "Ekran görüntüleri yalnızca cihaz üzerinde anlık olarak işlenir; " +
                                    "kaydedilmez, sunucuya gönderilmez ve üçüncü taraflarla paylaşılmaz. " +
                                    "Korumanın çalışması için ekran yakalama ve ekran üzerinde gösterim izinleri gerekir."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showScreenAnalysisDisclosure = false
                                onRequestProtectionStart()
                            }
                        ) {
                            Text("Anladım ve Devam Et")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showScreenAnalysisDisclosure = false
                            }
                        ) {
                            Text("Vazgeç")
                        }
                    }
                )
            }


            /*
             * -----------------------------------------------------
             * STOP PROTECTION PIN
             * -----------------------------------------------------
             */

            if (showStopProtectionDialog) {

                PinVerificationDialog(

                    title =
                        "Korumayı Durdur",

                    message =
                        "Muhafız korumasını durdurmak için " +
                                "ebeveyn PIN'ini girin.",

                    onDismiss = {

                        showStopProtectionDialog =
                            false
                    },

                    onVerify = { pin ->

                        if (
                            parentLockManager.verifyPin(
                                pin
                            )
                        ) {

                            showStopProtectionDialog =
                                false

                            onRequestProtectionStop()

                            onShowMessage(
                                "Muhafız koruması durduruldu."
                            )

                        } else {

                            onShowMessage(
                                "PIN doğrulanamadı."
                            )
                        }
                    }
                )
            }


            /*
             * -----------------------------------------------------
             * SETTINGS PIN
             * -----------------------------------------------------
             */

            if (showSettingsPinDialog) {

                PinVerificationDialog(

                    title =
                        "Ayarlar",

                    message =
                        "Muhafız ayarlarına girmek için " +
                                "ebeveyn PIN'ini girin.",

                    onDismiss = {

                        showSettingsPinDialog =
                            false
                    },

                    onVerify = { pin ->

                        if (
                            parentLockManager.verifyPin(
                                pin
                            )
                        ) {

                            showSettingsPinDialog =
                                false

                            currentScreen =
                                MuhafizScreen.SETTINGS

                        } else {

                            onShowMessage(
                                "PIN doğrulanamadı."
                            )
                        }
                    }
                )
            }
        }


        /*
         * =========================================================
         * SETTINGS
         * =========================================================
         */

        MuhafizScreen.SETTINGS -> {

            SettingsScreen(

                isDeveloperAccessEnabled =
                    hasDeveloperAccess,


                onBackClick = {

                    currentScreen =
                        MuhafizScreen.HOME
                },


                onResetPinClick = {

                    showResetPinDialog =
                        true
                },

                onManageSubscriptionClick =
                    onManageSubscriptionClick,

                onPrivacyPolicyClick =
                    onPrivacyPolicyClick,


                onDeveloperAccessSubmit =
                    onDeveloperAccessSubmit,


                onDeveloperAccessDisable =
                    onDeveloperAccessDisable
            )


            /*
             * -----------------------------------------------------
             * RESET PIN
             * -----------------------------------------------------
             */

            if (showResetPinDialog) {

                PinVerificationDialog(

                    title =
                        "PIN'i Sıfırla",

                    message =
                        "Ebeveyn PIN'ini sıfırlamak için " +
                                "mevcut PIN'i girin.",

                    onDismiss = {

                        showResetPinDialog =
                            false
                    },

                    onVerify = { pin ->

                        if (
                            parentLockManager.verifyPin(
                                pin
                            )
                        ) {

                            val cleared =
                                parentLockManager.clearPin()

                            if (cleared) {

                                showResetPinDialog =
                                    false

                                hasPin =
                                    false

                                currentScreen =
                                    MuhafizScreen.PIN_SETUP

                                onShowMessage(
                                    "PIN sıfırlandı."
                                )

                            } else {

                                onShowMessage(
                                    "PIN sıfırlanamadı."
                                )
                            }

                        } else {

                            onShowMessage(
                                "PIN doğrulanamadı."
                            )
                        }
                    }
                )
            }
        }
    }
}


/*
 * =============================================================
 * PIN VERIFICATION DIALOG
 * =============================================================
 */

@Composable
private fun PinVerificationDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onVerify: (String) -> Unit
) {

    var pin by remember {
        mutableStateOf("")
    }

    var errorText by remember {
        mutableStateOf<String?>(null)
    }


    AlertDialog(

        onDismissRequest =
            onDismiss,


        title = {

            Text(
                text = title
            )
        },


        text = {

            Column(

                verticalArrangement =
                    Arrangement.spacedBy(
                        DialogContentGap
                    )

            ) {

                Text(
                    text = message,
                    style =
                        MaterialTheme.typography.bodyMedium
                )


                OutlinedTextField(

                    value =
                        pin,

                    onValueChange = { value ->

                        pin =
                            value.onlyDigits(
                                maxLength =
                                    MAX_PIN_LENGTH
                            )

                        errorText =
                            null
                    },

                    label = {

                        Text(
                            "Ebeveyn PIN'i"
                        )
                    },

                    singleLine =
                        true,

                    visualTransformation =
                        PasswordVisualTransformation(),

                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType =
                                KeyboardType.NumberPassword
                        ),

                    modifier =
                        Modifier.fillMaxWidth()
                )


                if (errorText != null) {

                    Text(

                        text =
                            errorText.orEmpty(),

                        color =
                            MaterialTheme
                                .colorScheme
                                .error,

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }


                Spacer(

                    modifier =
                        Modifier.height(
                            DialogBottomGap
                        )
                )
            }
        },


        confirmButton = {

            TextButton(

                onClick = {

                    if (
                        pin.length <
                        MIN_PIN_LENGTH
                    ) {

                        errorText =
                            "Geçerli bir PIN girin."

                    } else {

                        onVerify(
                            pin
                        )
                    }
                }
            ) {

                Text(
                    "Doğrula"
                )
            }
        },


        dismissButton = {

            TextButton(
                onClick =
                    onDismiss
            ) {

                Text(
                    "Vazgeç"
                )
            }
        }
    )
}


/*
 * =============================================================
 * STRING HELPERS
 * =============================================================
 */

private fun String.onlyDigits(
    maxLength: Int
): String {

    return filter { character ->
        character.isDigit()
    }.take(
        maxLength
    )
}


/*
 * =============================================================
 * UI CONSTANTS
 * =============================================================
 */

private val DialogContentGap =
    12.dp

private val DialogBottomGap =
    2.dp

private const val MIN_PIN_LENGTH =
    4

private const val MAX_PIN_LENGTH =
    6

private const val PRIVACY_POLICY_URL =
    "https://tunahandso.github.io/muhafiz/"
