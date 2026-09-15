# Réexamen de la frontière Playwright — WO-030

**WO-030 documente une correction locale précise : refuser une origine de qualification dont le port dépasse `65535`, indépendamment dans le parent et dans le worker enfant. Les fichiers actuels conservent exactement les deux classes qualifiées. Cette preuve ne constitue pas une qualification nouvelle du runtime, de son câblage Spring ou du parcours J3 actuel.**

Revue du cas **JM-H01**, au **15 septembre 2026**, après lecture intégrale du skill demandé. Les opérations réalisées se limitent à des lectures locales, recherches dans les fichiers autorisés et calculs SHA-256. **Aucun build, test, accès réseau ou changement de fichier n’a été effectué.**

## 1. Identité et chronologie de la preuve

L’entrée `input.json` fournit le commit de provenance des politiques `6648dd423e556b5248b8793a539ae85a7680f9bc`. Il s’agit d’une métadonnée du corpus ; aucun contrôle Git ne permet ici de l’assimiler au HEAD du répertoire d’évaluation `JM-H01`.

Les documents historiques identifient un autre worktree, `.tmp/w30`, et les révisions suivantes :

| Repère historique | Valeur documentée |
|---|---|
| Base WO-030 | `daf55bf76521f81893f86d04fde3c2903bf22362` |
| Commit d’ouverture | `1c7c1358527c0461824a0de0e76b03e54c38c435` |
| Commit d’implémentation qualifié | `154349a2fbebe3fd0a43a63c7105f690ff04976b` |
| Qualification | 1er septembre 2026, `13:47:21.0195627Z` |
| Validation propriétaire enregistrée | 1er septembre 2026, `14:00:53Z` |

Le **rapport autonome** porte `PASS_LOCAL_FAIL_CLOSED` et `READY_FOR_OWNER_REVIEW`. Le **Work Order**, mis à jour ultérieurement, porte `VALIDATED`, consigne la décision propriétaire `VALIDATE` et le déplacement autorisé vers `completed`. Ces états décrivent deux moments successifs.

La décision enregistrée valide la disponibilité locale et le déplacement documentaire. Elle exclut explicitement les appels fournisseur ou receiver réels, le déploiement VPS, la production, le push et le merge. La présence du WO dans `completed` ne démontre donc pas une fusion.

Sources : [rapport, identité et verdict](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md:3) ; [WO-030, registre et décision propriétaire](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/docs/work_orders/completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md:139).

## 2. L’anomalie et sa correction

### Défaut historique

Deux validations exigeaient seulement un port explicite positif :

- `ProviderPlaywrightProperties.isSafeConfiguration()` dans le parent ;
- `ProviderPlaywrightWorkerConfiguration.parseOrigin()` dans le worker.

Le discriminant était `http://127.0.0.1:65536`. Le rapport indique que Java peut le représenter comme une URI structurée avec un port égal à `65536`. **La validité syntaxique de cette URI ne garantit pas que son port appartienne à l’intervalle TCP autorisé.**

Le parent pouvait donc accepter cette configuration ; le worker pouvait accepter la même valeur si elle lui était présentée directement par environnement. WO-030 ajoute la borne supérieure dans **chacune** des deux validations. La validation du parent ne dispense pas le worker de contrôler sa propre entrée.

### Contrat conservé dans les fichiers actuels

| Origine de qualification | Parent | Worker | Portée de l’acceptation |
|---|---|---|---|
| `http://127.0.0.1` | Refus | Refus | Port absent |
| `http://127.0.0.1:0` | Refus | Refus | Hors intervalle |
| `http://127.0.0.1:1` | Acceptation | Acceptation | Structure uniquement |
| `http://127.0.0.1:65535` | Acceptation | Acceptation | Structure uniquement |
| `http://127.0.0.1:65536` | Refus | Refus | Hors intervalle |

Les deux classes conservent aussi les restrictions sur le schéma `http`, l’hôte exact `127.0.0.1`, l’absence de user-info, query, fragment et chemin non vide. Sans mode qualification, une origine de substitution non vide est refusée.

