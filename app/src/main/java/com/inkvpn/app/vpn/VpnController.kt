package com.inkvpn.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.inkvpn.app.core.ServerConfig

/** Helper to start/stop the VpnService and check for the system VPN permission. */
object VpnController {

    /** Returns an Intent to request VPN permission, or null if already granted. */
    fun prepare(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context, server: ServerConfig) {
        val outbound = server.outboundJson
        if (outbound == null || !server.supportedByCore) {
            VpnState.setError("Этот сервер не поддерживается ядром (${server.transport})")
            VpnState.setStatus(VpnStatus.ERROR)
            return
        }
        // The service loads AppSettings and builds the final config on its worker thread.
        val intent = Intent(context, InkVpnService::class.java).apply {
            action = InkVpnService.ACTION_START
            putExtra(InkVpnService.EXTRA_OUTBOUND, outbound)
            putExtra(InkVpnService.EXTRA_SERVER_ID, server.id)
        }
        context.startForegroundService(intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, InkVpnService::class.java).apply {
            action = InkVpnService.ACTION_STOP
        }
        context.startService(intent)
    }
}
