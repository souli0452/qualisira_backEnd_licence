# QualiSira — Service de licences

Émission des licences signées remises aux partenaires chez qui QualiSira est installé.
Back-office associé : **`backOffice_licences`** (Angular), projet séparé.

> **Cet outil ne part jamais chez un partenaire.** Il détient la clé privée qui signe toutes les
> licences : la livrer reviendrait à laisser chacun s'en émettre une.

## Pourquoi signer plutôt que chiffrer

QualiSira s'installe chez le client : la base, les fichiers et le serveur lui appartiennent. Aucun
secret conservé là-bas ne protège quoi que ce soit — c'est pourquoi les licences ne sont pas
chiffrées mais **signées** (Ed25519).

- la **clé privée** vit ici, et nulle part ailleurs : elle seule permet d'émettre ;
- la **clé publique** est embarquée dans QualiSira : elle permet de vérifier, jamais de signer.

Le contenu d'une licence est lisible par son destinataire — c'est voulu, il doit pouvoir vérifier
ce qu'il a acheté. Ce qu'il ne peut pas faire, c'est en fabriquer une autre.

Une licence tient en ~340 caractères, assez court pour être collé sans que personne ne renonce.

## Démarrage

Prérequis : Java 21+, Maven, PostgreSQL.

```bash
createdb qualisira_licencesdb          # une fois
LICENCES_ADMIN_MDP=<mot-de-passe> mvn spring-boot:run
```

Le service écoute sur **8099**. Au premier démarrage il engendre sa paire de clés dans
`data/cles-editeur.properties`, inscrit trois offres au catalogue, charge les permissions et crée
le **super administrateur** — le compte par lequel tous les autres seront ouverts.

## Réglages

| Variable | Défaut | Rôle |
|---|---|---|
| `LICENCES_PORT` | `8099` | port d'écoute |
| `LICENCES_DB_URL` | `jdbc:postgresql://localhost:5432/qualisira_licencesdb` | base |
| `LICENCES_DB_USER` / `LICENCES_DB_MDP` | `postgres` / `postgres` | accès à la base |
| `LICENCES_AUTH` | `local` | `keycloak` en fonctionnement normal |
| `LICENCES_ADMIN_UTILISATEUR` / `LICENCES_ADMIN_MDP` | `admin` / *(tiré au hasard)* | super administrateur créé au premier démarrage |
| `LICENCES_ADMIN_NOM` / `LICENCES_ADMIN_EMAIL` | *(Super administrateur)* / — | son identité, et où partent ses accès |
| `LICENCES_ADMIN_PROVISION` | `true` | mode Keycloak : ouvrir le premier compte dans le royaume |
| `KC_LICENCES_ADMIN_CLIENT` / `KC_LICENCES_ADMIN_SECRET` | *(le client de connexion)* | client d'administration, s'il doit être distinct |
| `LICENCES_URL` | — | adresse publique, pour le lien dans les courriels |
| `LICENCES_ROLE` | `LICENCES_EDITEUR` | rôle Keycloak exigé pour entrer |
| `KC_LICENCES_ISSUER` | `http://localhost:8080/realms/qualisira-licences` | royaume Keycloak dédié |
| `KC_LICENCES_CLIENT` / `KC_LICENCES_SECRET` | `qualisira-licences` / *(secret de développement)* | client du royaume |
| `LICENCES_CORS` | `http://localhost:4300` | origines du back-office |
| `LICENCES_COOKIE_SECURE` | `false` | à passer à `true` derrière HTTPS |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | *(qualisira.com)* | remise par courriel |

Sans `LICENCES_ADMIN_MDP`, un mot de passe est tiré au hasard et annoncé **une fois** au démarrage,
à la création du super administrateur. Notez-le : le compte n'est pas recréé aux démarrages
suivants, et seule son empreinte est conservée.

## Comptes, rôles et permissions

Trois tables, et une règle : **un compte ne porte aucun droit en propre.**

```
utilisateurs ──< utilisateurs_roles >── roles ──< roles_permissions >── permissions
```

Les contrôles portent sur la **permission**, jamais sur le rôle — chaque méthode de contrôleur
déclare la sienne (`@PreAuthorize("hasAuthority('LICENCE_EMETTRE')")`). Ouvrir une action à un
profil de plus consiste donc à cocher une case dans l'écran des rôles, sans recompiler. Contrôler
le rôle aurait obligé à énumérer dans le code, action par action, tous les rôles autorisés.

**Au premier démarrage**, le service inscrit les 18 permissions du catalogue, crée trois rôles et
un compte :

| Rôle | Ce qu'il ouvre |
|---|---|
| `SUPER_ADMIN` | tout, dont les comptes et les rôles. Reçoit à **chaque démarrage** toutes les permissions, y compris celles ajoutées par une nouvelle version |
| `EDITEUR` | partenaires, offres, licences (émission, envoi, révocation). Aucune main sur les comptes |
| `LECTEUR` | consultation seule |

