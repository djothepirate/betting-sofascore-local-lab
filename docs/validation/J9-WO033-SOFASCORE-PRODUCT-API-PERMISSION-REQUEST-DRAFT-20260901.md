# J9 / WO-033 — Brouillon de demande de permission SofaScore Product -> API

- **Version du brouillon :** `1.2`
- **Préparé le :** 2026-09-01
- **Révisé le :** `2026-09-01T18:13:34.3075675Z`
- **Statut :** `PREPARED_NOT_SENT`
- **Canal envisagé :** formulaire officiel SofaScore, catégorie `Product -> API`
- **Objet :** permission écrite et conditions applicables à un usage API football borné
- **Langue d'envoi proposée :** anglais
- **Avis juridique :** `NO`

```text
REQUEST_STATUS=PREPARED_NOT_SENT
CONTACT_CHANNEL=SOFASCORE_PRODUCT_API
OWNER_AUTHORIZED_PREPARATION=YES
OWNER_AUTHORIZED_SEND=NO
EXTERNAL_MESSAGE_SENT=NO
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
EXPLICIT_APPLICABLE_PERMISSION_FOUND=NO
REAL_PROVIDER_USE_PERMISSION_EVIDENCE=NOT_EVIDENCED
REAL_RECEIVER_TRANSFER_PERMISSION_EVIDENCE=NOT_EVIDENCED
REAL_PROVIDER_USE_AUTHORIZED_UNDER_CURRENT_GOVERNANCE=NO
REAL_RECEIVER_TRANSFER_AUTHORIZED_UNDER_CURRENT_GOVERNANCE=NO
```

Ce document prépare une correspondance ; il ne prouve ni son envoi, ni sa réception, ni une
permission. Les champs entre crochets doivent être complétés et revus par le propriétaire. Aucun
statut personnel, non commercial ou commercial ne peut être déduit du caractère local du
laboratoire.

## 1. Sources officielles rafraîchies

Consultation en lecture seule effectuée le `2026-09-01T15:44:51.0151176Z`, soit
`2026-09-01T17:44:51.0151176+02:00` en Europe/Paris.

| Source officielle | URL | Accessibilité | Constat factuel utilisable |
|---|---|---|---|
| Conditions SofaScore | `https://www.sofascore.com/terms-and-conditions` | `ACCESSIBLE` ; dernière mise à jour affichée : 2024-09-18 | usage de la plateforme présenté comme personnel et non commercial ; encadrement de l'extraction de contenu de base, des requêtes automatisées, de l'agrégation, du scraping et de la reproduction sans consentement explicite, sous réserve du droit applicable |
| Documentation API externe | `https://api.sofascore.com/api/docs/external` | `ACCESSIBLE_DOCUMENTATION_SHELL` | existence d'une documentation technique publique ; aucune licence applicable aux six familles du laboratoire n'en est déduite |
| Contact SofaScore | `https://corporate.sofascore.com/contact` | `ACCESSIBLE` | catégorie officielle `Product`, sous-catégorie `API` disponible |

La documentation publique ne vaut pas permission. Le contenu des conditions ne reçoit ici aucune
interprétation juridique ; la demande sollicite précisément la position écrite de SofaScore.

## 2. Champs obligatoires avant tout envoi

