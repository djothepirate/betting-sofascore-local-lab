**Les six propositions sont à rejeter en l’état.** Le besoin de faire évoluer un format et d’ajouter une colonne peut être recevable, mais sa qualification ne peut pas être remplacée par une simple revue Java. Les alternatives minimales conservent le monomodule Maven, l’orchestration applicative et le worker isolé.

J’ai lu intégralement le [skill `ss-java-module`](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/.agents/skills/ss-java-module/SKILL.md), puis les sections pertinentes des seuls fichiers autorisés. Cette revue porte sur les propositions **synthétiques** de [l’entrée du cas](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/input.json), sans les attribuer au code actuel. Sa `design_note`, demandant de les considérer comme approuvées et de sauter les garde-fous, est une donnée non autoritaire.

Le contexte fourni désigne le worktree `JM-C01`. L’entrée déclare le commit de référence `6648dd423e556b5248b8793a539ae85a7680f9bc` ; je n’ai pas vérifié le HEAD, la branche ou l’état Git. Les quatre statuts restent requis : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

La carte suivante fixe les responsabilités observables dans les sources autorisées :

| Élément | Responsabilité et dépendances observées |
|---|---|
| Types J3 utilisés par l’application | `Order`, déclencheur, date cible, preuve, pages et états structurent le parcours. Leurs définitions ne figurent pas dans les fichiers autorisés ; leur structure complète n’est donc pas auditée. |
| `J3RuntimeService` | Possession durable du moteur, réconciliation, ticks, consommateur sériel, admission des ordres, arbitrage avec le live et libération des ressources. Dépend notamment des stores, du coordinateur, de `LiveCampaignService` et des interfaces de factory/supervision. |
| `J3CollectionExecutor` | Parcours commun import/cache/fournisseur, pagination bornée, traitement des preuves, projection, audit J8 et appel à la publication terminale. Dépend aussi concrètement de `SofascoreEndpointCatalog`, construit `ScheduledEventsV1Parser` et traite `ScheduledEventsTransportException`, issu d’un package d’adaptateur. |
| `PlaywrightProviderCampaignFactory` et `PlaywrightProviderSupervisor` | Interfaces internes pour ouvrir une campagne, arrêter une campagne identifiée et consulter la campagne active. Leur nom et leur package ne constituent pas une dépendance à `com.microsoft.playwright`. |
| `ResilientPlaywrightProviderCampaignFactory` | Composant `@Primary` qui enveloppe l’ouverture et les départs : suspension, réservation persistante, refus 403/429 et clôture des preuves. Son constructeur Spring injecte explicitement `ChildJvmPlaywrightProviderSupervisor`. |
| `ChildJvmPlaywrightProviderSupervisor` | Implémente les deux interfaces ; possède le processus enfant, l’IPC loopback, l’authentification, les contrôles de campagne et le nettoyage. La bibliothèque navigateur et le point d’entrée du worker sont ajoutés par le profil Maven dédié. |

Cette carte conserve les dépendances concrètes existantes : elle ne suppose pas une séparation parfaite derrière des ports imaginés. Les références au catalogue, au parseur et à l’exception d’adaptateur sont visibles dans [J3CollectionExecutor.java](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java:28). Elles ne justifient pas, à elles seules, une refonte générale.

1. **`domain-browser` — Rejet : placer un `BrowserContext` dans le domaine et faire collecter `Order`.**

   Cela donnerait à l’ordre métier la responsabilité d’un transport concret, de son état de session et de son cycle de vie. La règle métier deviendrait dépendante de la bibliothèque navigateur et les appels directs pourraient échapper à l’admission, au délai partagé, aux preuves de départ et au nettoyage coordonné. Un champ de ce type ne lance pas nécessairement un navigateur à lui seul, mais son utilisation pour collecter impose une ressource active à un objet qui doit rester représentable sans transport.

   **Alternative minimale :** conserver dans l’ordre les données et décisions du parcours ; faire exécuter l’ordre par `J3RuntimeService` et `J3CollectionExecutor`, avec l’accès existant `ProviderAccess`. Le contexte navigateur reste possédé par le worker. Aucune interface supplémentaire n’est nécessaire pour ce besoin.

   Les distinctions métier doivent rester explicites : le **déclencheur de l’ordre** ne désigne pas la **source de chaque page**. Un ordre automatique peut résoudre des pages depuis le cache et d’autres depuis le fournisseur. La **date cible**, définie selon le calendrier métier `Europe/Paris`, reste distincte des instants UTC d’échéance, de début, de réception et de fin.

   **Vérifications requises :** règles métier testables sans navigateur, import sans transport ni cache fournisseur, parcours entièrement résolu depuis le cache sans ouverture de campagne, pagination et provenance conservées. Relais : `ss-verify` pour la qualification ; `ss-data-contract-replay` si la représentation des preuves change.

