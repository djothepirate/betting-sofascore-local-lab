# Campagnes live locales J4/J5 — architecture WO-058

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Décision courante : [ADR-SS-005 v0.11](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md). Le correctif de réponses lentes/timeouts isolés et le calendrier v7 restent qualifiés dans leurs périmètres historiques. Le premier lot de résilience et son profil nominal restent également qualifiés dans leur portée : [rapport initial](../validation/WO-058-provider-resilience-qualification-20260909.md), [qualification temporelle v6](../validation/WO058-LIVE-V6-CAPACITY-20260909.md). Le [rapport du correctif](../validation/WO058-SLOW-TIMEOUT-RECOVERY-20260909.md) conserve séparément les validations finales réussies et leurs étapes intermédiaires. La révision V8 est autorisée par la demande propriétaire du 10 septembre et sa [qualification loopback locale](../validation/WO058-LIVE-V8-CAPACITY-20260910.md) est achevée ; elle ne vaut ni acceptation ni seuil du fournisseur. Les preuves v4 à V9 restent historiques.
Réalisation : [WO-058](../work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md).

## Révision live-v10 — huit rencontres et pression locale durable réduite

`live-v10` est la politique des **nouvelles** préparations. Elle remplace `live-v9` pour
une nouvelle sélection, sans réécrire, convertir ni réinterpréter un manifeste, une preuve,
un résultat de campagne ou une réservation V9 déjà persistés. Les règles métier J4/J5,
la présentation des compositions et la révalidation conditionnelle décrites pour V9 restent
la référence fonctionnelle héritée ; cette révision ne modifie que l'admission et la borne de
pression locale associées aux nouvelles préparations.

La sélection est limitée à **huit rencontres**. Avec les quatre familles (`EVENT_DETAILS`,
`EVENT_INCIDENTS`, `EVENT_STATISTICS`, `EVENT_LINEUPS`) planifiées au plus une fois par minute,
la charge nominale maximale est de **32 départs comptabilisés par 60 secondes** (`8 × 4`).
Le profil durable refuse tout départ qui ferait dépasser **35 départs comptabilisés sur toute
fenêtre glissante de 60 secondes** ou **2 100 sur toute fenêtre glissante d'une heure**. Le
lancement exige localement la place pour sa vague initiale de `4 × N` départs, soit 32 à la
capacité maximale. Chaque réservation demeure sérialisée et persistante jusqu'à sa clôture
prouvée : changer d'UUID, relancer l'application ou ouvrir une autre campagne ne recrée aucune
marge.

Ces chiffres sont des bornes internes de sécurité et de diagnostic. Ils ne constituent pas une
mesure, une garantie, un quota ou une autorisation de SofaScore, et ne modifient ni le transport
Playwright local, ni l'opt-in opérateur, ni les protections contre les réponses 403/429. Ils
n'autorisent aucun proxy, changement d'adresse, furtivité, réutilisation de cookie, contexte
persistant, défi ou reprise automatique. Les départs conditionnels qui aboutissent à un `304`
restent soumis à la même isolation de contexte et à la même preuve de cache que V9 ; la
révision ne transforme pas le cache en donnée fraîche ni en permission de relancer une collecte.

V10 possède un profil de qualification SHA-256 et huit enveloppes qui lui sont propres. Sans ce
profil complet et vérifié, sa capacité est zéro ; les valeurs V8 ou V9 ne constituent jamais un
repli. La qualification attendue couvre le replay hors réseau, les fenêtres durables de 35/60 s
et 2 100/h, la capacité de huit, la persistance PostgreSQL append-only et les régressions V9.
Elle reste distincte de la validation opérateur, d'une campagne fournisseur et de la clôture du
Work Order.

## Révision live-v9 — faits J4, réduction bornée des J5 et activation manuelle distincte (historique)

Les campagnes `live-v9` déjà préparées conservent la borne de dix rencontres et l'enveloppe
stricte de pression V8, mais placent ou suppriment les J5 après un J4 normalisé. Le
J4 conserve séparément, dans sa projection, les présences et valeurs de `finalResultOnly`,
`detailId`, `hasEventPlayerStatistics`,
`tournament.uniqueTournament.hasEventPlayerStatistics`, `status.description` et
`statusReason`. Le scheduler
utilise le type `LiveJ4ControlFacts`, pas le JSON textuel de la vue, pour chacune de ces
décisions.

