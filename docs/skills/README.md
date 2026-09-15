# Skills du SofaScore Local Lab

Les cinq skills du lot 1 ont été validés par le propriétaire le 5 septembre 2026 pour le Betting Project et le Local Lab. Leur livraison sur les deux branches `main` est autorisée. Ce dépôt contient le paquet autonome des adaptations `ss-*` ; son utilisation ne nécessite aucun checkout du Betting Project.

| Skill | Usage |
|---|---|
| [ss-work-order](local-lab/ss-work-order/SKILL.md) | Reprendre un Work Order dans le bon worktree, retrouver décisions et preuves. |
| [ss-verify](local-lab/ss-verify/SKILL.md) | Choisir les commandes Windows/Linux pertinentes et diagnostiquer les faux verts. |
| [ss-postgres-change](local-lab/ss-postgres-change/SKILL.md) | Relier migrations, transactions, concurrence, ledgers et preuves PostgreSQL. |
| [ss-data-contract-replay](local-lab/ss-data-contract-replay/SKILL.md) | Préserver provenance, snapshots, complétude, contrats et validation humaine. |
| [ss-review-closeout](local-lab/ss-review-closeout/SKILL.md) | Relier revue, qualification, décisions, clôture et état Git courant. |

## Lot 2 — développement cadré, skills à réaliser

Le [WO-062](../work_orders/active/WO-SS-20260915-062-skills-lot2.md), préparé le 15 septembre 2026,
adapte au Local Lab les rôles de la conversation « Skills du lot 2 » dans l'ordre demandé :

| Étape | Skill prévu | Rôle |
|---|---|---|
| A | `ss-provider-benchmark` | Protocole reproductible, populations et dénominateurs, coût et limites des mesures. |
| B | `ss-football-quality` | Cohérence sémantique des données football, anomalies et corrections tardives. |
| B | `ss-ci-security` | Diagnostic CI et preuve des contrôles réellement applicables au candidat. |
| C | `ss-java-module` | Placement des responsabilités et dépendances dans les couches Java du Lab. |
| C | `ss-windows-runtime` | Reproduction et diagnostic dans le runtime Windows concerné. |

