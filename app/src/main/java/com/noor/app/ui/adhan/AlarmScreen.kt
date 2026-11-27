package com.noor.app.ui.adhan

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import com.noor.app.ui.components.Wheel
import com.noor.app.ui.components.isArabicUi
import com.noor.app.ui.components.limitedGrowth
import com.noor.app.ui.components.withTick
import com.noor.app.ui.settings.ChoiceDialog
import com.noor.app.ui.settings.FootnoteStyle
import com.noor.app.ui.settings.NoorSwitch
import com.noor.app.ui.settings.SettingsDivider
import com.noor.app.ui.settings.SettingsItem
import com.noor.app.ui.settings.SettingsSection
import com.noor.app.ui.theme.NoorTheme
import com.noor.app.util.Formatter
import com.noor.prayer.Prayer
import com.noor.prayer.alerts.AlarmSound
import com.noor.prayer.alerts.PlannedAlarm
import com.noor.prayer.alerts.PrayerAlarm
import com.noor.prayer.alerts.PrayerAlert
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * One alarm, new ([id] 0) or as set, laid out as Samsung's Clock sets an alarm (0.26; the owner, who uses a Galaxy
 * phone every day: "it should look very similar to the Samsung clock app ... Even when you set an alarm, it should look
 * like it"). Top to bottom: a large card with when it rings next, very large, and under it the wheels that read as the
 * sentence it makes, "0 h  25 min  before  Fajr" (where Samsung's card holds the hour and the minute); the days as seven
 * round letters under a line that says them; the alarm's name; Sound, Vibration and Snooze as Samsung's rows, each
 * switch at its end; then, quietly, Try it in 5 seconds and Delete. Cancel and Save are the two words at the foot of the
 * page, as in Samsung's editor (0.25 had put them in the bar at the top, as on an iPhone). Nothing is kept until Save;
 * Android's Back is Cancel.
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
    // (Snooze's switch turned off and on again gives back the interval it had.)
    var lastSnooze by rememberSaveable { mutableStateOf(draft.snoozeMinutes.takeIf { it > 0 } ?: 10) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // (Applied to the draft as it is when the file has been added, not as it was when the picker opened.)
        if (uri != null) vm.importForAlarm(uri) { cid -> PrayerAlarm.decode(draftText)?.let { draftText = it.copy(sound = AlarmSound.CUSTOM, customId = cid).encode() } }
    }
    val leave = rememberUpdatedState(onBack)
    val notifPermission = com.noor.app.ui.components.rememberNotificationRequest { vm.refresh(); leave.value() }
    // (Hours and minutes as the wheels show them: 0 h 0 min is allowed on the wheels, not saved.)
    var hours by rememberSaveable { mutableStateOf(draft.minutes / 60) }
    var mins by rememberSaveable { mutableStateOf(draft.minutes % 60) }
    val total = hours * 60 + mins
    fun minutesOf(h: Int, m: Int) { if (h * 60 + m > 0) set { it.copy(minutes = h * 60 + m) } }
    val save = {
        vm.saveAlarm(draft.copy(id = if (isNew) 0 else id, enabled = true))
        // (An alarm can only ring with notifications allowed: asked here, where it is wanted.)
        if (ready.canRequestNotifications) notifPermission() else onBack()
    }

    // 0.18: the sound is a page of its own (the phone's sounds, Noor's sounds full or short, the Adhan, the user's own).
    androidx.activity.compose.BackHandler(enabled = soundDialog) { vm.stopPreview(); soundDialog = false }
    if (soundDialog) { AlarmSoundPage(contentPadding, draft, custom, vm, ar, f, onBack = { vm.stopPreview(); soundDialog = false },
        onPick = { x -> set { x } }, onAdd = { picker.launch(CustomSounds.PICKER_TYPES) }); return }

    // The foot of the page holds Cancel and Save (over the navigation bar, or over the tab bar where the page has one):
    // the page's own end is lifted clear of them, so its last row can always be scrolled out from under them.
    val footBottom = (contentPadding.calculateBottomPadding() - com.noor.app.TabBarClear).coerceAtLeast(0.dp)
    val page = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + FootBarHeight + 12.dp)
    // (The slim bar with the page's name and nothing to touch in it: the ways out are at the foot.)
    NoorPage(stringResource(if (isNew) R.string.alarm_new else R.string.alarm_edit), page, spacing = 14.dp, barStart = { },
        floating = {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                NoorSnackbarHost(snackbar, Modifier.align(Alignment.CenterHorizontally))
                FootBar(footBottom) {
                    FootAction(stringResource(R.string.alarm_cancel), onBack, Modifier.weight(1f))
                    FootAction(stringResource(R.string.alarm_save), save, Modifier.weight(1f), strong = true, enabled = total > 0)
                }
            }
        }) {
        GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 30.dp, highlighted = true) {
            Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // When it rings, very large, where Samsung's card shows the time being set: the answer to every turn
                // of the wheels below, at once. (Worked out off the main thread, so the wheels turn smoothly; the
                // answer before stays until the new one.)
                val next by androidx.compose.runtime.produceState(remember { vm.nextRingIfReady(draft) }, draftText, rings) { value = vm.nextRing(draft) }
                val big = MaterialTheme.typography.displayLarge.merge(com.noor.app.ui.theme.Tabular)
                    .copy(fontSize = 58.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp)
                next?.let { ClockText(f.clockAt(it.at, rings.zone), big, c.textPrimary) } ?: Text("–", style = big, color = c.textPrimary)
                FactsLine(if (total == 0) listOf(stringResource(R.string.alarm_at_least)) else next?.let { listOf(ringDay(it, rings.zone, f), alarmRelation(draft, f)) } ?: listOf(stringResource(R.string.alarm_none_week)),
                    MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp), if (total == 0) c.danger else c.textSecondary,
                    Modifier.fillMaxWidth().padding(top = 2.dp, start = 8.dp, end = 8.dp), center = true)
                Spacer(Modifier.height(12.dp))
                AlarmWheels(draft, hours, mins, f,
                    onHours = { h -> hours = h; minutesOf(h, mins) }, onMinutes = { m -> mins = m; minutesOf(hours, m) },
                    onAfter = { after -> set { it.copy(after = after) } }, onPrayer = { p -> set { it.copy(prayer = p) } })
            }
        }

        // The days, as Samsung's: a line that says them, and the week's seven letters, each a round toggle.
        GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 26.dp) {
            WeekDays(draft.days, f) { d -> if (d != 0) set { it.copy(days = d) } }
        }

        // Its name ("Wake up for Fajr"), shown over its time in the list.
        GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 26.dp) {
            NameField(draft.label) { t -> draftText = draft.copy(label = t.take(PrayerAlarm.MAX_LABEL)).encode() }
        }

        // How it rings, as Samsung's rows: what is chosen in the accent under each name; a switch at the end.
        GlassCard(Modifier.fillMaxWidth(), padding = 0.dp, corner = 26.dp) {
            OptionRow(stringResource(R.string.alarm_sound), soundLabel(draft, custom, vm, ar), gold = true, onOpen = { soundDialog = true })
            if (draft.sound != AlarmSound.NOTIFICATION) {
                OptionDivider()
                OptionRow(stringResource(R.string.alarm_vibration), null, checked = draft.vibrate, onCheck = { on -> set { it.copy(vibrate = on) } })
                OptionDivider()
                val snooze = draft.snoozeMinutes
                OptionRow(stringResource(R.string.alarm_snooze),
                    if (snooze > 0) pluralStringResource(R.plurals.alarm_minutes_long, snooze, f.number(snooze)) else stringResource(R.string.adhan_off), gold = snooze > 0,
                    onOpen = { snoozeDialog = true }, checked = snooze > 0, onCheck = { on -> set { it.copy(snoozeMinutes = if (on) lastSnooze else 0) } })
            }
        }

        // What is used now and then, quietly, under the rest.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NoorButton(stringResource(R.string.r026_test_in, f.number(5)), { vm.tryAlarm(draft.copy(id = if (isNew) PrayerAlarm.MAX else id)) },
                kind = ButtonKind.Plain, compact = true, enabled = total > 0)
            if (!isNew) NoorButton(stringResource(R.string.alarm_delete), { vm.deleteAlarm(id); onBack() }, kind = ButtonKind.Plain, compact = true, danger = true)
        }
    }

    if (snoozeDialog) ChoiceDialog(stringResource(R.string.alarm_snooze),
        PrayerAlarm.SNOOZE_CHOICES.filter { it > 0 }.map { it to pluralStringResource(R.plurals.alarm_minutes_long, it, f.number(it)) }, draft.snoozeMinutes,
        onSelect = { m -> lastSnooze = m; set { it.copy(snoozeMinutes = m) } }, onDismiss = { snoozeDialog = false })
}