La cible n'est pas rejetée avant une lecture J4 parce qu'aucune source durable ne prouve alors
`finalResultOnly`. Dès que le premier J4 l'observe vrai, V9 arrête cette cible sans J5. Les
statuts `notstarted`, `postponed` et `delayed` excluent incidents/statistiques ; ceux-ci ne
redeviennent admissibles que pour `inprogress`, `interrupted`, `canceled` ou `finished`.
Les compositions sont appelables lorsque le fait événement est vrai ou absent, et ne le sont
pas lorsqu'il est explicitement faux. `detailId` nul, inconnu ou incompatible ne peut pas être
assimilé à l'absence documentée et arrête la cible en revue sûre. Les autres faits conservent leur
sémantique propre : seul `finalResultOnly=true` arrête la cible, et seule la description explicite
`halftime` engage la pause.

`detailId=1` garde le groupe normal. Avec un `detailId` réellement absent, V9 compte seulement
les résultats dont le code est exactement `HTTP_404` pour J5 statistiques. Trois occurrences
consécutives suspendent cette famille; tout résultat différent réinitialise le compteur. Le
premier J4 `interrupted`, `canceled` ou `finished` peut ensuite ajouter un unique dernier J5
statistiques, sans boucle de reprise après un doublon, une erreur ou un timeout. Cette règle
spécifique évite le backoff générique qui empêcherait les deuxième et troisième observations.

Lorsqu'un J4 `inprogress` porte `status.description=halftime`, V9 ne propose aucun endpoint
pendant 15 minutes. À l'issue, il envoie un J4 de vérification seul; si la description ne vaut
pas `2nd half`, il reste en J4 seul toutes les minutes. La confirmation de la seconde mi-temps
recrée la cadence habituelle; un statut terminal conserve la priorité et déclenche seulement les
familles finales admissibles.

Un J4 qui fait passer la rencontre de `inprogress` à `suspended` ne l'arrête pas. Il retire les
trois familles J5 et conserve un J4 seul à chaque minute jusqu'à une réponse J4 de référence
`inprogress`, qui rétablit alors les familles admissibles. `event.statusReason` est une donnée
typée distincte de la description : la vue ne l'affiche que lorsque ce même statut de référence
est `suspended`, ne déduit aucun texte si elle est absente ou nulle, et efface la raison devenue
obsolète à la reprise.

V9 dispose d'un profil de qualification indépendant, vide par défaut. Il n'est donc jamais
autorisé de recopier le hash V8 pour le rendre exécutable. En revanche, sa pire vague reste les
quatre familles V8 : elle réutilise le même `DepartureProfile.LIVE_V8` pour le ledger de pression
et les mêmes quatre enveloppes, fences de 500 ms et réserve inter-groupe de 1 s. V52 admet le
manifeste V9 et exprime cette identité de pression sans modifier V48/V50 ni les départs
historiques. Ce partage ne crée ni hausse de budget, ni nouveau transport, ni sortie réseau.
Les décalages entre familles restent déterministes et bornés par ces enveloppes : à la capacité
de dix rencontres, la réservation remplit les 60 secondes. Le terme « jitter » désigne donc la
variabilité locale déjà absorbée par les fences et la réserve ; aucun délai aléatoire ou adaptatif
n'est introduit, car il ferait sortir la planification de sa borne qualifiée.

### Révalidation HTTP conditionnelle V9, reprise par V10 dans le même périmètre

Le transport V9 — et V10 après qualification de son profil indépendant — peut porter
`If-None-Match` seulement après qu'une réponse de la même famille,
de la même rencontre et de la même campagne a été reçue, lue intégralement, normalisée et
publiée avec succès. Le validateur associé est conservé en mémoire du contexte Playwright neuf,
non persistant, puis supprimé à la fin de la campagne. Il ne rejoint ni les snapshots, ni les
observations, ni les journaux, ni le parcours J5 manuel, et il ne survit ni à un redémarrage ni à
une autre campagne. Le périmètre se limite aux quatre routes live J4 détails et J5 incidents,
statistiques et compositions ; les chemins et commandes historiques gardent leur protocole.

