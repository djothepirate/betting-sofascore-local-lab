# WO-SS-20260812-003 — J3 Appel manuel borné et conservation du brut

- **Statut :** `IN_DEVELOPMENT`
- **Date :** 2026-08-12
- **Date de démarrage :** 2026-08-12
- **Prérequis :** WO-SS-20260808-002 validé sous Windows et fusionné sur `main`
- **Jalon :** J3 — Appel manuel
- **Branche :** `feat/j3-manual-call`
- **Commit de base :** `b2561e542b1f893ec2f15c5eaeb67a361ee551ea`
- **Base de l’unité de politique réseau hors ligne :** `c51c45f4831dad2922018e25baee97dcf7bbcf5c`
- **Famille initiale :** `SCHEDULED_EVENTS`
- **Mode d’acquisition prévu :** `DIRECT_LOCAL_ENDPOINT`
- **Développement hors ligne J3 autorisé :** `YES`
- **URI réelle autorisée dans cette unité documentaire :** `NO`
- **Appel SofaScore réel autorisé dans cette unité documentaire :** `NO`
- **Polling ou tâche planifiée autorisé :** `NO`
- **Déploiement VPS autorisé :** `NO`

## 1. Objectif

Préparer puis qualifier un chemin d’appel manuel strictement borné pour une seule requête de la
famille `SCHEDULED_EVENTS`. La preuve de sortie visée par le cadrage est : une requête réelle,
déclenchée explicitement par l’opérateur, dont le statut, les horaires, la latence, la taille, le brut,
les empreintes et la version de parseur sont conservés localement, avec affichage JSON et gestion
explicite des incidents.

Le démarrage de ce Work Order autorise la conception, les tests hors ligne et les changements
réversibles nécessaires à J3. Il n’autorise pas encore l’envoi de la requête réelle. Le passage au
réseau exige le point de décision défini à la section 7.

## 2. Contexte hérité de J2

J2 fournit déjà :

- un corpus synthétique `SCHEDULED_EVENTS` de neuf scénarios ;
- le parseur hors ligne `scheduled-events-v1` ;
- le calcul SHA-256 brut et JSON canonique ;
- la classification `PARSED`, `SCHEMA_INCOMPATIBLE` et `UNEXPECTED_CONTENT` ;
- la détection bloquante de contenu sensible ;
- un tableau de bord local capable d’afficher l’état du corpus ;
- les verrous réseau J1 encore intacts.

Le schéma réel du fournisseur reste `NON VALIDÉ`. Aucun payload réel n’est actuellement conservé.

## 3. Périmètre J3 proposé

### 3.1 Inclus

- politique d’activation explicite et confirmation opérateur avant chaque appel ;
- concurrence maximale de `1` et délai minimal de `3 s` ;
- cache consulté avant le transport ;
- client HTTP synchrone avec délais de connexion et de lecture bornés ;
- une définition réelle limitée à `SCHEDULED_EVENTS`, introduite seulement après le point de décision ;
- validation stricte du seul paramètre de date autorisé ;
- enregistrement local des métadonnées de transport et du payload brut ;
- SHA-256 du brut et déduplication des réponses identiques ;
- parsing avec `scheduled-events-v1` après conservation de la preuve brute ;
- affichage local du JSON et du statut de compatibilité ;
- arrêt immédiat et sans retry sur `400`, `401` et `403` ;
- ouverture du circuit et suspension sur `429`, HTML inattendu ou schéma incompatible ;
- au plus un comportement de retry `5xx`, désactivé tant qu’une décision dédiée ne l’autorise pas ;
- tests de transport exclusivement simulés dans Maven ;
- procédure Windows de qualification et d’arrêt.

### 3.2 Exclus

- polling, rafraîchissement automatique ou planification ;
- plus d’un appel simultané ;
- parcours de dates ou collecte en lot ;
- recherche générale d’événements, détails de rencontre, statistiques, incidents ou compositions ;
- cookies, comptes, sessions, jetons ou en-têtes copiés depuis un navigateur ;
- proxy, rotation d’adresse, simulation ou automatisation de navigateur ;
- contournement d’un refus, d’un challenge ou d’un blocage ;
- stockage de payload brut dans Git, dans les logs ou dans le Betting Project principal ;
- transmission au VPS ;
- usage commercial, production ou dépendance critique ;
- modification de l’ADR-SS-001 ;
- live, benchmark de volume ou export canonique J7.

## 4. Invariants

