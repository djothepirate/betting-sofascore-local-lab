# WO-062 — Qualification complémentaire PB-S01

- **Date :** 15 septembre 2026.
- **Résultat :** PB-S01 **PASS dans run-02** ; la preuve ouverte SEL-01 est levée par la revue indépendante.
- **État cumulé :** **12 cas distincts qualifiés**, 13 exécutions conservées : run-01 reste à 11 PASS / 1 BLOCKED ; run-02 ajoute le PASS complémentaire.
- **Statut :** `QUALIFIED_ON_FROZEN_CASES_PENDING_OWNER_VALIDATION`.
- **Périmètre :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

## Conclusion et pièces de référence

La [qualification courante](../skills/evaluations/WO-062/ss-provider-benchmark/qualification.json) réunit les onze PASS déjà établis
et la [nouvelle preuve PB-S01](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/result.json). Le candidat reste
[ss-provider-benchmark 0.1.0-candidate.1](../skills/local-lab/ss-provider-benchmark/SKILL.md),
sans modification de son contenu ni de ses métadonnées.

SHA-256 du candidat : `4bcecee4dbe75ec74530a7bda63acc8cbc84cc15dcd7c07e2ab9e6f80cc7be94`.
Base de reprise : `6e31062`, dans le worktree WO-062 existant.

Le propriétaire a demandé de compléter la qualification ouverte sur PB-S01. L'autorisation
de transmission du candidat et du contexte de routage au service Codex configuré était déjà
acquise ; elle a été conservée pour cet essai complémentaire.

## Ce qui a été démontré

| Point | Preuve observée |
|---|---|
| Demande explicite | [Prompt A1 inchangé](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/prompt.txt), SHA-256 identique à celui du premier essai. |
| Choix du candidat | [Réponse native](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/response.md) désignant ss-provider-benchmark. |
| Chargement effectif | [Journal natif](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/native-events.json) : une commande de lecture intégrale du SKILL.md isolé, terminée avec code 0. |
| Corps complet | Le texte réellement retourné correspond entièrement au candidat ; le relecteur recalcule la comparaison, sans se fonder sur un booléen déclaré. |
| Identité conservée | SHA-256 brut du candidat identique avant/après ; sortie texte normalisée `7e908db329a8d386c39733542ee5c3efaa1b5b74a72db303603b50db3ca876eb`. |
| Arrêt au routage | Une seule commande observée : lecture du skill. Aucun rapport métier consulté ni build, application, DB du Lab, Docker ou collecte lancé. |
| Revue | [Revue indépendante](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/review.md) et [assertions structurées](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/review.json) : PASS, clôture de la preuve SEL-01. |

La session native est neuve, éphémère et en lecture seule, sous le CLI Codex
`0.154.0-alpha.6.2`. Elle s'est déroulée de `2026-09-15T18:08:24.8069963Z` à
`2026-09-15T18:08:58.0901855Z` ; durée observée 33.283 secondes,
sans comparaison de productivité. Le modèle et l'effort exacts ne sont pas exposés dans les
événements disponibles ; aucun modèle n'a été forcé ni valeur inventée.

## Différence de méthode et limites

Le [protocole complémentaire](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/protocol.md) est déclaré avant le lancement.
Le prompt du cas reste intact ; l'[enveloppe de routage](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/routing-request.txt)
demande génériquement de lire le SKILL.md retenu avant de conclure. Elle ne fournit ni choix
attendu, ni corps de skill, ni hash, ni oracle ou résultat antérieur à l'évaluateur.

Cette réexécution est **instrumentée** : elle ne reproduit pas strictement l'enveloppe
de routage du premier essai. L'oracle exige le chargement exact sur demande explicite ;
la lecture outil l'établit ici. Cela ne certifie pas l'injection automatique du CLI avant
les outils, ni le même chargement avec le seul prompt A1 sans cette consigne de lecture,
et ne transforme pas rétrospectivement run-01 en succès. Les critères A1,
le candidat et tous les fichiers de run-01 restent intacts.

La comparaison du texte remplace CRLF par LF et retire uniquement les retours à la ligne
terminaux du transport. Elle conserve tous les autres espaces et le contenu. Le hash du
fichier brut est distingué de celui du texte retourné. Le journal publié garde le corps
retourné, limité au candidat autorisé, et omet les événements de raisonnement interne.
Le catalogue reconstruit localement après l'essai établit la provenance déclarée ; il
ne remplace pas une capture de la requête modèle. La preuve de chargement repose sur la
commande réellement exécutée et sur son résultat complet.
Le relecteur a vérifié ce journal publié ; l'exhaustivité de son filtrage n'a pas été
contrôlée indépendamment à partir du journal brut.

Les sorties ont été figées avant revue dans [freeze.json](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/freeze.json).
Le relecteur a reçu le critère et ces pièces après l'essai ; il n'a ni contacté l'évaluateur,
ni modifié les sorties. Les onze autres PASS sont conservés sur le même candidat, sans
relance inutile de leurs tâches métier ou de leurs scénarios de sélection.

## Vérification de la livraison

Les [contrôles documentaires](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/checks.json) et le
[manifeste des artefacts](../skills/evaluations/WO-062/ss-provider-benchmark/run-02/artifact-manifest.json) consignent les vérifications
de format, de liens, de cohérence, d'empreintes et de conservation des octets par Git.

- Les 38 sources, les cinq fichiers verrouillés et le manifeste A1 sont inchangés.
- Les 68 fichiers référencés dans le manifeste de run-01 et son manifeste restent inchangés.
- Le SKILL.md, agents/openai.yaml et les dix fichiers du lot 1 restent inchangés.
- Le delta est documentaire : aucun runtime, test applicatif, migration ou workflow modifié.

La suite applicative déjà exécutée sous ce WO porte sur ce même code :
`Verify-Local.ps1`, soit `mvnw.cmd -DskipITs clean verify`, puis
`java ci/VerifyTestReports.java standard` : 2 435 tests exécutés, zéro échec/erreur et cinq
ignorés, fin `2026-09-15T15:58:40Z`. La [preuve de cadrage](WO062-SKILLS-LOT2-SCOPING-20260915.md)
reste la référence. Cette suite n'est pas relancée pour une preuve de lecture de skill ;
aucun test fournisseur ou PostgreSQL supplémentaire n'est requis par ce delta.

## État du Work Order

Le candidat est qualifié sur les douze cas figés avec la méthode documentée. Sa validation
humaine du contenu et de ses limites reste à recueillir avant l'installation personnelle.
WO-062 reste actif ; les autres skills suivent l'ordre accepté. Ce complément ne crée ni
installation personnelle, ni publication, PR, fusion ou clôture du WO.

Fichiers ajoutés : run-02, qualification.json et ce rapport. Synthèses actualisées : WO-062,
les deux README, CHANGELOG, renvoi depuis le rapport A2 et règle .gitattributes ciblée.
