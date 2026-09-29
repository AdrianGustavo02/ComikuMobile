package com.example.comiku.screens;

import android.content.Intent;
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
import com.example.comiku.core.validation.InputValidator;
import com.example.comiku.data.model.UserShelfComicGroupData;
import com.example.comiku.data.model.UserShelfVolumeItemData;
import com.example.comiku.data.repository.UserShelfRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WishlistActivity extends BaseUserShelfActivity {
    private Spinner spinnerOrden;
    private EditText campoBusqueda;
    private final List<UserShelfComicGroupData> gruposDeseados = new ArrayList<>();
    private final List<WishlistItemData> itemsDeseados = new ArrayList<>();
    private String ordenActual = "recent";
    private String busquedaActual = "";

    // Abre la lista de deseados del usuario actual.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLoginAndClearStack();
            return;
        }
        setupDrawerShell(getString(R.string.deseados_titulo));
    }

    @Override
    protected int getShelfTitle() {
        return R.string.deseados_titulo;
    }

    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_wishlist;
    }

    @Override
    protected int getLoadingMessage() {
        return R.string.deseados_cargando;
    }

    @Override
    protected int getEmptyMessage() {
        return R.string.deseados_vacio;
    }

    // Prepara los filtros de la lista de deseados.
    @Override
    protected void setupShelfControls() {
        clearControls();

        TextView etiquetaOrden = createSectionLabel(getString(R.string.deseados_orden));
        addControlView(etiquetaOrden);

        spinnerOrden = new LimitedHeightSpinner(this);
        spinnerOrden.setBackground(getDrawable(R.drawable.bg_library_genre_filter));
        spinnerOrden.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
        LinearLayout.LayoutParams paramsSpinner = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsSpinner.bottomMargin = dpToPx(12);
        spinnerOrden.setLayoutParams(paramsSpinner);
        ArrayAdapter<String> adaptadorOrden = new ArrayAdapter<>(
                this,
                R.layout.item_library_genre_selected,
                new String[]{
                        getString(R.string.deseados_orden_reciente),
                        getString(R.string.deseados_orden_alfabetico)
                }
        );
        adaptadorOrden.setDropDownViewResource(R.layout.item_library_genre_dropdown_item);
        spinnerOrden.setAdapter(adaptadorOrden);
        spinnerOrden.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                ordenActual = position == 0 ? "recent" : "alphabetical";
                renderWishlistItems();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                ordenActual = "recent";
            }
        });
        addControlView(spinnerOrden);

        TextView etiquetaBusqueda = createSectionLabel(getString(R.string.deseados_busqueda));
        etiquetaBusqueda.setPadding(0, dpToPx(10), 0, dpToPx(6));
        addControlView(etiquetaBusqueda);

        campoBusqueda = new EditText(this);
        campoBusqueda.setHint(R.string.deseados_busqueda_hint);
        campoBusqueda.setTextColor(android.graphics.Color.WHITE);
        campoBusqueda.setHintTextColor(0xFF6B6B6B);
        campoBusqueda.setTextSize(16f);
        campoBusqueda.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
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
                renderWishlistItems();
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

        UserShelfRepository.getUserWishlistItems(uidUsuario)
                .addOnSuccessListener(grupos -> {
                    gruposDeseados.clear();
                    itemsDeseados.clear();
                    if (grupos != null) {
                        gruposDeseados.addAll(grupos);
                    }
                    flattenWishlistItems();
                    setLoading(false);
                    renderWishlistItems();
                })
                .addOnFailureListener(error -> {
                    setLoading(false);
                    showError(error instanceof Exception
                            ? error.getMessage()
                            : getString(R.string.deseados_error));
                    showEmptyState(getString(R.string.deseados_vacio));
                });
    }

    // Convierte los grupos a una lista plana para mostrar cada tomo.
    private void flattenWishlistItems() {
        for (UserShelfComicGroupData grupo : gruposDeseados) {
            for (UserShelfVolumeItemData tomoGuardado : grupo.tomos) {
                itemsDeseados.add(new WishlistItemData(grupo.comicId, grupo.comic.nombre, grupo.comic.editorial, grupo.comic.autores, tomoGuardado));
            }
        }
    }

    // Vuelve a mostrar los tomos segun la busqueda y el orden.
    private void renderWishlistItems() {
        clearResults();

        List<WishlistItemData> filtrados = new ArrayList<>();
        for (WishlistItemData item : itemsDeseados) {
            if (!matchesSearch(item)) {
                continue;
            }
            filtrados.add(item);
        }

        Collections.sort(filtrados, (itemA, itemB) -> {
            if ("alphabetical".equals(ordenActual)) {
                int comparacionComic = toSearchableText(itemA.comicNombre)
                        .compareTo(toSearchableText(itemB.comicNombre));
                if (comparacionComic != 0) {
                    return comparacionComic;
                }
                return compareVolumeOrder(itemA, itemB);
            }

            Date fechaA = itemA.tomo.fechaAgregado != null ? itemA.tomo.fechaAgregado : new Date(0);
            Date fechaB = itemB.tomo.fechaAgregado != null ? itemB.tomo.fechaAgregado : new Date(0);
            return fechaB.compareTo(fechaA);
        });

        if (filtrados.isEmpty()) {
            showEmptyState(itemsDeseados.isEmpty()
                    ? getString(R.string.deseados_vacio)
                    : getString(R.string.deseados_sin_coincidencias));
            return;
        }

        clearEmptyState();
        int indice = 0;
        while (indice < filtrados.size()) {
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            fila.setPadding(dpToPx(4), 0, dpToPx(4), dpToPx(8));

            int columnas = Math.min(2, filtrados.size() - indice);
            for (int columna = 0; columna < columnas; columna++) {
                View tarjeta = createWishlistCard(filtrados.get(indice + columna));
                LinearLayout.LayoutParams paramsTarjeta = new LinearLayout.LayoutParams(
                        columnas == 1 ? dpToPx(160) : 0,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        columnas == 1 ? 0f : 1f
                );
                paramsTarjeta.leftMargin = columna == 0 ? 0 : dpToPx(8);
                paramsTarjeta.rightMargin = columna == 0 ? dpToPx(8) : 0;
                tarjeta.setLayoutParams(paramsTarjeta);
                tarjeta.setMinimumHeight(dpToPx(260));
                fila.addView(tarjeta);
            }

            addResultView(fila);
            indice += columnas;
        }
    }

    // Revisa si un tomo coincide con la busqueda.
    private boolean matchesSearch(WishlistItemData item) {
        if (TextUtils.isEmpty(busquedaActual)) {
            return true;
        }

        String query = toSearchableText(busquedaActual);
        if (toSearchableText(item.comicNombre).contains(query)) {
            return true;
        }
        if (toSearchableText(item.comicEditorial).contains(query)) {
            return true;
        }
        for (String autor : item.comicAutores) {
            if (toSearchableText(autor).contains(query)) {
                return true;
            }
        }
        return false;
    }

    // Compara dos tomos para ordenarlos por numero dentro del mismo comic.
    private int compareVolumeOrder(WishlistItemData itemA, WishlistItemData itemB) {
        int ordenA = getVolumeOrderValue(itemA);
        int ordenB = getVolumeOrderValue(itemB);
        if (ordenA != ordenB) {
            return Integer.compare(ordenA, ordenB);
        }
        return toSearchableText(itemA.tomoId).compareTo(toSearchableText(itemB.tomoId));
    }

    // Devuelve un valor numerico para ordenar un tomo.
    private int getVolumeOrderValue(WishlistItemData item) {
        if (item == null || item.tomo == null || item.tomo.tomo == null) {
            return Integer.MAX_VALUE;
        }
        if (Boolean.TRUE.equals(item.tomo.tomo.tomoUnico)) {
            return 0;
        }
        Integer numeroTomo = item.tomo.tomo.numeroTomo;
        return numeroTomo != null ? numeroTomo : Integer.MAX_VALUE - 1;
    }

    // Crea una card para mostrar un tomo deseado con el estilo de las portadas del carrusel.
    private View createWishlistCard(WishlistItemData item) {
        LinearLayout tarjeta = createCardContainer();
        tarjeta.setOrientation(LinearLayout.VERTICAL);
        tarjeta.setBackground(getDrawable(R.drawable.bg_wishlist_volume_card));
        tarjeta.setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10));
        tarjeta.setClickable(true);
        tarjeta.setFocusable(true);
        tarjeta.setOnClickListener(v -> {
            Intent pantallaTomo = new Intent(this, VolumeDetailActivity.class);
            pantallaTomo.putExtra(VolumeDetailActivity.EXTRA_COMIC_ID, item.comicId);
            pantallaTomo.putExtra(VolumeDetailActivity.EXTRA_VOLUME_ID, item.tomoId);
            startActivity(pantallaTomo);
        });

        android.widget.FrameLayout contenedorPortada = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams paramsContenedor = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(220)
        );
        paramsContenedor.bottomMargin = dpToPx(10);
        contenedorPortada.setLayoutParams(paramsContenedor);
        contenedorPortada.setClipToOutline(true);
        contenedorPortada.setClipChildren(true);
        android.graphics.drawable.GradientDrawable fondoPortada = new android.graphics.drawable.GradientDrawable();
        fondoPortada.setColor(android.graphics.Color.TRANSPARENT);
        fondoPortada.setCornerRadius(dpToPx(12));
        contenedorPortada.setBackground(fondoPortada);

        RoundedImageView imagenPortada = new RoundedImageView(this);
        android.widget.FrameLayout.LayoutParams paramsImagen = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        imagenPortada.setLayoutParams(paramsImagen);
        imagenPortada.setCornerRadius(dpToPx(12));
        imagenPortada.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        imagenPortada.setAdjustViewBounds(true);
        android.graphics.Bitmap bitmap = decodeDataUrl(item.tomo.tomo.getPortadaDataUrl());
        if (bitmap != null) {
            imagenPortada.setImageBitmap(bitmap);
        } else {
            imagenPortada.setImageResource(R.drawable.default_profile_picture);
        }
        contenedorPortada.addView(imagenPortada);
        tarjeta.addView(contenedorPortada);

        TextView textoComic = new TextView(this);
        textoComic.setText(item.comicNombre != null ? item.comicNombre : "");
        textoComic.setTextSize(15f);
        textoComic.setTypeface(null, android.graphics.Typeface.BOLD);
        textoComic.setMaxLines(3);
        textoComic.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textoComic.setTextColor(getColor(android.R.color.white));
        tarjeta.addView(textoComic);

        TextView textoTomo = new TextView(this);
        textoTomo.setText(item.tomo != null && item.tomo.tomo != null ? item.tomo.tomo.getNumeroFormateado() : "");
        textoTomo.setTextSize(12f);
        textoTomo.setMaxLines(2);
        textoTomo.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textoTomo.setTextColor(getColor(R.color.carousel_subtitle));
        textoTomo.setPadding(0, dpToPx(4), 0, 0);
        tarjeta.addView(textoTomo);

        TextView textoEditorial = new TextView(this);
        textoEditorial.setText(item.comicEditorial != null ? item.comicEditorial : "");
        textoEditorial.setTextSize(12f);
        textoEditorial.setMaxLines(2);
        textoEditorial.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textoEditorial.setTextColor(getColor(R.color.carousel_subtitle));
        textoEditorial.setPadding(0, dpToPx(2), 0, 0);
        tarjeta.addView(textoEditorial);

        TextView textoIsbn = new TextView(this);
        textoIsbn.setText("ISBN: " + (item.tomo != null && item.tomo.tomo != null && item.tomo.tomo.isbn != null
                ? item.tomo.tomo.isbn
                : "No definido"));
        textoIsbn.setTextSize(12f);
        textoIsbn.setMaxLines(2);
        textoIsbn.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textoIsbn.setTextColor(getColor(R.color.carousel_subtitle));
        textoIsbn.setPadding(0, dpToPx(2), 0, 0);
        tarjeta.addView(textoIsbn);

        return tarjeta;
    }

    // Convierte texto para comparar sin distinguir mayusculas.
    private String toSearchableText(String valor) {
        return String.valueOf(valor == null ? "" : valor).toLowerCase(Locale.ROOT).trim();
    }

    // Guarda datos de un tomo deseado.
    private static final class WishlistItemData {
        private final String comicId;
        private final String comicNombre;
        private final String comicEditorial;
        private final List<String> comicAutores;
        private final String tomoId;
        private final UserShelfVolumeItemData tomo;

        private WishlistItemData(
                String comicId,
                String comicNombre,
                String comicEditorial,
                List<String> comicAutores,
                UserShelfVolumeItemData tomo
        ) {
            this.comicId = comicId;
            this.comicNombre = comicNombre;
            this.comicEditorial = comicEditorial;
            this.comicAutores = comicAutores != null ? new ArrayList<>(comicAutores) : new ArrayList<>();
            this.tomoId = tomo != null && tomo.tomo != null ? tomo.tomo.id : "";
            this.tomo = tomo;
        }
    }
}
