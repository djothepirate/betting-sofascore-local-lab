# WO-SS-20260831-019 — Preuve bornée de robustesse fournisseur pour J9

- **Statut :** `READY_FOR_OFFLINE_READINESS`
- **Date d'ouverture :** 2026-08-31
- **Jalon :** J9 — Preuve préalable à la décision
- **Base locale :** `a47c932`
- **Branche/worktree :** `codex/j9-provider-robustness`
- **Work Order parent :** `WO-SS-20260831-018-decision-j9`
- **ADR applicable :** `ADR-SS-001 v1.4`
- **Nouvel ADR :** `ADR-SS-002 v1.0 — ACCEPTED, GLOBAL_GO_NOT_GRANTED`
- **État de la preuve :** `DRAFT`
- **Réseau fournisseur :** `NOT_AUTHORIZED`
- **Go propriétaire global :** `NOT_GRANTED`
- **Go consommé :** `NO`
- **Plafond direct global :** `38`
- **Dossier de remplacement :** `NOT_AUTHORIZED`
- **Nouvel endpoint, URI, transport, code ou migration :** `NONE`
- **Polling, scheduler, live, retry ou fallback :** `NOT_AUTHORIZED`
- **Production VPS courante :** `NOT_AUTHORIZED`
- **Option de production VPS future :** `NOT_EXCLUDED, NOT_MEASURED`

## 1. Objectif

Produire une preuve réelle, locale, manuelle, bornée et reproductible de la robustesse actuelle des
parcours fournisseur déjà autorisés, sur trois dossiers hétérogènes, afin d'éclairer la décision
propriétaire J9.

La campagne doit mesurer :

- accessibilité et outcomes de transport ;
- compatibilité des parseurs courants ;
- disponibilité et complétude des composants ;
- coût exact en appels directs ;
- respect des confirmations, de la lease, du délai et de l'isolation Playwright ;
- audit, reproductibilité et limites non mesurées.

Elle ne constitue ni un test de charge, ni une qualification statistique, juridique, commerciale,
VPS ou de production. Elle ne prend pas la décision J9.

## 2. État initial et portes cumulatives

```text
EVIDENCE_STATUS=DRAFT
NETWORK_AUTHORIZED=NO
OWNER_GO_CONSUMED=NO
MAX_DIRECT_CALLS=38
REPLACEMENT_DOSSIER_ALLOWED=NO
ADR_SS_002_STATUS=ACCEPTED_V1_0
OFFLINE_READINESS=NOT_EXECUTED
V28_BACKUP_RESTORE=NOT_EXECUTED
GLOBAL_OWNER_GO=NOT_GRANTED
```

Le réseau reste bloqué jusqu'à preuve cumulative des quatre portes :

1. ADR-SS-002 accepté explicitement par le propriétaire après lecture de la revue officielle —
   acceptation enregistrée à `2026-08-30T22:54:47Z`, porte `SATISFIED` ;
2. tests et qualifications hors ligne entièrement verts ;
3. sauvegarde chiffrée fraîche au schéma Flyway V28 et restauration qualifiée sur une base isolée ;
4. go propriétaire global, explicite, à usage unique, portant le manifeste final.

L'acceptation explicite de l'ADR a été reçue. Elle ne remplace ni la readiness hors ligne, ni la
sauvegarde/restauration V28, ni le go réseau distinct.

## 3. Revue d'ADR-SS-001

Les parcours, routes, transports et gardes existants restent inchangés. Le plafond global de 38 est
cependant supérieur au précédent J8 de 30 et constitue le déclencheur de réexamen « modification des
bornes de volume » d'ADR-SS-001 §9.

```text
ADR_SS_001_VERSION=1.4
ADR_SS_001_MODIFICATION=NO
NEW_ADR_ID=ADR-SS-002
NEW_ADR_STATUS=ACCEPTED_V1_0
ADR_REEXAMINATION_REQUIREMENT=SATISFIED_BY_ADR_SS_002_V1_0
PROVIDER_NETWORK_AUTHORIZED_BY_ADR_ACCEPTANCE=NO
```

