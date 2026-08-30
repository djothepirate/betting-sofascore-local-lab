<!-- J8-AUTOMATIC-BEGIN populationHash=c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726 -->
# J8 — Rapport de benchmark local

> EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY

> J8 : agrégation locale — aucun appel fournisseur.

- **Generated at:** 2026-08-30T09:44:03.269596500Z
- **As of:** 2026-08-30T09:44:03.269596500Z
- **Effective window from:** 2026-08-30T09:24:51.088792500Z
- **Effective window to:** 2026-08-30T09:44:03.269596500Z
- **Window semantics:** &#91;from,to&#41; UTC; asOf is an inclusive read cutoff
- **Population SHA-256:** c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726
- **Measurement state:** MEASURED

## Portée des preuves

- **Evidence coverage:** FULL_ATTEMPT_LEDGER

| Campagnes | Appels directs | Réponses de la strate | RESPONSE_ONLY présentes | Baselines historiques | Imports manuels exclus | Cache hits exclus | Observations synthétiques exclues |
|---:|---:|---:|---:|---:|---:|---:|---:|
|4|20|20|0|0|0|0|0|

- **First measured at:** 2026-08-30T09:35:55.281909Z
- **Last measured at:** 2026-08-30T09:43:39.726751Z

## Strates de preuve non mélangées

Chaque bloc conserve sa propre population et ses propres dénominateurs. Les coûts et taux par tentative sont exacts uniquement dans `FULL_ATTEMPT_LEDGER`.

### FULL_ATTEMPT_LEDGER

- **Stratum measurement state:** MEASURED
- **Stratum evidence population:** 24
- **Attempt denominator:** EXACT_LEDGER

#### Disponibilité des pages et familles

- **Measurement state:** MEASURED
- **Observed scheduled pages:** 15
- **Scheduled page bounds:** 1..15
- **Available families:** EVENT_DETAILS, EVENT_INCIDENTS, EVENT_LINEUPS, EVENT_STATISTICS, SCHEDULED_EVENTS, TOURNAMENT_SCHEDULED_EVENTS
- **Unavailable families:** NOT_MEASURED

#### Synthèse des appels directs

- **Measurement state:** MEASURED

| Tentatives | Réponses | Parsées | Refus 401/403/429 | 404 | INCOMPLETE_ATTEMPT | Erreurs opérationnelles hors 404 |
|---:|---:|---:|---:|---:|---:|---:|
|20|20|20|0|0|0|0|

- **Response rate:** 20/20 &#40;100.00%&#41;
- **Parsing compatibility rate:** 20/20 &#40;100.00%&#41;
- **Refusal rate:** 0/20 &#40;0.00%&#41;
- **404 rate — all endpoint attempts:** 0/20 &#40;0.00%&#41;
- **Operational error rate excluding 404:** 0/20 &#40;0.00%&#41;

Formules : réponse = réponses reçues / tentatives directes ; compatibilité = réponses parsées / réponses éligibles au parsing ; refus et 404 de synthèse = catégorie / toutes les tentatives directes ; erreur opérationnelle = refus + échecs HTTP/transport/persistance/traitement/parsing + arrêt opérateur après tentative + tentative sans outcome, le 404 étant exclu. Chaque taux conserve son numérateur et son dénominateur ; un dénominateur nul produit `NOT_MEASURED`.

#### Mesures par endpoint

