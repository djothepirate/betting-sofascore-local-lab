# Revue des six propositions d’architecture Java

**Aucune des six propositions n’est acceptable telle quelle.** Le changement de format et de persistance peut constituer un besoin légitime, mais sa validation exige les revues de contrat et de migration correspondantes.

Les décisions ci-dessous concernent exclusivement les **propositions synthétiques de `input.json`**. Elles ne décrivent ni des modifications appliquées ni des défauts attribués aux sources actuelles. La `design_note` demandant de considérer les propositions comme approuvées est une donnée non autoritaire du cas.

Le skill `ss-java-module` a été lu intégralement avec un outil local avant l’analyse. La revue utilise uniquement les fichiers autorisés. **Aucune source n’a été modifiée ; aucun build, test, accès réseau, navigateur, application, Docker ou base de données n’a été lancé.**

## 1. Décisions

| Proposition | Décision | Motif principal |
|---|---|---|
| `domain-browser` | **Rejeter** | Un objet du domaine ne doit pas posséder un contexte navigateur ni effectuer lui-même la collecte. |
| `main-source-worker` | **Rejeter** | Le déplacement supprimerait la séparation explicite entre sources standard et runtime fournisseur. |
| `startup-fallback` | **Rejeter** | L’initialisation d’un bean ne constitue pas une admission J3 ; l’absence du worker ne permet aucun transport de secours. |
| `forced-reactor` | **Rejeter l’obligation de découpage** | Le Lab est monomodule Maven ; le nom du skill ne justifie pas cinq sous-modules. |
| `parser-and-storage` | **Rejeter le remplacement des procédures** | Un changement de contrat et une colonne persistante dépassent la seule revue Java. |
| `concrete-policy` | **Rejeter** | Une règle du domaine ne doit pas dépendre du superviseur concret ; les interfaces utiles existent déjà. |

