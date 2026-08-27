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

public class ReportGroupDialog {
    private final Context contexto;
    private final String grupoId;
    private final String grupoNombre;
    private final OnReportCompleted callback;

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

    private final ActivityResultLauncher<String> selectorImagen;

    public ReportGroupDialog(
            Context contexto,
            String grupoId,
            String grupoNombre,
            OnReportCompleted callback,
            ActivityResultLauncher<String> selectorImagen
    ) {
        this.contexto = contexto;
        this.grupoId = grupoId;
        this.grupoNombre = grupoNombre;
        this.callback = callback;
        this.selectorImagen = selectorImagen;
    }

    public void mostrar() {
        AlertDialog.Builder builder = new AlertDialog.Builder(contexto);
        LayoutInflater inflater = LayoutInflater.from(contexto);
        View vistaDialogo = inflater.inflate(R.layout.dialog_report_group, null);

        motivoSpinner = vistaDialogo.findViewById(R.id.reportGroupReasonSpinner);
        descripcionInput = vistaDialogo.findViewById(R.id.reportGroupDescriptionInput);
        imagenProuebaView = vistaDialogo.findViewById(R.id.reportGroupImagePreview);
        botonSeleccionarImagen = vistaDialogo.findViewById(R.id.reportGroupSelectImageButton);
        Button botonCancelar = vistaDialogo.findViewById(R.id.reportGroupCancelButton);
        Button botonEnviar = vistaDialogo.findViewById(R.id.reportGroupSubmitButton);

        configurarMotivos();

        botonSeleccionarImagen.setOnClickListener(v -> seleccionarImagen());
        botonCancelar.setOnClickListener(v -> dialogo.dismiss());
        botonEnviar.setOnClickListener(v -> enviarReporte());

        builder.setView(vistaDialogo);
        dialogo = builder.create();
        dialogo.show();
    }

    //Configura los motivos para reportar grupos de chat.
    private void configurarMotivos() {
        String[] motivos = {
            contexto.getString(R.string.report_reason_select),
            "Contenido inapropiado",
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

    private void seleccionarImagen() {
        selectorImagen.launch("image/*");
    }

    public void handleImageSelected(Uri imageUri) {
        if (imageUri == null) {
            return;
        }

        try {
            InputStream inputStream = contexto.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                Toast.makeText(contexto, "Error al cargar imagen", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(contexto, "Error al cargar imagen", Toast.LENGTH_SHORT).show();
        }
    }

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

        ReportRepository.createGroupReport(
            reportadorUid,
            grupoId,
            grupoNombre,
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
