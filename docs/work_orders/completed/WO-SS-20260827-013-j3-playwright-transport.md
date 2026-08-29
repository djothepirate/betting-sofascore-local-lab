# WO-SS-20260827-013 - Migration Playwright des parcours fournisseur manuels J3

- **Statut :** `VALIDATED`
- **Jalon :** `J3`
- **Date d'ouverture :** `2026-08-27`
- **Decision d'ouverture :** proprietaire du Betting Project
- **Branche cible :** `codex/j3-playwright-transport`
- **Worktree dedie :** `CREATED` (`.tmp/wo013-playwright`)
- **ADR applicable :** `ADR-SS-001 v1.3`
- **Qualification de reference :** `WO-SS-20260823-011` (`VALIDATED`)
- **Implementation par cette ouverture :** `COMPLETED`
- **Developpement et qualification loopback :** `LOCAL_READINESS_PASS`
- **Qualification fournisseur humaine :** `PASS_BY_OWNER_EXECUTION_2026-08-28`
- **Appel fournisseur supplementaire :** `NOT_AUTHORIZED`
- **Polling, scheduler, retry ou fallback :** `NOT_AUTHORIZED`
- **Production, VPS ou dependance critique :** `NOT_AUTHORIZED`
- **Decision de cloture :** `OWNER_CONFIRMED_2026-08-28`

## 1. Objectif

Remplacer les deux transports fournisseur manuels J3 fondes sur `RestClient` par un transport
Playwright local, manuel et opt-in, sans modifier les parcours d'import JSON hors ligne ni ajouter
de nouvel endpoint.

Ce Work Order livre egalement le socle Playwright durable commun que J4 et J5 devront reutiliser :

- un worker JVM enfant proprietaire de Playwright, Chromium et du contexte ;
- un superviseur identifiant exactement la campagne et son arbre de processus ;
- un protocole IPC prive, borne et sans payload dans les journaux ;
- une lease exclusive couvrant toute une campagne fournisseur ;
- un arret cible d'une requete en vol et un nettoyage mesurable ;
- une installation et une qualification loopback explicitement opt-in.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Prerequis d'implementation satisfaits

L'implementation a commence uniquement apres verification des conditions bloquantes suivantes :

1. l'ADR-SS-001 v1.3, les regles `AGENTS.md` et la cloture de `WO-SS-20260823-011` sont consolides
   sur une base Git propre ;
2. `WO-SS-20260812-003` et `WO-SS-20260820-009` restent les contrats metier J3 de reference ;
3. un worktree propre est cree depuis la base approuvee sur `codex/j3-playwright-transport` ;
4. aucun fichier POC FlareSolverr ou `src/j5-browser-qualification*` n'est copie dans le runtime ;
5. `mvnw.cmd clean verify` est vert avant la premiere modification d'implementation ;
6. aucune edition simultanee du meme worktree n'est effectuee depuis Eclipse et par un agent.

Ces prerequis ont autorise l'implementation et la qualification loopback locale. Ils ne valent ni
appel a SofaScore, ni qualification fournisseur, ni cloture du Work Order. L'installation du
navigateur et chaque lancement du worker restent des actions operateur explicites.

## 3. Allowlist exacte

### 3.1 Collecte datee paginee

```text
Famille : SCHEDULED_EVENTS
Methode : GET
Origine : https://www.sofascore.com
Chemin  : /api/v1/sport/football/scheduled-tournaments/{ISO_DATE}/page/{PAGE}
ISO_DATE: LocalDate validee cote serveur, rendue strictement YYYY-MM-DD
PAGE    : entier de 1 a 25
```

La campagne :

- commence toujours a la page `1` ;
- construit chaque URI exclusivement depuis la date confirmee cote serveur et le numero courant ;
- poursuit uniquement si le JSON parse de la page courante porte `hasNextPage=true` ;
- s'arrete sur `hasNextPage=false`, a la page `25` ou au premier incident terminal ;
- n'autorise jamais la saisie libre d'une URI ou d'un numero de page initial.

### 3.2 Decouverte par tournoi et date

```text
Famille : TOURNAMENT_SCHEDULED_EVENTS
Methode : GET
Origine : https://www.sofascore.com
Chemin  : /api/v1/unique-tournament/{UNIQUE_TOURNAMENT_ID}/scheduled-events/{ISO_DATE}
ID      : entier strictement positif issu de tournament.uniqueTournament.id
ISO_DATE: LocalDate validee cote serveur, rendue strictement YYYY-MM-DD
```

