# WO-SS-20260904-046 — Campagne E2E J7 réelle locale Windows/Windows

- **Statut :** `PAUSED_PENDING_MTLS_IDENTITY_PROVISIONING_DECISION`
- **Jalon :** après J9 — campagne locale d'une livraison J7 dérivée fournisseur
- **Ouvert le :** 2026-09-04
- **Ouverture UTC :** `2026-09-04T13:59:22.9943521Z`
- **Ouverture Europe/Paris :** `2026-09-04T15:59:22.9943521+02:00`
- **Branche :** `codex/j9-wo046-j7-real-local-e2e-campaign`
- **Worktree :** `.tmp/w46`
- **Base exacte :** `8a1225fc4b85d8e8b55af536fcbc7955676131ff`
- **Préalable runtime validé :** `WO-SS-20260904-045-j9-provider-derived-owner-go-boundary`

## 1. Décision propriétaire d'ouverture

Le propriétaire valide WO-045 et autorise séparément l'ouverture de WO-046, sans autoriser la
campagne, le manifeste, le go propriétaire ou un POST réel :

```text
J9_WO045_OWNER_REVIEW_DECISION=VALIDATE
J9_WO045_WORK_ORDER=WO-SS-20260904-045-j9-provider-derived-owner-go-boundary
J9_WO045_IMPLEMENTATION_COMMIT=67467d5dbd63fa54d11b2d1cd701a31edf4454e0
J9_WO045_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
J9_WO045_QUALIFICATION_REPORT_SHA256=5146c18542f0376a60bac5d4a25b68ff3f2fec9d7da700b32ee0aac60e16d946
J9_WO045_LOCAL_READINESS_ACKNOWLEDGED=YES
J9_WO045_WORK_ORDER_MOVE_TO_COMPLETED=YES

J9_WO046_OPENING_AUTHORIZED=YES
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_WO046_NEW_MANIFEST_BOUND_OWNER_GO_GRANTED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Cette décision est consommée uniquement pour créer ce Work Order, sa branche et son worktree. Elle
ne vaut pas autorisation de préparer ou geler le manifeste, d'enregistrer un grant durable, de
démarrer un receiver, de lire un certificat privé, d'ouvrir une socket ou d'émettre un POST.

## 2. Objectif futur de la campagne

Après franchissement de toutes les portes, WO-046 devra qualifier sur le poste Windows une unique
livraison manuelle d'un export J7 réel déjà `HUMAN_VALIDATED` vers le receiver Betting Project
lié à `https://127.0.0.1:8444` :

```text
Local Lab 127.0.0.1:8087
→ action opérateur distincte « Livrer »
→ confirmation exacte et à usage unique
→ claim atomique + consommation durable du go
→ un POST HTTPS/mTLS au maximum
→ ACK durable corrélé ou état fail-closed
```

La livraison ne doit déclencher aucun appel SofaScore J3, J4 ou J5. Aucun enrichissement métier
du Betting Project n'est attendu dans l'ACK. Aucun retry, scheduler, polling, redirection ou
fallback n'est admis.

## 3. Preuves déjà disponibles

### 3.1 Sender Local Lab

```text
SENDER_WORK_ORDER=WO-SS-20260902-035-j9-real-j7-delivery-sender
SENDER_IMPLEMENTATION_COMMIT=f5a27887b7db43576eb608d564c245c8cca3a602
SENDER_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
SENDER_QUALIFICATION_REPORT=docs/validation/J9-WO035-REAL-J7-DELIVERY-SENDER-QUALIFICATION-20260902.md
SENDER_QUALIFICATION_REPORT_SHA256=1028761cbc68154da780d314935880bf17b803c3cf8a35b1386fd29ab28bae76
SENDER_OWNER_VALIDATED=YES
```

Le futur manifeste devra toutefois référencer le commit Local Lab exact réellement exécuté, qui
inclura nécessairement la clôture validée de WO-045 et l'ouverture de WO-046.

### 3.2 E2E synthétique Windows/Windows

