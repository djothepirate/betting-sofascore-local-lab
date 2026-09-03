# Runbook J9 — Qualification offline et préparation du sender local optionnel v1.0

## 1. Objet et limite d’emploi

Ce runbook conserve la qualification du socle fail-closed v1.0 réalisée sous WO-027 et documente
la préparation runtime de WO-035. Il ne permet aucune livraison de données dérivées. Le premier
échange synthétique entre les deux applications relève de WO-036 ; après l’arrêt de son premier
essai avant receiver et la validation de WO-037, sa reprise a été autorisée par une décision
propriétaire distincte et exige un run neuf ainsi qu’un nouveau manifeste.

```text
RUNBOOK_SCOPE=WO027_OFFLINE_LOOPBACK_WO035_RUNTIME_AND_WO037_BROWSER_BOUNDARY
CONTRACT_VERSION=1.0
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
WO035_LOCAL_RECEIVER_ORIGIN=https://127.0.0.1:8444
WO035_NETWORK_EXECUTION_AUTHORIZED=NO
WO036_WINDOWS_WINDOWS_E2E_STATUS=RESUME_AUTHORIZED_PENDING_FRESH_MANIFEST_AND_PREFLIGHT
WO036_FIRST_ATTEMPT_RESULT=STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION
WO037_BROWSER_ORIGIN_BOUNDARY_STATUS=VALIDATED
WO037_BROWSER_ORIGIN_BOUNDARY_RESULT=PASS_LOCAL_FAIL_CLOSED
WO037_OWNER_REVIEW_DECISION=VALIDATE
WO036_RESUME_AFTER_WO037_VALIDATION=AUTHORIZED_BY_SEPARATE_OWNER_DECISION
WO036_FRESH_MANIFEST_REQUIRED=YES
LOCAL_SYNTHETIC_RECEIVER_LOOPBACK_AUTHORIZED=YES
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
LIVE_DELIVERY_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

Avec l’état actuel, toute tentative `PROVIDER_DERIVED` doit être refusée avant création du client
et avant socket. L’origine locale exacte est versionnée, mais WO-035 n’autorise pas à la contacter.
Ne pas ajouter une autre URI, un fichier de clé, un proxy, une exception de confiance ou un flag de
contournement pour « essayer » le parcours.

## 2. Contrats de référence

- [Architecture du push local v1.0](../architecture/J9-OPTIONAL-LOCAL-PUSH.md) ;
- [Modèle de menace](../security/J9-OPTIONAL-LOCAL-PUSH-THREAT-MODEL.md) ;
- [Schéma ACK v1](../../src/main/resources/schemas/j7-delivery-ack-v1.schema.json) ;
- [Schéma export J7](../../src/main/resources/schemas/j7-canonical-event-export-v1.schema.json) ;
- [Runbook export J7](J7-CANONICAL-EVENT-EXPORT.md) ;
- [Work Order WO-027](../work_orders/completed/WO-SS-20260901-027-optional-local-push-implementation.md) ;
- [Work Order WO-035](../work_orders/completed/WO-SS-20260902-035-j9-real-j7-delivery-sender.md).

En cas de divergence, ADR-SS-003 et le Work Order bornent l’autorisation ; le contrat v1.0 borne le
format. Une qualification ne doit pas assouplir un contrôle pour devenir verte.

## 3. Préconditions de poste et de dépôt

1. utiliser Java 25 LTS et Maven Wrapper ;
2. pour WO-035, travailler uniquement dans le worktree
   `codex/j9-wo035-real-j7-delivery-sender`; le worktree WO-027 reste gelé ;
3. confirmer qu’aucune modification concurrente Eclipse ne vise ce worktree ;
4. confirmer que `.env` reste ignoré et qu’aucun secret n’est dans Git ;
5. ne démarrer aucune campagne Playwright ;
6. ne configurer aucune cible distante ;
7. conserver l’application liée à `127.0.0.1` ;
8. conserver les flags fournisseur et livraison réelle désactivés ;
9. employer uniquement des exports et certificats synthétiques dans les tests loopback ; aucun
   test WO-035 ne consulte le magasin Windows réel ;
10. vérifier que le port du receiver synthétique est attribué par le système et non fixé ;
11. démarrer la JVM avec les trois arguments exacts suivants :
    `-Djdk.httpclient.disableRetryConnect=true`,
    `-Djdk.httpclient.redirects.retrylimit=1` et
    `-Djdk.httpclient.enableAllMethodRetry=false`.

Les trois valeurs sont complémentaires et doivent exister avant toute initialisation du
`HttpClient`. Maven les injecte automatiquement pour `test`, Failsafe et `spring-boot:run`. Dans
Eclipse, les inscrire dans **Run Configurations → Arguments → VM arguments**. Pour un futur
`java -jar`, les placer avant `-jar`. Ne jamais tenter de les ajouter dynamiquement après le
démarrage : la porte conservera l’observation initiale et refusera le sender.

Photographie minimale avant qualification :

```powershell
git status --short --branch
git diff --check
docker compose --env-file .env config
```

La commande Compose est une validation de configuration locale. Elle n’autorise ni démarrage de
receiver réel, ni accès fournisseur.

## 4. Données synthétiques autorisées

Le corpus de qualification doit être fabriqué dans le test et ne contenir aucun provider ID,
cookie, token ou extrait de payload réel. Il doit comprendre au minimum :

- un export J7 synthétique valide, `HUMAN_VALIDATED`, entre 1 octet et 5 Mio ;
- son `exportId` UUID canonique ;
- le SHA-256 des octets exacts du fichier ;
- le `dataSha256` synthétique déjà porté par le manifeste ;
- la clé `j7:<exportId>:sha256:<fileSha256>` calculée par le code ;
- un ACK `IMPORTED` strict et corrélé ;
- un ACK `DUPLICATE` strict et corrélé ;
- des variantes invalides limitées aux champs nécessaires au test.

Exemple d’identité purement synthétique :

```text
exportId=11111111-1111-4111-8111-111111111111
fileSha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
idempotencyKey=j7:11111111-1111-4111-8111-111111111111:sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
```

Ne jamais réutiliser dans la documentation un identifiant d’événement, d’équipe, de tournoi, de
snapshot ou de session observé auprès du fournisseur.

## 5. Qualification offline obligatoire

La qualification offline ne crée aucun serveur ni client réseau. Elle doit couvrir les contrôles
dans cet ordre :

1. **Configuration par défaut**
   - kill switch désactivé ;
   - mode `DISABLED`, origine receiver vide, qualifications sender/receiver `NOT_QUALIFIED` ;
   - permission `NOT_EVIDENCED` ;
   - URI réelle absente ;
   - aucun certificat réel sélectionné ;
   - concurrence `1` et retry automatique `0` ;
   - les trois arguments JVM anti-retry figurent dans les arguments d’entrée du processus ;
   - toute divergence individuelle refuse avant `sendAsync` et sans interaction client.
2. **Éligibilité J7**
   - accepter uniquement `HUMAN_VALIDATED` ;
   - refuser `COHERENCE_CHECKED`, `REJECTED` et toute valeur inconnue ;
   - ne jamais modifier la décision J7 ;
   - classer les cinq sources vérifiées : toutes synthétiques donnent `SYNTHETIC_ONLY`, toutes
     fournisseur donnent `PROVIDER_DERIVED`, tout mélange, absence incohérente ou valeur inconnue
     donne `MIXED_OR_UNKNOWN` et ferme le parcours.
3. **Confinement du fichier**
   - résolution canonique sous la racine autorisée ;
   - refus d’une traversée, d’un lien, d’un fichier absent ou changé ;
   - taille comprise entre `1` et `5 242 880` octets.
4. **Hash et identité**
   - SHA-256 des octets exacts ;
   - UUID canonique minuscule ;
   - clé idempotente de 111 octets ;
   - concordance manifeste, en-têtes, hashes et clé.
5. **Porte de permission**
   - refus avant construction du transport ;
   - aucune ligne de livraison créée, ledger inchangé (`NOT_ATTEMPTED` conceptuel) ;
   - aucune résolution DNS, aucun socket et aucun appel Playwright.
6. **Schéma ACK**
   - `IMPORTED` et `DUPLICATE` seuls ;
   - propriétés inconnues, version inconnue, UUID/hash/clé/date invalides refusés ;
   - corrélation complète avec la tentative.
7. **Machine d’états**
   - seules les transitions du contrat sont admises ;
   - les transitions illégales échouent ;
   - `HUMAN_VALIDATED` reste indépendant.

Résultat attendu :

```text
OFFLINE_PROVIDER_CALLS=0
OFFLINE_REAL_RECEIVER_CALLS=0
PERMISSION_REFUSAL_BEFORE_SOCKET=PASS
J7_VALIDATION_STATE_UNCHANGED=PASS
```

## 6. Qualification loopback synthétique

### 6.1 Enveloppe autorisée

Le receiver synthétique doit :

- exister uniquement dans le code de test ;
- écouter sur `127.0.0.1` et sur le port éphémère `0` ;
- utiliser des certificats et autorités synthétiques créés pour la durée du test ;
- ne lire aucun `.env`, certificat utilisateur réel ou magasin de production ;
- compter les effets d’import pour prouver l’idempotence ;
- fermer connexions, executor, serveur et fichiers temporaires en succès comme en échec ;
- ne jamais contacter un autre hôte.

Le profil synthétique est une preuve de comportement du code, pas un profil mTLS réel. Les valeurs
réelles d’autorité, EKU, révocation, rotation et récupération restent `NOT_DEFINED`.

### 6.2 Série nominale

1. créer un export synthétique valide et calculer son hash ;
2. démarrer le receiver synthétique sur loopback ;
3. persister l’intention et passer la tentative à `IN_FLIGHT` avant le socket ;
4. envoyer un `POST` unique avec les en-têtes v1.0 et les octets exacts ;
5. faire répondre HTTP `201` avec un ACK `IMPORTED` strict ;
6. vérifier `DELIVERED`, les corrélations et un seul effet receiver ;
7. vérifier que J7 reste `HUMAN_VALIDATED` ;
8. fermer le harness et vérifier l’absence de listener résiduel.

### 6.3 Série idempotente

1. faire appliquer un premier effet synthétique, puis fermer la connexion avant l’ACK ;
2. vérifier `UNKNOWN_RECONCILIATION_REQUIRED`, un seul POST et un seul effet ;
3. réutiliser exactement le même fichier et la même clé dans une nouvelle action manuelle,
   sans boucle automatique ;
4. faire répondre HTTP `200` avec un ACK `DUPLICATE` strict ;
5. vérifier `DUPLICATE_CONFIRMED` ;
6. vérifier que le compteur d’effet receiver reste égal à `1` et qu’aucune clé alternative n’a
   été créée.

### 6.4 Série de conflits et refus

Vérifier séparément :

| Cas | Résultat attendu |
|---|---|
| `exportId` connu localement, hash divergent | Refus avant claim et socket ; aucune ligne (`NOT_ATTEMPTED` conceptuel) |
| HTTP `409` reçu pour une requête admise localement | `REJECTED_TERMINAL`, zéro retry, jamais un doublon positif |
| Fichier > `5 242 880` octets | Refus local, aucun socket, aucune ligne (`NOT_ATTEMPTED` conceptuel) |
| Statut autre que `HUMAN_VALIDATED` | Refus local, aucun socket, aucune ligne (`NOT_ATTEMPTED` conceptuel) |
| Hash recalculé divergent | Refus local, aucun socket, aucune ligne (`NOT_ATTEMPTED` conceptuel) |
| HTTP `401`, `403`, `413`, `415`, `422`, `429` | `REJECTED_TERMINAL`, zéro retry |
| HTTP `3xx` | `UNKNOWN_RECONCILIATION_REQUIRED`, redirection non suivie |
| HTTP `5xx` | `UNKNOWN_RECONCILIATION_REQUIRED`, zéro retry |
| HTTP `204` ou `2xx` sans ACK valide | `UNKNOWN_RECONCILIATION_REQUIRED` |

### 6.5 Série ACK hostile ou incohérent

Vérifier au minimum :

- ACK vide ;
- ACK avec BOM UTF-8/UTF-16, NUL, encodage UTF-16 ou UTF-8 malformé ;
- JSON malformé ;
- propriété inconnue ;
- mauvaise version ;
- `status` non permis ;
- `exportId`, `fileSha256`, `dataSha256` ou clé non corrélés ;
- code HTTP incompatible avec `IMPORTED` ou `DUPLICATE` ;
- `remoteImportId` invalide ou `receivedAt` non canonique, décalé ou différent de
  `Instant.toString()` ;
- corps supérieur à `16 384` octets ;
- `Content-Encoding` autre que `identity` ;
- media type inattendu.

Pour un candidat positif `2xx`, tous ces cas donnent `UNKNOWN_RECONCILIATION_REQUIRED` lorsqu’une
tentative était `IN_FLIGHT`. Un corps supérieur à `16 384` octets ou des métadonnées de réponse
hostiles donnent également `UNKNOWN_RECONCILIATION_REQUIRED` quel que soit le statut annoncé, sans
HTTP fiable persisté. Une réponse `4xx` normalement reçue, aux métadonnées valides et au corps
borné, reste `REJECTED_TERMINAL`. Aucun corps hostile n’est journalisé.

### 6.6 Série mTLS

Le harness synthétique doit couvrir :

- certificat serveur approuvé et client attendu : succès ;
- client absent : échec TLS ;
- client signé par une autre autorité synthétique : échec TLS ;
- serveur signé par une autre autorité : échec TLS ;
- nom serveur incohérent : échec TLS ;
- certificat expiré ou non encore valide : échec TLS ;
- sélection client absente ou ambiguë : refus local ;
- aucun mode trust-all ou hostname-verification-off.

Chaque échec après passage à `IN_FLIGHT` produit `UNKNOWN_RECONCILIATION_REQUIRED`, sans nouvelle
tentative. Les certificats, clés et répertoires synthétiques sont détruits par le test.

### 6.7 Série d’ambiguïté et de nettoyage

Simuler :

- timeout avant réponse ;
- fermeture de connexion après lecture/import mais avant ACK ;
- ACK partiel ;
- arrêt du receiver ;
- exception du client ;
- annulation de la qualification.

Pour chaque cas, vérifier :

```text
AUTOMATIC_RETRY_ATTEMPTS=0
DELIVERY_STATE=UNKNOWN_RECONCILIATION_REQUIRED
J7_VALIDATION_STATE=HUMAN_VALIDATED_UNCHANGED
PROVIDER_CALLS=0
RESIDUAL_LOOPBACK_LISTENERS=0
RESIDUAL_TEST_CERTIFICATES_OR_KEYS=0
```

Simuler également un arrêt entre le claim et la complétion : avant `30 s` réelles mesurées par
PostgreSQL depuis le `created_at` immuable de la tentative, toute réconciliation doit être refusée ;
à partir de `30 s`, seule l'identité exacte de livraison, le même ordinal et le même `startedAt`
permettent d'inscrire un résultat append-only
`OPERATOR_RECONCILED_STALE_IN_FLIGHT` et de passer à
`UNKNOWN_RECONCILIATION_REQUIRED`. Cette opération ne crée aucun socket et ne relance jamais le
POST. Une reprise ultérieure reste une nouvelle action manuelle avec les mêmes octets et la même
clé.

## 7. Commandes standard de vérification

Depuis la racine du worktree WO-027 :

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd -Pintegration-tests verify
docker compose --env-file .env config
git diff --check
```