Un `304 Not Modified` avec ce contexte exact est une revalidation de la donnée déjà acceptée,
pas une nouvelle réception de contenu. Le Lab ne persiste pas de corps brut, ne crée pas de
snapshot, ne normalise pas une seconde fois et n'ajoute pas d'octet métier reçu. La dernière
réponse `200` réussie reste la source de la projection, avec sa date de réception d'origine.
Le `304` est néanmoins la réponse à une requête `If-None-Match` qui a quitté le Lab. Son départ
physique et sa tentative restent des faits append-only d'audit et de protection de cadence,
portés par le diagnostic `COMPLETE/304`; ils ne sont pas versés dans les compteurs fonctionnels,
le budget, la pression ou les compteurs affichés de la campagne. La ligne append-only déjà prévue
dans `live_call_result`, `NOT_MODIFIED/NONE/HTTP_304`, est la preuve de libération idempotente de
la révalidation. Cette sémantique suffit sans migration des lignes historiques. Le mécanisme ne
doit ni attribuer une fraîcheur de donnée fictive ni déduire une meilleure acceptation fournisseur.
Lorsque la preuve durable complète est présente, la vue peut toutefois distinguer la fraîcheur du
contrôle de cache, datée de `headersReceivedAt`, de la dernière réception `200`. Ce contrôle peut
rendre une famille fraîche sans modifier son âge depuis réception ni créer une nouvelle donnée.

Un `304` reçu sans contexte mémoire correspondant est non vérifiable : il ne passe pas dans le
parseur, ne réutilise pas arbitrairement une observation historique et mène à un arrêt sûr sans
retry, réarmement ni boucle de collecte. Le mécanisme n'ajoute ni endpoint, proxy, cookie,
réutilisation de session, résolution de challenge ou promesse de réduction des refus HTTP.

Les cartes joueurs utilisent la capacité tournoi J4 vraie comme unique permission de détail. Si
elle est absente, nulle ou fausse, la carte est statique avec une information explicite. Si J5
statistiques est indisponible mais J5 incidents et lineups sont lisibles, les incidents peuvent
enrichir uniquement la présentation d'une carte sans statistiques lineups, via la clé stricte
`(LineupSide, providerPlayerId)`. Les buts, passes, cartons et substitutions complètes sont
affichés avec leur minute; aucune observation, statistique réelle ou composition n'est modifiée.
Les sections de composition rendent déjà le rôle collectif : le poste individuel est donc retiré
de l'affichage visuel de chaque carte tout en restant disponible aux technologies d'assistance.
Cette règle est commune à la campagne et à la vue J5 manuelle. De la même manière, les deux vues
masquent le libellé de pays seulement après le chargement vérifié du SVG local ; le nom français
reste accessible et redevient le repli visible sans drapeau exploitable, après erreur de
chargement, sans JavaScript ou en contraste forcé.

`PROVIDER_CLOCK_REGRESSION` reste un refus local de planification avant le transport. Il ne
représente ni un HTTP 403 ni une action à contourner : le diagnostic conserve l'arrêt et requiert
une correction explicite de l'horloge locale avant une prochaine opération d'opérateur.

## Révision live-v8 — profil local historique, activation manuelle distincte

`live-v8` reste la politique historique qualifiée pour les campagnes déjà persistées. Elle vise **dix
rencontres au plus** et une cadence de départ normale de **60 secondes au plus par couple
rencontre/famille** en jeu. Les familles sont `EVENT_DETAILS`, `EVENT_INCIDENTS`,
`EVENT_STATISTICS` et `EVENT_LINEUPS`. Ce contrat est un objectif de départ local : il ne
déduit jamais la fraîcheur d'une donnée de l'heure de lecture de l'écran, d'une réservation
ou d'un corps qui n'est pas reçu intégralement.

Le [profil V8 versionné](../validation/WO058-GROUPED-LIVE-V8-PROFILE-20260910.json) qualifie une
capacité locale de dix rencontres lorsqu’il est chargé avec ses neuf entrées cohérentes : une
empreinte `SOFASCORE_LIVE_GROUPED_V8_QUALIFICATION_SHA256` et huit enveloppes
requête/traitement. Sans cet ensemble exact, l'admission retourne toujours une capacité zéro.
Un profil V7 qualifié à trois rencontres, ou tout profil v4–v6, ne constitue pas un repli
acceptable. Les campagnes déjà persistées gardent la version et les règles de leur manifeste ;
V8 ne les convertit pas.