```text
REQUESTOR_FULL_LEGAL_NAME=[REQUIRED]
REQUESTOR_ROLE=[REQUIRED]
ORGANIZATION_OR_PROJECT_OWNER=[REQUIRED]
LEGAL_ENTITY_TYPE=[INDIVIDUAL|ASSOCIATION|COMPANY|OTHER]
COUNTRY_AND_JURISDICTION=[REQUIRED]
REPLY_EMAIL=[REQUIRED]
PROJECT_WEBSITE_OR_REPOSITORY=[OPTIONAL]
INTENDED_USE_CLASSIFICATION=[PERSONAL_RESEARCH|NON_COMMERCIAL|COMMERCIAL|OTHER]
BETTING_RELATED_USE_DESCRIPTION=[REQUIRED_PLAIN_LANGUAGE_DESCRIPTION]
END_USER_ACCESS=[INTERNAL_ONLY|EXTERNAL_USERS|OTHER]
RECEIVER_OPERATOR=[SAME_CONTROLLER|THIRD_PARTY|OTHER]
RECEIVER_INITIAL_LOCATION=OWNER_WINDOWS_MACHINE_FOR_FIRST_TEST
RECEIVER_SECOND_LOCATION=PRODUCTION_VPS_FOR_SECOND_TEST
RECEIVER_FUTURE_HOSTING_PROVIDER_AND_REGION=[REQUIRED]
POSSIBLE_FUTURE_PROVIDER_AND_RECEIVER_COLOCATION=YES_SUBJECT_TO_EXPLICIT_PERMISSION_AND_GOVERNANCE
PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST=[NO_BY_DEFAULT|YES_IF_NECESSARY]
MODEL_TRAINING=[NO|YES_WITH_DETAILS]
DATA_RESALE=[NO|YES_WITH_DETAILS]
PUBLIC_REDISTRIBUTION=[NO|YES_WITH_DETAILS]
REQUESTED_PERMISSION_DURATION=[REQUIRED]
EXPECTED_MANUAL_ACQUISITIONS_PER_DAY=[REQUIRED]
EXPECTED_MANUAL_ACQUISITIONS_PER_WEEK=[REQUIRED]
EXPECTED_DIRECT_ATTEMPTS_PER_DAY=[REQUIRED]
EXPECTED_DIRECT_ATTEMPTS_PER_MONTH=[REQUIRED]
```

Le rôle réel du Betting Project doit être décrit sans euphémisme. Si ses résultats influencent des
recommandations, des décisions, des paris ou un service destiné à des tiers, le message final doit
le préciser.

## 3. Messages proposés pour le formulaire

La limite de caractères du formulaire devra être vérifiée immédiatement avant l'envoi. Le choix
entre la version compacte, le message principal seul et le message principal accompagné de
l'annexe sera figé dans le manifeste d'envoi.

### 3.1 Version compacte

```text
Hello SofaScore Product / API team,

I am [FULL LEGAL NAME], [ROLE] for [ORGANIZATION OR PROJECT OWNER] in [COUNTRY]. I am requesting
written guidance and, if available, explicit permission for a bounded football-data integration
used for [PLAIN-LANGUAGE BETTING-RELATED PURPOSE], classified as [PERSONAL RESEARCH /
NON-COMMERCIAL / COMMERCIAL / OTHER].

An experimental local Windows application would make operator-initiated requests for scheduled
events, event details, statistics, incidents and lineups. It currently uses Playwright with a
fresh non-persistent context, concurrency 1, at least three seconds between starts, no polling,
scheduler, retry, proxy rotation, session reuse or challenge bypass. Two prior one-off evidence
series made 48 direct attempts in total; this is not an ongoing cadence.

Raw responses would remain in the isolated laboratory for at most 30 days. Activating an already
human-validated J7 export would be a separate manual action that sends one HTTPS/mTLS request to a
Betting Project receiver. That receiver would never call SofaScore or trigger an acquisition. The
first end-to-end test would run both applications on one Windows workstation; a later test would
keep acquisition on Windows and run the receiver on a production VPS; a possible third stage would
run both applications on that VPS only if SofaScore and the applicable governance explicitly allow
hosted provider acquisition.

Please confirm the official API product, authentication and contract; whether this betting-related
use and Playwright transport are permitted; applicable pricing, rate, retention, normalization,
attribution and receiver-transfer terms; whether a remote receiver and possible future hosted
provider acquisition change the answer; and whether a sandbox is available. We found public Terms
and External API documentation but no permission
clearly applicable to this use, so no further real provider use or delivery will be enabled based
on this request alone.

Kind regards,
[FULL LEGAL NAME]
[ROLE]
[ORGANIZATION OR PROJECT OWNER]
[REPLY EMAIL]
```

### 3.2 Message principal

### Subject

```text
Request for written API permission for a bounded football-data integration test
```

### Message

