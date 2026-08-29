# WO-SS-20260827-015 - Migration Playwright des donnees fournisseur J5

- **Statut :** `VALIDATED`
- **Jalon :** `J5`
- **Date d'ouverture :** `2026-08-27`
- **Date de demarrage :** `2026-08-28`
- **Date de validation et de cloture :** `2026-08-29`
- **Decision d'ouverture :** proprietaire du Betting Project
- **Branche cible :** `codex/j5-playwright-event-data`
- **Worktree dedie :** `CREATED_codex/j5-playwright-event-data`
- **ADR applicable :** `ADR-SS-001 v1.4`
- **Qualification de reference :** `WO-SS-20260823-011` (`VALIDATED`)
- **Socle Playwright requis :** `WO-SS-20260827-013` (`VALIDATED`)
- **Implementation par cette ouverture :** `COMPLETE`
- **Restauration des adaptations POC proprietaire :** `COMPLETED_TARGET_MUTATION_NOT_REQUIRED`
- **Developpement et qualification technique :** `AUTHORIZED_COMPLETED`
- **Qualification fonctionnelle fournisseur :** `PASS_BY_OWNER_EXECUTION_2026_08_29`
- **Appel fournisseur supplementaire :** `NOT_AUTHORIZED`
- **Decision de cloture :** `AUTHORIZED_BY_OWNER_2026_08_29`
- **Polling, scheduler, retry ou fallback :** `NOT_AUTHORIZED`
- **Production, VPS ou dependance critique :** `NOT_AUTHORIZED`

## 1. Objectif

Remplacer le transport fournisseur manuel J5 des familles `EVENT_STATISTICS`, `EVENT_INCIDENTS` et
`EVENT_LINEUPS` par le runtime Playwright commun livre par `WO-SS-20260827-013`.

Le perimetre fonctionnel reste la campagne J5 en ligne existante : une identite canonique, trois
GET sequentiels au plus, un seul contexte neuf et un arret au premier incident hors `404`. Ce Work
Order ne transforme pas la campagne fournisseur en collecte multi-match.

Le lot d'import local multi-match livre par `WO-SS-20260822-010` reste strictement hors ligne et
inchangé. Il ne lance ni Playwright, ni cache fournisseur, ni coordinateur reseau.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Restauration differee des adaptations POC proprietaire

Le proprietaire a confirme que ses adaptations POC destinees a evaluer FlareSolverr seront
annulees au moment de l'execution de ce Work Order. Cette autorisation est enregistree maintenant,
mais aucune restauration n'est realisee lors de la simple ouverture du document.

```text
OWNER_POC_RESTORATION_AT_WO_015_START=COMPLETED
OWNER_POC_FILES_MODIFIED_BY_WO_OPENING=NO
TARGET_POC_MUTATION=NOT_REQUIRED
WHOLE_FILE_RESET_OR_CHECKOUT=FORBIDDEN
UNRELATED_USER_CHANGES_PRESERVED=YES
```

Le worktree cible a ete cree depuis la branche J4 Playwright approuvee, puis a conserve le commit
append-only de `WO-SS-20260827-012`. Les ajouts POC FlareSolverr n'etaient pas presents dans cette
base : ils n'ont donc pas ete recrees pour etre retires. `pom.xml` et `SofascoreProperties.java`
conservent le socle Playwright officiel ; l'ancien transport J5 direct est retire uniquement par
l'implementation WO-015.

Les fichiers POC actuellement identifies sont :

```text
pom.xml
src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/transport/ProviderJ5EventDataRestTransport.java
src/main/java/com/bettingproject/sofascorelocal/config/SofascoreProperties.java
```

Au demarrage effectif de `WO-SS-20260827-015`, un premier lot de prerequis distinct doit :

1. enregistrer en lecture seule l'etat Git source, le diff complet, l'origine des modifications et
   le SHA-256 de chaque fichier concerne avant toute mutation ;
