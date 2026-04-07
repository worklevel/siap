package wgg.siap.mit

import android.content.Context
import android.util.Log
import mx.com.mit.mobile.mitmobilelibrary.manager.com.rs232.MITComCallback
import mx.com.mit.mobile.mitmobilelibrary.manager.com.rs232.MITComManager
import mx.com.mit.mobile.mitmobilelibrary.model.MITError
import mx.com.mit.mobile.mitmobilelibrary.model.MITPort
import mx.com.mit.mobile.mitmobilelibrary.model.MITReader

class ComMIT(
    context: Context,
    private val listener: ComListener
) : MITComCallback {

    private val comManager: MITComManager by lazy {
        MITComManager(context, MITReader.Model.IM30, this)
    }
    fun connect() {
        comManager.setComParams(MITPort.COM2, 9600, 8, 'n', 1)
        comManager.connectComPort()
    }
    fun disconnect() {
        comManager.disconnectComPort()
    }
    fun write(msg: ByteArray) {
        comManager.writeMessage(msg)
    }
    override fun onCOMError(error: MITError) {
        Log.e("ComMIT", "onCOMError: $error")
    }
    override fun onConnected(msg: String) {
        Log.i("ComMIT", "onConnected: $msg")
    }
    override fun onDisconnected() {
        Log.w("ComMIT", "onDisconnected")
    }
    override fun onReadMessage(msg: ByteArray?) {
        listener.read(msg)
    }
    override fun onSetParam() {
        Log.d("ComMIT", "onSetParam")
    }
    interface ComListener {
        fun read(msg: ByteArray?)
    }
}