# WO-062 C4 — revue statique du complément opératoire Windows

- **Verdict : PASS_STATIC_PROTOCOL_ONLY.** Aucun défaut bloquant concret relevé dans le complément examiné. Ce verdict porte sur les conditions de conduite ; aucun nouveau WR-H01 ou WR-N01 n'a été exécuté par le relecteur.
- Date : 16 septembre 2026 Europe/Paris ; contrôle final le 15 septembre 2026 à 23:25:56 UTC.
- Worktree : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2`.
- HEAD examiné : `79d0f99c393a06d4d2e81749a55fb3bfbd222cc3` ; état Git propre au début et lors du contrôle final des sources.
- Périmètre demandé : autorisation C4, complément de session, protocole et fixture originaux, harnais WO-044, preuve préalable de propriété et ses limites. Aucune mutation des entrées, aucun modèle, aucune sonde, aucun changement de permissions ou d'environnement.

## Empreintes examinées

| Pièce | SHA-256 |
|---|---|
| `c-requalification-04/authorization.json` | `c408f9279f1f0b705b13bf074b7f13db899769761b3a0c0d2e377ebd25fc6d7f` |
| `c-requalification-04/windows-session-protocol.md` | `3658c70cd4a00d2902ecac2e99e79a2daa0fb500ac2f59d8d861b744b97a6c73` |
| `ss-windows-runtime/runtime-protocol.md` | `b4748a356b881a8574a78156c0ac00c90f493874efe8303d40a7224c12d91a52` |
| `ss-windows-runtime/fixtures/NativeProbe.java` | `4e29c5867eba0fb76518de2307391449c83450449bea0393179738505c69bc8b` |
| `scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1` | `59996b14941700523007e1f6b0784b102d65e088569f95e882340250d43565e4` |
| `scripts/wo044/Capture-WO044NativeArguments.ps1` | `841428737a500fd9ccb1407d3c0822b56872f40b308568ee59ab48bae88b7818` |
| `scripts/wo036/WO036-CampaignTools.psm1` | `43c7bc8eb242161729f6c18f8b0feb67c46715ce911b27572e4bd13e5956bd74` |
| `c-targeted-reprise/ownership-proof.json` | `70be6ee6153269c440944fdb2b15b21fef065ce5b0e5ad4f5c61f535b18bacef` |
| `c-targeted-results/ownership-review.json` | `ee3cddc7f8af80420fc503c2dce665e79e6ef6c47ffc58aa78044b4fcbbad07a` |

Les chemins des dossiers `c-*` et `ss-windows-runtime` sont relatifs à `docs/skills/evaluations/WO-062/`. Les deux entrées originales et les trois fichiers exécutables concordent en taille et SHA-256 avec `ss-windows-runtime/manifest.json` : respectivement 10 630, 1 009, 11 212, 948 et 176 724 octets. Le contenu d'AGENTS est inchangé depuis le HEAD de la précédente revue du lanceur.

## Critères

### C4-W01 — PASS — autorisation bornée et preuve distincte

`authorization.json` fixe Windows `0.1.0-candidate.1`, `run-04`, WR-H01 et WR-N01. Il conserve `automatic_retry=false`, l'exclusion des oracles et réponses précédentes, et l'absence de validation humaine ou d'installation. Les dix sessions maximales comprennent les huit cas Java du candidat révisé et les deux cas Windows. Les liens de propriété désignent un prérequis examiné par le superviseur ; leurs mesures ne deviennent pas celles du nouveau cas.

### C4-W02 — PASS — retrait du CIM auxiliaire sans suppression d'une garde

Le complément, lignes 41–46, établit le conducteur par `$PID` et son objet Process, puis l'enfant créé par son objet/handle, PID, heure, image et arguments. Il écarte la lecture WMI/CIM du parent du conducteur comme prérequis et interdit l'inventaire global.

Le conducteur historique run-03 effectuait précisément `Get-CimInstance Win32_Process -Filter ('ProcessId={0}' -f $PID)` à sa ligne 52, avant lancement. Le diagnostic séparé `c-targeted-results/wrh-preflight-diagnostic.json` constate que les contrôles de chemin, de dossier et d'image propre passent, tandis que `self_cim_parent` échoue avec `HRESULT 0x80041003`. Ce diagnostic provient du sandbox de la tâche parente ; il appuie la suppression du prérequis auxiliaire, sans établir rétroactivement la ligne fautive de la session évaluée dont la pile manquait.

Les gardes internes restent intactes : `Stop-WO044ExactOwnedProcess` vérifie sortie, heure de création, image et jeton de capture avant tout arrêt ; son appel CIM des lignes 80–88 est conservé. L'enlever aurait changé la propriété exigée pour un enfant encore vivant. Sur la branche normale, `HasExited` entraîne un retour avant cet appel.

### C4-W03 — PASS — WR-H01 garde son contrat réel

Le complément, lignes 36–39, conserve Windows PowerShell 7, `-Iterations 2`, l'arborescence de dépendances et TEMP/TMP de la seule copie d'environnement enfant. Le protocole original conserve la copie isolée, les chemins canoniques et sans liens, le transcript, le code natif immédiat, les attentes par capture, le nettoyage et le postflight.

Le harnais effectue une capture initiale, les deux itérations demandées et le cas sans espace ; la capture observe des arguments, sans lancer Java ou l'application. `ConvertTo-WO036JavaJarStartProcessArgument` protège une valeur JAR absolue et canonique ; les formes relatives, extension incorrecte, guillemet ou contrôle font partie des rejets à analyser depuis les sources. Le complément demande d'expliquer ces frontières et les différences historiques sans fournir les résultats de la future reproduction.

### C4-W04 — PASS — deux propriétés WR-N01 neuves

Les lignes 57–76 exigent Windows PowerShell 5.1 Desktop et Java 25 effectivement observés, une découverte de toutes les applications Java, le choix direct unique et sa version réelle. Le nom de fichier d'origine aide à distinguer un relais ; la concordance entre image MainModule vivante et exécutable demandé demeure requise.

Les modes `failure` puis `sleep` sont successifs, avec UUID distincts. Chaque nouveau `Process.Start` doit fournir objet, handle, PID, heure, image, commande, conducteur et racines. PID/UUID sont confrontés à la fixture. Le complément dit explicitement que la fixture originale ne publie ni parent, ni image, ni heure de création : la création connue du conducteur est distinguée d'une mesure de parent effectuée dans la JVM. Aucune ancienne propriété ne vaut pour ces nouveaux processus.

### C4-W05 — PASS — bornes, arrêt et nettoyage conservés

Les lignes 15–18 conservent 60 s pour la conduite H01 et 45 s pour N01, préflight et nettoyage compris. L'objectif de réponse avant six minutes et le watchdog extérieur de quinze minutes ne remplacent pas ces limites. Les budgets restants doivent borner les attentes.

N01 conserve attente failure 10 s, readiness sleep 10 s, attente de 1 500 ms depuis READY, puis sortie après arrêt au plus 5 s. Les lignes 80–89 exigent un handle encore vivant et la concordance PID/UUID/image/commande avant `Kill()` de .NET Framework. Identité manquante ou divergente interdit l'arrêt forcé ; une sortie naturelle ne qualifie pas le traitement d'expiration. Le second mode n'est lancé que si ses bornes restent tenables. Nettoyage par ressources exactes après sortie prouvée, répertoires vides seuls et conservation des résidus inattendus demeurent obligatoires.

### C4-W06 — PASS — conduite sans oracle ni résultat fourni

Le complément examiné contient des contraintes d'observation, de chronométrage, de propriété, de confinement et de réponse finale. Il ne fournit aucun PID/UUID d'un ancien essai, aucun code natif à reproduire, aucune mesure antérieure, aucun marqueur de réussite attendu et aucun verdict. Les constantes de délai et les noms `failure`/`sleep` viennent du protocole original.

Les lignes 22–32 imposent des marqueurs postérieurs aux actions réelles, une commande distincte `WR_PROMPT_RETURNED`, un postflight séparé, la réponse finale et la conservation des blocages. Le complément demande les références historiques sans donner leurs réponses. Cette revue contrôle le contenu de l'entrée ; elle ne certifie pas l'enveloppe effective des futurs appels, à vérifier lors du gel des contextes.

### C4-W07 — PASS — ancienne preuve limitée correctement

La preuve `PASS_OWNERSHIP_ONLY` porte sur une JVM directe lancée par la sonde distincte `OwnershipProbe`, libérée naturellement par RELEASE. Elle établit son identité et le nettoyage de sa racine, sans mode failure/sleep, sans arrêt forcé et sans reprise complète WR-N01. Sa revue le précise et rappelle que l'observation des deux heures de création n'était pas un test d'égalité textuelle.

Le complément C4 ne transforme pas ce prérequis en qualification des deux nouvelles JVM. Le conducteur et les accès MainModule du futur sandbox restent à observer ; les anciens fichiers et résidus ne sont pas autorisés au nettoyage par cette entrée.

## Limites à conserver dans les prochains résultats

1. **Garde CIM interne H01 :** sa suppression n'est pas autorisée. Elle peut encore refuser ou retarder un nettoyage d'enfant vivant. L'absence du CIM auxiliaire avant lancement ne prouve donc pas toutes les branches d'arrêt ; un échec conserve ses preuves et résidus exacts sans résultat réussi inventé.
2. **Mode failure court :** la fixture sort immédiatement après ses marqueurs. L'image et l'heure doivent être obtenues dès le lancement pendant la vie du processus ; une capture tardive manquante reste un blocage. Un PID ou les métadonnées du fichier ne réparent pas cette absence.
3. **Budgets :** le texte prescrit des bornes ; seul le conducteur futur et ses événements établiront leur application réelle. Les lectures, découverte, vérification de version, préflight et nettoyage doivent entrer dans le budget de conduite. Les six/quinze minutes ne sont pas un budget élargi des sondes.
4. **Propriété N01 :** la fixture originale ne mesure pas son parent. La preuve retenue est la création directe et la concordance du handle/PID/UUID/image ; elle doit être décrite avec cette provenance, sans reprendre les champs de l'ancienne OwnershipProbe.
5. **Séparation de contexte :** le gel final des requêtes doit exclure la preuve préalable, ses revues, cette revue, les réponses anciennes et l'oracle. L'absence de corrigé dans le complément ne prouve pas à elle seule la composition du contexte transmis.

## Méthode et état final

Lectures ciblées des fichiers ci-dessus, comparaison des contraintes et contrôle des tailles/SHA-256 avec le manifeste original. Une sous-revue indépendante par l'agent `jm_selection_review` a confronté WR-N01 au protocole original et à NativeProbe : aucun défaut bloquant ; mêmes limites sur le mode court, la sortie naturelle et l'enveloppe réellement transmise. Cette sous-revue était statique, sans écriture ou lancement.

Commandes de contrôle : état/HEAD/diff Git, lectures ciblées, recherches locales et empreintes. Contrôle d'intégrité des cinq fichiers originaux : code 0. Aucun test applicatif, sonde, harnais ou modèle lancé ; aucune variable d'environnement complète lue ou exportée. Seuls `.tmp/c4-windows-protocol-review.md` et `.tmp/c4-windows-protocol-review.json` sont écrits par cette revue. Les entrées suivies restent inchangées.
