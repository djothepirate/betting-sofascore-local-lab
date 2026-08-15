# J4 — Préparation technique de la qualification réelle `EVENT_DETAILS` sous-étape 1

- **Date :** 2026-08-15
- **Work Order :** `WO-SS-20260815-004`
- **Branche :** `codex/j4-real-event-details-phase1`
- **Nature de la preuve :** qualification technique hors ligne de la voie réelle bornée
- **Appel fournisseur exécuté :** `NO`
- **Validation humaine des matches :** `PENDING`

## 1. Portée vérifiée

La préparation technique couvre exclusivement la sous-étape 1 autorisée :

- origine exacte `https://www.sofascore.com` ;
- chemin exact `/api/v1/event/{eventId}` ;
- événements autorisés `16386245` puis `16421052` ;
- deux transports maximum, concurrence unitaire et délai minimal de trois secondes entre deux
  transports effectifs ;
- cache local consulté avant transport ;
- réponse brute persistée avec SHA-256 avant parsing ;
- classification append-only avec le parseur `event-details-v2`, sans réécriture des observations
  historiques `event-details-v1` ;
- affichage local limité aux champs normalisés et aux métadonnées de provenance ;
- arrêt et verrou terminal au premier incident, `403`, `429`, `5xx`, timeout, contenu non JSON,
  incompatibilité de schéma ou incohérence d'identifiant ;
- aucune saisie d'un troisième identifiant dans l'interface.

La revue de l'ADR-SS-001 est consignée dans le Work Order. Elle conclut à une compatibilité sans
modification de l'ADR, avec une politique J4 volontairement plus stricte : aucun retry sur `5xx`.

## 2. Commandes et résultats

### Suite standard

```text
COMMAND=mvnw.cmd clean verify
JAVA_VERSION=25.0.4
STANDARD_TESTS=198
FAILURES=0
ERRORS=0
SKIPPED=0
RESULT=PASS
REAL_PROVIDER_CALLS=0
```

### Suite PostgreSQL/Testcontainers

```text
COMMAND=mvnw.cmd -Pintegration-tests verify
STANDARD_TESTS=198
INTEGRATION_TESTS=15
FAILURES=0
ERRORS=0
SKIPPED=0
FLYWAY_SCHEMA=V6
RESULT=PASS
REAL_PROVIDER_CALLS=0
```

Les tests du transport utilisent un serveur simulé local. Ils couvrent le chemin exact, l'absence
de redirection et l'absence d'accès Internet. Les tests d'orchestration couvrent l'ordre
« persistance brute puis parsing », le cache, le délai, la limite de deux appels et l'arrêt sans
second appel après un `429` ou une incompatibilité de schéma.

## 3. État de qualification

La réussite de Maven établit que le code est prêt pour une campagne humaine contrôlée. Elle
n'établit ni la disponibilité actuelle du fournisseur, ni la compatibilité de ses réponses
réelles, ni la justesse métier des deux matches. Ces trois points nécessitent l'exécution humaine
décrite dans le runbook.

```text
TECHNICAL_PHASE1_READINESS=PASS
TECHNICAL_OFFLINE_QUALIFICATION=PASS
REAL_PROVIDER_QUALIFICATION=NOT_RUN
HUMAN_REAL_EVENT_16386245_QUALIFICATION=PENDING
HUMAN_REAL_EVENT_16421052_QUALIFICATION=PENDING
HUMAN_REAL_PHASE1_QUALIFICATION=PENDING
CONFIGURATION_RELOCK_AFTER_CAMPAIGN=PENDING_NOT_RUN
WORK_ORDER_STATUS=IN_DEVELOPMENT
J4_WORK_ORDER=ACTIVE
J4_CAN_BE_CLOSED=NO
```

## 4. Preuve humaine à consigner après la campagne

Pour chaque événement, la preuve doit rester minimisée et ne contenir que :

- identifiant fournisseur et identité canonique locale ;
- équipes, horaire local, statut, compétition et détail normalisés affichés ;
- identifiant du snapshot, taille, SHA-256, parseur, classification et heure de réception ;
- résultat humain `PASS` ou `FAIL` et anomalie éventuelle sans payload brut.

Au premier incident, aucun deuxième essai ni contournement n'est autorisé. L'opérateur arrête
l'application, remet les variables locales J3/J4 à l'état bloqué selon le runbook et consigne
l'incident. Même après deux résultats humains réussis, la sous-étape 2 reste interdite jusqu'à la
communication et l'autorisation explicites de son identifiant.
