package com.qualiapproche.licences.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Un paquet de permissions qu'on attribue d'un geste : « Super administrateur », « Éditeur »,
 * « Lecteur ».
 *
 * <p>Le rôle n'ouvre aucune porte par lui-même — il ne fait que porter les
 * {@link Permission permissions}, seules contrôlées. Ouvrir une action à un profil de plus se fait
 * donc en cochant une case sur cet écran, et non en recompilant.</p>
 *
 * <p>Le {@link #code} est aussi le nom du rôle côté Keycloak : un compte du royaume portant
 * {@code EDITEUR} reçoit ici les permissions du rôle {@code EDITEUR}. Les deux modes
 * d'authentification partagent ainsi une seule définition des droits.</p>
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    /** Le rôle qui peut tout, dont la gestion des comptes. Toujours présent, jamais amputé. */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Identifiant stable — « SUPER_ADMIN », « EDITEUR ».
     *
     * <p>Non modifiable : il désigne le rôle dans les comptes qui le portent comme dans le royaume
     * Keycloak, que cette base ne pilote pas.</p>
     */
    @NotBlank
    @Column(nullable = false, unique = true, length = 60, updatable = false)
    private String code;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String libelle;

    @Column(length = 500)
    private String description;

    /**
     * Les permissions du rôle, par la table de jonction {@code roles_permissions}.
     *
     * <p>Chargées d'office : elles sont lues à chaque authentification, pour construire les
     * habilitations de l'utilisateur — une lecture différée échouerait là, hors transaction.</p>
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "roles_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<Permission> permissions = new LinkedHashSet<>();

    /**
     * Rôle fourni par l'application, que l'écran ne peut ni supprimer ni vider.
     *
     * <p>Sans cela, retirer par mégarde {@code UTILISATEUR_MODIFIER} au super administrateur
     * fermerait la gestion des comptes à tout le monde, définitivement : plus personne ne pourrait
     * rétablir la permission qui permet de la rétablir.</p>
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean systeme = false;

    public boolean estSuperAdmin() {
        return SUPER_ADMIN.equalsIgnoreCase(code);
    }

    /** Les codes de ses permissions, triés — ce que lit l'écran. */
    public Set<String> codesDesPermissions() {
        Set<String> codes = new TreeSet<>();
        permissions.forEach(permission -> codes.add(permission.getCode()));
        return codes;
    }
}