/**
 * Cancel and Save at the foot of the editor, as Samsung's Clock ends its editor: each half the width, in the accent, Save
 * the heavier. (0.26 review: on an opaque bar in the page's own sky, [FootBar]; it was a 94% wash with a fade above, and
 * the rows under it showed through.)
 */
@Composable
private fun FootAction(text: String, onClick: () -> Unit, modifier: Modifier, strong: Boolean = false, enabled: Boolean = true) {
    val c = NoorTheme.colors
    Box(
        modifier.heightIn(min = 48.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, role = Role.Button, onClick = withTick(onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp * limitedGrowth(1.2f), fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium),
            color = c.goldText.copy(alpha = if (enabled) 1f else 0.38f), maxLines = 1, softWrap = false)
    }
}

/**
 * The four wheels that read as the alarm's sentence, [0 h] [25 min] [before] [Fajr], three rows in sight as Samsung's
 * Clock turns its hour and minute: the chosen row large and bright, its neighbours small and faint, no band behind
 * them. The units stay beside the chosen number while the numbers turn. Mirrored in Arabic, where the sentence reads
 * from the right ("٢٥ د قبل الفجر"); the digits keep their own order.
 */
@Composable
private fun AlarmWheels(
    a: PrayerAlarm, hours: Int, mins: Int, f: Formatter,
    onHours: (Int) -> Unit, onMinutes: (Int) -> Unit, onAfter: (Boolean) -> Unit, onPrayer: (Prayer) -> Unit,
) {
    // (Each as wide as what it holds on a 360 dp phone with text at 1.3: a digit and "h"; two digits and "min";
    // "before"; the longest prayer's name, "Maghrib".)
    val w = listOf(0.62f, 1.0f, 1.05f, 1.33f)
    val row = 52.dp
    val numbers = 30f
    val words = 21f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Wheel((0..PrayerAlarm.MAX_MINUTES / 60).map { f.number(it) }, hours, onHours, stringResource(R.string.alarm_hours),
            Modifier.weight(w[0]), band = false, size = numbers, awaySize = 21f, weight = FontWeight.Normal, unit = stringResource(R.string.alarm_unit_hours),
            visible = 3, rowHeight = row, unitSize = 14f, numberShare = 0.6f)
        Wheel((0..59).map { f.number(it) }, mins, onMinutes, stringResource(R.string.alarm_minutes_head),
            Modifier.weight(w[1]), loop = true, band = false, size = numbers, awaySize = 21f, weight = FontWeight.Normal, unit = stringResource(R.string.alarm_unit_minutes),
            visible = 3, rowHeight = row, unitSize = 14f, numberShare = 0.6f)
        Wheel(listOf(stringResource(R.string.alarm_before_word), stringResource(R.string.alarm_after_word)), if (a.after) 1 else 0,
            { i -> onAfter(i == 1) }, stringResource(R.string.alarm_before_word) + " / " + stringResource(R.string.alarm_after_word),
            Modifier.weight(w[2]), band = false, size = words, awaySize = 16f, weight = FontWeight.Medium, visible = 3, rowHeight = row)
        Wheel(Prayer.entries.map { f.prayerName(it) }, a.prayer.ordinal, { i -> onPrayer(Prayer.entries[i]) },
            stringResource(R.string.alarm_prayer), Modifier.weight(w[3]), band = false, size = words, awaySize = 16f, weight = FontWeight.Medium, visible = 3, rowHeight = row)
    }
}

