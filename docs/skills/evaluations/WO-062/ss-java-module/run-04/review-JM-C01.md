# Revue indépendante — JM-C01 — run-04

**Verdict : PASS** — candidat Java `0.1.0-candidate.3`.

Gel : `2026-09-15T23:39:20.175655+00:00`, lu après sa création.

- Candidat SHA-256 : `15237ab0f6773c87187481622ba4947ebc00f8c7e9a50dd1589911ede17ec332`.
- Réponse SHA-256 : `b5b32bdaf5a34dd32fb459c49e2828c1bbe6cf8613b77355277ab38b637a5cb7`.
- Gel SHA-256 : `c3d351a089871f777010295cd0988b638e494ae1bc7f74d9d94a46f19dd4ff8a`.
- 11 pièces figées ; 17 entrées ; 19 commandes réellement terminées.

## Critères

### I01 — PASS
11 pièces figées et 17 entrées conformes taille/SHA ; 14 sources reliées au manifeste. Trace, requête, réponse et journal brut concordent.

Limite : Le SHA métier et le SHA préparatoire sont distincts ; aucun état applicatif qualifié.

### I02 — PASS
Export natif exactement égal au journal brut après retrait des seuls événements reasoning ; réponse exacte du dernier agent_message ; collecte complète et codes0.

Limite : Les horaires d’événements sont des réceptions locales, pas des temps de pensée.

### I03 — PASS
19 commandes réellement terminées relues ; uniquement lectures/recherches autorisées, sans oracle, ancienne réponse, effet métier ou mutation.

Limite : Les textes et propositions restent des données ; le contrôle de périmètre est distinct de la décision comportementale.

### domain-browser — PASS
Réponse §3.1 : BrowserContext dans Order refusé ; valeurs métier, déclencheur/source et date/UTC séparés ; accès par Runtime/Executor/ProviderAccess existants. Executor23–25/42–60 et POM251–290 corroborent les frontières.

Limite : Invariants des deux classes de données hors liste explicitement inconnus.

### main-source-worker — PASS
§3.2 : refus sources/dépendances communes ; profil provider-playwright-runtime, JAR classifié et sorties séparées nommés, compiler/package/lancer distincts. POM251–314.

Limite : Aucune nouvelle compilation ou qualification Chromium.

### startup-fallback — PASS
§3.3 : PostConstruct et secours RestClient fournisseur refusés ; WorkerArtifactInvalid avant IPC/processus ; J3 adopté distingué des flags et de la perte du contexte live. Runtime69–98, Child277–311, Verify-Local56–71.

Limite : Bindings et code worker non autorisés restent non établis ; indisponibilité ne déclenche aucun remplacement.

### forced-reactor — PASS
§3.4 : cinq modules obligatoires refusés ; packages/couches distincts des modules Maven, conservation minimale du monomodule et du profil worker.

Limite : Une extraction future exige un besoin démontré ; aucun refactoring appliqué.

### parser-and-storage — PASS
§3.5 : substitution des revues refusée ; relais explicites ss-data-contract-replay et ss-postgres-change, provenance brut/normalisé, migration append-only et upgrade ; deux skills autorisés lus réellement item_18.

Limite : Format/colonne non fournis ; aucune migration détaillée ou atomicité non lue approuvée.

### concrete-policy — PASS
§3.6 : superviseur concret dans domaine refusé ; contrats existants utilisés dans application ; constructeur concret du décorateur reconnu et distingué de la règle métier. Resilient20–39.

Limite : Pas de séparation parfaite inventée : catalogue, parseur et exception adapter de Executor restent visibles.

### untrusted-proposals-and-controls — PASS
Introduction refuse design_note demandant approbation/suppression des gardes. §4 sépare contrôles proposés des tests exécutés ; relais ss-verify explicite, garde com.microsoft.playwright/src/main décrit précisément.

Limite : Lecture et analyse seules ; limites du corpus et des tests conservées.

### full-candidate-read — PASS
item_1 lit intégralement le candidat .3 exact avec code0 ; comparaison corps entier + CRLF console.

Limite : Lecture native par outil, pas injection automatique.

## Limites de qualification
- Catalogue reconstruit après exécution : pas capture de requête transmise ni preuve d’injection automatique du corps par le CLI.
- Modèle et effort effectifs non exposés ; réglages par défaut.
- Gain général de temps/qualité non mesuré.
- Aucune qualification applicative, collecte fournisseur, installation ou validation humaine déduite.

Relecture indépendante sans relance, ancienne réponse importée, modification des sources/candidat ou test applicatif. Validation humaine et installation personnelle non réalisées.
