# WO-SS-20260901-034 — Rendu final local de la demande de permission fournisseur

- **Statut :** `WAITING_FOR_OWNER_INPUT`
- **Jalon :** après J9 — rendu exact préalable à une éventuelle demande de permission
- **Ouvert le :** 2026-09-01
- **Ouverture UTC :** `2026-09-01T18:42:17.3534554Z`
- **Ouverture Europe/Paris :** `2026-09-01T20:42:17.3534554+02:00`
- **Branche :** `codex/j9-wo034-permission-final-render`
- **Worktree :** `.tmp/j9-wo034-permission-final-render`
- **Base exacte :** `a45daf79d46cf6df5b76fefcb78c7e9ef0ebfdf9`
- **Work Order source :**
  `WO-SS-20260901-033-j9-provider-permission-request-preparation`
- **Brouillon source immuable :**
  `docs/validation/J9-WO033-SOFASCORE-PRODUCT-API-PERMISSION-REQUEST-DRAFT-20260901.md`
- **Version et SHA-256 du brouillon source :**
  `1.2` — `0934f6c68bf7b9c6d072c9fc616c809d75967c090f81402e5bc8cfdeb8bc150d`
- **Type de lot :** rendu documentaire local et preuve cryptographique ; aucune soumission,
  acquisition fournisseur, connexion receiver, livraison, opération VPS ou production

## 1. Autorisation reçue et interprétation bornée

Le propriétaire autorise la mise en place du rendu final sans placeholders et le calcul de son
empreinte exacte. Cette décision autorise l'ouverture de WO-034, la collecte locale des valeurs
propriétaire, la production d'un fichier final dans un conteneur privé hors dépôt et le calcul de
ses empreintes exactes. Elle
n'autorise pas l'envoi du message ni la saisie de données dans le formulaire officiel.

Le rendu ne sera créé qu'après complétude et validation mécanique des valeurs propriétaire. Aucun
nom, rôle, statut légal ou commercial, e-mail, usage réel, opérateur du receiver, hébergeur,
cadence ou engagement relatif aux données ne sera déduit d'un compte Windows, Git, Codex, d'une
adresse IP, d'un dépôt ou des statuts techniques du laboratoire.

```text
J9_WO034_OWNER_DECISION=AUTHORIZE_LOCAL_FINAL_RENDER_PREPARATION
J9_WO034_SCOPE=COLLECT_OWNER_INPUT_LOCALLY_RENDER_FINAL_WITHOUT_PLACEHOLDERS_AND_COMPUTE_EXACT_SHA256
FINAL_RENDER_PREPARATION_AUTHORIZED=YES
FINAL_RENDER_CREATION_AUTHORIZED=YES_AFTER_COMPLETE_OWNER_INPUT
OWNER_INPUT_STORAGE=LOCAL_GIT_IGNORED_ONLY
EXTERNAL_MESSAGE_SEND_AUTHORIZED=NO
EXTERNAL_MESSAGE_SENT=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_CONNECTION_OR_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

La valeur historique `LOCAL_GIT_IGNORED_ONLY` du bloc d'autorisation est mise en œuvre plus
strictement : le stockage réel est hors du dépôt et hors de tout worktree, sous le profil Windows
propriétaire attesté.

## 2. Sources et formulaire cible

Le brouillon v1.2 de WO-033 reste immuable. Son empreinte a été recalculée à l'ouverture de WO-034
et correspond à la valeur qualifiée :

```text
SOURCE_DRAFT_VERSION=1.2
SOURCE_DRAFT_SHA256=0934f6c68bf7b9c6d072c9fc616c809d75967c090f81402e5bc8cfdeb8bc150d
SOURCE_DRAFT_SHA256_MATCH=YES
SOURCE_DRAFT_MUTATION_AUTHORIZED=NO
```

Le formulaire public `https://corporate.sofascore.com/contact` a été inspecté en lecture seule le
`2026-09-01T18:40:29.5992479Z`, soit `2026-09-01T20:40:29.5992479+02:00`. La catégorie `Product`
présente le sujet `API` et affiche l'adresse `product@sofascore.com`. Le formulaire observé contient
un champ société facultatif, puis des champs nom complet, e-mail et message obligatoires. Il ne
présente pas de champ sujet et aucun attribut de longueur maximale n'a été observé sur le message.
Cette absence d'attribut ne constitue ni une garantie d'acceptation d'un texte de toute longueur,
ni une autorisation d'envoi.

