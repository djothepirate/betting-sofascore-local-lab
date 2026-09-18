# Revue statique JM-C02 — couverture et cycle de vie J3

**Aucune des cinq affirmations de couverture n’est recevable telle quelle. Les quatre scénarios proposés contredisent le contrat J3 adopté.** Le code présente des mécanismes cohérents avec ce contrat, mais les sources disponibles ne permettent pas de déclarer le candidat courant qualifié.

## 1. Périmètre et valeur des preuves

Le [skill `ss-java-module`][skill] a été lu intégralement avec un outil local avant l’analyse. La revue utilise uniquement les fichiers autorisés, par sections pertinentes. Les seules commandes exécutées sont des lectures `Get-Content` et des recherches textuelles `rg`. **Aucun build, test, réseau, navigateur, application, PostgreSQL, Docker, installation ou changement de source n’a été exécuté.**

L’entrée [input.json][input] précise que les affirmations et propositions de JM-C02 sont **synthétiques**, sans changement appliqué ni résultat réel. Le SHA `6648dd423e556b5248b8793a539ae85a7680f9bc` y figure comme `_policy_source_commit` ; ce n’est pas une vérification du HEAD de ce répertoire. La révision Git effective, son état propre et sa correspondance avec les preuves historiques restent **non vérifiés**.

Dans cette revue :

- **Établi** signifie directement observable dans le code, la configuration ou les assertions lues.
- **Contredit** signifie incompatible avec une règle adoptée ou un mécanisme effectivement présent.
- **Non vérifié** signifie qu’il manque une source autorisée ou une preuve d’exécution.

Les statuts restent `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`. Le [POM][pom] déclare Spring Boot **4.1.0**, Java **25**, Maven **3.9.x** par Enforcer et la version `0.1.0-rc.1-SNAPSHOT`. Il décrit un **projet Maven monomodule** : les répertoires de sources Playwright ajoutés par profil ne sont pas des sous-modules.

## 2. Verdict sur chaque affirmation de couverture

### `standard-native` — Contredite

> Le `clean verify` standard sélectionnerait nécessairement `J3LivePauseWorkerQualificationIT` et prouverait la conservation du contexte live.

**Établi :**

- Le test natif appartient à `src/provider-playwright-qualification-test/java`, ajouté uniquement par le profil `provider-playwright-local-qualification`.
- Le runtime Playwright et ses sources sont ajoutés par `provider-playwright-runtime`.
- Le POM visible ne lie les objectifs Failsafe d’intégration au cycle qu’au travers des profils concernés. Surefire et Failsafe reçoivent `sofascore.j3.runtime-enabled=false`. Voir [pom.xml][pom], lignes 169–187, 222–367.
- L’interdiction de lancer un navigateur dans les tests standards demeure explicite dans [AGENTS.md][agents], lignes 54–62.

**Conclusion :** le succès d’un `clean verify` standard ne prouve ni l’exécution de cette qualification native, ni la conservation d’un contexte Chromium.

**Non vérifié :** toute exécution actuelle de cette qualification. Le test source et le compte rendu historique ont des valeurs probantes distinctes.

### `generic-native-profile` — Contredite

> Les deux profils Playwright sélectionneraient automatiquement tous les `*QualificationIT`.

Le motif Failsafe exact est :

```xml
<include>**/*LocalQualificationIT.java</include>
```

Il ne correspond pas à **`J3LivePauseWorkerQualificationIT`**. L’ajout d’un répertoire aux sources de test ne garantit pas la sélection de toutes ses classes. Voir [pom.xml][pom], lignes 330–361.

Le [rapport WO-060][rapport], ligne 107, utilise justement une sélection explicite :

```text
-Dit.test=J3LivePauseWorkerQualificationIT
```

avec les objectifs nommés `failsafe:integration-test@provider-playwright-loopback-qualification` et `failsafe:verify@provider-playwright-loopback-qualification`.

**Non vérifié :** les résultats que produirait aujourd’hui cette commande et l’identité du JAR worker effectivement exécuté.

### `archunit-green` — Non étayée ; conclusion architecturale contredite