Le planificateur V8 doit dériver des slots immuables par rencontre et famille à partir des
quatre enveloppes admises, d'un **fence local de 500 ms** après chaque fin d'échange
prouvée, puis d'une **réserve statique inter-groupe de 1 s** ajoutée par V51. Cette réserve
préserve le fence effectif de 500 ms lorsqu'un J4 normal consomme son jitter local borné de
500 ms. Tant que
les échanges demeurent dans ces bornes, les quatre suites de départ d'une rencontre restent
dans leur vague de 60 s malgré des coûts variables d'une famille à l'autre. Le J4 normal est
offert 500 ms avant l'échéance `REQUEST_SENT` : une émission authentifiée plus tardive quitte
la voie stricte vers `WAITING_CADENCE_RECHECK`; son groupe J4/J5 est conservé comme manqué et
ne peut pas être rephasé comme un cycle frais. Aucun J5 de rattrapage n'est lancé. Le prochain
J4 est offert sur la prochaine phase stable dérivée de ce départ authentifié (`REQUEST_SENT +
60 s − 500 ms`) ; cette reprise de cadence ne prend pas le backoff de cinq minutes réservé aux
timeouts et aux dépassements d'enveloppe. Les J5 ne reprennent qu'après ce nouveau J4 réussi.
Si une réponse dépasse son enveloppe, l'événement est une exception explicitement visible et
la prochaine action est différée selon les règles applicables : le système ne prétend pas que
la fraîcheur est tenue. Une réception fournisseur et la complétude du corps restent des faits
distincts du départ normal.

Le transport demeure séquentiel, avec un contexte non persistant créé uniquement par un
lancement opérateur. Pour V8, les départs sont bornés à **45 par 60 secondes glissantes** et
**2 756 par heure glissante**, avec les comptes persistants entre campagnes. La borne
temporelle V51 est distincte : `N × réserve de groupe <= 60 s`. La planification horaire de
dix rencontres utilise au plus **2 480 / 2 756** départs, soit 276 départs horaires laissés
non alloués. Le fence local effectif ajouté par ce profil vaut 500 ms ; l'attente observée peut être
plus longue lorsqu'un slot, un budget, une réponse lente, un 404 ou une suspension l'exige.
Ces chiffres sont des limites locales de pression, pas une mesure ou une autorisation du
fournisseur.

Après acquisition du `CampaignLease` exclusif, le lancement V8 exige que les fenêtres
persistantes disposent localement d'une marge libre de **`4 × N` départs** pour sa vague
initiale runtime (40 pour dix cibles). Cette prélecture ne réserve aucun départ : chaque
émission reste réservée atomiquement juste avant le transport. Elle établit la marge locale
conservatrice de la vague phasée, sous les enveloppes V8, et ne garantit ni une réponse ni
l'acceptation du fournisseur. La voie de qualification `INITIAL_COLD_START_STRESS` — quarante
réponses de 5 Mio suivies de leur drain — est intentionnellement distincte de cette vague
runtime ; elle mesure le stress froid, sans prétendre le faire tenir dans la fenêtre V51 de
60 s.

La réservation atomique est prise avant toute émission et laisse un verrou non résolu jusqu'à
la clôture locale prouvée. Pour V8, l'entrée immuable de fenêtre conserve l'instant
`REQUEST_SENT` remonté par le worker, seulement s'il est postérieur à la réservation et
antérieur ou égal à l'observation parent ; les anciens échanges et toute fin sans cette preuve
restent comptés depuis leur complétion. Un refus de budget qui reporte un créneau V8 ne laisse
pas la cible en `COLLECTING` : le cycle concerné, ses familles non parties et les autres cibles
dont le créneau tombe dans le même hold deviennent `WAITING_PRESSURE_RECHECK`, puis un J4
explicite reprend à l'échéance durable autorisée. Il n'existe ni départ de rattrapage ni seconde
réservation pendant ce délai.

La migration V50 propage la preuve de départ au contrôle J6 de sauvegarde/restauration ; V51
valide séparément la réserve temporelle inter-groupe V8, sans ajouter de table ni réécrire les
lignes historiques. Le runbook J6 courant exige donc le schéma V54, sans que cette révision V8
ne requalifie une exécution J6 :
`provider_departure_accounting` est inclus dans l'empreinte append-only et comparé entre source
et restauration. Une ligne `AUTHENTICATED_WORKER_REQUEST` est retenue seulement avec sa preuve ;
sinon `COMPLETION_FALLBACK` demeure l'heure conservatrice. Cette couverture ne lance, ne
réarme et ne requalifie aucune sauvegarde de la base opérateur.