```text
FORM_DESTINATION_URL=https://corporate.sofascore.com/contact
FORM_CATEGORY=PRODUCT
FORM_TOPIC=API
FORM_PRODUCT_EMAIL_DISPLAYED=product@sofascore.com
FORM_COMPANY_FIELD=OPTIONAL
FORM_FULL_NAME_FIELD=REQUIRED
FORM_EMAIL_FIELD=REQUIRED
FORM_MESSAGE_FIELD=REQUIRED
FORM_SUBJECT_FIELD=ABSENT
FORM_MESSAGE_MAXLENGTH_ATTRIBUTE=NOT_DECLARED
NO_FORM_DATA_ENTERED=YES
FORM_SUBMITTED=NO
OFFICIAL_CONTACT_PAGE_READ_ONLY_HTTPS_PERFORMED=YES
FORM_SUBMISSION_NETWORK_PERFORMED=NO
PROVIDER_DATA_API_CALLS_UNDER_WO034=0
```

Dans ce Work Order, `PROVIDER_NETWORK_AUTHORIZED=NO` désigne les endpoints d'acquisition de données
SofaScore. Il ne nie pas la consultation HTTPS en lecture seule de la page de contact officielle,
qui est consignée séparément ci-dessus.

## 3. Livrables et séparation des données

| Livrable | Emplacement | Versionnement | État initial |
|---|---|---|---|
| Bootstrap d'identité privée | `%USERPROFILE%\Documents\SofaScoreLocalLab-private\permission-requests\WO-SS-20260901-034\bootstrap-manifest.properties` | hors dépôt | créé, SHA-256 `3741fa3fdf4097f155c0d58891cca10bb9951561e996edff5f75dba78b75e731` |
| Saisie propriétaire | `%USERPROFILE%\Documents\SofaScoreLocalLab-private\permission-requests\WO-SS-20260901-034\owner-input.properties` | hors dépôt | modèle local vide créé, SHA-256 initial `fdd3765d81e732bf9b1c2440a2056bbf11c1f5b8a8029a0b87f9bbb0c11775c1` |
| Template canonique `PRIMARY_ONLY` | `docs/validation/J9-WO034-PERMISSION-REQUEST-PRIMARY-TEMPLATE-20260901.txt` | versionné, sans PII | v1, SHA-256 `bfb9195b78aa663c2e7bb36e896b813087afb1d4e1609346bc9cb0ff21ad8356` |
| Fixture synthétique canonique | `docs/validation/J9-WO034-PERMISSION-REQUEST-SYNTHETIC-FIXTURE-20260901.properties` | versionnée, sans PII | SHA-256 `abdafb5f87ee2fe0f46b2cfef8a33daebb5f3f6b4d99f86fd49c1de70bad8114` |
| Fixture synthétique de préflight | `docs/validation/J9-WO034-PERMISSION-REQUEST-PREFLIGHT-SYNTHETIC-FIXTURE-20260902.properties` | versionnée, sans PII | SHA-256 `3308e6b7f63adb51706fc92c3b8a9aba66b40347b5f3d6b8625e85833533f706` |
| Launcher attesté | `scripts/Invoke-WO034PermissionRequest.ps1` | versionné, sans PII | racine de confiance préchargement, SHA-256 `2398de4e1a9179f6bd79913876c64256de014c48ed0615a0c3709851f3e37094` |
| Renderer déterministe | `scripts/Render-WO034PermissionRequest.ps1` | versionné, sans PII | v1.1.0, SHA-256 `b722709fd0d8988718fcfefb8506803daba4cd241bb3dc00a88874dd708f28b6` |
| Préflight expurgé | `scripts/Test-WO034PermissionRequestInput.ps1` | versionné, sans PII | validation seule, SHA-256 `36cdc6e766a61a451570a934d4b51b97d6c62aab605e76aeb1d3170aac33d647` |
| Tests du préflight | `scripts/Test-WO034PermissionRequestInput.Tests.ps1` | versionnés, sans PII | 22 scénarios, SHA-256 `1b29ac27e152e0dcdac72a70d2c22f33e9e3611a9028e0d535546e7ca9a7d55d` |
| Orchestrateur préflight-rendu | `scripts/Invoke-WO034PermissionRequestWithPreflight.ps1` | versionné, sans PII | verrou continu et attestations, SHA-256 `6ff75681eaa2a51a992fdfaa5f0547fc5b2432643a2bbf77a921f2469d132379` |
| Tests de verrou de l'orchestrateur | `scripts/Invoke-WO034PermissionRequestWithPreflight.Tests.ps1` | versionnés, sans PII | 5 scénarios hors ligne, SHA-256 `a85074712a8a045ea696cce52174596571878459c71d625e513fffac8622d274` |
| Runtime qualifié | `%USERPROFILE%\Documents\SofaScoreLocalLab-private\permission-requests\WO-SS-20260901-034\qualified-runtime` | hors dépôt | copies byte-identiques du launcher et du renderer, ACL protégées |
| Message final | `%USERPROFILE%\Documents\SofaScoreLocalLab-private\permission-requests\WO-SS-20260901-034\outbound-message.utf8.txt` | hors dépôt | non créé, en attente des valeurs |
| Enveloppe de formulaire canonique | `%USERPROFILE%\Documents\SofaScoreLocalLab-private\permission-requests\WO-SS-20260901-034\outbound-form-envelope.utf8.json` | hors dépôt | non créée |
| Empreintes locales | même conteneur privé, fichiers `*.sha256.txt` | hors dépôt | non créées |
| Preuve locale expurgée | même conteneur privé, `render-evidence.properties` | hors dépôt | non créée ; sera publiée en dernier |
| Manifeste expurgé | `docs/validation/J9-WO034-PERMISSION-REQUEST-FINAL-RENDER-MANIFEST-20260901.md` | versionné | ouvert sans données personnelles |
| Traçabilité | `README.md`, `CHANGELOG.md` | versionnée | mise à jour à l'ouverture |

