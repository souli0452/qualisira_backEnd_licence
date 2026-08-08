package com.qualiapproche.licences.web;

import com.qualiapproche.licences.dto.CompteCree;
import com.qualiapproche.licences.dto.DemandeDeCompte;
import com.qualiapproche.licences.dto.PageVue;
import com.qualiapproche.licences.dto.UtilisateurVue;
import com.qualiapproche.licences.service.UtilisateurService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les comptes du back-office — ce que le super administrateur ouvre aux autres.
 *
 * <p>Chaque méthode exige sa permission, et non un rôle : ouvrir la création de comptes à un
 * nouveau profil se fait en cochant {@code UTILISATEUR_CREER} sur son rôle, sans toucher à ce
 * fichier.</p>
 */
@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurService service;

    @GetMapping
    @PreAuthorize("hasAuthority('UTILISATEUR_LIRE')")
    public PageVue<UtilisateurVue> lister(
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int taille) {
        return service.lister(recherche,
                PageRequest.of(page, taille, Sort.by(Sort.Order.asc("identifiant").ignoreCase())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('UTILISATEUR_LIRE')")
    public UtilisateurVue parId(@PathVariable UUID id) {
        return service.vue(id);
    }

    /**
     * Ouvre un compte.
     *
     * <p>La réponse porte le mot de passe provisoire quand il a été tiré au hasard : c'est la
     * seule fois où il est lisible, la base n'en garde que l'empreinte.</p>
     */
    @PostMapping
    @PreAuthorize("hasAuthority('UTILISATEUR_CREER')")
    public CompteCree creer(@Valid @RequestBody DemandeDeCompte demande, Principal auteur) {
        return service.creer(demande, auteur != null ? auteur.getName() : "inconnu");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UTILISATEUR_MODIFIER')")
    public UtilisateurVue modifier(@PathVariable UUID id, @Valid @RequestBody DemandeDeCompte demande) {
        return service.modifier(id, demande);
    }

    /** Suspendre ou rétablir, sans passer par le formulaire complet. */
    @PostMapping("/{id}/activation")
    @PreAuthorize("hasAuthority('UTILISATEUR_MODIFIER')")
    public UtilisateurVue activer(@PathVariable UUID id, @RequestBody Map<String, Boolean> corps) {
        return service.activer(id, Boolean.TRUE.equals(corps.get("actif")));
    }

    /**
     * Remet un mot de passe à celui qui a perdu le sien.
     *
     * <p>Le mot de passe tiré au hasard n'est rendu qu'ici. Un mot de passe soumis par
     * l'administrateur n'est pas renvoyé : il le connaît déjà.</p>
     */
    @PostMapping("/{id}/mot-de-passe")
    @PreAuthorize("hasAuthority('UTILISATEUR_MOT_DE_PASSE')")
    public Map<String, Object> reinitialiser(@PathVariable UUID id,
                                             @RequestBody(required = false) Map<String, String> corps) {
        String provisoire = service.reinitialiserLeMotDePasse(id,
                corps != null ? corps.get("motDePasse") : null);
        Map<String, Object> reponse = new HashMap<>();
        reponse.put("motDePasseProvisoire", provisoire);
        return reponse;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('UTILISATEUR_SUPPRIMER')")
    public Map<String, Object> supprimer(@PathVariable UUID id) {
        service.supprimer(id);
        return Map.of("supprime", true);
    }
}
