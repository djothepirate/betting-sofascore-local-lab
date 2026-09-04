# J9 — Qualification WO-038 de la sémantique temporelle de l’accusé J7

## 1. Identité de la preuve

```text
REPORT_ID=J9-WO038-J7-ACK-RECEIPT-TIME-SEMANTICS-QUALIFICATION-20260903
WORK_ORDER=WO-SS-20260903-038-j9-j7-ack-receipt-time-semantics
BASE_COMMIT=4cbcb1eb48344b153cd8d8f392aa805feb84ea32
OPENING_COMMIT=2b95e3885cad2f8729d9e429192c7a5171bb80a1
IMPLEMENTATION_COMMIT=3a0c297a5151c572417b4f2f12bb5c3ed216172f
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
```

Ce rapport ne contient pas sa propre empreinte. Son SHA-256 est calculé après finalisation puis
consigné dans les documents qui le référencent et dans le bloc soumis au propriétaire.

## 2. Autorité et périmètre

Le propriétaire a autorisé le diagnostic, la correction et la qualification locale ou hors ligne
de la sémantique temporelle des accusés J7. La qualification loopback permise reste strictement
synthétique. Le receiver INT-001, son runtime et son dépôt ne sont ni modifiés ni appelés.

```text
J9_WO038_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO038_SCOPE=DIAGNOSE_CORRECT_AND_QUALIFY_LOCAL_LAB_J7_DUPLICATE_ACK_DURABLE_RECEIPT_TIME_CLASSIFICATION_AND_DISTINCT_SENDER_RECEIVER_CLOCK_BOUNDARIES
J9_WO038_LOOPBACK_AND_OFFLINE_QUALIFICATION_AUTHORIZED=YES
J9_WO038_RECEIVER_CONTRACT_INVARIANT=DUPLICATE_REUSES_INITIAL_REMOTE_IMPORT_ID_AND_RECEIVED_AT
J9_WO038_RECEIVER_RUNTIME_CHANGE_AUTHORIZED=NO
J9_WO038_AUTOMATIC_RETRY_AUTHORIZED=NO
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

WO-038 ne reprend pas WO-036, ne consomme aucune nouvelle autorisation de campagne et ne modifie
ni l’ACK v1.0, ni le transport HTTPS/mTLS, ni les états de livraison. Il n’ajoute aucun retry,
endpoint, scheduler, polling, fallback ou cible réseau.

## 3. Preuve WO-036 préservée

La reprise R2 de WO-036 a établi le défaut : après un import durable, le receiver a renvoyé un
`200/DUPLICATE` conforme en réemployant le `remoteImportId` et le `receivedAt` initiaux, mais le
sender a ordonné cet instant distant contre la fenêtre de sa seconde tentative locale. La
classification résultante était `UNKNOWN_RECONCILIATION_REQUIRED` avec le code impropre
`ACK_HTTP_STATUS_MISMATCH`.

```text
WO036_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_REPORT=docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-STOP-20260903.md
WO036_REPORT_SHA256=219f54f2b429b1eaea04c920e55cc87d33f9c5644697129e6ff96fc9cad19062
WO036_RESUME_MANIFEST_SHA256=6ef4c4fbe8af1027ff06170bea00aa8ca75c3159bcd21134db2598ddd4b18a78
WO036_PROTECTED_DIFF_LINES=0
WO036_RESUME_AFTER_WO038_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
```

Le Work Order, le manifeste consommé et le rapport d’arrêt de WO-036 restent inchangés. Une
validation ultérieure de WO-038 ne constituerait pas une autorisation de reprise.

## 4. Cause et décision technique

`startedAt`, `completedAt` et l’instant de réception HTTP sont produits par l’horloge du Local Lab.
`ack.receivedAt` est produit par l’horloge indépendante du receiver et désigne l’instant de
persistance durable. En l’absence de contrat de synchronisation, aucun ordre entre ces deux
horloges ne peut servir de preuve causale.

| Option examinée | Conséquence factuelle | Décision |
|---|---|---|
| Remplacer ou borner `receivedAt` par un instant local | détruit la preuve exacte déclarée par le receiver | `REJECTED` |
| Introduire une tolérance temporelle fixe | conserve une comparaison inter-horloges non contractualisée et ajoute un seuil arbitraire | `REJECTED` |
| Lever seulement la borne basse pour `DUPLICATE` | laisse `IMPORTED` et la borne haute dépendre d’une horloge étrangère | `REJECTED` |
| Supprimer uniquement l’ordre inter-horloges, conserver la représentation exacte, les paires HTTP/ACK, les corrélations et l’ordre local | respecte le contrat receiver et maintient le comportement fail-closed | `SELECTED` |

Le sender valide un `remoteImportId` UUID non nul et canonique puis le persiste sans substitution.
La réutilisation du même identifiant et du même instant lors d’un duplicate reste un invariant du
receiver ; elle est exercée par le receiver synthétique, pas déduite d’une horloge locale.

## 5. Correction qualifiée

Le commit `3a0c297a5151c572417b4f2f12bb5c3ed216172f` apporte les changements bornés suivants :

- `J7OptionalDeliveryService` reconnaît uniquement les paires `201/IMPORTED` et
  `200/DUPLICATE`, sans comparer l’instant distant à la tentative locale ;
- `J7DeliveryReceivedAtPolicy` exige une valeur exactement persistable : UTC canonique avec
  suffixe `Z`, année ISO non étendue comprise entre `0001` et `9999` incluses et précision maximale
  à la microseconde ;
- le parseur et le modèle refusent les dates non canoniques, trop précises ou hors de cette plage ;
- `JdbcJ7DeliveryLedgerStore` conserve l’ordre local `completedAt >= startedAt`, exige une forme
  d’ACK complète et persiste l’instant receiver exact sans le borner à l’horloge du sender ;
- la migration append-only V30 remplace uniquement le corps du trigger de résultat et ses
  commentaires ; elle conserve l’état `IN_FLIGHT`, la dernière tentative et l’ordre local, tout en
  refusant les sentinelles PostgreSQL infinies et les dates hors de la plage canonique ;
- les scripts J6 de sauvegarde/restauration et de rétention exigent désormais le schéma courant
  V30. Cette mise à jour n’exécute aucune purge et ne qualifie aucune base primaire.

La migration V29 reste byte-identique. Aucune table, colonne, contrainte, ligne historique,
enveloppe J7, propriété mTLS, origine receiver ou classe runtime du receiver n’est modifiée.

## 6. Matrice de classification

| Entrée qualifiée | État sender | Code sûr | Preuve ACK persistée | Retry automatique |
|---|---|---|---|---:|
| `201/IMPORTED` strict, horloge receiver très en retard ou en avance | `DELIVERED` | `HTTP_201_IMPORTED` | instant et identité exacts | 0 |
| `200/DUPLICATE` strict réemployant l’instant durable initial | `DUPLICATE_CONFIRMED` | `HTTP_DUPLICATE_CONFIRMED` | instant, identité et SHA de l’ACK exacts | 0 |
| `200/IMPORTED` ou `201/DUPLICATE` | `UNKNOWN_RECONCILIATION_REQUIRED` | `ACK_HTTP_STATUS_MISMATCH` | aucune | 0 |
| `receivedAt` non canonique ou non exactement persistable | `UNKNOWN_RECONCILIATION_REQUIRED` | `ACK_INVALID_OR_MISMATCHED` | aucune | 0 |
| `completedAt` local antérieur à `startedAt` local | refus du ledger | `INVALID_COMPLETION` ou tentative non active selon l’état | aucune | 0 |

Les classifications existantes d’un ACK hostile, d’un timeout, d’un incident TLS, d’un `3xx`,
d’un autre `4xx` ou d’un `5xx` restent fail-closed et sans relance automatique.

## 7. Compatibilité append-only V29 vers V30

| Propriété vérifiée | Preuve | Résultat |
|---|---|---|
| migration V29 intacte | `13 569` octets ; SHA-256 `49f32e88af2ed0115fa9cc5587913f4f0ed7576d49eb1bd4634a0dd212416bd5` | `PASS` |
| migration V30 bornée | `1 804` octets ; SHA-256 `4fdb5e8ed1865d0b1fb0b028653f5600e70fa96148c4da102e0371b1ad2d2967` | `PASS` |
| historique Flyway V29 | rang, version, description, type, script, checksum et succès identiques avant/après | `PASS` |
| séquence Flyway | base ciblée V29 avec 29 entrées, exactement une migration exécutée, version courante V30 avec 30 entrées | `PASS` |
| données existantes | comptes des trois tables du ledger et empreinte agrégée identiques immédiatement avant/après migration | `PASS` |
| gardes locales | état `IN_FLIGHT`, tentative la plus récente et `completedAt >= startedAt` conservés | `PASS` |
| ordre inter-horloges | les deux comparaisons entre `acknowledgement_received_at` et la fenêtre locale sont absentes de V30 | `PASS` |
| bornes de représentation SQL | `infinity`, `-infinity`, année avant `0001` et année `10000` refusées | `PASS` |
| instant receiver historique | instant distant antérieur d’un jour accepté puis relu exactement | `PASS` |
| immuabilité | mise à jour du résultat append-only refusée après insertion | `PASS` |

Le test d’upgrade crée un ledger prérempli sous V29, capture son historique, ses comptes et son
empreinte, puis migre cette même base isolée vers V30. Il ne remplace pas une migration appliquée et
ne réécrit aucune preuve existante.

## 8. Matrice de qualification

| Couche | Cas principaux | Résultat |
|---|---|---|
| domaine et parsing ACK | valeur absente, offset/non-canonique, nanoseconde et année étendue refusés ; bornes `0001` et `9999` incluses | `PASS` |
| service applicatif | `IMPORTED` avec instants receiver `1900` et `2100`, duplicate historique, mauvaise paire et valeur non persistable | `PASS` |
| service runtime | duplicate confirmé, métadonnées exactes, un `execute`, un `close`, gate rendue idle | `PASS` |
| ledger PostgreSQL | instants receiver indépendants, ordre local conservé et précision non persistable refusée | `PASS` |
| upgrade Flyway V29→V30 | historique, données, trigger, bornes et append-only vérifiés sur base isolée | `PASS` |
| E2E mTLS synthétique | import nominal `201/IMPORTED` ; scénario séparé post-effet inconnu puis relance manuelle `200/DUPLICATE`, deux requêtes pour un seul effet durable | `PASS` |

Dans le scénario duplicate E2E, le ledger conserve exactement le `receivedAt`, le
`remoteImportId` et le SHA-256 de l’ACK. La relance est une action explicite du test ; aucun mécanisme
automatique ne la produit.

## 9. Vérifications consolidées

| Porte | Tests | Échecs | Erreurs | Ignorés | Résultat |
|---|---:|---:|---:|---:|---|
| Surefire | 1136 | 0 | 0 | 5 | `PASS` |
| Failsafe | 89 | 0 | 0 | 0 | `PASS` |
| tests ciblés domaine, service, runtime, ledger, Flyway et E2E | matrice ciblée | 0 | 0 | 0 | `PASS` |

```text
MVNW_CLEAN_VERIFY=PASS
MVNW_INTEGRATION_TESTS_VERIFY=PASS
TARGETED_WO038_TESTS=PASS
DOCKER_COMPOSE_CONFIG_QUIET=PASS
POWERSHELL_SCRIPT_PARSE_ERRORS=0
QUALIFIED_IMPLEMENTATION_TEXT_UTF8_STRICT=23/23
FINAL_DOCUMENT_TEXT_UTF8_STRICT=7/7
FINAL_TEXT_UTF8_BOM_COUNT=0
FINAL_TEXT_NUL_COUNT=0
ADDED_SECRET_PATTERN_HITS=0
WO036_PROTECTED_DIFF_LINES=0
SERVER_ADDRESS_DEFAULT=127.0.0.1
PROVIDER_DEFAULT_FLAGS=BLOCKED
REAL_RECEIVER_DEFAULT_FLAGS=BLOCKED
TESTCONTAINERS_RESIDUALS=0
JAVA_PROCESS_RESIDUALS=0
BROWSER_PROCESS_RESIDUALS=0
GIT_DIFF_CHECK=PASS
```

Les deux commandes Maven ont été exécutées séparément : `mvnw.cmd clean verify`, puis
`mvnw.cmd -Pintegration-tests verify`. Les tests standards et d’intégration n’ont effectué aucun
appel fournisseur ni aucun appel au receiver INT-001.

## 10. Absence d’effets interdits

```text
PROVIDER_CALLS=0
INT001_RUNTIME_CALLS=0
REAL_RECEIVER_CALLS=0
REMOTE_RECEIVER_CALLS=0
PROVIDER_DERIVED_DELIVERIES=0
AUTOMATIC_RETRIES=0
WO036_RESUME_CONSUMED_BY_WO038=NO
LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION
PRIMARY_DATABASE_PURGE=NO
FORBIDDEN_PAYLOAD_OR_ACK_ARTIFACTS=0
```

Le receiver des tests E2E est un serveur mTLS synthétique et éphémère lié au loopback. Les
répétitions éventuelles des suites Maven ne sont pas une campagne WO-036 et ne valent pas une
autorisation réseau.

## 11. Conclusion

Le défaut local est corrigé sans tolérance temporelle, sans substitution de preuve et sans
affaiblissement des validations de l’ACK. Le résultat est `PASS_LOCAL_FAIL_CLOSED` au commit
`3a0c297a5151c572417b4f2f12bb5c3ed216172f`.

```text
WO038_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WO038_LOCAL_READINESS=PASS
WO038_OWNER_REVIEW_REQUIRED=YES
WO038_WORK_ORDER_MOVE_TO_COMPLETED=NO
WO036_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_RESUME_AUTHORIZED=NO
LOCAL_INT001_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

WO-038 reste actif jusqu’à décision propriétaire. Une validation de WO-038 ne reprendra pas
WO-036 : cette reprise exigera une décision distincte et un manifeste neuf lié aux exécutables
qualifiés.
