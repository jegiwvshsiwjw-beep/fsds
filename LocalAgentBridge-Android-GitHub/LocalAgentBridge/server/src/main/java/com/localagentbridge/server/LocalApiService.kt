package com.localagentbridge.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class LocalApiService : Service() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Local Agent Bridge ativo")
            .setContentText("API local disponível na rede Wi-Fi")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ApiServer.start(applicationContext)
            AppPrefs.prefs(this).edit().putBoolean(AppPrefs.KEY_RUNNING, true).apply()
        } catch (error: Exception) {
            AppPrefs.prefs(this).edit().putBoolean(AppPrefs.KEY_RUNNING, false).apply()
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ApiServer.stop()
        AppPrefs.prefs(this).edit().putBoolean(AppPrefs.KEY_RUNNING, false).apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servidor de API local",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "local_agent_bridge_server"
        private const val NOTIFICATION_ID = 4201
    }
}
