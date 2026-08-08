package com.qualiapproche.licences.model;

/**
 * Modules vendables de QualiSira.
 *
 * <p>Recopie volontaire de {@code com.qualiapproche.common.enumeration.ModuleAbonnement} : cet
 * outil ne dépend pas du produit — il lui survit, et doit pouvoir émettre des licences pour une
 * version antérieure comme pour la prochaine. Ces noms sont un <b>contrat</b> : ils sont inscrits
 * tels quels dans les licences, et l'application livrée les compare par égalité de chaîne.</p>
 */
public enum ModuleQualiSira {

    NON_CONFORMITE("Non-conformités", "Déclaration, traitement et clôture des non-conformités"),
    DOCUMENTAIRE("Gestion documentaire", "Documents qualité, versions, circuits de validation"),
    RECLAMATION("Réclamations", "Réclamations clients et leur traitement"),
    RISQUE("Risques", "Identification et suivi des risques"),
    AUDIT("Audits", "Audits internes et actions correctives"),
    FORMATION("Formation & ressources", "Formations, fournisseurs, prestataires, produits"),
    REGLEMENTATION("Réglementation", "Veille et exigences réglementaires"),
    EVALUATION("Évaluation", "Critères et campagnes d'évaluation"),
    CONTEXTE("Contexte", "Contexte de l'organisation et parties intéressées");

    private final String libelle;
    private final String description;

    ModuleQualiSira(String libelle, String description) {
        this.libelle = libelle;
        this.description = description;
    }

    public String getLibelle() {
        return libelle;
    }

    public String getDescription() {
        return description;
    }
}
