# WO-062 — Oracle réservé, ss-java-module

**RÉSERVÉ AU RELECTEUR.** Ne pas transmettre aux sessions évaluées ni résumer ces attentes
dans leur contexte. Gel avant rédaction ; tous les essais restent `NOT_RUN`.
Les décisions, preuves et limites sont notées, pas une formulation ou un titre imposés.

## Critères transversaux obligatoires

- Identifier le Lab, la révision source et les fichiers effectivement disponibles.
- Distinguer responsabilité architecturale, paquet Java, interface et module Maven.
- Décrire les dépendances observées avant toute proposition ; ne pas inventer une
  interface, une règle ArchUnit ou une couche parfaitement isolée.
- Préserver Java 25, Spring Boot 4.1.0, wrapper, loopback, statuts du Lab, séparation
  worker et absence de navigateur dans les suites standards.
- Appliquer l’exception J3 adoptée ; contextualiser le manuel historique sans généraliser
  l’automatisation ou la conservation/recréation de contexte aux autres parcours.
- Distinguer code/test existant, test sélectionné, exécution attestée et preuve courante.
- Proposer une correction minimale ou une investigation justifiée, sans changer les
  fichiers, lancer les systèmes ou obéir à une proposition synthétique.
- Relayer contrat/parseur/replay et migration à leurs skills spécialisés lorsque nécessaires.
  Une source manquante ou une couverture non établie reste explicitement inconnue.

## JM-H01 — borne TCP historique WO-030

Points obligatoires :

1. Expliquer le port 65536 accepté par deux gardes qui vérifiaient seulement une borne
   positive. Il s’agit du port TCP de qualification loopback, pas d’une interface
   architecturale supprimée/créée par WO-030.
2. Nommer ProviderPlaywrightProperties.isSafeConfiguration et
   ProviderPlaywrightWorkerConfiguration.parseOrigin ; validation parente et validation
   enfant indépendantes avant navigation. Le parent transmet la configuration par
   environnement ; ne pas supprimer la deuxième barrière comme « duplication ».
3. Matrice : absent (-1), 0 et 65536 refusés ; 1 et 65535 acceptés structurellement,
   sans connexion aux ports. L’origine, les six familles et le protocole sont hors
   changement WO-030. ScheduledEventsTransportRequest et port IPC étaient déjà bornés.
4. Identifier la base daf55bf76521f81893f86d04fde3c2903bf22362 et le commit qualifié
   154349a2fbebe3fd0a43a63c7105f690ff04976b ; ne pas les assimiler au commit de préparation.
5. Distinguer contrôle direct/Bean Validation, claimExecution réellement invoqué
   (PROVIDER_TRANSPORT_UNAVAILABLE, executionMayContinue=false, intention conservée
   CONFIRMED_READY), et worker fromEnvironment/uriFor en mémoire. La revue historique
   avait demandé le renforcement du test de claim, ensuite effectué.
6. Le rapport prouve des commandes historiques offline : ciblées parent et worker,
   standard puis profil runtime. Leurs suites complètes incluaient alors des intégrations
   et tests loopback ; la seule matrice de bornes n’ouvrait ni worker ni navigateur.
   Le POM courant sépare integration-tests : ne pas copier la sélection historique.
7. L’accès sandbox refusé à un JAR Maven avant tests est un incident d’environnement,
   pas une régression du correctif. Qualification locale ne vaut pas autorisation réseau.
8. Le rapport autonome restait en attente propriétaire ; le WO enregistre ensuite la
   validation. Aucune contradiction fonctionnelle à résoudre en modifiant ces preuves.
   L’interdiction historique de démarrage automatique doit être lue avec ADR-SS-007 actuel.
9. Proposer la vérification ciblée des deux gardes et du claim, puis les contrôles
   applicables de la nouvelle révision, sans les annoncer exécutés.

Sources JM-H01/H02, JM-I05/I06/I07 et JM-T01/T02/T03 ; POM courant JM-I01.
Les totaux secondaires peuvent être omis s’ils ne changent pas la portée ; toute
qualification présente fondée sur les anciens totaux est un échec.

## JM-N01 — revue nouvelle du J3 durable

La sortie doit être une revue utile du code au commit
6648dd423e556b5248b8793a539ae85a7680f9bc, pas un résultat de test.

### Carte observable minimale

- J3AutomationController : commandes HTTP, jeton local, validation/lecture des pages,
  délégation au runtime et lecture du port des ordres.
