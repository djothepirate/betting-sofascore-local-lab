# Note de relecture — J8 du 30 août 2026

**Conclusion.** Le rapport final documente une campagne locale bornée avec **20 tentatives directes, 20 réponses et 20 parsings compatibles**, puis **un seul dossier ciblé exploitable, mais aucun strictement complet**. Les taux et coûts publiés sont cohérents avec les effectifs affichés. Les empreintes du fichier et du bloc automatique ont été vérifiées sur la copie fournie. Ces résultats établissent une accessibilité et une compatibilité locales observées pour cette population ; ils ne démontrent ni disponibilité générale de SofaScore, ni exactitude sportive, ni qualité live, ni avantage économique relatif.

La validation J8 et le processus de revue humaine sont déclarés réussis dans les preuves du 30 août. Ils préservent les limites par dimension et **J9_DECISION_TAKEN=NO**. Statuts conservés : **EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY**.

## 1. Sources et méthode

Références utilisées, avec numéros de lignes pour rendre chaque contrôle retrouvable :

- **R** — [Rapport J8 gelé](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-H01/corpus/docs/benchmark/J8-BENCHMARK-REPORT-20260830.md).
- **V** — [Validation finale](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-H01/corpus/docs/validation/J8-FINAL-VALIDATION-20260830.md).
- **M** — [Contrat des métriques](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-H01/corpus/docs/architecture/J8-BENCHMARK-METRICS.md).
- **B** — [Runbook J8](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-H01/corpus/docs/runbooks/J8-BENCHMARK.md).
- **A** — [AGENTS du corpus](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/PB-H01/corpus/AGENTS.md).

Relecture documentaire et calculs locaux seulement. Les comptes sont recalculés depuis les tableaux ; les latences individuelles, les dénominateurs de signaux, les identifiants complets des preuves, le code de sélection et la base ne sont pas fournis dans le corpus autorisé. Les références sortantes n'ont pas été ouvertes. Les résultats de tests et campagnes historiques sont des **résultats consignés dans les documents**, sans réexécution ici.

## 2. Population, temps et provenance

La population finale est sélectionnée dans :

- **Fenêtre UTC semi-ouverte** : [2026-08-30T09:24:51.088792500Z, 2026-08-30T09:44:03.269596500Z).
- **asOf** : 2026-08-30T09:44:03.269596500Z, borne inclusive de connaissance des preuves selon R ; la borne de sélection « to » reste exclue.
- **Première / dernière mesure affichées** : 09:35:55.281909Z / 09:43:39.726751Z.
- **Provenance** : FULL_ATTEMPT_LEDGER, dénominateur EXACT_LEDGER ; source humaine SOFASCORE_DIRECT_LOCAL_ENDPOINT.
- **4 campagnes terminées, 20 unités, 20 tentatives** : 15 pages J3 + 1 découverte tournoi + 1 détail J4 phase 2 + 3 familles J5.
- **1 événement distinct ciblé**, et non 20 matchs. Une page peut contenir plusieurs matchs, sans devenir plusieurs réponses. Le nombre total de matchs découverts et la couverture du calendrier ne sont pas publiés.

Sources : R, lignes 8–42, 229–240 ; V, lignes 23–48.

La « Stratum evidence population » vaut **24** dans R, ligne 34. Ce nombre de preuves n'est pas le dénominateur d'appels : les taux utilisent explicitement 20 tentatives. Le corpus ne définit pas suffisamment sa construction pour affirmer sa décomposition technique, même si 4 + 20 = 24.

RESPONSE_ONLY et LEGACY_BASELINE sont absentes de cette fenêtre, population 0, métriques NOT_MEASURED. Les exclusions affichées sont également 0 pour imports manuels, cache hits et observations synthétiques. Une absence de strate n'est pas une preuve de taux d'erreur ou de coût nul dans cette strate. Une réponse persistée historique ne permettrait pas de reconstruire les timeouts non persistés. Sources : R, lignes 20–29 et 107–213 ; M, §§ 5–6.