```text
Hello SofaScore Product / API team,

My name is [FULL LEGAL NAME], and I am [ROLE] for [ORGANIZATION OR PROJECT OWNER] in
[COUNTRY AND JURISDICTION].

I am requesting written guidance and, if available, explicit permission for a small, bounded
integration involving two separate Java applications:

1. "betting-sofascore-local-lab", an experimental Windows application that manually initiates
   limited football-data acquisitions; and
2. "betting-project", a separate betting-related analytics application intended to receive only
   a minimized, normalized and human-reviewed export.

The intended use is: [PLAIN-LANGUAGE BETTING-RELATED USE DESCRIPTION]. It is classified as
[PERSONAL RESEARCH / NON-COMMERCIAL / COMMERCIAL / OTHER], and the resulting data would be
available to [INTERNAL USERS / EXTERNAL USERS / OTHER].

The receiving application would never call SofaScore and would never trigger a SofaScore
acquisition. For an end-to-end test, both applications must run at the same time, but the sequence
would remain strictly separated:

operator-initiated bounded acquisition -> local parsing and human validation -> separate HTTPS
delivery of an already-existing export to the receiver.

Activating delivery means a separate explicit operator action on one already `HUMAN_VALIDATED` J7
export. It sends one request to the receiver; it is not an automatic consequence of validation and
does not start provider acquisition.

We envisage a staged qualification:

1. both applications running on the same Windows workstation;
2. the laboratory remaining on Windows while the receiver runs on a production VPS in
   [HOSTING PROVIDER, COUNTRY AND REGION]; and
3. potentially, both applications running on that VPS at a later date, but only if hosted provider
   acquisition is explicitly permitted and separately approved.

No raw SofaScore response, cookie, token, browser session, certificate material or provider URL
would be sent to the receiver.

The requested football-data families are:

- scheduled football events;
- scheduled events for one tournament and date;
- event details;
- event statistics;
- event incidents;
- event lineups.

There is no live polling, background scheduler or automatic retry. Requests are sequential, with
concurrency 1 and at least three seconds between request starts. A known-event operation requires
up to four GET requests.

For full transparency, two one-off bounded evidence series have already taken place. The first was
stopped after 20 direct attempts. A later independent series completed with 28 direct attempts
under a 38-attempt maximum. The cumulative audit ceiling for those two series was 58, and 48
attempts were effectively made. These are historical validation counts, not an ongoing cadence.
The future expected cadence would be [ACQUISITIONS PER DAY/WEEK] and [DIRECT ATTEMPTS PER
DAY/MONTH], subject to any lower or different limits specified by SofaScore.

The experimental client currently uses Playwright with a fresh, non-persistent browser context
to issue one allowlisted GET request at a time. It does not scrape rendered HTML or the DOM,
establish a reusable browser session, solve challenges, use stealth extensions, rotate proxies or
addresses, retain browser storage, reuse cookies, or retry after a refusal. Please confirm whether
this transport is acceptable. If it is not, please identify the official API product, credentials
and transport that must be used instead.

At the time of our review on 1 September 2026, we found the public SofaScore Terms and the External
API documentation, but did not identify a licence or permission clearly applicable to this use.
We are therefore asking before enabling further real provider use or real data delivery.

Could you please confirm:

1. whether the described betting-related use is permitted;
2. which official API product, host, authentication method and contract must be used;
3. whether the six data families listed above are available under that product;
4. the applicable pricing, attribution and per-second, per-minute, per-day and per-month limits;
5. whether raw responses may be retained locally for up to 30 days and included in an encrypted
   recovery backup;
6. whether the responses may be parsed and normalized, and whether minimized, human-reviewed
   event data may be retained and transmitted to a separate receiver controlled by
   [RECEIVER OPERATOR];
7. whether a production-VPS receiver may accept the normalized export while acquisition remains on
   Windows;
8. whether provider acquisition may ever run from that VPS, under which official API product and
   restrictions, or whether it must always remain on the Windows workstation;
9. whether the answer changes for commercial, user-facing or other hosted use; and
10. whether a sandbox or test environment is available for end-to-end qualification.

A detailed technical appendix and the exact route templates are available on request.

Kind regards,

[FULL LEGAL NAME]
[ROLE]
[ORGANIZATION OR PROJECT OWNER]
[COUNTRY]
[REPLY EMAIL]
```

## 4. Annexe technique proposée