2. creer le worktree dedie `codex/j5-playwright-event-data` depuis la base propre approuvee, puis
   verifier qu'aucune edition Eclipse concurrente ne le vise ;
3. produire un manifeste authentifie des seuls hunks POC proprietaire. Le patch de preuve n'est
   jamais applique aveuglement au worktree cible ; il sert a reconnaitre exactement les hunks a
   retirer si la base approuvee les contient ;
4. si aucun hunk POC n'existe dans le worktree cible propre, enregistrer
   `TARGET_POC_MUTATION=NOT_REQUIRED` au lieu de le recreer pour pouvoir l'annuler ;
5. s'ils sont presents dans la base cible, retirer de `pom.xml` uniquement les ajouts historiques
   propres au wrapper FlareSolverr, tout en
   preservant les dependances et profils Playwright officiels issus de `WO-SS-20260823-011` et de
   `WO-SS-20260827-013` ;
6. si le POC est present dans la base cible, restaurer dans
   `ProviderJ5EventDataRestTransport.java` le transport GET direct de reference
   comme etape intermediaire auditable, en supprimant uniquement l'appel a
   `http://localhost:8191/v1`, l'enveloppe `request.get`, la reconstruction HTML/JSON et les sorties
   `System.err` propres au POC ;
7. si le POC est present dans la base cible, restaurer dans `SofascoreProperties.java` la valeur de
   reference de `connectTimeout` sans
   toucher aux autres evolutions de configuration ;
8. preserver integralement le parseur incidents V14 et `WO-SS-20260827-012`, l'import local
   multi-match de `WO-SS-20260822-010`, la qualification Playwright de `WO-SS-20260823-011` et
   toute autre modification utilisateur sans rapport ;
9. verifier `mvnw.cmd clean verify` sur cette base restauree avant tout code Playwright ;
10. enregistrer dans le worktree dedie la restauration effective dans un commit de prerequis relie
    au present Work Order ; lorsque l'audit conclut `TARGET_POC_MUTATION=NOT_REQUIRED`, consigner
    cette preuve dans le diff WO-015 sans creer de commit vide ;
11. annuler ensuite dans le worktree source ces seuls hunks POC, s'ils y sont encore presents, en
    retablissant le code de reference avec le manifeste comme allowlist et sans toucher aux autres
    modifications utilisateur. Ce nettoyage du worktree source est une etape locale separee de
    `WO-SS-20260827-015`, explicitement autorisee par le proprietaire ; il n'est jamais melange au
    commit du worktree cible et n'est execute sous aucune edition Eclipse concurrente.

Une restauration par `git reset --hard`, `git checkout -- <fichier>` ou remplacement aveugle d'un
fichier entier est interdite. Si un hunk melange POC et travail valide, il est reconstruit
manuellement avec `apply_patch`, puis relu et teste.

## 3. Prerequis bloquants

Les prerequis suivants ont autorise le demarrage de l'implementation. Ils restent des invariants
du diff final :

1. `WO-SS-20260827-013` est integre et son runtime commun est qualifie en loopback ;
2. un worktree dedie est cree depuis la base approuvee sur `codex/j5-playwright-event-data` ;
3. la restauration controlee de la section 2 est terminee ; l'audit a conclu
   `TARGET_POC_MUTATION=NOT_REQUIRED`, donc aucun commit de prerequis vide n'etait requis ;
4. `WO-SS-20260815-005`, `WO-SS-20260815-006`, `WO-SS-20260822-010` et
   `WO-SS-20260827-012` restent les contrats J5 de reference ;
5. `mvnw.cmd clean verify` est vert sur la base restauree ;
6. aucun fichier POC FlareSolverr ou source `src/j5-browser-qualification*` n'est copie dans le
   runtime applicatif ;
7. aucune edition simultanee du meme worktree n'est effectuee depuis Eclipse et par un agent.

L'implementation et la qualification technique locale n'autorisent ni la preparation ni
l'execution d'une campagne fournisseur. Le cache Chromium commun ne peut etre installe que par
l'action locale explicite deja definie par WO-013.