**Accepter les ports `1` et `65535` ne prouve ni qu’un serveur y écoute, ni qu’une connexion réussirait.** Les tests de borne ne tentent aucune connexion.

Le port IPC et `ScheduledEventsTransportRequest` étaient déjà bornés à `[1, 65535]`, selon le WO. Leur contrat n’a pas été élargi par ce correctif.

### Concordance vérifiée aujourd’hui

Les SHA-256 calculés pendant cette revue correspondent exactement aux empreintes consignées :

| Fichier | SHA-256 actuel, identique à la référence documentaire |
|---|---|
| `ProviderPlaywrightProperties.java` | `5e6adc1e415086a8762b982a6df05446f9081022c08938b01cbd477b1d5b89ca` |
| `ProviderPlaywrightWorkerConfiguration.java` | `c294e517133dde24d05b52b4206fff921d4279d6034b6bf247b9bce4199d9616` |
| Rapport autonome WO-030 | `9bd030f4cc92f32bdeee977089d3f4c5c709278fec4c24cc66ed8fb6d258be5f` |

Cela établit la concordance de ces trois fichiers avec les empreintes enregistrées. Cela ne reconstitue pas l’ensemble du commit historique, ses dépendances ou son environnement d’exécution.

## 3. Responsabilités parent/enfant et dépendances observables

| Élément | Responsabilité visible | Limite de la preuve |
|---|---|---|
| **Parent — `ProviderPlaywrightProperties`** | Propriétés sous `sofascore.playwright`, défaut `enabled=false`, aucun JAR sélectionné ; mémoire et délais bornés ; validation de l’origine par `isSafeConfiguration()` annotée `@AssertTrue`. | `@ConfigurationProperties` et `@Validated` déclarent le contrat. Les tests lus instancient directement l’objet ; ils ne prouvent pas son binding dans l’application complète. |
| **Admission applicative — policy et contrôle J3** | Les tests construisent `J3ProviderQualificationPolicy`, puis un `J3ManualCallControlService` alimenté par `policy::snapshot` et `policy::localImportSnapshot`. Le contrôle refuse le claim lorsque le transport est indisponible. | Les implémentations de ces services et leur injection Spring ne sont pas dans le corpus autorisé. |
| **Enfant — `ProviderPlaywrightWorkerConfiguration`** | `fromEnvironment()` vérifie successivement le port IPC, le jeton via `ProviderPlaywrightWorkerProtocol.validateToken()`, puis l’origine. `parseOrigin()` refuse une configuration invalide avec `IllegalArgumentException("INVALID_CONFIGURATION")`. | Le fichier montre la validation avant retour de la configuration. L’ordre complet dans `WorkerMain`, avant ouverture IPC ou navigateur, n’est pas réinspectable ici. |
| **Construction des URI dans l’enfant** | `uriFor(GetCommand)` reconstruit les chemins des six familles prévues, à partir d’une origine fournisseur fixe ou d’une origine loopback explicitement qualifiée. | Construire une URI ne déclenche pas une navigation et ne prouve pas les contrôles d’un navigateur réel. |
| **Domaine — `ScheduledEventsTransportRequest`** | Valeur immuable : origine simulée et date cible obligatoires, port borné, clé de requête et URI `/simulated/scheduled-events?date=…`. Aucun transport concret appelé dans cette classe. | Ce contrat distinct accepte aussi le chemin racine `/`, contrairement aux origines de qualification Playwright. Une mutualisation naïve pourrait modifier les comportements existants. |

La `LocalDate` du contrat simulé désigne la **date cible** utilisée dans la clé et l’URI ; elle ne représente pas un instant d’exécution ou de réception.

Sources : [propriétés parentes](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/src/main/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightProperties.java:98), [configuration enfant](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerConfiguration.java:29), [contrat simulé](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/src/main/java/com/bettingproject/sofascorelocal/domain/provider/ScheduledEventsTransportRequest.java:8).

### Composition Maven actuelle