### Les épisodes restent séparés

| Épisode | Population et résultat documentés | Conséquence |
|---|---|---|
| Première fenêtre J8, 03:39:12.086771Z–04:32:04.339732Z | 20 unités déclarées, **19 tentatives** ; incidents V14 SCHEMA_INCOMPATIBLE ; compositions non atteintes, sans tentative | PARTIAL historique conservé |
| Correctif V15 et qualification WO-017 | Sonde hors ligne, puis campagne distincte de **3 appels**, extérieure à la première fenêtre J8 | Une nouvelle preuve ne réécrit pas le résultat V14 |
| Seconde fenêtre J8, objet du rapport final | **20 tentatives**, toutes PARSED ; incidents V15 | MEASURED / FULL_ATTEMPT_LEDGER |
| Revue de dossiers non ciblés | Exemples historiques partiels et de correction tardive | Hors dénominateurs et agrégats de la fenêtre finale |

Sources : B, §§ 10.5–10.8 ; V, lignes 50–51 et 108–111.

Le protocole choisit un événement terminal déjà J7 HUMAN_VALIDATED et déjà doté de cinq composants directs. Cette sélection favorable, avec un seul événement, ne représente pas un échantillon aléatoire de SofaScore, ni une comparaison prematch/live. Source : B, lignes 295–307.

## 3. Taux et stabilité : calculs vérifiables

Toutes les lignes ci-dessous portent sur la fenêtre finale et FULL_ATTEMPT_LEDGER. Le statut est celui de la métrique automatique, pas une appréciation générale du fournisseur.

| Mesure | Numérateur / dénominateur | Valeur | État et portée |
|---|---:|---:|---|
| Réponse | 20 réponses / 20 tentatives | **100,00 %** | MEASURED, tentatives observées |
| Compatibilité de parsing | 20 PARSED / 20 réponses éligibles | **100,00 %** | MEASURED, parseurs déclarés |
| Refus 401/403/429 | 0 refus / 20 tentatives | **0,00 %** | MEASURED, aucune occurrence observée |
| 404, synthèse tous endpoints | 0 / 20 tentatives | **0,00 %** | MEASURED ; distinct des erreurs opérationnelles |
| Erreur opérationnelle hors 404 | 0 / 20 tentatives | **0,00 %** | MEASURED |
| Tentatives incomplètes | 0 / 20 tentatives | **0,00 % calculé** | Compte source 0 ; aucune issue manquante signalée |
| Déduplications | 5 occurrences / 20 réponses | **25,00 % calculé** | Somme des colonnes par endpoint ; aucune économie d'appel démontrée |

Sources : R, lignes 49–72 et 84–105 ; V, lignes 27–48. Le runbook rapporte aussi vingt réponses HTTP 200 (B, lignes 427–431).

**Les dénominateurs de réponse et de parsing ont ici la même valeur, mais pas la même définition.** Un 404 ne serait pas une réponse éligible au parsing. Une tentative sans résultat à asOf resterait comptée dans les tentatives, le coût et les erreurs, avec INCOMPLETE_ATTEMPT et état PARTIAL. Les erreurs regroupent les catégories terminales prévues ; on ne doit pas additionner deux fois un même appel refusé puis terminé en erreur locale. Aucun cas de ce type n'est observé ici. Sources : M, lignes 203–211 et 258–284 ; R, ligne 59.

Les cinq réponses dédupliquées sont celles du tournoi, du détail et des trois familles J5 ; les quinze pages J3 affichent zéro déduplication. Elles suivent de vraies tentatives directes et ne doivent donc pas être retranchées du coût.

La stabilité locale est mesurée sur **six versions de parseurs**, une par endpoint, sans transition dans la fenêtre : scheduled-events-v1, tournament-scheduled-v1, event-details-v2, event-statistics-v2, event-incidents-v15, event-lineups-v2. Aucune incompatibilité ni contenu inattendu n'y est signalé. Première rupture, dernière rupture et reprise restent NOT_MEASURED. L'incident V14 de la première campagne interdit d'étendre cette stabilité à tout l'historique. Source : R, lignes 84–105 ; B, §§ 10.5–10.7.

