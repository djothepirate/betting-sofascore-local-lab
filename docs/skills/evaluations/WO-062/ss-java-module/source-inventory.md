# WO-062 — Inventaire préparatoire de ss-java-module

- Statut : `PREPARED_NOT_RUN` ; préparation C1, avant rédaction.
- Date : 15 septembre 2026.
- Racine lue : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2`.
- Commit de référence : `6648dd423e556b5248b8793a539ae85a7680f9bc`.
- Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`.
- Version observée : Maven `0.1.0-rc.1-SNAPSHOT`, Java 25, Spring Boot 4.1.0.
- 48 sources ciblées, 716265 octets de matérialisation locale.
- Cet inventaire est réservé à la conception/revue. Les sessions métier ne reçoivent
  que les fichiers de leurs `source_ids`, leur fragment d’entrée et le candidat exact.

## Provenance et limites

Le [manifeste](manifest.json) conserve taille et SHA-256 des octets natifs du worktree,
ainsi que l’OID Git de chaque source au commit ci-dessus. Les blobs Git et leur copie
Windows peuvent différer par les fins de ligne ; leur identité n’est pas interchangeable.
Toute matérialisation de run doit déclarer ses propres empreintes. Aucun source n’est
silencieusement actualisé après le gel. JM-N02 est une source de cadrage seulement,
absente de toutes les allowlists métier ; les mises à jour du WO ne modifient pas les cas.

Les gros fichiers de supervision et de live sont conservés entiers pour permettre une
vérification contradictoire. Leur lecture peut être ciblée sur les méthodes pertinentes :
aucun extrait annoté préparé par le concepteur ne remplace le code réel. L’analyse n’exige
pas d’auditer toutes les fonctionnalités live ni toutes les migrations du dépôt.
Les références internes n’étendent pas les fichiers autorisés.

