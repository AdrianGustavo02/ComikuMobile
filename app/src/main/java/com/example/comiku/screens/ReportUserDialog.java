package com.example.comiku.screens;

import android.app.AlertDialog;
import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;

import com.bumptech.glide.Glide;
import com.example.comiku.R;
import com.example.comiku.data.repository.ReportRepository;
import com.google.firebase.auth.FirebaseAuth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ReportUserDialog {
    private final Context contexto;
    private final String usuarioReportadoUid;
    private final String usuarioReportadoNick;
    private final OnReportCompleted callback;
    private final ActivityResultLauncher<String> selectorImagen;

    private AlertDialog dialogo;
    private Spinner motivoSpinner;
    private EditText descripcionInput;
    private ImageView imagenProuebaView;
    private Button botonSeleccionarImagen;
    private Map<String, Object> capturaPantalla = null;

    public interface OnReportCompleted {
        void onSuccess();
        void onError(String mensaje);
    }

    public ReportUserDialog(
            Context contexto,
            String usuarioReportadoUid,
            String usuarioReportadoNick,
            OnReportCompleted callback,
            ActivityResultLauncher<String> selectorImagen
    ) {
        this.contexto = contexto;
        this.usuarioReportadoUid = usuarioReportadoUid;
        this.usuarioReportadoNick = usuarioReportadoNick;
        this.callback = callback;
        this.selectorImagen = selectorImagen;
    }

    // Muestra formulario para reportar usuario
    public void mostrar() {
        AlertDialog.Builder builder = new AlertDialog.Builder(contexto);
        LayoutInflater inflater = LayoutInflater.from(contexto);
        View vistaDialogo = inflater.inflate(R.layout.dialog_report_user, null);

        motivoSpinner = vistaDialogo.findViewById(R.id.reportReasonSpinner);
        descripcionInput = vistaDialogo.findViewById(R.id.reportDescriptionInput);
        imagenProuebaView = vistaDialogo.findViewById(R.id.reportUserImagePreview);
        botonSeleccionarImagen = vistaDialogo.findViewById(R.id.reportUserSelectImageButton);
        Button botonCancelar = vistaDialogo.findViewById(R.id.reportCancelButton);
        Button botonEnviar = vistaDialogo.findViewById(R.id.reportSubmitButton);

        configurarMotivos();

        botonSeleccionarImagen.setOnClickListener(v -> seleccionarImagen());
        builder.setView(vistaDialogo);
        dialogo = builder.create();

        botonCancelar.setOnClickListener(v -> dialogo.dismiss());
        botonEnviar.setOnClickListener(v -> enviarReporte());

        dialogo.show();
    }

    // Carga los motivos para reporte de usuario.
    private void configurarMotivos() {
        String[] motivos = {
            contexto.getString(R.string.report_reason_select),
            "Comportamiento inapropiado",
            "Spam"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            contexto,
            android.R.layout.simple_spinner_item,
            motivos
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        motivoSpinner.setAdapter(adapter);
    }

    // Abre selector de imagen
    private void seleccionarImagen() {
        selectorImagen.launch("image/*");
    }

    // Recibe imagen elegida y la guarda en base64 para enviar en el reporte.
    public void handleImageSelected(Uri imageUri) {
        if (imageUri == null) {
            return;
        }

        try {
            InputStream inputStream = contexto.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                Toast.makeText(contexto, contexto.getString(R.string.report_error_image_load), Toast.LENGTH_SHORT).show();
                return;
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();

            byte[] imageBytes = outputStream.toByteArray();
            String contentType = contexto.getContentResolver().getType(imageUri);
            if (contentType == null || contentType.trim().isEmpty()) {
                contentType = "image/jpeg";
            }
            String fileName = imageUri.getLastPathSegment();
            if (fileName == null || fileName.trim().isEmpty()) {
                fileName = "captura.jpg";
            }
            String dataUrl = "data:" + contentType + ";base64," + Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            capturaPantalla = buildScreenshotPayload(contentType, dataUrl, fileName, imageBytes.length);

            Glide.with(contexto)
                    .load(imageUri)
                    .override(200, 200)
                    .into(imagenProuebaView);

            imagenProuebaView.setVisibility(View.VISIBLE);
            botonSeleccionarImagen.setText(contexto.getString(R.string.report_change_image));
        } catch (IOException e) {
            Toast.makeText(contexto, contexto.getString(R.string.report_error_image_load), Toast.LENGTH_SHORT).show();
        }
    }

    // Valida datos y envia el reporte del usuario.
    private void enviarReporte() {
        String motivo = (String) motivoSpinner.getSelectedItem();
        String descripcion = descripcionInput.getText().toString().trim();

        if (motivoSpinner.getSelectedItemPosition() <= 0) {
            Toast.makeText(contexto, "Selecciona un motivo", Toast.LENGTH_SHORT).show();
            return;
        }

        if (descripcion.isEmpty()) {
            Toast.makeText(contexto, "La descripcion es obligatoria", Toast.LENGTH_SHORT).show();
            return;
        }

        if (descripcion.length() < 10) {
            Toast.makeText(contexto, "La descripcion debe tener al menos 10 caracteres", Toast.LENGTH_SHORT).show();
            return;
        }

        String reportadorUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        ReportRepository.createUserReport(
            reportadorUid,
            usuarioReportadoUid,
            usuarioReportadoNick,
            motivo,
            descripcion,
            capturaPantalla
        ).addOnSuccessListener(reporteId -> {
            Toast.makeText(contexto, contexto.getString(R.string.report_success), Toast.LENGTH_SHORT).show();
            dialogo.dismiss();
            if (callback != null) {
                callback.onSuccess();
            }
        }).addOnFailureListener(error -> {
            String mensaje = error.getMessage();
            if (mensaje == null) {
                mensaje = contexto.getString(R.string.report_error);
            }
            Toast.makeText(contexto, mensaje, Toast.LENGTH_LONG).show();
            if (callback != null) {
                callback.onError(mensaje);
            }
        });
    }


    private Map<String, Object> buildScreenshotPayload(
            String contentType,
            String dataUrl,
            String fileName,
            int sizeBytes
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("contentType", contentType);
        payload.put("dataUrl", dataUrl);
        payload.put("fileName", fileName);
        payload.put("sizeBytes", sizeBytes);
        payload.put("source", "android-inline");
        return payload;
    }
}
