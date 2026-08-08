package com.qualiapproche.licences.repository;

import com.qualiapproche.licences.model.OffreAbonnement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OffreAbonnementRepository extends JpaRepository<OffreAbonnement, UUID> {

    Optional<OffreAbonnement> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<OffreAbonnement> findByActifTrueOrderByLibelleAsc();
}
