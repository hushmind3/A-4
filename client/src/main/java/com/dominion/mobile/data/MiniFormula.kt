package com.dominion.mobile.data

import java.math.BigDecimal
import java.math.MathContext

/** Bounded declarative arithmetic, never executable source or reflection. */
data class MiniFormula(val operation: String, val operands: List<String>, val unit: String = "") {
    fun compute(resolve: (String) -> BigDecimal?): BigDecimal? = runCatching {
        val numbers = operands.map { token ->
            MiniAppDocument.decimal(token) ?: requireNotNull(resolve(token))
        }
        val math = MathContext.DECIMAL64
        val result = when (operation) {
            "sum" -> numbers.fold(BigDecimal.ZERO) { a,b -> a.add(b,math) }
            "subtract" -> numbers[0].subtract(numbers[1],math)
            "multiply" -> numbers.fold(BigDecimal.ONE) { a,b -> a.multiply(b,math) }
            "divide" -> numbers[0].divide(numbers[1],math)
            "average" -> numbers.fold(BigDecimal.ZERO) { a,b -> a.add(b,math) }
                .divide(BigDecimal(numbers.size),math)
            "min" -> numbers.minOrNull()!!
            "max" -> numbers.maxOrNull()!!
            else -> error("Unknown formula")
        }
        require(result.abs() <= BigDecimal("1000000000000000000"))
        result.stripTrailingZeros()
    }.getOrNull()

    fun validate(allowedReferences: Set<String>) {
        require(operation in setOf("sum","subtract","multiply","divide","average","min","max"))
        require(operands.size in 1..16 &&
            (operation !in setOf("subtract","divide") || operands.size == 2))
        require(operands.all { MiniAppDocument.decimal(it) != null || it in allowedReferences })
        require(unit.length <= 20 && unit.none(Char::isISOControl))
    }
}
