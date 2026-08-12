# Politique réseau J3 hors ligne et modèle de circuit

## 1. Objet

Cette unité introduit le modèle de décision qui devra précéder un futur appel manuel J3. Le modèle
est entièrement hors ligne : il ne construit pas de client HTTP, ne résout aucune URI et ne déclenche
aucune entrée/sortie.

La sortie `TRANSPORT_ELIGIBLE` signifie uniquement que les préconditions modélisées sont réunies.
Elle ne constitue ni une autorisation métier, ni une autorisation contractuelle, ni un moyen de
contourner `ConnectorGate`. Dans cette unité, `ConnectorGate` refuse encore systématiquement toutes
les demandes et `DisabledSofascoreDataProvider` demeure le seul adaptateur fournisseur.

```text
POLICY_EXECUTION=OFFLINE_ONLY
REAL_ENDPOINT_URI=ABSENT
HTTP_CLIENT=ABSENT
PROVIDER_TRANSPORT=DISABLED
CONNECTOR_MODE=LOCKED_OFFLINE_J3_POLICY
```

## 2. Composants

| Composant | Responsabilité | Effet externe |
|---|---|---|
| `J3ManualCallPolicy` | évaluer les préconditions dans un ordre déterministe | aucun |
| `J3ManualCallPolicyInput` | porter un instantané immuable des préconditions | aucun |
| `J3ManualCallPolicyResult` | produire `USE_CACHE`, `BLOCKED` ou `TRANSPORT_ELIGIBLE` | aucun |
| `J3NetworkCircuit` | appliquer les transitions synchronisées du circuit en mémoire | aucun |
| `J3CircuitSnapshot` | exposer l’état, la raison, l’heure et la borne `Retry-After` | aucun |
| `J3CircuitIncident` | représenter un incident typé sans message libre sensible | aucun |
| `J3SingleCallGuard` | accorder atomiquement au plus un permis en mémoire | aucun |

Les composants ne sont pas enregistrés comme beans Spring dans cette unité. Une future orchestration
pourra les composer avec le cache, la persistance et le transport simulé, sans modifier leurs règles
fondamentales.

## 3. Ordre d’évaluation

La politique applique l’ordre suivant :

1. utiliser une entrée de cache disponible ;
2. vérifier que la fonctionnalité est activée ;
3. vérifier que l’arrêt global n’est pas actif ;
4. vérifier que la famille logique est autorisée ;
5. vérifier qu’une configuration de transport existe ;
6. vérifier l’activation explicite de l’opérateur ;
7. vérifier la confirmation propre à l’appel ;
8. exiger un circuit `CLOSED` ;
9. refuser si un appel est déjà en cours ;
10. vérifier le délai minimal depuis le dernier début de transport ;
11. retourner `TRANSPORT_ELIGIBLE`.

Une entrée de cache est traitée en premier parce qu’elle évite le transport. `USE_CACHE` ne permet
aucune connexion, même lorsque les autres préconditions réseau sont absentes.

La durée minimale acceptée par le modèle est de trois secondes. Si elle n’est pas écoulée, le
résultat contient `nextEligibleAt` mais ne dort pas, ne planifie rien et ne réessaie rien.

## 4. Décisions et motifs

| Décision | Signification |
|---|---|
| `USE_CACHE` | une donnée locale existante doit être utilisée, sans transport |
| `BLOCKED` | une précondition manque ; le motif est explicite |
| `TRANSPORT_ELIGIBLE` | toutes les préconditions hors ligne sont réunies, sans déclenchement d’appel |

Les motifs bloquants couvrent : fonctionnalité désactivée, arrêt global, endpoint non autorisé ou
non configuré, activation ou confirmation absente, circuit verrouillé ou ouvert, appel déjà en cours
et délai minimal non écoulé.

## 5. Circuit

Le modèle utilise volontairement trois états :

```text
                    activation explicite
        ┌─────────────────────────────────────┐
        │                                     ▼
     LOCKED ───────────────────────────────> CLOSED
        ▲                                     │
        │                                     │ incident
        │ arrêt explicite                     ▼
        └─────────────────────────────────── OPEN
```

