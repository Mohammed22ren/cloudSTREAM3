package com.kodastream.app.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
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
    private const val KEY_DL_TREE = "download_tree_uri"
    private const val KEY_DL_WIFI = "download_wifi_only"
    private const val KEY_DL_MAX = "download_max_concurrent"
    private const val KEY_DL_SERVER = "download_last_server"
    private const val KEY_DL_FOLDERS_DONE = "download_folders_migrated"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    private const val KEY_SETUP_DONE = "setup_done"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val KEY_UPDATE_CHECK = "update_last_check"
    private const val KEY_UPDATE_SKIPPED = "update_skipped_version"
    private const val POSITION_PREFIX = "pos_"

    const val NAV_SIDE = "side"
    const val NAV_BOTTOM = "bottom"
    const val LANGUAGE_SYSTEM = "system"

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

    // ---- Downloads ----

    /** The SAF tree the user picked, or null for the app's default folder. */
    fun downloadTreeUri(context: Context): String? = prefs(context).getString(KEY_DL_TREE, null)

    fun setDownloadTreeUri(context: Context, uri: String?) {
        prefs(context).edit { if (uri == null) remove(KEY_DL_TREE) else putString(KEY_DL_TREE, uri) }
    }

    fun downloadWifiOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_DL_WIFI, false)

    fun setDownloadWifiOnly(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_DL_WIFI, value) }
    }

    fun downloadMaxConcurrent(context: Context): Int = prefs(context).getInt(KEY_DL_MAX, 2).coerceIn(1, 3)

    fun setDownloadMaxConcurrent(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_DL_MAX, value.coerceIn(1, 3)) }
    }

    /** True once the one-time move into per-title folders has run. */
    fun downloadsFolderMigrated(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DL_FOLDERS_DONE, false)

    fun setDownloadsFolderMigrated(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_DL_FOLDERS_DONE, value) }
    }

    /** Keep the screen awake while the player plays. On by default. */
    fun keepScreenOn(context: Context): Boolean = prefs(context).getBoolean(KEY_KEEP_SCREEN_ON, true)

    fun setKeepScreenOn(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_KEEP_SCREEN_ON, value) }
    }

    /** The server name last picked in the download card, preselected next time. */
    fun lastDownloadServer(context: Context): String? = prefs(context).getString(KEY_DL_SERVER, null)

    fun setLastDownloadServer(context: Context, value: String) {
        prefs(context).edit { putString(KEY_DL_SERVER, value) }
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

    // ---- First-run setup ----

    fun setupDone(context: Context): Boolean = prefs(context).getBoolean(KEY_SETUP_DONE, false)

    fun setSetupDone(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_SETUP_DONE, value) }
    }

    /** "system" or a BCP 47 language tag such as "en" / "ar". */
    fun appLanguage(context: Context): String =
        prefs(context).getString(KEY_APP_LANGUAGE, null) ?: LANGUAGE_SYSTEM

    fun setAppLanguage(context: Context, tag: String) {
        prefs(context).edit { putString(KEY_APP_LANGUAGE, tag) }
    }

    /** Applies the saved language; called from Application before any activity exists. */
    fun applyAppLanguage(context: Context) {
        val locales = appLanguage(context).takeIf { it != LANGUAGE_SYSTEM }
            ?.let { LocaleListCompat.forLanguageTags(it) }
            ?: LocaleListCompat.getEmptyLocaleList()
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    // ---- App updates ----

    /** When the GitHub release check last completed, in epoch millis. */
    fun lastUpdateCheck(context: Context): Long = prefs(context).getLong(KEY_UPDATE_CHECK, 0L)

    fun setLastUpdateCheck(context: Context, timeMs: Long) {
        prefs(context).edit { putLong(KEY_UPDATE_CHECK, timeMs) }
    }

    /** The version the user chose to stop being reminded about. */
    fun skippedUpdateVersion(context: Context): String? =
        prefs(context).getString(KEY_UPDATE_SKIPPED, null)

    fun setSkippedUpdateVersion(context: Context, version: String?) {
        prefs(context).edit {
            if (version == null) remove(KEY_UPDATE_SKIPPED) else putString(KEY_UPDATE_SKIPPED, version)
        }
    }
}
