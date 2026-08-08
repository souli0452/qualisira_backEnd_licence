--
-- qualisira-licences — le journal des actions.
--
-- Cet outil signe les licences : savoir qui a émis quoi n'est pas un confort. Le nom de
-- l'émetteur figure déjà dans chaque licence, mais rien ne disait qui avait suspendu un compte,
-- changé les permissions d'un rôle ou remplacé une coordonnée — des gestes qui ne laissent aucune
-- trace dans leur résultat.
--
-- Les échecs y figurent au même titre que les succès, et ce sont souvent eux qui comptent : un
-- refus de connexion répété, une révocation tentée sans la permission.
--
-- Ce qui n'y figure pas, volontairement : le CORPS des requêtes. Il porte des mots de passe à la
-- création d'un compte comme à sa réinitialisation, et un journal qui les recopierait serait plus
-- dangereux que l'absence de journal.
--
-- Aucune permission d'écriture n'existe côté application : un registre qu'on peut retoucher ne
-- prouve rien. Sa purge relève de l'exploitation, sur une politique de conservation décidée
-- ailleurs — et cette table grossit, il faudra en fixer une.
--

CREATE TABLE IF NOT EXISTS public.journal (
    id        uuid NOT NULL,
    quand     timestamp(6) without time zone NOT NULL,
    -- « anonyme » pour une connexion refusée : c'est l'entrée qu'on vient chercher, l'écarter
    -- faute d'authentification la rendrait invisible.
    auteur    character varying(120) NOT NULL,
    action    character varying(120) NOT NULL,
    objet     character varying(60),
    objet_id  character varying(80),
    requete   character varying(300) NOT NULL,
    abouti    boolean NOT NULL,
    motif     character varying(500),
    adresse   character varying(60),
    duree     bigint,
    CONSTRAINT journal_pkey PRIMARY KEY (id)
);

-- Les deux accès du journal : par date, et par personne.
CREATE INDEX IF NOT EXISTS idx_journal_quand ON public.journal (quand DESC);
CREATE INDEX IF NOT EXISTS idx_journal_auteur ON public.journal (auteur);
