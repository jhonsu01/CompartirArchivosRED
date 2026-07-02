package com.compartirarchivosred.app.net

/** Constantes del protocolo común (ver shared/protocol/PROTOCOL.md). */
object Proto {
    const val VERSION = 1
    const val DISCOVERY_PORT = 45454
    const val TRANSFER_PORT = 45455

    const val ANNOUNCE = "ANNOUNCE"
    const val OFFER = "OFFER"
    const val PIN_REQUIRED = "PIN_REQUIRED"
    const val PIN = "PIN"
    const val ACCEPTED = "ACCEPTED"
    const val REJECTED = "REJECTED"
    const val FILE = "FILE"
    const val DONE = "DONE"
}

fun formatSize(bytes: Long): String {
    val u = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < u.size - 1) { v /= 1024; i++ }
    return if (i == 0) "$bytes B" else String.format("%.1f %s", v, u[i])
}
