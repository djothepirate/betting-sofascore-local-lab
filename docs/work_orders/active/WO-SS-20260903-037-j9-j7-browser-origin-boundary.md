# WO-SS-20260903-037 — Frontière d’origine du navigateur pour la livraison J7

- **Statut :** `IN_PROGRESS`
- **Jalon :** après J9 — correction runtime préalable à la reprise de WO-036
- **Ouvert le :** 2026-09-03
- **Ouverture UTC :** `2026-09-03T08:58:02.7540081Z`
- **Ouverture Europe/Paris :** `2026-09-03T10:58:02.7540081+02:00`
- **Branche :** `codex/j9-wo037-j7-browser-origin-boundary`
- **Worktree :** `.tmp/j9-wo037-j7-browser-origin-boundary`
- **Base locale d’ouverture vérifiée :** `f4f53aa24b4a8a764819bdb7cf79c87482bd7244`
- **Work Order bloqué à l’origine :** `WO-SS-20260902-036-j9-j7-local-e2e-qualification`
- **Rapport du constat :**
  `docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-20260903.md`
- **SHA-256 du rapport du constat :**
  `5244422187f0bc591e3ce57f20b076037f64bad266ad9052b985d8127b475994`
- **Permission officielle :** `NOT_EVIDENCED`

## 1. Autorisation propriétaire

Le propriétaire a autorisé l’implémentation de ce Work Order et sa qualification dans un vrai
navigateur sur la seule interface loopback. Le bloc suivant est consigné mot pour mot :

```text
J9_WO037_OWNER_DECISION=AUTHORIZE_IMPLEMENTATION
J9_WO037_WORK_ORDER=WO-SS-20260903-037-j9-j7-browser-origin-boundary
J9_WO037_SCOPE=DIAGNOSE_CORRECT_AND_QUALIFY_NATIVE_BROWSER_REFERRER_POLICY_AND_LOCAL_ORIGIN_BOUNDARY_COMPATIBILITY
J9_WO037_LOOPBACK_BROWSER_QUALIFICATION_AUTHORIZED=YES
J9_WO037_PREFERRED_DIRECTION=SCOPE_SAME_ORIGIN_REFERRER_POLICY_TO_J7_DELIVERY_WITHOUT_ACCEPTING_OPAQUE_ORIGINS

J9_WO036_STATUS=STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION
J9_WO036_RESUME_AFTER_WO037_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
J9_WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Cette autorisation permet une modification runtime bornée, ses tests hors ligne et une
qualification manuelle avec un navigateur réel sur `http://127.0.0.1:8087`. Elle ne vaut ni
validation de WO-037, ni reprise de WO-036, ni autorisation de livraison.

## 2. Diagnostic établi par WO-036

La campagne WO-036 s’est arrêtée avant le premier appel de la route d’import du receiver. Sur la
page J7 servie par le Local Lab, la politique `Referrer-Policy: no-referrer` a conduit Brave à
émettre `Origin: null` lors du `POST` de navigation vers l’action `delivery/prepare`. La frontière
locale `J7DeliveryLocalRequestBoundaryInterceptor` a correctement refusé cette origine opaque par
un `403`, avant le contrôleur, le claim, le transport et tout accès au receiver.

Le résultat observé est cohérent avec l’algorithme de l’en-tête `Origin` du Fetch Standard et la
sémantique de `no-referrer`. Le défaut à corriger n’est donc pas le refus de `Origin: null`, qui
reste une protection nécessaire, mais l’incompatibilité entre la politique de la page locale qui
porte les formulaires J7 et la frontière d’origine exacte appliquée à leurs mutations.

Les preuves WO-036 établissent également :

```text
WO036_EVIDENCE_RESULT=STOPPED
WO036_STOP_CLASS=LOCAL_BROWSER_BOUNDARY_INCOMPATIBILITY_PRE_RECEIVER
LOCAL_PREPARE_HTTP_STATUS=403
HOST_CLASS=EXACT_127_0_0_1_8087
ORIGIN_CLASS=NULL
FORWARDED_HEADERS_PRESENT=NO
SENDER_DURABLE_ATTEMPTS=0
RECEIVER_IMPORT_ROUTE_CALLS=0
RECEIVER_DURABLE_OBJECTS=0
PROVIDER_CALLS=0
REMOTE_RECEIVER_CALLS=0
```

