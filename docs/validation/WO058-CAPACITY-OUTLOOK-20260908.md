# WO-058 — Estimation de capacité et perspective de 50 à 100 rencontres

Date : 2026-09-08. Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`,
`NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

Cette note distingue le palier courant et les estimations pour une extension future.
**Vingt rencontres à 100 secondes sont qualifiées en boucle locale synthétique**, avec
300 secondes pour les compositions en jeu, après cinq minutes d’initialisation puis trente
minutes établies. Les matrices vers cinquante ou cent rencontres restent des estimations.
La portée de la preuve est explicitée ci-dessous ; la fraîcheur fournisseur réelle reste
à observer ; `clean verify` et `-Pintegration-tests verify` ont réussi. Le lot live-v5
reste borné à vingt rencontres, avec une cible fixe de 100 secondes pour toute sélection
admise. La qualification v4 et les extrapolations n’élargissent pas cette capacité.

## Hypothèse initiale v4, conservée pour comparaison

Le [profil v4 figé](WO058-GROUPED-LIVE-V4-PROFILE-20260908.json), de SHA-256
`5d34019578a5f1b616f3570aa47d8451afe283eb52dee73e1c94eeb4f370fcc1`, sert ici
uniquement d'hypothèse initiale de coût. Sa [preuve native](WO058-GROUPED-LIVE-V4-20260908.md)
porte sur Chromium en boucle locale, PostgreSQL isolé, et des corps de 64 Kio en régime établi.
Elle distingue les pics initiaux de 5 Mio. Ce n'est pas une mesure de latence Internet
ni une garantie pour des corps récurrents de 5 Mio.

| Famille | Requête | Traitement local | Coût total retenu |
|---|---:|---:|---:|
| J4 | 0,30 s | 0,40 s | 0,70 s |
| Incidents | 0,35 s | 0,45 s | 0,80 s |
| Statistiques | 0,30 s | 0,35 s | 0,65 s |
| Compositions | 0,30 s | 0,35 s | 0,65 s |

Le modèle conserve un seul transport séquentiel et 10 % de capacité non allouée.
Il suppose une pause d'une seconde entre deux groupes de la même campagne v5,
et aucune attente artificielle entre les appels autorisés d'un groupe.
Les transitions vers une autre session ou un autre parcours gardent leur pause historique.
Les démarrages, changements de phase, fins et variations de coûts nécessitent en outre
un rejeu de l'ordonnanceur et une qualification native : une moyenne admissible ne suffit pas.

Pour `N` rencontres toutes en jeu, avec un tour critique toutes les `D` secondes et des
compositions toutes les 300 secondes, la charge moyenne simplifiée est :

```text
charge = N × ((0,70 + 0,80 + 0,65 + 1,00) / D + 0,65 / 300)
       = N × (3,15 / D + 0,65 / 300)

charge ≤ 0,90
D_min = 3,15 × N / (0,90 − 0,65 × N / 300)
```

Cette formule suppose que chaque collecte de compositions peut rejoindre un tour critique
sans groupe supplémentaire. Elle est une borne de débit moyen. Elle ne garantit pas que le
calendrier de ces groupes respecte chaque échéance.

## Première matrice estimative, antérieure à la mesure v5

La troisième colonne présente le premier diviseur entier de 300 secondes, au moins égal à
60 secondes et à la borne moyenne. Elle illustre la contrainte du calendrier actuel ; elle
n'active aucune nouvelle cadence ni aucune capacité dans le Lab. Le candidat initial v5 utilisait 75 secondes
pour toutes ses nouvelles préparations admises, y compris les petites sélections.

| Rencontres simultanées | Borne moyenne `D_min` | Premier calendrier compatible avec `300 / D` entier et `D ≥ 60 s` |
|---:|---:|---:|
| 5 | 17,71 s | 60 s |
| 10 | 35,86 s | 60 s |
| 15 | 54,47 s | 60 s |
| 20 | 73,54 s | 75 s |
| 30 | 113,17 s | 150 s |
| 50 | 198,95 s | 300 s |
| 75 | 320,34 s, borne optimiste seulement | Aucun : la borne dépasse 300 s |
| 100 | 460,98 s, borne optimiste seulement | Aucun : la borne dépasse 300 s |