Ces commandes Maven utilisent les arguments JVM anti-retry définis dans `pom.xml`. Pour une
configuration Eclipse directe, reprendre exactement les trois valeurs de la section 3 avant de
lancer une qualification ; leur absence doit produire `AUTOMATIC_REPLAY_BLOCKED`, jamais un
assouplissement.

Ne pas ajouter de profil live, de cible réelle ou de secret pour rendre ces commandes vertes. Si
une commande standard nécessite une ressource non disponible, consigner l’environnement et arrêter
la qualification concernée ; ne pas contourner un contrôle.

## 8. Contrôles documentaires et de sécurité

Après les tests :

1. analyser le diff et les fichiers non suivis ;
2. rechercher clé privée, mot de passe, token, cookie, URI réelle, nom d’hôte, chemin utilisateur,
   payload brut et identifiant de session ;
3. confirmer que les schémas JSON sont syntaxiquement valides ;
4. vérifier les liens Markdown locaux ;
5. confirmer `server.address=127.0.0.1` ;
6. confirmer les flags fournisseur et livraison bloqués par défaut ;
7. confirmer l’absence de listener et de processus test résiduel ;
8. confirmer l’absence de certificat, clé, truststore, keystore, HAR, trace, vidéo, capture ou
   téléchargement ajouté à Git ;
