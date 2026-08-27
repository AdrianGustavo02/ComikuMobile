package com.example.comiku.data.model.places

// Solicitud para buscar comercios cercanos
data class NearbyBookstoresRequest(
    val latitude: Double,
    val longitude: Double,
    val radius: Int,
)