- `LOCKED` est l’état initial sûr ;
- `CLOSED` signifie seulement que le circuit peut être évalué par la politique ;
- `OPEN` interdit toute évaluation favorable après un incident ;
- un circuit `OPEN` ne repasse jamais directement à `CLOSED` ;
- l’opérateur doit d’abord arrêter et revoir l’incident, ce qui ramène à `LOCKED`, puis réactiver
  explicitement ;
- aucun état `HALF_OPEN`, temporisateur ou essai automatique n’est implémenté.

Les transitions sont synchronisées et doivent être chronologiques.

## 6. Incidents

Les raisons typées sont :

- `HTTP_BAD_REQUEST` (`400`) ;
- `HTTP_UNAUTHORIZED` (`401`) ;
- `HTTP_FORBIDDEN` (`403`) ;
- `HTTP_TOO_MANY_REQUESTS` (`429`) ;
- `TIMEOUT` ;
- `UNEXPECTED_CONTENT` ;
- `SCHEMA_INCOMPATIBLE` ;
- `SERVER_ERROR`.

Tous ces incidents ouvrent le circuit. Aucun n’entraîne un retry dans cette unité. Pour `429`, la
borne `retryNotBefore` est obligatoire et doit être postérieure à l’incident ; elle conserve la
contrainte du serveur mais ne provoque aucune réouverture automatique. Les autres incidents ne
peuvent pas porter de borne de retry.

Le modèle ne contient aucun champ de message libre, header ou payload. Il ne peut donc pas servir à
propager un cookie, un jeton, une URI ou une valeur sensible dans les logs.

## 7. Concurrence

`J3SingleCallGuard` utilise une acquisition atomique et accorde au plus un permis. Un second essai
échoue tant que le premier permis n’est pas fermé. La fermeture est idempotente afin de permettre un
usage sûr avec `try`-with-resources dans le futur orchestrateur.

La politique vérifie également l’indicateur `callInProgress`. Cette double représentation prépare
une défense en profondeur : décision lisible avant transport, puis acquisition atomique au dernier
moment. Aucun transport n’utilise encore ce permis.

## 8. Garde-fous conservés

- `sofascore.enabled=false` par défaut ;
- `allowed-endpoints=[]` par défaut ;
- catalogue `callable=false` et sans URI ;
- `ConnectorGate` systématiquement bloquant ;
- adaptateur fournisseur désactivé ;
- profil Maven `sofascore-live-test` bloqué ;
- boutons réseau désactivés ;
- rafraîchissement automatique et polling live interdits ;
- liaison de l’application à `127.0.0.1` ;
- aucune migration ou persistance d’incident dans cette unité.

## 9. Tests hors ligne

Les tests couvrent :

- priorité du cache ;
- activation, arrêt global, endpoint autorisé/configuré et confirmation ;
- circuits verrouillé et ouvert ;
- exclusion d’un second appel ;
- limite minimale de trois secondes et calcul de `nextEligibleAt` ;
- état initial verrouillé et activation explicite ;
- ouverture pour `400`, `401`, `403`, timeout, HTML inattendu, schéma incompatible et `5xx` ;
- conservation de la borne de retry pour `429` ;
- absence de réactivation directe après incident ;
- acquisition atomique unique sous tentatives concurrentes.

Ces tests ne construisent ni serveur simulé ni client. Le transport simulé appartient à une unité J3
ultérieure.

## 10. Évolution après cette unité

L’unité ultérieure `feat: add guarded scheduled-events transport` compose désormais cette politique
avec un transport HTTP limité à `127.0.0.1` et testé par serveur simulé. Elle ne modifie pas les
règles décrites ici : `TRANSPORT_ELIGIBLE` ne peut produire une entrée/sortie qu’après l’acquisition
de la garde atomique, et aucun transport fournisseur n’est activé. Le détail figure dans
`docs/architecture/J3-GUARDED-SCHEDULED-EVENTS-TRANSPORT.md`.
