package com.example.comiku.data.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class UserShelfComicGroupData {
    public final String comicId;
    public final ComicDetailData comic;
    public final Date fechaAgregadoComic;
    public final List<UserShelfVolumeItemData> tomos;

    // Guarda un comic con sus tomos dentro de una lista.
    public UserShelfComicGroupData(
            String comicId,
            ComicDetailData comic,
            Date fechaAgregadoComic,
            List<UserShelfVolumeItemData> tomos
    ) {
        this.comicId = comicId;
        this.comic = comic;
        this.fechaAgregadoComic = fechaAgregadoComic;
        this.tomos = tomos != null ? new ArrayList<>(tomos) : new ArrayList<>();
    }

    // Devuelve los tomos ordenados para mostrarlos mejor.
    public List<UserShelfVolumeItemData> getSortedVolumes() {
        List<UserShelfVolumeItemData> sortedVolumes = new ArrayList<>(tomos);
        sortedVolumes.sort((a, b) -> {
            VolumeDetailData tomoA = a.tomo;
            VolumeDetailData tomoB = b.tomo;

            if (tomoA.tomoUnico && !tomoB.tomoUnico) {
                return -1;
            }
            if (!tomoA.tomoUnico && tomoB.tomoUnico) {
                return 1;
            }
            if (tomoA.numeroTomo == null && tomoB.numeroTomo == null) {
                return 0;
            }
            if (tomoA.numeroTomo == null) {
                return 1;
            }
            if (tomoB.numeroTomo == null) {
                return -1;
            }
            return tomoA.numeroTomo - tomoB.numeroTomo;
        });
        return sortedVolumes;
    }

    // Devuelve el tomo que mejor representa al comic.
    public UserShelfVolumeItemData getFeaturedVolume() {
        List<UserShelfVolumeItemData> sortedVolumes = getSortedVolumes();
        if (sortedVolumes.isEmpty()) {
            return null;
        }

        for (UserShelfVolumeItemData tomo : sortedVolumes) {
            if (tomo.tomo != null && tomo.tomo.numeroTomo != null && tomo.tomo.numeroTomo == 1) {
                return tomo;
            }
        }

        for (UserShelfVolumeItemData tomo : sortedVolumes) {
            if (tomo.tomo != null && tomo.tomo.tomoUnico) {
                return tomo;
            }
        }

        return sortedVolumes.get(0);
    }
}
