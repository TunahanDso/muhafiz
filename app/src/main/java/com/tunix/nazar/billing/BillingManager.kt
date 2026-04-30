package com.tunix.nazar.billing

import android.app.Activity
import android.content.Context
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

class BillingManager(
    private val context: Context,
    private val onSubscriptionStatusChanged: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    private lateinit var billingClient: BillingClient

    private val productId = "muhafiz_monthly"

    fun startConnection() {
        val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts()
            .build()

        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(pendingPurchasesParams)
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Bağlantı koparsa bir sonraki kullanıcı işleminde tekrar bağlanılabilir.
            }
        })
    }

    fun purchase(activity: Activity) {
        if (!::billingClient.isInitialized || !billingClient.isReady) {
            startConnection()
            return
        }

        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                return@queryProductDetailsAsync
            }

            val productDetailsList = queryResult.productDetailsList
            val productDetails = productDetailsList.firstOrNull() ?: return@queryProductDetailsAsync

            launchSubscriptionFlow(activity, productDetails)
        }
    }

    private fun launchSubscriptionFlow(
        activity: Activity,
        productDetails: ProductDetails
    ) {
        val offerToken = productDetails.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
            ?: return

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        }
    }

    private fun queryPurchases() {
        if (!::billingClient.isInitialized || !billingClient.isReady) {
            onSubscriptionStatusChanged(false)
            return
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            } else {
                onSubscriptionStatusChanged(false)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        var isSubscribed = false

        purchases.forEach { purchase ->
            val hasThisProduct = purchase.products.contains(productId)
            val isPurchased = purchase.purchaseState == Purchase.PurchaseState.PURCHASED

            if (hasThisProduct && isPurchased) {
                isSubscribed = true

                if (!purchase.isAcknowledged) {
                    val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingClient.acknowledgePurchase(acknowledgeParams) {}
                }
            }
        }

        onSubscriptionStatusChanged(isSubscribed)
    }
}