# J3 — Collecte manuelle répétable à pagination dynamique

## 1. Décision et objectif

La qualification humaine des cinq pages du `2026-08-13` a confirmé le modèle d’URI :

```text
https://www.sofascore.com/api/v1/sport/football/scheduled-tournaments/<date>/page/<page_number>
```

`<date>` est une date ISO `AAAA-MM-JJ`. La page suivante existe uniquement lorsque le document
JSON courant est compatible avec `scheduled-events-v1` et contient `hasNextPage=true`. Le nombre
de pages n’est donc pas une constante fonctionnelle : les cinq pages qualifiées constituent une
observation pour une date, pas une limite fournisseur générale.

Cette unité remplace le parcours terminal de qualification `pages 3-5` par une collecte complète
répétable depuis le tableau de bord. Elle n’exécute aucun appel fournisseur pendant le
développement, les tests ou la validation automatisée.

## 2. Préconditions et confirmation

Chaque collecte est une nouvelle séquence opérateur explicite :

1. lever l’arrêt global ;
2. activer le circuit ;
3. choisir une date ;
4. préparer une intention éphémère ;
5. recopier exactement la phrase et acquitter les limites ;
6. choisir une seule action de déclenchement distincte : pagination directe ou import du lot JSON
   complet.

L’intention porte la clé suivante :

```text
SCHEDULED_EVENTS|date=<date>|pagination=has-next-page|max=35
```

La phrase confirme le départ en page 1, la pagination dynamique et le plafond de 35 pages. Une
confirmation ne produit aucun transport. Une intention ne peut être exécutée qu’une fois. Après
un succès ou un incident, l’arrêt global est réappliqué ; sa levée efface l’intention terminale et
permet de préparer une nouvelle séquence, y compris pour une autre date.

## 3. Algorithme de pagination directe

Une collecte suit exclusivement cet ordre :

```text
page = 1
tant que page <= 35 :
    vérifier arrêt global, circuit et garde de concurrence
    construire la clé exacte SCHEDULED_EVENTS|date=<date>|page=<page>
    rechercher un snapshot HTTP 2xx, PARSED, intègre, produit par le parseur courant
    si ce snapshot est âgé de moins de 10 minutes :
        le reparcourir hors ligne avec scheduled-events-v1
        utiliser son hasNextPage sans attente, transport ni écriture
    sinon :
        respecter le délai minimal depuis le dernier départ fournisseur réel
        exécuter exactement la requête de page courante
        persister le snapshot brut borné
        parser et classer le même snapshot
    si transport, persistance ou parsing est en incident : arrêt sans retry
    si hasNextPage == false : succès terminal
    si page == 35 et hasNextPage == true : arrêt PAGINATION_LIMIT_REACHED
    page = page + 1
```

Le parseur reste strict : une valeur `hasNextPage` absente, nulle ou non booléenne est une rupture
de schéma, pas une invitation à deviner la pagination. Aucune page 36 n’est demandée. Le plafond
local de 35 est une barrière de sécurité ; il ne prétend pas décrire le maximum fournisseur. Une
page 27 portant `hasNextPage=false` reste un terminal normal : la collecte ne doit jamais être
prolongée artificiellement jusqu'à la page 35.

Le TTL provient de la définition `SCHEDULED_EVENTS` du catalogue et vaut `PT10M`. Sa borne est
stricte : un checkpoint `cached_at` exactement dix minutes avant l’évaluation est expiré. La
recherche est effectuée sur la clé date/page complète, la version exacte `scheduled-events-v1` et
le statut historique `PARSED`. La taille et le SHA-256 des octets relus sont recalculés avant usage.
Le reparsing courant reste obligatoire : le cache ne transforme jamais un contenu incompatible en
succès et n’affaiblit aucun contrôle de schéma.

La table append-only de migration V3 ne contient aucun payload. Elle associe la clé exacte, le
snapshot immuable, la version du parseur et l’heure de la dernière observation parsée. Ce checkpoint
est mis à jour après succès du parsing. Ainsi, une réponse fournisseur identique, dédupliquée vers
un ancien snapshot, rafraîchit le TTL sans modifier les horaires ni les octets historiques de ce
snapshot.

Le délai de trois secondes s’applique entre deux départs fournisseur, et non entre deux pages
logiques. Ainsi, une page servie par le cache n’attend pas trois secondes ; si elle se trouve entre
deux cache misses, le second transport attend néanmoins jusqu’à trois secondes après le départ du
transport précédent.

### 3.1 Variante locale sans transport

Après la même confirmation, l'opérateur peut sélectionner en une fois les seuls corps JSON des
pages J3 obtenus hors de l'application. Les fichiers nommés `page-1.json` à `page-N.json` sont
triés par leur numéro et le lot entier est validé avant que l'intention soit réclamée : une à
35 pages contiguës, 5 Mio maximum par page, 25 Mio maximum au total, forme
`SCHEDULED_TOURNAMENT_LIST`, `hasNextPage=true` avant N puis `false` à N, et aucun motif sensible.
Un lot qui contient une page 36 est refusé avant claim, persistance ou mutation locale.

