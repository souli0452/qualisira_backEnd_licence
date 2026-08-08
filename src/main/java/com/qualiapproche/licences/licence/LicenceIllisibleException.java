package com.qualiapproche.licences.licence;

/**
 * La chaîne fournie n'est pas une licence authentique : format inconnu, signature absente ou
 * invalide, contenu illisible.
 *
 * <p>À distinguer d'une licence expirée ou destinée à un autre partenaire — celles-là sont
 * authentiques, et méritent un message qui dit la vraie raison du refus.</p>
 */
public class LicenceIllisibleException extends RuntimeException {

    public LicenceIllisibleException(String message) {
        super(message);
    }

    public LicenceIllisibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
