package com.dominion.mobile.platform.network

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.Proxy
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Native, read-only Korean service adapters. No PC, Cloudflare changes, shell,
 * arbitrary endpoints, user credentials or third-party script execution.
 * Contracts: NomaDamas/k-skill @ c41a117 (Kakao, Naver, Coupang and Danawa).
 */
data class KoreanLookup(val source:String,val query:String){
    fun checked():KoreanLookup {
        require(source in setOf("kakao-place","naver-news","coupang","danawa"))
        val value=query.trim()
        require(value.length in 2..100 && value.none(Char::isISOControl))
        require(!value.contains("://") && !value.contains('@') &&
            !Regex("[0-9][0-9 .-]{5,}[0-9]").containsMatchIn(value)){
            "공개 검색어만 입력하세요. 주소·연락처·계정정보는 전송하지 마세요."
        }
        return copy(query=value)
    }
}

internal object KoreanPublicTools {
    private const val PROXY="https://k-skill-proxy.nomadamas.org"
    private val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(7,TimeUnit.SECONDS).readTimeout(14,TimeUnit.SECONDS)
        .callTimeout(20,TimeUnit.SECONDS).build()

    fun endpoint(raw:KoreanLookup):String {
        val call=raw.checked()
        val (base,params)=when(call.source){
            "kakao-place"->"$PROXY/v1/kakao-map/search/keyword" to mapOf("q" to call.query,"size" to "5")
            "naver-news"->"$PROXY/v1/naver-news/search" to mapOf("q" to call.query,"display" to "5","sort" to "date")
            "coupang"->"$PROXY/v1/coupang/products/search" to mapOf("keyword" to call.query,"limit" to "5")
            "danawa"->"https://search.danawa.com/dsearch.php" to mapOf("query" to call.query)
            else->error("Unsupported source")
        }
        return base.toHttpUrl().newBuilder().apply{params.forEach{(k,v)->addQueryParameter(k,v)}}.build().toString()
    }
    private fun JsonObject.value(key:String):String =
        get(key)?.takeIf{it.isJsonPrimitive}?.asString?.trim()?.take(300).orEmpty()
    private fun https(value:String):String?=runCatching{
        val url=value.toHttpUrl()
        require(url.isHttps && url.username.isEmpty() && url.password.isEmpty())
        url.toString().take(500)
    }.getOrNull()

    fun extract(raw:KoreanLookup,body:String):String {
        val call=raw.checked()
        val lines=if(call.source=="danawa"){
            Jsoup.parse(body).select("li.prod_item").mapNotNull{node->
                val link=node.selectFirst(".prod_name a, a[name=productName]")?:return@mapNotNull null
                val title=link.text().trim().take(180)
                val id=node.id().removePrefix("productItem").takeIf{it.matches(Regex("[0-9]{1,15}"))}
                    ?:return@mapNotNull null
                val price=node.selectFirst("#min_price_"+id)?.attr("value")?.filter(Char::isDigit)
                    ?.takeIf{it.isNotEmpty()} ?:node.selectFirst(".price_sect strong, .prod_pricelist strong")
                    ?.text()?.filter(Char::isDigit).orEmpty()
                if(title.isBlank())null else title+" | "+(price.toLongOrNull()?.let{it.toString()+"원"}?:"가격 미표시")+
                    " | https://prod.danawa.com/info/?pcode="+id
            }.distinct().take(5)
        }else{
            val json=JsonParser.parseString(body).asJsonObject
            check(!json.has("error") || json.get("error").isJsonNull){"공급자 조회 오류"}
            val key=if(call.source=="kakao-place")"documents" else "items"
            val rows=json.getAsJsonArray(key)?:error("응답 스키마 불일치")
            rows.take(5).mapNotNull{entry->
                if(!entry.isJsonObject)return@mapNotNull null
                val row=entry.asJsonObject
                val title=row.value(if(call.source=="kakao-place")"place_name" else "title")
                if(title.isBlank())return@mapNotNull null
                when(call.source){
                    "kakao-place"->listOfNotNull(title,row.value("road_address_name").ifBlank{row.value("address_name")},
                        row.value("phone"),https(row.value("place_url"))).joinToString(" | ")
                    "naver-news"->listOfNotNull(title,row.value("pub_date_iso").ifBlank{row.value("pub_date")},
                        row.value("description").take(180),https(row.value("original_link"))?:https(row.value("link"))).joinToString(" | ")
                    "coupang"->listOfNotNull(title,row.value("price").let{if(it.isBlank())"가격 미표시" else it+"원"},
                        "로켓배송="+row.value("is_rocket"),"무료배송="+row.value("is_free_shipping"),https(row.value("url"))).joinToString(" | ")
                    else->error("Unsupported source")
                }
            }
        }
        check(lines.isNotEmpty()){"검색 결과 없음 또는 공급자 페이지 형식 변경"}
        val suffix=buildString {
            if(call.source=="coupang")append("\n쿠팡 파트너스 활동을 통해 일정액의 수수료를 제공받을 수 있습니다.")
            if(call.source in setOf("coupang","danawa"))append("\n가격·재고·배송 조건은 변경될 수 있습니다.")
        }
        val header="조회 출처: "+endpoint(call)+"\n조회 시각: "+Instant.now()+"\n"
        // Keep complete rows/URLs and required disclosure inside the receipt budget.
        var result=header
        for(line in lines){
            if(result.length+line.length+1+suffix.length>3800)break
            result+=line+"\n"
        }
        check(result.length>header.length){"공급자 결과가 너무 큽니다"}
        return result.trimEnd()+suffix
    }
    suspend fun execute(raw:KoreanLookup):String=withContext(Dispatchers.IO){
        val call=raw.checked()
        val request=Request.Builder().url(endpoint(call)).header("User-Agent","DOMINION-Mobile/1.0 (user requested public lookup)")
            .header("Accept",if(call.source=="danawa")"text/html" else "application/json").build()
        client.newCall(request).execute().use{response->
            check(response.isSuccessful){"공급자 HTTP "+response.code+" · 연결 또는 이용 한도 확인"}
            val body=requireNotNull(response.body)
            val limit=if(call.source=="danawa")3*1024*1024 else 256*1024
            require(body.contentLength()<=limit)
            val text=body.byteStream().use{input->
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true){
                    val count=input.read(buffer)
                    if(count<0)break
                    check(output.size()+count<=limit){"응답 크기 초과"}
                    output.write(buffer,0,count)
                }
                output.toString("UTF-8")
            }
            extract(call,text)
        }
    }
}
