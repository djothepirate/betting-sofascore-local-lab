# WO-046 R1 — owner-go V2 enregistré, arrêt avant import J7

- Work Order : `WO-SS-20260904-046-j9-j7-real-local-e2e-campaign`.
- Branche : `codex/j9-wo046-j7-real-local-e2e-campaign`.
- Base documentaire et commit du manifeste : `2cdb93d2236955a34528a11cc982fc01f4554b46`.
- Date : `2026-09-05`, fuseau opérateur Europe/Paris, UTC+02:00.
- Résultat : `STOPPED_PRE_IMPORT_LOCAL_LAB_CONFIGURATION_BINDING_REFUSED`.
- Ce rapport n'est ni un PASS E2E réel, ni une validation propriétaire, ni un nouveau go.

## 1. Autorité et limites

Après le gel documentaire, le propriétaire donne l'autorisation de préparer et soumettre le
bloc owner-go V2 lié au manifeste, puis l'autorisation à usage unique de l'exécution. Cette
instruction est interprétée comme couvrant la préparation du bloc exact et une seule exécution
du périmètre gelé. Elle ne constitue pas une revue propriétaire ultérieure des octets générés.
Le bloc complet privé est soumis dans la conversation et proposé dans le panneau de fichier,
puis enregistré avant sa fenêtre de validité.

Le manifeste reste strictement inchangé :

```text
MANIFEST_REFERENCE=docs/validation/J9-WO046-J7-REAL-LOCAL-E2E-CAMPAIGN-MANIFEST-20260904.md
MANIFEST_COMMIT=2cdb93d2236955a34528a11cc982fc01f4554b46
MANIFEST_SIZE_BYTES=18380
MANIFEST_SHA256=6590603286c6c422588bbc3f6a46f502716fa2e2df18b6ad0df741cbd1cbf277
EXECUTION_ACTOR=CODEX_LOCAL_UI
MAXIMUM_DIRECT_IMPORT_CALLS=1
AUTOMATIC_RETRY=0
```

La préparation technique précède la fenêtre de l'unique import autorisable. L'interface Local
Lab n'ayant jamais été disponible, aucune action « Livrer », aucune confirmation UI et aucun
claim de livraison n'ont été effectués. Aucun appel SofaScore, réseau receiver distant, VPS ou
production n'entre dans cette autorité. `NOT_EVIDENCED` reste le statut d'audit officiel ; il
n'est pas la cause de cet arrêt, conformément à ADR-SS-003 v0.2.

## 2. Bloc V2 exact et enregistrement durable

Le fichier complet `owner-go-v2.utf8.txt` reste externe sous ACL privée. Il contient l'empreinte
réelle du certificat sélectionné et n'est donc pas ajouté à Git. Les seuls engagements publics
sont les suivants :

```text
OWNER_GO_FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V2
GO_ID=2739de83-ae45-45f0-99f1-d527d00cf8db
OWNER_GO_SIZE_BYTES=2087
OWNER_GO_DOCUMENT_SHA256=1c03c0d97dbbf6f240182dc4aa53eebe610605d4b83d17c280481b221bc28e79
VALID_FROM_UTC=2026-09-05T00:10:00.000000Z
VALID_UNTIL_UTC=2026-09-05T01:10:00.000000Z
WINDOW_POLICY=HALF_OPEN_60_MINUTES
PARIS_WINDOW=2026-09-05T02:10:00+02:00/2026-09-05T03:10:00+02:00
REGISTERED_AT_UTC=2026-09-04T23:57:22.012607Z
JAVA_V2_CANONICAL_BYTES=PASS
POSTGRES_V2_CANONICAL_SHA256=PASS
REGISTERED_BEFORE_VALID_FROM=YES
FINAL_GO_STATUS=REVOKED_UNUSED
```

Le validateur hors ligne construit le `Grant.v2` avec les classes qualifiées, compare les octets
canoniques et leur SHA-256. L'enregistrement SQL s'effectue une fois dans une transaction sous
verrou consultatif `J7_DELIVERY_GLOBAL`, avec les contraintes et le trigger V32 existants.
Le postcontrôle PostgreSQL retrouve le même hash canonique, le même UUID, et zéro consommation.
Aucun trigger, schéma, format de go ou contrat J7 n'est modifié.

Avant cet enregistrement, le recontrôle borné en lecture seule confirme le schéma primaire V32,
zéro migration échouée et l'export sélectionné :

