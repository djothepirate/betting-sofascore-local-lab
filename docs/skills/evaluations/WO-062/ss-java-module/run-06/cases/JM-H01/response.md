**WO-030 apporte une preuve historique ciblée du refus d’un port loopback hors plage, et les fichiers actuels conservent les deux gardes correspondantes. Cette preuve ne qualifie ni toute l’architecture Playwright, ni le fonctionnement actuel du worker et du moteur J3 durable.** Aucun build, test, navigateur, appel réseau ou changement de fichier n’a été exécuté pendant cette revue.

Le skill demandé a été lu intégralement avec un outil local. L’analyse porte sur le cas `JM-H01`, à la date de référence du **15 septembre 2026** donnée par `input.json`. Le SHA `_policy_source_commit=6648dd423e556b5248b8793a539ae85a7680f9bc` est une provenance déclarée par l’entrée ; je ne l’assimile pas à un `HEAD` vérifié. Les anciens rapports Surefire/Failsafe et le runtime historique ne sont pas disponibles dans le corpus.

**L’anomalie concernait la borne numérique du port TCP.** Sur la baseline `daf55bf76521f81893f86d04fde3c2903bf22362`, le rapport indique que `ProviderPlaywrightProperties.isSafeConfiguration()` et `ProviderPlaywrightWorkerConfiguration.parseOrigin()` exigeaient seulement un port positif. Ainsi, `http://127.0.0.1:65536` pouvait franchir ces validations : sa représentation comme URI structurée ne rend pas son port utilisable en TCP.

Le correctif qualifié sous `154349a2fbebe3fd0a43a63c7105f690ff04976b` impose indépendamment `[1, 65535]` aux deux frontières. Le port IPC du worker et l’origine simulée de `ScheduledEventsTransportRequest` étaient déjà bornés ; WO-030 ne corrigeait pas ces deux contrats voisins. Ces constats historiques sont explicités dans le [rapport WO-030, lignes 28 et suivantes](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-06/JM-H01/docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md:28).

| Origine examinée | Résultat attendu aux deux frontières | Portée |
|---|---|---|
| `http://127.0.0.1` — port absent, `-1` | Refus | Aucun port explicite |
| `http://127.0.0.1:0` | Refus | Borne inférieure |
| `http://127.0.0.1:1` | Acceptation structurelle | Aucune connexion prouvée |
| `http://127.0.0.1:65535` | Acceptation structurelle | Aucune connexion prouvée |
| `http://127.0.0.1:65536` | Refus | Discriminant du correctif |

**Les responsabilités parent/enfant restent distinctes.**

| Élément | Responsabilité observable dans les sources autorisées | Limite de la conclusion |
|---|---|---|
| `ProviderPlaywrightProperties`, côté parent | Propriétés sous `sofascore.playwright`, annotées `@Validated`. `isSafeConfiguration()`, annotée `@AssertTrue`, contrôle les délais et l’origine de qualification : `http`, hôte exact `127.0.0.1`, port explicite borné, absence de user-info, query, fragment et chemin non vide. Sans qualification, l’override doit être vide. | Les annotations et le test Bean Validation ne prouvent pas à eux seuls le binding et la composition du contexte Spring complet. |
| Policy et contrôle d’exécution parents | Le test construit directement `J3ProviderQualificationPolicy`, puis `J3ManualCallControlService`, auquel il transmet `policy::snapshot` et `policy::localImportSnapshot`. Il exerce le refus d’admission avant `EXECUTING`. | Les implémentations de ces services ne sont pas autorisées à la lecture. Leur branchement au parcours actuel n’est donc pas vérifié. |
| `ProviderPlaywrightWorkerConfiguration`, côté enfant | `fromEnvironment()` valide successivement port IPC, jeton via `ProviderPlaywrightWorkerProtocol.validateToken()`, puis origine. `parseOrigin()` applique sa propre garde et produit `IllegalArgumentException("INVALID_CONFIGURATION")` en cas de refus. `uriFor()` construit les URI des six familles prévues. | La classe ne démarre aucun navigateur. L’ordre réel entre son invocation, l’ouverture IPC et le démarrage Playwright nécessiterait la lecture du point d’entrée et du superviseur. |
| `ScheduledEventsTransportRequest`, domaine | Valide l’origine simulée et la date non nulles, puis construit `/simulated/scheduled-events?date=…`. Aucun transport concret n’y est instancié. | Ce contrat distinct n’assure pas la validation de l’environnement du worker. Il accepte aussi le chemin racine `/`, contrairement aux gardes de qualification Playwright. |

