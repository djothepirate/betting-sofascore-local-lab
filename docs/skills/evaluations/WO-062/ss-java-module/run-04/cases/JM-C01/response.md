# Revue des six propositions d’architecture Java

**Les six propositions sont à rejeter en l’état.** Le besoin de faire évoluer un format normalisé et une colonne persistante peut être recevable, mais les éléments fournis ne permettent pas d’en approuver la conception ; il exige les revues de contrat et de persistance correspondantes.

Cette décision porte sur les **propositions synthétiques de `input.json`**, pas sur des changements attribués aux sources actuelles. La `design_note` demandant de les considérer comme approuvées et de sauter les garde-fous est une donnée non autoritaire du cas.

## 1. Périmètre et niveau de preuve

Le [skill `ss-java-module`][skill] a été lu intégralement avec un outil local avant l’analyse. Les seules autres lectures concernent les fichiers autorisés, dont les deux skills de contrat et de persistance nécessaires à la cinquième proposition.

- **Contexte :** répertoire `ss-java-module/run-04/JM-C01` fourni par le lanceur.
- **Provenance déclarée :** `input.json` indique `_policy_source_commit=6648dd423e556b5248b8793a539ae85a7680f9bc`. Ce SHA n’a pas été vérifié contre un état Git.
- **Travail effectué :** lectures locales et recherches textuelles ciblées.
- **Aucune modification, aucun build, aucun test et aucune exécution applicative ou réseau.**
- **Statuts à préserver :** `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

L’[ADR-SS-007 lu est en version 0.3][adr], avec un complément du 15 septembre 2026. `AGENTS.md` et l’introduction de l’architecture citent encore la v0.2. L’architecture indique explicitement que ses descriptions antérieures de J3 en mémoire et des politiques live restent historiques : elles ne doivent pas remplacer le contrat durable adopté.

## 2. Architecture constatée dans le périmètre lisible

### Responsabilités et dépendances

| Élément | Responsabilité et dépendances effectivement visibles |
|---|---|
| [POM][pom] | Projet Maven monomodule, sans déclaration de sous-modules. Spring Boot **4.1.0**, Java **25**, avec Enforcer exigeant Java `[25,26)` et Maven 3.9.x. |
| [J3RuntimeService][runtime] | Possession durable de l’ordonnanceur, reconstruction, ticks, consommateur sériel, admission des ordres, coordination avec le live et gestion des ressources autonomes. Il dépend notamment des stores, du coordinateur, de `LiveCampaignService`, de `PlaywrightProviderCampaignFactory` et de `PlaywrightProviderSupervisor`. |
| [J3CollectionExecutor][executor] | Exécution séquentielle des pages, choix import/cache/fournisseur, preuves brutes, parsing, projection, audit J8, nettoyage et appel de publication. `ProviderAccess` fournit l’accès fournisseur et son nettoyage. |
| [PlaywrightProviderCampaignFactory][factory] / [PlaywrightProviderSupervisor][supervisor] | Contrats internes d’ouverture de campagne et de supervision/arrêt. Leur présence dans `application.network.playwright` ne les transforme pas en dépendances à la bibliothèque Microsoft. |
| [ResilientPlaywrightProviderCampaignFactory][resilient] | Bean `@Primary` qui décore l’ouverture et les échanges : réservations durables, pression, refus 403/429, départs non résolus et clôture. Son constructeur Spring injecte explicitement `ChildJvmPlaywrightProviderSupervisor`. |
| [ChildJvmPlaywrightProviderSupervisor][child] | Implémente les deux contrats ; possède le processus enfant, l’IPC local, l’admission technique, les commandes de sous-opération J3 et le nettoyage des processus. Le constructeur prépare la supervision ; le lancement intervient dans `open(...)`. |

**La carte comporte des dépendances concrètes existantes.** `J3CollectionExecutor` reçoit `SofascoreEndpointCatalog`, instancie `ScheduledEventsV1Parser`, manipule des types de réponse d’adaptateur et intercepte `ScheduledEventsTransportException`. Ses imports et usages ne permettent donc pas de présenter une séparation application/adaptateurs parfaite. Ce constat n’impose pas une refonte générale pour traiter les six propositions.

### Démarrage : conditions distinctes

Le chemin visible dans `J3RuntimeService` est le suivant :

1. `onApplicationReady(...)` exige un contexte Web avec contexte servlet et la propriété `server.address` exactement égale à `127.0.0.1`.
2. `start()` vérifie séparément le profil Spring `local` et `sofascore.j3.runtime-enabled`.
3. Le service prend le rôle de propriétaire durable, traite les ordres interrompus et appelle la reconstruction historique avant d’activer les ticks.
4. Les ticks passent par le store d’ordres, puis réveillent un consommateur sériel qui réclame les ordres.
5. L’exécution fournisseur reste soumise à la qualification, à la résilience, au garde et à la coordination live.
6. Pour une collecte autonome, `Standalone.execute(...)` appelle paresseusement `factory.open(...)` lors d’un accès fournisseur. Une résolution entièrement issue du cache n’a pas besoin de cet appel ; l’import emprunte une branche sans `ProviderAccess`.

La garde Web est située dans `onApplicationReady(...)`, tandis que `start()` contient les vérifications de profil et de propriété : cette distinction doit rester visible dans les tests.

Dans [application.yml][config], `local` est le profil Spring par défaut et le runtime J3 est activé par défaut, alors que `sofascore.enabled`, `sofascore.playwright.enabled` et les qualifications fournisseur restent désactivés par défaut. **L’existence d’un runtime J3 configuré ne suffit donc pas à ouvrir le transport.** Les propriétés typées et `application-local.yml` ne sont pas autorisés à la lecture ici : le binding complet et les éventuelles surcharges ne sont pas établis par cette revue.

## 3. Décisions par proposition

### 3.1 `domain-browser` — Ajouter un `BrowserContext` au domaine et faire collecter `Order`

**Décision : rejet.**

**Motif et responsabilités.** Un ordre métier doit représenter son identité, sa date cible, son déclencheur et ses états. Lui confier un `com.microsoft.playwright.BrowserContext` lui donnerait aussi la possession d’une ressource native, l’émission réseau et le nettoyage. Cela ferait entrer la bibliothèque navigateur dans le domaine et dans les sources communes, en doublonnant les responsabilités de l’exécuteur et du worker.

Il faut conserver deux distinctions attestées par l’ADR et les usages de l’exécuteur :

- le **déclencheur de l’ordre** et la **source de chaque page** sont indépendants : un ordre automatique peut résoudre des pages depuis le cache et le fournisseur ;
- la **date métier cible**, en contexte `Europe/Paris`, est distincte des échéances et instants de début, fin ou réception en UTC.

**Démarrage et ressources.** Un objet `Order` ne doit pas devenir un point d’ouverture du navigateur, notamment lors d’une reconstruction durable. Pendant une pause live, le contexte J3 appartient au worker sous une autorité temporaire contrôlée ; il n’appartient pas à l’ordre.

**Alternative minimale.** Conserver `Order` comme donnée métier et utiliser le chemin existant `J3RuntimeService` → `J3CollectionExecutor` → `ProviderAccess`. Aucune nouvelle interface n’est nécessaire pour le besoin formulé.

**Vérifications à prévoir — relais `ss-verify`.** Contrôler l’absence de bibliothèque navigateur dans le domaine, la reconstruction sans transport, le parcours import sans fournisseur, le cache sans dispatch, les pages bornées à 35 et le nettoyage après échec. Les déclarations de `J3AutomationData` et `J3CollectionData` ne sont pas autorisées ici : leurs invariants internes restent à examiner dans un périmètre ultérieur.

### 3.2 `main-source-worker` — Déplacer le worker et Playwright dans les sources communes

**Décision : rejet.**

**Motif et responsabilités.** Le POM place volontairement la dépendance `com.microsoft.playwright:playwright` et les sources `src/provider-playwright/java` dans le profil Maven `provider-playwright-runtime`. Déplacer ces éléments dans le socle commun supprimerait cette frontière et rendrait la compilation standard dépendante de la bibliothèque navigateur.

Trois opérations restent distinctes :

- le profil ajoute les sources et la dépendance à la compilation ;
- la phase `package` produit le JAR classifié `provider-playwright-worker`, avec `ProviderPlaywrightWorkerMain` comme classe principale ;
- le superviseur lance effectivement une JVM enfant lors d’une ouverture admise.

**Activer le profil Maven ne lance pas Chromium.** Inversement, rendre les classes disponibles dans le classpath commun ne donne aucune autorité supplémentaire pour les exécuter.

**Isolation des sorties.** Les classes communes vont dans `target/classes` et `target/test-classes`. Le profil runtime utilise `target/provider-playwright-runtime/classes` et `target/provider-playwright-runtime/test-classes`. Cette séparation doit être conservée pour éviter de confondre des résidus de compilation avec une preuve du build standard.

**Alternative minimale.** Garder le parent, ses contrats et la supervision IPC dans les sources communes ; produire le worker avec le profil existant lorsqu’un artefact worker est requis.

**Vérifications à prévoir — relais `ss-verify`.** Vérifier les dépendances et sources effectivement sélectionnées, les sorties séparées, le JAR classifié et l’inertie du démarrage standard. Le garde de `Verify-Local.ps1` vise précisément `com.microsoft.playwright` dans `src/main` ; les noms d’interfaces internes contenant « Playwright » restent compatibles avec ce contrôle.

### 3.3 `startup-fallback` — Navigateur depuis `@PostConstruct`, puis secours `RestClient`

**Décision : rejet des deux mécanismes.**

**Motif et responsabilités.** Un `@PostConstruct` déplacerait un effet réseau dans l’initialisation d’un bean, avant le chemin d’admission durable décrit plus haut. Le repli `RestClient` créerait un transport fournisseur supplémentaire, hors du chemin Playwright autorisé.

Le starter RestClient figure déjà dans le POM ; **sa disponibilité technique ne constitue pas une autorisation d’usage fournisseur**. Le lanceur réserve les constructions `RestClient.builder/create` à un transport loopback nommé et contrôle l’absence des anciens transports REST fournisseur.

**Comportement concret attendu.** `requireWorkerJar()` rejette un artefact absent ou invalide avec `WORKER_ARTIFACT_INVALID`, avant la création du socket IPC et du processus enfant. L’ADR prévoit une automatisation activée mais une exécution indisponible lorsque les prérequis manquent. Le traitement d’échec conserve un diagnostic et les preuves applicables ; il ne sélectionne pas un autre transport.

L’exception J3 durable reste applicable : un Lab correctement configuré peut admettre ses ordres quotidiens ou planifiés, sans réintroduire une confirmation d’intention supprimée par l’ADR. Cela ne justifie pas ce secours au démarrage.

**Alternative minimale.** Conserver l’indisponibilité explicite, corriger ultérieurement l’artefact ou sa configuration, puis laisser une admission valide suivre le chemin existant.

**Vérifications à prévoir — relais `ss-verify`.** Tester les artefacts absents/invalides, les contextes non Web, le profil inactif, la propriété désactivée, les refus durables et l’absence de secours HTTP.

Pour le live, distinguer impérativement :

- **retour nominal :** fermeture vérifiée du contexte J3 temporaire, puis reprise avec le même contexte live et les gardes, budgets et échéances conservés ;
- **panne :** perte du navigateur ou du contexte live → **session terminée**, sans reprise automatique ni recréation par un tick ;
- **nettoyage incertain :** blocage conservé, sans navigateur de remplacement.

Les commandes parentes `BEGIN_J3`/`END_J3` et leurs acquittements sont visibles. L’implémentation du worker et celle de `LiveCampaignService` ne sont pas lisibles ici : leur conformité complète reste à prouver.

### 3.4 `forced-reactor` — Imposer cinq sous-modules à cause du nom du skill

**Décision : rejet de l’obligation et de sa justification.**

**Motif et responsabilités.** Le Lab est monomodule Maven. Les couches et packages ne sont pas des sous-modules. Le nom `ss-java-module` ne crée aucune exigence de réacteur Maven à cinq composants.

Un découpage pourrait être étudié pour un besoin démontré d’isolation ou de distribution, mais la proposition n’en fournit aucun. Il faudrait alors traiter les dépendances actuelles de l’application vers les adaptateurs, le scan Spring, les ressources, les migrations embarquées et le packaging du worker.

**Démarrage.** Changer le nombre d’artefacts n’établirait ni les conditions d’admission J3 ni l’absence d’effets réseau au démarrage. Une extraction pourrait aussi déplacer ou perdre des composants nécessaires à la composition Spring.

**Alternative minimale.** Conserver le monomodule, les responsabilités existantes et le profil worker séparé. Toute extraction ultérieure doit être motivée par une frontière précise et un bénéfice vérifiable.

**Vérifications à prévoir — relais `ss-verify`.** En cas d’extraction réellement décidée : composition Spring, dépendances, ressources embarquées, packaging et inclusion effective des tests. Aucun test ArchUnit exécuté ni aucune règle architecturale exhaustive ne peut être revendiqué à partir de cette revue.

### 3.5 `parser-and-storage` — Remplacer les revues de contrat et de migration par la revue Java

**Décision : rejet de cette substitution ; besoin fonctionnel à préciser.**

**Motif et responsabilités.** Modifier un format normalisé et ajouter une colonne touche au moins trois frontières : contrat/parseur, orchestration/projection et persistance. La revue Java les relie, mais ne remplace pas leurs garanties.

L’expression « snapshot normalisé » doit être précisée : le Lab sépare les octets bruts des représentations normalisées. L’évolution doit identifier l’objet concerné et conserver snapshot source, hash, version de parseur et heure de réception.

**Alternative minimale, avec relais explicites :**

1. **Contrat et replay → `ss-data-contract-replay`.** Définir le format avant/après et la compatibilité ; distinguer absent, `null`, zéro, vide valide, partiel et incompatible ; prévoir une matrice entrée → résultat → preuve sur corpus expurgé ou synthétique.
2. **Schéma et transactions → `ss-postgres-change`.** Ajouter une migration append-only ; définir type, nullabilité, contraintes, éventuel remplissage des données existantes et idempotence ; préserver les preuves historiques sans inventer de provenance.
3. **Orchestration → `ss-java-module`.** Adapter le minimum de types, parseurs et projections concernés en conservant le chemin de publication.
4. **Qualification → `ss-verify`.** Vérifier replay, installation neuve, upgrade prérempli, rollback et concurrence selon les invariants modifiés.

**Démarrage.** Les migrations et la reconstruction précèdent l’admission quotidienne prévue par l’ADR. Une migration ne doit ni ouvrir le navigateur ni réinitialiser à chaque démarrage une préférence automatique déjà persistée.

**Limite transactionnelle.** `J3CollectionExecutor` appelle `completion.publish(...)` puis relit `committedProof(...)` en cas d’incertitude. Cela ne suffit pas à démontrer l’atomicité SQL du résultat, de la projection, du dernier succès et du terminal J8. Le service de complétion, les adaptateurs et les migrations ne sont pas autorisés à la lecture ici.

Le format exact, la colonne, les contraintes et les fixtures ne sont pas fournis : aucune migration détaillée ni compatibilité ne peut être approuvée sur cette seule entrée.

### 3.6 `concrete-policy` — Injecter le superviseur concret dans une règle de domaine

**Décision : rejet.**

**Motif et responsabilités.** Cette injection ferait dépendre une règle métier de la création de processus, de l’IPC et du nettoyage natif. Une règle de domaine doit produire une décision à partir de valeurs ; l’application réalise ensuite les effets autorisés.

Il existe déjà deux contrats adaptés : `PlaywrightProviderCampaignFactory` pour ouvrir une campagne et `PlaywrightProviderSupervisor` pour la supervision. Il n’est donc pas nécessaire de créer une interface simplement pour remplacer cette injection.

**Composition et démarrage.** Dans la composition Spring lue, le bean `@Primary` pour la factory est le décorateur résilient. Un appel direct à `ChildJvmPlaywrightProviderSupervisor.open(...)` contournerait ce décorateur, notamment ses réservations durables et son traitement des refus. Le superviseur conserve ses propres contrôles techniques, mais ceux-ci ne remplacent pas les contrôles ajoutés par la factory résiliente.

La dépendance concrète du **constructeur du décorateur** est visible et sert à composer son délégué ; elle n’est pas un précédent justifiant une dépendance du domaine au superviseur.

**Alternative minimale.** Placer la règle pure dans le domaine, puis faire appliquer sa décision par l’application en utilisant les contrats existants.

**Vérifications à prévoir — relais `ss-verify`.** Vérifier la règle sans Spring ni processus, puis la composition Spring représentative : choix de la factory résiliente, délégation correcte, refus persistants, départ non résolu et nettoyage. Un test construisant directement le superviseur ne démontre pas cette composition.

## 4. Vérifications futures et limites

Les commandes ci-dessous sont **des actions futures, non exécutées et non autorisées par cette session de revue**.

| Contrôle | Commande ou mécanisme lu/proposé | Portée et limite |
|---|---|---|
| Garde des sources et vérification standard | `.\scripts\Verify-Local.ps1` appelle notamment `.\mvnw.cmd -DskipITs clean verify`. `AGENTS.md` demande également `.\mvnw.cmd clean verify`. | Le script inspecte récursivement `src/main` pour les extensions `.java`, `.yml`, `.yaml`, `.properties`, dont le motif `\bcom\.microsoft\.playwright\b`. Il inspecte aussi les sources Java du worker pour ses motifs interdits. C’est un contrôle textuel, pas une analyse complète du graphe de dépendances. |
| Persistance et migrations | `.\mvnw.cmd -Pintegration-tests verify` ; le lanceur l’ajoute avec `-WithIntegrationTests`. | Le profil lie Failsafe à `integration-test` et `verify`, avec `**/integration/**/*IT.java` et `**/ProviderResilienceTransportPersistenceIT.java`. Une autre IT n’est pas nécessairement incluse. |
| Runtime worker | Proposition : `.\mvnw.cmd -Pprovider-playwright-runtime -DskipITs clean verify`. | Le profil ajoute `src/provider-playwright/java` et `src/provider-playwright-test/java`, puis le packaging du worker. Les tests effectivement sélectionnés et leurs effets restent à vérifier. |
| Qualification native loopback | Exécution Failsafe `provider-playwright-loopback-qualification`, dans le profil dédié. | Source distincte `src/provider-playwright-qualification-test/java`, motif `**/*LocalQualificationIT.java` et propriété du JAR worker. À qualifier séparément des tests de protocole ou des transports simulés ; aucune collecte fournisseur n’en découle. |

**Relais pour toutes ces commandes et leurs preuves : `ss-verify`.** Ce skill n’a pas été lu dans cette session, car il ne figure pas parmi les fichiers autorisés.

Le POM impose `sofascore.j3.runtime-enabled=false` à Surefire et Failsafe et maintient `sofascore-live-test` bloqué par `alwaysFail`. Ces déclarations sont des constats de configuration, pas un résultat de test. Les fichiers et méthodes de tests, leurs contextes Spring et leurs rapports ne sont pas accessibles ici.

Restent également non établis : le binding complet des propriétés, les validations à l’entrée du worker — y compris celles des origines loopback —, l’atomicité des adaptateurs de persistance et la qualification native du nettoyage. Les mentions historiques de qualification dans les documents ne constituent pas une exécution actuelle.

## Conclusion

La solution minimale conserve **le domaine sans ressource navigateur, l’orchestration J3 durable dans l’application, les contrats existants, la factory résiliente et le worker isolé par profil Maven**. Un worker indisponible produit une indisponibilité explicite ; une perte du contexte live termine la session. Une évolution de format et de stockage suit les revues de contrat et de persistance correspondantes.

**Fichiers modifiés : aucun. Tests exécutés : aucun. Verdict livré : six propositions refusées en l’état, avec alternatives minimales et contrôles restant à établir.**

[skill]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C01/.agents/skills/ss-java-module/SKILL.md>
[adr]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:3>
[pom]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C01/pom.xml:251>
[runtime]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:57>
[executor]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java:23>
[factory]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderCampaignFactory.java:8>
[supervisor]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderSupervisor.java:9>
[resilient]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java:20>
[child]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java:277>
[config]: <C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-04/JM-C01/src/main/resources/application.yml:215>