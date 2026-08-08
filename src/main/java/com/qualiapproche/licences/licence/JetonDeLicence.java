package com.qualiapproche.licences.licence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Le format d'une licence QualiSira, et les deux opérations qu'on lui applique : signer — ici
 * seulement — et vérifier — partout.
 *
 * <p>Une licence est une chaîne de trois parties séparées par des points :</p>
 * <pre>QSL1.&lt;contenu en base64url&gt;.&lt;signature en base64url&gt;</pre>
 *
 * <p>Le contenu est lisible : le partenaire peut décoder sa propre licence et vérifier ce qu'il a
 * acheté. C'est voulu. Ce qu'il ne peut pas faire, c'est en fabriquer une autre : la signature
 * Ed25519 exige la clé privée, qui ne quitte jamais cet outil. Le produit livré ne connaît que la
 * clé publique, avec laquelle on vérifie sans pouvoir signer.</p>
 *
 * <p>C'est toute la différence avec le chiffrement symétrique employé jusqu'ici : une clé partagée
 * entre l'émetteur et le lecteur permet aussi bien de lire que d'écrire. Livrée avec le produit,
 * elle laissait fabriquer n'importe quelle licence.</p>
 *
 * <p>Ed25519 plutôt que RSA : la signature tient en 64 octets, soit 86 caractères une fois encodée.
 * Une licence complète fait environ 350 caractères — assez courte pour être collée dans un champ
 * de saisie sans que personne ne renonce en cours de route. Le JDK la prend en charge nativement
 * depuis la version 15 : aucune dépendance à ajouter, ni ici ni dans le produit.</p>
 *
 * <p><b>Cette classe est prévue pour être recopiée telle quelle dans l'application livrée</b>, où
 * seule {@link #lire(String, String)} sert. Elle ne dépend que du JDK et de Jackson, présent dans
 * toute application Spring Boot.</p>
 */
public final class JetonDeLicence {

    /** Version du format. Un changement de structure incrémentera ce préfixe. */
    public static final String PREFIXE = "QSL1";

    private static final String ALGORITHME = "Ed25519";

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JetonDeLicence() {
    }

    /**
     * Émet une licence signée. Réservé à cet outil : c'est la seule opération qui exige la clé
     * privée.
     */
    public static String signer(ContenuDeLicence contenu, PrivateKey clePrivee) {
        try {
            String charge = encoder(JSON.writeValueAsBytes(contenu));
            // La signature porte sur la forme encodée, et non sur l'objet : deux sérialisations
            // JSON du même contenu peuvent différer par l'ordre des propriétés ou un espace, ce
            // qui suffirait à invalider une licence pourtant authentique.
            Signature signature = Signature.getInstance(ALGORITHME);
            signature.initSign(clePrivee);
            signature.update(charge.getBytes(StandardCharsets.US_ASCII));
            return PREFIXE + "." + charge + "." + encoder(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("La licence n'a pas pu être signée : " + e.getMessage(), e);
        }
    }

    /**
     * Vérifie l'authenticité d'une licence et en rend le contenu.
     *
     * <p>Ne juge <b>que</b> l'authenticité : ni les dates, ni le destinataire, ni les modules ne
     * sont examinés ici. C'est à l'appelant de le faire, pour qu'il puisse distinguer une licence
     * contrefaite — qu'on refuse — d'une licence authentique mais expirée, dont on peut dire
     * exactement quand elle a pris fin.</p>
     *
     * @param jeton             la chaîne collée par l'administrateur, espaces et retours à la
     *                          ligne tolérés — un copier-coller depuis un courriel en ajoute
     * @param clePubliqueBase64 la clé publique de l'éditeur, en base64 (format X.509)
     * @throws LicenceIllisibleException si le format, la signature ou le contenu ne tiennent pas
     */
    public static ContenuDeLicence lire(String jeton, String clePubliqueBase64) {
        if (jeton == null || jeton.isBlank()) {
            throw new LicenceIllisibleException("Aucune licence n'a été fournie.");
        }
        // Un copier-coller depuis un courriel ou un PDF ramène des espaces et des sauts de ligne.
        // Les refuser pour cela seul serait incompréhensible pour qui a collé la bonne licence.
        String nettoye = jeton.replaceAll("\\s", "");

        String[] parties = nettoye.split("\\.");
        if (parties.length != 3 || !PREFIXE.equals(parties[0])) {
            throw new LicenceIllisibleException(
                    "Ce texte n'est pas une licence QualiSira. Vérifiez que la copie est complète.");
        }

        try {
            PublicKey clePublique = clePubliqueDepuis(clePubliqueBase64);
            Signature signature = Signature.getInstance(ALGORITHME);
            signature.initVerify(clePublique);
            signature.update(parties[1].getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(decoder(parties[2]))) {
                throw new LicenceIllisibleException(
                        "La signature de cette licence est invalide : elle n'a pas été émise par "
                                + "l'éditeur, ou son texte a été modifié.");
            }
        } catch (LicenceIllisibleException e) {
            throw e;
        } catch (Exception e) {
            throw new LicenceIllisibleException(
                    "La signature de cette licence n'a pas pu être vérifiée.", e);
        }

        try {
            return JSON.readValue(decoder(parties[1]), ContenuDeLicence.class);
        } catch (Exception e) {
            throw new LicenceIllisibleException("Le contenu de cette licence est illisible.", e);
        }
    }

    public static PublicKey clePubliqueDepuis(String base64) {
        try {
            byte[] octets = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
            return KeyFactory.getInstance(ALGORITHME).generatePublic(new X509EncodedKeySpec(octets));
        } catch (Exception e) {
            throw new LicenceIllisibleException("Clé publique de vérification inutilisable.", e);
        }
    }

    public static PrivateKey clePriveeDepuis(String base64) {
        try {
            byte[] octets = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
            return KeyFactory.getInstance(ALGORITHME).generatePrivate(new PKCS8EncodedKeySpec(octets));
        } catch (Exception e) {
            throw new IllegalStateException("Clé privée de signature inutilisable.", e);
        }
    }

    /** Base64 sans remplissage : le point sert de séparateur, le « = » n'a rien à faire là. */
    private static String encoder(byte[] octets) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    private static byte[] decoder(String texte) {
        return Base64.getUrlDecoder().decode(texte);
    }
}
