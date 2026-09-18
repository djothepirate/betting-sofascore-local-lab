**Les cinq affirmations de couverture ne sont pas recevables en l’état, et les quatre scénarios proposés contredisent le contrat J3 adopté.** Les sources établissent des mécanismes et des contrôles précis ; elles ne fournissent aucune qualification exécutée du candidat courant dans cette revue.

J’ai lu intégralement le [skill `ss-java-module`][skill], puis uniquement les sections pertinentes des fichiers autorisés. L’[entrée du cas][input] qualifie explicitement ses affirmations et propositions de **synthétiques**. Son `_policy_source_commit`, `6648dd423e556b5248b8793a539ae85a7680f9bc`, constitue une provenance déclarée, pas une preuve que le worktree courant correspond à ce SHA. Aucun état Git ni rapport extérieur à la liste autorisée n’a été consulté.

Dans les verdicts suivants, **établi** désigne un élément visible dans les sources ; **non vérifié** désigne une exécution, une composition ou une garantie que ces seules sources ne permettent pas d’attester.

1. **`standard-native` — Contredit pour la sélection standard ; conservation native non vérifiée actuellement.**

   Le POM standard ne déclare ni les sources `src/provider-playwright/java`, ni les sources de qualification native. Elles sont ajoutées par les profils dédiés. Les configurations Surefire et Failsafe imposent également `sofascore.j3.runtime-enabled=false`. Le `clean verify` standard ne sélectionne donc pas nécessairement `J3LivePauseWorkerQualificationIT` et ne démontre pas la conservation d’un contexte Chromium. Voir le [POM][pom], lignes 169–187, 250–315 et 317–367.

   **Établi :** le test natif existe et décrit un parcours J4 → deux pages J3 → J4, avec même processus worker, exclusion du live pendant J3, fermeture du sous-contexte et absence de cookies reçus. Il réside sous `src/provider-playwright-qualification-test`, et le rapport historique donne une commande explicitement distincte des tests standards. Voir [J3LivePauseWorkerQualificationIT][native], méthode `oneWorkerIsolatesJ3ThenRestoresLiveAuthorityAndItsOriginalContext`, et le [rapport WO-060][report], lignes 102–128.

   **Limite :** l’égalité du processus worker ne suffit pas, seule, à établir l’identité du contexte navigateur. Cette identité est contrôlée dans `WorkerRuntime.beginJ3/endJ3`, puis acquittée par le protocole. Les champs constants du rapport produit par le test, comme `liveBrowserRecreated=false`, ne sont pas des mesures indépendantes. Aucun résultat actuel de cette qualification n’est fourni.

2. **`generic-native-profile` — Contredit par le motif Failsafe exact.**

   Les deux profils ajoutent les sources nécessaires, la dépendance Playwright et la production du JAR worker. Cependant, l’exécution Failsafe nommée `provider-playwright-loopback-qualification` inclut exactement :

   ```text
   **/*LocalQualificationIT.java
   ```

   `J3LivePauseWorkerQualificationIT` ne correspond pas à ce motif. Ajouter un répertoire de tests ne signifie pas sélectionner tous ses tests. Le rapport historique contourne explicitement cette différence avec `-Dit.test=J3LivePauseWorkerQualificationIT` et les deux goals Failsafe nommés. Voir le [POM][pom], lignes 330–361, et le [rapport][report], ligne 107.

   **Établi :** compilation du runtime, empaquetage du worker et lancement d’un navigateur sont trois étapes distinctes. Les sorties du profil runtime sont séparées dans `target/provider-playwright-runtime/classes` et `test-classes`, ce qui réduit le risque de prendre des résidus de compilation de profil pour une preuve standard.

   **Non vérifié :** aucune sélection effective ni exécution de ces profils sur le candidat courant. Le profil Maven `sofascore-live-test` reste volontairement bloqué par `alwaysFail` ; il n’active aucune collecte. Le profil `j7-browser-origin-loopback-qualification` possède sa propre sélection J7 et n’est pas une qualification J3. Voir le [POM][pom], lignes 369–450.

