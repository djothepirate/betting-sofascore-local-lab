# Incident C5 — transport du modèle avant `JM-H01`

**État : `BLOCKED_BEFORE_MODEL_COMMAND`.** Cette pièce consigne une seule tentative
`JM-H01` de Java C5. Elle n'est ni un verdict comportemental du candidat
`ss-java-module 0.1.0-candidate.4`, ni une relance autorisée.

## Faits figés

| Élément | Observation |
| --- | --- |
| Cas / candidat | `JM-H01` / `0.1.0-candidate.4`, SHA-256 `f1f6533f678c5cdb4ae6a5bc9c996c062af419005e4f4cd6104a51c91e442d97` |
| Enveloppe | `codex exec --ephemeral --sandbox read-only`, contexte C5 gelé, sans oracle ni réponse antérieure |
| Tour | `thread.started` puis `turn.started`, sans `turn.completed` |
| Commandes de la session | `0` ; aucune lecture du candidat ou d'une source n'est attestée |
| Sortie finale / usage | absents / `null` |
| Durée | `900.266 s`, expiration du watchdog externe de 900 s |
| Incident | le WebSocket vers le service configuré est refusé avec `os error 10013`; le repli HTTPS échoue ensuite lui aussi |
| Nettoyage de l'exécutable collecté | PID détenu `7812` absent après l'expiration |

Les événements et le journal montrent que l'échec se produit avant toute commande du
modèle. Ils ne permettent pas de conclure que le candidat a été chargé, lu ou appliqué,
ni que la demande a été traitée côté service. Les fichiers de cas, le candidat et les
oracles C5 restent inchangés.

## Portée de la décision de suspension

La recette C5 limite le lot à dix sessions et interdit la relance automatique. Cette
tentative ayant créé un thread et un tour, elle est conservée de manière prudente comme
une session C5 commencée, mais **pas** comme une exécution comportementale : le compteur
de qualification du candidat reste à zéro. Les sept cas Java non lancés (`JM-N01`,
`JM-C01`, `JM-C02`, `JM-S01` à `JM-S04`) sont retenus ; les deux cas Windows restent
non lancés pour leur préflight Windows distinctement bloqué.

Une nouvelle tentative exacte de `JM-H01`, ou l'augmentation du plafond de sessions,
demande une nouvelle décision du propriétaire après rétablissement démontré du transport
du modèle. Aucun changement de sandbox, de modèle, de sources, de candidat ou de
configuration n'est déduit de cet incident.

## Pièces conservées

- `trace.json` : arguments, hashes d'entrée, expiration et diagnostic de collecte ;
- `native-events.json` et `native-timing.jsonl` : les erreurs de transport datées ;
- `stderr.log` : erreur WebSocket, repli HTTPS et reprises ;
- `native-timing-summary.json`, `host-runtime.json` et `freeze.json` : durée,
  environnement local et intégrité du gel.

Le manifeste local `artifact-manifest.json` attache une empreinte à chacune de ces
pièces. Les preuves sont une observation d'infrastructure ; elles ne valent ni build,
ni test applicatif, ni qualification humaine, ni installation personnelle.
