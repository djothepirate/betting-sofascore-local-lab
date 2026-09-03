# J9 — Modèle de menace du push local optionnel v1.0

## 1. Décision de sécurité

Ce modèle couvre le socle qualifié par WO-027, le sender runtime fail-closed préparé par WO-035, la
frontière d’origine navigateur qualifiée localement par WO-037 et la preuve partielle WO-036 de deux
échanges synthétiques avec le receiver loopback réel. Il ne qualifie ni la séquence complète
`201/200/409`, ni un déploiement, ni aucune donnée dérivée du fournisseur. La permission officielle
demeure `NOT_EVIDENCED`; par conséquent, toute livraison `PROVIDER_DERIVED` est bloquée avant
création du transport et avant réseau.

```text
SECURITY_MODEL_VERSION=1.0
J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED
WO035_LOCAL_RECEIVER_ORIGIN=https://127.0.0.1:8444
WO035_RUNTIME_NETWORK_AUTHORIZED=NO
WO036_WINDOWS_WINDOWS_E2E_STATUS=STOPPED_AFTER_DUPLICATE_ACK_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_FIRST_ATTEMPT_RESULT=STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION
WO036_SECOND_ATTEMPT_RESULT=STOPPED_AFTER_200_DUPLICATE_BEFORE_409
WO037_BROWSER_ORIGIN_BOUNDARY_STATUS=VALIDATED
WO037_BROWSER_ORIGIN_BOUNDARY_RESULT=PASS_LOCAL_FAIL_CLOSED
WO037_OWNER_REVIEW_DECISION=VALIDATE
WO036_RESUME_AFTER_WO037_VALIDATION=CONSUMED_BY_STOPPED_R2
WO036_RESUME_MANIFEST_STATUS=FROZEN_AND_CONSUMED
LOCAL_SYNTHETIC_RECEIVER_LOOPBACK_AUTHORIZED=NO_PENDING_NEW_OWNER_DECISION
REAL_NETWORK_AUTHORIZED=NO
PROVIDER_NETWORK_AUTHORIZED=NO
MTLS_REQUIRED_FOR_ANY_FUTURE_REAL_TARGET=YES
DEFAULT_FAIL_CLOSED=YES
```

Ce document n’est pas un avis juridique et ne transforme ni une documentation publique, ni une
décision interne en permission d’usage.

## 2. Système modélisé

### 2.1 Composants dans le périmètre

- fichier d’export J7 canonique v1, local, immuable et `HUMAN_VALIDATED` ;
- action opérateur manuelle et distincte ;
- préparation et confirmation exacte à usage unique, liées à la session, à l’export, à son
  `fileSha256` et au prochain ordinal attendu dans le ledger ;
- classification fail-closed des cinq sources J7 en `SYNTHETIC_ONLY`, `PROVIDER_DERIVED` ou
  `MIXED_OR_UNKNOWN` ;
- contrôles locaux d’éligibilité, de taille, de schéma et de hash ;
- calcul de la clé `j7:<exportId>:sha256:<fileSha256>` ;
- sender HTTPS mTLS fail-closed ;
- ledger local de livraison, séparé du statut J7 ;
- parseur et validateur strict de l’ACK v1.0 ;
- receiver synthétique test-only WO-027 sur `127.0.0.1` et port éphémère ;
- factory runtime paresseuse bornée à `https://127.0.0.1:8444`, au certificat `Windows-MY`
  sélectionné par SHA-256 et à la confiance explicite `Windows-ROOT`.

### 2.2 Composants explicitement hors périmètre

- déploiement réel du receiver Betting Project et toute cible non loopback ;
- nouvelle exécution du receiver local `127.0.0.1:8444` tant qu’un correctif runtime distinct et une
  nouvelle reprise de WO-036 ne sont pas autorisés ;
- URI, DNS, certificat ou autorité de production ;
- réseau fournisseur ou acquisition SofaScore ;
- Playwright sur VPS ou poste distant ;
- stockage, rétention, purge et restauration de la copie côté receiver réel ;
- disponibilité, SLO, astreinte ou exploitation de production.

