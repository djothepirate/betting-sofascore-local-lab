# WO-058 — Réponses lentes, timeouts isolés et familles différées

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

**État : réalisé et qualifié fonctionnellement hors fournisseur ; vérification complète finale réussie.** Ce document décrit le correctif
autorisé le 9 septembre, après `7593e36`, dans le worktree `.tmp/wo058-live-j4-j5`.
L'[ADR-SS-005 v0.9](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) porte la décision ;
le [WO058](../work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md) reste `IN_PROGRESS`.
Aucun résultat antérieur n'est présenté comme qualification de ces nouveaux chemins.

La commande finale explicite `-Pintegration-tests clean verify` termine le
`2026-09-09T17:17:59Z` en **8 min 12 s**, avec `BUILD SUCCESS` : **2 068 cas standards,
zéro échec, zéro erreur, cinq skips ; 213 cas PostgreSQL, zéro échec, zéro erreur,
zéro skip**. Les neuf cas Chromium transport/UI, les deux contrôles natifs de réception
et les régressions d'arrêt individuel sont également réussis dans leurs passes dédiées.
Le smoke nominal dure trois minutes ; aucune qualification de capacité sur 35 minutes
n'est renouvelée dans ce lot. Le WO reste `IN_PROGRESS` pour revue humaine, sans
livraison Eclipse, migration opérateur ni appel fournisseur.

## Retours opérateur et limites du diagnostic

La campagne `651d473e-7379-4da9-aea5-0b47b906c79c`, lancée avec sept rencontres, s'arrête
après 41 tentatives. Le relevé local du parent conserve `STOPPED_ERROR`, première cause
`TRANSPORT / PLAYWRIGHT_TIMEOUT`, famille `EVENT_DETAILS`, événement fournisseur
`16950643`. Le départ observé est `2026-09-09T15:31:27.874Z`, l'erreur
`2026-09-09T15:31:57.904970Z`, avec `requestTimeoutMillis=30000` et dernière phase
`REQUEST_SENT`. Les en-têtes et le statut HTTP ne sont pas connus. Le nettoyage n'est
plus en attente et aucune suspension fournisseur locale n'est active au relevé.

Ces métadonnées prouvent le timeout effectif et l'absence d'en-têtes rapportés avant
expiration. Elles ne prouvent ni un 403, ni un bannissement, ni la cause de la lenteur.
Le propriétaire signale aussi un chargement d'environ vingt secondes dans son navigateur,
ce qui reste une observation distincte. Ce diagnostic historique ne possède pas la
nouvelle preuve de réutilisabilité du contexte : **il n'est pas requalifié rétroactivement**.

Un autre arrêt, campagne `02ad2380…`, survient vers `2026-09-09T15:45:11Z` sur
`EVENT_LINEUPS`, événement `16921300`, avec `INVALID_REQUEST`, après un 404 initial des
statistiques et environ 75 tentatives. La lecture du code établit une incompatibilité
déterministe : v6 peut différer `STATISTICS`, alors que l'autorité transport utilisée
exige encore des indices consécutifs entre les familles en jeu. Le groupe légitime
`J4 → INCIDENTS → LINEUPS` est donc refusé localement avant transport. La régression
`LiveV6BackoffGroupContractTest` reproduit ce chemin à cinq minutes avec le véritable
ordonnanceur public et le tracker, sans réseau. La lecture du code suffit à établir
le défaut ; la régression déterministe est réussie dans la première vérification
complète consignée ci-dessous.

## Réalisation et invariants

Le périmètre reste la session **live-v6 déjà lancée explicitement**. Le timeout configuré
reste inchangé, au plus trente secondes ; les limites de sélection, de charge, d'octets
et de durée restent celles du manifeste et du profil qualifié. Aucune politique
historique n'est migrée et aucun processus de collecte n'est démarré par cette rédaction.

La tolérance exige une preuve nouvelle, corrélée à la requête, de fin CDP `FINISHED` ou
`ABORTED`, fermeture de page, nettoyage des cookies et maintien du contexte existant
réutilisable. L'attente de clôture réelle dispose au plus de deux secondes et la transmission terminale
IPC d'une seconde supplémentaire. Le superviseur exige une trame authentifiée ; une
fin ou un nettoyage incertain reste fatal. Aucun navigateur ou contexte neuf ne remplace
le contexte défaillant. Un corps incomplet est abandonné, sans snapshot ni résultat
normalisé ; la tentative et la charge de protection restent conservées.

