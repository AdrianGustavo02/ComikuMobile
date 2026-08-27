package com.example.comiku.screens;

import android.app.DatePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comiku.R;
import com.example.comiku.core.ui.ReportFormComponent;
import com.example.comiku.data.model.LibraryReadingEntryData;
import com.example.comiku.data.model.LibraryVolumeReadingData;
import com.example.comiku.data.model.VolumeShelfState;
import com.example.comiku.data.model.ComicDetailData;
import com.example.comiku.data.model.FriendActivityType;
import com.example.comiku.data.model.VolumeDetailData;
import com.example.comiku.data.model.places.NearbyBookstoreDto;
import com.example.comiku.data.repository.ComicDetailRepository;
import com.example.comiku.data.repository.FriendActivityRepository;
import com.example.comiku.data.repository.ReportRepository;
import com.example.comiku.data.repository.UserShelfRepository;
import com.example.comiku.data.repository.NearbyBookstoresRepository;
import com.example.comiku.data.service.RetrofitClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class VolumeDetailActivity extends AppCompatActivity {
    public static final String EXTRA_COMIC_ID = "extra_comic_id";
    public static final String EXTRA_VOLUME_ID = "extra_volume_id";

    private TextView textoNombreComic;
    private TextView textoNumeroTomo;
    private ImageView imagenPortadaTomo;
    private TextView textoIsbn;
    private TextView textoFechaPublicacion;
    private Button botonAgregarBiblioteca;
    private Button botonAgregarDeseados;
    private Button botonReportar;
    private TextView textoEstadoLectura;
    private LinearLayout contenedorLectura;
    private EditText campoFechaLectura;
    private Button botonIngresarFechaLectura;
    private TextView textoSinLecturas;
    private LinearLayout contenedorHistorialLectura;
    private TextView textoAyudaLectura;
    private ProgressBar barraCarga;
    private TextView textoError;

    // Comercios cercanos
    private LinearLayout contenedorComerciosCercanos;
    private Button botonBuscarComerciosCercanos;
    private Button botonRadio20km;
    private Button botonRadio50km;
    private ProgressBar cargaComerciosCercanos;
    private FrameLayout contenedorListaComerciosCercanos;
    private RecyclerView recyclerViewComerciosCercanos;
    private TextView textoComerciosCercanosVacio;
    private TextView textoErrorComerciosCercanos;
    private LinearLayout controlRadioComerciosCercanos;
    private NearbyBookstoresRepository repositorioComerciosCercanos;
    private AdaptadorComerciosCercanos adaptadorComerciosCercanos;
    private FusedLocationProviderClient proveedorUbicacion;
    private int radioActual = 20_000;

    private String comicId;
    private String tomoId;
    private String uidUsuario;
    private ComicDetailData comic;
    private VolumeDetailData tomo;
    private boolean enBiblioteca;
    private boolean enDeseados;
    private Date fechaLecturaSeleccionada;
    private Integer anioLecturaSeleccionada;
    private Integer mesLecturaSeleccionada;
    private Integer diaLecturaSeleccionada;
    private boolean guardandoLectura;
    private LibraryVolumeReadingData datosLectura;
    private static final TimeZone ZONA_LECTURA = TimeZone.getTimeZone("America/Argentina/Buenos_Aires");
    private ReportFormComponent reporteFormularioActual;
    private final ActivityResultLauncher<String> selectorCapturaReporte = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (reporteFormularioActual != null && uri != null) {
                    reporteFormularioActual.handleImageSelected(uri);
                }
            }
    );

    // Inicializa la pantalla de detalles del tomo.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }

        comicId = getIntent().getStringExtra(EXTRA_COMIC_ID);
        tomoId = getIntent().getStringExtra(EXTRA_VOLUME_ID);

        if (TextUtils.isEmpty(comicId) || TextUtils.isEmpty(tomoId)) {
            finish();
            return;
        }

        uidUsuario = FirebaseAuth.getInstance().getCurrentUser().getUid();
        setContentView(R.layout.activity_volume_detail);
        bindViews();
        loadVolumeDetail();
    }

    // Vincula las vistas del layout.
    private void bindViews() {
        textoNombreComic = findViewById(R.id.textoNombreComicVolumeDetail);
        textoNumeroTomo = findViewById(R.id.textoNumeroTomoVolumeDetail);
        imagenPortadaTomo = findViewById(R.id.imagenPortadaVolumeDetail);
        textoIsbn = findViewById(R.id.textoIsbnVolumeDetail);
        textoFechaPublicacion = findViewById(R.id.textoFechaPublicacionVolumeDetail);
        botonAgregarBiblioteca = findViewById(R.id.botonAgregarBibliotecaVolumeDetail);
        botonAgregarDeseados = findViewById(R.id.botonAgregarDeseadosVolumeDetail);
        botonReportar = findViewById(R.id.botonReportarVolumeDetail);
        textoEstadoLectura = findViewById(R.id.textoEstadoLecturaVolumeDetail);
        contenedorLectura = findViewById(R.id.contenedorLecturaVolumeDetail);
        campoFechaLectura = findViewById(R.id.campoFechaLecturaVolumeDetail);
        botonIngresarFechaLectura = findViewById(R.id.botonIngresarFechaLecturaVolumeDetail);
        textoSinLecturas = findViewById(R.id.textoSinLecturasVolumeDetail);
        contenedorHistorialLectura = findViewById(R.id.contenedorHistorialLecturaVolumeDetail);
        textoAyudaLectura = findViewById(R.id.textoAyudaLecturaVolumeDetail);
        barraCarga = findViewById(R.id.barraCargaVolumeDetail);
        textoError = findViewById(R.id.textoErrorVolumeDetail);
        datosLectura = new LibraryVolumeReadingData(false, false, new ArrayList<>(), new ArrayList<>());

        // Comercios cercanos
        contenedorComerciosCercanos = findViewById(R.id.contenedorComerciosCercanos);
        botonBuscarComerciosCercanos = findViewById(R.id.botonBuscarComerciosCercanos);
        botonRadio20km = findViewById(R.id.botonRadio20km);
        botonRadio50km = findViewById(R.id.botonRadio50km);
        cargaComerciosCercanos = findViewById(R.id.cargaComerciosCercanos);
        contenedorListaComerciosCercanos = findViewById(R.id.contenedorListaComerciosCercanos);
        recyclerViewComerciosCercanos = findViewById(R.id.recyclerViewComerciosCercanos);
        textoComerciosCercanosVacio = findViewById(R.id.textoComerciosCercanosVacio);
        textoErrorComerciosCercanos = findViewById(R.id.textoErrorComerciosCercanos);
        controlRadioComerciosCercanos = findViewById(R.id.controlRadioComerciosCercanos);

        // Inicializar repositorio con servicios
        repositorioComerciosCercanos = new NearbyBookstoresRepository(RetrofitClient.INSTANCE.getPlacesApiService(), FirebaseAuth.getInstance());
        proveedorUbicacion = LocationServices.getFusedLocationProviderClient(this);

        // Configurar RecyclerView
        recyclerViewComerciosCercanos.setLayoutManager(new LinearLayoutManager(this));
        adaptadorComerciosCercanos = new AdaptadorComerciosCercanos(new ArrayList<>(), this);
        recyclerViewComerciosCercanos.setAdapter(adaptadorComerciosCercanos);

        // Listeners de botones
        botonBuscarComerciosCercanos.setOnClickListener(v -> buscarComerciosCercanos());
        botonRadio20km.setOnClickListener(v -> cambiarRadioComerciosCercanos(20_000));
        botonRadio50km.setOnClickListener(v -> cambiarRadioComerciosCercanos(50_000));
    }

    // Carga los detalles del tomo desde Firestore.
    private void loadVolumeDetail() {
        barraCarga.setVisibility(View.VISIBLE);

        ComicDetailRepository.getComicDetail(comicId)
                .addOnSuccessListener(comicDetail -> {
                    if (comicDetail != null) {
                        this.comic = comicDetail;
                        loadVolumeData();
                    } else {
                        showError(getString(R.string.error_cargar_tomo));
                        barraCarga.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(error -> {
                    showError(getString(R.string.error_cargar_tomo));
                    barraCarga.setVisibility(View.GONE);
                });
    }

    // Carga los datos del tomo especifico.
    private void loadVolumeData() {
        ComicDetailRepository.getVolumeDetail(comicId, tomoId)
                .addOnSuccessListener(volumeDetail -> {
                    if (volumeDetail != null) {
                        this.tomo = volumeDetail;
                        renderVolumeDetail();
                        loadShelfState();
                    } else {
                        showError(getString(R.string.error_cargar_tomo));
                        barraCarga.setVisibility(View.GONE);
                    }
                })
                .addOnFailureListener(error -> {
                    showError(getString(R.string.error_cargar_tomo));
                    barraCarga.setVisibility(View.GONE);
                });
    }

    // Carga el estado del tomo en biblioteca y deseados.
    private void loadShelfState() {
        UserShelfRepository.getVolumeShelfState(uidUsuario, comicId, tomoId)
                .addOnSuccessListener(estado -> {
                    if (estado != null) {
                        enBiblioteca = estado.enBiblioteca;
                        enDeseados = estado.enDeseados;
                    } else {
                        enBiblioteca = false;
                        enDeseados = false;
                    }
                    setupListeners();
                    updateActionButtons();
                    updateReadingSectionForMembership();
                    loadLibraryReadingData();
                    barraCarga.setVisibility(View.GONE);
                })
                .addOnFailureListener(error -> {
                    showError(getString(R.string.error_cargar_tomo));
                    barraCarga.setVisibility(View.GONE);
                });
    }

    // Renderiza la información del tomo en la pantalla.
    private void renderVolumeDetail() {
        textoNombreComic.setText(comic.nombre);
        textoNumeroTomo.setText(tomo.getNumeroFormateado());

        String dataUrlPortada = tomo.getPortadaDataUrl();
        if (!TextUtils.isEmpty(dataUrlPortada)) {
            Bitmap bitmap = decodeDataUrl(dataUrlPortada);
            if (bitmap != null) {
                imagenPortadaTomo.setImageBitmap(bitmap);
            }
        }

        if (tomo.isbn != null) {
            textoIsbn.setText("ISBN: " + String.valueOf(tomo.isbn));
        } else {
            textoIsbn.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(tomo.fechaPublicacion)) {
            textoFechaPublicacion.setText("Fecha: " + tomo.fechaPublicacion);
        } else {
            textoFechaPublicacion.setVisibility(View.GONE);
        }
    }

    // Conecta acciones de los botones.
    private void setupListeners() {
        botonAgregarBiblioteca.setOnClickListener(v -> addToLibrary());
        botonAgregarDeseados.setOnClickListener(v -> addToWishlist());
        botonReportar.setOnClickListener(v -> openVolumeReportForm());
        campoFechaLectura.setOnClickListener(v -> openReadingDatePicker());
        botonIngresarFechaLectura.setOnClickListener(v -> saveReadingDate());
    }

    // Abre el formulario para reportar el tomo actual.
    private void openVolumeReportForm() {
        if (TextUtils.isEmpty(uidUsuario) || TextUtils.isEmpty(tomoId) || TextUtils.isEmpty(comicId)) {
            showError(getString(R.string.reporte_error_envio));
            return;
        }

        reporteFormularioActual = new ReportFormComponent(
                this,
                getString(R.string.reporte_titulo_tomo),
                getReportReasons(),
                getString(R.string.reporte_confirmacion_tomo),
                (motivo, descripcion, capturaPantalla) -> ReportRepository.createContentReport(
                        uidUsuario,
                        tomoId,
                        "tomo",
                        comicId,
                        motivo,
                        descripcion,
                        capturaPantalla
                ),
                selectorCapturaReporte
        );
        reporteFormularioActual.show();
    }

    // Devuelve los motivos para reportes de contenido.
    private String[] getReportReasons() {
        return new String[]{
                getString(R.string.reporte_motivo_contenido_inapropiado),
                getString(R.string.reporte_motivo_informacion_incorrecta)
        };
    }

    // Agrega el tomo a la biblioteca del usuario.
    private void addToLibrary() {
        if (tomo == null) {
            return;
        }

        setActionButtonsEnabled(false);
        UserShelfRepository.toggleVolumeInLibrary(uidUsuario, comicId, tomoId, tomo.isbn)
                .addOnSuccessListener(estado -> {
                    enBiblioteca = estado.enBiblioteca;
                    enDeseados = estado.enDeseados;
                    updateActionButtons();
                    updateReadingSectionForMembership();
                    loadLibraryReadingData();
                    setActionButtonsEnabled(true);
                    if (enBiblioteca) {
                        registerVolumeActivity(FriendActivityType.LIBRARY_ADD);
                    }
                    Toast.makeText(this,
                            enBiblioteca ? R.string.agregado_biblioteca : R.string.eliminado_biblioteca,
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> {
                    setActionButtonsEnabled(true);
                    showError(getString(R.string.error_guardado_biblioteca));
                });
    }

    // Agrega el tomo a la lista de deseados del usuario.
    private void addToWishlist() {
        if (tomo == null) {
            return;
        }

        setActionButtonsEnabled(false);
        UserShelfRepository.toggleVolumeInWishlist(uidUsuario, comicId, tomoId, tomo.isbn)
                .addOnSuccessListener(estado -> {
                    enBiblioteca = estado.enBiblioteca;
                    enDeseados = estado.enDeseados;
                    updateActionButtons();
                    updateReadingSectionForMembership();
                    if (!enBiblioteca) {
                        applyLibraryData(new LibraryVolumeReadingData(false, false, new ArrayList<>(), new ArrayList<>()));
                    }
                    setActionButtonsEnabled(true);
                    if (enDeseados) {
                        registerVolumeActivity(FriendActivityType.WISHLIST_ADD);
                    }
                    Toast.makeText(this, enDeseados
                            ? R.string.agregado_deseados
                            : R.string.eliminado_deseados, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> {
                    setActionButtonsEnabled(true);
                    showError(getString(R.string.error_guardado_deseados));
                });
    }

    // Actualiza el texto y estado de los botones.
    private void updateActionButtons() {
        botonAgregarBiblioteca.setText(enBiblioteca
                ? R.string.en_biblioteca_agregado
                : R.string.agregar_biblioteca);
        botonAgregarDeseados.setText(enDeseados
                ? R.string.en_deseados_agregado
                : R.string.agregar_deseados);
    }

    // Bloquea o habilita las acciones mientras se guarda.
    private void setActionButtonsEnabled(boolean habilitado) {
        botonAgregarBiblioteca.setEnabled(habilitado);
        botonAgregarDeseados.setEnabled(habilitado);
        campoFechaLectura.setEnabled(habilitado && enBiblioteca && !guardandoLectura);
        botonIngresarFechaLectura.setEnabled(habilitado && enBiblioteca && !guardandoLectura);
    }

    // Carga los datos de lectura del tomo en biblioteca.
    private void loadLibraryReadingData() {
        if (!enBiblioteca) {
            applyLibraryData(new LibraryVolumeReadingData(false, false, new ArrayList<>(), new ArrayList<>()));
            return;
        }

        UserShelfRepository.getLibraryVolumeData(uidUsuario, comicId, tomoId)
                .addOnSuccessListener(this::applyLibraryData)
                .addOnFailureListener(error ->
                        showError(getString(R.string.error_cargar_lecturas_tomo)));
    }

    // Aplica en pantalla el estado de lectura
    private void applyLibraryData(LibraryVolumeReadingData nuevosDatos) {
        datosLectura = nuevosDatos != null
                ? nuevosDatos
                : new LibraryVolumeReadingData(false, false, new ArrayList<>(), new ArrayList<>());

        String textoLeido = datosLectura.leido
                ? getString(R.string.lectura_estado_si)
                : getString(R.string.lectura_estado_no);
        textoEstadoLectura.setText(getString(R.string.lectura_estado_formato, textoLeido));
        textoEstadoLectura.setVisibility(enBiblioteca ? View.VISIBLE : View.GONE);
        renderReadingHistory();
    }

    // Muestra solo los controles de lectura cuando el tomo esta en biblioteca.
    private void updateReadingSectionForMembership() {
        if (enBiblioteca) {
            contenedorLectura.setVisibility(View.VISIBLE);
            textoAyudaLectura.setVisibility(View.GONE);
            textoEstadoLectura.setVisibility(View.VISIBLE);
        } else {
            contenedorLectura.setVisibility(View.GONE);
            textoAyudaLectura.setVisibility(View.VISIBLE);
            textoEstadoLectura.setVisibility(View.GONE);
            fechaLecturaSeleccionada = null;
            anioLecturaSeleccionada = null;
            mesLecturaSeleccionada = null;
            diaLecturaSeleccionada = null;
            campoFechaLectura.setText("");
        }
    }

    // Abre el selector de fecha para registrar una lectura.
    private void openReadingDatePicker() {
        if (!enBiblioteca || guardandoLectura) {
            return;
        }

        Calendar calendario = Calendar.getInstance();
        if (anioLecturaSeleccionada != null && mesLecturaSeleccionada != null && diaLecturaSeleccionada != null) {
            calendario.set(Calendar.YEAR, anioLecturaSeleccionada);
            calendario.set(Calendar.MONTH, mesLecturaSeleccionada);
            calendario.set(Calendar.DAY_OF_MONTH, diaLecturaSeleccionada);
        } else if (fechaLecturaSeleccionada != null) {
            calendario.setTime(fechaLecturaSeleccionada);
        }

        DatePickerDialog selectorFecha = new DatePickerDialog(
                this,
                (vista, anio, mes, dia) -> {
                    anioLecturaSeleccionada = anio;
                    mesLecturaSeleccionada = mes;
                    diaLecturaSeleccionada = dia;
                    fechaLecturaSeleccionada = buildReadingDate(anio, mes, dia);
                    campoFechaLectura.setText(formatReadingDate(fechaLecturaSeleccionada));
                },
                calendario.get(Calendar.YEAR),
                calendario.get(Calendar.MONTH),
                calendario.get(Calendar.DAY_OF_MONTH)
        );
        selectorFecha.getDatePicker().setMaxDate(System.currentTimeMillis());
        selectorFecha.show();
    }

    // Guarda la fecha seleccionada como una lectura del tomo.
    private void saveReadingDate() {
        if (!enBiblioteca) {
            showError(getString(R.string.error_lectura_fuera_biblioteca));
            return;
        }

        if (fechaLecturaSeleccionada == null) {
            showError(getString(R.string.error_fecha_lectura_invalida));
            return;
        }

        Date hoy = cleanTime(buildReadingDateFromDate(new Date()));
        Date fechaSeleccionada = cleanTime(fechaLecturaSeleccionada);
        if (fechaSeleccionada.after(hoy)) {
            showError(getString(R.string.error_fecha_lectura_futura));
            return;
        }

        setReadingMutationState(true);
        UserShelfRepository.addVolumeReading(uidUsuario, comicId, tomoId, fechaSeleccionada)
                .addOnSuccessListener(data -> {
                    applyLibraryData(data);
                    fechaLecturaSeleccionada = null;
                    anioLecturaSeleccionada = null;
                    mesLecturaSeleccionada = null;
                    diaLecturaSeleccionada = null;
                    campoFechaLectura.setText("");
                    Toast.makeText(this, R.string.lectura_guardada_ok, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> showError(
                        error instanceof Exception && !TextUtils.isEmpty(error.getMessage())
                                ? error.getMessage()
                                : getString(R.string.error_guardar_lectura)
                ))
                .addOnCompleteListener(unused -> setReadingMutationState(false));
    }

    // Pide confirmacion para eliminar una fecha de lectura.
    private void requestDeleteReading(LibraryReadingEntryData lectura) {
        if (lectura == null || guardandoLectura) {
            return;
        }

        String fechaFormateada = formatReadingDate(lectura.fecha);
        new AlertDialog.Builder(this)
                .setTitle(R.string.lectura_eliminar_titulo)
                .setMessage(getString(R.string.lectura_eliminar_mensaje, fechaFormateada))
                .setNegativeButton(R.string.lectura_cancelar, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.lectura_eliminar_accion, (dialog, which) ->
                        confirmDeleteReading(lectura.storageIndex))
                .show();
    }

    // Elimina una lectura guardada dentro de Firestore.
    private void confirmDeleteReading(int storageIndex) {
        if (!enBiblioteca) {
            showError(getString(R.string.error_lectura_fuera_biblioteca));
            return;
        }

        setReadingMutationState(true);
        UserShelfRepository.deleteVolumeReading(uidUsuario, comicId, tomoId, storageIndex)
                .addOnSuccessListener(data -> {
                    applyLibraryData(data);
                    Toast.makeText(this, R.string.lectura_eliminada_ok, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> showError(
                        error instanceof Exception && !TextUtils.isEmpty(error.getMessage())
                                ? error.getMessage()
                                : getString(R.string.error_eliminar_lectura)
                ))
                .addOnCompleteListener(unused -> setReadingMutationState(false));
    }

    // Cambia el estado de la pantalla durante guardado o eliminacion de lecturas.
    private void setReadingMutationState(boolean guardando) {
        guardandoLectura = guardando;
        setActionButtonsEnabled(!guardando);
        for (int i = 0; i < contenedorHistorialLectura.getChildCount(); i++) {
            View item = contenedorHistorialLectura.getChildAt(i);
            item.setEnabled(!guardando);
            if (item instanceof LinearLayout) {
                LinearLayout fila = (LinearLayout) item;
                if (fila.getChildCount() > 1) {
                    View botonEliminar = fila.getChildAt(1);
                    if (botonEliminar != null) {
                        botonEliminar.setEnabled(!guardando);
                    }
                }
            }
        }
    }

    // Crea el historial de lecturas registradas para el tomo.
    private void renderReadingHistory() {
        contenedorHistorialLectura.removeAllViews();

        List<LibraryReadingEntryData> lecturas = new ArrayList<>();
        if (datosLectura != null && datosLectura.readingEntries != null) {
            lecturas.addAll(datosLectura.readingEntries);
        }

        lecturas.sort((lecturaA, lecturaB) -> {
            Date fechaA = lecturaA != null && lecturaA.fecha != null ? lecturaA.fecha : new Date(0);
            Date fechaB = lecturaB != null && lecturaB.fecha != null ? lecturaB.fecha : new Date(0);
            return fechaB.compareTo(fechaA);
        });

        if (lecturas.isEmpty()) {
            textoSinLecturas.setVisibility(View.VISIBLE);
            return;
        }

        textoSinLecturas.setVisibility(View.GONE);
        for (LibraryReadingEntryData lectura : lecturas) {
            contenedorHistorialLectura.addView(createReadingRow(lectura));
        }
    }

    // Crea una fila para mostrar una fecha de lectura y su boton de eliminar.
    private View createReadingRow(LibraryReadingEntryData lectura) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        fila.setPadding(0, 0, 0, dpToPx(8));

        TextView fecha = new TextView(this);
        fecha.setText(formatReadingDate(lectura != null ? lectura.fecha : null));
        LinearLayout.LayoutParams paramsFecha = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        fecha.setLayoutParams(paramsFecha);
        fila.addView(fecha);

        Button botonEliminar = new Button(this);
        botonEliminar.setText(R.string.lectura_eliminar_accion);
        botonEliminar.setEnabled(!guardandoLectura);
        botonEliminar.setOnClickListener(v -> requestDeleteReading(lectura));
        fila.addView(botonEliminar);

        return fila;
    }

    // Formatea una fecha para mostrarla al usuario.
    private String formatReadingDate(Date fecha) {
        if (fecha == null) {
            return "";
        }
        SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        formato.setTimeZone(ZONA_LECTURA);
        return formato.format(fecha);
    }

    // Limpia la hora para comparar solo la fecha.
    private Date cleanTime(Date fecha) {
        Calendar calendario = Calendar.getInstance(ZONA_LECTURA);
        calendario.setTime(fecha);
        calendario.set(Calendar.HOUR_OF_DAY, 0);
        calendario.set(Calendar.MINUTE, 0);
        calendario.set(Calendar.SECOND, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        return calendario.getTime();
    }

    // Crea una fecha de lectura
    private Date buildReadingDate(int anio, int mes, int dia) {
        Calendar calendario = Calendar.getInstance(ZONA_LECTURA);
        calendario.clear();
        calendario.set(anio, mes, dia, 0, 0, 0);
        calendario.set(Calendar.MILLISECOND, 0);
        return calendario.getTime();
    }

    // Convierte una fecha cualquiera a la zona usada para lecturas.
    private Date buildReadingDateFromDate(Date fecha) {
        Calendar calendario = Calendar.getInstance(ZONA_LECTURA);
        calendario.setTime(fecha);
        return calendario.getTime();
    }

    // Registra una actividad social cuando se agrega un tomo.
    private void registerVolumeActivity(FriendActivityType tipoActividad) {
        if (tomo == null || TextUtils.isEmpty(uidUsuario) || TextUtils.isEmpty(comicId) || TextUtils.isEmpty(tomoId)) {
            return;
        }
        FriendActivityRepository.appendVolumeActivityForToday(
                uidUsuario,
                tipoActividad,
                comicId,
                tomoId,
                tomo.getPortadaDataUrl()
        );
    }

    // Convierte dp a pixeles para crear vistas dinamicas.
    private int dpToPx(int dp) {
        float densidad = getResources().getDisplayMetrics().density;
        return Math.round(dp * densidad);
    }

    // Convierte dataUrl a bitmap.
    private Bitmap decodeDataUrl(String dataUrl) {
        if (TextUtils.isEmpty(dataUrl)) {
            return null;
        }

        int indiceComa = dataUrl.indexOf(',');
        if (indiceComa < 0 || indiceComa >= dataUrl.length() - 1) {
            return null;
        }

        String base64 = dataUrl.substring(indiceComa + 1);
        try {
            byte[] bytesImagen = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytesImagen, 0, bytesImagen.length);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    // Muestra un mensaje de error.
    private void showError(String mensaje) {
        textoError.setText(mensaje);
        textoError.setVisibility(View.VISIBLE);
    }

    // Busca comercios cercanos con permisos de ubicacion.
    private void buscarComerciosCercanos() {
        // Verificar permisos de ubicacion.
        if (!hasLocationPermission()) {
            // Solicitar permisos
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    100);
            return;
        }

        // Obtener ubicacion
        cargaComerciosCercanos.setVisibility(View.VISIBLE);
        contenedorListaComerciosCercanos.setVisibility(View.GONE);
        textoComerciosCercanosVacio.setVisibility(View.GONE);
        textoErrorComerciosCercanos.setVisibility(View.GONE);

        if (!hasLocationPermission()) {
            mostrarErrorComerciosCercanos("Permiso de ubicacion denegado");
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mostrarErrorComerciosCercanos("Permiso de ubicacion denegado");
            return;
        }
        proveedorUbicacion.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                buscarComerciosConUbicacion(location.getLatitude(), location.getLongitude());
            } else {
                // Si no hay ubicacion reciente, solicitar ubicacion actual
                solicitarUbicacionActual();
            }
        }).addOnFailureListener(e -> mostrarErrorComerciosCercanos("No se pudo obtener tu ubicacion"));
    }

    // Solicita ubicacion actual si no hay una ubicacion reciente.
    @SuppressLint("MissingPermission")
    private void solicitarUbicacionActual() {
        if (!hasLocationPermission()) {
            return;
        }

        com.google.android.gms.location.LocationRequest solicitudUbicacion =
            com.google.android.gms.location.LocationRequest.create()
                .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setNumUpdates(1);

        proveedorUbicacion.requestLocationUpdates(solicitudUbicacion, new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(com.google.android.gms.location.LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if (locationResult != null && locationResult.getLastLocation() != null) {
                    buscarComerciosConUbicacion(
                        locationResult.getLastLocation().getLatitude(),
                        locationResult.getLastLocation().getLongitude()
                    );
                }
            }
        }, getMainLooper());
    }

    // Busca comercios con coordenadas especificas.
    private void buscarComerciosConUbicacion(double latitud, double longitud) {
        new Thread(() -> {
            try {
                List<NearbyBookstoreDto> comercios = repositorioComerciosCercanos.searchSync(latitud, longitud, radioActual);

                runOnUiThread(() -> {
                    cargaComerciosCercanos.setVisibility(View.GONE);
                    controlRadioComerciosCercanos.setVisibility(View.VISIBLE);

                    if (comercios != null && !comercios.isEmpty()) {
                        adaptadorComerciosCercanos.actualizarDatos(comercios);
                        contenedorListaComerciosCercanos.setVisibility(View.VISIBLE);
                        textoComerciosCercanosVacio.setVisibility(View.GONE);
                    } else {
                        mostrarComerciosCercanosVacio();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> mostrarErrorComerciosCercanos(e.getMessage()));
            }
        }).start();
    }

    // Cambia el radio de busqueda de comercios.
    private void cambiarRadioComerciosCercanos(int nuevoRadio) {
        if (radioActual == nuevoRadio) return;

        radioActual = nuevoRadio;
        cargaComerciosCercanos.setVisibility(View.VISIBLE);
        contenedorListaComerciosCercanos.setVisibility(View.GONE);
        textoComerciosCercanosVacio.setVisibility(View.GONE);

        // Buscar de nuevo con nuevo radio
        if (hasLocationPermission()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                mostrarErrorComerciosCercanos("Permiso de ubicacion denegado");
                return;
            }
            proveedorUbicacion.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    buscarComerciosConUbicacion(location.getLatitude(), location.getLongitude());
                }
            });
        }
    }

    // Muestra mensaje de error en comercios cercanos.
    private void mostrarErrorComerciosCercanos(String mensaje) {
        cargaComerciosCercanos.setVisibility(View.GONE);
        contenedorListaComerciosCercanos.setVisibility(View.GONE);
        textoComerciosCercanosVacio.setVisibility(View.GONE);
        textoErrorComerciosCercanos.setVisibility(View.VISIBLE);
        textoErrorComerciosCercanos.setText(mensaje != null ? mensaje : "Error");
    }

    // Muestra mensaje de sin resultados en comercios cercanos.
    private void mostrarComerciosCercanosVacio() {
        cargaComerciosCercanos.setVisibility(View.GONE);
        contenedorListaComerciosCercanos.setVisibility(View.GONE);
        textoComerciosCercanosVacio.setVisibility(View.VISIBLE);
        textoErrorComerciosCercanos.setVisibility(View.GONE);
    }

    // Valida si la app tiene algun permiso de ubicacion.
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Maneja resultado de solicitud de permisos.
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (hasLocationPermission()) {
                buscarComerciosCercanos();
            } else {
                mostrarErrorComerciosCercanos("Permiso de ubicacion denegado");
            }
        }
    }
}
