package com.lagradost.cloudstream3

import android.app.Activity
import android.widget.Toast
import androidx.annotation.MainThread
import java.lang.ref.WeakReference

/**
 * The part of CloudStream's CommonActivity that extensions actually call:
 * the current activity and toast helpers. Everything else in the original is
 * View-based UI and has no place in a Compose app.
 */
object CommonActivity {

    private var _activity: WeakReference<Activity>? = null
    var activity: Activity?
        get() = _activity?.get()
        private set(value) {
            _activity = WeakReference(value)
        }

    @MainThread
    fun setActivityInstance(newActivity: Activity?) {
        activity = newActivity
    }

    private var currentToast: Toast? = null

    fun showToast(message: String?, duration: Int? = null) {
        val act = activity ?: return
        act.runOnUiThread {
            showToast(act, message, duration)
        }
    }

    /** duration is Toast.LENGTH_SHORT if null */
    @MainThread
    fun showToast(act: Activity?, message: String?, duration: Int? = null) {
        if (act == null || message == null) return
        try {
            currentToast?.cancel()
        } catch (_: Throwable) {
        }
        try {
            val toast = Toast.makeText(act, message.trim(), duration ?: Toast.LENGTH_SHORT)
            currentToast = toast
            toast.show()
        } catch (_: Throwable) {
        }
    }
}
