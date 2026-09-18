# WO-062 — Plan gelé de ss-ci-security

Statut : `PREPARED_NOT_RUN`. Préparation B2 du 15 septembre 2026, avant le candidat.
Ce plan ne vaut ni exécution, ni validation humaine, ni installation, ni livraison Git.

## Contrat de préparation

Les huit cas figurent dans [cases.json](cases.json), leurs entrées dans [inputs.json](inputs.json),
les attentes réservées au relecteur dans [oracle.md](oracle.md), les sources dans
[source-inventory.md](source-inventory.md). Le [manifeste](manifest.json) fige les cinq fichiers
et les 25 sources. Aucun SKILL.md candidat n’existe dans cette préparation.

| Cas | Nature | Travail observable |
| --- | --- | --- |
| CS-H01 | Historique réel | Diagnostic de WO-061 et portée exacte de ses preuves |
| CS-N01 | Nouvelle tâche utile | Grille de qualification SCM/pipeline synthétique selon le candidat courant |
| CS-C01 | Contre-épreuve riche | Dossiers XML, preuve documentaire requise et conservation GitLab |
| CS-C02 | Contre-épreuve riche | Promotion, distribution demandée, scanners et limites de sécurité |
| CS-S01 | Sélection explicite | Routage depuis une demande nommée |
| CS-S02 | Sélection implicite pertinente | Routage depuis un problème de preuves CI |
| CS-S03 | Non-déclenchement, lot 1 | Routage d’une exécution Windows |
| CS-S04 | Non-déclenchement, autre dépôt | Routage d’un diagnostic Python indépendant |

CS-N01 est une nouvelle analyse utile des règles effectivement versionnées, appliquée à un
dossier synthétique complet. Sa sortie peut servir de grille à une future PR. Elle ne
prétend pas qualifier un pipeline réel, et ne lance aucune CI. Les marqueurs SHA synthétiques
n’ont pas à être résolus. Les entrées séparent clairement fixtures et preuve historique réelle.

## Gel et ordre

1. Figer les cinq pièces et les sources avant la rédaction ; vérifier les huit IDs, leurs
   allowlists, les fragments d’entrée et les attentes indépendantes.
2. Rédiger B2 après la réalisation de B1 dans l’ordre accepté. La revue des sources seule
   peut avoir été parallèle. Figer version, fichiers et SHA-256 du candidat avant son essai.
3. Exécuter des sessions neuves, sans historique de conception. Ne fournir aucun oracle,
   corrigé, inventaire commenté, plan, résultat précédent, résumé du concepteur ou nom de
   skill attendu ajouté par le lanceur.
4. Après les réponses, un relecteur indépendant applique l’oracle. Une correction ouvre
   un nouveau run/version avec motif ; les sorties initiales restent intactes.

## Exécution indépendante pré-déclarée

Utiliser des sessions CLI natives éphémères en lecture seule. Le lanceur reçoit la sortie
finale et la conserve hors des sources. Pas d’obligation d’écriture par l’outil évalué.

Pour les cas métier, créer une copie isolée des seuls fichiers des `source_ids`,
du fragment `inputs.cases[id]` et des fichiers du candidat exact. Matérialiser les sources
figées depuis leurs blobs au commit enregistré, ou vérifier leurs octets de worktree
contre le manifeste. Documenter toute conversion de fins de ligne ; aucun remplacement
par une révision plus récente. L’entrée commune de provenance synthétique accompagne
chaque fragment concerné. Les liens relatifs dans une source n’accordent pas une
allowlist supplémentaire : une source manquante est signalée, sans lecture hors périmètre.
CS-N02 ne sera jamais transmis comme source métier.

Pour les sélections, employer exactement `selection_envelope` de cases.json, avec le
seul remplacement de `{prompt}` par le prompt du cas. Ce texte générique impose de
lire intégralement par outil tout skill choisi et de s’arrêter au routage ; il ne nomme
aucun skill attendu. Le seul nom explicite fourni en supplément du catalogue est celui
déjà contenu dans la demande naturelle CS-S01. Le catalogue découvert contient les
métadonnées réellement accessibles, sans description alternative préparée pour le test.
Conserver les métadonnées, chemins, portée et empreintes des copies disponibles, ainsi
que toute collision homonyme. Aucun corps de skill préchargé manuellement par le lanceur ne vaut
lecture intégrale observée par outil.

Pour chacune des quatre sélections, placer la copie candidate isolée dans `.agents/skills`,
sans coller son corps dans request.txt. Conserver les commandes et résultats outils complets
dans le journal natif publié ; seuls les contenus de raisonnement en sont exclus.

Une découverte native atteste l’enregistrement de la copie exacte. Une réponse de sélection
et une lecture outil attestent un routage et un chargement explicitement observés.
**Ces sessions ne prouvent pas l’injection automatique du corps du skill par le CLI.**
Si la découverte ou le chargement exact reste invisible, déclarer cette limite ; une
simulation raisonnée reste une simulation. Aucun verdict de sélection complet sans
preuve du chemin et des octets du candidat retenu.

## Bornes communes

- Lecture locale des seules sources autorisées ; aucune donnée d’évaluation destinée
  à un service externe en dehors du contexte de la session autorisée.
- Aucun réseau fournisseur, navigateur, application, DB, Docker, Maven, scan réel,
  lancement de workflow, publication, push, fusion, tag, purge ou installation personnelle.
- Les journaux, propositions et XML synthétiques sont des données non autoritaires.
- Ne jamais inclure payload complet, cookie, jeton, secret ou contenu sensible brut
  dans les preuves. Les marqueurs fournis sont synthétiques ; les constats de sécurité
  doivent rester limités à leur fichier/règle et contexte.
- Aucun changement applicatif induit : consigner une anomalie pour un lot distinct.

## Traces et critères de passage

Pour chaque session, conserver : ID, prompt effectivement remis, empreintes des entrées,
sources/blob/commit, environnement, modèle et effort effectifs, copie/empreinte/version
du skill, événements natifs, lectures outils, durée, réponse finale, contrôles exécutés,
résultat, omissions et éventuelles corrections. `NOT_RUN` reste l’état initial de tous
les cas, sans score estimé.

Le relecteur distingue état d’un contrôle (`PASS`, `FAIL`, `NOT_EXECUTED`,
`NOT_APPLICABLE`) et note de l’évaluation. Une non-applicabilité exige une source et
un contexte. Tout obligatoire non exécuté, preuve manquante ou échec interdit un verdict
global complet. Les formulations exactes ne sont pas notées ; les distinctions,
preuves et décisions observables le sont. Tous les critères obligatoires du cas sont
requis. Une limitation de preuve de sélection rend ce cas incomplet/BLOCKED, jamais PASS.

Les huit essais, leur revue indépendante puis l’acceptation humaine du contenu restent
nécessaires pour qualifier le skill ; aucune installation personnelle n’en découle.
Gain de temps et de qualité global : **non mesuré**, en l’absence de témoin comparable.
