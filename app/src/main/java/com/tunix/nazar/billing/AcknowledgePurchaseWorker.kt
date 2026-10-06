package com.tunix.nazar.billing

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Google Play purchase acknowledgement için kalıcı güvenlik ağı.
 *
 * BillingManager önce normal/in-process acknowledge dener. Bu Worker
 * bir dakika gecikmeli planlanır; uygulama process'i ölürse veya
 * geçici Play/ağ hatası olursa token'ı daha sonra tekrar acknowledge eder.
 */
class AcknowledgePurchaseWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(
    appContext,
    workerParams
) {

    override fun doWork(): Result {
        val purchaseToken =
            inputData.getString(
                KEY_PURCHASE_TOKEN
            )?.takeIf {
                it.isNotBlank()
            } ?: return Result.failure()

        val pendingPurchasesParams =
            PendingPurchasesParams
                .newBuilder()
                .enableOneTimeProducts()
                .build()

        val billingClient =
            BillingClient
                .newBuilder(
                    applicationContext
                )
                .setListener { _, _ ->
                    /*
                     * Worker yeni purchase başlatmaz.
                     * Listener Builder sözleşmesi için gereklidir.
                     */
                }
                .enablePendingPurchases(
                    pendingPurchasesParams
                )
                .enableAutoServiceReconnection()
                .build()

        return try {
            val setupResult =
                connectBillingClient(
                    billingClient
                )

            if (
                setupResult !=
                BillingClient.BillingResponseCode.OK
            ) {
                return retryOrFail(
                    setupResult
                )
            }

            val acknowledgeResult =
                acknowledgeToken(
                    billingClient,
                    purchaseToken
                )

            retryOrFail(
                acknowledgeResult,
                successOnOk = true
            )
        } catch (_: Exception) {
            Result.retry()
        } finally {
            try {
                billingClient.endConnection()
            } catch (_: Exception) {
            }
        }
    }

    private fun connectBillingClient(
        billingClient: BillingClient
    ): Int {

        val latch =
            CountDownLatch(1)

        val responseCode =
            AtomicInteger(
                BillingClient.BillingResponseCode
                    .SERVICE_DISCONNECTED
            )

        billingClient.startConnection(
            object :
                BillingClientStateListener {

                override fun onBillingSetupFinished(
                    billingResult: BillingResult
                ) {
                    responseCode.set(
                        billingResult.responseCode
                    )

                    latch.countDown()
                }

                override fun onBillingServiceDisconnected() {
                    responseCode.set(
                        BillingClient.BillingResponseCode
                            .SERVICE_DISCONNECTED
                    )

                    latch.countDown()
                }
            }
        )

        val completed =
            latch.await(
                CALLBACK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )

        return if (completed) {
            responseCode.get()
        } else {
            BillingClient.BillingResponseCode
                .SERVICE_UNAVAILABLE
        }
    }

    private fun acknowledgeToken(
        billingClient: BillingClient,
        purchaseToken: String
    ): Int {

        val latch =
            CountDownLatch(1)

        val responseCode =
            AtomicInteger(
                BillingClient.BillingResponseCode
                    .SERVICE_DISCONNECTED
            )

        val params =
            AcknowledgePurchaseParams
                .newBuilder()
                .setPurchaseToken(
                    purchaseToken
                )
                .build()

        billingClient.acknowledgePurchase(
            params
        ) { billingResult ->

            responseCode.set(
                billingResult.responseCode
            )

            latch.countDown()
        }

        val completed =
            latch.await(
                CALLBACK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )

        return if (completed) {
            responseCode.get()
        } else {
            BillingClient.BillingResponseCode
                .SERVICE_UNAVAILABLE
        }
    }

    private fun retryOrFail(
        responseCode: Int,
        successOnOk: Boolean = false
    ): Result {

        if (
            successOnOk &&
            responseCode ==
            BillingClient.BillingResponseCode.OK
        ) {
            return Result.success()
        }

        return when (responseCode) {

            BillingClient.BillingResponseCode
                .SERVICE_DISCONNECTED,

            BillingClient.BillingResponseCode
                .SERVICE_UNAVAILABLE,

            BillingClient.BillingResponseCode
                .NETWORK_ERROR,

            BillingClient.BillingResponseCode
                .ERROR -> Result.retry()

            BillingClient.BillingResponseCode.OK ->
                Result.success()

            else ->
                Result.failure()
        }
    }

    companion object {

        private const val KEY_PURCHASE_TOKEN =
            "purchase_token"

        private const val WORK_NAME_PREFIX =
            "muhafiz_ack_"

        private const val CALLBACK_TIMEOUT_SECONDS =
            20L

        private const val INITIAL_DELAY_MINUTES =
            1L

        fun enqueue(
            context: Context,
            purchaseToken: String
        ) {
            if (purchaseToken.isBlank()) {
                return
            }

            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(
                        NetworkType.CONNECTED
                    )
                    .build()

            val request =
                OneTimeWorkRequestBuilder<AcknowledgePurchaseWorker>()
                    .setInputData(
                        Data
                            .Builder()
                            .putString(
                                KEY_PURCHASE_TOKEN,
                                purchaseToken
                            )
                            .build()
                    )
                    .setConstraints(
                        constraints
                    )
                    .setInitialDelay(
                        INITIAL_DELAY_MINUTES,
                        TimeUnit.MINUTES
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30L,
                        TimeUnit.SECONDS
                    )
                    .build()

            WorkManager
                .getInstance(
                    context.applicationContext
                )
                .enqueueUniqueWork(
                    workName(
                        purchaseToken
                    ),
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }

        fun cancel(
            context: Context,
            purchaseToken: String
        ) {
            if (purchaseToken.isBlank()) {
                return
            }

            WorkManager
                .getInstance(
                    context.applicationContext
                )
                .cancelUniqueWork(
                    workName(
                        purchaseToken
                    )
                )
        }

        private fun workName(
            purchaseToken: String
        ): String {

            val digest =
                MessageDigest
                    .getInstance(
                        "SHA-256"
                    )
                    .digest(
                        purchaseToken
                            .toByteArray(
                                Charsets.UTF_8
                            )
                    )

            val shortHash =
                digest
                    .take(12)
                    .joinToString(
                        separator = ""
                    ) { byte ->
                        "%02x".format(
                            byte
                        )
                    }

            return WORK_NAME_PREFIX +
                    shortHash
        }
    }
}
