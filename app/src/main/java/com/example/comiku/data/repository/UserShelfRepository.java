package com.example.comiku.data.repository;

import android.text.TextUtils;

import com.example.comiku.data.model.ComicRecommendationData;
import com.example.comiku.data.model.HomeRecommendationsData;
import com.example.comiku.data.model.ComicDetailData;
import com.example.comiku.data.model.ComicMissingVolumesData;
import com.example.comiku.data.model.HomeRecentVolumesData;
import com.example.comiku.data.model.HomeMissingVolumesData;
import com.example.comiku.data.model.LibraryReadingEntryData;
import com.example.comiku.data.model.LibraryVolumeReadingData;
import com.example.comiku.data.model.MissingVolumeData;
import com.example.comiku.data.model.RecentLibraryVolumeData;
import com.example.comiku.data.model.UserShelfComicGroupData;
import com.example.comiku.data.model.UserShelfVolumeItemData;
import com.example.comiku.data.model.VolumeDetailData;
import com.example.comiku.data.model.VolumeShelfState;
import com.example.comiku.data.repository.ComicDetailRepository;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.TimeZone;

public final class UserShelfRepository {
    private static final String USER_COLLECTION = "usuario";
    private static final String LIBRARY_COLLECTION = "biblioteca";
    private static final String WISHLIST_COLLECTION = "listaDeseados";
    private static final String LIST_ROOT_DOCUMENT = "coleccion";
    private static final String COMICS_SUBCOLLECTION = "comics";
    private static final String VOLUMES_SUBCOLLECTION = "tomos";

    private UserShelfRepository() {
    }

    // Revisa si un tomo esta en la biblioteca o en la lista de deseados.
    public static Task<VolumeShelfState> getVolumeShelfState(String uidUsuario, String comicId, String tomoId) {
        ensureFirestoreReady();
        validateIds(uidUsuario, comicId, tomoId);

        Task<DocumentSnapshot> bibliotecaTask = getVolumeReference(uidUsuario, LIBRARY_COLLECTION, comicId, tomoId).get();
        Task<DocumentSnapshot> deseadosTask = getVolumeReference(uidUsuario, WISHLIST_COLLECTION, comicId, tomoId).get();

        return Tasks.whenAllComplete(bibliotecaTask, deseadosTask).continueWith(task -> {
            DocumentSnapshot documentoBiblioteca = bibliotecaTask.isSuccessful() ? bibliotecaTask.getResult() : null;
            DocumentSnapshot documentoDeseados = deseadosTask.isSuccessful() ? deseadosTask.getResult() : null;
            return new VolumeShelfState(
                    documentoBiblioteca != null && documentoBiblioteca.exists(),
                    documentoDeseados != null && documentoDeseados.exists()
            );
        });
    }

    // Alterna la presencia de un tomo en la biblioteca del usuario.
    public static Task<VolumeShelfState> toggleVolumeInLibrary(String uidUsuario, String comicId, String tomoId, Long isbn) {
        return toggleVolumeInList(uidUsuario, comicId, tomoId, LIBRARY_COLLECTION, isbn);
    }

    // Alterna la presencia de un tomo en la lista de deseados del usuario.
    public static Task<VolumeShelfState> toggleVolumeInWishlist(String uidUsuario, String comicId, String tomoId, Long isbn) {
        return toggleVolumeInList(uidUsuario, comicId, tomoId, WISHLIST_COLLECTION, isbn);
    }

