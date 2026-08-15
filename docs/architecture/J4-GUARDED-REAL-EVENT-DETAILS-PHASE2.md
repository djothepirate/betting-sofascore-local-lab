# J4 — `EVENT_DETAILS` paramétrable et rafraîchissement manuel, sous-étape 2

## 1. Décision et compatibilité ADR

Le porteur du Work Order `WO-SS-20260815-004` a autorisé le 2026-08-15 un identifiant
`EVENT_DETAILS` paramétrable depuis l'interface locale, puis les rappels manuels du même endpoint
afin d'actualiser notamment un match en cours.

La revue de `ADR-SS-001` conclut `COMPATIBLE_NO_CHANGE_REQUIRED` : chaque transport reste manuel,
unitaire, synchrone, local au poste Windows et sans compte, cookie, jeton, proxy, redirection,
polling, tâche planifiée ou concurrence supérieure à un. Cette sous-étape ne déclenche donc aucun
des motifs de nouvel ADR. Elle ajoute des contraintes plus strictes que l'ADR sur l'arrêt au premier
incident et sur la confirmation liée à l'identifiant.

```text
ORIGIN=https://www.sofascore.com
LOGICAL_ENDPOINT=EVENT_DETAILS
PATH_TEMPLATE=/api/v1/event/{eventId}
PARAMETERIZED_EVENT_ID_RANGE=1..999999999
MAXIMUM_PROVIDER_CALLS_PER_CONFIRMED_CYCLE=1
MAXIMUM_CONCURRENCY=1
MINIMUM_DELAY_BETWEEN_PROVIDER_CALLS=3s
AUTOMATIC_RETRY=NO
POLLING=NO
SCHEDULING=NO
```

## 2. Sélection exclusive de la sous-étape

Les valeurs sûres restent désactivées par défaut. La sous-étape 2 exige un opt-in supplémentaire :

```text
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS
```

Quand `SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true`, la politique de sous-étape 1 ajoute le
bloqueur `J4_PHASE_2_MUST_BE_DISABLED`. Inversement, la politique de sous-étape 2 ajoute
`J4_EVENT_DETAILS_PHASE_2_DISABLED` tant que son opt-in dédié vaut `false`. Les deux formulaires ne
peuvent donc pas rendre leur transport disponible en même temps. `ConnectorGate`, le catalogue
`callable=false` et le profil Maven live restent inchangés et bloqués.

## 3. Identité saisie et confirmation liée

La préparation reçoit un entier décimal positif borné à neuf chiffres. L'ID est transformé en
`long` avant toute construction d'URI ; aucun segment, query, fragment ou hôte saisi par
l'opérateur n'est accepté. L'URI résolue reste exactement :

```text
https://www.sofascore.com/api/v1/event/<eventId>
```

La préparation ne réalise aucun transport. Elle crée une intention valable cinq minutes et une
phrase exacte contenant l'ID. L'action d'exécution ne reçoit pas de nouvel ID : elle reçoit
seulement l'UUID de l'intention, la phrase et l'acquittement. Le contrôle restitue alors un claim
immuable qui porte l'ID préparé. Une modification de l'ID entre préparation et exécution est donc
impossible depuis le formulaire.

```text
LOCKED
  │ préparation(eventId), sans réseau
  ▼
AWAITING_CONFIRMATION
  │ phrase exacte + acquittement, moins de 5 min
  ▼
EXECUTING
  ├─ un événement persisté et parsé ─► COMPLETED_LOCKED
  ├─ premier incident ───────────────► FAILED_LOCKED
  ├─ arrêt humain ───────────────────► STOPPED_LOCKED
  └─ expiration avant confirmation ──► EXPIRED_LOCKED
```

## 4. Rappels manuels et absence de cache

Après `COMPLETED_LOCKED` ou `EXPIRED_LOCKED`, l'opérateur peut préparer le même ID ou un autre ID.
Il n'existe pas de limite au nombre de cycles humains réussis successifs. En revanche, chaque cycle
exige une nouvelle préparation, une nouvelle phrase, un nouvel acquittement et ne peut produire
qu'un seul transport. `FAILED_LOCKED` et `STOPPED_LOCKED` interdisent toute nouvelle préparation
dans le processus courant et imposent revue puis redémarrage.

La sous-étape 2 ne consulte volontairement pas le cache `EVENT_DETAILS` : un rappel confirmé est
une demande explicite d'actualisation et doit acquérir un nouvel instantané fournisseur. Le service
impose néanmoins au moins trois secondes entre deux débuts de transport dans le même processus.
Cette attente interne n'est pas une boucle de polling et ne déclenche aucune requête supplémentaire.

Une réponse identique produit toujours un snapshot brut traçable selon les règles de déduplication
du stockage. La couche normalisée déduplique une observation métier strictement identique ; un
changement d'horaire, de statut, d'équipe ou de compétition ajoute une nouvelle observation à la
même identité canonique. Aucune observation antérieure n'est modifiée ou supprimée.

## 5. Ordre raw-before-parse

Pour chaque cycle confirmé :

1. revérifier que le contrôle est encore `EXECUTING` ;
2. respecter le délai minimal depuis le transport précédent ;
3. revérifier l'arrêt global après ce délai ;
4. exécuter un unique `GET` exact, sans credential ni donnée de session ;
5. calculer taille et SHA-256 puis persister le brut au statut `RAW_ONLY` ;
6. seulement après le retour de la persistance, contrôler HTTP et le type de contenu ;
7. parser avec `event-details-v2` et vérifier l'égalité de l'ID demandé et de l'ID reçu ;
8. persister l'observation canonique et le détail, puis classer le snapshot `PARSED` ;
9. afficher uniquement la preuve minimisée et reverrouiller le cycle.

Une erreur de parsing ou de normalisation ne peut donc pas annuler le snapshot brut déjà conservé.
Le payload n'est jamais placé dans les logs, les redirections Web ou les rapports de qualification.

## 6. Arrêts

Les codes `403`, `429`, `5xx`, timeout, contenu non JSON, dépassement de taille, contenu sensible,
schéma incompatible, ID incohérent ou erreur de persistance terminent immédiatement le cycle sans
retry. Un nouvel essai n'est possible qu'après décision humaine et nouvelle préparation ; il n'est
jamais automatique.

L'action **Arrêt global J4** verrouille les contrôles des sous-étapes 1 et 2. Après la campagne
humaine, la configuration locale doit revenir aux six valeurs bloquées du runbook avant tout autre
démarrage.

## 7. État de qualification

```text
PHASE_1_HUMAN_QUALIFICATION=PASS_AFTER_CORRECTIVE_LOCAL_RETEST
PHASE_2_AUTHORIZATION=YES
PHASE_2_IMPLEMENTATION=IMPLEMENTED_OFFLINE_TESTED
PHASE_2_REAL_PROVIDER_QUALIFICATION=NOT_RUN
PHASE_2_HUMAN_REFRESH_QUALIFICATION=PENDING
WORK_ORDER_STATUS=IN_DEVELOPMENT
J4_CAN_BE_CLOSED=NO
```
