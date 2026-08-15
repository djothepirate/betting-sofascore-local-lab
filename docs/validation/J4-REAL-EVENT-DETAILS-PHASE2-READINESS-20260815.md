# J4 — Readiness de la sous-étape 2 `EVENT_DETAILS` paramétrable et répétable

- **Date :** 2026-08-15
- **Work Order :** `WO-SS-20260815-004`
- **Branche :** `codex/j4-real-event-details-phase1`
- **Pull Request :** `#8` — `DRAFT`
- **Qualification sous-étape 1 :** `PASS_AFTER_CORRECTIVE_LOCAL_RETEST`
- **Autorisation sous-étape 2 :** `YES`
- **Appel réel sous-étape 2 pendant l'implémentation :** `NOT_RUN`
- **Statut :** `TECHNICAL_OFFLINE_QUALIFICATION_PASS`

## 1. Périmètre autorisé

L'interface accepte un seul ID numérique `EVENT_DETAILS` par intention. Chaque appel exige une
préparation sans réseau, une phrase contenant l'ID, un acquittement et une action finale distincte.
Après `COMPLETED_LOCKED`, le même ID peut être préparé à nouveau pour actualiser un match. Il n'y
a aucune limite au nombre de cycles humains réussis successifs, mais chaque cycle contient au plus
un transport et une nouvelle confirmation. Un échec ou un arrêt refuse tout nouveau cycle dans le
processus courant.

La voie paramétrable ne consulte pas le cache : un rappel confirmé doit acquérir un nouvel
instantané. Elle conserve un délai minimal de trois secondes entre transports et n'active aucun
polling, planification, retry ou concurrence supérieure à un.

## 2. Verrous vérifiés hors ligne

- valeurs sûres par défaut et nouvel opt-in `false` ;
- sélection exclusive : la sous-étape 1 est bloquée lorsque la sous-étape 2 est sélectionnée ;
- origine exacte `https://www.sofascore.com` et chemin composé depuis un entier borné ;
- absence d'ID dans la requête Web finale : seul le claim préparé porte l'identifiant ;
- un transport simulé maximum par cycle et nouvel appel simulé lors d'un second cycle ;
- persistance du brut avant parsing et normalisation ;
- arrêt sur `429` sans retry ni normalisation partielle ;
- arrêt global appliqué aux deux contrôles ;
- aucun payload brut dans le template, les notices ou cette preuve.

## 3. Commandes et résultats

```text
MAIN_COMPILE=PASS
TARGETED_TESTS=36
TARGETED_FAILURES=0
TARGETED_ERRORS=0
STANDARD_TESTS=212
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
INTEGRATION_TESTS=16
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
REAL_PROVIDER_CALLS_DURING_AUTOMATED_TESTS=0
```

Les transports des tests sont des mocks ou `MockRestServiceServer`. Maven ne dispose d'aucun
scénario autorisé à contacter SofaScore.

## 4. Qualification humaine encore requise

La réussite hors ligne ne prouve ni l'existence de l'ID que choisira l'opérateur, ni la
compatibilité de sa réponse actuelle, ni l'évolution d'un match en cours. Le porteur doit suivre la
section 3.13 du runbook, vérifier un premier cycle et, pour qualifier le rafraîchissement, exécuter
un second cycle manuel sur le même ID après une nouvelle confirmation.

La preuve finale devra indiquer les IDs de snapshots et champs normalisés sans reproduire le JSON,
puis confirmer l'arrêt global, les six valeurs bloquées et l'arrêt de l'application.

```text
TECHNICAL_OFFLINE_QUALIFICATION=PASS
REAL_PROVIDER_QUALIFICATION=NOT_RUN
HUMAN_PARAMETERIZED_EVENT_QUALIFICATION=PENDING
HUMAN_MANUAL_REFRESH_QUALIFICATION=PENDING
LOCAL_CONFIGURATION_RELOCKED_AFTER_PHASE2=PENDING
APPLICATION_STOPPED_AFTER_PHASE2=PENDING
WORK_ORDER_STATUS=IN_DEVELOPMENT
J4_WORK_ORDER=ACTIVE
J4_CAN_BE_CLOSED=NO
```