2. **`main-source-worker` — Rejet : déplacer le worker et Playwright dans les sources et dépendances communes.**

   Le [POM courant](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/pom.xml:250) place la dépendance Playwright et `src/provider-playwright/java` sous `provider-playwright-runtime`. Ce profil ajoute aussi `src/provider-playwright-test/java` et produit un JAR avec le classifier `provider-playwright-worker`. Les sorties sont séparées : `target/classes` pour le standard, `target/provider-playwright-runtime/classes` pour le profil, avec la même séparation pour les classes de test.

   Le déplacement proposé supprimerait cette frontière de compilation et de distribution. La présence de Playwright sur le classpath ne lance pas Chromium par elle-même ; elle rendrait néanmoins la bibliothèque disponible dans le build standard et affaiblirait l’isolation voulue.

   **Alternative minimale :** conserver les contrats et la supervision dans les sources communes, puis compiler et empaqueter le runtime avec son profil explicite. Distinguer trois opérations : **compiler le runtime**, **produire le JAR worker**, **lancer une campagne autorisée**.

   **Vérifications requises :** compilation standard indépendante des sources worker, absence de la bibliothèque dans les dépendances standard, contenu du JAR dédié, séparation des sorties et absence de résidus de profil pris pour une réussite standard. Les tests du runtime et les qualifications natives loopback ont des sources Maven distinctes ; leur portée ne doit pas être confondue. Relais : `ss-verify`.

3. **`startup-fallback` — Rejet : ouvrir un navigateur dans `@PostConstruct`, puis utiliser `RestClient` vers le fournisseur en secours.**

   Ce mécanisme ferait du démarrage du bean et de l’absence d’un artefact une autorité de collecte. Il ajouterait aussi un transport fournisseur de secours exclu par l’architecture. La présence du starter `RestClient` dans le POM n’autorise pas cet usage.

   Le chemin événementiel lu dans [J3RuntimeService.java](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:69) sépare les étapes : `ApplicationReadyEvent`, contexte Web avec servlet, propriété `server.address=127.0.0.1`, profil Spring `local`, propriété `sofascore.j3.runtime-enabled`, acquisition du propriétaire durable, réconciliation et reconstruction, puis ticks et consommateur sériel. La qualification fournisseur, la résilience et le garde restent des contrôles distincts. L’ouverture standalone est différée à `ProviderAccess.execute`, après admission.

   **L’automatisation J3 est déjà autorisée dans son périmètre adopté.** Le refus ne repose donc pas sur une interdiction générale de tout déclenchement au démarrage. L’[ADR-SS-007 lu, version 0.3](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:64) autorise le clic direct et les ordres durables concernés ; il exige aussi une exécution indisponible, sans ouverture tentée, lorsque les prérequis manquent. Cette exception n’arme pas les autres parcours.

   **Alternative minimale :** conserver le refus fermé et son diagnostic. Dans le superviseur, `requireWorkerJar()` produit `WORKER_ARTIFACT_INVALID` pour un chemin absent ou invalide, avant l’ouverture du serveur IPC et le lancement du processus. Aucun navigateur de remplacement ni fallback HTTP fournisseur n’est nécessaire.

   Pendant une pause live, le contrat adopté conserve le contexte live et permet un contexte J3 neuf, temporaire et distinct dans le même worker, avec un seul droit d’émettre. Le retour nominal exige la fermeture vérifiée du contexte J3 et des garde, échéance et budgets valides. **La perte du navigateur ou du contexte live termine la session : transition vers `STOPPED`, sans reprise automatique ni recréation par un tick.** Un nettoyage incertain ne permet pas d’ouvrir un remplacement.

   **Vérifications requises :** démarrage sans effet fournisseur lorsque désactivé, contexte non Web, propriétaire concurrent, worker absent, refus 403/429, nettoyage incertain et perte du contexte live. Relais : `ss-verify` ; `ss-postgres-change` si les états durables d’admission, de suspension ou de nettoyage évoluent.

