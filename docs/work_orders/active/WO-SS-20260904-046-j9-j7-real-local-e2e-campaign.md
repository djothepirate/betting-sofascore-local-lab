# WO-SS-20260904-046 — Campagne E2E J7 réelle locale Windows/Windows

- **Statut :** `PREPARATION_RESUMED_PENDING_RECEIVER_RESOURCE_DECISION`
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
- la référence logique privée et l'engagement SHA-256 de l'état PKI exact qualifié par WO-048 ;
  les empreintes des certificats restent exclusivement dans le record privé externe et le futur
  owner-go V2 externe, conformément à `PRIVATE_EXTERNAL_RECORD_ONLY` ;
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

Les décisions de reprise consignées en sections 10 et 11 supersèdent cette portée historique pour
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

## 10. Reprise après validation de WO-047 — état historique avant WO-048

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

## 11. Reprise préparatoire après validation de WO-048 — avant autorisation du préflight

Le propriétaire a validé WO-048, reconnu ses tentatives hôte échouées sans résidus, la déviation
Testcontainers et la conservation du run nominal, puis autorisé explicitement la reprise de
WO-046. La décision et le classement de WO-048 sont consignés au commit
`5f7756b17c42d14bc97c7ddd7fcbde616b2241e0`. La branche WO-046, propre au commit
`e771c2a5fedc508fbd0a420ae4596166251d82cd`, a intégré cette clôture par fast-forward exact,
sans réécriture de l'historique et sans modification du receiver.

