package com.inkvpn.app.core

/** Parses the (case-insensitive) subscription HTTP headers into [SubscriptionInfo]. */
object HeadersParser {

    fun parse(rawHeaders: Map<String, String>): SubscriptionInfo {
        val h = rawHeaders.mapKeys { it.key.lowercase() }
        fun get(vararg names: String): String? =
            names.firstNotNullOfOrNull { h[it]?.takeIf { v -> v.isNotBlank() } }

        var title = B64.maybeDecode(get("profile-title", "subscription-name"))
        if (title == null) {
            val cd = get("content-disposition")
            if (cd != null) {
                val fn = Regex("filename\\*?=\"?([^\";]+)\"?").find(cd)?.groupValues?.get(1)
                if (fn != null && !fn.endsWith(".txt") && !fn.endsWith(".yaml") && !fn.endsWith(".yml")) {
                    title = fn
                }
            }
        }

        val userinfo = get("subscription-userinfo")
        var upload = 0L; var download = 0L; var total = 0L; var expire = 0L
        if (userinfo != null) {
            userinfo.split(";").forEach { part ->
                val kv = part.trim().split("=")
                if (kv.size == 2) {
                    val value = kv[1].trim().toLongOrNull() ?: 0L
                    when (kv[0].trim().lowercase()) {
                        "upload" -> upload = value
                        "download" -> download = value
                        "total" -> total = value
                        "expire" -> expire = DateUtils.normalizeExpire(value)
                    }
                }
            }
        }

        return SubscriptionInfo(
            profileTitle = title,
            profileDescription = B64.maybeDecode(get("profile-description")),
            supportUrl = get("support-url"),
            webPageUrl = get("profile-web-page-url", "homepage"),
            premiumUrl = get("premium-url"),
            announce = B64.maybeDecode(get("announce")),
            announceUrl = get("announce-url"),
            updateIntervalHours = get("profile-update-interval")?.toIntOrNull(),
            upload = upload, download = download, total = total, expire = expire,
            subInfoText = B64.maybeDecode(get("sub-info-text")),
            subInfoColor = get("sub-info-color"),
            subInfoButtonText = get("sub-info-button-text"),
            subInfoButtonLink = get("sub-info-button-link"),
            subExpire = get("sub-expire") == "1",
            subExpireButtonLink = get("sub-expire-button-link"),
            sortOrder = get("sort-order"),
        )
    }
}
