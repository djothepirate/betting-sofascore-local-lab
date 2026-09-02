# WO-SS-20260902-035 — Sender réel J7 du Local Lab

- **Statut :** `READY_FOR_OWNER_REVIEW`
- **Jalon :** après J9 — activation contrôlée de `OPTIONAL_LOCAL_PUSH`
- **Ouvert le :** 2026-09-02
- **Ouverture UTC :** `2026-09-02T16:32:56.7260511Z`
- **Ouverture Europe/Paris :** `2026-09-02T18:32:56.7355286+02:00`
- **Branche :** `codex/j9-wo035-real-j7-delivery-sender`
- **Worktree :** `.tmp/j9-wo035-real-j7-delivery-sender`
- **Base locale d’ouverture vérifiée :** `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089`
- **`origin/main` intégré avant implémentation :** `579fa2545cc2cb8e7ce31afc15f0ec4fb3fd1571`
- **Commit d’alignement :** `49fac876ca5ee2f6dad603c3a339ca9b47dc1b1e`
- **Commit d’implémentation qualifié :** `f5a27887b7db43576eb608d564c245c8cca3a602`
- **Rapport de qualification :**
  `docs/validation/J9-WO035-REAL-J7-DELIVERY-SENDER-QUALIFICATION-20260902.md`
- **SHA-256 du rapport :**
  `1028761cbc68154da780d314935880bf17b803c3cf8a35b1386fd29ab28bae76`
- **Résultat :** `PASS_LOCAL_FAIL_CLOSED`
- **Work Order parent :** `WO-SS-20260901-027-optional-local-push-implementation` — `VALIDATED`
- **ADR :** `ADR-SS-003 v0.1` — `ACCEPTED`
- **Permission officielle :** `NOT_EVIDENCED`

## 1. Autorisation et séquencement

Le propriétaire a demandé une réalisation en trois étapes : WO-035, puis WO-036 pour la campagne
E2E synthétique Windows/Windows, puis seulement la création de la PR d'INT-001 dans le dépôt
Betting Project. Le présent Work Order exécute exclusivement la première étape.

