package com.qualiapproche.licences.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un client de QualiSira : l'organisation chez qui le produit est installé.
 *
 * <p>Tout ce qui l'identifie est connu ici, et <b>rien de ce qui relève de sa licence</b> : les
 * modules souscrits, la durée et le nombre d'utilisateurs appartiennent à la licence, qui est
 * émise puis figée. Confondre les deux — comme le fait aujourd'hui la colonne
 * {@code licence_active} portée par la structure du produit — c'est ne plus savoir ce qui a été
 * vendu une fois l'abonnement modifié.</p>
 *
 * <p>Le {@link #code} est inscrit dans chaque licence : c'est lui qui empêche qu'une licence
 * émise pour un partenaire serve chez un autre.</p>
 */
@Entity
@Table(name = "partenaires")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partenaire {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Identifiant stable et lisible — « CHU-BF », « MINSANTE ».
     *
     * <p>Inscrit dans les licences : le changer invaliderait celles déjà émises, d'où son
     * immuabilité une fois le partenaire créé.</p>
     */
    @NotBlank
    @Column(nullable = false, unique = true, length = 40, updatable = false)
    private String code;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String raisonSociale;

    @Column(length = 40)
    private String sigle;

    @Column(length = 120)
    private String secteurActivite;

    @Column(length = 120)
    private String contactNom;

    @Email
    @Column(length = 160)
    private String contactEmail;

    @Column(length = 40)
    private String contactTelephone;

    @Column(length = 250)
    private String adresse;

    @Column(length = 120)
    private String ville;

    @Column(length = 120)
    private String pays;

    /** Ce qu'on garde en tête sur ce dossier : conditions négociées, interlocuteur, historique. */
    @Column(length = 2000)
    private String notes;

    /**
     * Un partenaire inactif ne se voit plus émettre de licence.
     *
     * <p>Cela ne révoque rien : les licences déjà remises restent valables jusqu'à leur terme.
     * Une licence est un engagement, désactiver un dossier commercial n'y met pas fin.</p>
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean actif = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @Column(length = 120)
    private String creePar;

    public void prendreDate(String auteur) {
        this.creeLe = LocalDateTime.now();
        this.creePar = auteur;
    }
}
