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
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Une formule commerciale : ce qu'on vend, et à quelles conditions.
 *
 * <p>« Essentiel — 12 mois, 25 utilisateurs, non-conformités et documentaire ». Émettre une
 * licence consiste alors à choisir un partenaire et une offre, plutôt qu'à ressaisir chaque fois
 * la durée, le plafond d'utilisateurs et la liste des modules — ressaisie où se glissent les
 * erreurs qu'on ne découvre qu'à l'installation, chez le client.</p>
 *
 * <p>L'offre est un <b>modèle</b>, non une référence : ses valeurs sont recopiées dans la licence
 * au moment de l'émission. Retoucher une offre n'altère donc aucune licence déjà remise.</p>
 */
@Entity
@Table(name = "offres_abonnement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OffreAbonnement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String libelle;

    @Column(length = 500)
    private String description;

    /**
     * Durée couverte. La date de fin s'en déduit à l'émission.
     *
     * <p>Accompagnée de son {@link #uniteDuree} : un essai se compte en jours, un abonnement en
     * mois, et le second n'est pas le premier multiplié par trente — un an souscrit le 29 février
     * se termine le 28 février suivant.</p>
     */
    @Min(1)
    @Column(nullable = false)
    private int duree;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "unite_duree", nullable = false, length = 10)
    private UniteDeDuree uniteDuree = UniteDeDuree.MOIS;

    /**
     * Plafond d'utilisateurs actifs ; {@code 0} vaut « sans limite ».
     *
     * <p>Zéro plutôt qu'une valeur absente : une limite nulle serait comprise comme « aucun
     * utilisateur autorisé », ce qui fermerait l'application au lieu de l'ouvrir.</p>
     */
    @Min(0)
    @Column(nullable = false)
    private int utilisateursMax;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "offres_modules", joinColumns = @JoinColumn(name = "offre_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 40)
    @Builder.Default
    private Set<ModuleQualiSira> modules = new LinkedHashSet<>();

    /**
     * Prix de l'offre, en nombre — c'est lui qui s'additionne.
     *
     * <p>Une chaîne libre tenait ce rôle jusqu'ici, et ne pouvait rien produire : « 150 000 FCFA »
     * et « à négocier » ne se somment pas. Le tableau de bord ne savait donc rien dire des
     * revenus.</p>
     *
     * <p>Nul reste admis, et veut dire « à négocier » — ce qui se distingue d'un zéro, qui
     * affirmerait la gratuité.</p>
     */
    @Column(precision = 14, scale = 2)
    private BigDecimal montant;

    /**
     * Devise du montant, en code ISO — {@code XOF}, {@code EUR}.
     *
     * <p>Portée par l'offre plutôt que fixée dans le code : un éditeur qui vend hors de sa zone
     * facture dans deux monnaies, et additionner des sommes sans devise donnerait un total qui ne
     * veut rien dire.</p>
     */
    @Builder.Default
    @Column(nullable = false, length = 3)
    private String devise = "XOF";

    @Builder.Default
    @Column(nullable = false)
    private boolean actif = true;
}
