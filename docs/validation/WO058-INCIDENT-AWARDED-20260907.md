# WO-058 — penalty accordé de CE Sabadell — Córdoba

Date : 7 septembre 2026. Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`,
`NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Demande et diagnostic exact

Après identification de l'endpoint, le propriétaire demande d'analyser le JSON conservé et de
corriger le type ou attribut responsable du rejet, avec la compétence `ss-verify`.

| Élément | Preuve locale |
|---|---|
| Événement | CE Sabadell — Córdoba, ID fournisseur 16418278 |
| Campagne | `6e6246f0-f438-4dd1-b2c4-06a5176746d5` |
| Endpoint | `GET /api/v1/event/16418278/incidents` |
| HTTP / contenu | 200 / `application/json` |
| Réception | `2026-09-07T20:26:03.170Z`, soit 22:26:03.170 Europe/Paris |
| Snapshot / occurrence | 2340 / 2307 |
| Parseur historique | `event-incidents-v15` |
| Octets | 70 289 |
| SHA-256 du corps exact | `8824e98a6288da4db273b6c14421dd8390184826455c60df8f1eaa65817c1499` |

Les octets sont lus depuis `provider_snapshot.payload_raw`, en lecture seule et pour ce seul ID,
puis conservés dans le fichier local ignoré `.tmp/wo058-sabadell-incidents-snapshot-2340.json`.
Le SHA calculé sur le fichier est égal au SHA enregistré. Le corps complet n'est ni affiché,
ni journalisé, ni ajouté au dépôt. Aucun appel SofaScore n'est effectué.

Le replay Java avec le **parseur V15 existant**, sans Spring ni persistance, renvoie :

```text
status=SCHEMA_INCOMPATIBLE bytes=70289 problems=1 warnings=4
VALUE_OUT_OF_RANGE | $.incidents[5].incidentClass
Value is outside the documented football incident vocabulary
```

Le tableau contient 27 incidents. L'élément à l'indice 5 porte `incidentType=inGamePenalty`,
`incidentClass=awarded`, `time=83`, `isHome=true`, `confirmed=true`. Il n'a pas de joueur,
raison, description de tir ou score. Le vocabulaire historique `inGamePenalty` n'accepte
que `missed` : c'est la cause précise du rejet, et non un JSON illisible ou un nouveau type.
Les quatre avertissements ne constituent pas cet échec atomique.

La lecture séparée des avertissements, limitée aux codes et chemins, confirme leur origine :

| Code | Chemins | Traitement |
|---|---|---|
| `PROVIDER_SENTINEL_NORMALIZED` | `$.incidents[0].addedTime`, `$.incidents[20].addedTime` | Sentinelles de marqueur de période conservées dans le brut et omises du temps ajouté normalisé. |
| `UNKNOWN_FIELD` | `$.incidents[5].goalkeeperPenaltyHistory`, `$.incidents[5].penaltyHistory` | Métadonnées auxiliaires non utilisées par le contrat J5 courant, conservées dans le brut. |

Ces quatre avertissements sont identiques avant et après correction. Les historiques de
penalty ne déclenchent donc pas le rejet. La sortie limitée reste locale et ignorée dans
`.tmp/wo058-incident-warnings.txt`.

## Correction versionnée

`EventIncidentsV16Parser` hérite des règles V15 et ajoute la seule classe `awarded` pour
`inGamePenalty`. Les méthodes de spécialisation de la base V6 conservent leurs valeurs
historiques ; les parseurs V6 à V15 gardent donc leur comportement, notamment le rejet de
cette nouvelle classe sous V15.

La classe reste `awarded` dans les données normalisées. Elle ne signifie ni tir raté ni but.
Le parseur ne fabrique pas de tireur, score, motif ou signal de fin. Pour la complétude d'une
attribution, joueur tireur et détails du résultat ne sont pas applicables ; minute, classe et
côté restent contrôlés selon leurs règles. Un joueur présent demeure validé. Un côté absent
est signalé comme manquant. `missed` conserve son décompte historique des données manquantes.

Une classe inconnue, `awarded` sur un autre type, une minute invalide, un joueur mal formé ou
un couple de scores incomplet reste incompatible. Les raisons/descriptions de tir raté sont
contradictoires avec `awarded` et restent rejetées. Les structures auxiliaires et le champ
`confirmed` de cette attribution restent dans la preuve brute ; aucune décision VAR n'est inférée.

