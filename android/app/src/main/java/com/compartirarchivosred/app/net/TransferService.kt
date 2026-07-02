package com.compartirarchivosred.app.net

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.compartirarchivosred.app.model.IncomingInfo
import com.compartirarchivosred.app.model.Peer
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
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

    /** Carpeta de recepción elegida por el usuario (SAF). Si es null, se usa [downloadDir]. */
    @Volatile var receiveTreeUri: Uri? = null

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

                val name = sanitize(m.optString("name", "archivo"))
                val size = m.optLong("size", 0)
                val base = done
                val (out, savedName) = openReceiveOutput(name)
                out.use { os ->
                    f.copyExact(os, size) { written ->
                        onProgress(minOf(1f, (base + written) / total.toFloat()))
                    }
                }
                done += size
                onLog("Recibido: $savedName (${formatSize(size)})")
            }
        } catch (e: Exception) {
            onLog("Error en recepción: ${e.message}")
        } finally {
            onProgress(0f)
            if (announced) onIncoming(null)
            try { client.close() } catch (_: Exception) {}
        }
    }

    // ---- Envío ----

    /** Elemento a enviar: nombre, tamaño y un abridor de flujo perezoso. */
    private class OutItem(val name: String, val size: Long, val open: () -> InputStream)

    /** Envía archivos elegidos con el selector del sistema (content URIs). */
    fun sendUris(peer: Peer, uris: List<Uri>, pinProvider: () -> String?): Boolean {
        val items = uris.mapNotNull { u ->
            queryMeta(u)?.takeIf { it.size > 0 }?.let { meta ->
                OutItem(meta.name, meta.size) { context.contentResolver.openInputStream(u)!! }
            }
        }
        return sendItems(peer, items, pinProvider)
    }

    /** Envía archivos elegidos con el explorador interno (java.io.File). */
    fun sendFiles(peer: Peer, files: List<File>, pinProvider: () -> String?): Boolean {
        val items = files.filter { it.isFile && it.length() > 0 }
            .map { file -> OutItem(file.name, file.length()) { FileInputStream(file) } }
        return sendItems(peer, items, pinProvider)
    }

    private fun sendItems(peer: Peer, items: List<OutItem>, pinProvider: () -> String?): Boolean {
        if (items.isEmpty()) { onLog("No hay archivos válidos que enviar."); return false }
        val total = items.sumOf { it.size }

        try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(peer.host, peer.port), 8000)
                val f = Framed(sock.getInputStream(), sock.getOutputStream())

                f.writeLine(
                    JSONObject()
                        .put("v", Proto.VERSION).put("type", Proto.OFFER)
                        .put("name", selfName).put("fileCount", items.size)
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
                for (item in items) {
                    f.writeLine(
                        JSONObject().put("v", Proto.VERSION).put("type", Proto.FILE)
                            .put("name", item.name).put("size", item.size).toString()
                    )
                    val base = done
                    item.open().use { ins ->
                        f.writeBytes(ins, item.size) { sent ->
                            onProgress(minOf(1f, (base + sent) / total.toFloat()))
                        }
                    }
                    done += item.size
                    onLog("Enviado: ${item.name} (${formatSize(item.size)})")
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

    private data class FileMeta(val name: String, val size: Long)

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
        return FileMeta(name, size)
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

    /**
     * Abre el flujo de salida para un archivo entrante en la carpeta de recepción:
     * usa la carpeta SAF elegida por el usuario si existe; si no, [downloadDir].
     * Devuelve el flujo y el nombre final guardado.
     */
    private fun openReceiveOutput(name: String): Pair<OutputStream, String> {
        val uri = receiveTreeUri
        if (uri != null) {
            try {
                val tree = DocumentFile.fromTreeUri(context, uri)
                if (tree != null && tree.canWrite()) {
                    val doc = tree.createFile(mimeOf(name), name)
                    if (doc != null) {
                        val os = context.contentResolver.openOutputStream(doc.uri)
                        if (os != null) return Pair(os, doc.name ?: name)
                    }
                }
            } catch (_: Exception) {
                // Si falla la carpeta SAF, se cae a la carpeta interna.
            }
        }
        val file = uniqueFile(name)
        return Pair(FileOutputStream(file), file.name)
    }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
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
