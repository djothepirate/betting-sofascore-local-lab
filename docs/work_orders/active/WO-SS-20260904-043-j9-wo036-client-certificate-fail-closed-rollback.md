# WO-SS-20260904-043 — Rollback fail-closed du certificat client WO-036

- **Statut :** `READY_FOR_OWNER_REVIEW`
- **Jalon :** après J9 — résolution du P2 de la PR de remplacement WO-042
- **Ouvert le :** 2026-09-04
- **Ouverture UTC :** `2026-09-04T07:08:48.6335317Z`
- **Ouverture Europe/Paris :** `2026-09-04T09:08:48.6335317+02:00`
- **Branche :** `codex/ss-20260904-043-j9-wo036-client-certificate-rollback`
- **Worktree :** `.tmp/j9-wo043-client-certificate-rollback`
- **Base exacte :** `deca7e0c0e39e60f4c3647fd07fd4b0ca4901957`
- **Pull Request concernée :** `#26`
- **Remarque concernée :** `P2 — Register the client certificate before fallible validation`
- **Permission officielle :** `NOT_EVIDENCED`

## 1. Autorisation propriétaire

Après validation et classement de WO-042, le propriétaire demande explicitement :

```text
Ensuite faire la résolution de P2
```

Cette instruction autorise un Work Order distinct pour diagnostiquer, corriger et qualifier la
remarque P2 du review de la PR `#26`. Le changement reste limité au harnais E2E synthétique WO-036,
à son cleanup exact et à ses tests. Il n'autorise ni fusion, ni fermeture de PR, ni reprise de
campagne, ni échange réseau applicatif.

```text
J9_WO043_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO043_WORK_ORDER=WO-SS-20260904-043-j9-wo036-client-certificate-fail-closed-rollback
J9_WO043_SCOPE=DIAGNOSE_CORRECT_AND_QUALIFY_WO036_CLIENT_CERTIFICATE_PRE_REGISTRATION_FAIL_CLOSED_ROLLBACK
J9_WO043_BASE_COMMIT=deca7e0c0e39e60f4c3647fd07fd4b0ca4901957
J9_WO043_ALLOWED_CHANGE=WO036_CAMPAIGN_CERTIFICATE_TOOLING_TESTS_AND_DOCUMENTATION_ONLY
J9_WO043_WINDOWS_CURRENT_USER_CERTIFICATE_QUALIFICATION_AUTHORIZED=YES_EXACT_SYNTHETIC_OWNERSHIP_ONLY
J9_WO043_PROVIDER_CALLS_AUTHORIZED=NO
J9_WO043_RECEIVER_HTTP_CALLS_AUTHORIZED=NO
J9_WO043_DATABASE_START_AUTHORIZED=NO
J9_WO043_PRIMARY_DATABASE_TOUCH_AUTHORIZED=NO
J9_WO043_BROAD_CERTIFICATE_CLEANUP_AUTHORIZED=NO
J9_WO043_PR26_MERGE_AUTHORIZED=NO
J9_WO043_PR25_CLOSE_AUTHORIZED=NO

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
```

## 2. Constat factuel

Dans `Initialize-J7LocalE2eInfrastructure.ps1`, `New-SelfSignedCertificate` persiste le certificat
client et sa clé privée dans `Cert:\CurrentUser\My`. Avant WO-043, l'identité n'est ajoutée à
`state.OwnedCertificates` qu'après les validations de clé privée, EKU, Key Usage, politique CNG et
après `Export-Certificate`.

Le rollback externe parcourt exclusivement `state.OwnedCertificates`. Une exception entre la
création et cet enregistrement peut donc laisser un certificat synthétique et sa clé privée hors de
la preuve de propriété et du cleanup. La remarque P2 est confirmée par l'ordre des instructions ;
aucun résidu historique n'est affirmé sans mesure.

## 3. Objectif

WO-043 doit :

1. enregistrer en mémoire l'identité exacte du certificat immédiatement après sa création et avant
   toute validation post-création susceptible d'échouer ;
2. persister cette propriété avant les validations métier du certificat ;
3. conserver un rollback de secours exact même si l'écriture de l'état privé échoue ;
4. supprimer le certificat et sa clé privée uniquement après concordance du magasin, du
   thumbprint, du SHA-256 et du sujet attendus ;
5. ne jamais effectuer de suppression globale ou fondée sur le seul sujet ;
6. injecter des échecs déterministes avant et après la persistance de propriété, puis avant
   l'export public ;
7. attester après chaque échec zéro nouveau certificat, zéro nouvelle clé CNG et zéro racine privée
   de campagne ;
8. préserver le chemin nominal et l'ensemble des invariants WO-036 ;
9. produire un rapport expurgé avant toute mise à jour de la PR `#26`.

## 4. Périmètre autorisé

Fichiers runtime autorisés :

- `scripts/Initialize-J7LocalE2eInfrastructure.ps1` ;
- `scripts/Remove-J7LocalE2eInfrastructure.ps1` si la suppression explicite de clé privée doit être
  renforcée ;
- un harness de qualification WO-043 strictement local et hors ligne ;
- les tests Pester et documents associés.

Sont hors périmètre : code Java du sender, receiver INT-001, contrat HTTP, migrations, contenu J7,
Docker Compose, démarrage des bases, endpoints, transport fournisseur, réseau distant, VPS et
production.

## 5. Invariants fail-closed

