# Plan d'évaluation préparé — ss-provider-benchmark

- **Version :** 1, préparée le 15 septembre 2026, après acceptation du WO-062.
- **État :** `PREPARED_NOT_RUN`. Aucun skill candidat ni résultat d'évaluation n'existe encore.
- **Corpus :** [38 sources](source-inventory.md), [prompts](cases.json), [entrées synthétiques](inputs.json).
- **Gel :** [manifest.json](manifest.json), empreintes des sources et du matériel d'évaluation.
- **Grille réservée à la revue :** [oracle.md](oracle.md), à exclure du contexte évalué.
- **Portée :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

## 1. Objectif de cette préparation

Figer les questions, les entrées disponibles et les critères observables **avant** la rédaction
du skill. Cette étape prépare deux tâches complètes, six contre-épreuves et quatre cas de
sélection. Elle ne vaut ni qualification d'un skill ni répétition d'une campagne historique.

| Cas | Type | Décision ou comportement testé | État |
|---|---|---|---|
| PB-H01 | Historique | Relire le benchmark J8 gelé et borner ses conclusions. | NOT_RUN |
| PB-N01 | Tâche nouvelle | Produire un protocole et une scorecard prematch/live utiles à partir des résumés versionnés. | NOT_RUN |
| PB-C01 | Contre-épreuve | Séparer les strates et les exclusions ; ignorer une consigne contenue dans une note source. | NOT_RUN |
| PB-C02 | Contre-épreuve | Refus HTTP, 404, résultat absent, parsing et nearest-rank. | NOT_RUN |
| PB-C03 | Contre-épreuve | Complétude pondérée, données absentes et vide valide. | NOT_RUN |
| PB-C04 | Contre-épreuve | Dossiers distincts, composants antérieurs et dénominateur nul. | NOT_RUN |
| PB-C05 | Contre-épreuve | Fenêtre, cutoff, paramètres bruts et empreinte reproductible. | NOT_RUN |
| PB-C06 | Contre-épreuve | Délai J6 depuis l'état direct prouvé et reparsing séparé. | NOT_RUN |
| PB-S01 | Sélection | Demande explicite. | NOT_RUN |
| PB-S02 | Sélection | Demande implicite pertinente au Lab. | NOT_RUN |
| PB-S03 | Sélection | Demande de vérification de build relevant du lot 1. | NOT_RUN |
| PB-S04 | Sélection | Demande visant uniquement le Betting Project. | NOT_RUN |

Les deux tâches métier satisfont la nature historique/nouvelle demandée par WO-062. Les six
contre-épreuves apportent des calculs contrôlables ; elles ne remplacent pas les deux tâches.

## 2. Corpus et livrables attendus

### PB-H01 — relecture historique

La source principale H01 est le fichier du rapport complet, conservé à l'identique. H03 apporte
sa provenance de clôture ; N07 et P01 permettent de distinguer formule et procédure historique.
Livrer une note avec tableau de mesures, sources, dénominateurs et limites. Il n'est pas demandé
de reconstituer une base PostgreSQL, de recopier le payload ou de réexécuter l'export J8.

### PB-N01 — première scorecard prematch/live

La question nouvelle est figée dans `cases.json`. Elle doit produire :

1. un protocole distinguant cohortes, checkpoints et unités d'observation ;
2. une scorecard indiquant chaque mesure utilisable, sa source, son dénominateur et sa limite ;
3. une liste précise des preuves manquantes pour une comparaison plus complète.

H05/H08 donnent les résumés temporels historiques, H06 les départs et refus V8, H07/H09 la
qualification locale V11. Les fichiers privés `.tmp` cités dans les anciens rapports ne sont
pas disponibles dans ce corpus et ne doivent pas être recherchés. Les identifiants J8/live,
formules et fenêtres ne sont pas fusionnés sans justification. Une proposition de future
mesure reste documentaire : elle ne lance pas sa collecte.

### PB-C01 à PB-C06 — arithmétique et limites

Le corpus `inputs.json` contient uniquement des tableaux pédagogiques synthétiques. Il n'est
pas importable en base et n'est pas un manifeste de collecte. Le superviseur extrait **un seul
objet de cas** par exécution. Livrer les calculs avec unités, numérateurs/dénominateurs, états,
exclusions et limites ; aucune formulation exacte n'est imposée.

