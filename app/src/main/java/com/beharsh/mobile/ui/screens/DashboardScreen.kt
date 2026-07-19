package com.beharsh.mobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beharsh.mobile.model.AppRule
import com.beharsh.mobile.model.DayOfWeek
import com.beharsh.mobile.model.Settings
import com.beharsh.mobile.model.TimeBlock
import com.beharsh.mobile.ui.Accent
import com.beharsh.mobile.ui.TextPrimary
import com.beharsh.mobile.ui.TextSub

@Composable
fun DashboardScreen(settings: Settings, onSave: (Settings) -> Unit) {
    val f = settings.features
    // Which app rule is open in the editor dialog
    var editingRule by remember { mutableStateOf<AppRule?>(null) }

    if (editingRule != null) {
        AppRuleDialog(
            rule = editingRule!!,
            allRules = settings.appRules,
            onDismiss = { editingRule = null },
            onSave = { updated ->
                val newList = settings.appRules.toMutableList()
                val idx = newList.indexOfFirst { it.packageName == updated.packageName }
                if (idx >= 0) newList[idx] = updated else newList.add(updated)
                onSave(settings.copy(appRules = newList))
                editingRule = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Quick Actions",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Adult Filter
        FeatureCard(
            icon = Icons.Default.Shield,
            title = "Adult Filter",
            subtitle = "Routes all DNS through Cloudflare Family (1.1.1.3)",
            checked = f.adultFilter,
            onToggle = { onSave(settings.copy(features = f.copy(adultFilter = it))) }
        )

        // Apps Blocked — expandable with per-app rule list
        ExpandableFeatureCard(
            icon = Icons.Default.Block,
            title = "Apps Blocked",
            subtitle = "Schedule, time limits, and goal-based blocking per app",
            checked = f.appsBlocked,
            onToggle = { onSave(settings.copy(features = f.copy(appsBlocked = it))) }
        ) {
            AppsBlockedPanel(
                settings = settings,
                onSave = onSave,
                onEditRule = { editingRule = it }
            )
        }

        // Sites Blocked
        FeatureCard(
            icon = Icons.Default.Language,
            title = "Sites Blocked",
            subtitle = "Block distracting websites via DNS sinkhole",
            checked = f.sitesBlocked,
            onToggle = { onSave(settings.copy(features = f.copy(sitesBlocked = it))) }
        )

        // Keywords
        FeatureCard(
            icon = Icons.Default.TextFields,
            title = "Keywords Filter",
            subtitle = "Block content containing flagged keywords",
            checked = f.keywordsBlocked,
            onToggle = { onSave(settings.copy(features = f.copy(keywordsBlocked = it))) }
        )

        // YouTube Engine
        ExpandableFeatureCard(
            icon = Icons.Default.PlayCircle,
            title = "YouTube Engine",
            subtitle = "Granular YouTube content control via Accessibility",
            checked = f.youtubeEngine,
            onToggle = { onSave(settings.copy(features = f.copy(youtubeEngine = it))) }
        ) {
            SubCheckbox(
                label = "Block Shorts",
                subtitle = "Exits Shorts reel player immediately",
                checked = f.ytBlockShorts,
                onToggle = { onSave(settings.copy(features = f.copy(ytBlockShorts = it))) }
            )
            SubCheckbox(
                label = "Block Homepage Feed",
                subtitle = "Exits YouTube browse feed — search still works",
                checked = f.ytBlockFeed,
                onToggle = { onSave(settings.copy(features = f.copy(ytBlockFeed = it))) }
            )
        }

        // WhatsApp Filter
        ExpandableFeatureCard(
            icon = Icons.Default.Chat,
            title = "WhatsApp Filter",
            subtitle = "Block specific WhatsApp sections",
            checked = f.waBlockStatus || f.waBlockChannels,
            onToggle = {
                onSave(settings.copy(features = f.copy(waBlockStatus = it, waBlockChannels = it)))
            }
        ) {
            SubCheckbox(
                label = "Block Status Tab",
                subtitle = "Exits Status tab on open",
                checked = f.waBlockStatus,
                onToggle = { onSave(settings.copy(features = f.copy(waBlockStatus = it))) }
            )
            SubCheckbox(
                label = "Block Channels Tab",
                subtitle = "Exits Channels tab on open",
                checked = f.waBlockChannels,
                onToggle = { onSave(settings.copy(features = f.copy(waBlockChannels = it))) }
            )
        }
    }
}

// ── Apps Blocked Panel ────────────────────────────────────────────────────────

@Composable
private fun AppsBlockedPanel(
    settings: Settings,
    onSave: (Settings) -> Unit,
    onEditRule: (AppRule) -> Unit
) {
    var input by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Add a package name, then tap the rule icon to configure schedule/limits.",
            fontSize = 12.sp, color = TextSub
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("com.instagram.android", fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
            )
            FilledIconButton(
                onClick = {
                    val pkg = input.trim()
                    if (pkg.isNotEmpty()) {
                        val exists = settings.appRules.any { it.packageName == pkg }
                        if (!exists) {
                            val newRules = settings.appRules + AppRule(packageName = pkg, label = pkg)
                            onSave(settings.copy(appRules = newRules))
                        }
                        input = ""
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        }

        if (settings.appRules.isEmpty()) {
            Text("No app rules yet.", fontSize = 12.sp, color = TextSub)
        } else {
            settings.appRules.forEach { rule ->
                AppRuleRow(
                    rule = rule,
                    onEdit = { onEditRule(rule) },
                    onRemove = {
                        onSave(settings.copy(appRules = settings.appRules.filter { it.packageName != rule.packageName }))
                    }
                )
            }
        }
    }
}

@Composable
private fun AppRuleRow(rule: AppRule, onEdit: () -> Unit, onRemove: () -> Unit) {
    val activeConditions = listOf(rule.scheduleEnabled, rule.dailyLimitEnabled, rule.goalLimitEnabled).count { it }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(rule.label.ifEmpty { rule.packageName }, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                Text(
                    if (activeConditions == 0) "No conditions — tap ✎ to configure"
                    else "$activeConditions condition(s) active",
                    fontSize = 11.sp,
                    color = if (activeConditions > 0) Accent else TextSub
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Edit rule", tint = Accent, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextSub, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ── App Rule Dialog ───────────────────────────────────────────────────────────

@Composable
private fun AppRuleDialog(
    rule: AppRule,
    allRules: List<AppRule>,
    onDismiss: () -> Unit,
    onSave: (AppRule) -> Unit
) {
    var label            by remember { mutableStateOf(rule.label.ifEmpty { rule.packageName }) }
    var schedEnabled     by remember { mutableStateOf(rule.scheduleEnabled) }
    var schedDays        by remember { mutableStateOf(rule.scheduleDays.toMutableSet()) }
    var schedBlocks      by remember { mutableStateOf(rule.scheduleBlocks.toMutableList()) }
    var limitEnabled     by remember { mutableStateOf(rule.dailyLimitEnabled) }
    var limitMinutes     by remember { mutableIntStateOf(rule.dailyLimitMinutes) }
    var goalEnabled      by remember { mutableStateOf(rule.goalLimitEnabled) }
    var goalMinutes      by remember { mutableIntStateOf(rule.goalMinutes) }
    var goalPkgs         by remember { mutableStateOf(rule.goalPackages.toMutableList()) }
    var goalPkgInput     by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Configure Rule", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Package label
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Package: ${rule.packageName}",
                    fontSize = 11.sp, color = TextSub
                )

                HorizontalDivider()

                // ── Schedule Condition ────────────────────────────────────────
                RuleSection(
                    title = "Schedule",
                    subtitle = "Block on specific days and time windows",
                    icon = Icons.Default.CalendarToday,
                    enabled = schedEnabled,
                    onToggle = { schedEnabled = it }
                ) {
                    // Day picker
                    Text("Active days:", fontSize = 12.sp, color = TextSub)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            val sel = schedDays.contains(day)
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    schedDays = schedDays.toMutableSet().also {
                                        if (sel) it.remove(day) else it.add(day)
                                    }
                                },
                                label = { Text(day.short, fontSize = 11.sp) },
                                modifier = Modifier.size(36.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent.copy(alpha = 0.15f),
                                    selectedLabelColor = Accent
                                )
                            )
                        }
                    }

                    // Time blocks
                    Text("Time blocks (minutes since midnight):", fontSize = 12.sp, color = TextSub)
                    schedBlocks.forEachIndexed { idx, block ->
                        TimeBlockRow(
                            block = block,
                            onUpdate = { updated ->
                                schedBlocks = schedBlocks.toMutableList().also { it[idx] = updated }
                            },
                            onRemove = {
                                schedBlocks = schedBlocks.toMutableList().also { it.removeAt(idx) }
                            }
                        )
                    }
                    TextButton(
                        onClick = { schedBlocks = schedBlocks.toMutableList().also { it.add(TimeBlock(540, 1020)) } }
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp), tint = Accent)
                        Spacer(Modifier.width(4.dp))
                        Text("Add time block", fontSize = 12.sp, color = Accent)
                    }
                }

                HorizontalDivider()

                // ── Daily Limit Condition ─────────────────────────────────────
                RuleSection(
                    title = "Daily Usage Limit",
                    subtitle = "Block after N minutes of use today",
                    icon = Icons.Default.HourglassBottom,
                    enabled = limitEnabled,
                    onToggle = { limitEnabled = it }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Slider(
                            value = limitMinutes.toFloat(),
                            onValueChange = { limitMinutes = it.toInt() },
                            valueRange = 5f..240f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                        )
                        Text(
                            "${limitMinutes}m",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Accent,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                    Text(
                        "App will be blocked after $limitMinutes minutes of use today.",
                        fontSize = 11.sp, color = TextSub
                    )
                }

                HorizontalDivider()

                // ── Goal-Based Condition ──────────────────────────────────────
                RuleSection(
                    title = "Goal-Based Block",
                    subtitle = "Block until productive apps are used for N minutes",
                    icon = Icons.Default.TrackChanges,
                    enabled = goalEnabled,
                    onToggle = { goalEnabled = it }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Slider(
                            value = goalMinutes.toFloat(),
                            onValueChange = { goalMinutes = it.toInt() },
                            valueRange = 10f..240f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                        )
                        Text(
                            "${goalMinutes}m",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Accent,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                    Text("Productive apps to track:", fontSize = 12.sp, color = TextSub)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = goalPkgInput,
                            onValueChange = { goalPkgInput = it },
                            placeholder = { Text("com.duolingo", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        FilledIconButton(
                            onClick = {
                                val p = goalPkgInput.trim()
                                if (p.isNotEmpty() && !goalPkgs.contains(p)) {
                                    goalPkgs = goalPkgs.toMutableList().also { it.add(p) }
                                    goalPkgInput = ""
                                }
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent)
                        ) { Icon(Icons.Default.Add, null, tint = Color.White) }
                    }
                    goalPkgs.forEach { pkg ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = Accent, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(pkg, fontSize = 12.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = { goalPkgs = goalPkgs.toMutableList().also { it.remove(pkg) } },
                                modifier = Modifier.size(28.dp)
                            ) { Icon(Icons.Default.Close, null, tint = TextSub, modifier = Modifier.size(14.dp)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        rule.copy(
                            label            = label,
                            scheduleEnabled  = schedEnabled,
                            scheduleDays     = schedDays.toList(),
                            scheduleBlocks   = schedBlocks.toList(),
                            dailyLimitEnabled = limitEnabled,
                            dailyLimitMinutes = limitMinutes,
                            goalLimitEnabled = goalEnabled,
                            goalMinutes      = goalMinutes,
                            goalPackages     = goalPkgs.toList()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) { Text("Save Rule", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSub) }
        }
    )
}

@Composable
private fun RuleSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, fontSize = 11.sp, color = TextSub)
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.25f))
            )
        }
        AnimatedVisibility(
            visible = enabled,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit  = fadeOut(tween(130)) + shrinkVertically(tween(130))
        ) {
            Column(
                modifier = Modifier.padding(start = 26.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content
            )
        }
    }
}

