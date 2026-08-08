package com.qualiapproche.licences.model;

/**
 * Où en est une licence.
 *
 * <p>Seule {@link #REVOQUEE} est un état décidé et stocké ; les autres se déduisent des dates.
 * Un état d'expiration inscrit en base se serait périmé dès le lendemain, sauf à faire tourner un
 * traitement pour l'entretenir — et à dépendre de lui.</p>
 */
public enum StatutLicence {

    /** Émise, terme non atteint. */
    ACTIVE,

    /** Émise pour une période qui n'a pas encore commencé. */
    A_VENIR,

    /** Terme dépassé. */
    EXPIREE,

    /**
     * Retirée par l'éditeur.
     *
     * <p>À la portée limitée qu'il faut bien connaître : le produit tourne hors ligne chez le
     * partenaire et ne vient jamais demander si sa licence a été révoquée. La révocation vaut donc
     * pour <b>notre</b> suivi — savoir qu'un dossier est clos, ne pas rouvrir de droits dessus —
     * et ne désarme pas une installation en cours. Le seul vrai levier reste la durée : des
     * licences d'un an, renouvelées, plutôt que de longue durée.</p>
     */
    REVOQUEE
}
