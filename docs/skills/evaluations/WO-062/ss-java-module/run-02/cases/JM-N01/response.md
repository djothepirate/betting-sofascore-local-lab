# Revue d’architecture — J3 durable et worker Playwright

**Conclusion : le parcours dispose de frontières identifiables entre ordre durable, exécution, publication et transport isolé. Une refonte en modules Maven n’est pas justifiée par les sources examinées.** Les principaux points à traiter sont plus ciblés :

1. **Une resoumission d’import en conflit peut supprimer les octets d’un ordre déjà en attente.**
2. **La publication nominale associe J3 et J8, mais certains chemins d’échec terminent J3 sans terminal J8.**
3. **La qualification native de la pause J3 existe, mais son nom ne correspond pas au motif d’inclusion du profil Maven : son exécution exige actuellement une sélection explicite.**
4. **La composition Spring complète et la validation des origines aux deux extrémités restent partiellement non vérifiables dans le corpus autorisé.**

Ces constats proviennent d’une **lecture statique**, sans nouvelle compilation, exécution de test ou qualification.

## 1. Périmètre et provenance

- **État demandé :** parcours J3 durable au 15 septembre 2026, de la soumission à la consultation, incluant la sous-opération dans un worker live.
- **Commit déclaré par `input.json` :** `6648dd423e556b5248b8793a539ae85a7680f9bc`. Cette identité n’a pas été vérifiée par Git.
- **Racine examinée :** le worktree `…/ss-java-module/run-02/JM-N01` fourni.
- **Méthode :** lecture intégrale du skill demandé, puis lectures et repérages locaux dans les seuls fichiers autorisés. Les références contenues dans ces fichiers n’ont pas été suivies hors de cette liste.
- **Statuts conservés :** `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

L’[ADR-SS-007][adr] courant est en **v0.3**. Il indique explicitement que les formulations prospectives du cadrage décrivent désormais le contrat adopté. L’exception J3 autorise donc les clics directs et les ordres durables concernés ; elle n’appelle pas une nouvelle confirmation pour analyser leur architecture.

L’en-tête d’[ARCHITECTURE.md][architecture] précise également que les descriptions ultérieures du J3 en mémoire et des politiques live antérieures sont historiques. Elles ne doivent pas remplacer la lecture du code actuel.

## 2. Modèle métier : les distinctions structurantes

Les deux classes de données séparent correctement plusieurs dimensions qu’il faut conserver dans tout changement.

| Dimension | Représentation actuelle | Conséquence |
|---|---|---|
| Déclencheur de la collecte | `J3CollectionData.Trigger` : `MANUAL_PROVIDER`, `MANUAL_IMPORT`, `DAILY`, `DAILY_AT`, `SCHEDULED`, `LEGACY` | Un ordre automatique peut être résolu intégralement en cache. Son déclencheur ne devient pas `CACHE`. |
| Source de chaque page | `J3MinimizedPageEvidence.resolutionSource()` ; branches fournisseur, cache et import dans l’exécuteur | La provenance est indépendante du déclencheur et peut différer d’une page à l’autre. |
| Date métier | `J3AutomationData.Order.date`, `J3CollectionData.Proof.date`, en `LocalDate` | Elle désigne le calendrier demandé et reste figée pendant l’exécution. |
| Temps d’exécution | `dueAt`, `createdAt`, `admittedAt`, `deadline`, `finishedAt` ; `startedAt` et heures de page | Une réception aujourd’hui ne prouve pas une collecte du calendrier d’aujourd’hui. |
| Identité de l’ordre | UUID, `occurrenceKey`, règle/révision et, pour l’import, `inputSha256` | Les doublons exacts et les conflits d’identité sont distingués. |
| Identité propriétaire | `Owner(UUID, pid, processStartedAt)` | Un PID seul ne suffit pas à identifier une ancienne instance. |
| Résultat consultable | `Proof`, `Entry`, `Collection`, `CatalogPage`, `DateSummary` | Le catalogue est rattaché à une collecte et à une date, avec ses sources ; les libellés ne constituent pas son identité. |

`J3AutomationData` fixe `Europe/Paris`, une borne d’ordre de **20 minutes** et un maximum de **100 planifications actives**. `resolveOneShot` refuse une heure inexistante et exige un offset valide lors d’une heure doublée. `dailyOccurrence` avance une heure inexistante à la transition et choisit la première occurrence lors d’un chevauchement.

`J3CollectionData.Proof` impose un terminal explicite, des pages contiguës et au plus 35 pages. Pour `COMPLETED`, chaque page doit être enregistrée, parsée, en HTTP 2xx, avec `hasNextPage=true` avant la dernière et `false` sur la dernière. Une liste de tournois vide peut donc correspondre à un succès complet.

**Limite de lecture :** les appels à `J3DatePolicy` sont visibles dans le runtime, mais son implémentation n’est pas autorisée. Les bornes calendaires détaillées du runbook sont documentées ; leur calcul interne n’a pas été relu.

## 3. Carte des classes, responsabilités et dépendances

Les noms ci-dessous désignent les éléments existants. Les collaborateurs dont le fichier n’est pas autorisé sont signalés comme tels.

### 3.1 Entrées, orchestration et stockage

| Élément exact | Responsabilité et dépendances observées |
|---|---|
| `adapter.web.J3AutomationController` | Entrées `/j3/collect`, `/import`, `/settings`, `/plans`, annulation, nettoyage et suivi d’ordre. Consomme `LocalFormTokenService`, appelle `J3RuntimeService` pour les commandes et `J3AutomationStore` pour le suivi. Valide les noms, le nombre, la taille et la continuité des fichiers importés. |
| `application.network.J3RuntimeService` | Propriétaire du moteur durable : démarrage, leadership, réconciliation, ticks, file sérielle, admission réseau, surveillance et nettoyage autonome. Dépend de `J3AutomationStore`, `J3CollectionExecutor`, `J3CollectionCompletionService`, des contrats de transport et de `LiveCampaignService`. |
| `port.J3AutomationStore` | Contrat des préférences, leadership, planification, soumission manuelle, tick, claim, terminal et consultation des ordres. |
| `adapter.persistence.JdbcJ3AutomationStore` | Implémente ce port avec `NamedParameterJdbcTemplate`. Porte aussi des décisions de politique durable : matérialisation des occurrences, priorité explicite, opportunité quotidienne, désactivation et expiration. Dépend de `J3CollectionStore` pour revérifier les succès. |
| `application.network.J3CollectionExecutor` | Chemin commun des pages : import, cache ou fournisseur, preuve brute, parsing, audit J8, projection puis publication. Dépend notamment de `RawManualCallSnapshotStore`, `J3ScheduledEventsPageCache`, `J3CollectionStore`, `J3CatalogProjector`, `J8BenchmarkAuditService` et `J3CollectionCompletionService`. |
| `J3CollectionExecutor.ProviderAccess` | Interface locale à l’application : exécuter une page avec un callback juste avant départ et fermer la ressource. Implémentée par `J3RuntimeService.Standalone`, par l’accès anonyme de `LiveCampaignService.runJ3` et par les doubles de test. |
| `application.network.J3CollectionCompletionService` | Frontière transactionnelle terminale : publication de collection, terminal J8 lorsqu’une session est fournie, puis terminal d’ordre. Propose aussi `interrupt` et `committedProof`. |
| `port.J3CollectionStore` | Contrat de création du run, ajout de pages, publication, interruption, dernier succès, pagination et reprise historique. |
| `adapter.persistence.JdbcJ3CollectionStore` | Implémente le stockage durable des runs, pages, entrées, sources et pointeurs de succès ; vérifie les identités et les références brutes lors de la publication. |
| `adapter.web.J3CollectionController` | Consultation et preuve minimisée. Dépend de `J3CollectionStore`, puis reçoit `J3AutomationStore` et `J3LivePauseStore` par injection de méthode. Les méthodes lues n’appellent aucune acquisition. |

Sources principales : [runtime][runtime], [exécuteur][executor], [publication][completion], [stockage des ordres][automation-store], [stockage des collections][collection-store].

### 3.2 Transport et pause live

| Élément exact | Responsabilité et sens de dépendance |
|---|---|
| `application.network.playwright.PlaywrightProviderCampaignFactory` | Contrat d’ouverture des campagnes. Les méthodes de versions non implémentées refusent explicitement avec `UnsupportedOperationException`. |
| `application.network.playwright.PlaywrightProviderSupervisor` | Contrat de supervision : `stopCampaign` et `activeCampaignId`. |
| `ResilientPlaywrightProviderCampaignFactory` | Décorateur `@Primary @Component`. Son constructeur Spring reçoit **le concret** `ChildJvmPlaywrightProviderSupervisor` et `ProviderResilienceStore`. Contrôle suspension, réservation de départ, refus 403/429 et clôture des réservations. |
| `ChildJvmPlaywrightProviderSupervisor` | `@Component` implémentant les deux interfaces précédentes. Possède la JVM enfant, l’IPC, l’état de campagne, les verrous de dispatch et la vérification du nettoyage de l’arbre de processus. |
| `application.live.LiveCampaignService` | Reçoit un `Order` et un callback `J3Work`. Enregistre la demande de pause, bloque les prochains départs live et fait exécuter J3 par le propriétaire live existant. Dépend notamment de `J3LivePauseStore`, du garde, de la factory et du superviseur. |
| `LiveCampaignService.J3Work` | Callback retournant `J3CollectionExecutor.Result` à partir d’un `ProviderAccess` et d’une annulation. Il évite d’injecter le runtime J3 dans le service live. |
| `application.live.LiveProviderSession` | Possède une `PlaywrightProviderCampaign`. Sélectionne notamment `openLiveGroupedV11` et délègue `openJ3SubOperation`. Ses validateurs conditionnels restent locaux à la campagne. |
| `provider.playwright.worker.ProviderPlaywrightWorkerProtocol` | Contrat binaire côté enfant, version 10 : validation des commandes et scopes, sérialisation des réponses et acquittements. |
| `provider.playwright.worker.ProviderPlaywrightWorkerMain` | Point d’entrée autonome de la JVM enfant. Lit sa configuration, se connecte à l’IPC, attend `START`, puis ouvre le runtime navigateur. Son `WorkerRuntime` privé possède Playwright, Chromium et les contextes. |

**Dépendances concrètes à conserver dans la carte :**

- `J3CollectionExecutor` reçoit `adapter.sofascore.SofascoreEndpointCatalog`, instancie `ScheduledEventsV1Parser` et intercepte `adapter.sofascore.transport.ScheduledEventsTransportException`. Il ne dépend donc pas uniquement de ports abstraits.
- `J3ScheduledEventsOutcomeProcessor`, `J3NetworkCircuit`, `J3CatalogProjector`, `J3LocalBatchValidator`, `J3LegacyRecoveryService` et `J3ProviderQualificationPolicy` sont des collaborateurs référencés, dont les implémentations ne sont pas dans la liste autorisée.
- Il existe un couplage de types entre les packages `network` et `live`. Dans les portions lues, il ne forme pas de cycle réciproque d’injection entre `J3RuntimeService` et `LiveCampaignService`.

## 4. Déroulement actuel et frontières d’exécution

### 4.1 Du Web ou de l’ordre durable à l’exécution

Le démarrage par événement suit cette séquence :

1. `onApplicationReady` exige un `WebApplicationContext`, un contexte servlet non nul et `server.address` égal à `127.0.0.1`.
2. `start` exige le profil Spring `local` et `sofascore.j3.runtime-enabled=true`.
3. `orders.lead` établit le propriétaire durable.
4. Les ordres d’anciens propriétaires prouvés absents sont interrompus ; `recovery.recover()` précède la décision quotidienne.
5. Le thread `j3-clock` exécute un tick toutes les 500 ms ; `j3-collection-owner` consomme sériellement les ordres.

Le garde Web se trouve dans `onApplicationReady`, **pas dans la méthode publique `start()`**. Les tests du runtime appellent directement cette dernière. Ils ne constituent donc pas une preuve de toutes les conditions de démarrage Web.  
([Runtime][runtime], L69–120 et L184–203.)

`JdbcJ3AutomationStore.tick` matérialise et admet les occurrences sous verrou. Les horaires devenus échus pendant une discontinuité sont marqués manqués. Les jours absents du mode à heure fixe sont matérialisés par lots de 31, sans recollecte de rattrapage. `claim` refuse un second ordre `RUNNING`, traite les expirations et revérifie le succès de la date pour `DAILY`.

Pour un import, les octets attendent dans une map mémoire de huit lots maximum. L’ordre et son empreinte sont durables ; **les octets en attente ne le sont pas**. Le redémarrage réconcilie l’ordre sans rejouer automatiquement l’import.

### 4.2 Exécution des pages

`J3CollectionExecutor.execute` :

- valide à nouveau l’import et son empreinte ;
- crée le run et démarre l’audit J8 ;
- traite les pages 1 à 35 ;
- utilise l’import directement, ou cherche une page en cache avant un départ fournisseur ;
- reparcourt le parser pour une page de cache admissible ;
- enregistre la preuve de page et résout l’unité J8 ;
- projette le catalogue seulement à la page terminale ;
- ferme `ProviderAccess` **avant** la publication terminale.

Le callback de départ contrôle notamment la marge de **130 secondes** avant l’échéance et démarre la tentative J8. Un retour fournisseur sans exécution de ce callback est rejeté par `J3_DISPATCH_PROOF_MISSING`.

L’exécuteur n’ouvre pas de transaction SQL englobant la boucle réseau. Les pages et preuves intermédiaires sont conservées progressivement ; le terminal est une autre frontière.

### 4.3 Autonome ou dans le live

Pour une collecte fournisseur, `reserveNetwork` essaie d’abord `live.submitJ3`. En l’absence de live, il acquiert la lease J3. La campagne autonome est ouverte paresseusement au premier défaut de cache.

Lors d’un live compatible :

- `submitJ3` installe la demande sous `dispatchLock` puis l’enregistre via `J3LivePauseStore.request`.
- Une réponse SQL incertaine conserve l’exclusion en mémoire et arrête le live.
- Les prochains départs J4/J5 voient `s.j3 != null` et sont refusés.
- Le propriétaire live achève le travail en cours puis appelle `runJ3`.
- Le consommateur J3 attend la `CompletableFuture` ; la lease ne passe pas au thread HTTP ou au consommateur J3.
- L’échéance de sous-opération est le minimum de l’échéance J3 et de la fin live, avec la marge d’admission de 130 secondes.

Les phases persistées appelées par le service sont `REQUESTED`, `QUIESCENT`, éventuellement `J3_ACTIVE`, puis `CLEANED`, `RESUMING`, `RESUMED`, ou `STOPPED`. Un résultat entièrement en cache peut ne jamais ouvrir de contexte J3.

`resumeAfterJ3` reçoit la responsabilité des slots manqués et du redémarrage de cadence. **Son implémentation n’est pas autorisée ici** : la reprise par nouveau J4 est documentée et partiellement qualifiée, mais le calcul complet de l’ordonnanceur n’a pas été audité.  
([LiveCampaignService][live], L71–170, L737–829 et L992–1008.)

### 4.4 Dans la JVM enfant

Le protocole 10 ajoute `START_WITH_J3_PAUSE`, `BEGIN_J3`, `GET_J3`, `END_J3`, `J3_READY` et `J3_CLOSED`.

Les validations sont présentes des deux côtés :

| Frontière | Contrôles directement visibles |
|---|---|
| Parent, ouverture du scope | Capacité live-v11, thread propriétaire, absence de scope concurrent, échéance future bornée à 20 minutes. |
| Parent, départ J3 | Scope exact, `SCHEDULED_EVENTS`, même date, page strictement croissante et ≤ 35, absence de validateur conditionnel, marge timeout/nettoyage. |
| Enfant, décodage | UUID canonique, date ISO canonique, échéance future ≤ 20 minutes ; commande de page avec bornes et champs compatibles avec son endpoint. |
| Enfant, admission J3 | Même UUID/date, endpoint J3, page croissante, marge temporelle. Les commandes live sont refusées pendant un scope J3. |
| Enfant, contexte | Un contexte live initial sans page ouverte ; création d’un contexte J3 neuf avec JavaScript, téléchargements et service workers désactivés. Les routes d’un contexte non actif sont bloquées. |
| Enfant, fermeture J3 | Fermeture du contexte temporaire, navigateur connecté, un seul contexte restant et identité exacte du contexte live initial, puis acquittement `J3_CLOSED`. |

Les numéros de pages réseau peuvent sauter en présence de cache ; la continuité de la **collecte complète** est imposée par la preuve finale.

Le parent reçoit l’acquittement de fermeture avant de retirer son scope. La fermeture finale du worker est distincte : le superviseur vérifie l’identité des processus et l’absence de descendants résiduels avant d’effacer `active`. Un acquittement rapide de `stopCampaign` ne vaut pas preuve de nettoyage.

**Limite précise sur les origines :** le parent passe `loopback-origin` depuis `ProviderPlaywrightProperties`. L’enfant appelle `ProviderPlaywrightWorkerConfiguration.fromEnvironment`, puis `uriFor`. Ces classes ne sont pas autorisées. La validation du schéma, de l’hôte exact, du **port TCP 1…65535**, du chemin, de la query, du fragment et du user-info ne peut donc pas être attestée ici. Le port IPC attribué par `ServerSocket` sur `127.0.0.1` est un sujet distinct.

## 5. Transactions : garanties et limites établies

### Publication nominale

`J3CollectionCompletionService.publish` est appelé depuis un autre bean et porte `@Transactional` :

```text
collections.publish(proof, entries)
    → audit.finishWithCollectionPublication(...) si audit présent
    → orders.finish(...)
    → commit
