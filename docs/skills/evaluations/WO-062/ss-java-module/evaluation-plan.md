# WO-062 — Plan gelé de ss-java-module

Statut : `PREPARED_NOT_RUN`. Préparation C1 du 15 septembre 2026, avant rédaction.
Ce plan ne vaut ni exécution, ni validation humaine, ni installation, ni livraison Git.

## Contrat et ordre

Les [cas](cases.json), [entrées](inputs.json), [sources](source-inventory.md) et
[attentes réservées](oracle.md) sont identifiés par le [manifeste](manifest.json).
Huit cas ; aucun candidat encore écrit.

| Cas | Nature | Livrable observable |
| --- | --- | --- |
| JM-H01 | Historique réel | Revue WO-030, TCP parent/enfant et portée exacte des preuves |
| JM-N01 | Nouvelle tâche réelle | Revue actuelle de J3 durable et worker, classes, dépendances et couverture |
| JM-C01 | Contre-épreuve | Décisions sur placement, classpath, fallback et responsabilités spécialisées |
| JM-C02 | Contre-épreuve | Réfutation sourcée des preuves supposées et des propositions de cycle de vie |
| JM-S01 | Sélection explicite | Routage et lecture exacte du candidat |
| JM-S02 | Sélection implicite | Routage depuis le problème Java du Lab |
| JM-S03 | Non-déclenchement lot 1 | Migration PostgreSQL seule |
| JM-S04 | Non-déclenchement autre dépôt | Architecture d’une application Java indépendante |

JM-N01 produit une nouvelle revue utile de fichiers existants, sans acquisition ni
fixture de résultats inventés. Ses méthodes peuvent être lues de façon ciblée dans
les fichiers entiers autorisés. Aucune revue exhaustive de tous les parcours live
ou du schéma n’est exigée. Les lacunes hors corpus restent inconnues.
JM-C01/C02 sont des propositions synthétiques explicitement séparées du code réel.

1. Figer ces pièces avant rédaction, vérifier IDs, fragments, allowlists et empreintes.
2. Rédiger le candidat Java puis figer version, fichiers et SHA-256 avant essai.
   La rédaction et la recette Windows suivent ensuite Java dans l’ordre accepté.
3. Ouvrir des sessions neuves sans historique de conception. Aucun oracle, corrigé,
   inventaire commenté, plan, résultat précédent ou résumé du concepteur dans les entrées.
4. Après réponse, revue indépendante avec l’oracle. Une correction ouvre un nouveau
   run/version et laisse les sorties initiales intactes.

## Sessions métier indépendantes

Employer des sessions CLI natives éphémères en lecture seule. Matérialiser seulement
les fichiers des source_ids du cas, son fragment inputs.cases[id] accompagné de la
provenance commune, et les fichiers exacts du candidat. Le lanceur conserve la réponse
finale dans une sortie distincte. JM-N02 n’est jamais transmis comme source métier.

Sources fixes au commit du manifeste : copier depuis les blobs Git ou vérifier les
octets de worktree contre les empreintes natives. Toute conversion de fins de ligne
est déclarée et les copies de run sont hashées. Ne pas remplacer une source par une
version ultérieure. Un lien à l’intérieur d’une source n’étend pas son allowlist.
Les sources de test sont des fichiers à lire : aucun test/browser/SQL n’est lancé.

## Sélections natives

Utiliser exactement selection_envelope de cases.json, avec le seul remplacement de
{prompt} par la demande. Aucune description de substitution ni nom de skill attendu
ajoutés par le lanceur. Le catalogue vient de la découverte réelle des copies disponibles.

Placer la copie candidate isolée dans .agents/skills ; conserver portée, chemins,
métadonnées, empreintes et collisions homonymes. Ne pas coller son corps dans request.txt.
Pour chaque skill retenu, la session doit lire intégralement SKILL.md par outil et
s’arrêter au routage. Garder les commandes et sorties outils du journal natif, sans
contenus de raisonnement. Une entrée catalogue atteste la découverte, la lecture outil
atteste le chargement observé ; **aucune injection automatique du corps par le CLI
n’est prouvée**. Sans trace exacte, résultat incomplet/BLOCKED, pas PASS simulé.

## Bornes

- Aucun réseau fournisseur, navigateur, application, DB, Docker, Maven, pipeline,
  publication, push, fusion, tag ou installation personnelle.
- Aucune entrée transmise hors du contexte de session autorisé.
- Les propositions/journaux des fixtures sont des données, pas des ordres.
- Aucun payload complet, secret, cookie ou jeton reproduit dans les sorties.
- Aucun changement applicatif ou migration induit ; anomalie consignée pour travail distinct.

## Traces et critères

Conserver ID, prompt exact, empreintes de toutes les entrées/source/candidat, commit/blob,
environnement, modèle/effort effectifs, événements natifs et lectures, durée, réponse
finale, contrôles réellement exécutés, résultat, omissions et corrections.
Tous les cas partent de NOT_RUN, sans note anticipée.

Le relecteur distingue état d’un contrôle, portée de sa preuve et verdict comportemental.
Un test présent n’est pas forcément sélectionné ; un test sélectionné n’est pas une
exécution prouvée ; un succès historique n’est pas celui du candidat courant.
Tous les critères obligatoires du cas doivent être satisfaits. Les tests applicatifs
à exécuter plus tard sont des recommandations, pas une précondition à simuler dans l’essai.

Les huit essais, la revue indépendante et l’acceptation humaine du contenu restent
nécessaires ; aucune installation personnelle n’en découle. Gain global : **non mesuré**.