### 3.1 Décision propriétaire enregistrée

```text
ADR_SS_002_OWNER_DECISION=ACCEPT
OFFICIAL_SOURCE_REVIEW_ACKNOWLEDGED=YES
CORPUS_D1_D2_D3_ACCEPTED=YES
GLOBAL_MAXIMUM_DIRECT_ATTEMPTS_38_ACCEPTED=YES
ONE_GLOBAL_GO_MODEL_ACCEPTED=YES
GO_MAXIMUM_DURATION_60_MINUTES_ACCEPTED=YES
NATIVE_J4_J5_404_CONTINUATION_ACCEPTED=YES
PRIMARY_DATABASE_PURGE=NO
INTEGRATION_OR_PRODUCTION_AUTHORIZED=NO
FUTURE_VPS_PRODUCTION_OPTION_ACKNOWLEDGED=NOT_EXCLUDED_BUT_NOT_AUTHORIZED
```

La décision est bornée à ADR-SS-002 v1.0. Elle n'est pas un go global et n'autorise aucun appel
fournisseur.

## 4. Périmètre fermé

Seules les familles logiques suivantes peuvent être utilisées :

```text
SCHEDULED_EVENTS
TOURNAMENT_SCHEDULED_EVENTS
EVENT_DETAILS
EVENT_STATISTICS
EVENT_INCIDENTS
EVENT_LINEUPS
```

Routes et identités restent construites par les adaptateurs allowlistés. J4 est limité à la phase 2
sur un UUID canonique résolu côté serveur. Restent exclus :

```text
TOURNAMENT_STANDINGS
TEAM_RECENT_EVENTS
FREE_URI
FREE_PROVIDER_EVENT_ID
QUERY_EXTENSION
OTHER_ORIGIN
RESTCLIENT_FALLBACK
FLARESOLVERR
SECOND_BROWSER_CONTEXT
COOKIE_OR_STORAGE_STATE_REUSE
PROXY_OR_STEALTH
HAR_TRACE_VIDEO_SCREENSHOT_DOWNLOAD
```

## 5. Corpus immuable

| Ordre | Dossier | Provider ID | UUID canonique | Rôle dans la preuve |
|---:|---|---:|---|---|
| D1 | Cittadella — Atalanta U23 | `16691018` | `f4713f80-4769-3656-ba51-61d8ac1aa814` | Ancre du chemin complet J3 → tournoi → J4 → J5 ; date `2026-08-15`, phase `15118`, tournoi `824`, saison `99790` |
| D2 | Barracas Central — Rosario Central | `16671566` | `da075869-34d4-3d42-83d2-613583691845` | Cas historique J4/J5 incluant le vocabulaire incident `Off the ball foul` |
| D3 | Lille — Paris Saint-Germain | `16310930` | `c40066c9-987b-38d9-b415-869a453d2ad6` | Cas récent Playwright avec statistiques, incidents et compositions complètes |

Le corpus est un panel de robustesse choisi, pas un échantillon statistique. Les UUID, provider IDs,
ordre et rôle sont figés. Toute incohérence d'identité bloque la campagne avant réseau. Aucun
remplacement ou quatrième dossier n'est permis.

## 6. Séquence et plafond

Le runtime ne fournit pas de campagne multi-match. L'exécution est une série manuelle de huit
campagnes unitaires existantes :

| Séquence | Campagne | Cible | Maximum direct |
|---:|---|---|---:|
| 1 | J3 `SCHEDULED_EVENTS`, pages contiguës `1..N` | date D1 | 25 |
| 2 | J3 `TOURNAMENT_SCHEDULED_EVENTS` | tournoi/date D1 | 1 |
| 3 | J4 `EVENT_DETAILS` phase 2 | D1 | 1 |
| 4 | J5 `STATISTICS -> INCIDENTS -> LINEUPS` | D1 | 3 |
| 5 | J4 `EVENT_DETAILS` phase 2 | D2 | 1 |
| 6 | J5 `STATISTICS -> INCIDENTS -> LINEUPS` | D2 | 3 |
| 7 | J4 `EVENT_DETAILS` phase 2 | D3 | 1 |
| 8 | J5 `STATISTICS -> INCIDENTS -> LINEUPS` | D3 | 3 |
|  | **Total absolu** |  | **38** |

