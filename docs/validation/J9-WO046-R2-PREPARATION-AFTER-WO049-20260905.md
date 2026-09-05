# WO-046 R2 — Reprise préparatoire après validation WO-049

- Consignation : `2026-09-05T08:33:26Z`, `10:33:26+02:00` Europe/Paris.
- Base de reprise : `516a955de1033ddfe546b934fc7e3b2490ed486c`.
- Branche : `codex/j9-wo046-j7-real-local-e2e-campaign`.
- Nature : compte rendu préparatoire, **pas un manifeste ni un owner-go**.

Les sections 1 à 5 conservent le premier état préparatoire. La section 6 consigne la
décision de réutilisation et le gel R2 intervenus ensuite ; elle définit l'état courant.

## 1. Autorité reçue et limite du go

Le propriétaire valide WO-049, reconnaît son écart d'exécution initial et autorise sa clôture.
Il autorise aussi la reprise WO-046, un nouveau manifeste et un nouveau go, tout en maintenant
explicitement l'interdiction du POST réel :

```text
J9_WO046_RESUME_AFTER_WO049_VALIDATION=YES
J9_WO046_NEW_MANIFEST_AUTHORIZED=YES
J9_WO046_NEW_OWNER_GO_GRANTED=YES
J9_WO046_REAL_POST_AUTHORIZED=NO
J9_PROVIDER_NETWORK_AUTHORIZED=NO
J9_REMOTE_RECEIVER_NETWORK_AUTHORIZED=NO
J9_VPS_DEPLOYMENT_AUTHORIZED=NO
J9_PRODUCTION_AUTHORIZED=NO
```

Cet accord pour le go est conservé comme décision, sans déclarer un document V2 exact déjà
construit ou enregistré. Le constructeur `J7ProviderDerivedOwnerGo` et la contrainte partagée
V32 exigent `PROVIDER_DERIVED_REAL_POST_AUTHORIZED=YES`. Inverser le `NO` propriétaire serait
une extension d'autorité ; conserver `NO` dans ce format produirait un bloc refusé. Aucun
bloc exécutoire, aucune écriture de grant, aucun changement V2/V31/V32 n'est effectué.

## 2. Correction validée intégrée

La branche WO-046 propre à `a4dabd8023e957bee23c84c00fba0088146a5d49` est avancée par
fast-forward vers la clôture WO-049 `516a955de1033ddfe546b934fc7e3b2490ed486c`, sans squash,
rebase, push ou fusion main. Références qualifiées :

```text
WO049_IMPLEMENTATION_COMMIT=22130d16781cce41a370cbcdbf6cd1b7b91546c2
WO049_DOCUMENTATION_COMMIT=78cc5cefc184a8ddaf01099f7ef1f847cb51fb4a
WO049_REPORT_SHA256=e273d917ca07b3a9a1cd303d93868ee22d2ab35e0b3b5a253cad271dbdf11ad8
WO049_OWNER_VALIDATION=RECORDED
WO049_EXECUTION_DEVIATION_ACKNOWLEDGED=YES
LAUNCH_PREPARATION_MODULE=scripts/wo046/WO046-LaunchPreparation.psm1
LAUNCH_PREPARATION_MODULE_SHA256=731565492e0f17a61b918019083ecbb7231a8ff64c7c2f807572b46196600bd6
```

Le module est disponible pour préparer un lanceur successeur ; les scripts opérationnels R1
ne sont ni modifiés ni rejoués. Aucun nouveau lancement réel n'est qualifié par cette adoption.

## 3. Ressources receiver : choix explicite avant gel

Le rapport R1 et la documentation WO-049 imposent une décision explicite concernant le volume
conservé. Le contrôle Docker en lecture seule observe :

| Ressource exacte | Observation du 5 septembre | Action |
|---|---|---|
| `betting-project-postgres-1` | ID court `d2d670ac601a`, arrêté avec code 0 | Métadonnées seulement |
| `betting-project_betting-postgres-data` | Présent ; labels projet `betting-project`, volume `betting-postgres-data` | Métadonnées seulement |
| Listeners 8087/8444/5433 | Zéro | Inventaire seulement |
| `betting-sofascore-local-lab-postgres` | Running, healthy | Métadonnées seulement |
| Conteneurs étiquetés Testcontainers | Aucun | Inventaire seulement |

V008 et zéro import sont des faits historiques R1, pas un nouveau contrôle SQL. Le receiver
n'est pas démarré, son volume n'est pas monté ailleurs et aucun payload n'est lu. Aucun
certificat ou secret privé n'est consulté dans ce lot.

**Recommandation soumise :** réutiliser la base/volume R1 exacts après décision propriétaire
explicite et recontrôle borné de leur identité et compteurs. Cette solution conserve l'audit
sans suppression ; elle n'est pas encore sélectionnée par l'agent. Un remplacement exigerait
des cibles nouvelles explicites et la conservation du volume R1. Aucun changement de labels,
purge, démarrage ou adoption silencieuse de ressource n'est effectué.

