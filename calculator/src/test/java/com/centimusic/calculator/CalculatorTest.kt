package com.centimusic.calculator

import com.centimusic.calculator.engine.AngleMode
import com.centimusic.calculator.engine.CalcError
import com.centimusic.calculator.engine.CalcState
import com.centimusic.calculator.engine.Calculator
import com.centimusic.calculator.engine.Key
import com.centimusic.calculator.engine.NumberFormat
import com.centimusic.calculator.engine.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {

    /** Types a key sequence: digits, ".", operators, and named keys in braces like {sin} or {=}. */
    private fun type(input: String, start: CalcState = CalcState()): CalcState {
        var s = start
        var i = 0
        while (i < input.length) {
            val c = input[i]
            val key: Key = when {
                c == '{' -> {
                    val end = input.indexOf('}', i)
                    val name = input.substring(i + 1, end)
                    i = end
                    named(name)
                }
                c.isDigit() -> Key.Digit(c)
                c == '.' -> Key.Dot
                c == '+' -> Key.Operator(Tok.PLUS)
                c == '-' -> Key.Operator(Tok.MINUS)
                c == '*' -> Key.Operator(Tok.TIMES)
                c == '/' -> Key.Operator(Tok.DIVIDE)
                c == '^' -> Key.Operator(Tok.POWER)
                c == '(' -> Key.Open
                c == ')' -> Key.Close
                c == '%' -> Key.Postfix(Tok.PERCENT)
                c == '!' -> Key.Postfix(Tok.FACTORIAL)
                c == '=' -> Key.Equals
                else -> error("unknown key $c")
            }
            s = Calculator.reduce(s, key)
            i++
        }
        return s
    }

    private fun named(name: String): Key = when (name) {
        "sq" -> Key.Postfix(Tok.SQUARE)
        "inv" -> Key.Postfix(Tok.RECIPROCAL)
        "pi" -> Key.Constant(Tok.PI)
        "e" -> Key.Constant(Tok.E)
        "neg" -> Key.Negate
        "del" -> Key.Backspace
        "ac" -> Key.Clear
        "rad" -> Key.ToggleAngle
        "exp" -> Key.PowerOf("e")
        "ten" -> Key.PowerOf("10")
        else -> Key.Function("$name(")
    }

    private fun result(input: String, start: CalcState = CalcState()): String {
        val s = type(input, start)
        assertTrue("expected an answer for $input, got ${s.error}", s.evaluated)
        return s.expression
    }

    @Test fun basicArithmetic() {
        assertEquals("5", result("2+3="))
        assertEquals("14", result("2+3*4="))
        assertEquals("20", result("(2+3)*4="))
        assertEquals("2.5", result("10/4="))
        assertEquals("0.3", result(".1+.2="))
        assertEquals("1,000,000", result("1000*1000="))
    }

    @Test fun operatorsReplaceEachOther() {
        assertEquals("6", result("3+*2="))
        assertEquals("1", result("3*-2+7="))
    }

    @Test fun negativeNumbers() {
        assertEquals("−5", result("-5="))
        assertEquals("4", result("2{neg}^2=")) // (−2)² = 4
        assertEquals("−4", result("-(2)^2="))
        assertEquals("5", result("2-3{neg}="))
        assertEquals("(−3)", type("2*3{neg}").expression.substringAfter("×"))
    }

    @Test fun answerContinues() {
        val s = type("2+3=")
        assertEquals("15", result("*3=", s))
        assertEquals("7", result("7=", s)) // a digit starts over
        assertEquals("−5", result("{neg}=", s))
    }

    @Test fun percentAndFactorial() {
        assertEquals("0.5", result("50%="))
        assertEquals("120", result("5!="))
        assertEquals(CalcError.Domain, type("2.5!=").error)
    }

    @Test fun powersAndRoots() {
        assertEquals("512", result("2^3^2="))
        assertEquals("9", result("3{sq}="))
        assertEquals("0.25", result("4{inv}="))
        assertEquals("3", result("{√}9="))
        assertEquals("−2", result("{∛}8{neg}="))
        assertEquals("−2", result("8{neg}^(1/3)="))
    }

    @Test fun trigInDegreesIsExact() {
        assertEquals("0.5", result("{sin}30="))
        assertEquals("0", result("{sin}180="))
        assertEquals("0", result("{cos}90="))
        assertEquals("1", result("{tan}45="))
        assertEquals(CalcError.Domain, type("{tan}90=").error)
        assertEquals("30", result("{asin}.5="))
    }

    @Test fun trigInRadians() {
        val rad = Calculator.reduce(CalcState(), Key.ToggleAngle)
        assertEquals(AngleMode.RAD, rad.angle)
        assertEquals("0", result("{sin}{pi}=", rad))
        assertEquals("−1", result("{cos}{pi}=", rad))
    }

    @Test fun logs() {
        assertEquals("3", result("{log}1000="))
        assertEquals("1", result("{ln}{e}="))
        assertEquals("1", result("{exp}0="))
        assertEquals("2×10^(3", type("2{ten}3").expression)
        assertEquals("2,000", result("2{ten}3="))
        assertEquals(CalcError.Domain, type("{ln}0=").error)
    }

    @Test fun implicitMultiplicationAndAutoClose() {
        assertEquals("6.28318530718", result("2{pi}="))
        assertEquals("12", result("3(4="))
        assertEquals("1", result("2{sin}30="))
    }

    @Test fun errors() {
        val s = type("5/0=")
        assertEquals(CalcError.DivideByZero, s.error)
        assertEquals("5÷0", s.expression)
        assertNull(type("5/0=1").error) // next key clears it
        assertEquals(CalcError.Overflow, type("171!=").error)
    }

    @Test fun backspaceAndClear() {
        assertEquals("12", type("123{del}").expression)
        assertEquals("12+", type("12+3{del}").expression)
        assertEquals("", type("12{ac}").expression)
        assertEquals("", type("2+2={del}").expression)
    }

    @Test fun inputGuards() {
        assertEquals("1.5", type("1..5").expression)
        assertEquals("5", type("005").expression)
        assertEquals("2", type("2)").expression) // nothing to close
        assertEquals("", type("*").expression)
    }

    @Test fun preview() {
        assertEquals("7", type("3+4").preview)
        assertNull(type("3").preview)
        assertNull(type("3+4=").preview)
    }

    @Test fun historyRecordsClosedExpression() {
        val s = type("2*(3+4=")
        assertEquals("2×(3+4)", s.history.first().expression)
        assertEquals("14", s.history.first().result)
        val recalled = Calculator.recall(CalcState(), s.history.first())
        assertEquals("28", result("*2=", recalled))
    }

    @Test fun formatting() {
        assertEquals("1e20", NumberFormat.format(1e20))
        assertEquals("1.5e-12", NumberFormat.format(1.5e-12))
        assertEquals("0.333333333333", NumberFormat.format(1.0 / 3))
        assertEquals("−1,234.5", NumberFormat.display("-1234.5"))
    }
}
