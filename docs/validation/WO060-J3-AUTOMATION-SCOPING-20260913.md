# WO-060 — Preuve de cadrage de l’évolution J3, 13 septembre 2026

## 1. Portée et état documentaire

Le propriétaire a demandé les améliorations J3 puis choisi explicitement **« Work Order et
décisions d’architecture »**. La session prépare donc le
[WO-060](../work_orders/active/WO-SS-20260913-060-j3-automation-and-durable-catalog.md),
`SCOPED — READY_FOR_OWNER_REVIEW`, et
[ADR-SS-007 v0.1](../../ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md), `PROPOSED`.
L’activation initiale et l’exécution des horaires explicites après un succès sont des choix
propriétaire acquis ; les détails techniques sont identifiés comme propositions.

Cette preuve porte sur le cadrage. Aucun résultat de la suite existante ne qualifie les trente
cas futurs de WO-060, ses migrations à venir ou le futur protocole de pause J3/live.

## 2. Base et environnement établis

- Racine source : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab`, train propre `feature/V0.1.0-RC01`.
- HEAD source et sommet canonique du train : `d4c3d8ceeb6c46442f0792d436a3df7e2630f599`.
- `git ls-remote origin refs/heads/feature/V0.1.0-RC01 refs/heads/main` : train confirmé ; `main` distinct au SHA `3d2f9da1479898e19df7fcfe8ccacdaf17ce8452`.
- Le premier accès GitHub dans le sandbox a échoué par indisponibilité réseau ; le même contrôle en lecture seule avec l’accès approprié a réussi. Aucun refus d’auto-review n’a été reçu.
- Branche WO : `feature/V0.1.0-RC01-CODEX-WO-SS-20260913-060`, créée depuis le train vérifié.
- Worktree isolé : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo060-j3-automation-design`.
- Java observé : `25.0.4`, LTS ; Spring Boot `4.1.0`, version du projet Maven `0.1.0-rc.1-SNAPSHOT`, schéma source V54.
- `AGENTS.md` de cette base : SHA-256 `75e0ca02adfaf5516a21f70609b01f1e0924351c352bd7bd039cc1187332ee3c`.
- Skills lus et appliqués : `ss-work-order`, `ss-postgres-change`, `ss-verify`. Aucun sous-agent utilisé.

Les quatre statuts du Lab sont conservés. Le dépôt principal du Betting Project et le worktree
Eclipse ne sont pas modifiés. Aucun accès à la base opérateur n’est nécessaire à cette preuve.

## 3. Sources inspectées et conclusions

| Source locale inspectée | Conclusion utilisée dans le cadrage |
|---|---|
| `J3ManualCollectionEvidenceService` | Dernier document en mémoire, remplacé par chaque publication |
| `J3TournamentCatalogService` | Dernière preuve terminale complète du processus ; reconstruction depuis les snapshots exacts, vérification de la pagination et des sources |
| Contrôleurs J3/dashboard, formulaire `dashboard.html` | Étapes opérateur actuelles et absence de navigation durable par date |
| `TournamentEventDiscoveryControlService` | Dépendance au catalogue global à corriger pour les onglets et dates distincts |
| `ManualProviderRequestCoordinator`, garde V33 et `LiveCampaignService` | Verrou possédé par le thread live pendant toute la campagne ; refus du J3 concurrent, watchdog et nettoyage explicites |
| `LiveProviderSession`, factory et supervisor Playwright | Quatre endpoints live, un seul état de campagne actif ; nouveau protocole requis pour un contexte J3 isolé |
| Migrations V22, V27, V33, schéma jusqu’à V54 et runbook J6 | Métadonnées/sources J8 disponibles pour un backfill attesté ; preuve de reconstructibilité de la base réelle encore absente ; rétention/sauvegarde à étendre |
| ADR-SS-001 v1.4, ADR-SS-005 v0.11, ADR-SS-006 v0.1 | Exceptions de déclenchement/session à proposer sans déclarer les anciennes décisions déjà remplacées |
| `pom.xml`, `scripts/Verify-Local.ps1`, `application.yml`, `application-local.yml` | Java 25, binding loopback, fournisseur fermé par défaut ; distinction des profils et Failsafe hérité à observer |

La revue utilise le code courant du train. Les rapports historiques de WO-058/059 restent
des preuves à leur date ; leurs compteurs et limitations ne sont pas copiés comme résultats
de la présente session.

## 4. Vérifications réellement exécutées

La commande globale est exécutée en raison de l’exigence « Avant proposition de changement »
d’AGENTS, bien que le diff soit exclusivement documentaire.

