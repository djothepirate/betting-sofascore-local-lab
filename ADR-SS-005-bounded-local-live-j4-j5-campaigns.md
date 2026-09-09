# ADR-SS-005 — Campagnes live locales et bornées J4/J5

- **Version :** 0.7.
- **Statut :** `ACCEPTED` — v0.1 formellement acceptée ; capacité adaptative puis collecte des compositions avant le début explicitement demandées par le propriétaire le 7 septembre.
- **Date :** 2026-09-09.
- **Décideur :** propriétaire du Betting Project.
- **Acceptation formelle initiale, v0.1 :** `OWNER_ACCEPTED_2026_09_07` — « Je valide formellement la v0.1 de l'ADR ».
- **Observation de l'acceptation :** `2026-09-07T09:23:14Z` ; l'heure exacte du message n'est pas disponible.
- **Document accepté figé :** [copie exacte de la proposition v0.1](docs/validation/ADR-SS-005-v0.1-accepted-proposal-20260907.txt), SHA-256 `48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e` ; draft non committé.
- **Autorité de la v0.2 :** plafond paramétrable demandé, y compris à 10 et 25 rencontres ; cadence selon le nombre retenu, 60 s pour 1–3, 90 s pour 4, 120 s pour 5, choisie explicitement dans la conversation. La progression de 30 s par rencontre supplémentaire est appliquée aux sélections plus grandes.
- **Autorité de la v0.3 :** demande de J5 LINEUPS pour les rencontres initialement `notstarted` et non débutées au lancement, puis réponse explicite « Oui, collecte initiale puis périodique ». La première collecte suit le J4 `notstarted` ; sa répétition utilise D tant que le début n'est pas constaté. Aucune nouvelle cadence n'est décidée.
- **Autorité de la v0.4 :** demande explicite « PLEASE IMPLEMENT THIS PLAN: Campagnes live à 60 secondes par match, avec appels regroupés », le 8 septembre 2026. Cette décision autorise l’exception de délai décrite au §0, la politique `live-v4`, V39 et leur qualification hors fournisseur ; elle ne lance aucune campagne fournisseur.
- **Autorité de la v0.5 :** demande explicite du 8 septembre « La suppression de cette intervalle doit se faire aussi en mode collecte manuelle », pour J5. Le propriétaire signale également l’absence d’annulation d’une préparation live non lancée. Cette révision étend le regroupement à une collecte J5 manuelle bornée et autorise son contrôle distinct ; elle ne crée aucun polling manuel ni nouvelle famille fournisseur.
- **Autorité de la v0.6 :** demande du 8 septembre d’augmenter les plafonds à 20 000 appels par campagne et de réduire la pause entre groupes à une seconde, puis arbitrage explicite « Priorité aux 15–20 matchs, avec une cadence explicitement qualifiée ». La cible initialement souhaitée de 20 secondes cède donc la priorité au nombre de rencontres. Le propriétaire précise ensuite un objectif à moyen terme de 50–100 rencontres et demande une visibilité sur les cadences nécessaires ; cet objectif ne constitue pas une qualification de cette capacité.
- **Référence historique v0.2 :** contenu Git au commit `e98f7a74e39a1c57e601efb3d346ae55829fce73`, conservé sans réécriture.
- **Autorité de la v0.7 :** demande du propriétaire le 9 septembre d’un timeout Playwright « un peu plus élevé » pour le prochain essai après PLAYWRIGHT_TIMEOUT. La borne retenue est vingt secondes pour live-v5. Ce complément donne davantage de temps à un échange lent ; il ne change ni cadence, ni enveloppes qualifiées, ni budgets et n’autorise aucun lancement par l’agent.
- **Work Order :** [WO-SS-20260907-058](docs/work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md), validé par le propriétaire ; correctif et qualification de réalisation distincts de cette décision.
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
- **Base :** `6dfd14286d4f269cbe100bd965257c20298538db`, train `feature/V0.1.0-RC01` vérifié à l'ouverture.
- **Effet de cette révision :** cadre de sélection et cadence du correctif WO-058 ; aucun lancement de campagne fournisseur par cette rédaction.

Les prescriptions ci-dessous définissent la décision **acceptée**. L'acceptation établit le cadre
d'architecture ; la validation du WO et la qualification de la réalisation restent nécessaires
avant le manifeste concret et le lancement opérateur d'une campagne. La v0.2 remplace seulement
les limites de sélection et les cadences correspondantes de la v0.1. Les autres garanties restent
applicables ; la copie exacte acceptée de la v0.1 et les preuves de ses paliers sont conservées.
La v0.3 ajoute seulement la collecte prématch LINEUPS au manifeste `live-v3`. Les manifestes
historiques `live-v1` et `live-v2` gardent leur comportement, leurs échéances et leurs empreintes.

## 0. Décision live-v5 — vingt rencontres et cadence qualifiée

### Complément du 9 septembre — timeout de transport à vingt secondes

Le plafond de timeout Playwright autorisé au lancement **live-v5** passe de dix à
**vingt secondes**, strictement positif. Le profil applicatif `local` prend vingt
secondes par défaut ; `SOFASCORE_PLAYWRIGHT_REQUEST_TIMEOUT` permet un réglage
explicite dans cette borne. La configuration Playwright étant partagée, ce défaut
local concerne aussi les collectes manuelles. Le défaut hors profil local reste
dix secondes. Les politiques live-v1 à live-v4 gardent leur plafond de dix secondes.

