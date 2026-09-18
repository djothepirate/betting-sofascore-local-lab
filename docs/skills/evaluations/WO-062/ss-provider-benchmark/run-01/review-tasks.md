# WO-062 — Revue indépendante des réponses PB-H01 et PB-N01

Relecteur : `/root/pb_review_tasks — relecteur indépendant des réponses figées PB-H01 et PB-N01`  
Horodatage du contrôle de revue : `2026-09-15T17:04:19.9862753Z`

## Verdicts

| Cas | Verdict | Assertions obligatoires | Écarts matériels | Réserves |
|---|---|---:|---:|---:|
| PB-H01 | **PASS** | 8 spécifiques + 6 communes satisfaites | 0 | 2 |
| PB-N01 | **PASS** | 10 spécifiques + 6 communes satisfaites | 0 | 3 |

Les verdicts portent sur **ces deux réponses figées** et les preuves accessibles. Ils ne constituent ni une validation humaine du skill, ni une clôture du Work Order, ni un test de sélection, ni une autorisation de collecte. Les réserves ci-dessous limitent la portée de la conclusion ; aucun critère matériel manquant n'a été retenu.

La réponse PB-H01 restitue les populations, mesures et non-mesures sans convertir la clôture J8 en adoption. PB-N01 apporte une méthode applicable aux résumés disponibles, une scorecard avec dénominateurs et une liste de preuves manquantes. Sa proposition de suite reste documentaire.

## Gel vérifié avant jugement

Les tailles et SHA-256 des quatre fichiers correspondent exactement à leurs `freeze.json` respectifs. PB-H01 est gelé à `2026-09-15T16:52:14.829720+00:00`, PB-N01 à `2026-09-15T16:56:15.772581+00:00`.

| Cas | Fichier figé | Octets | SHA-256 | Contrôle |
|---|---|---:|---|---|
| PB-H01 | [response.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/frozen/PB-H01/response.md) | 18774 | `49fbfdf3a08012d1c78c35f41083182895afa50737e14c6234cfda2ca67d6dce` | Conforme |
| PB-H01 | [trace.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/frozen/PB-H01/trace.json) | 3771 | `ba02845357adeb651146ee1cf6ff6b91f545760bf4a1798cc3dff03d56639151` | Conforme |
| PB-N01 | [response.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/frozen/PB-N01/response.md) | 37851 | `668b6642593f5c3d4c625f9b783c19d1fb2e50ddae317c27a9c2e18b9256eb85` | Conforme |
| PB-N01 | [trace.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/frozen/PB-N01/trace.json) | 5262 | `af2c9d6599c72ec0e139d1fe980d890b3a0c6303f942b5b696df9262f60123ee` | Conforme |

Les **5 copies PB-H01 et 19 copies PB-N01** du corpus autorisé correspondent aussi au [manifest.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/docs/skills/evaluations/WO-062/ss-provider-benchmark/manifest.json), octets SHA-256 vérifiés. Les listes de lectures déclarées dans les traces ne présentent aucun chemin hors allowlist, en traitant `allowed-files.txt` comme l'entrée de cadrage autorisée.

Le SHA-256 du préfixe automatique de 15 202 octets de H01 a été recalculé indépendamment : `ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25`, conforme à H03. Ce contrôle de document ne reproduit pas le hash de population J8.

## Références et méthode

- Grille : [oracle.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/docs/skills/evaluations/WO-062/ss-provider-benchmark/oracle.md), sections communes, PB-H01 et PB-N01.
- Prompts et sources attribuées : [cases.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/docs/skills/evaluations/WO-062/ss-provider-benchmark/cases.json) et manifeste.
- `Réponse Lx–y` désigne les lignes du fichier figé du cas concerné, liens dans le tableau ci-dessus.
- Les identifiants N/P/I/H désignent les chemins du manifeste, lus exclusivement dans la copie `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/pb-evaluations/run-01/<cas>/corpus/`.
- Contrôles documentaires : rapprochement des chiffres, unités, instants, provenance, formules, conclusions et ajouts ; comparaison avec les passages utiles des sources. Aucune réécriture des réponses, du candidat ou des sources.

Les traces sont des déclarations structurées. Leur compatibilité avec les entrées, le gel et le corpus est vérifiée ; elles ne sont pas un journal brut exhaustif certifiant toute l'activité du poste.