Le compte `LICENCES_ADMIN_UTILISATEUR` porte `SUPER_ADMIN` : c'est lui qui ouvre les comptes des
autres. Rien n'est recréé tant qu'un compte actif porte ce rôle — renommer ou remplacer ce compte
est donc sans danger. Les rôles créés ne sont pas réécrits ensuite, `SUPER_ADMIN` excepté.

La liste des permissions appartient au code (`PermissionQualiSira`) et la table n'en est que le
reflet : leurs codes sont ce qu'écrivent les `@PreAuthorize`, et un code saisi de travers depuis
l'écran n'ouvrirait rien sans que la cause soit visible. C'est leur **attribution aux rôles** qui
s'administre.

**Quatre refus** protègent l'outil de lui-même, et non le confort de saisie :

- on n'attribue pas `SUPER_ADMIN` si on ne le porte pas — sans quoi la seule permission « créer un
  compte » suffirait à se hisser au rang supérieur ;
- on ne retire pas `SUPER_ADMIN`, ni ne suspend, ni ne supprime **le dernier qui le porte** ;
- on ne suspend ni ne supprime **son propre compte** ;
- les permissions de `SUPER_ADMIN` ne se retirent pas : il est le seul recours si la gestion des
  comptes se referme.

## Authentification

La session est portée par un **cookie** (`LICENCES_SESSION`, HTTP-Only, SameSite=Lax) : le
back-office présente les identifiants une fois, le navigateur transporte la session ensuite. Rien
n'est conservé côté client.

Les écritures exigent le jeton anti-rejeu déposé dans le cookie `XSRF-TOKEN` et renvoyé en
en-tête `X-XSRF-TOKEN` — sans quoi un site tiers pourrait faire émettre une licence par le
navigateur d'un administrateur connecté.

**Mode Keycloak** (`LICENCES_AUTH=keycloak`) : connexion par le fournisseur d'identité, avec le
rôle `LICENCES_EDITEUR` exigé. À créer dans le royaume et à n'attribuer qu'à l'équipe éditeur —
accéder à cet outil, c'est pouvoir s'émettre une licence.

Keycloak dit **qui** est l'utilisateur et quels rôles il porte ; la table `roles` dit ce que ces
rôles **ouvrent**. Un compte du royaume portant `SUPER_ADMIN` reçoit donc les permissions du rôle
`SUPER_ADMIN` de cette base, exactement comme un compte local — il suffit de créer le rôle du même
nom dans le royaume. Décrire les droits action par action dans Keycloak aurait obligé à y recopier,
et à y maintenir, un catalogue qui appartient à cette application. Aucun compte local n'est créé
dans ce mode : les mots de passe sont tenus par le royaume.

### Le royaume, tel qu'il doit être

Royaume **dédié** (`qualisira-licences`), et non celui des autres services QualiSira : les comptes
qui signent des licences n'ont pas à se mêler à ceux qui saisissent des audits, et un royaume à
part se ferme sans toucher au reste.

| À déclarer | Valeur |
| --- | --- |
| Client | `qualisira-licences`, confidentiel, flot standard |
| URI de redirection | `http://localhost:8099/*` — celle de **ce service**, qui sert aussi le back-office |
| Déconnexion | `post.logout.redirect.uris` = `http://localhost:8099/*` |
| Rôles du royaume | `LICENCES_EDITEUR` (entrée) **et** `SUPER_ADMIN` / `EDITEUR` / `LECTEUR` (droits) |

Chaque compte porte **deux** rôles : celui d'entrée, et son rôle métier. Le rôle d'entrée n'accorde
aucune permission — avec lui seul, l'application s'ouvre sur des actions toutes refusées.

Un réglage échappe à la console et casse tout en silence : les mappeurs **`realm roles`** et
**`client roles`** de la portée `roles` doivent cocher *Add to ID token*. Keycloak ne les met par
défaut que dans le jeton d'accès, alors que Spring lit les revendications du jeton d'identité :
sans cela l'authentification réussit, l'utilisateur n'a aucun rôle, et tout le monde bute sur le
rôle d'entrée sans qu'aucun message n'en dise la raison.

L'adresse de retour est déduite de l'en-tête `Host` reçu par ce service. Comme il sert lui-même le back-office, il n'y a qu'une origine et la question ne se pose pas. Si un répartiteur ou un terminateur TLS est placé devant, il doit **conserver cet en-tête** (`proxy_set_header Host $host;`) : le remplacer ferait réclamer une adresse interne, que le royaume refuserait.

### Le premier compte

La mise en route est un cercle : gérer les comptes exige un compte. C'est le premier démarrage qui
le rompt, et il le rompt là où vivent les mots de passe — en base en mode local, **dans le royaume**
en mode Keycloak.

