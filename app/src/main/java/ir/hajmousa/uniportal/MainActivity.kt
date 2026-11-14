package ir.hajmousa.uniportal

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.feature.PortalApp
import ir.hajmousa.uniportal.feature.theme.PortalTheme

class MainActivity : ComponentActivity() {
    private var reminderIntent by mutableStateOf<Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reminderIntent = if (savedInstanceState == null) intent else null
        enableEdgeToEdge()
        AppLog.record(AppLog.Event.APP_STARTED)
        setContent {
            PortalTheme {
                PortalApp(reminderIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        reminderIntent = intent
    }
}
