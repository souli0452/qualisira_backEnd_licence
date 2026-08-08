package com.qualiapproche.licences.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Une page de résultats, telle que l'écran l'attend.
 *
 * <p>Une enveloppe à nous plutôt que le {@code Page} de Spring : la forme JSON de ce dernier
 * dépend de sa version — Spring Boot avertit lui-même qu'elle n'est pas stable — et l'écran s'y
 * accrocherait sur une trentaine de propriétés dont il n'utilise que quatre.</p>
 *
 * <p>{@link #pages} est calculé plutôt que déduit côté écran : un total de 0 donne 0 page et non
 * une page vide, et cette arithmétique-là se refait de travers une fois sur deux.</p>
 */
public record PageVue<T>(
        List<T> contenu,
        /** Numéro de la page rendue, à partir de 0. */
        int page,
        int taille,
        /** Nombre total de lignes, toutes pages confondues — c'est lui que la pagination affiche. */
        long total,
        int pages) {

    public static <T> PageVue<T> de(Page<T> page) {
        return new PageVue<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    /** La même, en convertissant chaque ligne — l'entité ne sort jamais telle quelle. */
    public static <E, T> PageVue<T> de(Page<E> page, Function<E, T> versLaVue) {
        return new PageVue<>(page.getContent().stream().map(versLaVue).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