## 4. Allowlist exacte et ordre

Pour un `EVENT_ID` entier de `1` a `999999999` deja lie a l'UUID local affiche et confirme :

```text
1. Famille : EVENT_STATISTICS
   Methode : GET
   Origine : https://www.sofascore.com
   Chemin  : /api/v1/event/{EVENT_ID}/statistics

2. Famille : EVENT_INCIDENTS
   Methode : GET
   Origine : https://www.sofascore.com
   Chemin  : /api/v1/event/{EVENT_ID}/incidents

3. Famille : EVENT_LINEUPS
   Methode : GET
   Origine : https://www.sofascore.com
   Chemin  : /api/v1/event/{EVENT_ID}/lineups
```

L'ordre `STATISTICS -> INCIDENTS -> LINEUPS` est obligatoire. Un nom d'equipe, un UUID local ou un
identifiant saisi librement ne participe jamais a l'URI. Tout port explicite, user-info, query,
fragment, autre schema, autre hote, autre methode, redirection ou route secondaire est refuse.

Une campagne reste bornee a cette identite et a ces trois familles. Le bouton de confirmation
n'autorise jamais une quatrieme requete, un retry ou un nouveau contexte apres incident.

## 5. Reutilisation du runtime commun

J5 reutilise sans fork ni copie le runtime de `WO-SS-20260827-013` :

- worker JVM enfant proprietaire de Playwright, Chromium et du contexte ;
- lancement paresseux apres preparation et confirmation valides, au premier endpoint J5 ;
- un `BrowserContext` neuf et non persistant pour les trois familles de la campagne ;
- protocole IPC prive v5, attribuable, borne a 5 Mio et ne journalisant aucun payload ;
- lease exclusive de campagne partagee avec J3 et J4 ;
- arret cible par PID et heure de creation, jamais par nom de processus ;
- aucun profil Chrome personnel, cookie injecte, `storageState`, HAR, trace, video, capture ou
  telechargement ;
- aucun proxy, rotation d'adresse, furtivite, challenge, CAPTCHA ou fallback HTTP/FlareSolverr ;
- octets issus de `Response.body()` sans DOM, `Response.text()`, reconstruction ou reencodage ;
- fermeture du contexte, du navigateur, du worker et de Playwright sur chaque chemin terminal.

Le cache fournisseur J5 est `NOT_APPLICABLE` : une campagne J5 confirmee n'effectue aucune lecture
ni ecriture de cache et ne peut pas eviter, remplacer ou rejouer une famille par une valeur locale.
Les trois commandes voyagent dans le meme worker, le meme `BrowserContext` et la meme lease, dans
l'ordre strict `STATISTICS -> INCIDENTS -> LINEUPS`.

La limite de 5 Mio est appliquee a l'IPC et a la persistance. L'implementation doit tester un corps
surdimensionne sans exposer l'application a une allocation non bornee ; si l'API Playwright impose
la materialisation avant controle, le worker isole et sa limite memoire doivent echouer ferme.

## 6. Semantique des reponses

- le statut, le type utile, la taille et le SHA-256 sont controles avant parsing ;
- le brut reste separe du modele normalise et conserve sa provenance ;
- un `2xx` utilise strictement le parseur existant de la famille ;
- un `404` est conserve et normalise `ENDPOINT_UNAVAILABLE`, sans parsing ni retry, puis la
  campagne poursuit la famille suivante ;
- un `404` n'est ni une panne de transport, ni une liste vide, ni un motif de nouveau contexte ;
- tout autre non-`2xx`, timeout, HTML/challenge, redirection, route inattendue, corps trop grand ou
  contenu sensible arrete la campagne au premier incident ;
- aucun fallback vers `RestClient`, FlareSolverr, un import local ou une seconde session ;
- chaque resultat conserve snapshot, hash, parseur, heure de reception, completude et observation.

