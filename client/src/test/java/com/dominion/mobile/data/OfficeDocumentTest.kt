package com.dominion.mobile.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class OfficeDocumentTest {
    private fun zip(vararg parts:Pair<String,String>):ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            (listOf("[Content_Types].xml" to "<Types/>")+parts).forEach { (name,text) ->
                z.putNextEntry(ZipEntry(name));z.write(text.toByteArray());z.closeEntry()
            }
        }
        return out.toByteArray()
    }
    @Test fun wordParagraphEditsRoundTrip() {
        val bytes=zip("word/document.xml" to """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:rPr/><w:t>Hello</w:t></w:r><w:r><w:t> world</w:t></w:r></w:p></w:body></w:document>""")
        val doc=OfficeDocument.parse(OfficeFormat.WORD,bytes)
        assertEquals("Hello world",doc.fields.single().text)
        val edited=doc.export(mapOf(doc.fields.single().id to "수정 완료"))
        assertEquals("수정 완료",OfficeDocument.parse(OfficeFormat.WORD,edited).fields.single().text)
        assertEquals("Hello world",OfficeDocument.parse(OfficeFormat.WORD,bytes).fields.single().text)
    }
    @Test fun spreadsheetEditsOneCellAndPreservesFormula() {
        val bytes=zip("xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><calcPr/></workbook>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData><row><c r="A1"><v>10</v></c><c r="B1"><f>A1*2</f><v>20</v></c></row></sheetData></worksheet>""")
        val doc=OfficeDocument.parse(OfficeFormat.SHEET,bytes)
        assertFalse(doc.fields[1].editable)
        val edited=OfficeDocument.parse(OfficeFormat.SHEET,doc.export(mapOf(doc.fields[0].id to "15.5")))
        assertEquals("15.5",edited.fields[0].text)
        assertEquals("=A1*2 → 31",edited.fields[1].text)
        assertTrue(runCatching { doc.export(mapOf(doc.fields[1].id to "0")) }.isFailure)
    }
    @Test fun rejectsExternalEntityAndTraversal() {
        val xml="""<!DOCTYPE a [<!ENTITY x SYSTEM "file:///etc/passwd">]><a>&x;</a>"""
        assertTrue(runCatching { OfficeDocument.parse(OfficeFormat.WORD,zip("word/document.xml" to xml)) }.isFailure)
        assertTrue(runCatching { OfficeDocument.parse(OfficeFormat.WORD,zip("../file" to "x")) }.isFailure)
    }
}