3. **`archunit-green` — Interdiction générale contredite ; existence d’une règle « verte » non établie.**

   Le POM lu ne déclare aucune dépendance ArchUnit, et aucun contrôle ArchUnit ni rapport correspondant n’est fourni dans le corpus autorisé.

   Surtout, [J3CollectionExecutor][executor] dépend effectivement de classes d’adaptateur :

   - `SofascoreEndpointCatalog`, injecté pour obtenir le TTL ;
   - `ScheduledEventsV1Parser`, instancié pour le traitement et la relecture du cache ;
   - `ScheduledEventsTransportException`, interceptée dans le cas d’usage.

   Ces dépendances apparaissent aux lignes 3–5, 32–35, 51, 77 et 98–101. Elles contredisent l’idée d’une interdiction totale déjà satisfaite de `application` vers `adapter`.

   **Le contrôle réellement visible** est le scan textuel de [Verify-Local.ps1][verify] : il recherche notamment `\bcom\.microsoft\.playwright\b` dans les fichiers `.java`, `.yml`, `.yaml` et `.properties` de **`src/main`**. Un contrôle distinct examine `src/provider-playwright` pour des usages interdits. Ce scan ne prohibe pas les interfaces internes nommées `Playwright…` et n’analyse pas exhaustivement les dépendances entre couches. Il n’a pas été exécuté ici.

4. **`spring-primary` — Contredit par le périmètre du contexte chargé.**

   [ChildJvmPlaywrightProviderSupervisorSpringContextTest][springtest] utilise un `ApplicationContextRunner` avec une configuration qui importe uniquement `ChildJvmPlaywrightProviderSupervisor` et active deux classes de propriétés. Sa méthode `springUsesTheProductionConstructorAndPublishesBothSupervisorPorts` vérifie que :

   - le contexte réduit peut être créé ;
   - le superviseur est unique ;
   - les ports `PlaywrightProviderCampaignFactory` et `PlaywrightProviderSupervisor` désignent **ce même superviseur**.

   Le test n’importe pas le décorateur résilient et ne charge aucune classe J3 consommatrice. Il ne prouve donc pas l’injection du décorateur dans le contexte complet.

   **Établi par lecture :** [ResilientPlaywrightProviderCampaignFactory][resilience] porte `@Primary @Component`. Son constructeur `@Autowired` reçoit le superviseur concret et `ProviderResilienceStore`, tandis que `J3RuntimeService` et `LiveCampaignService` reçoivent le port factory. Cette composition prévoit bien une décoration. **Non vérifié :** son assemblage effectif dans l’application complète, avec les propriétés et profils réellement chargés. Les constructions manuelles avec mocks de `J3RuntimeIT` ne comblent pas cette lacune.

5. **`historical-current` — Suffisance contredite ; qualification actuelle non établie.**

   Le [rapport du 14 septembre][report] déclare une qualification locale sur une branche, une base, un worktree et un environnement identifiés. Il rapporte 2 364 tests Surefire avec cinq exclusions, 276 Failsafe et une qualification Chromium explicite. Ces résultats restent **des résultats historiques rapportés**, dont les journaux et manifestes détaillés ne figurent pas parmi les fichiers autorisés.

   Une divergence exige précisément de relire les commandes : le tableau historique attribue des résultats Failsafe à `mvnw.cmd clean verify`, alors que le POM fourni place l’exécution d’intégration visible dans `integration-tests`. Le lanceur actuel appelle d’abord `mvnw.cmd -DskipITs clean verify`, puis, sur option, `mvnw.cmd -Pintegration-tests verify`. Le dossier autorisé ne permet pas de résoudre cette différence de configuration ou de provenance.

   Les limites historiques doivent aussi rester attachées au verdict :

   - les cinq exclusions Surefire ne sont pas des réussites ;
   - une première modification du bytecode `LiveSchedule` avait invalidé des preuves V8/V9 ; le rapport indique sa correction par `LiveSessionSchedule` ;
   - le démarrage depuis un contexte de maintenance, un menu Web masqué et la matérialisation des horaires manqués ont nécessité des corrections ;
   - la qualification native décrite porte sur le worker loopback, pas sur les quotas, la latence ou la capacité soutenue du fournisseur.

   Aucun journal autorisé ne permet d’auditer d’éventuelles tentatives de résolution de dépendances, de compilation ou leurs relances. Aucun incident supplémentaire ni succès courant ne peut être déduit.

Les quatre scénarios appellent les décisions suivantes.

