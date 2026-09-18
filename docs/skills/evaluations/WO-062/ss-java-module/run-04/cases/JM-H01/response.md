# Réexamen de la frontière Playwright — WO-030 / JM-H01

**WO-030 documente la correction de deux validations incomplètes du port TCP de l’origine de qualification loopback : celle du parent et celle du worker enfant. Les deux bornes corrigées sont toujours présentes dans les fichiers autorisés actuels. Cela étaye le correctif ciblé, mais ne constitue ni une nouvelle exécution des tests, ni une qualification complète du lancement du worker ou du parcours J3 actuel.**

Le skill `ss-java-module` a été lu intégralement avec un outil local. Cette revue utilise uniquement les sources autorisées, sans build, test, réseau, navigateur, application, base de données, Docker ni modification.

## 1. Référentiel et portée de la preuve

L’entrée `input.json` identifie le cas **JM-H01**, avec une question arrêtée au **15 septembre 2026**. Elle fournit le commit de provenance de politique `6648dd423e556b5248b8793a539ae85a7680f9bc`. Ce renseignement ne vaut pas vérification du `HEAD` du répertoire examiné : aucun état Git n’a été consulté.

Trois niveaux doivent rester séparés :

| Niveau | Éléments disponibles | Conclusion permise |
|---|---|---|
| Historique WO-030 | Rapport autonome et Work Order versionnés | Restituer les essais, résultats et décisions qu’ils consignent |
| Fichiers actuels | POM, trois classes et trois fichiers de tests autorisés | Décrire les validations et assertions présentes, ainsi que leur composition Maven déclarée |
| Gouvernance applicable | `AGENTS.md` et ADR-SS-007 v0.3 | Identifier les autorisations et contraintes actuelles, sans prétendre requalifier leur réalisation |

Les anciens rapports Surefire/Failsafe et le runtime historique ne sont pas disponibles. Les résultats historiques ci-dessous sont donc **rapportés par les documents**, pas reproduits pendant cette revue.

## 2. L’anomalie et le correctif qualifié

### Défaut historique

Sur la base `daf55bf76521f81893f86d04fde3c2903bf22362`, les deux méthodes suivantes exigeaient seulement un port positif :

- `ProviderPlaywrightProperties.isSafeConfiguration()` côté parent ;
- `ProviderPlaywrightWorkerConfiguration.parseOrigin()` côté enfant.

Le discriminant était :

```text
http://127.0.0.1:65536
```

Le rapport explique que Java représente cette valeur comme une URI structurée dont le port vaut `65536`. **La validité syntaxique d’une URI ne garantit donc pas que son port appartient à l’intervalle TCP utilisable.** Sans borne supérieure explicite, cette valeur franchissait les deux validations.

WO-030 ajoute `MAXIMUM_TCP_PORT = 65_535` et exige l’intervalle fermé `[1, 65535]` aux deux frontières. Il conserve les gardes de schéma, d’hôte et de composants d’URI.

| Origine loopback | Résultat attendu parent et worker |
|---|---|
| `http://127.0.0.1` — port absent, représenté par `-1` | Refus |
| `http://127.0.0.1:0` | Refus |
| `http://127.0.0.1:1` | Acceptation structurelle |
| `http://127.0.0.1:65535` | Acceptation structurelle |
| `http://127.0.0.1:65536` | Refus |

L’acceptation des deux bornes valides ne prouve ni la présence d’un service à ces ports, ni la possibilité d’y établir une connexion. Les tests de cette matrice n’effectuent aucune connexion.

Le port **IPC**, utilisé pour communiquer entre parent et enfant, ainsi que l’origine simulée de `ScheduledEventsTransportRequest`, étaient déjà bornés correctement. WO-030 ne corrige pas ces contrats ; il remplace seulement le littéral de borne IPC par la constante du worker. Le push local J7 de WO-029 est un autre parcours.

**Source :** [rapport WO-030, §§2–4][rapport].

## 3. Responsabilités parent/enfant et état actuel des fichiers

### Carte des responsabilités observables

