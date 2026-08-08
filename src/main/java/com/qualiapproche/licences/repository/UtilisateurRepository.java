package com.qualiapproche.licences.repository;

import com.qualiapproche.licences.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID>, JpaSpecificationExecutor<Utilisateur> {

    Optional<Utilisateur> findByIdentifiantIgnoreCase(String identifiant);

    boolean existsByIdentifiantIgnoreCase(String identifiant);

    List<Utilisateur> findAllByOrderByIdentifiantAsc();

    /**
     * Combien de comptes actifs portent ce rôle.
     *
     * <p>Sert à ne jamais laisser suspendre ou supprimer le dernier super administrateur : la
     * gestion des comptes se refermerait sur elle-même, sans personne pour la rouvrir.</p>
     */
    long countByRoles_CodeIgnoreCaseAndActifTrue(String code);

    long countByRoles_Id(UUID roleId);
}
