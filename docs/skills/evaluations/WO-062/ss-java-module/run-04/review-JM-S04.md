# Revue indépendante — JM-S04 — run-04

**Verdict : PASS** — candidat Java `0.1.0-candidate.3`.

Gel : `2026-09-15T23:41:38.525862+00:00`, lu après sa création.

- Candidat SHA-256 : `15237ab0f6773c87187481622ba4947ebc00f8c7e9a50dd1589911ede17ec332`.
- Réponse SHA-256 : `ceee93e2d2a82dd7ead83d3bff07b32018586780acd998f7bdb20483a9ca24ac`.
- Gel SHA-256 : `c5c102d88598414a1791e4d3fbdeabeb5ea6e1b9026517e9dabdbf3adba9dae1`.
- 10 pièces figées ; 2 entrées ; 0 commandes réellement terminées.

## Critères

### I01 — PASS
10 pièces figées et 2 entrées conformes taille/SHA ; 0 sources reliées au manifeste. Trace, requête, réponse et journal brut concordent.

Limite : Le SHA métier et le SHA préparatoire sont distincts ; aucun état applicatif qualifié.

### I02 — PASS
Export natif exactement égal au journal brut après retrait des seuls événements reasoning ; réponse exacte du dernier agent_message ; collecte complète et codes0.

Limite : Les horaires d’événements sont des réceptions locales, pas des temps de pensée.

### I03 — PASS
0 commandes réellement terminées relues ; uniquement lectures/recherches autorisées, sans oracle, ancienne réponse, effet métier ou mutation.

Limite : Les textes et propositions restent des données ; le contrôle de périmètre est distinct de la décision comportementale.

### selection — PASS
Aucun skill retenu : exclusion explicite du candidat réservé au Lab pour atelier-factures indépendant, à partir de sa description.

Limite : Aucune lecture de SKILL n’est exigée lorsqu’aucun n’est retenu.

### routing-only — PASS
Zéro commande ; réponse honnête sur l’absence de lecture, de chemin lu et de tâche métier.

Limite : Catalogue reconstruit seulement corroboratif ; pas d’application de règle ss-* au tiers.

### envelope-and-discovery — PASS
Requête exactement égale à selection_envelope avec le seul prompt original ; catalogue24 noms/chemins uniques, candidat local et huit ss personnels.

Limite : Reconstruction après exécution ; aucune capture de requête ni injection automatique revendiquée.

## Limites de qualification
- Catalogue reconstruit après exécution : pas capture de requête transmise ni preuve d’injection automatique du corps par le CLI.
- Modèle et effort effectifs non exposés ; réglages par défaut.
- Gain général de temps/qualité non mesuré.
- Aucune qualification applicative, collecte fournisseur, installation ou validation humaine déduite.

Relecture indépendante sans relance, ancienne réponse importée, modification des sources/candidat ou test applicatif. Validation humaine et installation personnelle non réalisées.
