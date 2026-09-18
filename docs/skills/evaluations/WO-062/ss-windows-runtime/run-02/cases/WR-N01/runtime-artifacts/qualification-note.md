# WR-N01 — Qualification locale interrompue, preuves de cette session

Statuts conservés : **EXPERIMENTAL — LOCAL_ONLY — NOT_PRODUCTION_APPROVED — NO_CRITICAL_DEPENDENCY**.

## Verdict

**Qualification des deux modes NON ACQUISE.** L'exécution réelle a validé le runtime Windows PowerShell 5.1 et observé Java 25, puis le mode `failure` a rendu un code natif 23 à la frontière du lanceur détenu. Le PID émis par la fixture diffère du PID conservé par la conduite. Cette rupture d'identité a arrêté la conduite avant `sleep`. Aucune expiration après READY ni terminaison forcée n'a donc été mesurée. Aucun retry, substitution de runtime ou relèvement de délai n'a été effectué.

Il ne s'agit pas d'un Java 25 absent. La limite est la propriété incomplète de la chaîne de processus découverte via `javapath`, plus un résidu inattendu. Le nettoyage intégral n'est pas acquis.

## Session et runtime effectivement observés

- Session : `66a0391d-820d-4d03-b7f1-e3c17b4ce664`.
- Exécution le 15 septembre 2026 vers 21:59:26–21:59:28 UTC, soit 23:59:26–23:59:28 Europe/Paris. Le postflight indépendant a eu lieu le 16 septembre à 00:00:45 Europe/Paris.
- Outil appelant : PowerShell Core 7.6.6, `C:\Program Files\PowerShell\7\pwsh.exe`. Son PID, sa création, son répertoire et la commande complète sont dans `outer-result.json`.
- Conduite : **Windows PowerShell 5.1.26100.9444, Desktop**, CLR `4.0.30319.42000`, `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe`, PID 39520. `$PSHOME`, `$PSVersionTable`, version de fichier et répertoires observés sont dans `result.json`.
- Windows observé par l'API : `Microsoft Windows NT 10.0.26200.0`, Win32NT, OS et processus 64 bits, AMD64.
- Application Java découverte par `Get-Command -CommandType Application` : `C:\Program Files\Common Files\Oracle\Java\javapath\java.exe`, version de fichier `25.0.4.0`.
- Sortie réelle de `--version` : `java 25.0.4 2026-07-21 LTS`, Java(TM) SE Runtime Environment `25.0.4+7-LTS-189`, HotSpot 64-Bit Server VM. Code 0 conservé depuis `Process.ExitCode` immédiatement après l'attente ayant confirmé la sortie.
- Le chemin de la JVM exécutant effectivement la fixture n'a pas été établi : le chemin découvert est celui du lanceur détenu. Aucun chemin JDK n'a été inventé.
- Chronomètres monotones `Stopwatch`, haute résolution, fréquence 10 000 000 Hz. Horodatages UTC conservés en parallèle.

## Résultats et durées

| Étape | Observation réelle | Durée monotone |
|---|---|---:|
| Java `--version` | Code natif du processus détenu **0**, Java 25.0.4 | 213,906 ms jusqu'à la capture de sortie |
| Préflight total | PowerShell 5.1 et version Java observés | 1 089,8402 ms depuis le début de conduite |
| `failure` | Code natif du lanceur détenu **23** | 470,7955 ms depuis le début du lancement |
| READY de `failure` | Présent dans stdout, drainé après la sortie | Observé à 486,4807 ms ; ce n'est pas une mesure de disponibilité en direct |
| `sleep` | **NON LANCÉ**, rupture d'identité préalable | Aucune durée ni expiration disponible |
| Finalisation/postflight interne | Handles détenus sortis ; résidu conservé | 33,1145 ms |
| Conduite PS5.1 | Code **2**, `OBSERVATION_OR_CLEANUP_FAILURE` | 1 659,3235 ms au point de mesure du rapport ; dernier événement à 1 667,9606 ms |
| Invocation externe jusqu'au retour PS5.1 | `$LASTEXITCODE` copié immédiatement : **2** | 2 171,9922 ms |
| Appel outil | Code outil **1**, distinct des codes Java et PS5.1 | 2,6247308 s |
| Postflight indépendant PS5.1 | Code **0** de l'observation, résidu toujours présent | 348,1834 ms de script |

