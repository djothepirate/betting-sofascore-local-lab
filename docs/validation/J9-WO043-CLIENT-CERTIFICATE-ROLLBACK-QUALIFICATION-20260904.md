# J9 — qualification WO-043 du rollback fail-closed du certificat client WO-036

## 1. Identification

```text
WORK_ORDER=WO-SS-20260904-043-j9-wo036-client-certificate-fail-closed-rollback
BRANCH=codex/ss-20260904-043-j9-wo036-client-certificate-rollback
BASE_COMMIT=deca7e0c0e39e60f4c3647fd07fd4b0ca4901957
IMPLEMENTATION_COMMIT=f1e40da11e0c3f74661afcbb5de9dc458b385f60
QUALIFIED_AT_UTC=2026-09-04T07:50:42.9644685Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-04T09:50:42.9644685+02:00
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
PR26_REVIEW_FINDING=P2_RESOLVED_LOCALLY_PENDING_OWNER_VALIDATION
```

Cette preuve porte exclusivement sur l'outillage Windows de préparation et de nettoyage de la
campagne E2E synthétique WO-036. Elle ne modifie ni le sender Java, ni le receiver INT-001, ni le
contrat HTTP, ni les migrations, ni Docker Compose. Elle n'autorise aucune fusion, fermeture de
PR, campagne, donnée J7 réelle ou opération réseau applicative.

## 2. Défaut confirmé

Avant WO-043, `New-SelfSignedCertificate` créait le certificat client et sa clé privée dans
`CurrentUser\My`, puis les contrôles `HasPrivateKey`, EKU, Key Usage, politique CNG et l'export
public s'exécutaient avant l'ajout de l'identité à `state.OwnedCertificates`. Une exception pendant
cette fenêtre pouvait donc soustraire le certificat et sa clé au rollback fondé sur la propriété
exacte.

Le P2 de la PR `#26` est ainsi confirmé par l'ordre des opérations. La qualification ne déduit pas
de ce défaut qu'un résidu historique existait avant son exécution.

## 3. Correction qualifiée

Le commit `f1e40da11e0c3f74661afcbb5de9dc458b385f60` :

1. conserve l'objet exact renvoyé par `New-SelfSignedCertificate` dans une référence de portée
   script ;
2. place toutes les opérations post-création susceptibles d'échouer avant l'enregistrement dans
   un bloc de rollback local ;
3. calcule le thumbprint, le SHA-256 et le sujet exacts, puis enregistre cette propriété en mémoire
   avant toute validation post-création ;
4. persiste l'état de propriété avant `HasPrivateKey`, EKU, Key Usage, politique CNG et export ;
5. en cas d'échec avant enregistrement, exige une concordance magasin + thumbprint + SHA-256 +
   sujet avant de supprimer l'entrée exacte avec sa clé privée ;
6. emploie également `-DeleteKey` dans le rollback externe et le cleanup nominal du certificat
   client détenu ;
7. refuse de combiner une injection WO-043 avec le démarrage des bases ;
8. ajoute quatre points d'injection désactivés par défaut et un harnais de qualification
   strictement local.

Les suppressions fondées uniquement sur un PID, un sujet ou une énumération large du magasin
restent interdites. La racine privée d'une exécution n'est supprimée par le harnais que si son
chemin canonique, son enfant GUID immédiat, son SID propriétaire, son marqueur, son état privé,
l'absence de reparse point et l'absence de toute ressource détenue sont simultanément établis.

## 4. Qualification hôte Windows

Commande qualifiée :

```text
pwsh -NoLogo -NoProfile -File scripts/Invoke-WO043ClientCertificateRollbackQualification.ps1
```

Résultats des quatre échecs injectés :

| Point d'injection | Résultat | Durée | Résidu |
|---|---:|---:|---:|
| `AFTER_CLIENT_CERTIFICATE_CREATION_BEFORE_OWNERSHIP` | `PASS` | `9760 ms` | `0` |
| `AFTER_CLIENT_CERTIFICATE_OWNERSHIP_IN_MEMORY` | `PASS` | `7052 ms` | `0` |
| `AFTER_CLIENT_CERTIFICATE_OWNERSHIP_PERSISTED` | `PASS` | `6337 ms` | `0` |
| `BEFORE_CLIENT_CERTIFICATE_EXPORT` | `PASS` | `5583 ms` | `0` |

