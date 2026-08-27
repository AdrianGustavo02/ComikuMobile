package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.comiku.R;
import com.example.comiku.core.image.ImageCropperConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageCropperActivity extends AppCompatActivity {
    private static final int MAX_BITMAP_SIZE = 2048;
    private static final int DEFAULT_MARGIN_DP = 24;
    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 4f;

    private ImageView imagenRecorte;
    private CropOverlayView vistaRecorte;
    private SeekBar barraZoom;
    private TextView textoTitulo;
    private TextView textoError;
    private Button botonDescartar;
    private Button botonConfirmar;

    private Bitmap bitmapOriginal;
    private Matrix matrizImagen = new Matrix();
    private float escalaBase = 1f;
    private float escalaActual = 1f;
    private final PointF ultimoPunto = new PointF();
    private int modoArrastre = 0;
    private ScaleGestureDetector detectorEscala;

    private Uri uriEntrada;
    private String nombreSalida = "imagen-recortada.jpg";
    private String tituloPantalla = "Recortar imagen";
    private int aspectoX = 1;
    private int aspectoY = 1;

    private final View.OnTouchListener listenerImagen = (vista, event) -> {
        if (bitmapOriginal == null) {
            return false;
        }

        if (detectorEscala != null) {
            detectorEscala.onTouchEvent(event);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                ultimoPunto.set(event.getX(), event.getY());
                modoArrastre = 1;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (modoArrastre == 1 && !detectorEscala.isInProgress()) {
                    float dx = event.getX() - ultimoPunto.x;
                    float dy = event.getY() - ultimoPunto.y;
                    matrizImagen.postTranslate(dx, dy);
                    limitarTransformacion();
                    imagenRecorte.setImageMatrix(matrizImagen);
                    ultimoPunto.set(event.getX(), event.getY());
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                modoArrastre = 0;
                return true;
            default:
                return false;
        }
    };

    // Inicializa el recortador
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readExtras();
        setContentView(R.layout.activity_image_cropper);
        bindViews();
        setupListeners();
        loadBitmap();
    }

    // Lee los datos que abren el recortador.
    private void readExtras() {
        Intent intent = getIntent();
        String inputValue = intent.getStringExtra(ImageCropperConfig.EXTRA_INPUT_URI);
        if (!TextUtils.isEmpty(inputValue)) {
            uriEntrada = Uri.parse(inputValue);
        }
        nombreSalida = intent.getStringExtra(ImageCropperConfig.EXTRA_OUTPUT_FILENAME);
        if (TextUtils.isEmpty(nombreSalida)) {
            nombreSalida = "imagen-recortada.jpg";
        }
        String titulo = intent.getStringExtra(ImageCropperConfig.EXTRA_TITLE);
        if (!TextUtils.isEmpty(titulo)) {
            tituloPantalla = titulo;
        }
        aspectoX = Math.max(1, intent.getIntExtra(ImageCropperConfig.EXTRA_ASPECT_X, 1));
        aspectoY = Math.max(1, intent.getIntExtra(ImageCropperConfig.EXTRA_ASPECT_Y, 1));
    }

    // Vincula las vistas del recortador.
    private void bindViews() {
        textoTitulo = findViewById(R.id.textoTituloRecorte);
        imagenRecorte = findViewById(R.id.imagenRecorte);
        vistaRecorte = findViewById(R.id.vistaRecorte);
        barraZoom = findViewById(R.id.barraZoomRecorte);
        textoError = findViewById(R.id.textoErrorRecorte);
        botonDescartar = findViewById(R.id.botonDescartarRecorte);
        botonConfirmar = findViewById(R.id.botonConfirmarRecorte);

        textoTitulo.setText(tituloPantalla);
        vistaRecorte.setAspectRatio(aspectoX, aspectoY);
    }

    // Conecta acciones del recortador.
    private void setupListeners() {
        botonDescartar.setOnClickListener(v -> finishWithCancel());
        botonConfirmar.setOnClickListener(v -> confirmCrop());

        barraZoom.setMax(300);
        barraZoom.setProgress(0);
        barraZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (bitmapOriginal == null) {
                    return;
                }
                float zoom = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * (progress / 300f);
                applyScale(zoom);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        detectorEscala = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float nextScale = escalaActual * detector.getScaleFactor();
                applyScale(nextScale);
                return true;
            }
        });

        imagenRecorte.setOnTouchListener(listenerImagen);
    }

    // Carga la imagen seleccionada por el usuario.
    private void loadBitmap() {
        if (uriEntrada == null) {
            showError(getString(R.string.error_recorte_imagen));
            botonConfirmar.setEnabled(false);
            return;
        }

        try {
            bitmapOriginal = decodeSampledBitmap(uriEntrada);
            if (bitmapOriginal == null) {
                showError(getString(R.string.error_recorte_imagen));
                botonConfirmar.setEnabled(false);
                return;
            }

            imagenRecorte.setImageBitmap(bitmapOriginal);
            imagenRecorte.post(this::setupInitialMatrix);
        } catch (IOException error) {
            showError(getString(R.string.error_recorte_imagen));
            botonConfirmar.setEnabled(false);
        }
    }

    // Configura el encuadre inicial de la imagen.
    private void setupInitialMatrix() {
        if (bitmapOriginal == null || imagenRecorte.getWidth() == 0 || imagenRecorte.getHeight() == 0) {
            return;
        }

        RectF cropRect = vistaRecorte.getCropRect();
        if (cropRect == null) {
            return;
        }

        float scaleX = cropRect.width() / bitmapOriginal.getWidth();
        float scaleY = cropRect.height() / bitmapOriginal.getHeight();
        escalaBase = Math.max(scaleX, scaleY);
        escalaActual = escalaBase;

        matrizImagen.reset();
        matrizImagen.postScale(escalaBase, escalaBase);

        float imageWidth = bitmapOriginal.getWidth() * escalaBase;
        float imageHeight = bitmapOriginal.getHeight() * escalaBase;
        float dx = cropRect.centerX() - (imageWidth / 2f);
        float dy = cropRect.centerY() - (imageHeight / 2f);
        matrizImagen.postTranslate(dx, dy);

        imagenRecorte.setImageMatrix(matrizImagen);
        barraZoom.setProgress(0);
        limitarTransformacion();
        imagenRecorte.invalidate();
    }

    // Aplica un nuevo nivel de zoom.
    private void applyScale(float nextScale) {
        if (bitmapOriginal == null) {
            return;
        }

        float limitScale = Math.max(escalaBase, Math.min(nextScale, escalaBase * MAX_SCALE));
        float factor = limitScale / escalaActual;
        RectF cropRect = vistaRecorte.getCropRect();
        if (cropRect == null) {
            return;
        }

        matrizImagen.postScale(factor, factor, cropRect.centerX(), cropRect.centerY());
        escalaActual = limitScale;
        int progress = Math.round(((escalaActual / escalaBase) - 1f) / (MAX_SCALE - 1f) * 300f);
        barraZoom.setOnSeekBarChangeListener(null);
        barraZoom.setProgress(Math.max(0, Math.min(progress, 300)));
        barraZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (bitmapOriginal == null) {
                    return;
                }
                float zoom = escalaBase + (escalaBase * (MAX_SCALE - 1f)) * (progress / 300f);
                applyScale(zoom);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        limitarTransformacion();
        imagenRecorte.setImageMatrix(matrizImagen);
    }

    // Corrige la posicion para que el recorte siga dentro de la imagen.
    private void limitarTransformacion() {
        if (bitmapOriginal == null) {
            return;
        }

        RectF cropRect = vistaRecorte.getCropRect();
        if (cropRect == null) {
            return;
        }

        RectF displayRect = new RectF(0, 0, bitmapOriginal.getWidth(), bitmapOriginal.getHeight());
        matrizImagen.mapRect(displayRect);

        float ajusteX = 0f;
        float ajusteY = 0f;

        if (displayRect.left > cropRect.left) {
            ajusteX = cropRect.left - displayRect.left;
        } else if (displayRect.right < cropRect.right) {
            ajusteX = cropRect.right - displayRect.right;
        }

        if (displayRect.top > cropRect.top) {
            ajusteY = cropRect.top - displayRect.top;
        } else if (displayRect.bottom < cropRect.bottom) {
            ajusteY = cropRect.bottom - displayRect.bottom;
        }

        matrizImagen.postTranslate(ajusteX, ajusteY);
    }

    // Confirma el recorte y devuelve la imagen.
    private void confirmCrop() {
        if (bitmapOriginal == null) {
            showError(getString(R.string.error_recorte_imagen));
            return;
        }

        try {
            RectF cropRect = vistaRecorte.getCropRect();
            if (cropRect == null) {
                showError(getString(R.string.error_recorte_imagen));
                return;
            }

            Matrix inverse = new Matrix();
            matrizImagen.invert(inverse);
            float[] puntos = new float[] {
                    cropRect.left, cropRect.top,
                    cropRect.right, cropRect.top,
                    cropRect.right, cropRect.bottom,
                    cropRect.left, cropRect.bottom
            };
            inverse.mapPoints(puntos);

            float minX = puntos[0];
            float maxX = puntos[0];
            float minY = puntos[1];
            float maxY = puntos[1];
            for (int i = 2; i < puntos.length; i += 2) {
                minX = Math.min(minX, puntos[i]);
                maxX = Math.max(maxX, puntos[i]);
                minY = Math.min(minY, puntos[i + 1]);
                maxY = Math.max(maxY, puntos[i + 1]);
            }

            int x = Math.max(0, Math.round(minX));
            int y = Math.max(0, Math.round(minY));
            int width = Math.min(bitmapOriginal.getWidth() - x, Math.round(maxX - minX));
            int height = Math.min(bitmapOriginal.getHeight() - y, Math.round(maxY - minY));

            if (width <= 0 || height <= 0) {
                showError(getString(R.string.error_recorte_imagen));
                return;
            }

            Bitmap recortada = Bitmap.createBitmap(bitmapOriginal, x, y, width, height);
            File archivoSalida = new File(getCacheDir(), nombreSalida);
            FileOutputStream salida = new FileOutputStream(archivoSalida);
            try {
                recortada.compress(Bitmap.CompressFormat.JPEG, 92, salida);
            } finally {
                salida.close();
            }

            Intent resultado = new Intent();
            resultado.putExtra(ImageCropperConfig.EXTRA_RESULT_URI, Uri.fromFile(archivoSalida).toString());
            setResult(RESULT_OK, resultado);
            finish();
        } catch (Exception error) {
            showError(getString(R.string.error_recorte_imagen));
        }
    }

    // Cancela el recorte y vuelve atras.
    private void finishWithCancel() {
        setResult(RESULT_CANCELED);
        finish();
    }

    // Lee una imagen grande sin cargarla completa de golpe.
    private Bitmap decodeSampledBitmap(Uri uri) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        InputStream streamBounds = getContentResolver().openInputStream(uri);
        if (streamBounds == null) {
            return null;
        }
        try {
            BitmapFactory.decodeStream(streamBounds, null, options);
        } finally {
            streamBounds.close();
        }

        options.inSampleSize = calculateInSampleSize(options, MAX_BITMAP_SIZE, MAX_BITMAP_SIZE);
        options.inJustDecodeBounds = false;

        InputStream streamReal = getContentResolver().openInputStream(uri);
        if (streamReal == null) {
            return null;
        }

        try {
            return BitmapFactory.decodeStream(streamReal, null, options);
        } finally {
            streamReal.close();
        }
    }

    // Calcula cuanto reducir una imagen para cargarla mejor.
    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return Math.max(inSampleSize, 1);
    }

    // Muestra un error simple en pantalla.
    private void showError(String mensaje) {
        textoError.setText(mensaje);
    }

}