Le propriétaire a accepté WO-062 et autorisé la préparation de `ss-provider-benchmark`.
Son [inventaire de 38 sources](evaluations/WO-062/ss-provider-benchmark/source-inventory.md)
et ses [12 cas d'évaluation](evaluations/WO-062/ss-provider-benchmark/evaluation-plan.md)
ont été figés avec un oracle séparé avant rédaction. Le [candidat](local-lab/ss-provider-benchmark/SKILL.md)
est désormais évalué : douze cas exécutés, 11 PASS ; PB-S01 conserve une preuve de chargement
explicite insuffisante malgré un routage correct. Le [protocole et la scorecard prematch/live](evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-N01/response.md)
sont produits. Voir les [résultats et limites A2](../validation/WO062-PROVIDER-BENCHMARK-CANDIDATE-20260915.md).
La [préparation A1](../validation/WO062-PROVIDER-BENCHMARK-PREPARATION-20260915.md) demeure intacte,
avec son état historique `NOT_RUN`. Le candidat attend la preuve manquante puis la validation humaine.

Chaque skill devra passer un cas historique connu, une tâche nouvelle et des tests de sélection,
puis une revue. Les sources du Lab font autorité : J8 ne dispose pas de contrôle externe intégré,
et la CI suit notamment l'allègement adopté dans WO-061. Le WO distingue les
[preuves du cadrage](../validation/WO062-SKILLS-LOT2-SCOPING-20260915.md) de cette qualification future.

Le paquet et l'installateur ci-dessous livrent toujours **les cinq skills du lot 1**. WO-062
prévoit un nouveau manifeste et une évolution qualifiée de l'installateur pour les dix skills,
avec préservation du contenu approuvé et des preuves SKL-002. Seul `ss-provider-benchmark` est
rédigé comme candidat A2 ; il n'est pas couvert par l'installateur actuel ni installé personnellement.
Les quatre autres skills du lot 2 restent à développer.

## Installation personnelle depuis ce dépôt

Dans PowerShell sous Windows, à la racine du checkout :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -VerifyOnly
```

La destination par défaut est `%USERPROFILE%/.agents/skills`, portée utilisateur documentée par [Codex](https://learn.chatgpt.com/docs/build-skills). Les cinq skills sont alors disponibles dans les worktrees actuels et futurs sur ce compte. Les descriptions ciblent le SofaScore Local Lab et les références sont résolues depuis la racine Git du worktree concerné. Actualiser la tâche ou redémarrer Codex si son catalogue est ancien.

Le paquet reste dans `docs/skills/local-lab` : ne pas en créer une seconde copie homonyme dans `.agents/skills` du dépôt lorsque l'installation utilisateur existe. Codex peut découvrir plusieurs skills de même nom sans les fusionner. Les skills `bp-*` demeurent propres au Betting Project.

L'installateur contrôle les dix sources par taille et SHA-256 avant toute copie, puis tous les fichiers de destination existants. Il ne remplace aucun fichier différent et refuse les fichiers supplémentaires dans un dossier `ss-*`, afin de préserver une variante locale. La casse des chemins relatifs approuvés est exacte : un fichier `skill.md` ne remplace pas `SKILL.md`. Une casse différente du préfixe absolu de destination reste acceptée lorsque Windows résout le même dossier. Réconcilier ou sauvegarder cette variante explicitement avant de réinstaller ; aucun mode d'écrasement forcé n'est fourni. Une installation identique ne réécrit pas les fichiers. `-VerifyOnly` vérifie aussi la présence complète sans créer de répertoire. Les chemins liés sont refusés. Une erreur d'entrée/sortie peut laisser une installation partielle ; les fichiers déjà copiés sont contrôlés lors d'une nouvelle exécution.

`-Destination '<dossier de skills>'` permet une installation isolée. Pour qualifier l'installateur sans toucher au compte utilisateur :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1
```

Ce contrôle crée un dossier temporaire neuf avec les seuls fichiers synthétiques et copies du paquet ; il le conserve pour inspection. Il ne lance ni application, ni Maven, ni Docker.

## Utilisation

```text
Utilise $ss-work-order pour reprendre le WO concerné et établir son état actuel.
Utilise $ss-verify pour diagnostiquer cet échec CI et réaliser le correctif.
Utilise $ss-postgres-change et $ss-data-contract-replay pour préparer cette évolution du ledger et de son contrat.
Utilise $ss-review-closeout pour vérifier les preuves et clôturer ce lot.
```

Chaque dossier contient un `SKILL.md` et `agents/openai.yaml`. La sélection implicite reste disponible ; aucun outil MCP supplémentaire n'est requis. Les skills guident les vérifications et ne remplacent pas les preuves d'exécution.

## Provenance et qualification

Les dix fichiers sont les adaptations version 0.1 évaluées sous SKL-002, sans modification de leur contenu. Le [manifeste historique](evaluations/SKL-002/installation-manifest.json) fixe leurs empreintes. Les [deux essais analytiques et leur revue](evaluations/SKL-002/review.md) et la [découverte Codex](evaluations/SKL-002/discovery.json) conservent leurs octets d'origine. Les chemins personnels et états Git qu'ils contiennent sont des observations historiques du 5 septembre 2026 ; les liens du présent guide sont les références portables du paquet livré.

Les adaptations proviennent des cinq skills `bp-*` validés sous SKL-001, dans l'ordre du tableau : work-order, verify, postgres-change, data-contract-replay, review-closeout. Elles appliquent les conventions du Lab : WO `active`/`completed`, ADR-SS, profil Maven `integration-tests`, lanceurs PowerShell, ledgers et états des campagnes. Aucun composant applicatif commun n'est créé entre les dépôts.

Les essais historiques ont mesuré 77 et 99 secondes pour reprendre le contexte de WO-051 et WO-049. Aucun défaut nécessitant correction, faux vert déclaratif ou incohérence documentaire n'a été relevé dans ces deux réponses. Il n'y avait aucun témoin A/B et aucun test applicatif exécuté pendant ces essais : les gains généraux et répétitions réelles restent non mesurés.

Le [rapport WO-054](../validation/WO054-SKILLS-DELIVERY-20260905.md) distingue ces preuves historiques des contrôles de la livraison actuelle. Le [Work Order](../work_orders/completed/WO-SS-20260905-054-skills-lot1.md) consigne la validation propriétaire et l'autorisation de livraison. La PR et ses checks constituent le suivi distant de publication/fusion ; aucune ancienne qualification ne vaut CI du candidat courant.