```text
J9_WO048_OWNER_REVIEW_DECISION=VALIDATE
J9_WO048_LOCAL_READINESS_ACKNOWLEDGED=YES
J9_WO048_WORK_ORDER_MOVE_TO_COMPLETED=YES
J9_WO046_RESUME_AFTER_WO048_VALIDATION=YES
J9_WO046_MANIFEST_CREATION_AUTHORIZED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

### 11.1 Préconditions corroborées sans application ni base

- INT-001 reste propre au commit `de06153f0908a1bb2dc9bbd2c8e22f7fd14dacfd` ; sa validation
  propriétaire est consignée. Son rapport de readiness conserve les `14300` octets et le SHA-256
  `0ca2efb8e9424c2a776b95f9605ca8e5c38a15766a31ff900e09c46161cbcba2`, recalculé à la reprise.
- Le rapport WO-048 reste byte-identique : `12844` octets, SHA-256
  `c704961530020216bfbc644f2fa928357466eccf54ef4e3d80f5705a95ef109d`.
- L'engagement de l'état PKI privé conservé correspond exactement au SHA-256
  `29c53ef4b27cd9782bc49f52b5594152f12d0ca7f96fd7209052ea90f6cc7475`. Ce fichier contient des
  secrets protégés par ACL : seul son engagement est publié, jamais son contenu, son UUID ou ses
  chemins.
- La vérification courante des seules métadonnées publiques retrouve exactement un certificat
  détenu par rôle dans `CurrentUser\\My` et `CurrentUser\\Root`, avec concordance privée du
  thumbprint, du SHA-256 DER et du sujet. Les deux certificats sont temporellement valides à ce
  contrôle. La vérification ne lit, n'exporte ni n'utilise aucune clé privée et ne réalise aucune
  mutation, socket ou handshake. La validité devra être recontrôlée avant l'utilisation future.
- La preuve officielle et la sélection des métadonnées de l'export restent celles des rapports
  immuables des sections 4 et 10.1. `NOT_EVIDENCED` est une métadonnée d'audit non bloquante
  pour ce seul transfert local sous ADR-SS-003 v0.2 ; aucun accord SofaScore n'est affirmé et
  `EVIDENCED_INCOMPATIBLE` demeure bloquant.

### 11.2 Portes restantes et portée de la reprise

| Porte | État courant | Action restante |
|---|---|---|
| Receiver INT-001 | `OWNER_VALIDATED_LOCALLY_QUALIFIED_NOT_PUBLISHED` | épingler le commit réellement exécuté dans un futur manifeste autorisé |
| Identités mTLS | `SELECTED_RUN_BOUND_WO048_VALIDATED_PUBLIC_METADATA_REVALIDATED` | garder le record privé exact et recontrôler la validité avant utilisation |
| Export sélectionné | `SELECTED_METADATA_ONLY_PENDING_PRIMARY_LEDGER_REVALIDATION` | vérifier les seules métadonnées dans le ledger primaire sous autorisation distincte |
| Schéma primaire courant | `NOT_VERIFIED_IN_THIS_RESUME` | préflight primaire en lecture seule à autoriser ; aucune version actuelle déduite des migrations du dépôt |
| Migration primaire V32 et sauvegarde/restauration | `PENDING_SEPARATE_OWNER_DECISION` | appliquer la porte réservée par WO-047 section 3.10, sans purge primaire |
| Manifeste | `NOT_CREATED_NOT_AUTHORIZED` | décision séparée après satisfaction des prérequis, puis commit gelé |
| Owner-go V2 | `NOT_CREATED_NOT_GRANTED` | soumettre un nouveau bloc lié au manifeste gelé et au record PKI privé |
| POST réel | `NOT_AUTHORIZED` | autorisation distincte future et à usage unique |

La présente reprise n'autorise aucun accès à la base primaire, migration, sauvegarde/restauration,
démarrage d'application ou de base, ni appel receiver. La prochaine décision à demander concerne
le préflight primaire strictement en lecture seule : schéma Flyway effectif et concordance de
l'export déjà sélectionné avec son ledger, sans payload dans les sorties. Les mutations V32 et
la sauvegarde/restauration seront ensuite bornées d'après ces constats, avant toute demande de
création de manifeste. Aucun nouveau manifeste ni bloc de go n'est produit par cette section.

```text
J9_WO046_STATUS=RESUMED_PREPARATORY_PENDING_PRIMARY_PREFLIGHT_AUTHORIZATION
J9_WO046_MTLS_IDENTITY_SELECTION=SELECTED_RUN_BOUND_WO048_VALIDATED_PUBLIC_METADATA_REVALIDATED
J9_WO046_PRIMARY_DATABASE_READ_AUTHORIZED=NO
J9_WO046_PRIMARY_DATABASE_MUTATION_AUTHORIZED=NO
J9_WO046_MANIFEST_CREATED=NO
J9_WO046_OWNER_GO_REGISTERED=NO
J9_WO046_OWNER_GO_CONSUMED=NO
J9_WO046_DIRECT_IMPORT_ATTEMPTS=0
J9_WO046_PROVIDER_DERIVED_REAL_POSTS=0
J9_LOCAL_RECEIVER_LOOPBACK_AUTHORIZED=NO
```

Les modifications de cette reprise sont exclusivement documentaires. Les rapports historiques,
les migrations et les PDF de référence restent immuables ; aucun test applicatif, Testcontainers
ou pipeline natif n'est relancé pour ce lot. Les contrôles du lot portent sur le diff, les textes
UTF-8, les références et l'absence de données privées nouvellement introduites.

## 12. Préflight primaire autorisé, exécuté strictement en lecture seule

Le propriétaire a ensuite autorisé le préflight strictement en lecture seule via le conteneur
primaire exact `betting-sofascore-local-lab-postgres`, limité au schéma Flyway et aux métadonnées
de l'export sélectionné, sans migration, sauvegarde, démarrage d'application ni POST. Cette
décision supersède la seule attente de lecture primaire de la section 11 ; elle n'autorise
aucune mutation primaire ni aucun manifeste.

La preuve est consignée dans
[J9-WO046-PRIMARY-READONLY-PREFLIGHT-20260905.md](../../validation/J9-WO046-PRIMARY-READONLY-PREFLIGHT-20260905.md),
taille `7149` octets, SHA-256
`e4cf7c99f34ed7071562b6220a4ae0726cb6052de069c26a8fe905014ecc6e58`.
Le premier snapshot SQL est daté `2026-09-04T22:32:01.562779Z`.

```text
J9_WO046_PRIMARY_READONLY_PREFLIGHT=COMPLETE
J9_WO046_PRIMARY_SCHEMA_VERSION=30
J9_WO046_FLYWAY_SUCCESSFUL_VERSIONS=1..30
J9_WO046_FLYWAY_FAILED_ROW_COUNT=0
J9_WO046_V31_APPLIED=NO
J9_WO046_V32_APPLIED=NO
J9_WO046_EXPORT_SELECTION=FILE_AND_PRIMARY_LEDGER_METADATA_CORROBORATED
J9_WO046_SELECTED_EXPORT_DELIVERY_ROW_COUNT=0
J9_WO046_SELECTED_EXPORT_ATTEMPT_ROW_COUNT=0
J9_WO046_STATUS=PREFLIGHT_COMPLETE_PENDING_PRIMARY_MIGRATION_AND_BACKUP_AUTHORIZATION
J9_WO046_PRIMARY_DATABASE_MUTATION_AUTHORIZED=NO
J9_WO046_PRIMARY_BACKUP_RESTORE_AUTHORIZED=NO
J9_WO046_MANIFEST_CREATION_AUTHORIZED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
```

La concordance couvre le statut `HUMAN_VALIDATED`, la classification `PROVIDER_DERIVED`,
l'identité canonique/fournisseur, le chemin relatif, la décision et son intention, les hashes et
la taille. Le fichier existant est inchangé ; sources, warnings, identifiants de snapshots et
hash du jeu de sources correspondent structurellement au ledger. La provenance et l'avertissement
`MISSING_COMPONENT:EVENT_DETAILS` sont préservés. Aucun appel fournisseur ou receiver n'a eu lieu.

### 12.1 Préparation suivante identifiée hors ligne — non exécutée

Le parcours existant ne nécessite pas de nouveau code de sauvegarde :

1. préparer, sous autorisation, des destinations chiffrées neuves hors Git et la configuration
   locale exacte, sans exposer de credentials ; les deux worktrees ci-dessous n'ont pas de `.env`
   lors de l'inspection ;
2. protéger V30 avec `scripts/Backup-Restore-J6.ps1` du bundle propre
   `.tmp/j9-wo042-pr25-ci-readiness`, commit `400900410dfa751521ce387fbadcc4b5ca95a94a`,
   SHA-256 script `9580565fcc6f5065f2e1493316ff4a0b856108f5b4784e3bc050185b6122671c` ;
3. appliquer V31 puis V32 par Flyway, sans modification des migrations antérieures ; le parcours
   opérationnel existant passe par un démarrage Local Lab transitoire, toutes portes fournisseur
   et livraison fermées, puis arrêt : ce démarrage doit être explicitement autorisé ;
4. recontrôler le schéma, la concordance de l'export, les états de livraison et les flags bloquants ;
5. qualifier la sauvegarde/restauration V32 avec le bundle WO-046, SHA-256 script
   `4b68fd7f3d3231b5969ead307373fea6a06f5c987449badf9cfabcfc140e608f` ;
6. consigner les preuves avant toute demande de création de manifeste et de nouveau go.

Les cycles existants restaurent dans une base temporaire distincte sur le même serveur, pas un
second conteneur, et la suppriment après qualification. Les comparaisons intégrées ne comportent
pas d'empreinte dédiée à tous les champs d'`export_manifest` dans la cible restaurée. Les octets
du fichier J7 sont externes au dump SQL et doivent être conservés et hashés séparément. Ne pas
présenter une sauvegarde PostgreSQL comme une sauvegarde du fichier J7 ni élargir les garanties
de restauration au-delà des contrôles existants.

La présente consignation ne consomme aucune de ces autorisations futures. Le manifeste, le go,
le POST, les réseaux fournisseur/distant, le VPS et la production restent interdits.

## 13. Autorisation distincte de préparation primaire V30 vers V32

Le propriétaire autorise ensuite la préparation primaire V30 vers V32, incluant la configuration
locale privée nécessaire et les opérations proposées en section 12.1, sans création de manifeste
ni POST. Cette décision couvre la sauvegarde/restauration préalable V30, le démarrage transitoire
du seul Local Lab toutes portes fournisseur/livraison fermées pour appliquer V31 puis V32 par
Flyway, son arrêt, puis la sauvegarde/restauration isolée V32.

```text
J9_WO046_PRIMARY_PREPARATION_OWNER_DECISION=AUTHORIZE
J9_WO046_PRIVATE_LOCAL_CONFIGURATION_PREPARATION_AUTHORIZED=YES
J9_WO046_V30_ENCRYPTED_BACKUP_AND_ISOLATED_RESTORE_AUTHORIZED=YES
J9_WO046_V31_THEN_V32_FLYWAY_MIGRATION_AUTHORIZED=YES_AFTER_QUALIFIED_V30_BACKUP
J9_WO046_LOCAL_LAB_TRANSIENT_BLOCKED_START_AUTHORIZED=YES_FOR_MIGRATION_ONLY
J9_WO046_V32_ENCRYPTED_BACKUP_AND_ISOLATED_RESTORE_AUTHORIZED=YES
J9_WO046_PRIMARY_DATABASE_PURGE=NO
J9_WO046_MANIFEST_CREATION_AUTHORIZED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