Les parseurs et schemas metier existants, notamment `event-incidents-v14`, ne changent pas dans ce
Work Order. Une evolution de parseur exige un lot distinct.

## 7. Coordination et arret

La campagne acquiert la lease commune avant de creer le contexte et la conserve jusqu'a la
fermeture de l'arbre Playwright. Aucune campagne J3, J4 ou seconde campagne J5 ne peut s'intercaler
entre deux familles.

L'action d'arret J5 existante doit signaler le superviseur de la campagne en vol avant de
reverrouiller le controle metier. Elle ne peut pas se limiter a appeler le service de controle.

```text
STOP_ACKNOWLEDGEMENT_MAX=500ms
IN_FLIGHT_CANCELLATION_MAX=2s
PROCESS_TREE_CLEANUP_MAX=5s
RESIDUAL_OWNED_PROCESS_COUNT=0
```

Apres arret, aucune famille suivante n'est appelee et aucun contexte de remplacement n'est cree.

## 8. Lots d'implementation

1. **Restauration POC** : `COMPLETED_TARGET_MUTATION_NOT_REQUIRED` - base cible exempte des hunks
   FlareSolverr proprietaires.
2. **Integration runtime** : `COMPLETED` - ports, lease, worker et superviseur communs reutilises
   sans duplication.
3. **Adaptateur J5** : `COMPLETED` - transport Playwright borne aux trois familles, sans fallback.
4. **Campagne et controle** : `COMPLETED` - meme contexte, ordre strict et arret Web raccorde.
5. **Qualification loopback** : `COMPLETED` - statuts, octets, `404`, incidents et nettoyage verts.
6. **Regression hors ligne** : `COMPLETED` - imports unitaires et multi-match sans navigateur.
7. **Documentation et readiness** : `COMPLETED` - architecture, runbook, rapport, README,
   changelog et Work Order alignes.
8. **Qualification fournisseur** : `COMPLETED_BY_OWNER_2026_08_29` - campagne bornee et
   non-regressions J4/J3 validees ; aucun appel supplementaire autorise.

## 9. Matrice de validation minimale

| Scenario | Resultat attendu |
|---|---|
| base restauree avant migration | `clean verify` vert et aucune trace runtime FlareSolverr |
| demarrage normal / profil absent | aucun worker, Chromium ou telechargement |
| profil opt-in mais aucune confirmation | runtime inerte, zero GET |
| preparation seule ou claim invalide | zero contexte et zero GET |
| cache fournisseur J5 | `NOT_APPLICABLE`, aucune lecture ou ecriture de cache |
| campagne nominale loopback | trois GET ordonnes, un contexte, octets/statuts exacts |
| seconde campagne | nouveau worker/contexte, aucun etat reutilise |
| `404` statistiques ou incidents | indisponibilite conservee puis famille suivante |
| `404` compositions | indisponibilite conservee puis fermeture complete |
| `403`, `429` ou `5xx` | arret terminal, aucune famille suivante, zero retry |
| redirection ou HTML/challenge | cible secondaire jamais atteinte, aucune normalisation |
| timeout, corps > 5 Mio ou canari sensible | fermeture sure et aucune reprise |
| arret pendant la deuxieme famille | bornes 500 ms / 2 s / 5 s, zero troisieme GET et zero residu |
| tentative J3/J4 concurrente | aucune intercalation dans la campagne J5 |
| import local J5 unitaire | zero Playwright et zero appel fournisseur |
| import local multi-match 1 puis 25 | comportement WO-010 inchange, zero Playwright |
| parseur incidents V14 | regression complete sans changement de version ou de semantique |

Commandes requises avant proposition de cloture :

```text
mvnw.cmd clean verify
mvnw.cmd -Pintegration-tests verify
scripts/Verify-Local.ps1 -WithIntegrationTests
pwsh -NoProfile -File .\scripts\Invoke-J5PlaywrightLoopbackQualification.ps1
git diff --check
```