## 3. Exécution future sans fuite de la grille

1. Vérifier le commit et les identifiants Git de toutes les sources nécessaires au cas. Relever
   les changements éventuels ; si une règle substantielle change, préparer une nouvelle version
   des entrées/oracles avant l'essai, sans réécrire la version antérieure.
2. Établir le candidat `ss-provider-benchmark` et son hash, puis préparer une copie isolée
   pour l'évaluation, selon le mode d'installation du WO. L'installation utilisateur actuelle
   n'est pas une destination d'essai.
3. Créer un contexte évaluateur neuf **sans historique de conception**. Pour une sous-tâche,
   utiliser un démarrage sans héritage de conversation. Ne fournir ni cet inventaire commenté,
   ni ce plan, ni `oracle.md`, ni les conclusions des agents ayant préparé les cas.
4. Fournir uniquement le prompt du cas, le skill candidat pour les cas métier, les sources
   brutes désignées par `source_ids`, l'objet synthétique du cas si présent et le dossier de
   sortie isolé. Résoudre les IDs côté superviseur ; ne pas transmettre tout le manifeste.
5. Appliquer l'allowlist de lecture à ces fichiers. Les liens sortants ou chemins privés d'un
   rapport ne donnent pas accès à d'autres ressources. Aucun réseau, serveur, DB, Docker,
   navigateur, collecte, changement de source ou action sur un autre dépôt n'est autorisé.
6. Figer la réponse et ses éventuels artefacts avant toute revue. L'évaluateur peut signaler
   une preuve manquante ; le superviseur ne lui transmet pas le résultat attendu en cours d'essai.
7. Donner ensuite à un relecteur indépendant la sortie figée, l'oracle et la trace d'exécution.
   Toute correction substantielle du skill implique un nouveau hash et une exécution identifiée.

Une copie qui a vu l'oracle peut servir à une répétition guidée, jamais être requalifiée
rétrospectivement comme évaluation indépendante. Les mêmes cas servent à détecter une régression,
mais ne suffisent pas à prouver une généralisation à des tâches nouvelles non observées.

## 4. Sélection explicite et implicite

Pour PB-S01 à PB-S04, ne précharger ni le corps du skill ni un message invitant le modèle à le
choisir. Présenter le catalogue effectivement découvert et le seul prompt. S01 contient déjà
la mention explicite demandée ; S02 doit conduire à la sélection par pertinence ; S03/S04 doivent
permettre une autre route. Observer les skills chargés et les outils utilisés.

Ces quatre cas évaluent **la sélection seulement**. Il n'est pas demandé d'exécuter la vérification
de build ou une comparaison d'offres en ligne des cas négatifs. Arrêter après la décision de
routage, avant une éventuelle action hors de l'allowlist. Si le mécanisme réel de découverte
n'est pas disponible, consigner `NOT_RUN` pour la sélection réelle ; une appréciation manuelle
de la description reste une revue de texte distincte.

## 5. Traces et verdicts à conserver lors des essais

Pour chaque exécution, consigner :

- identifiant du cas et version du plan, horodatage UTC ;
- commit des sources, empreintes des entrées et du skill candidat ;
- modèle et effort effectivement observés, environnement et catalogue pour la sélection ;
- prompt exact, fichiers fournis, outils/actions, durée et chemins/empreintes des sorties ;
- critères satisfaits/manquants, écarts, verdict et limites de la revue.

Les valeurs modèle/effort ne sont pas inventées à la préparation. Une durée est une observation
d'essai ; aucun gain de productivité n'est affirmé sans comparaison équitable.

Verdicts : `PASS` si tous les critères obligatoires du cas sont satisfaits ; `FAIL` si un
critère matériel échoue ; `BLOCKED` si une entrée ou une capacité nécessaire empêche d'évaluer ;
`NOT_RUN` si l'essai n'a pas commencé. Une réponse manquante n'est pas un zéro ni un succès.

La réception de cette étape signifie : sources et entrées localisées, prompts figés, oracles
revus, liens/JSON/empreintes contrôlés. La réception **du skill** exigera les résultats réels,
la revue et la validation humaine prévus dans WO-062.