### 2.3 Actifs à protéger

1. intégrité des octets J7 validés et de leur SHA-256 ;
2. indépendance entre `HUMAN_VALIDATED` et l’état de livraison ;
3. unicité de l’effet receiver sous une clé idempotente ;
4. confidentialité de la clé privée client et des métadonnées sensibles ;
5. exactitude et corrélation de l’ACK ;
6. preuve d’audit locale sans payload ni secret ;
7. absence de dépendance critique du Betting Project ;
8. garantie qu’aucune livraison ne déclenche une acquisition fournisseur ;
9. blocage de toute cible réelle tant que la permission reste `NOT_EVIDENCED`.

## 3. Frontières de confiance

```text
Frontière A — stockage local
  export J7 validé | sender et ledger

Frontière B — identité opérateur
  consultation/validation | préparation | confirmation exacte à usage unique | livraison

Frontière C — configuration et magasins Windows
  propriétés non fiables | origine locale exacte | SHA-256 | Windows-MY / Windows-ROOT

Frontière D — transport futur
  sender Windows | TLS 1.3/1.2 et mTLS | receiver Betting Project

Frontière E — réponse distante
  octets ACK non fiables | limite 16 KiB | parseur strict | ledger

Frontière F — tests
  runtime normal | harness synthétique loopback non empaqueté comme cible réelle
```

Tout ce qui vient du fichier, de la configuration, du réseau, du certificat distant ou de l’ACK
est non fiable jusqu’à validation. Un code HTTP `2xx` n’est pas une preuve suffisante.

## 4. Hypothèses bornées

- Le poste Windows et le magasin `CurrentUser\\My` sont sous le contrôle de l’opérateur autorisé.
- Le fichier J7 a été produit par le laboratoire, mais son chemin, sa taille, son contenu et son
  hash sont néanmoins revérifiés au moment de la tentative.
- Le futur receiver peut être compromis, mal configuré, indisponible ou incohérent.
- Une erreur de transport peut survenir après que le receiver a appliqué l’import.
- Les erreurs et ACK peuvent être surdimensionnés, malformés ou mensongers.
- Aucun bénéfice de sécurité n’est déduit de l’adresse loopback pour une future cible réelle.
- Aucune accessibilité ou stabilité observée sous WO-023 n’est extrapolée au transport d’intégration.
- Un emplacement de source absent, une disponibilité inconnue ou une provenance mêlée ne sont
  jamais présumés synthétiques. Un emplacement présent avec l’état métier valide `MISSING` reste
  neutre.
- La présence d’une origine loopback versionnée ne constitue ni un go réseau, ni une confiance
  implicite dans le processus qui écoute sur ce port.

## 5. Menaces, contrôles et preuve attendue

