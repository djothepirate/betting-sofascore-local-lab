# SofaScore Local Lab

Laboratoire Java local et contrôlé destiné à évaluer, depuis Windows, l’intérêt de données SofaScore comme enrichissement **facultatif** du Betting Project.

> **Statut :** `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`

Le Work Order
[WO-SS-20260904-048](docs/work_orders/active/WO-SS-20260904-048-j9-wo046-local-mtls-identity-provisioning.md)
est implémenté et qualifié `PASS_LOCAL_FAIL_CLOSED` sur la branche distincte
`codex/j9-wo048-wo046-local-mtls-identity-provisioning`, au runtime exact
`063c91f2de57330ca2b6baf3753d7de4ea921881`. Il fournit à WO-046 un outillage PKI-only
fail-closed et un unique jeu d'identités mTLS locales neuf, éphémère et lié au run, sans démarrer
Docker, PostgreSQL, le Local Lab ou INT-001 et sans ouvrir de socket ou effectuer de handshake.
Seuls les exécutables Java 25 épinglés et un probe SunMSCAPI hors ligne ont été exécutés.

La qualification injecte sept échecs distincts : les sept rollbacks passent avec zéro résidu. Le
run nominal, lui, est volontairement conservé et un audit post-processus confirme exactement une
identité cliente et une identité receiver persistantes. Le profil CNG a été corrigé pour employer
`KeySpec=None`/`KeyUsageProperty=Sign`, puis le chemin instable `Import-Certificate`
(`0x80070057`) a été remplacé par une mutation `X509Store.Add` précédée d'une autorité de cleanup
durable et suivie de contrôles exacts. Les parseurs PowerShell et les `54/54` tests Pester passent ;
aucun finding P0/P1/P2 ne reste ouvert.

Le
[rapport expurgé WO-048](docs/validation/J9-WO048-LOCAL-MTLS-IDENTITY-PROVISIONING-QUALIFICATION-20260904.md),
taille `12844` octets et SHA-256
`c704961530020216bfbc644f2fa928357466eccf54ef4e3d80f5705a95ef109d`, consigne aussi une
déviation Testcontainers contenue et nettoyée, sans accès à la base primaire. WO-048 reste
`READY_FOR_OWNER_REVIEW` : la readiness, les tentatives hôte et cette déviation doivent encore
être reconnues explicitement avant classement.

Le UUID du run, les certificats, empreintes réelles, clés, stores, mots de passe et chemins privés
restent hors Git dans un état externe protégé. Cet état contient des secrets sous ACL ; seul son
engagement SHA-256, et non son contenu, est versionné avec les statuts expurgés. La qualification
de WO-048 n'autorise ni le manifeste WO-046, ni un
owner-go, ni un POST réel ; les réseaux fournisseur/distant, le VPS et la production restent
interdits.

Le Work Order
[WO-SS-20260904-047](docs/work_orders/completed/WO-SS-20260904-047-j9-j7-delivery-governance-separation.md)
est `VALIDATED` et classé sur la branche distincte
`codex/j9-wo047-j7-delivery-governance-separation`. ADR-SS-003 v0.2 a été acceptée sur la
proposition immuable `e1ec9936467dd570f7ed00c51227c8e7d5a35945` afin de corriger un
couplage erroné : l'absence de réponse officielle SofaScore reste un fait d'audit
`NOT_EVIDENCED`, mais ne doit pas bloquer le transfert local d'un J7 déjà `HUMAN_VALIDATED` vers
Betting Project, lequel ne contacte jamais SofaScore.

L'implémentation V2/V32 hors ligne et synthétique est qualifiée sous WO-047 au commit
`ddb41e8fd0dfe32e9c2aa5fdb4a50d9fcd90cb93`. Le propriétaire a validé sa readiness locale,
reconnu la déviation de sélection contenue et autorisé son classement le
`2026-09-04T16:37:53.4738879Z`. La reprise séparée de WO-046 est maintenant autorisée et sa
branche a intégré WO-047 par fast-forward exact jusqu'au commit de clôture publié
`73881ebf2c673d347b63e8be982572e70b6429ae`. Aucun manifeste, go ou POST réel n'est pour autant
autorisé.

Le Work Order
[WO-SS-20260904-046](docs/work_orders/active/WO-SS-20260904-046-j9-j7-real-local-e2e-campaign.md)
porte historiquement dans son document v0.1 l'état `BLOCKED_OFFICIAL_PERMISSION_NOT_EVIDENCED`,
depuis le commit exact de clôture de WO-045
`8a1225fc4b85d8e8b55af536fcbc7955676131ff`. L'état effectif de la séquence est désormais
`PAUSED_PENDING_WO048_OWNER_VALIDATION`. Il prépare la future campagne
Windows/Windows d'une livraison manuelle unique d'un export J7 fournisseur déjà
`HUMAN_VALIDATED` vers le receiver Betting Project sur `https://127.0.0.1:8444`.

L'autorisation historique propre à WO-046 couvre uniquement l'ouverture documentaire et
l'inventaire hors ligne.
Le receiver INT-001 est désormais validé par le propriétaire, localement qualifié et non publié au
commit documentaire `de06153f0908a1bb2dc9bbd2c8e22f7fd14dacfd` de son dépôt. La
[réconciliation officielle](docs/validation/J9-WO046-OFFICIAL-PERMISSION-RECONCILIATION-20260904.md),
taille `7273` octets et SHA-256
`707e0fd9b07dc0944225be80a590792e5e8ab0631dab964a465335f283ac1473`, ne trouve toutefois aucune
réponse, licence ou convention SofaScore exacte corroborant la déclaration propriétaire
`EVIDENCED_COMPATIBLE`. Le résultat exécutoire reste `NOT_EVIDENCED`.

La conclusion de blocage du rapport WO-046 reste la preuve exacte de la règle v0.1 alors effective,
mais ADR-SS-003 v0.2 la supersède pour le seul transfert J7 local. Le statut officiel reste
`NOT_EVIDENCED` comme fait d'audit et n'est pas présenté comme un accord. L'étape 3 a sélectionné
hors ligne les métadonnées de l'export `a8d40d57-98c5-4e01-8ecc-4f1f9b5feabc`, classé
`PROVIDER_DERIVED` et `HUMAN_VALIDATED`, taille `35663` octets, SHA-256 fichier
`d4aba249231bb9d575f40b5c23a629b938f685ee7d4cc479bfa7d846d61be3f6`. La concordance du ledger
primaire reste à revalider avant manifeste.

Le
[rapport de reprise WO-046](docs/validation/J9-WO046-RESUME-EXPORT-AND-MTLS-SELECTION-20260904.md),
taille `6215` octets et SHA-256
`18152301fbb4caa49218f7562f2a1ceb35b9e451dd6c6c5b02dc5b845eba574a`, constate que
`CurrentUser\\My` contenait zéro certificat lors de cet inventaire historique. WO-048 a depuis
provisionné et qualifié un jeu neuf lié au run ; l'étape 4 est désormais
`SELECTED_RUN_BOUND_PENDING_WO048_OWNER_VALIDATION`. Le manifeste WO-046 n'est pas créé et son
autorisation n'est pas consommée ; aucun nouveau go propriétaire n'est construit, accordé ou
enregistré.
Aucun receiver n'est démarré, aucun POST ou appel SofaScore n'est autorisé, et les réseaux distant
et fournisseur, le VPS et la production restent bloqués.

Le Work Order
[WO-SS-20260904-045](docs/work_orders/completed/WO-SS-20260904-045-j9-provider-derived-owner-go-boundary.md)
est `VALIDATED` et classé. Son commit d'implémentation
`67467d5dbd63fa54d11b2d1cd701a31edf4454e0` ajoute la frontière durable, exacte, atomique et à
usage unique V1 historique exigée avant toute future livraison J7 dérivée de données fournisseur.
La voie `SYNTHETIC_ONLY` reste inchangée ; `MIXED_OR_UNKNOWN` reste refusée.

Le grant propriétaire est lié par UUID et SHA-256 à un document canonique qui fixe le futur Work
Order/manifeste, les commits des deux dépôts, la preuve officielle, l'événement, l'export et ses
hashes, le schéma, le receiver loopback, le certificat client, l'ordinal `1`, un seul appel et une
fenêtre de 60 minutes maximum. Java et PostgreSQL recalculent le même préimage UTF-8/LF. La
migration V31 conserve les grants, révocations et consommations append-only ; consommation,
tentative et passage `IN_FLIGHT` sont committés dans une seule transaction avant toute ouverture du
transport.

La confirmation locale émet désormais une capacité mémoire liée à l'instance exacte du reçu,
expirante et consommable une seule fois. Une copie présentant les mêmes champs est refusée avant
toute lecture d'export ou de grant. Après claim, aucun échec ne rembourse le go et aucun retry
automatique n'est possible. Les fonctions SQL fixent leur `search_path`, et la sauvegarde J6 alors
qualifiée en V31 empreinte également les trois journaux owner-go.

WO-047 conserve ce format V1 et V31 byte-identiques, puis ajoute un format V2 et une migration V32
append-only qui discriminent explicitement audit fournisseur et gouvernance du transfert. Les
contrôles J6 courants exigent V32. Sous l'autorisation WO-047, leur qualification reste cependant
statique et ciblée : aucun `pg_dump`, `pg_restore`, pipeline natif, backup/restore ou contact de la
base primaire n'est autorisé. Le
[rapport WO-047](docs/validation/J9-WO047-J7-DELIVERY-GOVERNANCE-SEPARATION-QUALIFICATION-20260904.md),
taille `12033` octets et SHA-256
`75b55109c0705a44026376d7f0bdadf76d979dcd4268c1430d5ed8008d693540`, conclut
`PASS_LOCAL_FAIL_CLOSED`. Le propriétaire a validé cette readiness, reconnu la déviation de
sélection contenue et autorisé le classement du Work Order. Aucun profil `integration-tests`
intégral n'est revendiqué. La clôture seule n'autorisait pas la reprise de WO-046 ; cette reprise
a depuis été accordée par une décision propriétaire séparée et reste bornée par les portes
consignées dans le Work Order actif.

Pour la qualification historique WO-045, les parcours Maven standard et `integration-tests`
passent chacun à Surefire `1167/0/0/5` et
Failsafe `100/0/0/0`, incluant PostgreSQL, concurrence, mTLS et E2E synthétique strictement
loopback. Les revues adversariales finales comptent zéro P1/P2. Le
[rapport WO-045](docs/validation/J9-WO045-PROVIDER-DERIVED-OWNER-GO-BOUNDARY-QUALIFICATION-20260904.md),
taille `11471` octets, porte le SHA-256
`5146c18542f0376a60bac5d4a25b68ff3f2fec9d7da700b32ee0aac60e16d946` et conclut
`PASS_LOCAL_FAIL_CLOSED`.

Le propriétaire a validé WO-045, reconnu sa readiness locale et autorisé son classement le
`2026-09-04T13:56:44.6887667Z`, soit `2026-09-04T15:56:44.6887667+02:00` en Europe/Paris.

Aucun grant réel, POST fournisseur, appel SofaScore, receiver distant, accès VPS ou usage de
production n'a été effectué. Le propriétaire autorise séparément l'ouverture de WO-046, mais
n'accorde aucun go lié au futur manifeste et n'autorise aucun POST réel. Le nouveau manifeste gelé
et un nouveau go propriétaire exactement lié restent obligatoires avant toute tentative. Les
réseaux fournisseur et receiver distant, le VPS et la production restent interdits. La branche et
ses commits restent locaux ; aucun push ni merge n'est réalisé par cette clôture.

Le Work Order
[WO-SS-20260904-044](docs/work_orders/completed/WO-SS-20260904-044-j9-wo036-java-jar-path-argument-boundary.md)
est `VALIDATED` et classé. Le commit
`0b903b71ae70ccfb9b55f8d89bc8ac57eab0cbe3` résout localement le second P2 de la PR `#26` : le
chemin JAR WO-036 absolu et canonique est explicitement protégé à la frontière native de
`Start-Process`, puis reçu comme une valeur unique immédiatement après `-jar` même lorsque le
chemin Windows contient des espaces. Les valeurs ambiguës sont refusées fail-closed.

La preuve hôte passe sur `5/5` itérations et `4/4` refus, sans processus détenu ni racine temporaire
résiduelle. Pester passe à `5/5` ciblé et `95/95` global ; Maven standard hors ligne avec les
intégrations explicitement désactivées passe à Surefire `1136/0/0/5`. Le
[rapport WO-044](docs/validation/J9-WO044-JAVA-JAR-PATH-ARGUMENT-BOUNDARY-QUALIFICATION-20260904.md),
taille `8729` octets, porte le SHA-256
`02008824316e688b6e42c523aaec45acf7b8edb776f49a9b11a822afd6a125a7`.

Un écart d'exécution est déclaré : une première commande Maven hôte sans `-DskipITs` a démarré un
PostgreSQL Testcontainers isolé et Ryuk malgré la porte `DATABASE_START=NO`. Elle a été interrompue
immédiatement ; le postflight par identifiants exacts confirme zéro conteneur résiduel, tandis que
la base primaire est restée `running/healthy`, sans accès, arrêt ni purge. La qualification de la
correction est `PASS_LOCAL_FAIL_CLOSED`. Le propriétaire a validé WO-044, reconnu explicitement
cet écart et la readiness locale, puis autorisé son classement le
`2026-09-04T09:34:31.1785757Z`, soit
`2026-09-04T11:34:31.1785757+02:00` en Europe/Paris. Le P2 est désormais
`RESOLVED_LOCALLY_VALIDATED_PENDING_PR_UPDATE`. Aucun push ou changement de la PR `#26` n'est
autorisé sans décision propriétaire séparée ; son merge, la fermeture de la PR `#25`, les appels
HTTP, les données J7 réelles, le VPS et la production restent interdits.

Le Work Order
[WO-SS-20260904-043](docs/work_orders/completed/WO-SS-20260904-043-j9-wo036-client-certificate-fail-closed-rollback.md)
est `VALIDATED` et classé. Le commit
`f1e40da11e0c3f74661afcbb5de9dc458b385f60` ferme localement la fenêtre P2 de la PR `#26` : la
propriété exacte du certificat client WO-036 est enregistrée avant toute validation post-création,
puis le certificat `CurrentUser\My` et sa clé privée sont supprimés ensemble sur tout échec.

Les quatre injections déterministes et le parcours nominal passent sans certificat, clé privée,
racine de campagne, processus ou listener nouveau résiduel. Pester passe à `18/18` ciblé et `90/90`
global ; les parcours Maven standard et intégration passent chacun à Surefire `1136/0/0/5` et
Failsafe `89/0/0/0`. Le
[rapport WO-043](docs/validation/J9-WO043-CLIENT-CERTIFICATE-ROLLBACK-QUALIFICATION-20260904.md),
taille `7122` octets, porte le SHA-256
`f412dc4b7c489412ffb8b3499dfe3a1350985c602b8b7d9a87659e0f7733665c`.

Le propriétaire a reconnu la readiness locale de WO-043 et autorisé son push puis la poursuite de
la PR `#26`. Le P2 est `RESOLVED_LOCALLY_VALIDATED_PENDING_PR_UPDATE`. Le PostgreSQL primaire
préexistant est resté `running/healthy` sur `127.0.0.1:5432`, sans accès, arrêt ni purge. La fusion
de `#26`, la fermeture de `#25` et toute opération réseau réelle restent interdites.

Le Work Order
[WO-SS-20260904-042](docs/work_orders/completed/WO-SS-20260904-042-j9-pr25-ci-readiness.md)
est `VALIDATED` et classé depuis le commit WO-036 qualifié exact
`ad343d5f1ed131b9a766c60ffd0086dc354ee839` afin de préparer une PR de remplacement conforme à la
Convention 1A. Sa correction est limitée à la deadline externe du test J6 hors ligne, portée de 90 à
180 secondes ; les timeouts runtime J6, les invariants fail-closed, le cleanup et la politique CI
restent inchangés.

La qualification locale de WO-042 est `PASS_LOCAL_CI_READY`. Trois exécutions ciblées J6 passent
en `64.055 s`, `61.918 s` et `60.258 s` ; les parcours Maven standard et intégration passent chacun
avec Surefire `1136/0/0/5` et Failsafe `89/0/0/0`. Le maximum J6 observé est `64.825 s`, soit
`36.01 %` de la borne. Le
[rapport local](docs/validation/J9-WO042-PR25-CI-READINESS-QUALIFICATION-20260904.md) mesure `7124`
octets et porte le SHA-256
`cd278b5d3c9698030bee54ee3cefb01458c192c02761238c1111d7af19a9fc58`.

