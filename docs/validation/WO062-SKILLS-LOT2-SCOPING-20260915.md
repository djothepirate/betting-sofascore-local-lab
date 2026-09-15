# WO-062 — Preuve de cadrage des skills du lot 2

## Portée

Le [WO-062](../work_orders/active/WO-SS-20260915-062-skills-lot2.md) prépare cinq skills
spécialisés du Local Lab à partir de la conversation
[« Skills du lot 2 »](chatgpt-conversation://6aa965f5-70ac-83eb-abcd-c54b44c42b0b).
Les deux réponses complètes ont été consultées. Les exemples `bp-*` sont adaptés au
périmètre `ss-*` demandé, sans déclaration de maturité du Betting Project ni accès à ce dépôt.

Cette preuve concerne le cadrage : elle ne qualifie aucun des cinq skills à créer, aucune
installation, aucune campagne fournisseur ou nouvelle fonctionnalité applicative.

## Base établie

- Date : 15 septembre 2026.
- Train source propre : `feature/V0.1.0-RC01`.
- HEAD local, référence `origin/feature/V0.1.0-RC01` et sommet GitHub lus identiques :
  `74d3f38afd64ce587353fbd645cad7a388e9756c`.
- Aucun WO-062 trouvé parmi les WO actifs/terminés, worktrees et branches distantes du jour inspectés.
- Branche créée depuis ce train : `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`.
- Worktree isolé : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2`.
- Version du POM : `0.1.0-rc.1-SNAPSHOT` ; Java requis 25, Spring Boot 4.1.0.
- Skills utilisés : `ss-work-order`, `ss-verify`, OpenAI Docs pour le format des skills.
- Deux sous-agents ont relu les sources, sans écrire dans le dépôt : benchmark/football et
  CI/architecture/Windows/installateur. Leurs conclusions sont intégrées au WO.

Les accès initiaux au réseau GitHub et aux métadonnées Git partagées étaient limités par le
sandbox. Les mêmes opérations ont réussi avec les permissions appropriées : lecture du train,
puis création de branche/worktree. Aucun rejet de revue automatique d'autorisation n'a été reçu.

## Conclusions sourcées

| Source inspectée | Conséquence pour le WO |
|---|---|
| Conversation complète et demande actuelle | Noms `ss-*` ; ordre benchmark, football/CI, Java/Windows ; cas historique puis tâche nouvelle. |
| `docs/skills/README.md`, paquet et manifeste SKL-002 | Préserver le lot 1 et ses attestations ; aucune copie homonyme découverte dans le dépôt. |
| `Install-LocalLabSkills.ps1`, tests associés | Cinq noms/dix fichiers figés : nouveau manifeste et évolution explicite nécessaires pour le lot 2. |
| Contrat/runbook J8 et rapport du 30 août | Distinguer strates, population, fenêtre et dénominateurs ; comparaison/exactitude externes non mesurées. |
| Identité J4, règles J5, compléments V4, historique J6 et normaliseur live | Lire le contrat actif et préserver absence, ambiguïté, provenance et corrections ; pas de priorité multi-fournisseurs inventée. |
| ADR-SS-004, WO-061, guide CI et workflow courant | Tests et preuves requis restent bloquants ; ne pas restaurer scans et bundles automatiques hérités. |
| Architecture et amendement WO-060, POM, sources/tests | Couches du Lab, profil worker distinct, exception J3 actuelle ; aucune couverture ArchUnit présumée. |
| Runbook local, WO-044/049/053, lanceurs | Runtime Windows réel, arguments, codes natifs, ressources possédées et nettoyage borné. |
| [Documentation officielle des skills](https://learn.chatgpt.com/docs/build-skills), consultée le 15 septembre | `SKILL.md` avec nom/description, métadonnées UI facultatives, références ciblées et tests du déclenchement. |

## Contrôles du cadrage

La vérification standard est exécutée en raison de l'exigence d'AGENTS avant proposition de
changement. Le lanceur Windows a été lu avant exécution ; il borne cette passe au standard
avec `-DskipITs`. Les variables de sécurité du bloc `env` de la CI courante sont reprises dans
le processus de vérification ; le répertoire temporaire Java est un chemin canonique du
worktree. Aucun profil Playwright ni environnement opérateur n'est activé.

| Contrôle | Résultat |
|---|---|
| État Git, worktrees, `git ls-remote --heads origin feature/V0.1.0-RC01 '*WO-SS-20260915-062'` | PASS ; train confirmé, branche WO créée depuis cette base. |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-Local.ps1` | PASS, code 0 ; Maven `-DskipITs clean verify`, `BUILD SUCCESS`, 3 min 59 s, fin `2026-09-15T15:58:40Z` ; Java 25.0.4. |
| Lecture des 238 XML Surefire | 2 440 cas recensés, 2 435 exécutés, 0 échec, 0 erreur, 5 ignorés. |
| `java ci/VerifyTestReports.java standard` | PASS ; preuve XML obligatoire conforme, compteurs confirmés. |
| Failsafe de cette exécution | `integration-test (default)` et `verify (default)` explicitement ignorés par `-DskipITs` ; aucune intégration revendiquée. |
| `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .tmp/Verify-WO062Documents.ps1` | PASS sur les cinq fichiers ; UTF-8 strict, 306 liens locaux existants, aucun espace terminal/caractère de remplacement, onze motifs de contenu sensible sans signal. |
| `git diff --check`, inventaire des fichiers modifiés/non suivis et inspection du diff | PASS ; exactement les cinq documents listés ci-dessous, aucun changement applicatif ou du paquet lot 1. |
| `server.address`, tests et fichiers runtime | Binding `127.0.0.1` confirmé dans `application.yml` ; aucune modification de configuration, de test ou de source runtime. Le POM désactive le runtime J3 dans les tests standards. |

Les cinq cas ignorés sont quatre cas de liens symboliques dépendant des droits de l'hôte
(`LocalJ7ExportFileStoreTest`, deux ; `J8BenchmarkExportCommandTest`, deux), et le cas
`J6NativeBinaryPipelineQualificationTest.dockerQualificationPassesThreeSequentialRunsAtEffectiveDefaults`,
qui exige une propriété Docker dédiée. Ils ne sont pas comptés comme réussites.

Le journal local ignoré est `.tmp/wo062-verify-local.log` ; les rapports de cette passe sont
sous `target/surefire-reports`. La suite qualifie le code de la base `74d3f38`, inchangé par
le cadrage. Les compléments documentaires sont contrôlés séparément ; aucune recette future
des skills n'est déduite de cette exécution. Le scan de contenu sensible porte sur les cinq
documents du delta ; il n'est pas présenté comme une nouvelle analyse de tout l'historique Git.

## Revue indépendante

La revue benchmark/football a identifié une formulation qui assimilait une minute absente
autorisée et un penalty accordé à des ambiguïtés. Le WO a été corrigé : l'absence autorisée
reste inconnue, le penalty accordé reste un fait sans résultat de tir inféré ; seuls le conflit
non résolu ou l'appariement indécidable sont qualifiés d'ambigus. Les règles de mesure, de
comparabilité et l'adaptation `ss-*` n'ont pas fait apparaître d'autre défaut matériel.

La revue CI/Java/Windows/installateur n'a relevé aucun défaut matériel. Le WO précise aussi
le caractère monomodule Maven du Lab et l'allowlist explicite des éventuelles références
livrées en plus des vingt fichiers de base. La revue vérifie le cadrage, pas les futurs skills.

Les tests d'intégration séparés ne sont pas requis pour ce delta exclusivement documentaire.
La CI distante, les recettes des futurs skills et l'installation personnelle sont non exécutées
dans ce cadrage. Aucun résultat historique n'est attribué au contenu futur du lot 2.

## Fichiers livrés par le cadrage

- `docs/work_orders/active/WO-SS-20260915-062-skills-lot2.md` : périmètre, ordre, rôles, sources,
  scénarios, livrables, critères et étapes de qualification/livraison.
- `docs/validation/WO062-SKILLS-LOT2-SCOPING-20260915.md` : présente preuve.
- `docs/skills/README.md` : état futur du lot 2 et limite actuelle de l'installateur.
- `README.md` : entrée de cadrage.
- `CHANGELOG.md` : évolution documentaire.

Le WO reste actif et les cases de réception de sa réalisation restent ouvertes. Aucun skill,
code applicatif, test, migration, ADR, workflow CI ou paramètre opérateur n'est modifié.
