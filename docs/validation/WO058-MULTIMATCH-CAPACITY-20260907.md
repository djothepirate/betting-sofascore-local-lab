# WO-058 — Premier parcours fournisseur et paliers de capacité

Date : 7 septembre 2026. Base applicative : `4edc8bba2a74c21aabeae2179619c4c96c0c881e`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Retour propriétaire et lecture locale

Le propriétaire rapporte les sept captures et valide la préparation d'une observation ancienne
`notstarted`, le lancement explicite, J4 puis les trois familles J5, la disponibilité du bouton
d'arrêt pendant la collecte et sa désactivation à la fin, la consultation J5 et le score J4.

Une lecture GET locale de `/live-campaigns/a556dbef-4c56-48a3-8ed5-08735f024e5f/state` confirme :

| Champ | Valeur |
|---|---|
| Campagne | `a556dbef-4c56-48a3-8ed5-08735f024e5f`, `COMPLETED` |
| Rencontre | US Boulogne Côte-d'Opale — Dijon, `providerEventId=16386264` |
| Identité locale | `5067fb31-7503-398c-95b3-75751624d3f4` |
| Lancement | `2026-09-07T12:57:20.221858Z` |
| Résultat | `FINISHED_CONFIRMED`, `finished`, score 1–1, `finalComplete=true` |
| Appels / volume | 4 réservations, 133 667 octets reçus, zéro cycle manqué |
| J4 | snapshot 1270, occurrence 1237, reçu à `12:57:25.846Z`, `PARSED` |
| Statistiques | snapshot 1271, occurrence 1238, reçu à `12:57:28.979Z`, `PARSED` |
| Incidents | snapshot 1272, occurrence 1239, reçu à `12:57:32.105Z`, `PARSED` |
| Compositions | snapshot 1273, occurrence 1240, reçu à `12:57:35.249Z`, `PARSED` |

Les GET de la fiche statistiques et de la liste du 4 septembre retournent HTTP 200. Les lectures
ne déclenchent aucun transport fournisseur. Les contrôles ne modifient ni cette campagne, ni les
données, ni le lanceur Eclipse. Aucun payload brut n'est ajouté au rapport.

Cet essai porte sur la finalisation après un premier J4 déjà `finished`. Il ne prouve pas encore
l'attente d'un coup d'envoi, les cycles pendant un match actif ni le débit soutenu multi-match
du fournisseur. Les rapports précédents restent historiques et inchangés.

## Refus dès deux rencontres et périmètre de reprise

La configuration initiale prévoit `qualified-match-capacity=1`, `request-envelope=10s` et
`processing-envelope=1s`. `LiveAdmissionPolicy` compte quatre familles par match/minute, avec
le délai global de trois secondes : 56 s pour un match, 112 s pour deux, 168 s pour trois.
Le refus n'est donc pas provoqué par les cases à cocher ni par la présence de matchs terminés.
`prepareSelection` exclut d'abord les observations `finished`, puis contrôle les autres.

Le passage aux paliers déjà prévus par ADR-SS-005 requiert un profil qualifié complet. Quatre
variables Eclipse sont désormais reliées explicitement au YAML : capacité, enveloppe de requête,
enveloppe de traitement et empreinte de preuve. Les défauts du pilote et les opt-ins sont conservés.
Les deux refus de capacité exposent leurs codes distincts et expliquent le profil à configurer.
Un simple relèvement du nombre de matchs sans profil admissible reste refusé.

## Qualification exécutée

Les tests ciblés de préparation, liaison de configuration, rendu/confirmation multiple et
ordonnanceur passent : 56 tests, zéro échec, erreur ou ignoré ; commande
`mvnw.cmd -Dtest=LiveMultiMatchCapacityTest,LivePreparationAdmissionTest,LiveCampaignPropertiesTest,LiveCampaignControllerTest -DskipITs test`,
fin à `2026-09-07T13:26:57Z`.

Après que le propriétaire a libéré le port 8087, son absence de listener est vérifiée avant
`mvnw.cmd clean verify`. La commande se termine à `2026-09-07T13:32:11Z` en 4 min 46 s,
`BUILD SUCCESS`, code 0. Les XML effectifs sont lus : 179 suites Surefire, 1 354 tests,
zéro échec, zéro erreur, cinq ignorés ; six suites Failsafe, 122 tests, zéro échec, erreur ou ignoré.
Les cinq ignorés restent quatre cas de liens symboliques dépendant de Windows et un scénario
optionnel J6 Docker. Les migrations et la persistance sont inchangées ; les intégrations étant
déjà exécutées, le profil `-Pintegration-tests verify` n'est pas répété pour ce correctif.

