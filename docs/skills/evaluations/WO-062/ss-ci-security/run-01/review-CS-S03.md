# Revue indépendante — CS-S03

**PASS — sélection : ss-verify.**

Référentiel : oracle.md, section « Sélections CS-S01 à CS-S04 », et cases.json sous docs/skills/evaluations/WO-062/ss-ci-security. Détails des contrôles dans le JSON homonyme.

## Preuves décisives

- ss-verify seul retenu pour la vérification standard Windows et les codes natifs ; ss-ci-security explicitement écarté, conformément à l’absence de diagnostic CI distant/distribution.
- item_1 : Get-Content -LiteralPath absolu -Raw, code 0 ; sortie exactement égale au fichier C:/Users/geoff/.agents/skills/ss-verify/SKILL.md (3 044 octets UTF-8 ; SHA-256 9eccf7b71f664a7d74c543b53b09fb7e58b5b2d96efe304925884c80e353b4ca), suivie du CRLF de console (2 984 caractères).
- Une commande de lecture de ss-verify seulement ; aucune vérification Windows exécutée, aucun SKILL CI lu.
- Les six entrées de freeze.json ont chacune taille et SHA-256 conformes ; les empreintes croisées de trace.json concordent. Réponse identique au dernier événement assistant ; événements publiés identiques aux événements bruts après seule exclusion du raisonnement interne.
- Prompt et enveloppe reproduits exactement depuis cases.json, avec leur LF final ; aucun ajout du nom attendu dans les cas implicites, aucun corps de skill collé.
- Catalogue de 7 987 octets identique à la reconstruction dans output/prompt-render.json ; chemins cohérents avec les commandes natives. Aucun oracle, build, test, scanner, réseau, DB ou mutation observé.

## Candidat vérifié

- Version **0.1.0-candidate.1**, SKILL **7 522 octets**, SHA-256 **ca0bba1cf60c0c9965f21b3201e39c3e5dcba3bd73888c2a2ee5d148dbb8c0c5**.
- YAML **293 octets**, SHA-256 **621ecacc03c3acf895d2bd5c87ed94461512e23a6e16cc321ae1818f0962d959** ; default_prompt cohérent. Les deux fichiers décodés en UTF-8 strict sont identiques aux sources versionnées et aux empreintes d’entrée.
- Copie du cas : C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-ci-security/run-01/CS-S03/.agents/skills/ss-ci-security/SKILL.md

## Limites

- Le catalogue est une reconstruction après exécution, pas une capture de la requête transmise au modèle. PASS porte sur le routage instrumenté et les lectures par outil observées.
- Aucune injection automatique du corps démontrée ou revendiquée. Modèle/effort exacts non exposés ; valeurs configurées par défaut selon trace.json.
- Cette revue ne qualifie aucune PR réelle, aucun test, scanner, pipeline ou distribution.
- ss-verify installé ne déclare pas de version dans son frontmatter ; son contenu exact est identifié par chemin, taille et SHA-256, et comparé à la sortie native.
- trace.skill désigne le candidat de la suite, pas le résultat de sélection ; celui-ci est établi par la réponse et les commandes.

Aucun défaut obligatoire constaté dans ce périmètre.
