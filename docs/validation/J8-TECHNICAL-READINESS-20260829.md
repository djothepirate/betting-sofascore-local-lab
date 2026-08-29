# Readiness technique J8 — 2026-08-29

## 1. Statut au dépôt du lot

```text
J8_WORK_ORDER_STATUS=READY_FOR_HUMAN_QUALIFICATION
J8_TECHNICAL_READINESS=PASS
J8_STANDARD_SUITE=PASS_912_TESTS_0_FAILURES_0_ERRORS_4_SKIPPED
J8_INTEGRATION_SUITE=PASS_66_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
J8_VERIFY_LOCAL=PASS
J8_DIFF_CHECK=PASS
J8_COMPOSE_CONFIG=PASS_QUIET
J8_MARKDOWN_REPRODUCIBILITY=PASS_BYTE_IDENTICAL
J8_VISUAL_DESKTOP=PASS
J8_VISUAL_NARROW=PASS
J8_HUMAN_QUALIFICATION=NOT_RUN
J8_PROVIDER_CAMPAIGN=NOT_AUTHORIZED
J8_PROVIDER_CAMPAIGN_RESULT=NOT_RUN
J8_FINAL_BENCHMARK_REPORT=NOT_CREATED
J8_WORK_ORDER_VALIDATED=NO
```

Ce document est la fiche de readiness du lot J8, pas une preuve finale de campagne. Les portes
techniques ont été rejouées sur le diff final le 2026-08-29 UTC, après le test réel de l'exporteur
et son correctif de lancement Maven. Aucune campagne fournisseur ni qualification humaine n'est
autorisée ou présumée par ce document.

## 2. Environnement cible

| Élément | Valeur attendue | Valeur observée |
|---|---|---|
| Branche | `codex/j8-benchmark` | `codex/j8-benchmark` |
| Base J7 validée | `67268d805a4ba8c7d4706be7c18f6ff78d3ec1fd` | merge-base `67268d805a4ba8c7d4706be7c18f6ff78d3ec1fd` |
| Java | 25 LTS | 25.0.4, règle Maven Enforcer passée |
| Spring Boot | 4.1.0 | 4.1.0 |
| Maven | wrapper du dépôt | `mvnw.cmd`, PASS |
| Docker | Docker Desktop | Docker Desktop 29.7.2, PASS |
| PostgreSQL Testcontainers | image versionnée par le dépôt | `postgres:18.4-alpine`, PASS |
| Schéma courant | Flyway V27 | création V1→V27, upgrade V26→V27 et base locale V27, PASS |
| Liaison | `127.0.0.1:8087` | démarrage local sur `127.0.0.1:8087`, puis arrêt et port libéré |
| Appels fournisseur pendant les tests et la QA technique | 0 | 0, confirmation `SOFASCORE_NETWORK_CALLS_EXECUTED=NO` |
| Démarrages du worker Playwright du dépôt | 0 | 0 ; la QA visuelle utilise exclusivement le navigateur intégré Codex |

## 3. Contrat technique livré à qualifier

Le lot J8 doit fournir :

- une migration append-only `V27__j8_benchmark_evidence.sql` ;
- les tables `j8_benchmark_campaign`, `j8_benchmark_unit`,
  `j8_provider_call_attempt`, `j8_benchmark_unit_result` et
  `j8_benchmark_campaign_result` ;
- le modèle commun `J8BenchmarkReport`, la fenêtre `J8BenchmarkWindow` et les états
  `J8MeasurementState` ;
- les niveaux `FULL_ATTEMPT_LEDGER`, `RESPONSE_ONLY` et `LEGACY_BASELINE` sans mélange de
  dénominateurs ;
- les métriques de complétude, latence, stabilité, erreurs et corrections J6 ;
- les formules `discoveryOverhead`, `marginalCallsPerExploitableDossier` et
  `effectiveCallsPerExploitableDossier` ;
- `GET /benchmark` en HTML seulement ;
- un Markdown déterministe produit par `scripts/Export-J8Benchmark.ps1` depuis le même rapport ;
- zéro voie d'appel, polling, scheduler, retry ou démarrage Playwright ajoutée par J8.

Ces éléments ont été contrôlés par tests, audit statique, exécution locale et rendu réel. Ce `PASS`
reste une readiness technique : il ne transforme pas les métriques de campagne en preuve acquise.

