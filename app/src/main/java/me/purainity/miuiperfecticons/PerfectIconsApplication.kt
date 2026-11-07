package me.purainity.miuiperfecticons

import android.app.Application
import com.google.android.material.color.DynamicColors

/** 全局启用 Android 12+ 动态取色 */
class PerfectIconsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
