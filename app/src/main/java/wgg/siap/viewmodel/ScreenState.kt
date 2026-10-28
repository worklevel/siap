package wgg.siap.viewmodel

sealed class ScreenState {
    object Idle : ScreenState()
    data class Loading(val message: String) : ScreenState()
    data class Success(val title: String, val message: String = "") : ScreenState()
    data class SuccessRefound(val title: String, val message: String = "") : ScreenState()
    data class SuccessAck(val title: String, val message: String = "") : ScreenState()
    data class Error(val title: String, val subtitle: String = "", val persistent: Boolean = false) : ScreenState()
    data class ErrorAck(val title: String, val subtitle: String = "", val persistent: Boolean = false) : ScreenState()
    data class Cancel(val title: String, val message: String = "") : ScreenState()
    data class Warning(val type: String, val title: String, val message: String = "") : ScreenState()
}
