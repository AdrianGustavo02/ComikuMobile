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
import androidx.appcompat.app.AppCompatActivity;
import com.example.comiku.core.ui.StatusBarUtils;

import com.example.comiku.R;
import com.example.comiku.core.ui.DeleteConfirmDialogComponent;
import com.example.comiku.core.ui.ThematicListUiHelper;
import com.example.comiku.data.model.ThematicListData;
import com.example.comiku.data.repository.ThematicListRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class MyThematicListsActivity extends BasePlainScreenActivity {

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
        setupPlainScreenShell(R.layout.activity_my_thematic_lists);

        barraCarga = findViewById(R.id.barraCargaMisListas);
        contenedorListas = findViewById(R.id.contenedorMisListas);
        textoEstado = findViewById(R.id.textoEstadoMisListas);

        loadMyLists();
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
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(v -> openListDetail(lista.id));
        item.setPadding(0, 0, 0, ThematicListUiHelper.dpToPx(this, 12));
        item.addView(ThematicListUiHelper.createFixedWallpaperStrip(
                this,
                lista.fotosDePortadas,
                152,
                getString(R.string.listas_tematicas_sin_portadas)
        ));

        LinearLayout contenido = new LinearLayout(this);
        contenido.setOrientation(LinearLayout.VERTICAL);
        contenido.setPadding(
                ThematicListUiHelper.dpToPx(this, 12),
                ThematicListUiHelper.dpToPx(this, 12),
                ThematicListUiHelper.dpToPx(this, 12),
                0
        );

        TextView textoNombre = ThematicListUiHelper.createTitle(this, lista.nombre, 17f);
        textoNombre.setTextColor(android.graphics.Color.WHITE);
        LinearLayout.LayoutParams paramsTitulo = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        paramsTitulo.bottomMargin = ThematicListUiHelper.dpToPx(this, 4);
        textoNombre.setLayoutParams(paramsTitulo);
        contenido.addView(textoNombre);

        TextView textoNick = new TextView(this);
        textoNick.setText(getString(R.string.detalle_lista_creador_cargando));
        textoNick.setTextColor(android.graphics.Color.parseColor("#d9cdea"));
        textoNick.setTextSize(15f);
        textoNick.setMaxLines(1);
        textoNick.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams paramsNick = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        paramsNick.bottomMargin = ThematicListUiHelper.dpToPx(this, 6);
        textoNick.setLayoutParams(paramsNick);
        contenido.addView(textoNick);
        if (!TextUtils.isEmpty(lista.userId)) {
            ThematicListRepository.getCreatorNick(lista.userId)
                    .addOnSuccessListener(nick -> textoNick.setText(getString(R.string.listas_tematicas_creado_por_formato, nick)));
        }

        if (!TextUtils.isEmpty(lista.descripcion)) {
            TextView textoDescripcion = new TextView(this);
            textoDescripcion.setText(lista.descripcion);
            textoDescripcion.setTextColor(android.graphics.Color.parseColor("#d9cdea"));
            textoDescripcion.setTextSize(15f);
            textoDescripcion.setMaxLines(4);
            textoDescripcion.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams paramsDescripcion = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            paramsDescripcion.bottomMargin = ThematicListUiHelper.dpToPx(this, 8);
            textoDescripcion.setLayoutParams(paramsDescripcion);
            contenido.addView(textoDescripcion);
        }

        TextView textoMetricas = new TextView(this);
        textoMetricas.setTextColor(android.graphics.Color.parseColor("#d9cdea"));
        textoMetricas.setTextSize(14f);
        textoMetricas.setMaxLines(1);
        textoMetricas.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams paramsMetricas = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        textoMetricas.setLayoutParams(paramsMetricas);

        StringBuilder textoFila = new StringBuilder();
        if (lista.esGuiaDeLectura) {
            textoFila.append("📚 ");
            textoFila.append(getString(R.string.listas_tematicas_guia_badge));
            textoFila.append(" | ");
        }
        textoFila.append("Me gusta: ");
        textoFila.append(lista.cantidadLikes);
        textoFila.append(" | ");
        textoFila.append("Comentarios: ");
        textoFila.append(lista.cantidadComentarios);

        textoMetricas.setText(textoFila.toString());
        contenido.addView(textoMetricas);

        LinearLayout filaBotones = new LinearLayout(this);
        filaBotones.setOrientation(LinearLayout.HORIZONTAL);
        filaBotones.setPadding(0, ThematicListUiHelper.dpToPx(this, 12), 0, 0);

        Button botonEditar = new Button(this);
        botonEditar.setText(getString(R.string.mis_listas_editar));
        botonEditar.setTextColor(getColorStateList(R.color.button_primary_action_text));
        botonEditar.setTypeface(null, android.graphics.Typeface.BOLD);
        botonEditar.setBackgroundResource(R.drawable.bg_button_primary_action);
        botonEditar.setOnClickListener(v -> openEditList(lista.id));
        LinearLayout.LayoutParams paramsEditar = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        paramsEditar.rightMargin = ThematicListUiHelper.dpToPx(this, 8);
        botonEditar.setLayoutParams(paramsEditar);
        filaBotones.addView(botonEditar);

        Button botonEliminar = new Button(this);
        botonEliminar.setText(getString(R.string.mis_listas_eliminar));
        botonEliminar.setTextColor(android.graphics.Color.WHITE);
        botonEliminar.setTypeface(null, android.graphics.Typeface.BOLD);
        botonEliminar.setBackgroundResource(R.drawable.bg_button_danger);
        botonEliminar.setOnClickListener(v -> confirmDelete(lista));
        LinearLayout.LayoutParams paramsEliminar = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        botonEliminar.setLayoutParams(paramsEliminar);
        filaBotones.addView(botonEliminar);

        contenido.addView(filaBotones);

        item.addView(contenido);
        return item;
    }

    // Abre el detalle de una lista tematica.
    private void openListDetail(String listId) {
        Intent intentoDetalle = new Intent(this, ThematicListDetailActivity.class);
        intentoDetalle.putExtra(ThematicListDetailActivity.EXTRA_LIST_ID, listId);
        startActivity(intentoDetalle);
    }

    // Abre la pantalla de edicion de una lista.
    private void openEditList(String listId) {
        Intent intento = new Intent(this, CreateThematicListActivity.class);
        intento.putExtra(CreateThematicListActivity.EXTRA_LIST_ID, listId);
        lanzadorEdicion.launch(intento);
    }

    // Muestra una confirmacion antes de eliminar una lista.
    private void confirmDelete(ThematicListData lista) {
        DeleteConfirmDialogComponent dialogoConfirmacion = new DeleteConfirmDialogComponent(
                this,
                getString(R.string.mis_listas_confirmar_eliminar_titulo),
                getString(R.string.mis_listas_confirmar_eliminar_mensaje),
                () -> deleteList(lista.id)
        );
        dialogoConfirmacion.show();
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
