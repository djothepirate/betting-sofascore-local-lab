# Campagnes live locales J4/J5 — architecture WO-058

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Décision applicable : [ADR-SS-005 v0.6](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md), cible de vingt rencontres soumise à une qualification v5 dédiée, en cours, avec exception distincte pour une collecte J5 manuelle.
Réalisation : [WO-058](../work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md).

## Politique courante live-v5

Les nouvelles préparations utilisent `live-v5` et un profil `grouped-v5` séparé. Le manifeste
fige 100 secondes pour les familles critiques, 300 secondes pour les compositions en jeu,
trois tours de compositions, une seconde entre groupes, 2 500/20 000 appels et le plafond
brut indépendant de 15 728 640 000 octets. La limite effective vaut au plus vingt rencontres,
selon les enveloppes qualifiées, les simulations et le plafond configuré. Le régime établi
coûte 2 appels par minute et par rencontre ; les réserves finales et budgets sont contrôlés
séparément. La cadence n’est pas ralentie pour prolonger les budgets.

Le candidat précédent à 75 secondes a tenu sa cadence native, mais les enveloppes mesurées
n’admettent que dix-sept rencontres avec la marge requise. Sa [preuve reste conservée](../validation/WO058-GROUPED-LIVE-V5-CANDIDATE-75-PROFILE-20260908.json).
La cible courante de 100 secondes suit la priorité à vingt rencontres. Son admission rejoue
vingt-quatre scénarios de phases et transitions ; sa qualification native dédiée est en cours,
avec intervalles critiques P95 ≤105 s et maximum ≤115 s. Le calcul moyen seul ne suffit pas.

`GroupedAdmissionProfile` porte la version de politique ; le constructeur historique à deux
arguments reste v4. `GroupedLiveScheduleV4` conserve son nom et accepte explicitement les deux
versions, avec périodes et nombre de tours distincts. Les autres versions utilisent toujours
l’ordonnanceur historique. Les nouveaux budgets et les paramètres de planning sont contraints
par V40 en fonction de la version ; les migrations déjà partagées restent intactes.

`openLiveGroupedV5` crée une autorité de transport dédiée. Le repère de fin d’échange demeure
global, mais la pause d’une seconde est liée à l’identité de la même instance de session v5
et à une réponse utilisable. Une nouvelle session ou un autre parcours ne peut pas réutiliser
cette dérogation, même avec le même UUID. Le constructeur de la garde générale reste à au
moins trois secondes. Les contrôles d’ordre, doublons, arrêt, admissibilité et propriété
précèdent toujours le dispatch réel.

Les politiques historiques et leur profil sont relus sans conversion. Une préparation v4
est comparée à sa configuration v4 au lancement ; une configuration v5 ne la remplace pas.
L’interface lit les paramètres figés pour afficher la cadence et calculer l’autonomie.

## Politique historique live-v4

Les préparations historiques `live-v4` sont conservées. Leur manifeste inclut un
`GroupedAdmissionProfile` distinct : preuve SHA-256 dédiée, enveloppes requête/traitement
des quatre familles, cible 60 s, LINEUPS 300 s, délai intra-groupe nul, inter-groupes 3 s,
ordre J4/incidents/statistiques/LINEUPS et marge 10 %. L’ancien profil ne qualifie jamais le
nouveau transport. Au lancement d’un manifeste v4 conservé, la capacité est recontrôlée avec
son profil v4 et le plafond opérateur. La capacité proposée aux nouvelles préparations dépend
du profil v5 ; elle reste nulle sans preuve de groupes v5.

`LiveSchedule` conserve son ordonnanceur historique pour v1–v3 et délègue v4 à
`GroupedLiveScheduleV4`. Les groupes sont phasés sur la minute, et LINEUPS sur cinq tours
après l’initialisation. Une échéance nominale ne dérive pas de la fin du précédent échange.
Les slots dépassés sont coalescés sans rafale, les retards mesurés et les surcharges durables
arrêtées. J4 décide du début, du report et de la fin ; aucune famille J5 ne produit un score
ou un statut canonique. Une composition récente ne bloque pas incidents/statistiques.

