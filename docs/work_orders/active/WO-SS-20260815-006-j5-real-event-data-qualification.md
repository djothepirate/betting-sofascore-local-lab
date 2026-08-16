# WO-SS-20260815-006 — Qualification réelle bornée des données événement J5

- **Statut :** `REARM_REAL_PASS_V5_BENCH_CARD_CORRECTION_VALIDATED_OFFLINE`
- **Date :** 2026-08-15
- **Date de démarrage :** 2026-08-15
- **Prérequis fonctionnel :** WO-SS-20260815-005 validé et archivé
- **Base locale :** `5063ac8e` — clôture documentaire J5 hors ligne
- **Jalon :** J5 — extension de qualification réelle
- **Branche :** `codex/j5-real-event-data-qualification`
- **Familles :** `EVENT_STATISTICS`, `EVENT_INCIDENTS`, `EVENT_LINEUPS`
- **Développement et tests hors ligne :** `AUTHORIZED`
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Première campagne humaine réelle :** `EXECUTED_ONE_CALL_HTTP_404_MISCLASSIFIED`
- **Amendement correctif demandé par le propriétaire :** `AUTHORIZED`
- **Campagne corrective réelle V3 :** `EXECUTED_INCIDENTS_PASS_LINEUPS_NOT_ATTEMPTED`
- **Campagne corrective réelle V4 :** `EXECUTED_THREE_CALLS_INCIDENTS_AND_LINEUPS_PASS`
- **Correction du réarmement après succès :** `PASS_REAL_SECOND_CAMPAIGN_STARTED`
- **Seconde campagne après réarmement :** `STATISTICS_PASS_INCIDENTS_SCHEMA_INCOMPATIBLE`
- **Correction ciblée du carton de banc V5 :** `IMPLEMENTED_VALIDATED_OFFLINE_RETEST_REQUIRED`
- **Modification directe de `.env` par l'agent :** `NOT_AUTHORIZED`
- **Polling, planification ou retry :** `NOT_AUTHORIZED`
- **Déploiement VPS :** `NOT_AUTHORIZED`

## 1. Objectif

Ajouter une voie de qualification réelle J5, distincte des parcours J3 et J4, afin qu'un opérateur
puisse sélectionner une identité canonique J4 déjà persistée, préparer sans réseau une campagne,
confirmer exactement cette identité, puis effectuer au maximum trois lectures fournisseur
séquentielles : statistiques, incidents et compositions.

Chaque réponse doit être persistée brute avant toute interprétation. Une réponse compatible est
ensuite normalisée dans les tables append-only J5 avec une provenance `PROVIDER_SNAPSHOT`. Un HTTP
`404` sur une famille facultative produit une indisponibilité explicite et n'est pas un incident ;
le premier incident réel arrête la campagne, verrouille le contrôle et interdit tout appel restant.

La réalisation logicielle de ce Work Order n'a exécuté aucune requête réelle. Les campagnes
  humaines et leurs corrections successives sont consignées aux sections 15 à 20. Le retest
  V4 a terminé les trois appels et qualifié incidents et compositions, puis révélé que le verrou de
  succès empêchait à tort la préparation d'une campagne distincte dans la même instance. Le présent
  amendement a corrigé ce cycle de vie sans exécuter de requête réelle. Le retest humain du
  réarmement a démarré une seconde campagne dans la même instance ; celle-ci a parsé les
  statistiques, puis révélé une forme réelle de carton de banc que V5 traite de façon strictement
  bornée. La correction V5 est validée hors ligne et attend son retest humain.

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
- arrêté et verrouillé au premier incident réel, un HTTP `404` de famille étant une disponibilité
  négative observée et non un incident ;
- sans dépendance de production ni transmission au VPS.

Comme pour le rappel manuel J4 qualifié, l'absence de cache est volontaire : une campagne confirmée
constitue une acquisition réelle unique de chaque famille. Aucun second appel à une même famille
n'est possible dans un claim. Après un succès uniquement, une nouvelle campagne exige une
préparation explicite, un nouvel identifiant de requête, une nouvelle phrase et un nouvel
acquittement ; elle reste exclusive de toute autre campagne active. Aucun déclencheur de nouvel
ADR n'est atteint.

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

L'opt-in J5 est défini avec la valeur par défaut `false`. Les chemins J3, J4 et J5 sont
mutuellement exclusifs. L'origine avec un slash terminal pourra être normalisée comme origine
racine, mais la forme recommandée reste sans slash.

Après la dernière campagne de la session, ou immédiatement après incident ou abandon, et avant
tout redémarrage ordinaire :

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
campagne réussie passe à `COMPLETED_LOCKED` : son claim ne peut pas être rejoué, mais une nouvelle
préparation locale explicite peut créer une campagne distincte dans le même processus. Elle remet
les endpoints terminés et le code terminal à zéro, produit une nouvelle phrase et ne déclenche
aucun transport avant une nouvelle confirmation.

### 5.3 Persistance et parsing

- les octets exacts sont enregistrés dans `provider_snapshot` avant parsing ;
- la clé brute contient le type logique et l'identifiant d'événement ;
- les parseurs fournisseur courants sont versionnés `event-statistics-v2`, `event-incidents-v5` et
  `event-lineups-v2`, distincts des contrats synthétiques V1 ;
- l'identifiant attendu vient du claim et de la requête, car les enveloppes de famille peuvent ne
  pas répéter l'identifiant d'événement ;