/** The alarm's name, typed in its card (optional), as Samsung's "Alarm name". */
@Composable
private fun NameField(label: String, onChange: (String) -> Unit) {
    val c = NoorTheme.colors
    val hint = stringResource(R.string.alarm_label_hint)
    val style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp)
    // (0.26 review: with large text a long name ran into the card's edge, cut in a letter. It now wraps onto a second
    // line inside the card's margins, as long names do in Samsung's editor; on the list's cards it ends in "…".)
    Box(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp), contentAlignment = Alignment.CenterStart) {
        // (Up to 40 letters, wrapped over two lines at most rather than cut at the edge; a line break typed or pasted
        // is a space, so the name stays one line of words.)
        BasicTextField(label, { t -> onChange(t.replace('\n', ' ')) }, singleLine = false, maxLines = 2, textStyle = style.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.gold),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = hint },
            decorationBox = { inner -> Box { if (label.isEmpty()) Text(hint, style = style, color = c.textMuted); inner() } })
    }
}

/**
 * One of Samsung's rows of an alarm (Sound, Vibration, Snooze): its name, what is chosen under it, and at its end a
 * switch. With [onOpen] and a switch the row is in two parts, as Samsung's: the words open the choice, a thin upright
 * line, the switch turns it on or off. With a switch alone the whole row is the switch; with [onOpen] alone, a chevron.
 */
