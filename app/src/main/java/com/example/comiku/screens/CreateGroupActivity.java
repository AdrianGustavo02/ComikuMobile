package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.comiku.R;
import com.example.comiku.core.firebase.ChatChannelService;
import com.example.comiku.core.image.ImageCropperConfig;
import com.example.comiku.core.validation.GroupValidator;
import com.example.comiku.data.model.ChatChannelData;
import com.example.comiku.data.model.UserSearchData;
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


public class CreateGroupActivity extends AppCompatActivity {
    private static final int TAMANO_MAXIMO_FOTO_BYTES = 500 * 1024;
    private EditText campoNombreGrupo;
    private EditText campoDescripcionGrupo;
    private ImageView imagenGrupoPreview;
    private TextView textoCantidadSeleccion;
    private RecyclerView listaAmigosGrupo;
    private ProgressBar indicadorCargaGrupo;
    private Button botonCrearGrupoConfirmar;
    private Button botonElegirFotoGrupo;
    private Button botonQuitarFotoGrupo;
    private GroupFriendSelectorAdapter adaptador;
    private final List<UserSearchData> amigos = new ArrayList<>();
    private final List<String> miembrosSeleccionados = new ArrayList<>();
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;
    private ChatChannelService channelService;
    private byte[] bytesFotoGrupoSeleccionada;
    private String tipoFotoGrupoSeleccionada;
    private String dataUrlFotoGrupo;