1. **`retention-and-publication` — Proposition à rejeter : elle supprime une frontière transactionnelle requise.**

   **Établi :** l’[ADR-SS-007][adr], §§4.1–4.2 et 5.1–5.2, exige la publication cohérente du résultat complet, de la projection, du dernier succès et de la preuve terminale, ainsi que la coordination avec la rétention.

   Le chemin visible est concret :

   - `J3CollectionExecutor.execute` conserve les pages, construit la projection puis ferme `ProviderAccess` dans son `finally`, **avant** la publication terminale ;
   - il appelle le bean distinct `J3CollectionCompletionService.publish` ;
   - cette méthode `@Transactional` appelle successivement `collections.publish`, `audit.finishWithCollectionPublication` lorsque l’audit existe, puis `orders.finish` ;
   - `JdbcJ3CollectionStore.publish` verrouille l’admission/publication avec `pg_advisory_xact_lock(-6060)`, verrouille le run, vérifie les sources brutes avec `FOR SHARE`, écrit la projection et n’avance `j3_last_success` qu’en cas de succès.

   Voir [l’exécuteur][executor], lignes 127–144, le [service de complétion][completion], lignes 17–32, et [l’adaptateur de collections][collectionjdbc], lignes 61–112. La transaction terminale ne couvre pas toute la durée des échanges réseau ; la conservation progressive des pages reste distincte de la publication du succès.

   **Contrôles présents :** `J3CollectionExecutionIT.terminalPublicationFailureRollsBackCatalogueAndJ8SuccessTogether` injecte un échec après `orders.finish` et attend l’ancien succès intact, aucun terminal J8 de succès pour la tentative et un ordre final échoué. `lostSuccessfulCommitResponseIsReconciledWithoutRepeatingTheCollection` simule une réponse perdue après commit et vérifie l’absence de répétition. Voir [J3CollectionExecutionIT][executionit], lignes 79–96 et 145–157.

   **Non vérifié :** fonctionnement actuel de ces tests, configuration transactionnelle complète de production, contraintes des migrations et course réelle avec la purge. Les tests utilisent un helper `proxy` et une fixture provenant de `J3CollectionPersistenceIT`, fichier non autorisé. L’adaptateur de rétention et l’implémentation de l’audit J8 ne sont pas non plus accessibles. Le rapport historique décrit la course publication/purge ; cette description ne suffit pas à la requalifier aujourd’hui.

   **Placement recommandé :** conserver la transaction terminale dans `J3CollectionCompletionService`, les opérations SQL et verrous dans les adaptateurs derrière les ports, et la coordination publication/rétention dans leur frontière de persistance commune. **Relais : `ss-postgres-change`** pour transaction, contraintes et rétention ; **`ss-verify`** pour leur qualification.

2. **`paused-live-restart` — Proposition contredite sur la recréation et sur la prolongation.**

   L’[ADR][adr], §§6.1–6.3, impose le même worker, le même contexte live, un contexte J3 temporaire distinct et une seule autorité d’émission. L’échéance finale et les budgets live restent inchangés.

   **Établi dans le code :**

   - `J3RuntimeService.reserveNetwork` demande une sous-opération au propriétaire live ; le travail est exécuté sur ce propriétaire, qui conserve sa lease liée au thread.
   - `LiveCampaignService.submitJ3` borne l’échéance au minimum de celle de l’ordre et de la fin live. Le garde d’émission live refuse les nouveaux départs dès que `s.j3 != null`.
   - `runJ3` passe par `QUIESCENT`, éventuellement `J3_ACTIVE`, puis `CLEANED`, `RESUMING`, `RESUMED`, sous réserve de nettoyage, d’absence d’arrêt et de propriété du garde.
   - `WorkerRuntime.beginJ3` conserve `liveContext` et crée un contexte temporaire ; `endJ3` ferme ce dernier et exige que le contexte restant soit précisément `liveContext` avant de restituer l’autorité.

   Voir [J3RuntimeService][runtime], lignes 255–269, [LiveCampaignService][live], lignes 71–169 et 992–996, et [ProviderPlaywrightWorkerMain][worker], lignes 242–306.

   **La panne a une issue terminale distincte :** perte du navigateur/contexte live, refus global, nettoyage incertain ou interruption ne permettent aucune reprise ni création automatique d’un nouveau contexte live. Les branches d’erreur de `runJ3` publient `STOPPED` et arrêtent la session avec `STOPPED_ERROR` ou `STOPPED_INTERRUPTED` selon le chemin ; le nettoyage maintient l’exclusion si sa preuve manque. Au redémarrage, `markProvenOrphanWithoutRestart` réclame le nettoyage du garde, sans ouvrir de navigateur. Cela correspond à la transition vers `STOPPED` de l’ADR.

   **Contrôle présent et limite :** la qualification native autorisée décrit le retour nominal J4→J3→J4. Elle ne teste pas toutes les pannes de Chromium, les quatre familles live en vol, ni le ledger SQL de pause. Les contrôles supplémentaires cités par le rapport pour ces sujets sont hors liste autorisée ; la fixture native l’est également.

   **Placement recommandé :** conserver l’arbitrage dans le propriétaire `LiveCampaignService`, la supervision IPC dans `ChildJvmPlaywrightProviderSupervisor`, l’allocation/destruction des contextes dans le worker et la cadence dans le composant de planification existant. **Relais : `ss-verify`** pour les pannes, délais et qualifications natives ; **`ss-postgres-change`** pour les transitions durables et la génération du garde.

