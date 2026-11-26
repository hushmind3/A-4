package com.dominion.mobile.data.remote

import com.dominion.mobile.BuildConfig
import com.dominion.mobile.data.local.EngineConnection
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {

    fun create(connection: EngineConnection): DominionApi {
        val gson = GsonBuilder().setLenient().create()
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            // A dropped connection after submission must not silently replay a task.
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder().apply {
                    header("Accept", "application/json")
                    header("X-Client-Version", BuildConfig.VERSION_NAME)
                    header("X-Client-Platform", "android")
                    header("X-Dominion-Token", connection.ownerToken)
                }.build()
                chain.proceed(request)
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
