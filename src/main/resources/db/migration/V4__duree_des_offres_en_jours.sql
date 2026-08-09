--
-- qualisira-licences — une durée d'offre exprimée en jours autant qu'en mois.
--
-- « duree_mois » ne savait dire qu'un nombre de mois. Un essai de sept jours, une extension d'un
-- mois et demi, une période de démonstration : rien de tout cela ne s'exprimait, et il fallait
-- imposer la durée à chaque émission — donc la ressaisir, donc l'oublier.
--
-- Une durée ET son unité, plutôt que deux colonnes dont une seule s'applique : « 7 » et « JOURS »
-- ne laissent aucune place au doute, là où un duree_mois à zéro accompagné d'un duree_jours à
-- sept aurait obligé chaque lecteur à deviner lequel prime.
--
-- Les offres existantes sont toutes en mois : leur valeur est reprise telle quelle.
--

ALTER TABLE public.offres_abonnement
    ADD COLUMN IF NOT EXISTS duree integer,
    ADD COLUMN IF NOT EXISTS unite_duree character varying(10);

UPDATE public.offres_abonnement
   SET duree = COALESCE(duree_mois, 12),
       unite_duree = 'MOIS'
 WHERE duree IS NULL;

ALTER TABLE public.offres_abonnement
    ALTER COLUMN duree SET NOT NULL,
    ALTER COLUMN unite_duree SET NOT NULL;

ALTER TABLE public.offres_abonnement DROP COLUMN IF EXISTS duree_mois;