1. `server.address=127.0.0.1` reste obligatoire.
2. `sofascore.enabled=false` reste la valeur par défaut.
3. Une activation explicite et une confirmation distincte précèdent tout appel.
4. Les tests standards ne disposent d’aucun chemin vers Internet.
5. Le profil réel reste bloqué jusqu’au point de décision humain.
6. Aucun secret, cookie, jeton ou identifiant de session n’est stocké ou journalisé.
7. Le payload brut est conservé avant toute tentative de parsing ou normalisation.
8. Les données brutes et normalisées restent séparées.
9. Une donnée normalisée référence le snapshot, le hash, le parseur et l’heure de réception.
10. Une réponse identique ne produit pas un second snapshot inutile.
11. Un refus ou challenge arrête l’expérimentation ; aucun changement d’adresse n’est tenté.
12. L’arrêt du laboratoire n’affecte jamais le Betting Project principal.
13. Les payloads bruts restent locaux et ne sont pas versionnés.
14. Aucun polling ou appel automatique n’est introduit.

## 5. Vérification préalable des conditions d’utilisation

La page officielle `https://www.sofascore.com/terms-and-conditions` a été revue le 2026-08-12,
conformément au déclencheur de réexamen du document de cadrage. La page consultée indique une
dernière mise à jour au 2024-09-18 et contient notamment des clauses relatives au contenu de base de
données, aux requêtes automatisées, à l’agrégation et au scraping.

Ce constat ne constitue pas une interprétation juridique. L’ADR-SS-001 ne fournit lui-même aucune
validation juridique ou contractuelle. En conséquence :

- aucun appel réel ne sera lancé sur la seule base de ce Work Order ;
- le propriétaire doit rendre une décision explicite sur la poursuite de la qualification ;
- toute clarification juridique, contractuelle ou de consentement jugée nécessaire doit intervenir
  avant le premier appel ;
- une décision négative ou incertaine maintient le connecteur verrouillé et peut conduire à limiter J3
  à une démonstration entièrement simulée.

## 6. Unités de livraison prévues

1. `docs: close J2 and start bounded J3 manual call` — terminé
   - clôturer le Work Order J2 ;
   - enregistrer la base J3 ;
   - documenter les limites et le point de décision.
2. `feat: add offline J3 network policy and circuit model` — terminé
   - modéliser activation, délai, concurrence, cache, arrêt et incidents ;
   - tester sans URI réelle et sans réseau.
3. `feat: persist raw manual-call snapshots`
   - ajouter uniquement les évolutions append-only nécessaires ;
   - tester avec Testcontainers ;
   - garantir la déduplication par hash et la séparation brut/normalisé.
4. `feat: add guarded scheduled-events transport`
   - introduire le transport derrière les politiques ;
   - utiliser un serveur simulé dans les tests ;
   - conserver le profil réel bloqué.
5. `feat: add explicit manual-call confirmation`
   - exposer l’activation, la confirmation, l’arrêt global et les incidents dans l’interface locale ;
   - garder les actions réelles désactivées en l’absence de configuration et d’autorisation.
6. `test: cover J3 transport stop and incident policies`
   - couvrir succès, timeout, `400`, `401`, `403`, `429`, `5xx`, HTML inattendu, taille excessive,
     schéma incompatible, déduplication et absence de secret dans les logs.
7. `docs: record J3 Windows manual-call qualification`
   - seulement après le point de décision et, si autorisé, une requête unique ;
   - consigner les preuves minimisées sans publier le payload brut.

Chaque unité part du commit précédent, reste autonome et fait l’objet d’un commit explicite. Une
modification de persistance déclenche obligatoirement le profil `integration-tests`.

## 7. Point de décision avant tout appel réel

Toutes les conditions suivantes doivent être remplies :

- [ ] décision explicite du propriétaire autorisant une requête unique `SCHEDULED_EVENTS` ;
- [ ] examen des conditions d’utilisation considéré suffisant par le propriétaire ;
- [ ] source et exactitude de l’URI documentées sans donnée de session ;
- [ ] paramètre de date unique validé ;
- [ ] tests standards hors ligne réussis ;
- [ ] tests d’intégration PostgreSQL réussis ;
- [ ] tests de transport sur serveur simulé réussis ;
- [ ] confirmation que `127.0.0.1`, concurrence `1`, délai `3 s` et arrêt global sont effectifs ;
- [ ] preuve qu’aucun cookie, jeton ou secret n’est requis ;
- [ ] sauvegarde ou stratégie de conservation locale décidée ;
- [ ] application et PostgreSQL démarrés localement ;
- [ ] procédure d’incident et d’arrêt disponible à l’écran et dans le runbook.

Si une case manque, la qualification s’arrête avant le transport.

```text
J3_WORK_ORDER=IN_DEVELOPMENT
J3_OFFLINE_DEVELOPMENT_AUTHORIZED=YES
J3_REAL_ENDPOINT_URI_AUTHORIZED=NO
J3_REAL_CALL_AUTHORIZED=NO
J3_POLLING_AUTHORIZED=NO
CONNECTOR_DEFAULT=DISABLED
LIVE_TEST_PROFILE=BLOCKED
```

## 8. Matrice de tests minimale

