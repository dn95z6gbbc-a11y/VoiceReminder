package com.example.voicereminder

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.voicereminder.alarm.AlarmScheduler
import com.example.voicereminder.assistant.ReminderVoiceInteractionService
import com.example.voicereminder.alarm.ReminderNotifications
import com.example.voicereminder.data.Reminder
import com.example.voicereminder.data.ReminderStore
import com.example.voicereminder.voice.VoiceCaptureActivity
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var refreshToken by mutableIntStateOf(0)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshToken++
    }

    private val activityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshToken++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderNotifications.ensureChannel(this)

        setContent {
            MaterialTheme {
                ReminderAppScreen(
                    refreshToken = refreshToken,
                    onRefresh = { refreshToken++ },
                    onRequestPermissions = { requestRuntimePermissions() },
                    onRequestExactAlarm = { requestExactAlarmPermission() },
                    onRequestAssistant = { openAssistantSettings() },
                    onOpenNotificationSettings = { openNotificationSettings() },
                    onStartVoice = {
                        activityLauncher.launch(Intent(this, VoiceCaptureActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshToken++
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName")
            )
            activityLauncher.launch(intent)
        }
    }

    private fun openAssistantSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val manager = getSystemService(RoleManager::class.java)
            if (manager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
                activityLauncher.launch(
                    manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                )
                return
            }
        }

        val candidates = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        candidates.firstOrNull { it.resolveActivity(packageManager) != null }
            ?.let { activityLauncher.launch(it) }
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        activityLauncher.launch(intent)
    }
}

