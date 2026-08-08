package com.qualiapproche.licences.repository;

import com.qualiapproche.licences.model.EntreeDeJournal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface JournalRepository extends JpaRepository<EntreeDeJournal, UUID>,
        JpaSpecificationExecutor<EntreeDeJournal> {
}
