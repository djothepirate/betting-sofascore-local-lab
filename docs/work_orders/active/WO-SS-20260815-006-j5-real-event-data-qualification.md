# WO-SS-20260815-006 — Qualification réelle bornée des données événement J5

- **Statut :** `IN_DEVELOPMENT`
- **Date :** 2026-08-15
- **Date de démarrage :** 2026-08-15
- **Prérequis fonctionnel :** WO-SS-20260815-005 validé et archivé
- **Base locale :** `5063ac8e` — clôture documentaire J5 hors ligne
- **Jalon :** J5 — extension de qualification réelle
- **Branche :** `codex/j5-real-event-data-qualification`
- **Familles :** `EVENT_STATISTICS`, `EVENT_INCIDENTS`, `EVENT_LINEUPS`
- **Développement et tests hors ligne :** `AUTHORIZED`
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Campagne humaine future :** `PENDING_TECHNICAL_READINESS`
- **Modification directe de `.env` par l'agent :** `NOT_AUTHORIZED`
- **Polling, planification ou retry :** `NOT_AUTHORIZED`
- **Déploiement VPS :** `NOT_AUTHORIZED`

## 1. Objectif

Ajouter une voie de qualification réelle J5, distincte des parcours J3 et J4, afin qu'un opérateur
puisse sélectionner une identité canonique J4 déjà persistée, préparer sans réseau une campagne,
confirmer exactement cette identité, puis effectuer au maximum trois lectures fournisseur
séquentielles : statistiques, incidents et compositions.

Chaque réponse doit être persistée brute avant toute interprétation. Une réponse compatible est
ensuite normalisée dans les tables append-only J5 avec une provenance `PROVIDER_SNAPSHOT`. Le
premier incident arrête la campagne, verrouille le contrôle et interdit tout appel restant.

La réalisation de ce Work Order n'exécute aucune requête réelle. La compatibilité actuelle des
trois réponses fournisseur demeure non qualifiée jusqu'à une campagne humaine ultérieure.

## 2. Contexte opérateur reçu le 2026-08-15

Le propriétaire a exécuté un second parcours J4 paramétrable avec la configuration déclarée
suivante :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com/
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS
```

Le fichier `.env` n'a pas été lu par l'agent. Ces valeurs sont une déclaration opérateur et non une
copie du fichier local. Elles ne contiennent aucun secret.

Les huit captures fournies, non versionnées, permettent de consigner les observations minimisées
suivantes :

- l'événement `16412917` a reçu une quatrième observation J4, `snapshot:25`, au statut
  `interrupted`, après une version `inprogress` ;
- l'événement `16391135`, Bolton Wanderers — Preston North End, a été acquis par exactement un
  appel J4, `snapshot:26`, HTTP compatible et parsé par `event-details-v2` ;
- le snapshot 26 contient `10068` octets et porte le SHA-256
  `e860c867fe1d51f6b59777cdb2f23098ee10b36e4a7e4a16f9bc7ca988673723` ;
- l'identité canonique de `16391135` est `f7f7a708-0200-38da-8f3f-a00884761072` et son statut
  observé est `inprogress` ;
- la page J5 de cette identité n'effectue aucun repli réseau et montre trois absences locales ;
- l'import du corpus synthétique J5 sur cette identité réelle est refusé avec
  `EVENT_ID_MISMATCH` ;
- aucune donnée synthétique de `900001` n'est donc rattachée à `16391135`.

La phrase de confirmation ponctuelle visible dans une capture n'est ni reproduite ni conservée.
Le rapport prérequis détaillé est
`docs/validation/J5-REAL-EVENT-DATA-PREREQUISITE-OBSERVATION-20260815.md`.

## 3. Revue de l'ADR-SS-001

Les sections 3.4 à 3.8, 3.10 et 9 de l'ADR-SS-001 ont été relues. Le parcours prévu reste :

- local à Windows et lié à `127.0.0.1` ;
- manuel et synchrone ;
- limité à une seule requête simultanée ;
- borné à trois chemins construits localement ;
- sans compte, cookie, jeton, proxy, redirection ou simulation de navigateur ;
- sans polling, tâche planifiée, cache implicite ou retry ;
- avec trois secondes au minimum entre deux tentatives ;
- avec brut local avant parsing et données normalisées séparées ;
- arrêté et verrouillé au premier incident ;
- sans dépendance de production ni transmission au VPS.

Comme pour le rappel manuel J4 qualifié, l'absence de cache est volontaire : une campagne confirmée
constitue une acquisition réelle unique de chaque famille. Aucun second appel à une même famille
n'est possible dans le processus courant. Aucun déclencheur de nouvel ADR n'est atteint.

```text
ADR_SS_001_J5_REAL_REVIEW=COMPATIBLE_NO_CHANGE_REQUIRED
ADR_SS_001_MODIFICATION_REQUIRED=NO
```

## 4. Configuration future déterminée

Le futur test réel utilisera exclusivement les valeurs réseau suivantes, après réussite de toutes
les validations hors ligne et décision humaine de démarrer la campagne :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
```

