package com.qualiapproche.licences.licence;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Annonce sur {@code /actuator/info} la clé publique avec laquelle cette instance signe.
 *
 * <p>Elle ne se lisait qu'au détour d'un appel d'API, et dans le journal du tout premier démarrage
 * — c'est-à-dire nulle part, une fois l'installation en service. Résultat : rien ne permettait de
 * constater qu'une instance s'était forgé une nouvelle clé, et l'on ne s'en apercevait qu'en
 * collant une licence dans le produit, qui répondait « signature invalide » sans dire pourquoi.</p>
 *
 * <p>Ici, une adresse connue et un coup d'œil suffisent : on compare cette valeur à
 * {@code qualisira.licence.cle-publique} du produit. Si elles diffèrent, aucune licence émise ici
 * ne sera reconnue là-bas.</p>
 *
 * <h2>Pourquoi il faut tout de même être connecté</h2>
 *
 * <p>Aucun secret n'est divulgué : une clé publique vérifie une signature, elle n'en produit
 * aucune, et elle voyage déjà dans le fichier de configuration de chaque installation du produit.
 * Le relevé demande néanmoins une session — voir {@code SecuriteConfig} et
 * {@code SecuriteKeycloakConfig}, qui n'ouvrent que {@code /actuator/health}.</p>
 *
 * <p>Non par crainte pour la clé, mais parce que ce point d'entrée dit <b>quelle instance signe
 * avec quoi</b>. Ouvert, il permettait à qui connaissait l'adresse du back-office de dénombrer les
 * installations, de suivre le remplacement d'une clé et d'apparier une licence reçue à l'instance
 * qui l'a émise — un renseignement sur l'exploitation, que rien n'oblige à rendre sans compte. Qui
 * doit relever la clé a de toute façon un accès à l'outil.</p>
 */
@Component
@RequiredArgsConstructor
public class ClePubliqueAnnoncee implements InfoContributor {

    private final TrousseauDeSignature trousseau;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("licence", Map.of(
                "algorithme", "Ed25519",
                "clePublique", trousseau.clePubliqueBase64(),
                "proprieteSpring", "qualisira.licence.cle-publique",
                "aQuoiCaSert", "À comparer avec la clé publique du produit : si elles diffèrent, "
                        + "les licences émises ici y seront refusées."));
    }
}
