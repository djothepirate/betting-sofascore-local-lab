# WO-058 — Utiliser une campagne live locale bornée

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Références : [ADR-SS-005 accepté](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md),
[architecture](../architecture/LIVE-J4-J5-CAMPAIGNS.md),
[qualification V8 locale](../validation/WO058-LIVE-V8-CAPACITY-20260910.md).

## Nouvelles préparations : live-v8, profil local qualifié et activation manuelle distincte

Les nouvelles préparations sont orientées vers `live-v8`. Cette révision conserve la cible
de **60 s** pour les départs normaux de **chaque couple rencontre/famille** en jeu — J4,
incidents, statistiques et compositions — et admet au plus **dix rencontres**. Elle ne
transforme pas la date d'affichage, une tentative réservée ou une réception incomplète en
preuve de fraîcheur. Les manifestes v1–v7 restent inchangés et consultables avec leur
propre politique ; aucun profil plus ancien ne sert de repli à v8.

La qualification synthétique loopback V8 du 10 septembre est consignée dans le
[rapport de capacité](../validation/WO058-LIVE-V8-CAPACITY-20260910.md). Elle couvre dix
rencontres et les quatre familles, avec le worker de production, Chromium et PostgreSQL de test
sur `127.0.0.1`; elle n'a fait aucun appel SofaScore, aucune requête hors périmètre et n'a utilisé
aucune base opérateur. Elle qualifie l'admission locale à dix seulement pour le profil exact ci-dessous.

La configuration V8 requiert une empreinte
`SOFASCORE_LIVE_GROUPED_V8_QUALIFICATION_SHA256` et les huit enveloppes
`SOFASCORE_LIVE_GROUPED_V8_{J4,INCIDENTS,STATISTICS,LINEUPS}_{REQUEST,PROCESSING}_ENVELOPE`.
Sans cet ensemble cohérent, l'admission retourne une capacité à zéro et la préparation est
indisponible. Cet échec fermé reste attendu : **ne pas** recopier un hash V7 ni inventer une
enveloppe.

### Profil V8 exact à livrer dans le lanceur après revue et fusion

Les neuf variables V8 suivantes sont les seules valeurs de profil à saisir ensemble dans
`SofaScore - PLAYWRIGHT J3-J5 (MANUEL - OPT-IN) (LIVE)` lorsque la livraison du lanceur sera
effectuée. L’empreinte est celle du **profil** versionné, non celle du rapport natif.

| Variable | Valeur qualifiée |
| --- | --- |
| `SOFASCORE_LIVE_GROUPED_V8_QUALIFICATION_SHA256` | `c5cef2745422d70bab769d03a93991af8ce3d685fb9daabb64fd00ab0a1ca3c8` |
| `SOFASCORE_LIVE_GROUPED_V8_J4_REQUEST_ENVELOPE` | `300ms` |
| `SOFASCORE_LIVE_GROUPED_V8_J4_PROCESSING_ENVELOPE` | `500ms` |
| `SOFASCORE_LIVE_GROUPED_V8_INCIDENTS_REQUEST_ENVELOPE` | `300ms` |
| `SOFASCORE_LIVE_GROUPED_V8_INCIDENTS_PROCESSING_ENVELOPE` | `400ms` |
| `SOFASCORE_LIVE_GROUPED_V8_STATISTICS_REQUEST_ENVELOPE` | `350ms` |
| `SOFASCORE_LIVE_GROUPED_V8_STATISTICS_PROCESSING_ENVELOPE` | `400ms` |
| `SOFASCORE_LIVE_GROUPED_V8_LINEUPS_REQUEST_ENVELOPE` | `300ms` |
| `SOFASCORE_LIVE_GROUPED_V8_LINEUPS_PROCESSING_ENVELOPE` | `450ms` |

Cette empreinte lie le profil recalculé au planificateur V8 qui reprend un dépassement de
cadence au prochain slot J4 stable. Elle doit être livrée avec les huit enveloppes ci-dessus ;
elle ne modifie pas le manifeste d'une campagne déjà démarrée et ne réarme pas un refus 403/429.

Le plafond opérateur `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY` doit rester à `10` et
`SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT` à `30s`. Le fence V8 de `500ms` est une borne interne
du profil : il ne remplace pas `SOFASCORE_MINIMUM_DELAY`. Cette variable globale conserve sa
valeur inchangée, notamment son défaut de trois secondes ; ne pas lui attribuer `500ms`.
Cette documentation ne modifie pas le fichier `.launch`, ne redémarre pas le Lab et ne lance
aucune campagne fournisseur.

### Règles V8 qualifiées localement

| Élément | Règle de conception V8 |
| --- | --- |
| Capacité de sélection | Au plus **10** rencontres, sous réserve de l'admission du profil V8 et des plafonds de manifeste existants. |
| Cadence normale | Les quatre familles sont placées dans des slots déterministes par rencontre et par famille. Lorsque les échanges restent dans leurs enveloppes qualifiées, deux départs normaux successifs du même couple sont séparés de **60 s au plus**. |
| Fence de transport local | Après une fin d'échange prouvée, le fence ajouté est de **500 ms**. Le départ réel peut toutefois être plus tardif lorsqu'un slot, un budget, un 404, un timeout ou une suspension l'impose ; ce retard est visible et ne doit pas être qualifié de fraîcheur. |
| Réservation temporelle | Les quatre enveloppes, quatre fences de 500 ms et la réserve statique V51 de 1 000 ms forment une réservation stricte de **6 000 ms** par groupe. Dix groupes occupent exactement les 60 000 ms disponibles. |
| Budgets locaux persistants | Au plus **45 départs / 60 s glissantes** et **2 756 départs / heure glissante**. Les compteurs sont partagés et survivent aux campagnes ; ils ne sont pas un seuil supposé du fournisseur. |
| Preuve de départ et pression | La fenêtre V8 utilise `REQUEST_SENT` authentifié quand il est cohérent avec la réservation et l'observation parent ; sans preuve, la complétion est retenue de façon conservatrice. Un hold qui franchit un créneau normal rend les cycles/familles manqués visibles dans `WAITING_PRESSURE_RECHECK`, avant un J4 de reprise à l'échéance autorisée. |
| Dépassement de cadence J4 | Une émission J4 authentifiée plus de 500 ms après son slot strict conserve le groupe J4/J5 comme manqué dans `WAITING_CADENCE_RECHECK`. Aucun J5 de rattrapage ne part ; seul le prochain J4 est offert sur la phase stable `REQUEST_SENT + 60 s − 500 ms`. Ce chemin ne reçoit pas le backoff de cinq minutes des timeouts ou dépassements d'enveloppe. |
| Réception et réponse lente | Une réponse qui dépasse l'enveloppe ne permet pas d'affirmer la cadence normale. Elle est diagnostiquée comme exception ; une réception fournisseur reste distincte de l'heure de départ locale. |
| Refus connus | Un HTTP **403/429** suspend durablement l'accès partagé. Un réarmement demeure manuel, sans sonde, reset de budget, proxy, rotation d'IP/VPN ou reprise automatique. |