Cette annexe peut être jointe si le formulaire accepte sa longueur, ou envoyée seulement à la
demande de SofaScore. Elle doit rester identique au périmètre réellement envisagé.

### 4.1 Finalité et indépendance

```text
The project evaluates SofaScore football data as an optional, non-critical input to a
betting-related analytics system.

The local laboratory [CONFIRM: does not itself place wagers]. The receiving project's exact
intended use is:

[DESCRIBE TRUTHFULLY: internal research / decision support / user-facing analytics /
commercial service / other betting-related purpose].

SofaScore availability would not be a critical dependency. The Betting Project must continue to
operate if the laboratory, the receiver integration or SofaScore is unavailable.
```

### 4.2 Routes à qualifier explicitement

```text
GET /api/v1/sport/football/scheduled-tournaments/{ISO_DATE}/page/{PAGE}
GET /api/v1/unique-tournament/{UNIQUE_TOURNAMENT_ID}/scheduled-events/{ISO_DATE}
GET /api/v1/event/{EVENT_ID}
GET /api/v1/event/{EVENT_ID}/statistics
GET /api/v1/event/{EVENT_ID}/incidents
GET /api/v1/event/{EVENT_ID}/lineups
```

Question associée :

```text
Our current experimental implementation has observed these route templates under
https://www.sofascore.com.

Please tell us whether they may be called programmatically at all, and whether they must instead
be accessed through an authenticated product hosted at https://api.sofascore.com or another
official host. We will migrate to the official product and authentication model you specify.
```

L'existence technique d'une route ne constitue pas une demande de maintien de cette route, ni une
présomption de permission.

### 4.3 Profil d'accès proposé

```text
SPORT=FOOTBALL_ONLY
ACQUISITION_MODE=MANUAL_ON_DEMAND
CONCURRENT_REQUESTS=1
MINIMUM_REQUEST_START_INTERVAL=3_SECONDS
REQUEST_TIMEOUT=10_SECONDS
AUTOMATIC_RETRY=NONE
POLLING=NONE
SCHEDULER=NONE
LIVE_COLLECTION=NONE
TYPICAL_KNOWN_EVENT_REQUESTS=UP_TO_4
HISTORICAL_WO023_AUTHORIZED_MAXIMUM_DIRECT_ATTEMPTS=38
HISTORICAL_WO019_EFFECTIVE_DIRECT_ATTEMPTS=20
HISTORICAL_WO023_EFFECTIVE_DIRECT_ATTEMPTS=28
HISTORICAL_CUMULATIVE_EFFECTIVE_DIRECT_ATTEMPTS=48
HISTORICAL_CUMULATIVE_AUDIT_CEILING=58
EXPECTED_MANUAL_ACQUISITIONS_PER_DAY=[REQUIRED]
EXPECTED_MANUAL_ACQUISITIONS_PER_WEEK=[REQUIRED]
EXPECTED_DIRECT_ATTEMPTS_PER_DAY=[REQUIRED]
EXPECTED_DIRECT_ATTEMPTS_PER_MONTH=[REQUIRED]
PROXY_ROTATION=NONE
ADDRESS_ROTATION=NONE
CAPTCHA_OR_CHALLENGE_BYPASS=NONE
USER_ACCOUNT_OR_PERSISTENT_BROWSER_PROFILE=NONE
COOKIE_OR_SESSION_REUSE=NONE
```

### 4.4 Stockage et transformation dans le laboratoire

```text
RAW_JSON_LOCATION=ISOLATED_LOCAL_LAB_DATABASE_ONLY
RAW_RESPONSE_TRANSFER_TO_RECEIVER=NO
RAW_RESPONSE_PUBLICATION=NO
RAW_RESPONSE_RESALE=[CONFIRM_NO_OR_CORRECT]
RAW_RESPONSE_MODEL_TRAINING=[CONFIRM_NO_OR_CORRECT]
PROPOSED_RAW_RESPONSE_RETENTION=30_DAYS
ENCRYPTED_RECOVERY_BACKUP=YES
INTEGRITY_METADATA=SHA_256_TIMESTAMPS_PARSER_VERSION_PROVENANCE
```

Questions à poser sans présumer de la réponse :

