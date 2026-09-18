# Revue JM-C02 — couverture et responsabilités J3

**Les cinq affirmations de couverture ne peuvent pas être retenues en l’état. Les quatre propositions de scénario contredisent le contrat J3 adopté ; le code examiné met en place les comportements opposés.** Cela établit des mécanismes et des contrôles présents dans les sources, sans constituer une qualification exécutée du candidat courant.

Le skill `ss-java-module` a été lu intégralement avec un outil local avant l’analyse. Cette revue porte uniquement sur les fichiers autorisés, consultés par sections pertinentes. Aucune source n’a été modifiée et aucun build, test, navigateur, accès réseau, base de données ou Docker n’a été lancé.

`input.json` qualifie les affirmations et propositions de **synthétiques**. Son `_policy_source_commit`, `6648dd423e556b5248b8793a539ae85a7680f9bc`, est une provenance déclarée de l’entrée : je ne l’assimile pas à un SHA courant ou testé, faute de vérification Git autorisée. Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY` restent applicables.

**Lecture des verdicts :**

- **Établi** : visible dans une décision, une implémentation ou les assertions d’un test consulté.
- **Contredit** : incompatible avec les sources examinées.
- **Non vérifié** : résultat d’exécution, composition ou garantie nécessitant des preuves supplémentaires.

## 1. Les cinq affirmations de couverture

| Affirmation | Verdict et éléments établis | Limite de preuve |
|---|---|---|
| **`standard-native`** — le `clean verify` standard sélectionne nécessairement `J3LivePauseWorkerQualificationIT` et prouve la conservation du contexte live | **Contredite.** Les sources de qualification sont ajoutées uniquement par `provider-playwright-local-qualification`. L’exécution Failsafe correspondante sélectionne `**/*LocalQualificationIT.java` ; le nom `J3LivePauseWorkerQualificationIT` ne correspond pas à ce motif. Le POM de base configure Failsafe sans lui attacher cette exécution. Voir [POM][pom], lignes 179–188 et 317–367. | La classe native contient des assertions pertinentes, mais sa présence ne prouve ni sa sélection standard ni son succès actuel. Le rapport du 14 septembre distingue explicitement sa commande native des tests standards, lignes 104–107. |
| **`generic-native-profile`** — les deux profils Playwright sélectionnent tous les `*QualificationIT` | **Contredite.** Le motif est **`*LocalQualificationIT`**, pas `*QualificationIT`. Ajouter les sources à la compilation et sélectionner les tests sont deux opérations distinctes. Le rapport utilise `-Dit.test=J3LivePauseWorkerQualificationIT` et les objectifs Failsafe de l’exécution nommée. Voir [POM][pom], lignes 330–360, et [rapport][rapport], ligne 107. | Sans cette sélection explicite, l’activation des deux profils ne démontre pas l’exécution de cette classe J3. |
| **`archunit-green`** — une règle ArchUnit verte interdit toutes les dépendances `application → adapter` | **Interdiction effective contredite ; règle verte non établie.** Le POM consulté ne déclare pas de dépendance ArchUnit. `Verify-Local.ps1` effectue des recherches textuelles ciblées, pas une analyse générale des dépendances. Surtout, `J3CollectionExecutor` importe des classes d’`adapter.sofascore`, reçoit `SofascoreEndpointCatalog` et instancie `ScheduledEventsV1Parser`. Voir [lanceur][verify], lignes 17–28 et 115–141 ; [exécuteur][executor], lignes 3–5, 32–35, 51 et 77. | Aucun test ArchUnit ni rapport d’exécution correspondant n’est fourni. L’absence de cette dépendance dans le POM lu ne constitue pas un inventaire exhaustif de fichiers non autorisés. **La revue des imports reste nécessaire.** |
| **`spring-primary`** — le test Spring du superviseur prouve l’injection du décorateur `@Primary` dans toutes les classes J3 | **Contredite.** `ApplicationContextRunner` charge une configuration réduite qui importe uniquement `ChildJvmPlaywrightProviderSupervisor`. Le test attend que les deux ports de factory et de supervision désignent **ce superviseur**. Le décorateur n’est pas importé. Voir [test Spring][springtest], lignes 15–40. | Le décorateur existe bien avec `@Primary @Component`, et son constructeur injecte concrètement le superviseur et `ProviderResilienceStore` : [factory résiliente][resilient], lignes 20–34. Cela ne démontre pas sa présence ni sa sélection dans un contexte applicatif complet. |
| **`historical-current`** — le rapport du 14 septembre suffit à qualifier le candidat courant | **Contredite.** Le rapport documente un worktree, une branche, une base, des commandes et des résultats historiques. Il renvoie à des manifestes et empreintes qui ne sont pas autorisés ici. Voir [rapport][rapport], lignes 6–19 et 42–54. | Aucun rapprochement des sources courantes avec les empreintes historiques n’est possible dans ce périmètre. Le rapport annonce notamment des résultats Failsafe pour `clean verify`, alors que le POM lu attache l’exécution d’intégration au profil dédié : ce point demande les preuves et la configuration effective de l’exécution historique, sans en déduire que le rapport serait faux. |

## 2. Les quatre scénarios

### 2.1 `retention-and-publication` — séparer catalogue, dernier succès et terminaux

**Verdict : proposition contredite.**

**Établi dans la conception et le code :**

- L’ADR impose la publication conjointe de la projection et de la preuve, ainsi que l’avancement de `last_success` dans la même transaction. Une panne avant commit doit préserver l’ancien succès ; une réponse de commit perdue impose une relecture par identité. Voir [ADR-SS-007][adr], §§4.1, 4.2 et 5.1, notamment lignes 176–199 et 261–270.
- L’exécuteur ferme l’accès fournisseur avant la publication terminale, puis appelle **un autre bean**, `J3CollectionCompletionService.publish`. La frontière transactionnelle n’est donc pas seulement une annotation placée sur une méthode appelée directement depuis le même objet. Voir [exécuteur][executor], lignes 127–143.
- `J3CollectionCompletionService.publish`, annotée `@Transactional`, appelle successivement la publication de collection, la clôture J8 lorsque la session d’audit est présente, puis la terminaison de l’ordre. Voir [service de clôture][completion], lignes 27–32.
- `JdbcJ3CollectionStore.publish` verrouille la publication, contrôle l’identité et les pages, vérifie la présence des octets bruts, écrit les entrées et leurs sources, termine la collection puis avance le pointeur **uniquement en cas de succès**. Voir [adaptateur des collections][jdbccollection], lignes 61–112.

Les pages peuvent être enregistrées progressivement pour l’audit. Cette conservation intermédiaire n’autorise pas la publication d’un nouveau dernier succès avant la transaction terminale.

**Contrôles présents :**

Dans [J3CollectionExecutionIT][executionit] :

- `directImportPublishesCompleteProofAndOrderWithoutTouchingProviderCache`, ligne 52 : cohérence du succès de collection, de l’ordre et de J8 ;
- `lostSuccessfulCommitResponseIsReconciledWithoutRepeatingTheCollection`, ligne 79 : exception simulée après publication committée, puis relecture sans répétition ;
- `terminalPublicationFailureRollsBackCatalogueAndJ8SuccessTogether`, ligne 145 : exception injectée après `orders.finish`, ancien succès conservé, absence de nouveau succès J8 et ordre final en échec.

**Encore non vérifié :**

Ces tests sont prévus pour PostgreSQL Testcontainers, mais n’ont pas été exécutés. Leur fixture et leur helper de proxy viennent de `J3CollectionPersistenceIT`, hors liste autorisée. L’implémentation de l’audit J8 et les migrations ne sont pas consultables non plus.

La coordination complète **publication/rétention** est décrite par le rapport, lignes 139–141, mais l’adaptateur de rétention et son test de concurrence sont absents du corpus autorisé. Le verrou `-6060` observé est explicitement partagé avec l’admission ; il ne suffit pas, à lui seul, à prouver le protocole de purge.

**Recommandation bornée :** conserver la transaction terminale dans `J3CollectionCompletionService` et les écritures/verrous dans les adaptateurs JDBC. Ne pas introduire deux commits indépendants. L’ADR prévoit une éventuelle réconciliation J8 non atomique seulement si elle est durable, idempotente et démontrée ; le scénario ne fournit aucune telle alternative.

### 2.2 `paused-live-restart` — fermer/recréer le contexte live et prolonger la campagne

**Verdict : les deux changements proposés sont contredits.**

**Établi :**

- L’ADR exige le **même navigateur, le même contexte live, la même échéance finale et les mêmes budgets**. J3 reçoit un contexte temporaire distinct ; une perte de contexte termine la session. Voir [ADR-SS-007][adr], lignes 317–336 et 352–385.
- `LiveCampaignService.submitJ3` borne l’échéance à la plus proche entre celle de l’ordre J3 et la fin initiale du live. Il enregistre la demande sous `dispatchLock`. Le propriétaire live traite ensuite J3 sur son propre chemin d’exécution. Voir [service live][live], lignes 71–93 et 775–788.
- Les nouveaux départs live sont refusés lorsque `s.j3 != null`. La reprise dépend de `safeToResumeLive`, de l’absence d’arrêt/dépassement et de la possession du garde. Voir le même fichier, lignes 148–168 et 992–1006.
- Le superviseur contrôle le thread propriétaire, l’identité de sous-opération, la date, l’endpoint, la progression des pages et l’échéance. Il attend un acquittement `J3_CLOSED` avant de retirer la portée J3. Voir [superviseur][supervisor], lignes 768–824.
- Le worker conserve une référence finale `liveContext`. `beginJ3` crée un second contexte ; `endJ3` ferme le temporaire, vérifie que le seul contexte restant est le contexte live conservé, puis lui restitue l’autorité. Le routage refuse les émissions du contexte inactif. Voir [worker][worker], lignes 242–306.

**Contrôles présents et portée exacte :**

- [J3WorkerScopeProtocolTest][protocoltest], lignes 11–28, est un **test pur de protocole** : capacité explicite, identité/date/échéance, rejet d’échéances invalides et d’identifiants non canoniques. Il ne démarre pas Chromium.
- [J3LivePauseWorkerQualificationIT][nativeit], méthode ligne 18, décrit une **qualification native loopback** : J4 → deux pages J3 → J4, même objet processus, refus de plusieurs requêtes hors portée, fermeture, absence de cookies dans les requêtes et intervalles minimaux.

La conservation du contexte live s’appuie sur les contrôles du worker et leur acquittement ; l’assertion « même processus » ne suffirait pas seule. Plusieurs requêtes invalides du test peuvent être rejetées par le parent : elles ne prouvent pas toutes une injection invalide directement reçue par le worker.

**Encore non vérifié :**

Aucune qualification native actuelle. La fixture native utilisée est hors périmètre. Ce test passe par le superviseur et ne qualifie pas, à lui seul, l’ordonnanceur live complet, l’injection du décorateur, les transactions SQL, les budgets ou tous les incidents pendant une pause.

**Recommandation bornée :** conserver la coordination de pause dans `LiveCampaignService`, les contrôles IPC dans le superviseur et le cycle de vie des contextes dans le worker. Reprendre dans la fenêtre initiale, après nettoyage confirmé ; arrêter si les garanties manquent.

### 2.3 `maintenance-ready` — démarrer sur tout `ApplicationReadyEvent` local

**Verdict : proposition contredite.**

[ `J3RuntimeService.onApplicationReady` ][runtime] vérifie trois conditions avant d’appeler `start()` :

1. un `WebApplicationContext` ;
2. un `ServletContext` présent ;
3. `server.address` égal à `127.0.0.1`.

`start()` vérifie ensuite séparément `sofascore.j3.runtime-enabled`, le profil Spring `local`, l’acquisition du rôle durable, la réconciliation des ordres interrompus et la reconstruction historique avant d’armer les ticks. Voir lignes 69–98. La qualification fournisseur et les gardes de transport sont encore d’autres conditions, lignes 124–137.

**Contrôle présent :** [J3RuntimeIT][runtimeit], `maintenanceContextCannotTakeLeadershipOrCollectEvenWithAllProviderFlagsEnabled`, lignes 91–101, fournit un `GenericApplicationContext`, attend `J3_WEB_APPLICATION_REQUIRED`, aucun ordre, aucun propriétaire durable et aucune interaction avec la factory ou le coordinateur.

**Encore non vérifié :**

Le test construit un runtime avec des dépendances simulées et PostgreSQL ; il ne démarre pas l’application Web complète. Les autres tests appellent directement `start()`, qui reste publique : ils ne prouvent pas le franchissement du garde événementiel. Les fichiers YAML, propriétés typées et configuration globale sont hors liste ; l’adresse réellement liée et tous les bindings ne sont donc pas attestés.

**Recommandation bornée :** garder le déclenchement automatique derrière le garde Web local. Tout nouveau point d’entrée doit préserver ces conditions ; le profil `local` seul ne constitue pas une admission.

### 2.4 `missed-order` — rejouer les horaires manqués et effacer les refus

**Verdict : proposition contredite sur ses deux volets.**

**Établi :**

- L’ADR interdit le rattrapage des échéances passées pendant arrêt, veille ou désactivation. Une opportunité quotidienne du jour reste distincte d’un horaire manqué. Il interdit aussi d’effacer une suspension fournisseur par redémarrage, clic ou planification. Voir [ADR-SS-007][adr], lignes 90–93 et 123–170.
- `JdbcJ3AutomationStore.tick` matérialise les jours absents en lots d’au plus 31, puis marque les occurrences concernées `MISSED`. L’admission dépend de la continuité de service ; la clé quotidienne évite une nouvelle tentative opportuniste consommée. Voir [adaptateur des ordres][jdbcautomation], lignes 118–164.
- `claim` sérialise l’exécution, vérifie désactivation et échéance et relit le succès pour les seuls ordres opportunistes `DAILY`. Un horaire explicite reste exécutable malgré un succès antérieur. Voir lignes 172–185.
- Le runtime bloque l’accès en état `SUSPENDED`, avec nettoyage requis, départ non résolu ou propriétaire étranger. Le décorateur vérifie aussi la suspension et transmet les refus 403/429 à `ProviderResilienceStore.suspend`. Voir [runtime][runtime], lignes 124–137, et [factory résiliente][resilient], lignes 75–100, 145–150 et 226–233.

**Contrôles présents :**

[J3AutomationPersistenceIT][automationit] contient notamment :

- `firstStartEnabledAndFailedDailyOpportunityIsNeverRetriedOnSameDate`, ligne 29 ;
- `fixedDailyDatesMissedDuringSeveralDaysOffAreRecordedWithoutReplay`, ligne 40 ;
- `scheduledTimesDuringStopOrDisableAreMissedAndNeverCaughtUp`, ligne 65 ;
- `successAfterDailyQueueingIsRecheckedWhileExplicitScheduleStillRuns`, ligne 54 ;
- `concurrentClaimsCannotAdmitTwoJ3Runs`, ligne 108.

**Encore non vérifié :**

Le motif `HTTP_FORBIDDEN` utilisé pour terminer un ordre dans un test ne démontre pas la persistance d’une suspension globale 403/429. L’adaptateur de résilience, ses tests et son comportement après redémarrage ne sont pas fournis.

**Recommandation bornée :** conserver les transitions/idempotences dans `J3AutomationStore` et son adaptateur, les observations de continuité dans le runtime, et les refus communs dans la frontière de résilience. Matérialiser les manqués pour l’audit sans les réarmer.

## 3. Placement des responsabilités à conserver

Le POM décrit **un projet Maven monomodule**, avec Java 25 et Spring Boot 4.1.0. Les répertoires de sources Playwright sont ajoutés par profil ; ils ne sont pas des sous-modules Maven. Voir [POM][pom], lignes 7–16, 33–41 et 250–315.

| Élément | Responsabilité et dépendances observées |
|---|---|
| `J3RuntimeService` | Entrée de cycle de vie, rôle durable, reconstruction, ticks, consommateur sériel, annulation et arbitrage vers le live ou une campagne autonome. Dépend des ports d’ordres/gardes/résilience, de l’exécuteur, de la factory et du service live. |
| `J3CollectionExecutor` | Parcours commun import/cache/fournisseur, pages bornées, preuve, projection et fermeture avant publication. **Dépendances concrètes existantes** au catalogue et au parseur d’`adapter.sofascore` ; elles doivent rester visibles dans la carte. |
| `J3CollectionCompletionService` | Transaction terminale entre collection/catalogue/pointeur, J8 lorsque la session existe et ordre ; relecture après commit incertain. |
| `J3AutomationStore` / `JdbcJ3AutomationStore` | Contrat durable et réalisation SQL des préférences, occurrences, leadership, admission et terminaux. Les verrous et contraintes d’exécution restent côté persistance. |
| `J3CollectionStore` / `JdbcJ3CollectionStore` | Preuves/pages, projections, références sources et dernier succès par date. |
| `LiveCampaignService` | Transfert temporaire du droit d’émettre sur le propriétaire live existant, transitions et décision de reprise. |
| Factory résiliente / superviseur | Le décorateur porte refus et admission partagée ; le superviseur porte la JVM enfant, l’IPC et les validations parentales. |
| Worker Playwright | Bibliothèque navigateur, création/fermeture des contextes, contrôles reçus par IPC et restrictions d’émission. |

**Modification minimale recommandée au dossier :** corriger les cinq formulations de couverture, retirer les quatre propositions incompatibles et ajouter une matrice explicite « comportement → test → sélection Maven → preuve d’exécution ». Aucune nouvelle interface ni refonte générale n’est justifiée par ce cas.

La séparation du runtime navigateur doit rester intacte : sorties standard `target/classes` et `target/test-classes`, sorties du profil sous `target/provider-playwright-runtime/…`, JAR worker produit à `package`. La compilation et le packaging ne constituent pas un lancement ; celui-ci passe par `open`, ses gardes, le démarrage de la JVM enfant puis la commande de démarrage du worker.

## 4. Vérification proposée — aucune commande exécutée

| Niveau | Sélection pertinente | Ce qu’il permettrait de vérifier |
|---|---|---|
| Standard | `.\mvnw.cmd clean verify` | Tests Surefire standard, dont le contexte réduit du superviseur. Aucune preuve native J3 à en déduire. Le lanceur ajoute son garde textuel et emploie précisément `-DskipITs clean verify`, puis `-Pintegration-tests verify` avec son option d’intégration : [lanceur][verify], lignes 144–155. |
| Intégration J3 | `.\mvnw.cmd -Pintegration-tests '-Dit.test=J3RuntimeIT,J3AutomationPersistenceIT,J3CollectionExecutionIT' verify` | PostgreSQL Testcontainers, orchestration avec transport simulé, transitions durables et publication. Ces classes correspondent au motif `**/integration/**/*IT.java` du POM. |
| Protocole pur du worker | `.\mvnw.cmd -Pprovider-playwright-runtime '-Dtest=J3WorkerScopeProtocolTest' test` | Validations du protocole en mémoire. Les sources viennent de `src/provider-playwright-test/java` ; aucune conservation réelle de contexte n’est mesurée. |
| Qualification native J3 | Commande explicite ci-dessous | Transition locale J4/J3/J4 avec worker natif, sélection exacte de la classe et configuration de l’exécution Failsafe dédiée. |

Sélection native documentée dans le rapport, à reprendre seulement dans un contexte d’exécution distinct autorisant cette qualification :

```powershell
.\mvnw.cmd -q '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-Dtest=J3WorkerScopeProtocolTest' '-Dit.test=J3LivePauseWorkerQualificationIT' package failsafe:integration-test@provider-playwright-loopback-qualification failsafe:verify@provider-playwright-loopback-qualification
```

Pour rendre ces contrôles probants, il faudra conserver le SHA et l’état des sources testées, les profils/propriétés effectifs, les rapports Surefire/Failsafe avec les classes réellement exécutées, les exclusions, ainsi que l’empreinte du JAR worker pour la qualification native. Les sorties séparées réduisent le risque de confondre un résidu de build de profil avec une preuve standard ; un état de build propre reste à établir.

Les compléments les plus ciblés sont :

- un contexte Spring représentatif vérifiant la sélection effective du décorateur dans les consommateurs concernés, transport rendu inerte ;
- la course publication/rétention et la protection du dernier succès ;
- la persistance des refus 403/429 après redémarrage ;
- les incidents de pause : perte du contexte live, fermeture J3 incertaine, arrêt concurrent et échéance traversée ;
- des tests directs de l’entrée worker pour les rejets que le parent peut intercepter.

**Conclusion :** les responsabilités existantes offrent une base cohérente pour rejeter les quatre propositions. La qualification du candidat courant reste **non vérifiée**. Les limites portent sur des preuves précisément identifiées ; aucun résultat historique, nom de test ou annotation Spring ne les remplace.

[pom]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/pom.xml:169
[rapport]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/docs/validation/WO060-J3-IMPLEMENTATION-20260914.md:42
[verify]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/scripts/Verify-Local.ps1:17
[executor]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java:32
[springtest]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisorSpringContextTest.java:15
[resilient]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java:20
[adr]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:62
[completion]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionCompletionService.java:17
[jdbccollection]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3CollectionStore.java:61
[executionit]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3CollectionExecutionIT.java:52
[live]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java:71
[supervisor]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java:768
[worker]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerMain.java:242
[protocoltest]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/J3WorkerScopeProtocolTest.java:11
[nativeit]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/J3LivePauseWorkerQualificationIT.java:18
[runtime]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:69
[runtimeit]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3RuntimeIT.java:91
[jdbcautomation]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3AutomationStore.java:133
[automationit]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3AutomationPersistenceIT.java:29