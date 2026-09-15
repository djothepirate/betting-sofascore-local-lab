# WO-SS-20260915-061 — CI locale et maîtrise des quotas

- **Statut :** `COMPLETED_OWNER_AUTHORIZED_PREMERGE` — qualification locale et CI du candidat réussies ; clôture et fusion autorisées par le propriétaire le 15 septembre 2026. Le classement documentaire précède la fusion ; la clôture effective est liée à l'état fusionné de la PR #37 vers `feature/V0.1.0-RC01`.
- **Date :** 2026-09-15
- **Autorité :** demande propriétaire de retirer les politiques CI inutiles, l'archivage automatique
  des bundles intermédiaires et les coûts évitables, en préservant tests et preuves obligatoires
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-061`
- **Cible de PR :** `feature/V0.1.0-RC01`
- **PR :** [#37 — CI locale, preuves obligatoires et suppression des bundles intermédiaires](https://github.com/djothepirate/betting-sofascore-local-lab/pull/37)
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
- [x] CI distante du candidat `965caac` réussie et validation propriétaire ; fusion autorisée.
- [ ] Checks du commit documentaire final réussis puis fusion de la PR #37 ; clôture effective à cette fusion.

## Clôture autorisée — 15 septembre 2026

Le propriétaire confirme : « tout est OK, la fusion et la clôture de la PR peut se faire ».
Cette décision autorise le classement documentaire, sa publication et la fusion dans le
train cible. Le [run 34966547646](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/34966547646)
qualifie le candidat exact `965caac439184813be2ec1eb1be2fe727f42f518` :

| Check obligatoire | Résultat constaté |
| --- | --- |
| [Windows — garde locale et tests standards](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/34966547646/job/104372248002) | `SUCCESS`, contrôle et archivage des preuves XML réussis |
| [Linux — sécurité, PostgreSQL et distribution locale](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/34966547646/job/104372248251) | `SUCCESS`, contrôle et archivage des preuves XML réussis |

L'inventaire API de ce run contient uniquement les deux preuves XML, soit 2 714 245 octets
(environ 2,71 Mo), avec expiration le 18 septembre après trois jours ; aucun bundle intermédiaire.

Le dernier changement fonctionnel reste `a3ed5e7c854369f82d74291255fdcefdb3727792`.
La préparation de clôture ne modifie que le WO déplacé, les liens du README et du guide CI,
et le changelog. Le [rapport de qualification](../../validation/WO061-CI-LOCAL-QUOTAS-20260915.md)
reste une photographie historique inchangée : sa liste de fichiers et ses mentions d'attente
décrivent l'état lors de sa rédaction ; la présente décision les actualise pour la clôture.

Le commit documentaire final doit obtenir ses propres checks verts avant fusion. Les succès
ci-dessus ne sont pas attribués à un commit futur. Le classement dans `completed` ne prouve
pas à lui seul la fusion : la PR #37 porte son état effectif, son SHA et son horodatage de
fusion lorsqu'elle est réalisée. Aucun nouveau build local complet n'est requis pour cette
seule mise à jour documentaire, sans modification de code, test, configuration ou migration.

## Livraison et limites

Les réglages distants, la purge d'artefacts existants, la publication du candidat et sa fusion
doivent être distingués des fichiers modifiés. Aucun résultat d'un ancien SHA ne qualifie le
nouveau workflow. Voir le [guide CI](../../runbooks/CI-LOCAL-QUOTAS.md) pour le contrat et la
conservation des preuves.
