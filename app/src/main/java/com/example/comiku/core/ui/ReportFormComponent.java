package com.example.comiku.core.ui;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;

import com.example.comiku.R;
import com.example.comiku.core.ui.ToastUtils;
import com.example.comiku.core.validation.InputValidator;
import com.google.android.gms.tasks.Task;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class ReportFormComponent {
    public interface ReportSubmitter {
        Task<String> submit(String motivo, String descripcion, Map<String, Object> capturaPantalla);
    }

    public interface ReportFormValidator {
        String validate(String motivo, String descripcion);
    }

    public interface ReportResultListener {
        void onSuccess(String reporteId);
        void onError(String mensaje);
    }

    private final Context contexto;
    private final String titulo;
    private final String[] motivos;
    private final String mensajeConfirmacion;
    private final ReportSubmitter submitter;
    private final ActivityResultLauncher<String> selectorImagen;
    private final ReportFormValidator validadorFormulario;
    private final ReportResultListener listenerResultado;

    private AlertDialog dialogo;
    private Spinner spinnerMotivo;
    private EditText campoDescripcion;
    private View contenedorCaptura;
    private TextView textoError;
    private ImageView vistaPreviaCaptura;
    private Button botonSeleccionarCaptura;
    private Button botonQuitarCaptura;
    private String capturaDataUrl = "";
    private String capturaNombreArchivo = "";
    private String capturaTipoContenido = "";
    private long capturaTamanoBytes = 0L;

    // Prepara el formulario de reporte
    public ReportFormComponent(
            Context contexto,
            String titulo,
            String[] motivos,
            String mensajeConfirmacion,
            ReportSubmitter submitter,
            ActivityResultLauncher<String> selectorImagen
    ) {
        this(
                contexto,
                titulo,
                motivos,
                mensajeConfirmacion,
                submitter,
                selectorImagen,
                null,
                null
        );
    }

    // Prepara el formulario con validaciones y eventos opcionales.
    public ReportFormComponent(
            Context contexto,
            String titulo,
            String[] motivos,
            String mensajeConfirmacion,
            ReportSubmitter submitter,
            ActivityResultLauncher<String> selectorImagen,
            ReportFormValidator validadorFormulario,
            ReportResultListener listenerResultado
    ) {
        this.contexto = contexto;
        this.titulo = titulo;
        this.motivos = motivos;
        this.mensajeConfirmacion = mensajeConfirmacion;
        this.submitter = submitter;
        this.selectorImagen = selectorImagen;
        this.validadorFormulario = validadorFormulario;
        this.listenerResultado = listenerResultado;
    }

    // Muestra el dialogo y reinicia su estado.
    public void show() {
        View vistaFormulario = LayoutInflater.from(contexto).inflate(R.layout.view_report_form, null, false);
        spinnerMotivo = vistaFormulario.findViewById(R.id.spinnerMotivoReporte);
        campoDescripcion = vistaFormulario.findViewById(R.id.campoDescripcionReporte);
        contenedorCaptura = vistaFormulario.findViewById(R.id.contenedorCapturaReporte);
        textoError = vistaFormulario.findViewById(R.id.textoErrorReporte);
        vistaPreviaCaptura = vistaFormulario.findViewById(R.id.imagenCapturaReporte);
        botonSeleccionarCaptura = vistaFormulario.findViewById(R.id.botonSeleccionarCapturaReporte);
        botonQuitarCaptura = vistaFormulario.findViewById(R.id.botonQuitarCapturaReporte);

        String placeholder = contexto.getString(R.string.reporte_hint_motivo);
        String[] motivosConPlaceholder = new String[motivos.length + 1];
        motivosConPlaceholder[0] = placeholder;
        System.arraycopy(motivos, 0, motivosConPlaceholder, 1, motivos.length);

        ArrayAdapter<String> adaptadorMotivos = new ArrayAdapter<>(
                contexto,
                android.R.layout.simple_spinner_item,
                motivosConPlaceholder
        );
        adaptadorMotivos.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMotivo.setAdapter(adaptadorMotivos);

        configurarDescripcion();
        configurarCaptura();

        dialogo = new AlertDialog.Builder(contexto)
                .setCustomTitle(createDialogTitleView())
                .setView(vistaFormulario)
                .setNegativeButton(R.string.reporte_boton_cancelar, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.reporte_boton_enviar, null)
                .create();

        dialogo.setOnShowListener(dialogInterface -> {
            Button botonEnviar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE);
            Button botonCancelar = dialogo.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (botonEnviar != null) {
                applyDialogButtonStyle(
                        botonEnviar,
                        R.drawable.bg_button_primary_action,
                        contexto.getColorStateList(R.color.button_primary_action_text)
                );
                botonEnviar.setOnClickListener(v -> enviarReporte(botonEnviar, botonCancelar));
            }

            if (botonCancelar != null) {
                applyDialogButtonStyle(
                        botonCancelar,
                        R.drawable.bg_button_danger,
                        ColorStateList.valueOf(contexto.getColor(android.R.color.white))
                );
            }

            applyDialogButtonsSpacing(botonCancelar, botonEnviar);
        });

        dialogo.show();
        if (dialogo.getWindow() != null) {
            dialogo.getWindow().setBackgroundDrawableResource(R.drawable.bg_report_dialog_rounded);
        }
    }

    // Recibe la imagen elegida y la deja lista para enviar.
    public void handleImageSelected(Uri imageUri) {
        if (imageUri == null) {
            return;
        }

        try {
            ContentResolver resolver = contexto.getContentResolver();
            String tipoContenido = resolver.getType(imageUri);
            if (TextUtils.isEmpty(tipoContenido)) {
                tipoContenido = "image/png";
            }

            byte[] bytesImagen = readImageBytes(imageUri);
            if (bytesImagen == null || bytesImagen.length == 0) {
                throw new IOException("No se pudo leer la imagen.");
            }

            capturaDataUrl = buildDataUrl(tipoContenido, bytesImagen);
            capturaNombreArchivo = resolveFileName(imageUri);
            capturaTipoContenido = tipoContenido;
            capturaTamanoBytes = bytesImagen.length;

            Bitmap bitmap = BitmapFactory.decodeByteArray(bytesImagen, 0, bytesImagen.length);
            if (bitmap != null && vistaPreviaCaptura != null) {
                vistaPreviaCaptura.setImageBitmap(bitmap);
                vistaPreviaCaptura.setVisibility(View.VISIBLE);
            }
            if (contenedorCaptura != null) {
                contenedorCaptura.setVisibility(View.VISIBLE);
            }

            if (botonSeleccionarCaptura != null) {
                botonSeleccionarCaptura.setText(R.string.reporte_boton_captura_cambiar);
            }
            if (botonQuitarCaptura != null) {
                botonQuitarCaptura.setVisibility(View.VISIBLE);
            }
        } catch (IOException error) {
            limpiarCaptura();
            mostrarError(contexto.getString(R.string.reporte_error_captura_lectura));
        }
    }

    // Limpia los caracteres prohibidos sin quitar espacios.
    private String sanitizeText(String valor) {
        return InputValidator.sanitizeForbiddenChars(valor);
    }

    // Configura el campo de descripcion
    private void configurarDescripcion() {
        // La sanitizacion se aplica solo al momento de enviar el reporte.
    }

    // Configura el boton de captura y el estado inicial.
    private void configurarCaptura() {
        limpiarCapturaVista();
        botonSeleccionarCaptura.setOnClickListener(v -> {
            if (selectorImagen != null) {
                selectorImagen.launch("image/*");
            }
        });
        botonQuitarCaptura.setOnClickListener(v -> limpiarCaptura());
    }

    // Crea el titulo del dialogo con un estilo mas visible.
    private TextView createDialogTitleView() {
        TextView textoTitulo = new TextView(contexto);
        textoTitulo.setText(titulo);
        textoTitulo.setTextColor(contexto.getColor(android.R.color.black));
        textoTitulo.setTypeface(null, Typeface.BOLD);
        textoTitulo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        textoTitulo.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(8));
        return textoTitulo;
    }

    // Aplica el estilo visual del boton dentro del dialogo.
    private void applyDialogButtonStyle(Button boton, int fondoResId, ColorStateList colorTexto) {
        boton.setAllCaps(false);
        boton.setBackgroundResource(fondoResId);
        boton.setBackgroundTintList(null);
        boton.setTextColor(colorTexto);
        boton.setTypeface(null, Typeface.BOLD);
        boton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        boton.setPadding(dpToPx(16), dpToPx(11), dpToPx(16), dpToPx(11));
        boton.setMinHeight(0);
    }

    // Separa los botones del dialogo para que no queden pegados.
    private void applyDialogButtonsSpacing(Button botonCancelar, Button botonEnviar) {
        applyDialogButtonMargin(botonCancelar, 0, 8);
        applyDialogButtonMargin(botonEnviar, 8, 0);
    }

    // Agrega margen lateral al boton si el contenedor lo permite.
    private void applyDialogButtonMargin(Button boton, int margenInicioDp, int margenFinDp) {
        if (boton == null) {
            return;
        }

        ViewGroup.LayoutParams layoutParams = boton.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
        marginLayoutParams.setMarginStart(dpToPx(margenInicioDp));
        marginLayoutParams.setMarginEnd(dpToPx(margenFinDp));
        boton.setLayoutParams(marginLayoutParams);
    }

    // Convierte dp a pixeles para mantener medidas consistentes.
    private int dpToPx(int valorDp) {
        float densidad = contexto.getResources().getDisplayMetrics().density;
        return Math.round(valorDp * densidad);
    }

    // Envía el reporte con o sin captura.
    private void enviarReporte(Button botonEnviar, Button botonCancelar) {
        textoError.setVisibility(View.GONE);

        String motivoSeleccionado = spinnerMotivo.getSelectedItem() != null
                ? String.valueOf(spinnerMotivo.getSelectedItem())
                : "";
        String descripcion = campoDescripcion.getText() != null
                ? sanitizeText(campoDescripcion.getText().toString().trim())
                : "";

        if (spinnerMotivo.getSelectedItemPosition() <= 0 || TextUtils.isEmpty(motivoSeleccionado)) {
            mostrarError(contexto.getString(R.string.reporte_error_motivo));
            return;
        }

        if (TextUtils.isEmpty(descripcion)) {
            mostrarError(contexto.getString(R.string.reporte_error_descripcion));
            return;
        }

        String errorFormulario = validateForm(motivoSeleccionado, descripcion);
        if (!TextUtils.isEmpty(errorFormulario)) {
            mostrarError(errorFormulario);
            return;
        }

        botonEnviar.setEnabled(false);
        botonCancelar.setEnabled(false);
        campoDescripcion.setEnabled(false);
        spinnerMotivo.setEnabled(false);
        if (botonSeleccionarCaptura != null) {
            botonSeleccionarCaptura.setEnabled(false);
        }

        submitter.submit(motivoSeleccionado, descripcion, buildScreenshotPayload())
                .addOnSuccessListener(id -> {
                    if (!TextUtils.isEmpty(mensajeConfirmacion)) {
                        ToastUtils.showTextToast(contexto, mensajeConfirmacion, Toast.LENGTH_SHORT);
                    }
                    if (listenerResultado != null) {
                        listenerResultado.onSuccess(id);
                    }
                    dialogo.dismiss();
                })
                .addOnFailureListener(error -> {
                    botonEnviar.setEnabled(true);
                    botonCancelar.setEnabled(true);
                    campoDescripcion.setEnabled(true);
                    spinnerMotivo.setEnabled(true);
                    if (botonSeleccionarCaptura != null) {
                        botonSeleccionarCaptura.setEnabled(true);
                    }
                    String mensaje = error != null && !TextUtils.isEmpty(error.getMessage())
                            ? error.getMessage()
                            : contexto.getString(R.string.reporte_error_envio);
                    mostrarError(mensaje);
                    if (listenerResultado != null) {
                        listenerResultado.onError(mensaje);
                    }
                });
    }

    // Ejecuta validaciones extra del formulario si existen.
    private String validateForm(String motivo, String descripcion) {
        if (validadorFormulario == null) {
            return "";
        }

        String mensaje = validadorFormulario.validate(motivo, descripcion);
        return mensaje != null ? mensaje : "";
    }

    // Construye el objeto de captura para guardarlo en Firestore.
    private Map<String, Object> buildScreenshotPayload() {
        if (TextUtils.isEmpty(capturaDataUrl)) {
            return null;
        }

        Map<String, Object> captura = new HashMap<>();
        captura.put("dataUrl", capturaDataUrl);
        if (!TextUtils.isEmpty(capturaNombreArchivo)) {
            captura.put("fileName", capturaNombreArchivo);
        }
        if (!TextUtils.isEmpty(capturaTipoContenido)) {
            captura.put("contentType", capturaTipoContenido);
        }
        captura.put("sizeBytes", capturaTamanoBytes);
        captura.put("source", "android-inline");
        return captura;
    }

    // Lee los bytes de la imagen seleccionada.
    private byte[] readImageBytes(Uri imageUri) throws IOException {
        InputStream inputStream = contexto.getContentResolver().openInputStream(imageUri);
        if (inputStream == null) {
            return null;
        }

        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int leidos;
            while ((leidos = inputStream.read(buffer)) != -1) {
                salida.write(buffer, 0, leidos);
            }
            return salida.toByteArray();
        } finally {
            inputStream.close();
        }
    }

    // Convierte los bytes de imagen en un dataUrl compatible con Firestore.
    private String buildDataUrl(String tipoContenido, byte[] bytesImagen) {
        String base64 = Base64.encodeToString(bytesImagen, Base64.NO_WRAP);
        return "data:" + tipoContenido + ";base64," + base64;
    }

    // Obtiene un nombre simple de archivo para la captura.
    private String resolveFileName(Uri imageUri) {
        Cursor cursor = null;
        try {
            cursor = contexto.getContentResolver().query(imageUri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int indiceNombre = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (indiceNombre >= 0) {
                    String nombre = cursor.getString(indiceNombre);
                    if (!TextUtils.isEmpty(nombre)) {
                        return nombre;
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return imageUri.getLastPathSegment() != null ? imageUri.getLastPathSegment() : "";
    }

    // Muestra un error en el formulario.
    private void mostrarError(String mensaje) {
        textoError.setText(mensaje);
        textoError.setVisibility(View.VISIBLE);
    }

    // Limpia la captura guardada y su vista previa.
    private void limpiarCaptura() {
        capturaDataUrl = "";
        capturaNombreArchivo = "";
        capturaTipoContenido = "";
        capturaTamanoBytes = 0L;
        limpiarCapturaVista();
    }

    // Resetea la vista previa de la captura.
    private void limpiarCapturaVista() {
        if (contenedorCaptura != null) {
            contenedorCaptura.setVisibility(View.GONE);
        }
        if (vistaPreviaCaptura != null) {
            vistaPreviaCaptura.setImageDrawable(null);
            vistaPreviaCaptura.setVisibility(View.GONE);
        }
        if (botonSeleccionarCaptura != null) {
            botonSeleccionarCaptura.setText(R.string.reporte_boton_captura_agregar);
        }
        if (botonQuitarCaptura != null) {
            botonQuitarCaptura.setVisibility(View.GONE);
        }
    }
}
