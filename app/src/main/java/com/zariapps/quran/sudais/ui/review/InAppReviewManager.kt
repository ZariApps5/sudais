package com.zariapps.quran.sudais.ui.review

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory

object InAppReviewManager {
    fun requestReview(activity: Activity) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) {
                manager.launchReviewFlow(activity, request.result)
            }
        }
    }
}
