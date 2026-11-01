package com.centimusic.calculator.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

enum class AngleMode { DEG, RAD }

/** Why an expression has no value. [message] is shown under the display. */
enum class CalcError(val message: String) {
    DivideByZero("Can't divide by 0"),
    Domain("Not a number"),
    Overflow("Too large"),
    Syntax("Incomplete"),
}

sealed interface EvalResult {
    data class Value(val value: Double) : EvalResult
    data class Failure(val error: CalcError) : EvalResult
}

private class EvalException(val error: CalcError) : RuntimeException(error.message)

/**
 * Token vocabulary shared by [Calculator] and [Evaluator]. A number token is ASCII ("12.5", "-3",
 * "1.2e20"); a lone "-" is a minus sign still waiting for its number (it negates what follows).
 * The binary minus is the separate [MINUS] glyph so the two never collide.
 */
object Tok {
    const val PLUS = "+"
    const val MINUS = "−"
    const val TIMES = "×"
    const val DIVIDE = "÷"
    const val POWER = "^"
    const val OPEN = "("
    const val CLOSE = ")"
    const val PI = "π"
    const val E = "e"
    const val NEG = "-"

    const val PERCENT = "%"
    const val FACTORIAL = "!"
    const val SQUARE = "²"
    const val CUBE = "³"
    const val RECIPROCAL = "⁻¹"

    val binary = setOf(PLUS, MINUS, TIMES, DIVIDE, POWER)
    val postfix = setOf(PERCENT, FACTORIAL, SQUARE, CUBE, RECIPROCAL)
    val constants = setOf(PI, E)

    /** Function tokens carry their own opening parenthesis. */
    val functions = mapOf(
        "sin(" to "sin(", "cos(" to "cos(", "tan(" to "tan(",
        "asin(" to "sin⁻¹(", "acos(" to "cos⁻¹(", "atan(" to "tan⁻¹(",
        "ln(" to "ln(", "log(" to "log(", "√(" to "√(", "∛(" to "∛(",
    )

    fun isNumber(t: String) = t.isNotEmpty() && t != NEG && (t[0].isDigit() || t[0] == '.' || t[0] == '-')
    fun isFunction(t: String) = t in functions
    fun opensGroup(t: String) = t == OPEN || isFunction(t)

    /** The token can end an operand, so a binary operator or postfix may follow it. */
    fun endsOperand(t: String) = isNumber(t) && t.last() != '-' || t == CLOSE || t in constants || t in postfix
}

/** Recursive-descent evaluator over [Tok] tokens. Missing closing parentheses are implied. */
class Evaluator(private val angle: AngleMode) {
    private lateinit var tokens: List<String>
    private var pos = 0

    fun evaluate(input: List<String>): EvalResult {
        // A trailing operator or dangling minus is ignored, like most pocket calculators do.
        var end = input.size
        while (end > 0 && (input[end - 1] in Tok.binary || input[end - 1] == Tok.NEG)) end--
        tokens = input.subList(0, end)
        pos = 0
        if (tokens.isEmpty()) return EvalResult.Failure(CalcError.Syntax)
        return try {
            val v = expression()
            if (pos != tokens.size) throw EvalException(CalcError.Syntax)
            checked(v)
            EvalResult.Value(if (v == 0.0) 0.0 else v) // no "-0"
        } catch (e: EvalException) {
            EvalResult.Failure(e.error)
        }
    }

    private fun peek(): String? = tokens.getOrNull(pos)
    private fun next(): String = tokens.getOrNull(pos++) ?: throw EvalException(CalcError.Syntax)

    private fun expression(): Double {
        var v = term()
        while (true) {
            when (peek()) {
                Tok.PLUS -> { pos++; v = checked(v + term()) }
                Tok.MINUS -> { pos++; v = checked(v - term()) }
                else -> return v
            }
        }
    }

    private fun term(): Double {
        var v = unary()
        while (true) {
            val t = peek() ?: return v
            when {
                t == Tok.TIMES -> { pos++; v = checked(v * unary()) }
                t == Tok.DIVIDE -> {
                    pos++
                    val d = unary()
                    if (d == 0.0) throw EvalException(CalcError.DivideByZero)
                    v = checked(v / d)
                }
                // Implicit multiplication: 2π, 3(4), 2sin(30), (1)(2).
                startsOperand(t) -> v = checked(v * unary())
                else -> return v
            }
        }
    }

    private fun startsOperand(t: String) =
        Tok.isNumber(t) || t == Tok.NEG || t in Tok.constants || Tok.opensGroup(t)

    private fun unary(): Double = if (peek() == Tok.NEG) { pos++; -unary() } else power()

    private fun power(): Double {
        val base = postfix()
        if (peek() != Tok.POWER) return base
        pos++
        val exp = unary() // right-associative: 2^3^2 = 2^9
        return pow(base, exp)
    }

    private fun postfix(): Double {
        var v = primary()
        while (true) {
            v = when (peek()) {
                Tok.PERCENT -> v / 100
                Tok.FACTORIAL -> factorial(v)
                Tok.SQUARE -> pow(v, 2.0)
                Tok.CUBE -> pow(v, 3.0)
                Tok.RECIPROCAL -> if (v == 0.0) throw EvalException(CalcError.DivideByZero) else 1 / v
                else -> return v
            }
            pos++
        }
    }

