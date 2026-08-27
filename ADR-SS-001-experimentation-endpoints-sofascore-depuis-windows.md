# ADR-SS-001 - Expérimentation des endpoints SofaScore depuis Windows

- **Statut :** Accepté pour expérimentation locale contrôlée — amendé le 2026-08-27
- **Version :** 1.3
- **Amendement actif :** Playwright retenu comme transport cible local, manuel et opt-in pour J3, J4, J5 et tout futur jalon nécessitant un appel à un endpoint SofaScore ; FlareSolverr écarté
- **Date :** 2026-08-08
- **Décideur :** Porteur du Betting Project
- **Portée :** projet séparé `betting-sofascore-local-lab`
- **Cible Java :** Java 25 LTS
- **Document associé :** `Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf`
- **Décision de référence qualifiée :** document de cadrage du Betting Project v0.5.1, sections 2.3, 8.8 et 15.1

## 1. Contexte

Le document de cadrage du Betting Project v0.5.1 retient une règle de prudence : le coeur du projet et les composants exécutés sur le VPS ne doivent pas appeler directement des endpoints privés SofaScore. SofaScore y est traité comme une source visuelle facultative, exploitée au moyen d'observations validées, et son indisponibilité ne doit bloquer aucune fonction permanente.

Une expérimentation complémentaire est néanmoins souhaitée afin d'évaluer, depuis le poste Windows local du porteur :

- l'accessibilité réelle des données depuis une adresse résidentielle ;
- la stabilité des structures JSON observées ;
- la richesse et la complétude des données disponibles ;
- la fréquence d'appel supportable sans fonctionnement agressif ;
- la possibilité de normaliser puis d'exporter des données utiles au Betting Project.

Cette expérimentation crée un écart volontaire et borné avec la règle générale du cadrage v0.5.1. Elle ne vaut pas approbation d'un connecteur SofaScore de production.

## 2. Problème à résoudre

Il faut permettre une étude technique réelle de l'accès direct aux données tout en évitant :

- de rendre le Betting Project dépendant de SofaScore ou du poste Windows ;
- d'exposer le VPS aux blocages déjà observés ;
- d'introduire des mécanismes de contournement, d'anti-détection ou de collecte intensive ;
- de mélanger les réponses brutes expérimentales avec les données canoniques de production ;
- de transformer une validation technique locale en décision implicite de mise en production.

## 3. Décision

### 3.1 Projet isolé

Un dépôt Git séparé est créé sous le nom :

```text
betting-sofascore-local-lab
```

Il possède son propre cycle de version, sa propre base locale, ses fixtures, ses migrations et sa documentation. Aucun module du Betting Project principal ne dépend directement de ce dépôt.

### 3.2 Exécution locale uniquement

Le prototype s'exécute directement sous Windows :

- depuis Eclipse ou `mvnw.cmd` ;
- sur l'interface de boucle locale uniquement ;
- sans déploiement sur le VPS ;
- sans exposition de port entrant sur Internet ;
- avec PostgreSQL local dans Docker Desktop.

L'application écoute par défaut sur :

```text
http://127.0.0.1:8087
```

### 3.3 Socle technique

Le socle retenu est le suivant :

| Élément | Décision |
|---|---|
| IDE | Eclipse 2026-06 (4.40.0) |
| Spring Tools | 5.3.0 |
| Java | Java 25 LTS, compilation et exécution |
| Framework | Spring Boot 4.1.0 |
| Build | Maven avec `mvnw` et `mvnw.cmd` versionnés |
| Client HTTP historique | Spring `RestClient`, conservé jusqu'aux migrations dédiées de chaque jalon ; aucun nouvel endpoint fournisseur ni fallback fournisseur ne doit s'appuyer sur lui après la décision 1.3 |
| Transport fournisseur cible | Playwright Java 1.62.0 et Chromium associé, local, manuel, opt-in et intégré uniquement par des Work Orders dédiés pour J3, J4, J5 et les futurs jalons |
| Candidat écarté | FlareSolverr v3.5.0, conservé seulement comme preuve historique de `WO-SS-20260823-011` et interdit comme transport ou fallback fournisseur |
| Interface | Spring MVC + Thymeleaf, rendue côté serveur |
| Base | PostgreSQL local dans Docker Desktop |
| Migrations | Flyway |
| Cache | Caffeine |
| Supervision | Spring Boot Actuator et journalisation structurée |
| Tests | JUnit, tests du client HTTP, Testcontainers PostgreSQL, MockMvc et fixtures JSON |