Les règles prématch restent celles du calendrier adopté : groupe initial, contrôle T−60 pour
un coup d'envoi plus lointain, compositions seules toutes les cinq minutes jusqu'à confirmation
entre T−60 et T−5, puis attente du coup d'envoi. Au coup d'envoi, J4 seul reste à 60 s jusqu'à
`inprogress`; les quatre familles suivent ensuite la vague V8 de 60 s. Un `delayed` avec un
nouvel `startTimestamp` recalcule les fenêtres ; horaire manquant ou régression après
`inprogress` restent à examiner. Les 404 J5 restent différés par rencontre/famille et ne
créent ni rafale de rattrapage ni contournement du budget.

Lorsqu'un J4 initial `notstarted` est parti moins de 60 s avant T0, V8 ne lance pas une seconde
vague complète au coup d'envoi. Le premier J4 de bascule attend la plus tardive de la phase
sérialisée de T0 et de ce départ initial + 60 s ; les J5 restent conditionnels à sa réponse
`inprogress`. Cette règle conserve la fraîcheur par famille tout en évitant une pointe de 60
départs/minute pour dix rencontres lors d'un lancement très proche du coup d'envoi.

### Rejouer V8 uniquement avec le fournisseur absent

La qualification V8 fraîche a abouti le 10 septembre avec la commande explicite suivante :

```powershell
.\scripts\Invoke-LiveGroupedPlaywrightQualification.ps1 -PolicyVersion live-v8
```

Elle a utilisé le worker de production, Chromium et PostgreSQL de test contre un serveur
éphémère lié à `127.0.0.1`, avec 40 réponses froides de 5 Mio, un drain de 60 001 ms, cinq
minutes de mise en régime, 1 800,0198031 s établies et 2 100,0198031 s de voie stricte. Les
résultats sont 1 444 appels au total, dont 1 203 établis et 40 froids, zéro appel fournisseur,
zéro requête hors périmètre, zéro base opérateur et zéro cycle manqué. Le replay de production
exécute 16 scénarios ; le profil fixe 45/min, 2 756/h et 2 480 départs horaires planifiés pour
dix rencontres, avec un timeout Playwright effectif de 30 000 ms.

Les octets bruts sont conservés dans
[le rapport natif](../validation/WO058-GROUPED-LIVE-V8-NATIVE-20260910.json)
`f5b70709dcc51d9b40223fde1175d3c507190244562355e675689cb06e9a7fc0` et
[le profil](../validation/WO058-GROUPED-LIVE-V8-PROFILE-20260910.json)
`c5cef2745422d70bab769d03a93991af8ce3d685fb9daabb64fd00ab0a1ca3c8`. Le script ne charge pas le
lanceur et ne modifie aucune campagne réelle. Une passe favorable reste une preuve de
planification loopback sous enveloppes ; elle ne mesure ni l'acceptation de 45/min par
SofaScore, ni la latence Internet, ni la résistance future à un bannissement.

## Profil historique live-v7, qualifié avec ses propres bornes

Le lot V4/V46 et live-v7/V47 du 9 septembre est qualifié séparément du profil historique
v6. Son historique demande `SOFASCORE_LIVE_GROUPED_V7_QUALIFICATION_SHA256`
et les huit enveloppes `SOFASCORE_LIVE_GROUPED_V7_{J4,INCIDENTS,STATISTICS,LINEUPS}_{REQUEST,PROCESSING}_ENVELOPE`.
L'absence de preuve laissait la capacité v7 à zéro. La passe locale dédiée fournit les valeurs
et le profil ; une empreinte v6 ne valait pas qualification v7, et le profil v7 ne vaut pas
qualification v8.

### Relire le profil local v7 qualifié

Le [profil v7 versionné](../validation/WO058-LIVE-V7-CAPACITY-20260910.md) qualifie au plus
**trois rencontres** pour son périmètre synthétique loopback. Il ne mesure ni l’acceptation
du fournisseur ni une capacité à trois rencontres au-delà de ce périmètre. Ces onze valeurs
restent la trace de cette qualification v7 : elles ne sont pas appliquées automatiquement
et ne doivent pas être chargées comme un substitut au profil v8 absent.

| Variable | Valeur qualifiée |
| --- | --- |
| `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY` | conserver la valeur opérateur existante (`10` actuellement) |
| `SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT` | `30s` |
| `SOFASCORE_LIVE_GROUPED_V7_QUALIFICATION_SHA256` | `7b339ee1664abb744639164aa2e7d69388de22ac49db51edadb3b9b33f619335` |
| `SOFASCORE_LIVE_GROUPED_V7_J4_REQUEST_ENVELOPE` / `SOFASCORE_LIVE_GROUPED_V7_J4_PROCESSING_ENVELOPE` | `350ms` / `400ms` |
| `SOFASCORE_LIVE_GROUPED_V7_INCIDENTS_REQUEST_ENVELOPE` / `SOFASCORE_LIVE_GROUPED_V7_INCIDENTS_PROCESSING_ENVELOPE` | `350ms` / `550ms` |
| `SOFASCORE_LIVE_GROUPED_V7_STATISTICS_REQUEST_ENVELOPE` / `SOFASCORE_LIVE_GROUPED_V7_STATISTICS_PROCESSING_ENVELOPE` | `350ms` / `500ms` |
| `SOFASCORE_LIVE_GROUPED_V7_LINEUPS_REQUEST_ENVELOPE` / `SOFASCORE_LIVE_GROUPED_V7_LINEUPS_PROCESSING_ENVELOPE` | `350ms` / `500ms` |

L’empreinte est celle du fichier de profil, pas celle du rapport natif. Une valeur opérateur
supérieure à trois n’augmente pas l’admission v7, qui est déjà bornée à trois ; la valeur
existante peut donc rester inchangée. Les valeurs par défaut restent invalides afin que
l’absence d’une qualification explicite conserve la capacité à zéro.

Le profil v7 conservait les protections partagées 25/min, 1 000/h et deux secondes après
fin d’échange, et admettait trois rencontres au plus. Ce plafond est celui du profil,
pas une limite définitive de l’architecture. Les plafonds minute/heure sont des paramètres
locaux prudents et révisables. Un candidat 40/min et 2 000/h est analysé séparément, sans
activation ni modification du budget déjà consommé ou de la suspension fournisseur.
Avec sept rencontres, les deux secondes seules consomment 56 s par minute avant les
échanges : relever les compteurs ne suffit pas à établir une cadence fiable de 60 s.

