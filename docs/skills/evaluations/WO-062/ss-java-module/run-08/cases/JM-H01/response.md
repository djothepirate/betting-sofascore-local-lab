**WO-030 apporte une preuve historique ciblée de refus local d’un port hors plage. Les deux gardes correspondantes sont toujours présentes dans les fichiers actuels. Cette concordance ne constitue toutefois ni une nouvelle qualification exécutée, ni une preuve complète de la frontière parent/worker ou des parcours J3 actuels.**

J’ai lu intégralement le skill `ss-java-module` avec un outil local, puis uniquement les sources autorisées. Cette revue concerne le cas **JM-H01**, à la date demandée du **15 septembre 2026**. L’entrée fournit `6648dd423e556b5248b8793a539ae85a7680f9bc` comme commit de provenance de la politique ; cela ne prouve pas le SHA du checkout présent, que je n’ai pas interrogé. Les anciens rapports de tests et le runtime historique sont indisponibles. Aucun build, test, navigateur, réseau, application, DB, Docker ou changement de fichier n’a été exécuté.

Le [rapport WO-030](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-H01/docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md:3) rattache la qualification aux éléments suivants :

| Élément | État historique documenté |
|---|---|
| Base | `daf55bf76521f81893f86d04fde3c2903bf22362` |
| Commit d’implémentation | `154349a2fbebe3fd0a43a63c7105f690ff04976b` |
| Branche et worktree | `codex/j9-wo030-provider-playwright-port-boundary`, `.tmp/w30` |
| Qualification | `2026-09-01T13:47:21.0195627Z`, verdict `PASS_LOCAL_FAIL_CLOSED` |
| Portée | Refus du port invalide dans l’origine loopback de qualification ; zéro appel fournisseur |
| Autorisations | Aucun réseau fournisseur ou receiver réel, aucun déploiement VPS, aucun usage de production |

L’anomalie était une **validation sémantique incomplète** : les deux frontières exigeaient seulement un port positif. Selon le rapport, Java représentait `http://127.0.0.1:65536` comme une URI structurée avec `getPort() == 65536`, alors que cette valeur dépasse la borne TCP. Une URI analysable ne suffisait donc pas à établir une origine utilisable. Le correctif a ajouté la borne supérieure dans le parent **et indépendamment dans l’enfant**. Une validation exclusivement parente aurait laissé l’entrée environnement du worker vulnérable au même défaut si la première barrière était contournée.

La matrice qualifiée était la suivante ; elle reste explicitement décrite dans les tests actuels :

| Origine | Parent | Worker | Portée de l’acceptation |
|---|---|---|---|
| `http://127.0.0.1` — port absent, `-1` | Refus | Refus | — |
| `http://127.0.0.1:0` | Refus | Refus | — |
| `http://127.0.0.1:1` | Acceptation | Acceptation | Structurelle uniquement |
| `http://127.0.0.1:65535` | Acceptation | Acceptation | Structurelle uniquement |
| `http://127.0.0.1:65536` | Refus | Refus | — |

Aucune connexion aux ports `1` ou `65535` n’était nécessaire pour établir cette propriété. Le port IPC et l’origine simulée de `ScheduledEventsTransportRequest` étaient déjà bornés à `[1, 65535]` selon WO-030 ; ils ne constituaient pas les deux validations défectueuses.

Les responsabilités visibles dans les fichiers actuels sont précises :

| Élément | Responsabilité et dépendances constatées | Limite de ce constat |
|---|---|---|
| [ProviderPlaywrightProperties](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-H01/src/main/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightProperties.java:98) | Configuration du parent, préfixe `sofascore.playwright`, annotations `@Validated` et `@AssertTrue`. `isSafeConfiguration()` borne les délais puis vérifie l’origine : `http`, hôte exact `127.0.0.1`, port `[1,65535]`, absence de user-info, query, fragment et chemin non vide. Hors qualification, l’override doit être vide. Dépendances visibles : Jakarta Validation, configuration Spring et types JDK ; aucune classe navigateur importée. | L’annotation et le test Bean Validation ne prouvent pas à eux seuls le binding effectif dans le contexte Spring complet. |
| `J3ProviderQualificationPolicy` et `J3ManualCallControlService`, observés par leur test | La policy expose disponibilité, origine et motifs de blocage. Le service de contrôle consomme `policy::snapshot` et `policy::localImportSnapshot`, puis décide de l’admission via `claimExecution()`. | Leurs implémentations ne font pas partie des fichiers autorisés. Leurs appels et assertions sont visibles dans le test, pas toute leur réalisation interne. |
| [ProviderPlaywrightWorkerConfiguration](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-H01/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerConfiguration.java:29) | Frontière de configuration de l’enfant. `fromEnvironment()` valide successivement le port IPC, le jeton via le protocole, puis l’origine. `parseOrigin()` impose indépendamment la borne TCP et les restrictions loopback ; le refus produit une `IllegalArgumentException` portant `INVALID_CONFIGURATION`. Sans qualification explicite, l’origine reste `https://www.sofascore.com`. `uriFor()` reconstruit les chemins des six familles prévues à partir de commandes typées. | Ces méthodes ne lancent ni navigateur ni socket. L’ordre d’appel réel depuis le point d’entrée du worker n’est pas inspectable ici. |
| [ScheduledEventsTransportRequest](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-H01/src/main/java/com/bettingproject/sofascorelocal/domain/provider/ScheduledEventsTransportRequest.java:14) | Contrat du domaine pour une requête **simulée**, construit avec origine et date non nulles. Le constructeur contrôle déjà `[1,65535]` ; `targetUri()` produit `/simulated/scheduled-events?date=…`. Aucun transport concret n’est appelé. | Ce contrat n’est pas le validateur de l’environnement du worker. Il accepte un chemin racine `/`, contrairement aux deux validations de qualification qui exigent un chemin vide. |

