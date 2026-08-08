package com.qualiapproche.licences.licence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le format de licence : ce qu'il garantit, et ce qu'il ne garantit pas.
 *
 * <p>Ce qui se joue ici est la seule chose qui sépare une licence d'un texte quelconque — la
 * signature. Le chiffrement symétrique qu'elle remplace laissait forger n'importe quelle licence
 * à qui disposait du produit, la clé étant livrée avec.</p>
 */
class JetonDeLicenceTest {

    private static KeyPair editeur;
    private static String clePublique;

    @BeforeAll
    static void engendrerLesCles() throws Exception {
        editeur = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        clePublique = Base64.getEncoder().encodeToString(editeur.getPublic().getEncoded());
    }

    private ContenuDeLicence contenu() {
        return new ContenuDeLicence("LIC-2026-0001", "CHU-BF", "CHU du Burkina Faso",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                List.of("NON_CONFORMITE", "DOCUMENTAIRE"), 25, "COMMERCIALE", "Essentiel");
    }

    @Test
    @DisplayName("Une licence signée se relit à l'identique")
    void allerRetour() {
        ContenuDeLicence relu = JetonDeLicence.lire(
                JetonDeLicence.signer(contenu(), editeur.getPrivate()), clePublique);

        assertThat(relu.reference()).isEqualTo("LIC-2026-0001");
        assertThat(relu.partenaireCode()).isEqualTo("CHU-BF");
        assertThat(relu.debut()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(relu.fin()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(relu.modules()).containsExactly("NON_CONFORMITE", "DOCUMENTAIRE");
        assertThat(relu.utilisateursMax()).isEqualTo(25);
    }

    @Test
    @DisplayName("Le contenu reste lisible par le partenaire : il n'est pas chiffré, il est signé")
    void contenuLisible() {
        String jeton = JetonDeLicence.signer(contenu(), editeur.getPrivate());
        String charge = new String(Base64.getUrlDecoder().decode(jeton.split("\\.")[1]));

        // C'est voulu : le client doit pouvoir vérifier ce qu'il a acheté. Ce qu'il ne peut pas,
        // c'est en fabriquer une autre.
        assertThat(charge).contains("CHU-BF").contains("NON_CONFORMITE");
    }

    @Test
    @DisplayName("Une licence retouchée est rejetée — c'est tout l'objet de la signature")
    void contenuFalsifie() {
        String jeton = JetonDeLicence.signer(contenu(), editeur.getPrivate());
        String[] parties = jeton.split("\\.");

        // On prolonge la licence de dix ans et on rouvre tous les modules, comme le ferait
        // quelqu'un qui a compris le format.
        String chargeForgee = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"ref\":\"LIC-2026-0001\",\"cli\":\"CHU-BF\",\"deb\":\"2026-01-01\","
                        + "\"fin\":\"2036-12-31\",\"mod\":[\"NON_CONFORMITE\",\"AUDIT\"],\"usr\":0}")
                        .getBytes());

        String contrefacon = parties[0] + "." + chargeForgee + "." + parties[2];

        assertThatThrownBy(() -> JetonDeLicence.lire(contrefacon, clePublique))
                .isInstanceOf(LicenceIllisibleException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("Une licence signée par une autre clé est rejetée")
    void signeeParUnAutre() throws Exception {
        KeyPair imposteur = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String jeton = JetonDeLicence.signer(contenu(), imposteur.getPrivate());

        // Le cas du client qui recompile le produit avec sa propre clé : il faut qu'il ait à le
        // faire délibérément, et non qu'une licence étrangère passe toute seule.
        assertThatThrownBy(() -> JetonDeLicence.lire(jeton, clePublique))
                .isInstanceOf(LicenceIllisibleException.class);
    }

    @Test
    @DisplayName("Les espaces d'un copier-coller ne font pas échouer la lecture")
    void espacesTolerees() {
        String jeton = JetonDeLicence.signer(contenu(), editeur.getPrivate());
        String colle = "  " + jeton.substring(0, 40) + "\n   " + jeton.substring(40) + "  \n";

        // Un courriel replie les longues lignes : refuser pour cela seul serait incompréhensible
        // pour qui a pourtant collé la bonne licence.
        assertThat(JetonDeLicence.lire(colle, clePublique).reference()).isEqualTo("LIC-2026-0001");
    }

    @Test
    @DisplayName("Un texte qui n'est pas une licence le dit clairement")
    void texteQuelconque() {
        assertThatThrownBy(() -> JetonDeLicence.lire("bonjour", clePublique))
                .isInstanceOf(LicenceIllisibleException.class)
                .hasMessageContaining("licence QualiSira");
    }

    @Test
    @DisplayName("La validité dans le temps est une question distincte de l'authenticité")
    void expirationDistincteDeLAuthenticite() {
        ContenuDeLicence perimee = new ContenuDeLicence("LIC-2020-0001", "CHU-BF", "CHU du Burkina Faso",
                LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31),
                List.of("NON_CONFORMITE"), 10, "COMMERCIALE", "Essentiel");

        ContenuDeLicence relue = JetonDeLicence.lire(
                JetonDeLicence.signer(perimee, editeur.getPrivate()), clePublique);

        // Elle se relit sans erreur : elle est authentique. C'est à l'appliquant de dire
        // « votre abonnement a pris fin le … » plutôt que « licence invalide ».
        assertThat(relue.couvre(LocalDate.of(2026, 6, 1))).isFalse();
        assertThat(relue.couvre(LocalDate.of(2020, 6, 1))).isTrue();
    }

    @Test
    @DisplayName("Une licence tient dans un champ de saisie")
    void tailleRaisonnable() {
        String jeton = JetonDeLicence.signer(contenu(), editeur.getPrivate());

        // Ed25519 plutôt que RSA : la signature fait 64 octets, contre 256 pour une RSA-2048.
        // Personne ne recopie sans erreur une chaîne de deux mille caractères.
        assertThat(jeton.length()).isLessThan(500);
    }
}
