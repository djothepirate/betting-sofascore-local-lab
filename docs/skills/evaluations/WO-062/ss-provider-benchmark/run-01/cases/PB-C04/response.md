# PB-C04 — Dossiers exploitables et coût en appels

**Cas initial : 2 dossiers distincts ciblés, 1 exploitable et 0 strictement complet. La découverte coûte 2 appels ; les coûts marginal et effectif valent respectivement 3 et 5 appels par dossier exploitable. Dans la variante, aucun dossier n’est exploitable : les comptes restent 2, 3 et 5, mais les deux ratios deviennent `NOT_MEASURED`.**

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Périmètre et force de preuve

L’entrée porte la provenance `SYNTHETIC_EVALUATION_INPUT_NOT_PROVIDER_DATA`. Cette note présente des calculs sur le scénario fourni ; elle ne constitue pas une observation réelle du fournisseur.

- Fenêtre UTC : `[2026-09-01T10:00:00Z, 2026-09-01T11:00:00Z)` ; début inclus, fin exclue.
- Vue des composants à `asOf = 2026-09-01T12:00:00Z`.
- Population d’appels : les cinq tentatives G1 à G5, annoncées dans la fenêtre, dans cinq campagnes distinctes conformes aux plafonds. Aucune tentative supplémentaire n’est donnée.
- Population de dossiers : les identifiants distincts des unités J4/J5 ciblées dans la fenêtre, soit `{1001, 1002}`. Les campagnes de découverte sans identifiant d’événement n’ajoutent aucun dossier.

J’applique aux cinq lignes présentées comme tentatives la sémantique des tentatives directes du ledger. Un coût exact réellement publié par J8 exige `FULL_ATTEMPT_LEDGER` et le mode `GUARDED_PROVIDER`. Le JSON simplifié ne fournit ni les lignes persistées, ni leurs identifiants techniques, ni les résultats terminaux nécessaires à leur vérification. Les états `MEASURED` ci-dessous désignent donc la calculabilité dans le scénario déclaré, sous cette interprétation, sans certifier un ledger réel. Avec seulement `RESPONSE_ONLY` ou `LEGACY_BASELINE`, les coûts J8 resteraient `NOT_MEASURED`.

## 1. Qualification des dossiers au cas initial

Les cinq composants courants doivent être directs et disponibles : état canonique, détail, statistiques, incidents et compositions. Les trois familles J5 peuvent être `COMPLETE`, `PARTIAL` ou `EMPTY_VALID` pour l’exploitabilité ; la complétude stricte exige les trois à `COMPLETE`.

| Dossier | État et détail | Statistiques | Incidents | Compositions | Exploitable | Strictement complet |
|---|---|---|---|---|---|---|
| 1001 | Directs et disponibles | `COMPLETE` | `EMPTY_VALID` | `PARTIAL` | Oui | Non |
| 1002 | Directs et disponibles | `COMPLETE` | `COMPLETE` | `UNAVAILABLE` | Non | Non |

**1001 reste admissible avec des preuves initiales reçues à 09:00 UTC**, avant la fenêtre : l’entrée les indique toujours courantes à `asOf`. La fenêtre sélectionne les unités ciblant le dossier ; elle n’impose pas de réacquérir ses cinq composants pendant cette heure. G3 et G4 ciblent le même identifiant et ne créent qu’un dossier. `EMPTY_VALID` et `PARTIAL` suffisent à l’exploitabilité, mais excluent la complétude stricte.

**1002 est exclu à cause des compositions `UNAVAILABLE`**, malgré leur provenance directe. Une indisponibilité ne devient ni une liste vide valide, ni un score de complétude égal à zéro.

## 2. Calculs du cas initial

Toutes les mesures utilisent la fenêtre et `asOf` ci-dessus, l’entrée synthétique et les définitions J8, sections 9 et 10.

| Mesure | Population / unité | Formule et numérateur / dénominateur | Valeur | État dans le scénario |
|---|---|---|---:|---|
| Dossiers ciblés | Événements distincts J4/J5 | `cardinal({1001, 1002})` ; compte absolu | 2 dossiers | `MEASURED` |
| Dossiers exploitables | Dossiers ciblés à `asOf` | `1 / 2` | 1 ; 50,00 % | `MEASURED` |
| Dossiers strictement complets | Même population | `0 / 2` | 0 ; 0,00 % | `MEASURED` |
| `discoveryOverhead` | Tentatives de découverte | G1 J3 + G2 tournoi = `1 + 1` ; sans dénominateur | 2 appels | `MEASURED` |
| Appels marginaux | Tentatives J4 phase 2 et J5 | G3 + G4 + G5 + aucun J5 = `3 + 0` | 3 appels | `MEASURED` |
| `marginalCallsPerExploitableDossier` | Appels marginaux / dossiers exploitables | `(3 + 0) / 1` | 3,00 appels/dossier | `MEASURED` |
| Tous les appels | Toutes les tentatives directes de la fenêtre | G1 + G2 + G3 + G4 + G5 | 5 appels | `MEASURED` |
| `effectiveCallsPerExploitableDossier` | Tous les appels / dossiers exploitables | `5 / 1` | 5,00 appels/dossier | `MEASURED` |

