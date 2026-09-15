# Utilité du Local Lab avant match et en live — protocole et première scorecard

**Date de cette analyse : 15 septembre 2026. État de la décision : PARTIAL.**

**Le corpus établit une utilité locale pour constituer et relire des dossiers, ainsi que pour observer l’évolution de données pendant certaines campagnes. Il ne démontre pas encore une qualité suffisante sur une population représentative avant match, ni une continuité live durable avec la politique actuelle V11.** Il ne permet pas de classer SofaScore face à Highlightly ou à un autre fournisseur.

Statuts conservés : **EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY**.

Cette analyse est exclusivement documentaire. Aucun réseau, serveur applicatif, navigateur, accès PostgreSQL/Docker, test applicatif ou collecte n’a été lancé. Les sources sont inchangées. Les chiffres historiques ci-dessous sont attribués aux documents fournis ; seuls les rapprochements arithmétiques explicitement indiqués ont été recalculés ici. Le worktree actif, son commit et la configuration réellement utilisée par l’opérateur ne sont pas vérifiés : les bases et versions mentionnées sont les provenances déclarées dans le corpus.

## 1. Registre des preuves et des populations

Les clés de cette table portent la population, la fenêtre et la source de chaque ligne de scorecard. Elles empêchent d’additionner des populations incompatibles.

| Clé | Population et unité | Temps et politique | Source et portée |
|---|---|---|---|
| B30 | Quatre campagnes J8, vingt tentatives directes, six endpoints logiques ; un dossier ciblé, match finished de Coppa Italia Serie C 26/27 | [2026-08-30T09:24:51.088792500Z, 2026-08-30T09:44:03.269596500Z) ; asOf inclusif = fin de fenêtre. Première tentative mesurée 09:35:55.281909Z | [Rapport J8 du 30 août](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/benchmark/J8-BENCHMARK-REPORT-20260830.md) : FULL_ATTEMPT_LEDGER. Quatre campagnes + vingt tentatives ne constituent pas vingt-quatre appels. |
| H31 | Série historique D1/D2/D3, arrêtée après vingt tentatives ; distincte de B30 | Autorité ADR-SS-002 v1.0 consommée et terminée. Délai de départ D1 : NOT_MEASURED | [ADR-SS-002, §11](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/ADR-SS-002-bounded-multi-dossier-provider-robustness.md) ; rapport primaire de la série absent du périmètre. Ne pas fusionner ces vingt tentatives avec celles de B30. |
| T07-A | Campagne eeffbed1-c589-485c-94fe-f09ebc51ec2d : huit cibles, 181 tentatives | D = 210 s ; réceptions observées du 2026-09-07T16:54:18.734Z au 17:28:11.298Z | [Résumé temporel](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/validation/WO058-TEMPORAL-SUMMARY-20260907.json) ; population de campagne live historique, distincte du ledger J8. |
| T07-B | Campagne 11a22482-5997-4590-9058-3a89d55e8973 : seize cibles, 756 tentatives. Unité temporelle : (campagne, match, famille) | live-v2, D = 450 s ; réceptions du 2026-09-07T17:30:24.947Z au 19:54:27.534Z ; capture/asOf 20:02:42.611690Z | Même résumé et [rapport temporel du 7 septembre](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/validation/WO058-PREMATCH-TEMPORAL-20260907.md). Deux campagnes totalisent 937 tentatives ; cela ne prouve pas vingt-quatre matchs distincts. |
| R10 | Trois campagnes réelles live-v8 : respectivement dix, huit et huit cibles ; unité pression : départ REQUEST_SENT documenté | 10 septembre 2026 ; chaque intervalle court du début au premier HTTP 403, détaillé en §4.3 | [Observations V8 du 10 septembre](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/validation/WO058-REAL-LIVE-V8-403-OBSERVATIONS-20260910.md). Population choisie de trois campagnes arrêtées, sans groupe de contrôle. |
| Q14 | Qualification V11 : seize scénarios de replay ; séparément une exécution native loopback de quatre requêtes avec un worker | Profil daté du 14 septembre ; exécution native du 2026-09-13T23:03:55.200642100Z au 23:04:07.973168900Z | [Profil V11](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/validation/WO060-LIVE-V11-PROFILE-20260914.json), [preuve native](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/validation/WO060-J3-LIVE-WORKER-20260914.json) et [rapport de réalisation](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/validation/WO060-J3-IMPLEMENTATION-20260914.md). Zéro appel fournisseur. |

Le résumé T07 fournit cinq checkpoints UTC : **19:19:00, 19:39:00, 19:46:00, 19:47:00 et 20:02:42.611690**, le 7 septembre. Il contient des observations sélectionnées d’Elche et Nantes, pas la matrice exhaustive des 937 tentatives. Les 96 comparaisons de curseurs et quinze tests offline sont des résultats rapportés, non réexécutés ici.

