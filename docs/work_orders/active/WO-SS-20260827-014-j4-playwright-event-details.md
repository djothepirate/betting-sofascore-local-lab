# WO-SS-20260827-014 - Migration Playwright du detail fournisseur J4

- **Statut :** `OPEN_AWAITING_PREREQUISITES`
- **Jalon :** `J4`
- **Date d'ouverture :** `2026-08-27`
- **Decision d'ouverture :** proprietaire du Betting Project
- **Branche cible :** `codex/j4-playwright-event-details`
- **Worktree dedie :** `REQUIRED_NOT_CREATED`
- **ADR applicable :** `ADR-SS-001 v1.3`, avec deux decisions J4 encore requises
- **Qualification de reference :** `WO-SS-20260823-011` (`VALIDATED`)
- **Contrat metier J4 de reference :** `WO-SS-20260815-004` (`VALIDATED`)
- **Socle Playwright requis :** `WO-SS-20260827-013` (`VALIDATED`)
- **Implementation par cette ouverture :** `NOT_STARTED`
- **Developpement et qualification loopback :** `AWAITING_J4_ADR_DECISIONS_AND_WORKTREE`
- **Appel fournisseur :** `NOT_AUTHORIZED`
- **Polling, scheduler, retry ou fallback :** `NOT_AUTHORIZED`
- **Production, VPS ou dependance critique :** `NOT_AUTHORIZED`

## 1. Objectif

Remplacer le transport fournisseur manuel `EVENT_DETAILS` utilise en J4 par le runtime Playwright
commun livre par `WO-SS-20260827-013`, sans modifier les imports JSON hors ligne, le parseur metier,
la persistance, les identites canoniques ni l'allowlist existante.

Le Work Order couvre les deux parcours J4 deja gouvernes :

- la phase 1, qui qualifie au plus deux identifiants fixes dans leur ordre contractuel ;
- la phase 2, qui collecte explicitement le detail d'une seule identite canonique deja connue.

Il ne cree aucun runtime navigateur J4 autonome. Le worker, le superviseur, l'IPC prive, la lease de
campagne et le nettoyage sont obligatoirement ceux de `WO-SS-20260827-013`.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Prerequis bloquants

L'implementation ne commence que lorsque toutes les conditions suivantes sont reunies :

1. `WO-SS-20260815-004` reste le contrat metier J4 immuable de reference ;
2. `WO-SS-20260827-013` est integre et son runtime commun est qualifie en loopback ;
3. l'ADR-SS-001 porte les decisions J4 de la section 5 du present document ;
4. un worktree propre est cree depuis la base approuvee sur `codex/j4-playwright-event-details` ;
5. `mvnw.cmd clean verify` est vert avant la premiere modification d'implementation ;
6. aucun fichier POC FlareSolverr ou source de qualification `src/j5-browser-qualification*` n'est
   copie dans le runtime applicatif ;
7. aucune edition simultanee du meme worktree n'est effectuee depuis Eclipse et par un agent.

L'ouverture de ce Work Order autorise uniquement le cadrage. Elle ne vaut ni implementation,
installation de navigateur, preparation de campagne fournisseur, ni acces a SofaScore.

## 3. Allowlist exacte

```text
Famille : EVENT_DETAILS
Methode : GET
Origine : https://www.sofascore.com
Chemin  : /api/v1/event/{EVENT_ID}
EVENT_ID: entier de 1 a 999999999
```

### 3.1 Phase 1

Les seules cibles autorisees restent les deux identifiants fixes du contrat J4, dans cet ordre :

```text
1. GET https://www.sofascore.com/api/v1/event/16386245
2. GET https://www.sofascore.com/api/v1/event/16421052
```

La campagne execute au plus deux transports, moins les resultats servis par le cache selon la
decision de la section 5. Aucun identifiant fourni par l'interface ne peut remplacer cette liste.

### 3.2 Phase 2

Une confirmation J4 phase 2 autorise au plus un GET pour le `providerEventId` positif deja lie a
l'UUID local affiche et confirme cote serveur. Une identite absente de la selection canonique ne
peut pas etre saisie librement.

Pour les deux phases, tout port explicite, user-info, query, fragment, autre schema, autre hote,
autre methode, redirection ou chemin secondaire est refuse avant l'ouverture du contexte.

## 4. Reutilisation du runtime commun

J4 reutilise sans duplication le contrat de `WO-SS-20260827-013` :

- worker JVM enfant proprietaire de Playwright, Chromium et du contexte ;
- lancement paresseux seulement apres claim operateur valide et cache miss applicable ;
- un `BrowserContext` neuf, non persistant et ferme pour chaque campagne J4 ;
- protocole IPC prive, borne a 5 Mio et ne journalisant aucun payload ;
- lease exclusive de campagne partagee avec J3 et J5 ;
- arret cible par PID et heure de creation, jamais par nom de processus ;
- aucun profil Chrome personnel, cookie injecte, `storageState`, HAR, trace, video, capture ou
  telechargement ;
