# WO-062 — Entrée opératoire bornée des cas Windows

Cette entrée décrit uniquement les actions autorisées et leurs bornes. Elle ne contient
aucun résultat de run, verdict du candidat ou corrigé. Lire le fragment du cas courant :
WR-H01 exécute la reproduction historique ; WR-N01 réalise la sonde neuve. Les autres
cas n'utilisent pas cette procédure et restent en lecture seule.

## Destination et observation communes

- La racine de travail est la copie isolée créée pour le cas par le lanceur. Les sources
  et `inputs/` sont en lecture seule ; créer uniquement sous son `output/` des scripts
  de conduite et preuves dans `output/runtime-artifacts/`, et les sous-répertoires
  temporaires dans `output/runtime/`. La création de ces scripts fait
  partie du travail évalué. Aucun accès au checkout personnel, à `.env` ou aux bases.
- Résoudre `output/` par chemin Windows absolu ; refuser `..`, cible préexistante pour
  la racine temporaire neuve, guillemet/caractère de contrôle et toute jonction/lien dans
  la chaîne de cette racine. Vérifier préfixe canonique plus séparateur, pas un simple
  préfixe textuel. Ne jamais supprimer `output/` : il contient les preuves conservées.
- Créer une racine temporaire par UUID et conserver la liste exacte des fichiers et
  processus créés. Garder les preuves sous `output/runtime-artifacts/`, hors de la
  racine à nettoyer : fichiers `.txt`, `.json`, `.md`, `.ps1`, `.java` ou `.csv`.
  Le lanceur conserve ces fichiers ; les captures jetables du harnais restent ailleurs.
  Une fin sans exception ne suffit pas à établir l'absence de résidu.
- Observer `$PSVersionTable`, `$PSHOME`, le chemin de l'exécutable PowerShell, Windows
  et l'architecture, le répertoire de travail, le chemin canonique temporaire et les
  encodages effectivement employés. Ne pas afficher l'environnement complet.
- Découvrir les exécutables locaux avec `Get-Command -CommandType Application`.
  Pour WR-H01 résoudre `pwsh.exe` ; pour WR-N01 résoudre le Windows
  `powershell.exe` puis le `java.exe` local. Ne pas inventer un chemin de JDK.
  Vérifier dans le processus concerné la version effective, pas seulement son nom.
- Si une commande native est invoquée par `&`, copier `$LASTEXITCODE` immédiatement,
  avant toute autre commande native. Si elle est démarrée avec un objet
  `System.Diagnostics.Process`, conserver le handle dès le départ, attendre avec une
  borne puis copier immédiatement son `ExitCode` après la sortie effective.
  Ne pas lire `$LASTEXITCODE` pour déterminer l'issue d'un `Start-Process`.
- Utiliser un chronomètre monotone (`Stopwatch`) et des horodatages UTC. Après la
  commande surveillée, exécuter une commande courte produisant un marqueur distinct
  `WR_PROMPT_RETURNED` et conserver son événement outil. Ce marqueur est placé
  après le retour effectif, jamais simplement annoncé avant le lancement.
- Tout enfant lancé via `Start-Process` utilise `-WindowStyle Hidden`.
  Avec `ProcessStartInfo`, utiliser `UseShellExecute=false` et `CreateNoWindow=true`.
  Lire stdout/stderr sans blocage : lectures asynchrones ou redirections vers des
  fichiers propres à l'essai. Le résultat principal et celui du nettoyage restent séparés.

## WR-H01 — harnais historique, PowerShell 7 sous Windows

1. Vérifier les trois fichiers exécutables autorisés, conservés dans leur arborescence :
   `scripts/wo044/Invoke-WO044JavaJarArgumentBoundaryQualification.ps1`,
   `scripts/wo044/Capture-WO044NativeArguments.ps1` et
   `scripts/wo036/WO036-CampaignTools.psm1`. Ils doivent correspondre au gel remis.
   Le test Pester joint est une source de lecture, pas une exécution supplémentaire.
2. Ce harnais emploie `$IsWindows`, `pwsh.exe` et des API .NET de PowerShell 7.
   Il ne doit pas être porté silencieusement vers PowerShell 5.1. La capture imite
   les arguments Java ; elle ne lance pas Java ni un JAR applicatif.
3. Créer `output/runtime/` vide. Dans la seule copie d'environnement du processus
   enfant, fixer `TEMP` et `TMP` à son chemin absolu. Ne modifier aucune variable
   utilisateur/machine ni les fichiers historiques. Lancer la forme suivante, en
   résolvant et protégeant séparément chaque chemin :
   `pwsh.exe -NoLogo -NoProfile -NonInteractive -File <script absolu autorisé> -Iterations 2`.
   Le répertoire de travail de l'enfant reste sous `output/`.
4. Capturer la commande exacte et son environnement ciblé, son PID/heure de création,
   sortie et code, durée, puis la commande suivante. Le harnais gère une capture
   initiale, deux itérations et un cas sans espace ; chaque enfant est borné à 10 s,
   avec nettoyage à 5 s. Budget global de conduite : 60 s. Aucune relance automatique.
5. Conserver la sortie intégrale non sensible du harnais et observer après son retour
   le contenu de `output/runtime/`. Ses enfants sont identifiés par le harnais avec
   PID, heure de création, exécutable et identité d'instance avant tout arrêt.
   En cas de dépassement externe, ne jamais tuer tous les PowerShell ; seul un processus
   créé et identifié pour ce cas peut être arrêté, avec postflight distinct.