## 2. Politique historique et fonctionnement actuel

### 2.1 Autorité et générations

| Domaine | Lecture historique correcte | Lecture actuelle du corpus |
|---|---|---|
| Transport et déclenchement | ADR-SS-001 v1.4 porte le transport Playwright local, les gestes manuels et les périmètres dédiés. FlareSolverr reste écarté. | [ADR-SS-001 amendé v1.6](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md) et [ADR-SS-007 v0.3](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md) ajoutent les exceptions J3 et le clic tournoi ; elles n’arment pas automatiquement J4/J5. |
| J8 et preuve multi-dossier | Les deux go J8 du 30 août sont consommés ; aucun troisième essai n’en découle. ADR-SS-002 v1.1 accepte un cadre de nouvelle série, sans autorité exécutoire de collecte. | La borne J3 passe à 35 selon [ADR-SS-006 v0.2](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/ADR-SS-006-j3-manual-pagination-cap-35.md). Les anciens plafonds agrégés 38 nouveaux / 58 cumulés ne sont plus réutilisables pour un futur D1/D2/D3. Ne pas leur substituer automatiquement 48/68 : une enveloppe recomposée exige sa décision et son WO distincts. |
| Prématch du 7 septembre | Dans T07-B, V2 attend J4 inprogress avant LINEUPS. La modification V3 du même jour ajoute LINEUPS après J4 notstarted puis à D ; les observations de l’ancienne campagne ne qualifient pas cette modification. | Les règles V9 héritées par V10/V11 conditionnent les familles aux faits J4 : pas de statistiques/incidents pour notstarted/postponed/delayed ; finalResultOnly=true arrête la cible ; compositions selon le fait de capacité événement. |
| Cadence live | V1–V3 utilisent D = max(60, 30 × (N−1)) s. Les évolutions V4/V5/V6/V7/V8 ont leurs propres enveloppes, pauses, 404 et budgets. R10 relève de V8, avec borne locale 45/min, pas de V11. | [ADR-SS-005 v0.12](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) : nouvelles préparations V11 ; huit rencontres au maximum, nominal 8 × 4 = 32 départs comptabilisés/min, bornes partagées 35/60 s et 2 100/h, 2 500 appels/événement, 20 000/campagne, quatre heures. Ce sont des bornes locales et des hypothèses de qualification. |
| Phases et silences | L’ancienne cadence uniforme n’explique pas à elle seule le comportement actuel. | Selon [l’architecture live](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/architecture/LIVE-J4-J5-CAMPAIGNS.md), V11 hérite notamment de la pause de quinze minutes sur mi-temps explicite, du J4 seul de vérification, des conditions J5 et des suspensions par famille après 404. Une cible d’une minute n’implique pas quatre familles reçues chaque minute en toute phase. |
| Pause J3 | Aucune nouvelle capacité n’est appliquée rétroactivement aux manifestes V1–V10. | V11/protocole worker 10 : tous les départs live sont suspendus, l’échange engagé est publié, un contexte J3 neuf temporaire utilise le même worker ; nettoyage prouvé et terminal durable précèdent un nouveau J4 dans le contexte live conservé. Budget et échéance restent inchangés ; J5 abandonnés comptés manqués, aucun rattrapage en rafale. |
| J3 durable | Les campagnes J3 à plafond 25 conservent leur plafond historique. Un cache de pages ne suffit pas à attester une collecte complète. | [Guide J3 actuel](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/runbooks/J3-AUTOMATION-AND-DURABLE-CATALOG.md) : préférence automatique initialement active dans le serveur Web local entièrement configuré, quotidien/horaires durables, dernier succès par date, pages contiguës 1..35, terminal hasNextPage=false, ordre borné à vingt minutes. Un échec ne remplace pas le dernier succès et ne crée pas de retry automatique. |
| Tournoi | Les anciennes confirmations Web appartiennent à leur version. | Complément du 15 septembre : clic direct pour au plus un GET de découverte tournoi ou son cache ; import d’un corps local séparé. Un filtre, une consultation de catalogue ou un lien J5 ne démarre aucune campagne J5. |

Les refus fournisseur persistants, les gardes locaux, la provenance et le nettoyage restent applicables. Un arrêt, une perte de contexte, une veille ou un redémarrage ne recréent pas automatiquement le live. J3 ne démarre pas le Lab et les tests standards n’ouvrent aucun navigateur.

### 2.2 Écarts documentaires à conserver visibles