- un HTTP `404` ne déclenche aucun parsing du corps : il produit un snapshot
  `ENDPOINT_UNAVAILABLE`, une observation `UNAVAILABLE · N/A` et la poursuite sans retry ;
- un résultat compatible produit une observation V7/V8/V9/V10/V11/V12 liée au snapshot brut ;
- une réponse identique est dédupliquée sans réécriture ; si elle résout une preuve historique
  déjà classée, la nouvelle observation porte le résultat du parseur courant sans reclasser le
  snapshot ;
- aucune donnée partielle n'est persistée après une incompatibilité structurelle ;
- les absences métier compatibles restent `PARTIAL` ou `EMPTY_VALID` selon la famille ;
- `UNAVAILABLE` reste distinct de `EMPTY_VALID` et utilise un normaliseur
  `event-*-unavailable-v1` explicite.

### 5.4 Arrêt terminal

La campagne s'arrête avant tout appel restant sur :

- arrêt opérateur ;
- échec du délai minimal ;
- timeout ou erreur d'entrée/sortie ;
- payload trop volumineux ou contenu sensible ;
- statut HTTP hors `2xx`, notamment `400`, `401`, `403`, `408`, `429` ou `5xx`, à l'exception du
  seul HTTP `404` des trois endpoints exacts J5 ;
- contenu non JSON ou HTML inattendu sur une réponse `2xx` ;
- JSON ambigu, tronqué ou incompatible ;
- incohérence entre identité canonique et claim ;
- erreur de persistance brute, normalisée ou de classification.

Il n'existe aucun retry automatique. Un échec ou un arrêt exige un redémarrage avec configuration
reverrouillée avant toute nouvelle décision.

## 6. Périmètre inclus

- nouvel opt-in J5 sûr par défaut ;
- politique de configuration exclusive et origine exacte ;
- contrôle en mémoire à confirmation unique, verrou anti-rejeu après succès et réarmement local
  explicite d'une campagne distincte ;
- requêtes exactes et transport `RestClient` sans proxy ni redirection ;
- réponse bornée par `RawPayloadEvidence` ;
- parseurs fournisseur versionnés et fixtures de forme créées de zéro ;
- migration Flyway V8 append-only autorisant les versions V2 sans modifier V7 ;
- migration Flyway V9 append-only ajoutant l'indisponibilité explicite et corrigeant la
  classification des anciens snapshots J5 HTTP `404` sans modifier leur brut ;
- migration Flyway V10 append-only autorisant `event-incidents-v3`, sans reclasser ni réécrire les
  snapshots et observations historiques V2 ;
- migration Flyway V11 append-only autorisant `event-incidents-v4` et conservant les identités
  `playerIn` / `playerOut` sans réécrire les observations historiques ;
- migration Flyway V12 append-only autorisant `event-incidents-v5`, conservant la classe et le
  motif facultatifs d'un carton et laissant intactes toutes les observations historiques ;
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
- rejeu automatique, réutilisation d'un ancien claim ou campagnes parallèles dans le même
  processus ;
- polling, planification, boucle live, cache implicite ou retry ;
- proxy, cookie, jeton, compte, en-tête personnalisé ou navigateur automatisé ;
- réutilisation du transport J4 pour les familles J5 ;
- modification d'une migration V1 à V8 déjà partagée ;
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
13. Un HTTP `404` de famille est conservé puis la séquence continue sans retry ; le premier
    incident réel bloque les appels restants.
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
| HTTP `404` sur statistiques | observation `UNAVAILABLE`, puis incidents et compositions appelés une fois |
| marqueur incidents `period` avec `addedTime=999` | brut conservé, avertissement explicite, temps additionnel normalisé absent, compositions appelées |
| incident métier avec `addedTime=999` | `SCHEMA_INCOMPATIBLE`, aucune normalisation partielle |
| réponse `403` sur statistiques | un appel, brut classé, deux familles non appelées |
| réponse `429` sur incidents | deux appels, troisième famille non appelée |
| HTML ou schéma incompatible | brut conservé, aucune normalisation partielle de la famille |
| événement du claim incohérent | refus avant transport ou normalisation |
| réponse nominale répétée en persistance | déduplication brute et normalisée |
| réponse incidents dédupliquée vers un snapshot V2 terminal | nouvelle observation V4, classification historique inchangée, compositions appelées |
| remplacement avec `playerIn` et `playerOut` | deux identités conservées et affichées pour chaque substitution |
| remplacement avec une identité absente | observation `PARTIAL`, chemin manquant, aucun joueur inventé |
| carton de banc avec `time=-5` et `benchTime=58` | brut intact, minute normalisée 58, classe et motif conservés |
| temps négatif sur un incident autre que `card` | `SCHEMA_INCOMPATIBLE`, aucune règle extrapolée |
| campagne réussie | `COMPLETED_LOCKED`, ancien claim non rejouable, trois familles consultables |
| préparation explicite après succès | nouveau requestId et nouvelle phrase, endpoints remis à zéro, aucun transport |
| ancienne phrase ou ancien claim après réarmement | refus par identité de requête, aucune exécution |
| préparation pendant une campagne active | refus `ACTIVE_CAMPAIGN_EXISTS` |
| préparation après échec, arrêt ou expiration | refus terminal jusqu'au redémarrage |
| arrêt global | appels suivants refusés |
| suite Maven | zéro appel Internet |
| upgrade V8 contenant un HTTP `404` J5 mal classé | V9 reclasse le snapshot sans modifier son brut |
| upgrade V9 contenant des observations incidents V1/V2 | V10 autorise V3 sans réécrire l'historique |
| upgrade V10 contenant des observations V1/V2/V3 | V11 autorise V4 sans réécrire l'historique |
| upgrade V11 contenant des observations V1/V2/V3/V4 | V12 autorise V5 sans réécrire l'historique |
| Flyway V1 → V12 | migrations et append-only valides |