Les fenêtres avant match restent explicites : groupe initial, contrôle T−60 si nécessaire,
compositions tous les cinq minutes jusqu'à T−5 et attente de T−5 au coup d'envoi. J4 seul
continue alors à 60 s jusqu'à `inprogress`, puis les quatre familles entrent dans la vague V8.
Si le J4 initial a déjà constaté `notstarted` moins de 60 s avant le coup d'envoi connu, son
prochain contrôle est conservé sur la plus tardive de sa phase sérialisée de coup d'envoi et de
ce J4 initial + 60 s. Cette continuité évite une seconde vague artificielle à T0 : les trois J5
ne sont pas présentés comme prévues tant qu'un J4 n'a pas réellement confirmé `inprogress`.
Un J4 `delayed` muni d'un nouvel horaire recale les fenêtres ; un horaire manquant ou une
régression après `inprogress` reste à revoir. Les 404 J5 sont espacés par rencontre/famille,
sans rafale de rattrapage.

La suspension persistante après 403/429 est commune aux profils. Elle ne peut être effacée
par une campagne neuve, un redémarrage, une attente de budget, un changement de contexte ou
une rotation d'adresse. Le réarmement reste une opération manuelle sans requête de sonde et
ne reprend pas une campagne. Aucun proxy, changement automatique d'IP/VPN, challenge ou
réutilisation de cookie n'est ajouté.

La [qualification V8 fraîche](../validation/WO058-LIVE-V8-CAPACITY-20260910.md) a employé le
worker de production, Chromium et PostgreSQL de test contre une origine éphémère `127.0.0.1`,
sans appel fournisseur ni base opérateur. Elle a couvert dix rencontres, les quatre familles,
les fenêtres de pression et les slots rencontre/famille, après quarante réponses froides de
5 Mio et leur drain de 60 001 ms. Ses 1 800,0198031 s établies et 2 100,0198031 s de voie stricte
ont produit 1 444 appels, dont 1 203 établis et 40 froids, sans cycle manqué ni requête hors
périmètre. Les octets versionnés lient le rapport natif
`f5b70709dcc51d9b40223fde1175d3c507190244562355e675689cb06e9a7fc0` au profil
`c5cef2745422d70bab769d03a93991af8ce3d685fb9daabb64fd00ab0a1ca3c8` ; les bornes immuables sont
DETAILS 300/500 ms, INCIDENTS 300/400 ms, STATISTICS 350/400 ms et LINEUPS 300/450 ms. La revue,
la fusion et la livraison manuelle du lanceur restent séparées. Même cette réussite loopback ne
prouve ni une latence Internet bornée, ni l'acceptation de 45/min par SofaScore, ni l'absence de
refus futur.

## Politique historique live-v6 — profil nominal et correctif v0.9 qualifiés dans leurs portées distinctes

Les préparations historiques `live-v6` utilisaient au plus sept rencontres, avec un profil
`grouped-v6` et un SHA de qualification distincts. La cible reste 100 s pour les familles
critiques et les compositions prématch, 300 s pour les compositions en jeu. La limite
locale partagée peut reporter ces échéances ; la cible n'est pas une garantie sous pression.
Les plafonds individuels/campagne restent 2 500/20 000 appels, quatre heures et
15 728 640 000 octets bruts. L'admission conserve 10 % de marge : sa borne horaire compte
120 appels ordinaires, quatre initiaux et quatre finaux par rencontre, soit 896 à sept.
Le rejeu complète cette borne par les coûts et les différentes phases du match.

Le [profil mesuré du 9 septembre](../validation/WO058-GROUPED-LIVE-V6-PROFILE-20260909.json)
qualifie sept rencontres avec 250/350 ms pour J4, 300/400 ms pour les incidents,
300/450 ms pour les statistiques et 250/450 ms pour les compositions (échange/traitement).
Ces maxima établis arrondis vers le haut à 50 ms représentent 187,95 s de travail et
pauses pour sept sur les 270 s allouables par tranche de cinq minutes. Les 24 replays
de l'ordonnanceur confirment l'admission. La mesure native couvre 35 minutes, dont
30 établies, avec 494 échanges et aucun cycle manqué ; son wrapper de production,
ses diagnostics et son stockage PostgreSQL V44 sont réellement exercés.
La portée des enveloppes reste le corpus établi de 64 Kio, y compris LINEUPS V3
enrichies ; les 28 premières réponses de 5 Mio sont séparées. Les coûts d'échange,
le traitement et les attentes du limiteur sont distingués. Le plafond opérateur six
et son timeout 30 s sont conservés indépendamment ; la mesure ne les modifie pas.
La vérification finale de ce complément nominal et de son interface est réussie avant
`7593e36` ; elle ne qualifie pas le nouveau chemin de récupération sur timeout.

