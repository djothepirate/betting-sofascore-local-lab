# Changelog

Les évolutions notables du SofaScore Local Lab sont consignées dans ce fichier.

## [Non publié]

### Après J9 — WO-041 porte de readiness/listener LocalLabB

- ouverture depuis le commit WO-036 arrêté
  `62bb8d126d28da2aedefb8845ee3817492229a71`, dans la branche
  `codex/j9-wo041-local-labb-readiness-listener-gate` et le worktree distinct `.tmp/w41` ;
- diagnostic borné à la double observation des listeners dans l'outillage WO-036 : une apparition
  exacte entre les deux instantanés peut être classée à tort comme conflit ;
- correction attendue par instantané unique cohérent, sans assouplir l'adresse, le port, le nombre
  de listeners ou le PID propriétaire ;
- qualification hors ligne et hôte autorisée uniquement sur `127.0.0.1`, avec processus
  synthétiques possédés et cleanup exact ;
- WO-036 reste arrêté, R5 reste consommé et aucun R6, POST receiver, appel fournisseur, donnée
  dérivée, réseau distant, VPS, production, PR ou validation INT-001 n'est autorisé.

### Après J9 — WO-036 reprise R5 arrêtée avant le duplicate

- manifeste R5 neuf gelé avant le premier POST au commit
  `7c7505174c6a01da1c0134cd0564bd49529d0445`, SHA-256
  `c7a7fcee02b5d70b49551509a0773b0b404b369af94c386308838398e51b3ec0`, après qualifications
  vertes, clone A vers B, six journaux privés expurgés et preuve de zéro appel ;
- premier import conforme depuis A : `201/IMPORTED`, ledger `DELIVERED`, un seul receipt, payload
  byte-identique, audit `IMPORTED` et outbox, sans retry ;
- arrêt fail-closed avant le deuxième POST : après l'arrêt gracieux de A, la claim one-shot de
  démarrage B a échoué sur la porte du listener exact `127.0.0.1:8087` ; Spring/Tomcat a annoncé
  son démarrage dans le journal privé, mais l'outillage n'a pas validé le listener dans son délai
  borné et a nettoyé le processus ;
- zéro tentative B, duplicate ou collision, cause racine `NOT_ESTABLISHED`, aucun rejeu de la claim
  consommée et besoin d'un Work Order distinct de diagnostic de l'outillage avant un éventuel R6 ;
- scan final de huit journaux privés sans occurrence interdite, arrêt gracieux du receiver et
  cleanup exact à zéro processus, listener, conteneur, volume, certificat ou racine privée R5 ;
- redémarrage `healthy` du conteneur PostgreSQL primaire exact avec la même identité et le même
  volume sur `127.0.0.1:5432`, sans accès, recréation ni purge ;
- publication du rapport
  `J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R5-STOP-20260903`, résultat `STOPPED`, taille `12501`
  octets et SHA-256 `1bee325424931a8ecc6e0b445fbd06efd2e80364a2dad8e038245dd2012c8053` ;
  WO-036 reste actif, R5 est consommé et toute reprise R6 exige une correction qualifiée, une
  décision propriétaire distincte et un manifeste neuf gelé avant le premier POST ;
- qualifications post-campagne vertes : Pester `68/68`, Local Lab Surefire `1136/0/0/5` et
  Failsafe `89/0/0/0` dans les deux parcours Maven, receiver Surefire `297/0/0/0` et Failsafe
  `98/0/0/0`, puis zéro résidu WO-036 ou Testcontainers au contrôle hôte final ;
- maintien à `NOT_EVIDENCED` du statut de permission officielle et à `NO` des données dérivées,
  réseaux fournisseur, receiver réel ou distant, VPS, production, PR et validation INT-001.

### Après J9 — WO-036 reprise R5 synthétique autorisée

- push de la branche WO-040 validée, puis intégration linéaire de sa clôture dans WO-036 au commit
  `9cf14180404efe899abeb6a7f3f3d6b7f1e6a028`, sans rebase, squash ni réécriture ;
- décision propriétaire distincte enregistrée le `2026-09-03T19:05:02.0860147Z` pour reprendre
  WO-036 avec un run R5 neuf, exclusivement synthétique et loopback Windows/Windows ;
- réépinglage du seul chemin documentaire admis après enregistrement one-shot vers
  `J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-R5-20260903.md`, lequel doit être gelé et
  commité avant le premier POST ;
- maintien à `NO` de la permission officielle, des données dérivées, des réseaux fournisseur et
  distant, du VPS, de la production ainsi que de la PR et de la validation INT-001.

### Après J9 — WO-040 capture de réponse de collision WO-036

- ouverture depuis le commit R4 exact `33d2ae0bd5c2c8daae4cd708b0f12efaf9cd029d`, dans la branche
  `codex/j9-wo040-collision-response-client-qualification` et le worktree court `.tmp/w40` ;
- portée bornée au lecteur HTTP du harnais `scripts/wo036`, après un effet receiver de divergence
  durable dont le statut n’a pas été capturé côté client R4 ;
- qualification hors ligne autorisée, puis qualification optionnelle contre INT-001 exclusivement
  loopback et synthétique, sans reprise ou rejeu de WO-036 ;
- sender Java, receiver, migrations, protocole, permission officielle, réseaux fournisseur et
  distant, VPS, production et base primaire inchangés et hors périmètre.
- reproduction hors ligne déterministe de la dépendance fautive à l'EOF : une réponse fixe
  complète suivie d'un transport fautif était perdue parce que le lecteur réclamait encore un
  `Read()` ; attribution compatible avec R4 à forte confiance, sans inventer les octets R4 non
  conservés ;
- lecture désormais incrémentale selon le framing HTTP : restitution exacte à la longueur déclarée
  ou au chunk terminal, EOF conservé pour le close-delimited, refus fail-closed des doubles
  framing, troncatures, chunks hostiles, dépassements et octets surnuméraires déjà bufferisés ;
- première correction au commit `f6e6a804f507bad48943534d4179bfcc90437766`, puis compatibilité
  complémentaire au commit `80cd5a33b19b2da48f0ab0821ef6fdfbd2623ba4` pour accepter exactement
  l'unique séparateur d'une reason phrase vide émis par INT-001/Tomcat, sans accepter de version,
  code, séparateur ou caractère de contrôle ambigu ;
- préparations hôte fail-closed : un écart de destination du volume primaire détecté avant
  ressources, une route PFX refusée par SChannel avant écriture HTTP, puis un import nominal isolé
  arrêté sur `HTTP_STATUS_LINE` avant tout POST de collision ; aucune continuation, boucle ou retry
  automatique et cleanup exact entre les essais ;
- qualification finale exclusivement synthétique et loopback contre INT-001 inchangé : exactement
  deux POST, `201` sous `Content-Length`, puis `409/J7_IMPORT_CONFLICT` sous framing chunked, un
  seul receipt, payload et outbox, et les deux audits attendus dont
  `DIVERGENCE_REJECTED/EXPORT_ID_DIVERGENCE` ;
- qualifications vertes : Pester campagne `53/53`, Pester campagne plus infrastructure `68/68`,
  Surefire `1136/0/0/5` et Failsafe `89/0/0/0` sous `clean verify` puis sous le profil
  `integration-tests`, ainsi que la validation Compose ;
- cleanup hôte à zéro processus, listener `5433/8444`, conteneur, volume, certificat ou racine
  privée résiduel ; PostgreSQL primaire exact maintenu `running/healthy` sur `127.0.0.1:5432`,
  sans accès ni purge ;
- passage de WO-040 à `READY_FOR_OWNER_REVIEW` avec `PASS_LOCAL_FAIL_CLOSED` et publication du
  rapport `J9-WO040-COLLISION-RESPONSE-CLIENT-QUALIFICATION-20260903`, SHA-256
  `2eb13aa1825d21eb5c40e97ffebf77246099d781f626591132897c4d128e9aee` ; WO-036 reste arrêté,
  R5 non autorisé et soumis à une décision propriétaire distincte et à un manifeste neuf gelé
  avant son premier POST ;
- validation propriétaire de WO-040, readiness locale reconnue et déplacement vers les Work
  Orders terminés le `2026-09-03T18:58:26.5005063Z`, soit
  `2026-09-03T20:58:26.5005063+02:00` en Europe/Paris ; cette clôture ne reprend pas WO-036,
  n'autorise pas R5 et maintient toutes les portes fournisseur, receiver réel ou distant, VPS et
  production à `NO`.

### Après J9 — WO-036 reprise R4 synthétique arrêtée après divergence durable

- gel du manifeste R4 avant le premier POST au commit
  `e12500dc8af9133b254bfe075d74e979cad472a6`, SHA-256
  `ff73efe0f9840941c6b281cdba38e21cda63e379acd0154f673ef8d7624b39ef`, avec export, PKI,
  bases isolées et processus neufs, exclusivement synthétiques et loopback ;
- qualification de A sous `201/IMPORTED` et `DELIVERED`, puis de B sous `200/DUPLICATE` et
  `DUPLICATE_CONFIRMED`, avec un seul receipt, payload et outbox ;
- troisième et dernier appel consommé : le receiver a persisté exactement un audit corrélé
  `DIVERGENCE_REJECTED/EXPORT_ID_DIVERGENCE`, sans second receipt, payload ou outbox, mais le
  client de campagne n'a conservé ni statut HTTP ni code sûr et a classé la claim
  `FAILED_OR_UNKNOWN_CONSUMED` ; aucun retry, rejeu ou quatrième appel ;
- correction factuelle de l'hypothèse transitoire de timeout : l'intervalle exact entre claim et
  achèvement est `539.19 ms`, ce qui exclut le timeout configuré ; la cause de non-capture reste
  `NOT_ESTABLISHED` et aucun défaut du sender Java, du receiver, de WO-038 ou de la sérialisation
  de requête WO-039 n'est démontré ;
- arrêt gracieux des trois applications, scan de huit journaux privés sans occurrence interdite,
  cleanup exact à zéro résidu et redémarrage `running/healthy` du PostgreSQL primaire exact sur
  `127.0.0.1:5432`, sans recréation ni purge ;
- postflights verts : Pester `62/62`, Local Lab Surefire `1136/0/0/5` et Failsafe `89/0/0/0`
  dans les deux parcours Maven, receiver Surefire `297/0/0/0` et Failsafe `98/0/0/0`, sans
  résidu Testcontainers ;
- publication du rapport
  `J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R4-STOP-20260903`, résultat `STOPPED`, SHA-256
  `16e9f85e12109f2709146312410bbe2a5e040c4f176c2d5596746aa2b70076b5` ; WO-036 reste actif et
  exige un Work Order de harnais distinct, sa validation, une décision propriétaire séparée, un
  run R5 neuf et un manifeste R5 gelé avant son premier POST ;
- maintien de `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED` et des interdictions de donnée dérivée,
  réseau fournisseur ou distant, VPS, production, push, fusion, PR ou validation INT-001.

### Après J9 — WO-039 sérialisation HTTP de la sonde de collision WO-036

- intégration linéaire de la clôture validée WO-039 dans la branche WO-036, puis autorisation
  propriétaire séparée du run R4 neuf le `2026-09-03T16:37:32.7385871Z` ; la reprise reste
  exclusivement synthétique et loopback, avec trois appels maximum, aucun retry et un manifeste
  R4 distinct obligatoirement gelé avant le premier POST ;
- réépinglage du seul chemin documentaire admis après enregistrement one-shot des exécutables vers
  `J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-R4-20260903.md`, sans changement du sender Java,
  du receiver, du protocole ou des portes fournisseur, distante, VPS et production ;

- ouverture de `WO-SS-20260903-039-j9-wo036-collision-probe-http-serialization` depuis le commit
  R3 exact `277f65f318386c763069b9e1907d34acf3b228ba`, dans la branche dédiée
  `codex/j9-wo039-collision-probe-http-serialization` et le worktree Windows court `.tmp/w39` ;
- portée bornée au harnais `scripts/wo036` : correction et preuve de la valeur exacte du
  `Content-Type` sur le fil, plus conservation expurgée du statut et d’un code d’échec sûr ;
- aucun assouplissement du protocole ou du média type, aucune modification du sender Java, du
  receiver INT-001 ou des migrations, et aucun rejeu de WO-036 autorisés ;
- qualification autorisée hors ligne puis, si nécessaire, contre INT-001 exclusivement loopback
  et synthétique, sans donnée fournisseur, receiver distant, VPS, production ou base primaire.
- correction au commit `90c1354c97f506b8291fedae80b7dc6ed37e2c11` : construction explicite des octets
  HTTP/1.1, média type exact sans espace ajouté, en-têtes uniques, `Content-Length` vérifié, corps
  mutant byte-identique, TLS direct épinglé vers `127.0.0.1:8444`, aucun proxy, redirect ou retry,
  réponse bornée et expurgée ;
- qualification hôte synthétique : exactement un POST, un receipt, un payload et un outbox
  préexistants, puis un unique audit `DIVERGENCE_REJECTED/EXPORT_ID_DIVERGENCE`, sans second effet
  durable, retry ou rejeu ;
- correction complémentaire au commit `058c57b05ac4c40e1867af60e76cce6cc2864e67` : acceptation
  bornée d’une ligne de statut HTTP/1.1 sans reason phrase, maintien du refus des lignes ambiguës ou
  malformées et qualification hors ligne sans rejouer le POST consommé ;
- qualifications vertes : Pester `47/47`, Surefire `1136/0/0/5` et Failsafe `89/0/0/0` sous
  `clean verify` puis sous le profil `integration-tests`, `git diff --check`, contrôles UTF-8,
  secrets, loopback et flags bloquants ;
- cleanup complet des processus, listeners `5433/8444`, conteneur, volume, réseau et PKI
  temporaires ; le conteneur PostgreSQL primaire exact reste `running/healthy` sur
  `127.0.0.1:5432`, sans accès ni purge ;
- passage de WO-039 à `READY_FOR_OWNER_REVIEW` avec le résultat `PASS_LOCAL_FAIL_CLOSED` ; WO-036
  reste arrêté et R4 exige une décision propriétaire séparée ainsi qu’un manifeste neuf gelé avant
  son premier POST ; publication du rapport de qualification avec le SHA-256
  `4108f3916f0800c5d1c3616f0c6af866462cda5a188991f15f0305b0ea8d7f50`.
- validation propriétaire de WO-039, readiness locale reconnue et déplacement vers les Work Orders
  terminés le `2026-09-03T16:31:41Z`, soit `2026-09-03T18:31:41+02:00` en Europe/Paris ; WO-036
  reste arrêté, sans reprise R4 implicite, et toutes les interdictions fournisseur, receiver
  distant, VPS et production demeurent effectives.

### Après J9 — WO-038 sémantique temporelle de l’accusé J7

- ouverture de `WO-SS-20260903-038-j9-j7-ack-receipt-time-semantics` depuis le commit exact
  `4cbcb1eb48344b153cd8d8f392aa805feb84ea32`, dans une branche et un worktree dédiés ;
- cause bornée aux comparaisons du `receivedAt` durable, déclaré par le receiver, avec les instants
  de tentative issus de l’horloge distincte du Local Lab ; aucune tolérance inter-horloges ne peut
  fournir un ordre causal contractualisé ;
- correctif qualifié au commit `3a0c297a5151c572417b4f2f12bb5c3ed216172f` : le sender ne
  compare plus ces horloges, persiste l’instant receiver exact et conserve les paires
  `201/IMPORTED` et `200/DUPLICATE`, les corrélations strictes et l’ordre local
  `completedAt >= startedAt` ;
- ajout d’une politique commune de représentation de `receivedAt` : UTC canonique avec suffixe
  `Z`, précision maximale à la microseconde et année ISO non étendue `0001..9999` ; les valeurs
  trop précises ou hors plage restent classées fail-closed sans preuve ACK ni retry ;
- ajout de la migration append-only V30, limitée au remplacement du trigger de résultat : V29
  reste byte-identique à `13 569` octets et SHA-256
  `49f32e88af2ed0115fa9cc5587913f4f0ed7576d49eb1bd4634a0dd212416bd5`, tandis que V30 fait
  `1 804` octets et porte le SHA-256
  `4fdb5e8ed1865d0b1fb0b028653f5600e70fa96148c4da102e0371b1ad2d2967` ;
- preuve d’upgrade V29→V30 sur une base isolée : entrée Flyway V29, comptes et empreinte du ledger
  inchangés, une seule migration appliquée, gardes locales et append-only conservés, instant
  receiver antérieur relu exactement, sentinelles infinies et années hors plage refusées ;
- qualifications vertes : Surefire `1136/0/0/5`, Failsafe `89/0/0/0`, tests ciblés domaine,
  service, runtime, ledger, Flyway et E2E mTLS synthétique, Compose silencieux, parsing PowerShell,
  UTF-8 `23/23`, secrets, loopback, flags bloquants et cleanup ;
- publication du rapport
  `J9-WO038-J7-ACK-RECEIPT-TIME-SEMANTICS-QUALIFICATION-20260903` avec le résultat
  `PASS_LOCAL_FAIL_CLOSED` et l’empreinte
  `QUALIFICATION_REPORT_SHA256=e51bc537c775b4378ee1c6672f86f6c7c085849d62d51970c3d1b6e4aef1395a` ;
- validation propriétaire de WO-038, reconnaissance de la readiness locale et déplacement vers les
  Work Orders terminés le `2026-09-03T12:35:39Z`, soit `2026-09-03T14:35:39+02:00` en
  Europe/Paris ;
- invariants maintenus : receiver INT-001 inchangé et non appelé, aucun retry, réseau réel, donnée
  fournisseur, VPS ou production ; la validation de WO-038 ne reprenait pas elle-même WO-036 ;
- décision propriétaire distincte de reprise R3 enregistrée le
  `2026-09-03T13:02:58.9483990Z`, soit `2026-09-03T15:02:58.9483990+02:00` en Europe/Paris,
  exclusivement pour un run synthétique Windows/Windows neuf, un receiver INT-001 loopback local
  et un manifeste distinct avant le premier POST.

### Après J9 — WO-037 frontière d’origine du navigateur J7

- ouverture de `WO-SS-20260903-037-j9-j7-browser-origin-boundary` depuis le commit exact
  `f4f53aa24b4a8a764819bdb7cf79c87482bd7244`, dans une branche et un worktree dédiés ;
- correction runtime `f28e4b6954c0fb703923f770ab9156326e212a07` strictement bornée : la politique
  `same-origin` est réservée au sous-arbre canonique `/events/{canonicalEventId}/exports/**`,
  tandis que `no-referrer` reste appliqué aux autres routes ;
- maintien sans assouplissement de la frontière `HandlerMethod` : Host exact
  `127.0.0.1:8087`, Origin absent ou exactement `http://127.0.0.1:8087`, refus de
  `Origin: null`, des valeurs multiples ou hostiles et de tout en-tête forwarded ;
- ajout au commit `2596f0592496b2ba84893c8f4ef2cf0185d46f8f` d’un profil Maven explicite et d’un
  lanceur de qualification qui démarrent Spring/Tomcat réel avec services synthétiques en mémoire,
  sans base de données, receiver, fournisseur ou cible non loopback ;
- qualification Chromium réelle `PASS` : aperçu `200` avec `Referrer-Policy: same-origin`,
  préparation native `200` avec l’Origin loopback exact et préparation issue de l’origine opaque
  `null` refusée `403` ; exécution, réconciliation, claims, appels receiver/fournisseur/hors
  loopback, téléchargements, artefacts interdits et listeners résiduels tous à zéro ;
- contrôles verts : Surefire `1132/0/0/5` sur `164` suites, Failsafe `85/0/0/0` sur `4` suites,
  tests ciblés sécurité/livraison `42/0`, Pester `8/8`, harnais Chromium `1/0/0/0` et
  validation expurgée de `docker compose config` ;
- publication du rapport `J9-WO037-J7-BROWSER-ORIGIN-BOUNDARY-QUALIFICATION-20260903` avec résultat
  `PASS_LOCAL_FAIL_CLOSED` et SHA-256
  `db8993328643a0ecb39eb83ff9eab6223f6c6ca6605b4cacf3e96dea171fab01` ;
- validation propriétaire des commits runtime `f28e4b6954c0fb703923f770ab9156326e212a07`, harnais
  `2596f0592496b2ba84893c8f4ef2cf0185d46f8f` et documentation
  `24739d03d33801a8a6c8d2fe956e1dc254bb3d57`, readiness locale reconnue et déplacement autorisé
  vers `completed`, décision enregistrée le `2026-09-03T09:53:43Z` ;
- WO-037 passe à `VALIDATED` sans autoriser de livraison ni de réseau réel ;
- le premier essai WO-036 demeure une preuve immuable
  `STOPPED_PRE_RECEIVER_PENDING_DISTINCT_RUNTIME_CORRECTION` ;
- décision propriétaire distincte de reprise WO-036 enregistrée le `2026-09-03T10:10:23Z`, puis
  fast-forward de sa branche vers le commit validé WO-037
  `9a10447d0c94e441b860b942e436e2c943f1e2c1` ; à cet instant, la nouvelle série `201/200/409`
  restait subordonnée à un run neuf et à un nouveau manifeste lié aux exécutables qualifiés ; cette
  autorisation a depuis été consommée par la reprise R2 décrite dans la section WO-036 ci-dessous.

### Après J9 — WO-036 qualification E2E J7 locale Windows/Windows

- ouverture de `WO-SS-20260902-036-j9-j7-local-e2e-qualification` depuis la clôture validée de
  WO-035 au commit exact `aa405c6750062b9df9312f0845188f48f4c778de`, dans une branche et un
  worktree dédiés ;
- portée strictement synthétique : deux applications réelles, deux frontières PostgreSQL isolées,
  PKI mTLS locale éphémère et listeners exclusivement sur `127.0.0.1` ;
- protocole déterministe sans altération des ledgers : `201/IMPORTED` depuis la base Local Lab A,
  `200/DUPLICATE` depuis une base B clonée avant le premier claim, puis collision valide `409` par
  un probe mTLS synthétique de campagne ;
- maintien de `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED` et des interdictions de donnée dérivée,
  réseau fournisseur ou distant, VPS, production, push, merge ou PR INT-001.
- qualification pré-appel du harnais : refus de l’indirection `javapath` au profit du binaire Java
  25 exact, puis correction du scanner de journaux actifs Windows par snapshot borné
  `FileShare.ReadWrite`, avec contrôle de stabilité, limites de 50/100 MiB, erreur expurgée et
  conservation des gardes d’ACL, de contenu, de processus et de listener ; 52 tests Pester couvrent
  notamment la redirection `Start-Process` réelle et le refus fermé d’un writer exclusif ;
- les préparations ayant révélé ces écarts ont été arrêtées et nettoyées avant tout POST : zéro
  appel de route d’import, zéro appel fournisseur, zéro ressource de campagne résiduelle et aucune
  atteinte au PostgreSQL primaire.
- campagne figée ensuite arrêtée avant receiver par un `403` local reproductible dans Brave : la
  réponse `Referrer-Policy: no-referrer` produit `Origin: null` pour le `POST` HTML natif, valeur
  refusée par l'intercepteur qui n'accepte qu'un Origin absent ou exactement loopback ; les tests
  unitaires existants ne couvraient pas cette chaîne navigateur réelle ;
- preuve expurgée : deux préparations navigateur locales refusées, zéro tentative sender durable,
  zéro receipt, payload, audit ou outbox receiver, zéro retry et zéro appel fournisseur ou distant ;
- arrêt gracieux de Local Lab A et du receiver, non-démarrage de B et de la collision, scan de six
  journaux privés sans occurrence interdite, cleanup exact à zéro résidu et redémarrage `healthy`
  du PostgreSQL primaire exact avec son identité, son volume RW et son bind loopback inchangés ;
- vérification post-campagne : `1115` tests standards et `85` tests d'intégration, zéro échec ou
  erreur, `BUILD SUCCESS` ;