    private fun primary(): Double {
        val t = next()
        return when {
            Tok.isNumber(t) -> t.toDoubleOrNull() ?: throw EvalException(CalcError.Syntax)
            t == Tok.PI -> Math.PI
            t == Tok.E -> Math.E
            t == Tok.OPEN -> group()
            Tok.isFunction(t) -> apply(t, group())
            else -> throw EvalException(CalcError.Syntax)
        }
    }

    /** Contents of a parenthesised group; the closing parenthesis may be left off at the end. */
    private fun group(): Double {
        val v = expression()
        when (peek()) {
            Tok.CLOSE -> pos++
            null -> Unit
            else -> throw EvalException(CalcError.Syntax)
        }
        return v
    }

    private fun apply(fn: String, x: Double): Double = checked(
        when (fn) {
            "sin(" -> sin(x)
            "cos(" -> cos(x)
            "tan(" -> tan(x)
            "asin(" -> fromRadians(domain(kotlin.math.asin(x)))
            "acos(" -> fromRadians(domain(kotlin.math.acos(x)))
            "atan(" -> fromRadians(kotlin.math.atan(x))
            "ln(" -> if (x <= 0) throw EvalException(CalcError.Domain) else kotlin.math.ln(x)
            "log(" -> if (x <= 0) throw EvalException(CalcError.Domain) else log10(x)
            "√(" -> if (x < 0) throw EvalException(CalcError.Domain) else kotlin.math.sqrt(x)
            "∛(" -> Math.cbrt(x)
            else -> throw EvalException(CalcError.Syntax)
        },
    )

    // Degrees are reduced first so the textbook angles come out exact: sin(180) = 0, cos(90) = 0.
    private fun sin(x: Double): Double {
        if (angle == AngleMode.RAD) return chop(kotlin.math.sin(x))
        val r = ((x % 360) + 360) % 360
        return when (r) {
            0.0, 180.0 -> 0.0
            90.0 -> 1.0
            270.0 -> -1.0
            else -> chop(kotlin.math.sin(Math.toRadians(r)))
        }
    }

    private fun cos(x: Double): Double = if (angle == AngleMode.RAD) chop(kotlin.math.cos(x)) else sin(x + 90)

    private fun tan(x: Double): Double {
        if (angle == AngleMode.RAD) {
            val c = kotlin.math.cos(x)
            if (abs(c) < 1e-15) throw EvalException(CalcError.Domain)
            return chop(kotlin.math.tan(x))
        }
        val r = ((x % 180) + 180) % 180
        return when (r) {
            0.0 -> 0.0
            90.0 -> throw EvalException(CalcError.Domain)
            else -> chop(kotlin.math.tan(Math.toRadians(r)))
        }
    }

    private fun fromRadians(r: Double) = if (angle == AngleMode.DEG) Math.toDegrees(r) else r

    private fun pow(base: Double, exp: Double): Double {
        if (base == 0.0 && exp < 0) throw EvalException(CalcError.DivideByZero)
        // Odd roots of negatives: (-8)^(1/3) = -2 rather than NaN.
        if (base < 0 && exp != floor(exp)) {
            val inv = 1 / exp
            if (abs(inv - Math.rint(inv)) < 1e-9 && Math.rint(inv).toLong() % 2L != 0L) {
                return checked(-(-base).pow(exp))
            }
            throw EvalException(CalcError.Domain)
        }
        return checked(base.pow(exp))
    }

    private fun factorial(x: Double): Double {
        if (x < 0 || x != floor(x)) throw EvalException(CalcError.Domain)
        if (x > 170) throw EvalException(CalcError.Overflow)
        var r = 1.0
        for (i in 2..x.toInt()) r *= i
        return r
    }

    private fun domain(v: Double) = if (v.isNaN()) throw EvalException(CalcError.Domain) else v

    private fun checked(v: Double): Double = when {
        v.isNaN() -> throw EvalException(CalcError.Domain)
        v.isInfinite() -> throw EvalException(CalcError.Overflow)
        else -> v
    }

    private fun chop(v: Double) = if (abs(v) < 1e-14) 0.0 else v
}

object NumberFormat {
    private const val SIGNIFICANT = 12

    /**
     * A result as a number token: rounded to 12 significant digits, plain between 1e-9 and 1e15,
     * otherwise scientific ("1.5e20"). Parses back with [String.toDouble].
     */
    fun format(v: Double): String {
        if (v == 0.0) return "0"
        val a = abs(v)
        if (a >= 1e15 || a < 1e-9) {
            var exp = floor(log10(a)).toInt()
            var mant = BigDecimal(v / 10.0.pow(exp)).round(MathContext(10, RoundingMode.HALF_EVEN))
            if (mant.abs() >= BigDecimal.TEN) { mant = mant.movePointLeft(1); exp++ }
            return mant.stripTrailingZeros().toPlainString() + "e" + exp
        }
        return BigDecimal(v).round(MathContext(SIGNIFICANT, RoundingMode.HALF_EVEN))
            .stripTrailingZeros().toPlainString()
    }

    /** A number token for the screen: thousands separators and a real minus sign. */
    fun display(token: String): String {
        if (token == Tok.NEG) return "−"
        val negative = token.startsWith("-")
        val body = if (negative) token.substring(1) else token
        val shown = if (body.contains('e')) {
            body
        } else {
            val dot = body.indexOf('.')
            val int = if (dot >= 0) body.substring(0, dot) else body
            val frac = if (dot >= 0) body.substring(dot) else ""
            group(int) + frac
        }
        return (if (negative) "−" else "") + shown
    }

    private fun group(digits: String): String {
        if (digits.length <= 3) return digits
        val sb = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return sb.toString()
    }
}
