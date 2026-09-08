# WO-058 — Utiliser une campagne live locale bornée

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Références : [ADR-SS-005 accepté](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md),
[architecture](../architecture/LIVE-J4-J5-CAMPAIGNS.md),
[qualification](../validation/WO058-LIVE-J4-J5-IMPLEMENTATION-20260907.md).

## Nouvelles préparations : live-v4

Les nouvelles préparations utilisent une cible de **60 secondes par rencontre pour J4,
incidents et statistiques**. LINEUPS est initial puis nominalement toutes les cinq minutes
pendant le jeu, avec répartition entre les matchs ; avant le début confirmé, J4 et LINEUPS
restent à une minute. Les appels d’un même groupe sont séquentiels sans pause ajoutée, puis
trois secondes séparent les groupes. Les préparations v1–v3 conservent leur ancienne cadence.

Un ancien SHA de qualification et les propriétés historiques `REQUEST_ENVELOPE` /
`PROCESSING_ENVELOPE` **ne qualifient pas v4**. Les défauts ne sont pas abaissés. Une preuve de
groupes absente affiche une capacité nulle et empêche les nouvelles préparations éligibles.
Configurer explicitement un profil issu du [rapport v4](../validation/WO058-GROUPED-LIVE-V4-20260908.md)
dans le lanceur Eclipse, puis redémarrer et préparer un nouveau manifeste. Aucun changement de
configuration ne remplace le manifeste d’une campagne déjà préparée.

| Variable du lanceur | Valeur à reprendre de la qualification retenue |
|---|---|
| `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY` | Plafond opérateur, limité en plus par l’admission temporelle |
| `SOFASCORE_LIVE_GROUPED_QUALIFICATION_SHA256` | SHA-256 exact de la preuve dédiée de groupes |
| `SOFASCORE_LIVE_GROUPED_J4_REQUEST_ENVELOPE` / `...J4_PROCESSING_ENVELOPE` | Coût J4 et traitement local, unités explicites `ms` ou `s` |
| `SOFASCORE_LIVE_GROUPED_INCIDENTS_REQUEST_ENVELOPE` / `...INCIDENTS_PROCESSING_ENVELOPE` | Coûts incidents qualifiés |
| `SOFASCORE_LIVE_GROUPED_STATISTICS_REQUEST_ENVELOPE` / `...STATISTICS_PROCESSING_ENVELOPE` | Coûts statistiques qualifiés |
| `SOFASCORE_LIVE_GROUPED_LINEUPS_REQUEST_ENVELOPE` / `...LINEUPS_PROCESSING_ENVELOPE` | Coûts compositions qualifiés |

Les trois opt-ins existants et le contrôle d’espace PostgreSQL restent requis. Ne pas augmenter
le timeout Playwright de dix secondes. Le plafond configuré ne suffit pas : l’admission conserve
10 % de marge, additionne les coûts de groupe et vérifie les transitions. Une sélection trop grande
affiche sa capacité admissible ; elle ne remplace jamais les 60 secondes par une cadence plus lente.
La portée des corps, latences et environnements qualifiés doit accompagner les enveloppes ; une
mesure loopback ne prouve ni la latence Internet ni un quota accepté par SofaScore.

Le [profil mesuré le 08/09](../validation/WO058-GROUPED-LIVE-V4-PROFILE-20260908.json)
admet **10 rencontres**. Pour l'utiliser explicitement dans le lanceur :

```text
SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY=10
SOFASCORE_LIVE_GROUPED_QUALIFICATION_SHA256=5d34019578a5f1b616f3570aa47d8451afe283eb52dee73e1c94eeb4f370fcc1
SOFASCORE_LIVE_GROUPED_J4_REQUEST_ENVELOPE=300ms
SOFASCORE_LIVE_GROUPED_J4_PROCESSING_ENVELOPE=400ms
SOFASCORE_LIVE_GROUPED_INCIDENTS_REQUEST_ENVELOPE=350ms
SOFASCORE_LIVE_GROUPED_INCIDENTS_PROCESSING_ENVELOPE=450ms
SOFASCORE_LIVE_GROUPED_STATISTICS_REQUEST_ENVELOPE=300ms
SOFASCORE_LIVE_GROUPED_STATISTICS_PROCESSING_ENVELOPE=350ms
SOFASCORE_LIVE_GROUPED_LINEUPS_REQUEST_ENVELOPE=300ms
SOFASCORE_LIVE_GROUPED_LINEUPS_PROCESSING_ENVELOPE=350ms
```