Avec vingt rencontres à 75 secondes, chaque période de cinq minutes contient quatre tours
critiques et une collecte de compositions par rencontre :

```text
20 × (4 × (1,00 + 0,70 + 0,80 + 0,65) + 0,65) = 265 secondes
capacité allouable sur 300 secondes = 270 secondes
```

Il reste donc cinq secondes dans l'enveloppe allouable, en plus des trente secondes réservées
par la marge de 10 %. Cette estimation a motivé le premier candidat de 75 secondes ; elle ne remplace pas
la qualification native dédiée à vingt rencontres. Les critères de réception du
candidat 75 s étaient un P95 au plus égal à 80 secondes et un maximum de 90 secondes par couple
rencontre/famille critique, ainsi qu'un retard nominal P95 des compositions au plus égal à
15 secondes, après la phase initiale mesurée séparément.

À vingt secondes, le seul coût des pauses consomme déjà vingt secondes pour vingt rencontres,
avant les requêtes et leur traitement. Le modèle ne permet donc pas de promettre cette cadence
pour quinze à vingt rencontres, ni pour cinquante rencontres, avec les hypothèses retenues.

## Limites du calendrier et cas au-delà de cinq minutes

Les chiffres de cette section illustrent l’hypothèse initiale v4. Les bornes actualisées
avec les coûts mesurés lors de l’essai v5 figurent dans la section suivante.

Le calendrier actuel répartit les compositions sur un nombre entier de tours critiques :
`phaseCount = 300 / D`. Pour les exemples courants au moins égaux à une minute, les cadences
60, 75, 100, 150 et 300 secondes satisfont cette contrainte. Les estimations de 113 ou 199 secondes
ne peuvent donc pas être appliquées directement à cet ordonnanceur en conservant exactement
300 secondes pour les compositions.

Dès que `D > 300`, les compositions doivent aussi être collectées entre les tours critiques.
Cela crée des groupes supplémentaires et leurs pauses ; les valeurs de 320,34 et 460,98 secondes
de la matrice deviennent insuffisantes comme planification. Même avec `D = 300`, un groupe
complet coûte 3,80 secondes par rencontre : `floor(270 / 3,80) = 71` rencontres est la borne
moyenne maximale, avant les contrôles de phases et de latence.

Un futur calendrier dissociant les familles devra compter explicitement les groupes supplémentaires,
leurs collisions et la priorité des échéances. Il faudra ensuite vérifier chaque couple
rencontre/famille et les phases initiales/finales. Cette note ne propose ni de changer le nombre
de transports ni de lancer des appels concurrents.

## Mesure v5 du candidat 75 secondes et correction de la cible

Le test natif de cinq minutes d’initialisation puis trente minutes établies à vingt rencontres
réussit la cadence, avec 1 835 appels et zéro cycle manqué. Les maxima établis arrondis au
multiple supérieur de 50 ms sont toutefois plus élevés que l’hypothèse initiale v4 :

| Famille | Requête | Traitement local | Total retenu |
|---|---:|---:|---:|
| J4 | 0,40 s | 0,60 s | 1,00 s |
| Incidents | 0,40 s | 0,50 s | 0,90 s |
| Statistiques | 0,35 s | 0,50 s | 0,85 s |
| Compositions | 0,30 s | 0,40 s | 0,70 s |

Le profil diagnostique et ses échantillons sont conservés dans
[la preuve du candidat 75 s](WO058-GROUPED-LIVE-V5-CANDIDATE-75-PROFILE-20260908.json).
Le rejeu Java de la version du candidat à 75 secondes renvoie une capacité de **17** et refuse vingt rencontres.
Le profil reste non qualifié pour vingt. Les coûts ne sont pas abaissés pour obtenir une admission.