Les tests standards et d'integration PostgreSQL ne lancent jamais Chromium et n'accedent jamais a
SofaScore.

## 10. Qualification fonctionnelle fournisseur et decision proprietaire

La readiness loopback a execute zero appel fournisseur. Le `2026-08-29`, le proprietaire a ensuite
utilise le lanceur PowerShell 7 canonique et l'interface locale pour une campagne J5 bornee a
**Lille - Paris Saint-Germain**, identite canonique
`c40066c9-987b-38d9-b415-869a453d2ad6` et identifiant fournisseur `16310930`. La campagne a atteint
`COMPLETED_LOCKED` apres exactement trois appels ordonnes et zero import local :

| Famille | Snapshot | Taille | SHA-256 source | Parseur | Completude | Observation |
|---|---:|---:|---|---|---|---:|
| `EVENT_STATISTICS` | `603` | `25 745` octets | `8945c99bdd2d191737cfc6c82e24d2a94f9493a0165f71f540ae458e115b634b` | `event-statistics-v2` | `COMPLETE - 100 %` | `298` |
| `EVENT_INCIDENTS` | `604` | `55 115` octets | `8b1a1bfca25f259b486a555ad91d62e774725aa3e83d4f04c378053d7f5cf81c` | `event-incidents-v14` | `COMPLETE - 100 %` | `299` |
| `EVENT_LINEUPS` | `605` | `78 828` octets | `4d0fd84ccbe24dff0291efdadc33d742c3ea5a82b753dbd8a57f214734f0e82f` | `event-lineups-v2` | `COMPLETE - 100 %` | `300` |

Les vues normalisees des statistiques, incidents et compositions sont restees consultables. Dans
la meme session, J4 phase 2 a atteint `COMPLETED_LOCKED` apres un appel `EVENT_DETAILS` et conserve
le snapshot `606`, sans regression fonctionnelle identifiee.

La premiere reprise J3 a termine 18 pages fournisseur contigues, sans cache ni import local, mais
la reconstruction du catalogue a revele deux candidats dont les metadonnees d'affichage
fournisseur contenaient une tabulation ou un saut de ligne. Le constructeur d'option les refusait
alors qu'une exception englobante classait tout le catalogue `SNAPSHOT_PARSE_INCOMPATIBLE`.

Le correctif conserve les snapshots et libelles bruts inchanges, n'invente aucune valeur et exclut
de la projection les deux occurrences distinctes deja parsees dont le nom de phase porte une
tabulation ou le nom du tournoi unique un saut de ligne. Le test de regression associe une option
sure a ces deux formes exactes et confirme un catalogue `AVAILABLE`, les pages sources conservees
et deux exclusions non resolvables. Les valeurs blanches, categories invalides et conflits restent
soumis aux invariants stricts existants.

Le proprietaire a ensuite valide le correctif dans une nouvelle collecte J3 explicite : 18 pages,
catalogue `AVAILABLE`, `1 428` tournois actionnables, `338` occurrences exclues et liste deroulante
visible. La selection Ligue 1 a conserve le snapshot `643`, verifie `5 / 5` rencontres canoniques
et termine `COMPLETED` apres un appel fournisseur. Cette reprise confirme que J3 reste utilisable
avec le transport Playwright apres WO-015.

La decision proprietaire accepte ces preuves et clot le Work Order. Elle n'autorise aucun replay :
toute nouvelle campagne J3, J4 ou J5 exige une nouvelle preparation, une nouvelle confirmation et
un go proprietaire explicite. Les huit cles locales documentees ont ete reverrouillees, puis un
redemarrage de controle a confirme J4 et J5 `LOCKED` sur `127.0.0.1:8087`. L'application a ensuite
ete arretee ; le listener et les JVM applicative/worker attribuees au worktree sont absents.

## 11. Hors perimetre