6. Les captures temporaires sont supprimées par le harnais. Les preuves persistantes
   du nouvel essai sont son transcript, son code immédiat, les commandes de conduite,
   la durée et le postflight indépendant. Signaler cette portée de preuve. Les nombres
   historiques du rapport WO-044 ne deviennent pas les nombres de la nouvelle exécution.

## WR-N01 — sonde neuve, Windows PowerShell 5.1 et Java 25

1. Créer une racine neuve `output/runtime/WR native probe <uuid>/` contenant des espaces et
   un sous-répertoire temporaire. Conserver la fixture `inputs/fixtures/NativeProbe.java`
   inchangée ; le mode source de Java peut la lire directement. Aucun Maven, javac,
   application Spring ni module J6 n'est nécessaire.
2. Le script de conduite s'exécute par le vrai `powershell.exe -NoProfile
   -NonInteractive -ExecutionPolicy Bypass -File <script sous output/runtime-artifacts>`. Vérifier
   dans ce processus `PSEdition=Desktop`, version 5.1, Windows et le Java effectif
   25. Capturer le code de `java.exe --version` immédiatement. Si ce préflight échoue,
   consigner le résultat sans installer Java, sans WSL et sans substituer PS7.
3. Construire une copie d'environnement propre à chaque enfant Java. Fixer uniquement
   dans cette copie `TEMP` et `TMP` sous la racine de l'essai et neutraliser les
   variables d'injection `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `JDK_JAVA_OPTIONS`
   et `CLASSPATH`, sans en exposer les valeurs initiales. Ne pas modifier le parent.
   Le répertoire de travail, `java.io.tmpdir` et `user.home` de Java restent sous
   cette racine ; `-XX:-UsePerfData` évite les fichiers de performance partagés.
4. Démarrer successivement, jamais en parallèle, deux processus Java distincts, modes
   `failure` puis `sleep`, avec une identité UUID neuve par mode. La forme est :
   `<java.exe> -XX:-UsePerfData -Djava.io.tmpdir=<temp absolu> -Duser.home=<racine absolue>
   <NativeProbe.java absolu> <mode> <uuid>`.
   Chaque chemin est un argument complet ; sous .NET Framework, la ligne
   `ProcessStartInfo.Arguments` doit conserver les guillemets requis. Ne pas utiliser
   `ArgumentList` propre aux versions .NET récentes dans une conduite PS5.1.
5. Conserver immédiatement l'objet Process, son handle, PID, heure de création,
   exécutable et UUID. La fixture n'écrit aucun fichier, n'ouvre aucune socket et ne
   crée aucun descendant. Elle imprime son identité, son PID, un texte UTF-8, puis
   `WR_PROBE_READY`. Les deux modes sont volontairement différents ; aucune valeur
   de verdict ni mesure n'est fournie par cette entrée.
6. Le mode `failure` dispose d'une attente maximale de 10 s ; enregistrer son code
   réel immédiatement après sortie. Pour `sleep`, observer READY dans un délai
   maximal de 10 s, puis chronométrer une attente de 1 500 ms à partir de ce marqueur.
   L'échéance de readiness ne doit pas être confondue avec l'expiration après READY.
   Les lectures redirigées utilisent UTF-8 explicitement.
7. Après expiration, terminer seulement le processus de sonde exact encore vivant,
   dont le handle et l'identité sont conservés. Windows PowerShell 5.1 utilise le
   `Kill()` sans argument de .NET Framework, pas `Kill(true)`. Attendre au plus
   5 s sa sortie et relever séparément l'issue du nettoyage et le code après arrêt.
   Ne pas présenter le code après arrêt forcé comme le code natif du mode failure.
   La fixture se termine naturellement après 15 s si elle n'est pas arrêtée ; cela
   ne remplace pas une preuve du traitement d'expiration demandé.
8. Budget total de conduite : 45 s, y compris préflight, deux modes et nettoyage ;
   si le budget ne peut plus être respecté, ne pas lancer le mode suivant. Aucun
   retry automatique ni allongement de borne pour obtenir un succès.
9. Dans un `finally`, confirmer la sortie des processus exacts par leurs handles
   avant de les libérer. Supprimer seulement les fichiers créés dans la racine
   temporaire dont les chemins ont été vérifiés, puis ses répertoires vides.
   Refuser tout résidu inattendu ; ne pas le supprimer récursivement. Conserver la
   preuve de l'absence de cette racine et les scripts/journaux sous `output/runtime-artifacts/`.
10. Conserver code de chaque couche (Java, conduite PS5.1, outil), durées mesurées,
    identité, contenu de protocole, retour du prompt et absence de résidu détenu.
    Une défaillance d'observation/nettoyage doit apparaître sans masquer l'erreur
    principale. Aucune preuve de stabilité générale, Docker, Eclipse ou WSL ne découle
    automatiquement de cette petite sonde.

## Environnement et limites

Un incident de découverte de modules est observé avant toute modification.
Le contenu de `PSModulePath` du poste n'est pas une donnée à exporter intégralement.
Si un ajustement est nécessaire, il concerne seulement la copie d'environnement de
l'enfant, et sa justification, son avant/après limité et son effet sont consignés.
Aucune suppression systématique ni modification persistante de cette variable.

Les scripts de conduite et les preuves sont produits par la session évaluée, puis
conservés par le lanceur. Toute mutation d'une entrée ou substitution de runtime exige
un nouveau gel ; un blocage réel reste un blocage. Aucune règle de ce protocole n'autorise
un arrêt de Java/Docker/Eclipse tiers, une requête fournisseur ou une collecte.