| Phase observée | Appels programmés |
|---|---|
| Lancement, J4 notstarted | un groupe J4 + incidents + statistiques + compositions |
| J4 delayed avec nouveau `startTimestamp` | reprendre les fenêtres sur le nouvel horaire, sans arrêter la rencontre |
| Coup d’envoi à plus d’une heure | attendre T−60 min, puis un groupe complet |
| Dernière heure, avant T−5 min | compositions seules toutes les 5 min jusqu’à confirmation |
| De T−5 min au coup d’envoi | aucune répétition ; attente du coup d’envoi |
| Coup d’envoi atteint, J4 notstarted | J4 seul toutes les 60 s ; si un J4 initial a moins de 60 s, reprendre à sa prochaine phase stable plutôt que dupliquer la vague à T0 |
| J4 inprogress | J4 et les trois familles J5 toutes les 60 s nominales |
| J4 finished | collecte finale bornée puis arrêt de la rencontre |

Un refus 403/429 conserve la suspension persistante. Les 404 J5 en jeu restent espacés
par famille ; les timeouts admissibles exigent la preuve terminale et reportent le match.
Une reprise ne traverse pas la pause T−5/T0 : elle attend le coup d’envoi si nécessaire,
sans raccourcir son délai minimal. Les observations historiques ne sont pas réécrites.
Un J4 `delayed` doit porter un nouvel horaire lisible. À T−60 exactement, ce J4 sert de
contrôle horaire et ses trois familles J5 sont ajoutées au même groupe ; dans les autres
cas, aucune seconde lecture J4 immédiate n’est créée. Un `delayed` sans horaire, ou après
un `inprogress`, demande une revue. `postponed` reste arrêté. Une campagne déjà terminée
ou arrêtée n’est jamais relancée automatiquement par ce recalcul.
Le prochain lancement et le chargement du profil relèvent toujours de l’opérateur.

## Profil historique live-v6, qualifié en boucle locale

Le premier lot de résilience est implémenté et qualifié fonctionnellement hors fournisseur :
[résultats et limites](../validation/WO-058-provider-resilience-qualification-20260909.md).
Le [complément temporel v6](../validation/WO058-LIVE-V6-CAPACITY-20260909.md) qualifie désormais
sept rencontres avec une preuve distincte des anciennes v5. Les
préparations v6 nécessitaient leur profil `grouped-v6`, son SHA et les huit enveloppes dédiées ;
sans preuve complète, la capacité proposée reste nulle. Elle est plafonnée à **sept rencontres**,
même si le plafond opérateur est plus élevé. La cible est 100 s pour J4/incidents/statistiques
et les compositions prématch, puis 300 s pour les compositions en jeu.

Ces cibles sont soumises à une protection commune à **tous les accès Playwright J3/J4/J5** :
au moins **deux secondes après chaque fin d'échange**, **25 charges sur 60 s glissantes** et
**1 000 sur une heure glissante**. Les charges vieillissent depuis la fin de l'échange,
y compris après un échec ; une réservation non résolue reste bloquante. Les compteurs
survivent à un changement de campagne ou au redémarrage. Les délais historiques plus longs
restent appliqués aux frontières concernées. Il s'agit de paramètres locaux conservateurs,
sans seuil d'acceptation SofaScore connu. Une attente de budget peut réduire la fraîcheur :
la prochaine collecte est reportée sans rattrapage en rafale ni extension des quatre heures.

La sélection conserve les plafonds 2 500/20 000 appels et 15 728 640 000 octets. Sept matchs
en jeu représentent nominalement 14 appels/minute, hors initialisation/finalisation.
La borne d'admission horaire garde 896 appels dans une allocation de 900 ; elle ne garantit
pas la disponibilité immédiate d'un budget déjà consommé par un autre parcours.

### Consulter la protection et décider du réarmement

La page locale `/provider-access` distingue attente de budget, suspension fournisseur et
départ non résolu. Sa lecture ne contacte pas SofaScore. Un 403 ou 429 confirmé bloque les
nouveaux accès, même si le corps de cette réponse n'a pas été reçu complètement. Une
garde de processus FREE après nettoyage n'efface pas cette suspension.

La page de ce formulaire est servie avec `Referrer-Policy: same-origin` : Chromium/Brave
conserve ainsi l'origine loopback exacte pour le `POST` de réarmement. La frontière locale
continue de refuser `Origin: null`, toute origine étrangère et les en-têtes de proxy ; un
réarmement valide reste une décision opérateur locale, sans sonde ni appel fournisseur.

Après un arrêt, achever d'abord le nettoyage local de la session. Si un départ reste
incertain, sa clôture locale exige une garde libre et l'absence de session active ;
elle conserve son coût et ne fabrique aucune réponse. Le réarmement de la suspension
exige ensuite une confirmation opérateur sur l'état courant et l'expiration de tout
`Retry-After` valide. **Il ne lance aucune collecte, ne vérifie pas l'accès fournisseur,
ne remet pas les budgets à zéro et ne reprend pas une campagne arrêtée.** Une éventuelle
nouvelle campagne nécessite toujours un lancement manuel distinct. L'expiration d'un
délai ne constitue jamais un réarmement automatique.

### Lire un diagnostic ou une famille indisponible

Les diagnostics indiquent l'étape et le timeout effectif du transport, le statut HTTP
dès qu'il est connu, et la distinction entre en-têtes reçus et réponse complète. Sans
réception complète, il n'y a ni snapshot fictif ni nouvelle observation métier. Un timeout
sans statut connu ne démontre pas un 403 ou un blocage d'adresse. La première cause et le
dernier échec de nettoyage restent séparés et consultables après redémarrage.

En v6, les 404 J5 retardent la seule famille du seul match concerné : 300, 600 puis 900 s
pour incidents/statistiques/compositions prématch ; 600 puis 900 s pour les compositions
en jeu. Le prochain tour admissible peut être plus tardif selon J4 ou le budget partagé.
Une réponse exploitable rétablit le nominal ; un changement de phase J4 réévalue le délai.
J4 continue sa politique propre ; son 404 demande une revue individuelle. Le dernier cycle
reste borné, sans tentative finale supplémentaire. Une ancienne donnée disponible n'est
pas effacée : consulter son heure reçue, et distinguer UNAVAILABLE, absence, bloc vide et zéro.

### Timeout et profils historiques

La nouvelle garde v6 accepte un timeout strictement positif jusqu'à 30 s. Le défaut `local`
reste 20 s et `SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT` choisit la valeur effective au démarrage.
Modifier seulement le plafond Java n'augmente pas ce réglage. Les campagnes historiques v5
gardent leur plafond 20 s et v1–v4 leur plafond 10 s. Les enveloppes de qualification sont des
coûts mesurés distincts : ne pas les remplacer par ce timeout.

### Profil temporel v6 du 9 septembre et réglages du lanceur

Le [profil final](../validation/WO058-GROUPED-LIVE-V6-PROFILE-20260909.json) repose sur
35 minutes natives, dont cinq d'initialisation et trente établies, via Chromium loopback,
le wrapper persistant de production, ses diagnostics et PostgreSQL V44 isolé. La mesure
compte 494 échanges, dont 420 établis, zéro cycle manqué, au plus 18 départs sur 60 s
et un délai minimal observé après fin d'échange de 2,299724 s. L'admission Java confirme
sept rencontres avec ses 24 scénarios et les maxima arrondis vers le haut à 50 ms.

