# WO-SS-20260808-002 — J2 Fixtures et premier contrat de parsing hors ligne

- **Statut :** `VALIDATED`
- **Date :** 2026-08-08
- **Date de démarrage :** 2026-08-11
- **Date de validation :** 2026-08-12
- **Prérequis :** WO-SS-20260808-001 validé sous Windows
- **Jalon :** J2 — Fixtures
- **Branche :** `feat/j2-scheduled-events-fixtures`
- **Commit de base :** `197bf01c10c813374f1b701c26ff958497b0d08a`
- **Base de l’unité parseur :** `3d0803e694a3d52597660139524d647c1ca2ec9f`
- **Base de l’unité tests de rupture :** `59c80fdb763f630cb9fa790c1053bab37e458adb`
- **Base de l’unité tableau de bord :** `b3076826ba60d82bfa379ae58250987a59521c44`
- **Commit technique validé :** `4ba0232e81f86c306df187887fcac49b46619b34`
- **Commit final de la branche :** `74245896be67d091787e8a67afa2f7b5cce257c8`
- **Commit de fusion sur `main` :** `b2561e542b1f893ec2f15c5eaeb67a361ee551ea`
- **Tag de base :** `j0-j1-v0.1.1`
- **Première famille :** `SCHEDULED_EVENTS`
- **Réseau autorisé :** `NO`
- **URI d’endpoint réelle autorisée :** `NO`
- **Appel SofaScore réel autorisé :** `NO`

## 1. Objectif

Introduire une première famille de réponse hors ligne, un contrat de parsing versionné et des tests de rupture de schéma, sans activer le réseau et sans ajouter de chemin d’endpoint réel.

## 2. Périmètre proposé

La famille initiale sera `SCHEDULED_EVENTS`, sous réserve de disposer d’un payload obtenu et conservé conformément aux règles du laboratoire.

Livrables attendus :

- format de métadonnées de fixture ;
- payload nominal minimisé ;
- payload avec champs absents ;
- payload avec type modifié ;
- payload HTML inattendu ;
- DTO externe minimal ;
- parseur `scheduled-events-v1` ;
- mapper vers un modèle local minimal ;
- détection explicite `SCHEMA_INCOMPATIBLE` ;
- canonicalisation JSON et calcul SHA-256 ;
- tests unitaires exhaustifs ;
- mise à jour du tableau de bord pour compter les fixtures disponibles.

## 3. Invariants

1. aucun appel réel pendant l’exécution de Maven ;
2. aucun URI réel dans le catalogue ;
3. `ConnectorGate` reste bloqué ;
4. aucune donnée de session ou secret dans les fixtures ;
5. conservation de la preuve brute avant parsing ;
6. champs inconnus tolérés mais tracés ;
7. champs obligatoires absents provoquent une incompatibilité explicite ;
8. aucune normalisation silencieuse ;
9. le parseur est versionné ;
10. le test reste reproductible sans Internet et sans PostgreSQL lorsque la persistance n’est pas concernée.

## 4. Matrice de tests minimale

| Scénario | Résultat attendu |
|---|---|
| nominal | objet parsé, champs obligatoires présents |
| champ facultatif absent | parsing réussi, avertissement si utile |
| champ obligatoire absent | `SCHEMA_INCOMPATIBLE` |
| nombre devenu texte | comportement documenté, pas de conversion implicite dangereuse |
| tableau vide | résultat valide ou incompatibilité selon le contrat |
| nouveau champ inconnu | parsing tolérant et information conservée |
| HTML au lieu de JSON | classification `UNEXPECTED_CONTENT` |
| même JSON avec ordre différent | hash canonique identique |
| contenu réellement différent | hash différent |
| secret factice détecté dans fixture | test ou vérification de dépôt en échec |

## 5. Critères d’acceptation

- [x] aucun changement du statut J1 réseau ;
- [x] fixtures documentées et minimisées ;
- [x] parseur versionné ;
- [x] tests nominaux et de rupture réussis ;
- [x] aucun appel réseau exécuté pendant le développement et la validation ;
- [x] rapport de décision indiquant les champs retenus, ignorés et obligatoires ;
- [x] préparation du Work Order J3 distinct, sans appel réel.

## 6. Décisions actées et restant à prendre

Décisions actées pour l’infrastructure générique :

- le corpus initial de neuf scénarios est déclaré `SYNTHETIC` avec `providerSchemaValidated=false` ;
- chaque payload versionné est accompagné d’un manifeste v1 et chargé depuis le classpath ;
- le hash brut porte sur les octets exacts conservés ;
- le hash JSON canonique trie récursivement les propriétés des objets et conserve l’ordre des tableaux ;
- les propriétés JSON dupliquées, le contenu résiduel et le JSON invalide sont rejetés ;
- chaque manifeste fixe une taille maximale, sous une limite absolue de 5 Mio ;
- la présence d’un motif de cookie, jeton, secret, identifiant de session ou clé privée bloque le chargement sans journaliser la valeur détectée.

Décisions actées pour `scheduled-events-v1` :

