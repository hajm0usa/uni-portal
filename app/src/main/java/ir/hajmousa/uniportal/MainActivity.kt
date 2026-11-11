package ir.hajmousa.uniportal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ir.hajmousa.uniportal.core.AppLog
import ir.hajmousa.uniportal.feature.PortalApp
import ir.hajmousa.uniportal.feature.theme.PortalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLog.record(AppLog.Event.APP_STARTED)
        setContent {
            PortalTheme {
                PortalApp()
            }
        }
    }
}
