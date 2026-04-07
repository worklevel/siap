package wgg.siap.serial

import android.util.Log
import java.nio.charset.StandardCharsets

class SerialManager(private val onMessage: (IncomingMessage, String) -> Unit) {

    private val msgBuffer = StringBuilder()
    private val readLock = Any()

    fun clearBuffer() {
        synchronized(readLock) { msgBuffer.clear() }
    }

    fun onBytesReceived(msg: ByteArray?) {
        synchronized(readLock) {
            try {
                val data = msg?.let { String(it, StandardCharsets.UTF_8) } ?: return
                msgBuffer.append(data)
                val raw = msgBuffer.toString()
                val startIndex = raw.indexOf('{')
                val endIndex = if (startIndex != -1) findFirstJsonEnd(raw, startIndex) else -1

                if (startIndex != -1 && endIndex != -1) {
                    val jsonString = raw.substring(startIndex, endIndex + 1)
                    val remainder = raw.substring(endIndex + 1).trimStart()
                    msgBuffer.clear()
                    msgBuffer.append(remainder)

                    Log.d("SIAP", "[SERIAL:IN] $jsonString")
                    val message = MessageRouter.route(jsonString)
                    onMessage(message, jsonString)
                }
            } catch (e: Exception) {
                Log.e("SIAP", "[SERIAL:PARSE_ERROR]", e)
                msgBuffer.clear()
                onMessage(IncomingMessage.Unknown, "")
            }
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