Cette borne concerne l’attente dans le transport Playwright, avant le parsing et
la persistance métier du Lab. L’enveloppe de coût de chaque famille utilisée pour
l’admission conserve sa valeur et sa preuve ; elle ne devient pas vingt secondes.
Une série de réponses lentes peut donc dégrader la cadence ou déclencher l’arrêt
pour retard. Les critères de fraîcheur et les contrôles de surcharge s’appliquent
toujours ; aucune qualification à vingt rencontres avec réponses de vingt secondes
n’est déduite de ce complément.

Le timeout est une configuration du processus de lancement, non un champ historique
du manifeste. Consigner sa valeur avec les preuves du prochain essai, redémarrer le
Lab et préparer une nouvelle campagne. L’arrêt global sur timeout ou HTTP 403, les
fenêtres maximales, le nettoyage et l’absence de retry restent applicables.
La qualification locale doit démontrer une réponse J4 retardée au-delà de dix
secondes mais reçue avant vingt, puis l’expiration et le nettoyage à la nouvelle
borne, sans appel fournisseur.

### Cadence, charge et budgets

Les nouvelles préparations utilisent `live-v5`. Le candidat à qualifier est **20 rencontres
à 100 secondes nominales** pour J4, incidents et statistiques, avec les compositions en jeu
toutes les 300 secondes (trois tours). Avant le jeu, J4 et les compositions après
`notstarted` utilisent 100 secondes. L’initialisation conserve une collecte immédiate des
compositions éligibles. Une cadence de 20 secondes n’est pas annoncée à vingt rencontres.

Le premier candidat à 75 secondes a tenu sa cadence pendant trente minutes établies à vingt
rencontres, mais ses maxima mesurés ne permettent d’en admettre que dix-sept avec la marge
prévue. Le passage à 100 secondes suit la priorité explicite donnée au nombre de rencontres.
Cette nouvelle cadence est qualifiée séparément ; les preuves du candidat précédent sont conservées.

L’autorité de transport est créée côté serveur pour toute la session v5. Les endpoints,
l’ordre et la limite de quatre appels distincts par groupe restent ceux de v4. La pause
après la dernière réponse est ramenée à **une seconde entre deux groupes de cette même
session v5** ; les continuations valides restent sans pause artificielle. Les transitions
vers une autre session, les parcours manuels et les politiques historiques conservent leur
barrière d’au moins trois secondes. L’arrêt, l’admissibilité et la propriété sont revérifiés
avant chaque dispatch, y compris après l’attente. Aucun réglage HTTP ne peut réduire ces délais.

Le manifeste fige **2 500 appels par rencontre, 20 000 appels par campagne, quatre heures**
et un plafond brut indépendant de **15 728 640 000 octets**. Les plafonds d’appels ne sont
pas des volumes promis : l’épuisement de l’un des budgets termine la collecte. La réserve
finale de quatre appels par rencontre et le contrôle du double de l’espace brut restant,
plus la réserve disque locale, sont conservés. La capacité de sélection reste plafonnée à
vingt et limitée par l’admission temporelle.

Un profil v5 séparé, avec son propre SHA-256 et ses enveloppes par famille, est obligatoire.
Une preuve v4 ne devient pas une preuve v5. L’admission additionne, par rencontre et sur
300 secondes, trois triplets critiques, une composition et trois pauses d’une seconde ;
10 % du temps restent non alloués. Les simulations contrôlent aussi initialisation,
transitions, finalisations et variations de coût. V40 ajoute les contraintes versionnées
sans réécrire les manifestes, compteurs, observations et politiques historiques.

La qualification dédiée mesure vingt rencontres avec Chromium en loopback et PostgreSQL,
pendant cinq minutes d’initialisation puis au moins trente minutes établies. Chaque couple
rencontre/famille critique doit tenir un intervalle de réception et de publication
P95 ≤105 secondes et un maximum ≤115 secondes. Les compositions restent nominalement à
300 secondes, avec un retard P95 ≤15 secondes ; la dette de file ne doit pas croître.
Le délai de publication locale vers l’affichage dynamique est contrôlé séparément sous
dix secondes. L’écran continue à lire les données locales toutes les cinq secondes.

Le tableau de capacité vers 50–100 rencontres distingue les bornes arithmétiques, les
contraintes du calendrier et les capacités mesurées. Au-delà de vingt, aucune capacité
n’est activée par extrapolation. Une évolution du calendrier, du coût des échanges ou de
la répartition des familles exige une qualification dédiée avant activation. Les mesures
locales ne qualifient ni la latence Internet ni la fraîcheur des données chez le fournisseur.

### 0.0. Politique historique live-v4 — groupes live et minute fixe

Cette section remplace, **pour les nouvelles préparations `live-v4` uniquement**, les cadences,
l’ordre des familles, l’admission et le délai par appel décrits plus bas. Les §§1–12 conservent
le contexte et les règles historiques `live-v1` à `live-v3` ; leurs autres garanties s’appliquent
à v4. La révision v0.3 reste consultable au commit `c972d63` sans réécriture d’observations.

La cible pendant le jeu est **60 secondes nominales par événement pour J4, incidents et
statistiques**. Dix rencontres simultanées sont un critère de qualification, jamais une capacité
présumée. Les compositions sont initiales, puis réparties sur cinq tours, à 300 secondes nominales.
Avant le jeu, J4 reste périodique à 60 secondes ; LINEUPS est initial puis périodique à 60 secondes
après `notstarted`. Une composition récente ne bloque pas les deux autres familles après le début.

