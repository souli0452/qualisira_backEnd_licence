package com.qualiapproche.licences.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Force la production du jeton anti-rejeu, pour qu'il parvienne au navigateur.
 *
 * <p>Spring Security 6 ne le calcule qu'au moment où quelqu'un le réclame. Un back-office qui
 * n'envoie que des lectures au démarrage ne le réclame jamais : le cookie {@code XSRF-TOKEN}
 * n'est pas posé, et la première écriture — se connecter, émettre une licence — part sans jeton
 * et se fait refuser. L'erreur est d'autant plus déroutante qu'elle ne survient qu'à la première
 * action, jamais à l'ouverture de l'écran.</p>
 *
 * <p>Lire la valeur du jeton suffit à déclencher son écriture dans le cookie.</p>
 */
@Component
public class JetonAntiRejeuFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse,
                                    FilterChain suite) throws ServletException, IOException {
        CsrfToken jeton = (CsrfToken) requete.getAttribute(CsrfToken.class.getName());
        if (jeton != null) {
            jeton.getToken();
        }
        suite.doFilter(requete, reponse);
    }
}
