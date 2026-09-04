# J9 — Qualification WO-045 de la frontière de go propriétaire fournisseur

## 1. Résultat

```text
WORK_ORDER=WO-SS-20260904-045-j9-provider-derived-owner-go-boundary
BRANCH=codex/j9-wo045-provider-derived-owner-go-boundary
BASE_COMMIT=400900410dfa751521ce387fbadcc4b5ca95a94a
OPENING_COMMIT=831e011f3e82f03761029fcfa5b7990c11b50bf4
IMPLEMENTATION_COMMIT=67467d5dbd63fa54d11b2d1cd701a31edf4454e0
QUALIFIED_AT_UTC=2026-09-04T13:45:37.017643Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-04T15:45:37.018649+02:00
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
PROVIDER_DERIVED_REAL_POSTS=0
PROVIDER_CALLS=0
REMOTE_RECEIVER_CALLS=0
VPS_CONNECTIONS=0
WO046_OPENED=NO
WO046_MANIFEST_CREATED=NO
WO046_OWNER_GO_GRANTED=NO
```

WO-045 implémente et qualifie la frontière durable, exacte, atomique et à usage unique requise
avant qu'une future campagne puisse livrer un export J7 dérivé de données fournisseur. Cette
qualification ne crée aucun grant réel, ne consomme aucun go propriétaire de campagne, n'ouvre pas
WO-046 et ne réalise aucun POST réel.

## 2. Contrat implémenté

La voie `PROVIDER_DERIVED` exige désormais une référence exacte composée de `GO_ID` et du SHA-256
du document propriétaire canonique. Le document lie notamment :

- le Work Order et le manifeste gelé de campagne ;
- les commits qualifiés du Local Lab et du receiver ;
- la preuve officielle référencée et son empreinte ;
- l'acteur, l'événement canonique, l'identifiant fournisseur et l'export ;
- les hashes fichier/données, la taille, le schéma et sa version ;
- l'origine receiver exacte et l'empreinte publique du certificat client ;
- l'ordinal `1`, un seul appel d'import, une fenêtre UTC semi-ouverte de 60 minutes maximum ;
- les constantes `GRANT`, `ONE_TIME`, `PROVIDER_DERIVED`, `HUMAN_VALIDATED` et les drapeaux
  fail-closed requis.

La sérialisation normative impose l'ordre fixe des clés, UTF-8 sans BOM, LF entre les lignes et LF
final, UUID minuscules, URI ASCII et instants UTC à exactement six chiffres de microsecondes. Le
champ externe `OWNER_GO_DOCUMENT_SHA256` ne fait pas partie de son propre préimage. Java et
PostgreSQL recalculent indépendamment cette empreinte avant d'accepter le grant.

## 3. Persistance et atomicité

La migration additive V31 introduit trois journaux sans payload : grants, révocations et
consommations. Ils sont append-only et protégés par des contraintes et triggers PostgreSQL. La
classification bornée `payload_class` est ajoutée à la tentative de livraison avec les seules
valeurs `SYNTHETIC_ONLY` et `PROVIDER_DERIVED` ; elle ne contient aucun octet J7.

Pour une tentative fournisseur, l'adaptateur :

1. prend les verrous applicables et relit le grant exact ;
2. évalue la fenêtre avec l'horloge PostgreSQL après acquisition du verrou ;
3. requalifie toutes les identités, hashes, tailles, statuts et constantes ;
4. insère la tentative, la consommation et le passage du ledger à `IN_FLIGHT` dans une transaction
   `REQUIRES_NEW` unique ;
5. ne construit le transport et n'accède au certificat qu'après le commit réussi.

Une erreur avant ce commit ne laisse ni consommation seule ni claim seul. Dès que le commit est
acquis, le go reste définitivement consommé, y compris après erreur de factory, certificat, TLS,
HTTP, ACK, cleanup, résultat inconnu ou redémarrage. Aucun DELETE, remboursement, retry automatique
ou second claim n'existe. La réconciliation reste locale et ne contacte pas le receiver.

Les fonctions de garde V31 fixent leur `search_path` et qualifient explicitement les relations et
fonctions applicatives. Un test PostgreSQL avec un schéma hostile et une fonction homonyme confirme
qu'aucun détournement de la canonisation ou du hash n'est possible.

## 4. Frontière de confirmation locale