## 10. Critères d'acceptation

- [x] captures opérateur antérieures minimisées et frontière J4/J5 qualifiée ;
- [x] revue ADR-SS-001 consignée sans modification ;
- [x] futur paramétrage `.env` déterminé sans lire ou modifier `.env` ;
- [x] Work Order et architecture relus dans le diff ;
- [x] propriétés sûres et sélection exclusive implémentées ;
- [x] contrôle de préparation, confirmation et verrou terminal implémenté ;
- [x] trois transports exacts et bornés implémentés ;
- [x] brut persisté avant parsing ;
- [x] parseurs fournisseur versionnés et complétude qualifiés hors ligne ;
- [x] migrations V8/V9/V10/V11/V12 et provenance `PROVIDER_SNAPSHOT` qualifiées ;
- [x] interface et résultat minimisé qualifiés ;
- [x] poursuite après HTTP `404` sans retry et arrêt au premier incident réel prouvés ;
- [x] `mvnw.cmd clean verify` réussi ;
- [x] `mvnw.cmd -Pintegration-tests verify` réussi ;
- [x] readiness humaine publiée ;
- [x] configuration réelle toujours non exécutée pendant l'implémentation ;
- [x] comportement historique de la première campagne et snapshot 30 consignés sans payload brut ;
- [x] cause racine du verrouillage HTTP `404` identifiée ;
- [x] distinction `UNAVAILABLE` / `EMPTY_VALID` / `TRANSPORT_ERROR` implémentée et testée ;
- [x] campagnes humaines correctives exécutées après application de V9 ;
- [x] cause du faux positif incidents V2 identifiée sur deux snapshots réels indépendants ;
- [x] `event-incidents-v3` et l'upgrade V9 → V10 validés hors ligne ;
- [x] campagne humaine corrective exécutée après application de V10 ;
- [x] incidents V3 réels visibles avec `20/20` signaux sur le snapshot dédupliqué 32 ;
- [x] cause de `RAW_CLASSIFICATION_ERROR` identifiée avant le troisième appel ;
- [x] déduplication historique, parseur V4 et joueurs entrant/sortant validés hors ligne ;
- [x] campagne humaine corrective exécutée après application de V11 ;
- [x] incidents V4 réels avec joueurs entrant/sortant et compositions V2 réelles qualifiés ;
- [x] cause du verrou global après succès identifiée et réarmement explicite validé hors ligne ;
- [x] nouvelle campagne préparée et exécutée dans la même instance après un succès, avec nouveau claim ;
- [x] cause du blocage de la seconde campagne localisée sur le carton de banc du snapshot 60 ;
- [x] règle V5 étroite et migration V12 validées hors ligne, sans extrapolation aux autres incidents ;
- [ ] campagne humaine corrective exécutée avec `event-incidents-v5` ;
- [ ] configuration locale reverrouillée et arrêt final confirmés après le retest.

## 11. Unités de livraison prévues

1. `docs: start guarded J5 real qualification work order`
2. `feat: add guarded J5 provider contracts and parsers`
3. `feat: execute one confirmed J5 real campaign`
4. `test: qualify J5 real path offline`
5. `docs: publish J5 real campaign readiness`
6. `test: isolate historical bindings from armed J5 configuration`
7. `docs: record the failed-locked J5 real campaign`
8. `fix: continue J5 after an unavailable optional family`
9. `docs: record the J5 HTTP 404 policy correction`
10. `fix: accept provider incident period markers`
11. `docs: record the J5 incident parser correction`
12. `fix: preserve reparsed J5 evidence and substitution players`
13. `docs: record the J5 deduplication and substitution correction`
14. `fix: rearm J5 after a completed campaign`
15. `docs: record J5 V4 qualification and rearm correction`

## 12. Readiness technique hors ligne

```text
STANDARD_TESTS=278
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
INTEGRATION_TESTS=25
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
FLYWAY_MIGRATIONS=12
POSTGRESQL=18.4_TESTCONTAINERS
SOFASCORE_PROVIDER_CALLS=0
J5_REAL_TECHNICAL_READINESS=PASS
```

La preuve détaillée est conservée dans
`docs/validation/J5-REAL-EVENT-DATA-TECHNICAL-READINESS-20260815.md`. Elle qualifie le code et les
garde-fous hors ligne ; elle ne qualifie pas les trois formes de réponse nominales actuelles du
fournisseur. La première campagne humaine a révélé une indisponibilité HTTP `404` mal classée. Le
correctif est validé hors ligne ; le Work Order reste actif jusqu'au retest humain, au
reverrouillage de `.env` et à l'arrêt final.

## 13. État de préparation