`LiveProviderSession` choisit explicitement `openLiveGrouped`. Le contexte serveur
`LiveProviderDispatchGroup` accompagne chaque appel jusqu’au superviseur. Le tracker refuse
changement d’événement, endpoint dupliqué, ordre incorrect et réutilisation d’un groupe fermé.
Le verrou d’I/O sérialise tout le transport ; le fence commun enregistre chaque fin d’échange
et ne supprime la pause que pour une continuation reconnue. Interruption, campagne suivante
ou transition vers un parcours manuel conservent donc la barrière de trois secondes. Le groupe
J5 manuel confirmé dispose de sa propre autorité pour ses continuations, sans modifier les
manifestes ou le scheduler live. Chaque admission contrôle
l’arrêt, la fenêtre et la propriété ; chaque appel est réservé, reçu et publié séparément.

V39 ajoute les tables de politique de groupe, groupe, appartenance des tentatives et planning
des familles, sans modification des anciennes migrations ou empreintes. Les observations
normalisées gardent leurs stores, provenance et transaction de publication. Le planning des
familles est persisté par l’owner et révisé avec le ledger ; les écrans n’infèrent pas une
prochaine collecte depuis la seule date de changement de valeur.

Le DTO affiche la réception, l’échéance et le retard par famille, ainsi que l’autonomie estimée
selon le budget restant, les réserves, les phases et la fin autorisée. Aucun ajustement de cadence
n’étend cette autonomie. Le lecteur JavaScript conserve ses cinq secondes et ajoute un timeout
de dix secondes sur toute lecture, corps JSON compris, puis réessaie sans effacer l’écran.
Les panneaux statistiques et leur focus restent conservés.

Une préparation non lancée s’annule par un POST local portant son hash et le jeton de contrôle.
La transition `PREPARED` → `STOPPED_OPERATOR`, motif `PREPARATION_CANCELLED`, verrouille la même
ligne de campagne que le lancement et termine ses événements. Elle ne réserve aucun appel,
ne renseigne pas de démarrage et conserve manifeste et transitions. Une répétition est sans
effet supplémentaire ; si le lancement a gagné, l’annulation de préparation est refusée.

Les sections qui suivent documentent les garanties communes et les comportements historiques
v1–v3. Leurs cadences D, ordre J5 et délai systématique par endpoint ne s’appliquent pas à v4/v5.

## Session et autorité

`LiveCampaignService.prepareSelection` lit les identités canoniques et leur provenance fournisseur,
refuse les sélections vides, dupliquées, forgées ou issues de fixtures, écarte les statuts locaux
`finished` et `postponed` avant admission et contrôle de stockage, puis réserve un manifeste
immuable valable cinq minutes. Le manifeste fige les cibles, la durée, les plafonds et le profil
de capacité ainsi que l'intervalle de collecte. Sa préparation ne crée ni worker ni contexte. Le lancement confirme son empreinte,
consomme le formulaire local, vérifie les trois opt-ins et refuse une politique modifiée depuis
la préparation.

Une rencontre d'une campagne `RUNNING` ou `CLEANUP_REQUIRED` est non sélectionnable, sauf
si son état individuel est `STOPPED_ERROR`. La règle `CampaignView.blocksSelection` est partagée
par l'affichage initial, le DTO d'actualisation, la préparation et le contrôle d'un ancien
manifeste au lancement. Un refus porte le code `LIVE_EVENT_ALREADY_IN_CAMPAIGN` et n'ouvre
aucun transport. Un simple manifeste `PREPARED` ne réserve pas la rencontre. Après une campagne
terminale, les critères ordinaires de provenance et de statut sportif s'appliquent de nouveau.
Le garde fournisseur reste l'autorité exclusive au lancement, y compris pour une sélection
autorisée après erreur : pouvoir préparer ne lève pas un nettoyage encore requis.