`ResilientPlaywrightProviderCampaignFactory` enveloppe les parcours J3/J4/J5, manuels et
live, sans modifier leur allowlist ni créer de session automatiquement. Chaque départ
est réservé atomiquement auprès de `ProviderResilienceStore`. Après sa fin prouvée,
deux secondes au moins précèdent le départ suivant ; les réservations résolues restent
comptées pendant 60 secondes et une heure depuis cette fin, avec plafonds 25 et 1 000.
Une réservation non résolue bloque tout nouveau départ jusqu'à sa clôture locale prouvée.
Le contrôle reste acquis après redémarrage ou changement de campagne. Les pauses
historiques plus longues restent applicables ; aucun parallélisme n'est ajouté.

L'ordonnanceur vérifie la première admissibilité avant de réserver l'appel live ; la
factory la revérifie atomiquement au départ. `defer(Due, Instant)` conserve le groupe et
son ordinal, borne l'attente à la fin du manifeste et coalesce les tours expirés après
une attente de budget. Ce report n'est pas une permission de prolonger la campagne.
L'intervalle nominal, la prochaine échéance, l'âge reçu et l'attente de transport restent
des mesures distinctes. Les budgets sont conservés entre campagnes, donc l'admission
locale seule ne promet pas une place immédiatement disponible dans chaque fenêtre.

V6 espace les 404 J5 par couple rencontre/famille : 300/600/900 s pour incidents,
statistiques et compositions prématch ; 600/900 s pour les compositions en jeu. Une
réponse PARSED remet ce couple au nominal et une transition J4 réévalue les absences.
J4 n'est jamais espacé sur 404. Un 404 final reste une tentative finale incomplète,
sans répétition supplémentaire. Les snapshots et résultats UNAVAILABLE conservent
leur contrat ; la dernière observation exploitable reste consultable avec son âge.

Un 403/429 connu aux en-têtes suspend tous les nouveaux accès dans un état persistant,
distinct de `provider_campaign_guard`. Le retour de cette garde à FREE ne réarme pas
l'accès fournisseur. Le réarmement manuel vérifie la version observée, la clôture des
sessions et l'éventuel `Retry-After` ; il n'émet aucune requête et ne reprend aucune
campagne. Les délais de départ déjà consommés restent comptés. Un timeout sans statut
connu ne prouve aucun 403. Hors de l'exception v0.9 décrite ci-dessous, il garde l'arrêt
global ; aucun classement permissif n'est déduit de l'absence d'en-têtes.

V42 porte quatre tables de pression/suspension, V43 deux tables de diagnostic et V44
les contraintes de politique v6. Les diagnostics séparent en-têtes reçus et réponse
complète, timeout worker et attente IPC parent, cause primaire et dernier échec cleanup.
Ils ne contiennent ni en-têtes bruts ni payload et ne fabriquent aucune réception.
Les métadonnées historiques absentes restent inconnues. Le plafond de timeout v6 est
30 s, celui de v5 reste 20 s et ceux de v1–v4 restent 10 s ; le défaut `local` reste 20 s.
Ces plafonds ne changent pas les coûts d'admission. Aucune preuve v5 n'est renommée en v6.

### Correctif v0.9 : fin d'échange prouvée avant reprise différée du match

La récupération concerne uniquement une session v6 déjà lancée et un timeout accompagné
d'une fin CDP corrélée `FINISHED`/`ABORTED`, fermeture de page, nettoyage des cookies et
contexte existant réutilisable. Avant les en-têtes, l'annulation est demandée immédiatement
avec terminal `ABORTED` corrélé. Après les en-têtes, le worker attend naturellement
`FINISHED`, sans `Page.stopLoading` qui peut supprimer le terminal après `COMMIT`.
Ces deux chemins partagent la même grâce maximale de deux secondes ; sans terminal,
la fermeture fatale est conservée. Une seconde IPC est réservée à la preuve terminale
authentifiée. Cette attente ne prolonge ni le timeout de collecte ni ses budgets : le
corps après timeout est abandonné même s'il se termine dans la grâce. Les champs `exchangeEndedAt`,
`exchangeEndReason` et `contextReusable` sont ajoutés aux diagnostics par V45 ; les
anciens diagnostics restent sans preuve. Un corps incomplet ne crée ni snapshot ni
résultat normalisé. Les 403/429 connus restent prioritaires et suspendent les accès.

