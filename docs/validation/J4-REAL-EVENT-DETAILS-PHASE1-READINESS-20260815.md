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
INTEGRATION_TESTS=16
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
second appel après un `429` ou une incompatibilité de schéma. Le seizième test d’intégration
reproduit une base V5 préremplie, applique V6, compare tous les champs historiques avant/après et
confirme que le trigger append-only réactivé refuse encore `UPDATE` et `DELETE`.

## 3. Incident de migration locale et correction

Le démarrage humain du 2026-08-15 à `08:03:29+02:00` a échoué avant disponibilité de l’interface :
la base locale était en V5 avec une observation synthétique et le backfill initial de V6 a été
bloqué par `event_detail_observation_append_only`. Flyway a indiqué que les changements avaient été
annulés. L’application a ensuite fermé Hikari et Tomcat ; aucun transport `EVENT_DETAILS` n’a pu
être exécuté.

V6 n’ayant pas été poussée, appliquée avec succès sur un environnement partagé ou publiée, son SQL
a été corrigé avant publication. Le trigger de la seule table migrée est désormais suspendu
pendant le backfill structurel et réactivé dans la même transaction. Le test V5 préremplie → V6
valide ce chemin absent de la qualification initiale.

La reprise humaine du 2026-08-15 a ensuite appliqué V6 sur la base persistante avec les cinq clés
réseau bloquées. L’application a démarré, l’interface a confirmé l’état `LOCKED`, puis la recherche
locale du `2026-08-12` a retrouvé l’identité synthétique, son détail et ses deux versions. L’arrêt
gracieux de Tomcat, JPA et Hikari a été confirmé à `08:34:52+02:00`. Aucun transport fournisseur
n’a été déclenché.

```text
INCIDENT_TYPE=LOCAL_DATABASE_MIGRATION
INCIDENT_CODE=V6_BACKFILL_BLOCKED_BY_APPEND_ONLY_TRIGGER
FLYWAY_ROLLBACK=CONFIRMED_BY_STARTUP_LOG
PROVIDER_CALL_ATTEMPTS=0
RAW_PROVIDER_SNAPSHOT_CREATED_BY_INCIDENT=NO
REAL_PROVIDER_QUALIFICATION=NOT_RUN
DATABASE_VERSION_AFTER_CORRECTIVE_RETRY=V6
LOCAL_V5_TO_V6_CORRECTIVE_RETRY=PASS
LOCAL_CONFIGURATION_RELOCKED_AFTER_INCIDENT=YES
SYNTHETIC_HISTORY_PRESERVED=YES
APPLICATION_STOPPED=YES
```

### 3.1 Isolation de la validation automatisée sous configuration J4 armée

Après activation locale des cinq clés de la sous-étape 1, un premier `clean verify` a produit un
unique échec dans `SofascorePropertiesTest.bindsDocumentedJ3EnvironmentKeysThroughApplicationYaml`.
Le mini-contexte J3 héritait directement des variables J4 de l’opérateur pendant le binding Spring
et déclenchait correctement la règle d’exclusivité J3/J4. Aucun code de transport, parseur ou
persistance n’était en défaut et aucun appel fournisseur n’a été tenté.

Les deux scénarios de binding utilisent désormais des propriétés système temporaires pour leurs
cinq clés documentées, fixent explicitement l’opt-in opposé à `false` et retirent uniquement la
source `systemEnvironment` de leur mini-contexte. Le runtime conserve ses sources réelles, ses
validations et ses valeurs par défaut inchangées.

Le test ciblé puis `Verify-Local.ps1` ont été rejoués dans un processus possédant exactement la
configuration J4 active communiquée par l’opérateur.

```text
INCIDENT_TYPE=AUTOMATED_TEST_ENVIRONMENT_LEAK
AFFECTED_TEST=SofascorePropertiesTest.bindsDocumentedJ3EnvironmentKeysThroughApplicationYaml
RUNTIME_CONFIGURATION_CHANGED=NO
J3_J4_MUTUAL_EXCLUSION_CHANGED=NO
TARGETED_PROPERTIES_TESTS=6
TARGETED_FAILURES=0
STANDARD_TESTS_WITH_J4_CONFIGURATION_ACTIVE=198
FAILURES=0
ERRORS=0
SKIPPED=0
REAL_PROVIDER_CALLS=0
ACTIVE_J4_ENVIRONMENT_TEST_ISOLATION=PASS
```

## 4. État de qualification

La réussite de Maven établit que le code est prêt pour une campagne humaine contrôlée. Elle
n'établit ni la disponibilité actuelle du fournisseur, ni la compatibilité de ses réponses
réelles, ni la justesse métier des deux matches. Ces trois points nécessitent l'exécution humaine
décrite dans le runbook.

```text
TECHNICAL_PHASE1_READINESS=PASS_AFTER_V6_UPGRADE_FIX
TECHNICAL_OFFLINE_QUALIFICATION=PASS
REAL_PROVIDER_QUALIFICATION=NOT_RUN
HUMAN_REAL_EVENT_16386245_QUALIFICATION=PENDING
HUMAN_REAL_EVENT_16421052_QUALIFICATION=PENDING
HUMAN_REAL_PHASE1_QUALIFICATION=PENDING
CONFIGURATION_RELOCK_AFTER_CAMPAIGN=PENDING_NOT_RUN
LOCAL_V5_TO_V6_CORRECTIVE_RETRY=PASS
LOCAL_CONFIGURATION_RELOCKED_AFTER_INCIDENT=YES
SYNTHETIC_HISTORY_PRESERVED=YES
APPLICATION_STOPPED=YES
ACTIVE_J4_ENVIRONMENT_TEST_ISOLATION=PASS
WORK_ORDER_STATUS=IN_DEVELOPMENT
J4_WORK_ORDER=ACTIVE
J4_CAN_BE_CLOSED=NO
```

## 5. Preuve humaine à consigner après la campagne

Pour chaque événement, la preuve doit rester minimisée et ne contenir que :

- identifiant fournisseur et identité canonique locale ;
- équipes, horaire local, statut, compétition et détail normalisés affichés ;
- identifiant du snapshot, taille, SHA-256, parseur, classification et heure de réception ;
- résultat humain `PASS` ou `FAIL` et anomalie éventuelle sans payload brut.

Au premier incident, aucun deuxième essai ni contournement n'est autorisé. L'opérateur arrête
l'application, remet les variables locales J3/J4 à l'état bloqué selon le runbook et consigne
l'incident. Même après deux résultats humains réussis, la sous-étape 2 reste interdite jusqu'à la
communication et l'autorisation explicites de son identifiant.