Les deux classes de configuration actuelles contiennent chacune `MAXIMUM_TCP_PORT = 65_535` et le contrôle supérieur attendu. Dans le worker, cette constante sert à la fois au port IPC et au port de l’origine ; elle n’est pas une constante Java partagée avec le parent. L’origine fournisseur reste fixée à `https://www.sofascore.com` quand la qualification loopback n’est pas sélectionnée. Construire cette URI n’effectue aucun appel.

Sources directes : [propriétés parentes, ligne 98](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-06/JM-H01/src/main/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightProperties.java:98), [configuration du worker, ligne 29](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-06/JM-H01/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerConfiguration.java:29), [requête simulée, ligne 44](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-06/JM-H01/src/main/java/com/bettingproject/sofascorelocal/domain/provider/ScheduledEventsTransportRequest.java:44).

Le parent porte donc la configuration et l’admission applicative ; l’enfant doit rejeter lui-même une configuration invalide reçue par environnement. **La validation parente ne dispense pas de cette défense à l’entrée du worker.** Le rapport situe historiquement ce refus avant connexion IPC, ouverture du runtime et navigation. Le corpus actuel permet de vérifier la validation locale, mais pas de reconstituer toute la chaîne factory résiliente → superviseur de JVM enfant → point d’entrée → navigateur.

**Les tests lus invoquent effectivement les contrôles suivants ; ils n’ont pas été exécutés pendant cette revue.**

- Dans [ProviderPlaywrightPropertiesTest, ligne 48](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-06/JM-H01/src/test/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightPropertiesTest.java:48), `acceptsOnlyTheClosedTcpPortRangeBoundaries()` couvre les cinq valeurs de la matrice par appel direct à `isSafeConfiguration()` **et** par `validator.validate(properties)`. Pour `65536`, il vérifie une violation sur `safeConfiguration`. C’est une validation Java/Bean Validation sans contexte Spring.
- Dans [J3ProviderQualificationPolicyTest, ligne 179](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-06/JM-H01/src/test/java/com/bettingproject/sofascorelocal/application/network/J3ProviderQualificationPolicyTest.java:179), `rejectsAnOutOfRangeLoopbackPortBeforeAProviderClaim()` vérifie l’indisponibilité du snapshot et ses deux bloqueurs, prépare et confirme une intention, puis appelle réellement `claimExecution(requestId)`. Il attend `PROVIDER_TRANSPORT_UNAVAILABLE`, `executionMayContinue=false` et l’état final `CONFIRMED_READY`. Le JAR temporaire contient un manifeste déclarant la classe attendue ; ce n’est pas un worker lancé ni une preuve de son exécutabilité.
- Dans [ProviderPlaywrightWorkerProtocolTest, ligne 255](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-06/JM-H01/src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocolTest.java:255), `acceptsOnlyTheClosedLoopbackOriginTcpPortRange()` passe par `fromEnvironment()`. Les valeurs refusées doivent produire `INVALID_CONFIGURATION` ; pour `1` et `65535`, le test inspecte le port d’une URI construite par `uriFor()`. Il n’ouvre ni socket, ni JVM enfant, ni Chromium. Les autres tests de protocole utilisent des flux mémoire pour les commandes, réponses et échanges `START`/`READY`/`CLOSED`.

La revue indépendante historique avait précisément demandé de renforcer le test de policy : vérifier seulement un snapshot bloqué ne prouvait pas le refus du claim. Le rapport atteste cette correction avant les qualifications finales.

Une nuance demeure : le test du claim active aussi le bloqueur `PLAYWRIGHT_LOOPBACK_QUALIFICATION_ACTIVE`. Il prouve le refus de cette configuration et l’absence de transition à `EXECUTING`, sans isoler le port comme cause unique du refus. La preuve discriminante de la borne supérieure vient des tests directs de propriétés et de configuration worker.

**Les résultats historiques doivent conserver leur date, leur SHA et leurs incidents.** Le rapport autonome atteste les exécutions suivantes au 1er septembre 2026 ; les valeurs ci-dessous sont celles du rapport, sans nouvelle validation.

| Commande historique consignée | Résultat consigné |
|---|---|
| `mvnw.cmd --offline -Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest test` | PASS — `14/0/0/0` |
| `mvnw.cmd --offline -Pprovider-playwright-runtime -Dtest=ProviderPlaywrightWorkerProtocolTest test` | PASS — `11/0/0/0` |
| `mvnw.cmd --offline clean verify` | PASS — Surefire `1043/0/0/5`, Failsafe `84/0/0/0`, fin à `13:41:43Z` |
| `mvnw.cmd --offline -Pprovider-playwright-runtime clean verify` | PASS — Surefire `1065/0/0/5`, Failsafe `84/0/0/0`, fin à `13:45:44Z` |