    // Alterna la presencia de un tomo en una lista del usuario.
    private static Task<VolumeShelfState> toggleVolumeInList(String uidUsuario, String comicId, String tomoId, String targetList, Long isbn) {
        ensureFirestoreReady();
        validateIds(uidUsuario, comicId, tomoId);

        if (!LIBRARY_COLLECTION.equals(targetList) && !WISHLIST_COLLECTION.equals(targetList)) {
            throw new IllegalArgumentException("Lista invalida.");
        }

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        String sourceList = LIBRARY_COLLECTION.equals(targetList) ? WISHLIST_COLLECTION : LIBRARY_COLLECTION;

        DocumentReference targetComicReference = getComicReference(uidUsuario, targetList, comicId);
        DocumentReference sourceComicReference = getComicReference(uidUsuario, sourceList, comicId);
        DocumentReference targetVolumeReference = getVolumeReference(uidUsuario, targetList, comicId, tomoId);
        DocumentReference sourceVolumeReference = getVolumeReference(uidUsuario, sourceList, comicId, tomoId);
        DocumentReference userReference = firestore.collection(USER_COLLECTION).document(uidUsuario);

        Task<DocumentSnapshot> targetVolumeTask = targetVolumeReference.get();
        Task<DocumentSnapshot> sourceVolumeTask = sourceVolumeReference.get();
        Task<QuerySnapshot> targetVolumesTask = firestore.collection(USER_COLLECTION)
                .document(uidUsuario)
                .collection(targetList)
                .document(LIST_ROOT_DOCUMENT)
                .collection(COMICS_SUBCOLLECTION)
                .document(comicId)
                .collection(VOLUMES_SUBCOLLECTION)
                .get();
        Task<QuerySnapshot> sourceVolumesTask = firestore.collection(USER_COLLECTION)
                .document(uidUsuario)
                .collection(sourceList)
                .document(LIST_ROOT_DOCUMENT)
                .collection(COMICS_SUBCOLLECTION)
                .document(comicId)
                .collection(VOLUMES_SUBCOLLECTION)
                .get();

        return Tasks.whenAllComplete(targetVolumeTask, sourceVolumeTask, targetVolumesTask, sourceVolumesTask)
                .continueWithTask(task -> {
                    DocumentSnapshot targetVolumeSnapshot = targetVolumeTask.isSuccessful() ? targetVolumeTask.getResult() : null;
                    DocumentSnapshot sourceVolumeSnapshot = sourceVolumeTask.isSuccessful() ? sourceVolumeTask.getResult() : null;
                    QuerySnapshot targetVolumesSnapshot = targetVolumesTask.isSuccessful() ? targetVolumesTask.getResult() : null;
                    QuerySnapshot sourceVolumesSnapshot = sourceVolumesTask.isSuccessful() ? sourceVolumesTask.getResult() : null;

                    boolean shouldRemoveFromTarget = targetVolumeSnapshot != null && targetVolumeSnapshot.exists();
                    boolean shouldRemoveFromSource = sourceVolumeSnapshot != null && sourceVolumeSnapshot.exists();

                    boolean willAddToLibrary = LIBRARY_COLLECTION.equals(targetList) && !shouldRemoveFromTarget;
                    boolean willRemoveFromLibrary =
                            (LIBRARY_COLLECTION.equals(targetList) && shouldRemoveFromTarget)
                                    || (!LIBRARY_COLLECTION.equals(targetList) && shouldRemoveFromSource);

                    int userTotalComicsDelta = 0;
                    int userTotalTomosDelta = 0;

                    if (willAddToLibrary) {
                        int volumenesAntes = targetVolumesSnapshot != null ? targetVolumesSnapshot.size() : 0;
                        userTotalTomosDelta += 1;
                        if (volumenesAntes == 0) {
                            userTotalComicsDelta += 1;
                        }
                    }

                    if (willRemoveFromLibrary) {
                        int volumenesAntes = LIBRARY_COLLECTION.equals(targetList)
                                ? (targetVolumesSnapshot != null ? targetVolumesSnapshot.size() : 0)
                                : (sourceVolumesSnapshot != null ? sourceVolumesSnapshot.size() : 0);
                        userTotalTomosDelta -= 1;
                        if (volumenesAntes == 1) {
                            userTotalComicsDelta -= 1;
                        }
                    }

                    WriteBatch batch = firestore.batch();

                    if (shouldRemoveFromTarget) {
                        batch.delete(targetVolumeReference);
                    } else {
                        batch.set(targetComicReference, createComicMetadata(), SetOptions.merge());

                        if (LIBRARY_COLLECTION.equals(targetList)) {
                            batch.set(targetVolumeReference, createLibraryPayload(comicId, tomoId), SetOptions.merge());
                        } else {
                            batch.set(targetVolumeReference, createWishlistPayload(comicId, tomoId), SetOptions.merge());
                        }
                    }

                    if (!shouldRemoveFromTarget && shouldRemoveFromSource) {
                        batch.delete(sourceVolumeReference);
                    }

                    if (userTotalComicsDelta != 0 || userTotalTomosDelta != 0) {
                        Map<String, Object> updatePayload = new HashMap<>();
                        if (userTotalTomosDelta != 0) {
                            updatePayload.put("totalTomos", FieldValue.increment(userTotalTomosDelta));
                        }
                        if (userTotalComicsDelta != 0) {
                            updatePayload.put("totalComics", FieldValue.increment(userTotalComicsDelta));
                        }
                        batch.update(userReference, updatePayload);
                    }

                    VolumeShelfState resultadoEstado = new VolumeShelfState(
                            LIBRARY_COLLECTION.equals(targetList)
                                    ? !shouldRemoveFromTarget
                                    : (shouldRemoveFromTarget ? shouldRemoveFromSource : false),
                            LIBRARY_COLLECTION.equals(targetList)
                                    ? (shouldRemoveFromTarget ? shouldRemoveFromSource : false)
                                    : !shouldRemoveFromTarget
                    );

                    Task<Void> targetCleanupTask = shouldRemoveFromTarget
                            ? deleteComicIfEmpty(targetComicReference)
                            : Tasks.forResult(null);
                    Task<Void> sourceCleanupTask = (!shouldRemoveFromTarget && shouldRemoveFromSource)
                            ? deleteComicIfEmpty(sourceComicReference)
                            : Tasks.forResult(null);

                    return batch.commit().continueWithTask(commitTask ->
                            Tasks.whenAllComplete(targetCleanupTask, sourceCleanupTask)
                                    .continueWith(unused -> resultadoEstado));
                });
    }

    // Elimina un tomo de deseados si existe.
    public static Task<Void> removeVolumeFromWishlist(String uidUsuario, String comicId, String tomoId) {
        ensureFirestoreReady();
        validateIds(uidUsuario, comicId, tomoId);
        return getVolumeReference(uidUsuario, WISHLIST_COLLECTION, comicId, tomoId).delete();
    }

    // Obtiene el estado de lectura de un tomo en biblioteca.
    public static Task<LibraryVolumeReadingData> getLibraryVolumeData(String uidUsuario, String comicId, String tomoId) {
        ensureFirestoreReady();
        validateIds(uidUsuario, comicId, tomoId);

        DocumentReference volumenReferencia = getVolumeReference(uidUsuario, LIBRARY_COLLECTION, comicId, tomoId);
        return volumenReferencia.get().continueWith(task -> {
            DocumentSnapshot snapshot = task.getResult();
            if (snapshot == null || !snapshot.exists()) {
                return new LibraryVolumeReadingData(false, false, new ArrayList<>(), new ArrayList<>());
            }

            List<LibraryReadingEntryData> lecturas = normalizeReadingEntries(snapshot.get("FechaLectura"));
            List<Date> fechasLectura = new ArrayList<>();
            for (LibraryReadingEntryData lectura : lecturas) {
                if (lectura.fecha != null) {
                    fechasLectura.add(lectura.fecha);
                }
            }

            boolean leido = Boolean.TRUE.equals(snapshot.getBoolean("Leido")) || !lecturas.isEmpty();
            return new LibraryVolumeReadingData(true, leido, fechasLectura, lecturas);
        });
    }

