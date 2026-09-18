# WO-062 — Validation humaine et installation personnelle de ss-provider-benchmark

- **Date :** 15 septembre 2026.
- **État :** `QUALIFIED_OWNER_VALIDATED_PERSONALLY_INSTALLED`.
- **Version acceptée et installée :** `0.1.0-candidate.1`, sans changement de contenu ni de version.
- **Périmètre :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.
- **Base de cette étape :** `f349f7292a4c3cadd4edef1478abc7a0388318f6`, branche WO-062 existante.

## Décision propriétaire

Le propriétaire a explicitement accepté le contenu, le périmètre et les limites documentées
de `ss-provider-benchmark 0.1.0-candidate.1`, puis autorisé sa validation humaine et son passage
à l'installation personnelle. La [décision enregistrée](../skills/evaluations/WO-062/ss-provider-benchmark/human-validation.json)
conserve le texte exact de cette autorisation et les empreintes des deux fichiers concernés.

Cette acceptation porte sur le candidat déjà qualifié : **12 cas distincts PASS**, avec le
complément instrumenté PB-S01. Elle conserve les limites de mesure, de comparabilité et de
preuve d'injection automatique documentées dans la [qualification technique](WO062-PROVIDER-BENCHMARK-PBS01-QUALIFICATION-20260915.md).
Le suffixe `candidate.1` est conservé pour installer exactement l'artefact accepté.

## Installation réalisée

| Fichier | Taille | SHA-256 source et copie personnelle |
|---|---:|---|
| SKILL.md | 6 835 octets | `4bcecee4dbe75ec74530a7bda63acc8cbc84cc15dcd7c07e2ab9e6f80cc7be94` |
| agents/openai.yaml | 322 octets | `707b19c6ff8b73d6a5962244ef2855e163dc3c55b8f27ea14ef2e9863ac66bdd` |

