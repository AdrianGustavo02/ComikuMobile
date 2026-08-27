package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.comiku.R;
import com.example.comiku.core.firebase.ChatChannelService;
import com.example.comiku.core.image.ImageCropperConfig;
import com.example.comiku.data.model.ChatChannelData;
import com.example.comiku.data.model.GroupMemberData;
import com.example.comiku.data.model.UserSearchData;
import com.example.comiku.data.repository.FriendshipRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class GroupMembersActivity extends AppCompatActivity {
    private String channelId;
    private String usuarioActualId;
    private RecyclerView listaMiembrosGrupo;
    private ProgressBar progresoMiembrosGrupo;
    private TextView textoTituloGrupoMiembros;
    private TextView textoCreadoPorGrupo;
    private TextView textoDescripcionGrupo;
    private ImageView imagenGrupoCabecera;
    private ImageView imagenGrupoEditorPreview;
    private ImageButton botonMenuGrupoAcciones;
    private Button botonElegirFotoGrupoEditor;
    private Button botonQuitarFotoGrupoEditor;
    private Button botonGuardarDatosGrupo;
    private LinearLayout contenedorEditarGrupo;
    private EditText campoEditarNombreGrupo;
    private EditText campoEditarDescripcionGrupo;
    private ChatChannelService channelService;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private ChatChannelData canalActual;
    private final List<GroupMemberData> miembrosGrupo = new ArrayList<>();
    private GroupMemberAdapter adaptador;
    private ReportGroupDialog reportDialog;
    private boolean editorVisible = false;
    private boolean usuarioActualEsAdmin = false;
    private String dataUrlFotoGrupoEditada = "";
    private boolean fotoGrupoEditadaPorUsuario = false;
    private static final int MENU_EDITAR_INFO = 1;
    private static final int MENU_AGREGAR_MIEMBROS = 2;
    private static final int MENU_REPORTAR_GRUPO = 3;
    private static final int MENU_ABANDONAR_GRUPO = 4;
    private static final int MENU_BORRAR_GRUPO = 5;
    private static final int TAMANO_MAXIMO_FOTO_BYTES = 500 * 1024;

    private final ActivityResultLauncher<String> selectorImagenReporte = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (reportDialog != null && uri != null) {
                    reportDialog.handleImageSelected(uri);
                }
            }
    );
    private final ActivityResultLauncher<String> selectorFotoGrupoEditor = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::manejarFotoGrupoSeleccionada
    );
    private final ActivityResultLauncher<Intent> recortadorFotoGrupoEditor = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                if (resultado.getResultCode() != RESULT_OK || resultado.getData() == null) {
                    return;
                }
                String rutaRecortada = resultado.getData().getStringExtra(ImageCropperConfig.EXTRA_RESULT_URI);
                if (TextUtils.isEmpty(rutaRecortada)) {
                    return;
                }
                manejarFotoGrupoRecortada(Uri.parse(rutaRecortada));
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_members);

        inicializarDependencias();
        inicializarVistas();
        configurarLista();
        cargarGrupo();
    }


    private void inicializarDependencias() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        channelService = new ChatChannelService(this);
        channelId = getIntent().getStringExtra("channelId");
        usuarioActualId = auth.getCurrentUser().getUid();
    }


    private void inicializarVistas() {
        listaMiembrosGrupo = findViewById(R.id.listaMiembrosGrupo);
        progresoMiembrosGrupo = findViewById(R.id.progresoMiembrosGrupo);
        textoTituloGrupoMiembros = findViewById(R.id.textoTituloGrupoMiembros);
        textoCreadoPorGrupo = findViewById(R.id.textoCreadoPorGrupo);
        textoDescripcionGrupo = findViewById(R.id.textoDescripcionGrupo);
        imagenGrupoCabecera = findViewById(R.id.imagenGrupoCabecera);
        imagenGrupoEditorPreview = findViewById(R.id.imagenGrupoEditorPreview);
        botonMenuGrupoAcciones = findViewById(R.id.botonMenuGrupoAcciones);
        botonElegirFotoGrupoEditor = findViewById(R.id.botonElegirFotoGrupoEditor);
        botonQuitarFotoGrupoEditor = findViewById(R.id.botonQuitarFotoGrupoEditor);
        botonGuardarDatosGrupo = findViewById(R.id.botonGuardarDatosGrupo);
        contenedorEditarGrupo = findViewById(R.id.contenedorEditarGrupo);
        campoEditarNombreGrupo = findViewById(R.id.campoEditarNombreGrupo);
        campoEditarDescripcionGrupo = findViewById(R.id.campoEditarDescripcionGrupo);

        botonGuardarDatosGrupo.setOnClickListener(v -> guardarDatosGrupo());
        botonMenuGrupoAcciones.setOnClickListener(v -> mostrarMenuAccionesGrupo());
        botonElegirFotoGrupoEditor.setOnClickListener(v -> abrirSelectorFotoGrupoEditor());
        botonQuitarFotoGrupoEditor.setOnClickListener(v -> quitarFotoGrupoEditada());
        campoEditarNombreGrupo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                actualizarEstadoFotoGrupoEditor();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // Configurar miembros del grupo y acciones de cada miembro
    private void configurarLista() {
        adaptador = new GroupMemberAdapter(miembrosGrupo, false, usuarioActualId, new GroupMemberAdapter.MemberActionListener() {
            @Override
            public void onPromote(GroupMemberData miembro) {
                promoverMiembro(miembro);
            }

            @Override
            public void onRemove(GroupMemberData miembro) {
                confirmarQuitarMiembro(miembro);
            }

            @Override
            public void onOpenProfile(GroupMemberData miembro) {
                abrirPerfilMiembro(miembro);
            }
        });
        listaMiembrosGrupo.setLayoutManager(new LinearLayoutManager(this));
        listaMiembrosGrupo.setAdapter(adaptador);
    }

    // Cargar datos del grupo y miembros
    private void cargarGrupo() {
        if (channelId == null || channelId.trim().isEmpty()) {
            Toast.makeText(this, "Canal no valido", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        progresoMiembrosGrupo.setVisibility(android.view.View.VISIBLE);
        channelService.obtenerCanal(channelId, new ChatChannelService.ChannelDataCallback() {
            @Override
            public void onExito(ChatChannelData canal) {
                canalActual = canal;
                usuarioActualEsAdmin = canal.esAdmin(usuarioActualId);
                if (!usuarioActualEsAdmin) {
                    editorVisible = false;
                }
                contenedorEditarGrupo.setVisibility(usuarioActualEsAdmin && editorVisible ? android.view.View.VISIBLE : android.view.View.GONE);
                String nombreGrupo = canal.getGroupName() == null || canal.getGroupName().trim().isEmpty()
                        ? getString(R.string.grupo_nombre_default)
                        : canal.getGroupName();
                textoTituloGrupoMiembros.setText(nombreGrupo);
                textoCreadoPorGrupo.setText(obtenerTextoCreadoPor(canal.getCreatedBy()));
                String descripcionGrupo = canal.getGroupDescription() == null || canal.getGroupDescription().trim().isEmpty()
                        ? getString(R.string.grupo_descripcion_sin_texto)
                        : canal.getGroupDescription();
                textoDescripcionGrupo.setText(descripcionGrupo);
                mostrarFotoGrupo(canal.getGroupImageUrl(), nombreGrupo);
                campoEditarNombreGrupo.setText(canal.getGroupName() == null ? "" : canal.getGroupName());
                campoEditarDescripcionGrupo.setText(canal.getGroupDescription() == null ? "" : canal.getGroupDescription());
                dataUrlFotoGrupoEditada = canal.getGroupImageUrl() == null ? "" : canal.getGroupImageUrl();
                fotoGrupoEditadaPorUsuario = false;
                actualizarEstadoFotoGrupoEditor();
                cargarPerfilesMiembros(canal);
            }

            @Override
            public void onError(String error) {
                progresoMiembrosGrupo.setVisibility(android.view.View.GONE);
                Toast.makeText(GroupMembersActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Cargar perfiles de miembros
    private void cargarPerfilesMiembros(ChatChannelData canal) {
        miembrosGrupo.clear();
        List<String> ids = canal.getMembers() == null ? new ArrayList<>() : new ArrayList<>(canal.getMembers());
        cargarMiembroRecursivo(ids, 0, canal.getAdmins() == null ? new ArrayList<>() : canal.getAdmins());
    }


    private void cargarMiembroRecursivo(List<String> ids, int indice, List<String> admins) {
        if (indice >= ids.size()) {
            progresoMiembrosGrupo.setVisibility(android.view.View.GONE);
            boolean usuarioEsAdmin = canalActual != null && canalActual.esAdmin(usuarioActualId);
            adaptador = new GroupMemberAdapter(miembrosGrupo, usuarioEsAdmin, usuarioActualId, new GroupMemberAdapter.MemberActionListener() {
                @Override
                public void onPromote(GroupMemberData miembro) {
                    promoverMiembro(miembro);
                }

                @Override
                public void onRemove(GroupMemberData miembro) {
                    confirmarQuitarMiembro(miembro);
                }

                @Override
                public void onOpenProfile(GroupMemberData miembro) {
                    abrirPerfilMiembro(miembro);
                }
            });
            listaMiembrosGrupo.setAdapter(adaptador);
            return;
        }

        String miembroId = ids.get(indice);
        firestore.collection("usuario")
                .document(miembroId)
                .get()
                .addOnSuccessListener(doc -> {
                    String nick = doc.exists() ? doc.getString("Nick") : null;
                    if (nick == null && doc.exists()) {
                        nick = doc.getString("nick");
                    }
                    String fotoPerfil = doc.exists() ? extraerFotoPerfil(doc.get("FotoPerfil")) : "";
                    GroupMemberData miembro = new GroupMemberData(
                            miembroId,
                            nick == null ? "Usuario" : nick,
                            fotoPerfil,
                            admins.contains(miembroId)
                    );
                    miembrosGrupo.add(miembro);
                    cargarMiembroRecursivo(ids, indice + 1, admins);
                })
                .addOnFailureListener(e -> {
                    miembrosGrupo.add(new GroupMemberData(miembroId, "Usuario", "", admins.contains(miembroId)));
                    cargarMiembroRecursivo(ids, indice + 1, admins);
                });
    }

    // Mostrar dialogo para seleccionar nuevos miembros
    private void mostrarDialogoAgregarMiembros() {
        if (canalActual == null) {
            return;
        }
        String uidActual = usuarioActualId;

        firestore.collection("usuario")
                .document(uidActual)
                .collection("Amigos")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<String> candidatosIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String amigoId = doc.getId();
                        if (canalActual.getMembers() == null || !canalActual.getMembers().contains(amigoId)) {
                            candidatosIds.add(amigoId);
                        }
                    }

                    if (candidatosIds.isEmpty()) {
                        Toast.makeText(this, "No hay amigos disponibles para agregar", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    cargarCandidatosParaDialogo(candidatosIds);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "No se pudo cargar amigos", Toast.LENGTH_SHORT).show());
    }

    // Cargar candidatos para construir dialogo de seleccion multiple
    private void cargarCandidatosParaDialogo(List<String> ids) {
        List<UserSearchData> candidatos = new ArrayList<>();
        cargarCandidatoRecursivo(ids, 0, candidatos);
    }

    // Cargar candidato por candidato para el dialogo
    private void cargarCandidatoRecursivo(List<String> ids, int indice, List<UserSearchData> candidatos) {
        if (indice >= ids.size()) {
            mostrarDialogoSeleccionCandidatos(candidatos);
            return;
        }

        String uid = ids.get(indice);
        firestore.collection("usuario")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    String nick = doc.getString("Nick");
                    if (nick == null) {
                        nick = doc.getString("nick");
                    }
                    String nombre = doc.getString("Nombre");
                    String fotoPerfil = extraerFotoPerfil(doc.get("FotoPerfil"));
                    candidatos.add(new UserSearchData(uid, nick == null ? "Usuario" : nick, nombre == null ? "" : nombre, fotoPerfil));
                    cargarCandidatoRecursivo(ids, indice + 1, candidatos);
                })
                .addOnFailureListener(e -> {
                    candidatos.add(new UserSearchData(uid, "Usuario", "", null));
                    cargarCandidatoRecursivo(ids, indice + 1, candidatos);
                });
    }

    // Mostrar dialogo y agregar miembros al confirmar
    private void mostrarDialogoSeleccionCandidatos(List<UserSearchData> candidatos) {
        if (candidatos.isEmpty()) {
            Toast.makeText(this, "No hay amigos disponibles para agregar", Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> seleccionados = new HashSet<>();
        View vistaDialogo = getLayoutInflater().inflate(R.layout.dialog_group_member_selector, null);
        RecyclerView listaCandidatos = vistaDialogo.findViewById(R.id.listaCandidatosAgregarMiembros);
        GroupFriendSelectorAdapter adaptadorCandidatos = new GroupFriendSelectorAdapter(candidatos, false, seleccionActual -> {
            seleccionados.clear();
            seleccionados.addAll(seleccionActual);
        });
        listaCandidatos.setLayoutManager(new LinearLayoutManager(this));
        listaCandidatos.setAdapter(adaptadorCandidatos);
        new AlertDialog.Builder(this)
                .setTitle("Agregar miembros")
                .setView(vistaDialogo)
                .setPositiveButton("Agregar", (dialog, which) -> {
                    List<String> nuevos = new ArrayList<>(seleccionados);
                    if (nuevos.isEmpty()) {
                        Toast.makeText(this, "No seleccionaste miembros", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    channelService.agregarMiembrosGrupo(channelId, nuevos, new ChatChannelService.OperationCallback() {
                        @Override
                        public void onExito() {
                            Toast.makeText(GroupMembersActivity.this, "Miembros agregados", Toast.LENGTH_SHORT).show();
                            cargarGrupo();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(GroupMembersActivity.this, error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // Promover miembro a admin
    private void promoverMiembro(GroupMemberData miembro) {
        channelService.convertirMiembroEnAdmin(channelId, miembro.getUid(), new ChatChannelService.OperationCallback() {
            @Override
            public void onExito() {
                Toast.makeText(GroupMembersActivity.this, "Ahora es administrador", Toast.LENGTH_SHORT).show();
                cargarGrupo();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(GroupMembersActivity.this, obtenerMensajeErrorAbandono(error), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Devuelve un mensaje cuando no queda admin en el grupo.
    private String obtenerMensajeErrorAbandono(String error) {
        if (error == null) {
            return "No fue posible abandonar el grupo.";
        }
        String errorNormalizado = error.toLowerCase();
        if (errorNormalizado.contains("admin")) {
            return "No fue posible abandonar el grupo. Debe quedar al menos un administrador.";
        }
        return error;
    }

    // Quitar miembro del grupo
    private void quitarMiembro(GroupMemberData miembro) {
        channelService.eliminarMiembroGrupo(channelId, miembro.getUid(), new ChatChannelService.OperationCallback() {
            @Override
            public void onExito() {
                Toast.makeText(GroupMembersActivity.this, "Miembro eliminado", Toast.LENGTH_SHORT).show();
                cargarGrupo();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(GroupMembersActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Pide confirmacion antes de quitar un miembro del grupo.
    private void confirmarQuitarMiembro(GroupMemberData miembro) {
        if (miembro == null) {
            return;
        }
        String nickMiembro = miembro.getNick() == null || miembro.getNick().trim().isEmpty()
                ? "este miembro"
                : miembro.getNick();
        new AlertDialog.Builder(this)
                .setTitle("Eliminar del grupo")
                .setMessage("Se eliminara a " + nickMiembro + " del grupo. Deseas continuar?")
                .setPositiveButton("Eliminar", (dialog, which) -> quitarMiembro(miembro))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // Abre el perfil de un miembro
    private void abrirPerfilMiembro(GroupMemberData miembro) {
        if (miembro == null || TextUtils.isEmpty(miembro.getUid())) {
            return;
        }
        FriendshipRepository.canOpenUserProfile(usuarioActualId, miembro.getUid())
                .addOnSuccessListener(canOpen -> {
                    if (!Boolean.TRUE.equals(canOpen)) {
                        Toast.makeText(this, R.string.perfil_acceso_bloqueado, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Intent intent = new Intent(this, ProfileActivity.class);
                    intent.putExtra(ProfileActivity.EXTRA_USER_ID, miembro.getUid());
                    startActivity(intent);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.error_perfil_carga, Toast.LENGTH_SHORT).show());
    }

    // Extrae la foto
    private String extraerFotoPerfil(Object fotoPerfil) {
        if (fotoPerfil == null) {
            return "";
        }
        if (fotoPerfil instanceof String) {
            return String.valueOf(fotoPerfil);
        }
        if (fotoPerfil instanceof Map) {
            Object dataUrl = ((Map<?, ?>) fotoPerfil).get("dataUrl");
            return dataUrl == null ? "" : String.valueOf(dataUrl);
        }
        return "";
    }

    // Muestra u oculta el formulario de edicion del grupo.
    private void alternarEditorGrupo() {
        if (canalActual == null || !usuarioActualEsAdmin) {
            return;
        }
        editorVisible = !editorVisible;
        contenedorEditarGrupo.setVisibility(editorVisible ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    // Muestra un menu con las acciones del grupo.
    private void mostrarMenuAccionesGrupo() {
        if (canalActual == null) {
            return;
        }
        PopupMenu menuOpciones = new PopupMenu(this, botonMenuGrupoAcciones);
        if (usuarioActualEsAdmin) {
            menuOpciones.getMenu().add(Menu.NONE, MENU_EDITAR_INFO, 1, "Editar informacion de grupo");
            menuOpciones.getMenu().add(Menu.NONE, MENU_AGREGAR_MIEMBROS, 2, "Agregar miembros");
        }
        menuOpciones.getMenu().add(Menu.NONE, MENU_REPORTAR_GRUPO, 3, "Reportar grupo");
        menuOpciones.getMenu().add(Menu.NONE, MENU_ABANDONAR_GRUPO, 4, "Abandonar grupo");
        if (usuarioActualEsAdmin) {
            menuOpciones.getMenu().add(Menu.NONE, MENU_BORRAR_GRUPO, 5, "Borrar grupo");
        }
        menuOpciones.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == MENU_EDITAR_INFO) {
                alternarEditorGrupo();
                return true;
            }
            if (itemId == MENU_AGREGAR_MIEMBROS) {
                mostrarDialogoAgregarMiembros();
                return true;
            }
            if (itemId == MENU_REPORTAR_GRUPO) {
                mostrarDialogoReportarGrupo();
                return true;
            }
            if (itemId == MENU_ABANDONAR_GRUPO) {
                confirmarAbandonoGrupo();
                return true;
            }
            if (itemId == MENU_BORRAR_GRUPO) {
                confirmarBorradoGrupo();
                return true;
            }
            return false;
        });
        menuOpciones.show();
    }

    // Devuelve el texto de creador segun el usuario actual.
    private String obtenerTextoCreadoPor(String creadorId) {
        if (!TextUtils.isEmpty(creadorId) && creadorId.equals(usuarioActualId)) {
            return getString(R.string.grupo_creado_por_tu);
        }
        return getString(R.string.grupo_creado_por_otro);
    }

    // Muestra la foto del grupo y usa inicial como respaldo.
    private void mostrarFotoGrupo(String fotoGrupo, String nombreGrupo) {
        String inicialGrupo = extraerInicialGrupo(nombreGrupo);
        if (TextUtils.isEmpty(fotoGrupo)) {
            if (TextUtils.isEmpty(inicialGrupo)) {
                imagenGrupoCabecera.setImageResource(R.drawable.default_profile_picture);
            } else {
                imagenGrupoCabecera.setImageBitmap(crearAvatarInicialGrupo(inicialGrupo));
            }
            return;
        }
        if (fotoGrupo.startsWith("data:")) {
            Bitmap bitmap = decodeDataUrl(fotoGrupo);
            if (bitmap != null) {
                imagenGrupoCabecera.setImageBitmap(bitmap);
            } else if (TextUtils.isEmpty(inicialGrupo)) {
                imagenGrupoCabecera.setImageResource(R.drawable.default_profile_picture);
            } else {
                imagenGrupoCabecera.setImageBitmap(crearAvatarInicialGrupo(inicialGrupo));
            }
            return;
        }
        Glide.with(this)
                .load(fotoGrupo)
                .placeholder(R.drawable.default_profile_picture)
                .error(R.drawable.default_profile_picture)
                .into(imagenGrupoCabecera);
    }

    // Extrae la inicial del nombre del grupo.
    private String extraerInicialGrupo(String nombreGrupo) {
        if (TextUtils.isEmpty(nombreGrupo)) {
            return "";
        }
        String nombreLimpio = nombreGrupo.trim();
        if (TextUtils.isEmpty(nombreLimpio)) {
            return "";
        }
        return nombreLimpio.substring(0, 1).toUpperCase();
    }

    // Crea un avatar circular con la inicial del grupo.
    private Bitmap crearAvatarInicialGrupo(String inicial) {
        int tamanio = imagenGrupoCabecera.getLayoutParams() != null && imagenGrupoCabecera.getLayoutParams().width > 0
                ? imagenGrupoCabecera.getLayoutParams().width
                : (int) (104f * getResources().getDisplayMetrics().density);
        Bitmap bitmapAvatar = Bitmap.createBitmap(tamanio, tamanio, Bitmap.Config.ARGB_8888);
        Canvas lienzoAvatar = new Canvas(bitmapAvatar);

        Paint pinturaFondo = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinturaFondo.setColor(ContextCompat.getColor(this, R.color.blue_light));
        float radio = tamanio / 2f;
        lienzoAvatar.drawCircle(radio, radio, radio, pinturaFondo);

        Paint pinturaTexto = new Paint(Paint.ANTI_ALIAS_FLAG);
        pinturaTexto.setColor(ContextCompat.getColor(this, R.color.blue));
        pinturaTexto.setTextAlign(Paint.Align.CENTER);
        pinturaTexto.setTypeface(Typeface.DEFAULT_BOLD);
        pinturaTexto.setTextSize(tamanio * 0.42f);

        Paint.FontMetrics metricas = pinturaTexto.getFontMetrics();
        float ejeTextoY = radio - ((metricas.ascent + metricas.descent) / 2f);
        lienzoAvatar.drawText(inicial, radio, ejeTextoY, pinturaTexto);
        return bitmapAvatar;
    }

    // Convierte dataUrl a bitmap para foto local.
    private Bitmap decodeDataUrl(String dataUrl) {
        if (TextUtils.isEmpty(dataUrl)) {
            return null;
        }
        int indiceComa = dataUrl.indexOf(',');
        if (indiceComa < 0 || indiceComa >= dataUrl.length() - 1) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(dataUrl.substring(indiceComa + 1), Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    // Abre la galeria para elegir una foto nueva del grupo.
    private void abrirSelectorFotoGrupoEditor() {
        selectorFotoGrupoEditor.launch("image/*");
    }

    // Procesa la foto elegida y abre el recortador.
    private void manejarFotoGrupoSeleccionada(Uri uriSeleccionada) {
        if (uriSeleccionada == null) {
            return;
        }
        String tipoContenido = getContentResolver().getType(uriSeleccionada);
        if (!esTipoImagenPermitido(tipoContenido)) {
            Toast.makeText(this, R.string.grupo_error_tipo_foto_no_valido, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent recorte = ImageCropperConfig.createIntent(
                this,
                uriSeleccionada,
                "foto-grupo-editada.jpg",
                getString(R.string.recorte_titulo_foto_grupo),
                1,
                1
        );
        recortadorFotoGrupoEditor.launch(recorte);
    }

    // Recibe la foto recortada y la deja lista para guardar.
    private void manejarFotoGrupoRecortada(Uri uriRecortada) {
        if (uriRecortada == null) {
            return;
        }
        try {
            String tipoContenido = getContentResolver().getType(uriRecortada);
            if (TextUtils.isEmpty(tipoContenido)) {
                tipoContenido = "image/jpeg";
            }
            byte[] bytesImagen = leerBytesDesdeUri(uriRecortada);
            if (bytesImagen.length > TAMANO_MAXIMO_FOTO_BYTES) {
                Toast.makeText(this, R.string.grupo_error_tamano_foto_no_valido, Toast.LENGTH_SHORT).show();
                return;
            }
            dataUrlFotoGrupoEditada = construirDataUrlFoto(bytesImagen, tipoContenido);
            fotoGrupoEditadaPorUsuario = true;
            imagenGrupoEditorPreview.setImageURI(uriRecortada);
            actualizarEstadoFotoGrupoEditor();
        } catch (IOException error) {
            Toast.makeText(this, R.string.grupo_error_lectura_foto, Toast.LENGTH_SHORT).show();
        }
    }

    // Quita la foto para volver al estado sin imagen.
    private void quitarFotoGrupoEditada() {
        dataUrlFotoGrupoEditada = "";
        fotoGrupoEditadaPorUsuario = true;
        actualizarEstadoFotoGrupoEditor();
    }

    // Actualiza la vista previa y los botones del editor de foto.
    private void actualizarEstadoFotoGrupoEditor() {
        boolean tieneFoto = !TextUtils.isEmpty(dataUrlFotoGrupoEditada);
        if (tieneFoto) {
            botonElegirFotoGrupoEditor.setText(R.string.grupo_boton_cambiar_foto);
            botonQuitarFotoGrupoEditor.setVisibility(android.view.View.VISIBLE);
            imagenGrupoEditorPreview.setVisibility(android.view.View.VISIBLE);
            if (dataUrlFotoGrupoEditada.startsWith("data:")) {
                Bitmap bitmap = decodeDataUrl(dataUrlFotoGrupoEditada);
                if (bitmap != null) {
                    imagenGrupoEditorPreview.setImageBitmap(bitmap);
                } else {
                    imagenGrupoEditorPreview.setImageResource(R.drawable.default_profile_picture);
                }
            } else {
                Glide.with(this)
                        .load(dataUrlFotoGrupoEditada)
                        .placeholder(R.drawable.default_profile_picture)
                        .error(R.drawable.default_profile_picture)
                        .into(imagenGrupoEditorPreview);
            }
            return;
        }

        botonElegirFotoGrupoEditor.setText(R.string.grupo_boton_elegir_foto);
        botonQuitarFotoGrupoEditor.setVisibility(android.view.View.GONE);
        String nombreGrupo = campoEditarNombreGrupo.getText() == null
                ? ""
                : campoEditarNombreGrupo.getText().toString();
        String inicialGrupo = extraerInicialGrupo(nombreGrupo);
        if (TextUtils.isEmpty(inicialGrupo)) {
            imagenGrupoEditorPreview.setImageDrawable(null);
            imagenGrupoEditorPreview.setVisibility(android.view.View.GONE);
        } else {
            imagenGrupoEditorPreview.setImageBitmap(crearAvatarInicialGrupo(inicialGrupo));
            imagenGrupoEditorPreview.setVisibility(android.view.View.VISIBLE);
        }
    }

    // Resuelve que imagen debe persistirse al guardar cambios.
    private String resolverImagenGrupoParaGuardado() {
        if (fotoGrupoEditadaPorUsuario) {
            return dataUrlFotoGrupoEditada == null ? "" : dataUrlFotoGrupoEditada;
        }
        if (canalActual == null || canalActual.getGroupImageUrl() == null) {
            return "";
        }
        return canalActual.getGroupImageUrl();
    }

    // Convierte bytes de imagen en dataUrl.
    private String construirDataUrlFoto(byte[] bytesFoto, String tipoFoto) {
        if (bytesFoto == null || bytesFoto.length == 0 || TextUtils.isEmpty(tipoFoto)) {
            return "";
        }
        String base64 = Base64.encodeToString(bytesFoto, Base64.NO_WRAP);
        return "data:" + tipoFoto + ";base64," + base64;
    }

    // Lee bytes desde un archivo elegido por el usuario.
    private byte[] leerBytesDesdeUri(Uri uriArchivo) throws IOException {
        InputStream flujo = getContentResolver().openInputStream(uriArchivo);
        if (flujo == null) {
            throw new IOException("No se pudo abrir el archivo.");
        }
        try {
            ByteArrayOutputStream acumulador = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int leidos;
            while ((leidos = flujo.read(buffer)) != -1) {
                acumulador.write(buffer, 0, leidos);
            }
            return acumulador.toByteArray();
        } finally {
            flujo.close();
        }
    }

    // Valida formatos de imagen permitidos para el grupo.
    private boolean esTipoImagenPermitido(String tipoContenido) {
        return "image/jpeg".equals(tipoContenido)
                || "image/png".equals(tipoContenido)
                || "image/webp".equals(tipoContenido);
    }

    // Guardar datos editables del grupo si es admin
    private void guardarDatosGrupo() {
        String nombre = campoEditarNombreGrupo.getText() == null ? "" : campoEditarNombreGrupo.getText().toString();
        String descripcion = campoEditarDescripcionGrupo.getText() == null ? "" : campoEditarDescripcionGrupo.getText().toString();
        String imagen = resolverImagenGrupoParaGuardado();

        channelService.actualizarDatosGrupo(channelId, nombre, descripcion, imagen, new ChatChannelService.OperationCallback() {
            @Override
            public void onExito() {
                Toast.makeText(GroupMembersActivity.this, "Datos del grupo actualizados", Toast.LENGTH_SHORT).show();
                editorVisible = false;
                cargarGrupo();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(GroupMembersActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Salir del grupo actual
    private void abandonarGrupo() {
        channelService.abandonarGrupo(channelId, new ChatChannelService.OperationCallback() {
            @Override
            public void onExito() {
                Toast.makeText(GroupMembersActivity.this, "Abandonaste el grupo", Toast.LENGTH_SHORT).show();
                abrirPantallaChatsPrincipal();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(GroupMembersActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Pide confirmacion antes de abandonar el grupo.
    private void confirmarAbandonoGrupo() {
        new AlertDialog.Builder(this)
                .setTitle("Abandonar grupo")
                .setMessage("Esta accion te sacara del grupo. ¿Deseas continuar?")
                .setPositiveButton("Abandonar", (dialog, which) -> abandonarGrupo())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // Borrar grupo con permisos de admin
    private void borrarGrupo() {
        channelService.borrarGrupo(channelId, new ChatChannelService.OperationCallback() {
            @Override
            public void onExito() {
                Toast.makeText(GroupMembersActivity.this, "Grupo eliminado", Toast.LENGTH_SHORT).show();
                abrirPantallaChatsPrincipal();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(GroupMembersActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Pide confirmacion antes de borrar el grupo.
    private void confirmarBorradoGrupo() {
        new AlertDialog.Builder(this)
                .setTitle("Borrar grupo")
                .setMessage("Esta accion eliminara el grupo. ¿Deseas continuar?")
                .setPositiveButton("Borrar", (dialog, which) -> borrarGrupo())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // Abre la pantalla principal de chats y limpia la navegacion anterior.
    private void abrirPantallaChatsPrincipal() {
        Intent intent = new Intent(this, ChatsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Mostrar dialogo para reportar el grupo
    private void mostrarDialogoReportarGrupo() {
        if (canalActual == null) {
            Toast.makeText(this, "No se pudo obtener informacion del grupo", Toast.LENGTH_SHORT).show();
            return;
        }

        reportDialog = new ReportGroupDialog(
            GroupMembersActivity.this,
            channelId,
            canalActual.getGroupName(),
            new ReportGroupDialog.OnReportCompleted() {
                @Override
                public void onSuccess() {
                    Toast.makeText(GroupMembersActivity.this, "Gracias por ayudar a mantener la comunidad segura", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String mensaje) {
                    Toast.makeText(GroupMembersActivity.this, "Error: " + mensaje, Toast.LENGTH_SHORT).show();
                }
            },
            selectorImagenReporte
        );
        reportDialog.mostrar();
    }
}
