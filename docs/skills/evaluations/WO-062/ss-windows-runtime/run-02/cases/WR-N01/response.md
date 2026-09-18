**L’essai a été exécuté, mais la qualification des deux modes reste incomplète.** Le mode `failure` a rendu le code **23** ; une incohérence d’identité de processus a interrompu la conduite avant `sleep`. Les preuves de cette session et la note de diagnostic sont conservées.

### Résultats observés

| Contrôle | Résultat |
|---|---|
| Runtime de conduite | **Windows PowerShell 5.1.26100.9444 — Desktop**, Windows natif, AMD64 |
| Java effectif annoncé | **Java 25.0.4 LTS**, HotSpot 64 bits |
| Préflight Java | Code natif **0**, capturé immédiatement après sortie ; **213,906 ms** |
| Mode `failure` | Code natif du processus détenu **23**, capturé immédiatement ; **470,796 ms** |
| Mode `sleep` | **Non lancé** après l’échec du contrôle d’identité |
| Expiration après READY / arrêt forcé | **Non qualifiés** |
| Retour de la conduite PS5.1 | Code **2**, capturé immédiatement par le lanceur ; retour en **2 171,992 ms**, dans le budget de 45 s |
| Retour du prompt | Marqueur `WR_PROMPT_RETURNED` produit par un appel outil distinct, après le retour |
| Nettoyage intégral | **Non acquis : un résidu inattendu a été conservé** |

### Blocage établi

`Get-Command` a résolu Java vers :

```text
C:\Program Files\Common Files\Oracle\Java\javapath\java.exe
```

Pour `failure`, la conduite détenait le processus **34004**, tandis que la fixture a imprimé **`WR_PROBE_PID=26528`**, avec le bon UUID d’essai. Le code **23** est donc prouvé à la frontière du lanceur détenu ; le handle de la JVM exécutant la fixture n’a pas été conservé. La conduite a signalé **`Fixture PID mismatch`** et interrompu la suite.

Le runtime Java 25 est présent. La propriété de cette chaîne de processus reste insuffisamment établie pour qualifier l’arrêt forcé demandé.

### Postflight et résidu

Les deux handles détenus ont confirmé leur sortie avant libération. Le postflight indépendant n’a trouvé aucun des trois PID observés — **32700, 34004 et 26528**. Pour ce dernier, cette absence ponctuelle ne remplace pas le handle et l’heure de création manquants.

Le fichier suivant subsiste, avec sa racine temporaire :

```text
output/runtime/WR native probe 66a0391d-820d-4d03-b7f1-e3c17b4ce664/temp/JavaLauncher.log
```

Il mesure **4 892 octets**. Son contenu n’a pas été lu. Aucun processus n’a été arrêté et aucun effacement récursif n’a été effectué.

Le protocole interdit les relances automatiques. Le [candidat ss-windows-runtime utilisé](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-02/WR-N01/.agents/skills/ss-windows-runtime/SKILL.md) impose également : « Un résidu inattendu se signale, il ne déclenche pas un effacement récursif plus large. » Ces règles expliquent l’absence de nouvel essai et la conservation du résidu.

### Livrables

- [Note de qualification et diagnostic complet](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-02/WR-N01/output/runtime-artifacts/qualification-note.md)
- [Résultats structurés : commandes, runtime, codes, identités et durées](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-02/WR-N01/output/runtime-artifacts/result.json)
- [Script de conduite PS5.1 effectivement exécuté](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-02/WR-N01/output/runtime-artifacts/Invoke-WRN01.ps1)
- [Postflight indépendant](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-windows-runtime/run-02/WR-N01/output/runtime-artifacts/independent-postflight.json)

La fixture est inchangée, avec SHA-256 identique avant/après. Aucun Maven, Docker, navigateur, HTTP, application, base, installation ou changement persistant n’a été exécuté. Les statuts **EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY** sont conservés.