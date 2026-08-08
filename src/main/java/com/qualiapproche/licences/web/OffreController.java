package com.qualiapproche.licences.web;

import com.qualiapproche.licences.model.ModuleQualiSira;
import com.qualiapproche.licences.model.OffreAbonnement;
import com.qualiapproche.licences.service.OffreService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/offres")
@RequiredArgsConstructor
public class OffreController {

    private final OffreService service;

    @GetMapping
    @PreAuthorize("hasAuthority('OFFRE_LIRE')")
    public List<OffreAbonnement> lister() {
        return service.lister();
    }

    /** Les modules vendables, avec leur intitulé : l'écran n'a pas à les recopier. */
    @GetMapping("/modules")
    @PreAuthorize("hasAuthority('OFFRE_LIRE')")
    public List<Map<String, String>> modules() {
        return Arrays.stream(ModuleQualiSira.values())
                .map(module -> Map.of(
                        "code", module.name(),
                        "libelle", module.getLibelle(),
                        "description", module.getDescription()))
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OFFRE_CREER')")
    public OffreAbonnement creer(@Valid @RequestBody OffreAbonnement offre) {
        return service.creer(offre);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('OFFRE_MODIFIER')")
    public OffreAbonnement modifier(@PathVariable UUID id, @Valid @RequestBody OffreAbonnement offre) {
        return service.modifier(id, offre);
    }
}
