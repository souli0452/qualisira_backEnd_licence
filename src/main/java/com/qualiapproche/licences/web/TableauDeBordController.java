package com.qualiapproche.licences.web;

import com.qualiapproche.licences.dto.TableauDeBordVue;
import com.qualiapproche.licences.service.TableauDeBordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les indicateurs de l'écran d'accueil.
 *
 * <p>Sa propre permission, et non {@code LICENCE_LIRE} : cet écran montre le chiffre d'affaires,
 * qu'un commercial en lecture seule sur les licences n'a pas à connaître. Les deux se donnent
 * séparément.</p>
 */
@RestController
@RequestMapping("/api/tableau-de-bord")
@RequiredArgsConstructor
public class TableauDeBordController {

    private final TableauDeBordService service;

    @GetMapping
    @PreAuthorize("hasAuthority('TABLEAU_DE_BORD_LIRE')")
    public TableauDeBordVue indicateurs() {
        return service.composer();
    }
}
