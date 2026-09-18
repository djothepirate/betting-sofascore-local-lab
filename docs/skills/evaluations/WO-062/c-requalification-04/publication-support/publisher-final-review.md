# WO-062 — Revue finale du correctif de publication C4

**Verdict : PASS — les trois P2 précédents sont résolus dans le périmètre demandé.** Cette conclusion porte sur le publieur exact et les preuves synthétiques fournies ; elle ne constitue ni une publication réelle ni une nouvelle qualification des skills.

## Identité examinée

- HEAD : `cb67d18c66aaa3989bfc6121ed7b51945fd95f9f`, état Git propre.
- Publieur : `.tmp/publish_c4.py`.
- SHA256 du publieur : `702819539f583435082609adf703e87f38a18c40aa7dcd38640bc5e872a49985`.
- Helper réutilisé : `.tmp/publish_c_run02.py`, SHA256 `e1596d4c0731d1e71a521deb173d532aa6f44c14c9da25164e1e2013a871f5cd`.
- Tests : `.tmp/test_c4_publication.py`, SHA256 `658e5a0c2064d18891a32ad52b32d5827f003150cf334a782e6e7e8b9672d028`.
- Résultats des tests : `.tmp/c4-publication-tests.json`, SHA256 `336ba5c11cb967e10f0697637b9ef455d36398b838c6386bd299f060968419bd`.
- Relecteur : `/root/jm_counter_review`, 16 septembre 2026.

## Résolution des trois remarques

| Remarque | Conclusion | Preuve | Limite |
|---|---|---|---|
| P2-review-freeze-run-binding | RESOLVED | publish_c4.py:76–78 conserve verify_case/verify_review et exige run_id=run-04, skill courant, candidate_version exacte et freeze_sha256 obligatoire égal au gel. Le helper impose déjà case_id, candidate_sha256 et hash de réponse. | Ce renforcement vise chaque nouvelle revue comportementale C4. Les anciennes revues C/S restent inchangées et sont liées à leur gel par l'inventaire historique vérifié, les résultats et les hashes de réponse/candidat. |
| P2-windows-carry-and-count-contract | RESOLVED | publish_c4.py:85–100 impose exactement WR-C01/WR-C02/WR-S01..S04, source_run=run-01, PASS commun aux lignes d'agrégat/résultat/revue, hash de réponse, candidat identique et chemins résolus vers run-01. Les manifests historiques sont vérifiés et leurs hashes rapprochés lignes60–66. Ligne102 impose total25 Java/13 Windows et8 nouvelles exécutions Java. | Contrôles relus statiquement ; les quatre tests synthétiques fournis ne sont pas des tests négatifs des règles d'identité ou de reprise Windows. |
| P2-partial-publication | RESOLVED | publish_validated:14–36 vérifie les entrées avant et après staging, écrit tous les fichiers dans un répertoire UUID, vérifie le manifest staged, prépare une qualification séparée, renomme le run complet puis os.replace la qualification. Une exception de replace ramène dest vers son stage et remonte l'échec. | Garantie par skill pour les erreurs interceptées ; ce n'est pas une transaction unique run+qualification ni une transaction entre les deux skills. Arrêt brutal du processus et échec du rollback ne sont pas couverts par les quatre tests. |

### Liaison obligatoire des nouvelles revues

Les lignes77–78 ajoutent les quatre gardes manquants après la validation existante de cas/candidat/réponse. Une revue d'un autre run, skill, version, ou sans empreinte exacte du gel, échoue avant staging. Le cas d'une ancienne revue Windows sans réponse ne peut plus être repris comme revue C4 sur la seule identité du cas.

Les six revues historiques restent des preuves immuables. Certaines ne portent pas le nouveau champ obligatoire du run courant ; leur chaîne historique est contrôlée par les manifests, les résultats et les liaisons de réponse/candidat. Elles ne sont pas réécrites.

### Reprise Windows et compteurs

Le jeu repris est précisément `WR-C01`, `WR-C02`, `WR-S01`, `WR-S02`, `WR-S03` et `WR-S04`. Le code vérifie leur statut PASS, leur provenance run-01, leur réponse, leur revue et les chemins correspondants. Les deux cas H/N sont remplacés par les nouvelles revues run-04. Huit identités distinctes sont ensuite exigées.

Les totaux sont désormais des préconditions : **25 tentatives Java toutes versions, dont huit nouvelles pour .3 ; 13 tentatives Windows**. Ils ne sont plus seulement le résultat opportun d'une addition d'historiques.

### Staging et remplacement

Les fichiers sont écrits dans un staging confiné au workspace. Leur manifest est vérifié avant promotion. Les empreintes de toutes les entrées lues sont contrôlées une seconde fois avant le renommage. La qualification est préparée séparément, puis remplacée par `os.replace`.

Si ce remplacement échoue, le nouveau répertoire est ramené en staging et l'exception remonte. Les anciens runs restent en lecture seule. Les stages laissés après un échec conservent les preuves et ne sont pas des runs publiés.

## Quatre tests fournis et artefacts constatés

Le script de tests extrait uniquement la fonction de publication par AST et l'exerce dans quatre workspaces synthétiques. Son rapport lie les hashes exacts du publieur et du test. Les artefacts sur disque ont été lus et sont cohérents avec les résultats déclarés :

| Test | Résultat fourni | État constaté |
|---|---|---|
| `normal` | PASS | `docs/run-04` contient les octets de réponse attendus ; qualification `NEW`. |
| `atomic_replace_failure` | PASS, `PermissionError` | Run final absent, qualification `OLD` intacte, preuves et qualification nouvelle conservées en staging. |
| `manifest_mismatch` | PASS, `EvidenceError` | Run final absent, qualification `OLD` intacte ; manifest erroné resté au staging. |
| `input_drift` | PASS, `AssertionError` | Run final absent ; qualification modifiée de la fixture conservée exactement, refus avant staging. |

Ces tests portent sur la publication locale synthétique et ses erreurs. Ils n'établissent pas la validité de toutes les revues de cas, l'ensemble du traitement historique, ni une exécution réelle de `--publish`.

## Portée et limites

- Remplacement atomique du fichier de qualification et rollback par skill sur les exceptions interceptées. Le renommage du répertoire et le remplacement du JSON restent deux opérations ; aucune atomicité globale entre les deux skills n'est revendiquée.
- Les preuves fournies ne testent ni l'arrêt brutal du processus entre ces opérations, ni une défaillance du rollback.
- Les contrôles C4 utilisent `assert` et supposent une exécution Python normale, sans suppression des assertions.
- Aucun import ou lancement du publieur, aucun test rejoué, modèle ou probe lancé pendant cette revue.
- Aucun candidat, source, ancienne revue ou fichier suivi modifié. Seuls les deux rapports finaux sous `.tmp` ont été écrits.