Une sélection entièrement terminée ou reportée ne crée pas de manifeste et affiche une explication locale,
même si les opt-ins réseau sont désactivés. Pour une sélection mixte, les exclusions sont rendues
dans le récapitulatif et la capacité porte sur les seules cibles retenues. Le statut local est
revérifié au lancement puis dans le thread propriétaire avant ouverture du navigateur : un match
devenu terminé est arrêté sous `STOPPED_ALREADY_FINISHED`, un match devenu reporté sous
`STOPPED_ALREADY_POSTPONED`, sans modifier le manifeste ni fabriquer une nouvelle observation
sportive. Si toutes les cibles sont désormais terminées ou reportées, aucun transport n'est ouvert.
Le premier J4 d'un match admis qui découvre ensuite `finished` conserve le dernier cycle J5 borné.

Le statut `postponed` intervient ainsi à trois étapes : exclusion locale pendant la préparation,
revalidation au lancement et avant ouverture du navigateur, puis arrêt individuel si une réponse
J4 `PARSED` le révèle pendant la collecte. Dans ce dernier cas, la réponse et sa provenance sont
publiées, l'état sportif reste `postponed` et l'état de collecte devient `STOPPED_POSTPONED`.
Ses tâches J4, J5 et LINEUPS prématch sont retirées, sans dernier triplet de finalisation ni reprise
automatique ; les autres rencontres peuvent continuer. Une nouvelle sélection dépendra d'une
nouvelle observation locale admissible et d'un nouveau lancement explicite. `canceled` conserve
son traitement existant : il n'est pas ajouté aux exclusions locales et un J4 qui le retourne
arrête la rencontre sous `STOPPED_REVIEW_REQUIRED` comme statut non géré.

Les pages de formulaire live utilisent `Referrer-Policy: same-origin` : Chromium conserve ainsi
l'origine exacte du POST. Les politiques d'origine/host, les jetons à usage unique et le refus
d'`Origin: null` restent applicables. La politique des autres pages reste inchangée.

Un seul thread propriétaire acquiert `ManualProviderRequestCoordinator`, puis le garde durable
commun aux parcours manuels et live. `LiveProviderSession` ouvre une fois la factory Playwright
avec EVENT_DETAILS, EVENT_STATISTICS, EVENT_INCIDENTS et EVENT_LINEUPS. Les wrappers manuels
conservent leurs politiques ; aucun endpoint, cache fournisseur ou mécanisme de repli n'est ajouté.

L'horloge du service est injectable. La session dérive ses échéances d'une origine UTC et du
temps monotone ; `LiveSchedule` reçoit explicitement les instants. Aucun ordonnanceur de
démarrage ne lance Playwright. Un watchdog créé avec la session interrompt celle-ci lorsqu'il
observe une suspension de plus de 2,5 secondes ou une divergence UTC/monotone supérieure à deux
secondes. Ce contrôle conservateur peut aussi arrêter une session après une longue suspension
du runtime ; il ne reprend jamais les tâches perdues.

## Ordonnanceur et limites historiques v1–v3

Le bloc suivant décrit les politiques historiques v1–v3 et leur admission. Les nouvelles
préparations v5 et les manifestes v4 conservés suivent les politiques distinctes décrites
en tête de ce document ; les anciennes valeurs ne qualifient pas ces groupes.

`LiveSchedule` sépare état sportif, état de collecte et complétude finale. Avec N cibles retenues,
`LiveCadence` calcule `D = max(60, 30 × (N − 1))` secondes : 60 s jusqu'à trois cibles,
90 s pour quatre, 120 s pour cinq, 270 s pour dix et 720 s pour vingt-cinq. Le plafond configuré
est indépendant de D ; une valeur positive supérieure à trois n'invalide plus la configuration.
L'entrée reste bornée à 100 identifiants. J4 vérifie le statut au départ puis à l'intervalle D
en attente. Les nouvelles préparations `live-v3` ajoutent après J4 `notstarted` une tâche
`J5_PREMATCH_LINEUPS`, initiale puis périodique à D. Les statistiques et incidents attendent
le début. Un HTTP 404 conserve son indisponibilité jusqu'à la prochaine échéance ordinaire.
Le passage J4 à `inprogress` ou `finished` supprime la tâche prématch et aligne le premier
triplet sur l'éligibilité de ses trois familles. Une LINEUPS récente peut imposer jusqu'à D
d'attente avant éligibilité, sans réserver le transport ; la file commune peut ajouter du retard.
Les politiques `live-v1`/`live-v2` ne changent pas. En jeu, chaque triplet J5 reste contigu dans l'ordre statistiques, incidents,
compositions. Les départs d'une même famille sont espacés d'au moins D. Les signaux de phase
sont produits par les normaliseurs ; le scheduler ne lit pas de JSON. Un signal de première
période entraîne un contrôle ponctuel, un signal de fin potentielle arme J4 à l'intervalle D,
et le secours intervient après `max(300 s, D)` depuis le dernier J4 réussi.
Seul un J4 `finished` ouvre la finalisation. Son dernier triplet respecte encore l'espacement,
la fenêtre et les budgets ; une borne peut laisser la finalisation incomplète.