Une fois le lot accepté, chaque page est persistée sous la clé date/page habituelle avec le mode
`MANUAL_LOCAL_JSON_IMPORT`, puis classée par `scheduled-events-v1`. Cette variante ne consulte ni
n'alimente le cache fournisseur, ne déclenche aucun transport et n'attend pas le délai inter-appels.
Elle partage toutefois la même garde de concurrence, le même contrôle opérateur et le même verrou
terminal que la voie directe. Les deux sources ne peuvent pas être mélangées dans une collecte.

## 4. Invariants réseau et de persistance

Les invariants précédents restent obligatoires :

- origine exacte `https://www.sofascore.com` et chemin construit par type, sans URI libre ;
- concurrence maximale `1` et délai minimal de trois secondes entre départs ;
- aucun proxy, redirection, cookie, jeton, compte ou donnée de session ;
- aucune planification, aucun polling et aucun rafraîchissement automatique ;
- arrêt au premier incident, aucun retry automatique ;
- octets bruts bornés et persistés avant parsing ;
- classification rattachée au même snapshot, sans réécriture du payload ;
- exposition Web et Actuator limitée à `127.0.0.1` ;
- chemin désactivé par défaut par la configuration locale.

L'import local est un mode d'acquisition distinct, pas une imitation de navigateur : seuls les
corps JSON sont acceptés ; HAR, en-têtes, cookies, jetons et données de session sont refusés avant
la réclamation de l'intention. Un échec direct déjà terminal ne bascule jamais automatiquement vers
l'import : l'opérateur doit réarmer, préparer et confirmer une nouvelle intention.

La déduplication existante peut retourner `DEDUPLICATED` lors d’une nouvelle collecte identique.
Ce résultat reste une persistance valide et ne contourne aucun contrôle de schéma. Un cache hit
porte au contraire l’issue de preuve `CACHE_HIT` : aucune méthode de sauvegarde ou de classement
n’est invoquée et le snapshot historique reste immuable.

## 5. Preuve terminale minimisée v6

La preuve téléchargeable contient seulement les métadonnées nécessaires :

```text
J3_MINIMIZED_EVIDENCE_VERSION=6
COLLECTION_DATE=<date>
PAGINATION_MODE=HAS_NEXT_PAGE
CACHE_POLICY=FRESH_PARSED_SNAPSHOT_FIRST
CACHE_TTL_SECONDS=600
PROVIDER_FIRST_PAGE=1
MAXIMUM_PAGE_LIMIT=35
PAGES_RESOLVED=<liste ordonnée>
PROVIDER_PAGES_REQUESTED=<liste ou NONE>
CACHE_HIT_PAGES=<liste ou NONE>
LOCAL_JSON_IMPORT_PAGES=<liste ou NONE>
PROVIDER_REQUEST_COUNT=<nombre>
CACHE_HIT_COUNT=<nombre>
LOCAL_JSON_IMPORT_COUNT=<nombre>
PAGES_COMPLETED_COUNT=<nombre>
LAST_COMPLETED_PAGE=<page ou NONE>
PAGE_<n>_RESOLUTION_SOURCE=<PROVIDER|CACHE|LOCAL_JSON_IMPORT>
PAGE_<n>_CACHE_STORED_AT=<instant|NONE>
PAGE_<n>_PROVIDER_REQUEST_EXECUTED=<YES|NO>
PAGE_<n>_LOCAL_JSON_IMPORT_EXECUTED=<YES|NO>
PAGE_<n>_HAS_NEXT_PAGE=<true|false|NONE>
AUTOMATIC_RETRY_EXECUTED=NO
POLLING_OR_SCHEDULE_EXECUTED=NO
COOKIES_TOKENS_ACCOUNT_SESSION_USED=NO
```

Un succès exige `hasNextPage=false` sur la dernière page et `true` sur toutes les précédentes. La
preuve distingue sans ambiguïté les transports, cache hits et imports ; une tentative échouée avant
le frame `READY` reste une résolution fournisseur mais porte `PROVIDER_REQUEST_EXECUTED=NO` et ne
rejoint pas `PROVIDER_PAGES_REQUESTED` ni `PROVIDER_REQUEST_COUNT`. Les compteurs de résultat ne
peuvent ni mélanger les voies ni présenter un import comme un appel fournisseur. Un
arrêt `PAGINATION_LIMIT_REACHED` exige 35 pages parsées annonçant toutes une suite, sans tentative
de page 36. La preuve exclut le payload, l’URI, les en-têtes et l’identifiant de confirmation.

## 6. Hors périmètre

Cette unité n’ajoute ni tâche planifiée, ni polling, ni collecte multi-sport, ni endpoint libre,
ni récupération automatique de corps JSON hors de l'application, ni traitement normalisé métier,
ni envoi vers le VPS. Les tests de la politique de cache et de l'import local sont entièrement
hors ligne et n’exécutent aucun appel fournisseur.