| Menace | Scénario | Contrôle obligatoire | Preuve WO-027/WO-035/WO-036/WO-037 |
|---|---|---|---|
| Livraison sans permission | Une configuration fournisseur est activée alors que la permission est `NOT_EVIDENCED` | Porte de permission, autorisation distante et `PROVIDER_OWNER_GO_REQUIRED` avant création du transport ou socket | Test sans listener : refus local, aucune interaction ledger et aucune ligne créée (`NOT_ATTEMPTED` conceptuel) |
| Confusion de provenance | Un export mixte, incohérent ou fournisseur est présenté comme synthétique | Classification à partir des cinq sources vérifiées ; `MIXED_OR_UNKNOWN` toujours refusé ; mode exact par classe | Matrice entièrement synthétique, entièrement fournisseur, mixte, emplacement absent et valeur inconnue |
| Bypass interne du mode synthétique | Un appelant invoque directement la voie synthétique avec un artefact fournisseur ou mixte | La voie impose `expectedPayloadClass=SYNTHETIC_ONLY` avant claim, factory et socket | Appels directs `PROVIDER_DERIVED` et `MIXED_OR_UNKNOWN` refusés sans ledger ni transport |
| Contournement de la frontière navigateur | Une URI encodée, un Host/Origin dupliqué ou un proxy tente d’atteindre une action locale | Intercepteur fondé sur le `HandlerMethod`; Host/Origin exacts, en-têtes forwarded interdits, `frame-ancestors 'none'` et `DENY` | Handler de livraison avec URI brute hostile, doublons et forwarded refusé avant contrôleur |
| Origine opaque induite par la politique de réponse | Une page J7 servie avec `no-referrer` transforme l’origine d’un `POST` natif en `null`, rendant la frontière exacte incompatible avec son propre formulaire | `same-origin` exclusivement sur `/events/{canonicalEventId}/exports/**` ; `no-referrer` ailleurs ; `Origin: null` toujours refusé | Tests de sélection canonique et chemins adjacents ; Chromium réel avec Origin loopback exact atteignant `delivery/prepare`, puis origine opaque refusée avant contrôleur |
| Rejeu de confirmation | Une phrase copiée, expirée, issue d’une autre session ou préparée avant une tentative concurrente déclenche un POST | Demande bornée à cinq minutes, digest de session, identité événement/export/hash/action, prochain ordinal attendu et consommation atomique à usage unique ; l’ordinal reste caché pour la phrase publique et il est revalidé transactionnellement sous le verrou global au claim | Absence, expiration, seconde consommation, autre session et identités divergentes refusées au précontrôle ; intercalation concurrente refusée avant insert, `IN_FLIGHT` et socket, sans ordinal suivant |
| Mauvais fichier | Fichier non validé, déplacé, remplacé ou lié hors racine | Résolution canonique, confinement, absence de lien, statut `HUMAN_VALIDATED`, taille et hash recalculés | Cas offline de chemin, statut, taille et hash invalides |
| Altération en transit | Corps différent du fichier validé | Hash des octets exacts dans en-tête et clé ; HTTPS ; vérification receiver exigée | Receiver synthétique compare corps, en-tête et clé |
| Rejeu ou double clic | Même export envoyé plusieurs fois | Clé stable, concurrence `1`, receiver idempotent, ACK `DUPLICATE` sans second effet | Effet appliqué puis ACK perdu ; reprise manuelle synthétique, compteur d’effet égal à `1` |
| Arrêt après claim | `IN_FLIGHT` orphelin bloquant ou reprise hasardeuse | Aucun envoi automatique ; réconciliation manuelle de l’ordinal exact après délai minimal `30 s` mesuré par l’horloge PostgreSQL depuis la création DB immuable, vers `UNKNOWN` | Refus immédiat ou identité divergente, attente DB réelle, puis reprise manuelle sous la même clé |
| Collision logique | Même `exportId` associé à un hash différent | Refus local avant claim/socket ; aucune nouvelle clé opportuniste. Un HTTP `409` distant reste terminal | Identité divergente refusée par le ledger sans ligne ; classification contractuelle du `409` testée séparément |
| Résultat ambigu | Timeout ou rupture après import effectif | `UNKNOWN_RECONCILIATION_REQUIRED`, zéro retry, reprise manuelle avec même clé | Timeout post-import simulé, aucun second appel automatique |
| Retry interne du JDK | Le client reprend une connexion ou un échange sans seconde souscription visible du body | Arguments JVM de démarrage exacts `disableRetryConnect=true`, `redirects.retrylimit=1`, `enableAllMethodRetry=false`; attestation avant construction et avant envoi | Arguments visibles dans le processus ; divergence de chaque propriété refusée avant `sendAsync` ; un seul effet receiver |
| Réutilisation du transport | Une même instance est invoquée ou son corps est souscrit deux fois | Garde atomique mono-exécution et body publisher one-shot, en complément des propriétés JDK | Première exécution unique puis seconde invocation refusée avant `sendAsync`; seconde souscription refusée |
| Faux succès | `2xx` avec ACK absent, malformé ou non corrélé | UTF-8 strict sans BOM/NUL/UTF-16, schéma strict, `receivedAt` canonique UTC, corrélation de tous les champs, couple code/statut exact | ACK trop grand, encodage hostile, date non canonique, champ inconnu, hash/ID/statut incohérents |
| Sémantique temporelle du duplicate | Le receiver réemploie conformément au contrat le `receivedAt` durable initial, antérieur à la tentative de répétition, mais le sender applique la borne basse d’un nouvel import et produit un faux résultat inconnu | Distinguer la validation temporelle `IMPORTED` de `DUPLICATE`, conserver la borne haute et toutes les corrélations, puis tester avec deux instants réellement distincts | Reprise WO-036 arrêtée fail-closed après `200/DUPLICATE`; correctif runtime distinct requis |
| ACK ou erreur volumineux | Receiver envoie un corps illimité ou un ACK positif compressé | Limite de toute lecture à `16 384` octets ; ACK positif seulement avec `Content-Encoding` absent/identity | Réponse >16 KiB refusée quel que soit le statut ; ACK positif compressé refusé |
| Redirection/SSRF | Cible renvoie vers une autre origine ou une URI injectée | Seule origine `https://127.0.0.1:8444`, sans chemin/query/fragment/user-info ; redirections jamais suivies ; aucune cible de secours | Toutes les variantes d’origine refusées avant lecture du certificat ; `3xx` classé ambigu et zéro seconde connexion |
| Usurpation du receiver | Certificat serveur faux ou nom incohérent | Confiance chargée explicitement depuis `Windows-ROOT`/`SunMSCAPI`, vérification de nom et TLS 1.2/1.3 ; aucun trust-all | Certificat/nom/autorité synthétique incorrects refusés ; aucune consultation réelle sous WO-035 |
| Usurpation du sender | Client non autorisé contacte le receiver | mTLS et certificat client `Windows-MY` sélectionné par SHA-256 exact, EKU `clientAuth`, usage `digitalSignature` | Zéro/multiple correspondance, certificat périmé, mauvaise EKU ou mauvais usage refusés offline |
| Fuite de clé privée | Clé exportée, passée en fichier ou journalisée | Magasin utilisateur Windows, refus si le provider Java expose un encodage, aucune sérialisation ou copie ; politique native non exportable exigée avant toute cible réelle | Scan Git/logs et test de l'opacité Java ; attestation native encore `NOT_QUALIFIED` |
| Sélection ambiguë de certificat | Plusieurs certificats correspondent automatiquement | Identifiant de certificat exact, unicité et validité exigées, refus en cas d’ambiguïté | Cas zéro, un et plusieurs candidats synthétiques |
| Certificat expiré/révoqué | Identité périmée acceptée | Validité, chaîne et politique de révocation du futur profil ; aucun bypass | Échecs synthétiques bornés ; profil réel reste `NOT_DEFINED` |
| Fuite de données | Payload, ACK brut, corps d’erreur ou chemin sensible dans les logs | Journalisation par codes bornés, aucune donnée complète, exceptions assainies | Scan de logs, diff et preuves |
| Données fournisseur brutes | Un snapshot ou état Playwright est envoyé | Corps limité au fichier J7 canonique approuvé ; aucun champ ajouté | Validation du schéma et absence d’autre type de source de livraison |
| Confusion validation/livraison | Un échec distant modifie `HUMAN_VALIDATED` | Tables/états séparés, aucune transition croisée | Tests d’échec et succès laissant J7 inchangé |
| Couplage fournisseur | Le push déclenche J3/J4/J5/Playwright | Aucune dépendance ou callback d’acquisition dans sender/receiver | Tests sans mock fournisseur et contrôle des appels à zéro |
| Déni de service local | Très gros fichier, nombreuses tentatives ou ACK lent | 5 MiB, concurrence `1`, timeouts finis, 16 KiB ACK, zéro retry | Tests de bornes et de timeout |
| Évasion du harness | Listener de test persistant ou non loopback | WO-027 : `127.0.0.1`, port `0`; WO-037 : `127.0.0.1:8087`; reprise WO-036 : origine exacte `https://127.0.0.1:8444`; fermeture et audit listener | WO-037 ferme son listener sans résidu ; R2 ferme Local Lab A/B, receiver et bases avec résidus possédés à zéro et ports `8087`, `8444`, `5432`, `5433` libérés avant redémarrage séparé du primaire |
| État fantôme | Crash entre intention, socket et résultat | Intention persistée avant socket, `IN_FLIGHT`, classification fail-closed au redémarrage | Reprise locale classant l’ambiguïté sans nouvel envoi |
| Chevauchement cleanup/nouvelle livraison | Le ledger devient terminal avant que le client et ses ressources soient fermés | Gate singleton `IDLE/ACTIVE/POISONED` tenue jusqu’après `transport.close()` | Deuxième livraison refusée pendant un close bloqué ; elle n’atteint ni export, ni claim, ni socket |
| Réconciliation d’un processus vivant | Un `IN_FLIGHT` est classé stale pendant que son transport est encore actif | Livraison et réconciliation acquièrent la même gate singleton | Réconciliation refusée pendant `ACTIVE`, sans accès de mutation au ledger |
| Échec de fermeture | Une ressource native incertaine subsiste après `close()` | Toute erreur de fermeture passe la gate à `POISONED`, même comme erreur supprimée ; redémarrage obligatoire | Livraisons et réconciliations ultérieures refusées jusqu’à une nouvelle instance de processus |
| Fichier absent pendant réconciliation | L’impossibilité de relire le payload empêche de classer un état ambigu ou ouvre une voie de renvoi | Réconciliation exclusivement fondée sur les métadonnées d’export et le ledger | Accès fichier forcé en erreur ; préparation/exécution metadata-only, sans transport |
| Modification du contrat | Receiver tolère silencieusement un champ/version inconnu | Version explicite, JSON `additionalProperties=false`, media type strict | Versions/champs inconnus refusés |

