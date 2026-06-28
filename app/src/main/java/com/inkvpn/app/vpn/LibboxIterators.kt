package com.inkvpn.app.vpn

import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.RoutePrefix
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.StringIterator

// Linux interface flag bits expected by sing-box (matches golang.org/x/sys/unix).
const val syscallIFF_UP = 0x1
const val syscallIFF_LOOPBACK = 0x8
const val syscallIFF_POINTOPOINT = 0x10
const val syscallIFF_MULTICAST = 0x1000

fun RoutePrefixIterator?.toList(): List<RoutePrefix> {
    val list = mutableListOf<RoutePrefix>()
    val it = this ?: return list
    while (it.hasNext()) list.add(it.next())
    return list
}

fun StringIterator?.toList(): List<String> {
    val list = mutableListOf<String>()
    val it = this ?: return list
    while (it.hasNext()) list.add(it.next())
    return list
}

/** Adapts a Kotlin list of strings to libbox's StringIterator. */
class BoxStringIterator(private val items: List<String>) : StringIterator {
    private var index = 0
    override fun hasNext(): Boolean = index < items.size
    override fun next(): String = items[index++]
    override fun len(): Int = items.size
}

/** Adapts a Kotlin list of NetworkInterface to libbox's NetworkInterfaceIterator. */
class BoxNetworkInterfaceIterator(private val items: List<NetworkInterface>) : NetworkInterfaceIterator {
    private var index = 0
    override fun hasNext(): Boolean = index < items.size
    override fun next(): NetworkInterface = items[index++]
}
