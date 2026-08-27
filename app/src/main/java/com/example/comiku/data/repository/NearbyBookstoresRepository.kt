package com.example.comiku.data.repository

import com.example.comiku.data.model.places.NearbyBookstoreDto
import com.example.comiku.data.model.places.NearbyBookstoresRequest
import com.example.comiku.data.service.PlacesApiService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import retrofit2.HttpException
import java.io.IOException

// Repositorio para obtener comercios cercanos.
class NearbyBookstoresRepository(
    private val api: PlacesApiService,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    suspend fun search(
        latitude: Double,
        longitude: Double,
        radius: Int,
    ): List<NearbyBookstoreDto> {
        val usuario = firebaseAuth.currentUser
            ?: error("Debes iniciar sesion para buscar comercios cercanos.")

        val token = usuario.getIdToken(false).await().token
            ?: error("No fue posible validar la sesion.")

        return try {
            val respuesta = api.searchNearbyBookstores(
                authorization = "Bearer $token",
                request = NearbyBookstoresRequest(latitude, longitude, radius),
            )

            if (!respuesta.ok) {
                error(respuesta.message ?: "Error desconocido al buscar comercios.")
            }

            respuesta.places
        } catch (error: Exception) {
            throw Exception(mapearError(error))
        }
    }

    // Convierte errores de red y backend a mensajes claros para UI.
    private fun mapearError(error: Exception): String {
        return when (error) {
            is HttpException -> when (error.code()) {
                400 -> "Las coordenadas proporcionadas no son validas."
                401 -> "Tu sesion ha expirado. Inicia sesion nuevamente."
                500 -> "El servicio de mapas no esta disponible."
                502 -> "Google Places no pudo responder. Intenta nuevamente mas tarde."
                else -> "No fue posible buscar comercios cercanos en este momento."
            }
            is IOException -> "No hay conexion a internet. Verifica tu red."
            else -> error.message ?: "Error desconocido al buscar comercios."
        }
    }

    // Metodo sincronico para llamar desde Java.
    fun searchSync(
        latitude: Double,
        longitude: Double,
        radius: Int,
    ): List<NearbyBookstoreDto> {
        return runBlocking(Dispatchers.IO) {
            search(latitude, longitude, radius)
        }
    }
}
