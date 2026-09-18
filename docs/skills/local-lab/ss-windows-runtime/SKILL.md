---
name: ss-windows-runtime
description: "Diagnostiquer un lanceur ou un incident Windows du SofaScore Local Lab : PowerShell, Java/Maven, arguments, codes natifs, processus, délais et nettoyage. Ne pas utiliser pour un simple build sans incident ni pour un autre dépôt."
metadata:
  version: "0.1.0-candidate.1"
---

# Diagnostiquer le runtime Windows du Lab

Identifier le worktree, le SHA, le lanceur et le symptôme observé. Lire AGENTS et les
sources actuelles du parcours. Conserver EXPERIMENTAL, LOCAL_ONLY,
NOT_PRODUCTION_APPROVED et NO_CRITICAL_DEPENDENCY. Retenir les autorisations de la
session et les bornes applicables ; une reproduction n'autorise pas un lancement
fournisseur, un changement persistant de configuration ou l'arrêt d'un processus tiers.

## Identifier la chaîne réellement exécutée

- Relever dans le processus concerné Windows, architecture, `$PSVersionTable`,
  `$PSHOME`, exécutable, répertoire courant, lanceur parent et environnement ciblé.
  Résoudre les applications avec `Get-Command -CommandType Application`, puis
  observer leurs versions. Un nom `powershell` ou un `JAVA_HOME` ne prouve pas le
  runtime réellement employé. Java 25, Spring Boot 4.1.0 et le wrapper du dépôt
  restent le socle ; confronter AGENTS, `pom.xml`, `mvnw.cmd` et la configuration
  du wrapper. Eclipse peut employer un JDK ou un répertoire différents du terminal.
- Distinguer Windows PowerShell 5.1 Desktop, PowerShell 7, une console Eclipse,
  GitHub Actions Windows et WSL/Linux. Reproduire dans le runtime impliqué : un
  succès dans une autre chaîne reste une preuve distincte. Vérifier la compatibilité
  des API avant de proposer un script ; `$IsWindows`, certaines surcharges .NET,
  `ProcessStartInfo.ArgumentList` ou `Kill(true)` ne sont pas transposables tels
  quels à PowerShell 5.1 et .NET Framework.
- Lire `scripts/Preflight-Local.ps1`, `scripts/Verify-Local.ps1` et
  `docs/runbooks/RUNBOOK-LOCAL.md` selon le symptôme. Outil Docker trouvé, moteur
  accessible, contexte Docker choisi et Compose utilisable sont des observations
  séparées. `docker info` et son code établissent une réponse du moteur ; la seule
  présence de `docker.exe` ne le fait pas. N'exécuter ces sondes que dans le périmètre
  demandé, sans démarrer ou réinitialiser automatiquement Docker Desktop ou WSL.

## Observer arguments, sorties et codes à chaque frontière

- Décomposer la chaîne : outil appelant → PowerShell → wrapper ou processus natif
  → éventuel enfant. Conserver la commande exacte expurgée, son répertoire, ses
  arguments et les variables nécessaires, sans exporter l'environnement entier ni
  les valeurs des secrets. Un tableau PowerShell n'est pas la ligne reçue par Java.
- Pour `Start-Process -ArgumentList`, vérifier la recomposition des arguments et les
  chemins contenant des espaces. Le cas WO-044 est documenté dans
  `docs/validation/J9-WO044-JAVA-JAR-PATH-ARGUMENT-BOUNDARY-QUALIFICATION-20260904.md`.
  Lire le helper `ConvertTo-WO036JavaJarStartProcessArgument` dans
  `scripts/wo036/WO036-CampaignTools.psm1` et le harnais `scripts/wo044/` si cette
  frontière est en cause : chemin JAR canonique protégé, identité, trois flags JDK
  et ordre après `-jar`. Son enfant capture les arguments ; il ne lance pas Java.
  Le harnais historique exige PowerShell 7 Windows. Ne pas appeler cela une recette
  d'application ni une preuve PowerShell 5.1.
- Après invocation native par `&`, copier immédiatement `$LASTEXITCODE` avant
  toute autre commande native. Avec un objet `Process`, attendre sa sortie effective
  dans la borne, puis conserver son `ExitCode` ; `$LASTEXITCODE` n'est pas le résultat
  de `Start-Process`. Un `Write-Output`, une absence d'exception ou le code zéro du
  script extérieur ne remplace aucun de ces résultats. Ne pas masquer un échec
  natif par une commande ultérieure réussie ou par le résultat du nettoyage.
- Lire stdout et stderr sans bloquer l'attente surveillée : lectures asynchrones
  ou redirections propres à l'essai. Choisir explicitement l'encodage de lecture
  et d'écriture, en tenant compte des valeurs par défaut différentes de PS5.1 et PS7.
  Si les accents semblent corrompus, examiner les octets UTF-8, BOM et décodeur
  avant de réencoder. Un défaut d'affichage ne prouve pas une corruption du fichier ;
  conserver les fins de ligne attendues par le dépôt et les octets des preuves figées.

