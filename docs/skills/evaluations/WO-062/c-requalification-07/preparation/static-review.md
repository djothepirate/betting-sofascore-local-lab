# Revue statique C7 — sept cas Java restants

**Résultat : PASS_PRELAUNCH.** L'autorisation C7 borne exactement sept sessions fraîches, séquentielles et lecture seule : JM-N01, JM-C01, JM-C02, JM-S01, JM-S02, JM-S03 et JM-S04. Aucun cas Windows, aucune reprise C6 et aucun retry n'entrent dans le conducteur.

Le conducteur C7 est une copie distincte du conducteur C5. Il prépare seulement les sept contextes neufs, refuse tout autre run, skill, cas ou nombre de workers, puis lance les sessions une par une sous codex exec ephemeral read-only. Les conducteurs C5 et C6 restent inchangés. Le contrôle de contexte rapproche chaque enveloppe, demande et prompt de son contrat C5 d'origine : 39, 17, 27, 2, 2, 2 et 2 fichiers respectivement, sans oracle, réponse antérieure ni sortie avant lancement.

Aucun modèle, build, test applicatif, fournisseur, navigateur, base, Docker, CI ou mutation de source n'a été lancé pendant cette préparation.

Après le gel, chaque cas devra recevoir un audit indépendant des chemins de commandes
Java. Le conducteur C5 dont C7 dérive réserve son audit de lecture intégré à WR-H01 :
son statut non applicable ne peut pas, à lui seul, prouver le respect des allowlists Java.
