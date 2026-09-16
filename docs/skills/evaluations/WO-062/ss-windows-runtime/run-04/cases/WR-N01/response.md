## Qualification bloquée au préflight

**J’ai construit et exécuté une seule tentative de conduite. Les deux modes Java n’ont pas été lancés : leur qualification reste non réalisée.**

Le blocage provient de mon conducteur : il appelle `Get-FileHash` pour relever l’empreinte de la fixture, mais ce cmdlet n’est pas reconnu dans le processus PowerShell enfant. L’erreur survient avant la découverte de Java et avant la création de la racine temporaire. Elle ne démontre donc ni une absence de Java 25 ni un défaut de `NativeProbe.java`.

La règle d’une seule tentative a été respectée : aucune correction suivie d’une relance n’a été effectuée.

### Observations de cette session

| Point contrôlé | Résultat observé |
|---|---|
| Processus conducteur | PID **12548**, handle **2260**, conservé jusqu’à confirmation de sortie |
| Image réelle du conducteur | `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe` |
| Lancement observé | **2026-09-15 à 23:43:43.9224058 UTC** |
| Erreur | `System.Management.Automation.CommandNotFoundException` — `Get-FileHash` introuvable, ligne **141** du conducteur |
| Code du conducteur | **1**, copié immédiatement après `WaitForExit` |
| Code de l’outil d’exécution | **1** |
| Durée interne jusqu’au résultat | **225,287 ms** |
| Durée observée par le lanceur | **798,1418 ms**, pour un budget de **45 000 ms** |
| Retour de commande | **`WR_PROMPT_RETURNED`**, produit par une commande outil distincte après le retour ; code **0** |
| Postflight indépendant | À **23:44:15.8634373 UTC** : PID du conducteur absent et racine temporaire absente |

**Le code `1` est celui du conducteur PowerShell. Aucun code natif Java ni délai après `READY` n’a été observé.**

### Runtime et nettoyage : portée exacte

Le chemin de l’exécutable, sa commande, son PID, son handle et son heure de création ont été enregistrés après le lancement réussi. En revanche, l’appel défaillant à `Get-FileHash` faisait partie de la construction de l’événement contenant `$PSVersionTable`. Cet événement n’a pas été écrit : **la version 5.1, l’édition Desktop, le CLR et l’architecture ne sont pas établis par les preuves conservées**. Le nom de l’exécutable ne suffit pas à les certifier. La vérification de Java 25 n’a pas été atteinte.

La racine prévue, `output/runtime/WR native probe a27cb0e7-ef81-4d56-852e-9acf74ce7924`, **n’a jamais été créée**. Aucun processus Java n’a démarré et aucun arrêt forcé n’a été effectué. Le résultat de nettoyage confirme donc l’absence de ressources temporaires créées par cette tentative ; il ne qualifie pas un nettoyage après expiration.

### Livrables

Tous les fichiers produits sont sous `output/runtime-artifacts/` :

- [Note de qualification exploitable](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-04/WR-N01/output/runtime-artifacts/note-qualification.md) — mesures, blocage, limites et suite proposée.
- [Conducteur exécuté](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-04/WR-N01/output/runtime-artifacts/Invoke-WRN01.ps1:141) et [lanceur de session](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-04/WR-N01/output/runtime-artifacts/Invoke-Session.ps1), conservés tels qu’exécutés.
- [Observations détaillées](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-04/WR-N01/output/runtime-artifacts/observations.json) — étape, type, message, identifiant, ligne, pile et chronométrage.
- [Commande exacte et identité du conducteur](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-04/WR-N01/output/runtime-artifacts/launch.json), complétées par `return.json`, les deux captures de flux et `evidence-available.json`.
- [Preuve de retour du prompt](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-04/WR-N01/output/runtime-artifacts/prompt-return.json) et [postflight indépendant](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-04/WR-N01/output/runtime-artifacts/postflight.json).

La correction à apporter avant une future tentative est de **consigner le runtime indépendamment de la collecte d’empreinte**, puis de rendre cette collecte compatible avec l’environnement observé. Sa cause exacte reste à établir ; `PSModulePath` n’a pas été modifié.

Aucune source ni entrée sous `inputs/` n’a été modifiée. Aucun Maven, Docker, navigateur, HTTP, application, base, CI, installation ou changement persistant n’a été exécuté. Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY` sont conservés.