Cette différence sur `/` compte : une mutualisation mécanique des validateurs pourrait modifier un contrat voisin. **Le défaut historique étant déjà corrigé dans les fichiers lus, aucun nouveau correctif de production n’est justifié par ce seul réexamen.**

La séparation parent/enfant décrite par le skill repose sur un parent chargé des contrats, de l’admission et de la supervision IPC, et un enfant chargé du runtime navigateur. Le corpus ne permet pas de suivre intégralement la factory résiliente, le superviseur, le démarrage du processus, la navigation ou le nettoyage. Le rapport affirme que le refus enfant intervient avant connexion IPC et ouverture de Playwright ; le code autorisé confirme une validation locale sans effet réseau, mais ne permet pas de revérifier toute cette séquence de démarrage.

Les tests autorisés permettent de préciser les contrôles réellement appelés, au-delà de leurs noms :

- Dans [ProviderPlaywrightPropertiesTest](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-H01/src/test/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightPropertiesTest.java:47), `acceptsOnlyTheClosedTcpPortRangeBoundaries()` appelle **à la fois** `isSafeConfiguration()` et `validator.validate(properties)` pour les cinq cas. Pour `65536`, il exige notamment une violation sur `safeConfiguration`. Les autres méthodes examinent les défauts inertes, certaines origines interdites, l’override hors qualification et les délais. Les objets sont créés directement ; aucun contexte Spring n’est chargé.

- Dans [J3ProviderQualificationPolicyTest](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-H01/src/test/java/com/bettingproject/sofascorelocal/application/network/J3ProviderQualificationPolicyTest.java:178), `rejectsAnOutOfRangeLoopbackPortBeforeAProviderClaim()` ne s’arrête pas à `snapshot()`. Il construit le service avec une horloge fixe, réarme et active le parcours historique, prépare puis confirme une intention, et **appelle réellement `claimExecution(requestId)`**. Il attend `PROVIDER_TRANSPORT_UNAVAILABLE`, vérifie `executionMayContinue=false` et conserve l’état `CONFIRMED_READY`, sans passage à `EXECUTING`. Le JAR temporaire est un fichier synthétique contenant un manifeste `Start-Class`, pas un worker réellement lancé. Autre limite : le scénario cumule `PLAYWRIGHT_CONFIGURATION_UNSAFE` et `PLAYWRIGHT_LOOPBACK_QUALIFICATION_ACTIVE`. Il établit le refus de ce scénario ; l’attribution spécifique à la borne TCP repose aussi sur le test direct des propriétés.

- Dans [ProviderPlaywrightWorkerProtocolTest](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-H01/src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocolTest.java:254), `acceptsOnlyTheClosedLoopbackOriginTcpPortRange()` passe par `fromEnvironment()`, puis par `uriFor()` pour les bornes acceptées. Les refus exigent `INVALID_CONFIGURATION`. Les autres tests lisent ou écrivent des trames dans des flux mémoire, contrôlent les six formes GET, les arguments, `START`, `READY`, `CLOSED` et l’EOF parent. Ils n’établissent pas le comportement d’une JVM enfant ou de Chromium en fonctionnement.

Le test worker courant comporte **15 méthodes `@Test`**, alors que la commande ciblée historique rapporte 11 tests. Il contient également une assertion de protocole **10** et une borne J3 de **35 pages**. Ce sont des indices explicites d’évolution du corpus depuis WO-030 ; ses anciens totaux ne doivent pas devenir des résultats actuels.

Le [POM actuel](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-H01/pom.xml:33) décrit un projet Maven monomodule, Java **25**, Spring Boot **4.1.0**, version applicative `0.1.0-rc.1-SNAPSHOT`. Les répertoires de sources spécialisés ne sont pas des sous-modules.

