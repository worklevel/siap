package wgg.siap.serial

import com.google.gson.JsonObject

object ResponseBuilder {

    const val ACK         = "{\"Respuesta\": \"ACK\", \"ErrorCode\": \"0\"}"
    const val ACK_ONLY    = "{\"Respuesta\": \"ACK\"}"
    const val NACK        = "{\"Respuesta\": \"NACK\"}"
    const val NETWORK_ERR = "{\"Respuesta\": \"ACK\", \"ErrorCode\": \"-1\"}"

    fun buildTransactionResponse(
        msg: String,
        descripcionError: String = "",
        tipoOperacion: String? = null,
        numTarjeta: String = "",
        nombre: String = "",
        fechaAuth: String = "",
        fechaCancel: String = "",
        numOper: String = "",
        numAuth: String = "",
        ref: String = "",
        tipoTarjeta: String = "",
        marcaTarjeta: String = ""
    ): String {
        val json = JsonObject()
        json.addProperty("Msg", msg)
        json.addProperty("DescripcionError", descripcionError)
        if (tipoOperacion != null) json.addProperty("TipoOperacion", tipoOperacion)
        json.addProperty("NumTarjeta", numTarjeta)
        json.addProperty("Nombre", nombre)
        json.addProperty("FechaAuth", fechaAuth)
        json.addProperty("FechaCancel", fechaCancel)
        json.addProperty("NumOper", numOper)
        json.addProperty("NumAuth", numAuth)
        json.addProperty("Ref", ref)
        json.addProperty("TipoTarjeta", tipoTarjeta)
        json.addProperty("MarcaTarjeta", marcaTarjeta)
        return json.toString()
    }
}
