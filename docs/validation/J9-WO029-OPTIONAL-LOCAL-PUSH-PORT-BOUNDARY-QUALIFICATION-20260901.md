# J9 — Qualification corrective WO-029 de la borne de port du push local optionnel

## 1. État du rapport

```text
REPORT=J9-WO029-OPTIONAL-LOCAL-PUSH-PORT-BOUNDARY-QUALIFICATION-20260901
REPORT_STATUS=VALIDATED
WORK_ORDER=WO-SS-20260901-029-j9-optional-local-push-port-boundary-hardening
WORK_ORDER_STATUS=VALIDATED
BRANCH=codex/j9-wo029-port-boundary-hardening
BASE_COMMIT=36598f1979ddc7be7081431b147691e7e9b8e49d
OPENING_COMMIT=47732b3af56e1bcc0071b427f8bbf33d4676b5a6
RUNTIME_FIX_COMMIT=8fb4d5a64a8c36d553168c8e82cc4f46454ee96a
QUALIFIED_CODE_AND_TEST_COMMIT=699d2245322ef9614f05f912371db8fd9edca2b9
QUALIFIED_AT_UTC=2026-09-01T12:40:46Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-01T14:40:46+02:00
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Le défaut visé par le P2 de la PR #21 est corrigé et qualifié localement sur le parcours J7 local
optionnel. La configuration et le transport imposent le même intervalle de port fermé
`[1, 65535]`, et les tests prouvent qu'une origine invalide est refusée avant lecture de l'export,
avant claim ledger et avant utilisation du client HTTP. Cette qualification ne modifie ni la
preuve historique WO-027, ni les interdictions de réseau réel.

## 2. Constat et correction

La base `36598f1979ddc7be7081431b147691e7e9b8e49d` exigeait un port explicite positif dans
`OptionalLocalPushProperties.isLoopbackQualificationSafe()` et dans
`BettingProjectJ7DeliveryHttpTransport.requireExactHttpsIpv4LoopbackOrigin()`, mais n'imposait pas
la borne supérieure `65535`.

Une origine `https://127.0.0.1:65536` pouvait donc franchir ces validations. Après persistance du
claim `IN_FLIGHT`, le client HTTP Java pouvait lever une `IllegalArgumentException` hors du contrat
`J7DeliveryTransportException`, laissant une tentative non envoyée à réconcilier manuellement.

Le commit `8fb4d5a64a8c36d553168c8e82cc4f46454ee96a` ajoute la borne haute aux deux gardes. Le commit
`699d2245322ef9614f05f912371db8fd9edca2b9` complète la preuve discriminante sur le port absent,
`0`, `1`, `65535` et `65536`, sans connexion vers ces ports.

## 3. Contrat correctif qualifié

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

