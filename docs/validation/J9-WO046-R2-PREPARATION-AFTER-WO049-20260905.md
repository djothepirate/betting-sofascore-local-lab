# WO-046 R2 — Reprise préparatoire après validation WO-049

- Consignation : `2026-09-05T08:33:26Z`, `10:33:26+02:00` Europe/Paris.
- Base de reprise : `516a955de1033ddfe546b934fc7e3b2490ed486c`.
- Branche : `codex/j9-wo046-j7-real-local-e2e-campaign`.
- Nature : compte rendu préparatoire, **pas un manifeste ni un owner-go**.

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