```

`JdbcJ3CollectionStore.publish` prend le verrou transactionnel PostgreSQL `-6060`, également utilisé par le stockage des ordres. Il verrouille le run, vérifie son identité, les preuves de pages et la présence des bruts, écrit les entrées et leurs sources, puis avance le dernier succès de la date.

Les appels internes à `appendPage` ou à `publish` depuis `interrupt` restent dans la transaction déjà ouverte ; leur auto-invocation n’annule pas cette transaction.

Les tests construisent explicitement les proxies transactionnels avec un `JdbcTransactionManager` et un même `DataSource`. Ils permettent de contrôler la participation JDBC à la transaction. **Ils ne chargent pas la composition transactionnelle complète de l’application Spring Boot.**

### Limite du terminal J8

Le chemin d’échec est moins fort que le nominal :

- après un échec de publication non retrouvé comme committé, l’exécuteur publie un terminal J3 `FAILED` avec `audit=null` ;
- `J3CollectionCompletionService.interrupt` ne termine que la collection et l’ordre ;
- `committedProof` compare l’état et le motif de l’ordre avec la collection, sans consulter J8.

Ce n’est pas une simple absence de test :  
`terminalPublicationFailureRollsBackCatalogueAndJ8SuccessTogether` vérifie que l’ordre devient `FAILED`, que l’ancien catalogue demeure et que **le nombre de terminaux J8 du run est zéro**.

**Verdict borné :** le rollback empêche un faux succès J8 et protège le dernier catalogue. La clôture ou la réconciliation ultérieure du J8 commencé n’est pas établie par les fichiers autorisés. Il serait excessif d’affirmer que tous les terminaux J3/J8 sont atomiquement complets.  
([Publication][completion], L17–32 ; [exécuteur][executor], L132–144 ; [test d’exécution][execution-test], L145–157.)

## 6. Activation Spring et Maven

| Niveau | Configuration observée | Portée |
|---|---|---|
| Projet | Un seul POM, Java 25, Spring Boot 4.1.0, version `0.1.0-rc.1-SNAPSHOT` | Monomodule Maven ; les répertoires de sources ne sont pas des sous-modules. |
| Wrapper | Maven 3.9.16, empreinte SHA-256 déclarée | Version déclarée dans le wrapper, sans résolution ni vérification binaire nouvelle. |
| Spring | Profil `local` par défaut, adresse `127.0.0.1` | Configuration déclarée, non mesure d’un serveur démarré. |
| Runtime J3 | `sofascore.j3.runtime-enabled=true` par défaut | Autorise le moteur sous ses autres conditions ; ne remplace pas la qualification fournisseur. |
| Fournisseur | `sofascore.enabled`, Playwright et qualification J3 désactivés par défaut | La préférence quotidienne durable ne suffit pas à armer le transport. |
| Tests | Surefire et Failsafe injectent `sofascore.j3.runtime-enabled=false` | Les harnais qui construisent eux-mêmes le runtime peuvent explicitement lui passer `true`. |
| Profil `provider-playwright-runtime` | Ajoute Playwright 1.62.0, les sources worker et les tests de protocole | Compile ce runtime ; son activation ne lance pas Chromium. |
| Packaging worker | Repackage avec classifier `provider-playwright-worker` et `ProviderPlaywrightWorkerMain` | Produit un artefact ; ne démarre ni Lab ni navigateur. |
| Sorties | Standard : `target/classes`, `target/test-classes` ; runtime : `target/provider-playwright-runtime/...` | Sépare les sorties et réduit le risque de prendre un résidu de profil pour une preuve standard. |
| Profil réel historique | `sofascore-live-test` bloqué par `alwaysFail` | Aucun déverrouillage implicite par cette revue. |

La factory injectée dans l’application est normalement le décorateur `@Primary`. Le constructeur Spring du superviseur est celui annoté `@Autowired`, avec `ProviderPlaywrightProperties` **et** `SofascoreProperties`, dont il tire le délai minimal.

Le test `ChildJvmPlaywrightProviderSupervisorSpringContextTest` importe seulement le superviseur et les deux classes de propriétés. Il prouve l’exposition des deux interfaces **dans ce contexte réduit** ; il ne vérifie ni la sélection du décorateur `@Primary`, ni son assemblage avec le runtime, ni les valeurs effectivement issues des YAML de production.

Les classes de propriétés étant hors corpus, la présence d’une clé YAML ne suffit pas à affirmer son binding complet.  
([POM][pom], L169–185 et L250–364 ; [superviseur][supervisor], L118–139 ; [test Spring][spring-test].)

## 7. Contrôles disponibles, modes d’exécution et preuves

**Aucun contrôle de cette section n’a été exécuté pendant la revue.** Les commandes indiquent les modes auxquels rattacher les contrôles lors d’une qualification future.

| Contrôle et méthodes représentatives | Mode et commande | Ce qu’il couvre ; limite |
|---|---|---|
| `ChildJvmPlaywrightProviderSupervisorSpringContextTest.springUsesTheProductionConstructorAndPublishesBothSupervisorPorts` | `src/test/java`, Surefire ; `.\mvnw.cmd clean verify` | Contexte Spring réduit, sans ouverture de worker. Ne couvre pas le décorateur ni le contexte Web complet. |
| `J3WorkerScopeProtocolTest` : `startCapabilityIsExplicitAndHistoricalStartDoesNotGrantIt`, `scopeKeepsExactIdentityDateAndDeadline`, `expiredOrExtendedScopesAndNonCanonicalIdentifiersAreRejected` | Source ajoutée par `provider-playwright-runtime`, Surefire ; `.\mvnw.cmd -Pprovider-playwright-runtime "-Dtest=J3WorkerScopeProtocolTest" test` | Décodage binaire pur, sans Chromium. Ne teste pas tout le cycle du worker face à des trames adverses. |
| `J3AutomationPersistenceIT` : `firstStartEnabledAndFailedDailyOpportunityIsNeverRetriedOnSameDate`, `successAfterDailyQueueingIsRecheckedWhileExplicitScheduleStillRuns`, `concurrentClaimsCannotAdmitTwoJ3Runs`, `liveProcessCannotLoseLeadershipToSecondInstance` | `src/test/java/.../integration`, Failsafe avec `integration-tests` | PostgreSQL Testcontainers réel ; préférences, unicité, claims concurrents, leadership. |
| `J3CollectionPersistenceIT` : `migrationPreservesPopulatedV54AndReappliesWithoutChange`, `lastSuccessfulCatalogueSurvivesServiceRestartAndCollectingAnotherDate`, `exactDeduplicatedOccurrenceIsRequiredAndTerminalPublicationIsIdempotent` | Même mode Failsafe | Upgrade prérempli, consultation stable, occurrence exacte et terminal idempotent. Les migrations elles-mêmes n’ont pas été lues. |
| `J3CollectionPersistenceIT.retentionWaitsForConcurrentJ3PublicationAndPreservesItsSources` | PostgreSQL réel, deux threads et verrou advisory | Attend réellement le verrou de publication. Le test précise que la page tournoi n’est déjà pas candidate à la purge avant publication : sa portée n’est pas un audit général de rétention. |
| `J3CollectionExecutionIT` : `directImportPublishesCompleteProofAndOrderWithoutTouchingProviderCache`, `cacheOnlySuccessReusesSourcesAndNeverDispatchesProviderRequest`, `page35WithMoreDataFailsWithoutDispatchingPage36` | PostgreSQL réel, réponses en mémoire ; Failsafe | Import/cache/pagination et preuves, sans Playwright ni HTTP fournisseur. |
| Même classe : `lostSuccessfulCommitResponseIsReconciledWithoutRepeatingTheCollection`, `terminalPublicationFailureRollsBackCatalogueAndJ8SuccessTogether` | Proxies transactionnels JDBC et fautes injectées | Perte de réponse après commit et rollback terminal. Le second scénario atteste aussi l’absence de terminal J8 après le repli. |
| `J3RuntimeIT` : `firstStartupCollectsOnceAndRestartWithOtherDatesKeepsEachLastSuccess`, `maintenanceContextCannotTakeLeadershipOrCollectEvenWithAllProviderFlagsEnabled`, `admissionDeadlineCancelsClaimedOrderWithoutOpeningProvider` | Threads runtime réels, PostgreSQL réel, qualification/coordinateur/live/transport simulés | Démarrage, restart, maintenance et borne de réservation 129/135 s. Le live est simulé ; ce n’est pas la pause complète avec worker. |
| `J3LivePauseWorkerQualificationIT.oneWorkerIsolatesJ3ThenRestoresLiveAuthorityAndItsOriginalContext` | Sources de qualification, JVM enfant et Chromium réels, serveur synthétique loopback ; commande explicite ci-dessous | J4 → deux pages J3 → J4, même worker, cookies absents, trois intervalles ≥ 3 s, nettoyage des descendants. Admission durable et SQL ne sont pas exercés. |
| `scripts/Verify-Local.ps1` | Script PowerShell : garde textuel puis `.\mvnw.cmd -DskipITs clean verify` ; avec `-WithIntegrationTests`, ajoute `.\mvnw.cmd -Pintegration-tests verify` | Recherche d’origines/imports/constructions interdits et de certains usages Playwright. Ce n’est pas une analyse exhaustive des dépendances. Son preflight référencé n’a pas été lu. |

Les quatre IT J3 listées sont sélectionnées par `**/integration/**/*IT.java` avec :

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

La commande native conservée dans le rapport historique est :

```powershell
.\mvnw.cmd -q `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-Dtest=J3WorkerScopeProtocolTest' `
  '-Dit.test=J3LivePauseWorkerQualificationIT' `
  package `
  failsafe:integration-test@provider-playwright-loopback-qualification `
  failsafe:verify@provider-playwright-loopback-qualification
```

