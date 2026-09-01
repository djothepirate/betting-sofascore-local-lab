# J9 — readiness de la campagne autonome ADR-SS-002 v1.1

## 1. Portée et conclusion

Cette preuve qualifie uniquement la préparation locale de WO-023 après validation de WO-024. Elle
n'autorise aucun appel fournisseur, aucune campagne, aucun go, aucune intégration et aucune
production. Les parcours Playwright ont utilisé des serveurs éphémères liés à `127.0.0.1`.

```text
WORK_ORDER=WO-SS-20260831-023-j9-provider-robustness-v11
BRANCH=codex/j9-provider-robustness-v11
READINESS_CODE_COMMIT=8b91bf86c624ebff0ddf5c4c9bf454e83142b578
WO024_VALIDATED_COMMIT=8b91bf86c624ebff0ddf5c4c9bf454e83142b578
READINESS_RESULT=PASS
READINESS_RECORDED_AT_UTC=2026-08-31T19:10:44Z
PROVIDER_ACCESS_PERFORMED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
CAMPAIGN_EXECUTION_AUTHORIZED=NO
GLOBAL_OWNER_GO=NOT_GRANTED
```

## 2. Vérifications générales

| Vérification | Résultat factuel |
|---|---|
| `mvnw.cmd --offline clean verify` | `945` tests, `0` échec, `0` erreur, `4` ignorés ; fin `2026-08-31T18:46:04Z` |
| `mvnw.cmd --offline -Pintegration-tests verify` | `945/0/0/4` puis `67/0/0/0` ; PostgreSQL 18.4, Flyway V28 ; fin `2026-08-31T18:47:41Z` |
| `Verify-Local.ps1 -WithIntegrationTests` | `VERIFY_RESULT=PASS`, intégrations `YES`, accès SofaScore `NO` ; fin `2026-08-31T18:50:40Z` |
| Compose | `docker compose --env-file .env config --quiet` : code retour `0` |
| Tests ciblés runtime | superviseur `40/0/0/0`, coordinateur `8/0/0/0` |
| Configuration versionnée | adresse loopback et quatorze invariants réseau/cadence trouvés une fois chacun |
| Environnement du processus | zéro flag réseau dangereux et zéro origine/allowlist fournisseur injectée |
| Vérification finale du lot documentaire | `mvnw.cmd --offline clean verify` : `945/0/0/4`, fin `2026-08-31T19:19:32Z` |

Le premier lancement J3 dans le profil de sandbox s'est arrêté avant les tests car Maven y voyait
un dépôt local remappé et vide, puis Central était interdit. Un second essai hors ligne dans ce même
profil a confirmé l'absence du parent POM dans ce cache isolé. Aucun des deux essais n'a démarré
Playwright ni accédé au fournisseur. Les qualifications réussies ont été relancées dans le contexte
hôte, avec le dépôt Maven réel et `MAVEN_ARGS=--offline`.

## 3. Qualifications Playwright loopback

Le cache Chromium existant et complet de WO-019 a été passé par `-BrowserCachePath`. Aucune
installation et aucun téléchargement n'ont eu lieu. Chaque script a exécuté successivement les
tests worker (`10` protocole, `1` sécurité, `10` observation réseau), puis `14` tests Chromium.

| Parcours | Worker | Chromium | Fin UTC | Marqueurs complémentaires |
|---|---:|---:|---|---|
| J3 | `21/0/0/0` | `14/0/0/0` | `2026-08-31T18:59:38Z` | rapports sensibles `NO`, origine loopback, fournisseur `NO` |
| J4 | `21/0/0/0` | `14/0/0/0` | `2026-08-31T19:02:38Z` | route détails `PASS`, rapports sensibles `NO`, fournisseur `NO` |
| J5 | `21/0/0/0` | `14/0/0/0` | `2026-08-31T19:05:12Z` | routes J5 `PASS`, scanner Maven et canari runtime `PASS`, fournisseur `NO` |

J5 a rendu exactement :

```text
MINIMUM_PERSISTED_NETWORK_START_GAP_MS=PASS_GE_3000
MINIMUM_LOOPBACK_SERVER_ARRIVAL_GAP_NS=PASS_GE_3000000000
J5_REQUESTED_AT_GAPS=PASS_GE_3000_MS
J5_LOOPBACK_ARRIVAL_GAPS=PASS_GE_3000000000_NS
CROSS_WORKER_REQUESTED_AT_GAP=PASS_GE_3000_MS
CROSS_WORKER_LOOPBACK_ARRIVAL_GAP=PASS_GE_3000000000_NS
STOP_DURING_DELAY_NEW_REQUEST_COUNT=0
MAVEN_REPORT_SENSITIVE_SCANNER=PASS_SCANNER_PARITY_ENCODED_TEXT_ATTRIBUTES_PROPERTIES
RUNTIME_FILE_CANARY_SCAN=PASS_ISOLATED_WRITABLE_ROOTS_PER_RUN_RANDOM_CANARY
SENSITIVE_DATA_IN_TEST_REPORTS=NO
ORIGIN=http://127.0.0.1:<ephemeral>
PROVIDER_ACCESS_PERFORMED=NO
```