La sauvegarde V30 et son cleanup doivent réussir avant toute migration. Les fichiers techniques
`.age.manifest.json` produits par l'outillage de sauvegarde qualifié ne sont pas le manifeste de
campagne WO-046, dont la création demeure interdite. Les phrases secrètes `age` sont saisies
uniquement par le propriétaire dans un terminal interactif privé, sans capture ni transcript.
Cette section enregistre l'autorité, pas un résultat d'exécution ; les résultats restent à prouver.

## 14. Préparation primaire exécutée — V32 protégée et contrôlée

Le [rapport distinct de préparation primaire](../../validation/J9-WO046-PRIMARY-V30-V32-PREPARATION-20260905.md)
consigne le résultat `PASS_PRIMARY_V32_PREPARED_NO_CAMPAIGN_MANIFEST_NO_POST`.
Sa taille est `11638` octets et son SHA-256 est
`398d608a06cf228bbf299b608517328da73f95ecf43701243ddc39ebb6ee50bd`.
La protection V30 est qualifiée le `2026-09-04T22:57:14.8125701Z`, avant toute migration.
Le Local Lab reconstruit hors ligne au commit `f9788e63c3df862272a482ef83f5f45a9fa95371`
applique V31 puis V32 en mode non-web, toutes fonctions fournisseur et livraison bloquées.
Il sort naturellement code zéro en `6311 ms`, avec fermeture Hikari. La protection V32 est ensuite
qualifiée le `2026-09-04T23:11:15.2409962Z`, soit `2026-09-05T01:11:15.2409962+02:00` à Paris.

