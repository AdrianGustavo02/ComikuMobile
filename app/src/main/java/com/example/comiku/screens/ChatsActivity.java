package com.example.comiku.screens;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.comiku.R;
import com.example.comiku.core.firebase.StreamChatAuthService;
import com.example.comiku.data.repository.FriendshipRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.example.comiku.data.model.ChatChannelData;
import com.example.comiku.data.repository.StreamChatRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// Pantalla principal de chats que muestra todos los canales
public class ChatsActivity extends AppCompatActivity {
    private RecyclerView listaCanales;
    private ProgressBar indicadorCarga;
    private LinearLayout estadoVacio;
    private LinearLayout estadoError;
    private TextView textoErrorDetalle;
    private Button botonReintentar;
    private Button botonIniciarConversacion;
    private Button botonCrearGrupo;
    private ChatChannelAdapter adaptador;
    private List<ChatChannelData> canales;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private ListenerRegistration escuchadorCanales;
    private StreamChatRepository repositorio;
    private StreamChatAuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chats);

        inicializarVistas();
        inicializarFirebase();
        repositorio = StreamChatRepository.obtenerInstancia(this);
        authService = StreamChatAuthService.obtenerInstancia(this);
        configurarAdaptador();
        autenticarConStreamChat();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (auth != null && auth.getCurrentUser() != null) {
            cargarCanales();
        }
    }

    // Inicializar las vistas del layout
    private void inicializarVistas() {
        listaCanales = findViewById(R.id.listaCanales);
        indicadorCarga = findViewById(R.id.indicadorCarga);
        estadoVacio = findViewById(R.id.estadoVacio);
        estadoError = findViewById(R.id.estadoError);
        textoErrorDetalle = findViewById(R.id.textoErrorDetalle);
        botonReintentar = findViewById(R.id.botonReintentar);
        botonIniciarConversacion = findViewById(R.id.botonIniciarConversacion);
        botonCrearGrupo = findViewById(R.id.botonCrearGrupo);

        botonReintentar.setOnClickListener(v -> cargarCanales());
        botonIniciarConversacion.setOnClickListener(v -> iniciarConversacion());
        botonCrearGrupo.setOnClickListener(v -> crearGrupo());
    }

    // Inicializar Firebase
    private void inicializarFirebase() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    // Configurar el adaptador de la lista
    private void configurarAdaptador() {
        canales = new ArrayList<>();
        String usuarioActualId = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        adaptador = new ChatChannelAdapter(canales, usuarioActualId, this::abrirChat);
        listaCanales.setLayoutManager(new LinearLayoutManager(this));
        listaCanales.setAdapter(adaptador);
    }

    // Autenticar con Stream Chat antes de cargar canales
    private void autenticarConStreamChat() {
        mostrarIndicadorCarga();
        
        authService.autenticar(new StreamChatAuthService.AuthCallback() {
            @Override
            public void onExito(com.example.comiku.data.model.StreamChatTokenResponse response) {
                // Autenticación exitosa, cargar canales
                cargarCanales();
            }

            @Override
            public void onError(String error) {
                // Si falla la autenticación, mostrar error pero permitir cargar canales locales
                cargarCanales();
            }
        });
    }

    // Cargar canales desde Firestore
    private void cargarCanales() {
        if (auth.getCurrentUser() == null) {
            mostrarError("Sesion invalida. Vuelve a iniciar sesion.");
            return;
        }
        String usuarioActualId = auth.getCurrentUser().getUid();

        // Query para obtener los canales del usuario actual
        if (escuchadorCanales != null) {
            escuchadorCanales.remove();
        }
        escuchadorCanales = firestore.collection("streamChannels")
                .whereArrayContains("members", usuarioActualId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        mostrarError("Error al cargar chats: " + error.getMessage());
                        return;
                    }

                    if (snapshot == null || snapshot.isEmpty()) {
                        mostrarVacio();
                        return;
                    }

                    List<ChatChannelData> canalesTemporales = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        ChatChannelData canal = doc.toObject(ChatChannelData.class);
                        if (canal != null) {
                            if (canal.getId() == null || canal.getId().trim().isEmpty()) {
                                canal.setId(doc.getId());
                            }
                            canalesTemporales.add(canal);
                        }
                    }

                    Collections.sort(canalesTemporales, new Comparator<ChatChannelData>() {
                        @Override
                        public int compare(ChatChannelData canalA, ChatChannelData canalB) {
                            long fechaA = canalA != null ? canalA.getLastMessageAt() : 0L;
                            long fechaB = canalB != null ? canalB.getLastMessageAt() : 0L;
                            if (fechaA != fechaB) {
                                return Long.compare(fechaB, fechaA);
                            }
                            String idA = canalA != null && canalA.getId() != null ? canalA.getId() : "";
                            String idB = canalB != null && canalB.getId() != null ? canalB.getId() : "";
                            return idB.compareTo(idA);
                        }
                    });

                    Task<List<String>> tareaBloqueados = FriendshipRepository.getBlockedUserIds(usuarioActualId);
                    Task<List<String>> tareaBloqueadores = FriendshipRepository.getUsersWhoBlockedUserIds(usuarioActualId);

                    Tasks.whenAllSuccess(tareaBloqueados, tareaBloqueadores)
                            .addOnSuccessListener(resultados -> {
                                Set<String> bloqueos = new HashSet<>();
                                if (resultados.size() > 0 && resultados.get(0) instanceof List) {
                                    bloqueos.addAll((List<String>) resultados.get(0));
                                }
                                if (resultados.size() > 1 && resultados.get(1) instanceof List) {
                                    bloqueos.addAll((List<String>) resultados.get(1));
                                }

                                canales.clear();
                                for (ChatChannelData canal : canalesTemporales) {
                                    if (canal == null) {
                                        continue;
                                    }
                                    if (canal.isGroupChat() || canal.cantidadMiembros() > 2) {
                                        canales.add(canal);
                                        continue;
                                    }
                                    String otroUsuario = canal.getOtherUserId(usuarioActualId);
                                    if (otroUsuario == null || bloqueos.contains(otroUsuario)) {
                                        continue;
                                    }
                                    canales.add(canal);
                                }

                                if (canales.isEmpty()) {
                                    mostrarVacio();
                                } else {
                                    resolverNombresCanales(usuarioActualId);
                                }
                            })
                            .addOnFailureListener(taskError -> {
                                canales.clear();
                                canales.addAll(canalesTemporales);
                                if (canales.isEmpty()) {
                                    mostrarVacio();
                                } else {
                                    resolverNombresCanales(usuarioActualId);
                                }
                            });
                });
    }

    // Resuelve el nick del otro usuario para mostrar un titulo claro en chats personales.
    private void resolverNombresCanales(String usuarioActualId) {
        Set<String> otrosIds = new HashSet<>();
        for (ChatChannelData canal : canales) {
            if (canal == null || canal.isGroupChat()) {
                continue;
            }
            String otroUsuarioId = canal.getOtherUserId(usuarioActualId);
            if (otroUsuarioId != null && !otroUsuarioId.trim().isEmpty()) {
                otrosIds.add(otroUsuarioId);
            }
        }

        if (otrosIds.isEmpty()) {
            adaptador.notifyDataSetChanged();
            mostrarContenido();
            return;
        }

        cargarNicksUsuarios(new ArrayList<>(otrosIds), mapaNicks -> {
            for (ChatChannelData canal : canales) {
                if (canal == null || canal.isGroupChat()) {
                    continue;
                }
                String otroUsuarioId = canal.getOtherUserId(usuarioActualId);
                if (otroUsuarioId == null) {
                    continue;
                }
                String nick = mapaNicks.get(otroUsuarioId);
                if (nick != null && !nick.trim().isEmpty()) {
                    canal.setDisplayName(nick);
                }
            }
            adaptador.notifyDataSetChanged();
            mostrarContenido();
        });
    }

    // Carga los nicks de usuarios en lotes para evitar limites de whereIn.
    private void cargarNicksUsuarios(List<String> usuariosIds, NicknamesCallback callback) {
        Map<String, String> mapaNicks = new HashMap<>();
        if (usuariosIds == null || usuariosIds.isEmpty()) {
            callback.onResult(mapaNicks);
            return;
        }

        List<List<String>> lotes = new ArrayList<>();
        int indice = 0;
        while (indice < usuariosIds.size()) {
            int fin = Math.min(indice + 10, usuariosIds.size());
            lotes.add(new ArrayList<>(usuariosIds.subList(indice, fin)));
            indice = fin;
        }

        final int[] pendientes = {lotes.size()};
        for (List<String> lote : lotes) {
            firestore.collection("usuario")
                    .whereIn("__name__", lote)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            String nick = doc.getString("Nick");
                            if (nick == null || nick.trim().isEmpty()) {
                                nick = doc.getString("nick");
                            }
                            if (nick != null && !nick.trim().isEmpty()) {
                                mapaNicks.put(doc.getId(), nick);
                            }
                        }
                        pendientes[0] = pendientes[0] - 1;
                        if (pendientes[0] == 0) {
                            callback.onResult(mapaNicks);
                        }
                    })
                    .addOnFailureListener(error -> {
                        pendientes[0] = pendientes[0] - 1;
                        if (pendientes[0] == 0) {
                            callback.onResult(mapaNicks);
                        }
                    });
        }
    }

    // Abrir un chat específico
    private void abrirChat(ChatChannelData canal) {
        Intent intent = new Intent(this, ChatViewActivity.class);
        if (canal.isGroupChat()) {
            intent.putExtra("channelId", canal.getId());
            intent.putExtra("channelType", canal.getType());
            intent.putExtra("isGroupChat", true);
        } else {
            String otroUsuarioId = canal.getOtherUserId(usuarioActualIdSeguro());
            if (otroUsuarioId != null && !otroUsuarioId.trim().isEmpty()) {
                intent.putExtra("otherUserId", otroUsuarioId);
            }
            intent.putExtra("channelId", canal.getId());
            intent.putExtra("channelType", canal.getType());
            intent.putExtra("isGroupChat", false);
        }
        startActivity(intent);
    }

    // Obtiene el uid actual para calcular el otro miembro del chat.
    private String usuarioActualIdSeguro() {
        if (auth.getCurrentUser() == null) {
            return "";
        }
        return auth.getCurrentUser().getUid();
    }

    private interface NicknamesCallback {
        void onResult(Map<String, String> mapaNicks);
    }

    // Iniciar una nueva conversación
    private void iniciarConversacion() {
        Intent intent = new Intent(this, SelectFriendForChatActivity.class);
        startActivity(intent);
    }

    // Abrir pantalla para crear grupo
    private void crearGrupo() {
        Intent intent = new Intent(this, CreateGroupActivity.class);
        startActivity(intent);
    }

    // Mostrar indicador de carga
    private void mostrarIndicadorCarga() {
        indicadorCarga.setVisibility(View.VISIBLE);
        listaCanales.setVisibility(View.GONE);
        estadoVacio.setVisibility(View.GONE);
        estadoError.setVisibility(View.GONE);
    }

    // Mostrar estado vacío
    private void mostrarVacio() {
        indicadorCarga.setVisibility(View.GONE);
        listaCanales.setVisibility(View.GONE);
        estadoVacio.setVisibility(View.VISIBLE);
        estadoError.setVisibility(View.GONE);
    }

    // Mostrar error
    private void mostrarError(String mensaje) {
        indicadorCarga.setVisibility(View.GONE);
        listaCanales.setVisibility(View.GONE);
        estadoVacio.setVisibility(View.GONE);
        estadoError.setVisibility(View.VISIBLE);
        textoErrorDetalle.setText(mensaje);
    }

    // Mostrar contenido (lista de canales)
    private void mostrarContenido() {
        indicadorCarga.setVisibility(View.GONE);
        listaCanales.setVisibility(View.VISIBLE);
        estadoVacio.setVisibility(View.GONE);
        estadoError.setVisibility(View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (escuchadorCanales != null) {
            escuchadorCanales.remove();
        }
    }
}
