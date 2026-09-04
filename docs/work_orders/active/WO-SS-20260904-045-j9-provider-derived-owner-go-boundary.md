# WO-SS-20260904-045 — Frontière de go propriétaire pour une livraison J7 fournisseur

- **Statut :** `IN_DEVELOPMENT`
- **Jalon :** après J9 — préalable runtime à une campagne J7 réelle Windows/Windows
- **Ouvert le :** 2026-09-04
- **Ouverture UTC :** `2026-09-04T12:25:59.2434972Z`
- **Ouverture Europe/Paris :** `2026-09-04T14:25:59.2434972+02:00`
- **Branche :** `codex/j9-wo045-provider-derived-owner-go-boundary`
- **Worktree :** `.tmp/w45`
- **Base exacte :** `400900410dfa751521ce387fbadcc4b5ca95a94a`
- **Permission officielle déclarée par le propriétaire :** `EVIDENCED_COMPATIBLE`

## 1. Autorisation propriétaire

Le propriétaire autorise explicitement l'ouverture et l'implémentation de WO-045 :

```text
J9_WO045_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO045_OPEN_WORK_ORDER_AUTHORIZED=YES
J9_WO045_WORK_ORDER=WO-SS-20260904-045-j9-provider-derived-owner-go-boundary
J9_WO045_SCOPE=IMPLEMENT_AND_QUALIFY_DURABLE_EXACT_ATOMIC_ONE_TIME_PROVIDER_DERIVED_OWNER_GO_CONSUMPTION
J9_WO045_BRANCH=codex/j9-wo045-provider-derived-owner-go-boundary
J9_WO045_BASE_COMMIT=400900410dfa751521ce387fbadcc4b5ca95a94a
J9_WO045_OFFLINE_QUALIFICATION_AUTHORIZED=YES
J9_WO045_SYNTHETIC_LOOPBACK_QUALIFICATION_AUTHORIZED=YES
J9_WO045_PROVIDER_DERIVED_REAL_POST_AUTHORIZED=NO
J9_WO045_PROVIDER_NETWORK_AUTHORIZED=NO
J9_WO045_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_WO045_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_WO045_PRODUCTION_AUTHORIZED=NO
J9_WO045_AUTOMATIC_RETRY_AUTHORIZED=NO
J9_WO046_OPENING_AFTER_WO045_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO046_NEW_MANIFEST_BOUND_OWNER_GO_REQUIRED=YES
```

Le go générique reçu avant l'ouverture de WO-045 n'est pas consommé par ce Work Order et ne peut
pas être réutilisé pour WO-046. Le premier POST réel exigera un nouveau manifeste gelé et un
nouveau go propriétaire lié exactement à ce manifeste après validation de WO-045.

## 2. Constat factuel

Au commit de base, la politique vérifie déjà l'activation, le mode `PROVIDER_DERIVED`, l'origine
receiver exacte, les qualifications, le profil mTLS, l'absence de retry, la permission officielle
et l'autorisation de livraison. Elle termine néanmoins toujours ce chemin par
`PROVIDER_OWNER_GO_REQUIRED`, avant lecture du certificat, claim ou socket.

La confirmation UI existante est liée à la session, à l'export, au hash courant et au prochain
ordinal pendant cinq minutes. Elle constitue le geste opérateur local, mais elle ne remplace pas
une autorisation propriétaire de campagne durable, vérifiable après redémarrage et consommable une
seule fois.

## 3. Objectif

WO-045 doit :

1. définir un contrat fermé de go propriétaire lié à un manifeste de campagne déjà gelé ;
2. lier ce contrat au Work Order de campagne, aux commits qualifiés, au receiver, à l'export J7,
   à ses hashes, à sa provenance, à l'origine exacte et à une fenêtre UTC bornée ;
3. persister l'autorisation sans secret ni payload et imposer l'unicité de son identité ;
4. consommer l'autorisation au plus une fois, dans la même transaction que le claim du premier et
   unique ordinal autorisé ;
5. effectuer tous les contrôles de session, phrase, export, provenance et hash avant la
   consommation ;
6. refuser avant factory, certificat et socket tout go absent, divergent, expiré, prématuré,
   consommé, révoqué ou non applicable ;
7. ne pas rendre le go réutilisable après rollback du POST, résultat incertain, erreur HTTP ou
   redémarrage ;
8. conserver la voie synthétique WO-036 et toutes les valeurs par défaut inchangées ;
9. fournir une inspection locale minimisée de l'état du go sans exposer la clé d'idempotence,
   l'empreinte certificat, un chemin privé ou un payload ;
10. qualifier hors ligne et sur loopback synthétique l'atomicité, les courses et tous les refus
    fail-closed, sans effectuer de livraison dérivée fournisseur.

## 4. Contrat de go canonique