Le POM décrit un **projet monomodule**, avec Java **25**, Spring Boot **4.1.0** et la version applicative `0.1.0-rc.1-SNAPSHOT`. Les répertoires spécialisés ne sont pas des sous-modules Maven.

Le profil `provider-playwright-runtime` :

- ajoute la dépendance Playwright et `src/provider-playwright/java` ;
- ajoute les tests de `src/provider-playwright-test/java` ;
- sépare les classes compilées dans `target/provider-playwright-runtime/classes` et `test-classes`, au lieu des sorties standard ;
- produit à la phase `package` un JAR classifié `provider-playwright-worker`, avec `ProviderPlaywrightWorkerMain` comme point d’entrée.

**Compilation du runtime, production du JAR et lancement effectif sont trois opérations distinctes.** L’activation du profil ne démontre pas un démarrage de Chromium. La dépendance Playwright n’est pas déclarée dans les dépendances standard ; le profil séparé de qualification navigateur J7 la déclare également, avec portée `test`.

Le profil Maven doit aussi être distingué des propriétés d’exécution Spring : Surefire et Failsafe fixent ici `sofascore.j3.runtime-enabled=false`. Le câblage complet du superviseur de JVM enfant, de la factory et des beans ne peut pas être confirmé avec les seuls fichiers autorisés.

Source : [POM, plugins et profils](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/pom.xml:169).

## 4. Contrôles réellement invoqués par les tests

### Propriétés : validation directe et Bean Validation

`acceptsOnlyTheClosedTcpPortRangeBoundaries()` exerce les cinq cas de la matrice. Il appelle à la fois :

- `properties.isSafeConfiguration()` ;
- `validator.validate(properties)`.

Pour `65536`, il vérifie une violation sur `safeConfiguration`. Il s’agit d’une validation Java/Jakarta, sans contexte Spring ni navigateur.

Source : [test des propriétés, ligne 48](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/src/test/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightPropertiesTest.java:48).

### Policy : le claim est effectivement appelé

`rejectsAnOutOfRangeLoopbackPortBeforeAProviderClaim()` ne s’arrête pas au snapshot. Il :

1. configure l’origine `:65536` et constate les blockers ;
2. construit le contrôle manuel, prépare puis confirme une intention ;
3. appelle réellement `control.claimExecution(requestId)` ;
4. attend `PROVIDER_TRANSPORT_UNAVAILABLE` ;
5. vérifie `executionMayContinue=false` ;
6. vérifie que l’intention reste `CONFIRMED_READY`.

Le rapport précise que l’appel direct au claim a été ajouté après une première revue qui avait relevé son absence.

**Nuance de causalité :** le scénario possède deux blockers, `PLAYWRIGHT_CONFIGURATION_UNSAFE` et `PLAYWRIGHT_LOOPBACK_QUALIFICATION_ACTIVE`. Il prouve le refus effectif dans cette configuration combinée. Le test de propriétés apporte le discriminant propre à la borne supérieure.

Le JAR utilisé est créé temporairement par le test avec un manifeste `Start-Class`. Il ne s’agit pas d’un worker exécutable qualifié ou lancé.

Source : [test de policy, ligne 179](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/src/test/java/com/bettingproject/sofascorelocal/application/network/J3ProviderQualificationPolicyTest.java:179).

### Worker : configuration et protocole en mémoire

`acceptsOnlyTheClosedLoopbackOriginTcpPortRange()` passe par `fromEnvironment()` ; pour les deux ports acceptés, il appelle `uriFor()` et contrôle le port obtenu. Pour les refus, il attend `INVALID_CONFIGURATION`.

Les autres méthodes du fichier manipulent notamment des commandes et des trames dans des flux d’octets en mémoire. Elles ne constituent pas une qualification native du processus enfant, de sockets IPC ou du navigateur.

Le fichier actuel contient une assertion `ProviderPlaywrightWorkerProtocol.VERSION == 10`, bien que le nom de cette méthode mentionne V9. Les noms historiques des méthodes ne suffisent donc pas à dater la version testée ; les anciens totaux ne doivent pas être réutilisés comme résultats actuels.