9. confirmer qu’aucun payload complet ni ACK brut ne figure dans les rapports ;
10. confirmer qu’aucun appel fournisseur ou receiver réel n’a été réalisé.

## 9. Ledger et preuves autorisées

Une preuve de qualification peut conserver uniquement :

- version de contrat ;
- identifiant synthétique de tentative ;
- état initial et final ;
- catégorie de résultat et code HTTP synthétique éventuel ;
- SHA-256 des fixtures synthétiques ;
- nombre d’effets receiver ;
- durées et horodatages UTC ;
- résultat des contrôles de cleanup ;
- commandes exécutées et synthèse de tests.

Elle ne conserve jamais : payload, ACK brut, corps d’erreur, clé privée, mot de passe, certificat
privé, chemin sensible, cookie, token ou état de session.

## 10. Procédure d’arrêt

Arrêter immédiatement la qualification si :

- une cible n’est pas loopback ;
- une URI réelle apparaît dans la configuration ou les logs ;
- un certificat ou une clé non synthétique est demandé ;
- un appel fournisseur, Playwright ou Betting Project réel est possible ;
- un retry automatique ou une redirection est observé ;
- le fichier J7 ou son statut est modifié ;
- le payload ou un secret est journalisé ;
- un listener ou processus ne peut pas être nettoyé ;
- la limite de 5 Mio ou de 16 KiB n’est pas contrôlée avant allocation/lecture non bornée ;
- une transition de ledger hors contrat est possible.

