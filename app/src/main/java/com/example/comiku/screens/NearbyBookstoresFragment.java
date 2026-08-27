package com.example.comiku.screens;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comiku.R;
import com.example.comiku.data.model.places.NearbyBookstoreDto;
import com.example.comiku.data.repository.NearbyBookstoresRepository;
import com.example.comiku.data.service.RetrofitClient;
import com.example.comiku.ui.util.MapsHelper;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;


public class NearbyBookstoresFragment extends Fragment {

    private FusedLocationProviderClient ubicacionClient;
    private NearbyBookstoresRepository repositorio;
    private RecyclerView listaComerciosMerciosList;
    private ProgressBar indicadorCarga;
    private TextView mensajeEstado;
    private Button botonBuscar;
    private View contenedorSegmentado;
    private int radioActual = 20_000;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_nearby_bookstores, container, false);

        ubicacionClient = LocationServices.getFusedLocationProviderClient(requireContext());
        repositorio = new NearbyBookstoresRepository(
            RetrofitClient.INSTANCE.getPlacesApiService(),
            FirebaseAuth.getInstance()
        );

        listaComerciosMerciosList = view.findViewById(R.id.recyclerViewComerciosCercanos);
        indicadorCarga = view.findViewById(R.id.progressBarCargando);
        mensajeEstado = view.findViewById(R.id.textViewEstado);
        botonBuscar = view.findViewById(R.id.botonBuscarCercaMio);
        contenedorSegmentado = view.findViewById(R.id.contenedorSegmentado);

        listaComerciosMerciosList.setLayoutManager(new LinearLayoutManager(requireContext()));

        botonBuscar.setOnClickListener(v -> solicitarUbicacion());

        return view;
    }

    // Solicita permiso de ubicación y buscar locales cercanos
    private void solicitarUbicacion() {
        int permiso = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        );

        if (permiso == PackageManager.PERMISSION_GRANTED) {
            buscarComerciosCercanos();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(
                    new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    1001
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                buscarComerciosCercanos();
            } else {
                mensajeEstado.setText("Se requiere permiso de ubicacion para buscar comiquerias/librerias cercanas.");
                mensajeEstado.setVisibility(View.VISIBLE);
            }
        }
    }

    // Busca locales cercanos usando la ubicación actual
    private void buscarComerciosCercanos() {
        indicadorCarga.setVisibility(View.VISIBLE);
        mensajeEstado.setVisibility(View.GONE);
        botonBuscar.setEnabled(false);

        // Obtener ubicación del dispositivo
        try {
            ubicacionClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    ejecutarBusqueda(location.getLatitude(), location.getLongitude());
                } else {
                    mostrarError("No fue posible determinar tu ubicacion actual.");
                }
            });
        } catch (SecurityException e) {
            mostrarError("Error de seguridad: " + e.getMessage());
        }
    }

    // Ejecuta la búsqueda en el repositorio
    private void ejecutarBusqueda(double latitud, double longitud) {
        Thread thread = new Thread(() -> {
            try {
                List<NearbyBookstoreDto> comercios = repositorio.searchSync(
                    latitud,
                    longitud,
                    radioActual
                );

                requireActivity().runOnUiThread(() -> mostrarResultados(comercios));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> mostrarError(e.getMessage()));
            }
        });
        thread.start();
    }

    // Muestra los resultados en la UI
    private void mostrarResultados(List<NearbyBookstoreDto> comercios) {
        indicadorCarga.setVisibility(View.GONE);
        botonBuscar.setVisibility(View.GONE);
        contenedorSegmentado.setVisibility(View.VISIBLE);

        if (comercios.isEmpty()) {
            mensajeEstado.setText("Sin resultados en " + (radioActual / 1000) + " km.");
            mensajeEstado.setVisibility(View.VISIBLE);

            if (radioActual == 20_000) {
                Button botonAmpliar = new Button(requireContext());
                botonAmpliar.setText("Buscar en 50 km");
                botonAmpliar.setOnClickListener(v -> {
                    radioActual = 50_000;
                    mensajeEstado.setVisibility(View.GONE);
                    ((ViewGroup) mensajeEstado.getParent()).removeView(botonAmpliar);
                    buscarComerciosCercanos();
                });
                ((ViewGroup) mensajeEstado.getParent()).addView(botonAmpliar);
            }
        } else {
            mensajeEstado.setVisibility(View.GONE);
            AdaptadorComerciosCercanos adaptador = new AdaptadorComerciosCercanos(
                comercios,
                requireContext()
            );
            listaComerciosMerciosList.setAdapter(adaptador);
        }
    }

    // Muestra un mensaje de error
    private void mostrarError(String mensaje) {
        indicadorCarga.setVisibility(View.GONE);
        botonBuscar.setEnabled(true);
        mensajeEstado.setText(mensaje);
        mensajeEstado.setVisibility(View.VISIBLE);
    }
}
