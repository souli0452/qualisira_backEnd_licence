package com.qualiapproche.licences.repository;

import com.qualiapproche.licences.model.Partenaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PartenaireRepository extends JpaRepository<Partenaire, UUID>, JpaSpecificationExecutor<Partenaire> {

    Optional<Partenaire> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
