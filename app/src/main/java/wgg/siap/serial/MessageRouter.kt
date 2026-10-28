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

        val jsonObject = JsonParser.parseString(jsonString).asJsonObject

        //1. Primero ACK/NACK
        if (jsonObject.has("Respuesta")) {
            return when (jsonObject.get("Respuesta").asString) {
                "ACK"  -> IncomingMessage.Ack
                "NACK" -> IncomingMessage.Nack
                else   -> IncomingMessage.Unknown
            }
        }

        //2. Luego mensajes con Msg
        if (jsonObject.has("Msg")) {
            val msgValue = jsonObject.get("Msg").asString.trim()

            return when {
                msgValue.equals("payment", true) ->
                    IncomingMessage.Payment(jsonObject)

                msgValue.equals("refund", true) ->
                    IncomingMessage.Refund(jsonObject)

                msgValue.equals("status", true) ||
                        msgValue.equals("cancel", true) ->
                    IncomingMessage.Control(jsonObject)

                msgValue.toIntOrNull() != null -> //clave para {"Msg":"0"}
                    IncomingMessage.TransactionResult(jsonObject)

                msgValue in listOf("advertencia", "tolerancia", "tolerencia", "error") -> {
                    val tipoMensaje = jsonObject.get("TipoMensaje")?.asString ?: ""
                    IncomingMessage.Screen(msgValue, tipoMensaje)
                }else -> IncomingMessage.Unknown
            }
        }
        return IncomingMessage.Unknown
    }
}