Le premier passage Maven complet a aussi révélé un défaut sur un contre-exemple synthétique :
avec un seul score présent, le parseur ajoutait déjà `ATOMIC_FIELD_MISMATCH`, mais construisait
ensuite un objet métier qui levait une exception avant de retourner l'incompatibilité. V16
empêche cette construction pour un couple incomplet et retourne l'erreur structurée attendue.
Ce contrôle de construction est activé seulement en V16 ; les versions précédentes conservent
leur comportement. Les deux directions et les variantes explicites `null` sont couvertes ;
deux scores absents ne sont pas transformés en zéros.

Les branchements live, J5 direct et import local utilisent V16, ainsi que leur version de
provenance. V36 ajoute V16 à la contrainte SQL, sans supprimer les anciennes versions. Les
gardes de sauvegarde/rétention J6 sont alignés sur V36. Aucun résultat, snapshot, occurrence
ou manifeste historique n'est réécrit ; V36 n'est pas appliquée à la base opérateur ici.

Le contrat est documenté dans les [règles J5, section 9.0](../requirements/J5-FOOTBALL-INCIDENT-RULES.md).

## Qualification

Le replay Java des **mêmes 70 289 octets** donne :

| Parseur | Résultat | Incidents normalisés | Complétude | Erreurs / avertissements |
|---|---|---:|---|---|
| V15 historique | SCHEMA_INCOMPATIBLE | Aucun, rejet atomique | Non établie | 1 / 4 |
| V16 corrigé | PARSED | 27 | COMPLETE, 107/107 signaux, 100 % | 0 / 4 |

L'incident à l'indice 5 conserve `inGamePenalty/awarded`, minute 83, domicile vrai et scores
absents. Les quatre avertissements restent visibles : la correction ne les efface pas.
La preuve réelle est rejouée uniquement par `.tmp/WO058IncidentDiagnostic.java`, programme
local ignoré, sans Spring, réseau ni écriture en base. La compilation `javac -implicit:none`
et l'exécution `java` sous Java 25.0.4 ont toutes deux un code zéro et aucune erreur de compilation.
Une première compilation avait affiché une erreur de fermeture d'un JAR du cache malgré un
code zéro ; elle n'a pas été retenue comme preuve de compilation propre. Le passage propre
réutilise les dépendances Maven avec les permissions nécessaires.

Sortie minimisée ignorée : `.tmp/wo058-incident-v16-exact-replay.txt`, SHA-256
`d20db5399450bea8dd0360c52d2d800391cc6863b2d6c38e02e03ce3208df02c`.
Les tests standards ne lisent pas le fichier opérateur : ils utilisent un corpus synthétique
minimal avec les mêmes attributs. **42 cas V16 réussissent**, avec la variante accordée, ses
contre-exemples, les scores absents/null/zéro et les règles historiques. Les branchements live,
J5 direct/import, le stockage puis replay V16 et l'upgrade V35 prérempli vers V36 sont vérifiés.
La réexécution du diagnostic après le correctif de score, avec les classes compilées par Maven,
conserve exactement la sortie et le hash ci-dessus.

Commande finale : `.\mvnw.cmd clean verify -Pintegration-tests`, **BUILD SUCCESS**, code 0,
fin le 7 septembre à **23:27:44 Europe/Paris**, durée 5 min 36 s. Rapports XML lus :
**1 530 tests Surefire, aucun échec ni erreur, 5 ignorés ; 136 Failsafe, aucun échec, erreur ou
ignoré**. Les cinq ignorés concernent quatre tests de liens symboliques non disponibles sous
Windows et une qualification Docker J6 opt-in non activée. Aucune méthode n'est exclue par
la commande ; le test J6 nécessitant 8087 libre a réussi.

Les deux passages intermédiaires non verts restent documentés dans le
[bilan de qualification](WO058-PREMATCH-TEMPORAL-20260907.md) : score unilatéral devenu erreur
structurée, puis fixture d'upgrade déplacé dans une base isolée pour éviter une collision d'une
fonction historique de V31 dans `public`. Ils ne sont pas présentés comme des succès.

Cette correction est faite dans `.tmp/wo058-live-j4-j5`, sur la base `e98f7a74e39a1c57e601efb3d346ae55829fce73`.
L'opérateur a ensuite libéré le port 8087 pour la qualification complète. Son application du
checkout `human` n'est ni redémarrée ni remplacée par cette correction. La qualification
fournisseur humaine et une réinterprétation persistée de 2340
ne sont pas effectuées dans ce travail.
