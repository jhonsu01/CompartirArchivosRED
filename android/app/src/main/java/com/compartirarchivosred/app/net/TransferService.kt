package com.compartirarchivosred.app.net

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.compartirarchivosred.app.model.IncomingInfo
import com.compartirarchivosred.app.model.Peer
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Random
import kotlin.concurrent.thread

/** Servidor de recepción (TCP 45455) + cliente de envío. Ver PROTOCOL.md. */
class TransferService(
    private val context: Context,
    private val selfName: String,
    private val onLog: (String) -> Unit,
    private val onProgress: (Float) -> Unit,
    private val onIncoming: (IncomingInfo?) -> Unit
) {
    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null

    val downloadDir: File =
        (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Downloads")).apply { mkdirs() }

    fun startServer() {
        if (running) return
        running = true
        thread(isDaemon = true) {
            try {
                val ss = ServerSocket(Proto.TRANSFER_PORT)
                serverSocket = ss
                while (running) {
                    val client = ss.accept()
                    thread(isDaemon = true) { handleIncoming(client) }
                }
            } catch (e: Exception) {
                if (running) onLog("Servidor detenido: ${e.message}")
            }
        }
    }

    private fun handleIncoming(client: Socket) {
        var ok = false
        var announced = false
        try {
            val f = Framed(client.getInputStream(), client.getOutputStream())
            val offerLine = f.readLine() ?: return
            val offer = JSONObject(offerLine)
            if (offer.optString("type") != Proto.OFFER) return

            val pin = String.format("%06d", Random().nextInt(1_000_000))
            val info = IncomingInfo(
                senderName = offer.optString("name", "?"),
                fileCount = offer.optInt("fileCount", 0),
                totalSize = offer.optLong("totalSize", 0),
                pin = pin
            )
            announced = true
            onIncoming(info)
            onLog("Solicitud de «${info.senderName}» (${info.fileCount} archivo/s). PIN: $pin")

            val expires = System.currentTimeMillis() + 60_000
            f.writeLine(msg(Proto.PIN_REQUIRED))

            val pinLine = f.readLine()
            val pinMsg = if (pinLine != null) JSONObject(pinLine) else null
            if (pinMsg == null || pinMsg.optString("type") != Proto.PIN) { f.writeLine(reject("denied")); return }
            if (System.currentTimeMillis() > expires) { f.writeLine(reject("expired")); onLog("PIN expirado."); return }
            if (pinMsg.optString("pin") != pin) { f.writeLine(reject("bad_pin")); onLog("PIN incorrecto."); return }

            f.writeLine(msg(Proto.ACCEPTED))

            val total = if (info.totalSize > 0) info.totalSize else 1L
            var done = 0L
            while (true) {
                val line = f.readLine() ?: break
                val m = JSONObject(line)
                val type = m.optString("type")
                if (type == Proto.DONE) { ok = true; break }
                if (type != Proto.FILE) continue

                val dest = uniqueFile(sanitize(m.optString("name", "archivo")))
                val size = m.optLong("size", 0)
                val base = done
                FileOutputStream(dest).use { fos ->
                    f.copyExact(fos, size) { written ->
                        onProgress(minOf(1f, (base + written) / total.toFloat()))
                    }
                }
                done += size
                onLog("Recibido: ${dest.name} (${formatSize(size)})")
            }
        } catch (e: Exception) {
            onLog("Error en recepción: ${e.message}")
        } finally {
            onProgress(0f)
            if (announced) onIncoming(null)
            try { client.close() } catch (_: Exception) {}
        }
    }

    /**
     * Envía los archivos indicados por sus [uris] al [peer].
     * [pinProvider] bloquea hasta que el usuario introduce el PIN mostrado en el
     * receptor; devuelve null si cancela.
     */
    fun sendFiles(peer: Peer, uris: List<Uri>, pinProvider: () -> String?): Boolean {
        val metas = uris.mapNotNull { queryMeta(it) }.filter { it.size > 0 }
        if (metas.isEmpty()) { onLog("No hay archivos válidos que enviar."); return false }
        val total = metas.sumOf { it.size }

        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(peer.host, peer.port), 8000)
                val f = Framed(sock.getInputStream(), sock.getOutputStream())

                f.writeLine(
                    JSONObject()
                        .put("v", Proto.VERSION).put("type", Proto.OFFER)
                        .put("name", selfName).put("fileCount", metas.size)
                        .put("totalSize", total).toString()
                )

                val reqLine = f.readLine()
                val req = if (reqLine != null) JSONObject(reqLine) else null
                if (req == null || req.optString("type") != Proto.PIN_REQUIRED) {
                    onLog("El receptor no solicitó PIN."); return false
                }

                val pin = pinProvider() ?: run { onLog("Envío cancelado."); return false }
                f.writeLine(
                    JSONObject().put("v", Proto.VERSION).put("type", Proto.PIN)
                        .put("pin", pin.trim()).toString()
                )

                val respLine = f.readLine()
                val resp = if (respLine != null) JSONObject(respLine) else null
                if (resp == null || resp.optString("type") != Proto.ACCEPTED) {
                    onLog("Rechazado por el receptor: ${resp?.optString("reason") ?: "desconocido"}")
                    return false
                }

                var done = 0L
                for (m in metas) {
                    f.writeLine(
                        JSONObject().put("v", Proto.VERSION).put("type", Proto.FILE)
                            .put("name", m.name).put("size", m.size).toString()
                    )
                    val base = done
                    context.contentResolver.openInputStream(m.uri)?.use { ins ->
                        f.writeBytes(ins, m.size) { sent ->
                            onProgress(minOf(1f, (base + sent) / total.toFloat()))
                        }
                    } ?: throw Exception("No se pudo abrir ${m.name}")
                    done += m.size
                    onLog("Enviado: ${m.name} (${formatSize(m.size)})")
                }
                f.writeLine(msg(Proto.DONE))
                onLog("Transferencia completada.")
                return true
            }
        } catch (e: Exception) {
            onLog("Error en envío: ${e.message}")
            return false
        } finally {
            onProgress(0f)
        }
    }

    private data class FileMeta(val uri: Uri, val name: String, val size: Long)

    private fun queryMeta(uri: Uri): FileMeta? {
        var name = "archivo"
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (ni >= 0) c.getString(ni)?.let { name = it }
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
            if (size <= 0L) {
                context.contentResolver.openFileDescriptor(uri, "r")?.use {
                    if (it.statSize > 0) size = it.statSize
                }
            }
        } catch (_: Exception) {
            return null
        }
        return FileMeta(uri, name, size)
    }

    private fun msg(type: String) =
        JSONObject().put("v", Proto.VERSION).put("type", type).toString()

    private fun reject(reason: String) =
        JSONObject().put("v", Proto.VERSION).put("type", Proto.REJECTED).put("reason", reason).toString()

    private fun sanitize(raw: String): String {
        val name = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return name.ifBlank { "archivo" }
    }

    private fun uniqueFile(name: String): File {
        var f = File(downloadDir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            f = File(downloadDir, "$base ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
    }
}