Après arrêt : fermer le harness, supprimer seulement les artefacts synthétiques exactement possédés,
vérifier l’absence de listener, conserver des métadonnées minimisées et classer la tentative sans
relance.

## 11. Reprise manuelle d’un résultat ambigu

WO-027 qualifie seulement la sémantique suivante :

1. aucun retry automatique ;
2. état `UNKNOWN_RECONCILIATION_REQUIRED` ;
3. vérification humaine du receiver avant toute reprise ;
4. nouvelle action explicite ;
5. mêmes octets et même clé ;
6. ACK `DUPLICATE` attendu si l’import avait déjà produit son effet.

Sous l’autorisation actuelle, cette séquence n’est exécutée qu’avec le receiver synthétique. Une
reprise réelle requerrait la porte réseau distincte applicable.

## 12. Portes avant première livraison réelle

Ne pas transformer les résultats loopback en go réel. Une première livraison exige cumulativement :

```text
J9_OFFICIAL_PERMISSION_STATUS=EVIDENCED_COMPATIBLE
ADR_SS_003_REVIEW_STATUS=SATISFIED_FOR_REAL_DELIVERY
BETTING_PROJECT_RECEIVER_WORK_ORDER=VALIDATED
BETTING_PROJECT_RECEIVER_IMPLEMENTATION=QUALIFIED
REAL_IMPORT_ENDPOINT_URI=EXPLICITLY_AUTHORIZED
REAL_MTLS_PROFILE=QUALIFIED
CLIENT_PRIVATE_KEY_NATIVE_POLICY=QUALIFIED_NON_EXPORTABLE
RETENTION_PURGE_RESTORE_RECEIVER=QUALIFIED
REAL_DELIVERY_OWNER_DECISION=GRANT
PROVIDER_ACQUISITION_TRIGGERED_BY_DELIVERY=NO
PRODUCTION_AUTHORIZED=SEPARATE_DECISION_REQUIRED
```

