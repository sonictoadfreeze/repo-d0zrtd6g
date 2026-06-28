package com.inkvpn.app.core

/** Parsed result of an inkvpn:// (or proxy) deep link / pasted text. */
sealed class DeepLinkAction {
    object Connect : DeepLinkAction()
    object Disconnect : DeepLinkAction()
    object Toggle : DeepLinkAction()
    object Status : DeepLinkAction()
    data class AddSubscription(val url: String) : DeepLinkAction()
    data class AddServer(val link: String) : DeepLinkAction()
    data class AddRouting(val source: String, val activate: Boolean, val autoUpdate: Boolean) : DeepLinkAction()
    data class Unknown(val raw: String) : DeepLinkAction()
}

object DeepLink {

    /**
     * Normalize the URL value the user stores for a subscription.
     * Accepts `inkvpn://add/https://...`, `happ://add/https://...`, or a bare `https://...`,
     * and always returns the plain fetchable https url.
     */
    fun extractSubscriptionUrl(input: String): String {
        var s = input.trim()
        // strip known deep-link prefixes that wrap a real url
        val prefixes = listOf(
            "inkvpn://add/", "inkvpn://import/", "inkvpn://addsub/",
            "happ://add/", "happ://import/"
        )
        for (p in prefixes) {
            if (s.startsWith(p, ignoreCase = true)) {
                s = s.substring(p.length)
                break
            }
        }
        s = GithubUrl.toRaw(s)
        return s
    }

    /** The deep-link form we store/display in the edit screen, like Happ's `happ://add/<url>`. */
    fun toStoredForm(plainUrl: String): String = "inkvpn://add/$plainUrl"

    fun parse(raw: String): DeepLinkAction {
        val s = raw.trim()
        return when {
            ProtocolParser.isProxyLink(s) -> DeepLinkAction.AddServer(s)
            s.startsWith("inkvpn://connect", true) -> DeepLinkAction.Connect
            s.startsWith("inkvpn://disconnect", true) -> DeepLinkAction.Disconnect
            s.startsWith("inkvpn://toggle", true) -> DeepLinkAction.Toggle
            s.startsWith("inkvpn://status", true) -> DeepLinkAction.Status
            s.contains("autorouting/onadd/", true) -> DeepLinkAction.AddRouting(afterMarker(s, "autorouting/onadd/"), activate = true, autoUpdate = true)
            s.contains("autorouting/add/", true) -> DeepLinkAction.AddRouting(afterMarker(s, "autorouting/add/"), activate = false, autoUpdate = true)
            s.contains("routing/onadd/", true) -> DeepLinkAction.AddRouting(afterMarker(s, "routing/onadd/"), activate = true, autoUpdate = false)
            s.contains("routing/add/", true) -> DeepLinkAction.AddRouting(afterMarker(s, "routing/add/"), activate = false, autoUpdate = false)
            s.startsWith("inkvpn://add/", true) || s.startsWith("inkvpn://import/", true) ||
                s.startsWith("happ://add/", true) || s.startsWith("happ://import/", true) ->
                DeepLinkAction.AddSubscription(extractSubscriptionUrl(s))
            s.startsWith("http://", true) || s.startsWith("https://", true) ->
                DeepLinkAction.AddSubscription(GithubUrl.toRaw(s))
            else -> DeepLinkAction.Unknown(s)
        }
    }

    private fun afterMarker(s: String, marker: String): String {
        val idx = s.indexOf(marker, ignoreCase = true)
        return if (idx < 0) s else GithubUrl.toRaw(s.substring(idx + marker.length))
    }
}
