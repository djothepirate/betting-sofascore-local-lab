# WO-SS-20260912-059 — Plafond J3 de pagination manuelle à 35 pages

- **Statut :** `READY_FOR_REVIEW` — qualification ciblée et migration PostgreSQL réussies ; les deux suites globales ont été exécutées mais restent bloquées par l'échec V5 hors périmètre documenté dans le rapport de qualification.
- **Date de démarrage :** 2026-09-12.
- **Jalon :** J3 — collecte manuelle `SCHEDULED_EVENTS`, avec compatibilité d'audit J8.
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260912-059`.
- **Base :** `4f0413b`.
- **Worktree :** `.tmp/wo059-j3-pagination-limit-35`.
- **ADR :** [ADR-SS-006](../../../ADR-SS-006-j3-manual-pagination-cap-35.md).
- **Autorité fonctionnelle :** demande du propriétaire le 12 septembre 2026 de relever le plafond local de 25 à 35 pages après l'observation de 27 pages disponibles pour la date du jour.
- **Statuts conservés :** `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## 1. Objectif

Porter le plafond local de la pagination manuelle J3 `SCHEDULED_EVENTS` à 35 pages afin qu'une séquence normalement terminée en page 27 soit complète. La limite reste une barrière locale fixe : une page 35 qui porte encore `hasNextPage=true` ferme la collecte avec `PAGINATION_LIMIT_REACHED` avant toute tentative de page 36.

## 2. Périmètre inclus

- validation de domaine, de contrôleur, de preuve minimisée et de worker Playwright isolé pour les seules pages `1..35` ;
- pagination directe cache-first, séquentielle et à concurrence un ;
- import local atomique de `page-1.json` à `page-N.json`, où `1 <= N <= 35` ;
- conservation des limites de 5 Mio par page et 25 Mio par lot ;
- arrêt sans retry, sans polling, sans scheduler et sans tentative de page 36 ;
- migration append-only V53 pour la borne courante J8 à 35, avec lecture maintenue des campagnes historiques J8 à 25 ;
- tests unitaires, MVC, worker isolé et PostgreSQL isolé adaptés à la nouvelle limite ;
- documentation courante, ADR, changelog et rapport de validation du lot.

## 3. Hors périmètre

- tout nouvel endpoint, toute nouvelle origine ou toute modification d'allowlist ;
- toute modification du transport Playwright, des profils, cookies, jetons, comptes, sessions, proxy, redirection ou mécanismes de contournement ;
- polling, planification, collecte live, retry ou relance automatique ;
- modification des plafonds live J4/J5, du calendrier live-v9 ou des archives documentaires ;
- modification d'une migration Flyway déjà appliquée, réécriture des campagnes J8 historiques ou suppression de snapshots ;
- appel fournisseur pendant le développement, les tests ou la validation automatisée.

## 4. Règles de réalisation

1. Une nouvelle intention J3 déclare `max=35` et accepte seulement les pages 1 à 35.
2. Le terminal normal reste dicté par `hasNextPage=false` ; il peut donc apparaître en page 27.
3. Une page 35 avec `hasNextPage=true` crée le terminal `PAGINATION_LIMIT_REACHED`, avec page 36 refusée avant claim, persistance ou départ réseau.
4. Le cache frais `PARSED` est consulté avant tout transport. Les seuls départs fournisseur restent espacés d'au moins trois secondes ; les résolutions cache et imports locaux ne sont pas des départs fournisseur.
5. Une nouvelle campagne J8 `J3_SCHEDULED_EVENTS` est bornée à 35. Une campagne J8 historique déclarée à 25 reste lisible et conserve sa propre borne.
6. V53 élargit les seules contraintes nécessaires, append-only, sans mutation de données existantes.

## 5. Cas qualifiés

| Cas | Résultat qualifié |
|---|---|
| page 27, `hasNextPage=false` | `COMPLETED`, aucune page 28 demandée |
| pages 1 à 34 avec suite, page 35 sans suite | `COMPLETED` à la page 35 |
| pages 1 à 35 avec suite | `PAGINATION_LIMIT_REACHED`, aucune page 36 demandée |
| import local de 27 ou 35 pages terminales | lot accepté, zéro transport fournisseur |
| import local de 36 pages | refus avant claim, persistance ou mutation |
| nouvelle campagne J8 | `maximum_units=35` |
| campagne J8 historique | lecture de `maximum_units=25` conservée |
| migration V53 | contraintes 25/35 appliquées sans réécrire les données |

## 6. Validation exécutée

Le détail, les commandes et la limite connue des suites globales sont consignés dans [le rapport de qualification](../../validation/J3-PAGINATION-CAP-35-QUALIFICATION-20260912.md).

| Vérification | Résultat |
|---|---|
| tests ciblés J3/J8/MVC | 121 tests, 0 échec, 0 erreur, 1 ignoré |
| protocole worker Playwright isolé | 15 tests, 0 échec, 0 erreur |
| migration V52 vers V53 sur PostgreSQL Testcontainers | 74 tests, 0 échec, 0 erreur |
| construction sans tests | `mvnw.cmd -q -DskipTests package` réussie |
| `mvnw.cmd clean verify` | exécutée ; arrêt sur le hash V5 préexistant, hors périmètre |
| `mvnw.cmd -Pintegration-tests verify` | exécutée ; même arrêt Surefire V5 avant Failsafe |
| revue du diff | aucune erreur de whitespace ; seuls avertissements CRLF attendus des deux scripts PowerShell |

Aucun appel fournisseur n'a été effectué. La qualification PostgreSQL utilise uniquement Testcontainers et Docker local.

## 7. Définition de fini

1. les limites 25 actives du parcours J3 sont remplacées par 35 dans le code et la documentation courante, sans modifier les archives historiques ;
2. V53 est append-only et les lectures J8 historiques à 25 sont couvertes ;
3. les tests de frontière 27, 35 et 36 passent ;
4. les commandes globales requises sont exécutées et leurs résultats réels sont documentés ;
5. le diff est contrôlé pour les secrets, payloads, URI ou mécanismes hors périmètre ;
6. la revue humaine décide de la clôture du Work Order.