`%USERPROFILE%` désigne ici le profil de l'identité Windows propriétaire enregistrée dans le
bootstrap privé ; ni le nom du profil ni son SID ne sont versionnés. Le conteneur est hors du
worktree et hors Git. Son ACL héritée est coupée et n'accorde l'accès qu'au propriétaire exact,
à `SYSTEM` et aux administrateurs locaux ; les chemins reparse sont refusés. Le message et
l'enveloppe canonique contiennent des données personnelles et ne doivent être copiés dans aucun
journal, document versionné ou dossier synchronisé.

## 4. Valeurs propriétaire obligatoires

Le fichier local de saisie distingue les catégories suivantes :

1. identité et contact : nom légal complet, rôle, propriétaire du projet, type d'entité, pays et
   juridiction, e-mail de réponse, traitement du champ société et décision de divulguer ou non un
   site/dépôt ;
2. usage : classification commerciale, description betting-related exacte et audience ;
3. receiver et hébergement : relation de contrôle, identité de l'opérateur, hébergeur/pays/région,
   inclusion du palier VPS/VPS et divulgation ou non de l'IPv4 déclarée ;
4. traitement des données : périmètre exact de la déclaration, entraînement de modèle, revente et
   redistribution publique, avec détails obligatoires pour toute réponse positive ou catégorie
   `OTHER_WITH_DETAILS` ;
5. durée et cadence : durée de permission demandée, acquisitions manuelles attendues par jour et
   semaine, tentatives directes attendues par jour et mois ;
6. paris : confirmation exacte que le laboratoire place ou non lui-même des paris et explication ;
7. rendu : confirmation explicite du mode de contenu.

Le schéma contient 41 propriétés : `OUTBOUND_CONTENT_MODE=PRIMARY_ONLY`, 30 valeurs ou
confirmations inconditionnelles, et jusqu'à 10 champs de détail conditionnels. Un détail
conditionnel doit être renseigné exactement lorsque son option le requiert et rester vide dans les
autres cas. Le template versionné conserve volontairement 32 occurrences réparties sur 28 tokens ;
seul le message rendu doit avoir un compteur nul de tokens.

Le préflight lit ces 41 propriétés sans appeler le launcher ni le renderer. Il réapplique les
allowlists, formats, conditions et filtres de valeurs du renderer figé, puis exige en plus un
fichier strict UTF-8 sans BOM, avec fins de ligne LF et saut final. Avant lecture, il recalcule les
SHA-256 du launcher, du renderer et du template. Sa sortie ne contient que des compteurs, des
statuts et, pour aider la correction, les noms des clés connues manquantes, vides, dupliquées ou
invalides. Les valeurs et les noms de clés inconnus contrôlés par l'entrée ne sont jamais émis.

La description betting-related passe cette porte uniquement si, après normalisation NFC, elle est
strictement identique au texte approuvé dans le plan : `Betting Project will receive an immutable,
minimized, normalized and human-reviewed J7 export as a durable input for future versioned
enrichment and betting-analysis processing; it will not call SofaScore or trigger an acquisition.`
Une formulation partielle ou une formulation qui ajoute une assertion contradictoire est refusée.

