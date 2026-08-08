package com.qualiapproche.licences.web;

import com.qualiapproche.licences.dto.EntreeDeJournalVue;
import com.qualiapproche.licences.dto.PageVue;
import com.qualiapproche.licences.service.JournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Le journal des actions : qui a fait quoi, et quand.
 *
 * <p>En lecture seule, et sans exception : une entrée qu'on pourrait modifier ou retirer depuis
 * l'application ne prouverait rien. Sa purge relève de l'exploitation, pas d'un écran.</p>
 */
@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalController {

    private final JournalService service;

    @GetMapping
    @PreAuthorize("hasAuthority('JOURNAL_LIRE')")
    public PageVue<EntreeDeJournalVue> lister(
            @RequestParam(required = false) String recherche,
            @RequestParam(required = false) String auteur,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate depuis,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate jusqua,
            @RequestParam(required = false) Boolean abouti,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int taille) {
        // Les plus récentes d'abord : on ouvre ce journal pour savoir ce qui vient de se passer.
        return service.lister(recherche, auteur, depuis, jusqua, abouti,
                PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "quand")));
    }
}
