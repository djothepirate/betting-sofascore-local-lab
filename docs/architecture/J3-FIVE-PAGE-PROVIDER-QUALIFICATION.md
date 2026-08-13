# J3 — chemin fournisseur manuel borné à cinq pages

## 1. Portée de la décision

Cette unité rend possible, depuis l’interface locale, une qualification fournisseur unique pour la
date exacte `2026-08-13` et les pages `1`, `2`, `3`, `4`, `5` de la famille logique
`SCHEDULED_EVENTS`. Elle n’exécute aucun appel pendant le développement et ne transforme pas le
connecteur général en adaptateur appelable.

L’action opérateur unique peut donc produire jusqu’à cinq requêtes HTTP. Elles sont strictement
séquentielles, espacées d’au moins trois secondes entre leurs instants de départ et interrompues au
premier incident. Aucune page n’est découverte dans une réponse et aucun retry n’est programmé.

## 2. Activation explicite

Le chemin est bloqué avec la configuration versionnée. Il n’est disponible que si les quatre
propriétés locales suivantes concordent :

```text
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=SCHEDULED_EVENTS
```

La propriété `SOFASCORE_BASE_URL` doit représenter exactement l’origine indiquée : protocole
`https`, hôte `www.sofascore.com`, aucun port explicite, chemin, paramètre, fragment ou information
utilisateur. Une famille supplémentaire dans `SOFASCORE_ALLOWED_ENDPOINTS` bloque également le
chemin.

La configuration impose en plus : concurrence `1`, stockage brut actif, rafraîchissement
automatique et live désactivés, délai minimal d’au moins trois secondes, délais de connexion et de
lecture positifs et plafonnés à dix secondes.

## 3. Construction fermée des URI

`ScheduledEventsProviderPageRequest` n’accepte que :

```text
date = 2026-08-13
page ∈ {1, 2, 3, 4, 5}
origin = https://www.sofascore.com
```

Le chemin HTTP est construit dans l’adaptateur à partir de constantes contrôlées :

```text
/api/v1/sport/football/scheduled-tournaments/2026-08-13/page/{page}
```

Le domaine ne reçoit aucun chemin libre. Le client HTTP désactive les redirections et les proxies.
Il n’ajoute aucun cookie, jeton, compte, donnée de session, en-tête d’autorisation, référent de
navigateur ou mécanisme de simulation de navigateur.

## 4. Séquence opérateur

```text
STARTUP_LOCK + arrêt global actif
        |
        v
lever l’arrêt global
        |
        v
activer explicitement le circuit
        |
        v
préparer la date exacte et les pages 1-5
        |
        v
recopier la phrase + acquitter
        |
        v
CONFIRMED_READY
        |
        v
POST /manual-call/execute (action distincte)
        |
        v
page 1 -> délai -> page 2 -> délai -> ... -> page 5
```

La confirmation est valable cinq minutes avant usage. Elle est supprimée après confirmation et ne
peut être exécutée qu’une fois dans le processus applicatif. Un redémarrage réactive l’arrêt global ;
il ne doit jamais être utilisé pour contourner un état terminal ou répéter la qualification sans une
nouvelle décision du propriétaire.

## 5. Conservation et politiques d’incident

Chaque réponse reçue est conservée dans `provider_snapshot` avant parsing, avec une clé distincte :

```text
SCHEDULED_EVENTS|date=2026-08-13|page=1
...
SCHEDULED_EVENTS|date=2026-08-13|page=5
```

Les octets, la taille, le SHA-256, les horaires, le statut HTTP, le type de contenu, la latence et la
version `scheduled-events-v1` restent locaux. Une réponse `2xx` est ensuite classée par le parseur.
Un statut d’arrêt, un timeout, une erreur d’entrée/sortie, un contenu sensible, une taille excessive,
du HTML inattendu ou un schéma incompatible ouvre le circuit et empêche toute page suivante.

Le comportement terminal est :

- `COMPLETED` après cinq pages conservées et classées sans incident ;
- `FAILED` avec page et code sûr au premier incident ;
- `CANCELLED_BY_GLOBAL_STOP` si l’opérateur applique l’arrêt global ;
- aucun retry, aucune reprise automatique et aucune bascule vers un autre endpoint.

## 6. Tests hors ligne

Les tests standards n’activent pas la configuration réelle. Ils couvrent :

- les URI exactes, la date et les bornes de page ;
- les quatre conditions d’activation ;
- l’absence d’en-têtes d’authentification ou de session ;
- l’ordre des cinq pages et l’intervalle minimal ;
- la conservation page par page ;
- l’usage unique de la confirmation ;
- l’arrêt sur `403` avant la page suivante ;
- la route MVC distincte du geste de confirmation.

Le client HTTP réel est testé avec `MockRestServiceServer`. Aucune suite Maven standard ou
d’intégration ne contacte SofaScore.

```text
J3_PROVIDER_PATH_DEFAULT=DISABLED
J3_PROVIDER_ORIGIN=EXACT_HTTPS_WWW_SOFASCORE_COM
J3_QUALIFICATION_DATE=2026-08-13
J3_QUALIFICATION_PAGES=1,2,3,4,5
J3_CONCURRENCY=1
J3_MINIMUM_INTER_PAGE_DELAY=PT3S
J3_REDIRECTS=DISABLED
J3_PROXY=DISABLED
J3_SESSION_DATA=NONE
J3_RETRY=NONE
J3_IMPLEMENTATION_NETWORK_CALLS=0
```
