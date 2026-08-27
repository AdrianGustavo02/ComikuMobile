package com.example.comiku.screens;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.comiku.R;
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
        if (!generosOrdenados.isEmpty()) {
            generosSeleccionados.add(generosOrdenados.get(0));
        }
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
        new AlertDialog.Builder(this)
                .setTitle(R.string.creacion_manual_comic_generos)
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
                .setPositiveButton(android.R.string.ok, (dialog, which) -> renderSelectedGenres())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.dismiss())
                .show();
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

        EditText campoAutorExtra = new EditText(this);
        campoAutorExtra.setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        campoAutorExtra.setHint(R.string.creacion_manual_comic_autor_adicional);
        campoAutorExtra.setInputType(InputType.TYPE_CLASS_TEXT);
        campoAutorExtra.setFilters(new InputFilter[]{new InputFilter.LengthFilter(120)});
        campoAutorExtra.setTextColor(campoAutoresComic.getCurrentTextColor());
        campoAutorExtra.setTextSize(16f);
        campoAutorExtra.setPadding(
                campoAutoresComic.getPaddingLeft(),
                campoAutoresComic.getPaddingTop(),
                campoAutoresComic.getPaddingRight(),
                campoAutoresComic.getPaddingBottom()
        );

        contenedorAutoresComic.addView(campoAutorExtra);
        camposAutoresAdicionales.add(campoAutorExtra);
        campoAutorExtra.requestFocus();
    }
}
