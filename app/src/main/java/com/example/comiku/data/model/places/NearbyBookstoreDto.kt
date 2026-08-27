package com.example.comiku.data.model.places

// Representa un comercio cercano para comprar tomos
data class NearbyBookstoreDto(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val businessStatus: String,
    val type: String,
    val googleMapsUrl: String,
    val distanceMeters: Int,
)