Les configurations privées, archives chiffrées et manifestes techniques restent hors Git sous
ACL propriétaire/SYSTEM. Les deux restaurations utilisent des bases temporaires distinctes du
même serveur ; elles sont supprimées par le cleanup qualifié. Aucune purge primaire n'est faite.
Les compteurs et empreintes historiques comparés sont inchangés ; l'historique V1–V30, la ligne
de métadonnées et le fichier J7 sélectionné restent identiques. Le composant `EVENT_DETAILS`
manquant demeure signalé. Les limites exactes de la preuve de restauration figurent au rapport.

```text
J9_WO046_PRIMARY_PREPARATION_STATUS=COMPLETE
J9_WO046_PRIMARY_SCHEMA_VERSION=32
J9_WO046_V30_BACKUP_RESTORE=PASS
J9_WO046_V31_V32_MIGRATION=PASS
J9_WO046_V32_BACKUP_RESTORE=PASS
J9_WO046_FAILED_MIGRATIONS=0
J9_WO046_TEMP_RESTORE_DATABASE_COUNT=0
J9_WO046_J6_SESSION_COUNT=0
J9_WO046_APPLICATION_LISTENER_COUNT=0
J9_WO046_OWNER_GO_GRANT_COUNT=0
J9_WO046_OWNER_GO_CONSUMPTION_COUNT=0
J9_WO046_DELIVERY_COUNT=0
J9_WO046_DELIVERY_ATTEMPT_COUNT=0
J9_WO046_MANIFEST_CREATION_AUTHORIZED=NO
J9_WO046_MANIFEST_CREATED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_WO046_WORK_ORDER_MOVE_TO_COMPLETED=NO
```

La prochaine porte est l'autorisation distincte de créer et geler le manifeste de campagne,
après recontrôles frais des métadonnées et de l'identité PKI. Son commit précédera la soumission
du bloc owner-go canonique V2 et toute autorisation future du POST unique. Cette préparation ne
consomme aucune de ces décisions. Aucun receiver, fournisseur, réseau distant, VPS ou production
n'a été activé ; les restrictions demeurent inchangées.

