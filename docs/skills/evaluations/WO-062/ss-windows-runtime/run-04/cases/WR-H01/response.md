## Résultat de la reproduction

**Le harnais WO-044 a été exécuté une fois dans cette session Windows, avec `-Iterations 2`. Il a terminé avec le code natif `0` et le résultat `PASS_LOCAL_FAIL_CLOSED`.** Le découpage du chemin non protégé a été reproduit ; les deux captures utilisant la protection du chemin ont reçu les arguments exacts attendus.

| Observation nouvelle | Résultat |
|---|---|
| Runtime | PowerShell **7.6.6 Core**, x64, Windows **10.0.26200** |
| Exécutable observé | `C:\Program Files\PowerShell\7\pwsh.exe` |
| Lancement confirmé après `Process.Start` | `2026-09-15T23:33:07.5540213Z` |
| Sortie native observée | `2026-09-15T23:33:10.2245221Z` |
| Code du harnais, copié immédiatement après sa sortie | **0** |
| Durée monotone du harnais | **2 686,1568 ms** |
| Durée de la conduite, préflight et vérification du nettoyage compris | **2 872,3827 ms**, dans le budget de 60 s |
| Commande suivante effectivement exécutée | `WR_PROMPT_RETURNED`, à `23:33:15.3798568Z` |
| Postflight indépendant | À `23:33:48.1453075Z` : **zéro entrée dans `output/runtime/`**, PID extérieur `8500` absent |
| Arrêt forcé | Aucun |

La commande native exécutée utilisait les chemins absolus, conservés intégralement dans `execution.json` :

```text
"C:\Program Files\PowerShell\7\pwsh.exe" -NoLogo -NoProfile -NonInteractive -File "<racine isolée>\scripts\wo044\Invoke-WO044JavaJarArgumentBoundaryQualification.ps1" -Iterations 2
```

Seuls `TEMP` et `TMP` de l’environnement enfant ont été fixés à `output/runtime/`. Les valeurs du parent sont restées inchangées. Les trois fichiers exécutables autorisés ont été utilisés à leur emplacement original ; leurs empreintes avant/après sont identiques.

## Ce que la capture démontre

Le transcript confirme :

- le découpage du chemin JAR contenant des espaces lorsqu’il n’est pas explicitement protégé ;
- **deux itérations corrigées réussies**, avec le chemin reçu comme un argument unique et exact après `-jar` ;
- le contrôle sans espace réussi ;
- l’identité d’instance, les trois flags JDK et l’ordre des arguments préservés ;
- **quatre refus sur quatre** : chemin relatif, extension incorrecte, guillemet et caractère de contrôle ;
- zéro processus détenu et zéro racine temporaire résiduels selon les contrôles du harnais.

Le mécanisme est précis : `Start-Process` recompose son tableau `ArgumentList` en ligne de commande native. Le helper protège par des guillemets la seule valeur canonique du chemin JAR. Son code refuse également les valeurs vides et les chemins non canoniques ; ces catégories supplémentaires n’ont pas toutes fait l’objet d’un essai dynamique dans cette exécution.

**Cette capture ne lance pas Java.** Elle qualifie la réception des arguments par un enfant PowerShell contrôlé. Elle ne qualifie donc ni un démarrage applicatif, ni le comportement effectif des flags JDK, ni PowerShell 5.1, ni CTRL_BREAK, ni la CI.

Les captures temporaires ont été supprimées par le harnais figé. La preuve persistante comprend son stdout intégral, son stderr vide, le code extérieur, les identités et durées de conduite, le retour de commande et le postflight. Les codes, PID et durées individuels des quatre enfants de capture ne sont pas exportés par ce harnais. Le postflight extérieur établit séparément la disparition du processus surveillé et l’absence de fichiers temporaires ; il ne constitue pas un inventaire global du poste.

## Confrontation avec les preuves historiques

### WO-044 : même frontière, mesures distinctes

Le rapport historique rattache la correction au commit **`0b903b71ae70ccfb9b55f8d89bc8ac57eab0cbe3`**, sur la base `96dec8492dbc043c7d6e4310195421f4411722df`. Ses **cinq** itérations historiques, résultats Pester et résultats Maven restent distincts des **deux** itérations corrigées exécutées ici.

L’écart historique demeure explicite : une relance `mvnw.cmd --offline clean verify` sans `-DskipITs` avait démarré Ryuk et PostgreSQL via Testcontainers malgré l’interdiction de démarrage de base. Après interruption, le postflight sur leurs identifiants exacts avait trouvé zéro résidu ; le conteneur primaire restait sain, sans redémarrage. La commande ensuite retenue avec `-DskipITs` avait réussi. **`--offline` ne neutralise pas les effets des tests.** Aucune commande Maven ou Docker n’a été rejouée ici.

### WO-053 : diagnostic qualifié, cause toujours non établie

Les deux succès Windows documentés concernent le run **`33973934317`**, au commit **`5f3dea2b72c0751174ea389891397e11bd9e08ae`** :

| Essai historique | Job Windows | Observation |
|---|---|---|
| 1 | `101327214290` | Succès ; J6 en 63,73 s |
| 2 | `101327999530` | Succès ; J6 en 64,03 s |

Ils utilisaient Windows Server 2022 et Temurin 25.0.4.1, avec les délais inchangés. Toutefois, ils portaient une instrumentation nouvelle et provenaient d’un **`workflow_dispatch` sur branche diagnostique**, alors que l’incident original concernait le merge synthétique de la PR #29. L’instrumentation peut modifier l’ordonnancement.

Ces essais **n’ont pas reproduit** le défaut du run initial **`33972681012`**. Sa cause reste **`NOT_ESTABLISHED`**. Le résultat `UNCLASSIFIED_FAIL_CLOSED` ne permet pas d’attribuer rétrospectivement l’incident à la readiness, au signal, au résultat ou au nettoyage.

Le job Linux associé, **`101327214159`**, avait échoué au scanner sensible **avant les tests** et n’avait pas été réexécuté lors du second essai. Le run global ne peut donc pas être présenté comme vert.

Enfin, le durcissement WO-053, fondé sur `c65a2d85b80d8f17f0bfa3cb3a5924732d3721ca`, vérifie le relais immédiat des marqueurs autorisés, le filtrage des sorties et l’isolation d’une panne d’écriture pendant le nettoyage. Ses contre-épreuves C# n’exécutent pas les API Win32 : **elles qualifient l’instrumentation, sans reproduire la panne CTRL_BREAK**. Son build hôte complet sans `skipITs` avait aussi exécuté des tests PostgreSQL/Testcontainers isolés ; les anciens succès Windows CI ne qualifient pas ce nouveau diff.

**La nouvelle reproduction établit donc le mécanisme de frontière d’arguments WO-044. Elle n’établit aucun lien causal avec l’incident WO-053.**

## Livrables

Tous les fichiers sont sous [`output/runtime-artifacts/`](<C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-04/WR-H01/output/runtime-artifacts/>).

- **Scripts exécutés** : `Invoke-WR-H01.ps1` et `Test-WR-H01Postflight.ps1`.
- **Mesures et commandes** : `execution.json`, `commands.txt`.
- **Sorties intégrales** : `harness.stdout.txt`, `harness.stderr.txt`.
- **Retour et nettoyage** : `prompt-returned.txt`, `postflight.json`, `evidence-available.txt`, `conclusion.txt`.
- **[Diagnostic détaillé](<C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-04/WR-H01/output/runtime-artifacts/diagnostic.md>)** : analyse, limites, références historiques, SHA et écarts d’exécution.

Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.