Java 26 n'est pas retenu comme cible. Le projet utilise Java 25 LTS afin de stabiliser le cycle de support, le runtime, le build et les dépendances.

### 3.4 Périmètre fonctionnel initial

Le MVP est limité au football et aux appels manuels :

1. rechercher les événements d'une date ;
2. afficher les informations principales d'un événement ;
3. charger manuellement des détails et statistiques ;
4. conserver la réponse brute et son empreinte ;
5. normaliser un sous-ensemble documenté de champs ;
6. comparer plusieurs instantanés ;
7. exporter un JSON canonique et versionné.

Sont exclus du MVP : le polling live permanent, les rafraîchissements massifs, les recommandations de paris, les cotes, l'accès au VPS, la prise de pari et toute automatisation permanente de navigateur. Le transport Playwright retenu reste un geste local déclenché explicitement par l'opérateur ; son intégration dans J3, J4, J5 ou un futur jalon exige un Work Order dédié, une liste exacte d'endpoints et le respect du paragraphe 3.6.1.

### 3.5 Politique réseau protectrice

Le connecteur réseau est désactivé par défaut. Son activation exige une action explicite de l'opérateur.

Les règles minimales sont :

- un seul appel simultané ;
- déclenchement manuel par défaut ;
- délai interne minimal de 3 secondes entre deux appels ;
- cache obligatoire avant tout nouvel appel ;
- aucun test Maven standard ne contacte Internet ;
- aucun retry sur `400`, `401` ou `403` ;
- sur `429`, respect de `Retry-After` lorsqu'il est présent, suspension immédiate et absence de retry rapide ;
- sur `5xx`, au maximum un ou deux retries espacés ;
- si une réponse HTML remplace le JSON attendu, arrêt du traitement et signalement d'un probable challenge ;
- si le schéma JSON n'est plus reconnu, conservation du brut et classement en incompatibilité de parseur ;
- interrupteur d'arrêt global accessible depuis l'interface.

Le délai de 3 secondes est une limite interne prudente. Il ne constitue pas un quota publié ou garanti par SofaScore.

Pour chaque parcours fournisseur migré vers Playwright par un Work Order dédié, une politique plus stricte remplace les règles de retry ci-dessus :

- zéro retry sur tout statut, timeout ou erreur ;
- aucun fallback entre transport direct, Playwright, FlareSolverr ou un autre client ;
- `404` est conservable comme résultat ; tout autre non-`2xx` est terminal ;
- `403`, `429`, HTML/challenge ou redirection inattendue entraîne l'arrêt immédiat ;
- concurrence limitée à `1` et délai minimal de `3 s` entre tous les débuts de requête ;
- fenêtre exclusive, sans autre campagne fournisseur du laboratoire ;
- bornes de volume, familles et endpoints fixées explicitement par le Work Order du jalon ;
- toute nouvelle campagne reste précédée d'une action opérateur explicite.

Le banc comparatif historique de `WO-SS-20260823-011` ne lit ni n'alimente le cache métier, car il mesure chaque candidat contre le même manifeste gelé. Cette disposition, son plafond de `156` cibles et ses scénarios S1/S25 ne sont pas généralisés aux parcours métier : ceux-ci conservent leur politique cache-first et leurs propres bornes.

### 3.6 Absence de contournement

Le laboratoire ne doit pas intégrer :

- rotation de proxy ou d'adresse IP ;
- changement automatique d'identité réseau pour contourner un refus ;
- simulation de navigateur destinée à franchir un challenge ;
- réutilisation de cookies, sessions ou jetons interceptés ;
- automatisation permanente du navigateur ;
- attaque, saturation ou multiplication artificielle des requêtes ;
- accès depuis une autre adresse après blocage dans le but d'éviter ce blocage.

Un refus explicite, un challenge ou un blocage entraîne l'arrêt de l'expérimentation jusqu'à décision humaine.

### 3.6.1 Transport Playwright retenu

Playwright Java est retenu comme transport cible des appels aux endpoints SofaScore pour J3, J4, J5 et les futurs jalons qui en ont besoin. Cette orientation ne constitue pas une implémentation générale : chaque parcours doit être introduit par un Work Order dédié, une revue de l'ADR, une allowlist exacte et des opt-ins désactivés par défaut. Jusqu'à ces migrations, les transports existants restent inchangés et ne deviennent pas des fallbacks.