La borne de clôture ne garantit pas l'obtention d'un terminal CDP. Avant les en-têtes,
le worker demande immédiatement l'annulation et exige le terminal `ABORTED` corrélé.
Après les en-têtes, il attend naturellement `FINISHED` dans la même grâce maximale
de deux secondes, **sans `Page.stopLoading`** : la sonde locale montre que cette commande
peut supprimer le terminal d'un corps déjà engagé après `COMMIT`. Le nettoyage doit
ensuite être prouvé. Un corps restant bloqué sans terminal dans la grâce conserve
l'arrêt fatal et la fermeture. Même si le corps finit naturellement dans cette grâce,
il est abandonné après le timeout : aucune lecture métier ni persistance du corps
n'est autorisée. La grâce n'étend ni le timeout de collecte ni ses budgets et ne
convertit jamais une absence de preuve en annulation supposée.

Le correctif fixe l'instant de réception d'un succès immédiatement après `body()`,
avant le nettoyage local obligatoire : ce coût local ne décale pas la provenance de
la réponse complète. Un timeout ne met jamais à jour `receivedAt` et ne crée aucun
snapshot. Ce positionnement est inclus dans la reconstruction et le contrôle natif
réussis à 17:04:07Z et 17:04:20Z ; les passes antérieures ne sont pas invoquées comme
preuve de ce déplacement effectué après leur lancement.

Après cette preuve, le groupe est fermé définitivement. Le match entier attend au moins
300 secondes après la fin/nettoyage confirmés, puis reprend par un **nouveau J4** ; les
autres matchs poursuivent leur programme admissible. Il n'y a ni continuation du groupe
interrompu ni rattrapage en rafale. La barrière transport de trois secondes après cette
fin exceptionnelle et la protection persistante de deux secondes, 25/60 s et 1 000/h
restent applicables. Le report respecte la fenêtre, les quotas et l'arrêt opérateur.
La preuve est persistée avant la décision, puis cette décision est sérialisée avec
l'arrêt opérateur. Un arrêt concurrent produit `PLAYWRIGHT_TIMEOUT_ABANDONED` de portée
événement, sans reprise armée ni écrasement de son motif. L'échéance admissible vaut
au moins `max(instant courant monotone, fin observée) + 300 s`, après nettoyage confirmé.

Les limites de `LiveTimeoutRecoveryPolicy` sont cumulatives :

- trois tolérances au maximum dans la session, sans remise à zéro après succès ;
- un `PARSED` intermédiaire nécessaire entre deux timeouts tolérés ;
- un `PARSED` du même couple rencontre/famille nécessaire avant sa récidive ;
- aucun 404 ou autre résultat non `PARSED` ne tient lieu de succès ;
- un timeout admissible en finalisation arrête le match avec fin de collecte incomplète,
  sans retry final ; une pause dépassant la fenêtre arrête également le match.

L'état global `COMPLETED` indique l'épuisement des tâches actives et peut coexister avec
un match `STOPPED_ERROR`, comme dans les autres arrêts individuels. Il ne signifie pas
que tous les derniers cycles J5 ont réussi ; conserver l'état et la complétude finale
de chaque match dans l'interprétation du résultat.

Les erreurs hors de cette exception restent globales. Les statuts 403/429 connus
conservent leur priorité et leur suspension persistante, même avec corps incomplet.
Le réarmement reste manuel, sans requête, sans remise à zéro et sans reprise d'une
campagne terminée. Les compteurs de tolérance appartiennent à la session ; un redémarrage
ne restaure pas une campagne ni son contexte.

V45 ajoute `exchange_ended_at`, `exchange_end_reason` et `context_reusable` aux diagnostics.
Les champs anciens, corps et observations ne sont pas réécrits ; les preuves historiques
restent absentes (`null`/`false`). Les contraintes et contrôles de mise à jour préservent
la chronologie et les faits déjà acquis. Les tests PostgreSQL vérifient cette
conservation et les chemins de sauvegarde/restauration reconnaissent le schéma V45.

