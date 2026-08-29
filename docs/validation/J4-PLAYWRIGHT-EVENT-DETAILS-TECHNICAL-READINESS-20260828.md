# Readiness technique - transport J4 `EVENT_DETAILS` Playwright

## 1. Statut

```text
WORK_ORDER=WO-SS-20260827-014
WORK_ORDER_STATUS=VALIDATED
ADR_SS_001_VERSION=1.4
IMPLEMENTATION_STATUS=COMPLETED
TECHNICAL_READINESS=PASS
LOCAL_READINESS=PASS
TECHNICAL_QUALIFICATION_PROVIDER_ACCESS=NO
HUMAN_PROVIDER_QUALIFICATION=PASS_BY_OWNER_EXECUTION_2026_08_28
J3_PLAYWRIGHT_REGRESSION_CHECK=PASS_NO_REGRESSION_IDENTIFIED
CLOSURE=AUTHORIZED_BY_OWNER_2026_08_28
```

Cette readiness technique a ete etablie sur le diff final de l'implementation. La qualification a
utilise uniquement un serveur ephemere lie a `127.0.0.1` et n'a jamais contacte SofaScore. Elle ne
remplace ni un essai fonctionnel humain contre le fournisseur, ni son autorisation distincte.

## 2. Prerequis et baseline

Les decisions `404` et cache de phase 2 sont approuvees dans l'ADR-SS-001 v1.4. Le socle commun
WO-013 est `VALIDATED` et le checkout courant etait propre au point de baseline sur la branche
`codex/j4-playwright-event-details`.

La baseline standard executee avant la premiere modification d'implementation est :

```text
BASELINE_COMMAND=mvnw.cmd clean verify
BASELINE_STANDARD_SUITE=PASS
BASELINE_STANDARD_TESTS=691
BASELINE_STANDARD_FAILURES=0
BASELINE_STANDARD_ERRORS=0
BASELINE_STANDARD_SKIPPED=2
BASELINE_PROVIDER_CALLS=0
```

Cette baseline ne vaut ni implementation J4, ni readiness Playwright, ni autorisation fournisseur.

## 3. Portes attendues

| Porte | Preuve attendue | Etat |
|---|---|---|
| build standard | aucune source ou dependance Playwright chargee, aucun worker | `PASS` |
| configuration bloquee | Playwright et les deux opt-ins J4 inactifs par defaut | `PASS` |
| claim invalide | zero contexte et zero GET | `PASS` |
| phase 1, deux cache hits | deux resultats locaux, zero Playwright | `PASS` |
| phase 1, cache miss | seules les cibles manquantes sont transportees, ordre conserve | `PASS` |
| phase 1 loopback | deux GET exacts au plus dans un contexte neuf | `PASS` |
| phase 2 loopback | identite canonique resolue serveur, un GET, sans lecture cache | `PASS` |
| campagne suivante | worker et contexte neufs, aucun etat reutilise | `PASS` |
| `404` phase 1 | brut indisponible, aucun parse/retry, cible fixe suivante | `PASS` |
| `404` phase 2 | brut indisponible, resultat `COMPLETED_UNAVAILABLE` | `PASS` |
| autre incident HTTP/contenu | terminal, aucune cible suivante ou normalisation | `PASS` |
| timeout, taille, canari | fermeture sure et aucune reprise | `PASS` |
| arret en vol | 500 ms / 2 s / 5 s et zero processus residuel | `PASS` |
| lease partagee | aucune intercalation J3/J5 dans la campagne phase 1 | `PASS` |
| import JSON J4 | zero Playwright, cache fournisseur et transport | `PASS` |
| hygiene | aucun payload, cookie, token, header ou URI complete dans les sorties | `PASS` |
| frontiere reseau | serveur de qualification uniquement sur `127.0.0.1` | `PASS` |

## 4. Commandes finales requises

