# WO-058 — Qualification des groupes live-v4

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Périmètre et autorité

Plan explicitement demandé le 08/09/2026 : J4/incidents/statistiques à 60 secondes nominales,
compositions initiales puis réparties sur cinq minutes, groupes séquentiels et trois secondes
entre groupes. Décision : [ADR-SS-005 v0.4](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md).
Base de cette reprise : `c972d63`, worktree `.tmp/wo058-live-j4-j5`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.

**La cadence à dix rencontres est démontrée sur le scénario synthétique local décrit ici.**
Le profil d'admission associé couvre les coûts du régime établi de 64 Kio. Il ne constitue
pas une garantie de latence Internet, de corps récurrents de 5 Mio, ni de fraîcheur face au
site SofaScore. La base opérateur, ses collectes et ses paramètres Eclipse n'ont pas été modifiés.

## Réalisation

- Groupes côté serveur, campagne/événement/séquence/phase contrôlés, quatre endpoints distincts
  au maximum. Sérialisation et repère temporel après chaque échange ; trois secondes entre
  groupes, délai historique conservé pour les parcours manuels et v1–v3.
- J4 → incidents → statistiques → compositions dues. Un J4 `postponed` coupe la suite.
  Avant match, J4 et compositions sont périodiques après `notstarted` ; une composition
  récente ne bloque pas les premières statistiques/incidents au début du jeu.
- Phases réparties sur la minute, une prochaine échéance par famille, aucun rattrapage en
  rafale. Retards et surcharge durable contrôlés ; les finalisations attendues n'immobilisent
  pas les autres rencontres. Un refus de réservation ordinaire peut programmer le contrôle
  final réservé ; le refus final arrête le suivi.
- V39 additive : profil figé, groupes/tentatives et échéances/révisions par famille. Verrous
  propriété/campagne/événement/groupe et contraintes SQL. L'interruption orpheline annule les
  échéances dans la même transaction, historique conservé. Aucune ancienne observation,
  migration ou empreinte réécrite.
- Interface : cible, capacité, autonomie avec budget restant ; réception distincte du dernier
  changement, prochaine collecte et retard par famille. Lecture locale toutes les cinq secondes,
  timeout dix secondes, dernières données et panneaux statistiques repliables conservés.

## Qualification native soutenue

La commande dédiée utilise Java 25.0.4, Maven wrapper hors ligne, Spring Boot 4.1.0,
Chromium et PostgreSQL isolé. Le cache Chromium était déjà installé. Le packaging runtime
est exécuté explicitement, puis les deux goals Failsafe de l'exécution
`provider-playwright-loopback-qualification`, avec la propriété d'opt-in soutenu et une
sélection de méthode pour le run long. Les commandes exactes sont consolidées plus bas.

Run terminé le **08/09/2026 à 11:22:23 UTC** : `BUILD SUCCESS`, un test, zéro échec,
erreur ou ignoré, **35 min 11 s** de commande. Fenêtre réelle : 300 secondes initiales puis
**1 800,0136 secondes de régime établi**. 1 128 réservations/réponses, dont 960 établies,
281 018 368 octets, zéro cycle manqué, 250 observations immuables et 1 194 révisions d'échéance.

Le harness emploie l'ordonnanceur de production, le superviseur Chromium, les réservations
PostgreSQL isolées, la conservation brute, la normalisation et la publication transactionnelle.
Le contrôle `docker df` est effectué pour chaque appel. Une lecture complète du ledger
est effectuée toutes les cinq secondes : 420 lectures, P95 40,07 ms, maximum 78,26 ms.
Le rendu Chromium est vérifié séparément dans la qualification UI.

### Cadence établie

| Famille | Réponses établies | Intervalle de réception P95 | Maximum | Maximum entre publications |
|---|---:|---:|---:|---:|
| J4 | 300 | 60,098 s | 60,205 s | 60,227 s |
| Incidents | 300 | 60,141 s | 60,368 s | 60,413 s |
| Statistiques | 300 | 60,202 s | 60,520 s | 60,532 s |
| Compositions | 60 | 300,190 s | 300,273 s | 300,274 s |

Retard nominal des compositions : P95 **1,874 s**, maximum **1,909 s**, pour la cible 300 s.
Chaque match possède 30 réponses établies par famille critique et six compositions.
Le [contrôle séparé des 40 couples](WO058-GROUPED-LIVE-V4-PER-EVENT-20260908.json) vérifie
P95/max par match/famille : tous passent. Les agrégats ci-dessus ne remplacent pas ce contrôle.

Dette de publication P95 : 1,425 s au premier quart, 1,387 s au dernier quart, sans croissance.
Pause inter-groupes minimale observée : **3,05899 s**. Contexte et processus enfants clôturés ;
zéro requête hors périmètre, zéro appel réel fournisseur, aucune utilisation de la base opérateur.

