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
6. utiliser le bouton de déclenchement distinct.

L’intention porte la clé suivante :

```text
SCHEDULED_EVENTS|date=<date>|pagination=has-next-page|max=25
```

La phrase confirme le départ en page 1, la pagination dynamique et le plafond de 25 pages. Une
confirmation ne produit aucun transport. Une intention ne peut être exécutée qu’une fois. Après
un succès ou un incident, l’arrêt global est réappliqué ; sa levée efface l’intention terminale et
permet de préparer une nouvelle séquence, y compris pour une autre date.

## 3. Algorithme de pagination

Une collecte suit exclusivement cet ordre :

```text
page = 1
tant que page <= 25 :
    vérifier arrêt global, circuit et garde de concurrence
    respecter le délai minimal depuis le départ précédent
    exécuter exactement la requête de page courante
    persister le snapshot brut borné
    parser et classer le même snapshot
    si transport, persistance ou parsing est en incident : arrêt sans retry
    si hasNextPage == false : succès terminal
    si page == 25 et hasNextPage == true : arrêt PAGINATION_LIMIT_REACHED
    page = page + 1
```

Le parseur reste strict : une valeur `hasNextPage` absente, nulle ou non booléenne est une rupture
de schéma, pas une invitation à deviner la pagination. Aucune page 26 n’est demandée. Le plafond
local de 25 est une barrière de sécurité ; il ne prétend pas décrire le maximum fournisseur.

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

La déduplication existante peut retourner `DEDUPLICATED` lors d’une nouvelle collecte identique.
Ce résultat reste une persistance valide et ne contourne aucun contrôle de schéma.

## 5. Preuve terminale minimisée v3

La preuve téléchargeable contient seulement les métadonnées nécessaires :

```text
J3_MINIMIZED_EVIDENCE_VERSION=3
COLLECTION_DATE=<date>
PAGINATION_MODE=HAS_NEXT_PAGE
PROVIDER_FIRST_PAGE=1
MAXIMUM_PAGE_LIMIT=25
PAGES_ATTEMPTED=<liste ordonnée>
PAGES_COMPLETED_COUNT=<nombre>
LAST_COMPLETED_PAGE=<page ou NONE>
PAGE_<n>_HAS_NEXT_PAGE=<true|false|NONE>
AUTOMATIC_RETRY_EXECUTED=NO
POLLING_OR_SCHEDULE_EXECUTED=NO
COOKIES_TOKENS_ACCOUNT_SESSION_USED=NO
```

Un succès exige `hasNextPage=false` sur la dernière page et `true` sur toutes les précédentes. Un
arrêt `PAGINATION_LIMIT_REACHED` exige 25 pages parsées annonçant toutes une suite, sans tentative
de page 26. La preuve exclut le payload, l’URI, les en-têtes et l’identifiant de confirmation.

## 6. Hors périmètre

Cette unité n’ajoute ni tâche planifiée, ni polling, ni collecte multi-sport, ni endpoint libre,
ni traitement normalisé métier, ni envoi vers le VPS. Elle ne clôture pas à elle seule le jalon J3 :
une validation humaine ultérieure pourra qualifier le nouveau parcours répétable.
