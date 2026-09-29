package com.example.comiku.screens;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.example.comiku.R;
import com.example.comiku.core.image.ImageCropperConfig;
import com.example.comiku.core.ui.ProfileFeaturedComicCarouselComponent;
import com.example.comiku.data.repository.FriendshipRepository;
import com.example.comiku.data.repository.UserShelfRepository;
import com.example.comiku.data.model.UserShelfComicGroupData;
import com.example.comiku.screens.ComicDetailActivity;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfileActivity extends BaseDrawerActivity {
    private static final int EDAD_MINIMA_REGISTRO = 18;
    private static final int TAMANO_MAXIMO_FOTO_BYTES = 500 * 1024;

    private EditText campoNombre;
    private EditText campoApellido;
    private EditText campoNick;
    private EditText campoFechaNacimiento;
    private View contenedorMetadataPerfil;
    private View contenedorEdicionPerfil;
    private TextView textoNickCabecera;
    private TextView textoNombreCompletoPerfil;
    private TextView textoFechaNacimientoPerfil;
    private TextView textoEmail;
    private TextView textoTotalAmigos;
    private TextView textoError;
    private ProgressBar barraCarga;
    private RoundedImageView imagenPerfil;
    private ImageButton botonMenuPerfil;
    private Button botonTotalComics;
    private Button botonTotalTomos;
    private Button botonCambiarFoto;
    private Button botonCancelar;
    private Button botonGuardarPerfil;
    private Button botonAmistad;
    private TextView textoTituloDestacadosPerfil;
    private TextView textoAyudaDestacadosPerfil;
    private TextView textoContadorDestacadosPerfil;
    private TextView textoEstadoDestacadosPerfil;
    private EditText campoBusquedaDestacadosPerfil;
    private LinearLayout contenedorBusquedaDestacadosPerfil;
    private LinearLayout contenedorCarruselDestacadosPerfil;

    private ListenerRegistration escuchadorPerfil;
    private boolean estaEditando = false;
    private boolean estaGuardando = false;
    private boolean perfilListo = false;
    private boolean bibliotecaListo = false;
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
    private final List<String> featuredComicIdsOriginal = new ArrayList<>();
    private final List<String> editFeaturedComicIds = new ArrayList<>();
    private final List<UserShelfComicGroupData> bibliotecaUsuario = new ArrayList<>();
    private String busquedaDestacados = "";
    private ProfileFeaturedComicCarouselComponent componenteDestacadosPerfil;
    private static final int MAX_COMICS_DESTACADOS = 10;
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
        componenteDestacadosPerfil = new ProfileFeaturedComicCarouselComponent(this);
        bindViews();
        FirebaseUser usuarioActual = FirebaseAuth.getInstance().getCurrentUser();
        uidUsuarioActual = usuarioActual != null ? usuarioActual.getUid() : "";
        uidPerfilExterno = getIntent().getStringExtra(EXTRA_USER_ID);
        boolean esPerfilvPropio = TextUtils.isEmpty(uidPerfilExterno)
                || (!TextUtils.isEmpty(uidUsuarioActual) && uidPerfilExterno.equals(uidUsuarioActual));

        setupLibraryButtons();
        botonMenuPerfil.setOnClickListener(v -> showProfileOverflowMenu());
        botonMenuPerfil.setVisibility(View.VISIBLE);

        if (esPerfilvPropio) {
            uidPerfilExterno = uidUsuarioActual;
            setupListeners();
            setEditingMode(false);
            botonAmistad.setVisibility(View.GONE);
            listenUserProfile();
            loadFeaturedLibraryItems(uidPerfilExterno);
        } else {
            // Modo solo lectura para ver el perfil de otro usuario.
            setEditingMode(false);
            botonCambiarFoto.setVisibility(View.GONE);
            botonCancelar.setVisibility(View.GONE);
            botonAmistad.setVisibility(View.VISIBLE);
            campoNombre.setEnabled(false);
            campoApellido.setEnabled(false);
            campoNick.setEnabled(false);
            campoFechaNacimiento.setEnabled(false);
            setupFriendshipButton();
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
                            .addOnSuccessListener(documento -> {
                                applyProfileSnapshot(documento);
                                loadFeaturedLibraryItems(uidExterno);
                            })
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
        contenedorMetadataPerfil = findViewById(R.id.contenedorMetadataPerfil);
        contenedorEdicionPerfil = findViewById(R.id.contenedorEdicionPerfil);
        textoNickCabecera = findViewById(R.id.textoNickCabeceraPerfil);
        textoNombreCompletoPerfil = findViewById(R.id.textoNombreCompletoPerfil);
        textoFechaNacimientoPerfil = findViewById(R.id.textoFechaNacimientoPerfil);
        textoEmail = findViewById(R.id.textoEmailPerfil);
        botonTotalComics = findViewById(R.id.botonTotalComicsPerfil);
        botonTotalTomos = findViewById(R.id.botonTotalTomosPerfil);
        textoTotalAmigos = findViewById(R.id.textoTotalAmigosPerfil);
        textoError = findViewById(R.id.textoErrorPerfil);
        barraCarga = findViewById(R.id.barraCargaPerfil);
        imagenPerfil = findViewById(R.id.imagenPerfil);
        imagenPerfil.setCircular(true);
        botonMenuPerfil = findViewById(R.id.botonMenuPerfil);
        botonCambiarFoto = findViewById(R.id.botonCambiarFotoPerfil);
        botonCancelar = findViewById(R.id.botonCancelarPerfil);
        botonGuardarPerfil = findViewById(R.id.botonGuardarPerfil);
        botonAmistad = findViewById(R.id.botonAmistadPerfil);
        textoTituloDestacadosPerfil = findViewById(R.id.textoTituloDestacadosPerfil);
        textoAyudaDestacadosPerfil = findViewById(R.id.textoAyudaDestacadosPerfil);
        textoContadorDestacadosPerfil = findViewById(R.id.textoContadorDestacadosPerfil);
        textoEstadoDestacadosPerfil = findViewById(R.id.textoEstadoDestacadosPerfil);
        campoBusquedaDestacadosPerfil = findViewById(R.id.campoBusquedaDestacadosPerfil);
        contenedorBusquedaDestacadosPerfil = findViewById(R.id.contenedorBusquedaDestacadosPerfil);
        contenedorCarruselDestacadosPerfil = findViewById(R.id.contenedorCarruselDestacadosPerfil);
        campoBusquedaDestacadosPerfil.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                busquedaDestacados = s == null ? "" : s.toString();
                refreshFeaturedComicsSection();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // Conecta acciones de edicion del perfil.
    private void setupListeners() {
        botonCambiarFoto.setOnClickListener(v -> openPhotoPicker());
        campoFechaNacimiento.setOnClickListener(v -> openBirthdayPicker());
        botonCancelar.setOnClickListener(v -> handleCancel());
        botonGuardarPerfil.setOnClickListener(v -> handleEditOrSave());
    }

    // Muestra el menu desplegable con acciones del perfil.
    private void showProfileOverflowMenu() {
        PopupMenu menuOpciones = new PopupMenu(this, botonMenuPerfil);
        menuOpciones.getMenuInflater().inflate(R.menu.menu_profile_overflow, menuOpciones.getMenu());
        boolean esPerfilPropio = isShowingCurrentUserProfile();
        if (menuOpciones.getMenu().findItem(R.id.menu_perfil_editar_datos) != null) {
            menuOpciones.getMenu().findItem(R.id.menu_perfil_editar_datos).setVisible(esPerfilPropio && !estaEditando);
        }
        if (menuOpciones.getMenu().findItem(R.id.menu_perfil_usuarios_bloqueados) != null) {
            menuOpciones.getMenu().findItem(R.id.menu_perfil_usuarios_bloqueados).setVisible(esPerfilPropio);
        }
        if (menuOpciones.getMenu().findItem(R.id.menu_perfil_eliminar_cuenta) != null) {
            menuOpciones.getMenu().findItem(R.id.menu_perfil_eliminar_cuenta).setVisible(esPerfilPropio);
        }
        if (menuOpciones.getMenu().findItem(R.id.menu_perfil_reportar_usuario) != null) {
            menuOpciones.getMenu().findItem(R.id.menu_perfil_reportar_usuario).setVisible(!esPerfilPropio);
        }
        if (menuOpciones.getMenu().findItem(R.id.menu_perfil_bloquear_usuario) != null) {
            menuOpciones.getMenu().findItem(R.id.menu_perfil_bloquear_usuario).setVisible(!esPerfilPropio);
        }
        menuOpciones.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_perfil_editar_datos) {
                setEditingMode(true);
                return true;
            }
            if (id == R.id.menu_perfil_usuarios_bloqueados) {
                openBlockedUsersScreen();
                return true;
            }
            if (id == R.id.menu_perfil_bloquear_usuario) {
                confirmBlockUser();
                return true;
            }
            if (id == R.id.menu_perfil_reportar_usuario) {
                showReportDialog();
                return true;
            }
            if (id == R.id.menu_perfil_eliminar_cuenta) {
                mostrarDialogoEliminarCuenta();
                return true;
            }
            return false;
        });
        menuOpciones.show();
    }

    // Conecta el boton de amistad en perfil externo.
    private void setupFriendshipButton() {
        botonAmistad.setOnClickListener(v -> handleFriendshipAction());
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

    // Aplica estilo visual de botones de dialogo.
    private void styleDialogButton(Button boton, int fondoResId, ColorStateList colorTexto) {
        if (boton == null || colorTexto == null) {
            return;
        }
        boton.setAllCaps(false);
        boton.setBackgroundResource(fondoResId);
        boton.setBackgroundTintList(null);
        boton.setTextColor(colorTexto);
        boton.setTypeface(null, Typeface.BOLD);
        boton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        boton.setPadding(dpToPx(16), dpToPx(11), dpToPx(16), dpToPx(11));
        boton.setMinHeight(0);
    }

    // Agrega separacion horizontal entre botones del dialogo.
    private void spaceDialogButtons(Button botonIzquierdo, Button botonDerecho) {
        applyDialogButtonMargin(botonIzquierdo, 0, 8);
        applyDialogButtonMargin(botonDerecho, 8, 0);
    }

    // Ajusta margenes laterales de un boton cuando el contenedor lo permite.
    private void applyDialogButtonMargin(Button boton, int margenInicioDp, int margenFinDp) {
        if (boton == null) {
            return;
        }
        ViewGroup.LayoutParams params = boton.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams paramsMargen = (ViewGroup.MarginLayoutParams) params;
        paramsMargen.setMarginStart(dpToPx(margenInicioDp));
        paramsMargen.setMarginEnd(dpToPx(margenFinDp));
        boton.setLayoutParams(paramsMargen);
    }

    // Convierte dp a pixeles para mantener medidas parejas.
    private int dpToPx(int valorDp) {
        float densidad = getResources().getDisplayMetrics().density;
        return Math.round(valorDp * densidad);
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

        AlertDialog dialogoBloqueo = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.perfil_bloquear_confirmacion_titulo))
                .setMessage(getString(R.string.perfil_bloquear_confirmacion_mensaje))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> handleBlockUser())
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialogoBloqueo.setOnShowListener(dialogInterface -> {
            Button botonCancelar = dialogoBloqueo.getButton(AlertDialog.BUTTON_NEGATIVE);
            Button botonAceptar = dialogoBloqueo.getButton(AlertDialog.BUTTON_POSITIVE);

            styleDialogButton(
                    botonCancelar,
                    R.drawable.bg_button_danger,
                    getColorStateList(R.color.button_danger_text)
            );
            styleDialogButton(
                    botonAceptar,
                    R.drawable.bg_button_primary_action,
                    getColorStateList(R.color.button_primary_action_text)
            );
            spaceDialogButtons(botonCancelar, botonAceptar);
        });

        dialogoBloqueo.show();
        if (dialogoBloqueo.getWindow() != null) {
            dialogoBloqueo.getWindow().setBackgroundDrawableResource(R.drawable.bg_report_dialog_rounded);
        }
    }

    // Bloquea al usuario externo y vuelve a la pantalla anterior.
    private void handleBlockUser() {
        if (TextUtils.isEmpty(uidUsuarioActual) || TextUtils.isEmpty(uidPerfilExterno)) {
            return;
        }

        FriendshipRepository.blockUser(uidUsuarioActual, uidPerfilExterno)
                .addOnSuccessListener(unused -> {
                    showError(getString(R.string.perfil_bloquear_exitoso));
                    finish();
                })
                .addOnFailureListener(error -> {
                    showError(resolveFriendshipError(error, R.string.perfil_acceso_bloqueado));
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
        featuredComicIdsOriginal.clear();
        featuredComicIdsOriginal.addAll(readStringList(documento, "featuredComicIds"));
        if (!estaEditando) {
            editFeaturedComicIds.clear();
            editFeaturedComicIds.addAll(featuredComicIdsOriginal);
        }
        perfilListo = true;
        updateProfileHeader();
        updateProfileMetadata();

        if (estaEditando) {
            refreshFeaturedComicsSection();
            return;
        }

        campoNombre.setText(nombreOriginal);
        campoApellido.setText(apellidoOriginal);
        campoNick.setText(nickOriginal);
        campoFechaNacimiento.setText(formatDate(milisegundosCumpleanos));
        textoEmail.setText(emailOriginal);
        textoTotalAmigos.setText(String.valueOf(totalAmigosOriginal));
        renderProfilePhoto();
        refreshFeaturedComicsSection();
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
        editFeaturedComicIds.clear();
        editFeaturedComicIds.addAll(featuredComicIdsOriginal);
        busquedaDestacados = "";
        if (campoBusquedaDestacadosPerfil != null) {
            campoBusquedaDestacadosPerfil.setText("");
        }
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
        List<String> destacadosParaGuardar = resolveFeaturedComicIdsToSave();
        Map<String, Object> actualizacion = new HashMap<>();
        actualizacion.put("Nombre", nombre);
        actualizacion.put("Apellido", apellido);
        actualizacion.put("Nick", nick);
        actualizacion.put("FechaNacimiento", new Timestamp(new Date(milisegundosCumpleanos)));
        actualizacion.put("featuredComicIds", new ArrayList<>(destacadosParaGuardar));

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
                    featuredComicIdsOriginal.clear();
                    featuredComicIdsOriginal.addAll(destacadosParaGuardar);
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
        contenedorMetadataPerfil.setVisibility(editando ? View.GONE : View.VISIBLE);
        contenedorEdicionPerfil.setVisibility(editando ? View.VISIBLE : View.GONE);
        campoNombre.setEnabled(editando);
        campoApellido.setEnabled(editando);
        campoNick.setEnabled(editando);
        campoFechaNacimiento.setEnabled(editando);
        botonCambiarFoto.setVisibility(editando ? View.VISIBLE : View.GONE);
        botonCambiarFoto.setEnabled(editando && !estaGuardando);
        botonCancelar.setVisibility(editando ? View.VISIBLE : View.GONE);
        botonGuardarPerfil.setVisibility(editando ? View.VISIBLE : View.GONE);
        botonGuardarPerfil.setEnabled(!estaGuardando);
        if (campoBusquedaDestacadosPerfil != null) {
            if (editando) {
                editFeaturedComicIds.clear();
                editFeaturedComicIds.addAll(featuredComicIdsOriginal);
                busquedaDestacados = "";
                campoBusquedaDestacadosPerfil.setText("");
            } else {
                busquedaDestacados = "";
                campoBusquedaDestacadosPerfil.setText("");
            }
        }
        refreshFeaturedComicsSection();
    }

    // Actualiza estado visual durante guardado.
    private void setSavingState(boolean guardando) {
        estaGuardando = guardando;
        barraCarga.setVisibility(guardando ? View.VISIBLE : View.GONE);
        botonCancelar.setEnabled(!guardando);
        botonGuardarPerfil.setEnabled(!guardando);
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

    // Actualiza el header con el nick y las cantidades.
    private void updateProfileHeader() {
        textoNickCabecera.setText(TextUtils.isEmpty(nickOriginal)
                ? getString(R.string.menu_perfil_default)
                : nickOriginal);
        botonTotalComics.setText(buildCounterText(R.string.perfil_boton_comics, totalComicsOriginal));
        botonTotalTomos.setText(buildCounterText(R.string.perfil_boton_tomos, totalTomosOriginal));
    }

    // Pinta de blanco solo el numero del contador en el boton.
    private CharSequence buildCounterText(int idTexto, int cantidad) {
        String textoCompleto = getString(idTexto, cantidad);
        SpannableString textoConEstilo = new SpannableString(textoCompleto);
        Matcher matcherNumero = Pattern.compile("(\\d+)(?!.*\\d)").matcher(textoCompleto);
        if (matcherNumero.find()) {
            textoConEstilo.setSpan(
                    new ForegroundColorSpan(getColor(android.R.color.white)),
                    matcherNumero.start(),
                    matcherNumero.end(),
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return textoConEstilo;
    }

    // Actualiza los datos del bloque metadata en modo lectura.
    private void updateProfileMetadata() {
        String nombreCompleto = (nombreOriginal + " " + apellidoOriginal).trim();
        if (TextUtils.isEmpty(nombreCompleto)) {
            nombreCompleto = getString(R.string.perfil_nombre_no_definido);
        }
        textoNombreCompletoPerfil.setText(nombreCompleto);
        textoEmail.setText(emailOriginal);
        textoFechaNacimientoPerfil.setText(formatDate(milisegundosCumpleanos));
        textoTotalAmigos.setText(String.valueOf(totalAmigosOriginal));
    }

    // Conecta los botones con la pantalla de biblioteca.
    private void setupLibraryButtons() {
        botonTotalComics.setOnClickListener(v -> openLibraryForProfile());
        botonTotalTomos.setOnClickListener(v -> openLibraryForProfile());
    }

    // Abre la biblioteca del perfil actual.
    private void openLibraryForProfile() {
        String uidObjetivo = resolveProfileUserId();
        if (TextUtils.isEmpty(uidObjetivo)) {
            showError(getString(R.string.error_perfil_carga));
            return;
        }

        Intent pantallaBiblioteca = new Intent(this, LibraryActivity.class);
        if (!uidObjetivo.equals(uidUsuarioActual)) {
            pantallaBiblioteca.putExtra(LibraryActivity.EXTRA_LIBRARY_USER_ID, uidObjetivo);
            pantallaBiblioteca.putExtra(
                    LibraryActivity.EXTRA_LIBRARY_USER_NICK,
                    TextUtils.isEmpty(nickOriginal) ? campoNick.getText().toString().trim() : nickOriginal
            );
        }
        startActivity(pantallaBiblioteca);
    }

    // Devuelve el uid del perfil visible.
    private String resolveProfileUserId() {
        if (!TextUtils.isEmpty(uidPerfilExterno)) {
            return uidPerfilExterno;
        }
        return uidUsuarioActual;
    }

    // Indica si el perfil visible es el del usuario actual.
    public boolean isShowingCurrentUserProfile() {
        String uidPerfil = resolveProfileUserId();
        return !TextUtils.isEmpty(uidPerfil) && uidPerfil.equals(uidUsuarioActual);
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


    // Lee una lista de textos guardada en el documento.
    private List<String> readStringList(DocumentSnapshot documento, String campo) {
        Object valor = documento.get(campo);
        if (!(valor instanceof List)) {
            return new ArrayList<>();
        }
        List<?> lista = (List<?>) valor;
        List<String> resultado = new ArrayList<>();
        for (Object item : lista) {
            if (item != null) {
                resultado.add(String.valueOf(item));
            }
        }
        return resultado;
    }

    private int readInt(DocumentSnapshot documento, String campo) {
        Long valor = documento.getLong(campo);
        return valor == null ? 0 : valor.intValue();
    }

    // Carga la biblioteca del usuario para mostrar y editar destacados.
    private void loadFeaturedLibraryItems(String uidObjetivo) {
        if (TextUtils.isEmpty(uidObjetivo)) {
            bibliotecaUsuario.clear();
            bibliotecaListo = true;
            refreshFeaturedComicsSection();
            return;
        }

        UserShelfRepository.getUserLibraryItems(uidObjetivo)
                .addOnSuccessListener(items -> {
                    bibliotecaUsuario.clear();
                    if (items != null) {
                        bibliotecaUsuario.addAll(items);
                    }
                    bibliotecaListo = true;
                    refreshFeaturedComicsSection();
                })
                .addOnFailureListener(error -> {
                    bibliotecaUsuario.clear();
                    bibliotecaListo = true;
                    refreshFeaturedComicsSection();
                    showError(getString(R.string.error_perfil_carga));
                });
    }

    // Refresca la seccion de destacados segun el modo actual.
    private void refreshFeaturedComicsSection() {
        if (contenedorCarruselDestacadosPerfil == null
                || textoAyudaDestacadosPerfil == null
                || textoEstadoDestacadosPerfil == null
                || textoContadorDestacadosPerfil == null
                || contenedorBusquedaDestacadosPerfil == null
                || componenteDestacadosPerfil == null) {
            return;
        }

        boolean modoEdicion = estaEditando && isShowingCurrentUserProfile();
        boolean esPerfilPropio = isShowingCurrentUserProfile();
        contenedorBusquedaDestacadosPerfil.setVisibility(modoEdicion ? View.VISIBLE : View.GONE);

        if (textoTituloDestacadosPerfil != null) {
            textoTituloDestacadosPerfil.setText(modoEdicion
                    ? getString(R.string.perfil_destacados_titulo_edicion)
                    : getString(R.string.perfil_destacados_titulo));
        }

        if (modoEdicion) {
            textoAyudaDestacadosPerfil.setVisibility(View.VISIBLE);
            textoAyudaDestacadosPerfil.setText(getString(R.string.perfil_destacados_ayuda_edicion));
            textoContadorDestacadosPerfil.setVisibility(View.VISIBLE);
            textoContadorDestacadosPerfil.setText(getString(
                    R.string.perfil_destacados_contador_seleccion,
                    editFeaturedComicIds.size(),
                    MAX_COMICS_DESTACADOS
            ));
        } else {
            if (esPerfilPropio) {
                textoAyudaDestacadosPerfil.setVisibility(View.VISIBLE);
                textoAyudaDestacadosPerfil.setText(getString(R.string.perfil_destacados_ayuda_lectura));
            } else {
                textoAyudaDestacadosPerfil.setVisibility(View.GONE);
            }
            textoContadorDestacadosPerfil.setVisibility(View.GONE);
            if (campoBusquedaDestacadosPerfil != null && !TextUtils.isEmpty(campoBusquedaDestacadosPerfil.getText())) {
                campoBusquedaDestacadosPerfil.setText("");
            }
        }

        if (!perfilListo || !bibliotecaListo) {
            textoEstadoDestacadosPerfil.setVisibility(View.VISIBLE);
            textoEstadoDestacadosPerfil.setText(getString(R.string.perfil_destacados_cargando));
            contenedorCarruselDestacadosPerfil.removeAllViews();
            return;
        }

        List<UserShelfComicGroupData> comicsVisibles = modoEdicion
                ? getFilteredLibraryComics()
                : getFeaturedLibraryComics();

        if (comicsVisibles.isEmpty()) {
            textoEstadoDestacadosPerfil.setVisibility(View.VISIBLE);
            textoEstadoDestacadosPerfil.setText(getString(modoEdicion
                    ? R.string.perfil_destacados_sin_resultados
                    : R.string.perfil_destacados_sin_destacados));
            contenedorCarruselDestacadosPerfil.removeAllViews();
            return;
        }

        textoEstadoDestacadosPerfil.setVisibility(View.GONE);
        componenteDestacadosPerfil.renderComics(
                contenedorCarruselDestacadosPerfil,
                comicsVisibles,
                modoEdicion,
                new HashSet<>(editFeaturedComicIds),
                item -> handleFeaturedComicClick(item, modoEdicion)
        );
    }

    // Devuelve los comics destacados que existen en la biblioteca.
    private List<UserShelfComicGroupData> getFeaturedLibraryComics() {
        if (featuredComicIdsOriginal.isEmpty() || bibliotecaUsuario.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> destacados = new HashSet<>(featuredComicIdsOriginal);
        List<UserShelfComicGroupData> resultado = new ArrayList<>();
        for (UserShelfComicGroupData item : bibliotecaUsuario) {
            if (item != null && destacados.contains(item.comicId)) {
                resultado.add(item);
                if (resultado.size() >= MAX_COMICS_DESTACADOS) {
                    break;
                }
            }
        }
        return resultado;
    }

    // Filtra la biblioteca segun el texto de busqueda.
    private List<UserShelfComicGroupData> getFilteredLibraryComics() {
        if (bibliotecaUsuario.isEmpty()) {
            return new ArrayList<>();
        }

        String busqueda = busquedaDestacados == null ? "" : busquedaDestacados.trim().toLowerCase(Locale.getDefault());
        List<UserShelfComicGroupData> resultado = new ArrayList<>();
        for (UserShelfComicGroupData item : bibliotecaUsuario) {
            if (item == null || item.comic == null) {
                continue;
            }
            if (TextUtils.isEmpty(busqueda) || getComicSearchText(item).contains(busqueda)) {
                resultado.add(item);
            }
        }
        return resultado;
    }

    // Devuelve el texto usado para buscar un comic.
    private String getComicSearchText(UserShelfComicGroupData item) {
        StringBuilder texto = new StringBuilder();
        if (item.comic != null) {
            if (!TextUtils.isEmpty(item.comic.nombre)) {
                texto.append(item.comic.nombre).append(' ');
            }
            texto.append(item.comic.getAutoresFormateados()).append(' ');
            texto.append(item.comic.getGenerosFormateados()).append(' ');
        }
        if (item.tomos != null) {
            for (int i = 0; i < item.tomos.size(); i++) {
                if (item.tomos.get(i) != null && item.tomos.get(i).tomo != null) {
                    texto.append(item.tomos.get(i).tomo.getNumeroFormateado()).append(' ');
                }
            }
        }
        return texto.toString().toLowerCase(Locale.getDefault());
    }

    // Maneja un toque sobre una card de destacado.
    private void handleFeaturedComicClick(UserShelfComicGroupData item, boolean modoEdicion) {
        if (item == null || TextUtils.isEmpty(item.comicId)) {
            return;
        }

        if (modoEdicion) {
            toggleFeaturedComic(item.comicId);
            return;
        }

        openComicDetail(item.comicId);
    }

    // Alterna si un comic queda destacado o no.
    private void toggleFeaturedComic(String comicId) {
        if (TextUtils.isEmpty(comicId)) {
            return;
        }

        if (editFeaturedComicIds.contains(comicId)) {
            editFeaturedComicIds.remove(comicId);
            refreshFeaturedComicsSection();
            return;
        }

        if (editFeaturedComicIds.size() >= MAX_COMICS_DESTACADOS) {
            textoEstadoDestacadosPerfil.setVisibility(View.VISIBLE);
            textoEstadoDestacadosPerfil.setText(getString(R.string.perfil_destacados_limite));
            return;
        }

        editFeaturedComicIds.add(comicId);
        refreshFeaturedComicsSection();
    }

    // Devuelve la lista final de destacados para guardar.
    private List<String> resolveFeaturedComicIdsToSave() {
        List<String> resultado = new ArrayList<>();
        List<String> origen = estaEditando ? editFeaturedComicIds : featuredComicIdsOriginal;
        if (bibliotecaUsuario.isEmpty()) {
            for (String comicId : origen) {
                if (!TextUtils.isEmpty(comicId) && !resultado.contains(comicId)) {
                    resultado.add(comicId);
                }
                if (resultado.size() >= MAX_COMICS_DESTACADOS) {
                    break;
                }
            }
            return resultado;
        }

        Set<String> idsDeLaBiblioteca = new HashSet<>();
        for (UserShelfComicGroupData item : bibliotecaUsuario) {
            if (item != null && !TextUtils.isEmpty(item.comicId)) {
                idsDeLaBiblioteca.add(item.comicId);
            }
        }

        for (String comicId : origen) {
            if (!TextUtils.isEmpty(comicId) && idsDeLaBiblioteca.contains(comicId) && !resultado.contains(comicId)) {
                resultado.add(comicId);
            }
            if (resultado.size() >= MAX_COMICS_DESTACADOS) {
                break;
            }
        }
        return resultado;
    }

    // Abre la pantalla de detalle de comic.
    private void openComicDetail(String comicId) {
        if (TextUtils.isEmpty(comicId)) {
            return;
        }
        Intent pantallaDetalleComic = new Intent(this, ComicDetailActivity.class);
        pantallaDetalleComic.putExtra(ComicDetailActivity.EXTRA_COMIC_ID, comicId);
        startActivity(pantallaDetalleComic);
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
                Locale.getDefault(),
                "%02d-%02d-%04d",
                calendario.get(Calendar.DAY_OF_MONTH),
                calendario.get(Calendar.MONTH) + 1,
                calendario.get(Calendar.YEAR)
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
