# AGENTS.md — SofaScore Local Lab

## Mission du dépôt

Ce dépôt est un laboratoire local, expérimental et non critique du Betting Project. Toute modification doit préserver les statuts :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## Socle obligatoire

- Java 25 LTS pour compilation et exécution ;
- Spring Boot 4.1.0 ;
- Maven via `mvnw` ou `mvnw.cmd` ;
- PostgreSQL local dans Docker Desktop ;
- application liée uniquement à `127.0.0.1` ;
- Eclipse comme IDE principal ;
- fichiers texte UTF-8.

## Commandes autorisées par défaut

```text
./mvnw clean verify
./mvnw -Pintegration-tests verify
./mvnw -Dspring-boot.run.profiles=local spring-boot:run

docker compose --env-file .env config
docker compose --env-file .env up -d postgres
docker compose --env-file .env down
```

Sous Windows, utiliser les variantes `mvnw.cmd` et les scripts `scripts/*.ps1`.

## Invariants de sécurité

1. Aucun appel réel vers SofaScore dans les tests standards.
2. Aucun endpoint réel ajouté sans Work Order et revue de l’ADR-SS-001.
3. Aucun proxy rotatif, changement automatique d'adresse, mécanisme furtif, résolution de challenge ou réutilisation de cookie/jeton. Playwright est le seul transport cible autorisé vers les endpoints SofaScore : il reste local, à lancement manuel, opt-in et limité aux parcours J3, J4, J5 ou futurs couverts par un Work Order et l'ADR-SS-001 v1.4, avec les exceptions bornées d'[ADR-SS-005 v0.8](ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) pour WO-058 : campagnes live et regroupement des trois familles d'une collecte J5 manuelle confirmée. Chaque nouvel endpoint exige une allowlist et une revue dédiées. FlareSolverr est écarté comme transport et fallback ; ses preuves historiques restent conservées pour audit et toute réintroduction exige une nouvelle décision propriétaire explicite.
4. Aucun payload complet, cookie, jeton ou secret dans les logs.
5. Aucune écoute sur `0.0.0.0`, une IP LAN ou une interface publique.
6. Aucune dépendance du Betting Project principal à ce dépôt.
7. Aucun envoi de payload brut vers le VPS.
8. Aucune activation de polling, live ou tâche planifiée au J0/J1.
9. Les données brutes et normalisées restent séparées.
10. Toute donnée normalisée conserve le snapshot, le hash, le parseur et l’heure de réception.
11. Playwright ne démarre jamais automatiquement : ni au démarrage normal, ni pendant les tests standards, ni via scheduler ou polling. Chaque lancement exige une action opérateur explicite. Pour le seul parcours live couvert par [ADR-SS-005 v0.8](ADR-SS-005-bounded-local-live-j4-j5-campaigns.md), une campagne lancée explicitement par l'opérateur peut exécuter les cycles automatiques de son manifeste dans sa fenêtre et ses budgets. Aucun tick ne crée ou recrée un navigateur ; perte du contexte, veille, panne ou redémarrage terminent la session sans reprise automatique.
12. Chaque campagne Playwright utilise un contexte non persistant neuf ; aucun profil, cookie, `storageState`, HAR, trace, vidéo, capture ou téléchargement n'est conservé, journalisé ou ajouté à Git.

**Exception J3 adoptée le 13 septembre 2026 — WO-060 / [ADR-SS-007 v0.2](ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md) :**
les invariants 3 et 11 autorisent aussi le lancement J3 depuis un clic direct ou un ordre
quotidien/planifié durable du Lab local configuré. La préférence automatique est initialement
activée puis persistante ; les tests standards ne lancent aucun navigateur. Pendant une pause
live, une sous-opération J3 isolée peut disposer d’un contexte neuf temporaire dans le même
worker ; un seul contexte émet et aucun état de session n’est transféré. Le contexte live
est conservé et jamais recréé par un tick. Les plafonds, le garde durable, les refus fournisseur
et le nettoyage prouvé restent applicables. Cette exception supprime les confirmations
d’intention et les gestes d’arrêt/circuit du seul parcours J3 ; aucun autre parcours n’est armé.

## Règles du jalon J1

- `ConnectorGate` doit rester bloquant.
- Les définitions du catalogue doivent rester `callable=false` et sans URI.
- `sofascore.enabled` reste `false` par défaut.
- Le profil Maven `sofascore-live-test` doit rester bloqué.
- Les boutons réseau de l’interface restent désactivés.

Toute modification de ces quatre points relève au minimum du jalon J3 et exige un Work Order séparé.

## Tests et définition de fini

Avant proposition de changement :

1. exécuter `mvnw clean verify` ;
2. exécuter `mvnw -Pintegration-tests verify` si les migrations ou la persistance changent ;
3. vérifier qu’aucun secret n’est présent dans le diff ;
4. vérifier que `server.address=127.0.0.1` reste effectif ;
5. vérifier qu’aucun appel réel n’est introduit dans les tests standards ;
6. mettre à jour le Work Order, le changelog et la documentation concernée ;
7. fournir la liste des fichiers modifiés, les commandes exécutées et leurs résultats.

## Répertoires et fichiers sensibles

