package com.qualiapproche.licences.model;

/**
 * Le catalogue des permissions : la liste de référence, tenue par le code.
 *
 * <p>Les permissions vivent en base — voir {@link Permission} —, mais leurs codes sont un
 * <b>contrat</b> : ce sont eux qu'écrivent les {@code @PreAuthorize} des contrôleurs. Les laisser
 * saisir librement depuis l'écran, c'est permettre de créer une permission
 * {@code LICENCE_EMETRE} qui n'ouvrira jamais rien, sans que rien ne le signale.</p>
 *
 * <p>Cette énumération est donc la source, et la table son reflet : au démarrage, chaque valeur
 * absente est insérée et chaque libellé mis à jour. Ce qui est administrable, c'est
 * l'<b>attribution</b> des permissions aux rôles, pas la liste elle-même.</p>
 *
 * <p>Aucun code ne commence par {@code ROLE_}, préfixe que Spring réserve aux rôles — d'où
 * {@code HABILITATION_*} pour la gestion des rôles eux-mêmes.</p>
 */
public enum PermissionQualiSira {

    PARTENAIRE_LIRE("Partenaires", "Consulter le fichier des clients"),
    PARTENAIRE_CREER("Partenaires", "Ajouter un partenaire"),
    PARTENAIRE_MODIFIER("Partenaires", "Modifier un dossier partenaire"),

    OFFRE_LIRE("Offres", "Consulter le catalogue commercial"),
    OFFRE_CREER("Offres", "Ajouter une offre"),
    OFFRE_MODIFIER("Offres", "Modifier une offre"),

    LICENCE_LIRE("Licences", "Consulter les licences émises"),
    LICENCE_EMETTRE("Licences", "Émettre une licence — signer avec la clé de l'éditeur"),
    LICENCE_ENVOYER("Licences", "Remettre une licence au partenaire par courriel"),
    LICENCE_REVOQUER("Licences", "Révoquer une licence"),
    LICENCE_VERIFIER("Licences", "Relire une licence et lire la clé publique"),

    UTILISATEUR_LIRE("Comptes", "Consulter les comptes"),
    UTILISATEUR_CREER("Comptes", "Créer un compte"),
    UTILISATEUR_MODIFIER("Comptes", "Modifier un compte, l'activer ou le suspendre"),
    UTILISATEUR_SUPPRIMER("Comptes", "Supprimer un compte"),
    UTILISATEUR_MOT_DE_PASSE("Comptes", "Réinitialiser le mot de passe d'un compte"),

    HABILITATION_LIRE("Rôles", "Consulter les rôles et leurs permissions"),
    HABILITATION_GERER("Rôles", "Créer un rôle et choisir ses permissions"),

    REGLAGE_LIRE("Réglages", "Consulter les réglages — coordonnées et logo des courriels"),
    REGLAGE_MODIFIER("Réglages", "Modifier un réglage"),

    // Aucune permission d'écriture : le journal ne se modifie pas depuis l'application. Un
    // registre qu'on peut retoucher ne prouve rien.
    JOURNAL_LIRE("Journal", "Consulter le journal des actions"),

    // Distincte de LICENCE_LIRE : cet écran montre le chiffre d'affaires, qu'un commercial en
    // lecture seule sur les licences n'a pas à connaître.
    TABLEAU_DE_BORD_LIRE("Tableau de bord", "Consulter les indicateurs et les revenus");

    private final String domaine;
    private final String libelle;

    PermissionQualiSira(String domaine, String libelle) {
        this.domaine = domaine;
        this.libelle = libelle;
    }

    public String getDomaine() {
        return domaine;
    }

    public String getLibelle() {
        return libelle;
    }
}
