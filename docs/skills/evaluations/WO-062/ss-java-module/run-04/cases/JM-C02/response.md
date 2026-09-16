# Revue du dossier J3 — JM-C02

**Les cinq affirmations de couverture ne sont pas soutenables telles qu’écrites, et les quatre propositions de scénario contredisent le contrat adopté ou les contrôles visibles.** Le code fournit des éléments précis sur l’architecture et les protections prévues ; cette revue ne constitue pas une qualification exécutée du candidat courant.

## 1. Périmètre et valeur des preuves

Le skill [`ss-java-module`][skill] a été lu intégralement avec un outil local avant l’analyse. Seuls les fichiers autorisés ont été consultés, par sections pertinentes. Aucun build, test, réseau, navigateur, accès à une base, Docker, installation ou changement de source n’a été effectué.

L’entrée présente explicitement les affirmations et propositions comme **synthétiques**, sans changement appliqué ni résultat nouveau. Son `_policy_source_commit` vaut `6648dd423e556b5248b8793a539ae85a7680f9bc` : c’est une provenance déclarée dans l’entrée, **pas une preuve du SHA courant du worktree ni du SHA effectivement testé**. Ces derniers restent non vérifiés. [Entrée du cas][input]

Dans cette restitution :

- **Établi** signifie visible dans le contrat, le code ou les assertions d’un test lu ; cela ne signifie pas « exécuté avec succès ici ».
- **Contredit** signifie incompatible avec une règle adoptée ou avec le mécanisme effectivement décrit dans les sources.
- **Non vérifié** désigne une propriété qui exige une exécution, une composition complète ou des fichiers absents du périmètre autorisé.

Les statuts restent `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY`. Le POM déclare Spring Boot **4.1.0**, Java **25**, Maven **3.9.x** via son garde et la version applicative `0.1.0-rc.1-SNAPSHOT`. Il décrit **un projet Maven monomodule** ; les répertoires de sources du worker ne sont pas des sous-modules. [AGENTS][agents] · [POM][pom]

## 2. Verdict sur les cinq affirmations de couverture

