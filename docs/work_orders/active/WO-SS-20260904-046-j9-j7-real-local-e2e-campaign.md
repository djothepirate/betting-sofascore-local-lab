# WO-SS-20260904-046 — Campagne E2E J7 réelle locale Windows/Windows

- **Statut :** `OPEN_AWAITING_PRECONDITIONS`
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

## 4. Préconditions non encore réconciliées

L'ouverture ne transforme aucune déclaration ou observation historique en preuve exécutoire. Les
éléments suivants doivent être identifiés par une référence versionnée, un commit exact et une
empreinte avant toute création de manifeste :

| Porte | État à l'ouverture | Preuve exigée avant manifeste |
|---|---|---|
| Receiver INT-001 | `LOCALLY_QUALIFIED_READY_FOR_OWNER_REVIEW_NOT_VALIDATED` | validation propriétaire, commit exécutable exact, rapport et SHA-256, rétention/purge/restauration qualifiées |
| Permission officielle | `OWNER_DECLARED_EVIDENCED_COMPATIBLE_REFERENCE_NOT_RECONCILED` | preuve applicable expurgée, versionnée, référencée et hashée |
| Export J7 réel | `NOT_SELECTED` | métadonnées d'un export `PROVIDER_DERIVED` et `HUMAN_VALIDATED`, sans octets dans Git |
| Identité mTLS | `NOT_SELECTED` | origine loopback exacte et empreinte publique du certificat client exact |
| Manifeste WO-046 | `NOT_CREATED_NOT_AUTHORIZED` | décision séparée, contenu complet puis commit gelé avant tout POST |
| Go propriétaire WO-046 | `NOT_GRANTED` | bloc canonique lié au manifeste gelé et fenêtre future de 60 minutes maximum |
| Exécution | `NOT_AUTHORIZED` | autorisation explicite du POST réel et go durable enregistré |

La branche contient encore comme preuve officielle positive la seule déclaration propriétaire
`EVIDENCED_COMPATIBLE`. Le rapport versionné historique
`docs/validation/J9-WO027-OFFICIAL-PERMISSION-REVIEW-20260901.md` conclut
`NOT_EVIDENCED`. WO-046 ne déduit donc aucune référence ni aucun hash favorable : la preuve exacte
doit être réconciliée avant de pouvoir être liée au manifeste et au go.

Une inspection hors ligne et en lecture seule du worktree Betting Project INT-001 établit le
snapshot suivant à l'ouverture :

```text
INT001_BRANCH=codex/int-001-j7-receiver
INT001_BRANCH_HEAD_OBSERVED=25c0229aac06df9ab380f0dfa77a3e70f34a5632
INT001_WORKTREE_CLEAN=YES
INT001_WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW_LOCALLY_QUALIFIED
INT001_IMPLEMENTATION_RESULT=PASS_LOCAL_FAIL_CLOSED
INT001_LOCAL_READINESS_REPORT=docs/reviews/INT-001-local-readiness.md
INT001_LOCAL_READINESS_REPORT_SIZE=14300
INT001_LOCAL_READINESS_REPORT_SHA256=0ca2efb8e9424c2a776b95f9605ca8e5c38a15766a31ff900e09c46161cbcba2
INT001_OWNER_VALIDATION=NOT_RECORDED
```

Ce snapshot prouve une readiness locale, pas une validation propriétaire. Le futur manifeste devra
référencer le commit receiver réellement exécuté après validation ; ni le commit seulement observé
pendant WO-036, ni le snapshot ci-dessus ne sont promus silencieusement en receiver validé.

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
- la preuve officielle applicable, expurgée, versionnée et hashée ;
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
`J7_PROVIDER_DERIVED_OWNER_GO_V1` qualifié par WO-045. Il devra notamment fixer :

```text
WORK_ORDER=WO-SS-20260904-046-j9-j7-real-local-e2e-campaign
CAMPAIGN_MANIFEST_REFERENCE=docs/validation/J9-WO046-J7-REAL-LOCAL-E2E-CAMPAIGN-MANIFEST-20260904.md
CAMPAIGN_MANIFEST_SHA256=<sha256 du manifeste gelé>
EXPECTED_ATTEMPT_NUMBER=1
MAXIMUM_DIRECT_IMPORT_CALLS=1
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
OWNER_GO_DOCUMENT_SHA256=<sha256 externe au préimage canonique>
```

Le futur go doit avoir une fenêtre UTC semi-ouverte strictement positive, future au moment de son
enregistrement et d'une durée maximale de 60 minutes. Tout go antérieur à WO-045 est non
réutilisable. Aucun go n'est accordé, construit, hashé, enregistré ou consommé à l'ouverture de
WO-046.

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

## 8. Périmètre actuellement autorisé

Sont autorisés par la présente décision :

- la création de cette branche et de ce worktree distincts ;
- la création de ce Work Order actif ;
- les contrôles hors ligne et en lecture seule nécessaires pour décrire les portes manquantes ;
- les mises à jour documentaires d'ouverture de `README.md` et `CHANGELOG.md`.

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
J9_WO046_WORK_ORDER_STATUS=OPEN_AWAITING_PRECONDITIONS
J9_WO046_OPENING_DECISION_CONSUMED=YES
J9_WO046_DOCUMENTARY_AND_OFFLINE_INVENTORY_AUTHORIZED=YES

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

WO-046 est ouvert mais bloqué avant préparation de campagne. La prochaine étape autorisable est la
réconciliation hors ligne des préconditions puis, dans une décision distincte, la création et le
gel d'un manifeste complet. Aucun go ni POST ne peut être déduit de l'ouverture.
