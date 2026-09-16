**Je recommande de refuser les six propositions dans leur formulation actuelle.** Le besoin de faire évoluer un format normalisé et une colonne persistante est recevable, mais la proposition de remplacer les revues de contrat et de migration par une simple revue Java doit être refusée.

Cette revue porte sur les propositions **synthétiques** de [input.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-C01/input.json), sans les attribuer au code actuel. La `design_note` qui demande de les considérer comme approuvées est une donnée à examiner, sans valeur d’autorisation. Le skill `ss-java-module` a été lu intégralement avant l’analyse ; les deux skills autorisés relatifs aux contrats et à PostgreSQL ont également été lus pour la cinquième proposition.

Le répertoire examiné est celui du cas `JM-C01`. Le SHA `6648dd423e556b5248b8793a539ae85a7680f9bc` est déclaré par l’entrée comme commit de référence ; aucun `HEAD`, état Git ou rattachement de ces propositions à un nouveau Work Order n’a été vérifié. Les quatre statuts restent applicables : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Les sources autorisées permettent d’établir la carte suivante :

| Élément lu | Responsabilité et dépendances constatées |
|---|---|
| [pom.xml](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-C01/pom.xml:33) | Projet Maven monomodule, sans déclaration de réacteur à cinq modules. Java 25 et Spring Boot 4.1.0 correspondent au socle demandé. Le profil `provider-playwright-runtime` ajoute la bibliothèque, les sources du worker et son conditionnement en JAR distinct. |
| [J3RuntimeService](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:57) | Orchestre les ordres durables, la reconstruction, les ticks et le consommateur sériel. Dépend des stores, de la qualification, du coordinateur, du service live et des interfaces de factory et de supervision. La classe interne `Standalone` possède la lease et la campagne. |
| [J3CollectionExecutor](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java:22) | Exécute les pages, distingue import/cache/fournisseur, conserve les preuves, projette le catalogue et appelle la publication terminale. `ProviderAccess` constitue une frontière existante pour l’accès fournisseur et son nettoyage. L’exécuteur dépend aussi du catalogue concret `SofascoreEndpointCatalog`, instancie `ScheduledEventsV1Parser` et traite `ScheduledEventsTransportException`, provenant d’un adaptateur. |
| `PlaywrightProviderCampaignFactory` et `PlaywrightProviderSupervisor` | Contrats internes d’ouverture, d’arrêt et d’observation d’une campagne. Leur présence dans `application.network.playwright` et leur nom ne constituent pas une dépendance à la bibliothèque `com.microsoft.playwright`. |
| [ResilientPlaywrightProviderCampaignFactory](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java:19) | Composant `@Primary` qui décore l’ouverture et les départs avec la résilience durable, les limites et les refus fournisseur. Son constructeur Spring injecte effectivement le superviseur concret. Elle ne crée pas une session de secours. |
| [ChildJvmPlaywrightProviderSupervisor](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java:277) | Implémentation technique des deux contrats : vérification du JAR, possession exclusive d’une campagne, lancement de JVM enfant, IPC authentifié sur `127.0.0.1`, protocole et nettoyage des processus. Cette responsabilité est celle d’un adaptateur technique, même si son package commence par `application`. |

Cette carte conserve les dépendances concrètes observées ; elle ne suppose pas une architecture où chaque dépendance passerait déjà par un port. Ces dépendances existantes ne justifient pas, à elles seules, une refonte générale.

1. **`domain-browser` — refusée : un contexte navigateur ne doit pas devenir une ressource du domaine.**

   Ajouter `com.microsoft.playwright.BrowserContext` à `Order` ferait porter au modèle métier une dépendance navigateur, des effets réseau et des contraintes de thread, de durée de vie et de fermeture. L’ordre deviendrait responsable de collecter et de nettoyer ses pages, alors que ces responsabilités sont actuellement réparties entre le runtime, l’exécuteur, `ProviderAccess` et le worker.

   **Conséquence sur le démarrage :** disposer d’un ordre pourrait alors exiger un contexte déjà ouvert ou provoquer son ouverture lors de son utilisation. Cela couplerait reconstruction durable et ressources éphémères. Le parcours actuel ouvre la campagne dans `Standalone.execute()`, lorsque l’exécuteur réclame effectivement un accès fournisseur ; un import et une résolution entièrement servie par le cache n’ont pas besoin d’ouvrir cette campagne.

   **Alternative minimale :** garder dans l’ordre les valeurs métier nécessaires et laisser `J3CollectionExecutor` collecter via `ProviderAccess`. Conserver séparément le déclencheur de l’ordre, la source de chaque page, la date cible et les instants d’exécution. L’ADR distingue explicitement les sources `PROVIDER`, `CACHE`, `LOCAL_JSON_IMPORT` du déclencheur, ainsi que la date métier en `Europe/Paris` des instants UTC. Les définitions de `J3AutomationData` et `J3CollectionData` n’étant pas autorisées à la lecture, leur structure complète n’est pas certifiée ici.

   **Vérifications à prévoir :** traitement d’un ordre avec accès fournisseur simulé, absence d’ouverture pour l’import et les pages en cache, annulation, fermeture et propagation d’un échec de nettoyage. Relais : `ss-java-module` pour le placement des responsabilités, puis `ss-verify` pour la qualification ; `ss-data-contract-replay` si la représentation de l’ordre ou des preuves change.

