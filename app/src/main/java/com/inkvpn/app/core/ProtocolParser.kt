package com.inkvpn.app.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.util.UUID

/**
 * Parses proxy share links (vless://, vmess://, trojan://, ss://, hysteria2://, socks://, wireguard://)
 * into [ServerConfig], building a sing-box outbound JSON object for each.
 */
object ProtocolParser {

    private val SUPPORTED = listOf(
        "vless://", "vmess://", "trojan://", "ss://",
        "hysteria2://", "hy2://", "socks://", "socks5://", "wireguard://", "wg://"
    )
    private val IGNORED = listOf("ssr://", "tuic://", "hysteria://")

    fun isProxyLink(line: String): Boolean =
        SUPPORTED.any { line.startsWith(it, ignoreCase = true) }

    fun isIgnored(line: String): Boolean =
        IGNORED.any { line.startsWith(it, ignoreCase = true) }

    fun parse(link: String): ServerConfig? {
        val line = link.trim()
        return try {
            when {
                line.startsWith("vless://", true) -> parseVless(line)
                line.startsWith("vmess://", true) -> parseVmess(line)
                line.startsWith("trojan://", true) -> parseTrojan(line)
                line.startsWith("ss://", true) -> parseShadowsocks(line)
                line.startsWith("hysteria2://", true) || line.startsWith("hy2://", true) -> parseHysteria2(line)
                line.startsWith("socks://", true) || line.startsWith("socks5://", true) -> parseSocks(line)
                line.startsWith("wireguard://", true) || line.startsWith("wg://", true) -> parseWireguard(line)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---- helpers ----

    private fun query(uri: URI): Map<String, String> {
        val q = uri.rawQuery ?: return emptyMap()
        return q.split("&").mapNotNull {
            val idx = it.indexOf('=')
            if (idx < 0) it to "" else urlDecode(it.substring(0, idx)) to urlDecode(it.substring(idx + 1))
        }.toMap()
    }

    /** fragment = name?serverDescription=base64  ->  everything before first '?' is the name. */
    private fun fragmentNameDesc(uri: URI): Pair<String, String?> {
        val frag = uri.rawFragment?.let { urlDecode(it) } ?: return ("" to null)
        val q = frag.indexOf('?')
        if (q < 0) return frag to null
        val name = frag.substring(0, q)
        val rest = frag.substring(q + 1)
        val desc = rest.split("&").firstOrNull { it.startsWith("serverDescription=") }
            ?.substringAfter("=")?.let { B64.decode(it) }
        return name to desc
    }

    private fun tlsBlock(q: Map<String, String>, server: String): JsonObject? {
        val sec = q["security"]?.lowercase()
        if (sec != "tls" && sec != "reality" && sec != "xtls") return null
        return buildJsonObject {
            put("enabled", true)
            val sni = q["sni"] ?: q["peer"] ?: q["host"] ?: server
            put("server_name", sni)
            q["alpn"]?.takeIf { it.isNotBlank() }?.let { alpn ->
                put("alpn", buildJsonArray { alpn.split(",").forEach { add(it.trim()) } })
            }
            if (q["allowInsecure"] == "1" || q["insecure"] == "1") put("insecure", true)
            val fp = q["fp"]?.takeIf { it.isNotBlank() }
            if (fp != null) put("utls", buildJsonObject {
                put("enabled", true)
                put("fingerprint", fp)
            })
            val pbk = q["pbk"]
            if (sec == "reality" && !pbk.isNullOrBlank()) {
                put("reality", buildJsonObject {
                    put("enabled", true)
                    put("public_key", pbk)
                    q["sid"]?.let { put("short_id", it) }
                })
            }
        }
    }

    /** Returns transport JsonObject (or null for raw tcp) and a normalized transport label. */
    private fun transportBlock(q: Map<String, String>): Pair<JsonObject?, String> {
        var type = (q["type"] ?: q["net"] ?: "tcp").lowercase()
        if (type == "splithttp") type = "xhttp"
        return when (type) {
            "ws", "websocket" -> buildJsonObject {
                put("type", "ws")
                q["path"]?.let { put("path", it) }
                val host = q["host"]
                if (!host.isNullOrBlank()) put("headers", buildJsonObject { put("Host", host) })
            } to "ws"
            "grpc" -> buildJsonObject {
                put("type", "grpc")
                (q["serviceName"] ?: q["servicename"])?.let { put("service_name", it) }
            } to "grpc"
            "httpupgrade" -> buildJsonObject {
                put("type", "httpupgrade")
                q["path"]?.let { put("path", it) }
                q["host"]?.let { put("host", it) }
            } to "httpupgrade"
            "http", "h2" -> buildJsonObject {
                put("type", "http")
                q["host"]?.let { put("host", buildJsonArray { add(it) }) }
                q["path"]?.let { put("path", it) }
            } to "http"
            "xhttp" -> null to "xhttp" // not supported by sing-box core
            else -> null to "tcp"
        }
    }

    private fun parseVless(line: String): ServerConfig {
        val uri = URI(line)
        val uuid = uri.userInfo
        val server = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val q = query(uri)
        val (name, desc) = fragmentNameDesc(uri)
        val (transport, transportLabel) = transportBlock(q)
        val security = q["security"]?.lowercase() ?: "none"
        val supported = transportLabel != "xhttp"

        val outbound = buildJsonObject {
            put("type", "vless")
            put("tag", "proxy")
            put("server", server)
            put("server_port", port)
            put("uuid", uuid)
            put("packet_encoding", "xudp")
            // flow only valid with raw tcp
            val flow = q["flow"]
            if (!flow.isNullOrBlank() && transport == null) put("flow", flow)
            tlsBlock(q, server)?.let { put("tls", it) }
            transport?.let { put("transport", it) }
        }
        return ServerConfig(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "$server:$port" },
            description = desc,
            protocol = "vless",
            server = server,
            port = port,
            transport = transportLabel,
            security = security,
            rawLink = line,
            outboundJson = if (supported) outbound.toString() else null,
            supportedByCore = supported,
        )
    }

    private fun parseVmess(line: String): ServerConfig {
        val json = B64.decode(line.removePrefix("vmess://").removePrefix("VMESS://"))
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(json) as JsonObject
        fun s(k: String): String? = (obj[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val server = s("add") ?: ""
        val port = s("port")?.toIntOrNull() ?: 443
        val net = (s("net") ?: "tcp").lowercase()
        val tls = s("tls")
        val name = s("ps") ?: "$server:$port"
        val q = mutableMapOf<String, String>()
        s("host")?.let { q["host"] = it }
        s("path")?.let { q["path"] = it }
        s("sni")?.let { q["sni"] = it }
        s("type")?.let { q["serviceName"] = it }
        q["type"] = net
        val (transport, transportLabel) = transportBlock(q)

        val outbound = buildJsonObject {
            put("type", "vmess")
            put("tag", "proxy")
            put("server", server)
            put("server_port", port)
            put("uuid", s("id") ?: "")
            put("security", s("scy") ?: "auto")
            put("alter_id", s("aid")?.toIntOrNull() ?: 0)
            if (tls == "tls") put("tls", buildJsonObject {
                put("enabled", true)
                put("server_name", s("sni") ?: s("host") ?: server)
                if (!s("alpn").isNullOrBlank()) put("alpn", buildJsonArray { s("alpn")!!.split(",").forEach { add(it.trim()) } })
            })
            transport?.let { put("transport", it) }
        }
        return ServerConfig(
            id = UUID.randomUUID().toString(), name = name, protocol = "vmess",
            server = server, port = port, transport = transportLabel,
            security = if (tls == "tls") "tls" else "none",
            rawLink = line, outboundJson = outbound.toString(),
        )
    }

    private fun parseTrojan(line: String): ServerConfig {
        val uri = URI(line)
        val password = uri.userInfo
        val server = uri.host
        val port = if (uri.port > 0) uri.port else 443
        val q = query(uri)
        val (name, desc) = fragmentNameDesc(uri)
        val (transport, transportLabel) = transportBlock(q)
        val outbound = buildJsonObject {
            put("type", "trojan")
            put("tag", "proxy")
            put("server", server)
            put("server_port", port)
            put("password", password)
            put("tls", (tlsBlock(q + ("security" to (q["security"] ?: "tls")), server) ?: buildJsonObject {
                put("enabled", true); put("server_name", q["sni"] ?: server)
            }))
            transport?.let { put("transport", it) }
        }
        return ServerConfig(
            id = UUID.randomUUID().toString(), name = name.ifBlank { "$server:$port" }, description = desc,
            protocol = "trojan", server = server, port = port, transport = transportLabel,
            security = "tls", rawLink = line, outboundJson = outbound.toString(),
        )
    }

    private fun parseShadowsocks(line: String): ServerConfig {
        // ss://base64(method:password)@host:port#name  OR  ss://base64(method:password@host:port)#name
        val withoutScheme = line.removePrefix("ss://")
        val hashIdx = withoutScheme.indexOf('#')
        val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme
        val frag = if (hashIdx >= 0) withoutScheme.substring(hashIdx + 1) else ""
        val name = urlDecode(frag.substringBefore('?'))
        val desc = frag.substringAfter("serverDescription=", "").takeIf { it.isNotBlank() }?.let { B64.decode(it) }

        val method: String
        val password: String
        val server: String
        val port: Int
        if (main.contains("@")) {
            val userPart = main.substringBefore("@")
            val hostPart = main.substringAfter("@").substringBefore("?")
            val decoded = B64.decode(userPart)
            method = decoded.substringBefore(":")
            password = decoded.substringAfter(":")
            server = hostPart.substringBeforeLast(":")
            port = hostPart.substringAfterLast(":").substringBefore("/").toIntOrNull() ?: 443
        } else {
            val decoded = B64.decode(main.substringBefore("?"))
            val creds = decoded.substringBefore("@")
            val hostPart = decoded.substringAfter("@")
            method = creds.substringBefore(":")
            password = creds.substringAfter(":")
            server = hostPart.substringBeforeLast(":")
            port = hostPart.substringAfterLast(":").toIntOrNull() ?: 443
        }
        val outbound = buildJsonObject {
            put("type", "shadowsocks")
            put("tag", "proxy")
            put("server", server)
            put("server_port", port)
            put("method", method)
            put("password", password)
        }
        return ServerConfig(
            id = UUID.randomUUID().toString(), name = name.ifBlank { "$server:$port" }, description = desc,
            protocol = "shadowsocks", server = server, port = port, transport = "tcp",
            security = "none", rawLink = line, outboundJson = outbound.toString(),
        )
    }

    private fun parseHysteria2(line: String): ServerConfig {
        val normalized = line.replaceFirst("hy2://", "hysteria2://")
        val uri = URI(normalized)
        val password = uri.userInfo
        val server = uri.host
        // multi-port: first port used for connection
        val portRaw = if (uri.port > 0) uri.port else
            normalized.substringAfter("@").substringAfter(":").substringBefore("?")
                .split(",", "-").first().toIntOrNull() ?: 443
        val q = query(uri)
        val (name, desc) = fragmentNameDesc(uri)
        val outbound = buildJsonObject {
            put("type", "hysteria2")
            put("tag", "proxy")
            put("server", server)
            put("server_port", portRaw)
            put("password", password ?: "")
            q["obfs"]?.let { obfs ->
                put("obfs", buildJsonObject {
                    put("type", obfs)
                    q["obfs-password"]?.let { put("password", it) }
                })
            }
            put("tls", buildJsonObject {
                put("enabled", true)
                put("server_name", q["sni"] ?: server)
                if (q["insecure"] == "1") put("insecure", true)
                if (!q["alpn"].isNullOrBlank()) put("alpn", buildJsonArray { q["alpn"]!!.split(",").forEach { add(it.trim()) } })
                if (!q["pinSHA256"].isNullOrBlank()) put("certificate", q["pinSHA256"]!!)
            })
        }
        return ServerConfig(
            id = UUID.randomUUID().toString(), name = name.ifBlank { "$server:$portRaw" }, description = desc,
            protocol = "hysteria2", server = server, port = portRaw, transport = "udp",
            security = "tls", rawLink = line, outboundJson = outbound.toString(),
        )
    }

    private fun parseSocks(line: String): ServerConfig {
        val uri = URI(line.replaceFirst("socks5://", "socks://"))
        val server = uri.host
        val port = if (uri.port > 0) uri.port else 1080
        val (name, desc) = fragmentNameDesc(uri)
        val user = uri.userInfo?.substringBefore(":")
        val pass = uri.userInfo?.substringAfter(":", "")
        val outbound = buildJsonObject {
            put("type", "socks")
            put("tag", "proxy")
            put("server", server)
            put("server_port", port)
            put("version", "5")
            if (!user.isNullOrBlank()) {
                put("username", user)
                put("password", pass ?: "")
            }
        }
        return ServerConfig(
            id = UUID.randomUUID().toString(), name = name.ifBlank { "$server:$port" }, description = desc,
            protocol = "socks", server = server, port = port, transport = "tcp",
            security = "none", rawLink = line, outboundJson = outbound.toString(),
        )
    }

    private fun parseWireguard(line: String): ServerConfig {
        val uri = URI(line.replaceFirst("wg://", "wireguard://"))
        val secretKey = uri.userInfo
        val server = uri.host
        val port = if (uri.port > 0) uri.port else 51820
        val q = query(uri)
        val (name, desc) = fragmentNameDesc(uri)
        val outbound = buildJsonObject {
            put("type", "wireguard")
            put("tag", "proxy")
            put("server", server)
            put("server_port", port)
            put("private_key", secretKey ?: "")
            q["publickey"]?.let { put("peer_public_key", it) }
            q["address"]?.let { addr ->
                put("local_address", buildJsonArray { addr.split(",").forEach { add(it.trim()) } })
            }
            q["mtu"]?.toIntOrNull()?.let { put("mtu", it) }
            q["reserved"]?.let { res ->
                put("reserved", buildJsonArray { res.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { add(it) } })
            }
        }
        return ServerConfig(
            id = UUID.randomUUID().toString(), name = name.ifBlank { "$server:$port" }, description = desc,
            protocol = "wireguard", server = server, port = port, transport = "udp",
            security = "none", rawLink = line, outboundJson = outbound.toString(),
        )
    }
}