L'opt-in J5 sera ajouté avec la valeur par défaut `false`. Les chemins J3, J4 et J5 seront
mutuellement exclusifs. L'origine avec un slash terminal pourra être normalisée comme origine
racine, mais la forme recommandée reste sans slash.

Après succès, incident ou abandon, et avant tout redémarrage ordinaire :

```properties
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=
SOFASCORE_ALLOWED_ENDPOINTS=
```

Le propriétaire reste responsable de l'édition locale et du reverrouillage de `.env`. Aucune
valeur de mot de passe PostgreSQL ne doit apparaître dans les preuves.

## 5. Campagne réelle autorisable après readiness

### 5.1 Préparation

- la campagne est initiée depuis la page J5 d'une identité canonique existante ;
- aucun identifiant libre n'est envoyé par l'action finale ;
- la préparation ne contacte pas le fournisseur ;
- l'intention contient l'UUID canonique, l'identifiant fournisseur et les trois familles ;
- la phrase exacte contient l'identifiant fournisseur, les trois familles et un code aléatoire ;
- la confirmation expire après cinq minutes et exige un acquittement explicite.

### 5.2 Exécution

Une confirmation valide autorise exactement, dans cet ordre :

1. `GET /api/v1/event/<event_id>/statistics` ;
2. `GET /api/v1/event/<event_id>/incidents` ;
3. `GET /api/v1/event/<event_id>/lineups`.

La concurrence reste égale à un et un délai d'au moins trois secondes sépare deux tentatives. Une
campagne réussie passe à `COMPLETED_LOCKED` et ne peut pas être rejouée dans le même processus.

### 5.3 Persistance et parsing

- les octets exacts sont enregistrés dans `provider_snapshot` avant parsing ;
- la clé brute contient le type logique et l'identifiant d'événement ;
- les parseurs fournisseur sont versionnés `event-statistics-v2`, `event-incidents-v2` et
  `event-lineups-v2`, distincts des contrats synthétiques V1 ;
- l'identifiant attendu vient du claim et de la requête, car les enveloppes de famille peuvent ne
  pas répéter l'identifiant d'événement ;
- un résultat compatible produit une observation V7/V8 liée au snapshot brut ;
- une réponse identique est dédupliquée sans réécriture ;
- aucune donnée partielle n'est persistée après une incompatibilité structurelle ;
- les absences métier compatibles restent `PARTIAL` ou `EMPTY_VALID` selon la famille.

### 5.4 Arrêt terminal

La campagne s'arrête avant tout appel restant sur :

- arrêt opérateur ;
- échec du délai minimal ;
- timeout ou erreur d'entrée/sortie ;
- payload trop volumineux ou contenu sensible ;
- statut HTTP hors `2xx`, notamment `400`, `401`, `403`, `404`, `429` ou `5xx` ;
- contenu non JSON ou HTML inattendu ;
- JSON ambigu, tronqué ou incompatible ;
- incohérence entre identité canonique et claim ;
- erreur de persistance brute, normalisée ou de classification.

Il n'existe aucun retry automatique. Un échec ou un arrêt exige un redémarrage avec configuration
reverrouillée avant toute nouvelle décision.

## 6. Périmètre inclus

- nouvel opt-in J5 sûr par défaut ;
- politique de configuration exclusive et origine exacte ;
- contrôle en mémoire à confirmation unique et verrou terminal ;
- requêtes exactes et transport `RestClient` sans proxy ni redirection ;
- réponse bornée par `RawPayloadEvidence` ;
- trois parseurs fournisseur V2 et fixtures de forme créées de zéro ;
- migration Flyway V8 append-only autorisant les versions V2 sans modifier V7 ;
- persistance normalisée liée à `PROVIDER_SNAPSHOT` ;
- résultat minimisé affichant endpoint, snapshot, taille, hash, statut, complétude et insertion ;
- arrêt global J5 ;
- tests unitaires, MVC et PostgreSQL/Testcontainers exclusivement hors ligne ;
- architecture, runbook, readiness, README et changelog.

## 7. Hors périmètre

- tout appel réel pendant le développement ou Maven ;
- un endpoint autre que les trois familles ;
- une URL, un chemin ou un identifiant fourni librement à l'action finale ;
- plusieurs événements dans une campagne ;
- rappel ou rafraîchissement J5 dans le même processus ;
- polling, planification, boucle live, cache implicite ou retry ;
- proxy, cookie, jeton, compte, en-tête personnalisé ou navigateur automatisé ;
- réutilisation du transport J4 pour les familles J5 ;
- modification d'une migration V1 à V7 déjà partagée ;
- copie d'un payload réel dans Git, un rapport, les logs ou l'interface ;
- activation de `TOURNAMENT_STANDINGS` ;
- export J7, VPS, production ou intégration au Betting Project principal ;
- modification de l'ADR-SS-001 ou des PDF de référence.

