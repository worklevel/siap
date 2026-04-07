package wgg.siap

import android.app.Application
import wgg.siap.data.Prefs

class InitApplication: Application() {
    companion object{
        lateinit var prefs: Prefs
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(applicationContext)
    }
}