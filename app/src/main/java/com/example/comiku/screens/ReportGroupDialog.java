package com.example.comiku.screens;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;

import com.example.comiku.R;
import com.example.comiku.core.ui.ReportFormComponent;
import com.example.comiku.data.repository.ReportRepository;
import com.google.firebase.auth.FirebaseAuth;

public class ReportGroupDialog {
    private final Context contexto;
    private final String grupoId;
    private final String grupoNombre;
    private final OnReportCompleted callback;
    private final ActivityResultLauncher<String> selectorImagen;

    private ReportFormComponent formularioReporte;

    public interface OnReportCompleted {
        void onSuccess();
        void onError(String mensaje);
    }

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

    // Muestra la modal reusable para reportar un grupo.
    public void mostrar() {
        formularioReporte = new ReportFormComponent(
                contexto,
                contexto.getString(R.string.report_group_title),
                getReportReasons(),
                "",
                (motivo, descripcion, capturaPantalla) -> ReportRepository.createGroupReport(
                        getCurrentUserUid(),
                        grupoId,
                        grupoNombre,
                        motivo,
                        descripcion,
                        capturaPantalla
                ),
                selectorImagen,
                this::validateDescription,
                new ReportFormComponent.ReportResultListener() {
                    @Override
                    public void onSuccess(String reporteId) {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }

                    @Override
                    public void onError(String mensaje) {
                        if (callback != null) {
                            callback.onError(mensaje);
                        }
                    }
                }
        );
        formularioReporte.show();
    }


    public void handleImageSelected(Uri imageUri) {
        if (formularioReporte != null) {
            formularioReporte.handleImageSelected(imageUri);
        }
    }

    // Devuelve los motivos validos para reportar grupos.
    private String[] getReportReasons() {
        return new String[]{
                contexto.getString(R.string.reporte_motivo_contenido_inapropiado),
                contexto.getString(R.string.reporte_motivo_spam)
        };
    }

    // Valida que la descripcion tenga el largo minimo esperado.
    private String validateDescription(String motivo, String descripcion) {
        if (TextUtils.isEmpty(descripcion) || descripcion.length() >= 10) {
            return "";
        }
        return contexto.getString(R.string.reporte_error_descripcion_minima);
    }

    // Obtiene el uid actual o vacio para que el repositorio lo valide.
    private String getCurrentUserUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return "";
        }
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }
}
