package wgg.siap.mit

import android.content.Context
import android.util.Log
import mx.com.mit.mobile.mitmobilelibrary.manager.functions.MITTransactionCallback
import mx.com.mit.mobile.mitmobilelibrary.manager.functions.MITTransactionManager
import mx.com.mit.mobile.mitmobilelibrary.model.MITError
import mx.com.mit.mobile.mitmobilelibrary.model.MITReport
import mx.com.mit.mobile.mitmobilelibrary.model.MITTransaction

class TransactionMIT(
    private val context: Context,
    private val listener: TransactionListener
) : MITTransactionCallback {

    private val transactionManager: MITTransactionManager by lazy {
        MITTransactionManager(context, this)
    }

    fun refundTransaction(amount: String, folio: String, auth: String) {
        transactionManager.refundTransaction(amount, folio, auth)
    }

    fun getTransactionsByReference(reference: String){
        transactionManager.getTransactionsByReference(reference)
    }

    /**************************************************** [ M I T ] - MITTransactionCallback **/

    override fun onSaveSignatureResponse(error: MITError?) {
        Log.d("TransactionMIT", "onSaveSignatureResponse: $error")
    }

    override fun onVoucherByEmailResponse(error: MITError?) {
        Log.d("TransactionMIT", "onVoucherByEmailResponse: $error")
    }

    override fun onVoucherBySmsResponse(error: MITError?) {
        Log.d("TransactionMIT", "onVoucherBySmsResponse: $error")
    }

    override fun onURLVoucherResponse(urlTicket: String?, error: MITError?) {
        Log.d("TransactionMIT", "onURLVoucherResponse: $urlTicket, error: $error")
    }

    override fun onRefundTransaction(transaction: MITTransaction?, error: MITError?) {
        Log.d("TransactionMIT", "onRefoundTransaction: Approved=${transaction?.approved}, Error=${error?.description}")
        listener.onRefundResult(transaction, error)
    }

    override fun onReturnTransactions(report: MITReport?, error: MITError?) {
        Log.d("TransactionMIT", "onReturnTransactions: $report, error: $error")
        listener.onReturnTransaction(report = report, error = error)
    }

    interface TransactionListener {
        fun onRefundResult(transaction: MITTransaction?, error: MITError?)
        fun onReturnTransaction(report: MITReport?, error: MITError?)
    }
}