```text
WO_ID=WO-SS-20260815-006
WO_STATUS=REARM_REAL_PASS_V5_BENCH_CARD_CORRECTION_VALIDATED_OFFLINE
BASE_COMMIT=5063ac8e
BRANCH=codex/j5-real-event-data-qualification
J5_OFFLINE_STATUS=VALIDATED
J5_REAL_IMPLEMENTATION_STATUS=PASS
J5_REAL_PROVIDER_CALLS_LAST_SUCCESSFUL_CAMPAIGN=3
J5_REAL_FIRST_CAMPAIGN_STATUS=HTTP_404_MISCLASSIFIED_AND_LOCKED
J5_REAL_HTTP_404_POLICY=ENDPOINT_UNAVAILABLE_CONTINUE_NO_RETRY
J5_REAL_V4_CORRECTIVE_RETEST=PASS_THREE_CALLS
J5_REAL_REARM_RETEST=PASS_SECOND_CAMPAIGN_STARTED
J5_REAL_SECOND_CAMPAIGN=STATISTICS_PASS_INCIDENTS_SCHEMA_INCOMPATIBLE_LINEUPS_NOT_ATTEMPTED
J5_REAL_SECOND_CAMPAIGN_PROVIDER_CALLS=2
J5_REAL_BENCH_CARD_CORRECTION=PASS_OFFLINE_RETEST_REQUIRED
J5_REAL_ENV_CONFIGURATION=APPLIED_FOR_REAL_CAMPAIGN_RELOCK_PENDING
J5_REAL_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_APPLICATION_TRANSPORT=IMPLEMENTED_GUARDED_DEFAULT_OFF
J5_REAL_CAN_BE_EXECUTED=PENDING_V5_RETEST_AFTER_RESTART_AND_LOCAL_CONFIGURATION
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

## 14. Précontrôle opérateur avec J5 armé

Le 2026-08-15, l'opérateur a exécuté `mvnw.cmd clean verify` avec la configuration locale J5
armée, avant toute préparation ou confirmation Web. Les 257 tests ont été exécutés, mais trois
scénarios historiques de `SofascorePropertiesTest` ont échoué au démarrage parce qu'ils
héritaient de l'opt-in J5 tout en activant leur propre voie J3 ou J4. La contrainte d'exclusivité a
donc joué son rôle ; aucun endpoint fournisseur n'a été appelé.

Le correctif fixe explicitement J5 à `false` dans chacun de ces trois mini-contextes et en vérifie
la valeur. Le test ciblé puis la commande complète ont été rejoués avec la configuration J5
toujours armée :

```text
TARGETED_CONFIGURATION_TESTS=9
TARGETED_FAILURES=0
STANDARD_TESTS=257
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
BUILD_RESULT=SUCCESS
SOFASCORE_PROVIDER_CALLS=0
PRECHECK_REAL_CAMPAIGN_STATUS=NOT_RUN
```

Cette correction qualifie uniquement l'isolation de la suite Maven. La campagne humaine réelle,
la validation des trois schémas fournisseur et le reverrouillage final restent à exécuter.

## 15. Première campagne humaine et comportement historique

Le 2026-08-15, l'opérateur a préparé puis confirmé une campagne pour l'identité canonique
`9b9e7909-62e7-3985-bc34-759ad8684224`, fournisseur `16412917`. Le prérequis J4 était le
`snapshot:28`, HTTP `200`, parsé par `event-details-v2`, et la rencontre était au statut
`finished`.

Le premier endpoint `EVENT_STATISTICS` a renvoyé HTTP `404` avec un contenu JSON de 44 octets. Le
brut a été conservé dans le snapshot 30 puis l'ancienne politique l'a classé `TRANSPORT_ERROR`
avec le code `HTTP_STATUS_404`. Le contrôle est passé à `FAILED_LOCKED` après exactement un appel.
Aucun appel `EVENT_INCIDENTS` ou `EVENT_LINEUPS` n'a été tenté et aucun retry n'a été effectué.

L'ouverture ultérieure de la page de `16391135` a montré le même verrou terminal global et n'a
créé aucun snapshot J5 supplémentaire. Les trois schémas restent non validés. La phrase de
confirmation, le jeton local et le payload brut ne sont pas reproduits.

La preuve détaillée est conservée dans
`docs/validation/J5-REAL-EVENT-DATA-CAMPAIGN-20260815.md`.

```text
J5_REAL_EVENT_ID=16412917
J5_REAL_CANONICAL_EVENT_ID=9b9e7909-62e7-3985-bc34-759ad8684224
J5_REAL_ORIGINAL_TERMINAL_STATE=FAILED_LOCKED
J5_REAL_ORIGINAL_TERMINAL_CODE=HTTP_STATUS_404
J5_REAL_PROVIDER_CALLS=1
J5_REAL_STATISTICS_OBSERVATION=NOT_PERSISTED_BY_HISTORICAL_EXECUTION
J5_REAL_STATISTICS_AVAILABILITY=UNAVAILABLE_HTTP_404
J5_REAL_INCIDENTS_QUALIFICATION=NOT_ATTEMPTED
J5_REAL_LINEUPS_QUALIFICATION=NOT_ATTEMPTED
J5_REAL_SNAPSHOT_ID=30
J5_REAL_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_RETRY=0
J5_REAL_CORRECTIVE_RETEST=NOT_RUN_OPERATOR_ONLY
J5_REAL_APPLICATION_STOP=CONFIRMED_LOCAL_PORT_CLOSED
J5_REAL_ENV_RELOCK=PENDING_OPERATOR_CONFIRMATION
```

Le processus local de cette première campagne est arrêté. Cette section reste immuable comme preuve
du comportement historique ; la section 16 consigne la décision corrective ultérieure.

## 16. Amendement correctif — famille facultative indisponible

Le propriétaire a précisé que les statistiques ne sont pas obligatoirement disponibles dans
toutes les compétitions et que, dans ce cas, le championnat d'Ukraine n'est pas considéré comme un
championnat majeur. La requête avait atteint le bon chemin et le bon identifiant : la cause se
trouve dans `J5RealEventDataService`, qui envoyait tout statut non `2xx` vers
`failAndLock(...)` avant toute distinction de disponibilité.

Le correctif autorisé par cet amendement est strictement borné :

- le seul HTTP `404` des trois endpoints J5 exacts devient `ENDPOINT_UNAVAILABLE` ;
- une observation append-only vide de valeurs et de statut `UNAVAILABLE` conserve snapshot, hash,
  normaliseur `event-*-unavailable-v1` et heure de réception ;
- `UNAVAILABLE · N/A` reste distinct de `EMPTY_VALID · 100%` ;
- aucun corps `404` n'est parsé et aucune valeur n'est inventée ;
- le même endpoint n'est jamais rappelé ; la campagne attend le délai prévu puis poursuit ;
- `403`, `408`, `429`, `5xx`, transport, contenu ou schéma incompatible restent terminaux ;
- V9 reclasse les anciens snapshots J5 `TRANSPORT_ERROR/HTTP_STATUS_404` sans modifier le brut et
  sans créer de normalisation rétroactive.

```text
J5_HTTP_404_ROOT_CAUSE=NON_2XX_POLICY_TOO_BROAD
J5_HTTP_404_CORRECTIVE_IMPLEMENTATION=PASS
J5_HTTP_404_CORRECTIVE_STANDARD_TESTS=261
J5_HTTP_404_CORRECTIVE_INTEGRATION_TESTS=20
J5_HTTP_404_CORRECTIVE_FLYWAY_VERSION=9
J5_HTTP_404_CORRECTIVE_PROVIDER_CALLS_BY_AGENT=0
J5_HTTP_404_CORRECTIVE_RETEST=NOT_RUN_OPERATOR_ONLY
J5_PROVIDER_SCHEMA_VALIDATED=NO
```

La campagne corrective reste un geste humain séparé dans le temps : application préalable de V9,
revue de ce Work Order, préparation sans réseau, nouvelle confirmation exacte et au plus trois
appels ordonnés. Le succès attendu peut combiner `UNAVAILABLE` et des familles parsées ; seul un
incident réel doit produire `FAILED_LOCKED`. Après le geste, le propriétaire reverrouille `.env`
et arrête l'application. L'agent ne lit ni ne modifie ce fichier et n'exécute aucun appel réel.

## 17. Amendement correctif — sentinelle des marqueurs de période

Deux campagnes humaines exécutées après V9 ont dépassé l'étape des statistiques puis se sont
arrêtées sur `EVENT_INCIDENTS` :

- pour `16412917`, le snapshot 30 conserve l'indisponibilité HTTP `404` des statistiques et le
  snapshot 32 conserve une réponse incidents HTTP `200` de 22 945 octets contenant 20 objets ;
- pour `16391135`, le snapshot 33 contient des statistiques parsées `COMPLETE · 100%` avec
  `268/268` signaux, puis le snapshot 34 conserve une réponse incidents HTTP `200` de 48 690
  octets contenant 22 objets ;
- dans les deux réponses incidents, V2 signalait exactement deux valeurs hors plage :
  `addedTime=999` sur des objets `incidentType=period` ;
- `SCHEMA_INCOMPATIBLE` étant un incident terminal réel, `EVENT_LINEUPS` n'a été appelé dans
  aucune de ces deux campagnes. Il ne s'agit donc pas d'une absence de compositions en base, mais
  de la conséquence directe du verrouillage après parsing des incidents.

L'interruption signalée par l'opérateur peut expliquer le contexte temporel de `16412917`, mais
la même convention est présente dans l'autre rencontre. Aucune durée n'est donc déduite : `999`
est traité comme une sentinelle fournisseur, uniquement sur un marqueur de période. Les snapshots
32 et 34 restent immuables et conservent leur classification historique V2.

Le correctif versionné introduit `event-incidents-v3` :

- le brut et son SHA-256 restent inchangés ;
- `addedTime=999` sur `period` produit l'avertissement `PROVIDER_SENTINEL_NORMALIZED` et une valeur
  normalisée absente, jamais `999` minutes ;
- la même valeur reste incompatible pour un carton, un but, un remplacement ou tout incident
  métier ;
- les objets globaux `period` et `injuryTime` ne sont pas artificiellement rattachés à domicile ou
  extérieur pour le calcul de complétude ;
- V10 autorise la provenance V3 sans modifier les observations V1/V2 existantes ;
- les tests de service atteignent bien `EVENT_LINEUPS` après un résultat incidents V3 compatible.

La première exécution d'intégration a été bloquée avant les tests Flyway parce que le contexte
héritait des opt-ins opérateur J4/J5 simultanés. Le test PostgreSQL force désormais toutes les
voies réseau à `false`, sans lire ni modifier `.env`. La relance a validé les 21 tests et les
upgrades V1 → V10 et V9 → V10.

```text
J5_INCIDENT_V2_ROOT_CAUSE=PERIOD_ADDED_TIME_SENTINEL_999_REJECTED_BY_GLOBAL_RANGE
J5_INCIDENT_REAL_EVIDENCE=SNAPSHOTS_32_AND_34_HTTP_200
J5_INCIDENT_REAL_OBJECT_COUNTS=20_AND_22
J5_INCIDENT_V3_IMPLEMENTATION=PASS_OFFLINE
J5_INCIDENT_V3_PARSER=event-incidents-v3
J5_INCIDENT_V3_STANDARD_TESTS=263
J5_INCIDENT_V3_INTEGRATION_TESTS=21
J5_INCIDENT_V3_FLYWAY_VERSION=10
J5_INCIDENT_V3_PROVIDER_CALLS_BY_AGENT=0
J5_STATISTICS_REAL_COMPATIBILITY=OBSERVED_ON_16391135
J5_LINEUPS_REAL_COMPATIBILITY=NOT_YET_OBSERVED
J5_INCIDENT_V3_CORRECTIVE_RETEST=PASS_REAL_20_OF_20
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le rapport minimisé est conservé dans
`docs/validation/J5-REAL-INCIDENT-PERIOD-MARKER-CORRECTION-20260816.md`. Le retest réel V3 a
confirmé ensuite que les incidents devenaient parsables, mais a révélé un second verrou de
déduplication avant `EVENT_LINEUPS`, consigné à la section 18.

