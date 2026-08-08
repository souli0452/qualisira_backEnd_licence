package com.qualiapproche.licences.dto;

import com.qualiapproche.licences.model.ModuleQualiSira;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Ce qu'on demande à émettre.
 *
 * <p>Trois façons de remplir cette demande, du plus courant au plus rare :</p>
 * <ul>
 *   <li>un partenaire et une <b>offre</b> — le cas ordinaire, tout le reste en découle ;</li>
 *   <li>un partenaire et une offre, avec quelques valeurs <b>surchargées</b> — la remise
 *       négociée, les six mois offerts ;</li>
 *   <li>un partenaire seul et tout à la main — la licence <b>sur mesure</b>.</li>
 * </ul>
 *
 * <p>Les champs surchargeables sont nuls quand ils ne le sont pas : c'est ce qui distingue
 * « laisser l'offre décider » de « imposer cette valeur », qu'une valeur par défaut confondrait.</p>
 */
public record DemandeDeLicence(

        UUID partenaireId,

        /** Offre souscrite ; nulle pour une licence sur mesure. */
        UUID offreId,

        /** Premier jour couvert ; par défaut, aujourd'hui. */
        LocalDate debut,

        /** Durée en mois, si elle diffère de celle de l'offre. */
        Integer dureeMois,

        /** Durée en jours, pour les périodes courtes qu'un nombre de mois exprime mal. */
        Integer dureeJours,

        /** Plafond d'utilisateurs, s'il diffère de celui de l'offre. {@code 0} vaut « sans limite ». */
        Integer utilisateursMax,

        /** Modules, s'ils diffèrent de ceux de l'offre. */
        Set<ModuleQualiSira> modules
) {
}
