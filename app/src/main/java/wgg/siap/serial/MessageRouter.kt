package wgg.siap.serial

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.regex.Pattern

object MessageRouter {

    private val jsonPattern        = Pattern.compile("""\{"Msg":"[^"]*","Afiliacion_Comercio":"[^"]*","PuntoVenta":"[^"]*","Monto":"[^"]*","Divisa":"[^"]*","FechaTVM":"[^"]*","RefInt":"[^"]*"\}""")
    private val jsonPatternStatus  = Pattern.compile("""\{"Respuesta":\s*"[^"]*"\}""")
    private val jsonPatternCancel  = Pattern.compile("""\{"Msg":\s*"[^"]*"\}""")
    private val jsonPatternCom     = Pattern.compile("""\{.*?"Tipo":\s*"[^"]+".*?"Datos":\s*\{.*?\}.*?\}""")
    private val jsonPatternMessage = Pattern.compile("""\{.*?"Msg":"[^"]*".*?"TipoMensaje":"[^"]*".*?\}|\{.*?"TipoMensaje":"[^"]*".*?"Msg":"[^"]*".*?\}""")
    private val jsonPatternRefund  = Pattern.compile("""\{"Msg":"refund","Monto":"[^"]*","Folio":"[^"]*","Auth":"[^"]*"\}""")

    fun isValidFormat(message: String): Boolean {
        return when {
            jsonPattern.matcher(message).matches()        -> true
            jsonPatternStatus.matcher(message).matches()  -> true
            jsonPatternCancel.matcher(message).matches()  -> true
            jsonPatternCom.matcher(message).matches()     -> true
            jsonPatternMessage.matcher(message).matches() -> true
            jsonPatternRefund.matcher(message).matches()  -> true
            else -> false
        }
    }

    fun route(jsonString: String): IncomingMessage {
        if (!isValidFormat(jsonString)) return IncomingMessage.Unknown

        val jsonObject: JsonObject = JsonParser.parseString(jsonString).asJsonObject

        if (jsonObject.has("Msg")) {
            return when (jsonObject.get("Msg").asString) {
                "payment"     -> IncomingMessage.Payment(jsonObject)
                "refund"      -> IncomingMessage.Refund(jsonObject)
                "Status", "status", "Cancel" -> IncomingMessage.Control(jsonObject)
                "advertencia", "tolerancia", "tolerencia", "error" -> IncomingMessage.Screen(
                    jsonObject.get("Msg").asString,
                    jsonObject.get("TipoMensaje").asString
                )
                else -> IncomingMessage.Unknown
            }
        }

        if (jsonObject.has("Respuesta")) {
            return when (jsonObject.get("Respuesta").asString) {
                "ACK"  -> IncomingMessage.Ack
                "NACK" -> IncomingMessage.Nack
                else   -> IncomingMessage.Unknown
            }
        }

        return IncomingMessage.Unknown
    }
}