## 15. Manifeste créé et gelé sous autorisation documentaire distincte

Le propriétaire autorise ensuite explicitement la création, le gel et le commit local du
manifeste WO-046, après recontrôle des métadonnées de l'export et de l'identité mTLS, sans
démarrage du receiver, sans owner-go et sans POST. Cette décision supersède uniquement
l'attente de création du manifeste de la section 14. Les états des sections antérieures
demeurent historiques ; ils ne sont pas réécrits.

Le [manifeste de campagne](../../validation/J9-WO046-J7-REAL-LOCAL-E2E-CAMPAIGN-MANIFEST-20260904.md)
est créé depuis `e3765e58db8d430ab480c67af790cecd44ccbb61` et gelé par le premier commit local
qui le contient, avec les présentes mises à jour documentaires. Ses octets ne contiennent ni
leur propre hash ni leur propre commit. Le commit de gel exact est fourni dans le compte rendu
propriétaire ; aucune réécriture silencieuse du manifeste n'est admise après ce commit.

```text
J9_WO046_MANIFEST_PREPARED_AT_UTC=2026-09-04T23:30:24Z
J9_WO046_MANIFEST_PREPARED_AT_EUROPE_PARIS=2026-09-05T01:30:24+02:00
J9_WO046_MANIFEST_REFERENCE=docs/validation/J9-WO046-J7-REAL-LOCAL-E2E-CAMPAIGN-MANIFEST-20260904.md
J9_WO046_MANIFEST_SIZE_BYTES=18380
J9_WO046_MANIFEST_SHA256=6590603286c6c422588bbc3f6a46f502716fa2e2df18b6ad0df741cbd1cbf277
J9_WO046_MANIFEST_ENCODING=UTF8_LF_WITHOUT_BOM
J9_WO046_MANIFEST_CREATED=YES
J9_WO046_MANIFEST_FREEZE=IMMUTABLE_FROM_FIRST_LOCAL_COMMIT
J9_WO046_MANIFEST_PUBLICATION=LOCAL_ONLY_NO_PUSH
J9_WO046_STATUS=MANIFEST_FROZEN_PENDING_SEPARATE_OWNER_GO_PREPARATION_AUTHORIZATION
J9_WO046_OWNER_GO_CONSTRUCTION_AUTHORIZED=NO
J9_WO046_OWNER_GO_CREATED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_OWNER_GO_REGISTERED=NO
J9_WO046_OWNER_GO_CONSUMED=NO
J9_WO046_RECEIVER_START_AUTHORIZED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_WO046_WORK_ORDER_MOVE_TO_COMPLETED=NO
```

Les recontrôles préalables sont satisfaits :

- PKI le `2026-09-04T23:25:42.8257148Z` : registre privé qualifié et ACL conformes, identités
  exactes et profils publics concordants, certificats valides ; aucune clé privée lue, utilisée
  ou exportée, aucune mutation du magasin et aucun handshake. Seul l'engagement SHA-256 du
  registre est versionné ; les empreintes réelles et ses secrets restent externes ;
- métadonnées primaires le `2026-09-04T23:27:23.953071Z` : transaction bornée en lecture seule
  terminée par ROLLBACK, schéma V32 sans migration échouée, export unique
  `a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc` déjà `HUMAN_VALIDATED` et `PROVIDER_DERIVED`, taille
  et hashes inchangés, warning `MISSING_COMPONENT:EVENT_DETAILS` conservé ; zéro livraison,
  tentative, grant, révocation et consommation ;
- receiver INT-001 : reconstruction propre sous Java 25.0.4 au commit
  `de06153f0908a1bb2dc9bbd2c8e22f7fd14dacfd`, wrapper hors ligne, tests/profils de qualification
  désactivés, `clean package` réussi en `11.684 s` ; le JAR est byte-identique à l'artefact
  précédent et aucun receiver n'est lancé ;
- références qualifiées, proposition ADR v0.2 et JAR Local Lab rehashés ; aucun changement
  runtime ou de migration, aucun rapport historique ni PDF modifié ;
