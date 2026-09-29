package com.example.comiku.screens;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.core.ui.GenericFormContainerComponent;
import com.example.comiku.core.validation.InputValidator;
import com.example.comiku.data.constants.GenerosComic;
import com.example.comiku.data.constants.PaisesEdicion;
import com.example.comiku.data.model.ComicDraftData;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.auth.FirebaseAuth;

import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout.LayoutParams;
import android.text.InputFilter;
import android.text.InputType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.text.Collator;
import java.util.Locale;

public class ManualCreationActivity extends BaseDrawerActivity {
    public static final String EXTRA_COMIC_DRAFT = "extra_comic_draft";
    public static final String EXTRA_PRESELECTED_ISBN = "extra_preselected_isbn";

    private EditText campoNombreComic;
    private EditText campoAutoresComic;
    private Button botonAgregarAutorComic;
    private LinearLayout contenedorAutoresComic;
    private EditText campoEditorialComic;
    private Spinner spinnerPaisEditorialComic;
    private Spinner spinnerEstadoComic;
    private EditText campoFormatoComic;
    private TextView textoGenerosComic;
    private Button botonGenerosComic;
    private EditText campoDescripcionComic;
    private TextView textoError;
    private ProgressBar barraCarga;
    private Button botonContinuar;
    private boolean estaGuardando = false;
    private String isbnPreseleccionado;
    private final List<String> generosSeleccionados = new ArrayList<>();
    private List<String> generosOrdenados = new ArrayList<>();
    private final List<EditText> camposAutoresAdicionales = new ArrayList<>();
    private final List<Button> botonesQuitarAutoresAdicionales = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            openLoginAndClearStack();
            return;
        }

        setupDrawerShell(getString(R.string.creacion_manual_comic_titulo));
    }


    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_manual_creation_comic;
    }


    @Override
    protected void onScreenContentReady() {
        GenericFormContainerComponent.inflateFormContent(
                findViewById(android.R.id.content),
                R.layout.view_form_create_comic_content
        );
        bindViews();
        applyPreselectedIsbn();
        setupEstadoSpinner();
        setupGenresSelector();
        setupListeners();
    }

    // Aplica un ISBN recibido desde el escaner si existe.
    private void applyPreselectedIsbn() {
        isbnPreseleccionado = getIntent().getStringExtra(EXTRA_PRESELECTED_ISBN);
        if (!TextUtils.isEmpty(isbnPreseleccionado)) {
            // El ISBN se reutiliza luego en la pantalla de tomos.
        }
    }


    private void bindViews() {
        campoNombreComic = findViewById(R.id.campoNombreComicManual);
        campoAutoresComic = findViewById(R.id.campoAutoresComicManual);
        botonAgregarAutorComic = findViewById(R.id.botonAgregarAutorComicManual);
        contenedorAutoresComic = findViewById(R.id.contenedorAutoresComicManual);
        campoEditorialComic = findViewById(R.id.campoEditorialComicManual);
        spinnerPaisEditorialComic = findViewById(R.id.spinnerPaisEditorialComicManual);
        spinnerEstadoComic = findViewById(R.id.spinnerEstadoComicManual);
        campoFormatoComic = findViewById(R.id.campoFormatoComicManual);
        textoGenerosComic = findViewById(R.id.textoGenerosComicManual);
        botonGenerosComic = findViewById(R.id.botonGenerosComicManual);
        campoDescripcionComic = findViewById(R.id.campoDescripcionComicManual);
        textoError = findViewById(R.id.textoErrorCreacionManualComic);
        barraCarga = findViewById(R.id.barraCargaCreacionManualComic);
        botonContinuar = findViewById(R.id.botonContinuarCreacionManualComic);
    }

    // Carga opciones de estado para el comic.
    private void setupEstadoSpinner() {
        String[] opcionesEstado = new String[]{
                getString(R.string.creacion_manual_estado_en_curso),
                getString(R.string.creacion_manual_estado_finalizado)
        };
        ArrayAdapter<String> adaptadorEstado = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                opcionesEstado
        );
        adaptadorEstado.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerEstadoComic.setAdapter(adaptadorEstado);

        // Ordena los paises al momento de usarlos
        List<String> paisesOrdenados = new ArrayList<>(Arrays.asList(PaisesEdicion.LISTA));
        Collator comparador = Collator.getInstance(new Locale("es", "ES"));
        comparador.setStrength(Collator.PRIMARY);
        Collections.sort(paisesOrdenados, comparador);

        ArrayAdapter<String> adaptadorPaises = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                paisesOrdenados
        );
        adaptadorPaises.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaisEditorialComic.setAdapter(adaptadorPaises);
    }

    // Configura el selector de generos con orden alfabetico.
    private void setupGenresSelector() {
        generosOrdenados = new ArrayList<>(Arrays.asList(GenerosComic.LISTA));
        Collator comparador = Collator.getInstance(new Locale("es", "ES"));
        comparador.setStrength(Collator.PRIMARY);
        Collections.sort(generosOrdenados, comparador);

        generosSeleccionados.clear();
        renderSelectedGenres();
    }

    // Conecta eventos del formulario.
    private void setupListeners() {
        botonContinuar.setOnClickListener(v -> openVolumeActivity());
        botonGenerosComic.setOnClickListener(v -> openGenresDialog());
        botonAgregarAutorComic.setOnClickListener(v -> addAuthorField());
    }

    // Valida datos y abre la pantalla de tomos.
    private void openVolumeActivity() {
        if (estaGuardando) {
            return;
        }

        textoError.setText("");
        String errorComic = validateComicForm();
        if (!TextUtils.isEmpty(errorComic)) {
            textoError.setText(errorComic);
            return;
        }

        ComicDraftData borradorComic = buildComicDraft();
        if (borradorComic == null) {
            textoError.setText(getString(R.string.creacion_manual_error_comic_invalido));
            return;
        }

        setSavingState(true);
        Intent pantallaTomos = new Intent(this, ManualVolumeCreationActivity.class);
        pantallaTomos.putExtra(EXTRA_COMIC_DRAFT, borradorComic);
        if (!TextUtils.isEmpty(isbnPreseleccionado)) {
            pantallaTomos.putExtra(EXTRA_PRESELECTED_ISBN, isbnPreseleccionado);
        }
        startActivity(pantallaTomos);
        setSavingState(false);
    }

    // Arma el borrador de comic desde el formulario.
    private ComicDraftData buildComicDraft() {
        List<String> autores = collectAuthors();
        List<String> generos = new ArrayList<>(generosSeleccionados);
        if (autores.isEmpty() || generos.isEmpty()) {
            return null;
        }

        return new ComicDraftData(
                InputValidator.sanitizeForbiddenChars(campoNombreComic.getText().toString().trim()),
                autores,
                InputValidator.sanitizeForbiddenChars(campoEditorialComic.getText().toString().trim()),
                String.valueOf(spinnerPaisEditorialComic.getSelectedItem()),
                spinnerEstadoComic.getSelectedItem().toString(),
                InputValidator.sanitizeForbiddenChars(campoFormatoComic.getText().toString().trim()),
                generos,
                InputValidator.sanitizeForbiddenChars(campoDescripcionComic.getText().toString().trim())
        );
    }

    // Valida los campos del comic.
    private String validateComicForm() {
        String nombre = campoNombreComic.getText().toString().trim();
        String editorial = campoEditorialComic.getText().toString().trim();
        String formato = campoFormatoComic.getText().toString().trim();
        String descripcion = campoDescripcionComic.getText().toString().trim();

        if (TextUtils.isEmpty(nombre)
                || TextUtils.isEmpty(editorial)
                || TextUtils.isEmpty(formato)
                || TextUtils.isEmpty(descripcion)
                || collectAuthors().isEmpty()
                || generosSeleccionados.isEmpty()) {
            return getString(R.string.creacion_manual_error_comic_requerido);
        }

        if (InputValidator.hasForbiddenChars(nombre)
                || InputValidator.hasForbiddenChars(editorial)
                || InputValidator.hasForbiddenChars(formato)
                || InputValidator.hasForbiddenChars(descripcion)
                || hasForbiddenCharsInAuthors()) {
            return getString(R.string.creacion_manual_error_caracteres);
        }

        if (generosSeleccionados.isEmpty()) {
            return getString(R.string.creacion_manual_error_generos);
        }

        return "";
    }

    // Recolecta todos los autores escritos en la pantalla.
    private List<String> collectAuthors() {
        List<String> autores = new ArrayList<>();
        addAuthorIfValid(autores, campoAutoresComic.getText().toString());
        for (EditText campoAutorExtra : camposAutoresAdicionales) {
            addAuthorIfValid(autores, campoAutorExtra.getText().toString());
        }
        return autores;
    }

    // Agrega un autor a la lista si tiene contenido valido.
    private void addAuthorIfValid(List<String> autores, String textoAutor) {
        String autorLimpio = InputValidator.sanitizeForbiddenChars(textoAutor).trim();
        if (!TextUtils.isEmpty(autorLimpio)) {
            autores.add(autorLimpio);
        }
    }

    // Revisa si algun campo de autor tiene caracteres no permitidos.
    private boolean hasForbiddenCharsInAuthors() {
        List<String> valoresAutores = new ArrayList<>();
        valoresAutores.add(campoAutoresComic.getText().toString());
        for (EditText campoAutorExtra : camposAutoresAdicionales) {
            valoresAutores.add(campoAutorExtra.getText().toString());
        }
        return InputValidator.hasForbiddenCharsInList(valoresAutores);
    }

    // Cambia estado de carga de la pantalla.
    private void setSavingState(boolean guardando) {
        estaGuardando = guardando;
        barraCarga.setVisibility(guardando ? android.view.View.VISIBLE : android.view.View.GONE);
        campoNombreComic.setEnabled(!guardando);
        campoAutoresComic.setEnabled(!guardando);
        campoEditorialComic.setEnabled(!guardando);
        spinnerPaisEditorialComic.setEnabled(!guardando);
        spinnerEstadoComic.setEnabled(!guardando);
        campoFormatoComic.setEnabled(!guardando);
        botonGenerosComic.setEnabled(!guardando);
        botonAgregarAutorComic.setEnabled(!guardando);
        campoDescripcionComic.setEnabled(!guardando);
        botonContinuar.setEnabled(!guardando);
        for (EditText campoAutorExtra : camposAutoresAdicionales) {
            campoAutorExtra.setEnabled(!guardando);
        }
        for (Button botonQuitarAutor : botonesQuitarAutoresAdicionales) {
            botonQuitarAutor.setEnabled(!guardando);
        }
    }

    // Abre un selector multiple para elegir generos.
    private void openGenresDialog() {
        if (estaGuardando || generosOrdenados.isEmpty()) {
            return;
        }

        boolean[] seleccionActual = new boolean[generosOrdenados.size()];
        for (int i = 0; i < generosOrdenados.size(); i++) {
            seleccionActual[i] = generosSeleccionados.contains(generosOrdenados.get(i));
        }

        CharSequence[] items = generosOrdenados.toArray(new CharSequence[0]);
        AlertDialog dialogoGeneros = new AlertDialog.Builder(this)
                .setTitle(R.string.creacion_manual_comic_generos_selector_titulo)
                .setMultiChoiceItems(items, seleccionActual, (dialog, which, isChecked) -> {
                    String genero = generosOrdenados.get(which);
                    if (isChecked) {
                        if (!generosSeleccionados.contains(genero)) {
                            generosSeleccionados.add(genero);
                        }
                    } else {
                        generosSeleccionados.remove(genero);
                    }
                })
                .setPositiveButton(R.string.amigos_solicitud_aceptar, (dialog, which) -> renderSelectedGenres())
                .setNegativeButton(R.string.reporte_boton_cancelar, (dialog, which) -> dialog.dismiss())
                .create();

        dialogoGeneros.setOnShowListener(dialogInterface -> {
            Button botonAceptar = dialogoGeneros.getButton(AlertDialog.BUTTON_POSITIVE);
            Button botonCancelar = dialogoGeneros.getButton(AlertDialog.BUTTON_NEGATIVE);

            if (botonAceptar != null) {
                applyDialogButtonStyle(
                        botonAceptar,
                        R.drawable.bg_button_primary_action,
                        getColorStateList(R.color.button_primary_action_text)
                );
            }

            if (botonCancelar != null) {
                applyDialogButtonStyle(
                        botonCancelar,
                        R.drawable.bg_button_danger,
                        getColorStateList(R.color.button_danger_text)
                );
            }

            applyDialogButtonsSpacing(botonCancelar, botonAceptar);
        });

        dialogoGeneros.show();
        if (dialogoGeneros.getWindow() != null) {
            dialogoGeneros.getWindow().setBackgroundDrawableResource(R.drawable.bg_report_dialog_rounded);
        }
    }

    // Muestra los generos elegidos en pantalla.
    private void renderSelectedGenres() {
        if (generosSeleccionados.isEmpty()) {
            textoGenerosComic.setText(getString(R.string.creacion_manual_comic_generos));
            return;
        }
        textoGenerosComic.setText(String.join(", ", generosSeleccionados));
    }

    // Agrega un nuevo campo para escribir otro autor.
    private void addAuthorField() {
        if (estaGuardando) {
            return;
        }

        LinearLayout filaAutor = new LinearLayout(this);
        filaAutor.setOrientation(LinearLayout.HORIZONTAL);
        LayoutParams paramsFila = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsFila.topMargin = dpToPx(8);
        filaAutor.setLayoutParams(paramsFila);

        EditText campoAutorExtra = new EditText(this);
        LayoutParams paramsCampo = new LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsCampo.weight = 1f;
        paramsCampo.setMarginEnd(dpToPx(8));
        campoAutorExtra.setLayoutParams(paramsCampo);
        campoAutorExtra.setHint(R.string.creacion_manual_comic_hint_autor);
        campoAutorExtra.setInputType(InputType.TYPE_CLASS_TEXT);
        campoAutorExtra.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        campoAutorExtra.setBackgroundResource(R.drawable.bg_input_text_generic);
        campoAutorExtra.setBackgroundTintList(null);
        campoAutorExtra.setTextColor(campoAutoresComic.getCurrentTextColor());
        campoAutorExtra.setHintTextColor(campoAutoresComic.getHintTextColors());
        campoAutorExtra.setTextSize(TypedValue.COMPLEX_UNIT_PX, campoAutoresComic.getTextSize());
        campoAutorExtra.setMinHeight(campoAutoresComic.getMinHeight());
        campoAutorExtra.setPadding(
                campoAutoresComic.getPaddingLeft(),
                campoAutoresComic.getPaddingTop(),
                campoAutoresComic.getPaddingRight(),
                campoAutoresComic.getPaddingBottom()
        );

        ContextThemeWrapper contextoDanger = new ContextThemeWrapper(this, R.style.Theme_Comiku_DangerButton);
        Button botonQuitarAutor = new Button(contextoDanger);
        botonQuitarAutor.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        botonQuitarAutor.setText(R.string.crear_lista_quitar);
        botonQuitarAutor.setAllCaps(false);
        botonQuitarAutor.setBackgroundResource(R.drawable.bg_button_danger);
        botonQuitarAutor.setBackgroundTintList(null);
        botonQuitarAutor.setTextColor(getColorStateList(R.color.button_danger_text));
        botonQuitarAutor.setTypeface(null, Typeface.BOLD);
        botonQuitarAutor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        botonQuitarAutor.setPadding(dpToPx(16), dpToPx(11), dpToPx(16), dpToPx(11));
        botonQuitarAutor.setMinHeight(0);
        botonQuitarAutor.setOnClickListener(v -> removeAuthorField(filaAutor, campoAutorExtra, botonQuitarAutor));

        filaAutor.addView(campoAutorExtra);
        filaAutor.addView(botonQuitarAutor);
        contenedorAutoresComic.addView(filaAutor);
        camposAutoresAdicionales.add(campoAutorExtra);
        botonesQuitarAutoresAdicionales.add(botonQuitarAutor);
        campoAutorExtra.requestFocus();
    }

    // Quita un campo adicional de autor y su boton asociado.
    private void removeAuthorField(LinearLayout filaAutor, EditText campoAutorExtra, Button botonQuitarAutor) {
        contenedorAutoresComic.removeView(filaAutor);
        camposAutoresAdicionales.remove(campoAutorExtra);
        botonesQuitarAutoresAdicionales.remove(botonQuitarAutor);
    }

    // Aplica el estilo base de botones en el dialogo de generos.
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

    // Separa los botones del dialogo para mejorar la lectura visual.
    private void applyDialogButtonsSpacing(Button botonCancelar, Button botonAceptar) {
        applyDialogButtonMargin(botonCancelar, 0, 8);
        applyDialogButtonMargin(botonAceptar, 8, 0);
    }

    // Agrega margen lateral a cada boton si el contenedor lo permite.
    private void applyDialogButtonMargin(Button boton, int margenInicioDp, int margenFinDp) {
        if (boton == null) {
            return;
        }

        ViewGroup.LayoutParams params = boton.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        ViewGroup.MarginLayoutParams paramsMargen = (ViewGroup.MarginLayoutParams) params;
        paramsMargen.setMarginStart(dpToPx(margenInicioDp));
        paramsMargen.setMarginEnd(dpToPx(margenFinDp));
        boton.setLayoutParams(paramsMargen);
    }

    // Convierte dp a pixeles para mantener el espaciado visual.
    private int dpToPx(int valorDp) {
        float densidad = getResources().getDisplayMetrics().density;
        return Math.round(valorDp * densidad);
    }
}