## 3. Objectif et périmètre strict

WO-037 doit réconcilier les réponses qui rendent les formulaires de livraison J7 avec la frontière
d’origine déjà qualifiée, suivant la direction propriétaire :

1. appliquer `Referrer-Policy: same-origin` uniquement au sous-arbre J7 qui rend les formulaires
   et reçoit les actions de livraison sous `/events/{canonicalEventId}/exports/**` ;
2. maintenir `Referrer-Policy: no-referrer` sur toutes les routes étrangères à ce sous-arbre ;
3. conserver la frontière de mutation fondée sur le `HandlerMethod` résolu ;
4. continuer à exiger le Host exact `127.0.0.1:8087` ;
5. conserver comme seules sémantiques admises un `Origin` absent ou une valeur unique exactement
   égale à `http://127.0.0.1:8087` ;
6. continuer à refuser `Origin: null`, toute origine différente ou dupliquée, tout Host absent,
   différent ou dupliqué et tout en-tête `Forwarded`, `X-Forwarded-Host` ou
   `X-Forwarded-Proto` ;
7. prouver dans un navigateur réel qu’un formulaire servi depuis l’origine loopback exacte envoie
   désormais l’origine exacte et franchit la frontière locale sans `403`.

La politique ciblée ne doit jamais être obtenue par acceptation spéciale de `null`, par déduction
à partir de `Referer`, par confiance dans des en-têtes proxy, par correspondance de sous-chaîne ou
par affaiblissement global des en-têtes de sécurité. Les jetons restent dans le corps des
formulaires et ne doivent pas être déplacés dans l’URL.

## 4. Hors périmètre et interdictions

WO-037 ne modifie ni le protocole J7, ni l’enveloppe canonique, ni la persistance, ni les états de
livraison, ni le transport HTTPS/mTLS, ni le receiver Betting Project. Il n’autorise pas l’action
`delivery/execute`, une confirmation de livraison, un claim, une réconciliation, un retry ou une
connexion à `127.0.0.1:8444`.

Les interdictions restent explicites :

```text
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
J9_PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
PROVIDER_DERIVED_REAL_DELIVERY_AUTHORIZED=NO
REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
POLLING_OR_SCHEDULING_AUTHORIZED=NO
WO036_RESUME_AUTHORIZED_BY_WO037=NO
INT001_PULL_REQUEST_AUTHORIZED_BY_WO037=NO
PUSH_OR_MERGE_AUTHORIZED_BY_WO037=NO
```

Aucun appel SofaScore, aucun receiver réel même local, aucune donnée dérivée fournisseur, aucun
payload J7 réel, aucun certificat, aucune clé privée, aucun secret, cookie, jeton ou ACK brut ne
doit intervenir dans la qualification ou entrer dans Git. Les statuts `EXPERIMENTAL`,
`LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et `NO_CRITICAL_DEPENDENCY` restent inchangés.

## 5. Implémentation attendue

Le changement doit rester minimal et localisé à la sélection de la politique `Referrer-Policy`.
Le sous-arbre J7 doit être reconnu par un chemin d’application canonique, après retrait vérifié du
`contextPath`, sans accepter un préfixe étranger ou une route seulement adjacente. Les protections
existantes restent appliquées : cache strict, CSP, `frame-ancestors 'none'`,
`X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` et `X-Robots-Tag`.

La frontière `J7DeliveryLocalRequestBoundaryInterceptor` ne doit pas être élargie. Aucun changement
de contrôleur, service applicatif, ledger, transport, configuration receiver, endpoint, schéma SQL
ou migration n’est attendu. Si la correction exige l’un de ces changements, WO-037 s’arrête et le
besoin est soumis au propriétaire dans un Work Order séparé.

## 6. Qualifications obligatoires

### 6.1 Tests unitaires et web

- prouver `same-origin` sur la liste J7, l’aperçu d’un export et chacune des routes de livraison
  situées sous le sous-arbre J7 ;
- prouver `no-referrer` sur les routes générales, J3, J4, J5, benchmark et sur des chemins
  adjacents, préfixés ou hostiles qui ne sont pas le sous-arbre J7 ;
- couvrir un `contextPath` non vide afin de valider le calcul du chemin d’application ;
- préserver les en-têtes no-store, CSP, anti-frame, anti-MIME et anti-indexation existants ;
- prouver que le Host et l’Origin loopback uniques et exacts sont acceptés par la frontière ;
- prouver que `Origin: null`, les origines et Hosts hostiles ou dupliqués, les valeurs absentes
  interdites et tous les en-têtes forwarded restent refusés avant le contrôleur ;
- vérifier que le filtre ne transforme ni une erreur ni un refus en succès et que les handlers
  étrangers au contrôleur de livraison restent hors de la frontière dédiée ;
- maintenir les tests du formulaire, du jeton local consommable et de la préparation de la
  confirmation, sans appeler `delivery/execute`.

### 6.2 Qualification dans un navigateur réel

La qualification autorisée utilise une application Local Lab réelle, une base synthétique isolée
et un navigateur au contexte neuf sur `http://127.0.0.1:8087`. Elle s’arrête après la préparation
de livraison et doit établir au minimum :