- [AGENTS du corpus](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/AGENTS.md) mentionne encore ADR-SS-005 v0.8 et l’exception J3 v0.2 ; les amendements plus récents des ADR portent V11/v0.12 et le clic tournoi v0.3. Ce décalage de renvoi n’efface pas les amendements adoptés.
- [Le runbook J8](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/runbooks/J8-BENCHMARK.md) garde des prérequis V28 et un démarrage applicatif historiquement sans automatismes. **Démarrer aujourd’hui un serveur Web configuré peut déclencher J3 durable**. Cette analyse utilise donc les fichiers fournis. Le parcours de lecture J8 reste conceptuellement sans transport, mais il faut distinguer la route de lecture du démarrage de l’application qui l’héberge.
- Le rapport B30 affiche un état global MEASURED tout en laissant plusieurs dimensions NOT_MEASURED. Nous conservons le rapport gelé, publions les états par métrique et donnons à cette évaluation d’utilité l’état PARTIAL.
- Le texte du rapport B30 décrit le délai tardif à partir d’une version fournisseur précédente ; [l’architecture J8 actuelle](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/docs/architecture/J8-BENCHMARK-METRICS.md) et le lecteur imposent l’état canonique direct antérieur applicable. Aucun délai J6 n’est recalculable dans B30 : sa métrique automatique est NOT_MEASURED.
- Le rapport de réalisation du 14 septembre dit encore que le WO reste actif ; ADR-SS-007 consigne un suivi opérateur et une autorisation de fusion ultérieurs. Ce corpus permet de dater ces déclarations, pas de vérifier l’état Git actuel ni une fusion effective.

## 3. Protocole reproductible

### 3.1 Question et populations à figer

La question utile est : **pour quelles rencontres, familles et échéances le Lab fournit-il une donnée lisible, suffisamment complète et assez récente pour l’usage considéré, à quel coût et avec quelles interruptions ?**

Pour chaque édition, figer avant calcul :

1. La période UTC [from,to), un asOf unique, les identifiants de campagne/collecte, la politique de chaque manifeste, les parseurs/projections, les preuves directes et la date de chaque observation.
2. Une population avant match : rencontres ciblées et compétitions/saisons connues, avec séparation par statut J4, capacité événement/tournoi et source de découverte. Conserver aussi les cibles sans données ou arrêtées. Pour mesurer la couverture du catalogue, il faut un univers de tournois/rencontres attendu, absent ici.
3. Une population live : les mêmes identités à checkpoints communs, ventilées par génération, nombre simultané de matchs, phase sportive, durée réellement suivie et cause de fin. Rapporter séparément phases prématch, jeu, mi-temps, suspension, finalisation et pause J3.
4. Trois populations de provenance distinctes : FULL_ATTEMPT_LEDGER, RESPONSE_ONLY, LEGACY_BASELINE. Identifier en plus cache, import manuel et fixture ; ils n’établissent pas une accessibilité fournisseur. Le ledger live constitue encore un autre périmètre, à relier par identifiants avant toute comparaison avec J8.
5. Un comparateur éventuel sur les mêmes matchs/familles/checkpoints, avec identité contrôlée et référence temporelle attestée. Highlightly reste absent comme preuve comparable. L’étude interne peut comparer des checkpoints et des générations en descriptif, sans attribuer causalement leurs écarts au seul fournisseur.

**Unités :** réponse/page pour transport et parsing ; tentative pour coûts/erreurs ; (campagne, match, famille, checkpoint) pour disponibilité et temporalité ; match distinct pour dossier ; ordre/date/page pour J3. Une page J3 avec plusieurs rencontres reste une réponse. Des répétitions du même match ne sont pas des matchs indépendants.

### 3.2 Checkpoints

**Reproduction immédiate :** utiliser B30 avec sa fenêtre et son asOf exacts, puis les cinq checkpoints T07. R10 utilise ses trois fenêtres de campagne, Q14 sa fenêtre native propre.

**Grille proposée pour une étude ultérieure, sans collecte déclenchée :**

- Avant match : T0−24 h, −60 min, −30 min, −10 min, −5 min et T0.
- Live : premier J4 inprogress constaté, puis checkpoints toutes les cinq minutes ; annoter première mi-temps/mi-temps/reprise, premier état terminal, puis Tfin+5 et +30 min si des preuves existent.
- Ajouter les instants avant/après pause J3, arrêt, panne ou refus, puis la capture finale. Publier la durée ouverte depuis la dernière réception à chacun.
- T0 est l’horaire annoncé connu au moment retenu ; toute modification d’horaire est historisée. L’heure planifiée ne remplace jamais un statut sportif observé. Les checkpoints métier ne prescrivent aucune requête supplémentaire et restent NOT_MEASURED en l’absence de preuve.

Les seuils d’âge/dispersion acceptables doivent être fixés par usage avant un verdict. Le seuil d’écran de deux intervalles décrit la santé de collecte ; il n’est pas un seuil universel de fraîcheur métier. Jusqu’alors, les classes restent UNCLASSIFIED.

