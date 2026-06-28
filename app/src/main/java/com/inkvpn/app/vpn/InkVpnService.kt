package com.inkvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.inkvpn.app.R
import com.inkvpn.app.ui.MainActivity
import io.nekohasekai.libbox.BoxService
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.SetupOptions
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState
import java.net.InetSocketAddress

class InkVpnService : VpnService(), PlatformInterface {

    companion object {
        const val ACTION_START = "com.inkvpn.app.START"
        const val ACTION_STOP = "com.inkvpn.app.STOP"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_SERVER_ID = "server_id"
        private const val CHANNEL_ID = "inkvpn_vpn"
        private const val NOTIF_ID = 0x1A11
    }

    private var boxService: BoxService? = null
    private var tunFd: ParcelFileDescriptor? = null
    private var defaultNetwork: Network? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopVpn(); return START_NOT_STICKY }
            else -> {
                val config = intent?.getStringExtra(EXTRA_CONFIG)
                val serverId = intent?.getStringExtra(EXTRA_SERVER_ID)
                if (config.isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
                startVpn(config, serverId)
            }
        }
        return START_STICKY
    }

    private fun startVpn(config: String, serverId: String?) {
        if (running) return
        running = true
        VpnState.setActiveServer(serverId)
        VpnState.setStatus(VpnStatus.CONNECTING)
        startForeground(NOTIF_ID, buildNotification("Подключение..."))
        Thread {
            try {
                registerNetworkCallback()
                val opts = SetupOptions().apply {
                    basePath = filesDir.absolutePath
                    workingPath = filesDir.absolutePath + "/work"
                    tempPath = cacheDir.absolutePath
                }
                java.io.File(opts.workingPath).mkdirs()
                Libbox.setup(opts)
                Libbox.setMemoryLimit(true)
                val service = Libbox.newService(config, this)
                service.start()
                boxService = service
                VpnState.setStatus(VpnStatus.CONNECTED)
                updateNotification("Подключено")
            } catch (e: Exception) {
                VpnState.setError(e.message ?: "Ошибка запуска ядра")
                VpnState.setStatus(VpnStatus.ERROR)
                stopVpn()
            }
        }.start()
    }

    private fun stopVpn() {
        running = false
        try { boxService?.close() } catch (_: Exception) {}
        boxService = null
        try { tunFd?.close() } catch (_: Exception) {}
        tunFd = null
        unregisterNetworkCallback()
        VpnState.setStatus(VpnStatus.DISCONNECTED)
        VpnState.setActiveServer(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    // ---------------- PlatformInterface ----------------

    override fun openTun(options: TunOptions): Int {
        val builder = Builder()
        builder.setSession("InkVPN")
        builder.setMtu(options.mtu)

        for (prefix in options.inet4Address.toList()) {
            builder.addAddress(prefix.address(), prefix.prefix())
        }
        for (prefix in options.inet6Address.toList()) {
            builder.addAddress(prefix.address(), prefix.prefix())
        }
        if (options.autoRoute) {
            // default routes
            builder.addRoute("0.0.0.0", 0)
            val v6 = options.inet6Address.toList()
            if (v6.isNotEmpty()) builder.addRoute("::", 0)
            try {
                val dns = options.dnsServerAddress
                if (dns != null && dns.value.isNotBlank()) builder.addDnsServer(dns.value)
            } catch (_: Exception) {}

            // per-app proxy
            val include = options.includePackage.toList()
            val exclude = options.excludePackage.toList()
            for (pkg in include) runCatching { builder.addAllowedApplication(pkg) }
            for (pkg in exclude) runCatching { builder.addDisallowedApplication(pkg) }
        }
        builder.setBlocking(false)
        val pfd = builder.establish() ?: error("VpnService.Builder.establish() returned null")
        tunFd = pfd
        return pfd.fd
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true

    override fun autoDetectInterfaceControl(fd: Int) {
        protect(fd)
    }

    override fun usePlatformDefaultInterfaceMonitor(): Boolean = true

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        if (net != null) {
            val link = cm.getLinkProperties(net)
            listener.updateDefaultInterface(link?.interfaceName ?: "", net.networkHandle.toInt())
        } else {
            listener.updateDefaultInterface("", -1)
        }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun usePlatformInterfaceGetter(): Boolean = false

    override fun getInterfaces(): NetworkInterfaceIterator =
        throw UnsupportedOperationException("platform interface getter disabled")

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState? = null

    override fun useProcFS(): Boolean = false

    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int
    ): Int {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.getConnectionOwnerUid(
            ipProtocol,
            InetSocketAddress(sourceAddress, sourcePort),
            InetSocketAddress(destinationAddress, destinationPort)
        )
    }

    override fun packageNameByUid(uid: Int): String {
        return packageManager.getPackagesForUid(uid)?.firstOrNull() ?: throw Exception("unknown uid")
    }

    override fun uidByPackageName(packageName: String): Int {
        return packageManager.getPackageUid(packageName, 0)
    }

    override fun sendNotification(notification: io.nekohasekai.libbox.Notification) {}

    override fun writeLog(message: String) {
        android.util.Log.i("sing-box", message)
    }

    // ---------------- network callback ----------------

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { defaultNetwork = network }
            override fun onLost(network: Network) { if (defaultNetwork == network) defaultNetwork = null }
        }
        networkCallback = cb
        runCatching { cm.registerNetworkCallback(request, cb) }
    }

    private fun unregisterNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        networkCallback = null
    }

    // ---------------- notification ----------------

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "InkVPN", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("InkVPN")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
