# WO-058 — Capacité live-v5 et opérabilité dynamique

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

**État : qualification synthétique soutenue et admission Java réussies pour vingt rencontres
à 100 secondes. `clean verify` et `-Pintegration-tests verify` réussis ; configuration
Eclipse sauvegardée et mise à jour. Revue humaine et observation fournisseur distinctes.**

## Décision et périmètre

Le propriétaire demande une campagne de quinze à vingt rencontres, des plafonds augmentés,
une seconde entre groupes et une collecte plus fréquente. Après explicitation des coûts de
la file séquentielle, il privilégie quinze à vingt rencontres avec une cadence effectivement
qualifiée. Le candidat retenu est de vingt rencontres à 100 secondes, avec les compositions
en jeu toutes les 300 secondes. Le besoin de visibilité sur cinquante à cent rencontres
à moyen terme est documenté séparément ; il ne relève pas le plafond opérationnel de ce lot.

Autorité : [ADR-SS-005 v0.6](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md).
Base : `06c7e3d272e8f96873eca822e4bdaa8302cf1b01`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`, worktree `.tmp/wo058-live-j4-j5`.

## Réalisation

- Profil et manifeste `live-v5` séparés des profils historiques : 100 s pour J4/incidents/statistiques,
  100 s pour les compositions avant match, puis 300 s en jeu, réparties sur trois tours.
  Initialisation des compositions dès leur admissibilité ; groupes séquentiels sans attente
  artificielle entre les familles autorisées du même groupe.
- Une seconde entre les groupes validés d'une même session v5. Une transition vers une autre
  session ou une autre autorité conserve le délai global historique ; réutiliser un UUID
  ne permet pas de réutiliser l'autorité d'une ancienne session.
- Plafonds indépendants de 2 500 appels par rencontre, 20 000 par campagne et quatre heures.
  Quatre appels restent réservés par rencontre active pour le contrôle et la finalisation.
  Plafond brut fixe de 15 728 640 000 octets ; espace disponible contrôlé à deux fois le
  budget brut restant plus la réserve locale de 1 Gio.
- V40 additive et contraintes par version : budgets, profil, cadence et capacité cohérents
  avec le manifeste. Les campagnes déjà préparées ou lancées conservent leurs paramètres.
  Sauvegarde/restauration et qualification des scripts J6 suivent le schéma V40.
- Interface de préparation et de suivi : cadence, plafond effectif, échéances, retards et
  autonomie sont explicites. La lecture locale reste à cinq secondes. Elle ne déclenche
  aucune collecte fournisseur.
- Compositions live et manuelles : correction du réordonnancement DOM ; voir la
  [preuve de visibilité](WO058-LINEUPS-VISIBILITY-20260908.md), qui distingue le signalement
  opérateur de la reproduction native voisine réellement établie.

## Essais déjà exécutés

| Essai | Résultat |
|---|---|
| Tests standards ciblés du candidat 75 s, scheduler, transport, configuration et interface | 308 tests, zéro échec, erreur ou ignoré |
| Tests standards ciblés du candidat 100 s | 308 tests, un attendu de frontière synthétique incorrect ; correction du test sans changement de production, puis ses sept cas réussis lors du packaging |
| Essai natif court à vingt rencontres, PostgreSQL et normalisation de production | 120 secondes, 91 réponses, zéro cycle manqué, aucun appel réel ; essai initial avant renforcement des assertions du harness long |
| Dégradations natives v5 | Deux cas réussis : arrêt opérateur entre familles, HTTP 404 retardé et poursuite autorisée ; nettoyage vérifié |
| Interface Chromium vingt rencontres, échéances 75 s | Une mesure de changement à 5 004 ms et une réception identique à 4 735 ms |
| Interface Chromium cent rencontres, échéances 75 s | Une mesure de changement à 5 590 ms et une réception identique à 4 484 ms |
| Interface Chromium vingt rencontres, échéances 100 s | Une mesure de changement à 4 935 ms et une réception identique à 4 955 ms |
| Interface Chromium cent rencontres, échéances 100 s | Une mesure de changement à 4 900 ms et une réception identique à 4 841 ms |
| Compositions Chromium | Deux scénarios réussis ; détails dans la preuve dédiée |
| Première vérification complète à 100 s, 18:34:44 UTC | 1 834 tests : huit échecs et vingt-cinq erreurs dans `LivePreparationAdmissionTest`, cinq ignorés ; les fixtures de cette classe préparaient encore le profil v4 et attendaient ses anciennes bornes |
| `clean verify` après correction des fixtures, 18:47:26 UTC | `BUILD SUCCESS`, 8 min 49 s : 1 840 tests Surefire, zéro échec/erreur et cinq ignorés ; 166 tests Failsafe, zéro échec/erreur/ignoré |
| `-Pintegration-tests verify`, 18:56:06 UTC | `BUILD SUCCESS`, 8 min 22 s : 1 840 tests Surefire, zéro échec/erreur et cinq ignorés ; 166 tests Failsafe, zéro échec/erreur/ignoré |

La [preuve d'affichage vingt/cent](WO058-DYNAMIC-UI-SCALE-20260908.md) décrit 300 familles
critiques, 13 500 métriques et 3 000 incidents à cent rencontres. Ce test court emploie les
assets de production avec un serveur de publication synthétique. Il ne couvre ni la collecte
de cent rencontres, ni le chemin MVC/PostgreSQL, ni les compositions, ni une endurance UI.

Les essais PostgreSQL ciblés couvrent la montée V39 vers V40 sur données préremplies,
la conservation exacte des lignes et empreintes historiques, les contraintes de profil,
la concurrence sur les dernières réservations ordinaires, la réserve finale et la restauration.
Le `clean verify` final inclut déjà les sept suites PostgreSQL ciblées par défaut, dont
73 tests Flyway et 54 tests de persistance live. Le profil `integration-tests` réexécute
ces sept suites avec succès ; il n'ajoute pas de tests à ce total dans ce POM.

La [revue statique](WO058-LIVE-V5-STATIC-REVIEW-20260908.md) inventorie le candidat avant
qualification finale. Elle vérifie notamment les défauts réseau, la liaison loopback,
l’UTF-8, le diff et les quatre fixtures inchangées depuis le commit de base.

## Premier candidat à 75 secondes : cadence réussie, admission à vingt refusée

La mesure native de 300 secondes d’initialisation puis 1 800,024 secondes établies termine
avec 1 835 appels, dont 1 560 établis, et zéro cycle manqué. Les quatre-vingts couples sont
contrôlés. Les intervalles critiques maximaux atteignent 75,508 s à réception et 75,5263 s
à publication ; le retard nominal P95 des compositions est de 2,1652 s et la pause minimale
entre groupes de 1,0689 s. Le pic initial de 80 réponses de 5 Mio est mesuré séparément.

Les enveloppes établies arrondies sont J4 400/600 ms, incidents 400/500 ms, statistiques
350/500 ms et compositions 300/400 ms (requête/traitement). Elles donnent 314 s de travail
pour vingt rencontres sur les 270 s allouables par période de cinq minutes. Le contrôle
Java de production renvoie `capacity=17` et `fits(20)=false`, code 2 attendu.
Le [profil candidat](WO058-GROUPED-LIVE-V5-CANDIDATE-75-PROFILE-20260908.json) reste
`PENDING_ADMISSION_REVIEW`, capacité qualifiée zéro. Il ne permet aucune activation à vingt.
Le passage à 100 s suit l’arbitrage du propriétaire en faveur de quinze à vingt rencontres.

## Qualification native soutenue à 100 secondes et profil

La mesure réussie comprend 300 secondes d'initialisation puis 1 800,010 secondes établies,
avec vingt rencontres, quatre familles, Chromium et PostgreSQL isolés. Les quatre-vingts
premières réponses font chacune 5 Mio ; les suivantes font 64 Kio. Délais serveur alternés
de 0/30/80/150 ms, 135 métriques statistiques, 270 signaux, 30 incidents et 44 joueurs.
Les contenus alternent changements et répétitions, avec deux états normalisés revisités
pour les statistiques et les compositions.

La réservation, le stockage brut, la normalisation, la publication SQL et la sonde Docker
d'espace libre utilisent les chemins de production. La lecture du ledger est exercée toutes
les cinq secondes. Les horodatages de réception et de disponibilité sont mesurés séparément.

La preuve exige les seuils par rencontre/famille, l'absence de cycles perdus et de dette
croissante, les échéances nominales de compositions espacées de 300 s et leur répartition
sur trois tours, la pause intergroupes et la clôture des processus. Les enveloppes finales
ont été arrondies au-dessus des maxima établis, sans abaisser les planchers mesurés du candidat
75 s, puis soumises à l'admission Java indépendante et à ses vingt-quatre scénarios : tous passent
pour vingt rencontres. Une réussite de cadence seule ne suffit pas à valider les enveloppes.

| Mesure établie | Résultat |
|---|---:|
| Appels totaux / établis | 1 413 / 1 200 |
| Couples rencontre/famille vérifiés | 80 |
| Cycles manqués / appels fournisseur réel | 0 / 0 |
| Intervalle critique à réception, P95 / maximum | 100,183 / 100,520 s |
| Intervalle critique à publication, P95 / maximum | 100,187 / 100,531 s |
| Retard nominal des compositions, P95 / maximum | 2,207 / 2,633 s |
| Retard de publication critique, P95 / maximum | 1,633 / 1,973 s |
| Pause observée minimale entre groupes | 1,066 s |
| Lectures JDBC locales, nombre / P95 | 420 / 65,981 ms |
| Corps reçus | 506 789 888 octets |

Les enveloppes retenues sont J4 **400/600 ms**, incidents **400/500 ms**, statistiques
**350/500 ms**, compositions **350/450 ms** (requête/traitement incluant SQL). Les compositions
augmentent par rapport au candidat 75 s. Vingt rencontres demandent ainsi **241 secondes sur
270 allouables** par période de cinq minutes ; les trente secondes de marge de 10 % restent
non allouées. La borne moyenne non plafonnée vaut 22, mais le plafond effectivement testé et
admis reste **20**.

L'initialisation est mesurée séparément : 80 premiers corps de 5 Mio, avec un retard maximal
de publication de 19,448 s. Les enveloppes établies couvrent le corpus de 64 Kio avec deux états
normalisés revisités. Elles ne qualifient ni des réponses récurrentes de 5 Mio, ni des états
continuellement nouveaux, ni une latence Internet ou un délai fournisseur-vers-écran.

Le test natif termine le 8 septembre à 18:28:32 UTC : un test, zéro échec, erreur ou ignoré,
`BUILD SUCCESS`, 35 min 17 s Maven. Fermeture du worker et de ses enfants vérifiée.
Le contrôle Java indépendant retourne `capacity=20` et `fits(20)=true`, code 0.

Preuves conservées : [natif](WO058-GROUPED-LIVE-V5-NATIVE-20260908.json),
[runtime mesuré](WO058-GROUPED-LIVE-V5-MEASURED-RUNTIME-20260908.json),
[quatre-vingts couples](WO058-GROUPED-LIVE-V5-PER-EVENT-20260908.json),
[profil qualifié](WO058-GROUPED-LIVE-V5-PROFILE-20260908.json).
SHA-256 du profil : `923c4499d2005d4d2f91499148f749dc7f80ea0868ff1cfc7a42106ac9863225`.
Les 608 fichiers sources/JAR et 959 fichiers compilés sont contrôlés avant tout nettoyage.

## Perspective de cinquante à cent rencontres

La [note de capacité](WO058-CAPACITY-OUTLOOK-20260908.md) présente les hypothèses, la borne
de débit moyen, les contraintes de calendrier et les paliers d'opérabilité. Les chiffres
prospectifs basés sur v4 restent des estimations. Le plafond d'appels augmente l'autonomie ;
le débit dépend du coût des échanges, des traitements et des pauses. Aucune collecte
concurrente ni nouvelle capacité supérieure à vingt n'est activée.

## Livraison et vérification finale

La [synthèse finale vérifiable](WO058-LIVE-V5-FINAL-VERIFICATION-20260908.json) consigne
les deux commandes complètes, leurs résultats, les raisons des cinq ignorés, les empreintes
des journaux et du profil, les 607 sources natives restées identiques et l'inventaire des
70 fichiers modifiés. Run final : `20260908T183834Z-14f76e8f805746f8916f65da8aca920b`.
Les rapports JUnit sont archivés par phase, datés dans leur fenêtre d'exécution et hachés.
Les sources de production, tests, fixtures et build sont contrôlées avant et après les suites.

Le 8 septembre à 19:01:46 UTC, neuf variables v5 du lanceur Eclipse live ont été renseignées
avec l'empreinte du profil et les huit enveloppes validées. La capacité configurée est de
vingt rencontres. Une sauvegarde exacte du fichier `.launch` a été créée et vérifiée ;
les autres paramètres, dont les valeurs historiques v4, sont conservés. Les chemins et
empreintes de sauvegarde et de relecture figurent dans la synthèse finale. Le premier
contrôle du helper local avait refusé la lecture des dates JSON automatiquement converties
par PowerShell ; conserver leurs chaînes ISO corrige cette lecture avant toute écriture.

Le checkout humain reste au commit de base, propre et distinct du worktree du WO.
Il faut intégrer le commit du lot après revue humaine puis relancer Eclipse pour charger
le nouveau code. Aucune application ni campagne n'a été démarrée, aucun appel fournisseur
réel n'a été émis et aucune donnée opérateur n'a été modifiée par cette validation.
Le WO reste en cours de revue ; aucune fusion ni publication Git n'est revendiquée.

Contrôles finaux : les 70 fichiers de l'inventaire sont décodables en UTF-8 strict,
aucune signature de secret recherchée n'est détectée et `git diff --check` réussit.
`server.address=127.0.0.1` reste effectif, les activations fournisseur restent désactivées
par défaut et aucun nouveau transport réel n'est ajouté aux tests standards.
Le port 8087 est libre lors du contrôle final.

La première vérification complète est conservée dans
`.tmp/wo058-v5-final-20260908T183155Z-409d3e1a103b4723bd07d5b13843d689/standard/`.
Le test de preuve v5 réussit dans cette exécution. La correction des fixtures de préparation
ne change ni les sources de production mesurées, ni les enveloppes, ni les preuves natives.
Les cinq ignorés comprennent quatre cas de liens symboliques non disponibles sous cet
environnement Windows et un essai Docker J6 activé uniquement par sa propriété dédiée.

Les commandes effectives utilisent Java `25.0.4`, le wrapper Maven, `--offline` et
`-Dmaven.repo.local=C:/Users/geoff/.m2/repository`. L’accès hors sandbox permet la lecture
du cache existant et les sockets Docker locaux ; aucun appel réel SofaScore n’est autorisé.

```powershell
# Deux validations complètes, réussies sur les mêmes sources.
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository clean verify
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Pintegration-tests verify