```text
EXPORT_ID=a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc
CANONICAL_EVENT_ID=e2c9599a-2336-3888-ae88-2437dad02b48
PROVIDER_EVENT_ID=16310945
PAYLOAD_CLASS=PROVIDER_DERIVED
VALIDATION_STATUS=HUMAN_VALIDATED
FILE_SIZE_BYTES=35663
FILE_SHA256=d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6
DATA_SHA256=f95ae31e711a9433d6d06745d37f2535143266aa97c7d151fd26bea88ef03ac6
EXISTING_WARNING=MISSING_COMPONENT:EVENT_DETAILS
```

Le fichier est localisé de manière unique et rehashé sans affichage de son contenu. L'identité
mTLS exacte, ses profils publics, sa durée de validité et le registre PKI qualifié sont également
recontrôlés. Le registre conserve son engagement SHA-256
`29c53ef4b27cd9782bc49f52b5594152f12d0ca7f96fd7209052ea90f6cc7475`.

## 3. Préparation exécutée et arrêt

| Instant UTC | Observation |
|---|---|
| `2026-09-04T23:57:22.012607Z` | Grant V2 enregistré, non encore valide et non consommé |
| `2026-09-05T00:03:49.6483690Z` | Receiver exact sur `127.0.0.1:8444` : health mTLS `200/UP`, PostgreSQL V008, zéro import/payload/audit |
| `2026-09-05T00:04:20.153Z` | Démarrage du JAR Local Lab épinglé, profil local |
| `2026-09-05T00:04:28.255Z` | Annulation du contexte Spring ; matrice d'activation incohérente, aucun listener 8087 |
| `2026-09-05T00:07:48.997834Z` | Révocation durable du grant inutilisé |
| `2026-09-05T00:07:53.2913310Z` | Cleanup terminé : receiver et sa base arrêtés, volume conservé, primaire sain |
| `2026-09-05T00:16:42.061765Z` | Recontrôle primaire final en lecture seule : export et fichier inchangés, une révocation et aucun import tenté |

Le receiver INT-001 est lancé depuis son JAR épinglé, avec une nouvelle base canonique et une
configuration privée. La politique de redémarrage du conteneur est explicitement `no`. Les
anciennes ressources INT-001 Q1/Q2 ne sont ni adoptées, ni démarrées, ni supprimées. Les migrations
receiver V001 à V008 passent sur la base neuve ; aucune migration primaire n'a lieu dans R1.

Le GET de readiness emploie l'identité cliente exacte et la validation TLS normale de la
plateforme, sans callback permissif ni bypass. La clé privée cliente est utilisée par le
fournisseur cryptographique Windows pour mTLS ; elle n'est pas exportée. Le port PostgreSQL
receiver reste limité à `127.0.0.1:5433`, la rétention prévue est de 30 jours.

Local Lab refuse ensuite son démarrage avant disponibilité de son interface :

```text
Property: optional-integration.runtimeActivationCoherent
Value: false
Reason: the WO-035 runtime activation matrix is inconsistent
```

Il n'y a pas de relance après ce refus. Le contrôle Java fail-closed fonctionne ; cette preuve
n'établit pas un défaut du receiver, du transport Java ou de la consommation atomique du go.

## 4. Cause mesurée hors ligne : erreur de noms dans le lanceur Codex

Le lanceur de campagne ad hoc a été préparé par Codex avec plusieurs noms de variables erronés.
Il mélangeait le préfixe `OPTIONAL_INTEGRATION_` et des suffixes concaténés, alors que le lanceur
WO-036 qualifié utilise des suffixes séparés par underscores. Il n'avait pas été vérifié par un
préflight de binding complet avant le démarrage R1. C'est une erreur de préparation opérateur,
pas une erreur de saisie propriétaire ni un motif de contourner la validation Java.

Le diagnostic utilise uniquement les classes et bibliothèques extraites du JAR Local Lab exact,
son `application.yml`, Java 25.0.4 et le Binder Spring Boot 4.1.0. Il remplace toutes les sources
d'environnement par des valeurs synthétiques ; il ne crée aucun contexte Spring applicatif,
connexion SQL, socket, handshake ou accès au magasin de certificats.

| Valeur effective | Noms utilisés par R1 | Noms séparés attendus, test synthétique |
|---|---|---|
| `enabled` | `true` | `true` |
| `executionMode` | `DISABLED` | `PROVIDER_DERIVED` |
| `remoteDeliveryAuthorized` | `false` | `true` |
| Qualifications sender / receiver | `NOT_QUALIFIED` / `NOT_QUALIFIED` | `PASS` / `PASS` |
| Origine et référence certificat présentes | non / non | oui / oui |
| Référence owner-go complète | non | oui |
| `runtimeActivationCoherent` | `false` | `true` |