    // Agrega una fecha de lectura a un tomo que ya esta en biblioteca.
    public static Task<LibraryVolumeReadingData> addVolumeReading(
            String uidUsuario,
            String comicId,
            String tomoId,
            Date fechaLectura
    ) {
        ensureFirestoreReady();
        validateIds(uidUsuario, comicId, tomoId);
        validateReadingDate(fechaLectura);

        DocumentReference comicReferencia = getComicReference(uidUsuario, LIBRARY_COLLECTION, comicId);
        DocumentReference volumenReferencia = getVolumeReference(uidUsuario, LIBRARY_COLLECTION, comicId, tomoId);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        return volumenReferencia.get().continueWithTask(task -> {
            DocumentSnapshot snapshot = task.getResult();
            if (snapshot == null || !snapshot.exists()) {
                throw new IllegalArgumentException("Solo puedes agregar lecturas a tomos que esten en tu biblioteca.");
            }

            List<LibraryReadingEntryData> lecturasActuales = normalizeReadingEntries(snapshot.get("FechaLectura"));
            List<Object> lecturasSiguientes = new ArrayList<>();
            for (LibraryReadingEntryData lectura : lecturasActuales) {
                lecturasSiguientes.add(lectura.raw);
            }
            lecturasSiguientes.add(createReadingEntry(fechaLectura));

            WriteBatch batch = firestore.batch();
            batch.set(comicReferencia, createComicMetadata(), SetOptions.merge());

            Map<String, Object> updatePayload = new HashMap<>();
            updatePayload.put("FechaLectura", lecturasSiguientes);
            updatePayload.put("Leido", true);
            batch.set(volumenReferencia, updatePayload, SetOptions.merge());

            return batch.commit().continueWithTask(unused -> getLibraryVolumeData(uidUsuario, comicId, tomoId));
        });
    }

    // Elimina una fecha de lectura de un tomo en biblioteca.
    public static Task<LibraryVolumeReadingData> deleteVolumeReading(
            String uidUsuario,
            String comicId,
            String tomoId,
            int storageIndex
    ) {
        ensureFirestoreReady();
        validateIds(uidUsuario, comicId, tomoId);
        if (storageIndex < 0) {
            throw new IllegalArgumentException("No se encontro la lectura solicitada.");
        }

        DocumentReference volumenReferencia = getVolumeReference(uidUsuario, LIBRARY_COLLECTION, comicId, tomoId);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        return volumenReferencia.get().continueWithTask(task -> {
            DocumentSnapshot snapshot = task.getResult();
            if (snapshot == null || !snapshot.exists()) {
                throw new IllegalArgumentException("Solo puedes eliminar lecturas de tomos que esten en tu biblioteca.");
            }

            List<LibraryReadingEntryData> lecturasActuales = normalizeReadingEntries(snapshot.get("FechaLectura"));
            if (storageIndex >= lecturasActuales.size()) {
                throw new IllegalArgumentException("No se encontro la lectura solicitada.");
            }

            List<Object> lecturasSiguientes = new ArrayList<>();
            for (LibraryReadingEntryData lectura : lecturasActuales) {
                if (lectura.storageIndex != storageIndex) {
                    lecturasSiguientes.add(lectura.raw);
                }
            }

            WriteBatch batch = firestore.batch();
            Map<String, Object> updatePayload = new HashMap<>();
            updatePayload.put("FechaLectura", lecturasSiguientes);
            updatePayload.put("Leido", !lecturasSiguientes.isEmpty());
            batch.set(volumenReferencia, updatePayload, SetOptions.merge());

            return batch.commit().continueWithTask(unused -> getLibraryVolumeData(uidUsuario, comicId, tomoId));
        });
    }

    // Obtiene los comics y tomos guardados en una lista del usuario.
    public static Task<List<UserShelfComicGroupData>> getUserLibraryItems(String uidUsuario) {
        return getUserShelfItems(uidUsuario, LIBRARY_COLLECTION);
    }

    // Obtiene los comics y tomos deseados del usuario.
    public static Task<List<UserShelfComicGroupData>> getUserWishlistItems(String uidUsuario) {
        return getUserShelfItems(uidUsuario, WISHLIST_COLLECTION);
    }

    // Obtiene los tomos faltantes de un comic para el usuario actual.
    public static Task<ComicMissingVolumesData> getComicMissingVolumes(String uidUsuario, String comicId) {
        ensureFirestoreReady();
        validateComicIds(uidUsuario, comicId);

        Task<QuerySnapshot> tomosBibliotecaTask = getComicReference(uidUsuario, LIBRARY_COLLECTION, comicId)
                .collection(VOLUMES_SUBCOLLECTION)
                .get();
        Task<ComicDetailData> comicTask = ComicDetailRepository.getComicDetail(comicId);
        Task<List<VolumeDetailData>> tomosCatalogoTask = ComicDetailRepository.getComicVolumes(comicId);

        return Tasks.whenAllComplete(tomosBibliotecaTask, comicTask, tomosCatalogoTask).continueWith(task -> {
            QuerySnapshot tomosBiblioteca = tomosBibliotecaTask.isSuccessful() ? tomosBibliotecaTask.getResult() : null;
            ComicDetailData comic = comicTask.isSuccessful() ? comicTask.getResult() : null;
            List<VolumeDetailData> tomosCatalogo = tomosCatalogoTask.isSuccessful() ? tomosCatalogoTask.getResult() : null;

            if (tomosBiblioteca == null || tomosBiblioteca.isEmpty() || tomosCatalogo == null || tomosCatalogo.isEmpty()) {
                return new ComicMissingVolumesData(false, new ArrayList<>());
            }

            String nombreComic = comic != null ? comic.nombre : "";
            Set<String> tomosPropios = new HashSet<>();
            for (DocumentSnapshot tomoBiblioteca : tomosBiblioteca.getDocuments()) {
                tomosPropios.add(tomoBiblioteca.getId());
            }

            List<MissingVolumeData> tomosFaltantes = new ArrayList<>();
            for (VolumeDetailData tomo : tomosCatalogo) {
                if (tomo != null && !tomosPropios.contains(tomo.id)) {
                    tomosFaltantes.add(new MissingVolumeData(comicId, nombreComic, tomo));
                }
            }

            tomosFaltantes.sort((tomoA, tomoB) -> Integer.compare(
                    getVolumeOrderValue(tomoA != null ? tomoA.tomo : null),
                    getVolumeOrderValue(tomoB != null ? tomoB.tomo : null)
            ));
            return new ComicMissingVolumesData(true, tomosFaltantes);
        });
    }

