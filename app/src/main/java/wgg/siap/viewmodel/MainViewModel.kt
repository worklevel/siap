package wgg.siap.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import mx.com.mit.mobile.mitmobilelibrary.model.MITCard
import mx.com.mit.mobile.mitmobilelibrary.model.MITError
import mx.com.mit.mobile.mitmobilelibrary.model.MITTransaction
import wgg.siap.InitApplication.Companion.prefs
import wgg.siap.serial.ResponseBuilder
import wgg.siap.system.CheckNetworkTask
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _screenState = MutableLiveData<ScreenState>(ScreenState.Idle)
    val screenState: LiveData<ScreenState> = _screenState

    private val _serialOutput = MutableLiveData<String>()
    val serialOutput: LiveData<String> = _serialOutput

    @Volatile var lastResponseMessage: String = ""
        private set
    var typeReading: String = ""
        private set
    @Volatile var reference: String = ""
        private set
    private var amount: String = ""
    private var refInt: String = ""

    val isProcessingPayment   = AtomicBoolean(false)
    val networkAttempts       = AtomicInteger(0)
    val communicationAttempts = AtomicInteger(0)
    val responseAttempts      = AtomicInteger(0)

    companion object {
        private const val TAG = "SIAP"
        fun currentDate(): String =
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "MX")).format(Date())
    }

    // ========== Serial ==========

    private fun sendSerial(msg: String) {
        Log.i(TAG, "[SERIAL:OUT] $msg")
        _serialOutput.postValue(msg)
    }

    private fun sendTransactionResult(msg: String) {
        lastResponseMessage = msg
        responseAttempts.set(0)
        Log.i(TAG, "[TXN:RESULT] $msg")
        sendSerial(msg)
    }

    // ========== Payment ==========
    fun paymentProcess(msg: com.google.gson.JsonObject) {

        Log.d(TAG, "[DEBUG] Estado actual del Lock: ${isProcessingPayment.get()}")

        // --- NUEVA LÓGICA DE FUERZA BRUTA ---
        /*if (isProcessingPayment.get()) {
            // Si ya está en true, pero el estado de la pantalla es IDLE o ERROR,
            // significa que el SDK se quedó colgado y no nos avisó.
            val currentState = _screenState.value
            if (currentState is ScreenState.Idle || currentState is ScreenState.Error) {
                Log.w(TAG, "[LOCK:FORCE_RESET] El lock estaba en TRUE pero la pantalla está libre. Reseteando...")
                isProcessingPayment.set(false)
            }
        }*/
        if (isProcessingPayment.get()) {
            val currentState = _screenState.value
            // Log detallado para saber POR QUÉ no entraría al reset
            Log.d(TAG, "[DEBUG:LOCK] Lock en TRUE. Estado actual de pantalla: ${currentState?.javaClass?.simpleName}")

            if (currentState is ScreenState.Idle || currentState is ScreenState.Error) {
                Log.w(TAG, "[LOCK:FORCE_RESET] Reseteando lock...")
                isProcessingPayment.set(false)
            } else {
                Log.d(TAG, "[DEBUG:LOCK] No se reseteó porque el estado es ${currentState}")
            }
        }

        if (!isProcessingPayment.compareAndSet(false, true)) {
            Log.w(TAG, "[PAYMENT:SKIP] Ya en proceso, omitiendo RefInt=${msg.get("RefInt").asString}")
            return
        }

        var shouldKeepLockOpen = false

        try {
            communicationAttempts.set(0)
            val isConnected = CheckNetworkTask(getApplication()).execute()

            if (isConnected) {
                val isReferenceEmpty = prefs.getReference().isEmpty()

                lastResponseMessage = ""
                networkAttempts.set(0)
                amount = msg.get("Monto").asString
                refInt = msg.get("RefInt").asString

                reference = if (!isReferenceEmpty){
                    "$refInt-${prefs.getReference()}"
                }else{
                    refInt
                }

                Log.i(TAG, "[PAYMENT:START] Conectado. Iniciando SDK...")
                sendSerial(ResponseBuilder.ACK)
                _screenState.postValue(ScreenState.Idle)

                // IMPORTANTE: Aquí marcamos que NO queremos liberar el lock todavía,
                // porque ahora el trabajo es del SDK de MIT y sus callbacks.
                shouldKeepLockOpen = true

            } else {
                // CASO: SIN RED
                val attempts = networkAttempts.incrementAndGet()
                Log.w(TAG, "[PAYMENT:NO_NET] Intento $attempts sin conexión")
                sendSerial(ResponseBuilder.NETWORK_ERR)

                val texts = prefs.getTexts()
                if (attempts > 2) {
                    _screenState.postValue(ScreenState.Error(texts.msgNetworkError, "Intente más tarde"))
                } else {
                    _screenState.postValue(ScreenState.Error("Conectando", "Verificando conexión"))
                }
                // Aquí shouldKeepLockOpen se queda en false, por lo que el finally lo liberará
            }
        } catch (e: Exception) {
            Log.e(TAG, "[PAYMENT:EXCEPTION] Fallo crítico", e)
            _screenState.postValue(ScreenState.Error("Error interno", e.message ?: "Desconocido"))
        } finally {
            // 3. LA CLAVE: Si no logramos "pasar la estafeta" al SDK de MIT, liberamos el lock
            if (!shouldKeepLockOpen) {
                Log.d(TAG, "[LOCK:RELEASE] Liberando isProcessingPayment en bloque finally")
                isProcessingPayment.set(false)
            }
        }
    }

    fun getAmountForRetail(): String = amount
    fun getTimeoutForRetail(): Int = prefs.getTmout()
    fun getReferenceForRetail(): String = reference

    // ========== Refund ==========

    fun refundProcess(msg: com.google.gson.JsonObject) {
        if (!isProcessingPayment.compareAndSet(false, true)) {
            Log.w(TAG, "[REFUND:SKIP] Operación ya en proceso")
            return
        }
        communicationAttempts.set(0)
        val isConnected = CheckNetworkTask(getApplication()).execute()
        if (isConnected) {
            networkAttempts.set(0)
            Log.i(TAG, "[REFUND:START] Monto=${msg.get("Monto").asString} Folio=${msg.get("Folio").asString} Auth=${msg.get("Auth").asString}")
            sendSerial(ResponseBuilder.ACK)
            _screenState.postValue(ScreenState.Loading("Procesando devolución..."))
        } else {
            isProcessingPayment.set(false)
            val attempts = networkAttempts.incrementAndGet()
            Log.w(TAG, "[REFUND:NO_NET] Intento $attempts")
            sendSerial(ResponseBuilder.NETWORK_ERR)
            val texts = prefs.getTexts()
            if (attempts > 2) {
                _screenState.postValue(ScreenState.Error("Devolución no disponible", "Intente más tarde"))
            } else {
                _screenState.postValue(ScreenState.Error("Conectando", "Verificando conexión"))
            }
        }
    }

    fun getRefundData(msg: com.google.gson.JsonObject): Triple<String, String, String> {
        return Triple(msg.get("Monto").asString, msg.get("Folio").asString, msg.get("Auth").asString)
    }

    fun onRefundResult(transaction: MITTransaction?, error: MITError?) {
        isProcessingPayment.set(false)
        val currentDate = currentDate()
        val texts = prefs.getTexts()
        if (error == null && transaction != null) {
            Log.i(TAG, "[REFUND:OK] Auth=${transaction.auth} Folio=${transaction.folio} CC=${transaction.ccNumber} Nombre=${transaction.ccName}")
            lastResponseMessage = ResponseBuilder.buildTransactionResponse(
                msg = "0", tipoOperacion = "refund",
                numTarjeta = transaction.ccNumber.orEmpty(),
                nombre = transaction.ccName.orEmpty(),
                fechaAuth = "${transaction.date} ${transaction.time}",
                numOper = transaction.folio.orEmpty(),
                numAuth = transaction.auth.orEmpty(),
                ref = reference
            )
            sendTransactionResult(lastResponseMessage)
            _screenState.postValue(ScreenState.Success("Pago aceptado", "${texts.msgSuccessPrefix}: ${transaction.auth}"))
        } else {
            val errorMsg = error?.description ?: "No se pudo procesar la devolución"
            Log.e(TAG, "[REFUND:ERROR] $errorMsg")
            lastResponseMessage = ResponseBuilder.buildTransactionResponse(
                msg = "-1", descripcionError = errorMsg, tipoOperacion = "refund",
                fechaAuth = currentDate, ref = reference
            )
            sendTransactionResult(lastResponseMessage)
            _screenState.postValue(ScreenState.Error("Devolución rechazada", errorMsg))
        }
    }

    // ========== Control ==========

    fun handleControl(jsonObject: com.google.gson.JsonObject) {
        val msgType = jsonObject.get("Msg").asString
        Log.d(TAG, "[CONTROL] Msg=$msgType")
        if (msgType == "Cancel") {
            sendSerial(ResponseBuilder.ACK)
        } else {
            communicationAttempts.set(0)
            handleNetworkTask()
        }
    }

    fun isCancelControl(jsonObject: com.google.gson.JsonObject): Boolean {
        return jsonObject.get("Msg").asString == "Cancel"
    }

    // ========== Screen messages ==========

    fun handleScreen(type: String, message: String) {
        Log.d(TAG, "[SCREEN] type=$type message=$message")
        communicationAttempts.set(0)
        responseAttempts.set(0)
        lastResponseMessage = ""
        sendSerial(ResponseBuilder.ACK_ONLY)
        val title = when (type) {
            "advertencia" -> "Aviso"
            "tolerancia", "tolerencia" -> "Tolerancia"
            "error" -> "Atención"
            else -> "Aviso"
        }
        _screenState.postValue(ScreenState.Warning(type, title, message))
    }

    // ========== Unknown ==========

    fun handleUnknownMessage() {
        sendSerial(ResponseBuilder.NACK)
        val attempts = communicationAttempts.incrementAndGet()
        Log.w(TAG, "[UNKNOWN] NACK enviado, intento $attempts/3")
        if (attempts == 3) {
            Log.e(TAG, "[UNKNOWN] Terminal fuera de servicio")
            val texts = prefs.getTexts()
            _screenState.postValue(ScreenState.Error(texts.msgServiceError, persistent = true))
            sendSerial("{\"Msg\": \"Fuera de servicio\"}")
        }
    }

    // ========== ACK / NACK ==========

    fun handleAck() {
        Log.d(TAG, "[ACK] Recibido")
        communicationAttempts.set(0)
        responseAttempts.set(0)
        val current = _screenState.value
        if (current is ScreenState.Error && current.persistent) {
            Log.i(TAG, "[ACK] Cerrando pantalla persistent")
            _screenState.postValue(ScreenState.Idle)
        }
    }

    fun handleNack() {
        if (lastResponseMessage.isEmpty()) {
            Log.d(TAG, "[NACK] Ignorado (no hay mensaje pendiente de reenvío)")
            return
        }
        val attempts = responseAttempts.incrementAndGet()
        Log.w(TAG, "[NACK] Reintento $attempts/3")
        if (attempts <= 3) {
            sendSerial(lastResponseMessage)
        } else {
            responseAttempts.set(0)
            Log.e(TAG, "[NACK] Máximo de reintentos alcanzado, abandona envío")
        }
    }

    // ========== Network ==========

    fun handleNetworkTask() {
        try {
            val isConnected = CheckNetworkTask(getApplication()).execute()
            if (isConnected) {
                Log.d(TAG, "[NETWORK] Conectado")
                communicationAttempts.set(0)
                _screenState.postValue(ScreenState.Idle)
                sendSerial(ResponseBuilder.ACK)
            } else {
                val attempts = communicationAttempts.incrementAndGet()
                Log.w(TAG, "[NETWORK] Sin conexión, intento $attempts")
                sendSerial(ResponseBuilder.NETWORK_ERR)
                if (attempts == 1) {
                    val texts = prefs.getTexts()
                    _screenState.postValue(ScreenState.Error(texts.msgNetworkError, "Intente más tarde"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[NETWORK] Excepción", e)
        }
    }

    // ========== MIT Reading Callbacks ==========

    fun onReadingResult(result: String) {
        isProcessingPayment.set(false)
        val currentDate = currentDate()
        val texts = prefs.getTexts()
        val msg = when (result) {
            "Favor de verificar su conexión a Internet"                                  -> "Sin conexión a internet"
            "Time Out"                                                                   -> texts.msgTimeout
            "Card Removed"                                                               -> texts.msgCardRemoved
            "Error en el servicio, favor de intentar nuevamente."                         -> "Error en el servicio, intente de nuevo"
            "Sin conexión, favor de validar el estado de esta transacción en tu reporte" -> "Error en el servicio, intente de nuevo"
            else -> "Ocurrió un error, intente de nuevo"
        }
        Log.w(TAG, "[READING:ERROR] SDK=$result Mapped=$msg")
        lastResponseMessage = ResponseBuilder.buildTransactionResponse(
            msg = "-1", descripcionError = msg, fechaAuth = currentDate, ref = reference
        )
        sendTransactionResult(lastResponseMessage)
        _screenState.postValue(ScreenState.Error("Error en la operación", msg))
    }

    fun onCancelReadingCard() {
        isProcessingPayment.set(false)
        val currentDate = currentDate()
        val texts = prefs.getTexts()
        Log.i(TAG, "[READING:CANCEL] Operación cancelada")
        lastResponseMessage = ResponseBuilder.buildTransactionResponse(
            msg = "-1", descripcionError = texts.msgCancel, fechaAuth = currentDate, ref = reference
        )
        sendTransactionResult(lastResponseMessage)
        _screenState.postValue(ScreenState.Cancel("Operación cancelada", texts.msgCancel))
    }

    fun onConfirmTransaction(bankCard: MITCard) {
        typeReading = bankCard.reading.toString()
        val texts = prefs.getTexts()
        Log.i(TAG, "[READING:CONFIRM] Tipo lectura=$typeReading")
        _screenState.postValue(ScreenState.Loading(texts.msgLoadingChip))
    }

    fun onDisplayTransactionResult(transaction: MITTransaction) {
        isProcessingPayment.set(false)
        try {
            val texts = prefs.getTexts()
            if (transaction.errorCode.toString() == "null") {
                val ccType = transaction.ccType.orEmpty()
                Log.i(TAG, "[TXN:APPROVED] Auth=${transaction.auth} Folio=${transaction.folio} CC=${transaction.ccNumber} Nombre=${transaction.ccName} Tipo=$ccType Fecha=${transaction.date} ${transaction.time}")
                lastResponseMessage = ResponseBuilder.buildTransactionResponse(
                    msg = "0",
                    numTarjeta = transaction.ccNumber.orEmpty(),
                    nombre = transaction.ccName.orEmpty(),
                    fechaAuth = "${transaction.date} ${transaction.time}",
                    numOper = transaction.folio.orEmpty(),
                    numAuth = transaction.auth.orEmpty(),
                    ref = refInt,
                    tipoTarjeta = ccType.substringBeforeLast("/"),
                    marcaTarjeta = ccType.substringAfterLast("/")
                )
                sendTransactionResult(lastResponseMessage)
                _screenState.postValue(ScreenState.Success("Pago aceptado", "${texts.msgSuccessPrefix}: ${transaction.auth}"))
            } else {
                val errorDesc = transaction.errorDescription.toString()
                Log.e(TAG, "[TXN:REJECTED] Code=${transaction.errorCode} Desc=$errorDesc CC=${transaction.ccNumber} Folio=${transaction.folio} Auth=${transaction.auth}")
                lastResponseMessage = ResponseBuilder.buildTransactionResponse(
                    msg = transaction.errorCode.toString(),
                    descripcionError = errorDesc,
                    numTarjeta = transaction.ccNumber.orEmpty(),
                    nombre = transaction.ccName.orEmpty(),
                    fechaAuth = "${transaction.date} ${transaction.time}",
                    numOper = transaction.folio.orEmpty(),
                    numAuth = transaction.auth.orEmpty(),
                    ref = reference
                )
                sendTransactionResult(lastResponseMessage)
                _screenState.postValue(ScreenState.Error("Pago rechazado", errorDesc))
            }
        } catch (e: Exception) {
            Log.e(TAG, "[TXN:EXCEPTION]", e)
        }
    }
}