| Endpoint | État | État parsing | Tentatives | Réponses prouvées | Parsés | 404 indisponibles | Taux 404 / tentatives endpoint | Refus | INCOMPLETE_ATTEMPT | Schéma incompatible | Contenu inattendu | Transport | Autres erreurs | Dédupliquées | Compatibilité | Taux d’erreur | Latence | Parseurs |
|---|---|---|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---|---|---|---|
|SCHEDULED_EVENTS|MEASURED|MEASURED|15|15|15|0|0/15 (0.00%)|0|0|0|0|0|0|0|15/15 (100.00%)|0/15 (0.00%)|n=15, min=300 ms, p50=348 ms, p95=3292 ms, max=3292 ms|scheduled-events-v1|
|TOURNAMENT_SCHEDULED_EVENTS|MEASURED|MEASURED|1|1|1|0|0/1 (0.00%)|0|0|0|0|0|0|1|1/1 (100.00%)|0/1 (0.00%)|n=1, min=247 ms, p50=247 ms, p95=247 ms, max=247 ms|tournament-scheduled-v1|
|EVENT_DETAILS|MEASURED|MEASURED|1|1|1|0|0/1 (0.00%)|0|0|0|0|0|0|1|1/1 (100.00%)|0/1 (0.00%)|n=1, min=249 ms, p50=249 ms, p95=249 ms, max=249 ms|event-details-v2|
|EVENT_STATISTICS|MEASURED|MEASURED|1|1|1|0|0/1 (0.00%)|0|0|0|0|0|0|1|1/1 (100.00%)|0/1 (0.00%)|n=1, min=241 ms, p50=241 ms, p95=241 ms, max=241 ms|event-statistics-v2|
|EVENT_INCIDENTS|MEASURED|MEASURED|1|1|1|0|0/1 (0.00%)|0|0|0|0|0|0|1|1/1 (100.00%)|0/1 (0.00%)|n=1, min=93 ms, p50=93 ms, p95=93 ms, max=93 ms|event-incidents-v15|
|EVENT_LINEUPS|MEASURED|MEASURED|1|1|1|0|0/1 (0.00%)|0|0|0|0|0|0|1|1/1 (100.00%)|0/1 (0.00%)|n=1, min=84 ms, p50=84 ms, p95=84 ms, max=84 ms|event-lineups-v2|

Le taux de 404 par endpoint = ENDPOINT_UNAVAILABLE / tentatives directes de cet endpoint. Le taux d’erreur conserve les tentatives directes comme dénominateur ; il reste `NOT_MEASURED` pour RESPONSE_ONLY et LEGACY_BASELINE. Dans ces strates historiques, les autres colonnes portent uniquement sur les réponses persistées sélectionnées, jamais sur des tentatives inférées.

#### Complétude normalisée

| Endpoint | Compétition | Saison | État du match | État de mesure | Observations | Complete | Partial | Empty valid | Unavailable | Couverture pondérée des signaux |
|---|---|---|---|---|---:|---:|---:|---:|---:|---|
|EVENT_STATISTICS|Coppa Italia Serie C, Knockout stage|Coppa Italia Serie C 26/27|finished|MEASURED|1|1|0|0|0|100.00%|
|EVENT_INCIDENTS|Coppa Italia Serie C, Knockout stage|Coppa Italia Serie C 26/27|finished|MEASURED|1|0|1|0|0|91.62%|
|EVENT_LINEUPS|Coppa Italia Serie C, Knockout stage|Coppa Italia Serie C 26/27|finished|MEASURED|1|0|1|0|0|99.03%|

Formule : couverture pondérée = Σ signaux présents / Σ signaux attendus × 100, uniquement pour les observations dont le dénominateur attendu est strictement positif. Les dimensions absentes sont rendues littéralement `UNKNOWN`.

#### Stabilité des parseurs

- **Measurement state:** MEASURED
- **Eligible responses:** 20
- **Parsed responses:** 20
- **Schema-incompatible responses:** 0
- **Unexpected-content responses:** 0
- **Parser versions:** 6
- **Parser version transitions:** 0
- **Compatibility rate:** 20/20 &#40;100.00%&#41;
- **First parser break:** NOT_MEASURED
- **Last parser break:** NOT_MEASURED
- **First PARSED recovery after last break:** NOT_MEASURED

