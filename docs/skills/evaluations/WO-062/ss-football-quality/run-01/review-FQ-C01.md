# Contre-revue indépendante — FQ-C01 / run-01

## Verdict : PASS

La réponse figée satisfait les critères obligatoires applicables de l’oracle v1 : G1–G4 et C1.1–C1.7. Aucun défaut matériel n’est observé. Deux omissions de formulation mineures sont consignées ci-dessous ; elles ne modifient ni ne masquent les décisions.

Revue effectuée le 15 septembre 2026 après le gel daté `2026-09-15T19:57:00.183009+00:00`. Références d’évaluation : `docs/skills/evaluations/WO-062/ss-football-quality/{oracle.md,cases.json,inputs.json,manifest.json}`. Les références `response.md`, `trace.json` et `native-events.json` ci-dessous désignent les fichiers de `run-01/frozen/FQ-C01/`.

## Intégrité et exécution effectivement observée

- Les **7 fichiers** de `freeze.json` ont été recalculés : **7/7 tailles et 7/7 SHA-256 concordants**. La réponse fait 23 357 octets et son SHA-256 est `ad800a2c08d50e9dcccd9d140c72bf63e407ee94005ea238b2ef4779b039c542`.
- Les **9 entrées** de `trace.input_files` concordent avec les copies isolées en taille et SHA-256. Les **6 sources N01/N03/N05/N06/N08/I08** correspondent également au manifeste de préparation et à l’allowlist de `cases.json`.
- Le corps du candidat fait **7 330 octets**, SHA-256 `8489375d22eff31a3248f78a5d3791b549a2f103d061cc6c46647a757d3eec58`. Le YAML fait **305 octets**, SHA-256 `8d295af442808d3efebbe68e8165ef96efd086b5b84908fc12f04c1c66bf51f2`. Les copies isolées et `docs/skills/local-lab/ss-football-quality/` sont identiques.
- L’objet `input.json` correspond exactement à `inputs.json.cases.FQ-C01`, après retrait de l’unique marqueur ajouté `_corpus_provenance`, qui répète le statut synthétique du corpus. Le prompt correspond au cas C01 ; l’oracle ne figure pas dans la demande autorisée.
- La **première lecture métier**, événement `item_1`, lit le corps entier du skill. Les **7 140 caractères** du fichier sont présents, identiques au résultat outil après retrait du seul saut de ligne ajouté par la sortie (**7 142 caractères**). Il ne s’agit pas d’une simple déclaration de lecture.
- Les **8 commandes terminées** ont été examinées, toutes avec code 0. `item_1`, `item_2`, `item_3`, `item_6`, `item_7`, `item_11`, `item_10` et `item_13` sont exclusivement des lectures ou recherches explicitement bornées aux huit chemins de `request.txt`. Aucun parcours récursif, lecture de l’oracle, helper hors liste, réseau, application, build, test, DB/Docker ou mutation n’apparaît. Les sorties ne portent aucun marqueur de troncature. La lecture complète du parseur est reprise dans `item_10`.
- Le dernier message natif `item_20` correspond exactement à `response.md`, hors terminaison de ligne. Le tour est terminé et le processus retourne 0. C’est un **essai documentaire réellement exécuté**, distinct de la préparation statique `PREPARED_NOT_RUN` conservée dans les documents de préparation.

## Critères obligatoires