**Écart d’inclusion établi :** le profil de qualification inclut `**/*LocalQualificationIT.java`. `J3LivePauseWorkerQualificationIT` ne correspond pas à ce motif. La commande historique le sélectionne par `-Dit.test` et vise l’exécution Failsafe nommée ; elle ne prouve pas son inclusion automatique dans un simple `verify` du profil.

Aucune règle ArchUnit n’est établie par le POM et les tests autorisés. Le marqueur `SOFASCORE_NETWORK_CALLS_EXECUTED=NO` imprimé par le script est une déclaration du lanceur, pas une mesure réseau autonome.

### Portée des preuves historiques

Le [rapport du 14 septembre][validation] déclare des PASS pour les deux commandes générales, avec 2 364 tests Surefire, cinq exclusions et 276 tests Failsafe. Il donne également une pause native de 9 290 ms et des intervalles de 3 164, 3 093 et 3 094 ms.

Ces valeurs restent **historiques** :

- les rapports et manifestes JSON référencés ne sont pas autorisés à la lecture ;
- leur correspondance exacte avec le commit déclaré du cas n’est pas établie ;
- le total Failsafe attribué à `clean verify` dans ce rapport ne démontre pas l’inclusion des IT dans le cycle standard du POM actuel, dont les exécutions correspondantes sont profilées.

