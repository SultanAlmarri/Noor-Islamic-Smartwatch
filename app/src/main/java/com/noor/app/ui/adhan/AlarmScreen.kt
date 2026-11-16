package com.noor.app.ui.adhan

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noor.alerts.CustomSound
import com.noor.alerts.CustomSounds
import com.noor.app.R
import com.noor.app.ui.components.ButtonKind
import com.noor.app.ui.components.GlassCard
import com.noor.app.ui.components.NoorButton
import com.noor.app.ui.components.NoorPage
import com.noor.app.ui.components.PrayerIcon
import com.noor.app.ui.components.Wheel
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.settings.ChoiceDialog
import com.noor.app.ui.settings.FootnoteStyle
import com.noor.app.ui.settings.IconTile
import com.noor.app.ui.settings.NoorSwitch
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsItem
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.settings.SettingsSwitch
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlarmSound
import com.noor.prayer.alerts.PlannedAlarm
import com.noor.prayer.alerts.PrayerAlarm
import com.noor.prayer.alerts.PrayerAlert
import java.time.LocalDate
import java.time.ZoneId

/**
 * One alarm (0.17), new ([id] 0) or as set, laid out as the sentence it makes: "25 minutes / before / Fajr", and under
 * it at once when that rings next. Then the days, and how it rings. Nothing is kept until Save; Back leaves it as it was.
 */
