# Revue indépendante — prérequis de propriété Java ciblé

## Verdict : PASS — propriété seulement

La preuve relie une JVM Java 25 directe au **handle2520, PID4808**, à son **parent35688**, à son image et à l'UUID d'essai pendant qu'elle vit. Après `RELEASE`, le processus publie `OWNER_RELEASED`, sort avec **0**, puis ses deux répertoires vides sont supprimés. La durée totale rapportée est **959,1857 ms**.

**Aucun scénario failure/sleep de WR-N01 n'est qualifié par ce PASS.** Aucun code23, timeout1500ms, arrêt forcé, décodage d'un texte accentué ou traitement complet des deux modes n'a été testé. L'ancien résidu reste hors de ce travail.

## Identité des fichiers

HEAD revu : `380d9ed5e548fb2fbc398aaeccfca516395c6e82`, propre au début de revue.

| Fichier | SHA-256 |
|---|---|
| OwnershipProbe.java | `8f1aa2734f9ba19040145ab52b6668832a47c8bcbace98de368e18e53ca14863` |
| Probe-JavaOwnership.ps1 | `d88c87a13518c03ea32233ab43acfebcd5cbc06d31494e6d0ea0c12fad58cc48` |
| ownership-proof.json | `70be6ee6153269c440944fdb2b15b21fef065ce5b0e5ad4f5c61f535b18bacef` |

Les trois tailles et empreintes correspondent au manifeste préparatoire.

## Contrôles

- **O01 — PASS. Provenance et portée ciblée.** Les trois SHA et tailles correspondent au preparation-manifest. Authorization décrit explicitement un prérequis d'identité sans réexécution immédiate WR-N01. Le code Java ne contient que la publication d'identité et l'attente RELEASE. Limite : Le relecteur contrôle le dossier fourni ; il ne relance aucun programme et n'attribue pas une nouvelle mesure à cette revue.

- **O02 — PASS. Découverte Java directe sans chemin inventé.** Get-Command java.exe -All distingue javapath/originalFilename shimconsole.exe du JDK originalFilename java.exe. Un seul candidat direct25 est accepté. Chemin demandé, MainModule et ProcessHandle.info.command concordent sur C:\Program Files\Java\jdk-25.0.4\bin\java.exe ; version effective25.0.4+7-LTS-189. Limite : Le seul nom original de fichier n'est pas la preuve d'identité ; la concordance en exécution est ce qui l'établit ici. Pas de généralisation à tout hôte ou lancement futur.

- **O03 — PASS. PID, parent, image, UUID et handle vivant.** Handle2520/PID4808 ; fixture PID4808, parent35688 égal conducteur35688, UUID db7b5bbe-362a-4873-9f98-6b7cf1cd2d38, même image et aliveDuringCheck=true. Les tests du script refusent les divergences avant RELEASE. Limite : Le script enregistre les deux heures de création sans les comparer. Leur différence observée0.8359ms est compatible avec la précision milliseconde de la valeur Java, et n'est pas une égalité textuelle inventée.

- **O04 — PASS. Bornes et sortie naturelle observées.** Readiness bornée à5s, auto-sortie de secours Java après8s sans entrée, attente5s après RELEASE, attente finale limitée par reste20s. Preuve OWNER_RELEASED, exitCode0 immédiatement après sortie, durationMs959.1857. Limite : Les valeurs20s ne constituent pas un superviseur global indépendant de tous les appels de préflight/Process.Start ; seul ce parcours réussi sous1s est démontré. Aucun timeout ni Kill n'est qualifié.

- **O05 — PASS. Confinement enfant et nettoyage des ressources exactes.** RacineUUID neuve sous .tmp/wrn-ownership-preflight-01 dans le worktree, ancêtres sans reparse, cwd/temp/home confinés, UseShellExecute=false/CreateNoWindow=true, variables d'injection retirées du seul ProcessStartInfo. Handle sorti avant Dispose ; seuls deux répertoires vides supprimés, rootAbsent=true. Limite : La suppression est non récursive et tout fichier inattendu bloque. Pas de scan/arrêt tiers ni nettoyage de l'ancien JavaLauncher.log. Le postflight est interne à la conduite, pas une observation indépendante après coup.

- **O06 — PASS. Absence de qualification complète ou de faux vert.** result PASS_OWNERSHIP_ONLY, scope explicite, processesStopped0, oldWRN01ResidueTouched=false ; absence de branches failure/sleep, sockets, fichiers Java ou descendants dans la fixture. Limite : La courte attente de libération de l'identité n'est pas le mode sleep de NativeProbe. Aucun code23, UTF8 accentué, expiration1500ms, arrêt5s ou réussite des deux modes WR-N01 n'est démontré.

## Limites de réutilisation

- Les gardes du script sont adaptés à ce scénario d'identité. Ils ne prouvent pas une conduite universelle : garde PS Major5/Desktop sans test Minor1 explicite, création des répertoires avant try, quote helper non général. Le runtime observé est néanmoins bien5.1 et les chemins de cet essai sont contrôlés.

- Le JSON fournit une observation produite par le conducteur ; ce dossier ne comprend pas les événements natifs du lanceur extérieur. La revue ne remplace ni cette provenance ni une nouvelle qualification native complète.

- Aucune autorisation de réexécution complète n'est déduite de ce PASS ; l'entrée opératoire bornée doit être revue selon la porte de reprise.

L'heure native du handle, `22:41:02.0858359Z`, et celle de Java, `22:41:02.085Z`, sont enregistrées avec des précisions différentes ; leur différence est0,8359ms. Le script ne les compare pas. La présente revue ne transforme pas cette observation en assertion inexistante.

Aucun lancement de modèle ou de probe, aucune modification du candidat, des sources ou de l'oracle par le relecteur. Ce rapport est une revue du prérequis concret ; la réexécution complète WR-N01 demeure distincte.

