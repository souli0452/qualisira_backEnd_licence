package com.qualiapproche.licences.dto;

import com.qualiapproche.licences.model.EntreeDeJournal;

import java.time.LocalDateTime;
import java.util.UUID;

/** Une entrée du journal, telle que l'écran la montre. */
public record EntreeDeJournalVue(
        UUID id,
        LocalDateTime quand,
        String auteur,
        String action,
        String objet,
        String objetId,
        String requete,
        boolean abouti,
        String motif,
        String adresse,
        long duree) {

    public static EntreeDeJournalVue de(EntreeDeJournal e) {
        return new EntreeDeJournalVue(e.getId(), e.getQuand(), e.getAuteur(), e.getAction(),
                e.getObjet(), e.getObjetId(), e.getRequete(), e.isAbouti(), e.getMotif(),
                e.getAdresse(), e.getDuree());
    }
}