Une confirmation distincte autorise au plus un GET. Le libelle du tournoi ne participe jamais a
l'URI. La collecte paginee et la decouverte tournoi ne s'enchainent pas automatiquement.

Pour les deux familles, tout port explicite, user-info, query, fragment, autre schema, autre hote,
autre methode, redirection ou route secondaire est refuse.

## 4. Contrat Playwright durable commun

Le socle ne doit pas reprendre directement `PlaywrightQualificationCandidate`. Ce candidat etait un
banc de mesure ; son arret en vol n'est pas une implementation applicative suffisante.

Le runtime attendu respecte les invariants suivants :

- Playwright Java `1.62.0` et son Chromium associe, installes uniquement par une commande operateur
  explicite dans un cache dedie ignore par Git ;
- dependance disponible seulement dans un profil Maven applicatif opt-in a definir, propose
  `provider-playwright-runtime` ;
- profil de qualification locale distinct, propose `provider-playwright-local-qualification` ;
- application construite avec ce profil toujours inerte au demarrage ;
- aucun `Playwright.create()` dans un constructeur Spring, au chargement du contexte, dans un
  scheduler, un test standard ou un chemin de consultation ;
- creation paresseuse du worker seulement apres un claim operateur valide et le premier cache miss ;
- un `BrowserContext` neuf et non persistant par campagne ;
- aucune utilisation du Chrome personnel, d'un profil utilisateur ou d'un etat persistant ;
- JavaScript desactive lorsque le contrat exact du GET le permet, service workers bloques,
  telechargements refuses, popups, WebSockets et sous-requetes traites comme incidents ;
- aucun proxy, changement d'adresse, User-Agent personnalise, extension, furtivite, CAPTCHA,
  page d'amorcage, compte, credential, cookie injecte ou `storageState` ;
- aucun HAR, trace, video, capture ou telechargement conserve ;
- statut, `Content-Type` utile et octets lus uniquement depuis `Response.status()`, les en-tetes
  bornes de `Response` et `Response.body()` ;
- DOM, `page.content()`, `Response.text()` et toute reconstruction ou reencodage interdits ;
- fermeture de la session, du contexte, du navigateur, du worker et de Playwright sur tous les
  chemins terminaux.

Le payload ne peut transiter ni dans une ligne de commande, ni dans stdout/stderr, ni dans un
message d'exception, ni dans un fichier temporaire non authentifie. Le protocole IPC doit etre
borne a 5 Mio, prive, attribuable a la campagne et efface apres consommation.

## 5. Coordination et arret

`ManualProviderRequestCoordinator` conserve la concurrence `1` et le delai minimal de trois
secondes entre les debuts de GET, mais son verrou actuel par requete ne suffit pas. WO-013 ajoute une
lease de campagne exclusive afin qu'aucune campagne J4 ou J5 ne puisse s'intercaler entre deux pages
J3.

Les deux actions d'arret J3 doivent signaler le superviseur de la campagne courante avant de
reverrouiller le controle metier. L'arret cible uniquement le PID et l'heure de creation du worker
possede, puis son arbre Chromium. Un arret global par nom de processus est interdit.

Bornes d'acceptation :

```text
STOP_ACKNOWLEDGEMENT_MAX=500ms
IN_FLIGHT_CANCELLATION_MAX=2s
PROCESS_TREE_CLEANUP_MAX=5s
RESIDUAL_OWNED_PROCESS_COUNT=0
```

## 6. Semantique des reponses

- le brut, sa taille et son SHA-256 sont controles et persistables avant parsing ;
- `2xx` poursuit le contrat J3 existant ;
- `404` est conserve et classe explicitement `ENDPOINT_UNAVAILABLE`, sans parsing ni retry ;
- pour la pagination, un `404` termine la campagne car aucune page suivante ne peut etre deduite ;
- tout autre non-`2xx`, timeout, HTML/challenge, redirection, route inattendue, corps trop grand ou
  contenu sensible arrete immediatement la campagne ;
- aucun fallback vers `RestClient`, FlareSolverr, un import local ou un nouveau contexte ;
- un incident consomme et verrouille l'intention selon les regles J3 existantes.

Les imports locaux `page-1.json` a `page-N.json` et l'import JSON de decouverte tournoi restent
strictement sans navigateur, sans cache fournisseur et sans transport.

## 7. Lots d'implementation

1. **Base et contrat commun** : figer le packaging du worker, l'IPC, la lease de campagne, le
   superviseur et la politique d'arret.
2. **Profils opt-in** : installer/lancer Playwright seulement par commandes explicites et verifier
   son inertie au demarrage normal.