```text
- May raw responses be stored locally for up to 30 days?
- May an encrypted recovery backup contain the same raw responses, and what retention period
  applies to that backup?
- Must raw responses be deleted sooner?
- May integrity hashes, timestamps, endpoint-family identifiers and audit metadata be retained
  after raw-response deletion?
- May responses be parsed, normalized, compared over time and aggregated?
- Which data categories may be retained in normalized form, and for how long?
- Are there mandatory deletion, correction or revocation procedures?
```

### 4.5 Export normalisé et receiver réel

```text
The laboratory and the Betting Project receiver are separate applications and repositories.

For an end-to-end test, both processes must run concurrently so the receiver is available when
delivery is initiated. Concurrent availability does not create a coupled acquisition flow.

The ordered flow is:

1. an operator manually starts a bounded SofaScore acquisition;
2. the laboratory finishes provider requests and closes the browser context;
3. data is parsed and reviewed locally;
4. an operator explicitly marks one minimized canonical export as HUMAN_VALIDATED;
5. a separate operator activation sends one request containing that already-existing export by
   outbound HTTPS with mTLS;
6. the receiver validates it, applies idempotence and returns a minimized acknowledgement.

The receiver cannot call SofaScore, invoke Playwright, request a refresh or cause the sender to
perform an acquisition. Delivery failure never causes a provider retry.
```

Contrat technique actuellement proposé :

```text
PAYLOAD=MINIMIZED_NORMALIZED_HUMAN_REVIEWED_DATA_ONLY
RAW_PROVIDER_RESPONSE_INCLUDED=NO
COOKIES_OR_TOKENS_INCLUDED=NO
PROVIDER_SESSION_DATA_INCLUDED=NO
PROVIDER_REQUEST_URL_INCLUDED=NO
PAYLOAD_SCHEMA_ID=urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1
PAYLOAD_SCHEMA_VERSION=1.0.0
DELIVERY_PROTOCOL=J7_OPTIONAL_LOCAL_PUSH
DELIVERY_PROTOCOL_VERSION=1.0
DELIVERY_TRIGGER=EXPLICIT_OPERATOR_ACTIVATION_AFTER_HUMAN_VALIDATED
TRANSPORT=OUTBOUND_HTTPS_POST_WITH_MTLS
IDEMPOTENCY_KEY=EXPORT_ID_PLUS_FILE_SHA256
AUTOMATIC_DELIVERY_RETRY=NONE
RECEIVER_ACKNOWLEDGEMENT=MINIMIZED_ID_STATUS_HASHES_RECEIVED_AT
RECEIVER_PROVIDER_ACCESS=NONE
DELIVERY_TRIGGERS_PROVIDER_ACQUISITION=NEVER
```

Questions explicites :

```text
- May normalized and human-reviewed SofaScore-derived data be transferred from the local
  laboratory to a separate Betting Project receiver controlled by [IDENTITY]?
- May that receiver initially run on the owner's Windows workstation for the first end-to-end test?
- May the receiver later run on a VPS in [PROVIDER, COUNTRY AND REGION] while provider acquisition
  remains on the local Windows machine?
- May both the laboratory and receiver later run on that VPS, including provider acquisition from
  the VPS, or must provider acquisition remain on the local Windows machine?
- Does such transfer require a commercial API agreement, regional restriction, subprocessor
  declaration or additional licence?
- May the receiver retain the normalized data? If yes, for how long?
- May normalized data be used for the declared betting-related purpose?
- May it be displayed to end users, or is permission limited to internal analysis?
- Are attribution, source-linking, freshness notices or deletion mechanisms required?
```

La demande n'affirme aucun droit d'exécuter Playwright fournisseur sur VPS. Elle demande si ce
troisième palier pourrait être autorisé et par quel produit officiel :

```text
No provider acquisition will be enabled on a VPS based on this request alone. Please tell us
whether a licensable hosted-server use case exists, which official API product and authentication
it requires, and whether it must be reviewed under a separate agreement.
```

### 4.6 Catégories de données à distinguer

