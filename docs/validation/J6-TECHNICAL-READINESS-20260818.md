# Readiness technique J6 — 2026-08-18

## 1. Statut

```text
J6_TECHNICAL_READINESS=PASS
J6_HUMAN_HISTORY_UI_PHASE1=PASS
J6_HUMAN_HISTORY_UI_QUALIFICATION=IN_PROGRESS_IDEMPOTENCE_RETEST_PENDING
J6_OPERATIONAL_BACKUP_RESTORE_QUALIFICATION=PENDING
J6_PRIMARY_DATABASE_PURGE=NOT_EXECUTED_NOT_AUTHORIZED
J6_WORK_ORDER_STATUS=IN_DEVELOPMENT
PROVIDER_CALLS_DURING_IMPLEMENTATION_AND_TESTS=0
```

Cette preuve qualifie l'implémentation automatisée de l'historique, des différences, des
corrections tardives et de la rétention gardée. Elle ne transforme pas une restauration
Testcontainers en preuve de restauration de la base locale de l'opérateur et n'autorise aucune
purge sur cette base.

## 2. Environnement

| Élément | Valeur |
|---|---|
| Branche | `codex/j6-history` |
| Base J5 validée | `e1abb362b9da585c60311f4584eccfdd14a2642f` |
| Java | 25.0.4 |
| Spring Boot | 4.1.0 |
| Maven | wrapper du dépôt |
| Docker | Docker Desktop 29.6.2 |
| PostgreSQL Testcontainers | 18.4 Alpine |
| Schéma courant | Flyway V22 |
| Liaison versionnée | `127.0.0.1:8087` |
| Rétention par défaut | 30 jours, strictement avant cutoff |
| Lot maximal | 500 snapshots |

## 3. Résultats automatisés

### 3.1 Point de départ

Avant J6 :

```text
STANDARD_TESTS=369
INTEGRATION_TESTS=35
FAILURES=0
ERRORS=0
```

### 3.2 Suites J6 ciblées

Les tests du service historique, du hash de plan, du service de rétention et du tableau de bord
ont exécuté 13 scénarios, sans échec ni erreur. Ils couvrent notamment la première version
postérieure à un état terminal, une comparaison non adjacente qui franchit un état terminal, la
dérive de plan, la confirmation exacte, la couverture de sauvegarde et l'aperçu Web non
destructif.

Les deux scripts PowerShell J6 passent l'analyse syntaxique de PowerShell :

```text
Backup-Restore-J6.ps1=PARSE_OK
Invoke-J6Retention.ps1=PARSE_OK
```

### 3.3 Validation PostgreSQL et régression complète

Commande de preuve :

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

Résultat :

```text
BUILD=SUCCESS
STANDARD_TESTS=394
INTEGRATION_TESTS=38
FAILURES=0
ERRORS=0
SKIPPED=0
FLYWAY_FRESH_SCHEMA=V1_TO_V22_PASS
FLYWAY_UPGRADE=V21_TO_V22_PASS
POSTGRESQL=18.4
SOFASCORE_CALLS=0
```

La suite standard complète est également rejouée séparément par `mvnw.cmd clean verify` avant la
livraison du lot documentaire.

## 4. Matrice de preuve

| Contrôle | Résultat | Preuve automatisée |
|---|---|---|
| backfill des snapshots antérieurs | une occurrence `BASELINE` par snapshot | migration V21 |
| nouvelle réponse | occurrence `INSERTED` | persistance PostgreSQL |
| réponse brute identique | occurrence `DEDUPLICATED`, snapshot réutilisé | persistance PostgreSQL |
| occurrence modifiée ou supprimée | refus | trigger append-only V21 |
| cinq flux historiques | chargement ordonné et paginé | service + JDBC |
| taille de page | 25 par défaut, 100 maximum | domaine + MVC |
| comparaison arbitraire | même événement et même flux seulement | service + MVC |
| différences scalaires et collections | `ADDED`, `REMOVED`, `CHANGED` | tests unitaires |
| incident ambigu | retrait et ajout, aucune association inventée | tests de diff |
| score | dernier incident portant les deux scores | tests de diff |
| doublon technique | classification explicite | occurrence + classifieur |
| mêmes octets reparsés | `LOCAL_REPARSE` | classifieur |
| normalisation identique | `SEMANTICALLY_UNCHANGED` | classifieur |
| première version après fin | tardive | régression dédiée |
| correction fournisseur après fin | `LATE_CORRECTION` | classifieur + service historique |
| enrichissement fournisseur après fin | `LATE_ENRICHMENT` | classifieur + service historique |
| changement synthétique après fin | `SYNTHETIC_CHANGE` | corpus J6 |
| import J6 répété | aucune nouvelle version métier | service hors ligne |
| payload brut dans l'UI historique | absent | MVC + templates |
| purge Web | absente | contrôleur + template |
| candidat trop récent | exclu | requête de rétention |
| snapshot non normalisé | exclu | test PostgreSQL |
| statut incompatible | exclu | test PostgreSQL |
| plan obsolète | refus avant mutation | service + adaptateur |
| sauvegarde non qualifiée ou insuffisante | refus | service + adaptateur |
| purge autorisée en base éphémère | seuls les octets deviennent `NULL` | intégration V22 |
| métadonnées et provenance après purge | conservées | intégration V22 |
| inspection brute après purge | indisponible | intégration V22 |
| audit de purge | append-only et complet | intégration V22 |
| modification arbitraire d'un snapshot | refus PostgreSQL | trigger V22 |
| suppression d'un snapshot | refus PostgreSQL | trigger V22 |

