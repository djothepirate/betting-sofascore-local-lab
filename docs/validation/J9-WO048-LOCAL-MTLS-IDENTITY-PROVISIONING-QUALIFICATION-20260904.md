# J9 — Qualification WO-048 du provisionnement d’identités mTLS locales

## 1. Résultat

```text
WORK_ORDER=WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning
BRANCH=codex/j9-wo048-wo046-local-mtls-identity-provisioning
BASE_COMMIT=e771c2a5fedc508fbd0a420ae4596166251d82cd
QUALIFIED_RUNTIME_COMMIT=063c91f2de57330ca2b6baf3753d7de4ea921881
QUALIFIED_AT_UTC=2026-09-04T21:47:47Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-04T23:47:47+02:00
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
HOST_FAILURE_INJECTIONS=7_OF_7_PASS
HOST_NOMINAL_RUN=PASS
POST_PROCESS_CLIENT_IDENTITY_COUNT=1
POST_PROCESS_RECEIVER_TRUST_IDENTITY_COUNT=1
PROVIDER_CALLS=0
REMOTE_RECEIVER_CALLS=0
TLS_HANDSHAKES=0
SOCKETS_OPENED=0
HOST_PROVISIONING_DATABASE_STARTS=0
PRIMARY_DATABASE_STARTS=0
PRIMARY_DATABASE_READS=0
PRIMARY_DATABASE_WRITES=0
APPLICATION_STARTS=0
```

WO-048 a produit et qualifié un jeu neuf d’identités mTLS locales lié à un run destiné à la
future campagne WO-046. L’outillage reste PKI-only : il ne démarre ni le Local Lab, ni le receiver,
ni PostgreSQL, n’ouvre aucune socket et n’effectue aucun handshake. Le résultat est
`PASS_LOCAL_FAIL_CLOSED` et peut être soumis à la revue propriétaire.

La preuve versionnée est volontairement expurgée. Elle ne contient aucune empreinte de
certificat, aucun sujet exact, aucun numéro de série, aucun chemin privé, aucun secret, aucun
certificat et aucun matériel de clé. L’identité exacte des objets conservés demeure uniquement
dans l’état externe privé autorisé.

## 2. Portée et invariants préservés

La qualification couvre uniquement :

- la création d’une identité cliente neuve dans le magasin utilisateur Windows, avec clé CNG
  non exportable et usage de signature explicitement fixé ;
- la création d’une identité serveur receiver locale et de ses artefacts privés bornés au run ;
- l’ajout du certificat public serveur exact dans le magasin de confiance utilisateur ;
- la sélection hors ligne des identités par leur état privé exact ;
- les vérifications Java 25 des magasins Windows sans handshake ;
- le rollback par propriété exacte sur chaque point d’échec injecté ;
- la conservation intentionnelle du seul run nominal réussi pour WO-046.

Les invariants suivants restent inchangés :

