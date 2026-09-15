# Revue indépendante — JM-H01 — run-02

## Verdict : PASS

La réponse satisfait les critères obligatoires de l'oracle inchangé. Elle explique la borne TCP, préserve les validations indépendantes parent/enfant, décrit les appels de tests réels et leurs limites, restitue l'incident pré-tests et la chronologie propriétaire, puis propose des vérifications futures avec les relais appropriés.

Ce PASS qualifie la réponse documentaire du candidat `.2`. Il ne constitue ni exécution applicative, ni requalification du runtime, ni installation ou livraison. Le premier run demeure intact.

## Identité et preuve native

- Candidat `0.1.0-candidate.2`, commit `2d62f12c2d2ad4571c630aea0f98128927aa24ff`.
- SHA-256 candidat : `255655544771ae6cf2a71b967aba8efeb7e53e64acc41ca31dc784e16a719118`.
- SHA-256 réponse : `8b8818e00e902b318b1ef805e51cbc46cf6fc89459e458b6721f258f2c1793c3`.
- SHA-256 événements : `bcf0ba7d9556f87d6bc9f99634ea1e61b0a7a009c94b3899ecf91fdd45afcd32`.
- Oracle inchangé : `d13dc8c5ec833f30dc9025c3c22a22c00e60656eae3a89dc30361861ea390bd1`.
- Gel `2026-09-15T21:40:58.052697+00:00`, observé avant lecture de la réponse ; neuf pièces conformes, 14 entrées conformes dont 11 sources métier autorisées.
- Événement `item_1`, lecture `Get-Content -Raw`, code 0 : sortie contenant exactement l'intégralité du candidat. Les 20 commandes terminées sont des lectures/recherches locales et calculs d'empreintes autorisés.

## Matrice des critères

Les lignes renvoient à `frozen/JM-H01/response.md`.

| Critère | Verdict | Preuve / limite |
|---|---|---|
| Identité et architecture | PASS | Lignes 9–25, 68–97 : source, base, classes, responsabilités et monomodule distincts ; câblage absent non inventé. |
| Socle et statuts | PASS | Java25, Boot4.1, wrapper, loopback, séparation worker, quatre statuts et autorité J3 actuelle préservés. |
| H01 — anomalie | PASS | Lignes 31–38 : `65536` accepté par deux anciennes gardes positives, URI syntaxique distincte du port TCP. |
| H02 — parent/enfant | PASS | Lignes 38 et 72–75 : environnement et validation autonome du worker ; seconde barrière conservée. |
| H03 — matrice et contrats | PASS | Lignes 42–54 : absent/0/65536 refusés, bornes 1/65535 structurelles sans connexion ; voisins déjà bornés, familles/protocole distingués de l'extension future. |
| H04 — révisions | PASS | Base `daf55bf…`, ouverture `1c7c135…`, correctif `154349a…` et source courante distincts. |
| H05 — contrôles invoqués | PASS | Lignes 101–139 : validation directe/Bean Validation, vrai claim, refus/état conservé, renforcement historique et worker en mémoire. |
| H06 — portée historique | PASS | Lignes 145–172 : quatre commandes, suites complètes distinctes de la matrice pure, intégrations actuelles opt-in, anciens totaux non réutilisés. |
| H07 — incident sandbox | PASS | Ligne 158 : accès `spring-orm-7.0.8.jar` refusé avant tests, relance offline hors sandbox réussie, incident d'environnement distingué d'un échec applicatif. |
| H08 — propriétaire et J3 | PASS | Rapport en attente puis validation du WO ; autorité J3 adoptée, contexte live conservé et temporaire isolé, nettoyage requis, aucune extension générale. |
| H09 — vérification minimale | PASS | Lignes 205–229 : contrôles ciblés et généraux proposés, aucun exécuté ; trois relais spécialisés selon le périmètre. |
| Périmètre / faux vert | PASS | Aucun fichier hors allowlist, aucun système ou test métier lancé, aucune qualification actuelle inventée. |

Les modèles/efforts effectifs ne sont pas exposés par la trace. Le catalogue conservé est une reconstruction déclarée ; la lecture outil seule est prouvée, pas une injection automatique du CLI. Gain général de temps/qualité non mesuré. Critères détaillés et empreintes complémentaires dans `review-JM-H01.json`.
