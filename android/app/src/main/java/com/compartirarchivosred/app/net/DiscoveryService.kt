package com.compartirarchivosred.app.net

import android.content.Context
import android.net.wifi.WifiManager
import com.compartirarchivosred.app.model.Peer
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** Descubrimiento por UDP broadcast en la LAN (puerto 45454). */
class DiscoveryService(
    private val context: Context,
    private val selfId: String,
    private val selfName: String,
    private val onPeersChanged: (List<Peer>) -> Unit
) {
    @Volatile private var running = false
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val peers = ConcurrentHashMap<String, Peer>()

    fun start() {
        if (running) return
        running = true

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("compartirarchivosred").apply {
            setReferenceCounted(true)
            acquire()
        }

        val s = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(Proto.DISCOVERY_PORT))
        }
        socket = s

        thread(isDaemon = true) { listenLoop(s) }
        thread(isDaemon = true) { announceLoop(s) }
        thread(isDaemon = true) { expireLoop() }
    }

    private fun announceLoop(s: DatagramSocket) {
        val data = JSONObject()
            .put("v", Proto.VERSION)
            .put("type", Proto.ANNOUNCE)
            .put("id", selfId)
            .put("name", selfName)
            .put("platform", "android")
            .put("port", Proto.TRANSFER_PORT)
            .toString().toByteArray(Charsets.UTF_8)
        val addr = InetAddress.getByName("255.255.255.255")
        while (running) {
            try { s.send(DatagramPacket(data, data.size, addr, Proto.DISCOVERY_PORT)) } catch (_: Exception) {}
            try { Thread.sleep(2000) } catch (_: Exception) {}
        }
    }

    private fun listenLoop(s: DatagramSocket) {
        val buf = ByteArray(2048)
        while (running) {
            try {
                val p = DatagramPacket(buf, buf.size)
                s.receive(p)
                val o = JSONObject(String(p.data, 0, p.length, Charsets.UTF_8))
                if (o.optString("type") != Proto.ANNOUNCE) continue
                val id = o.optString("id")
                if (id.isEmpty() || id == selfId) continue
                val isNew = !peers.containsKey(id)
                peers[id] = Peer(
                    id = id,
                    name = o.optString("name", "?"),
                    platform = o.optString("platform", "?"),
                    host = p.address?.hostAddress ?: "",
                    port = o.optInt("port", Proto.TRANSFER_PORT),
                    lastSeen = System.currentTimeMillis()
                )
                if (isNew) onPeersChanged(snapshot())
            } catch (_: Exception) {}
        }
    }

    private fun expireLoop() {
        while (running) {
            try { Thread.sleep(3000) } catch (_: Exception) {}
            val now = System.currentTimeMillis()
            val before = peers.size
            peers.entries.removeAll { now - it.value.lastSeen > 10000 }
            if (peers.size != before) onPeersChanged(snapshot())
        }
    }

    private fun snapshot(): List<Peer> = peers.values.sortedBy { it.name }

    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        try { multicastLock?.release() } catch (_: Exception) {}
    }
}
