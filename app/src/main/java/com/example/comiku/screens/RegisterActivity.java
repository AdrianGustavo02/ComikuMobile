package com.example.comiku.screens;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.comiku.R;
import com.example.comiku.core.image.ImageCropperConfig;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {
    private static final int EDAD_MINIMA_REGISTRO = 18;
    private static final int TAMANO_MAXIMO_FOTO_BYTES = 500 * 1024;
    private static final String COLECCION_USUARIOS = "usuario";
    private static final String COLECCION_EMAILS_BLOQUEADOS = "emailsBloqueados";

    private EditText campoNombre;
    private EditText campoApellido;
    private EditText campoNick;
    private EditText campoCorreo;
    private EditText campoCumpleanos;
    private EditText campoContrasena;
    private EditText campoConfirmacionContrasena;
    private TextView textoError;
    private ProgressBar barraCarga;
    private ImageView imagenFotoPerfil;
    private Button botonRegistro;

    private byte[] bytesFotoSeleccionada;
    private String nombreFotoSeleccionada;
    private String tipoFotoSeleccionada;
    private long milisegundosCumpleanos = -1L;
    private boolean estaRegistrando = false;

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

    // Configura la pantalla y deja listo el formulario de registro.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        bindViews();
        setupListeners();
        updateLoadingState(false);
    }


    private void bindViews() {
        campoNombre = findViewById(R.id.campoNombre);
        campoApellido = findViewById(R.id.campoApellido);
        campoNick = findViewById(R.id.campoNick);
        campoCorreo = findViewById(R.id.campoCorreo);
        campoCumpleanos = findViewById(R.id.campoCumpleanos);
        campoContrasena = findViewById(R.id.campoContrasena);
        campoConfirmacionContrasena = findViewById(R.id.campoConfirmacionContrasena);
        textoError = findViewById(R.id.textoErrorRegistro);
        barraCarga = findViewById(R.id.barraCargaRegistro);
        imagenFotoPerfil = findViewById(R.id.imagenFotoPerfil);
        botonRegistro = findViewById(R.id.botonRegistrar);
    }


    private void setupListeners() {
        campoCumpleanos.setOnClickListener(v -> openBirthdayPicker());
        findViewById(R.id.botonElegirFoto).setOnClickListener(v -> openPhotoPicker());
        botonRegistro.setOnClickListener(v -> handleRegister());
    }

    // Muestra el selector de fecha
    private void openBirthdayPicker() {
        Calendar calendarioActual = Calendar.getInstance();
        int anio = calendarioActual.get(Calendar.YEAR);
        int mes = calendarioActual.get(Calendar.MONTH);
        int dia = calendarioActual.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog selectorFecha = new DatePickerDialog(
                this,
                (vista, anioSeleccionado, mesSeleccionado, diaSeleccionado) -> {
                    Calendar calendarioCumpleanos = Calendar.getInstance();
                    calendarioCumpleanos.set(anioSeleccionado, mesSeleccionado, diaSeleccionado, 0, 0, 0);
                    calendarioCumpleanos.set(Calendar.MILLISECOND, 0);
                    milisegundosCumpleanos = calendarioCumpleanos.getTimeInMillis();
                    String fechaTexto = String.format(
                            Locale.US,
                            "%04d-%02d-%02d",
                            anioSeleccionado,
                            mesSeleccionado + 1,
                            diaSeleccionado
                    );
                    campoCumpleanos.setText(fechaTexto);
                },
                anio,
                mes,
                dia
        );
        selectorFecha.show();
    }

    // Abre la galeria para elegir una foto de perfil.
    private void openPhotoPicker() {
        selectorFotoPerfil.launch("image/*");
    }

    // Procesa la imagen elegida y la muestra como vista previa.
    private void handlePhotoSelected(Uri uriSeleccionada) {
        if (uriSeleccionada == null) {
            return;
        }

        if (!isAllowedImageType(getContentResolver().getType(uriSeleccionada))) {
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

    // Recibe la foto ya recortada y la prepara para guardar.
    private void handleCroppedPhotoSelected(Uri uriRecortada) {
        try {
            String tipoContenido = getContentResolver().getType(uriRecortada);
            if (tipoContenido == null) {
                tipoContenido = "image/jpeg";
            }

            byte[] bytesImagen = readBytesFromUri(uriRecortada);
            if (bytesImagen.length > TAMANO_MAXIMO_FOTO_BYTES) {
                showError(getString(R.string.error_tamano_foto_no_valido));
                return;
            }

            bytesFotoSeleccionada = bytesImagen;
            tipoFotoSeleccionada = tipoContenido;
            nombreFotoSeleccionada = "foto-perfil.jpg";
            imagenFotoPerfil.setImageURI(uriRecortada);
            textoError.setText("");
        } catch (IOException excepcion) {
            showError(getString(R.string.error_lectura_foto));
        }
    }

    // Inicia el registro cuando el formulario es valido.
    private void handleRegister() {
        if (estaRegistrando) {
            return;
        }

        textoError.setText("");

        String nombre = campoNombre.getText().toString().trim();
        String apellido = campoApellido.getText().toString().trim();
        String nick = campoNick.getText().toString().trim();
        String correo = campoCorreo.getText().toString().trim();
        String contrasena = campoContrasena.getText().toString();
        String confirmacionContrasena = campoConfirmacionContrasena.getText().toString();

        String errorFormulario = validateRegistrationForm(
                nombre,
                apellido,
                nick,
                correo,
                contrasena,
                confirmacionContrasena,
                milisegundosCumpleanos
        );
        if (!TextUtils.isEmpty(errorFormulario)) {
            showError(errorFormulario);
            return;
        }

        updateLoadingState(true);
        checkNickAvailabilityAndRegister(
                nombre,
                apellido,
                nick,
                correo,
                contrasena,
                milisegundosCumpleanos
        );
    }

    // Revisa si el nick ya existe y luego continua con el alta.
    private void checkNickAvailabilityAndRegister(
            String nombre,
            String apellido,
            String nick,
            String correo,
            String contrasena,
            long cumpleanosEnMilisegundos
    ) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        firestore.collection(COLECCION_USUARIOS)
                .whereEqualTo("Nick", nick)
                .limit(1)
                .get()
                .addOnSuccessListener(resultado -> registerWithFirebaseIfNickAvailable(
                        resultado,
                        nombre,
                        apellido,
                        nick,
                        correo,
                        contrasena,
                        cumpleanosEnMilisegundos
                ))
                .addOnFailureListener(error -> {
                    updateLoadingState(false);
                    showError(getString(R.string.error_nick_verificacion));
                });
    }

    // Ejecuta el alta en Auth y luego guarda el perfil en Firestore.
    private void registerWithFirebaseIfNickAvailable(
            QuerySnapshot resultadoNick,
            String nombre,
            String apellido,
            String nick,
            String correo,
            String contrasena,
            long cumpleanosEnMilisegundos
    ) {
        if (!resultadoNick.isEmpty()) {
            updateLoadingState(false);
            showError(getString(R.string.error_nick_duplicado));
            return;
        }

        isEmailBlockedForRegistration(correo, bloqueado -> {
            if (bloqueado) {
                updateLoadingState(false);
                showError(getString(R.string.error_correo_bloqueado));
                return;
            }

            createFirebaseUserAccount(
                    nombre,
                    apellido,
                    nick,
                    correo,
                    contrasena,
                    cumpleanosEnMilisegundos
            );
        });
    }

    // Consulta si el correo ya quedo bloqueado para volver a registrarse.
    private void isEmailBlockedForRegistration(String correo, OnEmailBlockedCheckedListener listener) {
        String correoNormalizado = String.valueOf(correo == null ? "" : correo).trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(correoNormalizado)) {
            listener.onChecked(false);
            return;
        }

        String correoDocumento = Uri.encode(correoNormalizado);
        FirebaseFirestore.getInstance()
                .collection(COLECCION_EMAILS_BLOQUEADOS)
                .document(correoDocumento)
                .get()
                .addOnSuccessListener(documento -> listener.onChecked(documento.exists()))
                .addOnFailureListener(error -> {
                    updateLoadingState(false);
                    showError(getString(R.string.error_correo_bloqueado_verificacion));
                });
    }

    // Lanza la creacion del usuario en Auth cuando el correo esta permitido.
    private void createFirebaseUserAccount(
            String nombre,
            String apellido,
            String nick,
            String correo,
            String contrasena,
            long cumpleanosEnMilisegundos
    ) {
        FirebaseAuth.getInstance()
                .createUserWithEmailAndPassword(correo, contrasena)
                .addOnSuccessListener(resultadoAuth -> {
                    FirebaseUser usuarioCreado = resultadoAuth.getUser();
                    if (usuarioCreado == null) {
                        updateLoadingState(false);
                        showError(getString(R.string.error_registro_general));
                        return;
                    }

                    createUserProfileDocument(
                            usuarioCreado,
                            nombre,
                            apellido,
                            nick,
                            correo,
                            cumpleanosEnMilisegundos
                    );
                })
                .addOnFailureListener(error -> {
                    updateLoadingState(false);
                    showError(mapAuthError(error.getMessage()));
                });
    }

    // Recibe el resultado de la consulta del correo bloqueado.
    private interface OnEmailBlockedCheckedListener {
        void onChecked(boolean bloqueado);
    }

    // Crea el documento del usuario
    private void createUserProfileDocument(
            FirebaseUser usuarioCreado,
            String nombre,
            String apellido,
            String nick,
            String correo,
            long cumpleanosEnMilisegundos
    ) {
        Map<String, Object> fotoPerfil;
        try {
            fotoPerfil = buildPhotoPayload();
        } catch (IOException error) {
            rollbackCreatedUser(usuarioCreado);
            updateLoadingState(false);
            showError(getString(R.string.error_foto_default));
            return;
        }

        Map<String, Object> perfilUsuario = new HashMap<>();
        perfilUsuario.put("UserID", usuarioCreado.getUid());
        perfilUsuario.put("Nombre", nombre);
        perfilUsuario.put("Apellido", apellido);
        perfilUsuario.put("Nick", nick);
        perfilUsuario.put("Email", correo);
        perfilUsuario.put("Rol", "usuario");
        perfilUsuario.put("FechaNacimiento", new Timestamp(new Date(cumpleanosEnMilisegundos)));
        perfilUsuario.put("FotoPerfil", fotoPerfil);
        perfilUsuario.put("totalComics", 0);
        perfilUsuario.put("totalTomos", 0);
        perfilUsuario.put("cantidadAmigos", 0);
        perfilUsuario.put("featuredComicIds", new ArrayList<String>());

        FirebaseFirestore.getInstance()
                .collection(COLECCION_USUARIOS)
                .document(usuarioCreado.getUid())
                .set(perfilUsuario)
                .addOnSuccessListener(unused -> {
                    updateLoadingState(false);
                    navigateToHome();
                })
                .addOnFailureListener(error -> {
                    rollbackCreatedUser(usuarioCreado);
                    updateLoadingState(false);
                    showError(getString(R.string.error_guardar_perfil));
                });
    }

    // Devuelve un mensaje para errores de autenticacion.
    private String mapAuthError(String mensajeOriginal) {
        String mensaje = String.valueOf(mensajeOriginal).toLowerCase(Locale.ROOT);
        if (mensaje.contains("email address is already in use")
                || mensaje.contains("auth/email-already-in-use")) {
            return getString(R.string.error_correo_duplicado);
        }
        if (mensaje.contains("invalid email") || mensaje.contains("auth/invalid-email")) {
            return getString(R.string.error_correo_invalido);
        }
        return getString(R.string.error_registro_general);
    }

    // Intenta deshacer el usuario de Auth si falla el perfil.
    private void rollbackCreatedUser(FirebaseUser usuarioCreado) {
        usuarioCreado.delete();
    }

    // Construye el objeto FotoPerfil para Firestore.
    private Map<String, Object> buildPhotoPayload() throws IOException {
        if (bytesFotoSeleccionada != null && !TextUtils.isEmpty(tipoFotoSeleccionada)) {
            return buildPhotoPayloadFromBytes(
                    bytesFotoSeleccionada,
                    TextUtils.isEmpty(nombreFotoSeleccionada) ? "foto-perfil" : nombreFotoSeleccionada,
                    tipoFotoSeleccionada
            );
        }

        byte[] bytesDefault = buildDefaultPhotoBytes();
        return buildPhotoPayloadFromBytes(bytesDefault, "default_profile_picture.png", "image/png");
    }


    private Map<String, Object> buildPhotoPayloadFromBytes(
            byte[] bytesFoto,
            String nombreFoto,
            String tipoFoto
    ) {
        Map<String, Object> fotoPerfil = new HashMap<>();
        String base64 = Base64.encodeToString(bytesFoto, Base64.NO_WRAP);
        fotoPerfil.put("dataUrl", "data:" + tipoFoto + ";base64," + base64);
        fotoPerfil.put("fileName", nombreFoto);
        fotoPerfil.put("contentType", tipoFoto);
        fotoPerfil.put("sizeBytes", bytesFoto.length);
        return fotoPerfil;
    }

    // Lee la foto por defecto desde drawable para usarla en el perfil.
    private byte[] buildDefaultPhotoBytes() throws IOException {
        Drawable drawableDefault = ContextCompat.getDrawable(this, R.drawable.default_profile_picture);
        if (drawableDefault == null) {
            throw new IOException("No se pudo cargar la imagen por defecto.");
        }

        int ancho = Math.max(drawableDefault.getIntrinsicWidth(), 1);
        int alto = Math.max(drawableDefault.getIntrinsicHeight(), 1);
        Bitmap bitmapDefault = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888);
        Canvas lienzo = new Canvas(bitmapDefault);
        drawableDefault.setBounds(0, 0, lienzo.getWidth(), lienzo.getHeight());
        drawableDefault.draw(lienzo);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        boolean guardado = bitmapDefault.compress(Bitmap.CompressFormat.PNG, 100, salida);
        if (!guardado) {
            throw new IOException("No se pudo convertir la imagen por defecto.");
        }
        return salida.toByteArray();
    }


    private byte[] readBytesFromUri(Uri uriArchivo) throws IOException {
        InputStream flujo = getContentResolver().openInputStream(uriArchivo);
        if (flujo == null) {
            throw new IOException("No se pudo abrir el archivo.");
        }

        try {
            ByteArrayOutputStream acumulador = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int leidos;
            while ((leidos = flujo.read(buffer)) != -1) {
                acumulador.write(buffer, 0, leidos);
            }
            return acumulador.toByteArray();
        } finally {
            flujo.close();
        }
    }

    // Valida si la imagen seleccionada es un tipo permitido.
    private boolean isAllowedImageType(String tipoContenido) {
        return "image/jpeg".equals(tipoContenido)
                || "image/png".equals(tipoContenido)
                || "image/webp".equals(tipoContenido);
    }

    // Valida todos los datos antes de enviar el registro.
    private String validateRegistrationForm(
            String nombre,
            String apellido,
            String nick,
            String correo,
            String contrasena,
            String confirmacionContrasena,
            long cumpleanosEnMilisegundos
    ) {
        if (TextUtils.isEmpty(nick)
                || TextUtils.isEmpty(correo)
                || TextUtils.isEmpty(contrasena)
                || TextUtils.isEmpty(confirmacionContrasena)
                || cumpleanosEnMilisegundos <= 0L) {
            return getString(R.string.error_campos_obligatorios);
        }

        if (!TextUtils.isEmpty(nombre) && hasNumbers(nombre)) {
            return getString(R.string.error_nombre_apellido_numeros);
        }

        if (!TextUtils.isEmpty(apellido) && hasNumbers(apellido)) {
            return getString(R.string.error_nombre_apellido_numeros);
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            return getString(R.string.error_correo_invalido);
        }

        if (!contrasena.equals(confirmacionContrasena)) {
            return getString(R.string.error_contrasena_no_coincide);
        }

        if (!isValidPassword(contrasena)) {
            return getString(R.string.error_contrasena_regla);
        }

        int edad = getAgeFromBirthday(cumpleanosEnMilisegundos);
        if (edad < EDAD_MINIMA_REGISTRO) {
            return getString(R.string.error_edad_minima);
        }

        return "";
    }

    // Devuelve la edad en años
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

    // Verifica si una cadena tiene al menos un numero.
    private boolean hasNumbers(String valor) {
        return valor.matches(".*\\d.*");
    }

    // Verifica la seguridad de la contraseña
    private boolean isValidPassword(String contrasena) {
        return contrasena.length() >= 6 && contrasena.matches(".*\\d.*");
    }

    // Cambia el estado visual mientras se procesa el registro.
    private void updateLoadingState(boolean cargando) {
        estaRegistrando = cargando;
        barraCarga.setVisibility(cargando ? View.VISIBLE : View.GONE);

        campoNombre.setEnabled(!cargando);
        campoApellido.setEnabled(!cargando);
        campoNick.setEnabled(!cargando);
        campoCorreo.setEnabled(!cargando);
        campoCumpleanos.setEnabled(!cargando);
        campoContrasena.setEnabled(!cargando);
        campoConfirmacionContrasena.setEnabled(!cargando);
        botonRegistro.setEnabled(!cargando);
        findViewById(R.id.botonElegirFoto).setEnabled(!cargando);
    }

    // Muestra el error principal del formulario.
    private void showError(String mensaje) {
        textoError.setText(mensaje);
    }

    // Redirige al home luego de registrarse correctamente.
    private void navigateToHome() {
        Toast.makeText(this, R.string.registro_exitoso, Toast.LENGTH_SHORT).show();
        Intent pantallaInicio = new Intent(this, MainActivity.class);
        pantallaInicio.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(pantallaInicio);
        finish();
    }
}
