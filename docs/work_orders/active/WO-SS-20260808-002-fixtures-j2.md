# WO-SS-20260808-002 — J2 Fixtures et premier contrat de parsing hors ligne

- **Statut :** `DRAFT`
- **Date :** 2026-08-08
- **Prérequis :** WO-SS-20260808-001 validé sous Windows
- **Jalon :** J2 — Fixtures

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

## 6. Décisions à prendre avant démarrage

- méthode autorisée d’obtention de la première réponse ;
- politique exacte de minimisation ;
- liste des champs d’identité minimum ;
- distinction entre hash brut et hash canonique ;
- politique de conservation dans Git ;
- niveau de preuve requis pour l’origine de la fixture.