| ID | Classe | Source et localisation | Utilité |
| --- | --- | --- | --- |
| JM-N01 | normative | [AGENTS.md](../../../../../AGENTS.md) — Invariants 3, 11, 12 et exception J3 | Statuts, frontières fournisseur et exception J3. |
| JM-N02 | scoping | [docs/work_orders/active/WO-SS-20260915-062-skills-lot2.md](../../../../../docs/work_orders/active/WO-SS-20260915-062-skills-lot2.md) — 4.C1, 5, 6 | Cadrage seulement, exclu de toute entrée métier. |
| JM-N03 | normative | [ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md](../../../../../ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md) — v0.3 ; sections 2 à 5 | Autorité courante J3 durable et contexte temporaire dans le worker live. |
| JM-N04 | architecture | [docs/architecture/ARCHITECTURE.md](../../../../../docs/architecture/ARCHITECTURE.md) — Encart WO-060 ; section 3 | Couches actuelles et déclarations historiques à contextualiser. |
| JM-P01 | procedure_implementation | [scripts/Verify-Local.ps1](../../../../../scripts/Verify-Local.ps1) — forbiddenPatterns ; commandes Maven | Gardes textuels réels et commandes effectives ; pas une analyse globale des dépendances. |
| JM-P02 | procedure | [docs/runbooks/J3-AUTOMATION-AND-DURABLE-CATALOG.md](../../../../../docs/runbooks/J3-AUTOMATION-AND-DURABLE-CATALOG.md) — Parcours et limites | Modes opérateur, consultation durable et absence de reprise du live. |
| JM-H01 | historical | [docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md](../../../../../docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md) — Sections 1 à 8 | Qualification TCP réelle et bornée, pas une revue exhaustive des ports d’architecture. |
| JM-H02 | historical | [docs/work_orders/completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md](../../../../../docs/work_orders/completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md) — Sections 2, 5, 7 à 9 | Décision propriétaire postérieure au rapport autonome. |
| JM-H03 | historical | [docs/validation/WO060-J3-IMPLEMENTATION-20260914.md](../../../../../docs/validation/WO060-J3-IMPLEMENTATION-20260914.md) — Qualification Maven ; recette worker | Commande explicite de qualification worker et limites de la preuve datée. |
| JM-I01 | implementation | [pom.xml](../../../../../pom.xml) — parent ; properties ; build ; profiles | Monomodule, Java/Spring, profils standard/intégration/runtime/qualification. |
| JM-I02 | implementation | [src/main/resources/application.yml](../../../../../src/main/resources/application.yml) — spring.profiles ; server.address ; sofascore | Profil local par défaut, adresse loopback, runtime J3 et activation fournisseur distincts. |
| JM-I03 | implementation | [src/main/resources/application-local.yml](../../../../../src/main/resources/application-local.yml) — spring.config.activate ; request-timeout | Surcharge Spring locale, aucun ajout de source Maven. |
| JM-I04 | implementation | [.mvn/wrapper/maven-wrapper.properties](../../../../../.mvn/wrapper/maven-wrapper.properties) — distributionUrl | Version réellement distribuée par le wrapper. |
| JM-I05 | implementation | [src/main/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightProperties.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightProperties.java) — isSafeConfiguration | Validation parente de l’origine et des bornes. |
| JM-I06 | implementation | [src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerConfiguration.java](../../../../../src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerConfiguration.java) — fromEnvironment ; parseOrigin ; parsePort ; uriFor | Validation indépendante enfant et URI allowlistées. |
| JM-I07 | implementation | [src/main/java/com/bettingproject/sofascorelocal/domain/provider/ScheduledEventsTransportRequest.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/domain/provider/ScheduledEventsTransportRequest.java) — constructeur | Frontière loopback distincte déjà bornée avant WO-030. |
| JM-I08 | implementation | [src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java) — onApplicationReady ; start ; reserveNetwork ; Standalone | Admission durable, démarrage Web, consommateur et ressources standalone/live. |
| JM-I09 | implementation | [src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java) — imports ; ProviderAccess ; execute | Cas d’usage borné, ProviderAccess, cache/import et dépendances concrètes existantes. |
| JM-I10 | implementation | [src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionCompletionService.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionCompletionService.java) — publish ; committedProof ; interrupt | Transaction commune publication, terminal J8 et ordre. |
| JM-I11 | implementation | [src/main/java/com/bettingproject/sofascorelocal/domain/scheduledevents/J3AutomationData.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/domain/scheduledevents/J3AutomationData.java) — Settings ; Owner ; Order ; resolveOneShot | Types, identités, dates civiles/UTC et échéance. |
| JM-I12 | implementation | [src/main/java/com/bettingproject/sofascorelocal/domain/scheduledevents/J3CollectionData.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/domain/scheduledevents/J3CollectionData.java) — Trigger ; Proof ; Entry ; Collection | Résultats/provenance et catalogue par date. |
| JM-I13 | implementation | [src/main/java/com/bettingproject/sofascorelocal/port/J3AutomationStore.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/port/J3AutomationStore.java) — lead ; tick ; claim ; finish | Port de persistance des ordres, ticks et prises en charge. |
| JM-I14 | implementation | [src/main/java/com/bettingproject/sofascorelocal/port/J3CollectionStore.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/port/J3CollectionStore.java) — publish ; latest ; hasSuccess ; legacyCandidates | Port des collections, publication, lecture et reconstruction. |
| JM-I15 | implementation | [src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3AutomationStore.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3AutomationStore.java) — readSettings ; tick ; claim | Adaptateur SQL et sérialisation admission/configuration. |
| JM-I16 | implementation | [src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3CollectionStore.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3CollectionStore.java) — publish ; j3_last_success | Adaptateur de publication et dernier succès. |
| JM-I17 | implementation | [src/main/java/com/bettingproject/sofascorelocal/adapter/web/J3AutomationController.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/J3AutomationController.java) — collect ; importPages ; settings ; plan | Entrées HTTP vers cas d’usage et port, jeton local. |
| JM-I18 | implementation | [src/main/java/com/bettingproject/sofascorelocal/adapter/web/J3CollectionController.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/J3CollectionController.java) — latest ; page ; evidence | Consultation sans acquisition ; accès direct au port de lecture. |
| JM-I19 | implementation | [src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderCampaignFactory.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderCampaignFactory.java) — open ; openLiveGroupedV11 | Interface d’ouverture de campagne ; méthodes V11 explicites. |
| JM-I20 | implementation | [src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderSupervisor.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderSupervisor.java) — stopCampaign ; activeCampaignId | Interface de supervision indépendante de la bibliothèque Playwright. |
| JM-I21 | implementation | [src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java) — @Primary ; constructeur ; openJ3SubOperation | Composition Spring réelle et refus/départs durables. |
| JM-I22 | implementation | [src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java) — implements ; openLiveGroupedV11 ; openJ3Scope ; startWorker ; Campaign | Implémentation parent IPC et JVM enfant ; aucune bibliothèque navigateur dans src/main. |
| JM-I23 | implementation | [src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerMain.java](../../../../../src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerMain.java) — imports ; WorkerRuntime ; openJ3 ; closeJ3 ; open | Bibliothèque navigateur isolée ; deux contextes distincts lors de pause J3. |
| JM-I24 | implementation | [src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocol.java](../../../../../src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocol.java) — VERSION ; START_WITH_J3_PAUSE ; readJ3Scope | Protocole enfant v10, portée et validation des commandes. |
| JM-I25 | implementation | [src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java) — submitJ3 ; runJ3 ; j3Transition | Transfert au propriétaire live conservant sa lease et transitions persistantes. |
| JM-I26 | implementation | [src/main/java/com/bettingproject/sofascorelocal/application/live/LiveProviderSession.java](../../../../../src/main/java/com/bettingproject/sofascorelocal/application/live/LiveProviderSession.java) — openJ3SubOperation | Façade de session live vers sous-opération J3. |
| JM-T01 | test | [src/test/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightPropertiesTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightPropertiesTest.java) — acceptsOnlyTheClosedTcpPortRangeBoundaries | Matrice parente, validation directe et Bean Validation. |
| JM-T02 | test | [src/test/java/com/bettingproject/sofascorelocal/application/network/J3ProviderQualificationPolicyTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/application/network/J3ProviderQualificationPolicyTest.java) — rejectsAnOutOfRangeLoopbackPortBeforeAProviderClaim | Refus effectif de claim pour port hors plage. |
| JM-T03 | test | [src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocolTest.java](../../../../../src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocolTest.java) — acceptsOnlyTheClosedLoopbackOriginTcpPortRange | Matrice enfant et protocole sans Chromium. |
| JM-T04 | test | [src/test/java/com/bettingproject/sofascorelocal/integration/J3RuntimeIT.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/integration/J3RuntimeIT.java) — maintenanceContextCannotTakeLeadership ; admissionDeadlineCancels | Threads réels, PostgreSQL et fournisseur simulé ; maintenance et délai admission. |
| JM-T05 | test | [src/test/java/com/bettingproject/sofascorelocal/integration/J3AutomationPersistenceIT.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/integration/J3AutomationPersistenceIT.java) — concurrentClaimsCannotAdmitTwoJ3Runs ; scheduledTimesDuringStop | Concurrence, occurrences, horaires manqués et préférence. |
| JM-T06 | test | [src/test/java/com/bettingproject/sofascorelocal/integration/J3CollectionExecutionIT.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/integration/J3CollectionExecutionIT.java) — directImport ; cacheOnlySuccess ; terminalPublicationFailure | Exécution import/cache, borne page35 et publication atomique. |
| JM-T07 | test | [src/test/java/com/bettingproject/sofascorelocal/integration/J3CollectionPersistenceIT.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/integration/J3CollectionPersistenceIT.java) — failedAndRolledBackPublication ; historicalSuccess | Persistance par date, reprise, rétention et rollback. |
| JM-T08 | test | [src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/J3WorkerScopeProtocolTest.java](../../../../../src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/J3WorkerScopeProtocolTest.java) — trois tests | Capacité START distincte et identité/date/échéance sans navigateur. |
| JM-T09 | test | [src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/J3LivePauseWorkerQualificationIT.java](../../../../../src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/J3LivePauseWorkerQualificationIT.java) — oneWorkerIsolatesJ3ThenRestoresLiveAuthorityAndItsOriginalContext | Recette native loopback à sélection explicite ; pas incluse par motif générique. |
| JM-T10 | test | [src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisorSpringContextTest.java](../../../../../src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisorSpringContextTest.java) — SupervisorConfiguration | Contexte Spring restreint au superviseur, pas au décorateur @Primary complet. |
| JM-R01 | routing | [docs/skills/local-lab/ss-data-contract-replay/SKILL.md](../../../../../docs/skills/local-lab/ss-data-contract-replay/SKILL.md) — Déclenchement et procédure | Frontière contrats, parseurs et replay. |
| JM-R02 | routing | [docs/skills/local-lab/ss-postgres-change/SKILL.md](../../../../../docs/skills/local-lab/ss-postgres-change/SKILL.md) — Déclenchement et procédure | Frontière migrations, transactions et reprise. |
| JM-R03 | routing | [docs/skills/local-lab/ss-verify/SKILL.md](../../../../../docs/skills/local-lab/ss-verify/SKILL.md) — Choisir l’exécution | Frontière exécution standard et rapports. |