```text
CERTIFICATE_STORE=CurrentUser\My
CLIENT_CERTIFICATE_SUBJECT=EXACT_WO036_RUN_ID_BOUND
OWNERSHIP_MATCH=STORE_PLUS_THUMBPRINT_PLUS_SHA256_PLUS_SUBJECT
PRIVATE_KEY_REMOVAL=REQUIRED_FOR_EXACT_OWNED_CLIENT_CERTIFICATE
SUBJECT_ONLY_REMOVAL=FORBIDDEN
THUMBPRINT_ONLY_REMOVAL=FORBIDDEN
BROAD_STORE_ENUMERATION_FOR_DELETION=FORBIDDEN
QUALIFICATION_FAILURE_INJECTION=EXPLICIT_AND_DISABLED_BY_DEFAULT
QUALIFICATION_DATABASE_START=NO
QUALIFICATION_PROVIDER_CALLS=0
QUALIFICATION_RECEIVER_HTTP_CALLS=0
```

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY` restent inchangés.

## 6. Qualification attendue

- parse PowerShell et régressions Pester verts ;
- preuve d'ordre : propriété enregistrée avant `HasPrivateKey`, EKU, Key Usage, CNG et export ;
- échec injecté après enregistrement mémoire mais avant persistance : cleanup exact ;
- échec injecté après persistance mais avant validation : cleanup exact ;
- échec injecté avant export : cleanup exact ;
- après chaque cas : zéro delta dans les certificats synthétiques WO-036, les clés CNG utilisateur
  et les racines privées de campagne ;
- chemin nominal préparé puis nettoyé avec zéro résidu ;
- Maven standard et intégration, UTF-8, secrets, loopback et flags bloquants verts ;
- aucun appel fournisseur ou receiver HTTP.

## 7. Résultat de l'implémentation

```text
WO043_STATUS=READY_FOR_OWNER_REVIEW
WO043_IMPLEMENTATION_COMMIT=f1e40da11e0c3f74661afcbb5de9dc458b385f60
WO043_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WO043_REPORT=docs/validation/J9-WO043-CLIENT-CERTIFICATE-ROLLBACK-QUALIFICATION-20260904.md
WO043_REPORT_BYTES=7122
WO043_REPORT_SHA256=f412dc4b7c489412ffb8b3499dfe3a1350985c602b8b7d9a87659e0f7733665c
WO043_PESTER_TARGETED=18_OF_18_PASS
WO043_PESTER_FULL=90_OF_90_PASS
WO043_MAVEN_STANDARD=PASS_SUREFIRE_1136_FAIL_0_ERROR_0_SKIP_5_FAILSAFE_89_FAIL_0_ERROR_0_SKIP_0
WO043_MAVEN_INTEGRATION=PASS_SUREFIRE_1136_FAIL_0_ERROR_0_SKIP_5_FAILSAFE_89_FAIL_0_ERROR_0_SKIP_0
WO043_INJECTED_FAILURE_POINTS=4_OF_4_PASS
WO043_CLIENT_CERTIFICATE_AND_PRIVATE_KEY_RESIDUE=0
WO043_PRIVATE_CAMPAIGN_ROOT_DELTA=0
WO043_DATABASES_STARTED=NO
WO043_PROVIDER_CALLS=0
WO043_RECEIVER_HTTP_CALLS=0
PR26_REVIEW_FINDING=P2_RESOLVED_LOCALLY_PENDING_OWNER_VALIDATION
PR26_MERGE_AUTHORIZED=NO
PR25_STATUS=OPEN
PR25_CLOSE_AUTHORIZED=NO
```

La correction enregistre la propriété exacte avant les contrôles post-création, persiste cette
propriété avant les validations et supprime l'entrée exacte `CurrentUser\My` avec sa clé privée en
cas d'échec. Les quatre injections, le parcours nominal et les postflights hôte sont verts. Le
listener PostgreSQL primaire préexistant sur `127.0.0.1:5432` est resté `running/healthy`, sans
accès, arrêt ou purge par WO-043.

Le [rapport de qualification](../../validation/J9-WO043-CLIENT-CERTIFICATE-ROLLBACK-QUALIFICATION-20260904.md)
porte le SHA-256
`f412dc4b7c489412ffb8b3499dfe3a1350985c602b8b7d9a87659e0f7733665c`. Le Work Order reste
actif jusqu'à décision propriétaire explicite ; aucun push de WO-043, merge de `#26` ou fermeture
de `#25` n'est déduit de la readiness locale.

## 8. Bloc de revue propriétaire proposé

```text
J9_WO043_OWNER_REVIEW_DECISION=<VALIDATE|REJECT>
J9_WO043_WORK_ORDER=WO-SS-20260904-043-j9-wo036-client-certificate-fail-closed-rollback
J9_WO043_IMPLEMENTATION_COMMIT=f1e40da11e0c3f74661afcbb5de9dc458b385f60
J9_WO043_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
J9_WO043_QUALIFICATION_REPORT_SHA256=f412dc4b7c489412ffb8b3499dfe3a1350985c602b8b7d9a87659e0f7733665c
J9_WO043_LOCAL_READINESS_ACKNOWLEDGED=<YES|NO>
J9_WO043_WORK_ORDER_MOVE_TO_COMPLETED=<YES|NO>

J9_PR26_REVIEW_FINDING_STATUS=P2_RESOLVED_LOCALLY_PENDING_OWNER_VALIDATION
J9_PR26_MERGE_AUTHORIZED=NO
J9_PR25_STATUS=OPEN
J9_PR25_CLOSE_AUTHORIZED=NO
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
```