| Affirmation | Ce qui est établi | Ce qui est contredit ou non vérifié |
|---|---|---|
| **`standard-native`** — le `clean verify` standard sélectionne nécessairement `J3LivePauseWorkerQualificationIT` et prouve la conservation du contexte live | Le test se trouve dans `src/provider-playwright-qualification-test/java`, ajouté par un profil dédié. Le POM distingue ce profil du build standard et impose `sofascore.j3.runtime-enabled=false` à Surefire et Failsafe. [POM, configuration des tests][pom-test-config] · [POM, profils][pom-profiles] | **Contredit pour la sélection nécessaire.** Le répertoire de qualification n’est pas ajouté par le build standard. Le POM fourni n’attache pas non plus les objectifs Failsafe au cycle hors des profils concernés. **Non vérifié pour la preuve courante du navigateur** : aucune exécution nouvelle n’est fournie. Le rapport décrit expressément une commande native distincte. [Rapport, qualification explicite][report-native] |
| **`generic-native-profile`** — les deux profils sélectionnent automatiquement tous les `*QualificationIT` | `provider-playwright-runtime` ajoute les sources worker et leurs tests, la dépendance Playwright et le packaging du JAR worker. `provider-playwright-local-qualification` ajoute les sources de qualification et l’exécution Failsafe `provider-playwright-loopback-qualification`. [POM, profils][pom-profiles] | **Contredit.** L’inclusion déclarée est exactement `**/*LocalQualificationIT.java`, et non `**/*QualificationIT.java`. `J3LivePauseWorkerQualificationIT` ne correspond pas à ce motif. La commande historique le sélectionne expressément avec `-Dit.test=J3LivePauseWorkerQualificationIT` et les objectifs Failsafe qualifiés par l’identifiant d’exécution. [POM, inclusion native][pom-native] · [Rapport][report-native] |
| **`archunit-green`** — une règle ArchUnit verte interdit toutes les dépendances `application → adapter` | Le lanceur contient une recherche textuelle du motif `\bcom\.microsoft\.playwright\b` dans **`src/main`**, sur les extensions `.java`, `.yml`, `.yaml` et `.properties`. Ce contrôle vise la bibliothèque externe ; il n’interdit pas les contrats internes portant le nom Playwright. [Verify-Local.ps1][verify-guard] | **Contredit pour l’interdiction universelle.** `J3CollectionExecutor` importe le catalogue concret `adapter.sofascore.SofascoreEndpointCatalog`, `adapter.sofascore.scheduledevents.*` et l’exception `adapter.sofascore.transport.ScheduledEventsTransportException`. Il construit notamment `ScheduledEventsV1Parser`. **Aucune dépendance ArchUnit, règle correspondante ou preuve verte n’est établie dans le corpus autorisé.** Le scan textuel n’est pas une analyse exhaustive des dépendances. [Executor][executor] |
| **`spring-primary`** — le test Spring cité prouve l’injection du décorateur `@Primary` dans le contexte complet et toutes les classes J3 | Le test utilise `ApplicationContextRunner`, active deux classes de propriétés et importe uniquement `ChildJvmPlaywrightProviderSupervisor`. Il affirme que les deux contrats, factory et supervisor, résolvent vers **ce même superviseur**. [Test Spring][spring-test] | **Contredit quant à la portée du test.** Le décorateur n’est pas chargé par cette configuration. Son `@Primary @Component` et son constructeur injectant le superviseur concret sont visibles, mais leur utilisation dans le contexte complet reste **non vérifiée**. De plus, `J3CollectionExecutor` reçoit un `ProviderAccess` à l’exécution : toutes les classes J3 n’injectent pas une factory. [Factory résiliente][resilient] · [Executor][executor] |
| **`historical-current`** — le rapport du 14 septembre qualifie à lui seul le candidat courant | Le rapport documente un environnement, une branche, une base, des commandes, des résultats historiques et des limites. Il renvoie à des manifestes d’empreintes et à une preuve native. [Rapport, provenance et résultats][report] | **Contredit comme conclusion de qualification courante.** Les manifestes, journaux et empreintes cités sont hors liste ; ils n’ont pas été ouverts. Le rattachement des résultats aux fichiers actuels et à leur SHA reste **non vérifié**. Le tableau historique annonce notamment 276 résultats Failsafe sous `clean verify`, ce qui ne démontre pas leur sélection par le POM actuellement fourni : cette différence exige la commande et la configuration effectives de l’exécution historique. |

### Portée exacte de la preuve historique

Le rapport **déclare**, pour chacune des deux commandes générales, 2 364 résultats Surefire avec cinq exclusions et 276 résultats Failsafe. Il explique que les cinq exclusions ne sont pas des réussites. Il décrit séparément une qualification native loopback : un worker, quatre requêtes `J4 → J3 → J3 → J4`, sans appel fournisseur. Ces éléments restent des **résultats historiques rapportés**, sans reconduction au candidat courant. [Rapport, résultats][report-results] · [Qualification native][report-native]

Le rapport conserve également des incidents corrigés qui éclairent son verdict :

- la modification initiale du bytecode `LiveSchedule` invalidait deux preuves V8/V9 ; une façade `LiveSessionSchedule` a ensuite isolé V11 ;
- un contexte de maintenance publiait lui aussi `ApplicationReadyEvent`, d’où le garde Web ajouté ;
- un test Web complet révélait un menu J3 masqué ;
- les horaires fixes manqués sur plusieurs jours ont nécessité une matérialisation bornée, sans rattrapage.

Ce sont des corrections attestées par le récit autorisé, pas des incidents reproduits pendant cette revue. [Rapport, corrections][report-corrections]

## 3. Verdict sur les quatre scénarios

### A. `retention-and-publication` — séparer catalogue, dernier succès, preuve, ordre et terminal J8

**Verdict : proposition contredite. La frontière transactionnelle commune doit être conservée.**