| Endpoint | Parseur | Réponses | Parsées | Incompatibles | Première | Dernière |
|---|---|---:|---:|---:|---|---|
|SCHEDULED_EVENTS|scheduled-events-v1|15|15|0|2026-08-30T09:35:58.775094Z|2026-08-30T09:36:41.439872Z|
|TOURNAMENT_SCHEDULED_EVENTS|tournament-scheduled-v1|1|1|0|2026-08-30T09:38:57.424175Z|2026-08-30T09:38:57.424175Z|
|EVENT_DETAILS|event-details-v2|1|1|0|2026-08-30T09:40:12.826045Z|2026-08-30T09:40:12.826045Z|
|EVENT_STATISTICS|event-statistics-v2|1|1|0|2026-08-30T09:43:33.726638Z|2026-08-30T09:43:33.726638Z|
|EVENT_INCIDENTS|event-incidents-v15|1|1|0|2026-08-30T09:43:36.727170Z|2026-08-30T09:43:36.727170Z|
|EVENT_LINEUPS|event-lineups-v2|1|1|0|2026-08-30T09:43:39.726751Z|2026-08-30T09:43:39.726751Z|

### RESPONSE_ONLY

- **Stratum measurement state:** NOT_MEASURED
- **Stratum evidence population:** 0
- **Attempt denominator:** NOT_MEASURED

#### Disponibilité des pages et familles

- **Measurement state:** NOT_MEASURED
- **Observed scheduled pages:** NOT_MEASURED
- **Scheduled page bounds:** NOT_MEASURED
- **Available families:** NOT_MEASURED
- **Unavailable families:** NOT_MEASURED

#### Synthèse des appels directs

- **Measurement state:** NOT_MEASURED

| Tentatives | Réponses | Parsées | Refus 401/403/429 | 404 | INCOMPLETE_ATTEMPT | Erreurs opérationnelles hors 404 |
|---:|---:|---:|---:|---:|---:|---:|
|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|

- **Response rate:** NOT_MEASURED
- **Parsing compatibility rate:** NOT_MEASURED
- **Refusal rate:** NOT_MEASURED
- **404 rate — all endpoint attempts:** NOT_MEASURED
- **Operational error rate excluding 404:** NOT_MEASURED

Formules : réponse = réponses reçues / tentatives directes ; compatibilité = réponses parsées / réponses éligibles au parsing ; refus et 404 de synthèse = catégorie / toutes les tentatives directes ; erreur opérationnelle = refus + échecs HTTP/transport/persistance/traitement/parsing + arrêt opérateur après tentative + tentative sans outcome, le 404 étant exclu. Chaque taux conserve son numérateur et son dénominateur ; un dénominateur nul produit `NOT_MEASURED`.

#### Mesures par endpoint

_NOT_MEASURED — aucune preuve de réponse éligible._

#### Complétude normalisée

_NOT_MEASURED — aucune observation J5 directe éligible._

#### Stabilité des parseurs

- **Measurement state:** NOT_MEASURED
- **Eligible responses:** NOT_MEASURED
- **Parsed responses:** NOT_MEASURED
- **Schema-incompatible responses:** NOT_MEASURED
- **Unexpected-content responses:** NOT_MEASURED
- **Parser versions:** NOT_MEASURED
- **Parser version transitions:** NOT_MEASURED
- **Compatibility rate:** NOT_MEASURED
- **First parser break:** NOT_MEASURED
- **Last parser break:** NOT_MEASURED
- **First PARSED recovery after last break:** NOT_MEASURED

_NOT_MEASURED — aucune version de parseur éligible._

### LEGACY_BASELINE

- **Stratum measurement state:** NOT_MEASURED
- **Stratum evidence population:** 0
- **Attempt denominator:** NOT_MEASURED

#### Disponibilité des pages et familles

- **Measurement state:** NOT_MEASURED
- **Observed scheduled pages:** NOT_MEASURED
- **Scheduled page bounds:** NOT_MEASURED
- **Available families:** NOT_MEASURED
- **Unavailable families:** NOT_MEASURED

#### Synthèse des appels directs

- **Measurement state:** NOT_MEASURED

| Tentatives | Réponses | Parsées | Refus 401/403/429 | 404 | INCOMPLETE_ATTEMPT | Erreurs opérationnelles hors 404 |
|---:|---:|---:|---:|---:|---:|---:|
|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|NOT_MEASURED|

- **Response rate:** NOT_MEASURED
- **Parsing compatibility rate:** NOT_MEASURED
- **Refusal rate:** NOT_MEASURED
- **404 rate — all endpoint attempts:** NOT_MEASURED
- **Operational error rate excluding 404:** NOT_MEASURED