Le rapport conserve aussi les incidents ayant conduit à des corrections : modification initiale du bytecode `LiveSchedule` invalidant des preuves V8/V9, événement de maintenance prenant potentiellement le rôle d’ordonnanceur, et menu J3 masqué dans le contexte Web complet. La suite attestée est respectivement l’introduction de `LiveSessionSchedule`, le garde Web de démarrage et la correction de consultation. Les tests ou implémentations hors liste ne sont pas requalifiés par cette revue.

## 8. Proposition minimale, risques et actions suivantes

### 8.1 Premier correctif recommandé : préserver un import déjà admis

**Constat directement déductible du code.** Dans `J3RuntimeService.manual` :

1. `imports.put(id, pages)` remplace le lot mémoire ;
2. `orders.manual` vérifie ensuite l’identité durable ;
3. si cet appel rejette le conflit, le `catch` exécute `imports.remove(id)`.

Avec un ordre original encore en attente, une nouvelle soumission du même UUID avec un contenu différent peut donc supprimer son lot. Lors de son exécution, l’ordre original rencontre `J3_IMPORT_BYTES_UNAVAILABLE`. Le refus d’une soumission invalide affecte ainsi une soumission précédemment acceptée.  
([Runtime][runtime], L144–161 et L214–218.)

**Modification minimale proposée :**