Un groupe serveur appartient à une campagne, un événement et une séquence uniques. Il contient
au plus quatre endpoints distincts : **J4 → incidents → statistiques → compositions si dues**.
Chaque réponse est reçue, normalisée et publiée avant l’appel suivant. Seule une continuation
consécutive validée du même groupe supprime le délai artificiel. Aucun champ HTTP ne commande
ce comportement. La fin de chaque échange actualise le repère commun, y compris si le groupe
est interrompu : **trois secondes au moins après la dernière réponse** avant tout autre groupe,
parcours manuel ou campagne historique. Les requêtes restent globalement séquentielles.

J4 est premier quand présent et réévalue immédiatement les familles. `postponed` annule la suite
du groupe et termine ce seul suivi. `finished` annule les tours ordinaires et programme les
familles finales dans les réserves ; une famille finale encore inadmissible reste en attente
individuelle et ne bloque pas les autres matchs. Un groupe de finalisation peut commencer sur
une famille J5 restante. L’arrêt opérateur, la propriété de session, la fenêtre et les budgets
sont contrôlés avant **chaque appel**. Les 404 J5 restent locaux à la famille et les portées
des autres arrêts sont conservées.

Les phases de groupes sont réparties sur la minute. Une seule échéance est conservée par
événement/famille ; pas de rafale de rattrapage. Le retard reste mesuré et une surcharge durable
arrête le suivi. La minute est une cible nominale, pas un délai minimal ajouté après chaque
réponse : les variations de durée ne doivent pas déplacer cumulativement toutes les échéances.
Quand un match termine, ses créneaux sont libérés sans allonger ceux des autres.

L’admission utilise un **profil de groupes séparé**, avec une preuve SHA-256 dédiée et des coûts
qualifiés par famille, distincts du profil historique. Chaque groupe coûte la somme de ses
échanges et traitements, plus une seule pause de trois secondes ; 10 % de la minute restent
non alloués. Le coût établi est calculé sur cinq tours : cinq J4, cinq incidents, cinq statistiques,
une composition et cinq pauses par rencontre. Des simulations séparées vérifient les vagues
d’initialisation, de transitions simultanées et de finalisation. Le régime établi à dix matchs vaut environ 32 appels et dix pauses
par minute. Une sélection excessive est refusée avec la capacité effectivement admissible ;
la minute n’est jamais remplacée silencieusement par plusieurs minutes. Aucun profil existant
n’est abaissé automatiquement.

V39 ajoute la politique figée, les groupes, leurs appels et les échéances par famille. Les
empreintes des manifestes et observations historiques restent inchangées. L’écran montre cible,
capacité qualifiée, dernière réception, prochaine collecte, retard et autonomie estimée à partir
des budgets restants. Les plafonds 1 000/3 000 appels, quatre heures et la réserve finale restent
inchangés : la fraîcheur est prioritaire, sans ralentissement pour prolonger l’autonomie. La
lecture locale reste à cinq secondes ; une lecture bloquée est annulée à dix secondes et reprise
en conservant les dernières données et les panneaux statistiques repliables.

La qualification dédiée exige Chromium loopback et PostgreSQL pendant au moins trente minutes
en régime établi, séparé de l’initialisation, avec réponses représentatives des quatre familles
et variations de latence. À dix matchs : P95 des intervalles de réception critiques ≤65 s,
maximum ≤75 s ; compositions nominales 300 s et retard P95 ≤15 s ; publication visible sous
dix secondes en fonctionnement normal. Tester aussi interruptions/rejeu de groupe, dégradation,
budget, concurrence, upgrade V38 prérempli et sauvegarde/restauration. Les tests standards
n’appellent jamais SofaScore. Une campagne opérateur distincte reste nécessaire pour comparer
la fraîcheur externe réelle ; les mesures loopback ne la prouvent pas.

### 0.1. Complément v0.5 — collecte J5 manuelle et annulation d’une préparation

Une collecte J5 manuelle explicitement confirmée pour un événement forme un seul groupe
serveur : **statistiques → incidents → compositions**. Chaque appel attend la réception
et le traitement du précédent. Les deux pauses artificielles entre ces familles sont
supprimées ; aucun chevauchement de requêtes n’est autorisé. Une autorité dédiée à J5 manuel
lie campagne, événement, ordre et endpoints, distinctement des manifestes `live-v4`.
Ni un champ HTTP ni une simple ouverture historique de session ne peut activer l’exception.

Le coordinateur et le superviseur contrôlent la même portée. Arrêt opérateur, propriété de
lease et admissibilité sont revérifiés avant chaque dispatch. La fin de chaque échange
actualise le repère commun, même si le groupe s’interrompt : trois secondes restent exigées
avant une autre collecte J5, une campagne live ou tout autre parcours. Les groupes interrompus,
réutilisés, d’un autre événement ou comportant un endpoint répété sont refusés.
J3, J4 manuel et les campagnes live v1–v3 conservent leur délai historique ; `live-v4`
conserve ses paramètres, échéances et preuve de capacité. Les limites, confirmations,
traitements des erreurs et HTTP 404 du parcours J5 manuel gardent leur portée.