```text
MAX_J3_SCHEDULED_ATTEMPTS=25
MAX_TOURNAMENT_DISCOVERY_ATTEMPTS=1
MAX_J4_PHASE2_ATTEMPTS=3
MAX_J5_ATTEMPTS=9
MAX_TOTAL_DIRECT_ATTEMPTS=38
```

Le ledger est vérifié avant chaque sous-campagne. Si le prochain maximum possible dépasse le
reliquat, la préparation est refusée. Aucune tentative fictive n'est créée pour une unité non
commencée. Le contrôle global reste procédural ; toute ambiguïté découverte hors ligne bloque le
réseau et exige un Work Order de code séparé.

Les caches J3 et tournoi conservent leur TTL de 600 secondes. Ils ne sont ni supprimés, ni
invalidés, ni contournés. Un cache hit inattendu vaut zéro appel et borne la dimension concernée à
`PARTIAL` ou `NOT_MEASURED`.

## 7. Revue factuelle des sources officielles

Revue effectuée le `2026-08-30T22:29:27Z`, soit le 31 août 2026 en Europe/Paris, uniquement sur des
domaines officiels. Les observations ne constituent pas un avis juridique.

| URL officielle | Statut et fait supporté | Limite explicite de l'observation |
|---|---|---|
| `https://www.sofascore.com/en-us/terms-and-conditions` | Page officielle ; dernière mise à jour déclarée le 18 septembre 2024. Le texte restreint la charge serveur par requêtes automatisées, l'intégration, l'agrégation, le scraping, la reproduction et l'extraction substantielle sans consentement explicite, avec les réserves légales qu'il formule. | Le rendu direct est dynamique ; aucune permission propre aux endpoints du laboratoire, licence API ou limite de débit n'a été extraite. |
| `https://www.sofascore.com/robots.txt` | `text/plain`, 181 lignes. Des chemins sont exclus ; aucune correspondance textuelle `/api`, `Allow:` ou `Crawl-delay` n'est observée. | Hôte `www` uniquement. Une absence de règle n'est pas une autorisation et ne qualifie pas `api.sofascore.com`. |
| `https://api.sofascore.com/api/docs/external` | Point d'entrée officiel intitulé `Sofascore API`. | Aucun contenu extractible permettant d'établir authentification, licence, clé, tarif, rate limit ou endpoints permis. |
| `https://corporate.sofascore.com/contact` | Le formulaire officiel contient `Product -> API`. | Canal de demande seulement, sans permission publiée. |
| `https://corporate.sofascore.com/widgets` | Intégration officielle par widget/iframe décrite comme gratuite et sans limitation pour ce widget. | Ne documente pas l'extraction, le stockage ou les endpoints utilisés par le laboratoire. |

```text
OFFICIAL_SOURCE_REVIEW=COMPLETED_FACTUAL_ONLY
AUTOMATED_REQUEST_RESTRICTIONS_FOUND=YES
EXPLICIT_PERMISSION_FOR_CURRENT_LAB_ENDPOINTS_EVIDENCED=NO
API_LICENSE_OR_RATE_LIMIT_EXTRACTED=NO
OFFICIAL_API_CONTACT_CHANNEL_PRESENT=YES
LEGAL_CONCLUSION=NOT_PROVIDED
OWNER_ACKNOWLEDGEMENT_BEFORE_ADR_ACCEPTANCE=SATISFIED
```

Le propriétaire a accepté ADR-SS-002 après reconnaissance explicite de cette revue, sans exiger à
ce stade un consentement préalable via le canal officiel. Ce canal reste disponible pour une
démarche distincte. Le silence d'une source, `robots.txt`, la page API sans contenu extractible et
les campagnes techniques antérieures ne seront jamais présentés comme une permission.

