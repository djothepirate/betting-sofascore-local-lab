# WO-046 R2 — Réception réelle J7 locale Windows/Windows

## 1. Résultat et portée

Résultat technique : `PASS_REAL_LOCAL_J7_E2E`. Un seul POST d'import J7 a été exécuté
depuis l'interface Local Lab par `CODEX_LOCAL_UI`, après confirmation exacte et fraîche.
Betting Project répond `201/IMPORTED`, le sender persiste `DELIVERED`, le go est consommé
une fois. L'inbox est strictement byte-identique au fichier déjà `HUMAN_VALIDATED`.
L'audit et l'outbox sont durables. Aucun enrichissement analytique n'est revendiqué.

WO-046 reste actif : `READY_FOR_OWNER_REVIEW_R2_REAL_LOCAL_E2E`. Le présent résultat ne
constitue ni une validation propriétaire ni une autorisation de nouvelle tentative.
Un écart de minimisation des restitutions navigateur est exposé en section 7 ; le PASS
technique du transfert ne signifie pas que cet écart n'a pas existé.

## 2. Autorité exacte et références immuables

Le propriétaire déclare : « Je donne le go v2 lié à ce manifeste et l'unique POST ».
Cette décision succède au gel R2 et autorise cette seule exécution locale. Les NO du
manifeste décrivent son état au gel et n'ont pas été réécrits rétroactivement.

```text
WORK_ORDER=WO-SS-20260904-046-j9-j7-real-local-e2e-campaign
CAMPAIGN_SERIES=WO046_REAL_LOCAL_R2
EXECUTION_BRANCH=codex/j9-wo046-j7-real-local-e2e-campaign
EXECUTION_BASE_COMMIT=18452a90872f341e5bfad8519a80e5260c5789a5
MANIFEST_REFERENCE=docs/validation/J9-WO046-J7-REAL-LOCAL-E2E-R2-MANIFEST-20260905.md
MANIFEST_COMMIT=642e0e5ac42cdd476969e40cd3b5137d7d56b01d
MANIFEST_SIZE_BYTES=15737
MANIFEST_SHA256=ba21dfd8ac9cfa6284eab686b3206c5676736ac035c9b38096fb656376baef65
OWNER_GO_FORMAT=J7_PROVIDER_DERIVED_OWNER_GO_V2
GO_ID=5ae693fc-8832-43d6-a02a-10086bbdd61e
OWNER_GO_SIZE_BYTES=2081
OWNER_GO_SHA256=d3f8c4a1a73ab0a344e0e87d3f494caadf2586d365ff245fa3c72fb17c47a313
OWNER_GO_REGISTERED_AT_UTC=2026-09-05T09:02:21.756602Z
VALID_FROM=2026-09-05T09:15:00.000000Z
VALID_UNTIL=2026-09-05T10:15:00.000000Z
WINDOW_EUROPE_PARIS=[2026-09-05T11:15:00+02:00,2026-09-05T12:15:00+02:00)
MAXIMUM_IMPORT_POSTS=1
```

Le bloc exact privé est soumis avec son empreinte avant exécution. La validation Java
des octets canoniques et la recomposition PostgreSQL concordent. L'enregistrement est
antérieur à la fenêtre ; le grant historique R1 reste révoqué, sans effacement ni réarmement.
L'autorisation est consommée et ne survit pas à l'achèvement de cette série.

Les deux JAR et le module de préparation WO-049 sont rehashés contre le manifeste :

| Élément | Commit source / implémentation | SHA-256 |
|---|---|---|
| Local Lab, Java 25 / Boot 4.1.0 | `f9788e63c3df862272a482ef83f5f45a9fa95371` | `f1643edb08a77ed1b53b563b05dd5426bfa53c69027f66bd3b866e012f2aca0f` |
| Receiver, Java 25 / Boot 4.1.1 | `25c0229aac06df9ab380f0dfa77a3e70f34a5632` | `baa41241e0fa23139ccd0dce551c11747da4567f0934bd79fc9af7005d6dd965` |
| Module WO-049 | `22130d16781cce41a370cbcdbf6cd1b7b91546c2` | `731565492e0f17a61b918019083ecbb7231a8ff64c7c2f807572b46196600bd6` |