@Composable
private fun TimeBlockRow(block: TimeBlock, onUpdate: (TimeBlock) -> Unit, onRemove: () -> Unit) {
    var startText by remember { mutableStateOf(block.startMinute.toString()) }
    var endText   by remember { mutableStateOf(block.endMinute.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = startText,
            onValueChange = {
                startText = it
                val v = it.toIntOrNull()?.coerceIn(0, 1439) ?: return@OutlinedTextField
                onUpdate(block.copy(startMinute = v))
            },
            label = { Text("Start", fontSize = 10.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        Text("–", color = TextSub)
        OutlinedTextField(
            value = endText,
            onValueChange = {
                endText = it
                val v = it.toIntOrNull()?.coerceIn(0, 1439) ?: return@OutlinedTextField
                onUpdate(block.copy(endMinute = v))
            },
            label = { Text("End", fontSize = 10.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
        )
        Text(
            "${block.startMinute / 60}:%02d – ${block.endMinute / 60}:%02d".format(
                block.startMinute % 60, block.endMinute % 60
            ),
            fontSize = 10.sp, color = TextSub, modifier = Modifier.width(60.dp)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, null, tint = TextSub, modifier = Modifier.size(14.dp))
        }
    }
}

// ── Shared Card Components ────────────────────────────────────────────────────

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                Text(subtitle, fontSize = 12.sp, color = TextSub, lineHeight = 16.sp)
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Accent,
                    checkedTrackColor = Accent.copy(alpha = 0.25f)
                )
            )
        }
    }
}

@Composable
private fun ExpandableFeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    subContent: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                    Text(subtitle, fontSize = 12.sp, color = TextSub, lineHeight = 16.sp)
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        onToggle(it)
                        if (!it) expanded = false
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Accent,
                        checkedTrackColor = Accent.copy(alpha = 0.25f)
                    )
                )
            }

            AnimatedVisibility(
                visible = checked,
                enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                exit  = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                Column {
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (expanded) "Hide options" else "Show options",
                            color = Accent, fontSize = 12.sp
                        )
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                        exit  = fadeOut(tween(150)) + shrinkVertically(tween(150))
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            content = subContent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubCheckbox(
    label: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = Accent)
        )
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = TextSub)
        }
    }
}