Playwright ne démarre jamais automatiquement : ni au démarrage de l'application, ni pendant les tests standards, ni via scheduler ou polling. Chaque campagne exige une action opérateur explicite sur le poste Windows local. L'implémentation durable d'un transport manuel dans l'application n'est pas une automatisation permanente tant que ces conditions restent vraies.

Le seul geste réseau autorisé est le `GET` initial de l'URI exacte de la cible courante construite par l'adaptateur allowlisté. Toute origine, route, méthode ou redirection différente est bloquée. Playwright ne peut pas :

- attendre, exécuter ou piloter un mécanisme destiné à résoudre un challenge ;
- cliquer, naviguer vers une page d'amorçage ou acquérir préalablement des cookies ;
- modifier le `User-Agent` ou employer une extension ou propriété de furtivité ;
- résoudre un CAPTCHA ;
- configurer un proxy ou changer d'adresse ;
- utiliser un compte, des credentials ou un profil Chrome personnel ;
- injecter des cookies, un `storageState`, un script d'anti-détection ou un état intercepté ;
- relancer une requête après refus ou avec un nouveau contexte.

Un `BrowserContext` non persistant et neuf est créé par campagne. Les cookies et stockages éventuellement créés naturellement pendant cette campagne peuvent seulement exister en mémoire dans ce contexte. Ils ne sont ni lus comme preuve, ni exportés, ni journalisés, ni réinjectés, ni réutilisés entre campagnes. Aucun profil utilisateur, `storageState`, HAR, trace, vidéo, capture ou téléchargement n'est conservé.

Toutes les routes autres que l'URI exacte courante sont interrompues. Les service workers sont bloqués. Popups, téléchargements, WebSockets et sous-requêtes sont interdits. Toute tentative vaut incident terminal.

Le statut, le `Content-Type` strictement utile et les octets sont lus depuis l'objet `Response`. Le DOM, `page.content()`, `Response.text()`, `page_source` et toute reconstruction ou réencodage sont interdits comme preuve de fidélité.

L'arrêt opérateur ferme ou termine le processus enfant propriétaire du contexte, du navigateur et du worker. Aucun état de session ne subsiste. La fermeture cible exclusivement l'arbre de processus de la campagne identifiée, jamais l'ensemble des processus Chromium du poste.

La qualification locale de `WO-SS-20260823-011` établit Playwright à `3/3` sur S1 et `75/75` sur S25 avec fidélité des statuts, types de contenu et octets. Elle ne constitue pas une qualification contre le fournisseur et n'autorise pas la production. Elle fournit la base technique de la décision propriétaire du 27 août 2026.

### 3.6.2 FlareSolverr écarté

L'amendement 1.2 autorise le vrai sidecar FlareSolverr v3.5.0 uniquement pour mesurer le candidat
contre les fixtures déterministes locales de `WO-SS-20260823-011`. L'image est déjà présente dans
Docker Desktop et doit être exécutée avec `--pull=never`, l'index OCI
`sha256:139dfee1c6f89249c8d665d1333a42e8ec74ec0a86bc6bb1c8461e10d3a66a47`, le manifeste
`linux/amd64` `sha256:258523d25e4e07028c3a206f0e03ae807b26a50a201dd320f09a18464ecf86fa`, un port
éphémère lié à `127.0.0.1`, un contexte neuf et aucun journal Docker.

Cette autorisation ne couvre aucun endpoint fournisseur. La version qualifiée appelle son mécanisme
de résolution de challenge pendant `request.get` et ne fournit pas une preuve des octets amont ni
du statut HTTP amont équivalente à l'objet `Response` de Playwright. La phase A locale classe donc
sa fidélité `FAIL` et interdit toute campagne réelle tant qu'une nouvelle décision de gouvernance
n'a pas explicitement traité la résolution de challenge et qu'une nouvelle porte de fidélité n'a
pas été définie puis satisfaite. Un go opérateur conditionnel ne lève pas ces portes techniques et
de gouvernance.

La décision propriétaire du 27 août 2026 clôt cette autorisation comparative. Les digests, mesures et preuves restent conservés pour audit, mais FlareSolverr est écarté comme transport, dépendance, sidecar permanent et fallback du laboratoire. Aucune nouvelle qualification, intégration ou exécution FlareSolverr n'est autorisée sans une nouvelle décision propriétaire et un nouvel amendement de gouvernance.

