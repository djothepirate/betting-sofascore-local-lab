# J4 — Campagne humaine réelle `EVENT_DETAILS` sous-étape 1 et anomalie de navigation

- **Date :** 2026-08-15
- **Work Order :** `WO-SS-20260815-004`
- **Branche corrective :** `codex/j4-real-event-details-phase1`
- **Pull Request :** `#8` — `DRAFT`
- **Périmètre réseau exécuté :** événements `16386245` puis `16421052` uniquement
- **État de la campagne :** `COMPLETED_LOCKED`
- **État de la qualification humaine :** `PASS_AFTER_CORRECTIVE_LOCAL_RETEST`
- **Sous-étape 2 :** `AUTHORIZED_PENDING_HUMAN_EXECUTION`

## 1. Résultat minimisé de la campagne

L'opérateur a préparé puis confirmé une seule campagne depuis l'interface locale. L'écran terminal
indique deux événements parsés, sans troisième identifiant et sans champ libre. Les deux réponses
ont été persistées comme snapshots bruts avant leur parsing, puis reliées à des identités
canoniques locales. Les captures fournies restent des pièces temporaires de la tâche Codex et ne
sont pas ajoutées au dépôt.

| Événement | Identité canonique locale | Horaire local | Statut | Compétition | Snapshot | Taille | Parseur |
|---|---|---|---|---|---:|---:|---|
| `16386245` — Saint-Étienne — Clermont Foot | `2f56c736-8efa-361b-a9ff-10886b07d7c6` | `2026-08-14T20:45+02:00[Europe/Paris]` | `finished` | Ligue 2 | 16 | 9283 octets | `event-details-v2` |
| `16421052` — Sevilla — Rayo Vallecano | `e60a8f06-e6eb-3849-b84b-316e42d111e9` | `2026-08-15T21:30+02:00[Europe/Paris]` | `notstarted` | LaLiga | 17 | 9830 octets | `event-details-v2` |

La liste locale des snapshots confirme pour les lignes 16 et 17 le statut HTTP `200`, le schéma
`PARSED`, les tailles ci-dessus et les clés exactes
`EVENT_DETAILS|eventId=16386245` et `EVENT_DETAILS|eventId=16421052`. L'inspection locale explicite
du snapshot 16 confirme son intégrité et son rattachement au parseur `event-details-v2`. Aucun
octet du JSON brut n'est reproduit dans cette preuve.

```text
REAL_PROVIDER_CAMPAIGN_EXECUTION=COMPLETED
REAL_PROVIDER_TRANSPORT_COUNT=2
AUTHORIZED_EVENT_IDS_ONLY=PASS
RAW_BEFORE_PARSE=PASS
EVENT_16386245_SNAPSHOT_ID=16
EVENT_16386245_HTTP_STATUS=200
EVENT_16386245_RAW_SIZE_BYTES=9283
EVENT_16386245_SCHEMA_STATUS=PARSED
EVENT_16421052_SNAPSHOT_ID=17
EVENT_16421052_HTTP_STATUS=200
EVENT_16421052_RAW_SIZE_BYTES=9830
EVENT_16421052_SCHEMA_STATUS=PARSED
RAW_PAYLOAD_INCLUDED_IN_EVIDENCE=NO
```

## 2. Anomalie fonctionnelle observée après la campagne

Après ouverture du détail de Saint-Étienne — Clermont Foot, l'action
**« Recherche par date »** a ramené l'opérateur sur le `2026-08-15`. Le match n'apparaissait donc
plus dans le résultat, alors que Sevilla — Rayo Vallecano restait visible. Cette différence ne
provient ni d'une suppression, ni d'un échec de persistance : Saint-Étienne — Clermont Foot est
rangé au `2026-08-14` en `Europe/Paris`, tandis que Sevilla — Rayo Vallecano est rangé au
`2026-08-15`.

La cause est le lien de retour de la fiche : il transmettait la zone mais omettait la date. Le
contrôleur de recherche appliquait alors sa valeur par défaut, le jour local courant
`2026-08-15`. Deux libellés statiques de la même fiche présentaient en outre tout détail comme
`SYNTHETIC_FIXTURE` et `event-details-v1`, bien que la provenance persistée soit respectivement
`PROVIDER_SNAPSHOT`, `snapshot:16` ou `snapshot:17`, et `event-details-v2`.

L'opérateur a ensuite appliqué **« Arrêt global J4 »**. L'écran confirme l'état terminal
`STOPPED_LOCKED` et `OPERATOR_STOP`. Cette action verrouille le processus courant, mais ne prouve
pas à elle seule que la configuration locale a été remise à ses cinq valeurs bloquées ni que
l'application a été arrêtée après la campagne.

```text
DATA_LOSS=NO
ROOT_CAUSE=DETAIL_BACK_LINK_OMITTED_EVENT_CIVIL_DATE
EVENT_16386245_PERSISTED=YES
EVENT_16421052_PERSISTED=YES
PROVENANCE_DISPLAY_DEFECT=CONFIRMED
GLOBAL_STOP_AFTER_CAMPAIGN=PASS
LOCAL_CONFIGURATION_RELOCKED=PENDING_HUMAN_CONFIRMATION
APPLICATION_STOPPED_AFTER_CAMPAIGN=PENDING_HUMAN_CONFIRMATION
```