Avec ces coûts, la nouvelle borne moyenne est :

```text
D_min = 3,75 × N / (0,90 − 0,70 × N / 300)
20 à 75 s : 20 × (4 × 3,75 + 0,70) = 314 s, au-dessus des 270 s allouables
20 à 100 s : 20 × (3 × 3,75 + 0,70) = 239 s, dans les 270 s allouables
```

| Rencontres | Borne moyenne actualisée | Candidat compatible avec le calendrier actuel, D ≥ 60 s |
|---:|---:|---:|
| 5 | 21,11 s | 60 s |
| 10 | 42,78 s | 60 s |
| 15 | 65,03 s | 75 s |
| 17 | 74,10 s | 75 s |
| 20 | 87,89 s | 100 s, candidat retenu à l’issue de cet essai |
| 30 | 135,54 s | 150 s, estimation future |
| 50 | 239,36 s | 300 s, estimation future |
| 75 | 387,93 s, borne optimiste | Calendrier distinct nécessaire |
| 100 | 562,50 s, borne optimiste | Calendrier distinct nécessaire |

Les cadences de cette matrice servent à comparer les options de calendrier. Pour le lot
courant, toute sélection admise de quinze à vingt rencontres conserve la même cible de
100 secondes ; sélectionner quinze rencontres ne choisit pas automatiquement 75 secondes.

Ces nombres indiquent une limite de débit moyen avec marge, pas un minimum garanti par
l’ordonnanceur. Au-delà de 300 secondes, ils omettent les pauses de groupes de compositions
supplémentaires. À 300 secondes, la borne moyenne devient `floor(270 / 4,45) = 60` rencontres,
avant qualification des phases. Rafraîchir cinquante à cent matchs rapidement exige donc
d’abord de réduire les coûts mesurés et de revoir le calendrier des familles. Le budget de
20 000 appels n’augmente pas cette capacité de débit.

Cette mesure à 75 secondes a conduit au candidat de 100 secondes, dont la qualification
distincte est décrite ci-dessous. La matrice précédente reste liée aux coûts de cet essai.

## Profil final à 100 secondes et matrice actualisée

Le [profil final v5](WO058-GROUPED-LIVE-V5-PROFILE-20260908.json), SHA-256
`923c4499d2005d4d2f91499148f749dc7f80ea0868ff1cfc7a42106ac9863225`, porte le statut
`QUALIFIED_SYNTHETIC_LOOPBACK_WITH_STATED_SCOPE` et une capacité de vingt rencontres.
Les [mesures natives](WO058-GROUPED-LIVE-V5-NATIVE-20260908.json) couvrent 2 100,010 secondes,
dont 1 800,010 établies : 1 413 appels au total, 1 200 établis, zéro cycle manqué et
80 couples rencontre/famille contrôlés. Les 24 scénarios d’admission Java passent avec
les enveloppes suivantes, qui conservent les planchers de coûts de l’essai précédent :

| Famille | Requête | Traitement local | Total retenu |
|---|---:|---:|---:|
| J4 | 0,40 s | 0,60 s | 1,00 s |
| Incidents | 0,40 s | 0,50 s | 0,90 s |
| Statistiques | 0,35 s | 0,50 s | 0,85 s |
| Compositions | 0,35 s | 0,45 s | 0,80 s |

Les intervalles de réception critiques mesurés atteignent 100,183 s au P95 et 100,520 s au
maximum, sous les critères de 105/115 s. Le retard nominal des compositions vaut 2,207 s
au P95 et 2,633 s au maximum. La qualification reste locale : corps établis de 64 Kio,
délais contrôlés, Chromium et PostgreSQL isolé, avec traitement SQL et contrôle d’espace
Docker mesurés. Les quatre-vingts premières réponses de 5 Mio forment une vague initiale
séparée, hors des enveloppes constantes. Les corps récurrents de 5 Mio, la latence Internet
et la fraîcheur fournisseur jusqu’à l’écran ne sont pas qualifiés par cet essai.

