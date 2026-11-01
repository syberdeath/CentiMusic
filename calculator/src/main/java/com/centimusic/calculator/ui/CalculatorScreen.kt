package com.centimusic.calculator.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.centimusic.calculator.engine.CalcState
import com.centimusic.calculator.engine.HistoryEntry
import com.centimusic.calculator.engine.Key
import com.centimusic.calculator.engine.Tok
import kotlin.math.roundToInt

private enum class KeyStyle { Digit, Function, Operator, Equals, Sci }

private data class Pad(
    val label: String,
    val key: Key,
    val style: KeyStyle,
    /** Dot-matrix label; scientific function names read better in the sans face. */
    val dotLabel: Boolean = true,
    val active: Boolean = false,
    val description: String = label,
)

private val basicRows: List<List<Pad>> = listOf(
    listOf(
        Pad("AC", Key.Clear, KeyStyle.Function, description = "All clear"),
        Pad("+/−", Key.Negate, KeyStyle.Function, description = "Change sign"),
        Pad("%", Key.Postfix(Tok.PERCENT), KeyStyle.Function, description = "Percent"),
        Pad("÷", Key.Operator(Tok.DIVIDE), KeyStyle.Operator, description = "Divide"),
    ),
    listOf(digit('7'), digit('8'), digit('9'), Pad("×", Key.Operator(Tok.TIMES), KeyStyle.Operator, description = "Multiply")),
    listOf(digit('4'), digit('5'), digit('6'), Pad("−", Key.Operator(Tok.MINUS), KeyStyle.Operator, description = "Minus")),
    listOf(digit('1'), digit('2'), digit('3'), Pad("+", Key.Operator(Tok.PLUS), KeyStyle.Operator, description = "Plus")),
    listOf(
        digit('0'),
        Pad(".", Key.Dot, KeyStyle.Digit, description = "Decimal point"),
        Pad("DEL", Key.Backspace, KeyStyle.Function, description = "Delete"),
        Pad("=", Key.Equals, KeyStyle.Equals, description = "Equals"),
    ),
)

private fun digit(d: Char) = Pad(d.toString(), Key.Digit(d), KeyStyle.Digit)

/** The 15 scientific keys; "2nd" swaps most of them for their inverse. */
private fun sciPads(s: CalcState): List<Pad> {
    val inv = s.inverse
    fun fn(label: String, token: String) = Pad(label, Key.Function(token), KeyStyle.Sci, dotLabel = false)
    fun post(label: String, op: String, desc: String) = Pad(label, Key.Postfix(op), KeyStyle.Sci, dotLabel = false, description = desc)
    return listOf(
        Pad("2ND", Key.ToggleInverse, KeyStyle.Sci, active = inv, description = "Second functions"),
        Pad(s.angle.name, Key.ToggleAngle, KeyStyle.Sci, description = "Angle unit ${s.angle.name}"),
        if (inv) fn("sin⁻¹", "asin(") else fn("sin", "sin("),
        if (inv) fn("cos⁻¹", "acos(") else fn("cos", "cos("),
        if (inv) fn("tan⁻¹", "atan(") else fn("tan", "tan("),
        if (inv) post("1/x", Tok.RECIPROCAL, "Reciprocal")
        else Pad("xʸ", Key.Operator(Tok.POWER), KeyStyle.Sci, dotLabel = false, description = "Power"),
        if (inv) Pad("eˣ", Key.PowerOf(Tok.E), KeyStyle.Sci, dotLabel = false) else fn("ln", "ln("),
        if (inv) Pad("10ˣ", Key.PowerOf("10"), KeyStyle.Sci, dotLabel = false) else fn("log", "log("),
        if (inv) fn("∛x", "∛(") else fn("√x", "√("),
        if (inv) post("x³", Tok.CUBE, "Cube") else post("x²", Tok.SQUARE, "Square"),
        Pad("(", Key.Open, KeyStyle.Sci, description = "Open bracket"),
        Pad(")", Key.Close, KeyStyle.Sci, description = "Close bracket"),
        Pad("π", Key.Constant(Tok.PI), KeyStyle.Sci, dotLabel = false, description = "Pi"),
        Pad("e", Key.Constant(Tok.E), KeyStyle.Sci, dotLabel = false, description = "Euler's number"),
        post("x!", Tok.FACTORIAL, "Factorial"),
    )
}

