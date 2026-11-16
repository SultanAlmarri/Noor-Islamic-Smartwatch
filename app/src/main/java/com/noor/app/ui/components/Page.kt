package com.noor.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noor.app.R
import com.noor.app.ui.theme.NoorTheme

/**
 * The measures every page shares (0.14): an 8-point grid, one margin, one width for reading. Lists and cards never
 * grow wider than [MaxWidth], so a foldable's inner screen or a tablet shows a calm column, not stretched rows.
 */
object Page {
    val Margin = 16.dp
    val MaxWidth = 640.dp
    /** The slim bar at the top of a page: the back chevron and the page's name. */
    val BarHeight = 48.dp
    /** Between the groups of a page. */
    val GroupGap = 24.dp
}

/**
 * A page (0.14), as on iOS. A tab's page opens on a large title that scrolls away with the content; once it has gone
 * under the top of the screen, a slim bar carries the title so the page always says where one is. A page pushed from
 * a tab ([onBack] given) has the slim bar from the start, with a back chevron in Noor's accent and the title centred;
 * the bar turns solid, with a hairline, only once something has scrolled under it.
 *
 * [floating] is drawn over the page (a snackbar, a button held at the bottom).
 */
@Composable
fun NoorPage(
    title: String,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)? = null,
    scroll: ScrollState = rememberScrollState(),
    spacing: Dp = Page.GroupGap,
    floating: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = NoorTheme.colors
    val top = contentPadding.calculateTopPadding()
    val large = onBack == null
    val density = LocalDensity.current
    // How far the bar has come in: with the large title's leaving on a tab, with the first scrolled pixels otherwise.
    val from = with(density) { (if (large) 30.dp else 0.dp).toPx() }
    val over = with(density) { (if (large) 22.dp else 12.dp).toPx() }
    val bar: () -> Float = { ((scroll.value - from) / over).coerceIn(0f, 1f) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.widthIn(max = Page.MaxWidth).fillMaxWidth()
                    .padding(top = top + if (large) 0.dp else Page.BarHeight, bottom = contentPadding.calculateBottomPadding())
                    .padding(horizontal = Page.Margin),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                if (large) LargeTitle(title) else Spacer(Modifier.height(0.dp))
                content()
                Spacer(Modifier.height(8.dp))
            }
        }
        // The bar: the page's colour (almost opaque: what scrolls under it stays a hint) and a hairline.
        Box(Modifier.fillMaxWidth().height(top + Page.BarHeight).graphicsLayer { alpha = bar() }.background(c.background.copy(alpha = 0.97f))) {
            Hairline(Modifier.align(Alignment.BottomCenter))
        }
        Box(Modifier.fillMaxWidth().padding(top = top).height(Page.BarHeight), contentAlignment = Alignment.Center) {
            Text(
                title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 60.dp)
                    .then(if (large) Modifier.graphicsLayer { alpha = bar() }.clearAndSetSemantics { } else Modifier.semantics { heading() }),
            )
            if (onBack != null) BackButton(onBack, Modifier.align(Alignment.CenterStart).padding(start = 4.dp))
        }
        floating?.invoke(this)
    }
}

/** The large title of a tab's page. Arabic is joined writing, so only Latin titles have their letters tightened. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier) {
    val arabic = title.any { it in '؀'..'ۿ' }
    Text(
        title, color = NoorTheme.colors.textPrimary,
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = if (arabic) 0.sp else (-0.5).sp),
        modifier = modifier.padding(start = 4.dp, top = 12.dp).semantics { heading() },
    )
}

/** Back, as a chevron in the accent colour; mirrored in Arabic; named for TalkBack; a full 48 dp to touch. */
@Composable
fun BackButton(onBack: () -> Unit, modifier: Modifier = Modifier, tint: androidx.compose.ui.graphics.Color = NoorTheme.colors.goldText) {
    val label = stringResource(R.string.back)
    Box(
        modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onBack).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // (The glyph's own box is off-centre: it leaves room for the stem of an arrow it does not have.)
        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = tint, modifier = Modifier.padding(start = 6.dp).size(20.dp))
    }
}