> Une règle ArchUnit verte interdirait toutes les dépendances `application → adapter`.

**Aucune dépendance ArchUnit, règle correspondante ou preuve d’exécution n’est fournie dans le corpus lu.** Il serait excessif d’en déduire son absence dans tout fichier non autorisé ; il est en revanche impossible de l’annoncer « verte ».

Une dépendance actuelle contredit directement la séparation absolue alléguée :

- [J3CollectionExecutor.java][executor], lignes 3–5, importe le catalogue concret `SofascoreEndpointCatalog`, les classes de parsing `adapter.sofascore.scheduledevents.*` et l’exception d’adaptateur `ScheduledEventsTransportException`.
- Son constructeur injecte le catalogue concret pour obtenir le TTL ; `execute` construit `ScheduledEventsV1Parser` et traite l’exception de transport aux lignes 32–35, 51, 77 et 98–101.

Le [script Verify-Local.ps1][verify] effectue des **contrôles textuels ciblés** : imports Playwright interdits dans `src/main`, constructions de clients HTTP, anciens transports, options interdites du worker. Ce n’est pas une analyse générale des dépendances entre couches, et aucune exécution de ce script n’est attestée ici.

**Conclusion :** la revue des imports reste nécessaire. Les dépendances concrètes doivent apparaître dans la carte architecturale.

### `spring-primary` — Contredite quant à la portée du test

> Le test Spring prouverait l’injection du décorateur de résilience dans toutes les classes J3 du contexte complet.

[ChildJvmPlaywrightProviderSupervisorSpringContextTest.java][springtest] utilise un `ApplicationContextRunner` réduit :

- configuration de deux classes de propriétés ;
- `@Import(ChildJvmPlaywrightProviderSupervisor.class)` uniquement ;
- assertions selon lesquelles les deux ports, `PlaywrightProviderCampaignFactory` et `PlaywrightProviderSupervisor`, désignent **le superviseur lui-même**.

Il ne charge ni le décorateur ni toutes les classes J3.

**Établi séparément :** [ResilientPlaywrightProviderCampaignFactory.java][resilient], lignes 20–39, porte `@Primary @Component`, implémente la factory et reçoit le superviseur concret dans son constructeur `@Autowired`. `J3RuntimeService` et `LiveCampaignService` demandent une factory par interface.

**Non vérifié :** le choix effectif du décorateur dans le contexte applicatif complet, le binding réel des propriétés et l’absence de contournement dans tous les consommateurs. Les annotations expriment la composition prévue ; ce test réduit ne la qualifie pas.

### `historical-current` — Contredite

> Le rapport du 14 septembre suffirait à qualifier le candidat courant.

Le [rapport WO-060][rapport] conserve une qualification historique avec branche, base, versions, commandes, exclusions et limites. Il annonce notamment :

- 2 364 tests Surefire, dont cinq exclusions explicites ;
- 276 tests Failsafe ;
- une qualification native distincte J4 → deux pages J3 → J4 ;
- une pause mesurée de 9 290 ms dans cette exécution historique.

Ces valeurs **ne sont pas des résultats de la présente revue**.

Deux réserves concrètes empêchent leur transposition :

1. Les manifestes, journaux et empreintes auxquels le rapport renvoie ne sont pas autorisés à la lecture ici. Le SHA exact du candidat testé et sa correspondance avec les sources actuelles ne sont donc pas établis.
2. Le rapport attribue 276 tests Failsafe au simple `clean verify`, alors que le POM lu place les exécutions concernées dans des profils non activés par défaut. **Cette discordance nécessite la commande effective et son contexte de profils**, sans conclure que le rapport historique est faux.

Les incidents documentés restent partie intégrante du bilan : modification initiale de `LiveSchedule` invalidant deux preuves V8/V9, correction par `LiveSessionSchedule`, démarrage intempestif possible depuis la maintenance, menu Web masqué et matérialisation corrigée des horaires manqués. Voir le rapport, lignes 144–159. Leur correction déclarée historiquement ne dispense pas de vérifier le candidat courant.

## 3. Verdict sur chaque scénario

### `retention-and-publication` — Proposition contredite