## 4. Latences de réponse

Population : réponses directes avec latence éligible, dans la même fenêtre ; unité : milliseconde. Chaque distribution est MEASURED dans R.

| Endpoint | n | Min | P50 | P95 | Max |
|---|---:|---:|---:|---:|---:|
| SCHEDULED_EVENTS | 15 | 300 ms | 348 ms | 3 292 ms | 3 292 ms |
| TOURNAMENT_SCHEDULED_EVENTS | 1 | 247 ms | 247 ms | 247 ms | 247 ms |
| EVENT_DETAILS | 1 | 249 ms | 249 ms | 249 ms | 249 ms |
| EVENT_STATISTICS | 1 | 241 ms | 241 ms | 241 ms | 241 ms |
| EVENT_INCIDENTS | 1 | 93 ms | 93 ms | 93 ms | 93 ms |
| EVENT_LINEUPS | 1 | 84 ms | 84 ms | 84 ms | 84 ms |

Sources : R, lignes 63–70 ; M, § 7.

Convention **nearest-rank** : après tri, P50 = valeur au rang ceil(0,50 × n), P95 = valeur au rang ceil(0,95 × n), rangs comptés depuis 1. Pour J3, ce sont les rangs **8 et 15** : P95 égale donc nécessairement le maximum. Pour n = 1, les quatre statistiques décrivent la même observation.

Les rangs sont vérifiables ; les quinze latences J3 individuelles ne sont pas publiées, donc leur tri et P50 ne sont pas recalculables indépendamment. Aucun percentile global ne doit être obtenu en moyennant les percentiles par endpoint. Ces mesures ne donnent ni âge des données au moment d'une utilisation, ni retard entre événement sportif et réception, ni continuité live, ni performance sous charge. Avec n = 1, elles ne permettent pas de classer durablement la rapidité des familles.

## 5. Complétude et dossiers

Cohorte unique publiée : **Coppa Italia Serie C, Knockout stage**, saison **Coppa Italia Serie C 26/27**, match **finished**. Une observation directe par famille, toutes dans la fenêtre finale.

| Famille | Observation(s) | État des données | Couverture publiée | État de mesure | Recalcul indépendant |
|---|---:|---|---:|---|---|
| Statistiques | 1 | COMPLETE | 100,00 % | MEASURED | Signaux présents/attendus non publiés |
| Incidents | 1 | PARTIAL | 91,62 % | MEASURED | Signaux présents/attendus non publiés |
| Compositions | 1 | PARTIAL | 99,03 % | MEASURED | Signaux présents/attendus non publiés |

La formule est **Σ signaux présents / Σ signaux attendus × 100**, sur les observations dont le dénominateur attendu est strictement positif. Les chiffres sont donc retranscrits, pas recalculés. Leur moyenne simple ne donnerait pas la couverture pondérée globale. COMPLETE décrit le contrat de normalisation ; il ne certifie pas l'exactitude sportive. Sources : R, lignes 74–82 ; M, § 8.

Pour le dossier, on compte les identifiants fournisseur distincts déclarés par J4/J5 dans la fenêtre. À asOf, il faut l'état canonique, le détail et les trois familles J5 directs, compatibles et disponibles ; des preuves antérieures à la fenêtre peuvent satisfaire cette condition. Les familles PARTIAL ou EMPTY_VALID restent admissibles pour l'exploitabilité, mais les trois familles doivent être COMPLETE pour la complétude stricte. Les identifiants et horodatages individuels nécessaires à une vérification ligne à ligne ne sont pas exposés ici. Sources : R, ligne 240 ; M, § 9.

- **Exploitabilité : 1 / 1 = 100,00 %, MEASURED.**
- **Complétude stricte : 0 / 1 = 0,00 %, MEASURED.**
- Le dossier est donc exploitable et partiel ; la mesure de sa complétude peut être MEASURED sans que les données soient COMPLETE.

