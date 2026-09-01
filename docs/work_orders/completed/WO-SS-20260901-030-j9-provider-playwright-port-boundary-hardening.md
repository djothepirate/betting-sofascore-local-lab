# WO-SS-20260901-030 — Durcissement de la borne de port de qualification Playwright fournisseur

- **Statut :** `VALIDATED`
- **Jalon :** après J9 — correctif fournisseur distinct de WO-029
- **Ouvert le :** 2026-09-01
- **Ouverture UTC :** `2026-09-01T13:17:27.6325976Z`
- **Ouverture Europe/Paris :** `2026-09-01T15:17:27.6325976+02:00`
- **Branche :** `codex/j9-wo030-provider-playwright-port-boundary`
- **Worktree :** `.tmp/w30`
- **Base exacte :** `daf55bf76521f81893f86d04fde3c2903bf22362`
- **Commit d'ouverture :** `1c7c1358527c0461824a0de0e76b03e54c38c435`
- **Commit d'implémentation qualifié :** `154349a2fbebe3fd0a43a63c7105f690ff04976b`
- **Rapport autonome :**
  `docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md`
- **SHA-256 du rapport :**
  `9bd030f4cc92f32bdeee977089d3f4c5c709278fec4c24cc66ed8fb6d258be5f`
- **Validation propriétaire enregistrée UTC :** `2026-09-01T14:00:53Z`
- **Validation propriétaire enregistrée Europe/Paris :** `2026-09-01T16:00:53+02:00`
- **Type de lot :** correctif fail-closed fournisseur, tests offline et documentation ; aucun
  appel fournisseur réel

## 1. Autorisation et séparation

Le propriétaire demande un nouveau Work Order fournisseur distinct pour corriger
`ProviderPlaywrightProperties.java`, après la fusion des PR #20 et #21. WO-030 part donc du
`origin/main` vérifié au commit de fusion de la PR #21 et ne modifie ni WO-029, ni son rapport, ni
les preuves qualifiées antérieures.

```text
WORK_ORDER=WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening
WORK_ORDER_STATUS=VALIDATED
BASE_COMMIT=daf55bf76521f81893f86d04fde3c2903bf22362
PARENT_WORK_ORDER=NONE_DISTINCT_PROVIDER_CORRECTIVE_SCOPE
TARGET_PRIMARY_FILE=src/main/java/com/bettingproject/sofascorelocal/config/ProviderPlaywrightProperties.java
PROVIDER_PLAYWRIGHT_PORT_RANGE=[1,65535]
PROVIDER_PLAYWRIGHT_PARENT_GUARD_HARDENING=AUTHORIZED
PROVIDER_PLAYWRIGHT_WORKER_DEFENCE_IN_DEPTH=IN_SCOPE
LOOPBACK_AND_OFFLINE_QUALIFICATION_AUTHORIZED=YES
PROVIDER_NETWORK_AUTHORIZED=NO
NEW_PROVIDER_GO_GRANTED=NO
ENDPOINT_TRANSPORT_PROTOCOL_SCHEMA_MIGRATION_SCOPE_EXPANSION_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## 2. Anomalie factuelle

Sur la base de départ, `ProviderPlaywrightProperties.isSafeConfiguration()` exigeait un port
explicite positif pour l'origine de qualification loopback, mais n'en vérifiait pas la borne
supérieure. Une valeur telle que `http://127.0.0.1:65536` était donc acceptée par Bean Validation
alors qu'elle se trouve hors de l'intervalle TCP valide.

La même validation incomplète existait dans
`ProviderPlaywrightWorkerConfiguration.parseOrigin()`, frontière de défense en profondeur du
worker enfant. Le parent transmet l'origine de qualification au worker par environnement ; laisser
la seconde garde inchangée permettrait au worker d'accepter directement la même valeur hors plage
si la première barrière était contournée.

Les contrôles voisins étaient déjà corrects et leur contrat reste inchangé :

- le port IPC du worker reste borné à `[1, 65535]` ; son littéral est seulement remplacé par la
  constante commune du worker ;
