package com.xalitoria.vote

import com.xalitoria.vote.config.VoteConfig.Link
import java.net.URI

/** [url] as an address a player can be sent to: http or https with a host, nothing else (no `file:`, no `javascript:`). Null if it is not one. */
fun webAddress(url: String): URI? = try {
    URI(url.trim()).takeIf { (it.scheme == "http" || it.scheme == "https") && !it.host.isNullOrBlank() }
} catch (e: Exception) {
    null
}

/** The links of [links] that have a name and an address a player can be sent to, in the order given; [rejected] is told each that has not. */
fun usableLinks(links: List<Link>, rejected: (Link) -> Unit = {}): List<Link> = links.filter {
    val usable = it.name.isNotBlank() && webAddress(it.url) != null
    if (!usable) rejected(it)
    usable
}.map { it.copy(url = it.url.trim()) }
