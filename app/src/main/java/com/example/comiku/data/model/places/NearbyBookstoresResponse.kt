package com.example.comiku.data.model.places

// Respuesta del backend con comercios cercanos
data class NearbyBookstoresResponse(
    val ok: Boolean,
    val radius: Int,
    val places: List<NearbyBookstoreDto>,
    val message: String? = null,
)