La portée des enveloppes est **`STEADY_64_KIB_ONLY`** : corpus établi de 64 Kio, dont
compositions V3 enrichies. Les 28 premières réponses de 5 Mio sont mesurées séparément,
hors de ces enveloppes constantes. Ce profil ne qualifie pas des corps de 5 Mio récurrents,
des réponses fournisseur lentes ni l'acceptation de cette charge par SofaScore.
Le run de 35 minutes n'épuise pas la fenêtre horaire ; les invariants sur cette fenêtre
conservent leurs preuves PostgreSQL/replay distinctes.

| Variable d'environnement dédiée | Valeur du profil v6 |
|---|---|
| `SOFASCORE_LIVE_GROUPED_V6_QUALIFICATION_SHA256` | `5986e95306ef9b68cb0a96abdb02a5e312cb626277fd0720f9804b6ff5dd0e04` |
| `SOFASCORE_LIVE_GROUPED_V6_J4_REQUEST_ENVELOPE` | `250ms` |
| `SOFASCORE_LIVE_GROUPED_V6_J4_PROCESSING_ENVELOPE` | `350ms` |
| `SOFASCORE_LIVE_GROUPED_V6_INCIDENTS_REQUEST_ENVELOPE` | `300ms` |
| `SOFASCORE_LIVE_GROUPED_V6_INCIDENTS_PROCESSING_ENVELOPE` | `400ms` |
| `SOFASCORE_LIVE_GROUPED_V6_STATISTICS_REQUEST_ENVELOPE` | `300ms` |
| `SOFASCORE_LIVE_GROUPED_V6_STATISTICS_PROCESSING_ENVELOPE` | `450ms` |
| `SOFASCORE_LIVE_GROUPED_V6_LINEUPS_REQUEST_ENVELOPE` | `250ms` |
| `SOFASCORE_LIVE_GROUPED_V6_LINEUPS_PROCESSING_ENVELOPE` | `450ms` |

Conserver le choix opérateur `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY=6` et
`SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT=30s`. La preuve qualifie une capacité locale de sept,
mais n'augmente pas le plafond choisi à six et n'applique aucune variable automatiquement.
Le timeout de 30 s a été réellement utilisé dans le run ; il reste une borne d'attente,
distincte des enveloppes d'échange ci-dessus. Une ancienne instance garde ses valeurs
jusqu'à son redémarrage. L'application du profil dans Eclipse est une étape de livraison
distincte, suivie d'une nouvelle préparation et d'un lancement opérateur explicite.
La vérification finale de ce complément et les trois contrôles Chromium de son interface réussissent.

Le script accepte désormais `live-v6` avec son propre chemin wrapper/PostgreSQL ;
les branches v4/v5 restent historiques. La commande explicite hors fournisseur est :

```powershell
.\scripts\Invoke-LiveGroupedPlaywrightQualification.ps1 -PolicyVersion live-v6
```

Les [octets natifs conservés](../validation/WO058-GROUPED-LIVE-V6-NATIVE-20260909.json)
portent le SHA-256 `07500b848513cf75254eb110112f89a7b7e6d14cda71b4d5ed25d9fcd3da8683`.
Le [calculateur archivé](../validation/v6-profile-builder/V6MeasuredProfile.java) lit ce
rapport, arrondit les maxima établis et appelle l'admission/les replays Java ; il reste
un utilitaire de preuve hors réseau, sans modification de configuration ou de base.
Les sections suivantes conservent les réglages et qualifications historiques ainsi que
les commandes de consultation communes ; leurs preuves ne remplacent pas le profil v6.

### Correctif réalisé et qualifié hors fournisseur — réponses lentes et timeouts isolés

Le correctif [ADR-SS-005 v0.9](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md)
est réalisé ; sa [qualification fonctionnelle hors fournisseur](../validation/WO058-SLOW-TIMEOUT-RECOVERY-20260909.md)
est **réussie**. Le timeout configuré n'augmente pas et les plafonds, le profil admis,
les paramètres du lanceur et le rythme nominal restent distincts de cette tolérance.

La dernière `-Pintegration-tests clean verify` termine le 9 septembre à 17:17:59Z en
8 min 12 s : 2 068 cas standards, cinq skips explicités et 213 cas PostgreSQL, sans
échec ni erreur. Les neuf cas Chromium transport/UI et les deux contrôles natifs de
réception réussissent. Le smoke nominal de trois minutes reste distinct de la preuve
de capacité de 35 minutes, qui n'est pas renouvelée. L'inventaire et les empreintes des
logs figurent dans le rapport ; le WO reste `IN_PROGRESS` pour revue humaine. Cette
qualification n'effectue ni livraison Eclipse, ni démarrage, ni appel fournisseur.

Pour une session v6 déjà lancée, un timeout peut désormais laisser les autres rencontres
poursuivre **si le transport prouve la fin de l'échange et le nettoyage de la page dans
le contexte existant**. Les diagnostics doivent porter `exchangeEndedAt`,
`exchangeEndReason=FINISHED` ou `ABORTED`, et `contextReusable=true`. Ces champs ne sont
jamais déduits du seul code TIMEOUT ou d'un statut HTTP absent. Avant les en-têtes,
l'annulation est demandée immédiatement avec terminal `ABORTED` corrélé. Après les
en-têtes, le worker attend naturellement `FINISHED` dans la même grâce maximale de
deux secondes, sans `Page.stopLoading` qui peut supprimer le terminal après `COMMIT`.
Une seconde supplémentaire est réservée à la transmission IPC. Sans terminal dans
la grâce et nettoyage prouvé, l'arrêt global et la fermeture sont conservés.
Cette attente n'allonge ni le timeout de collecte ni ses budgets ; un corps terminé
après timeout reste abandonné, même si sa fin permet de prouver la clôture de l'échange.

Pour une réponse réussie, l'heure de réception est prise immédiatement après `body()`,
avant le nettoyage local obligatoire. Un timeout n'actualise pas cette heure et ne
crée aucun snapshot. La livraison exige l'application et son worker reconstruits
ensemble avec IPC v7 ; V45 sera appliquée au prochain démarrage opérateur. Ce lot ne
démarre pas l'application et n'applique pas de migration à la base opérateur.

Le corps incomplet est abandonné et la tentative reste comptée, sans snapshot ni donnée
normalisée nouvelle. Le groupe est fermé. Toutes les familles du match attendent au
moins **cinq minutes après la fin/nettoyage confirmés**, puis un nouveau J4 réévalue son
statut avant toute famille J5. La prochaine échéance peut être plus tardive selon les
limites globales. Les anciennes données restent consultables avec leur date ; un code
`PLAYWRIGHT_TIMEOUT_RETRY_DEFERRED` signale ce report, sans le transformer en succès.