Exemples exacts de différence : `OPTIONAL_INTEGRATION_EXECUTIONMODE` au lieu de
`OPTIONAL_INTEGRATION_EXECUTION_MODE`, et
`OPTIONAL_INTEGRATION_PROVIDEROWNERGO_GOID` au lieu de
`OPTIONAL_INTEGRATION_PROVIDER_OWNER_GO_GO_ID`. Le diagnostic reproduit la configuration refusée
avec les noms R1 et obtient une matrice cohérente avec les noms corrigés, sans employer de secret
réel. Il sort code zéro. Ce test de binding identifie la correction nécessaire ; il ne qualifie
pas un lanceur corrigé ni un nouveau démarrage E2E. Le lanceur R1 n'a pas été réécrit.

Une vérification intermédiaire du nombre de lignes de sortie attendait à tort 28 lignes au lieu
de 26 et a refusé l'enregistrement de cette preuve, malgré la sortie Java zéro. La vérification
expurgée a ensuite porté sur les deux cas et leurs deux résultats attendus. Aucune application
ou opération réseau n'a été rejouée pour cela.

## 5. Révocation, nettoyage exact et limites de preuve

La révocation est une décision technique fail-closed de l'agent liée à l'arrêt du manifeste,
pas une nouvelle décision propriétaire. Elle est insérée sous le verrou global existant avant
la fenêtre de validité. Son justificatif externe conserve le SHA-256
`3890e819a044a393d92fc78ba0e0b2326c084971e76368d7decf17f1201b7dda`.

Ce justificatif présente une anomalie de sérialisation PowerShell : les valeurs de `GO_ID` et
`STOPPED_AT_UTC` sont sur les lignes suivantes au lieu de la même ligne. Ses octets sont conservés
sans correction rétrospective et ne sont pas présentés comme un bloc clé/valeur canonique. Cette
anomalie ne concerne pas le owner-go V2, validé indépendamment par Java et PostgreSQL. La relation
de révocation vers le grant exact, son timestamp serveur et le hash du justificatif sont
corroborés directement en base ; la révocation n'est pas déduite d'un parseur de ce fichier.

L'arrêt receiver est demandé par un unique POST administratif mTLS à
`/actuator/shutdown`, réponse `200`, puis sortie gracieuse constatée. L'identité du processus est
vérifiée par exécutable, marqueur, ligne de commande hachée, instant de création et listener.
La base receiver est arrêtée seulement après contrôle de son identité et de son label de go.
Son volume canonique `betting-project_betting-postgres-data` est conservé, schéma V008 et tables
d'import vides. Aucune suppression de volume ou de certificat, aucun kill basé sur le seul PID
et aucune purge primaire ne sont effectués.

Les compteurs distinguent explicitement l'import métier de l'administration :

```text
DIRECT_IMPORT_POSTS=0
RECEIVER_HEALTH_GETS=1
RECEIVER_ADMINISTRATIVE_SHUTDOWN_POSTS=1
OWNER_GO_GRANTS=1
OWNER_GO_REVOCATIONS=1
OWNER_GO_CONSUMPTIONS=0
LOCAL_LAB_DELIVERIES=0
LOCAL_LAB_DELIVERY_ATTEMPTS=0
RECEIVER_IMPORTS=0
RECEIVER_PAYLOAD_ROWS=0
RECEIVER_IMPORT_AUDIT_ROWS=0
RESIDUAL_LISTENERS_8087_8444_5433=0
PRIMARY_DATABASE=RUNNING_HEALTHY_NOT_STOPPED
RECEIVER_DATABASE=STOPPED_VOLUME_RETAINED_RESTART_POLICY_NO
```

Il serait donc inexact de déclarer « aucun POST » sans préciser **aucun POST d'import J7**.
Les deux écritures primaires de gouvernance sont le grant et sa révocation ; zéro écriture de
livraison ne signifie pas zéro écriture SQL. Le registre PKI qualifié est inchangé. Aucun ACK
métier, aucune preuve d'inbox/outbox importée et aucun enrichissement ne sont revendiqués.

## 6. Engagements de preuves externes

Les fichiers suivants restent sous ACL privée ; seuls noms logiques, tailles et hashes sont
publiés. Les chemins privés, empreintes de certificats réelles, mots de passe, stores, payloads
et éventuelles données de session ne sont pas ajoutés à la documentation.