## Borner la reproduction et prouver le nettoyage

- Choisir une sonde minimale du défaut, avec entrées et résultat observable définis
  avant exécution. Préférer le harnais existant lorsqu'il correspond au runtime et
  au contrat. Une sonde synthétique doit être présentée comme telle. Définir budgets
  de démarrage, readiness, attente et nettoyage, ainsi qu'une borne globale ; employer
  un chronomètre monotone et des heures UTC. Ne pas relever un délai ou répéter un
  essai automatiquement pour obtenir un vert. Une absence de READY et une expiration
  après READY sont deux résultats différents.
- Créer une destination temporaire neuve et isolée, avec espaces lorsque cette
  frontière est testée. Vérifier chemin absolu canonique, préfixe plus séparateur,
  absence de lien/jonction et propriété des cibles avant écriture ou suppression.
  Conserver les preuves en dehors du répertoire jetable. Inventorier ce que l'essai
  crée ; supprimer les seuls fichiers connus puis les répertoires vides. Un résidu
  inattendu se signale, il ne déclenche pas un effacement récursif plus large.
- Conserver dès le lancement le handle, PID, heure de création, exécutable et identité
  d'instance des enfants. Utiliser une fenêtre cachée (`-WindowStyle Hidden` pour
  `Start-Process`, ou `UseShellExecute=false` / `CreateNoWindow=true`). En expiration,
  arrêter seulement le processus dont la propriété reste établie et vérifier sa
  sortie bornée avant de libérer son handle. Un PID seul peut avoir été réutilisé ;
  le nom `java` ne prouve jamais la propriété. Pour un lanceur à descendants, examiner
  le confinement et la preuve de nettoyage existants, notamment le module
  `scripts/J6-NativeBinaryPipeline.psm1`, sans importer tout le parcours pour une
  sonde indépendante.
- Distinguer résultat principal, code après arrêt forcé, retour effectif de la commande
  suivante et postflight des ressources. Vérifier séparément les enfants exacts et
  l'absence des fichiers/répertoires possédés. « finally terminé » ou « prompt revenu »
  ne signifie pas zéro résidu. Un échec de nettoyage reste visible sans remplacer
  le symptôme initial. Un port occupé impose d'abord d'identifier son propriétaire ;
  ne pas arrêter un Java, Eclipse ou Docker tiers ni changer l'écoute `127.0.0.1`.

## Diagnostiquer sans généraliser l'incident

- Distinguer hypothèse, observation et cause établie. Les rapports
  `docs/validation/J9-WO053-CTRL-BREAK-DIAGNOSTIC-20260905.md` et
  `docs/validation/J9-WO053-REVIEW-HARDENING-20260905.md` qualifient une instrumentation
  et ses contre-épreuves. Les essais Windows verts n'ont pas reproduit le défaut
  original : sa cause reste `NOT_ESTABLISHED`. Comparer SHA, déclencheur PR/branche,
  instrumentation, hôte et runtime avant d'attribuer un résultat à un correctif.
  Un échec antérieur aux tests, un échec applicatif et un scanner en échec restent
  trois constats distincts ; un seul job vert ne qualifie pas toute la CI.
- Pour une découverte de modules défaillante, observer l'erreur et le contexte avant
  toute modification. L'incident B de WO-062 est décrit dans
  `docs/validation/WO062-FOOTBALL-QUALITY-CI-SECURITY-INSTALLATION-20260915.md` :
  l'environnement enfant Python → PS5.1 héritait de chemins de modules, puis le test
  avec les valeurs natives a réussi. Cela justifie cet ajustement enfant précis,
  pas une suppression générale de `PSModulePath`. Si nécessaire, modifier seulement
  une copie d'environnement enfant et consigner l'effet ; préserver le parent et
  les variables utilisateur/machine. Ne pas exposer les valeurs initiales sensibles.
- Une revue historique conserve ses limites et écarts : l'exécution WO-044 sans
  `skipITs` a aussi lancé des Testcontainers isolés. Ne pas reprendre cette commande
  en supposant qu'elle évite Docker. Pour une vérification du dépôt courant, relayer
  à `ss-verify` et lire le lanceur effectif ; `--offline` limite la résolution Maven,
  pas les effets des tests. Une qualification des contrôles CI relève aussi de
  `ss-ci-security` lorsque demandée.

## Rendre un diagnostic exploitable

Relier le symptôme à la chaîne observée, aux hypothèses éliminées ou restantes, aux
commandes et sorties exactes, aux codes de chaque couche, aux durées et aux preuves
de retour et nettoyage. Délimiter ce que la sonde démontre et ce qu'elle n'a pas
exécuté. Proposer la correction minimale ou la prochaine observation discriminante.
Une source indisponible, un runtime absent ou un nettoyage non établi reste explicite ;
aucun succès WSL, historique ou synthétique ne devient une qualification Windows
applicative actuelle.
