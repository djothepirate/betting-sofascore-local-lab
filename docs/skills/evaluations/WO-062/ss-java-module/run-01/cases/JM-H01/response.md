# Réexamen de la frontière Playwright — JM-H01, au 15 septembre 2026

**WO-030 qualifie un correctif précis : le refus d’une origine de qualification loopback dont le port dépasse `65535`, côté parent et côté worker. Les deux fichiers actuels conservent exactement les empreintes du correctif qualifié.** Cela confirme la conservation de ces gardes ; cela ne constitue pas une nouvelle qualification du runtime, du câblage Spring ou de l’ensemble de l’architecture.

Le skill `ss-java-module` a été lu intégralement avant l’analyse. Cette revue utilise uniquement les fichiers autorisés. Aucun build, test, navigateur, réseau, service, base de données ou changement de source n’a été exécuté.

## 1. Anomalie et correction

Sur la base historique `daf55bf76521f81893f86d04fde3c2903bf22362`, deux validations exigeaient seulement un port positif :

- `ProviderPlaywrightProperties.isSafeConfiguration()`, côté parent ;
- `ProviderPlaywrightWorkerConfiguration.parseOrigin()`, côté enfant.

Le discriminant était `http://127.0.0.1:65536` : le rapport indique que Java accepte cette représentation comme URI structurée, avec `getPort() == 65536`. **La validité syntaxique de l’URI ne garantit donc pas un port TCP utilisable.**

Le correctif, identifié par le commit `154349a2fbebe3fd0a43a63c7105f690ff04976b`, ajoute la borne supérieure aux deux frontières, en conservant les contrôles de schéma, d’hôte et des autres composants de l’URI. Le port IPC et le contrat `ScheduledEventsTransportRequest` étaient déjà correctement bornés. [Rapport WO-030, §§2–3][rapport-anomalie]

| Origine de qualification | Port | Résultat structurel attendu, parent et worker |
|---|---:|---|
| `http://127.0.0.1` | absent, représenté par `-1` | Refus |
| `http://127.0.0.1:0` | `0` | Refus |
| `http://127.0.0.1:1` | `1` | Acceptation |
| `http://127.0.0.1:65535` | `65535` | Acceptation |
| `http://127.0.0.1:65536` | `65536` | Refus |

L’acceptation des deux bornes signifie uniquement que la configuration respecte le contrat. Elle ne prouve ni présence d’un serveur, ni connexion réussie, ni autorisation de collecte.

### Concordance des fichiers actuels avec la preuve

La lecture locale par `Get-FileHash -Algorithm SHA256` donne :

| Fichier | SHA-256 actuel | Correspondance documentaire |
|---|---|---|
| `ProviderPlaywrightProperties.java` | `5e6adc1e415086a8762b982a6df05446f9081022c08938b01cbd477b1d5b89ca` | Empreinte après correctif du rapport |
| `ProviderPlaywrightWorkerConfiguration.java` | `c294e517133dde24d05b52b4206fff921d4279d6034b6bf247b9bce4199d9616` | Empreinte après correctif du rapport |
| Rapport autonome WO-030 | `9bd030f4cc92f32bdeee977089d3f4c5c709278fec4c24cc66ed8fb6d258be5f` | Empreinte enregistrée dans le Work Order |

Cette concordance porte sur ces trois fichiers. Elle ne démontre pas l’identité de l’ensemble du dépôt ou de son environnement avec ceux du 1er septembre.

## 2. Responsabilités et dépendances constatées

