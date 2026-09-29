package com.example.comiku.data.repository;

import com.example.comiku.data.model.ComicSearchResult;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ComicSearchRepository {
    private static final String COLECCION_COMICS = "comics";
    private static final String SUBCOLECCION_TOMOS = "tomos";

    private ComicSearchRepository() {
    }

    // Busca comics por nombre de forma case-insensitive con búsqueda parcial.
    public static Task<List<ComicSearchResult>> searchComicsByName(String termino) {
        if (termino == null || termino.trim().isEmpty()) {
            return Tasks.forResult(new ArrayList<>());
        }

        String terminoNormalizado = termino.trim().toLowerCase(Locale.ROOT);
        FirebaseFirestore firestore = FirebaseFirestore.getInstance();

        // Obtiene comics (limite prudencial para no sobrecargar)
        return firestore.collection(COLECCION_COMICS)
                .limit(500)
                .get()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException() != null
                                ? task.getException()
                                : new RuntimeException("No se pudo buscar comics");
                    }

                    List<Task<ComicSearchResult>> tareasResultados = new ArrayList<>();
                    QuerySnapshot snapshot = task.getResult();
                    if (snapshot == null || snapshot.isEmpty()) {
                        return Tasks.forResult(new ArrayList<>());
                    }

                    // Filtra por busqueda parcial y agrega la portada principal.
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String nombre = doc.getString("Nombre");
                        if (nombre != null
                                && nombre.toLowerCase(Locale.ROOT).contains(terminoNormalizado)) {
                            tareasResultados.add(buildSearchResult(firestore, doc, nombre));
                        }
                    }

                    if (tareasResultados.isEmpty()) {
                        return Tasks.forResult(new ArrayList<>());
                    }

                    return Tasks.whenAllSuccess(tareasResultados)
                            .continueWith(taskResultados -> {
                                List<ComicSearchResult> resultados = new ArrayList<>();
                                if (taskResultados.getResult() == null) {
                                    return resultados;
                                }
                                for (Object item : taskResultados.getResult()) {
                                    if (item instanceof ComicSearchResult) {
                                        resultados.add((ComicSearchResult) item);
                                    }
                                }
                                return resultados;
                            });
                });
    }

    // Crea un resultado y resuelve su portada principal.
    private static Task<ComicSearchResult> buildSearchResult(
            FirebaseFirestore firestore,
            DocumentSnapshot doc,
            String nombre
    ) {
        List<String> autores = (List<String>) doc.get("Autor");
        String editorial = doc.getString("Editorial");
        String paisEditorial = doc.getString("PaisEditorial");

        return loadCoverDataUrl(firestore, doc.getId())
                .continueWith(taskPortada -> new ComicSearchResult(
                        doc.getId(),
                        nombre,
                        autores,
                        editorial,
                        paisEditorial,
                        taskPortada.isSuccessful() ? taskPortada.getResult() : null
                ));
    }

    // Busca la portada que se debe mostrar en los resultados.
    private static Task<String> loadCoverDataUrl(FirebaseFirestore firestore, String comicId) {
        return firestore.collection(COLECCION_COMICS)
                .document(comicId)
                .collection(SUBCOLECCION_TOMOS)
                .get()
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        return null;
                    }
                    return selectPreferredCover(task.getResult());
                });
    }

    // Elige la portada unica o la del tomo 1 si hay varios tomos.
    private static String selectPreferredCover(QuerySnapshot snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }

        List<DocumentSnapshot> tomos = snapshot.getDocuments();
        if (tomos.size() == 1) {
            return getCoverDataUrl(tomos.get(0));
        }

        DocumentSnapshot tomoNumeroUno = null;
        DocumentSnapshot tomoMenorNumero = null;
        Integer menorNumero = null;

        for (DocumentSnapshot tomo : tomos) {
            Integer numeroTomo = toInteger(tomo.get("NumeroTomo"));
            if (numeroTomo != null && numeroTomo == 1) {
                tomoNumeroUno = tomo;
                break;
            }
            if (numeroTomo != null && (menorNumero == null || numeroTomo < menorNumero)) {
                menorNumero = numeroTomo;
                tomoMenorNumero = tomo;
            }
        }

        if (tomoNumeroUno != null) {
            return getCoverDataUrl(tomoNumeroUno);
        }
        if (tomoMenorNumero != null) {
            return getCoverDataUrl(tomoMenorNumero);
        }
        return getCoverDataUrl(tomos.get(0));
    }

    // Extrae la dataUrl de la portada desde el documento del tomo.
    private static String getCoverDataUrl(DocumentSnapshot tomo) {
        if (tomo == null) {
            return null;
        }
        Object portada = tomo.get("Portada");
        if (!(portada instanceof Map)) {
            return null;
        }
        Object dataUrl = ((Map<?, ?>) portada).get("dataUrl");
        return dataUrl != null ? String.valueOf(dataUrl) : null;
    }

    // Convierte un valor numerico en entero.
    private static Integer toInteger(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
}
