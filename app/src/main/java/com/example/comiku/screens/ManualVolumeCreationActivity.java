package com.example.comiku.screens;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.example.comiku.R;
import com.example.comiku.data.model.ComicDraftData;
import com.example.comiku.data.repository.ManualCreationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManualVolumeCreationActivity extends BaseDrawerActivity {
    private static final int TAMANO_MAXIMO_PORTADA_BYTES = 500 * 1024;

    private RadioGroup grupoModoTomo;
    private RadioButton radioModoNumero;
    private RadioButton radioModoUnico;
    private EditText campoNumeroTomo;
    private EditText campoIsbnTomo;
    private EditText campoFechaPublicacionTomo;
    private ImageView imagenPortadaTomo;
    private TextView textoResumenTomos;
    private TextView textoError;
    private TextView textoConfirmacion;
    private ProgressBar barraCarga;
    private Button botonElegirPortada;
    private Button botonAgregarTomoResumen;
    private Button botonFinalizar;

    private final List<DraftVolume> tomosAgregados = new ArrayList<>();
    private byte[] bytesPortadaSeleccionada;
    private String tipoPortadaSeleccionada;
    private String nombrePortadaSeleccionada;
    private String fechaPublicacionSeleccionada = "";
    private String isbnPreseleccionado = "";
    private boolean estaGuardando = false;
    private String rolUsuarioActual = "usuario";
    private ComicDraftData borradorComic;

    private final ActivityResultLauncher<String> selectorPortada = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::handleCoverSelected
    );

    // Inicia la pantalla de carga de tomos.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLoginAndClearStack();
            return;
        }

        borradorComic = readComicDraftFromIntent();
        if (borradorComic == null) {
            finish();
            return;
        }

        isbnPreseleccionado = getIntent().getStringExtra(ManualCreationActivity.EXTRA_PRESELECTED_ISBN);

        setupDrawerShell(getString(R.string.creacion_manual_tomo_titulo));
    }


    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_manual_creation_volume;
    }


    @Override
    protected void onScreenContentReady() {
        bindViews();
        applyPrefilledIsbn();
        setupListeners();
        updateModeUi();
        renderVolumeSummary();
        loadCurrentUserRole();
    }

    // Carga el ISBN recibido desde la pantalla anterior.
    private void applyPrefilledIsbn() {
        if (!TextUtils.isEmpty(isbnPreseleccionado)) {
            campoIsbnTomo.setText(isbnPreseleccionado);
        }
    }


    private void bindViews() {
        grupoModoTomo = findViewById(R.id.grupoModoTomoManual);
        radioModoNumero = findViewById(R.id.radioModoNumeroTomoManual);
        radioModoUnico = findViewById(R.id.radioModoUnicoTomoManual);
        campoNumeroTomo = findViewById(R.id.campoNumeroTomoManual);
        campoIsbnTomo = findViewById(R.id.campoIsbnTomoManual);
        campoFechaPublicacionTomo = findViewById(R.id.campoFechaPublicacionTomoManual);
        imagenPortadaTomo = findViewById(R.id.imagenPortadaTomoManual);
        textoResumenTomos = findViewById(R.id.textoResumenTomosManual);
        textoError = findViewById(R.id.textoErrorCreacionManualTomo);
        textoConfirmacion = findViewById(R.id.textoConfirmacionCreacionManualTomo);
        barraCarga = findViewById(R.id.barraCargaCreacionManualTomo);
        botonElegirPortada = findViewById(R.id.botonElegirPortadaTomoManual);
        botonAgregarTomoResumen = findViewById(R.id.botonAgregarTomoResumenManual);
        botonFinalizar = findViewById(R.id.botonFinalizarCreacionManualTomo);
    }

    // Conecta eventos del formulario.
    private void setupListeners() {
        grupoModoTomo.setOnCheckedChangeListener((grupo, id) -> updateModeUi());
        campoFechaPublicacionTomo.setOnClickListener(v -> openPublicationMonthPicker());
        botonElegirPortada.setOnClickListener(v -> openCoverPicker());
        botonAgregarTomoResumen.setOnClickListener(v -> addVolumeToSummary());
        botonFinalizar.setOnClickListener(v -> finalizeCreation());
    }

    // Lee el borrador de comic recibido por intent.
    private ComicDraftData readComicDraftFromIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getIntent().getSerializableExtra(
                    ManualCreationActivity.EXTRA_COMIC_DRAFT,
                    ComicDraftData.class
            );
        }
        Object extraSerializado = getIntent().getSerializableExtra(ManualCreationActivity.EXTRA_COMIC_DRAFT);
        if (extraSerializado instanceof ComicDraftData) {
            return (ComicDraftData) extraSerializado;
        }
        return null;
    }

    // Obtiene el rol del usuario actual para decidir flujo.
    private void loadCurrentUserRole() {
        FirebaseUser usuarioActual = FirebaseAuth.getInstance().getCurrentUser();
        if (usuarioActual == null) {
            rolUsuarioActual = "usuario";
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("usuario")
                .document(usuarioActual.getUid())
                .get()
                .addOnSuccessListener(this::applyRoleFromProfile)
                .addOnFailureListener(error -> rolUsuarioActual = "usuario");
    }

    // Guarda el rol leido desde perfil.
    private void applyRoleFromProfile(DocumentSnapshot perfil) {
        String rol = perfil.getString("Rol");
        if (TextUtils.isEmpty(rol)) {
            rolUsuarioActual = "usuario";
            return;
        }
        rolUsuarioActual = rol;
    }

    // Abre selector de archivo para portada.
    private void openCoverPicker() {
        if (estaGuardando) {
            return;
        }
        selectorPortada.launch("image/*");
    }

    // Procesa la portada seleccionada.
    private void handleCoverSelected(Uri uriPortada) {
        if (uriPortada == null) {
            return;
        }

        String tipoContenido = getContentResolver().getType(uriPortada);
        if (!isAllowedImageType(tipoContenido)) {
            showError(getString(R.string.error_tipo_foto_no_valido));
            return;
        }

        try {
            if (tipoContenido == null) {
                tipoContenido = "image/jpeg";
            }

            byte[] bytesPortada = readBytesFromUri(uriPortada);
            if (bytesPortada.length > TAMANO_MAXIMO_PORTADA_BYTES) {
                showError(getString(R.string.error_tamano_foto_no_valido));
                return;
            }

            bytesPortadaSeleccionada = bytesPortada;
            tipoPortadaSeleccionada = tipoContenido;
            nombrePortadaSeleccionada = "portada-tomo";
            imagenPortadaTomo.setImageURI(uriPortada);
            imagenPortadaTomo.setVisibility(View.VISIBLE);
            clearMessages();
        } catch (IOException error) {
            showError(getString(R.string.error_lectura_foto));
        }
    }

    // Abre selector de mes y año de publicacion.
    private void openPublicationMonthPicker() {
        if (estaGuardando) {
            return;
        }
        Calendar calendario = Calendar.getInstance();
        int anioInicial = calendario.get(Calendar.YEAR);
        int mesInicial = calendario.get(Calendar.MONTH) + 1;

        if (fechaPublicacionSeleccionada.matches("^\\d{4}-\\d{2}$")) {
            try {
                anioInicial = Integer.parseInt(fechaPublicacionSeleccionada.substring(0, 4));
                mesInicial = Integer.parseInt(fechaPublicacionSeleccionada.substring(5, 7));
            } catch (NumberFormatException ignored) {
            }
        }

        LinearLayout contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.HORIZONTAL);
        contenedor.setPadding(24, 16, 24, 8);

        NumberPicker selectorMes = new NumberPicker(this);
        selectorMes.setMinValue(1);
        selectorMes.setMaxValue(12);
        selectorMes.setValue(Math.max(1, Math.min(mesInicial, 12)));
        selectorMes.setWrapSelectorWheel(true);
        selectorMes.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        selectorMes.setFormatter(valor -> String.format(Locale.US, "%02d", valor));

        NumberPicker selectorAnio = new NumberPicker(this);
        selectorAnio.setMinValue(1900);
        selectorAnio.setMaxValue(2100);
        selectorAnio.setValue(Math.max(1900, Math.min(anioInicial, 2100)));
        selectorAnio.setWrapSelectorWheel(false);
        selectorAnio.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        contenedor.addView(selectorMes, params);
        contenedor.addView(selectorAnio, params);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.creacion_manual_tomo_fecha_publicacion))
                .setView(contenedor)
                .setPositiveButton(android.R.string.ok, (dialogo, which) -> {
                    String valorInterno = String.format(Locale.US, "%04d-%02d", selectorAnio.getValue(), selectorMes.getValue());
                    String valorVisible = String.format(Locale.US, "%02d-%04d", selectorMes.getValue(), selectorAnio.getValue());
                    fechaPublicacionSeleccionada = valorInterno;
                    campoFechaPublicacionTomo.setText(valorVisible);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Agrega el tomo actual al resumen temporal.
    private void addVolumeToSummary() {
        if (estaGuardando) {
            return;
        }

        clearMessages();
        String errorTomo = validateVolumeForm(true);
        if (!TextUtils.isEmpty(errorTomo)) {
            showError(errorTomo);
            return;
        }

        long isbn = Long.parseLong(campoIsbnTomo.getText().toString().trim());
        if (isIsbnInCurrentDraft(isbn)) {
            showError(getString(R.string.creacion_manual_error_isbn_duplicado));
            return;
        }

        setSavingState(true);
        ManualCreationRepository.isbnExists(isbn)
                .addOnSuccessListener(existe -> {
                    if (existe) {
                        setSavingState(false);
                        showError(getString(R.string.creacion_manual_error_isbn_duplicado));
                        return;
                    }
                    DraftVolume tomo = buildVolumeDraft();
                    if (tomo == null) {
                        setSavingState(false);
                        showError(getString(R.string.creacion_manual_error_tomo_invalido));
                        return;
                    }
                    tomosAgregados.add(tomo);
                    resetVolumeInputs();
                    renderVolumeSummary();
                    setSavingState(false);
                    showNotice(getString(R.string.creacion_manual_notice_tomo_agregado));
                })
                .addOnFailureListener(error -> {
                    setSavingState(false);
                    showError(getString(R.string.creacion_manual_error_isbn_validacion));
                });
    }

    // Finaliza el flujo de carga de tomos.
    private void finalizeCreation() {
        if (estaGuardando) {
            return;
        }

        clearMessages();
        if (tomosAgregados.isEmpty()) {
            if (isSingleVolumeMode()) {
                finalizeSingleVolumeDirect();
                return;
            }
            showError(getString(R.string.creacion_manual_error_tomos_requeridos));
            return;
        }

        setSavingState(true);
        if (isAdminRole(rolUsuarioActual)) {
            saveCreationAsAdmin();
        } else {
            saveCreationAsPending();
        }
    }

    // Finaliza tomo unico sin pasar por el resumen.
    private void finalizeSingleVolumeDirect() {
        String errorTomo = validateVolumeForm(true);
        if (!TextUtils.isEmpty(errorTomo)) {
            showError(errorTomo);
            return;
        }

        long isbn = Long.parseLong(campoIsbnTomo.getText().toString().trim());
        if (isIsbnInCurrentDraft(isbn)) {
            showError(getString(R.string.creacion_manual_error_isbn_duplicado));
            return;
        }

        setSavingState(true);
        ManualCreationRepository.isbnExists(isbn)
                .addOnSuccessListener(existe -> {
                    if (existe) {
                        setSavingState(false);
                        showError(getString(R.string.creacion_manual_error_isbn_duplicado));
                        return;
                    }

                    DraftVolume tomo = buildVolumeDraft();
                    if (tomo == null) {
                        setSavingState(false);
                        showError(getString(R.string.creacion_manual_error_tomo_invalido));
                        return;
                    }

                    tomosAgregados.add(tomo);
                    if (isAdminRole(rolUsuarioActual)) {
                        saveCreationAsAdmin();
                    } else {
                        saveCreationAsPending();
                    }
                })
                .addOnFailureListener(error -> {
                    setSavingState(false);
                    showError(getString(R.string.creacion_manual_error_isbn_validacion));
                });
    }

    // Guarda comic y tomos directo en base de datos para admin.
    private void saveCreationAsAdmin() {
        Map<String, Object> comicPayload = borradorComic.toAdminMap();

        ManualCreationRepository.createComic(comicPayload)
                .addOnSuccessListener(comicId -> {
                    if (TextUtils.isEmpty(comicId)) {
                        setSavingState(false);
                        showError(getString(R.string.creacion_manual_error_guardado));
                        return;
                    }
                    saveAdminVolumesRecursively(comicId, 0);
                })
                .addOnFailureListener(error -> {
                    setSavingState(false);
                    showError(getString(R.string.creacion_manual_error_guardado));
                });
    }

    // Guarda cada tomo en orden para admin.
    private void saveAdminVolumesRecursively(String comicId, int indiceTomo) {
        if (indiceTomo >= tomosAgregados.size()) {
            setSavingState(false);
            navegarAInicioConConfirmacion(getString(R.string.creacion_manual_ok_admin));
            return;
        }

        Map<String, Object> datosTomo = tomosAgregados.get(indiceTomo).toFirestoreMap();
        ManualCreationRepository.addComicVolume(comicId, datosTomo)
                .addOnSuccessListener(unused -> saveAdminVolumesRecursively(comicId, indiceTomo + 1))
                .addOnFailureListener(error -> {
                    setSavingState(false);
                    showError(getString(R.string.creacion_manual_error_guardado));
                });
    }

    // Guarda solicitud pendiente para usuario normal.
    private void saveCreationAsPending() {
        FirebaseUser usuarioActual = FirebaseAuth.getInstance().getCurrentUser();
        if (usuarioActual == null) {
            setSavingState(false);
            openLoginAndClearStack();
            return;
        }

        List<Map<String, Object>> tomosPayload = new ArrayList<>();
        for (DraftVolume tomo : tomosAgregados) {
            tomosPayload.add(tomo.toPendingMap());
        }

        ManualCreationRepository.addPendingCreation(
                        "comic_y_tomos",
                        usuarioActual.getUid(),
                        borradorComic.toPendingMap(),
                        null,
                        tomosPayload
                )
                .addOnSuccessListener(idPendiente -> {
                    setSavingState(false);
                    navegarAInicioConConfirmacion(getString(R.string.creacion_manual_ok_pendiente));
                })
                .addOnFailureListener(error -> {
                    setSavingState(false);
                    showError(getString(R.string.creacion_manual_error_guardado));
                });
    }

    // Vuelve al inicio y muestra una confirmacion.
    private void navegarAInicioConConfirmacion(String mensaje) {
        Intent pantallaInicio = new Intent(this, MainActivity.class);
        pantallaInicio.putExtra(MainActivity.EXTRA_HOME_NOTICE, mensaje);
        pantallaInicio.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(pantallaInicio);
        finish();
    }

    // Crea un borrador de tomo desde formulario.
    private DraftVolume buildVolumeDraft() {
        String isbnTexto = campoIsbnTomo.getText().toString().trim();
        if (!isbnTexto.matches("^\\d{1,13}$")) {
            return null;
        }

        Integer numeroTomo = null;
        boolean tomoUnico = isSingleVolumeMode();
        if (!tomoUnico) {
            String numeroTexto = campoNumeroTomo.getText().toString().trim();
            if (!numeroTexto.matches("^\\d{1,4}$")) {
                return null;
            }
            numeroTomo = Integer.parseInt(numeroTexto);
        }

        if (bytesPortadaSeleccionada == null || TextUtils.isEmpty(tipoPortadaSeleccionada)) {
            return null;
        }

        String dataUrlPortada = "data:" + tipoPortadaSeleccionada + ";base64,"
                + Base64.encodeToString(bytesPortadaSeleccionada, Base64.NO_WRAP);

        return new DraftVolume(
                numeroTomo,
                tomoUnico,
                Long.parseLong(isbnTexto),
                fechaPublicacionSeleccionada,
                dataUrlPortada,
                TextUtils.isEmpty(nombrePortadaSeleccionada) ? "portada-tomo" : nombrePortadaSeleccionada,
                tipoPortadaSeleccionada,
                bytesPortadaSeleccionada.length
        );
    }

    // Valida los campos del tomo.
    private String validateVolumeForm(boolean validarPortada) {
        boolean modoUnico = isSingleVolumeMode();
        String numeroTomo = campoNumeroTomo.getText().toString().trim();
        String isbn = campoIsbnTomo.getText().toString().trim();

        if (modoUnico && !tomosAgregados.isEmpty()) {
            return getString(R.string.creacion_manual_error_tomo_unico_lista);
        }

        if (!modoUnico) {
            if (containsSingleVolumeInSummary()) {
                return getString(R.string.creacion_manual_error_lista_tomo_unico);
            }

            if (!numeroTomo.matches("^\\d{1,4}$")) {
                return getString(R.string.creacion_manual_error_numero_tomo);
            }
        } else if (!tomosAgregados.isEmpty()) {
            return getString(R.string.creacion_manual_error_tomo_unico_lista);
        }

        if (!isbn.matches("^\\d{1,13}$")) {
            return getString(R.string.creacion_manual_error_isbn_formato);
        }

        if (!fechaPublicacionSeleccionada.matches("^\\d{4}-\\d{2}$")) {
            return getString(R.string.creacion_manual_error_fecha_publicacion);
        }

        if (validarPortada && (bytesPortadaSeleccionada == null || TextUtils.isEmpty(tipoPortadaSeleccionada))) {
            return getString(R.string.creacion_manual_error_portada_requerida);
        }

        return "";
    }

    // Renderiza resumen de tomos agregados.
    private void renderVolumeSummary() {
        if (tomosAgregados.isEmpty()) {
            textoResumenTomos.setText(getString(R.string.creacion_manual_resumen_vacio));
            updateModeUi();
            return;
        }

        StringBuilder resumen = new StringBuilder();
        resumen.append(getString(R.string.creacion_manual_resumen_titulo)).append("\n");
        for (int i = 0; i < tomosAgregados.size(); i++) {
            DraftVolume tomo = tomosAgregados.get(i);
            resumen.append(i + 1).append(". ");
            if (tomo.tomoUnico) {
                resumen.append(getString(R.string.creacion_manual_resumen_tomo_unico));
            } else {
                resumen.append(getString(R.string.creacion_manual_resumen_numero)).append(" ").append(tomo.numeroTomo);
            }
            resumen.append(" | ISBN ").append(tomo.isbn);
            resumen.append(" | ").append(tomo.fechaPublicacion);
            resumen.append("\n");
        }
        textoResumenTomos.setText(resumen.toString().trim());
        updateModeUi();
    }

    // Ajusta ui del modo de tomo segun estado de formulario y resumen.
    private void updateModeUi() {
        boolean modoUnico = isSingleVolumeMode();
        campoNumeroTomo.setVisibility(modoUnico ? View.GONE : View.VISIBLE);
        botonAgregarTomoResumen.setVisibility(modoUnico ? View.GONE : View.VISIBLE);
        boolean ocultarEstadoVacio = modoUnico && tomosAgregados.isEmpty();
        textoResumenTomos.setVisibility(ocultarEstadoVacio ? View.GONE : View.VISIBLE);

        boolean bloquearCambioModo = containsSingleVolumeInSummary();
        if (bloquearCambioModo) {
            radioModoNumero.setEnabled(false);
            radioModoUnico.setEnabled(false);
            radioModoUnico.setChecked(true);
            campoNumeroTomo.setVisibility(View.GONE);
            botonAgregarTomoResumen.setVisibility(View.GONE);
            botonAgregarTomoResumen.setEnabled(false);
            return;
        }

        boolean hayTomosNumerados = !tomosAgregados.isEmpty();
        if (hayTomosNumerados) {
            radioModoUnico.setEnabled(false);
            radioModoNumero.setChecked(true);
            campoNumeroTomo.setVisibility(View.VISIBLE);
        } else {
            radioModoUnico.setEnabled(!estaGuardando);
        }

        radioModoNumero.setEnabled(!estaGuardando);
        botonAgregarTomoResumen.setEnabled(!estaGuardando);
    }

    // Limpia formulario de tomo luego de agregar.
    private void resetVolumeInputs() {
        if (isSingleVolumeMode()) {
            grupoModoTomo.clearCheck();
            radioModoNumero.setChecked(true);
        }
        campoNumeroTomo.setText("");
        campoIsbnTomo.setText("");
        campoFechaPublicacionTomo.setText("");
        fechaPublicacionSeleccionada = "";
        bytesPortadaSeleccionada = null;
        tipoPortadaSeleccionada = null;
        nombrePortadaSeleccionada = null;
        imagenPortadaTomo.setImageDrawable(null);
        imagenPortadaTomo.setVisibility(View.GONE);
        updateModeUi();
    }


    private void resetAllForms() {
        tomosAgregados.clear();
        resetVolumeInputs();
        renderVolumeSummary();
    }

    // Cambia estado de carga de la pantalla.
    private void setSavingState(boolean guardando) {
        estaGuardando = guardando;
        barraCarga.setVisibility(guardando ? View.VISIBLE : View.GONE);

        grupoModoTomo.setEnabled(!guardando);
        campoNumeroTomo.setEnabled(!guardando);
        campoIsbnTomo.setEnabled(!guardando);
        campoFechaPublicacionTomo.setEnabled(!guardando);
        botonElegirPortada.setEnabled(!guardando);
        botonAgregarTomoResumen.setEnabled(!guardando);
        botonFinalizar.setEnabled(!guardando);
        updateModeUi();
    }

    // Muestra error en pantalla.
    private void showError(String mensaje) {
        textoError.setText(mensaje);
    }

    // Muestra confirmacion en pantalla.
    private void showNotice(String mensaje) {
        textoConfirmacion.setText(mensaje);
    }

    // Limpia mensajes de error y confirmacion.
    private void clearMessages() {
        textoError.setText("");
        textoConfirmacion.setText("");
    }

    // Indica si el modo actual es tomo unico.
    private boolean isSingleVolumeMode() {
        return radioModoUnico.isChecked();
    }

    // Valida si existe tomo unico en el resumen.
    private boolean containsSingleVolumeInSummary() {
        for (DraftVolume tomo : tomosAgregados) {
            if (tomo.tomoUnico) {
                return true;
            }
        }
        return false;
    }

    // Verifica isbn repetido en tomos agregados.
    private boolean isIsbnInCurrentDraft(long isbn) {
        for (DraftVolume tomo : tomosAgregados) {
            if (tomo.isbn == isbn) {
                return true;
            }
        }
        return false;
    }

    // Evalua si el rol es de administrador.
    private boolean isAdminRole(String rol) {
        return String.valueOf(rol).toLowerCase(Locale.ROOT).contains("admin");
    }

    // Valida tipo de imagen permitido.
    private boolean isAllowedImageType(String tipoContenido) {
        return "image/jpeg".equals(tipoContenido)
                || "image/png".equals(tipoContenido)
                || "image/webp".equals(tipoContenido);
    }


    private byte[] readBytesFromUri(Uri uriArchivo) throws IOException {
        InputStream flujo = getContentResolver().openInputStream(uriArchivo);
        if (flujo == null) {
            throw new IOException("No se pudo abrir el archivo.");
        }

        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int leidos;
            while ((leidos = flujo.read(buffer)) != -1) {
                salida.write(buffer, 0, leidos);
            }
            return salida.toByteArray();
        } finally {
            flujo.close();
        }
    }


    private static final class DraftVolume {
        private final Integer numeroTomo;
        private final boolean tomoUnico;
        private final long isbn;
        private final String fechaPublicacion;
        private final String dataUrlPortada;
        private final String nombrePortada;
        private final String tipoPortada;
        private final int tamanoPortada;

        // Crea una instancia de tomo temporal.
        private DraftVolume(
                Integer numeroTomo,
                boolean tomoUnico,
                long isbn,
                String fechaPublicacion,
                String dataUrlPortada,
                String nombrePortada,
                String tipoPortada,
                int tamanoPortada
        ) {
            this.numeroTomo = numeroTomo;
            this.tomoUnico = tomoUnico;
            this.isbn = isbn;
            this.fechaPublicacion = fechaPublicacion;
            this.dataUrlPortada = dataUrlPortada;
            this.nombrePortada = nombrePortada;
            this.tipoPortada = tipoPortada;
            this.tamanoPortada = tamanoPortada;
        }

        // Devuelve estructura para guardar en comics y tomos.
        private Map<String, Object> toFirestoreMap() {
            Map<String, Object> portada = new HashMap<>();
            portada.put("dataUrl", dataUrlPortada);
            portada.put("fileName", nombrePortada);
            portada.put("contentType", tipoPortada);
            portada.put("sizeBytes", tamanoPortada);
            portada.put("source", "firestore-inline");

            Map<String, Object> payload = new HashMap<>();
            payload.put("NumeroTomo", numeroTomo);
            payload.put("TomoUnico", tomoUnico);
            payload.put("ISBN", isbn);
            payload.put("FechaPublicacion", fechaPublicacion);
            payload.put("Portada", portada);
            return payload;
        }

        // Devuelve estructura para guardar en pendientes.
        private Map<String, Object> toPendingMap() {
            Map<String, Object> portada = new HashMap<>();
            portada.put("dataUrl", dataUrlPortada);
            portada.put("fileName", nombrePortada);
            portada.put("contentType", tipoPortada);
            portada.put("sizeBytes", tamanoPortada);
            portada.put("source", "firestore-inline");

            Map<String, Object> payload = new HashMap<>();
            payload.put("numeroTomo", numeroTomo);
            payload.put("tomoUnico", tomoUnico);
            payload.put("isbn", isbn);
            payload.put("fechaPublicacion", fechaPublicacion);
            payload.put("portada", portada);
            return payload;
        }
    }
}
