# Complément opératoire — session Windows complète

Ce document définit la conduite de la session, sans résultats attendus. Appliquer
uniquement le fragment du cas reçu, avec le candidat exact et les entrées autorisées.
Les sources et la fixture d'origine restent inchangées. Produire les scripts de
conduite dans `output/runtime-artifacts/`, puis les exécuter dans cette même session.

## Organisation et fin de session

- Lire intégralement le candidat. Lire une seule fois les entrées courtes. Dans un
  module volumineux, rechercher les fonctions appelées puis lire leurs sections ;
  ne pas exporter le fichier entier. Les références ne donnent aucun accès hors liste.
- Après ces lectures, préparer une conduite compacte et exécuter une seule tentative
  du cas. Ne pas ouvrir de sous-agent, autre session, build ou tâche annexe.
- Viser une réponse finale avant six minutes ; le watchdog extérieur reste quinze
  minutes. Les bornes de la conduite sont celles du protocole original : 60 s pour
  WR-H01, 45 s pour WR-N01, y compris préflight et nettoyage. Mesurer avec Stopwatch
  et transmettre les budgets restants aux attentes. Ne pas prolonger les délais.
- Dans les erreurs de conduite, conserver étape, type, message, identifiant, ligne
  et pile, sans exposer l'environnement complet. Une observation auxiliaire absente
  n'autorise pas d'inventer la propriété d'un enfant ni de masquer un échec principal.
- Marquer en UTC le lancement réel, le retour, la disponibilité des preuves, le
  postflight et la conclusion. Ne pas écrire un marqueur de lancement avant le
  `Process.Start` réussi ; une fin de finally n'est pas une exécution réussie.
- Après retour réel, exécuter une commande courte distincte avec WR_PROMPT_RETURNED,
  puis un postflight séparé, lire une fois les résultats et donner la réponse finale.
  Le lanceur conserve les traces et calcule les empreintes : pas de manifeste,
  README doublon ou revue du skill à produire dans la session.
- La réponse finale couvre toutes les demandes du cas, les preuves et leurs limites.
  Conserver les références précises et les écarts historiques utiles ; une contrainte
  de concision ne doit pas supprimer une partie demandée. Un blocage est expliqué
  jusqu'à la réponse finale, sans relance automatique.

## WR-H01 — conducteur minimal et histoire complète

Le harnais original est exécuté sous PowerShell 7 Windows avec `-Iterations 2`, ses
dépendances intactes et TEMP/TMP de son seul enfant sous `output/runtime/`.
Sa gestion interne des captures reste inchangée. Conserver son transcript réel,
son code natif immédiat, son identité, ses durées et le postflight du runtime.

L'objet Process et son handle retenus dès le lancement, PID, heure de création,
image et arguments exacts établissent le processus créé par la conduite. Le PID
du conducteur vient de `$PID` ; son exécutable de son propre objet Process.
Il n'est pas nécessaire de lire le parent du conducteur par WMI/CIM. Ne pas faire
de cette observation auxiliaire un prérequis au lancement, ni ajouter un inventaire
global des processus. Ne pas modifier les gardes internes du harnais.

Dans l'analyse des sources historiques, expliquer la frontière des arguments et
les classes de chemins refusés. Restituer les références exactes des exécutions,
leurs SHA et leurs différences de déclenchement/instrumentation. Décrire également
les écarts des commandes effectivement exécutées, leurs effets et les preuves de
nettoyage correspondantes. Séparer ces observations de la nouvelle reproduction.
Une lecture réussie d'un rapport ne vaut pas nouvelle exécution de son contenu.

## WR-N01 — deux JVM directes effectivement détenues

1. Le conducteur est exécuté par Windows PowerShell **5.1 Desktop** réellement
   observé, avec Java **25**. Découvrir toutes les applications `java.exe` par
   `Get-Command -All`. Relever chemin, taille, SHA-256 et métadonnées de fichier.
   Distinguer un relais de lancement (`OriginalFilename=shimconsole.exe`) de
   l'image directe (`OriginalFilename=java.exe`). Exiger un candidat direct 25
   unique, puis vérifier sa version effective. Ne pas modifier PATH ni installer.
2. Appliquer la copie d'environnement enfant, les encodages UTF-8 et les chemins
   confinés du protocole original. Avec .NET Framework, utiliser `Arguments` en
   protégeant chaque argument requis ; ne pas employer `ArgumentList` de .NET récent.
3. Lancer successivement les modes `failure` puis `sleep`, avec UUID distincts.
   Pour **chaque** processus, retenir immédiatement l'objet Process, son handle,
   PID, heure de création et image MainModule pendant sa vie. Consigner aussi
   le PID du conducteur, la commande et les racines exactes à ce lancement.
   Lire les flux sans blocage. Comparer le PID et l'UUID détenus à ceux publiés
   par la fixture ; comparer l'image observée au chemin direct demandé.
4. La fixture originale publie PID et UUID, pas parent/image/heure de création.
   Ne pas inventer ces valeurs dans son stdout : le conducteur observe l'image
   et l'heure via le processus retenu ; sa relation de création vient de son
   `Process.Start`. Distinguer cette preuve d'une mesure du parent par la JVM.
   Aucune preuve préalable ou ancienne mesure ne vaut pour le nouveau processus.
5. Le mode failure attend au plus 10 s, puis son code réel est copié immédiatement.
   Pour sleep, la readiness est bornée à 10 s ; l'attente de 1 500 ms est mesurée
   **à partir de READY**, pas du lancement. L'heure de READY et les durées restent
   dans la preuve. Avant tout arrêt forcé, exiger handle encore vivant et concordance
   PID/UUID/image/commande ; utiliser `Kill()` de .NET Framework, puis attente de
   sortie au plus 5 s. Séparer code natif, expiration et résultat du nettoyage.
6. Une identité manquante ou divergente bloque l'arrêt forcé ; constater la sortie
   naturelle bornée sans en faire un traitement réussi du timeout. Ne jamais chercher
   ou arrêter un processus par nom, ni remplacer le handle par un PID seul.
7. Dans le budget global de 45 s, ne lancer le second mode que si ses bornes restent
   tenables. Après confirmation des sorties par handles, supprimer les seuls fichiers
   créés et identifiés, puis les répertoires vides. Préserver tout résidu inattendu.
   La preuve finale porte sur les deux nouveaux processus et leurs propres ressources.
