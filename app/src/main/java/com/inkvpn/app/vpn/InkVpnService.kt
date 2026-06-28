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
    private var interfaceListener: InterfaceUpdateListener? = null
    private val asyncExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
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
        // Large stack: libbox's Go runtime does nested cgo callbacks (openTun) during start;
        // a small thread stack trips "stack split at bad time".
        Thread(null, {
            try {
                registerNetworkCallback()
                val opts = SetupOptions().apply {
                    basePath = filesDir.absolutePath
                    workingPath = filesDir.absolutePath + "/work"
                    tempPath = cacheDir.absolutePath
                }
                java.io.File(opts.workingPath).mkdirs()
                Libbox.setup(opts)
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
        }, "inkvpn-box", 16L * 1024 * 1024).start()
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
        // EXPERIMENT: do not read any libbox `options` field here. Each getter is a Java->Go
        // re-entry while we are already inside a Go->Java callback, which can trip
        // "stack split at bad time" on the amd64 build. Build the tun from fixed values that
        // match BoxConfigBuilder's tun inbound.
        val builder = Builder()
        builder.setSession("InkVPN")
        builder.setMtu(9000)
        builder.addAddress("172.19.0.1", 30)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("1.1.1.1")
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

    /**
     * Registers the listener but never calls back into Go synchronously: doing so from inside
     * this cgo callback trips Go's runtime ("stack split at bad time"). The initial update and
     * all subsequent updates are dispatched on [asyncExecutor].
     */
    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        interfaceListener = listener
        asyncExecutor.execute { notifyDefaultInterface(listener) }
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        interfaceListener = null
    }

    private fun notifyDefaultInterface(listener: InterfaceUpdateListener) {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork
        if (net != null) {
            val link = cm.getLinkProperties(net)
            listener.updateDefaultInterface(link?.interfaceName ?: "", net.networkHandle.toInt())
        } else {
            listener.updateDefaultInterface("", -1)
        }
    }

    // Returning false lets sing-box enumerate interfaces with Go's own net.Interfaces(), avoiding
    // a Java->Go reentry inside the Go->Java getInterfaces callback (which trips the amd64 runtime).
    override fun usePlatformInterfaceGetter(): Boolean = false

    override fun getInterfaces(): NetworkInterfaceIterator =
        BoxNetworkInterfaceIterator(runCatching { enumerateInterfaces() }.getOrDefault(emptyList()))

    override fun underNetworkExtension(): Boolean = false

    override fun includeAllNetworks(): Boolean = false

    override fun clearDNSCache() {}

    override fun readWIFIState(): WIFIState? = null

    override fun useProcFS(): Boolean = false

    override fun findConnectionOwner(
        ipProtocol: Int, sourceAddress: String, sourcePort: Int, destinationAddress: String, destinationPort: Int
    ): Int {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return -1
        return runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.getConnectionOwnerUid(
                ipProtocol,
                InetSocketAddress(sourceAddress, sourcePort),
                InetSocketAddress(destinationAddress, destinationPort)
            )
        }.getOrDefault(-1)
    }

    override fun packageNameByUid(uid: Int): String =
        runCatching { packageManager.getPackagesForUid(uid)?.firstOrNull() ?: "" }.getOrDefault("")

    override fun uidByPackageName(packageName: String): Int =
        runCatching { packageManager.getPackageUid(packageName, 0) }.getOrDefault(-1)

    override fun sendNotification(notification: io.nekohasekai.libbox.Notification) {}

    override fun writeLog(message: String) {
        android.util.Log.i("sing-box", message)
    }

    private fun enumerateInterfaces(): List<io.nekohasekai.libbox.NetworkInterface> {
        val result = mutableListOf<io.nekohasekai.libbox.NetworkInterface>()
        val ifaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return result
        for (nif in ifaces) {
            val item = io.nekohasekai.libbox.NetworkInterface()
            item.name = nif.name
            item.index = nif.index
            item.mtu = runCatching { nif.mtu }.getOrDefault(-1)
            var flags = 0
            runCatching { if (nif.isUp) flags = flags or syscallIFF_UP }
            runCatching { if (nif.supportsMulticast()) flags = flags or syscallIFF_MULTICAST }
            runCatching { if (nif.isLoopback) flags = flags or syscallIFF_LOOPBACK }
            runCatching { if (nif.isPointToPoint) flags = flags or syscallIFF_POINTOPOINT }
            item.flags = flags
            val addrs = nif.interfaceAddresses.mapNotNull { ia ->
                ia.address?.hostAddress?.let { "$it/${ia.networkPrefixLength}" }
            }
            item.addresses = BoxStringIterator(addrs)
            result.add(item)
        }
        return result
    }

    // ---------------- network callback ----------------

    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                defaultNetwork = network
                interfaceListener?.let { l -> asyncExecutor.execute { notifyDefaultInterface(l) } }
            }
            override fun onLost(network: Network) {
                if (defaultNetwork == network) defaultNetwork = null
                interfaceListener?.let { l -> asyncExecutor.execute { notifyDefaultInterface(l) } }
            }
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