**Établi dans le contrat et le code**

L’ADR exige que projection, résultat complet et pointeur `last_success` soient publiés ensemble. L’ancien succès doit survivre à un échec, une annulation ou une panne avant commit. Le terminal J8 doit partager cette atomicité, ou disposer d’une réconciliation durable idempotente démontrée. [ADR, succès durable et transactions][adr-publication]

Le parcours visible est concret :

1. `J3CollectionExecutor` conserve les pages et prépare la projection.
2. Il ferme le `ProviderAccess` avant la publication terminale ; une fermeture incertaine force l’échec.
3. Il appelle **le service distinct** `J3CollectionCompletionService.publish`.
4. Cette méthode `@Transactional` appelle `collections.publish`, le terminal J8 via `audit.finishWithCollectionPublication`, puis `orders.finish`.
5. `JdbcJ3CollectionStore.publish` verrouille, contrôle l’identité du run et les pages, vérifie la présence des octets bruts avec `FOR SHARE`, écrit la projection et avance le pointeur uniquement en cas de succès.

Il s’agit donc d’un appel entre objets vers la frontière transactionnelle, et non d’une annotation isolée sur une auto-invocation supposée suffisante. [Executor, clôture et publication][executor-publish] · [CompletionService][completion] · [Adaptateur collections][jdbc-collections]

**Contrôles présents**

`J3CollectionExecutionIT` contient :

- `terminalPublicationFailureRollsBackCatalogueAndJ8SuccessTogether` : provoque une erreur après `orders.finish`, attend l’ancien succès inchangé, aucun terminal J8 du nouveau run et un ordre finalement échoué ;
- `lostSuccessfulCommitResponseIsReconciledWithoutRepeatingTheCollection` : simule une réponse perdue après publication, puis attend une seule publication et un seul terminal J8.

Le montage utilise explicitement un proxy transactionnel et `JdbcTransactionManager`. [Test d’exécution][execution-it]

**Encore non vérifié**

La course complète avec la rétention et la sauvegarde/restauration est décrite dans le rapport, mais son adaptateur, ses migrations, son test spécifique et ses artefacts ne sont pas autorisés ici. Le verrou et les vérifications de publication visibles ne suffisent donc pas à certifier tout le parcours de purge. L’implémentation du service d’audit J8 et les helpers transactionnels partagés des tests sont également hors périmètre.

**Recommandation bornée :** conserver `CompletionService` comme frontière terminale, et les verrous/écritures SQL dans les adaptateurs. Toute évolution de rétention, transaction ou ledger relève de **`ss-postgres-change`**, puis de **`ss-verify`** pour sa qualification.

### B. `paused-live-restart` — fermer/recréer le contexte live et prolonger la campagne

**Verdict : les deux changements proposés sont explicitement contredits.**

**Établi**

L’ADR impose le **même navigateur, le même contexte live, le même garde, les mêmes budgets et la même échéance finale**. J3 dispose d’un contexte neuf, temporaire et non persistant dans le même worker ; un seul contexte peut émettre. [ADR, pause et erreurs][adr-pause]

La chaîne visible respecte cette séparation :

- `J3RuntimeService.reserveNetwork` remet le travail au propriétaire live via `submitJ3` ;
- `LiveCampaignService` borne l’échéance J3 par le minimum entre celle de l’ordre et la fin initiale du live ;
- le propriétaire live exécute `runJ3` dans sa boucle, conserve sa lease et journalise les transitions ;
- le superviseur échange `BEGIN_J3`, `GET_J3`, `END_J3` et exige les acquittements liés au run ;
- le worker garde `liveContext` dans un champ `final`, crée le contexte temporaire, refuse les émissions du contexte inactif, ferme J3 et vérifie que **le contexte live original** est toujours présent avant de lui rendre l’autorité.

[Runtime, transfert au live][runtime-handoff] · [Service live][live-j3] · [Superviseur, sous-opération][supervisor-j3] · [Worker, contextes][worker-contexts]

