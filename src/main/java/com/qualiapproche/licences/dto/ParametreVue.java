package com.qualiapproche.licences.dto;

import com.qualiapproche.licences.model.Parametre;
import com.qualiapproche.licences.model.TypeParametre;

import java.time.LocalDateTime;
import java.util.UUID;

/** Un réglage tel que l'écran d'administration le présente. */
public record ParametreVue(
        UUID id,
        String cle,
        String valeur,
        String libelle,
        String description,
        TypeParametre type,
        LocalDateTime modifieLe,
        String modifiePar) {

    public static ParametreVue de(Parametre parametre) {
        return new ParametreVue(
                parametre.getId(),
                parametre.getCle(),
                parametre.getValeur(),
                parametre.getLibelle(),
                parametre.getDescription(),
                parametre.getType(),
                parametre.getModifieLe(),
                parametre.getModifiePar());
    }
}