| Commande ou contrôle | Résultat |
|---|---|
| `git status --short --branch`, `git worktree list --porcelain`, `git rev-parse HEAD` | Source propre et base identifiée ; worktree WO distinct |
| `git ls-remote` sur le train et `main` | Sommets distants vérifiés, aucun push |
| `git worktree add .tmp/wo060-j3-automation-design -b feature/V0.1.0-RC01-CODEX-WO-SS-20260913-060 feature/V0.1.0-RC01` | Worktree créé sur `d4c3d8c` |
| `java -version` | Java 25.0.4 LTS |
| `mvnw.cmd clean verify` | `BUILD SUCCESS`, code 0, 11 min 05 s ; fin `2026-09-13T19:33:54Z` |
| Rapports Surefire de cette exécution | 2 338 tests, 0 échec, 0 erreur, 5 ignorés |
| Rapports Failsafe de cette même exécution | 245 tests PostgreSQL/intégration locale, 0 échec, 0 erreur, 0 ignoré ; exécutions héritées `integration-test (default)` et `verify (default)` observées |
| `mvnw.cmd -Pintegration-tests verify` séparé | Non relancé : aucune migration/persistance modifiée dans le cadrage et la commande globale a déjà exécuté Failsafe ; aucune validation future WO-060 n’en est déduite |
| Contrôle documentaire local `.tmp/Verify-WO060Documents.ps1` | `PASS` sur les six fichiers : décodage UTF-8 strict, 272 liens locaux existants, pas d’espace terminal ni caractère de remplacement |
| Scan de contenu sensible des six fichiers et revue du diff | Aucune règle détectée : en-têtes d’authentification/cookies, champs de credentials, clés privées, JWT et jetons usuels ; aucune donnée opérateur ajoutée |
| `git diff --check` et inventaire des chemins changés/non suivis | Réussis ; seuls les six fichiers documentaires listés ci-dessous sont concernés |
| Lecture des défauts et absence de diff runtime | `server.address=127.0.0.1`, fournisseur et profils réels fermés par défaut ; aucune classe, propriété, migration ou test modifié |

Le journal local ignoré est `.tmp/wo060-clean-verify.log` dans le worktree WO. Les rapports
effectifs sont sous `target/surefire-reports` et, lorsqu’exécutés, `target/failsafe-reports`.
Aucun profil fournisseur ni profil Chromium n’est activé pour cette vérification de base.

Les cinq cas ignorés sont quatre contrôles de liens symboliques dépendants des capacités de
l’hôte (`LocalJ7ExportFileStoreTest`, deux cas ; `J8BenchmarkExportCommandTest`, deux cas) et
`J6NativeBinaryPipelineQualificationTest.dockerQualificationPassesThreeSequentialRunsAtEffectiveDefaults`,
qui exige la propriété explicite `j6.docker.qualification`. Ils ne sont pas comptés comme
réussites. Les tests PostgreSQL utilisent les instances isolées de Testcontainers.

La lecture directe des XML recense **231 rapports Surefire et 12 rapports Failsafe**. Les
empreintes des deux livrables de décision vérifiés sont conservées ici, hors de ces fichiers :

| Livrable | SHA-256 UTF-8 |
|---|---|
| ADR-SS-007 v0.1 proposé | `dda4240e3a9b35024bbb5fe67ee89aa47faabec56a490ba5ab623cba6056aa5f` |
| WO-060 cadré | `87e1304c25bbc27b80a85aaa0519253216509c25df05c86c13bc6a84d38e435b` |

Ces empreintes identifient la proposition relue, pas une acceptation propriétaire. La sortie
du contrôle de documents est conservée localement dans `.tmp/wo060-document-checks.json`.

## 5. Livrables et limites

Six fichiers documentaires sont créés ou orientés par ce cadrage :

1. `ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md` — proposition technique et choix fonctionnels acquis.
2. `docs/work_orders/active/WO-SS-20260913-060-j3-automation-and-durable-catalog.md` — objectifs, découpage R0–R5, trente critères et définition de fini.
3. `docs/validation/WO060-J3-AUTOMATION-SCOPING-20260913.md` — présente preuve.
4. `README.md` — orientation vers le cadrage courant.
5. `CHANGELOG.md` — entrée explicitement documentaire.
6. `docs/architecture/ARCHITECTURE.md` — renvoi au projet d’évolution, architecture courante distinguée.

Les ADR acceptés, AGENTS, fichiers Java, configuration, migrations, scripts, fixtures et données
opérateur ne sont pas modifiés. Aucune tâche planifiée, collecte SofaScore, migration primaire,
purge, sauvegarde primaire, publication, PR ou fusion n’est exécutée par cette phase.

La disponibilité des anciennes dates requiert l’inventaire de leurs preuves exactes lors de la
réalisation. Les règles d’échec quotidien, de désactivation en cours, d’heures civiles et de
borne J3 sont des propositions concrètes à relire ; le contexte J3 isolé et la reprise live
exigent une nouvelle qualification. L’ADR n’est pas marqué accepté et le WO n’est pas clôturé.