**La déduplication de G4 ne retire pas son appel.** G4 est présentée comme une tentative réalisée. Dédupliquer le résultat lors de la persistance ne transforme pas cette tentative en cache hit : G4 compte dans les 3 appels marginaux et les 5 appels effectifs, tout en ciblant toujours le seul dossier 1001.

La découverte est un compte absolu, jamais divisé. Le coût effectif inclut la découverte et les tentatives visant 1002, même si ce dossier n’est pas exploitable. Le coût marginal exclut la découverte. Les appels qui ont constitué les composants antérieurs à la fenêtre ne sont pas fournis : le coût historique total de constitution du dossier ne peut pas être calculé. Aucun tarif monétaire n’est fourni.

## 3. Variante : dernières statistiques directes incompatibles

La dernière composante directe `EVENT_STATISTICS` de 1001 est désormais `INCOMPATIBLE`, reçue avant `asOf`. Elle remplace l’état courant des statistiques utilisé pour qualifier le dossier. **On ne peut pas conserver le précédent `COMPLETE` pour déclarer 1001 exploitable.**

1001 devient inexploitable ; 1002 le reste à cause de ses compositions indisponibles. La variante ne déclare aucune tentative supplémentaire ni son appartenance à la fenêtre : les cinq tentatives données restent la population de coût, sans ajouter arbitrairement un sixième appel.

| Mesure | Formule et numérateur / dénominateur | Valeur | État dans le scénario |
|---|---|---:|---|
| Dossiers ciblés | `cardinal({1001, 1002})` | 2 | `MEASURED` |
| Dossiers exploitables | `0 / 2` | 0 ; 0,00 % | `MEASURED` |
| Dossiers strictement complets | `0 / 2` | 0 ; 0,00 % | `MEASURED` |
| Découverte, compte absolu | `1 + 1` ; sans dénominateur | 2 appels | `MEASURED` |
| Appels marginaux, compte absolu | `3 + 0` ; sans dénominateur | 3 appels | `MEASURED` |
| Coût marginal par dossier exploitable | `3 / 0` | Valeur absente | `NOT_MEASURED` |
| Tous les appels, compte absolu | `5` ; sans dénominateur | 5 appels | `MEASURED` |
| Coût effectif par dossier exploitable | `5 / 0` | Valeur absente | `NOT_MEASURED` |

Le taux de dossiers exploitables est mesurable à **0 %**, car son dénominateur est **2 dossiers ciblés**. Les coûts par dossier exploitable ont un dénominateur **nul** : leur valeur n’est ni zéro, ni l’infini. Les comptes d’appels restent visibles.

## 4. Code, états et limites

`J8BenchmarkService.dossierEfficiency` confirme le regroupement par `providerEventId` des cohortes déclarées dans la fenêtre, le compte absolu de découverte et les numérateurs marginal/effectif. `hasAllCurrentComponents` exige les cinq composants à `AVAILABLE` puis les statuts J5 admissibles. `isStrictlyComplete` exige les trois statuts `COMPLETE`. `divide` renvoie une valeur absente pour un dénominateur nul et arrondit les divisions définies à deux décimales.

Le bloc `DossierEfficiency` porte dans le code un état agrégé `PARTIAL` dès que le nombre de dossiers exploitables vaut zéro : cela s’applique à la variante, sans effacer les comptes mesurables. Pour le cas initial, ce bloc serait `MEASURED` si toutes les cohortes concernées ont un résultat terminal à `asOf`, sinon `PARTIAL`. Les résultats terminaux ne sont pas fournis : l’état exact du bloc ne peut pas être attesté. Ces calculs ne suffisent pas non plus à qualifier tout le rapport J8 comme `MEASURED`.

Limites :

- Le lecteur de persistance n’est pas autorisé dans ce corpus. La sélection réelle des derniers composants directs n’a pas été exécutée ; elle repose ici sur `currentAtAsOf` et sur la variante fournie.
- Les identifiants de snapshots, occurrences, observations, versions de parseur et preuves complètes de provenance manquent. Le hash de population J8 n’est pas reproductible à partir de ce seul JSON.
- Aucun compte de signaux présents/attendus n’est fourni : pas de score numérique de complétude à inventer. Latences, horaires des réponses et heures source fiables manquent également.
- Les plafonds conformes sont une donnée du scénario, pas une mesure de capacité ou de quota fournisseur. Aucun coût monétaire, niveau d’exactitude sportive ou choix d’intégration ne peut être déduit.

Sources utilisées : `input.json` ; `candidate/SKILL.md` ; `corpus/docs/architecture/J8-BENCHMARK-METRICS.md`, notamment sections 2, 5, 8, 9 et 10 ; `corpus/src/main/java/com/bettingproject/sofascorelocal/application/benchmark/J8BenchmarkService.java`, notamment `dossierEfficiency`, `hasAllCurrentComponents`, `isStrictlyComplete` et `divide` ; `corpus/src/main/java/com/bettingproject/sofascorelocal/application/benchmark/J8DirectObservationCohorts.java`, notamment `ObservationCohort`, `DirectComponentState` et `CurrentDirectEvidence`. Aucun renvoi sortant suivi.