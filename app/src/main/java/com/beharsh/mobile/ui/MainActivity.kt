package com.beharsh.mobile.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.beharsh.mobile.service.EnforcementService
import com.beharsh.mobile.ui.screens.AnalyticsScreen
import com.beharsh.mobile.ui.screens.DashboardScreen
import com.beharsh.mobile.ui.screens.ModesScreen
import com.beharsh.mobile.ui.screens.SyncScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // Runtime notification permission launcher (Android 13+)
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — non-blocking */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EnforcementService.start(this)

        // Request POST_NOTIFICATIONS on Android 13+ — non-blocking, alarm still works without it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            BEHarshTheme {
                val settings by vm.settings.collectAsState()

                var hasUsage       by remember { mutableStateOf(checkUsageAccess()) }
                var hasOverlay     by remember { mutableStateOf(checkOverlay()) }
                var hasAccessibility by remember { mutableStateOf(checkAccessibility()) }

                val lifecycle = LocalLifecycleOwner.current.lifecycle
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasUsage        = checkUsageAccess()
                            hasOverlay      = checkOverlay()
                            hasAccessibility = checkAccessibility()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                if (!hasUsage || !hasOverlay || !hasAccessibility) {
                    PermissionGate(
                        needsUsage        = !hasUsage,
                        needsOverlay      = !hasOverlay,
                        needsAccessibility = !hasAccessibility
                    )
                } else {
                    MainScaffold(settings = settings, onSave = { vm.save(it) })
                }
            }
        }
    }

    private fun checkUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName
            )
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkOverlay(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this)
        else true

    private fun checkAccessibility(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "${packageName}/${packageName}.service.BEHarshAccessibilityService"
        return enabledServices.split(":")
            .any { it.equals(target, ignoreCase = true) }
    }
}

// ── Permission Gate ───────────────────────────────────────────────────────────

@Composable
private fun PermissionGate(
    needsUsage: Boolean,
    needsOverlay: Boolean,
    needsAccessibility: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "BE Harsh",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Grant the permissions below to activate enforcement.",
                fontSize = 14.sp,
                color = TextSub,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(32.dp))

            if (needsUsage) {
                PermissionButton(
                    icon = Icons.Default.BarChart,
                    label = "Grant Usage Access",
                    description = "Required to detect foreground apps"
                ) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                Spacer(Modifier.height(12.dp))
            }
            if (needsOverlay) {
                PermissionButton(
                    icon = Icons.Default.Layers,
                    label = "Grant Overlay Permission",
                    description = "Required to show blocking overlays"
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (needsAccessibility) {
                PermissionButton(
                    icon = Icons.Default.Accessibility,
                    label = "Enable Accessibility Service",
                    description = "Required for YouTube & WhatsApp enforcement"
                ) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            }
        }
    }
}

@Composable
private fun PermissionButton(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextPrimary)
                Text(description, fontSize = 12.sp, color = TextSub)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextSub, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Navigation ────────────────────────────────────────────────────────────────

sealed class NavTab(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : NavTab("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Modes     : NavTab("modes",     "Modes",     Icons.Default.Security)
    object Analytics : NavTab("analytics", "Analytics", Icons.Default.BarChart)
    object Sync      : NavTab("sync",      "Sync",      Icons.Default.Sync)
}

@Composable
private fun MainScaffold(
    settings: com.beharsh.mobile.model.Settings,
    onSave: (com.beharsh.mobile.model.Settings) -> Unit
) {
    val tabs = listOf(NavTab.Dashboard, NavTab.Modes, NavTab.Analytics, NavTab.Sync)
    var selected by remember { mutableStateOf<NavTab>(NavTab.Dashboard) }

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = selected == tab,
                        onClick  = { selected = tab },
                        icon     = { Icon(tab.icon, contentDescription = tab.label) },
                        label    = { Text(tab.label, fontSize = 11.sp) },
                        colors   = NavigationBarItemDefaults.colors(
                            selectedIconColor   = Accent,
                            selectedTextColor   = Accent,
                            indicatorColor      = Accent.copy(alpha = 0.12f),
                            unselectedIconColor = TextSub,
                            unselectedTextColor = TextSub
                        )
                    )
                }
            }
        }
    ) { padding ->
        Crossfade(
            targetState = selected,
            animationSpec = tween(220),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "tab_crossfade"
        ) { tab ->
            when (tab) {
                NavTab.Dashboard -> DashboardScreen(settings = settings, onSave = onSave)
                NavTab.Modes     -> ModesScreen(settings = settings, onSave = onSave)
                NavTab.Analytics -> AnalyticsScreen(settings = settings)
                NavTab.Sync      -> SyncScreen(settings = settings, onSave = onSave)
            }
        }
    }
}
