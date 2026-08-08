package com.qualiapproche.licences.web;

import com.qualiapproche.licences.dto.PageVue;
import com.qualiapproche.licences.dto.PartenaireVue;
import com.qualiapproche.licences.model.Partenaire;
import com.qualiapproche.licences.service.PartenaireService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/partenaires")
@RequiredArgsConstructor
public class PartenaireController {

    private final PartenaireService service;

    /**
     * Une page de partenaires, filtrée par le serveur.
     *
     * <p>La recherche est un paramètre et non un tri côté écran : filtrer dans le navigateur ne
     * porterait que sur la page affichée, et un partenaire absent de celle-ci passerait pour
     * inexistant.</p>
     */
    @GetMapping
    @PreAuthorize("hasAuthority('PARTENAIRE_LIRE')")
    public PageVue<PartenaireVue> lister(
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int taille) {
        return service.lister(recherche,
                PageRequest.of(page, taille, Sort.by(Sort.Order.asc("raisonSociale").ignoreCase())));
    }

    /**
     * Tous les partenaires, pour les listes déroulantes.
     *
     * <p>Une liste de choix ne se pagine pas : celui qu'on cherche serait au-delà de la première
     * page, sans que rien ne le dise.</p>
     */
    @GetMapping("/selection")
    @PreAuthorize("hasAuthority('PARTENAIRE_LIRE')")
    public List<Partenaire> selection() {
        return service.selection();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTENAIRE_LIRE')")
    public Partenaire parId(@PathVariable UUID id) {
        return service.parId(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PARTENAIRE_CREER')")
    public Partenaire creer(@Valid @RequestBody Partenaire partenaire, Principal auteur) {
        return service.creer(partenaire, auteur != null ? auteur.getName() : "inconnu");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PARTENAIRE_MODIFIER')")
    public Partenaire modifier(@PathVariable UUID id, @Valid @RequestBody Partenaire partenaire) {
        return service.modifier(id, partenaire);
    }
}