## 6. Analyse par propriété de sécurité

### 6.1 Authenticité

L’authenticité de transport repose sur la chaîne serveur de `Windows-ROOT` chargée explicitement et
sur un certificat client de `Windows-MY` sélectionné par son SHA-256 exact. Le sender exige
`clientAuth`, `digitalSignature`, une période de validité courante et une clé privée Java opaque.
WO-035 ne provisionne toutefois aucune autorité, aucun certificat et aucun mécanisme de révocation.
Aucun certificat synthétique ne peut être promu en certificat opérationnel.

### 6.2 Intégrité

Trois identités doivent concorder :

1. `manifest.exportId` dans le fichier ;
2. `X-J7-Export-Id` et la composante UUID de `Idempotency-Key` ;
3. `exportId` de l’ACK.

De même, le SHA-256 des octets doit concorder avec `X-J7-File-SHA256`, la composante hash de la clé
et `fileSha256` de l’ACK. `manifest.dataSha256`, `X-J7-Data-SHA256` et `dataSha256` de l’ACK doivent
aussi concorder. Toute divergence est un refus ou un résultat ambigu, jamais un succès.

### 6.3 Confidentialité

Le transport réel devra être chiffré, mais TLS ne justifie pas l’envoi de données inutiles. Le corps
reste limité à l’export J7 approuvé. Les logs et le ledger ne dupliquent pas le fichier. Un ACK est
minimisé à sept champs strictement bornés.