La hausse du coût des compositions à 0,80 s actualise la borne moyenne :

```text
D_min = 3,75 × N / (0,90 − 0,80 × N / 300)
20 à 100 s : 20 × (3 × 3,75 + 0,80) = 241 s, dans les 270 s allouables
```

| Rencontres | Borne moyenne avec le profil final | Premier calendrier compatible, D ≥ 60 s |
|---:|---:|---:|
| 5 | 21,15 s | 60 s, estimation de calendrier |
| 10 | 42,94 s | 60 s, estimation de calendrier |
| 15 | 65,41 s | 75 s, estimation de calendrier |
| 17 | 74,59 s | 75 s, estimation de calendrier |
| 20 | 88,58 s | **100 s, qualifié dans la portée synthétique décrite** |
| 30 | 137,20 s | 150 s, estimation future |
| 50 | 244,57 s | 300 s, estimation future |
| 75 | 401,79 s, borne optimiste | Calendrier distinct nécessaire |
| 100 | 592,11 s, borne optimiste | Calendrier distinct nécessaire |

Ces estimations conservent la marge de 10 % et une seconde entre groupes. Seul le palier
courant, limité à vingt rencontres à 100 secondes, est activable avec son profil validé.
À 300 secondes, `floor(270 / 4,55) = 59` est seulement une borne moyenne avant contrôle
des phases ; au-delà de 300 secondes, la formule omet les groupes supplémentaires de
compositions. Les cadences de 60 ou 75 secondes dans la matrice ne sont pas des réglages
automatiquement choisis pour les petites sélections v5.

## Budgets et autonomie

Le débit théorique en jeu est `N × (180 / D + 0,2)` appels par minute : trois familles critiques
à chaque tour et une collecte de compositions toutes les cinq minutes.

| Situation | Débit établi | Autonomie théorique du seul plafond de 20 000 appels |
|---|---:|---:|
| 15 rencontres à 100 s | 30 appels/min | 666,7 min, soit environ 11 h 07 |
| 20 rencontres à 100 s | 40 appels/min | 500 min, soit environ 8 h 20 |
| 30 rencontres à 150 s, estimation future | 42 appels/min | 476,2 min |
| 50 rencontres à 300 s, estimation future | 40 appels/min | 500 min |

La durée autorisée reste quatre heures et limite donc les deux premiers cas avant le plafond
théorique d'appels. Quatre heures en jeu à 100 secondes représentent environ 480 appels par
rencontre, soit 9 600 appels pour vingt rencontres, hors ajustements d'initialisation et de fin.
Le plafond de 2 500 appels par rencontre laisse une marge supérieure à ce besoin nominal.
Les quatre appels de réserve par rencontre active pour le dernier contrôle/finalisation sont
conservés. Les changements de phase, indisponibilités, interruptions et la durée effectivement
restante modifient l'autonomie réelle.

Le plafond brut reste **15 728 640 000 octets**, soit environ 14,65 Gio ; il est indépendant du
plafond de 20 000 appels. Des corps volumineux peuvent l'épuiser avant le budget d'appels.
La vérification locale de stockage conserve sa réserve d'espace et la collecte s'arrête lorsque
l'une des limites s'applique. L'application n'allonge pas la cadence pour prolonger la campagne.

## Fraîcheur visible et travaux ultérieurs mesurables

La lecture locale de l'interface toutes les cinq secondes affiche ce qui est déjà reçu et publié.
Elle ne déclenche aucun appel fournisseur. Réduire uniquement cet intervalle d'affichage ne réduit
ni l'attente du prochain tour de collecte, ni la durée réseau, ni le traitement local.
La fraîcheur observée dépend aussi du moment où le fournisseur rend la donnée disponible :
elle ne peut pas être déduite de la seule heure du dernier changement visible dans son interface.

