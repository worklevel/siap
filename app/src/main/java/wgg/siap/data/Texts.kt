package wgg.siap.data

data class Texts(
    val msgLoadingPayment: String   = "Procesando pago",
    val msgLoadingChip: String      = "No retire su tarjeta",
    val msgSuccessPrefix: String    = "Autorización",
    val msgCancel: String           = "Operación cancelada",
    val msgTimeout: String          = "Tiempo de espera agotado",
    val msgCardRemoved: String      = "Tarjeta retirada, intente de nuevo",
    val msgServiceError: String     = "Terminal fuera de servicio",
    val msgNetworkError: String     = "Pago bancario no disponible",
    val autoDismissErrorMs: Long    = 3000L,
    val autoDismissSuccessMs: Long  = 3000L
)