Le dépôt public `https://github.com/djothepirate/betting-sofascore-local-lab` est connu, mais il ne
sera inséré que si le propriétaire choisit explicitement de le divulguer. L'IPv4 déclarée
`51.255.167.32` suit la même règle. Les deux décisions sont laissées vides dans le modèle local :
aucune omission ou divulgation n'est préremplie comme si elle provenait du propriétaire.

## 5. Contrat canonique du rendu

Le renderer v1.1.0 accepte uniquement le mode `PRIMARY_ONLY`, recommandé au regard du formulaire
observé. Le texte, l'ordre, les tokens et les emplacements conditionnels sont figés dans le template
PII-free versionné. Toute demande `COMPACT_ONLY` ou `PRIMARY_PLUS_APPENDIX` exige une nouvelle
version du template, du renderer, de leurs empreintes et de la revue ; elle n'est jamais improvisée
pendant le rendu.

```text
OUTBOUND_CONTENT_MODE_RECOMMENDATION=PRIMARY_ONLY
OUTBOUND_CONTENT_MODE=PENDING_OWNER_CONFIRMATION
CANONICAL_TEMPLATE_VERSION=1
CANONICAL_TEMPLATE_SHA256=bfb9195b78aa663c2e7bb36e896b813087afb1d4e1609346bc9cb0ff21ad8356
SYNTHETIC_FIXTURE_SHA256=abdafb5f87ee2fe0f46b2cfef8a33daebb5f3f6b4d99f86fd49c1de70bad8114
LAUNCHER_SHA256=2398de4e1a9179f6bd79913876c64256de014c48ed0615a0c3709851f3e37094
LAUNCHER_ROLE=PRELOAD_TRUST_ROOT
PRIVATE_BOOTSTRAP_SHA256=3741fa3fdf4097f155c0d58891cca10bb9951561e996edff5f75dba78b75e731
RENDERER_VERSION=1.1.0
RENDERER_SHA256=b722709fd0d8988718fcfefb8506803daba4cd241bb3dc00a88874dd708f28b6
MESSAGE_SECTIONS=GREETING_IDENTITY_PURPOSE_TOPOLOGY_DATA_FAMILIES_ACCESS_PROFILE_HISTORICAL_EVIDENCE_FUTURE_CADENCE_DATA_GOVERNANCE_PERMISSION_QUESTIONS_SIGNOFF
SUBJECT_FIELD_PRESENT=NO
ENCODING=UTF-8
BOM=NO
UNICODE_NORMALIZATION=NFC
LINE_ENDINGS=LF
FINAL_NEWLINE=YES
TRAILING_WHITESPACE=NONE
PLACEHOLDER_SCOPE=OWNER_CONTROLLED_VALUES_IN_EXACT_OUTBOUND_MESSAGE_BYTES
ROUTE_METAVARIABLES_INCLUDED=NO
EXPECTED_RESPONSE_TEMPLATE_INCLUDED=NO
FORM_ENVELOPE_SCHEMA=urn:betting-project:sofascore-local-lab:wo034:permission-request-form:v1
FORM_ENVELOPE_FIELDS=SCHEMA_DESTINATION_URL_CATEGORY_TOPIC_COMPANY_MODE_COMPANY_FULL_NAME_EMAIL_MESSAGE_BYTE_LENGTH_MESSAGE_SHA256_MESSAGE_ATTACHMENTS_PASTE_LINKS
```

Le launcher constitue la racine de confiance qui précède le renderer. Son empreinte exacte,
l'identité byte-à-byte de sa copie privée qualifiée, son ACL protégée et l'absence de reparse point
doivent être vérifiées hors du renderer avant l'invocation `pwsh -NoProfile`. Il ouvre ensuite les
copies runtime et source du renderer en lecture avec partage lecture seulement, vérifie leurs octets
et l'empreinte épinglée, puis conserve les deux handles jusqu'au retour complet. Le renderer exige
des streams lisibles, seekables et non inscriptibles, les relit et les compare avant toute mutation
du conteneur privé.

L'orchestrateur versionné épingle en outre les empreintes du préflight et du launcher. Il conserve
des handles `FileShare.Read` sur le préflight versionné et sur les copies source/runtime du launcher,
puis sur `owner-input.properties` sans interruption entre le préflight et le retour du renderer.
Cette politique autorise leurs relectures par les processus qualifiés tout en refusant écriture,
suppression et remplacement. Les octets de la saisie sont comparés avant/après sous le même handle ;
leur empreinte privée n'est ni affichée ni versionnée. La sortie intermédiaire
`RENDERER_EXECUTED=NO` reste capturée dans la phase préflight et n'est pas relayée comme preuve
finale après exécution du renderer.

