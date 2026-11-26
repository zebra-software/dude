package com.zebrasoftware.dude.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.zebrasoftware.dude.R

internal enum class ThemeMode(@StringRes val label: Int) {
    System(R.string.theme_phone),
    Light(R.string.theme_light),
    Dark(R.string.theme_dark)
}

/** App settings, saved on the device so they survive restarts. */
internal class DudeSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME_MODE, null) }
            ?: ThemeMode.System
    )
        private set

    fun updateThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit { putString(KEY_THEME_MODE, mode.name) }
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}