La session dispose au plus de trois tolérances. Deux timeouts sans `PARSED` intermédiaire
restent bloquants ; un 404 n'est pas un succès. Le même couple rencontre/famille doit
également avoir obtenu un `PARSED` depuis son précédent timeout pour bénéficier d'une
nouvelle tolérance. Le compteur total n'est pas réinitialisé par un succès. En
finalisation, un timeout admissible arrête seulement le match et laisse la finalisation
incomplète, sans nouvelle tentative finale. Si les cinq minutes dépassent la fenêtre,
le match est arrêté ; ni durée, ni budget, ni arrêt opérateur ne sont prolongés.
Quand tous les matchs sont terminaux, la campagne peut afficher `COMPLETED` même si
l'un porte `STOPPED_ERROR` : vérifier son état individuel et sa collecte finale.
Un arrêt opérateur intervenant pendant le nettoyage reste prioritaire : la tentative
porte `PLAYWRIGHT_TIMEOUT_ABANDONED`, sans reprise programmée ni remplacement du motif
d'arrêt. La reprise admissible est calculée au moins 300 s après le plus tardif de
l'instant courant et de la fin observée, après le nettoyage confirmé.

Un **403/429 connu arrête toujours la campagne et suspend durablement les accès**, même
si le corps de réponse expire ensuite. Le réarmement manuel et la clôture locale restent
des actions distinctes. Une campagne déjà arrêtée, notamment les anciens essais 651d473e
et 02ad2380, ne reprend pas avec ce correctif ; ses observations restent inchangées.

Le refus local `INVALID_REQUEST` après une famille J5 différée est corrigé séparément :
l'autorité v6 autorise les familles admissibles dans leur ordre source, en conservant
J4 initial, identité du groupe et interdiction des répétitions. Les autorités v4/v5 et
manuelles gardent leurs contrôles. Cette correction ne prouve aucune amélioration de
l'acceptation fournisseur et les preuves temporelles nominales antérieures ne couvrent
pas à elles seules les nouveaux scénarios de timeout.

Ce correctif ajoute V45 aux diagnostics ; les commandes J6 de sauvegarde/restauration
et rétention doivent donc reconnaître le schéma 45. La qualification temporelle précédente
reste datée sur PostgreSQL 44 et ses artefacts ne sont pas réécrits. L'installation opérateur
et ses migrations sont une étape distincte, après qualification du correctif.

### Historique : attendre une réponse lente — réglage v5 du 9 septembre

Le profil Spring `local` règle désormais le timeout Playwright à **20 secondes**
par défaut. Redémarrer le Lab avec le lanceur Eclipse habituel, puis préparer et
lancer manuellement une nouvelle campagne. Une instance déjà démarrée conserve
son ancien réglage. Si `SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT` est défini dans
Eclipse ou l’environnement, cette valeur explicite prime sur le défaut ; utiliser
`20s` pour cet essai, `10s` pour revenir au délai précédent. Vérifier aussi le
fichier `.env` importé par Spring : une valeur explicite copiée depuis
`.env.example` peut conserver dix secondes.

Le service live-v5 refuse zéro, une valeur négative ou supérieure à vingt secondes
avant d’acquérir le transport. Les manifestes historiques live-v1 à live-v4
conservent la limite de dix secondes. Le réglage Playwright du profil local est
partagé avec les parcours manuels. Le défaut hors profil local reste dix secondes.

Ce réglage allonge l’attente du transport, pas le traitement du JSON ou les écritures
en base. Les enveloppes du profil de capacité ne doivent pas être remplacées par
vingt secondes : elles correspondent aux coûts qualifiés. Des réponses lentes
peuvent toujours produire un retard de collecte ; les contrôles de surcharge,
les budgets et l’arrêt global sur timeout/403 restent actifs, sans retry.
La valeur du timeout n’est pas enregistrée dans les anciens manifestes : la noter
avec le réseau utilisé et les heures du prochain essai. Voir la
[validation ciblée](../validation/WO058-PLAYWRIGHT-TIMEOUT-20260909.md).

### Parcourir une campagne de plus de dix rencontres

Le complément de pagination affiche dix rencontres par page, dans l’ordre de la sélection.
Les commandes « Précédente », numéros de page et « Suivante » figurent au-dessus et au-dessous
des rencontres. Une campagne de dix-sept rencontres comporte deux pages de dix et sept cartes.
La navigation apparaît à partir de onze rencontres et reste disponible après la fin de la campagne.

La lecture automatique conserve la page choisie et actualise ses cartes. Un changement de
page recharge la page ; les panneaux y retrouvent leur ouverture initiale. Une rencontre
terminée reste à sa place. Depuis la liste ou le détail d’une rencontre, « Suivre la campagne »
ou « Ouvrir la campagne » mène directement à cette rencontre, même après la première page.

Le résumé, les plafonds, l’autonomie et « Arrêter toute la campagne » concernent toujours
l’ensemble de la campagne. L’arrêt individuel conserve la page choisie. Le lancement et
« Préparer une nouvelle campagne avec ces rencontres » utilisent le manifeste complet.
Cette pagination ne change ni la sélection collectée ni sa cadence.

### Interpréter un arrêt avec clôture en attente

« Collecte arrêtée / clôture locale requise » décrit l’état du processus. Il peut coexister
temporairement avec `RUNNING` et des rencontres `COLLECTING` enregistrés en base : ces états
durables ne sont réconciliés qu’à la clôture. Le complément d’affichage remplace alors
l’autonomie par « Collecte arrêtée » ; les compteurs et observations restent consultables.
« Finaliser la clôture locale » termine cette clôture, sans reprendre les appels. Un nouveau
suivi passe par une nouvelle préparation et un lancement manuel après clôture réussie.

L’état `LOCAL_CLEANUP_PENDING` n’expose pas la cause primaire de l’erreur qui a précédé la
clôture. Conserver la console de l’application et les horodatages pour le diagnostic ;
ne pas attribuer automatiquement l’arrêt à un plafond ou au fournisseur.

### Lire les incidents

Les incidents apparaissent dans l’ordre reçu, avec leur minute, l’équipe et le joueur.
Les buts présentent leur score ; les cartons jaunes, rouges et seconds jaunes ont
des libellés distincts. Les remplacements séparent le joueur entrant (flèche verte)
et le sortant (flèche rouge). La mention « Remplacement sur blessure » exige une blessure
explicitement renseignée dans l’observation. Les motifs, passeurs, décisions VAR et temps additionnel
sont affichés lorsqu’ils sont renseignés dans l’observation.

Les penalties distinguent « ⚽ But sur penalty », « 🧤 Penalty arrêté » et
« ❌ Penalty manqué ». Le gant exige le motif `goalkeeperSave` ou la description
`Goalkeeper save` ; sinon, un échec porte la croix. Cette distinction arrêté/manqué
s’applique également aux tirs au but.

« Tableau normalisé (technique) » ouvre les colonnes détaillées. Ce volet est replié
initialement et conserve son ouverture pendant l’actualisation live. Un score sur un
but décrit cet incident ; le score courant J4 reste dans le résumé de la rencontre.
Les repères de période conservent la minute fournie.