Receiver HEAD propre `de06153f0908a1bb2dc9bbd2c8e22f7fd14dacfd`. Aucun changement Java,
protocole, schéma, migration, export, PKI ou configuration persistante sous ce lot.

## 3. Préflight et démarrage bornés

- Export et métadonnées primaires recontrôlés à partir de 09:00:14 UTC : V32,
  aucune migration échouée, circuit `LOCKED`, réseau fournisseur désactivé.
- PKI conservée WO-048 : registre et module épinglés, ACL privées, profils et validité
  couvrant toute la fenêtre ; aucune exportation de clé privée ni mutation du magasin.
- Réutilisation du receiver exact `betting-project-postgres-1`, conteneur
  `d2d670ac601a2cf308c7883427e506f3d8e7e971ffdf9121901fc81416c83bc3`, volume
  `betting-project_betting-postgres-data`. Identité, image, création, labels R1, montage,
  loopback 5433 et restart `no` conservés. État initial frais : V008, zéro import,
  payload, audit, tombstone et outbox. Aucune migration requise.
- Receiver `127.0.0.1:8444`, puis Local Lab `127.0.0.1:8087` : un lancement Java par
  composant, arguments séparés, flux privés bornés à 1 MiB, supervision conservant le
  processus enfant. PID, création, exécutable, marqueur de run et hash de ligne de commande
  identifient les processus ; aucun arrêt sur le seul PID.
- Le mapping complet Local Lab passe d'abord un contrôle Spring Binder/Validation hors
  ligne avec les classes et dépendances comparées byte/hash au JAR exact, sans contexte
  applicatif, SQL ni socket dans ce probe. Les listeners et health `200/UP` passent ensuite.
- Le health receiver utilise mTLS et la validation de confiance de la plateforme, sans
  bypass TLS. L'interface affiche `PROVIDER_OWNER_GO_NOT_YET_VALID` avant 09:15 UTC,
  puis autorise la préparation après ouverture de la fenêtre.

## 4. Export, tentative et réception corrélés

```text
EXPORT_ID=a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc
CANONICAL_EVENT_ID=e2c9599a-2336-3888-ae88-2437dad02b48
PROVIDER_EVENT_ID=16310945
PAYLOAD_CLASS=PROVIDER_DERIVED
VALIDATION_STATUS=HUMAN_VALIDATED
FILE_SIZE_BYTES=35663
FILE_SHA256=d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6
DATA_SHA256=f95ae31e711a9433d6d06745d37f2535143266aa97c7d151fd26bea88ef03ac6
SOURCE_SET_SHA256=20b5326195551a70dec597e24d102cc178ed3ac4e3304001854e728943371c7e
WARNING=MISSING_COMPONENT:EVENT_DETAILS
DELIVERY_ID=93c1aa76-7625-437c-8dc7-5a5b72417a8c
ATTEMPT_ID=232b95ad-ffc7-4d1b-80e3-a19adeb36cec
ATTEMPT_NUMBER=1
ATTEMPT_STARTED_AT_UTC=2026-09-05T09:20:10.613249Z
GO_CONSUMED_AT_UTC=2026-09-05T09:20:10.677477Z
REMOTE_IMPORT_ID=9093ae49-a182-40f8-9504-32872f0b32f0
RECEIVED_AT_UTC=2026-09-05T09:20:11.412083Z
ATTEMPT_COMPLETED_AT_UTC=2026-09-05T09:20:11.526406Z
HTTP_STATUS=201
SAFE_RESULT_CODE=HTTP_201_IMPORTED
SENDER_STATE=DELIVERED
ACK_SHA256=3a92e1842aad347f04150d174a985aab063f3ec2f313a8416290a0575b206417
RECEIVER_RETENTION_DAYS=30
PAYLOAD_EXPIRES_AT_UTC=2026-10-05T09:20:11.412083Z
```

La confirmation UI fraîche expire à `2026-09-05T09:23:10.482823900Z`. La phrase exacte
liée à l'export et à son hash est renseignée et l'accusé d'absence de retry coché, puis
« Livrer maintenant » est activé une seule fois au clavier. Le résultat UI est `DELIVERED`,
tentatives persistées `1`. Durée mesurée entre début et fin sender : `0.913157 s`.

