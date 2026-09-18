# WO-062 — Revue de cohérence de la livraison Java `.2`

**Verdict de cette revue : PASS. Aucune incohérence nécessitant correction relevée.**

Ce PASS concerne la cohérence de la publication et de sa synthèse. La qualification
comportementale du candidat reste **INCOMPLETE_QUALIFICATION : 7 PASS / 1 FAIL**, avec
**JM-N01 en FAIL**. Aucun verdict de cas n'a été changé.

- Relecteur : `/root/jm_selection_review`.
- HEAD examiné : `3f758491c5a4bdf3c07daacc7fedec7a8458d2ec`.
- État suivi propre au début et à la fin de l'audit.
- Rapport examiné : `docs/validation/WO062-JAVA-WINDOWS-CANDIDATES-20260915.md`.
- SHA-256 exact du rapport : `5c4a9c7e7c606b631e1a5f2c0fbd6eda8fe19d74672b9ca92db37468a21d1960`.
- Détail reproductible : [résultats JSON](c-delivery-review.json), avec empreintes
  des pièces lues, 86 contrôles mécaniques réussis et cinq appréciations de contenu.

## Résultats courants et historique

| Portée | Candidat | Exécutions | PASS | FAIL | BLOCKED |
| --- | --- | ---: | ---: | ---: | ---: |
| Qualification courante, huit cas run-02 | Java `.2` | 8 | 7 | 1 | 0 |
| Historique conservé, huit cas run-01 | Java `.1` | 8 | 5 | 3 | 0 |
| Total historique des deux versions | Deux versions distinctes | 16 | 12 | 4 | 0 |

`qualification.json`, `run-02/results.json` et `run-02/current-results.json`
concordent sur les huit identités, les verdicts et les origines `run-02`. Aucun PASS
historique `.1` n'est repris pour qualifier `.2`. Les champs d'exécutions courantes
et de total historique sont distincts et correctement renseignés.

La révision métier `6648dd4`, le commit candidat `.2` `2d62f12`, le commit du
publisher `1bf14dd` et le HEAD documentaire examiné sont conservés distinctement.
Le candidat courant correspond à l'empreinte
`255655544771ae6cf2a71b967aba8efeb7e53e64acc41ca31dc784e16a719118` ; l'historique
conserve `.1` à `d20c8b838a9c4b9713e27c576e22272427d3f64fef50a670f5a09364cf849eb4`.

Les deux manifestes d'artefacts, leurs inventaires complets, chaque gel run-02,
les réponses, les revues et les liens de qualification ont été contrôlés par taille
et SHA-256. Les copies d'entrée des huit cas run-02 sont conformes à leur préflight.
Les empreintes de traces relient bien chaque réponse et journal natif publiés.

## Les trois omissions JM-N01 sont restituées exactement

| Critère | Omission maintenue | Distinction correctement préservée dans le rapport |
| --- | --- | --- |
| N02 | Nature UTC des instants d'exécution | La réponse sépare déjà date métier et champs temporels ; cette séparation ne restitue pas UTC. |
| N08 | Terminaison sans reprise ni recréation après perte du navigateur ou contexte live | La réponse décrit le retour nominal au contexte live initial ; ce chemin nominal n'énonce pas la règle de panne. |
| N13 | Interdiction `com.microsoft.playwright` dans `src/main` | La réponse identifie le garde textuel et ses limites ; « certains usages Playwright » omet sa cible et son périmètre exacts. |

L'oracle inchangé, la revue indépendante et les passages pertinents de la réponse
ont été relus directement. Les précisions étaient accessibles dans les sources
autorisées : ADR-SS-007, classes de données et `Verify-Local.ps1`. Le rapport
conserve le FAIL sans attribuer de décision inverse, de faux résultat positif,
d'incident du collecteur ou d'action hors périmètre à cette réponse.

## Constats J3 : formulations étayées et bornées

**Import conflictuel.** Les sources figées montrent `imports.put(id,pages)` avant
le contrôle durable, puis le retrait de la clé en cas d'exception
(`J3RuntimeService` 144–161). `JdbcJ3AutomationStore` 102–110 rejette bien une même
identité avec une autre empreinte. L'exécution attend les octets à la même clé
(`J3RuntimeService` 214–218). La formulation « peut retirer les octets de l'import
original » est donc une déduction statique conditionnelle fondée lorsque l'ordre
original attend encore. Aucune reproduction ni correction n'est revendiquée.

**Terminal J8 après panne.** Le repli de publication fournit `audit=null`
(`J3CollectionExecutor` 132–144) ; les chemins `committedProof` et `interrupt` ne
consultent ou ne terminent pas J8 (`J3CollectionCompletionService` 17–32). Le test
`J3CollectionExecutionIT` 145–157 attend un ordre FAILED, l'ancien catalogue et zéro
terminal J8. Le rapport dit que le sort ultérieur du terminal reste à qualifier.
Il ne transforme pas cette limite en absence certaine de toute réconciliation.

**Sélection de la qualification native.** Le motif du POM
`**/*LocalQualificationIT.java` ne correspond pas à
`J3LivePauseWorkerQualificationIT`. La réponse distingue la sélection historique
explicite par `-Dit.test` et les objectifs Failsafe nommés. Le rapport synthétise
correctement le besoin de sélection explicite sans déclarer ce test absent ou
annoncer une nouvelle exécution native réussie.

Ces trois constats restent des pistes pour des travaux applicatifs distincts.
Aucune correction `.3` ni exécution supplémentaire n'est suggérée dans cette revue.

## Sélection, instrumentation et validation humaine

- S01 et S02 sélectionnent Java `.2` ; S03 sélectionne `ss-postgres-change` ; S04
  ne sélectionne aucun skill pour le dépôt indépendant.
- Les quatre premiers résultats de lecture native correspondent exactement au
  corps concerné, avec seulement le CRLF ajouté par la console. S04 lit le candidat
  afin d'en vérifier l'exclusion ; cette lecture n'est pas une application du skill.
- Le rapport limite correctement la preuve à la sélection et à la lecture par
  outil. Le catalogue reconstruit après exécution n'est ni une capture de la requête
  transmise, ni une preuve d'injection automatique du corps par le CLI.
- Le modèle et l'effort effectifs restent inconnus ; aucun gain général de temps
  ou qualité n'est mesuré. La coexistence simultanée des dix skills reste à qualifier
  en D.
- `human_validation=false` et `personal_installation=false` concordent avec le
  rapport. Les autorisations de création et d'évaluation ne sont pas présentées
  comme une validation humaine du contenu.

## Limites et partie Windows

La partie Windows de cette version exacte du rapport reste explicitement provisoire :
run-01 conserve 6 PASS / 2 BLOCKED et les deux nouvelles sondes sont en cours.
Cette revue ne leur attribue aucun résultat. La finalisation Windows modifiera le
rapport et nécessitera de rattacher la version suivante à sa nouvelle empreinte.

La revue a effectué uniquement des lectures, comparaisons JSON et recalculs
d'empreintes, puis écrit ses propres pièces sous `.tmp`. Aucun modèle, build,
test applicatif, base, navigateur, publication, candidat, fichier suivi ou
installation personnelle n'a été lancé ou modifié. Les huit revues de cas ne
sont pas remplacées par cette revue de cohérence. Les diagnostics Windows et les
comptages historiques de tests applicatifs ne sont pas requalifiés ici.
