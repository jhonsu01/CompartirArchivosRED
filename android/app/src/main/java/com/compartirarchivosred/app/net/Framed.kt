package com.compartirarchivosred.app.net

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Enmarcado: líneas JSON delimitadas por '\n' intercaladas con bloques de bytes
 * crudos de tamaño conocido. Un búfer interno evita "comerse" bytes de payload.
 */
class Framed(private val input: InputStream, private val output: OutputStream) {
    private val buf = ByteArray(8192)
    private var start = 0
    private var end = 0

    fun readLine(): String? {
        val sb = ArrayList<Byte>(128)
        while (true) {
            if (start >= end) {
                end = input.read(buf, 0, buf.size)
                start = 0
                if (end <= 0) return if (sb.isEmpty()) null else String(sb.toByteArray(), Charsets.UTF_8)
            }
            val b = buf[start++]
            if (b == '\n'.code.toByte()) return String(sb.toByteArray(), Charsets.UTF_8)
            if (b != '\r'.code.toByte()) sb.add(b)
        }
    }

    fun copyExact(dest: OutputStream, count: Long, onProgress: (Long) -> Unit) {
        var remaining = count
        while (remaining > 0) {
            if (start >= end) {
                end = input.read(buf, 0, buf.size)
                start = 0
                if (end <= 0) throw IOException("Conexión cerrada durante la recepción.")
            }
            val avail = end - start
            val take = minOf(avail.toLong(), remaining).toInt()
            dest.write(buf, start, take)
            start += take
            remaining -= take
            onProgress(count - remaining)
        }
    }

    fun writeLine(s: String) {
        output.write((s + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
    }

    fun writeBytes(src: InputStream, count: Long, onProgress: (Long) -> Unit) {
        val tmp = ByteArray(81920)
        var sent = 0L
        while (sent < count) {
            val toRead = minOf(tmp.size.toLong(), count - sent).toInt()
            val n = src.read(tmp, 0, toRead)
            if (n <= 0) break
            output.write(tmp, 0, n)
            sent += n
            onProgress(sent)
        }
        output.flush()
    }
}
