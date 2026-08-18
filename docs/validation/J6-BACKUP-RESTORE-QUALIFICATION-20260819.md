# Qualification opératoire J6 de la sauvegarde/restauration — 2026-08-19

## 1. Décision

```text
J6_OPERATIONAL_BACKUP_RESTORE_QUALIFICATION=PASS
J6_BACKUP_RESULT=QUALIFIED
J6_RESTORE_QUALIFIED=YES
J6_BACKUP_CIPHER_SHA256=a6be0577a50f2c11d8745b04d9bbae9e5283320009903d86205d47a5a83358f8
J6_BACKUP_MANIFEST_SHA256=b86497876598196e8930b5cfe7c10c53eaca8a3624e797c949b2f47887329cb5
J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID=272
J6_BACKUP_COVERAGE_RECEIVED_AT=2026-08-18T21:44:27.857664Z
J6_TEMPORARY_DATABASE_RESIDUAL=NO
J6_PRIMARY_DATABASE_PURGE=NOT_EXECUTED_NOT_AUTHORIZED
J6_WORK_ORDER_STATUS=VALIDATED
```

La sauvegarde chiffrée de la base locale a été restaurée dans une base PostgreSQL temporaire et
qualifiée par le script opérateur J6. Les treize mesures du manifeste sont identiques entre la
source et la restauration. Les hashes publiés par le script correspondent aux deux fichiers réels,
la base temporaire ne subsiste plus et aucun octet de la base primaire n'a été purgé.

## 2. Périmètre et environnement

| Élément | Valeur qualifiée |
|---|---|
| Branche | `codex/j6-history` |
| Correctif préalable | `714ec1c` |
| PowerShell | `7.6.5 Core` |
| age | `v1.3.1`, installation WinGet |
| Docker | client et serveur `29.6.2`, Docker Desktop `4.85.0` |
| PostgreSQL source | `18.4-alpine`, service `healthy` |
| Schéma | Flyway V22 |
| Application Web | arrêtée avant sauvegarde |
| Écoute locale 8087 après qualification | absente |
| Contrôle connecteur persistant | `SAFE` |
| Opt-ins fournisseur déclarés | tous à `false`, origine et endpoints vides |
| Appels fournisseur | `0` |
| Purge primaire | non exécutée et non autorisée |

La phrase secrète `age` a été saisie interactivement. Elle n'est présente ni dans le dépôt, ni dans
ce rapport, ni dans les arguments consignés. Les deux artefacts sont conservés hors dépôt.

## 3. Chronologie de qualification

### 3.1 Préqualification et refus sûrs

Le premier lancement a été refusé parce que l'application Web écoutait encore sur le port 8087.
Après arrêt de l'application, le lancement suivant a détecté une dérive SQL avant `pg_dump` : le
script référençait `payload_sha256` dans trois tables d'observations dont la colonne de provenance
réelle est `source_payload_sha256`.

Ces deux refus sont intervenus sans archive, sans manifeste, sans base de restauration temporaire
et sans purge. Le correctif `714ec1c` qualifie désormais explicitement les colonnes réelles et ajoute
une régression qui exécute les quatre requêtes du script contre PostgreSQL 18.4 au schéma V22.

```text
STANDARD_TESTS_AFTER_FIX=394_PASS
INTEGRATION_TESTS_AFTER_FIX=39_PASS
BACKUP_SCRIPT_SQL_AGAINST_V22=PASS
```

### 3.2 Exécution qualifiée

L'exécution corrigée a :

1. contrôlé l'arrêt de l'application et le verrouillage réseau ;
2. calculé les empreintes et la couverture de la base primaire ;
3. envoyé directement le dump PostgreSQL vers `age`, sans fichier de dump clair ;
4. créé une base temporaire au nom borné et restauré le dump déchiffré par flux ;
5. recalculé les treize mesures et vérifié leur égalité ;
6. marqué le manifeste comme qualifié uniquement après réussite ;
7. supprimé la base temporaire dans le bloc de nettoyage.

Le résultat opérateur final est `J6_BACKUP_RESULT=QUALIFIED`.