    // Obtiene los tomos de biblioteca de un comic puntual.
    public static Task<List<UserShelfVolumeItemData>> getComicLibraryVolumes(String uidUsuario, String comicId) {
        ensureFirestoreReady();
        validateComicIds(uidUsuario, comicId);

        return getComicReference(uidUsuario, LIBRARY_COLLECTION, comicId)
                .collection(VOLUMES_SUBCOLLECTION)
                .get()
                .continueWithTask(task -> {
                    QuerySnapshot tomosBiblioteca = task.getResult();
                    if (tomosBiblioteca == null || tomosBiblioteca.isEmpty()) {
                        return Tasks.forResult(new ArrayList<>());
                    }

                    List<Task<UserShelfVolumeItemData>> tareasTomos = new ArrayList<>();
                    for (DocumentSnapshot tomoSnapshot : tomosBiblioteca.getDocuments()) {
                        tareasTomos.add(buildShelfVolumeItem(comicId, tomoSnapshot));
                    }

                    return Tasks.whenAllSuccess(tareasTomos).continueWith(resultTask -> {
                        List<UserShelfVolumeItemData> tomos = new ArrayList<>();
                        List<?> resultados = resultTask.getResult();
                        if (resultados != null) {
                            for (Object resultado : resultados) {
                                if (resultado instanceof UserShelfVolumeItemData) {
                                    tomos.add((UserShelfVolumeItemData) resultado);
                                }
                            }
                        }

                        tomos.sort((tomoA, tomoB) -> Integer.compare(
                                getVolumeOrderValue(tomoA != null ? tomoA.tomo : null),
                                getVolumeOrderValue(tomoB != null ? tomoB.tomo : null)
                        ));
                        return tomos;
                    });
                });
    }

    // Obtiene hasta un limite de tomos faltantes para mostrar en inicio.
    public static Task<HomeMissingVolumesData> getHomeMissingVolumes(String uidUsuario, int limite) {
        ensureFirestoreReady();
        validateUserId(uidUsuario);
        int limiteSeguro = limite > 0 ? limite : 25;

        return getUserLibraryItems(uidUsuario).continueWithTask(task -> {
            List<UserShelfComicGroupData> gruposBiblioteca = task.getResult();
            if (gruposBiblioteca == null || gruposBiblioteca.isEmpty()) {
                return Tasks.forResult(new HomeMissingVolumesData(false, new ArrayList<>()));
            }

            List<Task<List<MissingVolumeData>>> tareasPorComic = new ArrayList<>();
            for (UserShelfComicGroupData grupo : gruposBiblioteca) {
                if (grupo == null || TextUtils.isEmpty(grupo.comicId)) {
                    continue;
                }
                tareasPorComic.add(buildMissingVolumesForComicGroup(grupo));
            }

            if (tareasPorComic.isEmpty()) {
                return Tasks.forResult(new HomeMissingVolumesData(false, new ArrayList<>()));
            }

            return Tasks.whenAllSuccess(tareasPorComic).continueWith(tareasCompletas -> {
                List<MissingVolumeData> faltantes = new ArrayList<>();
                List<?> resultados = tareasCompletas.getResult();
                if (resultados != null) {
                    for (Object resultado : resultados) {
                        if (resultado instanceof List) {
                            for (Object item : (List<?>) resultado) {
                                if (item instanceof MissingVolumeData) {
                                    faltantes.add((MissingVolumeData) item);
                                }
                            }
                        }
                    }
                }

                faltantes.sort(UserShelfRepository::sortMissingLibraryVolumes);
                if (faltantes.size() > limiteSeguro) {
                    faltantes = new ArrayList<>(faltantes.subList(0, limiteSeguro));
                }
                return new HomeMissingVolumesData(true, faltantes);
            });
        });
    }

    // Obtiene los ultimos tomos agregados a biblioteca para inicio.
    public static Task<HomeRecentVolumesData> getHomeRecentLibraryVolumes(String uidUsuario, int limite) {
        ensureFirestoreReady();
        validateUserId(uidUsuario);
        int limiteSeguro = limite > 0 ? limite : 20;

        return getUserLibraryItems(uidUsuario).continueWith(task -> {
            List<UserShelfComicGroupData> gruposBiblioteca = task.getResult();
            if (gruposBiblioteca == null || gruposBiblioteca.isEmpty()) {
                return new HomeRecentVolumesData(false, new ArrayList<>());
            }

            List<RecentLibraryVolumeData> tomosRecientes = new ArrayList<>();
            for (UserShelfComicGroupData grupo : gruposBiblioteca) {
                if (grupo == null || TextUtils.isEmpty(grupo.comicId) || grupo.tomos == null) {
                    continue;
                }

                for (UserShelfVolumeItemData tomoBiblioteca : grupo.tomos) {
                    if (tomoBiblioteca == null || tomoBiblioteca.tomo == null) {
                        continue;
                    }
                    tomosRecientes.add(new RecentLibraryVolumeData(
                            grupo.comicId,
                            grupo.comic != null ? grupo.comic.nombre : "",
                            tomoBiblioteca.tomo,
                            tomoBiblioteca.fechaAgregado
                    ));
                }
            }

            tomosRecientes.sort(UserShelfRepository::sortLatestLibraryVolumes);
            if (tomosRecientes.size() > limiteSeguro) {
                tomosRecientes = new ArrayList<>(tomosRecientes.subList(0, limiteSeguro));
            }
            return new HomeRecentVolumesData(true, tomosRecientes);
        });
    }