Une préparation live en état `PREPARED` peut être annulée par une action locale explicite,
sans ouvrir de navigateur ni réserver d’appel. Le manifeste et l’historique restent conservés.
L’annulation et le lancement sont sérialisés : une préparation annulée ne peut plus démarrer,
et une campagne déjà lancée utilise son action d’arrêt existante.

Ce complément exige des tests d’annulation/concurrence PostgreSQL et une qualification
Chromium loopback du groupe J5 manuel, des frontières de trois secondes, de la sérialisation
et des interruptions. Il ne transforme pas une mesure locale en preuve de fraîcheur SofaScore.

## 1. Contexte et problème

Le propriétaire veut sélectionner des rencontres dans les **Résultats normalisés** de `/events`,
lancer une campagne locale et consulter l'évolution des données. J4 doit constater le début et
la fin du match ; J5 doit collecter statistiques, incidents et compositions à la cadence du manifeste pendant
le jeu. La liste initiale conserve l'horaire `Europe/Paris`, la compétition, les identités locale
et fournisseur, le statut reçu et sa provenance.

Les parcours actuels autorisent un GET J4 phase 2 par confirmation et trois GET J5 par campagne,
avec un contexte Playwright neuf, sans cache fournisseur et une coordination exclusive commune
J3/J4/J5. Ils n'autorisent pas la répétition automatique de leurs claims. Le fence commun attend
au moins trois secondes après la fin observable de l'échange précédent ; dix matchs actifs
demanderaient déjà trente GET J5 par minute, au-delà de cette capacité avant même J4 et les latences.

[ADR-SS-001 v1.4](ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md), §3.10 et §9,
exige un nouvel ADR pour ce passage au live. Celui-ci définit une expérimentation bornée sur le
poste Windows local. Il ne transforme pas le Lab en service permanent.

## 2. Décision acceptée et portée par rapport aux ADR existants

Autoriser une **campagne locale lancée manuellement une seule fois**, qui exécute ensuite les
cycles J4/J5 de son manifeste immuable, pendant sa fenêtre et dans ses budgets. L'opérateur
choisit les événements et confirme explicitement le plan ; la consultation ou un timer ne peut
ni lancer une nouvelle campagne ni réutiliser une autorisation terminée.

| Référence | Portée de la décision acceptée |
|---|---|
| ADR-SS-001 §3.4, §3.5, §3.6.1, §3.6.1.1 et critère live du §8 | Exception limitée au parcours live WO-058 : une confirmation de campagne remplace les confirmations unitaires répétées, dont l'unique GET J4 phase 2 ; J4 et J5 sont répétés sans cache dans les bornes ci-dessous. Les parcours manuels existants restent inchangés. |
| ADR-SS-001 §3.6.1, arrêt opérateur | L'arrêt de campagne ferme le contexte partagé ; l'arrêt individuel supprime les tâches futures du match et laisse terminer sous timeout le GET engagé. Les garanties d'annulation/nettoyage du contexte concernent l'arrêt global. |
| ADR-SS-001 §3.10 et §9 | Exigence d'un nouvel ADR satisfaite pour cette portée seulement ; les futures extensions restent soumises au réexamen. |
| [ADR-SS-002](ADR-SS-002-bounded-multi-dossier-provider-robustness.md) | Aucune supersession : campagnes historiques, plafond 38/60 min et go consommés ne fournissent aucune autorité ni budget live. |
| [ADR-SS-003 v0.2](ADR-SS-003-optional-integration-topology.md) | Revue d'impact sans changement de topologie : aucune livraison J7 automatique ni dépendance du projet principal. L'audit `NOT_EVIDENCED` ne redevient pas une porte bloquante à lui seul pour le transfert J7 local V2. |
| [ADR-SS-004](ADR-SS-004-integration-continue-et-distribution-locale-uniquement.md) | CI sans fournisseur, distribution locale et réseau désactivé par défaut inchangés. |