Le même rapport consigne des contrôles de diff, secrets, artefacts interdits, adresse locale, flags et ressources résiduelles. Il rapporte aussi des intégrations Flyway V1→V29, ledger J7, mTLS et échanges locaux de bout en bout. Ces activités sont distinctes de la matrice de ports, qui ne nécessitait aucune connexion. Compose est marqué `NOT_RUN_NOT_REQUIRED`, avec `.env` absent du worktree.

Deux incidents doivent rester visibles :

- Une première tentative ciblée s’est arrêtée **avant les tests**, faute d’accès Java à `spring-orm-7.0.8.jar` dans le cache Maven utilisateur. Le rapport atteste une relance hors sandbox, avec cache explicitement désigné et toujours sous `--offline`, puis un succès. Il ne fournit pas tous les détails de cette invocation. Cet incident d’accès à une dépendance n’établit pas une régression applicative.
- Des avertissements Hikari à la fermeture de certaines intégrations sont attribués à des connexions Testcontainers déjà fermées ; le rapport donne les tests concernés et les deux builds finaux comme réussis.

`--offline` concerne la résolution Maven : il ne signifie pas absence de DB, de sockets locaux ou de navigateur dans toute suite possible.

Le rapport, arrêté à `13:47:21Z`, mentionne encore `READY_FOR_OWNER_REVIEW`. Le [WO terminé, lignes 153 et suivantes](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-06/JM-H01/docs/work_orders/completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md:153), enregistre ensuite la décision propriétaire `VALIDATE` à `14:00:53Z`, liée au commit qualifié et au hash du rapport, et le déplacement vers `completed`. Cette succession explique les deux statuts. La décision autorisait ce déplacement documentaire ; elle n’autorisait ni push, ni fusion, ni nouvelle campagne fournisseur.

**Le POM actuel confirme une séparation de sources et de dépendances, avec une portée différente des anciennes commandes.**

Le [pom.xml actuel](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-06/JM-H01/pom.xml:169) décrit un projet monomodule, Java 25, Spring Boot 4.1.0, version applicative `0.1.0-rc.1-SNAPSHOT`. Les répertoires spécialisés ne sont pas des sous-modules Maven.

- Les deux tests parents résident dans `src/test/java`, avec des noms `*Test` et une sélection explicite possible par Surefire via `-Dtest`.
- `provider-playwright-runtime` ajoute la dépendance `com.microsoft.playwright:playwright:1.62.0`, les sources `src/provider-playwright/java` et les tests `src/provider-playwright-test/java`. Il sépare les classes compilées dans `target/provider-playwright-runtime/classes` et `test-classes`. À la phase `package`, il produit le JAR classifié `provider-playwright-worker`, avec `ProviderPlaywrightWorkerMain` comme entrée. **Compiler le runtime, produire ce JAR et lancer Chromium sont trois opérations distinctes.**
- `provider-playwright-local-qualification` ajoute d’autres sources de tests et une exécution Failsafe sélectionnant `**/*LocalQualificationIT.java`. Cette qualification native est distincte des tests de protocole en mémoire ; son exécution n’est pas attestée par les commandes ciblées WO-030.
- Dans le POM lu, les intégrations générales sont liées aux objectifs Failsafe `integration-test` et `verify` par le profil `integration-tests`, avec les motifs `**/integration/**/*IT.java` et `**/ProviderResilienceTransportPersistenceIT.java`. La déclaration globale de Failsafe configure le plugin sans y lier ces objectifs. Les anciens `84` tests Failsafe ne peuvent donc pas être reportés sur un `clean verify` courant par simple analogie.
- Surefire et Failsafe fixent `sofascore.j3.runtime-enabled=false`. Le profil `sofascore-live-test` reste bloqué par `maven-enforcer-plugin` et `alwaysFail`. Il n’active pas J3 et ne remplace pas `j7-browser-origin-loopback-qualification`, lequel ajoute Playwright uniquement en dépendance de test.

Le fichier actuel du test worker contient quinze méthodes `@Test` et attend `ProviderPlaywrightWorkerProtocol.VERSION == 10` ; le résultat historique de onze tests ne représente donc pas une exécution de ce fichier actuel.