    // Obtiene hasta 'limite' comics recomendados segun los generos de la biblioteca del usuario.
    public static Task<HomeRecommendationsData> getHomeRecommendations(String uidUsuario, int limite) {
        ensureFirestoreReady();
        validateUserId(uidUsuario);
        int limiteSeguro = limite > 0 ? limite : 20;

        Task<List<UserShelfComicGroupData>> bibliotecaTask = getUserLibraryItems(uidUsuario);
        Task<List<ComicDetailData>> catalogoTask = ComicDetailRepository.getAllComics();

        return Tasks.whenAllComplete(bibliotecaTask, catalogoTask).continueWithTask(task -> {
            List<UserShelfComicGroupData> gruposBiblioteca = bibliotecaTask.isSuccessful()
                    ? bibliotecaTask.getResult() : null;
            List<ComicDetailData> catalogoCompleto = catalogoTask.isSuccessful()
                    ? catalogoTask.getResult() : null;

            if (gruposBiblioteca == null || gruposBiblioteca.isEmpty()) {
                return Tasks.forResult(new HomeRecommendationsData(false, new ArrayList<>()));
            }

            // Recolecta IDs de comics ya en biblioteca y sus generos.
            Set<String> idsBiblioteca = new HashSet<>();
            Set<String> generosBiblioteca = new HashSet<>();
            for (UserShelfComicGroupData grupo : gruposBiblioteca) {
                if (grupo == null || TextUtils.isEmpty(grupo.comicId)) continue;
                idsBiblioteca.add(grupo.comicId);
                if (grupo.comic != null && grupo.comic.generos != null) {
                    generosBiblioteca.addAll(grupo.comic.generos);
                }
            }

            if (catalogoCompleto == null || catalogoCompleto.isEmpty()) {
                return Tasks.forResult(new HomeRecommendationsData(true, new ArrayList<>()));
            }

            // Candidatos: comics del catalogo que no estan en la biblioteca.
            List<ComicDetailData> candidatos = new ArrayList<>();
            for (ComicDetailData comic : catalogoCompleto) {
                if (comic != null && !TextUtils.isEmpty(comic.id) && !idsBiblioteca.contains(comic.id)) {
                    candidatos.add(comic);
                }
            }

            // Ordena candidatos por cantidad de generos coincidentes y luego por nombre.
            candidatos.sort((comicA, comicB) -> {
                int scoreA = contarGenerosCoincidentes(comicA, generosBiblioteca);
                int scoreB = contarGenerosCoincidentes(comicB, generosBiblioteca);
                if (scoreB != scoreA) return Integer.compare(scoreB, scoreA);
                return toSortableText(comicA.nombre).compareTo(toSortableText(comicB.nombre));
            });

            if (candidatos.size() > limiteSeguro) {
                candidatos = candidatos.subList(0, limiteSeguro);
            }

            // Para cada candidato, obtiene su tomo destacado.
            List<Task<ComicRecommendationData>> tareasRecomendaciones = new ArrayList<>();
            for (ComicDetailData comic : candidatos) {
                final ComicDetailData comicFinal = comic;
                final List<String> coincidentes = getGenerosCoincidentes(comicFinal, generosBiblioteca);
                Task<ComicRecommendationData> tareaRecomendacion =
                        ComicDetailRepository.getComicVolumes(comicFinal.id).continueWith(tomosTask -> {
                            List<VolumeDetailData> tomos = tomosTask.isSuccessful()
                                    ? tomosTask.getResult() : null;
                            return new ComicRecommendationData(
                                    comicFinal.id,
                                    comicFinal.nombre,
                                    comicFinal.autores,
                                    coincidentes,
                                    getFeaturedRecommendationVolume(tomos)
                            );
                        });
                tareasRecomendaciones.add(tareaRecomendacion);
            }

            return Tasks.whenAllSuccess(tareasRecomendaciones).continueWith(tareasCompletas -> {
                List<ComicRecommendationData> recomendaciones = new ArrayList<>();
                List<?> resultados = tareasCompletas.getResult();
                if (resultados != null) {
                    for (Object resultado : resultados) {
                        if (resultado instanceof ComicRecommendationData) {
                            recomendaciones.add((ComicRecommendationData) resultado);
                        }
                    }
                }
                return new HomeRecommendationsData(true, recomendaciones);
            });
        });
    }

    // Selecciona el tomo destacado de un comic para mostrar en recomendaciones.
    private static VolumeDetailData getFeaturedRecommendationVolume(List<VolumeDetailData> tomos) {
        if (tomos == null || tomos.isEmpty()) {
            return null;
        }

        List<VolumeDetailData> ordenados = new ArrayList<>(tomos);
        ordenados.sort((a, b) -> {
            boolean unicoA = Boolean.TRUE.equals(a != null ? a.tomoUnico : null);
            boolean unicoB = Boolean.TRUE.equals(b != null ? b.tomoUnico : null);
            if (unicoA && !unicoB) return -1;
            if (!unicoA && unicoB) return 1;
            Integer numA = a != null ? a.numeroTomo : null;
            Integer numB = b != null ? b.numeroTomo : null;
            if (numA == null && numB == null) return 0;
            if (numA == null) return 1;
            if (numB == null) return -1;
            return Integer.compare(numA, numB);
        });

        for (VolumeDetailData tomo : ordenados) {
            if (tomo != null && tomo.numeroTomo != null && tomo.numeroTomo == 1) return tomo;
        }
        for (VolumeDetailData tomo : ordenados) {
            if (tomo != null && Boolean.TRUE.equals(tomo.tomoUnico)) return tomo;
        }
        return ordenados.get(0);
    }

