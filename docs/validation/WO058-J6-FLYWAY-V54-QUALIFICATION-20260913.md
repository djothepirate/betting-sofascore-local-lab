# WO-058 — Garde J6 de sauvegarde, restauration et rétention sur Flyway V54

**Date :** 13 septembre 2026

**Work Order :** [WO-SS-20260907-058-bounded-live-j4-j5.md](../work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md)

**Périmètre :** complément de revue de la PR #35

**Statuts préservés :** `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Constat

La migration append-only V54 est le schéma Flyway courant du Local Lab. Les deux gardes J6
qui conditionnent la qualification de sauvegarde/restauration et l'acceptation d'un manifeste
de rétention restaient pourtant fixées à V53. Elles auraient donc refusé une preuve réellement
produite sur le schéma courant, tout en laissant la documentation annoncer V54.

Le correctif ne modifie ni V54 ni une migration historique. Il ne convertit, ne réécrit et ne
réinterprète aucune archive, aucun manifeste, aucune observation ni preuve antérieure.

## Contrat corrigé

`scripts/Backup-Restore-J6.ps1` exige strictement que la version Flyway lue sur la source soit
`54`. Toute autre version, y compris V53, échoue avant l'exécution d'une sauvegarde native.

`scripts/Invoke-J6Retention.ps1` continue de vérifier d'abord l'égalité exhaustive des blocs
`source` et `restored` du manifeste. Il exige ensuite que
`source.flywayVersion` soit exactement `54`. Ainsi, un manifeste V53 historique ne franchit
pas la garde actuelle et ne permet aucune purge.

Le contrat reste fail-closed : une version absente, non numérique, différente entre source et
restauration, ou différente de V54 est refusée. V53 reste une preuve historique lisible, mais
insuffisante pour la sauvegarde/restauration et la rétention sous le schéma courant V54.

## Couverture de régression

`J6NativeBinaryPipelineQualificationTest` vérifie les contrats statiques des scripts :

- `Backup-Restore-J6.ps1` ne conserve plus de garde V53 et exige V54 ;
- `Invoke-J6Retention.ps1` n'accepte plus V53 et exige V54 dans son manifeste ;
- la qualification reste sans exécution de `pg_dump`, `pg_restore`, suppression ou accès à
  une base opérateur.

`FlywayMigrationIT` vérifie que l'installation Testcontainers atteint la migration V54
append-only. Les migrations antérieures et les chemins d'upgrade restent exercés dans une base
PostgreSQL éphémère.

## Validation

| Commande | Résultat |
| --- | --- |
| `mvnw.cmd -q "-Dtest=J6NativeBinaryPipelineQualificationTest" test` | Succès : 4 tests, 0 échec, 0 erreur, 1 ignoré. |
| `mvnw.cmd -q -Pintegration-tests "-Dit.test=FlywayMigrationIT" failsafe:integration-test failsafe:verify` | Succès : 74 tests, 0 échec, 0 erreur, 0 ignoré. |
| `mvnw.cmd clean verify` | `BUILD SUCCESS` : 2 308 tests unitaires (0 échec, 0 erreur, 5 ignorés), puis 241 tests d'intégration (0 échec, 0 erreur, 0 ignoré). |
| `mvnw.cmd -Pintegration-tests verify` | `BUILD SUCCESS` : 2 308 tests unitaires (0 échec, 0 erreur, 5 ignorés), puis 241 tests d'intégration (0 échec, 0 erreur, 0 ignoré). |

Les validations utilisent des scripts examinés statiquement, des fixtures, des doubles locaux
et PostgreSQL Testcontainers. Elles n'exécutent ni sauvegarde native sur la base opérateur, ni
purge, ni campagne live, ni appel réel vers SofaScore.
