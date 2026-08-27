package com.example.comiku.screens;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.data.model.UserShelfComicGroupData;
import com.example.comiku.data.model.UserShelfVolumeItemData;
import com.example.comiku.data.constants.GenerosComic;
import com.example.comiku.data.repository.UserShelfRepository;
import com.example.comiku.core.validation.InputValidator;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LibraryActivity extends BaseUserShelfActivity {
    private Spinner spinnerGenero;
    private EditText campoBusqueda;
    private final List<UserShelfComicGroupData> itemsBiblioteca = new ArrayList<>();
    private final List<String> generosOrdenados = new ArrayList<>();
    private String generoSeleccionado = "";
    private String busquedaActual = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLoginAndClearStack();
            return;
        }
        setupDrawerShell(getString(R.string.biblioteca_titulo));
    }

    @Override
    protected int getShelfTitle() {
        return R.string.biblioteca_titulo;
    }

    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_library;
    }

    @Override
    protected int getLoadingMessage() {
        return R.string.biblioteca_cargando;
    }

    @Override
    protected int getEmptyMessage() {
        return R.string.biblioteca_vacia;
    }

    // Prepara los filtros de biblioteca.
    @Override
    protected void setupShelfControls() {
        clearControls();

        TextView etiquetaGenero = createSectionLabel(getString(R.string.biblioteca_filtro_genero));
        addControlView(etiquetaGenero);

        spinnerGenero = new Spinner(this);
        ArrayAdapter<String> adaptadorGenero = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                getSortedGenres()
        );
        adaptadorGenero.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGenero.setAdapter(adaptadorGenero);
        spinnerGenero.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Object valor = parent.getItemAtPosition(position);
                generoSeleccionado = position == 0 ? "" : String.valueOf(valor);
                renderLibraryItems();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                generoSeleccionado = "";
            }
        });
        addControlView(spinnerGenero);

        TextView etiquetaBusqueda = createSectionLabel(getString(R.string.biblioteca_busqueda));
        etiquetaBusqueda.setPadding(0, dpToPx(10), 0, dpToPx(6));
        addControlView(etiquetaBusqueda);

        campoBusqueda = new EditText(this);
        campoBusqueda.setHint(R.string.biblioteca_busqueda_hint);
        campoBusqueda.setSingleLine(true);
        campoBusqueda.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        campoBusqueda.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(android.text.Editable s) {
                busquedaActual = InputValidator.sanitizeForbiddenChars(String.valueOf(s));
                if (!TextUtils.equals(s, busquedaActual)) {
                    campoBusqueda.removeTextChangedListener(this);
                    campoBusqueda.setText(busquedaActual);
                    campoBusqueda.setSelection(busquedaActual.length());
                    campoBusqueda.addTextChangedListener(this);
                }
                renderLibraryItems();
            }
        });
        addControlView(campoBusqueda);
    }

    @Override
    protected void refreshData() {
        String uidUsuario = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";

        if (TextUtils.isEmpty(uidUsuario)) {
            openLoginAndClearStack();
            return;
        }

        setLoading(true);
        clearError();
        clearEmptyState();
        clearResults();

        UserShelfRepository.getUserLibraryItems(uidUsuario)
                .addOnSuccessListener(grupos -> {
                    itemsBiblioteca.clear();
                    if (grupos != null) {
                        itemsBiblioteca.addAll(grupos);
                    }
                    setLoading(false);
                    renderLibraryItems();
                })
                .addOnFailureListener(error -> {
                    setLoading(false);
                    showError(error instanceof Exception
                            ? error.getMessage()
                            : getString(R.string.biblioteca_error));
                    showEmptyState(getString(R.string.biblioteca_vacia));
                });
    }

    // Vuelve a mostrar la biblioteca segun los filtros actuales.
    private void renderLibraryItems() {
        clearResults();

        List<UserShelfComicGroupData> filtrados = new ArrayList<>();
        for (UserShelfComicGroupData grupo : itemsBiblioteca) {
            if (!TextUtils.isEmpty(generoSeleccionado)
                    && (grupo.comic.generos == null || !grupo.comic.generos.contains(generoSeleccionado))) {
                continue;
            }

            if (!matchesSearch(grupo)) {
                continue;
            }

            filtrados.add(grupo);
        }

        Collections.sort(filtrados, (grupoA, grupoB) ->
                toSearchableText(grupoA.comic.nombre).compareTo(toSearchableText(grupoB.comic.nombre)));

        if (filtrados.isEmpty()) {
            // Distingue entre biblioteca totalmente vacia y sin resultados por filtros.
            boolean hayFiltrosActivos = !TextUtils.isEmpty(generoSeleccionado) || !TextUtils.isEmpty(busquedaActual);
            if (hayFiltrosActivos && !itemsBiblioteca.isEmpty()) {
                showEmptyState(getString(R.string.biblioteca_sin_resultados));
            } else {
                showEmptyState(getString(R.string.biblioteca_vacia));
            }
            return;
        }

        clearEmptyState();
        for (UserShelfComicGroupData grupo : filtrados) {
            addResultView(createLibraryCard(grupo));
        }
    }

    // Revisa si un comic coincide con la busqueda.
    private boolean matchesSearch(UserShelfComicGroupData grupo) {
        if (TextUtils.isEmpty(busquedaActual)) {
            return true;
        }

        String query = toSearchableText(busquedaActual);
        if (toSearchableText(grupo.comic.nombre).contains(query)) {
            return true;
        }
        if (toSearchableText(grupo.comic.editorial).contains(query)) {
            return true;
        }
        for (String autor : grupo.comic.autores) {
            if (toSearchableText(autor).contains(query)) {
                return true;
            }
        }
        return false;
    }

    // Crea una card para mostrar un comic en la biblioteca.
    private View createLibraryCard(UserShelfComicGroupData grupo) {
        LinearLayout tarjeta = createCardContainer();
        tarjeta.setBackgroundColor(0xFFFFFFFF);
        tarjeta.setClickable(true);
        tarjeta.setFocusable(true);
        tarjeta.setOnClickListener(v -> {
            Intent pantallaComic = new Intent(this, ComicDetailActivity.class);
            pantallaComic.putExtra(ComicDetailActivity.EXTRA_COMIC_ID, grupo.comicId);
            startActivity(pantallaComic);
        });

        UserShelfVolumeItemData tomoDestacado = grupo.getFeaturedVolume();
        android.widget.ImageView imagenPortada = createCoverImage();
        android.graphics.Bitmap bitmap = decodeDataUrl(tomoDestacado != null && tomoDestacado.tomo != null
                ? tomoDestacado.tomo.getPortadaDataUrl()
                : null);
        if (bitmap != null) {
            imagenPortada.setImageBitmap(bitmap);
        } else {
            imagenPortada.setImageResource(R.drawable.default_profile_picture);
        }
        tarjeta.addView(imagenPortada);

        TextView textoNombre = createTextLine(grupo.comic.nombre, true);
        tarjeta.addView(textoNombre);
        tarjeta.addView(createTextLine(grupo.comic.editorial, false));
        tarjeta.addView(createTextLine(grupo.comic.paisEditorial, false));
        tarjeta.addView(createTextLine("Tomos guardados: " + grupo.tomos.size(), false));

        return tarjeta;
    }

    // Normaliza texto para buscar sin distinguir mayusculas.
    private String toSearchableText(String valor) {
        return String.valueOf(valor == null ? "" : valor).toLowerCase(Locale.ROOT).trim();
    }

    // Devuelve la lista de generos ordenada.
    private List<String> getSortedGenres() {
        if (generosOrdenados.isEmpty()) {
            generosOrdenados.addAll(Arrays.asList(GenerosComic.LISTA));
            Collections.sort(generosOrdenados, (a, b) -> a.compareToIgnoreCase(b));
            generosOrdenados.add(0, "");
        }
        List<String> opciones = new ArrayList<>();
        opciones.add(getString(R.string.biblioteca_todos_los_generos));
        for (String genero : generosOrdenados) {
            if (!TextUtils.isEmpty(genero)) {
                opciones.add(genero);
            }
        }
        return opciones;
    }
}