    // Cuenta cuantos generos de un comic coinciden con los generos del usuario.
    private static int contarGenerosCoincidentes(ComicDetailData comic, Set<String> generosBiblioteca) {
        if (comic == null || comic.generos == null) return 0;
        int contador = 0;
        for (String genero : comic.generos) {
            if (generosBiblioteca.contains(genero)) contador++;
        }
        return contador;
    }

    // Devuelve los generos de un comic que coinciden con los del usuario.
    private static List<String> getGenerosCoincidentes(ComicDetailData comic, Set<String> generosBiblioteca) {
        List<String> coincidentes = new ArrayList<>();
        if (comic == null || comic.generos == null) return coincidentes;
        for (String genero : comic.generos) {
            if (generosBiblioteca.contains(genero)) coincidentes.add(genero);
        }
        return coincidentes;
    }

    // Busca el estado de un tomo en una coleccion concreta.
    private static DocumentReference getComicReference(String uidUsuario, String listCollection, String comicId) {
        return FirebaseFirestore.getInstance()
                .collection(USER_COLLECTION)
                .document(uidUsuario)
                .collection(listCollection)
                .document(LIST_ROOT_DOCUMENT)
                .collection(COMICS_SUBCOLLECTION)
                .document(comicId);
    }

    // Busca el documento de un tomo dentro de una lista.
    private static DocumentReference getVolumeReference(String uidUsuario, String listCollection, String comicId, String tomoId) {
        return getComicReference(uidUsuario, listCollection, comicId)
                .collection(VOLUMES_SUBCOLLECTION)
                .document(tomoId);
    }

    // Recorre una lista y arma sus comics con sus tomos.
    private static Task<List<UserShelfComicGroupData>> getUserShelfItems(String uidUsuario, String listCollection) {
        ensureFirestoreReady();
        validateUserId(uidUsuario);

        return FirebaseFirestore.getInstance()
                .collection(USER_COLLECTION)
                .document(uidUsuario)
                .collection(listCollection)
                .document(LIST_ROOT_DOCUMENT)
                .collection(COMICS_SUBCOLLECTION)
                .get()
                .continueWithTask(task -> {
                    QuerySnapshot comicSnapshots = task.getResult();
                    if (comicSnapshots == null || comicSnapshots.isEmpty()) {
                        return Tasks.forResult(new ArrayList<>());
                    }

                    List<Task<UserShelfComicGroupData>> tareasGrupos = new ArrayList<>();
                    for (DocumentSnapshot comicSnapshot : comicSnapshots.getDocuments()) {
                        tareasGrupos.add(buildShelfGroup(comicSnapshot));
                    }

                    return Tasks.whenAllSuccess(tareasGrupos).continueWith(resultTask -> {
                        List<UserShelfComicGroupData> grupos = new ArrayList<>();
                        List<?> resultados = resultTask.getResult();
                        if (resultados != null) {
                            for (Object resultado : resultados) {
                                if (resultado instanceof UserShelfComicGroupData) {
                                    grupos.add((UserShelfComicGroupData) resultado);
                                }
                            }
                        }

                        grupos.sort((grupoA, grupoB) ->
                                toSortableText(grupoA.comic.nombre).compareTo(toSortableText(grupoB.comic.nombre)));
                        return grupos;
                    });
                });
    }

    // Arma un grupo de comic con todos sus tomos.
    private static Task<UserShelfComicGroupData> buildShelfGroup(DocumentSnapshot comicSnapshot) {
        String comicId = comicSnapshot.getId();
        Date fechaAgregadoComic = toDate(comicSnapshot.get("FechaAgregado"));

        Task<ComicDetailData> comicTask = ComicDetailRepository.getComicDetail(comicId);
        Task<QuerySnapshot> volumenesTask = comicSnapshot.getReference().collection(VOLUMES_SUBCOLLECTION).get();

        return Tasks.whenAllComplete(comicTask, volumenesTask).continueWithTask(task -> {
            ComicDetailData comic = comicTask.isSuccessful() ? comicTask.getResult() : null;
            QuerySnapshot volumenesSnapshot = volumenesTask.isSuccessful() ? volumenesTask.getResult() : null;

            if (comic == null || volumenesSnapshot == null || volumenesSnapshot.isEmpty()) {
                return Tasks.forResult(null);
            }

            List<Task<UserShelfVolumeItemData>> tareasTomos = new ArrayList<>();
            for (DocumentSnapshot tomoSnapshot : volumenesSnapshot.getDocuments()) {
                tareasTomos.add(buildShelfVolumeItem(comicId, tomoSnapshot));
            }

            return Tasks.whenAllSuccess(tareasTomos).continueWith(tomosTask -> {
                List<UserShelfVolumeItemData> tomos = new ArrayList<>();
                List<?> resultadosTomos = tomosTask.getResult();
                if (resultadosTomos != null) {
                    for (Object resultadoTomo : resultadosTomos) {
                        if (resultadoTomo instanceof UserShelfVolumeItemData) {
                            tomos.add((UserShelfVolumeItemData) resultadoTomo);
                        }
                    }
                }

                if (tomos.isEmpty()) {
                    return null;
                }

                return new UserShelfComicGroupData(comicId, comic, fechaAgregadoComic, tomos);
            });
        });
    }

