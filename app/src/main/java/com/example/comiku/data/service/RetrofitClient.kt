package com.example.comiku.data.service

import com.example.comiku.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// Proporciona instancia singleton de Retrofit configurada
object RetrofitClient {
    private val backendUrl: String = run {
        val urlConfigurada = BuildConfig.BACKEND_URL.trim()
        val urlBase = if (urlConfigurada.isEmpty()) "http://10.0.2.2:3000" else urlConfigurada
        if (urlBase.endsWith("/")) urlBase else "$urlBase/"
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl(backendUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val placesApiService: PlacesApiService = retrofit.create(PlacesApiService::class.java)
}