- campagne fournisseur multi-match ou automatisation de la collecte par journee ;
- modification de l'import local multi-match, de ses declarations `404` ou de son atomicite ;
- ajout d'un endpoint, changement d'un parseur ou nouvelle migration de donnees ;
- polling, scheduler, live, retry ou appel au demarrage ;
- proxy, VPN de contournement, rotation d'adresse, challenge ou furtivite ;
- fallback HTTP direct, FlareSolverr ou second contexte apres incident ;
- execution sur le VPS ou integration au Betting Project principal ;
- appel fournisseur sans autorisation proprietaire distincte.

## 12. Fichiers probables

- `pom.xml`, conserve sur le profil Playwright commun sans ajout FlareSolverr ;
- adaptateur Playwright J5 derriere les trois ports existants ;
- `ProviderJ5EventDataRestTransport.java`, retire du runtime apres equivalence ciblee ;
- services et controles de campagne J5 ;
- `SofascoreProperties.java` et configuration opt-in ;
- raccordement au superviseur, a la lease et a l'IPC de `WO-SS-20260827-013` ;
- tests unitaires, integration, loopback et regressions import multi-match/V14 ;
- architecture J5, runbook, README, changelog et rapport de readiness.

Aucune migration WO-015 n'est ajoutee. La migration append-only
`V26__j5_live_extra_time_period.sql`, prerequis de `WO-SS-20260827-012`, reste intacte. Toute
evolution ulterieure de provenance ou de persistance exige une migration Flyway append-only
separee.

## 13. Etat d'implementation au 2026-08-28

Le transport J5 direct a ete remplace par `ProviderJ5EventDataPlaywrightTransport`. Une campagne
possede un seul superviseur et envoie les trois familles au meme worker/contexte sous la meme
lease. Le protocole worker est passe a v5 pour porter les trois routes J5 fermees. Le `404` est
retourne a la couche raw-first et laisse la campagne poursuivre ; tout autre incident reste
terminal. L'arret J5 signale la campagne exacte, attend l'acquittement et interdit la famille
suivante avant de nettoyer l'arbre attribue. Il n'existe aucun fallback `RestClient`, FlareSolverr,
import local ou second contexte.

La fermeture est fail-closed : une demande de fermeture bloque immediatement toute nouvelle
commande, mais la meme campagne peut retenter un nettoyage echoue avant toute mutation. La lease
partagee n'est liberee qu'apres succes. Une erreur apres mutation rend le nettoyage terminal et la
conserve jusqu'au redemarrage du processus pour interdire tout chevauchement J3/J4 avec un worker
potentiellement orphelin. L'arret exact reste routable apres un resultat terminal sans reecrire
l'issue historique. L'arret global active aussi un latch de processus distinct : il conserve l'etat
et le code terminaux historiques, y compris apres un succes, mais refuse toute nouvelle preparation
J5 jusqu'au redemarrage.

Les preuves techniques acquises pendant l'implementation sont bornees :

```text
J5_PLAYWRIGHT_TRANSPORT_TARGETED_TESTS=PASS_20_TESTS
J5_CLEANUP_AND_LEASE_TARGETED_SUITE=PASS_78_TESTS
J5_SHARED_LEASE_EXCLUSIVITY=PASS_J3_J4_BLOCKED_UNTIL_CONFIRMED_J5_CLOSE
J5_CLEANUP_FAILURE_LEASE_RETENTION=PASS_2_TESTS_TRANSIENT_AND_PERSISTENT
J5_EXACT_TERMINAL_STOP_ROUTING=PASS_HISTORICAL_TERMINAL_RESULT_PRESERVED
J5_LAUNCHER_CLEAN_BUILD=PASS_LEGACY_REST_BYTECODE_ABSENT
J5_MAVEN_REPORT_SECRET_SCAN=PASS_XML_DECODED_ATTRIBUTES_AND_PROPERTIES
J5_GLOBAL_STOP_LATCH=PASS_TERMINAL_HISTORY_PRESERVED_REARM_BLOCKED_UNTIL_RESTART
J5_RUNTIME_FILE_CANARY_SCAN=PASS_ISOLATED_WRITABLE_ROOTS_PER_RUN_RANDOM_CANARY
J5_PLAYWRIGHT_LOOPBACK_IT=PASS_14_TESTS
J5_LOOPBACK_ROUTE_SEQUENCE=PASS_200_404_200_ONE_WORKER_CONTEXT
J5_LOOPBACK_STOP_DURING_INCIDENTS=PASS_ZERO_LINEUPS_ZERO_RESIDUE
STANDARD_CLEAN_VERIFY=PASS_765_TESTS_0_FAILURE_0_ERROR_2_SKIPPED
POSTGRESQL_INTEGRATION_VERIFY=PASS_53_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
VERIFY_LOCAL_WITH_INTEGRATION_TESTS=PASS
J5_LOOPBACK_CONSOLIDATED_SCRIPT=PASS_11_PROTOCOL_SECURITY_TESTS_14_CHROMIUM_TESTS
TECHNICAL_QUALIFICATION_PROVIDER_ACCESS=NO
```

