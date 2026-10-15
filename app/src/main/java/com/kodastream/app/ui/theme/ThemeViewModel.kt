package com.kodastream.app.ui.theme

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel

/** Holds the look the user picked and remembers it. */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var mode by mutableStateOf(readMode())
        private set
    var palette by mutableStateOf(prefs.getString(KEY_PALETTE, DYNAMIC_PALETTE_ID) ?: DYNAMIC_PALETTE_ID)
        private set
    var amoled by mutableStateOf(prefs.getBoolean(KEY_AMOLED, false))
        private set
    var style by mutableStateOf(PaletteStyle.fromStorageId(prefs.getString(KEY_STYLE, null)))
        private set

    private fun readMode(): ThemeMode {
        val saved = prefs.getString(KEY_MODE, null)
        return ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.DARK
    }

    fun updateMode(value: ThemeMode) {
        mode = value
        prefs.edit { putString(KEY_MODE, value.name) }
    }

    fun updatePalette(id: String) {
        palette = id
        prefs.edit { putString(KEY_PALETTE, id) }
    }

    fun updateAmoled(value: Boolean) {
        amoled = value
        prefs.edit { putBoolean(KEY_AMOLED, value) }
    }

    fun updateStyle(value: PaletteStyle) {
        style = value
        prefs.edit { putString(KEY_STYLE, value.storageId) }
    }

    private companion object {
        const val PREFS = "kodastream_theme"
        const val KEY_MODE = "mode"
        const val KEY_PALETTE = "palette"
        const val KEY_AMOLED = "amoled"
        const val KEY_STYLE = "style"
    }
}