Le coordinateur transport conserve trois secondes après la fin de l'échange précédent. Les
échéances sont coalescées, sans rafale de rattrapage. Deux cycles successifs non servis avant
leur échéance suivante arrêtent la campagne pour capacité, y compris au milieu d'un triplet.
L'admission simule le cas récurrent de trois J5 et un J4 par match/intervalle D, en nanosecondes.
La charge doit tenir dans D ; l'heure de fin exclusive peut tronquer le dernier intervalle,
sans prolongation de la campagne. Le profil initial
conservateur compte 10 s de requête, 1 s de traitement et 3 s de délai, soit 56 s pour un match.
Une sélection multiple effective exige une preuve de qualification renseignée et un profil qui
passe l'admission. Relever seulement le plafond ne réclame pas cette preuve pour une sélection
unique avec l'enveloppe conservatrice ; abaisser cette enveloppe exige toujours sa preuve.
Le hash déclaré de qualification doit renvoyer à une preuve revue ; sa forme seule ne prouve
aucune performance du fournisseur.

Les paramètres du palier sont liés explicitement aux variables
`SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY`, `SOFASCORE_LIVE_REQUEST_ENVELOPE`,
`SOFASCORE_LIVE_PROCESSING_ENVELOPE` et `SOFASCORE_LIVE_QUALIFICATION_SHA256`.
La [preuve des paliers du 7 septembre](../validation/WO058-MULTIMATCH-CAPACITY-20260907.md)
relie les mesures Chromium et les simulations aux profils 2 × 4 × (3 + 1 + 3) = 56 s
et 3 × 4 × (0,75 + 1 + 3) = 57 s par minute. Ces preuves historiques restent figées.
Le [correctif adaptatif](../validation/WO058-ADAPTIVE-CAPACITY-20260907.md) couvre ensuite les
sélections de 1 à 25 avec un profil de 1 s d'échange, 1 s de traitement et 3 s de délai,
soit 20 × N secondes par intervalle. La limite initiale reste le défaut ; son changement
est une configuration opérateur explicite. Le délai de requête du profil est une hypothèse de
charge, distincte du timeout de transport qui reste borné à dix secondes. Le contrôle des retards
continue de s'appliquer aux conditions réelles. La preuve de volume mesure des JSON synthétiques
minimaux complétés d'espaces à 5 Mio ; elle ne constitue pas une garantie de latence fournisseur
ou de coût SQL pour toutes les formes de réponse.

La fenêtre maximale est de quatre heures depuis le lancement. Les 1 000 tentatives par match
et 3 000 par campagne incluent les appels annulés après réservation et les issues inconnues.
Quatre places sont réservées pour une vérification J4 et les trois dernières familles J5.
Le plafond d'octets réserve le maximum de 5 Mio par réponse sans présumer de déduplication.
La sonde lit l'espace du volume PostgreSQL via une commande Docker fixe, sans shell intermédiaire,
avant préparation/lancement et avant chaque réservation. Elle exige deux fois l'enveloppe brute
restante plus au moins 1 Gio de marge ; une mesure indisponible refuse l'opération.

## Frontière d'arrêt

