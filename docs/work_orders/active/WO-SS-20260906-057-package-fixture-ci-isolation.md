# WO-SS-20260906-057 — Isolation CI des fixtures de packaging

- **Statut :** READY_FOR_REVIEW — qualification shell locale réussie, CI du candidat et revue requises
- **Date :** 2026-09-06
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260906-057`
- **Cible de PR :** `feature/V0.1.0-RC01`
- **SHA de départ exact du train :** `65d4369a22d4bce6bd7b19d9d950f04922782da0`
- **Autorité :** poursuite de la qualification CI et du snapshot RC01 autorisée par le propriétaire
- **Réseau fournisseur / application / Docker :** `NO`

## État de départ et défaut reproduit

La PR [#33](https://github.com/djothepirate/betting-sofascore-local-lab/pull/33), revue par le
propriétaire, a intégré le candidat `5f201a7e6ac9ff290482dbbac331397d2dc13b06` par merge commit
dans sa feature. Le SHA de départ ci-dessus est ce merge commit ; il n'est pas déclaré qualifié.

Le run push feature [34027057314](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/34027057314)
échoue dans le [job Linux 101469792627](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/34027057314/job/101469792627),
à l'étape de test des gardes, avant les tests applicatifs Linux, le packaging et le téléversement.
Les checks verts de la PR et du push WO précédent portaient un autre SHA/contexte.

Le scénario négatif de `ci/test-package-guards.sh` impose `CI_COMMIT_BRANCH=main` mais hérite
de `SOURCE_BRANCH_NAME=feature/V0.1.0-RC01` défini par GitHub. Le packaging donne priorité à ce
dernier : la fixture accepte alors la feature, poursuit sa préparation minimale et rencontre
un script absent. La raison du refus attendu n'est donc plus rencontrée :

```text
FAIL: motif de refus absent : un snapshot durable exige une branche d'intégration
DURABLE_SNAPSHOT_SOURCE=PASS:feature/V0.1.0-RC01
sh: 0: cannot open ci/assert-local-only.sh: No such file
```

La reproduction locale WSL sur la base exacte, avec `SOURCE_BRANCH_NAME`, `SOURCE_COMMIT_SHA`,
`GITHUB_EVENT_NAME=push`, `GITHUB_REF_TYPE=branch` et `GITHUB_REF_NAME` du pipeline, produit
exactement ces trois lignes et sort avec le code `1` avant correction.

## Correction et périmètre

La frontière des assertions devient `env -i PATH="$PATH" "$@"` : aucun contexte implicite du
runner n'entre dans les scripts testés. Les affectations `env ...` propres à chaque scénario
restent explicites et effectives. Les scripts de production ne sont pas modifiés.

Chaque assertion négative est exécutée avec contrôle du résultat et de son motif attendu dans
huit contextes : appelant, feature GitHub, WO GitHub, PR GitHub, feature GitLab, MR GitLab, tag
GitLab, puis variables volontairement invalides. Le contexte GitLab inclut le cache Maven du
runner. L'héritage est injecté dans un sous-processus et ne pollue pas les assertions suivantes.

Un contrôle positif traverse la même frontière : une `SOURCE_BRANCH_NAME=feature/V0.1.0-RC01`
explicitement fournie est passée au véritable classeur `check-durable-snapshot-source.sh`. Il
doit réussir et produire son marqueur exact dans chacun des huit contextes, prouvant que les
paramètres du scénario ne sont pas effacés avec les variables héritées.

Fichiers concernés : test `ci/test-package-guards.sh`, présent WO, changelog et lien README.
Maven reste `0.1.0-rc.1-SNAPSHOT`. Aucun packaging/garde de production, workflow, tag, release,
permission, rétention, base, fixture fournisseur ou Work Order antérieur n'est changé.
Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY` sont conservés.

## Critères et qualification

- [x] Reproduction rouge locale concordante avec le pipeline feature sur le SHA de départ.
- [x] Isolation complète des variables héritées à la frontière d'invocation des assertions.
- [x] Contrepreuve positive d'une feature explicitement valide, via le vrai classeur de production.
- [x] Agrégat shell vert avec environnement appelant neutre puis contexte feature post-fusion.
- [x] Syntaxe, diff et absence de secret dans les changements contrôlés.
- [ ] CI Windows/Linux du nouveau commit et revue du correctif avant fusion.
- [ ] Qualification du nouveau sommet feature et de son snapshot après fusion.

Les deux agrégats shell ont terminé avec le code `0` : `sh ci/test-package-guards.sh` sans contexte
CI ajouté, puis la même commande avec le contexte GitHub feature utilisé pour la reproduction.
Chacun a produit `PACKAGE_FIXTURE_ENVIRONMENT=PASS_EXPLICIT_INPUTS_ONLY`,
`RELEASE_REPRODUCIBILITY=PASS_LOCAL_ONLY`,
`DEPENDENCY_CHECK_ARGUMENTS=PASS_LOCAL_ONLY_OBSERVATION` et
`PACKAGE_GIT_GUARDS=PASS_LOCAL_ONLY`. Chaque exécution inclut les huit contre-contextes par
assertion, les graphes PR/MR et les tests de reproductibilité hors réseau.

`sh -n ci/test-package-guards.sh` et `git diff --check` réussissent. Le diff du test et les
documents ajoutés ont été relus : uniquement noms publics, références Git et variables
synthétiques, aucun secret. Aucun build Maven applicatif n'est relancé localement : seule une
fixture shell change, et la CI distante qualifie le nouveau commit avant sa revue. Le scan
historique du nouveau commit publié reste une preuve distincte à recueillir.

## Suite

Publier une PR vers la feature exacte après qualification locale. La fusion requiert la revue
humaine propre au correctif. Qualifier ensuite le nouveau merge commit et son snapshot avant
synchronisation GitLab ; ce WO n'affirme aucune nouvelle qualification de `main`, de la feature
de départ, d'un artefact ou d'une release.
