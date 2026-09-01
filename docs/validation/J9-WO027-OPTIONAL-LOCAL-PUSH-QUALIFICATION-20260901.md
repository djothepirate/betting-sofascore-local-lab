# J9 — Qualification WO-027 du push local optionnel fail-closed

## 1. Résultat

```text
REPORT=J9-WO027-OPTIONAL-LOCAL-PUSH-QUALIFICATION-20260901
WORK_ORDER=WO-SS-20260901-027-optional-local-push-implementation
BRANCH=codex/j9-optional-local-push-implementation
BASE_COMMIT=aedb5f424883c9e7a1839fd50aa2c2689fa65418
QUALIFIED_AT_UTC=2026-09-01T10:27:33.4845169Z
QUALIFIED_AT_EUROPE_PARIS=2026-09-01T12:27:33.4850233+02:00
QUALIFICATION_RESULT=PASS_LOCAL_FAIL_CLOSED
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
REAL_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

WO-027 qualifie le contrat v1.0, le sender local explicitement non activable, son ledger séparé,
l'idempotence, les accusés bornés et mTLS sur un receiver synthétique loopback. La preuve ne vaut
ni consentement officiel, ni qualification du receiver Betting Project réel, ni go de livraison.
Aucun appel SofaScore, Betting Project réel ou VPS n'a été effectué.

## 2. Périmètre réellement livré

- contrat versionné `J7_OPTIONAL_LOCAL_PUSH` v1.0 et schéma strict de l'ACK v1.0 ;
- relecture défensive de l'export J7 canonique déjà `HUMAN_VALIDATED`, avec vérification du chemin,
  de la taille, du schéma, des identités et des deux SHA-256 avant transport ;
- clé d'idempotence déterministe
  `j7:<exportId>:sha256:<fileSha256>` et confirmation opérateur exacte ;
- machine à six états de livraison, distincte du statut J7 ;
- migration append-only V29 pour l'identité, les tentatives, les résultats et la projection gardée ;
- concurrence globale `1`, absence de retry automatique et réconciliation manuelle seulement ;
- transport HTTPS borné, sans redirection, cookie, proxy, compression de requête, fallback ou
  cible alternative ;
- lecture de toute réponse limitée à `16 384` octets, y compris pour les erreurs ;
- délai absolu couvrant l'échange jusqu'à EOF et nettoyage borné/réessayable du client HTTP ;
- politique mTLS avec sélection exacte du certificat et refus de toute clé dont l'encodage Java
  est exposé ;
- harness synthétique uniquement sur `127.0.0.1` et port éphémère ;
- extension des preuves J6 V29 pour inclure les trois tables du ledger de livraison dans le cycle
  sauvegarde/restauration et son empreinte métadonnée.

Le service d'orchestration et le transport ne sont pas des beans Spring. Aucun contrôleur, route,
scheduler, polling, worker automatique, URI réelle ou mécanisme de démarrage automatique n'a été
ajouté.

## 3. Porte de permission officielle

La revue distincte
[`J9-WO027-OFFICIAL-PERMISSION-REVIEW-20260901.md`](J9-WO027-OFFICIAL-PERMISSION-REVIEW-20260901.md)
a consulté les sources officielles publiques disponibles et conclut
`J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED`. Une documentation d'API et un canal de contact ne
constituent pas une autorisation applicable au corpus, à son transfert et à l'usage envisagé. Ce
constat n'est pas un avis juridique.

Cette valeur est exécutoire : les propriétés par défaut restent désactivées, l'autorisation runtime
reste fausse, aucune URI réelle n'existe et la politique refuse la construction ou l'envoi réel
avant DNS et socket. Une cible réelle exigera une preuve officielle versionnée et compatible, une
revue d'ADR-SS-003 et une décision propriétaire distincte.

## 4. Contrat, idempotence et accusés

Le contrat qualifié est documenté dans
[`J9-OPTIONAL-LOCAL-PUSH.md`](../architecture/J9-OPTIONAL-LOCAL-PUSH.md) et son schéma d'ACK dans
[`j7-delivery-ack-v1.schema.json`](../../src/main/resources/schemas/j7-delivery-ack-v1.schema.json).

Les résultats positifs sont volontairement étroits :

| Réponse bornée et corrélée | État local |
|---|---|
| HTTP `201` + ACK `IMPORTED` | `DELIVERED` |
| HTTP `200` + ACK `DUPLICATE` | `DUPLICATE_CONFIRMED` |
| HTTP `4xx` reçu normalement | `REJECTED_TERMINAL` |
| timeout, TLS, transport, `5xx`, ACK absent/invalide/non corrélé ou corps hostile | `UNKNOWN_RECONCILIATION_REQUIRED` |

La collision d'identité est traitée à deux frontières distinctes :

1. le sender refuse localement un `exportId` connu avec un autre `fileSha256` avant claim, avant
   création d'une nouvelle identité et avant transport ; aucune ligne ou tentative trompeuse n'est
   créée ;
2. si un futur receiver répond HTTP `409` à une requête contractuellement valide, le sender classe
   cette réponse bornée en `REJECTED_TERMINAL`, sans l'accepter comme doublon positif.

Le receiver synthétique n'est donc pas artificiellement contourné pour produire un 409 impossible
à partir d'une requête que les contrôles locaux auraient déjà rejetée. La preuve combine le test
PostgreSQL de refus d'identité avant claim et le test de classification contractuelle du 409.

## 5. mTLS et limites de la preuve cryptographique

Les qualifications mTLS synthétiques couvrent :

- chaîne et nom serveur corrects avec certificat client attendu ;
- autorité serveur non approuvée ;
- nom SAN serveur divergent ;
- certificat client incorrect.

Les quatre scénarios sont verts. Les éléments cryptographiques sont générés dans le répertoire
temporaire du test, supprimés ensuite et ne correspondent à aucune identité réelle.

Le code qualifie l'opacité Java de la clé sélectionnée. Il ne démontre pas qu'une future clé privée
du magasin utilisateur Windows a été provisionnée avec une politique native non exportable. Cette
preuve, la révocation, la chaîne réelle, le renouvellement et le contrôle de l'identité exacte
restent `NOT_QUALIFIED_FOR_REAL_TARGET`.

## 6. Persistance et séparation J7

La migration V29 introduit un ledger dédié :

- identité de livraison immuable ;
- tentative et résultat append-only ;
- projection courante mise à jour uniquement par transitions gardées ;
- un seul `IN_FLIGHT` global ;
- ordinal exact et corrélations strictes ;
- horloge PostgreSQL pour la création et l'âge d'un `IN_FLIGHT` ;
- réconciliation manuelle d'un état stale seulement après `30 s`, sans terminaison ni retry
  automatique.

Les tests de migration et end-to-end prouvent que le statut de l'export J7 reste
`HUMAN_VALIDATED` après succès, doublon et échec de livraison. La sauvegarde/restauration J6 exige
désormais Flyway V29 et compare les comptes et l'empreinte métadonnée des trois tables du ledger,
sans élargir la purge primaire.

## 7. Politique réseau et absence de retry implicite

Le client JDK est créé avec `Redirect.NEVER`, `ProxySelector.of(null)`, aucun cookie handler et un
publisher de corps à souscription unique. Trois arguments doivent être présents dès le démarrage de
la JVM, puis sont revérifiés avant construction et avant envoi :

```text
-Djdk.httpclient.disableRetryConnect=true
-Djdk.httpclient.redirects.retrylimit=1
-Djdk.httpclient.enableAllMethodRetry=false
```

Ils sont configurés pour Surefire, Failsafe et `spring-boot:run`. Une future exécution Eclipse ou
`java -jar` devra les fournir explicitement ; leur absence ou divergence ferme localement le chemin
avant `sendAsync`. Cette contrainte n'autorise pas le runtime réel : elle retire seulement une
source de répétition implicite dans les qualifications autorisées.

## 8. Vérifications exécutées

### 8.1 Maven

| Commande | Résultat final |
|---|---|
| `.\mvnw.cmd --offline clean verify` | `BUILD SUCCESS` le `2026-09-01T10:20:24Z` |
| `.\mvnw.cmd --offline -Pintegration-tests verify` | `BUILD SUCCESS` le `2026-09-01T10:26:21Z` |

Sommes XML finales :

| Suite | Tests | Échecs | Erreurs | Skips |
|---|---:|---:|---:|---:|
| Surefire | 1 036 | 0 | 0 | 5 |
| Failsafe | 84 | 0 | 0 | 0 |

La suite d'intégration comprend `68` tests Flyway, `10` tests du ledger PostgreSQL, `4` tests mTLS
et `2` tests end-to-end sender + receiver synthétique + PostgreSQL.

Une exécution complète intermédiaire, antérieure au dernier état fonctionnel, avait fermé la porte
J6 avec `UNCLASSIFIED_FAIL_CLOSED` lors d'une observation transitoire de processus alors que ses
marqueurs fonctionnels étaient verts. Le test J6 isolé a réussi sans modification de code, puis les
deux vérifications complètes finales ci-dessus ont réussi. L'incident n'est ni masqué ni interprété
comme une preuve réseau ; le comportement observé était fail-closed.

### 8.2 Contrôles complémentaires

- configuration Compose résolue en lecture seule avec le fichier `.env` local ignoré : `PASS` ;
- première tentative depuis le worktree sans ce fichier ignoré : `NOT_EXECUTED`, puis remplacée
  par la validation ci-dessus, sans copier ni journaliser son contenu ;
- schéma JSON de l'ACK parsable, propriétés supplémentaires interdites et sept champs requis ;
- scripts PowerShell parsables ;
- `server.address=127.0.0.1`, flags fournisseur bloqués, intégration optionnelle désactivée et
  permission `NOT_EVIDENCED` ;
- `git diff --check` sans erreur de whitespace ;
- scan de secrets et artefacts navigateur interdits : aucun résultat ;
- aucun listener `8087`, worker, navigateur ou conteneur de test résiduel après qualification ;
- liens Markdown locaux contrôlés après production du présent rapport.

Les preuves ne conservent ni payload, ACK brut, cookie, jeton, certificat, clé, mot de passe,
passphrase, chemin sensible, HAR, trace, vidéo, capture, téléchargement ou `storageState`.

## 9. Lacunes et portes résiduelles

La première livraison réelle reste bloquée jusqu'à satisfaction cumulative de toutes les portes
suivantes :

1. permission officielle explicite, versionnée et compatible avec l'acquisition, le traitement et
   le transfert envisagés ;
2. Work Order distinct dans le dépôt du Betting Project pour implémenter et qualifier le receiver ;
3. contrat receiver effectivement accepté des deux côtés, URI réelle explicitement autorisée et
   rétention/purge/sauvegarde/restauration qualifiées côté receiver ;
4. PKI réelle, chaîne, SAN, révocation, rotation et clé Windows native non exportable qualifiés ;
5. revue d'ADR-SS-003 pour l'usage réel ;
6. décision propriétaire distincte autorisant une livraison réelle bornée ;
7. maintien démontré de l'indépendance du Betting Project et absence absolue de déclenchement
   d'acquisition SofaScore par la livraison.

Playwright VPS reste hors de portée et bloqué par la gouvernance courante. Une preuve loopback ne
modifie pas `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` ou `NO_CRITICAL_DEPENDENCY`.

## 10. Bloc de résultat soumis à revue propriétaire

```text
WORK_ORDER=WO-SS-20260901-027-optional-local-push-implementation
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
WO027_CONTRACT_VERSION=1.0
WO027_OFFLINE_RESULT=PASS
WO027_LOOPBACK_RESULT=PASS
WO027_MTLS_SYNTHETIC_RESULT=PASS
WO027_IDEMPOTENCY_RESULT=PASS
WO027_ACK_BOUND_16_KIB_RESULT=PASS
WO027_J7_STATE_SEPARATION_RESULT=PASS
WO027_PROVIDER_CALLS=0
WO027_REAL_RECEIVER_CALLS=0
WO027_RESIDUAL_LISTENERS=0
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
REAL_DELIVERY_AUTHORIZED=NO
BETTING_PROJECT_RECEIVER_IMPLEMENTED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
OWNER_REVIEW_REQUIRED=YES
```
