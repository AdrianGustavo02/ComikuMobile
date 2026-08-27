package com.example.comiku.screens;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.comiku.R;
import com.example.comiku.data.model.VolumeDetailData;
import com.example.comiku.data.repository.ComicDetailRepository;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BarcodeScannerActivity extends androidx.appcompat.app.AppCompatActivity {
    private PreviewView vistaPreviaCamara;
    private ProgressBar barraCarga;
    private TextView textoEstado;
    private ImageButton botonCerrar;

    private final ExecutorService executorCamara = Executors.newSingleThreadExecutor();
    private final AtomicBoolean resultadoProcesado = new AtomicBoolean(false);
    private BarcodeScanner scannerBarcodes;

    private final ActivityResultLauncher<String> permisoCamaraLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), concedido -> {
                if (Boolean.TRUE.equals(concedido)) {
                    iniciarCamara();
                } else {
                    Toast.makeText(this, R.string.scanner_permiso_camara, Toast.LENGTH_SHORT).show();
                    finish();
                }
            });

    // Inicializa la pantalla del escaner.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_scanner);
        bindViews();
        setupScanner();
        setupListeners();
        requestCameraPermission();
    }

    // Vincula las vistas del layout.
    private void bindViews() {
        vistaPreviaCamara = findViewById(R.id.vistaPreviaCamaraScanner);
        barraCarga = findViewById(R.id.barraCargaScanner);
        textoEstado = findViewById(R.id.textoEstadoScanner);
        botonCerrar = findViewById(R.id.botonCerrarScanner);
    }

    // Configura el lector de codigos de barras.
    private void setupScanner() {
        BarcodeScannerOptions opciones = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_EAN_13,
                        Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A,
                        Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_CODE_128
                )
                .build();
        scannerBarcodes = BarcodeScanning.getClient(opciones);
    }

    // Conecta los controles de la pantalla.
    private void setupListeners() {
        botonCerrar.setOnClickListener(v -> finish());
    }

    // Solicita permiso de camara si hace falta.
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            iniciarCamara();
            return;
        }
        permisoCamaraLauncher.launch(Manifest.permission.CAMERA);
    }

    // Inicia la camara y el analisis de imagen.
    private void iniciarCamara() {
        textoEstado.setText(R.string.scanner_procesando);
        ListenableFuture<ProcessCameraProvider> futuroCamara = ProcessCameraProvider.getInstance(this);
        futuroCamara.addListener(() -> bindCameraUseCases(futuroCamara), ContextCompat.getMainExecutor(this));
    }

    // Une la vista previa con el analisis de codigos.
    private void bindCameraUseCases(ListenableFuture<ProcessCameraProvider> futuroCamara) {
        try {
            ProcessCameraProvider provider = futuroCamara.get();
            provider.unbindAll();

            androidx.camera.core.Preview preview = new androidx.camera.core.Preview.Builder().build();
            preview.setSurfaceProvider(vistaPreviaCamara.getSurfaceProvider());

            ImageAnalysis analisis = new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            analisis.setAnalyzer(executorCamara, this::analyzeImage);

            CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
            provider.bindToLifecycle(this, selector, preview, analisis);
        } catch (Exception error) {
            Toast.makeText(this, R.string.scanner_error_camara, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // Analiza cada frame en busca de un ISBN.
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (resultadoProcesado.get()) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );
        ImageProxy imageProxyActual = imageProxy;

        scannerBarcodes.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String isbnDetectado = normalizeIsbn(barcode.getRawValue());
                        if (isValidIsbnCandidate(isbnDetectado)
                                && resultadoProcesado.compareAndSet(false, true)) {
                            buscarTomoPorIsbn(isbnDetectado);
                            break;
                        }
                    }
                })
                .addOnFailureListener(error -> {
                    // No se interrumpe el escaneo por un frame fallido.
                })
                .addOnCompleteListener(task -> imageProxyActual.close());
    }

    // Busca el tomo asociado al ISBN detectado.
    private void buscarTomoPorIsbn(String isbnDetectado) {
        long isbn = Long.parseLong(isbnDetectado);
        ComicDetailRepository.findVolumeByIsbn(isbn)
                .addOnSuccessListener(tomo -> {
                    if (tomo == null) {
                        showNoResultDialog(isbnDetectado);
                        return;
                    }
                    openVolumeDetail(tomo);
                })
                .addOnFailureListener(error -> {
                    resultadoProcesado.set(false);
                    textoEstado.setText(R.string.scanner_procesando);
                    Toast.makeText(this, R.string.error_busqueda_general, Toast.LENGTH_SHORT).show();
                });
    }

    // Abre la pantalla del tomo encontrado.
    private void openVolumeDetail(VolumeDetailData tomo) {
        Intent pantallaTomo = new Intent(this, VolumeDetailActivity.class);
        pantallaTomo.putExtra(VolumeDetailActivity.EXTRA_COMIC_ID, tomo.comicId);
        pantallaTomo.putExtra(VolumeDetailActivity.EXTRA_VOLUME_ID, tomo.id);
        startActivity(pantallaTomo);
        finish();
    }

    // Muestra el mensaje cuando no existe el ISBN.
    private void showNoResultDialog(String isbnDetectado) {
        runOnUiThread(() -> {
            barraCarga.setVisibility(android.view.View.GONE);
            textoEstado.setText(getString(R.string.scanner_no_resultados));
            new AlertDialog.Builder(this)
                    .setMessage(R.string.scanner_no_resultados)
                    .setCancelable(false)
                    .setNegativeButton(R.string.scanner_volver_inicio, (dialog, which) -> openHome())
                    .setPositiveButton(R.string.scanner_si, (dialog, which) -> openManualCreation(isbnDetectado))
                    .show();
        });
    }

    // Abre el flujo manual con el ISBN detectado.
    private void openManualCreation(String isbnDetectado) {
        Intent pantallaManual = new Intent(this, ManualCreationActivity.class);
        pantallaManual.putExtra(ManualCreationActivity.EXTRA_PRESELECTED_ISBN, isbnDetectado);
        startActivity(pantallaManual);
        finish();
    }

    // Vuelve a la pantalla de inicio.
    private void openHome() {
        Intent pantallaInicio = new Intent(this, MainActivity.class);
        pantallaInicio.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(pantallaInicio);
        finish();
    }

    // Limpia un ISBN y deja solo los numeros.
    private String normalizeIsbn(String valor) {
        if (TextUtils.isEmpty(valor)) {
            return "";
        }
        return valor.replaceAll("[^0-9]", "");
    }

    // Comprueba si el valor parece un ISBN valido para buscar.
    private boolean isValidIsbnCandidate(String isbn) {
        return !TextUtils.isEmpty(isbn) && isbn.length() >= 10 && isbn.length() <= 13;
    }

    // Libera recursos de la camara al cerrar.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorCamara.shutdown();
        if (scannerBarcodes != null) {
            scannerBarcodes.close();
        }
    }
}