3. **`maintenance-ready` — Proposition directement contredite par le garde et son test.**

   Dans [J3RuntimeService][runtime], `onApplicationReady` refuse le démarrage si le contexte n’est pas un `WebApplicationContext`, si son `ServletContext` manque ou si `server.address` n’est pas exactement `127.0.0.1`. Il expose alors `J3_WEB_APPLICATION_REQUIRED`. Ensuite seulement, `start()` exige la propriété d’exécution et le profil Spring `local`, obtient le propriétaire durable, réconcilie les ordres interrompus, lance la reconstruction historique puis programme les ticks.

   **Contrôle présent :** [J3RuntimeIT][runtimeit], méthode `maintenanceContextCannotTakeLeadershipOrCollectEvenWithAllProviderFlagsEnabled`, crée un événement sur `GenericApplicationContext` et vérifie le motif de refus, l’absence d’ordre, l’absence de propriétaire SQL et l’absence d’interaction avec factory/coordinateur.

   **Non vérifié :** démarrage effectif d’un serveur Web complet, binding réel des configurations et tous les appels possibles à `start()`. Cette méthode reste publique et ne répète pas le garde Web ; les autres tests l’appellent directement avec un environnement simulé. Le test négatif de maintenance ne démontre donc pas à lui seul tous les chemins d’activation de production.

   **Placement recommandé :** conserver le garde de cycle de vie dans `J3RuntimeService` et tester la composition Web réelle avec transports simulés. Un durcissement éventuel de l’accès direct à `start()` doit rester ciblé sur ses appelants avérés. **Relais : `ss-verify`** pour configuration, contexte Spring et inertie de maintenance.

4. **`missed-order` — Proposition contredite sur le rattrapage et l’effacement des refus.**

   L’[ADR][adr], §§2 et 3.2–3.4, interdit le rejeu des échéances passées pendant l’arrêt, la veille ou la désactivation. Il interdit aussi l’effacement d’une suspension fournisseur par redémarrage, clic ou planification.

   **Établi :** `JdbcJ3AutomationStore.tick` matérialise les journées manquées par lots bornés et attribue `MISSED` avec notamment `LAB_NOT_IN_SERVICE` ou `AUTOMATION_DISABLED`. Les occurrences consommées restent identifiées ; `claim` revérifie le succès pour le seul opportunisme `DAILY`. Un horaire explicite admissible conserve son droit à exécution même si sa date possède déjà un succès. Voir [JdbcJ3AutomationStore][automationjdbc], lignes 133–185.

   Les refus sont une responsabilité séparée : `J3RuntimeService.providerUnavailableReason` bloque sur `SUSPENDED`, nettoyage requis ou départ non résolu. Le [décorateur résilient][resilience] interdit ouverture et départ lorsqu’une suspension existe, persiste les 403/429 et garde un refus en mémoire si son acquittement SQL échoue. Sa fermeture réessaie la **persistance locale du refus**, pas une collecte.

   **Contrôles présents :** [J3AutomationPersistenceIT][automationit] contient notamment `fixedDailyDatesMissedDuringSeveralDaysOffAreRecordedWithoutReplay`, `scheduledTimesDuringStopOrDisableAreMissedAndNeverCaughtUp` et `firstStartEnabledAndFailedDailyOpportunityIsNeverRetriedOnSameDate`. Le dernier vérifie l’absence de nouvelle tentative après un ordre échoué avec motif `HTTP_FORBIDDEN` ; il ne constitue pas un test complet de persistance du magasin de résilience.

   **Non vérifié :** conservation effective du refus fournisseur à travers un redémarrage de l’ensemble de l’application, notamment après un échec de son écriture. L’adaptateur de résilience et ses tests dédiés sont hors corpus.

   **Placement recommandé :** garder occurrences, préférences et admission derrière `J3AutomationStore`, et les suspensions/compteurs dans `ProviderResilienceStore` avec son décorateur. **Relais : `ss-postgres-change`** pour la durabilité et les courses ; **`ss-verify`** pour les scénarios de redémarrage et de refus.

