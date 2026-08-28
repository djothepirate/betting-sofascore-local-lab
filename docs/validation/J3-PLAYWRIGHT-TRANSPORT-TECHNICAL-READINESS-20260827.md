# Readiness technique - transport fournisseur manuel J3 Playwright

## 1. Statut

```text
REPORT_DATE=2026-08-28
WORK_ORDER=WO-SS-20260827-013
WORK_ORDER_STATUS=IN_PROGRESS
IMPLEMENTATION_STATUS=COMPLETED_AWAITING_OWNER_VALIDATION
ADR_REVIEW=COMPATIBLE_WITH_ADR_SS_001_V1_3
LOCAL_READINESS=PASS
HUMAN_PROVIDER_QUALIFICATION=NOT_RUN_NOT_AUTHORIZED
PROVIDER_CALL_AUTHORIZED=NO
PROVIDER_CALLS_DURING_IMPLEMENTATION=0
PROVIDER_CALLS_DURING_AUTOMATED_TESTS=0
```

Ce rapport consolide la readiness locale executee sur le diff d'implementation. Il ne qualifie ni
le fournisseur, ni une campagne reelle SofaScore, ni la cloture du Work Order.

## 2. Portee a verifier

- profils Maven `provider-playwright-runtime` et
  `provider-playwright-local-qualification` absents des tests standards ;
- worker JVM enfant avec Playwright Java `1.62.0` et Chromium associe ;
- superviseur parent lie a `127.0.0.1`, IPC authentifie et payload borne a 5 Mio ;
- allowlist limitee a `SCHEDULED_EVENTS` et `TOURNAMENT_SCHEDULED_EVENTS` ;
- un contexte non persistant neuf par campagne, sans profil ou etat reutilise ;
- lease exclusive de campagne et delai minimal partage de trois secondes ;
- arret cible par identite de processus et zero nettoyage par nom ;
- persistance raw-first, y compris pour HTTP `404` classe `ENDPOINT_UNAVAILABLE` ;
- zero retry, redirection, fallback HTTP direct ou FlareSolverr ;
- imports JSON locaux J3 et tournoi entierement sans Playwright.

## 3. Matrice de preuve

| Porte | Preuve attendue | Etat |
|---|---|---|
| build standard | aucune dependance ou source Playwright chargee, aucun worker cree | `PASS` |
| profil runtime compile | jar worker executable classe, sans demarrage automatique | `PASS` |
| configuration bloquee | `SOFASCORE_PLAYWRIGHT_ENABLED=false` par defaut | `PASS` |
| claim invalide ou cache hit | aucun worker, contexte ou GET | `PASS` |
| import JSON local | aucun Playwright, cache fournisseur ou transport | `PASS` |
| allowlist | seules les deux familles J3 sont acceptables | `PASS` |
| pagination loopback | pages ordonnees dans une campagne et un contexte | `PASS` |
| fidelite | statut, `Content-Type` et octets identiques au serveur loopback | `PASS` |
| HTTP 404 | brut conserve, `ENDPOINT_UNAVAILABLE`, zero parse/retry/page suivante | `PASS` |
| redirection/route secondaire | cible jamais suivie, incident terminal | `PASS` |
| timeout/corps trop grand/canari sensible | arret terminal et nettoyage | `PASS` |
| arret en vol | acquittement <= 500 ms, annulation <= 2 s, nettoyage <= 5 s | `PASS` |
| campagne suivante | nouveau worker et contexte, aucun etat reutilise | `PASS` |
| hygiene | aucun payload, cookie, token, header ou URI complete dans les logs | `PASS` |
| frontiere reseau | serveur de qualification lie uniquement a `127.0.0.1` | `PASS` |

## 4. Commandes finales

Les commandes ci-dessous doivent etre executees dans cet ordre sur le diff final. Les trois
premieres restent sans navigateur et sans acces fournisseur.

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
pwsh -NoProfile -File .\scripts\Install-J3PlaywrightRuntime.ps1
pwsh -NoProfile -File .\scripts\Invoke-J3PlaywrightLoopbackQualification.ps1
git diff --check
```

Resultats observes le 28 aout 2026 :

```text
STANDARD_SUITE_RESULT=PASS
STANDARD_TESTS_RUN=687
STANDARD_TESTS_SKIPPED=2
INTEGRATION_SUITE_RESULT=PASS
INTEGRATION_TESTS_RUN=52
INTEGRATION_TESTS_SKIPPED=0
VERIFY_LOCAL_RESULT=PASS
PLAYWRIGHT_RUNTIME_INSTALL_RESULT=PASS
WORKER_PROTOCOL_TESTS_RUN=8
WORKER_SECURITY_TESTS_RUN=1
J3_PLAYWRIGHT_LOOPBACK_QUALIFICATION=PASS
LOOPBACK_TESTS_RUN=11
LOOPBACK_ORIGIN=127.0.0.1_EPHEMERAL_ONLY
PROVIDER_ACCESS_PERFORMED=NO
SENSITIVE_DATA_IN_TEST_REPORTS=NO
PROFILE_CONTAMINATION_GATE=PASS
STANDARD_JAR_WORKER_MAIN_ENTRIES=0
STANDARD_JAR_PLAYWRIGHT_LIBRARIES=0
STANDARD_CLASSES_WORKER_MAIN=ABSENT
STANDARD_TEST_CLASSES_QUALIFICATION_IT=ABSENT
POWERSHELL_AST_RESULT=PASS
DIFF_CHECK=PASS
SECRET_AND_PAYLOAD_REVIEW=PASS
SERVER_ADDRESS_127_0_0_1=PASS
DEFAULT_PLAYWRIGHT_INERT=PASS
```

## 5. Portes qui restent humaines

Une readiness locale `PASS` autorisera au plus la proposition de validation technique du Work
Order. Elle ne vaut pas :

- autorisation d'appeler SofaScore ;
- qualification de statuts ou d'octets fournisseur reels ;
- permission de retry apres incident ;
- ajout d'un endpoint J4/J5 ;
- cloture ou deplacement du Work Order vers `completed`.

Ces decisions restent explicitement au proprietaire.