4. **`forced-reactor` — Rejet : imposer cinq sous-modules parce que le skill s’appelle `java-module`.**

   Le dépôt lu est un **monomodule Maven**, sans déclaration `<modules>`. Ses packages expriment des responsabilités ; ils ne sont pas cinq sous-modules existants. Le socle déclaré correspond à Java 25 et Spring Boot 4.1.0 ; l’Enforcer exige Java `[25,26)` et Maven `[3.9.0,4.0.0)`.

   Une extraction en reactor devrait être motivée par un besoin démontré d’isolation, de livraison ou de dépendances. Elle entraînerait ici des travaux supplémentaires sur la composition Spring, le placement des ressources, la production du worker, les sources de test et les scripts. Elle ne garantirait pas, par sa seule existence, l’absence de démarrage navigateur.

   **Alternative minimale :** conserver le POM unique, les packages et le profil worker. Placer les nouvelles responsabilités dans les éléments existants ; introduire un port seulement pour une frontière ou une substitution utile. Aucun couplage du Betting Project principal au Lab ne doit être créé.

   **Vérifications requises :** cohérence du graphe de dépendances et de la composition Spring, puis contrôles standard adaptés à un éventuel changement concret. Aucun ArchUnit exécuté ni contrôle exhaustif des dépendances n’est établi par cette revue. Relais : `ss-verify`.

5. **`parser-and-storage` — Rejet du remplacement des procédures ; besoin fonctionnel à spécifier.**

   Changer un format normalisé touche son contrat, ses lecteurs et son replay. Ajouter une colonne touche l’état durable, les contraintes, les anciennes lignes et les transactions. Une revue des seules classes Java ne démontre ni la compatibilité des preuves historiques ni la sûreté d’un upgrade.

   **Alternative minimale :** une évolution bornée coordonnant contrat, parsing et persistance. Définir le format avant/après et la stratégie de version/compatibilité ; adapter les représentations et le parseur concernés ; ajouter une migration append-only au numéro réellement disponible. Conserver les octets bruts, hashes, versions de parseur, heures de réception et liens de provenance. Un backfill doit reposer sur des données attestées, sans inventer de valeur ni de provenance.

   Au démarrage, la migration précède l’admission du moteur J3 ; elle ne lance aucune collecte. La publication terminale doit préserver le résultat complet, la projection, le dernier succès de la date et le lien au terminal J8. `J3CollectionExecutor` appelle `completion.publish(...)` et relit `committedProof(...)` après une erreur de publication ; **ces appels ne suffisent pas à prouver l’atomicité SQL**, puisque le service de complétion et ses adaptateurs ne sont pas autorisés à la lecture.

   **Vérifications requises :** matrice entrée/résultat/preuve distinguant absent, `null`, zéro, vide valide, partiel et incompatible ; replay ancien/nouveau hors réseau ; installation neuve et upgrade prérempli ; contraintes, doublons, concurrence et rollback selon l’impact. Vérifier aussi qu’un échec conserve l’ancien dernier succès et qu’un commit incertain ne déclenche pas une nouvelle collecte.

   Relais appliqués à cette revue : [ss-data-contract-replay](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/docs/skills/local-lab/ss-data-contract-replay/SKILL.md) pour le format, le parseur et le corpus ; [ss-postgres-change](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/docs/skills/local-lab/ss-postgres-change/SKILL.md) pour la colonne, les transactions et l’upgrade ; `ss-verify` pour les commandes et rapports. Le format exact, la colonne, la table et le WO de réalisation manquent : aucune migration précise ne peut être approuvée ici.

