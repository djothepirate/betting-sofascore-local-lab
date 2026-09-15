# Revue des six propositions d’architecture Java

**Décision : les six propositions sont refusées en l’état.** Le besoin de modifier un format normalisé et d’ajouter une colonne persistante peut être recevable ; la proposition de remplacer les revues de contrat et de migration par une simple revue Java ne l’est pas.

Cette revue conserve les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY`.

## 1. Périmètre et valeur des preuves

Le [skill `ss-java-module`](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/.agents/skills/ss-java-module/SKILL.md) a été lu intégralement avec un outil local avant l’analyse. Les lectures suivantes ont été limitées aux fichiers autorisés et aux sections pertinentes.

Les six propositions proviennent d’[input.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/input.json). Elles sont **synthétiques : elles ne décrivent ni des changements appliqués ni des défauts constatés dans le code actuel**. La note demandant de les considérer comme approuvées et de sauter les garde-fous reste une donnée non autoritaire.

L’entrée déclare le commit source `6648dd423e556b5248b8793a539ae85a7680f9bc`. Ce SHA n’a pas été vérifié contre `HEAD` ; aucune inspection Git n’a été effectuée. Aucun Work Order d’implémentation des six propositions n’est fourni.

**Aucun fichier n’a été modifié. Aucun build, test, démarrage d’application, accès réseau, navigateur, DB, Docker, Maven ou CI n’a été exécuté.** Les vérifications décrites plus bas sont des contrôles futurs, pas des résultats obtenus.

## 2. Architecture constatée dans les sources autorisées

Le [POM](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/pom.xml:7) décrit un **projet Maven monomodule**, sans agrégat `<modules>`, avec Spring Boot **4.1.0**, Java **25** et un contrôle Enforcer exigeant Java `[25,26)`. Les répertoires de couches Java ne constituent pas des sous-modules Maven.

### Carte des responsabilités et dépendances

| Élément observé | Responsabilité et dépendances visibles |
|---|---|
| [`J3RuntimeService`](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3RuntimeService.java:57) | Porte le propriétaire durable, la reconstruction initiale, les ticks et le consommateur sériel. Utilise notamment `J3AutomationStore`, les stores de garde/résilience, le coordinateur, `LiveCampaignService`, l’exécuteur et les deux interfaces de campagne/supervision. |
| [`J3CollectionExecutor`](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/J3CollectionExecutor.java:22) | Orchestre les pages, l’import, le cache, les preuves brutes, la projection, l’audit J8 et la publication terminale. Son interface interne `ProviderAccess` sépare l’exécution fournisseur du moteur de collecte et impose une fermeture. |
| [`PlaywrightProviderCampaignFactory`](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderCampaignFactory.java:8) | Contrat d’ouverture des campagnes. Les variantes non prises en charge échouent explicitement ; elles ne se rabattent pas silencieusement sur une politique historique. |
| [`PlaywrightProviderSupervisor`](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderSupervisor.java:9) | Contrat distinct pour arrêter une campagne ciblée et connaître l’identité de la campagne active. |
| [`ResilientPlaywrightProviderCampaignFactory`](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java:20) | Décorateur `@Primary @Component`. Son constructeur `@Autowired` reçoit concrètement `ChildJvmPlaywrightProviderSupervisor`. Il ajoute les réservations de départ, la suspension sur refus et la conservation d’un refus bloquant même si sa persistance échoue. |
| [`ChildJvmPlaywrightProviderSupervisor`](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java:54) | Implémente les deux interfaces. Gère le processus enfant, l’IPC authentifié sur `127.0.0.1`, les admissions, les commandes du protocole 10 et le cycle de fermeture. Le lancement du processus intervient dans `open`, pas dans le constructeur. |

**Cette carte n’est pas une séparation idéale reconstruite pour la revue.** L’exécuteur dépend déjà de composants concrets : il reçoit `SofascoreEndpointCatalog`, instancie `ScheduledEventsV1Parser` et `J3ScheduledEventsOutcomeProcessor`, et intercepte `ScheduledEventsTransportException` du package adaptateur. Le runtime utilise aussi des exceptions propres au transport Playwright et au coordinateur. Ces dépendances restent visibles ; elles ne justifient pas, à elles seules, une refonte générale.

### Démarrage : conditions distinctes

Les sources montrent la séquence suivante :

1. `onApplicationReady` exige un contexte Web avec servlet et une propriété `server.address` égale à `127.0.0.1`.
2. `start` exige le profil Spring `local` et `sofascore.j3.runtime-enabled`.
3. Le runtime acquiert son rôle durable, traite les ordres interrompus et lance la reconstruction avant les ticks.
4. Le consommateur prend les ordres ; l’accès fournisseur dépend encore de la qualification, de la résilience, du garde et de l’arbitrage avec le live.
5. Pour J3 autonome, la campagne est ouverte paresseusement dans `Standalone.execute`, lorsque le moteur demande effectivement une page fournisseur. Une résolution entièrement depuis le cache n’emprunte pas cet appel.

Dans [application.yml](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/src/main/resources/application.yml:215), le runtime J3 est activé par défaut, tandis que les propriétés générales, Playwright et de qualification J3 restent désactivées par défaut. **Activer le moteur J3 n’équivaut donc pas à rendre le fournisseur disponible.** Le binding des propriétés typées et les éventuelles substitutions du contexte complet ne sont pas vérifiables avec les seuls fichiers autorisés.

L’[ADR-SS-007, version 0.3](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md:62) autorise déjà le clic direct et les ordres J3 durables concernés. Il ne faut pas réintroduire leurs anciennes confirmations. En pause live, il prévoit un contexte J3 neuf et temporaire dans le même worker, un seul droit d’émettre et la conservation du contexte live.

Enfin, trois notions doivent rester séparées :

- **Déclencheur de l’ordre** : manuel, quotidien ou planifié.
- **Source d’une page** : fournisseur, cache ou import JSON local.
- **Date métier et instants** : date cible en calendrier `Europe/Paris`, échéance et instants d’exécution/réception distincts en UTC.

L’ADR établit ces distinctions et l’exécuteur les utilise. Les définitions complètes de `J3AutomationData` et `J3CollectionData` ne font pas partie du périmètre autorisé.

## 3. Décisions par proposition

### 3.1 `domain-browser` — Ajouter `BrowserContext` au domaine et faire collecter `Order`

**Décision : refus.**

**Responsabilités.** La proposition ferait porter à un ordre durable une ressource native mutable et ses effets réseau. Elle couplerait le domaine à `com.microsoft.playwright`, au cycle de session et aux contraintes de thread. Elle détournerait la collecte du chemin qui coordonne actuellement cache, preuves, budgets, audit et nettoyage.

**Démarrage et ressources.** Un champ de type `BrowserContext` ne lance pas, à lui seul, un navigateur. Il rend toutefois la bibliothèque nécessaire à la compilation du domaine et impose de fournir un contexte vivant pour exécuter la collecte. La propriété et la fermeture de cette ressource deviendraient ambiguës, notamment pendant la pause live et après reconstruction des ordres.

**Alternative minimale.** Conserver `Order` comme donnée métier. Laisser `J3RuntimeService` admettre et coordonner l’ordre, puis `J3CollectionExecutor` collecter via **l’interface `ProviderAccess` déjà présente**. Les objets Playwright restent dans le runtime worker prévu à cet effet.

**Vérifications requises.**

- Aucun type de la bibliothèque Playwright dans le domaine ou les sources communes.
- Tests du moteur avec accès fournisseur simulé, import local et cache.
- Vérification des fermetures sur succès, annulation, exception et nettoyage incertain.
- Conservation du déclencheur, de la source de chaque page et de la date cible.

**Relais :** `ss-verify` pour la qualification ; `ss-data-contract-replay` si les données de l’ordre ou les preuves changent.

### 3.2 `main-source-worker` — Déplacer le worker et Playwright dans les sources communes

**Décision : refus.**

**Responsabilités et dépendances.** Le POM réserve la dépendance Playwright et l’ajout de `src/provider-playwright/java` au profil Maven `provider-playwright-runtime`. Il produit un JAR classifié `provider-playwright-worker` avec le point d’entrée proposé. Les contrats et la supervision du parent peuvent ainsi rester compilables sans la bibliothèque navigateur dans les dépendances communes.

**Démarrage.** Déplacer les sources ne démarrerait pas automatiquement Chromium, mais supprimerait la séparation de compilation et d’empaquetage voulue. Il faut conserver trois étapes distinctes : compiler le runtime, produire le JAR worker, puis lancer ce worker lors d’une campagne admissible.

**Alternative minimale.** Garder le monomodule et le profil existant. Produire le worker par ce profil lors d’une qualification ultérieure, puis configurer son chemin local. Conserver les sorties séparées :

- standard : `target/classes` et `target/test-classes` ;
- profil runtime : `target/provider-playwright-runtime/classes` et `test-classes`.

**Vérifications requises.**

- Compilation standard indépendante des sources et de la dépendance Playwright.
- Production du JAR worker avec le bon point d’entrée.
- Absence de contamination par des classes résiduelles d’un build de profil.
- Vérification distincte du packaging et du comportement natif.

Le profil J7 de qualification ajoute également Playwright, mais uniquement en portée `test` et sous activation explicite ; cela ne justifie pas son ajout aux dépendances communes.

**Relais :** `ss-verify`.

### 3.3 `startup-fallback` — Navigateur dans `@PostConstruct`, puis secours `RestClient`

**Décision : refus des deux mécanismes.**

**Responsabilités.** Un `@PostConstruct` ferait de la construction du service une opération de transport, avant la séquence d’admission durable. Le secours `RestClient` créerait une deuxième voie fournisseur, hors du chemin Playwright autorisé et de sa supervision.

**Démarrage.** L’exception J3 durable autorise une collecte issue d’un ordre admissible dans le Lab configuré. Elle n’autorise pas un navigateur de remplacement parce que le worker manque.

Dans le superviseur lu, `requireWorkerJar()` est appelé **avant** l’ouverture du serveur IPC et le démarrage du processus. Un chemin absent ou invalide produit `WORKER_ARTIFACT_INVALID`. Le simple contrôle du fichier ne prouve toutefois ni son contenu ni sa compatibilité protocolaire.

**Alternative minimale.** Conserver l’échec explicite sans secours fournisseur. Corriger ultérieurement la disponibilité ou la configuration de l’artefact local, puis laisser le parcours d’admission normal décider d’une nouvelle opération. Ne pas rejouer automatiquement un ordre interrompu.

Le starter `RestClient` présent dans le POM n’accorde aucune autorisation de transport fournisseur. Le lanceur ne prévoit une exception de construction directe que pour un transport loopback identifié.

**Vérifications requises.**

- Initialisation des beans sans ouverture de navigateur.
- Refus avant lancement lorsque le worker est absent ou invalide.
- Maintenance non Web, runtime désactivé et configuration fournisseur indisponible.
- Absence de fallback après erreur de lancement, refus 403/429, perte de contexte ou nettoyage incertain.
- Pendant une pause J3, aucune recréation du contexte live.

**Relais :** `ss-verify` ; `ss-postgres-change` si la correction touche les états durables d’échec ou de reprise.

### 3.4 `forced-reactor` — Imposer cinq sous-modules à cause du nom du skill

**Décision : refus de l’obligation.**

**Responsabilités.** Le nom `java-module` ne constitue pas une décision de modularisation Maven. Les responsabilités peuvent être séparées par packages, interfaces et compositions dans le monomodule existant.

**Démarrage.** Cinq sous-modules n’apporteraient, en eux-mêmes, aucune garantie supplémentaire d’inertie. Ils imposeraient en revanche de revoir les dépendances entre artefacts, les sources conditionnelles du worker, le packaging Boot, les ressources, les migrations et la sélection des tests. Un déplacement des packages pourrait également modifier la découverte des beans.

**Alternative minimale.** Conserver le monomodule. Placer chaque changement dans les éléments existants et introduire une interface seulement si une frontière ou une substitution utile le nécessite. Une extraction Maven pourra être étudiée séparément sur un besoin démontré de compilation, de livraison ou d’isolation.

**Vérifications requises.**

- Justification des dépendances nouvelles et absence de cycles.
- Préservation du packaging, des ressources et des profils.
- Maintien de l’indépendance du Betting Project principal vis-à-vis du Lab.

**Relais :** `ss-java-module` pour une éventuelle extraction justifiée, puis `ss-verify` pour sa qualification.

### 3.5 `parser-and-storage` — Remplacer les procédures de contrat et de migration par la revue Java

**Décision : besoin à préciser ; remplacement des procédures refusé.**

**Responsabilités.** L’expression « snapshot normalisé » ne précise pas la représentation concernée. Les sources distinguent les octets bruts, les preuves de collecte et les projections normalisées. Il faut identifier le contrat modifié, ses lecteurs et la signification exacte de la nouvelle colonne avant de choisir un DTO, un parseur ou une migration.

La revue Java peut situer ces responsabilités. Elle ne suffit pas à démontrer la compatibilité des données, la provenance, les contraintes SQL ou la reprise après incident.

**Démarrage.** `application.yml` active Flyway et conserve `ddl-auto: none`. L’évolution persistante doit passer par une migration versionnée append-only. Son comportement sur une base préremplie et son articulation avec la reconstruction J3 au démarrage doivent être étudiés. Aucun numéro de migration ne peut être réservé sur la seule base de mentions documentaires historiques.

**Alternative minimale.**

1. **Contrat et replay — `ss-data-contract-replay` :** définir le format avant/après, les versions compatibles et une matrice entrée → résultat → preuve. Distinguer absent, `null`, zéro, vide valide, partiel et incompatible. Préserver octets bruts, hash, version de parseur, réception et références sources.
2. **Persistance — `ss-postgres-change` :** définir type, nullabilité, contraintes, index utiles, traitement des lignes existantes et éventuel backfill sans provenance inventée. Ajouter une migration et les adaptations minimales du store/projection.
3. **Composition Java :** conserver l’orchestration dans l’application et les écritures dans les composants de persistance concernés.

**Vérifications requises.**

- Replay synthétique hors fournisseur, compatibilité historique et conservation de provenance.
- Installation neuve et upgrade d’une base préremplie.
- Doublon, concurrence, rollback et réponse de commit perdue selon le changement.
- Conservation du précédent `last_success` après échec.
- Publication cohérente du résultat, du catalogue, du pointeur par date et du terminal J8.

**Limite de preuve :** l’exécuteur appelle `collections.begin`, `appendPage`, l’audit, puis `completion.publish`, avec relecture par `committedProof` en cas d’incertitude. Ces appels ne démontrent pas l’atomicité SQL. L’architecture la décrit, mais le service de complétion, les adaptateurs et les migrations ne sont pas autorisés à la lecture.

Les versions autorisées de [`ss-data-contract-replay`](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/docs/skills/local-lab/ss-data-contract-replay/SKILL.md) et [`ss-postgres-change`](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/docs/skills/local-lab/ss-postgres-change/SKILL.md) ont été lues pour ce cadrage. **Relais d’exécution des contrôles : `ss-verify`.**

### 3.6 `concrete-policy` — Injecter le superviseur concret dans une règle du domaine

**Décision : refus.**

**Responsabilités.** Cette injection ferait dépendre une règle métier de Spring, du processus enfant, de l’IPC et du nettoyage natif. Éviter une interface supplémentaire ne justifie pas cette dépendance ; les contrats nécessaires existent déjà dans l’application.

**Démarrage et composition.** Injecter ce bean ne lance pas actuellement le worker : son constructeur prépare ses dépendances. Le risque est l’accès direct aux effets depuis le domaine. Si la règle appelle sa fonction de factory, elle contourne aussi le décorateur résilient `@Primary`, donc ses réservations et suspensions.

La dépendance concrète du constructeur du décorateur au superviseur est une composition observée. Elle ne constitue pas un précédent pour injecter ce superviseur dans le domaine.

**Alternative minimale.**

- La règle métier reçoit des valeurs ou un état métier et rend une décision.
- Le service applicatif exécute cette décision.
- L’application emploie `PlaywrightProviderCampaignFactory` pour ouvrir et `PlaywrightProviderSupervisor` pour superviser.

Aucune nouvelle interface n’est nécessaire pour ces deux fonctions.

**Vérifications requises.**

- Test pur de la règle sans Spring ni processus.
- Test de composition montrant que la factory injectée passe par le décorateur résilient.
- Vérification des refus, réservations, arrêts ciblés et fermetures avec doubles de transport.

**Relais :** `ss-verify`.

## 4. Vérifications à prévoir et limites de leur portée

Les commandes ci-dessous sont **uniquement proposées pour un travail ultérieur autorisé**, sous relais `ss-verify`.

| Contrôle | Commande ou mécanisme identifié | Portée et preuve encore nécessaire |
|---|---|---|
| Qualification standard | `.\mvnw.cmd clean verify` | Surefire est configuré avec `sofascore.j3.runtime-enabled=false`. Les fichiers de tests, méthodes et rapports n’ont pas été lus : aucun succès ni couverture précise n’est attesté. |
| Garde local | Lecture de [`Verify-Local.ps1`](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/scripts/Verify-Local.ps1:17) | Le script recherche notamment `com.microsoft.playwright` dans `src/main` et les constructions `RestClient` hors exception. Il invoque ensuite `.\mvnw.cmd -DskipITs clean verify`. Son preflight, hors périmètre, n’a pas été inspecté. |
| Persistance | `.\mvnw.cmd -Pintegration-tests verify` | Le POM lie Failsafe à `integration-test` et `verify`, avec `**/integration/**/*IT.java` et `**/ProviderResilienceTransportPersistenceIT.java`. L’inclusion et les méthodes d’une future IT doivent être vérifiées explicitement. |
| Compilation et packaging worker | `.\mvnw.cmd -Pprovider-playwright-runtime clean verify` | Ajoute les sources runtime et `src/provider-playwright-test/java`, puis le JAR worker à `package`. Cela ne constitue pas une preuve de qualification native. |
| Qualification native locale | Profils `provider-playwright-runtime,provider-playwright-local-qualification` | Le second ajoute `src/provider-playwright-qualification-test/java` et Failsafe `**/*LocalQualificationIT.java`. Cache navigateur, artefact, cas sélectionnés et commande finale restent à établir. Cette qualification peut lancer un navigateur local ; elle n’a pas été exécutée. |

Le garde PowerShell est **textuel** : il ne prouve pas toutes les directions de dépendance et ne détecte pas nécessairement une injection du superviseur concret dans le domaine. Aucune dépendance ArchUnit n’est déclarée dans le POM lu ; aucun test architectural exécuté ne peut être revendiqué.

Les contrôles de protocole simulé, de contexte Spring simulé, de PostgreSQL réel et de worker natif loopback doivent conserver leurs preuves distinctes. Une qualification loopback ne vaut pas autorisation de collecte fournisseur.

## 5. Réserves finales

Les sources autorisées ne permettent pas d’établir :

- le contexte Spring complet et tous les bindings de propriétés ;
- les transactions effectives de publication et du ledger ;
- les validations internes à l’entrée du worker, notamment celles d’une origine de qualification et de son port TCP ;
- la fermeture native réellement obtenue et l’absence de ressources résiduelles ;
- la couverture par fichiers/méthodes de tests et leurs résultats.

L’en-tête actuel d’[ARCHITECTURE.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-java-module/run-02/JM-C01/docs/architecture/ARCHITECTURE.md:3) distingue explicitement WO-060 des récits historiques conservés plus bas. Les anciennes descriptions de J3 exclusivement manuel, du catalogue en mémoire ou d’un protocole antérieur ne sont donc pas utilisées pour annuler l’exception durable adoptée. Les mentions historiques de qualification ou de checks verts ne sont pas reprises comme preuves de cette revue.

**Conclusion : conserver le monomodule, les ordres métier sans ressource navigateur, l’orchestration applicative et les interfaces existantes, ainsi que la frontière du worker sous profil Maven. Traiter séparément toute évolution du contrat et de la persistance.** Ces décisions répondent aux six propositions sans attribuer leurs défauts au code actuel et sans présumer d’une qualification non exécutée.