- préserver le lot existant tant que l’identité durable n’a pas été acceptée ;
- effectuer l’enregistrement mémoire et l’admission sous la section synchronisée existante ;
- limiter le nettoyage d’erreur au lot réellement ajouté par la soumission courante ;
- conserver la limite de huit lots et l’empreinte durable.

Aucun nouveau module ou port n’est nécessaire pour cette correction. Ajouter un scénario avec un premier ordre maintenu en attente, une resoumission conflictuelle et la vérification que l’original termine avec ses octets initiaux.

**Relais :** `ss-java-module` pour le changement local ; `ss-verify` pour sa qualification, notamment via le harnais runtime et PostgreSQL existant.

### 8.2 Rendre explicite le contrat de clôture J8 après panne

Le premier objectif est de décider et démontrer ce qui clôt un J8 commencé lorsque la publication nominale échoue ou que le propriétaire disparaît.

La responsabilité reste dans `J3CollectionCompletionService` et la persistance J8 existante. Une éventuelle réconciliation doit fonctionner **par identité de run**, être idempotente et s’appuyer sur les preuves déjà conservées. Les sources disponibles ne justifient pas de supposer qu’une ancienne `Session` mémoire reste réutilisable après rollback.

Les scénarios à compléter sont :

- échec avant commit puis terminal d’échec cohérent ;
- réponse perdue après commit ;
- interruption après création de l’audit ;
- absence de faux succès, absence de répétition fournisseur et sort explicite du terminal J8.

