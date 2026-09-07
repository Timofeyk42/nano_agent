package com.example.nanoagent.model

import java.text.DecimalFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AgentTools {
    fun execute(call: AgentToolCall): String = when (call) {
        AgentToolCall.CurrentTime -> ZonedDateTime.now().format(
            DateTimeFormatter.ofPattern("EEEE, d MMMM, HH:mm", Locale("ru"))
        )
        is AgentToolCall.Calculate -> "${call.expression} = ${format(NumberExpression(call.expression).evaluate())}"
    }

    private fun format(value: Double): String = DecimalFormat("0.##########").format(value)
}

/** Small parser for the arithmetic tool; it accepts only numbers, parentheses and + - * /. */
private class NumberExpression(private val source: String) {
    private var index = 0

    fun evaluate(): Double {
        val value = expression()
        skipWhitespace()
        require(index == source.length) { "Недопустимый символ в выражении" }
        return value
    }

    private fun expression(): Double {
        var value = term()
        while (true) {
            skipWhitespace()
            value = when {
                consume('+') -> value + term()
                consume('-') -> value - term()
                else -> return value
            }
        }
    }

    private fun term(): Double {
        var value = factor()
        while (true) {
            skipWhitespace()
            value = when {
                consume('*') -> value * factor()
                consume('/') -> value / factor().also { require(it != 0.0) { "Деление на ноль" } }
                else -> return value
            }
        }
    }

    private fun factor(): Double {
        skipWhitespace()
        if (consume('-')) return -factor()
        if (consume('(')) {
            val value = expression()
            require(consume(')')) { "Не закрыта скобка" }
            return value
        }
        val start = index
        while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
        require(start != index) { "Ожидалось число" }
        return source.substring(start, index).toDouble()
    }

    private fun consume(expected: Char): Boolean {
        skipWhitespace()
        return if (index < source.length && source[index] == expected) { index++; true } else false
    }

    private fun skipWhitespace() { while (index < source.length && source[index].isWhitespace()) index++ }
}
