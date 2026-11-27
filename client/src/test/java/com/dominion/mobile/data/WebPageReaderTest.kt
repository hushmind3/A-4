package com.dominion.mobile.data

import com.dominion.mobile.platform.network.WebPageReader
import org.junit.Assert.*
import org.junit.Test

class WebPageReaderTest {
    @Test fun rejectsLocalHostIpAndCredentials(){
        for(url in listOf("http://example.com","https://localhost/","https://127.0.0.1/",
            "https://user:password@example.com/","https://internal.local/page",
            "https://192.168.1.8/","https://example.com:8443/")){
            assertTrue(url,runCatching{WebPageReader.accept(url)}.isFailure)
        }
        assertEquals("example.com",WebPageReader.accept("https://example.com/info").host)
    }
    @Test fun removesHtmlScriptAndLimitsObservations(){
        val html="<html><head><title>Details</title><script>secret-command</script></head>"+
            "<body><main>Useful confirmed page text</main><style>bad-css</style></body></html>"
        val result=WebPageReader.pageText("text/html; charset=utf-8",html)
        assertTrue(result.contains("Useful confirmed page text"))
        assertFalse(result.contains("secret-command"))
        assertFalse(result.contains("bad-css"))
    }
}
