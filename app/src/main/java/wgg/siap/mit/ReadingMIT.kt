package wgg.siap.mit

import android.content.Context
import android.util.Log
import mx.com.mit.mobile.mitmobilelibrary.manager.transaction.reading.MITReadingCallback
import mx.com.mit.mobile.mitmobilelibrary.manager.transaction.reading.MITReadingManager
import mx.com.mit.mobile.mitmobilelibrary.model.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.ArrayList

class ReadingMIT(
    private val context: Context,
    private val listener: ReadingListener
) : MITReadingCallback {
    private val readingManager: MITReadingManager by lazy {
        MITReadingManager(context, MITReader.Model.IM30, this)
    }
    private var currency: MITCurrency = MITCurrency.MXN
    private var userTransaction: String = ""
    //private val payment = AtomicInteger(0)
    fun connectReading() {
        readingManager.connect()
    }
    fun doRetail(
        amount: String,
        reference: String,
        timeoutSeconds: Int
    ) {
        // Timeout configurable entre 15 y 60 segundos (Manual SIAP §2.8)
        Log.d("APPLOG", "Estoy en el doRetail")
        val clampedTimeout = timeoutSeconds.coerceIn(15, 60)
        Log.d("APPLOG", "$clampedTimeout")
        readingManager.setReadingSettings(clampedTimeout, true, true)
        readingManager.doRetail(
            amount = amount,
            reference = reference,
            currency = this.currency,
            userTransaction = this.userTransaction)
    }

    fun cancelReadingCard()  {
        Log.d("APPLOG", "Estoy en un onCancelReadingCard")
        readingManager.cancelReadingCard()
    }
    fun submitPayment(merchant: String, flag: Int) {
        readingManager.submitPayment(merchant)
    }
    /**************************************************** [ M I T ] - MITDeviceCallback **/

    override fun onConnectionResponse(reader: MITReader?, error: MITError?) {
        listener.onConnectionResponse(reader, error)
    }

    override fun onMITError(error: MITError?) {
        Log.d("APPLOG", "Estoy en un mit error")
        error?.description?.let {
            listener.readingResult(it)
        }
    }

    override fun onPaymentAskDcc(localCurrency: MITDccOption, foreignCurrency: MITDccOption) = TODO()

    override fun onCancelReadingCard() {
        Log.d("APPLOG", "Estoy en un onCancelReading")
        listener.cancelReadingCard()
    }

    override fun onMerchantResponse(
        contado: ArrayList<MITMerchant>?,
        promotions: ArrayList<MITMerchant>?,
        error: MITError?
    ) {
        listener.readingResult("onObtainMerchantCallback")
    }

    override fun onCardInformationResponse(card: MITCard) {
        listener.confirmTransaction(card)
    }

    override fun onTransactionResponse(transaction: MITTransaction) {
        listener.displayTransactionResult(transaction)
    }

    override fun onTransactionProgress(progress: MITProgress) { /** Don't support IM30 **/ }

    override fun onRequestCVVAmex() { /** Don't support IM30 **/ }

    override fun onSelectApplication(cardApplications: ArrayList<MITCardApplication>) { /** Don't support IM30 **/ }

    interface ReadingListener {
        fun onConnectionResponse(reader: MITReader?, error: MITError?)
        fun readingResult(result: String)
        fun cancelReadingCard()
        fun confirmTransaction(bankCard: MITCard)
        fun displayTransactionResult(transaction: MITTransaction)
    }
}