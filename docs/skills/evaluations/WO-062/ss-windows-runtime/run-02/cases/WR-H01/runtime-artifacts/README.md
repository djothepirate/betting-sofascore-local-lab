# WR-H01 — reproduction observée de WO-044 et comparaison à WO-053

Statuts conservés : **EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED, NO_CRITICAL_DEPENDENCY**.

## Résultat de cette session

Une invocation du harnais WO-044 figé a réellement terminé avec
`WO044_HOST_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED` et **code natif 0**.
Elle a exécuté une capture initiale sans protection, **deux** captures corrigées
avec espaces et une capture sans espace. Les cinq itérations du rapport historique
WO-044 ne sont pas les mesures de ce nouvel essai.

Exécution du 15 septembre 2026 à 22:03:58 UTC, soit le **16 septembre à 00:03:58
Europe/Paris**. Le champ `question_as_of=2026-09-15` de l'entrée n'est pas utilisé
pour antidater la mesure.

| Mesure actuelle | Résultat observé |
|---|---|
| PowerShell du conducteur | 7.6.6, Core, `PSHOME=C:\Program Files\PowerShell\7` |
| Exécutable réel du conducteur et du harnais | `C:\Program Files\PowerShell\7\pwsh.exe`, version de fichier `7.6.6.500` |
| Windows et architecture | Windows NT `10.0.26200.0`, processus et OS X64 |
| PID du harnais, handle conservé | `36948`, handle `1496` |
| Création du harnais | `2026-09-15T22:03:58.8528744Z` |
| Code natif du harnais | **0**, copié depuis `Process.ExitCode` immédiatement après `WaitForExit` réussi |
| Code de conduite / code de l'outil | **0 / 0**, couches conservées séparément |
| Durée monotone lancement → sortie observée du harnais | **2726.6811 ms** |
| Durée monotone du lanceur extérieur, conducteur inclus | **3609.1594 ms** |
| Durée rapportée par l'outil | **3.796855 s** |
| Postflight dans la conduite, drainage et empreintes inclus | **305.5492 ms** ; ce n'est pas la durée interne du nettoyage du harnais |
| Commande suivante | `Write-Output WR_PROMPT_RETURNED`, observée à `2026-09-15T22:04:10.8278624Z` dans un appel outil séparé |
| Postflight séparé | `2026-09-15T22:05:09.5243423Z`, **133.8408 ms**, code outil **0** |
| stderr du harnais | Vide |
| Arrêt forcé | Aucun ; aucun code après arrêt forcé |

Le chronomètre utilise `System.Diagnostics.Stopwatch`, fréquence observée
10 000 000 Hz. Les heures UTC sont des repères distincts des durées monotones.
Les attentes internes du harnais restent 10 s par capture et 5 s pour un arrêt
exact éventuel. Le budget extérieur reste 60 s, avec une échéance de surveillance
à 55 s pour réserver le nettoyage. Aucune échéance n'a été atteinte ou augmentée.
La durée par capture et celle du nettoyage interne ne sont pas émises par le
harnais figé ; aucune valeur n'est inventée pour ces étapes.

## Commande réellement exécutée et confinement

Le lanceur extérieur a exécuté `Run-WRH01Harness.ps1`, qui appelle
`Invoke-WRH01Harness.ps1`. Celui-ci a lancé exactement ces arguments par
`ProcessStartInfo.ArgumentList` :

```text
"C:\Program Files\PowerShell\7\pwsh.exe" -NoLogo -NoProfile -NonInteractive -File "C:\Dev\BettingProject\codex\betting-sofascore-local-lab\.tmp\wo062-skills-lot2\.tmp\wo062-evaluations\ss-windows-runtime\run-02\WR-H01\scripts\wo044\Invoke-WO044JavaJarArgumentBoundaryQualification.ps1" -Iterations 2
```

Le répertoire de travail de l'enfant est le `output/` absolu de cette copie.
Seuls `TEMP` et `TMP` dans sa copie d'environnement sont fixés à
`output/runtime/` absolu. Les deux valeurs du parent ont été comparées après
exécution et sont inchangées. Pas d'export global d'environnement.
`UseShellExecute=false` et `CreateNoWindow=true` sont utilisés ; les captures
internes conservent le `-WindowStyle Hidden` du harnais.

