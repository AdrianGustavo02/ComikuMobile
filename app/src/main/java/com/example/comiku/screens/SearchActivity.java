package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.core.ui.ThematicListUiHelper;
import com.example.comiku.data.model.ComicSearchResult;
import com.example.comiku.data.repository.ComicSearchRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends BasePlainScreenActivity {
    private EditText campoBusqueda;
    private ListView listaResultados;
    private TextView textoSinResultados;
    private TextView textoError;
    private ProgressBar barraCarga;
    private ImageButton botonCerrar;
    private ImageButton botonEscaner;

    private SearchResultAdapter adaptadorResultados;
    private List<ComicSearchResult> resultadosActuales = new ArrayList<>();
    private int tokenBusquedaActual;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        setupPlainScreenShell(R.layout.activity_search);
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
        adaptadorResultados = new SearchResultAdapter();
        listaResultados.setAdapter(adaptadorResultados);
    }

    // Busca comics en tiempo real.
    private void performSearch(String termino) {
        int tokenBusqueda = ++tokenBusquedaActual;
        String terminoNormalizado = termino != null ? termino.trim() : "";

        textoError.setText("");
        textoSinResultados.setVisibility(View.GONE);
        listaResultados.setVisibility(View.GONE);

        if (TextUtils.isEmpty(terminoNormalizado)) {
            barraCarga.setVisibility(View.GONE);
            clearResults();
            return;
        }

        barraCarga.setVisibility(View.VISIBLE);
        ComicSearchRepository.searchComicsByName(terminoNormalizado)
                .addOnSuccessListener(resultados -> handleSearchResults(tokenBusqueda, resultados))
                .addOnFailureListener(error -> {
                    if (tokenBusqueda != tokenBusquedaActual) {
                        return;
                    }
                    barraCarga.setVisibility(View.GONE);
                    clearResults();
                    textoError.setText(getString(R.string.error_busqueda_general));
                });
    }

    // Procesa los resultados de la busqueda.
    private void handleSearchResults(int tokenBusqueda, List<ComicSearchResult> resultados) {
        if (tokenBusqueda != tokenBusquedaActual) {
            return;
        }
        barraCarga.setVisibility(View.GONE);
        clearResults();

        if (resultados == null || resultados.isEmpty()) {
            textoSinResultados.setVisibility(View.VISIBLE);
            textoSinResultados.setText(getString(R.string.busqueda_sin_resultados));
            return;
        }

        resultadosActuales.addAll(resultados);
        adaptadorResultados.addAll(resultados);

        listaResultados.setVisibility(View.VISIBLE);
    }

    // Limpia los resultados visibles y el contenido del adaptador.
    private void clearResults() {
        resultadosActuales.clear();
        adaptadorResultados.clear();
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

    // Adapta cada resultado para mostrar portada y textos.
    private final class SearchResultAdapter extends ArrayAdapter<ComicSearchResult> {
        private final LayoutInflater inflador;

        // Crea el adaptador de resultados de busqueda.
        SearchResultAdapter() {
            super(SearchActivity.this, 0, new ArrayList<>());
            inflador = LayoutInflater.from(SearchActivity.this);
        }

        @Override
        public View getView(int posicion, View vistaReciclada, ViewGroup padre) {
            SearchResultViewHolder holder;
            View vista = vistaReciclada;

            if (vista == null) {
                vista = inflador.inflate(R.layout.item_search_result, padre, false);
                holder = new SearchResultViewHolder(vista);
                vista.setTag(holder);
            } else {
                holder = (SearchResultViewHolder) vista.getTag();
            }

            ComicSearchResult resultado = getItem(posicion);
            if (resultado == null) {
                holder.textoTitulo.setText("");
                holder.textoDetalle.setText("");
                holder.imagenPortada.setImageResource(R.drawable.default_profile_picture);
                return vista;
            }

            holder.textoTitulo.setText(resultado.nombre);
            holder.textoDetalle.setText(resultado.getSecondaryDisplay());

            Bitmap bitmapPortada = ThematicListUiHelper.decodeDataUrl(resultado.portadaDataUrl);
            if (bitmapPortada != null) {
                holder.imagenPortada.setImageBitmap(bitmapPortada);
            } else {
                holder.imagenPortada.setImageResource(R.drawable.default_profile_picture);
            }

            return vista;
        }
    }

    // Guarda las vistas de cada fila para reutilizarlas.
    private static final class SearchResultViewHolder {
        private final RoundedImageView imagenPortada;
        private final TextView textoTitulo;
        private final TextView textoDetalle;


        SearchResultViewHolder(View vista) {
            imagenPortada = vista.findViewById(R.id.imagenPortadaResultadoBusqueda);
            textoTitulo = vista.findViewById(R.id.textoTituloResultadoBusqueda);
            textoDetalle = vista.findViewById(R.id.textoDetalleResultadoBusqueda);
        }
    }
}