@Composable
fun AlarmScreen(contentPadding: PaddingValues, id: Int, onBack: () -> Unit, vm: AdhanViewModel = viewModel()) {
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val custom by vm.custom.collectAsStateWithLifecycle()
    val rings by vm.rings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val ready by vm.readiness.collectAsStateWithLifecycle()
    val ar = isArabicUi()
    val f = Formatter(ar, s.numerals, s.timeFormat)
    val c = NoorTheme.colors
    val saved = s.alerts.alarms.firstOrNull { it.id == id }
    val isNew = saved == null
    // (The draft as text, so it survives the screen being recreated; a new one carries the last id until it is saved.)
    var draftText by rememberSaveable { mutableStateOf((saved ?: PrayerAlarm(PrayerAlarm.MAX, Prayer.FAJR, 15, after = false)).encode()) }
    val draft = PrayerAlarm.decode(draftText) ?: return
    fun set(change: (PrayerAlarm) -> PrayerAlarm) { draftText = change(draft).normalized().encode() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }
    var soundDialog by rememberSaveable { mutableStateOf(false) }
    var snoozeDialog by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // (Applied to the draft as it is when the file has been added, not as it was when the picker opened.)
        if (uri != null) vm.importForAlarm(uri) { cid -> PrayerAlarm.decode(draftText)?.let { draftText = it.copy(sound = AlarmSound.CUSTOM, customId = cid).encode() } }
    }
    val leave = rememberUpdatedState(onBack)
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh(); leave.value() }
    // (Hours and minutes as the wheels show them: 0 h 0 min is allowed on the wheels, not saved.)
    var hours by rememberSaveable { mutableStateOf(draft.minutes / 60) }
    var mins by rememberSaveable { mutableStateOf(draft.minutes % 60) }
    val total = hours * 60 + mins
    fun minutesOf(h: Int, m: Int) { if (h * 60 + m > 0) set { it.copy(minutes = h * 60 + m) } }
    val save = {
        vm.saveAlarm(draft.copy(id = if (isNew) 0 else id, enabled = true))
        // (An alarm can only ring with notifications allowed: asked here, where it is wanted.)
        if (ready.canRequestNotifications) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else onBack()
    }

    NoorPage(stringResource(if (isNew) R.string.alarm_new else R.string.alarm_edit), contentPadding, onBack = onBack,
        floating = {
            // Cancel and Save, always in reach at the bottom, as in the Clock app.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = contentPadding.calculateBottomPadding())) {
                SnackbarHost(snackbar)
                Row(Modifier.fillMaxWidth().background(com.noor.app.ui.theme.LocalBackdrop.current.top.copy(alpha = 0.94f))
                    .padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NoorButton(stringResource(R.string.alarm_cancel), onBack, Modifier.weight(1f), kind = ButtonKind.Tonal)
                    NoorButton(stringResource(R.string.alarm_save), save, Modifier.weight(1f), enabled = total > 0)
                }
            }
        }) {
        GlassCard(Modifier.fillMaxWidth(), padding = 16.dp, corner = 30.dp, highlighted = true) {
            // When it rings, large, as a clock shows its alarm: the answer to every turn of the wheels below, at once.
            val next = remember(draftText, rings) { vm.nextRing(draft) }
            Text(next?.let { f.timeAt(it.at, rings.zone) } ?: "–",
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 46.sp, fontWeight = FontWeight.Light),
                color = c.textPrimary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Text(if (total == 0) stringResource(R.string.alarm_at_least) else next?.let { ringDay(it, rings.zone, f) + " · " + alarmRelation(draft, f) } ?: stringResource(R.string.alarm_none_week),
                style = MaterialTheme.typography.bodyLarge, color = if (total == 0) c.danger else c.goldText,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))

            // The wheels read as the sentence: [0 h] [25 min] [before] [Fajr].
            val head = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp)
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.alarm_hours), style = head, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(0.8f))
                Text(stringResource(R.string.alarm_minutes_head), style = head, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.weight(1.1f))
                Text(stringResource(R.string.alarm_prayer), style = head, color = c.textMuted, textAlign = TextAlign.Center, modifier = Modifier.weight(1.5f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Wheel((0..PrayerAlarm.MAX_MINUTES / 60).map { f.number(it) }, hours, { h -> hours = h; minutesOf(h, mins) }, stringResource(R.string.alarm_hours), Modifier.weight(0.8f))
                Wheel((0..59).map { f.number(it) }, mins, { m -> mins = m; minutesOf(hours, m) }, stringResource(R.string.alarm_minutes_head), Modifier.weight(1f), loop = true)
                Wheel(listOf(stringResource(R.string.alarm_before_word), stringResource(R.string.alarm_after_word)), if (draft.after) 1 else 0,
                    { i -> set { it.copy(after = i == 1) } }, stringResource(R.string.alarm_before_word) + " / " + stringResource(R.string.alarm_after_word), Modifier.weight(1.1f))
                Wheel(Prayer.entries.map { f.prayerName(it) }, draft.prayer.ordinal, { i -> set { it.copy(prayer = Prayer.entries[i]) } },
                    stringResource(R.string.alarm_prayer), Modifier.weight(1.5f))
            }
        }

        // Its name, as the Clock app's alarms have ("Wake up for Fajr"), shown in large type in the list.
        SettingsSection("") {
            LabelRow(draft.label) { t -> draftText = draft.copy(label = t.take(PrayerAlarm.MAX_LABEL)).encode() }
        }

        SettingsSection(stringResource(R.string.alarm_repeat), footer = daysLabel(draft.days, f)) {
            DaysRow(draft.days, f) { d -> if (d != 0) set { it.copy(days = d) } }
        }

        SettingsSection("", footer = stringResource(R.string.alarm_sound_footer)) {
            SettingsItem(Icons.Rounded.MusicNote, stringResource(R.string.alarm_sound), null, onClick = { soundDialog = true }, detail = soundLabel(draft, custom))
            if (draft.sound != AlarmSound.NOTIFICATION) {
                SettingsDivider()
                SettingsSwitch(Icons.Rounded.Vibration, stringResource(R.string.adhan_vibrate), null, draft.vibrate) { on -> set { it.copy(vibrate = on) } }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Snooze, stringResource(R.string.alarm_snooze), null, onClick = { snoozeDialog = true }, detail = minutesLabel(draft.snoozeMinutes, f))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NoorButton(stringResource(R.string.alarm_try, f.number(5)), { vm.tryAlarm(draft.copy(id = if (isNew) PrayerAlarm.MAX else id)) }, Modifier.fillMaxWidth(),
                kind = ButtonKind.Tonal, enabled = total > 0)
            if (!isNew) NoorButton(stringResource(R.string.alarm_delete), { vm.deleteAlarm(id); onBack() }, Modifier.fillMaxWidth(), kind = ButtonKind.Plain, danger = true)
        }
        // (Room for the bar of Cancel and Save.)
        Spacer(Modifier.height(64.dp))
    }

    if (soundDialog) {
        val options = buildList {
            add("phone" to stringResource(R.string.alarm_sound_phone))
            add("notify" to stringResource(R.string.alarm_sound_notification))
            custom.forEach { add(it.id to it.name) }
            add("add" to stringResource(R.string.alarm_sound_add))
        }
        val selected = when (draft.sound) { AlarmSound.PHONE_ALARM -> "phone"; AlarmSound.NOTIFICATION -> "notify"; AlarmSound.CUSTOM -> draft.customId ?: "phone" }
        ChoiceDialog(stringResource(R.string.alarm_sound), options, selected, onSelect = { k ->
            when (k) {
                "phone" -> set { it.copy(sound = AlarmSound.PHONE_ALARM, customId = null) }
                "notify" -> set { it.copy(sound = AlarmSound.NOTIFICATION, customId = null) }
                "add" -> picker.launch(CustomSounds.PICKER_TYPES)
                else -> set { it.copy(sound = AlarmSound.CUSTOM, customId = k) }
            }
        }, onDismiss = { soundDialog = false })
    }
    if (snoozeDialog) ChoiceDialog(stringResource(R.string.alarm_snooze), PrayerAlarm.SNOOZE_CHOICES.map { it to minutesLabel(it, f) }, draft.snoozeMinutes,
        onSelect = { m -> set { it.copy(snoozeMinutes = m) } }, onDismiss = { snoozeDialog = false })
}

