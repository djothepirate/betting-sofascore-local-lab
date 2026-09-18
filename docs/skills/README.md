# Skills du SofaScore Local Lab

Les cinq skills du lot 1 ont été validés par le propriétaire le 5 septembre 2026 pour le Betting Project et le Local Lab. Leur livraison sur les deux branches `main` est autorisée. Ce dépôt contient le paquet autonome des adaptations `ss-*` ; son utilisation ne nécessite aucun checkout du Betting Project.

| Skill | Usage |
|---|---|
| [ss-work-order](local-lab/ss-work-order/SKILL.md) | Reprendre un Work Order dans le bon worktree, retrouver décisions et preuves. |
| [ss-verify](local-lab/ss-verify/SKILL.md) | Choisir les commandes Windows/Linux pertinentes et diagnostiquer les faux verts. |
| [ss-postgres-change](local-lab/ss-postgres-change/SKILL.md) | Relier migrations, transactions, concurrence, ledgers et preuves PostgreSQL. |
| [ss-data-contract-replay](local-lab/ss-data-contract-replay/SKILL.md) | Préserver provenance, snapshots, complétude, contrats et validation humaine. |
| [ss-review-closeout](local-lab/ss-review-closeout/SKILL.md) | Relier revue, qualification, décisions, clôture et état Git courant. |

## Lot 2 — cinq skills qualifiés, validés humainement et installés personnellement

Le [WO-062](../work_orders/active/WO-SS-20260915-062-skills-lot2.md), préparé le 15 septembre 2026,
adapte au Local Lab les rôles de la conversation « Skills du lot 2 » dans l’ordre demandé.

| Étape | Skill | Version livrée | Qualification et état courant |
|---|---|---|---|
| A | `ss-provider-benchmark` | `0.1.0-candidate.1` | 12 cas PASS ; validé humainement et installé. |
| B | `ss-football-quality` | `0.1.0-candidate.1` | 8 cas PASS ; validé humainement et installé. |
| B | `ss-ci-security` | `0.1.0-candidate.1` | 8 cas PASS ; validé humainement et installé. |
| C | `ss-java-module` | `0.1.0-candidate.5` | Campagne C8 : 8 PASS / 0 FAIL / 0 BLOCKED ; validé humainement et installé. |
| C | `ss-windows-runtime` | `0.1.0-candidate.1` | 8 PASS / 0 FAIL / 0 BLOCKED ; validé humainement, installé et usage personnel vérifié. |

Les qualifications, validations humaines et manifestes d’installation sont versionnés sous
[`evaluations/WO-062`](evaluations/WO-062). La [matrice finale portable Windows](evaluations/WO-062/ss-windows-runtime/final-qualification.json)
est exactement celle liée au manifeste `WindowsRuntime` par SHA-256 ; elle conserve les six
revues PASS revalidées et les deux cas pratiques `WR-H01` et `WR-N01` rejoués sous autorisation.
Les traces Windows plus volumineuses restent dans l’archive locale hashée WO-062 afin de ne pas
alourdir le checkout, sans modifier la matrice de qualification livrée.

Chaque skill reste limité au SofaScore Local Lab. Les sources du Lab font autorité, le dépôt ne
introduit aucun composant applicatif commun avec le Betting Project principal, et le statut reste
`EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY`.
## Installation personnelle depuis ce dépôt

Dans PowerShell sous Windows, à la racine du checkout :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -VerifyOnly
```

Pour installer ou vérifier uniquement `ss-provider-benchmark` version `0.1.0-candidate.1` :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package ProviderBenchmark
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package ProviderBenchmark -VerifyOnly
```

Pour installer ou vérifier ensemble les deux versions humaines validées FQ et CS :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package FootballQualityCiSecurity
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package FootballQualityCiSecurity -VerifyOnly
```

Pour installer ou vérifier `ss-java-module` version `0.1.0-candidate.5` :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package JavaModule
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package JavaModule -VerifyOnly
```