## 8. Readiness hors ligne obligatoire

Toutes les commandes s'exécutent avec les flags fournisseur à `false` et ne doivent produire aucun
appel SofaScore.

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
docker compose --env-file .env config
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Invoke-J4PlaywrightLoopbackQualification.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Invoke-J5PlaywrightLoopbackQualification.ps1
```

La readiness confirme notamment :

- Java 25, Spring Boot 4.1.0 et Flyway V28 ;
- `server.address=127.0.0.1` ;
- toutes les activations fournisseur, Playwright, J3/J4/J5, polling et refresh à `false` par défaut ;
- allowlist exacte par profil et refus des familles différées ;
- concurrence `1`, délai `3 s`, timeout `10 s`, limite 5 Mio et zéro retry ;
- confirmations à usage unique, leases exclusives et arrêts terminaux ;
- scénarios loopback J3/J4/J5, y compris `404`, incident terminal et nettoyage ;
- aucun secret, payload brut ou artefact navigateur dans le diff et les racines runtime.

Un test qui échoue, un appel fournisseur détecté ou un besoin de modification runtime ramène le
statut à `OPEN_AWAITING_PREREQUISITES`. Aucun correctif n'est improvisé sous ce Work Order.

## 9. Sauvegarde/restauration V28

Après readiness et avant le go :

1. arrêter l'application et vérifier que le port 8087 est libre ;
2. choisir un nouveau fichier `.age` absolu hors dépôt, sans écrasement ;
3. exécuter `scripts/Backup-Restore-J6.ps1` avec saisie interactive de la phrase secrète `age` ;
4. restaurer dans la base temporaire créée par le script ;
5. exiger Flyway V28, comptes, couverture et fingerprints identiques, y compris le ledger J8 ;
6. conserver seulement dans la preuve Git les hashes et métadonnées minimisées ;
7. ne lancer aucune purge primaire.

```text
J6_BACKUP_RESULT=QUALIFIED
J6_RESTORE_QUALIFIED=YES
FLYWAY_VERSION=28
PRIMARY_DATABASE_PURGE=NO
```

Une ancienne sauvegarde V22, même qualifiée, ne satisfait pas cette porte.

## 10. Go propriétaire global

Après acceptation ADR, readiness et sauvegarde, le manifeste read-only final est soumis au
propriétaire. Le go doit identifier explicitement :

```text
WORK_ORDER=WO-SS-20260831-019-j9-provider-robustness
ADR=ADR-SS-002_<ACCEPTED_VERSION>
TARGET_PROVIDER_EVENT_IDS=16691018,16671566,16310930
TARGET_CANONICAL_EVENT_IDS=f4713f80-4769-3656-ba51-61d8ac1aa814,da075869-34d4-3d42-83d2-613583691845,c40066c9-987b-38d9-b415-869a453d2ad6
MAXIMUM_DIRECT_ATTEMPTS=38
WINDOW_UTC=[FROM,TO)
MAXIMUM_WINDOW_DURATION=60m
EXECUTION_ACTOR=<explicit>
GO_USE=ONE_TIME
```

Le go expire à `TO`, au premier incident global ou à la fin de la série. Il ne survit pas à un
redémarrage décidé après incident. Les confirmations techniques propres à chaque sous-campagne
restent obligatoires et utilisent des phrases, IDs et claims neufs.

## 11. Contrat d'exécution

```text
WORKER_JVM=FRESH_PER_SUBCAMPAIGN
CHROMIUM=FRESH_PER_SUBCAMPAIGN
BROWSER_CONTEXT=FRESH_NON_PERSISTENT_PER_SUBCAMPAIGN
MAXIMUM_CONCURRENCY=1
MINIMUM_DELAY_BETWEEN_DIRECT_STARTS=3s
REQUEST_TIMEOUT=10s
MAX_RESPONSE_BODY=5MiB
CONFIRMATION_TTL=5m
RETRY=0
FALLBACK=0
```

La séquence est strictement ordonnée. Le segment D2/D3 ne commence que si le chemin D1 n'a pas
déclenché d'arrêt global. Il n'existe aucune boucle entre dossiers. Chaque unité est préparée,
confirmée, exécutée, auditée et nettoyée avant la suivante.

## 12. Outcomes, `404` et arrêts

### 12.1 `404` natif

- J3 : `ENDPOINT_UNAVAILABLE`, arrêt de la pagination et de toute la série ;
- J4 phase 2 : `COMPLETED_UNAVAILABLE`, sans parsing ni retry, puis poursuite globale autorisée ;
- J5 : famille `UNAVAILABLE`, puis famille suivante ;
- tout `404` J4/J5 borne le verdict global à `PARTIAL_BOUNDED`.

### 12.2 Arrêt global immédiat

- `401`, `403`, `429`, `5xx`, timeout ou redirection ;
- HTML, challenge, contenu inattendu ou origine/route/méthode différente ;
- contenu sensible ou corps supérieur à 5 Mio ;
- JSON ou schéma incompatible ;
- erreur de transport, persistance, parsing, traitement ou ledger ;
- tentative sans outcome, identité incohérente ou perte de lease ;
- concurrence supérieure à un, délai inférieur à trois secondes ou nettoyage incomplet ;
- demande opérateur, plafond atteint, fenêtre expirée ou go invalide.

Les campagnes futures non commencées sont documentées `NOT_STARTED_AFTER_GLOBAL_STOP`. Elles ne
créent ni appel ni ligne de tentative fictive. Aucun retry, fallback, import de remplacement,
nouveau contexte ou dossier de substitution n'est permis.

## 13. Preuve et métriques

La population est le ledger exact dans `[FROM,TO)`, avec manifeste, comptes et hash de population
gelés. Le rapport distinct J9 consignera au minimum :

- campagnes prévues, commencées et terminales ;
- tentatives, réponses, parsings, 404, refus et erreurs ;
- nombre d'appels par famille, segment et dossier ;
- latences `n`, min, p50, p95 et max sans masquer les petits dénominateurs ;
- pages J3, cache hits, contiguïté et terminal `hasNextPage` ;
- versions de parseur, incompatibilités et contenus inattendus ;
- présence directe et statut des cinq composants de chaque dossier ;
- complétude `COMPLETE`, `PARTIAL`, `EMPTY_VALID`, `UNAVAILABLE` ou `UNKNOWN` ;
- snapshots insérés/dédupliqués, hashes, heures et provenance, sans payload ;
- dossiers exploitables et strictement complets selon la définition J8 ;
- concurrence et délai observés, durée de fenêtre et gestes opérateur ;
- nettoyage des processus, listeners et artefacts navigateur ;
- limites `NOT_MEASURED`, dont exactitude externe, valeur analytique relative, coût comparatif,
  accès depuis un VPS et compatibilité de production.

Les exports sont produits deux fois avec les mêmes bornes et `asOf`, puis comparés octet pour
octet. Les fichiers runtime restent sous `exports/` ignoré. Seuls le rapport minimisé, son SHA-256,
le hash de population et la validation humaine sont versionnés après la campagne.

## 14. Verdict déterministe

```text
PASS
  = série terminée sous 38
  + aucun incident bloquant
  + toutes les réponses non-404 parse-compatibles et persistées
  + trois dossiers exploitables
  + audit et nettoyage conformes