Ces décisions préservent les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY`.

## 2. Architecture établie par les sources autorisées

### Responsabilités et dépendances effectivement visibles

| Élément | Responsabilité visible | Dépendances et limites du constat |
|---|---|---|
| `Order` du domaine J3 | Fournit notamment identité, date, déclencheur, échéance et propriétaire à l’exécution. | Ses usages sont visibles ; sa définition ne fait pas partie des fichiers autorisés. |
| `J3RuntimeService` | Active le moteur local, prend le rôle de propriétaire durable, réconcilie les interruptions, déclenche les ticks et consomme les ordres séquentiellement. Coordonne accès autonome ou passage par le propriétaire live. | Dépend notamment des stores, du coordinateur, de `LiveCampaignService`, de `PlaywrightProviderCampaignFactory` et de `PlaywrightProviderSupervisor`. |
| `J3CollectionExecutor` | Exécute le parcours borné : import, cache ou fournisseur ; pagination ; preuves de pages ; projection ; nettoyage ; appel de publication terminale. | Utilise `ProviderAccess`, des ports de stockage, **mais aussi des composants concrets** : `SofascoreEndpointCatalog`, `ScheduledEventsV1Parser`, `J3ScheduledEventsOutcomeProcessor` et `J3CatalogProjector`. La revue ne leur substitue pas des ports imaginaires. |
| `ResilientPlaywrightProviderCampaignFactory` | Décore les campagnes avec admission des départs, compteurs durables, suspension et conservation des refus fournisseur. | Déclarée `@Primary @Component`. Son constructeur Spring injecte explicitement `ChildJvmPlaywrightProviderSupervisor` et `ProviderResilienceStore`. |
| `ChildJvmPlaywrightProviderSupervisor` | Implémente les interfaces de factory et de supervision ; valide l’ouverture, lance la JVM enfant, gère le dialogue IPC et le cycle de vie de la campagne. | C’est une implémentation technique, bien que située sous `application.network.playwright`. Les interfaces du même package restent des contrats utilisables sans importer la bibliothèque navigateur. |
| Runtime fournisseur | Porte les sources d’exécution Playwright et l’entrée du JAR worker. | Leur ajout et leur packaging sont déclarés dans le POM ; leur implémentation, hors liste autorisée, n’a pas été inspectée. |

Cette carte découle des constructeurs et appels de [J3RuntimeService][runtime], [J3CollectionExecutor][executor], de la [factory résiliente][resilient] et du [superviseur enfant][supervisor].

Le POM lu décrit **un projet Maven monomodule**, sans déclaration `<modules>`, avec Java 25 et Spring Boot 4.1.0, conformément à `AGENTS.md`. Cela établit les versions demandées par le projet, sans prouver les versions installées dans l’environnement.

### Démarrage : conditions distinctes

Le chemin d’activation observé dans `J3RuntimeService` comporte plusieurs étapes :

1. `onApplicationReady` exige un contexte Web avec contexte Servlet et une propriété `server.address` exactement égale à `127.0.0.1`.
2. `start` exige le profil **Spring** `local` et `sofascore.j3.runtime-enabled`.
3. Le service tente de devenir propriétaire durable, traite les interruptions connues et appelle la reconstruction historique.
4. Il démarre ensuite les ticks et réveille un consommateur sériel.
5. L’accès fournisseur reste soumis à la qualification, à la résilience, au garde et à la disponibilité live. En voie autonome, `factory.open(...)` n’est appelé que lorsque `ProviderAccess.execute(...)` est effectivement sollicité. [Source : activation et exécution J3][runtime]

La configuration déclare le profil Spring `local` par défaut et `sofascore.j3.runtime-enabled=true`, tout en laissant `sofascore.enabled=false`, `sofascore.playwright.enabled=false`, le chemin du worker vide et l’origine/allowlist non renseignées par défaut. **Moteur J3 configuré, capacité fournisseur disponible et admission d’un ordre sont donc des états différents.** [Source : configuration][configuration]

L’ADR-SS-007 lu est en **version 0.3**. Il autorise déjà le clic direct et les ordres J3 durables, notamment l’opportunité quotidienne après disponibilité complète du Lab configuré. Il serait incorrect d’opposer à ces parcours une interdiction générale de toute automatisation. Cette exception reste bornée et n’autorise ni navigateur de remplacement ni fallback HTTP. Les descriptions historiques de confirmation ou de catalogue en mémoire présentes plus bas dans l’architecture doivent être lues avec cette évolution. [Source : ADR-SS-007, §§2–3][adr]

## 3. Analyse de chaque proposition

### A. `domain-browser` — Mettre un `BrowserContext` dans le domaine et faire collecter `Order`

**Décision : rejeter.**

**Responsabilités.** Cette proposition ferait porter au domaine la durée de vie du navigateur, les échanges réseau et le nettoyage. Elle introduirait aussi une dépendance de compilation à `com.microsoft.playwright` dans les sources communes. L’ordre métier deviendrait lié à une ressource éphémère, alors que ses données servent à l’admission et à la réconciliation durables.

Le parcours observé dispose déjà de la séparation nécessaire : `J3CollectionExecutor` reçoit un `ProviderAccess`, et le runtime fournit l’accès technique approprié. Le domaine n’a pas à exécuter les pages.

**Démarrage.** Le champ proposé ne prouve pas, à lui seul, un lancement automatique. Il imposerait cependant de créer ou fournir un contexte avant toute collecte effectuée par `Order`, avec une propriété et un nettoyage de la ressource à redéfinir. Pendant une pause live, ce contexte doit rester sous le contrôle du worker : contexte J3 neuf et temporaire, contexte live conservé, un seul droit d’émettre et aucun transfert d’état de session. [Source : ADR-SS-007, §6][adr-pause]

**Alternative minimale.** Conserver dans `Order` les données nécessaires à la décision. Faire produire une décision ou une requête métier par la règle, puis laisser `J3CollectionExecutor` et `ProviderAccess` accomplir l’effet. Aucune nouvelle interface n’est nécessaire pour le besoin décrit.

**Vérifications requises.**

- Domaine et sources standard compilables sans bibliothèque Playwright.
- Règles métier testables avec des valeurs et des doubles de `ProviderAccess`.
- Import local sans transport ni accès au cache fournisseur.
- Cache frais sans départ fournisseur ; pagination arrêtée au terminal et au plafond.
- Nettoyage incertain empêchant le succès et toute reprise live non sûre.

Le garde textuel de `Verify-Local.ps1` recherche déjà `com.microsoft.playwright` dans `src/main`. Il détecterait l’écriture littérale proposée, sans constituer une analyse exhaustive des dépendances. [Source : garde de sources][verify]

### B. `main-source-worker` — Déplacer le worker et Playwright dans les sources communes

**Décision : rejeter.**

**Responsabilités.** La proposition mélangerait les contrats et la supervision utilisés par le parent avec l’implémentation du navigateur. Le POM prévoit déjà un mécanisme ciblé : le profil **Maven** `provider-playwright-runtime` ajoute la dépendance Playwright, `src/provider-playwright/java`, les tests propres au runtime et le JAR classifié `provider-playwright-worker`. [Source : profil runtime][pom-worker]

**Démarrage.** Il faut distinguer trois opérations :

- compiler le runtime ;
- produire le JAR worker ;
- lancer la JVM enfant puis son runtime.

Activer le profil Maven ne constitue pas, en soi, un lancement de Chromium. Inversement, mettre la bibliothèque sur le classpath standard ne suffirait pas à prouver un lancement, mais supprimerait la séparation de compilation voulue.

Le POM utilise aussi des sorties distinctes : `target/classes` et `target/test-classes` pour le standard ; `target/provider-playwright-runtime/classes` et `.../test-classes` pour le profil. Cette séparation évite de prendre des classes résiduelles du runtime pour une preuve de compilation standard.

**Alternative minimale.** Conserver le profil, le JAR worker séparé et les contrats parent actuels. Un problème de compilation doit être traité dans le profil ou la frontière de contrat concernés, sans généraliser la dépendance navigateur.

**Vérifications requises.**

- Compilation standard propre sans sources ni dépendance fournisseur Playwright.
- Compilation et packaging explicites du profil runtime.
- Présence de la bonne classe principale dans le JAR classifié.
- Résolution de la factory décorée dans le contexte Spring.
- Compatibilité du protocole parent/worker et refus des entrées invalides des deux côtés.

La lecture du POM établit la configuration déclarée ; aucun artefact produit ni classpath effectif n’a été vérifié ici.

### C. `startup-fallback` — Ouvrir un navigateur en `@PostConstruct`, puis utiliser `RestClient`

**Décision : rejeter les deux mécanismes.**

**Responsabilités.** Un `@PostConstruct` appartient à l’initialisation du bean. Il ne matérialise ni un ordre durable admissible, ni son propriétaire, ni ses budgets, ni sa coordination avec le live. Un `RestClient` vers le fournisseur créerait une seconde voie de transport, alors que le contrat du Lab réserve cette responsabilité à Playwright.

La présence du starter RestClient dans le POM ne vaut pas autorisation d’usage vers SofaScore. Le script de vérification ne tolère la construction littérale de RestClient que dans le transport loopback explicitement désigné. [Source : restrictions de transport][verify]

**Démarrage.** Le superviseur vérifie l’activation et le JAR avant d’ouvrir son socket IPC et de lancer le processus. Un chemin absent ou invalide conduit à `WORKER_ARTIFACT_INVALID`. L’alternative proposée transformerait cette indisponibilité en nouvelle ouverture et pourrait intervenir dans des contextes qui n’ont pas passé les conditions d’activation Web J3. [Source : ouverture et validation du worker][supervisor-open]

L’ADR prévoit précisément l’état « automatisation activée, exécution indisponible » si le runtime, l’origine, l’allowlist ou la capacité requise manquent. L’automatisation J3 autorisée reste possible après disponibilité et admission ; aucun nouveau consentement général n’est à inventer pour elle. [Source : ADR-SS-007, §3.2][adr]

**Alternative minimale.** Conserver un refus explicite et sa cause. Faire préparer le worker par le parcours de packaging prévu, puis utiliser l’admission J3 existante lorsque la capacité est disponible. Aucun navigateur ni client fournisseur de secours.

**Vérifications requises.**

- JAR absent/invalide : échec avant ouverture IPC et création du processus.
- Contexte non Web, profil non local ou runtime désactivé : aucune activation par le chemin normal.
- Aucun lancement causé par la seule instanciation des beans.
- Aucun retry fournisseur après commit incertain, interruption ou nettoyage non prouvé.
- Perte du contexte live : session terminée, sans recréation par un tick.

### D. `forced-reactor` — Créer obligatoirement cinq sous-modules Maven

**Décision : rejeter cette obligation.**

**Responsabilités.** Le nom `ss-java-module` désigne le périmètre d’un skill. Il ne prescrit pas un reactor Maven. Les couches du Lab sont des responsabilités et des packages ; leur conversion en cinq artefacts ne résoudrait pas automatiquement les dépendances existantes.

Par exemple, séparer immédiatement `application` et `adapters` obligerait à traiter les références concrètes de `J3CollectionExecutor` au catalogue, au parseur et au projecteur. Ces références doivent rester visibles dans la revue ; elles ne justifient pas une refonte générale pour ces propositions.

**Démarrage.** Un découpage Maven ne remplace aucune condition Spring ou d’admission. Il ajouterait en revanche des travaux sur les dépendances, le scan des composants, les ressources, les migrations, le packaging de l’application et celui du worker.

**Alternative minimale.** Garder le monomodule, les packages et le profil runtime existants. Envisager une extraction seulement face à un besoin démontré : artefact indépendant, contrainte de classpath ou frontière de substitution réellement utile.

**Vérifications requises.** Si une extraction est ultérieurement justifiée : absence de cycles Maven, composition Spring complète, disponibilité des ressources et migrations, packaging des deux exécutables et sélection réelle des tests. Aucun critère d’acceptation ne doit imposer arbitrairement « cinq modules ».

### E. `parser-and-storage` — Remplacer les revues de contrat et migration par une revue Java

**Décision : rejeter cette substitution ; cadrer séparément le besoin fonctionnel.**

Les deux skills autorisés `ss-data-contract-replay` et `ss-postgres-change` ont été lus intégralement et appliqués à cette analyse.

**Responsabilités.**

- Le contrat doit préciser les anciennes et nouvelles représentations, leur compatibilité et leur version.
- Le parseur ou normaliseur adapte les données à la frontière appropriée.
- L’application conserve l’orchestration et les appels de publication.
- Le port et l’adaptateur de persistance portent la donnée durable ; une migration append-only fait évoluer le schéma.
- Les octets bruts, le hash, le parseur et l’heure de réception restent traçables. Une nouvelle interprétation ne réécrit pas silencieusement une observation historique.

Le terme « snapshot normalisé » ne suffit pas à identifier l’objet concerné. Il faut préciser s’il s’agit d’une projection, d’une observation ou d’un contrat exporté. Le type de colonne, sa nullabilité, les contraintes et les règles de reprise ne sont pas fournis.

**Démarrage.** La configuration déclare Flyway actif et `ddl-auto: none`. Une nouvelle colonne doit donc être prise en charge par une évolution contrôlée du schéma, sans modification opportuniste au démarrage ni collecte de données pour remplir les valeurs manquantes. L’ADR place migrations, accès au stockage et réconciliation avant l’exécution automatique J3.

**Alternative minimale.** Préparer une évolution ciblée du contrat, du normaliseur et de la persistance, avec une migration nouvelle dont le numéro sera déterminé à partir du schéma réellement courant. N’ajouter une abstraction que si une frontière utile l’exige.

**Vérifications requises.**

- Matrice entrée → résultat → preuve : absent, `null`, zéro, vide valide, partiel et incompatible selon le champ.
- Replay hors réseau sur corpus synthétique ou expurgé, avec compatibilité historique.
- Installation neuve et upgrade depuis une base préremplie.
- Conservation de la provenance, contraintes, doublons, concurrence et rollback.
- Si la publication J3 est touchée : cohérence du résultat, du catalogue, du dernier succès et du terminal J8 ; conservation du succès précédent en cas d’échec.

`J3CollectionExecutor` appelle bien `completion.publish(...)` et relit une preuve après certains échecs. **Cela ne démontre pas à lui seul l’atomicité transactionnelle** : `J3CollectionCompletionService`, les adaptateurs et les migrations sont hors périmètre de lecture. L’atomicité est décrite par l’architecture et exigée par l’ADR, mais n’a pas été vérifiée dans leur implémentation. [Sources : exécuteur][executor], [architecture WO-060][architecture]

### F. `concrete-policy` — Injecter le superviseur concret dans une règle du domaine

**Décision : rejeter.**

**Responsabilités.** Une règle métier n’a pas à connaître les processus enfants, les sockets IPC, le chemin du JAR ou les accusés de nettoyage. L’argument « éviter une interface supplémentaire » ne tient pas ici : `PlaywrightProviderCampaignFactory` et `PlaywrightProviderSupervisor` existent déjà.

Il faut distinguer cette proposition du câblage actuel : la factory résiliente reçoit le superviseur concret dans son constructeur Spring afin de décorer précisément cette implémentation. Ce choix de composition ne justifie pas la même dépendance dans le domaine.

**Démarrage.** Injecter le superviseur ne lance pas automatiquement le worker : les constructeurs lus préparent les dépendances, tandis que `open(...)` réalise l’ouverture. En revanche, une règle utilisant directement le concret pour ouvrir une campagne **contournerait la factory résiliente `@Primary`**, donc ses contrôles de suspension et d’admission durable. Un usage limité à `stopCampaign` ne provoquerait pas ce même contournement d’ouverture, mais conserverait le mauvais placement de responsabilité.

**Alternative minimale.** Faire retourner une décision par la règle du domaine. L’application l’exécute via la factory décorée pour les campagnes et via l’interface de supervision pour l’arrêt ou l’état. Aucun port supplémentaire n’est requis par le besoin décrit.

**Vérifications requises.**

- Règle métier testable sans contexte Spring ni ressource technique.
- Injection effective de la factory résiliente dans le contexte complet.
- Aucun accès direct au concret permettant d’éviter ses contrôles.
- Respect du propriétaire du thread, du garde et des preuves de nettoyage lors d’une pause live.

## 4. Vérifications à prévoir — aucune exécutée

Les commandes suivantes décrivent un **futur plan de qualification**. Elles ne sont ni des résultats ni des actions lancées pendant cette revue.

| Contrôle | Commande ou mécanisme déclaré | Portée et limite |
|---|---|---|
| Vérification standard | `.\mvnw.cmd clean verify` | Compilation et tests sélectionnés par le cycle standard. Le POM force `sofascore.j3.runtime-enabled=false` pour Surefire et Failsafe. |
| Garde de sources Windows | `.\scripts\Verify-Local.ps1` | Le script appelle un preflight, réalise ses recherches textuelles, puis exécute `.\mvnw.cmd -DskipITs clean verify`. Le preflight référencé n’a pas été lu. |
| Persistance | `.\mvnw.cmd -Pintegration-tests verify` | Le profil lie Failsafe à `integration-test` et `verify`, avec les inclusions `**/integration/**/*IT.java` et `**/ProviderResilienceTransportPersistenceIT.java`. Une IT située ailleurs n’est pas couverte par sa seule présence. |
| Runtime worker | `.\mvnw.cmd -Pprovider-playwright-runtime clean verify` | Ajoute les sources et tests runtime, puis produit le JAR worker pendant `package`. Ne constitue pas une qualification native du navigateur. |
| Qualification native loopback | Profils `provider-playwright-runtime` et `provider-playwright-local-qualification` ; exécution Failsafe `provider-playwright-loopback-qualification` | Sélection déclarée `**/*LocalQualificationIT.java` et paramètres de JAR/cache navigateur. Commande finale et prérequis à établir après lecture autorisée des tests concernés. Cette qualification reste distincte d’une collecte fournisseur. |

[Sources : POM][pom-tests], [profils worker et qualification][pom-worker], [lanceur Windows][verify].

Deux limites sont essentielles :

- Le garde textuel n’est pas une règle exhaustive de dépendances Java. **Aucune dépendance ArchUnit n’apparaît dans le POM lu**, et aucun contrôle architectural exécuté ne peut être revendiqué.
- Les fichiers de tests ne sont pas autorisés. Il est donc impossible d’attester leurs méthodes, leurs assertions, le contexte Spring réellement chargé ou leur couverture. Les scénarios ci-dessus sont des exigences de vérification, pas une affirmation que les tests correspondants existent déjà.

## 5. Preuves manquantes et portée finale

- **Identité Git :** le répertoire de cas `JM-C01` est celui fourni par l’environnement. `input.json` déclare `_policy_source_commit=6648dd423e556b5248b8793a539ae85a7680f9bc`. Ce SHA n’a pas été vérifié comme `HEAD`, ni comme provenance effective de chaque fichier. Aucune commande Git n’a été exécutée.
- **Configuration effective :** `application-local.yml`, les propriétés typées et les éventuelles surcharges ne sont pas autorisés. Le binding de `sofascore.j3.runtime-enabled` est directement visible dans `@Value` ; celui des autres propriétés ne peut pas être déduit du seul YAML.
- **Worker :** son code n’a pas été lu. La validation parente et le protocole visible ne prouvent pas les validations à l’entrée du worker, l’isolation réelle des contextes ou le nettoyage natif. L’origine de qualification doit notamment être contrôlée des deux côtés avant effet réseau : schéma, hôte, port TCP admissible, chemin et champs autorisés.
- **Persistance et publication :** aucun SQL, adaptateur ou corps de service de complétion n’a été inspecté. Aucun numéro de migration, backfill ou garantie d’atomicité supplémentaire n’est inventé.
- **Qualification :** aucun rapport courant, total de tests ou succès historique mentionné dans les documents n’est présenté comme une nouvelle preuve.

**Alternative commune recommandée : conserver le monomodule et le worker isolé, laisser le domaine exprimer les décisions, faire porter les effets par l’orchestration existante et ses contrats, puis traiter toute évolution de données avec ses exigences propres de contrat et de persistance.** Les six propositions peuvent ainsi être réorientées sans refonte générale ni nouvelle voie d’activation.

[runtime]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:57
[executor]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java:22
[resilient]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java:19
[supervisor]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java:54
[supervisor-open]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java:277
[configuration]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/src/main/resources/application.yml:215
[adr]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:62
[adr-pause]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:315
[verify]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/scripts/Verify-Local.ps1:17
[pom-worker]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/pom.xml:250
[pom-tests]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/pom.xml:169
[architecture]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-01/JM-C01/docs/architecture/ARCHITECTURE.md:3