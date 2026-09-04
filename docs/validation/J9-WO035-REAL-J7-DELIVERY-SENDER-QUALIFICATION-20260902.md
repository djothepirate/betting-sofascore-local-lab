# J9 — qualification du sender réel J7 du Local Lab sous WO-035

## 1. Identification

```text
REPORT_ID=J9-WO035-REAL-J7-DELIVERY-SENDER-QUALIFICATION-20260902
REPORT_STATUS=FINAL_LOCAL_QUALIFICATION
WORK_ORDER=WO-SS-20260902-035-j9-real-j7-delivery-sender
BRANCH=codex/j9-wo035-real-j7-delivery-sender
ORIGIN_MAIN=579fa2545cc2cb8e7ce31afc15f0ec4fb3fd1571
ALIGNMENT_COMMIT=49fac876ca5ee2f6dad603c3a339ca9b47dc1b1e
IMPLEMENTATION_COMMIT=f5a27887b7db43576eb608d564c245c8cca3a602
QUALIFIED_AT_UTC=2026-09-02T18:32:13Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-02T20:32:13+02:00
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
```

Ce rapport qualifie uniquement l'implémentation locale du sender J7. Il ne constitue ni une
qualification du receiver INT-001, ni la campagne E2E Windows/Windows WO-036, ni une autorisation
de livraison réelle.

## 2. Périmètre qualifié

Le commit d'implémentation ajoute et vérifie les éléments suivants :

- action UI locale distincte pour préparer, confirmer puis tenter la livraison d'un export déjà
  `HUMAN_VALIDATED` ;
- confirmation exacte `LIVRER J7 <exportId> SHA256 <fileSha256>`, liée à la session, à l'action,
  aux octets et à l'ordinal de tentative attendu, avec expiration et consommation unique ;
- relecture complète et revalidation de l'artefact avant construction du transport ;
- claim PostgreSQL atomique de l'ordinal attendu, avec refus `ATTEMPT_ORDINAL_MISMATCH` avant
  création d'une nouvelle tentative ou ouverture de socket ;
- transport `POST` HTTPS/mTLS vers l'unique origine runtime admissible
  `https://127.0.0.1:8444`, sans proxy, cookie, redirection, retry ou seconde exécution ;
- sélection paresseuse du certificat client par SHA-256 exact dans `Windows-MY`, contrôle de
  validité, EKU `clientAuth`, usage `digitalSignature`, clé Java opaque et confiance explicite
  `Windows-ROOT` ;
- body publisher à souscription unique, ACK UTF-8/JSON strict et borné à 16 Kio ;
- gate singleton `IDLE` / `ACTIVE` / `POISONED`, partagée entre livraison et réconciliation et
  conservée jusqu'après la fermeture effective du transport ;
- réconciliation exclusivement fondée sur les métadonnées et le ledger, sans lecture de payload,
  sans transport et sans possibilité de renvoi ;
- frontière navigateur appliquée par `HandlerMethod`, avec `Host` et `Origin` loopback exacts,
  refus des en-têtes forwarded, `Content-Security-Policy: frame-ancestors 'none'` et
  `X-Frame-Options: DENY` ;
- classification de provenance `SYNTHETIC_ONLY`, `PROVIDER_DERIVED` ou `MIXED_OR_UNKNOWN`, avec
  refus des deux dernières classes dans la voie synthétique avant claim, certificat et socket ;
- neutralisation explicite des flags de livraison optionnelle dans les launchers et les pipelines
  CI.

Aucune migration n'est créée ou modifiée. Le sender réutilise la migration V29 déjà versionnée,
dont le SHA-256 reste :

```text
V29_SHA256=49f32e88af2ed0115fa9cc5587913f4f0ed7576d49eb1bd4634a0dd212416bd5
```

## 3. Qualification Maven

Deux cycles complets successifs ont été exécutés avec Java 25.0.4 et le cache Maven local, sans
téléchargement :

| Commande | Résultat | Tests standards | Tests d'intégration |
|---|---|---:|---:|
| `.\mvnw.cmd --offline clean verify` | `PASS` | 1 115, 0 échec, 0 erreur, 5 ignorés | 85, 0 échec, 0 erreur |
| `.\mvnw.cmd --offline -Pintegration-tests verify` | `PASS` | 1 115, 0 échec, 0 erreur, 5 ignorés | 85, 0 échec, 0 erreur |

Les rapports finaux contiennent 164 suites Surefire et quatre suites Failsafe :