Les huit tests du coordinateur couvrent notamment la cadence commune J3/J4/J5, le seuil exact de
trois secondes, la réévaluation des réveils anticipés, l'exclusion entre campagnes et l'interruption
fail-closed. Les quarante tests du superviseur couvrent notamment les fences inter-worker, la perte
de preuve temporelle, l'arrêt pendant délai, la fermeture gracieuse et le nettoyage d'arbre.

## 4. Ledgers et ancien go

Les deux ledgers de campagne sont distincts du nombre total historique de lignes J8 dans la base :
la nouvelle série démarre à `0/38` et le cumul de décision J9 à `20/58`. Les quatre frontières sont
rejetées avant claim par l'arithmétique fermée du manifeste :

| Cas | Calcul | Résultat |
|---|---:|---|
| tentative unitaire après nouvelle série pleine | `38 + 1 = 39 > 38` | `REJECTED` |
| sous-campagne J5 après `36` | `36 + 3 = 39 > 38` | `REJECTED` |
| tentative unitaire après cumul d'audit plein | `58 + 1 = 59 > 58` | `REJECTED` |
| sous-campagne J5 après cumul `56` | `56 + 3 = 59 > 58` | `REJECTED` |

Le go v1.0 est `CONSUMED_AND_TERMINATED_BY_STOP`; ADR-SS-002 v1.1 et WO-023 interdisent sa
réutilisation. Aucun go v1.1 n'existe encore.

```text
NEW_SERIES_LEDGER_38_PLUS_1=REJECTED
NEW_SERIES_LEDGER_36_PLUS_3=REJECTED
AUDIT_CUMULATIVE_LEDGER_58_PLUS_1=REJECTED
AUDIT_CUMULATIVE_LEDGER_56_PLUS_3=REJECTED
OLD_GLOBAL_OWNER_GO_REUSE=REJECTED
```

## 5. État primaire et nettoyage

La lecture locale en fin de readiness a établi :

```text
PERSISTED_CONNECTOR_CONTROL=SAFE
PRIMARY_PROVIDER_ATTEMPT_ROW_COUNT=116
LATEST_PRIMARY_PROVIDER_ATTEMPT_STARTED_AT=2026-08-31T07:59:01.092327Z
FLYWAY_SCHEMA_VERSION=28
LISTENER_8087_COUNT=0
OWNED_PLAYWRIGHT_PROCESS_COUNT=0
RUNTIME_SANDBOX_RESIDUAL=NO
FORBIDDEN_VERSIONABLE_BROWSER_ARTIFACT_COUNT=0
GIT_STATUS_ENTRY_COUNT_BEFORE_DOCUMENTATION=0
```

La dernière ligne fournisseur précède de plus de dix heures l'ensemble des commandes de readiness :
aucune tentative primaire n'a été inscrite pendant ces qualifications. Les scripts J3/J4/J5 ont
également rendu explicitement `PROVIDER_ACCESS_PERFORMED=NO`.

## 6. Hashes des rapports locaux au constat

Ces fichiers `target` restent ignorés et ne sont pas ajoutés à Git. Les hashes permettent seulement
de rattacher les comptes lus au constat local ; aucun rapport ne contient de payload fournisseur ou
de secret.

| Rapport | Compte | SHA-256 |
|---|---:|---|
| superviseur enfant | `40/0/0/0` | `444425545646d68edf9a525a5f90b9816cd33a9f39d6afda97c872b6de26c07e` |
| coordinateur manuel | `8/0/0/0` | `16b4135923bafd69e365b1daff336e990f7907ed678b5a3234b82e7520ee7154` |
| protocole worker | `10/0/0/0` | `cd2838bbaf1ace5a75155504af4c632ef1dc64e626f253538800464166f03af4` |
| sécurité worker | `1/0/0/0` | `fa8ae028e1cb816571a1e779be1b6d55b6889a48a6c002d6fe43fee99b59bec4` |
| observation réseau worker | `10/0/0/0` | `8c881a7d3b36c57b81e971552bc5975280dc6fd0e041606eb2960eba7ea9242f` |
| qualification Chromium J5 finale | `14/0/0/0` | `5b9feba68b0d4f47ecb08a1a576bbf892e5962980892928fcbb6cdf65b364d1f` |

## 7. Porte suivante

```text
WORK_ORDER_STATUS=READY_FOR_V28_BACKUP_RESTORE
WO023_BACKUP_RETRY_AUTHORIZED_NOW=YES_WITH_AGE_AUTOGENERATED_PASSPHRASE
POST_STOP_V28_BACKUP_RESTORE=AUTHORIZED_NOT_EXECUTED
MANIFEST_STATUS=NOT_CREATED
GLOBAL_OWNER_GO=NOT_GRANTED
OWNER_GO_CONSUMED=NO
NEW_SERIES_DIRECT_ATTEMPTS=0
AUDIT_CUMULATIVE_DIRECT_ATTEMPTS=20
WO023_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
NEW_GLOBAL_OWNER_GO_GRANTED=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
```
