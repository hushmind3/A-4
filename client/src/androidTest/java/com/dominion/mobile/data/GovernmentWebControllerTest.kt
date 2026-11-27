package com.dominion.mobile.data

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dominion.mobile.platform.web.GovernmentWebController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GovernmentWebControllerTest {
    @Test(timeout=30_000)
    fun fillsSelectsAndSubmitsARealDomForm()=runBlocking{
        val context=ApplicationProvider.getApplicationContext<Context>()
        val loaded=CompletableDeferred<Unit>()
        lateinit var web:WebView
        InstrumentationRegistry.getInstrumentation().runOnMainSync{
            web=WebView(context).apply{
                settings.javaScriptEnabled=true
                webViewClient=object:WebViewClient(){
                    override fun onPageFinished(view:WebView?,url:String?){
                        if(!loaded.isCompleted)loaded.complete(Unit)
                    }
                }
                loadDataWithBaseURL("https://official.example", """
                    <html><body>
                    <label for=name>이름</label><input id=name required>
                    <label for=kind>신청 구분</label>
                    <select id=kind><option value=person>본인</option><option value=family>가족</option></select>
                    <form id=f onsubmit="document.getElementById('out').innerText='접수번호: 2026-99999';return false">
                    <button id=send type=submit>신청</button></form>
                    <div id=out></div></body></html>
                """.trimIndent(),"text/html","UTF-8",null)
            }
        }
        try{
            withTimeout(10_000){loaded.await()}
            val controller=GovernmentWebController(web)
            val snapshot=controller.snapshot()
            android.util.Log.d("GovTest", "text="+snapshot.text+" elements="+snapshot.elements)
            val name=snapshot.elements.firstOrNull{it.label.contains("이름")} ?: error("name: "+snapshot.elements)
            val kind=snapshot.elements.firstOrNull{it.label.contains("신청 구분")} ?: error("kind: "+snapshot.elements)
            val send=snapshot.elements.firstOrNull{it.kind.contains("submit")} ?: error("send: "+snapshot.elements)
            assertTrue(controller.execute(GovernmentAction("fill",name.ref,"홍길동")))
            assertTrue(controller.execute(GovernmentAction("select",kind.ref,"family")))
            val text=controller.pageText()
            assertTrue(text.contains("신청 구분"))
            assertTrue(controller.execute(GovernmentAction("submit",send.ref)))
            Thread.sleep(300)
            val submitted=GovernmentSubmission.inspect(controller.pageText())
            assertTrue(submitted.submitted)
            assertEquals("2026-99999",submitted.receiptNumber)
        }finally{
            InstrumentationRegistry.getInstrumentation().runOnMainSync{web.destroy()}
        }
    }
}
