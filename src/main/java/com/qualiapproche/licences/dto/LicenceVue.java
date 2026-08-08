package com.qualiapproche.licences.dto;

import com.qualiapproche.licences.model.Licence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Une licence telle que l'écran la montre : à plat, avec l'état et les jours restants déjà
 * calculés.
 *
 * <p>Ces deux valeurs ne sont pas stockées — une licence expire d'elle-même, sans qu'aucun
 * traitement ait à passer. Les calculer au moment de rendre la vue évite qu'un écran affiche
 * « active » sur une licence terminée la veille.</p>
 */
public record LicenceVue(
        UUID id,
        String reference,
        UUID partenaireId,
        String partenaireCode,
        String partenaireNom,
        String offre,
        String type,
        String statut,
        LocalDate debut,
        LocalDate fin,
        long joursRestants,
        int utilisateursMax,
        /** Montant facturé, figé à l'émission. Nul pour un essai ou un prix négocié hors outil. */
        BigDecimal montant,
        String devise,
        List<String> modules,
        String jeton,
        LocalDateTime emiseLe,
        String emisePar,
        String motifRevocation,
        LocalDateTime envoyeeLe,
        String envoyeeA
) {

    public static LicenceVue de(Licence licence) {
        return new LicenceVue(
                licence.getId(),
                licence.getReference(),
                licence.getPartenaire().getId(),
                licence.getPartenaire().getCode(),
                licence.getPartenaire().getRaisonSociale(),
                licence.getOffre() != null ? licence.getOffre().getLibelle() : "Sur mesure",
                licence.getType().name(),
                licence.statutReel().name(),
                licence.getDebut(),
                licence.getFin(),
                licence.joursRestants(),
                licence.getUtilisateursMax(),
                licence.getMontant(),
                licence.getDevise(),
                licence.getModules().stream().map(Enum::name).toList(),
                licence.getJeton(),
                licence.getEmiseLe(),
                licence.getEmisePar(),
                licence.getMotifRevocation(),
                licence.getEnvoyeeLe(),
                licence.getEnvoyeeA());
    }
}
