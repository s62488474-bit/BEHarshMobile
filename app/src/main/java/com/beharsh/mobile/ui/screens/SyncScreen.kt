package com.beharsh.mobile.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beharsh.mobile.model.Settings
import com.beharsh.mobile.receiver.PlanningAlarmReceiver
import com.beharsh.mobile.service.SyncService
import com.beharsh.mobile.ui.Accent
import com.beharsh.mobile.ui.TextPrimary
import com.beharsh.mobile.ui.TextSub
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SyncScreen(settings: Settings, onSave: (Settings) -> Unit) {
    val context  = LocalContext.current
    val localIp  = remember { getLocalIp(context) }
    val syncPayload = "$localIp:9876"
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var alarmHour   by remember { mutableIntStateOf(settings.alarmHour) }
    var alarmMinute by remember { mutableIntStateOf(settings.alarmMinute) }
    var pairedIp    by remember { mutableStateOf(settings.pairedIp) }

    LaunchedEffect(syncPayload) {
        qrBitmap = withContext(Dispatchers.Default) { generateQr(syncPayload) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Sync & Planning",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // ── QR Sync Panel ─────────────────────────────────────────────────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.elevatedCardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wifi, null, tint = Accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Local Wi-Fi Sync", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Text("Same network required", fontSize = 12.sp, color = TextSub)
                    }
                    Switch(
                        checked = settings.syncEnabled,
                        onCheckedChange = { enabled ->
                            onSave(settings.copy(syncEnabled = enabled))
                            if (enabled) SyncService.start(context) else SyncService.stop(context)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Accent,
                            checkedTrackColor = Accent.copy(alpha = 0.25f)
                        )
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Text(
                    "Scan this QR code from another device on the same Wi-Fi to sync settings.",
                    fontSize = 12.sp,
                    color = TextSub,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Sync QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(48.dp))
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        syncPayload,
                        fontSize = 12.sp,
                        color = TextSub,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }

                // Paired device IP
                OutlinedTextField(
                    value = pairedIp,
                    onValueChange = { pairedIp = it },
                    label = { Text("Paired Device IP") },
                    placeholder = { Text("192.168.1.x", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.DeviceHub, null, tint = TextSub, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { onSave(settings.copy(pairedIp = pairedIp.trim())) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Save Paired IP", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Planning Alarm Panel ──────────────────────────────────────────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            elevation = CardDefaults.elevatedCardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, null, tint = Accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Daily Planning Alarm", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                        Text("Fires a notification to log tomorrow's tasks", fontSize = 12.sp, color = TextSub)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = alarmHour.toString(),
                        onValueChange = { alarmHour = it.toIntOrNull()?.coerceIn(0, 23) ?: alarmHour },
                        label = { Text("Hour (0–23)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = alarmMinute.toString(),
                        onValueChange = { alarmMinute = it.toIntOrNull()?.coerceIn(0, 59) ?: alarmMinute },
                        label = { Text("Minute (0–59)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Button(
                    onClick = {
                        val updated = settings.copy(alarmHour = alarmHour, alarmMinute = alarmMinute)
                        onSave(updated)
                        PlanningAlarmReceiver.schedule(context, alarmHour, alarmMinute)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Alarm, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Set Alarm", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                TextButton(
                    onClick = { PlanningAlarmReceiver.cancel(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel Alarm", color = TextSub)
                }
            }
        }
    }
}

private fun getLocalIp(context: Context): String {
    return try {
        @Suppress("DEPRECATION")
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        Formatter.formatIpAddress(wm.connectionInfo.ipAddress).takeIf { it != "0.0.0.0" }
            ?: "192.168.x.x"
    } catch (_: Exception) { "192.168.x.x" }
}

private fun generateQr(content: String): Bitmap {
    val writer = QRCodeWriter()
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512)
    val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
    for (x in 0 until 512) for (y in 0 until 512) {
        bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    return bmp
}
