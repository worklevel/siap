package wgg.siap.data

import android.content.Context

class Prefs(val context: Context) {
    private val DEVICE    = "PAX"
    private val REFERENCE = "Demo"
    private val TMOUT     = "tmout"
    private val AMEX         = "amex"
    private val GADAPI       = "gadapi"
    private val HIDE_BUTTONS    = "hide_buttons"
    private val KEY_HOME_TITLE    = "home_title"
    private val KEY_HOME_SUBTITLE = "home_subtitle"

    // Texts keys
    private val KEY_MSG_LOADING_PAYMENT    = "msg_loading_payment"
    private val KEY_MSG_LOADING_CHIP       = "msg_loading_chip"
    private val KEY_MSG_SUCCESS_PREFIX     = "msg_success_prefix"
    private val KEY_MSG_CANCEL             = "msg_cancel"
    private val KEY_MSG_TIMEOUT            = "msg_timeout"
    private val KEY_MSG_CARD_REMOVED       = "msg_card_removed"
    private val KEY_MSG_SERVICE_ERROR      = "msg_service_error"
    private val KEY_MSG_NETWORK_ERROR      = "msg_network_error"
    private val KEY_AUTO_DISMISS_ERROR_MS  = "auto_dismiss_error_ms"
    private val KEY_AUTO_DISMISS_SUCCESS_MS = "auto_dismiss_success_ms"
    private val SOUND_ENABLED = "sound_enabled"
    private val NO_ACK = "ack"

    private val storage = context.getSharedPreferences(DEVICE, 0)
    private val defaults = Texts()

    /**************************************************** [ REFERENCE / TIMEOUT ] **/

    fun setReference(result: String) = storage.edit().putString(REFERENCE, result).apply()
    fun getReference(): String = storage.getString(REFERENCE, "") ?: ""

    fun setTmout(result: Int) = storage.edit().putInt(TMOUT, result).apply()
    fun getTmout(): Int = storage.getInt(TMOUT, 15)

    fun setAmex(result: Boolean) = storage.edit().putBoolean(AMEX, result).apply()
    fun getAmex(): Boolean = storage.getBoolean(AMEX, true)

    fun setGadapi(result: Boolean) = storage.edit().putBoolean(GADAPI, result).apply()
    fun getGadapi(): Boolean = storage.getBoolean(GADAPI, false)

    fun setHideButtons(result: Boolean) = storage.edit().putBoolean(HIDE_BUTTONS, result).apply()
    fun getHideButtons(): Boolean = storage.getBoolean(HIDE_BUTTONS, true)

    fun setHomeTitle(result: String) = storage.edit().putString(KEY_HOME_TITLE, result).apply()
    fun getHomeTitle(): String = storage.getString(KEY_HOME_TITLE, "Bienvenido") ?: "Bienvenido"

    fun setHomeSubtitle(result: String) = storage.edit().putString(KEY_HOME_SUBTITLE, result).apply()
    fun getHomeSubtitle(): String = storage.getString(KEY_HOME_SUBTITLE, "Pago bancario disponible") ?: "Pago bancario disponible"

    fun setNoAck(retult: String) = storage.edit().putString(NO_ACK, retult).apply()
    fun getTimeErrorNoAck(): String = storage.getString(NO_ACK, "10") ?: "10"

    /** MÉTODOS  PARA SWITCH DE SONIDOS **/
    fun setSoundEnabled(isEnabled: Boolean) {
        storage.edit().putBoolean(SOUND_ENABLED, isEnabled).apply()
    }
    fun isSoundEnabled(): Boolean {
        return storage.getBoolean(SOUND_ENABLED, true)
    }

    /**************************************************** [ TEXTS ] **/

    fun getTexts(): Texts = Texts(
        msgLoadingPayment    = storage.getString(KEY_MSG_LOADING_PAYMENT, defaults.msgLoadingPayment) ?: defaults.msgLoadingPayment,
        msgLoadingChip       = storage.getString(KEY_MSG_LOADING_CHIP, defaults.msgLoadingChip) ?: defaults.msgLoadingChip,
        msgSuccessPrefix     = storage.getString(KEY_MSG_SUCCESS_PREFIX, defaults.msgSuccessPrefix) ?: defaults.msgSuccessPrefix,
        msgCancel            = storage.getString(KEY_MSG_CANCEL, defaults.msgCancel) ?: defaults.msgCancel,
        msgTimeout           = storage.getString(KEY_MSG_TIMEOUT, defaults.msgTimeout) ?: defaults.msgTimeout,
        msgCardRemoved       = storage.getString(KEY_MSG_CARD_REMOVED, defaults.msgCardRemoved) ?: defaults.msgCardRemoved,
        msgServiceError      = storage.getString(KEY_MSG_SERVICE_ERROR, defaults.msgServiceError) ?: defaults.msgServiceError,
        msgNetworkError      = storage.getString(KEY_MSG_NETWORK_ERROR, defaults.msgNetworkError) ?: defaults.msgNetworkError,
        autoDismissErrorMs   = storage.getLong(KEY_AUTO_DISMISS_ERROR_MS, defaults.autoDismissErrorMs),
        autoDismissSuccessMs = storage.getLong(KEY_AUTO_DISMISS_SUCCESS_MS, defaults.autoDismissSuccessMs)
    )

    fun saveTexts(texts: Texts) {
        storage.edit().apply {
            putString(KEY_MSG_LOADING_PAYMENT,     texts.msgLoadingPayment)
            putString(KEY_MSG_LOADING_CHIP,        texts.msgLoadingChip)
            putString(KEY_MSG_SUCCESS_PREFIX,      texts.msgSuccessPrefix)
            putString(KEY_MSG_CANCEL,              texts.msgCancel)
            putString(KEY_MSG_TIMEOUT,             texts.msgTimeout)
            putString(KEY_MSG_CARD_REMOVED,        texts.msgCardRemoved)
            putString(KEY_MSG_SERVICE_ERROR,       texts.msgServiceError)
            putString(KEY_MSG_NETWORK_ERROR,       texts.msgNetworkError)
            putLong(KEY_AUTO_DISMISS_ERROR_MS,     texts.autoDismissErrorMs)
            putLong(KEY_AUTO_DISMISS_SUCCESS_MS,   texts.autoDismissSuccessMs)
        }.apply()
    }
}