- `ADR-SS-001-*.md` : modification uniquement après décision explicite.
- `docs/reference/*.pdf` : référence immuable ; ne pas réécrire.
- `.env` : fichier local ignoré, ne jamais ajouter à Git.
- `fixtures/` : aucun secret, cookie ou identifiant de session.
- `exports/` : contenu runtime ignoré, sauf `.gitkeep`.
- `src/main/resources/db/migration/` : migrations append-only ; ne pas modifier une migration déjà appliquée après partage du dépôt.

## Workflow Git

- GitHub est canonique pour `main`, les branches `feature/*`, les branches de Work Order, les Pull
  Requests et les tags ; les branches `release/V*` existent uniquement sur GitLab ;
- un train de version porte l'une des formes exactes `feature/VX.Y.Z`,
  `feature/VX.Y.Z-RCnn` ou `feature/VX.Y.Z-RCnn-SNAPSHOT`, avec `nn` compris entre `01` et `99`,
  et part du même commit que sa branche GitLab protégée de même train sous `release/`, elle-même
  créée depuis `main` ;
- tout nouveau Work Order part du train feature correspondant, dans un worktree distinct, et porte
  exactement `feature/<TRAIN>-(CODEX|HUMAN)-WO-SS-YYYYMMDD-NNN` ;
- une PR de Work Order cible exclusivement le train feature de même version. Sa clôture, ou celle
  d'une suite de Work Orders explicitement listée, ne devient effective qu'après fusion de la PR ;
- la PR finale du train cible `main`, exige la version Maven finale correspondant exactement au
  train et utilise un merge commit. Un train stable ne peut donc pas entrer dans `main` avec
  `X.Y.Z-SNAPSHOT`. Le train est ensuite avancé en fast-forward jusqu'à ce merge commit de `main`,
  puis `main` et le train sont synchronisés vers GitLab ;
- la seule MR GitLab autorisée est `feature/<TRAIN>` vers la branche protégée
  `release/<TRAIN>` strictement identique, en fast-forward, sans squash ni rebase. Son garde exige
  un historique complet, le sommet source égal au SHA source canonique et à `origin/main`, et la
  release cible ancêtre de ce sommet ;
- les snapshots durables proviennent uniquement d'un push du train feature exact. Les bundles de
  PR, de branche WO, de `main` et de `release/V*` restent éphémères ; tous les artefacts du Lab
  conservent `LOCAL_ONLY` et `vps.deployable=false` ;
- le seul décalage temporaire entre train et version Maven est le push qui crée
  `feature/<TRAIN>` exactement au sommet canonique de `origin/main`. Sa provenance porte
  `source.train.seed=true` ; tout push ultérieur, PR ou lancement manuel exige le mapping Maven du
  train. GitHub accepte `main`, les features d'intégration et les branches WO valides, jamais une
  branche `release/V*` ni une feature approchante ;
- les versions Maven et tags conservent SemVer 2A : `RC01` se traduit par `rc.1`, donc une branche
  feature ou WO `VX.Y.Z-RC01` porte Maven `X.Y.Z-rc.1-SNAPSHOT` pendant le développement ou
  `X.Y.Z-rc.1` après une PR GitHub de finalisation du POM versionné vers le train. La PR finale
  vers `main`, la MR vers `release/VX.Y.Z-RC01` et le tag `vX.Y.Z-rc.1` exigent `X.Y.Z-rc.1` ;
  aucun retrait de suffixe dans le build GitLab ni commit direct sur release. Une branche
  `VX.Y.Z-RC01-SNAPSHOT` porte Maven `X.Y.Z-rc.1-SNAPSHOT` et n'est jamais taguée ;
- une même version Maven snapshot peut être reconstruite sans limite sur sa feature d'intégration.
  Les bundles conservent l'IID et le SHA ; un rebuild conforme utilise `source.train.seed=false` ;
- les tags communs GitHub/GitLab utilisent uniquement `vX.Y.Z` ou `vX.Y.Z-rc.N`, avec `v` et `rc`
  minuscules, après alignement exact de `main`, du train et de la release GitLab ; le pipeline tagué
  GitLab récupère et compare explicitement ces trois références au commit extrait. Seuls les RC 1 à
  99 disposent d'un train branché promouvable ;
- GitLab protège uniquement les branches `release/V*`. La règle distincte de tags protégés `v*`
  est néanmoins un prérequis obligatoire de toute promotion et ne protège aucune branche de plus ;
- l'unique exception de bootstrap est
  `codex/ss-20260905-055-version-branch-workflow` vers `main` sur la base exacte `054fa4c` ; le SHA
  source doit être résolu et descendre de cette base avec une merge-base strictement identique, et
  la version Maven doit rester exactement `0.1.0-SNAPSHOT` ;
- les branches antérieures restent des références historiques en lecture seule : elles ne créent
  aucun nouveau Work Order, snapshot durable ou promotion. Un pipeline de branche GitLab accepte seulement
  `main`, un train `feature/<TRAIN>` exact ou une branche `release/<TRAIN>` exacte, jamais une
  branche WO, bootstrap ou historique ;
- état propre avant délégation ;
- aucun changement simultané par Eclipse et un agent dans le même worktree ;
- commits petits, explicites et reliés au Work Order ;
- revue humaine et réexécution des tests avant fusion.
