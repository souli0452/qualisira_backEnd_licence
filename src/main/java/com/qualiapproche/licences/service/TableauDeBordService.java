package com.qualiapproche.licences.service;

import com.qualiapproche.licences.dto.TableauDeBordVue;
import com.qualiapproche.licences.repository.LicenceRepository;
import com.qualiapproche.licences.repository.PartenaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Les indicateurs de l'écran d'accueil : l'état du parc, et ce qu'il rapporte.
 *
 * <p>Tout est calculé par des <b>agrégations en base</b>. Charger les licences pour les compter en
 * mémoire aurait fonctionné sur trois lignes et se serait effondré sur trois mille — et c'est
 * précisément l'écran qu'on ouvre le plus souvent.</p>
 */
@Service
@RequiredArgsConstructor
public class TableauDeBordService {

    private final LicenceRepository licences;
    private final PartenaireRepository partenaires;

    @Transactional(readOnly = true)
    public TableauDeBordVue composer() {
        LocalDate aujourdhui = LocalDate.now();
        LocalDateTime debutDuMois = aujourdhui.withDayOfMonth(1).atStartOfDay();
        LocalDateTime debutDeLAnnee = aujourdhui.withDayOfYear(1).atStartOfDay();
        LocalDateTime demain = aujourdhui.plusDays(1).atStartOfDay();
        LocalDateTime ilYADouzeMois = aujourdhui.withDayOfMonth(1).minusMonths(11).atStartOfDay();

        return new TableauDeBordVue(
                partenaires(),
                licences(aujourdhui),
                revenus(licences.revenusEntre(debutDuMois, demain)),
                revenus(licences.revenusEntre(debutDeLAnnee, demain)),
                parMois(licences.revenusParMoisDepuis(ilYADouzeMois)),
                offres(),
                licences.compterSansMontant());
    }

    private TableauDeBordVue.Partenaires partenaires() {
        long total = partenaires.count();
        long actifs = partenaires.findAll().stream().filter(p -> p.isActif()).count();
        // Sans licence en cours : ceux qu'il faut relancer. Déduit du parc plutôt que compté à
        // part, le volume des partenaires restant sans commune mesure avec celui des licences.
        long avecLicence = licences.findAll().stream()
                .filter(l -> l.statutReel() == com.qualiapproche.licences.model.StatutLicence.ACTIVE)
                .map(l -> l.getPartenaire().getId())
                .distinct()
                .count();
        return new TableauDeBordVue.Partenaires(total, actifs, Math.max(0, total - avecLicence));
    }

    private TableauDeBordVue.Licences licences(LocalDate jour) {
        Object[] ligne = premiereLigne(licences.compterParEtat(jour, jour.plusDays(30)));
        return new TableauDeBordVue.Licences(
                nombre(ligne[6]), nombre(ligne[0]), nombre(ligne[1]), nombre(ligne[2]),
                nombre(ligne[3]), nombre(ligne[4]), nombre(ligne[5]));
    }

    private List<TableauDeBordVue.Revenu> revenus(List<Object[]> lignes) {
        return lignes.stream()
                .map(l -> new TableauDeBordVue.Revenu(
                        (String) l[0], montant(l[1]), nombre(l[2])))
                .toList();
    }

    private List<TableauDeBordVue.MoisDeRevenu> parMois(List<Object[]> lignes) {
        return lignes.stream()
                .map(l -> new TableauDeBordVue.MoisDeRevenu(
                        (String) l[0], (String) l[1], montant(l[2]), nombre(l[3])))
                .toList();
    }

    private List<TableauDeBordVue.OffreVendue> offres() {
        return licences.offresLesPlusVendues().stream()
                .map(l -> new TableauDeBordVue.OffreVendue(
                        (String) l[0], (String) l[1], nombre(l[2]), montant(l[3]), (String) l[4]))
                .toList();
    }

    /**
     * Spring rend parfois la ligne d'une requête à colonnes multiples enveloppée dans un tableau.
     *
     * <p>Le déballer ici plutôt que de s'y fier : selon la version, {@code Object[]} contient les
     * colonnes ou un unique {@code Object[]} qui les contient — et l'écart ne se voit qu'à
     * l'exécution, sur un {@code ClassCastException} loin de sa cause.</p>
     */
    private Object[] premiereLigne(Object[] brut) {
        if (brut.length == 1 && brut[0] instanceof Object[] interne) {
            return interne;
        }
        return brut;
    }

    private long nombre(Object valeur) {
        return valeur instanceof Number n ? n.longValue() : 0L;
    }

    private BigDecimal montant(Object valeur) {
        if (valeur instanceof BigDecimal montant) {
            return montant;
        }
        return valeur instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }
}
