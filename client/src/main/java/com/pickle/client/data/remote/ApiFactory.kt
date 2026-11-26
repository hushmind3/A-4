package com.pickle.client.data.remote

import com.pickle.client.BuildConfig
import com.pickle.client.data.local.LocalStore
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {

    fun create(store: LocalStore): PickleApi {
        val gson = GsonBuilder().setLenient().create()
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = store.authToken
                val request = chain.request().newBuilder().apply {
                    header("Accept", "application/json")
                    header("X-Client-Version", BuildConfig.VERSION_NAME)
                    header("X-Client-Platform", "android")
                    if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
                }.build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PickleApi::class.java)
    }
}
