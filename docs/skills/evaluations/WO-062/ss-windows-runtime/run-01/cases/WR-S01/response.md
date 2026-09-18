## Skill retenu : `ss-windows-runtime`

**Chemin effectivement lu :**  
[ C:\Dev\BettingProject\codex\betting-sofascore-local-lab\.tmp\wo062-skills-lot2\.tmp\wo062-evaluations\ss-windows-runtime\run-01\WR-S01\.agents\skills\ss-windows-runtime\SKILL.md ](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-01/WR-S01/.agents/skills/ss-windows-runtime/SKILL.md)

**Lecture :** fichier lu intégralement avec l’outil `exec_command`, par `Get-Content -LiteralPath … -Raw`. La commande a réussi et son résultat n’a pas été tronqué.

**Raison de la sélection :**

- La demande invoque explicitement `$ss-windows-runtime`, présent dans le catalogue disponible.
- Son périmètre correspond précisément à un incident de lanceur Windows du **SofaScore Local Lab**, impliquant **PowerShell 5.1** et un **enfant Java**.
- Son contenu traite directement du symptôme décrit : observer les codes à chaque frontière, distinguer `$LASTEXITCODE` du `Process.ExitCode`, et ne pas considérer le zéro du script extérieur comme une preuve de réussite de l’enfant.

**Aucun autre skill retenu.** `ss-verify` serait pertinent pour une vérification du dépôt ou un diagnostic de build ; `ss-ci-security` pour une qualification CI. Ces travaux ne sont pas demandés ici.

**Routage terminé.** Aucun diagnostic métier n’a été exécuté. Seul le fichier `SKILL.md` indiqué a été lu ; aucun oracle, résultat antérieur, inventaire commenté ou plan d’évaluation n’a été consulté.