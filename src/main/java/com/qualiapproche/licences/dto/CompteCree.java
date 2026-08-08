package com.qualiapproche.licences.dto;

/**
 * Le compte, et le mot de passe provisoire s'il a été tiré au hasard.
 *
 * <p>Ce mot de passe n'est lisible qu'ici, dans cette seule réponse : la base n'en garde que
 * l'empreinte. Ne pas le rendre obligerait à en réinitialiser un aussitôt après la création.</p>
 *
 * <p>{@code null} quand l'administrateur a saisi lui-même le mot de passe : il le connaît déjà,
 * et le renvoyer ne ferait que le promener une fois de plus sur le réseau.</p>
 */
public record CompteCree(UtilisateurVue utilisateur, String motDePasseProvisoire) {
}
