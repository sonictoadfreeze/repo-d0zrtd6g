package com.inkvpn.app.core

import android.util.Base64
import java.net.URLDecoder
import java.util.Locale
import kotlin.math.abs

object B64 {
    /** Decode base64, tolerating URL-safe alphabet, missing padding and an optional `base64:` prefix. */
    fun decode(input: String): String {
        var s = input.trim()
        if (s.startsWith("base64:")) s = s.substring(7)
        s = s.replace("-", "+").replace("_", "/").replace("\n", "").replace("\r", "").replace(" ", "")
        val pad = (4 - s.length % 4) % 4
        s += "=".repeat(pad)
        return try {
            String(Base64.decode(s, Base64.DEFAULT))
        } catch (e: Exception) {
            input
        }
    }

    fun decodeBytes(input: String): ByteArray {
        var s = input.trim().replace("-", "+").replace("_", "/").replace("\n", "").replace("\r", "")
        val pad = (4 - s.length % 4) % 4
        s += "=".repeat(pad)
        return Base64.decode(s, Base64.DEFAULT)
    }

    /** Returns decoded value if the string is valid base64 (or `base64:`-prefixed), else the original. */
    fun maybeDecode(input: String?): String? {
        if (input == null) return null
        if (input.startsWith("base64:")) return decode(input)
        return input
    }
}

fun urlDecode(s: String): String = try {
    URLDecoder.decode(s, "UTF-8")
} catch (e: Exception) {
    s
}

object Format {
    fun bytes(value: Long): String {
        if (value <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
        var v = value.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return if (i == 0) "${v.toLong()} ${units[i]}"
        else String.format(Locale.US, "%.2f %s", v, units[i])
    }

    fun speed(bytesPerSec: Long): String = bytes(bytesPerSec) + "/s"
}

object DateUtils {
    /** Normalise a unix timestamp that may be in milliseconds (> 32000000000) to seconds. */
    fun normalizeExpire(expire: Long): Long =
        if (expire > 32_000_000_000L) expire / 1000 else expire

    /** Days remaining (negative when expired). 0 means no expiry set. */
    fun daysLeft(expireSeconds: Long): Long? {
        if (expireSeconds <= 0) return null
        val now = System.currentTimeMillis() / 1000
        return (expireSeconds - now) / 86_400
    }

    fun expireLabel(expireSeconds: Long): String {
        val days = daysLeft(expireSeconds) ?: return "—"
        return when {
            days > 0 -> "Осталось $days дн."
            days == 0L -> "Истекает сегодня"
            else -> "Истекло ${abs(days)} дн. назад"
        }
    }
}

object GithubUrl {
    /** Convert a github blob URL to its raw.githubusercontent.com equivalent. */
    fun toRaw(url: String): String {
        val m = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$").find(url) ?: return url
        val (user, repo, rest) = m.destructured
        return "https://raw.githubusercontent.com/$user/$repo/$rest"
    }
}