- publication du rapport `J9-WO036-J7-LOCAL-E2E-CAMPAIGN-20260903` avec résultat `STOPPED` et
  SHA-256 `5244422187f0bc591e3ce57f20b076037f64bad266ad9052b985d8127b475994` ; WO-036 reste actif et
  exige un Work Order runtime distinct puis une nouvelle décision propriétaire et un nouveau
  manifeste avant toute reprise de la séquence `201/200/409`.
- reprise autorisée exécutée dans un run entièrement neuf après gel du manifeste R2 au commit
  `07fd440a6773526aeb2b28e7c43413023967f6d1`, SHA-256
  `6ef4c4fbe8af1027ff06170bea00aa8ca75c3159bcd21134db2598ddd4b18a78` ;
- séquence R2 arrêtée après deux appels sur trois : A a produit `201/IMPORTED` et `DELIVERED`, puis
  le receiver a persisté idempotemment `200/DUPLICATE` pour B, tandis que le sender a classé B
  `UNKNOWN_RECONCILIATION_REQUIRED` sous `ACK_HTTP_STATUS_MISMATCH` ;
- cause établie côté sender : sa borne basse temporelle rejette le `receivedAt` durable initial que
  le contrat receiver exige de réemployer pour un duplicate exact ; aucune incompatibilité mTLS,
  parsing, identité, hash ou idempotence receiver n’est observée ;
- arrêt fail-closed sans retry ni collision `409`, cleanup à zéro résidu et redémarrage `healthy` du
  PostgreSQL primaire exact ;
- publication du rapport `J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-STOP-20260903`, résultat `STOPPED`,
  SHA-256 `219f54f2b429b1eaea04c920e55cc87d33f9c5644697129e6ff96fc9cad19062` ; WO-036 reste actif et
  exige un Work Order runtime distinct, sa validation, une nouvelle décision de reprise et un
  manifeste neuf ;
- intégration linéaire de la clôture validée WO-038 au commit
  `a2d44150af6ad6a7931e29d6291a7df711e171e9`, puis autorisation de la reprise R3 ; les preuves R1
  et R2 restent immuables, la nouvelle série reste subordonnée à un répertoire privé, une PKI, des
  bases isolées, des exécutables enregistrés et le manifeste neuf
  `J9-WO036-J7-LOCAL-E2E-CAMPAIGN-MANIFEST-RESUME-R3-20260903` ;
- gel du manifeste R3 au commit `f14c1418995625ea653f8d01afd9c81044f3abd1`, SHA-256
  `cd20a30cd855d161fcaa0f5db93ef0c50fca5c297d569d0a4af2f4f02aab842d`, avant le premier POST,
  puis exécution avec export, PKI, bases et processus entièrement neufs et synthétiques ;
- A qualifie `201/IMPORTED` et `DELIVERED`; B qualifie `200/DUPLICATE` et
  `DUPLICATE_CONFIRMED`, avec un seul receipt, payload et outbox, confirmant ainsi le correctif
  temporel WO-038 dans le parcours Windows/Windows réel ;
- troisième et dernier appel consommé par la sonde de collision, mais réponse non-`409` dont le
  statut exact n'a pas été préservé : aucun audit `DIVERGENCE_REJECTED`, aucun retry et séquence
  contractuelle `201/200/409` non qualifiée ;
- diagnostic à forte confiance, distinct des observations runtime : le client .NET de la sonde
  insère un espace dans le `Content-Type` sérialisé, ce que le receiver strict rejetterait sous
  `400/INVALID_CONTENT_TYPE` avant le service ; aucun défaut du sender Java, de WO-038 ou du
  receiver n'est démontré par cet arrêt ;
- arrêt fail-closed, journaux expurgés sans occurrence interdite, cleanup exact à zéro résidu et
  redémarrage `healthy` du PostgreSQL primaire exact, sans recréation, purge ou lecture de son
  contenu ;
- publication du rapport
  `J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R3-STOP-20260903`, résultat `STOPPED`, SHA-256
  `f59cc0aeaa56fd6c8156032fea7e93048f17f616f1882cc3979bcd69363d1576`; WO-036 reste actif et
  exige un Work Order de harnais distinct, sa qualification, une nouvelle décision propriétaire et
  un manifeste R4 neuf avant tout nouvel appel.

### Après J9 — WO-035 sender réel J7 du Local Lab

- ouverture de `WO-SS-20260902-035-j9-real-j7-delivery-sender` depuis le `main` propre au commit
  exact `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089`, dans une branche et un worktree dédiés ;
- portée bornée à la composition runtime, l'action UI manuelle, mTLS Windows, l'affichage du ledger
  et la réconciliation opérateur sur le socle WO-027/V29 déjà validé ;
- maintien de `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED`, du sender désactivé sans origine ni go
  et de l'interdiction de tout réseau fournisseur/receiver réel, transfert dérivé, VPS ou production ;
- WO-036 et la création de la PR d'INT-001 restent des étapes séparées, non exécutées sous WO-035.
- implémentation qualifiée au commit `f5a27887b7db43576eb608d564c245c8cca3a602` : action UI
  manuelle, confirmation exacte liée à l'ordinal, revalidation des octets `HUMAN_VALIDATED`, claim
  atomique, transport HTTPS/mTLS Windows mono-exécution, ACK strict, ledger et réconciliation sans
  payload ;
- durcissement fail-closed par classification de provenance, voie synthétique strictement
  `SYNTHETIC_ONLY`, frontière `HandlerMethod`/Host/Origin, gate partagée
  `IDLE`/`ACTIVE`/`POISONED`, body publisher one-shot et neutralisation des flags dans les
  launchers et les pipelines CI ;
- qualification `PASS_LOCAL_FAIL_CLOSED` : deux cycles verts à 1 115 tests standards et 85 tests
  d'intégration, Compose silencieux, barrières local-only, distribution, reproductibilité, syntaxe,
  UTF-8, secrets et cleanup propres ;
- rapport autonome SHA-256
  `1028761cbc68154da780d314935880bf17b803c3cf8a35b1386fd29ab28bae76` et passage du Work Order à
  `READY_FOR_OWNER_REVIEW`, sans appel fournisseur ou receiver réel et sans ouvrir WO-036 ni créer
  la PR INT-001 ;
- validation propriétaire enregistrée le `2026-09-02T18:57:00Z`, avec reconnaissance de la
  readiness locale, concordance du commit et du SHA-256, rapport byte-identique avec empreinte
  inchangée, puis déplacement de WO-035 vers les Work Orders terminés ;
- clôture à `VALIDATED` sans modifier `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED`, sans autoriser
  de réseau fournisseur ou receiver réel, de livraison dérivée, de VPS ou de production, et sans
  ouvrir implicitement WO-036 ni créer la PR INT-001.

### Après J9 — WO-031 intégration continue et distribution locale uniquement

- ajout de pipelines GitHub Actions Windows/Linux et GitLab CI pour les tests standards,
  PostgreSQL/Testcontainers, la sécurité, les métriques qualité et le packaging local ;
- fiabilisation du launcher POSIX `mvnw` dans l'image GitLab épinglée : après validation du
  SHA-256, recours à l'outil JDK `jar` lorsque `unzip` et `python3` sont absents, puis restauration
  du bit exécutable de `bin/mvn`, sans modifier l'URL ni l'empreinte Maven épinglées ;
- regroupement des événements GitHub `push` et `pull_request` d'une même branche source afin
  d'annuler le run doublon sans collision entre dépôts ou forks homonymes ;
- remplacement des scripts source du bundle par des launchers qualifiés qui démarrent directement
  l'unique JAR embarqué, sans Maven, en conservant le profil local, les barrières JVM anti-retry et
  la neutralisation des surcharges d'environnement réseau, serveur, datasource et receiver ; les
  commandes Compose figent leur projet et refusent tout daemon Docker non local ;
- refus fail-closed de générer ou de faire tourner les identifiants PostgreSQL lorsqu'un volume
  persistant existe déjà : une nouvelle extraction doit réutiliser le `.env` précédent ou supprimer
  explicitement les données avant toute rotation du mot de passe ;
- production de distributions `EXPERIMENTAL_LOCAL_ONLY` contenant JAR, SBOM CycloneDX,
  provenance et SHA-256, avec `vps.deployable=false` et sans profil fournisseur ;
- génération CycloneDX reproductible à partir de l’horodatage du commit, sans numéro de série,
  avec deux générations successives obligatoirement byte-identiques avant création du bundle ;
- validation fail-closed de l'identité du commit empaqueté et refus de tout tag de release locale
  qui ne serait pas atteignable depuis la branche canonique `main` ;
- qualification d'une version Maven finale avant tag sous forme de snapshot `LOCAL_ONLY` non
  promouvable, puis création du tag sur le même SHA seulement après validation de `main` ;
- refus avant packaging de toute version Maven snapshot dont la base n'est pas un SemVer ou RC
  canonique `X.Y.Z[-rc.N]` ;
- suppression du cache Maven partagé GitLab afin qu'une branche contrôlant son YAML ne puisse
  empoisonner aucune dépendance ensuite consommée par `main` ou par un tag ;
- publication des releases locales taguées par GitLab uniquement ; GitHub conserve la compilation
  et les tests du tag sans reconstruire un second bundle avec une autre chaîne d'outils ;
- conservation des interdictions d'appel SofaScore, de receiver réel, de livraison optionnelle,
  de VPS et de production pendant toutes les qualifications CI.

### Après J9 — WO-030 borne de port de qualification Playwright fournisseur

- ouverture de
  `WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening` depuis `origin/main` au
  commit exact `daf55bf76521f81893f86d04fde3c2903bf22362`, dans une branche et un worktree dédiés,
  sans modifier les preuves WO-029 déjà fusionnées ;
- périmètre borné au refus fail-closed des origines de qualification loopback dont le port se
  trouve hors de `[1, 65535]`, dans `ProviderPlaywrightProperties` puis dans la garde miroir du
  worker enfant ; aucun endpoint, transport, protocole, retry, polling ou schéma ne change ;
- qualification exclusivement offline : aucun appel fournisseur, aucun réseau receiver, aucun
  déploiement VPS et aucune production ne sont autorisés sous WO-030.
- correctif commité sous `154349a2fbebe3fd0a43a63c7105f690ff04976b` : garde parente et
  défense en profondeur du worker refusent les ports absents, `0` et `65536`, tout en acceptant
  structurellement `1` et `65535` sans connexion vers ces bornes ;
- preuve directe du refus avant claim : `claimExecution()` rend
  `PROVIDER_TRANSPORT_UNAVAILABLE`, l'exécution ne démarre pas et l'intention reste
  `CONFIRMED_READY` ;
- qualification `PASS_LOCAL_FAIL_CLOSED` : tests ciblés `14/0/0/0` et `11/0/0/0`, suite
  standard Surefire `1043/0/0/5`, profil runtime `1065/0/0/5` et deux passes Failsafe
  `84/0/0/0`, avec Flyway V1→V29, ledger, mTLS et end-to-end loopback verts ;
- audits finaux propres : `git diff --check`, zéro secret haute confiance, zéro artefact
  Playwright interdit, `server.address=127.0.0.1`, flags fournisseur et livraison réelle bloqués,
  puis zéro processus attribuable au worktree, listener `8087` ou conteneur Testcontainers ;
- rapport autonome SHA-256
  `9bd030f4cc92f32bdeee977089d3f4c5c709278fec4c24cc66ed8fb6d258be5f` et passage du Work Order
  à `READY_FOR_OWNER_REVIEW` ;
- bloc propriétaire complet enregistré le `2026-09-01T14:00:53Z`, soit
  `2026-09-01T16:00:53+02:00` en Europe/Paris : commit d'implémentation et preuve qualifiés validés,
  readiness locale reconnue et déplacement de WO-030 vers `completed` autorisé ; le rapport reste
  byte-identique et son SHA-256 inchangé ;
- clôture documentaire à `VALIDATED` sans appel fournisseur ou receiver réel, sans déploiement VPS,
  sans production et sans déduction d'une autorisation de push ou de fusion.

### Après J9 — WO-029 durcissement de la borne de port du push local optionnel

- ouverture de
  `WO-SS-20260901-029-j9-optional-local-push-port-boundary-hardening` depuis le commit exact
  `36598f1979ddc7be7081431b147691e7e9b8e49d`, dans une branche et un worktree dédiés, pour
  corriger le constat P2 de la PR #21 sans réécrire WO-027 ni sa qualification ;
- périmètre borné au refus d'un port d'origine loopback hors de l'intervalle fermé
  `[1, 65535]` par la configuration avant lecture d'export et avant claim, puis par le transport
  avant le client HTTP ; aucune migration, aucun contrat, endpoint, protocole ou schéma ne change ;
- correctif runtime append-only `8fb4d5a64a8c36d553168c8e82cc4f46454ee96a` et preuve complémentaire
  `699d2245322ef9614f05f912371db8fd9edca2b9` : ports absent et `0` refusés, `1` et `65535`
  acceptés structurellement, `65536` refusé, sans connexion vers ces ports et avec zéro interaction
  export, ledger ou transport pour chaque origine invalide ;
- qualification `PASS_LOCAL_FAIL_CLOSED` : 50 tests ciblés, puis deux passages verts à
  Surefire `1041/0/0/5` et Failsafe `84/0/0/0`, avec migrations V1→V29, mTLS et end-to-end
  loopback ; Compose valide, diff et scan de secrets propres, zéro listener `8087` et zéro
  conteneur Testcontainers résiduel ;
- préservation byte-identique du Work Order WO-027
  (`be18d440edf33ae8c511c4b1bab5ad1e70f6c2167027d525769c98417ebf5c2c`) et de son rapport
  (`d656b7ea12ba38a40b88e9a78e5b7afb9642c4405080b9246b8539d177eb1d12`) ; publication du
  rapport WO-029 validé et déplacement du Work Order vers `completed` avant push, résolution de
  revue et fusion de la PR #21 ;
- maintien de `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED`, du sender désactivé par défaut et de
  toutes les interdictions de receiver réel, livraison réelle, réseau fournisseur, VPS et
  production pendant la qualification offline/loopback.

### Après J9 — WO-027 push local optionnel fail-closed

- autorisation propriétaire d'ouvrir et de réaliser
  `WO-SS-20260901-027-optional-local-push-implementation` depuis le commit exact de clôture de
  WO-026 `aedb5f424883c9e7a1839fd50aa2c2689fa65418`, sur une branche et un worktree dédiés ;
- portée bornée au contrat receiver versionné, au sender/ledger local désactivé, à mTLS,
  l'idempotence, aux accusés, aux états séparés et aux qualifications synthétiques offline/loopback ;
- revue officielle datée maintenant `J9_OFFICIAL_PERMISSION_STATUS=NOT_EVIDENCED` : la documentation
  API publique ne vaut pas consentement et aucune licence applicable n'est versionnée ;
- contrat `J7_OPTIONAL_LOCAL_PUSH` v1.0 et ACK strict versionnés, avec corps limité à l'export J7
  canonique déjà `HUMAN_VALIDATED`, taille maximale de 5 Mio et clé d'idempotence stable
  `j7:<exportId>:sha256:<fileSha256>` ;
- sender fail-closed sans bean, contrôleur, route, scheduler, polling, URI ou cible réelle, utilisant
  un transport HTTPS loopback synthétique sans proxy, redirection, cookie, fallback ou retry ;
- ajout des trois barrières JVM anti-retry, attestées au démarrage puis avant construction et avant
  envoi : `disableRetryConnect=true`, `redirects.retrylimit=1` et
  `enableAllMethodRetry=false` ;
- schéma V29 append-only pour l'identité, les tentatives et résultats de livraison, avec six états
  séparés de J7, concurrence globale `1`, projection gardée, horloge PostgreSQL et réconciliation
  stale manuelle seulement ;
- preuves J6 étendues à V29 : sauvegarde/restauration compare les comptes et l'empreinte métadonnée
  du ledger de livraison sans conserver les octets livrés ni élargir la purge primaire ;
- mTLS synthétique qualifié sur quatre scénarios : nominal, autorité serveur non approuvée, SAN
  divergent et certificat client incorrect ; l'opacité Java est prouvée, mais la non-exportabilité
  native Windows reste `NOT_QUALIFIED_FOR_REAL_TARGET` ;
- idempotence qualifiée avec effet unique et reprise manuelle `DUPLICATE`, refus local d'un
  `exportId` associé à un hash divergent avant claim/socket, et classification terminale d'un
  éventuel HTTP `409` distant ;
- qualification finale `PASS_LOCAL_FAIL_CLOSED` : Surefire `1036/0/0/5`, Failsafe `84/0/0/0`,
  ACK et corps d'erreur bornés à 16 KiB, J7 inchangé, zéro appel fournisseur, zéro receiver réel et
  zéro listener résiduel ;
- publication du rapport
  `docs/validation/J9-WO027-OPTIONAL-LOCAL-PUSH-QUALIFICATION-20260901.md` et passage de WO-027 à
  `READY_FOR_OWNER_REVIEW`, sans déplacement implicite vers les Work Orders terminés ;
- verdict propriétaire `VALIDATE` reçu pour le commit
  `5af48e5ea0e7150b460fe106da74aa5d3bd5489a`, du résultat `PASS_LOCAL_FAIL_CLOSED` et du SHA-256
  `d656b7ea12ba38a40b88e9a78e5b7afb9642c4405080b9246b8539d177eb1d12` ; les deux champs readiness
  et déplacement vers `completed` restant sous forme `<YES|NO>`, le bloc de revue demeure incomplet,
  aucune valeur n'est déduite et WO-027 reste actif à `READY_FOR_OWNER_REVIEW` ;
- bloc propriétaire final complet reçu avec readiness locale `YES` et déplacement vers
  `completed` `YES` : WO-027 passe à `VALIDATED` et est archivé dans les Work Orders terminés ; le
  commit qualifié, le résultat et le hash restent inchangés, comme tous les garde-fous réels ;
- activation réelle, receiver Betting Project, livraison, réseau fournisseur, VPS, production,
  polling, scheduler, retry automatique et fallback maintenus à `NO` ; zéro push déduit de cette
  ouverture.

### Après J9 — étude d’intégration optionnelle ouverte

- ouverture de WO-028 pour corriger factuellement l’identité du schéma J7 dans ADR-SS-003 :
  l’alias ambigu `J7_CANONICAL_EXPORT_V1` est remplacé par l’identifiant manifeste canonique
  `urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1` et sa version `1.0.0`,
  sans modifier la topologie acceptée, l’historique v0.1 ni aucune autorisation runtime ou réseau ;
- publication sans force de la plage J9 finalisée sur la branche privée
  `origin/codex/j9-decision`, au commit
  `1a58a3bd7673f5946d5c48ae573c191e52d223a2`, avec divergence locale/distante nulle et sans
  modification de `main`, resté à `40323faa7dca3341da6ef980b5762f1ff5a32a79` ;
- ouverture de `WO-SS-20260901-026` depuis ce tip J9 publié, sur la branche
  `codex/j9-optional-integration-study` et le worktree `.tmp/j9-optional-integration-study`, pour
  une étude documentaire séparée de toute implémentation ;
- comparaison factuelle, sans score arbitraire, de `OPTIONAL_LOCAL_PUSH`, `VPS_PLAYWRIGHT` et du
  repli `KEEP_LOCAL_NO_INTEGRATION`, avec preuves, confiance et lacunes restantes ;
- proposition initiale d'ADR-SS-003 v0.1, puis acceptation propriétaire du draft immuable le
  `2026-09-01T07:25:25.4809414Z` (`09:25:25.4809414+02:00` en Europe/Paris) : le push local d'un
  export J7 déjà `HUMAN_VALIDATED` devient la topologie sélectionnée pour un futur Work Order sous
  la gouvernance actuelle ; Playwright
  VPS reste `DEFERRED_BLOCKED_BY_CURRENT_GOVERNANCE`, car `LOCAL_ONLY`, ADR-SS-001 et la frontière
  des payloads bruts doivent être redécidés explicitement avant toute qualification distante ;
- gel du draft proposé dans le commit local
  `ca789a3a40ea5fc6c16312bd73f675bc9fd32650`, avec SHA-256 ADR-SS-003
  `0edcc1e7db2ffc268d1560342d8c2f7c8ea9e1f91c65148e0504c1217f5982be`, afin de lier la future
  décision propriétaire à un objet immuable ;
- maintien de la permission officielle à `NOT_EVIDENCED` et définition de portes distinctes pour
  l'acceptation de l'ADR, un futur contrat HTTPS/mTLS, une éventuelle preuve VPS et toute décision
  de production ;
- validation et clôture de WO-026 sur l’acceptation d’ADR-SS-003 v0.1, qui satisfait l’objet de ce
  lot documentaire conformément au précédent WO-022, sans fabriquer de champ de décision
  propriétaire supplémentaire ;
- confirmation que cette acceptation ne vaut ni implémentation du sender ou du receiver, ni réseau
  fournisseur, ni livraison live, ni déploiement VPS, ni production ;
- aucun code, endpoint, URI, schéma, migration, configuration, certificat, secret, appel
  fournisseur, transfert, déploiement VPS, polling, scheduler, live, retry ou fallback ajouté ou
  autorisé ; ADR-SS-001, ADR-SS-002, `AGENTS.md` et `docs/reference` restent inchangés ;
- vérification Maven du draft hors sandbox réussie avec 946 tests, zéro échec, zéro erreur et cinq
  skips prévus ; le rerun post-acceptation a ensuite rapporté 946 tests, un échec, zéro erreur et
  cinq skips : seul le contrôle J6 a refusé l’environnement, parce qu’une instance locale
  préexistante de `SofascoreLocalApplication` occupait
  déjà `127.0.0.1:8087`; la contre-vérification ciblée a reproduit cette porte fail-closed, sans
  arrêt du processus utilisateur ; `git diff --check`, 136 liens locaux, hygiène documentaire,
  loopback et flags fournisseur bloqués restent contrôlés ;
- aucun push, PR, merge ou Work Order d’implémentation n’est autorisé par l’instruction d’ouverture
  ou l’acceptation d’ADR-SS-003.

### J9 — décision de gouvernance clôturée

- ouverture de `WO-SS-20260831-018` sur `codex/j9-decision`, depuis la baseline propre
  `40323faa7dca3341da6ef980b5762f1ff5a32a79`, sans changement de code, endpoint, schéma, migration
  ou configuration réseau ;
- ajout d'une matrice de décision factuelle fondée sur les preuves gelées J6, J7 et J8, avec niveaux
  `PASS_BOUNDED`, `PASS_LOCAL`, `PARTIAL` et `NOT_MEASURED` et sans score arbitraire ;
- orientation propriétaire consignée comme `PREPARE_OPTIONAL_INTEGRATION`, tandis que la décision
  finale reste `PENDING_PROVIDER_ROBUSTNESS_EVIDENCE` et n'autorise aucune intégration ;
- préparation d'une preuve multi-dossier séparée sous WO-019, plafonnée à 38 appels directs et
  bloquée avant réseau par ADR-SS-002, la readiness hors ligne, une sauvegarde/restauration V28 et
  un go propriétaire explicite à usage unique ;
- ouverture de `WO-SS-20260831-019` sur le worktree distinct
  `codex/j9-provider-robustness`, avec corpus D1/D2/D3 et huit campagnes unitaires existantes,
  sans orchestrateur, code runtime, migration ou extension d'allowlist ;
- proposition d'ADR-SS-002 v0.1, sans effet réseau tant que le propriétaire ne l'a pas acceptée,
  pour rendre explicite l'exception unique portant le plafond agrégé de 30 à 38 appels ;
- acceptation propriétaire explicite d'ADR-SS-002 v1.0 après reconnaissance de la revue officielle,
  du corpus D1/D2/D3, du plafond 38, du go unique de 60 minutes au plus, de la poursuite sur les
  seuls `404` natifs J4/J5 et des non-autorisations ; WO-019 est d'abord passé à
  `READY_FOR_OFFLINE_READINESS`, avec `NETWORK_AUTHORIZED=NO` et go global toujours absent ;
- readiness hors ligne partiellement verte : 928 tests standards, 67 tests d'intégration,
  `Verify-Local.ps1 -WithIntegrationTests` et la validation Compose réussis, sans appel fournisseur ;