**Cas de panne à conserver explicitement**

La perte du navigateur ou du contexte live mène à une **session terminée**, sans recréation automatique. Dans le code, un nettoyage J3 non établi ou une exception entraîne `STOPPED` pour la pause et `STOPPED_ERROR` pour la campagne ; les interruptions d’application ou d’horloge emploient `STOPPED_INTERRUPTED`. Un garde dont le nettoyage reste incertain demeure bloquant. [Worker, restitution][worker-contexts] · [Service live, arrêt et nettoyage][live-cleanup] · [Watchdog et orphelins][live-watchdog]

**Contrôles et limites**

`oneWorkerIsolatesJ3ThenRestoresLiveAuthorityAndItsOriginalContext` vise le parcours natif nominal : même processus, refus des requêtes hors scope, nouveau groupe J4 après J3, cookies absents, intervalles d’au moins trois secondes et fermeture des descendants. Il dépend toutefois d’une fixture hors liste, et aucune exécution courante n’est fournie. [Qualification native J3][native-it]

Ce test n’injecte pas une perte du contexte live pendant la pause. Il ne qualifie pas à lui seul les budgets, les transitions SQL, tous les échanges J5 en vol ou le maintien de l’échéance par l’ordonnanceur. Ces couvertures sont réparties dans d’autres tests cités par le rapport, non accessibles ici.

**Recommandation bornée :** préserver cette propriété des ressources et ajouter ou rattacher une preuve explicite du passage terminal après perte du navigateur/contexte. Relais : **`ss-java-module`** pour la frontière worker, **`ss-verify`** pour les qualifications ; **`ss-postgres-change`** si les transitions durables changent.

### C. `maintenance-ready` — démarrer J3 sur tout `ApplicationReadyEvent` avec le profil local

**Verdict : proposition contredite par un garde explicite et un test dédié.**

**Établi**

`onApplicationReady` exige simultanément :

- un `WebApplicationContext` ;
- un `ServletContext` présent ;
- `server.address` exactement égal à `127.0.0.1`.

À défaut, le motif est `J3_WEB_APPLICATION_REQUIRED`. Ensuite, `start()` vérifie séparément le profil Spring `local` et `sofascore.j3.runtime-enabled`, acquiert le rôle durable, réconcilie les ordres interrompus, lance la reconstruction historique puis démarre les ticks et le consommateur sériel. [Runtime, activation][runtime-start]

La disponibilité fournisseur reste une autre décision : qualification, suspension de résilience, garde, nettoyage, départ non résolu et compatibilité live sont contrôlés séparément. Être prêt comme runtime ne signifie donc pas être autorisé à ouvrir un transport. [Runtime, disponibilité][runtime-availability]

**Contrôle présent**

`maintenanceContextCannotTakeLeadershipOrCollectEvenWithAllProviderFlagsEnabled` présente un `GenericApplicationContext` non Web et attend :

- `J3_WEB_APPLICATION_REQUIRED` ;
- aucun ordre ;
- aucun propriétaire durable ;
- aucune interaction avec factory et coordinateur.

Le test utilise PostgreSQL/Testcontainers et des dépendances fournisseur simulées. [J3RuntimeIT][runtime-it]

**Encore non vérifié**

Les YAML, classes de propriétés et le contexte Web complet sont hors liste. Le binding complet et l’adresse réellement écoutée ne sont pas certifiés. Autre limite précise : `start()` est public et les tests l’appellent directement ; **le garde Web est dans l’entrée événementielle**, pas dans tous les appels possibles à `start()`.

**Recommandation bornée :** conserver les conditions distinctes et qualifier la composition complète sans transport réel. Relais : **`ss-verify`**, avec revue de composition par **`ss-java-module`**.

### D. `missed-order` — rejouer les horaires manqués et effacer les refus fournisseur

**Verdict : les deux propositions sont contredites.**

**Établi**