- `ScheduledEventsTransportRequest` borne déjà son origine simulée à `[1, 65535]` et son
  implémentation n'est pas modifiée ;
- WO-029 a corrigé le chemin distinct du push local J7 et demeure gelé.

Empreintes SHA-256 de la base :

```text
PROVIDER_PLAYWRIGHT_PROPERTIES_SHA256=2e199db5d50b49910a0c2c17b646ef93066d21aad2f27b3240df07314dea70d9
PROVIDER_PLAYWRIGHT_WORKER_CONFIGURATION_SHA256=b8d84671e0e3e7f0f01abdfbc517b0ad97d9cfbd6ff91cb286c7c14bc36b43ec
```

## 3. Objectif et périmètre

Le lot doit :

1. imposer un port explicite compris entre `1` et `65535` inclus dans
   `ProviderPlaywrightProperties` ;
2. appliquer le même intervalle dans la configuration du worker enfant avant toute création ou
   résolution de navigation ;
3. refuser localement et de manière déterministe le port absent, `0` et `65536` ;
4. accepter structurellement `1` et `65535` sans tenter de connexion vers ces ports ;
5. conserver l'origine fournisseur fixe, les six familles allowlistées et le protocole Playwright
   existants ;
6. produire une preuve autonome offline, sans Chromium lorsque la simple validation de
   configuration suffit.

Sont hors périmètre : nouvel endpoint, modification d'ADR-SS-001, transport alternatif, protocole
IPC, délai inter-appels, retry, fallback, polling, scheduler, migration SQL, persistance, contrat
receiver J7, permission officielle, réseau fournisseur, déploiement VPS et production.

## 4. Invariants obligatoires

| Invariant | Exigence WO-030 |
|---|---|
| Port loopback | origine explicite dans l'intervalle fermé `[1, 65535]` uniquement |
| Première barrière | Bean Validation refuse hors plage avant disponibilité d'une qualification |
| Défense en profondeur | le worker refuse hors plage avant navigation ou démarrage Chromium |
| Origine fournisseur | `https://www.sofascore.com` inchangée |
| Allowlist | six familles J3/J4/J5 existantes uniquement |
| Réseau de test | aucun appel fournisseur ; validation offline pure |
| Runtime | aucun démarrage automatique de Playwright |
| Artefacts | aucun profil, cookie, `storageState`, HAR, trace, vidéo, capture ou téléchargement |
| Gouvernance | `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY` |

## 5. Cas de qualification obligatoires

| Cas | Propriétés parentes | Worker enfant | Connexion |
|---|---|---|---|
| port absent (`-1`) | refus | refus | aucune |
| port `0` | refus | refus | aucune |
| port `1` | accepté structurellement | accepté structurellement | aucune |
| port `65535` | accepté structurellement | accepté structurellement | aucune |
| port `65536` | refus | refus | aucune |
| schéma/hôte/path décoré | gardes existantes préservées | gardes existantes préservées | aucune |
| qualification désactivée avec override | refus existant préservé | refus existant préservé | aucune |

## 6. Vérifications et définition de fini

1. tests ciblés de `ProviderPlaywrightPropertiesTest` pour les cinq cas de port ;
2. tests ciblés du garde de qualification parent pour prouver l'indisponibilité fail-closed ;
3. tests du runtime Playwright pour la configuration du worker, sans lancer Chromium ni naviguer ;
4. `mvnw.cmd --offline clean verify` ;
5. compilation et tests du profil `provider-playwright-runtime` ;
6. `git diff --check`, scan des secrets et recherche des artefacts Playwright interdits ;
7. contrôle de `server.address=127.0.0.1` et des flags réseau bloqués par défaut ;
8. zéro listener, worker ou navigateur résiduel ;
9. mise à jour du changelog, du README, de l'architecture Playwright et publication d'un rapport
   autonome ;
10. déplacement vers `docs/work_orders/completed` uniquement après qualification verte et
    décision propriétaire explicite.

Les tests d'intégration PostgreSQL ne sont pas requis : ce lot ne modifie ni migration, ni schéma,
ni persistance. Ils pourront être rejoués uniquement comme non-régression supplémentaire, sans en
faire une condition artificielle du correctif.