| Suite Failsafe | Tests | Résultat |
|---|---:|---|
| `FlywayMigrationIT` | 68 | `PASS` |
| `J7DeliveryLedgerMigrationIT` | 11 | `PASS` |
| `J7DeliveryMutualTlsLoopbackIT` | 4 | `PASS` |
| `J7OptionalDeliveryEndToEndIT` | 2 | `PASS` |

Les fichiers de test directement ajoutés ou modifiés par WO-035 portent 173 tests standards et
17 tests d'intégration, tous verts.

Une première passe complète a identifié un test historique devenu incompatible avec l'invariant
nouveau « une instance de transport = une tentative » : le test réutilisait une même instance pour
l'import puis le doublon et recevait correctement `AUTOMATIC_REPLAY_BLOCKED`. Le test a été corrigé
pour ouvrir et fermer une instance distincte par tentative, puis étendu avec une collision réelle
`409`. Le test ciblé et les deux cycles complets ci-dessus passent après cette correction. Aucun
contrôle runtime n'a été assoupli.

## 4. Contrôles de configuration, scripts et reproductibilité

```text
DOCKER_COMPOSE_CONFIG_QUIET=PASS
LOCAL_ONLY_POLICY=PASS
DISTRIBUTION_LAUNCHERS=PASS_DIRECT_JAR
RELEASE_REPRODUCIBILITY=PASS_LOCAL_ONLY
BASH_SYNTAX=PASS
POWERSHELL_CHANGED_FILES_PARSE=8/8_PASS
GIT_DIFF_CHECK=PASS
UTF8_STRICT_CHANGED_FILES=75/75_PASS
UTF8_BOM_COUNT=0
NUL_BYTE_COUNT=0
REFERENCE_OR_PDF_CHANGE_COUNT=0
```

La validation Compose a utilisé en mode silencieux le fichier `.env` local ignoré du dépôt ;
aucune valeur n'a été affichée ni consignée.

Les valeurs versionnées et les launchers ont été contrôlés :

```text
server.address=127.0.0.1
sofascore.enabled=false
sofascore.playwright.enabled=false
automatic-refresh-enabled=false
live-polling-enabled=false

optional-integration.enabled=false
optional-integration.execution-mode=DISABLED
optional-integration.remote-delivery-authorized=false
optional-integration.official-permission-status=NOT_EVIDENCED
optional-integration.receiver-qualification=NOT_QUALIFIED
optional-integration.sender-qualification=NOT_QUALIFIED
optional-integration.receiver-origin=
optional-integration.loopback-qualification=false
optional-integration.loopback-origin=
optional-integration.automatic-retry-enabled=false
optional-integration.mtls.client-certificate-sha256=
```

## 5. Sécurité et absence d'effets réels

Le scan des 75 fichiers d'implémentation non encore committés au moment de la qualification n'a
trouvé aucun secret, credential, certificat ou clé privée, PII, chemin personnel, identifiant réel
du corpus fournisseur, adresse VPS publique ou payload J7 réel. Les seules adresses de courriel
sont des fixtures sous le domaine réservé `.invalid`.

Après les tests :

```text
RELEVANT_LISTENERS_8087_8444_5432_5433=0
PLAYWRIGHT_OR_CHROME_RESIDUAL_PROCESSES=0
BUILD_JAVA_RESIDUAL_PROCESSES=0
PROVIDER_CALLS_UNDER_WO035=0
REAL_RECEIVER_CALLS_UNDER_WO035=0
PROVIDER_DERIVED_DELIVERIES_UNDER_WO035=0
```

Les seuls échanges réseau exécutés par les tests WO-035 sont des échanges loopback synthétiques
et les connexions PostgreSQL Testcontainers locales. Aucun SofaScore, receiver Betting Project
réel ou VPS n'a été contacté.

## 6. Résultat déterministe et portes restantes

```text
WO035_RUNTIME_SENDER_RESULT=PASS_LOCAL_FAIL_CLOSED
WO035_PROVIDER_DERIVED_PATH=BLOCKED
WO035_PROVIDER_CALLS=0
WO035_REAL_RECEIVER_CALLS=0
WO035_WO036_OPENED=NO
WO035_INT001_PULL_REQUEST_CREATED=NO

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
OWNER_REVIEW_REQUIRED=YES
```

Le résultat `PASS_LOCAL_FAIL_CLOSED` signifie que le sender est implémenté, composé et qualifié
localement tout en restant désactivé et incapable d'envoyer une donnée fournisseur dans l'état
versionné. Il ne qualifie pas la non-exportabilité native d'une future clé privée, la PKI locale à
deux applications, le receiver réel, l'inbox/outbox du Betting Project ou une topologie VPS.

WO-036 doit rester un Work Order et une branche distincts. La création de la PR INT-001 reste la
troisième étape séparée demandée par le propriétaire.
