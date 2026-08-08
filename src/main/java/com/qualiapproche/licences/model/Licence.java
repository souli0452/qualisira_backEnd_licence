package com.qualiapproche.licences.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Une licence émise : ce qui a été vendu, à qui, et le jeton signé qui le prouve.
 *
 * <p>Une licence ne se modifie pas. Le jeton est signé au moment de l'émission : en retoucher
 * ensuite la date de fin ou les modules laisserait la base et le jeton se contredire, et c'est le
 * jeton qui fait foi chez le client. Prolonger un abonnement consiste donc à en émettre une
 * nouvelle — ce qui laisse au passage l'historique de ce qui a été vendu et quand.</p>
 *
 * <p>Les modules et le plafond d'utilisateurs sont recopiés de l'offre, jamais lus à travers elle :
 * une offre remaniée l'an prochain ne doit pas réécrire ce qu'un partenaire a acheté cette
 * année.</p>
 */
@Entity
@Table(name = "licences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Licence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Référence citée au support : « LIC-2026-0007 ». */
    @Column(nullable = false, unique = true, length = 40, updatable = false)
    private String reference;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "partenaire_id", nullable = false, updatable = false)
    private Partenaire partenaire;

    /** Offre d'origine, à titre documentaire ; nulle pour un essai ou une licence sur mesure. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "offre_id")
    private OffreAbonnement offre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TypeLicence type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutLicence statut;

    @Column(nullable = false, updatable = false)
    private LocalDate debut;

    @Column(nullable = false, updatable = false)
    private LocalDate fin;

    @Column(nullable = false, updatable = false)
    private int utilisateursMax;

    /**
     * Montant facturé, figé à l'émission.
     *
     * <p>Recopié depuis l'offre et non référencé, comme le sont les modules et le plafond
     * d'utilisateurs : un tarif révisé au catalogue réécrirait sinon rétroactivement le chiffre
     * d'affaires d'exercices clos, et deux éditions du même bilan ne donneraient pas le même
     * total.</p>
     *
     * <p>Nul pour un essai — il n'est pas facturé — et pour une licence dont le prix s'est
     * négocié hors de l'outil.</p>
     */
    @Column(precision = 14, scale = 2, updatable = false)
    private BigDecimal montant;

    @Column(length = 3, updatable = false)
    private String devise;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "licences_modules", joinColumns = @JoinColumn(name = "licence_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 40)
    @Builder.Default
    private Set<ModuleQualiSira> modules = new LinkedHashSet<>();

    /**
     * Le jeton signé, tel qu'il est remis au partenaire.
     *
     * <p>Conservé pour pouvoir le renvoyer sans réémettre : un administrateur qui a perdu son
     * courriel doit pouvoir récupérer sa licence, et une réémission changerait la référence.</p>
     */
    @Column(nullable = false, length = 4000, updatable = false)
    private String jeton;

    @Column(nullable = false, updatable = false)
    private LocalDateTime emiseLe;

    @Column(length = 120, updatable = false)
    private String emisePar;

    @Column(length = 500)
    private String motifRevocation;

    private LocalDateTime revoqueeLe;

    /**
     * Dernière remise au partenaire, et à quelle adresse.
     *
     * <p>Consigné parce que c'est la question qui revient : « la lui a-t-on envoyée ? ». Sans
     * trace, on renvoie deux fois ou pas du tout, et l'on découvre le second cas quand le client
     * appelle pour dire que son installation refuse de démarrer.</p>
     */
    private LocalDateTime envoyeeLe;

    @Column(length = 160)
    private String envoyeeA;

    /**
     * État réel à la date du jour, calculé et non stocké.
     *
     * <p>Une licence expire d'elle-même : aucun traitement n'a besoin de passer pour le constater.
     * Le produit s'en aperçoit d'ailleurs seul, hors ligne, en lisant la date de fin du jeton — ce
     * qui reste vrai même si cet outil ne tourne plus.</p>
     */
    public StatutLicence statutReel() {
        if (statut == StatutLicence.REVOQUEE) {
            return StatutLicence.REVOQUEE;
        }
        LocalDate aujourdhui = LocalDate.now();
        if (aujourdhui.isBefore(debut)) {
            return StatutLicence.A_VENIR;
        }
        return aujourdhui.isAfter(fin) ? StatutLicence.EXPIREE : StatutLicence.ACTIVE;
    }

    /** Négatif une fois le terme passé : « expirée depuis 12 jours » se lit alors directement. */
    public long joursRestants() {
        return ChronoUnit.DAYS.between(LocalDate.now(), fin);
    }
}
