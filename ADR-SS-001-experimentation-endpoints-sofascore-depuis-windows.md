# ADR-SS-001 - Expérimentation des endpoints SofaScore depuis Windows

- **Statut :** Accepté pour expérimentation locale contrôlée
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
| Client HTTP | Spring `RestClient` |
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

Sont exclus du MVP : le polling live permanent, les rafraîchissements massifs, les recommandations de paris, les cotes, l'accès au VPS, la prise de pari et toute automatisation de navigateur.

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
- de limiter les conséquences d'un changement de schéma ou d'un blocage.

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

## 7. Conséquences

### 7.1 Conséquences positives

- expérimentation mesurable et reproductible ;
- isolation forte du projet principal ;
- capacité de replay hors ligne ;
- architecture fournisseur remplaçable ;
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

## 10. Références

1. `Projet_Betting_Cadrage_Architecture_v0.5.1.pdf`, 7 août 2026, notamment sections 2.3, 3.1, 8.8, 11, 15.1 et annexe B.
2. Oracle, *Java SE Support Roadmap* : Java 25 est une version LTS ; Java 26 est une version non-LTS.
3. Spring Boot 4.1.0, *System Requirements* : Java 17 minimum, compatibilité jusqu'à Java 26 incluse.
4. Spring Tools, *Previous Versions* : distribution Spring Tools 5.3.0 pour Eclipse 2026-06 (4.40).
5. Eclipse Foundation, *Eclipse IDE 2026-06 (4.40)*.
6. Spring Framework, *REST Clients* : `RestClient` est le client synchrone recommandé ; `RestTemplate` est déprécié depuis Spring Framework 7.
7. Spring Boot 4.1.0, *Task Execution and Scheduling* : prise en charge des threads virtuels via `spring.threads.virtual.enabled=true` avec Java 21+.
8. SofaScore, *Terms & Conditions* et pages officielles relatives aux widgets. Les conditions doivent être revérifiées avant chaque évolution du périmètre.

## 11. Historique

| Version | Date | Évolution |
|---|---|---|
| 1.0 | 2026-08-08 | Décision initiale : expérimentation locale isolée, Java 25 LTS, aucune dépendance de production. |