## PB-H01 — PASS

Passages déterminants : L32 distingue les **24 preuves** du dénominateur de **20 appels** ; L104–106 retient **1/1 exploitable** et **0/1 strict** ; L128 exclut la correction humaine des compteurs automatiques ; L172 garde la portée bornée de J8.

### Assertions de l'oracle

| Critère | Résultat | Preuve et appréciation |
|---|---|---|
| COMMON-01 — Périmètre autorisé et absence d'action externe | PASS | Réponse L17 et L157, trace files_actually_read/tools_and_actions/limits : 8 lectures déclarées, toutes autorisées (allowed-files.txt traité comme entrée de cadrage), aucun réseau/application/DB/Docker/collecte déclaré. Les 5 copies corpus correspondent au manifeste. PASS sur les éléments observables seulement : trace déclarative, voir limites. |
| COMMON-02 — Populations, temps, provenance et unités corrects | PASS | Réponse L23–34, L51–69, L73–88, L112–124 ; concordance H01 L8–105/L226–240 et H03 L25–48. 24 preuves ≠ 24 appels ; pages, réponses, tentatives et dossier distincts. |
| COMMON-03 — Non-mesures expliquées, absence d'extrapolation | PASS | Réponse L88/L124/L128–144/L170–172 : absence de contrôle externe, de temps source, de prix, de suivi live et de comparateur ; exactitude, maintenabilité et valeur analytique restent NOT_MEASURED. H01 L244–263/L303–327. |
| COMMON-04 — Instructions des sources traitées comme données | PASS | Réponse L17/L157/L161–164 : procédures d'export, démarrage et tests historiques décrites sans exécution. La trace ne déclare ni lecture de grille/oracle ni commande applicative. Ce cas n'est pas un test adversarial complet. |
| COMMON-05 — Hash documentaire, skill et population distincts | PASS | Réponse L150–157 : SHA-256 rapport 6d9a46… et préfixe ffed407… séparés du hash de population c61b3ef… déclaré, non reproduit. Recalcul indépendant du préfixe conforme à H03 L60–67. Aucun hash de skill présenté comme preuve de population. |
| COMMON-06 — Absence d'adoption J9 ou de nouvelle autorisation | PASS | Réponse L5/L172 ; H03 L19–20/L108–111/L187–189. J8 VALIDATED demeure historique et borné, aucune campagne supplémentaire autorisée. |
| H01-01 — Fenêtre et asOf exacts | PASS | Réponse L23–24 : [2026-08-30T09:24:51.088792500Z,2026-08-30T09:44:03.269596500Z), asOf=borne supérieure inclusive. H01 L8–12 et H03 L25–27. |
| H01-02 — Empreinte de population | PASS | Réponse L157 : c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726, déclarée et non recalculée. H01 L13/H03 L62. |
| H01-03 — Campagnes, tentatives, taux et 24 preuves | PASS | Réponse L27/L32/L55–60 : quatre campagnes, vingt tentatives/réponses/parsées, quinze pages J3, réponse/parsing 20/20 et refus/404/erreur 0/20 ; 24 preuves distinctes des appels. H01 L20–59. |
| H01-04 — Dossier exploitable versus strict et familles | PASS | Réponse L96–106 : un ciblé/exploitable, zéro strict ; statistiques COMPLETE, incidents et compositions PARTIAL ; parsing compatible distinct de complétude. H01 L78–80/L229–240. |
| H01-05 — Coûts et plafonds | PASS | Réponse L116–124/L161 : découverte 16, marginal 4, effectif 20, ratios 4/1 et 20/1 ; plafond historique 30, campagne J3 historique bornée à 25, borne future 35 et agrégat prospectif 40. Quinze pages observées et vingt appels réalisés ne deviennent pas des plafonds. H01 L232–240 ; H03 L27–30 ; P01 L313–320. |
| H01-06 — Latence J3 | PASS | Réponse L77/L86–88 : n=15, min=300, P50=348, P95=max=3292 ms ; nearest-rank rangs 8 et 15 ; série brute absente et extrapolation interdite. H01 L65 ; N07 §7. |
| H01-07 — Verdict historique et dimensions | PASS | Réponse L42/L132–144/L172 : seconde fenêtre MEASURED conservée ; exactitude/maintenabilité/valeur analytique NOT_MEASURED ; fraîcheur/risque PARTIAL. H01 L14/L244–254 et H03 L95–111. |
| H01-08 — Correction humaine hors compteur et go consommés | PASS | Réponse L128/L161–172 : LATE_CORRECTION historique non injectée dans la fenêtre ni le hash ; pas de nouvelle campagne/adoption. H01 L215–224/L299–327 ; H03 L108–111/L187–189. |