Le journal `.tmp/wo058-multimatch-clean-verify.log` porte SHA-256
`fbd974f1a7e7d7fe0388612e9c03a05f23ca81595bf048bedbd0c99a9e0658d1`.
Les anciens échecs J6 restent dans les rapports précédents ; ils ne sont pas réattribués à cette
exécution.

La première exécution de `scripts/Invoke-LivePlaywrightLoopbackQualification.ps1` se termine
à `2026-09-07T13:36:48Z` en échec : huit tests, deux échecs, zéro erreur ou ignoré. Les deux
nouveaux scénarios de charge reçoivent `SCHEMA_INCOMPATIBLE` pour leur fixture de compositions,
qui omettait le champ obligatoire `confirmed`. La fixture reçoit `confirmed=true` et tous les
corps synthétiques sont désormais validés avant ouverture du navigateur de mesure. Aucun parseur
ni critère de performance n'est modifié. Le journal historique
`.tmp/wo058-multimatch-loopback.log` porte SHA-256
`d46ac627bec3a58ec64efb6c16aca8d40baab7392f0f7924478902ef58e999f6`.
La nouvelle exécution conserve les cadences réelles et les critères initiaux. Elle se termine
à `2026-09-07T13:47:53Z` en 8 min 29 s avec huit tests, zéro échec, erreur ou ignoré.
Le script confirme `WO058_LIVE_LOOPBACK=PASS` et `SOFASCORE_NETWORK_CALLS_EXECUTED=NO`.
Les trois XML effectifs donnent cinq tests de session, un de consultation navigateur et deux
de formulaires. Le journal `.tmp/wo058-multimatch-loopback-corrected.log` porte SHA-256
`c2093768f5258ebcff92856580ad8faf5fbd3b8a37a065c4446021908552ed18`.

| Mesure loopback, trois cycles par palier | Deux matchs | Trois matchs |
|---|---|---|
| Réponses reçues | 24 | 36 |
| Taille de chaque réponse | 5 242 880 octets | 5 242 880 octets |
| Départ → réception validée, maximum | 787,0164 ms | 700,7198 ms |
| Normalisation, maximum | 10,6869 ms | 9,9611 ms |
| Cycle des quatre familles, maximum | 26,5126593 s | 40,7663652 s |
| Mémoire du worker, première / dernière mesure | 113 807 360 / 268 955 648 octets | 123 932 672 / 271 826 944 octets |

Chaque scénario conserve un seul worker, le délai global de trois secondes et l'espacement
de soixante secondes pour chaque famille de chaque événement. Les contrôles de nettoyage,
d'absence d'artefacts interdits et d'absence de requêtes hors périmètre passent. Les tests
déterministes couvrent également 230 minutes de jeu simulé à deux puis trois matchs, la
surveillance de fin et les trois appels de finalisation : aucune échéance manquée, budgets
respectés, finalisation avant quatre heures. Les contre-épreuves conservent le refus d'un profil
à dix secondes par requête pour plusieurs matchs, même avec une empreinte renseignée.

## Profils retenus et limites de la preuve

La [preuve figée de capacité](WO058-MULTIMATCH-CAPACITY-PROFILE-20260907.json) relie les profils,
les métriques, les empreintes des XML et celles des fichiers source effectivement mesurés.
Sa valeur SHA-256 est :

```text
0f1ae6ad44190ae965bec3ad670ffb3c45a140a29856d217f28e71b5db262679
```

Les changements n'étaient pas committés pendant les mesures ; aucune révision Git fictive ne
leur est attribuée. La base `4edc8bb` et les empreintes du worktree assurent le rattachement.
La règle `.gitattributes` limitée à ce JSON conserve ses octets lors d'un checkout Windows.

| Profil | Enveloppe requête | Enveloppe traitement | Délai global | Charge modélisée par minute |
|---|---|---|---|---|
| Deux matchs | 3 000 ms | 1 000 ms | 3 000 ms | 2 × 4 × 7 s = 56 s |
| Trois matchs | 750 ms | 1 000 ms | 3 000 ms | 3 × 4 × 4,75 s = 57 s |