**Contrat établi.** L’[ADR-SS-007][adr], §§4.1, 4.2 et 5.1, exige la publication commune du résultat complet, du catalogue et du dernier succès. Le terminal J8 doit être lié atomiquement, ou bénéficier d’une réconciliation durable idempotente démontrée. La rétention doit protéger les sources exactes du dernier succès.

**Chemin actuel observable :**

1. `J3CollectionExecutor` conserve les pages au fil du parcours, produit la projection puis **ferme l’accès fournisseur avant la publication terminale**.
2. Il appelle le bean distinct [J3CollectionCompletionService][completion].
3. `publish`, annotée `@Transactional`, appelle successivement `collections.publish`, `audit.finishWithCollectionPublication` si l’audit existe, puis `orders.finish`.
4. [JdbcJ3CollectionStore.publish][collectionjdbc] prend le verrou transactionnel `-6060`, verrouille le run, contrôle les preuves et la présence des octets bruts, écrit catalogue et sources, puis avance `j3_last_success` uniquement pour un succès.
5. En cas de réponse de commit incertaine, l’exécuteur relit le terminal via `committedProof` ; il ne relance pas la collecte.

**Contrôles présents :** dans [J3CollectionExecutionIT][executiontest] :

- `directImportPublishesCompleteProofAndOrderWithoutTouchingProviderCache` vérifie catalogue, ordre et terminal J8 ;
- `terminalPublicationFailureRollsBackCatalogueAndJ8SuccessTogether` injecte un échec après `orders.finish` et attend la conservation du succès précédent ;
- `lostSuccessfulCommitResponseIsReconciledWithoutRepeatingTheCollection` simule une réponse perdue après publication et attend une seule publication/occurrence.

**Limites importantes :**

- Ces tests construisent explicitement des proxies transactionnels et utilisent une fixture définie dans `J3CollectionPersistenceIT`, fichier non autorisé. Leur exécution actuelle et tout le montage de la fixture restent non vérifiés.
- L’implémentation de l’audit J8, les migrations et l’adaptateur de purge sont hors corpus. Le verrou partagé publication/rétention et ses contraintes sont **déclarés historiquement**, pas intégralement démontrés par les fichiers lus.
- Le test de rollback attend **zéro terminal J8** pour la tentative dont la publication échoue, puis un ordre `FAILED`. Il prouve l’absence de faux succès J8 ; il ne prouve pas que chaque échec possède déjà un terminal J8 réconcilié. `committedProof` compare collection et ordre, sans vérifier J8.

**Recommandation :** conserver la frontière terminale existante. Séparer durablement catalogue et pointeur introduirait une fenêtre d’incohérence et une course avec la rétention. Relais : **`ss-postgres-change`** pour transaction/ledger/rétention, puis **`ss-verify`** pour les preuves.

### `paused-live-restart` — Proposition contredite

Fermer puis recréer le contexte live et prolonger sa fin viole explicitement l’[ADR-SS-007][adr], §§6.1–6.3 : **même contexte live, même échéance finale, mêmes budgets**, sans reprise après perte du contexte.

**Mécanismes présents :**

- [LiveCampaignService][live] borne la sous-opération par le minimum entre l’échéance J3 et la fin live initiale ; la demande bloque les nouveaux départs live sous `dispatchLock`.
- Le propriétaire live exécute la sous-opération en conservant sa lease. `runJ3` exige fermeture, résultat compatible avec la reprise, garde encore détenu et échéance valide.
- Le watchdog demeure actif pendant J3 et arrête la campagne en cas de rupture temporelle.
- Le [superviseur parent][supervisor] contrôle thread propriétaire, identité/date du scope, endpoint, progression des pages et délai ; il attend les acquittements `J3_READY` et `J3_CLOSED`.
- Le [worker][worker] conserve `liveContext`, crée un contexte J3 temporaire, refuse les routes du contexte inactif, ferme J3 puis vérifie que le contexte restant est précisément `liveContext`. Il ne recrée pas celui-ci.
- Le [protocole][protocol] distingue `START` de `START_WITH_J3_PAUSE` et valide aussi le scope côté worker.

