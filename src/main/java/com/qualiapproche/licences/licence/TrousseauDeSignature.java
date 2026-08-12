package com.qualiapproche.licences.licence;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Properties;

/**
 * La paire de clés de l'éditeur : celle qui signe toutes les licences.
 *
 * <p>C'est <b>le</b> secret de ce dispositif : qui la détient peut émettre des licences pour
 * n'importe quel partenaire, n'importe quels modules, sans limite de durée. Elle se sauvegarde
 * comme on sauvegarde un certificat de signature — sa perte oblige à réémettre toutes les licences
 * en circulation, puisque la clé publique embarquée dans le produit ne vérifierait plus rien.</p>
 *
 * <p>La clé publique, elle, est faite pour être diffusée : elle est embarquée dans l'application
 * livrée et permet de vérifier une licence sans permettre d'en fabriquer une. Elle s'annonce sur
 * {@code /actuator/info}, pour qu'on puisse la comparer d'un coup d'œil à celle du produit.</p>
 *
 * <h2>D'où vient la clé</h2>
 * <ol>
 *   <li>le fichier {@code licences.cles.fichier}, qui doit vivre sur un volume persistant ;</li>
 *   <li>à défaut, une paire neuve — <b>et seulement si on l'a demandé</b>.</li>
 * </ol>
 *
 * <p>La clé privée ne se lit pas dans la configuration, délibérément : une variable
 * d'environnement se recopie, s'affiche dans une console d'administration et se retrouve dans les
 * journaux de déploiement. Ce secret-là mérite un fichier, sur un volume qu'on sauvegarde.</p>
 *
 * <h2>Pourquoi le service refuse de démarrer plutôt que d'engendrer</h2>
 *
 * <p>La génération était automatique dès que le fichier manquait. Cela se passe bien une fois, sur
 * un poste de travail. En production, c'est l'inverse : un conteneur qui repart sans son volume
 * trouve le fichier absent, se forge une clé neuve, démarre normalement — et <b>toutes les licences
 * déjà remises deviennent invérifiables</b>, d'un coup, chez tous les clients. Rien ne le signale
 * ici : le service fonctionne parfaitement. C'est chez le client que ça casse, des semaines plus
 * tard, et sans que personne ne fasse le lien.</p>
 *
 * <p>Le service ne peut pas distinguer « première installation, c'est voulu » de « mon volume a
 * disparu ». Il ne devine donc plus : sans {@code licences.cles.engendrer-si-absent}, il s'arrête
 * en disant ce qui manque. Un démarrage qui échoue se remarque dans la minute ; une clé
 * silencieusement remplacée, jamais.</p>
 */
@Component
@Slf4j
public class TrousseauDeSignature {

    private static final String ALGORITHME = "Ed25519";

    /** Message signé puis vérifié pour s'assurer que les deux clés forment bien une paire. */
    private static final byte[] TEMOIN = "qualisira".getBytes(StandardCharsets.US_ASCII);

    @Value("${licences.cles.fichier:./data/cles-editeur.properties}")
    private String fichier;

    /**
     * Autorise l'engendrement d'une paire neuve quand rien n'est fourni.
     *
     * <p>Faux par défaut, et c'est tout l'intérêt : à vrai, on retrouve l'ancien comportement, qui
     * remplace une clé perdue sans rien dire. À n'activer que sur un poste de travail, ou le temps
     * du tout premier démarrage d'une installation dont on relèvera la clé ensuite.</p>
     */
    @Value("${licences.cles.engendrer-si-absent:false}")
    private boolean engendrerSiAbsent;

    private String clePriveeBase64;
    private String clePubliqueBase64;

    @PostConstruct
    void charger() throws Exception {
        if (chargerDepuisLeFichier()) {
            verifierQueLesClesVontEnsemble();
            return;
        }
        if (!engendrerSiAbsent) {
            throw new IllegalStateException(messageDAbsence());
        }
        engendrer();
    }

    private boolean chargerDepuisLeFichier() throws Exception {
        Path chemin = Path.of(fichier);
        if (!Files.exists(chemin)) {
            return false;
        }

        Properties proprietes = new Properties();
        try (var flux = Files.newInputStream(chemin)) {
            proprietes.load(flux);
        }
        this.clePriveeBase64 = proprietes.getProperty("cle.privee");
        this.clePubliqueBase64 = proprietes.getProperty("cle.publique");
        if (estVide(clePriveeBase64) || estVide(clePubliqueBase64)) {
            throw new IllegalStateException(
                    "Le fichier de clés " + chemin.toAbsolutePath() + " est incomplet.");
        }
        log.info("Clés de signature chargées depuis {}. Clé publique : {}",
                chemin.toAbsolutePath(), clePubliqueBase64);
        return true;
    }