Le renderer applique le mapping et les conditions fixés par son code versionné : catégories
`OTHER_WITH_DETAILS`, société facultative, site/dépôt, colocalisation, IPv4 et déclarations
`YES_WITH_DETAILS`. Il refuse les valeurs inutilisées, inconnues, dupliquées, vides, hors allowlist,
munies d'espaces périphériques, de contrôles ou de délimiteurs réservés ; les valeurs Unicode
valides sont normalisées en NFC avant la construction canonique.

Le rendu est produit dans cet ordre :

1. attester hors processus le launcher privé qualifié puis l'invoquer avec `pwsh -NoProfile` ;
2. attester sous handles non inscriptibles les copies runtime et source du renderer ;
3. vérifier le bootstrap, l'identité Windows propriétaire, les ACL, les chemins et l'absence de
   reparse point avant toute sortie ;
4. lire le fichier local sans l'imprimer ;
5. refuser tout champ obligatoire vide, enum invalide ou détail conditionnel absent ;
6. vérifier le hash du template puis construire le message par remplacement ordinal ;
7. appliquer la normalisation Unicode NFC, les fins de ligne LF, l'absence de BOM et un unique saut
   final ;
8. refuser toute marque propriétaire non résolue et tout motif secret haute confiance dans les
   octets sortants ;
9. produire le message et, en JSON compact et ordonné, l'enveloppe canonique du formulaire, incluant destination,
   catégorie, topic, action/valeur société, nom, e-mail, message exact et les deux listes vides
   `attachments` et `pasteLinks` ;
10. calculer séparément les tailles et SHA-256 du message et de l'enveloppe complète, relire les
    fichiers publiés et effectuer le round-trip du message contenu dans l'enveloppe ;
11. publier `render-evidence.properties` en cinquième et dernier fichier. Son absence rend le bundle
    incomplet et force son nettoyage canonique au prochain essai ; aucun fichier existant n'est
    écrasé.

Les métavariables de routes du brouillon et les champs attendus d'une réponse SofaScore ne sont pas
des valeurs propriétaire ; ils sont exclus du mode `PRIMARY_ONLY` plutôt que remplacés par des
valeurs artificielles.

## 6. Portes de contrôle

### 6.1 Porte de création du rendu

La création est bloquée tant que le fichier local n'est pas complet et que le propriétaire n'a pas
confirmé le mode. L'état courant est :

```text
WORK_ORDER_STATUS=WAITING_FOR_OWNER_INPUT
OWNER_INPUT_FILE_STATUS=PARTIALLY_COMPLETED_9_OF_30_UNCONDITIONAL
OWNER_INPUT_VALUES_STORED_IN_GIT=NO
OUTBOUND_CONTENT_MODE_OWNER_CONFIRMED=NOT_YET_QUALIFIED
FINAL_RENDERED_STATUS=NOT_CREATED_PENDING_OWNER_INPUT
OUTBOUND_MESSAGE_SHA256=NOT_COMPUTED
OUTBOUND_FORM_ENVELOPE_SHA256=NOT_COMPUTED
UNRESOLVED_OWNER_INPUT_STATUS=INCOMPLETE
UNRESOLVED_OWNER_INPUT_COUNT=21
CONDITIONAL_OWNER_INPUT_COUNT_MAXIMUM=10
FORM_SUBMISSION_AUTHORIZED=NO
EXTERNAL_MESSAGE_SENT=NO
```

Le renderer réel ne doit être lancé qu'après un préflight dont la sortie expurgée contient toutes
les portes suivantes :

```text
WO034_INPUT_PREFLIGHT_STATUS=PASS
FROZEN_LAUNCHER_SHA256_MATCH=YES
FROZEN_RENDERER_SHA256_MATCH=YES
FROZEN_TEMPLATE_SHA256_MATCH=YES
INPUT_STRICT_UTF8_NO_BOM_LF_FINAL_NEWLINE=YES
EXPECTED_KEY_COUNT=41
MISSING_KEY_COUNT=0
UNKNOWN_KEY_COUNT=0
DUPLICATE_KEY_COUNT=0
MALFORMED_LINE_COUNT=0
UNCONDITIONAL_COMPLETE_COUNT=30/30
EMPTY_UNCONDITIONAL_KEY_COUNT=0
FORMAT_ERROR_COUNT=0
ENUM_ERROR_COUNT=0
CONDITIONAL_ERROR_COUNT=0
SEMANTIC_COVERAGE_EVOLVING_BETTING_PROJECT_ANALYTICS=YES
OWNER_INPUT_VALUES_EMITTED=NO
RENDERER_EXECUTED=NO
```

