# Skills du SofaScore Local Lab

Les cinq skills du lot 1 ont été validés par le propriétaire le 5 septembre 2026 pour le Betting Project et le Local Lab. Leur livraison sur les deux branches `main` est autorisée. Ce dépôt contient le paquet autonome des adaptations `ss-*` ; son utilisation ne nécessite aucun checkout du Betting Project.

| Skill | Usage |
|---|---|
| [ss-work-order](local-lab/ss-work-order/SKILL.md) | Reprendre un Work Order dans le bon worktree, retrouver décisions et preuves. |
| [ss-verify](local-lab/ss-verify/SKILL.md) | Choisir les commandes Windows/Linux pertinentes et diagnostiquer les faux verts. |
| [ss-postgres-change](local-lab/ss-postgres-change/SKILL.md) | Relier migrations, transactions, concurrence, ledgers et preuves PostgreSQL. |
| [ss-data-contract-replay](local-lab/ss-data-contract-replay/SKILL.md) | Préserver provenance, snapshots, complétude, contrats et validation humaine. |
| [ss-review-closeout](local-lab/ss-review-closeout/SKILL.md) | Relier revue, qualification, décisions, clôture et état Git courant. |

## Lot 2 — PB, FQ et CS validés et installés personnellement

Le [WO-062](../work_orders/active/WO-SS-20260915-062-skills-lot2.md), préparé le 15 septembre 2026,
adapte au Local Lab les rôles de la conversation « Skills du lot 2 » dans l'ordre demandé :

| Étape | Skill | Rôle |
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
est désormais qualifié sur les douze cas figés : onze PASS de run-01 et le PASS complémentaire
PB-S01 de run-02, instrumenté avec lecture intégrale du candidat exact. Le [protocole et la scorecard prematch/live](evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-N01/response.md)
sont produits. Voir la [qualification et ses limites](../validation/WO062-PROVIDER-BENCHMARK-PBS01-QUALIFICATION-20260915.md).
La [préparation A1](../validation/WO062-PROVIDER-BENCHMARK-PREPARATION-20260915.md) demeure intacte,
avec son état historique `NOT_RUN`. Run-01 conserve son BLOCKED initial ; la qualification
courante renvoie à la preuve complémentaire. Le propriétaire a ensuite accepté le contenu,
le périmètre et les limites de `0.1.0-candidate.1` et autorisé son installation personnelle :
[validation et installation A3](../validation/WO062-PROVIDER-BENCHMARK-INSTALLATION-20260915.md).
Les deux fichiers installés restent exactement ceux évalués ; la version est conservée.

Chaque skill devra passer un cas historique connu, une tâche nouvelle et des tests de sélection,
puis une revue. Les sources du Lab font autorité : J8 ne dispose pas de contrôle externe intégré,
et la CI suit notamment l'allègement adopté dans WO-061. Le WO distingue les
[preuves du cadrage](../validation/WO062-SKILLS-LOT2-SCOPING-20260915.md) de cette qualification future.

