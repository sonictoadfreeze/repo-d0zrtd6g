package com.inkvpn.app.vpn

import io.nekohasekai.libbox.RoutePrefix
import io.nekohasekai.libbox.RoutePrefixIterator
import io.nekohasekai.libbox.StringIterator

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
