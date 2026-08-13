package xyz.mastriel.cutapi.resources.uploader

import java.net.IDN
import java.net.URI

internal fun resourcePackHostFromHandshake(handshakeHostname: String?): String? {
    val address = handshakeHostname
        ?.substringBefore('\u0000')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null

    if (address.any(Char::isISOControl) || address.any { it in "/?#@" }) return null

    val host = when {
        address.startsWith('[') -> {
            val bracket = address.indexOf(']')
            if (bracket <= 1) return null
            address.substring(1, bracket)
        }

        address.count { it == ':' } == 1 -> {
            val possiblePort = address.substringAfter(':')
            if (possiblePort.toIntOrNull() != null) address.substringBefore(':') else address
        }

        else -> address
    }.takeIf(String::isNotEmpty) ?: return null

    return if (':' in host) {
        host
    } else {
        runCatching { IDN.toASCII(host) }.getOrNull()
    }
}

internal fun resourcePackHttpUrl(host: String, port: Int): String =
    URI("http", null, host, port, "/", null, null).toASCIIString()
