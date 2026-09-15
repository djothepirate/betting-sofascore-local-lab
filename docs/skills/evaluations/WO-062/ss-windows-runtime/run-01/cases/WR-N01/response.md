**Qualification WR-N01 bloquée avant exécution.** Les outils locaux ont échoué dès la tentative de lecture du candidat obligatoire. Je n’ai donc pu ni lire le protocole, ni construire et exécuter la conduite des deux modes. Aucun résultat natif n’est revendiqué.

### Blocage observé dans cette session

Deux tentatives de lecture intégrale du candidat ont été soumises :

```powershell
Get-Content -LiteralPath '.agents/skills/ss-windows-runtime/SKILL.md' -Raw
```

La première utilisait le shell par défaut ; la seconde demandait explicitement `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe`, avec `login:false`. Toutes deux ont échoué **avant la création du processus**, avec la même erreur :

```text
CreateProcess: Rejected("Failed to create unified exec process:
helper_unknown_error: setup refresh had errors")
```

Deux tentatives de lecture du même fichier avec l’autre outil local ont également échoué :

```text
trusted Node process exited unexpectedly; kernel reset, rerun your request
```

Enfin, l’écriture d’une note de blocage sous `output/runtime-artifacts/WR-N01-session-blocked.md` a échoué avec `Failed to write file`. **Aucun artefact de preuve n’a pu être confirmé sur disque.**

### État de la qualification

| Élément demandé | Observation |
|---|---|
| Lecture intégrale du candidat | Bloquée ; aucun contenu obtenu |
| Windows PowerShell 5.1 et Java local 25 réels | Non identifiés |
| Exécution des deux modes | Non réalisée |
| Codes natifs immédiatement capturés | Non observés |
| Expiration et durées monotones | Non observées |
| Retour du prompt PowerShell | Non observé |
| Nettoyage et postflight | Non exécutés, non vérifiés |

Aucun processus d’essai Java n’a été lancé et aucun arrêt de processus n’a été effectué. Ces erreurs établissent un **blocage de l’outillage de la session**, sans permettre de conclure que PowerShell 5.1 ou Java 25 est absent.

La reprise nécessite une session dont l’accès local aux fichiers et le lancement de PowerShell fonctionnent. Elle devra commencer par la lecture intégrale du candidat exact, puis produire de nouvelles preuves d’exécution. Cette réponse constitue uniquement le constat du blocage, **pas une qualification du scénario**.