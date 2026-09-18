# Revue indépendante — JM-H01 — run-01

## Verdict : FAIL

La réponse constitue une revue historique utile, techniquement prudente et sans faux résultat de test. Elle omet cependant une exigence obligatoire : expliquer que le refus d'accès au JAR `spring-orm-7.0.8.jar` dans le sandbox, avant tout test, était un incident d'environnement et non une régression du correctif. L'oracle exige tous ses points ; cette omission empêche PASS.

La réponse et le candidat restent intacts. Aucun test applicatif n'a été lancé par le relecteur.

## Identité et preuve de lecture

- Cas : `JM-H01` ; candidat `ss-java-module` `0.1.0-candidate.1`.
- SHA-256 candidat : `d20c8b838a9c4b9713e27c576e22272427d3f64fef50a670f5a09364cf849eb4`.
- SHA-256 YAML : `ccc4fc2a4707838dd6defdd73ef6f6c7ee0d69e6df86e41689a46884691573ae`.
- SHA-256 réponse : `865876bd7bec2ed33fca93ac79a93ccf7363785f85c98678d71024c62750c195`.
- SHA-256 événements natifs : `d5d9ea0b7b61ca152c7b4fcb942a8de5bd9cbdbec5d8b564764baf68e1141ad4`.
- Source métier : `6648dd423e556b5248b8793a539ae85a7680f9bc` ; préparation : `d89f9f2f3da136faf00976266202a4ff9b8c8c1f`.
- Gel observé avant toute lecture de réponse : `2026-09-15T21:11:00.181632+00:00`.
- Neuf pièces figées sur neuf : taille et SHA-256 recalculés conformes. Les 14 entrées et les 11 sources métier correspondent au preflight et au manifeste d'origine.
- `native-events.json`, événement 5, `item_1` : lecture `Get-Content -Raw` du candidat, sortie complète contenant exactement le texte du fichier, statut terminé, code 0, sans troncature. Les 17 commandes terminées lisent uniquement les sources autorisées et calculent des empreintes.

## Critères obligatoires

Les numéros de lignes renvoient à `frozen/JM-H01/response.md`.

| Critère | Verdict | Preuve / limite |
|---|---|---|
| Identité, responsabilités et module Maven | PASS | Lignes 38–58 et 194–196 : origine des fichiers, limites du HEAD non vérifié, classes, contrat et monomodule distingués. |
| Socle et invariants | PASS | Lignes 58–76, 162–186 et 203–204 : Java 25, Boot 4.1.0, wrapper, séparation worker, statuts et exception J3 ; binding et adresse effective hors corpus restent inconnus. |
| H01 — anomalie TCP | PASS | Lignes 9–16 : `65536`, deux anciennes gardes positives et deux méthodes exactes. Aucune interface architecturale inventée. |
| H02 — parent/enfant | PASS | Lignes 44–54 : environnement, validation autonome enfant et nécessité de conserver les deux barrières. |
| H03 — matrice et périmètre | PASS | Lignes 16–26, 47 et 118–120 : cinq bornes, acceptation structurelle seule, voisins déjà bornés, six familles et protocole hors correction. |
| H04 — révisions | PASS | Base `daf55bf…`, correctif `154349a…` et source courante distingués. |
| H05 — contrôles invoqués | PASS | Lignes 82–120 : validation directe/Bean Validation, vrai `claimExecution`, refus et état conservé, renforcement après revue, worker en mémoire. |
| H06 — portée historique / POM courant | PASS | Lignes 124–154 : quatre commandes historiques, suites complètes distinctes de la matrice, intégrations actuelles opt-in ; anciens totaux non utilisés comme résultat courant. |
| H07 — incident sandbox | **FAIL** | La section 5 du rapport autorisé expose le JAR illisible avant tests puis la relance offline réussie. La réponse ne traite pas cet incident ni son classement. L'absence d'autorisation réseau est, elle, correctement maintenue. |
| H08 — décision propriétaire et J3 adopté | PASS | Lignes 156–186 : chronologie correcte, pas de preuve à réécrire, autorité J3 actuelle et limites live. |
| H09 — vérification minimale | PASS | Lignes 188–227 : deux gardes et claim ciblés, future révision et contrôles applicables, commandes explicitement non exécutées. |
| Gouvernance / absence de faux vert | PASS | Aucune mutation, aucun système métier lancé, aucune exécution nouvelle inventée. Aucun changement de contrat ni de schéma proposé, donc relais spécialisés non nécessaires. |

## Limites de l'évaluation

La preuve de chargement est une lecture par outil ; elle ne prouve pas une injection automatique du corps du skill par le CLI. `catalogue.txt` est explicitement une reconstruction après exécution. Le modèle et l'effort effectifs ne sont pas exposés (`null` dans la trace) ; leurs valeurs ne sont pas inventées. Le gain global de temps et de qualité reste non mesuré.

La matrice structurée exhaustive, exploitable par le publisher via `case_id` et `verdict`, est conservée dans `review-JM-H01.json`.
