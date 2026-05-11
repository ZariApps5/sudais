package com.zariapps.quran.sudais.ui.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppOpenAdManager @Inject constructor() {

    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var hasShownThisSession = false
    private val loadResult = MutableStateFlow<LoadState>(LoadState.Idle)

    private sealed interface LoadState {
        data object Idle : LoadState
        data object Loaded : LoadState
        data object Failed : LoadState
    }

    fun load(context: Context) {
        if (isLoading || appOpenAd != null || hasShownThisSession) return
        isLoading = true
        loadResult.value = LoadState.Idle
        AppOpenAd.load(
            context.applicationContext,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoading = false
                    loadResult.value = LoadState.Loaded
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    isLoading = false
                    loadResult.value = LoadState.Failed
                }
            }
        )
    }

    suspend fun showWhenReady(activity: Activity, timeoutMs: Long = 4000L) {
        if (hasShownThisSession) return

        try {
            withTimeout(timeoutMs) {
                loadResult.first { it != LoadState.Idle }
            }
        } catch (_: TimeoutCancellationException) {
            return
        }

        val ad = appOpenAd ?: return
        val dismissed = CompletableDeferred<Unit>()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                dismissed.complete(Unit)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                dismissed.complete(Unit)
            }
        }
        hasShownThisSession = true
        ad.show(activity)
        dismissed.await()
    }

    companion object {
        private const val AD_UNIT_ID = "ca-app-pub-3572341533498507/9860056648"
    }
}
