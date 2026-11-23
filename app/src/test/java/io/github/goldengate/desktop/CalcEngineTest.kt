package io.github.goldengate.desktop

import io.github.goldengate.desktop.apps.CalculatorState
import io.github.goldengate.desktop.core.CalcEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcEngineTest {
    private fun ev(s: String, deg: Boolean = false) = CalcEngine.evaluate(s, deg)

    @Test fun precedenceAndParentheses() {
        assertEquals(14.0, ev("2+3*4"), 1e-9)
        assertEquals(20.0, ev("(2+3)*4"), 1e-9)
        assertEquals(512.0, ev("2^3^2"), 1e-9)
        assertEquals(-8.0, ev("-2^3"), 1e-9)
    }

    @Test fun unicodeOperatorsAndPercent() {
        assertEquals(6.0, ev("2×3"), 1e-9)
        assertEquals(2.5, ev("5÷2"), 1e-9)
        assertEquals(1.0, ev("3−2"), 1e-9)
        assertEquals(0.5, ev("50%"), 1e-9)
        assertEquals(1.0, ev("7 % 3"), 1e-9)
    }

    @Test fun functionsConstantsFactorial() {
        assertEquals(3.0, ev("sqrt(9)"), 1e-9)
        assertEquals(1.0, ev("sin(90)", deg = true), 1e-9)
        assertEquals(Math.PI, ev("π"), 1e-9)
        assertEquals(120.0, ev("5!"), 1e-9)
        assertEquals(2.0, ev("log(100)"), 1e-9)
        assertEquals(6.0, ev("2(3)"), 1e-9)
    }

    @Test fun errors() {
        assertNull(CalcEngine.tryEvaluate("1/0"))
        assertNull(CalcEngine.tryEvaluate("2+"))
        assertNull(CalcEngine.tryEvaluate("foo(2)"))
        assertNull(CalcEngine.tryEvaluate("(1+2"))
    }

    @Test fun formatting() {
        assertEquals("4", CalcEngine.format(4.0))
        assertEquals("0.3333333333", CalcEngine.format(1.0 / 3, 10))
        assertEquals("0.3", CalcEngine.format(0.1 + 0.2))
    }

    @Test fun looksLikeMath() {
        assertTrue(CalcEngine.looksLikeMath("12*4"))
        assertFalse(CalcEngine.looksLikeMath("notes"))
    }

    @Test fun calculatorStateMachine() {
        val s = CalculatorState()
        "12+3".forEach { s.input(it.toString()) }
        s.input("×"); s.input("2"); s.input("=")
        assertEquals("18", s.result)
        assertEquals("12+3×2 = 18", s.tape.first())
        s.input("+"); s.input("2"); s.input("=")
        assertEquals("20", s.result)
        s.input("AC")
        assertEquals("0", s.result)
        s.input("5"); s.input("+"); s.input("×") // operator replacement
        assertEquals("5×", s.expr)
    }
}