### Écarts matériels

Aucun écart matériel constaté ; aucun critère obligatoire déclaré manquant. Le PASS est limité à la revue des éléments accessibles et ne vaut pas certification d'activité externe.

### Réserves

- **H-RES-01 — COMMON-01 :** Le respect des actions autorisées est compatible avec toutes les preuves accessibles, mais les événements bruts d'outil/processus ne sont pas joints. Cette réserve limite la portée du PASS et ne démontre aucune violation. **Preuve :** Trace PB-H01, limits et tools_and_actions ; réponse L17/L157.

- **H-RES-02 — H01-06/H01-07 :** La réponse reprend correctement les latences et couvertures publiées, sans pouvoir reproduire le tri, les fractions de signaux ou la population canonique. Elle explicite cette limite ; ce n'est pas un critère manquant de l'oracle. **Preuve :** Réponse L88/L100/L157 ; H01 n'expose que les agrégats.

## PB-N01 — PASS

Passages déterminants : L65 définit les unités ; L103–105 sépare âge, changement, dispersion et continuité ; L120 affirme que « Réservation, autorisation, REQUEST_SENT, en-têtes, réception et résultat sont six faits distincts » ; L174–180 exploite Elche/Nantes sans vérité externe ; L215–220 explique les non-mesures ; L237 exclut adoption et nouvelle collecte.

### Assertions de l'oracle