Si une seule valeur manque, le sender réel reste désactivé et le repli est
`KEEP_LOCAL_NO_INTEGRATION`.

## 13. Bloc de résultat de qualification

Le rapport WO-027 doit pouvoir produire, sans valeur présumée :

```text
WO027_CONTRACT_VERSION=1.0
WO027_OFFLINE_RESULT=<PASS|FAIL|NOT_EXECUTED>
WO027_LOOPBACK_RESULT=<PASS|FAIL|NOT_EXECUTED>
WO027_MTLS_SYNTHETIC_RESULT=<PASS|FAIL|NOT_EXECUTED>
WO027_IDEMPOTENCY_RESULT=<PASS|FAIL|NOT_EXECUTED>
WO027_ACK_BOUND_16_KIB_RESULT=<PASS|FAIL|NOT_EXECUTED>
WO027_J7_STATE_SEPARATION_RESULT=<PASS|FAIL|NOT_EXECUTED>
WO027_PROVIDER_CALLS=0
WO027_REAL_RECEIVER_CALLS=0
WO027_RESIDUAL_LISTENERS=<count>
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
REAL_DELIVERY_AUTHORIZED=NO
OWNER_REVIEW_REQUIRED=YES
```

## 14. Profil runtime WO-035 préparé, non exécuté