## 18. Amendement correctif — déduplication historique et participants des remplacements

Le retest humain exécuté après application de V10 sur `16412917` a confirmé les points suivants :

- les statistiques restent `UNAVAILABLE · N/A` sur le snapshot 30, conformément au HTTP `404`
  attendu pour cette rencontre ;
- `event-incidents-v3` parse correctement le snapshot dédupliqué 32 et rend visibles 20 incidents
  avec une complétude `20/20` ;
- la campagne s'arrête néanmoins avec `RAW_CLASSIFICATION_ERROR` après exactement deux appels ;
- `EVENT_LINEUPS` n'est donc toujours pas appelé ; son absence locale ne démontre aucune
  incompatibilité de son endpoint ;
- les remplacements sont présents, mais V3 ne conserve pas les objets distincts `playerIn` et
  `playerOut`, de sorte que les identités du joueur entrant et du joueur sortant ne sont pas
  affichées.

### 18.1 Cause racine de l'arrêt avant compositions

La réponse incidents est identique à celle déjà conservée au snapshot 32. La persistance brute la
déduplique correctement vers cette preuve historique, dont le statut V2
`SCHEMA_INCOMPATIBLE` est immuable. Après le succès V3, le service tentait pourtant de remplacer ce
statut par `PARSED`. Le stockage refusait cette réécriture append-only et le service transformait
ce refus attendu en `RAW_CLASSIFICATION_ERROR`, puis verrouillait la campagne avant le troisième
appel.