Pendant le délai réseau et immédiatement avant le dispatch, l'admission vérifie la session,
l'événement, la fenêtre et la propriété. Le contrôle SQL précède les verrous courts de départ :
aucun verrou d'arrêt du superviseur n'est détenu pendant une requête PostgreSQL. Le dernier
contrôle local et l'écriture de la commande GET sont synchronisés avec les arrêts.

L'arrêt individuel retire les tâches futures ; un GET déjà engagé peut finir sous timeout et
être persisté sans réactiver le match. L'arrêt global interdit les nouveaux départs et demande
l'annulation du transport partagé. Le nettoyage existant vérifie l'arbre des processus possédés.
Une exclusion durable reste occupée si la fermeture, la propriété ou le stockage sont incertains.
Au redémarrage, seule l'absence prouvée du processus propriétaire (PID et date de création)
permet de marquer une campagne orpheline interrompue. Une identité inaccessible n'est pas une preuve.
Le garde devient alors `CLEANUP_REQUIRED` ; aucune expiration ne le libère automatiquement.

Une défaillance SQL pendant l'arrêt ne devient pas un succès en mémoire. L'observation runtime
est exposée séparément de l'état durable et de sa révision : collecte arrêtée, clôture requise
ou clôture en cours. L'écran accepte une évolution de cette observation à révision SQL égale,
mais refuse un état durable plus ancien. Les transitions ne sont mises en cache qu'après leur
persistance réussie. Les commandes d'arrêt individuelles sont retirées quand la collecte cesse.

Le thread propriétaire conserve le lease en cas d'échec et attend une commande explicite de
clôture. Cette commande ne rouvre aucun contexte et ne relance aucun GET. Elle vérifie l'absence
du superviseur, relit l'ownership durable, conserve les réceptions et publie un résultat
`UNKNOWN / LOCAL_CLEANUP_UNRESOLVED` pour chaque seule tentative restée sans résultat, puis les
états terminaux. Le lease est libéré en dernier. Les demandes concurrentes sont fusionnées ;
il n'y a ni minuterie de réessai SQL ni transfert du verrou à un autre thread.

Si la libération SQL a été validée mais sa réponse perdue, une clôture explicite ultérieure
n'accepte un garde `FREE` que dans la même génération, avec tous les champs propriétaires vidés
et aucun superviseur actif. Un garde illisible ou une génération différente conserve le blocage.
Cette vérification locale ne constitue pas une libération générique de garde orphelin après crash.

Après redémarrage, une commande distincte `POST /live-campaigns/{id}/finalize-interruption`
réalise la clôture explicite du garde orphelin. Le formulaire local lie la campagne et la
génération observée. Le coordinateur exclut les appels manuels/live pendant la preuve et la
transaction ; aucun lease fournisseur ni contexte Playwright n’est créé. Le propriétaire
précédent est contrôlé par PID et date de création. Une sonde Windows bornée à dix secondes
classe ensuite les processus Java, Node et Chromium pour rechercher un worker ou descendant
potentiel. Le rapprochement du seul JVM appelant avec CIM respecte la précision milliseconde
de `ProcessHandle`; celui de l’ancien propriétaire Java conserve la précision persistée.
Le parent Maven du lanceur `spring-boot:run` est reconnu uniquement par son identité prouvée
dans la chaîne des ancêtres et par sa commande Maven ; un worker reste bloquant même ancêtre.
Une autre JVM lisible née strictement avant l’ancien propriétaire ne peut pas être un worker
neuf créé par sa session ; elle est donc écartée des candidats ambigus. Une égalité de dates
ne suffit pas. Les marqueurs Playwright restent bloquants indépendamment de l’âge du processus.
Les lignes de commande restent dans le processus de contrôle : seuls `ABSENT`, `ACTIVE` ou
`UNVERIFIED` sont retournés. Un JVM non attribuable, un inventaire inaccessible ou une autre
plateforme refuse la libération. Aucun processus inspecté n’est tué.