## 4. Manifeste successeur autorisé, non encore gelé

```text
PLANNED_CAMPAIGN_SERIES=WO046_REAL_LOCAL_R2
PLANNED_MANIFEST_REFERENCE=docs/validation/J9-WO046-J7-REAL-LOCAL-E2E-R2-MANIFEST-20260905.md
MANIFEST_CREATION_AUTHORIZED=YES
MANIFEST_CREATED=NO
MANIFEST_FROZEN=NO
MANIFEST_FREEZE_BLOCKER=EXPLICIT_RECEIVER_RETAINED_VOLUME_DECISION_REQUIRED
```

Le manifeste R1 reste gelé au commit `2cdb93d2236955a34528a11cc982fc01f4554b46`, SHA-256
`6590603286c6c422588bbc3f6a46f502716fa2e2df18b6ad0df741cbd1cbf277`. Son grant révoqué ne
sera pas réutilisé ; la contrainte unique sur le hash de manifeste est préservée.

Après le choix receiver, recontrôler les seules métadonnées de l'export déjà sélectionné,
ses hashes, les preuves qualifiées, les JAR exacts et la validité mTLS. Les observations de
la nuit ne sont pas des recontrôles frais. R2 devra distinguer les compteurs historiques de
grant/révocation des compteurs d'import, au lieu de recopier les zéros initiaux R1.

Le gel local précédera le bloc V2 avec nouvel identifiant et fenêtre future exacte. Ce bloc
ne pourra devenir exécutoire qu'après autorisation explicite du POST. Aucune fenêtre n'est
lancée pendant l'attente de cette décision ; aucun enregistrement durable ou démarrage
applicatif n'est déduit de la reprise préparatoire.

## 5. Vérifications et état de sortie

Le build de clôture WO-049 sur le même arbre de code termine à `2026-09-05T08:32:13Z` :
`clean verify --offline` borné selon son rapport, `skipITs=true`, exclusion J6 natif et profils
réseau désactivés ; 1185 tests, zéro échec/erreur, quatre ignorés, `BUILD SUCCESS`, 56.137 s,
deux goals Failsafe ignorés. Aucun autre build n'est exécuté pour ce seul lot documentaire ;
aucune qualification d'intégration ou receiver nouvelle n'est revendiquée. Contrôles de ce
lot : diff, UTF-8 sans BOM/NUL, hashes gelés et cibles documentaires.

```text
WO046_STATUS=PREPARATION_RESUMED_PENDING_RECEIVER_RESOURCE_DECISION
WO046_WORK_ORDER_MOVE_TO_COMPLETED=NO
NEW_OWNER_GO_OWNER_INTENT=GRANTED
NEW_CANONICAL_OWNER_GO_CREATED=NO
NEW_OWNER_GO_REGISTERED=NO
NEW_OWNER_GO_CONSUMED=NO
REAL_POST_AUTHORIZED=NO
APPLICATION_OR_RECEIVER_STARTED_THIS_TURN=NO
DATABASE_STARTED_THIS_TURN=NO
SQL_QUERIES_THIS_TURN=0
PROVIDER_CALLS_THIS_TURN=0
RECEIVER_HTTP_CALLS_THIS_TURN=0
PUSH_OR_MAIN_MERGE=NO
```

## 6. Réutilisation autorisée, recontrôles frais et gel R2

Le propriétaire autorise explicitement la réutilisation du volume exact
`betting-project_betting-postgres-data` pour R2, avec conservation intégrale de l'audit et
recontrôle préalable, sans purge ni POST. Cette décision lève la porte de choix receiver
de la section 3 ; aucun remplacement de volume ou changement de labels n'est effectué.

L'identité du conteneur existant est comparée au registre de possession R1 : ID complet
`d2d670ac601a2cf308c7883427e506f3d8e7e971ffdf9121901fc81416c83bc3`, nom, image, instant de
création, labels, volume monté et binding exact `127.0.0.1:5433`, restart `no`. Seul ce
PostgreSQL receiver est démarré temporairement pour le recontrôle, puis arrêté proprement.
Le receiver Java et le Local Lab ne sont pas démarrés. Aucun autre volume n'est adopté.

La transaction `REPEATABLE READ READ ONLY`, bornée à 5 s par statement, 1 s pour les locks,
10 s d'inactivité, terminée par `ROLLBACK`, constate à `2026-09-05T08:44:41.303338Z` :

```text
RECEIVER_SCHEMA=8
RECEIVER_MIGRATION_COUNT=8
RECEIVER_FAILED_MIGRATIONS=0
RECEIVER_IMPORTS=0
RECEIVER_PAYLOAD_ROWS=0
RECEIVER_AUDIT_ROWS=0
RECEIVER_TOMBSTONES=0
RECEIVER_OUTBOX_ROWS=0
```

