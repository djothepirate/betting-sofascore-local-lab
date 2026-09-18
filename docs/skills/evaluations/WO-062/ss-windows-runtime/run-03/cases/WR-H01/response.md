## Résultat : blocage avant lancement du harnais

**La reproduction WO-044 demandée n’a pas été réalisée.** Mon script de conduite s’est arrêté pendant le préflight avec le message **« Accès refusé »**, avant la création du processus exécutant le harnais. Aucun résultat actuel de qualification de la frontière d’argument ne peut donc être annoncé.

Le candidat local a été lu intégralement. La session a observé **PowerShell 7.6.6, édition Core**, sous **Windows 10.0.26200** ; `Get-Command` a résolu `C:\Program Files\PowerShell\7\pwsh.exe`, version de fichier **7.6.6.0**. Le blocage ne démontre donc pas une absence de PowerShell 7.

### Mesures et retour effectif

| Observation actuelle | Résultat |
|---|---|
| Commande de conduite exécutée | `& '.\output\runtime-artifacts\Invoke-WRH01.ps1'` |
| Début enregistré | `2026-09-15T22:44:17.1928600Z` |
| Résultat principal | `BLOCKED` — « Accès refusé » |
| Durée monotone enregistrée par la conduite | **194,609 ms** |
| Nombre de lancements du harnais | **0** |
| Code natif du harnais | **Absent**, aucun processus lancé |
| Code de sortie de l’outil de conduite | **0** : l’erreur a été interceptée et enregistrée |
| Commande suivante | `WR_PROMPT_RETURNED`, observé à **22:44:24.4114165Z** |
| Postflight indépendant | **0 entrée** dans `output/runtime/`, en **85,866 ms** |

Le code extérieur zéro ne signifie pas que le test a réussi. Le résultat principal conservé est bien `BLOCKED`.

**Limite de ma conduite :** elle n’a conservé que le message d’exception, sans la ligne fautive ni la pile d’appel. L’opération précise ayant reçu le refus d’accès reste donc non établie. Aucun stdout/stderr natif, PID enfant, argument reçu ou temps d’exécution du harnais n’existe pour cet essai. Le marqueur `EXECUTION_START`, placé avant le lancement natif, n’a pas été atteint ; cette absence est explicitement consignée.

Le postflight confirme que `output/runtime/` est vide. Aucune racine temporaire du harnais ni aucun processus enfant n’a été créé ; aucune suppression ni aucun arrêt n’a été nécessaire. Le complément opératoire demande qu’une impossibilité produise un constat final : aucune relance n’a été effectuée.

## Confrontation aux preuves historiques

**WO-044 établissait une cause précise à sa frontière d’arguments.** Le rapport décrit la recomposition de `Start-Process -ArgumentList` : sans protection explicite, le chemin contenant des espaces arrive découpé dans l’enfant de capture. Le helper entoure le chemin canonique de guillemets et le harnais vérifie sa réception exacte après `-jar`, l’identité d’instance et les trois arguments JDK. Les **cinq itérations réussies du rapport sont historiques**. Les deux itérations demandées ici n’ont pas été exécutées.

Même réussi, ce harnais qualifierait une capture par un enfant PowerShell : il ne démarre ni Java ni un JAR applicatif. L’écart historique WO-044 impliquant Testcontainers appartient à une commande Maven distincte ; aucune commande Maven ou Docker n’a été exécutée ici.

**WO-053 conserve une cause non établie.** Ses deux essais Windows instrumentés ont réussi sans reproduire le défaut CTRL_BREAK original. Ils utilisaient `workflow_dispatch` sur une branche diagnostique, avec une instrumentation différente du run initial sur merge synthétique de PR. Ils ne démontrent ni correction causale ni disparition d’une intermittence. Le job Linux associé avait échoué au scan avant les tests : le run global ne doit pas être présenté comme entièrement vert.

La correction de revue WO-053 qualifie notamment le relais des marqueurs et la résistance de l’observation aux erreurs d’écriture. Sa contre-épreuve C# n’exécute pas les API Win32 et ne reproduit pas la panne CI. Les anciens succès Windows ne qualifient pas automatiquement ce diff ultérieur. **La cause originale reste `NOT_ESTABLISHED`.**

Le refus d’accès de cette session est un nouvel échec de conduite avant lancement ; il n’établit aucun lien causal avec CTRL_BREAK ou avec le découpage du chemin JAR.

## Artefacts livrés

Tous les fichiers créés sont sous `output/runtime-artifacts/` :

- [Script de conduite exécuté](output/runtime-artifacts/Invoke-WRH01.ps1)
- [Résultat et durées](output/runtime-artifacts/result.json)
- [Contexte, codes extérieurs et preuves manquantes](output/runtime-artifacts/execution-context.json)
- [Marqueur de retour](output/runtime-artifacts/prompt-return.txt)
- [Script de finalisation](output/runtime-artifacts/Complete-WRH01.ps1) et [postflight indépendant](output/runtime-artifacts/postflight.json)
- [Journal UTC des étapes atteintes](output/runtime-artifacts/stages.txt)

Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.