Ces enveloppes contiennent les maxima observés pendant trente minutes de régime établi :
réponses synthétiques de 64 Kio, délais serveur de 0/30/80/150 ms, normalisation, PostgreSQL et
sonde Docker compris. Elles **ne bornent pas les coûts de démarrage de 5 Mio** : cette vague
initiale de quarante réponses a été mesurée séparément et a aussi passé les critères. Une
succession durable de corps de 5 Mio ou une latence Internet plus élevée n'est pas qualifiée
par ce profil. Le dépassement provoque un retard visible puis les arrêts prévus s'il dure ;
il ne remplace pas la minute par un intervalle plus long. La preuve conserve cette distinction.
Les paramètres historiques ne sont ni supprimés ni abaissés automatiquement.

### Si les cases `notstarted` sont désactivées

Une capacité affichée à zéro avec l’avertissement de qualification indique que le lanceur
n’a pas chargé un profil groupé complet et admissible. Rafraîchir J3 ou J4 ne change pas cette
configuration. Vérifier les neuf variables `SOFASCORE_LIVE_GROUPED_*` ci-dessus : ajouter
seulement le SHA ne suffit pas si les enveloppes restent à leurs défauts conservateurs.
Le plafond opérateur et la capacité temporelle sont combinés ; avec le profil ci-dessus,
un plafond opérateur historique de 20 reste limité à **10** pour `live-v4`.

Après modification du lanceur sur disque, fermer puis rouvrir Eclipse pour garantir qu’il
relise sa configuration, puis relancer l’application avec le lanceur live. La page doit
annoncer dix rencontres qualifiées et permettre la sélection des rencontres admissibles.
Préparer ensuite une nouvelle campagne : les manifestes déjà préparés restent figés.

### Annuler une préparation

Sur la page d’une campagne encore `PREPARED`, **Annuler la préparation** termine cette
préparation sans appel fournisseur. La campagne reste consultable avec le motif
`PREPARATION_CANCELLED`, et son lancement est désormais impossible. L’annulation peut être
répétée sans effet supplémentaire ; si le lancement a déjà gagné la course, utiliser l’arrêt
de campagne. Les observations et le manifeste restent conservés.

### Délai des collectes J5 manuelles

Une collecte J5 manuelle confirmée enchaîne statistiques, incidents et compositions pour le
même événement sans pause artificielle entre familles. Les appels restent séquentiels.
Trois secondes séparent deux collectes distinctes ainsi que leurs transitions avec les
campagnes live et les autres parcours ; une interruption ne supprime pas cette protection.
La collecte manuelle reste ponctuelle et ne dépend pas du profil de capacité live-v4.

L’écran indique l’autonomie estimée avec les appels restants et réserves, limitée aussi par la
fenêtre et les budgets individuels. À dix matchs tous en jeu, environ 32 appels/minute consomment
3 000 appels en quelque 94 minutes avant ajustement des phases et réserves. Les fins de match
libèrent des créneaux ; elles n’allongent pas la minute des autres. Chaque famille expose dernière
réception, dernier changement, prochaine collecte et retard. Un résultat inchangé nouvellement
reçu reste frais. Une lecture locale bloquée est annulée après dix secondes et réessayée cinq
secondes plus tard, sans perte de sélection ni d’état des panneaux.

### Migration V38 → V39

Avant upgrade d’une base opérateur V38, sauvegarder avec l’outillage J6 du commit `c972d63`
dans un checkout distinct et vérifier la restauration isolée V38. Les scripts courants sont V39
et fingerprintent aussi politique, groupes et échéances ; ne pas falsifier le manifeste V38
pour franchir leur garde. La qualification Testcontainers ne migre jamais la base opérateur.
V39 est append-only et conserve les empreintes des anciennes préparations et observations.