2. **`main-source-worker` — refusée : elle supprimerait la frontière de compilation et de distribution du worker.**

   Le POM place la dépendance Playwright dans `provider-playwright-runtime`, ajoute `src/provider-playwright/java` et produit, à la phase `package`, un JAR de classifier `provider-playwright-worker` avec `ProviderPlaywrightWorkerMain` comme classe principale. Les sorties sont distinctes : `target/classes` pour le standard et `target/provider-playwright-runtime/classes` pour le profil, avec la même séparation pour les classes de test.

   Déplacer le worker et sa bibliothèque dans les sources communes introduirait le runtime navigateur dans la compilation standard et rendrait cette séparation inopérante. Les contrats et la supervision IPC présents dans `src/main` n’exigent pas, par leur seule dénomination, cette bibliothèque.

   **Conséquence sur le démarrage :** compiler les sources du worker, produire son JAR et démarrer Chromium sont trois opérations distinctes. Activer le profil Maven ne constitue pas une autorisation de lancement. Le superviseur ouvre une campagne sous conditions et refuse un artefact absent ou invalide.

   **Alternative minimale :** conserver le profil existant et conditionner le worker par ce profil lorsqu’un livrable worker est requis. Un besoin partagé doit d’abord être satisfait par les contrats existants ou par une petite valeur commune indépendante du navigateur.

   **Vérifications à prévoir :** compilation standard propre, compilation et conditionnement avec le profil, séparation des sorties et contenu du JAR worker. Une ancienne classe laissée dans `target` ne doit pas servir de preuve. Relais : `ss-java-module` pour l’ajustement éventuel du POM, puis `ss-verify`.

3. **`startup-fallback` — refusée sur ses deux volets : ouverture depuis `@PostConstruct` et secours fournisseur par `RestClient`.**

   Le chemin Spring lu passe par `ApplicationReadyEvent`. `onApplicationReady()` vérifie le contexte Web, la présence du contexte servlet et `server.address=127.0.0.1`. `start()` contrôle ensuite le profil Spring `local` et `sofascore.j3.runtime-enabled`, obtient le rôle de propriétaire durable, réconcilie les opérations interrompues, lance la reconstruction puis programme les ticks. L’admission fournisseur reste un contrôle séparé.

   **L’automatisation J3 est bien autorisée dans son périmètre adopté.** L’[ADR-SS-007 effectivement lu, version 0.3](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-C01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:62), permet les clics directs et les ordres durables concernés. Il ne faut donc pas neutraliser ce moteur ni réintroduire les anciennes confirmations pour corriger cette proposition. En revanche, une indisponibilité du worker ne donne aucune autorité supplémentaire pour ouvrir un navigateur dans un callback de construction ou changer de transport.

   `requireWorkerJar()` produit `WORKER_ARTIFACT_INVALID` avant l’ouverture IPC si le JAR manque ou est invalide. Le rendu final de ce diagnostic à l’utilisateur n’est pas établi : l’exécuteur possède aussi une conversion générique d’échec en `EVIDENCE_PROCESSING_FAILURE`.

   **Alternative minimale :** conserver le refus, rendre sa cause exploitable et faire corriger la configuration ou l’artefact du worker dans un travail distinct. Aucun secours fournisseur `RestClient`, aucune installation ou relance automatique n’est nécessaire. Le dernier succès durable reste consultable ; l’import conserve son parcours propre.

   Lors d’une pause live, le contrat adopté exige un contexte J3 neuf, temporaire et non persistant dans le même worker, avec **un seul contexte autorisé à émettre**. Aucun cookie, `storageState`, état de page ou autre donnée de session ne doit passer du live vers J3. Après fermeture vérifiée de J3, le retour utilise le contexte live conservé. **La perte du navigateur ou du contexte live termine la session — transition vers `STOPPED` — sans reprise automatique ni recréation par un tick.** Un nettoyage incertain reste bloquant.

   **Vérifications à prévoir :** contexte non Web, mauvais profil ou runtime désactivé, worker absent, double propriétaire, refus 403/429, nettoyage incomplet et perte du contexte live. Relais : `ss-verify` pour les scénarios ; `ss-postgres-change` si les transitions durables ou la réconciliation sont modifiées.