### 6.2 Porte distincte d'envoi

Après création, l'utilisateur devra pouvoir relire le fichier exact localement. Le bloc de décision
d'envoi sera proposé avec les valeurs calculées mais restera à `NO` jusqu'à une nouvelle décision
propriétaire explicite. Cette future porte devra identifier au minimum WO-034, WO-033, les hashes du
brouillon, du launcher, du bootstrap, du template et du renderer, les SHA-256 du message et de
l'enveloppe complète, le mode,
la destination, la catégorie, le topic et un compteur nul de valeurs propriétaire non résolues.

```text
J9_WO034_PERMISSION_REQUEST_OWNER_DECISION=NOT_RECEIVED
FINAL_RENDER_REVIEWED_BY_OWNER=NO
FINAL_RENDER_SHA256_MATCH=NOT_CHECKED
OUTBOUND_FORM_ENVELOPE_SHA256_MATCH=NOT_CHECKED
FORM_FIELD_BY_FIELD_ROUND_TRIP=NOT_PERFORMED
EXTERNAL_MESSAGE_SEND_AUTHORIZED=NO
FORM_SUBMISSION_AUTHORIZED=NO
```

Une éventuelle autorisation ultérieure portera sur une soumission unique. Tout changement d'un
octet, du formulaire, de la destination ou de la catégorie invalidera cette autorisation. Un résultat
incertain ne permettra aucune resoumission automatique.

## 7. Cycle de vie des données personnelles locales

Le fichier de saisie, le message et l'enveloppe ne sont conservés que pour la revue et la décision
d'envoi. Ils ne doivent pas être copiés dans `docs/`, les logs, les artefacts Maven, les sauvegardes
J6, un dossier synchronisé ou un autre worktree. Les sorties synthétiques de qualification sont
supprimées dès leurs contrôles terminés.

Les artefacts réels devront être supprimés dans chacun des cas suivants : refus ou abandon par le
propriétaire, clôture de WO-034 sans envoi, ou réconciliation terminée de l'unique soumission
autorisée. En cas d'autorisation d'envoi, leur rétention cesse dès que le résultat de soumission est
qualifié ou déclaré incertain ; un résultat incertain n'autorise pas une seconde tentative. La
clôture exige un contrôle d'absence de tous les artefacts sensibles locaux.

```text
LOCAL_SENSITIVE_ARTIFACT_RETENTION=UNTIL_OWNER_REVIEW_AND_SEND_DECISION_OR_SINGLE_SUBMISSION_RECONCILIATION
LOCAL_SENSITIVE_ARTIFACT_COPIES_AUTHORIZED=NO
LOCAL_SENSITIVE_ARTIFACT_J6_BACKUP_AUTHORIZED=NO
CLEANUP_ON_OWNER_DENY_OR_ABANDON=REQUIRED
CLEANUP_ON_CLOSE_WITHOUT_SEND=REQUIRED
CLEANUP_AFTER_SINGLE_SUBMISSION_RECONCILIATION=REQUIRED
CURRENT_LOCAL_SENSITIVE_STATE=BOOTSTRAP_BLANK_OWNER_INPUT_AND_QUALIFIED_RUNTIME_PRESENT_WITHOUT_OWNER_VALUES
LOCAL_SENSITIVE_CLEANUP_STATUS=NOT_DUE
```

## 8. Critères d'acceptation

WO-034 pourra présenter le rendu à la revue propriétaire seulement si :

- le SHA-256 du brouillon v1.2 reste identique ;
- le fichier de saisie, le message, l'enveloppe, leurs hashes et la preuve locale restent hors du
  dépôt et non suivis par Git ;
- toutes les valeurs propriétaire requises sont présentes et cohérentes ;
- le préflight expurgé réussit les 41 clés exactes, les 30 champs inconditionnels, les formats,
  enums, conditions et la couverture sémantique sans émettre de valeur propriétaire ;
- le mode est confirmé explicitement ;
- le launcher préchargé est attesté hors processus, sa copie privée est byte-identique, son ACL est
  protégée et son chemin ne traverse aucun reparse point ;
- les hashes du bootstrap, du template, de la fixture synthétique et du renderer correspondent aux
  références qualifiées ;
