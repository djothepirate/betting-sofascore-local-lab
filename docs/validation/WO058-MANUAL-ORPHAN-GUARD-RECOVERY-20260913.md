# WO-058 — Récupération locale d'une garde manuelle orpheline

**Date :** 13 septembre 2026

**Work Order :** [WO-SS-20260907-058-bounded-live-j4-j5.md](../work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md)

**Périmètre :** complément P1 de revue de la PR #35

**Statuts préservés :** `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Constat

Une collecte manuelle J3/J4/J5 obtient le garde durable partagé, mais ne crée aucune ligne
`live_campaign`. Si son processus s'arrête après la transition du garde en `CLEANUP_REQUIRED`,
la clôture d'une campagne live ne peut pas s'appliquer à cette identité. Le garde devait rester
bloquant tant qu'une preuve locale et une action explicitement séparée n'étaient pas disponibles.

## Contrat ajouté

`/provider-access` affiche **« Garde manuelle orpheline »** seulement lorsque le garde est
`CLEANUP_REQUIRED`, possède un propriétaire et une identité de campagne, et que cette identité
ne correspond à aucune ligne `live_campaign`. Le `POST`
`/provider-access/release-orphaned-manual-guard` exige :

- un jeton de formulaire local à usage unique ;
- une confirmation opérateur ;
- l'identifiant de garde affiché et sa génération positive.

La commande prend l'exclusion locale de nettoyage, refuse une session ou un superviseur actif et
demande à `LiveOrphanProcessProbe` de prouver l'absence de l'ancien propriétaire et de tout
processus Playwright associé. Après cette preuve, elle relit l'absence de campagne live. La
transaction SQL ne libère le garde que si l'état, l'identifiant de campagne, le propriétaire
complet (instance, PID, date de création), la génération et la date de changement correspondent
encore exactement à ceux qui ont été observés. Une nouvelle acquisition, une campagne devenue
visible, un processus actif ou une preuve incomplète laisse le garde bloqué.

## Effets volontairement exclus

La libération ne démarre aucun navigateur et ne réalise aucun appel fournisseur. Elle ne crée ni
réponse ni snapshot, ne reprend aucune collecte ou retry, ne clôture pas une réservation de départ
incertain et ne réarme pas une suspension 403/429. Ces actions restent des décisions locales
explicites et séparées, avec leurs propres confirmations et gardes.

## Couverture de régression

- `LiveCampaignServiceTest` vérifie qu'un ancien propriétaire manuel absent devient
  `CLEANUP_REQUIRED` au redémarrage sans créer ni interrompre une campagne live.
- `LiveCampaignRecoveryTest` couvre la preuve d'absence avant la libération, les processus actifs
  ou incertains, le formulaire périmé, l'apparition concurrente d'une campagne live et l'exclusion
  commune avec une session active.
- `ProviderAccessControllerTest` couvre le panneau local, le jeton, la confirmation, la génération
  et l'absence d'interaction avec le réarmement, la réservation ou la clôture de départ.
- `LiveCampaignControllerTest` vérifie qu'un refus de lancement oriente ce seul cas vers
  `/provider-access`, sans lien vers une campagne live inexistante.
- `LiveCampaignPersistenceIT` vérifie la comparaison atomique de l'identité exacte et l'absence de
  création d'une ligne `live_campaign`.

## Validation ciblée

| Commande | Résultat |
| --- | --- |
| `mvnw.cmd -q "-Dtest=ProviderAccessControllerTest,LiveCampaignRecoveryTest,LiveCampaignServiceTest,LiveCampaignControllerTest,LiveOrphanProcessProbeTest,ManualProviderRequestCoordinatorTest" test` | Succès : 276 tests, 0 échec, 0 erreur. |
| `mvnw.cmd -q -Pintegration-tests "-Dit.test=LiveCampaignPersistenceIT" failsafe:integration-test failsafe:verify` | Succès : scénario PostgreSQL ciblé vert ; migration et validation Flyway jusqu’à V54. |
| `mvnw.cmd clean verify` | Succès : 2 320 tests standards, 0 échec, 0 erreur, 5 ignorés ; 242 tests d’intégration, 0 échec, 0 erreur. |
| `mvnw.cmd -Pintegration-tests verify` | Succès : 2 320 tests standards, 0 échec, 0 erreur, 5 ignorés ; 242 tests d’intégration, 0 échec, 0 erreur. |

Cette passe utilise des doubles locaux et des tests web loopback. Elle n'exécute aucun appel réel
vers SofaScore ni aucun lancement de campagne fournisseur. Les tests d’intégration utilisent les
instances PostgreSQL éphémères locales de Testcontainers ; aucun transport fournisseur ne fait
partie de la qualification.
