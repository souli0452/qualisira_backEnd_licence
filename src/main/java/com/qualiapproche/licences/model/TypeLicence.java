package com.qualiapproche.licences.model;

/** Nature d'une licence : vendue, ou consentie pour essai. */
public enum TypeLicence {

    /** Souscription payante, adossée à une offre. */
    COMMERCIALE,

    /**
     * Essai gratuit : courte durée, tous les modules ouverts.
     *
     * <p>Tous les modules, parce qu'un essai sert à montrer le produit — en restreindre la moitié
     * revient à faire essayer autre chose que ce qu'on vend. La brièveté suffit à le distinguer
     * d'une souscription.</p>
     */
    ESSAI
}