```text
SYNTHETIC_CAMPAIGN_WORK_ORDER=WO-SS-20260902-036-j9-j7-local-e2e-qualification
SYNTHETIC_CAMPAIGN_RESULT=PASS_LOCAL_SYNTHETIC_E2E
SYNTHETIC_CAMPAIGN_REPORT=docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R6-20260903.md
SYNTHETIC_CAMPAIGN_REPORT_SHA256=17a299cef826a4cc8da3fd4ff50a20eec99aee447cc4a0b69fc79914d4ab4280
```

Cette preuve couvre le protocole local synthétique ; elle ne constitue ni une qualification
propriétaire finale du receiver INT-001, ni une autorisation de donnée fournisseur.

### 3.3 Frontière de go propriétaire

```text
OWNER_GO_BOUNDARY_WORK_ORDER=WO-SS-20260904-045-j9-provider-derived-owner-go-boundary
OWNER_GO_BOUNDARY_IMPLEMENTATION_COMMIT=67467d5dbd63fa54d11b2d1cd701a31edf4454e0
OWNER_GO_BOUNDARY_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
OWNER_GO_BOUNDARY_REPORT=docs/validation/J9-WO045-PROVIDER-DERIVED-OWNER-GO-BOUNDARY-QUALIFICATION-20260904.md
OWNER_GO_BOUNDARY_REPORT_SHA256=5146c18542f0376a60bac5d4a25b68ff3f2fec9d7da700b32ee0aac60e16d946
OWNER_GO_BOUNDARY_OWNER_VALIDATED=YES
OWNER_GO_BOUNDARY_CLOSURE_COMMIT=8a1225fc4b85d8e8b55af536fcbc7955676131ff
```

### 3.4 Receiver INT-001 validé par le propriétaire

Le propriétaire a demandé que la validation d'INT-001 constitue la première étape de la séquence.
Elle est maintenant consignée dans le dépôt Betting Project, sans push, PR, merge, démarrage de
receiver ou réseau :

```text
INT001_BRANCH=codex/int-001-j7-receiver
INT001_IMPLEMENTATION_COMMIT=25c0229aac06df9ab380f0dfa77a3e70f34a5632
INT001_OWNER_VALIDATION_COMMIT=de06153f0908a1bb2dc9bbd2c8e22f7fd14dacfd
INT001_IMPLEMENTATION_RESULT=PASS_LOCAL_FAIL_CLOSED
INT001_LOCAL_READINESS_REPORT=docs/reviews/INT-001-local-readiness.md
INT001_LOCAL_READINESS_REPORT_SIZE=14300
INT001_LOCAL_READINESS_REPORT_SHA256=0ca2efb8e9424c2a776b95f9605ca8e5c38a15766a31ff900e09c46161cbcba2
INT001_OWNER_VALIDATION=RECORDED
INT001_STATUS=OWNER_VALIDATED_LOCALLY_QUALIFIED_NOT_PUBLISHED
```

## 4. Réconciliation ordonnée des préconditions à l'ouverture — historique v0.1

L'ouverture ne transforme aucune déclaration ou observation historique en preuve exécutoire. Les
éléments suivants doivent être identifiés par une référence versionnée, un commit exact et une
empreinte avant toute création de manifeste :

| Porte | État à l'ouverture | Preuve exigée avant manifeste |
|---|---|---|
| Receiver INT-001 | `OWNER_VALIDATED_LOCALLY_QUALIFIED_NOT_PUBLISHED` | porte locale satisfaite ; aucune publication ni exécution autorisée |
| Permission officielle | `RECONCILED_NOT_EVIDENCED_BLOCKING` | réponse ou accord applicable, expurgé, versionné, référencé et hashé |
| Export J7 réel | `NOT_SELECTED` | métadonnées d'un export `PROVIDER_DERIVED` et `HUMAN_VALIDATED`, sans octets dans Git |
| Identité mTLS | `NOT_SELECTED_NO_EXISTING_CANDIDATE` | provisionnement distinct puis origine loopback exacte et empreinte publique du certificat client exact |
| Manifeste WO-046 | `NOT_CREATED_NOT_AUTHORIZED` | décision séparée, contenu complet puis commit gelé avant tout POST |
| Go propriétaire WO-046 | `NOT_GRANTED` | bloc canonique lié au manifeste gelé et fenêtre future de 60 minutes maximum |
| Exécution | `NOT_AUTHORIZED` | autorisation explicite du POST réel et go durable enregistré |