- aucun listener applicatif 8087/8444, port receiver PostgreSQL 5433 libre. Les ressources
  canoniques receiver sont seulement planifiées ; aucune création, adoption ou réutilisation
  des anciennes ressources INT-001 Q1/Q2 n'a lieu.

Le manifeste fixe un export unique, les deux commits/JAR, la cible HTTPS/mTLS loopback, les
headers exacts, une tentative maximum, zéro retry, les contrôles de réception durable et de
cleanup. La rétention receiver planifiée est explicitement de 30 jours. Il ne construit aucun
UUID de go, fenêtre effective, préimage canonique ou hash de décision propriétaire.

Deux relectures indépendantes du contrat et du receiver n'ont relevé aucun défaut bloquant.
Les contrôles documentaires couvrent le diff, UTF-8 sans BOM/NUL, l'absence de placeholders et
de clés dupliquées, les bornes et la confidentialité des seules métadonnées publiées. Les
tests applicatifs, Testcontainers et pipelines natifs ne sont pas relancés : la présente
autorité est limitée au gel documentaire sans POST ni démarrage d'application ; aucun nouveau
`clean verify` n'est revendiqué. Les qualifications existantes sont référencées sans extension
de leur portée. Le seul build de cette préparation est le package receiver hors ligne sans tests.

La prochaine porte est une autorisation distincte de préparer et soumettre le nouveau owner-go
V2 lié au manifeste gelé, puis l'autorisation future, exacte et à usage unique de l'exécution.
Le statut officiel reste `NOT_EVIDENCED` comme audit non bloquant pour ce transfert local sous
ADR-SS-003 v0.2 ; `EVIDENCED_INCOMPATIBLE` reste bloquant. Aucun accord SofaScore, appel
fournisseur, réseau distant, VPS, production ou transfert réel n'est inféré de ce gel.

## 16. R1 — go V2 enregistré, arrêt avant import et révocation inutilisée

Après le gel, le propriétaire autorise la préparation et la soumission du bloc owner-go V2 lié
à ce manifeste, puis l'exécution à usage unique. L'agent prépare le bloc complet privé, le soumet
dans la conversation et l'enregistre avant la fenêtre. Le présent historique consigne cette
instruction ; il n'invente pas une confirmation propriétaire ultérieure des octets générés.

Le [rapport distinct R1](../../validation/J9-WO046-J7-REAL-LOCAL-E2E-R1-STOPPED-20260905.md)
contient `15490` octets, SHA-256
`819572974a6f43c28da4e4191766932c46c43102895ad58e3e436d4b90a783bd`.
Le manifeste du commit `2cdb93d2236955a34528a11cc982fc01f4554b46` reste byte-identique,
SHA-256 `6590603286c6c422588bbc3f6a46f502716fa2e2df18b6ad0df741cbd1cbf277`.

Le bloc V2 canonique de `2087` octets, SHA-256
`1c03c0d97dbbf6f240182dc4aa53eebe610605d4b83d17c280481b221bc28e79`, utilise le go
`2739de83-ae45-45f0-99f1-d527d00cf8db` et la fenêtre demi-ouverte
`[2026-09-05T00:10:00.000000Z,2026-09-05T01:10:00.000000Z)`, soit 02:10–03:10 à Paris.
L'enregistrement à `2026-09-04T23:57:22.012607Z` est corroboré par le hash canonique PostgreSQL
et le validateur Java V2. Le bloc privé contenant l'identité mTLS réelle n'est pas ajouté à Git.

Le receiver a passé la readiness mTLS `200/UP` sur `127.0.0.1:8444`, avec sa nouvelle base V008
et zéro import. Local Lab a ensuite refusé une matrice d'activation incohérente avant listener
8087. L'erreur vient des noms de variables dans le lanceur ad hoc préparé par Codex ; le
diagnostic de binding hors ligne reproduit le refus et identifie les noms corrects avec des
valeurs synthétiques. Il ne qualifie pas un lanceur corrigé et ne constitue pas une reprise.

