package com.centimusic.calculator.engine

/** One finished calculation, newest first in [CalcState.history]. */
data class HistoryEntry(val expression: String, val result: String, val resultToken: String)

data class CalcState(
    val tokens: List<String> = emptyList(),
    /** True right after "=": the display shows the answer and the next digit starts over. */
    val evaluated: Boolean = false,
    /** The expression that produced the answer, shown small above it. */
    val previousExpression: String = "",
    val angle: AngleMode = AngleMode.DEG,
    /** "2nd": the scientific keys switch to their inverse functions. */
    val inverse: Boolean = false,
    /** Error from the last "=", if it failed. Cleared by the next key. */
    val error: CalcError? = null,
    val history: List<HistoryEntry> = emptyList(),
) {
    val expression: String get() = Calculator.display(tokens)

    /** Live result while typing, null when there is nothing worth previewing. */
    val preview: String?
        get() {
            if (evaluated || tokens.size < 2) return null
            if (tokens.size == 2 && tokens[0] == Tok.NEG) return null
            return when (val r = Evaluator(angle).evaluate(tokens)) {
                is EvalResult.Value -> NumberFormat.display(NumberFormat.format(r.value))
                is EvalResult.Failure -> null
            }
        }
}

/** Every key on the keypad. */
sealed interface Key {
    data class Digit(val d: Char) : Key
    data object Dot : Key
    data class Operator(val op: String) : Key
    data class Postfix(val op: String) : Key
    data class Function(val fn: String) : Key
    data class Constant(val c: String) : Key
    data object Open : Key
    data object Close : Key
    data object Negate : Key
    data object Equals : Key
    data object Backspace : Key
    data object Clear : Key
    data object ToggleAngle : Key
    data object ToggleInverse : Key
    /** e^x and 10^x: a base followed by "^(" so the exponent goes inside. */
    data class PowerOf(val base: String) : Key
}

/** Pure input logic: (state, key) → new state. No Android, so it is unit-tested directly. */
object Calculator {
    private const val MAX_DIGITS = 15
    private const val MAX_HISTORY = 50

    fun reduce(s: CalcState, key: Key): CalcState {
        val state = s.copy(error = null)
        return when (key) {
            Key.Clear -> state.copy(tokens = emptyList(), evaluated = false, previousExpression = "")
            Key.ToggleAngle -> state.copy(angle = if (state.angle == AngleMode.DEG) AngleMode.RAD else AngleMode.DEG)
            Key.ToggleInverse -> state.copy(inverse = !state.inverse)
            Key.Equals -> equals(state)
            Key.Backspace -> backspace(state)
            else -> edit(state, key)
        }
    }

    /** Loads a history result back into the display, ready for the next operator. */
    fun recall(s: CalcState, entry: HistoryEntry) = s.copy(
        tokens = listOf(entry.resultToken), evaluated = true, previousExpression = entry.expression, error = null,
    )

    private fun equals(s: CalcState): CalcState {
        if (s.tokens.isEmpty() || s.evaluated) return s
        return when (val r = Evaluator(s.angle).evaluate(s.tokens)) {
            is EvalResult.Failure -> s.copy(error = r.error)
            is EvalResult.Value -> {
                val token = NumberFormat.format(r.value)
                val expr = closed(s.tokens)
                val entry = HistoryEntry(display(expr), NumberFormat.display(token), token)
                s.copy(
                    tokens = listOf(token),
                    evaluated = true,
                    previousExpression = entry.expression,
                    history = (listOf(entry) + s.history).take(MAX_HISTORY),
                )
            }
        }
    }

    private fun backspace(s: CalcState): CalcState {
        if (s.evaluated) return s.copy(tokens = emptyList(), evaluated = false, previousExpression = "")
        val last = s.tokens.lastOrNull() ?: return s
        val trimmed = when {
            // A typed number loses one character; a scientific result goes all at once.
            Tok.isNumber(last) && !last.contains('e') && last.length > 1 -> s.tokens.dropLast(1) + last.dropLast(1)
            else -> s.tokens.dropLast(1)
        }
        return s.copy(tokens = trimmed)
    }