`completeOrphanCleanup` verrouille le garde avant la campagne et compare son identité complète,
sa génération et sa date de changement. Il exige une campagne lancée terminale, des événements
terminaux, aucune tentative non résolue ni échéance future, et aucune autre campagne active.
La transition append-only `LOCAL_CLEANUP_VERIFIED` contient l’empreinte SHA-256 du garde vérifié ;
elle est validée dans la même transaction que sa libération. L’état historique `INTERRUPTED`,
les résultats, observations et compteurs restent inchangés. Une répétition après réponse perdue
accepte seulement une libération prouvée de la même génération. Une nouvelle acquisition interdit
cette répétition. Les tables existantes portent ce parcours sans migration.

Après la clôture, l’interface propose une nouvelle préparation à partir des identités du
manifeste historique. Les exclusions `finished`/`postponed`, l’admission et la confirmation
habituelles sont réappliquées. La lecture de la page et la préparation ne déclenchent aucune collecte.

Avant la première réconciliation, `requireCleanup` prend le verrou SQL du garde également
utilisé par le lancement et contrôle propriétaire et génération. La lecture suivante distingue
ainsi un lancement validé dont la réponse a été perdue d'une préparation jamais lancée ; elle
ne peut pas utiliser une ancienne vue PREPARED pendant qu'un commit de lancement est en vol.
Si cette barrière échoue, la clôture reste en attente. Elle n'est pas répétée après réconciliation
réussie, pour permettre la vérification d'une libération déjà validée dont la réponse a été perdue.

## Transactions et provenance

La migration additive V33 crée sept tables `live_*` et `provider_campaign_guard`, distinctes
de J8. Les cibles et le manifeste sont immuables ; appels, dispatchs, réceptions, résultats et
transitions conservent leurs contraintes d'identité, d'unicité et de génération propriétaire.
Les curseurs par famille sont reconstruits depuis les occurrences et résultats référencés.

V34 est append-only : le plafond devient un entier positif, l'ordre des cibles admet jusqu'à
100 entrées, et `cycle_interval_seconds` est protégé par le trigger d'immutabilité du manifeste.
Les anciennes lignes reçoivent le défaut 60 sans réécriture de leur SHA ni des autres colonnes.
Un manifeste `live-v2` doit porter exactement D calculé sur ses cibles. D entre dans son SHA.
V35 ajoute la même contrainte pour `live-v3`, sans transformer les lignes antérieures.
La version de politique entre dans l'empreinte ; les nouveaux manifestes ne modifient pas
les campagnes déjà préparées ou lancées. Le replay conserve `live-v2` par défaut et accepte
explicitement `live-v3` pour qualifier la nouvelle séquence.
V36 ajoute le parseur `event-incidents-v16` à la contrainte J5 : la variante
`inGamePenalty/awarded` conserve son sens d'attribution et n'invente pas un résultat de tir.
Les versions et observations antérieures restent intactes ; le snapshot 2340 est seulement
rejoué hors persistance pour la [preuve de correction](../validation/WO058-INCIDENT-AWARDED-20260907.md).
V37 ajoute `event-incidents-v17` pour le seul motif de carton `Professional handball`,
observé dans le snapshot 2427 d'Elche–Real Sociedad. Le corps et le rejet V16 historique sont
conservés ; le replay correctif demeure local, sans réécriture ni nouvelle collecte.
V38 ajoute au détail J4 les colonnes nullables `is_awarded`, `home_display_score` et
`away_display_score` et admet `event-details-v3`, sans backfill. Le contrat, ses bornes et
la compatibilité des hashes historiques sont décrits dans l'[architecture J4](J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md).

Le lancement conserve D même si certaines cibles sont devenues `finished` ou `postponed` ; l'admission vérifie
la charge restante sur cet intervalle consenti, sans accélérer la campagne en cours.

1. Une transaction réserve la tentative et consomme son budget avant tout réseau.
2. Une autorisation de dispatch est tracée. Cette date ne revendique pas un départ on-wire.
3. Le GET s'effectue sans transaction ni verrou SQL.
4. Une transaction sauvegarde le brut, son occurrence et le rattachement à l'appel.
5. Le parsing s'effectue hors transaction.
6. Une publication atomique rattache les observations normalisées, projections, complétude et
   état de collecte. Un échec conserve la réception déjà validée à l'étape 4.

