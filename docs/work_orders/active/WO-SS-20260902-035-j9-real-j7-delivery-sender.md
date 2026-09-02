# WO-SS-20260902-035 — Sender réel J7 du Local Lab

- **Statut :** `IN_PROGRESS`
- **Jalon :** après J9 — activation contrôlée de `OPTIONAL_LOCAL_PUSH`
- **Ouvert le :** 2026-09-02
- **Ouverture UTC :** `2026-09-02T16:32:56.7260511Z`
- **Ouverture Europe/Paris :** `2026-09-02T18:32:56.7355286+02:00`
- **Branche :** `codex/j9-wo035-real-j7-delivery-sender`
- **Worktree :** `.tmp/j9-wo035-real-j7-delivery-sender`
- **Base locale vérifiée :** `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089`
- **Work Order parent :** `WO-SS-20260901-027-optional-local-push-implementation` — `VALIDATED`
- **ADR :** `ADR-SS-003 v0.1` — `ACCEPTED`
- **Permission officielle :** `NOT_EVIDENCED`

## 1. Autorisation et séquencement

Le propriétaire a demandé une réalisation en trois étapes : WO-035, puis WO-036 pour la campagne
E2E synthétique Windows/Windows, puis seulement la création de la PR d'INT-001 dans le dépôt
Betting Project. Le présent Work Order exécute exclusivement la première étape.

```text
WORK_ORDER=WO-SS-20260902-035-j9-real-j7-delivery-sender
WORK_ORDER_STATUS=IN_PROGRESS
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
   unique ;
3. lecture et envoi des octets exacts de l'enveloppe J7 validée ;
4. transport `POST` HTTPS/mTLS vers le contrat INT-001, sans redirection, proxy, cookie, retry,
   fallback, polling ou scheduler ;
5. sélection stricte du certificat client dans `Windows-MY`, sans secret ni clé privée en Git ;
6. affichage du ledger séparé et réconciliation opérateur explicite d'un `IN_FLIGHT` réellement
   stale ;
7. maintien des six états de livraison séparés de la validation J7.

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
Une origine locale admissible est exactement une origine `https://127.0.0.1:<1..65535>` sans
userinfo, path, query ou fragment. Toute topologie VPS reste hors périmètre de WO-035.

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
- classifier toute erreur après claim en `UNKNOWN_RECONCILIATION_REQUIRED`, sans second envoi.

## 6. Vérifications obligatoires

- tests de propriétés et portes : defaults, origine, permission, readiness sender/receiver et go ;
- tests service : export non validé, confirmation invalide, `201`, `200`, `409`, autres `4xx`,
  `3xx`, `5xx`, timeout, TLS, ACK hostile et exception synchrone ;
- test d'absence d'interaction avec export, ledger, certificat et transport lorsque la politique
  refuse ;
- tests web : action absente hors `HUMAN_VALIDATED`, jeton consommé une fois, phrase exacte,
  affichage minimisé du ledger et réconciliation protégée ;
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