Source : [tests worker, version et cas de borne](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocolTest.java:67).

## 5. Résultats historiques et limites

### Exécutions consignées

Ces résultats sont **rapportés par la preuve versionnée**, sans accès aux anciens rapports bruts :

| Commande historique | Résultat consigné |
|---|---|
| `mvnw.cmd --offline -Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest test` | `PASS` — `14/0/0/0` |
| `mvnw.cmd --offline -Pprovider-playwright-runtime -Dtest=ProviderPlaywrightWorkerProtocolTest test` | `PASS` — `11/0/0/0` |
| `mvnw.cmd --offline clean verify` | Surefire `1043/0/0/5`, Failsafe `84/0/0/0` |
| `mvnw.cmd --offline -Pprovider-playwright-runtime clean verify` | Surefire `1065/0/0/5`, Failsafe `84/0/0/0` |

Le rapport consigne également les intégrations Flyway V1→V29, ledger J7, mTLS et loopback, ainsi que les contrôles du diff, des secrets, des artefacts interdits, de l’adresse locale, des flags et des ressources résiduelles. Compose est marqué `NOT_RUN_NOT_REQUIRED` : `.env` absent et aucun changement Compose sous WO-030.

### Incidents à conserver dans le bilan

- **Première tentative ciblée arrêtée avant les tests** : accès impossible à `spring-orm-7.0.8.jar` dans le cache Maven depuis le sandbox. La relance hors sandbox, cache explicitement désigné et toujours sous `--offline`, est rapportée réussie. Cet incident d’accès local ne constitue pas un échec applicatif. La ligne complète précisant le cache n’est pas fournie.
- **Avertissements Hikari pendant des fermetures d’intégrations** : le rapport les attribue à des connexions Testcontainers déjà fermées et indique des tests et builds finaux réussis.
- **Insuffisance initiale du test de policy** : corrigée avant les portes finales par l’ajout du claim effectif.

### Différence avec le POM actuel

Le rapport historique décrit des intégrations automatiquement liées à `verify`. Dans le POM actuel :

- la déclaration globale de Failsafe configure ses arguments et propriétés ;
- les goals `integration-test` et `verify` sont liés par le profil `integration-tests`, avec les inclusions `**/integration/**/*IT.java` et `**/ProviderResilienceTransportPersistenceIT.java` ;
- la qualification Playwright native possède un profil distinct, `provider-playwright-local-qualification`, ses propres sources et l’inclusion `**/*LocalQualificationIT.java`.

**Un `verify` standard actuel ne permet donc pas de revendiquer les 84 intégrations historiques.** Le POM historique et les rapports bruts ne sont pas disponibles pour reconstruire cette ancienne sélection.

Enfin, `--offline` concerne la résolution Maven. Les suites historiques ont pu utiliser du loopback et Testcontainers ; ce drapeau ne garantit pas, à lui seul, l’absence de sockets, de base ou de navigateur.

### Conclusion probatoire

La preuve permet de retenir :

- une qualification historique documentée du refus de port hors plage ;
- une validation propriétaire documentaire ultérieure ;
- la conservation exacte des deux classes corrigées ;
- la présence actuelle de tests visant ces comportements.

Elle ne permet pas de conclure à une réussite actuelle des tests, à la conformité exhaustive de l’architecture, au bon câblage Spring, au lancement/nettoyage réel du worker, ni à la qualification des évolutions J3 ultérieures. Aucun contrôle ArchUnit n’est établi par le corpus ; le lanceur de vérification n’est pas autorisé à la lecture.

## 6. Gouvernance applicable au 15 septembre 2026

WO-030 reste une preuve datée du 1er septembre. Ses mentions de lancement manuel ne suffisent pas à décrire toute la gouvernance actuelle.

`AGENTS.md` inscrit l’exception J3 adoptée le 13 septembre. L’ADR-SS-007 fourni est en **v0.3**, avec le complément du 15 septembre :