| Scénario | Résultat attendu |
|---|---|
| connecteur désactivé | refus avant résolution de l’URI |
| configuration absente | action réseau indisponible |
| confirmation absente | refus avant transport |
| concurrence déjà occupée | second appel refusé |
| délai minimal non écoulé | appel refusé ou différé sans attente agressive |
| cache valide | aucun transport, résultat existant utilisé |
| succès JSON simulé | brut conservé, hash calculé, parsing ensuite |
| réponse identique | aucun second snapshot utile |
| timeout | incident visible, circuit selon politique, aucun secret journalisé |
| `400`, `401`, `403` | arrêt sans retry |
| `429` | suspension, `Retry-After` conservé s’il existe, aucun retry rapide |
| `5xx` | aucun retry par défaut dans la première unité de transport |
| HTML inattendu | brut conservé, `UNEXPECTED_CONTENT`, circuit ouvert |
| schéma incompatible | brut conservé, `SCHEMA_INCOMPATIBLE`, aucune normalisation silencieuse |
| payload trop grand | arrêt borné et incident explicite |
| suite Maven standard | aucune connexion Internet possible |

## 9. Critères d’acceptation J3

- [ ] activation explicite nécessaire avant tout transport ;
- [ ] concurrence maximale égale à `1` ;
- [ ] cache et délai minimal vérifiés avant chaque appel ;
- [ ] une requête manuelle conserve statut, horaires, latence, taille, hash et parseur ;
- [ ] déduplication d’une réponse identique ;
- [ ] arrêt sans retry sur `400`, `401` et `403` ;
- [ ] suspension respectueuse de `Retry-After` sur `429` ;
- [ ] HTML inattendu visible et circuit ouvert ;
- [ ] schéma inconnu conservé brut et classé incompatible ;
- [ ] aucun test standard ne contacte Internet ;
- [ ] données brutes et normalisées séparées ;
- [ ] acquisition enregistrée comme `DIRECT_LOCAL_ENDPOINT` ;
- [ ] aucun secret, cookie ou jeton stocké ou journalisé ;
- [ ] aucun mécanisme de contournement ;
- [ ] derniers appels et incidents visibles dans le tableau de bord ;
- [ ] procédure Windows d’arrêt et de preuve exécutée ;
- [ ] appel réel, s’il est autorisé, limité à une requête unique.

## 10. Définition de fini

Avant toute proposition de fusion :

1. exécuter `mvnw.cmd clean verify` ;
2. exécuter `mvnw.cmd -Pintegration-tests verify` si la persistance change ;
3. confirmer que les tests standards sont entièrement hors ligne ;
4. contrôler le diff à la recherche de secret, cookie, jeton, payload brut et URI non autorisée ;
5. confirmer la liaison exclusive à `127.0.0.1` ;
6. mettre à jour le runbook, l’architecture, le changelog et le rapport de validation ;
7. obtenir une validation humaine Windows ;
8. fusionner seulement après revue humaine.

Le Work Order ne passe à `VALIDATED` qu’après satisfaction des critères applicables et validation de
la preuve de sortie J3. Une démonstration simulée peut valider l’architecture technique, mais ne vaut
pas preuve de sortie « une requête réelle » et doit être déclarée comme telle.

## 11. Avancement de la politique réseau hors ligne

L’unité `feat: add offline J3 network policy and circuit model` introduit :

- une évaluation pure donnant `USE_CACHE`, `BLOCKED` ou `TRANSPORT_ELIGIBLE` ;
- un ordre de décision déterministe couvrant activation, arrêt global, endpoint, confirmation,
  circuit, concurrence et délai minimal ;
- un circuit en mémoire démarrant à `LOCKED`, activable explicitement et ouvert par incident ;
- des incidents typés pour `400`, `401`, `403`, `429`, timeout, contenu inattendu, schéma
  incompatible et erreur serveur ;
- la conservation de `retryNotBefore` pour `429` sans réouverture ou retry automatique ;
- une garde atomique limitant à un le nombre de permis simultanés ;
- une documentation d’architecture et des tests unitaires entièrement hors ligne.

`TRANSPORT_ELIGIBLE` reste une information sans effet. `ConnectorGate` conserve un refus
systématique au mode `LOCKED_OFFLINE_J3_POLICY`, le catalogue reste non appelable et aucune URI
réelle n’est présente.

```text
J3_OFFLINE_POLICY=IMPLEMENTED
J3_CIRCUIT_MODEL=IMPLEMENTED
J3_SINGLE_CALL_GUARD=IMPLEMENTED
J3_INCIDENT_PERSISTENCE=NOT_STARTED
J3_RAW_SNAPSHOT_PERSISTENCE=NOT_STARTED
J3_TRANSPORT=NOT_STARTED
J3_REAL_ENDPOINT_URI_AUTHORIZED=NO
J3_REAL_CALL_AUTHORIZED=NO
```
