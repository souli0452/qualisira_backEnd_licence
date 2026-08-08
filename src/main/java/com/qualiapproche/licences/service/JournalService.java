package com.qualiapproche.licences.service;

import com.qualiapproche.licences.dto.EntreeDeJournalVue;
import com.qualiapproche.licences.dto.PageVue;
import com.qualiapproche.licences.model.EntreeDeJournal;
import com.qualiapproche.licences.repository.JournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Le journal : y inscrire, et le relire. */
@Service
@RequiredArgsConstructor
@Slf4j
public class JournalService {

    private final JournalRepository journal;

    /**
     * Inscrit une entrée, dans sa <b>propre transaction</b>.
     *
     * <p>{@code REQUIRES_NEW} n'est pas un détail : une action qui échoue emporte sa transaction
     * en arrière, et l'entrée qui la relate disparaîtrait avec elle. On perdrait exactement les
     * lignes qui comptent — les refus, les tentatives.</p>
     *
     * <p>Et l'inverse vaut aussi : un journal en échec ne doit jamais faire échouer l'action qu'il
     * relate. Émettre une licence pour un client bloqué ne peut pas dépendre de la bonne santé
     * d'une table d'audit.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void inscrire(EntreeDeJournal entree) {
        try {
            journal.save(entree);
        } catch (Exception e) {
            log.error("Entrée de journal non inscrite ({} par {}) : {}",
                    entree.getAction(), entree.getAuteur(), e.getMessage());
        }
    }

    /**
     * Une page du journal, filtrée par le serveur.
     *
     * <p>Les plus récentes d'abord : on ouvre ce journal pour savoir ce qui vient de se passer,
     * pas ce qui s'est passé le premier jour.</p>
     */
    @Transactional(readOnly = true)
    public PageVue<EntreeDeJournalVue> lister(String recherche, String auteur, LocalDate depuis,
                                              LocalDate jusqua, Boolean abouti, Pageable page) {
        Specification<EntreeDeJournal> criteres = (racine, requete, cb) -> cb.conjunction();

        if (recherche != null && !recherche.isBlank()) {
            String motif = "%" + recherche.trim().toLowerCase() + "%";
            criteres = criteres.and((racine, requete, cb) -> cb.or(
                    cb.like(cb.lower(racine.get("action")), motif),
                    cb.like(cb.lower(racine.get("auteur")), motif),
                    cb.like(cb.lower(cb.coalesce(racine.get("objet"), "")), motif),
                    cb.like(cb.lower(racine.get("requete")), motif)));
        }
        if (auteur != null && !auteur.isBlank()) {
            criteres = criteres.and((racine, requete, cb) ->
                    cb.equal(cb.lower(racine.get("auteur")), auteur.trim().toLowerCase()));
        }
        if (depuis != null) {
            criteres = criteres.and((racine, requete, cb) ->
                    cb.greaterThanOrEqualTo(racine.get("quand"), depuis.atStartOfDay()));
        }
        if (jusqua != null) {
            // Bornes incluses : « du 1er au 3 » doit contenir le 3 en entier, faute de quoi une
            // recherche sur une seule journée ne rendrait rien.
            criteres = criteres.and((racine, requete, cb) ->
                    cb.lessThan(racine.get("quand"), jusqua.plusDays(1).atStartOfDay()));
        }
        if (abouti != null) {
            criteres = criteres.and((racine, requete, cb) ->
                    cb.equal(racine.get("abouti"), abouti));
        }

        return PageVue.de(journal.findAll(criteres, page), EntreeDeJournalVue::de);
    }

    /** L'horodatage du serveur — un poste dont l'horloge dérive ne doit pas décaler le journal. */
    public LocalDateTime maintenant() {
        return LocalDateTime.now();
    }
}
