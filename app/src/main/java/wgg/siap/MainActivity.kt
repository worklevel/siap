package wgg.siap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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

    private val serialManager by lazy {
        SerialManager { message, _ -> handleIncomingMessage(message) }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var appConfigurator: AppConfigurator

    companion object {
        private const val TAG_LOADING = "loadingScreen"
        private const val TAG_STATUS  = "statusScreen"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appConfigurator = AppConfigurator(this)
        if (!appConfigurator.verificarConfiguracion()) {
            Log.w("SIAP", "[CONFIG] Sin configuración, lanzando StartActivity")
            val intent = Intent(this, Class.forName("com.example.firebasedatamodule.StartActivity"))
            startActivity(intent)
            finish()
            return
        }

        observeViewModel()
        screenManager.show(TAG_LOADING, LoadingFragment.newInstance("Iniciando..."))
        comManager.connect()
        permissions()
    }

    private fun observeViewModel() {
        val texts = prefs.getTexts()

        viewModel.screenState.observe(this) { state ->
            val texts = prefs.getTexts()
            when (state) {
                is ScreenState.Idle -> screenManager.dismissAll()

                is ScreenState.Loading -> {
                    screenManager.show(TAG_LOADING, LoadingFragment.newInstance(state.message))
                }

                is ScreenState.Success -> {
                    playSound(R.raw.approved_sound_v1)
                    screenManager.show(TAG_STATUS, StatusFragment.success(state.title, state.message))
                    screenManager.scheduleAutoDismiss(TAG_STATUS, texts.autoDismissSuccessMs)
                }

                is ScreenState.Error -> {
                    screenManager.show(TAG_STATUS, StatusFragment.error(state.title, state.subtitle))
                    if (!state.persistent) {
                        screenManager.scheduleAutoDismiss(TAG_STATUS, texts.autoDismissErrorMs)
                    }
                }

                is ScreenState.Cancel -> {
                    playSound(R.raw.deny_sound_v2)
                    screenManager.show(TAG_STATUS, StatusFragment.cancel(state.title, state.message))
                    screenManager.scheduleAutoDismiss(TAG_STATUS, texts.autoDismissErrorMs)
                }

                is ScreenState.Warning -> {
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
    }

    private fun init() {
        binding.firstStep.setOnClickListener { binding.secondStep.isEnabled = true }
        binding.secondStep.setOnClickListener { binding.settings.isEnabled = true }
        binding.settings.setOnClickListener {
            binding.settings.isEnabled = false
            binding.secondStep.isEnabled = false
            startActivity(
                Intent(this, PasswordActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }

    override fun onStart() {
        super.onStart()
        serialManager.clearBuffer()
    }

    override fun onResume() {
        super.onResume()
        binding.payments.setImageResource(if (prefs.getAmex()) R.drawable.marka else R.drawable.marks)
        binding.sponsor.setImageResource(if (prefs.getGadapi()) R.drawable.sponsorg else R.drawable.sponsor)
        binding.txtTitleHome.text = prefs.getHomeTitle()
        binding.txtSubtitleHome.text = prefs.getHomeSubtitle()
    }

    override fun onDestroy() {
        super.onDestroy()
        comManager.disconnect()
    }

    /**************************************************** [ Serial ] **/

    private fun handleIncomingMessage(message: IncomingMessage) {
        when (message) {
            is IncomingMessage.Payment -> {
                viewModel.paymentProcess(message.json)
                if (viewModel.isProcessingPayment.get()) {
                    readingMIT.doRetail(
                        viewModel.getAmountForRetail(),
                        viewModel.getReferenceForRetail(),
                        viewModel.getTimeoutForRetail()
                    )
                }
            }
            is IncomingMessage.Refund -> {
                val json = message.json
                viewModel.refundProcess(json)
                if (viewModel.isProcessingPayment.get()) {
                    val (amount, folio, auth) = viewModel.getRefundData(json)
                    transactionMIT.refundTransaction(amount, folio, auth)
                }
            }
            is IncomingMessage.Control -> {
                if (viewModel.isCancelControl(message.json)) {
                    readingMIT.cancelReadingCard()
                }
                viewModel.handleControl(message.json)
            }
            is IncomingMessage.Screen -> viewModel.handleScreen(message.type, message.message)
            is IncomingMessage.Ack    -> viewModel.handleAck()
            is IncomingMessage.Nack   -> viewModel.handleNack()
            is IncomingMessage.Unknown -> viewModel.handleUnknownMessage()
        }
    }

    private fun writeSerial(message: String) {
        try {
            Log.d("SIAP", "[SERIAL:WRITE] $message")
            comManager.write(message.toByteArray(StandardCharsets.US_ASCII))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun read(msg: ByteArray?) {
        serialManager.onBytesReceived(msg)
    }

    /**************************************************** [ MIT Callbacks ] **/

    override fun onConnectionResponse(reader: MITReader?, error: MITError?) {
        Log.d("SIAP", "[MIT:READER] Conectado")
    }

    override fun readingResult(result: String) {
        viewModel.onReadingResult(result)
    }

    override fun cancelReadingCard() {
        viewModel.onCancelReadingCard()
    }

    override fun confirmTransaction(bankCard: MITCard) {
        viewModel.onConfirmTransaction(bankCard)
        readingMIT.submitPayment("C", 1)
    }

    override fun displayTransactionResult(transaction: MITTransaction) {
        viewModel.onDisplayTransactionResult(transaction)
    }

    override fun onRefundResult(transaction: MITTransaction?, error: MITError?) {
        viewModel.onRefundResult(transaction, error)
    }

    override fun deviceResult(result: String) {
        Log.d("SIAP", "[MIT:DEVICE] $result")
    }

    override fun onConnectionResponse(result: Boolean) {
        if (result) {
            readingMIT.connectReading()
            deviceMIT.initTerminal()
        }
    }

    override fun onInitTerminalError(result: String) {
        Log.e("SIAP", "[MIT:INIT_ERROR] $result")
        screenManager.show(TAG_STATUS, StatusFragment.error(
            "Terminal no disponible", "Contacte a soporte técnico"
        ))
    }

    override fun onInitTerminalSuccess() {
        Log.i("SIAP", "[MIT:INIT_OK] Terminal inicializada")
        viewModel.communicationAttempts.set(0)
        prefs.setHideButtons(true)
        deviceMIT.hideButtons(true)
        screenManager.dismiss(TAG_LOADING)
        Handler(Looper.getMainLooper()).post { init() }
    }

    override fun loginResult(result: String) {
        Log.d("SIAP", "[MIT:LOGIN] $result")
    }

    override fun onLoginError(result: String) {
        Log.e("SIAP", "[MIT:LOGIN_ERROR] $result")
        screenManager.show(TAG_STATUS, StatusFragment.error(
            "Terminal no disponible", "Contacte a soporte técnico"
        ))
    }

    override fun onLoginSuccess(login: MITLogin?) {
        Log.i("SIAP", "[MIT:LOGIN_OK] Login exitoso")
        deviceMIT.connectDevice()
    }

    /**************************************************** [ Permissions ] **/

    private fun loadLogo() {
        val logoImageView: ImageView = findViewById(R.id.img_logo)
        appConfigurator.cargarLogo(logoImageView)
    }

    private fun permissions() {
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
            loginMIT.setCredentials()
            loadLogo()
        }
    }

    private fun playSound(resourceId: Int) {
        if (!prefs.isSoundEnabled()) return
        val mediaPlayer = MediaPlayer.create(this, resourceId)
        mediaPlayer?.setOnCompletionListener { it.release() }
        mediaPlayer?.start()
    }
}