Destination : `C:/Users/geoff/.agents/skills/ss-provider-benchmark`.
Les tailles exactes et les chemins installés figurent dans le
[résultat d'installation](../skills/evaluations/WO-062/ss-provider-benchmark/installation/result.json).
Le contrôle a constaté deux fichiers copiés, puis deux fichiers conformes en mode `-VerifyOnly`.
Les dix fichiers personnels du lot 1 ont conservé leur SHA-256 et leur date de modification.

La [découverte locale](../skills/evaluations/WO-062/ss-provider-benchmark/installation/discovery.json)
par Codex CLI `0.154.0-alpha.6.2` retrouve une seule entrée `ss-provider-benchmark`, résolue vers
ce dossier personnel, avec l'empreinte attendue. Ce contrôle utilise `codex debug prompt-input`
sous le compte de l'utilisateur : aucun modèle n'est appelé. Il atteste le catalogue local,
pas un nouveau test de sélection ni l'injection automatique du corps du skill.

La portée personnelle suit la [documentation officielle Codex](https://learn.chatgpt.com/docs/build-skills) :
le dossier utilisateur `.agents/skills` est découvert à travers les dépôts. Le skill devient
disponible dès le prochain tour ; si le catalogue affiché demeure ancien, actualiser la tâche
ou redémarrer Codex. Ses règles ciblent toujours le Local Lab.

## Adaptation ciblée de l'installateur

L'[installateur](../../scripts/Install-LocalLabSkills.ps1) conserve le paquet `Lot1` par défaut.
La nouvelle option explicite `-Package ProviderBenchmark` sélectionne seulement les deux
chemins exacts du [manifeste approuvé](../skills/evaluations/WO-062/ss-provider-benchmark/installation-manifest.json).
Les booléens d'acceptation et la version exacte sont exigés pour ce paquet.

Les protections existantes restent applicables : préflight intégral, taille/SHA-256,
casse exacte des chemins relatifs, refus des variantes locales, fichiers supplémentaires
et liens, copie sans écrasement, réexécution sans réécriture et vérification sans création.
La consolidation des dix skills reste à réaliser à l'étape D du WO ; ce paquet n'anticipe
aucun des quatre skills encore à développer. SKL-002 reste intact.

## Commandes et contrôles exécutés

| Contrôle | Résultat |
|---|---|
| `Test-LocalLabSkillsInstallation.ps1` | PASS, 7 cas du lot 1 en destination isolée. |
| Même commande avec `-Package ProviderBenchmark` | PASS, 11 cas en destination isolée. |
| Validateur officiel `skill-creator/scripts/quick_validate.py` | PASS, candidat exact ; format seulement. |
| `Install-LocalLabSkills.ps1 -Package ProviderBenchmark -Destination C:/Users/geoff/.agents/skills` | PASS, 2 fichiers contrôlés et copiés. |
| Même commande avec `-VerifyOnly` | PASS, 2 fichiers contrôlés, 0 copie. |
| `codex debug prompt-input` sous le compte utilisateur | PASS, une entrée USER et chemin exact. |
| Comparaison source/destination et lot 1 avant/après | PASS, deux fichiers installés conformes ; dix fichiers existants sans réécriture. |

Les scripts PowerShell ont été exécutés avec `powershell.exe -NoProfile -ExecutionPolicy Bypass -File`.
Les [journaux lot 1](../skills/evaluations/WO-062/ss-provider-benchmark/installation/tests-lot1.txt)
et [nouveau paquet](../skills/evaluations/WO-062/ss-provider-benchmark/installation/tests-provider.txt)
conservent les résultats. Les cas couvrent destination neuve avec espaces, idempotence,
vérification sans écriture, conflit tardif, casse locale différente, fichier supplémentaire,
source altérée, coexistence avec le lot 1, reprise partielle, approbation absente et chemin
de manifeste hors périmètre. Les sources testées sont reliées à leur hash et objet Git.

Deux particularités d'environnement ont été résolues : le premier appel au validateur ne
trouvait pas PyYAML ; sa relance a utilisé la dépendance isolée déjà préparée sous ce WO,
sans installation de bibliothèque personnelle. Le premier contrôle de découverte utilisait
le profil `CodexSandboxOffline`, qui ne contient pas les skills de l'utilisateur ; la preuve
retenue provient du compte `geoff`. Aucun de ces essais initiaux n'est compté comme un PASS.

La [revue indépendante](../skills/evaluations/WO-062/ss-provider-benchmark/installation/review.json)
est favorable, sans anomalie matérielle dans les deux scripts et les deux documents de décision/manifeste.
Elle est statique ; les exécutions ci-dessus ont été réalisées par l'agent principal.
Les [contrôles de livraison](../skills/evaluations/WO-062/ss-provider-benchmark/installation/checks.json)
et le [manifeste de preuves](../skills/evaluations/WO-062/ss-provider-benchmark/installation/artifact-manifest.json)
complètent cette traçabilité.

La suite Maven standard déjà exécutée sous WO-062 reste attachée au même code applicatif :
2 435 tests exécutés, zéro échec/erreur, cinq ignorés, fin `2026-09-15T15:58:40Z` ; voir la
[preuve de cadrage](WO062-SKILLS-LOT2-SCOPING-20260915.md). Elle n'est pas relancée pour cette
évolution de l'installateur, contrôlée par ses essais PowerShell ciblés. Aucun code applicatif,
test standard, migration, profil réseau ou workflow CI n'a changé dans cet incrément.

## État de livraison et suite

La [qualification courante](../skills/evaluations/WO-062/ss-provider-benchmark/qualification.json)
porte désormais la validation humaine et l'installation personnelle vérifiée. Les preuves
figées A1, run-01 et run-02 restent inchangées, y compris le BLOCKED historique de PB-S01/run-01.

Fichiers modifiés : les deux scripts d'installation/recette, la règle ciblée `.gitattributes`, la qualification courante,
les deux README, CHANGELOG, WO-062 et le renvoi daté depuis le rapport PB-S01. Fichiers ajoutés :
la décision propriétaire, le manifeste ciblé, les preuves d'installation et le présent rapport.

Le [WO-062](../work_orders/active/WO-SS-20260915-062-skills-lot2.md) reste actif. La suite prévue
est `ss-football-quality`, puis `ss-ci-security`, suivis de `ss-java-module` et `ss-windows-runtime`.
La consolidation des dix skills et la livraison Git du WO restent distinctes de cette
installation personnelle. Aucun push, PR, fusion ou tag n'a été réalisé dans cette étape.
