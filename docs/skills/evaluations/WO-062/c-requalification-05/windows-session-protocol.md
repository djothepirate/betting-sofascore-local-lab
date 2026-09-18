# Complément opératoire C5 — sessions Windows bornées

Ce complément régit seulement les deux sessions Windows C5. Il complète le fragment
du cas reçu, sans résultat attendu ni oracle. Les sources, fixtures et preuves C4
restent inchangées. Le travail produit par la session reste sous
`output/runtime-artifacts/` et est exécuté dans cette même session.

## Règle de lecture et audit mécanique

- Lire le candidat exact une fois. Lire `request.txt`, ce complément et chaque entrée
  explicitement autorisée une fois au plus, par sections nécessaires. Les références
  présentes dans une entrée n'élargissent jamais ces accès.
- Pour WR-H01, `scripts/wo036/WO036-CampaignTools.psm1` est copié uniquement afin que
  le harnais original puisse l'importer durant son exécution. C'est une dépendance de
  runtime, pas une source à ouvrir manuellement. Lire seulement
  `inputs/wrh-module-extract.md`, qui restitue avec son empreinte le court extrait
  pertinent du module. Ne pas employer contre le module complet `Get-Content`, `-Raw`,
  `cat`, `type`, `ReadAllText`, glob, recherche ou une seconde commande textuelle.
  L'import interne du harnais figé ne constitue pas une lecture manuelle.
- Le lanceur et la revue construisent un `reading-audit.json` à partir des événements
  d'outils reçus. Il signale toute commande textuelle visant ce module et toute lecture
  répétée d'une entrée déclarée. Cet audit est organisationnel, séparé de l'oracle et
  des résultats fonctionnels de la sonde.
- Après les lectures nécessaires, préparer une conduite compacte et faire une seule
  tentative. Pas de sous-agent, session annexe, build, navigateur, réseau, Maven,
  Docker ou modification hors du répertoire de sortie isolé.

## Chaîne de fin obligatoire

Viser une réponse avant six minutes ; le watchdog externe reste quinze minutes. Les
bornes natives restent 60 secondes pour WR-H01 et 45 secondes pour WR-N01, préflight
et nettoyage compris. Mesurer les attentes avec `Stopwatch` et ne pas les prolonger.

Le déroulé est : lancement réel du conducteur, preuves, retour réel, commande courte
distincte `WR_PROMPT_RETURNED`, postflight distinct, lecture unique des résultats,
conclusion, réponse finale. Un marqueur de lancement ne peut apparaître qu'après le
succès de `Process.Start`. Les erreurs conservent l'étape, le type, le message,
l'identifiant, la ligne et la pile sans inventaire de l'environnement. Le lanceur
conserve les traces et les empreintes ; la session ne produit ni manifeste, ni README,
ni revue du skill.

## WR-H01 — harnais et frontière du module

Exécuter une seule fois le harnais original sous PowerShell 7 Windows avec
`-Iterations 2`, ses dépendances inchangées et `TEMP`/`TMP` de son enfant confinés sous
`output/runtime/`. Ne modifier aucun garde interne. Retenir l'objet `Process`, le
handle, PID, heure de création, image et arguments dès son lancement ; le PID du
conducteur est `$PID` et son image vient de son propre objet Process. Ne pas faire de
la lecture parentale WMI/CIM un prérequis ou un inventaire global.

La restitution explique la frontière des arguments, les chemins refusés et les
références historiques réellement lues, avec leurs SHA et différences de
déclenchement/instrumentation. Elle sépare ces constats de la reproduction C5 et
mentionne les écarts de commandes et de nettoyage établis. Une lecture de rapport ne
vaut jamais une nouvelle exécution de son contenu.

## WR-N01 — préflight du conducteur puis deux JVM détenues

Avant le conducteur complet, copier sans modification
`inputs/c5/wrn-conductor-template.ps1` sous
`output/runtime-artifacts/Invoke-WRN01.ps1`, puis exécuter cette copie avec
`powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File ...
-PreflightOnly`. Le préflight produit une preuve locale sans lancer Java : il
enregistre d'abord le runtime du processus puis teste les dépendances du conducteur,
observe éventuellement `Get-FileHash` sans en dépendre et valide son repli .NET
SHA-256. Si ce préflight échoue, le signaler jusqu'à la réponse finale sans relance
automatique. Sa réussite ne remplace pas les preuves de la conduite complète : une
nouvelle invocation normale de cette même copie répète les contrôles dans son propre
processus avant toute JVM.

Dans le conducteur complet, écrire `runtime_registered` avant toute empreinte. Puis
enregistrer les dépendances du processus concerné, y compris l'observation de
`Get-FileHash`, et employer le repli .NET SHA-256 auto-testé pour les empreintes de
fixture et d'exécutables. Aucun import de module, changement de `PSModulePath`,
installation ou modification de PATH n'est autorisé.

Le conducteur effectif est Windows PowerShell 5.1 Desktop et recherche les
`java.exe` par `Get-Command -All`. Il distingue relais et image directe, exige un
Java 25 direct unique puis vérifie sa version. Il lance exactement `failure` puis
`sleep`, avec UUID distinct. Pour chaque JVM, retenir immédiatement l'objet,
le handle, PID, heure de création, image et arguments ; la relation de création vient
du `Process.Start` détenu. Aucun ancien relevé ne prouve les deux nouveaux processus.

`failure` attend au plus 10 secondes et copie son code réel immédiatement. `sleep`
attend READY au plus 10 secondes, attend 1 500 ms à compter de READY, puis ne force
l'arrêt qu'après concordance handle/PID/UUID/image/commande. L'arrêt .NET Framework
attend au plus cinq secondes. Une identité absente ou divergente bloque l'arrêt forcé,
ne déclenche aucune recherche par nom et reste décrite comme telle. Le conducteur
respecte le budget total de 45 secondes, confirme les sorties par handles puis ne
supprime que ses propres fichiers/répertoires vides identifiés ; tout résidu inattendu
reste conservé comme preuve.
