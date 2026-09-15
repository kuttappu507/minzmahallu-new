package com.mms.minzmahallu.ui.components

import android.app.DatePickerDialog
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mms.minzmahallu.i18n.I18n
import com.mms.minzmahallu.ui.theme.*
import java.util.Calendar

/** Safe glyph set (present in stock Android fonts — no tofu risk). */
object G {
    const val CHECK = "✓"
    const val CROSS = "✕"
    const val PLUS = "+"
    const val MINUS = "−"
    const val SEARCH = "⌕"
    const val DOT = "●"
    const val DIAMOND = "◆"
    const val STAR = "★"
    const val HEART = "♥"
    const val RIGHT = "→"
    const val LEFT = "←"
    const val UP = "↑"
    const val DOWN = "↓"
    const val SHARE = "↗"
    const val REFRESH = "↻"
    const val PHONE = "☎"
    const val ALERT = "!"
    const val PLAY = "►"
}

/** Framework date picker wrapped in an MMS-styled field (no Material dependency). */
@Composable
fun MmsDateField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "YYYY-MM-DD",
    enabled: Boolean = true,
) {
    val c = C()
    val ctx = LocalContext.current
    Column(modifier = modifier.alpha(if (enabled) 1f else 0.55f)) {
        if (label != null) {
            androidx.compose.foundation.text.BasicText(
                text = label.uppercase(),
                style = MmsType.label.copy(color = c.tx, letterSpacing = 0.8.sp),
                modifier = Modifier.padding(bottom = 7.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.panel)
                .border(1.5.dp, c.line2, RoundedCornerShape(10.dp))
                .then(
                    if (enabled) Modifier.mmsClickable {
                        val cal = Calendar.getInstance()
                        try {
                            val p = value.split("-")
                            if (p.size == 3) cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
                        } catch (_: Exception) {
                        }
                        DatePickerDialog(
                            ctx,
                            { _, y, m, d -> onChange("%04d-%02d-%02d".format(y, m + 1, d)) },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    } else Modifier
                )
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.text.BasicText(
                text = value.ifBlank { placeholder },
                style = MmsType.body.copy(
                    color = if (value.isBlank()) c.fnt else c.tx,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.selBg)
                    .border(1.dp, c.em.copy(alpha = 0.35f), RoundedCornerShape(99.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                androidx.compose.foundation.text.BasicText(
                    "PICK",
                    style = MmsType.label.copy(color = c.emd, fontSize = 10.sp)
                )
            }
        }
    }
}

/** Searchable full picker dialog for long option lists (families, members, accounts…). */
@Composable
fun MmsPickerDialog(
    title: String,
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = C()
    var q by remember { mutableStateOf("") }
    val filtered = remember(q, options) {
        if (q.isBlank()) options
        else options.filter { it.contains(q, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .shadow(24.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(c.panel)
                .border(1.dp, c.line, RoundedCornerShape(18.dp))
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicText(
                    title,
                    style = MmsType.headline.copy(color = c.tx, fontWeight = FontWeight.Bold, fontSize = 16.sp),
                    modifier = Modifier.weight(1f)
                )
                MmsIconButton(onClick = onDismiss) {
                    androidx.compose.foundation.text.BasicText(G.CROSS, style = TextStyle(color = c.fnt, fontSize = 16.sp))
                }
            }
            SearchField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp))
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                EmptyState("No matches", "Try a different search")
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filtered) { opt ->
                        val on = opt == selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) c.selBg else Color.Transparent)
                                .mmsClickable { onSelect(opt) }
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.text.BasicText(
                                opt,
                                style = MmsType.bodySm.copy(
                                    color = if (on) c.emd else c.tx,
                                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (on) androidx.compose.foundation.text.BasicText(
                                G.CHECK,
                                style = TextStyle(color = c.em, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            )
                        }
                    }
                    item { Spacer(Modifier.height(10.dp)) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            androidx.compose.foundation.text.BasicText(
                "${filtered.size} of ${options.size}",
                style = MmsType.caption.copy(color = c.fnt),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = I18n.t("action_delete"),
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    MmsDialog(
        title, onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        danger = danger,
        compact = true
    ) {
        androidx.compose.foundation.text.BasicText(
            message,
            style = MmsType.bodySm.copy(color = C().mut)
        )
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    strong: Boolean = false,
    mono: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    val c = C()
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        androidx.compose.foundation.text.BasicText(
            label,
            style = MmsType.caption.copy(color = c.fnt),
            modifier = Modifier.width(112.dp).padding(top = 2.dp)
        )
        androidx.compose.foundation.text.BasicText(
            value.ifBlank { "—" },
            style = (if (mono) MmsType.code else MmsType.bodySm).copy(
                color = valueColor ?: c.tx,
                fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun InfoBanner(text: String, kind: String = "info", modifier: Modifier = Modifier) {
    val c = C()
    val t = T()
    val (bg, fg, line) = when (kind) {
        "warn" -> Triple(Color(0xFFFDF5DD), Color(0xFF96640A), Color(0xFFF2E2A8))
        "error" -> Triple(c.roseBg, c.cRose, c.roseLine)
        "success" -> Triple(c.selBg, c.emd, c.em.copy(alpha = 0.3f))
        else -> Triple(t.sb, t.st, t.sl)
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, line, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.text.BasicText(
            text,
            style = MmsType.bodySm.copy(color = fg),
            modifier = Modifier.weight(1f)
        )
    }
}

/** Shimmer-style loading placeholders for lists. */
@Composable
fun LoadingList(lines: Int = 5, modifier: Modifier = Modifier) {
    val c = C()
    val alpha by rememberInfiniteTransition(label = "sh").animateFloat(
        0.35f, 0.9f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "a"
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(lines) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.panel2)
                    .border(1.dp, c.line, RoundedCornerShape(16.dp))
            )
        }
    }
}

@Composable
fun QrImage(bitmap: Bitmap?, size: Dp = 180.dp, modifier: Modifier = Modifier) {
    val c = C()
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), contentDescription = "QR code", modifier = Modifier.fillMaxSize())
        } else {
            androidx.compose.foundation.text.BasicText(
                "QR unavailable",
                style = MmsType.caption.copy(color = c.fnt)
            )
        }
    }
}

@Composable
fun ProgressBar01(progress: Float, modifier: Modifier = Modifier, tint: ModuleTint? = null) {
    val t = tint ?: T()
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(500), label = "pb")
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(t.sb)
    ) {
        Box(
            Modifier
                .fillMaxWidth(anim)
                .fillMaxHeight()
                .clip(RoundedCornerShape(99.dp))
                .background(Brush.horizontalGradient(listOf(t.sc, t.sc.copy(alpha = 0.7f))))
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = C()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicText(
            text.uppercase(),
            style = MmsType.overline.copy(color = c.mut, letterSpacing = 1.2.sp)
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).height(1.dp).background(c.line))
    }
}

/** Gradient monogram tile used across nav, dashboard and lists. */
@Composable
fun TintTile(letter: String, tint: ModuleTint, size: Dp = 40.dp, rounded: Dp = 12.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(rounded))
            .background(Brush.linearGradient(listOf(tint.sc, tint.sc.copy(alpha = 0.72f)))),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.text.BasicText(
            letter.take(2).uppercase(),
            style = TextStyle(
                color = Color.White,
                fontSize = (size.value * 0.4f).sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