@Composable
private fun ReminderAppScreen(
    refreshToken: Int,
    onRefresh: () -> Unit,
    onRequestPermissions: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestAssistant: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onStartVoice: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { ReminderStore(context) }
    val scheduler = remember { AlarmScheduler(context) }

    var tab by remember { mutableIntStateOf(0) }
    var upcoming by remember { mutableStateOf(emptyList<Reminder>()) }
    var completed by remember { mutableStateOf(emptyList<Reminder>()) }
    var showManual by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<Reminder?>(null) }

    fun reload() {
        upcoming = store.active()
        completed = store.completed()
    }

    LaunchedEffect(refreshToken, tab) {
        reload()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onStartVoice) {
                Text("🎙", style = MaterialTheme.typography.headlineMedium)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Голосовые напоминания",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Скажи: «завтра в 12 позвонить врачу»",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Версия 0.4 — виджет и повторы",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(14.dp))

                SetupCard(
                    context = context,
                    scheduler = scheduler,
                    onRequestPermissions = onRequestPermissions,
                    onRequestExactAlarm = onRequestExactAlarm,
                    onRequestAssistant = onRequestAssistant,
                    onOpenNotificationSettings = onOpenNotificationSettings
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onStartVoice) { Text("🎙 Голосом") }
                    OutlinedButton(onClick = { showManual = true }) { Text("+ Вручную") }
                }
            }

            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Предстоящие (${upcoming.size})") }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Выполненные (${completed.size})") }
                )
            }

            val list = if (tab == 0) upcoming else completed
            if (list.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(if (tab == 0) "Нет активных напоминаний" else "История пока пустая")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { reminder ->
                        ReminderRow(
                            reminder = reminder,
                            active = tab == 0,
                            onDone = {
                                store.markDone(reminder.id)
                                scheduler.cancel(reminder.id)
                                ReminderNotifications.cancel(context, reminder.id)
                                reload()
                                onRefresh()
                            },
                            onEdit = {
                                editingReminder = reminder
                            },
                            onDelete = {
                                scheduler.cancel(reminder.id)
                                ReminderNotifications.cancel(context, reminder.id)
                                store.delete(reminder.id)
                                reload()
                                onRefresh()
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showManual) {
        ManualReminderDialog(
            initialTitle = "",
            initialWhen = ZonedDateTime.now().plusHours(1).withSecond(0).withNano(0),
            initialRepeat = Reminder.REPEAT_NONE,
            onDismiss = { showManual = false },
            onSave = { title, epoch, repeatRule ->
                val reminder = store.insert(title, epoch, repeatRule)
                scheduler.schedule(reminder)
                showManual = false
                reload()
                onRefresh()
            }
        )
    }

    editingReminder?.let { current ->
        ManualReminderDialog(
            initialTitle = current.title,
            initialWhen = Instant.ofEpochMilli(current.scheduledAt).atZone(ZoneId.systemDefault()),
            initialRepeat = current.repeatRule,
            onDismiss = { editingReminder = null },
            onSave = { title, epoch, repeatRule ->
                scheduler.cancel(current.id)
                ReminderNotifications.cancel(context, current.id)
                store.update(current.id, title, epoch, repeatRule)
                store.get(current.id)?.let { scheduler.schedule(it) }
                editingReminder = null
                reload()
                onRefresh()
            }
        )
    }
}

@Composable
private fun SetupCard(
    context: Context,
    scheduler: AlarmScheduler,
    onRequestPermissions: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestAssistant: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    val micGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    val exactGranted = scheduler.canScheduleExact()

    val assistantGranted = VoiceInteractionService.isActiveService(
        context,
        ComponentName(context, ReminderVoiceInteractionService::class.java)
    )

    if (micGranted && notificationsGranted && exactGranted && assistantGranted) return

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Первичная настройка", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("${check(micGranted)} Микрофон")
            Text("${check(notificationsGranted)} Уведомления")
            Text("${check(exactGranted)} Точные напоминания")
            Text("${check(assistantGranted)} Цифровой ассистент")
            Spacer(Modifier.height(10.dp))

            if (!micGranted || !notificationsGranted) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestPermissions
                ) { Text("Разрешить микрофон и уведомления") }
            }
            if (!exactGranted) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestExactAlarm
                ) { Text("Разрешить точные напоминания") }
            }
            if (!assistantGranted) {
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onRequestAssistant
                ) { Text("Выбрать VoiceReminder ассистентом") }
            }
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenNotificationSettings
            ) { Text("Настройки всплывающих уведомлений") }

            Text(
                "На POCO/HyperOS после установки также включи автозапуск приложения и режим «Без ограничений» для батареи.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun check(value: Boolean) = if (value) "✓" else "○"

@Composable
private fun ReminderRow(
    reminder: Reminder,
    active: Boolean,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(reminder.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            formatEpoch(if (active) reminder.scheduledAt else reminder.completedAt ?: reminder.scheduledAt),
            style = MaterialTheme.typography.bodyMedium
        )
        if (reminder.isRepeating) {
            Spacer(Modifier.height(3.dp))
            Text(repeatLabel(reminder.repeatRule), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (active && !reminder.isRepeating) {
                FilledTonalButton(onClick = onDone) { Text("Готово") }
            }
            if (active) {
                TextButton(onClick = onEdit) { Text("Изменить") }
            }
            TextButton(onClick = onDelete) { Text("Удалить") }
        }
    }
}

@Composable
private fun ManualReminderDialog(
    initialTitle: String,
    initialWhen: ZonedDateTime,
    initialRepeat: String,
    onDismiss: () -> Unit,
    onSave: (String, Long, String) -> Unit
) {
    val context = LocalContext.current
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    var whenAt by remember(initialWhen) { mutableStateOf(initialWhen) }
    var repeatRule by remember(initialRepeat) { mutableStateOf(initialRepeat) }

    fun pickDate() {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                whenAt = whenAt.withYear(year).withMonth(month + 1).withDayOfMonth(day)
            },
            whenAt.year,
            whenAt.monthValue - 1,
            whenAt.dayOfMonth
        ).show()
    }

    fun pickTime() {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                whenAt = whenAt.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            },
            whenAt.hour,
            whenAt.minute,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Напоминание") },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Что напомнить") }
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate() }) {
                        Text(whenAt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                    }
                    OutlinedButton(onClick = { pickTime() }) {
                        Text(whenAt.format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Повтор", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                RepeatOption(
                    text = "Без повтора",
                    selected = repeatRule == Reminder.REPEAT_NONE,
                    onClick = { repeatRule = Reminder.REPEAT_NONE }
                )
                RepeatOption(
                    text = "Каждый час",
                    selected = repeatRule == Reminder.REPEAT_HOURLY,
                    onClick = { repeatRule = Reminder.REPEAT_HOURLY }
                )
                RepeatOption(
                    text = "Каждый день",
                    selected = repeatRule == Reminder.REPEAT_DAILY,
                    onClick = { repeatRule = Reminder.REPEAT_DAILY }
                )
                RepeatOption(
                    text = "Каждую неделю",
                    selected = repeatRule == Reminder.REPEAT_WEEKLY,
                    onClick = { repeatRule = Reminder.REPEAT_WEEKLY }
                )
                RepeatOption(
                    text = "Каждый месяц",
                    selected = repeatRule == Reminder.REPEAT_MONTHLY,
                    onClick = { repeatRule = Reminder.REPEAT_MONTHLY }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && whenAt.isAfter(ZonedDateTime.now()),
                onClick = { onSave(title.trim(), whenAt.toInstant().toEpochMilli(), repeatRule) }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun RepeatOption(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Text(if (selected) "✓ $text" else text)
    }
}

private fun repeatLabel(rule: String): String = when (rule) {
    Reminder.REPEAT_HOURLY -> "↻ Каждый час"
    Reminder.REPEAT_DAILY -> "↻ Каждый день"
    Reminder.REPEAT_WEEKLY -> "↻ Каждую неделю"
    Reminder.REPEAT_MONTHLY -> "↻ Каждый месяц"
    else -> ""
}

private fun formatEpoch(epoch: Long): String {
    val formatter = DateTimeFormatter.ofPattern("EEE, d MMMM • HH:mm", Locale("ru"))
    return Instant.ofEpochMilli(epoch)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
