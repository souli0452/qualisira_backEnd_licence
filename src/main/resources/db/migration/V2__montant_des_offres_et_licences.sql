--
-- qualisira-licences — le montant, en nombre.
--
-- « tarif » était une chaîne libre, et son commentaire le disait : « montant indicatif, pour
-- mémoire au moment de l'émission. Ne sert à aucun calcul. » On pouvait y écrire « 150 000 FCFA »,
-- « 1.500 €/an » ou « à négocier » — rien de tout cela ne s'additionne, et le tableau de bord ne
-- pouvait donc rien dire des revenus.
--
-- Le montant devient un nombre, avec sa devise. Une valeur nulle reste admise : elle dit « à
-- négocier », ce que la chaîne libre exprimait, et se distingue d'un zéro qui affirmerait la
-- gratuité.
--
-- Le montant est RECOPIÉ sur la licence à l'émission, comme le sont déjà les modules et le
-- plafond d'utilisateurs. Le référencer aurait fait qu'un tarif révisé au catalogue réécrive
-- rétroactivement le chiffre d'affaires des exercices clos.
--

ALTER TABLE public.offres_abonnement
    ADD COLUMN IF NOT EXISTS montant numeric(14, 2),
    ADD COLUMN IF NOT EXISTS devise character varying(3) DEFAULT 'XOF' NOT NULL;

ALTER TABLE public.licences
    ADD COLUMN IF NOT EXISTS montant numeric(14, 2),
    ADD COLUMN IF NOT EXISTS devise character varying(3);

-- Les trois offres livrées n'avaient aucun tarif renseigné : rien à reprendre. La colonne est
-- retirée plutôt que laissée à l'abandon — deux champs de prix sur un même écran, dont un seul
-- compte, est une invitation à saisir le mauvais.
ALTER TABLE public.offres_abonnement DROP COLUMN IF EXISTS tarif;

COMMENT ON COLUMN public.licences.montant IS
    'Montant facture, fige a l emission. Nul pour un essai, ou pour une licence a negocier.';
