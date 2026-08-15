# J5 réel — Readiness technique hors ligne

- **Date :** 2026-08-15
- **Work Order :** `WO-SS-20260815-006`
- **Branche :** `codex/j5-real-event-data-qualification`
- **Qualification technique :** `PASS`
- **Qualification humaine réelle :** `PENDING`
- **Appels SofaScore pendant la réalisation :** `0`
- **Lecture ou modification de `.env` par l'agent :** `NO`
- **Schémas fournisseur J5 validés :** `NO`

## 1. Périmètre qualifié

- opt-in J5 désactivé par défaut et exclusif de J3/J4 ;
- origine exacte et trois endpoints exacts ;
- préparation sans réseau, phrase cinq minutes, acquittement et claim immuable ;
- une campagne par processus, trois appels maximum dans l'ordre ;
- délai minimal de trois secondes et concurrence unitaire ;
- transport sans proxy, redirection, cookie, jeton ou `User-Agent` applicatif ;
- persistance des octets bruts avant parsing ;
- parseurs V2 sans résultat partiel sur incompatibilité ;
- provenance normalisée `PROVIDER_SNAPSHOT` ;
- migration append-only V8 ;
- arrêt au premier incident, sans retry ;
- interface locale protégée par jeton de formulaire et résultat minimisé.

## 2. Vérifications exécutées

```text
COMMAND=.\mvnw.cmd clean verify
RESULT=PASS
STANDARD_TESTS=257
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
SOFASCORE_PROVIDER_CALLS=0
```

```text
COMMAND=.\mvnw.cmd -Pintegration-tests verify
RESULT=PASS
STANDARD_TESTS=257
INTEGRATION_TESTS=18
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
FLYWAY_MIGRATIONS=8
POSTGRESQL=18.4_TESTCONTAINERS
SOFASCORE_PROVIDER_CALLS=0
```

### 2.1 Revalidation avec la configuration opérateur J5 armée

Une première exécution opérateur de `mvnw.cmd clean verify`, après activation locale de J5 mais
avant toute action Web, a mis en évidence un défaut d'isolation de trois tests historiques de
binding. Les scénarios J3, J4 sous-étape 1 et J4 sous-étape 2 surchargeaient leurs anciens opt-ins,
mais héritaient encore de l'opt-in J5 importé depuis la configuration locale. La validation
d'exclusivité bloquait correctement leur démarrage ; aucun transport fournisseur n'était engagé.

Les trois scénarios fixent désormais explicitement l'opt-in J5 à `false` et vérifient cette valeur.
La commande ayant échoué a été rejouée sans désarmer la configuration opérateur :

```text
COMMAND=.\mvnw.cmd clean verify
RESULT=PASS
STANDARD_TESTS=257
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
SOFASCORE_PROVIDER_CALLS=0
LOCAL_J5_CONFIGURATION=ARMED_FOR_OFFLINE_PRECHECK
REAL_J5_CAMPAIGN_EXECUTED=NO
```

Les avertissements Jansi sur l'accès natif et Mockito/Byte Buddy sur le chargement dynamique de
l'agent sont non bloquants et indépendants de ce correctif. Ils n'expliquent pas l'échec initial.

Les tests de transport utilisent `MockRestServiceServer`. Les fixtures V2 sont locales et ne sont
pas des payloads fournisseur. Le test PostgreSQL prouve qu'une observation V2 peut référencer un
snapshot brut et être relue avec sa provenance.

## 3. Invariants audités

```text
SERVER_ADDRESS=127.0.0.1
DEFAULT_SOFASCORE_ENABLED=false
DEFAULT_J5_QUALIFICATION_ENABLED=false
CATALOG_CALLABLE=false
CATALOG_REAL_URI=ABSENT
CONNECTOR_GATE=BLOCKING
LIVE_MAVEN_PROFILE=BLOCKING
AUTOMATIC_REFRESH=false
LIVE_POLLING=false
MAXIMUM_CONCURRENCY=1
MINIMUM_DELAY=3s
RETRY=ABSENT
RAW_BEFORE_PARSE=PASS
V1_TO_V8_MIGRATION=PASS
```

## 4. Décision

```text
J5_REAL_TECHNICAL_READINESS=PASS
J5_REAL_CAMPAIGN_AUTHORIZED_BY_WORK_ORDER=YES_AFTER_HUMAN_REVIEW
J5_REAL_CAMPAIGN_EXECUTED=NO
J5_REAL_PROVIDER_SCHEMA_VALIDATED=NO
WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le prochain geste appartient à l'opérateur : relire le diff et le runbook, choisir une identité
canonique, appliquer manuellement la configuration temporaire, exécuter une seule campagne puis
reverrouiller `.env` avant tout redémarrage. Succès et incident exigent tous deux une preuve
minimisée et l'arrêt de l'application.
