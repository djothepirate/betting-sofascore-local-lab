# Qualification technique Windows J5 — 2026-08-15

## 1. Statut

```text
TECHNICAL_OFFLINE_QUALIFICATION=PASS
HUMAN_OFFLINE_QUALIFICATION=PASS
PROVIDER_SCHEMA_VALIDATED=NO
APPLICATION_PROVIDER_TRANSPORT=NOT_IMPLEMENTED
DISCOVERY_PROVIDER_CALL_ATTEMPTS=1
DISCOVERY_PROVIDER_CALL_SUCCESSES=0
DISCOVERY_STOP_REASON=HTTP_403
MAVEN_PROVIDER_CALLS=0
ENV_FILE_READ_OR_MODIFIED=NO
HUMAN_EVIDENCE=8_SCREENSHOTS_REVIEWED_NOT_VERSIONED
REAL_CONDITIONS_CAMPAIGN=NOT_RUN
WORK_ORDER_STATUS=VALIDATED
```

Cette qualification couvre l'implémentation hors ligne du Work Order
`WO-SS-20260815-005` : modèles, parseurs synthétiques, complétude, persistance PostgreSQL,
déduplication, append-only et rendu MVC local. Elle ne qualifie ni le schéma réel actuel des trois
familles, ni un transport J5, ni un usage de production.

La preuve de sortie du cadrage — statistiques, incidents et compositions avec contrôles de
complétude — est satisfaite sur le corpus synthétique. La validation humaine locale du
2026-08-15 complète désormais la qualification technique et permet l'archivage du Work Order.

## 2. Environnement

| Élément | Valeur observée |
|---|---|
| Système | Windows, poste local |
| Branche | `codex/j5-statistics-completeness` |
| Base de branche | `c26dc2fe5791cde6030a29ad714dacffc99aa777` |
| Java | 25.0.4 |
| Spring Boot | 4.1.0 |
| Maven | wrapper du dépôt |
| Docker | Docker Desktop 29.6.2 |
| PostgreSQL Testcontainers | 18.4 Alpine |
| Schéma Flyway | V7, sept migrations validées |
| Liaison applicative versionnée | `127.0.0.1:8087` |

## 3. Suites automatisées finales

### 3.1 Standard hors ligne

Commande :

```powershell
.\mvnw.cmd clean verify
```

Résultat du 2026-08-15 :

```text
BUILD=SUCCESS
SOURCE_FILES_COMPILED=219
TEST_SOURCE_FILES_COMPILED=62
TESTS=232
FAILURES=0
ERRORS=0
SKIPPED=0
JAVA_RELEASE=25
SOFASCORE_CALLS=0
```

La suite couvre les trois parseurs J5, neuf fixtures et leurs hashes, les incompatibilités sans
objet partiel, les scores de complétude, l'import transactionnel, le refus d'une identité
incohérente, la requête et les trois états du contrôleur MVC. Les tests historiques J1 à J4 restent
verts. Aucun test ne résout les chemins fournisseur J5.

### 3.2 PostgreSQL/Testcontainers

Commande :

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

Résultat du 2026-08-15 :

```text
BUILD=SUCCESS
STANDARD_TESTS=232
INTEGRATION_TESTS=17
FAILURES=0
ERRORS=0
SKIPPED=0
POSTGRESQL=18.4
FLYWAY_VERSION=7
SOFASCORE_CALLS=0
```

Flyway a appliqué V1 à V7 sur une base éphémère. Le test J5 importe d'abord les trois lots
nominaux, vérifie cinq tables et leur contenu, répète l'import sans nouvelle ligne, puis ajoute une
version statistique `PARTIAL`, une version incidents `EMPTY_VALID` et une composition `PARTIAL`.
La lecture choisit les versions les plus récentes, reconstruit les modèles, conserve les chemins
manquants et refuse ensuite une tentative d'`UPDATE` par le trigger append-only.

Le nombre de `provider_snapshot` reste identique pendant l'import J5 : les fixtures classpath ne
sont ni déguisées en réponse fournisseur, ni recopiées dans le stockage brut J3.

## 4. Matrice technique J5

| Contrôle | Résultat | Preuve |
|---|---|---|
| statistiques nominales | `COMPLETE`, 3 métriques | parseur + PostgreSQL |
| valeur statistique extérieure absente | `PARTIAL`, score `75`, chemin conservé | parseur + PostgreSQL |
| incidents nominaux | `COMPLETE`, 3 lignes ordonnées | parseur + PostgreSQL |
| incidents explicitement vides | `EMPTY_VALID`, score structurel `100` | parseur + PostgreSQL |
| compositions nominales | `COMPLETE`, 2 côtés, 4 joueurs | parseur + PostgreSQL |
| composition non confirmée/incomplète | `PARTIAL`, absences documentées | parseur + PostgreSQL |
| identifiant événement ou joueur devenu texte | `SCHEMA_INCOMPATIBLE` | tests de rupture |
| minute devenue texte | `SCHEMA_INCOMPATIBLE` | tests de rupture |
| champ inconnu | avertissement structuré, parsing maintenu | tests parseurs |
| données partielles après rupture structurelle | aucune | invariant `J5ParseResult` |
| rattachement à un autre événement | refus avant écriture | test service |
| second import nominal | 3 lots dédupliqués | service + PostgreSQL |
| nouvelle version valide | nouvelle observation append-only | PostgreSQL |
| `UPDATE` d'un lot J5 | refus PostgreSQL | trigger V7 |
| page sans données | trois absences explicites, aucun repli | test MVC |
| page avec données | valeurs, scores, source, parseur et hashes rendus | test MVC |
| jeton de formulaire | consommé avant import | test MVC |
| transport J5 applicatif | absent | revue de code et architecture |
| suite Maven | zéro appel fournisseur | exécution standard/intégration |

