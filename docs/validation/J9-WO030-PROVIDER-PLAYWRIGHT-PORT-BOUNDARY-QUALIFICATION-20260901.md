# J9 — qualification WO-030 de la borne de port Playwright fournisseur

## 1. Identité de la preuve

```text
REPORT=J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901
WORK_ORDER=WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening
BASE_COMMIT=daf55bf76521f81893f86d04fde3c2903bf22362
OPENING_COMMIT=1c7c1358527c0461824a0de0e76b03e54c38c435
IMPLEMENTATION_COMMIT=154349a2fbebe3fd0a43a63c7105f690ff04976b
BRANCH=codex/j9-wo030-provider-playwright-port-boundary
WORKTREE=.tmp/w30
QUALIFIED_AT_UTC=2026-09-01T13:47:21.0195627Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-01T15:47:21.0195627+02:00
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
OWNER_REVIEW_REQUIRED=YES
PROVIDER_CALLS=0
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Cette preuve qualifie exclusivement le refus local d'un port hors plage dans l'origine loopback de
qualification Playwright. Elle n'exécute aucune acquisition SofaScore, n'ouvre aucune permission
officielle et ne modifie aucune autorisation de campagne.

## 2. Baseline et discriminant

La base `origin/main` issue de la fusion de la PR #21 contenait deux validations incomplètes du
même champ :

| Frontière | SHA-256 de la baseline | Défaut |
|---|---|---|
| `ProviderPlaywrightProperties.isSafeConfiguration()` | `2e199db5d50b49910a0c2c17b646ef93066d21aad2f27b3240df07314dea70d9` | vérifiait seulement `port >= 1` |
| `ProviderPlaywrightWorkerConfiguration.parseOrigin()` | `b8d84671e0e3e7f0f01abdfbc517b0ad97d9cfbd6ff91cb286c7c14bc36b43ec` | vérifiait seulement `port >= 1` |

Le discriminant est `http://127.0.0.1:65536`. Java le représente comme une URI structurée dont le
port vaut `65536`, mais ce nombre ne peut pas désigner un port TCP. Le comportement attendu est un
refus avant disponibilité de la qualification côté parent, avant le passage d'une intention à
`EXECUTING` et avant toute navigation côté worker.

Le port IPC du worker et `ScheduledEventsTransportRequest` appliquaient déjà `[1, 65535]` sur la
base ; ils ne sont pas modifiés par WO-030.

## 3. Delta qualifié

- `ProviderPlaywrightProperties` définit `MAXIMUM_TCP_PORT=65_535` et exige désormais
  `1 <= origin.getPort() <= 65_535` en conservant les gardes existantes de schéma, hôte,
  user-info, query, fragment et chemin ;
- `ProviderPlaywrightWorkerConfiguration` applique indépendamment la même borne dans
  `parseOrigin()` avant connexion IPC, ouverture du runtime Playwright ou navigation ;
- la constante borne également la validation IPC déjà existante, sans en changer le contrat ;
- les tests parents couvrent les deux barrières `isSafeConfiguration()` et Bean Validation ;
- le test de policy prépare et confirme une intention locale, appelle réellement
  `claimExecution()`, obtient `PROVIDER_TRANSPORT_UNAVAILABLE`, vérifie
  `executionMayContinue=false` et conserve l'intention à `CONFIRMED_READY` ;
- les tests du worker appellent seulement `fromEnvironment()` et `uriFor()` en mémoire : aucun
  worker enfant, navigateur, Playwright ou socket de fournisseur n'est créé pour la matrice de
  bornes.

Empreintes SHA-256 après correctif :

```text
PROVIDER_PLAYWRIGHT_PROPERTIES_SHA256=5e6adc1e415086a8762b982a6df05446f9081022c08938b01cbd477b1d5b89ca
PROVIDER_PLAYWRIGHT_WORKER_CONFIGURATION_SHA256=c294e517133dde24d05b52b4206fff921d4279d6034b6bf247b9bce4199d9616
```

## 4. Matrice des bornes

| Origine | Port URI | Garde parente | Garde worker | Navigation/connexion du cas de borne |
|---|---:|---|---|---|
| `http://127.0.0.1` | `-1` | `REFUSE` | `REFUSE` | aucune |
| `http://127.0.0.1:0` | `0` | `REFUSE` | `REFUSE` | aucune |
| `http://127.0.0.1:1` | `1` | `ACCEPTE_STRUCTURELLEMENT` | `ACCEPTE_STRUCTURELLEMENT` | aucune |
| `http://127.0.0.1:65535` | `65535` | `ACCEPTE_STRUCTURELLEMENT` | `ACCEPTE_STRUCTURELLEMENT` | aucune |
| `http://127.0.0.1:65536` | `65536` | `REFUSE` | `REFUSE` | aucune |