Pour préparer une extension au-delà de vingt rencontres :

- Mesurer séparément attente de groupe, requête, normalisation et écritures/lectures PostgreSQL,
  avec tailles de corps et variations représentatives. Comparer la réception réelle à l'échéance
  de chaque famille, et conserver les maxima en plus des percentiles.
- Examiner les lectures SQL répétées sur le chemin de chaque appel. Les réduire seulement si
  les mesures montrent leur coût et si les contrôles de propriétaire, budget et arrêt restent
  vérifiés avant le transport. Une optimisation supposée ne justifie pas un profil plus bas.
- Évaluer un calendrier propre à chaque famille si 300 secondes devient inférieur au tour critique,
  avec comptage des groupes supplémentaires et des phases de fin.
- Étudier une sélection ou une priorité explicitement définie des rencontres présentant le plus
  d'intérêt, avec une garantie d'équité et une cadence visible pour les autres. Cette règle devra
  être choisie par le propriétaire puis qualifiée ; aucun tri silencieux n'est ajouté ici.

Points de référence du code : `LiveAdmissionPolicy.qualifiedCapacityV5`,
`GroupedLiveAdmissionSimulation`, `GroupedLiveScheduleV4` dans sa branche live-v5,
et `LiveCampaignPresentation` pour l'autonomie et les échéances affichées.
L'estimation, la simulation, la qualification native et l'observation fournisseur restent
quatre niveaux de preuve distincts.

## Paliers d'opérabilité et objectif du week-end

L'objectif opérateur du week-end est de disposer d'une collecte J4/J5 rapide, observable et
dynamique. Le palier de ce lot est une campagne de quinze à vingt rencontres à 100 secondes,
qualifiée en boucle locale synthétique, avec les deux vérifications Maven complètes réussies,
et encore à observer chez l’opérateur après intégration du lot. Le plafond théorique futur de cinquante à cent
rencontres constitue un objectif d'architecture distinct : il n'est pas activé par V40.

| Palier | Preuve attendue pour le rendre utilisable |
|---|---|
| 15–20 rencontres, lot actuel | Qualification synthétique obtenue à vingt rencontres sur 35 minutes, dont 30 établies : 80 couples contrôlés, zéro cycle manqué et worker fermé. Profil fondé sur les maxima établis et leurs planchers antérieurs, admission Java validée. Les deux vérifications Maven complètes réussissent ; l’observation de fraîcheur fournisseur reste à effectuer. |
| Affichage de 50–100 rencontres | Charge synthétique de cent rencontres, 300 familles critiques, 13 500 métriques statistiques et 3 000 incidents ; publication locale vers rendu sous dix secondes, y compris pour une réception de contenu identique. Ce test n'émet aucun appel fournisseur et ne contient pas de compositions. |
| Collecte de 50 rencontres | Profil de coût actualisé puis calendrier propre aux familles ; rejeu des démarrages et fins ; nouvelle qualification native complète à cinquante. La borne moyenne est désormais 244,57 s avec le profil final à 100 s ; les étapes précédentes donnaient 198,95 s puis 239,36 s. Le calendrier actuel conduirait à un candidat de 300 s, encore à qualifier. |
| Collecte de 100 rencontres | Revoir le calendrier et mesurer les coûts locaux avant de choisir une cadence. Maintenir une cadence proche de vingt secondes pour toutes les familles exigerait une capacité de transport différente de la file séquentielle actuelle et une décision dédiée ; aucune concurrence n'est introduite dans ce lot. |

L'opérabilité doit rendre visibles, pour chaque famille et chaque rencontre, la cible,
l'échéance, la dernière réception, le retard et la raison d'un arrêt. Les validations locales
doivent ensuite être confrontées aux observations opérateur sur les corps et latences réels.
Le budget d'appels permet de prolonger une campagne ; il n'augmente pas son débit de transport.
