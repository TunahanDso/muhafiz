package com.tunix.nazar.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.lang.ref.WeakReference

class BillingManager(
    context: Context,

    /*
     * Kullanıcının aktif abonelik hakkı değiştiğinde çağrılır.
     */
    private val onSubscriptionStatusChanged: (Boolean) -> Unit,

    /*
     * Billing servisi gerçekten kullanılabilir olduğunda çağrılır.
     *
     * Default callback sayesinde eski constructor kullanımı
     * derlenmeye devam eder.
     */
    private val onBillingReadyChanged: (Boolean) -> Unit = {},

    /*
     * Google Play'den gelen yerelleştirilmiş abonelik fiyatı.
     */
    private val onSubscriptionPriceChanged: (String?) -> Unit = {},

    /*
     * Satın alma sırasında oluşan kullanıcıya gösterilebilir
     * mesajları Activity'ye gönderir.
     *
     * Böylece hiçbir hata sessizce return edilmez.
     */
    private val onUserMessage: (String) -> Unit = {}
) : PurchasesUpdatedListener {

    /*
     * =============================================================
     * CONTEXT / THREAD
     * =============================================================
     */

    /*
     * Activity context'i saklamıyoruz.
     * BillingClient için application context yeterlidir.
     */
    private val appContext: Context =
        context.applicationContext

    /*
     * UI callback'leri ana thread üzerinde çalıştırılır.
     */
    private val mainHandler =
        Handler(Looper.getMainLooper())

    /*
     * Aynı anda birden fazla bağlantı kurulmasını önler.
     */
    private val connectionLock = Any()


    /*
     * =============================================================
     * BILLING STATE
     * =============================================================
     */

    /*
     * Google, uygulama başına aynı anda tek BillingClient
     * kullanılmasını önerir.
     */
    private val billingClient: BillingClient =
        createBillingClient()

    @Volatile
    private var isConnecting: Boolean = false

    @Volatile
    private var isClosed: Boolean = false

    @Volatile
    private var purchaseFlowInProgress: Boolean = false


    /*
     * =============================================================
     * PENDING PURCHASE
     * =============================================================
     *
     * Kullanıcı satın alma butonuna Billing henüz hazır değilken
     * basarsa Activity'nin güçlü referansını tutmuyoruz.
     *
     * Bağlantı tamamlandığında satın alma akışı otomatik devam eder.
     */
    private var pendingPurchaseActivity:
            WeakReference<Activity>? = null


    /*
     * =============================================================
     * LAST REPORTED STATES
     * =============================================================
     */

    private var lastReportedSubscriptionState:
            Boolean? = null

    private var lastReportedBillingReadyState:
            Boolean? = null


    /*
     * =============================================================
     * PRODUCT
     * =============================================================
     */

    /*
     * Play Console'da YENİ uygulama altında ayrıca oluşturulmalıdır.
     *
     * Package:
     * com.tunix.muhafiz.guard
     *
     * Product:
     * muhafiz_monthly
     */
    private val productId =
        PRODUCT_ID


    /*
     * =============================================================
     * PUBLIC API
     * =============================================================
     */

    /**
     * Google Play Billing bağlantısını başlatır.
     *
     * Fonksiyon tekrar tekrar çağrılsa bile eş zamanlı olarak
     * ikinci bir bağlantı başlatılmaz.
     */
    fun startConnection() {
        if (isClosed) {
            Log.w(
                TAG,
                "startConnection çağrısı görmezden gelindi: BillingManager kapalı."
            )
            return
        }

        synchronized(connectionLock) {

            /*
             * Zaten hazırsa yeni connection açma.
             */
            if (billingClient.isReady) {
                isConnecting = false

                dispatchBillingReady(true)

                queryPurchases()

                continuePendingPurchaseIfPossible()

                return
            }

            /*
             * Başka bir bağlantı işlemi sürüyorsa bekle.
             */
            if (isConnecting) {
                Log.d(
                    TAG,
                    "Billing bağlantısı zaten kuruluyor."
                )
                return
            }

            isConnecting = true

            dispatchBillingReady(false)

            try {
                billingClient.startConnection(
                    object : BillingClientStateListener {

                        override fun onBillingSetupFinished(
                            result: BillingResult
                        ) {
                            isConnecting = false

                            if (isClosed) {
                                return
                            }

                            Log.d(
                                TAG,
                                buildString {
                                    append("Billing kurulumu tamamlandı. ")
                                    append("code=")
                                    append(result.responseCode)
                                    append(", message=")
                                    append(result.debugMessage)
                                }
                            )

                            if (
                                result.responseCode ==
                                BillingClient.BillingResponseCode.OK
                            ) {
                                /*
                                 * Billing gerçekten hazır.
                                 */
                                dispatchBillingReady(true)

                                /*
                                 * Önce mevcut aboneliği doğrula.
                                 */
                                queryPurchases()
                                querySubscriptionPrice()

                                /*
                                 * Kullanıcı bağlantı kurulmadan önce
                                 * satın alma düğmesine bastıysa devam et.
                                 */
                                continuePendingPurchaseIfPossible()
                            } else {
                                dispatchBillingReady(false)

                                /*
                                 * Burada aboneliği false yapmıyoruz.
                                 *
                                 * Geçici Play Store / ağ problemi,
                                 * kullanıcının mevcut hakkını yanlışlıkla
                                 * kapatmamalı.
                                 */
                                dispatchUserMessage(
                                    billingMessageFor(
                                        billingResult = result,
                                        fallback =
                                            "Google Play faturalandırma servisine bağlanılamadı."
                                    )
                                )

                                purchaseFlowInProgress = false
                                clearPendingPurchase()
                            }
                        }

                        override fun onBillingServiceDisconnected() {
                            isConnecting = false

                            dispatchBillingReady(false)

                            Log.w(
                                TAG,
                                "Google Play Billing servisi bağlantısı kesildi."
                            )

                            /*
                             * enableAutoServiceReconnection() açık olduğu için
                             * sonraki Billing API çağrısında sistem yeniden
                             * bağlanmayı deneyebilir.
                             *
                             * Kullanıcının mevcut aboneliğini burada false
                             * yapmıyoruz.
                             */
                        }
                    }
                )
            } catch (throwable: Throwable) {
                isConnecting = false

                dispatchBillingReady(false)

                Log.e(
                    TAG,
                    "Billing bağlantısı başlatılırken hata oluştu.",
                    throwable
                )

                dispatchUserMessage(
                    "Google Play bağlantısı başlatılamadı. " +
                            "Lütfen tekrar deneyin."
                )

                purchaseFlowInProgress = false
                clearPendingPurchase()
            }
        }
    }


    /**
     * Abonelik satın alma sürecini başlatır.
     */
    fun purchase(activity: Activity) {
        if (isClosed) {
            Log.w(
                TAG,
                "Satın alma başlatılamadı: BillingManager kapalı."
            )

            dispatchUserMessage(
                "Satın alma servisi şu anda kullanılamıyor."
            )

            return
        }

        if (!isActivityUsable(activity)) {
            Log.w(
                TAG,
                "Satın alma başlatılamadı: Activity kullanılabilir değil."
            )

            dispatchUserMessage(
                "Satın alma ekranı açılamadı. " +
                        "Lütfen tekrar deneyin."
            )

            return
        }

        /*
         * Kullanıcının hızlı şekilde birkaç kez butona basıp
         * birden fazla Billing akışı oluşturmasını önler.
         */
        if (purchaseFlowInProgress) {
            dispatchUserMessage(
                "Satın alma işlemi hazırlanıyor."
            )
            return
        }

        purchaseFlowInProgress = true

        pendingPurchaseActivity =
            WeakReference(activity)

        if (billingClient.isReady) {
            dispatchBillingReady(true)

            querySubscriptionProduct(activity)
        } else {
            /*
             * Kullanıcıya butonun çalıştığını bildiriyoruz.
             *
             * Eski uygulamadaki sessiz return problemi burada yok.
             */
            dispatchUserMessage(
                "Google Play bağlantısı hazırlanıyor…"
            )

            startConnection()
        }
    }


    /**
     * Uygulama tekrar ön plana geldiğinde mevcut satın alımları
     * yeniden doğrulamak için çağrılabilir.
     */
    fun refreshPurchases() {
        if (isClosed) {
            return
        }

        if (billingClient.isReady) {
            dispatchBillingReady(true)

            queryPurchases()
            querySubscriptionPrice()
        } else {
            dispatchBillingReady(false)

            startConnection()
        }
    }


    /**
     * UI gerektiğinde Billing'in gerçek hazır durumunu okuyabilir.
     */
    fun isReady(): Boolean {
        return !isClosed && billingClient.isReady
    }


    /*
     * =============================================================
     * BILLING CLIENT
     * =============================================================
     */

    private fun createBillingClient(): BillingClient {

        /*
         * Billing Library 7+ parametreli pending purchases API'sini
         * kullanır.
         *
         * Muhafız şu anda auto-renewing subscription kullanıyor.
         * Prepaid subscription desteği açılmıyor.
         */
        val pendingPurchasesParams =
            PendingPurchasesParams
                .newBuilder()
                .enableOneTimeProducts()
                .build()

        return BillingClient
            .newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(
                pendingPurchasesParams
            )

            /*
             * Billing Library 8+.
             *
             * Servis bağlantısı kopmuşsa sonraki API çağrısından
             * önce otomatik bağlantı kurmayı deneyebilir.
             */
            .enableAutoServiceReconnection()
            .build()
    }


    /*
     * =============================================================
     * PRODUCT / PRICE QUERY
     * =============================================================
     */

    private fun querySubscriptionPrice() {
        if (isClosed || !billingClient.isReady) {
            return
        }

        val product =
            QueryProductDetailsParams.Product
                .newBuilder()
                .setProductId(productId)
                .setProductType(
                    BillingClient.ProductType.SUBS
                )
                .build()

        val params =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    listOf(product)
                )
                .build()

        try {
            billingClient.queryProductDetailsAsync(
                params
            ) { billingResult, queryResult ->

                if (isClosed) {
                    return@queryProductDetailsAsync
                }

                if (
                    billingResult.responseCode !=
                    BillingClient.BillingResponseCode.OK
                ) {
                    Log.w(
                        TAG,
                        "Abonelik fiyatı alınamadı: " +
                                billingResult.debugMessage
                    )
                    return@queryProductDetailsAsync
                }

                val productDetails =
                    queryResult
                        .productDetailsList
                        .firstOrNull { details ->
                            details.productId == productId
                        }

                if (productDetails == null) {
                    dispatchSubscriptionPrice(null)
                    return@queryProductDetailsAsync
                }

                val offers =
                    productDetails
                        .subscriptionOfferDetails
                        .orEmpty()

                val selectedOffer =
                    offers.firstOrNull { offer ->
                        offer.offerId == null
                    } ?: offers.firstOrNull()

                val recurringPrice =
                    selectedOffer
                        ?.pricingPhases
                        ?.pricingPhaseList
                        ?.lastOrNull()
                        ?.formattedPrice

                dispatchSubscriptionPrice(
                    recurringPrice
                )
            }
        } catch (throwable: Throwable) {
            Log.w(
                TAG,
                "Abonelik fiyatı sorgulanırken hata oluştu.",
                throwable
            )
        }
    }


    /*
     * =============================================================
     * PRODUCT QUERY
     * =============================================================
     */

    private fun querySubscriptionProduct(
        activity: Activity
    ) {
        if (isClosed) {
            failPurchaseFlow(
                "Satın alma servisi kapatılmış."
            )
            return
        }

        if (!isActivityUsable(activity)) {
            failPurchaseFlow(
                "Satın alma ekranı artık kullanılamıyor."
            )
            return
        }

        if (!billingClient.isReady) {
            Log.w(
                TAG,
                "Ürün bilgisi sorgulanamadı: Billing hazır değil."
            )

            /*
             * Sessiz return YOK.
             *
             * Pending purchase korunarak yeniden bağlantı denenir.
             */
            dispatchBillingReady(false)

            startConnection()

            return
        }

        val product =
            QueryProductDetailsParams.Product
                .newBuilder()
                .setProductId(productId)
                .setProductType(
                    BillingClient.ProductType.SUBS
                )
                .build()

        val params =
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    listOf(product)
                )
                .build()

        try {
            billingClient.queryProductDetailsAsync(
                params
            ) { billingResult, queryResult ->

                if (isClosed) {
                    return@queryProductDetailsAsync
                }

                Log.d(
                    TAG,
                    buildString {
                        append("Ürün sorgusu tamamlandı. ")
                        append("code=")
                        append(billingResult.responseCode)
                        append(", message=")
                        append(billingResult.debugMessage)
                    }
                )

                /*
                 * Billing 8/9 ile gelen QueryProductDetailsResult,
                 * getirilemeyen ürünleri ayrıca bildirir.
                 */
                queryResult
                    .unfetchedProductList
                    .forEach { unfetchedProduct ->

                        Log.w(
                            TAG,
                            buildString {
                                append("Ürün getirilemedi. ")
                                append("productId=")
                                append(
                                    unfetchedProduct.productId
                                )
                                append(", productType=")
                                append(
                                    unfetchedProduct.productType
                                )
                                append(", statusCode=")
                                append(
                                    unfetchedProduct.statusCode
                                )
                            }
                        )
                    }

                if (
                    billingResult.responseCode !=
                    BillingClient.BillingResponseCode.OK
                ) {
                    failPurchaseFlow(
                        billingMessageFor(
                            billingResult = billingResult,
                            fallback =
                                "Abonelik bilgileri alınamadı. " +
                                        "Lütfen tekrar deneyin."
                        )
                    )

                    return@queryProductDetailsAsync
                }

                val productDetails =
                    queryResult
                        .productDetailsList
                        .firstOrNull { details ->
                            details.productId == productId
                        }

                if (productDetails == null) {

                    Log.e(
                        TAG,
                        "Abonelik ürünü bulunamadı. " +
                                "Play Console'da '$productId' ürününü, " +
                                "base planı ve yeni package adını kontrol edin."
                    )

                    failPurchaseFlow(
                        "Abonelik ürünü şu anda kullanılamıyor."
                    )

                    return@queryProductDetailsAsync
                }

                /*
                 * launchBillingFlow UI üzerinde açılacağı için
                 * main thread'e dönüyoruz.
                 */
                mainHandler.post {

                    if (
                        !isClosed &&
                        isActivityUsable(activity)
                    ) {
                        launchSubscriptionFlow(
                            activity = activity,
                            productDetails = productDetails
                        )
                    } else {
                        failPurchaseFlow(
                            "Satın alma ekranı açılamadı."
                        )
                    }
                }
            }
        } catch (throwable: Throwable) {
            Log.e(
                TAG,
                "Abonelik ürün bilgisi sorgulanırken hata oluştu.",
                throwable
            )

            failPurchaseFlow(
                "Abonelik bilgileri alınırken bir sorun oluştu."
            )
        }
    }


    /*
     * =============================================================
     * PURCHASE FLOW
     * =============================================================
     */

    private fun launchSubscriptionFlow(
        activity: Activity,
        productDetails: ProductDetails
    ) {
        if (
            isClosed ||
            !billingClient.isReady ||
            !isActivityUsable(activity)
        ) {
            Log.w(
                TAG,
                "Satın alma ekranı açılamadı: Billing veya Activity hazır değil."
            )

            failPurchaseFlow(
                "Satın alma ekranı açılamadı. " +
                        "Lütfen tekrar deneyin."
            )

            return
        }

        /*
         * Kullanıcı için uygun tüm abonelik teklifleri.
         */
        val offers =
            productDetails.subscriptionOfferDetails
                .orEmpty()

        if (offers.isEmpty()) {
            Log.e(
                TAG,
                "Abonelik için uygun offer/base plan bulunamadı."
            )

            failPurchaseFlow(
                "Bu hesap için kullanılabilir bir abonelik planı bulunamadı."
            )

            return
        }

        /*
         * Öncelik:
         *
         * 1. İndirimsiz normal base plan (offerId == null)
         * 2. Kullanıcının uygun olduğu ilk teklif
         *
         * Böylece sırf listede ilk geldiği için yanlışlıkla
         * promosyon offer'ı seçilmez.
         */
        val selectedOffer =
            offers.firstOrNull { offer ->
                offer.offerId == null
            } ?: offers.first()

        val offerToken =
            selectedOffer.offerToken

        if (offerToken.isBlank()) {
            Log.e(
                TAG,
                "Subscription offerToken boş."
            )

            failPurchaseFlow(
                "Abonelik planı başlatılamadı."
            )

            return
        }

        Log.d(
            TAG,
            buildString {
                append("Abonelik teklifi seçildi. ")
                append("basePlanId=")
                append(selectedOffer.basePlanId)
                append(", offerId=")
                append(selectedOffer.offerId)
            }
        )

        val productDetailsParams =
            BillingFlowParams.ProductDetailsParams
                .newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()

        val billingFlowParams =
            BillingFlowParams
                .newBuilder()
                .setProductDetailsParamsList(
                    listOf(productDetailsParams)
                )
                .build()

        try {
            val result =
                billingClient.launchBillingFlow(
                    activity,
                    billingFlowParams
                )

            Log.d(
                TAG,
                buildString {
                    append("launchBillingFlow sonucu. ")
                    append("code=")
                    append(result.responseCode)
                    append(", message=")
                    append(result.debugMessage)
                }
            )

            /*
             * launchBillingFlow sadece akışın başarıyla
             * AÇILIP AÇILAMADIĞINI bildirir.
             *
             * Gerçek satın alma sonucu onPurchasesUpdated()
             * callback'inden gelir.
             */
            if (
                result.responseCode !=
                BillingClient.BillingResponseCode.OK
            ) {
                if (
                    result.responseCode ==
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED
                ) {
                    dispatchUserMessage(
                        "Bu abonelik zaten hesabınızda mevcut. " +
                                "Durum doğrulanıyor…"
                    )

                    purchaseFlowInProgress = false
                    clearPendingPurchase()

                    queryPurchases()
                } else {
                    failPurchaseFlow(
                        billingMessageFor(
                            billingResult = result,
                            fallback =
                                "Satın alma ekranı açılamadı."
                        )
                    )
                }
            }
        } catch (throwable: Throwable) {
            Log.e(
                TAG,
                "Satın alma ekranı açılırken hata oluştu.",
                throwable
            )

            failPurchaseFlow(
                "Satın alma ekranı açılırken bir sorun oluştu."
            )
        }
    }


    /*
     * =============================================================
     * PURCHASE RESULT
     * =============================================================
     */

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (isClosed) {
            return
        }

        /*
         * Artık pending UI transaction tamamlandı veya sonlandı.
         */
        purchaseFlowInProgress = false
        clearPendingPurchase()

        Log.d(
            TAG,
            buildString {
                append("Satın alma güncellemesi. ")
                append("code=")
                append(billingResult.responseCode)
                append(", subCode=")
                append(
                    billingResult.onPurchasesUpdatedSubResponseCode
                )
                append(", message=")
                append(billingResult.debugMessage)
            }
        )

        when (billingResult.responseCode) {

            BillingClient.BillingResponseCode.OK -> {

                if (purchases.isNullOrEmpty()) {
                    /*
                     * OK fakat purchase yok.
                     *
                     * Yeni hak vermiyoruz ancak mevcut doğrulanmış
                     * state'i de körlemesine false yapmıyoruz.
                     */
                    Log.w(
                        TAG,
                        "Billing OK döndü fakat purchase listesi boş."
                    )

                    queryPurchases()
                } else {
                    handlePurchases(purchases)
                }
            }


            BillingClient.BillingResponseCode.USER_CANCELED -> {
                dispatchUserMessage(
                    "Satın alma işlemi iptal edildi."
                )
            }


            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                dispatchUserMessage(
                    "Abonelik zaten hesabınızda mevcut. " +
                            "Durum doğrulanıyor…"
                )

                queryPurchases()
            }


            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED -> {
                dispatchBillingReady(false)

                dispatchUserMessage(
                    "Google Play bağlantısı kesildi. " +
                            "Lütfen tekrar deneyin."
                )

                startConnection()
            }


            BillingClient.BillingResponseCode.NETWORK_ERROR -> {
                dispatchUserMessage(
                    "İnternet bağlantısı nedeniyle satın alma tamamlanamadı."
                )
            }


            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> {
                dispatchUserMessage(
                    "Google Play faturalandırma servisi geçici olarak kullanılamıyor."
                )
            }


            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                dispatchUserMessage(
                    "Google Play faturalandırma bu cihazda veya hesapta kullanılamıyor."
                )
            }


            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                dispatchUserMessage(
                    "Abonelik ürünü şu anda kullanılamıyor."
                )
            }


            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                /*
                 * Genellikle package / product / base plan /
                 * Play Console yapılandırması problemi.
                 */
                Log.e(
                    TAG,
                    "Billing DEVELOPER_ERROR: ${billingResult.debugMessage}"
                )

                dispatchUserMessage(
                    "Satın alma yapılandırması doğrulanamadı."
                )
            }


            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                dispatchUserMessage(
                    "Bu cihazdaki Google Play sürümü satın alma özelliğini desteklemiyor."
                )
            }


            else -> {

                /*
                 * Billing 9 ile onPurchasesUpdated alt hata kodları
                 * daha ayrıntılı sebep verebilir.
                 */
                val subResponseMessage =
                    messageForPurchaseSubResponse(
                        billingResult
                    )

                if (subResponseMessage != null) {
                    dispatchUserMessage(
                        subResponseMessage
                    )
                } else {
                    dispatchUserMessage(
                        billingMessageFor(
                            billingResult = billingResult,
                            fallback =
                                "Satın alma tamamlanamadı. " +
                                        "Lütfen tekrar deneyin."
                        )
                    )
                }
            }
        }
    }


    /*
     * =============================================================
     * OWNED PURCHASES
     * =============================================================
     */

    private fun queryPurchases() {
        if (isClosed) {
            return
        }

        if (!billingClient.isReady) {
            Log.w(
                TAG,
                "Satın alımlar sorgulanamadı: Billing hazır değil."
            )

            dispatchBillingReady(false)

            return
        }

        val params =
            QueryPurchasesParams
                .newBuilder()
                .setProductType(
                    BillingClient.ProductType.SUBS
                )
                .build()

        try {
            billingClient.queryPurchasesAsync(
                params
            ) { billingResult, purchases ->

                if (isClosed) {
                    return@queryPurchasesAsync
                }

                Log.d(
                    TAG,
                    buildString {
                        append("Mevcut satın alımlar sorgulandı. ")
                        append("code=")
                        append(billingResult.responseCode)
                        append(", count=")
                        append(purchases.size)
                        append(", message=")
                        append(billingResult.debugMessage)
                    }
                )

                if (
                    billingResult.responseCode ==
                    BillingClient.BillingResponseCode.OK
                ) {
                    dispatchBillingReady(true)

                    /*
                     * Başarılı query + boş liste:
                     * aktif abonelik yok.
                     */
                    handlePurchases(purchases)
                } else {

                    /*
                     * Geçici hata mevcut doğrulanmış abonelik durumunu
                     * false yapmaz.
                     */
                    if (
                        billingResult.responseCode ==
                        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED
                    ) {
                        dispatchBillingReady(false)
                    }

                    Log.w(
                        TAG,
                        "Abonelik durumu doğrulanamadı: " +
                                billingResult.debugMessage
                    )
                }
            }
        } catch (throwable: Throwable) {
            Log.e(
                TAG,
                "Mevcut satın alımlar sorgulanırken hata oluştu.",
                throwable
            )
        }
    }


    /*
     * =============================================================
     * ENTITLEMENT
     * =============================================================
     */

    private fun handlePurchases(
        purchases: List<Purchase>
    ) {
        if (isClosed) {
            return
        }

        val expectedPurchases =
            purchases.filter { purchase ->
                purchase.products.contains(productId)
            }

        /*
         * Aktif hak yalnızca PURCHASED durumunda verilir.
         *
         * PENDING satın alma abonelik hakkı açmaz.
         */
        val activePurchase =
            expectedPurchases
                .firstOrNull { purchase ->
                    purchase.purchaseState ==
                            Purchase.PurchaseState.PURCHASED
                }

        val pendingPurchase =
            expectedPurchases
                .firstOrNull { purchase ->
                    purchase.purchaseState ==
                            Purchase.PurchaseState.PENDING
                }

        val isSubscribed =
            activePurchase != null

        dispatchSubscriptionStatus(
            isSubscribed
        )

        when {

            activePurchase != null -> {

                if (!activePurchase.isAcknowledged) {
                    acknowledgePurchase(
                        activePurchase
                    )
                }

                if (purchaseFlowInProgress) {
                    dispatchUserMessage(
                        "Abonelik etkinleştirildi."
                    )
                }
            }


            pendingPurchase != null -> {
                dispatchUserMessage(
                    "Ödeme işlemi beklemede. " +
                            "Google Play ödemeyi tamamladığında abonelik etkinleşecek."
                )
            }
        }
    }


    /*
     * =============================================================
     * ACKNOWLEDGE
     * =============================================================
     */

    private fun acknowledgePurchase(
        purchase: Purchase
    ) {
        if (
            isClosed ||
            !billingClient.isReady ||
            purchase.isAcknowledged ||
            purchase.purchaseState !=
            Purchase.PurchaseState.PURCHASED
        ) {
            return
        }

        val acknowledgeParams =
            AcknowledgePurchaseParams
                .newBuilder()
                .setPurchaseToken(
                    purchase.purchaseToken
                )
                .build()

        try {
            billingClient.acknowledgePurchase(
                acknowledgeParams
            ) { billingResult ->

                if (isClosed) {
                    return@acknowledgePurchase
                }

                Log.d(
                    TAG,
                    buildString {
                        append("Satın alma acknowledge sonucu. ")
                        append("code=")
                        append(billingResult.responseCode)
                        append(", message=")
                        append(billingResult.debugMessage)
                    }
                )

                if (
                    billingResult.responseCode !=
                    BillingClient.BillingResponseCode.OK
                ) {
                    /*
                     * Hak hemen kapatılmaz.
                     *
                     * Bir sonraki refresh/query sırasında acknowledge
                     * yeniden denenebilir.
                     */
                    Log.e(
                        TAG,
                        "Abonelik acknowledge başarısız: " +
                                billingResult.debugMessage
                    )
                }
            }
        } catch (throwable: Throwable) {
            Log.e(
                TAG,
                "Satın alma acknowledge edilirken hata oluştu.",
                throwable
            )
        }
    }


    /*
     * =============================================================
     * PENDING PURCHASE CONTINUATION
     * =============================================================
     */

    private fun continuePendingPurchaseIfPossible() {
        if (isClosed) {
            return
        }

        val activity =
            pendingPurchaseActivity?.get()

        if (
            activity == null ||
            !isActivityUsable(activity)
        ) {
            purchaseFlowInProgress = false
            clearPendingPurchase()
            return
        }

        if (!billingClient.isReady) {
            return
        }

        querySubscriptionProduct(
            activity
        )
    }


    /*
     * =============================================================
     * STATE CALLBACKS
     * =============================================================
     */

    private fun dispatchSubscriptionStatus(
        isSubscribed: Boolean
    ) {
        if (isClosed) {
            return
        }

        if (
            lastReportedSubscriptionState ==
            isSubscribed
        ) {
            return
        }

        lastReportedSubscriptionState =
            isSubscribed

        mainHandler.post {
            if (isClosed) {
                return@post
            }

            try {
                onSubscriptionStatusChanged(
                    isSubscribed
                )
            } catch (throwable: Throwable) {
                Log.e(
                    TAG,
                    "Abonelik callback'i çalıştırılırken hata oluştu.",
                    throwable
                )
            }
        }
    }


    private fun dispatchSubscriptionPrice(
        formattedPrice: String?
    ) {
        if (isClosed) {
            return
        }

        mainHandler.post {
            if (isClosed) {
                return@post
            }

            try {
                onSubscriptionPriceChanged(
                    formattedPrice
                )
            } catch (throwable: Throwable) {
                Log.e(
                    TAG,
                    "Abonelik fiyat callback'i çalıştırılırken hata oluştu.",
                    throwable
                )
            }
        }
    }


    private fun dispatchBillingReady(
        isReady: Boolean
    ) {
        if (isClosed) {
            return
        }

        if (
            lastReportedBillingReadyState ==
            isReady
        ) {
            return
        }

        lastReportedBillingReadyState =
            isReady

        mainHandler.post {
            if (isClosed) {
                return@post
            }

            try {
                onBillingReadyChanged(
                    isReady
                )
            } catch (throwable: Throwable) {
                Log.e(
                    TAG,
                    "Billing ready callback'i çalıştırılırken hata oluştu.",
                    throwable
                )
            }
        }
    }


    private fun dispatchUserMessage(
        message: String
    ) {
        if (
            isClosed ||
            message.isBlank()
        ) {
            return
        }

        mainHandler.post {
            if (isClosed) {
                return@post
            }

            try {
                onUserMessage(
                    message
                )
            } catch (throwable: Throwable) {
                Log.e(
                    TAG,
                    "Billing kullanıcı mesajı callback'i çalıştırılırken hata oluştu.",
                    throwable
                )
            }
        }
    }


    /*
     * =============================================================
     * ERROR HANDLING
     * =============================================================
     */

    private fun failPurchaseFlow(
        userMessage: String
    ) {
        purchaseFlowInProgress = false

        clearPendingPurchase()

        dispatchUserMessage(
            userMessage
        )
    }


    private fun billingMessageFor(
        billingResult: BillingResult,
        fallback: String
    ): String {
        return when (
            billingResult.responseCode
        ) {

            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED ->
                "Google Play bağlantısı kesildi. Lütfen tekrar deneyin."

            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                "İnternet bağlantısı nedeniyle Google Play'e ulaşılamadı."

            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
                "Google Play faturalandırma servisi geçici olarak kullanılamıyor."

            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "Google Play faturalandırma bu cihazda veya hesapta kullanılamıyor."

            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "Abonelik ürünü şu anda kullanılamıyor."

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                "Abonelik zaten hesabınızda mevcut."

            BillingClient.BillingResponseCode.USER_CANCELED ->
                "Satın alma işlemi iptal edildi."

            BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED ->
                "Bu cihazdaki Google Play sürümü bu özelliği desteklemiyor."

            BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
                "Satın alma yapılandırması doğrulanamadı."

            BillingClient.BillingResponseCode.ERROR ->
                fallback

            else ->
                fallback
        }
    }


    /*
     * Billing Library 9:
     *
     * onPurchasesUpdated için response code'a ek olarak
     * daha ayrıntılı sub-response code bulunabilir.
     */
    private fun messageForPurchaseSubResponse(
        billingResult: BillingResult
    ): String? {
        return when (
            billingResult.onPurchasesUpdatedSubResponseCode
        ) {

            BillingClient.OnPurchasesUpdatedSubResponseCode
                .PAYMENT_DECLINED_DUE_TO_INSUFFICIENT_FUNDS ->
                "Ödeme yönteminize ait kullanılabilir bakiye yetersiz."

            BillingClient.OnPurchasesUpdatedSubResponseCode
                .USER_INELIGIBLE ->
                "Bu Google Play hesabı seçilen abonelik teklifinden yararlanamıyor."

            else ->
                null
        }
    }


    /*
     * =============================================================
     * HELPERS
     * =============================================================
     */

    private fun isActivityUsable(
        activity: Activity
    ): Boolean {
        return !activity.isFinishing &&
                !activity.isDestroyed
    }


    private fun clearPendingPurchase() {
        pendingPurchaseActivity
            ?.clear()

        pendingPurchaseActivity = null
    }


    /*
     * =============================================================
     * CLEANUP
     * =============================================================
     */

    /**
     * BillingManager artık kullanılmayacaksa çağrılmalıdır.
     *
     * BillingClient service binding'ini serbest bırakır.
     */
    fun close() {
        if (isClosed) {
            return
        }

        isClosed = true
        isConnecting = false
        purchaseFlowInProgress = false

        clearPendingPurchase()

        synchronized(connectionLock) {
            try {
                billingClient.endConnection()
            } catch (throwable: Throwable) {
                Log.w(
                    TAG,
                    "Billing bağlantısı kapatılırken hata oluştu.",
                    throwable
                )
            }
        }

        mainHandler.removeCallbacksAndMessages(
            null
        )
    }


    companion object {
        private const val TAG =
            "MuhafizBilling"

        private const val PRODUCT_ID =
            "muhafiz_monthly"
    }
}