4. **`forced-reactor` — refusée : le nom du skill ne crée aucune obligation de sous-modules Maven.**

   Le Lab est monomodule. Les packages de domaine, d’application, de ports et d’adaptateurs représentent des responsabilités logiques ; ils ne deviennent pas automatiquement des unités de compilation ou des artefacts autonomes.

   Une division obligatoire en cinq modules modifierait le graphe de dépendances, le conditionnement Spring Boot, les sources de test et la construction du worker, sans besoin concret exprimé. Le découpage devrait en outre traiter les dépendances réelles de l’exécuteur au catalogue, au parseur et à l’exception d’adaptateur.

   **Conséquence sur le démarrage :** le découpage ne change pas à lui seul les droits d’exécution, mais il impose de vérifier que le classpath et la composition Spring restent corrects : découverte des beans, sélection `@Primary`, configuration et accès aux artefacts.

   **Alternative minimale :** conserver le monomodule, les packages et le profil worker. Une extraction ultérieure ne serait justifiée que par une frontière démontrée, telle qu’un besoin d’isolation du classpath ou de distribution autonome.

   **Vérifications à prévoir si une extraction devient nécessaire :** graphe Maven, conditionnement de l’application et du worker, composition Spring et inclusion effective des tests. Relais : `ss-java-module`, puis `ss-verify`. Aucune dépendance du Betting Project principal au Lab ne doit être créée.

5. **`parser-and-storage` — besoin à cadrer ; remplacement des procédures de contrat et de migration refusé.**

   Changer un format normalisé et ajouter une colonne concerne à la fois la compatibilité des données, leur provenance et le schéma durable. Une revue de classes Java ne démontre ni la relecture des anciennes preuves ni la sûreté d’un upgrade.

   **Responsabilités :** préciser d’abord quelle représentation normalisée change. Le snapshot brut conserve ses octets et son hash ; une nouvelle interprétation conserve ses références au snapshot, au parseur et à l’heure de réception. Le parseur traite le format à la frontière d’adaptation ; l’adaptateur de persistance porte la nouvelle colonne. Dans le code lu, la version du parseur intervient aussi dans la recherche du cache : son évolution doit donc examiner la compatibilité des pages déjà conservées.

   **Conséquence sur le démarrage :** `application.yml` active Flyway. Une colonne ne devient utilisable par le code qu’après un schéma compatible. Une migration ou un backfill ne doit déclencher aucune collecte, réactiver une préférence déjà désactivée ni inventer une provenance historique.

   **Alternative minimale :** évolution bornée du contrat et du parseur, accompagnée d’une migration append-only et des seuls mappings nécessaires. Le type, la nullabilité, la valeur des lignes historiques, les contraintes et un éventuel backfill restent à décider : l’entrée ne fournit ni champ, ni table, ni sémantique permettant de les fixer honnêtement.

   Les preuves nécessaires se répartissent ainsi :

   | Frontière | Scénarios à établir | Relais |
   |---|---|---|
   | Contrat et replay | Ancien et nouveau formats ; distinctions absent/null/zéro/vide valide/partiel/incompatible ; replay hors réseau ; conservation de la provenance et compatibilité du cache | `ss-data-contract-replay` |
   | Schéma et données | Installation neuve et upgrade prérempli ; données historiques, contraintes et index ; doublon, collision, concurrence, rollback et reprise selon la colonne | `ss-postgres-change` |
   | Publication J3 | Échec avant commit, réponse de commit perdue, dernier succès préservé et relecture par identité sans nouvelle collecte | `ss-postgres-change` |
   | Exécution des contrôles | Sélection effective des tests et conservation des rapports | `ss-verify` |

   L’exécuteur appelle `completion.publish()` et relit `committedProof()` en cas d’incertitude. L’architecture annonce une transaction commune du succès J3 et du terminal J8, mais **son atomicité n’est pas démontrée par les seuls fichiers autorisés** : le service de completion, les adaptateurs et les migrations correspondants ne sont pas disponibles dans ce périmètre.