La page J5 utilise la même présentation. Les sources et la complétude restent
consultables ; une famille indisponible ne devient pas une liste vide.
[Portée et qualification graphique](../validation/WO058-INCIDENT-GRAPHICS-20260908.md).

### Lire les compositions

La page de campagne, le suivi de la fiche d’une rencontre et la page J5 manuelle utilisent
la même présentation. Les équipes apparaissent côte à côte sur écran large et l’une sous
l’autre sur mobile. Chaque en-tête indique le côté, le nom et la formation reçue ; le badge
de confirmation décrit la composition entière, indépendamment du taux de complétude.

Cliquer sur l’en-tête d’une équipe, « Titulaires » ou « Remplaçants » replie ou déplie ce
panneau. Ces commandes fonctionnent aussi avec Entrée/Espace. Le lecteur live conserve
l’état des panneaux pendant les actualisations, y compris lorsqu’un joueur ou la confirmation
change. Une recharge complète de la page retrouve les panneaux ouverts initialement.
Les cartes présentent le numéro, le nom et le poste ; les mentions « Titulaire » et
« Remplaçant » ne sont pas répétées sous chaque joueur, car les sections indiquent déjà ce rôle.

Le badge « C · Capitaine » apparaît uniquement pour un indicateur fournisseur explicitement vrai.
Cliquer sur une carte, ou l'activer au clavier, ouvre ses statistiques individuelles regroupées
en français. Seuls les chiffres présents dans cette observation sont affichés ; une donnée absente
ne devient pas zéro. La note principale et ses variantes éventuelles restent distinctes. Ouvrir
ce panneau ne déclenche aucune collecte, et le lecteur conserve son ouverture lors des actualisations.

La section « Joueurs indisponibles » conserve un comptage séparé. Elle indique le motif fourni,
traduit lorsqu'il est connu, et le retour estimé par le fournisseur s'ils sont présents. Sans
description, le joueur reste simplement indisponible. Une liste non renseignée est distincte
d'une liste reçue vide. Ces ajouts concernent les observations interprétées par V3 ; les anciennes
compositions ne sont pas enrichies automatiquement. Voir le
[contrat V3 et sa migration V41](../architecture/J5-LINEUPS-V3-PLAYER-DETAILS.md).

Le schéma des titulaires regroupe les postes gardien, défenseur, milieu et attaquant ; les
postes absents ou autres sont distingués. Il ne représente pas les positions tactiques
précises. Aucun numéro, joueur ou formation n’est ajouté pour compléter les données reçues.
Une réponse HTTP 404 reste une indisponibilité, distincte d’une liste observée vide. Source,
hash et heure de réception restent consultables au-dessus de la composition.

### Lire les statistiques

Les pages live et J5 traduisent les groupes et intitulés connus en français : par exemple,
« Vue d’ensemble du match », « Possession du ballon », « Buts attendus (xG) », « Tirs cadrés »
et « Arrêts du gardien ». Un nom inconnu reste affiché tel qu’il a été reçu.
Les fautes subies, entrées et phases dans cette zone utilisent « dans le tiers offensif ».
La traduction ne change ni les valeurs observées, ni leur ordre, ni les périodes disponibles.
Les panneaux gardent leur ouverture ou fermeture lors des actualisations live.
[Portée et validation des libellés](../validation/WO058-UI-LABELS-20260909.md).

### Historique : cadence et admission live-v5

La qualification dédiée de vingt rencontres à 100 secondes est réussie sur Chromium loopback
et PostgreSQL isolé : cinq minutes d’initialisation puis trente minutes établies, 1 413 appels
dont 1 200 établis, zéro cycle manqué et 80 couples rencontre/famille contrôlés. Le
[profil final](../validation/WO058-GROUPED-LIVE-V5-PROFILE-20260908.json) est qualifié dans
cette portée synthétique ; la fraîcheur fournisseur réelle reste à observer. Les deux commandes
finales `clean verify` et `-Pintegration-tests verify` réussissent le 08/09, respectivement à
18:47:26 et 18:56:06 UTC. Chacune produit 1 840 cas Surefire, sans échec ni erreur et avec
cinq ignorés, ainsi que 166 cas Failsafe sans échec, erreur ni ignoré. Ces résultats et leurs
commandes effectives sont reliés dans le [rapport v5](../validation/WO058-GROUPED-LIVE-V5-20260908.md).

L’essai précédent à 75 secondes a tenu sa cadence à vingt rencontres, mais ses coûts mesurés
n’admettent que dix-sept rencontres. Il reste une [preuve distincte](../validation/WO058-GROUPED-LIVE-V5-CANDIDATE-75-PROFILE-20260908.json)
et ne qualifie pas la cible v5 de vingt rencontres à 100 secondes.

Les préparations historiques v5 ciblent **100 secondes par rencontre pour J4, incidents et
statistiques**, jusqu’à vingt rencontres selon la qualification retenue. Les compositions
sont initiales puis réparties sur trois tours, toutes les cinq minutes pendant le jeu.
Avant le début confirmé, J4 et les compositions éligibles restent à 100 secondes.

Les appels d’un même groupe sont séquentiels sans pause ajoutée. Une seconde sépare deux
groupes de la même session v5 ; les frontières avec une autre session, les parcours manuels
et les campagnes historiques gardent trois secondes. Les politiques v1–v4 restent figées.

Le manifeste indique **2 500 appels par rencontre et 20 000 par campagne**, quatre heures au
maximum et un plafond indépendant de **15 728 640 000 octets bruts**. Il s’arrête au premier
budget épuisé. En régime établi, vingt matchs en jeu représentent environ **40 appels/minute** ;
quatre heures demandent environ 9 600 appels, plus les initialisations et finalisations.
L’autonomie affichée utilise les compteurs réellement restants et les phases des rencontres.

Un **profil v5 séparé** est obligatoire. Les propriétés de qualification historiques et
`SOFASCORE_LIVE_GROUPED_*` restent celles de v4 et ne qualifient pas v5. Les défauts
conservateurs ne sont pas abaissés. Sans preuve v5 complète, la capacité affichée est nulle.

| Variable du lanceur | Valeur du profil final v5 du 8 septembre 2026 |
|---|---|
| `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY` | `20` — plafond opérateur, vingt effectifs au maximum |
| `SOFASCORE_LIVE_GROUPED_V5_QUALIFICATION_SHA256` | `923c4499d2005d4d2f91499148f749dc7f80ea0868ff1cfc7a42106ac9863225` |
| `SOFASCORE_LIVE_GROUPED_V5_J4_REQUEST_ENVELOPE` | `400ms` |
| `SOFASCORE_LIVE_GROUPED_V5_J4_PROCESSING_ENVELOPE` | `600ms` |
| `SOFASCORE_LIVE_GROUPED_V5_INCIDENTS_REQUEST_ENVELOPE` | `400ms` |
| `SOFASCORE_LIVE_GROUPED_V5_INCIDENTS_PROCESSING_ENVELOPE` | `500ms` |
| `SOFASCORE_LIVE_GROUPED_V5_STATISTICS_REQUEST_ENVELOPE` | `350ms` |
| `SOFASCORE_LIVE_GROUPED_V5_STATISTICS_PROCESSING_ENVELOPE` | `500ms` |
| `SOFASCORE_LIVE_GROUPED_V5_LINEUPS_REQUEST_ENVELOPE` | `350ms` |
| `SOFASCORE_LIVE_GROUPED_V5_LINEUPS_PROCESSING_ENVELOPE` | `450ms` |

