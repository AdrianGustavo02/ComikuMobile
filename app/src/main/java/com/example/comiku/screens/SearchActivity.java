package com.example.comiku.screens;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.comiku.R;
import com.example.comiku.data.model.ComicSearchResult;
import com.example.comiku.data.repository.ComicSearchRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {
    private EditText campoBusqueda;
    private ListView listaResultados;
    private TextView textoSinResultados;
    private TextView textoError;
    private ProgressBar barraCarga;
    private ImageButton botonCerrar;
    private ImageButton botonEscaner;

    private ArrayAdapter<String> adaptadorResultados;
    private List<ComicSearchResult> resultadosActuales = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        setContentView(R.layout.activity_search);
        bindViews();
        setupListeners();
        setupAdapter();
    }


    private void bindViews() {
        campoBusqueda = findViewById(R.id.campoBusquedaComics);
        listaResultados = findViewById(R.id.listaResultadosBusqueda);
        textoSinResultados = findViewById(R.id.textoSinResultadosBusqueda);
        textoError = findViewById(R.id.textoErrorBusqueda);
        barraCarga = findViewById(R.id.barraCargaBusqueda);
        botonCerrar = findViewById(R.id.botonCerrarBusqueda);
        botonEscaner = findViewById(R.id.botonEscanerBusqueda);
    }

    // Conecta acciones de busqueda, cierre y escaneo
    private void setupListeners() {
        campoBusqueda.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        botonCerrar.setOnClickListener(v -> finish());
        botonEscaner.setOnClickListener(v -> openScanner());

        listaResultados.setOnItemClickListener((parent, view, position, id) -> {
            ComicSearchResult resultado = resultadosActuales.get(position);
            openComicDetail(resultado.id);
        });

        campoBusqueda.requestFocus();
    }

    // Configura el adaptador para mostrar resultados.
    private void setupAdapter() {
        adaptadorResultados = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                new ArrayList<>()
        );
        listaResultados.setAdapter(adaptadorResultados);
    }

    // Busca comics en tiempo real.
    private void performSearch(String termino) {
        textoError.setText("");
        textoSinResultados.setVisibility(View.GONE);
        listaResultados.setVisibility(View.GONE);

        if (TextUtils.isEmpty(termino)) {
            adaptadorResultados.clear();
            resultadosActuales.clear();
            return;
        }

        barraCarga.setVisibility(View.VISIBLE);
        ComicSearchRepository.searchComicsByName(termino)
                .addOnSuccessListener(this::handleSearchResults)
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoError.setText(getString(R.string.error_busqueda_general));
                });
    }

    // Procesa los resultados de la busqueda.
    private void handleSearchResults(List<ComicSearchResult> resultados) {
        barraCarga.setVisibility(View.GONE);
        resultadosActuales.clear();
        adaptadorResultados.clear();

        if (resultados == null || resultados.isEmpty()) {
            textoSinResultados.setVisibility(View.VISIBLE);
            textoSinResultados.setText(getString(R.string.busqueda_sin_resultados));
            return;
        }

        resultadosActuales.addAll(resultados);
        for (ComicSearchResult resultado : resultados) {
            adaptadorResultados.add(resultado.getFormattedDisplay());
        }

        listaResultados.setVisibility(View.VISIBLE);
    }

    // Abre la pantalla de detalles del comic.
    private void openComicDetail(String comicId) {
        Intent pantallaDetalleComic = new Intent(this, ComicDetailActivity.class);
        pantallaDetalleComic.putExtra(ComicDetailActivity.EXTRA_COMIC_ID, comicId);
        startActivity(pantallaDetalleComic);
    }

    // Abre la pantalla del escaner ISBN.
    private void openScanner() {
        Intent pantallaEscaner = new Intent(this, BarcodeScannerActivity.class);
        startActivity(pantallaEscaner);
    }
}