| Configuration actuelle | Effet déclaré et distinction utile |
|---|---|
| Sources standard et Surefire | Les deux tests parents sont dans `src/test/java`, avec des noms `*Test` et sans filtre Surefire particulier dans ce POM. `-Dtest=…` les sélectionne explicitement. |
| `provider-playwright-runtime` | Ajoute `com.microsoft.playwright:playwright:1.62.0`, `src/provider-playwright/java` et `src/provider-playwright-test/java`. Le test worker devient alors une cible Surefire. |
| Sorties du profil runtime | Classes et classes de test vont sous `target/provider-playwright-runtime/`, distinctement des sorties standard. Cette séparation évite de confondre les classes produites par les deux configurations ; elle ne prouve pas une exécution. |
| Packaging du worker | À la phase `package`, `repackage` produit le classifier `provider-playwright-worker` avec `ProviderPlaywrightWorkerMain`. Compiler les sources, produire le JAR et lancer ce JAR sont trois opérations distinctes. |
| `integration-tests` | Lie Failsafe à `integration-test` et `verify`, avec `**/integration/**/*IT.java` et `**/ProviderResilienceTransportPersistenceIT.java`. La configuration globale Failsafe lue ne déclare pas ces exécutions hors profil. |
| `provider-playwright-local-qualification` | Ajoute `src/provider-playwright-qualification-test/java` et une exécution Failsafe sélectionnant `**/*LocalQualificationIT.java`, avec chemin du JAR worker et cache navigateur. Ce périmètre diffère des tests mémoire du protocole ; ses sources ne sont pas autorisées ici. |
| `j7-browser-origin-loopback-qualification` | Déclare aussi Playwright, mais en portée `test`, avec sa source et son IT J7 propres. |
| `sofascore-live-test` | Reste explicitement bloqué par Enforcer `alwaysFail`. Il ne constitue ni une activation de collecte J3, ni une alternative au profil de qualification J7. |

Surefire et Failsafe reçoivent tous deux `sofascore.j3.runtime-enabled=false`. Ce paramétrage de tests, les profils **Maven**, les propriétés typées et un éventuel profil **Spring** sont des mécanismes distincts. Les fichiers YAML et la composition Spring complète étant hors corpus, leur activation effective n’est pas démontrée.

Le skill mentionne également un contrôle textuel de `com.microsoft.playwright` dans `src/main`. Le lanceur correspondant n’est pas autorisé à la lecture : **je ne le présente donc pas comme inspecté ou exécuté**. Un tel scan ne serait d’ailleurs pas une analyse exhaustive des dépendances. Aucune preuve disponible ne permet d’annoncer une règle ArchUnit exécutée.

Les exécutions historiques doivent être conservées avec leur portée exacte. Le rapport donne les résultats suivants, en notation tests/échecs/erreurs/ignorés :

| Commande historique rapportée | Résultat rapporté |
|---|---|
| `mvnw.cmd --offline -Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest test` | `14/0/0/0` |
| `mvnw.cmd --offline -Pprovider-playwright-runtime -Dtest=ProviderPlaywrightWorkerProtocolTest test` | `11/0/0/0` |
| `mvnw.cmd --offline clean verify` | Surefire `1043/0/0/5`, Failsafe `84/0/0/0` |
| `mvnw.cmd --offline -Pprovider-playwright-runtime clean verify` | Surefire `1065/0/0/5`, Failsafe `84/0/0/0` |

Le rapport affirme que les intégrations étaient alors liées automatiquement à `verify`, avec notamment Flyway V1→V29, ledger J7 et échanges locaux. **Le POM actuel ne permet pas de transposer cette inclusion aux commandes actuelles sans profil.** L’absence du POM historique et des rapports bruts empêche de reconstruire cette différence ; elle ne justifie ni d’effacer les résultats rapportés, ni de les appliquer à la révision présente.

Deux incidents limitent aussi la lecture d’un simple « vert » :

- Une première tentative ciblée s’est arrêtée **avant les tests**, Java ne pouvant lire `spring-orm-7.0.8.jar` dans le cache Maven utilisateur depuis le sandbox. Le rapport atteste ensuite une relance hors sandbox avec cache explicite, toujours `--offline`, réussie. C’est un incident d’accès aux dépendances, pas un échec applicatif démontré ; sa résolution n’autorise aucun fallback réseau.
- Des avertissements Hikari sont rapportés à la fermeture de connexions Testcontainers déjà fermées, avec suites finales réussies. `--offline` limitait la résolution Maven : il n’empêchait pas ces intégrations d’utiliser des ressources locales.

Les scans de secrets et d’artefacts, l’adresse `127.0.0.1`, les flags désactivés et l’absence de ressources résiduelles sont eux aussi **des constats historiques rapportés**, non revérifiés ici. Compose était `NOT_RUN_NOT_REQUIRED`.

