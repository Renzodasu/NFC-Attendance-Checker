package com.nezzar.nfcattendance

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nezzar.nfcattendance.ui.AppRoot
import com.nezzar.nfcattendance.ui.AppState
import com.nezzar.nfcattendance.ui.theme.NFCAttendanceTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The theme itself repaints the system icons, so the bars are transparent
        // under both palettes.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val state = AppState(applicationContext)
        setContent {
            NFCAttendanceTheme(mode = state.themeMode) {
                AppRoot(state, this@MainActivity)
            }
        }
    }
}
