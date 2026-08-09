package com.qualiapproche.licences.model;

import java.time.LocalDate;

/**
 * L'unité dans laquelle s'exprime la durée d'une offre.
 *
 * <p>Une durée et son unité, plutôt que deux champs dont un seul s'applique : « 7 » et « jours »
 * ne laissent aucune place au doute, là où un {@code dureeMois} à zéro accompagné d'un
 * {@code dureeJours} à sept obligerait chaque lecteur à deviner lequel prime.</p>
 */
public enum UniteDeDuree {

    /** Pour les périodes courtes qu'un nombre de mois exprime mal — un essai de sept jours. */
    JOURS("jour", "jours") {
        @Override
        public LocalDate ajouterA(LocalDate debut, int duree) {
            return debut.plusDays(duree);
        }
    },

    /**
     * Le cas courant d'un abonnement.
     *
     * <p>En mois et non en jours : un abonnement d'un an souscrit le 29 février doit se terminer
     * le 28 février suivant, ce qu'un compte en jours manquerait d'un jour une année sur quatre.</p>
     */
    MOIS("mois", "mois") {
        @Override
        public LocalDate ajouterA(LocalDate debut, int duree) {
            return debut.plusMonths(duree);
        }
    };

    private final String singulier;
    private final String pluriel;

    UniteDeDuree(String singulier, String pluriel) {
        this.singulier = singulier;
        this.pluriel = pluriel;
    }

    public abstract LocalDate ajouterA(LocalDate debut, int duree);

    /** « 1 mois », « 7 jours » — l'écran et les courriels n'ont pas à accorder eux-mêmes. */
    public String accorder(int duree) {
        return duree + " " + (duree > 1 ? pluriel : singulier);
    }
}
