package com.inkvpn.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VpnStatus { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** Global, observable VPN state shared between the service and the UI. */
object VpnState {
    private val _status = MutableStateFlow(VpnStatus.DISCONNECTED)
    val status: StateFlow<VpnStatus> = _status

    private val _activeServerId = MutableStateFlow<String?>(null)
    val activeServerId: StateFlow<String?> = _activeServerId

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    private val _connectedSince = MutableStateFlow(0L)
    val connectedSince: StateFlow<Long> = _connectedSince

    fun setStatus(s: VpnStatus) {
        _status.value = s
        if (s == VpnStatus.CONNECTED) _connectedSince.value = System.currentTimeMillis()
        if (s == VpnStatus.DISCONNECTED) _connectedSince.value = 0L
    }

    fun setActiveServer(id: String?) { _activeServerId.value = id }
    fun setError(msg: String?) { _lastError.value = msg }
}