Les commandes ont ete executees sur le diff final :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
pwsh -NoProfile -File .\scripts\Invoke-J4PlaywrightLoopbackQualification.ps1
git diff --check
```

Les trois premieres commandes restent sans navigateur et sans appel fournisseur. Le script J4
active explicitement le vrai worker et Chromium uniquement contre loopback. L'installation du
runtime commun, si elle est deja qualifiee et complete, n'est pas repetee implicitement.

## 5. Preuve terminale technique

La preuve terminale consolidee est :

```text
STANDARD_SUITE_RESULT=PASS
STANDARD_TESTS_RUN=728
STANDARD_TESTS_FAILURES=0
STANDARD_TESTS_ERRORS=0
STANDARD_TESTS_SKIPPED=2
INTEGRATION_SUITE_RESULT=PASS
INTEGRATION_TESTS_RUN=52
INTEGRATION_TESTS_FAILURES=0
INTEGRATION_TESTS_ERRORS=0
INTEGRATION_TESTS_SKIPPED=0
VERIFY_LOCAL_RESULT=PASS
VERIFY_LOCAL_INTEGRATION_TESTS_EXECUTED=TRUE
J4_PLAYWRIGHT_LOOPBACK_QUALIFICATION=PASS
WORKER_PROTOCOL_AND_SECURITY_TESTS=PASS
WORKER_PROTOCOL_AND_SECURITY_TEST_COUNT=10
J4_EVENT_DETAILS_ROUTE_TESTS=PASS
J4_EVENT_DETAILS_ROUTE_TEST_COUNT=12
LOOPBACK_TESTS_RUN=22
LOOPBACK_ORIGIN=127.0.0.1_EPHEMERAL_ONLY
SENSITIVE_DATA_IN_TEST_REPORTS=NO
PROFILE_CONTAMINATION_GATE=PASS
POWERSHELL_AST=PASS_12_SCRIPTS
DIFF_CHECK=PASS
SECRET_AND_PAYLOAD_REVIEW=PASS
SERVER_ADDRESS_127_0_0_1=PASS
DEFAULT_PLAYWRIGHT_INERT=PASS
TECHNICAL_QUALIFICATION_PROVIDER_ACCESS_PERFORMED=NO
```

## 6. Qualification fonctionnelle et validation proprietaire

Le `2026-08-28`, apres la readiness technique, le proprietaire a execute et declare concluant le
test fonctionnel J4 depuis l'interface locale. Les preuves minimisees transmises confirment :

- la selection d'une identite canonique dont le `providerEventId` est resolu cote serveur ;
- la preparation et la confirmation exactes avant l'action finale ;
- un rafraichissement J4 phase 2 `COMPLETED`, un appel fournisseur et un snapshot brut conserve ;
- la projection de la nouvelle observation dans les resultats normalises ;
- le reverrouillage terminal `COMPLETED_LOCKED` apres la campagne.

Le proprietaire a egalement rejoue J3 sous Playwright. La collecte `SCHEDULED_EVENTS` a termine ses
12 pages jusqu'a `hasNextPage=false`, puis la decouverte tournoi a conserve son controle de compte,
son appel unique et la creation canonique attendue. Il n'a identifie aucune regression J3.

```text
OWNER_FUNCTIONAL_EVIDENCE_REPORTED_AT=2026-08-28
J4_FUNCTIONAL_QUALIFICATION=PASS
J4_PHASE2_TERMINAL_STATE=COMPLETED_LOCKED
J4_PHASE2_PROVIDER_CALL_COUNT=1
J4_PHASE2_RESULT=COMPLETED
J3_SCHEDULED_EVENTS_REGRESSION=PASS_12_PAGES_TO_HAS_NEXT_PAGE_FALSE
J3_TOURNAMENT_DISCOVERY_REGRESSION=PASS
J3_REGRESSION_IDENTIFIED=NO
HUMAN_PROVIDER_QUALIFICATION=PASS_BY_OWNER_EXECUTION_2026_08_28
WORK_ORDER_STATUS=VALIDATED
CLOSURE=AUTHORIZED_BY_OWNER_2026_08_28
```

Cette validation ne change ni les opt-ins desactives par defaut, ni l'exigence d'une action humaine
explicite par campagne, ni l'interdiction du retry, du polling, du scheduler et des contextes
persistants.