| Élément | Responsabilité visible | Limite de la preuve |
|---|---|---|
| **Configuration parente — `ProviderPlaywrightProperties`** | Porte les propriétés `sofascore.playwright`, avec `@Validated` et `@AssertTrue`. Vérifie les délais et l’origine de qualification : HTTP, hôte exact `127.0.0.1`, port dans `[1,65535]`, absence de user-info, query, fragment et chemin non vide. Refuse un override lorsque la qualification est désactivée. | La validité de cet objet ne suffit pas à rendre un transport disponible : activation, artefact et policy sont des conditions distinctes. |
| **Admission parente — policy et contrôle manuel** | Le test construit `J3ProviderQualificationPolicy`, puis `J3ManualCallControlService`, auquel il fournit `policy::snapshot` et `policy::localImportSnapshot`. Le contrôle décide si l’intention peut être revendiquée pour exécution. | Les implémentations de ces deux classes ne sont pas autorisées à la lecture. Leur usage et les assertions sont visibles dans le test, pas leur câblage complet en application. |
| **Configuration enfant — `ProviderPlaywrightWorkerConfiguration`** | `fromEnvironment()` valide successivement le port IPC, le jeton via le protocole, puis l’origine. `parseOrigin()` répète indépendamment la validation loopback et produit `INVALID_CONFIGURATION` en cas de refus. | Cette classe ne montre pas l’appelant qui ordonne connexion IPC, création du runtime et navigation. L’ordre global « avant tout démarrage » est attesté par le rapport historique, sans reconstruction actuelle du point d’entrée enfant. |
| **Construction des cibles dans l’enfant** | `uriFor(GetCommand)` reconstruit un chemin parmi six familles, puis le résout sur l’origine fixe fournisseur ou l’origine loopback explicitement qualifiée. Aucune URI arbitraire n’est reçue par cette méthode. | Construire l’URI ne réalise pas la navigation. Le transport et les contrôles de navigation ne figurent pas dans les sources autorisées. |
| **Contrat de domaine — `ScheduledEventsTransportRequest`** | Valide son origine simulée dès le constructeur et construit `/simulated/scheduled-events?date=…`. La classe lue ne dépend pas d’un transport concret. | Ce contrat concerne la simulation ; il ne remplace pas la garde d’entrée du worker Playwright. |

Sources : [configuration parente][properties], [configuration enfant][worker-config], [contrat simulé][request], [test de policy][policy-test].

**La répétition de la garde dans l’enfant est justifiée.** Le parent transmet l’origine par environnement selon WO-030 ; un enfant sollicité directement doit refuser la même entrée invalide sans dépendre de la validation parente.

Il ne faut pas non plus fusionner aveuglément les validateurs voisins : `ScheduledEventsTransportRequest` accepte le chemin racine `/`, alors que les deux configurations de qualification Playwright exigent un chemin vide. Leur intervalle de ports est commun, mais leurs contrats ne sont pas strictement identiques.

## 3. Composition Maven actuelle et activation

Le POM décrit **un projet Maven monomodule**, avec Spring Boot `4.1.0` et Java `25`, conformément à `AGENTS.md`. Les répertoires spécialisés sont des ensembles de sources ajoutés par profils, pas des sous-modules. [POM][pom]

| Configuration Maven | Effet déclaré |
|---|---|
| Build standard | Sources et sorties standard ; aucune dépendance Playwright directement déclarée dans les dépendances communes. |
| `provider-playwright-runtime` | Ajoute `com.microsoft.playwright:playwright`, `src/provider-playwright/java` et `src/provider-playwright-test/java`. Utilise les sorties séparées `target/provider-playwright-runtime/classes` et `test-classes`. |
| Phase `package` de ce profil | Produit un artefact classifié `provider-playwright-worker`, avec `ProviderPlaywrightWorkerMain` comme classe principale. |
| `provider-playwright-local-qualification` | Ajoute `src/provider-playwright-qualification-test/java` et une exécution Failsafe sélectionnant `**/*LocalQualificationIT.java`. |
| `j7-browser-origin-loopback-qualification` | Ajoute séparément Playwright en dépendance de test pour la qualification J7. Ce profil ne fait pas partie de la correction WO-030. |

Trois opérations doivent rester distinctes : **compiler les sources du runtime, produire le JAR worker, puis lancer effectivement l’enfant et le navigateur**. L’activation du profil Maven ne prouve pas ce dernier lancement.

Les contrôles visibles apportent aussi ces précisions :