## 7. Registre d'exécution

| Date/heure UTC | Action | Résultat |
|---|---|---|
| `2026-09-01T13:17:27.6325976Z` | `origin/main` actualisé et base exacte vérifiée | `PASS` |
| `2026-09-01T13:17:27.6325976Z` | numéro WO-030, branche et worktree dédiés vérifiés | `PASS` |
| `2026-09-01T13:17:27.6325976Z` | ouverture documentaire commitée sous `1c7c1358527c0461824a0de0e76b03e54c38c435` | `PASS` |
| `2026-09-01T13:37:28Z` | correctif parent et worker commité sous `154349a2fbebe3fd0a43a63c7105f690ff04976b` | `PASS` |
| `2026-09-01T13:41:43Z` | `mvnw.cmd --offline clean verify` | `PASS` — Surefire `1043/0/0/5`, Failsafe `84/0/0/0` |
| `2026-09-01T13:45:44Z` | profil `provider-playwright-runtime` complet | `PASS` — Surefire `1065/0/0/5`, Failsafe `84/0/0/0` |
| `2026-09-01T13:47:21.0195627Z` | diff, secrets, flags, loopback, artefacts et ressources résiduelles | `PASS` |
| `2026-09-01T14:00:53Z` | réception du bloc propriétaire validant le commit et la preuve qualifiés | `VALIDATE` |
| `2026-09-01T14:00:53Z` | readiness locale reconnue et déplacement documentaire vers `completed` | `PASS` |

## 8. État courant

```text
WORK_ORDER_STATUS=VALIDATED
IMPLEMENTATION_STATUS=COMPLETED
IMPLEMENTATION_COMMIT=154349a2fbebe3fd0a43a63c7105f690ff04976b
QUALIFICATION_STATUS=PASS_LOCAL_FAIL_CLOSED
QUALIFICATION_REPORT=docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md
QUALIFICATION_REPORT_SHA256=9bd030f4cc92f32bdeee977089d3f4c5c709278fec4c24cc66ed8fb6d258be5f
OWNER_REVIEW_REQUIRED=NO
OWNER_REVIEW_DECISION=VALIDATE
OWNER_REVIEW_BLOCK_STATUS=COMPLETE
LOCAL_READINESS_ACKNOWLEDGED=YES
IMPLEMENTATION_COMMIT_MATCH=YES
QUALIFICATION_REPORT_SHA256_MATCH=YES
OWNER_DECISION_RECORDED_AT_UTC=2026-09-01T14:00:53Z
OWNER_DECISION_RECORDED_AT_EUROPE_PARIS=2026-09-01T16:00:53+02:00
WORK_ORDER_MOVE_TO_COMPLETED=YES
MOVE_TO_COMPLETED_AUTHORIZED=YES
MOVE_TO_COMPLETED_PERFORMED=YES
WORK_ORDER_LOCATION=docs/work_orders/completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md
PROVIDER_CALLS_UNDER_WO030=0
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## 9. Décision propriétaire reçue

```text
J9_WO030_OWNER_REVIEW_DECISION=VALIDATE
J9_WO030_WORK_ORDER=WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening
J9_WO030_IMPLEMENTATION_COMMIT=154349a2fbebe3fd0a43a63c7105f690ff04976b
J9_WO030_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
J9_WO030_QUALIFICATION_REPORT_SHA256=9bd030f4cc92f32bdeee977089d3f4c5c709278fec4c24cc66ed8fb6d258be5f
J9_WO030_LOCAL_READINESS_ACKNOWLEDGED=YES
J9_WO030_WORK_ORDER_MOVE_TO_COMPLETED=YES
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REAL_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Le bloc complet a été reçu et enregistré le `2026-09-01T14:00:53Z`, soit
`2026-09-01T16:00:53+02:00` en Europe/Paris. Il valide la readiness locale et autorise le seul
déplacement documentaire de WO-030 vers les Work Orders terminés. Il n'autorise aucun appel
fournisseur ou receiver réel, déploiement VPS, usage de production, push ou merge.