L'autorité `LIVE_V6` et l'ouverture `openLiveGroupedV6` séparent enfin les familles
différées des groupes historiques. Seule v6 admet un sous-ensemble strictement croissant
de J5 en jeu après J4 ; campagne, événement, groupe, phase, réponse utilisable, unicité
et interdiction de réouverture restent contrôlés. Les groupes manuels et finaux conservent
leur ordre strict. Cette correction de contrat ne réduit aucun délai ni budget global.

## Vérifications intermédiaires conservées et qualification finale

Les heures ci-dessous sont en UTC. Les logs sont des preuves locales ignorées, conservées
dans `.tmp/resilience-verification` ; leurs liens servent à la revue dans ce worktree.
Les passes intermédiaires gardent leur portée ; la dernière ligne porte l'exécution
complète sur l'état final après les correctifs.

| Exécution | Fin UTC le 9 septembre 2026 | Résultat établi | Portée |
|---|---|---|---|
| [Première sélection ciblée](../../.tmp/resilience-verification/slow-timeout-targeted.log) | 16:30:15 | 195 cas, 194 réussis, 0 échec d'assertion, 1 erreur de fixture, 0 skip ; `BUILD FAILURE` | La fixture supposait à tort un `runtimeStatus` présent. L'erreur initiale est conservée ; la fixture a ensuite été corrigée. |
| [Première `clean verify`](../../.tmp/resilience-verification/slow-timeout-clean-verify.log) | 16:41:18 | Surefire : 2 051 cas, 0 échec, 0 erreur, 5 skips ; Failsafe PostgreSQL : 213 cas, 0 échec, 0 erreur, 0 skip ; `BUILD SUCCESS` | Cette passe précède les dernières corrections, notamment la sérialisation avec l'arrêt opérateur. Elle n'est pas la vérification finale. |
| [Package du worker et sélection ciblée](../../.tmp/resilience-verification/slow-timeout-worker-package.log) | 16:42:30 | 219 cas, 0 échec, 0 erreur, 0 skip ; `BUILD SUCCESS` | Compilation et tests sur la dernière version du code disponible pour cette première qualification native ; les correctifs ultérieurs devaient encore être revérifiés à ce stade. |
| [Première qualification native](../../.tmp/resilience-verification/slow-timeout-native.log) | 16:48:57 | 27 cas, 1 échec, 0 erreur, 0 skip ; `BUILD FAILURE` | Le smoke réussissait ; 25 des 26 cas de transport réussissaient. L'attente du cas de corps bloqué restait à corriger et à rejouer à ce stade. |
| [Deuxième package ciblé](../../.tmp/resilience-verification/slow-timeout-final-package.log) | 16:57:06 | 219 cas, 0 échec, 0 erreur, 0 skip ; `BUILD SUCCESS` | Compilation et tests réussis sur l'état précédant les deux défauts observés à la passe suivante ; le mot `final` du nom de log n'en fait pas la preuve de l'état final. |
| [Deuxième sélection native et UI](../../.tmp/resilience-verification/slow-timeout-final-targeted-native.log) | 16:58:09 | 5 cas, 2 échecs, 0 erreur, 0 skip ; `BUILD FAILURE` | Un échec de terminal naturel après `COMMIT` et un échec d'affichage du bloc de preuve ; correctifs et réexécution restaient nécessaires à ce stade. |
| [Package qualifié après corrections](../../.tmp/resilience-verification/slow-timeout-qualified-package.log) | 17:01:15 | 220 cas, 0 échec, 0 erreur, 0 skip ; `BUILD SUCCESS` | Tests ciblés et reconstruction du worker après correction des deux défauts précédents ; avant le déplacement final de l'horodatage de réception. |
| [Qualification ciblée transport et UI après corrections](../../.tmp/resilience-verification/slow-timeout-qualified-native.log) | 17:02:51 | 9 cas, 0 échec, 0 erreur, 0 skip ; `BUILD SUCCESS` | Sept cas de transport et deux cas UI réussis ; vérification de l'horodatage final et dernière `clean verify` restaient à exécuter à ce stade. |
| [Reconstruction et tests worker après déplacement de la réception](../../.tmp/resilience-verification/slow-timeout-receipt-package.log) | 17:04:07 | 39 cas, 0 échec, 0 erreur, 0 skip ; `BUILD SUCCESS` | Worker reconstruit avec l'instant de réception pris immédiatement après `body()`. |
| [Contrôle natif de réception](../../.tmp/resilience-verification/slow-timeout-receipt-native.log) | 17:04:20 | 2 cas, 0 échec, 0 erreur, 0 skip ; `BUILD SUCCESS` | Qualification des réponses lentes avec le positionnement corrigé de l'horodatage. |
| [Vérification complète interrompue](../../.tmp/resilience-verification/slow-timeout-final-clean-verify.log) | Interruption volontaire, sans bilan global | Aucun résultat global exploitable | Arrêt pendant J6 après détection en revue du blocage de l'ordonnanceur lors d'un arrêt individuel pendant timeout ; cette passe ne vaut ni succès ni qualification finale. |
| [Régressions de l'arrêt individuel pendant timeout](../../.tmp/resilience-verification/slow-timeout-operator-stop-regression.log) | 17:09:45 | 100 cas, 0 échec, 0 erreur, 0 skip ; `BUILD SUCCESS` | 90 cas service, dont deux régressions avec deux rencontres, et 10 cas de politique. |
| [Dernière vérification complète avec intégrations](../../.tmp/resilience-verification/slow-timeout-complete-verify.log) | 17:17:59 | Surefire : 2 068 cas, 0 échec, 0 erreur, 5 skips ; Failsafe PostgreSQL : 213 cas, 0 échec, 0 erreur, 0 skip ; `BUILD SUCCESS`, 08:12 min | `-Pintegration-tests clean verify` sur le code figé après le correctif d'arrêt individuel et le déplacement de l'horodatage. |

Les empreintes SHA-256 des logs réussis ci-dessous sont relevées après leur terminaison :

| Log local | SHA-256 |
|---|---|
| `slow-timeout-qualified-package.log` | `05afdf8b1e88427e5263d5410ccf656709a12cd6be61d0a95c37e44d33d15683` |
| `slow-timeout-qualified-native.log` | `1f38626c91028f51c554a47638068f30ea5286417dce2a093ed7446b39ed8381` |
| `slow-timeout-receipt-package.log` | `53adb1f289d23dce84b6a0ced4cb38abdfa816ddfc2abed8f53c55ed90322e8e` |
| `slow-timeout-receipt-native.log` | `1c4ae8181a1edba7a9a75c714bc6374f53e7cab4d4aee11c01e47d9dad3217b9` |
| `slow-timeout-operator-stop-regression.log` | `04314a7bc66762577a3f29056ffeb269ddc8d410ea3ba73eaf956e86d37cc8f2` |
| `slow-timeout-complete-verify.log` | `552fa39273fb8868c3318f4a31ab921dd4076b0080f1169cc16022b832637d88` |

Les cinq skips de la première passe complète ont les conditions suivantes, confirmées
par les tests et les rapports Surefire ; ils ne sont pas comptés comme des succès :

- `LocalJ7ExportFileStoreTest.refusesASymbolicLinkInsteadOfFollowingIt` et
  `refusesAMatchingCandidateSymlinkDuringOrphanEnumeration` s'arrêtent sur une hypothèse
  JUnit lorsque `Files.createSymbolicLink` lève `UnsupportedOperationException`,
  `IOException` ou `SecurityException` : la création de liens symboliques n'est pas
  disponible dans cet environnement de test.
- `J8BenchmarkExportCommandTest.refusesAnExportsSymbolicLinkOrReparsePointWhenSupported`
  et `refusesAJ8SymbolicLinkOrReparsePointWhenSupported` utilisent la même condition de
  disponibilité de `Files.createSymbolicLink` ; leur hypothèse JUnit échoue avant les
  assertions sur les chemins d'export.
- `J6NativeBinaryPipelineQualificationTest.dockerQualificationPassesThreeSequentialRunsAtEffectiveDefaults`
  exige explicitement `-Dj6.docker.qualification=true`. La propriété est absente de
  cette invocation, comme le confirme Surefire. Ce skip ne désigne ni les 213 tests
  PostgreSQL Failsafe exécutés, ni le contrôle natif synthétique J6 de la même classe.

Les XML Surefire de la dernière exécution confirment exactement ces cinq skips et
leurs mêmes conditions. Les 2 068 cas comprennent donc 2 063 cas exécutés avec succès
et cinq ignorés ; les 213 intégrations PostgreSQL sont toutes exécutées avec succès.

Le package ciblé comprend `LiveCampaignServiceTest` (88 cas), `LiveTimeoutRecoveryTest`
(10), `ChildJvmPlaywrightProviderSupervisorTest` (64),
`ResilientPlaywrightProviderCampaignFactoryTest` (19), ainsi que les tests worker
d'observation réseau (13), de protocole (13), de contrat de sécurité (1) et de
`Retry-After` (11). Tous ces cas réussissent dans cette passe de 219 tests.

La première passe native échoue sur
`ProviderPlaywrightLocalQualificationIT.liveV6AbandonsTimedOutExchangeAndUsesTheSameWorkerForANewGroup(boolean)[2]` :
le scénario de corps bloqué retourne un timeout non récupérable alors que le test
attendait une récupération. La sonde locale explique ce résultat par l'absence de
terminal CDP après `COMMIT` pour ce corps bloqué. La qualification est ajustée pour
exiger l'arrêt global de ce cas, sans changer la garde de production. Un cas distinct
de corps finissant à 2,4 secondes pour un timeout de deux secondes couvre la possibilité
d'obtenir `FINISHED` dans la grâce, puis d'abandonner le corps et de nettoyer le contexte.
La deuxième passe ciblée native et UI de cinq cas révèle encore deux défauts concrets :
le terminal naturel du corps finissant à 2,4 secondes est supprimé par `Page.stopLoading`
après `COMMIT`, et une règle CSS `display: grid` neutralise `hidden` sur le bloc de preuve.
Le correctif transport attend désormais naturellement le terminal après les en-têtes
dans la même grâce ; le correctif UI rétablit la priorité de l'état masqué. La passe
réussie de 17:02:51Z vérifie sept cas de transport : deux réponses lentes complètes
(avant les en-têtes et pendant le corps), deux timeouts (avant les en-têtes avec
`ABORTED` et après les en-têtes sans terminal donc fatal), un `FINISHED` tardif avec
corps abandonné et deux refus 403/429 conservés. Les deux cas UI réussissent également.
Ces résultats corrigent les attentes et défauts précédents sans effacer les passes rouges.

La qualification ciblée transport/UI est donc acquise sur cet état. Le déplacement
ultérieur de `receivedAt` immédiatement après `body()` est ensuite vérifié par les
39 tests worker et les deux cas natifs réussis à 17:04. La clôture complète du correctif
n'est pas déduite de ces seules sélections : la dernière vérification complète
réussit ensuite à 17:17:59Z sur l'état corrigé.

### Arrêt individuel concurrent : régression découverte et corrigée

La revue a trouvé qu'un arrêt individuel pendant un timeout pouvait laisser une tâche
`inFlight` dans l'ordonnanceur et bloquer les autres rencontres. La passe
`slow-timeout-final-clean-verify.log` a été interrompue volontairement pendant J6, avant
tout bilan global, pour corriger ce défaut. Le service acquitte désormais la fin de
l'échange même lorsque la tentative est abandonnée à la suite de cet arrêt ; il ne
reprogramme pas la rencontre arrêtée. L'autre rencontre peut poursuivre ses tâches.

Deux régressions avec le véritable ordonnanceur et deux rencontres couvrent l'arrêt
avant la remontée du timeout et l'arrêt pendant la publication de la preuve. Elles
font partie des 90 tests service réussis, avec les 10 tests de politique, à 17:09:45Z.
Le code est figé après ce correctif. La nouvelle passe
`slow-timeout-complete-verify.log`, lancée avec `-Pintegration-tests clean verify`,
est **réussie à 17:17:59Z**, avec les résultats détaillés ci-dessus. L'interruption
précédente reste documentée sans lui attribuer un bilan inexistant.

### Commandes des passes intermédiaires

Les invocations ci-dessous ont été communiquées par le parent qui les a exécutées ;
les logs Maven ne contiennent pas eux-mêmes leurs lignes de lancement complètes.
Elles utilisent Java 25.0.4, Maven Wrapper et le dépôt local existant, hors réseau.
Le répertoire courant est le worktree `.tmp/wo058-live-j4-j5`. Pour le natif, le cache
Chromium est `.tmp/provider-playwright-browsers` dans ce même worktree,
`PLAYWRIGHT_BROWSERS_PATH` désigne ce cache et `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`.
Le `PATH` de l'invocation expose Java et le CLI Docker.

```powershell
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository clean verify

.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Pprovider-playwright-runtime,provider-playwright-local-qualification -Dtest=ChildJvmPlaywrightProviderSupervisorTest,LiveCampaignServiceTest,LiveTimeoutRecoveryTest,ResilientPlaywrightProviderCampaignFactoryTest,ProviderRetryAfterTest,ProviderPlaywrightWorkerProtocolTest,ProviderPlaywrightWorkerSecurityContractTest,ProviderPlaywrightWorkerNetworkObservationTest package

.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Pprovider-playwright-runtime,provider-playwright-local-qualification -DskipTests=false -DskipITs=false '-Dit.test=ProviderPlaywrightLocalQualificationIT,LiveGroupedCampaignLocalQualificationIT#smokeIncludesFirstTenGroupsAndTransactionalNormalization' -Dwo058.grouped.v6=true -Dprovider.playwright.browser-cache=C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo058-live-j4-j5/.tmp/provider-playwright-browsers failsafe:integration-test@provider-playwright-loopback-qualification failsafe:verify@provider-playwright-loopback-qualification
```

La ligne complète de la toute première sélection ciblée de 195 cas n'est pas consignée
ici ; son log et son résultat rouge sont conservés sans reconstruire ses options.

La passe réussie de neuf cas utilise les mêmes arguments natifs et le même environnement,
avec la sélection ciblée suivante, communiquée par le parent :

```powershell
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Pprovider-playwright-runtime,provider-playwright-local-qualification -DskipTests=false -DskipITs=false '-Dit.test=ProviderPlaywrightLocalQualificationIT#liveV6RequiresTerminalProofBeforeReusingTimedOutContext+liveV6DiscardsLateCompletedBodyAndReusesContextOnlyAfterFinishedProof+liveV6RetainsRefusalBeforeSlowBodyAndBlocksAnotherWrapperBeforeWorkerCreation+liveV6AcceptsDelayedHeadersOrBodyWithinTheUniqueDeadline,LiveCampaignPaginationBrowserQualificationIT' -Dwo058.grouped.v6=true -Dprovider.playwright.browser-cache=C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo058-live-j4-j5/.tmp/provider-playwright-browsers failsafe:integration-test@provider-playwright-loopback-qualification failsafe:verify@provider-playwright-loopback-qualification
```

La sélection des 100 régressions puis la dernière vérification complète utilisent les
invocations suivantes, également communiquées par le parent. Toutes deux sont terminées
avec succès dans les logs indiqués ci-dessus :

```powershell
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Dtest=LiveCampaignServiceTest,LiveTimeoutRecoveryTest test

.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Pintegration-tests clean verify
```

### Smoke nominal archivé séparément

La [preuve JSON propre à ce correctif](WO058-GROUPED-LIVE-V6-SLOW-TIMEOUT-SMOKE-20260909.json)
est une copie octet pour octet de `.tmp/wo058-v6-smoke-qualification.json`, vérifiée par
SHA-256 à la copie :

```text
ed82fafdea6bab84af538657213af32f787daa49850bef2aa5ae63c8d2f30c9f
```

Elle porte `PASSED`, un départ à `2026-09-09T16:43:05.678082800Z` et une durée mesurée
de 180,0024807 secondes, pour sept rencontres. Les 48 tentatives correspondent à
48 départs persistés, 48 fins persistées et 48 diagnostics de transport complets.
Le rapport constate zéro cycle manqué, zéro requête hors périmètre et zéro appel réel
au fournisseur. La base opérateur n'est pas utilisée ; le wrapper de résilience et la
persistance PostgreSQL sont ceux de production, avec un timeout effectif de 30 secondes.
Les 35 reports dus à la protection sont comptés séparément des cycles manqués.
`workerAndChildrenClosed=true` atteste la fermeture contrôlée du worker et de ses enfants.

Ce smoke vérifie le chemin nominal pendant trois minutes. Il ne couvre pas une fenêtre
de compositions de 300 secondes entière et **n'est pas une requalification soutenue de
35 minutes**. Il ne remplace pas les scénarios de corps bloqué, de fin de transport
incertaine ou de suspension 403/429. Ses enveloppes candidates restent des paramètres
du test ; elles ne créent ni nouveau profil qualifié, ni nouvelle capacité.

| Vérification requise | Preuve attendue | Résultat courant |
|---|---|---|
| Contrat de groupe v6 | Vrai ordonnanceur → tracker : 404 initial, statistiques différées, compositions dues, retour PARSED ; refus des sauts historiques, doublons et mauvaises identités | Régression et tests de contrat réussis ; dernière vérification complète réussie à 17:17:59Z |
| Politique de tolérance | Limite totale, deux timeouts sans PARSED, récidive d'une famille, finalisation, fenêtre et arrêt opérateur | 90 tests service et 10 tests de politique réussis à 17:09:45Z, dont deux régressions avec deux rencontres ; dernière vérification complète réussie |
| Réponse lente complète | Réception avant timeout, JSON conservé et traitement normal | Deux cas Chromium réussis à 17:02:51Z, puis deux cas natifs réussis à 17:04:20Z après déplacement final de `receivedAt` |
| Timeout avec fin prouvée | CDP corrélé, annulation/page nettoyée, contexte conservé, groupe fermé et prochain match admissible | Cas avant en-têtes avec `ABORTED` et corps finissant dans la grâce avec `FINISHED` réussis à 17:02:51Z |
| Timeout incertain ou protocole invalide | Arrêt global ; aucune réutilisation du contexte ni appel suivant | Cas Chromium après en-têtes sans terminal réussi à 17:02:51Z ; tests ciblés superviseur/worker réussis |
| Refus 403/429, y compris corps lent | Suspension persistante prioritaire et absence de reprise technique | Deux cas Chromium 403/429 réussis à 17:02:51Z ; tests ciblés wrapper/worker réussis |
| Présentation des diagnostics | Masquage sans preuve, affichage dynamique de la preuve, conservation des états et du focus | Deux cas UI Chromium réussis à 17:02:51Z |
| Persistance et reprise locale | V45 réelle, conservation historique, preuve monotone, compteurs et schéma de restauration | Dernière passe PostgreSQL : 213 cas exécutés avec succès, aucun échec, erreur ou skip |
| Vérifications générales | Commandes Maven effectives, Surefire/Failsafe, skips explicités et qualifications Chromium isolées | Package 220 vert, smoke vert, sélection native/UI 9 verte, contrôle de réception vert et 100 régressions vertes ; dernière `-Pintegration-tests clean verify` réussie sur code figé (2 068 standards / 213 PostgreSQL) |

Les contrôles proposés sur le contrat de groupe sont `LiveProviderGroupTrackerTest`,
`LiveV6BackoffGroupContractTest`, `ProviderLiveV5DelayGateTest` et `LiveProviderSessionTest`.
Ils utilisent le code de production, une horloge fournie ou des doublures locales,
sans accès fournisseur. La première `clean verify` confirme respectivement 15, 1, 7 et
6 cas réussis. Les commandes des trois passes suivantes sont consignées ci-dessus
d'après les invocations du parent ; l'inventaire de 56 fichiers est fixé ci-dessous.
Les résultats des dernières réexécutions et leurs limites figurent dans les tableaux ci-dessus.

La [preuve temporelle v6 précédente](WO058-LIVE-V6-CAPACITY-20260909.md) conserve ses
494 échanges, son corpus et ses empreintes immuables. Elle qualifie son régime nominal
dans sa portée annoncée ; elle ne prouve ni la récupération sur timeout ajoutée ici,
ni une cadence garantie avec réponses lentes, ni l'acceptation de SofaScore. Aucun nouveau
SHA de profil ni nouvelle capacité n'est inventé dans ce complément.

## Inventaire final des fichiers du correctif

Le relevé `git status --porcelain` effectué après la passe ciblée transport/UI réussie
contient **56 fichiers : 50 fichiers suivis modifiés et six nouveaux fichiers**.
Le premier relevé de 54 fichiers a été complété par l'annexe JSON du smoke et par
`app.css`, qui corrige la priorité du masquage. Cet inventaire inclut la documentation
et la preuve jointe ; il ne constitue pas le résultat de la vérification finale.

Les contrôles finaux du parent confirment `git diff --check`, le décodage UTF-8 strict
des 56 fichiers, l'absence de marqueur de credential dans les ajouts et l'absence du
marqueur temporaire `LOCAL_TIMEOUT_PROBE`. `server.address=127.0.0.1` et les paramètres
fournisseur/live/Playwright désactivés par défaut restent inchangés. Ces contrôles
s'ajoutent aux tests ; ils ne qualifient aucune acceptation du fournisseur.

### 50 fichiers suivis modifiés

```text
ADR-SS-005-bounded-local-live-j4-j5-campaigns.md
CHANGELOG.md
README.md
docs/architecture/LIVE-J4-J5-CAMPAIGNS.md
docs/architecture/LIVE-PROVIDER-RESILIENCE-PROPOSAL-20260909.md
docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md
docs/work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md
scripts/Backup-Restore-J6.ps1
scripts/Invoke-J6Retention.ps1
src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcLiveDiagnosticStore.java
src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java
src/main/java/com/bettingproject/sofascorelocal/application/live/GroupedLiveScheduleV4.java
src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java
src/main/java/com/bettingproject/sofascorelocal/application/live/LiveProviderSession.java
src/main/java/com/bettingproject/sofascorelocal/application/live/LiveSchedule.java
src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisor.java
src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveProviderGroupTracker.java
src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderCampaignFactory.java
src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightProviderException.java
src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/PlaywrightTransportDiagnostic.java
src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ProviderNetworkStartDelayGate.java
src/main/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactory.java
src/main/java/com/bettingproject/sofascorelocal/port/LiveDiagnosticStore.java
src/main/resources/static/css/app.css
src/main/resources/static/js/live-campaign.js
src/main/resources/templates/live-campaign.html
src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignFormBrowserQualificationIT.java
src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignIncidentsBrowserQualificationIT.java
src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignLineupsBrowserQualificationIT.java
src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPaginationBrowserQualificationIT.java
src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignStatisticsBrowserQualificationIT.java
src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveGroupedCampaignLocalQualificationIT.java
src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/ProviderPlaywrightLocalQualificationIT.java
src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerNetworkObservationTest.java
src/provider-playwright-test/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocolTest.java
src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderMainDocumentNetworkObservation.java
src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerMain.java
src/provider-playwright/java/com/bettingproject/sofascorelocal/provider/playwright/worker/ProviderPlaywrightWorkerProtocol.java
src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignControllerTest.java
src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java
src/test/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignServiceTest.java
src/test/java/com/bettingproject/sofascorelocal/application/live/LiveProviderSessionTest.java
src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ChildJvmPlaywrightProviderSupervisorTest.java
src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveProviderGroupTrackerTest.java
src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ProviderLiveV5DelayGateTest.java
src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/ResilientPlaywrightProviderCampaignFactoryTest.java
src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java
src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java
src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java
src/test/java/com/bettingproject/sofascorelocal/integration/LiveDiagnosticPersistenceIT.java
```

### Six nouveaux fichiers de réalisation, de rapport et de preuve

```text
docs/validation/WO058-GROUPED-LIVE-V6-SLOW-TIMEOUT-SMOKE-20260909.json
docs/validation/WO058-SLOW-TIMEOUT-RECOVERY-20260909.md
src/main/java/com/bettingproject/sofascorelocal/application/live/LiveTimeoutRecoveryPolicy.java
src/main/resources/db/migration/V45__proven_transport_exchange_end.sql
src/test/java/com/bettingproject/sofascorelocal/application/live/LiveTimeoutRecoveryTest.java
src/test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveV6BackoffGroupContractTest.java
```

L'annexe supplémentaire archivée dans cette passe documentaire est
`docs/validation/WO058-GROUPED-LIVE-V6-SLOW-TIMEOUT-SMOKE-20260909.json` ; ses octets ne
remplacent aucune preuve native ou de capacité antérieure.

Aucune livraison Eclipse, migration opérateur, modification VPN, collecte fournisseur,
fusion ou clôture de WO n'est annoncée par ce rapport. Une éventuelle nouvelle campagne
réelle reste une action opérateur distincte après validation du correctif.

La livraison devra reconstruire ensemble l'application et son worker pour IPC v7.
V45 sera appliquée au prochain démarrage opérateur ; ce lot ne démarre pas l'application
et n'applique aucune migration à la base opérateur.
