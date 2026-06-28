package com.inkvpn.app.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.inkvpn.app.InkVpnApp
import kotlinx.coroutines.runBlocking

/** Auto-connects on device boot when "connect on boot" is enabled and a server is selected. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? InkVpnApp ?: return
        val repo = app.repository
        val pending = goAsync()
        Thread {
            try {
                val settings = runBlocking { repo.currentSettings() }
                if (!settings.connectOnBoot) return@Thread
                // VPN permission must already be granted; we cannot prompt from a boot receiver.
                if (VpnService.prepare(context) != null) return@Thread
                val subs = runBlocking { repo.current() }
                val selectedId = runBlocking { repo.selectedServerId() }
                val servers = subs.flatMap { it.servers }
                val server = servers.firstOrNull { it.id == selectedId } ?: servers.firstOrNull()
                if (server != null) VpnController.start(context, server)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
