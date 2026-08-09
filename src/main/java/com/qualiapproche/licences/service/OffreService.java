package com.qualiapproche.licences.service;

import com.qualiapproche.licences.model.OffreAbonnement;
import com.qualiapproche.licences.repository.OffreAbonnementRepository;
import com.qualiapproche.licences.web.ErreurMetier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Le catalogue commercial : les formules qu'on propose. */
@Service
@RequiredArgsConstructor
public class OffreService {

    private final OffreAbonnementRepository repository;

    @Transactional(readOnly = true)
    public List<OffreAbonnement> lister() {
        return repository.findAll().stream()
                .sorted((a, b) -> a.getLibelle().compareToIgnoreCase(b.getLibelle()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OffreAbonnement parId(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ErreurMetier("Offre introuvable.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public OffreAbonnement creer(OffreAbonnement offre) {
        String code = offre.getCode() == null ? "" : offre.getCode().trim().toUpperCase();
        if (code.isEmpty()) {
            throw new ErreurMetier("Le code de l'offre est obligatoire.");
        }
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new ErreurMetier("Le code « " + code + " » est déjà utilisé par une autre offre.",
                    HttpStatus.CONFLICT);
        }
        verifier(offre);
        offre.setCode(code);
        return repository.save(offre);
    }

    /**
     * Modifie une formule du catalogue.
     *
     * <p>Sans effet sur les licences déjà émises : leurs modules et leur durée y ont été recopiés.
     * Une offre est un modèle de saisie, pas une référence vivante — c'est ce qui permet de faire
     * évoluer le catalogue sans rien changer à ce qui a été vendu.</p>
     */
    @Transactional
    public OffreAbonnement modifier(UUID id, OffreAbonnement valeurs) {
        OffreAbonnement offre = parId(id);
        verifier(valeurs);
        offre.setLibelle(valeurs.getLibelle());
        offre.setDescription(valeurs.getDescription());
        offre.setDuree(valeurs.getDuree());
        offre.setUniteDuree(valeurs.getUniteDuree());
        offre.setUtilisateursMax(valeurs.getUtilisateursMax());
        offre.setModules(new LinkedHashSet<>(valeurs.getModules()));
        offre.setMontant(valeurs.getMontant());
        // La devise ne se vide pas : une offre sans monnaie rendrait son montant inadditionnable.
        if (valeurs.getDevise() != null && !valeurs.getDevise().isBlank()) {
            offre.setDevise(valeurs.getDevise().trim().toUpperCase());
        }
        offre.setActif(valeurs.isActif());
        return repository.save(offre);
    }

    private void verifier(OffreAbonnement offre) {
        if (offre.getLibelle() == null || offre.getLibelle().isBlank()) {
            throw new ErreurMetier("Le libellé de l'offre est obligatoire.");
        }
        if (offre.getDuree() < 1) {
            throw new ErreurMetier("La durée doit valoir au moins une unité — un jour, un mois.");
        }
        if (offre.getUniteDuree() == null) {
            throw new ErreurMetier("Précisez si la durée s'entend en jours ou en mois.");
        }
        if (offre.getModules() == null || offre.getModules().isEmpty()) {
            throw new ErreurMetier(
                    "Une offre sans module n'ouvrirait rien : cochez au moins un module.");
        }
        if (offre.getUtilisateursMax() < 0) {
            throw new ErreurMetier("Le nombre d'utilisateurs ne peut pas être négatif. 0 vaut « sans limite ».");
        }
    }
}
