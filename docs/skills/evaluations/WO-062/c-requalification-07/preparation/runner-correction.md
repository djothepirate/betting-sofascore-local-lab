# Correction pré-modèle C7

Le premier appel du conducteur C7 a échoué localement avec le code 1 avant tout
processus codex exec : une assertion héritée de C5 exigeait encore run-05. Aucun des
sept cas ne contient un événement, une réponse, un catalogue ou un gel ; aucun appel
de modèle n’a commencé et l’autorisation de sept sessions reste entièrement disponible.

Le conducteur C7 distinct retire cette seule assertion C5 résiduelle. Il conserve le
run-07, ss-java-module, la liste ordonnée des sept cas, un worker, l’interdiction
d’écraser les sorties et l’exécution éphémère lecture seule. Les conducteurs C5 et C6
restent exactement inchangés. Le contrôle de contexte repasse après cette correction.
