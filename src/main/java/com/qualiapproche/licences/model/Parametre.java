package com.qualiapproche.licences.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un réglage désigné par une clé : coordonnées du pied de courriel, logo de l'éditeur.
 *
 * <p>Ces valeurs n'ont pas de table propre à leur nature — un téléphone n'est pas un référentiel —
 * et se multiplieraient en autant de colonnes et d'écrans. Une clé, une valeur : elles se modifient
 * depuis l'application, sans livrer une version ni intervenir en base. Le pied des courriels de
 * licence en est le premier usage.</p>
 *
 * <p><b>La clé ne se modifie pas</b> ({@code updatable = false}, et le service refuse la
 * tentative). C'est par elle que le code désigne un réglage : la renommer romprait silencieusement
 * ce qui la lit, et l'ancien nom ne rendrait plus rien. La liste des clés appartient au code, comme
 * celle des permissions — ce qui est administrable, c'est leur <b>valeur</b>.</p>
 */
@Entity
@Table(name = "parametres")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parametre {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Identité du réglage, en majuscules et soulignés : {@code COURRIEL_CONTACT_EMAIL}. */
    @Column(nullable = false, unique = true, updatable = false, length = 80)
    private String cle;

    /**
     * Ce que le réglage vaut. Vide, il est simplement ignoré par ce qui le lit.
     *
     * <p>En {@code text} : un logo y tient en base64, et une colonne bornée le tronquerait sans
     * rien dire — l'image arriverait illisible chez le destinataire.</p>
     */
    @Column(columnDefinition = "text")
    private String valeur;

    /** Intitulé lisible, pour l'écran d'administration. */
    @Column(nullable = false)
    private String libelle;

    /** À quoi ce réglage sert, et où il apparaît. */
    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeParametre type;

    private LocalDateTime modifieLe;

    private String modifiePar;
}
