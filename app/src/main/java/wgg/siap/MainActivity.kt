package wgg.siap

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import mx.com.mit.mobile.mitmobilelibrary.model.*
import wgg.siap.InitApplication.Companion.prefs
import wgg.siap.databinding.ActivityMainBinding
import wgg.siap.mit.ComMIT
import wgg.siap.mit.DeviceMIT
import wgg.siap.mit.LoginMIT
import wgg.siap.mit.ReadingMIT
import wgg.siap.mit.TransactionMIT
import wgg.siap.screens.LoadingFragment
import wgg.siap.screens.StatusFragment
import wgg.siap.serial.IncomingMessage
import wgg.siap.serial.SerialManager
import com.example.firebasedatamodule.AppConfigurator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import wgg.siap.system.PasswordActivity
import wgg.siap.ui.ScreenManager
import wgg.siap.viewmodel.MainViewModel
import wgg.siap.viewmodel.ScreenState
import java.nio.charset.StandardCharsets

private const val locationAndPhonePermissions = 200

open class MainActivity : AppCompatActivity(),
    LoginMIT.LoginListener,
    DeviceMIT.DeviceListener,
    ReadingMIT.ReadingListener,
    ComMIT.ComListener,
    TransactionMIT.TransactionListener {
    private val viewModel: MainViewModel by viewModels()
    private val loginMIT       by lazy { LoginMIT(this, this) }
    private val deviceMIT      by lazy { DeviceMIT(this, this) }
    private val readingMIT     by lazy { ReadingMIT(this, this) }
    private val comManager     by lazy { ComMIT(this, this) }
    private val transactionMIT by lazy { TransactionMIT(this, this) }
    private val screenManager  by lazy { ScreenManager(supportFragmentManager, binding.fragmentContainer) }
    private lateinit var timerCloseActivity: CountDownTimer
    private val serialManager by lazy {
        SerialManager { message, _ -> handleIncomingMessage(message) }
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var appConfigurator: AppConfigurator
    private val myTag = "SIAP_APK"

    var yaRecibiResultado = false
    companion object {
        private const val TAG_LOADING = "loadingScreen"
        private const val TAG_STATUS  = "statusScreen"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e(myTag, "Creando la actividad principal")
        appConfigurator = AppConfigurator(applicationContext)

        // Segunda validación por seguridad
        if (!appConfigurator.verificarConfiguracion()) {
            Log.e(myTag, "Ejecutando el lanzador de llaves")
            redirigirAConfiguracion()
            return
        }

        //Solo si pasó, inflamos
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        observeViewModel()

        // Retraso para que el sistema termine de registrar el toast
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                Log.e(myTag, "Ejecutando el flujo inicial")
                iniciarFlujoSeguro()
            }
        }, 800)
        viewModel.automaticRefund.observe(this) { executeRefund ->

            if (!executeRefund) return@observe

            Log.e(myTag, "[AUTO_REFUND] Ejecutando devolución automática")

            val (amount, folio, auth) = viewModel.getAutomaticRefundData()
            Log.e(myTag, "[AUTO_REFUND] Monto=$amount Folio=$folio Auth=$auth")

            transactionMIT.refundTransaction(amount, folio, auth)
            viewModel.clearAutomaticRefund()
        }

        viewModel.lookupTransaction.observe(this) { ref ->
            if (!ref.isNullOrBlank()) {
                Log.i(myTag, "[LOOKUP] Solicitando verificación de referencia: $ref")
                transactionMIT.getTransactionsByReference(ref)
                viewModel.clearLookupTransaction()
            }
        }
    }

    private fun redirigirAConfiguracion() {
        Log.w("SIAP", "[CONFIG] Falló. Redirigiendo y cerrando...")
        val intent = Intent(this, Class.forName("com.example.firebasedatamodule.StartActivity"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }

    private fun iniciarFlujoSeguro() {
        screenManager.show(TAG_LOADING, LoadingFragment.newInstance("Iniciando..."))
        permissions()
        Log.e(myTag, "comManager Conectado")
        comManager.connect()
    }

    private fun observeViewModel() {
        val texts = prefs.getTexts()

        viewModel.screenState.observe(this) { state ->
            val texts = prefs.getTexts()
            when (state) {
                is ScreenState.Idle -> screenManager.dismissAll()

                is ScreenState.Loading -> {
                    Log.e(myTag, "Mostrando mensaje de carga")
                    screenManager.show(TAG_LOADING, LoadingFragment.newInstance(state.message))
                }

                is ScreenState.Success -> {
                    Log.e(myTag, "Mostrando mensaje de exito")
                    if (::timerCloseActivity.isInitialized) {
                        timerCloseActivity.cancel()
                    }

                    playSound(R.raw.approved_sound_v1)
                    screenManager.show(TAG_STATUS, StatusFragment.success(state.title, state.message))
                    screenManager.scheduleAutoDismiss(TAG_STATUS, texts.autoDismissSuccessMs)
                }

                is ScreenState.SuccessRefound -> {
                    Log.e(myTag, "Mostrando mensaje de exito refound")
                    Log.e(myTag, "[UI] SUCCESS_REFOUND recibido")
                    // Cancelar cualquier contador anterior de ErrorAck
                    if (::timerCloseActivity.isInitialized) {
                        timerCloseActivity.cancel()
                    }

                    screenManager.show(TAG_STATUS, StatusFragment.success(state.title, state.message))
                    screenManager.scheduleAutoDismiss(TAG_STATUS, texts.autoDismissSuccessMs)
                }

                is ScreenState.SuccessAck -> {
                    Log.e(myTag, "Mostrando mensaje de exito ACK recivido")
                    screenManager.show(TAG_STATUS, StatusFragment.success(state.title, state.message))
                    screenManager.scheduleAutoDismiss(TAG_STATUS, (texts.autoDismissSuccessMs*2))
                }

                is ScreenState.Error -> {
                    Log.e(myTag, "Mostrando mensaje de error")
                    screenManager.show(TAG_STATUS, StatusFragment.error(state.title, state.subtitle))
                    if (!state.persistent) {
                        screenManager.scheduleAutoDismiss(TAG_STATUS, (texts.autoDismissErrorMs*1.6).toLong())
                    }
                }

                is ScreenState.ErrorAck -> {
                    Log.e(
                        myTag,
                        "[UI] ERROR_ACK recibido title=${state.title} subtitle=${state.subtitle}"
                    )
                    Log.e(myTag, "Mostrando mensaje de error no ACK")
                    screenManager.show(TAG_STATUS, StatusFragment.error(state.title, state.subtitle))
                    if (!state.persistent) {
                        timerCloseActivity = object: CountDownTimer(10000, 1000) {
                            override fun onTick(millisUntilFinished: Long) {
                                Log.e(myTag, "[UI] ErrorAck timer: ${millisUntilFinished / 1000}")
                                val fragment = supportFragmentManager.findFragmentByTag(TAG_STATUS)
                                val view = fragment?.view
                                val count = view?.findViewById<TextView>(R.id.counterNoAck)
                                count?.text = prefs.getTimeErrorNoAck()
                                count?.visibility = View.VISIBLE
                                count?.text = (millisUntilFinished/1000).toString()
                            }
                            override fun onFinish() {
                                Log.e(
                                    myTag,
                                    "[UI] ErrorAck timer TERMINADO"
                                )
                                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
                                screenManager.scheduleAutoDismiss(TAG_STATUS, 0)
                            }
                        }
                        timerCloseActivity.start()
                    }
                }

                is ScreenState.Cancel -> {
                    playSound(R.raw.deny_sound_v2)
                    Log.e(myTag, "Mostrando mensaje de cancelación")
                    screenManager.show(TAG_STATUS, StatusFragment.cancel(state.title, state.message))
                    screenManager.scheduleAutoDismiss(TAG_STATUS, texts.autoDismissErrorMs)
                }

                is ScreenState.Warning -> {
                    Log.e(myTag, "Mostrando mensaje de advertencia")
                    val fragment = when (state.type) {
                        "tolerancia", "tolerencia" -> StatusFragment.tolerance(state.title, state.message)
                        "error", "Atención" -> StatusFragment.error(state.title, state.message)
                        else -> StatusFragment.warning(state.title, state.message)
                    }

                    screenManager.show(TAG_STATUS, fragment)
                    screenManager.scheduleAutoDismiss(TAG_STATUS, texts.autoDismissErrorMs)
                }
            }
        }

        viewModel.serialOutput.observe(this) { msg ->
            writeSerial(msg)
        }

        viewModel.lookupTransaction.observe(this) { reference ->
            if (reference != null) {
                Log.d(TAG_STATUS, "[MAIN] Solicitando lookup para referencia: $reference")
                transactionMIT.getTransactionsByReference(reference)
                viewModel.clearLookupTransaction()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun init() {
        val versionName = packageManager
            .getPackageInfo(packageName, 0)
            .versionName

        binding.txtVersion.text = "V.$versionName"
        Log.e(myTag, "Inicializando Interfaz")
        loadLogo()
        binding.firstStep.setOnClickListener { binding.secondStep.isEnabled = true }
        binding.secondStep.setOnClickListener { binding.settings.isEnabled = true }
        binding.settings.setOnClickListener {
            binding.settings.isEnabled = false
            binding.secondStep.isEnabled = false
            Log.e(myTag, "Desconectando comManager")
            comManager.disconnect()
            startActivity(
                Intent(this, PasswordActivity::class.java)
            )
        }
    }

    override fun onStart() {
        super.onStart()
        Log.e(myTag, "Limpiando Buffer")
        serialManager.clearBuffer()
    }

    override fun onRestart() {
        super.onRestart()

        Log.e(myTag, "onRestart")

        if (!viewModel.isProcessingPayment.get()) {

            Log.e(myTag, "Sin pago activo, reconectando")

            comManager.connect()

            screenManager.show(
                TAG_LOADING,
                LoadingFragment.newInstance("Iniciando...")
            )

            loginMIT.setCredentials()

        } else {

            Log.e(myTag, "Pago activo, NO reiniciar MIT")
        }
    }

    override fun onResume() {
        Log.e(myTag, "Resumiendo, inflando interfaz")
        super.onResume()
        Log.e(myTag, "El valor de isPayment = ${viewModel.isProcessingPayment}")
        binding.payments.setImageResource(if (prefs.getAmex()) R.drawable.marka else R.drawable.marks)
        binding.sponsor.setImageResource(if (prefs.getGadapi()) R.drawable.sponsorg else R.drawable.sponsor)
        binding.txtTitleHome.text = prefs.getHomeTitle()
        binding.txtSubtitleHome.text = prefs.getHomeSubtitle()
    }

    override fun onDestroy() {
        super.onDestroy()
        if(isFinishing){
            Log.d("APPLOG", "Estoy en el onDestroy")
        }else{
            Log.d("APPLOG", "Estoy en el onDestroy, desconectando el comManager")
            Log.e(myTag, "Desconectando el comManager")
            comManager.disconnect()
        }
    }

    /**************************************************** [ Serial ] **/
    private fun handleIncomingMessage(message: IncomingMessage) {
        Log.e(myTag, "[SERIAL_IN]: $message")

        when (message) {

            // =====================================================
            // PAYMENT
            // =====================================================
            is IncomingMessage.Payment -> {
                Log.e(myTag, "[PAYMENT] Payment recibido")
                val paymentStarted = viewModel.paymentProcess(message.json)

                if (paymentStarted) {

                    Log.e(myTag, "[PAYMENT] Payment aceptado por ViewModel. Iniciando MIT...")

                    readingMIT.doRetail(
                        amount = viewModel.getAmountForRetail(),
                        reference = viewModel.getReferenceForRetail(),
                        timeoutSeconds = viewModel.getTimeoutForRetail()
                    )
                } else {
                    Log.w(
                        myTag,
                        "[PAYMENT] No se inició MIT porque el pago no pudo prepararse."
                    )
                }
            }

            // =====================================================
            // REFUND
            // =====================================================
            is IncomingMessage.Refund -> {

                Log.d(
                    myTag,
                    "Comando Refund recibido. Ejecutando en segundo plano..."
                )

                val json = message.json

                viewModel.refundProcess()

                if (viewModel.isProcessingPayment.get()) {

                    val (amount, folio, auth) =
                        viewModel.getRefundData(json)

                    transactionMIT.refundTransaction(
                        amount,
                        folio,
                        auth
                    )
                }
            }

            // =====================================================
            // CONTROL
            // =====================================================
            is IncomingMessage.Control -> {

                if (viewModel.isCancelControl(message.json)) {

                    Log.d(
                        myTag,
                        "Comando CANCEL recibido. Ejecutando..."
                    )

                    CoroutineScope(Dispatchers.IO).launch {
                        readingMIT.cancelReadingCard()
                    }
                }

                viewModel.handleControl(message.json)
            }

            // =====================================================
            // SCREEN
            // =====================================================
            is IncomingMessage.Screen -> {
                viewModel.handleScreen(
                    message.type,
                    message.message
                )
            }

            // =====================================================
            // ACK
            // =====================================================
            is IncomingMessage.Ack -> {

                Log.e(
                    myTag,
                    "[ACK] ACK recibido desde host"
                )

                viewModel.onAckReceived()
            }

            // =====================================================
            // NACK
            // =====================================================
            is IncomingMessage.Nack -> {

                Log.w(
                    myTag,
                    "[NACK] NACK recibido"
                )

                viewModel.handleNack()
            }

            // =====================================================
            // UNKNOWN
            // =====================================================
            is IncomingMessage.Unknown -> {
                viewModel.handleUnknownMessage()
            }

            // =====================================================
            // TRANSACTION RESULT
            // =====================================================
            is IncomingMessage.TransactionResult -> {

                if (yaRecibiResultado) {

                    Log.d(
                        "SIAP",
                        "Resultado duplicado ignorado"
                    )

                    return
                }

                yaRecibiResultado = true
            }
        }
    }

    private fun writeSerial(message: String) {
        try {
            Log.e("SIAP_APK", "[SERIAL_OUT]: $message")
            comManager.write(message.toByteArray(StandardCharsets.US_ASCII))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun read(msg: ByteArray?) {
        Log.e(myTag, "Bytes Recibidos: $msg")
        serialManager.onBytesReceived(msg)
    }

    /**************************************************** [ MIT Callbacks ] **/
    override fun onConnectionResponse(reader: MITReader?, error: MITError?) {
        Log.d("SIAP", "[MIT:READER] Conectado")
    }

    override fun readingResult(result: String) {
        Log.e("SIAP_APK", "Error Recibido: $result")
        viewModel.onReadingResult(result)
    }

    override fun cancelReadingCard() {
        Log.e("SIAP_APK", "Error en lectura de tarjeta")
        viewModel.onCancelReadingCard()
        return
    }

    override fun confirmTransaction(bankCard: MITCard) {
        Log.e("SIAP_APK", "Tarjeta Validada")
        viewModel.onConfirmTransaction(bankCard)
        Log.e("SIAP_APK", "Mandando Pago")
        readingMIT.submitPayment("C", 1)
    }

    override fun displayTransactionResult(transaction: MITTransaction) {
        Log.e("SIAP_APK", "Resultado de la transaccion: $transaction")
        viewModel.onDisplayTransactionResult(transaction)
    }

    override fun onRefundResult(transaction: MITTransaction?, error: MITError?) {
        Log.e("SIAP_APK", "Respuesta a la devolución, Transaccion: $transaction, Error: $error")
        viewModel.onRefundResult(transaction, error)
    }

    override fun onReturnTransaction(report: MITReport?, error: MITError?) {
        Log.e("SIAP_APK", "Resultado de búsqueda por referencia: $report")
        viewModel.onLookupResult(report, error)
    }

    override fun deviceResult(result: String) {
        Log.d("SIAP", "[MIT:DEVICE] $result")
    }

    override fun onConnectionResponse(result: Boolean) {
        if (result) {
            Log.e("SIAP_APK", "Inicializando terminal y conectando lectora")
            readingMIT.connectReading()
            deviceMIT.initTerminal()
        }
    }

    override fun onInitTerminalError(result: String) {
        Log.e("SIAP_APK", "Error al inicializar terminal: $result")
        Log.e("SIAP", "[MIT:INIT_ERROR] $result")
        screenManager.show(TAG_STATUS, StatusFragment.error(
            "Terminal no disponible", "Intente más tarde"
        ))

        Handler(Looper.getMainLooper()).postDelayed({
            loginMIT.setCredentials()
        }, 5000)
    }

     override fun onInitTerminalSuccess() {
        Log.i("SIAP", "[MIT:INIT_OK] Terminal inicializada")
        viewModel.communicationAttempts.set(0)
        prefs.setHideButtons(true)
        deviceMIT.hideButtons(true)

        Log.e(myTag, "Terminal inicializada correctamente, cerrando mensaje de carga")
        screenManager.dismiss(TAG_LOADING)

        viewModel.handleNetworkTask()
        Handler(Looper.getMainLooper()).post { init() }
    }

    override fun loginResult(result: String) {
        Log.e("SIAP_APK", "Resultado del Login: $result")
        Log.d("SIAP", "[MIT:LOGIN] $result")
    }

    override fun onLoginError(result: String) {
        Log.e("SIAP_APK", "Error al iniciar sesión: $result")
        Log.e("SIAP", "[MIT:LOGIN_ERROR] $result")
        screenManager.show(TAG_STATUS, StatusFragment.error(
            "Terminal no disponible", "Intente más tarde"
        ))
        if(result.contains("No Network", true)){
            Log.e("LogMIT", "Se perdio la conexion durante el login")
            Handler(Looper.getMainLooper()).postDelayed({ loginMIT.setCredentials()},10000)
        }
    }

    override fun onLoginSuccess(login: MITLogin?) {
        Log.e(myTag, "Sesión Inicializada Correctamente")
        Log.i("SIAP", "[MIT:LOGIN_OK] Login exitoso")
        deviceMIT.connectDevice()
    }

    /**************************************************** [ Permissions ] **/

    private fun loadLogo() {
        Log.e(myTag, "Cargando Logo...")
        val logoImageView: ImageView = findViewById(R.id.img_logo)
        appConfigurator.cargarLogo(logoImageView)
        Log.e(myTag, "Logo Cargado")
    }

    private fun permissions() {
        Log.e(myTag, "Solicitando Permisos")
        val permissionsToCheck = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        val permissionsNeeded = mutableListOf<String>()
        for (permission in permissionsToCheck) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission)
            }
        }
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), locationAndPhonePermissions)
        } else {
            Log.e(myTag, "Permisos ya otorgados, seteando credenciales")
            loginMIT.setCredentials()
        }
    }

    private fun playSound(resourceId: Int) {
        if (!prefs.isSoundEnabled()) return
        val mediaPlayer = MediaPlayer.create(this, resourceId)
        mediaPlayer?.setOnCompletionListener { it.release() }
        mediaPlayer?.start()
    }
}