**Contrôles présents :**

- [J3WorkerScopeProtocolTest][protocoltest] : capacité explicite, identité/date/échéance, rejet des scopes expirés ou étendus et des identifiants non canoniques.
- [J3LivePauseWorkerQualificationIT][nativetest], méthode `oneWorkerIsolatesJ3ThenRestoresLiveAuthorityAndItsOriginalContext` : même processus worker, quatre requêtes attendues, absence de cookies envoyés, refus des mauvais scopes et de l’ancien groupe, intervalles ≥ 3 secondes, fermeture des descendants.

**Non vérifié :** exécution native actuelle, intégration complète avec `LiveCampaignService`, SQL et résilience. Le test natif appelle directement le superviseur ; il ne qualifie pas ces couches. Plusieurs requêtes invalides peuvent être rejetées par le parent avant d’atteindre le worker : elles ne constituent pas toutes un test d’entrée IPC hostile. Les assertions de reprise du calendrier et des budgets sont rapportées historiquement, mais leurs tests détaillés sont hors corpus.

**Recommandation :** préserver cette répartition propriétaire live → scope IPC → contexte temporaire. Relais : **`ss-verify`** pour cycle de vie/protocole ; **`ss-postgres-change`** pour les transitions durables.

### `maintenance-ready` — Proposition contredite

[ J3RuntimeService.onApplicationReady ][runtime] exige :

- un `WebApplicationContext` ;
- un `ServletContext` présent ;
- `server.address` exactement égal à `127.0.0.1`.

Ensuite, `start()` vérifie séparément le profil Spring `local` et `sofascore.j3.runtime-enabled`, acquiert le rôle durable, réconcilie les opérations interrompues et reconstruit l’historique avant d’activer les ticks et le consommateur sériel.

La disponibilité fournisseur reste une autre condition : qualification, suspension persistante, nettoyage, propriétaire et état live sont contrôlés séparément. **Profil local, préférence activée et transport disponible ne sont pas équivalents.**

Dans [J3RuntimeIT][runtimetest], `maintenanceContextCannotTakeLeadershipOrCollectEvenWithAllProviderFlagsEnabled` envoie un événement depuis un `GenericApplicationContext` et attend :

- `J3_WEB_APPLICATION_REQUIRED` ;
- aucun ordre ;
- aucun propriétaire durable ;
- aucune interaction avec factory ou coordinateur.

**Limite :** ce test emploie PostgreSQL et des objets simulés, pas le contexte Spring complet. De plus, `start()` reste public et les tests l’appellent directement : la garde Web est établie sur le chemin événementiel, pas sur tous les appelants possibles.

**Recommandation :** garder l’admission du runtime dans ce service, après disponibilité Web. Un éventuel resserrement de la visibilité de `start()` demande d’abord un inventaire autorisé de ses appelants. Relais de vérification : **`ss-verify`**.

### `missed-order` — Proposition contredite

L’[ADR-SS-007][adr], §§3.2–3.4 et 5.1, interdit le rattrapage des horaires manqués et l’effacement des refus fournisseur au redémarrage.

**Code établi :**

- [JdbcJ3AutomationStore.tick][automationjdbc] matérialise les jours absents par lots bornés, puis marque les occurrences `MISSED` lorsque la continuité de service manque, que l’automatisation est désactivée ou que le fournisseur est indisponible.
- La matérialisation des absences crée une trace ; elle ne rejoue pas les collectes.
- `claim` sérialise les admissions, refuse un second run actif et revérifie le succès pour les seules opportunités `DAILY`.
- Un horaire explicite reste distinct : un succès préalable de la date ne suffit pas à l’annuler.
- La [factory résiliente][resilient] bloque sur `SUSPENDED`, conserve un refus en attente si sa persistance échoue et persiste les 403/429. Sa fermeture ne libère la réservation qu’après fermeture du transport et traitement du refus.
- Le runtime relit la suspension et ne contient pas de réarmement automatique.

**Contrôles présents dans [J3AutomationPersistenceIT][automationtest] :**

