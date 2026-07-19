package com.beharsh.mobile.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beharsh.mobile.admin.BEHarshDeviceAdminReceiver
import com.beharsh.mobile.model.AppMode
import com.beharsh.mobile.model.ChallengeType
import com.beharsh.mobile.model.Settings
import com.beharsh.mobile.model.StrictCooldown
import com.beharsh.mobile.ui.Accent
import com.beharsh.mobile.ui.TextPrimary
import com.beharsh.mobile.ui.TextSub
import kotlinx.coroutines.delay
import java.security.MessageDigest
import kotlin.random.Random

private val ModeNormalColor = Color(0xFF22C55E)
private val ModeLockColor   = Color(0xFFF59E0B)
private val ModeStrictColor = Color(0xFFEF4444)

private const val STRICT_PHRASE =
    "I choose focus over distraction and will not bypass this protection system today"

@Composable
fun ModesScreen(settings: Settings, onSave: (Settings) -> Unit) {
    val context = LocalContext.current

    // Dialog state machine: PASSWORD → COOLDOWN → CHALLENGE → done
    var showDialog    by remember { mutableStateOf(false) }
    var pendingMode   by remember { mutableStateOf<AppMode?>(null) }
    var dialogStage   by remember { mutableStateOf(DialogStage.PASSWORD) }

    // Password stage
    var pwInput  by remember { mutableStateOf("") }
    var pwError  by remember { mutableStateOf(false) }

    // Challenge stage
    var challengeType by remember { mutableStateOf(ChallengeType.MATH) }
    var mathA by remember { mutableIntStateOf(0) }
    var mathB by remember { mutableIntStateOf(0) }
    var mathC by remember { mutableIntStateOf(0) }
    var mathD by remember { mutableIntStateOf(0) }
    var challengeInput by remember { mutableStateOf("") }
    var challengeError by remember { mutableStateOf(false) }

    fun resetDialog() {
        showDialog = false; pendingMode = null
        dialogStage = DialogStage.PASSWORD
        pwInput = ""; pwError = false
        challengeInput = ""; challengeError = false
    }

    fun openDialog(mode: AppMode) {
        pendingMode = mode
        dialogStage = DialogStage.PASSWORD
        pwInput = ""; pwError = false
        challengeInput = ""; challengeError = false
        showDialog = true
    }

    fun applyMode() {
        val mode = pendingMode ?: return
        val updated = settings.copy(appMode = mode)
        onSave(updated)
        if (mode == AppMode.STRICT) BEHarshDeviceAdminReceiver.applyStrict(context)
        else BEHarshDeviceAdminReceiver.removeStrict(context)
        resetDialog()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "App Mode",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Current mode chip
        val (chipColor, chipLabel) = when (settings.appMode) {
            AppMode.NORMAL -> ModeNormalColor to "Normal — No restrictions"
            AppMode.LOCK   -> ModeLockColor   to "Lock — Password protected"
            AppMode.STRICT -> ModeStrictColor to "Strict — Device Owner active"
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = chipColor.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FiberManualRecord, null, tint = chipColor, modifier = Modifier.size(10.dp))
                Text(chipLabel, fontSize = 13.sp, color = chipColor, fontWeight = FontWeight.SemiBold)
            }
        }

        Text(
            "Switching modes requires a password. Strict Mode additionally requires a cooldown wait and a randomized challenge.",
            fontSize = 12.sp, color = TextSub, lineHeight = 17.sp
        )

        ModeCard(
            icon = Icons.Default.LockOpen, title = "Normal Mode",
            description = "Fully open. No restrictions active.",
            selected = settings.appMode == AppMode.NORMAL, accentColor = ModeNormalColor
        ) { if (settings.appMode != AppMode.NORMAL) openDialog(AppMode.NORMAL) }

        ModeCard(
            icon = Icons.Default.Lock, title = "Lock Mode",
            description = "All setting changes protected by a local password.",
            selected = settings.appMode == AppMode.LOCK, accentColor = ModeLockColor
        ) { if (settings.appMode != AppMode.LOCK) openDialog(AppMode.LOCK) }

        ModeCard(
            icon = Icons.Default.Security, title = "Strict Mode",
            description = "Device Owner active. Blocks Settings, prevents uninstall. Requires cooldown + challenge to exit.",
            selected = settings.appMode == AppMode.STRICT, accentColor = ModeStrictColor
        ) { if (settings.appMode != AppMode.STRICT) openDialog(AppMode.STRICT) }

        // Cooldown config card (only shown in Strict Mode)
        if (settings.appMode == AppMode.STRICT) {
            CooldownConfigCard(settings = settings, onSave = onSave)
        }

        // Device Owner warning
        if (!BEHarshDeviceAdminReceiver.isDeviceOwner(context)) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = ModeLockColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Device Owner Not Set", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF92400E))
                    }
                    Text(
                        "Run once on a fresh device before adding a Google account:",
                        fontSize = 12.sp, color = Color(0xFF92400E), lineHeight = 17.sp
                    )
                    Surface(shape = MaterialTheme.shapes.small, color = Color(0xFF1E293B)) {
                        Text(
                            "adb shell dpm set-device-owner\ncom.beharsh.mobile/.admin.BEHarshDeviceAdminReceiver",
                            fontSize = 11.sp, color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(10.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    // ── Multi-stage dialog ────────────────────────────────────────────────────
    if (showDialog) {
        when (dialogStage) {

            DialogStage.PASSWORD -> {
                val isFirst = settings.lockPasswordHash.isEmpty()
                AlertDialog(
                    onDismissRequest = { resetDialog() },
                    title = { Text(if (isFirst) "Set Password" else "Enter Password", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (isFirst) "Create a password to protect mode changes."
                                else "Enter your password to switch to ${pendingMode?.name?.lowercase()?.replaceFirstChar { it.uppercase() }} Mode.",
                                fontSize = 13.sp, color = TextSub, lineHeight = 18.sp
                            )
                            OutlinedTextField(
                                value = pwInput,
                                onValueChange = { pwInput = it; pwError = false },
                                visualTransformation = PasswordVisualTransformation(),
                                label = { Text("Password") },
                                isError = pwError,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            if (pwError) Text("Incorrect password.", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val hash = sha256(pwInput)
                                val correct = isFirst || hash == settings.lockPasswordHash
                                if (correct) {
                                    // Save hash if first time
                                    if (isFirst) onSave(settings.copy(lockPasswordHash = hash))
                                    // If leaving Strict Mode, require cooldown + challenge
                                    if (settings.appMode == AppMode.STRICT) {
                                        dialogStage = DialogStage.COOLDOWN
                                    } else {
                                        applyMode()
                                    }
                                } else {
                                    pwError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Confirm", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetDialog() }) { Text("Cancel", color = TextSub) }
                    }
                )
            }

            DialogStage.COOLDOWN -> {
                val cooldown = settings.strictCooldown
                val elapsed  = (System.currentTimeMillis() - cooldown.lastAttemptEpochMs) / 1000L
                val remaining = (cooldown.cooldownSeconds - elapsed).coerceAtLeast(0L)

                // Record attempt time on first entry
                LaunchedEffect(Unit) {
                    if (cooldown.lastAttemptEpochMs == 0L ||
                        elapsed >= cooldown.cooldownSeconds
                    ) {
                        onSave(settings.copy(
                            strictCooldown = cooldown.copy(lastAttemptEpochMs = System.currentTimeMillis())
                        ))
                    }
                }

                var secondsLeft by remember { mutableLongStateOf(remaining) }
                LaunchedEffect(Unit) {
                    while (secondsLeft > 0) {
                        delay(1000L)
                        secondsLeft--
                    }
                }

                AlertDialog(
                    onDismissRequest = { resetDialog() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Timer, null, tint = ModeStrictColor, modifier = Modifier.size(22.dp))
                            Text("Strict Mode Cooldown", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Strict Mode requires a mandatory wait before you can exit. This prevents impulsive bypasses.",
                                fontSize = 13.sp, color = TextSub, lineHeight = 18.sp
                            )
                            if (secondsLeft > 0) {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = ModeStrictColor.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            formatCooldown(secondsLeft),
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = ModeStrictColor,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text("remaining", fontSize = 12.sp, color = TextSub)
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { 1f - (secondsLeft.toFloat() / cooldown.cooldownSeconds.toFloat()) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp),
                                    color = ModeStrictColor,
                                    trackColor = ModeStrictColor.copy(alpha = 0.15f)
                                )
                            } else {
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = ModeNormalColor.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, null, tint = ModeNormalColor, modifier = Modifier.size(20.dp))
                                        Text("Cooldown complete. Proceed to challenge.", fontSize = 13.sp, color = ModeNormalColor, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                // Randomize challenge type
                                val ct = when (cooldown.challengeType) {
                                    ChallengeType.MATH   -> ChallengeType.MATH
                                    ChallengeType.PHRASE -> ChallengeType.PHRASE
                                    ChallengeType.RANDOM -> if (Random.nextBoolean()) ChallengeType.MATH else ChallengeType.PHRASE
                                }
                                challengeType = ct
                                if (ct == ChallengeType.MATH) {
                                    mathA = Random.nextInt(5, 20)
                                    mathB = Random.nextInt(2, 12)
                                    mathC = Random.nextInt(1, 15)
                                    mathD = Random.nextInt(1, 10)
                                }
                                challengeInput = ""; challengeError = false
                                dialogStage = DialogStage.CHALLENGE
                            },
                            enabled = secondsLeft == 0L,
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Continue to Challenge", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetDialog() }) { Text("Cancel", color = TextSub) }
                    }
                )
            }

            DialogStage.CHALLENGE -> {
                val expectedAnswer = mathA.toLong() * mathB - mathC + mathD

                AlertDialog(
                    onDismissRequest = { resetDialog() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Psychology, null, tint = Accent, modifier = Modifier.size(22.dp))
                            Text("Unlock Challenge", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (challengeType == ChallengeType.MATH) {
                                Text(
                                    "Solve the equation to confirm you are making a deliberate, conscious decision:",
                                    fontSize = 13.sp, color = TextSub, lineHeight = 18.sp
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = Accent.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "$mathA × $mathB − $mathC + $mathD = ?",
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Accent,
                                        modifier = Modifier.padding(16.dp),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                OutlinedTextField(
                                    value = challengeInput,
                                    onValueChange = { challengeInput = it; challengeError = false },
                                    label = { Text("Answer") },
                                    isError = challengeError,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    )
                                )
                            } else {
                                Text(
                                    "Type this exact phrase to confirm your intent:",
                                    fontSize = 13.sp, color = TextSub, lineHeight = 18.sp
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = Accent.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "\"$STRICT_PHRASE\"",
                                        fontSize = 13.sp,
                                        color = Accent,
                                        modifier = Modifier.padding(14.dp),
                                        lineHeight = 19.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                OutlinedTextField(
                                    value = challengeInput,
                                    onValueChange = { challengeInput = it; challengeError = false },
                                    label = { Text("Type phrase exactly") },
                                    isError = challengeError,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 4
                                )
                            }
                            if (challengeError) {
                                Text(
                                    if (challengeType == ChallengeType.MATH) "Wrong answer. Try again."
                                    else "Phrase doesn't match exactly. Try again.",
                                    color = Color(0xFFEF4444), fontSize = 12.sp
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val passed = if (challengeType == ChallengeType.MATH) {
                                    challengeInput.trim().toLongOrNull() == expectedAnswer
                                } else {
                                    challengeInput.trim() == STRICT_PHRASE
                                }
                                if (passed) {
                                    // Reset cooldown timestamp so next attempt starts fresh
                                    onSave(settings.copy(
                                        strictCooldown = settings.strictCooldown.copy(lastAttemptEpochMs = 0L)
                                    ))
                                    applyMode()
                                } else {
                                    challengeError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent)
                        ) { Text("Unlock", color = Color.White) }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetDialog() }) { Text("Cancel", color = TextSub) }
                    }
                )
            }
        }
    }
}

// ── Cooldown Config Card ──────────────────────────────────────────────────────

@Composable
private fun CooldownConfigCard(settings: Settings, onSave: (Settings) -> Unit) {
    val cooldown = settings.strictCooldown
    var seconds by remember { mutableIntStateOf(cooldown.cooldownSeconds) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFFFFF1F2))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = ModeStrictColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Strict Mode Cooldown", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
            }
            Text(
                "How long to wait before the unlock challenge appears. Longer = harder to bypass.",
                fontSize = 12.sp, color = TextSub, lineHeight = 17.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = seconds.toFloat(),
                    onValueChange = { seconds = it.toInt() },
                    valueRange = 30f..1800f,
                    steps = 35,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = ModeStrictColor, activeTrackColor = ModeStrictColor)
                )
                Text(
                    formatCooldown(seconds.toLong()),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ModeStrictColor,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(56.dp)
                )
            }
            // Challenge type selector
            Text("Challenge type:", fontSize = 12.sp, color = TextSub)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ChallengeType.RANDOM, ChallengeType.MATH, ChallengeType.PHRASE).forEach { ct ->
                    val selected = cooldown.challengeType == ct
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onSave(settings.copy(strictCooldown = cooldown.copy(challengeType = ct)))
                        },
                        label = { Text(ct.name.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ModeStrictColor.copy(alpha = 0.15f),
                            selectedLabelColor = ModeStrictColor
                        )
                    )
                }
            }
            Button(
                onClick = {
                    onSave(settings.copy(strictCooldown = cooldown.copy(cooldownSeconds = seconds)))
                },
                colors = ButtonDefaults.buttonColors(containerColor = ModeStrictColor),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Cooldown Settings", color = Color.White, fontWeight = FontWeight.SemiBold) }
        }
    }
}

// ── Mode Card ─────────────────────────────────────────────────────────────────

@Composable
private fun ModeCard(
    icon: ImageVector, title: String, description: String,
    selected: Boolean, accentColor: Color, onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor else Color.Transparent,
        animationSpec = tween(300), label = "border_$title"
    )
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().border(2.dp, borderColor, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(if (selected) 4.dp else 2.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accentColor, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextPrimary)
                Text(description, fontSize = 12.sp, color = TextSub, lineHeight = 17.sp)
            }
            if (selected) Icon(Icons.Default.CheckCircle, null, tint = accentColor, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private enum class DialogStage { PASSWORD, COOLDOWN, CHALLENGE }

private fun formatCooldown(seconds: Long): String {
    val m = seconds / 60; val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

private fun sha256(input: String): String =
    MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }
