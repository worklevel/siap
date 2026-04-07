package wgg.siap.serial

import com.google.gson.JsonObject

sealed class IncomingMessage {
    data class Payment(val json: JsonObject) : IncomingMessage()
    data class Refund(val json: JsonObject) : IncomingMessage()
    data class Control(val json: JsonObject) : IncomingMessage()
    data class Screen(val type: String, val message: String) : IncomingMessage()
    object Ack : IncomingMessage()
    object Nack : IncomingMessage()
    object Unknown : IncomingMessage()
}
