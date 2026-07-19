package com.beharsh.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.beharsh.mobile.data.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class SyncService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private var serverRunning = false
    private lateinit var repo: SettingsRepository
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override fun onCreate() {
        super.onCreate()
        repo = SettingsRepository(this)
        showNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!serverRunning) {
            serverRunning = true
            scope.launch { runServer() }
        }
        return START_STICKY
    }

    private suspend fun runServer() = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(9876)
            while (isActive) {
                val client: Socket = serverSocket!!.accept()
                launch { handleClient(client) }
            }
        } catch (_: Exception) {}
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)
            val token = reader.readLine() ?: return
            val s = repo.load()
            if (token != s.pairedIp && token != "BEHARSH_SYNC") { writer.println("DENIED"); return }
            writer.println("OK")
            // Send current settings
            writer.println("SYNC:${json.encodeToString(s)}")
            // Listen for incoming updates
            var line = reader.readLine()
            while (line != null) {
                if (line.startsWith("SYNC:")) {
                    try {
                        val incoming = json.decodeFromString<com.beharsh.mobile.model.Settings>(line.substring(5))
                        repo.save(incoming)
                    } catch (_: Exception) {}
                }
                line = reader.readLine()
            }
        } catch (_: Exception) {
        } finally {
            socket.close()
        }
    }

    private fun showNotification() {
        val ch = "sync_svc"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(ch, "LAN Sync", NotificationManager.IMPORTANCE_LOW))
        }
        startForeground(
            2003,
            NotificationCompat.Builder(this, ch)
                .setContentTitle("BE Harsh — LAN Sync")
                .setContentText("Listening for peer connections on port 9876")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build()
        )
    }

    override fun onDestroy() {
        scope.cancel()
        serverSocket?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) {
            val i = Intent(ctx, SyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, SyncService::class.java))
    }
}