    private final ActivityResultLauncher<String> selectorFotoGrupo = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::handlePhotoSelected
    );
    private final ActivityResultLauncher<Intent> recortadorFotoGrupo = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            resultado -> {
                if (resultado.getResultCode() != RESULT_OK || resultado.getData() == null) {
                    return;
                }
                String rutaRecortada = resultado.getData().getStringExtra(ImageCropperConfig.EXTRA_RESULT_URI);
                if (TextUtils.isEmpty(rutaRecortada)) {
                    return;
                }
                handleCroppedPhotoSelected(Uri.parse(rutaRecortada));
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        inicializarDependencias();
        inicializarVistas();
        configurarListaAmigos();
        configurarValidaciones();
        cargarAmigos();
    }

    // Inicializar servicios de firestore y chat
    private void inicializarDependencias() {
        firestore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        channelService = new ChatChannelService(this);
    }


    private void inicializarVistas() {
        campoNombreGrupo = findViewById(R.id.campoNombreGrupo);
        campoDescripcionGrupo = findViewById(R.id.campoDescripcionGrupo);
        imagenGrupoPreview = findViewById(R.id.imagenGrupoPreview);
        textoCantidadSeleccion = findViewById(R.id.textoCantidadSeleccion);
        listaAmigosGrupo = findViewById(R.id.listaAmigosGrupo);
        indicadorCargaGrupo = findViewById(R.id.indicadorCargaGrupo);
        botonCrearGrupoConfirmar = findViewById(R.id.botonCrearGrupoConfirmar);
        botonElegirFotoGrupo = findViewById(R.id.botonElegirFotoGrupo);
        botonQuitarFotoGrupo = findViewById(R.id.botonQuitarFotoGrupo);

        botonCrearGrupoConfirmar.setOnClickListener(v -> crearGrupo());
        botonElegirFotoGrupo.setOnClickListener(v -> openPhotoPicker());
        botonQuitarFotoGrupo.setOnClickListener(v -> clearSelectedPhoto());
        actualizarEstadoFotoGrupo();
        actualizarTextoSeleccion();
    }


    private void configurarListaAmigos() {
        adaptador = new GroupFriendSelectorAdapter(amigos, this::actualizarSeleccionAmigos);
        listaAmigosGrupo.setLayoutManager(new LinearLayoutManager(this));
        listaAmigosGrupo.setAdapter(adaptador);
    }

    // Configurar validacion de nombre en tiempo real
    private void configurarValidaciones() {
        campoNombreGrupo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validarFormulario();
                actualizarEstadoFotoGrupo();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // Actualizar la seleccion de amigos
    private void actualizarSeleccionAmigos(Set<String> seleccionActual) {
        miembrosSeleccionados.clear();
        miembrosSeleccionados.addAll(seleccionActual);
        actualizarTextoSeleccion();
        validarFormulario();
    }

    // Mostrar cantidad actual de amigos elegidos
    private void actualizarTextoSeleccion() {
        textoCantidadSeleccion.setText(getString(R.string.grupo_miembros_formato, miembrosSeleccionados.size() + 1));
    }

    // Validar formulario para habilitar boton de crear
    private void validarFormulario() {
        GroupValidator.ValidationResult validacion = GroupValidator.validarCreacionGrupo(
                campoNombreGrupo.getText() == null ? "" : campoNombreGrupo.getText().toString(),
                miembrosSeleccionados
        );
        botonCrearGrupoConfirmar.setEnabled(validacion.valido);
    }

    // Cargar amigos del usuario actual para seleccionar miembros
    private void cargarAmigos() {
        indicadorCargaGrupo.setVisibility(View.VISIBLE);
        if (auth.getCurrentUser() == null) {
            indicadorCargaGrupo.setVisibility(View.GONE);
            mostrarSesionInvalida();
            return;
        }
        String usuarioActualId = auth.getCurrentUser().getUid();

        firestore.collection("usuario")
                .document(usuarioActualId)
                .collection("Amigos")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        indicadorCargaGrupo.setVisibility(View.GONE);
                        amigos.clear();
                        adaptador.notifyDataSetChanged();
                        return;
                    }

                    List<String> amigosIds = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        amigosIds.add(doc.getId());
                    }
                    cargarDatosAmigosEnLotes(amigosIds);
                })
                .addOnFailureListener(e -> {
                    indicadorCargaGrupo.setVisibility(View.GONE);
                    Toast.makeText(this, R.string.grupo_error_carga_amigos, Toast.LENGTH_SHORT).show();
                });
    }

    // Carga perfiles de amigos en lotes para evitar limites de whereIn.
    private void cargarDatosAmigosEnLotes(List<String> amigosIds) {
        if (amigosIds == null || amigosIds.isEmpty()) {
            amigos.clear();
            adaptador.notifyDataSetChanged();
            indicadorCargaGrupo.setVisibility(View.GONE);
            return;
        }

        List<List<String>> lotes = crearLotes(amigosIds, 10);
        List<UserSearchData> amigosCargados = new ArrayList<>();
        Set<String> idsAgregados = new HashSet<>();
        final int[] pendientes = {lotes.size()};

        for (List<String> lote : lotes) {
            firestore.collection("usuario")
                    .whereIn("__name__", lote)
                    .get()
                    .addOnSuccessListener(amigoSnapshot -> {
                        for (DocumentSnapshot doc : amigoSnapshot.getDocuments()) {
                            if (idsAgregados.contains(doc.getId())) {
                                continue;
                            }
                            idsAgregados.add(doc.getId());
                            amigosCargados.add(mapearAmigo(doc));
                        }
                        pendientes[0] = pendientes[0] - 1;
                        if (pendientes[0] == 0) {
                            aplicarListaAmigos(amigosCargados, amigosIds);
                        }
                    })
                    .addOnFailureListener(e -> {
                        pendientes[0] = pendientes[0] - 1;
                        if (pendientes[0] == 0) {
                            aplicarListaAmigos(amigosCargados, amigosIds);
                        }
                    });
        }
    }

    // Separa una lista en lotes pequeños para consultas seguras.
    private List<List<String>> crearLotes(List<String> ids, int tamanoLote) {
        List<List<String>> lotes = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return lotes;
        }
        int inicio = 0;
        while (inicio < ids.size()) {
            int fin = Math.min(inicio + tamanoLote, ids.size());
            lotes.add(new ArrayList<>(ids.subList(inicio, fin)));
            inicio = fin;
        }
        return lotes;
    }

    // Convierte un documento de usuario en item de seleccion de amigo.
    private UserSearchData mapearAmigo(DocumentSnapshot doc) {
        String amigoId = doc.getId();
        String nick = doc.getString("Nick");
        if (nick == null || nick.trim().isEmpty()) {
            nick = doc.getString("nick");
        }
        String nombre = doc.getString("Nombre");
        String fotoPerfil = extraerFotoPerfil(doc.get("FotoPerfil"));
        return new UserSearchData(
                amigoId,
                nick == null || nick.trim().isEmpty() ? "Usuario" : nick,
                nombre == null ? "" : nombre,
                fotoPerfil
        );
    }

    // Extrae la foto de perfil sin fallar si viene como objeto o texto.
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

    // Aplica la lista final en el orden original de amistad.
    private void aplicarListaAmigos(List<UserSearchData> amigosCargados, List<String> ordenIds) {
        amigos.clear();
        if (amigosCargados != null && !amigosCargados.isEmpty()) {
            for (String amigoId : ordenIds) {
                for (UserSearchData amigo : amigosCargados) {
                    if (amigo != null && amigo.uid != null && amigo.uid.equals(amigoId)) {
                        amigos.add(amigo);
                        break;
                    }
                }
            }
        }
        adaptador.notifyDataSetChanged();
        indicadorCargaGrupo.setVisibility(View.GONE);
    }

    // Ejecutar creacion del grupo con datos del formulario
    private void crearGrupo() {
        if (auth.getCurrentUser() == null) {
            mostrarSesionInvalida();
            return;
        }

        String nombreGrupo = campoNombreGrupo.getText() == null ? "" : campoNombreGrupo.getText().toString();
        String descripcionGrupo = campoDescripcionGrupo.getText() == null ? "" : campoDescripcionGrupo.getText().toString();
        String imagenGrupo = dataUrlFotoGrupo == null ? "" : dataUrlFotoGrupo;

        GroupValidator.ValidationResult validacion = GroupValidator.validarCreacionGrupo(nombreGrupo, miembrosSeleccionados);
        if (!validacion.valido) {
            Toast.makeText(this, validacion.error, Toast.LENGTH_SHORT).show();
            return;
        }

        indicadorCargaGrupo.setVisibility(View.VISIBLE);
        botonCrearGrupoConfirmar.setEnabled(false);

        channelService.crearGrupoChat(nombreGrupo, descripcionGrupo, imagenGrupo, miembrosSeleccionados, new ChatChannelService.ChannelCallback() {
            @Override
            public void onExito(ChatChannelData canal) {
                indicadorCargaGrupo.setVisibility(View.GONE);
                Toast.makeText(CreateGroupActivity.this, R.string.grupo_creado_ok, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(CreateGroupActivity.this, ChatViewActivity.class);
                intent.putExtra("channelId", canal.getId());
                intent.putExtra("channelType", canal.getType());
                intent.putExtra("isGroupChat", true);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String error) {
                indicadorCargaGrupo.setVisibility(View.GONE);
                botonCrearGrupoConfirmar.setEnabled(true);
                Toast.makeText(CreateGroupActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Abre la galeria para elegir una foto de grupo.
    private void openPhotoPicker() {
        selectorFotoGrupo.launch("image/*");
    }

    // Procesa la foto elegida y abre el recortador.
    private void handlePhotoSelected(Uri uriSeleccionada) {
        if (uriSeleccionada == null) {
            return;
        }
        String tipoContenido = getContentResolver().getType(uriSeleccionada);
        if (!isAllowedImageType(tipoContenido)) {
            Toast.makeText(this, R.string.grupo_error_tipo_foto_no_valido, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent recorte = ImageCropperConfig.createIntent(
                this,
                uriSeleccionada,
                "foto-grupo.jpg",
                getString(R.string.recorte_titulo_foto_grupo),
                1,
                1
        );
        recortadorFotoGrupo.launch(recorte);
    }

    // Recibe la foto recortada y la guarda para enviar al crear grupo.
    private void handleCroppedPhotoSelected(Uri uriRecortada) {
        if (uriRecortada == null) {
            return;
        }
        try {
            String tipoContenido = getContentResolver().getType(uriRecortada);
            if (TextUtils.isEmpty(tipoContenido)) {
                tipoContenido = "image/jpeg";
            }
            byte[] bytesImagen = readBytesFromUri(uriRecortada);
            if (bytesImagen.length > TAMANO_MAXIMO_FOTO_BYTES) {
                Toast.makeText(this, R.string.grupo_error_tamano_foto_no_valido, Toast.LENGTH_SHORT).show();
                return;
            }

            bytesFotoGrupoSeleccionada = bytesImagen;
            tipoFotoGrupoSeleccionada = tipoContenido;
            dataUrlFotoGrupo = buildPhotoDataUrl(bytesFotoGrupoSeleccionada, tipoFotoGrupoSeleccionada);
            imagenGrupoPreview.setImageURI(uriRecortada);
            actualizarEstadoFotoGrupo();
        } catch (IOException error) {
            Toast.makeText(this, R.string.grupo_error_lectura_foto, Toast.LENGTH_SHORT).show();
        }
    }

    // Borra la foto seleccionada para dejar el grupo sin imagen
    private void clearSelectedPhoto() {
        bytesFotoGrupoSeleccionada = null;
        tipoFotoGrupoSeleccionada = null;
        dataUrlFotoGrupo = null;
        actualizarEstadoFotoGrupo();
    }

    // Actualiza la vista de foto y botones segun si hay imagen seleccionada.
    private void actualizarEstadoFotoGrupo() {
        boolean tieneFoto = !TextUtils.isEmpty(dataUrlFotoGrupo);
        if (tieneFoto) {
            botonElegirFotoGrupo.setText(R.string.grupo_boton_cambiar_foto);
            botonQuitarFotoGrupo.setVisibility(View.VISIBLE);
            imagenGrupoPreview.setVisibility(View.VISIBLE);
        } else {
            botonElegirFotoGrupo.setText(R.string.grupo_boton_elegir_foto);
            botonQuitarFotoGrupo.setVisibility(View.GONE);
            String inicial = extractInitialLetter(campoNombreGrupo.getText() == null
                    ? ""
                    : campoNombreGrupo.getText().toString());
            if (TextUtils.isEmpty(inicial)) {
                imagenGrupoPreview.setImageDrawable(null);
                imagenGrupoPreview.setVisibility(View.GONE);
            } else {
                imagenGrupoPreview.setImageBitmap(createInitialAvatarBitmap(inicial));
                imagenGrupoPreview.setVisibility(View.VISIBLE);
            }
        }
    }

    // Obtiene la letra inicial visible para la preview del grupo.
    private String extractInitialLetter(String nombreGrupo) {
        if (TextUtils.isEmpty(nombreGrupo)) {
            return "";
        }
        String nombreLimpio = nombreGrupo.trim();
        if (TextUtils.isEmpty(nombreLimpio)) {
            return "";
        }
        return nombreLimpio.substring(0, 1).toUpperCase();
    }

    // Crea una imagen circular con la letra inicial del grupo.
    private Bitmap createInitialAvatarBitmap(String inicial) {
        int tamanio = (int) (88f * getResources().getDisplayMetrics().density);
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
        pinturaTexto.setTextSize(tamanio * 0.46f);

        Paint.FontMetrics metricas = pinturaTexto.getFontMetrics();
        float ejeTextoY = radio - ((metricas.ascent + metricas.descent) / 2f);
        lienzoAvatar.drawText(inicial, radio, ejeTextoY, pinturaTexto);
        return bitmapAvatar;
    }

    // Convierte bytes de imagen a dataUrl para enviar al backend.
    private String buildPhotoDataUrl(byte[] bytesFoto, String tipoFoto) {
        if (bytesFoto == null || bytesFoto.length == 0 || TextUtils.isEmpty(tipoFoto)) {
            return "";
        }
        String base64 = Base64.encodeToString(bytesFoto, Base64.NO_WRAP);
        return "data:" + tipoFoto + ";base64," + base64;
    }

    // Lee bytes de un archivo seleccionado por el usuario.
    private byte[] readBytesFromUri(Uri uriArchivo) throws IOException {
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

    // Valida si la imagen seleccionada es un tipo permitido.
    private boolean isAllowedImageType(String tipoContenido) {
        return "image/jpeg".equals(tipoContenido)
                || "image/png".equals(tipoContenido)
                || "image/webp".equals(tipoContenido);
    }

    // Muestra estado de sesion invalida sin cerrar la pantalla de grupo.
    private void mostrarSesionInvalida() {
        Toast.makeText(this, "Sesion invalida. Vuelve a iniciar sesion.", Toast.LENGTH_SHORT).show();
        amigos.clear();
        miembrosSeleccionados.clear();
        if (adaptador != null) {
            adaptador.notifyDataSetChanged();
        }
        actualizarTextoSeleccion();
        if (botonCrearGrupoConfirmar != null) {
            botonCrearGrupoConfirmar.setEnabled(false);
        }
        if (indicadorCargaGrupo != null) {
            indicadorCargaGrupo.setVisibility(View.GONE);
        }
    }
}