### Corpus et initialisation séparée

Délais serveur de 0/30/80/150 ms et corps établis de 64 Kio : 135 métriques sur trois périodes
(270 signaux), 30 incidents et 44 joueurs. Les réponses alternent changements et répétitions ;
statistiques et compositions revisitent deux états normalisés. Ce mélange exerce publication
et déduplication, sans représenter des métriques/joueurs tous nouveaux à chaque réponse.

Les quarante premières réponses, une par couple, font chacune 5 Mio. Cette vague initiale
a un retard maximal de réception de 13,210 s et de publication de 13,468 s, sans cycle perdu.
Ses coûts ne sont pas assimilés à ceux du régime établi.

| Famille | Requête max. initiale | Traitement max. initial | Requête max. établie | Traitement max. établi |
|---|---:|---:|---:|---:|
| J4 | 742,3 ms | 1 085,2 ms | 295,8 ms | 385,8 ms |
| Incidents | 740,6 ms | 490,9 ms | 324,8 ms | 411,5 ms |
| Statistiques | 783,5 ms | 471,7 ms | 293,2 ms | 339,7 ms |
| Compositions | 788,2 ms | 454,0 ms | 255,2 ms | 308,7 ms |

## Profil de capacité retenu

Le candidat initial « requête 500 ms + traitement 200 ms » est **rejeté** : le traitement
dépasse 200 ms dans les quatre familles. La cadence native réussie ne valide pas ce candidat.

Le [profil figé](WO058-GROUPED-LIVE-V4-PROFILE-20260908.json) retient les maxima établis
arrondis au-dessus :

| Famille | Requête budgétée | Traitement budgété |
|---|---:|---:|
| J4 | 300 ms | 400 ms |
| Incidents | 350 ms | 450 ms |
| Statistiques | 300 ms | 350 ms |
| Compositions | 300 ms | 350 ms |

Poids de dix matchs sur cinq minutes : **264 s pour 270 s allouables**, soit 52,8 s/minute
et 12 % de marge totale. L'admission rejoue 40 scénarios : cinq phases de compositions,
démarrage en jeu ou après prématch, coûts constants ou variables, puis finalisations
en deux cohortes ou de toutes les rencontres au même tour. Elle admet dix
et refuse onze. Un test standard relie les enveloppes aux maxima et au SHA de la preuve native.

Ces enveloppes constantes portent sur `STEADY_64_KIB_ONLY`. Elles ne couvrent pas tous les
pics initiaux de 5 Mio ; la vague initiale native est une preuve distincte. L'application
peut toujours recevoir des corps plus grands dans sa limite existante. Une latence durable
supérieure au profil produit les retards et arrêts prévus, sans ralentissement silencieux.

À titre de sensibilité, arrondir tous les maxima initiaux à 50 ms
(J4 750/1 100, incidents 750/500, statistiques et compositions 800/500 ms) donne une capacité
simulée de **sept**. Cela ne qualifie pas des corps récurrents de 5 Mio pendant trente minutes.

SHA-256 du profil : `5d34019578a5f1b616f3570aa47d8451afe283eb52dee73e1c94eeb4f370fcc1`.
Le [runbook](../runbooks/LIVE-J4-J5-CAMPAIGNS.md) fournit les variables explicites.
Aucune configuration existante n'est abaissée automatiquement ; seules les nouvelles
préparations utilisent le nouveau profil.

## Preuves et versions mesurées

- [Rapport natif intact avec échantillons](WO058-GROUPED-LIVE-V4-NATIVE-20260908.json),
  SHA-256 `51ab31c25b55a6cc5d70895ba0ad339f65b618c2373cadd2b4198ac70998e10a`.
- [Contrôle individuel](WO058-GROUPED-LIVE-V4-PER-EVENT-20260908.json), lié au même SHA natif.
- [Empreintes du runtime réellement mesuré](WO058-GROUPED-LIVE-V4-MEASURED-RUNTIME-20260908.json),
  capturées pendant le run avant tout `clean`.
- Logs, XML et copie indépendante : `.tmp/wo058-v4-sustained-native-proof/`.
  Les XML contenant des propriétés locales ne sont pas ajoutés au dépôt.

Le natif conserve la métadonnée historique `candidateCapacityAtInitialWaveBound=9`,
issue d'une borne provisoire facturant quatre familles chaque minute. L'admission finale
pondère cinq tours critiques et un tour de compositions. Les échantillons et résultats
natifs n'ont pas été réécrits pour corriger cette description. Le harness final calcule
désormais ce champ et porte les assertions individuelles déjà passées par le contrôle séparé.

