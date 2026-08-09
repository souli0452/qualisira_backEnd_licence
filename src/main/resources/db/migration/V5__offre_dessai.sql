--
-- qualisira-licences — l'essai devient une offre comme une autre.
--
-- L'essai était un chemin à part : sa durée venait d'une variable d'environnement, ses modules
-- étaient tous ouverts en dur, et son plafond d'utilisateurs valait « sans limite ». Rien de tout
-- cela ne se réglait sans livrer une version, et personne ne savait ce qui avait été accordé au
-- prospect précédent.
--
-- Il devient une offre du catalogue, portant ce drapeau. Ce que le drapeau change, et qu'un code
-- « ESSAI » ne pourrait pas porter de façon fiable :
--   - la licence émise est de type ESSAI, ce que l'écran signale au partenaire ;
--   - un seul essai par partenaire, règle qui existait déjà et qui est conservée ;
--   - la licence n'est pas facturée, quel que soit le montant porté par l'offre.
--
-- Plusieurs offres d'essai peuvent coexister — une courte, une longue pour un grand compte — et
-- la règle du « un seul par partenaire » vaut pour l'ensemble.
--

ALTER TABLE public.offres_abonnement
    ADD COLUMN IF NOT EXISTS essai boolean DEFAULT false NOT NULL;

UPDATE public.offres_abonnement SET essai = true WHERE upper(code) = 'ESSAI';

COMMENT ON COLUMN public.offres_abonnement.essai IS
    'Offre d essai : type ESSAI, un seul par partenaire, jamais facturee.';