### 6.4 Disponibilité et ambiguïté

Le contrat ne promet ni fraîcheur, ni disponibilité continue. La concurrence `1`, les timeouts et
l’absence de retry protègent le poste et le receiver. La garantie ne repose pas uniquement sur le
publisher à usage unique : les trois propriétés JDK
`-Djdk.httpclient.disableRetryConnect=true`,
`-Djdk.httpclient.redirects.retrylimit=1` et
`-Djdk.httpclient.enableAllMethodRetry=false` sont obligatoires dès le démarrage JVM. La première
neutralise la reprise interne de connexion, la deuxième borne le nombre d’échanges à un et la
troisième écrase explicitement toute politique de reprise de méthodes issue de la configuration
JDK. Une absence ou divergence ferme le transport avant réseau. Une indisponibilité ne se
transforme jamais en boucle. L’état ambigu exige une réconciliation humaine et conserve la même
clé.

### 6.5 Traçabilité

Le ledger consigne les identités et hashes nécessaires à l’audit, la version du contrat, les
horodatages et une catégorie de résultat. Il ne conserve ni payload, ni corps d’erreur. L’identité
de l’opérateur et la politique de rétention du ledger devront respecter les règles locales avant
toute utilisation réelle.

## 7. Propriétés mTLS attendues et lacunes

