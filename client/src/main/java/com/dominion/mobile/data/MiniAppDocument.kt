package com.dominion.mobile.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.security.MessageDigest

data class MiniAppBlock(val id: String, val kind: String, val title: String, val text: String = "",
    val items: List<String> = emptyList(), val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(), val formula: MiniFormula? = null)

/** Closed native UI grammar: no dynamic code, scripts, network or command execution. */
data class MiniAppDocument(val title: String, val blocks: List<MiniAppBlock>, val digest: String) {
    fun storageKey(room: String, session: String, message: String): String =
        sha(listOf(room, session, message, digest).joinToString("\u0000"))

    fun validateValues(values: Map<String, String>): Map<String, String> {
        val fields = blocks.flatMap { block ->
            when (block.kind) {
                "checklist", "form", "budget", "number" -> block.items.indices.map { index -> (block.id + ":" + index) to block }
                "choice" -> listOf((block.id + ":0") to block)
                "editable_table" -> block.rows.flatMapIndexed { row, cells ->
                    cells.indices.map { col -> (block.id + ":" + row + ":" + col) to block }
                }
                else -> emptyList()
            }
        }.toMap()
        require(values.size <= fields.size && values.keys.all { it in fields })
        return values.mapValues { (key, value) ->
            val block = fields.getValue(key)
            when (block.kind) {
                "checklist" -> value.also { require(it in setOf("true", "false")) }
                "budget" -> value.also { require(it.isEmpty() || money(it) != null) }
                "number" -> value.also { require(it.isEmpty() || decimal(it) != null) }
                "choice" -> value.also { require(it.isEmpty() || it in block.items) }
                else -> value.also { require(it.length <= 300) }
            }
        }
    }

    fun total(block: MiniAppBlock, values: Map<String, String>): BigDecimal {
        require(block.kind == "budget")
        return block.items.indices.fold(BigDecimal.ZERO) { sum, index ->
            sum.add(money(values[block.id + ":" + index].orEmpty()) ?: BigDecimal.ZERO)
        }
    }

    fun calculated(values: Map<String,String>): Map<String, BigDecimal?> {
        val available = mutableMapOf<String,BigDecimal?>()
        for (block in blocks) when (block.kind) {
            "budget", "number" -> block.items.indices.forEach { index ->
                val key = block.id + ":" + index
                available[key] = if (block.kind == "budget") money(values[key].orEmpty())
                    else decimal(values[key].orEmpty())
            }
            "metric" -> available[block.id] = requireNotNull(block.formula).compute { available[it] }
        }
        return blocks.filter { it.kind == "metric" }.associate { it.id to available[it.id] }
    }

    fun result(values: Map<String, String>): String {
        val valid = validateValues(values)
        return JsonObject().apply {
            addProperty("schema", "dominion-miniapp-result/v1")
            addProperty("title", title)
            addProperty("documentDigest", digest)
            add("values", JsonObject().apply {
                valid.toSortedMap().forEach { (key, value) -> addProperty(key, value) }
            })
            add("metrics", JsonObject().apply {
                calculated(valid).forEach { (key,value) ->
                    if (value == null) add(key,com.google.gson.JsonNull.INSTANCE)
                    else addProperty(key,value.toPlainString())
                }
            })
            add("totalsKrw", JsonObject().apply {
                blocks.filter { it.kind == "budget" }.forEach { addProperty(it.id, total(it,valid).toPlainString()) }
            })
        }.toString()
    }

