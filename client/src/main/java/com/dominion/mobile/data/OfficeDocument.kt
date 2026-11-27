package com.dominion.mobile.data

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.*
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

enum class OfficeFormat(val extension:String,val mime:String) {
    WORD("docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    SHEET("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    SLIDES("pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation");
    companion object {
        fun forFile(name:String,mime:String)=entries.firstOrNull { name.endsWith("."+it.extension,true) || mime==it.mime }
    }
}
data class OfficeField(val id:String,val section:String,val label:String,val text:String,
    val editable:Boolean,val numeric:Boolean=false,val styleEditable:Boolean=false)
data class OfficeTextStyle(val bold:Boolean?=null,val italic:Boolean?=null)

/** Bounded editing of existing Open XML parts. No macro execution, external network, or extraction
 * to disk. Complex runs, formulas, charts, embedded files and package relationships remain intact. */
class OfficeDocument private constructor(val format:OfficeFormat,
    private val files:LinkedHashMap<String,ByteArray>, private val edits:Map<String,Target>,
    val fields:List<OfficeField>, val truncated:Boolean) {
    private data class Target(val part:String,val node:Element,val type:String,val textNodes:List<Element>)
    fun export(changes:Map<String,String>,styles:Map<String,OfficeTextStyle> = emptyMap()):ByteArray {
        require(changes.size<=fields.size && changes.keys.all { it in edits })
        require(styles.size<=fields.size && styles.keys.all { id -> fields.any { it.id==id && it.styleEditable } })
        val dirty=linkedSetOf<String>()
        for ((id,value) in changes) {
            val target=edits.getValue(id)
            require(value.length<=8000 && value.none { it=='\u0000' || it.isISOControl() && it !in "\n\r\t" })
            when(target.type) {
                "number" -> {
                    require(value.matches(Regex("-?[0-9]{1,15}(\\.[0-9]{1,10})?")))
                    target.textNodes.single().textContent=BigDecimal(value).toPlainString()
                }
                "cell" -> {
                    val cell=target.node
                    val doc=cell.ownerDocument
                    children(cell).filter { it.localName in setOf("v","is") }.forEach { cell.removeChild(it) }
                    cell.setAttribute("t","inlineStr")
                    val prefix=cell.prefix?.let { "$it:" }.orEmpty()
                    val inline=doc.createElementNS(S,prefix+"is")
                    val t=doc.createElementNS(S,prefix+"t")
                    t.setAttributeNS(XML,"xml:space","preserve")
                    t.textContent=value
                    inline.appendChild(t);cell.appendChild(inline)
                }
                else -> {
                    require(target.textNodes.isNotEmpty())
                    // Preserve run structure and styling; distribute text over existing runs.
                    var remaining=value
                    target.textNodes.forEachIndexed { i,node ->
                        val count=if(i==target.textNodes.lastIndex) remaining.length else minOf(node.textContent.length,remaining.length)
                        // Never split a surrogate pair across XML text nodes.
                        val safe=if(count in 1 until remaining.length &&
                            Character.isHighSurrogate(remaining[count-1]) && Character.isLowSurrogate(remaining[count])) count-1 else count
                        node.textContent=remaining.substring(0,safe)
                        node.setAttributeNS(XML,"xml:space","preserve")
                        remaining=remaining.substring(safe)
                    }
                }
            }
            dirty.add(target.part)
        }
        for((id,style) in styles) {
            val target=edits.getValue(id)
            val paragraph=target.node
            val ns=paragraph.namespaceURI
            require(ns==W || ns==A)
            val prefix=paragraph.prefix?.let{"$it:"}.orEmpty()
            for(run in children(paragraph).filter{it.namespaceURI==ns && it.localName=="r"}) {
                if(children(run).none{it.namespaceURI==ns && it.localName=="t"})continue
                val doc=run.ownerDocument
                val properties=children(run).firstOrNull{it.namespaceURI==ns && it.localName=="rPr"}
                    ?:doc.createElementNS(ns,prefix+"rPr").also{run.insertBefore(it,run.firstChild)}
                for((key,value) in listOf("b" to style.bold,"i" to style.italic)) {
                    if(value==null)continue
                    if(ns==A)properties.setAttribute(key,if(value)"1" else "0")
                    else{
                        children(properties).filter{it.namespaceURI==W && it.localName==key}.forEach{properties.removeChild(it)}
                        val flag=doc.createElementNS(W,prefix+key)
                        val attrPrefix=paragraph.lookupPrefix(W)?.takeIf{it.isNotEmpty()}?:"w"
                        flag.setAttributeNS(W,attrPrefix+":val",if(value)"1" else "0")
                        // The order of Word rPr properties is significant: keep b/i before
                        // color/size/underline, and after style/font properties.
                        val order=listOf("rStyle","rFonts","b","bCs","i","iCs","caps","smallCaps","strike",
                            "dstrike","outline","shadow","emboss","imprint","noProof","snapToGrid","vanish",
                            "webHidden","color","spacing","w","kern","position","sz","szCs","highlight","u",
                            "effect","bdr","shd","fitText","vertAlign","rtl","cs","em","lang","eastAsianLayout",
                            "specVanish","oMath","rPrChange")
                        val index=order.indexOf(key)
                        val after=children(properties).firstOrNull{it.namespaceURI==W && order.indexOf(it.localName)>index}
                        properties.insertBefore(flag,after)
                    }
                }
            }
            dirty.add(target.part)
        }
        val result=LinkedHashMap(files)
        for (part in dirty) {
            val doc=edits.values.first { it.part==part }.node.ownerDocument
            result[part]=xmlBytes(doc)
        }
        // Recalculate supported same-sheet numeric formulas. Remove stale caches for
        // unsupported formulas, including dependent formulas in other worksheets.
        if(format==OfficeFormat.SHEET && dirty.isNotEmpty()) {
            for (part in result.keys.filter { it.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml")) }) {
                val doc=xml(result.getValue(part))
                val rows=elements(doc,S,"c")
                val cells=rows.associate { cell ->
                    val f=children(cell).firstOrNull { it.namespaceURI==S && it.localName=="f" }
                    val v=children(cell).firstOrNull { it.namespaceURI==S && it.localName=="v" }
                    val type=cell.getAttribute("t")
                    cell.getAttribute("r") to SheetFormula.Cell(
                        number=if(f==null && type in setOf("","n")) v?.textContent?.toBigDecimalOrNull() else null,
                        formula=f?.textContent,
                        unsupported=if(f!=null) f.hasAttribute("t") && f.getAttribute("t")!="normal" ||
                            type !in setOf("","n") else type !in setOf("","n") || v!=null && v.textContent.toBigDecimalOrNull()==null)
                }
                val evaluator=SheetFormula(cells)
                var changed=false
                for(cell in rows) {
                    if(children(cell).none { it.namespaceURI==S && it.localName=="f" })continue
                    val value=evaluator.evaluate(cell.getAttribute("r"))
                    children(cell).filter { it.namespaceURI==S && it.localName=="v" }.forEach { cell.removeChild(it) }
                    if(value!=null) {
                        val prefix=cell.prefix?.let { "$it:" }.orEmpty()
                        val v=doc.createElementNS(S,prefix+"v")
                        v.textContent=value.stripTrailingZeros().toPlainString()
                        cell.appendChild(v)
                    }
                    changed=true
                }
                if(changed)result[part]=xmlBytes(doc)
            }
            val book=result["xl/workbook.xml"]?.let(::xml)
            if(book!=null) {
                val calc=elements(book,S,"calcPr").firstOrNull()
                if(calc!=null) { calc.setAttribute("fullCalcOnLoad","1");calc.setAttribute("forceFullCalc","1") }
                result["xl/workbook.xml"]=xmlBytes(book)
            }
        }
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            result.forEach { (name,bytes) ->
                zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()
                require(out.size()<=MAX_INPUT*2)
            }
        }
        return out.toByteArray()
    }

    companion object {
        const val MAX_INPUT=8*1024*1024
        private const val MAX_EXPANDED=48*1024*1024
        private const val W="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        private const val S="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        private const val A="http://schemas.openxmlformats.org/drawingml/2006/main"
        private const val XML="http://www.w3.org/XML/1998/namespace"
        private fun xml(bytes:ByteArray):Document {
            require(bytes.size<=MAX_EXPANDED)
            val text=bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            require(!text.contains("<!DOCTYPE",true) && !text.contains("<!ENTITY",true) && !text.contains('\u0000'))
            val factory=DocumentBuilderFactory.newInstance().apply { isNamespaceAware=true;isExpandEntityReferences=false }
            val builder=factory.newDocumentBuilder()
            builder.setEntityResolver { _,_ -> throw org.xml.sax.SAXException("External XML is disabled") }
            return builder.parse(InputSource(StringReader(text)))
        }
        private fun xmlBytes(doc:Document):ByteArray {
            val out=ByteArrayOutputStream()
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(javax.xml.transform.OutputKeys.ENCODING,"UTF-8")
            }.transform(DOMSource(doc),StreamResult(out))
            return out.toByteArray()
        }
        private fun children(node:Node):List<Element> = (0 until node.childNodes.length)
            .mapNotNull { node.childNodes.item(it) as? Element }
        private fun elements(node:Node,ns:String,local:String):List<Element> {
            val list=when(node) { is Document -> node.getElementsByTagNameNS(ns,local); is Element -> node.getElementsByTagNameNS(ns,local)
                else -> return emptyList() }
            return (0 until list.length).mapNotNull { list.item(it) as? Element }
        }
        private fun unzip(bytes:ByteArray):LinkedHashMap<String,ByteArray> {
            require(bytes.size in 1..MAX_INPUT)
            val result=linkedMapOf<String,ByteArray>()
            var total=0;var count=0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while(true) {
                    val e=zip.nextEntry ?: break
                    require(++count<=2048)
                    if(e.isDirectory) {zip.closeEntry();continue}
                    val name=e.name
                    require(name.length in 1..300 && !name.startsWith("/") &&
                        name.none { it.isISOControl() || it=='\\' || it==':' } &&
                        name.split('/').all { it.isNotBlank() && it!="." && it!=".." } && name !in result)
                    require(!name.endsWith("vbaProject.bin",true))
                    val out=ByteArrayOutputStream();val buf=ByteArray(8192)
                    while(true) {
                        val n=zip.read(buf);if(n<0)break
                        total+=n;require(total<=MAX_EXPANDED);out.write(buf,0,n)
                    }
                    result[name]=out.toByteArray();zip.closeEntry()
                }
            }
            require("[Content_Types].xml" in result)
            return result
        }