La carte de responsabilités à conserver est la suivante. Elle décrit le **monomodule Maven existant**, compatible dans son POM avec Java 25 et Spring Boot 4.1.0 ; les répertoires de sources de profils ne sont pas des sous-modules. Le [document d’architecture][architecture], lignes 3–13, présente la mise à jour WO-060 et signale que les descriptions antérieures restent historiques.

| Élément | Responsabilité et dépendances observées |
|---|---|
| `J3RuntimeService` | Cycle de vie local, identité de propriétaire, ticks, consommateur sériel, admission manuelle/planifiée, réconciliation et réservation fournisseur. Dépend des ports durables, de la politique de qualification, du coordinateur, des ports factory/superviseur et de `LiveCampaignService`. |
| `J3CollectionExecutor` | Chemin commun import/cache/fournisseur, pagination 1–35, traitement, projection, audit et fermeture avant publication. Sa frontière `ProviderAccess` permet la substitution du transport. Les dépendances concrètes catalogue/parseur et l’exception d’adaptateur restent visibles. |
| `J3CollectionCompletionService` | Frontière transactionnelle terminale entre collection, projection, dernier succès, terminal J8 lorsqu’un audit existe et ordre. Relecture du résultat après réponse de commit incertaine. |
| [`J3CollectionStore`][collectionport] / `JdbcJ3CollectionStore` | Contrat de stockage et implémentation SQL des runs, pages, projections, références sources, derniers succès par date et consultation stable par identité. |
| [`J3AutomationStore`][automationport] / `JdbcJ3AutomationStore` | Contrat durable des préférences, occurrences, propriétaire, ticks, claims et terminaux ; sérialisation SQL des décisions concurrentes. |
| Factory résiliente / superviseur enfant | Le décorateur porte refus et admission des départs ; le superviseur possède la JVM enfant, l’IPC, l’exclusion d’une campagne active et les acquittements de fermeture. |
| Worker / protocole | La bibliothèque `com.microsoft.playwright` et les contextes restent dans les sources dédiées. Les commandes reçues sont revalidées côté worker : capacité explicite, UUID/date canoniques, échéance bornée, endpoint, page et autorité courante. |
| `LiveCampaignService` | Propriété de la session et de sa lease, transfert temporaire du droit d’émettre, transitions de pause et arrêt terminal en cas de panne. |

Les distinctions métier doivent rester explicites. Le **déclencheur** (`DAILY`, `DAILY_AT`, `SCHEDULED`, `MANUAL_PROVIDER`, `MANUAL_IMPORT`) n’est pas la **source de chaque page** (`PROVIDER`, `CACHE`, `LOCAL_JSON_IMPORT`). La **date cible**, définie dans le calendrier Europe/Paris, n’est ni l’échéance d’exécution ni les instants UTC de début, réception et fin. Un import est indépendant du transport et du cache ; une collecte fournisseur peut réussir entièrement depuis le cache. Le catalogue durable n’expire pas avec le TTL de ce cache. Ces distinctions sont décrites dans l’ADR et utilisées par l’exécuteur et les adaptateurs ; les définitions complètes `J3AutomationData` et `J3CollectionData`, non autorisées, n’ont pas été lues.

La recommandation est de **corriger les affirmations de couverture et de conserver ces frontières existantes**, sans refonte générale ni nouvelle interface de parseur imposée par cette revue. Les vérifications suivantes sont des travaux futurs ; aucune des commandes ci-dessous n’a été exécutée.