### 3.3 Sélection temporelle et absence de connaissance future

Selon [JdbcJ8BenchmarkReadStore](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/JdbcJ8BenchmarkReadStore.java) :

- Les tentatives J8 sont sélectionnées par started_at dans [from,to), avec created_at et started_at ≤ asOf.
- Les unités sont sélectionnées par déclaration dans la fenêtre ou présence d’une tentative sélectionnée. Une déclaration sans départ n’est pas une tentative.
- Les résultats sont visibles seulement si created_at et resolved_at ≤ asOf. Une réponse connue après to peut donc renseigner une tentative antérieure à to, si elle est connue à asOf. Une tentative sans résultat à asOf reste dans le dénominateur, avec INCOMPLETE_ATTEMPT et état PARTIAL.
- Les occurrences historiques se bornent sur requested_at, avec preuves de réception/normalisation disponibles à asOf. Le statut mutable actuel d’un snapshot ne reconstitue pas son état passé.
- Pour qualifier le dossier, prendre les cinq composants directs courants à asOf, même antérieurs à from. Ne pas réduire le dossier à ses seuls nouveaux appels.
- Pour les checkpoints live, ne rendre lisible qu’un succès résolu à T, en conservant séparément réception et publication. Les timestamps SQL fournis approchent la disponibilité historique ; ils ne reconstituent pas l’instant exact du commit.

Le contrat HTTP J8 exige deux instants terminés par Z, de 1 à 64 caractères sans contrôle, from < to ; une fin future est intersectée avec asOf, et une fenêtre entièrement future devient vide. L’export exige from, to et asOf explicites avec asOf ≥ to. **Ces contrats sont décrits, aucun n’a été exécuté.**

### 3.4 Formules et règles de lecture

| Mesure | Numérateur / dénominateur ou calcul | Règle de validité |
|---|---|---|
| Disponibilité à T | Familles lisibles / familles attendues pour l’usage ; publier aussi lisibles / familles admissibles selon la politique | Conserver la différence entre besoin utilisateur non satisfait et famille non demandée par la politique. NOT_REQUESTED ne prouve pas UNAVAILABLE. |
| Réponse et parsing | Réponses / tentatives ; PARSED / réponses réellement éligibles au parsing | Définir l’éligibilité ; 404 hors parsing. Ne pas appeler « compatibilité » PARSED / toutes tentatives. |
| Refus, 404, erreurs | Refus 401/403/429 / tentatives ; 404 / tentatives de la famille ; erreurs opérationnelles hors 404 / tentatives | Compter une tentative une fois par métrique. Un refus suivi d’une erreur locale conserve la preuve HTTP mais n’est pas doublé dans le total d’erreurs. |
| Complétude | Σ signaux présents / Σ signaux attendus, uniquement attendus > 0 | Ventiler famille, compétition, saison, état et parseur. Jamais moyenne simple des pourcentages. UNKNOWN, MISSING, PARTIAL, EMPTY_VALID et UNAVAILABLE restent distincts. |
| Dossiers | Dossiers exploitables / matchs ciblés distincts ; strictement complets / matchs ciblés distincts | Exploitable : état canonique + détail + trois J5 directs parse-compatibles, aucune famille indisponible/absente. COMPLETE, PARTIAL ou EMPTY_VALID admis ; strict : trois J5 COMPLETE. |
| Latence transport | Durées des réponses directes éligibles | n, min/P50/P95/max en ms, nearest-rank J8 : rang ceil(p×n), indexé à 1. Ne pas mélanger famille et génération. |
| Âge, contrôle et changement | Âge = T−réception du dernier contenu lisible ; âge du contrôle 304 distinct ; durée depuis dernier changement sémantique | Réception répétée ne signifie pas changement ; 304 ne rajeunit pas la réception du contenu 200. Un contenu inchangé aux lectures ne prouve pas l’absence de changement entre elles. |
| Dispersion | max(receivedAt)−min(receivedAt), sur familles présentes | Publier k/3 familles dynamiques et k/4 totales. Une famille seule ne démontre aucune synchronisation ; le résumé T07 laisse la dispersion absente dans ce cas. |
| Continuité | Intervalles entre réceptions du même match/famille, plus durée ouverte jusqu’à T ; couverture temporelle = durée satisfaisant le seuil / durée de suivi requise | Inclure arrêts et trous ; distinguer frontières de phase, cycles finaux et intervalle périodique. Ne pas effacer la queue ouverte en ne gardant que les paires terminées. |
| Corrections tardives | Comptes J6 directs LATE_ENRICHMENT / LATE_CORRECTION ; délai depuis l’état canonique direct terminal applicable jusqu’à réception tardive | Sous-série directe ; LOCAL_REPARSE, SYNTHETIC_CHANGE, inchangé et doublon séparés. Heure de version de famille ≠ heure canonique terminale. |
| Coût J8 | Découverte = J3 + tournoi, compte absolu ; marginal = (J4 phase 2 + J5)/dossiers exploitables ; effectif = tous appels directs/dossiers exploitables | Exact seulement avec FULL_ATTEMPT_LEDGER. Dénominateur nul : compte visible, ratio NOT_MEASURED. Une déduplication après transport coûte bien une tentative. |
| Coûts complémentaires | Départs physiques, appels logiques, octets, temps opérateur, stockage, calcul/maintenance ; coût par dossier/checkpoint utile | Publier chaque unité séparément. Sans tarif attesté et périmètre de facturation, aucun prix ni coût monétaire comparatif. |

