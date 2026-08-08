package com.qualiapproche.licences.licence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * Ce que la licence affirme : à qui, pour quoi, jusqu'à quand.
 *
 * <p>C'est la charge utile signée. Les noms de propriétés sont volontairement courts : ils
 * voyagent dans une chaîne que l'administrateur du partenaire va copier-coller, et chaque
 * caractère économisé la rend plus maniable.</p>
 *
 * <p>Les modules et le nombre d'utilisateurs sont <b>recopiés</b> ici depuis l'offre au moment de
 * l'émission, jamais référencés : une offre commerciale qui change plus tard ne doit pas modifier
 * rétroactivement ce qu'un partenaire a acheté.</p>
 *
 * <p>{@link JsonIgnoreProperties} tolère les propriétés inconnues : une licence émise par une
 * version plus récente de l'outil — portant un champ que cette version ne connaît pas encore —
 * reste lisible au lieu de faire échouer la vérification.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContenuDeLicence(

        /** Référence lisible de la licence, celle qu'on cite au support. */
        @JsonProperty("ref") String reference,

        /** Code du partenaire destinataire : une licence ne vaut que pour lui. */
        @JsonProperty("cli") String partenaireCode,

        /** Raison sociale, pour que l'écran d'installation dise à qui la licence est destinée. */
        @JsonProperty("nom") String partenaireNom,

        /** Premier jour de validité. */
        @JsonProperty("deb") LocalDate debut,

        /** Dernier jour de validité, inclus. */
        @JsonProperty("fin") LocalDate fin,

        /** Modules ouverts. Un module absent d'ici n'est ouvert par aucune permission. */
        @JsonProperty("mod") List<String> modules,

        /** Nombre d'utilisateurs actifs autorisés ; {@code 0} vaut « sans limite ». */
        @JsonProperty("usr") int utilisateursMax,

        /** {@code COMMERCIALE} ou {@code ESSAI}. */
        @JsonProperty("typ") String type,

        /** Intitulé de l'offre souscrite, à titre d'information. */
        @JsonProperty("edt") String edition
) {

    /** Une licence d'essai : l'écran d'installation le dit, et le compte à rebours est plus visible. */
    public boolean estUnEssai() {
        return "ESSAI".equalsIgnoreCase(type);
    }

    public boolean sansLimiteDUtilisateurs() {
        return utilisateursMax <= 0;
    }

    /**
     * La licence couvre-t-elle ce jour ?
     *
     * <p>Volontairement séparé de la vérification de signature : une licence expirée est
     * <b>authentique</b>, et l'appliquant doit pouvoir dire « votre abonnement a pris fin le … »
     * plutôt que « licence invalide », qui laisserait croire à une erreur de saisie.</p>
     */
    public boolean couvre(LocalDate jour) {
        return !jour.isBefore(debut) && !jour.isAfter(fin);
    }

    public boolean ouvre(String module) {
        return modules != null && modules.stream().anyMatch(m -> m.equalsIgnoreCase(module));
    }
}