Le dossier historique de remplacement examiné par l'humain contient une famille UNAVAILABLE, une EMPTY_VALID et une PARTIAL. Il ne s'ajoute pas à la cohorte ciblée. **UNAVAILABLE n'est jamais 0 %**, EMPTY_VALID est une liste vide valide, et une absence ou UNKNOWN conserve son sens propre. Source : R, lignes 295–299.

## 6. Coûts en appels

Population : tentatives directes de la fenêtre finale ; dénominateur des ratios : **un dossier direct exploitable**, disponible à asOf.

| Mesure | Calcul | Résultat | État |
|---|---|---:|---|
| Découverte, compte absolu | 15 pages J3 + 1 découverte tournoi | **16 appels** | MEASURED |
| Appels marginaux J4 phase 2 + J5 | 1 + 3 | **4 appels** | MEASURED |
| Coût marginal par dossier exploitable | (1 + 3) / 1 | **4,00 appels/dossier** | MEASURED |
| Toutes les tentatives directes | 15 + 1 + 1 + 3 | **20 appels** | MEASURED |
| Coût effectif par dossier exploitable | 20 / 1 | **20,00 appels/dossier** | MEASURED |

Sources : R, lignes 226–240 ; M, § 10.

Les seize appels de découverte sont un total absolu ; leur égalité numérique avec « par dossier » serait accidentelle puisque n = 1. Le coût effectif porte sur la fenêtre et peut bénéficier d'un dossier déjà existant : il ne mesure pas le coût historique total de construction depuis zéro. Une division par le nombre de dossiers strictement complets, ici zéro, serait NOT_MEASURED, et non un coût nul. Les plafonds sont des limites internes, pas des quotas fournisseur. Aucun tarif ni temps opérateur mesuré ne permet de convertir ces appels en euros ou d'établir une efficacité relative.

## 7. Fraîcheur, corrections et portée du verdict

Les nombres d'événements J6 analysés, de versions, d'enrichissements, de corrections tardives et leurs délais sont **NOT_MEASURED dans la fenêtre finale** ; ils ne valent pas zéro. La revue historique identifie une LATE_CORRECTION locale sur les trois familles et une classification PASS, tout en laissant la vérité externe NOT_MEASURED. Ces résultats ne sont pas injectés dans la fenêtre ni dans son hash. Sources : R, lignes 215–224, 299 et 310 ; V, lignes 108–111.

Une ambiguïté documentaire mérite d'être conservée : R, ligne 224, décrit le départ du délai comme la « version fournisseur terminale précédente », alors que M, lignes 370–385, exige l'état direct applicable précédant la version et le délai depuis l'état terminal applicable. Il ne faut pas remplacer l'heure de cet état canonique par celle d'une version de famille. Le code et les preuves individuelles n'étant pas autorisés, cette relecture ne tranche pas l'implémentation. Aucun délai numérique du rapport final n'est affecté ici puisqu'il reste NOT_MEASURED. Un reparsing ou un changement synthétique ne devient pas une correction fournisseur.

| Dimension | État automatique final | Verdict humain | Conclusion admissible |
|---|---|---|---|
| Accessibilité | MEASURED | PASS | 20 réponses sur les 20 tentatives instrumentées |
| Complétude | MEASURED | PARTIAL | Un dossier exploitable ; deux familles partielles |
| Exactitude | NOT_MEASURED | NOT_MEASURED | Aucun contrôle externe d'identité, horaire, statut, score ou correction |
| Fraîcheur | PARTIAL | PARTIAL | Latences locales disponibles ; retard source inconnu |
| Stabilité | MEASURED | PASS | Compatibilité des parseurs sur cette fenêtre |
| Efficacité | MEASURED | PARTIAL | Coûts exacts sur un dossier, sans seuil ni comparateur |
| Maintenabilité | NOT_MEASURED | NOT_MEASURED | Temps d'adaptation et de restauration non chronométrés |
| Risque | PARTIAL | PARTIAL | Refus/échecs observés uniquement ; aucune extrapolation |
| Valeur analytique | NOT_MEASURED | NOT_MEASURED | Comparateur externe absent |