```text
Please indicate whether permission covers each of the following:

- event identifiers, teams, competition, season, stage and scheduled time;
- event status, venue and score;
- match statistics;
- incidents such as goals, cards and substitutions;
- lineups and player participation;
- historical data;
- derived completeness indicators and comparisons;
- team, competition and player names or identifiers;
- logos, images or other media assets.

No permission for logos, images or media assets will be inferred from permission for structured
event data.
```

### 4.7 Questions contractuelles complètes

```text
1. What contract or licence governs this use?
2. Is an API key, account, paid plan or partnership agreement required?
3. Is betting-related use permitted, and under which restrictions?
4. Is commercial use permitted?
5. Are internal analytics and user-facing use treated differently?
6. What are the per-second, per-minute, per-day and per-month limits?
7. Are there cache, storage, historical-data or retention limits?
8. Are transformation and aggregation permitted?
9. Is transfer to a separately deployed receiver under the same controller permitted?
10. Is VPS or cloud storage of normalized data permitted, and in which regions?
11. Is provider acquisition from a VPS permitted under any official product, or must acquisition
    remain on the operator's Windows workstation?
12. Are attribution and source-link requirements mandatory?
13. Is there a sandbox or test credential for end-to-end qualification?
14. What process applies to suspension, revocation, schema changes and incident notification?
15. Who should be contacted before changing volume, endpoints, purpose or deployment topology?
```

## 5. Forme de réponse écrite souhaitée

La réponse sera plus facilement qualifiable si SofaScore précise les dimensions suivantes :

```text
REQUESTOR_OR_LEGAL_ENTITY=[IDENTITY]
PERMISSION_DECISION=[APPROVED|DENIED|REQUIRES_CONTRACT]
AUTHORIZED_API_PRODUCT_AND_HOST=[VALUE]
AUTHORIZED_AUTHENTICATION=[VALUE]
AUTHORIZED_ENDPOINTS_OR_DATA_FAMILIES=[VALUE]
AUTHORIZED_PURPOSE=[VALUE]
BETTING_RELATED_USE=[PERMITTED|NOT_PERMITTED|CONDITIONAL]
COMMERCIAL_USE=[PERMITTED|NOT_PERMITTED|CONDITIONAL]
AUTHORIZED_ENVIRONMENTS=[WINDOWS_PROVIDER_TEST|WINDOWS_RECEIVER_TEST|RECEIVER_VPS|PROVIDER_VPS|COLOCATED_VPS|OTHER]
RATE_LIMITS=[VALUE]
RAW_STORAGE_AND_RETENTION=[VALUE]
ENCRYPTED_BACKUP_RULES=[VALUE]
NORMALIZATION_AND_DERIVED_DATA=[VALUE]
RECEIVER_TRANSFER=[VALUE]
RECEIVER_RETENTION=[VALUE]
ATTRIBUTION_REQUIREMENTS=[VALUE]
GEOGRAPHIC_OR_HOSTING_RESTRICTIONS=[VALUE]
PERMISSION_START_AND_END=[VALUE]
REVOCATION_OR_CHANGE_CONTACT=[VALUE]
APPLICABLE_TERMS_OR_CONTRACT=[URL_OR_DOCUMENT]
```

Une réponse commerciale générique, un accusé automatique, un lien vers la documentation ou
l'existence d'une clé API ne feront pas automatiquement passer la porte à
`EVIDENCED_COMPATIBLE`. La réponse reçue devra être datée, rattachée au message et à l'identité
réels, puis comparée au périmètre exact ; les données personnelles et secrets inutiles resteront
hors Git.

## 6. Rendu final et bloc requis pour autoriser ultérieurement l'envoi

Le SHA-256 stable de ce fichier sera enregistré dans WO-033 comme preuve du modèle revu. Il ne
peut pas autoriser l'envoi : compléter les placeholders change les octets du message.

Avant toute demande d'autorisation, un rendu `FINAL_RENDERED_NOT_SENT` devra être produit avec les
caractéristiques suivantes :

- texte exact destiné au formulaire, encodé en UTF-8 et sans placeholder ;
- destination exacte `https://corporate.sofascore.com/contact`, catégorie `Product -> API` ;
- choix figé `COMPACT_ONLY`, `PRIMARY_ONLY` ou `PRIMARY_PLUS_APPENDIX` après vérification de la
  limite du formulaire ;