- aucun proxy, rotation d'adresse, furtivite, challenge, CAPTCHA ou fallback HTTP/FlareSolverr ;
- conservation des octets issus de `Response.body()` sans passage par le DOM, `Response.text()` ou
  reencodage ;
- fermeture du contexte, du navigateur, du worker et de Playwright sur tout chemin terminal.

La phase 1 utilise un seul contexte neuf pour ses deux cibles ordonnees. La phase 2 utilise un autre
contexte neuf pour son unique cible. Aucun etat ne traverse deux campagnes ou deux phases.

## 5. Decisions J4 requises avant implementation

### 5.1 Semantique HTTP 404

L'ADR-SS-001 v1.3 reconnait un `404` comme indisponibilite fournisseur persistable, alors que le
service J4 actuel traite tout non-`2xx` comme terminal et attend un detail parse pour produire son
resultat. Le contrat recommande a faire valider est :

```text
J4_404_CLASSIFICATION=ENDPOINT_UNAVAILABLE
J4_404_PARSE_ATTEMPTED=NO
J4_404_RETRY=NO
J4_PHASE1_404_NEXT_FIXED_TARGET=YES
J4_PHASE2_404_RESULT=COMPLETED_UNAVAILABLE
```

En phase 1, le `404` d'une cible fixe est une observation complete pour cette cible et permet de
passer a l'autre cible independante. En phase 2, il termine la campagne avec une indisponibilite
explicite. Tout `403`, `429`, `5xx`, timeout, redirection, HTML/challenge ou anomalie de contenu
reste terminal au premier incident.

Cette recommandation doit etre inscrite explicitement dans l'ADR ou refusee par une decision
proprietaire avant le debut du code.

### 5.2 Politique de cache de la phase 2

La phase 1 reste `FRESH_PARSED_SNAPSHOT_FIRST`. La phase 2 actuelle est une action manuelle de
rafraichissement qui interroge le fournisseur apres confirmation meme si une observation existe.
Cette exception doit etre rendue explicite :

```text
J4_PHASE1_CACHE_POLICY=FRESH_PARSED_SNAPSHOT_FIRST
J4_PHASE2_CACHE_POLICY=EXPLICIT_MANUAL_REFRESH_NO_CACHE_READ
J4_PHASE2_CACHE_EXCEPTION_SCOPE=ONE_CONFIRMED_EVENT_DETAILS_GET
```

La phase 2 ne peut pas devenir un polling, une reprise automatique ou un rafraichissement en
arriere-plan. Une decision ADR/proprietaire est requise avant implementation.

## 6. Coordination, arret et verrouillage

Le controle J4 doit obtenir la lease de campagne commune avant de creer le contexte. Une campagne
J3 ou J5 ne peut pas s'intercaler entre les deux cibles de phase 1. La lease est liberee dans un
`finally` apres fermeture verifiee de l'arbre Playwright.

Les actions d'arret J4 existantes signalent d'abord le superviseur de la campagne en vol, puis
reverrouillent l'intention metier. Elles ne doivent pas se limiter a changer un statut applicatif.

```text
STOP_ACKNOWLEDGEMENT_MAX=500ms
IN_FLIGHT_CANCELLATION_MAX=2s
PROCESS_TREE_CLEANUP_MAX=5s
RESIDUAL_OWNED_PROCESS_COUNT=0
```

Apres un arret, aucun nouvel appel de la campagne n'est autorise et aucun contexte de remplacement
n'est cree.

## 7. Semantique des reponses et persistance

- le statut, le type utile, la taille et le SHA-256 des octets sont controles avant parsing ;
- les octets bruts restent separes de la donnee normalisee ;
- toute normalisation conserve snapshot, hash, parseur et heure de reception ;
- un `2xx` utilise strictement le parseur `EVENT_DETAILS` existant ;
- le `404` suit uniquement la decision approuvee en section 5.1 ;
- tout autre non-`2xx`, corps trop grand, contenu non JSON, route inattendue ou contenu sensible
  verrouille la campagne sans retry ;
- aucun fallback vers `RestClient`, FlareSolverr, un import local ou un second contexte ;
- les imports locaux J4 restent sans navigateur, sans cache fournisseur et sans transport.

## 8. Lots d'implementation

1. **Decision ADR J4** : trancher et documenter le `404` et l'exception cache de phase 2.
2. **Integration runtime** : brancher J4 sur les ports, la lease, le worker et le superviseur de
   `WO-SS-20260827-013` sans les dupliquer.
3. **Transport phase 1** : remplacer `ProviderEventDetailsRestTransport` pour les deux cibles fixes.
4. **Transport phase 2** : utiliser le meme adaptateur avec une nouvelle campagne et l'identite
   canonique confirmee.