### 3.5 Pression live et HTTP 304 : séparation obligatoire

[JdbcLiveCampaignPressureReadStore](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-N01/corpus/src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcLiveCampaignPressureReadStore.java) lit les diagnostics contenant requested_at pour une campagne, indépendamment de leur phase finale. Il calcule les pics sur **(t−60 s,t]** et **(t−5 min,t]**, ce qui diffère de la fenêtre J8 [from,to).

Pour V9/V10/V11, ce lecteur exclut un NOT_MODIFIED/NONE/HTTP_304 **seulement avec preuve positive complète** : diagnostic COMPLETE/304, réponse achevée, autorisation antérieure au départ, absence de réception et de nouvelle projection/normalisation. Un 304 mal formé ne doit pas masquer un départ.

En conséquence :

- Le nombre affiché représente une pression logique de collecte. Le coût physique doit aussi compter les départs des 304 validés, dont les diagnostics persistent.
- Un 304 validé donne un nouvel instant de contrôle aux en-têtes, mais pas un nouveau contenu, snapshot, octet métier ni âge de réception. Publier les deux temporalités.
- Réservation, autorisation, REQUEST_SENT, en-têtes, réception et résultat sont six faits distincts.
- Le lecteur n’accepte ni fenêtre ni asOf ; une lecture présente ne reproduit pas un checkpoint passé. Il faut une extraction temporelle explicite pour cela.
- Les budgets partagés persistants ne sont pas le pic d’une seule campagne : ils peuvent contenir d’autres campagnes et J3, et utilisent leurs règles de réservation/clôture. Le rapprochement avec la pression affichée exige la réconciliation des identifiants.
- La présence du ledger live ne prouve pas sa couverture exacte par FULL_ATTEMPT_LEDGER J8. Ne pas utiliser 590 REQUEST_SENT V8 comme dénominateur J8.

### 3.6 Reproduction et livrable de chaque édition

Conserver un manifeste de sources immuables, les identifiants de population, fenêtres/asOf, politiques/parseurs, règles d’exclusion et version des formules. Trier de façon stable les preuves puis recalculer les métriques et publier les comptes avant les ratios.

Pour B30, l’empreinte de population publiée est **c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726**. Le hash du bloc documentaire est distinct. Le rapport et les résumés n’exposent pas tous les identifiants ni les données nécessaires à sa reconstruction. Le service de calcul/sérialisation J8 et le ledger complet ne figurent pas dans les fichiers autorisés : **reproduction des agrégats documentés possible, reproduction intégrale du hash/rapport non démontrée ici**.

Pour T07, le hash d’extraction cité est **838bab698d7d82a5176ac7be8a2dddea018987bb4856c1960222131e6cf49d80**. C’est une référence de provenance, pas une permission d’ouvrir son fichier privé. Les sources sortantes n’ont pas été suivies.

## 4. Première scorecard

MEASURED signifie mesuré **dans la population annoncée**, sans jugement de qualité ; PARTIAL indique une couverture/preuve incomplète ; NOT_MEASURED indique l’absence du dénominateur ou de la preuve requise. Les « valeurs rapportées » proviennent de mesures historiques ; elles ne sont pas de nouvelles exécutions.

### 4.1 Accessibilité, dossier et coût — B30

Toutes les lignes utilisent la fenêtre/asOf B30 et le rapport J8 du §1.

