# Revue indépendante — CS-H01 / run-01

**Verdict de l'essai : PASS.** La réponse satisfait les sept décisions obligatoires de l'oracle et les critères transversaux applicables. Aucun défaut décisionnel constaté. Ce verdict ne vaut aucune qualification CI réelle.

## Pièces et intégrité

- Pièces relues : `frozen/CS-H01/{freeze.json,trace.json,native-events.json,request.txt,input.json,response.md}` ; prompt et catalogue vérifiés par empreinte, prompt inclus dans la requête relue.
- Oracle, cas et entrées : `docs/skills/evaluations/WO-062/ss-ci-security/` ; sections H01 et critères transversaux, avec sources CS-N03, CS-P01, CS-H01, CS-I01, CS-I02.
- Les sept empreintes et tailles de `freeze.json` concordent. Les huit fichiers d'entrée concordent avec `trace.json`, dont les cinq sources autorisées également conformes au manifeste du corpus. Les cinq empreintes du corpus de préparation concordent.
- Gel déclaré : **2026-09-15T19:58:36.941142+00:00**, antérieur à cette revue.
- SHA-256 réponse : `23a9872aa367a20196cf30661d8fb0d2ba37bb38838eb289d4786cc2ea7493bb`.
- SHA-256 événements natifs : `38bc3ddf37a3d433d50420cb154285ea0c9854379d958f95d10ca22a62cf0a95`.

## Lecture et respect du périmètre

L'événement `item_1 completed` contient la sortie de la lecture intégrale du skill. Les **7 340 caractères** du fichier candidat sont présents exactement et dans l'ordre ; seuls deux caractères CRLF supplémentaires proviennent de la console. Fichier de **7 522 octets**, SHA-256 **`ca0bba1cf60c0c9965f21b3201e39c3e5dcba3bd73888c2a2ee5d148dbb8c0c5`**, identique à l'empreinte de la trace. La lecture précède l'entrée du cas et les sources métier.

Les **8 commandes**, toutes achevées avec code 0, ont été examinées individuellement :

| Événement | Opération observée | Borne vérifiée |
| --- | --- | --- |
| item_1 | Lecture brute du skill candidat | Chemin autorisé explicite |
| item_2 | Lecture brute de l'entrée | `input.json` |
| item_3 | Recherche textuelle | Trois chemins exacts : ADR, runbook et rapport WO-061 |
| item_4 | Lecture numérotée | Runbook autorisé |
| item_5 | Lecture numérotée | ADR autorisé |
| item_6 | Lecture numérotée | Rapport WO-061 autorisé |
| item_8 | Lecture numérotée | Workflow GitHub autorisé |
| item_9 | Lecture numérotée | GitLab autorisé |

Aucune commande des documents n'est exécutée. Les événements publiés ne montrent que messages et commandes de lecture ; aucune mutation, application, base, réseau, Docker, Maven, test, scanner ou pipeline. Le lancement porte `--ephemeral --sandbox read-only`. La requête contient uniquement l'enveloppe de lecture, les chemins autorisés et la demande du cas ; aucun oracle ni attentes réservées. Cela concorde avec `oracle_supplied=false` et `design_history_supplied=false`.

## Confrontation au fond

Les repères ci-dessous sont les lignes de `frozen/CS-H01/response.md`.

| Critère obligatoire | État | Preuve de décision |
| --- | --- | --- |
| Run, job, tests/packaging puis quota | PASS | L. 23–35 et 100–101 : run `34950465172`, job `104319936308`, échec du snapshot distingué d'une régression et d'un upload XML |
| SHA non inventé et identités séparées | PASS | L. 80–90 : base `59b4…`, commit fonctionnel `a3ed…`, politique `57c0614…`, SHA incident et nouveau candidat non fournis |
| Qualification locale datée | PASS | L. 92–111 : Windows, Java 25.0.4, Wrapper 3.9.16, Docker Desktop 29.7.2 ; 2 440/5 ignorés/2 435 exécutés ; intégration 286/0 ignoré, zéro erreur/échec |
| Résolution Maven initiale séparée | PASS | L. 102–105 : tests non exécutés pendant l'échec de résolution ; succès autorisé ultérieur, historique |
| Politique actuelle de preuves | PASS | L. 64–76 et 138–154 : retrait des bundles automatiques, XML/upload obligatoires, rapport WO bloquant, Javadoc conditionnellement facultative |
| Purge historique délimitée | PASS | L. 37–48 : 22 bundles/1 202 214 595 octets ; caches deux/18 249 921 non purgés ; ni nouvelles suppressions ni gain immédiat revendiqués |
| Limites et qualification du nouveau candidat | PASS | L. 50–62, 108, 138–170 : 403, réglage GitLab historique, aucune purge GitLab, YAML distinct d'un pipeline ; preuves propres exigées |

Les valeurs et décisions concordent avec le rapport historique, le runbook, l'ADR et les workflows autorisés. Les statuts de contrôles sont qualifiés dans leur portée ; aucun ancien vert, packaging produit ou analyse de source ne devient une preuve d'exécution actuelle.

## Limites

- Cette revue est documentaire. Aucun contrôle applicatif ni CI réel n'a été lancé ou revalidé.
- Le catalogue est une reconstruction après exécution, explicitement signalée dans la trace. Le cas explicite permet de prouver la lecture par outil ; aucune découverte implicite ni injection automatique du corps par le CLI n'est revendiquée.
- Les événements natifs publiés omettent le raisonnement interne. Le modèle et l'effort exacts ne sont pas exposés.
- La provenance de lancement `658309c0e8d9d18e7d96396d8768ac90d8d16cca` reste distincte de la politique déclarée `57c0614…` et du SHA inconnu du run historique.

Revue structurée correspondante : `review-CS-H01.json`. Seuls les fichiers de revue demandés sont produits.
