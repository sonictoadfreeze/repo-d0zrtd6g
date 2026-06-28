package com.inkvpn.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.inkvpn.app.core.ServerConfig
import com.inkvpn.app.ui.theme.InkVPNTheme
import com.inkvpn.app.vpn.VpnController

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private var pendingServer: ServerConfig? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingServer?.let { VpnController.start(this, it) }
        }
        pendingServer = null
    }

    /** Called by the UI: ensures VPN permission before starting a server. */
    fun requestConnect(server: ServerConfig) {
        val intent = VpnController.prepare(this)
        if (intent != null) {
            pendingServer = server
            vpnPermissionLauncher.launch(intent)
        } else {
            VpnController.start(this, server)
        }
    }

    val pendingDeepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            InkVPNTheme {
                InkVpnApp(vm = vm, activity = this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.dataString ?: return
        pendingDeepLink.value = data
    }
}
