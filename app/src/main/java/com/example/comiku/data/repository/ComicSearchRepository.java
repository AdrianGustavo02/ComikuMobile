package com.example.comiku.data.repository;

import com.example.comiku.data.model.ComicSearchResult;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ComicSearchRepository {
    private static final String COLECCION_COMICS = "comics";

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
                .continueWith(task -> {
                    List<ComicSearchResult> resultados = new ArrayList<>();
                    
                    if (task.getResult() == null || task.getResult().isEmpty()) {
                        return resultados;
                    }

                    // Filtra por búsqueda parcial case-insensitive
                    for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                        String nombre = doc.getString("Nombre");
                        if (nombre != null && 
                            nombre.toLowerCase(Locale.ROOT).contains(terminoNormalizado)) {
                            
                            List<String> autores = (List<String>) doc.get("Autor");
                            String editorial = doc.getString("Editorial");
                            String paisEditorial = doc.getString("PaisEditorial");

                            ComicSearchResult resultado = new ComicSearchResult(
                                    doc.getId(),
                                    nombre,
                                    autores,
                                    editorial,
                                    paisEditorial
                            );
                            resultados.add(resultado);
                        }
                    }

                    return resultados;
                });
    }
}
