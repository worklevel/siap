package wgg.siap.system

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import wgg.siap.InitApplication.Companion.prefs
import wgg.siap.data.Texts
import wgg.siap.mit.DeviceMIT
import wgg.siap.R
import wgg.siap.databinding.SettingsActivityBinding

class SettingsActivity : AppCompatActivity(), DeviceMIT.DeviceListener {
    private lateinit var binding: SettingsActivityBinding
    private val deviceMIT: DeviceMIT by lazy { DeviceMIT(this, this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = SettingsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        deviceMIT.connectDevice()

        binding.back.setOnClickListener { hideKeyboard(); finish() }

        // ---- Switches (efecto inmediato) ----
        // checked = botones visibles, unchecked = botones ocultos
        binding.switchHideButtons.isChecked = !prefs.getHideButtons()
        binding.switchHideButtons.setOnCheckedChangeListener { _, isChecked ->
            val hide = !isChecked
            prefs.setHideButtons(hide)
            deviceMIT.hideButtons(hide)
        }

        /****** S O N I D O S ******/
        binding.enableSounds.isChecked = prefs.isSoundEnabled()
        binding.enableSounds.setOnCheckedChangeListener { _, isChecked ->
            prefs.setSoundEnabled(isChecked)
            val message = if (isChecked) "Sonidos activados" else "Sonidos desactivados"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        binding.showAmex.isChecked = prefs.getAmex()
        binding.showAmex.setOnCheckedChangeListener { _, isChecked -> prefs.setAmex(isChecked) }

        binding.showGadapi.isChecked = prefs.getGadapi()
        binding.showGadapi.setOnCheckedChangeListener { _, isChecked -> prefs.setGadapi(isChecked) }

        // ---- Cargar valores actuales ----
        binding.editTextHomeTitle.setText(prefs.getHomeTitle())
        binding.editTextHomeSubtitle.setText(prefs.getHomeSubtitle())
        binding.editTextReferenceBank.setText(prefs.getReference())
        binding.editTextTimeout.setText(prefs.getTmout().toString())

        val texts = prefs.getTexts()
        binding.editTextMsgLoadingPayment.setText(texts.msgLoadingPayment)
        binding.editTextMsgLoadingChip.setText(texts.msgLoadingChip)
        binding.editTextMsgSuccessPrefix.setText(texts.msgSuccessPrefix)
        binding.editTextMsgCancel.setText(texts.msgCancel)
        binding.editTextMsgTimeout.setText(texts.msgTimeout)
        binding.editTextMsgCardRemoved.setText(texts.msgCardRemoved)
        binding.editTextMsgServiceError.setText(texts.msgServiceError)
        binding.editTextMsgNetworkError.setText(texts.msgNetworkError)
        binding.editTextAutoDismissError.setText(texts.autoDismissErrorMs.toString())
        binding.editTextAutoDismissSuccess.setText(texts.autoDismissSuccessMs.toString())

        // ---- Guardar todo ----
        binding.btnSave.setOnClickListener { saveAll(texts) }
    }

    private fun saveAll(defaults: Texts) {
        // Validar timeout
        val timeout = binding.editTextTimeout.text.toString().toIntOrNull()
        if (timeout == null || timeout < 15 || timeout > 60) {
            Toast.makeText(this, "Timeout: valor entre 15 y 60 segundos", Toast.LENGTH_SHORT).show()
            return
        }

        // Validar auto-dismiss
        val dismissError = binding.editTextAutoDismissError.text.toString().toLongOrNull()
        val dismissSuccess = binding.editTextAutoDismissSuccess.text.toString().toLongOrNull()
        if (dismissError == null || dismissError < 500) {
            Toast.makeText(this, "Auto-cerrar error: minimo 500 ms", Toast.LENGTH_SHORT).show()
            return
        }
        if (dismissSuccess == null || dismissSuccess < 500) {
            Toast.makeText(this, "Auto-cerrar exito: minimo 500 ms", Toast.LENGTH_SHORT).show()
            return
        }

        // Guardar home, referencia y timeout
        prefs.setHomeTitle(binding.editTextHomeTitle.text.toString().ifBlank { "Bienvenido" })
        prefs.setHomeSubtitle(binding.editTextHomeSubtitle.text.toString().ifBlank { "Pago bancario disponible" })
        prefs.setReference(binding.editTextReferenceBank.text.toString())
        prefs.setTmout(timeout)

        // Guardar textos
        val updated = Texts(
            msgLoadingPayment    = binding.editTextMsgLoadingPayment.text.toString().ifBlank { defaults.msgLoadingPayment },
            msgLoadingChip       = binding.editTextMsgLoadingChip.text.toString().ifBlank { defaults.msgLoadingChip },
            msgSuccessPrefix     = binding.editTextMsgSuccessPrefix.text.toString().ifBlank { defaults.msgSuccessPrefix },
            msgCancel            = binding.editTextMsgCancel.text.toString().ifBlank { defaults.msgCancel },
            msgTimeout           = binding.editTextMsgTimeout.text.toString().ifBlank { defaults.msgTimeout },
            msgCardRemoved       = binding.editTextMsgCardRemoved.text.toString().ifBlank { defaults.msgCardRemoved },
            msgServiceError      = binding.editTextMsgServiceError.text.toString().ifBlank { defaults.msgServiceError },
            msgNetworkError      = binding.editTextMsgNetworkError.text.toString().ifBlank { defaults.msgNetworkError },
            autoDismissErrorMs   = dismissError,
            autoDismissSuccessMs = dismissSuccess
        )
        prefs.saveTexts(updated)
        Toast.makeText(this, "Configuracion guardada", Toast.LENGTH_SHORT).show()
    }

    override fun onPause() {
        super.onPause()
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    /**************************************************** [ M I T ] - DeviceMIT **/
    override fun deviceResult(result: String) {}
    override fun onConnectionResponse(result: Boolean) {}
    override fun onInitTerminalError(error: String) {}
    override fun onInitTerminalSuccess() {}
}