Pendant le run, les sources d'interruption orpheline, échéances terminales, bascule vers la
réserve finale, présentation et admission ont été corrigées. Ces chemins étaient hors de
la boucle établie mesurée. Aucun binaire mesuré n'a été recompilé. Les tests intégrés et le
smoke final qualifient le code livré ; le run long reste lié aux empreintes ci-dessus.

## Vérifications intégrées

Commandes avec dépôt local explicite `C:/Users/geoff/.m2/repository`, Java 25 et Docker Desktop :

Le cache Chromium existant est `.tmp/provider-playwright-browsers`, résolu en chemin absolu
dans `PLAYWRIGHT_BROWSERS_PATH` et `provider.playwright.browser-cache`.
`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` est effectif. Les deux commandes Maven du run soutenu
ont été lancées séquentiellement, avec le répertoire Java 25 et le CLI Docker dans `PATH` :

```powershell
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository `
  -Pprovider-playwright-runtime,provider-playwright-local-qualification -DskipTests package
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository `
  -Pprovider-playwright-runtime,provider-playwright-local-qualification `
  -DskipTests=false -DskipITs=false -Dwo058.grouped.sustained=true `
  '-Dit.test=LiveGroupedCampaignLocalQualificationIT#tenMatchesRemainFreshForThirtyMinutesAfterFiveMinuteWarmup' `
  "-Dprovider.playwright.browser-cache=$env:PLAYWRIGHT_BROWSERS_PATH" `
  failsafe:integration-test@provider-playwright-loopback-qualification `
  failsafe:verify@provider-playwright-loopback-qualification