3. **Transport J3 pagine** : remplacer `ProviderScheduledEventsRestTransport` sans fallback.
4. **Transport J3 tournoi** : remplacer `ProviderTournamentScheduledEventsRestTransport` sans
   partager le contexte de la campagne precedente.
5. **Services et controle** : ouvrir la session apres claim/cache miss, la fermer en `finally` et
   raccorder les arrets Web au superviseur.
6. **Qualification loopback** : exercer le vrai Chromium uniquement contre `127.0.0.1`.
7. **Documentation et readiness** : architecture J3, runbook, rapport, README, changelog et WO.
8. **Qualification fournisseur bornee** : deux actions J3 distinctes, uniquement apres preparation,
   confirmation et geste final proprietaire.

Les lots 1 a 8 sont implementes et qualifies. Aucun appel fournisseur supplementaire n'est autorise
par la cloture.

## 8. Matrice de validation minimale

| Scenario | Resultat attendu |
|---|---|
| demarrage normal / profil absent | aucun worker, Chromium ou telechargement |
| profil opt-in mais aucune confirmation | runtime inerte, zero GET |
| claim invalide, expire ou incoherent | zero creation de contexte et zero GET |
| cache hit | zero Playwright et resultat metier courant preserve |
| import JSON local | zero Playwright et zero cache fournisseur |
| pagination nominale loopback | pages `1..N`, un contexte, statut/type/octets exacts |
| seconde campagne | nouveau worker et nouveau contexte, aucun etat reutilise |
| `404` | brut conserve, indisponibilite explicite, aucun appel suivant |
| `403`, `429` ou `5xx` | arret terminal, zero retry |
| redirection | cible de redirection jamais atteinte |
| HTML/challenge ou route secondaire | incident terminal, aucune normalisation |
| timeout, corps > 5 Mio ou canari sensible | fermeture sure, aucune reprise |
| arret pendant une reponse lente | bornes 500 ms / 2 s / 5 s et zero residu |
| campagne J3 face a une tentative J4/J5 | aucune intercalation |
| qualification humaine `SCHEDULED_EVENTS` | pages 1 a 12 HTTP 200, `PARSED`, terminal `hasNextPage=false` |
| qualification humaine `TOURNAMENT_SCHEDULED_EVENTS` | cinq actions confirmees, snapshots 584 a 588, comptes verifies |

Commandes requises avant proposition de cloture :

```text
mvnw.cmd clean verify
mvnw.cmd -Pintegration-tests verify
scripts/Verify-Local.ps1 -WithIntegrationTests
<commande loopback opt-in definie par WO-013>
git diff --check
```

Les tests Maven standards et d'integration PostgreSQL ne lancent jamais Chromium et n'accedent
jamais a SofaScore.

Readiness technique executee le 28 aout 2026 :

```text
STANDARD_TESTS=691 (2 SKIPPED)
INTEGRATION_TESTS=52
WORKER_PROTOCOL_AND_SECURITY_TESTS=9
REAL_CHROMIUM_LOOPBACK_TESTS=11
VERIFY_LOCAL=PASS
PROFILE_CONTAMINATION_GATE=PASS
PROVIDER_ACCESS_PERFORMED=NO
```

Correctif de recette du 28 aout 2026 : le premier essai operateur a echoue avant `READY` et avant
tout GET SofaScore, car `Playwright.create()` tentait d'installer implicitement Firefox et WebKit
apres l'installation explicite de Chromium. Le superviseur impose maintenant
`PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`, le lanceur exige un cache Chromium complet et la fermeture
accepte la disparition du PID racine seulement apres une trame terminale authentifiee. La preuve
minimisee v6 n'annonce plus de requete fournisseur lorsque l'ouverture du worker echoue. Apres
correction, `clean verify` passe a 691 tests (2 ignores) et deux executions consecutives du banc
Chromium loopback passent a 11/11.

Le nouvel essai operateur du 28 aout 2026 qualifie `SCHEDULED_EVENTS`. Apres une preparation, une
confirmation et une action finale distincte, les pages 1 a 12 sont demandees dans l'ordre. Les
snapshots 572 a 583 sont tous HTTP `200`, `PARSED` et `INSERTED`; la page 12 termine normalement
avec `hasNextPage=false`. La preuve minimisee v6 indique 12 requetes fournisseur, zero cache hit,
zero import JSON local, aucun echec, retry, polling, scheduler, cookie, jeton, compte ou session.
L'arret global est reapplique et le circuit revient a
`LOCKED / MANUAL_COLLECTION_TERMINAL_LOCK`.