- qualification J3 Playwright loopback puis unique contre-qualification hors sandbox échouées de
  manière identique : 14 tests, 12 erreurs `RUNTIME_FAILURE` lors de la fermeture gracieuse et deux
  scénarios d'arrêt opérateur réussis ;
- arrêt à la porte de sécurité avec zéro appel fournisseur, aucun processus possédé résiduel, aucun
  listener 8087 et aucun artefact navigateur interdit ; J4, J5 et le cycle sauvegarde/restauration
  V28 n'ont pas été exécutés ;
- WO-019 revient à `OPEN_AWAITING_PREREQUISITES` ; WO-020 est ouvert depuis `542f352` sur
  `codex/j9-playwright-graceful-close`, sous autorisation propriétaire bornée au diagnostic et au
  correctif de la fermeture gracieuse et du nettoyage de l'arbre Playwright ; réseau fournisseur,
  reprise de WO-019, intégration et production restent explicitement non autorisés ;
- diagnostic causal WO-020 établi par un test discriminant rouge : le worker émettait `CLOSED` puis
  attendait l'EOF parent, tandis que le superviseur attendait ou terminait l'arbre avant cet EOF ;
  le timeout gracieux configuré à cinq secondes était aussi plafonné à tort par la borne opérateur
  de deux secondes ;
- correction locale limitée au superviseur : inventaire frais après `CLOSED`, `shutdownOutput()`,
  modes gracieux/opérateur explicites, horodatage d'arrêt immuable, préemption sous `ReentrantLock`
  par polling de 20 ms, fenêtre de sortie naturelle de 250 ms puis replis bornés à 1 s / 2 s / 5 s,
  et nouveau budget uniquement pour un nouvel essai de nettoyage échoué avant mutation ; worker,
  protocole IPC, endpoints, scripts et configuration inchangés ;
- qualification locale WO-020 verte : superviseur `31/31`, protocole `10/10`, sécurité `1/1`, puis
  J3, J4 et J5 loopback `14/14` chacun ; le premier lancement J5 sous Windows PowerShell 5.1 s'est
  arrêté avant Maven faute de `ResolveLinkTarget`, puis la commande inchangée a réussi sous
  PowerShell 7.6 ;
- portes finales WO-020 : `clean verify` avec 931 tests standards, quatre skips prévus, 67 tests
  d'intégration, Flyway V28 confirmé uniquement dans Testcontainers et Compose valides ; zéro accès
  fournisseur, processus possédé ou listener 8087, scanner d'artefacts J5 vert ; ceci ne vaut pas le
  cycle chiffré sauvegarde/restauration V28 de WO-019 ;
- WO-020 passe d'abord à `READY_FOR_OWNER_REVIEW`, sans validation propriétaire implicite ;
- validation propriétaire explicite de WO-020 reçue le 2026-08-31 à `00:41:58Z`, avec déplacement
  autorisé vers les Work Orders terminés ; WO-019 reste `OPEN_AWAITING_PREREQUISITES`, sa preuve
  reste `DRAFT` et réseau, go, reprise, intégration et production restent à `NO` ;
- autorisation propriétaire distincte de reprise de WO-019 reçue le 2026-08-31, exprimée exactement
  par « J’autorise la reprise de WO-019 » ; cette décision place d'abord WO-019 à
  `READY_FOR_OFFLINE_READINESS`, avec `OFFLINE_READINESS=AUTHORIZED_NOT_EXECUTED` et
  `WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO` : seul le rejeu hors ligne est autorisé ;
- rejeu hors ligne WO-019 distinct ensuite vert le 2026-08-31 : `clean verify` à `931/0/0/4`, intégration à
  `67/0/0/0`, commande exacte `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\Verify-Local.ps1 -WithIntegrationTests`
  à `PASS` avec intégrations `YES` et réseau
  SofaScore `NO`, Compose silencieux à `PASS`, puis J3, J4 et J5 sous `pwsh` à `14/0/0/0` chacun,
  sans accès fournisseur ; la readiness devient `PASS_REEXECUTED_AFTER_VALIDATED_WO020` et WO-019
  passe alors à `READY_FOR_V28_BACKUP_RESTORE` ; le cycle chiffré sauvegarde/restauration V28 reste
  `NOT_EXECUTED`, la preuve reste `DRAFT`, et campagne fournisseur, réseau, go global, consommation
  du go, intégration et production restent non autorisés ;
- première session V28 hôte détachée interrompue à l'invite `age` avant toute saisie, après échec de
  rattachement de son onglet ; audit après arrêt à zéro processus, fichier ou base temporaire
  résiduel ; tentative manuelle suivante arrêtée dans le terminal Codex restreint au préflight
  `DESTINATION_DIRECTORY_NOT_VISIBLE`, avant `age`, `pg_dump`, fichier ou base temporaire ; ces
  deux arrêts locaux n'ont produit aucun appel ni retry fournisseur ;
- sauvegarde/restauration V28 ensuite qualifiée sous PowerShell 7 natif interactif : création à
  `2026-08-31T06:44:41.9669041Z`, qualification à `2026-08-31T06:46:07.7013794Z`, archive chiffrée
  de `6 996 157` octets au SHA-256
  `2b1402d12274f3e9a646aa6134f8cf8bee7a5e11b3249e8dff5c63040cb89d34` et manifeste de `2 202`
  octets au SHA-256 `7d112e4db804125656a54c61edd8c1eb9417f95e8b7c3d4df88d99d92cde6e62` ;
  Flyway `28`, couverture maximale snapshot `794` reçue à `2026-08-30T20:30:46.412Z`, restauration
  qualifiée, zéro mismatch source/restauration, zéro échec d'intégrité brute, zéro fichier partiel
  et zéro base temporaire résiduelle, absence vérifiée indépendamment, connecteur `SAFE`, port 8087
  libre, zéro accès
  fournisseur et aucune purge primaire ; WO-019 passe à `READY_FOR_GLOBAL_OWNER_GO` avec
  `NEXT_GATE=GLOBAL_OWNER_GO`, tandis que `WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO`, réseau,
  go global, consommation, intégration et production restent à `NO`/`NOT_GRANTED` et la preuve à
  `DRAFT` ;
- revue factuelle de sources officielles : restrictions publiées sur les requêtes automatisées,
  le scraping, l'agrégation, la reproduction et l'extraction substantielle sans consentement
  explicite ; point d'entrée `Sofascore API` et canal `Product -> API` présents, mais aucune licence,
  authentification, limite d'appel ou permission applicable extraite ; aucune conclusion juridique ;
- maintien d'ADR-SS-003 à `NOT_CREATED` : une étude de faisabilité ultérieure et distincte comparera
  le push local optionnel vers le Betting Project à une topologie Playwright sur VPS ; celle-ci
  n'est plus exclue, mais reste non mesurée et non autorisée, sans dépendance critique ;
- validation standard de chacun des deux lots documentaires : 928 tests, zéro échec, zéro erreur et
  quatre skips prévus ; diff, liens Markdown locaux, recherche de credentials et valeurs par défaut
  loopback/réseau contrôlés, sans appel fournisseur ;
- aucun appel fournisseur, push, PR ou fusion vers `main` n'est autorisé par ce lot d'ouverture.
- go propriétaire global unique accordé pour la fenêtre
  `[2026-08-31T07:15:00Z,2026-08-31T08:15:00Z)`, puis consommé irréversiblement au premier claim J3
  accepté ; aucun autre go ni droit de rejeu n'en découle ;
- exécution limitée au dossier D1 : quinze pages `SCHEDULED_EVENTS`, une découverte tournoi, un
  appel `EVENT_DETAILS` et les trois familles J5, soit `20` tentatives directes sur le plafond de
  `38`, `20` réponses HTTP `200`, `20` parsings compatibles, zéro `404`, zéro retry et zéro appel
  D2/D3 ;
- arrêt de la série avant D2 après l'audit temporel J5 : les timestamps `requested_at` persistés
  sont capturés avant `page.navigate` et ne mesurent pas le départ réseau réel ; l'écart de
  `2 967 ms` calculé entre ces timestamps ne démontre donc ni violation on-wire ni conformité au
  délai strict de trois secondes, qui reste `NOT_MEASURED` ;
- WO-019 et sa preuve globale passent à `STOPPED`, le go devient
  `CONSUMED_AND_TERMINATED_BY_STOP`, D2/D3 deviennent `NOT_STARTED_AFTER_GLOBAL_STOP` et toute
  nouvelle qualification exige un Work Order runtime distinct, une nouvelle readiness et un nouveau
  go propriétaire ;
- arrêt gracieux réussi, port 8087, application et descendants Playwright absents, aucun artefact
  navigateur interdit ; flags fournisseur persistés à `false`, origine et allowlist persistées
  vides, réseau reverrouillé, intégration et production toujours non autorisées ; la recommandation
  déterministe J9 est `KEEP_LOCAL`, sous réserve de la confirmation explicite du propriétaire ;
- validation post-arrêt réussie : `clean verify` avec `931/0/0/4`, profil d'intégration avec
  `67/0/0/0`, schéma Flyway V28 et configuration Compose valides, sans appel fournisseur.
- ouverture documentaire de `WO-SS-20260831-021` sur la branche/worktree distinct
  `codex/j9-playwright-minimum-delay`, depuis le commit STOPPED `216b184`, pour rendre mesurable et
  garantir en loopback le délai minimal de trois secondes entre départs réseau observables ; statut
  `OPEN_AWAITING_OWNER_AUTHORIZATION`, implémentation, tests ajoutés, réseau fournisseur, reprise de
  WO-019, nouveau go, intégration et production à `NO` ; validation documentaire à `931/0/0/4`,
  diff, liens locaux, credentials et invariants loopback/réseau verts.
- autorisation propriétaire de réaliser WO-021 reçue le 2026-08-31 à
  `08:43:48.8202621Z`, strictement bornée au diagnostic, au correctif et à la qualification
  loopback du délai minimal de trois secondes ; WO-021 passe à `IN_DEVELOPMENT`, tandis que réseau
  fournisseur, reprise de WO-019, nouveau go, endpoint, transport, protocole incompatible, schéma,
  migration, intégration et production restent à `NO` ;
- refus propriétaire explicite de transformer la recommandation déterministe `KEEP_LOCAL` en
  décision finale J9 : la photographie `STOPPED` reste factuelle, mais
  `J9_FINAL_DECISION=NOT_TAKEN` et la décision attend WO-021 ainsi qu'une éventuelle nouvelle preuve
  WO-019 ; toute reprise après incident exigera une nouvelle décision, une readiness fraîche, le
  réexamen ADR prévu par ADR-SS-002 §9, un nouveau manifeste et un nouveau go global à usage unique.
- discriminant WO-021 exécuté avant correction et rouge en `0,08 s`, établissant l'absence de fence
  conservateur fondé sur la fin de réponse et la lacune de preuve de la marge résiduelle de `33 ms` ;
- correction runtime bornée : temporisation logique monotone avec réévaluation des réveils
  anticipés, fence parent commun aux campagnes/workers attendant au moins trois secondes après la
  fin observable du dispatch précédent, et observation CDP corrélée de l'unique document principal
  exact ; toute perte de preuve temporelle, réponse de cache/service worker ou identité incohérente
  reste fail-closed ; une interruption avant ou pendant l'attente conserve son statut, empoisonne la
  preuve temporelle et n'émet aucun `GET`, sans nouvel endpoint ni changement incompatible du
  protocole ;
- premier rejeu J3 Chromium après le correctif principal arrêté avec un seul échec sur `14` tests :
  `TIMEOUT` attendu, `PROTOCOL_ERROR` reçu et `RUNTIME_FAILURE` de clôture supprimé ; diagnostic du
  teardown établi sur les désactivations/détachements CDP synchrones exécutés avant `page.close()`
  sur la navigation bloquée, qui retardaient la trame timeout au-delà du canal IPC ; correction par
  fermeture de la page avant le teardown CDP, sans modifier `clearCookies()` ni les bornes WO-020,
  puis test ciblé `1/1` vert en `5,323 s` et suite complète `14/14` verte en `101,5 s` ;
- qualification locale WO-021 verte : le premier `Verify-Local.ps1 -WithIntegrationTests` compte
  `941` tests standards et `67` tests d'intégration ; chacun des scripts J3/J4/J5 réussit `21` tests
  worker (`10` protocole, `1` sécurité, `10` observation réseau) puis `14` tests Chromium ; J5
  confirme les écarts `requestedAt`, les arrivées serveur loopback et les écarts inter-workers
  `>= 3 s`, ainsi que zéro nouvelle requête pendant un arrêt au fence ; les suites ciblées
  postérieures passent `40/40` pour le superviseur et `8/8` pour le coordinateur, y compris le garde
  d'interruption fail-closed ; le `clean verify` final après ce garde passe `943` tests, zéro échec,
  zéro erreur et quatre skips à `2026-08-31T09:48:10Z` ;
- passage de WO-021 à `READY_FOR_OWNER_REVIEW`, sans déplacement vers les Work Orders terminés et
  sans accès fournisseur ; WO-019 reste `STOPPED`, ses `20` tentatives restent gelées, réseau,
  reprise, nouveau go, intégration et production restent à `NO`, et toute reprise future exige une
  nouvelle décision propriétaire ainsi que le réexamen d'ADR-SS-002.
- validation propriétaire explicite de WO-021 reçue le 2026-08-31, avec readiness locale, garantie
  du délai minimal et qualifications loopback J3/J4/J5 reconnues ; WO-021 passe à `VALIDATED` et
  est déplacé vers les Work Orders terminés ; WO-019 reste `STOPPED`, les vingt tentatives restent
  gelées, réseau, reprise et nouveau go restent à `NO`, et `J9_FINAL_DECISION=NOT_TAKEN` ;
- déclenchement séparé du réexamen d'ADR-SS-002 exigé par sa section 9 avant toute éventuelle
  décision de reprise ; la clôture de WO-021 ne vaut ni acceptation d'une révision de l'ADR, ni
  manifeste, ni go fournisseur.
- ouverture de `WO-SS-20260831-022` sur `codex/j9-adr002-reexamination` depuis la clôture validée
  de WO-021, pour réaliser exclusivement le réexamen documentaire d'ADR-SS-002 avant toute reprise ;
  aucun appel fournisseur, reprise, go, intégration ou production n'est autorisé ;
- conclusion du réexamen : v1.0 est `ACCEPTED_CONSUMED_AND_TERMINATED_BY_STOP` et non réutilisable,
  car la reprise après incident et le fence global exécutoire WO-021 déclenchent tous deux le
  paragraphe 9 ; proposition d'un draft v1.1 sans effet exécutoire ;
- matrice propriétaire du draft v1.1 : continuation D2/D3 recommandée avec `8` nouveaux appels
  maximum et `28` cumulés, nouvelle série complète alternative avec `38` nouveaux et `58` cumulés,
  ou absence de reprise ; la voie minimale ne requalifie pas le rapport historique `STOPPED` et
  borne la consolidation à `PARTIAL_BOUNDED` sous les règles actuelles ;
- revue officielle rafraîchie le `2026-08-31T10:18:51.8559197Z` : restrictions et absence de
  permission/licence/quota applicable toujours constatées ; avant tout go restent requis le choix
  puis l'acceptation propriétaire de v1.1, une readiness fraîche, une nouvelle sauvegarde/restauration
  V28 post-arrêt, un manifeste et un nouveau go à usage unique.
- sélection propriétaire reçue le `2026-08-31T11:07:13.3823864Z` du profil
  `RESTART_FULL_D1_D2_D3` pour le draft ADR-SS-002 v1.1 : 38 nouvelles tentatives au maximum, 58
  cumulées avec les vingt tentatives v1.0 gelées, rejeu D1 inclus et nouveau rapport autonome ;
  l'acceptation v1.1 reste distincte et réseau, campagne et go restent à `NO` ;
- la nouvelle série complète n'est pas une reprise de WO-019 : son statut `STOPPED`, la campagne
  v1.0, ses vingt tentatives et son rapport historique restent immuables ; conformément à la règle
  un Work Order/worktree par campagne, un nouveau Work Order et un nouveau worktree seront requis
  après acceptation, mais ne sont pas encore ouverts ;
- indisponibilité déclarée du propriétaire pour les huit séquences unitaires d'interrogation ; aucun
  autre opérateur humain n'était alors désigné et l'état était `BLOCKED_PENDING_OPERATOR_DECISION`.
  Aucune délégation à Codex ne découle de ce constat ; le précédent WO-017 permet toutefois de
  proposer séparément une exécution ponctuelle `CODEX_LOCAL_UI` sur instruction propriétaire
  explicite. Script, orchestration, scheduler et modification runtime restent hors autorisation.
- acceptation propriétaire explicite d'ADR-SS-002 v1.1 reçue le
  `2026-08-31T12:02:37.0545305Z` pour le draft `fc18f3d`, SHA-256
  `c1fc398703585e0dcc3ccf36d880f0a009d8366e5492835b9f244f3437ebdba4` ; le modèle ponctuel
  `CODEX_LOCAL_UI` est sélectionné mais son exécution reste à `NO` ;
- WO-022 validé et déplacé vers les Work Orders terminés. À ce stade, WO-019 reste `STOPPED`,
  WO-023 n'est pas encore ouvert et campagne, réseau, go, intégration et production restent non
  autorisés ;
- instruction propriétaire de lancement de la nouvelle campagne à usage unique observée le
  `2026-08-31T13:08:48.0887445Z` : ouverture de WO-023 sur
  `codex/j9-provider-robustness-v11` avec worktree dédié ; `CODEX_LOCAL_UI` est autorisé comme acteur
  conditionnel de ce WO. L'instruction permet la préparation hors ligne, mais ne constitue pas le
  go fournisseur lié à un manifeste et une fenêtre UTC : campagne, réseau et consommation restent
  à `NO`/`NOT_GRANTED` avant readiness, sauvegarde/restauration, manifeste gelé et décision finale.
- après deux désaccords interactifs de phrase secrète, diagnostic de deux pipelines natives
  `pg_dump | age` dont le producteur et sa session PostgreSQL sont restés suspendus après la sortie
  anticipée du consommateur ; nettoyage ciblé vérifié à zéro processus, session `pg_dump`, fichier
  de sauvegarde et base temporaire résiduels, sans aucun appel fournisseur ;
- autorisation propriétaire et ouverture de `WO-SS-20260831-024` sur
  `codex/j9-backup-pipeline-fail-closed-cleanup`, depuis `2d9c2dd7`, pour corriger et qualifier
  exclusivement hors ligne l'annulation coordonnée, les timeouts et le nettoyage de l'arbre natif.
  WO-023 reste suspendu, réseau fournisseur, reprise de campagne et nouveau go restent à `NO` ; la
  nouvelle sauvegarde/restauration WO-023 avec phrase auto-générée ne devient autorisée qu'après
  validation propriétaire de WO-024 ;
- implémentation locale `969f29d` d'un superviseur binaire fail-closed partagé par les voies
  `pg_dump -> age` et `age -> pg_restore` : porte préalable à la création de la cible, Job Objects
  Windows `KILL_ON_JOB_CLOSE`, suivi indépendant des statuts, échéance bornée commune, nettoyage
  des arbres, sessions exactes `PGAPPNAME`, fichiers `.partial-*` et base de restauration isolée ;
- qualification WO-024 hors ligne et Docker réussie : octets et SHA-256 identiques, échecs des deux
  côtés, troncature, timeout, annulation et vrai `CTRL_BREAK` refusés fail-closed, zéro processus,
  session PostgreSQL, base temporaire, fichier partiel ou listener résiduel. Le double `age` ne
  transporte aucun secret et ne simule pas le TTY ou l'auto-génération réelle ; aucune mauvaise
  phrase humaine n'est requise ;
- vérifications finales réussies : 945 tests standards, 67 tests d'intégration, Java 25, PostgreSQL
  18.4/V28, `Verify-Local.ps1 -WithIntegrationTests`, Compose, scans de portée et de secrets. WO-024
  avait atteint `READY_FOR_OWNER_REVIEW` au commit `e0beee0` ; WO-023, réseau fournisseur, campagne,
  nouveau go, intégration et production restaient interdits ;
- validation propriétaire explicite de WO-024 reçue : qualification fail-closed reconnue,
  déplacement vers les Work Orders terminés autorisé et nouvelle sauvegarde/restauration WO-023
  avec phrase `age` auto-générée désormais autorisée, mais pas encore exécutée au moment de cette
  clôture ;
- intention propriétaire d'enchaîner ensuite sur la campagne fournisseur enregistrée sans élargir
  le bloc exécutoire : réseau, reprise WO-023 et nouveau go restent à `NO` jusqu'à qualification de
  la sauvegarde, gel du manifeste et nouveau go global lié à une fenêtre UTC.
- fast-forward du correctif WO-024 validé dans le worktree WO-023, puis readiness fraîche sur
  `8b91bf8` : Maven standard `945/0/0/4`, intégration `67/0/0/0`, `Verify-Local` avec réseau
  fournisseur `NO`, Compose valide et Flyway V28 ;
- qualifications Playwright J3, J4 et J5 strictement loopback réussies, chacune avec `21/0/0/0`
  tests worker puis `14/0/0/0` tests Chromium ; J5 confirme les départs et arrivées `>= 3 s`, les
  fences inter-worker, zéro requête après arrêt pendant délai, les scans canari/sensible et l'absence
  d'accès fournisseur ; superviseur `40/40`, coordinateur `8/8` ;
- audit final de readiness : connecteur primaire `SAFE`, dernière tentative fournisseur à
  `2026-08-31T07:59:01.092327Z`, port 8087, processus possédés, sandbox résiduel et artefacts
  navigateur versionnables à zéro. WO-023 passe à `READY_FOR_V28_BACKUP_RESTORE` ; la tentative
  chiffrée avec phrase `age` auto-générée est autorisée mais reste non exécutée, et réseau,
  campagne, nouveau go, intégration et production restent à `NO`.
- tentative unique post-WO-024 exécutée avec phrase `age` auto-générée : chiffrement terminé avec
  producteur et consommateur à `EXIT_0`, copie à EOF et nettoyage local `PASS`, puis arrêt
  fail-closed sur une confirmation de nettoyage PostgreSQL exacte échouée ou invérifiable ; ce
  point précède toute restauration, publication finale et création de manifeste ;
- audit post-incident : trois observations successives à zéro pour la session exacte et toutes les
  sessions J6 possédées, zéro processus exact, fichier final/partiel, base temporaire ou listener
  8087 ; ces résultats prouvent le confinement et non une qualification rétroactive ;
- écart de qualification établi entre le défaut runtime de `5 000 ms` et les quatre parcours Docker
  WO-024 exécutés avec `10 000 ms`, tandis que la cause interne est masquée par le message final
  générique. WO-023 passe à `BLOCKED_AFTER_BACKUP_CLEANUP_UNCONFIRMED` ; la tentative autorisée est
  consommée et un Work Order runtime distinct ainsi qu'une nouvelle décision propriétaire sont
  requis avant tout nouvel essai ; fournisseur, campagne, manifeste, go, intégration et production
  restent non autorisés.
- contre-validation standard post-incident : `945` tests, un échec, zéro erreur et quatre skips ;
  le seul scénario rouge est la commande native bornée J6 dont la preuve PID n'a pas été créée dans
  sa fenêtre de `1 500 ms`. Aucun code runtime/test n'a changé depuis WO-024, la cause reste
  indéterminée et aucun lien causal avec l'incident PostgreSQL n'est déduit ;
- audit Windows complémentaire : une entrée synthétique antérieure exécutant uniquement un sleep
  de 30 secondes reste visible en état `Unknown` par `tasklist`/CIM, avec parent absent, tout en
  étant inaccessible aux API ordinaires de processus ; elle n'appartient pas à la tentative réelle
  de sauvegarde et son attribution au verify courant n'est pas établie ; un ancien répertoire
  temporaire synthétique, créé plus de quatre heures avant le verify rouge, subsiste aussi hors
  dépôt. La qualification runtime devient `NOT_REPRODUCIBLE` et le futur Work Order devra durcir la
  preuve PID/absence multi-API sans relâcher les garanties fail-closed.