| Élément | Responsabilité et dépendances visibles | Limite de ce qui est établi |
|---|---|---|
| `ProviderPlaywrightProperties` — sources standard | Configuration typée `sofascore.playwright`, annotations Spring et Jakarta Validation, validation des durées et de l’origine de qualification. Utilise `URI`, sans bibliothèque navigateur. | Le préfixe et `@Validated` sont visibles ; le binding dans l’application complète n’est pas testé par les fichiers fournis. |
| `J3ProviderQualificationPolicy` et `J3ManualCallControlService` — utilisés par le test | La policy expose la disponibilité et les motifs de blocage. Le contrôle d’intention doit refuser la prise en charge lorsque le transport est indisponible. | Le test montre les appels et assertions ; les implémentations de ces deux classes ne font pas partie du périmètre lisible. |
| Supervision de la JVM enfant | Le rapport décrit la transmission de l’origine au worker par environnement. L’ADR cite `ChildJvmPlaywrightProviderSupervisor`. | La factory, le superviseur et le point d’entrée du worker ne sont pas inspectables ici : leur ordre actuel de lancement et de nettoyage n’est pas directement vérifié. |
| `ProviderPlaywrightWorkerConfiguration` — sources du profil runtime | Relit et valide indépendamment l’environnement reçu ; construit les URI des six familles de commandes. Dépend du protocole interne du worker et de types Java. | Une validation parente ne remplace pas cette barrière enfant. Le fichier examiné ne réalise lui-même aucune connexion ni navigation. |
| `ScheduledEventsTransportRequest` — domaine | Record de requête simulée : origine loopback et date obligatoires, validation au constructeur, construction d’une URI `/simulated/scheduled-events?date=…`. | Ce contrat voisin ne remplace aucune des deux validations de qualification Playwright. |

### Parent : la borne corrigée est présente

Dans le fichier actuel, `isSafeConfiguration()` exige :

- des durées positives et plafonnées ;
- une origine vide lorsque la qualification loopback est désactivée ;
- lorsqu’elle est activée : `http`, hôte exact `127.0.0.1`, port entre `1` et `65535`, absence de user-info, query et fragment, et chemin absent ou vide.

La méthode porte `@AssertTrue`. Les limites mémoire `64–512 MiB` relèvent séparément de `@Min` et `@Max`. Les valeurs initiales de cette classe sont inertes : `enabled=false`, aucun JAR sélectionné et qualification loopback désactivée.

**Source :** [ProviderPlaywrightProperties.java, ligne 98][properties].

### Enfant : une validation indépendante

`fromEnvironment()` effectue, dans cet ordre visible :

1. validation du port IPC par `parsePort()` ;
2. validation du jeton par `ProviderPlaywrightWorkerProtocol.validateToken()` ;
3. validation de l’origine par `parseOrigin()` ;
4. construction de la configuration.

`parseOrigin()` applique aussi `[1, 65535]`. Il refuse une origine de remplacement non vide sans commutateur de qualification exactement égal à `"true"`. En qualification, il exige l’origine HTTP loopback exacte, sans composants supplémentaires. Un refus produit une `IllegalArgumentException` portant `INVALID_CONFIGURATION`.

Sans origine de qualification, la constante reste `https://www.sofascore.com`. `uriFor()` reconstruit les chemins des six familles `SCHEDULED_EVENTS`, `TOURNAMENT_SCHEDULED_EVENTS`, détails, statistiques, incidents et compositions. Construire ces URI n’autorise pas leur appel.

Le rapport historique situe cette validation avant connexion IPC, ouverture du runtime et navigation. **Le code autorisé confirme la validation à la création de la configuration ; il ne suffit pas, sans le point d’entrée du worker, à revérifier tout cet ordre d’exécution aujourd’hui.**

**Source :** [ProviderPlaywrightWorkerConfiguration.java, ligne 29][worker-config].

### Contrat voisin : ne pas uniformiser sans raison

`ScheduledEventsTransportRequest` borne toujours le port à `[1, 65535]`. Il accepte un chemin racine `/`, contrairement aux deux origines de qualification Playwright, qui exigent un chemin vide ou absent. Il s’agit d’une différence de contrat visible, pas d’une régression démontrée de WO-030.

Aucune extraction d’un validateur commun ni modification de ce record n’est nécessaire pour conserver le correctif ciblé.

**Source :** [ScheduledEventsTransportRequest.java, ligne 44][request].

## 4. Contrôles réellement invoqués et composition Maven

### Ce que font les tests autorisés

**`ProviderPlaywrightPropertiesTest.acceptsOnlyTheClosedTcpPortRangeBoundaries()`**

Pour chacun des cinq cas, le test appelle directement `isSafeConfiguration()` et `validator.validate(properties)`. Pour `65536`, il vérifie notamment une violation sur `safeConfiguration`. Il teste ainsi le prédicat et son raccordement à Bean Validation, sans charger un contexte Spring complet.

**`J3ProviderQualificationPolicyTest.rejectsAnOutOfRangeLoopbackPortBeforeAProviderClaim()`**

