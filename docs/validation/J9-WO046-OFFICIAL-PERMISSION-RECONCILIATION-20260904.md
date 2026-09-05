# J9 / WO-046 — Réconciliation de la preuve officielle SofaScore

## 1. Portée et résultat

Cette revue répond à l'étape 2 de la séquence propriétaire imposée pour
`WO-SS-20260904-046-j9-j7-real-local-e2e-campaign`. Elle recherche une preuve officielle exacte,
datée et vérifiable autorisant le périmètre réellement envisagé : acquisitions manuelles J3/J4/J5,
normalisation locale, export J7 puis transmission au receiver Betting Project.

La revue est documentaire, hors ligne pour les artefacts privés et en lecture seule pour les sources
publiques. Elle ne constitue pas un avis juridique.

```text
RECONCILIATION_AT_UTC=2026-09-04T14:19:47.2387448Z
RECONCILIATION_AT_EUROPE_PARIS=2026-09-04T16:19:47.2417609+02:00
OWNER_DECLARED_OFFICIAL_PERMISSION_STATUS=EVIDENCED_COMPATIBLE
OWNER_DECLARATION_CORROBORATED_BY_EXACT_OFFICIAL_EVIDENCE=NO
OFFICIAL_RESPONSE_STATUS=NOT_EVIDENCED_IN_INSPECTED_SCOPE
DETERMINISTIC_OFFICIAL_PERMISSION_RESULT=NOT_EVIDENCED
WO046_OFFICIAL_PERMISSION_GATE=BLOCKED
```

La valeur propriétaire `EVIDENCED_COMPATIBLE` est bien conservée comme déclaration interne. Elle
n'est toutefois reliée à aucune réponse SofaScore, licence, convention, identifiant de dossier ou
preuve de soumission exacte. Elle ne peut donc pas être promue silencieusement en autorisation
officielle exécutoire.

## 2. Artefacts versionnés réconciliés

### 2.1 Revue officielle historique

```text
REFERENCE=docs/validation/J9-WO027-OFFICIAL-PERMISSION-REVIEW-20260901.md
REFERENCE_COMMIT=8ad89ff9baa71762fbcf2e91f1b86666ca26b965
SIZE_BYTES=3530
SHA256=10c89c47a33cc573b30e9e34d3665e60a7ee39ff1165ce433f5e2cf65c33cfb2
DETERMINISTIC_RESULT=NOT_EVIDENCED
```

Cette revue est présente dans la lignée WO-046. Elle conclut explicitement que l'autorisation
officielle n'est pas démontrée.

### 2.2 Brouillon de demande WO-033

```text
REFERENCE=docs/validation/J9-WO033-SOFASCORE-PRODUCT-API-PERMISSION-REQUEST-DRAFT-20260901.md
REFERENCE_COMMIT=cdb9c73fc26c6456d3e42020108fadb55769398d
REFERENCE_BRANCH=codex/j9-wo033-provider-permission-request
SIZE_BYTES=24967
SHA256=0934f6c68bf7b9c6d072c9fc616c809d75967c090f81402e5bc8cfdeb8bc150d
STATUS=PREPARED_NOT_SENT
```

Le document est un brouillon de demande, pas une réponse officielle ni un accord.

### 2.3 Manifeste de rendu WO-034

```text
REFERENCE=docs/validation/J9-WO034-PERMISSION-REQUEST-FINAL-RENDER-MANIFEST-20260901.md
REFERENCE_COMMIT=cde32e86a8086f32f938c07b6585802f0608a02e
REFERENCE_BRANCH=codex/j9-wo034-permission-final-render
SIZE_BYTES=13531
SHA256=6e316826e19b54ae43a9f6d275696cb642067978e2514cce52d111c7ec8189cd
FORM_SUBMITTED=NO
EXTERNAL_MESSAGE_SENT=NO
OUTBOUND_MESSAGE_SHA256=NOT_COMPUTED
```

Le manifeste prouve que le pipeline WO-034 n'a pas produit puis soumis une demande finale. Il ne
permet pas d'exclure une action externe non consignée ; aucune action de ce type n'est toutefois
établie dans le périmètre inspecté.

## 3. Préflight privé expurgé

Le conteneur privé WO-034 a été inspecté sans afficher, copier ou versionner les valeurs
propriétaire. Le préflight expurgé produit :

```text
WO034_INPUT_PREFLIGHT_STATUS=FAIL
UNCONDITIONAL_COMPLETE_COUNT=14/30
EMPTY_UNCONDITIONAL_KEY_COUNT=16
FORMAT_ERROR_COUNT=0
ENUM_ERROR_COUNT=0
CONDITIONAL_ERROR_COUNT=1
SEMANTIC_COVERAGE_EVOLVING_BETTING_PROJECT_ANALYTICS=YES
OWNER_INPUT_VALUES_EMITTED=NO
RENDERER_EXECUTED=NO
EXTERNAL_MESSAGE_SEND_AUTHORIZED=NO
```