        fun parse(format:OfficeFormat,bytes:ByteArray):OfficeDocument {
            val files=unzip(bytes)
            val targets=linkedMapOf<String,Target>()
            val fields=mutableListOf<OfficeField>()
            var truncated=false
            fun record(part:String,label:String,text:String,node:Element,type:String,nodes:List<Element>,editable:Boolean) {
                if(fields.size>=500){truncated=true;return}
                val id="field-"+fields.size
                val allow=editable && text.length<=8000
                fields.add(OfficeField(id,part,label,text.take(8000),allow,type=="number",allow && type=="paragraph"))
                if(allow)targets[id]=Target(part,node,type,nodes)
            }
            when(format) {
                OfficeFormat.WORD,OfficeFormat.SLIDES -> {
                    val parts=if(format==OfficeFormat.WORD) files.keys.filter { it=="word/document.xml" ||
                        it.matches(Regex("word/(header|footer)[0-9]+\\.xml")) }
                        else files.keys.filter { it.matches(Regex("ppt/slides/slide[0-9]+\\.xml")) }
                            .sortedBy { it.substringAfter("slide").substringAfter("slide").substringBefore(".xml").toIntOrNull() ?: 0 }
                    require(parts.isNotEmpty())
                    val ns=if(format==OfficeFormat.WORD)W else A
                    for(part in parts) {
                        val doc=xml(files.getValue(part))
                        elements(doc,ns,"p").forEachIndexed { i,p ->
                            val texts=elements(p,ns,"t")
                            if(texts.isNotEmpty()) {
                                val text=texts.joinToString(""){it.textContent}
                                val legal=if(ns==W) setOf("pPr","r") else setOf("pPr","r","endParaRPr")
                                val simple=children(p).all { child -> child.namespaceURI==ns && child.localName in legal &&
                                    (child.localName!="r" || children(child).all { it.namespaceURI==ns && it.localName in setOf("rPr","t") }) }
                                record(part,"문단 "+(i+1),text,p,"paragraph",texts,simple)
                            }
                        }
                    }
                }
                OfficeFormat.SHEET -> {
                    require("xl/workbook.xml" in files)
                    val shared=files["xl/sharedStrings.xml"]?.let { content ->
                        elements(xml(content),S,"si").map { elements(it,S,"t").joinToString("") { n->n.textContent } }
                    }.orEmpty()
                    val parts=files.keys.filter { it.matches(Regex("xl/worksheets/sheet[0-9]+\\.xml")) }.sorted()
                    require(parts.isNotEmpty())
                    for(part in parts) {
                        val doc=xml(files.getValue(part))
                        for(cell in elements(doc,S,"c")) {
                            val address=cell.getAttribute("r")
                            if(address.isBlank())continue
                            val type=cell.getAttribute("t")
                            val formula=children(cell).firstOrNull { it.namespaceURI==S && it.localName=="f" }
                            val value=children(cell).firstOrNull { it.namespaceURI==S && it.localName=="v" }
                            val raw=value?.textContent.orEmpty()
                            val text=when(type) {
                                "s" -> shared.getOrNull(raw.toIntOrNull()?:-1).orEmpty()
                                "inlineStr" -> elements(cell,S,"t").joinToString(""){it.textContent}
                                else -> raw
                            }
                            val numeric=type in setOf("","n") && value!=null && raw.isNotBlank()
                            val canEdit=formula==null && type in setOf("","n","s","inlineStr","str")
                            record(part,address,if(formula==null)text else "="+formula.textContent+" → "+text,
                                cell,if(numeric)"number" else "cell",listOfNotNull(value),canEdit)
                        }
                    }
                }
            }
            return OfficeDocument(format,files,targets,fields,truncated)
        }
    }
}