/** "25 min before Fajr", "10 min after Dhuhr" (Jumu'ah's name is not used: the alarm is every week's). */
@Composable
internal fun alarmRelation(a: PrayerAlarm, f: Formatter): String =
    stringResource(if (a.after) R.string.alarm_after else R.string.alarm_before,
        androidx.compose.ui.res.pluralStringResource(R.plurals.alarm_minutes, a.minutes, f.number(a.minutes)), f.prayerName(a.prayer))

/** "Today at 4:27 AM", "Tomorrow at 4:27 AM", "Friday at 4:27 AM". */
@Composable
internal fun ringWhen(p: PlannedAlarm, zone: ZoneId, f: Formatter): String {
    val day = p.at.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val t = f.timeAt(p.at, zone)
    return when (day) {
        today -> stringResource(R.string.alarm_rings_today, t)
        today.plusDays(1) -> stringResource(R.string.alarm_rings_tomorrow, t)
        else -> stringResource(R.string.alarm_rings_on, f.weekday(day), t)
    }
}

/** "Today", "Tomorrow", or the day's name, for when an alarm rings next. */
@Composable
internal fun ringDay(p: PlannedAlarm, zone: ZoneId, f: Formatter): String {
    val day = p.at.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> stringResource(R.string.alarm_day_today)
        today.plusDays(1) -> stringResource(R.string.alarm_day_tomorrow)
        else -> f.weekday(day)
    }
}

/** "Every day", or the days in the week's order, three or more in a row as a span ("Mon–Fri", "Sat, Sun"). */
@Composable
internal fun daysLabel(days: Int, f: Formatter): String {
    if (days == PrayerAlert.ALL_DAYS) return stringResource(R.string.days_every)
    val first = java.time.temporal.WeekFields.of(f.locale).firstDayOfWeek
    val week = (0 until 7).map { first.plus(it.toLong()) }
    val runs = mutableListOf<MutableList<java.time.DayOfWeek>>()
    week.forEachIndexed { i, d ->
        if (days and (1 shl (d.value - 1)) == 0) return@forEachIndexed
        if (runs.isNotEmpty() && i > 0 && runs.last().last() == week[i - 1]) runs.last().add(d) else runs.add(mutableListOf(d))
    }
    return runs.flatMap { r -> if (r.size >= 3) listOf(f.weekdayShort(r.first()) + "–" + f.weekdayShort(r.last())) else r.map { f.weekdayShort(it) } }
        .joinToString(if (f.arabic) "، " else ", ")
}

