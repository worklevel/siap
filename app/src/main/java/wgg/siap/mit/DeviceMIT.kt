package wgg.siap.mit

import android.content.Context
import mx.com.mit.mobile.mitmobilelibrary.manager.device.MITDeviceCallback
import mx.com.mit.mobile.mitmobilelibrary.manager.device.MITDeviceManager
import mx.com.mit.mobile.mitmobilelibrary.model.MITBondedDevices
import mx.com.mit.mobile.mitmobilelibrary.model.MITEnvironment
import mx.com.mit.mobile.mitmobilelibrary.model.MITError
import mx.com.mit.mobile.mitmobilelibrary.model.MITProcessStatus
import mx.com.mit.mobile.mitmobilelibrary.model.MITReader
class DeviceMIT(
    private val context: Context,
    private val listener: DeviceListener
) : MITDeviceCallback {
    private val deviceManager: MITDeviceManager by lazy {
        MITDeviceManager(context, MITReader.Model.IM30, this, MITEnvironment.QA)
    }

    fun connectDevice() {
        deviceManager.connect()
    }

    fun initTerminal() {
        deviceManager.initTerminal()
    }

    fun hideButtons(visible: Boolean) {
        deviceManager.hideButtons(visible)
    }


    /**************************************************** [ M I T ] - MITDeviceCallback **/

    override fun onConnectionResponse(reader: MITReader?, error: MITError?) {
        if(reader != null) {
            listener.onConnectionResponse(true)
        } else if(error != null) {
            error.description?.let { listener.onConnectionResponse(false) }
        }
    }

    override fun onDeviceFoundResponse(foundDevice: MITBondedDevices) {
    }

    override fun onInitTerminalResponse(error: MITError?) {
        if(error != null) {
            listener.onInitTerminalError(error.toString())
        } else {
            listener.onInitTerminalSuccess()
        }
    }

    override fun onMITError(error: MITError?) {
    }


    override fun onDeviceInformationResponse(reader: MITReader) {
        listener.deviceResult("########################## - onDeviceInformationResponse[Success]: $reader")
    }

    override fun onDeviceScanFinishedResponse() {
    }

    override fun onEmvConfigResponse(error: MITError?) { /** Don't support IM30 **/ }
    override fun isCardPresent(hasCard: Boolean, error: MITError?) { /** Don't support IM30 **/ }
    override fun onPrintResult(error: MITError?) { /** Don't support IM30 **/ }
    override fun onRePrintVoucherResponse(error: MITError?) { /** Don't support IM30 **/ }
    override fun onResultBarCode(content: String?, error: MITError?) { /** Don't use **/ }
    override fun onUpdateFirmwereResponse(
        process: Int,
        processStatus: MITProcessStatus,
        error: MITError?
    ) {
    }

    interface DeviceListener {
        fun deviceResult(result: String)
        fun onConnectionResponse(result: Boolean)
        fun onInitTerminalError(result: String)
        fun onInitTerminalSuccess()
    }
}

