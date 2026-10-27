package com.danielportelabyrne.cinema.ui.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.music.cast.CastHost
import com.danielportelabyrne.cinema.data.settings.CinemaSettings
import com.danielportelabyrne.cinema.data.settings.Integration
import com.danielportelabyrne.cinema.device.AndroidPlatform
import com.danielportelabyrne.cinema.platform.Log
import java.util.UUID

/**
 * Keeps the TV in phones' cast lists while Music is set up, from boot on, even with Cinema closed. A foreground
 * service, so Android leaves it running; TVs do not show its notification.
 */
class CastService : Service() {
    private var multicast: WifiManager.MulticastLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Casting", NotificationManager.IMPORTANCE_MIN))
        @Suppress("DEPRECATION")
        val notification = (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this))
            .setSmallIcon(R.drawable.ic_cin_music).setContentTitle("Ready to cast music").build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIFICATION, notification)

        val settings = CinemaSettings(AndroidPlatform(this))
        Music.use(settings.subsonic.takeIf { settings.isOn(Integration.MUSIC) })
        multicast = applicationContext.getSystemService(WifiManager::class.java)?.createMulticastLock("cinema-cast")?.apply {
            setReferenceCounted(false); acquire()
        }
        val prefs = getSharedPreferences("cast", MODE_PRIVATE)
        val uuid = prefs.getString("uuid", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("uuid", it).apply() }
        val device = Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
        val host = CastHost(Cast, uuid, "Cinema ($device)")
        Cast.host = host
        Thread { runCatching { host.start() }.onFailure { Log.e(TAG, "renderer failed to start", it) } }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        val host = Cast.host
        Cast.host = null
        Thread { host?.stop() }.start()
        multicast?.release()
        super.onDestroy()
    }

    /** Starts the service after a reboot or an update. */
    class Boot : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) sync(context)
        }
    }

    companion object {
        private const val TAG = "CinemaCast"
        private const val CHANNEL = "cast"
        private const val NOTIFICATION = 7

        /** Runs the service when Music is on and set up, and stops it otherwise. */
        fun sync(context: Context) {
            val settings = CinemaSettings(AndroidPlatform(context))
            val on = settings.isOn(Integration.MUSIC) && settings.subsonic != null
            val intent = Intent(context, CastService::class.java)
            runCatching {
                when {
                    !on -> context.stopService(intent)
                    Build.VERSION.SDK_INT >= 26 -> context.startForegroundService(intent)
                    else -> context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "could not start casting", it) }
        }
    }
}
