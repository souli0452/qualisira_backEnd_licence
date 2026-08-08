package com.qualiapproche.licences.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ce que quelqu'un a fait dans cet outil, et quand.
 *
 * <p>Cet outil signe les licences : savoir qui a émis quoi, qui a ouvert un compte, qui a révoqué
 * une licence n'est pas un confort. Le nom de l'émetteur est déjà inscrit dans chaque licence,
 * mais rien ne disait qui avait suspendu un compte, changé les permissions d'un rôle ou remplacé
 * une coordonnée — des gestes qui ne laissent aucune trace dans leur résultat.</p>
 *
 * <h2>Ce qui n'est pas conservé</h2>
 *
 * <p>Le <b>corps des requêtes</b>. Volontairement : y figurent des mots de passe à la création
 * d'un compte et à la réinitialisation, et un journal qui les recopierait serait plus dangereux
 * que l'absence de journal. On retient ce qui a été fait et sur quoi, jamais avec quelles
 * valeurs.</p>
 *
 * <p>Une entrée ne se modifie ni ne se supprime depuis l'application : un journal qu'on peut
 * retoucher ne prouve rien. Sa purge relève de l'exploitation, sur une politique de conservation
 * décidée ailleurs.</p>
 */
@Entity
@Table(name = "journal", indexes = {
        @Index(name = "idx_journal_quand", columnList = "quand"),
        @Index(name = "idx_journal_auteur", columnList = "auteur")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntreeDeJournal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Horodatage du serveur, seul repère commun à tous les postes. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime quand;

    /**
     * Qui — l'identifiant de session.
     *
     * <p>{@code anonyme} pour une tentative de connexion refusée : c'est précisément l'entrée qu'on
     * vient chercher quand on soupçonne quelque chose, et l'écarter parce que personne n'était
     * authentifié la rendrait invisible.</p>
     */
    @Column(nullable = false, updatable = false, length = 120)
    private String auteur;

    /** Ce qui a été fait, en clair — « Émettre une licence ». */
    @Column(nullable = false, updatable = false, length = 120)
    private String action;

    /** Sur quoi — « Licence », « Compte », « Rôle ». */
    @Column(updatable = false, length = 60)
    private String objet;

    /** L'identifiant de l'objet touché, quand la requête le désigne. */
    @Column(updatable = false, length = 80)
    private String objetId;

    /** La requête telle qu'elle est arrivée : « POST /api/licences ». */
    @Column(nullable = false, updatable = false, length = 300)
    private String requete;

    /**
     * Vrai si l'action a abouti.
     *
     * <p>Les échecs sont conservés, et ce sont souvent eux qui comptent : un refus de connexion
     * répété, une révocation tentée sans la permission. Ne garder que les succès ferait un journal
     * qui ne raconte que ce qui allait bien.</p>
     */
    @Column(nullable = false, updatable = false)
    private boolean abouti;

    /** Le motif du refus, tel que l'utilisateur l'a lu. */
    @Column(updatable = false, length = 500)
    private String motif;

    /** L'adresse d'où venait la requête, pour distinguer deux usages d'un même compte. */
    @Column(updatable = false, length = 60)
    private String adresse;

    /** Durée en millisecondes — une émission qui traîne se voit ici avant qu'on n'appelle. */
    @Column(updatable = false)
    private long duree;
}
