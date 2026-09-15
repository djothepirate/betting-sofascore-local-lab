# WO-SS-20260915-061 — CI locale et maîtrise des quotas

- **Statut :** READY_FOR_REVIEW — qualification locale réussie ; CI distante, revue humaine et fusion requises
- **Date :** 2026-09-15
- **Autorité :** demande propriétaire de retirer les politiques CI inutiles, l'archivage automatique
  des bundles intermédiaires et les coûts évitables, en préservant tests et preuves obligatoires
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-061`
- **Cible de PR :** `feature/V0.1.0-RC01`
- **Base canonique vérifiée :** `59b4daedd089b778d1fd2560799f20dd16658cf1`
- **Version Maven :** `0.1.0-rc.1-SNAPSHOT`
- **Worktree :** `.tmp/wo061-ci-local-quota`, distinct du checkout du train et d'Eclipse

## Problème établi

Le [run GitHub 34950465172](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/34950465172)
a réussi les tests Windows et Linux/PostgreSQL, puis échoué à l'upload du bundle à cause du
quota de stockage. L'inventaire API du 15 septembre compte 22 bundles intermédiaires de près
de 55 Mo chacun. Les pushes de WO et leurs PR relancent chacun les deux suites.

GitLab répète les suites sur les branches synchronisées et archive les snapshots de train.
`quality:observe` mélange des tests avec des exports sous `allow_failure: true`. La demande
propriétaire confirme le développement et l'utilisation locaux Windows, avec GitHub Desktop,
sans déploiement VPS. L'héritage des politiques de fabrique du Betting Project est donc révisé
dans le périmètre CI, sans toucher aux règles de collecte ni à la persistance applicative.

## Décision et périmètre

- GitHub : PR et demande manuelle ; deux jobs Windows/Linux toujours bloquants.
- GitLab : MR, tags et demande manuelle ; tests PostgreSQL et promotion toujours contrôlés.
- Supprimer fabrication/archivage automatique des bundles intermédiaires et observations
  coûteuses automatiques, conserver distribution finale taguée et ses preuves.
- Distinguer tests, preuves obligatoires et seul export Javadoc réellement facultatif.
- Ajouter un contrôle explicite des XML ; rétention courte, sans upload de bundle.
- Mettre à jour ADR-SS-004, AGENTS, README, modèle de PR, changelog et guide CI.

Les exigences des WO-031/055/056 sur snapshots automatiques et qualité héritée sont remplacées
par cette décision. Leurs preuves historiques restent inchangées. Les conventions de trains,
la revue humaine avant fusion, Java 25, Spring Boot 4.1.0, la localité et les statuts
`EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY` sont conservés.

## Critères observables

- [x] Les tests Windows et Linux/PostgreSQL restent bloquants ; aucune suite réelle n'est dans un job tolérant.
- [x] Un rapport exigé absent, invalide ou sans test réellement exécuté empêche la validation.
- [x] L'indisponibilité de Javadoc facultative ne modifie pas le verdict des tests réussis.
- [x] Aucun upload automatique de bundle intermédiaire GitHub/GitLab, aucun double run push/PR.
- [x] Les gardes branches, versions, promotion et secrets continuent à refuser les contre-exemples.
- [x] Qualification locale et revue indépendante consignées dans le [rapport](../../validation/WO061-CI-LOCAL-QUOTAS-20260915.md).
- [ ] CI distante du candidat et revue humaine avant fusion ; WO actif jusqu'à fusion.

## Livraison et limites

Les réglages distants, la purge d'artefacts existants, la publication du candidat et sa fusion
doivent être distingués des fichiers modifiés. Aucun résultat d'un ancien SHA ne qualifie le
nouveau workflow. Voir le [guide CI](../../runbooks/CI-LOCAL-QUOTAS.md) pour le contrat et la
conservation des preuves.