## Distinctions normatives et temporelles

1. **WO-030 concerne un port TCP.** Le mot « port » du rapport n’est pas une preuve de
   découplage architectural général. Le défaut historique est l’absence de borne 65535
   dans la configuration parente et enfant. Le rapport qualifie le commit 154349a2f…
   sans navigateur pour la matrice de bornes ; les suites complètes historiques ont une
   portée distincte. La décision propriétaire du WO est postérieure au rapport qui
   conservait encore OWNER_REVIEW_REQUIRED=YES. Sources JM-H01/H02, I05/I06/I07, T01/T03.
2. **ADR-SS-007 courant est v0.3.** AGENTS et l’encart d’architecture citent encore v0.2.
   La v0.3 complète le clic tournoi ; l’autorité des ordres J3 durables et l’isolation
   temporaire du contexte pendant la pause sont déjà adoptées en v0.2. Les sections
   anciennes de l’architecture qui disent J3 manuel, catalogue du processus ou IPC v5
   ne décrivent pas intégralement l’état courant (J3 durable, protocole 10, live-v11).
   Conserver l’historique, signaler la discordance, ne pas demander une nouvelle décision
   pour une exception déjà adoptée et ne pas l’étendre à J4/J5.
3. **Le Lab reste monomodule Maven.** Des responsabilités et des répertoires de sources
   différents ne sont pas des sous-modules Maven. `provider-playwright-runtime` ajoute
   dépendance/sources/tests/artefact worker et sorties séparées ; son activation ne lance
   pas un navigateur. Le profil Spring local et les profils Maven ne sont pas équivalents.
   La dépendance Playwright de qualification J7 est elle aussi explicite et de portée test.
