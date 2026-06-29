package com.inkvpn.app.core

import kotlinx.serialization.Serializable

/** A single parsed proxy server / node. */
@Serializable
data class ServerConfig(
    val id: String,
    val name: String,
    val description: String? = null,
    val protocol: String,          // vless, vmess, trojan, shadowsocks, hysteria2, socks, wireguard
    val server: String,
    val port: Int,
    val transport: String? = null, // tcp, ws, grpc, xhttp, ...
    val security: String? = null,  // tls, reality, none
    val rawLink: String,
    /** Pre-built sing-box outbound JSON object for this server, or null if core-unsupported. */
    val outboundJson: String? = null,
    val supportedByCore: Boolean = true,
) {
    val displayProtocol: String
        get() = when (protocol) {
            "shadowsocks" -> "SS"
            "vless" -> "VLESS"
            "vmess" -> "VMess"
            "trojan" -> "Trojan"
            "hysteria2" -> "Hysteria2"
            "socks" -> "SOCKS5"
            "wireguard" -> "WireGuard"
            else -> protocol.uppercase()
        }

    /** Pretty transport label (gRPC, xHTTP, WS, ...) or null for plain TCP. */
    val displayTransport: String?
        get() = when (transport?.lowercase()) {
            null, "", "tcp", "raw" -> null
            "ws", "websocket" -> "WS"
            "grpc" -> "gRPC"
            "xhttp", "splithttp" -> "xHTTP"
            "httpupgrade" -> "HTTPUpgrade"
            "http", "h2" -> "HTTP/2"
            "quic" -> "QUIC"
            else -> transport.uppercase()
        }

    /** Protocol plus transport variant, e.g. "VLESS · gRPC". */
    val displayProtocolFull: String
        get() = displayTransport?.let { "$displayProtocol · $it" } ?: displayProtocol
}

/** Parsed subscription metadata coming from HTTP headers and/or body markers. */
@Serializable
data class SubscriptionInfo(
    val profileTitle: String? = null,
    val profileDescription: String? = null,
    val supportUrl: String? = null,
    val webPageUrl: String? = null,
    val premiumUrl: String? = null,
    val announce: String? = null,
    val announceUrl: String? = null,
    val updateIntervalHours: Int? = null,
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val subInfoText: String? = null,
    val subInfoColor: String? = null,
    val subInfoButtonText: String? = null,
    val subInfoButtonLink: String? = null,
    val subExpire: Boolean = false,
    val subExpireButtonLink: String? = null,
    val sortOrder: String? = null,
)

/** A stored subscription: its source URL plus everything parsed from it. */
@Serializable
data class Subscription(
    val id: String,
    val name: String,
    val url: String,                 // stored deep-link form: inkvpn://add/https://...
    val rawUrl: String,              // plain https url used for fetching
    val servers: List<ServerConfig> = emptyList(),
    val info: SubscriptionInfo = SubscriptionInfo(),
    val lastUpdated: Long = 0,
    val pinned: Boolean = false,
)

/** Result of parsing a subscription body + headers. */
data class ParsedSubscription(
    val servers: List<ServerConfig>,
    val info: SubscriptionInfo,
)