## 8. Invariants

1. `server.address=127.0.0.1`.
2. Tous les opt-ins fournisseur valent `false` par défaut.
3. Une seule voie J3, J4 ou J5 peut être active.
4. Le catalogue reste `callable=false` et sans URI réelle.
5. `ConnectorGate` et le profil Maven `sofascore-live-test` restent bloquants.
6. Maven ne contacte jamais SofaScore.
7. Trois tentatives fournisseur au maximum après une confirmation humaine.
8. Un seul appel simultané et délai minimal de trois secondes.
9. Aucun retry, polling, planification ou rafraîchissement implicite.
10. Persistance brute avant parsing ; sources brutes et normalisées séparées.
11. Chaque normalisation conserve identité, snapshot, hash, parseur et heure de réception.
12. Aucune donnée partielle après incompatibilité de schéma.
13. Le premier incident bloque les appels restants.
14. Les observations normalisées restent append-only.
15. Aucun payload brut, cookie, jeton, secret ou valeur `.env` sensible dans les sorties.
16. `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
    `NO_CRITICAL_DEPENDENCY` restent effectifs.

## 9. Matrice de tests minimale

| Scénario hors ligne | Résultat attendu |
|---|---|
| configuration par défaut | voie J5 bloquée |
| opt-in J5 sans les trois familles exactes | démarrage ou politique bloqué |
| opt-in J5 avec J3 ou J4 | configuration refusée |
| origine différente, port, utilisateur, query ou fragment | bloqué |
| préparation d'une identité absente | refus sans transport |
| phrase, UUID, acquittement ou TTL invalide | refus sans transport |
| exécution nominale simulée | trois transports dans l'ordre, délai appliqué |
| réponse `403` sur statistiques | un appel, brut classé, deux familles non appelées |
| réponse `429` sur incidents | deux appels, troisième famille non appelée |
| HTML ou schéma incompatible | brut conservé, aucune normalisation partielle de la famille |
| événement du claim incohérent | refus avant transport ou normalisation |
| réponse nominale répétée en persistance | déduplication brute et normalisée |
| campagne réussie | `COMPLETED_LOCKED`, trois familles consultables |
| second déclenchement même processus | refus terminal |
| arrêt global | appels suivants refusés |
| suite Maven | zéro appel Internet |
| Flyway V1 → V8 | migrations et append-only valides |

## 10. Critères d'acceptation

- [x] captures opérateur antérieures minimisées et frontière J4/J5 qualifiée ;
- [x] revue ADR-SS-001 consignée sans modification ;
- [x] futur paramétrage `.env` déterminé sans lire ou modifier `.env` ;
- [ ] Work Order et architecture relus dans le diff ;
- [ ] propriétés sûres et sélection exclusive implémentées ;
- [ ] contrôle de préparation, confirmation et verrou terminal implémenté ;
- [ ] trois transports exacts et bornés implémentés ;
- [ ] brut persisté avant parsing ;
- [ ] parseurs V2 et complétude qualifiés hors ligne ;
- [ ] migration V8 et provenance `PROVIDER_SNAPSHOT` qualifiées ;
- [ ] interface et résultat minimisé qualifiés ;
- [ ] arrêt au premier incident sans retry prouvé ;
- [ ] `mvnw.cmd clean verify` réussi ;
- [ ] `mvnw.cmd -Pintegration-tests verify` réussi ;
- [ ] readiness humaine publiée ;
- [ ] configuration réelle toujours non exécutée pendant l'implémentation.

## 11. Unités de livraison prévues

1. `docs: start guarded J5 real qualification work order`
2. `feat: add guarded J5 provider contracts and parsers`
3. `feat: execute one confirmed J5 real campaign`
4. `test: qualify J5 real path offline`
5. `docs: publish J5 real campaign readiness`

## 12. État initial

```text
WO_ID=WO-SS-20260815-006
WO_STATUS=IN_DEVELOPMENT
BASE_COMMIT=5063ac8e
BRANCH=codex/j5-real-event-data-qualification
J5_OFFLINE_STATUS=VALIDATED
J5_REAL_IMPLEMENTATION_STATUS=NOT_STARTED
J5_REAL_PROVIDER_CALLS=0
J5_REAL_CAMPAIGN_STATUS=NOT_RUN
J5_REAL_ENV_CONFIGURATION=DEFINED_NOT_APPLIED
J5_REAL_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_APPLICATION_TRANSPORT=NOT_IMPLEMENTED
J5_REAL_CAN_BE_EXECUTED=NO
```