    // Arma un tomo guardado dentro de una lista.
    private static Task<UserShelfVolumeItemData> buildShelfVolumeItem(String comicId, DocumentSnapshot tomoSnapshot) {
        String tomoId = tomoSnapshot.getId();
        Date fechaAgregado = toDate(tomoSnapshot.get("FechaAgregado"));
        boolean leido = Boolean.TRUE.equals(tomoSnapshot.getBoolean("Leido"));
        List<Date> fechasLectura = toDateList(tomoSnapshot.get("FechaLectura"));

        return ComicDetailRepository.getVolumeDetail(comicId, tomoId)
                .continueWith(task -> {
                    VolumeDetailData tomo = task.getResult();
                    if (tomo == null) {
                        return null;
                    }
                    return new UserShelfVolumeItemData(tomo, fechaAgregado, leido, fechasLectura);
                });
    }

    // Arma los tomos faltantes de un comic para la pantalla de inicio.
    private static Task<List<MissingVolumeData>> buildMissingVolumesForComicGroup(UserShelfComicGroupData grupoBiblioteca) {
        if (grupoBiblioteca == null || TextUtils.isEmpty(grupoBiblioteca.comicId)) {
            return Tasks.forResult(new ArrayList<>());
        }

        return ComicDetailRepository.getComicVolumes(grupoBiblioteca.comicId).continueWith(task -> {
            List<VolumeDetailData> tomosCatalogo = task.getResult();
            if (tomosCatalogo == null || tomosCatalogo.isEmpty()) {
                return new ArrayList<>();
            }

            Set<String> tomosPropios = new HashSet<>();
            if (grupoBiblioteca.tomos != null) {
                for (UserShelfVolumeItemData tomoBiblioteca : grupoBiblioteca.tomos) {
                    if (tomoBiblioteca != null && tomoBiblioteca.tomo != null && !TextUtils.isEmpty(tomoBiblioteca.tomo.id)) {
                        tomosPropios.add(tomoBiblioteca.tomo.id);
                    }
                }
            }

            List<MissingVolumeData> faltantes = new ArrayList<>();
            for (VolumeDetailData tomoCatalogo : tomosCatalogo) {
                if (tomoCatalogo != null && !tomosPropios.contains(tomoCatalogo.id)) {
                    faltantes.add(new MissingVolumeData(
                            grupoBiblioteca.comicId,
                            grupoBiblioteca.comic != null ? grupoBiblioteca.comic.nombre : "",
                            tomoCatalogo
                    ));
                }
            }
            return faltantes;
        });
    }

    // Ordena tomos recientes por fecha, nombre de comic y numero de tomo.
    private static int sortLatestLibraryVolumes(RecentLibraryVolumeData tomoA, RecentLibraryVolumeData tomoB) {
        long fechaA = getDateTime(tomoA != null ? tomoA.fechaAgregado : null);
        long fechaB = getDateTime(tomoB != null ? tomoB.fechaAgregado : null);
        int comparacionFecha = Long.compare(fechaB, fechaA);
        if (comparacionFecha != 0) {
            return comparacionFecha;
        }

        int comparacionComic = toSortableText(tomoA != null ? tomoA.comicNombre : "")
                .compareTo(toSortableText(tomoB != null ? tomoB.comicNombre : ""));
        if (comparacionComic != 0) {
            return comparacionComic;
        }

        return Integer.compare(
                getVolumeOrderValue(tomoB != null ? tomoB.tomo : null),
                getVolumeOrderValue(tomoA != null ? tomoA.tomo : null)
        );
    }

    // Ordena tomos faltantes para inicio segun fecha, numero y nombre de comic.
    private static int sortMissingLibraryVolumes(MissingVolumeData tomoA, MissingVolumeData tomoB) {
        long fechaA = getVolumePublicationTime(tomoA != null ? tomoA.tomo : null);
        long fechaB = getVolumePublicationTime(tomoB != null ? tomoB.tomo : null);
        int comparacionFecha = Long.compare(fechaB, fechaA);
        if (comparacionFecha != 0) {
            return comparacionFecha;
        }

        int ordenA = getVolumeOrderValue(tomoA != null ? tomoA.tomo : null);
        int ordenB = getVolumeOrderValue(tomoB != null ? tomoB.tomo : null);
        int comparacionOrden = Integer.compare(ordenB, ordenA);
        if (comparacionOrden != 0) {
            return comparacionOrden;
        }

        return toSortableText(tomoA != null ? tomoA.comicNombre : "")
                .compareTo(toSortableText(tomoB != null ? tomoB.comicNombre : ""));
    }

    // Convierte una fecha opcional a milisegundos para ordenar.
    private static long getDateTime(Date fecha) {
        if (fecha == null) {
            return 0L;
        }
        return fecha.getTime();
    }

    // Convierte la fecha de publicacion a un valor comparable.
    private static long getVolumePublicationTime(VolumeDetailData tomo) {
        if (tomo == null || TextUtils.isEmpty(tomo.fechaPublicacion)) {
            return 0L;
        }
        String valor = tomo.fechaPublicacion.trim();
        try {
            if (valor.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(valor).getTime();
            }
            if (valor.matches("^\\d{4}-\\d{2}$")) {
                return new java.text.SimpleDateFormat("yyyy-MM", Locale.ROOT).parse(valor).getTime();
            }
        } catch (Exception ignored) {
            return 0L;
        }
        return 0L;
    }

    // Devuelve un valor de orden para comparar tomos por numero.
    private static int getVolumeOrderValue(VolumeDetailData tomo) {
        if (tomo == null) {
            return Integer.MIN_VALUE;
        }
        if (Boolean.TRUE.equals(tomo.tomoUnico)) {
            return Integer.MAX_VALUE;
        }
        if (tomo.numeroTomo != null) {
            return tomo.numeroTomo;
        }
        return Integer.MIN_VALUE + 1;
    }

