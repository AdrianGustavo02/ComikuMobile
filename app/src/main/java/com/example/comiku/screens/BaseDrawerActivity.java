package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.comiku.R;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Map;

public abstract class BaseDrawerActivity extends AppCompatActivity {
    private DrawerLayout layoutDrawer;
    private NavigationView vistaNavegacion;
    private ImageView imagenPerfilMenu;
    private TextView textoNickMenu;
    private ListenerRegistration escuchadorPerfilMenu;

    // Configura el contenedor comun con navbar y menu.
    protected void setupDrawerShell(@NonNull String tituloPantalla) {
        setContentView(R.layout.activity_with_navbar);
        bindDrawerViews();
        inflateScreenContent();
        setupToolbar(tituloPantalla);
        setupDrawerToggle();
        setupDrawerActions();
        listenProfileHeader();
    }

    // Devuelve el layout de contenido de cada pantalla.
    @LayoutRes
    protected abstract int getScreenLayoutId();

    // Permite ajustar la pantalla luego de inflar su layout.
    protected abstract void onScreenContentReady();

    // Resuelve vistas base del componente navbar.
    private void bindDrawerViews() {
        layoutDrawer = findViewById(R.id.layoutDrawerPrincipal);
        vistaNavegacion = findViewById(R.id.vistaNavegacionPrincipal);

        ViewEncabezadoMenu enlaceEncabezado = new ViewEncabezadoMenu(vistaNavegacion);
        imagenPerfilMenu = enlaceEncabezado.imagenPerfil;
        textoNickMenu = enlaceEncabezado.textoNick;
        enlaceEncabezado.contenedorPerfil.setOnClickListener(v -> {
            openProfile();
            layoutDrawer.closeDrawers();
        });
    }

    // Inserta el contenido especifico de la pantalla actual.
    private void inflateScreenContent() {
        FrameLayout contenedorContenido = findViewById(R.id.contenedorContenidoPrincipal);
        LayoutInflater.from(this).inflate(getScreenLayoutId(), contenedorContenido, true);
        onScreenContentReady();
    }

