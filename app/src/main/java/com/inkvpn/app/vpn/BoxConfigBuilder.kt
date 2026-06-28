package com.inkvpn.app.vpn

import com.inkvpn.app.core.ServerConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds a full sing-box config JSON wrapping a single proxy outbound. */
object BoxConfigBuilder {

    fun build(server: ServerConfig): String {
        val proxy = Json.parseToJsonElement(server.outboundJson ?: error("server has no core config")) as JsonObject

        val config = buildJsonObject {
            put("log", buildJsonObject {
                put("level", "info")
                put("timestamp", true)
            })
            put("dns", buildJsonObject {
                put("servers", buildJsonArray {
                    add(buildJsonObject {
                        put("tag", "remote")
                        put("address", "https://1.1.1.1/dns-query")
                        put("detour", "proxy")
                    })
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
                put("strategy", "prefer_ipv4")
                put("final", "remote")
            })
            put("inbounds", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put("interface_name", "inkvpn-tun")
                    put("address", buildJsonArray { add("172.19.0.1/30") })
                    put("mtu", 9000)
                    put("auto_route", true)
                    put("strict_route", false)
                    put("stack", "system")
                    put("sniff", true)
                    put("sniff_override_destination", false)
                })
            })
            put("outbounds", buildJsonArray {
                add(proxy)
                add(buildJsonObject { put("type", "direct"); put("tag", "direct") })
                add(buildJsonObject { put("type", "dns"); put("tag", "dns-out") })
            })
            put("route", buildJsonObject {
                put("rules", buildJsonArray {
                    add(buildJsonObject { put("protocol", "dns"); put("outbound", "dns-out") })
                    add(buildJsonObject { put("ip_is_private", true); put("outbound", "direct") })
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
}