- `events`, `hasNextPage`, l’identité et l’horaire d’un événement, les deux équipes et le type de statut sont obligatoires ;
- `tournament` et `status.description` sont facultatifs et leur absence produit un avertissement structuré ;
- les champs inconnus sont ignorés pour le mapping et leur chemin est signalé par `UNKNOWN_FIELD` ;
- les identifiants exigent un entier 64 bits positif et aucune chaîne numérique n’est convertie ;
- une incompatibilité n’expose jamais de page locale partielle ;
- chaque résultat conserve l’identifiant de fixture, les hashes disponibles, la date du manifeste et la version du parseur ;
- le contrat complet est consigné dans `docs/architecture/SCHEDULED-EVENTS-V1.md`.

Décisions restant nécessaires avant une fixture représentative du fournisseur :

- méthode autorisée d’obtention de la première réponse ;
- politique exacte de minimisation ;
- liste des champs d’identité minimum ;
- politique de conservation dans Git d’un payload observé ;
- niveau de preuve requis pour l’origine de la fixture.

## 7. Avancement J2

- [x] format de manifeste v1 fortement validé ;
- [x] chargement classpath borné ;
- [x] SHA-256 brut ;
- [x] canonicalisation et SHA-256 JSON ;
- [x] classification `JSON`, `HTML` et `OTHER` ;
- [x] détection bloquante de motifs sensibles ;
- [x] corpus synthétique `SCHEDULED_EVENTS` ;
- [x] DTO externe et parseur `scheduled-events-v1` ;
- [x] mapper vers le modèle local ;
- [x] couverture des incompatibilités de schéma et du contenu HTML inattendu ;
- [x] mise à jour du tableau de bord.

## 8. État du corpus synthétique

```text
FIXTURE_FAMILY=SCHEDULED_EVENTS
FIXTURE_SCENARIOS=9
FIXTURE_ORIGIN=SYNTHETIC
PROVIDER_SCHEMA_VALIDATED=NO
PARSER_VERSION=scheduled-events-v1
DASHBOARD_CORPUS_AVAILABILITY=AVAILABLE_OFFLINE
DASHBOARD_FIXTURES_DECLARED=9
DASHBOARD_FIXTURES_AVAILABLE=9
DASHBOARD_PARSE_RESULTS=5
DASHBOARD_SCHEMA_INCOMPATIBLE_RESULTS=3
DASHBOARD_UNEXPECTED_CONTENT_RESULTS=1
NETWORK_AUTHORIZED=NO
REAL_SOFASCORE_CALL_EXECUTED=NO
```

Les scénarios versionnés sont : nominal, variante d’ordre des propriétés, champ facultatif absent, champ obligatoire absent, nombre devenu texte, tableau vide, champ inconnu, objet inattendu et HTML inattendu.

Le parseur classe les résultats en `PARSED`, `SCHEMA_INCOMPATIBLE` ou `UNEXPECTED_CONTENT`. Les tests valident désormais les neuf scénarios du corpus : nominal, variante d’ordre, absences facultatives, tableau vide, champs inconnus, champs obligatoires absents, nombre devenu texte, objet inattendu et HTML inattendu. Les tests de rupture vérifient également l’absence de page partielle et la conservation de la preuve de traçabilité.

## 9. Visibilité dans le tableau de bord

Le tableau de bord recharge les neuf manifestes depuis le classpath, applique les contrôles
d’intégrité existants et agrège les résultats de `scheduled-events-v1`. Il affiche le nombre de
fixtures déclarées et disponibles, la répartition `PARSED`, `SCHEMA_INCOMPATIBLE` et
`UNEXPECTED_CONTENT`, l’origine synthétique et l’absence de validation du schéma fournisseur.

Si une ressource ne peut plus être chargée ou vérifiée, le tableau de bord reste disponible et le
corpus passe à l’état `INCOMPLETE` avec un compteur d’échecs. Cet inventaire n’utilise ni réseau,
ni base de données, ni lecture d’un répertoire extérieur au classpath de l’application.

## 10. Validation et clôture

La validation Windows est consignée dans
`docs/validation/J2-WINDOWS-VALIDATION-20260812.md`. Elle établit notamment :

- la réussite des tests Maven hors ligne et des tests d’intégration PostgreSQL ;
- la disponibilité des neuf fixtures et la répartition attendue des résultats ;
- le rendu du corpus dans le tableau de bord local ;
- le maintien de la liaison à la boucle locale ;
- l’absence d’URI réelle, d’appel SofaScore, de déverrouillage du connecteur et d’accès au VPS ;
- l’arrêt propre de l’application et de PostgreSQL.

La Pull Request GitHub `#2` a été fusionnée sur `main` par un merge commit. Le jalon J2 est donc
clôturé avec le statut `VALIDATED`. Cette clôture valide le corpus synthétique et le comportement
hors ligne ; elle ne valide pas le schéma réel du fournisseur et n’autorise pas à elle seule un appel
réseau.

```text
J2_STATUS=VALIDATED
J2_WINDOWS_MANUAL_VALIDATION=PASS
J2_MERGE_COMMIT=b2561e542b1f893ec2f15c5eaeb67a361ee551ea
PROVIDER_SCHEMA_VALIDATED=NO
REAL_SOFASCORE_CALLS_EXECUTED=NO
J3_WORK_ORDER_REQUIRED=YES
```