## 5. Sauvegarde et restauration

Le script de qualification impose :

- application arrêtée ;
- contrôle réseau persistant `LOCKED` ;
- Flyway V22 ;
- destination absolue `.age` hors dépôt ;
- aucune écriture d'un dump en clair ;
- phrase secrète interactive, absente des arguments ;
- restauration vers une base temporaire au nom borné ;
- égalité des comptes et des empreintes de snapshots, occurrences et provenances normalisées ;
- vérification du hash réel des payloads retenus ;
- manifeste qualifié seulement après restauration complète ;
- suppression finale de la base temporaire.

Le script d'exécution revérifie le hash du fichier chiffré, les blocs source/restauration du
manifeste, Flyway V22, la couverture, tous les opt-ins réseau et le verrou persistant. La commande
Java s'exécute avec `WebApplicationType.NONE`.

Ces propriétés sont qualifiées par analyse syntaxique, revue de code et tests du service/de la
base éphémère. L'exécution interactive réelle avec `age` reste `PENDING`.

## 6. Garde-fous maintenus

- `server.address=127.0.0.1` ;
- `sofascore.enabled=false` par défaut ;
- aucun nouvel endpoint réel ni URI fournisseur ;
- `ConnectorGate`, catalogue `callable=false` et profil live inchangés ;
- aucun polling, ordonnanceur, retry, proxy, cookie ou jeton ;
- aucun payload brut dans les logs, le manifeste de sauvegarde ou l'audit de purge ;
- séparation brute/normalisée maintenue ;
- snapshot, SHA-256, parseur et heure de réception conservés ;
- migrations V1 à V20 non modifiées ; V21 et V22 ajoutées en append-only ;
- `.env`, l'ADR et le PDF de référence non versionnés ou réécrits par J6.

## 7. Validations humaines restantes

### 7.1 Interface

La phase 1 est acceptée sur seize captures opérateur externes : chargement du corpus, cinq flux,
chronologie postérieure à l'état terminal, différences, comparaisons consécutives et arbitraires,
pagination, provenance, absence de payload brut et absence de bouton de purge. Le rapport détaillé
est `J6-HUMAN-HISTORY-UI-PHASE1-20260819.md`.

Le premier résultat affiche six versions ajoutées et six déjà présentes. L'opérateur doit encore
relancer l'import pour prouver `0 ajoutée / 12 déjà présentes`, confirmer que le total reste à
douze, puis arrêter l'application. La confirmation visuelle du verrou persistant pourra être jointe
au même lot final. Les classifications tardives de provenance fournisseur restent qualifiées par
les tests automatisés.

### 7.2 Sauvegarde/restauration

L'opérateur doit exécuter le script avec `age`, conserver les deux fichiers hors dépôt, vérifier le
résultat `J6_BACKUP_RESULT=QUALIFIED` et confirmer que la base temporaire a été supprimée.

### 7.3 Purge primaire

Elle n'est pas nécessaire pour qualifier l'historique et n'est pas autorisée par le Work Order
actuel. Si elle devient souhaitée, une autorisation distincte doit précéder le mode `Execute`, puis
le lot minimisé et les contrôles postérieurs doivent être consignés.

## 8. Conclusion

```text
SNAPSHOT_HISTORY=TECHNICALLY_TRACEABLE
SEMANTIC_DIFF=TECHNICALLY_AVAILABLE
LATE_CORRECTIONS=TECHNICALLY_DETECTED
HISTORICAL_RECORDS_MUTATED=NO
BACKUP_AND_RESTORE_IMPLEMENTATION=READY_FOR_HUMAN_QUALIFICATION
RAW_RETENTION=MANUAL_BACKUP_GATED
HUMAN_HISTORY_UI_PHASE1=PASS
HUMAN_HISTORY_UI_FINAL=PENDING_IDEMPOTENCE_RETEST
J6_CAN_BE_CLOSED=NO
```

J6 a franchi la première phase de qualification humaine de l'interface sans appel fournisseur. Le
rejeu idempotent et la qualification opératoire de sauvegarde/restauration restent ouverts. Le Work
Order doit rester dans `docs/work_orders/active` et conserver le statut `IN_DEVELOPMENT` jusque-là.