@Composable
private fun OptionRow(
    title: String, detail: String?, gold: Boolean = false, onOpen: (() -> Unit)? = null,
    checked: Boolean? = null, onCheck: ((Boolean) -> Unit)? = null,
) {
    val c = NoorTheme.colors
    val whole = onOpen == null && checked != null && onCheck != null
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 64.dp)
            .then(if (whole) Modifier.toggleable(checked!!, role = Role.Switch, onValueChange = withTick(onCheck!!)) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight()
                .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = withTick(onOpen)) else Modifier)
                .padding(start = 20.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp), color = c.textPrimary)
            if (detail != null) Text(detail, style = FootnoteStyle.copy(fontSize = 14.sp, lineHeight = 19.sp), color = if (gold) c.goldText else c.textSecondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when {
            checked != null -> {
                if (onOpen != null) Box(Modifier.width(1.dp).height(30.dp).background(c.separator))
                Box(Modifier.padding(start = 16.dp, end = 16.dp)) { NoorSwitch(checked, if (whole) null else onCheck) }
            }
            onOpen != null -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.textMuted.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 14.dp).size(22.dp))
        }
    }
}

@Composable
private fun OptionDivider() = HorizontalDivider(Modifier.padding(start = 20.dp), thickness = 0.5.dp, color = NoorTheme.colors.separator)

/**
 * "25 min before Fajr", "1 h 30 min after Isha" (as the wheels set it; Jumu'ah's name is not used: the alarm is every
 * week's).
 */
@Composable
internal fun alarmRelation(a: PrayerAlarm, f: Formatter): String {
    val h = a.minutes / 60
    val m = a.minutes % 60
    val amount = when {
        h == 0 -> pluralStringResource(R.plurals.alarm_minutes, a.minutes, f.number(a.minutes))
        m == 0 -> stringResource(R.string.alarm_amount_h, f.number(h))
        else -> stringResource(R.string.alarm_amount_hm, f.number(h), f.number(m))
    }
    return stringResource(if (a.after) R.string.alarm_after else R.string.alarm_before, amount, f.prayerName(a.prayer))
}

