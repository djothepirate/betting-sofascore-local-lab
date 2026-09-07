# WO-058 — Utiliser une campagne live locale bornée

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Références : [ADR-SS-005 accepté](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md),
[architecture](../architecture/LIVE-J4-J5-CAMPAIGNS.md),
[qualification](../validation/WO058-LIVE-J4-J5-IMPLEMENTATION-20260907.md).

## Qualification hors fournisseur

La réalisation du WO utilise uniquement des données synthétiques et des bases Testcontainers.
Ces commandes sont lancées depuis le worktree du WO avec Java 25 et Docker Desktop disponibles :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Install-J3PlaywrightRuntime.ps1
.\scripts\Invoke-LivePlaywrightLoopbackQualification.ps1
```

L'installation explicite utilise le cache Chromium dédié `.tmp/provider-playwright-browsers`.
Le dernier lanceur construit le worker et exécute uniquement les qualifications de transport
et de vue live sur HTTP loopback. Les tests standards n'ouvrent pas de navigateur. Le test J6
requiert le port 8087 libre ; une application de l'opérateur ne doit pas être arrêtée implicitement.
Lire les XML Surefire/Failsafe effectifs, les skips et le résultat du lanceur. Un packaging avec
`-DskipTests` n'est pas une qualification.

Le replay local est une API Java `LiveReplayRunner.run(ReplayInput)`, utilisée dans ses tests.
L'entrée contient identifiant de fixture, heure initiale, durée, sélection, réponses avec SHA-256,
temps simulé de chaque réponse, arrêts et plafonds. Le résultat porte `SYNTHETIC_REPLAY`, son
empreinte de manifeste, les traces de familles et états finaux. `complete=false` signale notamment
un script de réponses épuisé. Aucun replay ne lance Playwright ni ne modifie la base.

## Préparer l'application de l'opérateur

L'application de V34 à la base utilisée par l'opérateur est une opération distincte de ces tests.
Préparer d'abord la sauvegarde, les empreintes et la restauration isolée selon
[J6](J6-BACKUP-RESTORE-AND-RETENTION.md), avec le conteneur et la base exacts. Ne pas faire pointer
une simple validation Spring/Flyway vers la base de l'opérateur pour obtenir un test vert.

Pour une base opérateur en V33, utiliser pour la sauvegarde préalable l'outillage J6 et ses
dépendances figés au commit `a7f544cc2f70db2066836107d1e7a21c4feccb4c`, dans un checkout distinct,
avec manifeste et cible exacts préparés pour cette opération. Vérifier cette sauvegarde par
restauration isolée V33 avant d'appliquer V34. Après migration, utiliser l'outillage J6 courant
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

1. Dans les résultats normalisés, sélectionner le match du pilote puis préparer la campagne.
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

J4 est interrogé dès le lancement puis à la minute tant que `notstarted`. J5 commence après
`inprogress`. Les incidents déclenchent des contrôles, jamais une conclusion sportive.
Le secours J4 intervient à cinq minutes sans signal ; seule sa réponse `finished` confirme
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

Après crash ou nettoyage incertain, `CLEANUP_REQUIRED` conserve l'exclusion fournisseur. Aucun
délai, redémarrage ou nouvelle préparation ne la lève. Consulter les preuves de campagne et faire
vérifier l'identité et la disparition de l'arbre de processus avant toute intervention locale
sur ce garde. Ce lot ne fournit pas de bouton de libération aveugle ; aucun GET n'est rejoué.
Un processus propriétaire encore actif ou d'identité inaccessible demeure protégé.

`LIVE_RAW_PREVIOUSLY_PURGED` signifie qu'une réception identique rencontre une ancienne preuve
dont J6 a purgé le corps. La réception nouvelle est annulée atomiquement ; la campagne s'arrête
pour stockage. Une réhydratation ou une politique de rétention différente exige un travail
distinct ; ne pas réécrire les dates du snapshot historique.

Le premier pilote fournisseur, la migration de la base de l'opérateur et la montée à deux ou
trois matchs restent des opérations à préparer avec leur manifeste exact. Le rapport synthétique
du WO ne vaut ni pilote réel réussi, ni validation de performance sur quatre heures.