**Relais :** `ss-postgres-change` pour transaction/ledger et `ss-verify` pour les fautes injectées et rapports. Si le vocabulaire ou le format de preuve évolue, relais complémentaire `ss-data-contract-replay`.

### 8.3 Raccorder explicitement la qualification native J3

Ajouter au profil de qualification existant une inclusion précise de `J3LivePauseWorkerQualificationIT`, ou une exécution dédiée équivalente. Conserver cette qualification dans son mode explicite nécessitant le worker et Chromium local.

Le critère de fini doit vérifier le **rapport Failsafe de cette classe**, son nombre de tests et ses exclusions, pas seulement le succès global Maven.

**Relais :** `ss-java-module` pour le raccordement Maven ; `ss-verify` pour la commande effective et la conservation de la preuve.

### 8.4 Lacunes à lever dans une revue ultérieure autorisée

| Lacune | Risque ou portée non démontrée | Relais |
|---|---|---|
| `ProviderPlaywrightProperties`, `SofascoreProperties`, qualification J3 et configuration enfant non accessibles | Binding réel, origine exacte et bornes TCP non vérifiés. Aucun défaut précis de validation ne peut être affirmé. | `ss-java-module`, puis `ss-verify` pour les cas de configuration, dont ports 0 et 65536. |
| Test Spring limité au superviseur | Sélection effective du décorateur, assemblage Web et transaction manager de production non démontrés. | `ss-java-module` et `ss-verify`. |
| Tests natifs négatifs passant par l’API parente | Plusieurs refus sont arrêtés avant IPC ; ils ne prouvent pas tous les gardes enfant contre des trames forgées. | `ss-java-module` pour la frontière protocolaire, `ss-verify` pour les contrôles directs et la qualification native. |
| `J3LivePauseStore`, ses transactions et l’ordonnanceur V11 non accessibles | Persistance des phases, générations, budgets et calcul de reprise non audités de bout en bout. | `ss-postgres-change` pour le ledger ; `ss-java-module` et `ss-verify` pour l’ordonnancement. |
| Parser, projecteur, processeur de résultats et reprise historique non accessibles | Exhaustivité des contrôles de schéma, identité, contenu sensible et provenance non démontrée par la seule orchestration. | `ss-data-contract-replay`, puis `ss-verify`. |

**Décision proposée : conserver le découpage actuel et engager des corrections bornées sur l’idempotence d’import, la clôture J8 et l’inclusion de la qualification.** Les interfaces de campagne, la JVM enfant et le propriétaire live existant offrent déjà les points d’intervention utiles.

**État de livraison :** revue et proposition uniquement. Aucun fichier modifié ; aucune compilation, aucun test, aucun navigateur, aucune base ni aucun appel fournisseur exécuté. La présente note n’attribue aucun nouveau statut de qualification au commit déclaré.

[adr]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:3>
[architecture]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/docs/architecture/ARCHITECTURE.md:3>
[runtime]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:69>
[executor]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java:42>
[completion]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionCompletionService.java:17>
[automation-store]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3AutomationStore.java:133>
[collection-store]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3CollectionStore.java:61>
[live]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java:71>
[supervisor]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java:128>
[pom]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/pom.xml:169>
[spring-test]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisorSpringContextTest.java:15>
[execution-test]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/src/test/java/com/bettingproject/sofascorelocal/integration/J3CollectionExecutionIT.java:145>
[validation]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-N01/docs/validation/WO060-J3-IMPLEMENTATION-20260914.md:42>