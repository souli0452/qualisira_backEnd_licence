package com.qualiapproche.licences.repository;

import com.qualiapproche.licences.model.Licence;
import com.qualiapproche.licences.model.TypeLicence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LicenceRepository extends JpaRepository<Licence, UUID>, JpaSpecificationExecutor<Licence> {

    List<Licence> findByPartenaireIdOrderByEmiseLeDesc(UUID partenaireId);

    List<Licence> findAllByOrderByEmiseLeDesc();

    /**
     * Les licences des partenaires d'une page, pour en calculer le compte et l'échéance en cours.
     *
     * <p>Une requête pour la page entière, et non une par ligne : dix partenaires affichés
     * feraient sinon dix allers-retours, pour une information qui tient en une jointure.</p>
     */
    List<Licence> findByPartenaireIdIn(Collection<UUID> partenaireIds);

    long countByPartenaireIdAndType(UUID partenaireId, TypeLicence type);

    boolean existsByReference(String reference);

    // ------------------------------------------------------------------ tableau de bord
    //
    // Des requêtes d'agrégation plutôt qu'un parcours en mémoire : compter et sommer sur le parc
    // entier chargerait toutes les licences à chaque ouverture de l'écran d'accueil, ce que la
    // pagination cherche précisément à éviter ailleurs.
    //
    // L'état réel se déduit des dates, comme le fait « statutReel() » : seule la révocation est un
    // état stocké, les autres se périmeraient dès le lendemain.

    @Query(value = """
            SELECT
              COUNT(*) FILTER (WHERE statut <> 'REVOQUEE' AND debut <= :jour AND fin >= :jour),
              COUNT(*) FILTER (WHERE statut <> 'REVOQUEE' AND debut > :jour),
              COUNT(*) FILTER (WHERE statut <> 'REVOQUEE' AND fin < :jour),
              COUNT(*) FILTER (WHERE statut = 'REVOQUEE'),
              COUNT(*) FILTER (WHERE statut <> 'REVOQUEE' AND fin >= :jour AND fin <= :dans30Jours),
              COUNT(*) FILTER (WHERE envoyee_le IS NULL),
              COUNT(*)
            FROM licences
            """, nativeQuery = true)
    // Deux dates plutôt qu'une arithmétique dans le SQL : « :jour + 30 » laisse PostgreSQL
    // deviner le type du paramètre, et il le prend pour un entier — l'erreur ne surgit qu'à
    // l'exécution, sur un « operator does not exist: date < integer » qui ne dit pas d'où il vient.
    Object[] compterParEtat(@Param("jour") LocalDate jour,
                            @Param("dans30Jours") LocalDate dans30Jours);

    /** Les revenus d'une période, par devise. Une licence révoquée n'est pas un revenu. */
    @Query(value = """
            SELECT devise, COALESCE(SUM(montant), 0), COUNT(*)
            FROM licences
            WHERE montant IS NOT NULL AND statut <> 'REVOQUEE'
              AND emise_le >= :debut AND emise_le < :fin
            GROUP BY devise
            ORDER BY 2 DESC
            """, nativeQuery = true)
    List<Object[]> revenusEntre(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin);

    @Query(value = """
            SELECT to_char(emise_le, 'YYYY-MM'), devise, COALESCE(SUM(montant), 0), COUNT(*)
            FROM licences
            WHERE montant IS NOT NULL AND statut <> 'REVOQUEE' AND emise_le >= :depuis
            GROUP BY 1, 2
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> revenusParMoisDepuis(@Param("depuis") LocalDateTime depuis);

    /**
     * Les offres qui rapportent le plus.
     *
     * <p>Classées sur le montant encaissé et non sur le nombre de licences : une offre vendue dix
     * fois à bas prix pèse moins qu'une vendue deux fois cher, et c'est la seconde qu'on veut voir
     * en tête.</p>
     */
    @Query(value = """
            SELECT o.code, o.libelle, COUNT(l.id), COALESCE(SUM(l.montant), 0),
                   COALESCE(MAX(l.devise), o.devise)
            FROM licences l
            JOIN offres_abonnement o ON o.id = l.offre_id
            WHERE l.statut <> 'REVOQUEE'
            GROUP BY o.code, o.libelle, o.devise
            ORDER BY 4 DESC, 3 DESC
            LIMIT 8
            """, nativeQuery = true)
    List<Object[]> offresLesPlusVendues();

    /** Les licences facturables sans montant — le trou dans le chiffre d'affaires. */
    @Query(value = "SELECT COUNT(*) FROM licences WHERE montant IS NULL AND type <> 'ESSAI'",
            nativeQuery = true)
    long compterSansMontant();

    /** Combien de licences déjà émises cette année, pour composer la référence suivante. */
    @Query("SELECT COUNT(l) FROM Licence l WHERE l.reference LIKE CONCAT('LIC-', :annee, '-%')")
    long compterPourLAnnee(String annee);
}
