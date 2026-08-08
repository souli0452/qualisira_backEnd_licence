--
-- qualisira-licences — schéma initial.
--
-- Relevé du schéma tel que Hibernate l'avait créé, et repris ici pour que les évolutions
-- suivantes soient décrites plutôt que devinées. « ddl-auto » est désormais en « validate » :
-- il signale au démarrage tout écart entre les entités et le schéma migré, au lieu de corriger
-- la base en silence.
--
-- Une base déjà en service n'est pas vide : Flyway la marque « v1 » (baseline) au lieu de rejouer
-- ce script, qui échouerait sur des tables existantes. Une base vide, elle, le reçoit puis la
-- suite. Les deux convergent ensuite.
--
-- Ce que porte cette base mérite les mêmes égards que la production : les partenaires, leurs
-- conditions commerciales, et les licences signées qu'aucune réémission ne rattraperait.
--

CREATE TABLE IF NOT EXISTS public.licences (
    id uuid NOT NULL,
    debut date NOT NULL,
    emise_le timestamp(6) without time zone NOT NULL,
    emise_par character varying(120),
    envoyeea character varying(160),
    envoyee_le timestamp(6) without time zone,
    fin date NOT NULL,
    jeton character varying(4000) NOT NULL,
    motif_revocation character varying(500),
    reference character varying(40) NOT NULL,
    revoquee_le timestamp(6) without time zone,
    statut character varying(20) NOT NULL,
    type character varying(20) NOT NULL,
    utilisateurs_max integer NOT NULL,
    offre_id uuid,
    partenaire_id uuid NOT NULL,
    CONSTRAINT licences_statut_check CHECK (((statut)::text = ANY ((ARRAY['ACTIVE'::character varying, 'A_VENIR'::character varying, 'EXPIREE'::character varying, 'REVOQUEE'::character varying])::text[]))),
    CONSTRAINT licences_type_check CHECK (((type)::text = ANY ((ARRAY['COMMERCIALE'::character varying, 'ESSAI'::character varying])::text[])))
);

CREATE TABLE IF NOT EXISTS public.licences_modules (
    licence_id uuid NOT NULL,
    module character varying(40) NOT NULL,
    CONSTRAINT licences_modules_module_check CHECK (((module)::text = ANY ((ARRAY['NON_CONFORMITE'::character varying, 'DOCUMENTAIRE'::character varying, 'RECLAMATION'::character varying, 'RISQUE'::character varying, 'AUDIT'::character varying, 'FORMATION'::character varying, 'REGLEMENTATION'::character varying, 'EVALUATION'::character varying, 'CONTEXTE'::character varying])::text[])))
);

CREATE TABLE IF NOT EXISTS public.offres_abonnement (
    id uuid NOT NULL,
    actif boolean NOT NULL,
    code character varying(40) NOT NULL,
    description character varying(500),
    duree_mois integer NOT NULL,
    libelle character varying(120) NOT NULL,
    tarif character varying(40),
    utilisateurs_max integer NOT NULL,
    CONSTRAINT offres_abonnement_duree_mois_check CHECK ((duree_mois >= 1)),
    CONSTRAINT offres_abonnement_utilisateurs_max_check CHECK ((utilisateurs_max >= 0))
);

CREATE TABLE IF NOT EXISTS public.offres_modules (
    offre_id uuid NOT NULL,
    module character varying(40) NOT NULL,
    CONSTRAINT offres_modules_module_check CHECK (((module)::text = ANY ((ARRAY['NON_CONFORMITE'::character varying, 'DOCUMENTAIRE'::character varying, 'RECLAMATION'::character varying, 'RISQUE'::character varying, 'AUDIT'::character varying, 'FORMATION'::character varying, 'REGLEMENTATION'::character varying, 'EVALUATION'::character varying, 'CONTEXTE'::character varying])::text[])))
);

CREATE TABLE IF NOT EXISTS public.parametres (
    id uuid NOT NULL,
    cle character varying(80) NOT NULL,
    description character varying(500),
    libelle character varying(255) NOT NULL,
    modifie_le timestamp(6) without time zone,
    modifie_par character varying(255),
    type character varying(20) NOT NULL,
    valeur text,
    CONSTRAINT parametres_type_check CHECK (((type)::text = ANY ((ARRAY['TEXTE'::character varying, 'COURRIEL'::character varying, 'TELEPHONE'::character varying, 'URL'::character varying, 'IMAGE'::character varying])::text[])))
);

CREATE TABLE IF NOT EXISTS public.partenaires (
    id uuid NOT NULL,
    actif boolean NOT NULL,
    adresse character varying(250),
    code character varying(40) NOT NULL,
    contact_email character varying(160),
    contact_nom character varying(120),
    contact_telephone character varying(40),
    cree_le timestamp(6) without time zone NOT NULL,
    cree_par character varying(120),
    notes character varying(2000),
    pays character varying(120),
    raison_sociale character varying(200) NOT NULL,
    secteur_activite character varying(120),
    sigle character varying(40),
    ville character varying(120)
);

