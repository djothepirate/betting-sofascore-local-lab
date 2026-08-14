# Qualification technique et humaine Windows J4 — 2026-08-15

## 1. Statut

```text
TECHNICAL_QUALIFICATION=PASS
HUMAN_WINDOWS_VALIDATION=PASS
FUNCTIONAL_TEST=PASS
ENV_FILE_MODIFIED=NO
WORK_ORDER_STATUS=VALIDATED
REAL_SOFASCORE_CALLS=0
EVENT_DETAILS_PROVIDER_TRANSPORT=NOT_IMPLEMENTED
```

Cette qualification couvre l’implémentation, PostgreSQL, le parcours graphique local assisté et le
test fonctionnel humain du Work Order `WO-SS-20260815-004`. Elle ne constitue ni une approbation de
production, ni une autorisation réseau supplémentaire.

## 2. Environnement

| Élément | Valeur observée |
|---|---|
| Système | Windows, poste local |
| Java | 25.0.4 |
| Spring Boot | 4.1.0 |
| Maven | wrapper du dépôt |
| Docker | Docker Desktop 29.6.2 |
| PostgreSQL Testcontainers | 18.4 Alpine |
| Application | `127.0.0.1:8087`, profil `local` |
| Schéma Flyway | V5, cinq migrations validées |

## 3. Suites automatisées

### 3.1 Standard

Commande :

```powershell
.\mvnw.cmd clean verify
```

Résultat :

```text
BUILD=SUCCESS
TESTS=180
FAILURES=0
ERRORS=0
SKIPPED=0
JAVA_RELEASE=25
```

La suite couvre notamment l’identité canonique, les observations, les deux parseurs hors ligne,
les services d’import/normalisation/recherche et les contrôleurs MVC. Les transports J3 sont testés
par boucle locale ou serveur simulé ; aucun appel SofaScore n’est effectué.

### 3.2 PostgreSQL/Testcontainers

Commande :

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

Résultat :

```text
BUILD=SUCCESS
STANDARD_TESTS=180
INTEGRATION_TESTS=14
FAILURES=0
ERRORS=0
SKIPPED=0
FLYWAY_VERSION=5
```

Flyway a appliqué V1, V2, V3, V4 puis V5 sur une base PostgreSQL 18.4 éphémère. Les tests J4
vérifient les contraintes, les triggers append-only, la stabilité d’identité, la déduplication, la
persistance du détail, les recherches par date et la normalisation des snapshots locaux.

## 4. Parcours graphique local

L’application a démarré avec les deux activations réseau explicitement désactivées. Le healthcheck
Actuator a retourné `UP`, y compris pour PostgreSQL, puis Flyway a confirmé le schéma local V5.

Parcours exécuté dans le navigateur intégré, à une largeur de 1280 pixels :

1. ouverture de `/events?date=2026-08-12&zone=Europe%2FParis` ;
2. constat initial de zéro événement ;
3. sélection de **« Charger la démonstration J4 »** ;
4. apparition d’une unique identité
   `9740bb59-0207-31a3-a6ae-5c8463255887`, `providerEventId=900001` ;
5. affichage de deux versions, l’une issue de `scheduled-events-nominal`, l’autre de
   `event-details-nominal` ;
6. ouverture de la page de détail ;
7. vérification du stade `Synthetic Park`, de la saison `2026`, des deux hashes et des deux
   versions de parseur ;
8. contrôle de l’absence de débordement horizontal et de toute erreur/alerte dans la console du
   navigateur.

Les valeurs sont toutes synthétiques. Aucun payload fournisseur brut n’a été rendu ou copié dans
ce rapport. L’application a été arrêtée après le parcours.

### 4.1 Validation humaine fournie par le propriétaire

Le propriétaire a ensuite exécuté le test fonctionnel sans modifier `.env` et a fourni quatre
captures d'écran. Elles confirment visuellement :

- le connecteur général toujours verrouillé et les deux actions fournisseur désactivées ;
- l'accès `/events`, le badge **« AUCUN TRANSPORT FOURNISSEUR »** et zéro résultat pour la date
  initiale du `2026-08-15` ;
- le chargement synthétique, puis un résultat pour le `2026-08-12` avec le même UUID canonique,
  `providerEventId=900001`, `SYNTHETIC_FIXTURE` et deux versions ;
- le détail `event-details-v1`, le stade `Synthetic Park`, la ville `Local City`, la saison `2026`,
  le tour `1`, les deux SHA-256 et les deux observations append-only.

Les captures restent des pièces temporaires de la tâche Codex et ne sont pas versionnées. Elles ne
montrent aucun secret, cookie, jeton, payload fournisseur brut ou contenu de `.env`. Le statut Git
`!! .env` confirme uniquement que le fichier demeure ignoré et non versionné ; son contenu n'a pas
été lu ou modifié par l'agent.

## 5. Matrice J4

| Contrôle | Résultat | Preuve |
|---|---|---|
| UUID stable pour la même paire fournisseur/ID | PASS | tests domaine et PostgreSQL |
| nouvelle version sur changement métier | PASS | tests PostgreSQL |
| déduplication d’une version identique | PASS | tests PostgreSQL |
| conservation append-only | PASS | triggers V4/V5 testés |
| provenance snapshot ou fixture complète | PASS | contraintes et vues locales |
| rattachement du détail au mauvais événement | BLOCKED | tests service d’import |
| rupture `event-details-v1` | BLOCKED | tests parseur sans objet partiel |
| snapshot sans liste `events` | ZERO_EVENTS | test de normalisation |
| recherche date + zone IANA | PASS | tests service et parcours graphique |
| détail absent | LOCAL_ONLY_STATE | test MVC, aucun repli réseau |
| console navigateur | PASS | zéro erreur/avertissement |
| test fonctionnel humain | PASS | quatre captures fournies par le propriétaire |
| connecteur général visible | LOCKED | actions URI et fournisseur désactivées |
| fichier `.env` | UNCHANGED | déclaration du propriétaire, fichier toujours ignoré par Git |

## 6. Garde-fous vérifiés

- `server.address=127.0.0.1` reste versionné ;
- `sofascore.enabled=false` et `sofascore.j3-qualification-enabled=false` restent les défauts ;
- `automatic-refresh-enabled=false` et `live-polling-enabled=false` restent effectifs ;
- `ConnectorGate`, le catalogue, le POM, l’ADR et les PDF de référence ne sont pas modifiés ;
- chaque définition du catalogue demeure `manualOnly=true`, `callable=false` et
  `uriTemplateConfigured=false` ;
- aucune URI ajoutée ne vise un fournisseur ; seuls le localhost du runbook et les espaces de noms
  XML Thymeleaf apparaissent dans les ajouts ;
- le scan des ajouts ne trouve aucune affectation d’autorisation, cookie, session, bearer, clé API
  ou mot de passe ;
- `git diff --check` ne signale aucune erreur.

## 7. Conclusion et reste à faire

L’implémentation J4 et son parcours fonctionnel humain sont qualifiés dans le périmètre hors ligne.
La branche a été poussée et la Pull Request GitHub `#6` a été déclarée `MERGEABLE` et `CLEAN`, sans
check distant rapporté. Le Work Order est archivé dans `docs/work_orders/completed` au statut
`VALIDATED`. Les validations Maven finales ont ensuite confirmé 180 tests standards et 14 tests
d'intégration sans échec ni erreur avant la fusion autorisée vers `main`.

La validation humaine ne vaut pas autorisation de production ou d'appel fournisseur. Les statuts
du dépôt restent `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY`.