L'acceptation de `1` et `65535` est structurelle uniquement : les tests de borne ne tentent pas de
se connecter à ces ports. Les suites complètes exécutent séparément leurs tests loopback locaux et
Testcontainers habituels ; aucune de ces activités ne cible SofaScore.

## 5. Vérifications exécutées

| Porte | Commande ou contrôle | Résultat |
|---|---|---|
| tests ciblés parent | `mvnw.cmd --offline -Dtest=ProviderPlaywrightPropertiesTest,J3ProviderQualificationPolicyTest test` | `PASS` — `14/0/0/0` |
| test ciblé worker | `mvnw.cmd --offline -Pprovider-playwright-runtime -Dtest=ProviderPlaywrightWorkerProtocolTest test` | `PASS` — `11/0/0/0` |
| suite standard finale | `mvnw.cmd --offline clean verify` | `PASS` — Surefire `1043/0/0/5`, Failsafe `84/0/0/0`, terminé à `2026-09-01T13:41:43Z` |
| profil runtime final | `mvnw.cmd --offline -Pprovider-playwright-runtime clean verify` | `PASS` — Surefire `1065/0/0/5`, Failsafe `84/0/0/0`, terminé à `2026-09-01T13:45:44Z` |
| migrations et persistance | intégrations liées automatiquement à `verify` | `PASS` — Flyway V1→V29, ledger J7, mTLS et end-to-end loopback |
| diff | `git diff --check` | `PASS` |
| secrets | scan haute confiance des 10 fichiers du delta | `PASS` — `0` occurrence |
| artefacts interdits | chemins ajoutés/modifiés | `PASS` — `0` HAR, trace, vidéo, capture, téléchargement ou `storageState` |
| adresse locale | `server.address=127.0.0.1` | `PASS` |
| flags fournisseur | sept valeurs versionnées `false` par défaut | `PASS` |
| livraison réelle | `optional-integration.enabled=false` et `remote-delivery-authorized=false` | `PASS` |
| ressources résiduelles | processus attribuable au worktree, listener `8087`, conteneur Testcontainers | `PASS` — `0/0/0` |
| Compose | présence de `.env` dans le worktree dédié | `NOT_RUN_NOT_REQUIRED` — `.env` absent, aucun changement Compose sous WO-030 |

Un premier lancement ciblé dans le sandbox s'est arrêté avant exécution des tests, Java ne pouvant
pas lire `spring-orm-7.0.8.jar` dans le cache Maven utilisateur. La même commande a été relancée
hors sandbox avec le cache explicitement désigné, toujours sous `--offline`, puis a réussi. Cet
incident d'accès local n'est ni un échec du correctif, ni un appel réseau, ni une autorisation de
fallback.

Les avertissements Hikari observés pendant la fermeture de certaines intégrations signalent des
connexions Testcontainers déjà fermées ; les tests concernés et les deux builds finaux sont verts.

## 6. Revue indépendante

Une revue indépendante du delta a d'abord relevé que le test de policy prouvait le snapshot bloqué
sans invoquer directement le claim. Le test a été renforcé avant le commit d'implémentation et les
portes finales. Après cette correction de preuve, la revue ne relève plus de bug fonctionnel,
d'élargissement de portée ou de problème de sécurité dans le code et les tests WO-030.

## 7. Compatibilité et sécurité

```text
ENDPOINTS_CHANGED=NO
ALLOWLIST_CHANGED=NO
PROVIDER_ORIGIN_CHANGED=NO
TRANSPORT_CHANGED=NO
IPC_PROTOCOL_CHANGED=NO
DATABASE_OR_MIGRATION_CHANGED=NO
PLAYWRIGHT_AUTOSTART_ADDED=NO
RETRY_OR_FALLBACK_ADDED=NO
POLLING_OR_SCHEDULER_ADDED=NO
RAW_PAYLOAD_OR_SECRET_RECORDED=NO
OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
```

## 8. Verdict

```text
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
PARENT_PORT_RANGE_GUARD=PASS
WORKER_PORT_RANGE_GUARD=PASS
OUT_OF_RANGE_REFUSED_BEFORE_PROVIDER_CLAIM=PASS
OUT_OF_RANGE_REFUSED_BEFORE_NAVIGATION=PASS
IMPLEMENTATION_COMMIT=154349a2fbebe3fd0a43a63c7105f690ff04976b
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
OWNER_CONFIRMATION_REQUIRED=YES
PROVIDER_CALLS=0
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Le résultat qualifie le correctif local. Il n'autorise ni déplacement du Work Order vers
`completed`, ni push, ni fusion, ni nouvelle campagne fournisseur. Ces actions restent soumises à
leurs décisions explicites respectives.