- le message et l'enveloppe sont encodés et normalisés conformément au contrat ;
- le compteur de valeurs propriétaire non résolues est nul ;
- les deux empreintes sont calculées deux fois et concordent ;
- le message extrait de l'enveloppe est byte-identique au fichier de message ;
- le diff versionné ne contient aucune valeur d'identité ou de contact propriétaire, aucun secret,
  ni corps final rendu ; le template PII-free, l'IPv4 et le dépôt déjà publics restent distingués
  de ces valeurs locales ;
- `git diff --check` et les contrôles documentaires réussissent ;
- tous les droits d'envoi, réseau fournisseur, receiver réel, livraison, VPS et production restent
  bloqués.

Le déplacement vers `completed` demandera une décision propriétaire distincte après revue de la
preuve expurgée. La création du rendu ne modifiera pas
`J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED`.

## 9. Registre d'exécution

| Horodatage UTC | Contrôle | Résultat |
|---|---|---|
| `2026-09-01T18:42:17Z` | propreté, base, disponibilité du numéro et ouverture du worktree | `PASS` — base `a45daf79d46cf6df5b76fefcb78c7e9ef0ebfdf9`, branche dédiée |
| `2026-09-01T18:42:17Z` | SHA-256 du brouillon source v1.2 | `PASS` — `0934f6c68bf7b9c6d072c9fc616c809d75967c090f81402e5bc8cfdeb8bc150d` |
| `2026-09-01T18:45:52Z` | vérification documentaire, UTF-8, LF, BOM, NUL, ignore Git et secrets haute confiance | `PASS` |
| `2026-09-01T18:46:45Z` | première vérification Maven isolée | `INCONCLUSIVE_ENVIRONMENT` — cache Maven isolé incomplet puis lecture sandboxée du JAR local ; aucun test en échec |
| `2026-09-01T18:52:28Z` | `mvnw.cmd --offline clean verify`, avec sélection explicite du cache Maven propriétaire dont le chemin n'est pas versionné | `PASS` — Surefire `1043/0/0/5`, Failsafe `84/0/0/0`, `BUILD SUCCESS` |
| `2026-09-01T20:33:23Z` | double rendu synthétique PII-free via launcher attesté et renderer v1.1.0 | `PASS` — deux bundles de cinq fichiers byte-identiques, message/enveloppe round-trip, zéro token non résolu |
| `2026-09-01T20:33:23Z` | matrice finale de 20 scénarios | `PASS` — complétude/modes, confinement/ACL/reparse, secrets, verrou, overwrite, staging, récupération, streams et restauration exacte qualifiés fail-closed |
| `2026-09-01T20:33:23Z` | double audit indépendant du launcher et du renderer figés | `PASS` — aucun défaut matériel runtime, sécurité, PII ou preuve ; attestation externe du launcher exigée comme racine préchargement |
| `2026-09-01T20:37:18Z` | nettoyage borné des preuves synthétiques et des anciens emplacements WO-034 | `PASS` — 13 éléments synthétiques et deux racines obsolètes supprimés après validation canonique ; bootstrap, modèle vide, verrou et runtime qualifié conservés |
| `2026-09-01T20:33:23Z` | contrôle de complétude du modèle privé sans afficher ses valeurs | `WAITING` — 30 valeurs ou confirmations inconditionnelles et jusqu'à 10 détails conditionnels restent à fournir |
| `2026-09-01T20:44:41Z` | `mvnw.cmd --offline clean verify` final avec cache Maven propriétaire explicite | `PASS` — Surefire `1043/0/0/5`, Failsafe `84/0/0/0`, `BUILD SUCCESS` |
| `2026-09-01T20:48:13Z` | postflight runtime et Docker en lecture seule | `PASS` — aucun listener `8087`/`8444`, processus lié au worktree ou conteneur Testcontainers résiduel |
| `2026-09-01T23:07:21Z` | qualification synthétique PII-free du préflight expurgé | `PASS` — 22 scénarios bornés, mode fixe non vide, phrase sémantique exacte NFC, refus partiel/contradictoire, sentinelles commentaire/clé inconnue, valeur libre NFD contrôlée en formes brute/NFC, canaux stdout/stderr, schéma `41/30`, UTF-8/LF et absence de fuite/exécution du renderer |
| `2026-09-01T23:07:21Z` | qualification hors ligne du verrou continu de l'orchestrateur | `PASS` — 5 scénarios : écriture, suppression et remplacement concurrents refusés, relectures séquentielles autorisées, vrai processus préflight réussi sous le verrou, aucun hash privé émis et renderer non exécuté |
| `2026-09-01T23:21:34Z` | `mvnw.cmd --offline clean verify` après durcissement du préflight et de l'orchestrateur | `PASS` — Surefire `1043/0/0/5`, Failsafe `84/0/0/0`, `BUILD SUCCESS` ; aucun appel fournisseur |
| `2026-09-01T23:25:29Z` | préflight expurgé de la saisie propriétaire après clarification de `PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST=NO` | `WAITING` — entrée lisible et conforme UTF-8/LF, `41/41` clés, `9/30` champs inconditionnels complets, `21` vides, zéro erreur de format/énumération/condition, couverture sémantique encore absente et renderer non exécuté |