    // Convierte texto para ordenar sin depender de mayusculas.
    private static String toSortableText(String valor) {
        return String.valueOf(valor == null ? "" : valor).toLowerCase(Locale.ROOT).trim();
    }

    // Convierte un valor de Firestore en fecha.
    private static Date toDate(Object value) {
        if (value instanceof Map) {
            Object fechaDate = ((Map<?, ?>) value).get("date");
            if (fechaDate != null) {
                return toDate(fechaDate);
            }
            Object fecha = ((Map<?, ?>) value).get("fecha");
            if (fecha != null) {
                return toDate(fecha);
            }
        }
        if (value instanceof String) {
            String textoFecha = ((String) value).trim();
            if (textoFecha.isEmpty()) {
                return null;
            }
            try {
                SimpleDateFormat formatoFecha = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
                formatoFecha.setTimeZone(TimeZone.getTimeZone("UTC"));
                return formatoFecha.parse(textoFecha);
            } catch (Exception ignored) {
                return null;
            }
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate();
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        return null;
    }

    // Convierte una lista de fechas de Firestore en fechas Java.
    private static List<Date> toDateList(Object value) {
        List<Date> fechas = new ArrayList<>();
        if (!(value instanceof List)) {
            return fechas;
        }

        for (Object item : (List<?>) value) {
            Date fecha = toDate(item);
            if (fecha != null) {
                fechas.add(fecha);
            }
        }
        return fechas;
    }

    // Convierte una lista de lecturas de Firestore en datos completos para la app.
    private static List<LibraryReadingEntryData> normalizeReadingEntries(Object value) {
        List<LibraryReadingEntryData> lecturas = new ArrayList<>();
        if (!(value instanceof List)) {
            return lecturas;
        }

        List<?> lista = (List<?>) value;
        for (int i = 0; i < lista.size(); i++) {
            Object item = lista.get(i);
            Date fecha = toDate(item);
            if (fecha == null) {
                continue;
            }

            String id = null;
            if (item instanceof Map) {
                Object valorId = ((Map<?, ?>) item).get("id");
                if (valorId != null && !String.valueOf(valorId).trim().isEmpty()) {
                    id = String.valueOf(valorId);
                }
            }
            if (id == null) {
                id = fecha.getTime() + "-" + i;
            }

            lecturas.add(new LibraryReadingEntryData(i, id, fecha, item));
        }
        return lecturas;
    }

    // Crea la estructura de una lectura nueva para Firestore.
    private static Map<String, Object> createReadingEntry(Date fechaLectura) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", String.valueOf(fechaLectura.getTime()) + "-" + UUID.randomUUID());
        payload.put("date", new Timestamp(fechaLectura));
        return payload;
    }

    // Crea el documento del comic dentro de una lista.
    private static Map<String, Object> createComicMetadata() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("FechaAgregado", Timestamp.now());
        return payload;
    }

    // Crea el documento de un tomo en biblioteca.
    private static Map<String, Object> createLibraryPayload(String comicId, String tomoId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ComicId", comicId);
        payload.put("TomoID", tomoId);
        payload.put("FechaLectura", new ArrayList<>());
        payload.put("Leido", false);
        return payload;
    }

    // Crea el documento de un tomo en deseados.
    private static Map<String, Object> createWishlistPayload(String comicId, String tomoId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("ComicId", comicId);
        payload.put("TomoID", tomoId);
        payload.put("FechaAgregado", Timestamp.now());
        return payload;
    }

    // Elimina el comic si ya no quedan tomos dentro de la lista.
    private static Task<Void> deleteComicIfEmpty(DocumentReference comicReference) {
        return comicReference.collection(VOLUMES_SUBCOLLECTION)
                .get()
                .continueWithTask(task -> {
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null || snapshot.isEmpty()) {
                        return comicReference.delete();
                    }
                    return Tasks.forResult(null);
                });
    }

    // Revisa que los identificadores existan.
    private static void validateIds(String uidUsuario, String comicId, String tomoId) {
        if (uidUsuario == null || uidUsuario.trim().isEmpty()
                || comicId == null || comicId.trim().isEmpty()
                || tomoId == null || tomoId.trim().isEmpty()) {
            throw new IllegalArgumentException("Datos invalidos.");
        }
    }

    // Revisa que el usuario exista.
    private static void validateUserId(String uidUsuario) {
        if (uidUsuario == null || uidUsuario.trim().isEmpty()) {
            throw new IllegalArgumentException("Datos invalidos.");
        }
    }

    // Revisa que usuario y comic sean validos.
    private static void validateComicIds(String uidUsuario, String comicId) {
        if (uidUsuario == null || uidUsuario.trim().isEmpty()
                || comicId == null || comicId.trim().isEmpty()) {
            throw new IllegalArgumentException("Datos invalidos.");
        }
    }

    // Revisa que la fecha de lectura sea valida y no sea futura.
    private static void validateReadingDate(Date fechaLectura) {
        if (fechaLectura == null) {
            throw new IllegalArgumentException("Debes seleccionar una fecha valida.");
        }

        Date hoy = new Date();
        if (startOfDay(fechaLectura).after(startOfDay(hoy))) {
            throw new IllegalArgumentException("No puedes guardar una lectura con una fecha futura.");
        }
    }

    // Limpia la hora para comparar solo fechas.
    private static Date startOfDay(Date fecha) {
        java.util.Calendar calendario = java.util.Calendar.getInstance();
        calendario.setTime(fecha);
        calendario.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendario.set(java.util.Calendar.MINUTE, 0);
        calendario.set(java.util.Calendar.SECOND, 0);
        calendario.set(java.util.Calendar.MILLISECOND, 0);
        return calendario.getTime();
    }

    // Asegura que Firebase este disponible.
    private static void ensureFirestoreReady() {
        FirebaseFirestore.getInstance();
    }
}
