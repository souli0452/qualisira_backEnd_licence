package com.qualiapproche.licences.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/**
 * Ce qu'on demande à créer ou à retoucher comme compte.
 *
 * <p>Le mot de passe est facultatif : laissé vide à la création, il est tiré au hasard et rendu
 * une seule fois à celui qui crée le compte, à charge pour lui de le transmettre. Cela évite la
 * pratique qui consiste à donner à tous le même mot de passe « à changer plus tard ».</p>
 *
 * <p>À la modification, un mot de passe vide veut dire « ne pas y toucher ».</p>
 */
public record DemandeDeCompte(

        /** Immuable après création ; ignoré à la modification. */
        @NotBlank String identifiant,

        String nomComplet,

        @Email String email,

        /** Codes des rôles attribués — au moins un, sans quoi le compte n'ouvre rien. */
        Set<String> roles,

        String motDePasse,

        Boolean actif
) {
}