```text
OPENING_DOCUMENTATION_QUALIFICATION=PASS
STANDARD_VERIFY_RESULT=PASS
STANDARD_VERIFY_SUREFIRE=1043_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED
STANDARD_VERIFY_FAILSAFE=84_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
RENDERER_SYNTHETIC_QUALIFICATION=PASS_DETERMINISTIC_FAIL_CLOSED
SYNTHETIC_MESSAGE_BYTE_LENGTH=6014
SYNTHETIC_MESSAGE_SHA256=0e00198a0b774fa0e88c0769009ddb5d3258aa3a18be0004ba4a2ea910459ab4
SYNTHETIC_FORM_ENVELOPE_BYTE_LENGTH=6572
SYNTHETIC_FORM_ENVELOPE_SHA256=25b6c3cec577ba33ca456de1ed57c3c52a93528a6f49733a4bf98c8dc93c32f8
SYNTHETIC_MESSAGE_HASH_FILE_BYTE_LENGTH=92
SYNTHETIC_MESSAGE_HASH_FILE_SHA256=365fb1d9e07ce7d7bf4cd90c4397377121c8d0313273d2e090af9a2a7e0d53a8
SYNTHETIC_ENVELOPE_HASH_FILE_BYTE_LENGTH=99
SYNTHETIC_ENVELOPE_HASH_FILE_SHA256=453765b29269221c30a35bfc7aa174b9797cfaa5a06f811f85e81bbf84d9fa16
SYNTHETIC_RENDER_EVIDENCE_BYTE_LENGTH=1422
SYNTHETIC_RENDER_EVIDENCE_SHA256=68a97b5057cf1f47d86d61902b62abc2709da37489c21dc8a1055608d3c862d0
SYNTHETIC_FIVE_FILE_REPRODUCIBILITY=PASS_BYTE_IDENTICAL
FAIL_CLOSED_SCENARIO_COUNT=20
RESERVED_DELIMITER_FAIL_CLOSED=PASS
SYNTHETIC_OUTPUT_CLEANUP=PASS
OFFICIAL_CONTACT_PAGE_READ_ONLY_HTTPS_PERFORMED=YES
FORM_SUBMISSION_NETWORK_PERFORMED=NO
PROVIDER_DATA_API_CALLS_UNDER_WO034=0
PREFLIGHT_SCRIPT_SHA256=36cdc6e766a61a451570a934d4b51b97d6c62aab605e76aeb1d3170aac33d647
PREFLIGHT_TEST_SCRIPT_SHA256=1b29ac27e152e0dcdac72a70d2c22f33e9e3611a9028e0d535546e7ca9a7d55d
PREFLIGHT_SYNTHETIC_FIXTURE_SHA256=3308e6b7f63adb51706fc92c3b8a9aba66b40347b5f3d6b8625e85833533f706
PREFLIGHT_SYNTHETIC_TEST_CASES=22
PREFLIGHT_SYNTHETIC_QUALIFICATION=PASS_PII_FREE_NO_RENDERER_EXECUTION
PREFLIGHT_ORCHESTRATOR_SHA256=6ff75681eaa2a51a992fdfaa5f0547fc5b2432643a2bbf77a921f2469d132379
PREFLIGHT_ORCHESTRATOR_TEST_SHA256=a85074712a8a045ea696cce52174596571878459c71d625e513fffac8622d274
PREFLIGHT_ORCHESTRATOR_LOCK_SCENARIOS=5
PREFLIGHT_ORCHESTRATOR_QUALIFICATION=PASS_OFFLINE_WRITE_DELETE_REPLACEMENT_DENIED
PREFLIGHT_HARDENING_STANDARD_VERIFY_RESULT=PASS
PREFLIGHT_HARDENING_STANDARD_VERIFY_FINISHED_AT_UTC=2026-09-01T23:21:34Z
PREFLIGHT_HARDENING_STANDARD_VERIFY_SUREFIRE=1043_TESTS_0_FAILURES_0_ERRORS_5_SKIPPED
PREFLIGHT_HARDENING_STANDARD_VERIFY_FAILSAFE=84_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
```
