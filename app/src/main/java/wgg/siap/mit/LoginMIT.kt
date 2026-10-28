package wgg.siap.mit

import android.content.Context
import android.util.Log
import com.example.firebasedatamodule.SecureDataManager
import mx.com.mit.mobile.mitmobilelibrary.manager.login.MITLoginCallback
import mx.com.mit.mobile.mitmobilelibrary.manager.login.MITLoginManager
import mx.com.mit.mobile.mitmobilelibrary.model.MITEnvironment
import mx.com.mit.mobile.mitmobilelibrary.model.MITError
import mx.com.mit.mobile.mitmobilelibrary.model.MITLogin
import mx.com.mit.mobile.mitmobilelibrary.model.MITReader
import kotlin.getValue

class LoginMIT(
    private val context: Context,
    private val listener: LoginListener
) : MITLoginCallback {

    private val loginManager: MITLoginManager by lazy {
        MITLoginManager(context, this, MITEnvironment.QA, MITReader.Model.IM30)
    }

    fun setCredentials() {
        val secureDataManager = SecureDataManager(context)
        val comercio = secureDataManager.getComercio()

        if (comercio != null) {
            val datos = secureDataManager.getData(comercio)
            if (datos != null) {
                val businessId = datos["BusinessID"]
                val apiKey = datos["ApiKey"]
                val encryptKey = datos["EncryptionKey"]

                if (businessId != null && apiKey != null && encryptKey != null) {
                    Log.i("SIAP", "[LOGIN] Credenciales obtenidas de SecureDataManager")
                    loginManager.doLogin(businessId, apiKey, encryptKey)
                    return
                }
            }
        }

        Log.e("SIAP", "[LOGIN] Credenciales no disponibles")
        listener.loginResult("Credenciales no disponibles")
    }

    override fun onLoginResponse(login: MITLogin?, error: MITError?) {
        if (login != null) {
            Log.i("SIAP", "[LOGIN:OK] $login")
            listener.onLoginSuccess(login)
        } else {
            Log.e("SIAP", "[LOGIN:ERROR] $error")
            listener.onLoginError(error.toString())
        }
    }

    override fun onSessionResponse(login: MITLogin?) {
        Log.d("SIAP", "[LOGIN:SESSION] $login")
    }

    interface LoginListener {
        fun loginResult(result: String)
        fun onLoginError(result: String)
        fun onLoginSuccess(login: MITLogin?)
    }
}