```text
WORK_ORDER=WO-SS-20260902-035-j9-real-j7-delivery-sender
WORK_ORDER_STATUS=READY_FOR_OWNER_REVIEW
BRANCH=codex/j9-wo035-real-j7-delivery-sender

WO035_IMPLEMENTATION_AUTHORIZED=YES
WO036_CAMPAIGN_AUTHORIZED_BY_THIS_WORK_ORDER=NO
INT001_PULL_REQUEST_AUTHORIZED_BY_THIS_WORK_ORDER=NO

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

WO-035 peut construire et qualifier hors ligne le chemin runtime réel. Il n'autorise ni connexion à
un receiver réel, ni transfert d'un export dérivé de SofaScore, ni appel fournisseur. Les tests
réseau restent limités à des doubles locaux contrôlés ; le premier E2E à deux applications réelles
appartiendra à WO-036.

## 2. Objectif

Transformer le socle fail-closed de WO-027 en sender Local Lab réellement composé par Spring, tout
en maintenant toutes les portes réelles fermées par défaut :

1. action UI locale distincte, disponible uniquement pour un export `HUMAN_VALIDATED` ;
2. phrase exacte `LIVRER J7 <exportId> SHA256 <fileSha256>` et jeton formulaire local à usage
   unique, avec demande liée en mémoire au prochain ordinal de tentative attendu ;
3. lecture et envoi des octets exacts de l'enveloppe J7 validée ;
4. transport `POST` HTTPS/mTLS vers le contrat INT-001, sans redirection, proxy, cookie, retry,
   fallback, polling ou scheduler ;
5. sélection stricte du certificat client dans `Windows-MY`, sans secret ni clé privée en Git ;
6. affichage du ledger séparé et réconciliation opérateur explicite d'un `IN_FLIGHT` réellement
   stale ;
7. maintien des six états de livraison séparés de la validation J7 ;
8. frontière navigateur liée au `HandlerMethod`, Host/Origin exacts, en-têtes forwarded refusés et
   protections anti-frame ;
9. transport mono-exécution, body publisher one-shot et gate partagée livraison/réconciliation
   tenue jusqu’après la fermeture effective du transport.

## 3. Contrat et invariants réutilisés

WO-035 ne modifie ni l'enveloppe J7, ni son schéma, ni le contrat HTTP v1.0 versionné sous WO-027.

```text
METHOD=POST
PATH=/api/imports/sofascore/j7-canonical-events
REQUEST_MEDIA_TYPE=application/vnd.betting-project.j7-canonical-event+json;version=1.0
ACK_MEDIA_TYPE=application/vnd.betting-project.j7-delivery-ack+json;version=1.0
MAX_REQUEST_BYTES=5242880
MAX_ACK_BYTES=16384
CONCURRENCY=1
AUTOMATIC_RETRY=0
REDIRECTS=NEVER
CONNECT_TIMEOUT_MAXIMUM=10s
REQUEST_TIMEOUT_MAXIMUM=10s
BODY=EXACT_HUMAN_VALIDATED_J7_BYTES
```

La génération, la validation humaine, le démarrage de l'application et toute acquisition
fournisseur ne déclenchent jamais la livraison. Le port `8087` reste celui de l'interface loopback
du Local Lab ; le receiver est une cible HTTPS distincte et ne vient jamais récupérer le fichier.

## 4. Portes runtime

Le chemin synthétique loopback peut être qualifié sans permission SofaScore. Le chemin portant une
donnée dérivée fournisseur reste refusé avant claim, lecture du certificat et ouverture de socket
tant que toutes les preuves suivantes ne sont pas simultanément satisfaites :

```text
J9_OFFICIAL_PERMISSION_STATUS=EVIDENCED_COMPATIBLE
RECEIVER_QUALIFICATION=PASS
SENDER_QUALIFICATION=PASS
PROVIDER_DERIVED_DELIVERY_OWNER_GO=GRANTED_ONE_TIME
```

La configuration versionnée reste à `NOT_EVIDENCED`, désactivée, sans origine receiver et sans go.
L’unique origine locale admissible au runtime WO-035 est exactement
`https://127.0.0.1:8444`, sans userinfo, path, query ou fragment. Toute autre origine, tout autre
port et toute topologie VPS restent hors périmètre de WO-035.

## 5. Implémentation attendue

- étendre `OptionalLocalPushProperties` avec des portes explicites, sûres et désactivées par
  défaut ;
- composer conditionnellement politique, sender, transport et identité mTLS sans consulter le
  magasin Windows au démarrage normal ;
- faire appliquer les portes avant chargement du payload et avant claim ;
- conserver la sélection du certificat client par empreinte SHA-256 exacte, la validité, la
  présence de clé privée et les usages client TLS ;
- protéger la route locale avec le jeton formulaire consommable existant ;
- rendre l'état courant et les blockers visibles sans exposer payload, ACK brut, URI sensible,
  alias, chaîne de certificat ou diagnostics de transport ;
- exposer une action de réconciliation uniquement pour un claim `IN_FLIGHT` stale et corrélé ;
- classifier toute erreur après claim en `UNKNOWN_RECONCILIATION_REQUIRED`, sans second envoi ;
- imposer `SYNTHETIC_ONLY` dans la voie synthétique elle-même avant claim, factory et socket ;
- appliquer la frontière locale à toute méthode de `J7DeliveryController` via le `HandlerMethod`
  résolu, avec `Host`/`Origin` exacts, refus des en-têtes forwarded et protections anti-frame ;
- rendre chaque instance de transport mono-exécution et son body publisher one-shot ;
- partager une gate singleton `IDLE`/`ACTIVE`/`POISONED` entre livraison et réconciliation, garder
  son lease jusqu’après `transport.close()` et exiger un redémarrage après toute erreur de close ;
- réaliser la réconciliation à partir des seules métadonnées d’export et du ledger, indépendamment
  de la présence ou de la lisibilité du fichier payload.

