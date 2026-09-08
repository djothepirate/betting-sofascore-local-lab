# WO-058 — Clôture locale après interruption de l’application

Date : 2026-09-08. Base : `73cba517bb6e81d7e6054192027594fa27238ae6`.
Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Constat et périmètre

Le propriétaire valide les compositions graphiques J5, puis signale un blocage après avoir
arrêté Eclipse pour libérer le port 8087 sans utiliser préalablement l’arrêt de campagne.
La campagne `35cae8c0-80e2-48c3-90bd-cd7240157efe` est affichée
`INTERRUPTED / OWNER_PROCESS_ABSENT`, avec 140 appels réservés. Un nouveau lancement est
refusé avec `LIVE_PROVIDER_CLEANUP_REQUIRED`, mais l’interface ne proposait que la clôture
runtime, qui exige le lease en mémoire du processus disparu.

Une lecture seule de PostgreSQL confirme le garde `CLEANUP_REQUIRED`, génération 40,
appartenant à cette campagne et au processus 53980 créé le 08/09 à 13:51:25.069 UTC.
L’application redémarrée utilisait le processus 65920 ; le propriétaire a confirmé son arrêt
et la libération de 8087 pour les tests. Le garde opérateur n’a pas été libéré par SQL ou par
les tests. Ses données restent disponibles pour essayer le bouton après chargement du correctif.

## Parcours livré

La campagne qui détient le garde propose **« Clôturer la session interrompue »**. Le refus
de nouveau lancement mène directement à cette campagne. La commande est un POST protégé
par jeton local à usage unique, avec l’identité de campagne et la génération affichée.
Un refus de clôture permet de revenir à la campagne pour relire l’état et réessayer explicitement.

La clôture prend l’exclusion locale du coordinateur, refuse toute session live ou manuelle
en cours, puis vérifie l’absence de l’ancien propriétaire et des composants Playwright.
Elle ne crée ni lease, ni contexte navigateur, ni requête fournisseur. Après cette preuve,
la transaction PostgreSQL verrouille le garde avant la campagne et compare tous les champs
du garde : état, campagne, instance, PID, début de processus, génération et date de changement.
Elle exige des états terminaux, aucune tentative sans résultat, aucune échéance future
et aucune autre campagne active. Toute incohérence empêche la libération.

La transition append-only `LOCAL_CLEANUP_VERIFIED` porte l’empreinte du garde vérifié. Cette
trace et le passage du garde à `FREE` sont validés ensemble. Un échec SQL annule les deux ;
une répétition après réponse perdue accepte uniquement la libération prouvée de la même
génération. Une acquisition ultérieure ne peut pas être annulée par un ancien formulaire.
La campagne conserve son état `INTERRUPTED`, ses appels, observations, compteurs et manifeste.
Les tables existantes suffisent : aucune migration n’est ajoutée ou modifiée.

Après clôture, **« Préparer une nouvelle campagne avec ces rencontres »** reprend les
identités du manifeste historique et exécute la préparation habituelle. Les exclusions
`finished`/`postponed`, les campagnes déjà actives et la capacité sont réévaluées. Le nouvel
écran exige ensuite sa confirmation de lancement. Ce bouton est aussi disponible pour les
campagnes terminées ou arrêtées ; il ne reprend jamais l’exécution de l’ancien manifeste.
Un passage à `postponed` pendant une campagne conserve l’arrêt existant du suivi concerné.

## Preuve d’absence sous Windows

`LiveOrphanProcessProbe` utilise `ProcessHandle` pour le propriétaire, puis un processus
PowerShell local à script fixe et une lecture CIM des processus Java, Node et Chromium.
Les lignes de commande restent internes à ce processus : seuls les verdicts `ABSENT`,
`ACTIVE` et `UNVERIFIED` sont transmis. L’exécution est bornée à dix secondes, sans navigateur,
worker ou appel externe. En cas de délai dépassé, seul le processus de contrôle créé par
cette commande est arrêté ; aucun processus inspecté n’est tué.

L’inventaire perdu de l’ancien superviseur n’est pas assimilé à une preuve d’absence.
Le lanceur Eclipse passe par `mvnw spring-boot:run` et conserve un JVM Maven parent. La sonde
reconnaît ce seul cas avec une identité PID/date réellement présente parmi les ancêtres de
l’application et une commande désignant le lanceur Maven avec le but `spring-boot:run`.
La classification d’un worker reste prioritaire. Une JVM dont les métadonnées sont lisibles
et la création strictement antérieure à celle de l’ancien propriétaire ne peut pas être un
worker neuf créé par cette application. Elle n’est pas classée comme candidate ambiguë ; une
égalité à la milliseconde ne suffit pas. Tout marqueur Playwright reste bloquant quel que soit
l’âge du processus. Les autres JVM non attribuables conservent le blocage.
Une identité inaccessible, un worker ou descendant potentiel, un JAR configuré
absent, une sortie inattendue ou une plateforme autre que Windows empêche également la clôture.
Le périmètre qualifié est le Lab Windows local ; cette sonde ne promet pas une récupération
universelle de tous les processus ou d’un stockage incohérent.