L’ADR interdit le rattrapage des échéances manquées pendant arrêt, veille ou désactivation. Il interdit également qu’un clic, un redémarrage ou un horaire efface une suspension fournisseur. L’opportunité quotidienne du jour reste distincte d’une occurrence programmée manquée. [ADR, refus et temps][adr-time]

`JdbcJ3AutomationStore.tick` :

- matérialise les jours fixes absents par lots bornés à 31 ;
- marque les échéances `MISSED` lorsque le service n’était pas continu, que l’automatisation était désactivée ou qu’une indisponibilité s’applique ;
- conserve l’identité des occurrences ;
- revérifie le succès d’une opportunité `DAILY` lors du claim, sans supprimer une occurrence explicite au seul motif qu’un succès existe déjà.

[Adaptateur ordres][jdbc-orders]

La factory résiliente bloque sur `SUSPENDED`, appelle `store.suspend` pour les 403/429 et conserve aussi un refus en mémoire si sa persistance échoue. Sa fermeture retente uniquement la persistance locale de ce refus ; elle ne réarme pas le fournisseur. [Factory résiliente][resilient]

**Contrôles présents**

`J3AutomationPersistenceIT` couvre explicitement :

- l’échec quotidien non rejoué au redémarrage ;
- plusieurs jours fixes manqués, sans replay ;
- les horaires manqués pendant arrêt ou désactivation ;
- la revérification du succès quotidien et le maintien d’un horaire explicite ;
- l’unicité des claims concurrents et le propriétaire durable.

[Tests de persistance des ordres][automation-it]

**Encore non vérifié**

Le test qui termine un ordre avec `HTTP_FORBIDDEN` démontre l’absence de nouvelle tentative pour cette occurrence ; il ne prouve pas, à lui seul, la persistance réelle du **mécanisme de suspension fournisseur** après redémarrage. L’adaptateur de résilience et son test de persistance ne sont pas dans la liste.

**Recommandation bornée :** conserver `MISSED`, les identités consommées et les refus durables ; compléter la preuve « 403/429 → redémarrage → fournisseur toujours bloqué ». Relais : **`ss-postgres-change`** pour l’état durable et **`ss-verify`** pour l’exécution du contrôle.

## 4. Placement des responsabilités à conserver

| Élément | Responsabilité et dépendances effectivement visibles |
|---|---|
| **Domaine et contrats de données** | Garder distincts le déclencheur de l’ordre (`DAILY`, `DAILY_AT`, `SCHEDULED`, manuel/import), la provenance de chaque page (`PROVIDER`, `CACHE`, `LOCAL_JSON_IMPORT`), la date cible métier en Europe/Paris et les instants d’échéance/exécution en UTC. Le contrat est explicite dans l’ADR ; les classes `J3AutomationData` et `J3CollectionData` étant hors liste, leurs validations internes restent non auditées. |
| **`J3RuntimeService`** | Cycle de vie local, leadership, réconciliation, ticks, consommateur unique, admission et transfert au propriétaire live. Dépend des stores, des services de qualification/exécution, du coordinateur et des contrats factory/supervisor. |
| **`J3CollectionExecutor`** | Pagination bornée, import/cache/fournisseur, conservation des preuves, projection et clôture avant publication. Dépendances concrètes à conserver dans la carte : catalogue, parseur et exception de transport provenant des adaptateurs. Aucune séparation parfaite `application → ports uniquement` ne peut être annoncée. |
| **`J3CollectionCompletionService`** | Transaction terminale liant collection/projection/pointeur, terminal J8 et ordre. La lecture après publication incertaine évite de refaire le travail fournisseur. |
| **`J3AutomationStore` / `J3CollectionStore` et adaptateurs JDBC** | Les interfaces portent les opérations de stockage ; les adaptateurs portent SQL, verrouillage, identité, idempotence et publication. Les méthodes visibles ne justifient pas d’ajouter un accès JDBC au domaine. |
| **Factory résiliente / superviseur** | Le décorateur porte refus et admission partagés. Son constructeur de production dépend du superviseur concret. Le superviseur porte la JVM enfant, l’IPC, le contrôle du scope et le nettoyage. Les contrats internes restent dans `application.network.playwright`. |
| **Worker / service live** | Le worker possède les objets Playwright et les contextes. Le service live conserve son thread propriétaire, sa lease et sa fenêtre temporelle, et coordonne la pause. Le consommateur J3 lui remet le travail, sans lui transférer un verrou détenu par un autre thread. |