Les chemins sont vérifiés comme absolus et canoniques, avec préfixe parent plus
séparateur, refus de `..`, de guillemets et de caractères de contrôle, et examen
des attributs de chaque ancêtre pour refuser les jonctions/liens. La base runtime
était vide. Le harnais a créé sa propre racine UUID neuve :

```text
output/runtime/WO044 Java JAR argument boundary e0bcc5c7-8317-4591-8221-ef76740ceafe
```

Cette racine a été observée pendant l'essai puis trouvée absente. Les deux
postflights trouvent **zéro entrée dans `output/runtime/`**. Le handle détenu
établit la sortie du harnais ; la vérification séparée ne retrouve plus son PID.
Le harnais rapporte aussi `WO044_RESIDUAL_PROCESS_COUNT=0` pour ses enfants exacts
et `WO044_RESIDUAL_TEMP_ROOT_COUNT=0`. Aucun processus tiers n'a été arrêté.
Le conducteur n'a effectué aucune suppression ; le nettoyage normal est celui
du harnais, limité à ses fichiers enregistrés puis à sa racine vide.

Les flux redirigés ont été drainés de manière asynchrone avec un décodeur UTF-8
strict ; les preuves sont en UTF-8 sans BOM. L'entrée console du conducteur était
`ibm850`, sa sortie console et `$OutputEncoding` étaient `utf-8` ; aucune saisie
console n'a servi à alimenter le harnais. Le captureur figé écrit son JSON en
UTF-8 sans BOM. Son stdout/stderr est vide : ces flux internes n'établissent pas
un comportement d'encodage sur du texte accentué.

## Ce que la capture établit

Les marqueurs intégraux sont dans [execution.stdout.txt](execution.stdout.txt).
Ils valident les assertions du harnais fourni :

- **Défaut reproduit** : le chemin non protégé est découpé ; la capture initiale
  contient plus de six arguments et ne contient pas le chemin JAR complet comme
  une valeur unique.
- **Correction vérifiée deux fois** : le helper
  `ConvertTo-WO036JavaJarStartProcessArgument` protège le seul chemin canonique
  par une paire de guillemets. L'enfant reçoit exactement les six valeurs
  attendues : identité, trois flags JDK, `-jar`, chemin JAR complet.
- Le chemin sans espace reste exact ; quatre valeurs ambiguës sont refusées
  (relative, extension non JAR, guillemet et caractère de contrôle).
- Les trois flags et l'ordre après `-jar` restent exacts. Cela qualifie leur
  transmission textuelle, pas le comportement de retry du JDK.

Le mécanisme causal local est la recomposition de `Start-Process -ArgumentList`
en une ligne native sans protection suffisante du chemin. Le helper change
cette sérialisation ; la contre-épreuve non protégée et les captures protégées
utilisent la même chaîne contrôlée. Ce constat correspond au défaut décrit
dans le rapport WO-044, sections 2 à 4, et au helper du module WO-036, ligne 241.

## Limites de preuve

Le captureur est **PowerShell**, et le fichier synthétique contient seulement
quatre octets d'en-tête ZIP. **Java et un JAR applicatif n'ont pas été lancés.**
Cette preuve ne qualifie donc ni Java 25 en exécution, ni Spring Boot, ni la
disponibilité d'une application, ni Eclipse, PowerShell 5.1, WSL ou une stabilité
générale. Aucun Maven, Pester, Docker, navigateur, fournisseur, HTTP, base,
application ou CI n'a été exécuté par cette session. Le test Pester fourni
est resté une source de lecture.

Les JSON d'arguments et journaux temporaires sont supprimés par le harnais figé.
Leur contenu brut n'a pas été archivé. La preuve durable est son transcript,
le code extérieur immédiat, les mesures, les empreintes et les postflights.
Le sondage des métadonnées a vu **12 fichiers sur les 13 que le parcours réussi
crée** : il n'a pas aperçu `qualified-no-space.arguments.json` avant sa suppression.
Les 13 noms attendus sont le JAR et, pour chacun des quatre labels
`baseline-unquoted`, `qualified-01`, `qualified-02`, `qualified-no-space`, les
suffixes `.arguments.json`, `.stdout.log`, `.stderr.log`. La création/lecture du
dernier JSON est attestée par le succès de l'assertion interne, pas par une
observation externe de son fichier. Les listes « filesObserved » ne sont donc
pas présentées comme un inventaire externe exhaustif.