| Capacité / unité | Numérateur / dénominateur | Valeur | État | Limite |
|---|---|---|---|---|
| Réponse, tentative directe | 20 / 20 | 100 % | MEASURED, rapporté | Une courte fenêtre locale ; aucun engagement de disponibilité durable. |
| Parsing, réponse éligible | 20 / 20 | 100 % | MEASURED, rapporté | Six versions de parseur ; zéro transition observée ne démontre pas une stabilité à long terme. |
| Refus ; indisponibilité ; erreur hors 404 | 0/20 ; 0/20 ; 0/20 | 0 % chacun | MEASURED, rapporté | Ces zéros n’annoncent pas une absence future de refus. |
| Découverte J3, page/réponse | 15 réponses parsées / 15 tentatives | Pages 1..15 observées | MEASURED | Ni nombre de rencontres couvertes ni exhaustivité du catalogue global. |
| Dossier exploitable, match distinct | 1 / 1 | 100 % | MEASURED | Match finished ; aucune preuve de performance prématch sur ce dossier. |
| Dossier strict, match distinct | 0 / 1 | 0 % | MEASURED | Statistiques COMPLETE, incidents PARTIAL, compositions PARTIAL. |
| Signaux par famille, observation | 1 observation/famille ; sommes présentes/attendues non fournies | Statistiques 100 % ; incidents 91,62 % ; compositions 99,03 % | PARTIAL pour reconstruction indépendante | Valeurs MEASURED dans le rapport ; impossible de reconstituer les fractions exactes à partir de ces seuls pourcentages. |
| Surcoût de découverte, appels | 15 J3 + 1 tournoi ; sans division | 16 appels | MEASURED, calcul vérifiable | Coût absolu de cette découverte. |
| Coût marginal, appels/dossier exploitable | (1 J4 phase 2 + 3 J5) / 1 | 4 appels/dossier | MEASURED, calcul vérifiable | N’inclut pas les seize appels de découverte. |
| Coût effectif, appels/dossier exploitable | 20 / 1 | 20 appels/dossier | MEASURED, calcul vérifiable | Le faible coût marginal ne décrit pas le coût complet. |
| Corrections tardives, version directe | Population automatique éligible absente | — | NOT_MEASURED | Le cas historique de revue humaine est hors population automatique B30. |

Latences B30, nearest-rank, toutes en millisecondes :

| Famille | n | Min | P50 | P95 | Max | État / limite |
|---|---:|---:|---:|---:|---:|---|
| SCHEDULED_EVENTS | 15 | 300 | 348 | 3 292 | 3 292 | MEASURED, rapporté ; quinze pages, pas quinze matchs |
| TOURNOI | 1 | 247 | 247 | 247 | 247 | MEASURED ; une observation |
| DÉTAIL | 1 | 249 | 249 | 249 | 249 | MEASURED ; une observation |
| STATISTIQUES | 1 | 241 | 241 | 241 | 241 | MEASURED ; une observation |
| INCIDENTS | 1 | 93 | 93 | 93 | 93 | MEASURED ; une observation |
| COMPOSITIONS | 1 | 84 | 84 | 84 | 84 | MEASURED ; une observation |

Ces latences ne mesurent ni l’âge des données à l’écran ni le retard d’une information sportive depuis sa publication source.

### 4.2 Avant match et temporalité — T07

| Capacité / population et checkpoint | Unité et numérateur / dénominateur | Valeur | État | Limite / source |
|---|---|---|---|---|
| Issues locales, T07-A | Résultats PARSED / tentatives ; 404 / tentatives | 180/181 = 99,45 % ; 1/181 | MEASURED, ratio recalculé | Résumé T07. Taux d’issues parsées, pas taux de compatibilité du parsing. |
| Issues locales, T07-B | Même définition | 755/756 = 99,87 % ; 1/756 | MEASURED, ratio recalculé | Total descriptif des deux campagnes : 935/937 = 99,79 % ; ne pas confondre avec continuité. |
| LINEUPS prématch, Elche, 19:19Z | Tentatives LINEUPS avant première publication J4 inprogress | 0 ; famille NOT_REQUESTED | MEASURED pour le non-déclenchement ; disponibilité fournisseur NOT_MEASURED | V2. Un manque d’utilité à ce checkpoint peut relever de la politique du Lab. |
| Première composition Elche | 1 réception LINEUPS − première réception J4 inprogress | 19:32:33.598Z − 19:32:24.192Z = 9,406 s | MEASURED | Snapshot 2236, occurrence 2203, lineups-v2, confirmée 11+11, COMPLETE 100 %. Ce n’est pas le délai depuis publication fournisseur. |
| Écart au coup d’envoi prévu Elche | Même réception − horaire prévu 19:30Z | 153,598 s | MEASURED pour cet écart | Le démarrage réel du match n’est pas attesté par cet horaire. |
| Couverture Elche, 19:39Z | J4 + trois familles lisibles / quatre ; trois J5 COMPLETE / trois | 4/4 ; 3/3 | MEASURED sur un checkpoint | Âges J4/statistiques/incidents/LINEUPS : 395,808 / 392,689 / 389,544 / 386,402 s. Complétude et âge sont distincts. |
| Nantes, 19:46Z | Âge J4 ; dispersion J4/statistiques/incidents, 3/3 présentes | 399,622 s ; 28,077 s | MEASURED | J4 0–0, Halftime, reçu 19:39:20.378Z ; snapshot 2222, occurrence 2217. Checkpoint reconstruit, capture écran initiale seulement à la minute. |
| Nantes, 19:47Z | Âge J4 ; dispersion dynamique 3/3 ; quatre familles 4/4 | 6,575 s ; 428,077 s dans les deux ensembles | MEASURED | J4 1–0, 2nd half, reçu 19:46:53.425Z (2278/2245). Statistiques/incidents : âges 434,652/431,545 s ; la famille récente n’actualise pas les autres. |
| Continuité T07-B à capture | Sept matchs encore actifs ; âge maximal par famille | J4 914,772690 s ; statistiques 942,934690 s ; incidents 939,809690 s ; LINEUPS 936,678690 s | MEASURED pour les âges ; continuité globale PARTIAL | Dernière réception globale 19:54:27.534Z ; queue ouverte 495,077690 s. RUNNING et zéro tentative pendante ne prouvent pas un collecteur actif. |
| Catalogue/prématch représentatif actuel | Rencontres pertinentes complètes à un checkpoint / population pertinente connue | — | NOT_MEASURED | Absence de cohorte V11/J3 durable réelle avec catalogue et checkpoints prematch exhaustifs. |

