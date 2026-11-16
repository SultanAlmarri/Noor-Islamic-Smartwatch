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
        if (uri != null) vm.importForAlarm(uri) { cid -> set { it.copy(sound = AlarmSound.CUSTOM, customId = cid) } }
    }
    val leave = rememberUpdatedState(onBack)
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh(); leave.value() }
    val save = {
        vm.saveAlarm(draft.copy(id = if (isNew) 0 else id, enabled = true))
        // (An alarm can only ring with notifications allowed: asked here, where it is wanted.)
        if (ready.canRequestNotifications) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else onBack()
    }

    NoorPage(stringResource(if (isNew) R.string.alarm_new else R.string.alarm_edit), contentPadding, onBack = onBack,
        floating = { SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(contentPadding)) }) {
        GlassCard(Modifier.fillMaxWidth(), padding = 20.dp, corner = 30.dp, highlighted = true) {
            // What it is, in words, and when it rings next: the answer to every change below, at once.
            Text(alarmRelation(draft, f), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
                color = c.textPrimary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            val next = remember(draftText, rings) { vm.nextRing(draft) }
            Text(next?.let { ringWhen(it, rings.zone, f) } ?: stringResource(R.string.alarm_none_week),
                style = MaterialTheme.typography.bodyLarge, color = c.goldText, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))

            // The minutes: − and + (held, they run), and the usual ones at a touch.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                StepButton(Icons.Rounded.Remove, stringResource(R.string.alarm_less), draft.minutes > 1) { set { it.copy(minutes = it.minutes - 1) } }
                Column(Modifier.width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(f.number(draft.minutes), style = MaterialTheme.typography.displayMedium.copy(fontSize = 54.sp, fontWeight = FontWeight.Light), color = c.textPrimary)
                    Text(stringResource(R.string.alarm_minutes_word), style = FootnoteStyle, color = c.textSecondary)
                }
                StepButton(Icons.Rounded.Add, stringResource(R.string.alarm_more), draft.minutes < PrayerAlarm.MAX_MINUTES) { set { it.copy(minutes = it.minutes + 1) } }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                PrayerAlarm.QUICK_MINUTES.forEach { m ->
                    Chip(f.number(m), m == draft.minutes) { set { it.copy(minutes = m) } }
                }
            }
            Spacer(Modifier.height(16.dp))
            Segmented(listOf(false to stringResource(R.string.alarm_before_word), true to stringResource(R.string.alarm_after_word)), draft.after, { a -> set { it.copy(after = a) } })
            Spacer(Modifier.height(12.dp))
            PrayerTiles(draft.prayer, rings.today, rings.zone, f) { p -> set { it.copy(prayer = p) } }
        }

        SettingsSection(stringResource(R.string.alarm_repeat), footer = daysLabel(draft.days, f)) {
            DaysRow(draft.days, f) { d -> if (d != 0) set { it.copy(days = d) } }
        }

        SettingsSection("", footer = stringResource(R.string.alarm_sound_footer)) {
            LabelRow(draft.label) { t -> draftText = draft.copy(label = t.take(PrayerAlarm.MAX_LABEL)).encode() }
            SettingsDivider()
            SettingsItem(Icons.Rounded.MusicNote, stringResource(R.string.alarm_sound), null, onClick = { soundDialog = true }, detail = soundLabel(draft, custom))
            if (draft.sound != AlarmSound.NOTIFICATION) {
                SettingsDivider()
                SettingsSwitch(Icons.Rounded.Vibration, stringResource(R.string.adhan_vibrate), null, draft.vibrate) { on -> set { it.copy(vibrate = on) } }
                SettingsDivider()
                SettingsItem(Icons.Rounded.Snooze, stringResource(R.string.alarm_snooze), null, onClick = { snoozeDialog = true }, detail = minutesLabel(draft.snoozeMinutes, f))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NoorButton(stringResource(R.string.alarm_save), save, Modifier.fillMaxWidth())
            NoorButton(stringResource(R.string.alarm_try, f.number(5)), { vm.tryAlarm(draft.copy(id = if (isNew) PrayerAlarm.MAX else id)) }, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal)
            if (!isNew) NoorButton(stringResource(R.string.alarm_delete), { vm.deleteAlarm(id); onBack() }, Modifier.fillMaxWidth(), kind = ButtonKind.Plain, danger = true)
        }
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

/** A round − or +; held down, it repeats faster and faster. */
@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onStep: () -> Unit) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    val on by rememberUpdatedState(enabled)
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        kotlinx.coroutines.delay(450)
        var wait = 140L
        while (on) { step(); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); kotlinx.coroutines.delay(wait); wait = (wait * 0.86).toLong().coerceAtLeast(45) }
    }
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(c.gold.copy(alpha = if (c.isDark) 0.16f else 0.13f)).alpha(if (enabled) 1f else 0.4f)
            .clickable(interaction, androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); step()
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.gold, modifier = Modifier.size(28.dp)) }
}

@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val c = NoorTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.heightIn(min = 36.dp).clip(shape).background(if (on) c.gold else c.textMuted.copy(alpha = if (c.isDark) 0.18f else 0.12f))
            .clickable(role = Role.RadioButton, onClick = onClick).semantics { selected = on }.padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = if (on) c.onGold else c.textPrimary) }
}

/** The six times of the day, three to a row, each with today's time under its name. */
@Composable
private fun PrayerTiles(selected: Prayer, today: Map<Prayer, java.time.Instant>, zone: ZoneId, f: Formatter, onSelect: (Prayer) -> Unit) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Prayer.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    val on = p == selected
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.weight(1f).clip(shape)
                            .background(if (on) c.gold.copy(alpha = if (c.isDark) 0.18f else 0.14f) else c.glass)
                            .border(if (on) 1.5.dp else 0.75.dp, if (on) c.gold else c.glassEdge.copy(alpha = c.glassEdge.alpha * 0.6f), shape)
                            .clickable(role = Role.RadioButton) { if (!on) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(p) }
                            .semantics { this.selected = on }.padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PrayerIcon(p, 20.dp, if (on) c.gold else c.textSecondary)
                        Text(f.prayerName(p), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                            color = if (on) c.goldText else c.textPrimary, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                        today[p]?.let { Text(f.timeAt(it, zone), style = FootnoteStyle.copy(fontSize = 12.sp), color = c.textSecondary, maxLines = 1) }
                    }
                }
            }
        }
    }
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
 * One alarm in the Adhan tab's list, as in the Clock app: what it is in large type (its label, if it has one), when
 * it rings next and on which days under it, and its switch.
 */
@Composable
internal fun AlarmRow(a: PrayerAlarm, next: PlannedAlarm?, zone: ZoneId, f: Formatter, onOpen: () -> Unit, onToggle: (Boolean) -> Unit) {
    val c = NoorTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClick = onOpen).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(Icons.Rounded.Alarm, if (a.enabled) c.gold else c.textMuted)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(if (a.enabled) 1f else 0.7f)) {
            val relation = alarmRelation(a, f)
            Text(a.label.ifBlank { relation }, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
            val whenText = if (!a.enabled) stringResource(R.string.adhan_off) else next?.let { ringWhen(it, zone, f) } ?: stringResource(R.string.alarm_none_week)
            Text(listOfNotNull(if (a.label.isNotBlank()) relation else null, whenText, if (a.days != PrayerAlert.ALL_DAYS) daysLabel(a.days, f) else null).joinToString(" · "),
                style = FootnoteStyle, color = c.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        NoorSwitch(a.enabled, onToggle)
    }
}