## 4. Artefacts hors dépôt

| Artefact | Chemin opérateur | Taille | SHA-256 réel |
|---|---|---:|---|
| archive chiffrée | `C:\SofaScoreBackups\sofascore-j6-20260818.age` | 1 645 333 octets | `a6be0577a50f2c11d8745b04d9bbae9e5283320009903d86205d47a5a83358f8` |
| manifeste qualifié | `C:\SofaScoreBackups\sofascore-j6-20260818.age.manifest.json` | 1 692 octets | `b86497876598196e8930b5cfe7c10c53eaca8a3624e797c949b2f47887329cb5` |

```text
MANIFEST_FORMAT_VERSION=1
MANIFEST_PURPOSE=J6_RAW_PAYLOAD_RETENTION_BACKUP
BACKUP_CREATED_AT=2026-08-18T22:46:19.1203591Z
BACKUP_QUALIFIED_AT=2026-08-18T22:47:37.2369445Z
RESTORE_QUALIFIED=true
```

Le hash de l'archive stocké dans le manifeste est identique au hash recalculé. Le hash du manifeste
recalculé est identique à celui rendu par le script. Le manifeste ne contient ni payload brut, ni
phrase secrète, ni cookie, ni jeton.

## 5. Parité source/restauration

| Mesure | Source | Restauration | Résultat |
|---|---:|---:|---|
| version Flyway | 22 | 22 | égal |
| snapshots | 239 | 239 | égal |
| occurrences | 239 | 239 | égal |
| observations canoniques | 86 | 86 | égal |
| observations de détails | 84 | 84 | égal |
| observations de données événement | 148 | 148 | égal |
| audits de purge | 0 | 0 | égal |
| snapshot maximal couvert | 272 | 272 | égal |
| réception maximale couverte | `2026-08-18T21:44:27.857664Z` | `2026-08-18T21:44:27.857664Z` | égal |
| échecs d'intégrité des payloads | 0 | 0 | égal |
| SHA-256 métadonnées snapshots | `e48819392f1bb04138f77db7f4f1556d04eead3241c49620592f9eceaa902583` | `e48819392f1bb04138f77db7f4f1556d04eead3241c49620592f9eceaa902583` | égal |
| SHA-256 occurrences | `2935445d9fa27a5544ecdd94880585649bc38dfca389fca0f59d51792ecedc0b` | `2935445d9fa27a5544ecdd94880585649bc38dfca389fca0f59d51792ecedc0b` | égal |
| SHA-256 provenances normalisées | `a2d64045f954d02aa90a62d81c501aa00f02f50d04e284a53325663703276765` | `a2d64045f954d02aa90a62d81c501aa00f02f50d04e284a53325663703276765` | égal |

## 6. Contrôles postérieurs

Les contrôles locaux en lecture seule effectués après la qualification confirment :

- aucune base dont le nom commence par `sofascore_j6_restore_` ;
- contrôle connecteur persistant `SAFE` ;
- aucun listener sur `127.0.0.1:8087` et aucun processus Java du laboratoire ;
- base primaire toujours à 239 snapshots, snapshot maximal 272 et même date de couverture ;
- table `j6_raw_payload_purge_audit` toujours vide ;
- aucun appel fournisseur et aucune purge.

Une seconde invocation avec la destination déjà qualifiée a été refusée avec le message
`The destination or its manifest already exists; choose a new backup name.` Ce résultat confirme
que le script ne remplace ni l'archive ni son manifeste et préserve leur immutabilité opératoire.

## 7. Conclusion

La sauvegarde/restauration réelle complète la readiness technique et la qualification humaine de
l'interface. J6 satisfait ses critères d'acceptation et peut être clos en `VALIDATED`. L'archive et
son manifeste restent hors Git sous la responsabilité de l'opérateur.

La purge de la base primaire n'est pas un critère de clôture J6. Elle demeure interdite dans ce Work
Order ; toute rétention destructive future doit faire l'objet d'une autorisation explicite, d'un
nouveau Work Order et d'un nouvel aperçu borné correspondant exactement à la sauvegarde retenue.
