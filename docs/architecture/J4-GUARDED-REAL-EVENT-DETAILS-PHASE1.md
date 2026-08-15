# J4 — Transport réel `EVENT_DETAILS` gardé, sous-étape 1

## 1. Décision bornée

Le Work Order `WO-SS-20260815-004` autorise une seule famille, une seule origine et deux
identifiants fournisseur :

```text
ORIGIN=https://www.sofascore.com
LOGICAL_ENDPOINT=EVENT_DETAILS
PATH_TEMPLATE=/api/v1/event/{eventId}
AUTHORIZED_EVENT_IDS=16386245,16421052
MAXIMUM_PROVIDER_CALL_ATTEMPTS=2
MAXIMUM_CONCURRENCY=1
MINIMUM_DELAY=3s
AUTOMATIC_RETRY=NO
```

`EventDetailsProviderRequest` porte cette allowlist dans le code. Un autre ID, une autre origine,
un port explicite, un chemin préalable, une query ou un fragment sont rejetés avant la résolution
de l’URI. Il n’existe aucun champ graphique capable de transmettre un ID arbitraire.

## 2. Séparation des verrous

Le chemin ne modifie ni `ConnectorGate`, ni le catalogue général :

```text
configuration sûre par défaut
  enabled=false
  j4-event-details-qualification-enabled=false
  allowed-endpoints=[]
        │
        ▼ opt-in temporaire exact, J3 désactivé
J4EventDetailsQualificationPolicy
        │
        ▼ préparation locale, sans transport
J4RealPhase1ControlService : AWAITING_CONFIRMATION
        │
        ▼ phrase exacte + acquittement, validité 5 min
EXECUTING
        │
        ├─ succès des deux événements ──► COMPLETED_LOCKED
        ├─ incident quelconque ─────────► FAILED_LOCKED
        ├─ arrêt humain ────────────────► STOPPED_LOCKED
        └─ expiration ──────────────────► EXPIRED_LOCKED
```

Une nouvelle campagne exige toujours une nouvelle préparation et une nouvelle phrase. Les opt-ins
J3 et J4 sont mutuellement exclusifs par validation de configuration.

## 3. Ordre de traitement d’un événement

Pour chaque ID fixe, l’orchestrateur suit cet ordre :

1. vérifier que le circuit est encore `EXECUTING` ;
2. consulter le cache `EVENT_DETAILS` sur la clé exacte `EVENT_DETAILS|eventId=<id>` et le parseur
   `event-details-v2` ;
3. si le cache est absent, appliquer le délai interne avant une deuxième tentative fournisseur ;
4. exécuter le `GET` exact, sans proxy, redirection, cookie, jeton, compte ou donnée de session ;
5. calculer taille et SHA-256, puis **insérer et valider transactionnellement le brut** au statut
   `RAW_ONLY` ;
6. seulement après le retour de cette persistance, contrôler HTTP et type de contenu ;
7. parser l’enveloppe `event` en mémoire avec `event-details-v2` ;
8. vérifier l’égalité entre l’ID demandé et l’ID parsé ;
9. dans une transaction distincte, enregistrer l’observation canonique et le détail, classer le
   snapshot `PARSED`, puis pointer le cache vers ce snapshot ;
10. passer à l’ID suivant uniquement si toutes les étapes ont réussi.

La séparation des transactions garantit qu’une erreur de parseur ou de normalisation ne supprime
pas le snapshot brut préalablement acquis. Les classifications finales sont idempotentes : une
ligne finale ne peut pas être reclassée vers un autre résultat.

## 4. Matrice d’arrêt sans retry

| Observation | Classification du brut | Code terminal | Suite |
|---|---|---|---|
| HTTP `403` | `TRANSPORT_ERROR` | `HTTP_403` | arrêt |
| HTTP `429` | `TRANSPORT_ERROR` | `HTTP_429` | arrêt |
| HTTP `5xx` | `TRANSPORT_ERROR` | `HTTP_5XX` | arrêt |
| HTTP `408` | `TRANSPORT_ERROR` | `HTTP_TIMEOUT` | arrêt |
| autre non-`2xx` | `TRANSPORT_ERROR` | `HTTP_STATUS_<n>` | arrêt |
| timeout client | aucun payload disponible | `TRANSPORT_TIMEOUT` | arrêt |
| taille excessive ou contenu sensible | aucun payload accepté | code transport sûr | arrêt |
| média non JSON | `UNEXPECTED_CONTENT` | `UNEXPECTED_CONTENT` | arrêt |
| JSON invalide | `UNEXPECTED_CONTENT` | `UNEXPECTED_CONTENT` | arrêt |
| schéma incompatible | `SCHEMA_INCOMPATIBLE` | `SCHEMA_INCOMPATIBLE` | arrêt |
| ID incohérent | `SCHEMA_INCOMPATIBLE` | `EVENT_ID_MISMATCH` | arrêt |

Aucun de ces états ne contient de boucle de retry, de réouverture automatique ou de reprise sur le
second événement.

## 5. Preuve et affichage

La page `/events` affiche uniquement :

- l’ID fournisseur autorisé et l’UUID canonique local ;
- les équipes, l’horaire, le statut et les champs de détail normalisés ;
- la source `CACHE` ou `PROVIDER` ;
- l’identifiant du snapshot, sa taille et son SHA-256 ;
- le code terminal de campagne.

Le payload brut n’est ni journalisé, ni inclus dans la preuve, ni envoyé hors du poste. Il demeure
consultable uniquement par le mécanisme local de snapshot déjà protégé, qui n’est pas nécessaire à
la qualification humaine des champs normalisés.

## 6. Frontière de la sous-étape 2

La sous-étape 2 devra accepter un événement communiqué par le porteur comme paramètre graphique.
Elle n’est pas incluse dans ce code :

```text
PHASE_2_GUI_PARAMETER=NOT_IMPLEMENTED
PHASE_2_EVENT_ID=NOT_AUTHORIZED
PHASE_2_PROVIDER_CALL=BLOCKED
```

Elle ne pourra être ouverte qu’après validation humaine des deux événements de la sous-étape 1 et
amendement explicite de l’allowlist.
