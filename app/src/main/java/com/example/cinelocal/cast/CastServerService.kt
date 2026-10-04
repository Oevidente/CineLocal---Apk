package com.example.cinelocal.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.cinelocal.MainActivity

class CastServerService : Service() {

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL_ID = "cinelocal_cast_channel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_START = "com.example.cinelocal.cast.START"
        const val ACTION_STOP = "com.example.cinelocal.cast.STOP"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.cinelocal.cast.TOGGLE_PLAY_PAUSE"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_MEDIA_TITLE = "media_title"
        const val EXTRA_IS_PLAYING = "is_playing"

        fun start(context: Context, deviceName: String, mediaTitle: String, isPlaying: Boolean = true) {
            val intent = Intent(context, CastServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_MEDIA_TITLE, mediaTitle)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CastServerService::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            try {
                CastManager.getInstance(applicationContext).disconnect()
            } catch (_: Exception) {}
            stopForegroundService()
            return START_NOT_STICKY
        }

        if (action == ACTION_TOGGLE_PLAY_PAUSE) {
            try {
                CastManager.getInstance(applicationContext).togglePlayPause()
            } catch (_: Exception) {}
            return START_STICKY
        }

        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "Chromecast"
        val mediaTitle = intent?.getStringExtra(EXTRA_MEDIA_TITLE) ?: "Reproduzindo na TV"
        val isPlaying = intent?.getBooleanExtra(EXTRA_IS_PLAYING, true) ?: true

        val notification = buildNotification(deviceName, mediaTitle, isPlaying)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun acquireLocks() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CineLocal:CastServerWifiLock")
            wifiLock?.acquire()

            val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CineLocal:CastServerWakeLock")
            wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12h
        } catch (_: Exception) {}
    }

    private fun releaseLocks() {
        try {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (_: Exception) {}
        wifiLock = null

        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun stopForegroundService() {
        releaseLocks()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Transmissão para TV"
            val desc = "Controles ativos durante a transmissão Chromecast"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(deviceName: String, mediaTitle: String, isPlaying: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val toggleIntent = Intent(this, CastServerService::class.java).apply {
            action = ACTION_TOGGLE_PLAY_PAUSE
        }
        val pToggleIntent = PendingIntent.getService(
            this,
            1,
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, CastServerService::class.java).apply {
            action = ACTION_STOP
        }
        val pStopIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isPlaying) "Pausar" else "Reproduzir"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transmitindo para $deviceName")
            .setContentText(mediaTitle)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pOpenIntent)
            .setOngoing(true)
            .addAction(playPauseIcon, playPauseText, pToggleIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desconectar", pStopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
}