- autorisation propriétaire et ouverture de `WO-SS-20260831-025` sur la branche/worktree dédiée
  `codex/j9-backup-cleanup-proof-hardening`, depuis le commit d'incident `5014902` ; le périmètre
  couvre uniquement la confirmation PostgreSQL effective, le parsing scalaire et la cause sanitée,
  le handshake PID, la corroboration multi-API/états fantômes et le nettoyage exact du temp root ;
- qualifications WO-025 limitées au local, loopback et Docker local. WO-024 reste gelé, WO-023
  reste bloqué et une nouvelle sauvegarde réelle exigera une décision propriétaire séparée ; toute
  terminaison par PID seul, acquisition fournisseur, nouveau go, intégration et production restent
  à `NO`.
- correctif PostgreSQL WO-025 au commit `d019be2` : défauts effectifs distincts de `5 000 ms` pour
  le nettoyage natif et `10 000 ms` pour l'observation, `PGAPPNAME` exact sensible à la casse,
  `ON_ERROR_STOP=1`, couple ciblé/réussi issu d'un même CTE, parsing canonique strict, trois zéros
  frais et idempotence uniquement après preuve stabilisée ; un ancien compte après terminaison ne
  peut plus produire un faux `SESSION_REMAINING` ;
- classifications Docker, SQL, parsing, observation, session restante et nettoyage de processus
  désormais distinctes et fail-closed ; les causes internes publiables sont sanitées et agrégées
  avec les échecs fichier/temp sans restituer stderr brut, secret, `.env`, phrase `age` ou payload ;
- preuve native Windows fondée sur un handshake `CurrentUserOnly` lié par nonce avant le budget
  d'exécution, identité cible `PID + StartTime`, Job Object `KILL_ON_JOB_CLOSE` et corroboration
  bornée .NET/Toolhelp/CIM/`tasklist` ; un état fantôme est classé ambigu et réobservé dans une
  borne, sans jamais autoriser une terminaison par PID seul ;
- temp root synthétique limité à l'enfant canonique exactement possédé du temp système : marqueur
  atomique de chemin/nonce, refus du parent, des globs, chemins extérieurs et reparse points,
  suppression littérale et preuve d'absence avec préservation du frère sentinelle ; aucune
  attribution ou suppression d'un résidu historique non possédé ;
- qualification finale locale sur le commit fonctionnel : un parcours direct et cinq répétitions
  Maven hors ligne, puis un parcours direct et trois répétitions Maven avec Docker local, tous
  verts ; portes complètes à `946/0/0/5` tests standards, `67/0/0/0` intégrations, Verify-Local avec
  intégrations, Compose et contrôle du diff passés, zéro secret à haute confiance, listener,
  processus/session/base temporaire/fichier partiel/temp root possédé résiduel ou appel fournisseur ;
- WO-025 atteint `READY_FOR_OWNER_REVIEW`, pas `VALIDATED` : la décision propriétaire et le
  déplacement vers `completed` restent requis. WO-023 reste bloqué ; un retry de sauvegarde exige
  une décision propriétaire séparée, et campagne, réseau, nouveau go, purge primaire, intégration
  et production restent à `NO`.
- validation propriétaire explicite de WO-025 reçue à `2026-08-31T22:35:48.6609979Z` : readiness
  locale, durcissement de la preuve et invariants fail-closed/exact ownership reconnus, avec
  terminaison par PID seul confirmée absente et déplacement autorisé vers les Work Orders
  terminés ;
- clôture documentaire de WO-025 sans effet implicite sur WO-023 : le retry de
  sauvegarde/restauration exige toujours une décision propriétaire séparée et postérieure ;
  campagne fournisseur, réseau, nouveau go, purge primaire, intégration et production restent à
  `NO`.
- décision propriétaire séparée reçue à `2026-08-31T22:46:53.9166240Z` pour un unique nouvel essai
  local chiffré de sauvegarde/restauration WO-023 ; le parcours hérité reste la génération native
  interactive par `age`, avec phrase confinée au terminal opérateur. Le jeton n'est consommé qu'à
  la première invocation réelle après préflight vert ; toute sortie du script le consomme. La
  vérification standard fraîche est verte à `946/0/0/5`, sans différence de code runtime depuis le
  commit qualifié WO-025 ; un diff exact confirme aussi l'absence de changement du runtime et de la
  configuration fournisseur depuis la readiness WO-023 au commit `8b91bf8`, ce qui permet de
  reporter ses preuves loopback J3/J4/J5 sans nouvel accès fournisseur. Cette décision ne réactive
  ni la campagne fournisseur, ni le réseau, ni un go global, ni la purge primaire, l'intégration ou
  la production.
- unique invocation réelle WO-023 ensuite qualifiée : archive `age` de `7 268 470` octets au
  SHA-256 `526f2faa22f8da0506194030f6f7fbe0966e65550310375d055041d4ea257041`, manifeste
  de `2 203` octets au SHA-256
  `7994a3b2ec6ced505b36514ad0b5eb113c5c1b30a0a77811e4d1a7ae80a2eaa9`, restauration V28
  marquée `restoreQualified=true`, snapshot maximal `814`, `781` occurrences et `116` tentatives
  J8 couvertes ; toutes les valeurs et quatre empreintes source/restauration concordent, intégrité
  brute et mismatches sont à zéro ;
- postflight WO-023 vert : état primaire identique au manifeste source, zéro purge, session J6,
  base temporaire, processus `age`/host natif, listener 8087 ou fichier partiel. Le jeton est
  consommé `1_OF_1` ; la prochaine porte est le gel du manifeste et la corroboration indépendante
  du ledger, tandis que campagne, réseau, go global, intégration et production restent à `NO`.
- corroboration indépendante ensuite verte : 20 tentatives historiques WO-019 retrouvées avec la
  répartition `15+1+1+1+1+1`, zéro doublon d'unité et zéro tentative WO-023 ; total append-only 116,
  dernier départ à `2026-08-31T07:59:01.092327Z` ;
- manifeste de campagne WO-023 gelé au SHA-256
  `ff909f298f7d3c99b1027c071ac4d783a19529f6241b37941151c2c08c418a9e`, avec runtime, readiness,
  sauvegarde, corpus, ordre A1..B4, ledgers et conditions d'arrêt. WO-023 devient
  `READY_FOR_GLOBAL_OWNER_GO`, ce qui ne vaut ni go courant, ni réseau ou campagne autorisés.
- go propriétaire global reçu pour le commit `f632fd0`, le manifeste gelé et la fenêtre exacte
  `[2026-08-31T23:45:00Z,2026-09-01T00:45:00Z)`, acteur ponctuel `CODEX_LOCAL_UI`, plafond 38
  nouvelles tentatives et 58 cumulées, sans remplacement, purge primaire, intégration ou production ;
- exécution séquentielle complète A1..B4 : quinze pages J3, une découverte tournoi, trois détails
  J4 et neuf familles J5, soit 28 tentatives ; 28 réponses HTTP 200, 28 parsings, 24 insertions et
  quatre déduplications après réponse fraîche, zéro cache hit, 404, autre HTTP, retry ou incident ;
- D2 et D3 qualifiés `COMPLETE · 100 %` sur les trois familles J5 ; D1 conserve des lacunes
  optionnelles bornées à `PARTIAL · 91 %` pour les incidents et `PARTIAL · 99 %` pour les
  compositions, séparées du verdict de robustesse technique ;
- cadence qualifiée avec minimum `3 000,277 ms` entre départs de tentative et `3 088 ms` entre
  `requested_at` fournisseur, zéro intervalle inférieur à trois secondes, concurrence et overlap à
  zéro ; 28/38 nouvelles tentatives, 48/58 cumulées et dix tentatives expirées non réutilisables ;
- double export J8 post-campagne byte-identique sur deux exécutions de 15 696 octets, SHA-256
  `76d1983dc466356de033efae859822b005aa4e77f3fe19403619ff8e2c240580`, même hash de population
  `c8c19ddc3ece1497c26e3348ef78a736f1eaf8fe0b0980801d077f97ab2bf399`, couverture
  `FULL_ATTEMPT_LEDGER` et zéro appel réseau ;
- postflight vert : connecteur `SAFE`, circuit `LOCKED`, flags fournisseur à `false`, zéro listener
  8087, application, worker, navigateur possédé, session/base J6 ou artefact Playwright interdit ;
  vérifications finales à 946 tests standards et 67 intégrations sans échec/erreur, Flyway V28,
  Compose, diff et contrôle des secrets réussis ;
- rapport autonome `J9-WO023-PROVIDER-ROBUSTNESS-CAMPAIGN-20260901` au SHA-256
  `1c6a97f872d5621efcaffa505d16a7724f81816891aa0a9a990a0abec56e87c1`, classé `PASS`. La matrice recommande désormais
  `PREPARE_OPTIONAL_INTEGRATION`, sous confirmation propriétaire et avec permission officielle
  toujours `NOT_EVIDENCED` ; le go est consommé, WO-023 reste `READY_FOR_OWNER_REVIEW` et réseau,
  intégration, production et VPS courant restent interdits.
- validation propriétaire de WO-023, acceptation de la preuve `PASS` au SHA-256
  `1c6a97f872d5621efcaffa505d16a7724f81816891aa0a9a990a0abec56e87c1` et déplacement de WO-023
  vers les Work Orders terminés à `2026-09-01T05:49:58.398Z`, soit
  `2026-09-01T07:49:58.398+02:00` en Europe/Paris ; la décision J9 finale devient
  `PREPARE_OPTIONAL_INTEGRATION` et WO-018 est validé puis déplacé vers les Work Orders terminés.
  La permission officielle reste `NOT_EVIDENCED` ; ADR-SS-003 n'est pas créé et aucun appel
  fournisseur, nouvelle campagne, implémentation, production ou VPS courant n'est autorisé.

### J5 — correctif borné V15 des actions vides en tirs au but

- ouverture de `WO-SS-20260830-017` sur `codex/j8-incidents-v15`, depuis la preuve J8 partielle
  `cfd536e`, après revue de l'ADR-SS-001 v1.4 : aucun endpoint, allowlist, worker, protocole IPC,
  transport ou garde réseau ne change ;
- ajout de `event-incidents-v15`, héritier intégral de V14, qui assimile une propriété
  `footballPassingNetworkAction` absente à un tableau exactement vide uniquement dans une séance
  terminale non minutée déjà cohérente selon V12, sans déduire de minute ;
- rejet conservé pour JSON `null`, les autres types, les tableaux non vides incohérents, les
  séquences ou scores contradictoires et toute séance mêlant une tentative réellement minutée à
  une tentative non minutée ;
- câblage cohérent de V15 sur la voie fournisseur gardée, les imports locaux unitaire et
  multi-match et la provenance des observations ; V12 à V14 restent inchangés dans leur
  comportement historique ;
- migration append-only V28 ajoutant seulement V15 à la contrainte fermée de parseur J5, sans DML,
  colonne, table, backfill ou réécriture V1–V27 ; scripts J6 alignés sur la version courante sans
  changement du périmètre de sauvegarde, de purge ou de fingerprint ;
- 76 tests ciblés parseurs/services et l'upgrade Testcontainers V27→V28 sont verts, sans appel
  fournisseur ; une fixture synthétique minimisée couvre les formes positives et négatives sans
  copier le payload réel ;
- sonde PostgreSQL read-only des octets exacts du snapshot 717 : SHA-256 inchangé,
  `PARSED/PARTIAL · 91%`, 35 incidents, zéro problème et 18 warnings sous V15, sans reparse
  persistant, observation ou mutation J8 ;
- portes finales vertes après le dernier changement : 928 tests standards, 4 ignorés prévus,
  67 tests PostgreSQL/Testcontainers, `Verify-Local.ps1 -WithIntegrationTests`, Compose et
  `git diff --check`, avec tous les drapeaux réseau à `false`, aucun listener 8087, aucun processus
  application/worker et aucun appel fournisseur ;
- après la readiness à zéro appel, go propriétaire distinct puis délégation explicite à Codex
  d'une seule campagne locale sur `16691018`, sans modification de `.env` ;
- campagne `COMPLETED_LOCKED` après trois appels ordonnés, sans retry, fallback, import ni seconde
  campagne : statistiques `COMPLETE · 100%` (`716`/`322`), incidents V15 `PARTIAL · 91%`,
  `164/179` signaux et 35 incidents (`717`/`324`), puis compositions `PARTIAL · 99%`
  (`720`/`325`) ;
- réobservation byte-identique de la variante tableau vide par déduplication vers le snapshot 717 :
  la nouvelle observation V15 est append-only et la classification historique V14
  `SCHEMA_INCOMPATIBLE` n'est pas réécrite ;
- création normale d'une campagne ledger J5 corrective ; la fenêtre benchmark J8 gelée, ses 19
  tentatives, son hash et son résultat `PARTIAL` restent inchangés. Une agrégation dynamique de
  tout l'historique peut en revanche inclure le nouveau ledger ;
- application arrêtée après le terminal, activation process-scoped terminée et `.env` inchangé
  avec les réseaux bloqués ; redémarrage inerte contrôlé avec J5 `LOCKED` et préparation
  désactivée, puis nouvel arrêt sans listener résiduel ;
- décision propriétaire distincte après constat du test fonctionnel concluant : WO-017 passe à
  `VALIDATED` et rejoint `docs/work_orders/completed/`, sans instance locale active ; le go reste
  consommé et aucun appel supplémentaire, push, PR ou fusion n'est autorisé par cette clôture.

### J8 — benchmark local validé

- ouverture de `WO-SS-20260829-016` sur la branche `codex/j8-benchmark`, depuis la baseline propre
  `67268d805a4ba8c7d4706be7c18f6ff78d3ec1fd`, après revue de l'ADR-SS-001 v1.4 : aucun endpoint,
  aucune allowlist et aucun élément du protocole worker Playwright v5 ne changent ;
- ajout de la migration append-only V27 et d'un ledger immuable séparant campagnes, unités,
  tentatives directes et résultats terminaux, sans rétrocréer de coût d'appel historique ;
- ajout des niveaux de preuve `FULL_ATTEMPT_LEDGER`, `RESPONSE_ONLY` et `LEGACY_BASELINE`, avec
  `NOT_MEASURED` dès qu'un dénominateur exact ne peut pas être prouvé ;
- ajout de la page HTML locale en lecture seule `GET /benchmark`, de l'agrégation reproductible et
  de l'export Markdown explicite sous `exports/j8/`, tous sans appel fournisseur ni JavaScript ;
- extension de la preuve sauvegarde/restauration J6 aux cinq tables J8 via comptes et fingerprint
  déterministe, sans les inclure dans la purge des payloads bruts ;
- readiness technique rejouée à zéro appel fournisseur : 912 tests standards et 66 tests
  PostgreSQL/Testcontainers sans échec, `Verify-Local`, Compose silencieux et diff à `PASS`, deux
  exports Markdown byte-identiques, puis contrôle visuel desktop/étroit sans JavaScript ni
  débordement horizontal global ;
- passage initial de la phase applicative à `J8-BENCHMARK-READY-FOR-HUMAN-QUALIFICATION`, avant tout
  appel réel ; cette readiness reste la baseline technique de la campagne ultérieure ;
- campagne prospective exécutée le 2026-08-30 après go propriétaire distinct, dans la fenêtre
  exclusive `[2026-08-30T03:39:12.086771Z,2026-08-30T04:32:04.339732Z)` sur l'événement fournisseur
  `16691018`, avec 19 tentatives sur le plafond absolu de 30 : quinze pages J3, une découverte
  tournoi et J4 phase 2 terminés, puis statistiques J5 parsées et complètes ;
- arrêt terminal J5 sans retry sur `SCHEMA_INCOMPATIBLE` pour les incidents ; l'unité compositions
  est conservée `NOT_REACHED_AFTER_TERMINAL_FAILURE` sans tentative, sans import, fallback ou
  seconde campagne ;
- analyse locale de régression : l'ancienne preuve incidents reste acceptée par V14, tandis que la
  nouvelle réponse représente quatorze tirs au but non minutés par des tableaux d'actions vides que
  le contrat strict hérité de V12 ne reconnaît pas encore. Le parseur, le worker Playwright et le
  transport sont inchangés par J8 et l'instrumentation ne transforme pas le payload ;
- double export de la fenêtre de campagne à mêmes `from/to/asOf`, byte-identique, 15 201 octets,
  SHA-256 `d901790d1f05ddd32b92821bee51f11ae3e688ca3d929af36f667ac026b2934c`, hash de population
  `da158fb04c8dc113a56e94e2bc7da6ad27278111af5cf8179b7476e5d8f1cc95` et zéro appel fournisseur ;
- résultat J8 conservé `PARTIAL` et Work Order actif : configuration reverrouillée, application et
  worker arrêtés, revue humaine ciblée non terminée, aucun rapport final gelé, aucune clôture J8 ou
  décision J9. Le correctif technique relève désormais du Work Order séparé WO-017 ; toute
  qualification fournisseur ou nouvelle campagne exige toujours un go distinct ;
- après validation de WO-017, second go propriétaire distinct consommé par une seule fenêtre
  exclusive `[2026-08-30T09:24:51.0887925Z,2026-08-30T09:44:03.2695965Z)`, sans mutation de
  `.env`, retry, fallback, import ou troisième campagne ;
- parcours complet terminé avec quatre campagnes `COMPLETED`, vingt unités, vingt tentatives,
  vingt réponses HTTP 200 et vingt issues `PARSED` : quinze pages J3, une découverte tournoi,
  J4 phase 2, puis statistiques, incidents V15 et compositions J5 dans l'ordre ;
- double export non-Web de 15 202 octets, strictement byte-identique et à zéro appel fournisseur,
  SHA-256 `ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25`, hash de population
  `c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726` et état `MEASURED` sous
  `FULL_ATTEMPT_LEDGER` ;
- dossier ciblé exploitable mais non strictement complet : statistiques `COMPLETE · 100%`,
  incidents V15 `PARTIAL · 91,62%` et compositions `PARTIAL · 99,03%`; coûts exacts de seize
  appels de découverte, quatre appels marginaux et vingt appels effectifs par dossier exploitable ;
- arrêt de l'instance qualifiée, contrôle d'un redémarrage inerte avec tous les connecteurs,
  Playwright, polling et refresh à `false`, puis nouvel arrêt et port 8087 libre ;
- dernier export non-Web de gel exécuté avec les mêmes `from/to/asOf`, tous les connecteurs forcés à
  `false` et zéro appel fournisseur : bloc automatique toujours égal à 15 202 octets et SHA-256
  `ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25` ;
- revue ciblée des trois archétypes conclue et acceptée avec
  `CONTROL_SOURCE_ABSENT` / `EXTERNAL_COMPARISON_ABSENT` : accessibilité et stabilité à `PASS`,
  complétude, fraîcheur, efficacité et risque à `PARTIAL`, autres dimensions non observables à
  `NOT_MEASURED`, sans décision J9 ;
- gel du rapport final sous `docs/benchmark/J8-BENCHMARK-REPORT-20260830.md`, avec préfixe
  automatique byte-identique et section humaine séparée; décision propriétaire de clôture
  consignée après audit formel des critères ;
- portes finales rejouées après les changements de clôture, configuration locale sûre et aucun
  appel fournisseur; phase applicative passée à `J8-BENCHMARK-VALIDATED` et WO-016 déplacé sous
  `docs/work_orders/completed/`, sans troisième campagne, push, PR ni fusion.

### J5 — migration Playwright des donnees evenement validee

- implementation de `WO-SS-20260827-015` sur la branche dediee
  `codex/j5-playwright-event-data`, fondee sur le runtime commun valide par WO-013 et WO-014 et
  conforme a l'ADR-SS-001 v1.4 ;
- remplacement du transport J5 direct par `ProviderJ5EventDataPlaywrightTransport`, sans
  `RestClient`, FlareSolverr ni fallback, avec un worker, un `BrowserContext` non persistant et une
  lease dédiés à la même campagne pour `EVENT_STATISTICS`, `EVENT_INCIDENTS` puis
  `EVENT_LINEUPS` ;
- passage du protocole worker a v5 pour les routes J5 fermees, fidelite des statuts et octets issus
  de `Response.body()`, poursuite apres un `404` raw-first et arret terminal sans retry pour tout
  autre incident ;
- raccordement de l'arret J5 a la campagne Playwright exacte : acquittement borne, annulation de
  l'appel en vol, aucune famille suivante et nettoyage de l'arbre attribue sans arret par nom ;
- latch d'arret global J5 independant de l'historique terminal : un succes conserve son etat et son
  code `COMPLETED_LOCKED`/`COMPLETED`, mais aucune nouvelle preparation n'est acceptee avant le
  redemarrage du processus ;
- fermeture fail-closed : une erreur de nettoyage survenue avant toute mutation reste retentable
  sur la meme campagne, la lease partagee n'est liberee qu'apres nettoyage confirme et un echec
  persistant apres mutation bloque toute campagne J3/J4/J5 jusqu'au redemarrage du processus ;
  l'arret exact reste routable sans reecrire l'issue terminale historique ;
- lanceur J5 fonde sur un `clean package` obligatoire avec verification de l'absence du bytecode
  historique direct, et preuves d'hygiene renforcees par scan XML decode des rapports Maven et
  recherche d'un canari aleatoire par run dans le contenu effectif de toutes les racines runtime
  isolées ; seules trois ressources exactes embarquees par le driver Playwright sont distinguees des
  artefacts generes, apres comparaison octet pour octet avec le classpath ;
- cache fournisseur J5 declare `NOT_APPLICABLE` : aucune lecture ni ecriture de cache pendant une
  campagne, tandis que les imports locaux unitaires et multi-match restent strictement sans worker
  ni transport ;
- tests cibles du transport verts (`20` tests), exclusivite de lease J5/J3/J4 verte (`1` test) et
  qualification Chromium loopback ciblee verte (`14` tests), dont une sequence `200/404/200` dans
  un seul contexte et un arret pendant `INCIDENTS` avec zero `LINEUPS` et zero residu ; aucun acces
  SofaScore n'a ete effectue ;
- conservation intacte de la migration append-only V26 et du parseur `event-incidents-v14` de
  WO-012 ; aucune migration n'est ajoutee par WO-015 ;
- readiness technique finale verte : 765 tests standards (2 ignores), 53 tests PostgreSQL,
  11 tests de protocole/securite et 14 tests Chromium loopback, puis
  `Verify-Local.ps1 -WithIntegrationTests` en `PASS`, sans appel SofaScore ;
- qualification fonctionnelle proprietaire du `2026-08-29` sur Lille - Paris Saint-Germain
  (`providerEventId=16310930`) : terminal J5 `COMPLETED_LOCKED`, trois appels ordonnes, zero import
  local et snapshots `603`, `604`, `605` complets pour statistiques, incidents et compositions ;
- non-regression J4 confirmee dans la meme session avec `EVENT_DETAILS` conserve dans le snapshot
  `606` et terminal `COMPLETED_LOCKED` ;
- correction d'une indisponibilite du catalogue J3 provoquee par deux candidats dont les libelles
  fournisseur contenaient une tabulation ou un saut de ligne : ces candidats sont maintenant
  exclus individuellement, sans sanitisation ni modification des snapshots bruts, et un test de
  regression couvre le maintien des options sures ;
- reprise humaine J3 verte sur 18 pages : catalogue `AVAILABLE`, `1 428` tournois actionnables,
  `338` occurrences exclues, liste deroulante visible, puis qualification Ligue 1 `5 / 5` dans le
  snapshot `643` ;
- `WO-SS-20260827-015` passe a `VALIDATED` et rejoint `completed`. La preuve est consommee et
  n'autorise aucun appel fournisseur supplementaire. Les huit cles locales ont ete reverrouillees,
  J4 et J5 ont ete constates `LOCKED` apres redemarrage, puis l'application de controle a ete
  arretee sans JVM attribuee restante.

### J4 — migration Playwright de `EVENT_DETAILS` validée

