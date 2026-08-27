# AGENTS.md — SofaScore Local Lab

## Mission du dépôt

Ce dépôt est un laboratoire local, expérimental et non critique du Betting Project. Toute modification doit préserver les statuts :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## Socle obligatoire

- Java 25 LTS pour compilation et exécution ;
- Spring Boot 4.1.0 ;
- Maven via `mvnw` ou `mvnw.cmd` ;
- PostgreSQL local dans Docker Desktop ;
- application liée uniquement à `127.0.0.1` ;
- Eclipse comme IDE principal ;
- fichiers texte UTF-8.

## Commandes autorisées par défaut

```text
./mvnw clean verify
./mvnw -Pintegration-tests verify
./mvnw -Dspring-boot.run.profiles=local spring-boot:run

docker compose --env-file .env config
docker compose --env-file .env up -d postgres
docker compose --env-file .env down
```

Sous Windows, utiliser les variantes `mvnw.cmd` et les scripts `scripts/*.ps1`.

## Invariants de sécurité

1. Aucun appel réel vers SofaScore dans les tests standards.
2. Aucun endpoint réel ajouté sans Work Order et revue de l’ADR-SS-001.
3. Aucun proxy rotatif, changement automatique d’adresse, mécanisme furtif, résolution de challenge ou réutilisation de cookie/jeton. Playwright est le seul transport cible autorisé vers les endpoints SofaScore : il reste local, manuel, opt-in et limité aux parcours J3, J4, J5 ou futurs couverts par un Work Order et l'ADR-SS-001 v1.3. Chaque nouvel endpoint exige une allowlist et une revue dédiées. FlareSolverr est écarté comme transport et fallback ; ses preuves historiques restent conservées pour audit et toute réintroduction exige une nouvelle décision propriétaire explicite.
4. Aucun payload complet, cookie, jeton ou secret dans les logs.
5. Aucune écoute sur `0.0.0.0`, une IP LAN ou une interface publique.
6. Aucune dépendance du Betting Project principal à ce dépôt.
7. Aucun envoi de payload brut vers le VPS.
8. Aucune activation de polling, live ou tâche planifiée au J0/J1.
9. Les données brutes et normalisées restent séparées.
10. Toute donnée normalisée conserve le snapshot, le hash, le parseur et l’heure de réception.
11. Playwright ne démarre jamais automatiquement : ni au démarrage normal, ni pendant les tests standards, ni via scheduler ou polling. Chaque lancement exige une action opérateur explicite.
12. Chaque campagne Playwright utilise un contexte non persistant neuf ; aucun profil, cookie, `storageState`, HAR, trace, vidéo, capture ou téléchargement n'est conservé, journalisé ou ajouté à Git.

## Règles du jalon J1

- `ConnectorGate` doit rester bloquant.
- Les définitions du catalogue doivent rester `callable=false` et sans URI.
- `sofascore.enabled` reste `false` par défaut.
- Le profil Maven `sofascore-live-test` doit rester bloqué.
- Les boutons réseau de l’interface restent désactivés.

Toute modification de ces quatre points relève au minimum du jalon J3 et exige un Work Order séparé.

## Tests et définition de fini

Avant proposition de changement :

1. exécuter `mvnw clean verify` ;
2. exécuter `mvnw -Pintegration-tests verify` si les migrations ou la persistance changent ;
3. vérifier qu’aucun secret n’est présent dans le diff ;
4. vérifier que `server.address=127.0.0.1` reste effectif ;
5. vérifier qu’aucun appel réel n’est introduit dans les tests standards ;
6. mettre à jour le Work Order, le changelog et la documentation concernée ;
7. fournir la liste des fichiers modifiés, les commandes exécutées et leurs résultats.

## Répertoires et fichiers sensibles

- `ADR-SS-001-*.md` : modification uniquement après décision explicite.
- `docs/reference/*.pdf` : référence immuable ; ne pas réécrire.
- `.env` : fichier local ignoré, ne jamais ajouter à Git.
- `fixtures/` : aucun secret, cookie ou identifiant de session.
- `exports/` : contenu runtime ignoré, sauf `.gitkeep`.
- `src/main/resources/db/migration/` : migrations append-only ; ne pas modifier une migration déjà appliquée après partage du dépôt.

## Workflow Git

- branche principale : `main` ;
- une branche ou un worktree par Work Order ;
- état propre avant délégation ;
- aucun changement simultané par Eclipse et un agent dans le même worktree ;
- commits petits, explicites et reliés au Work Order ;
- revue humaine et réexécution des tests avant fusion.