    // Configura el titulo y la barra superior.
    private void setupToolbar(@NonNull String tituloPantalla) {
        Toolbar barraSuperior = findViewById(R.id.barraSuperiorPrincipal);
        setSupportActionBar(barraSuperior);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(tituloPantalla);
        }
    }

    // Activa el comportamiento del boton hamburguesa.
    private void setupDrawerToggle() {
        Toolbar barraSuperior = findViewById(R.id.barraSuperiorPrincipal);
        ActionBarDrawerToggle alternadorDrawer = new ActionBarDrawerToggle(
                this,
                layoutDrawer,
                barraSuperior,
                R.string.menu_abrir,
                R.string.menu_cerrar
        );
        layoutDrawer.addDrawerListener(alternadorDrawer);
        alternadorDrawer.syncState();
    }

    // Conecta acciones del menu lateral comun.
    private void setupDrawerActions() {
        vistaNavegacion.setNavigationItemSelectedListener(item -> {
            int idItem = item.getItemId();
            if (idItem == R.id.menu_perfil) {
                openProfile();
            } else if (idItem == R.id.menu_biblioteca) {
                openLibrary();
            } else if (idItem == R.id.menu_deseados) {
                openWishlist();
            } else if (idItem == R.id.menu_listas_tematicas) {
                openThematicLists();
            } else if (idItem == R.id.menu_amigos) {
                openFriends();
            } else if (idItem == R.id.menu_chats) {
                openChats();
            } else if (idItem == R.id.menu_contactanos) {
                openContact();
            } else if (idItem == R.id.menu_actividades) {
                openActivities();
            } else if (idItem == R.id.menu_notificaciones) {
                openNotifications();
            } else if (idItem == R.id.menu_cerrar_sesion) {
                FirebaseAuth.getInstance().signOut();
                openLoginAndClearStack();
            }
            layoutDrawer.closeDrawers();
            return true;
        });
    }

    // Crea el menu de opciones con icono de busqueda.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_toolbar, menu);
        return true;
    }

    // Maneja acciones del menu de opciones.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.accion_busqueda) {
            openSearch();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Abre la pantalla de busqueda.
    private void openSearch() {
        Intent pantallaBusqueda = new Intent(this, SearchActivity.class);
        startActivity(pantallaBusqueda);
    }

    // Escucha cambios de foto y nick para el encabezado.
    private void listenProfileHeader() {
        FirebaseUser usuarioActual = FirebaseAuth.getInstance().getCurrentUser();
        if (usuarioActual == null) {
            openLoginAndClearStack();
            return;
        }

        DocumentReference referenciaPerfil = FirebaseFirestore.getInstance()
                .collection("usuario")
                .document(usuarioActual.getUid());

        escuchadorPerfilMenu = referenciaPerfil.addSnapshotListener((documento, error) -> {
            if (error != null || documento == null || !documento.exists()) {
                textoNickMenu.setText(getString(R.string.menu_perfil_default));
                imagenPerfilMenu.setImageResource(R.drawable.default_profile_picture);
                return;
            }

            String nick = String.valueOf(documento.getString("Nick"));
            if (TextUtils.isEmpty(nick) || "null".equalsIgnoreCase(nick)) {
                nick = getString(R.string.menu_perfil_default);
            }
            textoNickMenu.setText(nick);

            Object foto = documento.get("FotoPerfil");
            String dataUrlFoto = extractPhotoDataUrl(foto);
            Bitmap bitmapFoto = decodeDataUrl(dataUrlFoto);
            if (bitmapFoto == null) {
                imagenPerfilMenu.setImageResource(R.drawable.default_profile_picture);
                return;
            }
            imagenPerfilMenu.setImageBitmap(bitmapFoto);
        });
    }

    // Navega a perfil si no estamos ya en esa pantalla.
    private void openProfile() {
        if (this instanceof ProfileActivity) {
            return;
        }
        Intent pantallaPerfil = new Intent(this, ProfileActivity.class);
        startActivity(pantallaPerfil);
    }

    // Abre la pantalla de biblioteca.
    private void openLibrary() {
        if (this instanceof LibraryActivity) {
            return;
        }
        Intent pantallaBiblioteca = new Intent(this, LibraryActivity.class);
        startActivity(pantallaBiblioteca);
    }

    // Abre la pantalla de deseados.
    private void openWishlist() {
        if (this instanceof WishlistActivity) {
            return;
        }
        Intent pantallaDeseados = new Intent(this, WishlistActivity.class);
        startActivity(pantallaDeseados);
    }

    // Abre la pantalla de listas tematicas si no estamos ya en ella.
    private void openThematicLists() {
        if (this instanceof ThematicListsActivity) {
            return;
        }
        Intent pantallaListasTematicas = new Intent(this, ThematicListsActivity.class);
        startActivity(pantallaListasTematicas);
    }

    // Abre la pantalla de amigos si no estamos ya en ella.
    private void openFriends() {
        if (this instanceof FriendsActivity) {
            return;
        }
        Intent pantallaAmigos = new Intent(this, FriendsActivity.class);
        startActivity(pantallaAmigos);
    }

    // Abre la pantalla de chats si no estamos ya en ella.
    private void openChats() {
        if (getClass().equals(ChatsActivity.class)) {
            return;
        }
        Intent pantallaChats = new Intent(this, ChatsActivity.class);
        startActivity(pantallaChats);
    }

    // Abre la pantalla de contacto si no estamos ya en ella.
    private void openContact() {
        if (this instanceof ContactActivity) {
            return;
        }
        Intent pantallaContacto = new Intent(this, ContactActivity.class);
        startActivity(pantallaContacto);
    }

    // Abre la pantalla de actividades si no estamos ya en ella.
    private void openActivities() {
        if (this instanceof ActivitiesActivity) {
            return;
        }
        Intent pantallaActividades = new Intent(this, ActivitiesActivity.class);
        startActivity(pantallaActividades);
    }

    // Abre la pantalla de notificaciones si no estamos ya en ella.
    private void openNotifications() {
        if (this instanceof NotificationsActivity) {
            return;
        }
        Intent pantallaNotificaciones = new Intent(this, NotificationsActivity.class);
        startActivity(pantallaNotificaciones);
    }

    // Navega a login limpiando el historial.
    protected void openLoginAndClearStack() {
        Intent pantallaLogin = new Intent(this, LoginActivity.class);
        pantallaLogin.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(pantallaLogin);
        finish();
    }

    // Extrae el dataUrl de FotoPerfil.
    private String extractPhotoDataUrl(Object fotoPerfil) {
        if (!(fotoPerfil instanceof Map)) {
            return "";
        }
        Map<?, ?> mapaFoto = (Map<?, ?>) fotoPerfil;
        Object dataUrl = mapaFoto.get("dataUrl");
        if (dataUrl == null) {
            return "";
        }
        return String.valueOf(dataUrl);
    }

    // Convierte dataUrl a bitmap para mostrar imagen.
    private Bitmap decodeDataUrl(String dataUrl) {
        if (TextUtils.isEmpty(dataUrl)) {
            return null;
        }
        int indiceComa = dataUrl.indexOf(',');
        if (indiceComa < 0 || indiceComa >= dataUrl.length() - 1) {
            return null;
        }
        String base64 = dataUrl.substring(indiceComa + 1);
        try {
            byte[] bytesImagen = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytesImagen, 0, bytesImagen.length);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    // Libera el escuchador del encabezado al cerrar.
    @Override
    protected void onDestroy() {
        if (escuchadorPerfilMenu != null) {
            escuchadorPerfilMenu.remove();
        }
        super.onDestroy();
    }

    // Agrupa vistas del encabezado para simplificar la lectura.
    private static final class ViewEncabezadoMenu {
        private final android.view.View contenedorPerfil;
        private final ImageView imagenPerfil;
        private final TextView textoNick;

        // Busca las vistas del encabezado en un solo paso.
        private ViewEncabezadoMenu(@NonNull NavigationView vistaNavegacion) {
            android.view.View encabezado = vistaNavegacion.getHeaderView(0);
            contenedorPerfil = encabezado.findViewById(R.id.contenedorPerfilMenu);
            imagenPerfil = encabezado.findViewById(R.id.imagenPerfilMenu);
            textoNick = encabezado.findViewById(R.id.textoNickMenu);
        }
    }
}
