package wgg.siap.system

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import wgg.siap.MainActivity

class Relaucher: Application() {
    override fun onCreate() {
        super.onCreate()
        setupExceptionHandler()
    }

    private fun setupExceptionHandler() {
        val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e("MyApplication", "Uncaught exception in thread: ${thread.name}", exception)

            // Intent to restart the application
            val intent = Intent(applicationContext, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pendingIntent =
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)

            // Schedule the application to restart
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExact(
                AlarmManager.RTC,
                System.currentTimeMillis() + 1000,
                pendingIntent
            )

            // Kill the current process
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }
}