4. **Le moteur J3 et l’accès fournisseur ont des conditions distinctes.** Profil local
   par défaut et runtime J3 activé par défaut n’arment pas tous les transports : propriétés
   fournisseur/qualification/Playwright restent séparées. L’événement exige un contexte
   Web doté de servlet et server.address=127.0.0.1 ; start vérifie runtime-enabled et
   local. Surefire/Failsafe forcent runtime-enabled=false sauf essai explicite simulé.
   Le démarrage réconcilie l’état durable avant les ticks ; ceux-ci ne recréent pas le live.
5. **Les frontières sont celles des fichiers réels.** Les interfaces Factory/Supervisor
   sont dans `application.network.playwright`, non dans `port`. Le superviseur concret
   se trouve lui aussi dans application ; il remplit un rôle d’adaptateur IPC/processus.
   ResilientPlaywrightProviderCampaignFactory est @Primary et compose le superviseur.
   J3CollectionExecutor dépend effectivement de SofascoreEndpointCatalog, de parseurs
   concrets et d’une exception de transport ; ne pas présenter un découplage parfait.
   Le domaine des ordres ne doit pas recevoir BrowserContext ou un superviseur concret.
6. **La persistance est observable sans redéfinir les migrations.** Completion publie
   preuve/catalogue/dernier succès, terminal J8 et ordre dans une transaction ; les
   adaptateurs JDBC matérialisent verrouillage et dernier succès par date. La revue
   d’architecture doit pointer cette frontière et relayer tout changement de schéma à
   ss-postgres-change, tout contrat/parseur/replay à ss-data-contract-replay.
7. **Les tests doivent être nommés et leur portée limitée.** J3RuntimeIT utilise de vrais
   threads et PostgreSQL, mais une factory fournisseur simulée ; les tests de protocole
   ne lancent pas Chromium. Le test Spring du superviseur importe seulement celui-ci,
   pas le décorateur @Primary complet. Verify-Local est un garde textuel ciblé : il
   interdit notamment com.microsoft.playwright dans src/main, mais ne prouve pas tout
   le graphe application → adapter. Aucun ArchUnit n’est déclaré dans le POM inspecté.
8. **La recette native J3 n’est pas automatiquement sélectionnée.** Le motif du profil
   est `**/*LocalQualificationIT.java`. J3LivePauseWorkerQualificationIT ne lui correspond
   pas. Le rapport WO-060 montre explicitement `-Dit.test=J3LivePauseWorkerQualificationIT`
   et les goals Failsafe nommés. C’est une sélection à reproduire si nécessaire, pas
   un test absent ni une preuve native du clean verify standard. La recette couvre
   transition d’autorité/contexte, pas toute l’admission ni toute la SQL.
9. **Une preuve historique ne qualifie pas cette préparation.** Aucun Maven, navigateur,
   PostgreSQL ou test applicatif n’est exécuté ici. Les validations du futur changement
   restent à exécuter dans leur contexte et sur leur révision exacte.

## Cas et frontières

[Huit cas figés](cases.json) : historique WO-030, nouvelle revue J3, deux contre-épreuves
et quatre sélections. Les [entrées](inputs.json) ne contiennent aucun corrigé.
L’[oracle](oracle.md) et le [plan](evaluation-plan.md) restent réservés au dispositif de revue.
Aucun candidat, installation, résultat comportemental ou modification applicative n’est produit.
Les fichiers d’autres skills, l’installateur et le runtime restent hors de cette préparation.