@Composable
fun CalculatorScreen(vm: CalcViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val sciPreference by vm.scientific.collectAsStateWithLifecycle()
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val c = LocalCalcColors.current
    val view = LocalView.current
    val press: (Key) -> Unit = { key ->
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        vm.press(key)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val landscape = maxWidth > maxHeight
        val scientific = landscape || sciPreference
        val gap = if (landscape) 8.dp else 10.dp
        val topBar = 48.dp

        // Key sizes come from the width (round keys in basic mode), capped so the display keeps room.
        val cell = (maxWidth - gap * 3) / 4
        val basicTarget: Dp
        val sciKey: Dp
        if (landscape) {
            val k = ((maxHeight - topBar) * 0.68f - gap * 4) / 5
            basicTarget = k
            sciKey = k
        } else {
            val wantBasic = if (scientific) cell * 0.70f else cell * 0.92f
            val wantSci = cell * 0.50f
            val wanted = wantBasic * 5 + gap * 4 + if (scientific) wantSci * 3 + gap * 3 else 0.dp
            val budget = (maxHeight - topBar) * 0.70f
            val scale = if (wanted > budget) budget / wanted else 1f
            basicTarget = wantBasic * scale
            sciKey = wantSci * scale
        }
        val basicKey by animateDpAsState(basicTarget, spring(stiffness = Spring.StiffnessMediumLow), label = "keyHeight")
        val keypadHeight = if (landscape) {
            basicTarget * 5 + gap * 4
        } else {
            basicTarget * 5 + gap * 4 + if (scientific) sciKey * 3 + gap * 3 else 0.dp
        }

        Column(Modifier.fillMaxSize()) {
            TopBar(
                showModeToggle = !landscape,
                scientific = scientific,
                historyOpen = showHistory,
                onScientific = vm::setScientific,
                onHistory = { showHistory = !showHistory },
                modifier = Modifier.height(topBar),
            )
            Display(state, compact = landscape, modifier = Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(gap))
            if (showHistory) {
                HistoryPanel(
                    history = state.history,
                    onRecall = { vm.recall(it); showHistory = false },
                    onClear = vm::clearHistory,
                    modifier = Modifier.fillMaxWidth().height(keypadHeight),
                )
            } else if (landscape) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    KeyGrid(sciPads(state).chunked(3), sciKey, gap, press, Modifier.weight(3f))
                    KeyGrid(basicRows, basicKey, gap, press, Modifier.weight(4f), onClear = { press(Key.Clear) })
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    AnimatedVisibility(
                        visible = scientific,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        KeyGrid(sciPads(state).chunked(5), sciKey, gap, press)
                    }
                    KeyGrid(basicRows, basicKey, gap, press, onClear = { press(Key.Clear) })
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    showModeToggle: Boolean,
    scientific: Boolean,
    historyOpen: Boolean,
    onScientific: (Boolean) -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalCalcColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            "CENTICALC",
            style = TextStyle(
                fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                letterSpacing = 0.06.em, color = c.textPrimary,
            ),
        )
        Box(Modifier.padding(start = 3.dp, bottom = 10.dp).size(5.dp).clip(CircleShape).background(c.accent))
        Spacer(Modifier.weight(1f))
        if (showModeToggle) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, c.divider, RoundedCornerShape(50))
                    .padding(3.dp),
            ) {
                Chip("BASIC", selected = !scientific) { onScientific(false) }
                Chip("SCI", selected = scientific) { onScientific(true) }
            }
            Spacer(Modifier.width(8.dp))
        }
        HistoryButton(open = historyOpen, onClick = onHistory)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalCalcColors.current
    val bg by animateColorAsState(if (selected) c.operatorKey else Color.Transparent, label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onOperatorKey else c.textSecondary, label = "chipFg")
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        BasicText(label, style = TextStyle(fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.05.em, color = fg))
    }
}

