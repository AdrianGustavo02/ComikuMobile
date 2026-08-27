package com.example.comiku.data.repository;

import com.example.comiku.data.model.ComicReviewData;
import com.example.comiku.data.model.ComicReviewPageData;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ComicReviewRepository {
    private static final String COL_COMICS = "comics";
    private static final String SUB_RESENAS = "Resenas";
    private static final String COL_USUARIO = "usuario";
    private static final String SUB_BIBLIOTECA = "biblioteca";
    private static final String DOC_COLECCION = "coleccion";
    private static final String SUB_COMICS_LIB = "comics";

    private ComicReviewRepository() {
    }

    // Obtiene la reseña del usuario para un comic especifico.
    public static Task<ComicReviewData> getUserReview(String comicId, String uid) {
        return FirebaseFirestore.getInstance()
                .collection(COL_COMICS)
                .document(comicId)
                .collection(SUB_RESENAS)
                .whereEqualTo("UserID", uid)
                .limit(1)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) return null;
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null || snapshot.isEmpty()) return null;
                    return buildReview(snapshot.getDocuments().get(0));
                });
    }

    // Obtiene reseñas de un comic en paginas de 10, usando cursor de paginacion.
    public static Task<ComicReviewPageData> getComicReviews(String comicId, int pageSize, String startAfterId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (startAfterId != null && !startAfterId.isEmpty()) {
            // Obtiene el documento cursor antes de paginar.
            return db.collection(COL_COMICS)
                    .document(comicId)
                    .collection(SUB_RESENAS)
                    .document(startAfterId)
                    .get()
                    .continueWithTask(docTask -> {
                        DocumentSnapshot cursorDoc = docTask.isSuccessful() ? docTask.getResult() : null;
                        Query q;
                        if (cursorDoc != null && cursorDoc.exists()) {
                            q = db.collection(COL_COMICS)
                                    .document(comicId)
                                    .collection(SUB_RESENAS)
                                    .orderBy("Fecha", Query.Direction.DESCENDING)
                                    .startAfter(cursorDoc)
                                    .limit(pageSize);
                        } else {
                            q = db.collection(COL_COMICS)
                                    .document(comicId)
                                    .collection(SUB_RESENAS)
                                    .orderBy("Fecha", Query.Direction.DESCENDING)
                                    .limit(pageSize);
                        }
                        return q.get().continueWith(task -> buildPage(task.getResult(), pageSize));
                    });
        }

        return db.collection(COL_COMICS)
                .document(comicId)
                .collection(SUB_RESENAS)
                .orderBy("Fecha", Query.Direction.DESCENDING)
                .limit(pageSize)
                .get()
                .continueWith(task -> buildPage(task.getResult(), pageSize));
    }

    // Agrega una reseña y recalcula el promedio del comic.
    public static Task<Void> addReview(String comicId, String uid, String descripcion, int calificacion) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("UserID", uid);
        datos.put("Descripcion", descripcion);
        datos.put("Calificacion", calificacion);
        datos.put("Fecha", FieldValue.serverTimestamp());

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        return db.collection(COL_COMICS)
                .document(comicId)
                .collection(SUB_RESENAS)
                .add(datos)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw getError(task, "No se pudo guardar la reseña");
                    // Recalcula el promedio agregando la nueva calificacion.
                    return db.collection(COL_COMICS).document(comicId).get()
                            .continueWithTask(comicTask -> {
                                DocumentSnapshot comicDoc = comicTask.getResult();
                                long countActual = toLong(comicDoc, "CantidadCalificaciones");
                                Double avgActual = toDouble(comicDoc, "PromedioCalificacion");
                                long nuevoCount = countActual + 1;
                                double nuevoAvg = avgActual == null
                                        ? calificacion
                                        : (avgActual * countActual + calificacion) / nuevoCount;
                                Map<String, Object> update = new HashMap<>();
                                update.put("CantidadCalificaciones", nuevoCount);
                                update.put("PromedioCalificacion", nuevoAvg);
                                return db.collection(COL_COMICS).document(comicId).update(update);
                            });
                });
    }

    // Actualiza una reseña existente y recalcula el promedio.
    public static Task<Void> updateReview(String comicId, String reviewId, String descripcion, int calificacion) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference refResena = db.collection(COL_COMICS)
                .document(comicId)
                .collection(SUB_RESENAS)
                .document(reviewId);

        return refResena.get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw getError(task, "No se encontro la reseña");
            DocumentSnapshot resenaDoc = task.getResult();
            if (resenaDoc == null || !resenaDoc.exists()) throw new Exception("Reseña no encontrada.");

            int calAnterior = toInt(resenaDoc, "Calificacion");

            Map<String, Object> datos = new HashMap<>();
            datos.put("Descripcion", descripcion);
            datos.put("Calificacion", calificacion);
            datos.put("Fecha", FieldValue.serverTimestamp());

            return refResena.update(datos).continueWithTask(updateTask ->
                    db.collection(COL_COMICS).document(comicId).get()
                            .continueWithTask(comicTask -> {
                                DocumentSnapshot comicDoc = comicTask.getResult();
                                long count = toLong(comicDoc, "CantidadCalificaciones");
                                Double avg = toDouble(comicDoc, "PromedioCalificacion");
                                if (count > 0 && avg != null) {
                                    double nuevoAvg = (avg * count - calAnterior + calificacion) / count;
                                    return db.collection(COL_COMICS).document(comicId)
                                            .update("PromedioCalificacion", nuevoAvg);
                                }
                                return Tasks.forResult(null);
                            })
            );
        });
    }

    // Elimina una reseña y recalcula el promedio del comic.
    public static Task<Void> deleteReview(String comicId, String reviewId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference refResena = db.collection(COL_COMICS)
                .document(comicId)
                .collection(SUB_RESENAS)
                .document(reviewId);

        return refResena.get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw getError(task, "No se encontro la reseña");
            DocumentSnapshot resenaDoc = task.getResult();
            if (resenaDoc == null || !resenaDoc.exists()) throw new Exception("Reseña no encontrada.");

            int calAnterior = toInt(resenaDoc, "Calificacion");

            return refResena.delete().continueWithTask(deleteTask ->
                    db.collection(COL_COMICS).document(comicId).get()
                            .continueWithTask(comicTask -> {
                                DocumentSnapshot comicDoc = comicTask.getResult();
                                long count = toLong(comicDoc, "CantidadCalificaciones");
                                Double avg = toDouble(comicDoc, "PromedioCalificacion");
                                long nuevoCount = count > 0 ? count - 1 : 0;
                                Map<String, Object> update = new HashMap<>();
                                if (nuevoCount == 0) {
                                    update.put("CantidadCalificaciones", null);
                                    update.put("PromedioCalificacion", null);
                                } else if (avg != null) {
                                    double nuevoAvg = (avg * count - calAnterior) / nuevoCount;
                                    update.put("CantidadCalificaciones", nuevoCount);
                                    update.put("PromedioCalificacion", nuevoAvg);
                                }
                                if (!update.isEmpty()) {
                                    return db.collection(COL_COMICS).document(comicId).update(update);
                                }
                                return Tasks.forResult(null);
                            })
            );
        });
    }

    // Verifica si el usuario tiene al menos un tomo del comic en su biblioteca.
    public static Task<Boolean> hasVolumeInLibrary(String uid, String comicId) {
        if (uid == null || uid.trim().isEmpty() || comicId == null || comicId.trim().isEmpty()) {
            return Tasks.forResult(false);
        }

        return FirebaseFirestore.getInstance()
                .collection(COL_USUARIO)
                .document(uid)
                .collection(SUB_BIBLIOTECA)
                .document(DOC_COLECCION)
                .collection(SUB_COMICS_LIB)
                .document(comicId)
                .collection("tomos")
                .limit(1)
                .get()
                .continueWith(tomosTask -> {
                    if (!tomosTask.isSuccessful()) {
                        return false;
                    }
                    QuerySnapshot snap = tomosTask.getResult();
                    return snap != null && !snap.isEmpty();
                });
    }

    // Construye un objeto de pagina de reseñas desde el snapshot.
    private static ComicReviewPageData buildPage(QuerySnapshot snapshot, int pageSize) {
        if (snapshot == null || snapshot.isEmpty()) {
            return new ComicReviewPageData(new ArrayList<>(), null, false);
        }
        List<ComicReviewData> resenas = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            ComicReviewData resena = buildReview(doc);
            if (resena != null) resenas.add(resena);
        }
        String ultimoId = !snapshot.getDocuments().isEmpty()
                ? snapshot.getDocuments().get(snapshot.size() - 1).getId()
                : null;
        boolean hayMas = snapshot.size() == pageSize;
        return new ComicReviewPageData(resenas, ultimoId, hayMas);
    }

    // Convierte un DocumentSnapshot en ComicReviewData.
    private static ComicReviewData buildReview(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Date fecha = null;
        Object fechaObj = doc.get("Fecha");
        if (fechaObj instanceof Timestamp) fecha = ((Timestamp) fechaObj).toDate();
        Number calNum = doc.getLong("Calificacion");
        int calificacion = calNum != null ? calNum.intValue() : 0;
        return new ComicReviewData(
                doc.getId(),
                doc.getString("UserID"),
                doc.getString("Descripcion"),
                calificacion,
                fecha
        );
    }

    // Convierte un campo numerico a long con valor default 0.
    private static long toLong(DocumentSnapshot doc, String campo) {
        if (doc == null || !doc.exists()) return 0L;
        Long val = doc.getLong(campo);
        return val != null ? val : 0L;
    }

    // Convierte un campo numerico a double o null si no existe.
    private static Double toDouble(DocumentSnapshot doc, String campo) {
        if (doc == null || !doc.exists()) return null;
        Object val = doc.get(campo);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return null;
    }

    // Convierte un campo numerico a int con valor default 0.
    private static int toInt(DocumentSnapshot doc, String campo) {
        if (doc == null || !doc.exists()) return 0;
        Long val = doc.getLong(campo);
        return val != null ? val.intValue() : 0;
    }

    // Extrae el error de una tarea fallida.
    private static Exception getError(Task<?> tarea, String mensaje) {
        Exception error = tarea.getException();
        return error != null ? error : new Exception(mensaje);
    }
}