Le parcours nominal a ensuite préparé puis nettoyé une infrastructure sans démarrer les bases.
L'attestation finale du harnais est :

```text
WO043_CLIENT_CERTIFICATE_ROLLBACK_QUALIFICATION=PASS_LOCAL_FAIL_CLOSED
WO043_CLIENT_CERTIFICATE_FAILURE_POINTS=4_OF_4_PASS
WO043_CLIENT_CERTIFICATE_AND_PRIVATE_KEY_RESIDUE=0
WO043_PRIVATE_CAMPAIGN_ROOT_DELTA=0
WO043_PREEXISTING_LOOPBACK_LISTENERS_PRESERVED=YES
WO043_DATABASES_STARTED=NO
WO043_PROVIDER_CALLS=0
WO043_RECEIVER_HTTP_CALLS=0
```

Le postflight absolu observe :

```text
POST_CLIENT_CERTIFICATE_COUNT=0
POST_SERVER_ROOT_COUNT=0
POST_CAMPAIGN_GUID_ROOT_COUNT=0
POST_LISTENER_8087_COUNT=0
POST_LISTENER_8444_COUNT=0
POST_LISTENER_5432_COUNT=1
POST_LISTENER_5433_COUNT=0
PRIMARY_CONTAINER_STATUS=running|healthy|127.0.0.1:5432
```

Le listener `127.0.0.1:5432` et le conteneur primaire exact
`betting-sofascore-local-lab-postgres` préexistaient à la qualification. Ils sont restés
`running/healthy`, n'ont pas été ouverts par le harnais et n'ont été ni arrêtés, ni accédés, ni
purgés.

Des passes exploratoires antérieures au résultat retenu ont servi à qualifier l'interaction entre
le cleanup WO-036 et ce listener préexistant ainsi qu'un blocage transitoire d'import dans le
magasin `CurrentUser\Root`. Elles ne sont pas comptées dans les quatre mesures ci-dessus. Chaque
racine alors retenue a été supprimée uniquement après les contrôles exacts de confinement et de
propriété décrits plus haut ; le postflight final atteste l'absence de résidu.

## 5. Tests et garde-fous

| Contrôle | Résultat |
|---|---|
| Parse PowerShell des quatre scripts concernés | `PASS`, zéro erreur |
| Pester ciblé `WO036Infrastructure.Tests.ps1` | `18/18 PASS` |
| Pester complet `scripts/Tests` | `90/90 PASS` |
| `mvnw.cmd clean verify` | `PASS`, Surefire `1136/0/0/5`, Failsafe `89/0/0/0` |
| `mvnw.cmd -Pintegration-tests verify` | `PASS`, Surefire `1136/0/0/5`, Failsafe `89/0/0/0` |
| Convention de branche et tests associés | `PASS` |
| Garde `LOCAL_ONLY` et flags bloquants | `PASS` |
| Tests du scanner de secrets et signaux | `PASS` |
| Garde-fous de packaging | `PASS_LOCAL_ONLY` |
| Reproductibilité de distribution | `PASS_LOCAL_ONLY` |
| Launchers de distribution | `PASS_DIRECT_JAR` |
| UTF-8 strict sans BOM | `PASS` |
| `git diff --check` | `PASS` |

Le premier `mvnw.cmd clean verify` lancé dans l'environnement restreint n'a pas pu accéder au
cache Maven de l'hôte. La même commande exacte a été rejouée dans l'environnement hôte autorisé
et a terminé en `BUILD SUCCESS` en `04:29`, puis le profil d'intégration explicite a terminé en
`BUILD SUCCESS` en `03:58`. Cette limitation de sandbox n'est pas classée comme un échec du code.

## 6. Conclusion et portes maintenues

La fenêtre de fuite identifiée par le P2 est fermée et les quatre positions d'échec représentatives
sont qualifiées sans résidu. Le résultat est `PASS_LOCAL_FAIL_CLOSED`. Le Work Order reste actif
jusqu'à validation propriétaire et la remarque de PR reste seulement résolue localement à ce
stade.

```text
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_REAL_J7_EXPORT_TEST_AUTHORIZED=NO
J9_PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
INT001_PULL_REQUEST_AUTHORIZED=NO
INT001_VALIDATION_AUTHORIZED=NO
PR26_MERGE_AUTHORIZED=NO
PR25_CLOSE_AUTHORIZED=NO
```