La préparation conserve la référence du grant côté serveur ; cette référence n'est ni reçue d'un
champ HTTP ni exposée dans le modèle HTML. Le challenge reste lié à la session, à l'événement, à
l'export, au hash courant et au prochain ordinal, puis est brûlé à la première soumission, valide
ou non.

Une confirmation humaine valide produit une capacité éphémère liée à l'identité de l'instance Java
exacte du reçu. La capacité :

- expire avec la demande de confirmation ;
- est consommée atomiquement une seule fois par le runtime ;
- disparaît au redémarrage ;
- ne peut pas être reconstruite par copie des champs du reçu.

Le runtime consomme cette capacité avant toute lecture de l'export, consultation du grant,
construction du transport, lecture de certificat ou socket. Les tests refusent une copie égale du
reçu, une seconde consommation, un reçu expiré et toute substitution de référence.

## 5. Vérifications fonctionnelles et de sécurité

Les tests couvrent notamment :

- defaults désactivés et référence de grant vide ;
- voie synthétique inchangée et `MIXED_OR_UNKNOWN` toujours refusé ;
- document canonique Java/SQL byte-identique et hash constant-time côté Java ;
- format, ordre, LF final, microsecondes, URI, booléens et aliases fermés ;
- absence, divergence, fenêtre future/expirée/inversée/trop longue, révocation et consommation ;
- divergence de Work Order, manifeste, commits, preuve officielle, acteur, événement, export,
  hashes, taille, schéma, origine, certificat, ordinal et plafond ;
- concurrence sur un grant : un seul gagnant et une seule tentative ;
- course révocation/consommation ;
- rollback complet sur échec transactionnel ;
- consommation définitive après claim et absence de retry ;
- reçu de confirmation construit ou copié refusé avant toute consultation durable ;
- migration V30 vers V31, installation neuve V31 et résistance au `search_path` hostile ;
- sauvegarde/restauration avec empreintes et compteurs identiques pour les trois journaux owner-go ;
- aucune colonne apte à stocker un payload brut, hors le nom exact de classification bornée
  `payload_class` qui reste de type `varchar(32)` ;
- HTTPS/mTLS et E2E strictement synthétiques sur `127.0.0.1`.

Trois revues adversariales finales indépendantes ont recherché les contournements du reçu, les
réutilisations de go, les ouvertures de socket prématurées, les incohérences Java/SQL, les courses,
les faiblesses de `search_path` et les lacunes de sauvegarde/restauration. Résultat :

```text
FINAL_RED_TEAM_P1_FINDINGS=0
FINAL_RED_TEAM_P2_FINDINGS=0
FINAL_RED_TEAM_MUTATIONS=0
```

## 6. Commandes et résultats

Les exécutions Maven ont utilisé Java 25.0.4, le wrapper du dépôt, le cache Maven local hors ligne
et `maven.compiler.fork=true`. Ce dernier paramètre contourne une erreur d'accès Windows du sandbox
Codex lors de la fermeture de `spring-orm-7.0.8.jar` dans le cache utilisateur ; il ne change ni le
code testé ni le périmètre réseau.

### 6.1 Qualification ciblée après correction de preuve

```text
mvnw.cmd --offline -q -Dmaven.repo.local=C:\Users\geoff\.m2\repository
  -Dmaven.compiler.fork=true
  -Djava.io.tmpdir=C:\Dev\BettingProject\codex\betting-sofascore-local-lab\.tmp\w45\target\tmp-flyway
  -Dit.test=FlywayMigrationIT#restoresJ8AndJ7DeliveryEvidenceWithIdenticalFingerprints
  test-compile failsafe:integration-test failsafe:verify
RESULT=PASS
TESTS=1
FAILURES=0
ERRORS=0
```

Le contrôle historique de colonnes sensibles comptait initialement `payload_class` uniquement à
cause de son nom. Le correctif exempte ce nom exact dans la branche lexicale, tout en conservant la
détection par types `bytea`, `json`, `jsonb` et `text` ainsi que tous les autres noms suspects. Le
contrôle spécialisé appliquait déjà cette même exception. La sauvegarde et la restauration
elles-mêmes n'étaient pas en défaut.

### 6.2 Build standard complet

```text
mvnw.cmd --offline -q -Dmaven.repo.local=C:\Users\geoff\.m2\repository
  -Dmaven.compiler.fork=true
  -Djava.io.tmpdir=C:\Dev\BettingProject\codex\betting-sofascore-local-lab\.tmp\w45\target\tmp-standard clean verify
RESULT=PASS
SUREFIRE_TESTS=1167
SUREFIRE_FAILURES=0
SUREFIRE_ERRORS=0
SUREFIRE_SKIPPED=5
FAILSAFE_TESTS=100
FAILSAFE_FAILURES=0
FAILSAFE_ERRORS=0
FAILSAFE_SKIPPED=0
```

