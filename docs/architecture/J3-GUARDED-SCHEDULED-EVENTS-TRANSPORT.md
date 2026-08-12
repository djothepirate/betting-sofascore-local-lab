# Transport J3 `SCHEDULED_EVENTS` protégé et simulé

## 1. Portée

Cette unité part du commit `f8f01225bd4efa0eead432c90899d6c9e1f293b9` et introduit le
premier chemin HTTP du laboratoire. Ce chemin sert exclusivement à tester la composition technique
du transport avec les politiques J3. Il ne contient aucune URI SofaScore et ne peut cibler qu’un
serveur simulé sur l’adresse IPv4 de boucle locale.

```text
TRANSPORT_SCOPE=SIMULATED_LOOPBACK_ONLY
ALLOWED_ORIGIN=http://127.0.0.1:<port>
ALLOWED_PATH=/simulated/scheduled-events
REAL_PROVIDER_URI=ABSENT
REAL_PROVIDER_CALL=BLOCKED
```

Le transport n’est pas un bean Spring. `DisabledSofascoreDataProvider` reste l’adaptateur fournisseur
actif et `ConnectorGate` continue de refuser systématiquement toute demande.

## 2. Chaîne d’exécution

```text
J3ScheduledEventsCallAuthorization
                │
                ▼
J3ManualCallPolicy
     │ USE_CACHE/BLOCKED ───────────────► aucun transport
     │ TRANSPORT_ELIGIBLE
     ▼
J3SingleCallGuard.tryAcquire()
     │ permis absent ───────────────────► BLOCKED
     │ permis unique
     ▼
ScheduledEventsTransport
     │
     ▼
RestClient ──► http://127.0.0.1:<port>/simulated/scheduled-events?date=AAAA-MM-JJ
```

L’orchestrateur `J3GuardedScheduledEventsTransport` réévalue toutes les préconditions puis acquiert
le permis atomique immédiatement avant l’entrée/sortie. L’heure du début de transport est conservée
en mémoire même si le transport échoue, afin que le délai minimal de trois secondes ne soit pas
contourné par un nouvel essai immédiat.

## 3. Construction de la requête

`ScheduledEventsTransportRequest` accepte seulement :

- le schéma `http` ;
- l’hôte littéral `127.0.0.1` ;
- un port explicite de `1` à `65535` ;
- aucune information utilisateur, aucun chemin fourni, aucune query et aucun fragment dans
  l’origine ;
- une date `LocalDate`, rendue dans la clé canonique
  `SCHEDULED_EVENTS|date=AAAA-MM-JJ`.

Le chemin et le nom du paramètre sont constants dans le code. L’appelant ne peut donc fournir ni
hôte, ni chemin, ni paramètre arbitraire. `localhost`, IPv6, une IP LAN, HTTPS et toute destination
publique sont rejetés avant le transport.

## 4. Bornes du client

Le client synchrone utilise le moteur HTTP du JDK avec :

- délai de connexion strictement positif et plafonné à dix secondes ;
- délai de lecture strictement positif et plafonné à dix secondes ;
- redirections désactivées ;
- proxy désactivé explicitement ;
- méthode unique `GET` ;
- en-tête sortant unique `Accept: application/json` ajouté par le transport ;
- aucun cookie, jeton, identifiant, header de session ou authentification ;
- aucun retry automatique ;
- lecture maximale de 5 Mio plus un octet de détection.

Une réponse dépassant 5 Mio est interrompue et classée `PAYLOAD_TOO_LARGE`. Une erreur d’entrée/
sortie est classée `IO_FAILURE`. Un motif sensible détecté avant exposition des octets est classé
`SENSITIVE_CONTENT_REJECTED`. Les exceptions ne reprennent ni payload, ni URI, ni valeur sensible.

## 5. Résultat brut

Le transport retourne :

- la clé de requête canonique ;
- les instants de début et de réception ;
- le statut HTTP, y compris hors `2xx` ;
- le type de contenu borné ;
- la latence ;
- un `RawPayloadEvidence` contenant une copie défensive, la taille et le SHA-256.

Le transport ne parse, ne normalise et ne persiste rien. Cette séparation garantit que le brut reste
disponible avant les étapes ultérieures. La composition transport–persistance–parseur et les
politiques détaillées d’incident sont couvertes par
`docs/architecture/J3-TRANSPORT-STOP-AND-INCIDENT-POLICIES.md`.

## 6. Simulation et tests

`MockRestServiceServer` intercepte le `RestClient` en mémoire. Les tests vérifient :

- l’URI exacte, la méthode `GET` et l’en-tête `Accept` ;
- la conservation octet pour octet d’une réponse JSON ;
- la remontée d’un statut `429` et de son brut sans second appel ;
- le rejet d’un payload de plus de 5 Mio ;
- le rejet d’un motif sensible sans exposition de sa valeur ;
- les délais invalides ;
- le refus de toutes les origines autres que l’origine loopback exacte ;
- l’absence totale d’appel lorsque le cache est utilisé ou qu’une précondition manque ;
- un seul appel lorsque toutes les gardes sont satisfaites ;
- le blocage immédiat d’un second appel par le délai minimal.

Le serveur simulé ne crée aucune connexion Internet et n’utilise aucune connaissance d’une URI
réelle. Le scanner `Verify-Local.ps1` continue d’interdire les URI SofaScore et limite la construction
d’un `RestClient` de production à cette classe de transport loopback.

## 7. Garde-fous toujours actifs

```text
SOFASCORE_ENABLED_DEFAULT=false
ALLOWED_ENDPOINTS_DEFAULT=[]
CATALOG_CALLABLE=false
CATALOG_URI_CONFIGURED=false
CONNECTOR_GATE=LOCKED_OFFLINE_J3_POLICY
ACTIVE_PROVIDER=DisabledSofascoreDataProvider
LIVE_MAVEN_PROFILE=BLOCKED
UI_NETWORK_ACTIONS=DISABLED
AUTOMATIC_REFRESH=false
LIVE_POLLING=false
```

La prochaine unité est `feat: add explicit manual-call confirmation`. Elle ne devra pas transformer
ce transport simulé en appel fournisseur sans satisfaire le point de décision humain distinct du
Work Order.