- `firstStartEnabledAndFailedDailyOpportunityIsNeverRetriedOnSameDate` ;
- `fixedDailyDatesMissedDuringSeveralDaysOffAreRecordedWithoutReplay` ;
- `scheduledTimesDuringStopOrDisableAreMissedAndNeverCaughtUp` ;
- `successAfterDailyQueueingIsRecheckedWhileExplicitScheduleStillRuns` ;
- tests de priorité, claims concurrents et propriétaire unique.

**Non vérifié :** la persistance réelle d’une suspension fournisseur à travers un redémarrage complet. Enregistrer `HTTP_FORBIDDEN` comme motif d’un ordre, comme le fait un test, ne prouve pas la conservation de l’état global de `ProviderResilienceStore`.

**Recommandation :** conserver les occurrences terminales et les refus. Relais : **`ss-postgres-change`** pour unicité, redémarrage et suspension durable ; **`ss-verify`** pour l’exécution des scénarios.

## 4. Placement borné des responsabilités

| Élément existant | Responsabilité à conserver |
|---|---|
| `J3RuntimeService` | Disponibilité Web, rôle durable, reconstruction préalable, ticks, consommateur sériel, annulation et choix entre accès autonome et propriétaire live. |
| `J3CollectionExecutor` | Parcours commun import/cache/fournisseur, pagination bornée, preuves de pages, projection, fermeture de l’accès et demande de publication terminale. |
| `J3CollectionCompletionService` | Frontière transactionnelle courte pour collection, terminal J8 disponible et ordre ; relecture après commit incertain. |
| [Ports `J3AutomationStore`][automationport] et [`J3CollectionStore`][collectionport] | Contrats d’ordres, d’admission, de publication et de consultation ; leurs adaptateurs JDBC portent les verrous et opérations SQL. |
| `LiveCampaignService` | Transfert temporaire du droit d’émettre sur le thread propriétaire live, phases de pause, limites et décision de reprise. |
| Factory résiliente | Admission des départs, pression commune et refus persistants autour du superviseur. |
| Superviseur / protocole / worker | JVM enfant et IPC côté parent ; validation des commandes aux deux frontières ; propriété et nettoyage des objets Chromium côté worker. |

Trois distinctions doivent rester explicites :

1. **Déclencheur ≠ source de page.** Un ordre `DAILY` ou `SCHEDULED` peut consommer du `CACHE` et du `PROVIDER`. L’import garde `LOCAL_JSON_IMPORT`. Il ne faut pas encoder le déclencheur dans un mode d’acquisition.
2. **Date cible ≠ instant d’exécution.** La date métier Europe/Paris reste figée ; échéance, début, fin et réception sont des instants distincts. L’ADR et les usages le montrent ; les définitions complètes de `J3AutomationData` et `J3CollectionData` ne sont pas autorisées ici.
3. **Identité ≠ libellé.** Catalogue et pagination restent liés à `collectionId`, date et `tournamentId`. L’adaptateur utilise l’identité de tournoi et un tri déterministe ; un nom variable ne doit pas devenir une clé.

La dépendance de l’exécuteur au catalogue, au parseur concret et à l’exception d’adaptateur est une **dette visible**, pas une séparation déjà imposée. La revue ne justifie ni nouveau module Maven ni refonte générale. `ProviderAccess` constitue déjà une frontière de substitution utile. Une évolution du parsing ou de la provenance relèverait de **`ss-data-contract-replay`**.

## 5. Vérification proposée — aucune commande exécutée

Les contrôles suivants sont des actions futures pour **`ss-verify`**.

| Contrôle | Sélection et portée |
|---|---|
| Socle standard | `mvnw.cmd clean verify` ; aucune preuve Chromium à en déduire. Le script `Verify-Local.ps1` utilise précisément `-DskipITs clean verify`, puis éventuellement `-Pintegration-tests verify`. |
| Composition réduite existante | `mvnw.cmd '-Dtest=ChildJvmPlaywrightProviderSupervisorSpringContextTest' test` ; Surefire, `src/test/java`. |
| Trois IT J3 du corpus | `mvnw.cmd -Pintegration-tests '-Dit.test=J3RuntimeIT,J3AutomationPersistenceIT,J3CollectionExecutionIT' verify` ; Failsafe, motif `**/integration/**/*IT.java`, PostgreSQL Testcontainers et réponses simulées. |
| Protocole pur | `mvnw.cmd -Pprovider-playwright-runtime '-Dtest=J3WorkerScopeProtocolTest' test` ; Surefire, source ajoutée `src/provider-playwright-test/java`, sans lancement Chromium par ce test. |
| Qualification native ciblée | Commande explicite ci-dessous ; source `src/provider-playwright-qualification-test/java`, JAR worker et Chromium local nécessaires. |