Le premier passage ciblé a détecté une différence de précision entre Java et CIM : Java
expose le début de processus à la milliseconde, alors que Windows conserve davantage de
précision. Le rapprochement du seul JVM appelant, encore vivant pendant le contrôle, est
effectué à la milliseconde. Le propriétaire historique reste comparé Java à Java avec la
précision persistée. Le test CIM réel qui échouait passe sans modification de son attente,
et une différence d’une milliseconde sur l’identité courante est explicitement refusée.

La première tentative avec le vrai parent Maven a conservé `UNVERIFIED`. Une lecture CIM
minimisée a identifié trois JVM auxiliaires d’Eclipse, toutes antérieures au propriétaire
disparu : deux du 05/09 et une créée le 08/09 à 13:50:14.226 UTC, avant le propriétaire à
13:51:25.069 UTC. Ce diagnostic motive la distinction temporelle ci-dessus ; aucun de ces
processus n’a été arrêté, et aucune commande brute n’a été imprimée.

## Vérification

Windows, Java 25.0.4, Spring Boot 4.1.0, Maven wrapper ; cache local avec
`--offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository`. Les tests de persistance utilisent
PostgreSQL Testcontainers isolé. Aucun test standard ne lance Playwright ou un appel SofaScore.

| Commande / contrôle | Résultat |
|---|---|
| `mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Dtest=LiveCampaignRecoveryTest,LiveCampaignServiceTest,ManualProviderRequestCoordinatorTest,LiveCampaignControllerTest,LiveOrphanProcessProbeTest test` | PASS, 172 tests à 15:01:41 UTC, puis 174 avec la reconnaissance du parent Maven à 15:16:32 UTC ; aucun échec, erreur ou ignoré sur ces deux passages |
| Premier passage de cette sélection | 172 tests, un échec de précision CIM ; corrigé et rejoué ci-dessus. Journal conservé, sans qualification verte de ce premier essai |
| Même wrapper et options de cache, `-Dtest=LiveOrphanProcessProbeTest test` après distinction des JVM antérieures | PASS final, 11 tests, aucun échec, erreur ou ignoré ; fin 15:22:52 UTC |
| Programme local de contrôle sous le vrai `spring-boot:run`, sans contexte applicatif | PASS final, `ABSENT`, contrôle en 1 321 ms, fin 15:23:17 UTC ; première tentative `UNVERIFIED` conservée |
| `mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository clean verify` | PASS, fin 15:08:44 UTC en 6 min 33 s ; 1 790 cas Surefire dont 5 ignorés, 160 Failsafe sans ignoré, aucun échec/erreur. Passage avant le complément de compatibilité du parent Maven |
| `mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository -Pintegration-tests clean verify` | PASS final, fin 15:31:00 UTC en 7 min 17 s ; 196 suites Surefire, 1 793 cas dont 5 ignorés (1 788 exécutés), 7 suites Failsafe et 160 cas sans ignoré, aucun échec/erreur. Le `clean` supplémentaire reconstruit l’ensemble du correctif |

Les tests ciblés couvrent le formulaire rendu, la soumission, le retour GET, la consommation
du jeton, les refus, la nouvelle sélection issue du manifeste, l’ordre preuve/transaction,
les identités périmées, l’exclusion mutuelle et la répétition après succès. Les quatre contrôles
Windows exécutent le classificateur sur des inventaires synthétiques (workers, ancêtres Maven,
JVM antérieures) et le collecteur CIM sur le seul JVM courant.
La suite PostgreSQL complète ces vérifications avec historique v1–v4,
identités exactes, rollback et concurrence mesurée par les verrous PostgreSQL.

La qualification du vrai parent utilise le helper ignoré `.tmp/OrphanCleanupReadOnlyProbe.java`,
compilé temporairement dans `target/classes`, et la commande suivante :

```powershell
.\mvnw.cmd --offline -Dmaven.repo.local=C:/Users/geoff/.m2/repository `
  -Dspring-boot.run.main-class=OrphanCleanupReadOnlyProbe spring-boot:run