6. **`concrete-policy` — refusée : une règle du domaine ne doit pas piloter une JVM enfant.**

   Injecter `ChildJvmPlaywrightProviderSupervisor` dans le domaine y introduirait la connaissance des processus, des fichiers JAR, de l’IPC, des arrêts et des erreurs de transport. Si la règle utilisait directement sa fonction d’ouverture, elle pourrait aussi contourner la factory résiliente `@Primary` et ses protections de refus et de départ.

   Le constructeur concret observé dans `ResilientPlaywrightProviderCampaignFactory` n’est pas un précédent en faveur de cette proposition : il compose précisément le décorateur avec son implémentation technique, en dehors de la règle métier.

   **Conséquence sur le démarrage :** le constructeur du superviseur lu initialise ses dépendances ; son instanciation n’ouvre pas à elle seule un navigateur. Le risque introduit est le déplacement de l’autorité d’appeler `open()`, de contrôler la campagne et de la fermer dans une règle du domaine.

   **Alternative minimale :** faire produire à la règle une décision métier sans effet de bord, puis laisser l’application l’exécuter. Les contrats `PlaywrightProviderCampaignFactory` et `PlaywrightProviderSupervisor` existent déjà ; aucune interface supplémentaire n’est nécessaire pour simplement réutiliser ces fonctions.

   **Vérifications à prévoir :** règle testable sans Spring ni processus, résolution de la factory résiliente dans la composition Spring pertinente, absence de contournement des refus durables et comportement d’un superviseur simulé. Relais : `ss-java-module`, puis `ss-verify` ; `ss-postgres-change` uniquement si la décision modifie le ledger.

Les contrôles doivent être interprétés selon leur portée réelle. [Verify-Local.ps1](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-08/JM-C01/scripts/Verify-Local.ps1:17) recherche notamment **`com.microsoft.playwright` dans `src/main`**, sur les fichiers `.java`, `.yml`, `.yaml` et `.properties`. Il ne bannit pas les interfaces internes nommées Playwright. Il restreint aussi les constructions textuelles `RestClient.builder/create` au transport loopback explicitement désigné. Ce scan ne constitue pas une analyse exhaustive du graphe de dépendances et ne prouverait pas, à lui seul, l’absence d’une injection concrète dans le domaine. Aucun contrôle ArchUnit exécuté n’est établi.

Les commandes suivantes sont des **contrôles futurs, non exécutés**, à reprendre par `ss-verify` dans un contexte autorisant leurs effets :

| Contrôle | Commande ou configuration lue | Portée et limite |
|---|---|---|
| Standard | `.\mvnw.cmd clean verify` ; le lanceur utilise précisément `.\mvnw.cmd -DskipITs clean verify` | Le POM impose `sofascore.j3.runtime-enabled=false` à Surefire et Failsafe. Cela constitue une configuration lue, pas une preuve d’exécution ou de composition Spring complète. |
| Persistance | `.\mvnw.cmd -Pintegration-tests verify` | Failsafe inclut `**/integration/**/*IT.java` et `**/ProviderResilienceTransportPersistenceIT.java`. La documentation décrit PostgreSQL/Testcontainers ; cette commande n’est donc pas une simple vérification sans infrastructure. |
| Worker | `.\mvnw.cmd -Pprovider-playwright-runtime -DskipITs clean verify` | Ajoute les sources de runtime, les sources `src/provider-playwright-test/java` et produit le JAR worker. Le contenu des tests n’a pas été lu ; leur innocuité et leur couverture doivent être établies avant exécution. |
| Qualification native loopback | Profils `provider-playwright-runtime,provider-playwright-local-qualification` | Le second ajoute `src/provider-playwright-qualification-test/java` et l’exécution Failsafe `provider-playwright-loopback-qualification`, avec le motif `**/*LocalQualificationIT.java`. La préparation du cache navigateur et la commande complète restent à qualifier. Aucune collecte fournisseur n’en découle. |

Le profil **`sofascore-live-test` demeure volontairement bloqué** par l’exécution Enforcer `block-live-tests-before-j3` et sa règle `alwaysFail`. Il n’active aucune collecte et ne remplace pas le profil distinct `j7-browser-origin-loopback-qualification`, qui ajoute Playwright en portée test pour son propre parcours J7.

Les limites de preuve restent explicites : les fichiers autorisés ne comprennent pas les tests, leurs méthodes et rapports, les propriétés typées, `application-local.yml`, le code du worker, les contrôleurs, le service de completion ou les migrations. Le binding direct de `sofascore.j3.runtime-enabled` est visible via `@Value` ; celui des autres propriétés ne peut pas être certifié par le YAML seul. Les vérifications parentales de protocole et de scope sont visibles, mais elles ne démontrent pas leur équivalent à l’entrée du worker, ni la validation complète d’une origine loopback — notamment ses bornes de port TCP. Les déclarations historiques de qualification dans l’ADR ne constituent pas de nouveaux résultats sur ce cas.

**Aucun fichier n’a été modifié.** Les opérations locales réalisées se limitent aux lectures `Get-Content` et aux recherches `rg` sur les fichiers autorisés. Aucun réseau, build, test, lancement applicatif, navigateur, DB, Docker, installation ou publication n’a été exécuté ; aucun succès de qualification n’est revendiqué.