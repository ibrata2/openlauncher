package com.openlauncher.app.designer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.openlauncher.app.designer.ui.DesignerColors
import com.openlauncher.app.designer.ui.DesignerScreen

/**
 * Stand-alone entry point for the theme editor ("Theme Studio" icon in the app
 * drawer). Lets you design on an Android tablet without making Open Launcher the
 * tablet's home screen; the designs are the same files the launcher uses.
 */
class DesignerActivity : ComponentActivity() {

    private val vm: DesignerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = DesignerColors.Accent,
                    onPrimary = Color.Black,
                    background = DesignerColors.Background,
                    surface = DesignerColors.Panel,
                    onSurface = DesignerColors.Text,
                    onBackground = DesignerColors.Text
                )
            ) {
                DesignerScreen(vm, onExit = { finish() })
            }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        vm.saveIfDirty()
    }
}
