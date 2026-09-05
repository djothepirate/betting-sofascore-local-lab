# WO-SS-20260905-053 — Diagnostic expurgé CTRL_BREAK J6

- Statut : IN_PROGRESS_DIAGNOSTIC.
- Autorité : demande propriétaire de poursuivre le diagnostic avec marqueurs stables, compilation/readiness/signal/harness/nettoyage séparés et reproduction Windows CI, sans relever les délais ni exclure le test.
- Base : bbce3c5e643c4cc0840e2654a30142ea9ba136f4.
- Branche : codex/wo-053-j6-ctrl-break-diagnostics ; worktree .tmp/w53.
- PR source : #29, run Windows 33972681012, job 101323866506.

## Périmètre

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

Vérifier syntaxe PowerShell, contrat nominal/hostile du filtrage, suite Windows standard via Verify-Local.ps1 avec les variables CI pertinentes ; aucun test supplémentaire exclu. Comparer Java et Windows locaux au runner windows-2022/Temurin : une reproduction locale n'est pas une reproduction exacte de l'image GitHub. Publication/relance CI distante à distinguer des essais hôte. Aucune fusion automatique.
