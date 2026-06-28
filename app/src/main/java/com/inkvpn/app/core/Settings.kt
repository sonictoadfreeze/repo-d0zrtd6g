package com.inkvpn.app.core

import kotlinx.serialization.Serializable

enum class TunnelMode(val label: String) { TUN_PROXY("TUN + Proxy"), PROXY_ONLY("Только прокси") }

enum class IpStrategy(val label: String, val strategy: String) {
    AUTO("Авто", "prefer_ipv4"),
    IPV4_ONLY("Только IPv4", "ipv4_only"),
    IPV6_ONLY("Только IPv6", "ipv6_only"),
    PREFER_IPV6("Предпочесть IPv6", "prefer_ipv6"),
}

enum class DnsChoice(val label: String, val doh: String, val ip: String, val secondaryDoh: String? = null) {
    CLOUDFLARE_GOOGLE("Cloudflare + Google", "https://1.1.1.1/dns-query", "1.1.1.1", "https://8.8.8.8/dns-query"),
    CLOUDFLARE("Cloudflare", "https://1.1.1.1/dns-query", "1.1.1.1"),
    GOOGLE("Google", "https://8.8.8.8/dns-query", "8.8.8.8"),
    ADGUARD("AdGuard", "https://94.140.14.14/dns-query", "94.140.14.14"),
    QUAD9("Quad9", "https://9.9.9.9/dns-query", "9.9.9.9"),
}

enum class ThemeChoice(val label: String) { GREEN("Зелёная"), INDIGO("Индиго"), CYAN("Бирюзовая") }

enum class LangChoice(val label: String, val tag: String?) { SYSTEM("Как в системе", null), RU("Русский", "ru"), EN("English", "en") }

enum class PerAppMode(val label: String) { OFF("Выкл"), INCLUDE("Только выбранные"), EXCLUDE("Кроме выбранных") }

/** All user-configurable settings, persisted as JSON in DataStore. */
@Serializable
data class AppSettings(
    // Tunnel
    val tunnelMode: TunnelMode = TunnelMode.TUN_PROXY,
    val routingProfiles: Boolean = false,
    val speedInNotification: Boolean = false,
    val wakelock: Boolean = false,
    val fragment: Boolean = false,
    val mux: Boolean = false,
    val ipStrategy: IpStrategy = IpStrategy.AUTO,
    val dns: DnsChoice = DnsChoice.CLOUDFLARE_GOOGLE,
    val memoryLimitMb: Int = 100,
    val unlimitedMemory: Boolean = false,
    val proxyOnly: Boolean = false,
    val socks5Auth: Boolean = false,
    val socks5User: String = "",
    val socks5Pass: String = "",
    val localPort: Int = 2080,
    // Connection
    val autoConnect: Boolean = false,
    val connectOnBoot: Boolean = false,
    val killSwitch: Boolean = false,
    val allowLan: Boolean = true,
    val hotspot: Boolean = false,
    val lanThroughProxy: Boolean = false,
    // Appearance
    val theme: ThemeChoice = ThemeChoice.GREEN,
    val language: LangChoice = LangChoice.SYSTEM,
    // Per-app proxy
    val perAppMode: PerAppMode = PerAppMode.OFF,
    val perAppPackages: List<String> = emptyList(),
)