@Composable
private fun soundLabel(a: PrayerAlarm, custom: List<CustomSound>): String = when (a.sound) {
    AlarmSound.PHONE_ALARM -> stringResource(R.string.alarm_sound_phone)
    AlarmSound.NOTIFICATION -> stringResource(R.string.alarm_sound_notification)
    AlarmSound.CUSTOM -> custom.firstOrNull { it.id == a.customId }?.name ?: stringResource(R.string.alarm_sound_phone)
}




/** The alarm's name, typed in the row itself (optional). */
@Composable
private fun LabelRow(label: String, onChange: (String) -> Unit) {
    val c = NoorTheme.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(Icons.Rounded.Label)
        Spacer(Modifier.width(14.dp))
        val hint = stringResource(R.string.alarm_label_hint)
        BasicTextField(label, onChange, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.gold),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.weight(1f).semantics { contentDescription = hint },
            decorationBox = { inner -> Box { if (label.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = c.textMuted); inner() } })
    }
}

/**
 * The alarms (0.17.1), as the Clock app lists its own: the next one first, in large type; then each alarm as a card
 * with its name, the time it rings next in large type, what it is and its days, and its switch; and Add alarm. With
 * none yet, what an alarm is for, and the button.
 */
@Composable
internal fun AlarmsList(a: com.noor.prayer.alerts.AlertSettings, rings: Rings, f: Formatter, onOpen: (Int) -> Unit, onToggle: (Int, Boolean) -> Unit) {
    val c = NoorTheme.colors
    val full = a.nextAlarmId() == null
    if (a.alarms.isEmpty()) {
        GlassCard(Modifier.fillMaxWidth(), corner = 30.dp, highlighted = true) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.noor.app.ui.components.Medallion(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Rounded.Alarm))
                Text(stringResource(R.string.alarms_none), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, textAlign = TextAlign.Center)
                Text(stringResource(R.string.alarms_empty_footer), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
                NoorButton(stringResource(R.string.alarm_add), { onOpen(0) }, Modifier.fillMaxWidth().padding(top = 6.dp), icon = Icons.Rounded.Add)
            }
        }
        return
    }
    // The next alarm, as the Clock app says it at the top ("Alarm in 9 hours 12 minutes").
    rings.next.values.filter { p -> a.alarms.any { it.id == p.alarm.id && it.enabled } }.minByOrNull { it.at }?.let { n ->
        val left = java.time.Duration.between(java.time.Instant.now(), n.at).coerceAtLeast(java.time.Duration.ZERO)
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            val (h, m) = f.hoursMinutes(left)
            Text(if (h > 0) stringResource(R.string.alarm_next_in_hm, f.number(h.toInt()), f.number(m.toInt())) else stringResource(R.string.alarm_next_in_m, f.number(m.toInt())), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(ringWhen(n, rings.zone, f) + " · " + n.alarm.label.ifBlank { alarmRelation(n.alarm, f) }, style = FootnoteStyle, color = c.textSecondary)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        a.alarms.forEach { al -> AlarmCard(al, rings.next[al.id], rings.zone, f, onOpen = { onOpen(al.id) }, onToggle = { on -> onToggle(al.id, on) }) }
    }
    NoorButton(stringResource(if (full) R.string.alarm_full else R.string.alarm_add), { onOpen(0) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, enabled = !full)
    Text(stringResource(R.string.alarms_footer), style = FootnoteStyle, color = c.textMuted, modifier = Modifier.padding(horizontal = 16.dp))
}

/** One alarm as a card: its name, the time it rings next (large), what it is and its days, and its switch. */
@Composable
private fun AlarmCard(a: PrayerAlarm, next: PlannedAlarm?, zone: ZoneId, f: Formatter, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = NoorTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(c.glass).clickable(role = Role.Button, onClick = onOpen)
            .padding(start = 18.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(if (a.enabled) 1f else 0.55f)) {
            if (a.label.isNotBlank()) Text(a.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = c.goldText, maxLines = 1)
            Text(next?.let { f.timeAt(it.at, zone) } ?: "–", style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontWeight = FontWeight.Light),
                color = c.textPrimary, maxLines = 1)
            Text(alarmRelation(a, f) + " · " + daysLabel(a.days, f), style = FootnoteStyle, color = c.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        NoorSwitch(a.enabled, onToggle)
    }
}
