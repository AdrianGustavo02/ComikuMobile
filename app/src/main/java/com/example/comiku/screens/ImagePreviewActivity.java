package com.example.comiku.screens;

import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.example.comiku.R;

// Muestra una imagen del chat en pantalla completa.
public class ImagePreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);
        ImageView imagenVistaPrevia = findViewById(R.id.imagenVistaPrevia);
        ImageView botonCerrarVistaPrevia = findViewById(R.id.botonCerrarVistaPrevia);

        String urlImagen = getIntent().getStringExtra("imageUrl");
        if (urlImagen == null || urlImagen.trim().isEmpty()) {
            urlImagen = getIntent().getStringExtra("imagePreviewUrl");
        }
        if (urlImagen != null && !urlImagen.trim().isEmpty()) {
            Glide.with(this)
                    .load(urlImagen)
                    .fitCenter()
                    .into(imagenVistaPrevia);
        }

        botonCerrarVistaPrevia.setOnClickListener(v -> finish());
        imagenVistaPrevia.setOnClickListener(v -> finish());
    }
}