PARTIAL_BOUNDED
  = uniquement 404 natifs, cache hit limitant ou lacune explicitement bornée
  + aucune violation de sécurité
  + preuve sûre et auditable

STOPPED
  = condition d'arrêt ou prérequis violé
```

La validation du processus et le résultat fournisseur restent séparés. Une preuve correctement
arrêtée peut être valide comme audit tout en produisant `STOPPED`.

## 15. Revue humaine et décision J9

Après arrêt et reverrouillage complet, l'opérateur vérifie pour chaque dossier identité, date,
compétition, état, score, détail, trois familles J5, complétude, provenance, snapshot, hash, parseur
et heure de réception. Aucun payload, cookie, jeton ou artefact navigateur n'est copié dans Git.

La preuve met à jour la matrice WO-018, mais ne choisit pas automatiquement J9. Le propriétaire
reçoit `ABANDON`, `KEEP_LOCAL` et `PREPARE_OPTIONAL_INTEGRATION` avec la recommandation dérivée et
confirme explicitement sa décision.

Une future production VPS demeure une option d'architecture non exclue. Elle n'est pas qualifiée
par cette campagne Windows. Une étude de faisabilité ultérieure et distincte comparera le push
local optionnel à une topologie VPS Playwright ; si la décision finale J9 le justifie, ADR-SS-003
portera ensuite le choix d'architecture. Une nouvelle revue des droits d'usage et une qualification
propre au réseau et au runtime VPS resteront obligatoires.

## 16. Livraison Git

Lot préparatoire prévu :

```text
docs(j9): propose bounded provider robustness evidence
```

Acceptation propriétaire de l'ADR :

```text
docs(j9): accept bounded robustness ADR
```

Après readiness/sauvegarde, puis après campagne et revue, des commits distincts consigneront les
preuves réelles. Aucun push, PR ou merge vers `main` n'est autorisé. La branche
`codex/j9-decision` ne sera avancée qu'après validation des commits de WO-019.

## 17. Portes de statut

```text
OPEN_AWAITING_PREREQUISITES
  -> READY_FOR_ADR_OWNER_DECISION
  -> READY_FOR_OFFLINE_READINESS
  -> READY_FOR_GLOBAL_OWNER_GO
  -> QUALIFICATION_RUNNING
  -> READY_FOR_HUMAN_REVIEW
  -> VALIDATED
