# J8 — Revue humaine ciblée finale du 30 août 2026

> EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY

## 1. Décision et périmètre

La revue est close sur décision propriétaire conditionnelle du 30 août 2026, après audit formel des preuves locales par Codex. La condition posée — toutes les étapes exécutées et tous les critères de validation formellement prouvés — est contrôlée par la présente preuve, le rapport gelé et les portes finales de WO-016.

```text
SOURCE_LABELS_DECLARED_AT=2026-08-30T10:36:11.8215853Z
REVIEWED_AT=2026-08-30T10:44:31.8274298Z
REVIEW_DECISION_BY=OWNER
EVIDENCE_AUDIT_BY=CODEX
LOCAL_EVIDENCE_SOURCE_LABEL=SOFASCORE_DIRECT_LOCAL_ENDPOINT
CONTROL_SOURCE_LABEL=CONTROL_SOURCE_ABSENT
EXTERNAL_COMPARISON_SOURCE=EXTERNAL_COMPARISON_ABSENT
OWNER_ACCEPTED_REVIEW_AS_DOCUMENTED=YES
HUMAN_TARGETED_REVIEW=PASS
REVIEW_LIMITATIONS=PRESENT
ADDITIONAL_PROVIDER_CALLS=0
J9_DECISION_TAKEN=NO
```

Les deux codes d’absence sont déclarés avant l’examen. Ils interdisent toute conclusion d’exactitude externe, de valeur analytique comparative, de maintenabilité chronométrée ou de coût opérateur relatif. Une dimension non observable reste `NOT_MEASURED`; aucun zéro et aucun succès ne sont inventés.

## 2. Trois dossiers distincts examinés

| Rôle | Archétype | Heure du contrôle UTC | Source | Verdict local | Comparaison externe | Synthèse bornée |
|---|---|---|---|---|---|---|
|Dossier ciblé de la campagne J8|AVAILABLE|2026-08-30T10:44:31.8274298Z|SOFASCORE_DIRECT_LOCAL_ENDPOINT|PARTIAL|NOT_MEASURED|Identité, compétition, tournoi, saison, horaire, état terminal, score et cinq composants directs sont présents. Le dossier est exploitable mais non strictement complet : statistiques COMPLETE, incidents PARTIAL et compositions PARTIAL.|
|Dossier direct non ciblé PARTIAL ou UNAVAILABLE|FALLBACK_DIRECT_DOSSIER|2026-08-30T10:44:31.8274298Z|SOFASCORE_DIRECT_LOCAL_ENDPOINT|PARTIAL|NOT_MEASURED|L’archétype littéral le plus récent ne possédait pas son détail direct; le dossier direct à cinq composants le plus proche est utilisé. Une famille est UNAVAILABLE, une EMPTY_VALID et une PARTIAL. `UNAVAILABLE` n’est pas converti en complétude de 0 %.|
|Dossier direct non ciblé avec LATE_CORRECTION|AVAILABLE|2026-08-30T10:44:31.8274298Z|SOFASCORE_DIRECT_LOCAL_ENDPOINT|PARTIAL|NOT_MEASURED|Le classifieur J6 réutilisé établit une LATE_CORRECTION locale pour les trois familles. La classification locale est PASS; la vérité externe et le retard depuis une heure source fiable restent NOT_MEASURED.|

Les contrôles couvrent, dans les limites de la source déclarée, l’identité des équipes, la compétition, le tournoi, la saison, l’horaire prévu, l’état et le score, les statistiques, les incidents, les compositions, la correction tardive, le niveau de détail, la fraîcheur, les divergences et le coût opérateur. Aucune donnée brute, URL, URI, clé de requête, en-tête, cookie, jeton, `.env`, journal brut ou preuve navigateur n’est reprise.

## 3. Verdicts humains bornés

```text
TARGET_DOSSIER_VERDICT=PARTIAL
NON_TARGET_PARTIAL_UNAVAILABLE_VERDICT=PARTIAL
LATE_CORRECTION_DOSSIER_VERDICT=PARTIAL
LATE_CORRECTION_LOCAL_CLASSIFICATION=PASS
LATE_CORRECTION_EXTERNAL_TRUTH=NOT_MEASURED

ACCESSIBILITY_VERDICT=PASS
COMPLETENESS_VERDICT=PARTIAL
ACCURACY_VERDICT=NOT_MEASURED
FRESHNESS_VERDICT=PARTIAL
STABILITY_VERDICT=PASS
EFFICIENCY_VERDICT=PARTIAL
MAINTAINABILITY_VERDICT=NOT_MEASURED
RISK_VERDICT=PARTIAL
ANALYTICAL_VALUE_VERDICT=NOT_MEASURED
OPERATOR_COST_VERDICT=NOT_MEASURED
```

- `ACCESSIBILITY=PASS` : 20 tentatives instrumentées, 20 réponses, 20 parsings, aucun refus, 404, retry, échec ou appel incomplet.
- `COMPLETENESS=PARTIAL` : un dossier direct exploitable, mais aucun dossier strictement complet dans la fenêtre.
- `FRESHNESS=PARTIAL` : latences de transport mesurées dans la fenêtre; les délais locaux de correction tardive appartiennent uniquement au dossier historique de revue et ne sont pas injectés dans la métrique automatique de la fenêtre, qui reste `NOT_MEASURED`; retard heure source vers réception non observable.
- `STABILITY=PASS` : 20 réponses éligibles parsées, sans incompatibilité ni contenu inattendu dans la fenêtre.
- `EFFICIENCY=PARTIAL` : coût exact, mais population d’un seul dossier exploitable et absence de seuil comparateur.
- `RISK=PARTIAL` : refus et échecs locaux observés mesurés sans extrapolation externe ni test de charge.

## 4. Rapport gelé et décision

Le bloc automatique de [J8-BENCHMARK-REPORT-20260830.md](../benchmark/J8-BENCHMARK-REPORT-20260830.md) est byte-identique au dernier export non-Web reproductible. La revue humaine est ajoutée seulement après son marqueur de fin.

```text
AUTOMATIC_BLOCK_BYTES=15202
AUTOMATIC_BLOCK_SHA256=ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25
POPULATION_HASH=c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726
FINAL_REPORT_SHA256=6d9a46b4391beffe9c1c54591089f0b035a5ed81f9522c03998a0feff18e7e52
AUTOMATIC_PREFIX_BYTE_IDENTICAL=YES
EXPORT_NETWORK_CALLS=0
J8_VALIDATION_DECISION=VALIDATED
J9_DECISION_TAKEN=NO
```

La validation de J8 certifie la disponibilité des mesures et la reproductibilité du protocole borné. Elle ne transforme pas les dimensions `PARTIAL` ou `NOT_MEASURED` en `PASS` et ne préjuge pas de la décision d’adoption J9.
