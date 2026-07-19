package com.beharsh.mobile.service

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.beharsh.mobile.data.SettingsRepository
import kotlin.random.Random

class VideoGateService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var videoId: String? = null
    private var channelName: String? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        videoId = intent?.getStringExtra("video_id")
        channelName = intent?.getStringExtra("channel_name")
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        if (overlayView == null) showGate()
        return START_NOT_STICKY
    }

    private fun showGate() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE
        ).apply { gravity = Gravity.TOP or Gravity.START }

        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@VideoGateService)
            setViewTreeSavedStateRegistryOwner(this@VideoGateService)
            setContent { VideoGateContent() }
        }
        windowManager?.addView(overlayView, params)
    }

    @Composable
    private fun VideoGateContent() {
        val useMath = remember { Random.nextBoolean() }
        var dismissed by remember { mutableStateOf(false) }

        if (dismissed) {
            LaunchedEffect(Unit) { stopSelf() }
            return
        }

        Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("VIDEO GATE", color = Color(0xFF2563EB), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Channel: ${channelName ?: "Unknown"}\nVideo: ${videoId ?: "Unknown"}",
                    color = Color(0xFFCBD5E1), fontSize = 12.sp
                )
                Text("Complete the challenge to whitelist this content:", color = Color.White, fontSize = 13.sp)

                if (useMath) MathChallenge(onPass = { whitelistAndDismiss(); dismissed = true })
                else IntentChallenge(onPass = { whitelistAndDismiss(); dismissed = true })

                TextButton(onClick = { dismissed = true }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        }
    }

    @Composable
    private fun MathChallenge(onPass: () -> Unit) {
        val a = remember { Random.nextInt(5, 20) }
        val b = remember { Random.nextInt(2, 12) }
        val c = remember { Random.nextInt(1, 15) }
        val d = remember { Random.nextInt(1, 10) }
        val answer = remember { a.toLong() * b - c + d }
        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }

        Text("Solve: $a × $b − $c + $d = ?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = false },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF2563EB), unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Answer", color = Color.Gray) }
        )
        if (error) Text("Wrong answer. Try again.", color = Color.Red, fontSize = 12.sp)
        Button(
            onClick = { if (input.trim().toLongOrNull() == answer) onPass() else error = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            modifier = Modifier.fillMaxWidth()
        ) { Text("SUBMIT", color = Color.White) }
    }

    @Composable
    private fun IntentChallenge(onPass: () -> Unit) {
        val phrase = "I intentionally choose to watch this content for a productive reason today"
        var input by remember { mutableStateOf("") }
        var error by remember { mutableStateOf(false) }

        Text("Type this phrase exactly:", color = Color.White, fontSize = 13.sp)
        Text("\"$phrase\"", color = Color(0xFFCBD5E1), fontSize = 12.sp)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = false },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF2563EB), unfocusedBorderColor = Color.Gray
            ),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Type here…", color = Color.Gray) }
        )
        if (error) Text("Incorrect. Try again.", color = Color.Red, fontSize = 12.sp)
        Button(
            onClick = { if (input.trim() == phrase) onPass() else error = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            modifier = Modifier.fillMaxWidth()
        ) { Text("WHITELIST", color = Color.White) }
    }

    private fun whitelistAndDismiss() {
        val repo = SettingsRepository(this)
        val s = repo.load()
        val updatedChannels = if (channelName != null && !s.whitelistedChannels.contains(channelName))
            s.whitelistedChannels + channelName!! else s.whitelistedChannels
        val updatedVideos = if (videoId != null && !s.whitelistedVideos.contains(videoId))
            s.whitelistedVideos + videoId!! else s.whitelistedVideos
        repo.save(s.copy(whitelistedChannels = updatedChannels, whitelistedVideos = updatedVideos))
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        overlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