Ces enveloppes couvrent les maxima établis mesurés avec des réponses de 64 Kio et conservent
les planchers de l’essai précédent. La vague initiale de quatre-vingts réponses de 5 Mio est
mesurée séparément, hors de ces enveloppes constantes. Le profil porte explicitement le
statut `QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE`. Les corps récurrents de 5 Mio et
la latence Internet ne sont pas qualifiés. Aucun de ces paramètres n’est appliqué
automatiquement au lanceur par la qualification.

Les enveloppes utilisent des unités explicites (`ms` ou `s`). Le plafond opérateur ne suffit
pas : l’admission conserve 10 % de marge et contrôle les transitions simulées. Une sélection
excessive est refusée ; la cadence n’est pas allongée silencieusement. Les trois opt-ins
existants et le contrôle d’espace PostgreSQL restent requis. Avec ces coûts, vingt rencontres
consomment 241 secondes sur les 270 allouables par cinq minutes ; les 24 scénarios Java de
phases et transitions passent avec ce profil exact.

La qualification est lancée explicitement, sans accès fournisseur, par :

```powershell
.\scripts\Invoke-LiveGroupedPlaywrightQualification.ps1 -PolicyVersion live-v5
```

Le profil publié à l’issue de la mesure fixe sa portée : taille des réponses, variations de
latence, coût SQL et environnement. Une mesure locale ne prouve ni la latence Internet ni
un quota accepté par SofaScore. L’objectif à moyen terme de 50–100 matchs nécessite une
qualification supplémentaire ; augmenter seulement le plafond n’active pas cette capacité.

### Appliquer un nouveau profil dans Eclipse

Arrêter la campagne avec **Arrêter toute la campagne**, attendre sa clôture, puis arrêter
le lanceur Eclipse avant de charger la nouvelle version. Après modification de son fichier
de configuration sur disque, fermer puis rouvrir Eclipse pour qu’il le relise, puis lancer
l’application avec le lanceur live. Préparer une nouvelle campagne et vérifier sa politique,
sa cadence et sa capacité avant le lancement manuel.

Une capacité nulle sur des rencontres `notstarted` indique notamment l’absence d’un profil
v6 complet. Vérifier les neuf valeurs dédiées au profil `grouped-v6` : le SHA seul ne suffit
pas si les enveloppes restent à leurs défauts. Un profil v5 ne qualifie pas v6. Les paramètres historiques v4 restent décrits
dans le [rapport v4 conservé](../validation/WO058-GROUPED-LIVE-V4-20260908.md).

### Annuler une préparation

Sur la page d’une campagne encore `PREPARED`, **Annuler la préparation** termine cette
préparation sans appel fournisseur. La campagne reste consultable avec le motif
`PREPARATION_CANCELLED`, et son lancement est désormais impossible. L’annulation peut être
répétée sans effet supplémentaire ; si le lancement a déjà gagné la course, utiliser l’arrêt
de campagne. Les observations et le manifeste restent conservés.

### Délai des collectes J5 manuelles

Une collecte J5 manuelle confirmée enchaîne statistiques, incidents et compositions pour le
même événement dans un groupe séquentiel. La protection globale courante impose désormais
deux secondes après chaque fin d'échange, même entre familles de ce groupe, et ses plafonds
minute/heure restent applicables.
Trois secondes séparent deux collectes distinctes ainsi que leurs transitions avec les
campagnes live et les autres parcours ; une interruption ne supprime pas cette protection.
La collecte manuelle reste ponctuelle et ne dépend pas du profil de capacité live.

L’écran indique l’autonomie estimée avec les appels restants et réserves, limitée aussi par la
fenêtre et les budgets individuels. Les fins de match libèrent des créneaux sans modifier
la cadence des autres. Chaque famille expose dernière
réception, dernier changement, prochaine collecte et retard. Un résultat inchangé nouvellement
reçu reste frais. Une lecture locale bloquée est annulée après dix secondes et réessayée cinq
secondes plus tard, sans perte de sélection ni d’état des panneaux.

### Portée des migrations historiques et de V40

Pour le passage historique V38 → V39, sauvegarder avec l’outillage J6 du commit `c972d63`
dans un checkout distinct et vérifier la restauration isolée V38. Les scripts de ce passage étaient V39
et fingerprintaient aussi politique, groupes et échéances ; ne pas falsifier le manifeste V38
pour franchir leur garde. La qualification Testcontainers ne migre jamais la base opérateur.
V39 est append-only et conserve les empreintes des anciennes préparations et observations.

Les scripts courants exigent désormais V44 et vérifient les six tables de résilience/diagnostic
en plus du ledger existant. Une preuve de sauvegarde/restauration V39/V40/V41 reste
historique ; elle ne qualifie pas le nouveau schéma. La procédure et les contrôles
de version figurent dans le [runbook J6](J6-BACKUP-RESTORE-AND-RETENTION.md).

Les sections historiques ci-dessous restent utiles pour v1–v3 ; leurs valeurs D et profils de
capacité ne doivent être employés ni pour annoncer une capacité v4 à une minute, ni pour
qualifier ou préparer une nouvelle campagne v6 avec protection partagée.

## Qualification hors fournisseur