Les budgets de l'entrée sont conservés dans le script : version 5 s, failure 10 s, readiness 10 s, 1 500 ms après READY, nettoyage processus 5 s, global 45 s. La conduite interrompue est restée dans son budget. Le postflight indépendant est une observation ultérieure, pas une prolongation ou une reprise de la sonde.

## Frontière de processus et sorties

Processus version : PID **32700**, handle **2664**, création `2026-09-15T21:59:27.0833248Z`.

Mode failure : UUID `a7dbc7be-ca57-4771-b3d3-aafbf30f04cd`, processus détenu PID **34004**, handle **2420**, création `2026-09-15T21:59:27.3988876Z`. L'image du mode failure n'a pas pu être relevée via `MainModule` ; la valeur enregistrée est nulle. Le chemin demandé est conservé séparément.

Sortie intégrale de la fixture :

```text
WR_PROBE_RUN=a7dbc7be-ca57-4771-b3d3-aafbf30f04cd
WR_PROBE_PID=26528
WR_PROBE_TEXT=café équipe
WR_PROBE_READY
WR_PROBE_NATIVE_FAILURE
```

L'UUID correspond, mais **26528 ≠ 34004**. Le code 23 est donc prouvé pour le processus natif détenu à la frontière `javapath`, pas par un handle directement conservé de la JVM 26528. L'existence d'une frontière de processus supplémentaire est observée ; son mécanisme précis n'a pas été inspecté. Le garde `Fixture PID mismatch` a interrompu la suite.

Les flux ont été lus de manière asynchrone avec un décodeur UTF-8 explicite et strict. Le texte accentué a également été vérifié par relecture UTF-8 stricte de la preuve produite. Stderr est vide pour les deux processus lancés. Les preuves texte sont en UTF-8 sans BOM. Aucun code après `Kill()` n'existe : aucun processus n'a été arrêté.

## Retour effectif du prompt et codes des couches

Après le retour de l'appel outil de qualification `aa7819`, un appel outil séparé `88e060` a exécuté une commande courte et produit :

```text
WR_PROMPT_RETURNED 2026-09-15T21:59:41.1832529Z
```

Ce marqueur est postérieur au retour ; il n'a pas été annoncé avant le lancement. L'événement outil et sa sortie ont été observés dans cette conversation ; `prompt-returned.txt` et `tool-observations.json` les relient aux preuves. Le code outil 1 ne remplace ni le code natif 23 ni le code PS5.1 2. La raison exacte de cette différence de code externe n'est pas établie.

## Propriété, environnement et postflight

Racine créée neuve, avec espaces :

```text
output/runtime/WR native probe 66a0391d-820d-4d03-b7f1-e3c17b4ce664/
```

La conduite a vérifié le chemin Windows absolu, le préfixe canonique suivi d'un séparateur, l'absence de cible préexistante, de composant `..`, de guillemet/caractère de contrôle et de jonction/lien dans la chaîne. Seuls cette racine et son sous-répertoire `temp` étaient prévus comme ressources jetables.

Pour chaque enfant Java, la copie d'environnement fixe TEMP/TMP sous `temp`, supprime JAVA_TOOL_OPTIONS, _JAVA_OPTIONS, JDK_JAVA_OPTIONS et CLASSPATH sans révéler leurs valeurs, et laisse PSModulePath hérité. Le répertoire de travail et user.home restent dans la racine, java.io.tmpdir dans `temp`, avec `-XX:-UsePerfData`. Les variables parent ciblées sont inchangées. Aucune configuration utilisateur/machine n'a été modifiée.