- ouverture de l'implementation de `WO-SS-20260827-014` sur une baseline standard verte de 691
  tests, 0 echec, 0 erreur et 2 ignores, sans appel fournisseur ;
- amendement ADR-SS-001 v1.4 approuvant la persistance du `404` comme
  `ENDPOINT_UNAVAILABLE` sans parsing ni retry, la poursuite de la seconde cible fixe en phase 1 et
  le resultat `COMPLETED_UNAVAILABLE` en phase 2 ;
- exception cache strictement bornee de phase 2 : un rafraichissement manuel confirme sur une
  identite canonique imposee cote serveur realise au plus un GET sans lecture du cache ;
- architecture cible fondee sur le worker, le superviseur, l'IPC, la lease et le nettoyage communs
  de WO-013, avec un contexte neuf par campagne et aucune duplication de runtime J4 ;
- remplacement du transport direct J4 par l'adaptateur Playwright commun, campagne phase 1 partagee
  et paresseuse, campagne phase 2 neuve sans lecture cache, selection canonique imposee cote serveur
  et arret cible avec nettoyage prioritaire ;
- readiness finale verte : 728 tests standards, 52 tests PostgreSQL, 10 tests de protocole/securite
  et 12 tests Chromium `EVENT_DETAILS`, sans appel fournisseur pendant les validations techniques ;
- qualification fonctionnelle declaree concluante par le proprietaire le `2026-08-28` :
  rafraichissement J4 phase 2 `COMPLETED`, snapshot conserve et reverrouillage terminal ;
- controle de non-regression J3 Playwright concluant sur la collecte paginee de 12 pages et la
  decouverte tournoi ; `WO-SS-20260827-014` passe a `VALIDATED` et rejoint `completed`.

### J3 — implémentation du transport fournisseur manuel Playwright

- remplacement, dans le Work Order `WO-SS-20260827-013`, des deux adaptateurs J3
  `SCHEDULED_EVENTS` et `TOURNAMENT_SCHEDULED_EVENTS` par un worker Playwright JVM enfant commun,
  sans URI libre ni fallback `RestClient` ou FlareSolverr ;
- ajout des profils Maven `provider-playwright-runtime` et
  `provider-playwright-local-qualification`, avec Playwright Java `1.62.0` et cache Chromium local
  ignoré sous `.tmp/provider-playwright-browsers`. Le build standard reste sans source ni
  dépendance Playwright et `SOFASCORE_PLAYWRIGHT_ENABLED=false` conserve l’inertie par défaut ;
- un worker, un Chromium headless et un `BrowserContext` non persistant neufs par campagne ;
  JavaScript et service workers bloqués, cookies effacés, téléchargements, popups, WebSockets,
  routes secondaires, redirections et retries interdits, sans profil, proxy, `storageState`, HAR,
  trace, vidéo ou capture ;
- IPC privé et authentifié sur `127.0.0.1`, payload borné à 5 Mio, réponse issue exclusivement du
  statut, du `Content-Type` borné et des octets de `Response.body()`, sans reconstruction DOM ;
- lease exclusive couvrant toute la pagination J3 et délai minimal partagé de trois secondes entre
  départs fournisseur ; les imports JSON locaux et les cache hits ne démarrent pas Playwright ;
- arrêt ciblé de la campagne et de son arbre exact par PID et instant de création, avec bornes de
  500 ms pour l’acquittement, 2 s pour l’annulation et 5 s pour le nettoyage, sans arrêt par nom ;
- traitement raw-first des réponses, y compris HTTP `404` persisté et classé
  `ENDPOINT_UNAVAILABLE` avant tout parsing, sans retry, fallback ou page suivante ;
- scripts explicites d’installation et de qualification loopback sur `127.0.0.1`. Cette
  qualification locale n’autorise aucun appel fournisseur et ne clôt pas le Work Order.
- correction issue du premier essai opérateur J3 : le superviseur impose désormais
  `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` dans l'environnement assaini du worker, afin que
  `Playwright.create()` n'installe jamais implicitement Firefox ou WebKit avant le frame `READY` ;
  les lanceurs refusent aussi un cache sans marqueurs et exécutables Chromium complets ;
- la disparition attendue du PID racine après une trame terminale authentifiée est reconnue sans
  relâcher l'authentification des autres chemins de nettoyage. La preuve minimisée v6 distingue une
  ouverture de campagne échouée d'un GET réellement transmis et n'incrémente plus le compteur
  fournisseur avant l'émission effective de la requête.
- qualification humaine corrective du 28 août 2026 : `SCHEDULED_EVENTS` termine douze pages HTTP
  `200` dans l'ordre, snapshots 572 à 583, avec `hasNextPage=false` en page 12, zéro cache, import,
  retry, polling ou donnée de session et arrêt global réappliqué. Cinq actions distinctes
  `TOURNAMENT_SCHEDULED_EVENTS` conservent ensuite les snapshots 584 à 588, vérifient six rencontres
  canoniques sur Ligue 1, Premier League, LaLiga, Bundesliga et Serie A, puis rendent leurs liens J5
  disponibles sans saisie d'identifiant ;
- validation propriétaire et clôture de `WO-SS-20260827-013` après 17 appels humains confirmés.
  L'application est arrêtée gracieusement, sans listener `127.0.0.1:8087`, JVM applicatif ou worker
  Playwright résiduel. Le Work Order rejoint `docs/work_orders/completed`; aucun appel supplémentaire
  n'est autorisé par cette clôture.
### J5 — marqueur de prolongation live `Extra time`

- nouvelle règle incidents : le parseur versionné `event-incidents-v14` accepte exactement
  `incidentType="period"`, `text="Extra time"`, `isLive=true`, conserve le libellé brut dans le
  détail J5 et le distingue du marqueur terminal `ET` ;
- V6 à V13 restent immuables. V13 rejette encore la nouvelle valeur sur
  `$.incidents[0].text`, une valeur V14 explicitement associée à `isLive=false` reste
  `SCHEMA_INCOMPATIBLE`, et l'absence de `isLive` produit une complétude `PARTIAL` ;
- les parcours J5 direct, import JSON local unitaire et lot hors ligne utilisent désormais V14 ;
  aucune URI, permission réseau, boucle, retry ou automatisation navigateur n'est ajouté ;
- migration append-only `V26__j5_live_extra_time_period.sql` ajoutant uniquement V14 à la
  contrainte de version de parseur, sans colonne ni réécriture des observations V1–V25 ;
- fixture minimale anonymisée et tests dédiés couvrant la fidélité du SHA-256, la sentinelle
  `addedTime=999`, la minute 120, les voies directe et zéro appel, la compatibilité V13 ainsi que
  l'upgrade PostgreSQL V25→V26 ;
- qualification fonctionnelle propriétaire finale : deux imports locaux zéro appel sont
  `COMPLETE · 100%` sous `event-incidents-v14`, avec 24 incidents et `86/86` signaux puis
  28 incidents et `99/99` signaux. `Extra time` reste affiché à la minute 120 tandis que le score
  évolue de `1-1` à `1-2`. `WO-SS-20260827-012` est validé, fermé et déplacé vers `completed`.
### J5 — import hors ligne multi-match atomique

- correction issue de la recette Borussia Dortmund — FC Bayern München (`16248441`) : chaque
  famille de l'import J5 unitaire et multi-match accepte désormais exactement une preuve, soit un
  fichier JSON, soit une case « 404 observé ». La case crée sans réseau l'enveloppe canonique locale
  `{"error":{"code":404,"message":"LOCAL_OPERATOR_DECLARED_HTTP_404"}}`; fichier plus case,
  famille sans preuve, code libre et inférence depuis l'état du match restent refusés avant claim ;
- assistance de manifeste avant envoi : la page affiche les comptes fichiers, déclarations 404 et
  saisies sur `3N`, marque chaque preuve `FICHIER`, `404`, `CONFLIT`, `DOUBLON`, `INVALIDE` ou
  `À FOURNIR`, nomme les anomalies et désactive localement l'import tant que l'ensemble n'est pas
  exact. Le script ne lit que noms et tailles, reste de même origine et la validation serveur garde
  seule autorité ; les autres pages conservent `script-src 'none'` ;
- nouvelle page locale `/j5-import-batches`, liée depuis la recherche et les fiches J5, permettant
  de cocher de 1 à 25 UUID canoniques déjà présents pour une date et une zone, sans champ
  d'identifiant fournisseur libre ;
- plan déterministe conservé quinze minutes en mémoire, trié par `providerEventId`, lié aux
  observations canoniques courantes, aux trois noms attendus par match, à un SHA-256 versionné et
  à la phrase exacte `IMPORTER {N} MATCHS J5 HORS LIGNE {PLAN_SHA256}` ;
- multipart plat composé de 0 à 75 `batchFiles` et de déclarations `unavailable404`, pour exactement
  `3N` preuves strictes, 5 Mio par fichier et 25 Mio cumulés ; manifeste, tailles, contenu sensible,
  enveloppe 404, JSON, parseurs et état
  canonique sont tous contrôlés avant claim et avant écriture ; le seuil multipart mémoire de
  32 MB empêche le conteneur de créer un fichier temporaire avant ces contrôles, et la borne
  Tomcat de 82 parties autorise 75 preuves, les six champs du lot maximal et l'éventuelle sentinelle
  vide du sélecteur de fichiers ; toutes
  les parties natives sont énumérées, les six contrôles doivent être uniques et la disposition
  ASCII brute refuse `filename*`, les encodages et toute partie inconnue avant capture ;
- contrôle mémoire `J5OfflineBatchControlService` indépendant du contrôle réel, accessible avec
  `SOFASCORE_ENABLED=false` ou `true` lorsque le rafraîchissement et le polling sont désarmés et la
  conservation brute active. Les qualifications, la découverte, l'origine et l'allowlist peuvent
  rester armées ; un test de contexte reprend exactement la configuration combinée J3/J4 phase
  2/J5/découverte avec le connecteur activé ;
- réutilisation du processeur local J5 pour écrire les `3N` traitements dans l'ordre stable au
  sein d'une transaction PostgreSQL `REPEATABLE_READ` unique ; toute erreur tardive annule
  snapshots, occurrences et observations de l'ensemble du lot ;
- provenance `MANUAL_LOCAL_JSON_IMPORT`, déduplication V25 et occurrences J6 conservées sans
  migration nouvelle ; un réimport explicite peut dédupliquer les données tout en ajoutant les
  occurrences, et un snapshot dédupliqué encore `RAW_ONLY` est reclassifié dans la transaction,
  sans persister de plan ou d'historique propre au lot ;
- zéro transport, cache fournisseur ou coordinateur réseau, et aucun scheduler, watcher, retry,
  staging, archive, tâche de fond ou reprise après redémarrage ;
- validation corrective du 2026-08-22 : 64 tests ciblés, puis `clean verify` à 609 tests, 2 ignorés,
  et 51 tests PostgreSQL d'intégration sur PostgreSQL 18.4 et 25 migrations jusqu'à V25 ; le script
  `Verify-Local.ps1 -WithIntegrationTests` rend `PASS` et confirme zéro appel SofaScore. Les
  validations finales portent `clean verify` à 614 tests, 2 ignorés, et la suite PostgreSQL à
  52 tests. Le navigateur local valide
  `CONFLIT`, `DOUBLON`, le retour exact `FICHIER / 404 / 404`, les vues desktop/mobile sans
  débordement et zéro erreur console, sans soumettre le formulaire ; la CSP couvre aussi l'URL
  locale réécrite avec `;jsessionid` sans élargir les autres routes. La qualification humaine hors
  ligne complète reste en attente et le Work Order demeure actif ;
- première recette humaine nominale positive sur Olympique de Marseille — RC Strasbourg
  (`16310922`) : plan d'une rencontre, trois imports locaux, 146 706 octets, snapshots 417/418/419
  et observations 222/223/224 tous `COMPLETE · 100%`, avec zéro appel fournisseur, cache,
  coordinateur ou retry. La relecture SQL confirme `MANUAL_LOCAL_JSON_IMPORT`, HTTP 200, `PARSED`
  et une occurrence `INSERTED` par snapshot. Cette preuve reste partielle : le lot multi-match avec
  404, les comptes avant/après d'un refus, le rollback tardif, le réimport dédupliqué et le
  redémarrage sans reprise restent requis avant `VALIDATED`.
- recette complémentaire sur Borussia Dortmund — FC Bayern München (`16248441`) : statistiques
  snapshot 473 / observation 225 `UNAVAILABLE · N/A`, incidents snapshot 474 / observation 226
  `EMPTY_VALID · 100%`, compositions snapshot 475 / observation 227 `PARTIAL · 97%`, zéro appel
  fournisseur. Le traitement était correct, mais la création manuelle obligatoire d'un fichier
  depuis le corps 404 non téléchargeable a déclenché le correctif de déclaration locale ci-dessus ;
- requalification multi-match positive : demande `465f6849-e585-4b3f-9182-a21f91770dea`, quatre
  rencontres, douze preuves dont trois déclarations 404, 363 863 octets contrôlés, résultat
  `COMPLETED` et zéro appel fournisseur, cache, coordinateur ou retry. Le Work Order revient à
  `IMPLEMENTED_AWAITING_HUMAN_QUALIFICATION` pour les portes de clôture restantes ;
- qualification propriétaire du parcours d'import multi-match : demande
  `7ecf9176-79c4-49bf-89cd-5a39e5ce3634`, deux rencontres, quatre fichiers plus deux déclarations
  404, six preuves, 106 210 octets contrôlés, résultat `COMPLETED` et tous les compteurs réseau à
  zéro. Le terminal affiche trois `NOUVELLE_VERSION` et trois `DÉDUPLIQUÉE` ; un second plan valide
  humainement les états de manifeste `À FOURNIR` puis `404`. Le parcours et son assistance sont
  concluants, sans assimiler cette preuve aux comptes SQL, au rollback tardif ou au redémarrage
  sans reprise encore requis pour la clôture ;
- recette négative puis corrective du manifeste sur Saint-Étienne — Grenoble Foot 38 : un nom hors
  plan et un fichier combiné à une déclaration 404 sont nommés avant envoi, la famille est marquée
  `CONFLIT` et l'import reste désactivé. Les trois fichiers attendus rétablissent ensuite, sur le
  même plan, un manifeste exact et permettent un import réussi d'une rencontre et trois preuves,
  sans déclaration 404 ni appel fournisseur. Cette preuve qualifie le garde-fou local et sa reprise,
  mais pas encore les comptes SQL avant/après un rejet serveur ;
- qualification propriétaire de l'annulation avant import : la demande
  `7b929722-2808-41c2-b20f-24dd1c61b5ce` termine en `OPERATOR_STOP` pour une rencontre planifiée,
  zéro preuve, zéro octet, aucun résultat par famille et tous les compteurs réseau à zéro. Aucun
  corps JSON n'est persisté ; le scénario de redémarrage sans reprise reste une porte distincte ;
- qualification propriétaire du redémarrage sans reprise : après interruption du processus, la
  recherche restitue les événements canoniques persistés mais aucun plan, formulaire de confirmation
  ni résultat terminal antérieur. Le contrôle du lot est recalculé en `LOCKED`, aucune reprise
  automatique n'apparaît et le listener de validation n'est plus actif après les captures. Cette
  porte passe à `PASS` ; restent les comptes SQL, le rollback tardif et le comptage d'occurrences
  du réimport dédupliqué ;
- qualification propriétaire et SQL du réimport dédupliqué : deux demandes successives importent
  le même manifeste de deux rencontres, six fichiers et 318 884 octets avec zéro réseau. La première
  crée les snapshots 508 à 513 et observations 252 à 257 ; la seconde réutilise les mêmes objets et
  les marque tous `DÉDUPLIQUÉE`. Une lecture JDBC PostgreSQL confirme, pour chaque snapshot, deux
  occurrences append-only ordonnées `INSERTED,DEDUPLICATED`, sans nouveau snapshot ni nouvelle
  observation ;
- compatibilité de configuration corrigée : le lot hors ligne reste disponible avec
  `SOFASCORE_ENABLED=true` lorsque les opt-ins J3, J4 phase 2, J5 et découverte tournoi, l'origine
  et les six endpoints sont configurés. Les politiques manuelles réelles peuvent alors être
  éligibles, mais le lot ne dépend toujours d'aucun transport, cache ou coordinateur. Ses seules
  causes de blocage propres restent une automatisation active ou la désactivation de la
  conservation brute ;
- preuves de résilience complémentaires automatisées :
  `rejectsTheWholeOfflineBatchDuringPrevalidationWithoutPersistence` relève par JDBC les comptes
  `provider_snapshot`, `provider_snapshot_occurrence` et `j5_event_data_observation` avant et après
  un refus `LINEUPS_PAYLOAD_INCOMPATIBLE`, puis exige leur égalité et un plan encore
  `AWAITING_CONFIRMATION`. Le test
  `rollsBackTheWholeOfflineBatchWhenTheLastFamilyOfTheLastEventFails` injecte ensuite un incident
  PostgreSQL sur la dernière famille du dernier match et exige zéro écriture résiduelle pour tout le
  lot. Les deux tests ciblés et les 52 tests d'intégration passent sur PostgreSQL 18.4, sans pgAdmin
  ni client `psql` ;
- validation runtime de la configuration combinée avec `SOFASCORE_ENABLED=true` sur
  `127.0.0.1:8091` : HTTP 200, recherche et préparation locales présentes, badge
  `ZÉRO APPEL FOURNISSEUR`, aucun verrouillage du lot et aucun bloqueur lié au connecteur maître ;
- qualification propriétaire effective de cette configuration armée : demande
  `fd7b7833-5d5c-464c-bcf8-d7a2aa26e90b`, trois rencontres, neuf fichiers, zéro déclaration 404 et
  450 124 octets contrôlés. Le lot termine `COMPLETED_LOCKED` avec neuf résultats
  `COMPLETE · 100%`, six `NOUVELLE_VERSION`, trois `DÉDUPLIQUÉE` et zéro appel fournisseur,
  opération cache fournisseur, acquisition coordinateur ou retry automatique. Cette preuve ferme
  la qualification humaine de compatibilité avec `SOFASCORE_ENABLED=true` ; seule la décision
  explicite de clôture du Work Order reste en attente.
- clôture propriétaire du 2026-08-23 : toutes les portes automatisées, PostgreSQL et humaines sont
  acceptées ; `WO-SS-20260822-010` passe à `VALIDATED` et rejoint `docs/work_orders/completed`.
  Cette décision n'autorise aucun appel fournisseur supplémentaire et ne modifie aucun statut de
  gouvernance du laboratoire.

### Évolution J3 → J5 — découverte tournoi → rencontres

- catalogue de sélection reconstruit exclusivement depuis les snapshots exacts de la preuve J3
  la plus récente du processus courant lorsqu'elle est terminale `COMPLETED`, avec contrôle des
  pages contiguës, clés date/page, statuts, tailles, SHA-256, parseur et forme `scheduled` ; aucun
  lot historique n'est recomposé implicitement après redémarrage ;
- liste déroulante libellée exactement par
  `tournament.name - tournament.category.name` afin d'exposer la portée géographique, tout en
  postant seulement `tournament.id` ; une catégorie absente rend l'occurrence non actionnable et
  une contradiction de catégorie pour le même identifiant refuse le catalogue ; le serveur résout
  et revalide la date ainsi que `tournament.uniqueTournament.id`, seul identifiant numérique
  autorisé dans le nouveau chemin fermé
  `/api/v1/unique-tournament/{id}/scheduled-events/{date}` ;
- filtrage de cette liste par présence, dans `timezoneEventCount`, d'un offset réellement
  applicable pendant la date J3 dans `Europe/Paris` : `7200` en été, `3600` en hiver et l'une des
  deux clés le jour d'une bascule ; une table vide ou limitée à d'autres fuseaux est exclue jusque
  dans la résolution serveur, tandis qu'une valeur de compteur zéro reste éligible si sa clé est
  présente ;
- endpoint logique distinct `TOURNAMENT_SCHEDULED_EVENTS`, opt-in
  `SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED=false` par défaut, sélection et préparation sans
  réseau, confirmation exacte de cinq minutes, un GET au maximum, coordinateur partagé, délai
  minimal, arrêt terminal et aucun retry, polling ou ordonnancement ;
- solution hors réseau lorsque la collecte J3 directe est arrêtée par le fournisseur : après une
  nouvelle intention explicite, import atomique des corps `page-1.json` à `page-N.json`, contrôlés
  ensemble par `scheduled-events-v1` avant persistance ; la voie est bornée à 25 pages, 5 Mio par
  page et 25 Mio par lot, refuse les contenus sensibles, partage la garde J3, exécute zéro
  transport/cache et publie une preuve minimisée v5 distinguant `LOCAL_JSON_IMPORT` des appels et
  cache hits ;
- action alternative après la même confirmation : import local du seul corps JSON, borné à 5 Mio
  et scanné avant consommation de l'intention ; cette voie refuse HAR, en-têtes, cookies et
  secrets, exécute zéro transport, n'utilise pas le cache fournisseur et réemploie le même parseur,
  la même projection et la même transaction canonique ;
- extension unitaire de l'alternative hors réseau jusqu'à J5, pour une seule rencontre : après une nouvelle préparation, l'opérateur
  peut choisir trois preuves locales statistiques, incidents et compositions au lieu des trois GET ;
  le lot est scanné puis prévalidé atomiquement avant claim, conserve trois snapshots sous
  `MANUAL_LOCAL_JSON_IMPORT`, réemploie les parseurs J5 courants et expose séparément zéro appel
  fournisseur et trois imports locaux ; seule une enveloppe JSON fermée `error.code=404` représente
  une famille indisponible, tandis qu'un 403 reste refusé et qu'aucun terminal direct ne bascule
  automatiquement vers l'import ;
- correctif visuel des grilles d'exécution et champs fichier : les cartes J3/J5, leurs URL longues
  et les sélecteurs multipart peuvent désormais se rétracter dans leur colonne sans déborder du
  premier bloc d'import ;
- parser strict `tournament-scheduled-v1` à racine `events`, contrôle de l'identité du tournoi
  unique, filtre de la phase `tournament.id`, fenêtre semi-ouverte `Europe/Paris`, déduplication par
  `event.id` et états explicites de cohérence `timezoneEventCount` ; un `COUNT_MISMATCH` interdit
  toute observation canonique ;
- persistance du brut avant parsing, projection entièrement validée avant une transaction unique
  d'identités et d'observations canoniques, provenance snapshot/hash/parseur/heure conservée et
  résultat MVC minimisé ;
- liens directs `/events/{canonicalEventId}/statistics?zone=Europe%2FParis` vers J5 sans appel
  `EVENT_DETAILS`, campagne J4, saisie manuelle d'identifiant ou lancement automatique de J5 ;
- migration append-only `V24__j3_j5_tournament_scheduled_events_cache.sql` élargissant uniquement
  la portée fermée de `provider_response_cache` à `TOURNAMENT_SCHEDULED_EVENTS`, avec TTL de dix
  minutes et sans réécriture de V1–V23 ni modification de l'historique J7 ;
- migration append-only `V25__manual_local_json_import_provenance.sql` ajoutant la provenance
  `MANUAL_LOCAL_JSON_IMPORT` et séparant sa déduplication de `DIRECT_LOCAL_ENDPOINT`, sans rendre
  les snapshots importés éligibles au cache fournisseur ; cette provenance couvre maintenant le
  lot J3 et le second endpoint, sans les confondre grâce aux clés et endpoints logiques ;
- architecture, règles structurelles et runbook actualisés ; après les portes Maven/PostgreSQL et
  les vérifications fonctionnelles J3/J5 consignées, le propriétaire a qualifié l'évolution le
  2026-08-21, clôturé le Work Order au statut `VALIDATED` et autorisé son déplacement vers
  `docs/work_orders/completed` ;
- correctif de reproductibilité : les tests de binding sont désormais isolés du `.env` local même
  lorsque la découverte tournoi y est armée ; le profil combiné J3 + découverte + J4 phase 2 + J5
  est couvert avec son union exacte de six endpoints, tandis que `TOURNAMENT_STANDINGS` et
  `TEAM_RECENT_EVENTS` restent explicitement refusés comme familles supplémentaires différées ;
