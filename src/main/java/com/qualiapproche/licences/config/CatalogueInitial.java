package com.qualiapproche.licences.config;

import com.qualiapproche.licences.model.ModuleQualiSira;
import com.qualiapproche.licences.model.OffreAbonnement;
import com.qualiapproche.licences.repository.OffreAbonnementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Trois formules au premier démarrage, pour que l'outil serve tout de suite.
 *
 * <p>Rien n'est réécrit ensuite : ce sont des points de départ à retoucher depuis l'écran, pas
 * un catalogue imposé. Un code déjà présent est laissé tel quel, y compris s'il a été modifié.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogueInitial implements CommandLineRunner {

    private final OffreAbonnementRepository repository;

    @Override
    public void run(String... args) {
        creerSiAbsente(OffreAbonnement.builder()
                .code("ESSENTIEL")
                .libelle("Essentiel")
                .description("Non-conformités et gestion documentaire, pour démarrer la démarche qualité.")
                .dureeMois(12)
                .utilisateursMax(25)
                .modules(modules(ModuleQualiSira.NON_CONFORMITE, ModuleQualiSira.DOCUMENTAIRE))
                .actif(true)
                .build());

        creerSiAbsente(OffreAbonnement.builder()
                .code("AVANCE")
                .libelle("Avancé")
                .description("Ajoute les réclamations, les risques et les audits au socle Essentiel.")
                .dureeMois(12)
                .utilisateursMax(100)
                .modules(modules(ModuleQualiSira.NON_CONFORMITE, ModuleQualiSira.DOCUMENTAIRE,
                        ModuleQualiSira.RECLAMATION, ModuleQualiSira.RISQUE, ModuleQualiSira.AUDIT))
                .actif(true)
                .build());

        creerSiAbsente(OffreAbonnement.builder()
                .code("INTEGRAL")
                .libelle("Intégral")
                .description("Tous les modules, sans limite d'utilisateurs.")
                .dureeMois(12)
                .utilisateursMax(0)
                .modules(modules(ModuleQualiSira.values()))
                .actif(true)
                .build());
    }

    private void creerSiAbsente(OffreAbonnement offre) {
        if (repository.existsByCodeIgnoreCase(offre.getCode())) {
            return;
        }
        repository.save(offre);
        log.info("Offre « {} » ajoutée au catalogue.", offre.getLibelle());
    }

    private Set<ModuleQualiSira> modules(ModuleQualiSira... modules) {
        return new LinkedHashSet<>(Arrays.asList(modules));
    }
}