La réconciliation exacte est enregistrée dans
`docs/validation/J9-WO046-OFFICIAL-PERMISSION-RECONCILIATION-20260904.md`, taille `7273` octets et
SHA-256 `707e0fd9b07dc0944225be80a590792e5e8ab0631dab964a465335f283ac1473`. Elle distingue la
déclaration propriétaire `EVIDENCED_COMPATIBLE` d'une preuve officielle positive : aucune demande
finale rendue, preuve de soumission, réponse, licence ou convention applicable n'est établie dans
le périmètre inspecté. Le résultat déterministe reste donc `NOT_EVIDENCED`.

Dans l'état historique antérieur à ADR-SS-003 v0.2, la séquence propriétaire s'arrêtait à cette
porte :

```text
ORDERED_STEP_1_INT001_OWNER_VALIDATION=COMPLETE
ORDERED_STEP_2_OFFICIAL_EVIDENCE_RECONCILIATION=COMPLETE_NEGATIVE
ORDERED_STEP_2_POSITIVE_GATE_SATISFIED=NO
ORDERED_STEP_3_EXPORT_SELECTION=NOT_REACHED
ORDERED_STEP_4_MTLS_IDENTITY_SELECTION=NOT_REACHED
ORDERED_STEP_5_MANIFEST_AUTHORIZATION=NOT_CONSUMED
ORDERED_STEP_6_MANIFEST_COMMIT=NOT_CREATED
ORDERED_STEP_7_OWNER_GO_SUBMISSION=NOT_CREATED
ORDERED_STEP_8_REAL_POST_AUTHORIZATION=NOT_GRANTED
```

Deux inventaires strictement en lecture seule ont été effectués sans franchir les étapes 3 et 4 :
un export candidat `PROVIDER_DERIVED` et `HUMAN_VALIDATED` existe, mais n'est pas sélectionné ; le
magasin `CurrentUser\\My` ne contient actuellement aucun certificat, donc aucune identité mTLS
exacte réutilisable ne peut être sélectionnée. Le provisionnement futur de l'identité cliente et
de la confiance receiver exige une autorisation distincte avant le manifeste.

## 5. Manifeste futur obligatoire

Sous une autorisation distincte, le manifeste devra être créé exactement à l'emplacement suivant,
puis commité et gelé avant toute demande de go :

```text
MANIFEST_REFERENCE=docs/validation/J9-WO046-J7-REAL-LOCAL-E2E-CAMPAIGN-MANIFEST-20260904.md
MANIFEST_STATUS=NOT_CREATED
MANIFEST_CREATION_AUTHORIZED=NO
MANIFEST_FROZEN=NO
```

Il devra lier au minimum :

- le commit Local Lab exact et le commit receiver exact exécutés ;
- les rapports et hashes de qualification sender, receiver et frontière owner-go ;
- la preuve d'audit officielle exacte, même négative, et la base de gouvernance ADR-SS-003 v0.2,
  chacune expurgée, versionnée et hashée ;
- l'événement canonique, l'identifiant fournisseur et l'export sélectionné ;
- `exportId`, `fileSha256`, `dataSha256`, taille, schema ID et version ;
- l'origine exacte `https://127.0.0.1:8444` ;
- l'empreinte publique du certificat client exact ;
- l'acteur `CODEX_LOCAL_UI`, l'ordinal `1` et un seul appel direct maximum ;
- l'inventaire initial des listeners, processus, conteneurs et bases détenus ;
- les étapes de cleanup et de postflight sans payload ni secret.

Les octets du J7, le certificat privé, sa phrase secrète, les credentials PostgreSQL, les cookies,
les jetons et les ACK bruts restent hors manifeste, hors documentation et hors Git.

## 6. Go canonique futur

