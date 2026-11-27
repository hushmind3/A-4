package com.dominion.mobile.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dominion.mobile.data.MiniAppDocument
import com.dominion.mobile.platform.ContactQr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun MiniAppDialog(document: MiniAppDocument, initial: Map<String,String>,
    save: suspend (Map<String,String>) -> Unit, onDismiss: () -> Unit, onUseResult: (String) -> Unit) {
    StopOnBackground(onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(document.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
                Box(Modifier.weight(1f)) { MiniAppPanel(document, initial, save, onUseResult) }
            }
        }
    }
}

@Composable
fun MiniAppPanel(document: MiniAppDocument, initial: Map<String,String> = emptyMap(),
    save: (suspend (Map<String,String>) -> Unit)? = null, onUseResult: ((String) -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var values by remember(document.digest) {
        mutableStateOf(runCatching { document.validateValues(initial) }.getOrDefault(emptyMap()))
    }
    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    fun set(key: String, value: String) { values = values + (key to value); notice = "" }
    Column(Modifier.fillMaxSize().padding(6.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(document.title, style = MaterialTheme.typography.titleLarge)
            Text("본체가 작성한 데이터 · 기기에서 실행되는 네이티브 화면", style = MaterialTheme.typography.labelMedium)
            document.blocks.forEach { block ->
                key(block.id) {
                    HorizontalDivider()
                    Text(block.title, style = MaterialTheme.typography.titleMedium)
                    when (block.kind) {
                        "text" -> SelectionContainer { Text(block.text) }
                        "checklist" -> {
                            val completed = block.items.indices.count { values[block.id + ":" + it] == "true" }
                            LinearProgressIndicator(progress = { completed.toFloat() / block.items.size },
                                modifier = Modifier.fillMaxWidth())
                            Text(completed.toString() + " / " + block.items.size)
                            block.items.forEachIndexed { index, label ->
                                val id = block.id + ":" + index
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = values[id] == "true", onCheckedChange = { set(id, it.toString()) })
                                    Text(label)
                                }
                            }
                        }
                        "form", "budget" -> {
                            block.items.forEachIndexed { index, label ->
                                val id = block.id + ":" + index
                                OutlinedTextField(value = values[id].orEmpty(),
                                    onValueChange = { raw ->
                                        if (block.kind == "budget") {
                                            if (raw.isEmpty() || MiniAppDocument.money(raw) != null) set(id,raw)
                                        } else set(id,raw.take(300))
                                    }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = if (block.kind == "budget")
                                        KeyboardType.Number else KeyboardType.Text), singleLine = block.kind == "budget",
                                    maxLines = if (block.kind == "budget") 1 else 3)
                            }
                            if (block.kind == "budget") Text("합계: " +
                                java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA)
                                    .format(document.total(block, values)) + "원", style = MaterialTheme.typography.titleMedium)
                        }
                        "table" -> {
                            var query by remember { mutableStateOf("") }
                            OutlinedTextField(query, { query = it.take(200) }, label = { Text("표 검색") },
                                modifier = Modifier.fillMaxWidth(), singleLine = true)
                            val rows = block.rows.filter { row -> query.isBlank() || row.any { it.contains(query,true) } }
                            Text(rows.size.toString() + " / " + block.rows.size + "행")
                            Column(Modifier.horizontalScroll(rememberScrollState())) {
                                Row { block.columns.forEach { Text(it, Modifier.width(140.dp).padding(6.dp), style = MaterialTheme.typography.labelLarge) } }
                                rows.forEach { row ->
                                    HorizontalDivider()
                                    Row { row.forEach { cell -> SelectionContainer { Text(cell, Modifier.width(140.dp).padding(6.dp)) } } }
                                }
                            }
                        }
                    }
                }
            }
            if (notice.isNotBlank()) Text(notice, style = MaterialTheme.typography.labelMedium)
        }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (save != null) TextButton(enabled = !saving, onClick = {
                val snapshot = values.toMap()
                saving = true
                scope.launch {
                    try { save(snapshot); notice = "기기에 결과 저장 완료" }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { notice = "저장 실패 · 원본 대화와 저장 한도를 확인하세요." }
                    finally { saving = false }
                }
            }) { Text("저장") }
            TextButton(enabled = !saving, onClick = {
                runCatching { ContactQr.share(context, document.result(values)) }
                    .onFailure { notice = "결과 공유 실패" }
            }) { Text("결과 공유") }
            if (onUseResult != null) TextButton(enabled = !saving, onClick = {
                runCatching { document.result(values) }.onSuccess { onUseResult(it) }
                    .onFailure { notice = "결과 형식을 확인하세요." }
            }) { Text("대화에 넣기") }
        }
    }
}