| Contrôle futur | Sélection et portée attendues | Relais |
|---|---|---|
| Standard et garde de sources | `.\mvnw.cmd clean verify` pour Surefire standard ; `.\scripts\Verify-Local.ps1` pour le scan textuel puis sa commande interne `-DskipITs clean verify`. Le scan ne vaut pas règle ArchUnit. | `ss-verify` |
| Ordres, runtime et publication | `.\mvnw.cmd -Pintegration-tests '-Dit.test=J3RuntimeIT,J3AutomationPersistenceIT,J3CollectionExecutionIT' verify`. Les trois fichiers sont sous `src/test/java/.../integration` et correspondent au motif Failsafe `**/integration/**/*IT.java`. Ils nécessitent PostgreSQL Testcontainers ; le transport fournisseur reste simulé. | `ss-verify`, avec `ss-postgres-change` pour les garanties SQL |
| Protocole seul | `.\mvnw.cmd -Pprovider-playwright-runtime '-Dtest=J3WorkerScopeProtocolTest' test`. Surefire sélectionne le test ajouté sous `src/provider-playwright-test`. Il vérifie le décodage et les bornes du scope, sans lancer Chromium. | `ss-verify` |
| Worker natif J3/live | Reprendre la commande explicite ci-dessous, avec sélection `-Dit.test` et exécution Failsafe nommée. Exiger les rapports, la liste de tests réellement exécutés et l’empreinte du JAR utilisé. | `ss-verify` |

```powershell
.\mvnw.cmd -q `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-Dtest=J3WorkerScopeProtocolTest' `
  '-Dit.test=J3LivePauseWorkerQualificationIT' `
  package `
  failsafe:integration-test@provider-playwright-loopback-qualification `
  failsafe:verify@provider-playwright-loopback-qualification
```

Les compléments prioritaires de qualification sont bornés :

- **Composition Spring complète avec transport simulé :** vérifier le décorateur effectivement injecté dans les consommateurs concernés, les profils/propriétés et le démarrage Web loopback. Relais **`ss-verify`**.
- **Transaction et rétention :** relire, dans un périmètre ultérieur autorisé, les migrations, l’audit J8, l’adaptateur de rétention et les fixtures ; qualifier rollback, commit à réponse perdue, concurrence publication/purge et références brutes protégées. Relais **`ss-postgres-change`**, puis **`ss-verify`**.
- **Pannes pendant la pause :** perte du worker ou du contexte live, fermeture J3 sans acquittement, arrêt opérateur, fin de fenêtre et refus persistant ; exiger une fin de session sans recréation automatique. Relais **`ss-verify`**, avec **`ss-postgres-change`** pour la preuve durable des transitions.
- **Contrats et provenance, si modifiés :** préserver pages exactes, identité, hash, parseur, réception et séparation brut/normalisé. Relais **`ss-data-contract-replay`**.
- **Validation des origines :** la liaison IPC du parent à `127.0.0.1` est visible, mais les classes de configuration de l’origine worker ne sont pas autorisées. La validation complète du schéma, de l’hôte, du port TCP dans `1..65535`, du chemin et des champs interdits avant émission reste à vérifier ; une URI structurée ne suffit pas. Relais **`ss-verify`**.

Seules des lectures locales et recherches textuelles ciblées ont été réalisées. Aucun fichier n’a été modifié ; aucun réseau, application, navigateur, build, test, DB, Docker ou CI n’a été lancé. Les statuts **`EXPERIMENTAL`**, **`LOCAL_ONLY`**, **`NOT_PRODUCTION_APPROVED`** et **`NO_CRITICAL_DEPENDENCY`** restent applicables.

[skill]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/.agents/skills/ss-java-module/SKILL.md
[input]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/input.json
[pom]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/pom.xml
[adr]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md
[architecture]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/docs/architecture/ARCHITECTURE.md
[report]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/docs/validation/WO060-J3-IMPLEMENTATION-20260914.md
[verify]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/scripts/Verify-Local.ps1
[runtime]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java
[executor]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java
[completion]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionCompletionService.java
[collectionjdbc]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3CollectionStore.java
[automationjdbc]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3AutomationStore.java
[collectionport]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/main/java/com/bettingproject/sofascorelocal/port/J3CollectionStore.java
[automationport]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/main/java/com/bettingproject/sofascorelocal/port/J3AutomationStore.java
[resilience]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java
[live]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java
[worker]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerMain.java
[springtest]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisorSpringContextTest.java
[native]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/J3LivePauseWorkerQualificationIT.java
[runtimeit]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3RuntimeIT.java
[executionit]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3CollectionExecutionIT.java
[automationit]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3AutomationPersistenceIT.java