package com.beharsh.mobile.ui.screens

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beharsh.mobile.model.AppRule
import com.beharsh.mobile.model.Settings
import com.beharsh.mobile.ui.Accent
import com.beharsh.mobile.ui.Canvas
import com.beharsh.mobile.ui.TextPrimary
import com.beharsh.mobile.ui.TextSub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppUsageRow(val label: String, val packageName: String, val minutes: Long)

@Composable
fun AnalyticsScreen(settings: Settings) {
    val context = LocalContext.current
    var rows         by remember { mutableStateOf<List<AppUsageRow>>(emptyList()) }
    var totalMinutes by remember { mutableLongStateOf(0L) }
    var loading      by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val data = withContext(Dispatchers.IO) { queryUsage(context) }
        rows         = data
        totalMinutes = data.sumOf { it.minutes }
        loading      = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Analytics",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.BarChart,
                label = "Screen Time",
                value = if (loading) "—" else formatMinutes(totalMinutes)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.PhoneAndroid,
                label = "Apps Used",
                value = if (loading) "—" else rows.size.toString()
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        } else if (rows.isEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No usage data available", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                    Text(
                        "Go to Settings → Apps → Special App Access → Usage Access and grant permission to BE Harsh.",
                        fontSize = 13.sp, color = TextSub, lineHeight = 18.sp
                    )
                }
            }
        } else {
            val maxMinutes = rows.maxOfOrNull { it.minutes }?.coerceAtLeast(1L) ?: 1L
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.packageName }) { row ->
                    val rule = settings.appRules.find { it.packageName == row.packageName }
                    AppUsageCard(row = row, maxMinutes = maxMinutes, rule = rule)
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String) {
    ElevatedCard(modifier = modifier, shape = MaterialTheme.shapes.large, elevation = CardDefaults.elevatedCardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(label, fontSize = 12.sp, color = TextSub)
        }
    }
}

@Composable
private fun AppUsageCard(row: AppUsageRow, maxMinutes: Long, rule: AppRule?) {
    val relativeProgress = (row.minutes.toFloat() / maxMinutes.toFloat()).coerceIn(0f, 1f)
    val animatedRelative by animateFloatAsState(relativeProgress, tween(600), label = "rel_${row.packageName}")

    // Daily limit progress (0..1), only shown when limit is configured
    val limitProgress = if (rule != null && rule.dailyLimitEnabled && rule.dailyLimitMinutes > 0) {
        (row.minutes.toFloat() / rule.dailyLimitMinutes.toFloat()).coerceIn(0f, 1f)
    } else null
    val animatedLimit by animateFloatAsState(limitProgress ?: 0f, tween(600), label = "lim_${row.packageName}")

    val limitColor = when {
        limitProgress == null -> Accent
        limitProgress >= 1f   -> Color(0xFFEF4444)
        limitProgress >= 0.8f -> Color(0xFFF59E0B)
        else                  -> Accent
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.elevatedCardElevation(1.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.label,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatMinutes(row.minutes),
                    fontSize = 13.sp, color = limitColor, fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(6.dp))

            // Relative usage bar (always shown)
            LinearProgressIndicator(
                progress = { animatedRelative },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Accent.copy(alpha = 0.4f),
                trackColor = Canvas,
                strokeCap = StrokeCap.Round
            )

            // Daily limit bar (only when rule has limit)
            if (limitProgress != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.HourglassBottom, null, tint = limitColor, modifier = Modifier.size(12.dp))
                    LinearProgressIndicator(
                        progress = { animatedLimit },
                        modifier = Modifier.weight(1f).height(5.dp),
                        color = limitColor,
                        trackColor = Canvas,
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        "${row.minutes}/${rule!!.dailyLimitMinutes}m",
                        fontSize = 10.sp, color = limitColor
                    )
                }
                if (limitProgress >= 1f) {
                    Text(
                        "Limit reached — app is blocked",
                        fontSize = 10.sp,
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

private fun queryUsage(context: Context): List<AppUsageRow> {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val pm  = context.packageManager
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
    }
    val stats = usm.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY, cal.timeInMillis, System.currentTimeMillis()
    ) ?: return emptyList()

    return stats
        .filter { it.totalTimeInForeground > 60_000L && it.packageName != context.packageName }
        .map { stat ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) { stat.packageName }
            AppUsageRow(label, stat.packageName, stat.totalTimeInForeground / 60_000L)
        }
        .sortedByDescending { it.minutes }
        .take(20)
}

private fun formatMinutes(mins: Long): String {
    val h = mins / 60; val m = mins % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