```

Le lanceur `scripts/Invoke-LivePlaywrightLoopbackQualification.ps1` exécute séparément
les 16 cas natifs historiques, UI et dégradation. Il reçoit les mêmes options hors ligne
par `MAVEN_ARGS`. Le nouveau lanceur `Invoke-LiveGroupedPlaywrightQualification.ps1`
rassemble smoke, mesure soutenue et affichage de dix matchs pour les réexécutions : lors
de cette qualification initiale, ces trois cas ont été sélectionnés dans des commandes
distinctes, et le run long n'a pas été refait uniquement pour regrouper leurs rapports.

| Commande | Résultat |
|---|---|
| `mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository clean verify` | PASS final à 12:23:20Z : 1 725 Surefire, 0 échec/erreur, 5 ignorés ; 144 Failsafe, 0 échec/erreur/ignoré |
| `mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Pintegration-tests verify` | PASS final à 12:29:12Z : 1 725 Surefire, 0 échec/erreur, 5 ignorés ; 144 Failsafe, 0 échec/erreur/ignoré |
| Qualification native historique, UI et dégradation | ÉCHEC : 16 tests, 15 réussis ; réponse initiale de 5 Mio du cas historique 25 matchs à 1,014846 s pour une borne de 1 s |
| Recontrôle identique des capacités historiques | ÉCHEC à 12:14:28Z : 6 tests, 3 réussis et 3 dépassements du même seuil, zéro erreur/ignoré ; aucune nouvelle relaxation |
| Affichage de dix matchs | PASS répété sur le package final : 1 test, zéro échec/erreur/ignoré ; publications rendues en 4,932 s et 4,938 s (premier passage 4,940 s / 4,880 s) |
| Smoke groupé final | PASS à 12:32:02Z : 1 test sans échec/erreur/ignoré, 120,015 s mesurées, 72 appels publiés, zéro cycle manqué, dix rencontres couvertes |
| Qualification groupée soutenue | PASS : 1 test, zéro échec/erreur/ignoré, 35 min 11 s |

La persistance comprend 32 tests live : V38 préremplie → V39, anciennes politiques,
réservations concurrentes, interruption orpheline et `pg_dump`/`pg_restore` des nouvelles
données dans des bases isolées. Les scripts J6 reconnaissent les quatre nouvelles tables.

Le test `LiveTenMatchRefreshBrowserQualificationIT` sert les assets de production sur HTTP
`127.0.0.1` : dix rencontres, trente familles critiques, 1 350 métriques et 300 incidents.
La première publication modifie les données des dix rencontres ; la seconde conserve
hashes/snapshots et contenu, mais avance les trente réceptions et leurs occurrences.
Les deux fenêtres de rendu respectent dix secondes, les âges restent frais et les nœuds de
contenu sont effectivement réutilisés. Cette preuve HTTP → rendu complète les lectures JDBC
du test long ; elle ne mesure pas une chaîne réseau SofaScore → écran.

Le test UI existant d'indisponibilité annule une lecture locale bloquée après dix secondes,
conserve les dernières données, puis constate la reprise sous dix secondes. Les deux cas
natifs de dégradation passent : arrêt opérateur après J4 sans départ de continuation, et
HTTP 404 retardé sans élargissement de sa portée. Leurs rapports sont conservés dans
`.tmp/wo058-v4-native-first-proof/`, avec l'échec historique, sans relever le seuil d'une seconde.
La borne échouée mesure le dispatch jusqu'à la réception validée, après la pause : ce n'est
pas un délai inter-endpoints inclus à tort. Le test utilise `supervisor.open(...)` historique,
sans groupe live-v4. Les preuves ne permettent pas d'attribuer le dépassement à une pause GC,
au CPU ou à une autre cause précise. La réexécution ne remplace pas la première mesure.

Le recontrôle des six paramètres conserve exactement le même code et le même seuil :
2/3/4 matchs passent ; les premières réponses J4 de 5 Mio des cas 5/10/25 prennent
respectivement **1,078146 / 1,253795 / 1,090070 secondes**. Cette enveloppe historique de
1 seconde n'est donc pas revalidée dans les conditions présentes. Le lanceur historique
reste en échec, ses assertions ne sont ni retirées ni relevées. Les vérifications natives
de sérialisation, arrêt, 404 et UI réussies sont distinguées de cette qualification de coût.
La preuve établie live-v4 de 64 Kio, la vague initiale du run long et cette nouvelle série
historique portent des conditions et résultats distincts ; **le bilan natif global n'est pas vert**.
Logs et XML de recontrôle : `.tmp/wo058-v4-native-historical-recheck.log` et
`.tmp/wo058-v4-native-historical-recheck.xml`. Aucun troisième essai de convenance n'est lancé.

Le premier `clean verify` a trouvé un profil synthétique incorrect dans
`LiveCampaignCleanupTest` : enveloppes conservatrices refusées avant la panne de lancement
simulée. Correction du test avec son profil synthétique explicite, sans changement de production
pour masquer l'erreur. Log conservé : `.tmp/wo058-v4-full-verify.log`.
Les essais confinés empêchés de lire un JAR Spring restent des échecs d'environnement,
distingués des réexécutions autorisées.

Le premier smoke final a aussi révélé que sa fenêtre de 70 secondes pouvait couper la première
vague de quarante corps de 5 Mio : 37 appels étaient publiés à l'arrêt, sans cycle manqué.
Sa durée est portée à 120 secondes pour vérifier tous les couples, les transactions et le
nettoyage. Ce smoke ne qualifie pas la minute. Les seuils de cadence du run soutenu et ses
fenêtres fixes de 300 + 1 800 secondes restent identiques ; le JSON d'échec court est conservé.
La réexécution finale passe sur 120,015 secondes : 72 tentatives/réponses publiées, zéro
cycle manqué et zéro requête hors périmètre ; pause inter-groupes minimale 3,06159 secondes.

Les cinq skips Surefire sont explicites : quatre tests de liens symboliques indisponibles
dans cet environnement Windows (deux `LocalJ7ExportFileStoreTest`, deux
`J8BenchmarkExportCommandTest`) et le test J6 natif optionnel
`dockerQualificationPassesThreeSequentialRunsAtEffectiveDefaults`, dont la propriété
`j6.docker.qualification` n'est pas activée. Les sauvegardes/restaurations PostgreSQL de V39
sont effectivement exercées par les tests d'intégration, sans aucun skip.

L'[index de vérification et du diff](WO058-GROUPED-LIVE-V4-VERIFICATION-20260908.json)
contient la liste des 64 fichiers modifiés/ajoutés, les empreintes des 48 sources et scripts,
ainsi que celles des logs et XML effectifs. Les hashes de sources sont calculés en UTF-8,
avec normalisation CRLF → LF ; les preuves JSON figées gardent leurs octets exacts via
`.gitattributes`. Le SHA de profil publié reste identique à celui validé par les tests.

Contrôles finaux : textes UTF-8 valides, liens locaux vérifiés, `git diff --check` sans erreur,
aucun motif de secret dans les ajouts, `server.address=127.0.0.1`, opt-ins fournisseur/live/
Playwright désactivés par défaut. Aucune ancienne migration, aucun `.env` ni PDF de référence
modifiés. Les scripts PowerShell se parsèment sans erreur. Relecture séparée du superviseur,
du scheduler, des budgets et de V39 : aucun défaut concret bloquant identifié ; elle ne
remplace pas la revue humaine ni ne transforme la qualification historique rouge en succès.
Revue humaine et fusion du WO restent distinctes. Une campagne opérateur mesurera ensuite
le décalage externe avec SofaScore ; aucune collecte réelle n'est déclenchée par la livraison.
