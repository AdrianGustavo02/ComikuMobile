package com.example.comiku.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.comiku.R;
import com.example.comiku.core.ui.ToastUtils;
import com.example.comiku.data.repository.DeleteAccountRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawableResource(R.drawable.bg_report_dialog_rounded);
        }
    }

    // Muestra la primera etapa de confirmacion.
    private void mostrarEtapa1() {
        etapaActual = 1;
        aplicarTextoAdvertencia(contexto.getString(R.string.delete_account_warning_1));
        campoConfirmacion.setHint(contexto.getString(R.string.delete_account_input_hint_1));
        campoConfirmacion.setText("");
        campoConfirmacion.setVisibility(View.GONE);
        barraProgreso.setVisibility(View.GONE);
        botonConfirmar.setText(contexto.getString(R.string.delete_account_understand));
    }

    // Muestra la segunda etapa de confirmacion.
    private void mostrarEtapa2() {
        etapaActual = 2;
        aplicarTextoAdvertencia(contexto.getString(R.string.delete_account_warning_2, nickUsuario));
        campoConfirmacion.setHint(contexto.getString(R.string.delete_account_input_hint_2));
        campoConfirmacion.setText("");
        campoConfirmacion.setVisibility(View.VISIBLE);
        barraProgreso.setVisibility(View.GONE);
        botonConfirmar.setText(contexto.getString(R.string.delete_account_final_confirm));
    }

    // Aplica el texto con palabras en mayusculas en negrita.
    private void aplicarTextoAdvertencia(String texto) {
        if (TextUtils.isEmpty(texto)) {
            textoAdvertencia.setText("");
            return;
        }
        SpannableString textoFormateado = new SpannableString(texto);
        Pattern patronMayusculas = Pattern.compile("\\b[\\p{Lu}0-9]{2,}\\b");
        Matcher matcher = patronMayusculas.matcher(texto);
        while (matcher.find()) {
            textoFormateado.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    matcher.start(),
                    matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        textoAdvertencia.setText(textoFormateado);
    }

    private void procederConConfirmacion() {
        if (etapaActual == 1) {
            mostrarEtapa2();
            return;
        }

        if (etapaActual == 2) {
            String inputUsuario = campoConfirmacion.getText().toString().trim();

            if (TextUtils.isEmpty(inputUsuario)) {
                ToastUtils.showTextToast(contexto, contexto.getString(R.string.delete_account_empty_confirmation), Toast.LENGTH_SHORT);
                return;
            }

            if (!inputUsuario.equals(nickUsuario)) {
                ToastUtils.showTextToast(contexto, contexto.getString(R.string.delete_account_nick_mismatch), Toast.LENGTH_SHORT);
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

        ToastUtils.showTextToast(contexto, mensaje, Toast.LENGTH_LONG);
        textoAdvertencia.setText("Error: " + mensaje);
    }
}
