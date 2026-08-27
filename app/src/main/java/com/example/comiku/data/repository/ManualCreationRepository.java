package com.example.comiku.data.repository;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ManualCreationRepository {
    private static final String COLECCION_COMICS = "comics";
    private static final String SUBCOLECCION_TOMOS = "tomos";
    private static final String COLECCION_PENDIENTES = "creaciones_pendientes";

    private ManualCreationRepository() {
    }

    // Crea un comic y devuelve su id.
    public static Task<String> createComic(Map<String, Object> datosComic) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        return firestore.collection(COLECCION_COMICS)
                .add(datosComic)
                .continueWith(task -> {
                    DocumentReference referencia = task.getResult();
                    return referencia == null ? "" : referencia.getId();
                });
    }

    // Agrega un tomo dentro de un comic.
    public static Task<Void> addComicVolume(String comicId, Map<String, Object> datosTomo) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        return firestore.collection(COLECCION_COMICS)
                .document(comicId)
                .collection(SUBCOLECCION_TOMOS)
                .add(datosTomo)
                .continueWith(task -> null);
    }

    // Verifica si un isbn ya existe en la base.
    public static Task<Boolean> isbnExists(long isbn) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        TaskCompletionSource<Boolean> resultado = new TaskCompletionSource<>();
        String isbnTexto = String.valueOf(isbn);

        firestore.collectionGroup(SUBCOLECCION_TOMOS)
                .whereEqualTo("ISBN", isbn)
                .limit(1)
                .get()
                .addOnSuccessListener(snapNumero -> {
                    if (snapNumero != null && !snapNumero.isEmpty()) {
                        resultado.setResult(true);
                        return;
                    }
                    buscarIsbnComoTextoConFallback(firestore, isbn, isbnTexto, resultado);
                })
                .addOnFailureListener(error ->
                        buscarIsbnComoTextoConFallback(firestore, isbn, isbnTexto, resultado));

        return resultado.getTask();
    }

    // Intenta validar ISBN como texto y si falla recorre comics por seguridad.
    private static void buscarIsbnComoTextoConFallback(
            FirebaseFirestore firestore,
            long isbn,
            String isbnTexto,
            TaskCompletionSource<Boolean> resultado
    ) {
        firestore.collectionGroup(SUBCOLECCION_TOMOS)
                .whereEqualTo("ISBN", isbnTexto)
                .limit(1)
                .get()
                .addOnSuccessListener(snapTexto -> {
                    if (snapTexto != null && !snapTexto.isEmpty()) {
                        resultado.setResult(true);
                        return;
                    }
                    buscarIsbnPorComics(firestore, isbn, isbnTexto, resultado);
                })
                .addOnFailureListener(error -> buscarIsbnPorComics(firestore, isbn, isbnTexto, resultado));
    }

    // Recorre comics y tomos para validar ISBN cuando collectionGroup falla.
    private static void buscarIsbnPorComics(
            FirebaseFirestore firestore,
            long isbn,
            String isbnTexto,
            TaskCompletionSource<Boolean> resultado
    ) {
        firestore.collection(COLECCION_COMICS)
                .limit(400)
                .get()
                .addOnSuccessListener(snapComics -> {
                    List<DocumentSnapshot> comics = snapComics != null
                            ? snapComics.getDocuments()
                            : new ArrayList<>();
                    verificarIsbnEnComic(firestore, comics, 0, isbn, isbnTexto, resultado);
                });
    }

    // Verifica de forma recursiva cada comic hasta encontrar un ISBN.
    private static void verificarIsbnEnComic(
            FirebaseFirestore firestore,
            List<DocumentSnapshot> comics,
            int indice,
            long isbn,
            String isbnTexto,
            TaskCompletionSource<Boolean> resultado
    ) {
        if (resultado.getTask().isComplete()) {
            return;
        }
        if (comics == null || indice >= comics.size()) {
            resultado.setResult(false);
            return;
        }

        DocumentSnapshot comic = comics.get(indice);
        if (comic == null || comic.getId() == null || comic.getId().trim().isEmpty()) {
            verificarIsbnEnComic(firestore, comics, indice + 1, isbn, isbnTexto, resultado);
            return;
        }

        firestore.collection(COLECCION_COMICS)
                .document(comic.getId())
                .collection(SUBCOLECCION_TOMOS)
                .whereEqualTo("ISBN", isbn)
                .limit(1)
                .get()
                .addOnSuccessListener(snapNumero -> {
                    if (snapNumero != null && !snapNumero.isEmpty()) {
                        resultado.setResult(true);
                        return;
                    }
                    firestore.collection(COLECCION_COMICS)
                            .document(comic.getId())
                            .collection(SUBCOLECCION_TOMOS)
                            .whereEqualTo("ISBN", isbnTexto)
                            .limit(1)
                            .get()
                            .addOnSuccessListener(snapTexto -> {
                                if (snapTexto != null && !snapTexto.isEmpty()) {
                                    resultado.setResult(true);
                                } else {
                                    verificarIsbnEnComic(firestore, comics, indice + 1, isbn, isbnTexto, resultado);
                                }
                            })
                            .addOnFailureListener(error ->
                                    verificarIsbnEnComic(firestore, comics, indice + 1, isbn, isbnTexto, resultado));
                })
                .addOnFailureListener(error ->
                        verificarIsbnEnComic(firestore, comics, indice + 1, isbn, isbnTexto, resultado));
    }

    // Guarda una creacion pendiente para revision de administracion.
    public static Task<String> addPendingCreation(
            String tipo,
            String uidUsuario,
            Map<String, Object> metadataComic,
            String comicId,
            List<Map<String, Object>> tomos
    ) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        Map<String, Object> payload = new HashMap<>();
        payload.put("tipo", tipo);
        payload.put("UserID", uidUsuario);
        payload.put("metadata", metadataComic);
        payload.put("comicId", comicId);
        payload.put("tomos", new ArrayList<>(tomos));
        payload.put("estado", "pendiente");
        payload.put("fechaEnvio", Timestamp.now());

        return firestore.collection(COLECCION_PENDIENTES)
                .add(payload)
                .continueWith(task -> {
                    DocumentReference referencia = task.getResult();
                    return referencia == null ? "" : referencia.getId();
                });
    }
}
