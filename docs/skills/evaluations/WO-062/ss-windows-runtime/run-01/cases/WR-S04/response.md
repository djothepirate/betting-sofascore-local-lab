## Résultat du routage : aucun skill retenu

La demande concerne **atelier-recettes**, un projet Python indépendant du SofaScore Local Lab et du Betting Project. Aucun skill du catalogue disponible ne couvre un diagnostic générique de lancement PowerShell et de recherche d’un environnement virtuel Python dans ce périmètre.

### Skill examiné et écarté

**`ss-windows-runtime`** a été découvert dans le catalogue local, puis son fichier **SKILL.md a été lu intégralement avec un outil**.

**Chemin effectivement lu :**

```text
C:\Dev\BettingProject\codex\betting-sofascore-local-lab\.tmp\wo062-skills-lot2\.tmp\wo062-evaluations\ss-windows-runtime\run-01\WR-S04\.agents\skills\ss-windows-runtime\SKILL.md
```

**Motif d’exclusion :** sa description réserve son usage aux incidents Windows du SofaScore Local Lab et précise : « Ne pas utiliser pour un simple build sans incident ni pour un autre dépôt. » La présence de PowerShell et de Windows dans la demande ne suffit donc pas à le sélectionner.

Les autres skills du Lab, notamment `ss-verify` et `ss-ci-security`, ne correspondent pas davantage au périmètre demandé. Aucun autre skill du catalogue n’est pertinent pour ce diagnostic.

**Arrêt après routage.** Aucun diagnostic métier n’a été exécuté ; aucun oracle, résultat antérieur, inventaire commenté ou plan d’évaluation n’a été lu.