`OWNER_GO_DOCUMENT_SHA256` est le SHA-256 hexadécimal minuscule des octets UTF-8, sans BOM, du
préimage ci-dessous. Les lignes sont dans cet ordre exact, séparées par LF (`0x0A`) et le bloc se
termine par un LF. Les UUID sont canoniques en minuscules, les entiers sont décimaux, les booléens
sont exclusivement `YES` ou `NO`, l'URI est sa représentation ASCII et les deux instants UTC ont
exactement six chiffres de microsecondes. `OWNER_GO_DOCUMENT_SHA256` est délibérément absent du
préimage afin d'éviter toute autoréférence. Aucun alias de clé n'est accepté.

Le bloc qu'un futur WO-046 devra geler est exactement :

```text
FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V1
GO_ID=<uuid>
WORK_ORDER=WO-SS-20260904-046-j9-j7-real-local-e2e-campaign
CAMPAIGN_MANIFEST_REFERENCE=docs/validation/<manifest gelé>.md
CAMPAIGN_MANIFEST_SHA256=<sha256>
LOCAL_LAB_COMMIT=<sha1>
RECEIVER_COMMIT=<sha1>
OFFICIAL_PERMISSION_EVIDENCE_REFERENCE=docs/validation/<preuve expurgée>.md
OFFICIAL_PERMISSION_EVIDENCE_SHA256=<sha256 non sensible>
OFFICIAL_PERMISSION_STATUS=EVIDENCED_COMPATIBLE
RECEIVER_QUALIFICATION=PASS
SENDER_QUALIFICATION=PASS
EXECUTION_ACTOR=CODEX_LOCAL_UI
CANONICAL_EVENT_ID=<uuid>
PROVIDER_EVENT_ID=<identifiant numérique>
EXPORT_ID=<uuid>
FILE_SHA256=<sha256>
DATA_SHA256=<sha256>
FILE_SIZE_BYTES=<1..5242880>
SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
SCHEMA_VERSION=1.0.0
RECEIVER_ORIGIN=https://127.0.0.1:8444
CLIENT_CERTIFICATE_SHA256=<empreinte publique SHA-256 exacte>
EXPECTED_ATTEMPT_NUMBER=1
MAXIMUM_DIRECT_IMPORT_CALLS=1
VALID_FROM=<instant UTC à six chiffres de microsecondes>
VALID_UNTIL=<instant UTC ultérieur à six chiffres de microsecondes>
OWNER_DECISION=GRANT
GO_USE=ONE_TIME
PAYLOAD_CLASS=PROVIDER_DERIVED
VALIDATION_STATUS=HUMAN_VALIDATED
PROVIDER_DERIVED_REAL_POST_AUTHORIZED=YES
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
AUTOMATIC_RETRY_AUTHORIZED=NO
```

Après calcul, `OWNER_GO_DOCUMENT_SHA256=<sha256 du préimage exact>` accompagne séparément le bloc
et forme avec `GO_ID` la référence durable. Java recalcule cette empreinte avant l'enregistrement
et avant le claim ; PostgreSQL la recalcule également dans la garde d'insertion. La fenêtre
`[VALID_FROM, VALID_UNTIL)` doit être strictement positive et ne pas dépasser 60 minutes.

Les identités immuables d'autorisation et de consommation doivent être append-only. Aucun contenu
du J7, ACK brut, secret, certificat, mot de passe ou réponse officielle confidentielle ne doit être
persisté dans cette frontière.

## 5. Atomicité et ordre obligatoire

L'ordre logique attendu est :

```text
frontière navigateur locale
→ jeton formulaire à usage unique
→ demande de confirmation liée à la session
→ phrase exacte et accusé explicite
→ relecture du candidat J7 et de son prochain ordinal
→ relecture byte-for-byte de l'artefact HUMAN_VALIDATED
→ concordance provenance/hashes/manifeste/go
→ claim du ledger + consommation du go dans une transaction unique
→ ouverture du transport possédé
→ au plus un POST
→ résultat terminal ou UNKNOWN_RECONCILIATION_REQUIRED
```

Une erreur avant le claim atomique ne consomme pas le go. À partir du claim atomique, le go est
consommé définitivement, y compris lorsque la livraison se termine par rejet, timeout, TLS,
réponse hostile ou résultat inconnu. Une réconciliation ne contacte jamais le receiver et ne crée
jamais une seconde consommation.

## 6. Périmètre autorisé

Sont autorisés :

- modèle, port applicatif et adaptateur PostgreSQL de la frontière de go ;
- migration Flyway additive suivante après vérification de disponibilité ;
- intégration minimale à la politique, au service de livraison, au ledger et à la composition
  Spring ;
- représentation UI locale expurgée si nécessaire à la qualification ;
- tests unitaires, PostgreSQL/Testcontainers, concurrence et loopback synthétique ;
- documentation d'architecture, sécurité, runbook, rapport, README et changelog.

Sont interdits :