Dans `finally`, les handles 32700 et 34004 ont confirmé leur sortie avant libération. À `2026-09-15T22:00:45Z`, le postflight indépendant n'a trouvé aucun des PID observés 32700, 34004 et 26528. Pour 26528, cette absence ponctuelle ne remplace pas la preuve manquante du handle et de l'heure de création à l'origine. Aucun arrêt global ou ciblé n'a été exécuté.

**Résidu conservé :** `temp/JavaLauncher.log`, 4 892 octets, créé à `2026-09-15T21:59:27.1308683Z`, dernière écriture à `2026-09-15T21:59:27.8625526Z`. Son contenu n'a pas été lu. Son apparition dans la destination neuve est observée ; l'auteur exact n'a pas été tracé. La racine et `temp` restent présents. Aucun effacement récursif n'a été tenté.

Cette conservation applique le protocole WR-N01 §9 : « Refuser tout résidu inattendu ; ne pas le supprimer récursivement. » Le candidat utilisé, [ss-windows-runtime](../../.agents/skills/ss-windows-runtime/SKILL.md), précise : « Un résidu inattendu se signale, il ne déclenche pas un effacement récursif plus large. » La qualification interrompue n'a pas été relancée, conformément au §8 du protocole : « Aucun retry automatique ni allongement de borne pour obtenir un succès. »

## Provenance, périmètre et conclusion exploitable

Fixture inchangée, SHA-256 avant/après :

```text
4e29c5867eba0fb76518de2307391449c83450449bea0393179738505c69bc8b
```

Le script effectivement exécuté et son hash sont conservés ; il n'a pas été modifié après cet essai. Les commandes complètes, arguments quotés, UUID, répertoires, handles, dates et codes se trouvent dans `result.json`, `events.jsonl.txt` et `outer-result.json`. Aucun SHA Git courant n'est revendiqué : les données Git n'appartenaient pas à la liste de lecture autorisée. Le commit de politique dans input.json n'est pas assimilé au SHA de cette session.

Les sources autorisées indiquent Java 25, Spring Boot 4.1.0, le wrapper Maven configuré pour 3.9.16 et l'adresse applicative 127.0.0.1. Ces fichiers ont été lus, pas exécutés. Aucun Maven, Docker, navigateur, fournisseur, HTTP, application, base, CI, installation ni source modifiée. Les autres références citées par le skill n'ont pas été ouvertes.

La condition manquante pour qualifier l'expiration est une chaîne Java dont la JVM portant l'UUID est détenue et identifiée dès le lancement. Le nom `java.exe`, la sortie Java 25 et le code du relais ne suffisent pas à cette preuve. Cette session démontre le retour natif observé, le retour du prompt et le refus de poursuivre sur une identité incohérente ; elle **ne démontre pas** le timeout de sleep, sa terminaison forcée, l'absence de tout résidu, la stabilité générale du poste ou celle du Lab.

## Preuves conservées

- `Invoke-WRN01.ps1` : conduite PS5.1 des deux modes, interruption effectivement observée avant sleep.
- `Invoke-Qualification.ps1` : invocation du vrai powershell.exe et capture immédiate du code externe.
- `Observe-Postflight.ps1` : observation indépendante PS5.1 sans mutation des ressources.
- `result.json`, `events.jsonl.txt`, `conductor-transcript.txt`, `outer-result.json` : événements, commandes et résultats bruts.
- `version.stdout.txt`, `version.stderr.txt`, `failure.stdout.txt`, `failure.stderr.txt` : sorties observées.
- `prompt-returned.txt`, `tool-observations.json`, `independent-postflight.json` : retour effectif et état ultérieur.
- `provenance.json` : hashes des entrées autorisées et scripts de cette session.