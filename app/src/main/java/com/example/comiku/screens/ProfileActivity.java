package com.example.comiku.screens;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.example.comiku.R;
import com.example.comiku.core.image.ImageCropperConfig;
import com.example.comiku.data.repository.FriendshipRepository;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends BaseDrawerActivity {
    private static final int EDAD_MINIMA_REGISTRO = 18;
    private static final int TAMANO_MAXIMO_FOTO_BYTES = 500 * 1024;

    private EditText campoNombre;
    private EditText campoApellido;
    private EditText campoNick;
    private EditText campoFechaNacimiento;
    private TextView textoEmail;
    private TextView textoTotalComics;
    private TextView textoTotalTomos;
    private TextView textoTotalAmigos;
    private TextView textoError;
    private ProgressBar barraCarga;
    private ImageView imagenPerfil;
    private Button botonCambiarFoto;
    private Button botonCancelar;
    private Button botonEditarGuardar;
    private Button botonAmistad;
    private Button botonBloquearUsuario;
    private Button botonReportarUsuario;
    private Button botonUsuariosBloqueados;
    private Button botonEliminarCuenta;

    private ListenerRegistration escuchadorPerfil;
    private boolean estaEditando = false;
    private boolean estaGuardando = false;
    private long milisegundosCumpleanos = -1L;
    private byte[] bytesFotoSeleccionada;
    private String tipoFotoSeleccionada;
    private String nombreFotoSeleccionada;

    private String nombreOriginal = "";
    private String apellidoOriginal = "";
    private String nickOriginal = "";
    private long cumpleanosOriginal = -1L;
    private String emailOriginal = "";
    private int totalComicsOriginal = 0;
    private int totalTomosOriginal = 0;
    private int totalAmigosOriginal = 0;
    private String dataUrlFotoOriginal = "";
    private static final int ESTADO_AMISTAD_NINGUNA = 0;
    private static final int ESTADO_AMISTAD_SOLICITUD_ENVIADA = 1;
    private static final int ESTADO_AMISTAD_CONFIRMADA = 2;

    private String uidUsuarioActual = "";
    private String uidPerfilExterno = "";
    private boolean procesandoAmistad = false;
    private int estadoAmistadActual = ESTADO_AMISTAD_NINGUNA;
    private ReportUserDialog reportUserDialog;

    private final ActivityResultLauncher<String> selectorFotoPerfil = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::handlePhotoSelected
    );
    private final ActivityResultLauncher<Intent> recortadorFotoPerfil = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                if (resultado.getResultCode() != RESULT_OK || resultado.getData() == null) {
                    return;
                }
                String rutaRecortada = resultado.getData().getStringExtra(ImageCropperConfig.EXTRA_RESULT_URI);
                if (TextUtils.isEmpty(rutaRecortada)) {
                    return;
                }
                handleCroppedPhotoSelected(Uri.parse(rutaRecortada));
            }
    );
    private final ActivityResultLauncher<String> selectorImagenReporteUsuario = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (reportUserDialog != null && uri != null) {
                    reportUserDialog.handleImageSelected(uri);
                }
            }
    );

    public static final String EXTRA_USER_ID = "extra_user_id";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLoginAndClearStack();
            return;
        }
        setupDrawerShell(getString(R.string.perfil_titulo));
    }


    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_profile;
    }


    @Override
    protected void onScreenContentReady() {
        bindViews();
        FirebaseUser usuarioActual = FirebaseAuth.getInstance().getCurrentUser();
        uidUsuarioActual = usuarioActual != null ? usuarioActual.getUid() : "";
        uidPerfilExterno = getIntent().getStringExtra(EXTRA_USER_ID);
        boolean esPerfilvPropio = TextUtils.isEmpty(uidPerfilExterno)
                || (!TextUtils.isEmpty(uidUsuarioActual) && uidPerfilExterno.equals(uidUsuarioActual));

        if (esPerfilvPropio) {
            uidPerfilExterno = uidUsuarioActual;
            setupListeners();
            setEditingMode(false);
            botonAmistad.setVisibility(View.GONE);
            botonBloquearUsuario.setVisibility(View.GONE);
            botonReportarUsuario.setVisibility(View.GONE);
            botonUsuariosBloqueados.setVisibility(View.VISIBLE);
            botonEliminarCuenta.setVisibility(View.VISIBLE);
            listenUserProfile();
        } else {
            // Modo solo lectura para ver el perfil de otro usuario.
            botonCambiarFoto.setVisibility(View.GONE);
            botonCancelar.setVisibility(View.GONE);
            botonEditarGuardar.setVisibility(View.GONE);
            botonAmistad.setVisibility(View.VISIBLE);
            botonBloquearUsuario.setVisibility(View.VISIBLE);
            botonReportarUsuario.setVisibility(View.VISIBLE);
            botonUsuariosBloqueados.setVisibility(View.GONE);
            botonEliminarCuenta.setVisibility(View.GONE);
            campoNombre.setEnabled(false);
            campoApellido.setEnabled(false);
            campoNick.setEnabled(false);
            campoFechaNacimiento.setEnabled(false);
            setupFriendshipButton();
            setupBlockButton();
            setupReportButton();
            loadExternalProfile(uidPerfilExterno);
            refreshFriendshipState();
        }
    }

    // Carga el perfil de otro usuario en modo solo lectura.
    private void loadExternalProfile(String uidExterno) {
        FriendshipRepository.canOpenUserProfile(uidUsuarioActual, uidExterno)
                .addOnSuccessListener(canOpen -> {
                    if (!Boolean.TRUE.equals(canOpen)) {
                        showError(getString(R.string.perfil_acceso_bloqueado));
                        finish();
                        return;
                    }

                    FirebaseFirestore.getInstance()
                            .collection("usuario")
                            .document(uidExterno)
                            .get()
                            .addOnSuccessListener(this::applyProfileSnapshot)
                            .addOnFailureListener(error -> showError(getString(R.string.error_perfil_carga)));
                })
                .addOnFailureListener(error -> showError(getString(R.string.error_perfil_carga)));
    }

    // Vincula controles del formulario de perfil.
    private void bindViews() {
        campoNombre = findViewById(R.id.campoNombrePerfil);
        campoApellido = findViewById(R.id.campoApellidoPerfil);
        campoNick = findViewById(R.id.campoNickPerfil);
        campoFechaNacimiento = findViewById(R.id.campoFechaNacimientoPerfil);
        textoEmail = findViewById(R.id.textoEmailPerfil);
        textoTotalComics = findViewById(R.id.textoTotalComicsPerfil);
        textoTotalTomos = findViewById(R.id.textoTotalTomosPerfil);
        textoTotalAmigos = findViewById(R.id.textoTotalAmigosPerfil);
        textoError = findViewById(R.id.textoErrorPerfil);
        barraCarga = findViewById(R.id.barraCargaPerfil);
        imagenPerfil = findViewById(R.id.imagenPerfil);
        botonCambiarFoto = findViewById(R.id.botonCambiarFotoPerfil);
        botonCancelar = findViewById(R.id.botonCancelarPerfil);
        botonEditarGuardar = findViewById(R.id.botonEditarGuardarPerfil);
        botonAmistad = findViewById(R.id.botonAmistadPerfil);
        botonBloquearUsuario = findViewById(R.id.botonBloquearPerfil);
        botonReportarUsuario = findViewById(R.id.botonReportarUsuarioPerfil);
        botonUsuariosBloqueados = findViewById(R.id.botonUsuariosBloqueadosPerfil);
        botonEliminarCuenta = findViewById(R.id.botonEliminarCuentaPerfil);
    }

    // Conecta acciones de edicion del perfil.
    private void setupListeners() {
        botonCambiarFoto.setOnClickListener(v -> openPhotoPicker());
        campoFechaNacimiento.setOnClickListener(v -> openBirthdayPicker());
        botonCancelar.setOnClickListener(v -> handleCancel());
        botonEditarGuardar.setOnClickListener(v -> handleEditOrSave());
        botonBloquearUsuario.setOnClickListener(v -> confirmBlockUser());
        botonUsuariosBloqueados.setOnClickListener(v -> openBlockedUsersScreen());
        botonEliminarCuenta.setOnClickListener(v -> mostrarDialogoEliminarCuenta());
    }

    // Conecta el boton de amistad en perfil externo.
    private void setupFriendshipButton() {
        botonAmistad.setOnClickListener(v -> handleFriendshipAction());
    }

    // Conecta el boton de bloqueo en perfil externo.
    private void setupBlockButton() {
        botonBloquearUsuario.setOnClickListener(v -> confirmBlockUser());
    }

    // Conecta el boton de reporte en perfil externo.
    private void setupReportButton() {
        botonReportarUsuario.setOnClickListener(v -> showReportDialog());
    }

    // Muestra el dialogo para reportar un usuario.
    private void showReportDialog() {
        String nickUsuario = campoNick.getText().toString().trim();
        if (TextUtils.isEmpty(nickUsuario)) {
            showError("No se pudo obtener el nick del usuario.");
            return;
        }

        reportUserDialog = new ReportUserDialog(
            ProfileActivity.this,
            uidPerfilExterno,
            nickUsuario,
            new ReportUserDialog.OnReportCompleted() {
                @Override
                public void onSuccess() {
                    showError("Reporte enviado correctamente. Gracias por ayudar a mantener la comunidad segura.");
                }

                @Override
                public void onError(String mensaje) {
                    showError("Error al enviar reporte: " + mensaje);
                }
            },
            selectorImagenReporteUsuario
        );
        reportUserDialog.mostrar();
    }

    // Resuelve la accion segun el estado actual de amistad.
    private void handleFriendshipAction() {
        if (procesandoAmistad || TextUtils.isEmpty(uidUsuarioActual) || TextUtils.isEmpty(uidPerfilExterno)) {
            return;
        }

        procesandoAmistad = true;
        botonAmistad.setEnabled(false);
        showError("");

        if (estadoAmistadActual == ESTADO_AMISTAD_CONFIRMADA) {
            FriendshipRepository.removeFriend(uidUsuarioActual, uidPerfilExterno)
                    .addOnSuccessListener(unused -> {
                        procesandoAmistad = false;
                        refreshFriendshipState();
                    })
                    .addOnFailureListener(error -> {
                        procesandoAmistad = false;
                        botonAmistad.setEnabled(true);
                        showError(resolveFriendshipError(error, R.string.error_amistad_eliminar));
                    });
            return;
        }

        if (estadoAmistadActual == ESTADO_AMISTAD_SOLICITUD_ENVIADA) {
            FriendshipRepository.cancelFriendRequest(uidUsuarioActual, uidPerfilExterno)
                    .addOnSuccessListener(unused -> {
                        procesandoAmistad = false;
                        refreshFriendshipState();
                    })
                    .addOnFailureListener(error -> {
                        procesandoAmistad = false;
                        botonAmistad.setEnabled(true);
                        showError(resolveFriendshipError(error, R.string.error_amistad_cancelar));
                    });
            return;
        }

        FriendshipRepository.sendFriendRequest(uidUsuarioActual, uidPerfilExterno)
                .addOnSuccessListener(unused -> {
                    procesandoAmistad = false;
                    refreshFriendshipState();
                })
                .addOnFailureListener(error -> {
                    procesandoAmistad = false;
                    botonAmistad.setEnabled(true);
                    showError(resolveFriendshipError(error, R.string.error_amistad_enviar));
                });
    }

    // Carga el estado de amistad para decidir texto y accion del boton.
    private void refreshFriendshipState() {
        if (TextUtils.isEmpty(uidUsuarioActual) || TextUtils.isEmpty(uidPerfilExterno)) {
            return;
        }

        botonAmistad.setText(getString(R.string.perfil_boton_amistad_cargando));
        botonAmistad.setEnabled(false);

        FriendshipRepository.areFriends(uidUsuarioActual, uidPerfilExterno)
                .addOnSuccessListener(esAmigo -> {
                    if (Boolean.TRUE.equals(esAmigo)) {
                        updateFriendshipButton(ESTADO_AMISTAD_CONFIRMADA);
                        return;
                    }
                    FriendshipRepository.hasSentRequest(uidUsuarioActual, uidPerfilExterno)
                            .addOnSuccessListener(tieneSolicitud -> {
                                if (Boolean.TRUE.equals(tieneSolicitud)) {
                                    updateFriendshipButton(ESTADO_AMISTAD_SOLICITUD_ENVIADA);
                                } else {
                                    updateFriendshipButton(ESTADO_AMISTAD_NINGUNA);
                                }
                            })
                            .addOnFailureListener(error -> {
                                updateFriendshipButton(ESTADO_AMISTAD_NINGUNA);
                                showError(getString(R.string.error_amistad_carga));
                            });
                })
                .addOnFailureListener(error -> {
                    updateFriendshipButton(ESTADO_AMISTAD_NINGUNA);
                    showError(getString(R.string.error_amistad_carga));
                });
    }

    // Aplica texto y estado visual del boton de amistad.
    private void updateFriendshipButton(int estadoAmistad) {
        estadoAmistadActual = estadoAmistad;
        botonAmistad.setEnabled(!procesandoAmistad);
        if (estadoAmistad == ESTADO_AMISTAD_CONFIRMADA) {
            botonAmistad.setText(getString(R.string.perfil_boton_amigos));
            return;
        }
        if (estadoAmistad == ESTADO_AMISTAD_SOLICITUD_ENVIADA) {
            botonAmistad.setText(getString(R.string.perfil_boton_cancelar_solicitud));
            return;
        }
        botonAmistad.setText(getString(R.string.perfil_boton_enviar_solicitud));
    }

    // Devuelve el mensaje de error para acciones de amistad.
    private String resolveFriendshipError(Exception error, int resIdDefault) {
        if (error != null && !TextUtils.isEmpty(error.getMessage())) {
            return error.getMessage();
        }
        return getString(resIdDefault);
    }

    // Abre la pantalla con la lista de usuarios bloqueados.
    private void openBlockedUsersScreen() {
        Intent intento = new Intent(this, BlockedUsersActivity.class);
        startActivity(intento);
    }

    // Pide confirmacion antes de bloquear al usuario actual del perfil.
    private void confirmBlockUser() {
        if (TextUtils.isEmpty(uidPerfilExterno) || uidPerfilExterno.equals(uidUsuarioActual)) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.perfil_bloquear_confirmacion_titulo))
                .setMessage(getString(R.string.perfil_bloquear_confirmacion_mensaje))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> handleBlockUser())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Bloquea al usuario externo y vuelve a la pantalla anterior.
    private void handleBlockUser() {
        if (TextUtils.isEmpty(uidUsuarioActual) || TextUtils.isEmpty(uidPerfilExterno)) {
            return;
        }

        botonBloquearUsuario.setEnabled(false);
        FriendshipRepository.blockUser(uidUsuarioActual, uidPerfilExterno)
                .addOnSuccessListener(unused -> {
                    showError(getString(R.string.perfil_bloquear_exitoso));
                    finish();
                })
                .addOnFailureListener(error -> {
                    showError(resolveFriendshipError(error, R.string.perfil_acceso_bloqueado));
                    botonBloquearUsuario.setEnabled(true);
                });
    }

    // Escucha cambios del documento usuario actual.
    private void listenUserProfile() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            openLoginAndClearStack();
            return;
        }

        DocumentReference referenciaPerfil = FirebaseFirestore.getInstance()
                .collection("usuario")
                .document(usuario.getUid());

        escuchadorPerfil = referenciaPerfil.addSnapshotListener((documento, error) -> {
            if (error != null || documento == null || !documento.exists()) {
                showError(getString(R.string.error_perfil_carga));
                return;
            }
            applyProfileSnapshot(documento);
        });
    }

    // Aplica snapshot de perfil en pantalla.
    private void applyProfileSnapshot(DocumentSnapshot documento) {
        nombreOriginal = readString(documento, "Nombre");
        apellidoOriginal = readString(documento, "Apellido");
        nickOriginal = readString(documento, "Nick");
        emailOriginal = readString(documento, "Email");
        totalComicsOriginal = readInt(documento, "totalComics");
        totalTomosOriginal = readInt(documento, "totalTomos");
        totalAmigosOriginal = readInt(documento, "cantidadAmigos");
        milisegundosCumpleanos = readBirthdayMillis(documento);
        cumpleanosOriginal = milisegundosCumpleanos;
        dataUrlFotoOriginal = extractPhotoDataUrl(documento.get("FotoPerfil"));

        if (estaEditando) {
            return;
        }

        campoNombre.setText(nombreOriginal);
        campoApellido.setText(apellidoOriginal);
        campoNick.setText(nickOriginal);
        campoFechaNacimiento.setText(formatDate(milisegundosCumpleanos));
        textoEmail.setText(emailOriginal);
        textoTotalComics.setText(String.valueOf(totalComicsOriginal));
        textoTotalTomos.setText(String.valueOf(totalTomosOriginal));
        textoTotalAmigos.setText(String.valueOf(totalAmigosOriginal));
        renderProfilePhoto();
    }

    // Alterna entre edicion y guardado.
    private void handleEditOrSave() {
        if (estaGuardando) {
            return;
        }

        if (!estaEditando) {
            setEditingMode(true);
            return;
        }

        saveProfileChanges();
    }

    // Cancela cambios y vuelve a modo lectura.
    private void handleCancel() {
        if (estaGuardando) {
            return;
        }

        campoNombre.setText(nombreOriginal);
        campoApellido.setText(apellidoOriginal);
        campoNick.setText(nickOriginal);
        milisegundosCumpleanos = cumpleanosOriginal;
        campoFechaNacimiento.setText(formatDate(milisegundosCumpleanos));
        bytesFotoSeleccionada = null;
        tipoFotoSeleccionada = null;
        nombreFotoSeleccionada = null;
        renderProfilePhoto();
        setEditingMode(false);
        showError("");
    }

    // Guarda cambios validos en firestore.
    private void saveProfileChanges() {
        String nombre = campoNombre.getText().toString().trim();
        String apellido = campoApellido.getText().toString().trim();
        String nick = campoNick.getText().toString().trim();

        String errorFormulario = validateProfileForm(nombre, apellido, nick, milisegundosCumpleanos);
        if (!TextUtils.isEmpty(errorFormulario)) {
            showError(errorFormulario);
            return;
        }

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            openLoginAndClearStack();
            return;
        }

        showError("");
        setSavingState(true);

        FirebaseFirestore.getInstance()
                .collection("usuario")
                .whereEqualTo("Nick", nick)
                .limit(1)
                .get()
                .addOnSuccessListener(resultado -> {
                    if (!resultado.isEmpty()
                            && !usuario.getUid().equals(resultado.getDocuments().get(0).getId())) {
                        setSavingState(false);
                        showError(getString(R.string.error_nick_duplicado));
                        return;
                    }
                    writeProfileDocument(usuario.getUid(), nombre, apellido, nick);
                })
                .addOnFailureListener(error -> {
                    setSavingState(false);
                    showError(getString(R.string.error_nick_verificacion));
                });
    }

    // Escribe datos editables y foto en el documento.
    private void writeProfileDocument(String uid, String nombre, String apellido, String nick) {
        Map<String, Object> actualizacion = new HashMap<>();
        actualizacion.put("Nombre", nombre);
        actualizacion.put("Apellido", apellido);
        actualizacion.put("Nick", nick);
        actualizacion.put("FechaNacimiento", new Timestamp(new Date(milisegundosCumpleanos)));

        Map<String, Object> fotoPerfil = buildPhotoPayloadOrNull();
        if (fotoPerfil != null) {
            actualizacion.put("FotoPerfil", fotoPerfil);
        }

        FirebaseFirestore.getInstance()
                .collection("usuario")
                .document(uid)
                .set(actualizacion, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    setSavingState(false);
                    setEditingMode(false);
                    bytesFotoSeleccionada = null;
                    tipoFotoSeleccionada = null;
                    nombreFotoSeleccionada = null;
                })
                .addOnFailureListener(error -> {
                    setSavingState(false);
                    showError(getString(R.string.error_perfil_guardado));
                });
    }

    // Habilita campos y acciones de edicion.
    private void setEditingMode(boolean editando) {
        estaEditando = editando;
        campoNombre.setEnabled(editando);
        campoApellido.setEnabled(editando);
        campoNick.setEnabled(editando);
        campoFechaNacimiento.setEnabled(editando);
        botonCambiarFoto.setEnabled(editando && !estaGuardando);
        botonCancelar.setVisibility(editando ? View.VISIBLE : View.GONE);
        botonEditarGuardar.setText(editando
                ? getString(R.string.perfil_boton_guardar)
                : getString(R.string.perfil_boton_editar));
    }

    // Actualiza estado visual durante guardado.
    private void setSavingState(boolean guardando) {
        estaGuardando = guardando;
        barraCarga.setVisibility(guardando ? View.VISIBLE : View.GONE);
        botonEditarGuardar.setEnabled(!guardando);
        botonCancelar.setEnabled(!guardando);
        botonCambiarFoto.setEnabled(estaEditando && !guardando);
        campoNombre.setEnabled(estaEditando && !guardando);
        campoApellido.setEnabled(estaEditando && !guardando);
        campoNick.setEnabled(estaEditando && !guardando);
        campoFechaNacimiento.setEnabled(estaEditando && !guardando);
    }

    // Valida reglas de perfil editado.
    private String validateProfileForm(
            String nombre,
            String apellido,
            String nick,
            long cumpleanosEnMilisegundos
    ) {
        if (TextUtils.isEmpty(nick)) {
            return getString(R.string.error_perfil_nick_obligatorio);
        }

        if (containsNumbers(nombre) || containsNumbers(apellido)) {
            return getString(R.string.error_nombre_apellido_numeros);
        }

        if (cumpleanosEnMilisegundos <= 0L) {
            return getString(R.string.error_perfil_fecha_invalida);
        }

        int edad = getAgeFromBirthday(cumpleanosEnMilisegundos);
        if (edad < EDAD_MINIMA_REGISTRO) {
            return getString(R.string.error_edad_minima);
        }

        return "";
    }

    // Abre calendario para editar fecha.
    private void openBirthdayPicker() {
        if (!estaEditando || estaGuardando) {
            return;
        }

        Calendar calendario = Calendar.getInstance();
        if (milisegundosCumpleanos > 0L) {
            calendario.setTimeInMillis(milisegundosCumpleanos);
        }

        DatePickerDialog selector = new DatePickerDialog(
                this,
                (vista, anio, mes, dia) -> {
                    Calendar calendarioCumpleanos = Calendar.getInstance();
                    calendarioCumpleanos.set(anio, mes, dia, 0, 0, 0);
                    calendarioCumpleanos.set(Calendar.MILLISECOND, 0);
                    milisegundosCumpleanos = calendarioCumpleanos.getTimeInMillis();
                    campoFechaNacimiento.setText(formatDate(milisegundosCumpleanos));
                },
                calendario.get(Calendar.YEAR),
                calendario.get(Calendar.MONTH),
                calendario.get(Calendar.DAY_OF_MONTH)
        );
        selector.show();
    }

    // Abre selector de imagen para foto.
    private void openPhotoPicker() {
        if (!estaEditando || estaGuardando) {
            return;
        }
        selectorFotoPerfil.launch("image/*");
    }

    // Procesa foto elegida para el perfil.
    private void handlePhotoSelected(Uri uriSeleccionada) {
        if (uriSeleccionada == null || !estaEditando) {
            return;
        }

        String tipoContenido = getContentResolver().getType(uriSeleccionada);
        if (!isAllowedImageType(tipoContenido)) {
            showError(getString(R.string.error_tipo_foto_no_valido));
            return;
        }

        Intent recorte = ImageCropperConfig.createIntent(
                this,
                uriSeleccionada,
                "foto-perfil.jpg",
                getString(R.string.recorte_titulo_foto_perfil),
                1,
                1
        );
        recortadorFotoPerfil.launch(recorte);
    }

    // Recibe la foto recortada y la prepara para guardar.
    private void handleCroppedPhotoSelected(Uri uriRecortada) {
        try {
            String tipoContenido = getContentResolver().getType(uriRecortada);
            if (tipoContenido == null) {
                tipoContenido = "image/jpeg";
            }

            byte[] bytesFoto = readBytesFromUri(uriRecortada);
            if (bytesFoto.length > TAMANO_MAXIMO_FOTO_BYTES) {
                showError(getString(R.string.error_tamano_foto_no_valido));
                return;
            }
            bytesFotoSeleccionada = bytesFoto;
            tipoFotoSeleccionada = tipoContenido;
            nombreFotoSeleccionada = "foto-perfil.jpg";
            imagenPerfil.setImageURI(uriRecortada);
            showError("");
        } catch (IOException error) {
            showError(getString(R.string.error_lectura_foto));
        }
    }

    // Arma objeto foto para firestore.
    private Map<String, Object> buildPhotoPayloadOrNull() {
        if (bytesFotoSeleccionada == null || TextUtils.isEmpty(tipoFotoSeleccionada)) {
            return null;
        }
        Map<String, Object> fotoPerfil = new HashMap<>();
        String base64 = Base64.encodeToString(bytesFotoSeleccionada, Base64.NO_WRAP);
        fotoPerfil.put("dataUrl", "data:" + tipoFotoSeleccionada + ";base64," + base64);
        fotoPerfil.put("fileName",
                TextUtils.isEmpty(nombreFotoSeleccionada) ? "foto-perfil" : nombreFotoSeleccionada);
        fotoPerfil.put("contentType", tipoFotoSeleccionada);
        fotoPerfil.put("sizeBytes", bytesFotoSeleccionada.length);
        return fotoPerfil;
    }

    // Renderiza foto actual del perfil.
    private void renderProfilePhoto() {
        if (bytesFotoSeleccionada != null && !TextUtils.isEmpty(tipoFotoSeleccionada)) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    bytesFotoSeleccionada,
                    0,
                    bytesFotoSeleccionada.length
            );
            if (bitmap != null) {
                imagenPerfil.setImageBitmap(bitmap);
                return;
            }
        }

        Bitmap bitmapPerfil = decodeDataUrl(dataUrlFotoOriginal);
        if (bitmapPerfil != null) {
            imagenPerfil.setImageBitmap(bitmapPerfil);
            return;
        }
        imagenPerfil.setImageResource(R.drawable.default_profile_picture);
    }

    // Muestra error principal de perfil.
    private void showError(String mensaje) {
        textoError.setText(mensaje);
    }

    // Lee string de documento evitando nulos.
    private String readString(DocumentSnapshot documento, String campo) {
        String valor = documento.getString(campo);
        return valor == null ? "" : valor;
    }


    private int readInt(DocumentSnapshot documento, String campo) {
        Long valor = documento.getLong(campo);
        return valor == null ? 0 : valor.intValue();
    }

    // Lee fecha de nacimiento desde timestamp.
    private long readBirthdayMillis(DocumentSnapshot documento) {
        Timestamp timestamp = documento.getTimestamp("FechaNacimiento");
        if (timestamp == null) {
            return -1L;
        }
        return timestamp.toDate().getTime();
    }

    // Formatea fecha para mostrar en input.
    private String formatDate(long milisegundos) {
        if (milisegundos <= 0L) {
            return "";
        }
        Calendar calendario = Calendar.getInstance();
        calendario.setTimeInMillis(milisegundos);
        return String.format(
                Locale.US,
                "%04d-%02d-%02d",
                calendario.get(Calendar.YEAR),
                calendario.get(Calendar.MONTH) + 1,
                calendario.get(Calendar.DAY_OF_MONTH)
        );
    }

    // Calcula edad en años desde fecha.
    private int getAgeFromBirthday(long cumpleanosEnMilisegundos) {
        Calendar hoy = Calendar.getInstance();
        Calendar cumpleanos = Calendar.getInstance();
        cumpleanos.setTimeInMillis(cumpleanosEnMilisegundos);
        int edad = hoy.get(Calendar.YEAR) - cumpleanos.get(Calendar.YEAR);
        boolean aunNoCumplio =
                hoy.get(Calendar.MONTH) < cumpleanos.get(Calendar.MONTH)
                        || (hoy.get(Calendar.MONTH) == cumpleanos.get(Calendar.MONTH)
                        && hoy.get(Calendar.DAY_OF_MONTH) < cumpleanos.get(Calendar.DAY_OF_MONTH));
        if (aunNoCumplio) {
            edad -= 1;
        }
        return edad;
    }

    // Verifica si el texto contiene numeros.
    private boolean containsNumbers(String valor) {
        if (TextUtils.isEmpty(valor)) {
            return false;
        }
        return valor.matches(".*\\d.*");
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

    // Extrae dataUrl desde objeto foto.
    private String extractPhotoDataUrl(Object fotoPerfil) {
        if (!(fotoPerfil instanceof Map)) {
            return "";
        }
        Map<?, ?> mapaFoto = (Map<?, ?>) fotoPerfil;
        Object dataUrl = mapaFoto.get("dataUrl");
        if (dataUrl == null) {
            return "";
        }
        return String.valueOf(dataUrl);
    }

    // dataUrl a bitmap.
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

    // Refresca estado de amistad al volver en perfiles externos.
    @Override
    protected void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(uidPerfilExterno)
                && !TextUtils.isEmpty(uidUsuarioActual)
                && !uidPerfilExterno.equals(uidUsuarioActual)
                && botonAmistad.getVisibility() == View.VISIBLE) {
            refreshFriendshipState();
        }
    }


    @Override
    protected void onDestroy() {
        if (escuchadorPerfil != null) {
            escuchadorPerfil.remove();
        }
        super.onDestroy();
    }

    // Muestra el dialogo para eliminar la cuenta con doble confirmacion.
    private void mostrarDialogoEliminarCuenta() {
        String nickUsuario = campoNick.getText().toString().trim();
        DeleteAccountDialog dialogo = new DeleteAccountDialog(
            ProfileActivity.this,
            nickUsuario,
            new DeleteAccountDialog.OnDeleteCompleted() {
                @Override
                public void onSuccess() {
                    openLoginAndClearStack();
                }

                @Override
                public void onError(String mensaje) {
                    showError("Error al eliminar cuenta: " + mensaje);
                }

                @Override
                public void onCancelled() {
                    showError("Eliminación de cuenta cancelada.");
                }
            }
        );
        dialogo.mostrar();
    }
}