Le test ne s’arrête pas à une consultation de disponibilité. Il :

1. configure `http://127.0.0.1:65536` ;
2. vérifie les blocages `PLAYWRIGHT_CONFIGURATION_UNSAFE` et `PLAYWRIGHT_LOOPBACK_QUALIFICATION_ACTIVE` ;
3. construit un contrôle d’intention avec horloge et identifiants déterministes ;
4. réarme, active, prépare puis confirme une intention ;
5. **appelle effectivement `claimExecution(requestId)`** ;
6. attend `PROVIDER_TRANSPORT_UNAVAILABLE` ;
7. vérifie `executionMayContinue=false` et l’état conservé `CONFIRMED_READY`.

Le JAR utilisé est un fichier temporaire contenant un manifeste `Start-Class`, pas un worker lancé. Ce test ne qualifie donc pas l’exécutabilité du JAR.

Une nuance causale demeure : l’activation de la qualification loopback constitue elle-même un blocage de l’appel fournisseur. Le refus du claim n’isole donc pas, à lui seul, l’effet de la borne supérieure. La présence de `PLAYWRIGHT_CONFIGURATION_UNSAFE`, les tests directs de propriétés et la matrice enfant complètent cette preuve.

**`ProviderPlaywrightWorkerProtocolTest.acceptsOnlyTheClosedLoopbackOriginTcpPortRange()`**

Le test passe par `fromEnvironment()` :

- les valeurs invalides doivent lever `INVALID_CONFIGURATION` ;
- les valeurs `1` et `65535` sont vérifiées après construction d’une URI par `uriFor()`.

Ces appels restent en mémoire. Les autres tests visibles du fichier manipulent également des commandes ou trames avec des flux d’octets en mémoire. Leur présence ne constitue pas une qualification native de Chromium.

**Sources :** [test de propriétés, ligne 48][properties-test], [test de claim, ligne 179][policy-test], [test des bornes enfant, ligne 255][worker-test].

### Ce que déclare le POM actuel

Le projet est **monomodule Maven**. Les répertoires de sources supplémentaires ne sont pas des sous-modules.

- Le socle déclaré concorde avec `AGENTS.md` : **Java 25**, **Spring Boot 4.1.0**, Maven `3.9.x` imposé par Enforcer. Ce sont des déclarations lues, pas des versions exécutées.
- Le profil `provider-playwright-runtime` ajoute la dépendance `com.microsoft.playwright:playwright`, les sources `src/provider-playwright/java` et les tests `src/provider-playwright-test/java`.
- Il utilise des sorties séparées sous `target/provider-playwright-runtime/classes` et `test-classes`, distinctes des sorties standard.
- À la phase `package`, il produit le JAR classifié `provider-playwright-worker`, avec `ProviderPlaywrightWorkerMain` comme classe principale.
- **Compiler le runtime, produire ce JAR et lancer un navigateur sont trois opérations distinctes.**
- Le profil `provider-playwright-local-qualification` ajoute un autre répertoire de tests et une exécution Failsafe pour `**/*LocalQualificationIT.java`. Il n’est pas équivalent aux tests de protocole ci-dessus.
- Le profil J7 de qualification navigateur possède aussi une dépendance Playwright, de portée `test` et explicitement profilée.
- Surefire et Failsafe fixent `sofascore.j3.runtime-enabled=false`. Le profil `sofascore-live-test` conserve un garde Enforcer `alwaysFail`.

Les deux tests parents sont sous `src/test/java`. Le test enfant devient source de test avec `provider-playwright-runtime`. Les commandes ciblées historiques les sélectionnent explicitement par `-Dtest` et exécutent Surefire à la phase `test`.

**Écart temporel essentiel :** le POM actuel configure Failsafe globalement, mais ses exécutions d’intégration sont rattachées à des profils explicites. Le profil `integration-tests` inclut `**/integration/**/*IT.java` et `**/ProviderResilienceTransportPersistenceIT.java`. L’affirmation historique « intégrations liées automatiquement à `verify` » ne doit donc pas être transposée au cycle courant.

**Source :** [pom.xml, ligne 139][pom].

### Garde architectural non vérifié ici

Le skill demande de vérifier dans le lanceur la recherche textuelle de **`com.microsoft.playwright` dans `src/main`**. `scripts/Verify-Local.ps1` n’est pas autorisé à la lecture : ce garde n’a été ni inspecté ni exécuté. Une telle recherche ne constituerait de toute façon pas une analyse exhaustive du graphe de dépendances. Aucune preuve d’exécution ArchUnit n’est fournie.

## 5. Qualification historique : résultats, incidents et décision