- tout POST contenant un export fournisseur ;
- tout appel SofaScore J3, J4 ou J5 ;
- tout receiver distant ou VPS ;
- toute exposition autre que `127.0.0.1` ;
- retry automatique, redirection, scheduler, polling ou live ;
- modification du contrat J7, du contrat HTTP receiver ou d'INT-001 ;
- secret, certificat privé, payload J7 ou ACK brut dans Git ou les journaux ;
- purge de la base primaire ;
- ouverture de WO-046, push ou fusion sans décision distincte.

## 7. Invariants

```text
DEFAULT_DELIVERY_ENABLED=false
DEFAULT_EXECUTION_MODE=DISABLED
DEFAULT_REMOTE_DELIVERY_AUTHORIZED=false
DEFAULT_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
DEFAULT_PROVIDER_OWNER_GO=ABSENT
OWNER_GO_MAXIMUM_USES=1
OWNER_GO_EXPECTED_ATTEMPT_NUMBER=1
OWNER_GO_MAXIMUM_WINDOW=60m
OWNER_GO_CONSUMPTION_AND_DELIVERY_CLAIM=ONE_DATABASE_TRANSACTION
OWNER_GO_CONSUMED_BEFORE_SOCKET=YES
OWNER_GO_REUSABLE_AFTER_NETWORK_FAILURE=NO
OWNER_GO_REUSABLE_AFTER_PROCESS_RESTART=NO
CONFIRMATION_RUNTIME_CAPABILITY=EXACT_INSTANCE_ONE_TIME_IN_MEMORY
FORGED_OR_COPIED_CONFIRMATION_RECEIPT=REJECTED_BEFORE_EXPORT_OR_GRANT
MIXED_OR_UNKNOWN_ALLOWED=NO
AUTOMATIC_RETRY=0
MAXIMUM_CONCURRENCY=1
REDIRECTS=NEVER
PROVIDER_ACQUISITION_TRIGGERED_BY_DELIVERY=NO
SERVER_ADDRESS=127.0.0.1
```

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY` restent inchangés.

## 8. Qualification obligatoire

- defaults et absence de bean ou accès DB supplémentaire lorsque la livraison est désactivée ;
- migration additive, contraintes SQL, triggers append-only et restauration PostgreSQL ;
- go valide consommé exactement une fois avec le claim correspondant ;
- double soumission et concurrence : un seul gagnant, aucune deuxième tentative ni socket ;
- absence, format invalide, mauvais manifeste, commit, receiver, export, hash, taille, provenance,
  statut ou origine : refus avant consommation et avant transport ;
- fenêtre future, expirée, inversée ou trop large : refus fail-closed ;
- go révoqué ou déjà consommé : refus avant transport ;
- confirmation/session/ordinal invalides : refus sans consommation ;
- échec transactionnel : ni consommation seule, ni claim seul ;
- après claim : tout résultat consomme définitivement le go et aucun retry n'est produit ;
- `SYNTHETIC_ONLY` conserve la sémantique qualifiée WO-036 sans exiger ce go ;
- `MIXED_OR_UNKNOWN` reste refusé ;
- `mvnw.cmd clean verify` et `mvnw.cmd -Pintegration-tests verify` ;
- vérifications UTF-8, secrets, `git diff --check`, loopback, flags bloquants et absence de réseau
  réel.

## 9. État initial

```text
WO045_STATUS=IN_DEVELOPMENT
WO045_BASE_COMMIT=400900410dfa751521ce387fbadcc4b5ca95a94a
WO045_IMPLEMENTATION_COMMIT=NOT_CREATED
WO045_QUALIFICATION_RESULT=NOT_RUN
WO045_PROVIDER_DERIVED_REAL_POSTS=0
WO045_PROVIDER_CALLS=0
WO045_REMOTE_RECEIVER_CALLS=0
WO045_VPS_CONNECTIONS=0
PRE_WO045_OWNER_GO=RECEIVED_NOT_CONSUMED_NOT_REUSABLE_FOR_WO046
WO046_OPENING=NOT_AUTHORIZED
WO046_MANIFEST=NOT_CREATED
WO046_OWNER_GO=NOT_GRANTED
```

## 10. Porte de revue propriétaire

Après implémentation et qualification, le bloc suivant sera soumis prérempli uniquement avec les
preuves calculées :

```text
J9_WO045_OWNER_REVIEW_DECISION=<VALIDATE|REJECT>
J9_WO045_WORK_ORDER=WO-SS-20260904-045-j9-provider-derived-owner-go-boundary
J9_WO045_IMPLEMENTATION_COMMIT=<commit>
J9_WO045_QUALIFICATION_RESULT=<PASS_LOCAL_FAIL_CLOSED|FAIL>
J9_WO045_QUALIFICATION_REPORT_SHA256=<sha256>
J9_WO045_LOCAL_READINESS_ACKNOWLEDGED=<YES|NO>
J9_WO045_WORK_ORDER_MOVE_TO_COMPLETED=<YES|NO>

J9_WO046_OPENING_AUTHORIZED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_WO046_NEW_MANIFEST_BOUND_OWNER_GO_GRANTED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```