Aucune règle ArchUnit exécutée n’est établie. Le skill décrit un garde textuel recherchant `com.microsoft.playwright` dans `src/main`, mais `scripts/Verify-Local.ps1` n’appartient pas aux fichiers autorisés : je n’ai vérifié ni son contenu ni son invocation. Une telle recherche resterait, en tout état de cause, moins complète qu’une analyse des dépendances. Le binding Spring effectif, l’adresse d’écoute actuelle et l’absence de dépendances indirectes interdites ne sont pas démontrés par ce corpus restreint.

**La gouvernance applicable a évolué après WO-030, sans élargir rétroactivement sa preuve.**

AGENTS conserve `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY`. Son exception J3 du 13 septembre autorise les clics directs et les ordres durables quotidiens ou planifiés du Lab configuré. L’[ADR-SS-007 actuelle, v0.3](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-06/JM-H01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:14) ajoute le 15 septembre le geste direct pour le tournoi sélectionné. Les confirmations J3 supprimées ne doivent pas être réintroduites sur la seule base du test manuel historique. Elles restent pertinentes pour les parcours internes de qualification qui les utilisent.

L’ADR distingue explicitement son état de réalisation et les formulations de cadrage conservées. Ses anciennes mentions de catalogue en mémoire ou de capacité de pause inexistante ne décrivent pas, à elles seules, l’état actuel. Inversement, son annonce de réalisation ne remplace pas une vérification du code absent de cette revue.

Pour la frontière worker pendant une pause live, le contrat adopté prévoit :

1. Un coordinateur et un garde durable propriétaires ; les nouveaux départs J4/J5 sont suspendus.
2. Le navigateur et le contexte live sont conservés. Un contexte J3 neuf, temporaire et non persistant est créé dans le même worker ; un seul contexte a le droit d’émettre, sans transfert de session.
3. Le contexte J3 est fermé avec nettoyage établi avant une reprise sur le **même** contexte live, sous contrôle des budgets, échéances et refus persistants.
4. La perte du navigateur ou du contexte live termine la session : notamment `PAUSED_FOR_J3 → STOPPED`. Aucun tick ne recrée le contexte live, aucune reprise automatique n’est permise après cette perte ; un nettoyage incertain empêche également la reprise.

Ce contrat ne constitue ni une autorisation fournisseur issue de WO-030, ni une qualification de ces transitions par les tests de ports.

**Pour une nouvelle révision, aucune correction supplémentaire de la borne n’est justifiée par les fichiers lus.** La vérification minimale proposée, entièrement future, serait la suivante :

1. **Fixer l’identité de la révision et le périmètre — relais `ss-verify`.** Enregistrer le SHA réellement testé, les profils, commandes et rapports frais. Vérifier que les deux gardes restent indépendantes, que les contrôles URI sont préservés et que la bibliothèque navigateur demeure hors du socle standard. La chaîne précédant les effets et le contexte Spring devront être examinés si le verdict revendiqué les inclut ; leurs sources manquent ici.

2. **Rejouer la preuve ciblée — relais `ss-verify`.** Après vérification du lanceur et de l’environnement autorisés, les deux commandes candidates sont :
   ```powershell
   .\mvnw.cmd --offline "-Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest" test
   .\mvnw.cmd --offline -Pprovider-playwright-runtime "-Dtest=ProviderPlaywrightWorkerProtocolTest" test
   ```
   Exiger la matrice complète, les deux voies de validation parentes, l’appel effectif au claim et le refus worker direct. Conserver séparément les échecs d’accès aux dépendances, les erreurs de compilation et les résultats de tests. Aucun Chromium ni DB n’est nécessaire à cette preuve ciblée.

3. **Adapter la qualification au changement réel — relais `ss-verify`.** Pour une modification de sources, ajouter le `mvnw.cmd clean verify` exigé par AGENTS. Une modification du packaging ou de la composition worker nécessite aussi sa qualification sous `provider-playwright-runtime`. Une qualification native loopback devient pertinente si l’ordre des effets, le lancement ou le nettoyage changent ; elle n’est pas nécessaire pour prouver uniquement une borne numérique. Toute évolution transactionnelle, de schéma ou de ledger relève en plus de `ss-postgres-change` et des intégrations appropriées. Toute évolution de contrat, parsing ou replay relève de `ss-data-contract-replay`.

4. **Produire un verdict neuf et borné — relais `ss-verify`.** Relier chaque assertion au comportement exercé et à son rapport, sans reprendre les anciens totaux. Conserver WO-030 comme preuve du correctif de 2026-09-01. Une nouvelle révision doit établir ses propres résultats ; les éléments disponibles ici ne permettent pas de lui attribuer un nouveau `PASS`.