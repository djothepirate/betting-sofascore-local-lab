# WO-SS-20260905-053 — Diagnostic expurgé CTRL_BREAK J6

- Statut : COMPLETED — validation et clôture propriétaires après CI post-fusion verte, le 2026-09-05 ; cause CI initiale toujours NOT_ESTABLISHED.
- Preuve : [suivi expurgé](../../validation/J9-WO053-CTRL-BREAK-DIAGNOSTIC-20260905.md). Instrumentation publiée au commit 5f3dea2 ; deux essais Windows CI réussis sans reproduction du défaut initial, run global non vert à cause du scan historique Linux. Aucun correctif causal ou clôture revendiqué.
- Autorité : demande propriétaire de poursuivre le diagnostic avec marqueurs stables, compilation/readiness/signal/harness/nettoyage séparés et reproduction Windows CI, sans relever les délais ni exclure le test.
- Base : bbce3c5e643c4cc0840e2654a30142ea9ba136f4.
- Branche : codex/wo-053-j6-ctrl-break-diagnostics ; worktree .tmp/w53.
- PR source : #29, run Windows 33972681012, job 101323866506.

## Décision de clôture — 2026-09-05

Le propriétaire confirme : « tous les checks sont verts. WO-053 est donc terminé ». Cette décision supersède les mentions historiques d'attente de revue ou d'absence de clôture ci-dessous et dans les rapports, dont les preuves restent inchangées.

- Correctif final : `43ac1945ac87ee15b56092265890d06311bcab29`.
- PR #29 fusionnée : `d187d501bb51be265788fa68b59deec3f84ee281`, le 2026-09-05 à 16:00:36Z (18:00:36 Europe/Paris).
- CI de PR `33975918479` : Windows et Linux PASS.
- [CI post-fusion 33976519624](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/33976519624), sur ce commit de fusion : SUCCESS vérifié ; Windows 3 min 07 s, Linux 4 min 54 s, fin du dernier job à 16:05:35Z.
- Qualification locale complète déjà consignée : 1193 tests Surefire et 106 Failsafe, zéro échec/erreur. Pas de nouvelle exécution Maven pour le seul classement documentaire ; aucune nouvelle qualification runtime revendiquée.
- Clôture documentaire locale après fusion du correctif ; ce classement n'est pas encore publié dans main au moment de sa rédaction.

Aucun nouveau POST, appel fournisseur, accès primaire, déploiement VPS ou changement de production. Aucun assouplissement des bornes ni preuve de disparition définitive de l'intermittence initiale.

## Périmètre historique

Instrumentation du seul outillage synthétique de qualification et tests de son contrat expurgé. Aucun correctif comportemental du pipeline, délai, retry, filtre ou exclusion de test. Aucune base primaire, sauvegarde, restauration, migration, fournisseur, receiver, certificat ou VPS. Pas de -WithDocker.

## Contrat de preuve

| Étape | Résultat attendu | Preuve |
|---|---|---|
| Compilation | lancement / sortie / fichier présent | marqueurs fixes et code borné |
| Readiness | observation / délai / sortie anticipée | classification stable native |
| Signal | émission réussie ou échec API | marqueur fixe, pas de message système brut |
| Harness | attente / sortie / assertions du résultat | code expurgé et nom de porte |
| Nettoyage | tentative et résultat, distincts du primaire | marqueurs dédiés ; propriété exacte inchangée |

Préserver les marqueurs historiques et l'absence de secrets, chemins, PID ou contenu arbitraire dans les nouveaux marqueurs. Les simulations PostgreSQL restent synthétiques ; aucune preuve de restauration primaire revendiquée. Les manifests et preuves J7 restent immuables.

## Qualification et limites

Reprise corrective autorisée après revue : [suivi des deux P2](../../validation/J9-WO053-REVIEW-HARDENING-20260905.md). Diagnostic natif non bloquant pour le nettoyage et relais progressif des marqueurs ; contre-épreuves raccordées à la qualification existante. Pas d'intégration automatique à PR #29 ni de clôture.

Vérifier syntaxe PowerShell, contrat nominal/hostile du filtrage, suite Windows standard via Verify-Local.ps1 avec les variables CI pertinentes ; aucun test supplémentaire exclu. Comparer Java et Windows locaux au runner windows-2022/Temurin : une reproduction locale n'est pas une reproduction exacte de l'image GitHub. Publication/relance CI distante à distinguer des essais hôte. Aucune fusion automatique.