L'acceptation initiale de la v0.1 et la décision de capacité de la v0.2 sont enregistrées avec
les renvois nécessaires à l'exception live dans ADR-SS-001 et [AGENTS.md](AGENTS.md).
Les décisions historiques sont conservées ; ADR-SS-002 à 004 restent inchangés.

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY`
restent obligatoires. Java 25 LTS, Spring Boot 4.1.0, Maven wrapper, PostgreSQL local Docker Desktop,
Eclipse et UTF-8 restent le socle. L'application écoute seulement `127.0.0.1:8087`.

## 3. Arbitrages propriétaires validés et bornes

Les choix ci-dessous proviennent des réponses du propriétaire pendant le cadrage, puis du plan
dont la réalisation documentaire a été explicitement demandée. Ce sont des limites locales
acceptées initialement puis modifiées explicitement pour le plafond et la cadence dans la v0.2,
pas des quotas SofaScore connus ni une qualification de débit.

| Paramètre | Décision retenue |
|---|---|
| Capacité | `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY` est le maximum de rencontres éligibles retenues par campagne, défaut 1 ; 5, 10 et 25 sont des configurations admises. Une seule campagne fournisseur active globalement. |
| Cadence D | Selon N cibles retenues après exclusion des `finished` : `D = max(60, 30 × (N − 1))` secondes ; 1–3 → 60 s, 4 → 90 s, 5 → 120 s, 10 → 270 s, 25 → 720 s. D est figé au manifeste, indépendant du plafond configuré. |
| J4 avant début | Premier contrôle au lancement, puis cible D tant que `notstarted`. |
| J5 avant début, live-v3 | LINEUPS seul après J4 `notstarted`, première place admissible puis D ; statistiques et incidents attendent J4 `inprogress`. |
| J5 pendant le jeu | Cible D, trois familles conservées à chaque cycle normal dans l'ordre statistiques → incidents → compositions. |
| J4 pendant le jeu | Sur signaux pertinents, secours à `max(300 s, D)` depuis le dernier J4 réussi, puis D en surveillance de fin. |
| Fenêtre | Au plus 4 h depuis le lancement, attente prématch comprise ; fin UTC exclusive, affichée aussi en heure de Paris. |
| Budget | Au plus 1 000 tentatives par match et 3 000 par campagne, J4, J5, appels échoués et clôture compris. |
| Erreur métier | Schéma incompatible isolable : arrêt du seul match ; autres événements maintenus. |
| J5 HTTP 404 | Indisponibilité de la famille, poursuite du cycle et nouvelle interrogation au cycle normal suivant ; aucun retry immédiat. |
| Finalisation | Un dernier cycle J5 après J4 `finished`, dans les délais, le budget et la fenêtre. |

Paramètres complémentaires conservés du WO : préparation valable cinq minutes ; timeout de
requête au plus dix secondes pour les politiques historiques, vingt pour live-v5 selon le
complément v0.7 du §0 ; réponse bornée à 5 Mio ; réserve d'un J4 et trois familles J5 par
match **comprise** dans le budget ; lecture locale d'écran toutes les cinq secondes. Une réserve
ne permet jamais de dépasser l'heure limite ou de déclarer un match fini sans preuve.

Le contrôle d'admission évalue les échéances, les transitions possibles, la réserve, les durées
qualifiées et l'espace disque. Une sélection incompatible est refusée intégralement avant
lancement ; aucune rencontre n'est retirée silencieusement. Le plafond paramétré ne prouve pas
la capacité temporelle : celle-ci est évaluée sur les N cibles et D. La limite technique d'entrée
reste de 100 identifiants par formulaire, y compris les exclus ; un plafond supérieur ne rend pas
une petite sélection invalide. Une borne plus basse inscrite au manifeste ne peut pas être augmentée
pendant la session. Trois mille corps maximaux représentent environ 14,65 Gio avant projections
et index : le contrôle de volume ne suppose aucun gain de déduplication et arrête avant saturation.

## 4. Exécution de la session et cadence

La sélection serveur vérifie UUID, ID fournisseur, provenance et absence de doublon. Elle produit
un manifeste gelé, limité aux identités existantes et aux quatre endpoints déjà connus. Aucun
identifiant libre ou URL fournie par le navigateur utilisateur n'est admis.

Le clic de lancement crée un seul worker et un contexte Playwright neuf non persistant, conservé
en mémoire pour cette campagne. Aucun cycle ne recrée le navigateur. La fermeture du worker ou
de son contexte, une panne, une veille ou un redémarrage de l'application terminent la session.
Fermer un onglet de consultation ne ferme pas le processus de campagne ; la reconnexion reprend
seulement sa lecture. Une campagne terminée n'est jamais reprise automatiquement.

Un opt-in live et une politique dédiés restent inactifs par défaut. Ne pas basculer simplement
les propriétés de rafraîchissement automatique que les politiques manuelles actuelles refusent.
Le catalogue générique reste `callable=false`, le `ConnectorGate` générique bloquant, le profil
`sofascore-live-test` bloqué, et les claims manuels J3/J4/J5 restent unitaires.

La campagne utilise le transport Playwright existant, l'origine exacte autorisée
`https://www.sofascore.com` et uniquement :

| Famille | GET |
|---|---|
| `EVENT_DETAILS` | `/api/v1/event/{EVENT_ID}` |
| `EVENT_STATISTICS` | `/api/v1/event/{EVENT_ID}/statistics` |
| `EVENT_INCIDENTS` | `/api/v1/event/{EVENT_ID}/incidents` |
| `EVENT_LINEUPS` | `/api/v1/event/{EVENT_ID}/lineups` |

Tous les départs restent séquentiels, sous coordination commune avec le fence d'au moins trois
secondes après l'échange précédent. Aucun appel manuel fournisseur ne s'intercale pendant la
campagne. Les lectures locales restent disponibles. Un cycle J5 conserve l'ordre de ses familles
et ne chevauche pas le cycle suivant du même match.

Les échéances nominales suivent `t0 + k × D` et une horloge monotone, avec au moins D entre
deux départs de la même famille pour un événement. Les horaires d'audit restent UTC. Un retard
ne produit pas de rafale : coalescer les échéances dépassées, les compter et afficher le retard.
Deux cycles successifs non servis avant leur échéance suivante arrêtent globalement la campagne
pour capacité insuffisante. Une seule requête J4 satisfait un signal et un secours simultanément.

## 5. Début, surveillance de fin et finalisation

1. Exécuter J4 dès la première place admissible après lancement. `notstarted` entretient la boucle
   J4 à l'intervalle D et, pour `live-v3`, une collecte LINEUPS initiale puis périodique à D.
   Statistiques et incidents ne sont pas interrogés dans cette phase. Les anciens manifestes
   `live-v1`/`live-v2` restent J4 seuls ; l'heure prévue du coup d'envoi n'est pas une preuve de début.