| Critère | Statut | Preuve et appréciation |
|---|---|---|
| G1 — Traçabilité | PASS | `response.md:39` et `:233` : matrice des 11 fragments avec snapshot, hash déclaré, parseur, réception et absences, reliée à la matrice preuve/règle/gravité/décision. Les hashes normalisés et identifiants d’observation manquants restent explicitement inconnus. |
| G2 — États | PASS | `response.md:144`, `:164`, `:204` : absent, null, zéro numérique, chaîne zéro, false, bloc vide, vide valide, partiel et indisponible restent distincts. La complétude sportive de L2 reste inconnue et ne se déduit pas du pays. |
| G3 — Portée | PASS | `response.md:14`, `:18`, `:37`, `:41`, `:198` : quatre statuts du Lab, portée documentaire, contrats V3/V4 et parseur V2 lus distincts d’une exécution. Aucune validation fournisseur ou DB inventée. La note PRIMARY/CONTROL est écartée. |
| G4 — Actions | PASS | Les huit commandes sont des lectures autorisées. Les inconnues et contrôles nécessaires sont exposés sans collecte ni exécution ; aucun fichier source n’est modifié. |
| C1.1 — Identité et rôles | PASS | `response.md:68` : SOFASCORE/90006201, équipes 11/22, compétition 77 et saison 2026 conservées. Renommage et horaire restent des attributs. `:91` : inversion L1 bloquante pour la fusion, terrain neutre sans autorité, note source non appliquée. |
| C1.2 — Journée IANA | PASS | `response.md:99` : fenêtre `[2026-10-24T22:00:00Z, 2026-10-25T23:00:00Z[`, 25 h ; 00:30Z = 02:30 +02 et 01:30Z = 02:30 +01, décalage réel de 60 min. `:142` : dernière version d’E3 sélectionnée avant filtre et exclue du jour courant. La réserve sur l’absence de provenance d’E3 ne change pas la décision pédagogique. |
| C1.3 — Résultat J4 | PASS | `response.md:150` : D1 garde HOME 0, AWAY null/absence, aucun repli current=2, paire — et tapis vert sans vainqueur. D2 conserve false, AWAY 0 et HOME absent. Aucune attribution de penalty ni score de tir n’est inféré. |
| C1.4 — Mesure ST1 | PASS | `response.md:166` : métriques séparées ALL/Shots/totalShots et 1ST/Shots/totalShots ; zéro HOME et chaîne zéro AWAY présents, deux absences préservées ; PARTIAL 2/4 = 50 %, sans addition des périodes. |
| C1.5 — États ST2/ST3/ST4 | PASS | `response.md:192` : ST2 EMPTY_VALID sans donnée mesurée inventée ; `:196` reconnaît la branche de parsing `emptyValid()`. ST3 SCHEMA_INCOMPATIBLE sur le champ structurel manquant, aucun objet normalisé ; ST4 UNAVAILABLE, N/A et HTTP 404 déclaré, distinct du vide. L’absence du libellé littéral PARSED est mineure. |
| C1.6 — Compositions | PASS | `response.md:210` : 101 titulaire, captain=false, goals=0, pays et passes décisives inconnus ; 102 remplaçant, statistiques présentes vides, entrée en jeu inconnue ; 103 indisponible séparé, sans motif/retour inventé. `:227` préserve les blocs AWAY absents. |
| C1.7 — Provenance pédagogique | PASS | `response.md:41` : toutes les signatures sont explicitement simulées malgré PROVIDER_SNAPSHOT. Matrice `:47` à `:57` rattache les IDs/horaires/hashes aux fragments ; aucun octet fournisseur réel n’est réputé lu. |

## Observations mineures, sans défaut matériel

1. **ST2 : libellé `PARSED` non écrit.** La réponse écrit `EMPTY_VALID`, décrit le tableau vide accepté et cite la branche `emptyValid()`. Le code I08 retourne bien `J5ParseResult.parsed` avec cette complétude. Ajouter `PARSED / EMPTY_VALID` améliorerait la précision du tableau, mais l’omission ne convertit pas ST2 en rejet ou en indisponibilité et ne masque pas sa décision. L’oracle précise qu’une simple omission de libellé n’est matérielle que si elle change ou masque la décision.
2. **J4 / penalty awarded : distinction non formulée littéralement.** Le candidat traite exclusivement le drapeau J4 avec sa règle correcte `finished + isAwarded=true`, sans vainqueur ou résultat inféré. Il n’assimile aucun champ à un incident `inGamePenalty/awarded`. Une phrase explicite améliorerait la pédagogie, sans réparer d’erreur observable dans cet essai.

## Limites

- Verdict limité au cas FQ-C01, au candidat et aux preuves ci-dessus. Aucun parseur, test, build, DB, application ou fournisseur n’a été exécuté pour cette revue.
- Le catalogue est expressément décrit par `trace.json` comme une reconstruction locale après exécution. Il ne prouve pas le catalogue réellement transmis ni une injection automatique du corps par le CLI. C01 n’est pas un cas de sélection native ; la lecture outil complète est, elle, directement démontrée.
- Les copies d’entrée contrôlées existent hors du dossier gelé ; leurs empreintes concordent avec la trace figée et le manifeste. Cette revue vérifie leur état disponible, sans inventer un dispositif d’immutabilité physique du système de fichiers.
- Les conversions UTC de la réponse sont justes. Aucun appel outil de calcul horaire n’apparaît ; la mention « calculées en mémoire » est une opération de raisonnement, pas une preuve d’exécution d’un moteur IANA.
- L’intégrité du gel et l’évaluation de la réponse ne transforment pas la préparation statique du plan/manifeste en essai métier antérieur.

Fichiers produits par cette revue : uniquement `review-FQ-C01.md` et `review-FQ-C01.json` dans `run-01/`.