Après le gel du manifeste, un nouveau bloc propriétaire devra reprendre exactement le format
`J7_PROVIDER_DERIVED_OWNER_GO_V2` qualifié par WO-047. V1 reste strictement historique et exige
toujours `EVIDENCED_COMPATIBLE` ; il ne peut pas porter la décision courante. Le futur bloc V2
devra notamment fixer :

```text
FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V2
WORK_ORDER=WO-SS-20260904-046-j9-j7-real-local-e2e-campaign
CAMPAIGN_MANIFEST_REFERENCE=docs/validation/J9-WO046-J7-REAL-LOCAL-E2E-CAMPAIGN-MANIFEST-20260904.md
CAMPAIGN_MANIFEST_SHA256=<sha256 du manifeste gelé>
EXPECTED_ATTEMPT_NUMBER=1
MAXIMUM_DIRECT_IMPORT_CALLS=1
OWNER_DECISION=GRANT
GO_USE=ONE_TIME
PAYLOAD_CLASS=PROVIDER_DERIVED
VALIDATION_STATUS=HUMAN_VALIDATED
PROVIDER_PERMISSION_AUDIT_REFERENCE=docs/validation/J9-WO046-OFFICIAL-PERMISSION-RECONCILIATION-20260904.md
PROVIDER_PERMISSION_AUDIT_SHA256=707e0fd9b07dc0944225be80a590792e5e8ab0631dab964a465335f283ac1473
PROVIDER_PERMISSION_AUDIT_STATUS=NOT_EVIDENCED
J7_TRANSFER_GOVERNANCE_BASIS_REFERENCE=ADR-SS-003-optional-integration-topology.md
J7_TRANSFER_GOVERNANCE_BASIS_COMMIT=e1ec9936467dd570f7ed00c51227c8e7d5a35945
J7_TRANSFER_GOVERNANCE_BASIS_SHA256=ded6a4da8a3161caae491f62919f4f5c3569c69c821be5a807772cc542cede3f
J7_TRANSFER_GOVERNANCE_BASIS_STATUS=ADR_ACCEPTED_NO_EXECUTION_AUTHORITY
PROVIDER_DERIVED_REAL_POST_AUTHORIZED=YES
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
AUTOMATIC_RETRY_AUTHORIZED=NO
OWNER_GO_DOCUMENT_SHA256=<sha256 externe au préimage canonique>
```

Le futur go doit avoir une fenêtre UTC semi-ouverte strictement positive, future au moment de son
enregistrement et d'une durée maximale de 60 minutes. Tout go V1 ou antérieur à WO-047 est non
réutilisable. Aucun go n'est accordé, construit, hashé, enregistré ou consommé par la reprise ou
la sélection hors ligne.

## 7. Exécution future bornée

Une décision ultérieure devra autoriser explicitement l'exécution. Même alors, la campagne devra :

1. confirmer les deux dépôts et les deux bases exactes, sans toucher une base primaire étrangère ;
2. confirmer les listeners exclusifs sur `127.0.0.1:8087` et `127.0.0.1:8444` ;
3. qualifier le certificat serveur et le certificat client exact hors Git ;
4. enregistrer le grant durable exact avant la fenêtre autorisée ;
5. démarrer le receiver local possédé et vérifier sa readiness sans donnée fournisseur ;
6. recharger byte-for-byte l'export `HUMAN_VALIDATED` lié au manifeste ;
7. demander la phrase exacte `LIVRER J7 <exportId> SHA256 <fileSha256>` ;
8. effectuer au maximum un POST HTTPS/mTLS et aucun retry ;
9. vérifier l'ACK, le ledger Local Lab, l'inbox/outbox receiver et les hashes sans journaliser les
   contenus ;
10. réarmer les flags à `false`, arrêter uniquement les ressources possédées et produire un rapport
    expurgé.

Une erreur avant le claim atomique ne crée ni tentative ni consommation. Dès que le claim est
committé, le go est consommé définitivement et toute incertitude impose
`UNKNOWN_RECONCILIATION_REQUIRED`, sans second POST.

