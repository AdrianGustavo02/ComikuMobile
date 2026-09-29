package com.example.comiku.screens;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.comiku.R;
import com.example.comiku.core.ui.ThematicListUiHelper;
import com.example.comiku.core.ui.ToastUtils;
import com.example.comiku.data.model.AppNotificationData;
import com.example.comiku.data.model.NotificationPageData;
import com.example.comiku.data.model.NotificationType;
import com.example.comiku.data.repository.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import android.graphics.Typeface;

public class NotificationsActivity extends BaseDrawerActivity {
    private static final int TAMANO_PAGINA = 15;

    private ProgressBar barraCarga;
    private TextView textoEstado;
    private LinearLayout contenedorNotificaciones;
    private Button botonCargarMas;
    private Button botonMarcarTodas;

    private String uidActual = "";
    private final List<AppNotificationData> notificaciones = new ArrayList<>();
    private String cursorId;
    private Date cursorFecha;
    private boolean hayMas;
    private boolean cargando;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser usuario = FirebaseAuth.getInstance().getCurrentUser();
        if (usuario == null) {
            openLoginAndClearStack();
            return;
        }

        uidActual = usuario.getUid();
        setupDrawerShell(getString(R.string.notificaciones_titulo));
    }


    @Override
    protected int getScreenLayoutId() {
        return R.layout.activity_notifications;
    }

    // Prepara vistas y acciones iniciales.
    @Override
    protected void onScreenContentReady() {
        bindViews();
        botonCargarMas.setOnClickListener(v -> loadNotifications(false));
        botonMarcarTodas.setOnClickListener(v -> markAllAsRead());
        loadNotifications(true);
    }

    // Recarga notificaciones al volver a la pantalla.
    @Override
    protected void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(uidActual)) {
            loadNotifications(true);
        }
    }

    // Vincula las vistas del layout.
    private void bindViews() {
        barraCarga = findViewById(R.id.barraCargaNotificaciones);
        textoEstado = findViewById(R.id.textoEstadoNotificaciones);
        contenedorNotificaciones = findViewById(R.id.contenedorNotificaciones);
        botonCargarMas = findViewById(R.id.botonCargarMasNotificaciones);
        botonMarcarTodas = findViewById(R.id.botonMarcarTodasLeidasNotificaciones);
    }

    // Carga una pagina de notificaciones.
    private void loadNotifications(boolean reiniciar) {
        if (cargando) {
            return;
        }

        if (reiniciar) {
            notificaciones.clear();
            cursorId = null;
            cursorFecha = null;
            hayMas = false;
            contenedorNotificaciones.removeAllViews();
        }

        cargando = true;
        barraCarga.setVisibility(View.VISIBLE);
        textoEstado.setVisibility(View.VISIBLE);
        textoEstado.setText(getString(R.string.notificaciones_cargando));
        botonCargarMas.setEnabled(false);
        botonMarcarTodas.setEnabled(false);

        NotificationRepository.getNotificationsPage(
                        uidActual,
                        TAMANO_PAGINA,
                        reiniciar ? null : cursorId,
                        reiniciar ? null : cursorFecha
                )
                .addOnSuccessListener(this::applyNotificationPage)
                .addOnFailureListener(error -> {
                    textoEstado.setText(
                            error.getMessage() != null
                                    ? error.getMessage()
                                    : getString(R.string.notificaciones_error_carga)
                    );
                    textoEstado.setVisibility(View.VISIBLE);
                })
                .addOnCompleteListener(unused -> {
                    cargando = false;
                    barraCarga.setVisibility(View.GONE);
                    botonCargarMas.setEnabled(true);
                    botonMarcarTodas.setEnabled(true);
                    refreshUnreadCountLabel();
                });
    }

    // Aplica la pagina recibida y evita duplicados visibles.
    private void applyNotificationPage(NotificationPageData pagina) {
        if (pagina == null) {
            textoEstado.setText(getString(R.string.notificaciones_error_carga));
            textoEstado.setVisibility(View.VISIBLE);
            return;
        }

        Set<String> ids = new HashSet<>();
        for (AppNotificationData item : notificaciones) {
            ids.add(item.id);
        }
        for (AppNotificationData item : pagina.notificaciones) {
            if (item == null || ids.contains(item.id)) {
                continue;
            }
            notificaciones.add(item);
            ids.add(item.id);
        }

        cursorId = pagina.cursorId;
        cursorFecha = pagina.cursorFecha;
        hayMas = pagina.hayMas;
        renderNotifications();
    }

    // Muestra la lista de notificaciones o estado vacio.
    private void renderNotifications() {
        contenedorNotificaciones.removeAllViews();
        if (notificaciones.isEmpty()) {
            textoEstado.setText(getString(R.string.notificaciones_vacia));
            textoEstado.setVisibility(View.VISIBLE);
            botonCargarMas.setVisibility(View.GONE);
            return;
        }

        textoEstado.setVisibility(View.GONE);
        for (int i = 0; i < notificaciones.size(); i++) {
            AppNotificationData notificacion = notificaciones.get(i);
            contenedorNotificaciones.addView(buildNotificationCard(notificacion, i < notificaciones.size() - 1));
        }
        botonCargarMas.setVisibility(hayMas ? View.VISIBLE : View.GONE);
    }

    // Marca todas las notificaciones como leidas.
    private void markAllAsRead() {
        botonMarcarTodas.setEnabled(false);
        NotificationRepository.markAllNotificationsAsRead(uidActual)
                .addOnSuccessListener(unused -> {
                    for (int i = 0; i < notificaciones.size(); i++) {
                        AppNotificationData actual = notificaciones.get(i);
                        if (actual.leida) {
                            continue;
                        }
                        notificaciones.set(i, new AppNotificationData(
                                actual.id,
                                actual.receptorId,
                                actual.actorId,
                                actual.actorNick,
                                actual.actorFotoPerfilDataUrl,
                                actual.tipoNotificacion,
                                true,
                                actual.fechaCreacion,
                                actual.metadata
                        ));
                    }
                    renderNotifications();
                    refreshUnreadCountLabel();
                })
                .addOnFailureListener(error -> ToastUtils.showTextToast(
                        this,
                        getString(R.string.notificaciones_error_marcar_todas),
                        Toast.LENGTH_SHORT
                ))
                .addOnCompleteListener(unused -> botonMarcarTodas.setEnabled(true));
    }

    // Actualiza el texto del boton para marcar todas las notificaciones como leidas
    private void refreshUnreadCountLabel() {
        NotificationRepository.getUnreadNotificationsCount(uidActual)
                .addOnSuccessListener(cantidadNoLeidas -> {
                    if (cantidadNoLeidas <= 0) {
                        botonMarcarTodas.setText(getString(R.string.notificaciones_marcar_todas));
                        botonMarcarTodas.setEnabled(false);
                        return;
                    }
                    botonMarcarTodas.setEnabled(true);
                    botonMarcarTodas.setText(getString(
                            R.string.notificaciones_marcar_todas_con_total,
                            cantidadNoLeidas
                    ));
                });
    }

    // Crea una card de notificacion.
    private View buildNotificationCard(AppNotificationData notificacion, boolean mostrarDivisor) {
        LinearLayout contenedorItem = new LinearLayout(this);
        contenedorItem.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams paramsContenedor = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        contenedorItem.setLayoutParams(paramsContenedor);

        LinearLayout tarjeta = new LinearLayout(this);
        tarjeta.setOrientation(LinearLayout.HORIZONTAL);
        tarjeta.setGravity(Gravity.CENTER_VERTICAL);
        tarjeta.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        tarjeta.setBackgroundResource(notificacion.leida
                ? R.drawable.bg_notification_item_read
                : R.drawable.bg_notification_item_unread);
        tarjeta.setClickable(true);
        tarjeta.setFocusable(true);

        RoundedImageView avatar = ThematicListUiHelper.createCircularProfileImage(this, 48);
        LinearLayout.LayoutParams paramsAvatar = new LinearLayout.LayoutParams(dpToPx(48), dpToPx(48));
        paramsAvatar.setMarginEnd(dpToPx(12));
        avatar.setLayoutParams(paramsAvatar);
        avatar.setCircular(true);
        Bitmap bitmap = ThematicListUiHelper.decodeDataUrl(notificacion.actorFotoPerfilDataUrl);
        if (bitmap != null) {
            avatar.setImageBitmap(bitmap);
        }
        tarjeta.addView(avatar);

        LinearLayout columna = new LinearLayout(this);
        columna.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams paramsColumna = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        );
        columna.setLayoutParams(paramsColumna);

        TextView textoContenido = new TextView(this);
        textoContenido.setText(buildNotificationText(notificacion));
        textoContenido.setTextColor(getColor(R.color.carousel_subtitle));
        textoContenido.setTextSize(16f);
        columna.addView(textoContenido);

        TextView textoFecha = new TextView(this);
        textoFecha.setText(formatRelativeDate(notificacion.fechaCreacion));
        textoFecha.setTextSize(12f);
        textoFecha.setTextColor(getColor(R.color.carousel_subtitle));
        columna.addView(textoFecha);

        tarjeta.addView(columna);

        if (!notificacion.leida) {
            View puntoNoLeida = new View(this);
            LinearLayout.LayoutParams paramsPunto = new LinearLayout.LayoutParams(
                    dpToPx(10),
                    dpToPx(10)
            );
            paramsPunto.setMarginStart(dpToPx(10));
            puntoNoLeida.setLayoutParams(paramsPunto);
            puntoNoLeida.setBackgroundResource(R.drawable.bg_notification_unread_dot);
            tarjeta.addView(puntoNoLeida);
        }

        tarjeta.setOnClickListener(v -> handleNotificationClick(notificacion));
        contenedorItem.addView(tarjeta);

        if (mostrarDivisor) {
            View divisor = new View(this);
            divisor.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(1)
            ));
            divisor.setBackgroundColor(android.graphics.Color.parseColor("#14ffffff"));
            contenedorItem.addView(divisor);
        }

        return contenedorItem;
    }

    // Construye el texto legible segun el tipo de notificacion.
    private CharSequence buildNotificationText(AppNotificationData notificacion) {
        String nick = !TextUtils.isEmpty(notificacion.actorNick)
                ? notificacion.actorNick
                : getString(R.string.amigos_nick_desconocido);
        String mensaje;
        if (notificacion.tipoNotificacion == NotificationType.FRIEND_REQUEST) {
            mensaje = getString(R.string.notificaciones_texto_solicitud_amistad, nick);
            return buildNotificationTextWithBoldNick(nick, mensaje);
        }
        if (notificacion.tipoNotificacion == NotificationType.THEMATIC_LIST_LIKE) {
            mensaje = getString(R.string.notificaciones_texto_like_lista, nick);
            return buildNotificationTextWithBoldNick(nick, mensaje);
        }
        if (notificacion.tipoNotificacion == NotificationType.THEMATIC_LIST_COMMENT) {
            mensaje = getString(R.string.notificaciones_texto_comentario_lista, nick);
            return buildNotificationTextWithBoldNick(nick, mensaje);
        }
        if (notificacion.tipoNotificacion == NotificationType.ACTIVITY_LIKE
                || notificacion.tipoNotificacion == NotificationType.ACTIVITY_COMMENT) {
            String verbo = notificacion.tipoNotificacion == NotificationType.ACTIVITY_LIKE
                    ? getString(R.string.notificaciones_verbo_like)
                    : getString(R.string.notificaciones_verbo_comentario);
            String tipoActividad = getMetadataText(notificacion, "activityType");
            if ("library_add".equals(tipoActividad)) {
                mensaje = getString(R.string.notificaciones_texto_actividad_biblioteca, nick, verbo);
                return buildNotificationTextWithBoldNick(nick, mensaje);
            }
            if ("wishlist_add".equals(tipoActividad)) {
                mensaje = getString(R.string.notificaciones_texto_actividad_deseados, nick, verbo);
                return buildNotificationTextWithBoldNick(nick, mensaje);
            }
            if ("thematic_list_create".equals(tipoActividad)) {
                mensaje = getString(R.string.notificaciones_texto_actividad_lista_creada, nick, verbo);
                return buildNotificationTextWithBoldNick(nick, mensaje);
            }
            mensaje = getString(R.string.notificaciones_texto_actividad_generica, nick, verbo);
            return buildNotificationTextWithBoldNick(nick, mensaje);
        }
        return getString(R.string.notificaciones_texto_generico);
    }

    // Destaca el nick del usuario al inicio del texto.
    private CharSequence buildNotificationTextWithBoldNick(String nick, String mensaje) {
        SpannableStringBuilder texto = new SpannableStringBuilder(mensaje);
        if (!TextUtils.isEmpty(nick) && mensaje.startsWith(nick)) {
            texto.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    0,
                    nick.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        return texto;
    }

    // Maneja click de notificacion: marca leida y redirige.
    private void handleNotificationClick(AppNotificationData notificacion) {
        if (!notificacion.leida) {
            NotificationRepository.markNotificationAsRead(notificacion.id)
                    .addOnSuccessListener(unused -> updateNotificationAsRead(notificacion.id))
                    .addOnFailureListener(error -> {
                    });
        }
        openNotificationTarget(notificacion);
    }

    // Actualiza una notificacion como leida.
    private void updateNotificationAsRead(String notificationId) {
        for (int i = 0; i < notificaciones.size(); i++) {
            AppNotificationData item = notificaciones.get(i);
            if (!TextUtils.equals(item.id, notificationId)) {
                continue;
            }
            notificaciones.set(i, new AppNotificationData(
                    item.id,
                    item.receptorId,
                    item.actorId,
                    item.actorNick,
                    item.actorFotoPerfilDataUrl,
                    item.tipoNotificacion,
                    true,
                    item.fechaCreacion,
                    item.metadata
            ));
            break;
        }
        renderNotifications();
        refreshUnreadCountLabel();
    }

    // Navega al destino segun el tipo de notificacion.
    private void openNotificationTarget(AppNotificationData notificacion) {
        if (notificacion.tipoNotificacion == NotificationType.FRIEND_REQUEST) {
            startActivity(new Intent(this, FriendsActivity.class));
            return;
        }
        if (notificacion.tipoNotificacion == NotificationType.THEMATIC_LIST_LIKE
                || notificacion.tipoNotificacion == NotificationType.THEMATIC_LIST_COMMENT) {
            String listId = getMetadataText(notificacion, "listId");
            if (!TextUtils.isEmpty(listId)) {
                Intent pantallaLista = new Intent(this, ThematicListDetailActivity.class);
                pantallaLista.putExtra(ThematicListDetailActivity.EXTRA_LIST_ID, listId);
                startActivity(pantallaLista);
            }
            return;
        }
        if (notificacion.tipoNotificacion == NotificationType.ACTIVITY_LIKE
                || notificacion.tipoNotificacion == NotificationType.ACTIVITY_COMMENT) {
            String activityId = getMetadataText(notificacion, "activityId");
            Intent pantallaActividad = new Intent(this, ActivitiesActivity.class);
            if (!TextUtils.isEmpty(activityId)) {
                pantallaActividad.putExtra(ActivitiesActivity.EXTRA_ACTIVITY_ID, activityId);
            }
            startActivity(pantallaActividad);
        }
    }

    // Obtiene texto de metadata con una clave puntual.
    private String getMetadataText(AppNotificationData notificacion, String key) {
        if (notificacion == null || notificacion.metadata == null || TextUtils.isEmpty(key)) {
            return "";
        }
        Object value = notificacion.metadata.get(key);
        return value != null ? String.valueOf(value) : "";
    }

    // Formatea fecha a texto relativo simple.
    private String formatRelativeDate(Date fecha) {
        if (fecha == null) {
            return getString(R.string.actividades_fecha_no_disponible);
        }
        long diffMs = System.currentTimeMillis() - fecha.getTime();
        long minutos = diffMs / 60_000L;
        long horas = diffMs / 3_600_000L;
        long dias = diffMs / 86_400_000L;
        if (minutos < 1L) {
            return getString(R.string.notificaciones_hace_poco);
        }
        if (minutos < 60L) {
            return getString(R.string.notificaciones_hace_minutos, minutos);
        }
        if (horas < 24L) {
            return getString(R.string.notificaciones_hace_horas, horas);
        }
        if (dias < 7L) {
            return getString(R.string.notificaciones_hace_dias, dias);
        }
        return new SimpleDateFormat("dd/MM", Locale.getDefault()).format(fecha);
    }

    // Convierte dp a pixeles para vistas dinamicas.
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