L'installateur livre **les cinq skills du lot 1 par défaut**. L'option explicite
`-Package ProviderBenchmark` installe uniquement le premier skill du lot 2, à partir de son
[manifeste approuvé](evaluations/WO-062/ss-provider-benchmark/installation-manifest.json).
Les candidats [ss-football-quality](local-lab/ss-football-quality/SKILL.md) et
[ss-ci-security](local-lab/ss-ci-security/SKILL.md), version `0.1.0-candidate.1`, sont
désormais qualifiés sur huit cas chacun, avec revue indépendante et [limites documentées](../validation/WO062-FOOTBALL-QUALITY-CI-SECURITY-CANDIDATES-20260915.md).
Leurs sources et critères ont été figés avant rédaction ; les nouvelles analyses football
et CI sont produites. Le propriétaire a accepté leur contenu, leur périmètre et leurs limites ;
les quatre fichiers exacts sont désormais [validés humainement et installés personnellement](../validation/WO062-FOOTBALL-QUALITY-CI-SECURITY-INSTALLATION-20260915.md).
Le paquet explicite `FootballQualityCiSecurity` suit son [manifeste approuvé](evaluations/WO-062/football-quality-ci-security/installation-manifest.json).
Le suffixe `candidate.1` conserve l’identité des fichiers évalués et acceptés.
Les candidats [ss-java-module](local-lab/ss-java-module/SKILL.md) `.2` et
[ss-windows-runtime](local-lab/ss-windows-runtime/SKILL.md) `.1` sont rédigés.
La première recette conserve 5 PASS / 3 FAIL Java et 6 PASS / 2 BLOCKED Windows.
Après les dix tentatives supplémentaires puis deux sessions ciblées, Java `.2` conserve
7 PASS / 1 FAIL (deux omissions JM-N01) ; Windows garde 6 PASS / 2 BLOCKED. WR-H01
termine cette fois en moins de cinq minutes, mais bloque au préflight avant le harnais.
Une preuve séparée établit la filiation et la propriété d'une JVM directe ; WR-N01
complet n'est pas relancé, ses conditions de reprise sont préparées et le résidu conservé.
Voir la [reprise ciblée et ses limites](../validation/WO062-C-TARGETED-REPRISE-20260916.md)
et le [rapport C précédent](../validation/WO062-JAVA-WINDOWS-CANDIDATES-20260915.md).
La qualification reste ouverte ; validation humaine C, installation et consolidation des dix restent à réaliser.

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

La destination par défaut est `%USERPROFILE%/.agents/skills`, portée utilisateur documentée par [Codex](https://learn.chatgpt.com/docs/build-skills). Les skills du paquet sélectionné sont alors disponibles dans les worktrees actuels et futurs sur ce compte. Les descriptions ciblent le SofaScore Local Lab et les références sont résolues depuis la racine Git du worktree concerné. Actualiser la tâche ou redémarrer Codex si son catalogue est ancien.

Le paquet reste dans `docs/skills/local-lab` : ne pas en créer une seconde copie homonyme dans `.agents/skills` du dépôt lorsque l'installation utilisateur existe. Codex peut découvrir plusieurs skills de même nom sans les fusionner. Les skills `bp-*` demeurent propres au Betting Project.

L'installateur contrôle les sources du paquet sélectionné (dix pour `Lot1`, deux pour
`ProviderBenchmark`, quatre pour `FootballQualityCiSecurity`) par taille et SHA-256 avant toute copie, puis tous les fichiers de destination existants. Il ne remplace aucun fichier différent et refuse les fichiers supplémentaires dans un dossier `ss-*`, afin de préserver une variante locale. La casse des chemins relatifs approuvés est exacte : un fichier `skill.md` ne remplace pas `SKILL.md`. Une casse différente du préfixe absolu de destination reste acceptée lorsque Windows résout le même dossier. Réconcilier ou sauvegarder cette variante explicitement avant de réinstaller ; aucun mode d'écrasement forcé n'est fourni. Une installation identique ne réécrit pas les fichiers. `-VerifyOnly` vérifie aussi la présence complète sans créer de répertoire. Les chemins liés sont refusés. Une erreur d'entrée/sortie peut laisser une installation partielle ; les fichiers déjà copiés sont contrôlés lors d'une nouvelle exécution.

`-Destination '<dossier de skills>'` permet une installation isolée. Pour qualifier l'installateur sans toucher au compte utilisateur :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1 -Package ProviderBenchmark
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Test-LocalLabSkillsInstallation.ps1 -Package FootballQualityCiSecurity
```

Ce contrôle crée un dossier temporaire neuf avec les seuls fichiers synthétiques et copies du paquet ; il le conserve pour inspection. Il ne lance ni application, ni Maven, ni Docker.

## Utilisation

```text
Utilise $ss-provider-benchmark pour préparer une scorecard prematch/live à partir des rapports versionnés du Lab.
Utilise $ss-football-quality pour examiner cette incohérence de score et de statut dans les preuves J6.
Utilise $ss-ci-security pour vérifier les contrôles et rapports CI applicables au SHA de cette PR.
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
