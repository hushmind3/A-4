package com.dominion.mobile.ui.screens.archive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dominion.mobile.data.OfficeDocument
import com.dominion.mobile.data.OfficeFormat
import kotlinx.coroutines.*

@Composable
fun OfficeEditorPanel(format:OfficeFormat,name:String,bytes:ByteArray,onPicker:(Boolean)->Unit = {}) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var document by remember(bytes){mutableStateOf<OfficeDocument?>(null)}
    var changes by remember(bytes){mutableStateOf<Map<String,String>>(emptyMap())}
    var styles by remember(bytes){mutableStateOf<Map<String,com.dominion.mobile.data.OfficeTextStyle>>(emptyMap())}
    var filter by remember(bytes){mutableStateOf("")}
    var error by remember(bytes){mutableStateOf("")}
    var busy by remember(bytes){mutableStateOf(false)}
    var exportBytes by remember(bytes){mutableStateOf<ByteArray?>(null)}
    LaunchedEffect(bytes) {
        try{document=withContext(Dispatchers.IO){OfficeDocument.parse(format,bytes)}}
        catch(e:CancellationException){throw e}
        catch(_:Exception){error="지원되지 않거나 손상된 Office 문서입니다."}
    }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { uri:Uri? ->
        onPicker(false)
        val payload=exportBytes
        exportBytes=null
        if(uri!=null && payload!=null) {
            busy=true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        require(uri.scheme=="content")
                        requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).use { it.write(payload) }
                        val written=requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192)
                            while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=payload.size);out.write(b,0,n)}
                            out.toByteArray()
                        }
                        check(written.contentEquals(payload))
                    }
                    error="편집한 사본 저장·재읽기 확인 완료"
                } catch(e:CancellationException){throw e}
                catch(_:Exception){error="파일 저장 또는 재읽기 확인 실패"}
                finally{busy=false}
            }
        }
    }
    val doc=document
    Column(Modifier.fillMaxSize()) {
        if(doc==null) {
            if(error.isEmpty())CircularProgressIndicator() else Text(error,color=MaterialTheme.colorScheme.error)
            return@Column
        }
        Text("원본 보존 · 편집 가능한 텍스트·셀만 변경",style=MaterialTheme.typography.labelMedium)
        if(doc.truncated)Text("최대 500개 항목을 표시합니다. 나머지 원본은 유지합니다.")
        if(format==OfficeFormat.SHEET)Text("수식 원본은 보존합니다. 저장 시 지원하는 기본 계산식은 다시 계산하고, 미지원 수식의 이전 결과는 제거합니다.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(filter,{filter=it.take(150)},label={Text("문단·셀 검색")},singleLine=true,modifier=Modifier.fillMaxWidth())
        val visible=doc.fields.filter { filter.isBlank() || (it.section+" "+it.label+" "+(changes[it.id]?:it.text)).contains(filter,true) }
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(visible,key={it.id}) { field ->
                Text(field.section+" · "+field.label,style=MaterialTheme.typography.labelSmall)
                if(field.editable) OutlinedTextField(changes[field.id]?:field.text,
                    {changes=changes+(field.id to it.take(8000));error=""},enabled=!busy,
                    modifier=Modifier.fillMaxWidth(),maxLines=5,
                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=
                        if(field.numeric)androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text))
                else Text(field.text,style=MaterialTheme.typography.bodyMedium)
                if(field.styleEditable) {
                    val value=styles[field.id]?:com.dominion.mobile.data.OfficeTextStyle()
                    fun cycle(previous:Boolean?):Boolean?=when(previous){null->true;true->false;false->null}
                    Row {
                        TextButton(enabled=!busy,onClick={
                            styles=styles+(field.id to value.copy(bold=cycle(value.bold)))
                        }){Text("굵게: "+when(value.bold){true->"적용";false->"해제";null->"유지"})}
                        TextButton(enabled=!busy,onClick={
                            styles=styles+(field.id to value.copy(italic=cycle(value.italic)))
                        }){Text("기울임: "+when(value.italic){true->"적용";false->"해제";null->"유지"})}
                    }
                }
            }
        }
        if(error.isNotBlank())Text(error)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(enabled=!busy && (changes.isNotEmpty() || styles.values.any{it.bold!=null||it.italic!=null}),onClick={
            busy=true;error=""
            val snapshot=changes.toMap()
            val styleSnapshot=styles.filterValues{it.bold!=null||it.italic!=null}
            scope.launch {
                try {
                    // Each export starts from immutable source, not a previously mutated XML tree.
                    exportBytes=withContext(Dispatchers.IO){OfficeDocument.parse(format,bytes).export(snapshot,styleSnapshot)}
                    onPicker(true)
                    try { save.launch(name.substringBeforeLast('.').take(100)+"-edited."+format.extension) }
                    catch (e:Exception) { onPicker(false);throw e }
                }catch(e:CancellationException){throw e}
                catch(_:Exception){error="입력값 또는 문서 형식 검증 실패";exportBytes=null}
                finally{busy=false}
            }
        }){Text("편집한 사본 저장")}
    }
}