/** "Today at 4:27 AM", "Tomorrow at 4:27 AM", "Friday at 4:27 AM". */
@Composable
internal fun ringWhen(p: PlannedAlarm, zone: ZoneId, f: Formatter): String {
    val day = p.at.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val t = f.timeAt(p.at, zone).together()
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
private fun soundLabel(a: PrayerAlarm, custom: List<CustomSound>, vm: AdhanViewModel, ar: Boolean): String = when (a.sound) {
    AlarmSound.PHONE_ALARM -> stringResource(R.string.alarm_sound_phone)
    AlarmSound.RINGTONE -> stringResource(R.string.alarm_sound_ringtone)
    AlarmSound.NOTIFICATION -> stringResource(R.string.alarm_sound_notification)
    AlarmSound.CUSTOM -> custom.firstOrNull { it.id == a.customId }?.name ?: stringResource(R.string.alarm_sound_phone)
    AlarmSound.LIBRARY -> (voiceName(a.customId, vm, custom, ar) ?: stringResource(R.string.alarm_sound_phone)) +
        if (a.short) " · " + stringResource(R.string.alarm_length_short) else ""
}

/**
 * An alarm's sound (0.18), in four plain groups so the choice is never a long list: the phone's own (its alarm sound,
 * its ringtone, a notification only), Noor's sounds and the Adhan voices (with one Full | Short above them for both),
 * and the user's recordings. A touch chooses and plays it; Back returns to the alarm.
 */
@Composable
private fun AlarmSoundPage(contentPadding: PaddingValues, a: PrayerAlarm, custom: List<CustomSound>, vm: AdhanViewModel, ar: Boolean, f: Formatter,
                           onBack: () -> Unit, onPick: (PrayerAlarm) -> Unit, onAdd: () -> Unit) {
    val preview by vm.preview.collectAsStateWithLifecycle()
    val lib = vm.library
    var short by rememberSaveable { mutableStateOf(a.short) }
    val label = stringResource(R.string.adhan_preview)
    fun pickLib(id: String, s: Boolean) { onPick(a.copy(sound = AlarmSound.LIBRARY, customId = id, short = s)); vm.previewLibrary(id, s, 90) }
    NoorPage(stringResource(R.string.alarm_sound), contentPadding, onBack = onBack) {
        SettingsSection(stringResource(R.string.alarm_sounds_phone)) {
            SoundRow(stringResource(R.string.alarm_sound_phone), null, a.sound == AlarmSound.PHONE_ALARM, preview?.id == SYS_ALARM, label,
                onSelect = { onPick(a.copy(sound = AlarmSound.PHONE_ALARM, customId = null, short = false)); vm.previewSystem(SYS_ALARM) }, onPreview = null)
            SettingsDivider()
            SoundRow(stringResource(R.string.alarm_sound_ringtone), null, a.sound == AlarmSound.RINGTONE, preview?.id == SYS_RINGTONE, label,
                onSelect = { onPick(a.copy(sound = AlarmSound.RINGTONE, customId = null, short = false)); vm.previewSystem(SYS_RINGTONE) }, onPreview = null)
            SettingsDivider()
            SoundRow(stringResource(R.string.alarm_sound_notification), null, a.sound == AlarmSound.NOTIFICATION, false, label,
                onSelect = { vm.stopPreview(); onPick(a.copy(sound = AlarmSound.NOTIFICATION, customId = null, short = false)) }, onPreview = null)
        }

        // One length for Noor's sounds and the Adhan: the whole, or its opening (a bird's chirp or two). (Second review:
        // under its name, and a line that says what it applies to; it stood there unlabelled.)
        Column {
            com.noor.app.ui.settings.SectionHeader(stringResource(R.string.alarm_length_title))
            Segmented(listOf(false to stringResource(R.string.alarm_length_full), true to stringResource(R.string.alarm_length_short)), short, { v ->
                short = v
                if (a.sound == AlarmSound.LIBRARY && a.customId != null) pickLib(a.customId!!, v)
            })
            com.noor.app.ui.settings.SectionFooter(stringResource(R.string.alarm_length_note))
        }
        if (lib.sounds.isNotEmpty()) SettingsSection(stringResource(R.string.alarm_sounds_effects)) {
            lib.sounds.forEachIndexed { i, e ->
                if (i > 0) SettingsDivider()
                SoundRow(e.title(ar), null, a.sound == AlarmSound.LIBRARY && a.customId == e.id, preview?.id == e.id, label,
                    onSelect = { pickLib(e.id, short) }, onPreview = null)
            }
        }
        val voices = ClockVoice.entries.mapNotNull { v -> v.libraryId.takeIf { lib.byId(it) != null }?.let { v to it } }
        if (voices.isNotEmpty()) SettingsSection(stringResource(R.string.alarm_sounds_adhan),
            footer = if (!a.after) stringResource(R.string.alarm_adhan_before_warn) else null) {
            voices.forEachIndexed { i, (v, id) ->
                if (i > 0) SettingsDivider()
                SoundRow(stringResource(v.label), null, a.sound == AlarmSound.LIBRARY && a.customId == id, preview?.id == id, label,
                    onSelect = { pickLib(id, short) }, onPreview = null)
            }
        }
        SettingsSection(stringResource(R.string.adhan_my_sounds)) {
            custom.forEachIndexed { i, cs ->
                if (i > 0) SettingsDivider()
                SoundRow(cs.name, f.duration(cs.seconds), a.sound == AlarmSound.CUSTOM && a.customId == cs.id, preview?.id == cs.id, label,
                    onSelect = { onPick(a.copy(sound = AlarmSound.CUSTOM, customId = cs.id, short = false)); vm.previewCustom(cs.id, 90) }, onPreview = null)
            }
            if (custom.isNotEmpty()) SettingsDivider()
            SettingsItem(Icons.Rounded.Add, stringResource(R.string.alarm_sound_add), null, onClick = onAdd)
        }
    }
}

/**
 * The alarms, as Samsung's Clock lists its own (0.26): at the top, large and centred, when the next one rings ("Alarm in
 * 9 hours 12 minutes", and under it the day, the time and its name), or that they are all off; a "+" at the end of the
 * row under it, where Samsung has it; then each alarm as a card of its own, the time it rings next in large type, its
 * name over it and "25 min before Fajr" under it, the week's letters with its days lit, and its switch. An alarm that is
 * off is dimmed. A long press selects (as in Samsung's): every card shows a round check, the top says how many are
 * chosen, "All" chooses them all, and Delete at the foot deletes them. With none yet, what an alarm is for, and the
 * button. (The signature is the one [AdhanScreen] calls; [vm] is the same view model as the page's.)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlarmsList(
    a: com.noor.prayer.alerts.AlertSettings, rings: Rings, f: Formatter, onOpen: (Int) -> Unit, onToggle: (Int, Boolean) -> Unit,
    vm: AdhanViewModel = viewModel(),
    /** (0.26 review) What is being chosen, held by the page, whose foot shows Delete ([AlarmsDeleteBar]). */
    choice: AlarmChoice = remember { AlarmChoice() },
) {
    val c = NoorTheme.colors
    val haptics = LocalHapticFeedback.current
    val full = a.nextAlarmId() == null
    val selecting = choice.selecting
    val picked = choice.picked(a)
    fun stop() = choice.stop()
    androidx.activity.compose.BackHandler(enabled = selecting) { stop() }
    if (a.alarms.isEmpty()) {
        EmptyCard(Icons.Rounded.Alarm, stringResource(R.string.alarms_none), stringResource(R.string.alarms_empty_footer, f.number(25)),
            stringResource(R.string.alarm_add), { onOpen(0) })
        return
    }
    // (0.21) In the order of the day: by prayer, then before it, then after it.
    val sorted = a.alarms.sortedWith(compareBy({ it.prayer.ordinal }, { if (it.after) it.minutes else -it.minutes }, { it.id }))

    Column(Modifier.fillMaxWidth()) {
        // When the next alarm rings, as Samsung says it at the top of its alarms ("Alarm in 9 hours 12 minutes").
        val next = rings.next.values.filter { p -> a.alarms.any { it.id == p.alarm.id && it.enabled } }.minByOrNull { it.at }
        val title: String
        var under: List<String> = emptyList()
        when {
            selecting -> title = if (picked.isEmpty()) stringResource(R.string.alarms_select) else stringResource(R.string.alarms_selected, f.number(picked.size))
            next != null -> {
                val left = java.time.Duration.between(java.time.Instant.now(), next.at).coerceAtLeast(java.time.Duration.ZERO)
                val (h, m) = f.hoursMinutes(left)
                val hh = pluralStringResource(R.plurals.alarm_hours_long, h.toInt(), f.number(h.toInt()))
                val mm = pluralStringResource(R.plurals.alarm_minutes_long, m.toInt(), f.number(m.toInt()))
                title = when {
                    h == 0L -> stringResource(R.string.alarm_header_in, mm)
                    m == 0L -> stringResource(R.string.alarm_header_in, hh)
                    else -> stringResource(R.string.alarm_header_in_hm, hh, mm)
                }
                under = listOf(ringWhen(next, rings.zone, f), next.alarm.label.ifBlank { alarmRelation(next.alarm, f) })
            }
            a.alarms.none { it.enabled } -> title = stringResource(R.string.alarms_all_off)
            else -> title = stringResource(R.string.alarm_none_week)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
            color = c.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp).semantics { heading() })
        if (under.isNotEmpty()) FactsLine(under, FootnoteStyle.copy(fontSize = 14.sp, lineHeight = 20.sp), c.textSecondary,
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp), center = true)

        // The row under it: "+" at its end (Samsung's); while choosing, "All" at its start and Cancel at its end.
        Row(Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                val all = picked.size == a.alarms.size
                Row(Modifier.clip(RoundedCornerShape(50)).toggleable(all, role = Role.Checkbox, onValueChange = withTick<Boolean> { on -> choice.chosen = if (on) a.alarms.map { it.id }.toSet() else emptySet() })
                    .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PickMark(all)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.alarms_select_all), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                }
                Spacer(Modifier.weight(1f))
                BarAction(stringResource(R.string.alarm_cancel), { stop() })
            } else {
                Spacer(Modifier.weight(1f))
                val add = stringResource(if (full) R.string.alarm_full else R.string.alarm_add, f.number(PrayerAlarm.MAX))
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(enabled = !full, role = Role.Button, onClick = withTick { onOpen(0) })
                        .semantics { contentDescription = add },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Add, null, tint = c.goldText.copy(alpha = if (full) 0.38f else 1f), modifier = Modifier.size(30.dp)) }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sorted.forEach { al ->
            val mine = al.id in picked
            AlarmCard(al, rings.next[al.id], rings.zone, f, selecting, mine,
                onOpen = { if (selecting) choice.chosen = if (mine) picked - al.id else picked + al.id else onOpen(al.id) },
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (!selecting) { choice.selecting = true; choice.chosen = setOf(al.id) } else choice.chosen = if (mine) picked - al.id else picked + al.id
                },
                onToggle = { on -> onToggle(al.id, on) })
        }
        if (full && !selecting) com.noor.app.ui.settings.SectionFooter(stringResource(R.string.alarm_full, f.number(PrayerAlarm.MAX)))
    }
}