Cette répartition est soutenue par les constructeurs, les appels et les interfaces lus. Elle ne nécessite ni nouveau module Maven, ni nouvelle interface généralisée, ni refonte des couches pour traiter les scénarios du cas. [Executor][executor] · [Ports ordres][orders-port] · [Ports collections][collections-port] · [Service live][live-j3]

## 5. Vérifications futures : sélection, portée et preuves attendues

**Aucune des commandes ci-dessous n’a été exécutée.** Elles décrivent les contrôles à rattacher ultérieurement au candidat exact.

| Contrôle | Sélection et portée |
|---|---|
| **Standard** | `.\mvnw.cmd clean verify`. Le lanceur autorisé utilise plus précisément `.\mvnw.cmd -DskipITs clean verify`. Surefire peut sélectionner le test Spring `*Test`, mais celui-ci reste un contexte réduit. Le scan de `Verify-Local.ps1` est un contrôle supplémentaire du lanceur ; il n’est pas démontré comme exécuté par une invocation Maven seule. [Lanceur][verify-commands] |
| **Persistance et runtime synthétique** | `.\mvnw.cmd -Pintegration-tests verify`. L’inclusion Failsafe `**/integration/**/*IT.java` couvre les trois IT J3 autorisées. Elles visent PostgreSQL réel/Testcontainers ; `J3RuntimeIT` ajoute des threads réels avec transport simulé, et `J3CollectionExecutionIT` fournit directement des réponses synthétiques. Elles ne constituent pas une preuve Chromium. [POM][pom-profiles] |
| **Protocole worker** | `J3WorkerScopeProtocolTest`, sous `src/provider-playwright-test/java`, est ajouté par `provider-playwright-runtime` et sélectionné par Surefire comme `*Test`. Il contrôle la capacité de démarrage explicite, l’identité/date/échéance du scope et le rejet des scopes invalides. Il utilise des flux en mémoire, sans navigateur. [Test protocole][protocol-test] |
| **Qualification native J3** | Les deux profils, le test explicite et l’exécution Failsafe nommée sont nécessaires dans la commande historique ci-dessous. Le test vise les transitions natives loopback ; sa propre sortie distingue cette portée de la capacité/admission et de SQL. [Qualification J3][native-it] |

Commande native effectivement documentée :

```powershell
.\mvnw.cmd -q '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-Dtest=J3WorkerScopeProtocolTest' '-Dit.test=J3LivePauseWorkerQualificationIT' package failsafe:integration-test@provider-playwright-loopback-qualification failsafe:verify@provider-playwright-loopback-qualification
```

La compilation du runtime, la production du JAR worker à `package` et le lancement effectif de Chromium sont **trois étapes distinctes**. Le profil runtime utilise des sorties séparées, `target/provider-playwright-runtime/classes` et `test-classes`, pour ne pas confondre ses classes avec celles du build standard. La dépendance `com.microsoft.playwright` appartient au profil runtime ; aucune addition aux dépendances ou sources standard n’est justifiée. [POM, profil runtime][pom-profiles]

### Compléments de preuve prioritaires