1. la page d’aperçu J7 répond avec `Referrer-Policy: same-origin` et les autres en-têtes de sécurité
   attendus ;
2. le formulaire `delivery/prepare` est soumis par le navigateur avec un Host exact, un Origin
   unique égal à `http://127.0.0.1:8087` et aucun en-tête forwarded ;
3. la requête franchit la frontière, atteint le contrôleur et produit la vue de confirmation sans
   `403` ;
4. aucune action `delivery/execute` n’est déclenchée, aucun claim n’est créé et aucun transport
   receiver n’est construit ou exécuté ;
5. aucune trace, HAR, vidéo, capture, téléchargement, `storageState`, cookie, jeton ou corps de
   formulaire n’est conservé dans les preuves ou dans Git ;
6. l’application, le navigateur de qualification et la base synthétique sont arrêtés et nettoyés
   avec zéro listener, worker ou processus attribuable restant.

La preuve versionnée est limitée aux commits, versions, statuts, compteurs et classifications
expurgées. Toute divergence vers `Origin: null`, tout `403`, toute tentative réseau receiver ou
toute impossibilité d’attribuer et nettoyer les ressources arrête la qualification.

### 6.3 Vérifications de dépôt

- `mvnw.cmd clean verify` ;
- `mvnw.cmd -Pintegration-tests verify` ;
- qualifications ciblées du filtre, de la frontière et du contrôleur J7 ;
- `docker compose --env-file .env config` si la CLI Docker est disponible ;
- contrôle UTF-8, secrets, données personnelles et `git diff --check` ;
- vérification de `server.address=127.0.0.1` et des flags bloquants ;
- vérification qu’aucun test standard ou d’intégration ne réalise d’appel fournisseur ou receiver
  réel.

## 7. Critères de sortie et relation avec WO-036

Un résultat `PASS_LOCAL_FAIL_CLOSED` exige simultanément :

- la matrice de tests unitaires et web entièrement verte ;
- la soumission native loopback réussie avec l’Origin exact, sans accepter une origine opaque ;
- zéro appel receiver, fournisseur ou distant ;
- zéro tentative durable et zéro claim de livraison ;
- un cleanup exact et aucun artefact sensible conservé ;
- un rapport autonome expurgé et son SHA-256 ;
- les vérifications Maven, intégration, UTF-8, secrets, loopback et flags bloquants réussies.

Le commit qualifié et le rapport seront soumis à une revue propriétaire distincte. WO-037 restera
actif jusqu’à cette validation explicite et ne sera déplacé vers `completed` que si le propriétaire
l’autorise.

Même après validation de WO-037, WO-036 reste `STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION`.
Sa reprise exige une nouvelle décision propriétaire, un nouveau manifeste lié au nouveau commit et
au nouveau JAR, puis une nouvelle campagne depuis son début. Le manifeste gelé de WO-036, son
rapport d’arrêt et les preuves antérieures ne sont pas modifiés.

```text
WO037_OWNER_REVIEW_REQUIRED=YES
WO037_WORK_ORDER_MOVE_TO_COMPLETED=NO
WO036_STATUS=STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_RESUME_AFTER_WO037_VALIDATION=REQUIRES_SEPARATE_OWNER_DECISION
WO036_WORK_ORDER_MOVE_TO_COMPLETED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```