5. **Controle et arret** : relier les actions Web et services J4 au superviseur en vol.
6. **Qualification loopback** : verifier les deux phases, les caches, les erreurs et l'arret reel.
7. **Documentation et readiness** : architecture J4, runbook, rapport, README, changelog et WO.
8. **Qualification fournisseur optionnelle** : seulement apres un nouveau go proprietaire exact.

## 9. Matrice de validation minimale

| Scenario | Resultat attendu |
|---|---|
| demarrage normal / profil absent | aucun worker, Chromium ou telechargement |
| profil opt-in mais aucune confirmation | runtime inerte, zero GET |
| claim invalide, expire ou incoherent | zero contexte et zero GET |
| phase 1 avec deux cache hits frais | zero Playwright et deux resultats locaux |
| phase 1 avec un cache miss | une seule cible transportee, ordre contractuel preserve |
| phase 1 nominale loopback | deux GET exacts, un contexte, octets et statuts exacts |
| phase 2 nominale loopback | un GET exact, contexte distinct, identite serveur imposee |
| seconde campagne | nouveau worker/contexte, aucun cookie ou etat reutilise |
| `404` phase 1 | indisponibilite explicite puis cible fixe suivante selon decision ADR |
| `404` phase 2 | campagne complete indisponible selon decision ADR |
| `403`, `429` ou `5xx` | arret terminal, zero retry |
| redirection ou HTML/challenge | cible secondaire jamais atteinte, aucune normalisation |
| timeout, corps > 5 Mio ou canari sensible | fermeture sure et aucune reprise |
| arret pendant une reponse lente | bornes 500 ms / 2 s / 5 s et zero residu |
| tentative J3/J5 concurrente | aucune intercalation dans la campagne J4 |
| import JSON J4 | zero Playwright et zero appel fournisseur |

Commandes requises avant proposition de cloture :

```text
mvnw.cmd clean verify
mvnw.cmd -Pintegration-tests verify
scripts/Verify-Local.ps1 -WithIntegrationTests
<commande loopback opt-in definie apres integration de WO-013>
git diff --check
```

Les tests standards et d'integration PostgreSQL ne lancent jamais Chromium et n'accedent jamais a
SofaScore.

## 10. Qualification fournisseur separee

La readiness loopback ne vaut pas autorisation fournisseur. Un futur go doit figer la phase, les
identifiants exacts, le plafond d'appels et la fenetre horaire. Une phrase exacte, un acquittement et
une action finale restent requis.

Le premier `403`, `429`, challenge, HTML, redirection, timeout, `5xx`, route inattendue ou anomalie
sensible arrete la campagne. Le rapport final est minimise et ne contient ni payload, cookie,
en-tete, URI complete ou donnee de session. Le controle est ensuite reverrouille et l'absence de
processus residuel est constatee.

## 11. Hors perimetre

- ajout d'un endpoint ou changement du parseur `EVENT_DETAILS` ;
- modification du catalogue J3, des familles J5 ou des imports hors ligne ;
- campagne J4 multi-identite libre, polling, scheduler ou appel au demarrage ;
- proxy, VPN de contournement, rotation d'adresse, challenge ou furtivite ;
- fallback HTTP direct, FlareSolverr ou second contexte apres incident ;
- execution sur le VPS ou integration au Betting Project principal ;
- appel fournisseur sans autorisation proprietaire distincte.

## 12. Fichiers probables

- adaptateur Playwright J4 derriere le port `EVENT_DETAILS` existant ;
- `ProviderEventDetailsRestTransport.java`, retire du runtime apres couverture equivalente ;
- services et controles J4 phase 1/phase 2 ;
- raccordement au superviseur, a la lease et a l'IPC de `WO-SS-20260827-013` ;
- configuration opt-in, tests unitaires, integration et loopback ;
- ADR-SS-001, architecture J4, runbook, README, changelog et rapport de readiness.

Aucune migration n'est attendue. Toute evolution de provenance ou de persistance exige une
migration Flyway append-only separee.

## 13. Portes de statut

```text
WORK_ORDER_STATUS=OPEN_AWAITING_PREREQUISITES
IMPLEMENTATION_STARTED=NO
PROVIDER_CALL_AUTHORIZED=NO
SHARED_RUNTIME_DEPENDENCY=WO-SS-20260827-013
SHARED_RUNTIME_DEPENDENCY_STATUS=VALIDATED
J4_404_POLICY=PENDING_ADR_REVIEW
J4_PHASE2_CACHE_POLICY=PENDING_SCOPED_ADR_DECISION
LOCAL_READINESS=NOT_RUN
HUMAN_PROVIDER_QUALIFICATION=NOT_RUN_NOT_AUTHORIZED
CLOSURE=NOT_AUTHORIZED
```
