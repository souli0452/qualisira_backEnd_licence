package com.qualiapproche.licences.dto;

import com.qualiapproche.licences.model.Utilisateur;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Un compte tel que l'écran le montre — sans l'empreinte du mot de passe, qui n'a rien à faire
 * dans une réponse.
 *
 * <p>Les permissions effectives sont jointes aux rôles : c'est ce que l'administrateur veut
 * vérifier après avoir attribué un rôle, sans avoir à ouvrir l'écran des rôles pour le
 * déplier.</p>
 */
public record UtilisateurVue(
        UUID id,
        String identifiant,
        String nomComplet,
        String email,
        List<String> roles,
        List<String> permissions,
        boolean actif,
        boolean superAdmin,
        boolean doitChangerMotDePasse,
        LocalDateTime creeLe,
        String creePar,
        LocalDateTime derniereConnexion
) {

    public static UtilisateurVue de(Utilisateur utilisateur) {
        return new UtilisateurVue(
                utilisateur.getId(),
                utilisateur.getIdentifiant(),
                utilisateur.getNomComplet(),
                utilisateur.getEmail(),
                List.copyOf(utilisateur.codesDesRoles()),
                List.copyOf(utilisateur.codesDesPermissions()),
                utilisateur.isActif(),
                utilisateur.estSuperAdmin(),
                utilisateur.isDoitChangerMotDePasse(),
                utilisateur.getCreeLe(),
                utilisateur.getCreePar(),
                utilisateur.getDerniereConnexion());
    }
}
