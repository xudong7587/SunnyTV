package io.github.xudong7587.sunnytv

import io.github.xudong7587.sunnytv.core.network.UpdateTransport
import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class UpdateTransportTest {
    @Test fun davPathsEncodeSpacesAndScopeCredentialsAcrossRedirect()=runBlocking {
        MockWebServer().use {nas->MockWebServer().use {cdn->
            nas.enqueue(MockResponse().setResponseCode(302).addHeader("Location",cdn.url("/latest.apk")))
            cdn.enqueue(MockResponse().setBody("apk"))
            val output=ByteArrayOutputStream()
            UpdateTransport(nas.url("/V1%20Tools/SunnyTV-updata/").toString(),"fixture","password")
                .fetch("SunnyTV-v30.apk",10) {b,n->output.write(b,0,n)}
            assertEquals("apk",output.toString())
            val first=nas.takeRequest();val last=cdn.takeRequest()
            assertEquals("GET",first.method);assertEquals("/V1%20Tools/SunnyTV-updata/SunnyTV-v30.apk",first.path)
            assertTrue(first.getHeader("Authorization")!!.startsWith("Basic "))
            assertNull(last.getHeader("Authorization"));assertEquals(HttpPolicy.USER_AGENT,last.getHeader("User-Agent"))
        }}
    }
    @Test fun filenamesCannotEscapeConfiguredFolder()=runBlocking {
        MockWebServer().use {nas->
            val transport=UpdateTransport(nas.url("/dav/").toString(),"","")
            for(name in listOf("../latest.apk","https://other.invalid/x",".apk","x/y.apk","x%2f.apk")) {
                assertTrue(runCatching {transport.fetch(name,10) {_,_->}}.exceptionOrNull() is IllegalArgumentException)
            }
            assertEquals(0,nas.requestCount)
        }
    }
    @Test fun oversizedFixedAndChunkedResponsesAreRejected()=runBlocking {
        MockWebServer().use {nas->
            val transport=UpdateTransport(nas.url("/dav/").toString(),"","")
            nas.enqueue(MockResponse().setBody("12345"))
            nas.enqueue(MockResponse().setChunkedBody("12345",1))
            repeat(2) {assertTrue(runCatching {transport.fetch("latest.json",4) {_,_->}}.exceptionOrNull() is IllegalArgumentException)}
        }
    }
    @Test fun cancelStopsBlockedReadPromptly()=runBlocking {
        MockWebServer().use {nas->
            nas.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job=launch {UpdateTransport(nas.url("/dav/").toString(),"","").fetch("latest.json",10) {_,_->}}
            withContext(Dispatchers.IO) {assertNotNull(nas.takeRequest(3,TimeUnit.SECONDS))}
            withTimeout(2000) {job.cancelAndJoin()}
            assertTrue(job.isCancelled)
        }
    }
    @Test fun authenticationFailureIsReportedWithoutResponseBody()=runBlocking {
        MockWebServer().use {nas->
            nas.enqueue(MockResponse().setResponseCode(401).setBody("private secret"))
            val e=runCatching {UpdateTransport(nas.url("/dav/").toString(),"","").fetch("latest.json",10) {_,_->}}.exceptionOrNull()!!
            assertTrue(e.message!!.contains("401"));assertFalse(e.message!!.contains("secret"))
        }
    }
}
