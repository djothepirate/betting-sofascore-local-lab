# Plan d'évaluation — ss-football-quality

- **Version :** 1 ; préparation B1 figée avant rédaction du candidat.
- **État :** PREPARED_NOT_RUN, 15 septembre 2026.
- **Sources :** [inventaire](source-inventory.md) et [manifest.json](manifest.json).
- **Entrées :** [cases.json](cases.json), [inputs.json](inputs.json).
- **Revue réservée :** [oracle.md](oracle.md), exclu du contexte évalué.
- **Portée :** EXPERIMENTAL · LOCAL_ONLY · NOT_PRODUCTION_APPROVED · NO_CRITICAL_DEPENDENCY.

## Huit cas préétablis

| Cas | Type | Livrable observable | État |
|---|---|---|---|
| FQ-H01 | Historique réel | Réexamen WO-017 ; preuve réelle, fixture, compatibilité et inconnus séparés. | NOT_RUN |
| FQ-N01 | Nouvelle tâche utile | Audit croisé WO-058, parseurs actuels et projection J6 ; garanties et lacunes documentées. | NOT_RUN |
| FQ-C01 | Contre-épreuve synthétique | Matrice identité/date/HOME-AWAY/résultat/compositions/statistiques. | NOT_RUN |
| FQ-C02 | Contre-épreuve synthétique | Compatibilité incidents et table de classifications J6. | NOT_RUN |
| FQ-S01 | Sélection explicite | Lecture outil du candidat exact retenu. | NOT_RUN |
| FQ-S02 | Sélection implicite | Découverte et lecture du skill pertinent au Lab. | NOT_RUN |
| FQ-S03 | Non-sélection lot 1 | Routage du seul build ; arrêt avant exécution. | NOT_RUN |
| FQ-S04 | Non-sélection autre dépôt | Aucun chargement indu du skill Lab. | NOT_RUN |

H01 et N01 satisfont les deux tâches de WO-062. N01 apporte un audit nouveau sur des
preuves existantes : il croise un diagnostic incidents exact et une correction de restitution J6
avec les contrats/code au commit fixé. Les octets privés 2340 et 717 ne sont pas fournis ;
l'essai doit montrer cette limite. Les deux contre-épreuves renforcent la couverture et ne
remplacent pas les tâches réelle historique/nouvelle.

## Exécution future indépendante

1. Contrôler le commit source, les objets Git et les SHA-256 des fichiers concernés.
   Une évolution matérielle de contrat impose une nouvelle version du gel avant le run ;
   conserver toute version historique et son résultat.
2. Rédiger et figer le candidat ensuite. Consigner version/hash exacts. La préparation ne
   contient ni candidat, ni verdict de qualité, ni preuve d'installation.
3. Ouvrir une session native éphémère neuve, sans contexte de conception, en lecture seule.
   Préparer une copie isolée des seuls fichiers bruts des `source_ids`, de l'objet
   `input_case` concerné et, pour les cas métier, du candidat.
4. Le superviseur résout les IDs sans transmettre `source-inventory.md`,
   `evaluation-plan.md`, `oracle.md`, `manifest.json` ou le catalogue complet des autres cas.
   Les textes `use` et `locator` de l'inventaire ne sont pas des entrées évaluateur.
   Les liens d'une source n'élargissent pas l'allowlist.
5. Fournir le prompt exact. La réponse est rendue en final et sauvegardée par le lanceur ;
   aucune écriture par l'évaluateur n'est nécessaire. Aucun réseau, navigateur, application,
   Maven, parseur exécuté, DB, Docker ou collecte n'est autorisé dans ces essais documentaires.
6. Figer réponse, trace et artefacts avant une revue indépendante avec l'oracle.
   Ne transmettre aucun attendu pendant l'essai. Toute correction du candidat porte un nouveau
   hash ; toute réexécution est identifiée et n'écrase pas une tentative antérieure.

L'isolation copie uniquement les fichiers nécessaires ; elle ne cherche pas les chemins
privés cités dans un ancien rapport. L'existence de ces sources, leur lecture documentaire
et les tests que leur texte relate restent trois niveaux de preuve distincts.

## Sélection instrumentée dès run-01

Le champ `selection_envelope` de cases.json contient le texte générique exact.
Le lanceur remplace `{prompt}` une seule fois par le prompt du cas, sans nom attendu ajouté
dans l'enveloppe. Il présente le catalogue effectivement découvert ; aucun corps de skill
n'est préchargé manuellement pour ces cas et aucune réponse attendue n'est fournie.
La copie candidate est présente dans le catalogue des quatre sélections. Le CLI peut
effectuer une injection de lui-même ; ce mécanisme n'est pas certifié par ces essais.
Aucun collage du corps du skill dans le prompt de demande n'est autorisé.

Tous les skills retenus doivent être **lus intégralement par un outil** avant l'arrêt au routage,
même si une description ou un extrait est visible. Une mention du nom, un bloc de métadonnées
ou la seule déclaration « lu » ne suffit pas. Cette exigence préétablie traite la lacune
de preuve rencontrée sur PB-S01 ; elle ne revendique aucune injection automatique du corps
du skill par le CLI.

La preuve critique porte sur une **sélection native avec chargement outil instrumenté** :
catalogue réel, origine de la copie, chemin, hash et contenu complet lu. Les cas négatifs
s'arrêtent également au routage ; ils ne déclenchent ni build ni comparaison réseau.
Sans capacité de découverte/lecture traçable, conserver BLOCKED ou NOT_RUN selon le stade ;
une appréciation textuelle de pertinence ne remplace pas cette preuve.

## Traces et acceptation

Pour chaque exécution : ID, version du plan, UTC début/fin, durée, commit source, hashes
entrées/candidat, modèle et effort observés, environnement/catalogue, prompt final assemblé,
allowlist exacte, outils et fichiers lus, réponse figée, hash de sortie et revue indépendante.

Verdicts : PASS si tous les critères obligatoires sont satisfaits ; FAIL si un critère matériel
échoue ; BLOCKED si une capacité ou entrée indispensable manque ; NOT_RUN tant que l'essai
n'a pas démarré. Une absence de preuve n'est pas un succès. Les limites de chaque verdict
restent explicites ; aucun gain de productivité ou généralisation n'est mesuré sans témoin.
La validation humaine, l'installation personnelle, la qualification applicative et la clôture
du WO demeurent des étapes distinctes.