## 8. Périmètre autorisé à l'ouverture — historique v0.1

Sont autorisés par la présente décision :

- la création de cette branche et de ce worktree distincts ;
- la création de ce Work Order actif ;
- les contrôles hors ligne et en lecture seule nécessaires pour décrire les portes manquantes ;
- les mises à jour documentaires d'ouverture de `README.md` et `CHANGELOG.md`.

La décision de reprise consignée en section 10 supersède uniquement cette portée historique pour
l'intégration linéaire et les étapes de sélection hors ligne ; les interdictions du manifeste, du
go, du POST et des réseaux restent effectives.

Restent interdits :

- la création ou le gel du manifeste de campagne ;
- la génération, l'enregistrement ou la consommation d'un go réel ;
- le démarrage du Local Lab ou du receiver pour cette campagne ;
- la lecture d'une clé privée ou l'ouverture d'un magasin de certificats ;
- tout POST réel, même sur loopback ;
- tout appel SofaScore J3, J4 ou J5 ;
- tout receiver distant, réseau non-loopback ou accès au VPS ;
- toute production, purge, retry, scheduler, polling, live ou fallback ;
- tout payload, secret, certificat privé ou ACK brut dans Git ;
- tout push, merge ou clôture sans décision séparée.

## 9. État initial effectif

```text
J9_WO046_WORK_ORDER=WO-SS-20260904-046-j9-j7-real-local-e2e-campaign
J9_WO046_BRANCH=codex/j9-wo046-j7-real-local-e2e-campaign
J9_WO046_BASE_COMMIT=8a1225fc4b85d8e8b55af536fcbc7955676131ff
J9_WO046_WORK_ORDER_STATUS=BLOCKED_OFFICIAL_PERMISSION_NOT_EVIDENCED
J9_WO046_OPENING_DECISION_CONSUMED=YES
J9_WO046_DOCUMENTARY_AND_OFFLINE_INVENTORY_AUTHORIZED=YES

J9_WO046_INT001_OWNER_VALIDATION=COMPLETE
J9_WO046_OFFICIAL_PERMISSION_RECONCILIATION=COMPLETE_NEGATIVE
J9_WO046_OFFICIAL_PERMISSION_GATE=BLOCKED
J9_WO046_EXPORT_SELECTION=NOT_REACHED
J9_WO046_MTLS_IDENTITY_SELECTION=NOT_REACHED

J9_WO046_CAMPAIGN_EXECUTION_AUTHORIZED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_WO046_MANIFEST_STATUS=NOT_CREATED
J9_WO046_MANIFEST_CREATION_AUTHORIZED=NO
J9_WO046_MANIFEST_FROZEN=NO
J9_WO046_OWNER_GO_STATUS=NOT_GRANTED
J9_WO046_OWNER_GO_REGISTERED=NO
J9_WO046_OWNER_GO_CONSUMED=NO
J9_WO046_DIRECT_IMPORT_ATTEMPTS=0
J9_WO046_PROVIDER_DERIVED_REAL_POSTS=0
J9_WO046_AUTOMATIC_RETRY_AUTHORIZED=NO

J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_LOCAL_RECEIVER_LOOPBACK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Dans cet état initial v0.1, WO-046 était bloqué avant préparation de campagne et exigeait à tort
une preuve officielle positive. Cette photographie reste conservée pour audit. ADR-SS-003 v0.2,
WO-047 puis la décision propriétaire de reprise la supersèdent pour le seul transfert J7 local ;
l'état gouvernant courant est consigné ci-dessous. Aucun go ni POST ne peut être déduit de cette
supersession.

## 10. Reprise après validation de WO-047

Le propriétaire a autorisé la reprise après la validation et le push de WO-047 :

```text
J9_WO047_FINAL_STATUS=VALIDATED
J9_WO046_STATUS=RESUME
J9_WO046_RESUME_AUTHORIZED=YES
J9_WO046_MANIFEST_CREATED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

