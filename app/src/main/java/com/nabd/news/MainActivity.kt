package com.nabd.news

import android.os.Bundle
import android.content.Intent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {
    private val vm: NabdViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // All screens are protected so a key cannot appear during navigation transitions.
        // It also keeps personal notes and conversations out of task-switcher previews.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        receiveShare(intent)
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { NabdAppearance(vm) } }
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);receiveShare(intent) }
    private fun receiveShare(intent:Intent?) {
        consumeSharedText(intent)?.let(vm::acceptShare)
    }

}

@androidx.compose.runtime.Composable private fun NabdAppearance(vm:NabdViewModel) {
    val version by vm.configVersion.collectAsState()
    val appearance=remember(version){vm.settings.get("appearance","dark")}
    NabdTheme(mode=appearance){NabdApp(vm)}
}