    private fun edit(s0: CalcState, key: Key): CalcState {
        // After "=", operators continue from the answer; anything that starts a new number starts over.
        val continues = key is Key.Operator || key is Key.Postfix || key is Key.Negate ||
            key == Key.Close
        val s = if (s0.evaluated && !continues) s0.copy(tokens = emptyList()) else s0
        val t = s.tokens
        val last = t.lastOrNull()
        val newTokens: List<String>? = when (key) {
            is Key.Digit -> digit(t, key.d)
            Key.Dot -> dot(t)
            is Key.Operator -> operator(t, key.op)
            is Key.Postfix -> if (last != null && Tok.endsOperand(last)) t + key.op else null
            is Key.Function -> t + key.fn
            is Key.Constant -> t + key.c
            // "2" then 10ˣ must not read as "210": write the multiplication out.
            is Key.PowerOf -> (if (last != null && Tok.endsOperand(last)) t + Tok.TIMES else t) +
                key.base + Tok.POWER + Tok.OPEN
            Key.Open -> t + Tok.OPEN
            Key.Close -> if (last != null && Tok.endsOperand(last) && openCount(t) > 0) t + Tok.CLOSE else null
            Key.Negate -> negate(t)
            else -> null
        }
        return if (newTokens == null) s0 else s.copy(tokens = newTokens, evaluated = false, previousExpression = "")
    }

    private fun digit(t: List<String>, d: Char): List<String> {
        val last = t.lastOrNull()
        if (last != null && Tok.isNumber(last) && !last.contains('e') || last == Tok.NEG) {
            val digits = last!!.count { it.isDigit() }
            if (digits >= MAX_DIGITS) return t
            val next = when (last) {
                "0" -> "$d"
                "-0" -> "-$d"
                else -> last + d
            }
            return t.dropLast(1) + next
        }
        return t + "$d"
    }

    private fun dot(t: List<String>): List<String> {
        val last = t.lastOrNull()
        return when {
            last == Tok.NEG -> t.dropLast(1) + "-0."
            last != null && Tok.isNumber(last) && !last.contains('e') ->
                if (last.contains('.')) t else t.dropLast(1) + "$last."
            last != null && Tok.isNumber(last) -> t // scientific result: leave it alone
            else -> t + "0."
        }
    }

    private fun operator(t: List<String>, op: String): List<String>? {
        val last = t.lastOrNull()
        return when {
            // A minus where no operand can precede it begins a negative number.
            last == null || Tok.opensGroup(last) -> if (op == Tok.MINUS) t + Tok.NEG else null
            last == Tok.NEG -> null
            last in Tok.binary ->
                if (op == Tok.MINUS && last != Tok.MINUS && last != Tok.PLUS) t + Tok.NEG else t.dropLast(1) + op
            Tok.endsOperand(last) -> t + op
            else -> null // e.g. "5." is still an operand; anything else is ignored
        }
    }

    /** ± flips the sign of the number being typed (or of the answer). */
    private fun negate(t: List<String>): List<String> {
        val last = t.lastOrNull() ?: return listOf(Tok.NEG)
        return when {
            last == Tok.NEG -> t.dropLast(1)
            Tok.isNumber(last) -> t.dropLast(1) + (if (last.startsWith("-")) last.substring(1) else "-$last")
            last in Tok.binary || Tok.opensGroup(last) -> t + Tok.NEG
            else -> t // after ")" or a constant there is no single number to flip
        }
    }

    private fun openCount(t: List<String>) = t.count { Tok.opensGroup(it) } - t.count { it == Tok.CLOSE }

    /** The expression with its implied closing parentheses written out, for history. */
    private fun closed(t: List<String>): List<String> {
        var end = t.size
        while (end > 0 && (t[end - 1] in Tok.binary || t[end - 1] == Tok.NEG)) end--
        val body = t.subList(0, end)
        return body + List(openCount(body).coerceAtLeast(0)) { Tok.CLOSE }
    }

    fun display(tokens: List<String>): String = buildString {
        tokens.forEachIndexed { i, tok ->
            when {
                Tok.isNumber(tok) || tok == Tok.NEG -> {
                    val shown = NumberFormat.display(tok)
                    val next = tokens.getOrNull(i + 1)
                    // Wrap a negative number when it would otherwise read as "minus x" of something.
                    val wrap = tok.startsWith("-") && tok != Tok.NEG && tok.length > 1 &&
                        (i > 0 || next == Tok.POWER || next in Tok.postfix)
                    append(if (wrap) "($shown)" else shown)
                }
                Tok.isFunction(tok) -> append(Tok.functions.getValue(tok))
                tok == Tok.POWER -> append("^")
                else -> append(tok)
            }
        }
    }
}
