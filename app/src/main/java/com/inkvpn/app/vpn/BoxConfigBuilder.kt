package com.inkvpn.app.vpn

import com.inkvpn.app.core.AppSettings
import com.inkvpn.app.core.ServerConfig
import com.inkvpn.app.core.TunnelMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds a full sing-box config JSON wrapping a single proxy outbound, applying [AppSettings]. */
object BoxConfigBuilder {

    fun build(server: ServerConfig, settings: AppSettings = AppSettings()): String =
        build(server.outboundJson ?: error("server has no core config"), settings)

    fun build(outboundJson: String, settings: AppSettings): String {
        val baseProxy = Json.parseToJsonElement(outboundJson) as JsonObject
        val proxy = if (settings.mux) withMultiplex(baseProxy) else baseProxy
        val proxyOnly = settings.tunnelMode == TunnelMode.PROXY_ONLY || settings.proxyOnly

        val config = buildJsonObject {
            put("log", buildJsonObject {
                put("level", "info")
                put("timestamp", true)
            })
            put("dns", buildJsonObject {
                put("servers", buildJsonArray {
                    add(buildJsonObject {
                        put("tag", "remote")
                        put("address", settings.dns.doh)
                        put("detour", "proxy")
                    })
                    settings.dns.secondaryDoh?.let {
                        add(buildJsonObject {
                            put("tag", "remote2")
                            put("address", it)
                            put("detour", "proxy")
                        })
                    }
                    add(buildJsonObject {
                        put("tag", "local")
                        put("address", "https://223.5.5.5/dns-query")
                        put("detour", "direct")
                    })
                })
                put("rules", buildJsonArray {
                    add(buildJsonObject {
                        put("outbound", "any")
                        put("server", "local")
                    })
                })
                put("strategy", settings.ipStrategy.strategy)
                put("final", "remote")
            })
            put("inbounds", buildJsonArray {
                if (proxyOnly) {
                    add(buildJsonObject {
                        put("type", "mixed")
                        put("tag", "mixed-in")
                        put("listen", if (settings.hotspot) "0.0.0.0" else "127.0.0.1")
                        put("listen_port", settings.localPort)
                        if (settings.socks5Auth && settings.socks5User.isNotBlank()) {
                            put("users", buildJsonArray {
                                add(buildJsonObject {
                                    put("username", settings.socks5User)
                                    put("password", settings.socks5Pass)
                                })
                            })
                        }
                    })
                } else {
                    add(buildJsonObject {
                        put("type", "tun")
                        put("tag", "tun-in")
                        put("interface_name", "inkvpn-tun")
                        put("address", buildJsonArray { add("172.19.0.1/30") })
                        put("mtu", 9000)
                        put("auto_route", true)
                        put("strict_route", false)
                        put("stack", "gvisor")
                        put("sniff", true)
                        put("sniff_override_destination", false)
                    })
                }
            })
            put("outbounds", buildJsonArray {
                add(proxy)
                add(buildJsonObject { put("type", "direct"); put("tag", "direct") })
                add(buildJsonObject { put("type", "dns"); put("tag", "dns-out") })
            })
            put("route", buildJsonObject {
                put("rules", buildJsonArray {
                    add(buildJsonObject { put("protocol", "dns"); put("outbound", "dns-out") })
                    // LAN through proxy: keep private IPs in the tunnel; otherwise send them direct.
                    if (!settings.lanThroughProxy) {
                        add(buildJsonObject { put("ip_is_private", true); put("outbound", "direct") })
                    }
                })
                put("auto_detect_interface", true)
                put("final", "proxy")
            })
            put("experimental", buildJsonObject {
                put("cache_file", buildJsonObject { put("enabled", true) })
            })
        }
        return config.toString()
    }

    private fun withMultiplex(proxy: JsonObject): JsonObject = buildJsonObject {
        proxy.forEach { (k, v: JsonElement) -> put(k, v) }
        put("multiplex", buildJsonObject {
            put("enabled", true)
            put("protocol", "h2mux")
            put("max_streams", 8)
        })
    }
}
