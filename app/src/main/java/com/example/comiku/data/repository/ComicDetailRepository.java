package com.example.comiku.data.repository;

import com.example.comiku.data.model.ComicDetailData;
import com.example.comiku.data.model.VolumeDetailData;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ComicDetailRepository {
    private static final String COLECCION_COMICS = "comics";
    private static final String SUBCOLECCION_TOMOS = "tomos";

    private ComicDetailRepository() {
    }

    // Obtiene los detalles completos de un comic por ID.
    public static Task<ComicDetailData> getComicDetail(String comicId) {
        return FirebaseFirestore.getInstance()
                .collection(COLECCION_COMICS)
                .document(comicId)
                .get()
                .continueWith(task -> {
                    DocumentSnapshot doc = task.getResult();
                    if (doc == null || !doc.exists()) {
                        return null;
                    }

                    return new ComicDetailData(
                            doc.getId(),
                            doc.getString("Nombre"),
                            toStringList(doc.get("Autor")),
                            doc.getString("Editorial"),
                            doc.getString("PaisEditorial"),
                            doc.getString("Estado"),
                            doc.getString("Formato"),
                            toStringList(doc.get("Genero")),
                            doc.getString("Descripcion"),
                            toDouble(doc.get("PromedioCalificacion")),
                            toLong(doc.get("CantidadCalificaciones"))
                    );
                });
    }

    // Obtiene todos los tomos de un comic.
    public static Task<List<VolumeDetailData>> getComicVolumes(String comicId) {
        return FirebaseFirestore.getInstance()
                .collection(COLECCION_COMICS)
                .document(comicId)
                .collection(SUBCOLECCION_TOMOS)
                .get()
                .continueWith(task -> {
                    List<VolumeDetailData> tomos = new ArrayList<>();
                    QuerySnapshot snapshot = task.getResult();

                    if (snapshot == null || snapshot.isEmpty()) {
                        return tomos;
                    }

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        VolumeDetailData tomo = new VolumeDetailData(
                                doc.getId(),
                                comicId,
                                toInteger(doc.get("NumeroTomo")),
                                doc.getBoolean("TomoUnico"),
                                toLong(doc.get("ISBN")),
                                doc.getString("FechaPublicacion"),
                                toMap(doc.get("Portada"))
                        );
                        tomos.add(tomo);
                    }

                    return tomos;
                });
    }

    // Obtiene los detalles de un tomo especifico.
    public static Task<VolumeDetailData> getVolumeDetail(String comicId, String tomoId) {
        return FirebaseFirestore.getInstance()
                .collection(COLECCION_COMICS)
                .document(comicId)
                .collection(SUBCOLECCION_TOMOS)
                .document(tomoId)
                .get()
                .continueWith(task -> {
                    DocumentSnapshot doc = task.getResult();
                    if (doc == null || !doc.exists()) {
                        return null;
                    }

                    return new VolumeDetailData(
                            doc.getId(),
                            comicId,
                            toInteger(doc.get("NumeroTomo")),
                            doc.getBoolean("TomoUnico"),
                            toLong(doc.get("ISBN")),
                            doc.getString("FechaPublicacion"),
                            toMap(doc.get("Portada"))
                    );
                });
    }

    // Obtiene todos los comics del catalogo ordenados por nombre.
    public static Task<List<ComicDetailData>> getAllComics() {
        return FirebaseFirestore.getInstance()
                .collection(COLECCION_COMICS)
                .get()
                .continueWith(task -> {
                    List<ComicDetailData> comics = new ArrayList<>();
                    QuerySnapshot snapshot = task.getResult();

                    if (snapshot == null || snapshot.isEmpty()) {
                        return comics;
                    }

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        comics.add(new ComicDetailData(
                                doc.getId(),
                                doc.getString("Nombre"),
                                toStringList(doc.get("Autor")),
                                doc.getString("Editorial"),
                                doc.getString("PaisEditorial"),
                                doc.getString("Estado"),
                                doc.getString("Formato"),
                                toStringList(doc.get("Genero")),
                                doc.getString("Descripcion"),
                                toDouble(doc.get("PromedioCalificacion")),
                                toLong(doc.get("CantidadCalificaciones"))
                        ));
                    }

                    comics.sort((a, b) -> String.valueOf(a.nombre == null ? "" : a.nombre)
                            .toLowerCase(java.util.Locale.ROOT)
                            .compareTo(String.valueOf(b.nombre == null ? "" : b.nombre)
                                    .toLowerCase(java.util.Locale.ROOT)));
                    return comics;
                });
    }

    // Busca un tomo por ISBN en toda la base.
    public static Task<VolumeDetailData> findVolumeByIsbn(long isbn) {
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        TaskCompletionSource<VolumeDetailData> resultado = new TaskCompletionSource<>();
        String isbnTexto = String.valueOf(isbn);

        firestore.collectionGroup(SUBCOLECCION_TOMOS)
                .whereEqualTo("ISBN", isbn)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshotNumero -> {
                    VolumeDetailData tomo = mapearPrimerTomo(snapshotNumero);
                    if (tomo != null) {
                        resultado.setResult(tomo);
                        return;
                    }
                    buscarTomoPorIsbnTexto(firestore, isbn, isbnTexto, resultado);
                })
                .addOnFailureListener(error ->
                        buscarTomoPorIsbnTexto(firestore, isbn, isbnTexto, resultado));

        return resultado.getTask();
    }

    // Intenta buscar el tomo con ISBN guardado como texto.
    private static void buscarTomoPorIsbnTexto(
            FirebaseFirestore firestore,
            long isbn,
            String isbnTexto,
            TaskCompletionSource<VolumeDetailData> resultado
    ) {
        firestore.collectionGroup(SUBCOLECCION_TOMOS)
                .whereEqualTo("ISBN", isbnTexto)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshotTexto -> {
                    VolumeDetailData tomo = mapearPrimerTomo(snapshotTexto);
                    if (tomo != null) {
                        resultado.setResult(tomo);
                        return;
                    }
                    buscarTomoRecorriendoComics(firestore, isbn, isbnTexto, resultado);
                })
                .addOnFailureListener(error ->
                        buscarTomoRecorriendoComics(firestore, isbn, isbnTexto, resultado));
    }

    // Recorre comics y busca en su subcoleccion para evitar fallos de collectionGroup.
    private static void buscarTomoRecorriendoComics(
            FirebaseFirestore firestore,
            long isbn,
            String isbnTexto,
            TaskCompletionSource<VolumeDetailData> resultado
    ) {
        firestore.collection(COLECCION_COMICS)
                .limit(400)
                .get()
                .addOnSuccessListener(snapshotComics -> {
                    List<DocumentSnapshot> comics = snapshotComics != null
                            ? snapshotComics.getDocuments()
                            : new ArrayList<>();
                    buscarEnComicPorIndice(firestore, comics, 0, isbn, isbnTexto, resultado);
                })
                .addOnFailureListener(error -> resultado.setResult(null));
    }

    // Busca un tomo por ISBN dentro de cada comic de forma secuencial.
    private static void buscarEnComicPorIndice(
            FirebaseFirestore firestore,
            List<DocumentSnapshot> comics,
            int indice,
            long isbn,
            String isbnTexto,
            TaskCompletionSource<VolumeDetailData> resultado
    ) {
        if (resultado.getTask().isComplete()) {
            return;
        }
        if (comics == null || indice >= comics.size()) {
            resultado.setResult(null);
            return;
        }

        DocumentSnapshot comic = comics.get(indice);
        if (comic == null || comic.getId() == null || comic.getId().trim().isEmpty()) {
            buscarEnComicPorIndice(firestore, comics, indice + 1, isbn, isbnTexto, resultado);
            return;
        }

        firestore.collection(COLECCION_COMICS)
                .document(comic.getId())
                .collection(SUBCOLECCION_TOMOS)
                .whereEqualTo("ISBN", isbn)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshotNumero -> {
                    VolumeDetailData tomoNumero = mapearPrimerTomo(snapshotNumero);
                    if (tomoNumero != null) {
                        resultado.setResult(tomoNumero);
                        return;
                    }

                    firestore.collection(COLECCION_COMICS)
                            .document(comic.getId())
                            .collection(SUBCOLECCION_TOMOS)
                .whereEqualTo("ISBN", isbnTexto)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshotTomos -> {
                    VolumeDetailData tomo = mapearPrimerTomo(snapshotTomos);
                    if (tomo != null) {
                        resultado.setResult(tomo);
                    } else {
                        buscarEnComicPorIndice(firestore, comics, indice + 1, isbn, isbnTexto, resultado);
                    }
                })
                .addOnFailureListener(error ->
                        buscarEnComicPorIndice(firestore, comics, indice + 1, isbn, isbnTexto, resultado));
                })
                .addOnFailureListener(error ->
                        buscarEnComicPorIndice(firestore, comics, indice + 1, isbn, isbnTexto, resultado));
    }

    // Convierte el primer documento del snapshot en un tomo si existe.
    private static VolumeDetailData mapearPrimerTomo(QuerySnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        DocumentSnapshot doc = snapshot.getDocuments().get(0);
        String comicId = "";
        if (doc.getReference().getParent() != null
                && doc.getReference().getParent().getParent() != null) {
            comicId = doc.getReference().getParent().getParent().getId();
        }
        return new VolumeDetailData(
                doc.getId(),
                comicId,
                toInteger(doc.get("NumeroTomo")),
                doc.getBoolean("TomoUnico"),
                toLong(doc.get("ISBN")),
                doc.getString("FechaPublicacion"),
                toMap(doc.get("Portada"))
        );
    }

    // Convierte un valor de Firestore a lista de textos.
    private static List<String> toStringList(Object value) {
        List<String> lista = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item != null) {
                    lista.add(String.valueOf(item));
                }
            }
        }
        return lista;
    }

    // Convierte un valor numerico a entero.
    private static Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    // Convierte un valor numerico a decimal.
    private static Double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    // Convierte un valor numerico a entero largo.
    private static Long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    // Convierte un valor de portada a mapa.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
}