```text
TRANSPORT=HTTPS_MTLS
TLS_PROTOCOLS=TLSv1.3,TLSv1.2
CLIENT_CERTIFICATE_STORE=Windows-MY
CLIENT_CERTIFICATE_PROVIDER=SunMSCAPI
CLIENT_CERTIFICATE_SELECTOR=EXACT_SHA256
CLIENT_CERTIFICATE_EKU=clientAuth
CLIENT_CERTIFICATE_KEY_USAGE=digitalSignature
CLIENT_PRIVATE_KEY_JAVA_ENCODING_EXPOSED=NO
CLIENT_PRIVATE_KEY_NATIVE_NON_EXPORTABLE=REQUIRED_NOT_QUALIFIED
SERVER_TRUST_STORE=Windows-ROOT
SERVER_TRUST_PROVIDER=SunMSCAPI
SERVER_CHAIN_VALIDATION=REQUIRED
SERVER_HOSTNAME_VALIDATION=REQUIRED
TRUST_ALL_OR_HOSTNAME_BYPASS=FORBIDDEN
CERTIFICATE_SELECTION=EXACT_AND_UNAMBIGUOUS
REAL_CA_PROFILE=NOT_DEFINED
REAL_CLIENT_EKU_PROFILE=NOT_DEFINED
REAL_REVOCATION_MODE=NOT_DEFINED
REAL_ROTATION_WINDOW=NOT_DEFINED
REAL_RECOVERY_RUNBOOK=NOT_DEFINED
```

Les valeurs `NOT_DEFINED` sont des portes de la qualification WO-036 et d’une exploitation
ultérieure. Elles ne peuvent pas être remplacées par des choix opportunistes dans le code ou les
variables d’environnement. De même, `getEncoded()==null` prouve seulement que le provider Java ne
livre pas les octets de la clé ; cela ne constitue pas une attestation de la politique native
Windows. Le sender ne consulte ces magasins qu’après les portes runtime et une confirmation valide.

## 8. Risques résiduels

| Risque | Statut WO-027/WO-035/WO-036/WO-037 | Condition de réduction |
|---|---|---|
| Permission ou licence applicable | `NOT_EVIDENCED` | Source ou autorisation versionnée et revue qualifiée |
| Receiver et idempotence transactionnelle | `PARTIAL_BOUNDED_201_AND_200_OBSERVED` | Correctif sender puis nouvelle campagne complète incluant `409` |
| URI et exposition | `PASS_BOUNDED_SYNTHETIC_LOOPBACK_TWO_CALLS` | Aucune cible réelle ; nouvelle autorisation requise pour tout échange |
| Compatibilité navigateur/frontière locale | `PASS_LOCAL_FAIL_CLOSED_VALIDATED` | Frontière qualifiée par WO-037 et exercée par WO-036 |
| Validation temporelle ACK duplicate | `STOPPED_FAIL_CLOSED` | Correctif sender et tests à instants distincts, puis reprise WO-036 séparément autorisée |
| Profil PKI réel | `NOT_DEFINED` | Autorités, EKU, révocation, rotation et récupération approuvées |
| Non-exportabilité native de la clé client | `NOT_QUALIFIED` | Provisionnement Windows réel et preuve native contrôlée |
| Rétention de la copie importée | `NOT_DEFINED` | Politique receiver, purge et restauration qualifiées |
| Volumétrie, disponibilité et exploitation | `NOT_MEASURED` | Campagne dédiée sans fournisseur |
| Exactitude et valeur analytique | `NOT_MEASURED_BY_TRANSPORT` | Revue métier indépendante |

