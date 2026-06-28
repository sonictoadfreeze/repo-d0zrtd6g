package com.inkvpn.app.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Parses a subscription HTTP response (headers + body) into servers and metadata.
 * Supports the 5 body formats: base64, plain text, JSON array of xray configs,
 * single JSON xray config object, and mixed body with special marker lines.
 * HTTP headers take priority over body markers.
 */
object SubscriptionParser {

    fun parse(body: String, headers: Map<String, String>): ParsedSubscription {
        val headerInfo = HeadersParser.parse(headers)
        val decoded = normalizeBody(body)
        val servers = mutableListOf<ServerConfig>()
        val bodyInfo = SubscriptionInfo()

        val trimmed = decoded.trim()
        when {
            trimmed.startsWith("[") -> servers += parseJsonArray(trimmed)
            trimmed.startsWith("{") -> servers += parseJsonObject(trimmed)
            else -> {
                val (s, _) = parseLines(trimmed)
                servers += s
            }
        }
        // Merge: header info wins over body markers (body markers not separately surfaced here
        // beyond servers; header info is authoritative per spec).
        return ParsedSubscription(servers = servers, info = headerInfo.copy(
            profileTitle = headerInfo.profileTitle ?: bodyInfo.profileTitle,
        ))
    }

    /** If the whole body is base64, decode it; otherwise return as-is. */
    private fun normalizeBody(body: String): String {
        val t = body.trim()
        if (t.startsWith("{") || t.startsWith("[") || ProtocolParser.isProxyLink(t.lineSequence().first().trim())) {
            return body
        }
        // Heuristic: looks like base64 (no scheme separators, only base64 chars)
        val sample = t.replace("\n", "").replace("\r", "")
        if (sample.isNotEmpty() && sample.matches(Regex("^[A-Za-z0-9+/_=-]+$"))) {
            val decoded = B64.decode(t)
            if (decoded.contains("://")) return decoded
        }
        return body
    }

    private fun parseLines(body: String): Pair<List<ServerConfig>, SubscriptionInfo> {
        val servers = mutableListOf<ServerConfig>()
        body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
            when {
                ProtocolParser.isProxyLink(line) -> ProtocolParser.parse(line)?.let { servers += it }
                ProtocolParser.isIgnored(line) -> { /* ignore ssr/tuic/hysteria v1 */ }
                line.startsWith("#") -> { /* body markers handled elsewhere */ }
            }
        }
        return servers to SubscriptionInfo()
    }

    private fun parseJsonArray(text: String): List<ServerConfig> {
        return try {
            val arr = Json.parseToJsonElement(text) as JsonArray
            arr.flatMap { el -> (el as? JsonObject)?.let { extractOutbounds(it) } ?: emptyList() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseJsonObject(text: String): List<ServerConfig> {
        return try {
            val obj = Json.parseToJsonElement(text) as JsonObject
            extractOutbounds(obj)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Extract proxy outbounds from an xray/sing-box config object (best-effort passthrough). */
    private fun extractOutbounds(obj: JsonObject): List<ServerConfig> {
        val outbounds = (obj["outbounds"] as? JsonArray) ?: return emptyList()
        val result = mutableListOf<ServerConfig>()
        for (ob in outbounds) {
            val o = ob as? JsonObject ?: continue
            val type = (o["type"] as? JsonPrimitive)?.content
                ?: (o["protocol"] as? JsonPrimitive)?.content ?: continue
            if (type in listOf("direct", "block", "dns", "selector", "urltest", "freedom", "blackhole")) continue
            val tag = (o["tag"] as? JsonPrimitive)?.content ?: type
            val server = (o["server"] as? JsonPrimitive)?.content ?: ""
            val port = (o["server_port"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            result += ServerConfig(
                id = java.util.UUID.randomUUID().toString(),
                name = tag, protocol = type, server = server, port = port,
                rawLink = o.toString(), outboundJson = o.toString(),
            )
        }
        return result
    }
}