Commande native documentée :

```powershell
.\mvnw.cmd -q `
  '-Pprovider-playwright-runtime,provider-playwright-local-qualification' `
  '-Dtest=J3WorkerScopeProtocolTest' `
  '-Dit.test=J3LivePauseWorkerQualificationIT' `
  package `
  failsafe:integration-test@provider-playwright-loopback-qualification `
  failsafe:verify@provider-playwright-loopback-qualification
```

Le POM sépare les sorties standard de `target/provider-playwright-runtime/classes` et `test-classes`. **Compilation du profil, production du JAR worker et lancement du navigateur sont trois événements distincts.** La preuve future devra lier SHA candidat, profils, sélections, rapports et empreinte du worker exécuté.

Compléments ciblés à prévoir :

- **`ss-verify` — composition Spring :** vérifier dans un contexte représentatif que runtime et live reçoivent bien la factory résiliente, tout en neutralisant les effets fournisseur.
- **`ss-postgres-change` puis `ss-verify` — publication/rétention/J8 :** examiner les migrations et adaptateurs manquants, reproduire la course publication/purge et préciser la réconciliation J8 après échec terminal.
- **`ss-verify` — worker :** compléter les rejets directement à l’entrée IPC et les cas de perte du contexte/nettoyage incertain. La validation complète de l’origine loopback, notamment du port TCP utilisable, n’est pas démontrée : ses classes de configuration sont hors corpus.
- **`ss-postgres-change` puis `ss-verify` — redémarrage :** vérifier avec le stockage réel de résilience qu’un refus persiste et qu’aucun horaire manqué ni ordre consommé n’est réarmé.
- **`ss-data-contract-replay` — seulement si le contrat évolue :** revoir parseur, complétude, provenance et reconstruction historique avant toute modification de ces responsabilités.

## Conclusion

**Rejeter les quatre propositions dans leur forme actuelle et corriger les cinq affirmations de couverture.** Le placement existant offre les frontières pertinentes pour une évolution limitée : admission du runtime, exécution commune, publication transactionnelle, propriétaire live et worker isolé.

Le livrable établit les mécanismes et les assertions présents. Il **ne constitue pas une qualification exécutée du candidat courant**. Aucun fichier n’a été modifié.

[skill]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/.agents/skills/ss-java-module/SKILL.md
[input]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/input.json:43
[agents]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/AGENTS.md:54
[pom]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/pom.xml:169
[adr]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:95
[rapport]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/docs/validation/WO060-J3-IMPLEMENTATION-20260914.md:42
[verify]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/scripts/Verify-Local.ps1:17
[executor]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java:3
[completion]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionCompletionService.java:17
[runtime]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:69
[collectionjdbc]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3CollectionStore.java:61
[automationjdbc]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3AutomationStore.java:133
[automationport]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/main/java/com/bettingproject/sofascorelocal/port/J3AutomationStore.java:9
[collectionport]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/main/java/com/bettingproject/sofascorelocal/port/J3CollectionStore.java:11
[live]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java:71
[resilient]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java:20
[supervisor]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java:768
[worker]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerMain.java:267
[protocol]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocol.java:340
[springtest]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisorSpringContextTest.java:15
[executiontest]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3CollectionExecutionIT.java:52
[runtimetest]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3RuntimeIT.java:91
[automationtest]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3AutomationPersistenceIT.java:29
[protocoltest]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/J3WorkerScopeProtocolTest.java:11
[nativetest]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C02/src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/J3LivePauseWorkerQualificationIT.java:18