Intervalles terminés T07-B, toutes phases/finalisations confondues, par paire de réceptions successives du même match/famille :

| Famille | Paires | Min (s) | Médiane rapportée (s) | Max (s) | P95 |
|---|---:|---:|---:|---:|---|
| J4 | 230 | 450,148 | 450,404 | 469,312 | NOT_MEASURED |
| Statistiques | 154 | 450,192 | 450,4265 | 469,195 | NOT_MEASURED |
| Incidents | 154 | 450,222 | 450,4275 | 469,169 | NOT_MEASURED |
| Compositions | 154 | 450,213 | 450,429 | 469,186 | NOT_MEASURED |

Ces médianes historiques ne sont pas présentées comme des P50 J8 recalculés : la convention du résumé et les données de détail ne permettent pas de les remplacer. Les sous-séries périodiques non finales sont disponibles séparément (171 paires J4 et 145 par J5) ; il faut choisir cette sous-population pour comparer une cadence nominale. L’interruption PostgreSQL documentée après les dernières réceptions explique un trou local étayé par les observations ; elle ne prouve pas une panne du fournisseur.

### 4.3 Refus et pression — R10

Unité : départ REQUEST_SENT documenté ; fenêtres glissantes (t−W,t]. Les valeurs proviennent du rapport R10.

| Campagne | Début → premier 403 (UTC, 10/09) | Cibles | Départs observés | Pic 60 s / 5 min | Durée avant 403 | Famille refusée |
|---|---|---:|---:|---|---|---|
| 0ac5c1bd-b6cc-40bb-b63c-a59e05816c23 | 16:37:41.704620 → 16:46:42.885574 | 10 | 147 | 37 / 89 | 9 min 01,181 s | Détail |
| 0c5c1386-3519-46cb-bffc-634ffd351c0f | 16:49:21.554658 → 16:57:26.463956 | 8 | 180 | 29 / 113 | 8 min 04,909 s | Détail |
| 01542403-5099-4da9-9f22-4903004df17d | 17:05:46.481947 → 17:17:30.994046 | 8 | 263 | 29 / 113 | 11 min 44,512 s | Compositions |

**MEASURED, rapporté :** trois arrêts avec HTTP 403 complets sur trois campagnes de cette population ; 590 départs documentés au total, calculés 147+180+263. Dans la troisième : 74 détail + 43 statistiques + 73 incidents + 73 compositions = 263.

**PARTIAL pour le risque général :** le pic maximal 37 est inférieur à la borne V8 de 45/min, mais un 403 est tout de même survenu. Cela ne fixe aucun seuil fournisseur, ne démontre pas sa cause, ne désigne pas une famille responsable et ne garantit pas qu’une cadence plus basse fonctionne durablement. Le trafic extérieur au Lab n’est pas observé. Aucun taux d’erreur J8 n’est dérivé de ces trois lignes.

### 4.4 Politique actuelle et dimensions encore absentes

