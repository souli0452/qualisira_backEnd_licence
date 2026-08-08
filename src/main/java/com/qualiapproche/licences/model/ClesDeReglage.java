package com.qualiapproche.licences.model;

/**
 * Les clés que le code cite, réunies en un seul endroit.
 *
 * <p>Une clé écrite en toutes lettres à l'endroit qui la lit se recopie de travers un jour ou
 * l'autre : {@code COURRIEL_SITE} devient {@code COURRIEL_SITE_WEB}, la lecture ne rend plus rien,
 * et le pied de courriel part amputé sans qu'aucune erreur ne survienne. Nommées ici, elles ne se
 * saisissent qu'une fois.</p>
 */
public final class ClesDeReglage {

    /** Nom de l'éditeur, affiché au bas des courriels. */
    public static final String COURRIEL_MARQUE = "COURRIEL_MARQUE";

    /** Adresse à laquelle le destinataire d'une licence peut demander de l'aide. */
    public static final String COURRIEL_CONTACT_EMAIL = "COURRIEL_CONTACT_EMAIL";

    /** Téléphone affiché au bas des courriels. */
    public static final String COURRIEL_CONTACT_TELEPHONE = "COURRIEL_CONTACT_TELEPHONE";

    /** Adresse du site, affichée au bas des courriels. */
    public static final String COURRIEL_SITE = "COURRIEL_SITE";

    /** Logo affiché au bas des courriels, porté en base64 dans la valeur. */
    public static final String COURRIEL_LOGO = "COURRIEL_LOGO";

    private ClesDeReglage() {
    }
}