### Résultats consignés le 1er septembre 2026

| Contrôle | Commande historique | Résultat rapporté |
|---|---|---|
| Tests ciblés parents | `mvnw.cmd --offline -Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest test` | `14/0/0/0` |
| Tests ciblés enfant | `mvnw.cmd --offline -Pprovider-playwright-runtime -Dtest=ProviderPlaywrightWorkerProtocolTest test` | `11/0/0/0` |
| Suite standard finale | `mvnw.cmd --offline clean verify` | Surefire `1043/0/0/5`, Failsafe `84/0/0/0` |
| Profil runtime final | `mvnw.cmd --offline -Pprovider-playwright-runtime clean verify` | Surefire `1065/0/0/5`, Failsafe `84/0/0/0` |

Le rapport consigne aussi les contrôles du diff, des secrets, des artefacts interdits, de l’adresse `127.0.0.1`, des flags réseau et des ressources résiduelles. Compose est marqué **`NOT_RUN_NOT_REQUIRED`**, avec `.env` absent et aucun changement Compose. Les intégrations PostgreSQL ont servi de non-régression supplémentaire ; le WO ne les rendait pas nécessaires à ce correctif sans persistance.

Ces anciens totaux ne sont pas des résultats actuels. Le fichier de test worker examiné contient notamment des contrôles supplémentaires et une assertion `VERSION == 10`, malgré un nom de méthode conservant « V9 ».

### Incidents à conserver dans le bilan

- **Accès Maven avant tests :** un premier lancement ciblé s’est arrêté parce que Java ne pouvait pas lire `spring-orm-7.0.8.jar` dans le cache utilisateur. Le rapport décrit une relance hors sandbox, avec cache explicitement désigné et toujours `--offline`, qui a réussi. Cet arrêt antérieur aux tests ne démontre pas une régression applicative.
- **Avertissements Hikari :** le rapport les attribue à des connexions Testcontainers déjà fermées pendant l’arrêt ; les tests concernés et les builds finaux sont consignés comme réussis.
- **Lacune initiale de preuve :** la revue indépendante avait constaté que le test de policy ne faisait que vérifier le snapshot bloqué. L’appel réel à `claimExecution()` a été ajouté avant le commit d’implémentation et les vérifications finales.

`--offline` concerne la résolution Maven. Il ne garantit pas l’absence de sockets locaux, de base ou de navigateur dans les tests sélectionnés. Le rapport distingue explicitement la matrice pure des suites plus larges utilisant loopback et Testcontainers.

### Chronologie de validation

- Commit d’implémentation qualifié : `154349a2fbebe3fd0a43a63c7105f690ff04976b`.
- Rapport établi à **13:47:21 UTC**, avec `PASS_LOCAL_FAIL_CLOSED` et attente de revue propriétaire.
- Décision propriétaire enregistrée ensuite à **14:00:53 UTC** : `VALIDATE`.
- Le Work Order porte donc `VALIDATED`, reconnaît la readiness locale et consigne son déplacement vers `completed`.

Il n’y a pas contradiction entre le rapport en attente et le WO validé : ils décrivent deux instants successifs. La décision enregistrée n’autorise toutefois ni réseau fournisseur, ni receiver réel, ni VPS, ni production, ni push ou merge. Les sources fournies ne prouvent pas la fusion de WO-030.

**Sources :** [rapport, §§5–8][rapport], [Work Order, §§7–9][wo].

## 6. Gouvernance applicable au J3 actuel

La formulation historique « aucun démarrage automatique ajouté par WO-030 » reste exacte pour ce delta. Elle ne décrit pas à elle seule la politique actuelle.

L’ADR-SS-007 autorisé est en **v0.3** :

- la v0.2 adopte le clic direct J3 et les ordres quotidiens ou planifiés durables dans le Lab local configuré ;
- la préférence automatique est initialement activée, puis persistante ; elle ne suffit pas à rendre disponible un transport mal configuré ;
- le complément du **15 septembre** étend le geste direct à la collecte/import des rencontres du tournoi sélectionné ;
- les contrôles historiques internes restent disponibles pour les qualifications qui les utilisent.

Le test `prepare → confirm → claimExecution` reste donc pertinent pour son contrôle interne. Il ne démontre pas que ces confirmations constituent encore le parcours utilisateur J3, ni que le moteur durable actuel traverse cette même chaîne.

Pour la pause live, le contrat adopté distingue :