| Capacité / population | Unité, formule ou dénominateur | Valeur | État | Limite |
|---|---|---|---|---|
| Admission locale V11, Q14 | Seize scénarios ; capacité qualifiée du profil | 8 rencontres ; 9 refusées selon rapport | MEASURED localement, rapporté | Enveloppes explicitement hypothétiques, aucun appel fournisseur. |
| Charge nominale V11 | 8 × 4 familles / minute | 32/min ; plafonds 35/60 s, 2 100/h | Valeurs normatives ; débit fournisseur NOT_MEASURED | Les budgets partagés, phases, 304 et pauses modifient le nombre réellement compté. |
| Pause et isolation natives Q14 | Une exécution, quatre requêtes loopback, un worker | Pause jusqu’au J4 repris : 9 290 ms ; intervalles 3 164 / 3 093 / 3 094 ms | MEASURED, rapporté | J4 → deux pages J3 → J4 ; aucun transfert de session ni recréation live ; ne représente pas une collecte de 35 pages. |
| Latence V11 réelle | Distribution des durées fournisseur par famille | — | NOT_MEASURED | Les requestMillis 300/300/350/300 et processingMillis 500/400/400/450 sont des hypothèses de replay, pas des mesures fournisseur. |
| Fraîcheur live V11, incidence d’une pause J3 | Âges/dispersion/slots manqués avant-pendant-après ; couverture sur durée requise | — | NOT_MEASURED chez le fournisseur | Le scénario de pause de vingt minutes qualifie les règles ; il ne mesure pas la fraîcheur obtenue en exploitation. |
| Exactitude sportive / contrôle externe | Champs ou événements concordants / champs ou événements vérifiables | — | NOT_MEASURED | Aucun contrôle indépendant d’identité, horaire, score, statut, incidents ou correction. |
| Retard source | receivedAt − sourcePublishedAt fiable | — | NOT_MEASURED | Horloge source homogène absente ; minute de jeu et heure prévue ne la remplacent pas. |
| Maintenabilité et coût opérateur | Temps d’adaptation, restauration, intervention ; coût par dossier utile | — | NOT_MEASURED | Aucun chronométrage correspondant dans ce corpus. Durée d’une commande de test ≠ temps de maintenance. |
| Valeur analytique / comparaison fournisseur | Même population et checkpoints, comparateur attesté | — | NOT_MEASURED | Déclaration Highlightly sans extrait comparable ; aucun score de supériorité ni effet sur une décision de pari établi. |

## 5. Comparaisons déjà possibles et preuves manquantes

| Comparaison | Ce qui est déjà possible | Preuve nécessaire pour aller plus loin |
|---|---|---|
| Dossier exploitable vs strict | B30 : 1/1 exploitable, 0/1 strict ; coût marginal 4 contre effectif 20 | Plusieurs matchs et cohortes prématch/live homogènes, signaux présents/attendus exacts. |
| Famille présente vs réellement récente | Elche et Nantes aux checkpoints T07 : contenu COMPLETE et pourtant âgé ; J4 récent avec autres familles anciennes | Matrice exhaustive par match/famille, seuils métier, résultats/publications et pauses/arrêts horodatés. |
| Cadence annoncée vs continuité | T07-B : intervalles clos proches de 450 s mais âges atteignant environ 943 s à capture | Durée entière de suivi attendue et toutes queues ouvertes ; coût et disponibilité pendant les interruptions. |
| Politique V2 vs V3/V11 | Différence de déclenchement LINEUPS et des conditions J4/J5 explicable | Populations appariées et traces réelles des nouvelles générations ; impossible d’attribuer un gain chiffré à un changement de code seul. |
| Pression V8 vs plafonds V11 | Trois historiques V8 et paramètres V11 juxtaposables en descriptif | Séries réelles V11 comparables, liste de tous départs dont 304, budgets partagés et exposition totale ; aucun essai n’est lancé ici. |
| Durabilité J3 et pause live | Contrat actuel et qualification native établissent une capacité fonctionnelle locale | Exports minimisés des ordres, succès/échecs, pages terminales, provenance/cache, consultations après redémarrage et pauses réelles. |
| SofaScore vs Highlightly/autre | Définition d’une grille commune | Échantillons horodatés autorisés sur mêmes événements/familles, contrôles d’identité et disponibilité à T, politique/version et tarif attestés le cas échéant. |
| Reproductibilité intégrale | Vérification des ratios publiés et exemples temporels | Ledger complet autorisé, listes d’identifiants triées, service de formule/sérialisation et preuves immuables à asOf. |

**Priorité pour la prochaine édition documentaire :** obtenir des exports minimisés déjà autorisés qui réconcilient tentative, départ physique, résultat, dernière donnée lisible et dernier contrôle 304, avec leurs politiques et checkpoints. Conserver les échecs et inconnus dans la population. Ensuite seulement établir les taux de couverture utile et le coût par checkpoint satisfait.

Le constat actuel justifie de poursuivre une évaluation ciblée du Lab : **la construction de dossiers et les diagnostics locaux sont étayés ; l’utilité prématch représentative et la continuité live actuelle restent à démontrer.** Cette note ne constitue ni adoption J9, ni nouvelle autorisation de collecte, ni activation du Lab ou d’une campagne.