| Preuve | Octets | SHA-256 |
|---|---:|---|
| `registration-result.json` | 186 | `d81966cdfb81999e7df0a5ed9567a583996a9929b9114b6fa79abb8efe4f517d` |
| `receiver-readiness.json` | 366 | `def36dcc32d615f210e6127f5bfa747e19c3fc8e65997db0457f988f2f86e24d` |
| `revocation-result.json` | 249 | `fc3697594fb3324401fb9090ff71c23a8106eddf6d00958518438befa1e0e42c` |
| `stopped-campaign-postflight.json` | 549 | `e16795153b38e89a05d3bdf20c77204f5bff457a46cfe57ed83c8ba820a0c169` |
| `final-readonly-postflight.json` | 435 | `2e88339634b20d21d15c6bdfdd3413ff367beaa2da00bad58a0bfd76b653d348` |
| `offline-binding-diagnostic.utf8.txt` | 702 | `301f2ad2eac894ea26296347d6109c57b4bb3e6c87218d2764f849e138ba98b9` |
| Local Lab stdout privé | 2540 | `3bfde012fa527cb67ef742c068a1c63e8b99eba0cc0bf07e2b2e325d98e36edb` |

Les quatre journaux privés Local Lab/receiver ont été contrôlés contre les mots de passe connus
et l'empreinte cliente sélectionnée : zéro correspondance. Le stdout receiver est vide ; les
deux stderr ne contiennent que les notifications d'options JVM vides. Ce contrôle ciblé n'est
pas présenté comme une preuve universelle d'absence de toute donnée personnelle.

L'outillage opérationnel ad hoc reste ignoré dans Git et est conservé pour audit, sans être
promu comme outillage qualifié. Engagements principaux :

| Outil | SHA-256 |
|---|---|
| `VerifyWO046OwnerGo.java` | `6f6fc25c875795bb26e24c25995c0a27c828805b3494d0068881b611b7b3b72e` |
| `Register-Wo046OwnerGo.ps1` | `bbeafbb1067b469b56be97551702cf0ab6c2535fc8723abcb78c67cf0fb1a68c` |
| `Start-Wo046Component.ps1` refusé | `4049844558328d4875ba8ae3961937e1a52f1e29b271319cf7d8bda55c00d8d8` |
| `Test-Wo046Readiness.ps1` | `58e90826609903634ab1383836a547ba9c23039fae1dd1fbc9cd8bcb51a1ece7` |
| `Stop-Wo046Campaign.ps1` | `2ec19f7593715cb232a609d1fe8a281e8d5ab23831c238eace454122deb9a622` |
| `ProbeWO046EnvironmentBinding.java` synthétique | `73647e861c849fbc19db72d241c788219a33dc82cd4d7d39f9418df1f5941db5` |

## 7. Vérifications et suite non autorisée automatiquement

Les JAR Local Lab et receiver restent respectivement liés aux empreintes
`f1643edb08a77ed1b53b563b05dd5426bfa53c69027f66bd3b866e012f2aca0f` et
`baa41241e0fa23139ccd0dce551c11747da4567f0934bd79fc9af7005d6dd965`.
Le manifeste gelé, les rapports qualifiés, les sources runtime, migrations et références
immuables ne sont pas réécrits. Les contrôles de ce lot sont le diagnostic Java hors ligne,
les preuves SQL/ressources bornées, le diff, UTF-8 sans BOM/NUL et la confidentialité du diff.
`mvnw.cmd clean verify` et les tests d'intégration ne sont pas réexécutés pour ce compte rendu
d'arrêt : aucun changement applicatif n'est proposé et les JAR épinglés sont conservés. Aucun
nouveau PASS de qualification applicative n'est revendiqué.

WO-046 reste actif et arrêté. La correction de la préparation/binding du lanceur, son préflight
complet et la fiabilité du justificatif d'arrêt doivent être qualifiés dans un périmètre
d'outillage distinct avant reprise. Le test synthétique de cette section ne remplace pas cette
qualification et n'autorise pas une nouvelle exécution.

La contrainte existante `uq_j7_provider_owner_go_manifest` est unique sur le hash du manifeste.
Un grant enregistré puis révoqué interdit donc l'enregistrement d'un autre grant pour ce même
manifeste, même avec zéro consommation. Une éventuelle reprise exige une décision propriétaire
distincte, un manifeste successeur nouveau et gelé par commit, puis un nouveau go exact à usage
unique. Aucun contournement de cette unicité, modification du grant, suppression d'audit,
nouveau manifeste, nouveau go ou retry n'a lieu dans ce lot.
Le futur préflight devra également traiter explicitement le volume receiver V008 conservé :
ni sa réutilisation ni sa suppression ne peuvent être déduites d'une reprise automatique.

```text
WORK_ORDER_MOVE_TO_COMPLETED=NO
CURRENT_REAL_IMPORT_AUTHORIZATION=NO_STOPPED_GO_REVOKED
NEW_MANIFEST_AUTHORIZED=NO
NEW_OWNER_GO_GRANTED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```