```

Terminaux alternatifs :

```text
CANCELLED_BEFORE_NETWORK
STOPPED_BY_SAFETY_RULE
INVALID_EVIDENCE
```

WO-019 ne passe à `VALIDATED` qu'après preuve versionnée et décision humaine sur sa validité. Il est
alors déplacé vers `docs/work_orders/completed`. Ce déplacement n'autorise aucun nouvel appel.

## 18. Journal d'exécution

```text
BRANCH_BASE=a47c932
ADR_SS_002_DRAFT=CREATED
ADR_SS_002_VERSION=1.0
ADR_OWNER_DECISION=ACCEPTED
ADR_OWNER_DECISION_RECORDED_AT_UTC=2026-08-30T22:54:47Z
OFFICIAL_SOURCE_REVIEW=COMPLETED_FACTUAL_ONLY
STANDARD_VERIFY=PASS_928_TESTS_0_FAILURE_0_ERROR_4_SKIPPED
STANDARD_VERIFY_COMMAND=.\mvnw.cmd clean verify
DIFF_CHECK=PASS
LOCAL_MARKDOWN_LINKS=PASS
SECRET_SCAN=PASS_NO_CREDENTIAL_PATTERN_IN_J9_FILES
LOOPBACK_AND_NETWORK_DEFAULTS_CHECK=PASS
INTEGRATION_VERIFY=PENDING
VERIFY_LOCAL_WITH_INTEGRATION=PENDING
LOOPBACK_J3=PENDING
LOOPBACK_J4=PENDING
LOOPBACK_J5=PENDING
V28_BACKUP_RESTORE=PENDING
GLOBAL_OWNER_GO=PENDING
PROVIDER_CALLS_UNDER_WO019=0
```

ADR-SS-002 v1.0 est accepté et WO-019 entre dans la porte de readiness hors ligne. Les tests
PostgreSQL, qualifications Chromium loopback et sauvegarde/restauration restent `PENDING` tant que
leurs résultats ne sont pas consignés. Aucun de ces travaux ne constitue une autorisation réseau.
