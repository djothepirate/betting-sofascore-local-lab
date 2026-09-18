# WR-N01 — conditions opératoires de reprise après preuve de propriété

## État de cette étape

La sonde de filiation distincte a été exécutée et revue. Les modes `failure` et
`sleep` de WR-N01 n'ont pas été relancés. Ce document prépare leur conduite ;
il ne constitue ni une réponse de session évaluée ni la qualification du cas.
Le protocole original et `NativeProbe.java` restent inchangés.

## Entrée additionnelle proposée pour la prochaine session

Appliquer les bornes du protocole WR-N01 : vrai Windows PowerShell 5.1 Desktop,
Java 25, deux modes successifs, UUID distincts, attente failure 10 s, readiness
sleep 10 s puis expiration 1 500 ms, sortie après arrêt 5 s, conduite totale 45 s.
Le script de conduite et ses preuves sont à produire dans le contexte neuf du cas.

1. Découvrir toutes les applications `java.exe` par `Get-Command -All`. Relever
   chemin, taille, SHA-256 et métadonnées de fichier. Distinguer un relais de
   lancement (`OriginalFilename=shimconsole.exe`) de l'image Java directe. Ne pas
   choisir le premier résultat par défaut, ne pas modifier PATH ni installer Java.
   Exiger un candidat direct Java 25 unique et vérifier sa version effective.
   Les métadonnées seules ne démontrent pas l'identité de la JVM exécutée.
2. Pour chaque mode, lancer cette image directe avec `ProcessStartInfo` et garder
   immédiatement l'objet `Process`, son handle, le PID, l'heure de création et,
   pendant sa vie, le chemin de `MainModule`. Le PID du conducteur, la commande
   exacte, le répertoire de travail et l'UUID sont consignés à ce lancement.
   Comparer le PID détenu et l'UUID aux valeurs publiées par la fixture, puis
   l'image observée au chemin demandé. Toute divergence bloque le cas ; elle
   n'autorise ni recherche globale ni arrêt d'un processus tiers.
3. La fixture originale publie PID et UUID, mais pas parent, image ni heure de
   création. Décrire la provenance des preuves sans inventer ces trois champs
   dans son stdout. Le parent de création est connu par le `Process.Start` du
   conducteur ; cette relation est une preuve de création conservée, différente
   d'une observation indépendante du parent par la JVM. La preuve préalable de
   filiation ne doit jamais être copiée comme mesure du nouveau processus.
4. Avant tout arrêt du mode sleep, exiger la concordance du handle encore vivant,
   du PID et de l'UUID publiés, de l'image et du lancement conservés. Ne pas
   remplacer le handle par une recherche fondée sur un PID seul. Si l'identité
   n'est pas établie, aucun arrêt forcé : constater la limite et la sortie
   naturelle bornée de la fixture, sans la déclarer traitement réussi du timeout.
   Après sortie, copier le code réel immédiatement et conserver les issues de
   l'exécution et du nettoyage séparément. Si l'image ou l'heure n'a pas pu être
   relevée à temps, consigner cette absence et ne pas reconstruire une preuve.
5. Garder les seules ressources neuves du cas dans les racines UUID vérifiées,
   sous `output/runtime/`, avec stdout/stderr UTF-8 explicites. Après confirmation
   de sortie par handles, supprimer seulement les fichiers créés et identifiés,
   puis les répertoires vides. Conserver tout résidu inattendu. Un postflight
   séparé et le marqueur de retour du prompt précèdent la conclusion et la réponse
   finale. Pas de relance automatique ; pas d'allongement des bornes.

## Séparation du contexte évalué

Cette entrée additionnelle doit être gelée avec un nouveau préflight avant un
éventuel essai complet. Ne transmettre à la session ni les mesures de la sonde
préalable, ni ses revues, ni les résultats des runs précédents, ni l'oracle.
Les points 1 à 5 sont des contraintes de conduite et de preuve ; ils ne donnent
pas les résultats attendus des modes. Une nouvelle réponse doit être figée puis
revue indépendamment. Le présent dossier n'en produit aucune.

## Limite persistante

La preuve préalable établit une JVM directe réellement fille du conducteur dans
son contexte d'exécution. Elle ne démontre pas encore cette conduite complète
dans une session de modèle sandboxée, ni le code natif failure, le timeout sleep,
l'arrêt forcé ou le nettoyage des deux modes. Le résidu historique reste conservé
et sa propriété n'est pas réattribuée à la sonde préalable.