### 3.7 Catalogue logique d'endpoints

Les chemins HTTP réels ne sont jamais dispersés dans le domaine. Ils sont confinés dans un adaptateur et référencés par des types logiques, par exemple :

```text
SCHEDULED_EVENTS
EVENT_DETAILS
EVENT_STATISTICS
EVENT_INCIDENTS
EVENT_LINEUPS
TOURNAMENT_STANDINGS
TEAM_RECENT_EVENTS
```

Chaque entrée conserve au minimum : le modèle d'URI, la version de parseur, la politique de cache, la fréquence minimale, le statut d'activation et la criticité.

### 3.8 Données, provenance et séparation

Chaque appel conserve :

- fournisseur et endpoint logique ;
- date de demande et date de réception ;
- statut HTTP et type de contenu ;
- latence ;
- payload brut ;
- empreinte SHA-256 ;
- version du parseur ;
- statut de compatibilité du schéma ;
- erreur éventuelle.

Les données brutes et les données normalisées sont stockées séparément.

La provenance minimale est :

```text
source = SOFASCORE
acquisitionMode = DIRECT_LOCAL_ENDPOINT
receivedAt
sourceUpdatedAt
payloadHash
parserVersion
validationStatus
```

Le mode `DIRECT_LOCAL_ENDPOINT` ne doit jamais être enregistré comme une `VisualObservation`.

### 3.9 Intégration future avec le Betting Project

La première intégration éventuelle repose uniquement sur un export fichier JSON versionné. Une phase ultérieure pourra autoriser un push HTTPS sortant depuis Windows vers un point d'import du Betting Project.

Les règles sont :

- aucune connexion entrante vers Windows ;
- aucun cookie, jeton ou paramètre technique SofaScore transmis ;
- les payloads bruts restent locaux ;
- seules les données utiles, normalisées, tracées et éventuellement validées sont exportées ;
- l'indisponibilité du laboratoire n'affecte jamais la chaîne principale ;
- aucune consommation directe du laboratoire par les composants critiques du VPS.

### 3.10 Statut de production

Le résultat de cette décision est classé :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

Toute mise en production, tout polling live, toute augmentation importante du volume ou toute intégration directe dans le coeur du Betting Project exige un nouvel ADR.

## 4. Qualification de la décision du cadrage v0.5.1

Le présent ADR n'annule pas la règle générale du Betting Project. Il introduit une exception limitée :

- le VPS et le coeur du Betting Project continuent de ne pas appeler directement les endpoints SofaScore ;
- le SofaScore Observation Bridge reste une option valide pour les observations visuelles ;
- le laboratoire local peut effectuer des appels directs dans un dépôt et une base isolés ;
- les critères d'acceptation du projet principal relatifs à la non-dépendance SofaScore restent applicables ;
- les résultats du laboratoire ne deviennent pas automatiquement des données de production.

## 5. Raisons de la décision

La décision permet :

- d'obtenir des mesures réelles plutôt que de raisonner uniquement sur des hypothèses ;
- de profiter de la connectivité résidentielle locale sans exposer le VPS ;
- de conserver une architecture réversible et remplaçable ;
- de tester les parseurs hors ligne grâce aux réponses enregistrées ;
- de préparer un contrat d'export indépendant de SofaScore ;
- de limiter les conséquences d'un changement de schéma ou d'un blocage ;
- de retenir un transport capable de reproduire localement les statuts et octets attendus sur S1 et S25 ;
- d'écarter un sidecar qui n'a pas franchi la porte de fidélité S1 malgré plusieurs requalifications reproductibles.

## 6. Alternatives étudiées

### 6.1 Conserver uniquement le SofaScore Observation Bridge

**Avantage :** alignement complet avec le cadrage v0.5.1 et risque réseau faible.

**Limite :** ne permet pas de mesurer directement la stabilité des réponses JSON, la complétude des structures ou le coût d'un enrichissement automatisable.

**Décision :** conservé comme solution visuelle, mais insuffisant pour l'objectif de benchmark technique.

### 6.2 Intégrer directement SofaScore dans le Betting Project sur le VPS

**Avantage :** chaîne technique plus courte.

**Limites :** blocages connus, dépendance critique, risque de panne globale, contradiction avec le cadrage.

