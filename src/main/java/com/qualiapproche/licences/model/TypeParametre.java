package com.qualiapproche.licences.model;

/** Nature de la valeur d'un {@link Parametre}, pour que l'écran sache la présenter et la vérifier. */
public enum TypeParametre {

    /** Texte libre : un nom d'éditeur, une mention. */
    TEXTE,

    /** Adresse de courriel. */
    COURRIEL,

    /** Numéro de téléphone. */
    TELEPHONE,

    /** Adresse web. */
    URL,

    /**
     * Une image, portée par la valeur elle-même sous forme de {@code data:} en base64.
     *
     * <p>Et non une adresse distante : le logo part dans des courriels lus hors de tout réseau
     * d'entreprise, et une image servie par un lien serait bloquée par défaut par la plupart des
     * clients de messagerie — le pied arriverait amputé. Portée dans le message, elle s'affiche
     * partout.</p>
     */
    IMAGE
}
