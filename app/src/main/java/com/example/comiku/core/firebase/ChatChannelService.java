package com.example.comiku.core.firebase;

import android.content.Context;
import android.util.Log;
import com.example.comiku.core.validation.GroupValidator;
import com.example.comiku.data.model.ChatChannelData;
import com.example.comiku.data.model.CreateChannelRequest;
import com.example.comiku.data.model.CreateChannelResponse;
import com.example.comiku.data.repository.StreamChatRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatChannelService {
    private static final String TAG = "ChatChannelService";
    private final FirebaseFirestore firestore;
    private final FirebaseAuth auth;
    private final StreamChatRepository streamRepository;

    public ChatChannelService(Object unused) {
        this.firestore = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        if (unused instanceof Context) {
            this.streamRepository = StreamChatRepository.obtenerInstancia((Context) unused);
        } else {
            this.streamRepository = null;
        }
    }

    // Obtener o crear un canal de chat entre dos usuarios
    public void obtenerOCrearCanalPrivado(String otroUsuarioId, ChannelCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();

        validarUsuarios(usuarioActualId, otroUsuarioId, new ValidationCallback() {
            @Override
            public void onResult(boolean esValido) {
                if (!esValido) {
                    callback.onError("No puedes crear un chat con este usuario");
                    return;
                }

                if (streamRepository != null) {
                    crearCanalPrivadoDesdeBackend(usuarioActualId, otroUsuarioId, callback);
                    return;
                }

                buscarCanalExistente(usuarioActualId, otroUsuarioId, new SearchChannelCallback() {
                    @Override
                    public void onCanalEncontrado(ChatChannelData canal) {
                        if (canal != null) {
                            callback.onExito(canal);
                        } else {
                            crearNuevoCanalPrivado(usuarioActualId, otroUsuarioId, callback);
                        }
                    }
                });
            }

            // Crea u obtiene un chat privado usando el backend para mantener paridad con React.
            private void crearCanalPrivadoDesdeBackend(String usuarioActualId, String otroUsuarioId, ChannelCallback callback) {
                List<String> miembros = new ArrayList<>();
                miembros.add(usuarioActualId);
                miembros.add(otroUsuarioId);

                CreateChannelRequest request = new CreateChannelRequest();
                request.setMembers(miembros);

                streamRepository.crearCanal(request, new StreamChatRepository.ChannelCallback() {
                    @Override
                    public void onExito(CreateChannelResponse.ChannelInfo channel) {
                        if (channel == null || channel.getId() == null || channel.getId().trim().isEmpty()) {
                            callback.onError("No se recibio un canal valido desde el backend");
                            return;
                        }

                        ChatChannelData canalData = new ChatChannelData();
                        canalData.setId(channel.getId());
                        canalData.setType("personal");
                        canalData.setMembers(miembros);
                        canalData.setEstado("active");
                        canalData.setCreatedAt(System.currentTimeMillis());
                        canalData.setUpdatedAt(System.currentTimeMillis());
                        canalData.setLastMessageAt(System.currentTimeMillis());
                        canalData.setUnreadCount(0);
                        callback.onExito(canalData);
                    }

                    @Override
                    public void onError(String errorApi) {
                        callback.onError(errorApi);
                    }
                });
            }
        });
    }

    // Crear un grupo de chat con amigos seleccionados
    public void crearGrupoChat(String nombreGrupo, String descripcionGrupo, String imagenGrupoUrl,
                               List<String> miembrosSeleccionados, ChannelCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();
        GroupValidator.ValidationResult validacion = GroupValidator.validarCreacionGrupo(nombreGrupo, miembrosSeleccionados);
        if (!validacion.valido) {
            callback.onError(validacion.error);
            return;
        }

        GroupValidator.ValidationResult validacionDescripcion = GroupValidator.validarDescripcionGrupo(descripcionGrupo);
        if (!validacionDescripcion.valido) {
            callback.onError(validacionDescripcion.error);
            return;
        }

        List<String> miembrosFinales = new ArrayList<>();
        miembrosFinales.add(usuarioActualId);
        for (String miembroId : miembrosSeleccionados) {
            if (miembroId != null && !miembroId.trim().isEmpty() && !miembrosFinales.contains(miembroId)) {
                miembrosFinales.add(miembroId);
            }
        }

        if (miembrosFinales.size() < 3) {
            callback.onError("El grupo debe tener al menos tres miembros");
            return;
        }

        validarMiembrosParaGrupo(usuarioActualId, miembrosFinales, new GroupValidationCallback() {
            @Override
            public void onResult(boolean esValido, String error) {
                if (!esValido) {
                    callback.onError(error);
                    return;
                }
                if (streamRepository != null) {
                    CreateChannelRequest request = new CreateChannelRequest();
                    request.setMembers(miembrosFinales);
                    request.setGroupName(nombreGrupo.trim());
                    request.setGroupDescription(descripcionGrupo == null ? "" : descripcionGrupo.trim());
                    request.setGroupImageUrl(imagenGrupoUrl == null || imagenGrupoUrl.trim().isEmpty() ? null : imagenGrupoUrl.trim());

                    streamRepository.crearCanal(request, new StreamChatRepository.ChannelCallback() {
                        @Override
                        public void onExito(CreateChannelResponse.ChannelInfo channel) {
                            crearNuevoCanalGrupoDesdeBackend(usuarioActualId, nombreGrupo, descripcionGrupo, imagenGrupoUrl, miembrosFinales, channel.getId(), callback);
                        }

                        @Override
                        public void onError(String errorApi) {
                            callback.onError(errorApi);
                        }
                    });
                } else {
                    crearNuevoCanalGrupo(usuarioActualId, nombreGrupo, descripcionGrupo, imagenGrupoUrl, miembrosFinales, callback);
                }
            }
        });
    }

    // Agregar miembros al grupo
    public void agregarMiembrosGrupo(String channelId, List<String> nuevosMiembros, OperationCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();
        obtenerCanal(channelId, new ChannelDataCallback() {
            @Override
            public void onExito(ChatChannelData canal) {
                if (!canal.isGroupChat()) {
                    callback.onError("Solo puedes agregar miembros en grupos");
                    return;
                }
                if (!canal.esAdmin(usuarioActualId)) {
                    callback.onError("Solo los administradores pueden agregar miembros");
                    return;
                }

                List<String> miembrosActuales = canal.getMembers() == null ? new ArrayList<>() : new ArrayList<>(canal.getMembers());
                List<String> candidatos = new ArrayList<>();
                for (String miembroId : nuevosMiembros) {
                    if (miembroId != null && !miembroId.trim().isEmpty() && !miembrosActuales.contains(miembroId)) {
                        candidatos.add(miembroId);
                    }
                }

                if (candidatos.isEmpty()) {
                    callback.onError("No hay miembros nuevos para agregar");
                    return;
                }

                List<String> miembrosConNuevos = new ArrayList<>(miembrosActuales);
                miembrosConNuevos.addAll(candidatos);

                validarMiembrosParaGrupo(usuarioActualId, candidatos, new GroupValidationCallback() {
                    @Override
                    public void onResult(boolean esValido, String error) {
                        if (!esValido) {
                            callback.onError(error);
                            return;
                        }

                        if (streamRepository != null) {
                            streamRepository.agregarMiembros(channelId, candidatos, new StreamChatRepository.ActionCallback() {
                                @Override
                                public void onExito(com.example.comiku.data.model.StreamActionResponse response) {
                                    persistirMiembrosGrupo(channelId, miembrosConNuevos, canal.getAdmins(), callback, "No se pudo agregar miembros");
                                }

                                @Override
                                public void onError(String errorApi) {
                                    callback.onError(errorApi);
                                }
                            });
                        } else {
                            persistirMiembrosGrupo(channelId, miembrosConNuevos, canal.getAdmins(), callback, "No se pudo agregar miembros");
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Eliminar miembro del grupo
    public void eliminarMiembroGrupo(String channelId, String miembroId, OperationCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();
        obtenerCanal(channelId, new ChannelDataCallback() {
            @Override
            public void onExito(ChatChannelData canal) {
                if (!canal.isGroupChat()) {
                    callback.onError("Solo puedes eliminar miembros en grupos");
                    return;
                }
                if (!canal.esAdmin(usuarioActualId)) {
                    callback.onError("Solo los administradores pueden eliminar miembros");
                    return;
                }

                List<String> miembros = canal.getMembers() == null ? new ArrayList<>() : new ArrayList<>(canal.getMembers());
                List<String> admins = canal.getAdmins() == null ? new ArrayList<>() : new ArrayList<>(canal.getAdmins());
                if (!miembros.contains(miembroId)) {
                    callback.onError("El usuario no pertenece al grupo");
                    return;
                }

                miembros.remove(miembroId);
                admins.remove(miembroId);
                if (miembros.size() <= 1) {
                    eliminarGrupoPorMiembrosMinimos(channelId, callback);
                    return;
                }

                garantizarAdmin(admins, miembros);
                if (streamRepository != null) {
                    streamRepository.quitarMiembro(channelId, miembroId, new StreamChatRepository.ActionCallback() {
                        @Override
                        public void onExito(com.example.comiku.data.model.StreamActionResponse response) {
                            persistirMiembrosGrupo(channelId, miembros, admins, callback, "No se pudo eliminar al miembro");
                        }

                        @Override
                        public void onError(String errorApi) {
                            callback.onError(errorApi);
                        }
                    });
                } else {
                    persistirMiembrosGrupo(channelId, miembros, admins, callback, "No se pudo eliminar al miembro");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Convertir miembro a administrador del grupo
    public void convertirMiembroEnAdmin(String channelId, String miembroId, OperationCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();
        obtenerCanal(channelId, new ChannelDataCallback() {
            @Override
            public void onExito(ChatChannelData canal) {
                if (!canal.isGroupChat()) {
                    callback.onError("Solo puedes cambiar roles en grupos");
                    return;
                }
                if (!canal.esAdmin(usuarioActualId)) {
                    callback.onError("Solo los administradores pueden asignar otros administradores");
                    return;
                }
                if (canal.getMembers() == null || !canal.getMembers().contains(miembroId)) {
                    callback.onError("El usuario no pertenece al grupo");
                    return;
                }

                List<String> admins = canal.getAdmins() == null ? new ArrayList<>() : new ArrayList<>(canal.getAdmins());
                if (!admins.contains(miembroId)) {
                    admins.add(miembroId);
                }

                if (streamRepository != null) {
                    streamRepository.hacerAdmin(channelId, miembroId, new StreamChatRepository.ActionCallback() {
                        @Override
                        public void onExito(com.example.comiku.data.model.StreamActionResponse response) {
                            persistirMiembrosGrupo(channelId, canal.getMembers(), admins, callback, "No se pudo actualizar administradores");
                        }

                        @Override
                        public void onError(String errorApi) {
                            callback.onError(errorApi);
                        }
                    });
                } else {
                    persistirMiembrosGrupo(channelId, canal.getMembers(), admins, callback, "No se pudo actualizar administradores");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Editar datos del grupo si el usuario es administrador
    public void actualizarDatosGrupo(String channelId, String nombreGrupo, String descripcionGrupo, String imagenGrupoUrl,
                                     OperationCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();
        GroupValidator.ValidationResult validacionNombre = GroupValidator.validarNombreGrupo(nombreGrupo);
        if (!validacionNombre.valido) {
            callback.onError(validacionNombre.error);
            return;
        }
        GroupValidator.ValidationResult validacionDescripcion = GroupValidator.validarDescripcionGrupo(descripcionGrupo);
        if (!validacionDescripcion.valido) {
            callback.onError(validacionDescripcion.error);
            return;
        }

        obtenerCanal(channelId, new ChannelDataCallback() {
            @Override
            public void onExito(ChatChannelData canal) {
                if (!canal.isGroupChat()) {
                    callback.onError("Solo puedes editar grupos");
                    return;
                }
                if (!canal.esAdmin(usuarioActualId)) {
                    callback.onError("Solo los administradores pueden editar el grupo");
                    return;
                }

                Map<String, Object> updates = new HashMap<>();
                updates.put("groupName", nombreGrupo.trim());
                updates.put("groupDescription", descripcionGrupo == null ? "" : descripcionGrupo.trim());
                updates.put("groupImageUrl", imagenGrupoUrl == null || imagenGrupoUrl.trim().isEmpty() ? null : imagenGrupoUrl.trim());
                updates.put("updatedAt", System.currentTimeMillis());

                if (streamRepository != null) {
                    streamRepository.actualizarGrupo(channelId, nombreGrupo.trim(), descripcionGrupo == null ? "" : descripcionGrupo.trim(),
                            imagenGrupoUrl == null || imagenGrupoUrl.trim().isEmpty() ? null : imagenGrupoUrl.trim(),
                            new StreamChatRepository.ActionCallback() {
                                @Override
                                public void onExito(com.example.comiku.data.model.StreamActionResponse response) {
                                    firestore.collection("streamChannels")
                                            .document(channelId)
                                            .update(updates)
                                            .addOnSuccessListener(v -> callback.onExito())
                                            .addOnFailureListener(e -> callback.onError("No se pudo actualizar el grupo"));
                                }

                                @Override
                                public void onError(String errorApi) {
                                    callback.onError(errorApi);
                                }
                            });
                } else {
                    firestore.collection("streamChannels")
                            .document(channelId)
                            .update(updates)
                            .addOnSuccessListener(v -> callback.onExito())
                            .addOnFailureListener(e -> callback.onError("No se pudo actualizar el grupo"));
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Permitir abandonar grupo con reasignacion de admin
    public void abandonarGrupo(String channelId, OperationCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();
        obtenerCanal(channelId, new ChannelDataCallback() {
            @Override
            public void onExito(ChatChannelData canal) {
                if (!canal.isGroupChat()) {
                    callback.onError("Solo puedes abandonar grupos");
                    return;
                }

                List<String> miembros = canal.getMembers() == null ? new ArrayList<>() : new ArrayList<>(canal.getMembers());
                List<String> admins = canal.getAdmins() == null ? new ArrayList<>() : new ArrayList<>(canal.getAdmins());
                if (!miembros.contains(usuarioActualId)) {
                    callback.onError("No perteneces a este grupo");
                    return;
                }
                boolean esAdminActual = admins.contains(usuarioActualId);
                if (esAdminActual && admins.size() <= 1 && miembros.size() > 2) {
                    callback.onError("No fue posible abandonar el grupo. Debe quedar al menos un administrador.");
                    return;
                }

                miembros.remove(usuarioActualId);
                admins.remove(usuarioActualId);

                if (miembros.size() <= 1) {
                    abandonarYEliminarGrupo(channelId, callback);
                    return;
                }

                garantizarAdmin(admins, miembros);
                if (streamRepository != null) {
                    streamRepository.abandonarGrupo(channelId, new StreamChatRepository.ActionCallback() {
                        @Override
                        public void onExito(com.example.comiku.data.model.StreamActionResponse response) {
                            persistirMiembrosGrupo(channelId, miembros, admins, callback, "No se pudo abandonar el grupo");
                        }

                        @Override
                        public void onError(String errorApi) {
                            callback.onError(errorApi);
                        }
                    });
                } else {
                    persistirMiembrosGrupo(channelId, miembros, admins, callback, "No se pudo abandonar el grupo");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Elimina el grupo cuando queda uno o cero miembros.
    private void eliminarGrupoPorMiembrosMinimos(String channelId, OperationCallback callback) {
        if (streamRepository != null) {
            streamRepository.borrarGrupo(channelId, new StreamChatRepository.ActionCallback() {
                @Override
                public void onExito(com.example.comiku.data.model.StreamActionResponse response) {
                    eliminarCanalFirestore(channelId, callback, "No se pudo eliminar el grupo");
                }

                @Override
                public void onError(String errorApi) {
                    callback.onError(errorApi);
                }
            });
            return;
        }
        eliminarCanalFirestore(channelId, callback, "No se pudo eliminar el grupo");
    }

    // Abandona y luego elimina el grupo cuando queda un solo miembro.
    private void abandonarYEliminarGrupo(String channelId, OperationCallback callback) {
        if (streamRepository != null) {
            streamRepository.abandonarGrupo(channelId, new StreamChatRepository.ActionCallback() {
                @Override
                public void onExito(com.example.comiku.data.model.StreamActionResponse response) {
                    eliminarCanalFirestore(channelId, callback, "No se pudo eliminar el grupo");
                }

                @Override
                public void onError(String errorApi) {
                    callback.onError(errorApi);
                }
            });
            return;
        }
        eliminarCanalFirestore(channelId, callback, "No se pudo eliminar el grupo");
    }

    // Borra el documento de canal en Firestore.
    private void eliminarCanalFirestore(String channelId, OperationCallback callback, String mensajeError) {
        firestore.collection("streamChannels")
                .document(channelId)
                .delete()
                .addOnSuccessListener(v -> callback.onExito())
                .addOnFailureListener(e -> callback.onError(mensajeError));
    }

    // Borrar grupo si el usuario es administrador
    public void borrarGrupo(String channelId, OperationCallback callback) {
        String usuarioActualId = auth.getCurrentUser().getUid();
        obtenerCanal(channelId, new ChannelDataCallback() {
            @Override
            public void onExito(ChatChannelData canal) {
                if (!canal.isGroupChat()) {
                    callback.onError("Solo puedes borrar grupos");
                    return;
                }
                if (!canal.esAdmin(usuarioActualId)) {
                    callback.onError("Solo los administradores pueden borrar el grupo");
                    return;
                }

                if (streamRepository != null) {
                    streamRepository.borrarGrupo(channelId, new StreamChatRepository.ActionCallback() {
                        @Override
                        public void onExito(com.example.comiku.data.model.StreamActionResponse response) {
                            firestore.collection("streamChannels")
                                    .document(channelId)
                                    .delete()
                                    .addOnSuccessListener(v -> callback.onExito())
                                    .addOnFailureListener(e -> callback.onError("No se pudo borrar el grupo"));
                        }

                        @Override
                        public void onError(String errorApi) {
                            callback.onError(errorApi);
                        }
                    });
                } else {
                    firestore.collection("streamChannels")
                            .document(channelId)
                            .delete()
                            .addOnSuccessListener(v -> callback.onExito())
                            .addOnFailureListener(e -> callback.onError("No se pudo borrar el grupo"));
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    // Obtener datos del canal
    public void obtenerCanal(String channelId, ChannelDataCallback callback) {
        firestore.collection("streamChannels")
                .document(channelId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onError("No se encontro el canal");
                        return;
                    }
                    ChatChannelData canal = doc.toObject(ChatChannelData.class);
                    if (canal == null) {
                        callback.onError("No se pudo leer el canal");
                        return;
                    }
                    canal.setId(doc.getId());
                    callback.onExito(canal);
                })
                .addOnFailureListener(e -> callback.onError("No se pudo cargar el canal"));
    }

    // Validar que ambos usuarios existan y no estén bloqueados
    private void validarUsuarios(String usuarioActualId, String otroUsuarioId, ValidationCallback callback) {
        firestore.collection("usuario")
                .document(otroUsuarioId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onResult(false);
                        return;
                    }

                    validarNoBloqueo(usuarioActualId, otroUsuarioId, new BlockCheckCallback() {
                        @Override
                        public void onResult(boolean noEstaBloqueado) {
                            callback.onResult(noEstaBloqueado);
                        }
                    });
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // Validar que miembros candidatos sean amigos del creador, existan y no tengan bloqueo
    private void validarMiembrosParaGrupo(String usuarioActualId, List<String> miembros, GroupValidationCallback callback) {
        List<String> candidatos = new ArrayList<>();
        for (String miembroId : miembros) {
            if (miembroId != null && !miembroId.equals(usuarioActualId)) {
                candidatos.add(miembroId);
            }
        }

        if (candidatos.isEmpty()) {
            callback.onResult(true, "");
            return;
        }

        validarCandidatoRecursivo(usuarioActualId, candidatos, 0, callback);
    }

    // Validar candidato actual y avanzar recursivamente
    private void validarCandidatoRecursivo(String usuarioActualId, List<String> candidatos, int indice,
                                           GroupValidationCallback callback) {
        if (indice >= candidatos.size()) {
            callback.onResult(true, "");
            return;
        }

        String candidatoId = candidatos.get(indice);
        firestore.collection("usuario")
                .document(candidatoId)
                .get()
                .addOnSuccessListener(docUsuario -> {
                    if (!docUsuario.exists()) {
                        callback.onResult(false, "Uno de los usuarios seleccionados no existe");
                        return;
                    }

                    firestore.collection("usuario")
                            .document(usuarioActualId)
                            .collection("Amigos")
                            .document(candidatoId)
                            .get()
                            .addOnSuccessListener(docAmigo -> {
                                if (!docAmigo.exists()) {
                                    callback.onResult(false, "Solo puedes agregar amigos al grupo");
                                    return;
                                }

                                validarNoBloqueo(usuarioActualId, candidatoId, new BlockCheckCallback() {
                                    @Override
                                    public void onResult(boolean noEstasBloqueado) {
                                        if (!noEstasBloqueado) {
                                            callback.onResult(false, "No puedes agregar usuarios bloqueados");
                                            return;
                                        }
                                        validarCandidatoRecursivo(usuarioActualId, candidatos, indice + 1, callback);
                                    }
                                });
                            })
                            .addOnFailureListener(e -> callback.onResult(false, "No se pudo validar amistades"));
                })
                .addOnFailureListener(e -> callback.onResult(false, "No se pudo validar usuarios"));
    }

    // Validar que no hay bloqueos entre usuarios
    private void validarNoBloqueo(String usuarioActualId, String otroUsuarioId, BlockCheckCallback callback) {
        firestore.collection("usuario")
                .document(usuarioActualId)
                .collection("UsuariosBloqueados")
                .document(otroUsuarioId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onResult(false);
                        return;
                    }

                    firestore.collection("usuario")
                            .document(otroUsuarioId)
                            .collection("UsuariosBloqueados")
                            .document(usuarioActualId)
                            .get()
                            .addOnSuccessListener(doc2 -> callback.onResult(!doc2.exists()))
                            .addOnFailureListener(e -> callback.onResult(false));
                })
                .addOnFailureListener(e -> callback.onResult(false));
    }

    // Buscar si ya existe un canal entre dos usuarios
    private void buscarCanalExistente(String usuarioActualId, String otroUsuarioId,
                                      SearchChannelCallback callback) {
        String channelId = generarIdCanal(usuarioActualId, otroUsuarioId);

        firestore.collection("streamChannels")
                .document(channelId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        ChatChannelData canal = doc.toObject(ChatChannelData.class);
                        callback.onCanalEncontrado(canal);
                    } else {
                        callback.onCanalEncontrado(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error buscando canal: " + e.getMessage());
                    callback.onCanalEncontrado(null);
                });
    }

    // Crear un nuevo canal de chat privado
    private void crearNuevoCanalPrivado(String usuarioActualId, String otroUsuarioId,
                                        ChannelCallback callback) {
        String channelId = generarIdCanal(usuarioActualId, otroUsuarioId);
        ChatChannelData canalData = new ChatChannelData();
        canalData.setId(channelId);
        canalData.setType("personal");
        canalData.setMembers(Arrays.asList(usuarioActualId, otroUsuarioId));
        canalData.setCreatedAt(System.currentTimeMillis());
        canalData.setUpdatedAt(System.currentTimeMillis());
        canalData.setEstado("active");
        canalData.setLastMessageAt(System.currentTimeMillis());
        canalData.setUnreadCount(0);

        firestore.collection("streamChannels")
                .document(channelId)
                .set(canalData.toMap())
                .addOnSuccessListener(v -> {
                    Log.d(TAG, "Canal creado: " + channelId);
                    callback.onExito(canalData);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creando canal: " + e.getMessage());
                    callback.onError("Error creando canal");
                });
    }

    // Crear grupo local usando id generado por backend Stream
    private void crearNuevoCanalGrupoDesdeBackend(String usuarioActualId, String nombreGrupo, String descripcionGrupo,
                                                  String imagenGrupoUrl, List<String> miembrosFinales, String channelId,
                                                  ChannelCallback callback) {
        ChatChannelData canalData = new ChatChannelData();
        canalData.setId(channelId);
        canalData.setType("group");
        canalData.setMembers(miembrosFinales);
        canalData.setAdmins(new ArrayList<>(Collections.singletonList(usuarioActualId)));
        canalData.setCreatedBy(usuarioActualId);
        canalData.setGroupName(nombreGrupo.trim());
        canalData.setGroupDescription(descripcionGrupo == null ? "" : descripcionGrupo.trim());
        canalData.setGroupImageUrl(imagenGrupoUrl == null || imagenGrupoUrl.trim().isEmpty() ? null : imagenGrupoUrl.trim());
        canalData.setCreatedAt(System.currentTimeMillis());
        canalData.setUpdatedAt(System.currentTimeMillis());
        canalData.setEstado("active");
        canalData.setLastMessageAt(System.currentTimeMillis());
        canalData.setUnreadCount(0);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("memberCount", miembrosFinales.size());
        metadata.put("createdFrom", "android");
        canalData.setMetadata(metadata);

        firestore.collection("streamChannels")
                .document(channelId)
                .set(canalData.toMap())
                .addOnSuccessListener(v -> callback.onExito(canalData))
                .addOnFailureListener(e -> callback.onError("No se pudo crear el grupo"));
    }

    // Crear un nuevo grupo de chat
    private void crearNuevoCanalGrupo(String usuarioActualId, String nombreGrupo, String descripcionGrupo,
                                      String imagenGrupoUrl, List<String> miembrosFinales, ChannelCallback callback) {
        String channelId = generarIdGrupo();
        ChatChannelData canalData = new ChatChannelData();
        canalData.setId(channelId);
        canalData.setType("group");
        canalData.setMembers(miembrosFinales);
        canalData.setAdmins(new ArrayList<>(Collections.singletonList(usuarioActualId)));
        canalData.setCreatedBy(usuarioActualId);
        canalData.setGroupName(nombreGrupo.trim());
        canalData.setGroupDescription(descripcionGrupo == null ? "" : descripcionGrupo.trim());
        canalData.setGroupImageUrl(imagenGrupoUrl == null || imagenGrupoUrl.trim().isEmpty() ? null : imagenGrupoUrl.trim());
        canalData.setCreatedAt(System.currentTimeMillis());
        canalData.setUpdatedAt(System.currentTimeMillis());
        canalData.setEstado("active");
        canalData.setLastMessageAt(System.currentTimeMillis());
        canalData.setUnreadCount(0);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("memberCount", miembrosFinales.size());
        metadata.put("createdFrom", "android");
        canalData.setMetadata(metadata);

        firestore.collection("streamChannels")
                .document(channelId)
                .set(canalData.toMap())
                .addOnSuccessListener(v -> callback.onExito(canalData))
                .addOnFailureListener(e -> callback.onError("No se pudo crear el grupo"));
    }

    // Generar ID de canal de forma determinista para dm
    private String generarIdCanal(String usuario1, String usuario2) {
        String[] usuarios = {usuario1, usuario2};
        Arrays.sort(usuarios);
        return "dm-" + usuarios[0] + "-" + usuarios[1];
    }

    // Persistir miembros y admins luego de una accion de grupo
    private void persistirMiembrosGrupo(String channelId, List<String> miembros, List<String> admins,
                                        OperationCallback callback, String errorMensaje) {
        List<String> miembrosFinales = miembros == null ? new ArrayList<>() : miembros;
        List<String> adminsFinales = admins == null ? new ArrayList<>() : admins;
        firestore.collection("streamChannels")
                .document(channelId)
                .update(
                        "members", miembrosFinales,
                        "admins", adminsFinales,
                        "updatedAt", System.currentTimeMillis()
                )
                .addOnSuccessListener(v -> callback.onExito())
                .addOnFailureListener(e -> callback.onError(errorMensaje));
    }

    // Generar ID de grupo
    private String generarIdGrupo() {
        return "group-" + System.currentTimeMillis() + "-" + Math.abs((int) (Math.random() * 100000));
    }

    // Garantizar que siempre exista al menos un admin
    private void garantizarAdmin(List<String> admins, List<String> miembros) {
        if (admins == null || miembros == null || miembros.isEmpty()) {
            return;
        }

        if (admins.isEmpty()) {
            List<String> miembrosOrdenados = new ArrayList<>(miembros);
            Collections.sort(miembrosOrdenados);
            admins.add(miembrosOrdenados.get(0));
            return;
        }

        List<String> adminsInvalidos = new ArrayList<>();
        for (String adminId : admins) {
            if (!miembros.contains(adminId)) {
                adminsInvalidos.add(adminId);
            }
        }
        admins.removeAll(adminsInvalidos);

        if (admins.isEmpty()) {
            List<String> miembrosOrdenados = new ArrayList<>(miembros);
            Collections.sort(miembrosOrdenados);
            admins.add(miembrosOrdenados.get(0));
        }
    }

    // Actualizar la informacion del ultimo mensaje
    public void actualizarUltimoMensaje(String channelId, String ultimoMensaje, long timestamp) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", ultimoMensaje);
        updates.put("lastMessageAt", timestamp);
        updates.put("updatedAt", timestamp);

        firestore.collection("streamChannels")
                .document(channelId)
                .update(updates)
                .addOnFailureListener(e -> Log.e(TAG, "Error actualizando canal: " + e.getMessage()));
    }

    // Marcar un canal como leido
    public void marcarCanalComoLeiido(String channelId) {
        firestore.collection("streamChannels")
                .document(channelId)
                .update("unreadCount", 0)
                .addOnFailureListener(e -> Log.e(TAG, "Error marcando canal: " + e.getMessage()));
    }

    public interface ChannelCallback {
        void onExito(ChatChannelData canal);

        void onError(String error);
    }

    public interface ValidationCallback {
        void onResult(boolean esValido);
    }

    public interface GroupValidationCallback {
        void onResult(boolean esValido, String error);
    }

    public interface BlockCheckCallback {
        void onResult(boolean noEstasBloqueado);
    }

    public interface SearchChannelCallback {
        void onCanalEncontrado(ChatChannelData canal);
    }

    public interface ChannelDataCallback {
        void onExito(ChatChannelData canal);

        void onError(String error);
    }

    public interface OperationCallback {
        void onExito();

        void onError(String error);
    }
}