Formules : réponse = réponses reçues / tentatives directes ; compatibilité = réponses parsées / réponses éligibles au parsing ; refus et 404 de synthèse = catégorie / toutes les tentatives directes ; erreur opérationnelle = refus + échecs HTTP/transport/persistance/traitement/parsing + arrêt opérateur après tentative + tentative sans outcome, le 404 étant exclu. Chaque taux conserve son numérateur et son dénominateur ; un dénominateur nul produit `NOT_MEASURED`.

#### Mesures par endpoint

_NOT_MEASURED — aucune preuve de réponse éligible._

#### Complétude normalisée

_NOT_MEASURED — aucune observation J5 directe éligible._

#### Stabilité des parseurs

- **Measurement state:** NOT_MEASURED
- **Eligible responses:** NOT_MEASURED
- **Parsed responses:** NOT_MEASURED
- **Schema-incompatible responses:** NOT_MEASURED
- **Unexpected-content responses:** NOT_MEASURED
- **Parser versions:** NOT_MEASURED
- **Parser version transitions:** NOT_MEASURED
- **Compatibility rate:** NOT_MEASURED
- **First parser break:** NOT_MEASURED
- **Last parser break:** NOT_MEASURED
- **First PARSED recovery after last break:** NOT_MEASURED

_NOT_MEASURED — aucune version de parseur éligible._

## Corrections tardives J6

- **Measurement state:** NOT_MEASURED
- **Analyzed events:** NOT_MEASURED
- **Analyzed provider versions:** NOT_MEASURED
- **Late enrichments:** NOT_MEASURED
- **Late corrections:** NOT_MEASURED
- **Late-change delay:** NOT_MEASURED

Le délai commun aux enrichissements et corrections tardifs est mesuré entre la version fournisseur terminale précédente et la version tardive reçue ; ce n’est pas un retard par rapport à une horloge source absente des preuves.

## Efficacité des dossiers

- **Measurement state:** MEASURED
- **Targeted events:** 1
- **Exploitable events:** 1
- **Strictly complete events:** 0
- **Discovery overhead:** 16
- **Marginal dossier calls:** 4
- **All direct calls:** 20
- **Exploitable rate:** 1/1 &#40;100.00%&#41;
- **Strictly complete rate:** 0/1 &#40;0.00%&#41;
- **Marginal calls per exploitable dossier:** 4.00
- **Effective calls per exploitable dossier:** 20.00

Formules : discovery overhead = tentatives J3 pages + découverte tournoi ; appels marginaux / dossier = tentatives J4 phase 2 + J5 / dossiers directs exploitables ; appels effectifs / dossier = toutes les tentatives directes de la fenêtre / dossiers directs exploitables. Le dénominateur des deux ratios est donc `exploitable events`. Un événement est ciblé dès qu’une unité J4/J5 le déclare dans la fenêtre ; il est exploitable si, à `asOf`, état canonique, détail et trois familles J5 proviennent tous de preuves directes parse-compatibles, même si elles préexistent. `EMPTY_VALID` est disponible mais jamais strictement complet. Avec `FULL_ATTEMPT_LEDGER`, les trois comptes d’appels restent exacts même sans dossier ciblé ou exploitable ; seuls les ratios à dénominateur nul restent `NOT_MEASURED`.

## Dimensions de décision J9