Le go est révoqué sans consommation à `2026-09-05T00:07:48.997834Z`, avant sa validité. Le
receiver est arrêté gracieusement par un unique POST administratif mTLS à `/actuator/shutdown`,
distinct de l'import J7. Sa base est arrêtée avec son volume conservé et sans redémarrage
automatique ; le primaire demeure sain, l'export inchangé, sans purge. Le contrôle final en
lecture seule à `2026-09-05T00:16:42.061765Z` confirme l'absence de livraison/tentative et
l'absence des listeners 8087/8444/5433. La preuve technique d'arrêt présente deux valeurs sur
leurs lignes suivantes ; cette anomalie de sérialisation est documentée, non réécrite, et la
révocation est corroborée indépendamment en base. Le bloc owner-go V2 n'est pas affecté.

```text
J9_WO046_STATUS=STOPPED_PRE_IMPORT_LOCAL_LAB_CONFIGURATION_BINDING_REFUSED
J9_WO046_R1_OWNER_GO_STATUS=REVOKED_UNUSED
J9_WO046_OWNER_GO_GRANT_COUNT=1
J9_WO046_OWNER_GO_REVOCATION_COUNT=1
J9_WO046_OWNER_GO_CONSUMPTION_COUNT=0
J9_WO046_DELIVERY_COUNT=0
J9_WO046_DELIVERY_ATTEMPT_COUNT=0
J9_WO046_DIRECT_IMPORT_POST_COUNT=0
J9_WO046_RECEIVER_IMPORT_COUNT=0
J9_WO046_RECEIVER_HEALTH_GET_COUNT=1
J9_WO046_RECEIVER_ADMINISTRATIVE_SHUTDOWN_POST_COUNT=1
J9_WO046_PRIMARY_DATABASE=RUNNING_HEALTHY_NOT_STOPPED
J9_WO046_RECEIVER_DATABASE=STOPPED_VOLUME_RETAINED
J9_WO046_RESIDUAL_LISTENERS_8087_8444_5433=0
J9_WO046_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_WO046_REAL_IMPORT_AUTHORIZED=NO_STOPPED_GO_REVOKED
J9_WO046_NEW_MANIFEST_AUTHORIZED=NO
J9_WO046_NEW_OWNER_GO_GRANTED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

La suite nécessite un périmètre d'outillage distinct pour corriger/qualifier le binding complet
du lanceur et le justificatif d'arrêt. La contrainte unique du hash de manifeste ne permet pas
un second grant sur le manifeste R1 révoqué ; toute reprise exige ensuite une décision
propriétaire distincte pour un nouveau manifeste gelé et un nouveau go. Aucun retry, nouveau
manifeste ou go n'est créé. Aucun changement applicatif, de protocole, de migration, de PDF ou
des rapports qualifiés n'est effectué ; les contrôles de ce lot et leurs limites sont détaillés
dans le rapport R1, sans nouveau PASS Maven ni qualification réelle revendiqués.

## 17. Reprise préparatoire R2 après clôture WO-049

Le propriétaire valide WO-049 avec reconnaissance de l'écart d'exécution et autorise
`J9_WO046_RESUME_AFTER_WO049_VALIDATION=YES`, `J9_WO046_NEW_MANIFEST_AUTHORIZED=YES` et
`J9_WO046_NEW_OWNER_GO_GRANTED=YES`. Il maintient `J9_WO046_REAL_POST_AUTHORIZED=NO` et
les interdictions fournisseur, receiver distant, VPS et production.

La clôture WO-049 `516a955de1033ddfe546b934fc7e3b2490ed486c` est intégrée par fast-forward
dans ce worktree propre. Le [compte rendu préparatoire R2](../../validation/J9-WO046-R2-PREPARATION-AFTER-WO049-20260905.md)
consigne l'autorité, le module qualifié, les contrôles Docker purement métadonnées et les
portes restantes. Le manifeste successeur est autorisé, mais pas encore créé ou gelé : le
volume receiver R1 conservé nécessite le choix explicite prévu par le rapport d'arrêt.

Le nouveau go est accordé en intention propriétaire, pas enregistré comme document canonique
exécutoire. V2 impose l'autorisation de POST à `YES` ; aucun tel bloc n'est fabriqué contre
le `NO` maintenu. Aucun grant, SQL, nouveau manifeste, démarrage ou POST n'est effectué par
ce lot. Le run R1 et ses preuves restent gelés ; WO-046 reste actif.