/** (0.26 review) The alarms being chosen to delete: held by the Adhan tab, whose foot shows Delete while choosing. */
@androidx.compose.runtime.Stable
internal class AlarmChoice {
    var selecting by mutableStateOf(false)
    var chosen by mutableStateOf(emptySet<Int>())
    /** What is chosen of the alarms there are. */
    fun picked(a: com.noor.prayer.alerts.AlertSettings): Set<Int> = chosen.filter { id -> a.alarms.any { it.id == id } }.toSet()
    fun stop() { selecting = false; chosen = emptySet() }
}

/**
 * Delete, while alarms are being chosen (0.26 review: it stood under the last card, off the screen with five alarms):
 * pinned at the page's foot, as Samsung's Clock does, on the same opaque bar as the editor's Cancel and Save.
 */
@Composable
internal fun AlarmsDeleteBar(choice: AlarmChoice, a: com.noor.prayer.alerts.AlertSettings, vm: AdhanViewModel, bottom: Dp) {
    val c = NoorTheme.colors
    val picked = choice.picked(a)
    val enabled = picked.isNotEmpty()
    FootBar(bottom) {
        Row(
            Modifier.weight(1f).heightIn(min = 48.dp).padding(vertical = 4.dp).clip(RoundedCornerShape(50))
                .clickable(enabled = enabled, role = Role.Button, onClick = withTick { picked.forEach { vm.deleteAlarm(it) }; choice.stop() }),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = c.danger.copy(alpha = if (enabled) 1f else 0.38f)
            Icon(Icons.Rounded.DeleteOutline, null, tint = ink, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.alarms_delete_selected), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp * limitedGrowth(1.2f), fontWeight = FontWeight.SemiBold),
                color = ink, maxLines = 1, softWrap = false)
        }
    }
}