**Décision :** rejeté.

### 6.3 Exécuter le laboratoire dans Docker

**Avantage :** packaging reproductible.

**Limites :** débogage réseau plus complexe et bénéfice réduit pendant la phase exploratoire.

**Décision :** application Java exécutée directement sous Windows ; seul PostgreSQL est conteneurisé au démarrage.

### 6.4 Utiliser WebClient et une architecture réactive

**Avantage :** forte concurrence et streaming.

**Limites :** complexité inutile pour un seul appel simultané et un trafic volontairement faible.

**Décision :** `RestClient` retenu.

### 6.5 Utiliser JavaFX

**Avantage :** application de bureau native.

**Limites :** dépendances graphiques et packaging supplémentaires, réutilisation web moindre.

**Décision :** Spring MVC + Thymeleaf retenu pour le MVP ; JavaFX reste une option ultérieure.

### 6.6 Utiliser FlareSolverr comme transport fournisseur

**Avantage :** sidecar HTTP simple à isoler dans Docker et prototype local déjà disponible.

**Limites :** la qualification locale ne fournit pas une fidélité équivalente des statuts et des octets amont ; les requalifications S1 restent à `0/3`, tandis que le cycle de vie Docker et la preuve réseau ajoutent une complexité importante.

**Décision :** rejeté comme transport, dépendance et fallback. Les preuves du comparatif restent historiques et auditables.

### 6.7 Utiliser Playwright comme transport fournisseur local

**Avantage :** accès direct aux objets `Response`, fidélité locale obtenue sur S1 et S25, contrôle fin des routes, du contexte et de l'arbre de processus.

**Limites :** dépendance à un navigateur épinglé, coût mémoire supérieur à un client HTTP simple, cycle de vie et nettoyage à maintenir explicitement.

**Décision :** retenu comme cible pour J3, J4, J5 et les futurs jalons, sous Work Orders dédiés et sans assouplir les garde-fous du présent ADR.

## 7. Conséquences

### 7.1 Conséquences positives

- expérimentation mesurable et reproductible ;
- isolation forte du projet principal ;
- capacité de replay hors ligne ;
- architecture fournisseur remplaçable ;
- transport fournisseur unique et contrôlable pour les migrations à venir ;
- interface locale simple ;
- intégration future par contrat JSON stable ;
- adoption de Java 25 LTS pour un socle durable.

### 7.2 Conséquences négatives et risques acceptés

- l'accès peut cesser sans préavis ;
- les structures JSON peuvent changer ;
- les conditions d'utilisation peuvent restreindre l'automatisation ;
- la source peut bloquer l'adresse locale ;
- des données peuvent être incomplètes ou corrigées tardivement ;
- le laboratoire ajoute un dépôt, une base et une documentation à maintenir ;
- Playwright ajoute un binaire Chromium épinglé, une consommation mémoire et un cycle de vie de processus à maintenir ;
- une validation juridique ou contractuelle n'est pas fournie par cet ADR.

## 8. Critères de validation

L'expérimentation est considérée correctement mise en oeuvre lorsque :

- [ ] l'application compile et s'exécute avec Java 25 ;
- [ ] elle démarre depuis Eclipse et avec `mvnw.cmd` ;
- [ ] elle écoute uniquement sur `127.0.0.1` ;
- [ ] le connecteur SofaScore est désactivé par défaut ;
- [ ] les appels sont manuels et limités à une seule requête simultanée ;
- [ ] un `403`, un `429` ou une réponse HTML inattendue suspend les appels ;
- [ ] aucun test standard ne contacte Internet ;
- [ ] les payloads bruts sont rejouables hors ligne ;
- [ ] une réponse identique ne crée pas deux snapshots ;
- [ ] un changement de schéma produit une incompatibilité explicite ;
- [ ] les données brutes et normalisées restent distinctes ;
- [ ] chaque export possède une version de schéma et une provenance ;
- [ ] aucun cookie, jeton ou secret de session n'est stocké ;
- [ ] l'arrêt du laboratoire n'a aucun effet sur le Betting Project ;
- [ ] aucun mécanisme de contournement n'est présent.
- [ ] Playwright reste absent de tout démarrage automatique, test Maven standard, scheduler et polling ;
- [ ] chaque parcours Playwright est désactivé par défaut et exige une action opérateur explicite ;
- [ ] aucune route autre que la cible exacte n'est contactée par le navigateur ;
- [ ] aucun retry, fallback, proxy, profil persistant ou état injecté n'existe dans le transport ;
- [ ] chaque nouvel endpoint et chaque migration J3/J4/J5 sont couverts par un Work Order, une allowlist et des critères d'acceptation dédiés ;
- [ ] FlareSolverr n'est ni invoqué ni proposé comme fallback fournisseur ;
- [ ] le navigateur, ses contextes et ses processus sont nettoyés après chaque campagne ;
- [ ] aucun canari ni donnée sensible n'apparaît dans les sorties de qualification.