    /**
     * Les deux clés forment-elles bien une paire ?
     *
     * <p>Elles se recopient à la main, d'un fichier à une console de déploiement. Prendre la privée
     * d'une paire et la publique d'une autre donnerait un service qui signe sans se plaindre, et
     * des licences qu'aucun produit ne peut vérifier — le défaut le plus coûteux du dispositif,
     * puisqu'il ne se voit qu'après livraison. Une signature d'essai le règle en une milliseconde,
     * une fois au démarrage.</p>
     */
    private void verifierQueLesClesVontEnsemble() {
        try {
            Signature signature = Signature.getInstance(ALGORITHME);
            signature.initSign(JetonDeLicence.clePriveeDepuis(clePriveeBase64));
            signature.update(TEMOIN);
            byte[] empreinte = signature.sign();

            Signature controle = Signature.getInstance(ALGORITHME);
            controle.initVerify(JetonDeLicence.clePubliqueDepuis(clePubliqueBase64));
            controle.update(TEMOIN);
            if (!controle.verify(empreinte)) {
                throw new IllegalStateException(
                        "La clé privée et la clé publique ne forment pas une paire : les licences "
                                + "seraient signées avec l'une et vérifiées avec l'autre, donc "
                                + "refusées par le produit. Reprenez les deux du même trousseau.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Les clés de signature sont illisibles : " + e.getMessage(), e);
        }
    }

    /** Ce qu'il faut lire quand le service refuse de démarrer : ce qui manque, et quoi faire. */
    private String messageDAbsence() {
        return """

                ════════════════════════════════════════════════════════════════════
                  AUCUNE CLÉ DE SIGNATURE — le service ne démarre pas.

                  Le fichier « %s » est introuvable. Engendrer une paire ici
                  remplacerait celle qui signe les licences déjà remises : elles
                  deviendraient toutes invérifiables chez les clients, sans le
                  moindre message.

                  Selon la situation :

                  • Le volume a été perdu ou n'est pas monté — RESTAUREZ la
                    sauvegarde du fichier de clés, et vérifiez que LICENCES_CLES
                    pointe bien un volume persistant. C'est le cas le plus
                    probable si des licences ont déjà été émises.

                  • Toute première installation, aucune licence émise — montez
                    d'abord le volume, puis demandez l'engendrement :
                        LICENCES_ENGENDRER_CLES=true

                    Relevez ensuite la clé publique sur /actuator/info, posez-la
                    dans le produit (QUALISIRA_CLE_PUBLIQUE), sauvegardez le
                    fichier, et remettez LICENCES_ENGENDRER_CLES à false.
                ════════════════════════════════════════════════════════════════════
                """.formatted(Path.of(fichier).toAbsolutePath());
    }

    /**
     * Engendre une paire neuve et l'écrit sur le disque.
     *
     * <p>Les clés sont engendrées plutôt que livrées avec le code : une clé versionnée est une clé
     * publique, quel que soit le nom qu'on lui donne.</p>
     */
    private void engendrer() throws Exception {
        Path chemin = Path.of(fichier);
        KeyPair paire = KeyPairGenerator.getInstance(ALGORITHME).generateKeyPair();
        this.clePriveeBase64 = Base64.getEncoder().encodeToString(paire.getPrivate().getEncoded());
        this.clePubliqueBase64 = Base64.getEncoder().encodeToString(paire.getPublic().getEncoded());

        Files.createDirectories(chemin.toAbsolutePath().getParent());
        String contenu = """
                # Clés de signature des licences QualiSira — NE JAMAIS VERSIONNER, NE JAMAIS LIVRER.
                # La clé privée signe les licences : sa divulgation permet d'en fabriquer sans limite.
                # Sauvegardez ce fichier : sans lui, toutes les licences émises deviennent invérifiables.
                cle.privee=%s
                cle.publique=%s
                """.formatted(clePriveeBase64, clePubliqueBase64);
        Files.writeString(chemin, contenu, StandardCharsets.UTF_8);
        try {
            // Lisible du seul propriétaire, quand le système de fichiers le permet.
            chemin.toFile().setReadable(false, false);
            chemin.toFile().setReadable(true, true);
            chemin.toFile().setWritable(false, false);
            chemin.toFile().setWritable(true, true);
        } catch (Exception e) {
            log.warn("Droits du fichier de clés non restreints : {}", e.getMessage());
        }

        log.warn("""

                ════════════════════════════════════════════════════════════════════
                  Nouvelles clés de signature engendrées : {}
                  SAUVEGARDEZ CE FICHIER. Sa perte rend invérifiables toutes les
                  licences émises ; sa divulgation permet d'en forger.
                  Clé publique à embarquer dans QualiSira :
                  {}
                  Elle s'annonce aussi sur /actuator/info.
                ════════════════════════════════════════════════════════════════════
                """, chemin.toAbsolutePath(), clePubliqueBase64);
    }

    private static boolean estVide(String valeur) {
        return valeur == null || valeur.isBlank();
    }

    public PrivateKey clePrivee() {
        return JetonDeLicence.clePriveeDepuis(clePriveeBase64);
    }

    /** À embarquer dans l'application livrée. Se diffuse sans risque. */
    public String clePubliqueBase64() {
        return clePubliqueBase64;
    }
}
