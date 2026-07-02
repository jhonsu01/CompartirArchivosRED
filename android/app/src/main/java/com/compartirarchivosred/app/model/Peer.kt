package com.compartirarchivosred.app.model

data class Peer(
    val id: String,
    val name: String,
    val platform: String,
    val host: String,
    val port: Int,
    val lastSeen: Long
) {
    val display: String get() = "$platform  ·  $host"
}

data class IncomingInfo(
    val senderName: String,
    val fileCount: Int,
    val totalSize: Long,
    val pin: String
)