La configuration ferme le parcours avant lecture de l'export et avant claim. Le transport applique
la même borne indépendamment, avant la construction du client qu'il possède ou toute utilisation
d'un client injecté. La défense en profondeur ne repose donc pas sur une capture tardive de
l'exception du JDK.

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
WO027_FROZEN_FILES_BYTE_IDENTICAL=YES
```

Les deux SHA-256 ont été recalculés après les tests finaux et concordent exactement avec la
baseline. Aucun octet n'a été ajouté aux fichiers historiques WO-027.

## 5. Matrice de qualification

| Porte | Preuve mesurée | Résultat |
|---|---|---|
| port absent (`-1`) | propriétés et transport refusent ; service : zéro interaction export/ledger/transport | `PASS` |
| port `0` | propriétés et transport refusent ; service : zéro interaction export/ledger/transport | `PASS` |
| port `1` | propriétés et fabrique transport acceptent structurellement ; zéro utilisation du client | `PASS` |
| port `65535` | propriétés et fabrique transport acceptent structurellement ; zéro utilisation du client | `PASS` |
| port `65536` | propriétés et transport refusent ; service : zéro interaction export/ledger/transport | `PASS` |
| barrière avant claim | `verifyNoInteractions(exportService, ledgerStore, transport)` sur chaque origine invalide | `PASS` |
| J7 | aucun chargement ni changement d'export sur refus ; invariants WO-027 rejoués | `PASS` |
| origine valide loopback | tests mTLS et end-to-end loopback WO-027 verts | `PASS` |
| suite standard | `mvnw.cmd --offline clean verify` : 1041/0/0/5 et 84/0/0/0 | `PASS` |
| suite d'intégration explicite | `mvnw.cmd --offline -Pintegration-tests verify` : 1041/0/0/5 et 84/0/0/0 | `PASS` |
| preuves WO-027 | deux SHA-256 historiques inchangés | `PASS` |
| hygiène | diff, secrets, flags, listeners, conteneurs et artefacts interdits contrôlés | `PASS` |

## 6. Fichiers runtime et tests

Le correctif runtime reste limité à :

- `OptionalLocalPushProperties.isLoopbackQualificationSafe()` ;
- `BettingProjectJ7DeliveryHttpTransport.requireExactHttpsIpv4LoopbackOrigin()`.

La qualification ciblée couvre :

- `OptionalLocalPushPropertiesTest` : intervalle inférieur et supérieur, validation Bean
  Validation et defaults fail-closed ;
- `BettingProjectJ7DeliveryHttpTransportTest` : intervalle inférieur et supérieur avant toute
  utilisation du client injecté ;
- `J7OptionalDeliveryServiceTest` : refus du port absent, `0` et `65536` avant export, claim ou
  transport.

Aucun changement d'interface publique, endpoint, schéma SQL, migration, contrat J7, retry,
fallback, polling ou scheduler n'est introduit sous WO-029.

## 7. Vérifications exécutées

| Vérification | Résultat |
|---|---|
| ciblés `OptionalLocalPushPropertiesTest,BettingProjectJ7DeliveryHttpTransportTest,J7OptionalDeliveryServiceTest` | 50 tests, 0 échec, 0 erreur, 0 ignoré ; `BUILD SUCCESS` à `2026-09-01T12:32:56Z` |
| `mvnw.cmd --offline clean verify` | Surefire 1041/0/0/5 ; Failsafe 84/0/0/0 ; `BUILD SUCCESS` à `2026-09-01T12:37:12Z` |
| `mvnw.cmd --offline -Pintegration-tests verify` | Surefire 1041/0/0/5 ; Failsafe 84/0/0/0 ; `BUILD SUCCESS` à `2026-09-01T12:40:46Z` |
| `docker compose --env-file <fichier local ignoré> config --quiet` | `PASS` ; aucune valeur sensible affichée |
| `git diff --check` | `PASS` |
| scan de secrets à haute confiance du delta | 0 correspondance |
| `server.address` | `127.0.0.1` |
| flags livraison | `enabled=false`, `remote-delivery-authorized=false`, `loopback-qualification=false`, `automatic-retry-enabled=false` |
| flags fournisseur | `sofascore.enabled=false`, Playwright et qualifications J3/J4/J5/tournoi à `false` par défaut |
| permission officielle | `NOT_EVIDENCED` |
| SHA-256 WO-027 | `be18d440...f5c2c` et `d656b7ea...b1d12` concordants |
| listener applicatif `8087` après qualification | 0 |
| conteneur Testcontainers résiduel | 0 |
| appel fournisseur, receiver réel, VPS ou livraison réelle | 0 |

Les avertissements Hikari de validation de connexions déjà fermées sont apparus pendant le cycle
de destruction des conteneurs ; ils n'ont produit ni échec ni ressource résiduelle. Les deux suites
se terminent par `BUILD SUCCESS`.

## 8. Verdict final local

```text
REPORT_STATUS=VALIDATED
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
PORT_RANGE_GUARDS_QUALIFIED=YES
BEFORE_CLAIM_INVARIANT_QUALIFIED=YES
WO027_BYTE_IDENTITY_QUALIFIED=YES
PR20_MERGED=YES
PR20_MERGE_COMMIT=cb7c8efe72bd2ef4a5d0f9aab4ae72d4e75453bc
PR21_REVIEW_THREAD_RESOLVED=NO
PR21_MERGED=NO
PR21_NEXT_ACTION=PUSH_RESOLVE_MERGE
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Les deux champs PR #21 restent volontairement à `NO` dans la preuve locale : la publication du
tip qualifié, la résolution de `discussion_r3903674609` et la fusion avec garde de commit exact
sont les actions GitHub suivantes, sans amendement, rebase, squash ni force-push.