1. un navigateur et un contexte live conservés ;
2. un contexte J3 neuf, non persistant et temporaire dans le même worker ;
3. un seul contexte autorisé à émettre à la fois, sans transfert d’état de session ;
4. fermeture prouvée du contexte J3 avant restitution du droit d’émettre au live ;
5. **perte du navigateur ou du contexte live : session terminée, sans recréation ni reprise automatique**.

Ces règles sont documentées, mais leur réalisation n’est pas vérifiable dans les classes autorisées. L’ADR décrit lui-même les constats de l’ancienne base comme historiques et les formulations prospectives comme un contrat adopté.

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY` restent applicables. La nouvelle autorité J3 ne généralise pas l’accès aux autres parcours et ne permet aucun navigateur dans les tests standards.

**Source :** [ADR-SS-007, état courant et exceptions][adr], notamment §§2, 3 et 6.

## 7. Vérification minimale proposée pour une nouvelle révision

**Aucune correction des bornes n’est à réappliquer : elles sont présentes.** La prochaine étape utile serait une nouvelle preuve attachée à une révision précise, dans un périmètre d’exécution distinct de cette revue.

### A. Rejouer les tests ciblés — relais `ss-verify`

Après identification du SHA testé et vérification des conditions d’exécution, les deux commandes minimales proposées sont :

```powershell
.\mvnw.cmd --offline "-Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest" test

.\mvnw.cmd --offline -Pprovider-playwright-runtime "-Dtest=ProviderPlaywrightWorkerProtocolTest" test
```

Elles sont **proposées, non exécutées**. La preuve à conserver doit montrer les classes réellement exécutées, leurs résultats, les profils actifs et les rapports correspondants, sans reprendre les anciens totaux.

Critères minimaux :

- matrice des cinq ports aux deux frontières ;
- validation directe et Bean Validation côté parent ;
- refus effectif du claim avec état conservé ;
- refus enfant à `fromEnvironment()` ;
- absence de lancement natif pour ces cas.

### B. Vérifier les gardes adjacentes si leur conservation est revendiquée — relais `ss-java-module`, puis `ss-verify`

Les fichiers montrent les gardes de user-info, query, fragment et chemin, mais les tests autorisés ne présentent pas une matrice symétrique complète de ces variantes aux deux frontières. Quelques cas ciblés pourraient compléter cette assertion, notamment le chemin `/`, sans modifier le contrat distinct de `ScheduledEventsTransportRequest`.

### C. Adapter la clôture au delta réel — relais `ss-verify`

Pour une modification effective, appliquer les vérifications requises par `AGENTS.md`, dont `clean verify`, et le profil runtime si le worker change. Vérifier les sorties séparées, le diff, les secrets et les défauts réseau dans un périmètre autorisant les fichiers nécessaires.

Ne pas ajouter artificiellement une obligation PostgreSQL à une simple borne de port. Si la persistance évolue, le relais devient **`ss-postgres-change`**, avec qualification d’intégration adaptée. Si un contrat de données, parsing ou replay évolue, le relais est **`ss-data-contract-replay`**.

### D. Réserver la qualification native à une affirmation plus large — relais `ss-java-module` et `ss-verify`

Affirmer aujourd’hui « refus avant toute connexion IPC ou création de navigateur », ou qualifier la pause J3/live, exigerait de lire le point d’entrée, la supervision, la composition Spring et les tests natifs concernés. Leur absence limite la présente conclusion ; elle ne prouve pas un défaut.

---

**Verdict :** le refus local des ports hors plage est **qualifié historiquement par WO-030 et conservé dans le code actuel examiné**. La validation propriétaire historique est enregistrée. Aucune nouvelle qualification d’exécution n’a été réalisée, et cette preuve ciblée ne couvre ni toute l’architecture des ports et adaptateurs, ni le lancement natif actuel, ni l’automatisation et la pause live introduites ultérieurement.

**Fichiers modifiés : aucun. Commandes effectuées : lectures locales et repérage de sections uniquement.**

[rapport]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-H01/docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md:28
[wo]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-H01/docs/work_orders/completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md:139
[properties]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-H01/src/main/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightProperties.java:98
[worker-config]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-H01/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerConfiguration.java:29
[request]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-H01/src/main/java/com/bettingproject/sofascorelocal/domain/provider/ScheduledEventsTransportRequest.java:44
[properties-test]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-H01/src/test/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightPropertiesTest.java:48
[policy-test]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-H01/src/test/java/com/bettingproject/sofascorelocal/application/network/J3ProviderQualificationPolicyTest.java:179
[worker-test]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-H01/src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocolTest.java:255
[pom]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-H01/pom.xml:139
[adr]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-H01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:3