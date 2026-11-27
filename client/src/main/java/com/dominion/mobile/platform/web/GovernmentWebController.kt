package com.dominion.mobile.platform.web

import android.webkit.WebView
import com.dominion.mobile.data.GovernmentAction
import com.dominion.mobile.data.GovernmentElement
import com.dominion.mobile.data.GovernmentPageSnapshot
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Executes a constrained form plan against the WebView's own DOM. The model
 * never supplies JavaScript, CSS selectors, cookies or credentials. The user
 * authenticates inside the same visible WebView and reviews the final submit.
 */
class GovernmentWebController(private val webView: WebView) {
    private val gson = Gson()

    private suspend fun eval(script: String): String = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { continuation ->
            val done = AtomicBoolean(false)
            try {
                webView.evaluateJavascript(script) { value ->
                    if (!done.compareAndSet(false, true)) return@evaluateJavascript
                    if (continuation.isActive) continuation.resume(value ?: "null")
                }
            } catch (failure: Exception) {
                if (done.compareAndSet(false, true) && continuation.isActive)
                    continuation.resumeWithException(failure)
            }
            continuation.invokeOnCancellation {
                if (done.compareAndSet(false, true)) {
                    runCatching { webView.stopLoading() }
                }
            }
        }
    }

    private fun decoded(raw: String): String {
        if (raw == "null" || raw == "undefined") return ""
        return runCatching { gson.fromJson(raw, String::class.java) }.getOrElse { raw }
    }

    suspend fun snapshot(): GovernmentPageSnapshot {
        val raw = decoded(eval(SNAPSHOT_JS))
        require(raw.isNotBlank()) { "페이지 구조를 읽지 못했습니다" }
        val obj = gson.fromJson(raw, com.google.gson.JsonObject::class.java)
        val elements = obj.getAsJsonArray("elements").map { item ->
            val row = item.asJsonObject
            GovernmentElement(
                ref = row.get("ref").asString,
                label = row.get("label")?.takeUnless { it.isJsonNull }?.asString.orEmpty().take(180),
                kind = row.get("kind").asString.take(30),
                value = row.get("value")?.takeUnless { it.isJsonNull }?.asString?.take(500),
                required = row.get("required")?.asBoolean == true
            )
        }
        return GovernmentPageSnapshot(
            url = obj.get("url").asString.take(1000),
            title = obj.get("title").asString.take(300),
            text = obj.get("text").asString.take(12000),
            elements = elements
        )
    }

    suspend fun execute(action: GovernmentAction): Boolean {
        val script = when (action.action) {
            "fill" -> fillJs(requireNotNull(action.ref), requireNotNull(action.value))
            "select" -> selectJs(requireNotNull(action.ref), requireNotNull(action.value))
            "check" -> checkJs(requireNotNull(action.ref))
            "click" -> clickJs(requireNotNull(action.ref))
            "submit" -> submitJs(action.ref)
            "read" -> "true"
            else -> error("지원하지 않는 동작")
        }
        val result = decoded(eval(script))
        return result == "true"
    }

    suspend fun pageText(): String = decoded(eval(TEXT_JS)).take(20000)

    private fun fillJs(ref: String, value: String): String {
        val safeRef = ref.replace("'", "")
        val safeValue = gson.toJson(value)
        return """(function(){
          var el=window.__dominionRefs&&window.__dominionRefs['$safeRef'];
          if(!el||el.disabled)return false;
          var v=$safeValue;
          var proto=el.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:
            el.tagName==='SELECT'?HTMLSelectElement.prototype:HTMLInputElement.prototype;
          var setter=Object.getOwnPropertyDescriptor(proto,'value')&&Object.getOwnPropertyDescriptor(proto,'value').set;
          if(setter)setter.call(el,v);else el.value=v;
          el.dispatchEvent(new Event('input',{bubbles:true}));
          el.dispatchEvent(new Event('change',{bubbles:true}));
          return true;
        })()""".trimIndent()
    }

    private fun selectJs(ref: String, value: String): String {
        val safeRef = ref.replace("'", "")
        val safeValue = gson.toJson(value)
        return """(function(){
          var el=window.__dominionRefs&&window.__dominionRefs['$safeRef'];
          if(!el||el.tagName!=='SELECT'||el.disabled)return false;
          var v=$safeValue,found=false;
          for(var i=0;i<el.options.length;i++){
            if(el.options[i].value===v||el.options[i].text===v){el.selectedIndex=i;found=true;break;}
          }
          if(!found)return false;
          el.dispatchEvent(new Event('input',{bubbles:true}));
          el.dispatchEvent(new Event('change',{bubbles:true}));
          return true;
        })()""".trimIndent()
    }

    private fun checkJs(ref: String): String {
        val safeRef = ref.replace("'", "")
        return """(function(){
          var el=window.__dominionRefs&&window.__dominionRefs['$safeRef'];
          if(!el||el.disabled)return false;
          if(!el.checked)el.click();
          return el.checked===true;
        })()""".trimIndent()
    }

    private fun clickJs(ref: String): String {
        val safeRef = ref.replace("'", "")
        return """(function(){
          var el=window.__dominionRefs&&window.__dominionRefs['$safeRef'];
          if(!el||el.disabled)return false;
          el.click();return true;
        })()""".trimIndent()
    }

    private fun submitJs(ref: String?): String {
        val safeRef = ref?.replace("'", "")
        return """(function(){
          var el=${if (safeRef == null) "null" else "window.__dominionRefs&&window.__dominionRefs['$safeRef']"};
          if(el&&!el.disabled){el.click();return true;}
          var form=document.querySelector('form');
          if(form){if(form.requestSubmit)form.requestSubmit();else form.submit();return true;}
          var button=document.querySelector('button[type="submit"],input[type="submit"]');
          if(button&&!button.disabled){button.click();return true;}
          return false;
        })()""".trimIndent()
    }

    private companion object {
        const val SNAPSHOT_JS = """(function(){
          window.__dominionRefs=window.__dominionRefs||{};
          var nodes=[].slice.call(document.querySelectorAll('input,select,textarea,button,a[role="button"],[role="button"]')).slice(0,120);
          var out=[];
          for(var i=0;i<nodes.length;i++){
            var el=nodes[i];
            var style=window.getComputedStyle(el);
            if(style.display==='none'||style.visibility==='hidden'||el.disabled)continue;
            var ref='e'+out.length;
            window.__dominionRefs[ref]=el;
            var label=(el.labels&&el.labels[0]&&el.labels[0].innerText)||el.getAttribute('aria-label')||
              el.placeholder||el.name||el.id||el.innerText||'';
            label=String(label).replace(/\s+/g,' ').trim().slice(0,160);
            out.push({ref:ref,label:label,
              kind:(el.tagName+(el.type?':'+el.type:'')).toLowerCase().slice(0,30),
              value:el.value==null?null:String(el.value).slice(0,500),
              required:!!(el.required||el.getAttribute('aria-required')==='true')});
          }
          return JSON.stringify({url:location.href,title:document.title||'',
            text:(document.body&&document.body.innerText||'').slice(0,12000),elements:out});
        })()"""
        const val TEXT_JS = """(function(){return document.body?document.body.innerText:''})()"""
    }
}