```text
CLIENT_PRIVATE_KEY_EXPORT=NO
CERTIFICATE_FINGERPRINT_IN_GIT=NO
DATABASE_ACCESS=NO
RECEIVER_OR_LOCAL_LAB_START=NO
TLS_HANDSHAKE_OR_SOCKET=NO
WO046_MANIFEST_CREATION_AUTHORIZED=NO
WO046_OWNER_GO_GRANTED=NO
WO046_REAL_POST_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## 3. Historique qualifié de l’implémentation

La branche part de la base exacte `e771c2a5fedc508fbd0a420ae4596166251d82cd`. La chaîne de
commits runtime et documentaire examinée jusqu’au runtime final est :

```text
05d3f2bdba595f9188aa89b76e409873abad6077 docs(wo-048): open local mtls identity provisioning
9755dd6921cff0bdabb1d112f9630ce046ff4db5 feat(wo-048): provision bounded local mtls identities
6c3c59c8dd20d5575d3b6f986ba8274f353c28c3 fix(wo-048): preserve pinned module scope
dc38177e1a225620309f1fb69251c2b7b87fc9de fix(wo-048): classify client identity failures
b556cb034a64d3a26a7c8f2209212bf5c41ab99d fix(wo-048): refine client identity failure phases
95a8b6d58928e8e4a78b94b65b745ffb0bdbde4f fix(wo-048): preserve bounded failure codes
88c9a59cbbae3e358dd33441a7ca45a7de341bb2 fix(wo-048): classify native crypto failures safely
874bcc3c6d816df3bc4fcb6eb620707078dd5888 fix(wo-048): pin CNG signing identity
063c91f2de57330ca2b6baf3753d7de4ea921881 fix(wo-048): harden receiver trust provisioning
```

Le commit `874bcc3c6d816df3bc4fcb6eb620707078dd5888` fixe explicitement le profil de clé cliente CNG à
une identité de signature non exportable. Le commit
`063c91f2de57330ca2b6baf3753d7de4ea921881` remplace le chemin instable d’import de confiance par
une écriture directe et vérifiée au moyen de `X509Store.Add`, encadrée par une déclaration durable
de propriété avant l’effet et par une validation exacte après l’effet.

## 4. Incidents de qualification et corrections

### 4.1 Profil de clé cliente CNG

Une première tentative hôte a échoué pendant la création ou l’utilisation de la clé cliente avec
le code natif expurgé `0x80090017`. Le profil demandait alors `KeySpec=Signature`, combinaison qui
n’était pas compatible avec le chemin CNG effectivement exercé. Le correctif du commit
`874bcc3c6d816df3bc4fcb6eb620707078dd5888` impose :

```text
KEY_SPEC=None
KEY_USAGE_PROPERTY=Sign
KEY_USAGE=DigitalSignature
PRIVATE_KEY_EXPORT_POLICY=None
```

La qualification finale confirme une clé cliente CNG non exportable, apte à signer et sélectionnée
de manière unique, sans export de clé privée.

### 4.2 Import de la confiance receiver

Après correction CNG, au moins deux exécutions complètes se sont arrêtées sur l’import de la
confiance receiver avec `0x80070057`. Les échecs se produisaient dans le chemin
`Import-Certificate`; chaque tentative a exécuté son rollback et laissé zéro objet possédé ou
racine de run résiduelle.

Le runtime final utilise directement `X509Store` en mode utilisateur :

1. refus préalable de toute collision exacte ;
2. persistance de l’autorité de cleanup avant l’effet d’ajout ;
3. ajout de l’objet public exact par `X509Store.Add` ;
4. contrôle post-ajout de la cardinalité et de l’identité exactes ;
5. promotion à l’état `OWNED` seulement après validation ;
6. libération systématique des objets de magasin.

Cette séquence permet également de récupérer de façon bornée un échec survenant après l’effet
d’ajout mais avant la fin de la validation.

## 5. Qualification fail-closed par injection

Les sept points d’échec autorisés ont été injectés séparément. Pour chacun, la phase attendue a
été identifiée exactement, l’échec a été classifié sans exposer de message natif sensible, puis le
rollback de propriété exacte a été vérifié.

```text
FAILURE_INJECTION_CASES=7
FAILURE_INJECTION_PASS=7
FAILURE_INJECTION_FAILURES=0
ROLLBACK_PASS=7
OWNED_CERTIFICATE_RESIDUE_AFTER_FAILED_RUNS=0
OWNED_PRIVATE_RUN_ROOT_RESIDUE_AFTER_FAILED_RUNS=0
UNOWNED_OBJECT_TERMINATION_OR_DELETION=0
PID_ONLY_TERMINATION_OR_CERTIFICATE_SUBJECT_ONLY_DELETION=0
```

Le chiffre `RESIDUE=0` s’applique aux exécutions volontairement mises en échec. Il ne s’applique
pas au run nominal réussi : les deux identités exactes et les artefacts privés de ce dernier sont
conservés intentionnellement pour une future reprise autorisée de WO-046. Cette conservation est
un résultat attendu, pas un résidu de rollback.

## 6. Run nominal conservé et audit après processus

Le run nominal final conservé n'est pas identifié dans Git. La preuve versionnée ne publie que
l'engagement SHA-256 de son état privé complet, sans révéler le UUID, l'identité des certificats
ou le contenu engagé :

```text
NOMINAL_RUN_REFERENCE=PRIVATE_EXTERNAL_ONLY
PRIVATE_IDENTITY_RECORD_SHA256=29c53ef4b27cd9782bc49f52b5594152f12d0ca7f96fd7209052ea90f6cc7475
SELECTED_CLIENT_IDENTITY_COUNT=1
SELECTED_RECEIVER_IDENTITY_COUNT=1
CLIENT_PRIVATE_KEY_PROFILE=CNG_NON_EXPORTABLE
JAVA_25_PROVENANCE=PASS
JAVA_SUNMSCAPI_STORE_QUALIFICATION=PASS_NO_HANDSHAKE
```

L'état engagé contient les secrets et identités nécessaires au futur usage et au cleanup exact ;
il reste sous ACL privée hors dépôt. Son digest ne les expose pas et ne doit pas être décrit comme
un relevé de métadonnées sans secret.

Un audit indépendant exécuté après la fin du processus de provisionnement a relu l'état privé
et recherché chaque objet par son identité exacte, sans afficher les valeurs privées. Il confirme :

```text
POST_PROCESS_PRIVATE_RECORD_SHA256_MATCH=PASS
POST_PROCESS_EXACT_CLIENT_MATCH_COUNT=1
POST_PROCESS_EXACT_CLIENT_IDENTITY=PASS
POST_PROCESS_EXACT_RECEIVER_TRUST_MATCH_COUNT=1
POST_PROCESS_EXACT_RECEIVER_TRUST_IDENTITY=PASS
```

Deux diagnostics non autoritatifs ont été écartés : un comptage grossier par motif de sujet avait
indiqué `0/0`, puis une première version de l'audit privé avait interrogé par erreur la collection
inexistante `identities`. La passe corrigée utilise le schéma v2 exact
`ownedCertificates[].sha256`, lie chaque recherche au record privé et établit la persistance
`1/1`. Aucun sujet, thumbprint ou SHA-256 de certificat n’a été versé dans la preuve versionnée.

## 7. Tests et contrôles

### 7.1 Tests PowerShell finaux

Les tests finaux portent sur les octets PowerShell du runtime qualifié :

```text
POWERSHELL_PARSERS=PASS
PESTER_TESTS=54
PESTER_PASSED=54
PESTER_FAILED=0
PESTER_SKIPPED=0
GIT_DIFF_CHECK=PASS
```

Ils couvrent notamment le confinement de racine, le verrou, la propriété exacte, les collisions,
la non-exportabilité, les profils EKU/KU/SAN, les magasins, les limites de sortie, les injections
d’échec et le rollback sans suppression d’un objet non possédé.

### 7.2 Build Maven antérieur

Le build Maven standard a été exécuté avec Java 25 avant les derniers changements exclusivement
PowerShell. Aucun fichier Java n’a changé depuis ce build :

```text
MAVEN_STANDARD_VERIFY=PASS_BEFORE_FINAL_POWERSHELL_ONLY_CHANGES
TESTS=1182
FAILURES=0
ERRORS=0
SKIPPED=5
DURATION=00:03:14
```

Ce résultat n’est pas présenté comme une réexécution Maven postérieure au runtime final. La
couverture finale des changements PowerShell est fournie séparément par les parseurs et les
`54/54` tests Pester.

### 7.3 Déviation Testcontainers contenue

Une exécution Maven antérieure a involontairement démarré des ressources Testcontainers isolées,
alors que WO-048 interdisait tout démarrage de base. Cette déviation a été arrêtée, nettoyée et
n’est utilisée comme preuve d’aucun résultat fonctionnel WO-048.

```text
TESTCONTAINERS_EXECUTION_DEVIATION=RECORDED_PENDING_OWNER_ACKNOWLEDGEMENT
PRIMARY_DATABASE_STARTED_BY_WO048=NO
PRIMARY_DATABASE_READS_BY_WO048=0
PRIMARY_DATABASE_WRITES_BY_WO048=0
TESTCONTAINERS_RESIDUAL_COUNT=0
```

Le conteneur PostgreSQL observé après qualification était le conteneur primaire préexistant ; il
n’a pas été démarré, interrogé, modifié ou purgé par WO-048.

## 8. État final de l’hôte

L’inventaire final des ports bornés est :

```text
LISTENER_127_0_0_1_8087=0
LISTENER_127_0_0_1_8444=0
LISTENER_127_0_0_1_5432=1_PREEXISTING
LISTENER_127_0_0_1_5433=0
```

Le listener `5432` préexistait à WO-048. Aucun listener Local Lab ou receiver n’a été ouvert, et
aucun appel réseau fournisseur ou distant n’a été effectué. Les processus natifs possédés par les
tentatives échouées ont été nettoyés ; aucun processus WO-048 résiduel n’a été attribué après les
contrôles finaux.

## 9. Revue adversariale et risques résiduels

Deux revues indépendantes du runtime final n’ont relevé aucun finding bloquant :

```text
P0_OPEN=0
P1_OPEN=0
P2_OPEN=0
```

Les limites P3 suivantes restent documentées pour l’exploitation future :

- dans une longue session PowerShell interactive, certains échecs précoces peuvent laisser le
  module chargé en mémoire ; l’exécution qualifiée utilise un processus `pwsh -File` neuf qui se
  termine ensuite ;
- un échec de validation d’import peut de même laisser le module chargé dans une session
  interactive, sans conserver un processus natif ou un objet de magasin non possédé ;
- un module déjà chargé depuis le même chemin n’est pas repinné par hash au sein d’une longue
  session de développement ; la qualification utilise un processus frais ;
- quelques comparaisons de statut non destructives restent insensibles à la casse ; les phases
  d’injection et toutes les frontières destructives utilisent des comparaisons exactes.

Ces limites n’ouvrent aucune autorité réseau et ne changent pas la qualification fail-closed du
chemin exécuté.

## 10. Conclusion et état de gouvernance

```text
J9_WO048_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
J9_WO048_LOCAL_READINESS=PASS
J9_WO048_STATUS=READY_FOR_OWNER_REVIEW
J9_WO048_OWNER_REVIEW_REQUIRED=YES
J9_WO048_WORK_ORDER_MOVE_TO_COMPLETED=NO_PENDING_OWNER_DECISION
J9_WO046_MANIFEST_CREATION_AUTHORIZED=NO
J9_WO046_OWNER_GO_GRANTED=NO
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

WO-048 est prêt pour revue propriétaire. Sa validation pourra autoriser sa clôture et, sur ordre
distinct, la poursuite des étapes préparatoires de WO-046. Elle ne créera pas un manifeste
WO-046, ne constituera pas un owner-go et n’autorisera pas un POST réel.
