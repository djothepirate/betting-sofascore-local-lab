# J9 — Qualification corrective WO-029 de la borne de port du push local optionnel

## 1. État du rapport

```text
REPORT=J9-WO029-OPTIONAL-LOCAL-PUSH-PORT-BOUNDARY-QUALIFICATION-20260901
REPORT_STATUS=DRAFT
WORK_ORDER=WO-SS-20260901-029-j9-optional-local-push-port-boundary-hardening
WORK_ORDER_STATUS=IN_PROGRESS
BRANCH=codex/j9-wo029-port-boundary-hardening
BASE_COMMIT=36598f1979ddc7be7081431b147691e7e9b8e49d
QUALIFICATION_RESULT=NOT_RUN
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Ce rapport autonome doit recevoir les preuves du correctif P2 de la PR #21. Son état `DRAFT` ne
qualifie encore ni l'implémentation ni la branche et ne modifie pas la preuve historique WO-027.

## 2. Constat à corriger

La base `36598f1979ddc7be7081431b147691e7e9b8e49d` exige un port explicite positif dans
`OptionalLocalPushProperties.isLoopbackQualificationSafe()` et dans
`BettingProjectJ7DeliveryHttpTransport.requireExactHttpsIpv4LoopbackOrigin()`, mais n'impose pas la
borne supérieure `65535`.

Une origine `https://127.0.0.1:65536` peut donc franchir ces validations. Après persistance du claim
`IN_FLIGHT`, le client HTTP Java peut lever une `IllegalArgumentException` hors du contrat
`J7DeliveryTransportException`, laissant une tentative non envoyée à réconcilier manuellement.

## 3. Contrat correctif attendu

```text
MINIMUM_EXPLICIT_ORIGIN_PORT=1
MAXIMUM_EXPLICIT_ORIGIN_PORT=65535
PORT_RANGE_KIND=CLOSED_INTERVAL
CONFIGURATION_VALIDATION_BEFORE_EXPORT=YES
CONFIGURATION_VALIDATION_BEFORE_CLAIM=YES
TRANSPORT_DEFENCE_IN_DEPTH_BEFORE_HTTP_CLIENT=YES
INVALID_PORT_LEDGER_ROWS=0
INVALID_PORT_SOCKET_ATTEMPTS=0
INVALID_PORT_PROVIDER_CALLS=0
```

La configuration doit fermer le parcours avant lecture de l'export et avant claim. Le transport
doit appliquer la même borne indépendamment, avant la construction du client qu'il possède ou toute
utilisation d'un client injecté. La défense en profondeur ne doit pas être remplacée par une
capture tardive de l'exception du JDK.

## 4. Baseline WO-027 immuable

```text
WO027_QUALIFIED_COMMIT=5af48e5ea0e7150b460fe106da74aa5d3bd5489a
WO027_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WO027_QUALIFICATION_REPORT_SHA256=d656b7ea12ba38a40b88e9a78e5b7afb9642c4405080b9246b8539d177eb1d12
WO027_COMPLETED_WORK_ORDER_SHA256=be18d440edf33ae8c511c4b1bab5ad1e70f6c2167027d525769c98417ebf5c2c
WO027_SUREFIRE_BASELINE=1036/0/0/5
WO027_FAILSAFE_BASELINE=84/0/0/0
WO027_PROVIDER_CALLS=0
WO027_REAL_RECEIVER_CALLS=0
WO027_RESIDUAL_LISTENERS=0
```

La qualification finale WO-029 devra recalculer les deux SHA-256 ci-dessus et confirmer leur
identité byte-for-byte. Elle n'ajoutera aucune information aux fichiers historiques eux-mêmes.

## 5. Matrice de qualification à remplir

| Porte | Preuve attendue | Résultat |
|---|---|---|
| port absent (`-1`) | propriétés et transport refusent localement | `PENDING` |
| port `0` | refus avant export, claim, client et socket | `PENDING` |
| port `1` | acceptation structurelle, sans tentative de connexion | `PENDING` |
| port `65535` | acceptation structurelle, sans tentative de connexion | `PENDING` |
| port `65536` | refus par propriétés et transport | `PENDING` |
| barrière avant claim | aucune identité/tentative/projection créée | `PENDING` |
| J7 | export non lu ou inchangé, statut `HUMAN_VALIDATED` préservé | `PENDING` |
| origine valide loopback | non-régression WO-027 | `PENDING` |
| suite standard | `mvnw.cmd --offline clean verify` | `PENDING` |
| suite d'intégration | `mvnw.cmd --offline -Pintegration-tests verify` | `PENDING` |
| preuves WO-027 | SHA-256 historiques inchangés | `PENDING` |
| hygiène | diff, secrets, flags, listeners et artefacts interdits | `PENDING` |

## 6. Fichiers runtime et tests attendus

Le correctif est attendu au minimum dans les gardes suivantes, sans changement d'interface
publique :

- `OptionalLocalPushProperties.isLoopbackQualificationSafe()` ;
- `BettingProjectJ7DeliveryHttpTransport.requireExactHttpsIpv4LoopbackOrigin()`.

Les tests doivent discriminer les deux bornes et prouver le refus avant claim. Aucun test ne doit
contacter SofaScore, le Betting Project réel, un VPS, ni tenter une connexion vers les ports `1`,
`65535` ou `65536`.

## 7. Verdict provisoire

```text
REPORT_STATUS=DRAFT
QUALIFICATION_RESULT=NOT_RUN
PORT_RANGE_GUARDS_QUALIFIED=NO
BEFORE_CLAIM_INVARIANT_QUALIFIED=NO
WO027_BYTE_IDENTITY_QUALIFIED=NO
PR21_REVIEW_THREAD_RESOLVED=NO
PR21_MERGED=NO
```