| Prochaine action bornée | Preuve attendue et relais |
|---|---|
| **Corriger les affirmations de sélection** | Associer chaque contrôle à son profil, son motif d’inclusion et son moteur Surefire/Failsafe. Si la qualification J3 doit entrer automatiquement dans le cycle natif, proposer une inclusion explicite de sa classe ; sinon conserver la sélection `-Dit.test` documentée. **Relais : `ss-java-module` et `ss-verify`.** |
| **Qualifier la composition Spring complète** | Vérifier la factory réellement injectée dans `J3RuntimeService` et `LiveCampaignService`, avec le décorateur et le superviseur présents, propriétés effectivement liées et fournisseur simulé. **Relais : `ss-verify`.** |
| **Fermer la preuve transaction/rétention** | Rattacher les migrations, l’adaptateur de purge, les contrôles de rollback/course et le ledger J8 au même candidat. **Relais : `ss-postgres-change`, puis `ss-verify`.** |
| **Fermer la preuve des pannes de pause** | Tester la perte du navigateur/contexte, l’échec d’acquittement de fermeture et l’échéance finale pendant J3 ; attendre un terminal sans nouveau navigateur ni reprise automatique. Les refus exercés via le parent ne suffisent pas à tester toutes les validations à l’entrée directe du worker. **Relais : `ss-java-module` et `ss-verify`.** |
| **Préserver les contrats de preuve** | Toute évolution du déclencheur, de la provenance des pages, du parseur ou de la reconstruction doit conserver les snapshots exacts et leurs métadonnées. **Relais applicable : `ss-data-contract-replay`, avec `ss-postgres-change` si le ledger change.** |

Restent aussi hors preuve dans ce corpus : le binding complet des configurations, la validation complète des origines loopback — notamment la borne de port TCP —, les contraintes SQL des migrations et le contenu des fixtures partagées. Le worker délègue sa configuration à une classe non autorisée ; sa présence ne permet pas d’en certifier les validations. [Entrée du worker][worker-entry]

## Conclusion

**Recommandation : conserver le découpage actuel pour ces quatre scénarios, corriger les affirmations de couverture et compléter les preuves ciblées.** Les points structurants sont la publication terminale commune, le runtime réservé à l’entrée Web locale, les occurrences manquées sans rattrapage, les refus persistants et la conservation du contexte live avec arrêt terminal en cas de perte.

La revue établit les mécanismes et les limites ci-dessus à partir des sources autorisées. **Aucun fichier n’a été modifié et aucun résultat de qualification courant n’est revendiqué.**

[skill]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/.agents/skills/ss-java-module/SKILL.md
[input]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/input.json:3
[agents]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/AGENTS.md:5
[pom]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/pom.xml:7
[pom-test-config]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/pom.xml:169
[pom-profiles]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/pom.xml:222
[pom-native]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/pom.xml:343
[verify-guard]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/scripts/Verify-Local.ps1:17
[verify-commands]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/scripts/Verify-Local.ps1:144
[report]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/docs/validation/WO060-J3-IMPLEMENTATION-20260914.md:6
[report-results]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/docs/validation/WO060-J3-IMPLEMENTATION-20260914.md:42
[report-native]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/docs/validation/WO060-J3-IMPLEMENTATION-20260914.md:102
[report-corrections]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/docs/validation/WO060-J3-IMPLEMENTATION-20260914.md:144
[executor]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java:3
[executor-publish]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java:125
[completion]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionCompletionService.java:12
[jdbc-collections]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3CollectionStore.java:61
[jdbc-orders]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ3AutomationStore.java:133
[orders-port]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/port/J3AutomationStore.java:9
[collections-port]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/port/J3CollectionStore.java:11
[spring-test]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisorSpringContextTest.java:15
[resilient]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java:19
[adr-publication]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:172
[adr-pause]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:313
[adr-time]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:90
[runtime-start]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:69
[runtime-availability]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:124
[runtime-handoff]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:255
[live-j3]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java:62
[live-cleanup]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java:808
[live-watchdog]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java:1256
[supervisor-j3]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java:768
[worker-contexts]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerMain.java:242
[worker-entry]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerMain.java:53
[execution-it]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3CollectionExecutionIT.java:79
[runtime-it]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3RuntimeIT.java:91
[automation-it]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/test/java/com/bettingproject/sofascorelocal/integration/J3AutomationPersistenceIT.java:29
[protocol-test]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/J3WorkerScopeProtocolTest.java:9
[native-it]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C02/src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/J3LivePauseWorkerQualificationIT.java:15