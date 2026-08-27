package com.example.comiku.data.service

import com.example.comiku.data.model.places.NearbyBookstoresRequest
import com.example.comiku.data.model.places.NearbyBookstoresResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

// Servicio Retrofit para obtener comercios cercanos
interface PlacesApiService {
    @POST("api/places/nearby-bookstores")
    suspend fun searchNearbyBookstores(
        @Header("Authorization") authorization: String,
        @Body request: NearbyBookstoresRequest,
    ): NearbyBookstoresResponse
}
