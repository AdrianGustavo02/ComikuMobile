package com.example.comiku.screens;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.appcompat.app.AppCompatActivity;
import com.example.comiku.core.ui.StatusBarUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.comiku.R;
import com.example.comiku.data.model.UserSearchData;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SelectFriendForChatActivity extends BasePlainScreenActivity {
    private RecyclerView listaAmigos;
    private ProgressBar indicadorCarga;
    private LinearLayout estadoVacio;
    private FriendSelectorAdapter adaptador;
    private List<UserSearchData> amigos;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupPlainScreenShell(R.layout.activity_select_friend_for_chat);

        inicializarVistas();
        inicializarFirebase();
        configurarAdaptador();
        cargarAmigos();
    }


    private void inicializarVistas() {
        listaAmigos = findViewById(R.id.listaAmigos);
        indicadorCarga = findViewById(R.id.indicadorCarga);
        estadoVacio = findViewById(R.id.estadoVacio);
    }

    // Inicia Firebase
    private void inicializarFirebase() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    // Configurar el adaptador de la lista
    private void configurarAdaptador() {
        amigos = new ArrayList<>();
        adaptador = new FriendSelectorAdapter(amigos, this::seleccionarAmigo);
        listaAmigos.setLayoutManager(new LinearLayoutManager(this));
        listaAmigos.setAdapter(adaptador);
    }

    // Cargar amigos desde Firestore
    private void cargarAmigos() {
        mostrarIndicadorCarga();
        String usuarioActualId = auth.getCurrentUser().getUid();

        firestore.collection("usuario")
                .document(usuarioActualId)
                .collection("Amigos")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        mostrarVacio();
                        return;
                    }

                    List<String> amigosIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        amigosIds.add(doc.getId());
                    }

                    // Cargar todos los amigos
                    if (!amigosIds.isEmpty()) {
                        firestore.collection("usuario")
                                .whereIn("__name__", amigosIds)
                                .get()
                                .addOnSuccessListener(amigoSnapshot -> {
                                    amigos.clear();
                                    for (DocumentSnapshot doc : amigoSnapshot.getDocuments()) {
                                        String amigoId = doc.getId();
                                        String amigoNick = doc.getString("Nick") != null ?
                                                doc.getString("Nick") :
                                                doc.getString("nick");
                                        String nombre = doc.getString("Nombre");
                                        String fotoPerfil = extraerFotoPerfil(doc.get("FotoPerfil"));

                                        UserSearchData amigo = new UserSearchData(
                                                amigoId,
                                                amigoNick != null ? amigoNick : "Usuario",
                                                nombre != null ? nombre : "",
                                                fotoPerfil
                                        );
                                        amigos.add(amigo);
                                    }

                                    adaptador.notifyDataSetChanged();
                                    if (amigos.isEmpty()) {
                                        mostrarVacio();
                                    } else {
                                        mostrarContenido();
                                    }
                                })
                                .addOnFailureListener(e -> mostrarError("Error al cargar amigos: " + e.getMessage()));
                    }
                })
                .addOnFailureListener(e -> mostrarError("Error al cargar amigos: " + e.getMessage()));
    }

    // Extrae la foto de perfil
    private String extraerFotoPerfil(Object fotoPerfil) {
        if (!(fotoPerfil instanceof Map)) {
            return "";
        }
        Map<?, ?> mapaFoto = (Map<?, ?>) fotoPerfil;
        Object dataUrl = mapaFoto.get("dataUrl");
        return dataUrl == null ? "" : String.valueOf(dataUrl);
    }

    // Seleccionar un amigo para iniciar conversación
    private void seleccionarAmigo(UserSearchData amigo) {
        // Pasar al ChatViewActivity que se encargará de crear el canal
        Intent intent = new Intent(this, ChatViewActivity.class);
        intent.putExtra("otherUserId", amigo.uid);
        intent.putExtra("otherUserNick", amigo.nick);
        intent.putExtra("createNewChannel", true);
        startActivity(intent);
        finish();
    }

    // Mostrar indicador de carga
    private void mostrarIndicadorCarga() {
        indicadorCarga.setVisibility(View.VISIBLE);
        listaAmigos.setVisibility(View.GONE);
        estadoVacio.setVisibility(View.GONE);
    }

    // Mostrar estado vacío
    private void mostrarVacio() {
        indicadorCarga.setVisibility(View.GONE);
        listaAmigos.setVisibility(View.GONE);
        estadoVacio.setVisibility(View.VISIBLE);
    }

    // Mostrar error
    private void mostrarError(String mensaje) {
        indicadorCarga.setVisibility(View.GONE);
        listaAmigos.setVisibility(View.GONE);
        estadoVacio.setVisibility(View.VISIBLE);

    }

    // Mostrar lista de amigos
    private void mostrarContenido() {
        indicadorCarga.setVisibility(View.GONE);
        listaAmigos.setVisibility(View.VISIBLE);
        estadoVacio.setVisibility(View.GONE);
    }
}