Le harnais garde en mémoire les identités de ses quatre enfants et vérifie
leurs codes de sortie ; il ne les imprime pas. Les PID individuels, leurs codes
numériques détaillés et leurs durées ne sont pas disponibles dans les preuves
persistantes. Le code zéro du harnais et ses assertions sont leur preuve indirecte.
Le postflight processus des captures repose sur le harnais, sans inventaire CIM
indépendant. Le `$PSVersionTable` conservé est celui du conducteur ; pour le
processus natif enfant, le chemin réel et la version de fichier ont été observés
et correspondent au même exécutable. Le harnais n'imprime pas sa propre table.

## Incident de conduite conservé, avant lancement

La première conduite `Run-WRH01.ps1` s'est arrêtée à 22:00:14 UTC pendant une
lecture CIM ajoutée pour observer le processus courant : `CimException`,
`HRESULT 0x80041003`, `PermissionDenied`, **« Accès refusé »**. Son code extérieur
est **1**, sa durée extérieure **584.6146 ms** ; `COMMAND=NOT_STARTED` et aucun
code natif de harnais ne lui est attribué. Le retour du prompt a été observé.
Cette tentative est intégralement conservée dans `run.json`, `transcript.txt`,
`outer-return.json`, `prompt-return.json` et les scripts initiaux.

Son message secondaire « Allowed source hashes changed » résulte d'une mauvaise
comparaison du conducteur initial entre une liste avant encore vide et la liste
après. **Il n'établit pas une modification des sources.** Les trois empreintes
exécutables relevées avant cette tentative sont inchangées, puis les douze
entrées sont comparées avant/après le véritable essai et lors du postflight séparé.

Une lecture ciblée du seul PID courant a confirmé le refus CIM, conservé dans
`cim-observation-block.json`. Le conducteur corrigé conserve le handle de son
propre enfant sans imposer cette lecture supplémentaire. Aucune configuration,
permission, source ou version de runtime n'a été modifiée. Le harnais a été lancé
**une seule fois** ; ce n'est pas une relance d'un essai du harnais échoué.
Le chemin d'arrêt forcé CIM du harnais n'a pas été sollicité ni qualifié ici.

## Confrontation aux preuves historiques WO-053

Les éléments suivants sont des constats **des rapports fournis**, sans nouvelle
exécution ni consultation de CI distante.

| Preuve | Observation et portée | Conclusion causale permise |
|---|---|---|
| WO-044 historique, implémentation `0b903b71ae70ccfb9b55f8d89bc8ac57eab0cbe3` | Cinq itérations corrigées rapportées ; sérialisation native observée par un captureur PowerShell | Défaut de découpage reproduit et correction de cette frontière qualifiée localement |
| Présent WR-H01 | Deux itérations corrigées, baseline et cas sans espace ; code 0, sous PowerShell 7.6.6 Windows client | Nouvelle corroboration bornée du mécanisme WO-044 ; aucune qualification CTRL_BREAK |
| WO-053 diagnostic, base `bbce3c5e643c4cc0840e2654a30142ea9ba136f4` | Instrumentation des phases création/readiness/signal/attente/nettoyage ; délais inchangés | Instrumentation pour classifier une panne future, aucune cause rétrospective établie |
| WO-053 run `33973934317`, commit `5f3dea2b72c0751174ea389891397e11bd9e08ae` | Deux jobs Windows réussis, Windows Server 2022, Temurin 25.0.4.1 ; `workflow_dispatch` de branche instrumentée | Le défaut initial du run `33972681012` n'est pas reproduit ; cause **NOT_ESTABLISHED** |
| WO-053 durcissement, base `c65a2d85b80d8f17f0bfa3cb3a5924732d3721ca` | Contre-épreuves du relais, filtrage et échec d'écriture ; build hôte ultérieur | Qualification de l'observation et de l'isolation des erreurs, pas correction causale de CTRL_BREAK |

Le diagnostic WO-053 distingue plusieurs résultats qu'il faut conserver :

