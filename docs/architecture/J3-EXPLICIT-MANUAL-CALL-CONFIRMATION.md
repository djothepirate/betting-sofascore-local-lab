# Confirmation explicite d’appel manuel J3

## 1. Portée

Cette unité part du commit `c33192969f780dfa6d5d98590c4fee79907ccaa5` et rend visibles dans
l’interface locale les contrôles opérateur prévus par le Work Order J3. Elle permet de confirmer
une **intention** `SCHEDULED_EVENTS`, pas d’exécuter un appel.

```text
CONFIRMATION_SCOPE=LOCAL_INTENT_ONLY
PROVIDER_TRANSPORT_AVAILABLE=NO
REAL_ENDPOINT_URI=ABSENT
REAL_CALL_AUTHORIZED=NO
NETWORK_CALLS_EXECUTED=NO
```

`DisabledSofascoreDataProvider`, `ConnectorGate`, le catalogue non appelable et le profil Maven
bloquant restent inchangés. Le transport simulé de l’unité précédente n’est pas injecté dans cette
chaîne et aucun composant de confirmation ne possède une référence vers lui.

## 2. Séquence opérateur

Le contrôle démarre toujours dans l’état le plus sûr :

```text
démarrage / redémarrage
        │
        ▼
arrêt global ACTIF + circuit LOCKED
        │ action POST explicite
        ▼
arrêt global LEVÉ + circuit LOCKED
        │ action POST distincte
        ▼
circuit CLOSED par l’opérateur
        │ date unique préparée
        ▼
intention AWAITING_CONFIRMATION (durée maximale : 5 minutes)
        │ phrase exacte + case d’acquittement
        ▼
intention CONFIRMED_BLOCKED
        │
        └──────────────► aucun transport, action fournisseur désactivée
```

La préparation produit une clé canonique `SCHEDULED_EVENTS|date=AAAA-MM-JJ`, un identifiant UUID
et une phrase à usage unique de la forme :

```text
CONFIRMER SCHEDULED_EVENTS AAAA-MM-JJ NNNNNN
```

La comparaison est exacte et effectuée en temps constant. Les espaces ajoutés, une casse différente,
une case non cochée, un autre UUID ou une phrase de plus de 128 caractères entraînent un refus.

## 3. Expiration, arrêt et redémarrage

- une intention non confirmée expire après cinq minutes ;
- la phrase est supprimée dès la confirmation, l’expiration ou l’arrêt global ;
- une intention confirmée reste `CONFIRMED_BLOCKED` et ne peut pas être confirmée une seconde fois ;
- l’arrêt global replace le circuit à `LOCKED` et classe l’intention active
  `CANCELLED_BY_GLOBAL_STOP` ;
- aucun état n’est restauré après un redémarrage : arrêt global actif et circuit verrouillé sont
  réappliqués en mémoire.

La non-persistance est volontaire pour cette unité : un redémarrage ne doit jamais réarmer une
autorisation opérateur précédente.

## 4. Protection des commandes Web

Toutes les transitions sont des requêtes `POST` vers des routes locales fixes :

| Route | Effet maximal |
|---|---|
| `/manual-call/rearm` | lève l’arrêt global, sans activer le circuit |
| `/manual-call/activate` | ferme le circuit après réarmement |
| `/manual-call/prepare` | crée une intention datée et bornée |
| `/manual-call/confirm` | classe l’intention `CONFIRMED_BLOCKED` |
| `/manual-call/stop` | verrouille le circuit et annule l’intention active |

Chaque formulaire exige un jeton aléatoire de 256 bits lié à la session locale et consommé une
seule fois. Un jeton absent, modifié, réutilisé ou issu d’une autre session est refusé en `400`. Le
cookie de session est `HttpOnly` et `SameSite=Strict`. La politique CSP existante maintient
`form-action 'self'` et l’application reste liée exclusivement à `127.0.0.1`.

Les messages de refus exposés à l’opérateur sont bornés et ne reprennent ni phrase secrète, ni
contenu saisi, ni exception interne.

## 5. Verrous fournisseur invariants

Le modèle de contrôle refuse sa propre construction si le transport est marqué disponible ou si la
liste de verrous est vide. L’interface rend explicitement les cinq barrières suivantes :

```text
REAL_ENDPOINT_URI_ABSENT
REAL_CALL_NOT_AUTHORIZED
CONNECTOR_GATE_LOCKED
CATALOG_NOT_CALLABLE
LIVE_PROFILE_BLOCKED
```

Le bouton « Lancer l’appel fournisseur » est toujours désactivé, y compris après une confirmation
réussie. Cette unité ne modifie ni les propriétés `sofascore.enabled=false`, ni le catalogue, ni le
profil réel, ni les classes de transport.

## 6. Incidents

L’interface affiche l’état, le motif et la date de changement du circuit ainsi que la présence d’un
incident. Le service de confirmation n’ouvre lui-même aucun incident fournisseur : il ne réalise
aucune entrée/sortie. Cette exposition prépare l’unité de tests des politiques d’arrêt et d’incident,
sans anticiper leur branchement à un transport réel.

## 7. Preuves automatisées

Les tests hors ligne vérifient :

- le démarrage verrouillé et les cinq barrières fournisseur ;
- l’ordre obligatoire réarmement, activation, préparation et confirmation ;
- la phrase exacte, l’acquittement, l’UUID et l’expiration ;
- l’arrêt global et la suppression de la phrase ;
- l’unicité et la liaison de session du jeton de formulaire ;
- le rejet HTTP d’un jeton rejoué ;
- le rendu du tableau de bord avec l’action fournisseur désactivée.

Aucun test de cette unité ne construit un client HTTP ou une URI fournisseur.

Validation consolidée du 2026-08-12 : `93` tests réussis, scanner de garde-fous réussi et zéro
appel réseau SofaScore exécuté. Le profil d’intégration PostgreSQL n’est pas requis par cette unité,
qui ne modifie ni schéma ni code de persistance.