- Surefire et Failsafe reçoivent `sofascore.j3.runtime-enabled=false`.
- Le profil `sofascore-live-test` reste explicitement bloqué par Enforcer.
- Les propriétés Java par défaut gardent Playwright désactivé, sans JAR sélectionné et sans qualification loopback.
- Les fichiers YAML et les configurations Spring d’assemblage sont hors corpus : ni leur binding effectif, ni les beans injectés, ni l’adresse effective du serveur ne sont revalidés ici.
- Aucun contrôle ArchUnit n’est établi par les sources lues. Le lanceur `Verify-Local.ps1` n’est pas autorisé à la lecture ; aucune conclusion ne lui est attribuée.

## 4. Contrôles réellement appelés dans les tests

Les points suivants décrivent **le code de test actuel**, sans annoncer son exécution pendant cette revue.

### Propriétés : méthode directe et Bean Validation

`acceptsOnlyTheClosedTcpPortRangeBoundaries()` exerce les cinq cas de la matrice par deux voies :

1. appel direct à `isSafeConfiguration()` ;
2. appel à `validator.validate(properties)`.

Pour `65536`, il exige notamment une violation sur `safeConfiguration`. Ce n’est donc pas une simple assertion sur la valeur de la constante.

Le validateur est construit avec `Validation.buildDefaultValidatorFactory()`. Ce test ne charge pas un contexte Spring et ne démontre pas le binding depuis une configuration externe. [ProviderPlaywrightPropertiesTest][properties-test]

### Policy : tentative effective de claim

`rejectsAnOutOfRangeLoopbackPortBeforeAProviderClaim()` :

- active la qualification avec `http://127.0.0.1:65536` ;
- vérifie le snapshot indisponible, l’origine fournisseur absente et les deux blocages exacts :
  `PLAYWRIGHT_CONFIGURATION_UNSAFE` et `PLAYWRIGHT_LOOPBACK_QUALIFICATION_ACTIVE` ;
- prépare et confirme une intention ;
- **appelle réellement `claimExecution(requestId)`** ;
- attend `PROVIDER_TRANSPORT_UNAVAILABLE` ;
- vérifie `executionMayContinue=false` et le maintien de l’intention à `CONFIRMED_READY`.

Le rapport signale qu’une première version ne vérifiait que le snapshot ; cet appel au claim a été ajouté après revue indépendante.

**Nuance causale :** la qualification loopback active constitue elle-même un blocage du parcours fournisseur. Le refus du claim, pris isolément, ne suffit donc pas à démontrer la borne supérieure. L’assertion sur `PLAYWRIGHT_CONFIGURATION_UNSAFE` et les tests directs de configuration rendent l’ensemble discriminant.

Le JAR utilisé est un fichier temporaire fabriqué avec un manifeste `Start-Class`. Le test exerce la policy sur cet artefact minimal ; il ne prouve pas qu’un worker complet démarre. [J3ProviderQualificationPolicyTest][policy-test]

### Worker : validation autonome en mémoire

`acceptsOnlyTheClosedLoopbackOriginTcpPortRange()` passe par les helpers qui appellent `fromEnvironment()`, avec port IPC et jeton de test valides :

- les cas refusés attendent `IllegalArgumentException` avec `INVALID_CONFIGURATION` ;
- les bornes acceptées sont contrôlées dans l’URI retournée par `uriFor()`.

Les autres tests lisent ou écrivent des trames sur des tableaux d’octets, vérifient les six formes de commande, les arguments et les gardes `START`/`READY`/`CLOSED`. Cela n’équivaut pas à une communication avec une JVM enfant ni à une observation de Chromium.

Le fichier actuel contient notamment une assertion `VERSION == 10` : la qualification WO-030 ne doit pas être étendue par déclaration aux évolutions ultérieures du protocole. [ProviderPlaywrightWorkerProtocolTest][worker-test]

## 5. Ce que démontre la preuve historique

Le rapport identifie une qualification au **1er septembre 2026 à 13:47:21 UTC**, avec le verdict `PASS_LOCAL_FAIL_CLOSED`.

### Exécutions rapportées

