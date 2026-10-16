package com.kodastream.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small app settings that are not part of the theme. */
object AppPrefs {
    private const val PREFS = "kodastream_prefs"
    private const val KEY_PROVIDER = "selected_provider"
    private const val KEY_AUTO_UPDATE = "auto_update_extensions"
    private const val KEY_NAV_POSITION = "nav_position"
    private const val KEY_SPEED = "playback_speed"
    private const val KEY_PAUSE_HISTORY = "pause_history"
    private const val POSITION_PREFIX = "pos_"

    const val NAV_SIDE = "side"
    const val NAV_BOTTOM = "bottom"

    private val _navPosition = MutableStateFlow<String?>(null)

    /** The bar position, as a flow so the shell can switch layouts the moment it changes. */
    val navPosition: StateFlow<String?> = _navPosition.asStateFlow()

    /** Where the navigation bar sits. Side (left) is the default. */
    fun navPosition(context: Context): String =
        _navPosition.value
            ?: prefs(context).getString(KEY_NAV_POSITION, null)?.also { _navPosition.value = it }
            ?: NAV_SIDE

    fun setNavPosition(context: Context, value: String) {
        _navPosition.value = value
        prefs(context).edit { putString(KEY_NAV_POSITION, value) }
    }

    /** The player's chosen speed, remembered between episodes. */
    fun playbackSpeed(context: Context): Float = prefs(context).getFloat(KEY_SPEED, 1f)

    fun setPlaybackSpeed(context: Context, value: Float) {
        prefs(context).edit { putFloat(KEY_SPEED, value) }
    }

    /** True when nothing new should be written to the watch history. Existing entries stay. */
    fun pauseHistory(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSE_HISTORY, false)

    fun setPauseHistory(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_PAUSE_HISTORY, value) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selectedProvider(context: Context): String? = prefs(context).getString(KEY_PROVIDER, null)

    fun setSelectedProvider(context: Context, name: String?) {
        prefs(context).edit { if (name == null) remove(KEY_PROVIDER) else putString(KEY_PROVIDER, name) }
    }

    fun autoUpdate(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_UPDATE, true)

    fun setAutoUpdate(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_AUTO_UPDATE, value) }
    }

    /** Where playback stopped, in milliseconds. 0 when unknown. */
    fun position(context: Context, key: String): Long =
        prefs(context).getLong(POSITION_PREFIX + key.hashCode(), 0L)

    fun setPosition(context: Context, key: String, positionMs: Long) {
        prefs(context).edit { putLong(POSITION_PREFIX + key.hashCode(), positionMs) }
    }
}