Le correctif distingue désormais le résultat de persistance brute :

- un brut `INSERTED` reçoit sa classification terminale après persistance normalisée ;
- un brut `DEDUPLICATED` conserve sa classification historique ; le résultat du parseur courant
  est représenté exclusivement par la nouvelle observation normalisée append-only ;
- le chemin direct J5 n'accepte toujours pas de résultat de cache implicite ;
- aucune famille n'est rappelée et tous les délais ainsi que les règles d'arrêt réseau restent
  inchangés.

Le test de service reproduit exactement la séquence minimisée : snapshot 30 dédupliqué en
`ENDPOINT_UNAVAILABLE`, snapshot 32 dédupliqué et reparsé, puis snapshot compositions nouvellement
inséré. Toute tentative de classification des snapshots 30 ou 32 fait échouer le test ; la
campagne doit malgré cela terminer ses trois appels ordonnés.

### 18.2 Cause racine et décision V4 pour les remplacements

V3 ne lisait que l'objet générique `player`. La forme fournisseur d'un remplacement contient
deux objets sémantiquement distincts : `playerIn` et `playerOut`. `event-incidents-v4` reprend la
règle V3 de `addedTime=999` et ajoute, pour chaque objet `incidentType=substitution` :

- identifiant fournisseur et nom du joueur entrant ;
- identifiant fournisseur et nom du joueur sortant ;
- deux signaux de complétude indépendants ;
- un statut `PARTIAL` et le chemin exact lorsqu'une identité manque, sans identité synthétique.

La migration append-only V11 ajoute quatre colonnes facultatives liées par paires, autorise la
provenance `event-incidents-v4` et ne réécrit aucune observation V1/V2/V3. La vue locale ajoute les
colonnes « Entrant » et « Sortant ». Les tests couvrent plusieurs remplacements dans une même
réponse afin de garantir que la règle ne s'applique pas seulement au premier objet.

### 18.3 Validation hors ligne et suite humaine

