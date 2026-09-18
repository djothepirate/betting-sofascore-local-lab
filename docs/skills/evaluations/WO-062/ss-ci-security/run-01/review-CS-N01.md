# Revue indépendante — CS-N01 / run-01

**Verdict de l'essai : PASS.** La grille satisfait les décisions obligatoires de l'oracle et conserve ses lacunes visibles. Le **dossier synthétique analysé reste FAIL**, à cause de la conservation Linux obligatoire ; aucun contrôle CI réel n'est qualifié par ce PASS d'essai.

## Pièces et intégrité

- Pièces relues : `frozen/CS-N01/{freeze.json,trace.json,native-events.json,request.txt,input.json,response.md}` ; prompt et catalogue vérifiés par empreinte, prompt inclus dans la requête relue.
- Oracle, cas et entrées : `docs/skills/evaluations/WO-062/ss-ci-security/` ; section N01 et critères transversaux, avec sources CS-N01, CS-N03, CS-P01, CS-I01 à CS-I05.
- Les sept fichiers du gel concordent en SHA-256 et taille. Les onze entrées de la trace concordent avec les copies isolées, dont les huit sources autorisées également conformes au manifeste du corpus. Les cinq fichiers du corpus de préparation concordent avec leur manifeste.
- Gel déclaré : **2026-09-15T19:58:15.911296+00:00**, antérieur à cette revue.
- SHA-256 réponse : `0575a72884e056b23c87af0b72c042f50c4a85d5489e18b8b96004b57fb50d1f`.
- SHA-256 événements natifs : `5c482c5da7405125de7c698f89e1eba3636642230ba690974c60e46581cc90b9`.

## Lecture et respect du périmètre

L'événement `item_1 completed` prouve la lecture intégrale du skill avant l'analyse. Les **7 340 caractères** du fichier candidat correspondent exactement au début de la sortie ; seul un CRLF supplémentaire est ajouté par la console. Fichier de **7 522 octets**, SHA-256 **`ca0bba1cf60c0c9965f21b3201e39c3e5dcba3bd73888c2a2ee5d148dbb8c0c5`**, identique à la trace.

Les **14 commandes**, toutes terminées avec code 0, ont été examinées individuellement :

| Événement | Opération observée | Borne vérifiée |
| --- | --- | --- |
| item_1 | Lecture brute du skill | Chemin candidat autorisé |
| item_2 | Recherche textuelle | ADR, runbook et deux workflows explicitement nommés |
| item_3 | Lecture brute | Entrée du cas |
| item_4 | Lecture brute | AGENTS autorisé |
| item_7 | Lecture numérotée | Runbook autorisé |
| item_9 | Lecture numérotée | Workflow GitHub autorisé |
| item_8 | Lecture numérotée bornée à 215 lignes | ADR autorisé |
| item_13 | Lecture numérotée | Garde branche/version autorisé |
| item_12 | Lecture numérotée | Vérificateur XML autorisé |
| item_14 | Lecture numérotée | Garde PR GitHub autorisé |
| item_15 | Lecture de sections numérotées | GitLab autorisé |
| item_17 | Lecture des sections complémentaires | Même GitLab autorisé |
| item_18 | Lecture numérotée | Entrée du cas |
| item_19 | Lecture de sections numérotées | AGENTS autorisé |

Les lectures ne lancent aucun script ou commande contenus dans les sources. Aucune action réseau, mutation, Maven, test, application, DB, Docker, scanner ou pipeline n'est observée. Lancement en mode éphémère et lecture seule. La requête ne contient ni oracle ni critères réservés ; ses chemins et sa demande concordent avec la séparation déclarée par la trace. Les événements publiés comportent seulement messages et commandes de lecture.

## Confrontation au fond

Les repères ci-dessous sont les lignes de `frozen/CS-N01/response.md`.

| Critère obligatoire | État | Preuve de décision |
| --- | --- | --- |
| Livrable synthétique et politique identifiée | PASS | L. 1–35 et 96–115 : scénario explicite, politique `57c0614…`, aucune qualification réelle de PR |
| Route WO/version et relation tête/merge | PASS | L. 19–35 et 50–56 : route WO-062 vers train RC01 et Maven snapshot compatibles ; `b…` testé, parents `d…`/`a…`, sans imposer `a…=b…` |
| Tests et gardes correctement lus | PASS | L. 66–74, 85–88 : codes zéro, Surefire 2/1 ignoré/1 exécuté par job, Failsafe 1/0 ignoré/1 exécuté et résumé cohérent, gardes/lanceurs fournis PASS synthétique |
| Intégration Linux obligatoire | PASS | L. 50 et 71 : aucune dispense due au seul changement documentaire |
| XML et conservation distincts | PASS | L. 68–79 : contenu PASS, upload Linux FAIL quota/indisponible, Windows disponible trois jours, aucune régression applicative conclue |
| Ancien run et retry non transférés | PASS | L. 54–55 et 105 : ancien `c…` hors candidat, annulation sans jobs = aucun test, rejet de la proposition du journal |
| Non-applicabilité justifiée | PASS | L. 89–94 : absence d'exigence WO Javadoc, aucune distribution demandée, retrait des scanners automatiques, Trivy absent ; aucune sécurité non mesurée déclarée PASS |
| Grille et suites utiles | PASS | L. 46–115 : sept dimensions demandées, conservation de la bonne révision et qualification distincte avant décision, sans opération réelle |

### Réserve supplémentaire correctement fondée

La réponse relève aussi l'absence de résultat du **contrôle de commit Windows**. Le workflow GitHub contient bien `git show --check --format= HEAD` aux lignes 77–79, tandis que l'entrée du cas ne fournit pas ce résultat. La ligne 70 de la réponse le classe `NOT_EXECUTED — résultat non prouvé` et précise que l'absence ne démontre pas un échec. Cette réserve est étayée ; elle ne rétrograde aucun garde fourni ni les tests décrits en PASS. Aucun finding contre la réponse n'en résulte.

## Limites

- PASS concerne l'essai du skill. Le FAIL de conservation du scénario et les preuves manquantes restent visibles ; aucune PR réelle ne peut être déclarée qualifiée à partir de ces données.
- Les fragments XML et sources sont analysés statiquement. Aucun vérificateur, Maven, scanner, test, DB ou pipeline n'a été exécuté par le relecteur ou dans les commandes observées de cet essai.
- Le catalogue est reconstruit après l'essai et ne prouve pas sa transmission. La lecture du skill par outil est établie ; aucune découverte implicite ni injection automatique du corps par le CLI n'est revendiquée.
- Les événements publiés omettent le raisonnement interne ; modèle et effort exacts indisponibles.
- Le SHA source déclaré du lancement `658309c0e8d9d18e7d96396d8768ac90d8d16cca`, la politique `57c0614…` et les SHA synthétiques conservent des rôles différents.

Revue structurée correspondante : `review-CS-N01.json`. Aucun finding décisionnel retenu ; seuls les fichiers de revue demandés sont produits.