2. Dès J4 `inprogress`, arrêter cette boucle d'attente et démarrer J5. Les contrôles J4 ultérieurs
   ne redémarrent pas un « premier cycle » J5. Un match déjà commencé suit ce chemin immédiatement.
   Annuler la prochaine LINEUPS prématch. Pour conserver le triplet contigu et D entre deux
   départs LINEUPS, aligner le premier triplet sur l'éligibilité de toutes ses familles : l'attente
   due à une composition récente peut atteindre D, puis s'ajoute l'attente de service partagée.
   Cette attente ne réserve pas le transport et laisse les autres rencontres progresser.
3. Un nouvel `injuryTime` à 45, un `HT` ou un signal de mi-prolongation à 105 déclenche un J4
   ponctuel. Si J4 reste `inprogress`, conserver les échéances J5 sans armer une boucle de fin.
4. Un signal de fin potentielle à 90/120, `FT`, fin de prolongation/séance ou signal ambigu de fin
   arme J4 à l'intervalle D. J5 continue pendant mi-temps, prolongations et tirs au but tant que le
   match reste suivi. Ni `FT` historique, ni `Extra time` avec `isLive=true`, ni `time=120` comme
   borne de période ne suffit à conclure. Une boucle de fin déjà armée reste active jusqu'à une
   preuve J4 ou une borne ; les signaux répétés/corrigés ne créent pas de boucles supplémentaires.
5. Sans signal, J4 de secours reste programmé à `max(300 s, D)` depuis le dernier succès.
   Il est remplacé par la cadence de fin, jamais cumulé. Un endpoint incidents vide/404 n'implique
   aucun statut sportif ; `addedTime=999` n'est pas une durée d'attente de 999 minutes.
6. Seul J4 `finished` confirme la fin, même s'il est reçu au premier contrôle. Annuler les tâches
   ordinaires et effectuer au plus un cycle J5 final à sa première échéance admissible, en
   conservant D par famille et le délai global. Aucune répétition de stabilisation n'est ajoutée.
7. Si cette collecte est impossible ou partielle, conserver la fin sportive et indiquer la
   finalisation incomplète. À expiration sans J4 final, arrêter avec fin non confirmée.

## 6. Erreurs, indisponibilités et portée de l'arrêt

La portée de l'erreur est explicite dans les résultats et transitions : **événement** ou
**campagne**. Un statut sportif, un résultat de collecte et une complétude restent distincts.
L'ancienne règle du draft WO-058 qui arrêtait tous les matchs pour un schéma incompatible est
remplacée, pour ce seul parcours live, par la classification ci-dessous.

| Situation | Effet obligatoire |
|---|---|
| JSON reçu, contrôles de sécurité et de corrélation applicables satisfaits, schéma métier incompatible explicitement identifié | Arrêter seulement ce match ; conserver le brut et les familles déjà reçues. Ne plus exécuter ses familles restantes ni ses cycles futurs ; poursuivre les autres matchs dans le même contexte valide. |
| J4 404, statut non géré ou régression sportive incohérente | Arrêter ce match avec motif de revue, sans inventer `finished`. |
| J5 404 | Conserver `ENDPOINT_UNAVAILABLE`, traiter les familles suivantes ; au prochain cycle normal, réinterroger la famille indisponible si le match reste actif. |
| Transport, timeout, HTTP non-2xx hors 404, HTML/challenge, redirection/route inattendue, corps trop grand, incohérence d'identité, contenu sensible, exception interne du parseur, stockage/runtime défaillant | Arrêter globalement, nettoyer et interdire toute reprise automatique. |
| Anomalie non classifiable avec certitude | Arrêt global ; aucun classement métier permissif par défaut. |
| Arrêt individuel demandé | Supprimer immédiatement les tâches futures ; laisser son GET engagé terminer sous timeout, conserver son résultat sans réactiver le match. |
| Arrêt global demandé | Interdire tout nouveau départ et annuler le transport partagé. |

Les défauts de sécurité, de corrélation et d'identité priment sur une étiquette de schéma. Un
`UNEXPECTED_CONTENT`, un JSON dont la sécurité n'est pas établie ou une exception imprévue ne
devient pas une erreur métier isolable. Ne pas utiliser uniquement `status != PARSED` pour
déterminer la portée. Si le stockage du résultat métier échoue, cet échec reste global.

Un arrêt métier ne corrige pas automatiquement le parseur et ne réessaie pas le match. La future
reprise exige une nouvelle campagne manuelle après traitement de la cause. Le 404 J5 constitue
l'unique cas d'indisponibilité explicitement rééchantillonné à cadence normale ; il n'autorise
aucun retry technique accéléré. Un `Retry-After` ne réarme pas la campagne.

L'arrêt de tous les événements termine la campagne et ferme ses ressources. Préserver les
bornes existantes d'acquittement opérateur ≤500 ms, annulation ≤2 s et nettoyage ≤5 s. Un
nettoyage non confirmé conserve l'exclusion fournisseur ; l'arrêt individuel ne promet pas
l'annulation isolée d'un GET dans le contexte partagé.

## 7. Provenance, persistance et consultation

J4 phase 2 et J5 restent sans cache fournisseur ; la politique cache J3 n'est pas modifiée.
Les bruts restent locaux et séparés des normalisés. Conserver chaque occurrence avec son
snapshot, son hash, le parseur, l'observation et les heures de demande/réception. L'heure
pré-navigation n'est pas présentée comme une mesure de départ réseau.

