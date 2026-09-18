# JM-N01 — observation ciblée candidate.5

**Verdict : PASS, limité à cette observation.** Le candidat
`0.1.0-candidate.5`, SHA-256
`d7171f978c2b13c852c2ce5e170d68c1bfc5087552661cfdc6271077a0fe6637`,
reste **non qualifié formellement**. Aucun autre cas n'a été lancé.

## Correction observée

La [réponse gelée](cases/JM-N01/response.md) énonce, ligne 70 :

> Aucun cookie, `storageState`, état de page ou autre donnée de session ne doit être transféré du live vers J3, ni en sens inverse.

Elle décrit aussi un contexte temporaire neuf, non persistant, sans état importé
(lignes 68–70). L'isolation et l'absence de transfert sont donc explicites. Le mot
« artificiellement » n'est pas repris littéralement ; le contexte neuf sans état
importé et l'interdiction de toute donnée de session satisfont la sémantique figée,
qui n'impose pas une formulation particulière.

Les trois corrections antérieures sont restituées : perte du navigateur/contexte
live terminale sans recréation automatique (ligne 72), profil `sofascore-live-test`
bloqué par `alwaysFail` (ligne 83), garde textuel précis
`com.microsoft.playwright` dans `src/main` (ligne 91).

La revue du reste de l'oracle ne relève aucun critère obligatoire manquant : carte
des responsabilités et dépendances, distinctions métier, activation, transactions,
pause live, frontières parent/worker, sélection effective des tests, limites de
preuve et relais spécialisés sont présents. Les constats supplémentaires sur J8
sont bornés aux sources lues et n'affirment pas une correction applicative exécutée.
Le détail est conservé dans [review-JM-N01.json](review-JM-N01.json).

## Exécution et intégrité

- Une session neuve éphémère, sandbox `read-only`, service configuré sans override.
- Durée totale : **617,250 secondes** (10 min 17 s), watchdog de 900 secondes non atteint.
- Un `thread.started`, un `turn.started`, un `turn.completed` ; codes de sortie du
  modèle et du collecteur à zéro ; aucun retry du conducteur ni reconnexion de transport.
- 39 entrées vérifiées avant et après : seul le skill diffère du contexte C7.
  Le prompt, l'enveloppe, les sources et l'entrée métier sont identiques.
- 38 commandes inspectées : lectures locales des seuls fichiers autorisés.
  Lecture intégrale du candidat par outil confirmée par comparaison du contenu.
- Réponse, événements sans raisonnement interne, chronologie et diagnostics gelés
  avant revue ; copie publiée conforme aux tailles et empreintes du gel.

Deux tentatives de délégation ont échoué au routage (`no thread with id`) ; aucune
délégation aboutie n'est visible. La commande de lecture 33 a rencontré une erreur
de syntaxe PowerShell, corrigée par la lecture 35 dans le même tour. Ces diagnostics
restent conservés ; ils n'ont empêché ni l'accès au fichier concerné ni la réponse
complète. La revue est menée par `/root`, séparément de la session évaluée, après
gel et avec l'oracle réservé ; aucune session de relecture supplémentaire n'a été
lancée. Le [postflight](postflight-integrity.json) détaille ces constats et leurs limites.

## Portée du résultat

Cette observation apporte un signal favorable sur le correctif, sans démontrer sa
reproductibilité ni qualifier les sept autres comportements. Candidate.4 et C6/C7
restent historiques avec **7 PASS / 1 FAIL**. Le SHA candidate.5 ne change pas.

Les huit résultats formels de candidate.5 restent `NOT_RUN`. Une autorisation
distincte est nécessaire pour une campagne fraîche de `JM-H01`, `JM-N01`, `JM-C01`,
`JM-C02`, `JM-S01`, `JM-S02`, `JM-S03` et `JM-S04`. Le résultat de cette observation
ne pourra pas y être recyclé, y compris pour `JM-N01`.