Pour un succès, `receivedAt` est fixé immédiatement après `body()`, avant le nettoyage
local obligatoire. La provenance distingue ainsi la réception du coût de nettoyage.
Un timeout ne produit aucun nouvel instant de réception ni snapshot. Application et
worker doivent être reconstruits ensemble pour IPC v7 ; V45 sera appliquée lors du
prochain démarrage opérateur, sans démarrage ni migration opérateur dans ce lot.

Après persistance de cette preuve, la décision de reprise est sérialisée avec l'arrêt
opérateur. Le groupe est fermé et jamais rouvert ; toutes les familles du match attendent
au moins 300 secondes après le plus tardif de l'instant courant et de la fin observée,
puis un nouveau J4 réévalue sa phase. Les autres matchs peuvent progresser. Un arrêt
demandé entre-temps reste prioritaire : le timeout est publié comme abandonné, sans
réarmer le match ni remplacer son motif d'arrêt. La protection partagée et le fence
transport de trois secondes après cette fin exceptionnelle restent applicables.

`LiveTimeoutRecoveryPolicy` borne la session à trois tolérances, exige un `PARSED` entre
deux timeouts et un `PARSED` du même couple avant sa récidive. Les 404 ne réinitialisent
pas ces gardes et les succès ne remettent pas le total à zéro. Un timeout admissible
en finalisation arrête le match, sans nouvelle collecte finale ; les quotas, la fenêtre
et les arrêts opérateur restent prioritaires. La qualification fonctionnelle dédiée
réussit : neuf cas Chromium transport/UI, deux contrôles natifs après correction de
l'horodatage et vérification finale `-Pintegration-tests clean verify` à 17:17:59Z
(2 068 cas standards, cinq skips explicités, 213 PostgreSQL, zéro échec et erreur).
L'arrêt individuel pendant timeout acquitte la tâche en vol même abandonnée, sans
reprogrammer le match arrêté ; deux régressions avec deux rencontres le contrôlent.
Le smoke de trois minutes ne renouvelle pas la preuve de capacité de 35 minutes.
Aucune livraison Eclipse ni action fournisseur n'est effectuée par cette qualification.

L'ouverture `openLiveGroupedV6` porte une autorité distincte. Pour les familles en jeu
différées après 404, elle accepte les sous-ensembles strictement croissants de J5 après
J4, avec identité de campagne/groupe/événement inchangée. Les répétitions, retours en
arrière, groupes fermés et phases incompatibles restent refusés ; v4/v5 et J5 manuel
gardent leur ordre historique. Les nouveaux contrôles n'assouplissent aucun endpoint,
budget ou seuil de suspension et ne recréent aucun contexte.

## Politique historique live-v5

Depuis le complément du 9 septembre, la garde de lancement live-v5 accepte un
timeout Playwright strictement positif jusqu’à vingt secondes ; les politiques
historiques restent à dix secondes. Le profil `local` utilise vingt secondes par
défaut, avec override d’environnement. Ce plafond d’attente est distinct des coûts
d’admission gelés dans le profil qualifié. Aucun changement de migration, de hash
historique ou d’enveloppe n’est réalisé. La valeur de transport est une configuration
du processus ; elle doit être jointe aux preuves d’une nouvelle campagne.
Les contrôles de retard et la fermeture globale sur timeout restent applicables.

Les préparations conservées `live-v5` utilisent un profil `grouped-v5` séparé. Leur manifeste
fige 100 secondes pour les familles critiques, 300 secondes pour les compositions en jeu,
trois tours de compositions, une seconde entre groupes, 2 500/20 000 appels et le plafond
brut indépendant de 15 728 640 000 octets. La limite effective vaut au plus vingt rencontres,
selon les enveloppes qualifiées, les simulations et le plafond configuré. Le régime établi
coûte 2 appels par minute et par rencontre ; les réserves finales et budgets sont contrôlés
séparément. La cadence n’est pas ralentie pour prolonger les budgets.

Le candidat précédent à 75 secondes a tenu sa cadence native, mais les enveloppes mesurées
n’admettent que dix-sept rencontres avec la marge requise. Sa [preuve reste conservée](../validation/WO058-GROUPED-LIVE-V5-CANDIDATE-75-PROFILE-20260908.json).
La cible v5 de 100 secondes suivait la priorité à vingt rencontres. Son admission rejoue
vingt-quatre scénarios de phases et transitions ; sa [qualification native dédiée](../validation/WO058-GROUPED-LIVE-V5-20260908.md)
est acquise en loopback, avec intervalles critiques P95 ≤105 s et maximum ≤115 s.
Elle ne démontre pas la tenue de cette cadence avec des réponses fournisseur lentes.

