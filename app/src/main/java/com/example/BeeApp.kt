package com.example

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds

class BeeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            MobileAds.initialize(this) { initializationStatus ->
                Log.d("BeeApp", "MobileAds SDK initialized successfully")
            }
        } catch (e: Throwable) {
            Log.e("BeeApp", "Error initializing MobileAds SDK: ${e.message}", e)
        }
    }
}
