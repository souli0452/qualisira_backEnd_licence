package com.qualiapproche.licences.repository;

import com.qualiapproche.licences.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCodeIgnoreCase(String code);

    /** Les codes sont ceux de {@code PermissionQualiSira}, toujours en majuscules. */
    List<Permission> findByCodeIn(Collection<String> codes);

    List<Permission> findAllByOrderByDomaineAscCodeAsc();
}