Les commandes finales de la section 9 sont vertes sur le diff complet. Elles ne valent ni
qualification humaine fournisseur, ni autorisation de campagne reelle, ni cloture du Work Order.

## 14. Portes de statut

```text
WORK_ORDER_STATUS=VALIDATED
IMPLEMENTATION_STARTED=YES
IMPLEMENTATION_COMPLETED=YES
TECHNICAL_READINESS=PASS
ADDITIONAL_PROVIDER_CALL_AUTHORIZED=NO
SHARED_RUNTIME_DEPENDENCY=WO-SS-20260827-013
SHARED_RUNTIME_DEPENDENCY_STATUS=VALIDATED
OWNER_POC_RESTORATION=COMPLETED_TARGET_MUTATION_NOT_REQUIRED
OWNER_POC_FILES_TOUCHED_DURING_OPENING=NO
ONLINE_MULTI_MATCH_SCOPE=EXCLUDED
OFFLINE_MULTI_MATCH_REGRESSION=REQUIRED
J5_PROVIDER_CACHE=NOT_APPLICABLE
WORKER_PROTOCOL_VERSION=5
FLYWAY_BASELINE=V26_WO_SS_20260827_012_PRESERVED
WO_015_MIGRATION=NONE
LOCAL_READINESS=PASS
TECHNICAL_QUALIFICATION_PROVIDER_ACCESS=NO
HUMAN_PROVIDER_QUALIFICATION=PASS_BY_OWNER_EXECUTION_2026_08_29
J5_FUNCTIONAL_QUALIFICATION=PASS
J5_REAL_EVENT_ID=16310930
J5_REAL_CANONICAL_EVENT_ID=c40066c9-987b-38d9-b415-869a453d2ad6
J5_REAL_TERMINAL_STATE=COMPLETED_LOCKED
J5_REAL_PROVIDER_CALLS=3
J5_PROVIDER_SCHEMA_VALIDATED=YES_CURRENT_PARSERS
J5_REAL_STATISTICS_QUALIFICATION=PASS
J5_REAL_INCIDENTS_QUALIFICATION=PASS
J5_REAL_LINEUPS_QUALIFICATION=PASS
J4_SAME_SESSION_REGRESSION=PASS_COMPLETED_LOCKED_SNAPSHOT_606
J3_CORRECTIVE_RETEST=PASS_AVAILABLE_18_PAGES_1428_OPTIONS_338_EXCLUDED
J3_TOURNAMENT_DISCOVERY_RETEST=PASS_COMPLETED_SNAPSHOT_643_5_OF_5
LOCAL_CONFIGURATION_RELOCKED=YES_8_KEYS
J4_LOCKED_AFTER_RESTART=PASS
J5_LOCKED_AFTER_RESTART=PASS
FINAL_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_OWNED_JAVA_PROCESS_COUNT=0
CLOSURE=AUTHORIZED_BY_OWNER_2026_08_29
```
