# PB-S01 — Réexécution instrumentée, protocole figé avant lancement

La demande propriétaire de compléter la qualification ouverte PB-S01 autorise cet essai
complémentaire. L'autorisation de la session précédente concernant l'envoi du contexte au
service de modèle configuré est conservée. Le candidat reste 0.1.0-candidate.1, inchangé.

## Critère et différence avec run-01

Le critère A1 reste le chargement du candidat exact sur demande explicite. Le prompt métier
PB-S01 est recopié à l'identique depuis cases.json. L'enveloppe de run-01 rendait la lecture
facultative et n'a pas produit de trace de lecture. L'enveloppe de run-02 exige une lecture
intégrale par outil local du SKILL.md sélectionné, sans désigner le choix attendu ni donner
le corps, son hash ou un extrait. Cela précise l'observabilité, pas le contenu du candidat.

Il s'agit d'une réexécution instrumentée distincte, pas d'une reproduction strictement
identique de run-01. Le §4 du plan A1, ses cas et son oracle ne sont pas réécrits. Le constat
BLOCKED de run-01 reste historique. Un PASS complémentaire pourra lever la preuve ouverte
dans la qualification courante, avec cette différence de méthode explicitement conservée.

## Exécution

Une session CLI Codex neuve, éphémère, en lecture seule reçoit routing-request.txt sur stdin.
Seul le candidat est copié dans .agents/skills du dossier isolé ; les skills personnels du
lot 1 peuvent être découverts dans leur emplacement existant. Aucune installation personnelle.
Le catalogue est découvert par le CLI. Aucun corps de skill, oracle, revue ou historique
de conception n'est ajouté manuellement à l'entrée évaluée. Aucun autre fichier du Lab
n'est autorisé en lecture dans cet essai de sélection. Le test s'arrête au routage.

## Preuves nécessaires à la revue

1. Prompt PB-S01 inchangé, enveloppe et candidat identifiés par SHA-256 avant l'essai.
2. Lecture locale du chemin candidat exact, terminée avec succès, dans le journal natif.
3. Corps complet effectivement retourné : comparaison indépendante au fichier immuable.
   Seules les fins de ligne CRLF/LF et les retours terminaux de transport sont normalisés ;
   aucun espace interne ni texte de contenu n'est retiré.
4. Identité binaire du candidat avant et après l'essai ; aucune modification des preuves A1/run-01.
5. Réponse de routage cohérente et aucune tâche métier, réseau fournisseur, application,
   build, accès aux données du Lab, Docker ou collecte.
6. Gel des sorties avant revue indépendante ; aucune correction rétrospective du résultat.

Le journal publié conserve la lecture et son texte retourné, qui est uniquement le candidat
autorisé. Les événements de raisonnement interne sont omis. Le journal brut reste temporaire,
avec son empreinte. Une reconstruction locale du contexte est seulement corroborante : elle
ne vaut pas capture de requête ni preuve de l'injection automatique préalable du CLI.

Un essai réussi qualifie le chargement par outil dans ce dispositif. Il ne démontre ni le
chargement automatique sans cette consigne, ni la validation humaine, ni l'installation,
ni la valeur des métriques fournisseur. Les autres cas ne sont pas relancés puisque le
candidat et leurs entrées restent identiques.