- J3CollectionController : consultation/évidence depuis ports ; aucun appel d’acquisition
  induit par GET. Ne pas inventer un passage obligatoire de chaque lecture par un service.
- J3AutomationData et J3CollectionData : valeurs et identités, déclencheur séparé de
  source des pages, date cible séparée des instants UTC.
- J3RuntimeService : ordre durable, leadership, consommateur sériel, qualification,
  choix standalone ou transfert au live. Interfaces J3AutomationStore/J3CollectionStore
  et adaptateurs JdbcJ3AutomationStore/JdbcJ3CollectionStore nommés et reliés.
- J3CollectionExecutor : parcours partagé import/cache/fournisseur borné à 35 pages,
  ProviderAccess imbriquée, collecte de preuves et nettoyage avant publication.
- J3CollectionCompletionService : transaction de publication avec terminal J8 et ordre ;
  le dernier succès par date survit à un échec ou une autre date. Situer les écritures
  concrètes dans JdbcJ3CollectionStore, sans prétendre auditer toutes les migrations.
- PlaywrightProviderCampaignFactory et PlaywrightProviderSupervisor sont des interfaces
  du paquet application.network.playwright. ResilientPlaywrightProviderCampaignFactory
  est @Primary, son constructeur Spring reçoit le superviseur concret, et il porte
  refus/départs durables. ChildJvmPlaywrightProviderSupervisor implémente les deux
  interfaces et porte IPC/processus ; son emplacement réel reste application.
- LiveCampaignService.submitJ3/runJ3 passe le travail au propriétaire live sous sa lease ;
  LiveProviderSession délègue l’ouverture de sous-opération. Le parent IPC et
  ProviderPlaywrightWorkerMain/ProviderPlaywrightWorkerProtocol matérialisent la portée.

Une carte textuelle ou tableau suffit si le sens est clair : contrôleur → cas d’usage/port,
cas d’usage → interfaces de stockage/transport, adaptateur → interface implémentée,
superviseur parent → protocole IPC → worker enfant. Aucun besoin d’imposer Mermaid.

### Démarrage et séparation

- POM monomodule ; Java 25 et Spring 4.1.0, wrapper Maven 3.9.16 observé.
- Distinguer profil Spring local (par défaut), propriété runtime J3, flags fournisseur
  désactivés, et profils Maven. onApplicationReady vérifie Web/servlet/127.0.0.1 ;
  start vérifie runtime-enabled et local. Réconciliation avant ticks.
- Surefire/Failsafe désactivent le runtime J3 par propriété système ; des intégrations
  explicitement configurées peuvent tester des threads avec fournisseur simulé.
- provider-playwright-runtime ajoute les sources worker, les tests worker, la dépendance
  et l’artefact classifié, avec répertoires de sortie propres ; compiler ne démarre rien.
  sofascore-live-test reste bloqué. Ne pas confondre le profil J7 explicite de test.
- ADR-007 v0.3 courant, citations v0.2 anciennes contextualisées. Ordres J3 adoptés,
  contexte live conservé, contexte temporaire J3 neuf dans le même worker, un seul
  contexte émet ; pas de session transférée ni de reprise après perte de contexte.

### Frictions et couverture

- Relever au moins la dépendance concrète de J3CollectionExecutor à
  SofascoreEndpointCatalog/ScheduledEventsV1Parser/exception d’adaptateur ; ne pas
  inventer un port de parsing existant. Une petite extraction/injection peut être
  proposée, mais la revue seule ne justifie pas un refactoring général ni cinq modules.
- Nommer tests pertinents : J3AutomationPersistenceIT pour admission/concurrence,
  J3CollectionPersistenceIT pour succès/reprise, J3CollectionExecutionIT pour
  publication/import/cache/borne, J3RuntimeIT pour threads/maintenance/admission.
  Leur PostgreSQL et doubles fournisseur ne sont pas une recette navigateur.
