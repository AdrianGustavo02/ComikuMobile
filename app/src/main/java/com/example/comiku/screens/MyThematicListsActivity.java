package com.example.comiku.screens;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.comiku.R;
import com.example.comiku.core.ui.ThematicListUiHelper;
import com.example.comiku.data.model.ThematicListData;
import com.example.comiku.data.repository.ThematicListRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class MyThematicListsActivity extends AppCompatActivity {

    private android.widget.ProgressBar barraCarga;
    private LinearLayout contenedorListas;
    private TextView textoEstado;

    private final ActivityResultLauncher<Intent> lanzadorEdicion = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                if (resultado.getResultCode() == RESULT_OK) {
                    setResult(RESULT_OK);
                    loadMyLists();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_thematic_lists);
        setupToolbar();

        barraCarga = findViewById(R.id.barraCargaMisListas);
        contenedorListas = findViewById(R.id.contenedorMisListas);
        textoEstado = findViewById(R.id.textoEstadoMisListas);

        loadMyLists();
    }


    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbarMisListasTematicas);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.mis_listas_titulo));
        }
    }

    // Carga las listas tematicas del usuario actual.
    private void loadMyLists() {
        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            finish();
            return;
        }

        barraCarga.setVisibility(View.VISIBLE);
        contenedorListas.removeAllViews();
        textoEstado.setVisibility(View.GONE);

        ThematicListRepository.getUserThematicLists(usuario.getUid())
                .addOnSuccessListener(this::renderMyLists)
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoEstado.setText(getString(R.string.mis_listas_error_carga));
                    textoEstado.setVisibility(View.VISIBLE);
                });
    }

    // Muestra las listas tematicas del usuario en pantalla.
    private void renderMyLists(List<ThematicListData> listas) {
        barraCarga.setVisibility(View.GONE);
        if (listas == null || listas.isEmpty()) {
            textoEstado.setText(getString(R.string.mis_listas_vacia));
            textoEstado.setVisibility(View.VISIBLE);
            return;
        }

        for (ThematicListData lista : listas) {
            contenedorListas.addView(buildListItem(lista));
        }
    }

    // Construye la card de una lista tematica propia con botones de editar y eliminar.
    private View buildListItem(ThematicListData lista) {
        LinearLayout item = ThematicListUiHelper.createCardContainer(this);
        item.addView(ThematicListUiHelper.createWallpaperStrip(
                this,
                lista.fotosDePortadas,
                110,
                getString(R.string.listas_tematicas_sin_portadas)
        ));

        item.addView(ThematicListUiHelper.createTitle(this, lista.nombre, 15f));

        if (!TextUtils.isEmpty(lista.descripcion)) {
            TextView textoDescripcion = new TextView(this);
            textoDescripcion.setText(lista.descripcion);
            textoDescripcion.setMaxLines(2);
            textoDescripcion.setEllipsize(TextUtils.TruncateAt.END);
            item.addView(textoDescripcion);
        }

        if (lista.esGuiaDeLectura) {
            TextView badge = new TextView(this);
            badge.setText(getString(R.string.listas_tematicas_guia_badge));
            badge.setTextColor(0xFF388E3C);
            item.addView(badge);
        }

        LinearLayout filaBotones = new LinearLayout(this);
        filaBotones.setOrientation(LinearLayout.HORIZONTAL);
        filaBotones.setPadding(0, ThematicListUiHelper.dpToPx(this, 8), 0, 0);

        Button botonEditar = new Button(this);
        botonEditar.setText(getString(R.string.mis_listas_editar));
        botonEditar.setOnClickListener(v -> openEditList(lista.id));
        filaBotones.addView(botonEditar);

        Button botonEliminar = new Button(this);
        botonEliminar.setText(getString(R.string.mis_listas_eliminar));
        botonEliminar.setOnClickListener(v -> confirmDelete(lista));
        filaBotones.addView(botonEliminar);

        item.addView(filaBotones);
        return item;
    }

    // Abre la pantalla de edicion de una lista.
    private void openEditList(String listId) {
        Intent intento = new Intent(this, CreateThematicListActivity.class);
        intento.putExtra(CreateThematicListActivity.EXTRA_LIST_ID, listId);
        lanzadorEdicion.launch(intento);
    }

    // Muestra una confirmacion antes de eliminar una lista.
    private void confirmDelete(ThematicListData lista) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.mis_listas_confirmar_eliminar_titulo))
                .setMessage(getString(R.string.mis_listas_confirmar_eliminar_mensaje))
                .setPositiveButton(getString(R.string.mis_listas_eliminar), (dialog, which) -> deleteList(lista.id))
                .setNegativeButton(getString(R.string.lectura_cancelar), null)
                .show();
    }

    // Elimina una lista tematica de Firestore y recarga la pantalla.
    private void deleteList(String listId) {
        barraCarga.setVisibility(View.VISIBLE);
        ThematicListRepository.deleteThematicList(listId)
                .addOnSuccessListener(resultado -> {
                    setResult(RESULT_OK);
                    loadMyLists();
                })
                .addOnFailureListener(error -> {
                    barraCarga.setVisibility(View.GONE);
                    textoEstado.setText(getString(R.string.mis_listas_error_eliminar));
                    textoEstado.setVisibility(View.VISIBLE);
                });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
