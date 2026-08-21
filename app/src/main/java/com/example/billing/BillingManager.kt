package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(context: Context) : BillingClientStateListener {
    companion object {
        const val REMOVE_ADS_PRODUCT_ID = "remove_ads_subscription"
        private const val TAG = "BillingManager"
    }

    private val appContext = context.applicationContext
    private val _adsRemoved = MutableStateFlow(false)
    val adsRemoved: StateFlow<Boolean> = _adsRemoved.asStateFlow()
    private val _status = MutableStateFlow("Connecting to Google Play...")
    val status: StateFlow<String> = _status.asStateFlow()

    private var removeAdsProduct: ProductDetails? = null
    private var removeAdsProductType: String? = null
    private var billingClient: BillingClient = createBillingClient()

    init {
        billingClient.startConnection(this)
    }

    private fun createBillingClient(): BillingClient {
        return BillingClient.newBuilder(appContext)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.orEmpty().forEach(::processPurchase)
                } else {
                    Log.w(TAG, "Purchase update failed: ${billingResult.debugMessage}")
                }
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            _status.value = "Google Play billing is unavailable."
            Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
            return
        }
        _status.value = "Loading remove-ads product..."
        queryRemoveAdsProduct(BillingClient.ProductType.SUBS)
        queryRemoveAdsProduct(BillingClient.ProductType.INAPP)
        refreshPurchases()
    }

    override fun onBillingServiceDisconnected() {
        _status.value = "Google Play connection lost. Tap Remove Ads to retry."
        Log.w(TAG, "Billing service disconnected")
    }

    private fun queryRemoveAdsProduct(productType: String) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(REMOVE_ADS_PRODUCT_ID)
            .setProductType(productType)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                result.productDetailsList.firstOrNull()?.let {
                    removeAdsProduct = it
                    removeAdsProductType = productType
                    _status.value = "Remove-ads product ready"
                } ?: run {
                    if (removeAdsProduct == null) {
                        _status.value = "Remove-ads product is not available for this app"
                    }
                    Log.w(TAG, "No product details returned for $REMOVE_ADS_PRODUCT_ID ($productType)")
                }
            } else {
                _status.value = "Could not load remove-ads product from Google Play"
                Log.w(TAG, "Product query failed for $productType: ${billingResult.responseCode} ${billingResult.debugMessage}")
            }
        }
    }

    fun refreshPurchases() {
        if (!billingClient.isReady) return
        listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).forEach { productType ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build()
            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (purchases.any { purchase ->
                            purchase.products.contains(REMOVE_ADS_PRODUCT_ID) &&
                                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                        }
                    ) {
                        _adsRemoved.value = true
                    }
                    purchases.forEach(::processPurchase)
                } else {
                    Log.w(TAG, "Purchase restore failed for $productType: ${billingResult.debugMessage}")
                }
            }
        }
    }

    fun launchRemoveAdsPurchase(activity: Activity) {
        val product = removeAdsProduct ?: run {
            _status.value = "Product is still loading. Check Play Store setup and try again."
            Log.w(TAG, "Remove-ads product is not available yet; refreshing product details")
            if (billingClient.isReady) {
                queryRemoveAdsProduct(BillingClient.ProductType.SUBS)
                queryRemoveAdsProduct(BillingClient.ProductType.INAPP)
            }
            return
        }
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
        if (removeAdsProductType == BillingClient.ProductType.SUBS) {
            val offerToken = product.subscriptionOfferDetails
                ?.firstOrNull()
                ?.offerToken
            if (offerToken == null) {
                _status.value = "Subscription has no active base plan or offer"
                Log.w(TAG, "Remove-ads subscription has no active base-plan offer")
                return
            }
            productParamsBuilder.setOfferToken(offerToken)
        }
        val productParams = productParamsBuilder.build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        val billingResult = billingClient.launchBillingFlow(activity, flowParams)
        _status.value = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            "Complete the purchase in Google Play"
        } else {
            "Google Play could not start the purchase"
        }
        Log.d(TAG, "Remove-ads billing flow: ${billingResult.responseCode} ${billingResult.debugMessage}")
    }

    private fun processPurchase(purchase: Purchase) {
        if (!purchase.products.contains(REMOVE_ADS_PRODUCT_ID) ||
            purchase.purchaseState != Purchase.PurchaseState.PURCHASED
        ) return

        _adsRemoved.value = true
        if (!purchase.isAcknowledged) {
            billingClient.acknowledgePurchase(
                com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Purchase acknowledgement failed: ${result.debugMessage}")
                }
            }
        }
    }

    fun close() {
        billingClient.endConnection()
    }
}