Pour installer ou vérifier `ss-windows-runtime` version `0.1.0-candidate.1` :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package WindowsRuntime
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Install-LocalLabSkills.ps1 -Package WindowsRuntime -VerifyOnly
```

La destination par défaut est `%USERPROFILE%/.agents/skills`, portée utilisateur documentée par [Codex](https://learn.chatgpt.com/docs/build-skills). Les skills du paquet sélectionné sont alors disponibles dans les worktrees actuels et futurs sur ce compte. Les descriptions ciblent le SofaScore Local Lab et les références sont résolues depuis la racine Git du worktree concerné. Actualiser la tâche ou redémarrer Codex si son catalogue est ancien.

Le paquet reste dans `docs/skills/local-lab` : ne pas en créer une seconde copie homonyme dans `.agents/skills` du dépôt lorsque l'installation utilisateur existe. Codex peut découvrir plusieurs skills de même nom sans les fusionner. Les skills `bp-*` demeurent propres au Betting Project.

L'installateur contrôle les sources du paquet sélectionné (dix pour `Lot1`, deux pour
`ProviderBenchmark`, quatre pour `FootballQualityCiSecurity`, deux pour `JavaModule` et deux pour
`WindowsRuntime`) par taille et SHA-256 avant toute copie, puis tous les fichiers de destination existants.
Pour `JavaModule` et `WindowsRuntime`, il vérifie aussi le chemin portable, le SHA-256, le candidat et
le bilan 8 PASS / 0 FAIL / 0 BLOCKED de la qualification liée. Il ne remplace aucun fichier différent et refuse les fichiers supplémentaires dans un dossier `ss-*`, afin de préserver une variante locale. La casse des chemins relatifs approuvés est exacte : un fichier `skill.md` ne remplace pas `SKILL.md`. Une casse différente du préfixe absolu de destination reste acceptée lorsque Windows résout le même dossier. Réconcilier ou sauvegarder cette variante explicitement avant de réinstaller ; aucun mode d'écrasement forcé n'est fourni. Une installation identique ne réécrit pas les fichiers. `-VerifyOnly` vérifie aussi la présence complète sans créer de répertoire. Les chemins liés sont refusés. Une erreur d'entrée/sortie peut laisser une installation partielle ; les fichiers déjà copiés sont contrôlés lors d'une nouvelle exécution.

`-Destination '<dossier de skills>'` permet une installation isolée. Pour qualifier l'installateur sans toucher au compte utilisateur :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1 -Package ProviderBenchmark
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1 -Package FootballQualityCiSecurity
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1 -Package JavaModule
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1 -Package WindowsRuntime
```

Ce contrôle crée un dossier temporaire neuf avec les seuls fichiers synthétiques et copies du paquet ; il le conserve pour inspection. Il ne lance ni application, ni Maven, ni Docker.

## Utilisation

```text
Utilise $ss-provider-benchmark pour préparer une scorecard prematch/live à partir des rapports versionnés du Lab.
Utilise $ss-football-quality pour examiner cette incohérence de score et de statut dans les preuves J6.
Utilise $ss-ci-security pour vérifier les contrôles et rapports CI applicables au SHA de cette PR.
Utilise $ss-java-module pour concevoir ou relire l’intégration Java/Spring/Maven du Lab.
Utilise $ss-windows-runtime pour diagnostiquer un incident de lanceur Windows, de processus ou de nettoyage.
Utilise $ss-work-order pour reprendre le WO concerné et établir son état actuel.
Utilise $ss-verify pour diagnostiquer cet échec CI et réaliser le correctif.
Utilise $ss-postgres-change et $ss-data-contract-replay pour préparer cette évolution du ledger et de son contrat.
Utilise $ss-review-closeout pour vérifier les preuves et clôturer ce lot.
```

Chaque dossier contient un `SKILL.md` et `agents/openai.yaml`. La sélection implicite reste disponible ; aucun outil MCP supplémentaire n'est requis. Les skills guident les vérifications et ne remplacent pas les preuves d'exécution.

## Provenance et qualification

Les dix fichiers du lot 1 sont les adaptations version 0.1 évaluées sous SKL-002, sans modification de leur contenu. Le [manifeste historique](evaluations/SKL-002/installation-manifest.json) fixe leurs empreintes. Les [deux essais analytiques et leur revue](evaluations/SKL-002/review.md) et la [découverte Codex](evaluations/SKL-002/discovery.json) conservent leurs octets d'origine. Les chemins personnels et états Git qu'ils contiennent sont des observations historiques du 5 septembre 2026 ; les liens du présent guide sont les références portables du paquet livré.

Les adaptations proviennent des cinq skills `bp-*` validés sous SKL-001, dans l'ordre du tableau : work-order, verify, postgres-change, data-contract-replay, review-closeout. Elles appliquent les conventions du Lab : WO `active`/`completed`, ADR-SS, profil Maven `integration-tests`, lanceurs PowerShell, ledgers et états des campagnes. Aucun composant applicatif commun n'est créé entre les dépôts.

Les essais historiques ont mesuré 77 et 99 secondes pour reprendre le contexte de WO-051 et WO-049. Aucun défaut nécessitant correction, faux vert déclaratif ou incohérence documentaire n'a été relevé dans ces deux réponses. Il n'y avait aucun témoin A/B et aucun test applicatif exécuté pendant ces essais : les gains généraux et répétitions réelles restent non mesurés.

Le [rapport WO-054](../validation/WO054-SKILLS-DELIVERY-20260905.md) distingue ces preuves historiques des contrôles de la livraison actuelle. Le [Work Order](../work_orders/completed/WO-SS-20260905-054-skills-lot1.md) consigne la validation propriétaire et l'autorisation de livraison. La PR et ses checks constituent le suivi distant de publication/fusion ; aucune ancienne qualification ne vaut CI du candidat courant.
