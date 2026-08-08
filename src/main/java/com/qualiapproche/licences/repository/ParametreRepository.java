package com.qualiapproche.licences.repository;

import com.qualiapproche.licences.model.Parametre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParametreRepository extends JpaRepository<Parametre, UUID> {

    Optional<Parametre> findByCleIgnoreCase(String cle);

    List<Parametre> findAllByOrderByLibelleAsc();
}