Les fichiers attendus après un rendu réussi sont absents :

```text
outbound-message.utf8.txt=ABSENT
outbound-form-envelope.utf8.json=ABSENT
outbound-message.sha256.txt=ABSENT
outbound-form-envelope.sha256.txt=ABSENT
render-evidence.properties=ABSENT
submission_receipt=ABSENT
official_response=ABSENT
```

Aucun hash du fichier privé de saisie n'est promu dans Git : il authentifierait seulement un
conteneur de données propriétaire, pas une permission SofaScore.

## 4. Sources officielles publiques consultées

```text
OFFICIAL_PUBLIC_SOURCE_REVIEW_AT_UTC=2026-09-04T14:19:47.2387448Z
EXTERNAL_API_DOCUMENTATION_URL=https://api.sofascore.com/api/docs/external
EXTERNAL_API_DOCUMENTATION_ACCESSIBLE=YES
CONTACT_URL=https://corporate.sofascore.com/contact
CONTACT_PRODUCT_API_CHANNEL_PRESENT=YES
TERMS_URL=https://www.sofascore.com/terms-and-conditions
TERMS_ACCESSIBLE=YES
PROJECT_SPECIFIC_PERMISSION_PRESENT=NO
```

La documentation « External API » confirme l'existence d'une surface documentaire. La page de
contact officielle propose le canal `Product` puis `API`. Les conditions publiques imposent des
restrictions sur l'usage commercial, l'extraction de contenu de base de données, les requêtes
automatisées et le scraping en l'absence d'accord ou consentement applicable. Aucune de ces pages
ne contient une autorisation personnalisée couvrant ce projet, ses parcours J3/J4/J5 et le transfert
J7 vers Betting Project.

L'existence d'endpoints documentés et d'un canal de contact ne vaut donc pas consentement explicite
pour le cas d'usage considéré.

## 5. Conséquence déterministe sur la séquence WO-046

L'étape 1 est terminée séparément dans le dépôt Betting Project : INT-001 est validé par le
propriétaire et reste localement qualifié, non publié. L'étape 2 est exécutée comme réconciliation,
mais son résultat ne satisfait pas la porte positive exigée avant une livraison fournisseur.

```text
ORDERED_STEP_1_INT001_OWNER_VALIDATION=COMPLETE
ORDERED_STEP_2_EXACT_OFFICIAL_EVIDENCE_RECONCILIATION=COMPLETE_NEGATIVE
ORDERED_STEP_2_POSITIVE_GATE_SATISFIED=NO
ORDERED_STEP_3_EXPORT_SELECTION=NOT_REACHED
ORDERED_STEP_4_MTLS_IDENTITY_SELECTION=NOT_REACHED
ORDERED_STEP_5_MANIFEST_CREATION_AND_FREEZE_AUTHORIZATION=NOT_CONSUMED
ORDERED_STEP_6_MANIFEST_COMMIT=NOT_CREATED
ORDERED_STEP_7_CANONICAL_OWNER_GO_SUBMISSION=NOT_CREATED
ORDERED_STEP_8_REAL_POST_AUTHORIZATION=NOT_GRANTED

J9_WO046_MANIFEST_STATUS=NOT_CREATED
J9_WO046_MANIFEST_CREATION_AUTHORIZED=NO
J9_WO046_OWNER_GO_STATUS=NOT_GRANTED
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_WO046_DIRECT_IMPORT_ATTEMPTS=0
J9_WO046_PROVIDER_DERIVED_REAL_POSTS=0
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_LOCAL_RECEIVER_LOOPBACK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Un candidat d'export et l'état du magasin de certificats peuvent être inventoriés en lecture seule,
mais ils ne sont ni sélectionnés ni consommés tant que la porte de l'étape 2 reste fermée.

## 6. Preuve nécessaire pour reprendre

Pour satisfaire l'étape 2 sans modifier la gouvernance, il faut fournir une réponse officielle ou
un accord applicable, puis :

1. conserver l'original hors Git ;
2. établir sa relation avec la demande effectivement envoyée ;
3. consigner la date, le canal, l'identifiant et le périmètre accordé ;
4. calculer son SHA-256 exact ;
5. produire une synthèse expurgée et versionnée ;
6. vérifier explicitement que le périmètre couvre J3/J4/J5, la normalisation locale et le transfert
   J7 vers Betting Project.

Toute décision qui remplacerait cette preuve par une autre base de gouvernance nécessiterait un
Work Order distinct : elle modifierait une porte de sécurité qualifiée et ne peut pas être déduite
de la seule déclaration propriétaire actuelle.