## 9. Conditions d’arrêt et disqualifiants

Arrêter le lot et revenir à `KEEP_LOCAL_NO_INTEGRATION` si une implémentation exige :

- un contournement de permission ou de certificat ;
- une URI réelle codée en dur ou une cible de secours ;
- une redirection, un proxy rotatif, un challenge bypass ou une session persistante ;
- l’export d’une clé privée ;
- l’envoi d’un payload brut ou d’un état Playwright ;
- un retry automatique ou une concurrence supérieure à `1` ;
- la modification de `HUMAN_VALIDATED` à partir d’un résultat distant ;
- un déclenchement fournisseur par le sender ou le receiver ;
- une dépendance critique du Betting Project ;
- un ACK non borné ou une validation permissive ;
- l’impossibilité d’auditer, réconcilier, purger ou désactiver la livraison.

## 10. Portes avant toute cible réelle

Toutes les portes suivantes sont cumulatives :

1. permission officielle ou base d’usage applicable suffisamment établie et versionnée ;
2. revue compétente lorsque nécessaire, sans présomption favorable ;
3. réexamen d’ADR-SS-003 si la preuve change les hypothèses ;
4. Work Order et implémentation du receiver dans le dépôt Betting Project ;
5. contrat et modèle de menace receiver approuvés ;
6. URI, DNS, exposition et politiques egress autorisés ;
7. profil mTLS réel, rotation, révocation et récupération qualifiés ;
8. rétention, purge, restauration et audit receiver qualifiés ;
9. tests offline puis loopback intégralement verts ;
10. décision propriétaire distincte autorisant une livraison réelle d’un export déjà validé.

La reprise WO-036 a été arrêtée après un premier `201/IMPORTED` et un duplicate durable que le
sender a classé inconnu en raison d’un invariant temporel incompatible avec le contrat receiver.
La sortie fail-closed, l’absence de retry et de collision, puis le cleanup complet ont préservé les
frontières de sécurité. Un Work Order runtime distinct et une nouvelle décision sont requis.

WO-036 ne satisfera que la qualification synthétique Windows/Windows. Même verte, elle ne change
ni `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED`, ni le blocage `PROVIDER_OWNER_GO_REQUIRED`.

Une qualification loopback ne satisfait aucune de ces portes par elle-même.

## 11. Références

1. [Contrat d’architecture v1.0](../architecture/J9-OPTIONAL-LOCAL-PUSH.md).
2. [Schéma ACK v1](../../src/main/resources/schemas/j7-delivery-ack-v1.schema.json).
3. [Runbook de qualification](../runbooks/J9-OPTIONAL-LOCAL-PUSH.md).
4. [ADR-SS-003 acceptée](../../ADR-SS-003-optional-integration-topology.md).
5. [Work Order WO-027](../work_orders/completed/WO-SS-20260901-027-optional-local-push-implementation.md).
6. [Architecture J7](../architecture/J7-CANONICAL-EVENT-EXPORT.md).
7. [Règles du dépôt](../../AGENTS.md).
8. [Work Order WO-035](../work_orders/completed/WO-SS-20260902-035-j9-real-j7-delivery-sender.md).
9. [Work Order WO-037](../work_orders/completed/WO-SS-20260903-037-j9-j7-browser-origin-boundary.md).
10. [Qualification WO-037](../validation/J9-WO037-J7-BROWSER-ORIGIN-BOUNDARY-QUALIFICATION-20260903.md).
11. [Rapport de reprise arrêtée WO-036](../validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-STOP-20260903.md).
