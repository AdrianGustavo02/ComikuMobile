package com.example.comiku.screens;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comiku.R;
import com.example.comiku.data.model.places.NearbyBookstoreDto;
import com.example.comiku.ui.util.MapsHelper;

import java.util.List;

// Adaptador para mostrar lista de comercios cercanos
public class AdaptadorComerciosCercanos extends RecyclerView.Adapter<AdaptadorComerciosCercanos.ViewHolder> {

    private List<NearbyBookstoreDto> comercios;
    private Context context;

    public AdaptadorComerciosCercanos(List<NearbyBookstoreDto> comercios, Context context) {
        this.comercios = comercios;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(
            R.layout.item_comercio_cercano,
            parent,
            false
        );
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NearbyBookstoreDto comercio = comercios.get(position);

        holder.textoOrdenComercio.setText(String.valueOf(position + 1));
        holder.nombreComercio.setText(comercio.getName());
        holder.direccionComercio.setText(comercio.getAddress());
        holder.distanciaComercio.setText(MapsHelper.formatDistance(comercio.getDistanceMeters()));

        holder.botonVerMapa.setOnClickListener(v -> MapsHelper.openPlace(context, comercio));
        holder.botonComoLlegar.setOnClickListener(v -> MapsHelper.openDirections(context, comercio));
    }

    @Override
    public int getItemCount() {
        return comercios.size();
    }

    // Actualiza los datos del adaptador con nuevos comercios.
    public void actualizarDatos(List<NearbyBookstoreDto> nuevosDatos) {
        this.comercios = nuevosDatos;
        notifyDataSetChanged();
    }

    // ViewHolder para un elemento de comercio cercano
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textoOrdenComercio;
        TextView nombreComercio;
        TextView direccionComercio;
        TextView distanciaComercio;
        Button botonVerMapa;
        Button botonComoLlegar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textoOrdenComercio = itemView.findViewById(R.id.textViewOrdenComercio);
            nombreComercio = itemView.findViewById(R.id.textViewNombreComercio);
            direccionComercio = itemView.findViewById(R.id.textViewDireccionComercio);
            distanciaComercio = itemView.findViewById(R.id.textViewDistanciaComercio);
            botonVerMapa = itemView.findViewById(R.id.botonVerMapa);
            botonComoLlegar = itemView.findViewById(R.id.botonComoLlegar);
        }
    }
}
