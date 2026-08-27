package com.example.comiku.screens;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.comiku.R;
import com.example.comiku.core.ui.ThematicListUiHelper;
import com.example.comiku.core.validation.InputValidator;
import com.example.comiku.data.model.ThematicListData;
import com.example.comiku.data.repository.FriendshipRepository;
import com.example.comiku.data.repository.ThematicListRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

public class ThematicListsActivity extends BaseDrawerActivity {

    public static final int TAMANO_PAGINA = 15;
    private static final int REQUEST_LISTA = 100;
    private static final int REQUEST_MIS_LISTAS = 101;

    private android.widget.ProgressBar barraCarga;
    private LinearLayout contenedorListas;
    private Button botonCargarMas;
    private TextView textoNoMas;
    private TextView textoEstado;
    private EditText barraBusqueda;
    private Spinner spinnerFiltro;

    private List<ThematicListData> todasLasListas = new ArrayList<>();
    private List<ThematicListData> listasFiltradas = new ArrayList<>();
    private int paginaActual;
    private int indiceFiltro;
    private String textoBusqueda = "";
    private boolean mostrandoGuardadas;
    private boolean spinnerListo;

    // Inicia la pantalla con el componente de navbar.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            openLoginAndClearStack();
            return;
        }
        setupDrawerShell(getString(R.string.listas_tematicas_titulo));
    }

    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_thematic_lists;
    }


    @Override
    protected void onScreenContentReady() {
        barraCarga = findViewById(R.id.barraCargaListasTematicas);
        contenedorListas = findViewById(R.id.contenedorListasTematicas);
        botonCargarMas = findViewById(R.id.botonCargarMasListasTematicas);
        textoNoMas = findViewById(R.id.textoNoMasListasTematicas);
        textoEstado = findViewById(R.id.textoEstadoListasTematicas);
        barraBusqueda = findViewById(R.id.barraBusquedaListasTematicas);
        spinnerFiltro = findViewById(R.id.spinnerFiltroListasTematicas);

        Button botonCrear = findViewById(R.id.botonCrearListaTematica);
        Button botonMisListas = findViewById(R.id.botonMisListasTematicas);

        botonCrear.setOnClickListener(v -> {
            Intent intentoCrear = new Intent(this, CreateThematicListActivity.class);
            startActivityForResult(intentoCrear, REQUEST_LISTA);
        });

        botonMisListas.setOnClickListener(v -> {
            Intent intentoMisListas = new Intent(this, MyThematicListsActivity.class);
            startActivityForResult(intentoMisListas, REQUEST_MIS_LISTAS);
        });

        setupSpinnerFiltro();
        setupSearchInput();

        botonCargarMas.setOnClickListener(v -> {
            paginaActual++;
            mostrarPagina();
        });

        loadLists();
    }

    // Configura los filtros de las listas tematicas
    private void setupSpinnerFiltro() {
        String[] opciones = {
                getString(R.string.listas_tematicas_filtro_populares),
                getString(R.string.listas_tematicas_filtro_recientes),
                getString(R.string.listas_tematicas_filtro_guia),
                getString(R.string.listas_tematicas_filtro_guardadas)
        };
        ArrayAdapter<String> adaptador = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, opciones);
        adaptador.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFiltro.setAdapter(adaptador);
        spinnerListo = true;

        spinnerFiltro.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                indiceFiltro = position;
                paginaActual = 0;
                if (position == 3) {
                    mostrandoGuardadas = true;
                    loadSavedLists();
                    return;
                }
                if (mostrandoGuardadas) {
                    mostrandoGuardadas = false;
                    loadLists();
                    return;
                }
                aplicarFiltroYBusqueda();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                indiceFiltro = 0;
            }
        });
    }

    // Configura la barra de busqueda con limpieza de caracteres.
    private void setupSearchInput() {
        barraBusqueda.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String textoLimpio = InputValidator.sanitizeForbiddenChars(String.valueOf(s));
                if (!TextUtils.equals(textoLimpio, String.valueOf(s))) {
                    barraBusqueda.removeTextChangedListener(this);
                    barraBusqueda.setText(textoLimpio);
                    barraBusqueda.setSelection(textoLimpio.length());
                    barraBusqueda.addTextChangedListener(this);
                }
                textoBusqueda = textoLimpio;
                paginaActual = 0;
                aplicarFiltroYBusqueda();
            }
        });
    }

    // Carga todas las listas tematicas desde Firestore.
    private void loadLists() {
        barraCarga.setVisibility(View.VISIBLE);
        textoEstado.setVisibility(View.GONE);
        botonCargarMas.setVisibility(View.GONE);
        textoNoMas.setVisibility(View.GONE);

        ThematicListRepository.getAllThematicLists()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw new IllegalStateException(getString(R.string.listas_tematicas_sin_listas));
                    }
                    return filterVisibleLists(task.getResult());
                })
                .addOnSuccessListener(listas -> {
                    barraCarga.setVisibility(View.GONE);
                    todasLasListas = listas != null ? listas : new ArrayList<>();
                    paginaActual = 0;
                    aplicarFiltroYBusqueda();
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    contenedorListas.removeAllViews();
                    textoEstado.setText(getString(R.string.listas_tematicas_sin_listas));
                    textoEstado.setVisibility(View.VISIBLE);
                });
    }

    // Carga las listas guardadas por el usuario.
    private void loadSavedLists() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            textoEstado.setText(getString(R.string.listas_tematicas_vacia));
            textoEstado.setVisibility(View.VISIBLE);
            return;
        }

        barraCarga.setVisibility(View.VISIBLE);
        textoEstado.setVisibility(View.GONE);
        botonCargarMas.setVisibility(View.GONE);
        textoNoMas.setVisibility(View.GONE);

        ThematicListRepository.getUserSavedThematicLists(usuario.getUid())
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        throw new IllegalStateException(getString(R.string.listas_tematicas_vacia));
                    }
                    return filterVisibleLists(task.getResult());
                })
                .addOnSuccessListener(listas -> {
                    barraCarga.setVisibility(View.GONE);
                    todasLasListas = listas != null ? listas : new ArrayList<>();
                    paginaActual = 0;
                    aplicarFiltroYBusqueda();
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    contenedorListas.removeAllViews();
                    textoEstado.setText(getString(R.string.listas_tematicas_vacia));
                    textoEstado.setVisibility(View.VISIBLE);
                });
    }

    // Filtra listas creadas por usuarios que no pueden ser visibles.
    private Task<List<ThematicListData>> filterVisibleLists(List<ThematicListData> listas) {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            return Tasks.forResult(listas != null ? listas : new ArrayList<>());
        }

        Task<List<String>> tareaBloqueados = FriendshipRepository.getBlockedUserIds(usuario.getUid());
        Task<List<String>> tareaBloqueadores = FriendshipRepository.getUsersWhoBlockedUserIds(usuario.getUid());

        return Tasks.whenAllSuccess(tareaBloqueados, tareaBloqueadores)
                .continueWith(task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().size() < 2) {
                        throw new IllegalStateException(getString(R.string.listas_tematicas_sin_listas));
                    }

                    List<String> bloqueados = task.getResult().get(0) instanceof List
                            ? (List<String>) task.getResult().get(0)
                            : new ArrayList<>();
                    List<String> bloqueadores = task.getResult().get(1) instanceof List
                            ? (List<String>) task.getResult().get(1)
                            : new ArrayList<>();

                    Set<String> bloqueos = new HashSet<>();
                    bloqueos.addAll(bloqueados);
                    bloqueos.addAll(bloqueadores);

                    List<ThematicListData> visibles = new ArrayList<>();
                    for (ThematicListData lista : listas) {
                        if (lista == null || TextUtils.isEmpty(lista.userId) || !bloqueos.contains(lista.userId)) {
                            visibles.add(lista);
                        }
                    }
                    return visibles;
                });
    }

    // Filtra y busca sobre las listas ya cargadas.
    private void aplicarFiltroYBusqueda() {
        if (!spinnerListo) {
            return;
        }

        List<ThematicListData> listasOrdenadas = new ArrayList<>(todasLasListas);

        if (indiceFiltro == 2) {
            List<ThematicListData> listasGuia = new ArrayList<>();
            for (ThematicListData lista : listasOrdenadas) {
                if (lista.esGuiaDeLectura) {
                    listasGuia.add(lista);
                }
            }
            listasOrdenadas = listasGuia;
        }

        if (indiceFiltro == 0 || indiceFiltro == 2 || indiceFiltro == 3) {
            listasOrdenadas.sort((listaA, listaB) -> {
                long scoreA = listaA.cantidadLikes * 2L + listaA.cantidadComentarios;
                long scoreB = listaB.cantidadLikes * 2L + listaB.cantidadComentarios;
                if (scoreB != scoreA) {
                    return Long.compare(scoreB, scoreA);
                }
                long fechaA = listaA.fechaCreacion != null ? listaA.fechaCreacion.getTime() : 0L;
                long fechaB = listaB.fechaCreacion != null ? listaB.fechaCreacion.getTime() : 0L;
                if (fechaB != fechaA) {
                    return Long.compare(fechaB, fechaA);
                }
                return listaA.nombre.compareToIgnoreCase(listaB.nombre);
            });
        } else if (indiceFiltro == 1) {
            listasOrdenadas.sort((listaA, listaB) -> {
                long fechaA = listaA.fechaCreacion != null ? listaA.fechaCreacion.getTime() : 0L;
                long fechaB = listaB.fechaCreacion != null ? listaB.fechaCreacion.getTime() : 0L;
                return Long.compare(fechaB, fechaA);
            });
        }

        if (!textoBusqueda.isEmpty()) {
            String query = textoBusqueda.toLowerCase(Locale.ROOT);
            List<ThematicListData> listasBuscadas = new ArrayList<>();
            for (ThematicListData lista : listasOrdenadas) {
                if (lista.nombre.toLowerCase(Locale.ROOT).contains(query)) {
                    listasBuscadas.add(lista);
                }
            }
            listasOrdenadas = listasBuscadas;
        }

        listasFiltradas = listasOrdenadas;
        mostrarPagina();
    }

    // Muestra la pagina actual de listas en pantalla.
    private void mostrarPagina() {
        if (paginaActual == 0) {
            contenedorListas.removeAllViews();
        }

        if (listasFiltradas.isEmpty()) {
            textoEstado.setText(getTextoVacio());
            textoEstado.setVisibility(View.VISIBLE);
            botonCargarMas.setVisibility(View.GONE);
            textoNoMas.setVisibility(View.GONE);
            return;
        }

        textoEstado.setVisibility(View.GONE);

        int inicio = paginaActual * TAMANO_PAGINA;
        if (inicio >= listasFiltradas.size()) {
            botonCargarMas.setVisibility(View.GONE);
            textoNoMas.setVisibility(View.VISIBLE);
            return;
        }
        int fin = Math.min(inicio + TAMANO_PAGINA, listasFiltradas.size());

        for (int i = inicio; i < fin; i++) {
            contenedorListas.addView(buildListCard(listasFiltradas.get(i)));
        }

        boolean hayMas = fin < listasFiltradas.size();
        botonCargarMas.setVisibility(hayMas ? View.VISIBLE : View.GONE);
        textoNoMas.setVisibility(!hayMas && listasFiltradas.size() > TAMANO_PAGINA ? View.VISIBLE : View.GONE);
    }

    // Devuelve el texto correcto para estados vacios.
    private String getTextoVacio() {
        if (!textoBusqueda.isEmpty()) {
            return getString(R.string.listas_tematicas_sin_resultados_busqueda);
        }
        if (indiceFiltro == 2) {
            return getString(R.string.listas_tematicas_sin_listas_guia);
        }
        if (indiceFiltro == 3) {
            return getString(R.string.listas_tematicas_vacia);
        }
        return getString(R.string.listas_tematicas_sin_listas);
    }

    // Construye la card de una lista tematica.
    private View buildListCard(ThematicListData lista) {
        LinearLayout tarjeta = ThematicListUiHelper.createCardContainer(this);
        tarjeta.setClickable(true);
        tarjeta.setOnClickListener(v -> openListDetail(lista.id));

        tarjeta.addView(ThematicListUiHelper.createWallpaperStrip(
                this,
                lista.fotosDePortadas,
                120,
                getString(R.string.listas_tematicas_sin_portadas)
        ));

        TextView textoNombre = ThematicListUiHelper.createTitle(this, lista.nombre, 16f);
        tarjeta.addView(textoNombre);

        if (!TextUtils.isEmpty(lista.descripcion)) {
            TextView textoDescripcion = new TextView(this);
            textoDescripcion.setText(lista.descripcion);
            textoDescripcion.setMaxLines(2);
            textoDescripcion.setEllipsize(TextUtils.TruncateAt.END);
            tarjeta.addView(textoDescripcion);
        }

        TextView textoNick = new TextView(this);
        textoNick.setText(getString(R.string.detalle_lista_creador_cargando));
        tarjeta.addView(textoNick);
        if (!TextUtils.isEmpty(lista.userId)) {
            ThematicListRepository.getCreatorNick(lista.userId)
                    .addOnSuccessListener(nick -> textoNick.setText(getString(R.string.detalle_lista_creador_formato, nick)));
        }

        if (lista.esGuiaDeLectura) {
            TextView badge = new TextView(this);
            badge.setText(getString(R.string.listas_tematicas_guia_badge));
            badge.setTextColor(0xFF388E3C);
            tarjeta.addView(badge);
        }

        TextView textoMetricas = new TextView(this);
        textoMetricas.setText(
                getString(R.string.listas_tematicas_likes, lista.cantidadLikes)
                        + "  "
                        + getString(R.string.listas_tematicas_comentarios, lista.cantidadComentarios)
        );
        tarjeta.addView(textoMetricas);

        return tarjeta;
    }

    // Abre el detalle de una lista.
    private void openListDetail(String listId) {
        Intent intentoDetalle = new Intent(this, ThematicListDetailActivity.class);
        intentoDetalle.putExtra(ThematicListDetailActivity.EXTRA_LIST_ID, listId);
        startActivity(intentoDetalle);
    }

    // Recarga listas al volver a la pantalla.
    @Override
    protected void onResume() {
        super.onResume();
        if (mostrandoGuardadas) {
            loadSavedLists();
        } else if (spinnerListo) {
            loadLists();
        }
    }

    // Refresca la pantalla luego de crear o editar una lista.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_LISTA || requestCode == REQUEST_MIS_LISTAS) && resultCode == RESULT_OK) {
            if (mostrandoGuardadas) {
                loadSavedLists();
            } else {
                loadLists();
            }
        }
    }
}