## 6. Vérifications obligatoires

- tests de propriétés et portes : defaults, origine, permission, readiness sender/receiver et go ;
- tests service : export non validé, confirmation invalide, `201`, `200`, `409`, autres `4xx`,
  `3xx`, `5xx`, timeout, TLS, ACK hostile et exception synchrone ;
- test d'absence d'interaction avec export, ledger, certificat et transport lorsque la politique
  refuse ;
- tests web : action absente hors `HUMAN_VALIDATED`, jeton consommé une fois, phrase exacte,
  affichage minimisé du ledger, réconciliation protégée et demande inter-session périmée refusée
  si une tentative concurrente a avancé l’ordinal ;
- test PostgreSQL : ordinal confirmé propagé jusqu’au claim et refus atomique d’un ordinal périmé,
  sans insert de tentative suivante, sans nouvel `IN_FLIGHT` et sans socket ;
- appels directs de la voie synthétique avec provenance fournisseur et mixte refusés avant ledger
  et transport ;
- tests de frontière web par handler : Host/Origin exacts, doublons, en-têtes forwarded, URI brute
  hostile, CSP `frame-ancestors 'none'` et `X-Frame-Options: DENY` ;
- test d’une même instance de transport exécutée deux fois et d’une seconde souscription du body,
  toutes deux refusées sans second `sendAsync` ;
- tests de concurrence : une deuxième livraison et une réconciliation refusées pendant un close
  bloqué ; retour à `IDLE` après succès ; passage durable à `POISONED` après erreur de close ;
- test de réconciliation metadata/ledger-only lorsque toute tentative d’accès au payload échoue ;
- tests de composition : aucun accès `Windows-MY` ni client HTTP lorsque le sender est désactivé ;
- maintien des tests PostgreSQL V29, mTLS et E2E synthétique existants ;
- `mvnw.cmd clean verify` ;
- `mvnw.cmd -Pintegration-tests verify` ;
- `docker compose --env-file .env config` si la CLI Docker est disponible ;
- `git diff --check`, contrôle de secrets, UTF-8, loopback et flags bloquants.

## 7. Critères de sortie

Le Work Order restera actif après le commit qualifié jusqu'à revue propriétaire. La clôture exige
un rapport autonome, les hashes des preuves, un résultat local fail-closed et un bloc propriétaire
distinct. Aucun push, merge, appel fournisseur, receiver réel, livraison réelle, VPS ou production
n'est déduit de la réussite locale.

## 8. Résultat qualifié

Le sender runtime a été implémenté et qualifié au commit exact
`f5a27887b7db43576eb608d564c245c8cca3a602`. Deux cycles complets Maven successifs passent avec
1 115 tests standards et 85 tests d'intégration, sans échec. Les contrôles Compose, local-only,
distribution, reproductibilité, syntaxe, UTF-8, secrets, loopback et processus résiduels passent
également.

```text
WO035_RUNTIME_SENDER_RESULT=PASS_LOCAL_FAIL_CLOSED
WO035_IMPLEMENTATION_COMMIT=f5a27887b7db43576eb608d564c245c8cca3a602
WO035_QUALIFICATION_REPORT=docs/validation/J9-WO035-REAL-J7-DELIVERY-SENDER-QUALIFICATION-20260902.md
WO035_QUALIFICATION_REPORT_SHA256=1028761cbc68154da780d314935880bf17b803c3cf8a35b1386fd29ab28bae76

WO035_PROVIDER_DERIVED_PATH=BLOCKED
WO035_PROVIDER_CALLS=0
WO035_REAL_RECEIVER_CALLS=0
WO035_WO036_OPENED=NO
WO035_INT001_PULL_REQUEST_CREATED=NO

J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
OWNER_REVIEW_REQUIRED=YES
```

Le Work Order reste actif jusqu'à décision propriétaire explicite. Une validation pourra autoriser
son déplacement vers `completed`, sans autoriser par elle-même WO-036, une PR INT-001, un réseau
réel ou une production.
