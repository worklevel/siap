package wgg.siap.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mx.com.mit.mobile.mitmobilelibrary.model.MITCard
import mx.com.mit.mobile.mitmobilelibrary.model.MITError
import mx.com.mit.mobile.mitmobilelibrary.model.MITReport
import mx.com.mit.mobile.mitmobilelibrary.model.MITTransaction
import wgg.siap.InitApplication.Companion.prefs
import wgg.siap.mit.TransactionMIT
import wgg.siap.serial.ResponseBuilder
import wgg.siap.system.CheckNetworkTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _screenState = MutableLiveData<ScreenState>(ScreenState.Idle)
    val screenState: LiveData<ScreenState> = _screenState
    private val _serialOutput = MutableLiveData<String>()
    val serialOutput: LiveData<String> = _serialOutput
    @Volatile
    var lastResponseMessage: String = ""
        private set
    var typeReading: String = ""
        private set
    @Volatile
    var reference: String = ""
        private set
    private var amount: String = ""
    private var refInt: String = ""
    private val myTag = "SIAP_APK"
    private var timeoutJob: Job? = null
    private var paymentAcceptedJob: Job? = null
    private var errorAckDelayJob: Job? = null
    @Volatile
    private var errorAckTimeFinished = false
    @Volatile
    private var refundCompleted = false
    @Volatile
    private var refundTransaction: MITTransaction? = null
    @Volatile
    private var refundError: MITError? = null
    private val refundStarted = AtomicBoolean(false)
    val isProcessingPayment = AtomicBoolean(false)
    val networkAttempts = AtomicInteger(0)
    val communicationAttempts = AtomicInteger(0)
    val responseAttempts = AtomicInteger(0)
    private val esperandoAck = AtomicBoolean(false)
    private val resultadoProcesado = AtomicBoolean(false)
    private val maxRetries = 5
    private var retryCount = 0
    private var card = ""
    private var name = ""
    private var FechaAuth = ""
    private var NumOper = ""
    private var NumAuth = ""
    private var Ref = ""
    private var TipoTarjeta = ""
    private var MarcaTarjeta = ""
    private val _automaticRefund = MutableLiveData<Boolean>()
    val automaticRefund: LiveData<Boolean> = _automaticRefund
    private val _lookupTransaction = MutableLiveData<String?>()
    val lookupTransaction: LiveData<String?> = _lookupTransaction
    companion object {
        private const val TAG = "SIAP"
        private const val REFERENCE_PREFS = "transaction_reference"
        private const val KEY_BASE_REFERENCE = "base_reference"
        private const val KEY_REFERENCE_COUNTER = "reference_counter"

        fun currentDate(): String =
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale("es", "MX")).format(Date())
    }

    // =========================================================
    // SERIAL
    // =========================================================

    private fun sendSerial(msg: String) {
        Log.e(myTag, "Escribiendo en el puerto serial: $msg")
        Log.i(TAG, "[SERIAL:OUT] $msg")
        _serialOutput.postValue(msg)
    }

    // =========================================================
    // SEND TRANSACTION RESULT
    // =========================================================

    private fun sendTransactionResult(msg: String) {
        Log.e(myTag, "Resultado de la Transacción: $msg")

        timeoutJob?.cancel()
        timeoutJob = null
        esperandoAck.set(false)
        lastResponseMessage = msg
        retryCount = 0
        responseAttempts.set(0)
        esperandoAck.set(true)
        Log.i(TAG, "[TXN:RESULT] Esperando ACK final")

        Log.e(TAG, "[ACK] Enviando resultado " + "intento $retryCount/$maxRetries")

        sendSerial(msg)

        timeoutJob =
            viewModelScope.launch {
                startRetryLoop()
            }
    }

    // =========================================================
    // RETRY LOOP
    // =========================================================

    private suspend fun startRetryLoop() {

        while (
            retryCount < maxRetries &&
            esperandoAck.get()
        ) {
            delay(2000)

            if (!esperandoAck.get()) {
                Log.e(TAG, "[ACK] ACK recibido durante espera. " + "Deteniendo retry loop.")
                return
            }

            retryCount++

            Log.e(TAG, "[ACK] Reenviando resultado " + "intento $retryCount/$maxRetries")

            sendSerial(lastResponseMessage)
        }

        if (esperandoAck.get() && retryCount >= maxRetries) {
            Log.e(TAG, "[ACK_TIMEOUT] No se recibió ACK después de " + "$maxRetries intentos.")

            esperandoAck.set(false)
            handleAckTimeout()
        }
    }

    // =========================================================
    // ACK TIMEOUT
    // =========================================================

    private fun handleAckTimeout() {

        Log.e(TAG, "[ACK_TIMEOUT] Se agotaron los 5 intentos.")

        /*
         * Ya no esperamos ACK.
         */
        esperandoAck.set(false)
        timeoutJob?.cancel()
        timeoutJob = null
        isProcessingPayment.set(false)

        /*
         * Evitar que se dispare dos veces.
         */
        if (!refundStarted.compareAndSet(false, true)) {
            Log.w(TAG, "[AUTO_REFUND] La devolución ya fue iniciada.")
            return
        }

        /*
         * =====================================================
         * VALIDAR DATOS PARA REFUND
         * =====================================================
         */

        Log.e(
            TAG,
            "[AUTO_REFUND] Datos disponibles: " + "Monto=$amount " + "NumOper=$NumOper " + "NumAuth=$NumAuth"
        )

        if (amount.isBlank() || NumOper.isBlank() || NumAuth.isBlank()) {
            Log.e(
                TAG,
                "[AUTO_REFUND:ERROR] " + "No existen datos suficientes para realizar " + "la devolución automática."
            )
            refundStarted.set(false)

            _screenState.postValue(
                ScreenState.ErrorAck(
                    "Registro de pago fallido",
                    "No fue posible realizar la devolución automáticamente"
                )
            )
            return
        }

        /*
         * =====================================================
         * MOSTRAR REGISTRO FALLIDO
         * =====================================================
         */

        Log.e(TAG, "[UI] >>> REGISTRO DE PAGO FALLIDO <<<")

        _screenState.postValue(
            ScreenState.ErrorAck(
                "Registro de pago fallido",
                "Realizando la devolución de tu dinero"
            )
        )

        /*
         * =====================================================
         * ESPERAR 10 SEGUNDOS
         * =====================================================
         */

        errorAckTimeFinished = false

        errorAckDelayJob?.cancel()

        errorAckDelayJob =
            viewModelScope.launch {
                Log.e(
                    TAG,
                    "[AUTO_REFUND] Esperando 10 segundos " + "antes de realizar devolución..."
                )

                delay(10_000)
                errorAckTimeFinished = true
                Log.e(TAG, "[AUTO_REFUND] 10 segundos terminados.")

                /*
                 * =================================================
                 * EJECUTAR DEVOLUCIÓN
                 * =================================================
                 */

                Log.e(TAG, "[AUTO_REFUND] Solicitando devolución automática.")

                _automaticRefund.postValue(true)
            }
    }

    // =========================================================
    // ACK RECEIVED
    // =========================================================

    fun onAckReceived() {
        Log.e(TAG, "[ACK] onAckReceived()")


        if (esperandoAck.get()) {
            Log.e(TAG, "[ACK] ACK recibido mediante onAckReceived().")

            esperandoAck.set(false)
            timeoutJob?.cancel()
            timeoutJob = null
            paymentAcceptedJob?.cancel()
            paymentAcceptedJob = null
            isProcessingPayment.set(false)

            Log.e(TAG, "[UI] >>> PAGO REGISTRADO <<<")

            val current = _screenState.value
            if (
                current !is ScreenState.Success &&
                current !is ScreenState.Error &&
                current !is ScreenState.Cancel &&
                current !is ScreenState.Warning &&
                current !is ScreenState.SuccessAck &&
                current !is ScreenState.SuccessRefound &&
                current !is ScreenState.ErrorAck
            ) {
                when (retryCount) {
                    0, 1 -> ShowRegister(3000L)
                    2 -> ShowRegister(1000L)
                    3, 4, 5 -> ShowRegister(0)
                }
            } else {
                Log.d(TAG, "[ACK] UI ya está en un estado final (${current.javaClass.simpleName}), no sobreescribiendo con SuccessAck")
            }
        }
    }

    fun ShowRegister(ms: Long) {
        viewModelScope.launch {
            delay(ms)
            viewModelScope.launch(Dispatchers.Main) {
                _screenState.postValue(
                    ScreenState.SuccessAck(
                        "Pago registrado",
                        "Tolerancia para salir 15 minutos"
                    )
                )
            }
        }
    }

    // =========================================================
    // PAYMENT
    // =========================================================

    fun paymentProcess(msg: com.google.gson.JsonObject): Boolean {

        Log.e(myTag, "Mensaje de Pago Recibido")

        /*
         * =====================================================
         * ADQUIRIR LOCK
         * =====================================================
         */

        if (!isProcessingPayment.compareAndSet(false, true)) {
            Log.w(
                TAG,
                "[PAYMENT:SKIP] Ya existe un pago en proceso. " + "RefInt=${msg.get("RefInt").asString}"
            )
            return false
        }

        try {
            communicationAttempts.set(0)

            /*
             * =================================================
             * VALIDAR RED
             * =================================================
             */

            val isConnected = CheckNetworkTask(getApplication()).execute()

            if (!isConnected) {
                Log.e(myTag, "[PAYMENT:NO_NET] Sin conexión.")
                isProcessingPayment.set(false)

                val attempts = networkAttempts.incrementAndGet()

                sendSerial(ResponseBuilder.NETWORK_ERR)

                if (attempts > 2) {

                    val texts = prefs.getTexts()
                    _screenState.postValue(
                        ScreenState.Error(
                            texts.msgNetworkError,
                            "Intente más tarde"
                        )
                    )

                } else {
                    _screenState.postValue(
                        ScreenState.Error(
                            "Conectando",
                            "Verificando conexión"
                        )
                    )
                }
                return false
            }

            /*
             * =================================================
             * PREPARAR TRANSACCIÓN
             * =================================================
             */

            resultadoProcesado.set(false)
            networkAttempts.set(0)
            amount = msg.get("Monto").asString
            refInt = msg.get("RefInt").asString

            val isReferenceEmpty = prefs.getReference().isEmpty()

            reference = if (!isReferenceEmpty) {
                "$refInt-${prefs.getReference()}"
            } else {
                refInt
            }

            /*
             * Limpiar estado anterior.
             */
            esperandoAck.set(false)
            responseAttempts.set(0)
            retryCount = 0
            lastResponseMessage = ""
            refundStarted.set(false)
            refundCompleted = false
            refundTransaction = null
            refundError = null
            errorAckTimeFinished = false

            /*
             * Cancelar Jobs anteriores.
             */
            timeoutJob?.cancel()
            timeoutJob = null
            paymentAcceptedJob?.cancel()
            paymentAcceptedJob = null
            errorAckDelayJob?.cancel()
            errorAckDelayJob = null

            /*
             * =================================================
             * ACK INICIAL
             * =================================================
             */

            Log.e(myTag, "[PAYMENT:START] Conectado. Iniciando SDK...")

            sendSerial(ResponseBuilder.ACK)

            /*
             * =================================================
             * TIMEOUT DEL SDK
             * =================================================
             */

            startPaymentTimeout(prefs.getTmout() + 2)

            Log.e(myTag, "[PAYMENT:READY] Pago activo. MIT puede iniciar.")

            return true

        } catch (e: Exception) {

            Log.e(
                myTag,
                "[PAYMENT:EXCEPTION] Fallo crítico",
                e
            )

            isProcessingPayment.set(false)

            esperandoAck.set(false)

            timeoutJob?.cancel()
            timeoutJob = null

            _screenState.postValue(
                ScreenState.Error(
                    "Error interno",
                    e.message ?: "Desconocido"
                )
            )

            return false
        }
    }

    // =========================================================
    // PAYMENT DATA
    // =========================================================

    fun getAmountForRetail(): String = amount
    fun getTimeoutForRetail(): Int = prefs.getTmout()
    fun getReferenceForRetail(): String = reference

    // =========================================================
    // REFUND
    // =========================================================

    fun refundProcess() {
        Log.e(myTag, "Iniciando proceso de reembolso")

        if (!isProcessingPayment.compareAndSet(false, true)) {
            Log.w(myTag, "[REFUND:SKIP] Operación ya en proceso")
            return
        }

        communicationAttempts.set(0)

        val isConnected = CheckNetworkTask(getApplication()).execute()

        if (isConnected) {
            Log.e(myTag, "Estoy conectado, iniciando reembolso")
            _automaticRefund.postValue(true)
            networkAttempts.set(0)
            Log.e(myTag, "Reembolso solicitado")
            esperandoAck.set(false)
            timeoutJob?.cancel()
            timeoutJob = null
        } else {
            Log.e(myTag, "Proceso de Reembolso no Inicializado, sin conexión.")
            isProcessingPayment.set(false)
            val attempts = networkAttempts.incrementAndGet()

            Log.w(myTag, "[REFUND:NO_NET] Intento $attempts")

            lastResponseMessage =
                ResponseBuilder.buildTransactionResponse(
                    msg = "-1",
                    tipoOperacion = "Transacción Cancelada",
                    numTarjeta = "",
                    fechaAuth = "",
                    numOper = "",
                    numAuth = "",
                    ref = reference,
                    tipoTarjeta = "",
                    marcaTarjeta = ""
                )

            sendTransactionResultWithoutAck(lastResponseMessage)

            if (attempts > 2) {
                _screenState.postValue(
                    ScreenState.Error(
                        "Devolución no disponible",
                        "Intente más tarde"
                    )
                )
            } else {
                _screenState.postValue(
                    ScreenState.Error(
                        "Conectando",
                        "Verificando conexión"
                    )
                )
            }
        }
    }

    // =========================================================
    // REFUND DATA
    // =========================================================

    fun getAutomaticRefundData(): Triple<String, String, String> {

        return Triple(
            amount,
            NumOper,
            NumAuth
        )
    }

    fun getRefundData(
        msg: com.google.gson.JsonObject
    ): Triple<String, String, String> {

        return Triple(
            msg.get("Monto").asString,
            msg.get("Folio").asString,
            msg.get("Auth").asString
        )
    }

    // =========================================================
    // REFUND RESULT
    // =========================================================

    fun onRefundResult(
        transaction: MITTransaction?,
        error: MITError?
    ) {
        Log.e(TAG, "[REFUND] Resultado recibido")

        refundTransaction = transaction
        refundError = error

        if (error == null && transaction != null) {
            Log.e(TAG, "[REFUND:OK] Devolución realizada")
            refundCompleted = true
            lastResponseMessage =
                ResponseBuilder.buildTransactionResponse(
                    msg = "-2",
                    descripcionError = "Transaccion Cancelada",
                    numTarjeta = transaction.ccNumber.orEmpty(),
                    nombre = transaction.ccName.orEmpty(),
                    fechaAuth = "${transaction.date} ${transaction.time}",
                    numOper = transaction.folio.orEmpty(),
                    numAuth = transaction.auth.orEmpty(),
                    ref = reference,
                    tipoTarjeta = transaction.ccType?.substringBeforeLast("/").orEmpty(),
                    marcaTarjeta = transaction.ccType?.substringAfterLast("/").orEmpty()
                )

            sendSerial(lastResponseMessage)

            isProcessingPayment.set(false)
            esperandoAck.set(false)
            timeoutJob?.cancel()
            timeoutJob = null

            _screenState.postValue(
                ScreenState.SuccessRefound(
                    "Devolución realizada",
                    "Tu dinero fue devuelto correctamente"
                )
            )
            return
        }

        val errorMsg = error?.description ?: "No se pudo procesar la devolución"

        Log.e(TAG, "[REFUND:ERROR] $errorMsg")

        refundCompleted = false
        lastResponseMessage =
            ResponseBuilder.buildTransactionResponse(
                msg = "-1",
                descripcionError = errorMsg,
                tipoOperacion = "refund",
                fechaAuth = currentDate(),
                ref = reference
            )

        sendTransactionResultWithoutAck(lastResponseMessage)
        isProcessingPayment.set(true)

        _screenState.postValue(
            ScreenState.Error(
                "Devolución rechazada",
                errorMsg
            )
        )
    }

    // =========================================================
    // CONTROL
    // =========================================================

    fun handleControl(
        jsonObject: com.google.gson.JsonObject
    ) {
        val msgType = jsonObject.get("Msg").asString

        Log.d(myTag, "[CONTROL] Msg=$msgType")

        if (msgType == "Cancel") {
            lastResponseMessage =
                ResponseBuilder.buildTransactionResponse(
                    msg = "-1",
                    descripcionError = msgType,
                    tipoOperacion = "",
                    numTarjeta = "",
                    fechaAuth = "",
                    numOper = "",
                    numAuth = "",
                    ref =reference,
                    tipoTarjeta = "",
                    marcaTarjeta = ""
                )

            sendTransactionResultWithoutAck(lastResponseMessage)
            Log.e(myTag, "Se recibió un cancel")
        } else {
            communicationAttempts.set(0)
            handleNetworkTask()
        }
    }

    fun isCancelControl(jsonObject: com.google.gson.JsonObject): Boolean =
        jsonObject.get("Msg").asString == "Cancel"


    // =========================================================
    // SCREEN
    // =========================================================

    fun handleScreen(type: String, message: String) {
        Log.e(myTag, "Manejando muestras de pantalla")
        communicationAttempts.set(0)
        responseAttempts.set(0)
        lastResponseMessage = ""
        sendSerial(ResponseBuilder.ACK_ONLY)

        val title =
            when (type) {
                "advertencia" -> "Aviso"
                "tolerancia",
                "tolerencia" -> "Tolerancia"

                "error" -> "Atención"
                else -> "Aviso"
            }
        _screenState.postValue(
            ScreenState.Warning(
                type,
                title,
                message
            )
        )
    }

    // =========================================================
    // UNKNOWN
    // =========================================================

    fun handleUnknownMessage() {
        Log.e(myTag, "Se recibió un mensaje desconocido")
        sendSerial(ResponseBuilder.NACK)
        val attempts = communicationAttempts.incrementAndGet()

        Log.w(TAG, "[UNKNOWN] NACK enviado, intento $attempts/3")

        if (attempts == 3) {
            Log.e(TAG, "[UNKNOWN] Terminal fuera de servicio")

            val texts = prefs.getTexts()

            _screenState.postValue(
                ScreenState.Error(
                    texts.msgServiceError,
                    persistent = false
                )
            )

            sendSerial("{\"Msg\": \"Fuera de servicio\"}")
        }
    }

    // =========================================================
    // NACK
    // =========================================================

    fun handleNack() {

        if (lastResponseMessage.isEmpty()) {
            Log.d(TAG, "[NACK] Ignorado. " + "No hay mensaje pendiente.")
            return
        }

        val attempts = responseAttempts.incrementAndGet()
        Log.w(TAG, "[NACK] Reintento $attempts/3")

        if (attempts <= 3) {
            sendSerial(lastResponseMessage)
        } else {
            responseAttempts.set(0)
            Log.e(TAG, "[NACK] Máximo de reintentos alcanzado")
        }
    }

    // =========================================================
    // NETWORK
    // =========================================================

    fun handleNetworkTask() {
        Log.e(myTag, "Revisando el estado de la red")

        try {
            val isConnected = CheckNetworkTask(getApplication()).execute()

            if (isConnected) {
                Log.e(myTag, "La red está conectada, procediendo")
                communicationAttempts.set(0)
                val current = _screenState.value

                if (
                    current !is ScreenState.Success &&
                    current !is ScreenState.Error &&
                    current !is ScreenState.Cancel &&
                    current !is ScreenState.Warning &&
                    current !is ScreenState.SuccessAck &&
                    current !is ScreenState.SuccessRefound &&
                    current !is ScreenState.ErrorAck
                ) {
                    _screenState.postValue(ScreenState.Idle)
                }
                Log.e(myTag, "handleNetworkTask")
                sendSerial(ResponseBuilder.ACK)
            } else {
                val attempts = communicationAttempts.incrementAndGet()

                Log.w(TAG, "[NETWORK] Sin conexión, intento $attempts")
                sendSerial(ResponseBuilder.NETWORK_ERR)

                if (attempts == 1) {
                    val texts = prefs.getTexts()

                    _screenState.postValue(
                        ScreenState.Error(
                            texts.msgNetworkError,
                            "Intente más tarde"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "[NETWORK] Excepción",
                e
            )
        }
    }

    // =========================================================
    // MIT READING RESULT
    // =========================================================

    fun onReadingResult(result: String) {

        Log.e(TAG, "Resultado de lectura: $result")

        // GANAR LA CARRERA: Si el timer o un resultado previo ya ganó, ignoramos este mensaje tardío.
        if (!resultadoProcesado.compareAndSet(false, true)) {
            Log.w(TAG, "[READING:RESULT] Ignorado: ya procesado anteriormente. SDK Result=$result")
            return
        }

        if (result.equals("Time Out", ignoreCase = true)) {
            Log.e(TAG, "[TIMEOUT] >>> TRANSACCIÓN CANCELADA POR TIME OUT <<<")

            clearPaymentProcess()

            lastResponseMessage =
                ResponseBuilder.buildTransactionResponse(
                    msg = "-1",
                    descripcionError = "Se cancelo la transaccion",
                    numTarjeta = "",
                    nombre = "",
                    fechaAuth = "",
                    numOper = "",
                    numAuth = "",
                    ref = reference
                )

            sendTransactionResultWithoutAck(lastResponseMessage)

            _screenState.postValue(
                ScreenState.Error(
                    "Tiempo de espera agotado",
                    "Tiempo de espera agotado"
                )
            )

            Log.e(TAG, "[TIMEOUT] >>> PAGO LIBERADO. " + "SE PUEDE PROCESAR UN NUEVO PAGO <<<")
            return
        }

        timeoutJob?.cancel()
        timeoutJob = null

        val currentDate = currentDate()
        val texts = prefs.getTexts()
        val msg = when (result) {
            "Favor de verificar su conexión a Internet" -> "Sin conexión a internet"
            "Card Removed" -> texts.msgCardRemoved
            "Error en el servicio, favor de intentar nuevamente." -> "Error en el servicio, intente de nuevo"
            "Sin conexión, favor de validar el estado de esta transacción en tu reporte" -> "Error en el servicio, intente de nuevo"
            else -> "Ocurrió un error, intente de nuevo"
        }

        Log.w(TAG, "[READING:ERROR] SDK=$result Mapped=$msg")

        clearPaymentProcess()

        val transactionResult = ResponseBuilder.buildTransactionResponse(
            msg = "-1",
            descripcionError = msg,
            fechaAuth = currentDate,
            ref = reference
        )

        sendTransactionResultWithoutAck(transactionResult)

        _screenState.postValue(
            ScreenState.Error(
                "Error en la operación",
                msg
            )
        )
    }

    fun onCancelReadingCard() {

        // GANAR LA CARRERA
        if (!resultadoProcesado.compareAndSet(false, true)) {
            Log.w(TAG, "[READING:CANCEL] Ignorado: ya procesado anteriormente.")
            return
        }

        Log.e(myTag, "Se canceló la lectura.")
        timeoutJob?.cancel()
        timeoutJob = null
        esperandoAck.set(false)
        isProcessingPayment.set(false)

        val currentDate = currentDate()
        val texts = prefs.getTexts()

        lastResponseMessage =
            ResponseBuilder.buildTransactionResponse(
                msg = "-1",
                descripcionError = texts.msgCancel,
                fechaAuth = currentDate,
                ref = reference
            )

        sendTransactionResultWithoutAck(lastResponseMessage)

        timeoutJob?.cancel()
        timeoutJob = null
        esperandoAck.set(false)
        isProcessingPayment.set(false)

        _screenState.postValue(
            ScreenState.Cancel(
                "Operación cancelada",
                texts.msgCancel
            )
        )
    }

    fun onConfirmTransaction(bankCard: MITCard) {

        Log.e(myTag, "Tarjeta validada")

        timeoutJob?.cancel()
        timeoutJob = null

        typeReading =
            bankCard.reading.toString()

        val texts = prefs.getTexts()

        Log.i(TAG, "[READING:CONFIRM] Tipo lectura=$typeReading. Iniciando timer de 6s para autorización.")

        _screenState.postValue(
            ScreenState.Loading(
                texts.msgLoadingChip
            )
        )

        // Iniciamos el contador de 6 segundos para la fase de autorización (submitPayment)
        startPaymentTimeout(6)
    }

    // =========================================================
    // TRANSACTION RESULT
    // =========================================================

    fun onDisplayTransactionResult(transaction: MITTransaction) {

        Log.e(myTag, "[MIT:RESULT] Resultado de transacción recibido")
        Log.e(myTag, "[MIT:RESULT] approved=${transaction.approved}")
        Log.e(myTag, "[MIT:RESULT] errorCode=${transaction.errorCode}")
        Log.e(myTag, "[MIT:RESULT] errorDescription=${transaction.errorDescription}")
        timeoutJob?.cancel()
        timeoutJob = null

        try {
            Log.e(myTag, "[MIT:RESULT] resultadoProcesado antes=${resultadoProcesado.get()}")
            if (!resultadoProcesado.compareAndSet(false, true)) {
                Log.w(myTag, "[TXN] Resultado duplicado ignorado")
                return
            }
            val texts = prefs.getTexts()

            if (transaction.approved == true && transaction.errorCode == null) {
                val ccType = transaction.ccType.orEmpty()
                card = transaction.ccNumber.orEmpty()
                name = transaction.ccName.orEmpty()
                FechaAuth = "${transaction.date} ${transaction.time}"
                NumOper = transaction.folio.orEmpty()
                NumAuth = transaction.auth.orEmpty()
                Ref = refInt
                TipoTarjeta = ccType.substringBeforeLast("/")
                MarcaTarjeta = ccType.substringAfterLast("/")

                lastResponseMessage =
                    ResponseBuilder.buildTransactionResponse(
                        msg = "0",
                        numTarjeta = card,
                        nombre = name,
                        fechaAuth = FechaAuth,
                        numOper = NumOper,
                        numAuth = NumAuth,
                        ref = Ref,
                        tipoTarjeta = TipoTarjeta,
                        marcaTarjeta = MarcaTarjeta
                    )

                esperandoAck.set(false)
                retryCount = 0
                responseAttempts.set(0)
                isProcessingPayment.set(true)

                Log.e(TAG, "[UI] >>> PAGO ACEPTADO <<<")

                _screenState.postValue(
                    ScreenState.Success(
                        "Pago aceptado",
                        "${texts.msgSuccessPrefix}: " +
                                "${transaction.auth}"
                    )
                )

                sendTransactionResult(
                    lastResponseMessage
                )

                paymentAcceptedJob?.cancel()
                paymentAcceptedJob = viewModelScope.launch {
                    delay(3000)

                    if (!esperandoAck.get()) {
                        Log.e(TAG, "[UI] ACK ya recibido. " + "No se mostrará Registrando pago.")
                        return@launch
                    }

                    Log.e(TAG, "[UI] 3 segundos terminados. " + ">>> REGISTRANDO PAGO <<<")
                    _screenState.postValue(ScreenState.Loading("Registrando pago"))
                }

                return
            }

            val errorDesc = transaction.errorDescription.orEmpty()
            Log.e(
                myTag,
                "[MIT:ERROR] errorCode=${transaction.errorCode}, " + "errorDescription=$errorDesc"
            )
            isProcessingPayment.set(false)
            esperandoAck.set(false)
            paymentAcceptedJob = null
            timeoutJob = null
            val isDuplicateReference =
                transaction.errorCode == "11" ||
                        transaction.errorCode.equals("BAD_REQUEST", ignoreCase = true) ||
                        errorDesc.contains(
                            "La transacción ya fue aprobada / rechazada",
                            ignoreCase = true
                        ) ||
                        errorDesc.contains("400", ignoreCase = true)
            when {
                isDuplicateReference -> {
                    lastResponseMessage =
                        ResponseBuilder.buildTransactionResponse(
                            msg = "-1",
                            descripcionError = "Referencia duplicada, intente con una nueva referencia",
                            numTarjeta = transaction.ccNumber.orEmpty(),
                            nombre = transaction.ccName.orEmpty(),
                            fechaAuth = "${transaction.date} ${transaction.time}",
                            numOper = transaction.folio.orEmpty(),
                            numAuth = transaction.auth.orEmpty(),
                            ref = reference
                        )

                    sendTransactionResultWithoutAck(lastResponseMessage)

                    _screenState.postValue(
                        ScreenState.Error(
                            "Pago rechazado",
                            "Referencia duplicada, intente con una nueva referencia"
                        )
                    )
                }

                else -> {
                    lastResponseMessage =
                        ResponseBuilder.buildTransactionResponse(
                            msg = "-1",
                            descripcionError = errorDesc,
                            numTarjeta = transaction.ccNumber.orEmpty(),
                            nombre = transaction.ccName.orEmpty(),
                            fechaAuth = "${transaction.date} ${transaction.time}",
                            numOper = transaction.folio.orEmpty(),
                            numAuth = transaction.auth.orEmpty(),
                            ref = reference
                        )

                    sendTransactionResultWithoutAck(lastResponseMessage)

                    _screenState.postValue(
                        ScreenState.Error(
                            "Pago rechazado",
                            errorDesc
                        )
                    )
                }
            }

        } catch (e: Exception) {
            isProcessingPayment.set(false)
            esperandoAck.set(false)
            paymentAcceptedJob?.cancel()
            paymentAcceptedJob = null
            timeoutJob?.cancel()
            timeoutJob = null
        }
    }

    // =========================================================
    // PAYMENT SECURITY TIMEOUT
    // =========================================================

    private fun startPaymentTimeout(timeoutSeconds: Int) {

        timeoutJob?.cancel()
        Log.e(myTag, "Iniciando Cronometro de seguridad")

        timeoutJob = viewModelScope.launch {
            Log.i(TAG, "[TIMEOUT:START] " + "Cronómetro por $timeoutSeconds segundos.")

            delay(timeoutSeconds * 1000L)

            // GANAR LA CARRERA: Si ya se procesó por el SDK o el host envió ACK, ignoramos el timeout.
            if (!resultadoProcesado.compareAndSet(false, true)) {
                Log.e(TAG, "[TIMEOUT] Ignorado porque la transacción ya se completó.")
                return@launch
            }

            Log.e(TAG, "[TIMEOUT:TRIGGERED] MIT no respondió a tiempo. Verificando estado...")
            
            // En lugar de error inmediato, buscamos por referencia
            _screenState.postValue(ScreenState.Loading("Verificando estado de la transacción..."))
            _lookupTransaction.postValue(reference)
        }
    }

    // =========================================================
    // LOOKUP RESULT
    // =========================================================

    fun clearLookupTransaction() {
        _lookupTransaction.value = null
    }

    fun onLookupResult(report: MITReport?, error: MITError?) {
        Log.d(TAG, "[LOOKUP] Resultado recibido. Report=$report, Error=${error?.description}")

        if (error != null || report?.transactions.isNullOrEmpty()) {
            Log.e(TAG, "[LOOKUP:FAIL] No se encontró la transacción o hubo error.")
            
            isProcessingPayment.set(false)
            esperandoAck.set(false)
            paymentAcceptedJob?.cancel()
            paymentAcceptedJob = null
            timeoutJob?.cancel()
            timeoutJob = null

            lastResponseMessage =
                ResponseBuilder.buildTransactionResponse(
                    msg = "-1",
                    descripcionError = "Tiempo de espera agotado / No encontrada",
                    numTarjeta = "",
                    nombre = "",
                    fechaAuth = "",
                    numOper = "",
                    numAuth = "",
                    ref = reference
                )

            sendTransactionResultWithoutAck(lastResponseMessage)

            _screenState.postValue(
                ScreenState.Error(
                    "Tiempo de espera agotado",
                    "No se pudo confirmar el pago"
                )
            )
            return
        }

        val transaction = report?.transactions?.lastOrNull()
        
        if (transaction != null) {
            Log.i(TAG, "[LOOKUP:SUCCESS] Transacción encontrada! Folio=${transaction.folio}")
            processTransactionResult(transaction)
        }
    }

    private fun processTransactionResult(transaction: MITTransaction) {
        val texts = prefs.getTexts()

        if (transaction.approved == true && transaction.errorCode == null) {
            val ccType = transaction.ccType.orEmpty()
            card = transaction.ccNumber.orEmpty()
            name = transaction.ccName.orEmpty()
            FechaAuth = "${transaction.date} ${transaction.time}"
            NumOper = transaction.folio.orEmpty()
            NumAuth = transaction.auth.orEmpty()
            Ref = refInt
            TipoTarjeta = ccType.substringBeforeLast("/")
            MarcaTarjeta = ccType.substringAfterLast("/")

            lastResponseMessage =
                ResponseBuilder.buildTransactionResponse(
                    msg = "0",
                    numTarjeta = card,
                    nombre = name,
                    fechaAuth = FechaAuth,
                    numOper = NumOper,
                    numAuth = NumAuth,
                    ref = Ref,
                    tipoTarjeta = TipoTarjeta,
                    marcaTarjeta = MarcaTarjeta
                )

            esperandoAck.set(false)
            retryCount = 0
            responseAttempts.set(0)
            isProcessingPayment.set(true)

            Log.e(TAG, "[UI] >>> PAGO ACEPTADO (vía lookup) <<<")

            _screenState.postValue(
                ScreenState.Success(
                    "Pago aceptado",
                    "${texts.msgSuccessPrefix}: " +
                            "${transaction.auth}"
                )
            )

            sendTransactionResult(lastResponseMessage)
            return
        }

        // Si no fue aprobada
        val errorDesc = transaction.errorDescription.orEmpty()
        isProcessingPayment.set(false)
        esperandoAck.set(false)

        lastResponseMessage =
            ResponseBuilder.buildTransactionResponse(
            msg = "-1",
                descripcionError = errorDesc,
                numTarjeta = transaction.ccNumber.orEmpty(),
                nombre = transaction.ccName.orEmpty(),
                fechaAuth = "${transaction.date} ${transaction.time}",
                numOper = transaction.folio.orEmpty(),
                numAuth = transaction.auth.orEmpty(),
                ref = reference
            )

        sendTransactionResultWithoutAck(lastResponseMessage)

        _screenState.postValue(
            ScreenState.Error(
                "Pago rechazado",
                errorDesc
            )
        )
    }

    // =========================================================
    // AUTOMATIC REFUND CLEAR
    // =========================================================

    fun clearAutomaticRefund() {
        _automaticRefund.value = false
    }

    // =========================================================
    // VIEWMODEL CLEAR
    // =========================================================

    override fun onCleared() {

        Log.e(
            TAG,
            "[VM] ViewModel destruido. Cancelando Jobs."
        )

        timeoutJob?.cancel()
        timeoutJob = null

        paymentAcceptedJob?.cancel()
        paymentAcceptedJob = null

        errorAckDelayJob?.cancel()
        errorAckDelayJob = null

        super.onCleared()
    }

    private fun clearPaymentProcess() {

        Log.e(
            TAG,
            "[PAYMENT:CLEAR] Limpiando completamente el proceso de pago"
        )

        // Cancelar timeout de seguridad
        timeoutJob?.cancel()
        timeoutJob = null

        // Cancelar timer de "Registrando pago"
        paymentAcceptedJob?.cancel()
        paymentAcceptedJob = null

        // Cancelar espera de 10 segundos de devolución
        errorAckDelayJob?.cancel()
        errorAckDelayJob = null

        // Ya no esperamos ACK
        esperandoAck.set(false)

        // Limpiar mensaje pendiente
        lastResponseMessage = ""

        // Reiniciar intentos
        retryCount = 0
        responseAttempts.set(0)
        communicationAttempts.set(0)
        networkAttempts.set(0)

        // Permitir nuevo pago
        isProcessingPayment.set(false)

        // Reset refund
        refundStarted.set(false)
        refundCompleted = false
        refundTransaction = null
        refundError = null

        Log.e(
            TAG,
            "[PAYMENT:CLEAR] Pago completamente liberado"
        )
    }

    private fun sendTransactionResultWithoutAck(msg: String) {
        Log.e(TAG, "[TXN:NO_ACK] Enviando resultado sin esperar ACK")

        esperandoAck.set(false)
        timeoutJob?.cancel()
        timeoutJob = null
        paymentAcceptedJob?.cancel()
        paymentAcceptedJob = null
        lastResponseMessage = ""
        retryCount = 0
        responseAttempts.set(0)
        isProcessingPayment.set(false)

        sendSerial(msg)

        Log.e(TAG, "[TXN:NO_ACK] Resultado enviado. " + "Proceso liberado.")
    }
}