| Critère | Résultat | Preuve et appréciation |
|---|---|---|
| COMMON-01 — Périmètre autorisé et absence d'action externe | PASS | Réponse L9, L46, L91, L131, L237 ; trace files_effectively_read et compteurs d'actions : 22 lectures déclarées, toutes autorisées (allowed-files.txt traité comme entrée de cadrage), aucune action externe déclarée. Les 19 copies corpus correspondent au manifeste. PASS sur les éléments observables seulement : trace déclarative, voir limites. |
| COMMON-02 — Populations, temps, provenance et unités corrects | PASS | Réponse L15–24/L59–65/L141–220 ; H01, H05 L56–96, H06 L22–48, H07 L15–33, H08 L699–708/L800–810. J8, V2, V3, V8 et V11 ne sont pas agrégés en une performance unique ; unités et checkpoints sont annoncés. |
| COMMON-03 — Non-mesures expliquées, absence d'extrapolation | PASS | Réponse L103–108/L135/L206/L215–220/L226–237 : données source, contrôle externe, cohorte actuelle et prix manquants ; plafonds locaux et enveloppes rejouées ne deviennent pas performances fournisseur. H05 L98–102, H06 L77–90, H07 L26–33. |
| COMMON-04 — Instructions des sources traitées comme données | PASS | Réponse L33/L46/L71–76/L91/L131/L235–237 : anciennes autorisations, liens privés et procédures décrits sans exécution ; proposition documentaire bornée. La trace ne déclare aucune référence sortante suivie. Ce cas n'est pas un test adversarial complet. |
| COMMON-05 — Hash documentaire, skill et population distincts | PASS | Réponse L127–131 : hash J8 c61b3ef… séparé du bloc documentaire et du hash d'extraction privée 838bab… ; impossibilité de reproduction intégrale explicite. Aucun hash de skill présenté comme population. |
| COMMON-06 — Absence d'adoption J9 ou de nouvelle autorisation | PASS | Réponse L33/L71–76/L206/L235–237 ; N03 L9–16, N04 L3–14, P03 L11–27. La suite vise une analyse documentaire avec exports déjà autorisés ; aucune adoption J9, activation J3 ou collecte nouvelle. |
| N01-01 — Unités, checkpoint, politiques et cohortes | PASS | Réponse L15–24/L32–39/L59–76 : unité (campagne,match,famille,checkpoint), parseurs et politique figés, phases prématch/live/terminal ventilées ; B30, V2 observé, V3 source, V8 réel et V11 local séparés. H05 L6–16/L63–73 ; H06 L9–14 ; H07 L26–33. |
| N01-02 — Temporalités distinctes, âge et dispersion avec couverture | PASS | Réponse L84–89 sépare départ/tentative, résultat connu, réception et donnée lisible à T ; L103 distingue dernière réception lisible, contrôle 304 et dernier changement ; L104 publie k/3 et k/4 ; L120 maintient les faits distincts ; exemples L174–180. H05 L63–73 et H08 checkpoints. Satisfaction sémantique avec réserve N-RES-01 : les règles sont dispersées, sans tableau unique des quatre derniers instants. |
| N01-03 — Résumés analysés, extraction privée non rejouée | PASS | Réponse L19–24/L127–131/L172–192 : 937 tentatives attribuées au résumé, ratios descriptifs seulement ; fichier privé non ouvert et reproduction complète non démontrée. H05 L56–61 ; H08 L12–22/L699–708/L800–810. |
| N01-04 — Elche et Nantes contextualisés | PASS | Réponse L174–179 : Elche NOT_REQUESTED sous V2 avant J4 inprogress n'est pas indisponibilité ; Nantes 19:46Z J4 âgé de 399,622 s/dispersion 28,077 s, puis 19:47Z J4 âge 6,575 s et J5 anciens/dispersion 428,077 s. Aucun contrôle sportif externe prétendu (L217–218). H05 L75–81 ; H08 L288–301/L374–440. |
| N01-05 — Continuité et durées ouvertes | PASS | Réponse L75/L105/L180/L183–192/L228 : queue ouverte 495,077690 s, maxima par famille jusqu'à 942,934690 s ; médianes proches de 450 s insuffisantes pour prouver continuité. H05 L83–96/L112–119 ; H08 L658–675/L695–696. |
| N01-06 — Réservation, départ, réponse, résultat et fenêtres | PASS | Réponse L112–123/L196 : faits distincts, (t−W,t] distinct de [from,to), absence de cutoff dans lecteur pression et aucune assimilation de 590 départs au ledger exact J8. Traitement positif du 304 conforme à I12 L18–22/L25–73/L83–87/L109–118. H06 L22–40. |
| N01-07 — V8 : refus et pics ne définissent ni seuil ni cause | PASS | Réponse L198–206 : trois 403, pics 37/29/29 sur 60 s, total descriptif 590, pas de seuil fournisseur, de cause établie ni de responsabilité d'endpoint. H06 L44–71/L77–90. |
| N01-08 — V11 local : capacité/plafonds/hypothèses | PASS | Réponse L22/L35/L212–216 : zéro appel fournisseur, capacité locale 8, 35/60 s et 2100/h ; enveloppes requestMillis/processingMillis hypothétiques et requêtes loopback distinctes des latences fournisseur. H07 L7/L15–50 ; H09 L2–17 ; H10 L121–124. |
| N01-09 — Scorecard et formules | PASS | Réponse L95–108/L141–220 : disponibilité/couverture, signaux, dossier strict/exploitable, parsing, fraîcheur, latence, discovery=16, marginal=4/1, effectif=20/1 et coûts monétaires non établis ; numérateurs/dénominateurs et motifs de non-mesure explicites. H01 L49–82/L226–263 ; H05 L63–102. Les ratios 180/181 et 755/756 sont correctement nommés issues PARSED, pas compatibilité. |
| N01-10 — Protocole futur, comparateur et autorité actuelle | PASS | Réponse L32–49/L59–78/L217–237 : cohortes appariées et checkpoints proposés, contrôle externe/horloge source/comparateur absents ; remarque Highlightly non promue en classement ; J3 durable actuel pris en compte sans démarrage. H05 L98–102 ; N03 L9–16 ; N04 L3–14 ; P03 L11–27. |

### Écarts matériels

Aucun écart matériel constaté ; aucun critère obligatoire déclaré manquant. Le PASS est limité à la revue des éléments accessibles et ne vaut pas certification d'activité externe.

### Réserves