Destination unique : `POST https://127.0.0.1:8444/api/imports/sofascore/j7-canonical-events`.
Le transport qualifié conserve le corps exact, les headers J7 et la clé d'idempotence,
concurrence 1, aucun retry et aucune redirection. Aucun proxy ou client de substitution
n'est utilisé pour l'import. L'avertissement `EVENT_DETAILS` manquant reste inchangé :
succès de livraison ne veut pas dire complétude analytique du dossier.

Le postflight SQL est en lecture seule. Il corrobore un grant R2 exact consommé dans sa
fenêtre et lié à l'unique tentative, un résultat sender `201`, un reçu receiver avec même
UUID d'import, même export, mêmes hashes, même instant durable et même identité mTLS.
Les octets inbox sont relus uniquement en mémoire privée et comparés un par un au fichier
source ; la longueur et le SHA-256 sont aussi comparés. Aucun payload ni ACK brut n'est
ajouté aux preuves documentaires. Résultat : `BYTE_IDENTICAL=YES`.

Receiver : 1 reçu, 1 payload, 1 audit `IMPORTED/ACCEPTED`, 1 outbox
`J7_IMPORT_ACCEPTED`, état `PENDING`, zéro tentative d'enrichissement, zéro tombstone.
Le reçu est durable ; l'enrichissement futur reste asynchrone et non réalisé par cette preuve.

## 5. Arrêt, conservation et réseau fermé

L'application Local Lab est arrêtée gracieusement à 09:24:21 UTC, puis le receiver à
09:24:23 UTC, chacun par un POST administratif de shutdown `200` distinct du POST d'import.
Les deux superviseurs propriétaires rapportent ensuite le code de sortie Java `0`.
Les champs exit-code des enregistrements d'arrêt externes sont nuls : le code 0 est donc
corroboré par les superviseurs parents, et non inventé depuis ces champs nuls.

Postflight après arrêt : un import, un payload, un audit, une outbox ; côté sender,
une tentative, une consommation, une livraison `DELIVERED`, zéro `IN_FLIGHT`.
Le PostgreSQL receiver exact est arrêté avec code 0 et volume/audit/inbox conservés.
Le primaire reste actif et sain ; aucune purge et aucun arrêt primaire.

À 09:24:25 UTC : zéro Java de campagne résiduel et zéro listener 8087/8444/5433.
Les flags éphémères et références go disparaissent avec les processus ; aucune activation
persistante n'est écrite. Le go consommé n'est pas réutilisable. Aucun certificat n'est
supprimé. L'onglet de campagne est fermé, sans HAR, trace, vidéo, capture, téléchargement
ou storageState créé par l'agent.

À 09:26:07 UTC : circuit fournisseur `LOCKED`, `network_enabled=false`, zéro snapshot et
zéro occurrence fournisseur demandés depuis 09:00 UTC. Aucun nouveau J3/J4/J5 ni Playwright
fournisseur n'a été exécuté. Aucune requête vers un receiver distant, VPS ou production.

## 6. Preuves expurgées et empreintes

Les fichiers ci-dessous restent hors dépôt sous ACL privées. Seuls leurs noms logiques,
tailles et hashes sont consignés. Le bloc go contient la référence d'identité privée ;
son contenu complet et les empreintes de certificats ne sont pas copiés dans Git.

| Preuve privée | Octets | SHA-256 |
|---|---:|---|
| registration-result.json | 186 | `9da15355f183f52fe1feaafd907d48dc996b46323aa4b08dd0940565d274ce24` |
| receiver-readiness.json | 366 | `125a77bda502674038e4049b59c8bf327320309b6dad1502bdde8ea0e06041f8` |
| locallab-readiness.json | 249 | `245985ee7d0bb608eee123f93799dd0d0b679abdce6f729fc3590922f367ac9f` |
| sender-durable-postflight.json | 1383 | `1d867ec8732e99d13e4de5cd87fbfbc26637549e4f7826cc50a40a445bc38655` |
| receiver-durable-postflight.json | 1635 | `dbe71d9b68dc3e55f5398b78c78089345c80f11b1299086c12d738241035fcac` |
| durable-receipt-proof.json | 979 | `e3efb7d2234ea3922c97a8cbf3aa3b6bae4efa65b30d86036b08390963ce71af` |
| completed-campaign-cleanup.json | 1288 | `2dd1d5a141672546816dc5da7f5554e7c976839fb1b9a4cca1a93b097660a6e1` |
| final-evidence-index.json | index privé | `f7146d7103efccf8b40f7b400e6538987e05a6380ff3c5394157619b2076f331` |