```text
J5_INCIDENT_V3_REAL_RETEST=PASS_20_OF_20_ON_SNAPSHOT_32
J5_SECTION_18_REAL_PROVIDER_CALLS=2
J5_SECTION_18_REAL_TERMINAL_CODE=RAW_CLASSIFICATION_ERROR
J5_SECTION_18_LINEUPS_REAL_STATUS=NOT_ATTEMPTED
J5_DEDUPLICATED_RAW_RECLASSIFICATION=DISABLED
J5_INCIDENT_CURRENT_PARSER=event-incidents-v4
J5_SUBSTITUTION_PARTICIPANTS=PERSISTED_AND_RENDERED
J5_FLYWAY_VERSION=11
J5_STANDARD_TESTS=268
J5_INTEGRATION_TESTS=23
J5_PROVIDER_CALLS_BY_AGENT=0
J5_V4_REAL_RETEST_AT_SECTION_18=NOT_RUN_OPERATOR_ONLY
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le rapport minimisé détaillé est conservé dans
`docs/validation/J5-REAL-DEDUPLICATION-AND-SUBSTITUTION-CORRECTION-20260816.md`. Le geste réel qui
était alors requis — redémarrage avec V11, nouvelle préparation et nouvelle confirmation — a
depuis qualifié incidents V4 et compositions V2 ; son résultat et la nouvelle anomalie de cycle
de vie sont consignés à la section 19. L'agent ne lit ni ne modifie `.env` et n'exécute aucun appel
fournisseur.

## 19. Retest réel V4, compositions et réarmement après succès

Le retest humain exécuté après application de V11 sur `16412917` a terminé la campagne sans
incident bloquant et sans retry. Les captures opérateur, non versionnées, établissent les preuves
minimisées suivantes :

| Ordre | Famille | Snapshot | HTTP | Parseur / normaliseur | Complétude | Observation |
|---:|---|---:|---:|---|---|---:|
| 1 | `EVENT_STATISTICS` | 30 | 404 | `event-statistics-unavailable-v1` | `UNAVAILABLE · N/A` | 7 |
| 2 | `EVENT_INCIDENTS` | 32 | 200 | `event-incidents-v4` | `COMPLETE · 36/36` | 12 |
| 3 | `EVENT_LINEUPS` | 55 | 200 | `event-lineups-v2` | `COMPLETE · 85/85` | 13 |

Le snapshot statistiques conserve 44 octets et représente l'indisponibilité attendue pour cette
rencontre. Le snapshot incidents conserve 22 945 octets ; toutes les lignes `substitution`
affichent le joueur entrant et le joueur sortant lorsque le fournisseur les fournit. Le snapshot
compositions conserve 35 669 octets ; les deux côtés sont visibles, avec les formations domicile
`4-1-4-1` et extérieur `4-3-3`. Le contrôle a terminé en `COMPLETED_LOCKED` après exactement trois
appels. Aucun quatrième appel, retry ou rappel d'une famille n'est observé. La phrase ponctuelle de
confirmation n'est ni reproduite ni conservée.

### 19.1 Nouvelle anomalie de cycle de vie

Après ce succès, l'ouverture de la page de `16391135` dans la même instance affichait encore le
snapshot global `COMPLETED_LOCKED` de `16412917` et désactivait « Préparer la campagne J5 ». La
cause est le contrôle J5 singleton : il impose volontairement une concurrence globale égale à un,
mais son garde historique confondait le verrou anti-rejeu d'un succès avec les verrous de
processus issus d'un échec, d'un arrêt ou d'une expiration.

La correction conserve un seul contrôle global et applique désormais les règles suivantes :

- `LOCKED` et `COMPLETED_LOCKED` autorisent une préparation locale explicite si la politique
  fournisseur reste disponible ;
- cette préparation crée un nouvel identifiant de requête et une nouvelle phrase, remplace
  l'identité ciblée, vide les endpoints terminés et efface le code terminal ;
- elle ne résout aucune URI et n'appelle jamais le service d'exécution ;
- l'ancienne phrase est refusée et l'ancien claim ne peut plus continuer ;
- `AWAITING_CONFIRMATION` et `EXECUTING` refusent une campagne concurrente ;
- `FAILED_LOCKED`, `STOPPED_LOCKED` et `EXPIRED_LOCKED` exigent toujours un redémarrage.

Cette distinction n'ajoute ni retry, ni cache, ni polling, ni exécution automatique. Chaque
nouvelle campagne réussie exige un nouveau jeton de formulaire local, une préparation, une phrase
exacte, un acquittement et une action finale distincte.

### 19.2 Validation hors ligne et suite humaine

```text
J5_V4_REAL_RETEST=PASS_THREE_CALLS_NO_RETRY
J5_REAL_STATISTICS=UNAVAILABLE_HTTP_404_SNAPSHOT_30_OBSERVATION_7
J5_REAL_INCIDENTS=EVENT_INCIDENTS_V4_COMPLETE_36_OF_36_SNAPSHOT_32_OBSERVATION_12
J5_REAL_SUBSTITUTION_PARTICIPANTS=PASS_RENDERED
J5_REAL_LINEUPS=EVENT_LINEUPS_V2_COMPLETE_85_OF_85_SNAPSHOT_55_OBSERVATION_13
J5_REAL_TERMINAL_STATE=COMPLETED_LOCKED
J5_COMPLETED_CAMPAIGN_REARM=PASS_OFFLINE_RETEST_REQUIRED
J5_STANDARD_TESTS=270
J5_INTEGRATION_TESTS=23
J5_FLYWAY_VERSION=11
J5_PROVIDER_CALLS_BY_AGENT=0
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le rapport minimisé détaillé est
`docs/validation/J5-REAL-V4-LINEUPS-AND-REARM-CORRECTION-20260816.md`. Après chargement du nouveau
binaire, le prochain contrôle humain doit terminer une campagne A, puis préparer explicitement une
campagne B dans la même instance. Il doit vérifier un nouvel identifiant et une nouvelle phrase,
l'absence de transport pendant la préparation, le refus de l'ancien claim et le maintien du
verrou de processus après tout échec, arrêt ou expiration. Le reverrouillage local et l'arrêt final
restent obligatoires. L'agent ne lit ni ne modifie `.env` et n'exécute aucun appel fournisseur.