## 5. Découverte réelle bornée

L'opérateur a fourni trois formes de chemin et six exemples : trois pour un événement commencé et
trois pour un événement pas encore commencé. Le Work Order a autorisé une découverte séquentielle,
sans cookie, jeton, en-tête personnalisé ou retry, avec arrêt au premier incident.

Les lecteurs Web et navigateur ont d'abord bloqué localement l'ouverture sans atteindre le
fournisseur. Le premier GET direct a ensuite ciblé uniquement la ressource de statistiques de
l'événement `16391135` et reçu :

```text
HTTP_STATUS=403
CONTENT_TYPE=application/json
RESPONSE_BYTES=48
RESPONSE_SHA256=6b771bca0fe4271cc0baf29954f10c68be99102048e4df524b6d7d6bf38dad5d
LATENCY_SECONDS=0.553132
RETRIES=0
REMAINING_EXAMPLES_CALLED=0
```

Le contenu complet de l'erreur n'est pas reproduit et n'est pas versionné. Aucun changement de
client, d'agent utilisateur, d'adresse, d'en-tête ou d'identité n'a été tenté. La campagne reste
`STOPPED_LOCKED`; les contrats J5 portent donc tous `providerSchemaValidated=false`.

## 6. Garde-fous vérifiés

- `server.address=127.0.0.1` reste versionné ;
- `sofascore.enabled=false` reste la valeur par défaut ;
- `ConnectorGate` et le profil Maven `sofascore-live-test` restent bloquants ;
- toutes les définitions du catalogue restent `callable=false` et sans URI ;
- J5 n'ajoute aucun `RestClient`, opt-in, propriété réseau, polling, tâche planifiée ou retry ;
- les sources et les tables normalisées sont séparées ;
- chaque lot conserve identité, fixture, SHA-256 source, parseur, heure et SHA-256 normalisé ;
- les cinq tables V7 sont append-only ;
- les fixtures sont synthétiques, bornées, sans cookie, jeton, session ou secret ;
- `.env`, l'ADR-SS-001 et le PDF de référence n'ont pas été modifiés ;
- `git diff --check` ne signale aucune erreur.

## 7. Validation humaine locale — `PASS`

Le propriétaire a exécuté le parcours de la section **3.10 bis** du runbook avec les propriétés
réseau bloquées, puis a déclaré les tests hors ligne concluants. Huit captures ont été revues dans
la conversation de qualification ; elles ne sont pas copiées dans Git et aucun payload brut n'est
repris dans ce rapport.

Les écrans attestent successivement :

1. la navigation depuis les fiches J4 vers la page J5 ;
2. l'état initial avec trois absences locales explicites et aucun repli réseau ;
3. l'identité synthétique canonique `900001` et son historique J4 append-only ;
4. l'import réussi des trois familles et leur rattachement à cette identité ;
5. `EVENT_STATISTICS · EVENT-STATISTICS-V1`, `COMPLETE · 100%`, `6/6` signaux ;
6. `EVENT_INCIDENTS · EVENT-INCIDENTS-V1`, `COMPLETE · 100%`, `3/3` signaux et trois incidents ;
7. `EVENT_LINEUPS · EVENT-LINEUPS-V1`, `COMPLETE · 100%`, `13/13` signaux, compositions
   confirmées et deux formations ;
8. les sources synthétiques, les parseurs, les heures de réception et les deux SHA-256 ;
9. `PROVIDER_SCHEMA_VALIDATED=NO`, sans action fournisseur J5 disponible ;
10. les bloqueurs réseau J4 toujours visibles dans le parcours local.

La répétition de l'import est déclarée concluante par l'opérateur et sa propriété d'idempotence est
également prouvée par la suite PostgreSQL/Testcontainers. Une fiche J4 réelle déjà persistée pour
`16412917`, au statut `inprogress`, montre que le lien J5 est disponible sans lancer de transport :
aucune donnée J5 fournisseur n'est créée et les trois familles y restent explicitement absentes.

Cette validation n'a pas appelé les six exemples réels, modifié `.env`, copié un payload ou
qualifié un usage de production.

## 8. Conclusion

L'implémentation J5 est qualifiée humainement et techniquement dans sa frontière hors ligne. La
preuve principale du jalon existe pour les trois familles avec une complétude explicite, une
provenance vérifiable et une persistance append-only. `TOURNAMENT_STANDINGS` est différé : il n'est
pas requis par la preuve de sortie du cadrage et n'a pas été ajouté pendant que les schémas réels
principaux restent non validés.

Le Work Order est archivable au statut `VALIDATED`. La campagne suivante annoncée en conditions
réelles reste `NOT_RUN` et n'est pas autorisée par ce Work Order : l'autorisation de découverte est
consommée et verrouillée depuis le `HTTP 403`. Une autorisation distincte devra précéder tout nouvel
appel. Les statuts du dépôt restent `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY`.

```text
J5_OFFLINE_MILESTONE=PASS
J5_HUMAN_OFFLINE_QUALIFICATION=PASS
J5_REAL_CONDITIONS_CAMPAIGN=NOT_RUN
J5_REAL_CONDITIONS_AUTHORIZED_BY_THIS_WORK_ORDER=NO
J5_WORK_ORDER_STATUS=VALIDATED
J5_CAN_BE_CLOSED=YES
```