Le mot de passe part par **courriel** à `LICENCES_ADMIN_EMAIL` plutôt que de s'afficher. Cette boîte
doit exister : une adresse plausible mais non ouverte est acceptée par le serveur SMTP, puis refusée
à la remise (`550 Mailbox does not exist`), et rien ne le dit au démarrage. `LICENCES_EXPEDITEUR`,
lui, doit être une **adresse** et non un libellé — un simple nom fait échouer tous les envois avant
même que le serveur soit contacté. Le journal
du premier démarrage, sur une installation livrée, part dans un collecteur que personne ne lit à
cet instant précis, et le mot de passe s'y perd — sans autre issue que de réécrire une empreinte
dans la base. Il reste annoncé dans le journal quand l'envoi échoue : réglez donc le SMTP **avant**
le premier démarrage. Dans les deux cas ce mot de passe ne vaut que pour entrer une fois, son
changement étant réclamé avant toute autre action.

En mode Keycloak, l'application crée le compte dans le royaume, y crée les rôles `LICENCES_EDITEUR`
et `SUPER_ADMIN` s'ils manquent, et les lui attribue. **Rien n'est fait si un compte porte déjà le
rôle d'entrée** : un royaume en service ne se voit pas ajouter de compte que personne n'a demandé.
Un échec — Keycloak injoignable, droits refusés — n'interrompt jamais le démarrage : il se signale
dans le journal en disant quoi faire à la main, et les licences déjà émises restent servies.

Cela demande d'activer le **compte de service** du client et de lui donner `manage-users` et
`manage-realm`. Ce droit mérite d'être pesé : un client autorisé à créer des comptes dans le royaume
peut s'en créer un, et sur un royaume partagé cette portée dépasse cet outil. `KC_LICENCES_ADMIN_CLIENT`
permet de le confier à un client distinct de celui que rencontrent les utilisateurs, et
`LICENCES_ADMIN_PROVISION=false` de ne pas l'accorder du tout — le premier compte se crée alors
depuis la console.

## API

Chaque appel exige sa permission ; sans elle, la réponse est un **403** portant un message
lisible, et non une erreur technique.

| Méthode | Chemin | Rôle | Permission |
|---|---|---|---|
| `GET/POST/PUT` | `/api/partenaires` | fichier des clients | `PARTENAIRE_LIRE` · `_CREER` · `_MODIFIER` |
| `GET/POST/PUT` | `/api/offres` | catalogue commercial | `OFFRE_LIRE` · `_CREER` · `_MODIFIER` |
| `GET` | `/api/offres/modules` | modules vendables | `OFFRE_LIRE` |
| `GET/POST/PUT/DELETE` | `/api/utilisateurs` | comptes du back-office | `UTILISATEUR_LIRE` · `_CREER` · `_MODIFIER` · `_SUPPRIMER` |
| `POST` | `/api/utilisateurs/{id}/mot-de-passe` | réinitialisation | `UTILISATEUR_MOT_DE_PASSE` |
| `POST` | `/api/utilisateurs/{id}/activation` | suspendre / rétablir | `UTILISATEUR_MODIFIER` |
| `GET/POST/PUT/DELETE` | `/api/roles` | rôles et leurs permissions | `HABILITATION_LIRE` · `HABILITATION_GERER` |
| `GET` | `/api/roles/permissions` | catalogue des permissions | `HABILITATION_LIRE` |
| `POST` | `/api/session/mot-de-passe` | changer le sien | *(aucune)* |
| `GET/POST` | `/api/licences` | liste, émission | `LICENCE_LIRE` · `LICENCE_EMETTRE` |
| `POST` | `/api/licences/essai` | essai gratuit (un seul par partenaire) | `LICENCE_EMETTRE` |
| `POST` | `/api/licences/{id}/envoyer` | remise par courriel, `.lic` en pièce jointe | `LICENCE_ENVOYER` |
| `GET` | `/api/licences/{id}/fichier` | téléchargement du `.lic` | `LICENCE_LIRE` |
| `POST` | `/api/licences/{id}/revoquer` | révocation (voir ci-dessous) | `LICENCE_REVOQUER` |
| `POST` | `/api/licences/verifier` | relit une licence comme le fera le produit | `LICENCE_VERIFIER` |
| `GET` | `/api/licences/cle-publique` | clé à embarquer dans QualiSira | `LICENCE_VERIFIER` |
| `POST` | `/api/session/connexion` · `/deconnexion` | session | *(aucune)* |

## À savoir

**Une licence ne se modifie pas.** Elle est signée à l'émission ; le jeton fait foi chez le
client. Prolonger un abonnement consiste à en **émettre une nouvelle**, ce qui laisse au passage
l'historique de ce qui a été vendu et quand.

**La révocation ne désarme aucune installation.** Le produit tourne hors ligne et ne vient jamais
demander si sa licence a été retirée : la révocation vaut pour le suivi commercial. Le seul vrai
levier est la durée — des licences d'un an, renouvelées.

**Sauvegardez `data/cles-editeur.properties`.** Sa perte rend invérifiables toutes les licences en
circulation ; sa divulgation permet d'en forger. Ce dossier n'est pas versionné.
