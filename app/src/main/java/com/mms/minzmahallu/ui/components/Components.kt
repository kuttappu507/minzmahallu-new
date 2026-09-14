package com.mms.minzmahallu.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mms.minzmahallu.i18n.I18n
import com.mms.minzmahallu.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun C() = LocalMmsColors.current

@Composable
fun T() = LocalTint.current

@Composable
fun MmsCard(
    modifier: Modifier = Modifier,
    tint: ModuleTint? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = C()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "card")
    Column(
        modifier = modifier
            .scale(scale)
            .shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(0.06f), spotColor = Color.Black.copy(0.12f))
            .clip(RoundedCornerShape(14.dp))
            .background(c.panel)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.pointerInput(Unit) {
                detectTapGestures(onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                    onClick()
                })
            } else Modifier)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun MmsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
    ghost: Boolean = false,
    small: Boolean = false,
) {
    val c = C()
    var pressed by remember { mutableStateOf(false) }
    val y by animateFloatAsState(if (pressed) 1f else 0f, tween(100), label = "btn")
    val bg = when {
        !enabled -> c.line
        danger && ghost -> c.panel
        danger -> c.cRose
        primary && !ghost -> c.em
        else -> c.panel
    }
    val fg = when {
        !enabled -> c.fnt
        danger && ghost -> c.cRose
        danger || (primary && !ghost) -> Color.White
        else -> c.tx
    }
    val borderCol = when {
        danger && ghost -> c.roseLine
        primary && !ghost -> c.emdd
        else -> c.line2
    }
    Box(
        modifier = modifier
            .height(if (small) 34.dp else 42.dp)
            .offset(y = y.dp)
            .shadow(if (pressed || !enabled) 0.dp else 2.dp, RoundedCornerShape(if (small) 99.dp else 11.dp))
            .clip(RoundedCornerShape(if (small) 99.dp else 11.dp))
            .background(bg)
            .border(1.5.dp, borderCol, RoundedCornerShape(if (small) 99.dp else 11.dp))
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    pressed = true
                    tryAwaitRelease()
                    pressed = false
                    onClick()
                })
            }
            .padding(horizontal = if (small) 12.dp else 17.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = TextStyle(
                color = fg,
                fontSize = if (small) 12.5.sp else 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun MmsIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = C()
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .mmsClickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun MmsInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    singleLine: Boolean = true,
    password: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = C()
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        if (label != null) {
            androidx.compose.foundation.text.BasicText(
                text = label.uppercase(),
                style = MmsType.label.copy(color = c.tx, letterSpacing = 0.8.sp),
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.panel)
                .border(
                    1.5.dp,
                    if (focused) c.em else c.line2,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                androidx.compose.foundation.text.BasicText(
                    text = placeholder,
                    style = MmsType.body.copy(color = c.fnt, fontSize = 15.sp)
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                enabled = enabled,
                textStyle = MmsType.body.copy(color = c.tx, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(c.em),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth(),
                onTextLayout = {},
                decorationBox = { inner ->
                    // track focus roughly via composition
                    inner()
                }
            )
        }
    }
}

@Composable
fun MmsSelect(
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val c = C()
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        if (label != null) {
            androidx.compose.foundation.text.BasicText(
                text = label.uppercase(),
                style = MmsType.label.copy(color = c.tx),
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.panel)
                .border(1.5.dp, c.line2, RoundedCornerShape(10.dp))
                .mmsClickable { open = !open }
                .padding(horizontal = 13.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            androidx.compose.foundation.text.BasicText(
                text = value.ifBlank { "—" },
                style = MmsType.body.copy(color = c.tx, fontSize = 15.sp)
            )
            androidx.compose.foundation.text.BasicText(
                text = "▾",
                style = MmsType.body.copy(color = c.fnt),
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
        AnimatedVisibility(open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .shadow(12.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.panel)
                    .border(1.dp, c.line, RoundedCornerShape(12.dp))
            ) {
                options.forEach { opt ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .mmsClickable { onSelect(opt); open = false }
                            .background(if (opt == value) c.selBg else Color.Transparent)
                            .padding(12.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(opt, style = MmsType.body.copy(color = c.tx))
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit, tint: ModuleTint? = null) {
    val c = C()
    val t = tint ?: T()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) t.sc else c.panel)
            .border(1.5.dp, if (selected) t.sc else c.line2, RoundedCornerShape(99.dp))
            .mmsClickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 7.dp)
    ) {
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = MmsType.bodySm.copy(
                color = if (selected) Color.White else c.mut,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

@Composable
fun StatusPill(text: String, kind: String = "default") {
    val c = C()
    val (bg, fg, line) = when (kind.lowercase()) {
        "paid", "active", "approved", "collected", "issued" -> Triple(c.selBg, c.emd, c.em.copy(0.3f))
        "pending", "partial" -> Triple(Color(0xFFFDF5DD), Color(0xFF96640A), Color(0xFFF2E2A8))
        "overdue", "rejected", "cancelled", "void" -> Triple(c.roseBg, c.cRose, c.roseLine)
        "disbursed" -> Triple(Color(0xFFE7EFFE), Color(0xFF1D4ED8), Color(0xFFC4D8FB))
        else -> Triple(c.panel2, c.mut, c.line)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .border(1.5.dp, line, RoundedCornerShape(99.dp))
            .padding(horizontal = 11.dp, vertical = 4.dp)
    ) {
        androidx.compose.foundation.text.BasicText(
            text = text,
            style = TextStyle(color = fg, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
        )
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    tint: ModuleTint,
    delta: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = C()
    MmsCard(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(bottomStart = 5.dp, bottomEnd = 5.dp))
                .background(Brush.horizontalGradient(listOf(tint.sc, Color.Transparent)))
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.text.BasicText(
            label.uppercase(),
            style = MmsType.overline.copy(color = c.fnt, letterSpacing = 1.2.sp)
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.text.BasicText(
            value,
            style = MmsType.stat.copy(color = tint.st.copy(alpha = 0.9f), fontSize = 28.sp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        if (delta != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(tint.sb)
                    .border(1.dp, tint.sl, RoundedCornerShape(99.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                androidx.compose.foundation.text.BasicText(delta, style = TextStyle(color = tint.st, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
fun PageHeader(title: String, subtitle: String, tint: ModuleTint, actions: @Composable RowScope.() -> Unit = {}) {
    val c = C()
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(tint.sb, c.panel2)))
                .border(1.5.dp, tint.sl, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(tint.sc))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.foundation.text.BasicText(title, style = MmsType.title.copy(color = c.tx, fontSize = 22.sp))
            androidx.compose.foundation.text.BasicText(subtitle, style = MmsType.bodySm.copy(color = c.mut))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun EmptyState(title: String, desc: String, cta: String? = null, onCta: (() -> Unit)? = null) {
    val c = C(); val t = T()
    Column(
        Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(t.sb).border(1.5.dp, t.sl, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(20.dp).clip(CircleShape).background(t.sc.copy(0.5f))) }
        Spacer(Modifier.height(12.dp))
        androidx.compose.foundation.text.BasicText(title, style = MmsType.headline.copy(color = c.tx, fontSize = 14.5.sp))
        Spacer(Modifier.height(4.dp))
        androidx.compose.foundation.text.BasicText(desc, style = MmsType.caption.copy(color = c.fnt))
        if (cta != null && onCta != null) {
            Spacer(Modifier.height(12.dp))
            MmsButton(cta, onCta, small = true)
        }
    }
}

@Composable
fun MmsDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String = I18n.t("action_save"),
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    danger: Boolean = false,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = C()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1521).copy(alpha = 0.62f))
                .mmsClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth(if (compact) 0.88f else 0.94f)
                    .heightIn(max = 640.dp)
                    .shadow(24.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.panel)
                    .border(1.dp, c.line, RoundedCornerShape(18.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.text.BasicText(title, style = MmsType.headline.copy(color = c.tx, fontWeight = FontWeight.Bold, fontSize = 17.sp), modifier = Modifier.weight(1f))
                    MmsIconButton(onClick = onDismiss) {
                        androidx.compose.foundation.text.BasicText("✕", style = TextStyle(color = c.fnt, fontSize = 16.sp))
                    }
                }
                Box(Modifier.height(1.dp).fillMaxWidth().background(c.line))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .background(
                            Brush.verticalGradient(listOf(c.bg, c.bg))
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
                if (onConfirm != null) {
                    Box(Modifier.height(1.dp).fillMaxWidth().background(c.line))
                    Row(
                        Modifier.fillMaxWidth().background(c.panel2).padding(13.dp, 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.End)
                    ) {
                        MmsButton(I18n.t("action_cancel"), onDismiss, primary = false, small = true)
                        MmsButton(confirmLabel, onConfirm, primary = !danger, danger = danger, enabled = confirmEnabled, small = true)
                    }
                }
            }
        }
    }
}

@Composable
fun DataTable(
    columns: List<String>,
    rows: List<List<@Composable () -> Unit>>,
    onRowClick: ((Int) -> Unit)? = null,
) {
    val c = C()
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(c.panel)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
    ) {
        Row(Modifier.fillMaxWidth().background(c.head).padding(horizontal = 12.dp, vertical = 12.dp)) {
            columns.forEach { col ->
                androidx.compose.foundation.text.BasicText(
                    col.uppercase(),
                    style = MmsType.label.copy(color = c.mut, fontSize = 11.sp, letterSpacing = 1.sp),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (rows.isEmpty()) {
            EmptyState(I18n.t("common_no_data").ifBlank { "No records" }, "")
        } else {
            rows.forEachIndexed { idx, cells ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (onRowClick != null) Modifier.mmsClickable { onRowClick(idx) } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    cells.forEach { cell ->
                        Box(Modifier.weight(1f)) { cell() }
                    }
                }
                if (idx < rows.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            }
        }
    }
}

@Composable
fun ToastHost(messages: List<com.mms.minzmahallu.data.model.ToastMsg>, onDismiss: (Long) -> Unit) {
    val c = C()
    Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.BottomEnd) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp), horizontalAlignment = Alignment.End) {
            messages.takeLast(5).forEach { msg ->
                key(msg.id) {
                    LaunchedEffect(msg.id) { delay(3500); onDismiss(msg.id) }
                    val border = when (msg.kind) {
                        com.mms.minzmahallu.data.model.ToastMsg.Kind.Error -> c.cRose
                        com.mms.minzmahallu.data.model.ToastMsg.Kind.Warn -> c.cGold
                        com.mms.minzmahallu.data.model.ToastMsg.Kind.Success -> c.em
                        else -> c.cSky
                    }
                    Row(
                        Modifier
                            .widthIn(max = 340.dp)
                            .shadow(16.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.panel)
                            .border(1.5.dp, c.line, RoundedCornerShape(14.dp))
                            .padding(start = 0.dp)
                    ) {
                        Box(Modifier.width(6.dp).fillMaxHeight().background(border))
                        androidx.compose.foundation.text.BasicText(
                            msg.message,
                            style = MmsType.bodySm.copy(color = c.tx),
                            modifier = Modifier.padding(12.dp, 12.dp).weight(1f, false)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleBarChart(points: List<com.mms.minzmahallu.data.model.ChartPoint>, dual: Boolean = false, modifier: Modifier = Modifier) {
    val c = C()
    val max = (points.maxOfOrNull { maxOf(it.value, it.value2) } ?: 1.0).coerceAtLeast(1.0)
    Column(modifier.fillMaxWidth().height(180.dp).padding(8.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            points.forEach { p ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    if (dual) {
                        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                            Box(Modifier.weight(1f).fillMaxHeight((p.value / max).toFloat().coerceIn(0.02f, 1f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(c.em))
                            Box(Modifier.weight(1f).fillMaxHeight((p.value2 / max).toFloat().coerceIn(0.02f, 1f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(c.cRose))
                        }
                    } else {
                        Box(Modifier.fillMaxWidth(0.7f).fillMaxHeight((p.value / max).toFloat().coerceIn(0.02f, 1f)).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Brush.verticalGradient(listOf(c.em, c.emd))))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            points.forEach { p ->
                androidx.compose.foundation.text.BasicText(p.label, style = MmsType.caption.copy(color = c.fnt, fontSize = 9.sp), modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = I18n.t("search_placeholder")) {
    val c = C()
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(c.panel2)
            .border(1.5.dp, c.line, RoundedCornerShape(99.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.text.BasicText("⌕", style = TextStyle(color = c.fnt, fontSize = 14.sp))
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MmsType.bodySm.copy(color = c.tx),
            cursorBrush = SolidColor(c.em),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) androidx.compose.foundation.text.BasicText(placeholder, style = MmsType.bodySm.copy(color = c.fnt))
                inner()
            }
        )
        if (value.isNotEmpty()) {
            MmsIconButton(onClick = { onChange("") }) {
                androidx.compose.foundation.text.BasicText("×", style = TextStyle(color = c.fnt, fontSize = 16.sp))
            }
        }
    }
}

@Composable
fun CellText(text: String, strong: Boolean = false, sub: String? = null) {
    val c = C()
    Column {
        androidx.compose.foundation.text.BasicText(
            text,
            style = MmsType.body.copy(color = c.tx, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium, fontSize = 13.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (sub != null) androidx.compose.foundation.text.BasicText(sub, style = MmsType.caption.copy(color = c.fnt, fontSize = 11.sp), maxLines = 1)
    }
}
