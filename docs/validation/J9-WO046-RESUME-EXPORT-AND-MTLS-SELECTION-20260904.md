# J9 — WO-046 — Reprise, sélection de l'export et inventaire de l'identité mTLS

- **Résultat :** `PARTIAL_READY_WAITING_MTLS_IDENTITY_PROVISIONING_DECISION`
- **Work Order :** `WO-SS-20260904-046-j9-j7-real-local-e2e-campaign`
- **Branche :** `codex/j9-wo046-j7-real-local-e2e-campaign`
- **Contrôle UTC :** `2026-09-04T16:52:30.8204091Z`
- **Contrôle Europe/Paris :** `2026-09-04T18:52:30.8204091+02:00`

## 1. Autorité consommée

La reprise a été explicitement autorisée après validation de WO-047 :

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

Cette décision est consommée pour intégrer linéairement WO-047, consigner la supersession de la
porte historique et effectuer les sélections hors ligne antérieures au manifeste. Elle ne vaut ni
autorisation de créer le manifeste, ni go propriétaire, ni autorisation de POST.

## 2. Intégration linéaire et supersession

La branche WO-046 était propre au commit
`0338821cc07130f5d200a70db10209bb898a59ae`. Ce commit est l'ancêtre exact de la clôture WO-047.
La branche a été avancée exclusivement par fast-forward jusqu'au commit validé et publié :

```text
WO047_CLOSURE_COMMIT=73881ebf2c673d347b63e8be982572e70b6429ae
WO047_REMOTE_BRANCH=origin/codex/j9-wo047-j7-delivery-governance-separation
WO047_QUALIFICATION_REPORT_SHA256=75b55109c0705a44026376d7f0bdadf76d979dcd4268c1430d5ed8008d693540
WO046_LINEAR_INTEGRATION=PASS_FAST_FORWARD_ONLY
```

La preuve officielle antérieure reste exacte : aucun accord ou réponse SofaScore n'est établi et
son statut d'audit demeure `NOT_EVIDENCED`. ADR-SS-003 v0.2 et WO-047 rendent ce seul statut non
bloquant pour le transfert local d'un export J7. `EVIDENCED_INCOMPATIBLE` reste bloquant. Les
portes d'acquisition J3/J4/J5 restent inchangées et indépendantes.

## 3. Étape 3 — export sélectionné

L'inventaire a été limité aux fichiers `*.validated.json` de la racine d'exports du Local Lab
primaire. Les octets du candidat ont été lus localement et en lecture seule pour parser les
métadonnées, recalculer le SHA-256 du fichier et appliquer les règles de classification du sender.
Ils n'ont été ni affichés, ni copiés, ni ajoutés à Git.

Le candidat le plus récent est sélectionné. Il possède cinq entrées de source : les sources
disponibles sont des `PROVIDER_SNAPSHOT` et la source absente porte correctement un
`sourceKind=null`. La classification déterministe est donc `PROVIDER_DERIVED`.

```text
VALIDATED_EXPORT_FILE_COUNT=5
PROVIDER_DERIVED_HUMAN_VALIDATED_CANDIDATE_COUNT=4
SELECTION_RULE=MAX_GENERATED_AT_UTC_THEN_EXPORT_ID
EXPORT_SELECTION_STATUS=SELECTED_METADATA_ONLY_PENDING_PRIMARY_LEDGER_REVALIDATION
EXPORT_FILENAME=j7-e2c9599a-2336-3888-ae88-2437dad02b48-a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc.validated.json
EXPORT_ID=a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc
CANONICAL_EVENT_ID=e2c9599a-2336-3888-ae88-2437dad02b48
PROVIDER=SOFASCORE
PROVIDER_EVENT_ID=16310945
PAYLOAD_CLASS=PROVIDER_DERIVED
VALIDATION_STATUS=HUMAN_VALIDATED
GENERATED_AT_UTC=2026-09-04T10:27:48.6370240Z
DECIDED_AT_UTC=2026-09-04T10:28:13.8561990Z
SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
SCHEMA_VERSION=1.0.0
ROOT_ENVELOPE=manifest,data
SOURCE_COUNT=5
WARNING_COUNT=1
WARNING_1=MISSING_COMPONENT:EVENT_DETAILS
SIZE_BYTES=35663
FILE_SHA256=d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6
DATA_SHA256=f95ae31e711a9433d6d06745d37f2535143266aa97c7d151fd26bea88ef03ac6
UTF8_BOM=ABSENT
```

La sélection n'affirme pas encore que le ledger primaire correspond : tout contact avec la base
primaire reste interdit. Cette concordance, le statut courant, le chemin confiné et les octets
devront être revalidés après une autorisation distincte, avant le gel du manifeste.

L'avertissement `MISSING_COMPONENT:EVENT_DETAILS` est conservé comme fait. Il n'empêche ni le
statut humain déjà décidé ni la classification de provenance, mais devra apparaître dans le
manifeste afin que la campagne ne promette pas une complétude absente.

## 4. Étape 4 — identité mTLS

L'inventaire a lu uniquement les métadonnées publiques du magasin Windows
`Cert:\CurrentUser\My`. Aucun certificat, aucune clé privée et aucune phrase secrète n'ont été
exportés, utilisés ou créés.

```text
CURRENT_USER_MY_CERTIFICATE_COUNT=0
J7_RELATED_PUBLIC_IDENTITY_COUNT=0
MTLS_IDENTITY_SELECTION_STATUS=NOT_SELECTED_NO_EXISTING_CANDIDATE
CERTIFICATE_PROVISIONING_PERFORMED=NO
PRIVATE_KEY_READ_EXPORT_OR_USE=NO
```

L'étape 4 ne peut donc pas être achevée par sélection. Le provisionnement d'une identité cliente
neuve, de la confiance receiver et de leurs métadonnées publiques exactes requiert une décision
propriétaire séparée. Le certificat privé et sa protection resteront hors Git et hors preuve.

## 5. État résultant et portes

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

J9_WO046_MANIFEST_CREATED=NO
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

Aucun receiver ou Local Lab n'a été démarré, aucune socket n'a été ouverte, aucun appel fournisseur
n'a été effectué et aucun POST n'a été tenté pendant cette reprise.
