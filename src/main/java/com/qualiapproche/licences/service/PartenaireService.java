package com.qualiapproche.licences.service;

import com.qualiapproche.licences.dto.PageVue;
import com.qualiapproche.licences.dto.PartenaireVue;
import com.qualiapproche.licences.model.Licence;
import com.qualiapproche.licences.model.Partenaire;
import com.qualiapproche.licences.model.StatutLicence;
import com.qualiapproche.licences.repository.LicenceRepository;
import com.qualiapproche.licences.repository.PartenaireRepository;
import com.qualiapproche.licences.web.ErreurMetier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Le fichier des clients : qui ils sont, comment les joindre. */
@Service
@RequiredArgsConstructor
public class PartenaireService {

    private final PartenaireRepository repository;
    private final LicenceRepository licences;

    /**
     * Une page de partenaires, avec l'état de leurs licences.
     *
     * <p>La recherche est faite <b>ici</b> et non par l'écran : filtrer côté navigateur ne
     * porterait que sur la page affichée, et un partenaire absent de celle-ci passerait pour
     * inexistant.</p>
     */
    @Transactional(readOnly = true)
    public PageVue<PartenaireVue> lister(String recherche, Pageable page) {
        Page<Partenaire> trouves = repository.findAll(correspondA(recherche), page);
        return PageVue.de(trouves.map(this::avecSesLicences));
    }

    /**
     * Tous les partenaires, en bref — pour les listes déroulantes.
     *
     * <p>Une liste de choix ne se pagine pas : celui qu'on cherche serait au-delà de la première
     * page, et rien ne le dirait. Elle reste donc entière, mais réduite à ce qu'un choix
     * demande.</p>
     */
    @Transactional(readOnly = true)
    public List<Partenaire> selection() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Partenaire::getRaisonSociale, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Le nombre de licences et l'échéance en cours, pour les partenaires de la page.
     *
     * <p>Calculés page par page : le total et l'état de <b>tous</b> les partenaires exigeraient de
     * lire toutes les licences à chaque affichage, ce que la pagination cherche précisément à
     * éviter.</p>
     */
    private PartenaireVue avecSesLicences(Partenaire partenaire) {
        return construire(partenaire, licences.findByPartenaireIdIn(List.of(partenaire.getId())));
    }

    private PartenaireVue construire(Partenaire partenaire, List<Licence> siennes) {
        LocalDate finEnCours = siennes.stream()
                .filter(l -> l.statutReel() == StatutLicence.ACTIVE)
                .map(Licence::getFin)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return PartenaireVue.de(partenaire, siennes.size(), finEnCours);
    }

    /**
     * Ce sur quoi porte la recherche : le code, la raison sociale, la ville, le contact.
     *
     * <p>Insensible à la casse, et sur des fragments : on cherche « abj » sans savoir si le code
     * s'écrit « CHU-ABJ » ou « ABJ-CHU ».</p>
     */
    private Specification<Partenaire> correspondA(String recherche) {
        if (recherche == null || recherche.isBlank()) {
            return (racine, requete, cb) -> cb.conjunction();
        }
        String motif = "%" + recherche.trim().toLowerCase() + "%";
        return (racine, requete, cb) -> cb.or(
                cb.like(cb.lower(racine.get("code")), motif),
                cb.like(cb.lower(racine.get("raisonSociale")), motif),
                cb.like(cb.lower(cb.coalesce(racine.get("ville"), "")), motif),
                cb.like(cb.lower(cb.coalesce(racine.get("contactNom"), "")), motif));
    }

    @Transactional(readOnly = true)
    public Partenaire parId(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ErreurMetier("Partenaire introuvable.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public Partenaire creer(Partenaire partenaire, String auteur) {
        String code = normaliser(partenaire.getCode());
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new ErreurMetier("Le code « " + code + " » est déjà attribué à un autre partenaire.",
                    HttpStatus.CONFLICT);
        }
        partenaire.setCode(code);
        partenaire.prendreDate(auteur);
        return repository.save(partenaire);
    }

    /**
     * Met à jour le dossier commercial.
     *
     * <p>Le code n'est jamais réécrit : il est inscrit dans les licences déjà émises, et le
     * changer les rendrait toutes invalides chez le partenaire — sans que rien ici ne le signale.</p>
     */
    @Transactional
    public Partenaire modifier(UUID id, Partenaire valeurs) {
        Partenaire partenaire = parId(id);
        partenaire.setRaisonSociale(valeurs.getRaisonSociale());
        partenaire.setSigle(valeurs.getSigle());
        partenaire.setSecteurActivite(valeurs.getSecteurActivite());
        partenaire.setContactNom(valeurs.getContactNom());
        partenaire.setContactEmail(valeurs.getContactEmail());
        partenaire.setContactTelephone(valeurs.getContactTelephone());
        partenaire.setAdresse(valeurs.getAdresse());
        partenaire.setVille(valeurs.getVille());
        partenaire.setPays(valeurs.getPays());
        partenaire.setNotes(valeurs.getNotes());
        partenaire.setActif(valeurs.isActif());
        return repository.save(partenaire);
    }

    /**
     * « ACME-001 » : majuscules, sans espace ni accent.
     *
     * <p>Le code voyage dans la licence et y est comparé caractère par caractère. Une casse ou un
     * espace de plus, et la licence serait refusée chez le partenaire sans qu'on comprenne
     * pourquoi.</p>
     */
    private String normaliser(String code) {
        if (code == null || code.isBlank()) {
            throw new ErreurMetier("Le code du partenaire est obligatoire.");
        }
        String normalise = Normalizer.normalize(code.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (normalise.isEmpty()) {
            throw new ErreurMetier("Le code du partenaire ne contient aucun caractère utilisable.");
        }
        return normalise;
    }
}
