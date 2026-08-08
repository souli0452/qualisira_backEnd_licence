package com.qualiapproche.licences.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Une action autorisable, telle qu'elle existe en base.
 *
 * <p>Les droits ne sont jamais contrôlés sur le rôle mais sur la permission : un contrôle posé sur
 * le rôle — « seul l'éditeur peut émettre » — obligerait à retoucher le code chaque fois qu'un
 * profil de plus doit émettre. Ici, il suffit d'ajouter la permission au rôle depuis l'écran.</p>
 *
 * <p>Table à part plutôt que simple colonne de texte : un rôle en désigne plusieurs, plusieurs
 * rôles désignent la même, et la table de jonction {@code roles_permissions} porte cette relation.
 * Le {@link #code} est ce qu'écrivent les {@code @PreAuthorize} des contrôleurs.</p>
 *
 * <p>Les lignes sont posées au démarrage à partir de {@link PermissionQualiSira} : la liste
 * appartient au code, seule son attribution aux rôles s'administre.</p>
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** « LICENCE_EMETTRE » — l'habilitation Spring, mot pour mot. */
    @NotBlank
    @Column(nullable = false, unique = true, length = 60, updatable = false)
    private String code;

    /** Regroupement d'affichage : « Licences », « Comptes ». */
    @Column(nullable = false, length = 60)
    private String domaine;

    @Column(nullable = false, length = 200)
    private String libelle;

    public static Permission de(PermissionQualiSira reference) {
        return Permission.builder()
                .code(reference.name())
                .domaine(reference.getDomaine())
                .libelle(reference.getLibelle())
                .build();
    }
}