### 14.1 Valeurs par défaut obligatoires

La livraison est inactive après un démarrage normal :

```text
optional-integration.enabled=false
optional-integration.execution-mode=DISABLED
optional-integration.receiver-origin=
optional-integration.receiver-qualification=NOT_QUALIFIED
optional-integration.sender-qualification=NOT_QUALIFIED
optional-integration.remote-delivery-authorized=false
optional-integration.official-permission-status=NOT_EVIDENCED
optional-integration.automatic-retry-enabled=false
optional-integration.mtls.client-certificate-sha256=
```

Sous ces valeurs, la consultation HTML reste locale et n’ouvre ni magasin de certificats, ni
client HTTP, ni socket. La génération, le contrôle de cohérence et la transition
`HUMAN_VALIDATED` ne préparent et n’exécutent aucune livraison.

### 14.2 Geste opérateur à usage unique

Pour un export `HUMAN_VALIDATED`, la livraison est une action distincte en deux temps :

1. préparer une demande liée à l’événement, l’export, son `fileSha256` vérifié et au prochain
   ordinal de tentative calculé depuis le ledger ;
2. recopier exactement la phrase présentée ;
3. cocher l’accusé explicite ;
4. consommer la demande une seule fois dans sa fenêtre de cinq minutes ;
5. seulement après cette consommation, construire le transport puis persister le claim avant le
   POST.

La phrase normative est :

```text
LIVRER J7 <exportId> SHA256 <fileSha256>
```

L’ordinal n’est pas ajouté à cette phrase publique. À l’exécution, le ledger est d’abord relu : une
demande expirée, déjà consommée, liée à une autre session, à d’autres octets ou à un ordinal déjà
différent est brûlée et refusée avant factory. Le même ordinal est ensuite transmis jusqu’au claim
PostgreSQL et revalidé sous le verrou global, dans la transaction, avant insert de tentative,
passage `IN_FLIGHT` et ouverture de socket. Une évolution concurrente intercalée après la première
lecture est donc elle aussi refusée, puis le transport possédé est fermé. Ainsi, une demande
préparée avant qu’une autre session produise `UNKNOWN_RECONCILIATION_REQUIRED` ne peut pas
autoriser la tentative suivante ; une nouvelle attestation et une nouvelle préparation sont
obligatoires.
La réconciliation d’un `IN_FLIGHT` périmé emploie une demande séparée :

```text
RECONCILIER J7 <exportId> TENTATIVE <ordinal> SHA256 <fileSha256>
```

