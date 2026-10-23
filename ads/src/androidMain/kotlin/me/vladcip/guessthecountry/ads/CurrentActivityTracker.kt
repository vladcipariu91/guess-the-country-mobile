package me.vladcip.guessthecountry.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** The consent form and ads are shown on top of the resumed activity. */
internal class CurrentActivityTracker(application: Application) : Application.ActivityLifecycleCallbacks {
    private val current = MutableStateFlow<Activity?>(null)

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    val activity: Activity? get() = current.value

    suspend fun awaitActivity(): Activity = current.filterNotNull().first()

    override fun onActivityResumed(activity: Activity) {
        current.value = activity
    }

    override fun onActivityPaused(activity: Activity) {
        if (current.value === activity) current.value = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
