package com.dominion.mobile.data
import org.junit.Assert.*
import org.junit.Test
class SheetFormulaTest {
 @Test fun evaluatesDependenciesRangesAndRejectsUnknown(){
  val engine=SheetFormula(mapOf(
   "A1" to SheetFormula.Cell(number="10".toBigDecimal()),
   "A2" to SheetFormula.Cell(number="20".toBigDecimal()),
   "B1" to SheetFormula.Cell(formula="SUM(A1:A2)*2"),
   "B2" to SheetFormula.Cell(formula="B1/3"),
   "B3" to SheetFormula.Cell(formula="OTHER(A1)"),
   "C1" to SheetFormula.Cell(formula="C2"),
   "C2" to SheetFormula.Cell(formula="C1")))
  assertEquals("60",engine.evaluate("B1")?.toPlainString())
  assertEquals("20",engine.evaluate("B2")?.toPlainString())
  assertNull(engine.evaluate("B3"));assertNull(engine.evaluate("C1"))
 }
}