Les sections historiques ci-dessous restent utiles pour v1–v3 ; leurs valeurs D et profils de
capacité ne doivent pas être employés pour annoncer une capacité v4 à une minute.

## Qualification hors fournisseur

La réalisation du WO utilise uniquement des données synthétiques et des bases Testcontainers.
Ces commandes sont lancées depuis le worktree du WO avec Java 25 et Docker Desktop disponibles :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Install-J3PlaywrightRuntime.ps1
.\scripts\Invoke-LivePlaywrightLoopbackQualification.ps1
.\scripts\Invoke-LiveGroupedPlaywrightQualification.ps1
```

L'installation explicite utilise le cache Chromium dédié `.tmp/provider-playwright-browsers`.
Les lanceurs construisent le worker et exécutent uniquement les qualifications de transport
et de vue live sur HTTP loopback. Les tests standards n'ouvrent pas de navigateur. Le test J6
requiert le port 8087 libre ; une application de l'opérateur ne doit pas être arrêtée implicitement.
Lire les XML Surefire/Failsafe effectifs, les skips et le résultat du lanceur. Un packaging avec
`-DskipTests` n'est pas une qualification.

Le lanceur groupé exige en plus Docker Desktop et `docker.exe` disponible, exécute un essai court
puis cinq minutes d’initialisation et au moins trente minutes mesurées sur PostgreSQL isolé,
ainsi qu'un contrôle Chromium des publications dans dix panneaux de rencontre.
Si le CLI Docker n’est pas dans `PATH`, passer son chemin installé avec `-DockerExecutablePath`.
Il contrôle l’âge des rapports, l’absence de tests ignorés et la durée réelle ; un essai court
seul ne prouve pas la capacité soutenue. Le rapport JSON conserve les mesures par famille.
La réussite de cadence et l’adéquation des enveloppes candidates sont des résultats distincts.

Le replay local est une API Java `LiveReplayRunner.run(ReplayInput)`, utilisée dans ses tests.
L'entrée contient identifiant de fixture, heure initiale, durée, sélection, réponses avec SHA-256,
temps simulé de chaque réponse, arrêts et plafonds. Le résultat porte `SYNTHETIC_REPLAY`, son
empreinte de manifeste, les traces de familles et états finaux. `complete=false` signale notamment
un script de réponses épuisé. Aucun replay ne lance Playwright ni ne modifie la base.

## Préparer l'application de l'opérateur

Pour une base déjà en V36, la sauvegarde/restauration préalable emploie les scripts V36 du
commit `b4e85d613004e74d3a071e8af7c56ef86e74359e`, puis les scripts courants V37 après upgrade.
Ne pas falsifier la version d'un manifeste pour satisfaire les gardes.

L'application de V35/V36/V37 à la base utilisée par l'opérateur est une opération distincte de ces tests.
Préparer d'abord la sauvegarde, les empreintes et la restauration isolée selon
[J6](J6-BACKUP-RESTORE-AND-RETENTION.md), avec le conteneur et la base exacts. Ne pas faire pointer
une simple validation Spring/Flyway vers la base de l'opérateur pour obtenir un test vert.

Pour une base opérateur en V34, utiliser pour la sauvegarde préalable l'outillage J6 et ses
dépendances du commit `e98f7a74e39a1c57e601efb3d346ae55829fce73`, dans un checkout distinct.
Valider une restauration isolée V34 avant l'upgrade ; utiliser ensuite les outils courants V37
pour la nouvelle preuve. V35 ajoute la contrainte de cadence `live-v3` sans réécrire les anciens
manifestes ; V36 autorise le parseur incidents V16 (`inGamePenalty/awarded`), puis V37 ajoute V17 (`Professional handball`). Les essais ci-dessous
n'ont pas appliqué ces migrations à la base de l'opérateur.

Procédure historique V33 vers V34 : utiliser pour la sauvegarde préalable l'outillage J6 et ses
dépendances figés au commit `a7f544cc2f70db2066836107d1e7a21c4feccb4c`, dans un checkout distinct,
avec manifeste et cible exacts préparés pour cette opération. Vérifier cette sauvegarde par
restauration isolée V33 avant d'appliquer V34. Après migration, utiliser l'outillage J6 historique
V34 pour la nouvelle preuve. V34 élargit le plafond et ajoute la cadence ; les anciens manifestes
gardent leurs SHA et 60 s. Ne pas modifier la version Flyway déclarée ni mélanger les scripts
V33/V34 pour franchir leur garde. Ces opérations sur la base opérateur n'ont pas été exécutées
dans le présent correctif. Pour une base encore en V32, la procédure historique de sauvegarde
au commit `6dfd14286d4f269cbe100bd965257c20298538db` reste nécessaire avant son upgrade.

Une fois cette préparation opérationnelle effectuée, l'application locale utilise les propriétés
suivantes. Le lot ne modifie aucun fichier `.env` et conserve les défauts désactivés.

| Propriété | Valeur ou exigence |
|---|---|
| `server.address` | `127.0.0.1` |
| `sofascore.enabled` | Opt-in fournisseur explicite |
| `sofascore.playwright.enabled` | Opt-in transport explicite |
| `sofascore.live.enabled` | Opt-in live explicite, défaut false |
| `sofascore.playwright.worker-jar` | JAR worker exact construit par le profil runtime |
| `PLAYWRIGHT_BROWSERS_PATH` | Cache dédié installé explicitement |
| `sofascore.live.docker-executable` | Chemin absolu du Docker CLI local |
| `sofascore.live.postgres-container` | Nom exact du conteneur PostgreSQL dont le volume sera mesuré |
| `sofascore.live.qualified-match-capacity` | Maximum de rencontres éligibles par campagne, défaut 1 ; peut être réglé à 5, 10, 25, etc. |
| `sofascore.live.duration` | Au plus 4 h, attente avant coup d'envoi comprise |
| `sofascore.live.request-envelope` / `processing-envelope` | 10 s / 1 s, hypothèses d'admission initiales |
| `sofascore.live.qualification-sha256` | Preuve revue obligatoire pour abaisser l'enveloppe ou retenir effectivement plusieurs matchs |

L'exécutable Docker et le nom de conteneur servent à lire l'espace du volume PostgreSQL réel,
avec timeout et refus fermé si la mesure échoue. Le pilote à un match réserve 5 242 880 000
octets de réponses possibles ; la marge d'espace exige deux fois cette enveloppe plus 1 Gio.
Les plafonds de 1 000/3 000 tentatives sont locaux et ne décrivent pas un quota fournisseur connu.
Les anciennes propriétés `automatic-refresh-enabled` et `live-polling-enabled` restent désactivées.

### Paramètres du lanceur Eclipse pour le contrôle de stockage

Dans **External Tools Configurations → Environment**, renseigner les variables explicitement
reliées par `application.yml` :

| Variable | Valeur |
|---|---|
| `SOFASCORE_LIVE_DOCKER_EXECUTABLE` | Chemin absolu de `docker.exe`, sans ajouter de guillemets dans la valeur Eclipse ; l'obtenir avec `(Get-Command docker).Source` dans PowerShell |
| `SOFASCORE_LIVE_POSTGRES_CONTAINER` | `betting-sofascore-local-lab-postgres` pour le conteneur de `compose.yaml`, ou le nom exact du conteneur utilisé |

Le chemin reste non configuré par défaut ; aucun exécutable n'est recherché ni lancé automatiquement
au démarrage du Lab. La préparation mesure l'espace uniquement lorsqu'une sélection contient
au moins une rencontre éligible. Le nom de conteneur possède déjà le défaut ci-dessus, mais le
chemin Docker doit être fourni. Après modification du lanceur, redémarrer l'application et
recharger `/events`. Les opt-ins fournisseur ne remplacent pas cette configuration locale.

Un refus de préparation affiche désormais un code de contrôle connu et une action adaptée :
chemin manquant (`LIVE_STORAGE_PROBE_NOT_CONFIGURED`), mesure impossible, délai dépassé,
espace insuffisant ou limites non qualifiées. Les messages n'exposent jamais le contenu arbitraire
d'une exception. Une préparation réussie affiche le manifeste à confirmer ; elle ne lance pas
Playwright et ne rafraîchit pas J4.

Un ancien snapshot `notstarted` décrit l'état reçu à sa date, même si le match est maintenant
terminé. La préparation ne déduit pas `finished` à partir du calendrier. Le rafraîchissement J4
manuel peut mettre à jour cette observation ; la prochaine préparation exclura alors le match.
Pour une campagne explicitement lancée avec une observation ancienne encore éligible, le premier
J4 réseau établit le statut et conserve les transitions/finalisation bornées de l'ADR.

### Profils historiques des paliers deux et trois

Le pilote sur une rencontre découvert `finished` au premier J4 a été confirmé par l'opérateur.
Les profils suivants disposent désormais d'une [qualification locale](../validation/WO058-MULTIMATCH-CAPACITY-20260907.md)
et d'une [preuve figée](../validation/WO058-MULTIMATCH-CAPACITY-PROFILE-20260907.json).
Le défaut reste une rencontre ; les valeurs ci-dessous permettent le passage explicite aux
paliers initiaux de l'ADR v0.1. Ces profils et leur empreinte restent des preuves historiques ;
le plafond paramétrable et la nouvelle cadence sont décrits dans la section suivante.

Dans le lanceur Eclipse, ajouter les quatre variables ensemble :

| Variable | Palier deux | Palier trois |
|---|---|---|
| `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY` | `2` | `3` |
| `SOFASCORE_LIVE_REQUEST_ENVELOPE` | `3000ms` | `750ms` |
| `SOFASCORE_LIVE_PROCESSING_ENVELOPE` | `1000ms` | `1000ms` |
| `SOFASCORE_LIVE_QUALIFICATION_SHA256` | Empreinte ci-dessous | Même empreinte |

Empreinte SHA-256 du fichier de preuve, à copier sans guillemets :

```text
0f1ae6ad44190ae965bec3ad670ffb3c45a140a29856d217f28e71b5db262679
```

La vérifier depuis le checkout qui contient ce correctif :

```powershell
(Get-FileHash -LiteralPath docs/validation/WO058-MULTIMATCH-CAPACITY-PROFILE-20260907.json -Algorithm SHA256).Hash.ToLowerInvariant()
```

Le fichier est protégé des conversions de fins de ligne Git. Une empreinte de forme correcte
ne remplace pas une preuve : ne pas inventer sa valeur. Redémarrer l'application après changement,
recharger `/events` et préparer un nouveau manifeste. Les paramètres Docker et les opt-ins déjà
configurés restent nécessaires. Une préparation antérieure ne peut pas adopter un nouveau profil.

Le palier deux modélise 56 s par minute ; le palier trois, 57 s, avec les quatre familles et le
délai global de trois secondes. L'enveloppe de requête sert au calcul d'admission ; le timeout
transport reste au maximum dix secondes. Les mesures loopback ne garantissent pas la latence
du fournisseur. Les retards restent visibles et deux cycles successifs manqués arrêtent la campagne.
Les budgets, la réserve de clôture et le contrôle du volume PostgreSQL restent applicables.

Une sélection de deux ou trois rencontres éligibles atteint alors le récapitulatif du manifeste
si le profil et le stockage sont admissibles. Les rencontres déjà `finished` sont exclues avant
le calcul : par exemple deux `notstarted` et un `finished` préparent deux cibles au palier deux.
Le clic de confirmation lance une seule campagne sur les cibles retenues.

### Plafond paramétrable et cadence adaptative (ADR-SS-005 v0.2)

`SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY` désigne désormais le plafond des seules rencontres
éligibles dans une campagne. Avec la valeur 25, une sélection de 1 à 25 cibles est permise,
sous réserve du profil temporel et de l'espace disque. Le défaut reste 1. Les `finished`
ne comptent pas ; les rencontres déjà suivies sont verrouillées, sauf `STOPPED_ERROR`.
La limite technique de lecture reste 100 identifiants par formulaire, exclus compris.

La cadence est calculée après exclusion des `finished` puis figée au manifeste :

| Cibles retenues N | Intervalle D | Charge de quatre familles, profil 1 s + 1 s + 3 s |
|---|---|---|
| 1–3 | 60 s | 20–60 s |
| 4 | 90 s | 80 s |
| 5 | 120 s | 100 s |
| 10 | 270 s (4 min 30 s) | 200 s |
| 25 | 720 s (12 min) | 500 s |

La règle générale est `D = max(60, 30 × (N − 1))` secondes. Un plafond à 25 avec trois
cibles garde 60 s. J4 en attente/surveillance de fin et les trois familles J5 utilisent D ;
le secours J4 utilise `max(300 s, D)`. Une indisponibilité J5 HTTP 404 conserve la prochaine
échéance normale. L'actualisation de l'écran reste une lecture locale toutes les cinq secondes.

Pour le profil testé avec les valeurs 5, 10 et 25, conserver `SOFASCORE_LIVE_REQUEST_ENVELOPE=1000ms`
et `SOFASCORE_LIVE_PROCESSING_ENVELOPE=1000ms`. La nouvelle
[preuve JSON](../validation/WO058-ADAPTIVE-CAPACITY-PROFILE-20260907.json) et les mesures du
[rapport de capacité adaptative](../validation/WO058-ADAPTIVE-CAPACITY-20260907.md) correspondent
à cette empreinte à renseigner dans `SOFASCORE_LIVE_QUALIFICATION_SHA256` :

```text
99bffa13e057a07fd9493d26e8a186ffa25b2262f03f652da2869347850fceea
```

Vérification de l'empreinte dans le checkout du correctif :

```powershell
(Get-FileHash -LiteralPath docs/validation/WO058-ADAPTIVE-CAPACITY-PROFILE-20260907.json -Algorithm SHA256).Hash.ToLowerInvariant()
```

Les anciens profils 2/3 restent admissibles pour leurs sélections ; une simple forme SHA valide
ne qualifie pas un débit. Les tests Chromium sont locaux, les latences réelles restent surveillées.

J4 affiche le plafond et le nombre éligible. À ce plafond, les autres cases éligibles sont
désactivées ; décocher une cible permet d'en choisir une autre. Une case `finished` reste
sélectionnable pour obtenir l'explication d'exclusion. Si un changement local rend la sélection
trop grande, il faut la réduire avant préparation ; le serveur contrôle toujours le décompte.
Redémarrer l'application sur le correctif et recharger `/events`, puis préparer un nouveau
manifeste. Le changement de plafond ou de profil ne modifie jamais une campagne déjà préparée
ou démarrée ; une nouvelle préparation est requise pour utiliser la nouvelle configuration.

## Parcours depuis /events

Une seule campagne fournisseur peut être active globalement, même si une autre sélection
contient des rencontres entièrement différentes. `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY`
limite les rencontres de chaque campagne ; il n'augmente pas le nombre de campagnes simultanées.
Préparer une autre sélection reste possible si ses rencontres sont éligibles, mais son lancement
renvoie `LIVE_PROVIDER_BUSY` tant que la session fournisseur est occupée. Attendre la fin ou
l'arrêt et le nettoyage de la campagne active. Refaire ensuite la préparation si sa validité
de cinq minutes a expiré. Ce comportement a été observé lors du
[retour opérateur à huit puis seize rencontres](../validation/WO058-OPERATOR-EIGHT-SIXTEEN-20260907.md).

Une rencontre déjà dans une campagne en cours a sa case désactivée sur J4. L'actualisation
locale retire aussi une sélection devenue indisponible. L'exception `STOPPED_ERROR` permet
de la sélectionner de nouveau, sans la recocher automatiquement. Un formulaire ancien ou
forgé est refusé sous `LIVE_EVENT_ALREADY_IN_CAMPAIGN` ; suivre la campagne existante ou retirer
la rencontre. Une préparation seule (`PREPARED`) ne verrouille pas la sélection.

Une rencontre déjà `finished` dans la dernière observation locale est exclue, avant tout contrôle
de capacité ou appel fournisseur. Si toute la sélection est terminée, la page l'explique et aucune
campagne n'est créée. Une sélection mixte indique les exclusions avant confirmation du manifeste.
Les statuts sont revérifiés au lancement pour ne pas appeler une rencontre devenue terminée.

Après mise à jour de ce correctif et redémarrage de l'application dans Eclipse, recharger `/events`
pour recevoir la nouvelle politique de formulaire et un jeton local neuf. Le refus 403 observé avec
l'ancienne page `no-referrer` n'est pas corrigé par l'activation de `SOFASCORE_PLAYWRIGHT_ENABLED`.
La préparation et l'exclusion des matchs terminés ne nécessitent aucun opt-in réseau. Les trois
opt-ins restent requis pour lancer les rencontres éligibles. Utiliser une même adresse
(`localhost:8087` ou `127.0.0.1:8087`) durant tout le parcours.

1. Dans les résultats normalisés, sélectionner les rencontres éligibles puis préparer la campagne.
   Le serveur vérifie UUID, ID fournisseur et snapshot source ; aucune requête fournisseur ne part.
2. Relire la sélection figée, les heures, la fenêtre, les plafonds, le profil de capacité et
   l'empreinte du manifeste. La préparation expire après cinq minutes ; toute modification de
   politique impose une nouvelle préparation.
3. Confirmer explicitement le lancement. Cette action autorise cette seule session et ses cycles.
   Le navigateur est créé une fois ; un double clic ou un formulaire rejoué ne crée pas une seconde session.
4. Observer séparément statut sportif J4, date du score, état de collecte, fraîcheur de chaque
   famille, prochaine échéance, retard, budget et complétude. Les données J5 d'un même cycle
   proviennent de trois instants distincts.
5. Utiliser « Arrêter » pour un match ou pour la campagne. L'arrêt individuel laisse terminer
   son éventuel GET engagé ; l'arrêt global annule le transport partagé. Attendre la preuve de
   nettoyage avant un nouveau lancement.

J4 est interrogé dès le lancement puis à l'intervalle D du manifeste tant que `notstarted`.
Les nouvelles préparations `live-v3` collectent alors LINEUPS seul : une première réception dès
la place admissible, puis un rafraîchissement à D jusqu'au début constaté. Statistiques et incidents
commencent après J4 `inprogress`. Le premier triplet peut attendre l'éligibilité d'une LINEUPS
récente (jusqu'à D, puis attente de service), afin de respecter l'espacement de cette famille.
Les préparations historiques `live-v1`/`live-v2` restent J4 seules avant le début. Un redémarrage
ne convertit pas leur politique ; préparer un nouveau manifeste pour utiliser `live-v3`.
Les incidents déclenchent des contrôles, jamais une conclusion sportive.
Le secours J4 intervient après `max(300 s, D)` sans J4 réussi ; seule sa réponse `finished` confirme
la fin. Un dernier triplet J5 peut rester incomplet si la fenêtre, le budget ou l'indisponibilité
d'une famille l'empêche. Un 404 J5 est rééchantillonné au cycle normal suivant.

Un HTTP 404 de statistiques, incidents ou compositions s'affiche comme `ENDPOINT_UNAVAILABLE`
avec une complétude `UNAVAILABLE`. Il ne bloque pas la campagne : les autres familles et
rencontres continuent, et chaque endpoint indisponible est réinterrogé comme planifié.
La dernière donnée lisible reste visible avec sa propre date. Ce cas est distinct d'un HTTP 404
J4 qui demande la revue du seul match, et d'une véritable panne technique ou de stockage.

Après installation du correctif du [retour du 7 septembre](../validation/WO058-J5-404-AND-SELECTION-20260907.md),
redémarrer le Lab et recharger J4 pour recevoir le nouveau HTML/script. Une campagne arrêtée
reste un historique : sélectionner les rencontres en `STOPPED_ERROR`, préparer un nouveau
manifeste et le lancer manuellement. Vérifier que le garde a terminé son nettoyage ; aucune
reprise automatique ni réécriture des tentatives historiques n'est effectuée.

Le rafraîchissement de l'écran est une lecture locale toutes les cinq secondes. Masquer l'onglet
suspend ces lectures, sans interrompre une campagne déjà lancée dans l'application. Fermer le
processus de l'application, perdre le worker ou mettre le poste en veille termine la session.

## Arrêt et reprise fermée

Un schéma métier incompatible admissible arrête le seul match. Un refus HTTP hors 404, timeout,
contenu inattendu, défaut d'identité, exception interne ou erreur de stockage arrête globalement.
La dernière donnée acquise reste visible avec sa date ; aucune erreur n'est transformée en zéro.

Avec le correctif de clôture, si PostgreSQL devient indisponible alors que le processus applicatif
reste vivant, la page indique séparément que la collecte a cessé et que sa clôture reste à
enregistrer. Après rétablissement de PostgreSQL, utiliser **« Finaliser la clôture locale »**.
Cette commande ferme et vérifie les ressources, résout les seules tentatives sans résultat,
enregistre les états terminaux et libère le garde ; elle n'effectue aucun appel fournisseur.
Le bouton est désactivé pendant cette opération. Un nouvel échec laisse la clôture disponible
pour une autre commande explicite ; aucun réessai périodique n'est déclenché.

Une fois la clôture confirmée, revenir aux rencontres, préparer les cibles encore éligibles et
confirmer un nouveau lancement. Un redémarrage entre-temps relève du cas d'orphelin ci-dessous :
le nouveau processus ne possède pas le lease de l'ancien et ne peut pas utiliser cette clôture
runtime. Les codes `LIVE_PROVIDER_CLEANUP_REQUIRED` et `LIVE_LAUNCH_FAILED` distinguent le garde
à vérifier d'un autre échec local de lancement, sans exposer le texte arbitraire des exceptions.

Après crash ou nettoyage incertain, `CLEANUP_REQUIRED` conserve l'exclusion fournisseur. Aucun
délai, redémarrage ou nouvelle préparation ne la lève. Consulter les preuves de campagne et faire
vérifier l'identité et la disparition de l'arbre de processus avant toute intervention locale
sur ce garde. Ce lot ne fournit pas de bouton de libération aveugle ; aucun GET n'est rejoué.
Un processus propriétaire encore actif ou d'identité inaccessible demeure protégé.

Une mise à jour de Docker Desktop peut interrompre PostgreSQL et donc arrêter la collecte
avant que son état terminal puisse être enregistré. Le retour de PostgreSQL ne relance aucun
appel fournisseur. Le redémarrage du 7 septembre a marqué `INTERRUPTED / OWNER_PROCESS_ABSENT`,
mais a conservé le garde `CLEANUP_REQUIRED` : les sept matchs étaient préparables, leur lancement
restait refusé. La libération ponctuelle de ce garde a nécessité une preuve locale de disparition
du propriétaire et des composants Playwright, puis une transaction sur son identité/génération
exactes. Ce contrôle ne doit jamais être remplacé par une remise à zéro inconditionnelle.
Le [rapport de l'incident](../validation/WO058-PREMATCH-TEMPORAL-20260907.md) conserve avant/après
et le lancement opérateur qui a suivi ; aucune campagne historique n'a été reprise ou réécrite.

`LIVE_RAW_PREVIOUSLY_PURGED` signifie qu'une réception identique rencontre une ancienne preuve
dont J6 a purgé le corps. La réception nouvelle est annulée atomiquement ; la campagne s'arrête
pour stockage. Une réhydratation ou une politique de rétention différente exige un travail
distinct ; ne pas réécrire les dates du snapshot historique.

Le premier pilote fournisseur, la migration de la base de l'opérateur et la montée à deux ou
trois matchs restent des opérations à préparer avec leur manifeste exact. Le rapport synthétique
du WO ne vaut ni pilote réel réussi, ni validation de performance sur quatre heures.