```

Ce main appelle uniquement `LiveOrphanProcessProbe.requireAbsent` avec l’identité précédente
et le JAR worker existant du projet humain. Il ne crée pas Spring, ne lit pas PostgreSQL,
n’ouvre aucun port et ne contacte pas le fournisseur. Le `clean` final enlève cette classe
de diagnostic avant le packaging. Cette preuve constate l’absence à cet instant ; la
commande UI refait le contrôle au moment de la clôture, sous exclusion et avant la transaction.

Les preuves de ce complément sont distinctes de la qualification Chromium des compositions
et de la charge live-v4 antérieure. Aucun rythme de collecte, parseur ou composant de rendu
des données sportives n’est modifié ici. La validation opérateur du bouton sur le garde
historique reste à effectuer après relancement d’Eclipse avec le correctif.

Les cinq skips standard sont quatre contrôles de liens symboliques non disponibles dans ce
contexte Windows (`LocalJ7ExportFileStoreTest` et `J8BenchmarkExportCommandTest`, deux chacun),
et la qualification J6 de trois passages Docker qui exige `j6.docker.qualification=true`.
Ce profil de qualification supplémentaire n’est pas activé pour cette clôture ; les tests
PostgreSQL du profil d’intégration ont tous été exécutés, dont 48 dans `LiveCampaignPersistenceIT`.
Aucun test de récupération n’est ignoré. Le JAR final contient la sonde de production et aucune
classe du helper de diagnostic. Le port 8087 est vérifié libre après les suites.

Les 18 fichiers modifiés sont UTF-8 valides et le diff passe `git diff --check`, sans secret
détecté à la recherche de signatures et à la relecture. `server.address=127.0.0.1` et les
opt-ins fournisseur désactivés par défaut restent inchangés. Aucun endpoint fournisseur, migration,
fichier `.env`, ADR ou preuve sportive historique n’est modifié. La revue indépendante
du service, du coordinateur, de la transaction et du parcours UI n’a relevé aucune autre
anomalie actionnable. Les erreurs diagnostiquées de précision et de classement des processus
sont conservées ci-dessous avec leurs contrôles finaux réussis.

Les journaux et rapports XML sont locaux, ignorés par Git. Les métadonnées par suite sont
capturées dans `.tmp/wo058-orphan-cleanup-*-evidence.json` avant chaque `clean` utile.
Empreintes SHA-256 des contrôles et diagnostics :

| Preuve locale | SHA-256 |
|---|---|
| Premier ciblé, échec CIM conservé | `b0a1ba6901d87b980c09f13cd3a49faba71741bcee419975c29a4648836ba432` |
| Ciblé avec parent Maven, 174 cas | `b77ee2f4029d60126243bf1df0b07c51c54a1f932c38509d5a84531aadea9611` |
| Première suite `clean verify`, avant compatibilité du lanceur | `0b438077bdbcd84dcddb17eeb1561a483307b4856eb37283df61e72406f8504e` |
| Sonde finale, 11 cas | `74b87db045010b49a7b9b01e08ecf68e37d691359a6aadce1cf185beb4108045` |
| Premier vrai lanceur, verdict `UNVERIFIED` conservé | `a1772f14ec795088338b6cc8543c4021a3b77cdf49958df08faa0f23f81495d1` |
| Vrai lanceur final, verdict `ABSENT` | `b9bd27bc0c5ec778e58fd5b76a1d2e07a4ea22160607d1adffa1ca88ea9dfb2f` |
| Source du helper local de lecture seule | `c86e921c39a96af4c6c9e0b938feb41e2651e21bdcbbfcd6be73d796dae692f5` |
| Suite finale `-Pintegration-tests clean verify` | `32a2b8d53f42a8551d9f6ec73852b16a01d84431cc1db8ea450ae99ef038ae8c` |

## Fichiers du complément

- Application : `application/live/LiveCampaignService.java`, `application/live/LiveOrphanProcessProbe.java`,
  `application/network/ManualProviderRequestCoordinator.java`.
- Persistance : `port/LiveCampaignStore.java`, `adapter/persistence/live/JdbcLiveCampaignStore.java`.
- Interface : `adapter/web/LiveCampaignController.java`, `templates/live-campaign.html`,
  `templates/live-campaign-error.html`.
- Tests : `LiveCampaignRecoveryTest.java`, `LiveOrphanProcessProbeTest.java`, `ManualProviderRequestCoordinatorTest.java`,
  `LiveCampaignControllerTest.java`, `LiveCampaignPersistenceIT.java`.
- Documentation : ce rapport, `CHANGELOG.md`, le
  [runbook](../runbooks/LIVE-J4-J5-CAMPAIGNS.md), l’[architecture](../architecture/LIVE-J4-J5-CAMPAIGNS.md)
  et le [WO actif](../work_orders/active/WO-SS-20260907-058-bounded-live-j4-j5.md).

Les chemins Java sont relatifs à `src/main/java/com/bettingproject/sofascorelocal/`, les
templates à `src/main/resources/` et les tests au paquet homologue sous `src/test/java/`.