## 3. Correction locale

La fiche calcule maintenant sa date de retour depuis
`detail.current().startsAtInZone().toLocalDate()` et transmet explicitement cette date avec la zone
IANA. Elle affiche également le type, la référence et le parseur réellement persistés :

- Saint-Étienne — Clermont Foot revient sur `/events?date=2026-08-14&zone=Europe/Paris` ;
- Sevilla — Rayo Vallecano revient sur `/events?date=2026-08-15&zone=Europe/Paris` ;
- un snapshot réel affiche `PROVIDER_SNAPSHOT`, `snapshot:<id>` et `event-details-v2` ;
- une fixture synthétique conserve sa provenance `SYNTHETIC_FIXTURE`.

Un test MVC de régression construit localement l'événement `16386245`, vérifie la date civile du
14 août, le lien de retour, `PROVIDER_SNAPSHOT`, `snapshot:16` et `event-details-v2`. Ce test ne
résout aucune URI et n'exécute aucun transport fournisseur.

```text
TARGETED_EVENT_EXPLORER_CONTROLLER_TESTS=6
TARGETED_FAILURES=0
STANDARD_TESTS=199
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
INTEGRATION_TESTS_NOT_RERUN=NO_PERSISTENCE_OR_MIGRATION_CHANGE
REAL_PROVIDER_CALLS_DURING_CORRECTIVE_TESTS=0
```

## 4. Retest humain correctif exécuté, sans réseau

Ce retest a relu uniquement les données déjà persistées, sans préparer ni exécuter une nouvelle
campagne. Le porteur a confirmé que les captures 4 et 5 ont été prises après retour arrière
navigateur et a déclaré le test concluant.

1. application arrêtée, remettre d'abord la configuration locale à l'état bloqué décrit dans le
   runbook ;
2. démarrer PostgreSQL et la version corrigée de l'application ;
3. rechercher `2026-08-14` en `Europe/Paris`, ouvrir Saint-Étienne — Clermont Foot, vérifier
   `PROVIDER_SNAPSHOT`, `snapshot:16` et `event-details-v2`, puis utiliser
   **« Recherche par date »** ;
4. confirmer que la page reste au `2026-08-14` et que le match demeure consultable ;
5. rechercher `2026-08-15`, ouvrir Sevilla — Rayo Vallecano, vérifier `snapshot:17`, puis confirmer
   que le retour reste au `2026-08-15` ;
6. confirmer visuellement les verrous réseau, arrêter l'application et ne pas réarmer la campagne.

```text
CORRECTIVE_RETEST_PROVIDER_CALLS_AUTHORIZED=0
CORRECTIVE_RETEST_USES_PERSISTED_SNAPSHOTS_ONLY=YES
HUMAN_REAL_EVENT_16386245_QUALIFICATION=PASS
HUMAN_REAL_EVENT_16421052_QUALIFICATION=PASS
HUMAN_REAL_PHASE1_QUALIFICATION=PASS
LOCAL_NAVIGATION_QUALIFICATION=PASS_AFTER_CORRECTION
PROVENANCE_DISPLAY_QUALIFICATION=PASS_AFTER_CORRECTION
LOCAL_CONFIGURATION_RELOCKED=YES
APPLICATION_STOPPED_AFTER_CORRECTIVE_RETEST=PENDING_HUMAN_CONFIRMATION
J4_REAL_PHASE2_AUTHORIZED=YES
J4_WORK_ORDER=ACTIVE
J4_CAN_BE_CLOSED=NO
```

## 5. Décision de qualification

La campagne réseau bornée a bien été exécutée, les deux réponses ont été conservées puis parsées,
et le retest local a confirmé la correction de navigation ainsi que les libellés de provenance.
La sous-étape 1 est donc humainement qualifiée. Le Work Order reste néanmoins `IN_DEVELOPMENT` :
la sous-étape 2 paramétrable et répétable est autorisée mais n'a encore exécuté aucun événement
réel, et l'arrêt de l'application après le dernier retest doit encore être confirmé.

```text
REAL_PROVIDER_QUALIFICATION=EXECUTED_PHASE1
LOCAL_NAVIGATION_QUALIFICATION=PASS_AFTER_CORRECTION
HUMAN_REAL_PHASE1_QUALIFICATION=PASS
J4_REAL_PHASE1_PULL_REQUEST=8
J4_REAL_PHASE1_PULL_REQUEST_STATE=DRAFT
J4_REAL_PHASE2_AUTHORIZED=YES
J4_REAL_PHASE2_PROVIDER_QUALIFICATION=NOT_RUN
APPLICATION_STOPPED_AFTER_CORRECTIVE_RETEST=PENDING_HUMAN_CONFIRMATION
WORK_ORDER_STATUS=IN_DEVELOPMENT
J4_WORK_ORDER=ACTIVE
J4_CAN_BE_CLOSED=NO
```