/** A round check: filled in the accent when chosen, an empty ring otherwise (Samsung's selection). */
@Composable
private fun PickMark(on: Boolean) {
    val c = NoorTheme.colors
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .then(if (on) Modifier.background(c.goldFill) else Modifier.border(1.5.dp, c.textMuted, CircleShape)),
        contentAlignment = Alignment.Center,
    ) { if (on) Icon(Icons.Rounded.Check, null, tint = c.onGold, modifier = Modifier.size(17.dp)) }
}

/**
 * One alarm as a card of Samsung's list: its name (when it has one) small over its time; the time it rings next, large
 * and light, its AM or PM at half the size, with its switch at the card's end; under them what it is, "25 min before
 * Fajr", and at the end of that line the week's letters with its days lit ("Every day" when it rings on all seven).
 * Off, it is dimmed. While choosing, a round check stands at its start and the switch goes. When the letters do not
 * fit on the line (large text) they go on a line of their own under it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlarmCard(
    a: PrayerAlarm, next: PlannedAlarm?, zone: ZoneId, f: Formatter, selecting: Boolean, picked: Boolean,
    onOpen: () -> Unit, onLongPress: () -> Unit, onToggle: (Boolean) -> Unit,
) {
    val c = NoorTheme.colors
    val shape = RoundedCornerShape(26.dp)
    val dim = if (a.enabled) 1f else 0.45f
    val selectLabel = stringResource(R.string.alarms_select)
    GlassCard(
        Modifier.fillMaxWidth().clip(shape).combinedClickable(role = Role.Button, onLongClickLabel = selectLabel, onLongClick = onLongPress, onClick = withTick(onOpen)),
        padding = 0.dp, corner = 26.dp, highlighted = picked,
    ) {
        val big = MaterialTheme.typography.displaySmall.merge(com.noor.app.ui.theme.Tabular).copy(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp)
        val small = FootnoteStyle.copy(fontSize = 14.sp, lineHeight = 19.sp)
        Layout(
            content = {
                Box { if (selecting) PickMark(picked) }
                Box(Modifier.alpha(dim)) {
                    if (a.label.isNotBlank()) Text(a.label, style = small.copy(fontWeight = FontWeight.Medium), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.alpha(dim)) { next?.let { ClockText(f.clockAt(it.at, zone), big, c.textPrimary) } ?: Text("–", style = big, color = c.textPrimary, maxLines = 1) }
                Box(Modifier.alpha(dim)) { Text(alarmRelation(a, f), style = small, color = c.textSecondary) }
                Box(Modifier.alpha(dim)) { DayLetters(a.days, f) }
                Box { if (!selecting) NoorSwitch(a.enabled, onToggle) }
            },
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 15.dp),
        ) { m, cs ->
            val width = cs.maxWidth
            val gap = 12.dp.roundToPx()
            val loose = Constraints(maxWidth = width)
            val check = m[0].measure(loose)
            val lead = if (check.width > 0) check.width + 14.dp.roundToPx() else 0
            val toggle = m[5].measure(loose)
            val tail = if (toggle.width > 0) toggle.width + gap else 0
            val room = (width - lead).coerceAtLeast(0)
            // The name over the time and the time beside the switch keep clear of it; the line under the time runs the
            // card's width, what it is at its start and the days at its end (under the switch), as long as both fit;
            // otherwise the days go on a line of their own under it. Every card is built the same way.
            val label = m[1].measure(Constraints(maxWidth = (room - tail).coerceAtLeast(0)))
            val time = m[2].measure(Constraints(maxWidth = (room - tail).coerceAtLeast(0)))
            val lineWants = m[3].maxIntrinsicWidth(Constraints.Infinity)
            val daysWants = m[4].maxIntrinsicWidth(Constraints.Infinity)
            val beside = lineWants + gap + daysWants <= room
            val line = m[3].measure(Constraints(maxWidth = if (beside) lineWants else room))
            val days = m[4].measure(Constraints(maxWidth = room))
            val labelGap = if (label.height > 0) 1.dp.roundToPx() else 0
            val upper = label.height + labelGap + maxOf(time.height, toggle.height)
            val lower = if (beside) maxOf(line.height, days.height) else line.height + 4.dp.roundToPx() + days.height
            val height = upper + lower
            layout(width, height) {
                check.placeRelative(0, (height - check.height) / 2)
                label.placeRelative(lead, 0)
                val row = label.height + labelGap
                val rowH = maxOf(time.height, toggle.height)
                time.placeRelative(lead, row + (rowH - time.height) / 2)
                toggle.placeRelative(width - toggle.width, row + (rowH - toggle.height) / 2)
                if (beside) {
                    line.placeRelative(lead, upper + (lower - line.height) / 2)
                    days.placeRelative(width - days.width, upper + (lower - days.height) / 2)
                } else {
                    line.placeRelative(lead, upper)
                    days.placeRelative(lead, upper + line.height + 4.dp.roundToPx())
                }
            }
        }
    }
}

/**
 * An alarm's days on its card, as Samsung writes them: "Every day", or the week's seven letters with the days it rings
 * on lit in the accent and the others faint. Read out as the days' names ("Mon–Fri").
 */
@Composable
private fun DayLetters(days: Int, f: Formatter) {
    val c = NoorTheme.colors
    val grow = limitedGrowth(1.15f)
    val style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp * grow, letterSpacing = 0.sp)
    if (days == PrayerAlert.ALL_DAYS) { Text(stringResource(R.string.days_every), style = style.copy(fontWeight = FontWeight.Medium), color = c.goldText, maxLines = 1); return }
    val said = daysLabel(days, f)
    // (Each letter's place grows with the letters, to the same limit.)
    val slot = minOf(androidx.compose.ui.platform.LocalDensity.current.fontScale, 1.15f).coerceAtLeast(1f)
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = said }) {
        for (d in week(f)) {
            val on = days and (1 shl (d.value - 1)) != 0
            Box(Modifier.width(15.dp * slot), contentAlignment = Alignment.Center) {
                Text(letter(d, f), style = style.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (on) c.goldText else c.textMuted.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}