| Commande historique | Résultat consigné |
|---|---|
| `mvnw.cmd --offline -Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest test` | `PASS` — `14/0/0/0` |
| `mvnw.cmd --offline -Pprovider-playwright-runtime -Dtest=ProviderPlaywrightWorkerProtocolTest test` | `PASS` — `11/0/0/0` |
| `mvnw.cmd --offline clean verify` | Surefire `1043/0/0/5` ; Failsafe `84/0/0/0` |
| `mvnw.cmd --offline -Pprovider-playwright-runtime clean verify` | Surefire `1065/0/0/5` ; Failsafe `84/0/0/0` |

Ces valeurs sont les résultats **rapportés à l’époque**, dans l’ordre tests/échecs/erreurs/ignorés. Les anciens rapports bruts ne sont pas disponibles dans le cas ; aucun de ces résultats n’a été reproduit ici. [Rapport, vérifications exécutées][rapport-verifications]

Le rapport distingue correctement :

- la matrice de ports, exercée sans connexion ni navigateur ;
- les suites complètes, qui ont exécuté séparément des intégrations loopback et Testcontainers ;
- zéro appel fournisseur sous WO-030.

`--offline` concerne la résolution Maven ; il n’interdit pas à un test d’utiliser sockets, Docker, base de données ou navigateur.

### Écart avec le POM actuel

Le rapport historique dit que les intégrations étaient liées automatiquement à `verify`. **Le POM actuel place les objectifs Failsafe `integration-test` et `verify` des intégrations générales dans le profil `integration-tests`**, avec les inclusions :

```text
**/integration/**/*IT.java
**/ProviderResilienceTransportPersistenceIT.java
```

La configuration Failsafe commune ne déclare pas ces exécutions. On ne peut donc pas reprendre aujourd’hui les anciens totaux Failsafe comme résultat attendu de `clean verify` seul, ni déduire que toutes les IT présentes seraient sélectionnées.

### Qualification et décision propriétaire : deux moments

Le rapport conserve l’état `READY_FOR_OWNER_REVIEW`. Le Work Order enregistre ensuite une décision `VALIDATE` à **14:00:53 UTC**, avec correspondance du commit et de l’empreinte du rapport, puis déplacement documentaire vers `completed`.

Il s’agit d’une succession d’événements, pas d’une validation encore manquante. Cette décision autorisait le déplacement documentaire ; elle n’autorisait ni réseau fournisseur, ni push, ni merge, ni production. [Work Order, décision enregistrée][wo-decision]

## 6. Gouvernance applicable au 15 septembre

Les statuts restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

**L’ancien parcours manuel testé par WO-030 ne définit plus à lui seul tous les déclenchements J3 autorisés.**

L’exception de `AGENTS.md` et l’ADR-SS-007 adoptent :

- le clic direct J3 et les ordres quotidiens ou planifiés durables dans le Lab local configuré ;
- une préférence automatique initialement activée puis persistée, sans rendre le transport disponible si ses prérequis manquent ;
- la suppression des anciennes confirmations d’intention J3 ;
- en v0.3, le geste direct pour le tournoi sélectionné, tout en conservant les contrôles historiques internes nécessaires aux qualifications.

Il serait donc incorrect d’exiger à nouveau, pour ces parcours adoptés, la séquence historique d’activation et de confirmation visible dans le test. [AGENTS.md][agents] ; [ADR-SS-007, adoption et portée][adr]

Pour une pause live, le contrat adopté conserve un seul propriétaire et un seul garde durable : contexte live conservé, contexte J3 neuf et temporaire dans le même worker, **un seul contexte autorisé à émettre**, aucun état de session transféré. La reprise exige le nettoyage prouvé du contexte J3 et les garanties restantes ; une perte du contexte live ne permet pas sa recréation automatique.

Ces règles n’ouvrent ni accès fournisseur général, ni nouvel endpoint, ni navigateur dans les tests standards. Leur implémentation actuelle complète n’est pas démontrée par les fichiers disponibles. WO-030 n’en constitue pas une qualification.

## 7. Vérification minimale proposée pour une nouvelle révision