/** The slim bar as a row of its own, for pages that lay themselves out (the location screens). */
@Composable
fun NavBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Page.BarHeight), contentAlignment = Alignment.Center) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = NoorTheme.colors.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 56.dp).semantics { heading() })
        BackButton(onBack, Modifier.align(Alignment.CenterStart).layout { m, cs ->
            // Pages keep their own margin; the chevron hangs a little into it, so it lines up with the content's edge.
            val p = m.measure(cs); layout(p.width, p.height) { p.placeRelative(-8.dp.roundToPx(), 0) }
        })
    }
}

/** Lets a row of chips or pictures run to the edges of the screen, past the page's margin. */
fun Modifier.bleed(margin: Dp = Page.Margin): Modifier = layout { measurable, constraints ->
    val m = margin.roundToPx()
    val p = measurable.measure(constraints.copy(minWidth = (constraints.minWidth + 2 * m).coerceAtMost(constraints.maxWidth + 2 * m), maxWidth = constraints.maxWidth + 2 * m))
    layout((p.width - 2 * m).coerceAtLeast(0), p.height) { p.place(-m, 0) }
}

/**
 * A row of capsules to choose one of a few (0.14): always one line, scrolling sideways past the page's edges when
 * there are many, instead of wrapping into a ragged block.
 */
@Composable
fun <T> ChoiceChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = NoorTheme.colors
    Row(
        modifier.bleed().fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Page.Margin),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.heightIn(min = 48.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { onSelect(value) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, letterSpacing = 0.sp), maxLines = 1,
                    color = if (on) c.onGold else c.textSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.gold else c.surface)
                        .then(if (!on && c.isDark) Modifier.background(c.separator, RoundedCornerShape(50)) else Modifier)
                        .heightIn(min = 36.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** A search field as on iOS: a quiet filled capsule, a magnifier, and a round button that clears it. */
@Composable
fun NoorSearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val c = NoorTheme.colors
    val style = MaterialTheme.typography.bodyLarge.copy(color = c.textPrimary)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true, textStyle = style, cursorBrush = SolidColor(c.gold),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                    .then(if (c.isDark) Modifier.background(c.separator.copy(alpha = 0.04f)) else Modifier).padding(start = 12.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.textMuted, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    if (value.isEmpty()) Text(hint, style = style, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
                if (value.isNotEmpty()) {
                    val clear = stringResource(R.string.settings_search_clear)
                    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = clear) { onChange("") }.semantics { contentDescription = clear },
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Cancel, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                    }
                } else Spacer(Modifier.width(10.dp))
            }
        },
    )
}

/** How a button weighs: the one main action of a page, a second action beside it, or a quiet one. */
enum class ButtonKind { Filled, Tonal, Plain }

/** Noor's button (0.14): 50 dp tall, softly rounded, a single line that shrinks before it wraps. */
@Composable
fun NoorButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: ButtonKind = ButtonKind.Filled,
    icon: ImageVector? = null, enabled: Boolean = true, danger: Boolean = false,
) {
    val c = NoorTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val bg = when (kind) {
        ButtonKind.Filled -> c.gold
        ButtonKind.Tonal -> c.gold.copy(alpha = if (c.isDark) 0.16f else 0.14f)
        ButtonKind.Plain -> androidx.compose.ui.graphics.Color.Transparent
    }
    val fg = when { danger -> c.danger; kind == ButtonKind.Filled -> c.onGold; else -> c.goldText }
    Row(
        modifier.heightIn(min = 50.dp).clip(shape).background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = ripple(color = fg), enabled = enabled, role = Role.Button, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }.padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), color = fg, textAlign = TextAlign.Center)
    }
}

/** A sheet that rises from the bottom, with Noor's surface, corners and grabber. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoorSheet(onDismiss: () -> Unit, state: SheetState, content: @Composable ColumnScope.() -> Unit) {
    val c = NoorTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = c.surface, contentColor = c.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 5.dp).clip(CircleShape).background(c.textMuted.copy(alpha = 0.35f))) },
        content = content,
    )
}
