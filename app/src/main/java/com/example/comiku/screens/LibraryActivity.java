package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
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
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LibraryActivity extends BaseUserShelfActivity {
    public static final String EXTRA_LIBRARY_USER_ID = "extra_library_user_id";
    public static final String EXTRA_LIBRARY_USER_NICK = "extra_library_user_nick";

    private Spinner spinnerGenero;
    private EditText campoBusqueda;
    private final List<UserShelfComicGroupData> itemsBiblioteca = new ArrayList<>();
    private final List<String> generosOrdenados = new ArrayList<>();
    private String generoSeleccionado = "";
    private String busquedaActual = "";
    private String uidBibliotecaObjetivo = "";
    private String nickBibliotecaObjetivo = "";
    private boolean esBibliotecaExterna;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLoginAndClearStack();
            return;
        }
        uidBibliotecaObjetivo = resolveTargetUserId();
        nickBibliotecaObjetivo = getIntent().getStringExtra(EXTRA_LIBRARY_USER_NICK);
        esBibliotecaExterna = isExternalShelf();
        setupDrawerShell(getString(R.string.biblioteca_titulo));
        if (esBibliotecaExterna && TextUtils.isEmpty(nickBibliotecaObjetivo)) {
            loadExternalShelfTitle();
        }
    }

    @Override
    protected int getShelfTitle() {
        return R.string.biblioteca_titulo;
    }

    // Devuelve el titulo segun el usuario de la biblioteca.
    @Override
    protected CharSequence getShelfTitleText() {
        if (esBibliotecaExterna && !TextUtils.isEmpty(nickBibliotecaObjetivo)) {
            return getString(R.string.biblioteca_titulo_externo, nickBibliotecaObjetivo);
        }
        return getString(getShelfTitle());
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
        etiquetaGenero.setPadding(0, dpToPx(2), 0, dpToPx(10));
        addControlView(etiquetaGenero);

        spinnerGenero = new LimitedHeightSpinner(this);
        spinnerGenero.setBackground(getDrawable(R.drawable.bg_library_genre_filter));
        spinnerGenero.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        LinearLayout.LayoutParams paramsSpinner = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsSpinner.bottomMargin = dpToPx(12);
        spinnerGenero.setLayoutParams(paramsSpinner);
        ArrayAdapter<String> adaptadorGenero = new ArrayAdapter<>(
                this,
                R.layout.item_library_genre_selected,
                getSortedGenres()
        );
        adaptadorGenero.setDropDownViewResource(R.layout.item_library_genre_dropdown_item);
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
        etiquetaBusqueda.setPadding(0, dpToPx(12), 0, dpToPx(10));
        addControlView(etiquetaBusqueda);

        campoBusqueda = new EditText(this);
        campoBusqueda.setHint(R.string.biblioteca_busqueda_hint);
        campoBusqueda.setTextColor(android.graphics.Color.WHITE);
        campoBusqueda.setHintTextColor(0xFF6B6B6B);
        campoBusqueda.setTextSize(16f);
        campoBusqueda.setTypeface(Typeface.DEFAULT_BOLD);
        campoBusqueda.setBackground(getDrawable(R.drawable.bg_library_search_input));
        campoBusqueda.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));
        LinearLayout.LayoutParams paramsBusqueda = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsBusqueda.topMargin = dpToPx(2);
        campoBusqueda.setLayoutParams(paramsBusqueda);
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
        String uidUsuario = uidBibliotecaObjetivo;

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

    // Resuelve el uid que se debe mostrar en la biblioteca.
    private String resolveTargetUserId() {
        String uidExtra = getIntent().getStringExtra(EXTRA_LIBRARY_USER_ID);
        if (!TextUtils.isEmpty(uidExtra)) {
            return uidExtra;
        }
        return FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
    }

    // Indica si la biblioteca pertenece a otro usuario.
    private boolean isExternalShelf() {
        String uidActual = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";
        return !TextUtils.isEmpty(uidBibliotecaObjetivo)
                && !TextUtils.isEmpty(uidActual)
                && !uidBibliotecaObjetivo.equals(uidActual);
    }

    // Indica si la biblioteca visible es la del usuario actual.
    public boolean isShowingCurrentUserLibrary() {
        return !isExternalShelf();
    }

    // Carga el nick cuando la biblioteca ajena llega sin nombre.
    private void loadExternalShelfTitle() {
        FirebaseFirestore.getInstance()
                .collection("usuario")
                .document(uidBibliotecaObjetivo)
                .get()
                .addOnSuccessListener(documento -> {
                    if (documento == null || !documento.exists()) {
                        return;
                    }
                    String nick = documento.getString("Nick");
                    if (TextUtils.isEmpty(nick)) {
                        return;
                    }
                    nickBibliotecaObjetivo = nick;
                    if (textoTituloListaUsuario != null) {
                        textoTituloListaUsuario.setText(getShelfTitleText());
                    }
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
        tarjeta.setOrientation(LinearLayout.HORIZONTAL);
        tarjeta.setBackground(getDrawable(R.drawable.bg_library_comic_card));
        tarjeta.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        tarjeta.setClickable(true);
        tarjeta.setFocusable(true);
        tarjeta.setOnClickListener(v -> {
            Intent pantallaComic = new Intent(this, ComicDetailActivity.class);
            pantallaComic.putExtra(ComicDetailActivity.EXTRA_COMIC_ID, grupo.comicId);
            startActivity(pantallaComic);
        });

        UserShelfVolumeItemData tomoDestacado = grupo.getFeaturedVolume();
        android.widget.ImageView imagenPortada = new android.widget.ImageView(this);
        LinearLayout.LayoutParams parametrosPortada = new LinearLayout.LayoutParams(dpToPx(116), dpToPx(168));
        parametrosPortada.setMarginEnd(dpToPx(12));
        imagenPortada.setLayoutParams(parametrosPortada);
        imagenPortada.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        imagenPortada.setBackground(getDrawable(R.drawable.bg_volume_cover_image));
        imagenPortada.setClipToOutline(true);

        android.graphics.Bitmap bitmap = decodeDataUrl(tomoDestacado != null && tomoDestacado.tomo != null
                ? tomoDestacado.tomo.getPortadaDataUrl()
                : null);
        if (bitmap != null) {
            imagenPortada.setImageBitmap(bitmap);
        } else {
            imagenPortada.setImageResource(R.drawable.default_profile_picture);
        }
        tarjeta.addView(imagenPortada);

        LinearLayout contenedorInfo = new LinearLayout(this);
        contenedorInfo.setOrientation(LinearLayout.VERTICAL);
        contenedorInfo.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView textoNombre = new TextView(this);
        textoNombre.setText(grupo.comic != null ? grupo.comic.nombre : "");
        textoNombre.setTextSize(22f);
        textoNombre.setTypeface(null, android.graphics.Typeface.BOLD);
        textoNombre.setTextColor(getColor(android.R.color.white));
        contenedorInfo.addView(textoNombre);

        contenedorInfo.addView(createLibraryInfoLine(grupo.comic != null ? grupo.comic.editorial : ""));
        contenedorInfo.addView(createLibraryInfoLine(grupo.comic != null ? grupo.comic.paisEditorial : ""));
        contenedorInfo.addView(createLibraryInfoLine("Tomos guardados: " + grupo.tomos.size()));

        tarjeta.addView(contenedorInfo);
        return tarjeta;
    }

    // Crea una linea de texto secundario para la card de biblioteca.
    private TextView createLibraryInfoLine(String texto) {
        TextView linea = new TextView(this);
        linea.setText(texto);
        linea.setTextSize(18f);
        linea.setTextColor(getColor(R.color.carousel_subtitle));
        return linea;
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
