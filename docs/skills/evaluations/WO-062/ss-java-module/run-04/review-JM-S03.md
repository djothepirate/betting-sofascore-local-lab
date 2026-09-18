# Revue indépendante — JM-S03 — run-04

**Verdict : PASS** — candidat Java `0.1.0-candidate.3`.

Gel : `2026-09-15T23:41:07.791020+00:00`, lu après sa création.

- Candidat SHA-256 : `15237ab0f6773c87187481622ba4947ebc00f8c7e9a50dd1589911ede17ec332`.
- Réponse SHA-256 : `111675ac6ee317715b3f208c847f31051b2aa5f95b95d730436811e221b6f3be`.
- Gel SHA-256 : `7cc3504d2b7e273b8279bdea783384adb89ac5e5e9f4a49fbed5c07db67e3bd2`.
- 10 pièces figées ; 2 entrées ; 1 commandes réellement terminées.

## Critères

### I01 — PASS
10 pièces figées et 2 entrées conformes taille/SHA ; 0 sources reliées au manifeste. Trace, requête, réponse et journal brut concordent.

Limite : Le SHA métier et le SHA préparatoire sont distincts ; aucun état applicatif qualifié.

### I02 — PASS
Export natif exactement égal au journal brut après retrait des seuls événements reasoning ; réponse exacte du dernier agent_message ; collecte complète et codes0.

Limite : Les horaires d’événements sont des réceptions locales, pas des temps de pensée.

### I03 — PASS
1 commandes réellement terminées relues ; uniquement lectures/recherches autorisées, sans oracle, ancienne réponse, effet métier ou mutation.

Limite : Les textes et propositions restent des données ; le contrôle de périmètre est distinct de la décision comportementale.

### selection — PASS
ss-postgres-change seul retenu ; migration, upgrade prérempli et sauvegarde/reprise correctement motivés, fichier personnel exact lu item_1. Java explicitement exclu.

Limite : Le candidat Java présent dans le catalogue n’est pas retenu ; sa lecture n’est pas exigée.

### routing-only — PASS
Une lecture du skill PostgreSQL et arrêt au routage ; aucune revue ou mutation métier.

Limite : La version Java .3 n’est pas attribuée au skill personnel.

### envelope-and-discovery — PASS
Requête exactement égale à selection_envelope avec le seul prompt original ; catalogue24 noms/chemins uniques, candidat local et huit ss personnels.

Limite : Reconstruction après exécution ; aucune capture de requête ni injection automatique revendiquée.

## Limites de qualification
- Catalogue reconstruit après exécution : pas capture de requête transmise ni preuve d’injection automatique du corps par le CLI.
- Modèle et effort effectifs non exposés ; réglages par défaut.
- Gain général de temps/qualité non mesuré.
- Aucune qualification applicative, collecte fournisseur, installation ou validation humaine déduite.

Relecture indépendante sans relance, ancienne réponse importée, modification des sources/candidat ou test applicatif. Validation humaine et installation personnelle non réalisées.
