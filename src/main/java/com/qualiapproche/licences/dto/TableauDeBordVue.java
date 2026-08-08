package com.qualiapproche.licences.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ce que le tableau de bord montre : l'état du parc, et ce qu'il rapporte.
 *
 * <p>Les revenus sont <b>groupés par devise</b> et jamais réunis en un total unique : additionner
 * des francs CFA et des euros donnerait un nombre qui ne veut rien dire, et que personne ne
 * pourrait rapprocher d'une comptabilité.</p>
 *
 * <p>Ils portent sur le montant <b>figé à l'émission</b>, non sur le tarif courant du catalogue :
 * réviser un prix ne réécrit pas un exercice clos.</p>
 */
public record TableauDeBordVue(
        Partenaires partenaires,
        Licences licences,
        List<Revenu> revenusDuMois,
        List<Revenu> revenusDeLAnnee,
        /** Les douze derniers mois, pour voir une tendance plutôt qu'un instantané. */
        List<MoisDeRevenu> douzeDerniersMois,
        List<OffreVendue> offresLesPlusVendues,
        /**
         * Combien de licences facturables ne portent aucun montant.
         *
         * <p>Affiché, et non tu : les licences émises avant que les offres n'aient un prix en
         * comptent pour zéro. Sans cette mention, on lirait un chiffre d'affaires plus bas que le
         * réel en croyant lire le réel.</p>
         */
        long licencesSansMontant) {

    public record Partenaires(long total, long actifs, long sansLicenceEnCours) { }

    public record Licences(
            long total,
            long actives,
            long aVenir,
            long expirees,
            long revoquees,
            /** Celles qui prennent fin dans les 30 jours : ce sont les renouvellements à préparer. */
            long echeantSous30Jours,
            /** Émises mais jamais remises — l'oubli qu'on découvre quand le client appelle. */
            long jamaisEnvoyees) { }

    public record Revenu(String devise, BigDecimal montant, long licences) { }

    /** {@code mois} au format {@code 2026-08}, pour que l'écran ordonne sans interpréter. */
    public record MoisDeRevenu(String mois, String devise, BigDecimal montant, long licences) { }

    public record OffreVendue(String code, String libelle, long licences, BigDecimal montant,
                              String devise) { }
}
