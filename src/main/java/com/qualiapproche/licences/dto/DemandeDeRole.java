package com.qualiapproche.licences.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/**
 * Ce qu'on demande à créer ou à retoucher comme rôle.
 *
 * <p>Les permissions sont désignées par leur code : l'écran coche des cases dans la liste rendue
 * par {@code /api/roles/permissions}, et renvoie les codes retenus. Un code inconnu est refusé
 * plutôt qu'ignoré — un rôle amputé d'une permission mal orthographiée n'ouvrirait rien, et la
 * cause resterait invisible.</p>
 */
public record DemandeDeRole(

        /** Immuable après création ; ignoré à la modification. */
        @NotBlank String code,

        @NotBlank String libelle,

        String description,

        Set<String> permissions
) {
}
