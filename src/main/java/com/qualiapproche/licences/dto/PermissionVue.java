package com.qualiapproche.licences.dto;

import com.qualiapproche.licences.model.Permission;

/** Une permission telle que l'écran la coche : son code, et de quoi la ranger et la lire. */
public record PermissionVue(String code, String domaine, String libelle) {

    public static PermissionVue de(Permission permission) {
        return new PermissionVue(permission.getCode(), permission.getDomaine(),
                permission.getLibelle());
    }
}
