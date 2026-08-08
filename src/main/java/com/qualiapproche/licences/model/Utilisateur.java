package com.qualiapproche.licences.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Un compte du back-office.
 *
 * <p>Les comptes vivent en base plutôt qu'en mémoire : le compte unique déclaré dans la
 * configuration ne permettait ni de savoir qui a émis quelle licence — tout le monde s'appelait
 * « admin » —, ni d'ouvrir l'outil à un commercial sans lui donner la main sur les clés.</p>
 *
 * <p>Les droits ne sont pas portés par le compte mais par ses {@link Role rôles}, eux-mêmes faits
 * de {@link Permission permissions}. Retirer un droit à toute une équipe se fait donc en un seul
 * endroit.</p>
 */
@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Ce qui est saisi à la connexion, et inscrit dans « émise par » sur chaque licence. */
    @NotBlank
    @Column(nullable = false, unique = true, length = 80)
    private String identifiant;

    /**
     * Empreinte BCrypt — jamais le mot de passe.
     *
     * <p>{@code @JsonIgnore} en plus des vues dédiées : une entité renvoyée par mégarde depuis un
     * contrôleur emporterait sinon l'empreinte de tous les comptes dans la réponse.</p>
     */
    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String motDePasse;

    @Column(length = 160)
    private String nomComplet;

    @Email
    @Column(length = 160)
    private String email;

    /**
     * Chargés d'office : ils sont lus à chaque authentification, hors de toute transaction
     * ouverte par l'appelant.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "utilisateurs_roles",
            joinColumns = @JoinColumn(name = "utilisateur_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new LinkedHashSet<>();

    /**
     * Un compte suspendu ne peut plus ouvrir de session.
     *
     * <p>Suspendre plutôt que supprimer : le nom reste lisible sur les licences déjà émises, qui
     * portent leur auteur.</p>
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean actif = true;

    /**
     * Vrai tant que le mot de passe est celui remis par l'administrateur.
     *
     * <p>Celui qui crée le compte connaît forcément ce mot de passe : tant qu'il n'a pas été
     * changé, le compte n'est pas vraiment personnel — et « émise par » ne désigne alors personne
     * avec certitude.</p>
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean doitChangerMotDePasse = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creeLe;

    @Column(length = 120)
    private String creePar;

    private LocalDateTime derniereConnexion;

    public void prendreDate(String auteur) {
        this.creeLe = LocalDateTime.now();
        this.creePar = auteur;
    }

    /** Les codes de permission de tous ses rôles réunis — ses habilitations effectives. */
    public Set<String> codesDesPermissions() {
        Set<String> reunies = new TreeSet<>();
        roles.forEach(role -> reunies.addAll(role.codesDesPermissions()));
        return reunies;
    }

    /** Les codes de ses rôles, triés. */
    public Set<String> codesDesRoles() {
        Set<String> codes = new TreeSet<>();
        roles.forEach(role -> codes.add(role.getCode()));
        return codes;
    }

    public boolean estSuperAdmin() {
        return roles.stream().anyMatch(Role::estSuperAdmin);
    }
}
