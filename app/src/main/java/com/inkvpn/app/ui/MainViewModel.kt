package com.inkvpn.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inkvpn.app.InkVpnApp
import com.inkvpn.app.core.AppSettings
import com.inkvpn.app.core.DeepLink
import com.inkvpn.app.core.DeepLinkAction
import com.inkvpn.app.core.ServerConfig
import com.inkvpn.app.core.Subscription
import com.inkvpn.app.vpn.VpnController
import com.inkvpn.app.vpn.VpnState
import com.inkvpn.app.vpn.VpnStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class UiMessage(val text: String, val isError: Boolean = false)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as InkVpnApp).repository

    val subscriptions: StateFlow<List<Subscription>> =
        repo.subscriptions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val status: StateFlow<VpnStatus> = VpnState.status
    val activeServerId: StateFlow<String?> = VpnState.activeServerId
    val selectedServerId: StateFlow<String?> =
        repo.selectedServerId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val settingsMutex = Mutex()

    /** Apply a transform to the current settings and persist. */
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsMutex.withLock { repo.saveSettings(transform(repo.currentSettings())) }
        }
    }

    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<UiMessage?>(null)

    private val _pings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val pings: StateFlow<Map<String, Int>> = _pings

    fun pingAllServers(servers: List<ServerConfig>) {
        viewModelScope.launch {
            servers.forEach { server ->
                launch {
                    val ms = measurePing(server.server, server.port)
                    if (ms != null) {
                        _pings.value = _pings.value + (server.id to ms)
                    }
                }
            }
        }
    }

    private suspend fun measurePing(host: String, port: Int): Int? = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            val start = System.currentTimeMillis()
            socket.connect(InetSocketAddress(host, port), 3000)
            val elapsed = (System.currentTimeMillis() - start).toInt()
            socket.close()
            elapsed
        } catch (_: Exception) {
            null
        }
    }

    val allServers: List<ServerConfig>
        get() = subscriptions.value.flatMap { it.servers }

    fun selectedServer(): ServerConfig? {
        val id = selectedServerId.value ?: allServers.firstOrNull()?.id
        return allServers.firstOrNull { it.id == id }
    }

    fun selectServer(id: String) = viewModelScope.launch { repo.setSelectedServer(id) }

    fun addSubscription(name: String?, url: String, existingId: String? = null) {
        viewModelScope.launch {
            loading = true
            message = null
            try {
                val sub = repo.addOrUpdateSubscription(name, url, existingId)
                message = UiMessage("Импортировано серверов: ${sub.servers.size}")
            } catch (e: Exception) {
                message = UiMessage("Ошибка импорта: ${e.message}", isError = true)
            } finally {
                loading = false
            }
        }
    }

    fun refresh(id: String) {
        viewModelScope.launch {
            loading = true
            try { repo.refresh(id); message = UiMessage("Подписка обновлена") }
            catch (e: Exception) { message = UiMessage("Ошибка обновления: ${e.message}", isError = true) }
            finally { loading = false }
        }
    }

    fun delete(id: String) = viewModelScope.launch { repo.delete(id) }
    fun togglePin(id: String) = viewModelScope.launch { repo.togglePin(id) }

    fun connectSelected() {
        val server = selectedServer()
        if (server == null) { message = UiMessage("Нет выбранного сервера", true); return }
        VpnController.start(getApplication(), server)
    }

    fun connect(server: ServerConfig) {
        viewModelScope.launch { repo.setSelectedServer(server.id) }
        VpnController.start(getApplication(), server)
    }

    fun disconnect() = VpnController.stop(getApplication())

    /** Handle a deep link / pasted import string. Returns the url to confirm if it's a subscription. */
    fun handleDeepLink(raw: String): String? {
        return when (val action = DeepLink.parse(raw)) {
            is DeepLinkAction.AddSubscription -> action.url
            is DeepLinkAction.AddServer -> { addServerLink(action.link); null }
            DeepLinkAction.Connect -> { connectSelected(); null }
            DeepLinkAction.Disconnect -> { disconnect(); null }
            DeepLinkAction.Toggle -> { if (status.value == VpnStatus.CONNECTED) disconnect() else connectSelected(); null }
            else -> null
        }
    }

    private fun addServerLink(link: String) {
        val server = com.inkvpn.app.core.ProtocolParser.parse(link)
        if (server != null) message = UiMessage("Сервер ${server.name} добавлен")
        else message = UiMessage("Не удалось разобрать ссылку", true)
    }
}
