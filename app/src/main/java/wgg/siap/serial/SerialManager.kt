package wgg.siap.serial

import android.util.Log
import java.nio.charset.StandardCharsets

class SerialManager(private val onMessage: (IncomingMessage, String) -> Unit) {

    private val msgBuffer = StringBuilder()
    private val readLock = Any()
    var yaRecibiResultado = false

    fun clearBuffer() {
        synchronized(readLock) { msgBuffer.clear() }
    }

    fun onBytesReceived(msg: ByteArray?) {
        // 1. Lista temporal para guardar lo que extraigamos
        val messagesToProcess = mutableListOf<Pair<IncomingMessage, String>>()

        synchronized(readLock) {
            try {
                val data = msg?.let { String(it, StandardCharsets.UTF_8) } ?: return
                msgBuffer.append(data)
                var raw = msgBuffer.toString()

                // 2. Bucle 'while' para sacar TODOS los JSONs completos que hayan llegado juntos
                while (true) {
                    val startIndex = raw.indexOf('{')
                    val endIndex = if (startIndex != -1) findFirstJsonEnd(raw, startIndex) else -1

                    if (startIndex != -1 && endIndex != -1) {
                        val jsonString = raw.substring(startIndex, endIndex + 1)
                        val message = MessageRouter.route(jsonString)
                        messagesToProcess.add(Pair(message, jsonString))

                        raw = raw.substring(endIndex + 1).trimStart()
                    } else {
                        break // Ya no hay más JSONs completos en el buffer
                    }
                }

                // 3. Limpiamos y guardamos la basura o fragmentos incompletos
                msgBuffer.clear()
                msgBuffer.append(raw)

            } catch (e: Exception) {
                Log.e("SIAP", "[SERIAL:PARSE_ERROR]", e)
                msgBuffer.clear()
                messagesToProcess.add(Pair(IncomingMessage.Unknown, ""))
            }
        } // 🔓 ¡AQUÍ SE LIBERA EL CANDADO DEL PUERTO SERIAL!

        // 4. Se envían los mensajes a tu Activity/ViewModel sin bloquear la lectura
        messagesToProcess.forEach { (message, jsonString) ->
            onMessage(message, jsonString)
        }
    }

    private fun findFirstJsonEnd(raw: String, start: Int): Int {
        var depth = 0
        for (i in start until raw.length) {
            when (raw[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }
}