Elle ne contacte jamais le receiver et ne relance jamais le POST. Les états du ledger restent
séparés de `HUMAN_VALIDATED` : `NOT_ATTEMPTED`, `IN_FLIGHT`, `DELIVERED`,
`DUPLICATE_CONFIRMED`, `REJECTED_TERMINAL` et `UNKNOWN_RECONCILIATION_REQUIRED`.

### 14.3 Matrice de provenance

| Classe calculée | Mode runtime exigé | État sous WO-035 |
|---|---|---|
| `SYNTHETIC_ONLY` | `SYNTHETIC_LOOPBACK` | Code préparé ; échange réel reporté à WO-036 |
| `PROVIDER_DERIVED` | `PROVIDER_DERIVED` | Bloqué par `NOT_EVIDENCED`, autorisation distante absente et `PROVIDER_OWNER_GO_REQUIRED` |
| `MIXED_OR_UNKNOWN` | Aucun | Refus `PAYLOAD_PROVENANCE_NOT_ELIGIBLE` |

Le mode synthétique n’accepte que l’origine exacte `https://127.0.0.1:8444`, les qualifications
receiver et sender à `PASS`, une empreinte certificat SHA-256 exacte et
`remote-delivery-authorized=false`. Cette matrice est une spécification pour WO-036, pas un ordre
de lancer les applications sous WO-035.

Avant toute qualification, vérifier également que la voie applicative synthétique transmet
explicitement la classe attendue `SYNTHETIC_ONLY`. Un export classé `PROVIDER_DERIVED` ou
`MIXED_OR_UNKNOWN` doit être refusé avant claim, création du transport et socket, y compris dans un
test qui appelle directement le service sans passer par l’UI.

### 14.4 Frontière navigateur locale

Pour toute action résolue vers `J7DeliveryController`, contrôler :

```text
Host=127.0.0.1:8087 exactement une fois
Origin=absent ou http://127.0.0.1:8087 exactement une fois
Origin=null=REFUSE
Forwarded=absent
X-Forwarded-Host=absent
X-Forwarded-Proto=absent
Referrer-Policy=same-origin sur /events/{canonicalEventId}/exports/**
Referrer-Policy=no-referrer sur toute route étrangère à ce sous-arbre
Content-Security-Policy contient frame-ancestors 'none'
X-Frame-Options=DENY
```

Le contrôle doit reposer sur le `HandlerMethod` résolu et non sur un préfixe d’URI. Tester les
doublons, les valeurs hostiles et une URI brute encodée tout en conservant le même handler. Un
refus intervient avant le contrôleur, sans jeton consommé, claim ou transport.

La qualification native WO-037 part d’une page d’aperçu J7 réellement servie par un
Spring/Tomcat lié à `http://127.0.0.1:8087`, avec des services synthétiques en mémoire et sans base
de données. Elle vérifie l’en-tête `same-origin`, puis soumet uniquement `delivery/prepare`. La
preuve doit constater un Host exact, un Origin unique égal à `http://127.0.0.1:8087`, l’absence
d’en-têtes forwarded et l’arrivée au contrôleur sans `403`.

Ne pas soumettre `delivery/execute` ni une réconciliation, ne pas produire de claim et ne pas
démarrer de receiver. Un contexte navigateur neuf doit être fermé sans conserver HAR, trace,
vidéo, capture, téléchargement, `storageState`, cookie, jeton ou corps de formulaire. Une page
d’origine opaque doit continuer à produire `Origin: null` et être refusée `403` avant contrôleur.

### 14.5 Profil mTLS Windows

Le transport runtime, construit paresseusement après toutes les portes, applique :

```text
CLIENT_CERTIFICATE_STORE=Windows-MY
CLIENT_CERTIFICATE_PROVIDER=SunMSCAPI
CLIENT_CERTIFICATE_SELECTOR=EXACT_SHA256
CLIENT_CERTIFICATE_EKU=clientAuth
CLIENT_CERTIFICATE_KEY_USAGE=digitalSignature
SERVER_TRUST_STORE=Windows-ROOT
SERVER_TRUST_PROVIDER=SunMSCAPI
TLS_PROTOCOLS=TLSv1.3,TLSv1.2
REDIRECT_POLICY=NEVER
AUTOMATIC_RETRY=0
```