Le ledger live distingue échéance, réservation, tentative, réception, résultat et transition.
Réserver le budget avant départ ; conserver le brut avant parsing ; publier atomiquement le
résultat normalisé et sa projection via les ports existants. La panne après départ avant résultat
laisse une issue inconnue et un budget consommé conservateur, sans renvoi automatique. Les
nouveaux éléments durables sont append-only pour les preuves et transitions, avec projection
courante reconstructible ; migrations après la dernière version partagée, sans faux backfill.

Les cas A→A et A→B→A réutilisent éventuellement des observations dédupliquées, mais chaque nouvelle
réception reste visible. La vue live suit la dernière occurrence traitée, pas seulement le tri
historique par date d'observation. Aucune date ancienne n'est réécrite pour simuler de la fraîcheur.

Les signaux de phase viennent d'une projection versionnée du normaliseur, avec provenance.
L'index JSON d'un incident n'est pas une identité durable. Le score courant exige l'extension
versionnée J4 prévue dans le WO ; il porte la date du J4 et n'est pas reconstitué en comptant les
buts ou depuis le dernier index des incidents. Absent, null, zéro, vide valide, partiel et
indisponible restent distincts ; VAR, prolongations et tirs au but restent représentables.

Le rafraîchissement local toutes les cinq secondes ne déclenche aucun GET fournisseur. Afficher
par famille dernière tentative, dernière réception réussie, dernière nouvelle version,
complétude, ancienneté et motif d'arrêt. Les familles peuvent provenir de réceptions différentes.
Préserver la sélection/focus, ignorer une réponse locale de révision plus ancienne et signaler
les anciennes données après erreur. Le replay synthétique reste intégralement hors réseau.

## 8. Alternatives écartées et conséquences

| Alternative | Motif du choix retenu |
|---|---|
| Dix matchs à la minute | Incompatible avec le débit séquentiel existant ; dix cibles ont une cadence de 270 s dans la v0.2. |
| Plafond fixe de trois matchs | Retenu initialement dans la v0.1, remplacé à la demande du propriétaire par le plafond paramétrable et la cadence selon les cibles retenues. |
| J4 systématique à chaque cycle pendant tout le jeu | Plus d'appels et score potentiellement plus frais ; le propriétaire retient signaux + secours, espacé d'au moins D. |
| Session huit heures ou 600/1 800 tentatives | Options présentées mais non retenues ; fenêtre quatre heures et plafonds 1 000/3 000 retenus. |
| Arrêt global pour toute incompatibilité métier | Non retenu ; isolation du match si l'intégrité de la session est établie. |
| Suspension définitive d'une famille J5 au premier 404 | Non retenue ; nouvel échantillon au prochain cycle normal, dans les bornes. |
| Arrêt immédiat de J5 après `finished` | Non retenu ; un dernier cycle borné collecte des données postérieures à la confirmation. |
| Recréation de contexte par timer, fallback, polling permanent ou reprise automatique | Hors modèle de session choisi et hors périmètre de cette décision. |

Conséquences : fraîcheur observable et historique de révisions ; volume de stockage accru ;
qualification longue durée du contexte et des arrêts ; score J4 potentiellement plus ancien que
les incidents J5 ; sélection multiple limitée par capacité ; pertes de fenêtres possibles sans
rattrapage. Une fin sportive confirmée ne garantit pas la complétude de la dernière collecte.

Playwright reste le seul transport ; aucun proxy, rotation d'adresse, furtivité, résolution de
challenge ou fallback FlareSolverr/HTTP. Aucun cookie, profil, storageState, HAR, trace, vidéo,
capture ou téléchargement conservé/journalisé ; aucun brut dans les logs ou vers le VPS.
Les contrôles existants d'effacement des cookies et d'isolation entre commandes sont conservés.

## 9. Critères et passage à la réalisation

Les critères ci-dessous sont **à qualifier**, pas des résultats acquis par la rédaction :

- Deux matchs simulés, JSON de schéma incompatible sur le premier : arrêt de ce seul match,
  autres familles du match annulées, brut conservé et second match poursuivi.
- Même scénario avec 403, timeout, incohérence d'identité, contenu inattendu, exception interne
  ou panne de stockage : arrêt global, sans nouveau départ ni contexte recréé.
- 404 J5 suivi d'un succès au cycle normal suivant : aucune tentative immédiate ; J4 404 arrête
  seulement le match. Statut sportif et complétude restent distincts.
- Début retardé, `inprogress`/`finished` initiaux, HT, prolongations, tirs au but et absence de
  signal : aucun début/fin inféré depuis l'heure théorique ou le seul incident.
- Dernier cycle J5 après `finished` respectant D, les budgets et l'heure limite ;
  finalisation partielle explicitée si une famille manque.
- Surcharge refusée, deux cycles manqués, budget/réserve exacts, volume maximal, arrêt individuel
  en vol et arrêt global ; délais et nettoyage mesurés en qualification loopback séparée.
- Redémarrage, veille, résultat tardif et second processus : pas de reprise, vol de lease ou
  double départ ; occurrence et état terminal préservés.
- PostgreSQL neuf et upgrade depuis le précédent prérempli, rollback, concurrence réelle,
  A→A/A→B→A ; aucune perte de provenance ni altération des ledgers J8/J7.
- Vue locale dynamique et replay sans transport fournisseur ; aucun lancement Playwright dans
  le démarrage normal ou les tests standards.

