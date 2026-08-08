package com.qualiapproche.licences.service;

import com.qualiapproche.licences.dto.DemandeDeLicence;
import com.qualiapproche.licences.licence.ContenuDeLicence;
import com.qualiapproche.licences.licence.JetonDeLicence;
import com.qualiapproche.licences.licence.TrousseauDeSignature;
import com.qualiapproche.licences.model.Licence;
import com.qualiapproche.licences.model.ModuleQualiSira;
import com.qualiapproche.licences.model.OffreAbonnement;
import com.qualiapproche.licences.model.Partenaire;
import com.qualiapproche.licences.dto.LicenceVue;
import com.qualiapproche.licences.dto.PageVue;
import com.qualiapproche.licences.model.StatutLicence;
import com.qualiapproche.licences.model.TypeLicence;
import com.qualiapproche.licences.repository.LicenceRepository;
import com.qualiapproche.licences.repository.OffreAbonnementRepository;
import com.qualiapproche.licences.repository.PartenaireRepository;
import com.qualiapproche.licences.web.ErreurMetier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Émission des licences : la seule opération de cet outil qui engage l'éditeur.
 *
 * <p>Le jeton est signé une fois, à l'émission, et n'est plus jamais retouché. Prolonger un
 * abonnement, c'est en émettre un nouveau — jamais réécrire l'ancien : le jeton fait foi chez le
 * client, et une base qui le contredirait ne servirait qu'à nous égarer.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LicenceService {

    private final LicenceRepository licenceRepository;
    private final PartenaireRepository partenaireRepository;
    private final OffreAbonnementRepository offreRepository;
    private final TrousseauDeSignature trousseau;

    @Value("${licences.essai.jours:7}")
    private int joursDEssai;

    @Transactional(readOnly = true)
    public List<Licence> lister() {
        return licenceRepository.findAllByOrderByEmiseLeDesc();
    }

    /**
     * Une page de licences, filtrée par le serveur.
     *
     * <p>La recherche porte sur la référence, le partenaire et l'offre — ce que l'on a sous les
     * yeux quand un client appelle. Faite ici et non côté écran : filtrer le navigateur ne
     * porterait que sur la page affichée, et une licence absente de celle-ci passerait pour
     * inexistante.</p>
     */
    @Transactional(readOnly = true)
    public PageVue<LicenceVue> lister(String recherche, UUID partenaireId, Pageable page) {
        Specification<Licence> criteres = correspondA(recherche);
        if (partenaireId != null) {
            criteres = criteres.and((racine, requete, cb) ->
                    cb.equal(racine.get("partenaire").get("id"), partenaireId));
        }
        return PageVue.de(licenceRepository.findAll(criteres, page), LicenceVue::de);
    }

    private Specification<Licence> correspondA(String recherche) {
        if (recherche == null || recherche.isBlank()) {
            return (racine, requete, cb) -> cb.conjunction();
        }
        String motif = "%" + recherche.trim().toLowerCase() + "%";
        return (racine, requete, cb) -> cb.or(
                cb.like(cb.lower(racine.get("reference")), motif),
                cb.like(cb.lower(racine.get("partenaire").get("code")), motif),
                cb.like(cb.lower(racine.get("partenaire").get("raisonSociale")), motif));
    }

    @Transactional(readOnly = true)
    public List<Licence> listerPour(UUID partenaireId) {
        return licenceRepository.findByPartenaireIdOrderByEmiseLeDesc(partenaireId);
    }

    @Transactional(readOnly = true)
    public Licence parId(UUID id) {
        return licenceRepository.findById(id)
                .orElseThrow(() -> new ErreurMetier("Licence introuvable.", HttpStatus.NOT_FOUND));
    }

    /**
     * Émet une licence commerciale.
     *
     * <p>L'offre fournit la durée, le plafond d'utilisateurs et les modules ; la demande peut
     * surcharger chacun — la remise négociée, les modules ajoutés en cours de contrat. Ce qui est
     * retenu est recopié dans la licence, pas référencé : l'offre pourra changer sans réécrire ce
     * qui a été vendu.</p>
     */
    @Transactional
    public Licence emettre(DemandeDeLicence demande, String auteur) {
        Partenaire partenaire = partenaire(demande.partenaireId());
        if (!partenaire.isActif()) {
            throw new ErreurMetier(
                    "Le partenaire « " + partenaire.getRaisonSociale() + " » est désactivé : "
                            + "réactivez-le avant d'émettre une licence.");
        }

        OffreAbonnement offre = null;
        if (demande.offreId() != null) {
            offre = offreRepository.findById(demande.offreId())
                    .orElseThrow(() -> new ErreurMetier("Offre introuvable.", HttpStatus.NOT_FOUND));
            if (!offre.isActif()) {
                throw new ErreurMetier("L'offre « " + offre.getLibelle() + " » n'est plus proposée.");
            }
        }

        Set<ModuleQualiSira> modules = premierNonVide(demande.modules(),
                offre != null ? offre.getModules() : null);
        if (modules.isEmpty()) {
            throw new ErreurMetier(
                    "Aucun module n'est souscrit : la licence n'ouvrirait rien. Choisissez une "
                            + "offre ou cochez au moins un module.");
        }

        LocalDate debut = demande.debut() != null ? demande.debut() : LocalDate.now();
        LocalDate fin = terme(debut, demande, offre);
        if (!fin.isAfter(debut)) {
            throw new ErreurMetier("La date de fin doit suivre la date de début.");
        }

        int utilisateurs = demande.utilisateursMax() != null
                ? demande.utilisateursMax()
                : (offre != null ? offre.getUtilisateursMax() : 0);
        if (utilisateurs < 0) {
            throw new ErreurMetier("Le nombre d'utilisateurs ne peut pas être négatif. 0 vaut « sans limite ».");
        }

        return enregistrer(partenaire, offre, TypeLicence.COMMERCIALE, debut, fin, utilisateurs,
                modules, auteur);
    }

    /**
     * Émet un essai gratuit : durée courte, tous les modules.
     *
     * <p>Un seul par partenaire. Sans cette limite, il suffirait d'en redemander un à chaque
     * échéance — et l'essai remplacerait l'abonnement.</p>
     */
    @Transactional
    public Licence emettreEssai(UUID partenaireId, Integer jours, String auteur) {
        Partenaire partenaire = partenaire(partenaireId);
        if (licenceRepository.countByPartenaireIdAndType(partenaireId, TypeLicence.ESSAI) > 0) {
            throw new ErreurMetier(
                    "Un essai a déjà été accordé à « " + partenaire.getRaisonSociale() + " ». "
                            + "Émettez une licence commerciale, ou une licence sur mesure si vous "
                            + "voulez prolonger l'évaluation.",
                    HttpStatus.CONFLICT);
        }

        LocalDate debut = LocalDate.now();
        int duree = jours != null && jours > 0 ? jours : joursDEssai;
        Set<ModuleQualiSira> tous = new LinkedHashSet<>(Arrays.asList(ModuleQualiSira.values()));

        return enregistrer(partenaire, null, TypeLicence.ESSAI, debut, debut.plusDays(duree),
                0, tous, auteur);
    }

    @Transactional
    public Licence revoquer(UUID id, String motif) {
        Licence licence = parId(id);
        if (licence.getStatut() == StatutLicence.REVOQUEE) {
            return licence;
        }
        licence.setStatut(StatutLicence.REVOQUEE);
        licence.setMotifRevocation(motif == null || motif.isBlank() ? "Non précisé" : motif.trim());
        licence.setRevoqueeLe(LocalDateTime.now());
        log.info("Licence {} révoquée : {}", licence.getReference(), licence.getMotifRevocation());
        // Le produit tourne hors ligne : cette révocation ne désarme aucune installation. Elle
        // clôt le dossier de notre côté. Voir StatutLicence.REVOQUEE.
        return licenceRepository.save(licence);
    }

    /**
     * Relit une licence comme le fera le produit installé.
     *
     * <p>Sert à lever un doute au support — « la licence que je vous ai envoyée est-elle la
     * bonne ? » — sans avoir à la déployer pour le savoir.</p>
     */
    public ContenuDeLicence verifier(String jeton) {
        return JetonDeLicence.lire(jeton, trousseau.clePubliqueBase64());
    }

    private Licence enregistrer(Partenaire partenaire, OffreAbonnement offre, TypeLicence type,
                                LocalDate debut, LocalDate fin, int utilisateurs,
                                Set<ModuleQualiSira> modules, String auteur) {
        String reference = referenceSuivante();

        ContenuDeLicence contenu = new ContenuDeLicence(
                reference,
                partenaire.getCode(),
                partenaire.getRaisonSociale(),
                debut,
                fin,
                modules.stream().map(Enum::name).toList(),
                utilisateurs,
                type.name(),
                offre != null ? offre.getLibelle() : (type == TypeLicence.ESSAI ? "Essai" : "Sur mesure"));

        Licence licence = Licence.builder()
                .reference(reference)
                .partenaire(partenaire)
                .offre(offre)
                .type(type)
                .statut(StatutLicence.ACTIVE)
                .debut(debut)
                .fin(fin)
                .utilisateursMax(utilisateurs)
                // Recopié, jamais référencé : un tarif révisé au catalogue ne doit pas réécrire
                // le chiffre d'affaires d'un exercice clos. Un essai n'est pas facturé.
                .montant(type == TypeLicence.ESSAI || offre == null ? null : offre.getMontant())
                .devise(type == TypeLicence.ESSAI || offre == null ? null : offre.getDevise())
                .modules(new LinkedHashSet<>(modules))
                .jeton(JetonDeLicence.signer(contenu, trousseau.clePrivee()))
                .emiseLe(LocalDateTime.now())
                .emisePar(auteur)
                .build();

        Licence enregistree = licenceRepository.save(licence);
        log.info("Licence {} émise pour {} ({} → {}, {} module(s))", reference,
                partenaire.getCode(), debut, fin, modules.size());
        return enregistree;
    }

    /** Terme retenu : durée en jours si elle est donnée, sinon en mois, sinon celle de l'offre. */
    private LocalDate terme(LocalDate debut, DemandeDeLicence demande, OffreAbonnement offre) {
        if (demande.dureeJours() != null && demande.dureeJours() > 0) {
            return debut.plusDays(demande.dureeJours());
        }
        if (demande.dureeMois() != null && demande.dureeMois() > 0) {
            return debut.plusMonths(demande.dureeMois());
        }
        if (offre != null) {
            return debut.plusMonths(offre.getDureeMois());
        }
        throw new ErreurMetier("Aucune durée : choisissez une offre, ou indiquez une durée.");
    }

    private Set<ModuleQualiSira> premierNonVide(Set<ModuleQualiSira> choisis, Set<ModuleQualiSira> repli) {
        if (choisis != null && !choisis.isEmpty()) {
            return new LinkedHashSet<>(choisis);
        }
        return repli != null ? new LinkedHashSet<>(repli) : new LinkedHashSet<>();
    }

    private Partenaire partenaire(UUID id) {
        if (id == null) {
            throw new ErreurMetier("Aucun partenaire n'a été choisi.");
        }
        return partenaireRepository.findById(id)
                .orElseThrow(() -> new ErreurMetier("Partenaire introuvable.", HttpStatus.NOT_FOUND));
    }

    /**
     * « LIC-2026-0007 » : l'année puis un rang. Lisible au téléphone, ce que ne serait pas un
     * identifiant technique — et c'est cette référence qu'un client cite au support.
     */
    private String referenceSuivante() {
        String annee = String.valueOf(LocalDate.now().getYear());
        long rang = licenceRepository.compterPourLAnnee(annee) + 1;
        // Le compte suffit tant que rien n'est supprimé ; la boucle couvre le cas contraire, où
        // il redonnerait un rang déjà pris.
        String reference = "LIC-%s-%04d".formatted(annee, rang);
        while (licenceRepository.existsByReference(reference)) {
            reference = "LIC-%s-%04d".formatted(annee, ++rang);
        }
        return reference;
    }
}