`GroupedAdmissionProfile` porte la version de politique ; le constructeur historique à deux
arguments reste v4. `GroupedLiveScheduleV4` conserve son nom et accepte explicitement v4/v5/v6,
avec périodes et règles propres. Les versions v1–v3 utilisent toujours
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
Cette vérification de campagne ne libère jamais un garde dépourvu de campagne : le cas des
requêtes manuelles est traité par une voie locale strictement distincte décrite ci-dessous.

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

Une interruption peut aussi se produire après l’acquisition du garde live, mais avant le commit
de `launch`. La ligne locale est alors une préparation strictement non exécutée : elle ne possède
ni ownership, ni génération d’exécution, ni instant de lancement, appel, octet, échéance ou
planification de famille. Cette forme ne passe jamais par `interruptOrphan`, qui exige à juste
titre une ownership d’exécution. Au redémarrage, le Lab conserve la préparation et marque seulement
la garde `CLEANUP_REQUIRED`; il ne la libère pas automatiquement et ne transforme pas la préparation
en campagne interrompue.

La page de cette préparation peut afficher l’action distincte **« Libérer la garde avant
lancement »**. Son formulaire local à usage unique porte la génération observée et exige une
confirmation. Sous exclusion locale, le Lab refuse toute session ou tout superviseur actif, puis
`LiveOrphanProcessProbe` doit prouver l’absence du propriétaire et des processus Playwright
associés. `completePreLaunchOrphanCleanup` verrouille ensuite, dans cet ordre, le garde, la
préparation et ses cibles. Il compare l’état, l’identifiant de campagne, l’instance, le PID,
l’instant de démarrage, la génération et `changed_at` de la garde, puis revalide l’absence de toute
exécution ou planification. Une modification concurrente, une preuve incomplète ou une préparation
altérée conserve le garde bloqué.

Quand cette preuve est complète, la transaction ajoute seulement
`LOCAL_PRELAUNCH_CLEANUP_VERIFIED`, avec l’empreinte SHA-256 de la garde vérifiée, puis remet cette
garde exacte à `FREE`. La préparation, ses cibles et son état `PREPARED` — ou son annulation
pré-lancement `STOPPED_OPERATOR / PREPARATION_CANCELLED` — restent historiques et inchangés. Une
nouvelle tentative de lancement doit reprendre l’acquisition normale, avec une génération nouvelle;
le mécanisme ne lance aucun navigateur, appel fournisseur, reprise, retry ou réarmement.

Une collecte manuelle J3/J4/J5 interrompue ne crée pas de ligne `live_campaign`. Après un crash,
la page locale `/provider-access` présente donc une action de libération seulement si le garde
durable est `CLEANUP_REQUIRED`, possède un propriétaire et une identité de campagne, et que cette
identité ne correspond à aucune campagne live. Le `POST` exige le jeton de formulaire local à
usage unique, une confirmation opérateur et la génération observée. Il prend la même exclusion
locale que les clôtures live, vérifie qu’aucune session ou superviseur n’est actif, puis
`LiveOrphanProcessProbe` doit prouver l’absence du propriétaire et de ses processus Playwright.
Une présence, une identité incertaine ou une inspection indisponible conserve le garde bloqué ;
aucun processus n’est arrêté.

Après cette preuve, le service relit l’absence de `live_campaign`. La transaction verrouille le
garde et ne le libère que si son état, son identifiant de campagne, son propriétaire complet
(instance, PID et date de création), sa génération et sa date de changement sont exactement ceux
lus par le service après validation du formulaire. Une acquisition, une campagne apparue entre-temps
ou une soumission périmée échoue fermée. Cette voie ne crée aucun navigateur ni appel fournisseur,
ne reprend aucun échange,
ne réarme aucune suspension et ne clôture aucun départ incertain : ces décisions restent des
opérations locales explicites et séparées.

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
J5 404 conserve l'indisponibilité : cycle suivant pour l'historique, backoff par famille pour v6. Transport, refus HTTP hors
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
Pour v6, ces échéances intègrent aussi le report global et le délai après 404. La protection
commune est consultable sans appel fournisseur ; une suspension est distincte d'une simple
attente de budget et d'une clôture de processus encore requise.
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
