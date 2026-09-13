# WO-058 — Récupération d’une garde live acquise avant lancement

**Date :** 13 septembre 2026

**Work Order :** [WO-SS-20260907-058-bounded-live-j4-j5.md](../work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md)

**Périmètre :** complément P1 de revue de la PR #35

**Statuts préservés :** `EXPERIMENTAL`, `LOCAL_ONLY`,
`NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Constat

Une interruption entre l’acquisition durable du `CampaignLease` par
`acquireLiveCampaign` et le commit de `store.launch` laisse une campagne
`PREPARED` sans ownership, génération d’exécution, lancement, tentative, appel ou
planification, tandis que le garde durable reste `OWNED`. La récupération historique
appelait `interruptOrphan`, qui exige une ownership d’exécution et refusait donc cette
forme pré-lancement.

## Contrat de récupération

Au redémarrage, le Lab reconnaît exclusivement la préparation non exécutée, y compris
sa forme strictement annulée `STOPPED_OPERATOR / PREPARATION_CANCELLED`. Il conserve
la préparation et passe seulement le garde à `CLEANUP_REQUIRED`, sans libération
automatique.

La page de campagne présente une action locale distincte. Son POST exige un jeton
local à usage unique, une confirmation opérateur et la génération affichée. Sous
exclusion locale, le service refuse une session ou un superviseur actif puis
`LiveOrphanProcessProbe` doit prouver l’absence du propriétaire et des processus
Playwright associés.

`completePreLaunchOrphanCleanup` verrouille le garde, la campagne puis les cibles. Il
compare l’état, la campagne, l’instance, le PID, l’instant de démarrage, la génération
et `changed_at` du garde. Il vérifie aussi l’absence de lancement, tentative, appel,
compteur, échéance et planification de famille. Si la preuve est complète, il ajoute
la transition append-only `LOCAL_PRELAUNCH_CLEANUP_VERIFIED`, dont la raison contient
l’empreinte SHA-256 de l’identité vérifiée, puis remet seulement ce garde à `FREE`.
La préparation et ses cibles restent inchangées.

## Concurrence et refus fermé

Une acquisition, un lancement, une modification de garde ou une annulation qui ne
conserve pas exactement la forme `STOPPED_OPERATOR / PREPARATION_CANCELLED` refuse
la clôture. La transaction prend le verrou du garde avant celui de la campagne :
un lancement concurrent gagne ou échoue de façon observable, sans que la clôture
pré-lancement puisse libérer une exécution devenue active.

Une répétition après perte de réponse est admise seulement si le garde est déjà
`FREE` à la même génération, sans propriétaire, et si la transition
`LOCAL_PRELAUNCH_CLEANUP_VERIFIED` porte l’empreinte attendue. Une génération plus
récente reste refusée.

## Effets volontairement exclus

Cette voie ne démarre aucun navigateur et n’émet aucun endpoint ou appel fournisseur.
Elle ne crée ni réponse, snapshot, cookie, session, retry, reprise, réarmement de
suspension 403/429 ni nouvelle campagne.

## Couverture de régression

- `LiveCampaignServiceTest` couvre le passage au nettoyage requis sans
  `interruptOrphan`, sans transport et sans libération automatique.
- `LiveCampaignRecoveryTest` couvre les formes `PREPARED` et
  `STOPPED_OPERATOR / PREPARATION_CANCELLED`, les preuves de processus, les gardes
  périmés, l’exclusion locale et les états modifiés.
- `LiveCampaignControllerTest` couvre le panneau, le jeton, la confirmation, la
  génération, les refus et le retour à la préparation conservée.
- `LiveCampaignPersistenceIT` couvre l’atomicité, le rollback de la libération,
  l’identité complète du garde et la course PostgreSQL avec un lancement concurrent.

## Validation

| Commande | Résultat |
| --- | --- |
| `.\mvnw.cmd -q "-Dtest=LiveCampaignServiceTest,LiveCampaignRecoveryTest,LiveCampaignControllerTest" test` | Succès : 262 tests, 0 échec, 0 erreur. |
| `.\mvnw.cmd -q -Pintegration-tests test-compile "-Dit.test=LiveCampaignPersistenceIT#verifiedPreLaunchOrphanCleanupAtomicallyFreesTheExactGuardWhileKeepingPreparationLaunchable+preLaunchOrphanCleanupRejectsAnyDivergentGuardAndAnAlreadyLaunchedCampaign+preLaunchCleanupWaitsForAConcurrentLaunchAndThenRefusesTheCommittedExecution" failsafe:integration-test failsafe:verify` | Succès : 3 tests PostgreSQL ciblés, 0 échec, 0 erreur, 0 ignoré. |
| `.\mvnw.cmd -q -Pintegration-tests "-Dit.test=LiveCampaignPersistenceIT" failsafe:integration-test failsafe:verify` | Succès : 69 tests PostgreSQL, 0 échec, 0 erreur, 0 ignoré. |
| `.\mvnw.cmd clean verify` | Succès : 2 338 tests unitaires, 0 échec, 0 erreur, 5 ignorés ; 245 tests d’intégration, 0 échec, 0 erreur, 0 ignoré. |
| `.\mvnw.cmd -Pintegration-tests verify` | Succès : 2 338 tests unitaires, 0 échec, 0 erreur, 5 ignorés ; 245 tests d’intégration, 0 échec, 0 erreur, 0 ignoré. |
| `git diff --check` | Succès : aucun espace terminal ni marqueur de conflit. |

Les tests utilisent des doubles, fixtures, loopback et PostgreSQL éphémère local via
Testcontainers. Ils ne lancent aucune campagne fournisseur et n’effectuent aucun appel
réel vers SofaScore.
