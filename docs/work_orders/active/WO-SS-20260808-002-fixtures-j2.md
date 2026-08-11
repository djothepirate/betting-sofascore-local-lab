# WO-SS-20260808-002 — J2 Fixtures et premier contrat de parsing hors ligne

- **Statut :** `IN_DEVELOPMENT`
- **Date :** 2026-08-08
- **Date de démarrage :** 2026-08-11
- **Prérequis :** WO-SS-20260808-001 validé sous Windows
- **Jalon :** J2 — Fixtures
- **Branche :** `feat/j2-scheduled-events-fixtures`
- **Commit de base :** `197bf01c10c813374f1b701c26ff958497b0d08a`
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

- [ ] aucun changement du statut J1 réseau ;
- [ ] fixtures documentées et minimisées ;
- [ ] parseur versionné ;
- [ ] tests nominaux et de rupture réussis ;
- [ ] aucun appel réseau capturé ;
- [ ] rapport de décision indiquant les champs retenus, ignorés et obligatoires ;
- [ ] préparation du Work Order J3, sans l’exécuter.

## 6. Décisions actées et restant à prendre

Décisions actées pour l’infrastructure générique :

- le corpus initial de neuf scénarios est déclaré `SYNTHETIC` avec `providerSchemaValidated=false` ;
- chaque payload versionné est accompagné d’un manifeste v1 et chargé depuis le classpath ;
- le hash brut porte sur les octets exacts conservés ;
- le hash JSON canonique trie récursivement les propriétés des objets et conserve l’ordre des tableaux ;
- les propriétés JSON dupliquées, le contenu résiduel et le JSON invalide sont rejetés ;
- chaque manifeste fixe une taille maximale, sous une limite absolue de 5 Mio ;
- la présence d’un motif de cookie, jeton, secret, identifiant de session ou clé privée bloque le chargement sans journaliser la valeur détectée.

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
- [ ] DTO externe et parseur `scheduled-events-v1` ;
- [ ] mapper vers le modèle local ;
- [ ] mise à jour du tableau de bord.

## 8. État du corpus synthétique

```text
FIXTURE_FAMILY=SCHEDULED_EVENTS
FIXTURE_SCENARIOS=9
FIXTURE_ORIGIN=SYNTHETIC
PROVIDER_SCHEMA_VALIDATED=NO
PARSER_VERSION=UNASSIGNED
NETWORK_AUTHORIZED=NO
REAL_SOFASCORE_CALL_EXECUTED=NO
```

Les scénarios versionnés sont : nominal, variante d’ordre des propriétés, champ facultatif absent, champ obligatoire absent, nombre devenu texte, tableau vide, champ inconnu, objet inattendu et HTML inattendu.

À ce stade, le test du corpus valide le chargement classpath, les métadonnées, les tailles, les hashes et la forme synthétique annoncée. Les décisions `PARSED`, `SCHEMA_INCOMPATIBLE` et `UNEXPECTED_CONTENT` restent volontairement différées jusqu’au parseur `scheduled-events-v1`.