- un clic direct ou un ordre quotidien/planifié durable peut constituer l’autorité J3 ;
- les anciennes confirmations et manipulations de circuit ne doivent pas être rejouées secrètement par le nouveau parcours ;
- le complément étend le geste direct au parcours tournoi concerné ;
- les contrôles historiques internes peuvent rester utilisés par les qualifications ;
- les confirmations propres aux campagnes J4/J5 et à J7 restent hors de cette exception.

Pendant une pause live, le contrat adopté prévoit un contexte J3 neuf, temporaire et non persistant dans le même worker. Un seul contexte émet ; aucun état de session n’est transféré. Le contexte live est conservé, et la reprise dépend notamment du nettoyage établi et des budgets restants. Une perte du contexte live n’autorise pas sa recréation automatique.

Ainsi, le test historique utilisant `prepare/confirm/claimExecution` reste pertinent pour le contrôle interne qu’il exerce ; **il ne qualifie pas le clic direct, les ordres durables ou la pause live actuels**. L’ADR rapporte leur évolution, mais leurs sources et preuves détaillées sont hors corpus.

Les statuts demeurent `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`. La gouvernance Git actuelle impose aux nouveaux WO une branche de train et un worktree distincts ; l’ancienne branche `codex/j9-wo030-…` reste une référence historique.

Sources : [AGENTS, exception J3](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/AGENTS.md:54) ; [ADR-SS-007, adoption et complément v0.3](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-H01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:3).

## 7. Vérification minimale proposée pour une nouvelle révision

**Aucune correction supplémentaire des deux gardes n’est justifiée par les éléments lus.** Pour une révision limitée à cette borne, conserver les deux validations indépendantes et établir une nouvelle preuve ciblée suffit comme noyau de qualification.

Toutes les actions suivantes sont **proposées, non exécutées** :

| Action future | Critère et portée | Relais |
|---|---|---|
| Identifier le SHA candidat, le diff, le POM et le lanceur applicables | Associer les résultats aux bons fichiers et vérifier la sélection réelle des tests et les sorties séparées. | `ss-verify` |
| Rejouer les tests parents | Vérifier les cinq bornes, Bean Validation et le refus effectif de `claimExecution()`. | `ss-verify` |
| Rejouer le test worker sous son profil Maven | Vérifier le refus autonome à l’entrée et la reconstruction structurelle des URI, sans navigateur. | `ss-verify` |
| Compléter les contrôles de livraison du changement | `clean verify` standard ; profil runtime complet si concerné ; conserver les rapports avant un nettoyage suivant, vérifier diff, secrets, adresse et défauts réseau. | `ss-verify` |
| Si le périmètre s’étend au contrat ou au protocole | Examiner compatibilité, parsing et replay ; la preuve de borne ne couvre pas cette extension. | `ss-data-contract-replay`, puis `ss-verify` |
| Si le périmètre touche transactions, schéma ou ledger | Définir les invariants de persistance et les intégrations requises. Aucune telle évolution n’est nécessaire pour la seule borne. | `ss-postgres-change`, puis `ss-verify` |

Les deux commandes candidates pour le noyau ciblé, à faire confirmer par la revue du lanceur dans un futur contexte autorisé, sont :

```powershell
.\mvnw.cmd --offline "-Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest" clean test
.\mvnw.cmd --offline -Pprovider-playwright-runtime "-Dtest=ProviderPlaywrightWorkerProtocolTest" clean test
```

Pour le seul correctif de borne, une qualification native Chromium ou PostgreSQL n’apporte pas le discriminant recherché. Si une nouvelle révision modifie l’ordre de démarrage, la supervision IPC, les beans ou le transfert d’autorité entre contextes, elle exigera des sources et contrôles supplémentaires sous `ss-verify`.

**Verdict final : correctif WO-030 historiquement qualifié et conservé à l’identique dans les deux classes actuelles ; aucune nouvelle qualification d’exécution établie.** La revue n’a modifié aucun fichier et n’a exécuté aucun des contrôles proposés.