La clé doit exister et rester opaque pour le provider Java. L’empreinte, la clé et les certificats
ne sont jamais inscrits dans Git ou dans un rapport. `Windows-ROOT` est chargé explicitement : le
truststore JVM par défaut, un trust-all et la désactivation de la vérification de nom ne sont pas
des fallbacks. La non-exportabilité native et le provisionnement de la PKI doivent être qualifiés
séparément pendant WO-036.

### 14.6 Mono-exécution, cleanup et réconciliation

Pour chaque tentative, vérifier conjointement :

1. l’instance de transport refuse une seconde invocation de `execute` ;
2. le body publisher refuse une seconde souscription ;
3. les propriétés JVM anti-retry sont attestées avant construction puis avant envoi ;
4. la gate partagée passe de `IDLE` à `ACTIVE` avant tout travail et reste `ACTIVE` jusqu’après la
   fin de `transport.close()` ;
5. une seconde livraison et toute réconciliation sont refusées pendant `ACTIVE` avec
   `DELIVERY_IN_PROGRESS` ;
6. une fermeture réussie rend `IDLE` ;
7. une fermeture en erreur rend `POISONED`, même si une autre erreur est primaire, puis toute
   livraison ou réconciliation reste refusée avec `DELIVERY_RUNTIME_POISONED` jusqu’au
   redémarrage.

La réconciliation ne doit ni lire, ni ouvrir, ni revérifier le fichier J7. Préparer puis exécuter
la réconciliation à partir des seules métadonnées d’export et du ledger ; un test où l’accès au
payload échoue doit encore atteindre le contrôle stale/ordinal, sans aucun POST.

### 14.7 Porte vers WO-036

WO-036 est actif. Son premier manifeste et son rapport d’arrêt restent gelés. La qualification
verte de WO-037 a réduit le défaut de frontière navigateur, puis une décision propriétaire
distincte a autorisé la reprise. Avant tout premier POST du nouveau run, il reste obligatoire de
refaire le préflight, reconstruire et enregistrer les exécutables, créer des ressources isolées
neuves et geler un nouveau manifeste lié au commit et aux JAR qualifiés. La série repart ensuite
intégralement depuis son début ; aucun compteur, export, certificat ou état privé du premier essai
n’est réutilisé.

WO-036 reste limité à un export J7 entièrement synthétique, au Local Lab sur
`127.0.0.1:8087`, au receiver sur `127.0.0.1:8444`, à deux bases distinctes et à une PKI locale
hors Git. Il devra encore prouver `201/IMPORTED`, `200/DUPLICATE`, `409`, l’identité byte-à-byte,
l’outbox et le cleanup. La reprise autorise le receiver INT-001 réel uniquement sur le loopback et
avec ce corpus synthétique. Elle n’autorise aucun appel SofaScore, export dérivé fournisseur,
receiver distant, VPS ou production.

Bloc de clôture attendu pour WO-035 :

```text
WO035_RUNTIME_SENDER_RESULT=PASS_LOCAL_FAIL_CLOSED
WO035_IMPLEMENTATION_COMMIT=f5a27887b7db43576eb608d564c245c8cca3a602
WO035_QUALIFICATION_REPORT_SHA256=1028761cbc68154da780d314935880bf17b803c3cf8a35b1386fd29ab28bae76
WO035_PROVIDER_DERIVED_PATH=BLOCKED
WO035_PROVIDER_CALLS=0
WO035_REAL_RECEIVER_CALLS=0
WO035_WO036_OPENED=NO
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
REAL_DELIVERY_AUTHORIZED=NO
WORK_ORDER_STATUS=VALIDATED
OWNER_REVIEW_REQUIRED=NO
OWNER_REVIEW_DECISION=VALIDATE
LOCAL_READINESS_ACKNOWLEDGED=YES
WORK_ORDER_MOVE_TO_COMPLETED=YES
OWNER_DECISION_RECORDED_AT_UTC=2026-09-02T18:57:00Z
```