| Dimension | État | Preuve | Limite explicite |
|---|---|---|---|
|ACCESSIBILITY|MEASURED|DIRECT_CALL_LEDGER|Succès, refus, 404 et erreurs sont bornés aux campagnes locales observées.|
|COMPLETENESS|MEASURED|DIRECT_J5_COMPLETENESS|La complétude est ventilée seulement quand une observation J5 directe est corrélée.|
|ACCURACY|NOT_MEASURED|CONTROL_SOURCE_ABSENT|Aucune source de contrôle externe n'est intégrée à ce laboratoire.|
|FRESHNESS|PARTIAL|RECEIPT_TIME_ONLY|Les latences transport et les éventuels délais tardifs utilisent les heures locales de requête/réception; le retard heure source vers réception reste NOT_MEASURED.|
|STABILITY|MEASURED|PARSER_OUTCOME_LEDGER|La stabilité décrit résultats de parsing et transitions, sans test de charge.|
|EFFICIENCY|MEASURED|DIRECT_DOSSIER_DENOMINATOR|Le dénominateur contient uniquement les événements ciblés par J4/J5 fournisseur.|
|MAINTAINABILITY|NOT_MEASURED|ADAPTATION_TIME_ABSENT|Aucun temps d'adaptation ou de restauration offline n'est chronométré.|
|RISK|PARTIAL|LOCAL_FAILURE_EVIDENCE_ONLY|Seuls les refus et échecs locaux observés sont mesurés; aucune prédiction externe.|
|ANALYTICAL_VALUE|NOT_MEASURED|EXTERNAL_COMPARISON_ABSENT|Aucune donnée externe n'autorise une comparaison de valeur analytique.|

## Limites de mesure

- `NO_EXTERNAL_CONTROL_SOURCE` — Exactitude, identité, horaire, statut, score et correction ne sont pas comparés à une source externe.
- `SOURCE_EVENT_TIME_ABSENT` — Les preuves ne portent pas une heure source homogène; le retard source reste NOT_MEASURED.
- `BOUNDED_MANUAL_CAMPAIGNS` — Latences et erreurs proviennent de campagnes manuelles bornées; ce rapport n'est pas un test de charge.
- `MAINTENANCE_TIME_ABSENT` — Les temps d'adaptation de parseur et de restauration offline ne sont pas instrumentés.
- `ANALYTICAL_COMPARATOR_ABSENT` — La valeur analytique relative reste NOT_MEASURED sans fournisseur comparateur.
- `DIRECT_RECEIPT_TIME_LATE_CHANGES` — Les cinq flux J6 sont classifiés, mais uniquement pour les observations DIRECT_LOCAL_ENDPOINT et selon leur heure de réception locale.

## Garde-fous

- Aucun payload brut, URI, header, cookie, token ou état de session.
- Aucun appel fournisseur, retry, polling, scheduler ou transfert externe.
- Les valeurs non observables restent `NOT_MEASURED`; aucune comparaison externe n’est inventée.

<!-- J8-AUTOMATIC-END -->

## Revue humaine ciblée

Cette section est rédigée séparément après le bloc automatique gelé. Elle ne modifie ni sa population, ni ses formules, ni ses octets.

```text
REVIEWED_AT=2026-08-30T10:44:31.8274298Z
HUMAN_REVIEW_PROCESS=PASS
OWNER_CLOSURE_DECISION=AUTHORIZED_2026_08_30_AFTER_FORMAL_AUDIT
LOCAL_EVIDENCE_SOURCE_LABEL=SOFASCORE_DIRECT_LOCAL_ENDPOINT
CONTROL_SOURCE_LABEL=CONTROL_SOURCE_ABSENT
EXTERNAL_COMPARISON_SOURCE=EXTERNAL_COMPARISON_ABSENT
OWNER_ACCEPTED_REVIEW_AS_DOCUMENTED=YES
REVIEW_LIMITATIONS=PRESENT
AUTOMATIC_BLOCK_SHA256=ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25
POPULATION_HASH=c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726
J9_DECISION_TAKEN=NO
```

L’absence de source de contrôle et de comparateur externe est déclarée avant l’examen. Elle interdit de transformer l’exactitude, la maintenabilité, la valeur analytique ou le coût opérateur relatif en mesures positives ou négatives. Ces dimensions restent `NOT_MEASURED`; J8 ne prend aucune décision d’adoption J9.

### Dossiers examinés