- correctif du catalogue après collecte J3 réelle : PostgreSQL restitue les `timestamptz` à la
  microseconde alors que la preuve terminale en mémoire conserve les nanosecondes de l'`Instant` ;
  la relecture accepte désormais uniquement un écart absolu strictement inférieur à une
  microseconde pour `receivedAt`, tandis que l'identifiant du snapshot, l'endpoint, la clé, le
  statut HTTP, le parseur, la classification, la taille et le SHA-256 restent comparés exactement.

### J7 — Export canonique local validé, PR #12 propre et fusionnable

- contrat JSON Draft 2020-12 v1 fermé, identifié par
  `urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1`, chargé depuis le
  classpath sans résolution réseau et limité à une enveloppe déterministe `{manifest,data}` de
  5 Mio ;
- assemblage en lecture seule `REPEATABLE_READ` des cinq composants courants d'un événement,
  avec provenance complète, états `PRESENT`/`UNAVAILABLE`/`MISSING`/`EMPTY_VALID`, complétude J5
  et avertissements structurés sans lecture ni copie de `payload_raw`, puis verrou d'événement
  partagé avec les écritures d'observation, leurs lignes métier J5 et les mutations autorisées de
  snapshots pendant la validation de fraîcheur ;
- trois preuves distinctes : `dataSha256` invariant à la décision, `sourceSetSha256` recalculé
  avant validation et SHA-256 du fichier complet conservé hors enveloppe ;
- identifiants numériques positifs bornés à `Long.MAX_VALUE`, UTF-8 strict et scanner renforcé sur
  les octets, textes et clés JSON, bloquant payloads bruts, URL/URI, headers, cookies, credentials,
  JWT, clés privées, sessions, `.env`, preuves de sauvegarde et phrases secrètes ;
- cycle local `COHERENCE_CHECKED` vers une unique décision `HUMAN_VALIDATED` ou `REJECTED`, avec
  jeton de formulaire à usage unique, confirmations exactes, motif de rejet borné et
  téléchargement réservé au seul statut validé ;
- migration append-only V23 étendant `export_manifest` tout en préservant ses lignes génériques,
  avec unicités partielles, verrou de fraîcheur des sources jusque dans les quatre tables filles
  J5, refus fermé d'un parent non visible, intention terminale write-ahead, transition
  authentifiée, immutabilité et interdiction de suppression des lignes J7 ;
- fichiers exclusivement sous `sofascore.export-directory`, noms générés par le serveur,
  temporaire synchronisé puis publication atomique create-new par lien physique sur le même
  système de fichiers et refus de traversée, lien symbolique, écrasement ou altération ;
- pages HTML locales d'historique et d'aperçu, lien depuis la fiche événement, contenu échappé et
  en-têtes `no-store`/`noindex`, sans endpoint JSON général, collecte, polling, planification,
  retry réseau ou transfert vers le Betting Project ;
- amendement opérateur permettant d'enchaîner, dans une même instance locale, les campagnes
  manuelles J3, J4 phase 2 et J5 puis les parcours locaux J6/J7 : union exacte des cinq familles
  déjà approuvées, opt-ins toujours désactivés par défaut, J4 phase 1 toujours exclusif, et
  coordinateur commun sérialisant toute requête fournisseur J3/J4/J5 avec un délai minimal partagé
  de trois secondes ; la configuration locale combinée réelle démarre sur `127.0.0.1:8087` sans
  appel automatique ni nouvel endpoint fournisseur ;
- qualification opérateur du mode combiné : une première séquence a terminé J3 sur six pages et
  J4 phase 2 avant de s'arrêter sans retry sur un timeout J5 ; après redémarrage, la même instance a
  terminé J4 phase 2 sur le snapshot 315, J5 sur les snapshots 316/317/318, puis J3 sur les six
  pages 319 à 324, toujours sans activité automatique ;
- résultat de reparsage J4 clarifié pour la forme J3 `SCHEDULED_TOURNAMENT_LIST` : le nombre de
  compétitions est affiché séparément et l'écran précise que cet endpoint ne fournit aucune
  rencontre programmée, n'invente aucun match et ne crée aucune observation J4 ; la forme
  `EVENT_LIST` conserve ses compteurs de rencontres insérées ou dédupliquées ;
- documentation d'architecture, runbook opérateur et rapport de readiness J7 ajoutés ; la suite
  standard finale recense 457 tests, avec 0 échec, 0 erreur et 2 scénarios de création de liens symboliques
  ignorés faute de permission Windows ; la suite PostgreSQL/Testcontainers passe 43 tests avec
  0 échec, 0 erreur et 0 test ignoré, y compris l'upgrade V22→V23 avec corpus J4/J5/J6 préexistant,
  l'export fournisseur complet et les verrous concurrents de toutes les écritures sources, y
  compris les quatre tables filles J5 ; la revue technique passe,
- première preuve opérateur partielle réussie sur l'événement fournisseur complet `16707704` :
  candidat inspecté, décision `HUMAN_VALIDATED`, téléchargement de 34 739 octets sans erreur,
  SHA-256 `2f60b37151e81625a96ab9426f5474f4600191d102c398a4be8cfd96c5a230a9` identique au terminal,
  historique relu avec une ligne terminale identique, empreintes de données et de sources
  cohérentes, cinq provenances fournisseur `COMPLETE`, aucun avertissement ni contenu sensible et
  aucun artefact de recette versionné ;
- preuve opérateur de rejet réussie sur l'événement fournisseur ciblé `16691018` : les trois
  avertissements attendus (`EVENT_STATISTICS` indisponible, incidents et compositions partiels)
  sont visibles, le terminal de 28 767 octets est seul conservé sous l'extension `.rejected.json`,
  ses hashes de données et de sources se recalculent à l'identique, son scan sensible est vide et
  la tentative locale de téléchargement renvoie `404` avec un corps vide ;
- preuve opérateur bout en bout dans une seule instance, commencée avant minuit le 19 août et
  terminée après minuit le 20 août en heure de Paris : J3 a persisté les six pages 325 à 330, J5 a
  terminé les trois familles sur 331/332/333, J6 a comparé localement les incidents append-only,
  J4 phase 2 a créé la version courante depuis 334, puis J7 a validé et téléchargé l'export
  `696c1965-183e-43ba-ba47-4ee4d2ad5481` de l'événement `16421055` ; le fichier de 37 239 octets
  porte le SHA-256 `37784bb4187fb80de06b765d8a9cb54b36f8035ed24eee82f9d6791c5a388880`,
  cinq sources fournisseur complètes, aucun avertissement et aucun contenu sensible ; les dix
  transports de cette séquence sont exclusivement les gestes humains J3/J5/J4, tandis que J6 et
  J7 restent sans transport et que les trois campagnes réseau finissent verrouillées ;
- recette humaine obligatoire fermée : `16691018` a été recréé, validé et téléchargé sous l'export
  `af9735bc-ccfa-419e-bfea-fb6e500bc5bb` ; son fichier de 28 701 octets porte le SHA-256
  `87ed3d0e17a3150bf3a7aee1834da35e40a8dbcafecb037032cdf893ec543063` et conserve les trois
  avertissements attendus, sans constat sensible ni transport J7 ;
- rejet puis validation synthétiques fermés sur l'événement `900001` : le terminal rejeté
  `e6bd61ab-80e2-4a9d-854c-015ea37521a1` est conservé mais non téléchargeable, le terminal validé
  `563dbeb8-8660-422b-b214-e3b1b306aa6d` est téléchargé à l'identique, et les deux décisions
  conservent le même `dataSha256` et le même jeu de cinq sources `SYNTHETIC_FIXTURE` ; le fichier
  validé de 8 707 octets porte le SHA-256
  `fb637b4c87fc6a8cba230c6d4ad0928af3d52c1a793d379ae6ac9b9d1888b1f4`, avec cinq avertissements
  `SYNTHETIC_SOURCE` et zéro constat sensible ;
- la branche `codex/j7-canonical-export` est publiée avec les commits `46d633b` et `cd30fca` ; la
  PR `#12` en brouillon est confirmée par GitHub `mergeable=true` et `mergeable_state=clean`, sans
  contrôle distant en attente ; le Work Order passe donc à `VALIDATED` et rejoint `completed` ;
  aucune fusion vers `main` n'est exécutée sans confirmation humaine explicite, et la phase
  applicative demeure volontairement `J6-HISTORY-AND-GUARDED-RETENTION-VALIDATED` jusqu'à cette
  fusion.

### J6 — Historique et rétention gardée

- migration Flyway V21 ajoutant une occurrence append-only pour chaque tentative de persistance
  brute future, y compris les réponses dédupliquées, et un backfill `BASELINE` prudent pour les
  snapshots antérieurs ;
- agrégation locale des cinq flux `EVENT_STATE`, `EVENT_DETAILS`, `EVENT_STATISTICS`,
  `EVENT_INCIDENTS` et `EVENT_LINEUPS`, avec ordre stable, pagination bornée, provenance complète et
  état explicite des octets bruts ;
- différences sémantiques calculées à la demande pour les champs scalaires, métriques, incidents,
  compositions, score et complétude, sans relecture du JSON brut ni mutation de l'historique ;
- classification `BASELINE`, `TECHNICAL_DUPLICATE`, `LOCAL_REPARSE`,
  `SEMANTICALLY_UNCHANGED`, `SYNTHETIC_CHANGE`, `PROVIDER_UPDATE`, `LATE_ENRICHMENT` ou
  `LATE_CORRECTION`, avec prise en compte du dernier état terminal strictement antérieur ;
- pages Thymeleaf d'historique et de comparaison sur boucle locale, réponses `no-store`, contenu
  échappé, pagination et erreurs explicites, sans endpoint JSON, export ni téléchargement brut ;
- corpus synthétique J6 idempotent ajoutant un état final et quatre changements postérieurs à cet
  état aux fixtures nominales J4/J5, classés `SYNTHETIC_CHANGE` et sans transport fournisseur ;
- migration Flyway V22 limitant la rétention aux seuls octets `payload_raw` éligibles, avec
  conservation des lignes, hashes, tailles, métadonnées, occurrences et observations normalisées,
  audit append-only et trigger bloquant toute mutation non auditée ;
- aperçu Web en lecture seule et commande non Web limitée à 500 éléments, avec plan SHA-256,
  phrase exacte, relecture transactionnelle `SERIALIZABLE` et refus sur dérive ou couverture de
  sauvegarde insuffisante ;
- scripts PowerShell de sauvegarde PostgreSQL directement chiffrée par `age`, restauration dans
  une base temporaire, comparaison des empreintes, manifeste qualifié et invocation manuelle de la
  rétention ; aucun dump clair, secret en argument, bouton de purge ou planification ;
- phase applicative d'abord avancée à `J6-HISTORY-AND-GUARDED-RETENTION-READINESS`, avec
  documentation d'architecture, runbook opérateur et rapport de readiness ;
- première phase humaine de l'interface J6 acceptée sur seize captures opérateur non versionnées :
  cinq flux, douze versions, différences sémantiques, trois comparaisons dont l'état 2 → 96,
  pagination à trois éléments sur quatre pages et aperçu de rétention sans purge Web ; le second
  import idempotent et la sauvegarde/restauration restent ouverts ;
- qualification humaine finale de l'interface J6 sur deux captures supplémentaires : second import
  à `0 ajoutée / 12 déjà présentes`, total stable, comparaison d'état 96 → 97 correctement
  `SEMANTICALLY_UNCHANGED`, puis absence de listener sur le port 8087 ;
- qualification opératoire finale de la sauvegarde/restauration chiffrée : archive `age` et
  manifeste créés hors dépôt, restauration PostgreSQL temporaire qualifiée par égalité des treize
  mesures source/restauration, couverture jusqu'au snapshot 272, contrôle d'intégrité brut sans
  échec, suppression de la base temporaire et refus d'écraser les artefacts lors d'un second
  lancement ; aucune purge de la base primaire n'a été exécutée ou autorisée ;
- contrôle visuel complémentaire sur cinq captures opérateur non versionnées du snapshot réel 32
  de l'événement `16412917` : même brut et même SHA-256 source pour les observations incidents 10
  et 12, passage de `event-incidents-v3` à `event-incidents-v4`, complétude `20/20` → `36/36`,
  classification `LOCAL_REPARSE` et 18 écarts prudents correspondant à huit retraits, huit ajouts et
  deux compteurs ; aucun appariement ambigu, appel fournisseur ou changement du brut n'est inventé ;
- J6 passe à `VALIDATED`, la phase applicative devient
  `J6-HISTORY-AND-GUARDED-RETENTION-VALIDATED` et le Work Order rejoint `completed`.

### Corrigé

- correction de l'empreinte des provenances normalisées dans le script de sauvegarde/restauration
  J6 : les trois tables d'observations utilisent désormais leur colonne réelle
  `source_payload_sha256` au lieu de `payload_sha256` ; une régression d'intégration extrait les
  requêtes du script et les exécute contre le schéma Flyway V22 afin de détecter toute nouvelle
  dérive avant la qualification opérateur ;
- retrait de la liste technique des chemins JSON manquants au-dessus des tableaux
  `EVENT_INCIDENTS` et `EVENT_LINEUPS` : les rapports de complétude, badges, compteurs, données et
  lignes des tableaux restent inchangés ; une régression MVC alimente volontairement les deux
  familles avec des chemins manquants et vérifie leur absence dans le HTML ;
- qualification humaine V13 des deux formes courantes : Cittadella — Atalanta U23 (`16691018`)
  termine trois appels avec incidents snapshot 189 / observation 100 à
  `PARTIAL · 83% · 142/171` et compositions snapshot 192 / observation 101 à `94/95`, puis
  Shanghai Shenhua — Beijing Guoan (`16851672`) termine trois appels avec incidents snapshot 195 /
  observation 103 à `COMPLETE · 81/81`, motif `Leaving field` rendu à la minute 74 et compositions
  snapshot 196 / observation 104 à `97/97` ; `PROVIDER_SCHEMA_VALIDATED=YES` dans cette portée
  bornée ;
- clôture fonctionnelle J5 sur neuf captures opérateur conservées hors dépôt : listes de chemins
  techniques absentes, tableaux incidents/compositions inchangés, motif `Leaving field` conservé,
  configuration locale reverrouillée, contrôles J4/J5 `LOCKED` après redémarrage et application
  finalement arrêtée ; le Work Order réel J5 passe à `VALIDATED` et rejoint `completed` ;
- correction versionnée `event-incidents-v13` de la valeur fournisseur
  `reason="Leaving field"` sur un carton jaune : le libellé exact rejoint le vocabulaire fermé,
  reste persisté et affiché comme motif, tandis que V12 rejette encore la même forme et que toute
  autre valeur non documentée demeure `SCHEMA_INCOMPATIBLE` ; V13 conserve sans modification la
  règle stricte V12 des séances terminales entièrement non minutées ;
- qualification hors ligne de la structure opérateur de Yongjing Cao à la minute 74, de la
  poursuite ordonnée jusqu'aux compositions après trois transports sans retry et de la fermeture
  du vocabulaire ; cette preuve hors ligne est désormais complétée par les deux campagnes humaines
  V13 consignées ci-dessus ;
- correction versionnée `event-incidents-v12` de la séance terminale entièrement non minutée
  observée dans le snapshot 189 de l'événement `16691018` : V11 rejetait exactement le marqueur
  `PEN` et quatorze `penaltyShootout` sans minute globale ni action imbriquée ; V12 conserve la
  minute absente, la persiste en `NULL` et l'affiche par `—` sans jamais la déduire de la séquence ;
- validation contextuelle stricte de cette exception temporelle : marqueur `PEN` inactif et score
  complet, marqueur `FT` ou `ET` minuté, totalité de la séance non minutée, séquences uniques et
  contiguës, scores présents et score final concordant ; les séances mixtes, isolées, lacunaires ou
  contradictoires ainsi qu'un `inGamePenalty` sans minute restent `SCHEMA_INCOMPATIBLE` ;
- régression explicite de l'absence simultanée de `reason` et `description` sur un penalty
  `missed` : elle produit `PARTIAL` sans motif inventé pour `inGamePenalty` comme pour
  `penaltyShootout`, tandis que les tuples fournis, dont `Woodwork/woodwork`, restent stricts ;
- retour du statut courant à `PROVIDER_SCHEMA_VALIDATED=NO` après la campagne `16691018` :
  statistiques snapshot 188 indisponibles, incidents snapshot 189 persistés avant rejet V11,
  verrou `FAILED_LOCKED` après deux appels, zéro retry et compositions non tentées ; le payload
  complet passe hors ligne sous V12 avec 33 incidents, quatorze tirs au but, quinze avertissements
  temporels et aucun problème de schéma ;
- qualification humaine réelle du mode combiné J4 phase 2 + J5 sur Barracas Central — Rosario
  Central (`16671566`) dans un même démarrage : J4 termine après un appel et le snapshot 183, puis
  J5 termine trois appels ordonnés sans retry avec les snapshots 184/185/186 et atteint les
  compositions à `COMPLETE · 95/95` ;
- qualification réelle du motif V11 `Off the ball foul` dans le snapshot incidents 185,
  observation append-only 96 : `event-incidents-v11` traite 18 incidents à `COMPLETE · 69/69` et
  rend le carton jaune extérieur de Facundo Mallo à la minute 77 sans modifier le libellé ; les
  onze preuves PNG restent hors dépôt et leur inventaire minimisé est consigné dans les rapports de
  validation ;
- constat d'arrêt postérieur à la campagne combinée, sans listener local ni processus Java du
  laboratoire ; le Work Order reste actif uniquement pour la remise de la configuration locale à
  l'état bloqué, sa vérification après redémarrage et l'arrêt final de l'instance de contrôle ;
- correction versionnée `event-incidents-v11` de la nouvelle valeur fournisseur
  `reason="Off the ball foul"` sur un carton : le libellé exact rejoint le vocabulaire fermé, reste
  persisté et affiché comme motif, tandis que toute autre valeur non attestée demeure
  `SCHEMA_INCOMPATIBLE` ; son sens métier est documenté comme obstruction ou faute loin du ballon ;
- première qualification humaine bornée du parseur courant V11 sur Al Tai — Al-Qadsiah : campagne
  `COMPLETED_LOCKED`, trois appels sans retry, snapshot incidents historique 179 reparsé dans
  l'observation append-only 93 à `COMPLETE · 24/24`, cinq buts `regular/from=regular` visibles, puis
  compositions snapshot 182 / observation 94 à `COMPLETE · 67/67` ; cette campagne intermédiaire
  ne contenait pas le nouveau motif `Off the ball foul`, qualifié depuis sur l'événement `16671566` ;
- correction versionnée `event-incidents-v10` des cinq buts du snapshot 179 portant le tuple
  fournisseur redondant `incidentClass="regular"` / `from="regular"` : la valeur reste dans le
  brut, elle est omise de l'origine spéciale normalisée et toute combinaison croisée ou nouvelle
  valeur inconnue reste `SCHEMA_INCOMPATIBLE` ;
- retour du statut courant à `PROVIDER_SCHEMA_VALIDATED=NO` après la campagne de l'événement
  `16251993` : statistiques snapshot 178 et observation 91 complètes, incidents snapshot 179
  conservés avant le rejet V9, verrou `FAILED_LOCKED` après exactement deux appels, zéro retry et
  compositions non tentées ; le payload opérateur complet passe hors ligne sous V10 avec sept
  incidents sur sept et `24/24` signaux ;
- correction versionnée `event-incidents-v9` du carton de banc observé dans le snapshot 162 :
  `Other reason` rejoint le vocabulaire fermé des motifs et `benchAddedTime` est accepté uniquement
  avec un `card`, un `time` technique négatif, un `benchTime` présent et aucun `addedTime`
  concurrent ; le cas attesté est normalisé à `90+9`, tandis que tout usage hors contexte et tout
  nouveau motif inconnu restent `SCHEMA_INCOMPATIBLE` ;
- retour du statut courant à `PROVIDER_SCHEMA_VALIDATED=NO` après la campagne Cardiff City —
  Wrexham : statistiques snapshot 161 compatibles, incidents snapshot 162 conservés avant le rejet
  V8, verrou `FAILED_LOCKED` après exactement deux appels, zéro retry et compositions non tentées ;
  le payload complet passe hors ligne sous V9 avec 20 incidents sur 20 ;
- restauration bornée de `PROVIDER_SCHEMA_VALIDATED=YES` après le retest humain V9 de Cardiff City
  — Wrexham : `COMPLETED_LOCKED`, trois appels sans retry, snapshot incidents 162 reparsé dans
  l'observation 77 à `COMPLETE · 100%`, carton `Other reason` rendu à `90+9`, puis compositions
  snapshot 165, observation 78, `event-lineups-v2`, `85/85` ;
- correction versionnée `event-incidents-v8` du marqueur terminal de séance de tirs au but : la
  sentinelle `time=999` n'est acceptée que pour la combinaison exacte `period/PEN/penalties`,
  inactive, avec score complet et tirs au but minutés ; elle reste dans le brut tandis que la
  minute normalisée devient la dernière minute effective de la séance ;
- confirmation par rejeu hors ligne que les neuf `penaltyShootout` sans minute globale sont déjà
  normalisés depuis leur première action imbriquée et que les trois cartons sans `reason` restent
  valides, persistés sans motif et affichés par `—` ;
- prise en charge bornée du champ fournisseur `from="shot"` sur un but `regular`, conservé dans le
  brut mais omis de l'origine spéciale normalisée ; cette variante connexe empêchait encore le
  payload comportant les cartons sans motif d'être accepté intégralement ;
- ajout du résultat cohérent `incidentClass="missed"`, `description="Woodwork"`,
  `reason="woodwork"` pour `inGamePenalty` et `penaltyShootout`, avec rejet maintenu des tuples
  incomplets, croisés ou inconnus ;
- remplacement de la règle incidents V5 volontairement étroite par le contrat métier V6 couvrant
  les huit types de faits de jeu documentés ; les métadonnées d'affichage facultatives manquantes
  produisent désormais `PARTIAL`, tandis que les contradictions atomiques, types inconnus et
  valeurs hors vocabulaire restent bloquants en `SCHEMA_INCOMPATIBLE` ;
- clarification du temps des cartons de banc : un `time` technique négatif exige `benchTime`, qui
  devient la minute métier ; à l'inverse, un carton portant déjà une minute non négative ne peut pas
  aussi porter `benchTime` ; le brut, notamment `time=-5`, reste inchangé ;
- correction de la fiche métier incidents : sous-sections `10.2` et `10.3` du chapitre 10,
  libellés entrant/sortant, vocabulaire `Forward`, distinction brute `owngoal` / canonique
  `ownGoal`, règles VAR et correspondance des résultats de séance de tirs au but ;
- correction bornée du carton de banc réel qui arrêtait la seconde campagne J5 après les
  statistiques : `event-incidents-v5` traite uniquement un `card` portant le marqueur exact
  `time=-5` avec un `benchTime` valide, conserve le brut, normalise la minute depuis `benchTime` et
  retient la classe ainsi que le motif ; toute autre valeur négative reste incompatible dans
  l'attente de la fiche de règles de gestion des incidents football ;
- réarmement explicite de J5 après une campagne réussie : `COMPLETED_LOCKED` continue d'interdire
  tout rejeu de l'ancien claim, mais permet une nouvelle préparation locale avec nouvel identifiant
  de requête, nouvelle phrase et nouvel acquittement ; `FAILED_LOCKED`, `STOPPED_LOCKED` et
  `EXPIRED_LOCKED` restent verrouillés jusqu'au redémarrage ;
- poursuite de la campagne J5 lorsqu'une réponse brute identique est dédupliquée vers une preuve
  historique déjà classée : la classification V1/V2 reste immuable, le résultat du parseur courant
  est porté par une nouvelle observation append-only et `EVENT_LINEUPS` n'est plus bloqué par un
  faux `RAW_CLASSIFICATION_ERROR` ;