L'instant SQL receiver retourné avec un offset +02:00 est normalisé en UTC. Les démarrage
et arrêt PostgreSQL produisent leurs écritures internes normales : ce contrôle ne prétend
pas à un volume byte-identique. Aucune écriture SQL métier, migration, purge ou suppression
d'audit n'est exécutée. La conservation porte sur les données et l'audit intacts, ici vides.

À `2026-09-05T08:46:31.577255Z`, une transaction primaire en lecture seule (timeouts 10 s,
lock 2 s, inactivité 10 s, `ROLLBACK`) recontrôle uniquement les métadonnées de l'export et
les compteurs : V32, zéro migration échouée, fournisseur verrouillé, un grant R1 révoqué,
zéro consommation/livraison/tentative. Export unique déjà `HUMAN_VALIDATED`, cinq sources
dont quatre fournisseur, taille 35663 et hashes inchangés. Le fichier exact est haché sans
exposition de ses octets. Le warning `MISSING_COMPONENT:EVENT_DETAILS` reste consigné.

À `2026-09-05T08:46:32.4725772Z`, registre PKI privé inchangé, module épinglé, ACL et deux
certificats publics exacts sont recontrôlés : profils et validité courante PASS, validité
pour l'heure suivante au contrôle. Présence de clé vérifiée par métadonnée seulement,
sans lecture/utilisation/export de clé, mutation du magasin ou handshake. Les identifiants
privés et empreintes DER ne sont pas publiés. Ce contrôle ne préjuge pas d'une fenêtre future.

Les JAR sélectionnés restent byte-identiques aux artefacts R1. Le receiver est propre au
commit exact INT-001 ; le diff des sources applicatives, POM et wrappers Local Lab depuis
le commit du JAR est vide. Rapports qualifiés et proposition ADR acceptée rehashés.

Le [manifeste R2](J9-WO046-J7-REAL-LOCAL-E2E-R2-MANIFEST-20260905.md) est gelé :

```text
MANIFEST_COMMIT=642e0e5ac42cdd476969e40cd3b5137d7d56b01d
MANIFEST_SIZE_BYTES=15737
MANIFEST_SHA256=ba21dfd8ac9cfa6284eab686b3206c5676736ac035c9b38096fb656376baef65
MANIFEST_KEYS=155
MANIFEST_FROZEN=YES
MANIFEST_UTF8_NO_BOM_LF_FINAL=PASS
MANIFEST_PLACEHOLDER_OR_DUPLICATE_KEY_COUNT=0
```

Le contrôle final à `2026-09-05T08:51:58Z` confirme le receiver PostgreSQL arrêté avec code
0, restart `no`, volume conservé, primaire running/healthy et zéro listener 8087/8444/5433.
Aucun conteneur Testcontainers. Manifeste R1 et rapport d'arrêt R1 inchangés.

Les scripts de contrôle ad hoc restent ignorés, sans être promus en outillage runtime :

| Script de recontrôle | SHA-256 des octets exécutés |
|---|---|
| `Recheck-Wo046R2Receiver.ps1` | `3fa7e4502e56d269b35d0f397916ecdf4007b3e2bd260d425034f62423d1f3c0` |
| `Recheck-Wo046R2Metadata.ps1` | `4df0a444664140d90591a7943fae8cf8218ec6e5367cd28d5ecabd421723c893` |

Les vérifications de ce lot sont les transactions en lecture seule, profils publics PKI,
hashes, parseur PowerShell, inventaires exacts, UTF-8, absence de placeholder/clé dupliquée
et `git diff --check`. Aucun build ou test d'intégration supplémentaire n'est exécuté :
aucun code applicatif n'est modifié et les JAR qualifiés sont volontairement conservés.
Le build borné de clôture WO-049 reste la référence, sans nouveau PASS Maven revendiqué.

```text
CURRENT_WO046_STATUS=R2_MANIFEST_FROZEN_PENDING_REAL_POST_OWNER_DECISION
CURRENT_CANONICAL_OWNER_GO_CREATED=NO
CURRENT_OWNER_GO_REGISTERED=NO
CURRENT_REAL_POST_AUTHORIZED=NO
JAVA_APPLICATION_STARTS_THIS_RECHECK=0
EXACT_RECEIVER_POSTGRES_TEMPORARY_STARTS=1
EXACT_RECEIVER_POSTGRES_STOPS=1
READ_ONLY_SQL_TRANSACTIONS=2
BUSINESS_SQL_WRITES=0
PROVIDER_CALLS=0
RECEIVER_HTTP_CALLS=0
PURGE_OPERATIONS=0
PUSH_OR_MAIN_MERGE_PERFORMED=NO
```