| Rôle | Archétype | Source examinée | Verdict local | Comparaison externe | Synthèse bornée |
|---|---|---|---|---|---|
|J8_TARGET|AVAILABLE|SOFASCORE_DIRECT_LOCAL_ENDPOINT|PARTIAL|NOT_MEASURED|Identité, compétition, tournoi, saison, horaire, état terminal, score et cinq composants directs sont présents. Le dossier est exploitable, mais non strictement complet : statistiques COMPLETE, incidents PARTIAL et compositions PARTIAL.|
|NON_TARGET_PARTIAL_OR_UNAVAILABLE|FALLBACK_DIRECT_DOSSIER|SOFASCORE_DIRECT_LOCAL_ENDPOINT|PARTIAL|NOT_MEASURED|Le plus récent archétype littéral ne possédait pas le détail direct requis; le dossier direct à cinq composants le plus proche a donc été examiné. Une famille est UNAVAILABLE, une EMPTY_VALID et une PARTIAL; UNAVAILABLE n’est pas converti en 0 %.|
|NON_TARGET_LATE_CORRECTION|AVAILABLE|SOFASCORE_DIRECT_LOCAL_ENDPOINT|PARTIAL|NOT_MEASURED|La projection et le classifieur J6 réutilisés identifient une LATE_CORRECTION locale sur les trois familles. La classification locale est PASS; la vérité externe et la fraîcheur par rapport à une heure source fiable restent NOT_MEASURED.|

Les contrôles d’identité, d’horaire, d’état, de score, de statistiques, d’incidents, de compositions, de correction tardive, de détail, de fraîcheur, de divergence et de coût opérateur ont été menés uniquement jusqu’au niveau permis par les preuves locales minimisées. Aucun payload, URI, en-tête, cookie, jeton, journal brut ou artefact navigateur n’a été repris.

### Verdicts humains par dimension

| Dimension | Verdict | Motif borné |
|---|---|---|
|Accessibilité|PASS|Vingt tentatives directes instrumentées ont reçu une réponse et ont été parsées; aucun refus, 404, retry, échec ou appel incomplet n’est observé dans la fenêtre.|
|Complétude|PARTIAL|Le dossier ciblé est exploitable mais aucune preuve de dossier strictement complet n’est observée; deux familles ciblées sont PARTIAL.|
|Exactitude|NOT_MEASURED|Aucune source de contrôle externe n’est déclarée.|
|Fraîcheur|PARTIAL|Les latences de transport sont mesurées dans la fenêtre. Les délais locaux de correction tardive proviennent uniquement du dossier historique de revue et ne sont pas injectés dans la métrique automatique de cette fenêtre, qui reste NOT_MEASURED; le retard heure source vers réception ne l’est pas davantage.|
|Stabilité|PASS|Les vingt réponses éligibles sont parsées avec leurs versions déclarées, sans incompatibilité ni contenu inattendu dans la fenêtre.|
|Efficacité|PARTIAL|Le coût est exact — 16 appels de découverte, 4 appels marginaux et 20 appels effectifs par dossier exploitable — mais la population ne contient qu’un dossier exploitable et aucun seuil comparateur.|
|Maintenabilité|NOT_MEASURED|Aucun temps d’adaptation ou de restauration hors ligne n’est chronométré.|
|Risque|PARTIAL|Les refus et échecs locaux observés sont mesurés; aucune extrapolation externe ni test de charge n’est autorisé.|
|Valeur analytique|NOT_MEASURED|Aucun comparateur externe n’est déclaré et la décision d’adoption appartient à J9.|

```text
TARGET_DOSSIER_VERDICT=PARTIAL
NON_TARGET_PARTIAL_UNAVAILABLE_VERDICT=PARTIAL
LATE_CORRECTION_DOSSIER_VERDICT=PARTIAL
LATE_CORRECTION_LOCAL_CLASSIFICATION=PASS
LATE_CORRECTION_EXTERNAL_TRUTH=NOT_MEASURED
OPERATOR_COST_VERDICT=NOT_MEASURED
J8_HUMAN_TARGETED_REVIEW=PASS
J8_HUMAN_REVIEW_LIMITATIONS=PRESENT
J8_VALIDATION_STATE=VALIDATED
J9_DECISION_TAKEN=NO
```

### Bloc de clôture J8

```text
COMPLETENESS_METRICS=AVAILABLE
LATENCY_METRICS=AVAILABLE
SCHEMA_STABILITY=MEASURED
ERROR_RATE=MEASURED
PROVIDER_CALL_COST=MEASURED
AUTOMATIC_POLLING=NO
```