Sources : R, lignes 244–263 et 303–327 ; V, lignes 95–111. Le coût opérateur est également NOT_MEASURED.

## 8. Reproductibilité et écarts de générations documentaires

### Ce qui a été vérifié sur les octets fournis

| Objet | Taille vérifiée | SHA-256 vérifié |
|---|---:|---|
| Rapport complet | 20 008 octets | 6d9a46b4391beffe9c1c54591089f0b035a5ed81f9522c03998a0feff18e7e52 |
| Préfixe automatique | 15 202 octets | ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25 |

Un marqueur de début et un marqueur de fin ont été comptés. Ces résultats correspondent à V, lignes 59–75.

Le **hash de population déclaré** est c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726. Il est concordant entre les documents, mais **n'a pas été reproduit** : cela exigerait les identifiants ordonnés réellement sélectionnés et la sérialisation canonique j8-benchmark-v1, avec fenêtre et asOf. Le hash du document ne démontre pas à lui seul l'exactitude de cette sélection. L'export local sans réseau et la comparaison entre exports sont rapportés historiquement ; aucun export ni accès PostgreSQL n'a été relancé ici. Source : M, lignes 95–110 ; V, § 3.

### Points à ne pas lire rétroactivement

1. **Plafond historique 30, protocole prospectif 40.** V décrit 20 tentatives sur un plafond de 30. B, lignes 313–320, décrit désormais 35 + 1 + 1 + 3 = 40, avec conservation des campagnes J3 historiques à 25 et nouvelle borne V53 à 35. Le plafond du 30 août reste 30 ; aucune de ces limites locales n'établit un quota SofaScore.
2. **Démarrage J3 et procédures historiques.** B contient une procédure ancienne de démarrage « sans réseau ». A, lignes 54–62, porte l'exception J3 durable adoptée le 13 septembre. Ces textes appartiennent à des états différents du laboratoire. La présente relecture n'a pas utilisé le démarrage d'application pour consulter J8.
3. **Clôture documentaire et état Git.** V consigne J8 VALIDATED, WO-016 COMPLETED, au commit de référence b92d41e50876072b98a542073c0931c40806428c, ainsi que PUSH_PR_OR_MERGE=NO. Cela atteste le verdict historique déclaré ; cette note ne vérifie pas une fusion Git ni l'état actuel du dépôt.
4. **Tests rapportés et preuve fournisseur.** V consigne 928 tests standards, 4 ignorés attendus, 67 tests PostgreSQL, zéro échec/erreur et aucune collecte standard. La résolution Docker initialement absente est distinguée d'un contrôle Compose ultérieur réussi sur hôte autorisé. Ce sont des preuves historiques de qualification locale ; aucune ne mesure à elle seule les capacités de SofaScore.

## 9. Conclusion utilisable

SofaScore a fourni, dans la seconde campagne locale et sous les parseurs déclarés, les vingt réponses compatibles permettant de disposer d'un dossier terminal direct exploitable. Les trois familles J5 n'étaient pas toutes complètes. La première campagne a rencontré une incompatibilité incidents V14, résolue dans une génération ultérieure sans réécrire l'échec historique.

Restent inconnus : couverture représentative des compétitions et matchs, comportement prematch/live, continuité et simultanéité soutenables, taux d'erreur à long terme, quota fournisseur, cause générale des refus, exactitude sportive, retard source, coût monétaire ou opérateur, effort de maintenance et valeur relative face à un autre fournisseur.

**J8 VALIDATED valide la démarche et ses preuves bornées ; il ne transforme aucune dimension PARTIAL ou NOT_MEASURED en résultat positif.** La clôture n'autorise aucune campagne supplémentaire et n'emporte aucune décision d'adoption J9.
