package com.example.ad

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdHelper(private val context: Context) {
    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null

    // Test Ad Unit IDs from Google documentation
    private val REWARDED_TEST_ID = "ca-app-pub-3940256099942544/5224354917"
    private val INTERSTITIAL_TEST_ID = "ca-app-pub-3940256099942544/1033173712"

    private var isInitializing = false

    init {
        initializeAdMob()
    }

    private fun initializeAdMob() {
        if (isInitializing) return
        isInitializing = true
        MobileAds.initialize(context) {
            loadRewardedAd()
            loadInterstitialAd()
        }
    }

    fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, REWARDED_TEST_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e("AdHelper", "Rewarded Ad failed to load: ${loadAdError.message}")
                rewardedAd = null
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d("AdHelper", "Rewarded Ad loaded successfully")
                rewardedAd = ad
            }
        })
    }

    fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context, INTERSTITIAL_TEST_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                Log.e("AdHelper", "Interstitial Ad failed to load: ${loadAdError.message}")
                interstitialAd = null
            }

            override fun onAdLoaded(ad: InterstitialAd) {
                Log.d("AdHelper", "Interstitial Ad loaded successfully")
                interstitialAd = ad
            }
        })
    }

    fun isRewardedAdLoaded(): Boolean {
        // We will consider it loaded if the ad reference is present.
        return rewardedAd != null
    }

    fun showRewardedAd(activity: Activity, onAdDismissed: (Boolean) -> Unit) {
        val ad = rewardedAd
        if (ad != null) {
            var earnedReward = false
            ad.show(activity) { rewardItem ->
                earnedReward = true
                Log.d("AdHelper", "User earned reward: ${rewardItem.amount}")
            }
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdHelper", "Rewarded Ad dismissed")
                    rewardedAd = null
                    loadRewardedAd() // Auto reload
                    activity.runOnUiThread {
                        onAdDismissed(earnedReward)
                    }
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.e("AdHelper", "Rewarded Ad failed to show: ${adError.message}")
                    rewardedAd = null
                    loadRewardedAd()
                    activity.runOnUiThread {
                        // In case of error (e.g. offline sandbox build), fallback to gracefully rewarding the user
                        onAdDismissed(true)
                    }
                }
            }
        } else {
            // Safe simulation reward fallback in sandbox to ensure level progression doesn't block
            loadRewardedAd()
            onAdDismissed(true)
        }
    }

    fun isInterstitialAdLoaded(): Boolean {
        return interstitialAd != null
    }

    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        val ad = interstitialAd
        if (ad != null) {
            ad.show(activity)
            ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdHelper", "Interstitial Ad dismissed")
                    interstitialAd = null
                    loadInterstitialAd() // Auto reload
                    activity.runOnUiThread {
                        onAdDismissed()
                    }
                }

                override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                    Log.e("AdHelper", "Interstitial Ad failed to show: ${adError.message}")
                    interstitialAd = null
                    loadInterstitialAd()
                    activity.runOnUiThread {
                        onAdDismissed()
                    }
                }
            }
        } else {
            loadInterstitialAd()
            onAdDismissed()
        }
    }
}