## 9. Déclencheurs de réexamen

Un nouvel ADR est obligatoire si l'un des événements suivants survient :

- déploiement hors du poste Windows local ;
- passage à un polling automatique ou live ;
- augmentation de la concurrence au-delà d'un appel ;
- transmission de payloads bruts au VPS ;
- dépendance d'une analyse standard à la disponibilité de SofaScore ;
- utilisation de cookies, comptes, jetons ou sessions ;
- besoin d'un mécanisme de proxy ;
- changement substantiel des conditions d'utilisation ;
- blocage persistant ou incident de sécurité ;
- décision d'abandon ou de promotion en production.
- exécution automatique, planifiée ou permanente d'un navigateur ;
- ajout d'un transport fournisseur autre que Playwright ou réintroduction de FlareSolverr ;
- modification des bornes de volume, de concurrence ou de la politique sans retry ;
- réutilisation d'un état navigateur entre deux campagnes ;
- ajout d'un mécanisme de challenge, proxy, furtivité ou changement d'identité réseau.

## 10. Références

1. `Projet_Betting_Cadrage_Architecture_v0.5.1.pdf`, 7 août 2026, notamment sections 2.3, 3.1, 8.8, 11, 15.1 et annexe B.
2. Oracle, *Java SE Support Roadmap* : Java 25 est une version LTS ; Java 26 est une version non-LTS.
3. Spring Boot 4.1.0, *System Requirements* : Java 17 minimum, compatibilité jusqu'à Java 26 incluse.
4. Spring Tools, *Previous Versions* : distribution Spring Tools 5.3.0 pour Eclipse 2026-06 (4.40).
5. Eclipse Foundation, *Eclipse IDE 2026-06 (4.40)*.
6. Spring Framework, *REST Clients* : `RestClient` est le client synchrone recommandé ; `RestTemplate` est déprécié depuis Spring Framework 7.
7. Spring Boot 4.1.0, *Task Execution and Scheduling* : prise en charge des threads virtuels via `spring.threads.virtual.enabled=true` avec Java 21+.
8. SofaScore, *Terms & Conditions* et pages officielles relatives aux widgets. Les conditions doivent être revérifiées avant chaque évolution du périmètre.
9. Playwright Java, *Installation*, *Browser contexts*, *Network*, *Response*, *Multithreading* et *Browsers* : <https://playwright.dev/java/docs/intro>.
10. FlareSolverr v3.5.0, *README* : <https://github.com/FlareSolverr/FlareSolverr/blob/v3.5.0/README.md>.
11. FlareSolverr v3.5.0, implémentation `request.get` et résolution de challenge : <https://github.com/FlareSolverr/FlareSolverr/blob/v3.5.0/src/flaresolverr_service.py#L424-L440>.

## 11. Historique

| Version | Date | Évolution |
|---|---|---|
| 1.3 | 2026-08-27 | Déclencheurs d'usage hors WO-011 et d'intégration métier satisfaits par la décision propriétaire : Playwright devient le transport cible local, manuel et opt-in pour J3/J4/J5 et les futurs jalons sous Work Orders dédiés ; FlareSolverr est écarté et ses preuves restent historiques. |
| 1.2 | 2026-08-24 | Vrai sidecar FlareSolverr v3.5.0 autorisé seulement contre les fixtures locales du WO-011 ; appel fournisseur interdit après échec de fidélité et tant que la résolution automatique de challenge n'est pas décidée séparément. |
| 1.1 | 2026-08-23 | Exception locale, manuelle et bornée de qualification Playwright J5 pour `WO-SS-20260823-011` ; aucun contournement ni usage permanent autorisé. |
| 1.0 | 2026-08-08 | Décision initiale : expérimentation locale isolée, Java 25 LTS, aucune dépendance de production. |
