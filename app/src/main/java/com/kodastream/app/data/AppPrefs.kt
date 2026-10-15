package com.kodastream.app.data

import android.content.Context
import androidx.core.content.edit

/** Small app settings that are not part of the theme. */
object AppPrefs {
    private const val PREFS = "kodastream_prefs"
    private const val KEY_PROVIDER = "selected_provider"
    private const val KEY_AUTO_UPDATE = "auto_update_extensions"
    private const val POSITION_PREFIX = "pos_"

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