Il faut enfin distinguer deux états documentaires successifs. Le rapport figé demandait encore la revue propriétaire. Le [WO complété](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-H01/docs/work_orders/completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md:181) enregistre ensuite la décision `VALIDATE` à **14:00:53 UTC**, autorisant le déplacement documentaire vers `completed`. Cette décision ne donnait aucune autorisation de push, fusion, collecte réelle ou production. Il n’y a donc pas de confirmation historique à redemander, ni de qualification générale à déduire du seul statut `VALIDATED`.

La gouvernance applicable au 15 septembre a évolué depuis ce correctif. L’[ADR-SS-007 v0.3](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-H01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:14) autorise les parcours J3 concernés par clic direct ou ordre durable quotidien/planifié, avec préférence automatique initialement activée puis persistée. Son complément du 15 septembre étend le geste direct au tournoi sélectionné. Les contrôles historiques internes restent disponibles pour les qualifications qui les utilisent : **le test de claim WO-030 ne prouve donc pas que le parcours utilisateur J3 courant exige encore préparation et confirmation**, ni qu’il qualifie l’admission durable nouvelle.

Pour la pause live, le contrat adopté conserve le contexte live et autorise un contexte J3 neuf, temporaire et non persistant dans le même worker. Un seul contexte peut émettre ; **aucun cookie, `storageState`, validateur conditionnel ou autre état de session n’est transféré entre eux**. Le retour nominal exige la fermeture prouvée du contexte J3, le même contexte live, un propriétaire valide et des budgets inchangés. La perte du navigateur ou du contexte live mène à **`STOPPED` / session terminée**, sans reprise automatique ni recréation par un tick. Un nettoyage incertain n’autorise pas un navigateur de remplacement. Ces règles sont établies par l’ADR, pas qualifiées par les tests de borne de WO-030.

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY` restent applicables. Cette évolution J3 ne lève ni le blocage de `sofascore-live-test`, ni l’interdiction de navigateur dans les tests standards, ni les exigences propres à tout nouvel endpoint.

Pour une nouvelle révision, je propose une vérification minimale en quatre étapes, **toutes futures et non exécutées ici** :

1. **Fixer l’identité et le périmètre de la preuve — relais `ss-java-module` et `ss-verify`.** Enregistrer le SHA réellement testé, le diff, le POM et les versions effectives. Conserver les deux validations indépendantes et les différences du contrat simulé. Si la révision ne modifie que cette validation locale, aucun changement de protocole, de persistance ou d’architecture n’est nécessaire.

2. **Rejouer les contrôles ciblés — relais `ss-verify`.** Après lecture du lanceur et vérification de l’environnement dans un cadre autorisant l’exécution, les commandes candidates sont :

   ```powershell
   .\mvnw.cmd --offline "-Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest" test
   .\mvnw.cmd --offline -Pprovider-playwright-runtime "-Dtest=ProviderPlaywrightWorkerProtocolTest" test
   ```

   Conserver des rapports frais rattachés au SHA et aux commandes. Vérifier les cinq bornes, l’appel effectif du claim, son erreur et son état final. L’acceptation structurelle ne nécessite ni Chromium ni connexion aux ports limites. Si les gardes voisines sont modifiées, compléter les cas user-info/query/fragment/chemin concernés : leur présence dans le code dépasse la couverture explicite actuellement lue.

3. **Compléter la qualification exigée par le changement — relais `ss-verify`.** Pour une modification effective, appliquer `.\mvnw.cmd clean verify` selon AGENTS ; ajouter la qualification du profil runtime si cette frontière change. Déterminer explicitement les intégrations attendues d’après le POM courant, sans recopier les anciens totaux Failsafe ni ajouter arbitrairement `-DskipITs`. Un défaut de borne TCP seul ne justifie pas une qualification PostgreSQL supplémentaire. Si l’objectif devient de prouver le refus *avant démarrage réel* du worker, il faudra aussi autoriser la lecture du point d’entrée/superviseur et choisir une preuve adaptée ; le corpus présent ne suffit pas.

4. **Router toute extension de portée.** Une évolution du contrat IPC, du parsing ou du replay relève de `ss-data-contract-replay` ; une évolution de transaction, schéma ou ledger relève de `ss-postgres-change`, avec qualification via `ss-verify`. Ces skills n’ont pas été exécutés dans cette revue.

Le livrable présent établit ainsi **la persistance statique des deux corrections et la portée exacte de la preuve WO-030**. Restent non établis pour la révision actuelle : les résultats exécutés, le binding Spring complet, la séquence réelle de démarrage et de nettoyage du worker, ainsi que la qualification des nouveaux parcours J3. Aucun fichier n’a été modifié.