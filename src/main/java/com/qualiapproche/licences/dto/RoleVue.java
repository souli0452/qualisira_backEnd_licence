package com.qualiapproche.licences.dto;

import com.qualiapproche.licences.model.Role;

import java.util.List;
import java.util.UUID;

/**
 * Un rôle tel que l'écran le montre : ses permissions à plat, et le nombre de comptes qui le
 * portent.
 *
 * <p>Ce décompte évite la question qu'on se pose juste avant de retirer une permission — « qui
 * cela va-t-il gêner ? » — et à laquelle l'écran ne répondait qu'en changeant de page.</p>
 */
public record RoleVue(
        UUID id,
        String code,
        String libelle,
        String description,
        List<String> permissions,
        boolean systeme,
        long comptes
) {

    public static RoleVue de(Role role, long comptes) {
        return new RoleVue(
                role.getId(),
                role.getCode(),
                role.getLibelle(),
                role.getDescription(),
                List.copyOf(role.codesDesPermissions()),
                role.isSysteme(),
                comptes);
    }
}