    companion object {
        private const val SCHEMA = "dominion-miniapp/v1"
        private fun sha(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

        fun money(value: String): BigDecimal? = value.takeIf { it.matches(Regex("-?[0-9]{1,12}")) }
            ?.toBigDecimalOrNull()

        fun decimal(value: String): BigDecimal? = value.takeIf {
            it.matches(Regex("-?[0-9]{1,12}(\\.[0-9]{1,6})?"))
        }?.toBigDecimalOrNull()

        private fun string(element: JsonElement?, max: Int): String {
            require(element != null && element.isJsonPrimitive && element.asJsonPrimitive.isString)
            return element.asString.also { require(it.length <= max && it.isNotBlank()) }
        }
        private fun array(element: JsonElement?, max: Int): JsonArray {
            require(element != null && element.isJsonArray && element.asJsonArray.size() in 1..max)
            return element.asJsonArray
        }
        private fun keys(row: JsonObject, allowed: Set<String>) { require(row.keySet().all { it in allowed }) }
        fun parse(json: String): MiniAppDocument {
            require(json.length in 1..65536)
            val row = JsonParser.parseString(json).asJsonObject
            keys(row, setOf("schema", "title", "blocks"))
            val schema = string(row.get("schema"),32)
            require(schema in setOf(SCHEMA,"dominion-miniapp/v2"))
            val advanced = schema.endsWith("/v2")
            val title = string(row.get("title"), 120)
            var cells = 0
            val numericReferences = mutableSetOf<String>()
            val blocks = array(row.get("blocks"), 24).map { entry ->
                val block = entry.asJsonObject
                val id = string(block.get("id"), 32)
                require(id.matches(Regex("[A-Za-z0-9_-]{1,32}")))
                val kind = string(block.get("kind"), 24)
                val label = string(block.get("title"), 120)
                val base = setOf("id", "kind", "title")
                when (kind) {
                    "text" -> {
                        keys(block, base + "text")
                        MiniAppBlock(id, kind, label, text = string(block.get("text"), 4000))
                    }
                    "checklist", "form", "budget", "number", "choice" -> {
                        require(advanced || kind !in setOf("number","choice"))
                        keys(block, base + "items")
                        val items = array(block.get("items"), 32).map { string(it, 160) }
                        cells += items.size
                        if (kind in setOf("number","budget"))
                            items.indices.forEach { numericReferences.add(id + ":" + it) }
                        if (kind == "choice") require(items.distinct().size == items.size)
                        MiniAppBlock(id, kind, label, items = items)
                    }
                    "table", "editable_table" -> {
                        require(advanced || kind == "table")
                        keys(block, base + setOf("columns","rows"))
                        val columns = array(block.get("columns"), 8).map { string(it, 100) }
                        val rows = array(block.get("rows"), 100).map {
                            array(it, columns.size).map { cell ->
                                if (kind == "editable_table" && cell.isJsonPrimitive &&
                                    cell.asJsonPrimitive.isString && cell.asString.isEmpty()) ""
                                else string(cell,300)
                            }
                                .also { values -> require(values.size == columns.size) }
                        }
                        cells += columns.size * rows.size
                        MiniAppBlock(id, kind, label, columns = columns, rows = rows)
                    }
                    "metric" -> {
                        require(advanced)
                        keys(block,base + setOf("operation","operands","unit"))
                        val formula = MiniFormula(string(block.get("operation"),24),
                            array(block.get("operands"),16).map { string(it,48) },
                            block.get("unit")?.let { string(it,20) }.orEmpty())
                        formula.validate(numericReferences)
                        numericReferences.add(id)
                        MiniAppBlock(id,kind,label,formula=formula)
                    }
                    else -> error("Unsupported native block")
                }
            }
            require(blocks.map { it.id }.distinct().size == blocks.size && cells <= 600)
            return MiniAppDocument(title, blocks, sha(json.trim()))
        }

        fun parseOrNull(json: String) = runCatching { parse(json) }.getOrNull()
        fun fromMessage(text: String): MiniAppDocument? {
            if (text.length > 100000) return null
            val marker = 96.toChar().toString().repeat(3)
            val fence = Regex("(?s)" + marker + "dominion-miniapp[ \\t]*\\r?\\n(.*?)\\r?\\n" + marker)
            val matches = fence.findAll(text).take(2).toList()
            return when {
                matches.size == 1 -> parseOrNull(matches.single().groupValues[1])
                matches.isEmpty() && text.trimStart().startsWith("{") -> parseOrNull(text.trim())
                else -> null
            }
        }

        fun prompt(request: String): String = "사용자 요구: " + request.take(4000) + "\n" + """
            A-4 네이티브 미니앱으로 제작해 주세요. 별도 웹 서버나 실행 코드를 만들지 말고,
            아래 스키마의 JSON을 dominion-miniapp 코드 펜스 하나로 출력하세요.
            {"schema":"dominion-miniapp/v2","title":"앱 이름","blocks":[
              {"id":"intro","kind":"text","title":"안내","text":"설명"},
              {"id":"tasks","kind":"checklist","title":"할 일","items":["항목 1","항목 2"]},
              {"id":"data","kind":"table","title":"자료","columns":["항목","내용"],"rows":[["예시","값"]]},
              {"id":"budget","kind":"budget","title":"예산 원화 계산","items":["교통","식비"]},
              {"id":"input","kind":"form","title":"입력","items":["메모","목표"]},
              {"id":"choice","kind":"choice","title":"유형","items":["기본","상세"]},
              {"id":"numbers","kind":"number","title":"수치","items":["수량","단가"]},
              {"id":"subtotal","kind":"metric","title":"계산 결과","operation":"multiply","operands":["numbers:0","numbers:1"],"unit":"원"},
              {"id":"records","kind":"editable_table","title":"기록","columns":["이름","내용"],"rows":[["",""],["",""]]}
            ]}
            필요한 블록만 선택하고 고유 id를 사용하세요. 확인하지 않은 데이터를 실제 값으로 꾸며내지 마세요.
            budget은 원 단위 정수, number는 소수점 6자리까지 지원합니다.
            metric은 앞서 정의한 number/budget의 id:인덱스, 앞선 metric id 또는 숫자 상수를 참조합니다.
            operation은 sum, subtract, multiply, divide, average, min, max 중 하나입니다.
            choice는 한 항목 선택, editable_table은 실제 셀 입력을 저장합니다.
            스크립트·외부 실행·HTML은 지원하지 않습니다.
            최대 24개 블록, 표·입력 항목 합계 600개, 각 표 8열 100행 이하입니다.
        """.trimIndent()
    }
}
