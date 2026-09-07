# WO-058 — Réalisation et qualification locale J4/J5, 7 septembre 2026

État de la preuve : `READY_FOR_REVIEW` ; réalisation qualifiée hors fournisseur et résultats
consignés après lecture des rapports effectifs. Ce rapport ne clôture ni le WO ni une campagne fournisseur.

## Autorité, périmètre et base

Le propriétaire a déclaré « Je valide le WO-058 les travaux peuvent commencer », puis a
explicitement demandé la réalisation du plan J4/J5. Il a libéré le port 8087 pour la qualification.
Cette autorité fait suite à « Je valide formellement la v0.1 de l'ADR », datée du 7 septembre
2026. L'observation administrative `2026-09-07T09:23:14Z` n'est pas l'heure exacte du message.

- [WO-058](../work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md), validation acquise ;
  passage à `IN_PROGRESS` enregistré à la reprise.
- [ADR-SS-005 v0.1 accepté](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md), décisions inchangées.
- [Proposition exacte acceptée](ADR-SS-005-v0.1-accepted-proposal-20260907.txt), SHA-256
  `48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e`.
- ADR administratif après acceptation : SHA-256
  `48105be765fbe943e5153ff551ea5eaf5e3b79345c2b08c28d57581dee78ca51`.
- Worktree : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo058-live-j4-j5`.
- Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
- HEAD/base du train `feature/V0.1.0-RC01` :
  `6dfd14286d4f269cbe100bd965257c20298538db`. Aucun commit n'est attribué au diff local.

Les documents antérieurs sont conservés. Les quatre skills `ss-work-order`,
`ss-data-contract-replay`, `ss-postgres-change` et `ss-verify` ont guidé la réalisation et
les contrôles. La tâche référencée « Poursuivre collectes » fournit seulement du contexte.
Le lot n'exécute aucune instruction de collecte issue de cet historique.

## Réalisation soumise à revue

Le lot livre la sélection depuis /events, le manifeste immuable et le lancement explicite,
un ordonnanceur pur, une session worker/contexte unique, le garde durable commun aux parcours
manuels, les budgets et l'admission selon cadence/espace PostgreSQL. V33 ajoute sept tables live
et un garde. Brut, occurrences, résultats et projections sont publiés par transactions séparées,
sans SQL pendant le réseau. Les lectures suivent les occurrences et conservent A→A/A→B→A.

Le score courant est sourcé par J4 ; les signaux J5 ne confirment jamais la fin sportive.
L'interface distingue fraîcheur, résultat sportif, collecte et complétude, actualise localement
toutes les cinq secondes et protège les commandes par jetons locaux et origine.
L'[architecture](../architecture/LIVE-J4-J5-CAMPAIGNS.md) et le
[runbook](../runbooks/LIVE-J4-J5-CAMPAIGNS.md) détaillent les paramètres et les limites.

La relecture croisée a corrigé avant livraison : échéance J5 effective retardant un J4,
retards au milieu d'un triplet, contrôle final de l'heure après SQL, SQL sous le verrou d'arrêt,
complétude/arrêt métier provisoires après échec de publication, profil d'admission non relisible,
préparation nouvelle masquant une campagne en cours, hash de projection JSONB non reconstructible
et propriété encore reconnue pendant `CLEANUP_REQUIRED`. Ces points ont des contre-tests ciblés.

## Historique d'exécution conservé

| Exécution | Résultat observé |
|---|---|
| Cadrage initial `clean verify`, 08:44:38Z | 1 193 tests, 1 échec, 0 erreur, 5 skips ; J6 refusait le listener 8087. [Preuve antérieure](WO058-LIVE-J4-J5-SCOPING-20260907.md) conservée |
| J6 ciblé après libération du port, 09:46:09Z | 1 test, 0 échec, 0 erreur, 0 skip ; succès ciblé distinct de la suite initiale |
| Première compilation live ciblée | Échec : mauvais accesseur de complétude, corrigé en `scorePercent()` |
| Normaliseurs/processeur/scheduler, 10:05:35Z | 36 tests réussis |
| Premier ensemble Live* | 52 tests, 1 échec, 1 erreur : configuration des mocks MVC ; corrigée |
| Première intégration ciblée, 10:10:49Z | 10 tests scheduler et 10 tests PostgreSQL réussis ; ensuite enrichis et réexécutés |
| Ensemble ciblé, 10:13:40Z | 94 tests réussis, sans erreur/échec/skip |
| Installation explicite du cache Chromium | Réussie ; téléchargement du runtime dédié, aucun appel SofaScore |
| Lanceur J5 historique, 10:19:32Z | Worker 11+1+10 tests et 14 Chromium réussis ; lanceur ensuite en échec car il attendait 10 tests protocole au lieu de 11 |
| Relecture du gate J5 sur ces XML | Compte corrigé à 11 exact et cas TCP fermé nommé obligatoire ; contrôle des rapports et scanner réussis, sans rejouer les tests |
| Live loopback, 10:27:18Z | 3 tests : 1 réussite, 1 échec, 1 erreur. Attente JS incompatible CSP et assertion de sortie processus trop immédiate ; corrigées |
| Live loopback, 10:34:47Z | 4 tests : 2 réussites, 2 échecs de contrôle d'artefacts ; ressources Playwright identifiées par chemin et octets exacts dans le contrôle corrigé |
| Première suite complète de réalisation, 10:38:10Z | 1 292 tests, 1 échec, 3 erreurs, 5 skips : assertion historique de formulaire et mocks imbriqués ; corrigés |

Le test protocole supplémentaire préexistait au WO dans
`154349a2fbebe3fd0a43a63c7105f690ff04976b`
(`acceptsOnlyTheClosedLoopbackOriginTcpPortRange`). Le correctif du lanceur ne réduit pas
le nombre attendu et exige explicitement le succès de ce cas. Les anciens échecs ne sont pas
remplacés par les résultats des exécutions ultérieures.

## Qualification finale

| Commande | Résultat final observé |
|---|---|
| `mvnw.cmd clean verify`, 10:54:23Z | PASS ; Surefire : 1 302 tests, 0 échec/erreur, 5 skips ; Failsafe hérité : 122 tests, 0 échec/erreur/skip |
| `mvnw.cmd -Pintegration-tests verify`, 10:59:36Z | PASS ; Surefire : 1 302 tests, 0 échec/erreur, 5 skips ; Failsafe : 122 tests, 0 échec/erreur/skip |
| `scripts/Invoke-LivePlaywrightLoopbackQualification.ps1`, 10:42:48Z | PASS ; 3 tests transport + 1 navigateur, 0 échec/erreur/skip ; gate exact et rapports récents vérifiés |
| `scripts/Invoke-J5PlaywrightLoopbackQualification.ps1`, 11:01:56Z | PASS après correctif du verrou partagé et du gate ; 22 tests worker et 14 Chromium, 0 échec/erreur/skip ; scanner sensible et canaris runtime verts |
| Chromium `LiveCampaignBrowserQualificationIT` ciblé, 11:02:59Z | PASS ; 1 test / 33,61 s, 0 échec/erreur/skip ; fraîcheur finale, âge figé, priorité entre campagnes, focus et sélection |

La session de transport a servi 24 requêtes sur deux IDs et quatre familles avec un seul worker
sur trois passages espacés. Les arrivées loopback respectent le délai global de trois secondes.
Mémoire JVM observée : 112 472 064 octets au premier relevé et 95 109 120 au dernier.
L'arrêt pendant admission lente a répondu en moins de 500 ms sans GET ultérieur ; la sortie
du worker et le nettoyage des processus possédés sont vérifiés.

Empreintes des XML lus à cette exécution (artefacts runtime ignorés par Git) :

- Transport, 3 cas / 152,382 s :
  `66ec96246f5f9ce3f093dbe972bedf05b6d2212cea826a780b1b5d77d5c1d44a`.
- Navigateur, 1 cas / 23,622 s :
  `6f2f1c02c5fb2043ea8627b23f80680288b64d128b0b1badc2058f0a523962df`.

La commande avec profil explicite d'intégration et la requalification ciblée de l'interface
finale passent. Le [relevé des suites](WO058-QUALIFICATION-SUITES-20260907.json) conserve
les comptes, cas ignorés et SHA-256 de chaque XML lu. Le build hérite bien de Failsafe même sans
`-Pintegration-tests`. Les deux commandes explicitement demandées sont exécutées ; le lanceur
`Verify-Local.ps1 -WithIntegrationTests`, qui rejouerait ces mêmes suites, n'est pas ajouté.

Les cinq skips standards sont quatre cas de symlinks/reparse points non disponibles dans
ce contexte Windows et le scénario Docker J6 en trois passages, qui exige l'opt-in distinct
`j6.docker.qualification`. Ils ne deviennent pas des réussites. Le test J6 synthétique initialement
bloqué par 8087 passe ; la sauvegarde/restauration PostgreSQL live est qualifiée séparément par
`LiveCampaignPersistenceIT`, sans revendiquer l'exécution du scénario J6 optionnel.

La commande navigateur finale utilise les profils
`provider-playwright-runtime,provider-playwright-local-qualification`,
`-DskipTests=false -DskipITs=false -Dit.test=LiveCampaignBrowserQualificationIT`, le cache
Chromium dédié et les objectifs
`failsafe:integration-test@provider-playwright-loopback-qualification` puis
`failsafe:verify@provider-playwright-loopback-qualification`. Le worker et les classes avaient
été reconstruits par le lanceur J5 immédiatement précédent. Le long scénario de transport live,
inchangé depuis sa réussite, n'est pas rejoué pour ces seuls ajustements d'interface.

## Revue finale et inventaire

La [liste des fichiers et empreintes du candidat](WO058-CANDIDATE-FILES-20260907.sha256)
couvre le diff local, fichiers nouveaux compris, à l'exception de cet inventaire lui-même.
Elle distingue ce candidat non committé du HEAD de base. Le relevé JSON des suites ne conserve
que noms, comptes, cas ignorés et empreintes ; aucun payload ou secret de test n'y est copié.

UTF-8 strict, liens locaux et espaces finaux sont contrôlés sur les fichiers modifiés/nouveaux.
`git diff --check` passe. La copie ADR acceptée garde le même objet Git avec ou sans filtres :
`f1290d7d049b43930cf8978a2827587d0bbc1c89`. Les signatures de secrets relevées dans le lanceur
J5 correspondent exclusivement à ses canaris historiques inchangés ; aucune valeur secrète
nouvelle n'est ajoutée. La revue confirme V1..V32 et ADR-SS-002..004 inchangés, liaison loopback
et opt-ins réseau désactivés par défaut.

## Correspondance AC01–AC17

| Critère | Preuves et portée |
|---|---|
| AC01 sélection | LiveCampaignServiceTest, LiveCampaignControllerTest, manifeste/identités/FK PostgreSQL ; sélection vide, doublon, fixture, ID forgé, expiration, profil modifié |
| AC02 lancement unique | Service : double lancement ; PostgreSQL : garde concurrent, génération, unicité ; Web : jeton à usage unique |
| AC03 attente | LiveScheduleTest et replay : début retardé, notstarted répété, absence de J5 avant inprogress |
| AC04 début déjà en cours/fini | Scheduler, service et replay : transition directe et un dernier triplet borné |
| AC05 J5 | Scheduler, replay et Chromium : familles ordonnées et contexte partagé ; espacement logique de 60 s testé, délai réel global de 3 s mesuré |
| AC06 fin sportive | LivePayloadNormalizerTest, scheduler et replay : HT, injuryTime 45/90/105/120, prolongation, signaux révisés ; seul J4 finished confirme |
| AC07 signal absent | Scheduler : secours cinq minutes ; replay et normaliseur : incidents vides/absents, indisponibilité et borne sans faux finished |
| AC08 fraîcheur | PostgreSQL : occurrences A→A→B→A, IDs d'observations, dates et classifications préservées ; UI protège une observation manuelle plus récente |
| AC09 404 | Processeur, service/replay et Chromium : indisponibilité distincte puis succès au cycle normal ; dernière donnée bonne conservée |
| AC10 erreurs | Deux matchs synthétiques : schéma isolable, contre-épreuves identité/HTTP/contenu/transport/parser/stockage ; échec publication finale et échec publication d'erreur métier globaux |
| AC11 capacité | Admission conservatrice et profil qualifié simulé, enveloppes maximales, refus surcharge ; deux cycles manqués au milieu/hors triplet ; pas de rafale |
| AC12 arrêt | Service, scheduler et Chromium : fence, GET engagé, SQL lent, stop global/individuel, fenêtre après SQL, nettoyage borné |
| AC13 reprise | Garde et service : propriétaire absent/actif/identité inaccessible, budgets inconnus conservés, aucun redémarrage réseau ; watchdog conservateur de suspension |
| AC14 PostgreSQL | LiveCampaignPersistenceIT et FlywayMigrationIT : neuf, V32 prérempli→V33, concurrence, contraintes, rollback, rétention, restauration et empreintes |
| AC15 vue | MockMvc sur vrais templates et Chromium sur HTML synthétique avec JS de production : révisions hors ordre, onglet masqué, focus et sélection, origine et CSP |
| AC16 défauts | Suite standard, SecurityHeadersFilterTest et revue configuration : loopback, trois opt-ins désactivés, aucune construction de navigateur au démarrage standard |
| AC17 limites | Replay marqué SYNTHETIC_REPLAY ; données et PostgreSQL isolés, loopback réel distinct du fournisseur ; pilote opérateur non exécuté |

Cette correspondance identifie les éléments de qualification de réalisation ; elle ne coche
pas AC17 comme campagne fournisseur réussie et ne remplace pas la revue humaine.

## Limites et suites opérationnelles

- Le premier pilote reste à un match. Deux événements dans un test synthétique ou loopback
  ne qualifient pas automatiquement le palier fournisseur 2/3. Le profil doit être revu et admis.
- La session Chromium mesure trois passages espacés sur plus de deux minutes, 24 appels et la
  mémoire du worker JVM. Elle ne constitue pas un essai de quatre heures ni une mesure exhaustive
  de la mémoire de tous les processus Chromium.
- Les scénarios de crash/veille sont déterministes ou fondés sur l'identité de processus ;
  aucune mise en veille physique du poste de l'opérateur n'est effectuée.
- Le résultat d'une famille JSONB et son hash sont liés à la forme effectivement persistée.
  Le replay synthétique ne confère aucune validation humaine aux données.
- Une clé brute précédemment purgée provoque `LIVE_RAW_PREVIOUSLY_PURGED` et un arrêt global
  de stockage, sans réhydratation historique. Le garde incertain n'est jamais libéré par délai.
- La sauvegarde/restauration PostgreSQL du lot est synthétique. La migration de la base de
  l'opérateur exige sa préparation J6 concrète ; aucun accès à cette base n'est inclus.

Les travaux restent locaux sans commit, push, PR publiée ou collecte réelle. ADR-SS-002 à 004,
les migrations V1..V32, les endpoints et les valeurs réseau par défaut restent inchangés.
La revue de réalisation, la préparation de la base opérateur et le manifeste exact du pilote
précèdent toute expérimentation fournisseur ; la clôture du WO est distincte.