# Compilation / tests ciblés puis JAR natif ; les profils natifs n'exécutent ici aucun navigateur.
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository `
  -Pprovider-playwright-runtime,provider-playwright-local-qualification `
  -Dtest=GroupedLiveAdmissionPolicyV5Test -DskipTests=false -DskipITs=true package

# Exécution soutenue ; le nom historique de la méthode ne détermine pas le nombre de matchs.
# wo058.grouped.v5=true configure effectivement vingt rencontres à 100 secondes.
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository `
  -Pprovider-playwright-runtime,provider-playwright-local-qualification `
  -DskipTests=false -DskipITs=false -Dwo058.grouped.v5=true -Dwo058.grouped.sustained=true `
  '-Dit.test=LiveGroupedCampaignLocalQualificationIT#tenMatchesRemainFreshForThirtyMinutesAfterFiveMinuteWarmup' `
  -Dprovider.playwright.browser-cache=.tmp/provider-playwright-browsers `
  failsafe:integration-test@provider-playwright-loopback-qualification `
  failsafe:verify@provider-playwright-loopback-qualification

# Deux charges UI exécutées séparément avec la même classe :
# -Dit.test=LiveTenMatchRefreshBrowserQualificationIT -Dwo058.ui.matches=20 puis =100
# -Dwo058.grouped.v5=true, mêmes profils et mêmes objectifs Failsafe explicites.
```
