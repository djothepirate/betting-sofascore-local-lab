# J9 — Qualification WO-039 de la sérialisation HTTP de la sonde de collision

## 1. Identité de la preuve

```text
REPORT_ID=J9-WO039-COLLISION-PROBE-HTTP-SERIALIZATION-QUALIFICATION-20260903
WORK_ORDER=WO-SS-20260903-039-j9-wo036-collision-probe-http-serialization
BASE_COMMIT=277f65f318386c763069b9e1907d34acf3b228ba
OPENING_COMMIT=f4fb713
IMPLEMENTATION_COMMIT_1=90c1354c97f506b8291fedae80b7dc6ed37e2c11
IMPLEMENTATION_COMMIT_2=058c57b05ac4c40e1867af60e76cce6cc2864e67
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
```

Ce rapport ne contient pas sa propre empreinte. Son SHA-256 est calculé après finalisation puis
consigné dans les documents qui le référencent et dans le bloc soumis au propriétaire.

## 2. Autorité et limites

Le propriétaire a autorisé la correction du seul outillage de campagne WO-036, sa qualification
hors ligne et une qualification synthétique contre le receiver INT-001 lié au loopback. Il a
ensuite autorisé explicitement l’exécution hôte strictement bornée nécessaire à cette preuve.

```text
WO036_CAMPAIGN_TOOLING_ONLY=YES
LOCAL_LAB_JAVA_SENDER_CHANGED=NO
RECEIVER_RUNTIME_CHANGED=NO
PROTOCOL_RELAXED=NO
CONTENT_TYPE_CONTRACT_CHANGED=NO
NETWORK_SCOPE=127.0.0.1_ONLY
PROVIDER_CALLS=0
PROVIDER_DERIVED_PAYLOADS=0
REMOTE_RECEIVER_CALLS=0
PRIMARY_DATABASE_TOUCH=NO
PRIMARY_DATABASE_PURGE=NO
WO036_REPLAY_OR_RESUME=NO
```

La qualification WO-039 n’est pas le run R4 de WO-036. Elle ne rend réutilisable aucune claim
consommée et n’autorise ni PR ou validation INT-001, ni réseau fournisseur ou distant, ni VPS ou
production.

## 3. Cause R3 confirmée hors ligne

Le harnais R3 confiait la valeur du média type à `MediaTypeHeaderValue`. La sérialisation .NET
ajoutait un espace après le point-virgule :

```text
application/vnd.betting-project.j7-canonical-event+json; version=1.0
```

Le contrat et le receiver exigent exactement :

```text
application/vnd.betting-project.j7-canonical-event+json;version=1.0
```

La preuve R3 reste immuable et ne contient pas le statut ni le code de son rejet. La
classification `400/INVALID_CONTENT_TYPE` demeure donc une inférence de chemin de code à forte
confiance, et non une observation rétroactive inventée.

## 4. Correction de sérialisation

Le commit `90c1354c97f506b8291fedae80b7dc6ed37e2c11` remplace la construction abstraite de la
sonde par une enveloppe HTTP/1.1 bornée dont les octets sont assemblés explicitement :

- ligne de requête fixe vers `/api/imports/sofascore/j7-canonical-events` ;
- média type exact, présent une fois, sans canonicalisation implicite ;
- en-têtes contractuels uniques et dépourvus de CR/LF injecté ;
- `Content-Length` calculé depuis le tableau d’octets du corps ;
- corps mutant synthétique copié byte pour byte ;
- connexion TLS directe à `127.0.0.1:8444`, certificat serveur épinglé et certificat client issu
  du magasin utilisateur Windows ;
- aucun proxy, redirect, retry, cache, fallback ou cible non loopback ;
- connexion, handshake, écriture et lecture bornés à dix secondes ;
- réponse plafonnée et réduite, après validation, au statut HTTP et à un code ProblemDetail sûr.

La claim est créée avant l’écriture réseau et reste consommée après toute issue postérieure. Le
sender Java, ses états, les migrations, l’enveloppe J7 et le receiver ne sont pas modifiés.

## 5. Qualification hors ligne de la requête

Les tests construisent un export synthétique, modifient uniquement une valeur de métadonnée de
même longueur et vérifient la requête résultante sans l’émettre.

