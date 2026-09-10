# WO-058 — Observations opérateur de trois campagnes live-v8 arrêtées par HTTP 403

> **Statut : observation locale, non qualifiante auprès du fournisseur.**
>
> Ce relevé décrit uniquement les preuves persistées par le Lab. Il ne démontre
> ni un seuil d’acceptation de SofaScore, ni la cause précise du refus, ni la
> responsabilité d’une famille d’endpoint particulière.

## Objet et périmètre

Le 10 septembre 2026, l’opérateur a lancé manuellement trois campagnes locales
`live-v8`. Elles se sont toutes arrêtées après la réception d’un HTTP 403 complet.
Ce document fige les observations après coup ; sa rédaction et les vérifications
associées n’ont déclenché aucun appel fournisseur.

L’analyse utilise seulement les preuves locales durables suivantes :

- les diagnostics de transport associés aux appels de campagne ;
- les instants `REQUEST_SENT` documentés par le worker ;
- les états et diagnostics de clôture persistés.

Une réservation d’appel, une autorisation de budget partagé, une réponse reçue ou
un trafic extérieur au Lab ne sont pas assimilés à un départ effectivement observé.

## Méthode de comptage

Un départ est compté lorsqu’un diagnostic de transport persistant contient
`requested_at`, c’est-à-dire lorsque le worker a documenté `REQUEST_SENT`. Le
diagnostic peut ensuite progresser vers `HEADERS_RECEIVED` ou `COMPLETE` sans
perdre cet instant : la vue ne filtre donc pas sur sa phase finale.

Les pics sont calculés sur ces départs documentés, par campagne et par famille :

- fenêtre glissante de 60 secondes ;
- fenêtre glissante de 5 minutes ;
- à chaque fin de fenêtre `t`, les départs retenus appartiennent à `(t − fenêtre, t]`.

Cette méthode mesure l’activité que la campagne a documentée. Elle ne mesure pas
les appels de navigateur, processus ou équipement pouvant partager la sortie
réseau en dehors du Lab.

## Résultats observés

| Campagne | Cibles effectives | Début UTC | Premier HTTP 403 UTC | Durée avant 403 | `REQUEST_SENT` observés | Pic 60 s | Pic 5 min | Famille de l’appel refusé |
| --- | ---: | --- | --- | ---: | ---: | ---: | ---: | --- |
| `0ac5c1bd-b6cc-40bb-b63c-a59e05816c23` | 10 | `2026-09-10T16:37:41.704620Z` | `2026-09-10T16:46:42.885574Z` | 9 min 01,181 s | 147 | 37 | 89 | `EVENT_DETAILS` |
| `0c5c1386-3519-46cb-bffc-634ffd351c0f` | 8 | `2026-09-10T16:49:21.554658Z` | `2026-09-10T16:57:26.463956Z` | 8 min 04,909 s | 180 | 29 | 113 | `EVENT_DETAILS` |
| `01542403-5099-4da9-9f22-4903004df17d` | 8 | `2026-09-10T17:05:46.481947Z` | `2026-09-10T17:17:30.994046Z` | 11 min 44,512 s | 263 | 29 | 113 | `EVENT_LINEUPS` |

Pour la troisième campagne, la répartition des 263 départs documentés est :

| Famille | Départs `REQUEST_SENT` observés |
| --- | ---: |
| J4 détails (`EVENT_DETAILS`) | 74 |
| J5 statistiques (`EVENT_STATISTICS`) | 43 |
| J5 incidents (`EVENT_INCIDENTS`) | 73 |
| J5 compositions (`EVENT_LINEUPS`) | 73 |

## Constats établis

Les trois arrêts sont associés à un HTTP 403 reçu intégralement par le transport.
Le dernier échec de chacune de ces campagnes n’est donc pas seulement un timeout
Playwright isolé.

Les pics locaux observés — 37, 29 et 29 départs dans une fenêtre de 60 secondes —
restent sous le budget local de 45 départs/minute. Les observations ne correspondent
donc pas à une interruption du garde-fou local de cadence.

La famille en cours au moment du refus n’est pas stable : deux refus sont associés
à `EVENT_DETAILS`, un à `EVENT_LINEUPS`. Cette variation ne permet pas d’attribuer
le refus à une famille J4 ou J5 donnée.

Des réponses 404 normales concernant les statistiques J5 ont été persistées et
espacées sans provoquer l’arrêt global de la campagne. Elles restent distinctes d’un
refus HTTP 403 ou 429.

## Limites d’inférence

Les observations établissent qu’un refus HTTP 403 complet est survenu après une
activité locale mesurée. Elles n’établissent pas :

- le seuil de débit accepté par SofaScore ;
- une durée de campagne garantie ;
- le caractère déclencheur de l’endpoint qui a reçu le 403 ;
- l’absence de critères fournisseur autres que la cadence ;
- le volume de trafic effectué hors du Lab ou par d’autres clients partageant la
  même sortie réseau.

Le fait que le budget local de 45 départs/minute n’ait pas été atteint ne constitue
pas une preuve qu’une cadence inférieure est acceptée durablement par le fournisseur.

## Décision de sûreté et suite de WO-058

La suspension persistante après HTTP 403 ou 429 demeure la règle. Ce relevé ne
justifie ni réarmement automatique, ni nouvelle tentative automatique, ni relèvement
supplémentaire des budgets locaux.

La vue de pression ajoutée par ce lot présente, pour chaque campagne, les départs
`REQUEST_SENT` documentés, leurs pics glissants et leur répartition par famille. Elle
ne les présente ni comme un quota, ni comme un seuil fournisseur. Toute évolution de
cadence, de périmètre de collecte ou de capacité doit rester qualifiée hors fournisseur
avant une nouvelle campagne réelle.