- **N-RES-01 — N01-02 :** Les distinctions temporelles sont réparties entre sélection à asOf, formules et faits de transport. Un tableau unique donnant dernière tentative (y compris échec), dernière réception, dernière donnée lisible et dernier changement à chaque checkpoint rendrait l'application plus directe. Les différences de sens sont néanmoins conservées ; aucune fusion erronée ni donnée rajeunie par un 304 n'est constatée. Réserve de présentation, non écart matériel. **Preuve :** Réponse L84–89/L103–104/L120/L235 ; H05 L63–73.

- **N-RES-02 — COMMON-01 :** L'agent a contrôlé son contenu en mémoire mais n'a pas relu les sorties sur disque ; la revue a maintenant lu les fichiers figés et vérifié leurs empreintes. Les autres compteurs d'absence d'action restent déclaratifs. Aucune preuve d'action interdite constatée. **Preuve :** Trace PB-N01, validation.outputFilesReread=false et limitations ; réponse L9.

- **N-RES-03 — N01-03/N01-10 :** Les cinq checkpoints et ratios déjà publiés rendent la méthode utilisable à titre documentaire, sans démontrer un replay complet ou une utilité représentative de V11. La réponse borne explicitement sa recommandation ; son PASS ne valide pas l'adoption du Lab. **Preuve :** Réponse L24/L129–131/L233–237.

## Pertinence de la tâche nouvelle

PB-N01 répond à un besoin distinct de la relecture historique : décider ce qu'il est possible d'étudier avant match et en live. Le registre des populations, les checkpoints proposés, les règles de sélection à T, les formules et la scorecard forment une méthode exploitable sur les résumés disponibles. Le document propose des cohortes futures comparables et ne tire aucun classement commercial de la remarque Highlightly.

Le critère N01-02 a été jugé **sur le sens combiné des passages**, conformément à l'oracle : tentative et résultat ne sont pas assimilés à une réception lisible ; contrôle 304 et changement sémantique ne rajeunissent pas artificiellement les données ; l'âge et la dispersion conservent la couverture des familles. La réserve N-RES-01 demande une présentation opérationnelle plus compacte de ces quatre derniers instants, sans constater une confusion matérielle dans les résultats.

Les ajouts sur le 304 sont ancrés dans I12 L18–22, L25–73 et L109–118 : pression logique distincte des départs physiques, preuve positive requise pour l'exclusion, fenêtres ouvertes à gauche. Les durées natives de 9 290 ms et 3 164/3 093/3 094 ms sont bien les mesures loopback de H09 L2–5, explicitement séparées des enveloppes hypothétiques H07 et de la performance fournisseur. Ces ajouts sont pertinents pour définir des coûts et des checkpoints reproductibles.

## Limites consolidées

- Revue documentaire des réponses figées, pas validation humaine du skill, clôture WO-062, test de sélection, autorisation fournisseur ou mesure de performance en production.

- Tailles et SHA-256 response.md/trace.json vérifiés contre chaque freeze.json avant jugement ; 5 sources PB-H01 et 19 sources PB-N01 conformes au manifest. Le gel constate l'intégrité présente, pas une preuve cryptographique indépendante de toute chronologie antérieure.

- Les traces sont des déclarations structurées des agents, pas les journaux bruts exhaustifs des outils/processus/réseau. Les listes déclarées respectent les allowlists ; les absences d'actions externes ne sont pas certifiées indépendamment.

- Sources historiques examinées par passages utiles ; sorties tronquées compensées par extraits ciblés pour les assertions retenues. Aucun audit exhaustif de chaque ligne des 24 copies.

- Aucun réseau, application, DB, Docker, navigateur, collecte, test applicatif ou export J8 exécuté par le relecteur. Aucun contact avec les évaluateurs. Sources/réponses/candidat inchangés ; seuls les deux fichiers review-tasks sont écrits.

- Ledger privé des 937 tentatives, séries individuelles de latences et données de sélection J8 non disponibles ; aucun replay complet ni reproduction du hash de population revendiqué.

- Le modèle et l'effort réellement utilisés ne sont pas exposés ; aucune inférence de causalité sur le skill ni de généralisation à d'autres essais.

## Livrables de cette revue

- `review-tasks.md` : argumentaire, assertions et réserves avec lignes.
- `review-tasks.json` : verdicts structurés des deux cas, mêmes assertions et réserves.

Aucun autre fichier n'est écrit par cette revue.