- La première exécution locale R1 échoue parce que le nouveau mot `HARNESS`
  heurte la garde sur `HAR`. C'est un échec d'instrumentation. R2 emploie `CHILD`
  et réussit : 1193 tests, cinq skips natifs, Windows client / Oracle 25.0.4.
- Les deux essais Windows distants rapportent chacun 1193 tests sans échec ni
  erreur, un skip natif ; qualification J6 en 63.73 s puis 64.03 s. Ces valeurs
  historiques ne mesurent pas la présente reproduction.
- L'instrumentation et le déclencheur diffèrent du run initial en merge
  synthétique `pull_request` de la PR #29. L'instrumentation peut influer sur
  l'ordonnancement. Aucun succès ne prouve la disparition d'une intermittence,
  et `UNCLASSIFIED_FAIL_CLOSED` ne permet pas de déduire après coup une panne
  de readiness, d'envoi du signal, de résultat ou de nettoyage.
- Le job Linux du premier essai a échoué **avant les tests** sur le scan de
  commits historiques ; il n'a pas été rejoué lors du second essai Windows.
  Le run global n'est pas vert. Le rapport n'établit pas un faux positif.
- `FALLBACK_CLEANUP COMPLETED` décrit un teardown revenu sans exception. Cela
  ne remplace pas une preuve de disparition des identités exactes.

Le durcissement WO-053 vérifie le relais immédiat des seules lignes allowlistées,
leur conservation avant interruption, la non-divulgation des messages et la
préservation de l'erreur primaire lorsqu'un écrivain lève une `IOException`.
La méthode C# est extraite et compilée, avec panne injectée **sans exécution des
API Win32** dans cette contre-épreuve. Ce n'est pas une reproduction CTRL_BREAK.
Le rapport distingue un échec de redirection avant lancement et un échec Maven
avant compilation d'un build hôte ensuite réussi (1193 Surefire, 106 Failsafe,
PostgreSQL/Testcontainers isolé). Les anciens succès Windows distants ne
qualifient pas ce nouveau diff ; la cause reste `NOT_ESTABLISHED`.

Enfin, le rapport WO-044 déclare un écart historique : une commande Maven sans
`skipITs` a lancé des Testcontainers isolés, puis son nettoyage exact a été
vérifié. Cette observation appartient au rapport historique et n'a pas été
rejouée ici. `--offline` n'est pas une garantie d'absence de démarrage de base.

## Fichiers livrés et provenance

- **Conduite exécutée** : [Run-WRH01Harness.ps1](Run-WRH01Harness.ps1),
  [Invoke-WRH01Harness.ps1](Invoke-WRH01Harness.ps1).
- **Sorties et mesures** : [execution-transcript.txt](execution-transcript.txt),
  [execution.stdout.txt](execution.stdout.txt), [execution.stderr.txt](execution.stderr.txt),
  [execution.json](execution.json), [execution-outer-return.json](execution-outer-return.json).
- **Retour effectif et événements outil** :
  [execution-prompt-return.json](execution-prompt-return.json), [tool-events.json](tool-events.json).
- **Postflight séparé** : [Postflight-WRH01.ps1](Postflight-WRH01.ps1),
  [independent-postflight.json](independent-postflight.json).
- **Tentative préliminaire arrêtée** : `Run-WRH01.ps1`, `Invoke-WRH01.ps1`,
  `run.json`, `transcript.txt`, `harness.stdout.txt`, `harness.stderr.txt`,
  `outer-return.json`, `prompt-return.json`, `cim-observation-block.json`.

Les empreintes SHA-256 complètes avant/après figurent dans `execution.json` et
`independent-postflight.json`. Elles attestent les octets fournis et inchangés ;
aucun manifeste signé indépendant n'a été fourni. Le SHA de politique déclaré
par `input.json` est `6648dd423e556b5248b8793a539ae85a7680f9bc` : il n'est pas
présenté comme un SHA Git courant vérifié. Aucun accès Git n'a étendu la liste
de lecture. Les trois exécutables sont restés dans leur arborescence originale.
Les seules écritures persistantes sont les preuves et scripts sous
`output/runtime-artifacts/`. `output/runtime/` est conservé vide.
