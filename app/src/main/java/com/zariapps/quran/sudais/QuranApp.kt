package com.zariapps.quran.sudais

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class QuranApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        MobileAds.initialize(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "playback_channel",
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quran audio playback controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