- conservation de l'identité des joueurs entrant et sortant sur tous les incidents
  `substitution` : `event-incidents-v4` lit séparément `playerIn` et `playerOut`, conserve leurs
  identifiants et noms, mesure toute absence comme `PARTIAL` et les affiche dans deux colonnes
  dédiées sans synthétiser de joueur ;
- correction du faux positif `SCHEMA_INCOMPATIBLE` sur les réponses réelles `EVENT_INCIDENTS` :
  `event-incidents-v3` reconnaît `addedTime=999` comme une sentinelle fournisseur uniquement sur
  un marqueur `period`, conserve les octets bruts inchangés, omet la sentinelle de la valeur
  normalisée et continue de refuser `999` sur tout incident métier latéralisé ;
- comptage de complétude des marqueurs techniques `period` et `injuryTime` sans exiger le champ
  latéral `isHome`, afin de ne pas inventer de côté pour un événement global à la période ;
- isolation du contexte PostgreSQL/Testcontainers vis-à-vis des opt-ins opérateur J3/J4/J5 : la
  suite d'intégration force désormais toutes les voies réseau à l'état désactivé sans lire ni
  modifier `.env` ;
- correction de la politique J5 qui traitait tout statut non `2xx` comme terminal : un HTTP `404`
  sur `EVENT_STATISTICS`, `EVENT_INCIDENTS` ou `EVENT_LINEUPS` devient une indisponibilité de
  famille persistée, sans retry, puis la campagne continue dans l'ordre prévu ;
- distinction explicite entre `UNAVAILABLE`, `EMPTY_VALID` et `TRANSPORT_ERROR`, avec normaliseur
  de provenance par famille, rendu `UNAVAILABLE · N/A` et absence de valeur synthétisée ;
- migration Flyway V9 reclassant les anciens snapshots J5 HTTP `404` de
  `TRANSPORT_ERROR/HTTP_STATUS_404` vers `ENDPOINT_UNAVAILABLE`, sans modifier les octets bruts ni
  créer rétroactivement une observation normalisée ;
- conservation de la date civile du match et de la zone IANA dans le lien de retour de la fiche
  J4 : Saint-Étienne — Clermont Foot revient désormais sur le `2026-08-14` au lieu de la date
  locale courante du `2026-08-15` ;
- remplacement des libellés statiques `SYNTHETIC_FIXTURE` et `event-details-v1` de la fiche par la
  provenance, la référence de source et le parseur réellement persistés, notamment
  `PROVIDER_SNAPSHOT`, `snapshot:16` ou `snapshot:17`, et `event-details-v2` ;
- ajout d’un test MVC de régression reproduisant localement l’événement `16386245`, sa date en
  `Europe/Paris` et sa provenance fournisseur, sans résolution d’URI ni appel réseau ;
- correction de l’upgrade Flyway V5 préremplie vers V6 : le trigger
  `event_detail_observation_append_only` est suspendu uniquement pendant le backfill transactionnel
  des nouvelles colonnes de provenance, puis réactivé avant les contraintes finales ;
- ajout d’un test PostgreSQL reproduisant une base V5 contenant déjà une identité, une observation
  canonique et un détail synthétique, puis vérifiant après V6 l’égalité des champs
  historiques, l’absence de ligne ajoutée ou supprimée et le refus persistant de `UPDATE`/`DELETE` ;
- qualification de l’échec de démarrage local du 2026-08-15 comme incident de migration avant
  campagne : rollback Flyway réussi, zéro appel fournisseur et aucune qualification réelle
  exécutée ;
- qualification humaine de la reprise corrective sur la base persistante : migration V5 → V6
  réussie avec les verrous réseau actifs, identité et deux versions synthétiques toujours
  consultables, puis arrêt gracieux complet de l’application sans appel fournisseur ;
- isolation des scénarios de binding J3/J4 vis-à-vis d’une configuration opérateur déjà armée :
  chaque mini-contexte retire la source d’environnement ambiante avant le chargement
  d’`application.yml` et fixe explicitement les opt-ins J3, J4 et J4 sous-étape 2. Les 212 tests
  standards passent avec la configuration J4 sous-étape 2 activée, sans appel fournisseur.
- extension de cette isolation au nouvel opt-in J5 : les trois scénarios historiques J3/J4 fixent
  désormais explicitement `SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false` et vérifient que
  J5 reste inactif. La commande `mvnw.cmd clean verify`, initialement en échec sur ces trois tests
  avec la configuration opérateur J5 armée, repasse avec 257 tests et zéro appel fournisseur.

### Ajouté

- migration Flyway V20 append-only autorisant `event-incidents-v13`, avec preuve V19 → V20 sans
  réécriture d'une observation V12 ni de sa minute nulle ; un carton `Leaving field` est ensuite
  persisté et relu avec son motif exact ;
- rapport minimisé `J5-OBSERVED-V13-LEAVING-FIELD-CARD-REASON-20260818.md`, règle métier,
  comparaison historique V12, poursuite de service, upgrade PostgreSQL et bilan courant de 369
  tests standards plus 35 tests d'intégration réussis sans appel fournisseur ;
- migration Flyway V19 append-only autorisant `event-incidents-v12`, rendant la minute nullable
  sous une contrainte réservée aux seuls tirs au but et marqueurs `PEN`, avec preuve V18 → V19 sans
  réécriture de l'historique V11 et round-trip PostgreSQL d'une minute absente ;
- rapport minimisé `J5-REAL-V12-UNMINUTED-SHOOTOUT-20260818.md`, inventaire SHA-256 des trois
  captures conservées hors dépôt, diagnostic V11 des quinze chemins temporels, rejeu V12 de la
  pièce opérateur, 366 tests standards et 34 tests PostgreSQL/Testcontainers réussis, puis nouvelle
  séquence de retest humain avant reverrouillage ;
- mode de qualification combiné J4 phase 2 + J5 dans un même processus : union exacte des quatre
  familles, J3 et J4 phase 1 toujours exclus, contrôles terminaux indépendants et preuve que
  l'arrêt global J4 n'empêche pas une préparation J5 sans redémarrage ;
- coordinateur de requêtes partagé par les services réels J4/J5, maintenant une seule section HTTP
  active et le délai minimal de trois secondes entre deux départs, y compris entre les jalons ;
- rapport `J4-J5-COMBINED-QUALIFICATION-SESSION-20260818.md`, configuration temporaire bornée,
  procédure humaine en un seul démarrage et bilan de 358 tests standards plus 32 tests
  d'intégration réussis sans appel fournisseur ;
- migration Flyway V18 append-only autorisant `event-incidents-v11`, avec preuve d'upgrade V17 →
  V18 sans réécriture d'une observation ni d'un incident V10 ;
- rapport minimisé `J5-REAL-V11-OFF-BALL-CARD-REASON-20260818.md`, régression sur la structure JSON
  fournie par l'opérateur, comparaison historique V10, poursuite de service jusqu'aux compositions
  et bilan courant de 358 tests standards plus 32 tests d'intégration réussis sans appel
  fournisseur ;
- migration Flyway V17 append-only autorisant `event-incidents-v10`, avec preuve d'upgrade V16 →
  V17 sans réécriture d'une observation ni d'un incident V9 ;
- rapport minimisé `J5-REAL-V10-REGULAR-GOAL-ORIGIN-20260818.md`, trois captures de preuve
  conservées hors dépôt, rejeu exact de la transcription opérateur hors réseau, régressions V10 et
  bilan de 348 tests standards plus 31 tests d'intégration réussis ; ce retest a ensuite été
  remplacé par la qualification humaine du parseur courant V11 ;
- qualification fonctionnelle humaine V9 à partir de cinq captures conservées hors dépôt :
  préparation sans transport, campagne à trois appels, observation incidents append-only et
  compositions complètes ; le reverrouillage, alors encore attendu, est couvert par la preuve de
  clôture finale J5 ;
- migration Flyway V16 append-only autorisant `event-incidents-v9`, avec preuve d'upgrade V15 →
  V16 sans réécriture d'une observation V8 et persistance du carton à la minute `90+9` avec le
  motif `Other reason` ;
- rapport minimisé `J5-REAL-V9-BENCH-CARD-OTHER-REASON-20260818.md`, tests de contrat V9, rejeu
  opérateur complet hors dépôt et bilan de 344 tests standard plus 30 tests d'intégration réussis ;
- qualification fonctionnelle humaine V8 à partir d'un lot externe de 48 captures, conservé hors
  dépôt : trois campagnes `COMPLETED_LOCKED` le 2026-08-18, dont le rejeu réussi par V8 du snapshot
  139 contenant neuf tirs au but et `PEN/time=999`, les cartons sans motif du snapshot 144 et le
  `inGamePenalty/missed/Woodwork` du snapshot 150 ; les deux campagnes entièrement disponibles
  qualifient de façon bornée les trois schémas courants ; l'arrêt local est confirmé par l'absence
  de listener 8087 et de processus Java du laboratoire, tandis que le reverrouillage de la
  configuration reste à confirmer avant archivage ;
- migration Flyway V15 append-only autorisant la provenance `event-incidents-v8`, avec preuve
  d'upgrade V14 → V15 sans réécriture d'une observation V7 ni modification de ses incidents ;
- régressions hors ligne V8 sur les deux payloads opérateur conservés hors dépôt : 36 incidents
  dont neuf tirs au but et un marqueur `PEN` normalisé à 146, puis 16 incidents dont trois cartons
  sans motif et trois buts ordinaires `from="shot"` ;
- rapport `J5-REAL-V8-PENALTY-INCIDENT-VARIANTS-20260818.md` et extension de la fiche métier aux
  résultats sur le poteau ou la barre transversale pour les deux familles de pénalty ;
- parseur `event-incidents-v7` limité à la classe fournisseur de substitution sur blessure
  `incidentClass="injury"`, conservation de `injury`, du joueur entrant et du joueur sortant,
  rejet de la contradiction explicite `injury=false`, et régression prouvant la poursuite vers
  `EVENT_LINEUPS` sans retry ;
- migration Flyway V14 append-only autorisant la provenance V7, avec preuve d'upgrade V13 → V14
  sans réécriture d'une observation V6 ni modification de ses incidents ;
- preuve minimisée Arsenal — Manchester City : statistiques snapshot 70 compatibles, brut
  incidents snapshot 71 persisté avant le rejet V6, cause isolée sur le remplacement sur blessure
  de Jérémy Doku par Jack Grealish parmi 23 incidents, puis validation hors ligne intégrale par V7 ;
- qualification humaine V6 de Lens — Paris Saint-Germain : marqueurs `HT`/`FT`, carton de
  l'entraîneur, temps additionnels de trois et cinq minutes et participants visibles ; campagne
  terminée avec les snapshots dédupliqués 64/65 et le snapshot compositions 69 ;
- rapport de validation `J5-REAL-V6-LENS-PASS-AND-V7-INJURY-SUBSTITUTION-CORRECTION-20260817.md`
  réunissant la preuve réelle V6, la cause exacte du rejet Arsenal — Manchester City, la
  validation hors ligne V7 des 23 incidents sur 23, la poursuite vers les compositions, les 330
  tests standards et les 28 tests d'intégration, tous réussis sans appel fournisseur ;
- fiche canonique `docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md`, parseur
  `event-incidents-v6` et matrice hors ligne couvrant `period`, `substitution`, `goal`, `card`,
  `injuryTime`, `varDecision`, `inGamePenalty` et `penaltyShootout` ;
- migration Flyway V13 append-only conservant texte de période, blessure, passeur, origine du but,
  temps additionnel, décision VAR, description et ordre de séance de tirs au but, sans réécriture
  des observations V1–V12 ;
- affichage J5 étendu aux colonnes passeur, détail et ordre de séance, avec participants et motifs
  issus exclusivement des observations normalisées ;
- preuve minimisée de Paris Saint-Germain — Lens : campagne réussie en première mi-temps sur les
  snapshots 61/62/63, puis rejeu manuel réussi à la fin du match sur les snapshots 64/65/66 ;
- preuve minimisée du retest réel du réarmement : une première campagne sur `16391135` a terminé
  ses trois appels, puis une seconde campagne distincte sur `16483632` a été préparée et exécutée
  dans la même instance ; les statistiques du snapshot 59 ont été normalisées avant l'arrêt strict
  de V4 sur le snapshot incidents 60, sans appel compositions ni retry ;
- migration Flyway V12 append-only ajoutant `incident_class` et `reason`, autorisant
  `event-incidents-v5` et qualifiant les upgrades V1/V11 → V12 sans réécriture de l'historique ;
- tests hors ligne du carton de banc (`benchTime=58`, classe jaune, motif `Argument`), de la
  persistance PostgreSQL, du rendu MVC et de la poursuite ordonnée vers les compositions avec V5 ;
- preuve minimisée du retest réel V4 sur `16412917` : statistiques HTTP `404` indisponibles,
  incidents V4 `36/36` avec joueurs entrant/sortant, compositions V2 `85/85`, exactement trois
  appels ordonnés sans retry, puis régression hors ligne du nouveau cycle après succès ;
- migration Flyway V11 append-only ajoutant les couples identifiant/nom des joueurs entrant et
  sortant, autorisant `event-incidents-v4` et qualifiant l'upgrade V10 → V11 sans réécriture de
  l'historique ;
- régression hors ligne du retest réel : statistiques 404 dédupliquées, incidents 200
  dédupliqués et reparsés par V4, puis unique appel compositions, ainsi qu'une preuve PostgreSQL
  que le snapshot V2 conserve `SCHEMA_INCOMPATIBLE` pendant qu'une observation V4 est ajoutée ;
- migration Flyway V10 append-only autorisant la provenance `event-incidents-v3` sans réécrire les
  observations historiques V1/V2, avec test d'upgrade V9 → V10 sur PostgreSQL 18.4 ;
- fixture synthétique minimale et tests de régression du marqueur de période fournisseur, ainsi
  que couverture du parcours ordonné prouvant que les compositions sont atteintes après parsing
  compatible des incidents ;
- tests hors ligne du scénario mixte « statistiques HTTP `404`, incidents et compositions `2xx` »,
  de l'affichage d'indisponibilité, de la persistance PostgreSQL `UNAVAILABLE` et de l'upgrade
  V8 → V9 d'un snapshot historique mal classé ;
- voie de qualification réelle J5, désactivée par défaut et exclusive de J3, mais partageable avec
  J4 phase 2 sous l'union exacte des quatre endpoints,
  limitée à l'origine exacte `https://www.sofascore.com` et aux trois endpoints logiques
  `EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS` ;
- contrôle humain J5 avec préparation sans réseau, phrase exacte valable cinq minutes,
  acquittement, claim immuable et trois appels séquentiels maximum ; le succès verrouille le claim
  terminé tout en autorisant une campagne ultérieure distincte, tandis qu'incident, expiration ou
  arrêt verrouillent le processus ;
- transport J5 sans proxy, redirection, cookie, jeton, compte, en-tête de navigateur ou retry,
  avec réponse bornée, délai minimal de trois secondes et arrêt avant toute famille restante au
  premier incident ;
- parseurs fournisseur `event-statistics-v2`, `event-incidents-v2` et `event-lineups-v2`,
  persistance brute avant parsing, normalisation `PROVIDER_SNAPSHOT` et migration Flyway V8
  append-only étendant les contraintes V7 sans modifier une migration partagée ;
- panneau J5 local de qualification gardée et résultat minimisé, protégés par le jeton de formulaire
  à usage unique, sans payload brut ni identifiant libre dans l'action finale ;
- couverture hors ligne de la configuration, de la confirmation, de l'ordre des trois transports,
  des `429`, des incompatibilités sans objet partiel, de la provenance PostgreSQL V8 et de
  l'absence de credentials ou d'en-tête navigateur ;
- jalon J5 hors ligne pour `EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS`, rattaché aux
  identités canoniques J4 sans ajouter de transport fournisseur ;
- contrats synthétiques versionnés `event-statistics-v1`, `event-incidents-v1` et
  `event-lineups-v1`, corpus nominal/partiel/vide et ruptures de type sans coercition ;
- rapports déterministes `COMPLETE`, `PARTIAL` et `EMPTY_VALID`, avec score, nombres de signaux et
  chemins JSON manquants, sans donnée inventée ;
- migration Flyway V7 créant les observations J5, les métriques, les incidents, les deux côtés de
  composition et leurs joueurs, avec provenance, deux hashes, déduplication et triggers
  append-only ;
- import transactionnel et idempotent des trois fixtures nominales sur l'identité synthétique J4,
  et lecture de la dernière version de chaque famille ;
- page locale `/events/{canonicalEventId}/statistics` présentant valeurs, complétude et provenance,
  avec action d'import synthétique protégée par jeton de formulaire à usage unique ;
- sous-étape 2 J4 paramétrable dans `/events`, protégée par un opt-in distinct qui rend la
  sous-étape 1 indisponible pendant son activation ;
- préparation sans réseau d'un ID `EVENT_DETAILS` borné, phrase exacte liée à cet ID, acquittement
  et claim immuable empêchant de remplacer l'identifiant lors de l'action finale ;
- rafraîchissements manuels répétables du même match, avec exactement un nouvel appel fournisseur
  sans cache par cycle, nouvelle confirmation obligatoire et délai minimal de trois secondes ;
- persistance brute avant parsing sur chaque rafraîchissement, affichage minimisé du snapshot et
  indication `NOUVELLE_VERSION` ou `DÉDUPLIQUÉE` sans exposer le payload ;
- arrêt global commun aux deux sous-étapes et tests hors ligne des `429`, de l'absence de retry, de
  la répétition manuelle, de l'exclusion de configuration et du binding Web sans ID libre lors de
  l'exécution ;
- voie de qualification réelle J4 sous-étape 1, désactivée par défaut et limitée par construction
  aux événements `16386245` et `16421052` sur le chemin exact `/api/v1/event/{eventId}` ;
- parseur fournisseur versionné `event-details-v2`, distinct du contrat synthétique historique V1,
  avec enveloppe `event`, tour imbriqué, champs facultatifs et incompatibilité sans objet partiel ;
- migration Flyway V6 append-only ajoutant la provenance `PROVIDER_SNAPSHOT` aux observations de
  détail et étendant le cache local à `EVENT_DETAILS` sans déplacer les octets bruts ;
- circuit opérateur J4 à confirmation exacte, expiration cinq minutes, arrêt global et verrou
  terminal automatique après succès, incident ou expiration ;
- orchestration de deux événements maximum avec cache préalable, délai minimal de trois secondes,
  persistance brute avant parsing, normalisation atomique et aucun retry ;
- arrêt au premier `403`, `429`, `5xx`, timeout, contenu inattendu, rupture de schéma ou incohérence
  d’identifiant, couvert sans appel Internet par transport simulé et tests PostgreSQL ;
- migration Flyway V4 créant les identités canoniques d’événements et leurs observations
  append-only, avec UUID déterministe, provenance complète, déduplication et trigger d’immuabilité ;
- migration Flyway V5 conservant les détails J4 hors ligne sous forme d’observations append-only ;
- corpus synthétique `EVENT_DETAILS`, parseur strict `event-details-v1` et couverture des champs
  inconnus, absents ou de type incompatible ;
- import transactionnel et idempotent du corpus J4, avec validation du rattachement à la même
  identité fournisseur avant toute écriture ;
- normalisation manuelle d’un snapshot local `SCHEDULED_EVENTS` après contrôle de son intégrité,
  sans mutation de sa classification historique et sans résultat partiel ;
- recherche locale par date civile et zone IANA, page de résultat et page de détail exposant
  identité, provenance et chronologie des observations ;
- manifeste de fixture v1 avec origine, preuve de schéma, taille maximale, hashes attendus et traçabilité de minimisation ;
- chargeur de fixtures classpath entièrement hors ligne, avec classification `JSON`, `HTML` ou `OTHER` ;
- calcul SHA-256 brut et JSON canonique stable malgré l’ordre des propriétés ;
- contrôles bloquants de taille, d’intégrité, de JSON ambigu et de motifs sensibles.
- corpus synthétique `SCHEDULED_EVENTS` de neuf scénarios avec manifestes, hashes réels et test d’inventaire classpath.
- parseur hors ligne `scheduled-events-v1`, DTO externe minimal et mapper vers un modèle local immuable ;
- résultats de parsing structurés avec statuts, avertissements, problèmes et preuve de traçabilité ;
- couverture hors ligne des incompatibilités `scheduled-events-v1` : champs obligatoires absents, type numérique modifié, structure inattendue et contenu HTML ;
- inventaire applicatif du corpus classpath avec disponibilité et répartition des résultats de parsing ;
- politique de décision J3 hors ligne avec résultats `USE_CACHE`, `BLOCKED` et `TRANSPORT_ELIGIBLE` ;
- circuit J3 en mémoire à activation explicite, incidents typés et absence de réouverture automatique ;
- garde atomique limitant à un le nombre de permis d’appel simultanés ;
- migration Flyway V2 append-only conservant les octets exacts, leur taille et la provenance `DIRECT_LOCAL_ENDPOINT` ;
- port et adaptateur JDBC de persistance des snapshots manuels bruts, avec résultat explicite `INSERTED` ou `DEDUPLICATED` ;
- validation bornée des métadonnées et payloads bruts, calcul SHA-256 et déduplication par endpoint, requête et hash ;
- tests PostgreSQL/Testcontainers de la migration V2, de la fidélité binaire, de la séparation brut/normalisé et de la déduplication ;
- contrat de transport `SCHEDULED_EVENTS` limité à l’origine exacte `127.0.0.1` et à une route de simulation fixe ;
- transport `RestClient` synchrone sans proxy ni redirection, avec délais plafonnés, lecture bornée et conservation des octets de réponse ;
- orchestrateur appliquant la politique J3 puis la garde atomique avant toute entrée/sortie simulée ;
- tests de serveur simulé couvrant la requête exacte, le statut `429` sans retry, la taille maximale et le rejet de contenu sensible ;
- contrôle opérateur J3 en mémoire démarrant sous arrêt global, avec réarmement et activation distincts ;
- intention manuelle `SCHEDULED_EVENTS` datée, phrase exacte à usage unique, acquittement explicite et expiration après cinq minutes ;
- tableau de bord local exposant circuit, incidents, arrêt global, confirmation et verrous fournisseur sans rendre le transport disponible ;
- processeur d’issues J3 conservant le brut avant parsing puis appliquant une classification idempotente au même snapshot ;
- couverture des arrêts sur `400`, `401`, `403`, `429`, `5xx`, timeout, erreur d’entrée/sortie, taille excessive, HTML et schéma incompatible ;
- conservation bornée de `Retry-After` comme frontière de blocage, sans programmation de retry ni réouverture automatique ;
- preuve automatisée de déduplication et d’absence de valeur sensible dans les sorties capturées ;
- chemin fournisseur J3 opt-in limité à l’origine exacte `https://www.sofascore.com`, à la date
  `2026-08-13` et aux pages `1` à `5` de `SCHEDULED_EVENTS` ;
- orchestrateur d’une action manuelle unique exécutant les pages séquentiellement, avec concurrence
  `1`, délai minimal de trois secondes, conservation avant parsing et arrêt au premier incident ;
- action Web distincte après confirmation, états `CONFIRMED_READY`, `EXECUTING`, `COMPLETED` et
  `FAILED`, et interdiction d’une seconde exécution dans le même processus ;
- client fournisseur sans proxy, redirection, cookie, jeton, compte ou donnée de session, couvert
  hors ligne avec `MockRestServiceServer` ;
- preuve terminale J3 minimisée, affichable et téléchargeable localement, composée uniquement des
  métadonnées de transport, de persistance et de classement des pages effectivement tentées ;
- verrou terminal automatique `QUALIFICATION_TERMINAL_LOCK` après succès ou incident, avec refus
  du réarmement dans le même processus après consommation de la qualification ;
- fixture synthétique minimisée au contrat utile observé `scheduled` / `tournament` /
  `timezoneEventCount`, sans valeur, URI, en-tête ou donnée de session fournisseur ;
- modèle local explicite des disponibilités de tournois et des compteurs d’événements par décalage
  horaire, distinct de l’ancien modèle synthétique `events` ;