Cinq preparations, confirmations et actions finales independantes qualifient ensuite
`TOURNAMENT_SCHEDULED_EVENTS` pour Ligue 1, Premier League, LaLiga, Bundesliga et Serie A. Le
catalogue issu des 12 pages expose 524 tournois actionnables apres exclusion de 648 occurrences.
Les cinq requetes HTTP `200` conservent les snapshots 584 a 588, passent chacune le controle de
compte et ajoutent six observations canoniques sans deduplication : Lille - Paris Saint-Germain,
Crystal Palace - Manchester City, Real Racing Club - Elche, Deportivo Alaves - Villarreal,
FC Bayern Munchen - VfB Stuttgart et AC Milan - Venezia. Les liens directs vers l'etape J5 sont
disponibles sans saisie d'identifiant.

La campagne complete represente 17 appels humains confirmes, sans appel automatique. La preuve v6
ne contient aucun motif sensible. Apres la decision de cloture, l'application est arretee
gracieusement; le listener `127.0.0.1:8087`, le JVM applicatif et le JVM worker Playwright sont
absents.

Le detail reproductible est conserve dans
`docs/validation/J3-PLAYWRIGHT-TRANSPORT-TECHNICAL-READINESS-20260827.md`.

## 9. Qualification fournisseur separee

La qualification humaine du 28 aout 2026 est bornee aux actions decrites ci-dessus. Toute campagne
future doit recevoir un nouveau go, figer la date, la ou les familles, le plafond d'appels et la
fenetre horaire. La preparation reste sans reseau ; une phrase exacte, un acquittement et une action
finale sont obligatoires.

Le premier `403`, `429`, challenge, HTML, redirection, timeout, `5xx`, route inattendue ou anomalie
sensible arrete la campagne sans retry. Le rapport final reste minimise et ne publie ni payload,
ni cookie, ni en-tete, ni URI complete. La configuration est ensuite reverrouillee et l'absence de
processus residuel est constatee.

## 10. Hors perimetre

- ajout d'un endpoint ou changement d'un parseur metier ;
- automatisation de l'import JSON local ;
- multi-campagne, polling, scheduler ou appel au demarrage ;
- proxy, VPN de contournement, rotation d'adresse, challenge ou furtivite ;
- fallback HTTP direct, FlareSolverr ou second contexte apres incident ;
- execution sur le VPS ou integration au Betting Project principal ;
- appel fournisseur sans autorisation proprietaire distincte.

## 11. Fichiers probables

- `pom.xml` et `scripts/Verify-Local.ps1` ;
- nouveau source set/runtime Playwright commun et tests associes ;
- superviseur, protocole IPC et lease de campagne sous `src/main/java` ;
- `ProviderScheduledEventsRestTransport.java` et
  `ProviderTournamentScheduledEventsRestTransport.java` retires du runtime ;
- adaptateurs Playwright derriere les deux ports J3 existants ;
- `J3DynamicManualCallService`, `TournamentEventDiscoveryService`, les controles et controleurs ;
- `SofascoreProperties`, `application.yml` et tests de configuration ;
- architecture J3, runbook, README, changelog et rapport de readiness.

Aucune migration n'est attendue. Toute evolution de provenance ou de persistance exige une
migration Flyway append-only separee.

## 12. Portes de statut

```text
WORK_ORDER_STATUS=VALIDATED
IMPLEMENTATION_STARTED=YES
IMPLEMENTATION_STATUS=COMPLETED
PROVIDER_CALL_QUALIFICATION=PASS_BY_OWNER_EXECUTION_2026_08_28
PROVIDER_CALLS_DURING_HUMAN_QUALIFICATION=17
ADDITIONAL_PROVIDER_CALL_AUTHORIZED=NO
ADR_REVIEW=COMPATIBLE_WITH_V1_3
SHARED_RUNTIME_OWNER=WO-SS-20260827-013
LOCAL_READINESS=PASS
HUMAN_PROVIDER_QUALIFICATION=PASS_SCHEDULED_AND_FIVE_TOURNAMENT_DISCOVERY_ACTIONS
SCHEDULED_EVENTS_QUALIFICATION=PASS_12_PAGES_SNAPSHOTS_572_TO_583
TOURNAMENT_SCHEDULED_EVENTS_QUALIFICATION=PASS_5_CALLS_SNAPSHOTS_584_TO_588
CANONICAL_EVENTS_ADDED=6
FINAL_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_APPLICATION_JVM_COUNT=0
FINAL_PLAYWRIGHT_WORKER_JVM_COUNT=0
CLOSURE=AUTHORIZED_BY_OWNER_2026_08_28
```
