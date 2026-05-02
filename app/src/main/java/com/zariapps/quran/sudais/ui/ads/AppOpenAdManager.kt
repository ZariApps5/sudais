package com.zariapps.quran.sudais.ui.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenAdManager @Inject constructor() {

    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var hasShownThisSession = false

    fun load(context: Context) {
        if (isLoading || appOpenAd != null || hasShownThisSession) return
        isLoading = true
        AppOpenAd.load(
            context.applicationContext,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    isLoading = false
                }
            }
        )
    }

    fun showIfAvailable(activity: Activity, onFinished: () -> Unit) {
        if (hasShownThisSession) {
            onFinished()
            return
        }
        val ad = appOpenAd
        if (ad == null) {
            hasShownThisSession = true
            onFinished()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                hasShownThisSession = true
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                hasShownThisSession = true
                onFinished()
            }
        }
        hasShownThisSession = true
        ad.show(activity)
    }

    companion object {
        private const val AD_UNIT_ID = "ca-app-pub-3572341533498507/9860056648"
    }
}