La PR de remplacement
[`#26`](https://github.com/djothepirate/betting-sofascore-local-lab/pull/26) est ouverte vers `main`,
sans conflit. Ses runs `33817549423` et `33818064414` sont verts ; sur le HEAD final validé
`deb404cbb93bd7135d820aab2b8eebcecf87117c`, Windows passe en `3 min 35 s` et Linux en
`5 min 20 s`. Le propriétaire reconnaît la readiness locale et CI de WO-042. Aucune fusion n'est
autorisée.

La revue de la PR `#26` conserve un P2 ouvert sur le rollback du certificat client du harnais E2E
WO-036. Sa correction relève d'un Work Order distinct ; la validation de WO-042 ne le résout pas.

La PR `#25` reste ouverte malgré les checks verts de la remplaçante. Aucune fusion, fermeture de
`#25`, donnée J7 réelle, opération fournisseur/receiver réelle ou distante,
VPS, production, PR ou validation INT-001 n'est autorisée par WO-042.

Le run neuf R6 de
[WO-SS-20260902-036](docs/work_orders/completed/WO-SS-20260902-036-j9-j7-local-e2e-qualification.md)
a qualifié le flux synthétique Windows/Windows complet. Son manifeste a été gelé et commité avant
le premier `POST` au commit `c9ab6075210adb6593457ec6fac0691069ed528d`, SHA-256
`9e61dbcac5c2d13f0755c506ab1ad13df6ceaed1df9b186c21351be6f91e7d68`. La séquence réelle
loopback a produit exactement `201/IMPORTED`, `200/DUPLICATE` et
`409/DIVERGENCE_REJECTED`, avec un unique receipt, payload et outbox durables, trois audits, zéro
retry et zéro quatrième appel.

Le cleanup est complet, les processus ont été arrêtés gracieusement et le PostgreSQL primaire
exact est de nouveau `healthy` sur `127.0.0.1:5432`, avec identité et volume inchangés. Les
postflights passent : Pester `87/87`, Local Lab Surefire `1136/0/0/5` et Failsafe `89/0/0/0`,
receiver Surefire `297/0/0/0` et Failsafe `98/0/0/0`. Le
[rapport R6](docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R6-20260903.md), taille
`14304` octets et SHA-256 `17a299cef826a4cc8da3fd4ff50a20eec99aee447cc4a0b69fc79914d4ab4280`, classe la
preuve `PASS_LOCAL_SYNTHETIC_E2E`.

Le propriétaire a validé WO-036, reconnu sa readiness locale et autorisé son classement, le push
de sa branche ainsi que la création d'une Pull Request vers `main`. L'exception loopback
synthétique est refermée. Aucun export J7 réel, réseau fournisseur, payload dérivé, receiver réel
ou distant, VPS, production, fusion ou validation INT-001 n'est autorisé par cette validation.

Le Work Order runtime
[WO-SS-20260903-041](docs/work_orders/completed/WO-SS-20260903-041-j9-local-labb-readiness-listener-gate.md)
est `VALIDATED`. Il reproduit et corrige le défaut
`INCOHERENT_DOUBLE_LISTENER_SNAPSHOT_TOCTOU_FALSE_CONFLICT` : l'ancienne boucle pouvait classer
comme conflit un listener exact apparu entre deux lectures successives. Le commit
`7893136664c5fc6c162da844735e36cb7829814c` emploie désormais un seul instantané cohérent par
tentative, sans relâcher l'unicité, `127.0.0.1`, le port ou le PID exacts.

La qualification est `PASS_LOCAL_FAIL_CLOSED` : 87 tests Pester passent, dix apparitions retardées
réelles sur `127.0.0.1:8087` sont acceptées, le propriétaire conflictuel est refusé, les deux
vérifications Maven passent avec 1 136 tests Surefire et 89 tests Failsafe sans échec, et aucun
processus, listener synthétique ou conteneur Testcontainers ne subsiste. Le rapport
[J9-WO041-LOCALLABB-READINESS-LISTENER-GATE-QUALIFICATION-20260903](docs/validation/J9-WO041-LOCALLABB-READINESS-LISTENER-GATE-QUALIFICATION-20260903.md)
a pour SHA-256
`ed650e2d9631f39b442769315fd50d47e5ee85129fb9e4549bb7093110a2976c`.

La validation de WO-041 autorise son classement. Le propriétaire autorise séparément la reprise de
WO-036 par un run neuf R6, avec un manifeste neuf gelé avant son premier POST. Aucun appel
fournisseur, donnée dérivée, receiver distant, VPS, production, PR ou validation INT-001 n'est
autorisé ; la claim R5 demeure consommée et ne sera pas réutilisée.

Le Work Order
[WO-SS-20260903-040](docs/work_orders/completed/WO-SS-20260903-040-j9-wo036-collision-response-client-qualification.md)
est `VALIDATED` sur une branche et un worktree distincts depuis le commit R4 exact
`33d2ae0bd5c2c8daae4cd708b0f12efaf9cd029d`. Sa portée est limitée au diagnostic, à la correction
et à la qualification du lecteur de réponse HTTP de la sonde WO-036 après un effet durable de
divergence. WO-036 reste arrêté, sa claim R4 demeure consommée et aucun sender Java, receiver,
contrat, migration, réseau fournisseur ou distant, VPS ou production n’est autorisé à changer ou
à être utilisé.

Le diagnostic WO-040 a reproduit hors ligne une dépendance erronée à l'EOF : même après une
réponse HTTP fixe complète, l'ancien lecteur effectuait un `Read()` supplémentaire et pouvait
perdre le statut si le transport restait ouvert ou échouait. Le lecteur qualifié restitue désormais
les réponses `Content-Length` et `chunked` dès que leur framing exact est complet ; les réponses
close-delimited exigent toujours un EOF propre et les framing tronqués, ambigus, surdimensionnés
ou déjà surnuméraires restent refusés. Le commit
`f6e6a804f507bad48943534d4179bfcc90437766` porte ce framing incrémental fail-closed ; le commit
`80cd5a33b19b2da48f0ab0821ef6fdfbd2623ba4` accepte en plus l'unique séparateur émis par
INT-001/Tomcat pour une reason phrase vide, tout en refusant les formes ambiguës.

La qualification finale contre INT-001 inchangé, exclusivement synthétique et loopback, a produit
exactement deux POST : `201` sous `Content-Length`, puis `409/J7_IMPORT_CONFLICT` sous framing
chunked, sans retry. La base isolée contient exactement un receipt, un payload, un outbox, un audit
`IMPORTED` et un audit `DIVERGENCE_REJECTED/EXPORT_ID_DIVERGENCE`. Les 68 tests Pester, les
1 136 tests Surefire et les 89 tests Failsafe passent dans les deux parcours Maven. Le cleanup est
à zéro résidu ; le PostgreSQL primaire exact reste `running/healthy` sur `127.0.0.1:5432`, sans
accès ni purge. Le
[rapport WO-040](docs/validation/J9-WO040-COLLISION-RESPONSE-CLIENT-QUALIFICATION-20260903.md)
a pour SHA-256 `2eb13aa1825d21eb5c40e97ffebf77246099d781f626591132897c4d128e9aee`.
Le propriétaire a validé WO-040, reconnu sa readiness locale et autorisé son déplacement vers les
Work Orders terminés le `2026-09-03T18:58:26.5005063Z`, soit
`2026-09-03T20:58:26.5005063+02:00` en Europe/Paris. Cette validation clôt uniquement WO-040 :
WO-036 reste arrêté et R5 n'est pas autorisé sans décision propriétaire distincte ni manifeste
neuf gelé avant son premier POST.

Le propriétaire a depuis demandé de pousser WO-040 puis de reprendre WO-036. La branche WO-040
est poussée sur `origin` et sa clôture est intégrée dans WO-036 par fast-forward exact au commit
`9cf14180404efe899abeb6a7f3f3d6b7f1e6a028`. Le run R5 exclusivement synthétique et loopback a
gelé son manifeste neuf avant le premier POST, puis A a produit `201/IMPORTED` et `DELIVERED`.
Après l'arrêt gracieux de A, la claim one-shot de démarrage B a été consommée sur l'échec de la
porte du listener exact `127.0.0.1:8087`, alors que le journal privé expurgé rapporte le démarrage
Spring/Tomcat. Aucun deuxième POST, duplicate, collision ou retry n'a suivi.

R5 est donc `STOPPED_AFTER_FIRST_IMPORT_AND_CONSUMED_B_START_CLAIM`, avec cause racine
`NOT_ESTABLISHED`. Le cleanup et les postflights sont verts : Pester `68/68`, Local Lab Surefire
`1136/0/0/5` et Failsafe `89/0/0/0`, receiver Surefire `297/0/0/0` et Failsafe `98/0/0/0`, zéro
résidu WO-036 ou Testcontainers, et le PostgreSQL primaire exact est `healthy` sur
`127.0.0.1:5432`. Le
[rapport R5](docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R5-STOP-20260903.md), taille
`12501` octets et SHA-256 `1bee325424931a8ecc6e0b445fbd06efd2e80364a2dad8e038245dd2012c8053`,
consigne l'arrêt. WO-036 reste actif ; un Work Order distinct doit diagnostiquer l'outillage avant
toute proposition R6. Les réseaux fournisseur et distant, les données dérivées de SofaScore, le
VPS, la production, tout nouveau push de WO-036, la PR et la validation INT-001 restent interdits.

Le run neuf R4 de
[WO-SS-20260902-036](docs/work_orders/completed/WO-SS-20260902-036-j9-j7-local-e2e-qualification.md)
a été gelé avant son premier POST au commit `e12500dc8af9133b254bfe075d74e979cad472a6`, manifeste
SHA-256 `ff73efe0f9840941c6b281cdba38e21cda63e379acd0154f673ef8d7624b39ef`, puis exécuté avec un
export J7 entièrement synthétique. A a produit `201/IMPORTED` et `DELIVERED`; B a produit
`200/DUPLICATE` et `DUPLICATE_CONFIRMED`. Le troisième et dernier appel a atteint la branche
receiver de divergence et persisté l'audit `DIVERGENCE_REJECTED/EXPORT_ID_DIVERGENCE`, sans
second receipt, payload ou outbox. Le client de campagne n'a toutefois capturé ni statut HTTP ni
code sûr : sa claim est consommée sous `FAILED_OR_UNKNOWN_CONSUMED`, sans retry ni rejeu.

R4 est donc `STOPPED_AFTER_CONSUMED_R4_COLLISION_RESPONSE_QUALIFICATION_FAILURE`. La mesure
`539.19 ms` entre claim et achèvement exclut l'hypothèse d'un timeout ; la cause exacte du défaut
de capture reste `NOT_ESTABLISHED`. Le
[rapport R4](docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R4-STOP-20260903.md), SHA-256
`16e9f85e12109f2709146312410bbe2a5e040c4f176c2d5596746aa2b70076b5`, consigne l'arrêt,
le cleanup sans résidu, le redémarrage sain du PostgreSQL primaire exact et les postflights verts.
WO-036 reste actif ; une correction de harnais distincte, sa validation, une nouvelle décision
propriétaire et un manifeste R5 neuf sont requis. Aucun réseau fournisseur ou distant, donnée
dérivée, VPS, production, push, fusion, PR ou validation INT-001 n'est autorisé.

Le Work Order
[WO-SS-20260903-039](docs/work_orders/completed/WO-SS-20260903-039-j9-wo036-collision-probe-http-serialization.md)
est `VALIDATED` avec le résultat `PASS_LOCAL_FAIL_CLOSED`. Le commit
`90c1354c97f506b8291fedae80b7dc6ed37e2c11` construit la sonde de collision sous forme d’octets
HTTP/1.1 exacts : média type contractuel sans espace ajouté, en-têtes uniques et bornés,
`Content-Length` exact, corps byte-identique et TLS direct vers `127.0.0.1:8444`, sans proxy,
redirect ni retry. Une exécution synthétique hôte a produit exactement un POST et le receiver
INT-001 inchangé a persisté l’unique audit
`DIVERGENCE_REJECTED/EXPORT_ID_DIVERGENCE`, sans second receipt, payload ou outbox.

Le commit `058c57b05ac4c40e1867af60e76cce6cc2864e67` corrige ensuite le parseur borné pour accepter une
ligne `HTTP/1.1 409` sans reason phrase tout en refusant les formes ambiguës. La requête déjà
consommée n’a pas été rejouée ; cette correction est qualifiée hors ligne. Les 47 tests Pester,
les 1 136 tests Surefire et les 89 tests Failsafe passent, y compris sous le profil d’intégration.
Le cleanup hôte est complet et le PostgreSQL primaire exact reste `running/healthy` sur
`127.0.0.1:5432`. Le
[rapport WO-039](docs/validation/J9-WO039-COLLISION-PROBE-HTTP-SERIALIZATION-QUALIFICATION-20260903.md)
a pour SHA-256 `4108f3916f0800c5d1c3616f0c6af866462cda5a188991f15f0305b0ea8d7f50`. Le propriétaire a
ensuite autorisé le run R4 neuf de WO-036, exclusivement synthétique et loopback ; cette
autorisation a été consommée par la campagne arrêtée décrite ci-dessus. Aucun réseau fournisseur
ou distant, VPS ou production n’est autorisé.

Le propriétaire a validé WO-039, reconnu sa readiness locale et autorisé son déplacement vers les
Work Orders terminés le `2026-09-03T16:31:41Z`, soit `2026-09-03T18:31:41+02:00` en
Europe/Paris. Cette validation clôt WO-039 uniquement ; l'autorisation propriétaire ultérieure de
R4 est distincte et désormais consommée.

Le Work Order
[WO-SS-20260903-038](docs/work_orders/completed/WO-SS-20260903-038-j9-j7-ack-receipt-time-semantics.md)
est `VALIDATED` avec le résultat `PASS_LOCAL_FAIL_CLOSED` au commit
`3a0c297a5151c572417b4f2f12bb5c3ed216172f`. Le sender ne compare plus le `receivedAt` durable
du receiver à la fenêtre murale de sa tentative locale ; il conserve la valeur exacte tout en
exigeant sa forme UTC canonique, sa représentation à la microseconde dans les années
`0001..9999`, les paires HTTP/ACK strictes et toutes les corrélations. La migration append-only V30
ne réécrit ni V29 ni les données existantes et maintient uniquement les gardes d’ordre locales. Les
commandes Maven sont vertes avec Surefire `1136/0/0/5` et Failsafe `89/0/0/0`. Le
[rapport WO-038](docs/validation/J9-WO038-J7-ACK-RECEIPT-TIME-SEMANTICS-QUALIFICATION-20260903.md)
a pour empreinte `QUALIFICATION_REPORT_SHA256=e51bc537c775b4378ee1c6672f86f6c7c085849d62d51970c3d1b6e4aef1395a`. La qualification est
hors ligne ou synthétique loopback, sans modification ni appel du receiver INT-001, sans retry et
sans reprise de WO-036. Les réseaux fournisseur, receiver réel ou distant, le VPS et la production
restent interdits ; la permission officielle demeure `NOT_EVIDENCED`.

Le propriétaire a validé WO-038, reconnu sa readiness locale et autorisé son déplacement vers les
Work Orders terminés le `2026-09-03T12:35:39Z`, soit `2026-09-03T14:35:39+02:00` en
Europe/Paris. Cette validation ne reprenait pas à elle seule WO-036. Une nouvelle décision
propriétaire distincte a autorisé le `2026-09-03T13:02:58.9483990Z`, soit
`2026-09-03T15:02:58.9483990+02:00` en Europe/Paris, une campagne R3 neuve exclusivement
synthétique et loopback avec le receiver local INT-001. Cette autorisation est désormais consommée.

Le Work Order
[WO-SS-20260903-037](docs/work_orders/completed/WO-SS-20260903-037-j9-j7-browser-origin-boundary.md)
est `VALIDATED` avec le résultat `PASS_LOCAL_FAIL_CLOSED`. Le commit runtime
`f28e4b6954c0fb703923f770ab9156326e212a07` réserve `Referrer-Policy: same-origin` au sous-arbre
canonique `/events/{canonicalEventId}/exports/**`, maintient `no-referrer` sur les autres routes et
conserve le refus de `Origin: null`, des Hosts ou Origins hostiles ou dupliqués et des en-têtes
forwarded. Le commit de harnais `2596f0592496b2ba84893c8f4ef2cf0185d46f8f` qualifie un
Spring/Tomcat réel avec services synthétiques en mémoire et Chromium dans un contexte neuf : aperçu
`200`, politique `same-origin`, préparation native `200` avec l’Origin loopback exact et préparation
issue d’une origine opaque refusée `403`. Les appels d’exécution, réconciliation, claim, receiver,
fournisseur et hors loopback, les téléchargements, artefacts interdits et listeners résiduels sont
tous à zéro. Le
[rapport WO-037](docs/validation/J9-WO037-J7-BROWSER-ORIGIN-BOUNDARY-QUALIFICATION-20260903.md)
a pour empreinte `db8993328643a0ecb39eb83ff9eab6223f6c6ca6605b4cacf3e96dea171fab01`.
Le propriétaire a validé les commits runtime, harnais et documentation, reconnu la readiness locale
et autorisé le déplacement du Work Order vers `completed` le `2026-09-03T09:53:43Z`, soit
`2026-09-03T11:53:43+02:00` en Europe/Paris.

Cette validation n’autorisait pas à elle seule la reprise de WO-036. Le propriétaire l’a autorisée
séparément le `2026-09-03T10:10:23Z`, soit `2026-09-03T12:10:23+02:00` en Europe/Paris, uniquement
pour une campagne synthétique Windows/Windows neuve et un manifeste distinct. Cette reprise a été
exécutée, puis arrêtée de façon fail-closed après le deuxième appel receiver.

Le Work Order
[WO-SS-20260902-036](docs/work_orders/completed/WO-SS-20260902-036-j9-j7-local-e2e-qualification.md)
reste actif à `STOPPED_AFTER_FIRST_IMPORT_AND_CONSUMED_B_START_CLAIM`.
Le premier import R2
a produit `201/IMPORTED` et l’état sender `DELIVERED`. La répétition byte-identique a été persistée
idempotemment par le receiver sous `200/DUPLICATE`, sans second payload ni second outbox, mais le
sender l’a classée `UNKNOWN_RECONCILIATION_REQUIRED` avec `ACK_HTTP_STATUS_MISMATCH`. Le
[rapport de reprise WO-036](docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-STOP-20260903.md),
SHA-256 `219f54f2b429b1eaea04c920e55cc87d33f9c5644697129e6ff96fc9cad19062`, établit que le receiver
réemploie conformément à son contrat l’instant durable du premier import, tandis que le sender exige
à tort que cet instant soit postérieur au début de la seconde tentative. Aucun retry ni probe `409`
n’a été exécuté. Le cleanup est complet et le PostgreSQL primaire exact a été redémarré `healthy`.
WO-038 fournit le correctif runtime qualifié et validé. R3 a ensuite été gelée au commit
`f14c1418995625ea653f8d01afd9c81044f3abd1`, manifeste SHA-256
`cd20a30cd855d161fcaa0f5db93ef0c50fca5c297d569d0a4af2f4f02aab842d`, puis exécutée dans un
environnement neuf. A a produit `201/IMPORTED` et `DELIVERED`; B a produit `200/DUPLICATE` et
`DUPLICATE_CONFIRMED`, ce qui confirme WO-038 dans le parcours réel. Le troisième et dernier appel
a toutefois reçu une réponse non-`409`; la claim est consommée, aucun retry n’a eu lieu et la
défense `409/DIVERGENCE_REJECTED` n’est pas qualifiée. L’analyse statique et une reproduction hors
ligne désignent fortement la sérialisation .NET du `Content-Type` par la sonde comme cause, mais le
statut numérique exact n’a pas été conservé dans la preuve runtime. Le cleanup est complet et le
PostgreSQL primaire exact est `healthy`. Le
[rapport R3](docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R3-STOP-20260903.md) consigne
le résultat `STOPPED`, SHA-256
`f59cc0aeaa56fd6c8156032fea7e93048f17f616f1882cc3979bcd69363d1576`. WO-039 a ensuite qualifié
la sérialisation HTTP exacte, puis R4 a été autorisé et exécuté. Son manifeste neuf a été gelé
avant le premier POST ; les résultats `201` et `200` sont conformes et l'effet receiver de
divergence est durable, mais le statut du troisième appel n'a pas été capturé côté client. Le
rapport R4 et la porte R5 sont décrits en tête du présent document. R5 a ensuite été préparé dans
un environnement neuf et son manifeste a été gelé avant le premier POST au commit
`7c7505174c6a01da1c0134cd0564bd49529d0445`. A a produit `201/IMPORTED` et `DELIVERED`. Après
l'arrêt gracieux de A, la claim one-shot de démarrage B a échoué sur la porte du listener exact
`127.0.0.1:8087` avant toute tentative de duplicate. Le journal privé expurgé indique un démarrage
Spring/Tomcat terminé, mais la cause du défaut d'observation reste `NOT_ESTABLISHED`; aucun retry,
second POST ou probe de collision n'a été exécuté. Le cleanup R5 est complet et le PostgreSQL
primaire exact a retrouvé l'état `healthy`. Le
[rapport R5](docs/validation/J9-WO036-J7-LOCAL-E2E-CAMPAIGN-RESUME-R5-STOP-20260903.md) consigne
le résultat `STOPPED`. Une correction de l'outillage sous Work Order distinct, sa validation, une
décision propriétaire séparée, un run R6 neuf et un manifeste R6 gelé avant le premier POST sont
requis. La permission officielle reste
`NOT_EVIDENCED`; aucune donnée dérivée de SofaScore, aucun réseau fournisseur ou distant, VPS,
production, push, fusion, PR ou validation INT-001 n’est autorisé.

Le Work Order
[WO-SS-20260902-035](docs/work_orders/completed/WO-SS-20260902-035-j9-real-j7-delivery-sender.md)
est `VALIDATED` avec le résultat `PASS_LOCAL_FAIL_CLOSED` au commit
`f5a27887b7db43576eb608d564c245c8cca3a602`. Le sender J7 réel du Local Lab est composé avec action
manuelle, confirmation exacte, mTLS Windows, ledger séparé et réconciliation opérateur, tout en
restant désactivé par défaut. La validation propriétaire a été enregistrée le
`2026-09-02T18:57:00Z`, soit `2026-09-02T20:57:00+02:00` en Europe/Paris, avec readiness locale et
déplacement vers `completed` confirmés. La permission officielle demeure `NOT_EVIDENCED` : aucun
appel fournisseur, receiver réel, transfert d'un export dérivé, déploiement VPS ou usage de
production n'est autorisé. WO-036 avait ensuite été ouvert par une autorisation distincte ; la PR
d'INT-001 reste l’étape ultérieure séparée.

Le correctif fournisseur distinct
[WO-SS-20260901-030](docs/work_orders/completed/WO-SS-20260901-030-j9-provider-playwright-port-boundary-hardening.md)
est `VALIDATED` et archivé depuis le `main` issu des PR #20/#21. Le commit
`154349a2fbebe3fd0a43a63c7105f690ff04976b` borne à `[1, 65535]` le port explicite de la
qualification Playwright loopback dans la configuration parente et dans le worker enfant. Le
[rapport WO-030](docs/validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md)
conclut `PASS_LOCAL_FAIL_CLOSED` après `1043` tests standards, `1065` tests du profil runtime et
deux passages de `84` tests d'intégration, sans appel fournisseur ni navigateur. Le propriétaire a
validé le commit et le SHA-256 qualifiés, reconnu la readiness locale et autorisé le déplacement du
Work Order vers `completed` ; cette décision a été enregistrée le `2026-09-01T14:00:53Z`, soit
`2026-09-01T16:00:53+02:00` en Europe/Paris. Elle n'autorise aucun push, merge, réseau fournisseur
ou receiver réel, déploiement VPS ou usage de production.

Le propriétaire a pris la décision finale du jalon **J9 — Décision de gouvernance** :
`PREPARE_OPTIONAL_INTEGRATION`. `WO-SS-20260831-018` et la preuve WO-023 sont désormais validés et
clôturés sur la branche de décision. WO-019 reste la campagne historique `STOPPED` de 20 tentatives ;
son ancienne recommandation `KEEP_LOCAL` a été refusée comme décision finale sans être réécrite.

La nouvelle campagne autonome WO-023 est désormais `VALIDATED` avec un résultat `PASS` : les huit
segments D1/D2/D3 ont produit
28 réponses HTTP 200 et 28 parsings sur 38 tentatives autorisées, sans cache hit, 404, retry ou
incident. La sauvegarde/restauration V28, le double export reproductible et le postflight sont
qualifiés. Le propriétaire a validé WO-023 et autorisé son déplacement vers les Work Orders
terminés à `2026-09-01T05:49:58.398Z`, soit `2026-09-01T07:49:58.398+02:00` en Europe/Paris.
Il a simultanément choisi `PREPARE_OPTIONAL_INTEGRATION` comme décision J9 finale. Cette décision
n'autorise ni implémentation, ni production, ni nouvelle campagne, ni acquisition live ou
planifiée, ni déploiement VPS courant. La permission officielle reste `NOT_EVIDENCED`. Le go est
consommé et le réseau reverrouillé.

Les travaux J9 finalisés sont publiés sur `origin/codex/j9-decision` au commit
`1a58a3bd7673f5946d5c48ae573c191e52d223a2`, sans modification de `main`. Le Work Order
[WO-SS-20260901-026](docs/work_orders/completed/WO-SS-20260901-026-optional-integration-feasibility.md)
a été ouvert depuis cette base pour comparer le push local optionnel à Playwright sur VPS et proposer
[ADR-SS-003 v0.1](ADR-SS-003-optional-integration-topology.md). L'ADR est
`ACCEPTED` depuis le `2026-09-01T07:25:25.4809414Z` : le propriétaire a sélectionné
`OPTIONAL_LOCAL_PUSH` comme direction d’un futur Work Order sous la gouvernance actuelle, tandis
que Playwright VPS reste `DEFERRED_BLOCKED_BY_CURRENT_GOVERNANCE`. L’acceptation ne crée aucun
endpoint et n’autorise aucun réseau, déploiement, intégration, receiver, livraison live ou usage de
production. WO-026 est validé et terminé sur cette décision documentaire.

Le propriétaire a ensuite autorisé l'ouverture et la réalisation du Work Order distinct
[WO-SS-20260901-027](docs/work_orders/completed/WO-SS-20260901-027-optional-local-push-implementation.md)
sur `codex/j9-optional-local-push-implementation`. Ce lot traite le contrat receiver, le sender et
son ledger séparé, mTLS, l'idempotence, les accusés et les qualifications offline/loopback.
WO-027 a atteint `READY_FOR_OWNER_REVIEW` avec le
[rapport de qualification local](docs/validation/J9-WO027-OPTIONAL-LOCAL-PUSH-QUALIFICATION-20260901.md) :
`1036/0/0/5` tests standards et `84/0/0/0` tests d'intégration sont verts, dont les scénarios
PostgreSQL V29, quatre scénarios mTLS et deux parcours end-to-end synthétiques. Le sender reste sans
bean, route, scheduler, origine ou cible réelle ; les états de livraison sont séparés du statut J7,
les réponses sont bornées à 16 KiB, la concurrence vaut `1` et aucune reprise automatique n'existe.

La revue officielle maintient toutefois la permission à `NOT_EVIDENCED` : le socle reste
fail-closed, désactivé et sans cible réelle. L'opacité Java d'une clé est qualifiée, mais la preuve
Windows native de non-exportabilité, la PKI réelle et le receiver Betting Project restent à
qualifier dans leurs Work Orders respectifs. Le receiver Betting Project exige son propre Work
Order dans son dépôt ; aucune livraison réelle, aucun appel fournisseur, aucun VPS et aucune
production ne sont autorisés sous WO-027. Les qualifications ont produit zéro appel fournisseur,
zéro appel vers un receiver réel et zéro listener résiduel.

Un premier bloc propriétaire avait énoncé `VALIDATE` pour le commit
`5af48e5ea0e7150b460fe106da74aa5d3bd5489a`, le résultat `PASS_LOCAL_FAIL_CLOSED` et le SHA-256 du
rapport, tout en laissant deux champs sous forme `<YES|NO>` ; aucune clôture n'en avait alors été
déduite. Le propriétaire a ensuite fourni le bloc complet avec readiness locale `YES` et déplacement
vers `completed` `YES`. WO-027 est donc `VALIDATED` et archivé parmi les Work Orders terminés. La
permission officielle, le receiver réel et tous les usages réseau/VPS/production restent bloqués.

La preuve arrêtée relève de WO-019 ; le profil sélectionné a exigé une nouvelle campagne sous un
nouveau Work Order. ADR-SS-002 v1.0 avait été accepté explicitement. Le premier
contrôle J3 Playwright loopback de WO-019 et sa
contre-qualification hors sandbox avaient produit `12` erreurs `RUNTIME_FAILURE` sur `14` tests à
la fermeture gracieuse. WO-020 a depuis établi la cause : le worker émettait `CLOSED` puis attendait
l'EOF parent, tandis que le parent attendait ou terminait l'arbre avant de produire cet EOF ; le
timeout gracieux de `5 s` était en outre plafonné à tort à la borne opérateur de `2 s`.

Sous WO-020, la correction locale du superviseur inventorie l'arbre après `CLOSED`, ferme la sortie parent,
sépare les modes gracieux et opérateur, laisse `250 ms` à la sortie naturelle, puis conserve le
repli souple à `1 s`, l'acquittement opérateur à `500 ms`, son annulation à `2 s` et le nettoyage
total à `5 s`.
Le worker, le protocole et les endpoints sont inchangés. Les tests du superviseur (`31/31`), du
protocole (`10/10`), de sécurité (`1/1`) et les qualifications loopback J3, J4 et J5 (`14/14`
chacune) sont verts. Les portes Maven sont également vertes avec `931` tests standards et `67`
tests d'intégration ; l'audit final trouve zéro processus possédé, zéro listener 8087 et aucun
artefact navigateur interdit. Aucun accès fournisseur n'a eu lieu.

Le propriétaire a validé WO-020 et autorisé son déplacement vers les Work Orders terminés le
2026-08-31 à `00:41:58Z`. Cette validation runtime ne reprenait pas, à elle seule, WO-019. Le
propriétaire a ensuite autorisé séparément la reprise de WO-019 le 2026-08-31. Cette décision a
d'abord placé WO-019 à `READY_FOR_OFFLINE_READINESS`, avec
`OFFLINE_READINESS=AUTHORIZED_NOT_EXECUTED` et
`WO019_PROVIDER_CAMPAIGN_RESUME_AUTHORIZED=NO` : elle autorisait le rejeu hors ligne, pas la
campagne fournisseur.

Ce rejeu propre à WO-019 a ensuite réussi le 2026-08-31 : `clean verify` compte `931/0/0/4`, l'intégration
`67/0/0/0`, `Verify-Local.ps1 -WithIntegrationTests` rend `PASS` avec intégrations `YES` et réseau
SofaScore `NO`, et `docker compose --env-file .env config --quiet` rend `PASS`. Les qualifications
loopback J3, J4 et J5 exécutées sous `pwsh` comptent chacune `14/0/0/0`, sans accès fournisseur.
La readiness devient `PASS_REEXECUTED_AFTER_VALIDATED_WO020` et WO-019 passe alors à
`READY_FOR_V28_BACKUP_RESTORE`.

Une première session hôte détachée a atteint l'invite `age`, puis a été interrompue avant toute
saisie parce que son onglet n'était pas rattaché ; l'audit après arrêt a confirmé zéro processus,
fichier ou base temporaire résiduel. La tentative manuelle suivante dans le terminal Codex
restreint s'est arrêtée au préflight `DESTINATION_DIRECTORY_NOT_VISIBLE`, avant `age`, `pg_dump`,
fichier ou base temporaire. Ces arrêts locaux ne sont ni des appels ni des retries fournisseur.
L'exécution interactive PowerShell 7 native suivante a qualifié la sauvegarde/restauration : création
`2026-08-31T06:44:41.9669041Z`, qualification `2026-08-31T06:46:07.7013794Z`, archive chiffrée
`6 996 157` octets avec SHA-256
`2b1402d12274f3e9a646aa6134f8cf8bee7a5e11b3249e8dff5c63040cb89d34`, manifeste minimisé
`2 202` octets avec SHA-256
`7d112e4db804125656a54c61edd8c1eb9417f95e8b7c3d4df88d99d92cde6e62`, Flyway `28`, couverture
jusqu'au snapshot `794` reçu à `2026-08-30T20:30:46.412Z` et `restoreQualified=true`. Les contrôles
comptent zéro mismatch source/restauration, zéro échec d'intégrité brute, zéro fichier partiel et
zéro base temporaire résiduelle vérifiée indépendamment ; le connecteur est `SAFE`, le port 8087
est libre, l'accès fournisseur et la purge primaire restent à `NO`.

Le go propriétaire global a ensuite été accordé pour l'unique fenêtre
`[2026-08-31T07:15:00Z,2026-08-31T08:15:00Z)` et consommé irréversiblement au premier claim J3
accepté. Le chemin D1 a exécuté quinze pages J3, une découverte tournoi, un appel J4 et les trois
familles J5 : `20/38` tentatives directes, `20` réponses HTTP `200`, `20` parsings compatibles et
aucun retry.

L'audit postérieur à J5 a toutefois établi que `requested_at` est un timestamp pré-navigation, et
non l'instant exact de départ réseau. L'écart de `2 967 ms` calculé entre ces timestamps persistés
ne prouve ni une violation on-wire, ni le respect du délai strict de trois secondes. Conformément à
la règle d'arrêt de WO-019, la preuve temporelle est `NOT_MEASURED`, WO-019 et la preuve globale sont
`STOPPED`, et D2/D3 n'ont reçu aucun appel.

L'application a été arrêtée gracieusement ; le port 8087, le processus applicatif et les descendants
Playwright sont absents. Les flags fournisseur persistés sont à `false`, l'origine et l'allowlist
persistées sont vides, et le réseau est de nouveau verrouillé. Le go est consommé et terminé par
l'arrêt ; il ne peut pas autoriser une reprise. À ce stade historique, ADR-SS-003 n'existait pas.

WO-021 a produit sur `codex/j9-playwright-minimum-delay` une mesure démontrable et une garantie
loopback du délai minimal de trois secondes. Son implémentation et sa qualification loopback ont
été autorisées, puis validées explicitement par le propriétaire le 2026-08-31. WO-021 est désormais
`VALIDATED` et archivé dans les Work Orders terminés.

Le discriminant préalable a échoué en `0,08 s` avant correction, comme attendu. Le correctif utilise
une horloge monotone réévaluée après chaque réveil, un fence conservateur dans le superviseur parent
— trois secondes complètes après la fin observable du dispatch précédent — et l'observation CDP
`Network.requestWillBeSent` du seul document principal exact. L'identité requête/réponse est
corrélée ; cache navigateur/CDP, service worker, prefetch, redirection ou preuve temporelle
incomplète ferment la série au lieu de produire un timestamp favorable supposé. Le cache métier
cache-first garde sa sémantique distincte : un hit frais vaut zéro appel direct. Le fence couvre
également deux campagnes ou workers successifs.

Le premier rejeu Chromium complet a isolé un défaut de teardown sur le cas timeout :
`Fetch.disable`, `Network.disable` et les détachements CDP synchrones précédaient `page.close()` sur
la navigation encore bloquée, ce qui retardait la trame `TIMEOUT` et produisait `PROTOCOL_ERROR`,
puis `RUNTIME_FAILURE` à la clôture. Le worker ferme désormais d'abord la page pour annuler la
navigation, puis termine le teardown CDP ; le test ciblé passe `1/1` en `5,323 s` et la suite
Chromium complète `14/14` en `101,5 s`. La requalification finale de chaque script J3, J4 et J5
compte `21` tests worker verts — `10` protocole, `1` sécurité et `10` observation réseau — puis `14`
tests Chromium verts.
J5 confirme les écarts `requestedAt`, les arrivées monotones sur le serveur loopback et les écarts
inter-workers `>= 3 s`, avec zéro nouvelle requête lorsqu'un arrêt intervient pendant le fence.
Le premier `Verify-Local.ps1 -WithIntegrationTests` est vert avec `941` tests standards et `67`
tests d'intégration. Le garde d'interruption ajouté ensuite traite toute interruption avant ou
pendant le fence comme une perte de preuve, conserve le statut d'interruption et n'émet aucun
`GET`; les suites ciblées passent `40/40` pour le superviseur et `8/8` pour le coordinateur. Le
`clean verify` final après ce garde passe `943` tests, zéro échec, zéro erreur et quatre skips à
`2026-08-31T09:48:10Z`.

La validation de WO-021 n'a autorisé aucun accès fournisseur. WO-019 reste `STOPPED`, ses `20` tentatives sont
gelées, et l'accès fournisseur, sa reprise, un nouveau go, l'intégration et la production restent
interdits.

WO-022 réalise ensuite le réexamen imposé par ADR-SS-002 §9. Il conclut que v1.0 est
`ACCEPTED_CONSUMED_AND_TERMINATED_BY_STOP` et ne peut pas être réutilisée : l'arrêt après incident
et le fence global exécutoire ajouté par WO-021 déclenchent tous deux une nouvelle version. Le
propriétaire a sélectionné `RESTART_FULL_D1_D2_D3`, puis accepté ADR-SS-002 v1.1 : une nouvelle
série complète de 38 appels maximum, soit 58 cumulés avec les vingt tentatives historiques, incluant
le rejeu D1 et pouvant produire un nouveau verdict autonome.

Cette série ne rouvre pas WO-019. Après l'instruction propriétaire de lancement à usage unique,
WO-023 est ouvert sur `codex/j9-provider-robustness-v11` avec son worktree dédié. Le propriétaire ne
pourra pas réaliser les huit séquences unitaires ; le modèle ponctuel `CODEX_LOCAL_UI` est autorisé
comme acteur de WO-023 sous condition de toutes les portes. Aucun appel fournisseur n'est encore
autorisé : la readiness, la sauvegarde/restauration, le manifeste gelé et un go global lié à une
fenêtre UTC explicite restent requis. Aucun script, orchestration ou automatisation UI n'est autorisé.
Deux échecs interactifs de phrase secrète ont ensuite révélé que la pipeline native
`pg_dump | age` ne terminait pas de manière coordonnée son producteur lorsque le consommateur
quittait prématurément. Les deux arbres de processus et sessions `pg_dump` suspendus ont été arrêtés
et le nettoyage a été vérifié sans archive, base temporaire ni appel fournisseur résiduel.

WO-024 a désormais corrigé et qualifié localement le cycle de vie fail-closed des deux pipelines
`pg_dump -> age` et `age -> pg_restore`. Un hôte attend une porte avant de créer la cible, le
processus est préalablement confiné dans un Job Object Windows `KILL_ON_JOB_CLOSE`, les flux restent
binaires et le succès exige l'arrêt vérifié de chaque arbre, session PostgreSQL, fichier partiel et
base isolée. Le chemin interactif hérite du terminal déjà attaché et ne demande aucune nouvelle
console. Les essais hors ligne et Docker passent, ainsi que 945 tests standards et 67 tests
d'intégration ; aucun processus possédé, listener, session ou artefact ne subsiste.

La contre-épreuve `age` est abstraite, locale et sans secret : elle ne qualifie ni le dialogue TTY,
ni l'auto-génération réelle d'une phrase et ne demande aucune saisie humaine volontairement
incorrecte. Le propriétaire a validé WO-024 et autorisé son déplacement parmi les Work Orders
terminés. Le correctif WO-024 a été intégré par fast-forward dans WO-023, puis la readiness fraîche
a réussi sur le commit `8b91bf8` : 945 tests standards, 67 tests d'intégration, contrôle local avec
réseau `NO`, Compose, trois parcours Playwright loopback à `21` tests worker puis `14` tests Chromium,
tests superviseur `40/40` et coordinateur `8/8`, délai minimal `>= 3 s`, port/processus/artefacts à
zéro, connecteur `SAFE` et Flyway V28. La nouvelle sauvegarde/restauration hors ligne avec la vraie
phrase auto-générée par `age` a ensuite été exécutée une fois. Le chiffrement a terminé avec les
deux processus à `EXIT_0`, copie à EOF et nettoyage local `PASS`, mais la confirmation bornée de la
session PostgreSQL exactement possédée a échoué ou est devenue invérifiable avant toute
restauration et publication finale. Trois observations postérieures trouvent zéro session exacte
et zéro session J6 possédée ; processus exact, fichier final/partiel, base temporaire et listener
8087 sont également à zéro. Ces contrôles prouvent le confinement, pas le succès rétroactif.

WO-023 est donc `BLOCKED_AFTER_BACKUP_CLEANUP_UNCONFIRMED`. L'écart de qualification est factuel :
la commande réelle a utilisé le défaut `5 000 ms`, tandis que les quatre parcours Docker de WO-024
forçaient `10 000 ms`, et le message final n'a pas conservé la cause interne. La tentative unique
est consommée. Un Work Order runtime distinct et une nouvelle décision propriétaire sont requis
avant tout nouvel essai. Réseau fournisseur, campagne, manifeste et nouveau go restent à `NO`,
`NOT_CREATED` ou `NOT_GRANTED`.

La validation standard post-incident a confirmé ce blocage : sur `945` tests, le seul échec est le
scénario synthétique J6 dont la commande bornée n'a pas créé sa preuve PID dans la fenêtre de
`1 500 ms`. La cause reste indéterminée et aucun lien causal n'est affirmé avec l'incident
PostgreSQL. Une entrée synthétique antérieure d'état Windows `Unknown` reste par ailleurs visible
par `tasklist`/CIM alors que les API ordinaires ne peuvent ni l'ouvrir ni la terminer ; un ancien
répertoire temporaire synthétique subsiste aussi hors dépôt. La qualification runtime actuelle est
donc `NOT_REPRODUCIBLE` ; le futur Work Order devra couvrir à la fois la borne PostgreSQL réelle,
la classification sanitée et la preuve PID/absence multi-API.

Le propriétaire a depuis autorisé `WO-SS-20260831-025-j9-backup-cleanup-proof-hardening`. Sur la
branche dédiée, WO-025 a durci et qualifié localement la confirmation PostgreSQL exacte, les bornes
effectives distinctes de `5 000/10 000 ms`, le parsing scalaire, la cause sanitée, le handshake de
cible, l'identité `PID + StartTime`, la corroboration Windows multi-API et le nettoyage du seul temp
root exactement possédé. Les parcours hors ligne et Docker local répétés, les `946` tests standards,
les `67` tests d'intégration, Verify-Local et Compose sont verts sur le commit `d019be2`, sans appel
fournisseur ni purge primaire. Le propriétaire a validé cette readiness le 2026-09-01 et autorisé
le déplacement de WO-025 vers les Work Orders terminés. La preuve ne réattribue ni ne supprime les
résidus historiques non possédés. Le propriétaire a ensuite fourni la décision séparée requise et
autorisé un unique nouvel essai local chiffré de sauvegarde/restauration WO-023. Cet essai est
qualifié : archive `age` de 7 268 470 octets, restauration V28 identique, couverture maximale 814,
116 tentatives J8 couvertes, intégrité brute et mismatches à zéro, puis zéro session, base temporaire,
processus natif, listener ou fichier partiel résiduel. L'autorisation est consommée et n'est pas une
autorisation de campagne. Le ledger indépendant a ensuite retrouvé les 20 tentatives historiques,
zéro tentative WO-023 et zéro doublon ; le manifeste de campagne est gelé au SHA-256
`ff909f298f7d3c99b1027c071ac4d783a19529f6241b37941151c2c08c418a9e`. WO-023 est prêt à recevoir
un nouveau go propriétaire global lié à ce manifeste et à une fenêtre future, mais réseau
fournisseur, campagne, go courant, purge primaire, intégration et production restent interdits.

Le go global finalement reçu pour la fenêtre
`[2026-08-31T23:45:00Z,2026-09-01T00:45:00Z)` a été consommé une fois. A1 à B4 ont terminé avec
28/28 réponses et parsings, 24 snapshots insérés et quatre réponses fraîches dédupliquées, pour
28/38 nouvelles tentatives et 48/58 cumulées avec WO-019. D2 et D3 sont complets sur les trois
familles J5 ; D1 reste partiel à 91 % pour les incidents et 99 % pour les compositions. Le délai
minimal observé est supérieur à trois secondes, l'export J8 est byte-identique sur deux exécutions,
et l'arrêt final ne laisse aucun listener, worker, navigateur possédé ni artefact Playwright. Le go
est terminé par l'achèvement D3 et ne peut pas être rejoué.

La revue officielle factuelle a relevé des restrictions sur les requêtes automatisées, le scraping,
l'agrégation et l'extraction substantielle sans consentement explicite ; aucune permission, licence
ou limite d'API applicable aux endpoints du laboratoire n'a été extraite. Ce constat n'est pas une
conclusion juridique ; le propriétaire avait explicitement reconnu cette revue dans son acceptation
d'ADR-SS-002 v1.0.

L'usage futur de Playwright sur un VPS de production n'est plus exclu comme option d'architecture,
mais il reste `NOT_MEASURED`, `NOT_AUTHORIZED` et `BLOCKED_BY_CURRENT_GOVERNANCE`. Le dépôt conserve
`LOCAL_ONLY` et `NOT_PRODUCTION_APPROVED` ; ADR-SS-001 maintient les appels fournisseur hors du VPS
et les payloads bruts localement. WO-026 a mené l'étude factuelle. ADR-SS-003 v0.1 acceptée
sélectionne le push local d'un export J7 déjà `HUMAN_VALIDATED` comme seule direction candidate
compatible avec les règles actuelles, et diffère la topologie VPS jusqu'à une décision de
gouvernance, une permission officielle et des preuves VPS distinctes. L’acceptation ne vaut pas
implémentation. Chaque topologie conserve ses propres portes de droits d'usage, réseau, navigateur,
secrets, exploitation, frontière de données et non-dépendance critique.

Le dépôt matérialise les jalons validés **J0 — Gouvernance**, **J1 — Bootstrap**, **J2 — Fixtures**,
**J3 — Appel manuel**, **J4 — Événements**, **J5 — Statistiques**, **J6 — Historique**,
**J7 — Export canonique** et **J8 — Benchmark**. J8 est `VALIDATED` : après la campagne initiale
partielle et le correctif V15, une seconde campagne complète explicitement autorisée a terminé vingt
unités sur vingt avec un rapport automatique `MEASURED / FULL_ATTEMPT_LEDGER`. Le bloc automatique
reproductible a été gelé, la revue ciblée a été acceptée avec ses limites explicites et WO-016 est
clôturé. Les dimensions non observables restent `NOT_MEASURED`; aucune troisième campagne ni
conclusion d'adoption J9 n'est autorisée.

Le correctif borné WO-017 fournit `event-incidents-v15` et Flyway V28 : propriété d'actions absente
et tableau exactement vide sont équivalents uniquement dans une séance terminale non minutée déjà
cohérente. Après la readiness à zéro appel, une campagne fournisseur corrective distincte,
explicitement autorisée par le propriétaire et exécutée une seule fois par Codex sur l'interface
locale, a terminé `COMPLETED_LOCKED` avec trois appels. Les incidents de la réponse dédupliquée
vers le snapshot `717` sont désormais parsés par V15 en `PARTIAL · 91%`, observation `324`, puis
les compositions ont été atteintes. La preuve J8/V14 et sa fenêtre exclusive restent immuables ;
le propriétaire a jugé le test fonctionnel concluant et WO-017 est désormais clôturé `VALIDATED`.

J7 a franchi les portes techniques, la recette humaine et la revue de publication et appartient
désormais à la baseline `main`. L'implémentation J4, son parcours hors ligne et ses deux
sous-étapes réelles bornées sont qualifiés humainement. La sous-étape 1 a validé `16386245` et
`16421052` après correction du retour par date. La sous-étape 2 a validé la saisie d'identifiants,
le rappel manuel avec une nouvelle confirmation, la déduplication d'une réponse inchangée et la
création d'une observation append-only lorsque `16412917` est passé de `notstarted` à
`inprogress`. Après l'arrêt global, la configuration a été remise à l'état bloqué, ce verrouillage
a été vérifié après redémarrage et l'application a été arrêtée gracieusement. La Pull Request `#8`
a été fusionnée et le Work Order J4 est archivé `VALIDATED`. Les voies fournisseur restent
désactivées par défaut ; une configuration locale explicitement armée peut réunir J3, J4 phase 2
et J5 dans une même instance, avec une seule requête fournisseur active et un délai minimal
partagé. Une recette réelle a depuis achevé, dans un même démarrage, J4 phase 2, les trois familles
J5 puis une collecte J3 paginée sur six pages. La forme J3 `scheduled` compte des compétitions
disponibles pour la date et ne fournit pas de rencontres programmées ; le reparsage J4 l'indique
désormais sans présenter son total nul de matchs comme une anomalie. Aucun appel fournisseur n’est
exécuté par Maven, conformément au document de cadrage
`Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf` et à l’ADR `ADR-SS-001`.

Le Work Order `WO-SS-20260827-014` est `VALIDATED` depuis le `2026-08-28`. Il migre
`EVENT_DETAILS` vers le runtime Playwright commun de WO-013, avec le `404` indisponible sans parsing
ni retry, la poursuite de la seconde cible fixe en phase 1 et le rafraichissement manuel phase 2 sans
lecture du cache. La readiness finale compte 728 tests standards, 52 tests PostgreSQL et 22 tests
Playwright loopback, tous verts. La recette humaine a confirme un rafraichissement J4 termine et la
non-regression de J3 Playwright sur la collecte paginee et la decouverte tournoi.

Le Work Order `WO-SS-20260827-015` est `VALIDATED` depuis le `2026-08-29`. Il migre J5 vers ce
runtime commun. Les familles `EVENT_STATISTICS`,
`EVENT_INCIDENTS` et `EVENT_LINEUPS` sont transmises dans cet ordre par un seul worker, un seul
`BrowserContext` non persistant et une seule lease, via le protocole IPC v5. Un `404` raw-first est
conserve comme `ENDPOINT_UNAVAILABLE` et la famille suivante continue ; tout autre incident est
terminal. Le cache fournisseur J5 est `NOT_APPLICABLE`. Il n'existe aucun transport direct,
fallback FlareSolverr, retry ou second contexte. Les tests cibles du transport et les scenarios
Chromium loopback `200/404/200` et arret pendant `INCIDENTS` sont verts, sans acces fournisseur ;
les portes globales finales comptent 765 tests standards, 53 tests PostgreSQL, 11 tests de
protocole/securite et 14 tests Chromium loopback. La recette proprietaire a termine J5 en
`COMPLETED_LOCKED` avec trois appels et trois familles completes, puis confirme J4 et J3 dans le
meme environnement Playwright. Si le nettoyage du worker ne peut pas etre confirme, la lease fournisseur reste
retenue et bloque J3/J4 jusqu'au redemarrage du processus. V26 et le parseur incidents V14 de WO-012
restent intacts, et WO-015 n'ajoute aucune migration. L'arrêt global J5 pose en outre un latch de
processus : il conserve l'état et le code terminaux historiques, mais interdit toute nouvelle
préparation J5 jusqu'au redémarrage. La validation ponctuelle est consommee ; tout nouvel appel
exige un go proprietaire distinct.

L'évolution **J3 → J5 — découverte tournoi → rencontres** est `VALIDATED` depuis le 2026-08-21 et
son Work Order est clôturé dans `docs/work_orders/completed`. Après une collecte J3 `COMPLETED` du processus courant,
elle expose les occurrences de tournois observées, résout côté serveur le
`tournament.uniqueTournament.id` numérique, normalise atomiquement les rencontres de la phase et
de la journée `Europe/Paris`, puis affiche leurs liens J5 sans exiger J4. Après confirmation,
l'opérateur choisit soit au plus un GET direct, soit l'import local du seul corps JSON obtenu
manuellement. Cette seconde voie exécute zéro transport, refuse HAR/en-têtes/cookies, reste bornée
à 5 Mio et conserve une provenance distincte. Les tests automatisés exécutent zéro appel
  fournisseur ; toutes les voies restent désactivées par défaut. Depuis chaque rencontre retenue,
  la campagne J5 propose également un choix explicite : trois GET ordonnés, ou l'import local de
  trois preuves statistiques, incidents et compositions. Chaque preuve est un fichier JSON ou une
  déclaration fermée « 404 observé » produisant une enveloppe locale canonique. Le lot est prévalidé
  avant la confirmation, conserve trois snapshots de provenance locale et exécute zéro appel fournisseur ;
un terminal direct sur 403 exige toujours un redémarrage et une nouvelle préparation.

Les noms fournisseur utilises dans le libelle du catalogue sont controles sans etre reecrits. Le
correctif de recette exclut de la projection une occurrence deja parsee dont le nom de phase ou de
tournoi unique contient un caractere ISO de controle, comme la tabulation et le saut de ligne
observes sur deux phases distinctes. Les snapshots bruts et les options sures du meme catalogue
restent inchanges. Les noms blancs, les categories invalides et les conflits entre occurrences
restent soumis aux invariants stricts du parseur et du catalogue.

Le Work Order `WO-SS-20260822-010`, `VALIDATED` et clôturé le 2026-08-23, ajoute un parcours J5
distinct, exclusivement hors ligne, pour les journées chargées. La page `/j5-import-batches`
permet de cocher de 1 à 25
événements canoniques issus d'une recherche date/zone, prépare pendant quinze minutes un plan
  mémoire déterministe, puis accepte exactement les `3N` preuves nommées par le serveur, sous forme
  de fichier JSON XOR déclaration 404. Toutes les preuves et l'état canonique sont prévalidés avant
  le claim ; une transaction PostgreSQL unique
écrit ensuite le lot ou le rollback intégralement. Ce parcours accepte
`SOFASCORE_ENABLED=false` comme `SOFASCORE_ENABLED=true`, exige les automatisations coupées et la
conservation brute active. Même lorsque les voies fournisseur sont armées, le parcours local
n'invoque ni transport, ni cache fournisseur, ni coordinateur réseau.

La qualification humaine couvre les lots nominaux, les déclarations 404, les manifestes en conflit
ou incomplets, leur correction avant envoi, l'annulation, le redémarrage sans reprise et le réimport
dédupliqué avec occurrences append-only. Les tests PostgreSQL comparent les comptes avant et après
une prévalidation refusée et provoquent un incident sur la dernière famille du dernier match pour
confirmer le rollback atomique de tout le lot.

La configuration métier combinée J3/J4/J5 peut rester armée pendant l'import : avec
`SOFASCORE_ENABLED=true`, les opt-ins, l'origine et les six endpoints autorisés, son périmètre est
valide sans rendre le lot local inéligible. Elle n'arme toutefois pas à elle seule le transport J3
Playwright : une campagne J3 exige le lanceur local explicite décrit plus bas. La campagne terminale
de qualification a importé trois rencontres et neuf fichiers, contrôlé 450 124 octets, produit six
nouvelles versions et trois déduplications, avec neuf familles `COMPLETE · 100%` et zéro appel
fournisseur, cache, coordinateur ou retry.

Le jalon **J5 — Statistiques** est validé techniquement et humainement sur sa frontière hors ligne :
statistiques, incidents et compositions synthétiques, contrôles explicites de complétude,
persistance append-only V7 et écran local. Le Work Order séparé `WO-SS-20260815-006` a ajouté une
voie de qualification réelle gardée, désactivée par défaut, limitée à une identité canonique par
campagne et à trois appels confirmés. Les campagnes humaines ont successivement qualifié le HTTP
`404` d'une famille facultative, la sentinelle fournisseur `addedTime=999`, le reparsing
append-only d'un brut dédupliqué, les participants `playerIn` / `playerOut`, le réarmement explicite
après succès et le carton de banc dont la minute métier est portée par `benchTime`.

La campagne Paris Saint-Germain — Lens collectée en première mi-temps a terminé les trois familles
sur les snapshots 61/62/63. Son rejeu manuel après la fin du match a de nouveau terminé les trois
familles sur les snapshots 64/65/66, avec 22 incidents et les deux compositions. Le retest V6 a
ensuite qualifié les règles complètes : marqueurs `HT`/`FT`, carton de l'entraîneur, durées des
temps additionnels et participants applicables sont visibles, avec une campagne terminée sur les
snapshots dédupliqués 64/65 et le snapshot compositions 69.

Une campagne Arsenal — Manchester City a alors révélé une neuvième variante de contrat au sein du
type déjà connu `substitution` : le remplacement de Jérémy Doku par Jack Grealish sur blessure porte
simultanément `incidentClass="injury"` et `injury=true`. `event-incidents-v7` ajoute uniquement cette
classe attestée, conserve le booléen et les deux joueurs, et refuse la contradiction explicite
`incidentClass="injury"` avec `injury=false`. La migration V14 autorise cette provenance sans
réécrire l'historique V1–V13. Quatre campagnes V7 complètes du 2026-08-17, dont France — Maroc et
Argentine — Autriche avec remplacement sur blessure, ont qualifié ce contrat en conditions réelles.

Deux payloads opérateur supplémentaires ont ensuite isolé les variantes prises en charge par
`event-incidents-v8`. Le premier contient un marqueur terminal de séance `PEN` avec `time=999`,
neuf `penaltyShootout` sans minute globale et des minutes effectives imbriquées jusqu'à 146. Le
second contient trois cartons sans `reason` ; ce caractère facultatif était déjà supporté, mais le
même document restait bloqué par trois buts ordinaires portant `from="shot"`. V8 normalise le seul
marqueur `PEN` exact à la dernière minute effective de la séance, conserve les cartons sans motif,
omet `shot` de l'origine spéciale normalisée et accepte le triplet cohérent
`missed/Woodwork/woodwork` pour `inGamePenalty` comme pour `penaltyShootout`. Les deux payloads
complets passent hors ligne sans être ajoutés au dépôt. La migration V15 autorise cette provenance
sans nouvelle colonne ni réécriture historique.

Le lot de 48 captures du retest humain V8 du 2026-08-18 a ensuite documenté trois campagnes
terminées. IF Gnistan — Ilves a confirmé les cartons sans motif et la poursuite jusqu'aux
compositions malgré des statistiques indisponibles. Al Orobah — Abha a reparsé à `36/36` le
snapshot 139 auparavant rejeté par V7, avec neuf tirs au but et le marqueur `PEN` normalisé à 146.
Samsunspor — Göztepe a affiché `inGamePenalty/missed/Woodwork` dans une observation V8 complète à
`102/102`. Ces preuves conservent leur valeur de non-régression historique.

Une campagne ultérieure Cardiff City — Wrexham (`16391145`) a toutefois révélé une nouvelle dérive
fournisseur : après les statistiques complètes du snapshot 161, le snapshot incidents 162 a été
conservé en HTTP `200` puis rejeté par V8 sur le motif de carton `Other reason`. La campagne s'est
correctement verrouillée après deux appels, sans retry ni appel aux compositions. Le parseur
`event-incidents-v9` ajoute uniquement cette valeur attestée et interprète strictement
`benchAddedTime` comme temps additionnel d'un carton de banc, ce qui normalise le cas observé à
`90+9`. Le payload complet fourni par l'opérateur passe hors ligne avec 20 incidents sur 20 ; la
migration V16 autorise V9 sans réécrire V1–V15. Le retest humain V9 a ensuite terminé la campagne
après exactement trois appels : snapshots 161/162/165, incidents `COMPLETE · 100%` dans
l'observation 77, carton `Other reason` rendu à `90+9`, puis compositions `85/85` dans
l'observation 78. À cette étape historique, V9 était qualifié de façon réelle et bornée ; seul le
reverrouillage local après redémarrage restait alors à prouver. Cette preuve est désormais acquise.
Les fixtures synthétiques conservent naturellement `providerSchemaValidated=false`.

Une campagne ultérieure sur l'événement `16251993` a de nouveau invalidé ce statut courant. Après
les statistiques complètes du snapshot 178, `event-incidents-v9` a conservé le snapshot 179 en
HTTP `200`, puis rejeté cinq buts portant le tuple redondant
`incidentClass="regular"` / `from="regular"`. La campagne s'est correctement arrêtée à
`FAILED_LOCKED` après deux appels, sans retry ni compositions. `event-incidents-v10` accepte
uniquement ce tuple exact, conserve `from` dans le brut et l'omet de l'origine spéciale normalisée.
Le payload opérateur complet passe hors ligne à 7/7 incidents et `24/24` signaux. La migration V17
préserve l'historique V9 ; 348 tests standards et 31 tests d'intégration passent. À ce stade, le
schéma restait `PROVIDER_SCHEMA_VALIDATED=NO` dans l'attente d'un retest humain.

Une observation opérateur supplémentaire a attesté `reason="Off the ball foul"` sur un incident
`card/yellow` à la minute 77, valeur métier correspondant à une obstruction ou faute loin du
ballon. `event-incidents-v11` ajoute uniquement ce motif exact au vocabulaire fermé, le conserve
tel quel dans la donnée normalisée et l'affiche sans traduction silencieuse. V10 rejette encore la
même forme sur `reason`, et toute autre valeur inconnue reste incompatible. La migration V18
préserve l'historique V10. Un premier retest V11 sur `16251993` a qualifié les cinq buts
`regular/from=regular`. Une campagne réelle ultérieure sur Barracas Central — Rosario Central
(`16671566`) qualifie dans le même démarrage le parcours J4 phase 2 puis J5 : J4 termine après un
appel et le snapshot 183, puis J5 termine trois appels sans retry avec les snapshots 184/185/186.
Le snapshot incidents 185 est complet à `69/69` et rend le carton `Off the ball foul` inchangé à
la minute 77. Cette qualification V11 reste une preuve historique bornée. Une campagne plus récente
sur `16691018` a conservé les statistiques indisponibles dans le snapshot 188, puis rejeté sous V11
le snapshot incidents 189 après deux appels sans retry : le marqueur `PEN` et les quatorze tirs au
but ne portent aucune minute exploitable. Les sept tirs ratés omettent aussi `reason` et
`description`, mais ces absences métier ne sont pas la cause du rejet.

`event-incidents-v12` accepte exclusivement une séance terminale entièrement non minutée et
cohérente : marqueurs de fin contrôlés, séquences uniques et contiguës, scores complets et score
final concordant. Il persiste la minute absente et affiche `—`, sans la déduire de la séquence.
L'absence simultanée de motif et de description sur un penalty raté reste `PARTIAL` pour
`inGamePenalty` comme pour `penaltyShootout`; les tuples fournis, notamment
`missed/Woodwork/woodwork`, restent stricts. Le payload opérateur complet passe hors ligne avec 33
incidents, 14 tirs au but et 15 avertissements temporels. La migration V19 réserve les minutes
nulles à ce contexte sans réécrire l'historique. Le retest humain sous le parseur courant V13 a
ensuite terminé les trois appels sur l'événement `16691018` : statistiques indisponibles dans le
snapshot 188, incidents snapshot 189 / observation 100 à `PARTIAL · 83% · 142/171`, puis
compositions snapshot 192 / observation 101 à `PARTIAL · 98% · 94/95`. Le marqueur `PEN` et les
quatorze tirs au but affichent `—`, les tirs ratés sans motif sont conservés et aucune minute n'est
inventée.

Une nouvelle observation opérateur a ensuite attesté `reason="Leaving field"` sur un
`card/yellow` à la minute 74, pour un joueur quittant le terrain sans autorisation préalable.
`event-incidents-v13` hérite intégralement des contrôles V12 et ajoute uniquement ce libellé exact
au vocabulaire fermé des cartons. V12 rejette encore la forme, V13 conserve et affiche le motif
sans le traduire, et une autre valeur inconnue reste incompatible. La migration V20 autorise la
nouvelle provenance sans réécrire l'historique ni modifier la contrainte temporelle V19. Les suites
courantes passent 369 tests standards et 35 tests d'intégration sans appel fournisseur. La
campagne humaine Shanghai Shenhua — Beijing Guoan (`16851672`) a ensuite terminé les trois appels :
statistiques snapshot 194 / observation 102 à `256/256`, incidents snapshot 195 / observation 103
à `COMPLETE · 81/81`, puis compositions snapshot 196 / observation 104 à `COMPLETE · 97/97`. Le
carton de Yongjing Cao conserve exactement `Leaving field` dans `MOTIF` à la minute 74. Les deux
formes V13 sont donc qualifiées dans cette portée réelle bornée. Le contrôle visuel final confirme
également la présentation corrigée, le reverrouillage local, les états J4/J5 `LOCKED` après
redémarrage et l'arrêt final de l'application.

Deux nouvelles preuves opérateur ont ensuite attesté le marqueur de période
`text="Extra time"`, `isLive=true` pendant une prolongation. `event-incidents-v14` hérite de V13,
ajoute seulement ce libellé live exact, le conserve sans le transformer en `ET` et refuse la
contradiction explicite `isLive=false`. V13 reste immuable et rejette encore la forme. La migration
append-only V26 autorise la nouvelle version de parseur sans colonne ni réécriture historique. La
qualification fonctionnelle propriétaire finale du 2026-08-27 valide deux imports locaux à zéro
appel fournisseur : 24 incidents et `86/86` signaux, puis 28 incidents et `99/99` signaux. Les
deux observations restent `COMPLETE · 100%` sous `event-incidents-v14` et conservent `Extra time`
à la minute 120, avec un score évoluant de `1-1` à `1-2`.

Le jalon **J6 — Historique** est désormais implémenté et validé. Il ajoute une
chronologie locale des cinq flux d'un événement, des différences sémantiques entre versions, la
détection des enrichissements et corrections tardifs, ainsi qu'une rétention manuelle des seuls
octets bruts. Cette rétention reste sans bouton Web, limitée à 500 snapshots par lot, protégée par
un aperçu haché, une confirmation exacte et une sauvegarde chiffrée restaurée avec succès. La
qualification humaine complète de l'interface est acceptée sur dix-huit captures hors dépôt, avec
un second import à zéro ajout. La qualification opératoire sauvegarde/restauration est également
acceptée : l'archive `age` couvre le snapshot 272, les treize mesures restaurées sont identiques à
la source, la base temporaire a été supprimée et un second lancement refuse tout écrasement. J6 est
donc `VALIDATED`, sans purge de la base primaire.

Le jalon **J7 — Export canonique** assemble, sans réseau, les dernières observations locales d'un
seul événement dans une enveloppe JSON v1 autonome. Le parcours crée d'abord un candidat
`COHERENCE_CHECKED`, puis exige une validation ou un rejet humain explicite après aperçu. Seul
`HUMAN_VALIDATED` est téléchargeable. Les cinq provenances, la complétude, les avertissements et
les hashes restent auditables ; aucun payload brut, secret, session, transport vers le Betting
Project ou appel SofaScore n'est inclus. L'implémentation est `VALIDATED` après passage des portes
techniques, de la recette du runbook J7 et de la revue de publication. La PR `#12` a été revue puis
fusionnée vers `main` ; J7 constitue donc la baseline de départ de J8.

Le jalon **J8 — Benchmark reproductible, audité et borné** ajoute une preuve prospective
append-only au niveau de chaque campagne, unité, tentative fournisseur et issue terminale. La page
locale `GET /benchmark` agrège sans réseau les niveaux `FULL_ATTEMPT_LEDGER`, `RESPONSE_ONLY` et
`LEGACY_BASELINE` sans les promouvoir artificiellement en une mesure exacte. L'exporteur local
explicite produit le même modèle numérique en Markdown sous `exports/j8/`. Toute valeur sans
dénominateur fiable reste `NOT_MEASURED`. La première campagne bornée du 2026-08-30 conserve son
ledger de 19 tentatives et son rapport `PARTIAL` après l'arrêt J5 V14, sans retry ni appel
compositions. Le correctif V15 n'a réécrit aucune de ces preuves.

Une campagne J5 corrective ultérieure, instrumentée par le ledger J8 mais extérieure à cette
fenêtre exclusive, a qualifié V15 sur les mêmes octets du snapshot incidents 717. Elle ne reclasse
pas l'unité J8 historique, ne constitue pas un retry du benchmark et ne transforme pas son rapport
`PARTIAL`. En revanche, une lecture dynamique de tout l'historique inclut normalement ce nouveau
ledger ; toute comparaison reproductible doit donc continuer d'utiliser la fenêtre et l'`asOf`
gelés.

Après un second go propriétaire distinct, une nouvelle fenêtre exclusive
`[2026-08-30T09:24:51.0887925Z,2026-08-30T09:44:03.2695965Z)` a terminé le parcours complet :
quinze pages J3, une découverte tournoi, J4 phase 2 et les trois familles J5. Les vingt tentatives
ont toutes reçu HTTP 200 et produit `PARSED`, dont les incidents sous `event-incidents-v15` puis
les compositions. Les deux exports de 15 202 octets sont byte-identiques, SHA-256
`ffed40714a7c13f79273d7ddfacd15b02fdd877a2e946f6b843ba63e6b8cfb25`, avec un hash de population
`c61b3ef3a9ac12f94d787da8c396dae58e4208a6f04aa240538e38eac5ab4726` et l'état `MEASURED`. Un
dossier sur un est exploitable; les coûts exacts sont 16 appels de découverte, 4 marginaux et 20
effectifs par dossier exploitable. Le reverrouillage après redémarrage inerte est prouvé et
l'application est arrêtée. Le dernier export non-Web a reproduit exactement le bloc automatique à
zéro appel fournisseur; la revue ciblée conserve `PARTIAL` et `NOT_MEASURED` lorsque nécessaire.
Le rapport final est gelé sous `docs/benchmark/`, la phase est `J8-BENCHMARK-VALIDATED` et aucune
troisième campagne ou décision J9 n'est autorisée.

## Ce qui est livré localement

- dépôt Git autonome, documentation, ADR, règles agent et Work Orders ;
- Java **25 LTS**, Spring Boot **4.1.0** et Maven Wrapper versionné ;
- interface Spring MVC + Thymeleaf sur `127.0.0.1:8087` ;
- PostgreSQL local dans Docker Desktop, migrations Flyway V1 à V30 et stockage brut séparé ;
- Actuator, Caffeine, validation de configuration et garde de liaison locale ;
- catalogue logique des familles d’endpoints, sans URI réelle ;
- connecteur verrouillé dans le code au mode `LOCKED_OFFLINE_J3_POLICY` ;
- tests unitaires hors ligne et test Flyway/Testcontainers dans un profil explicite ;
- scripts PowerShell de configuration, préflight, démarrage, arrêt et vérification.
- corpus synthétique `SCHEDULED_EVENTS` de douze fixtures classpath avec hashes vérifiés ;
- parseur hors ligne `scheduled-events-v1`, compatible avec le corpus J2 `events` et avec la forme
  fournisseur qualifiée `scheduled`, modèle local et tests de rupture de schéma ;
- inventaire du corpus visible dans le tableau de bord, sans dépendance à PostgreSQL.
- politique J3 hors ligne pour l’activation explicite, la confirmation par appel, le cache préalable, le délai minimal et l’arrêt global ;
- circuit J3 en mémoire initialisé à `LOCKED`, incidents typés et garde atomique limitant la concurrence à un appel ;
- persistance J3 des octets bruts avec taille, SHA-256, métadonnées bornées et déduplication par requête ;
- transport J3 simulé limité à `127.0.0.1`, derrière la politique manuelle et la garde de concurrence ;
- contrôle J3 visible avec arrêt global, activation distincte et confirmation exacte d’une intention datée ;
- jeton de formulaire local lié à la session et à usage unique, sans rendre l’action fournisseur disponible ;
- matrice J3 d’incidents simulés avec conservation du brut avant parsing, circuit ouvert et aucun retry automatique ;
- qualification Windows du parcours opérateur local et des politiques simulées, avec preuve explicite
  que l’action fournisseur reste indisponible ;
- chemin J3 dédié à une collecte manuelle explicite démarrant toujours en page `1`, poursuivie
  uniquement tant que le parseur retourne `hasNextPage=true` et bornée localement à 25 pages ;
- client fournisseur sans proxy, redirection, cookie, jeton, compte ou donnée de session, avec arrêt
  au premier incident et aucun retry ;
- parcours répétable uniquement après réarmement, activation, nouvelle intention datée,
  confirmation exacte et action finale distincte, sans polling ni retry ;
- cache réel du parcours dynamique consulté avant chaque transport sur la clé exacte date/page :
  seuls les snapshots `PARSED` frais selon le TTL de dix minutes et le parseur courant sont relus
  hors ligne ; un cache hit ne déclenche ni transport, ni attente, ni mutation de persistance ;
- alternative J3 après la même confirmation : import atomique des seuls corps JSON
  `page-1.json` à `page-N.json`, avec prévalidation de la pagination, 5 Mio maximum par page,
  25 Mio maximum au total, zéro transport/cache et preuve minimisée v5 portant
  `LOCAL_JSON_IMPORT` ;
- catalogue de tournoi reconstruit uniquement depuis les snapshots exacts de la dernière preuve J3
  `COMPLETED` du processus courant, avec contrôle pages/clé/hash/parseur et aucune reconstruction
  implicite après redémarrage ;
- liste déroulante au libellé exact `tournament.name - tournament.category.name`, postant seulement
  `tournament.id`, résolution serveur du `tournament.uniqueTournament.id` numérique et requête fermée
  `/api/v1/unique-tournament/{id}/scheduled-events/{date}` ;
- parser `tournament-scheduled-v1`, cache de dix minutes étendu par V24, projection de la phase sur
  la journée semi-ouverte `Europe/Paris`, contrôle du nombre et canonicalisation transactionnelle ;
- import alternatif du seul corps JSON du second endpoint après sa confirmation : zéro transport,
  zéro cache fournisseur, limite 5 Mio, scanner sensible et provenance
  `MANUAL_LOCAL_JSON_IMPORT` distincte de `DIRECT_LOCAL_ENDPOINT` ;
- liens directs vers J5 pour les rencontres retenues, sans appel `EVENT_DETAILS`, campagne J4,
  saisie manuelle d'identifiant ou lancement automatique de J5 ;
- alternative J5 après sa confirmation : import atomique de trois preuves locales, chacune fournie
  par fichier JSON ou déclaration 404 explicite, 5 Mio maximum par fichier, scanner sensible,
  validation complète avant claim, zéro transport/cache et snapshots
  `MANUAL_LOCAL_JSON_IMPORT` normalisés dans l'ordre statistiques → incidents → compositions ;
- lot J5 multi-match hors ligne séparé sous `/j5-import-batches` : sélection de 1 à 25 UUID
  canoniques depuis une recherche date/zone, plan mémoire de quinze minutes, `3N` noms stricts et
  exactement une preuve fichier XOR déclaration 404 par nom, 5 Mio par fichier et 25 Mio cumulés,
  prévalidation complète puis transaction atomique unique ;
  provenance V25 et occurrences J6 sont réutilisées sans migration ni historique durable du lot ;
- catalogue local limité à 50 métadonnées de snapshots bruts et inspection JSON explicite d’une
  ligne, avec contrôle taille/SHA-256, blocage des contenus sensibles, parsing strict, rendu HTML
  échappé et réponse `no-store`, sans transport, téléchargement ou mutation ;
- identité canonique J4 déterministe par paire `(provider, providerEventId)`, indépendante des
  noms, horaires et statuts mutables ;
- observations normalisées J4 append-only, dédupliquées par empreinte et toujours reliées à leur
  snapshot ou fixture, leur SHA-256, leur parseur et leur heure de réception ;
- contrat synthétique `event-details-v1`, rattachement strict à l’identité locale et stockage du
  détail sans URI, transport ou donnée fournisseur réelle ;
- recherche locale `/events` par date civile et zone IANA, page de détail et chronologie des
  observations, avec import de démonstration synthétique idempotent ;
- parseur fournisseur `event-details-v2` séparé du contrat historique V1, acceptant uniquement
  l’enveloppe `event` et produisant une incompatibilité explicite sans objet partiel ;
- migration V6 ajoutant la provenance `PROVIDER_SNAPSHOT` aux détails sans modifier V1–V5, et
  cache `EVENT_DETAILS` de quinze minutes pointant toujours vers le brut séparé ;
- voie J4 sous-étape 1 limitée par construction à `https://www.sofascore.com`, au chemin exact
  `/api/v1/event/{eventId}` et aux seuls IDs `16386245` et `16421052` ;
- circuit J4 local avec préparation, confirmation exacte, cache préalable, délai minimal de trois
  secondes, deux tentatives maximum, persistance brute avant parsing et verrou terminal ;
- arrêt sans retry au premier incident, `403`, `429`, `5xx`, timeout, contenu non JSON,
  incompatibilité ou incohérence d’identifiant ;
- voie J4 sous-étape 2 sélectionnée par un opt-in distinct, avec un ID numérique saisi dans
  l'interface et lié à une confirmation de cinq minutes ;
- rafraîchissements manuels répétables du même événement : un nouvel appel sans cache par cycle,
  nouvelle confirmation obligatoire, délai minimal de trois secondes et aucune boucle automatique ;
- contrats synthétiques J5 `event-statistics-v1`, `event-incidents-v1` et `event-lineups-v1`, avec
  parsing JSON strict, avertissements bornés et aucune coercition de type ;
- contrôles J5 `COMPLETE`, `PARTIAL`, `EMPTY_VALID` et `UNAVAILABLE`, score déterministe et chemins
  manquants, sans valeur, incident ou joueur inventé ;
- migration V7 conservant les trois familles sous forme d'observations et de lignes normalisées
  append-only, dédupliquées et rattachées à l'identité canonique J4 ;
- page locale `/events/{canonicalEventId}/statistics` avec import synthétique idempotent, valeurs,
  complétude, provenance, parseur et hashes, sans payload ni repli fournisseur ;
- voie J5 réelle opt-in, désactivée par défaut, utilisable seule ou dans l'union exacte J3 + J4
  phase 2 + J5, limitée à l'origine exacte `https://www.sofascore.com` et aux chemins actifs d'une
  identité canonique déjà persistée ; J4 phase 1 reste incompatible avec cette session combinée ;
- coordinateur commun J3/J4/J5 sérialisant tous les transports manuels et appliquant le délai
  minimal entre leurs départs, y compris lors du passage de `SCHEDULED_EVENTS` à `EVENT_DETAILS`
  puis à `EVENT_STATISTICS` ;
- préparation J5 sans réseau, confirmation exacte de cinq minutes, acquittement, trois appels
  séquentiels au maximum et délai minimal de trois secondes ; après succès, l'ancien claim reste
  non rejouable mais une nouvelle campagne explicite peut être préparée dans la même instance ;
- parseurs fournisseur `event-statistics-v2`, `event-incidents-v15` et `event-lineups-v2`, brut
  persisté avant parsing, provenance `PROVIDER_SNAPSHOT` et résultat d'écran minimisé ;
- traitement borné du HTTP `404` sur les trois chemins J5 exacts : snapshot
  `ENDPOINT_UNAVAILABLE`, observation `UNAVAILABLE · N/A`, aucun parsing du corps, aucun retry et
  poursuite ordonnée vers la famille suivante ;
- reparsing d'un brut dédupliqué sans reclassification de sa preuve historique : le résultat du
  parseur courant est une nouvelle observation append-only et la séquence peut atteindre la
  famille suivante ;
- conservation V4 des deux participants d'un remplacement (`playerIn` et `playerOut`), avec
  identifiants fournisseur, noms, complétude explicite et affichage « Entrant / Sortant » ;
- normalisation V6 pilotée par la fiche métier des incidents de football : les huit types
  `period`, `substitution`, `goal`, `card`, `injuryTime`, `varDecision`, `inGamePenalty` et
  `penaltyShootout` sont conservés ; les absences métier facultatives deviennent `PARTIAL`, tandis
  que les contradictions atomiques, types inconnus et vocabulaires hors contrat restent
  `SCHEMA_INCOMPATIBLE` ;
- extension V7 strictement limitée à la classe de remplacement sur blessure observée :
  `incidentClass="injury"` est conservé avec `injury=true`, les joueurs entrant et sortant restent
  rattachés au remplacement, et une contradiction booléenne explicite reste
  `SCHEMA_INCOMPATIBLE` ;
- occurrence J6 append-only à chaque tentative future de persistance brute, avec résultat
  `INSERTED` ou `DEDUPLICATED` et une occurrence `BASELINE` pour chaque snapshot antérieur ;
- chronologie paginée des flux état, détails, statistiques, incidents et compositions, avec
  comparaison sémantique calculée à la demande et classification des changements tardifs ;
- corpus synthétique J6 idempotent couvrant un état terminal puis des corrections sur les quatre
  autres familles, sans appel réseau ni copie d'un payload fournisseur ;
- aperçu de rétention en lecture seule dans le tableau de bord et commande opérateur non Web : la
  suppression est limitée aux octets `payload_raw`, tandis que lignes, taille, SHA-256, provenance,
  occurrences, observations normalisées et audit append-only sont conservés ;
- sauvegarde PostgreSQL chiffrée directement par `age`, restauration de qualification dans une
  base temporaire et vérification des empreintes avant toute commande de purge.
- export J7 d'un événement courant à la fois, avec contrat Draft 2020-12 classpath, cinq sources
  ordonnées, états d'absence explicites, complétude et avertissements sans données inventées ;
- cycle local J7 candidat puis décision humaine, empreintes séparées des données, sources et
  fichier, intention terminale write-ahead, persistance V23 gardée et téléchargement réservé à
  `HUMAN_VALIDATED` ;
- stockage J7 uniquement sous la racine configurée, noms serveur, temporaire synchronisé puis lien
  physique atomique create-new sur le même système de fichiers, avec refus des chemins
  utilisateurs, liens symboliques, écrasements, altérations et contenus sensibles.

## Limite essentielle du bootstrap

**Aucun appel SofaScore réel n’est actif par défaut et aucun n’est exécuté par les tests.** Le
connecteur général, `ConnectorGate`, le catalogue `callable=false` et le profil Maven
`sofascore-live-test` restent bloqués. J3 pour `SCHEDULED_EVENTS`, J4 phase 2 pour `EVENT_DETAILS`
et J5 pour `EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS` restent des voies manuelles
distinctes, mais peuvent être armés ensemble avec l'union exacte des cinq familles. Dans J4,
`SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED` sélectionne exclusivement une identité canonique
résolue côté serveur, laquelle impose le `providerEventId`, et est obligatoire dès que J4 partage
la session avec J3 ou J5. J4 phase 1 reste exclusif.
Dans J5, l'action finale réutilise l'identité canonique affichée et autorise au maximum trois
transports Playwright ordonnés après une confirmation humaine unique. Une lease et un coordinateur
communs sérialisent toutes les campagnes J3/J4/J5 et imposent au moins trois secondes entre leurs
départs, y compris entre jalons. Toutes ces voies interdisent polling, planification et retry.
J6/J7 restent locaux et sans transport. Les configurations temporaires et leur remise à l'état
bloqué sont décrites dans `docs/runbooks/RUNBOOK-LOCAL.md`.

Le Work Order validé `WO-SS-20260827-013` implémente pour les deux familles J3 un transport Playwright
dans un worker JVM enfant. Le build et le démarrage standards restent sans runtime Playwright et
`SOFASCORE_PLAYWRIGHT_ENABLED=false` est la valeur persistée par défaut. Une configuration métier
J3/J4/J5 valide peut donc afficher `CONFIG_ENABLED_BUT_QUALIFICATION_BLOCKED` et un transport J3
`INDISPONIBLE` lorsqu'elle est lancée par la commande Spring Boot générique : c'est le verrou attendu.
Après installation explicite du runtime, `scripts/Start-J3PlaywrightLocal.ps1` construit et vérifie
`target/betting-sofascore-local-lab-0.1.0-SNAPSHOT-provider-playwright-worker.jar`, puis injecte
`SOFASCORE_PLAYWRIGHT_ENABLED`, le JAR worker et le cache Chromium uniquement dans le processus de
ce lancement. Le lanceur vérifie les marqueurs et exécutables Chromium ; le superviseur impose au
worker l'absence de tout téléchargement implicite de navigateur. Le profil
`provider-playwright-runtime` compile seulement le worker ; il ne démarre ni Chromium ni une
campagne. Le lanceur n'appelle pas SofaScore : la préparation, la confirmation et l'action finale
distincte dans l'interface restent indispensables. Chaque campagne explicitement armée utilise un
worker, un navigateur headless et un contexte non persistant neufs, puis un arrêt ciblé nettoie
leur arbre de processus exact. La qualification technique reste exclusivement loopback sur
`127.0.0.1`. La qualification humaine bornée du 28 août 2026 est ensuite concluante pour les deux
familles J3 : `SCHEDULED_EVENTS` conserve en HTTP `200` les douze pages et snapshots 572 à 583
jusqu'à `hasNextPage=false`; cinq actions `TOURNAMENT_SCHEDULED_EVENTS` conservent les snapshots
584 à 588, vérifient six rencontres canoniques et les relient à J5. Ces dix-sept requêtes ont exigé
des préparations, confirmations et actions finales distinctes. L'application est ensuite arrêtée
gracieusement, sans listener ni JVM applicatif ou worker résiduel. La décision propriétaire clôt le
Work Order en `VALIDATED`; aucune nouvelle campagne fournisseur n'est autorisée par cette clôture.

WO-014 étend ce même worker à J4 sans créer de navigateur autonome. La phase 1 conserve ses deux
IDs fixes et un seul contexte de campagne ; la phase 2 part d'une identité canonique affichée,
résout son ID fournisseur côté serveur et ouvre un contexte neuf par confirmation. Les scripts
J4 dédiés `Start-J4PlaywrightLocal.ps1` et `Invoke-J4PlaywrightLoopbackQualification.ps1` restent
explicites, et la qualification technique utilise uniquement un serveur loopback éphémère sur
`127.0.0.1`. Le lanceur n'autorise à lui seul aucun appel fournisseur.

WO-015 étend maintenant le protocole commun v5 aux trois familles J5. Les scripts dédiés
`Start-J5PlaywrightLocal.ps1` et `Invoke-J5PlaywrightLoopbackQualification.ps1` vérifient le worker
et le cache Chromium sans les persister dans `.env`; le second ne vise que loopback. Le premier ne
doit être utilisé pour une campagne fournisseur qu'après une autorisation propriétaire distincte.
La qualification humaine du `2026-08-29` a termine les trois familles sur Lille - Paris
Saint-Germain avec les snapshots `603`, `604` et `605`, trois observations normalisees completes et
le terminal `COMPLETED_LOCKED`. Dans la meme session, J4 a conserve le snapshot `606`. La reprise
corrective J3 a ensuite affiche un catalogue `AVAILABLE` de `1 428` options sur 18 pages, puis la
selection Ligue 1 a retenu `5 / 5` rencontres dans le snapshot `643`. La readiness finale est verte
avec 765 tests standards, 53 tests PostgreSQL, 11 tests de protocole/securite et 14 tests Chromium
loopback. WO-015 est clos ; cette preuve n'autorise aucun appel supplementaire.

Le lot multi-match WO-010 reste indépendant de ces voies armables : l'état de
`SOFASCORE_ENABLED` n'entre pas dans sa décision d'éligibilité. Son automatisation s'arrête à la
validation et à l'ingestion synchrones de preuves remises ou déclarées manuellement ; elle
n'observe aucun répertoire et ne lance aucune acquisition, tâche planifiée ou reprise automatique,
même lorsque le connecteur et les qualifications manuelles sont activés.

La découverte tournoi est une autre voie spéciale : elle exige ensemble
`SOFASCORE_ENABLED=true`, `SOFASCORE_J3_QUALIFICATION_ENABLED=true` et
`SOFASCORE_TOURNAMENT_EVENT_DISCOVERY_ENABLED=true`, l'origine exacte
`https://www.sofascore.com` et l'allowlist exacte
`SCHEDULED_EVENTS,TOURNAMENT_SCHEDULED_EVENTS`. Ce contrat ne vaut pas autorisation d'appel réel ;
sélection et préparation restent sans réseau, et l'action finale exige une décision humaine
distincte. J4 et J5 demeurent désactivés dans cette configuration isolée.

La confirmation J3 permet de choisir soit la pagination directe existante, soit l'import local
d'un lot complet de pages 1 à N obtenu manuellement. L'import J3 ne lit ni n'alimente le cache,
n'exécute aucun transport, refuse HAR/en-têtes/cookies et exige une suite `hasNextPage` terminale.
Un `HTTP_FORBIDDEN` direct reste terminal et ne bascule pas automatiquement : il faut réarmer,
préparer et confirmer une nouvelle intention avant de sélectionner le lot local. La procédure
exacte et les limites sont décrites dans le runbook.

Après une collecte J3 `COMPLETED`, la liste de découverte ne conserve que les occurrences portant
un `tournament.category.name` exploitable et dont `timezoneEventCount` contient l'offset applicable
à la date collectée dans `Europe/Paris` : `7200` en heure d'été, `3600` en heure d'hiver, ou l'un des
deux le jour d'une bascule. Le libellé associe le nom de phase et cette portée géographique avec
` - `, sans modifier l'identité postée. Cette éligibilité est recalculée côté serveur ; un
`tournament.id` exclu ne peut pas être préparé par un POST manuel.

Si J3, la découverte tournoi, J4 phase 2 et J5 sont armés dans une même instance, l'allowlist
exacte devient `SCHEDULED_EVENTS,TOURNAMENT_SCHEDULED_EVENTS,EVENT_DETAILS,EVENT_STATISTICS,`
`EVENT_INCIDENTS,EVENT_LINEUPS`. `TOURNAMENT_STANDINGS` et `TEAM_RECENT_EVENTS` restent différés et
ne doivent pas être ajoutés : une famille supplémentaire rend volontairement la configuration
invalide. Le build Maven reste indépendant des opt-ins présents dans le `.env` local et n'exécute
aucun appel fournisseur.

Cette limite préserve la règle du Betting Project principal : aucun composant du VPS ne dépend du laboratoire, et l’arrêt du poste Windows ne doit avoir aucun effet sur la chaîne globale.

## Prérequis Windows

- Windows 11 ;
- Eclipse 2026-06 (4.40.0) avec Spring Tools 5.3.0 ;
- JDK 25 configuré comme JRE par défaut du workspace ;
- Docker Desktop avec `docker compose` ;
- Git.

Maven n’a pas besoin d’être installé globalement : `mvnw.cmd` télécharge la distribution Maven verrouillée lors de sa première exécution et vérifie son empreinte SHA-256.

## Démarrage rapide

Depuis PowerShell, à la racine du dépôt :

```powershell
# 1. Génère un mot de passe local aléatoire dans .env
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Initialize-LocalConfig.ps1

# 2. Vérifie Java 25, Docker, Compose et la configuration
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Preflight-Local.ps1

# 3. Démarre PostgreSQL et attend son healthcheck
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Start-Local.ps1

# 4. Compile et exécute les tests standard, sans accès SofaScore
.\mvnw.cmd clean verify

# 5. Démarre l’application locale
.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Ce démarrage normal conserve Playwright désarmé, même si les opt-ins métier J3/J4/J5 du `.env`
forment une combinaison valide. Pour un test fournisseur nouvellement et explicitement autorisé,
installer une fois le runtime puis utiliser le lanceur exact du jalon à la place de la commande de
l'étape 5. La validation de WO-015 ne constitue pas ce nouveau go :

```powershell
pwsh -NoProfile -File .\scripts\Install-J3PlaywrightRuntime.ps1
# Choisir ensuite un seul lanceur autorisé :
pwsh -NoProfile -File .\scripts\Start-J3PlaywrightLocal.ps1
# ou J4 :
pwsh -NoProfile -File .\scripts\Start-J4PlaywrightLocal.ps1
# ou J5, seulement après un nouveau go propriétaire distinct :
pwsh -NoProfile -File .\scripts\Start-J5PlaywrightLocal.ps1
```

Le lanceur fournit les chemins du worker et de Chromium seulement à son processus. Il démarre
l'application, mais aucun accès SofaScore n'a lieu avant la préparation, la confirmation et la
commande finale distincte de l'opérateur dans l'interface.

Ouvrir ensuite :

```text
http://127.0.0.1:8087
```

Points Actuator :

```text
http://127.0.0.1:8087/actuator/health
http://127.0.0.1:8087/actuator/info
http://127.0.0.1:8087/actuator/metrics
```

## Import dans Eclipse

1. Enregistrer le JDK 25 dans **Window → Preferences → Java → Installed JREs** et le définir comme JRE par défaut.
2. Choisir **File → Import → Maven → Existing Maven Projects**.
3. Sélectionner le dossier `betting-sofascore-local-lab`.
4. Vérifier dans **Project Properties → Java Compiler** que le niveau est `25`.
5. Créer une configuration **Spring Boot App** sur `SofascoreLocalApplication` avec le profil `local`.
6. Définir le répertoire de travail sur la racine du dépôt afin que `.env` et `exports/` soient résolus correctement.

Le démarrage depuis Eclipse nécessite que PostgreSQL ait déjà été lancé par `Start-Local.ps1` ou `docker compose up -d postgres`.

## Commandes de validation

Les commandes ci-dessous décrivent le socle général du dépôt. Elles ne valent pas autorisation
d'exécuter les scénarios natifs sous WO-047 : pour ce Work Order, utiliser uniquement le
`clean verify` avec exclusion explicite de `J6NativeBinaryPipelineQualificationTest` et les
sélections positives du profil `integration-tests` consignées dans le
[WO-047](docs/work_orders/completed/WO-SS-20260904-047-j9-j7-delivery-governance-separation.md).
Le test Flyway `pg_dump`/`pg_restore`, le pipeline natif et le profil intégral restent interdits.

### Tests standards hors ligne fournisseur

```powershell
.\mvnw.cmd clean verify
```

Les tests standards ne contiennent aucun appel Internet vers SofaScore.

### Migration réelle PostgreSQL avec Testcontainers

```powershell
.\mvnw.cmd -Pintegration-tests verify
```

Ce profil exige Docker et peut télécharger l’image PostgreSQL au premier lancement. Il ne contacte pas SofaScore.

### Vérification consolidée

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1

powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Verify-Local.ps1 -WithIntegrationTests
```

### Arrêt

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Stop-Local.ps1
```

La suppression volontaire des données PostgreSQL nécessite :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Stop-Local.ps1 -RemoveData
```

## Arborescence

```text
betting-sofascore-local-lab/
├── ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md
├── ADR-SS-002-bounded-multi-dossier-provider-robustness.md
├── ADR-SS-003-optional-integration-topology.md
├── AGENTS.md
├── README.md
├── SECURITY.md
├── CHANGELOG.md
├── compose.yaml
├── pom.xml
├── mvnw / mvnw.cmd
├── docs/
│   ├── architecture/
│   ├── reference/
│   ├── runbooks/
│   ├── validation/
│   └── work_orders/
├── fixtures/
├── scripts/
├── exports/
└── src/
```

## Modèle de données J1 à J7

La migration `V1__bootstrap_schema.sql` crée :

- `provider_snapshot` : métadonnées de transport, emplacement normalisé JSONB, hash, parseur et statut de schéma ;
- `export_manifest` : registre générique étendu par V23 pour les exports canoniques J7 gardés ;
- `connector_control` : état opérateur persistant, initialisé à `network_enabled=false` et `circuit_state=LOCKED`.

La migration append-only `V2__raw_manual_call_snapshots.sql` ajoute à `provider_snapshot` les octets exacts dans `payload_raw` (`bytea`), leur taille et le mode de provenance obligatoire `DIRECT_LOCAL_ENDPOINT`. Le brut reste distinct de `payload_jsonb`, qui n’est pas alimenté par cette unité. La taille est limitée à 5 Mio. Depuis V25, la déduplication porte sur le fournisseur, le mode d'acquisition, l'endpoint logique, la clé de requête et le SHA-256 afin de ne pas confondre réponse directe et import manuel.

La migration append-only `V3__dynamic_manual_collection_cache.sql` ajoute uniquement un checkpoint
de fraîcheur par clé date/page. Il référence le snapshot brut immuable et permet de rafraîchir le
TTL après une nouvelle réponse identique dédupliquée, sans recopier ni modifier le payload.

La migration append-only `V4__canonical_events_and_observations.sql` introduit
`canonical_event` et `canonical_event_observation`. L’identité UUID reste stable pour la paire
fournisseur/identifiant ; les observations successives conservent les changements métier et leur
provenance. Un trigger PostgreSQL bloque toute mise à jour ou suppression d’une observation.

La migration append-only `V5__offline_event_details.sql` ajoute `event_detail_observation` pour
les fixtures synthétiques J4. La migration append-only
`V6__guarded_real_event_details.sql` étend ensuite sa provenance aux snapshots fournisseur réels
de la voie bornée. Pour les lignes V5 existantes, V6 complète uniquement les deux colonnes de
provenance structurelle dans sa transaction : le trigger de cette table est suspendu pendant ce
backfill borné, puis réactivé avant la fin de la migration. Aucun champ métier historique n’est
modifié. Fixture ou snapshot, chaque détail conserve hash, parseur et heure source obligatoires ;
les observations restent protégées contre `UPDATE` et `DELETE` après V6.

La migration append-only `V7__j5_event_data_completeness.sql` ajoute un lot de provenance et de
complétude par famille, puis des tables séparées pour métriques, incidents, côtés de composition et
joueurs. Chaque lot conserve fixture, SHA-256 brut, parseur, heure source et empreinte normalisée.
Les cinq tables refusent `UPDATE` et `DELETE`; une nouvelle version est ajoutée ou une version
identique est dédupliquée. Les octets de fixture restent dans le corpus classpath et ne sont jamais
recopiés dans ces tables.

La migration append-only `V8__guarded_real_j5_event_data.sql` étend uniquement les contraintes de
parseur et de provenance J5 afin d'accepter les versions fournisseur V2 rattachées à un snapshot
brut. Elle ne modifie aucune migration antérieure ni aucune observation existante.

La migration append-only `V9__j5_optional_family_unavailable.sql` distingue une famille non
publiée d'un incident de transport et d'une liste vide valide. Elle ajoute les statuts
`ENDPOINT_UNAVAILABLE` et `UNAVAILABLE`, autorise les normaliseurs d'indisponibilité versionnés et
reclasse les anciens snapshots J5 HTTP `404` marqués `TRANSPORT_ERROR/HTTP_STATUS_404`, sans
modifier leurs octets, hashes, heures ou identifiants et sans fabriquer d'observation rétroactive.

La migration append-only `V10__j5_incident_period_marker_parser.sql` autorise
`event-incidents-v3` dans la contrainte de provenance sans réécrire les observations V1/V2. Ce
parseur conserve `addedTime=999` dans le snapshot brut mais omet cette sentinelle de la valeur
normalisée uniquement pour un incident `period`, avec un avertissement explicite. La même valeur
reste incompatible sur un but, un carton, un remplacement ou tout autre incident latéralisé.

La migration append-only `V11__j5_incident_substitution_players.sql` ajoute les couples facultatifs
identifiant/nom des joueurs entrant et sortant aux incidents et autorise la provenance
`event-incidents-v4`. V4 reprend strictement la règle de sentinelle V3, lit séparément
`playerIn` et `playerOut` pour tous les remplacements et classe une identité manquante comme une
complétude `PARTIAL` sans fabriquer de joueur. Une réponse brute identique peut être reparsée par
V4 et produire une nouvelle observation normalisée tout en conservant le statut historique du
snapshot dédupliqué.

La migration append-only `V12__j5_incident_bench_card_details.sql` ajoute la classe et le motif
facultatifs d'un carton, autorise `event-incidents-v5` et conserve une minute normalisée comprise
entre 0 et 300. V5 ne traite qu'une forme observée : pour un `card` dont `time` vaut exactement
`-5`, `benchTime` doit être présent et devient la minute normalisée. Le brut reste inchangé ; aucune
autre valeur négative ni aucun autre type d'incident ne bénéficie d'une règle nouvelle avant la
définition de la fiche de gestion métier dédiée aux incidents de football.

La migration append-only `V13__j5_football_incident_business_rules.sql` applique cette fiche sans
modifier V1–V12. Elle autorise `event-incidents-v6` et ajoute les attributs normalisés nécessaires
aux huit types documentés : texte de période, blessure, passeur, origine du but, durée du temps
additionnel, décision VAR, description, ordre d'une séance de tirs au but et participants. Les
noms fournisseur peuvent être conservés même lorsque leur identifiant numérique est absent ;
cette absence reste mesurée par la complétude au lieu de fabriquer un identifiant.

La migration append-only `V14__j5_incident_injury_substitution_class.sql` autorise la provenance
`event-incidents-v7` sans modifier V1–V13 ni les observations V6. V7 conserve toutes les règles de
la fiche et ajoute la classe fournisseur `injury` pour une substitution. La paire
`incidentClass="injury"` / `injury=false` est rejetée comme contradiction atomique ; l'absence du
booléen reste mesurée `PARTIAL` sans valeur inventée.

La migration append-only `V15__j5_penalty_incident_variants.sql` autorise
`event-incidents-v8` sans modifier V1–V14. V8 ajoute uniquement les variantes attestées : sentinelle
terminale `PEN/time=999` dérivée de la dernière minute effective de la séance, motif de carton
absent, origine brute `shot` sur un but ordinaire et résultat `Woodwork/woodwork` cohérent pour les
deux types de pénalty. La minute 999, l'origine `shot` et les payloads complets restent dans le brut ;
aucune donnée historique n'est reclassée ou réécrite.

La migration append-only `V16__j5_bench_card_other_reason.sql` autorise
`event-incidents-v9` sans modifier V1–V15 ni ajouter de colonne. V9 conserve le contrat V8, ajoute
le motif de carton attesté `Other reason` et accepte `benchAddedTime` uniquement pour un carton
portant un `time` technique négatif, un `benchTime` valide et aucun `addedTime` concurrent. La
minute et le temps additionnel normalisés sont alors issus de `benchTime` et `benchAddedTime` ; les
champs fournisseur complets restent exclusivement dans le snapshot brut.

La migration append-only `V17__j5_regular_goal_origin.sql` autorise
`event-incidents-v10` sans modifier V1–V16 ni ajouter de colonne. V10 conserve le contrat V9 et
neutralise uniquement la valeur brute redondante `from="regular"` lorsque le même but porte
`incidentClass="regular"`. Le champ reste dans le snapshot brut, aucune origine spéciale n'est
inventée et toute combinaison croisée ou valeur future inconnue reste `SCHEMA_INCOMPATIBLE`.

La migration append-only `V18__j5_off_the_ball_card_reason.sql` autorise
`event-incidents-v11` sans modifier V1–V17 ni ajouter de colonne. V11 conserve le contrat V10 et
ajoute uniquement le motif de carton attesté `Off the ball foul`, documenté comme obstruction ou
faute loin du ballon. Le libellé fournisseur exact est persisté et affiché ; toute autre valeur
future inconnue reste `SCHEMA_INCOMPATIBLE`.

La migration append-only `V19__j5_unminuted_terminal_shootout.sql` autorise
`event-incidents-v12` et rend la minute normalisée nullable sous une contrainte fermée. V12 accepte
`NULL` uniquement pour les `penaltyShootout` et le marqueur `period/PEN` d'une séance terminale
entièrement non minutée et cohérente ; un `inGamePenalty` sans minute ou une séance mixte restent
incompatibles. Les séquences ne sont jamais converties en minutes. Les lignes et observations
V1–V18 restent inchangées pendant l'upgrade.

La migration append-only `V20__j5_leaving_field_card_reason.sql` autorise
`event-incidents-v13` sans modifier les colonnes ni la contrainte temporelle de V19. V13 conserve
le contrat complet V12 et ajoute seulement `Leaving field` au vocabulaire fermé des motifs de
carton. Le libellé exact est persisté et affiché ; toute autre valeur non documentée reste
`SCHEMA_INCOMPATIBLE`. Les observations V1–V19 restent inchangées pendant l'upgrade.

La migration append-only `V21__j6_snapshot_occurrences.sql` ajoute
`provider_snapshot_occurrence`. Une occurrence `BASELINE` décrit chaque snapshot déjà présent,
sans prétendre reconstruire les tentatives historiques inconnues. Chaque sauvegarde future ajoute
ensuite une occurrence `INSERTED` ou `DEDUPLICATED`, même si les octets sont identiques et que la
ligne `provider_snapshot` est réutilisée. La table est immuable et ne contient aucun payload.

La migration append-only `V22__j6_guarded_raw_payload_retention.sql` ajoute la date de purge des
octets et l'audit `j6_raw_payload_purge_audit`. Elle interdit la suppression d'une ligne snapshot,
les modifications arbitraires et toute purge non reliée, dans la même transaction, à un lot
d'audit et à une preuve de sauvegarde restaurée. Après purge, le SHA-256, la taille, les métadonnées,
les occurrences et toutes les observations normalisées restent consultables ; seul
`payload_raw` devient absent avec un état explicite `PAYLOAD_PURGED`.

La migration append-only `V23__j7_canonical_event_exports.sql` étend exclusivement
`export_manifest`. Les lignes génériques historiques restent valides ; les lignes
`J7_CANONICAL_EVENT` conservent UUID d'export et d'événement, schéma, génération, empreintes des
données/sources/candidat/fichier courant, taille, chemin, cinq sources structurées, snapshots,
avertissements, décision et intention terminale write-ahead. Cette intention fixe statut, heure,
motif éventuel, chemin, hash et taille attendus avant l'écriture du fichier. Des index partiels et
des triggers imposent un seul candidat courant, l'unicité d'un `dataSha256` validé, une transition
terminale identique à l'intention, l'immuabilité des preuves, le verrou de fraîcheur par événement
sur les écritures d'observation et l'interdiction de supprimer une ligne J7.

La migration append-only `V24__j3_j5_tournament_scheduled_events_cache.sql` ne modifie aucune
donnée canonique ni preuve J7. Elle étend uniquement la contrainte fermée de
`provider_response_cache.logical_endpoint` à `TOURNAMENT_SCHEDULED_EVENTS`, afin que le cache de
dix minutes de la découverte tournoi référence ses snapshots bruts intègres.

La migration append-only `V25__manual_local_json_import_provenance.sql` autorise uniquement le
mode brut `MANUAL_LOCAL_JSON_IMPORT` et l'ajoute à la clé de déduplication des snapshots. Elle ne
transforme pas un import en réponse fournisseur : les caches restent limités aux snapshots
`DIRECT_LOCAL_ENDPOINT`, tandis que l'inspection et la rétention J6 couvrent les deux provenances.
Ce mode est utilisé aussi bien pour le lot paginé J3 que pour le corps importé du second endpoint,
avec des endpoints logiques et des clés distincts.

La migration append-only `V26__j5_live_extra_time_period.sql` autorise
`event-incidents-v14` dans la contrainte de version de parseur J5. V14 conserve toutes les règles V13 et
ajoute uniquement `text="Extra time"` comme marqueur `period` live, distinct du marqueur terminal
`ET`. Aucune colonne, observation historique ni donnée normalisée existante n'est réécrite.

La migration append-only `V28__j5_incidents_empty_shootout_action.sql` autorise
`event-incidents-v15` dans cette même contrainte, sans colonne, table ni DML. V15 conserve toutes
les règles V14 et assimile une propriété `footballPassingNetworkAction` absente à un tableau
exactement vide uniquement dans la séance terminale non minutée déjà cohérente de V12. JSON
`null`, les autres types, les tableaux non vides incohérents et une séance mêlant tentative
réellement minutée et tentative non minutée restent incompatibles. V28 ne reparse ni ne réécrit
aucune preuve V1–V27, notamment le résultat J8 du snapshot 717.

Le mode `DIRECT_LOCAL_ENDPOINT` ne doit jamais être confondu avec une `VisualObservation` du projet
global. La persistance n'effectue elle-même aucun appel : les écritures J5 réelles éventuelles sont
initiées uniquement par la voie humaine gardée, puis référencent le brut séparé avec
`PROVIDER_SNAPSHOT`.

## Politique réseau J1

Le bootstrap cumule plusieurs barrières :

1. `sofascore.enabled=false` par défaut ;
2. aucune base URL par défaut ;
3. aucune origine fournisseur dans la configuration par défaut ;
4. toutes les définitions du catalogue restent `callable=false` ; les voies spéciales J3 et
   découverte ne deviennent éligibles qu'avec leurs opt-ins et l'union exacte
   `SCHEDULED_EVENTS,TOURNAMENT_SCHEDULED_EVENTS` ;
5. `ConnectorGate` refuse systématiquement les appels ;
6. le profil `sofascore-live-test` échoue volontairement ;
7. l’application n’écoute que sur une adresse de boucle locale ;
8. les paramètres imposent concurrence `1`, délai minimal `3s`, rafraîchissement et live désactivés.

Après la clôture de J3, aucune de ces barrières ne peut être retirée sans un nouveau Work Order,
une décision de gouvernance explicite et une qualification humaine dédiée.

## Documentation de référence

- [ADR-SS-001](ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md)
- [ADR-SS-002 accepté — preuve J9 multi-dossier bornée](ADR-SS-002-bounded-multi-dossier-provider-robustness.md)
- [ADR-SS-003 v0.1 accepté — topologie d'intégration optionnelle](ADR-SS-003-optional-integration-topology.md)
- [Architecture J0/J1](docs/architecture/ARCHITECTURE.md)
- [Contrat hors ligne scheduled-events-v1](docs/architecture/SCHEDULED-EVENTS-V1.md)
- [Contrat hors ligne event-details-v1](docs/architecture/EVENT-DETAILS-V1.md)
- [Architecture des événements canoniques J4](docs/architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md)
- [Transport fournisseur J4 Playwright validé](docs/architecture/J4-PLAYWRIGHT-EVENT-DETAILS.md)
- [Architecture J5 hors ligne et contrôles de complétude](docs/architecture/J5-OFFLINE-EVENT-DATA-AND-COMPLETENESS.md)
- [Architecture de la qualification réelle gardée J5](docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md)
- [Architecture du lot J5 hors ligne multi-match](docs/architecture/J5-OFFLINE-MULTI-MATCH-IMPORT.md)
- [Règles du lot J5 hors ligne multi-match](docs/requirements/J5-OFFLINE-MULTI-MATCH-IMPORT-RULES.md)
- [Readiness du lot J5 hors ligne multi-match](docs/validation/J5-OFFLINE-MULTI-MATCH-IMPORT-TECHNICAL-READINESS-20260822.md)
- [Work Order validé du lot J5 hors ligne multi-match](docs/work_orders/completed/WO-SS-20260822-010-j5-offline-multi-match-import.md)
- [Architecture J6 — historique et rétention gardée](docs/architecture/J6-HISTORY-AND-GUARDED-RETENTION.md)
- [Architecture J7 — export canonique local et audité](docs/architecture/J7-CANONICAL-EVENT-EXPORT.md)
- [Architecture J8 — métriques, preuves et formules](docs/architecture/J8-BENCHMARK-METRICS.md)
- [Architecture J3 → J5 — découverte tournoi → rencontres](docs/architecture/J3-J5-TOURNAMENT-EVENT-DISCOVERY.md)
- [Règles structurelles de découverte tournoi → rencontres](docs/requirements/J3-J5-TOURNAMENT-EVENT-DISCOVERY-RULES.md)
- [Readiness technique de la découverte tournoi → rencontres](docs/validation/J3-J5-TOURNAMENT-EVENT-DISCOVERY-TECHNICAL-READINESS-20260820.md)
- [Règles métier des incidents de football J5](docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md)
- [Voie réelle bornée J4 — sous-étape 1](docs/architecture/J4-GUARDED-REAL-EVENT-DETAILS-PHASE1.md)
- [Politique réseau J3 hors ligne](docs/architecture/J3-OFFLINE-NETWORK-POLICY.md)
- [Persistance des snapshots bruts J3](docs/architecture/J3-RAW-SNAPSHOT-PERSISTENCE.md)
- [Transport scheduled-events J3 protégé et simulé](docs/architecture/J3-GUARDED-SCHEDULED-EVENTS-TRANSPORT.md)
- [Confirmation explicite d’appel manuel J3](docs/architecture/J3-EXPLICIT-MANUAL-CALL-CONFIRMATION.md)
- [Politiques d’arrêt et d’incident J3](docs/architecture/J3-TRANSPORT-STOP-AND-INCIDENT-POLICIES.md)
- [Transport fournisseur manuel J3 Playwright](docs/architecture/J3-PLAYWRIGHT-PROVIDER-TRANSPORT.md)
- [Readiness technique du transport J3 Playwright](docs/validation/J3-PLAYWRIGHT-TRANSPORT-TECHNICAL-READINESS-20260827.md)
- [Work Order validé du transport J3 Playwright](docs/work_orders/completed/WO-SS-20260827-013-j3-playwright-transport.md)
- [Readiness technique et qualification humaine J4 Playwright](docs/validation/J4-PLAYWRIGHT-EVENT-DETAILS-TECHNICAL-READINESS-20260828.md)
- [Work Order validé du transport J4 Playwright](docs/work_orders/completed/WO-SS-20260827-014-j4-playwright-event-details.md)
- [Readiness technique et qualification humaine J5 Playwright](docs/validation/J5-PLAYWRIGHT-EVENT-DATA-TECHNICAL-READINESS-20260828.md)
- [Work Order validé du transport J5 Playwright](docs/work_orders/completed/WO-SS-20260827-015-j5-playwright-event-data.md)
- [Chemin fournisseur J3 borné à cinq pages](docs/architecture/J3-FIVE-PAGE-PROVIDER-QUALIFICATION.md)
- [Reprise fournisseur J3 contrôlée à la page 2](docs/architecture/J3-PAGE-TWO-PROVIDER-RESUME.md)
- [Adaptation hors ligne au schéma qualifié de la page 2](docs/architecture/J3-PAGE-TWO-SCHEMA-ADAPTATION.md)
- [Reprise fournisseur J3 contrôlée à la page 3](docs/architecture/J3-PAGE-THREE-PROVIDER-RESUME.md)
- [Collecte manuelle J3 répétable à pagination dynamique](docs/architecture/J3-DYNAMIC-MANUAL-PAGINATION.md)
- [Inspection JSON locale des snapshots bruts J3](docs/architecture/J3-LOCAL-RAW-SNAPSHOT-JSON-INSPECTION.md)
- [Runbook local](docs/runbooks/RUNBOOK-LOCAL.md)
- [Runbook J6 — sauvegarde, restauration et rétention](docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md)
- [Runbook J7 — candidat, décision humaine et téléchargement](docs/runbooks/J7-CANONICAL-EVENT-EXPORT.md)
- [Runbook J8 — readiness, export et campagne bornée](docs/runbooks/J8-BENCHMARK.md)
- [Cadrage PDF](docs/reference/Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf)
- [Rapport de validation du bootstrap](docs/validation/J0-J1-VALIDATION-REPORT.md)
- [Work Order J0/J1](docs/work_orders/completed/WO-SS-20260808-001-bootstrap-j0-j1.md)
- [Rapport de validation J2](docs/validation/J2-WINDOWS-VALIDATION-20260812.md)
- [Work Order J2 validé](docs/work_orders/completed/WO-SS-20260808-002-fixtures-j2.md)
- [Qualification Windows J3 — contrôle local et politiques simulées](docs/validation/J3-WINDOWS-MANUAL-CALL-QUALIFICATION-20260812.md)
- [Qualification Windows J3 — pagination dynamique](docs/validation/J3-WINDOWS-DYNAMIC-PAGINATION-QUALIFICATION-20260814.md)
- [Work Order J3 validé](docs/work_orders/completed/WO-SS-20260812-003-manual-call-j3.md)
- [Qualification technique Windows J4](docs/validation/J4-WINDOWS-TECHNICAL-QUALIFICATION-20260815.md)
- [Préparation technique J4 réelle — sous-étape 1](docs/validation/J4-REAL-EVENT-DETAILS-PHASE1-READINESS-20260815.md)
- [Campagne humaine J4 réelle — sous-étape 1 et anomalie de navigation](docs/validation/J4-REAL-EVENT-DETAILS-PHASE1-CAMPAIGN-20260815.md)
- [Préparation technique J4 réelle — sous-étape 2](docs/validation/J4-REAL-EVENT-DETAILS-PHASE2-READINESS-20260815.md)
- [Campagne humaine J4 réelle — sous-étape 2 et actualisation](docs/validation/J4-REAL-EVENT-DETAILS-PHASE2-CAMPAIGN-20260815.md)
- [Incident et correction de l’upgrade V5 préremplie vers V6](docs/validation/J4-V6-PREFILLED-UPGRADE-INCIDENT-20260815.md)
- [Work Order J4 validé](docs/work_orders/completed/WO-SS-20260815-004-events-j4.md)
- [Qualification technique Windows J5](docs/validation/J5-WINDOWS-TECHNICAL-QUALIFICATION-20260815.md)
- [Work Order J5 validé](docs/work_orders/completed/WO-SS-20260815-005-statistics-j5.md)
- [Observation préalable à la qualification réelle J5](docs/validation/J5-REAL-EVENT-DATA-PREREQUISITE-OBSERVATION-20260815.md)
- [Readiness technique de la qualification réelle J5](docs/validation/J5-REAL-EVENT-DATA-TECHNICAL-READINESS-20260815.md)
- [Correction du marqueur de période incidents J5](docs/validation/J5-REAL-INCIDENT-PERIOD-MARKER-CORRECTION-20260816.md)
- [Correction de la déduplication et des remplacements J5](docs/validation/J5-REAL-DEDUPLICATION-AND-SUBSTITUTION-CORRECTION-20260816.md)
- [Retest réel V4, compositions et correction du réarmement J5](docs/validation/J5-REAL-V4-LINEUPS-AND-REARM-CORRECTION-20260816.md)
- [Qualification du réarmement réel et correction V5 du carton de banc J5](docs/validation/J5-REAL-V5-BENCH-CARD-CORRECTION-20260816.md)
- [Validation hors ligne des règles métier incidents V6](docs/validation/J5-FOOTBALL-INCIDENT-BUSINESS-RULES-V6-20260817.md)
- [Qualification réelle V6 et correction hors ligne V7](docs/validation/J5-REAL-V6-LENS-PASS-AND-V7-INJURY-SUBSTITUTION-CORRECTION-20260817.md)
- [Qualification historique V8 des variantes d'incidents](docs/validation/J5-REAL-V8-PENALTY-INCIDENT-VARIANTS-20260818.md)
- [Correction V9 du carton de banc `Other reason`](docs/validation/J5-REAL-V9-BENCH-CARD-OTHER-REASON-20260818.md)
- [Correction V10 de l'origine redondante d'un but régulier](docs/validation/J5-REAL-V10-REGULAR-GOAL-ORIGIN-20260818.md)
- [Correction V11 du motif de carton `Off the ball foul`](docs/validation/J5-REAL-V11-OFF-BALL-CARD-REASON-20260818.md)
- [Correction V12 d'une séance terminale entièrement non minutée](docs/validation/J5-REAL-V12-UNMINUTED-SHOOTOUT-20260818.md)
- [Correction V13 du motif de carton `Leaving field`](docs/validation/J5-OBSERVED-V13-LEAVING-FIELD-CARD-REASON-20260818.md)
- [Qualification V14 du marqueur live `Extra time`](docs/validation/J5-OBSERVED-V14-LIVE-EXTRA-TIME-PERIOD-20260827.md)
- [Work Order V14 validé](docs/work_orders/completed/WO-SS-20260827-012-j5-live-extra-time-period.md)
- [Readiness technique V15 des tableaux d'actions vides](docs/validation/J5-OBSERVED-V15-EMPTY-SHOOTOUT-ACTIONS-TECHNICAL-READINESS-20260830.md)
- [Qualification fournisseur V15 bornée](docs/validation/J5-V15-PROVIDER-QUALIFICATION-20260830.md)
- [Work Order correctif V15 validé](docs/work_orders/completed/WO-SS-20260830-017-j5-incidents-empty-shootout-action-v15.md)
- [Qualification hors ligne de la session combinée J4 phase 2 + J5](docs/validation/J4-J5-COMBINED-QUALIFICATION-SESSION-20260818.md)
- [Work Order validé de qualification réelle J5](docs/work_orders/completed/WO-SS-20260815-006-j5-real-event-data-qualification.md)
- [Readiness technique J6](docs/validation/J6-TECHNICAL-READINESS-20260818.md)
- [Qualification humaine J6 de l'interface — phase 1](docs/validation/J6-HUMAN-HISTORY-UI-PHASE1-20260819.md)
- [Qualification humaine finale de l'interface J6](docs/validation/J6-HUMAN-HISTORY-UI-QUALIFICATION-20260819.md)
- [Qualification opératoire J6 de la sauvegarde/restauration](docs/validation/J6-BACKUP-RESTORE-QUALIFICATION-20260819.md)
- [Work Order J6 validé](docs/work_orders/completed/WO-SS-20260818-007-history-j6.md)
- [Readiness technique J7 — acquise](docs/validation/J7-TECHNICAL-READINESS-20260819.md)
- [Work Order J7 validé](docs/work_orders/completed/WO-SS-20260819-008-canonical-export-j7.md)
- [Readiness technique J8](docs/validation/J8-TECHNICAL-READINESS-20260829.md)
- [Preuve de campagne bornée J8](docs/validation/J8-BOUNDED-CAMPAIGN-20260830.md)
- [Readiness de la seconde campagne J8](docs/validation/J8-SECOND-BOUNDED-CAMPAIGN-READINESS-20260830.md)
- [Preuve de la seconde campagne J8](docs/validation/J8-SECOND-BOUNDED-CAMPAIGN-20260830.md)
- [Readiness de la revue humaine ciblée J8](docs/validation/J8-TARGETED-HUMAN-REVIEW-READINESS-20260830.md)
- [Revue humaine ciblée finale J8](docs/validation/J8-TARGETED-HUMAN-REVIEW-20260830.md)
- [Rapport final gelé J8](docs/benchmark/J8-BENCHMARK-REPORT-20260830.md)
- [Validation finale J8](docs/validation/J8-FINAL-VALIDATION-20260830.md)
- [Work Order J8 validé](docs/work_orders/completed/WO-SS-20260829-016-benchmark-j8.md)
- [Work Order validé de décision J9](docs/work_orders/completed/WO-SS-20260831-018-decision-j9.md)
- [Work Order actif de preuve de robustesse J9](docs/work_orders/active/WO-SS-20260831-019-j9-provider-robustness.md)
- [Rapport arrêté de la campagne J9](docs/validation/J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831.md)
- [Work Order runtime J9 validé](docs/work_orders/completed/WO-SS-20260831-020-j9-playwright-graceful-close.md)
- [Work Order runtime J9 validé](docs/work_orders/completed/WO-SS-20260831-021-j9-playwright-minimum-on-wire-delay.md)
- [Work Order validé de réexamen ADR-SS-002](docs/work_orders/completed/WO-SS-20260831-022-j9-adr-ss-002-reexamination.md)
- [Work Order validé de nouvelle preuve J9](docs/work_orders/completed/WO-SS-20260831-023-j9-provider-robustness-v11.md)
- [Work Order validé de nettoyage fail-closed J6/J9](docs/work_orders/completed/WO-SS-20260831-024-j9-backup-pipeline-fail-closed-cleanup.md)
- [Incident fail-closed post-sauvegarde WO-023](docs/validation/J9-WO023-POST-BACKUP-CLEANUP-INCIDENT-20260831.md)
- [Work Order validé de durcissement de preuve J6/J9](docs/work_orders/completed/WO-SS-20260831-025-j9-backup-cleanup-proof-hardening.md)
- [Readiness locale du durcissement de preuve WO-025](docs/validation/J9-WO025-BACKUP-CLEANUP-PROOF-HARDENING-READINESS-20260901.md)
- [Qualification du nouvel essai V28 WO-023](docs/validation/J9-WO023-V28-BACKUP-RESTORE-RETRY-20260901.md)
- [Manifeste gelé de campagne WO-023](docs/validation/J9-WO023-PROVIDER-CAMPAIGN-MANIFEST-20260901.md)
- [Rapport autonome PASS de la campagne WO-023](docs/validation/J9-WO023-PROVIDER-ROBUSTNESS-CAMPAIGN-20260901.md)
- [Work Order terminé d'étude d'intégration optionnelle — ADR-SS-003 acceptée](docs/work_orders/completed/WO-SS-20260901-026-optional-integration-feasibility.md)

## J3 et J4 validés, voies fournisseur de nouveau verrouillées

La qualification humaine du `2026-08-14` a collecté dix pages sur dix, après les cinq pages
observées le `2026-08-13`. Elle confirme que le parcours repart de la page 1, persiste avant
parsing, continue uniquement sur `hasNextPage=true` et s’arrête normalement sur `false`, sans
conserver l’ancienne hypothèse fixe de cinq pages. Une barrière locale interdit toute page 26.

La pagination dynamique est désormais qualifiée dans le périmètre manuel J3. Toute automatisation,
planification, collecte live ou généralisation à une autre famille reste hors périmètre.

La première unité post-qualification applique désormais la politique de cache au chemin dynamique.
La preuve minimisée v4 distingue explicitement les pages demandées au fournisseur des pages
résolues depuis un snapshot local frais, sans introduire de contournement manuel du TTL.

L’inspection JSON locale permet maintenant de relire explicitement un snapshot brut déjà persisté,
après vérification de son intégrité et de son innocuité. Elle reste une aide opérateur en lecture
seule : le brut n’est ni téléchargé, ni réécrit, ni ajouté aux rapports de qualification.

La qualification complémentaire du cache et de cette inspection confirme que le formatage local ne
rafraîchit ni n’invalide un checkpoint de cache, ne modifie aucune classification historique et ne
rend pas un snapshot incompatible éligible. La matrice technique et les observations humaines sont
consignées dans
[`docs/validation/J3-WINDOWS-CACHE-AND-LOCAL-SNAPSHOT-INSPECTION-QUALIFICATION-20260814.md`](docs/validation/J3-WINDOWS-CACHE-AND-LOCAL-SNAPSHOT-INSPECTION-QUALIFICATION-20260814.md).

Le jalon J3 est validé et son Work Order est archivé dans `docs/work_orders/completed`. Cette
clôture ne change pas les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY`. Le polling, la planification, le déploiement VPS, une nouvelle famille
d’endpoint ou l’intégration au Betting Project principal restent interdits tant qu’un nouveau Work
Order et une décision de gouvernance dédiée ne les autorisent pas.

L'implémentation J4 construit désormais une vue métier locale sur les sources déjà présentes,
synthétiques ou issues de la campagne réelle strictement bornée. Une normalisation manuelle d’un
snapshot `SCHEDULED_EVENTS` compatible vérifie son intégrité et le reparse sans modifier sa
classification historique ; le corpus de démonstration reste explicitement `SYNTHETIC_FIXTURE`.

La campagne J4 sous-étape 1 a exécuté les deux événements fixes autorisés. Les snapshots 16 et 17
ont été persistés, classés `PARSED` et ouverts localement. Un défaut de navigation ramenait
toutefois la fiche de Saint-Étienne — Clermont Foot au 15 août au lieu de sa date civile du 14
août, et la fiche utilisait des libellés synthétiques statiques pour une provenance fournisseur.
Le correctif conserve maintenant la date du match et affiche la provenance réellement persistée.
Le retest humain sans réseau a confirmé les deux retours par date et la provenance.

La sous-étape 2 a ensuite qualifié les événements paramétrables `16483632` et `16412917`. Un
rappel de `16412917` avant le coup d'envoi a effectué un nouvel appel mais dédupliqué la réponse
inchangée sur le snapshot 19. Un second rappel après le coup d'envoi a persisté le snapshot 23,
fait évoluer le statut à `inprogress` et conservé les deux observations consultables sous la même
identité canonique. L'arrêt global, le reverrouillage des six paramètres, le contrôle `LOCKED`
après redémarrage et l'arrêt gracieux final ont été confirmés. La Pull Request `#8`, déclarée
`MERGEABLE` et `CLEAN`, a été fusionnée sur `main` par le commit
`950bb0f0ddd0edfe2a5e7d1e768498c049a86467`, puis le Work Order a été archivé `VALIDATED`.

```text
J4_IMPLEMENTATION_STATUS=IMPLEMENTATION_MERGED
J4_OFFLINE_STATUS=OFFLINE_PATH_QUALIFIED
J4_REAL_PHASE1_CAMPAIGN_STATUS=EXECUTED
J4_REAL_PHASE1_HUMAN_STATUS=PASS_AFTER_CORRECTIVE_LOCAL_RETEST
J4_REAL_PHASE2_PARAMETERIZED_EVENT_STATUS=PASS
J4_REAL_PHASE2_MANUAL_RECALL_STATUS=PASS
J4_REAL_PHASE2_PRE_KICKOFF_DEDUPLICATION_STATUS=PASS
J4_REAL_PHASE2_IN_MATCH_REFRESH_STATUS=PASS
J4_APPEND_ONLY_HISTORY_STATUS=PASS
J4_CONFIGURATION_RELOCK_STATUS=PASS
J4_FINAL_APPLICATION_SHUTDOWN_STATUS=PASS
J4_FINAL_STANDARD_TESTS=212
J4_FINAL_INTEGRATION_TESTS=16
J4_FINAL_MAVEN_PROVIDER_CALLS=0
J4_PULL_REQUEST=8
J4_MERGE_COMMIT=950bb0f0ddd0edfe2a5e7d1e768498c049a86467
J4_WORK_ORDER_STATUS=VALIDATED
J4_CAN_BE_CLOSED=YES
J4_CLOSED=YES
```

Cette situation ne déverrouille aucune nouvelle famille, automatisation ou dépendance de
production. Les rappels de sous-étape 2 restent exclusivement manuels et unitaires.

## J5 réel : V14 qualifié sur import JSON local

J5 réutilise l'identité synthétique `900001` de J4 pour démontrer les trois familles demandées. Les
neuf fixtures J5 sont explicitement synthétiques et ne valident aucun schéma fournisseur. La page
locale distingue une rupture structurelle, une famille partielle, une liste vide valide et une
famille fournisseur indisponible. Les chemins techniques manquants restent conservés dans le
rapport de complétude, mais ne sont plus affichés au-dessus des tableaux d'incidents et de
compositions ; les badges, compteurs, données et lignes des tableaux restent inchangés.

```text
J5_V13_IMPLEMENTATION_STATUS=VALIDATED
J5_V13_HUMAN_OFFLINE_QUALIFICATION=PASS
J5_V13_PROVIDER_SCHEMA_VALIDATED=YES
J5_V13_PROVIDER_SCHEMA_VALIDATION_SCOPE=EVENTS_16691018_AND_16851672
J5_V14_PROVIDER_SCHEMA_VALIDATED=YES_OWNER_LOCAL_JSON_IMPORT
J5_V14_PROVIDER_SCHEMA_VALIDATION_SCOPE=EVENT_16809018_OBSERVATIONS_567_AND_570
J5_SYNTHETIC_FIXTURES_PROVIDER_SCHEMA_VALIDATED=NO
J5_APPLICATION_TRANSPORT=IMPLEMENTED_GUARDED_DEFAULT_OFF
J5_DISCOVERY_ATTEMPTS=1
J5_DISCOVERY_RESULT=HTTP_403_STOPPED_NO_RETRY
J5_FIXTURE_ORIGIN=SYNTHETIC
J5_V13_FLYWAY_VERSION=20
J5_FLYWAY_VERSION=28
J5_MAVEN_PROVIDER_CALLS=0
J5_REAL_TECHNICAL_READINESS=PASS
J5_REAL_FIRST_CAMPAIGN=HTTP_404_MISCLASSIFIED_AND_LOCKED
J5_REAL_FIRST_CAMPAIGN_PROVIDER_CALLS=1
J5_REAL_FIRST_STATISTICS_SNAPSHOT=30
J5_REAL_LAST_SUCCESSFUL_CAMPAIGN_PROVIDER_CALLS=3
J5_REAL_LATEST_FAILED_CAMPAIGN_PROVIDER_CALLS=2
J5_REAL_STATISTICS_LATEST=V2_COMPLETE_SNAPSHOT_716_OBSERVATION_322_100_PERCENT
J5_REAL_STATISTICS_UNAVAILABLE_RETEST=SNAPSHOT_118_CONTINUED_TO_INCIDENTS_AND_LINEUPS
J5_REAL_INCIDENTS_LAST_PASS=V15_PARTIAL_SNAPSHOT_717_OBSERVATION_324_164_OF_179
J5_REAL_INCIDENTS_LATEST=V15_PARTIAL_SNAPSHOT_717_OBSERVATION_324_EVENT_16691018
J5_REAL_INCIDENTS_CURRENT_PARSER=event-incidents-v15
J5_REAL_SUBSTITUTION_PLAYERS=PASS_REAL_RENDERED
J5_REAL_LAST_SUCCESSFUL_TERMINAL=COMPLETED_LOCKED
J5_REAL_LINEUPS=V2_PARTIAL_SNAPSHOT_720_OBSERVATION_325_99_PERCENT
J5_HTTP_404_POLICY=ENDPOINT_UNAVAILABLE_CONTINUE_NO_RETRY
J5_CORRECTIVE_V3_RETEST=PASS_REAL
J5_CORRECTIVE_V4_RETEST=PASS_REAL_THREE_CALLS
J5_COMPLETED_CAMPAIGN_REARM=PASS_REAL_SECOND_CAMPAIGN_STARTED
J5_REAL_V5_FIRST_HALF_CAMPAIGN=PASS_SNAPSHOTS_61_62_63
J5_REAL_V5_FULL_TIME_REPLAY=PASS_SNAPSHOTS_64_65_66
J5_REAL_BENCH_CARD_CORRECTIVE_PARSER=event-incidents-v5
J5_REAL_BENCH_CARD_CORRECTION=PASS_REAL
J5_INCIDENT_RULES_SOURCE=docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md
J5_INCIDENT_CURRENT_PARSER=event-incidents-v15
J5_INCIDENT_V14_STATUS=VALIDATED
J5_INCIDENT_V14_HUMAN_FUNCTIONAL_QUALIFICATION=PASS
J5_INCIDENT_V14_LIVE_EXTRA_TIME=EXACT_TEXT_ACCEPTED_WHEN_LIVE
J5_REAL_INCIDENTS_LATEST_DIRECT=V15_PARTIAL_SNAPSHOT_717_OBSERVATION_324_EVENT_16691018
J5_INCIDENT_V14_J8_HISTORICAL_RESULT=SCHEMA_INCOMPATIBLE_SNAPSHOT_717_EVENT_16691018
J5_INCIDENT_V15_EXACT_STORED_READ_ONLY_PROBE=PARSED_PARTIAL_91_SNAPSHOT_717
J5_INCIDENT_V15_PROVIDER_QUALIFICATION=PASS_EVENT_16691018_SNAPSHOT_717_OBSERVATION_324
J5_INCIDENT_V15_EMPTY_ARRAY_VARIANT_REOBSERVED=YES_BY_BYTE_IDENTICAL_SNAPSHOT_REUSE
J5_INCIDENT_V15_PERSISTED_REPARSE=NO
J5_PROVIDER_SCHEMA_CURRENT_STATUS=VALIDATED_BOUNDED_EVENT_16691018
J5_INCIDENT_V14_HISTORICAL_QUALIFICATION=RETAINED_IN_ORIGINAL_BOUNDED_SCOPE
J5_INCIDENT_CORRECTIVE_WORK_ORDER=WO_017_VALIDATED_COMPLETED
J5_INCIDENT_CORRECTIVE_WORK_ORDER_LOCATION=docs/work_orders/completed
J5_INCIDENT_OWNER_CLOSURE=AUTHORIZED_2026_08_30
J5_INCIDENT_ADDITIONAL_PROVIDER_CALL_AUTHORIZED=NO
J5_INCIDENT_V6_STATUS=PASS_REAL_LENS_PSG
J5_INCIDENT_V7_STATUS=PASS_REAL_AND_INHERITED_BY_V8_V9_V10_V11_V12_V13
J5_INCIDENT_V7_FULL_OPERATOR_PAYLOAD=PASS_23_OF_23
J5_INCIDENT_V7_STANDARD_TESTS=330_PASS
J5_INCIDENT_V7_INTEGRATION_TESTS=28_PASS
J5_INCIDENT_V7_FLYWAY_UPGRADE=V13_TO_V14_PASS
J5_INCIDENT_V8_SHOOTOUT_PAYLOAD=PASS_OFFLINE_36_OF_36_PEN_TO_146
J5_INCIDENT_V8_CARD_PAYLOAD=PASS_OFFLINE_16_OF_16_THREE_REASONS_ABSENT
J5_INCIDENT_V8_WOODWORK=inGamePenalty_AND_penaltyShootout
J5_INCIDENT_V8_FLYWAY_UPGRADE=V14_TO_V15_PASS
J5_INCIDENT_V8_STANDARD_TESTS=340_PASS
J5_INCIDENT_V8_INTEGRATION_TESTS=29_PASS
J5_INCIDENT_V8_HUMAN_RETEST=PASS_THREE_COMPLETED_LOCKED_CAMPAIGNS
J5_INCIDENT_V8_REAL_SHOOTOUT=PASS_SNAPSHOT_139_PEN_TO_146
J5_INCIDENT_V8_REAL_CARD_WITHOUT_REASON=PASS_SNAPSHOT_144
J5_INCIDENT_V8_REAL_WOODWORK_INGAME=PASS_SNAPSHOT_150
J5_INCIDENT_V8_WOODWORK_SHOOTOUT=PASS_OFFLINE_CONTRACT_AND_PERSISTENCE
J5_INCIDENT_V9_TRIGGER=SNAPSHOT_162_CARD_OTHER_REASON_BENCH_ADDED_TIME
J5_INCIDENT_V9_FULL_OPERATOR_PAYLOAD=PASS_OFFLINE_20_OF_20
J5_INCIDENT_V9_NORMALIZED_MINUTE=90_PLUS_9
J5_INCIDENT_V9_FLYWAY_UPGRADE=V15_TO_V16_PASS
J5_INCIDENT_V9_STANDARD_TESTS=344_PASS
J5_INCIDENT_V9_INTEGRATION_TESTS=30_PASS
J5_INCIDENT_V9_HUMAN_RETEST=PASS_COMPLETED_LOCKED_THREE_CALLS
J5_INCIDENT_V9_REAL_OBSERVATION=77_COMPLETE_100_PERCENT
J5_INCIDENT_V9_REAL_LINEUPS=SNAPSHOT_165_OBSERVATION_78_85_OF_85
J5_INCIDENT_V9_FUNCTIONAL_EVIDENCE=5_PNG_464810_BYTES
J5_INCIDENT_V10_TRIGGER=SNAPSHOT_179_FIVE_REGULAR_GOALS_FROM_REGULAR
J5_INCIDENT_V10_FULL_OPERATOR_PAYLOAD=PASS_OFFLINE_7_OF_7_COMPLETE_24_OF_24
J5_INCIDENT_V10_FLYWAY_UPGRADE=V16_TO_V17_PASS
J5_INCIDENT_V10_STANDARD_TESTS=348_PASS
J5_INCIDENT_V10_INTEGRATION_TESTS=31_PASS
J5_INCIDENT_V10_HUMAN_RETEST=PASS_UNDER_V11_EVENT_16251993
J5_INCIDENT_V10_FUNCTIONAL_EVIDENCE=3_PNG_366639_BYTES
J5_INCIDENT_V11_TRIGGER=INLINE_CARD_REASON_OFF_THE_BALL_FOUL
J5_INCIDENT_V11_OBSERVED_SHAPE=PASS_OFFLINE_COMPLETE_3_OF_3
J5_INCIDENT_V11_ORDERED_SERVICE=PASS_THREE_CALLS_CONTINUES_TO_LINEUPS
J5_INCIDENT_V11_FLYWAY_UPGRADE=V17_TO_V18_PASS
J5_INCIDENT_V11_STANDARD_TESTS=358_PASS
J5_INCIDENT_V11_INTEGRATION_TESTS=32_PASS
J5_INCIDENT_V11_HUMAN_RETEST=PASS_OFF_BALL_CARD_EVENT_16671566
J5_INCIDENT_V11_REAL_CAMPAIGN=EVENT_16671566_COMPLETED_LOCKED_THREE_CALLS
J5_INCIDENT_V11_REAL_OBSERVATION=SNAPSHOT_185_OBSERVATION_96_COMPLETE_69_OF_69
J5_INCIDENT_V11_FUNCTIONAL_EVIDENCE=11_PNG_1128540_BYTES_PLUS_INLINE_OPERATOR_JSON
J5_INCIDENT_V12_TRIGGER=SNAPSHOT_189_PEN_AND_14_SHOOTOUTS_WITHOUT_EFFECTIVE_MINUTE
J5_INCIDENT_V12_FULL_OPERATOR_PAYLOAD=PASS_OFFLINE_PARTIAL_33_INCIDENTS_14_SHOOTOUTS
J5_INCIDENT_V12_MISSED_WITHOUT_REASON_AND_DESCRIPTION=PARTIAL_BOTH_PENALTY_TYPES
J5_INCIDENT_V12_MINUTE_POLICY=NULL_NO_SEQUENCE_INFERENCE
J5_INCIDENT_V12_FLYWAY_UPGRADE=V18_TO_V19_PASS_APPEND_ONLY
J5_INCIDENT_V12_TARGETED_TESTS=30_PASS
J5_INCIDENT_V12_STANDARD_TESTS=366_PASS
J5_INCIDENT_V12_INTEGRATION_TESTS=34_PASS
J5_INCIDENT_V12_HUMAN_RETEST=PASS_UNDER_V13_EVENT_16691018_SNAPSHOT_189_OBSERVATION_100
J5_INCIDENT_V12_REAL_RESULT=PARTIAL_33_INCIDENTS_142_OF_171_THREE_CALLS
J5_INCIDENT_V13_TRIGGER=INLINE_CARD_REASON_LEAVING_FIELD
J5_INCIDENT_V13_OBSERVED_SHAPE=PASS_OFFLINE_COMPLETE
J5_INCIDENT_V13_ORDERED_SERVICE=PASS_THREE_CALLS_CONTINUES_TO_LINEUPS
J5_INCIDENT_V13_FLYWAY_UPGRADE=V19_TO_V20_PASS_APPEND_ONLY
J5_INCIDENT_V13_TARGETED_TESTS=33_PASS
J5_INCIDENT_V13_STANDARD_TESTS=369_PASS
J5_INCIDENT_V13_INTEGRATION_TESTS=35_PASS
J5_INCIDENT_V13_HUMAN_RETEST=PASS_SHOOTOUT_AND_LEAVING_FIELD
J5_INCIDENT_V13_REAL_SHOOTOUT=EVENT_16691018_COMPLETED_LOCKED_SNAPSHOTS_188_189_192
J5_INCIDENT_V13_REAL_LEAVING_FIELD=EVENT_16851672_COMPLETED_LOCKED_SNAPSHOTS_194_195_196
J5_UI_INCIDENT_MISSING_PATHS=HIDDEN_OFFLINE_TEST_PASS
J5_UI_LINEUP_MISSING_PATHS=HIDDEN_OFFLINE_TEST_PASS
J5_UI_TABLE_CONTENT=UNCHANGED_BY_TEMPLATE_ONLY_CORRECTION
J5_UI_VISUAL_RETEST=PASS_OPERATOR_9_SCREENSHOTS
J4_J5_COMBINED_SESSION=PASS_REAL_EVENT_16671566
J4_J5_COMBINED_J4_RESULT=COMPLETED_LOCKED_ONE_CALL_SNAPSHOT_183
J4_J5_COMBINED_J5_RESULT=COMPLETED_LOCKED_THREE_CALLS_SNAPSHOTS_184_185_186
J4_J5_SHARED_REQUEST_COORDINATOR=SERIALIZED_MINIMUM_DELAY_3_SECONDS
J5_OFFLINE_WORK_ORDER_STATUS=VALIDATED
J5_REAL_WORK_ORDER_STATUS=VALIDATED
J5_REAL_WORK_ORDER_LOCATION=docs/work_orders/completed/WO-SS-20260815-006-j5-real-event-data-qualification.md
J5_LOCAL_CONFIGURATION_RELOCKED=YES_OPERATOR_EVIDENCE
J5_J4_LOCKED_AFTER_RESTART=PASS
J5_J5_LOCKED_AFTER_RESTART=PASS
J5_CURRENT_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
J5_FINAL_APPLICATION_STOPPED_AFTER_RELOCK=PASS
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=YES
```

`TOURNAMENT_STANDINGS` demeure différé : il ne fait pas partie de la preuve de sortie J5 définie
par le cadrage. Les captures de validation humaine ne sont pas versionnées ; leur constat minimisé
est conservé dans les rapports J5. Les campagnes V5, V6, V7 et le retest V8 restent des preuves
historiques qualifiées. La
variante exacte `Woodwork/woodwork` de `penaltyShootout` reste une preuve automatisée hors ligne,
car le lot du 2026-08-18 observe ce résultat sur `inGamePenalty` et observe séparément neuf tirs au
but, mais pas leur combinaison. La réussite V9 de Cardiff City — Wrexham reste une preuve
historique. La campagne V11 sur `16251993` a reparsé le snapshot 179 et qualifié la correction V10.
La campagne combinée ultérieure sur `16671566` a ensuite enchaîné J4 phase 2 et J5 sans redémarrage,
atteint les trois familles J5 et rendu le carton `Off the ball foul` inchangé dans le snapshot 185.
La campagne V13 `16691018` a requalifié historiquement la séance non minutée et atteint les
compositions après trois appels ; la campagne `16851672` a qualifié séparément le motif exact
`Leaving field` et les trois familles. Ces qualifications restent valides dans leur corpus borné.
La réponse incidents observée pendant J8 pour `16691018` utilise une variante avec des listes
d'actions vides que V14 rejette. WO-017 introduit V15 ; sa sonde read-only avait d'abord rendu les
octets exacts du snapshot 717 `PARSED/PARTIAL · 91%` sans persistance. Après go propriétaire
distinct, une seule campagne corrective a ensuite réobservé des octets dédupliqués vers ce même
snapshot : l'observation V15 `324` conserve 35 incidents et `164/179` signaux, puis la campagne
atteint les compositions dans le snapshot `720`, observation `325`, et termine
`COMPLETED_LOCKED` après trois appels sans retry. La preuve J8/V14 reste immuable et sa fenêtre
exclusive demeure `PARTIAL`.

L'activation corrective a été limitée à l'arbre de processus du lanceur ; `.env` est resté
inchangé et bloquant. Un redémarrage inerte a confirmé J5 `LOCKED` et sa préparation désactivée,
puis l'application a été arrêtée sans listener résiduel sur `127.0.0.1:8087`. Le Work Order réel
J5 historique reste `VALIDATED` et archivé dans `completed`. Après constat propriétaire du test
fonctionnel concluant, le Work Order correctif WO-017 est lui aussi clôturé `VALIDATED` et archivé
dans `completed`. Le go est consommé et n'autorise aucun nouvel appel.

## J6 : historique et sauvegarde/restauration validés

La démonstration J6 part de l'identité synthétique J4/J5 et ajoute des versions postérieures à un
état `finished`. Les cinq flux peuvent être filtrés et paginés ; deux versions du même flux peuvent
être comparées sans relire le JSON brut. Chaque version est associée à une classification primaire
parmi `BASELINE`, `TECHNICAL_DUPLICATE`, `LOCAL_REPARSE`, `SEMANTICALLY_UNCHANGED`,
`SYNTHETIC_CHANGE`, `PROVIDER_UPDATE`, `LATE_ENRICHMENT` et `LATE_CORRECTION`.
Les corrections du corpus restent explicitement `SYNTHETIC_CHANGE`; les classifications `LATE_*`
sont réservées à la provenance fournisseur et couvertes hors réseau par les tests.

Le tableau de bord n'expose qu'un aperçu de rétention. L'exécution demeure une commande locale
ponctuelle, application arrêtée et connecteurs verrouillés. La procédure complète est documentée
dans `docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md`; elle exige PowerShell 7.4, `age`, une
destination absolue hors dépôt, une restauration temporaire qualifiée, puis la reprise exacte du
cutoff, du SHA-256 de plan et de la phrase de confirmation issus d'un même aperçu.

La qualification humaine de l'interface a confirmé sur dix-huit captures externes les cinq flux,
les baselines, les changements synthétiques, les états sémantiquement inchangés, quatre
comparaisons — dont les états 2 → 96 et 96 → 97 — et la pagination à trois éléments sur quatre
pages. Le premier chargement a ajouté six versions J6 sur les six versions nominales J4/J5 ; le
second a confirmé `0 ajoutée / 12 déjà présentes`, avec un total stable à douze. L'application a
ensuite été trouvée arrêtée sur le port 8087.

La qualification opératoire a ensuite créé hors dépôt une archive chiffrée et son manifeste,
restauré le dump dans une base PostgreSQL temporaire puis comparé treize mesures de structure,
d'intégrité et de provenance. Toutes sont identiques entre la source et la restauration. Le
manifeste couvre le snapshot 272 reçu à `2026-08-18T21:44:27.857664Z`, la base temporaire ne subsiste
plus et une seconde invocation avec le même nom est refusée avant toute écriture. Cette preuve ne
contient aucun secret et n'autorise aucune purge.

```text
J6_IMPLEMENTATION_STATUS=VALIDATED
J6_FLYWAY_VERSION=22
J6_HISTORY_STREAMS=5
J6_HISTORY_PAGE_SIZE_DEFAULT=25
J6_HISTORY_PAGE_SIZE_MAXIMUM=100
J6_RETENTION_DAYS_DEFAULT=30
J6_RETENTION_BATCH_MAXIMUM=500
J6_RETENTION_WEB_EXECUTION=ABSENT
J6_RETENTION_AUTOMATIC_SCHEDULING=ABSENT
J6_BACKUP_FORMAT=PG_DUMP_CUSTOM_ENCRYPTED_WITH_AGE
J6_PRIMARY_DATABASE_PURGE_EXECUTED=NO
J6_PROVIDER_CALLS_DURING_IMPLEMENTATION=0
J6_HUMAN_UI_PHASE1=PASS
J6_HUMAN_UI_QUALIFICATION=PASS
J6_OPERATIONAL_BACKUP_RESTORE_QUALIFICATION=PASS
J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID=272
J6_WORK_ORDER_STATUS=VALIDATED
```

Le Work Order J6 est clos. La purge de la base primaire demeure hors périmètre, non exécutée et non
autorisée ; toute suppression future d'octets exige une autorisation et un Work Order distincts,
puis la reprise exacte d'un nouvel aperçu de rétention.

## J7 : export canonique validé, PR #12 propre et fusionnable

J7 sélectionne sous transaction locale en lecture seule les dernières observations des cinq
composants `EVENT_STATE`, `EVENT_DETAILS`, `EVENT_STATISTICS`, `EVENT_INCIDENTS` et
`EVENT_LINEUPS`. Le JSON respecte le schéma Draft 2020-12
`urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1`. Les absences,
indisponibilités et listes vides valides sont explicites ; les origines synthétiques ou fournisseur
et l'état de rétention du brut restent visibles sans recopier aucun octet brut. Les identifiants
numériques sont positifs et bornés à `Long.MAX_VALUE` ; le scanner applique UTF-8 strict et contrôle
à la fois les octets, le texte et les clés JSON sensibles.

La création n'accorde que `COHERENCE_CHECKED`. L'aperçu HTML permet ensuite de saisir une phrase
exacte pour `HUMAN_VALIDATED` ou `REJECTED`; une dérive des observations courantes bloque la
validation avec `SOURCE_SET_CHANGED`. Le recalcul de fraîcheur tient un verrou PostgreSQL partagé
avec les écritures d'observation et les mutations autorisées des snapshots qu'elles référencent.
Avant le fichier terminal, une intention write-ahead persiste le
statut, l'heure, le motif éventuel, le chemin, le hash et la taille afin d'authentifier tout retry.
Les fichiers candidats, validés et rejetés restent sous la racine locale configurée ; ils sont
publiés sans remplacement par lien physique atomique après synchronisation d'un temporaire sur le
même système de fichiers. Un candidat resté sans ligne après un résultat PostgreSQL indéterminé
n'est repris qu'après reconstruction courante et égalité octet par octet. Seul le fichier validé
est téléchargeable après nouvelle vérification de son schéma, sa taille et son SHA-256.

```text
J7_IMPLEMENTATION_STATUS=VALIDATED
J7_SCHEMA_VERSION=1.0.0
J7_FLYWAY_VERSION=23
J7_CURRENT_ONLY=YES
J7_HISTORY_INCLUDED=NO
J7_HUMAN_DECISION_REQUIRED=YES
J7_AUTOMATIC_PROVIDER_CALLS=0
J7_AUTOMATIC_TRANSFER=ABSENT
J7_PRIMARY_DATABASE_PURGE=NO
J7_STANDARD_SUITE=PASS_457_TESTS_0_FAILURES_0_ERRORS_2_SKIPPED_WINDOWS_SYMLINK
J7_INTEGRATION_SUITE=PASS_43_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
J7_COMBINED_SESSION_TARGETED_TESTS=PASS_55_TESTS_0_FAILURES_0_ERRORS_0_SKIPPED
J7_COMBINED_SESSION_ACTUAL_CONFIG_STARTUP=PASS_127_0_0_1_8087
J7_COMBINED_SESSION_MANUAL_COORDINATOR=PASS_J3_J4_J5
J7_COMBINED_SESSION_AUTOMATIC_PROVIDER_CALLS=0
J7_COMBINED_SESSION_NEW_PROVIDER_ENDPOINTS=0
J7_COMBINED_HUMAN_SINGLE_INSTANCE_J3_J4_J5_J6_J7=PASS
J7_FIRST_HUMAN_EXPORT=PASS_PROVIDER_EVENT_16707704_HUMAN_VALIDATED_DOWNLOADED
J7_PROVIDER_16421055_EXPORT=PASS_HUMAN_VALIDATED_DOWNLOADED_SHA256_VERIFIED
J7_PROVIDER_16691018_REJECTION=PASS_REJECTED_RETAINED_NOT_DOWNLOADABLE
J7_PROVIDER_16691018_VALIDATION=PASS_HUMAN_VALIDATED_DOWNLOADED_SHA256_VERIFIED
J7_SYNTHETIC_REJECTION=PASS_REJECTED_RETAINED_NOT_DOWNLOADABLE
J7_SYNTHETIC_VALIDATION=PASS_HUMAN_VALIDATED_DOWNLOADED_SHA256_VERIFIED
J7_HUMAN_QUALIFICATION=PASS
J7_WORK_ORDER_STATUS=VALIDATED
J7_PR_NUMBER=12
J7_PR_STATE=DRAFT_CLEAN_MERGEABLE
J7_MAIN_MERGE=AWAITING_EXPLICIT_CONFIRMATION
```

Un premier export fournisseur complet `16707704` a été validé humainement et téléchargé sans
erreur le 2026-08-19. Le fichier téléchargé est identique au terminal local, ses trois empreintes
sont cohérentes et le scan sensible est vide. Cette preuve complémentaire ne remplace pas les
scénarios obligatoires ci-dessous et ne change donc pas le statut global.

Le parcours ciblé `16691018` a ensuite confirmé l'inspection des avertissements attendus
(`EVENT_STATISTICS` indisponible, incidents et compositions partiels), le rejet motivé, la
conservation d'un unique fichier `.rejected.json` et son refus de téléchargement avec une réponse
locale `404` vide. Les hashes de données et de sources ont été recalculés à l'identique et le scan
sensible du terminal est vide. L'événement a ensuite été recréé, validé puis téléchargé sous
l'export `af9735bc-ccfa-419e-bfea-fb6e500bc5bb`. Son fichier de 28 701 octets est identique au
terminal, avec le SHA-256
`87ed3d0e17a3150bf3a7aee1834da35e40a8dbcafecb037032cdf893ec543063`.

Une séquence supplémentaire, commencée avant minuit le 19 août et terminée après minuit le 20 août
en heure de Paris, a enchaîné dans une seule instance J3 sur six pages, J5 complet, une comparaison
locale J6, J4 phase 2 puis un export J7 validé et téléchargé pour l'événement `16421055`. Les dix
appels fournisseur de cette séquence sont uniquement ceux explicitement confirmés pour J3, J5 et
J4 ; J6 et J7 n'ont exécuté aucun transport. Le fichier J7 de 37 239 octets a été relu hors de
l'application et son SHA-256
`37784bb4187fb80de06b765d8a9cb54b36f8035ed24eee82f9d6791c5a388880` correspond au manifeste
persisté, sans clé interdite, contenu sensible ni avertissement. J3, J4 et J5 sont revenus à leurs
états terminaux verrouillés.

Le runbook synthétique est également fermé sur l'événement `900001` : un premier export est
`REJECTED`, conservé localement et non téléchargeable, puis un second est `HUMAN_VALIDATED` et
téléchargé. Les deux terminaux conservent le même `dataSha256`
`1a30148f03efce117bbe1cb8b30308eeab338741bbec392a4b5c58de488984a5` et le même jeu de sources ;
le fichier validé de 8 707 octets porte le SHA-256
`fb637b4c87fc6a8cba230c6d4ad0928af3d52c1a793d379ae6ac9b9d1888b1f4`. Les cinq provenances et
avertissements synthétiques sont explicites, et les scans sensibles sont vides. Les deux suites
Maven, la revue technique et la recette humaine sont acquises. La PR `#12` pointe sur la branche
publiée, GitHub la déclare `mergeable=true` avec `mergeable_state=clean`, et le Work Order est
archivé `VALIDATED`. La PR reste en brouillon et aucune fusion vers `main` n'a été effectuée.
