package com.qualiapproche.licences.dto;

import com.qualiapproche.licences.model.Partenaire;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un partenaire tel que l'écran le montre, avec l'état de ses licences déjà calculé.
 *
 * <p>Ces deux valeurs — le nombre de licences et la date de fin de celle en cours — étaient
 * jusqu'ici déduites côté écran en parcourant <b>toutes</b> les licences, que la page devait donc
 * charger entièrement. Cela ne pouvait pas survivre à la pagination : la liste n'aurait plus
 * compté que les licences de la page affichée, et le compte se serait mis à baisser en tournant
 * les pages, sans que rien ne le signale.</p>
 */
public record PartenaireVue(
        UUID id,
        String code,
        String raisonSociale,
        String sigle,
        String secteurActivite,
        String contactNom,
        String contactEmail,
        String contactTelephone,
        String adresse,
        String ville,
        String pays,
        String notes,
        boolean actif,
        LocalDateTime creeLe,
        String creePar,
        /** Nombre de licences émises pour ce partenaire, toutes périodes confondues. */
        long nbLicences,
        /** Fin de la licence en cours, ou {@code null} s'il n'en a aucune de valide aujourd'hui. */
        LocalDate licenceActiveFin) {

    public static PartenaireVue de(Partenaire p, long nbLicences, LocalDate licenceActiveFin) {
        return new PartenaireVue(
                p.getId(), p.getCode(), p.getRaisonSociale(), p.getSigle(), p.getSecteurActivite(),
                p.getContactNom(), p.getContactEmail(), p.getContactTelephone(), p.getAdresse(),
                p.getVille(), p.getPays(), p.getNotes(), p.isActif(), p.getCreeLe(), p.getCreePar(),
                nbLicences, licenceActiveFin);
    }
}
