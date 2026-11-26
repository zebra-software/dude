package com.zebrasoftware.dude

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.zebrasoftware.dude.data.DudeSettings
import com.zebrasoftware.dude.data.ThemeMode
import com.zebrasoftware.dude.ui.DudeApp
import com.zebrasoftware.dude.ui.theme.DudeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settings = remember { DudeSettings(applicationContext) }

            val darkTheme = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            // Keep the status and navigation bar icons readable when the app
            // theme differs from the phone's theme.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                )
                onDispose {}
            }

            DudeTheme(darkTheme = darkTheme) {
                DudeApp(settings = settings, darkTheme = darkTheme)
            }
        }
    }
}
