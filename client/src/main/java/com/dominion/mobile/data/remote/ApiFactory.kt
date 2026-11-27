package com.dominion.mobile.data.remote

import com.dominion.mobile.BuildConfig
import com.dominion.mobile.data.local.EngineConnection
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {

    fun create(connection: EngineConnection): DominionApi {
        connection.validated()
        val gson = GsonBuilder().setLenient().create()
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // A dropped connection after submission must not silently replay a task.
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val expected = connection.origin.toHttpUrl()
                val actual = chain.request().url
                check(actual.scheme == expected.scheme && actual.host == expected.host && actual.port == expected.port) {
                    "Engine credentials cannot leave configured origin"
                }
                if(connection.isRelay)return@addInterceptor NativeRelayTransport.execute(chain,connection)
                val request = chain.request().newBuilder().apply {
                    if (chain.request().header("Accept") == null) header("Accept", "application/json")
                    header("X-Client-Version", BuildConfig.VERSION_NAME)
                    header("X-Client-Platform", "android")
                    header("X-Dominion-Token", connection.ownerToken)
                    if (connection.accessClientId != null && connection.accessClientSecret != null) {
                        header("CF-Access-Client-Id", connection.accessClientId)
                        header("CF-Access-Client-Secret", connection.accessClientSecret)
                    }
                }.build()
                val seconds = when (request.url.encodedPath) {
                    "/api/voice/transcribe" -> 310
                    "/api/voice/speak" -> 130
                    else -> 60
                }
                chain.withReadTimeout(seconds, TimeUnit.SECONDS).proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(connection.origin)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(DominionApi::class.java)
    }
}