**Aucune nouvelle correction de la borne n’est nécessaire dans les deux fichiers lus.** La proposition minimale consiste à conserver les gardes indépendantes et à renouveler une preuve ciblée sur la révision à qualifier.

### A. Identifier la révision et conserver le périmètre

Consigner le SHA réellement testé, le diff, les versions Java/Maven, les profils et les commandes effectives. L’entrée du cas donne `6648dd423e556b5248b8793a539ae85a7680f9bc` comme `_policy_source_commit` ; cela n’est pas une vérification du `HEAD` local. [Entrée du cas][input]

Aucune nouvelle interface, dépendance standard Playwright ou extraction en sous-module n’est justifiée par ce défaut.

### B. Réexécuter les contrôles ciblés, dans un futur cadre autorisant les tests

Commandes proposées d’après le POM fourni, **non exécutées ici** :

```powershell
.\mvnw.cmd --offline "-Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest" clean test
.\mvnw.cmd --offline -Pprovider-playwright-runtime "-Dtest=ProviderPlaywrightWorkerProtocolTest" clean test
```

Elles ciblent Surefire et les bonnes sources Maven. Elles ne produisent pas le JAR de phase `package` et ne constituent pas une qualification native du navigateur.

La preuve attendue doit conserver :

- les cinq ports, avec acceptation exclusivement structurelle des bornes ;
- les deux voies de validation parente ;
- le véritable claim refusé et l’état inchangé ;
- le refus enfant autonome via `fromEnvironment()` ;
- les gardes d’origine et l’interdiction d’override hors qualification.

Si la validation d’URI évolue, compléter les cas concernés — schéma, hôte, chemin, user-info, query et fragment — sans modifier implicitement les contrats voisins.

### C. Adapter les vérifications à la conclusion recherchée

Pour affirmer seulement **« la borne TCP est conservée »**, la revue et les tests ciblés suffisent au périmètre technique.

Pour affirmer **« le runtime actuel refuse toujours avant tout effet »**, il manque une revue du point d’entrée enfant, du superviseur, de la transmission d’environnement et de l’assemblage Spring, puis un contrôle adapté de l’absence de lancement sur configuration invalide.

Pour étendre la conclusion aux ordres durables J3 ou à la pause live, il faut une preuve propre à ces chemins actuels ; le test du contrôle manuel historique ne peut pas la remplacer.

Enfin, une livraison de code devra satisfaire les contrôles généraux du dépôt, dont `clean verify` et la qualification du profil runtime. Les intégrations de persistance deviennent nécessaires si ce périmètre change ; elles ne constituent pas une condition technique supplémentaire du seul correctif de port.

## Conclusion de revue

**La preuve WO-030 est cohérente et précisément bornée.** Elle documente la correction des deux gardes et une tentative de claim effectivement refusée. Les empreintes actuelles confirment la conservation exacte des deux fichiers corrigés.

Les éléments manquants concernent une nouvelle exécution, le câblage complet et les parcours introduits depuis. Ils empêchent une requalification globale, sans invalider la preuve historique.

**Bilan de cette intervention :** lectures locales avec `Get-Content` et `rg`, comparaison de trois empreintes avec `Get-FileHash`, concordances obtenues ; aucun fichier modifié et aucun test exécuté.

[rapport-anomalie]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md:28
[properties]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/src/main/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightProperties.java:98
[worker-config]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerConfiguration.java:29
[request]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/src/main/java/com/bettingproject/sofascorelocal/domain/provider/ScheduledEventsTransportRequest.java:44
[policy-test]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/src/test/java/com/bettingproject/sofascorelocal/application/network/J3ProviderQualificationPolicyTest.java:178
[pom]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/pom.xml:169
[properties-test]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/src/test/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightPropertiesTest.java:47
[worker-test]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocolTest.java:254
[rapport-verifications]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md:83
[wo-decision]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/docs/work_orders/completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md:153
[agents]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/AGENTS.md:39
[adr]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:14
[input]: /C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-H01/input.json:2