Les quatre variables à renseigner ensemble dans Eclipse et la reprise opérateur figurent dans
le [runbook](../runbooks/LIVE-J4-J5-CAMPAIGNS.md). Le défaut reste une rencontre. Un nouveau
manifeste est nécessaire après changement de profil. Les sélections mixtes excluent les matchs
locaux `finished` avant le calcul : deux `notstarted` et un `finished` retiennent deux cibles.
Le plafond de cette expérimentation demeure trois rencontres éligibles.

L'enveloppe de requête est une hypothèse de charge qualifiée localement ; le timeout du transport
reste borné à dix secondes. Les mesures ne garantissent pas la latence du fournisseur. Les JSON
synthétiques minimaux, complétés d'espaces jusqu'à 5 Mio, qualifient le volume brut et leur
normalisation ; ils ne couvrent pas toute la complexité sémantique possible. La seconde de
traitement est l'allocation conservatrice existante : le test de transport ne chronomètre pas
le parcours PostgreSQL complet. Les intégrations fonctionnelles de persistance sont vérifiées
séparément dans la suite complète. Les deux cycles successifs manqués, les budgets et le stockage
conservent leurs contrôles à l'exécution réelle.

## Remise pour essais et inventaire

Le WO est `READY_FOR_REVIEW`, sans clôture. Le prochain essai opérateur peut passer au palier
deux, puis au palier trois, avec les paramètres et le manifeste correspondants. La prochaine
qualification fonctionnelle doit encore observer l'attente du coup d'envoi et les cycles d'un
match réellement `inprogress`. Cette reprise ne lance aucune collecte fournisseur et ne modifie
ni la base de l'opérateur ni son lanceur. Aucun commit n'est publié sur un distant.

Les contrôles avant commit couvrent l'UTF-8, les liens locaux, les espaces, le diff de secrets,
les défauts réseau et la conservation de l'ADR accepté. Aucun changement de migration, de parseur,
d'ordonnanceur ou de transaction n'est inclus. Le commit local demandé sera effectué après ces
contrôles, comme dernière action avant remise en main.

La revue finale confirme les 16 fichiers UTF-8, les 238 liens locaux résolus, l'absence d'espaces
en fin de ligne et un `git diff --check` conforme. Les empreintes de la preuve, de ses sources,
des rapports XML et des journaux concordent ; celles de l'ADR accepté et de sa copie figée sont
inchangées. Le diff ne révèle aucun secret. L'adresse `127.0.0.1`, les opt-ins fournisseur/live
désactivés par défaut et les autres décisions ADR restent conservés.

Fichiers du lot :

- [`.gitattributes`](../../.gitattributes) : préservation des octets de la preuve JSON.
- [`README.md`](../../README.md) et [`CHANGELOG.md`](../../CHANGELOG.md) : orientation et évolution du lot.
- [Architecture live](../architecture/LIVE-J4-J5-CAMPAIGNS.md) et [runbook live](../runbooks/LIVE-J4-J5-CAMPAIGNS.md) : profils et configuration Eclipse.
- [WO-058](../work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md) : retour propriétaire et qualification des paliers.
- [Présent rapport](WO058-MULTIMATCH-CAPACITY-20260907.md) et [preuve JSON](WO058-MULTIMATCH-CAPACITY-PROFILE-20260907.json) : traçabilité et limites des mesures.
- [Script de qualification](../../scripts/Invoke-LivePlaywrightLoopbackQualification.ps1) : nombre de tests effectifs attendu.
- [Contrôleur live](../../src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java) : refus de capacité explicites et messages contrôlés.
- [`application.yml`](../../src/main/resources/application.yml) : liaison des quatre variables, défauts inchangés.
- [Qualification de session Chromium](../../src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveProviderSessionQualificationIT.java) : charges maximales à deux et trois matchs.
- [Tests Web](../../src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignControllerTest.java) : sélection multiple et confirmation du même manifeste.
- [Tests de préparation](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LivePreparationAdmissionTest.java) : admission et exclusion des matchs terminés.
- [Tests de configuration](../../src/test/java/com/bettingproject/sofascorelocal/config/LiveCampaignPropertiesTest.java) : paramètres Eclipse et preuve obligatoire.
- [Tests de capacité](../../src/test/java/com/bettingproject/sofascorelocal/application/live/LiveMultiMatchCapacityTest.java) : longue durée simulée et contre-épreuves de surcharge.
