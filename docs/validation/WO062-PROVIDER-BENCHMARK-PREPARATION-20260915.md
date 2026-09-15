# WO-062 — Préparation A1 de ss-provider-benchmark

## Autorité, portée et résultat

Le 15 septembre 2026, le propriétaire confirme « J’accepte le WO-062 » puis autorise
l'inventaire des sources et la préparation des cas d'évaluation de `ss-provider-benchmark`.
Le [WO accepté](../work_orders/active/WO-SS-20260915-062-skills-lot2.md) passe à
`OWNER_ACCEPTED — A1_PREPARED`.

La préparation livre un [inventaire de 38 sources](../skills/evaluations/WO-062/ss-provider-benchmark/source-inventory.md),
un [plan de 12 cas](../skills/evaluations/WO-062/ss-provider-benchmark/evaluation-plan.md),
les prompts et entrées synthétiques, un oracle séparé et un manifeste de provenance.
Les deux tâches métier, six contre-épreuves et quatre cas de sélection restent tous `NOT_RUN`.
Le skill, ses métadonnées UI, son installation et ses évaluations ne sont pas réalisés à cette étape.

`EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY` sont conservés.

## Reprise et sources

- Worktree : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2`.
- Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`, cible `feature/V0.1.0-RC01`.
- Base inchangée : `74d3f38afd64ce587353fbd645cad7a388e9756c`.
- À la reprise, les cinq fichiers du cadrage étaient les seuls changements locaux et n'étaient
  pas commités. Ils ont été reconnus avant les ajouts de cette phase ; aucune modification
  applicative d'un autre intervenant n'a été constatée.
- Les deux sous-agents ont inspecté les sources dans le checkout du train propre au même SHA,
  en lecture seule. Leurs revues du paquet candidat sont des revues de préparation, pas des
  exécutions indépendantes d'un skill déjà construit.
- Skills appliqués : `ss-work-order`, `skill-creator` pour les cas sans fuite d'oracle,
  `ss-verify` pour proportionner les contrôles et attribuer les preuves.

Le manifeste conserve l'identifiant Git de chaque source et le SHA-256 de ses octets locaux.
Les cas sont des tableaux pédagogiques minimisés : aucun payload fournisseur, export privé,
base opérateur ou manifeste de campagne à exécuter n'a été créé ou lu pour cette préparation.

## Revue et corrections avant gel

La revue arithmétique a demandé deux précisions : donner explicitement le même endpoint
`SCHEDULED_EVENTS` aux réponses historiques de PB-C01 pour justifier leur distribution commune,
et ajouter le lecteur SQL I07 à l'allowlist PB-C05 pour vérifier fenêtre et cutoff. Les deux
corrections sont intégrées avant le gel.

La revue de la tâche nouvelle a fait distinguer les observations historiques V2 de H05 et
le changement source V3 décrit dans le même document. Elles ne sont plus présentées comme
une qualification réelle de V3. PB-N01 dispose de ses 19 sources autorisées, dont le résumé
temporel H08, et peut produire une analyse sans rechercher les 937 tentatives privées.

Les deux relecteurs ont vérifié les deltas corrigés et levé leurs findings. Aucun défaut
matériel restant n'a été relevé dans leurs périmètres de revue de la préparation ; aucune
évaluation du skill n'a été exécutée pour produire ces conclusions.

Trois nuances des sources sont conservées pour les futurs essais : borne de longueur brute
avant trim, heure du dernier état canonique direct pour le délai J6, et projection de hash
ordonnée par `TreeSet`. Aucun contrat, Java, SQL ou runbook source n'est modifié pour les résoudre.

## Vérifications effectuées

Les contrôles portent sur la préparation et les fichiers documentaires. La version précédente
du cadrage, son rapport et ses résultats restent conservés comme preuve de cette étape antérieure.

| Contrôle | Résultat |
|---|---|
| Résolution des 38 sources, `git rev-parse HEAD:<chemin>`, absence de diff source et empreintes SHA-256 | PASS ; chaque source est versionnée sur la base annoncée et intacte. |
| JSON des cas et entrées ; références des sources et identifiants | PASS ; 12 cas uniques, six entrées synthétiques, références résolues, tous les verdicts `NOT_RUN`. |
| Revue des six contre-épreuves et de leurs calculs | PASS après les corrections ci-dessus ; cette revue ne simule pas un verdict du futur skill. |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .tmp/Verify-WO062Preparation.ps1` | PASS ; 12 documents du delta global WO-062, 366 liens locaux valides, UTF-8 strict, JSON lisibles, onze motifs de contenu sensible sans signal, arithmétique ciblée cohérente. |
| `git diff --check` et contrôle des chemins modifiés/non suivis | PASS ; périmètre documentaire exact, aucun fichier runtime ou du lot 1 modifié. |
| Gel des cinq fichiers de préparation dans le manifeste | PASS ; tailles et SHA-256 enregistrés puis revérifiés pour inventaire, plan, oracle, prompts et entrées. |
| Absence de candidat et d'exécution | `docs/skills/local-lab/ss-provider-benchmark` absent ; aucun skill installé ou essai comportemental réalisé. |

La vérification standard déjà exécutée sur ce même code sous WO-062 reste celle du
[cadrage](WO062-SKILLS-LOT2-SCOPING-20260915.md) : `Verify-Local.ps1`, Maven
`-DskipITs clean verify`, 2 440 cas recensés, 2 435 exécutés, zéro échec/erreur, cinq ignorés,
fin `2026-09-15T15:58:40Z`. Elle n'est pas relancée pour cet ajout de documents et de données
d'évaluation non exécutables ; aucun code, test, profil, configuration ou migration n'a changé.
Les XML restent une preuve du code de la base, pas une qualification des futurs skills.
L'intégration PostgreSQL n'est pas requise pour ce delta ; aucune CI distante n'est revendiquée.

## Fichiers de cette étape

Nouveaux fichiers dans `docs/skills/evaluations/WO-062/ss-provider-benchmark/` :

- `source-inventory.md` : sources classées et limites actuelles/historiques.
- `manifest.json` : provenance, empreintes des sources et fichiers d'évaluation ; usage préparation uniquement.
- `evaluation-plan.md` : tâches, protocole d'exécution future, traces et critères de réception.
- `cases.json` : douze prompts, sources autorisées et état `NOT_RUN`.
- `inputs.json` : six jeux pédagogiques synthétiques sans payload fournisseur.
- `oracle.md` : assertions réservées à la revue.

S'ajoutent la présente preuve et les mises à jour de `WO-SS-20260915-062-skills-lot2.md`,
`README.md`, `docs/skills/README.md` et `CHANGELOG.md`. Le rapport de cadrage antérieur demeure
intact. Le worktree du train reste propre. Les fichiers sont locaux, sans nouveau commit,
publication, PR, installation personnelle ou clôture du WO.
