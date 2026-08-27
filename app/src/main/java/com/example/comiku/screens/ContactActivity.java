package com.example.comiku.screens;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.comiku.R;
import com.example.comiku.core.validation.InputValidator;
import com.example.comiku.data.repository.ContactMessageRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ContactActivity extends BaseDrawerActivity {
    private Spinner selectorTipoMensaje;
    private EditText campoMensaje;
    private Button botonEnviar;
    private ProgressBar barraCarga;
    private TextView textoEstado;
    private String uidActual = "";

    // Inicializa la pantalla de contacto.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser usuarioActual = FirebaseAuth.getInstance().getCurrentUser();
        if (usuarioActual == null) {
            openLoginAndClearStack();
            return;
        }

        uidActual = usuarioActual.getUid();
        setupDrawerShell(getString(R.string.contacto_titulo));
    }

    // Devuelve el layout de esta pantalla.
    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_contact;
    }

    // Prepara vistas, selector y acciones.
    @Override
    protected void onScreenContentReady() {
        bindViews();
        setupTypeSelector();
        setupSendButton();
    }

    // Vincula vistas del formulario.
    private void bindViews() {
        selectorTipoMensaje = findViewById(R.id.selectorTipoMensajeContacto);
        campoMensaje = findViewById(R.id.campoMensajeContacto);
        botonEnviar = findViewById(R.id.botonEnviarContacto);
        barraCarga = findViewById(R.id.barraCargaContacto);
        textoEstado = findViewById(R.id.textoEstadoContacto);
    }

    // Carga los tipos de mensaje permitidos.
    private void setupTypeSelector() {
        ArrayAdapter<String> adaptadorTipos = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                ContactMessageRepository.getMessageTypes()
        );
        adaptadorTipos.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        selectorTipoMensaje.setAdapter(adaptadorTipos);
    }

    // Conecta el envio del mensaje a la administracion.
    private void setupSendButton() {
        botonEnviar.setOnClickListener(v -> submitMessage());
    }

    // Valida y guarda el mensaje de contacto.
    private void submitMessage() {
        String tipoSeleccionado = String.valueOf(selectorTipoMensaje.getSelectedItem());
        String mensaje = InputValidator.sanitizeForbiddenChars(
                String.valueOf(campoMensaje.getText())
        ).trim();

        textoEstado.setVisibility(View.GONE);
        if (TextUtils.isEmpty(mensaje)) {
            textoEstado.setText(getString(R.string.contacto_error_mensaje_obligatorio));
            textoEstado.setVisibility(View.VISIBLE);
            return;
        }

        setLoadingState(true);
        ContactMessageRepository.createMessage(tipoSeleccionado, mensaje, uidActual)
                .addOnSuccessListener(idMensaje -> {
                    setLoadingState(false);
                    campoMensaje.setText("");
                    selectorTipoMensaje.setSelection(0);
                    Toast.makeText(
                            this,
                            getString(R.string.contacto_confirmacion),
                            Toast.LENGTH_LONG
                    ).show();
                })
                .addOnFailureListener(error -> {
                    setLoadingState(false);
                    textoEstado.setText(
                            error.getMessage() != null
                                    ? error.getMessage()
                                    : getString(R.string.contacto_error_envio)
                    );
                    textoEstado.setVisibility(View.VISIBLE);
                });
    }

    // Controla el estado de carga del formulario.
    private void setLoadingState(boolean cargando) {
        barraCarga.setVisibility(cargando ? View.VISIBLE : View.GONE);
        botonEnviar.setEnabled(!cargando);
        selectorTipoMensaje.setEnabled(!cargando);
        campoMensaje.setEnabled(!cargando);
    }
}

