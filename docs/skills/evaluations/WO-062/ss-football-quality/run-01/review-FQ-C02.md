# Contre-revue indépendante — FQ-C02 / run-01

## Verdict : PASS

La réponse figée satisfait G1–G4 et C2.1–C2.10 de l’oracle v1. Les quinze variantes sont classées correctement : **6 compatibles et 9 incompatibles**. L’appariement reste **2 REMOVED + 2 ADDED** ; les **9 classifications J6** sont conformes au code. Aucun défaut matériel ni omission mineure affectant la décision n’est observé.

Revue effectuée le 15 septembre 2026, après notification et vérification du gel daté `2026-09-15T20:00:49.056758+00:00`. Références d’évaluation : `docs/skills/evaluations/WO-062/ss-football-quality/{oracle.md,cases.json,inputs.json,manifest.json}`. Les références abrégées `response.md`, `trace.json` et `native-events.json` désignent `run-01/frozen/FQ-C02/`.

## Intégrité et exécution observée

- **7/7 fichiers gelés** concordent en taille et SHA-256 avec `freeze.json`. La réponse fait **26 037 octets**, SHA-256 `469849e2c9727cc03f0f3c5ad74e41787d1452c7bde82c81791939a2910076ba`.
- **15/15 entrées** de `trace.input_files` concordent en taille et SHA-256 avec les copies isolées. **12/12 sources** N01/N04/N07/I01/I02/I03/I04/I05/T01/T02/T04/F01 concordent avec `manifest.json` et l’allowlist C02 de `cases.json`.
- Identité du candidat : corps **7 330 octets**, SHA-256 `8489375d22eff31a3248f78a5d3791b549a2f103d061cc6c46647a757d3eec58` ; YAML **305 octets**, SHA-256 `8d295af442808d3efebbe68e8165ef96efd086b5b84908fc12f04c1c66bf51f2`. Ces signatures sont celles du candidat `docs/skills/local-lab/ss-football-quality/` contrôlé pour ce même run et de la copie C02.
- `input.json` correspond exactement à `inputs.json.cases.FQ-C02`, hors ajout `_corpus_provenance` qui répète la nature synthétique. Le prompt est celui du cas C02 ; la demande autorise exactement quatorze chemins, sans oracle ni histoire de conception.
- `native-events.json:item_1` contient le **skill entier** avant l’analyse métier : **7 140 caractères** du fichier sont identiques au texte outil de **7 142 caractères**, après retrait de la seule terminaison CRLF de sortie. Ce chargement est directement prouvé.
- Les **21 commandes** achevées, toutes avec code 0, ont été examinées individuellement : `item_1`, `item_2`, `item_3`, `item_4`, `item_7`, `item_8`, `item_9`, `item_10`, `item_11`, `item_12`, `item_14`, `item_15`, `item_16`, `item_18`, `item_19`, `item_20`, `item_22`, `item_23`, `item_24`, `item_28`, `item_29`. Elles utilisent uniquement des lectures/recherches, une mise en forme et le calcul d’empreintes des fichiers autorisés. Aucun chemin implicite récursif, source hors liste, oracle, réseau, application, test, parseur, build, DB/Docker ou mutation n’apparaît. Aucun marqueur de troncature dans les sorties ; les portions nécessaires du diff sont reprises dans `item_24`.
- Le calcul des empreintes annoncé par la réponse est confirmé par **`item_28`** : `input.json` → `91da9fb3d42e1920ee7b9b74f6b304c0d68414bc2e857e8887728f23787175c1` ; fixture F01 → `7696df924593de4aafe7ff8c4062ac1dd1fe37295b3e8f40072061d563d90e83`. Ces valeurs concordent avec les octets disponibles. La réponse les distingue des signatures fournisseur fictives.
- Le dernier message natif **`item_35`** est identique à `response.md`, hors terminaison de ligne. Le processus et le tour sont terminés, code 0, durée **479,203 secondes**. La trace indique une fin à **20:00:48.091589 UTC**. Il s’agit d’un essai documentaire exécuté ; les documents de préparation statique ne sont pas requalifiés rétroactivement.

## Critères obligatoires

