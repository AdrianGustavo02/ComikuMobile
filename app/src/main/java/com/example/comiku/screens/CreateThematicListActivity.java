package com.example.comiku.screens;

import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.core.ui.GenericFormContainerComponent;
import com.example.comiku.core.ui.ThematicListUiHelper;
import com.example.comiku.core.validation.InputValidator;
import com.example.comiku.data.model.ComicDetailData;
import com.example.comiku.data.repository.FriendActivityRepository;
import com.example.comiku.data.model.ThematicListData;
import com.example.comiku.data.model.ThematicListVolumeCard;
import com.example.comiku.data.model.ThematicListVolumeData;
import com.example.comiku.data.model.VolumeDetailData;
import com.example.comiku.data.repository.ComicDetailRepository;
import com.example.comiku.data.repository.ThematicListRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CreateThematicListActivity extends BasePlainScreenActivity {

    public static final String EXTRA_LIST_ID = "extra_list_id";

    private EditText campoNombre;
    private EditText campoDescripcion;
    private Spinner spinnerGuia;
    private Button botonSiguiente;
    private LinearLayout panelPaso1;
    private LinearLayout panelPaso2;
    private EditText campoBusquedaTomos;
    private LinearLayout contenedorResultados;
    private TextView textoContador;
    private LinearLayout contenedorSeleccionados;
    private Button botonAtras;
    private Button botonGuardar;
    private TextView textoMensaje;
    private TextView textoTituloPantalla;
    private android.widget.ProgressBar barraCarga;

    private String listId;
    private boolean esModoEdicion;
    private boolean guardando;
    private boolean catalogoCargado;
    private boolean tomosExistentesCargados;
    private String listaCreadaId = "";

    private final List<ComicDetailData> todosLosComics = new ArrayList<>();
    private final List<ThematicListVolumeCard> tomosSeleccionados = new ArrayList<>();
    private final java.util.Map<String, List<VolumeDetailData>> tomosPorComic = new java.util.HashMap<>();

    // Inicializa la actividad de creacion o edicion de lista.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupPlainScreenShell(R.layout.activity_create_thematic_list);
        GenericFormContainerComponent.inflateFormContent(
                findViewById(android.R.id.content),
                R.layout.view_form_create_thematic_list_content
        );

        listId = getIntent().getStringExtra(EXTRA_LIST_ID);
        esModoEdicion = !TextUtils.isEmpty(listId);

        bindViews();
        setupScreenTitle();
        setupSpinnerGuia();
        setupInputSanitizers();
        setupButtons();
        refreshSelectedVolumes();

        if (esModoEdicion) {
            loadExistingList();
        }
    }

    // Vincula todas las vistas del layout.
    private void bindViews() {
        campoNombre = findViewById(R.id.campoNombreLista);
        campoDescripcion = findViewById(R.id.campoDescripcionLista);
        spinnerGuia = findViewById(R.id.spinnerEsGuiaDeLectura);
        botonSiguiente = findViewById(R.id.botonSiguientePaso);
        panelPaso1 = findViewById(R.id.panelPaso1);
        panelPaso2 = findViewById(R.id.panelPaso2);
        campoBusquedaTomos = findViewById(R.id.campoBusquedaTomos);
        contenedorResultados = findViewById(R.id.contenedorResultadosBusqueda);
        textoContador = findViewById(R.id.textoContadorTomos);
        contenedorSeleccionados = findViewById(R.id.contenedorTomosSeleccionados);
        botonAtras = findViewById(R.id.botonAtras);
        botonGuardar = findViewById(R.id.botonGuardarLista);
        textoMensaje = findViewById(R.id.textoMensajeCreacion);
        textoTituloPantalla = findViewById(R.id.textoTituloCrearListaTematica);
        barraCarga = findViewById(R.id.barraCargaCreacion);
    }

    // Muestra el titulo principal de la pantalla.
    private void setupScreenTitle() {
        textoTituloPantalla.setText(esModoEdicion
                ? getString(R.string.crear_lista_titulo_editar)
                : getString(R.string.crear_lista_titulo));
    }

    // Configura el spinner de guia de lectura.
    private void setupSpinnerGuia() {
        String[] opciones = {
                getString(R.string.opcion_no),
                getString(R.string.opcion_si)
        };
        ArrayAdapter<String> adaptador = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, opciones);
        adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGuia.setAdapter(adaptador);
    }

    // Limpia solo la busqueda mientras el usuario escribe.
    private void setupInputSanitizers() {
        attachSanitizer(campoBusquedaTomos, false);
    }

    // Configura las acciones principales de la pantalla.
    private void setupButtons() {
        botonSiguiente.setOnClickListener(v -> goToStepTwo());
        botonAtras.setOnClickListener(v -> goToStepOne());
        botonGuardar.setOnClickListener(v -> saveList());

        campoBusquedaTomos.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchVolumes(String.valueOf(s));
            }
        });
    }

    // Carga los datos de la lista existente para edicion.
    private void loadExistingList() {
        barraCarga.setVisibility(View.VISIBLE);
        ThematicListRepository.getThematicListById(listId)
                .addOnSuccessListener(lista -> {
                    barraCarga.setVisibility(View.GONE);
                    if (lista == null) {
                        showMessage(getString(R.string.detalle_lista_no_encontrada));
                        return;
                    }
                    bindExistingData(lista);
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    showMessage(getString(R.string.crear_lista_error_cargar));
                });
    }

    // Copia los datos actuales en los campos visibles.
    private void bindExistingData(ThematicListData lista) {
        campoNombre.setText(lista.nombre);
        campoDescripcion.setText(lista.descripcion);
        spinnerGuia.setSelection(lista.esGuiaDeLectura ? 1 : 0);
    }

    // Pasa al paso de seleccion de tomos.
    private void goToStepTwo() {
        String nombre = sanitizeInput(campoNombre.getText().toString().trim());
        if (TextUtils.isEmpty(nombre)) {
            showMessage(getString(R.string.crear_lista_error_nombre));
            return;
        }
        hideMessage();
        panelPaso1.setVisibility(View.GONE);
        panelPaso2.setVisibility(View.VISIBLE);
        loadAllComicsAndVolumes();
    }

    // Vuelve al paso inicial de datos generales.
    private void goToStepOne() {
        panelPaso2.setVisibility(View.GONE);
        panelPaso1.setVisibility(View.VISIBLE);
    }

    // Carga todos los comics y sus tomos para la busqueda.
    private void loadAllComicsAndVolumes() {
        if (catalogoCargado) {
            if (esModoEdicion && !tomosExistentesCargados) {
                loadExistingVolumes();
            }
            return;
        }

        barraCarga.setVisibility(View.VISIBLE);
        campoBusquedaTomos.setEnabled(false);
        botonGuardar.setEnabled(false);

        ComicDetailRepository.getAllComics()
                .addOnSuccessListener(comics -> {
                    todosLosComics.clear();
                    tomosPorComic.clear();
                    if (comics != null) {
                        todosLosComics.addAll(comics);
                    }
                    loadVolumesForCatalog();
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    campoBusquedaTomos.setEnabled(true);
                    botonGuardar.setEnabled(true);
                    showMessage(getString(R.string.crear_lista_error_catalogo));
                });
    }

    // Carga todos los tomos del catalogo para la busqueda local.
    private void loadVolumesForCatalog() {
        List<Task<?>> tareas = new ArrayList<>();
        for (ComicDetailData comic : todosLosComics) {
            Task<List<VolumeDetailData>> tarea = ComicDetailRepository.getComicVolumes(comic.id)
                    .addOnSuccessListener(tomos -> tomosPorComic.put(comic.id, tomos != null ? tomos : new ArrayList<>()));
            tareas.add(tarea);
        }

        Tasks.whenAll(tareas)
                .addOnSuccessListener(resultado -> {
                    catalogoCargado = true;
                    barraCarga.setVisibility(View.GONE);
                    campoBusquedaTomos.setEnabled(true);
                    botonGuardar.setEnabled(true);
                    if (esModoEdicion && !tomosExistentesCargados) {
                        loadExistingVolumes();
                    }
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    campoBusquedaTomos.setEnabled(true);
                    botonGuardar.setEnabled(true);
                    showMessage(getString(R.string.crear_lista_error_catalogo));
                });
    }

    // Carga los tomos ya guardados cuando la pantalla esta en modo edicion.
    private void loadExistingVolumes() {
        if (TextUtils.isEmpty(listId)) {
            return;
        }
        barraCarga.setVisibility(View.VISIBLE);
        ThematicListRepository.getListVolumes(listId)
                .addOnSuccessListener(tomosLista -> {
                    barraCarga.setVisibility(View.GONE);
                    tomosSeleccionados.clear();
                    if (tomosLista != null) {
                        for (ThematicListVolumeData tomoLista : tomosLista) {
                            VolumeDetailData tomo = findVolume(tomoLista.comicId, tomoLista.tomoId);
                            if (tomo == null) {
                                continue;
                            }
                            tomosSeleccionados.add(new ThematicListVolumeCard(
                                    tomoLista.comicId,
                                    tomoLista.tomoId,
                                    getComicNombre(tomoLista.comicId),
                                    tomo,
                                    tomosSeleccionados.size()
                            ));
                        }
                    }
                    tomosExistentesCargados = true;
                    refreshSelectedVolumes();
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    showMessage(getString(R.string.crear_lista_error_tomos_existentes));
                });
    }

    // Busca tomos segun el texto ingresado.
    private void searchVolumes(String query) {
        contenedorResultados.removeAllViews();
        String queryLimpio = sanitizeSearchInput(query).trim();
        if (TextUtils.isEmpty(queryLimpio)) {
            return;
        }

        QueryBusqueda queryBusqueda = parseSearchQuery(queryLimpio);
        int resultados = 0;

        for (ComicDetailData comic : todosLosComics) {
            if (TextUtils.isEmpty(comic.nombre)) {
                continue;
            }

            String nombreComic = comic.nombre.toLowerCase(Locale.ROOT);
            if (!nombreComic.contains(queryBusqueda.nombreComic)) {
                continue;
            }

            List<VolumeDetailData> tomos = tomosPorComic.get(comic.id);
            if (tomos == null) {
                continue;
            }

            for (VolumeDetailData tomo : tomos) {
                if (!matchesVolumeNumber(queryBusqueda.numeroTomo, tomo)) {
                    continue;
                }
                contenedorResultados.addView(buildSearchResultRow(comic, tomo));
                resultados++;
                if (resultados >= 20) {
                    break;
                }
            }

            if (resultados >= 20) {
                break;
            }
        }

        if (resultados == 0) {
            TextView textoVacio = new TextView(this);
            textoVacio.setText(getString(R.string.crear_lista_sin_resultados));
            textoVacio.setTextColor(getColor(R.color.carousel_subtitle));
            contenedorResultados.addView(textoVacio);
        }
    }

    // Revisa si el tomo coincide con el numero buscado.
    private boolean matchesVolumeNumber(Integer numeroBuscado, VolumeDetailData tomo) {
        if (numeroBuscado == null) {
            return true;
        }
        return tomo.numeroTomo != null && tomo.numeroTomo.equals(numeroBuscado);
    }

    // Construye una fila para un resultado de busqueda.
    private View buildSearchResultRow(ComicDetailData comic, VolumeDetailData tomo) {
        LinearLayout fila = ThematicListUiHelper.createCardContainer(this);
        fila.setBackgroundResource(R.drawable.bg_thematic_list_search_result_card);
        fila.setPadding(
                ThematicListUiHelper.dpToPx(this, 8),
                ThematicListUiHelper.dpToPx(this, 8),
                ThematicListUiHelper.dpToPx(this, 8),
                ThematicListUiHelper.dpToPx(this, 8)
        );
        fila.setOnClickListener(v -> addVolume(comic, tomo));

        LinearLayout contenido = new LinearLayout(this);
        contenido.setOrientation(LinearLayout.HORIZONTAL);
        contenido.addView(ThematicListUiHelper.createMiniCover(this, tomo.getPortadaDataUrl(), 48, 72));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView textoTitulo = ThematicListUiHelper.createTitle(
                this,
                comic.nombre + " - " + tomo.getNumeroFormateado(),
                16f
        );
        textoTitulo.setTextColor(getColor(android.R.color.white));
        info.addView(textoTitulo);

        TextView textoMeta = new TextView(this);
        textoMeta.setText(buildComicMeta(comic));
        textoMeta.setTextColor(getColor(R.color.carousel_subtitle));
        textoMeta.setTextSize(14f);
        info.addView(textoMeta);

        contenido.addView(info);
        fila.addView(contenido);
        return fila;
    }

    // Arma una linea corta con datos del comic.
    private String buildComicMeta(ComicDetailData comic) {
        String editorial = !TextUtils.isEmpty(comic.editorial) ? comic.editorial : "";
        String pais = !TextUtils.isEmpty(comic.paisEditorial) ? comic.paisEditorial : "";
        String autores = comic.getAutoresFormateados();
        StringBuilder constructor = new StringBuilder();
        if (!TextUtils.isEmpty(editorial)) {
            constructor.append(editorial);
        }
        if (!TextUtils.isEmpty(pais)) {
            if (constructor.length() > 0) {
                constructor.append(" · ");
            }
            constructor.append(pais);
        }
        if (!TextUtils.isEmpty(autores)) {
            if (constructor.length() > 0) {
                constructor.append("\n");
            }
            constructor.append(autores);
        }
        return constructor.toString();
    }

    // Agrega un tomo a la lista si no estaba repetido.
    private void addVolume(ComicDetailData comic, VolumeDetailData tomo) {
        for (ThematicListVolumeCard item : tomosSeleccionados) {
            if (TextUtils.equals(item.tomoId, tomo.id)) {
                showMessage(getString(R.string.crear_lista_tomo_duplicado));
                return;
            }
        }

        tomosSeleccionados.add(new ThematicListVolumeCard(
                comic.id,
                tomo.id,
                comic.nombre,
                tomo,
                tomosSeleccionados.size()
        ));
        reindexSelectedVolumes();
        hideMessage();
        campoBusquedaTomos.setText("");
        contenedorResultados.removeAllViews();
        refreshSelectedVolumes();
    }

    // Refresca la vista de tomos seleccionados.
    private void refreshSelectedVolumes() {
        contenedorSeleccionados.removeAllViews();
        textoContador.setText(getString(R.string.crear_lista_tomos_agregados, tomosSeleccionados.size()));

        if (tomosSeleccionados.isEmpty()) {
            TextView textoVacio = new TextView(this);
            textoVacio.setText(getString(R.string.crear_lista_sin_tomos));
            textoVacio.setTextColor(getColor(R.color.carousel_subtitle));
            textoVacio.setTextSize(17f);
            contenedorSeleccionados.addView(textoVacio);
            return;
        }

        for (int i = 0; i < tomosSeleccionados.size(); i++) {
            ThematicListVolumeCard card = tomosSeleccionados.get(i);
            contenedorSeleccionados.addView(buildSelectedVolumeRow(card, i));
        }
    }

    // Construye una fila para un tomo seleccionado.
    private View buildSelectedVolumeRow(ThematicListVolumeCard card, int indice) {
        LinearLayout fila = ThematicListUiHelper.createCardContainer(this);
        fila.setBackgroundResource(R.drawable.bg_thematic_list_search_result_card);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setPadding(
                ThematicListUiHelper.dpToPx(this, 8),
                ThematicListUiHelper.dpToPx(this, 8),
                ThematicListUiHelper.dpToPx(this, 8),
                ThematicListUiHelper.dpToPx(this, 8)
        );

        fila.addView(ThematicListUiHelper.createMiniCover(this,
                card.tomoData != null ? card.tomoData.getPortadaDataUrl() : null,
                60,
                90));

        LinearLayout columnaInfo = new LinearLayout(this);
        columnaInfo.setOrientation(LinearLayout.VERTICAL);
        columnaInfo.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        TextView textoTomo = new TextView(this);
        textoTomo.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        textoTomo.setText(card.comicNombre + " - " + getVolumeLabel(card.tomoData));
        textoTomo.setTextColor(getColor(android.R.color.white));
        textoTomo.setTextSize(17f);
        textoTomo.setTypeface(null, android.graphics.Typeface.BOLD);
        columnaInfo.addView(textoTomo);

        android.view.ContextThemeWrapper contextoDanger = new android.view.ContextThemeWrapper(this, R.style.Theme_Comiku_DangerButton);
        Button botonQuitar = new Button(contextoDanger, null, 0);
        LinearLayout.LayoutParams parametrosBoton = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        parametrosBoton.topMargin = ThematicListUiHelper.dpToPx(this, 8);
        botonQuitar.setLayoutParams(parametrosBoton);
        botonQuitar.setText(getString(R.string.crear_lista_quitar));
        botonQuitar.setOnClickListener(v -> removeSelectedVolume(indice));
        columnaInfo.addView(botonQuitar);

        fila.addView(columnaInfo);

        return fila;
    }

    // Devuelve el texto visible para un tomo.
    private String getVolumeLabel(VolumeDetailData tomo) {
        return tomo != null ? tomo.getNumeroFormateado() : getString(R.string.crear_lista_tomo_sin_datos);
    }

    // Quita un tomo de la lista y refresca el orden.
    private void removeSelectedVolume(int indice) {
        if (indice < 0 || indice >= tomosSeleccionados.size()) {
            return;
        }
        tomosSeleccionados.remove(indice);
        reindexSelectedVolumes();
        refreshSelectedVolumes();
    }

    // Recalcula el orden de los tomos seleccionados.
    private void reindexSelectedVolumes() {
        List<ThematicListVolumeCard> tomosOrdenados = new ArrayList<>();
        for (int i = 0; i < tomosSeleccionados.size(); i++) {
            ThematicListVolumeCard actual = tomosSeleccionados.get(i);
            tomosOrdenados.add(new ThematicListVolumeCard(
                    actual.comicId,
                    actual.tomoId,
                    actual.comicNombre,
                    actual.tomoData,
                    i
            ));
        }
        tomosSeleccionados.clear();
        tomosSeleccionados.addAll(tomosOrdenados);
    }

    // Guarda o actualiza la lista tematica.
    private void saveList() {
        if (guardando) {
            return;
        }
        if (tomosSeleccionados.isEmpty()) {
            showMessage(getString(R.string.crear_lista_minimo_uno));
            return;
        }

        String nombre = sanitizeInput(campoNombre.getText().toString().trim());
        String descripcion = sanitizeInput(campoDescripcion.getText().toString().trim());
        boolean esGuia = spinnerGuia.getSelectedItemPosition() == 1;

        if (TextUtils.isEmpty(nombre)) {
            showMessage(getString(R.string.crear_lista_error_nombre));
            return;
        }

        guardando = true;
        botonGuardar.setEnabled(false);
        barraCarga.setVisibility(View.VISIBLE);
        hideMessage();

        if (esModoEdicion) {
            updateList(nombre, descripcion, esGuia);
        } else {
            createList(nombre, descripcion, esGuia);
        }
    }

    // Crea una nueva lista tematica.
    private void createList(String nombre, String descripcion, boolean esGuia) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            finishWithError(getString(R.string.crear_lista_error_usuario));
            return;
        }

        ThematicListRepository.createThematicList(usuario.getUid(), nombre, descripcion, esGuia)
                .addOnSuccessListener(nuevoListId -> {
                    listaCreadaId = nuevoListId;
                    saveVolumesAndPhotos(nuevoListId);
                })
                .addOnFailureListener(error -> finishWithError(getString(R.string.crear_lista_error_guardar)));
    }

    // Actualiza los datos de una lista existente.
    private void updateList(String nombre, String descripcion, boolean esGuia) {
        ThematicListRepository.updateThematicList(listId, nombre, descripcion, esGuia)
                .addOnSuccessListener(resultado -> ThematicListRepository.clearListVolumes(listId)
                        .addOnSuccessListener(v -> saveVolumesAndPhotos(listId))
                        .addOnFailureListener(error -> finishWithError(getString(R.string.crear_lista_error_guardar))))
                .addOnFailureListener(error -> finishWithError(getString(R.string.crear_lista_error_guardar)));
    }

    // Guarda los tomos y luego actualiza las portadas resumen.
    private void saveVolumesAndPhotos(String idLista) {
        addVolumesSequentially(idLista, 0)
                .addOnSuccessListener(resultado -> buildAndUpdatePhotos(idLista))
                .addOnFailureListener(error -> finishWithError(getString(R.string.crear_lista_error_guardar)));
    }

    // Agrega los tomos seleccionados en orden.
    private Task<Void> addVolumesSequentially(String idLista, int indiceActual) {
        if (indiceActual >= tomosSeleccionados.size()) {
            return Tasks.forResult(null);
        }
        ThematicListVolumeCard card = tomosSeleccionados.get(indiceActual);
        return ThematicListRepository.addVolumeToList(idLista, card.comicId, card.tomoId, indiceActual)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        Exception error = task.getException();
                        throw error != null ? error : new Exception(getString(R.string.crear_lista_error_guardar));
                    }
                    return addVolumesSequentially(idLista, indiceActual + 1);
                });
    }

    // Construye las fotos resumen y las guarda en Firestore.
    private void buildAndUpdatePhotos(String idLista) {
        List<String> fotos = buildListCoverPhotos();
        ThematicListRepository.updateListPhotos(idLista, fotos)
                .addOnSuccessListener(resultado -> finishWithSuccess())
                .addOnFailureListener(error -> finishWithError(getString(R.string.crear_lista_error_guardar)));
    }

    // Construye las primeras portadas de la lista.
    private List<String> buildListCoverPhotos() {
        List<String> fotos = new ArrayList<>();
        int limite = Math.min(3, tomosSeleccionados.size());
        for (int i = 0; i < limite; i++) {
            ThematicListVolumeCard card = tomosSeleccionados.get(i);
            if (card.tomoData == null) {
                continue;
            }
            String portada = compressPortadaDataUrl(card.tomoData.getPortadaDataUrl(), 220, 330, 70);
            if (!TextUtils.isEmpty(portada)) {
                fotos.add(portada);
            }
        }
        return fotos;
    }

    // Comprime una portada al tamano pedido.
    private String compressPortadaDataUrl(String dataUrl, int maxAncho, int maxAlto, int calidad) {
        Bitmap bitmap = ThematicListUiHelper.decodeDataUrl(dataUrl);
        if (bitmap == null) {
            return null;
        }

        int ancho = bitmap.getWidth();
        int alto = bitmap.getHeight();
        float escala = Math.min((float) maxAncho / ancho, (float) maxAlto / alto);
        if (escala < 1f) {
            bitmap = Bitmap.createScaledBitmap(bitmap, Math.round(ancho * escala), Math.round(alto * escala), true);
        }

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.JPEG, calidad, salida);
        return "data:image/jpeg;base64," + android.util.Base64.encodeToString(salida.toByteArray(), android.util.Base64.NO_WRAP);
    }

    // Busca el nombre de un comic segun su id.
    private String getComicNombre(String comicId) {
        for (ComicDetailData comic : todosLosComics) {
            if (TextUtils.equals(comic.id, comicId)) {
                return comic.nombre != null ? comic.nombre : "";
            }
        }
        return "";
    }

    // Busca un tomo cargado localmente segun comic y tomo.
    private VolumeDetailData findVolume(String comicId, String tomoId) {
        List<VolumeDetailData> tomos = tomosPorComic.get(comicId);
        if (tomos == null) {
            return null;
        }
        for (VolumeDetailData tomo : tomos) {
            if (TextUtils.equals(tomo.id, tomoId)) {
                return tomo;
            }
        }
        return null;
    }

    // Limpia los caracteres no permitidos del texto.
    private String sanitizeInput(String valor) {
        return InputValidator.sanitizeForbiddenChars(valor).trim();
    }

    // Une la limpieza de caracteres a un campo de texto.
    private void attachSanitizer(EditText campoTexto, boolean recortarEspacios) {
        campoTexto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String textoLimpio = recortarEspacios
                        ? sanitizeInput(String.valueOf(s))
                        : sanitizeSearchInput(String.valueOf(s));
                if (!TextUtils.equals(textoLimpio, String.valueOf(s))) {
                    campoTexto.removeTextChangedListener(this);
                    campoTexto.setText(textoLimpio);
                    campoTexto.setSelection(textoLimpio.length());
                    campoTexto.addTextChangedListener(this);
                }
            }
        });
    }

    // Analiza la busqueda para separar nombre y numero.
    private QueryBusqueda parseSearchQuery(String texto) {
        String query = sanitizeSearchInput(texto).toLowerCase(Locale.ROOT).trim();
        String[] partes = query.split("\\s+");
        if (partes.length > 1) {
            String ultimaParte = partes[partes.length - 1];
            try {
                int numero = Integer.parseInt(ultimaParte);
                int indiceNumero = query.lastIndexOf(ultimaParte);
                String nombreComic = query.substring(0, indiceNumero).trim();
                if (!TextUtils.isEmpty(nombreComic)) {
                    return new QueryBusqueda(nombreComic, numero);
                }
            } catch (NumberFormatException error) {
                return new QueryBusqueda(query, null);
            }
        }
        return new QueryBusqueda(query, null);
    }

    // Limpia solo los caracteres prohibidos sin tocar los espacios.
    private String sanitizeSearchInput(String valor) {
        return InputValidator.sanitizeForbiddenChars(valor);
    }

    // Muestra un mensaje de error o ayuda.
    private void showMessage(String mensaje) {
        textoMensaje.setText(mensaje);
        textoMensaje.setVisibility(View.VISIBLE);
    }

    // Oculta el mensaje inferior.
    private void hideMessage() {
        textoMensaje.setText("");
        textoMensaje.setVisibility(View.GONE);
    }

    // Finaliza la pantalla con error visible.
    private void finishWithError(String mensaje) {
        barraCarga.setVisibility(View.GONE);
        guardando = false;
        botonGuardar.setEnabled(true);
        showMessage(mensaje);
    }

    // Finaliza la pantalla luego de un guardado correcto.
    private void finishWithSuccess() {
        registerCreatedListActivity();
        barraCarga.setVisibility(View.GONE);
        guardando = false;
        setResult(RESULT_OK);
        finish();
    }

    // Registra la actividad social si la lista fue creada en este flujo.
    private void registerCreatedListActivity() {
        if (esModoEdicion || TextUtils.isEmpty(listaCreadaId)) {
            return;
        }
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            return;
        }
        String nombreLista = sanitizeInput(campoNombre.getText().toString().trim());
        if (TextUtils.isEmpty(nombreLista)) {
            return;
        }
        FriendActivityRepository.appendThematicListActivityForToday(
                usuario.getUid(),
                listaCreadaId,
                nombreLista
        );
    }

    // Navega hacia atras al presionar la flecha del toolbar.
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // Guarda el estado simple de una busqueda de tomos.
    private static final class QueryBusqueda {
        private final String nombreComic;
        private final Integer numeroTomo;

        // Crea un resultado simple de analisis de busqueda.
        private QueryBusqueda(String nombreComic, Integer numeroTomo) {
            this.nombreComic = nombreComic;
            this.numeroTomo = numeroTomo;
        }
    }
}