- politique de reprise J3 relisant et reparsant localement l’unique snapshot de page 1 avant
  d’autoriser une séquence fournisseur strictement limitée aux pages 2 à 5 ;
- checkpoint PostgreSQL contrôlant l’unicité, l’intégrité brute, le succès HTTP, la compatibilité du
  parseur et `hasNextPage=true`, avec blocage persistant dès qu’une page 2 existe ;
- intention, action graphique et preuve minimisée v2 dédiées à la reprise, sans ouvrir la future
  interrogation complète répétable ;
- fixtures synthétiques de la forme qualifiée page 2 et de sa rupture par tableau non vide, sans
  donnée fournisseur, URI, cookie, jeton, compte ou session ;
- reparsing applicatif en lecture seule des checkpoints J3, avec séparation explicite entre statut
  historique persisté et résultat courant en mémoire ;
- politique de reprise J3 à la page 3 exigeant les deux checkpoints locaux reparsables et l’absence
  persistée des pages 3 à 5 avant de rendre le transport éligible ;
- intention et orchestration bornées aux pages 3, 4 et 5, avec compteur initial à deux, absence de
  répétition des pages 1 et 2 et preuve minimisée distinguant checkpoints et tentatives réseau ;
- collecte manuelle répétable depuis le tableau de bord, repartant obligatoirement de la page 1 et
  progressant uniquement selon le booléen `hasNextPage` produit par `scheduled-events-v1` ;
- plafond local de 25 pages avec arrêt `PAGINATION_LIMIT_REACHED` avant toute tentative de page 26,
  réarmement explicite entre deux collectes et maintien de la concurrence à un ;
- preuve minimisée v3 indiquant le mode de pagination, la limite locale et `hasNextPage` pour les
  pages parsées, sans payload, URI, en-tête, secret ou donnée de session ;
- cache PostgreSQL du parcours manuel dynamique, indexé par la clé exacte date/page, limité aux
  snapshots HTTP réussis classés `PARSED`, frais pendant les dix minutes du catalogue et produits
  par la version courante de `scheduled-events-v1` ;
- migration Flyway V3 append-only séparant le checkpoint de fraîcheur du snapshot brut : une
  nouvelle observation identique peut rafraîchir le cache sans réécrire ni dupliquer le payload ;
- preuve minimisée v4 distinguant `CACHE` et `PROVIDER`, les pages réellement demandées au
  fournisseur et les cache hits locaux, sans inclure le payload ou l’URI ;
- catalogue borné des 50 snapshots bruts locaux les plus récents, sans chargement automatique des
  payloads, et action explicite permettant d’inspecter une seule ligne à la fois ;
- vue JSON formatée en mémoire après contrôle de la taille, du SHA-256, des motifs sensibles et du
  JSON strict, avec échappement HTML et en-têtes `no-store` ;

### Documentation

- diagnostic minimisé des snapshots réels 32 et 34 : réponses incidents HTTP `200`, respectivement
  20 et 22 objets, deux sentinelles de période `addedTime=999` dans chaque réponse, arrêt terminal
  avant les compositions sous V2, puis correction versionnée V3 validée exclusivement hors ligne ;
- consignation minimisée de la première campagne réelle J5 : détail J4 préalable dans le snapshot
  28, réponse JSON HTTP `404` de `EVENT_STATISTICS` conservée dans le snapshot 30, arrêt
  `FAILED_LOCKED` après un seul appel, zéro retry et aucune tentative `incidents` ou `lineups` ;
- Work Order séparé `WO-SS-20260815-006` pour la qualification réelle J5, avec revue de
  l'ADR-SS-001, configuration temporaire exacte, politique d'arrêt, preuve minimisée et obligation
  de reverrouillage avant tout redémarrage ;
- consignation minimisée du second test J4 fourni par l'opérateur : snapshot 25 de `16412917`,
  acquisition réussie de `16391135` dans le snapshot 26 et refus `EVENT_ID_MISMATCH` de l'import
  synthétique J5 sur cette identité réelle ;
- architecture et readiness de la voie réelle J5 : `257` tests standards, `18` tests
  PostgreSQL/Testcontainers, huit migrations Flyway et zéro appel fournisseur pendant la
  réalisation ;
- procédure humaine J5 définissant l'activation temporaire, l'ordre des trois appels, l'arrêt sans
  retry, les champs de preuve autorisés et l'état bloqué à restaurer après succès ou incident ;
- qualification humaine hors ligne de J5 à partir de huit captures opérateur non versionnées :
  navigation J4 → J5, absences explicites avant import, trois familles à `COMPLETE · 100%`,
  provenance, parseurs et hashes visibles, avec `PROVIDER_SCHEMA_VALIDATED=NO` et voies réseau
  toujours bloquées ;
- archivage du Work Order J5 au statut `VALIDATED`, tout en conservant la future campagne en
  conditions réelles à `NOT_RUN` et hors autorisation de ce Work Order ;
- architecture hors ligne historique J5 précisant les trois formes de chemins cibles, l'absence de
  transport dans le périmètre du Work Order 005, les algorithmes de complétude, la séparation
  source/normalisé et le schéma V7 ;
- consignation minimisée de l'unique tentative de découverte J5 : `HTTP 403`, zéro retry, arrêt
  immédiat, cinq exemples non appelés et `providerSchemaValidated=false` maintenu ;
- rapport de qualification technique J5 couvrant parsing, MVC, Flyway/PostgreSQL, déduplication,
  append-only et invariants réseau ;
- consignation minimisée de la campagne humaine J4 sous-étape 1 : deux transports autorisés, deux
  snapshots HTTP `200` classés `PARSED`, arrêt global appliqué, anomalie locale corrigée puis retest
  humain concluant des deux retours par date et des provenances ;
- procédure de requalification utilisant exclusivement les snapshots locaux 16 et 17 après
  reverrouillage de la configuration, sans préparation ni réexécution de la campagne ;
- protocole correctif imposant une première application de V6 avec les cinq clés réseau remises à
  l’état bloqué, avant toute nouvelle activation de la campagne réelle ;
- amendement du Work Order J4 autorisant uniquement la campagne réelle sous-étape 1 et consignant
  la revue compatible de l’ADR-SS-001, avec politique J4 plus stricte sans retry sur `5xx` ;
- protocole Windows J4 pour l’activation temporaire, la validation humaine des deux matches,
  l’arrêt au premier incident et la remise obligatoire de la configuration à l’état bloqué ;
- amendement du Work Order après qualification humaine de la sous-étape 1, autorisant la
  sous-étape 2 paramétrable et les rappels manuels unitaires avec une nouvelle confirmation par
  appel, avec une qualification fournisseur réelle alors encore à `NOT_RUN` au moment de cette
  autorisation ;
- revue de l'ADR-SS-001 concluant `COMPATIBLE_NO_CHANGE_REQUIRED` pour le parcours paramétrable :
  appel manuel, concurrence unitaire, absence de polling, compte, cookie, jeton, proxy ou retry ;
- qualification technique hors ligne de la sous-étape 2 avec 212 tests standards et 16 tests
  d'intégration réussis, sans appel fournisseur ; sa qualification humaine et réelle était encore
  en attente au stade de cette readiness ;
- qualification humaine réelle de la sous-étape 2 sur les identifiants paramétrables `16483632`
  et `16412917`, avec une préparation, une confirmation et exactement un transport fournisseur
  par cycle ;
- validation du rappel manuel de `16412917` : réponse inchangée dédupliquée avant le coup d'envoi,
  puis nouvelle observation `inprogress` issue du snapshot 23 après le coup d'envoi, sans
  réécriture du snapshot 19 `notstarted` ;
- confirmation que les deux versions restent consultables dans l'historique append-only et que
  les snapshots bruts demeurent inspectables localement, sans intégrer le JSON aux preuves ;
- arrêt global J4, arrêt de l'instance de campagne, remise des six paramètres locaux à l'état
  bloqué, puis vérification après redémarrage du statut `LOCKED`, du champ d'identifiant
  insaisissable et de l'action de préparation désactivée ;
- arrêt gracieux final de Tomcat, JPA et Hikari après la vérification du reverrouillage ;
- vérification Maven finale en configuration bloquée : 212 tests standards et 16 tests
  PostgreSQL/Testcontainers réussis, Flyway V6 validé et aucun appel fournisseur ;
- fusion sans conflit de la Pull Request `#8` par le commit
  `950bb0f0ddd0edfe2a5e7d1e768498c049a86467`, après qualification humaine réelle et réussite des
  vérifications Maven finales ;
- clôture du Work Order `WO-SS-20260815-004` au statut `VALIDATED` et archivage dans
  `docs/work_orders/completed`, sans autoriser polling, production ou déploiement VPS ;
- ouverture du Work Order `WO-SS-20260815-004` sur la branche `codex/j4-events` depuis le merge J3
  `b79ccd62e7863718f49a22b6c54a7fc73cf87986` ;
- contrats J4 de l’identité canonique, de la normalisation versionnée et du détail synthétique hors
  ligne, avec rapport de qualification technique Windows ;
- validation fonctionnelle humaine J4 sous Windows, sans modification de `.env`, confirmant le
  verrouillage visible du connecteur, la recherche locale, l'identité synthétique stable, ses deux
  versions et son détail hors ligne ;
- intégration de l'implémentation J4 par la Pull Request `#6`, sans conflit, après réussite de 180
  tests standards, 14 tests d'intégration et du parcours fonctionnel hors ligne ;
- réouverture corrective du Work Order `WO-SS-20260815-004` au statut `IN_DEVELOPMENT` : J4 est
  `IMPLEMENTATION_MERGED` et `OFFLINE_PATH_QUALIFIED`, tandis que
  `REAL_MATCH_QUALIFICATION_PENDING` interdisait alors sa clôture définitive ;
- démarrage du Work Order `WO-SS-20260808-002` sur la branche `feat/j2-scheduled-events-fixtures` depuis le tag `j0-j1-v0.1.1` ;
- passage du jalon J2 au statut `IN_DEVELOPMENT` avec `SCHEDULED_EVENTS` comme première famille de fixtures hors ligne.
- contrat d’architecture `scheduled-events-v1` détaillant les champs obligatoires, facultatifs et inconnus.
- validation Windows et clôture du Work Order J2 après fusion de la Pull Request `#2` sur `main` ;
- ouverture du Work Order `WO-SS-20260812-003` sur la branche `feat/j3-manual-call` depuis le commit de fusion `b2561e542b1f893ec2f15c5eaeb67a361ee551ea` ;
- périmètre J3 découpé en unités hors ligne avec un point de décision explicite avant toute URI ou requête réelle.
- contrat d’architecture de la politique réseau J3 hors ligne, de son ordre d’évaluation et de ses transitions de circuit.
- contrat de persistance J3 du snapshot brut, de ses contraintes, de sa clé de déduplication et de ses limites de sécurité.
- contrat de transport J3 simulé, de sa frontière loopback et de sa composition avec les politiques.
- contrat de confirmation manuelle J3, de ses transitions sûres et de ses protections Web locales.
- matrice d’architecture des politiques J3 d’arrêt, d’incident, de conservation du brut et d’absence de retry.
- qualification Windows J3 du parcours opérateur local, de l’arrêt global et des politiques simulées,
  avec déclaration distincte de l’absence d’appel réel et de preuve de sortie fournisseur.
- amendement du Work Order J3 avec le périmètre cinq pages, la source
  `OBSERVATION_MANUELLE_DANS_LE_NAVIGATEUR`, la décision propriétaire et l’interdiction d’exécuter
  un appel pendant l’implémentation ;
- contrat d’architecture et procédure opérateur du chemin fournisseur J3 borné.
- rapport Windows de qualification pré-exécution du chemin cinq pages, couvrant la liaison de la
  configuration locale, l’injection Spring, le parcours opérateur jusqu’à `CONFIRMED_READY` et la
  disponibilité du bouton final sans l’exécuter.
- contrat et procédure de collecte de la preuve minimisée après l’unique lot réel, sans copie du
  payload brut et avec réapplication automatique de l’arrêt global.
- rapport terminal de la qualification réelle cinq pages, limité à la page 1 par l’arrêt sûr sur
  incompatibilité, puis diagnostic structurel hors ligne du snapshot local conservé.
- contrat d’architecture et procédure Windows de la reprise explicite à la page 2, sans appel réel
  pendant l’implémentation ni les tests.
- diagnostic et contrat d’adaptation hors ligne du schéma qualifié de la page 2, avec frontière
  explicite interdisant la reprise à la page 3 sans nouvelle décision.
- contrat d’architecture et procédure Windows de la reprise explicitement autorisée à la page 3,
  sans appel réel pendant l’implémentation ou les tests.
- contrat d’architecture et procédure opérateur de la collecte manuelle répétable à pagination
  dynamique, sans appel fournisseur pendant l’implémentation ou les tests.
- rapport Windows de qualification humaine de la pagination dynamique, confirmant dix pages sur
  dix pour le `2026-08-14`, la progression `hasNextPage=true` des pages 1 à 9, la terminaison sur
  `false` en page 10, la persistance avant parsing et la réapplication du verrou terminal.
- qualification complémentaire Windows du cache et de l’inspection JSON locale, avec preuve
  PostgreSQL de non-mutation entre les deux fonctions et matrice humaine minimisée des snapshots
  historiques déjà présents.
- clôture du Work Order `WO-SS-20260812-003` au statut `VALIDATED` après qualification du chemin
  manuel, de la pagination dynamique, du cache et de l’inspection JSON locale ;
- archivage du Work Order J3 dans `docs/work_orders/completed`, sans appel fournisseur pendant la
  clôture et sans étendre l’autorisation au polling, à la production ou au VPS.

### Modifié

- tableau de bord enrichi d’un accès à l’explorateur J4, sans modifier les contrôles réseau J3 ;
- modèle de données local étendu aux observations normalisées tout en conservant une séparation
  stricte avec les octets bruts de `provider_snapshot` ;
- renommage du package Java de base de `com.geoffrey.betting.sofascorelocal` vers `com.bettingproject.sofascorelocal`.
- renommage du groupId `com.geoffrey.betting` vers `com.bettingproject` dans le pom.xml
- tableau de bord enrichi avec le compteur des dix fixtures hors ligne et l’état distinct de
  validation structurelle du schéma fournisseur.
- mode visible du verrou mis à jour vers `LOCKED_OFFLINE_J3_POLICY` sans ouvrir le transport.
- phase applicative avancée à `J3-OFFLINE-RAW-PERSISTENCE` sans modifier l’état du connecteur.
- phase applicative avancée à `J3-GUARDED-SIMULATED-TRANSPORT`, toujours sans transport fournisseur actif.
- phase applicative avancée à `J3-EXPLICIT-MANUAL-CONFIRMATION`, avec confirmation d’intention uniquement.
- phase applicative avancée à `J3-TRANSPORT-STOP-INCIDENT-POLICIES`, toujours sans transport fournisseur actif.
- phase applicative avancée à `J3-FIVE-PAGE-PROVIDER-QUALIFICATION-PATH`, avec chemin dédié
  désactivé par défaut et connecteur général toujours verrouillé.
- phase applicative avancée à `J3-MINIMIZED-PROVIDER-EVIDENCE`, sans modifier la désactivation par
  défaut du chemin fournisseur.
- phase applicative avancée à `J3-PAGE-TWO-PROVIDER-RESUME`, avec checkpoint local obligatoire et
  reprise fournisseur limitée aux pages 2 à 5.
- identité de build Maven protégée par Enforcer et par un test des métadonnées Actuator générées,
  afin d’empêcher la réapparition de `com.geoffrey.betting` depuis un dossier `target` obsolète.
- calcul SHA-256 et détection de contenu sensible mutualisés entre les fixtures hors ligne et les futures preuves brutes.
- classification JSON/HTML/OTHER mutualisée entre le corpus hors ligne et les réponses du transport simulé.
- adaptateur JDBC étendu avec une transition contrôlée de `RAW_ONLY` vers le résultat final du parseur.
- liaison explicite de `SOFASCORE_ENABLED`, `SOFASCORE_J3_QUALIFICATION_ENABLED`,
  `SOFASCORE_BASE_URL` et `SOFASCORE_ALLOWED_ENDPOINTS` vers les propriétés Spring, avec valeurs
  versionnées toujours sûres par défaut ;
- sélection explicite des constructeurs Spring de production du transport, de l’orchestrateur cinq
  pages et du contrôle manuel ;
- présentation du connecteur prête en vert avec un libellé humain et classes CSS exclusives.
- parseur `scheduled-events-v1` étendu au schéma fournisseur qualifié dont la racine contient
  `scheduled` et `hasNextPage`, tout en conservant la compatibilité du corpus J2 `events` ;
- inventaire hors ligne porté à dix fixtures et indicateur de schéma fournisseur validé après
  relecture locale réussie du snapshot qualifié, sans nouvel appel réseau.
- parseur `scheduled-events-v1` adapté à la représentation `[]` strictement vide observée pour
  `timezoneEventCount` en page 2, tout tableau non vide restant incompatible ;
- inventaire hors ligne porté à douze fixtures (`7` parsées, `4` incompatibles et `1` contenu
  inattendu) et phase applicative avancée à `J3-PAGE-TWO-SCHEMA-ADAPTATION`.
- phase applicative avancée à `J3-DYNAMIC-MANUAL-PAGINATION` ; le chemin actif n’est plus limité à
  la date qualifiée ni aux cinq pages observées le `2026-08-13`.
- phase applicative avancée à `J3-DYNAMIC-CACHE-POLICY` ; le cache frais est désormais évalué et
  reparsé avant le délai et avant tout transport du chemin réel dynamique.
- phase applicative avancée à `J3-LOCAL-RAW-JSON-INSPECTION`, sans modification de la politique
  réseau, de la persistance brute ou du cache dynamique.

### Sécurité

- maintien des trois définitions J5 `callable=false` et sans URI dans le catalogue général ; la
  voie spécialisée est désactivée par défaut, exige l'opt-in J5 et l'ensemble exact des familles
  actives, reste inaccessible si J3 ou J4 phase 1 est actif et n'accepte J4 que dans sa phase 2 ;
- sérialisation commune des transports réels J4/J5 avec concurrence maximale de un, verrou détenu
  pendant l'échange et délai minimal partagé, sans polling, automatisation ni retry ajouté ;
- aucun appel fournisseur dans Maven ou pendant l'implémentation J5 ; les tests du `RestClient`
  dédié sont interceptés localement par `MockRestServiceServer`, et la campagne humaine réelle
  demeure `NOT_RUN` avec `providerSchemaValidated=false` ;
- arrêt de la découverte J5 au premier `HTTP 403`, sans variation d'en-tête, de client, d'adresse
  ou d'identité, et maintien de toutes les fixtures à `providerSchemaValidated=false` ;
- séparation des octets de fixture et des données J5 normalisées, conservation obligatoire de la
  source, du SHA-256, du parseur et de l'heure, et refus SQL de toute mutation ;
- maintien de `EVENT_DETAILS` sans URI et `callable=false` dans le catalogue général ; seule la
  voie spéciale J4 sous-étape 1 possède un transport HTTP, limité à l'origine, au chemin et aux
  deux identifiants autorisés, sans polling, retry ou repli fournisseur ;
- protection des actions J4 par jeton de formulaire local à usage unique, et réponses de lecture
  marquées `no-store`/`noindex` ;
- maintien du verrouillage réseau pendant J2 : aucune URI d’endpoint réelle et aucun appel SofaScore réel ne sont autorisés.
- maintien du connecteur et du profil réel bloqués au démarrage de J3 ; la revue des conditions officielles impose une décision humaine préalable avant tout appel.
- dans l’unité de politique hors ligne, résultat `TRANSPORT_ELIGIBLE` explicitement sans effet :
  aucun client HTTP, aucune URI réelle et aucun appel fournisseur n’y étaient introduits.
- rejet avant persistance des payloads dépassant 5 Mio ou contenant des motifs de secret, cookie, jeton ou clé privée ; aucun octet brut n’est journalisé ou versionné.
- maintien de `payload_jsonb` à `NULL` pour les snapshots bruts afin d’éviter toute normalisation implicite avant parsing.
- maintien de `ConnectorGate`, du profil réel et de l’adaptateur fournisseur général en état
  bloqué ; le transport simulé reste strictement limité à `127.0.0.1` et le chemin J3 dédié ne peut
  être activé que par sa politique distincte.
- formulaires opérateur protégés par un jeton aléatoire lié à la session et à usage unique, avec cookie `HttpOnly` et `SameSite=Strict`.
- suppression de la phrase de confirmation après usage, expiration ou arrêt global ; aucun contenu saisi n’est journalisé.
- exceptions de transport réduites à des codes sûrs, sans URI, payload ou diagnostic interne ; aucune donnée partielle n’est persistée après un échec de lecture.
- maintien de tous les incidents en circuit `OPEN` jusqu’à un arrêt et une nouvelle activation explicites, y compris après `Retry-After`.
- arrêt de la qualification Windows avant transport tant que le point de décision réel reste incomplet ;
  aucune phrase active, jeton, URI fournisseur ou donnée brute n’est versionné comme preuve.
- activation du chemin fournisseur subordonnée à quatre propriétés concordantes, à une origine
  exacte, à l’unique famille `SCHEDULED_EVENTS`, au stockage brut actif et à l’absence de mode live ;
- validation de domaine de la date et des cinq pages, sans chemin libre, redirection, proxy,
  pagination découverte, retry ni donnée de session ;
- maintien de tous les tests Maven hors ligne et absence d’appel SofaScore pendant l’implémentation.
- qualification Windows arrêtée avant le bouton final : aucune page fournisseur demandée, aucun
  snapshot réel persisté et aucune preuve de sortie fournisseur ajoutée au dépôt.
- relecture locale des snapshots 1 et 2 sans transport ni écriture, avec confirmation que leur
  statut historique `SCHEMA_INCOMPATIBLE` reste inchangé.
- arrêt normal uniquement sur `hasNextPage=false`, rupture sûre si ce champ n’est pas un booléen,
  arrêt au premier incident, délai inter-pages minimal de trois secondes et absence de retry,
  polling ou planification dans le parcours répétable.
- un cache hit ne déclenche aucun appel fournisseur, aucune attente inter-page et aucune écriture ;
  le délai minimal reste calculé exclusivement entre deux départs fournisseur réels, y compris
  lorsqu’une page intermédiaire est résolue depuis le cache.
- l’inspection brute reste une opération PostgreSQL locale en lecture seule, soumise au jeton Web
  à usage unique ; elle refuse toute divergence d’intégrité, contenu sensible ou JSON ambigu et ne
  propose aucun téléchargement du payload.
- l’inspection d’un snapshot n’altère ni son statut historique, ni le nombre de lignes brutes, ni le
  checkpoint de cache ; un snapshot `SCHEMA_INCOMPATIBLE` inspectable reste inéligible au cache.

## [0.1.0] — 2026-08-08

### Ajouté

- dépôt Git autonome `betting-sofascore-local-lab` ;
- ADR-SS-001 et cadrage PDF de référence ;
- bootstrap Spring Boot 4.1.0 / Java 25 LTS ;
- Maven Wrapper verrouillé sur Maven 3.9.16 avec contrôle SHA-256 ;
- interface Spring MVC + Thymeleaf liée à `127.0.0.1:8087` ;
- garde de liaison locale et en-têtes de sécurité ;
- PostgreSQL 18.4 local via Docker Compose ;
- migration Flyway V1 : snapshots bruts, manifestes d’export et contrôle du connecteur ;
- catalogue logique des familles d’endpoints, sans URI ni appel possible ;
- verrou logiciel `LOCKED_OFFLINE_J1` ;
- tests unitaires hors ligne et test d’intégration PostgreSQL/Testcontainers ;
- scripts PowerShell de configuration, préflight, démarrage, arrêt et vérification ;
- documentation d’architecture, runbook et Work Orders J0/J1 et J2.

### Sécurité

- connecteur désactivé par défaut ;
- profil `sofascore-live-test` bloqué ;
- aucune URI SofaScore intégrée ;
- aucun accès au VPS ni exposition réseau non locale.
