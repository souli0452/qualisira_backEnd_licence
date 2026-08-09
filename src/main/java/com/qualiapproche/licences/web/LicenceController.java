package com.qualiapproche.licences.web;

import com.qualiapproche.licences.dto.DemandeDeLicence;
import com.qualiapproche.licences.dto.LicenceVue;
import com.qualiapproche.licences.dto.PageVue;
import com.qualiapproche.licences.licence.ContenuDeLicence;
import com.qualiapproche.licences.licence.TrousseauDeSignature;
import com.qualiapproche.licences.model.Licence;
import com.qualiapproche.licences.service.EnvoiDeLicenceService;
import com.qualiapproche.licences.service.LicenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/licences")
@RequiredArgsConstructor
public class LicenceController {

    private final LicenceService service;
    private final EnvoiDeLicenceService envoiService;
    private final TrousseauDeSignature trousseau;

    @GetMapping
    @PreAuthorize("hasAuthority('LICENCE_LIRE')")
    public PageVue<LicenceVue> lister(
            @RequestParam(required = false) UUID partenaireId,
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int taille) {
        // Les plus récentes d'abord : c'est celle qu'on vient d'émettre qu'on cherche du regard.
        return service.lister(recherche, partenaireId,
                PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "emiseLe")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('LICENCE_LIRE')")
    public LicenceVue parId(@PathVariable UUID id) {
        return LicenceVue.de(service.parId(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LICENCE_EMETTRE')")
    public LicenceVue emettre(@RequestBody DemandeDeLicence demande, Principal auteur) {
        return LicenceVue.de(service.emettre(demande, auteur != null ? auteur.getName() : "inconnu"));
    }

    @PostMapping("/{id}/revoquer")
    @PreAuthorize("hasAuthority('LICENCE_REVOQUER')")
    public LicenceVue revoquer(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> corps) {
        return LicenceVue.de(service.revoquer(id, corps != null ? corps.get("motif") : null));
    }

    /**
     * Envoie la licence au partenaire, en pièce jointe.
     *
     * <p>L'adresse du contact sert par défaut ; une autre peut être indiquée — l'informaticien du
     * partenaire n'est pas toujours l'interlocuteur commercial.</p>
     */
    @PostMapping("/{id}/envoyer")
    @PreAuthorize("hasAuthority('LICENCE_ENVOYER')")
    public LicenceVue envoyer(@PathVariable UUID id,
                              @RequestBody(required = false) Map<String, String> corps) {
        String destinataire = corps != null ? corps.get("destinataire") : null;
        return LicenceVue.de(envoiService.envoyer(id, destinataire));
    }

    /**
     * Le fichier que l'on transmet au partenaire.
     *
     * <p>Un fichier plutôt qu'un simple copier-coller : une licence expédiée par courriel se
     * fait couper par les clients de messagerie, qui replient les longues lignes. La pièce jointe
     * arrive intacte.</p>
     */
    @GetMapping("/{id}/fichier")
    @PreAuthorize("hasAuthority('LICENCE_LIRE')")
    public ResponseEntity<ByteArrayResource> fichier(@PathVariable UUID id) {
        Licence licence = service.parId(id);
        byte[] contenu = licence.getJeton().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + licence.getReference() + ".lic\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(new ByteArrayResource(contenu));
    }

    /** Relit une licence comme le fera le produit installé — pour lever un doute au support. */
    @PostMapping("/verifier")
    @PreAuthorize("hasAuthority('LICENCE_VERIFIER')")
    public ContenuDeLicence verifier(@RequestBody Map<String, String> corps) {
        return service.verifier(corps.get("jeton"));
    }

    /**
     * La clé publique à embarquer dans QualiSira.
     *
     * <p>Elle se diffuse sans risque : elle permet de vérifier une licence, jamais d'en signer
     * une. C'est l'inverse exact de la clé privée, qui ne quitte pas cet outil.</p>
     */
    @GetMapping("/cle-publique")
    @PreAuthorize("hasAuthority('LICENCE_VERIFIER')")
    public Map<String, String> clePublique() {
        return Map.of(
                "algorithme", "Ed25519",
                "clePublique", trousseau.clePubliqueBase64(),
                "proprieteSpring", "qualisira.licence.cle-publique");
    }
}
