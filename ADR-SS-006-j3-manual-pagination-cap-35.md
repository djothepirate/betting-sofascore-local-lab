# ADR-SS-006 — Plafond local de la pagination manuelle J3 à 35 pages

- **Version :** 0.1.
- **Statut :** `ACCEPTED` — décision du propriétaire du Betting Project le 12 septembre 2026 ; la réalisation et sa qualification restent distinctes.
- **Date :** 2026-09-12.
- **Décideur :** propriétaire du Betting Project.
- **Work Order :** [WO-SS-20260912-059](docs/work_orders/active/WO-SS-20260912-059-j3-pagination-cap-35.md).
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260912-059`.
- **Base :** `4f0413b`.

## 1. Contexte

La collecte manuelle J3 `SCHEDULED_EVENTS` utilise une pagination pilotée par le booléen parsé
`hasNextPage`. Le précédent plafond local de 25 pages a arrêté la séquence avant la page 26 alors
que l'observation opérateur du 12 septembre 2026 signalait 27 pages disponibles pour la date
concernée. Cette observation ne constitue ni une mesure de quota, ni une propriété permanente du
fournisseur.

Le plafond est une barrière locale du Lab. Il doit donc être assez large pour couvrir ce cas
observé, tout en restant fermé, explicite et contrôlé par les mêmes garanties manuelles que le
parcours J3 existant.

## 2. Décision

### 2.1 Borne active des nouvelles collectes J3

Les nouvelles collectes manuelles J3, directes ou importées localement, acceptent uniquement les
pages entières **1 à 35**. La clé d'intention porte donc
`SCHEDULED_EVENTS|date=<date>|pagination=has-next-page|max=35` et la preuve minimisée porte
`MAXIMUM_PAGE_LIMIT=35`.

La pagination reste séquentielle et pilotée exclusivement par `hasNextPage` après persistance et
parsing de chaque page :

1. `hasNextPage=false` termine normalement la collecte sur la page courante, y compris en page 27 ;
2. `hasNextPage=true` sur une page strictement inférieure à 35 rend seulement la page suivante
   admissible ;
3. `hasNextPage=true` sur la page 35 produit `PAGINATION_LIMIT_REACHED` avant toute tentative de
   page 36 ;
4. une page 36, fournie par import ou demandée au domaine, est refusée avant claim, persistance ou
   départ fournisseur.

La borne 35 ne décrit pas et ne cherche pas à déduire une limite du fournisseur.

### 2.2 Garanties inchangées

Cette décision ne crée aucun endpoint, aucune origine, aucune entrée d'allowlist, aucun transport
ni aucune autorisation réseau supplémentaire. Elle conserve :

- une action opérateur explicite par collecte, avec concurrence maximale de un ;
- la lecture cache-first des snapshots J3 `PARSED` frais avant tout transport ;
- le délai minimal de trois secondes entre deux départs fournisseur réels ;
- l'arrêt au premier incident, sans retry, polling, scheduler ni relance compensatrice ;
- les limites d'import de **5 Mio par page** et **25 Mio par lot** ;
- les protections contre les URI libres, redirections, proxy, cookie, jeton, compte, session et
  contenu sensible.

Les validations automatisées et la réalisation de ce Work Order n'effectuent aucun appel
fournisseur.

### 2.3 Compatibilité de l'audit J8 et migration

Une nouvelle campagne J8 `J3_SCHEDULED_EVENTS` est créée avec `maximum_units=35`. Les campagnes
historiques persistées avec `maximum_units=25` restent lisibles, auditables et bornées à leur
propre valeur historique ; elles ne sont ni réécrites ni promues à 35.

La migration `V53__j3_scheduled_events_page_cap_35.sql` est append-only. Elle élargit les
contraintes de campagne et de clé canonique afin d'accepter la nouvelle borne tout en conservant
explicitement la valeur historique 25. Elle ne modifie ni ne supprime les snapshots, preuves ou
campagnes existants.

### 2.4 Relation avec ADR-SS-002 v1.1

Cette ADR ne réécrit ni l'acceptation de v1.1 d'ADR-SS-002 ni l'historique v1.0, dont les vingt
tentatives arrêtées restent immuables. Elle remplace prospectivement la seule borne J3 A1 `1..25`
d'ADR-SS-002 v1.1 par la borne active `1..35` décidée ici : l'ancienne borne ne crée donc aucune
exception pour une future collecte manuelle J3.

Les plafonds agrégés `38` et `58`, calculés à partir de cet A1 à 25 pages, ne peuvent plus être
utilisés pour un futur go D1/D2/D3. Cette ADR ne leur substitue ni `48` ni `68` et n'autorise aucune
nouvelle série : une décision et un Work Order distincts devront d'abord recomposer et accepter
l'enveloppe agrégée à partir de la borne J3 à 35.

## 3. Conséquences

- Une séquence de 27 pages dont la dernière porte `hasNextPage=false` est complète ; elle ne doit
  jamais être artificiellement prolongée jusqu'à 35.
- Une séquence qui annonce encore une page après la page 35 est fermée avec un diagnostic explicite
  et ne produit aucun départ pour la page 36.
- Les validations de domaine, de contrôleur, du worker isolé, de l'import local, des preuves et de
  l'audit J8 doivent partager la même limite active de 35.
- L'augmentation de la borne ne change pas les enveloppes de taille ni les règles d'espacement. Elle
  ne vaut pas autorisation de multiplier les exécutions manuelles.

## 4. Critères de réalisation

La réalisation est acceptable lorsque les conditions suivantes sont démontrées hors fournisseur :

- pages 1, 27 et 35 acceptées dans les parcours applicables ;
- page 36 refusée avant toute mutation ou départ réseau ;
- terminaison normale à la page 27 lorsque `hasNextPage=false` ;
- arrêt `PAGINATION_LIMIT_REACHED` après une page 35 parsée avec `hasNextPage=true`, sans page 36 ;
- écriture des nouvelles campagnes J8 à 35 et lecture maintenue des campagnes J8 historiques à 25 ;
- migration V53 appliquée sur PostgreSQL isolé, sans réécriture des données historiques ;
- suites standard et d'intégration exécutées selon le Work Order, sans appel fournisseur.

## 5. Hors périmètre

Cette décision ne modifie pas les règles live J4/J5, les plafonds de campagnes live, les parcours
J5, la découverte de tournoi après J3, les API fournisseur, les délais de réponse, les quotas ou
les politiques de sécurité du fournisseur. Elle n'autorise ni changement d'adresse, ni proxy,