La [preuve documentaire initiale WO-058](docs/validation/WO058-LIVE-J4-J5-SCOPING-20260907.md)
distingue les critères de la revue de cadrage exécutée, dont le contrôle J6 alors bloqué par le
port 8087 occupé. Les résultats de réalisation ultérieurs sont consignés séparément dans les
rapports du WO ; la [preuve de capacité adaptative](docs/validation/WO058-ADAPTIVE-CAPACITY-20260907.md)
porte sur la v0.2. L'acceptation de cet ADR ne constitue pas un résultat de test.

Ordre de passage : **ADR rédigé, relu et accepté → WO-058 aligné puis validé → réalisation et
qualification hors fournisseur → manifeste concret et lancement opérateur du pilote**.
L'acceptation de l'ADR établit le cadre ; elle ne lance pas une campagne, ne valide pas les tests
et ne clôture pas le WO. Sa clôture Git reste soumise à la revue
humaine et à la fusion vers le train exact.

## 10. Historique des décisions et état d'acceptation

| Date | Événement | Portée |
|---|---|---|
| 2026-09-07 | Demande de cadrage live et création du draft WO-058 | Documentation initiale ; certaines bornes et politiques étaient proposées. |
| 2026-09-07 | Première série de réponses : pilote 1→2/3, signaux + secours 5 min, durée 4 h | Arbitrages fonctionnels validés par le propriétaire. |
| 2026-09-07 | Deuxième série : 1 000/3 000, isolation des erreurs métier, dernier cycle J5 | Remplace l'ancienne proposition d'arrêt global sur incompatibilité de schéma. |
| 2026-09-07 | Réponse 404 J5 : nouvelle interrogation au cycle normal suivant | Indisponibilité distinguée du retry technique terminal. |
| 2026-09-07 | Demande explicite de réaliser le plan ADR-SS-005 | Création de cette v0.1 proposée et alignement documentaire du WO ; pas d'acceptation formelle implicite. |
| 2026-09-07 | « Je valide formellement la v0.1 de l'ADR » | Acceptation explicite de la proposition au SHA-256 `48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e` ; observation à `2026-09-07T09:23:14Z`, distincte de l'heure exacte du message non disponible. |
| 2026-09-07 | Demande de réaliser le plan d'enregistrement de l'acceptation | Copie exacte figée, métadonnées actualisées sans changement normatif, renvois ciblés et WO prêt pour revue ; aucune validation implicite de réalisation ou clôture. |
| 2026-09-07 | « SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY paramétrable doit servir au nombre maximum de rencontres éligibles sélectionnable pour une même campagne live » | Le plafond configuré porte sur la sélection ; il ne doit pas invalider une campagne plus petite. |
| 2026-09-07 | Choix « Adapter selon le nombre retenu : 60 s pour 1–3 matchs, 90 s pour 4, 120 s pour 5 » puis demande explicite de 10 et 25 rencontres | Autorité de la v0.2. La progression est prolongée par D = max(60, 30 × (N − 1)) s ; l'heure exacte de ces messages n'est pas disponible. |
| 2026-09-07 | Demande de LINEUPS avant coup d'envoi, confirmée « Oui, collecte initiale puis périodique » | Autorité de la v0.3 ; J4 confirme `notstarted`, puis LINEUPS à D jusqu'au début constaté. Pas de statistiques/incidents prématch, ni de réduction de D. |

État historique au moment de l'enregistrement de l'acceptation v0.1, avant réalisation :

```text
ADR_SS_005_VERSION=0.1
ADR_SS_005_STATUS=ACCEPTED
FUNCTIONAL_CHOICES=OWNER_VALIDATED
ADR_SS_005_FORMAL_ACCEPTANCE=OWNER_ACCEPTED_2026_09_07
ADR_SS_005_ACCEPTANCE_OBSERVED_AT=2026-09-07T09:23:14Z
ADR_SS_005_ACCEPTANCE_MESSAGE_EXACT_TIME=NOT_AVAILABLE
WO058_STATUS=READY_FOR_OWNER_REVIEW
WO058_VALIDATION=PENDING_OWNER_REVIEW
RUNTIME_IMPLEMENTATION=NOT_STARTED
PROVIDER_CAMPAIGN=NOT_AUTHORIZED_BY_THIS_DOCUMENT
```

La copie figée identifie les octets de la v0.1 effectivement acceptés ; elle n'est pas réécrite
par la v0.2. Aucun commit n'est attribué au draft initial non committé. Toute modification
normative ultérieure exige une nouvelle version et une décision traçable.

État historique de la v0.3, distinct de la qualification et de la clôture Git du WO :

```text
ADR_SS_005_VERSION=0.3
ADR_SS_005_STATUS=ACCEPTED
CAPACITY_CHANGE_AUTHORITY=EXPLICIT_OWNER_REQUEST_2026_09_07
CADENCE_1_TO_5=OWNER_SELECTED
CADENCE_ABOVE_5=CONTINUATION_OF_SELECTED_PROGRESSION
SELECTION_MAXIMUM=CONFIGURED_ELIGIBLE_MATCHES
PREMATCH_LINEUPS=OWNER_CONFIRMED_INITIAL_AND_PERIODIC
NEW_MANIFEST_POLICY=live-v3
RUNTIME_IMPLEMENTATION=SEE_WO058_VALIDATION_REPORTS
PROVIDER_CAMPAIGN=NOT_AUTHORIZED_BY_THIS_DOCUMENT
```