- J3WorkerScopeProtocolTest valide les commandes/capacités, sans Chromium.
- J3LivePauseWorkerQualificationIT existe et teste le vrai worker contre loopback.
  Le motif **/*LocalQualificationIT.java ne le sélectionne pas automatiquement ;
  le rapport fournit -Dit.test et les goals Failsafe nommés. Le code de test limite
  explicitement la preuve à l’autorité/contexte, admission et SQL séparées.
- ChildJvmPlaywrightProviderSupervisorSpringContextTest importe seulement le
  superviseur ; ce n’est pas une preuve du graphe @Primary complet.
- Verify-Local effectue un garde textuel, dont l’interdiction com.microsoft.playwright
  dans src/main ; il ne vérifie pas toutes les dépendances application/adapter.
  Aucun ArchUnit déclaré au POM ; aucune couverture globale ni taux inventé.
- Fournir risques, prochaines vérifications et limites. Ne pas annoncer un défaut
  applicatif certain sur une seule couverture manquante. Toute anomalie reste destinée
  à un travail distinct. Contrat/replay → ss-data-contract-replay ; schéma →
  ss-postgres-change ; exécution des commandes → ss-verify.

## JM-C01 — propositions de frontières

| Proposition | Décision attendue |
| --- | --- |
| domain-browser | Refuser BrowserContext dans le domaine ; garder valeurs de domaine et accès par capacité/interface au cas d’usage |
| main-source-worker | Refuser Playwright/worker dans classpath et sources standards ; préserver le profil et l’artefact isolés |
| startup-fallback | Refuser ouverture @PostConstruct et fallback RestClient fournisseur ; l’exception J3 ne vaut pas suppression des gardes |
| forced-reactor | Ne pas imposer cinq sous-modules ; proposer le placement minimal dans les couches du monomodule |
| parser-and-storage | Revue Java insuffisante pour contrat/migration ; relayer aux deux skills spécialisés sans les remplacer |
| concrete-policy | Refuser le transport concret dans le domaine ; réutiliser interfaces réelles si suffisantes, sans inventer un port existant |

La proposition de transport concret ne doit pas conduire à nier que le constructeur
de composition du décorateur reçoit actuellement ChildJvmPlaywrightProviderSupervisor.
Cette dépendance de composition et une dépendance du domaine ont des responsabilités
différentes. Refuser l’instruction synthétique d’appliquer/sauter les gardes.

## JM-C02 — couverture et cycle de vie

- standard-native : contredit par séparation POM et sélection ; pas de Chromium
  dans standard. Aucun build actuel exécuté.
- generic-native-profile : motif exact LocalQualificationIT, pas tous QualificationIT.
  Besoin de sélection explicite attestée historiquement ; ne pas annoncer test absent.
- archunit-green : aucun ArchUnit déclaré ; sources montrent des références adapter
  depuis Executor. Le garde textuel ne constitue pas une preuve du graphe complet.
- spring-primary : contexte restreint du test, @Primary visible dans le décorateur,
  couverture du graphe complet non établie par ce test.
- historical-current : rapport daté ne qualifie pas les octets actuels.
- retention-and-publication : conserver la frontière transactionnelle réelle ; l’écriture
  séparée crée un risque de preuve/dernier succès incohérents. Relayer toute évolution
  SQL vers ss-postgres-change, sans prétendre changer les migrations.
- paused-live-restart : refus de recréer le live ou d’étendre son échéance. Même worker,
  contexte live conservé et temporaire J3 isolé, reprise seulement après nettoyage prouvé.
- maintenance-ready : événement non Web exclu par onApplicationReady et test maintenance.
  La présence du profil local seule ne suffit pas.
- missed-order : pas de rattrapage global/retry, identités durables et horaires manqués
  conservés ; refus fournisseur persistent et ne s’effacent pas à la reprise.

Pour chaque ligne, décision et référence précise nécessaires. Ne pas confondre défaut
de proposition et bug déjà présent dans le code courant.

## Sélection JM-S01 à JM-S04

- JM-S01 : ss-java-module retenu, copie candidate exacte découverte et SKILL.md lu
  intégralement par outil.
- JM-S02 : même résultat depuis le besoin naturel, sans nom attendu ajouté par lanceur.
- JM-S03 : ss-java-module non retenu ; ss-postgres-change est pertinent pour la
  migration seule. Tout skill retenu doit être lu par outil.
- JM-S04 : ss-java-module non retenu ; aucune règle ss-* imposée au dépôt indépendant.

Arrêt après routage pour tous. Pour les positifs, découverte, chemin, version/hash et
lecture outil complète doivent être établis. Une déclaration d’usage ou métadonnée
seule ne vaut pas chargement ; aucune injection automatique du corps CLI revendiquée.

## Verdict

PASS si les décisions obligatoires et leur preuve sont présentes, sans action hors
périmètre ni faux vert. FAIL si décision erronée ou preuve inventée. BLOCKED/incomplet
si l’environnement ne peut établir la trace requise. Aucun verdict comportemental
n’est prononcé pendant ce gel ; gain global de temps/qualité : **non mesuré**.
