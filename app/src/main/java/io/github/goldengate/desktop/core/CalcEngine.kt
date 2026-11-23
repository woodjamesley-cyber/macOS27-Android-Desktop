package io.github.goldengate.desktop.core

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.floor

/**
 * A small, dependency-free expression evaluator used by Calculator, Spotlight and Terminal.
 * Grammar: expr := term (('+'|'-') term)* ; term := factor (('*'|'/'|'%') factor)* ;
 * factor := unary ('^' factor)? ; unary := ('-'|'+') unary | postfix ; postfix := primary '!'?
 * primary := number | constant | func '(' expr ')' | '(' expr ')'.
 * Supports ×, ÷, − symbols, implicit multiplication like "2(3)" or "2pi", and degrees mode.
 */
object CalcEngine {
    class CalcException(message: String) : Exception(message)

    fun evaluate(input: String, degrees: Boolean = false): Double {
        val p = Parser(normalize(input), degrees)
        val v = p.parseExpr()
        p.skipWs()
        if (!p.atEnd()) throw CalcException("Unexpected '${p.peek()}'")
        if (v.isNaN()) throw CalcException("Not a number")
        if (v.isInfinite()) throw CalcException("Division by zero")
        return v
    }

    /** Returns null instead of throwing; handy for Spotlight's live math. */
    fun tryEvaluate(input: String, degrees: Boolean = false): Double? = try { evaluate(input, degrees) } catch (_: Exception) { null }

    /** Heuristic: does the text look like math (so Spotlight should show a result)? */
    fun looksLikeMath(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty() || t.length > 120) return false
        if (!t.any { it.isDigit() }) return false
        return t.any { it in "+-*/×÷−^%()!" } || t.startsWith("sqrt") || t.startsWith("sin") || t.startsWith("cos")
    }

    fun format(v: Double, maxDigits: Int = 12): String {
        if (v == floor(v) && abs(v) < 1e15) return BigDecimal(v).setScale(0, RoundingMode.HALF_UP).toPlainString()
        if (abs(v) >= 1e15 || abs(v) < 1e-9) return String.format(java.util.Locale.US, "%.6e", v)
        val bd = BigDecimal(v).round(MathContext(maxDigits, RoundingMode.HALF_UP)).stripTrailingZeros()
        return bd.toPlainString()
    }

    private fun normalize(s: String) = s.replace('×', '*').replace('÷', '/').replace('−', '-').replace("π", "pi").replace(",", "")

    private class Parser(val s: String, val degrees: Boolean) {
        var i = 0
        fun atEnd() = i >= s.length
        fun peek() = s[i]
        fun skipWs() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun eat(c: Char): Boolean { skipWs(); if (i < s.length && s[i] == c) { i++; return true }; return false }

        fun parseExpr(): Double {
            var v = parseTerm()
            while (true) {
                v = when {
                    eat('+') -> v + parseTerm()
                    eat('-') -> v - parseTerm()
                    else -> return v
                }
            }
        }

        fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                skipWs()
                v = when {
                    eat('*') -> v * parseFactor()
                    eat('/') -> { val d = parseFactor(); if (d == 0.0) throw CalcException("Division by zero"); v / d }
                    eat('%') -> {
                        // "50%" alone means 0.5; "a % b" is modulo.
                        skipWs()
                        if (atEnd() || peek() == ')' || peek() in "+-*/") v / 100.0 else { val d = parseFactor(); if (d == 0.0) throw CalcException("Division by zero"); v % d }
                    }
                    !atEnd() && (peek() == '(' || peek().isLetter()) -> v * parseFactor() // implicit multiplication
                    else -> return v
                }
            }
        }

        fun parseFactor(): Double {
            val base = parseUnary()
            return if (eat('^')) Math.pow(base, parseFactor()) else base
        }

        fun parseUnary(): Double = when {
            eat('-') -> -parseUnary()
            eat('+') -> parseUnary()
            else -> parsePostfix()
        }

        fun parsePostfix(): Double {
            var v = parsePrimary()
            while (eat('!')) v = factorial(v)
            return v
        }

        fun parsePrimary(): Double {
            skipWs()
            if (atEnd()) throw CalcException("Incomplete expression")
            val c = peek()
            if (c == '(') {
                i++
                val v = parseExpr()
                if (!eat(')')) throw CalcException("Missing ')'")
                return v
            }
            if (c.isDigit() || c == '.') {
                val start = i
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                if (i < s.length && (s[i] == 'e' || s[i] == 'E') && i + 1 < s.length && (s[i + 1].isDigit() || s[i + 1] == '-' || s[i + 1] == '+')) {
                    i++
                    if (s[i] == '-' || s[i] == '+') i++
                    while (i < s.length && s[i].isDigit()) i++
                }
                return s.substring(start, i).toDoubleOrNull() ?: throw CalcException("Bad number")
            }
            if (c.isLetter()) {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(start, i).lowercase()
                when (name) {
                    "pi" -> return Math.PI
                    "e" -> return Math.E
                }
                val arg = if (eat('(')) { val v = parseExpr(); if (!eat(')')) throw CalcException("Missing ')'"); v } else parseUnary()
                val toRad = { x: Double -> if (degrees) Math.toRadians(x) else x }
                return when (name) {
                    "sqrt" -> { if (arg < 0) throw CalcException("Not a number"); Math.sqrt(arg) }
                    "cbrt" -> Math.cbrt(arg)
                    "sin" -> Math.sin(toRad(arg))
                    "cos" -> Math.cos(toRad(arg))
                    "tan" -> Math.tan(toRad(arg))
                    "asin" -> Math.asin(arg).let { if (degrees) Math.toDegrees(it) else it }
                    "acos" -> Math.acos(arg).let { if (degrees) Math.toDegrees(it) else it }
                    "atan" -> Math.atan(arg).let { if (degrees) Math.toDegrees(it) else it }
                    "ln" -> { if (arg <= 0) throw CalcException("Not a number"); Math.log(arg) }
                    "log" -> { if (arg <= 0) throw CalcException("Not a number"); Math.log10(arg) }
                    "abs" -> abs(arg)
                    "exp" -> Math.exp(arg)
                    else -> throw CalcException("Unknown function '$name'")
                }
            }
            throw CalcException("Unexpected '$c'")
        }

        fun factorial(v: Double): Double {
            if (v < 0 || v != floor(v)) throw CalcException("Factorial needs a whole number")
            if (v > 170) throw CalcException("Too large")
            var r = 1.0
            var k = 2.0
            while (k <= v) { r *= k; k += 1.0 }
            return r
        }
    }
}