/** A dot-matrix clock face; turns into the keypad's "close" state when history is open. */
@Composable
private fun HistoryButton(open: Boolean, onClick: () -> Unit) {
    val c = LocalCalcColors.current
    val bg by animateColorAsState(if (open) c.operatorKey else Color.Transparent, label = "histBg")
    val fg by animateColorAsState(if (open) c.onOperatorKey else c.textSecondary, label = "histFg")
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (open) Color.Transparent else c.divider, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (open) "Close history" else "History" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val r = size.minDimension / 2
            val dot = 1.1.dp.toPx()
            for (i in 0 until 12) {
                val a = Math.toRadians(i * 30.0)
                drawCircle(fg, dot, center + Offset((kotlin.math.sin(a) * r * 0.9).toFloat(), (-kotlin.math.cos(a) * r * 0.9).toFloat()))
            }
            val stroke = 1.6.dp.toPx()
            drawLine(fg, center, center + Offset(0f, -r * 0.5f), stroke, StrokeCap.Round)
            drawLine(fg, center, center + Offset(r * 0.38f, 0f), stroke, StrokeCap.Round)
            drawCircle(AccentRed, dot * 1.3f, center)
        }
    }
}

@Composable
private fun Display(state: CalcState, compact: Boolean, modifier: Modifier = Modifier) {
    val c = LocalCalcColors.current
    val shake = remember { Animatable(0f) }
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(state.error) {
        if (state.error != null) for (x in listOf(16f, -13f, 9f, -5f, 0f)) shake.animateTo(x, tween(45))
    }
    LaunchedEffect(state.evaluated) {
        if (state.evaluated) {
            reveal.snapTo(0f)
            reveal.animateTo(1f, tween(280))
        }
    }

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .dotGrid(c.dotGrid)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            BasicText(state.angle.name, style = caption(c.textSecondary))
            if (state.inverse) {
                Spacer(Modifier.width(10.dp))
                BasicText("2ND", style = caption(c.accent))
            }
        }

        val main = state.expression.ifEmpty { "0" }
        val base = if (compact) 48f else 80f
        // Doto glyphs are about 0.62em wide: shrink until the line fits, then let it wrap.
        val fit = maxWidth.value / (main.length * 0.62f)
        val target = minOf(base, fit).coerceAtLeast(base * 0.42f)
        val fontSize by animateFloatAsState(target, spring(stiffness = Spring.StiffnessMediumLow), label = "displaySize")
        val density = LocalDensity.current

        Column(Modifier.align(Alignment.BottomEnd).fillMaxWidth(), horizontalAlignment = Alignment.End) {
            if (state.evaluated && state.previousExpression.isNotEmpty()) {
                BasicText(
                    state.previousExpression + " =",
                    style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = if (compact) 15.sp else 19.sp, color = c.textSecondary, textAlign = TextAlign.End),
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = reveal.value },
                )
                Spacer(Modifier.height(4.dp))
            }
            BasicText(
                main,
                style = TextStyle(
                    fontFamily = DotFamily, fontWeight = FontWeight.Bold,
                    fontSize = fontSize.sp, lineHeight = (fontSize * 1.08f).sp,
                    color = if (state.tokens.isEmpty()) c.textTertiary else c.textPrimary,
                    textAlign = TextAlign.End,
                ),
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(with(density) { shake.value.dp.roundToPx() }, 0) }
                    .graphicsLayer {
                        translationY = (1f - reveal.value) * 28.dp.toPx()
                        alpha = 0.35f + 0.65f * reveal.value
                    }
                    .semantics { contentDescription = main },
            )
            val sub = state.error?.message?.uppercase() ?: state.preview?.let { "= $it" }
            BasicText(
                sub ?: " ",
                style = TextStyle(
                    fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = if (compact) 18.sp else 24.sp,
                    letterSpacing = 0.03.em, color = if (state.error != null) c.accent else c.textSecondary,
                    textAlign = TextAlign.End,
                ),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun caption(color: Color) = TextStyle(
    fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.06.em, color = color,
)

/** The faint dot-matrix screen behind the display. */
private fun Modifier.dotGrid(color: Color) = drawBehind {
    val step = 9.dp.toPx()
    val r = 0.9.dp.toPx()
    var y = step / 2
    while (y < size.height) {
        var x = step / 2
        while (x < size.width) {
            drawCircle(color, r, Offset(x, y))
            x += step
        }
        y += step
    }
}

@Composable
private fun KeyGrid(
    rows: List<List<Pad>>,
    keyHeight: Dp,
    gap: Dp,
    onPress: (Key) -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { pad ->
                    CalcKey(
                        pad = pad,
                        height = keyHeight,
                        onPress = onPress,
                        // Holding DEL clears everything.
                        onLongPress = if (pad.key == Key.Backspace) onClear else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalcKey(
    pad: Pad,
    height: Dp,
    onPress: (Key) -> Unit,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = LocalCalcColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "keyScale")

    val (bg, fg) = when (pad.style) {
        KeyStyle.Digit -> c.digitKey to c.textPrimary
        KeyStyle.Function -> c.functionKey to c.textPrimary
        KeyStyle.Operator -> c.operatorKey to c.onOperatorKey
        KeyStyle.Equals -> c.accent to c.onAccent
        KeyStyle.Sci -> c.sciKey to (if (pad.active) c.accent else c.textPrimary)
    }
    val fill by animateColorAsState(if (pressed) lerp(bg, fg, 0.18f) else bg, tween(90), label = "keyFill")
    val shape = RoundedCornerShape(50)

    val density = LocalDensity.current
    val labelSize: TextUnit = with(density) {
        when {
            pad.style == KeyStyle.Sci -> min(height * 0.36f, 19.dp).toSp()
            pad.label.length > 1 -> min(height * 0.30f, 24.dp).toSp()
            else -> min(height * 0.40f, 36.dp).toSp()
        }
    }

    Box(
        modifier
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .border(1.dp, if (pad.style == KeyStyle.Sci) (if (pad.active) c.accent else c.divider) else Color.Transparent, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = onLongPress,
                onClick = { onPress(pad.key) },
            )
            .semantics { contentDescription = pad.description },
        contentAlignment = Alignment.Center,
    ) {
        if (pad.key == Key.Backspace) {
            BackspaceIcon(fg, Modifier.size(with(density) { labelSize.toDp() } * 1.15f))
        } else {
            BasicText(
                pad.label,
                style = TextStyle(
                    fontFamily = if (pad.dotLabel) DotFamily else FontFamily.SansSerif,
                    fontWeight = if (pad.dotLabel) FontWeight.Bold else FontWeight.Normal,
                    fontSize = labelSize,
                    letterSpacing = if (pad.dotLabel && pad.label.length > 1) 0.04.em else 0.em,
                    color = fg,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BackspaceIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height * 0.72f
        val top = (size.height - h) / 2
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val outline = Path().apply {
            moveTo(w * 0.30f, top)
            lineTo(w, top)
            lineTo(w, top + h)
            lineTo(w * 0.30f, top + h)
            lineTo(0f, top + h / 2)
            close()
        }
        drawPath(outline, color, style = stroke)
        val cx = w * 0.63f
        val cy = top + h / 2
        val d = h * 0.2f
        drawLine(color, Offset(cx - d, cy - d), Offset(cx + d, cy + d), stroke.width, StrokeCap.Round)
        drawLine(color, Offset(cx - d, cy + d), Offset(cx + d, cy - d), stroke.width, StrokeCap.Round)
    }
}

@Composable
private fun HistoryPanel(
    history: List<HistoryEntry>,
    onRecall: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalCalcColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .border(1.dp, c.divider, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("HISTORY", style = caption(c.textSecondary))
            Spacer(Modifier.weight(1f))
            if (history.isNotEmpty()) {
                BasicText(
                    "CLEAR",
                    style = caption(c.accent),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClear).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(
                    "NOTHING HERE YET",
                    style = TextStyle(fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 0.05.em, color = c.textTertiary),
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(history) { entry ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onRecall(entry) }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        BasicText(
                            entry.expression,
                            style = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, color = c.textSecondary, textAlign = TextAlign.End),
                        )
                        BasicText(
                            "= " + entry.result,
                            style = TextStyle(fontFamily = DotFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.textPrimary, textAlign = TextAlign.End),
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
                }
            }
        }
    }
}
