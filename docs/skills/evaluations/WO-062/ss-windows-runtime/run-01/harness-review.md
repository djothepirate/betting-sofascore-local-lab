# WO-062 C — revue locale du lanceur et correctifs bornés

- Date de fin : 2026-09-15 20:58 UTC.
- Worktree : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2`.
- HEAD observé au début : `6648dd423e556b5248b8793a539ae85a7680f9bc`.
- Périmètre : comparaison du lanceur B et de sa copie C, corrections de la seule copie C autorisées par le superviseur, contrôles synthétiques sans processus externe ni modèle.
- Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Conclusion

**Correctifs locaux vérifiés : 17 tests réussis, zéro échec et zéro erreur.** Aucun défaut bloquant du lanceur corrigé n'a été relevé dans le périmètre de cette revue. Cela qualifie les branches synthétiques de collecte et de conservation des preuves ; aucune évaluation des deux candidats C n'a été exécutée par ce relecteur.

| Fichier | SHA-256 |
|---|---|
| `.tmp/wo062_evaluate.py`, B lu puis revérifié intact | `17640bd78caf18abb834f72a1610c0bc613ce4bc14645a29a1d5f7e3311d000d` |
| `.tmp/wo062_evaluate_c.py`, version initialement revue | `6740545ed509780f187fa468016a6068b90b0483bfa4cc1532e5744a270d6f06` |
| `.tmp/wo062_evaluate_c.py`, version corrigée et testée | `8a72b9b69d452bdcc545cc85ce4931beef5a58a41625eba3e12d9c240f44425f` |
| `.tmp/test_c_harness.py` | `62410fea11ea79632e6633c5f81b063ffb59677d2c3369bdff037b3e316c765b` |

## Défauts concrets et résolution

### C-HARNESS-01 — mode écriture insuffisamment borné — corrigé

La version initiale, lignes 90–91, choisissait `workspace-write` pour toute entrée portant `execution_mode=bounded_local_runtime`. Un cas Java ou de sélection mal déclaré obtenait donc ce mode. `runtime_execution_for`, appelé par la préparation et par l'exécution, accepte maintenant ce mode uniquement pour le skill `ss-windows-runtime`, les identifiants `WR-H01`/`WR-N01` et une nature autre que sélection. Les modes inconnus échouent. Les cas Java et de sélection restent en `read-only`.

Preuve : cinq combinaisons invalides refusées, WR-H01 et WR-N01 acceptés, arguments réellement reçus par le processus simulé contrôlés pour Java et sélection.

### C-HARNESS-02 — perte du diagnostic figé après expiration ou journal invalide — corrigé

Dans la version initiale, `TimeoutExpired` à la ligne 97 ou `JSONDecodeError` à la ligne 99 quittait la fonction avant `trace.json` et `freeze.json`. `events.jsonl` subsistait et interdisait un nouvel essai dans le même dossier, sans dossier de revue complet.

La copie C conserve maintenant les événements valides, un diagnostic de décodage/parsing avec numéro de ligne, la trace et le manifeste après échec. L'expiration garde `exit_code=null`, `timed_out=true`, un état de nettoyage non vérifié et `automatic_retry=false`. La limite de l'appel natif reste 900 secondes. Aucun nettoyage ni nouvelle tentative n'est déclenché. La reconstruction locale du catalogue est omise après expiration. Les événements bruts restent dans la sortie locale ; les événements sans éléments de raisonnement et le stderr sont figés.

Preuve : expiration simulée, JSON invalide et événement de mauvaise forme conservent une trace et un manifeste cohérents ; une nouvelle tentative dans le même dossier est refusée sans modifier le manifeste existant.

### C-HARNESS-03 — assertion postexécution supprimant le gel — corrigé

La version initiale vérifiait les sources par assertion à la ligne 111, puis le suffixe et l'encodage des artefacts aux lignes 125–126. Une violation arrêtait la collecte avant le manifeste final ; les fichiers déjà copiés dans `frozen` pouvaient être incomplets.

Les mutations ou absences d'entrées deviennent des diagnostics. La requête et le prompt sont maintenant aussi revérifiés. Les artefacts textuels admissibles de `output/runtime-artifacts` sont figés récursivement avec leurs empreintes ; un artefact refusé garde un inventaire diagnostique et reste explicitement `frozen=false`. Les liens sortant du dossier du cas sont refusés avant lecture de contenu. Les erreurs de copie ont leur diagnostic. Un dossier `runtime-artifacts` absent dans un cas Windows exécutant est signalé.

Preuve : changement de source, changement de requête, UTF-8 invalide, suffixe refusé et absence d'artefacts produisent un résultat collecteur non nul avec gel exploitable. Le cas normal conserve `runtime-artifacts/result.json`, son nombre d'octets et son SHA-256.

### C-HARNESS-04 — code natif zéro sans session complète — corrigé

La version initiale retournait uniquement `completed.returncode` à la ligne 130, même avec `turn_completed=false` ou une réponse absente. Le regroupement des futures pouvait donc retourner zéro pour une collecte incomplète.

`exit_code` conserve maintenant la valeur native. `collector_exit_code` vaut zéro seulement avec un code natif zéro, un événement final `turn.completed`, une réponse non vide et aucune erreur de collecte. Le code natif non nul est conservé. `qualification_status=NOT_REVIEWED` distingue explicitement cette collecte du verdict métier de la revue indépendante.

Preuve : événement de fin absent, réponse absente et réponse vide échouent malgré le zéro natif ; un code natif 7 reste 7.

## Points contrôlés sans nouvelle évaluation

- **Séparation oracle/entrées :** le lanceur copie les sources désignées par `source_ids`, les seuls `input_files` du cas et son `input_case`. La vérification du manifeste hache les fichiers préparatoires sans transmettre leur contenu au modèle. Oracle, plan et inventaire ne sont pas copiés globalement. Cette propriété repose sur la liste préparatoire revue ; elle ne constitue pas une attestation des préparations C encore en cours lors de cette revue.
- **Collecte native et candidat :** les arguments `exec --ephemeral`, l'absence d'override modèle/effort, les empreintes exactes du candidat et des fichiers copiés, ainsi que les vérifications avant/après sont conservés. Les commandes et sorties natives restent dans `native-events.json` après exclusion du raisonnement. La lecture intégrale du candidat exact et la pertinence de sa sélection restent à établir sur chaque future trace réelle, par la revue indépendante.
- **Catalogue :** le texte reconstruit après exécution conserve son étiquette de reconstruction locale ; aucune capture de requête modèle ou injection automatique n'est revendiquée.
- **Windows :** la sonde d'identité hôte reste distincte de la reproduction réellement effectuée par le cas ; les preuves de durée, codes et nettoyage doivent provenir de la future session Windows. Les données utilisées dans les tests de cette revue sont entièrement synthétiques.
- **Anciennes preuves :** le lanceur B garde son SHA initial. Le contrôle Git ciblé sur PB, FQ, CS et SKL-002 ne rapporte aucune modification. Les seules écritures de ce relecteur sont sous `.tmp` ; les modifications concurrentes de préparation C et `.gitattributes` appartiennent aux autres travaux, sans intervention de ce relecteur.

## Vérification exécutée

Commande : Python fourni par le runtime local, exécutant `.tmp/test_c_harness.py` depuis le worktree WO-062. Code de sortie : **0**. Résultat : **17 tests, 0 échec, 0 erreur**. Tous les appels de sous-processus du lanceur sont remplacés par des doubles locaux ; `os.environ` est remplacé par un dictionnaire vide. Aucun modèle, CLI Codex, PowerShell enfant, Java, Maven, Docker, navigateur, fournisseur ou API n'a été lancé par ces tests.

Chaque test vérifie les SHA-256 et tailles du manifeste figé produit. Les données synthétiques sont conservées sous `.tmp/c-harness-synthetic/b72fc2cc-d974-464c-92d2-ef85df3baf8c`. Résumé machine : `.tmp/c-harness-synthetic-results.json`.

`git diff --check` : code 0. Contrôle Git ciblé anciennes preuves et candidats PB/FQ/CS/SKL-002 : sortie vide. Aucun test applicatif n'a été lancé : le périmètre confié porte sur le lanceur temporaire, sans modification applicative ou de fichier suivi par ce relecteur.

Les fichiers finaux de cette intervention sont `.tmp/wo062_evaluate_c.py`, `.tmp/test_c_harness.py`, `.tmp/c-harness-synthetic-results.json`, `.tmp/c-harness-review.md` et `.tmp/c-harness-review.json`, avec les sorties synthétiques conservées. Aucune installation, publication, fusion ou validation humaine de candidat n'est revendiquée.