- compteur de placeholders non résolus égal à zéro ;
- SHA-256 calculé sur les octets exacts qui seront copiés dans le formulaire ;
- revue visuelle par le propriétaire avant autorisation.

Le rendu pourra être conservé uniquement dans un emplacement local ignoré ou temporaire, puis
supprimé après l'envoi, afin de ne pas versionner nom, rôle ou adresse électronique. Le Work Order
conservera son SHA-256, le mode choisi et une copie expurgée si elle est nécessaire à l'audit. Au
moment de l'envoi, l'agent devra recalculer le hash du rendu local et refuser toute divergence.

Un futur bloc devra couvrir toutes les valeurs ayant remplacé les placeholders et identifier les
octets exacts du rendu :

```text
J9_WO033_PERMISSION_REQUEST_OWNER_DECISION=AUTHORIZE_SEND
WORK_ORDER=WO-SS-20260901-033-j9-provider-permission-request-preparation
DRAFT_REFERENCE=docs/validation/J9-WO033-SOFASCORE-PRODUCT-API-PERMISSION-REQUEST-DRAFT-20260901.md
DRAFT_TEMPLATE_SHA256=<EXACT_REVIEWED_TEMPLATE_SHA256>
FINAL_RENDERED_STATUS=FINAL_RENDERED_NOT_SENT
SEND_DESTINATION_URL=https://corporate.sofascore.com/contact
SEND_CATEGORY=PRODUCT_API
OUTBOUND_CONTENT_MODE=<COMPACT_ONLY|PRIMARY_ONLY|PRIMARY_PLUS_APPENDIX>
OUTBOUND_MESSAGE_SHA256=<EXACT_FINAL_RENDERED_SHA256>
UNRESOLVED_PLACEHOLDER_COUNT=0
SENDER_FULL_LEGAL_NAME=<VALUE>
SENDER_ROLE=<VALUE>
ORGANIZATION_OR_PROJECT_OWNER=<VALUE>
LEGAL_ENTITY_TYPE=<VALUE>
COUNTRY_AND_JURISDICTION=<VALUE>
REPLY_EMAIL=<VALUE>
INTENDED_USE_CLASSIFICATION=<VALUE>
BETTING_RELATED_USE_DESCRIPTION=<VALUE>
END_USER_ACCESS=<VALUE>
RECEIVER_OPERATOR=<VALUE>
RECEIVER_INITIAL_LOCATION=OWNER_WINDOWS_MACHINE_FOR_FIRST_TEST
RECEIVER_SECOND_LOCATION=PRODUCTION_VPS_FOR_SECOND_TEST
RECEIVER_FUTURE_HOSTING_PROVIDER_AND_REGION=<VALUE>
POSSIBLE_FUTURE_PROVIDER_AND_RECEIVER_COLOCATION=<INCLUDED|EXCLUDED>
PRODUCTION_VPS_PUBLIC_IP_DISCLOSURE_IN_REQUEST=<YES|NO>
MODEL_TRAINING=<NO|YES_WITH_DETAILS>
DATA_RESALE=<NO|YES_WITH_DETAILS>
PUBLIC_REDISTRIBUTION=<NO|YES_WITH_DETAILS>
REQUESTED_PERMISSION_DURATION=<VALUE>
EXPECTED_MANUAL_ACQUISITIONS_PER_DAY=<VALUE>
EXPECTED_MANUAL_ACQUISITIONS_PER_WEEK=<VALUE>
EXPECTED_DIRECT_ATTEMPTS_PER_DAY=<VALUE>
EXPECTED_DIRECT_ATTEMPTS_PER_MONTH=<VALUE>
LAB_ITSELF_PLACES_WAGERS=<YES|NO_WITH_ACCURATE_EXPLANATION>
SEND_CHANNEL=SOFASCORE_PRODUCT_API
EXTERNAL_MESSAGE_SEND_AUTHORIZED=YES
```

Sans concordance exacte entre ce bloc et les octets du rendu final revu, aucun envoi ne doit être
effectué. Les valeurs sensibles du bloc d'autorisation pourront être présentées au propriétaire et
consignées localement de façon bornée sans être ajoutées à Git.