La vue courante suit les occurrences A → A et A → B → A, sans réécrire l'heure ni la
classification d'un snapshot dédupliqué. Le score est une projection J4 versionnée, datée de sa
réception ; aucun score courant n'est reconstruit depuis les incidents. Absent, null, zéro et
indisponible restent distincts. Les signaux J5 conservent une clé indépendante de l'ordre du tableau.

Les nouvelles publications J4 utilisent `event-details-v3` et la projection `j4-live-score-v2`.
Cette projection conserve les marqueurs `ABSENT`, `NULL` et `VALUE` des objets score et de leurs
champs, et ajoute `isAwarded` avec les mêmes marqueurs. Les anciennes projections
`j4-live-score-v1` restent stockées telles quelles. Le curseur compare le hash normalisé,
la version et le contenu de projection ; aucune publication historique n'est recalculée sous
`j4-live-score-v2`.
Les versions des projections statistiques, incidents et compositions ne changent pas.

Un contenu déjà purgé par J6 ne peut pas être silencieusement réhydraté via sa clé dédupliquée :
`LIVE_RAW_PREVIOUSLY_PURGED` fait échouer la nouvelle réception atomiquement et arrête la campagne
comme défaut de stockage. Les dates et preuves historiques demeurent inchangées. La rétention
refuse une campagne active ou un garde incertain ; la sauvegarde J6 couvre également les huit
tables du lot et leurs empreintes. Voir le [runbook J6](../runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md).

## Classification et consultation

Un JSON de schéma métier incompatible n'arrête que son match après contrôle de sécurité et
corrélation d'identité. J4 404, statut non géré ou régression sportive demandent une revue du match.
J5 404 conserve l'indisponibilité et attend le cycle normal suivant. Transport, refus HTTP hors
404, HTML/challenge, identité, contenu sensible, exception interne, stockage et anomalie non
classifiable arrêtent globalement. La portée live est distincte du statut renvoyé par un parseur.

Pour chaque famille J5 404, la publication atomique conserve une observation `UNAVAILABLE`,
le résultat `ENDPOINT_UNAVAILABLE / HTTP_404 / NONE` et la réception courante. Le snapshot brut
inséré est classé `ENDPOINT_UNAVAILABLE` avec `error_code=null`, conformément au contrat de
persistance ; un snapshot dédupliqué conserve sa classification historique. Le curseur de
dernière donnée lisible n'est pas effacé. L'indisponibilité n'ajoute pas de tentative immédiate :
les autres familles et rencontres continuent, puis l'endpoint est réinterrogé à son échéance.

MVC/Thymeleaf rend la préparation, les commandes et l'historique. Les GET d'état ne font que des
lectures locales. Le script de même origine lit toutes les cinq secondes, suspend son timer sur
onglet masqué, ignore une ancienne révision et conserve focus, sélection et dernières données.
Une case devenue non sélectionnable est désactivée et décochée ; le compteur exclut les cases
désactivées. `STOPPED_ERROR` rend la case éligible à nouveau sans la recocher. Une fixture
synthétique reste désactivée indépendamment des états de campagne.
Les DTO excluent payloads et identité de processus. Le retard d'autorisation transport reste
explicitement distinct d'une mesure on-wire. Les familles deviennent en retard après deux
intervalles sans succès. Pour v1–v3, J4 utilise D en attente/contrôle de fin et `max(300 s, D)`
en jeu ; J5 utilise D en jeu ; LINEUPS utilise aussi D en attente de début pour `live-v3` seulement.
D est lu dans le manifeste historique, pas dans la configuration courante. Pour v4/v5, la vue
utilise l'intervalle et la prochaine échéance de chaque famille persistée : respectivement
60/100 secondes pour les familles critiques et 300 secondes pour LINEUPS en jeu, 60/100 secondes
pour J4 et LINEUPS en attente de début. Le retard nominal de cette échéance est affiché
séparément de l'âge de réception et du délai d'autorisation transport.
La fin du suivi fige leur âge à la transition terminale persistée.
Le libellé sportif affiche la description J4 pour `inprogress`, avec repli sur le type si elle
manque. Pour `finished` avec `isAwarded=true`, il affiche « Victoire sur tapis vert », tout en
conservant le type technique `finished`. Le résultat affiche uniquement la paire
`homeScore.display – awayScore.display`, chaque entier étant borné à 0..999 ; si un côté manque,
il affiche `—`. Aucun repli vers `current`, `normaltime`, `penalties` ou les incidents ne comble
cette absence. Le drapeau absent ou `false` ne produit pas de libellé d'attribution.
Type, score, description et provenance restent associés à la même observation de campagne.
Une observation manuelle plus récente ne remplace pas silencieusement cette preuve.
La présentation lit le détail V3 référencé par le curseur, ou la projection J4 conservée par ce
curseur lorsqu'un détail V3 correspondant n'est pas disponible. Une projection historique qui
contient les deux champs `display` peut donc encore afficher sa paire, sans inventer un drapeau
d'attribution manquant. L'affichage du score n'altère ni les échéances ni la machine à états.

