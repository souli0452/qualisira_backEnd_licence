package com.qualiapproche.licences.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Sert le back-office depuis cette application.
 *
 * <p>Un seul serveur, donc une <b>seule origine</b> : la session est portée par un cookie
 * {@code SameSite=Lax}, qui n'accompagnerait pas des appels vers un autre domaine. Aucun CORS à
 * régler, aucun relais à configurer — et surtout, l'adresse de retour envoyée à Keycloak est celle
 * que le navigateur a réellement demandée, sans qu'un intermédiaire ait pu la réécrire. C'est le
 * piège qui coûte le plus cher dans ce genre de montage : il ne se voit qu'au passage à Keycloak,
 * sur un « Invalid parameter: redirect_uri » que rien ne relie à sa cause.</p>
 *
 * <h2>Où sont les fichiers</h2>
 *
 * <p>Dans le dossier que désigne {@code licences.front.dossier}, s'il est renseigné — les deux
 * projets vivant dans des dépôts distincts, le front se livre à côté du service plutôt que dans
 * son jar, et se met à jour sans reconstruire le serveur. À défaut, {@code classpath:/static/},
 * pour qui préfère tout empaqueter ensemble.</p>
 *
 * <h2>Le repli sur index.html</h2>
 *
 * <p>Le routage du back-office est côté navigateur. Sans repli, rafraîchir sur {@code /licences}
 * ou ouvrir un lien vers {@code /journal} demande au serveur un fichier qui n'existe pas : il
 * répond 404, et l'utilisateur conclut que l'application est cassée.</p>
 *
 * <p>Le repli ne vaut que pour ce qui <b>ressemble à une route</b> : les chemins de l'API, ceux
 * d'OAuth et tout ce qui porte une extension en sont exclus. Sans cette réserve, une image absente
 * rendrait la page d'accueil au lieu d'un 404, et l'écran afficherait du HTML là où il attend une
 * image — une panne bien plus difficile à lire qu'un fichier manquant.</p>
 */
@Configuration
@Slf4j
public class BackOfficeConfig implements WebMvcConfigurer {

    /** Chemins qui appartiennent au serveur, et ne sont jamais une route du back-office. */
    private static final String[] AU_SERVEUR = {"api/", "oauth2/", "login", "logout", "actuator/"};

    @Value("${licences.front.dossier:}")
    private String dossier;

    /**
     * La racine mène à l'accueil.
     *
     * <p>Un aiguillage explicite plutôt qu'un cas du résolveur : Spring lui présente la racine
     * sous la forme {@code "."}, que rien ne distingue d'un chemin ordinaire — et le repli la
     * manquait, rendant un 404 sur la seule adresse que tout le monde tape.</p>
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registre) {
        registre.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registre) {
        String emplacement = dossier == null || dossier.isBlank()
                ? "classpath:/static/"
                : "file:" + (dossier.endsWith("/") ? dossier : dossier + "/");

        log.info("Back-office servi depuis {}", emplacement);

        registre.addResourceHandler("/**")
                .addResourceLocations(emplacement)
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String chemin, Resource racine) throws IOException {
                        if (!chemin.isBlank()) {
                            Resource demandee = racine.createRelative(chemin);
                            // « isFile » et pas seulement « exists » : un dossier existe et se lit,
                            // et le rendre ferait échouer l'écriture de la réponse — sur une panne
                            // qui ne dit pas qu'on a demandé un dossier.
                            if (demandee.exists() && demandee.isReadable() && estUnFichier(demandee)) {
                                return demandee;
                            }
                            if (!estUneRoute(chemin)) {
                                return null;
                            }
                        }
                        // La racine « / » comme les routes du back-office : l'accueil.
                        Resource accueil = racine.createRelative("index.html");
                        return accueil.exists() ? accueil : null;
                    }

                    private boolean estUnFichier(Resource resource) {
                        try {
                            return resource.getFile().isFile();
                        } catch (IOException e) {
                            // Dans un jar, « getFile » n'a pas de sens : le contenu s'y lit en flux,
                            // et ce qui existe y est nécessairement un fichier.
                            return true;
                        }
                    }
                });
    }

    /**
     * Ce chemin est-il une route du back-office, plutôt qu'un fichier absent ?
     *
     * <p>L'extension tranche : {@code /licences} est une route, {@code /assets/logo.svg} un
     * fichier. Rendre l'accueil pour ce dernier masquerait sa disparition derrière une page qui
     * s'affiche — et l'on chercherait longtemps pourquoi une image est « vide ».</p>
     */
    private boolean estUneRoute(String chemin) {
        for (String reserve : AU_SERVEUR) {
            if (chemin.startsWith(reserve)) {
                return false;
            }
        }
        int dernierSegment = chemin.lastIndexOf('/');
        return !chemin.substring(dernierSegment + 1).contains(".");
    }
}