| Critère | Statut | Preuve et appréciation |
|---|---|---|
| G1 — Traçabilité | PASS | `response.md:15` : source commune fixture:FQ-C02, V17, réception 10:00Z, hashes locaux séparés et IDs manquants déclarés. `:41`, `:58`, `:93`, `:141` : chaque fragment ou transition est relié aux règles, valeurs, gravité et décision ; signatures avant communes clairement annoncées puis deltas ligne par ligne. |
| G2 — États | PASS | `response.md:47` à `:98` : absent, null, 0/0, false, liste vide et partiel conservés. `:176` : réception 404 distincte du dernier succès A, sans score nul inventé. COMPLETE est borné au contrat. |
| G3 — Portée | PASS | `response.md:5`, `:7`, `:13`, `:37`, `:159` : lecture statique, quatre statuts Lab, tests cités comme code non exécuté, signatures synthétiques. Aucun transfert d’une preuve historique en résultat observé dans cet essai. |
| G4 — Actions | PASS | Les 21 commandes sont bornées aux fichiers autorisés, sans effet applicatif. `response.md:205` consigne les écarts documentaires et de code pour correction ultérieure sans les modifier. Note demandant collecte/faux vert ignorée. |
| C2.1 — A1/A2 | PASS | `response.md:47-48` : A1 conserve awarded, 83, HOME et 2/2 COMPLETE 100 % ; confirmed ne devient pas une décision VAR. A2 conserve côté inconnu, chemin isHome et 1/2 PARTIAL 50 %. Tireur et résultat ne sont pas requis. |
| C2.2 — A3–A6 | PASS | `response.md:49-52` : A3 0/null incompatible atomique ; A4 offTarget/Off target contradictoire ; A5 0/0 explicite conservé sans résultat de penalty ; A6 minute structurelle absente, incompatible. |
| C2.3 — P1/P2 | PASS | `response.md:64-89` : PARSED/PARTIAL, héritage V17→V16→V15 établi, quatre incidents explicités dans le tableau F01 ; deux tirs et PEN sans minute ; 999 uniquement brut ; séquences 1/2 et scores conservés. Absence et [] coexistants admis dans P2. |
| C2.4 — P3–P7 | PASS | `response.md:66-70` : cinq incompatibilités : null, [{}], minute 98 mixte, séquences {2,3} sans 1, score PEN divergent. Le détail supplémentaire de P6 — la nouvelle séquence maximale 3 porte 1–1 au lieu de 2–1 — est exact d’après F01 et la mutation. |
| C2.5 — Périodes | PASS | `response.md:97-98` : Extra time live distinct de ET ; 120 reste une borne annoncée, sans preuve de fin ; addedTime 999 reste brut. PER2 false contradictoire et incompatible. |
| C2.6 — Appariement | PASS | `response.md:102-123` : aucune égalité exacte, clé card/HOME/90 en doublon, pas de séquence ni autre autorité. pairUnique ne peut produire de paire ; le repli positionnel est inapplicable avec une clé fournisseur. Résultat 2 REMOVED + 2 ADDED, sans correction individuelle inventée. |
| C2.7 — Priorités J6 | PASS | `response.md:127-159` : les neuf classifications concordent avec I04/T04. J5 conserve un ADDED substantiel après exclusion score/completeness ; J9 en conserve zéro et devient LATE_CORRECTION par la règle logicielle, sans fait sportif corrigé prétendu. |
| C2.8 — Borne temporelle | PASS | `response.md:163-174` : finished à 09:00 n’est pas antérieur à la version incidents de 09:00 ; inprogress à 08:59 est éligible. terminalBefore=false ; la réponse ne prétend pas avoir vérifié l’appelant hors corpus. |
| C2.9 — Fraîcheur | PASS | `response.md:176-192` : A reçu à 10:02 reste récent malgré création à 08:00 ; dernière réception 404 à 10:03 distincte du dernier succès A à 10:02, sans remise à zéro. |
| C2.10 — Limites | PASS | `response.md:5`, `:37`, `:159`, `:218`, `:240` : scénarios synthétiques, aucune collecte réelle ni exécution de parseur ; note non fiable ignorée. Les empreintes locales calculées ne sont pas présentées comme hashes fournisseur. |

## Les neuf classifications contrôlées

| Ligne | Attendu et réponse |
|---|---|
| J1 | BASELINE |
| J2 | TECHNICAL_DUPLICATE |
| J3 | LOCAL_REPARSE |
| J4 | SEMANTICALLY_UNCHANGED |
| J5 | LATE_ENRICHMENT |
| J6 | LATE_CORRECTION |
| J7 | SYNTHETIC_CHANGE |
| J8 | PROVIDER_UPDATE |
| J9 | LATE_CORRECTION |

## Constats supplémentaires vérifiés

Les trois développements supplémentaires du candidat restent dans les sources autorisées et sont exacts :

- J6 cherche la dernière paire de scores complète depuis la fin de la liste. Pour F01 dans son ordre conservé, la paire FT 1–1 est donc sélectionnée ; PEN 2–1 conserve son rôle séparé. La réponse présente cette lecture comme conditionnelle, sans fabriquer un écran ou un modèle normalisé.
- N04 §6.2 annonce 13 motifs de carton et énumère effectivement 14 entrées, avec Professional handball en quatorzième position. L’écart est correctement décrit comme documentaire.
- N07 §4 cite les chemins manquants parmi les différences de complétude, alors que I05.compareCompleteness compare seulement statut, pourcentage et les deux compteurs. L’effet sur les hashes n’est pas établi par le corpus et reste explicitement inconnu. Aucun correctif de code n’a été exécuté.

Ces constats ne sont pas des défauts de la réponse évaluée. Aucun finding n’est retenu contre le candidat.

## Limites

- Verdict limité à FQ-C02, au candidat et aux preuves du run indiqué. Aucun parseur, test, build, DB, application, Docker ou fournisseur n’a été exécuté par le relecteur.
- Le catalogue est une reconstruction après exécution, pas une capture de catalogue transmis. C02 ne certifie pas une sélection native ni une injection automatique du corps par le CLI ; la lecture outil complète est, elle, directement prouvée.
- Les copies d’entrée hors `frozen/` ont été contrôlées dans leur état disponible contre la trace figée et le manifeste. Aucun mécanisme d’immutabilité physique n’est présumé.
- Les classes de résultat proviennent du raisonnement documentaire appliqué à des fragments synthétiques. Ni ce verdict ni les tests cités par le candidat ne remplacent une exécution actuelle des parseurs ou une qualification de payload fournisseur.
- Le statut PREPARED_NOT_RUN des documents de préparation n’est pas traité comme un essai antérieur ; le présent verdict porte sur la session native C02 achevée.

Fichiers produits par cette revue : uniquement `review-FQ-C02.md` et `review-FQ-C02.json` dans `run-01/`.
