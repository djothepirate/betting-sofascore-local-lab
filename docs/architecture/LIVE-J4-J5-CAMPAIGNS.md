# Campagnes live locales J4/J5 — architecture WO-058

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Décision applicable : [ADR-SS-005 v0.3](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md), capacité adaptative et compositions avant le début.
Réalisation : [WO-058](../work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md).

## Session et autorité

`LiveCampaignService.prepareSelection` lit les identités canoniques et leur provenance fournisseur,
refuse les sélections vides, dupliquées, forgées ou issues de fixtures, écarte les statuts locaux
`finished` avant admission et contrôle de stockage, puis réserve un manifeste
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

Une sélection entièrement terminée ne crée pas de manifeste et affiche une explication locale,
même si les opt-ins réseau sont désactivés. Pour une sélection mixte, les exclusions sont rendues
dans le récapitulatif et la capacité porte sur les seules cibles retenues. Le statut local est
revérifié au lancement puis dans le thread propriétaire avant ouverture du navigateur : un match
devenu terminé est arrêté sous `STOPPED_ALREADY_FINISHED`, sans modifier le manifeste ni fabriquer
une nouvelle observation sportive. Si tous sont terminés, aucun transport n'est ouvert.
Le premier J4 d'un match admis qui découvre ensuite `finished` conserve le dernier cycle J5 borné.

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

## Ordonnanceur et limites

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
Le lancement conserve D même si certaines cibles sont devenues `finished` ; l'admission vérifie
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
Les DTO excluent payloads et identité de processus. Le retard affiché est celui de l'autorisation
transport, explicitement distinct d'une mesure on-wire. Les familles deviennent en retard après
deux intervalles sans succès : J4 utilise D en attente/contrôle de fin et `max(300 s, D)` en jeu ;
J5 utilise D en jeu ; LINEUPS utilise aussi D en attente de début pour `live-v3` seulement.
D est lu dans le manifeste historique, pas dans la configuration courante.
La fin du suivi fige leur âge à la transition terminale persistée.
Le libellé sportif affiche la description J4 pour `inprogress`, avec repli sur le type si elle
manque. Type, score, description et provenance restent associés à la même observation de campagne.
Une observation manuelle plus récente ne remplace pas silencieusement cette preuve.

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