La réalisation du WO utilise uniquement des données synthétiques et des bases Testcontainers.
Ces commandes sont lancées depuis le worktree du WO avec Java 25 et Docker Desktop disponibles :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Install-J3PlaywrightRuntime.ps1
.\scripts\Invoke-LivePlaywrightLoopbackQualification.ps1
.\scripts\Invoke-LiveGroupedPlaywrightQualification.ps1 -PolicyVersion live-v5
```

L'installation explicite utilise le cache Chromium dédié `.tmp/provider-playwright-browsers`.
Les lanceurs construisent le worker et exécutent uniquement les qualifications de transport
et de vue live sur HTTP loopback. Les tests standards n'ouvrent pas de navigateur. Le test J6
requiert le port 8087 libre ; une application de l'opérateur ne doit pas être arrêtée implicitement.
Lire les XML Surefire/Failsafe effectifs, les skips et le résultat du lanceur. Un packaging avec
`-DskipTests` n'est pas une qualification.

Le lanceur groupé v5 exige en plus Docker Desktop et `docker.exe` disponible, exécute un essai court
puis cinq minutes d’initialisation et au moins trente minutes mesurées sur PostgreSQL isolé,
ainsi qu'un contrôle Chromium des publications dans vingt panneaux de rencontre.
La variante historique `-PolicyVersion live-v4` conserve ses dix rencontres et ses propres
critères ; le défaut du script reste v4 et ne doit pas être omis pour qualifier v5.
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
| `sofascore.live.qualified-match-capacity` | Plafond opérateur, défaut 1 ; la capacité effective v5 vaut au plus 20 et dépend aussi de son profil. Les valeurs supérieures restent possibles pour la compatibilité historique, sans ouvrir plus de 20 cibles v5. |
| `sofascore.live.duration` | Au plus 4 h, attente avant coup d'envoi comprise |
| `sofascore.live.request-envelope` / `processing-envelope` | 10 s / 1 s, hypothèses d'admission historiques ; v5 utilise les enveloppes séparées `grouped-v5` décrites plus haut |
| `sofascore.live.qualification-sha256` | Preuve historique requise pour abaisser ces enveloppes ; ne qualifie pas les groupes v4 ou v5 |

L'exécutable Docker et le nom de conteneur servent à lire l'espace du volume PostgreSQL réel,
avec timeout et refus fermé si la mesure échoue. Le pilote historique v1–v4 à un match réserve 5 242 880 000
octets de réponses possibles ; la marge d'espace exige deux fois cette enveloppe plus 1 Gio.
Les plafonds historiques de 1 000/3 000 tentatives, comme ceux de 2 500/20 000 pour v5,
sont locaux et ne décrivent pas un quota fournisseur connu. Le plafond brut v5 est indépendant
du nombre de rencontres et vaut 15 728 640 000 octets.
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

Ouvrir **« Diagnostic de l’arrêt »** lorsqu’un diagnostic est disponible. La première
erreur de collecte et le dernier échec de clôture sont présentés séparément, avec leur
phase, code et instant UTC. Depuis V43, ces diagnostics sont conservés séparément en base
et restent consultables après redémarrage ; les observations sportives ne sont pas réécrites.
Si PostgreSQL refuse leur écriture, la session conserve la preuve en mémoire et la clôture
reste bloquante jusqu'à publication vérifiée. Les diagnostics historiques absents ne sont
pas reconstitués.
Les mêmes valeurs sont émises dans les journaux et dans `runtimeStatus.firstFailure`
et `runtimeStatus.cleanupFailure` du JSON local. Un diagnostic absent pour une ancienne
campagne n’est pas une preuve d’absence d’erreur.

Un échec mémorisé par le superviseur après le début du nettoyage peut empêcher toute
nouvelle tentative de clôture dans la même JVM. Dans ce cas, le retour de la page avec
le même bandeau ne prouve pas une libération. Conserver le diagnostic, arrêter le Lab,
puis utiliser après redémarrage le parcours **« Clôturer la session interrompue »**
ci-dessous. Le diagnostic seul ne permet jamais de forcer le garde ni de déclarer que
les processus ont disparu. Voir les [preuves et limites](../validation/WO058-LIVE-FAILURE-DIAGNOSTICS-20260908.md).

Une fois la clôture confirmée, consulter `/provider-access` : une suspension après 403/429
reste active et exige un réarmement explicite distinct. Revenir ensuite aux rencontres, préparer les cibles encore éligibles et
confirmer un nouveau lancement. Un redémarrage entre-temps relève du cas d'orphelin ci-dessous :
le nouveau processus ne possède pas le lease de l'ancien et ne peut pas utiliser cette clôture
runtime. Les codes `LIVE_PROVIDER_CLEANUP_REQUIRED` et `LIVE_LAUNCH_FAILED` distinguent le garde
à vérifier d'un autre échec local de lancement, sans exposer le texte arbitraire des exceptions.

Après crash ou nettoyage incertain, `CLEANUP_REQUIRED` conserve l'exclusion fournisseur. Aucun
délai, redémarrage ou nouvelle préparation ne la lève. Après redémarrage sous Windows :

1. Ouvrir la campagne interrompue. Le refus d’un nouveau lancement fournit aussi le lien
   **« Ouvrir la campagne à clôturer »**.
2. Utiliser **« Clôturer la session interrompue »**. La commande locale vérifie que l’ancien
   propriétaire est absent, qu’aucune collecte ne possède la session et que l’inventaire des
   processus ne signale aucun worker, pilote ou navigateur Playwright actif ou incertain.
3. Une fois la clôture confirmée, consulter `/provider-access` pour résoudre l'éventuel départ
   incertain puis décider séparément du réarmement d'une suspension. Ces opérations conservent
   les budgets et ne vérifient pas la disponibilité du fournisseur.
4. Utiliser **« Préparer une nouvelle campagne avec ces rencontres »**.
   L’éligibilité et la capacité sont vérifiées à nouveau ; vérifier puis confirmer son lancement.

L’ancienne campagne conserve son état `INTERRUPTED`, ses compteurs et ses observations.
La clôture ajoute la trace `LOCAL_CLEANUP_VERIFIED` et ne lance aucune collecte. Elle ne reprend
pas l’ancien manifeste. Le parent Maven du lanceur Eclipse est reconnu par son identité et sa
commande. Les services Java lisibles antérieurs à l’ancien propriétaire sont distingués des
workers qu’il a pu créer ; les marqueurs Playwright restent bloquants quel que soit leur âge.
Un processus actif de la session, une identité inaccessible, un autre JVM non attribuable,
un garde modifié ou une réconciliation SQL incomplète maintient le verrou avec un message explicite.
Le contrôle après redémarrage est qualifié sous Windows ; les autres systèmes refusent cette
preuve automatiquement. Aucun processus inspecté n’est arrêté par le bouton.

Le panneau de rétention de l’accueil peut être temporairement indisponible pendant
une campagne active ou une clôture non finalisée. Le tableau de bord reste accessible ;
aucun nombre de candidats ni plan de purge n’est alors calculé. Actualiser l’accueil
après la clôture pour retrouver l’aperçu. Ce message n’autorise aucune purge pendant
une collecte et ne signifie pas nécessairement qu’un navigateur tourne encore.

Le parcours opérateur du 8 septembre a été constaté jusqu’à `LOCAL_CLEANUP_VERIFIED`,
puis jusqu’à `COMPLETED` pour une nouvelle sélection de quinze rencontres. Les
[preuves de récupération](../validation/WO058-RECOVERY-DASHBOARD-20260908.md)
distinguent cet aboutissement de la cause historique de l’arrêt, toujours non établie.

Avant un prochain arrêt d’Eclipse, utiliser **« Arrêter toute la campagne »** et attendre sa
clôture. Le parcours ci-dessus reste disponible après un arrêt brutal. Le
[rapport du 08/09](../validation/WO058-ORPHAN-CLEANUP-20260908.md) décrit ses contrôles et limites.

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

Tout nouveau pilote fournisseur et toute migration de la base de l’opérateur restent des
opérations à préparer avec leur manifeste exact. Les preuves synthétiques et les mesures
loopback ne valent ni validation de fraîcheur fournisseur ni essai réel de quatre heures.