Les quatre flux applicatifs privés mesurent respectivement 0/92 octets receiver et
1902/92 octets Local Lab, zéro ligne ERROR. Le contrôle des secrets configurés, de
l'empreinte cliente, des marqueurs de payload/ACK/clé privée/cookie et des ACL passe.
Cette preuve porte sur les flux applicatifs, pas sur toute la conversation navigateur.

## 7. Écarts d'observation, limites et contrôles du lot

L'ouverture de l'onglet a restitué automatiquement dans la sortie d'outil l'aperçu J7
normalisé complet et des URL de session. Une tentative de découpage ultérieure, sensible
à la casse du libellé, a également restitué un extrait de cet aperçu. Il serait donc faux
d'affirmer « aucun contenu J7 dans toutes les sorties d'outil ». Rien de ce contenu, ni
des URL de session, n'est recopié dans Git, les rapports ou les preuves applicatives.
Les lectures suivantes ont été limitées au panneau de livraison ; aucun ACK brut n'a
été extrait par le navigateur. Cet écart de minimisation doit être reconnu lors de la
revue propriétaire ; il ne remet pas en cause la preuve byte-identique ou le décompte
de l'import, mais interdit une conclusion globale d'observabilité parfaitement expurgée.

Le premier clic de préparation n'a produit aucun changement visible ; formulaire et
état ont été inspectés sans activer l'import. L'activation au clavier a ensuite affiché
la confirmation fraîche. Quelques lectures DOM ont expiré ; aucune action finale de
livraison n'a été répétée. Les ledgers indépendants corroborent une seule tentative.

Un contrôle préparatoire de statut natif s'est arrêté sur un code hérité avant création
du go ; seul le renderer non encore exécuté a ensuite été lancé. Une première lecture de
flux actifs a refusé le partage du fichier ; la lecture diagnostique a utilisé le partage
lecture/écriture sans changer les processus. Ces incidents d'observation n'ont créé aucun
grant supplémentaire, aucun redémarrage applicatif et aucun retry d'import.

Commandes de campagne : préflight de métadonnées, `Prepare-WO046OwnerGoR2.ps1`,
`Register-Wo046OwnerGoR2.ps1`, `Start-Wo046R2RetainedDatabase.ps1`, superviseur
`Run-Wo046R2Component.ps1` pour chaque composant, `Test-Wo046R2Readiness.ps1`, parcours
UI, `Test-Wo046R2DurableReceipt.ps1`, `Stop-Wo046R2CompletedCampaign.ps1` et
`Test-Wo046R2FinalEvidence.ps1`. Ce sont des commandes locales d'exécution ignorées,
pas un nouvel orchestrateur applicatif. Aucun script R1 fautif n'est rejoué.

Les JAR gelés ne sont pas reconstruits par cette campagne : pas de nouveau `mvnw clean
verify`, de Testcontainers ou de sauvegarde/restauration revendiqués. Les qualifications
build antérieures demeurent celles référencées par le manifeste. Le contrôle documentaire
porte sur UTF-8 sans BOM, diff sans espaces fautifs, absence de secrets/PII/payload dans
les quatre fichiers du lot, maintien des hashes gelés et absence de modification runtime.
La campagne réelle ne remplace pas un futur build/revue avant fusion.

```text
EVIDENCE_RESULT=PASS
QUALIFICATION_RESULT=PASS_REAL_LOCAL_J7_E2E
BROWSER_OBSERVATION_MINIMIZATION=DEVIATION_DISCLOSED_PENDING_OWNER_ACKNOWLEDGEMENT
OWNER_REVIEW_DECISION=PENDING
WORK_ORDER_MOVE_TO_COMPLETED=NO
OWNER_GO_STATUS=CONSUMED_ONCE_NOT_REUSABLE
NEW_POST_AUTHORIZED=NO
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
PUSH_OR_MERGE_AUTHORIZED=NO
```

`NOT_EVIDENCED` est conservé en audit conformément à ADR-SS-003 v0.2 pour ce seul transfert
local ; aucun accord SofaScore n'est inventé et les portes d'acquisition J3/J4/J5 sont inchangées.
