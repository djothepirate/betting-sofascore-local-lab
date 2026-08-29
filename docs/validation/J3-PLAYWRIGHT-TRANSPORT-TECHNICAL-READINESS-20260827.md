# Readiness technique - transport fournisseur manuel J3 Playwright

## 1. Statut

```text
REPORT_DATE=2026-08-28
WORK_ORDER=WO-SS-20260827-013
WORK_ORDER_STATUS=VALIDATED
IMPLEMENTATION_STATUS=COMPLETED
ADR_REVIEW=COMPATIBLE_WITH_ADR_SS_001_V1_3
LOCAL_READINESS=PASS
HUMAN_PROVIDER_QUALIFICATION=PASS_SCHEDULED_AND_FIVE_TOURNAMENT_DISCOVERY_ACTIONS
PROVIDER_CALL_QUALIFICATION=PASS_BY_OWNER_EXECUTION_2026_08_28
PROVIDER_CALLS_DURING_HUMAN_QUALIFICATION=17
ADDITIONAL_PROVIDER_CALL_AUTHORIZED=NO
PROVIDER_CALLS_DURING_IMPLEMENTATION=0
PROVIDER_CALLS_DURING_AUTOMATED_TESTS=0
WORK_ORDER_CLOSURE=AUTHORIZED_BY_OWNER_2026_08_28
```

Ce rapport consolide la readiness locale executee sur le diff d'implementation et la qualification
humaine bornee des deux familles J3. Il n'autorise aucune nouvelle campagne SofaScore.

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
STANDARD_TESTS_RUN=691
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

### 4.1 Revalidation apres le premier essai operateur

Le premier essai du 28 aout a atteint l'ouverture du worker mais pas son frame `READY`. Les
horodatages de la preuve et du cache ont montre que Playwright installait implicitement Firefox et
WebKit pendant les 30 secondes du timeout ; aucun Chromium et aucun GET SofaScore n'avaient alors
ete lances. La correction impose `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` dans l'environnement enfant,
verifie l'installation Chromium avant le demarrage et rend concluant le nettoyage d'un worker qui
quitte apres une trame terminale authentifiee. La preuve minimisee v6 compte separement la tentative
de resolution et le GET effectivement transmis.

Apres cette correction, la suite standard a passe 691 tests avec 2 ignores et le banc reel Chromium
loopback a passe deux fois de suite ses 11 scenarios, sans acces fournisseur ni donnee sensible dans
les rapports. La readiness locale reste `PASS`.

### 4.2 Qualification humaine fournisseur

Le nouvel essai operateur du 28 aout 2026 est concluant pour `SCHEDULED_EVENTS`. Apres preparation,
confirmation et action finale distincte, les pages 1 a 12 sont demandees au fournisseur puis
conservees dans les snapshots 572 a 583. Chaque page est HTTP `200`, `PARSED` et `INSERTED`; les
pages 1 a 11 annoncent `hasNextPage=true` et la page 12 termine avec `false`. La preuve minimisee v6
se termine en `COMPLETED` avec 12 requetes fournisseur, zero cache hit, zero import JSON local,
aucun echec, retry, polling, scheduler ou donnee de session. L'arret global est reapplique et le
circuit est verrouille avec `MANUAL_COLLECTION_TERMINAL_LOCK`. Un scan de ses 8 643 octets ne
detecte aucun motif d'URL, d'autorisation, de cookie, de jeton ou de mot de passe.

Cinq preparations et confirmations distinctes qualifient ensuite `TOURNAMENT_SCHEDULED_EVENTS` :

| Phase | Phase / route | Snapshot | Attendu / retenu | Rencontres |
|---|---:|---:|---:|---:|
| Ligue 1 - France | `4 / 34` | 584 | `1 / 1` | 1 |
| Premier League - England | `1 / 17` | 585 | `1 / 1` | 1 |
| LaLiga - Spain | `36 / 8` | 586 | `2 / 2` | 2 |
| Bundesliga - Germany | `42 / 35` | 587 | `1 / 1` | 1 |
| Serie A - Italy | `33 / 23` | 588 | `1 / 1` | 1 |

Le catalogue des 12 pages contient 524 tournois actionnables apres exclusion de 648 occurrences.
Les cinq resultats sont HTTP `200`, `COMPLETED`, de source `PROVIDER` et `COUNT_VERIFIED`. Ils
ajoutent six observations canoniques sans deduplication et rendent leurs liens J5 disponibles sans
saisie d'identifiant. La qualification humaine porte donc sur 17 appels confirmes au total.

Apres confirmation proprietaire que ces tests suffisent a la cloture, l'application est arretee
gracieusement. Le listener `127.0.0.1:8087`, le JVM applicatif et le JVM worker Playwright sont
absents. Le Work Order passe a `VALIDATED` et rejoint `docs/work_orders/completed`.

## 5. Limites apres validation

La readiness locale, la qualification humaine et la cloture sont `PASS`. Elles ne valent pas :

- autorisation d'une nouvelle campagne SofaScore ;
- permission de retry apres incident ;
- ajout d'un endpoint J4/J5 ;
- approbation de production, de VPS ou de dependance critique.

Ces decisions restent explicitement au proprietaire et exigent leur Work Order applicable.
