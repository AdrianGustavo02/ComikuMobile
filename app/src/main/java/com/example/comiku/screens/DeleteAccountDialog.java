package com.example.comiku.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.comiku.R;
import com.example.comiku.data.repository.DeleteAccountRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class DeleteAccountDialog {
    private final Context contexto;
    private final String nickUsuario;
    private final OnDeleteCompleted callback;

    private AlertDialog dialogo;
    private int etapaActual = 0;
    private EditText campoConfirmacion;
    private ProgressBar barraProgreso;
    private TextView textoAdvertencia;
    private Button botonCancelar;
    private Button botonConfirmar;

    public interface OnDeleteCompleted {
        void onSuccess();
        void onError(String mensaje);
        void onCancelled();
    }

    public DeleteAccountDialog(
            Context contexto,
            String nickUsuario,
            OnDeleteCompleted callback
    ) {
        this.contexto = contexto;
        this.nickUsuario = nickUsuario;
        this.callback = callback;
    }

    public void mostrar() {
        AlertDialog.Builder builder = new AlertDialog.Builder(contexto);
        LayoutInflater inflater = LayoutInflater.from(contexto);
        View vistaDialogo = inflater.inflate(R.layout.dialog_delete_account, null);

        campoConfirmacion = vistaDialogo.findViewById(R.id.deleteAccountConfirmInput);
        barraProgreso = vistaDialogo.findViewById(R.id.deleteAccountProgressBar);
        textoAdvertencia = vistaDialogo.findViewById(R.id.deleteAccountWarningText);
        botonCancelar = vistaDialogo.findViewById(R.id.deleteAccountCancelButton);
        botonConfirmar = vistaDialogo.findViewById(R.id.deleteAccountConfirmButton);

        builder.setView(vistaDialogo);
        builder.setCancelable(false);
        dialogo = builder.create();

        botonCancelar.setOnClickListener(v -> {
            dialogo.dismiss();
            if (callback != null) {
                callback.onCancelled();
            }
        });

        botonConfirmar.setOnClickListener(v -> procederConConfirmacion());

        mostrarEtapa1();
        dialogo.show();
    }

    private void mostrarEtapa1() {
        etapaActual = 1;
        textoAdvertencia.setText(contexto.getString(R.string.delete_account_warning_1));
        campoConfirmacion.setHint(contexto.getString(R.string.delete_account_input_hint_1));
        campoConfirmacion.setText("");
        campoConfirmacion.setVisibility(View.GONE);
        barraProgreso.setVisibility(View.GONE);
        botonConfirmar.setText(contexto.getString(R.string.delete_account_understand));
    }

    private void mostrarEtapa2() {
        etapaActual = 2;
        textoAdvertencia.setText(contexto.getString(R.string.delete_account_warning_2, nickUsuario));
        campoConfirmacion.setHint(contexto.getString(R.string.delete_account_input_hint_2));
        campoConfirmacion.setText("");
        campoConfirmacion.setVisibility(View.VISIBLE);
        barraProgreso.setVisibility(View.GONE);
        botonConfirmar.setText(contexto.getString(R.string.delete_account_final_confirm));
    }

    private void procederConConfirmacion() {
        if (etapaActual == 1) {
            mostrarEtapa2();
            return;
        }

        if (etapaActual == 2) {
            String inputUsuario = campoConfirmacion.getText().toString().trim();

            if (TextUtils.isEmpty(inputUsuario)) {
                Toast.makeText(contexto, contexto.getString(R.string.delete_account_empty_confirmation), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!inputUsuario.equals(nickUsuario)) {
                Toast.makeText(contexto, contexto.getString(R.string.delete_account_nick_mismatch), Toast.LENGTH_SHORT).show();
                campoConfirmacion.setText("");
                return;
            }

            ejecutarEliminacionDeCuenta();
        }
    }

    private void ejecutarEliminacionDeCuenta() {
        botonConfirmar.setEnabled(false);
        botonCancelar.setEnabled(false);
        campoConfirmacion.setEnabled(false);
        barraProgreso.setVisibility(View.VISIBLE);

        FirebaseUser usuarioActual = FirebaseAuth.getInstance().getCurrentUser();
        if (usuarioActual == null) {
            mostrarError(contexto.getString(R.string.delete_account_error_no_session));
            return;
        }

        DeleteAccountRepository.deleteAccountComplete()
                .addOnSuccessListener(mensaje -> {
                    FirebaseAuth.getInstance().signOut();
                    dialogo.dismiss();
                    if (callback != null) {
                        callback.onSuccess();
                    }
                })
                .addOnFailureListener(error -> {
                    String mensajeError = error.getMessage();
                    if (TextUtils.isEmpty(mensajeError)) {
                        mensajeError = contexto.getString(R.string.delete_account_error_unknown);
                    }
                    mostrarError(mensajeError);
                });
    }

    private void mostrarError(String mensaje) {
        botonConfirmar.setEnabled(true);
        botonCancelar.setEnabled(true);
        campoConfirmacion.setEnabled(true);
        barraProgreso.setVisibility(View.GONE);

        Toast.makeText(contexto, mensaje, Toast.LENGTH_LONG).show();
        textoAdvertencia.setText("Error: " + mensaje);
    }
}