Les statistiques de ce curseur sont présentées par `StatisticsPresentation` et le fragment
Thymeleaf partagé `fragments/statistics` dans les vues campagne live et statistiques J5.
Le détail de rencontre reçoit le même composant par son polling JavaScript et conserve son lien J5.
Les périodes reçues sont sélectionnables (ALL par défaut lorsqu'elle existe) et les groupes
conservent leurs métriques source. Aucune somme entre périodes n'est calculée. Les valeurs source
restent visibles ; possession valide et fractions X/Y calculables disposent de jauges HTML
`meter`, sans styles inline. Une absence, zéro et 0/0 restent distincts. `statistics.js` conserve
le sélecteur, le focus et la période tant qu'elle reste présente ; sinon le repli est annoncé.
Sans JavaScript toutes les périodes restent lisibles dans les pages campagne et statistiques J5.
Les périodes et leurs groupes utilisent des `details/summary` imbriqués, ouverts par défaut.
Le bandeau vert replie la période entière ; le bandeau gris replie seulement son groupe.
Ces actions fonctionnent au clic et au clavier, même sans JavaScript. Le rafraîchissement
conserve les états ouverts/repliés par période et nom de groupe, séparément pour chaque composant,
et restitue le focus d'un en-tête remplacé. Les groupes conservent leur état lorsque leur période
est fermée puis rouverte. Un élément nouvellement reçu est ouvert par défaut ; aucun état n'est
écrit en base ni conservé après rechargement complet de la page.
Cette présentation ne déclenche aucun appel fournisseur et ne modifie ni parseur
statistique, ni normalisation, ni provenance. Le libellé sportif fournisseur `2nd half` est conservé.

La fraîcheur d'exploitation ci-dessus mesure la santé de collecte. Elle ne qualifie pas la
fraîcheur métier d'un benchmark. L'outil offline `scripts/Analyze-LiveTemporalEvidence.py`
recalcule à un instant T l'âge réel de chaque couple match/famille, les réceptions identiques
consécutives et les dispersions temporelles. Il distingue les quatre familles d'une comparaison
dynamique J4/statistiques/incidents et laisse les classes `UNCLASSIFIED`. Les résultats résolus
après T restent exclus. Le changement sémantique reprend la clé du curseur (hash normalisé,
version et contenu de projection) ; le changement brut ne suffit pas à lui seul.
La CSP autorise `script-src 'self'` sur les pages live. Les lectures héritent de la restriction
`default-src 'self'` commune au site ; aucune directive `connect-src` explicite n'est déclarée.
Les scripts inline et l'évaluation dynamique restent interdits.

`LiveReplayRunner` rejoue des réponses synthétiques hachées, une horloge et des arrêts opérateur
avec le vrai scheduler et le normaliseur pur. Ses identifiants de trace ne sont pas des IDs SQL.
Il ne publie aucune observation ni validation humaine. Les tests de stockage, Web, replay et
Chromium restent des preuves séparées dans le [rapport du lot](../validation/WO058-LIVE-J4-J5-IMPLEMENTATION-20260907.md).
