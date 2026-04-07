package wgg.siap.system

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import wgg.siap.BuildConfig
import wgg.siap.R
import java.math.BigInteger
import java.security.MessageDigest

class PasswordActivity : AppCompatActivity() {
    lateinit var count: TextView
    private lateinit var timerCloseActivity: CountDownTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password)
        val PASS = BuildConfig.ACCESS
        val pass: EditText = findViewById(R.id.etxt_password)
        pass.setOnKeyListener { _, keyCode, event ->
            when {
                ((keyCode == KeyEvent.KEYCODE_ENTER) && (event.action == KeyEvent.ACTION_DOWN)) -> {
                    if(PASS == encrypt(pass.text.toString())) {
                        val intent = Intent(this, SettingsActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        pass.setText("")
                    }
                    return@setOnKeyListener true
                }
                else -> false
            }
        }
        timerCloseActivity = object: CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                count = findViewById<TextView>(R.id.counter)
                count.text = (millisUntilFinished/1000).toString()
            }
            override fun onFinish() {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
                finish()
            }
        }
        timerCloseActivity.start()
    }
    private fun encrypt(input:String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }
}