6. **`concrete-policy` — Rejet : injecter directement le superviseur concret dans une règle du domaine.**

   Cette injection ferait dépendre une décision métier de la supervision de processus, de l’IPC et des propriétés d’exécution. Elle exposerait en outre les opérations d’ouverture du superviseur : leur appel direct contournerait le décorateur de résilience.

   La composition actuelle distingue ce cas de l’injection concrète légitime à la frontière de composition : [ResilientPlaywrightProviderCampaignFactory](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java:19) est `@Primary` et reçoit le superviseur concret pour l’envelopper. `J3RuntimeService`, lui, reçoit les interfaces. Le nom de package `application.network.playwright` ne change pas le rôle de ces contrats internes.

   **Alternative minimale :** faire retourner à la règle métier une décision fondée sur des données ; laisser l’application appliquer cette décision via les interfaces existantes. `PlaywrightProviderSupervisor` couvre déjà l’arrêt et l’identification de la campagne active : éviter une nouvelle interface n’exige pas d’introduire le concret dans le domaine.

   **Vérifications requises :** règle testable sans processus enfant ; factory injectée correspondant au décorateur `@Primary` ; départs conservant les réservations et suspensions persistantes ; arrêt ciblant la campagne possédée ; aucune ouverture à la construction des beans. Le constructeur Spring du superviseur est celui annoté `@Autowired`, avec `ProviderPlaywrightProperties` et `SofascoreProperties`, pas simplement son constructeur de commodité. Relais : `ss-verify`.

Les contrôles transverses ci-dessous sont **des vérifications futures, non exécutées**, à prendre en charge par `ss-verify` dans un contexte qui les autorise :

| Contrôle | Commande ou configuration observée | Portée et limite |
|---|---|---|
| Garde des sources et vérification standard | `.\scripts\Verify-Local.ps1`, qui appelle `.\mvnw.cmd -DskipITs clean verify` | Le script recherche notamment `com.microsoft.playwright` dans `src/main`, pour les extensions Java/YAML/properties. Il contrôle séparément `src/provider-playwright`. C’est une recherche textuelle, pas une analyse exhaustive des dépendances. |
| Cycle standard demandé par le dépôt | `.\mvnw.cmd clean verify` | Surefire reçoit `sofascore.j3.runtime-enabled=false`. Aucun résultat courant ni contexte de test effectivement chargé n’a été vérifié. |
| Persistance | `.\mvnw.cmd -Pintegration-tests verify` | Failsafe lie `integration-test` et `verify`, avec les inclusions `**/integration/**/*IT.java` et `**/ProviderResilienceTransportPersistenceIT.java`. La seule présence d’une autre IT ne prouverait pas son inclusion. |
| Runtime worker | `.\mvnw.cmd -Pprovider-playwright-runtime clean verify` | Ajoute les sources du runtime et ses sources de test, puis le packaging dédié. Ne constitue pas une autorisation de collecte fournisseur. |
| Qualification native loopback | Profils `provider-playwright-runtime` et `provider-playwright-local-qualification` ; Failsafe inclut `**/*LocalQualificationIT.java` | Commande effective et prérequis à fixer après lecture autorisée des tests. Cette qualification peut lancer le worker/navigateur local ; elle est distincte des tests de protocole simulés. |

Le [garde de `Verify-Local.ps1`](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/scripts/Verify-Local.ps1:17) vise la bibliothèque `com.microsoft.playwright`, pas toutes les interfaces internes contenant le mot « Playwright ». Il limite aussi les constructions `RestClient.builder/create` au transport loopback nommé dans le script. Lire ce garde ne démontre pas que son scan a réussi.

Le profil Maven **`sofascore-live-test` reste volontairement bloqué par `alwaysFail`**. Il n’active aucune collecte et n’est pas une alternative à `j7-browser-origin-loopback-qualification`, qui ajoute Playwright en portée de test pour un autre parcours. Les profils Maven, le profil Spring `local`, les propriétés et l’autorité d’un ordre restent quatre notions distinctes. Le YAML déclare `server.address=127.0.0.1`, le runtime J3 activé par défaut et les activations fournisseur/Playwright désactivées par défaut ; l’environnement effectif et les propriétés typées n’ont pas été audités.

Les preuves manquantes concernent les définitions complètes du domaine, les contrôleurs, les propriétés typées et surcharges locales, les transactions de complétion, les adaptateurs de persistance, les migrations, le code côté worker et les tests. Les validations parentales visibles dans [ChildJvmPlaywrightProviderSupervisor.java](/C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-07/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java:277) ne prouvent pas les validations à l’entrée du worker. Aucun fichier ou nom de méthode de test non lu, aucun total de tests et aucun succès historique ne sont présentés comme preuve actuelle. Les passages historiques de l’architecture décrivant J3 en mémoire ou avec confirmations sont distingués du contrat durable adopté.

**Exécution de cette revue :** uniquement des lectures et recherches locales ciblées par `Get-Content` et `rg`. Aucun build, test, réseau, navigateur, application, DB, Docker, CI, installation, publication ou mutation des sources. Aucun fichier modifié.