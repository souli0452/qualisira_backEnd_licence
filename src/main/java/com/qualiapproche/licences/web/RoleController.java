package com.qualiapproche.licences.web;

import com.qualiapproche.licences.dto.DemandeDeRole;
import com.qualiapproche.licences.dto.PermissionVue;
import com.qualiapproche.licences.dto.RoleVue;
import com.qualiapproche.licences.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les rôles et le catalogue des permissions.
 *
 * <p>La liste des permissions est rendue telle qu'elle existe en base : l'écran y coche des cases
 * plutôt que de recopier des codes, et un code recopié de travers ne peut donc pas s'y glisser.</p>
 */
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService service;

    @GetMapping
    @PreAuthorize("hasAuthority('HABILITATION_LIRE')")
    public List<RoleVue> lister() {
        return service.lister();
    }

    /** Le catalogue complet, groupé par domaine côté écran. */
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('HABILITATION_LIRE')")
    public List<PermissionVue> permissions() {
        return service.catalogue();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('HABILITATION_GERER')")
    public RoleVue creer(@Valid @RequestBody DemandeDeRole demande) {
        return service.creer(demande);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('HABILITATION_GERER')")
    public RoleVue modifier(@PathVariable UUID id, @Valid @RequestBody DemandeDeRole demande) {
        return service.modifier(id, demande);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('HABILITATION_GERER')")
    public Map<String, Object> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return Map.of("supprime", true);
    }
}
