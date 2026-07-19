package com.beharsh.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beharsh.mobile.data.SettingsRepository
import com.beharsh.mobile.model.PlanningTask
import com.beharsh.mobile.receiver.PlanningAlarmReceiver
import java.text.SimpleDateFormat
import java.util.*

class PlanningActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = SettingsRepository(this)
        setContent {
            BEHarshTheme {
                PlanningScreen(
                    repo = repo,
                    onDone = {
                        val s = repo.load()
                        PlanningAlarmReceiver.schedule(this, s.alarmHour, s.alarmMinute)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun PlanningScreen(repo: SettingsRepository, onDone: () -> Unit) {
    var settings   by remember { mutableStateOf(repo.load()) }
    var taskInput  by remember { mutableStateOf("") }
    val timeFmt    = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val doneCount  = settings.planningTasks.count { it.done }
    val totalCount = settings.planningTasks.size

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Plan Tomorrow", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (totalCount > 0) {
                            Text(
                                "$doneCount / $totalCount completed",
                                fontSize = 12.sp,
                                color = TextSub
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas),
                actions = {
                    if (totalCount > 0 && doneCount == totalCount) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF22C55E),
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Log your focus intervals for tomorrow. Tap a task to mark it done.",
                fontSize = 13.sp,
                color = TextSub,
                lineHeight = 18.sp
            )

            // ── Add task row ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = taskInput,
                    onValueChange = { taskInput = it },
                    label = { Text("e.g. Study Math 9–11am") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                FilledIconButton(
                    onClick = {
                        val trimmed = taskInput.trim()
                        if (trimmed.isNotEmpty()) {
                            val task = PlanningTask(
                                id      = UUID.randomUUID().toString(),
                                label   = trimmed,
                                epochMs = System.currentTimeMillis()
                            )
                            val updated = settings.copy(planningTasks = settings.planningTasks + task)
                            repo.save(updated)
                            settings  = updated
                            taskInput = ""
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add task", tint = Color.White)
                }
            }

            // ── Task list ─────────────────────────────────────────────────────
            if (settings.planningTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.EventNote,
                            contentDescription = null,
                            tint = TextSub.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "No tasks yet.\nAdd your first focus interval above.",
                            fontSize = 14.sp,
                            color = TextSub,
                            lineHeight = 20.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(settings.planningTasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            timeLabel = timeFmt.format(Date(task.epochMs)),
                            onToggleDone = {
                                val updated = settings.copy(
                                    planningTasks = settings.planningTasks.map {
                                        if (it.id == task.id) it.copy(done = !it.done) else it
                                    }
                                )
                                repo.save(updated)
                                settings = updated
                            },
                            onDelete = {
                                val updated = settings.copy(
                                    planningTasks = settings.planningTasks.filter { it.id != task.id }
                                )
                                repo.save(updated)
                                settings = updated
                            }
                        )
                    }
                }
            }

            // ── Done button ───────────────────────────────────────────────────
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Done — Schedule Tomorrow", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: PlanningTask,
    timeLabel: String,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (task.done) Color(0xFFF0FDF4) else Color.White,
        animationSpec = tween(300),
        label = "task_bg_${task.id}"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Done toggle checkbox
            Checkbox(
                checked = task.done,
                onCheckedChange = { onToggleDone() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF22C55E),
                    uncheckedColor = TextSub
                )
            )
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.label,
                    fontSize = 14.sp,
                    color = if (task.done) TextSub else TextPrimary,
                    fontWeight = if (task.done) FontWeight.Normal else FontWeight.Medium,
                    textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                )
                Text(
                    "Added $timeLabel",
                    fontSize = 11.sp,
                    color = TextSub.copy(alpha = 0.7f)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