## 4. Commandes de preuve à exécuter sur le diff final

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
.\scripts\Verify-Local.ps1 -WithIntegrationTests
docker compose --env-file .env config --quiet
git diff --check
git status --short
```

La variante Compose `--quiet` valide exactement le modèle sans imprimer les valeurs locales issues
de `.env`. Le rendu complet n'a volontairement pas été affiché ni consigné.

| Commande | Code de sortie | Tests | Échecs | Erreurs | Appels fournisseur | Résultat |
|---|---:|---:|---:|---:|---:|---|
| `mvnw.cmd clean verify` | 0 | 912, 4 ignorés prévus | 0 | 0 | 0 | `PASS` |
| `mvnw.cmd -Pintegration-tests verify` | 0 | 912 standards + 66 intégration | 0 | 0 | 0 | `PASS` |
| `Verify-Local.ps1 -WithIntegrationTests` | 0 | 912 standards + 66 intégration | 0 | 0 | 0 | `PASS` |
| `docker compose --env-file .env config --quiet` | 0 | n/a | n/a | n/a | n/a | `PASS` |
| `git diff --check` | 0 | n/a | n/a | n/a | n/a | `PASS` |

## 5. Matrice de preuve attendue

| Domaine | Contrôle exigé | Preuve attendue | Résultat actuel |
|---|---|---|---|
| V27 | création fraîche V1→V27 et upgrade V26→V27 | intégration PostgreSQL | `PASS` |
| append-only | refus de modification/suppression des cinq tables | contraintes/triggers + intégration | `PASS` |
| ledger | issue sans snapshot conservée et comptée une fois | service + PostgreSQL | `PASS` |
| niveaux | ledger, réponses, baseline et mélange | agrégateur | `PASS` |
| exclusions | imports J3 scheduled/tournoi et J5, cache, fixtures et baseline valent zéro tentative | agrégateur | `PASS` |
| fenêtre | absente, paire UTC, bornes invalides, `[from,to)` | domaine + MVC | `PASS` |
| `asOf` | instant unique, aucune preuve postérieure | agrégateur + export | `PASS` |
| hash | ordre canonique et reproductibilité | tests déterministes | `PASS` |
| latence | vide, nearest-rank P50/P95, ordre monotone | tests unitaires | `PASS` |
| complétude | catégories distinctes et ventilations | agrégateur | `PASS` |
| dossier | direct, cinq emplacements, exclusions, strict | agrégateur | `PASS` |
| coût | trois formules, zéro dénominateur | agrégateur + rendu | `PASS` |
| corrections | classifications J6, série directe et preuve d'état terminal | agrégateur + PostgreSQL | `PASS` |
| HTML | 200, vide, partiel, échappement et headers | MVC | `PASS` |
| erreurs | 400/405/406/422/503 et `;jsessionid` | MVC + sécurité | `PASS` |
| Markdown | même rapport, ordre stable, LF/UTF-8, aucun brut | renderer + deux exports réels | `PASS` |
| exporteur | non-Web, gates false, classe principale substituable, chemin construit, SHA affiché | tests + exécution réelle | `PASS` |
| réseau | aucun transport, cache, coordinateur ou worker Playwright | mocks stricts + Verify-Local | `PASS` |
| visuel desktop | hiérarchie, lisibilité, message sans appel et absence de collecte | navigateur local 1280×720 | `PASS` |
| visuel étroit | reflow, défilement borné des tableaux, aucun débordement de page | navigateur local 390×844 | `PASS` |

## 6. Formules à vérifier

```text
discoveryOverhead =
  count(tentatives J3) + count(tentatives de découverte tournoi)

marginalCallsPerExploitableDossier =
  (count(tentatives J4 phase 2) + count(tentatives J5))
  / count(dossiers directs exploitables)

effectiveCallsPerExploitableDossier =
  count(toutes les tentatives directes de la fenêtre)
  / count(dossiers directs exploitables)