### 6.3 Profil d'intégration complet

```text
mvnw.cmd --offline -q -Dmaven.repo.local=C:\Users\geoff\.m2\repository
  -Dmaven.compiler.fork=true
  -Djava.io.tmpdir=C:\Dev\BettingProject\codex\betting-sofascore-local-lab\.tmp\w45\target\tmp-integration -Pintegration-tests verify
RESULT=PASS
SUREFIRE_TESTS=1167
SUREFIRE_FAILURES=0
SUREFIRE_ERRORS=0
SUREFIRE_SKIPPED=5
FAILSAFE_TESTS=100
FAILSAFE_FAILURES=0
FAILSAFE_ERRORS=0
FAILSAFE_SKIPPED=0
```

Le total Failsafe comprend `69` tests Flyway, `25` tests du ledger J7, `4` tests mTLS loopback et
`2` tests E2E loopback. Les receivers de test sont synthétiques et liés à `127.0.0.1`.

### 6.4 Contrôles complémentaires

```text
POWERSHELL_PARSE_BACKUP_RESTORE_J6=PASS
POWERSHELL_PARSE_J6_RETENTION=PASS
DOCKER_COMPOSE_CONFIG_WITH_IGNORED_ROOT_ENV=PASS
GIT_DIFF_CHECK=PASS
UTF8_CHANGED_FILES=33
UTF8_INVALID_FILES=0
UTF8_BOM_FILES=0
SECRET_SCAN_CHANGED_FILES=33
SECRET_SCAN_HITS=0
SERVER_ADDRESS=127.0.0.1
DEFAULT_DELIVERY_ENABLED=false
DEFAULT_AUTOMATIC_RETRY_ENABLED=false
SOFASCORE_ENABLED_DEFAULT=false
LIVE_POLLING_ENABLED_DEFAULT=false
```

Le worktree ne recopie pas le fichier `.env` ignoré. Le contrôle Compose a donc lu, sans afficher
son contenu, le `.env` local du dépôt racine. Après les tests, le seul conteneur visible est le
PostgreSQL primaire préexistant `betting-sofascore-local-lab-postgres`, toujours borné à
`127.0.0.1:5432`. Aucun conteneur Testcontainers n'est résiduel.

## 7. Sauvegarde et restauration J6

Les scripts J6 attendent désormais le schéma V31 et incluent dans leurs empreintes, compteurs et
preuves restaurées :

```text
j7_provider_delivery_owner_go_grant
j7_provider_delivery_owner_go_revocation
j7_provider_delivery_owner_go_consumption
```

La restauration Testcontainers compare les empreintes source/restaurée et retrouve exactement deux
grants, une révocation et une consommation dans son corpus synthétique. Aucun essai de sauvegarde ou
de purge de la base primaire n'a été réalisé sous WO-045.

## 8. Fichiers concernés

- contrat, configuration, politique, confirmation, runtime, query et contrôleur de livraison J7 ;
- adaptateur et port du ledger PostgreSQL ;
- migration additive `V31__j9_provider_derived_owner_go_boundary.sql` ;
- scripts et runbook J6 de sauvegarde/restauration et rétention ;
- documentation d'architecture, sécurité et exploitation J9 ;
- tests unitaires, web, configuration, PostgreSQL, concurrence et loopback.

Aucun endpoint SofaScore, contrat J7, contrat HTTP receiver, migration antérieure, PDF de référence,
certificat, secret, payload réel ou ACK brut n'est ajouté.

## 9. Conclusion et portes restantes

```text
WO045_STATUS=READY_FOR_OWNER_REVIEW
WO045_QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
WO045_PROVIDER_DERIVED_REAL_POSTS=0
WO046_OPENING_AUTHORIZED=NO
WO046_REAL_POST_AUTHORIZED=NO
WO046_NEW_MANIFEST_BOUND_OWNER_GO_GRANTED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

La mise en œuvre est techniquement prête pour revue propriétaire locale. Seule une validation
explicite pourra classer WO-045. L'ouverture de WO-046, son nouveau manifeste gelé, son go exact et
un éventuel POST réel exigent ensuite une décision propriétaire séparée.