La branche WO-046, propre au commit
`0338821cc07130f5d200a70db10209bb898a59ae`, a été avancée exclusivement par fast-forward sur la
clôture WO-047 validée et publiée
`73881ebf2c673d347b63e8be982572e70b6429ae`. Le statut officiel reste exactement
`NOT_EVIDENCED`, mais ADR-SS-003 v0.2 le traite comme un fait d'audit non bloquant pour ce seul
transfert local. `EVIDENCED_INCOMPATIBLE` demeure bloquant et les portes J3/J4/J5 sont inchangées.

La reprise hors ligne est qualifiée dans
`docs/validation/J9-WO046-RESUME-EXPORT-AND-MTLS-SELECTION-20260904.md`, taille `6215` octets et
SHA-256 `18152301fbb4caa49218f7562f2a1ceb35b9e451dd6c6c5b02dc5b845eba574a`.

### 10.1 Étape 3 — sélection des métadonnées J7

Le candidat fournisseur validé le plus récent est sélectionné par ses seules métadonnées :

```text
EXPORT_SELECTION_STATUS=SELECTED_METADATA_ONLY_PENDING_PRIMARY_LEDGER_REVALIDATION
EXPORT_ID=a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc
CANONICAL_EVENT_ID=e2c9599a-2336-3888-ae88-2437dad02b48
PROVIDER_EVENT_ID=16310945
PAYLOAD_CLASS=PROVIDER_DERIVED
VALIDATION_STATUS=HUMAN_VALIDATED
SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
SCHEMA_VERSION=1.0.0
SIZE_BYTES=35663
FILE_SHA256=d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6
DATA_SHA256=f95ae31e711a9433d6d06745d37f2535143266aa97c7d151fd26bea88ef03ac6
WARNING=MISSING_COMPONENT:EVENT_DETAILS
```

La sélection est bornée au fichier local. Le statut et le chemin dans le ledger primaire devront
être revalidés avant le manifeste ; aucun contact avec la base primaire n'a été effectué ici.

### 10.2 Étape 4 — absence d'identité mTLS sélectionnable

L'inventaire des seules métadonnées publiques de `Cert:\CurrentUser\My` retourne zéro certificat.
Aucune identité ne peut être sélectionnée et aucun provisionnement n'a été déduit de la reprise :

```text
CURRENT_USER_MY_CERTIFICATE_COUNT=0
MTLS_IDENTITY_SELECTION_STATUS=NOT_SELECTED_NO_EXISTING_CANDIDATE
CERTIFICATE_PROVISIONING_PERFORMED=NO
PRIVATE_KEY_READ_EXPORT_OR_USE=NO
```

L'état gouvernant devient donc :

```text
J9_WO046_STATUS=PAUSED_PENDING_MTLS_IDENTITY_PROVISIONING_DECISION
ORDERED_STEP_1_INT001_OWNER_VALIDATION=COMPLETE
ORDERED_STEP_2_OFFICIAL_EVIDENCE_RECONCILIATION=COMPLETE_NEGATIVE_AUDIT_ONLY_NON_BLOCKING_FOR_LOCAL_J7_TRANSFER
ORDERED_STEP_3_EXPORT_METADATA_SELECTION=COMPLETE_PENDING_PRIMARY_LEDGER_REVALIDATION
ORDERED_STEP_4_MTLS_IDENTITY_SELECTION=BLOCKED_NO_EXISTING_CANDIDATE
ORDERED_STEP_5_MANIFEST_AUTHORIZATION=NOT_CONSUMED
ORDERED_STEP_6_MANIFEST_COMMIT=NOT_CREATED
ORDERED_STEP_7_OWNER_GO_SUBMISSION=NOT_CREATED
ORDERED_STEP_8_REAL_POST_AUTHORIZATION=NOT_GRANTED
J9_WO046_MANIFEST_CREATION_AUTHORIZED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_WO046_DIRECT_IMPORT_ATTEMPTS=0
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_LOCAL_RECEIVER_LOOPBACK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Le prochain changement d'état possible est le provisionnement borné d'une identité mTLS locale,
sous décision propriétaire distincte. Le manifeste, le go et le POST restent des portes
ultérieures séparées.