## 20. Retest réel du réarmement et correction V5 du carton de banc

Le retest humain du correctif de cycle de vie a qualifié son objectif : après une première campagne
réussie sur `16391135`, l'opérateur a préparé, confirmé puis exécuté une campagne distincte sur
`16483632` sans redémarrer l'instance. Le nouvel identifiant de requête et la nouvelle confirmation
ont donc effectivement remplacé le claim terminé sans le rejouer.

La seconde campagne a ensuite fourni les preuves minimisées suivantes :

| Ordre | Famille | Snapshot | HTTP | Taille | Résultat |
|---:|---|---:|---:|---:|---|
| 1 | `EVENT_STATISTICS` | 59 | 200 | 25 562 octets | `event-statistics-v2`, `COMPLETE · 100 %` |
| 2 | `EVENT_INCIDENTS` | 60 | 200 | 47 092 octets | V4 `SCHEMA_INCOMPATIBLE` sur un incident parmi 17 |
| 3 | `EVENT_LINEUPS` | — | — | — | non appelé après l'arrêt strict |

Il n'y a eu que deux appels fournisseur, sans retry. Le brut incidents a été persisté avant parsing.
La seule rupture V4 est portée par un carton dont `time=-5` n'est pas une minute de match. La règle
métier confirmée par le propriétaire est la suivante : le joueur Kerem Aktürkoğlu reçoit un carton
jaune à la 58e minute, minute portée par `benchTime=58`, avec le motif `Argument`. Aucun calcul à
partir de `reversedPeriodTime` n'est autorisé.

### 20.1 Correction strictement bornée

`event-incidents-v5` hérite de toutes les règles V4 et ajoute uniquement cette forme de carton :

- `incidentType` doit être exactement `card` ;
- `time` doit porter exactement le marqueur technique observé `-5` et exige alors un `benchTime`
  compris entre 0 et 300 ;
- `benchTime` devient la minute normalisée et produit l'avertissement
  `PROVIDER_BENCH_CARD_MINUTE_USED` ;
- `incidentClass` et `reason` sont conservés uniquement pour un carton ;
- le snapshot, son hash, son heure et sa classification historique restent immuables ;
- toute autre valeur négative, y compris sur un `card`, demeure `SCHEMA_INCOMPATIBLE`.

La migration append-only V12 ajoute les colonnes facultatives `incident_class` et `reason`, autorise
la provenance `event-incidents-v5` et ne modifie aucune migration antérieure. La minute persistée
reste contrainte entre 0 et 300. Une réponse brute dédupliquée vers le snapshot 60 pourra ainsi
produire une nouvelle observation V5 sans reclasser V4, puis permettre l'unique appel
`EVENT_LINEUPS` restant dans une nouvelle campagne explicitement confirmée.

Les autres formes d'incidents restent volontairement inchangées. La fiche de règles de gestion par
type d'incident football annoncée par le propriétaire deviendra la source de vérité pour toute
évolution ultérieure ; aucune heuristique générale n'est introduite dans ce correctif.

### 20.2 Validation hors ligne et suite humaine

```text
J5_REAL_REARM_RETEST=PASS_SECOND_CAMPAIGN_STARTED
J5_REAL_SECOND_EVENT_ID=16483632
J5_REAL_SECOND_STATISTICS=EVENT_STATISTICS_V2_COMPLETE_SNAPSHOT_59_OBSERVATION_17
J5_REAL_SECOND_INCIDENTS=EVENT_INCIDENTS_V4_SCHEMA_INCOMPATIBLE_SNAPSHOT_60
J5_REAL_SECOND_LINEUPS=NOT_ATTEMPTED
J5_REAL_SECOND_PROVIDER_CALLS=2
J5_INCIDENT_CURRENT_PARSER=event-incidents-v5
J5_BENCH_CARD_NORMALIZED_MINUTE_SOURCE=benchTime
J5_BENCH_CARD_CORRECTION=PASS_OFFLINE_RETEST_REQUIRED
J5_STANDARD_TESTS=278
J5_INTEGRATION_TESTS=25
J5_FLYWAY_VERSION=12
J5_PROVIDER_CALLS_BY_AGENT=0
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le rapport minimisé détaillé est
`docs/validation/J5-REAL-V5-BENCH-CARD-CORRECTION-20260816.md`. Le prochain geste réel nécessite un
redémarrage, car la campagne V4 s'est correctement verrouillée en `FAILED_LOCKED`, puis une nouvelle
préparation et une nouvelle confirmation opérateur. Il doit vérifier le reparsing V5 du snapshot 60,
l'affichage de la minute 58, de la classe jaune et du motif `Argument`, puis l'appel unique des
compositions. Le reverrouillage local et l'arrêt final restent obligatoires. L'agent ne lit ni ne
modifie `.env` et n'exécute aucun appel fournisseur.