| Propriété | Résultat |
|---|---|
| méthode, route et version HTTP exactes | `PASS` |
| média type contractuel exact, une occurrence | `PASS` |
| variante avec espace absente | `PASS` |
| Host fixé à `127.0.0.1:8444` | `PASS` |
| en-têtes contractuels uniques et corrélés | `PASS` |
| `Content-Length` égal à la taille du corps | `PASS` |
| octets du corps identiques à la mutation | `PASS` |
| hash déclaré égal au hash du corps | `PASS` |
| identité invalide ou injection d’en-tête refusée | `PASS_FAIL_CLOSED` |
| retry, proxy et redirect automatiques | `0` |

Le corpus runtime employé ensuite était entièrement synthétique. Le corps source comptait
`3 089` octets ; son SHA-256 complet était
`8c27f9c06481bd89ad248820ea653a5cc67def52b692da239f70c9245827901e`, le SHA-256 de
`data` était `a177f7df2f53bd0520772aa5720420c85a9697746db9e441f1f5a98ffe195879` et le hash de
provenance synthétique était
`9956fa13648cf26135276bbd3fe718c3aaa69d0a9cdb5c54cfe98e6ee0cc67bf`. Aucun octet du
payload n’est conservé dans Git ou dans ce rapport.

## 6. Exécution hôte bornée

Trois préparations ont échoué avant toute écriture sur la route d’import : composition Docker trop
large, lancement Java via un shim puis affectation PowerShell mal formée. Pour chacune :

```text
IMPORT_ROUTE_POSTS=0
ISOLATED_RESOURCES_RECONCILED=YES
PRIMARY_DATABASE_TOUCHED=NO
```

La quatrième préparation, identifiée de façon privée par le run
`f01cc97a-7e29-4389-a9e2-214de67a82c9`, a démarré uniquement :

- une base PostgreSQL éphémère liée à `127.0.0.1:5433` ;
- le JAR INT-001 inchangé, lié à `127.0.0.1:8444` ;
- une PKI mTLS éphémère hors dépôt, avec confirmation humaine du certificat client.

Un état nominal synthétique a été préchargé dans la base isolée. Une seule requête POST de
collision a ensuite été écrite sur la route d’import. Aucun second POST n’a été tenté.

```text
WO039_IMPORT_ROUTE_POST_COUNT=1
WO039_AUTOMATIC_RETRY_COUNT=0
WO039_MANUAL_REPLAY_COUNT=0
RECEIPT_COUNT=1
PAYLOAD_COUNT=1
OUTBOX_J7_COUNT=1
IMPORTED_AUDIT_COUNT=1
DIVERGENCE_AUDIT_COUNT=1
AUDIT_TOTAL_COUNT=2
DIVERGENCE_REASON=EXPORT_ID_DIVERGENCE
```

Ces postconditions établissent que le receiver a franchi les validations mTLS, route, média type,
en-têtes, schéma et hashes, puis a refusé le contenu divergent sans créer un second receipt,
payload ou outbox. Elles constituent la preuve durable de la sérialisation effectivement acceptée
sur le fil et de l’unique effet terminal attendu.

## 7. Défaut secondaire de lecture de réponse

Après l’effet terminal durable, le client a rejeté la ligne de statut avant de retourner sa preuve
expurgée. Le parseur exigeait une reason phrase non vide après le code. Les serveurs HTTP modernes
peuvent émettre la forme légitime suivante :

```text
HTTP/1.1 409
```

Le receiver et le corps de réponse n’ayant pas été modifiés, l’absence de reason phrase est la
cause déduite la plus étroite cohérente avec le point d’échec. Les octets bruts de la réponse
n’ayant volontairement pas été persistés, cette attribution est explicitement une inférence et non
une citation de payload runtime.

Le commit `058c57b05ac4c40e1867af60e76cce6cc2864e67` accepte désormais soit le code seul, soit un
code suivi d’une reason phrase ASCII visible bornée. Une séparation vide, une version différente,
un code hors plage ou une ligne surdimensionnée reste refusé fail-closed.

La requête déjà consommée n’a pas été rejouée. La classification est qualifiée sur un flux mémoire
byte-exact contenant `HTTP/1.1 409`, le ProblemDetail synthétique sûr et les bornes réelles du
parseur.

