# Qualification — plafond J3 de pagination manuelle à 35 pages

- **Work Order :** [WO-SS-20260912-059](../work_orders/active/WO-SS-20260912-059-j3-pagination-cap-35.md)
- **ADR :** [ADR-SS-006](../../ADR-SS-006-j3-manual-pagination-cap-35.md)
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260912-059`
- **Base examinée :** `4f0413b`
- **Date :** 2026-09-12
- **Statuts conservés :** `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`

## Objet qualifié

La borne locale de pagination manuelle J3 `SCHEDULED_EVENTS` passe de 25 à **35** pages.

- Une séquence peut se terminer normalement à la page 27 si `hasNextPage=false`.
- La page 35 est admise.
- Une page 35 avec `hasNextPage=true` produit `PAGINATION_LIMIT_REACHED` sans départ, import, claim ou persistance de la page 36.
- Une page 36 est rejetée par le domaine, le contrôleur, l'import local et le protocole worker.
- Les nouvelles campagnes J8 ont `maximum_units=35`. Les campagnes J8 historiques à 25 restent lisibles et restent bornées à 25.
- V53 est append-only : elle n'écrit, ne supprime et ne promeut aucune preuve ou campagne existante.

## Résultats de qualification

| Vérification | Commande | Résultat |
|---|---|---|
| parcours ciblés J3, J8 et MVC | `mvnw.cmd -q -Dtest=ScheduledEventsProviderPageRequestTest,PlaywrightProviderRequestTest,J3DynamicManualCallServiceTest,J3LocalJsonImportServiceTest,J3ManualCallControlServiceTest,J8BenchmarkEvidenceTest,J8BenchmarkAuditServiceTest,ManualCallControlViewTest,ManualCallControllerTest,DashboardControllerTest,J6NativeBinaryPipelineQualificationTest test` | **Réussi** — 121 tests, 0 échec, 0 erreur, 1 ignoré |
| protocole worker Playwright isolé | `mvnw.cmd -q -Pprovider-playwright-runtime -Dtest=ProviderPlaywrightWorkerProtocolTest test` | **Réussi** — 15 tests, 0 échec, 0 erreur, 0 ignoré |
| migration V52 vers V53 | `mvnw.cmd -q -Pintegration-tests -Dit.test=FlywayMigrationIT failsafe:integration-test failsafe:verify` | **Réussi** — 74 tests, 0 échec, 0 erreur, 0 ignoré, 42,89 s |
| compilation/assemblage | `mvnw.cmd -q -DskipTests package` | **Réussi** |
| qualité du diff | `git diff --check` | **Réussi** — aucun défaut de whitespace ; avertissements CRLF attendus pour `Backup-Restore-J6.ps1` et `Invoke-J6Retention.ps1` |
| configuration locale | examen de `application.yml` | **Réussi** — `server.address=127.0.0.1` reste effectif |

Les tests J3 démontrent explicitement les cas 27, 35 et 36, l'absence de demande de page suivante au terminal, le parcours cache-first de 35 pages, et l'import atomique local. Les tests MVC vérifient que la page 36 est refusée avant délégation vers le service d'import. Le test Flyway démarre de V52, conserve une campagne historique à 25 avec sa borne, puis accepte une nouvelle campagne à 35 et refuse la page 36.

Aucun test ni aucune commande de cette qualification n'a déclenché un appel vers le fournisseur. La vérification d'intégration emploie PostgreSQL via Testcontainers/Docker local uniquement.

## Suites globales obligatoires exécutées

Les deux commandes prescrites ont été lancées. Elles ne sont pas vertes, car elles rencontrent avant le packaging et avant Failsafe le même échec préexistant, extérieur au périmètre de cette modification :

| Commande | Résultat observé | Cause |
|---|---|---|
| `mvnw.cmd clean verify` | 1304 tests, 1 échec, 0 erreur, 2 ignorés | `GroupedLiveAdmissionPolicyV5EvidenceTest` attend le SHA-256 `184aebda6a0650ff96fc8dde8cac2d375cbf4df6276c398fd414889192eee6fe` alors que le fichier archivé de base porte `89b618fe512292fec950dc09816a5d893596625f6c074cfa40b3876210454a7d`. |
| `mvnw.cmd -Pintegration-tests verify` | 2301 tests, 1 échec, 0 erreur, 5 ignorés | même échec Surefire V5, donc Failsafe ne démarre pas dans cette commande globale. |

Le test V5 concerné et son entrée brute ne sont pas modifiés par ce Work Order par rapport à `4f0413b`. L'écart de hash est donc documenté comme un bloqueur de qualification globale déjà présent, sans modification corrective hors périmètre.

## Contrôles complémentaires

- Les limites de taille existantes restent de 5 Mio par page et 25 Mio par lot.
- Aucune URI libre, origine supplémentaire, secret, cookie, jeton, payload complet, proxy, profil persistant ou mécanisme de relance n'est ajouté.
- La migration V53 est append-only et le déclencheur PostgreSQL existant conserve la borne stockée de chaque campagne historique.
- Les références actives du parcours J3 à 25 sont remplacées par 35. Les seules mentions restantes de `1..25` dans ADR-SS-002 décrivent une décision historique explicitement rendue non prospective par ADR-SS-006 ; d'autres occurrences concernent les lots J5 hors champ.

## Conclusion de qualification

Le comportement demandé est démontré localement : une date comportant 27 pages peut désormais être collectée jusqu'à sa terminaison normale, et la barrière locale ne s'applique qu'après la page 35. Le lot est prêt pour revue humaine, avec la limite de la suite globale V5 consignée ci-dessus.