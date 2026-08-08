package com.qualiapproche.licences.web;

import com.qualiapproche.licences.dto.ParametreVue;
import com.qualiapproche.licences.service.ParametreService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Les réglages de l'application : ce qui figure au bas des courriels de licence.
 *
 * <p>On ne crée ni ne supprime rien ici : la liste des réglages appartient au code, qui la sème au
 * démarrage, exactement comme le catalogue des permissions. Ce qui s'administre, c'est leur
 * valeur — sans quoi il faudrait livrer une version pour changer un numéro de téléphone.</p>
 */
@RestController
@RequestMapping("/api/parametres")
@RequiredArgsConstructor
public class ParametreController {

    private final ParametreService service;

    @GetMapping
    @PreAuthorize("hasAuthority('REGLAGE_LIRE')")
    public List<ParametreVue> lister() {
        return service.lister();
    }

    /**
     * Change la valeur d'un réglage.
     *
     * <p>Désigné par sa clé plutôt que par son identifiant : c'est elle que l'écran montre et que
     * le code cite, et elle est la même d'une installation à l'autre.</p>
     */
    @PutMapping("/{cle}")
    @PreAuthorize("hasAuthority('REGLAGE_MODIFIER')")
    public ParametreVue modifier(@PathVariable String cle, @RequestBody Map<String, String> corps) {
        return service.modifier(cle, corps.get("valeur"));
    }
}