## 8. Résultats des tests

| Porte | Tests | Échecs | Erreurs | Ignorés | Résultat |
|---|---:|---:|---:|---:|---|
| Pester WO-036/WO-039 | 47 | 0 | 0 | 0 | `PASS` |
| Surefire, `clean verify` | 1 136 | 0 | 0 | 5 | `PASS` |
| Failsafe, `clean verify` | 89 | 0 | 0 | 0 | `PASS` |
| Surefire, profil `integration-tests` | 1 136 | 0 | 0 | 5 | `PASS` |
| Failsafe, profil `integration-tests` | 89 | 0 | 0 | 0 | `PASS` |

Les cas Pester couvrent notamment la requête exacte, la conservation byte-identique du corps, les
hashes, les identités hostiles, la réponse à longueur fixe, la réponse chunked, la ligne de statut
sans reason phrase, le rejet d’un séparateur vide, les en-têtes dupliqués, les codes de problème
hostiles et la corrélation d’audit expurgée.

```text
MVNW_CLEAN_VERIFY=PASS
MVNW_INTEGRATION_TESTS_VERIFY=PASS
PESTER_WO036_WO039=PASS_47_OF_47
DOCKER_COMPOSE_CONFIG_QUIET=PASS
GIT_DIFF_CHECK=PASS
SERVER_ADDRESS_DEFAULT=127.0.0.1
PROVIDER_DEFAULT_FLAGS=BLOCKED
REAL_RECEIVER_DEFAULT_FLAGS=BLOCKED
```

Les suites Maven utilisent uniquement leurs doubles locaux et leurs bases Testcontainers isolées.
Elles ne constituent ni un rejeu WO-036 ni un appel au receiver INT-001 réel.

## 9. Cleanup et intégrité de l’hôte

Après lecture des seuls compteurs et motifs expurgés nécessaires à la preuve, les processus, le
conteneur, le volume, le réseau, la PKI et la racine privée WO-039 ont été supprimés de façon
ciblée.

```text
WO039_DOCKER_RESIDUAL_COUNT=0
WO039_JAVA_PROCESS_RESIDUAL_COUNT=0
LISTENER_127_0_0_1_5433_COUNT=0
LISTENER_127_0_0_1_8444_COUNT=0
PRIMARY_CONTAINER_NAME=betting-sofascore-local-lab-postgres
PRIMARY_CONTAINER_ID=e7b218117df39b7cbfbffb3b1223647568f149912a84592e506b5690f9686c1f
PRIMARY_CONTAINER_STATE=running
PRIMARY_CONTAINER_HEALTH=healthy
PRIMARY_LISTENER=127.0.0.1:5432
PRIMARY_DATABASE_TOUCHED=NO
PRIMARY_DATABASE_PURGED=NO
```

Aucun HAR, trace, vidéo, capture, téléchargement, `storageState`, certificat privé, payload, ACK
brut, cookie, jeton ou secret n’est conservé.

## 10. Conclusion et portes maintenues

WO-039 atteint `PASS_LOCAL_FAIL_CLOSED` : les octets contractuels de la sonde sont maîtrisés, un
unique POST synthétique a produit l’audit durable de divergence attendu sans effet métier
supplémentaire, et le défaut secondaire de ligne de statut est corrigé et qualifié hors ligne sans
rejouer la requête consommée.

```text
WO039_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WO039_LOCAL_READINESS=PASS
WO039_OWNER_REVIEW_REQUIRED=YES
WO039_WORK_ORDER_MOVE_TO_COMPLETED=NO
WO036_STATUS=STOPPED_AFTER_CONSUMED_COLLISION_PROBE_PENDING_WO039
WO036_RESUME_AUTHORIZED=NO
WO036_NEXT_FRESH_RUN=R4
WO036_R4_MANIFEST=REQUIRED_NEW_AND_FROZEN_BEFORE_FIRST_POST
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Une validation propriétaire de WO-039 n’autoriserait pas la reprise de WO-036. R4 exige toujours
une décision propriétaire séparée, une campagne neuve et un manifeste neuf gelé avant son premier
POST.
