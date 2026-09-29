package com.example.comiku.core.image;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//En el emulador, se usa Google Fotos por lo que se devuelve un ID como nombre archivo

// Resuelve el nombre visible de un archivo seleccionado por Uri.
public final class FileNameResolver {
    private FileNameResolver() {
    }

    // Devuelve nombre de archivo legible para mostrar y guardar.
    public static String resolveFileName(Context contexto, Uri uriArchivo, String nombreFallback) {
        if (contexto == null || uriArchivo == null) {
            return nombreFallback;
        }

        ContentResolver resolver = contexto.getContentResolver();
        List<String> candidatos = new ArrayList<>();
        candidatos.add(resolveOpenableDisplayName(resolver, uriArchivo));
        candidatos.add(resolveDisplayNameFromUriColumns(resolver, uriArchivo));
        candidatos.add(resolveDisplayNameFromMediaStoreId(resolver, uriArchivo));
        candidatos.add(resolveFileSchemeName(uriArchivo));
        candidatos.add(resolveLastPathSegmentName(uriArchivo));

        for (String candidato : candidatos) {
            String nombreNormalizado = normalizeName(candidato);
            if (isUsableFileName(nombreNormalizado)) {
                return nombreNormalizado;
            }
        }

        String baseFallback = normalizeName(nombreFallback);
        if (TextUtils.isEmpty(baseFallback)) {
            baseFallback = "imagen";
        }
        return ensureExtension(baseFallback, resolver.getType(uriArchivo));
    }

    // Intenta leer el nombre
    private static String resolveOpenableDisplayName(ContentResolver resolver, Uri uriArchivo) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(
                    uriArchivo,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                int indiceNombre = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (indiceNombre >= 0) {
                    String nombre = cursor.getString(indiceNombre);
                    if (!TextUtils.isEmpty(nombre)) {
                        return nombre;
                    }
                }
            }
        } catch (SecurityException ignored) {
        } catch (IllegalArgumentException ignored) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "";
    }

    // Intenta leer nombre con columnas comunes del proveedor.
    private static String resolveDisplayNameFromUriColumns(ContentResolver resolver, Uri uriArchivo) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(
                    uriArchivo,
                    new String[]{MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.TITLE},
                    null,
                    null,
                    null
            );
            if (cursor == null || !cursor.moveToFirst()) {
                return "";
            }
            int indiceDisplayName = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
            if (indiceDisplayName >= 0) {
                String nombre = cursor.getString(indiceDisplayName);
                if (!TextUtils.isEmpty(nombre)) {
                    return nombre;
                }
            }
            int indiceTitle = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE);
            if (indiceTitle >= 0) {
                String titulo = cursor.getString(indiceTitle);
                if (!TextUtils.isEmpty(titulo)) {
                    return titulo;
                }
            }
        } catch (SecurityException ignored) {
            return "";
        } catch (IllegalArgumentException ignored) {
            return "";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "";
    }

    // Intenta resolver el nombre desde id de documento
    private static String resolveDisplayNameFromMediaStoreId(ContentResolver resolver, Uri uriArchivo) {
        String idDocumento;
        try {
            idDocumento = DocumentsContract.getDocumentId(uriArchivo);
        } catch (IllegalArgumentException ignored) {
            return "";
        }

        if (TextUtils.isEmpty(idDocumento) || !idDocumento.contains(":")) {
            return "";
        }

        String[] partes = idDocumento.split(":");
        if (partes.length < 2 || TextUtils.isEmpty(partes[1])) {
            return "";
        }

        String idNumerico = partes[1];
        Cursor cursor = null;
        try {
            cursor = resolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.Images.Media.DISPLAY_NAME},
                    MediaStore.Images.Media._ID + "=?",
                    new String[]{idNumerico},
                    null
            );
            if (cursor != null && cursor.moveToFirst()) {
                int indiceNombre = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
                if (indiceNombre >= 0) {
                    String nombre = cursor.getString(indiceNombre);
                    if (!TextUtils.isEmpty(nombre)) {
                        return nombre;
                    }
                }
            }
        } catch (SecurityException ignored) {
            return "";
        } catch (IllegalArgumentException ignored) {
            return "";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "";
    }

    // Extrae nombre desde uri file://.
    private static String resolveFileSchemeName(Uri uriArchivo) {
        if (!"file".equalsIgnoreCase(uriArchivo.getScheme())) {
            return "";
        }
        String ruta = uriArchivo.getPath();
        if (TextUtils.isEmpty(ruta)) {
            return "";
        }
        return new File(ruta).getName();
    }

    // Extrae nombre desde ultimo segmento de la uri.
    private static String resolveLastPathSegmentName(Uri uriArchivo) {
        String ultimoSegmento = uriArchivo.getLastPathSegment();
        return TextUtils.isEmpty(ultimoSegmento) ? "" : ultimoSegmento;
    }

    // Limpia prefijos de ruta en nombres.
    private static String normalizeName(String nombreArchivo) {
        if (TextUtils.isEmpty(nombreArchivo)) {
            return "";
        }
        String nombre = nombreArchivo.trim();
        int indiceBarra = Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\'));
        if (indiceBarra >= 0 && indiceBarra < nombre.length() - 1) {
            nombre = nombre.substring(indiceBarra + 1);
        }
        return nombre.trim();
    }

    // Decide si el nombre parece util y no un id
    private static boolean isUsableFileName(String nombreArchivo) {
        if (TextUtils.isEmpty(nombreArchivo)) {
            return false;
        }
        String nombre = nombreArchivo.trim();
        if (nombre.matches("^\\d+$")) {
            return false;
        }
        String nombreSinExtension = nombre.contains(".")
                ? nombre.substring(0, nombre.lastIndexOf('.'))
                : nombre;
        String candidatoNormalizado = nombreSinExtension.toLowerCase(Locale.ROOT);
        if (candidatoNormalizado.matches("^(image|img|photo|foto|msf|raw):?\\d+$")) {
            return false;
        }
        if (candidatoNormalizado.matches("^[a-z_\\-]*\\d{5,}$")) {
            return false;
        }
        return nombre.matches(".*[A-Za-z].*") || nombre.contains(".");
    }

    // Asegura extension cuando el fallback no trae una.
    private static String ensureExtension(String nombreBase, String tipoContenido) {
        if (nombreBase.contains(".")) {
            return nombreBase;
        }
        String extension = "";
        if (!TextUtils.isEmpty(tipoContenido)) {
            extension = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(tipoContenido.toLowerCase(Locale.ROOT));
        }
        if (TextUtils.isEmpty(extension)) {
            extension = "jpg";
        }
        return nombreBase + "." + extension;
    }
}