CREATE TABLE IF NOT EXISTS public.permissions (
    id uuid NOT NULL,
    code character varying(60) NOT NULL,
    domaine character varying(60) NOT NULL,
    libelle character varying(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS public.roles (
    id uuid NOT NULL,
    code character varying(60) NOT NULL,
    description character varying(500),
    libelle character varying(120) NOT NULL,
    systeme boolean NOT NULL
);

CREATE TABLE IF NOT EXISTS public.roles_permissions (
    role_id uuid NOT NULL,
    permission_id uuid NOT NULL
);

CREATE TABLE IF NOT EXISTS public.utilisateurs (
    id uuid NOT NULL,
    actif boolean NOT NULL,
    cree_le timestamp(6) without time zone NOT NULL,
    cree_par character varying(120),
    derniere_connexion timestamp(6) without time zone,
    doit_changer_mot_de_passe boolean NOT NULL,
    email character varying(160),
    identifiant character varying(80) NOT NULL,
    mot_de_passe character varying(100) NOT NULL,
    nom_complet character varying(160)
);

CREATE TABLE IF NOT EXISTS public.utilisateurs_roles (
    utilisateur_id uuid NOT NULL,
    role_id uuid NOT NULL
);

ALTER TABLE ONLY public.licences_modules
    ADD CONSTRAINT licences_modules_pkey PRIMARY KEY (licence_id, module);

ALTER TABLE ONLY public.licences
    ADD CONSTRAINT licences_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.offres_abonnement
    ADD CONSTRAINT offres_abonnement_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.offres_modules
    ADD CONSTRAINT offres_modules_pkey PRIMARY KEY (offre_id, module);

ALTER TABLE ONLY public.parametres
    ADD CONSTRAINT parametres_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.partenaires
    ADD CONSTRAINT partenaires_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.roles_permissions
    ADD CONSTRAINT roles_permissions_pkey PRIMARY KEY (role_id, permission_id);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.parametres
    ADD CONSTRAINT uk66f501x0ybntj7qo3th1t4e2a UNIQUE (cle);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT uk7lcb6glmvwlro3p2w2cewxtvd UNIQUE (code);

ALTER TABLE ONLY public.partenaires
    ADD CONSTRAINT uka0by4bovroo5an7yk7cx6rht2 UNIQUE (code);

ALTER TABLE ONLY public.utilisateurs
    ADD CONSTRAINT ukaoudmead16ptqds111rrrgrni UNIQUE (identifiant);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT ukch1113horj4qr56f91omojv8 UNIQUE (code);

ALTER TABLE ONLY public.licences
    ADD CONSTRAINT ukjknae1x8edu3fhkk1weg2u1rx UNIQUE (reference);

ALTER TABLE ONLY public.offres_abonnement
    ADD CONSTRAINT ukpfh77a5lu9d3faoltb855hgej UNIQUE (code);

ALTER TABLE ONLY public.utilisateurs
    ADD CONSTRAINT utilisateurs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.utilisateurs_roles
    ADD CONSTRAINT utilisateurs_roles_pkey PRIMARY KEY (utilisateur_id, role_id);

ALTER TABLE ONLY public.utilisateurs_roles
    ADD CONSTRAINT fk1foc70wx0qutcwr19a4jfoek2 FOREIGN KEY (role_id) REFERENCES public.roles(id);

ALTER TABLE ONLY public.licences_modules
    ADD CONSTRAINT fk4f0siogji5p4v543tp2nom3ef FOREIGN KEY (licence_id) REFERENCES public.licences(id);

ALTER TABLE ONLY public.roles_permissions
    ADD CONSTRAINT fkbx9r9uw77p58gsq4mus0mec0o FOREIGN KEY (permission_id) REFERENCES public.permissions(id);

ALTER TABLE ONLY public.licences
    ADD CONSTRAINT fkd2bu8yw4mag8mpiqjfknm0ln8 FOREIGN KEY (offre_id) REFERENCES public.offres_abonnement(id);

ALTER TABLE ONLY public.offres_modules
    ADD CONSTRAINT fkdfixcceinctowq0yp9b953h24 FOREIGN KEY (offre_id) REFERENCES public.offres_abonnement(id);

ALTER TABLE ONLY public.licences
    ADD CONSTRAINT fke6hp8walqe630dg2bkpcxxknx FOREIGN KEY (partenaire_id) REFERENCES public.partenaires(id);

ALTER TABLE ONLY public.utilisateurs_roles
    ADD CONSTRAINT fkn6gc9xu3vj7iivdrllrkmgyej FOREIGN KEY (utilisateur_id) REFERENCES public.utilisateurs(id);

ALTER TABLE ONLY public.roles_permissions
    ADD CONSTRAINT fkqi9odri6c1o81vjox54eedwyh FOREIGN KEY (role_id) REFERENCES public.roles(id);
