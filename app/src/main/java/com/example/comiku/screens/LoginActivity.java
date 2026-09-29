package com.example.comiku.screens;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.comiku.R;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;

public class LoginActivity extends BasePlainScreenActivity {
    private EditText campoCorreo;
    private EditText campoContrasena;
    private TextView textoError;
    private ProgressBar barraCarga;
    private boolean estaIngresando = false;

    // Usa el mismo fondo del login para que el borde superior no se vea blanco.
    @Override
    protected int getShellBackgroundColorRes() {
        return R.color.fondo_login_registro;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            navigateToHome();
            return;
        }

        setupPlainScreenShell(R.layout.activity_login);
        bindViews();
        setupListeners();
        updateLoadingState(false);
    }


    private void bindViews() {
        campoCorreo = findViewById(R.id.campoCorreoLogin);
        campoContrasena = findViewById(R.id.campoContrasenaLogin);
        textoError = findViewById(R.id.textoErrorLogin);
        barraCarga = findViewById(R.id.barraCargaLogin);
    }

    // Conecta acciones de login y registro.
    private void setupListeners() {
        Button botonIngresar = findViewById(R.id.botonIngresar);
        botonIngresar.setOnClickListener(v -> handleLogin());

        TextView textoIrRegistro = findViewById(R.id.textoIrRegistro);
        textoIrRegistro.setOnClickListener(v -> {
            Intent pantallaRegistro = new Intent(this, RegisterActivity.class);
            startActivity(pantallaRegistro);
        });
    }

    // Inicia login si los datos del formulario son validos.
    private void handleLogin() {
        if (estaIngresando) {
            return;
        }

        textoError.setText("");
        String correo = campoCorreo.getText().toString().trim();
        String contrasena = campoContrasena.getText().toString();

        String errorFormulario = validateLoginForm(correo, contrasena);
        if (!TextUtils.isEmpty(errorFormulario)) {
            showError(errorFormulario);
            return;
        }

        updateLoadingState(true);
        FirebaseAuth.getInstance()
                .signInWithEmailAndPassword(correo, contrasena)
                .addOnSuccessListener(resultado -> {
                    updateLoadingState(false);
                    navigateToHome();
                })
                .addOnFailureListener(error -> {
                    updateLoadingState(false);
                    showError(mapAuthError(error.getMessage()));
                });
    }

    // Valida correo y contraseña obligatorios en login.
    private String validateLoginForm(String correo, String contrasena) {
        if (TextUtils.isEmpty(correo) || TextUtils.isEmpty(contrasena)) {
            return getString(R.string.error_login_campos_obligatorios);
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            return getString(R.string.error_correo_invalido);
        }

        return "";
    }

    // Traduce errores de Firebase a mensajes claros para el usuario.
    private String mapAuthError(String mensajeOriginal) {
        String mensaje = String.valueOf(mensajeOriginal).toLowerCase(Locale.ROOT);

        if (mensaje.contains("auth/invalid-credential")
                || mensaje.contains("auth/wrong-password")
                || mensaje.contains("auth/user-not-found")
                || mensaje.contains("invalid login credentials")) {
            return getString(R.string.error_login_credenciales);
        }

        if (mensaje.contains("auth/invalid-email") || mensaje.contains("invalid email")) {
            return getString(R.string.error_correo_invalido);
        }

        return getString(R.string.error_login_general);
    }

    // Actualiza estado visual de carga durante login.
    private void updateLoadingState(boolean cargando) {
        estaIngresando = cargando;
        barraCarga.setVisibility(cargando ? View.VISIBLE : View.GONE);
        campoCorreo.setEnabled(!cargando);
        campoContrasena.setEnabled(!cargando);
        findViewById(R.id.botonIngresar).setEnabled(!cargando);
        findViewById(R.id.textoIrRegistro).setEnabled(!cargando);
    }

    // Muestra error principal del formulario de login.
    private void showError(String mensaje) {
        textoError.setText(mensaje);
    }

    // Redirige al inicio luego de login correcto.
    private void navigateToHome() {
        Intent pantallaInicio = new Intent(this, MainActivity.class);
        pantallaInicio.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(pantallaInicio);
        finish();
    }
}