```

`discoveryOverhead` est absolu. Le dossier direct exploitable est un `provider_event_id` distinct
ciblé par J4 ou J5 `GUARDED_PROVIDER`, avec à `asOf` un état, un détail et les trois familles
J5 directs. Les familles acceptées sont `COMPLETE`, `PARTIAL` ou `EMPTY_VALID`; aucune absence,
`UNAVAILABLE`, incompatibilité, import-only ou fixture n'est acceptée. Le dossier strict a les
trois familles à `COMPLETE`. Un dénominateur nul rend les deux ratios `NOT_MEASURED`.

## 7. Garde-fous à vérifier

```text
SERVER_ADDRESS=127.0.0.1
SOFASCORE_ENABLED_DEFAULT=false
CONNECTOR_GATE=BLOCKING
AUTOMATIC_PROVIDER_CALLS=0
AUTOMATIC_POLLING=NO
PLAYWRIGHT_AUTOSTART=NO
NEW_PROVIDER_ENDPOINT=NO
RAW_PROVIDER_PAYLOAD_IN_REPORT=NO
EXTERNAL_TRANSFER=NO
```

La revue doit aussi confirmer : aucune modification de l'ADR ou des PDF de référence, aucune
réécriture de V1 à V26, aucun secret dans le diff, aucun rapport runtime versionné et aucune
dépendance du Betting Project principal.

Ces points ont été revus sur les 90 fichiers du lot : zéro modification V1–V26, ADR ou PDF, zéro
motif de secret détecté, `.env` ignoré, exports runtime ignorés et aucun artefact navigateur suivi.
L'audit statique exhaustif et ses deux sous-audits indépendants SQL/instrumentation concluent à
zéro écart P0/P1/P2.

## 8. Export réel et contrôle visuel

L'exporteur a été exécuté deux fois avec les mêmes bornes :

```text
FROM=2026-08-01T00:00:00Z
TO=2026-08-29T23:37:00Z
AS_OF=2026-08-29T23:37:00Z
EXPORT_STATE=PARTIAL
EXPORT_EVIDENCE_SCOPE=RESPONSE_ONLY
EXPORT_BYTES=28624
EXPORT_SHA256=baa7fa8d05d99389463042631485a38157bdd0ca8f9809e8586e317aab33fa08
EXPORT_POPULATION_HASH=b86a49b645ca44d04f79bdb847f8d0770c393af42426ad0a8c99b3300db64aec
EXPORT_BYTE_IDENTICAL=YES
EXPORT_NETWORK_CALLS=0
```

Les deux fichiers restent sous `exports/j8/`, sont ignorés par Git et ne constituent pas le rapport
gelé de qualification. Le premier essai d'intégration a par ailleurs détecté que la configuration
Maven fixe neutralisait l'option de classe principale : le POM a été rendu explicitement
substituable, un test de non-régression a été ajouté, puis les deux exports et toutes les portes ont
été rejoués avec succès.

La page réelle `GET /benchmark` a été contrôlée avec l'application liée à `127.0.0.1:8087`, tous
les drapeaux fournisseur forcés à `false`, puis l'application a été arrêtée :

```text
DESKTOP_VIEWPORT=1280x720
DESKTOP_PAGE_HORIZONTAL_OVERFLOW=NO
NARROW_VIEWPORT=390x844
NARROW_PAGE_HORIZONTAL_OVERFLOW=NO
TABLE_OVERFLOW=BOUNDED_HORIZONTAL_SCROLL
HTML_SCRIPT_ELEMENTS=0
HTML_MUTATION_CONTROLS=0
HTML_FORMS=GET_ONLY
DASHBOARD_STATIC_LINK=PASS
REQUIRED_NO_PROVIDER_MESSAGE=PASS
PORT_8087_AFTER_QA=FREE
```

## 9. Qualification humaine et campagne

La recette est décrite dans `docs/runbooks/J8-BENCHMARK.md`. Son état reste :

```text
J8_HUMAN_QUALIFICATION=NOT_RUN
J8_HTML_TECHNICAL_QA=PASS
J8_MARKDOWN_REPRODUCIBILITY=PASS_BYTE_IDENTICAL
J8_TECHNICAL_QA_PROVIDER_CALLS=0
J8_PROVIDER_CALLS_DURING_HUMAN_QUALIFICATION=NOT_RUN
```

La campagne réelle n'est pas un prérequis implicite de la recette locale et ne doit pas être
lancée pour remplir cette fiche. Elle exige un go propriétaire séparé avant la première requête,
avec manifeste, fenêtre, unités, endpoints, plafond d'appels et arrêts figés.

```text
J8_PROVIDER_CAMPAIGN_GO=NOT_GRANTED
J8_PROVIDER_CAMPAIGN=NOT_AUTHORIZED
J8_PROVIDER_CAMPAIGN_RESULT=NOT_RUN
J8_FINAL_CAMPAIGN_METRICS=NOT_RUN
J8_FINAL_BENCHMARK_REPORT=NOT_CREATED
```

## 10. Conclusion de readiness

```text
COMPLETENESS_METRICS=IMPLEMENTED_AND_TECHNICALLY_VERIFIED
LATENCY_METRICS=IMPLEMENTED_AND_TECHNICALLY_VERIFIED
SCHEMA_STABILITY=IMPLEMENTED_AND_TECHNICALLY_VERIFIED
ERROR_RATE=IMPLEMENTED_AND_TECHNICALLY_VERIFIED
PROVIDER_CALL_COST=IMPLEMENTED_AND_TECHNICALLY_VERIFIED
AUTOMATIC_POLLING=NO
J8_TECHNICAL_READINESS=PASS
J8_NEXT_STATE=READY_FOR_HUMAN_QUALIFICATION
J8_CAN_BE_CLOSED=NO
```

Cette conclusion certifie la compilation, la migration, l'interface, l'export et les garde-fous
locaux ; elle ne certifie pas une campagne fournisseur ni la comparaison humaine. Le Work Order
reste actif à
`READY_FOR_HUMAN_QUALIFICATION` et ne peut devenir `VALIDATED` qu'après la campagne réelle bornée
autorisée séparément, la qualification humaine, le rapport gelé et une décision propriétaire
explicite.

Le bloc de clôture définitif (`COMPLETENESS_METRICS=AVAILABLE`, `LATENCY_METRICS=AVAILABLE`,
`SCHEMA_STABILITY=MEASURED`, `ERROR_RATE=MEASURED`, `PROVIDER_CALL_COST=MEASURED`,
`AUTOMATIC_POLLING=NO`) n'est pas acquis par la readiness technique et ne sera consigné comme
résultat qu'après campagne bornée, revue humaine et rapport gelé.
