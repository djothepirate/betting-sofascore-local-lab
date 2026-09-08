# Runbook J6 — Historique, sauvegarde, restauration et rétention

## 1. Objet et autorisations

Ce runbook couvre deux opérations distinctes :

1. qualifier l'historique J6 avec le corpus synthétique, sans réseau ;
2. préparer puis, seulement après autorisation explicite, purger les octets bruts éligibles.

La consultation, l'import synthétique, l'aperçu de rétention et la sauvegarde/restauration ne
suppriment aucune donnée de la base primaire. Le mode `Execute` de la rétention supprime des octets
et doit faire l'objet d'une décision opérateur distincte. Il n'existe ni bouton Web, ni tâche
planifiée, ni purge automatique.

ADR-SS-003 v0.2 supersède la règle v0.1 uniquement pour la porte J7 locale ; le format V1 et V31
restent immuables, tandis que V2/V32 portent la séparation audit/gouvernance. Sous WO-047,
l'interdiction propriétaire est plus stricte : sauvegarde/restauration native,
`pg_dump`, `pg_restore`, pipeline natif, migration ou contact de la base primaire et purge primaire
ne sont pas autorisés. Les procédures natives ci-dessous restent une référence historique pour un
futur Work Order explicitement autorisé ; elles ne doivent pas être lancées pour qualifier
WO-047. Seuls les contrôles statiques ciblés des scripts et les migrations Testcontainers sur un
PostgreSQL isolé relèvent de son périmètre.

WO-058 ajoute le ledger des campagnes live et le garde fournisseur commun en V33, puis le plafond paramétrable et la cadence immuable en V34. V35 applique la contrainte de cadence aux nouveaux manifestes `live-v3` avec LINEUPS prématch, sans modifier les lignes historiques ni la portée de purge. V36 autorise le parseur incidents V16 pour le penalty accordé. V37 ajoute le motif de carton exact `Professional handball` via V17, sans réécrire les observations précédentes. V38 ajoute les trois champs J4 optionnels d'attribution et de score affiché, en conservant les hashes et lignes historiques. Les références
V31/V32 ci-dessus décrivent les décisions et preuves historiques J7 ; elles ne désignent plus le
schéma courant exigé par les scripts J6. La qualification WO-058 utilise des bases PostgreSQL
éphémères et des données synthétiques. Elle n'autorise ni sauvegarde ni purge de la base primaire.

V39 ajoute la politique groupée immuable, les groupes de requêtes et les échéances par famille.
V40 ajoute les contraintes propres à `live-v5` : cadence de 100 secondes, pause d'une seconde entre
groupes de cette politique, plafonds de 2 500 appels par rencontre et 20 000 par campagne, budget
brut indépendant de 15 728 640 000 octets maximum. Aucune ligne ni empreinte historique n'est
réécrite ; `live-v4` conserve ses paramètres. Les gardes courants exigent V40 ; les preuves V39 et
antérieures restent historiques. Le format du manifeste et
l'algorithme `normalizedProvenanceSha256` restent inchangés : cette empreinte porte sur la
provenance et les hashes normalisés, sans comparer directement chaque colonne métier.
Le test PostgreSQL de roundtrip vérifie séparément la conservation matérielle des trois
nouvelles valeurs J4 (`is_awarded=true`, `home_display_score=0`, `away_display_score=3`),
avec leurs identifiants et leur provenance, après restauration dans une base de test distincte.
Le fingerprint live couvre également les quatre tables groupées introduites en V39 ; les cas de
roundtrip du ledger restaurent `live-v4` sous V39 et `live-v5` sous V40 avec leur profil, groupes,
projection et révisions des échéances, sans réarmer la collecte. La montée V39 préremplie vers V40
compare aussi matériellement les manifestes, appels, observations et anciennes empreintes. Le
format des champs du manifeste J6 reste inchangé ; une preuve V39 ne qualifie pas les nouvelles
bornes ni la restauration d'une campagne `live-v5`.

```text
PROVIDER_CALL_REQUIRED=NO
POLLING_OR_SCHEDULING=NO
PRIMARY_DATABASE_PURGE_DEFAULT=NOT_AUTHORIZED
NORMALIZED_OBSERVATION_DELETION=IMPOSSIBLE_BY_DESIGN
```

## 2. Prérequis

- Windows 11, Java 25 et Docker Desktop ;
- PowerShell 7.4 ou plus récent pour préserver les pipelines binaires natifs ;
- exécutable `age` disponible dans `PATH` ou fourni avec `-AgePath` ;
- PostgreSQL local démarré et sain ;
- Flyway V40 appliqué ; la rétention reste définie par V22, V23 étend seulement
  `export_manifest` pour J7, V24 élargit la portée du cache de découverte tournoi, V25 ajoute
  uniquement la provenance de l'import JSON local, V26 autorise `event-incidents-v14`, V27 ajoute
  le ledger J8 sans étendre le périmètre de purge et V28 autorise uniquement
  `event-incidents-v15`, sans table ni réécriture, et V29 ajoute le ledger de livraison J7
  metadata-only dont les trois compteurs et l'empreinte sont inclus dans la preuve de
  sauvegarde/restauration sans étendre le périmètre de purge ; V30 remplace uniquement le trigger
  de résultat J7 afin de ne plus ordonner l’horloge receiver contre l’horloge locale ; V31 ajoute
  les preuves append-only de grant, révocation et consommation du go propriétaire provider-derived,
  dont les trois comptes et les lignes canoniques participent à la même empreinte J7 ; V32 ajoute
  de façon append-only le discriminant V1/V2 et les champs d'audit/gouvernance V2, sans modifier
  V31 ni la fonction canonique V1. L'empreinte `to_jsonb(owner_go)` couvre donc les colonnes V32.
  V33 ajoute les preuves live et le garde fournisseur commun ; elle n'étend pas les données
  supprimables et ne réécrit aucune preuve V32. V34 ajoute la cadence au manifeste live ; son empreinte complète est couverte par le même to_jsonb, sans étendre la purge ;
- application liée uniquement à `127.0.0.1` ;
- toutes les voies J3/J4/J5, y compris la découverte tournoi, désactivées et
  `connector_control` à `LOCKED` ;
- `SOFASCORE_LIVE_ENABLED=false`, `SOFASCORE_PLAYWRIGHT_ENABLED=false`, garde
  `provider_campaign_guard.state=FREE` et aucune campagne live `RUNNING` ou `CLEANUP_REQUIRED` ;
- destination de sauvegarde absolue, existante et située hors du dépôt ;
- espace disque suffisant pour la sauvegarde chiffrée et la restauration temporaire.

Ne jamais placer la sauvegarde, son manifeste, une phrase secrète ou un dump dans Git. Le script
refuse un chemin de sauvegarde situé dans le dépôt et refuse d'écraser un fichier existant.

## 3. Qualification humaine de l'historique hors ligne

### 3.1 Démarrer localement

Depuis la racine du dépôt :

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\Start-Local.ps1

.\mvnw.cmd -Dspring-boot.run.profiles=local spring-boot:run
```

Ouvrir `http://127.0.0.1:8087`. Vérifier avant l'import :

- l'adresse locale exacte ;
- les contrôles réseau désactivés et verrouillés ;
- le panneau J6 de rétention en lecture seule ;
- l'absence de bouton de purge.

### 3.2 Importer la démonstration

Dans l'interface, utiliser **Charger la démonstration J6**. L'action est un POST protégé par le
jeton de formulaire local ; elle ne contacte aucun fournisseur.

Contrôler ensuite :

1. la présence des cinq flux ;
2. le filtre de flux et la pagination ;
3. la version `BASELINE` de chaque flux ;
4. l'état terminal `finished` ;
5. les versions `SYNTHETIC_CHANGE` placées après cet état terminal, sans les confondre avec des
   réponses fournisseur ;
6. la comparaison de deux versions consécutives ;
7. la comparaison arbitraire de deux versions du même flux ;
8. les valeurs ajoutées, retirées et modifiées ;
9. les informations de provenance, parseur et complétude ;
10. l'absence de JSON brut ou de téléchargement.

Les classifications `LATE_ENRICHMENT` et `LATE_CORRECTION` sont réservées aux versions de provenance
fournisseur. Leur détection, y compris lors d'une comparaison qui saute un état terminal
intermédiaire, est couverte par les tests automatisés et ne doit pas être simulée par un faux
payload fournisseur dans l'interface hors ligne.

Relancer une seconde fois l'import et vérifier qu'aucune version métier supplémentaire n'apparaît.
Arrêter ensuite proprement l'application avec `Ctrl+C` avant toute opération de sauvegarde ou de
rétention.

## 4. Aperçu de rétention sans mutation

L'application Web doit être arrêtée. Le script force les opt-ins réseau à `false`, démarre une
commande Spring non Web, vérifie le verrou persistant puis s'arrête.
Cela inclut les opt-ins live et Playwright. L'aperçu refuse aussi un garde fournisseur occupé ou
un nettoyage incertain avec `PROVIDER_CAMPAIGN_ACTIVE` ; aucune purge ne doit servir à libérer ce
garde.

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6Retention.ps1 -Mode Preview
```

Sortie attendue :

```text
J6_RETENTION_MODE=PREVIEW
J6_RETENTION_DAYS=30
J6_RETENTION_CUTOFF_AT=<instant UTC>
J6_RETENTION_TOTAL_ELIGIBLE=<N>
J6_RETENTION_SELECTED=<0 à 500>
J6_RETENTION_PLAN_SHA256=<64 caractères hexadécimaux>
J6_RETENTION_CONFIRMATION=PURGER <N> PAYLOADS J6 <PLAN_SHA256>
J6_RETENTION_MUTATION=NO
```

Si `J6_RETENTION_SELECTED=0`, ne pas poursuivre : aucun payload n'est éligible. Si
`J6_RETENTION_TRUNCATED=true`, un seul lot de 500 au maximum sera exécutable. Un lot ultérieur exige
au minimum un nouvel aperçu et une nouvelle confirmation ; la preuve de sauvegarde ne peut être
réutilisée que si sa couverture qualifiée englobe encore tous les candidats concernés.

Ne jamais recomposer manuellement le cutoff ou le SHA-256. Les quatre valeurs utilisées en mode
`Execute` doivent provenir du même aperçu final.

## 5. Créer et qualifier la sauvegarde chiffrée

Choisir un nouveau nom absolu hors dépôt. Le répertoire parent doit déjà exister. Exemple :

```powershell
pwsh -NoProfile -File .\scripts\Backup-Restore-J6.ps1 `
  -Destination 'D:\SofaScoreBackups\sofascore-j6-20260818.age'
```

Si `age` n'est pas dans `PATH` :

```powershell
pwsh -NoProfile -File .\scripts\Backup-Restore-J6.ps1 `
  -Destination 'D:\SofaScoreBackups\sofascore-j6-20260818.age' `
  -AgePath 'C:\Tools\age\age.exe'
```

`age` demande interactivement la phrase secrète pendant le chiffrement puis le déchiffrement. Ne
pas passer cette phrase sur la ligne de commande, ne pas la coller dans un rapport et ne pas la
conserver dans `.env`. La commande doit être lancée depuis un terminal interactif déjà attaché : le
script transmet directement ce terminal à `age` et ne crée aucune nouvelle console. Après une
modification des variables d'environnement utilisateur, redémarrer entièrement l'application ou le
terminal qui lance la commande, ou fournir explicitement les valeurs non secrètes requises dans la
commande ; un onglet enfant ne peut pas récupérer rétroactivement l'environnement de son parent.

Les deux bornes de nettoyage par défaut sont distinctes :

```text
PIPELINE_NATIVE_CLEANUP_DEFAULT_MS=5000
POSTGRES_OWNED_SESSION_CLEANUP_DEFAULT_MS=10000
```

La première borne couvre l'arrêt et la preuve de nettoyage des processus natifs possédés. La
seconde couvre l'observation, la terminaison exacte éventuelle et la stabilisation de la session
PostgreSQL possédée. Elles ne constituent ni une boucle indéfinie ni un budget interchangeable.

Le script :

1. refuse une application encore à l'écoute sur le port 8087 ;
2. vérifie Compose, le verrou réseau, la version courante Flyway V40, le garde fournisseur `FREE`
   et l'absence de campagne live `RUNNING` ou `CLEANUP_REQUIRED` ;
3. vérifie le SHA-256 réel de chaque payload retenu ;
4. vérifie l'exécutable Docker exact ; sous Windows, il doit être un fichier absolu sans reparse
   point, signé validement et attribué au produit Docker Inc. ;
5. démarre chaque côté derrière une porte, confine d'abord son hôte dans un Job Object Windows
   `KILL_ON_JOB_CLOSE`, puis acquiert sur un named pipe `CurrentUserOnly` un handshake cible lié par
   nonce avec l'identité exacte `PID + StartTime` avant de mesurer le budget d'exécution ;
6. dirige les handles binaires natifs de `pg_dump --format=custom` directement vers `age -p`,
   observe séparément les sorties, applique une échéance bornée et annule le pair ainsi que ses
   descendants au premier échec, timeout ou interruption ;
7. n'accepte la preuve d'absence native qu'après corroboration bornée du Job Object, de l'identité
   .NET exacte, de Toolhelp, CIM et `tasklist` ; aucune vue d'observation n'autorise une terminaison
   par PID seul ;
8. identifie les sessions `pg_dump` et `pg_restore` par un `PGAPPNAME` unique conforme exactement à
   `^j6_(backup|restore)_[a-f0-9]{32}$`, exécute `psql` avec `ON_ERROR_STOP=1`, distingue les cibles
   des signaux de terminaison acceptés et n'accepte le nettoyage qu'après trois observations
   fraîches à zéro ;
9. conserve uniquement des classifications et causes internes sanitées ; stderr brut, phrase
   secrète, `.env`, chaîne de connexion et payload ne doivent pas être restitués ;
10. conserve archive et manifeste sous des noms `.partial-*` jusqu'à la qualification complète ;
11. crée un manifeste initial non qualifié ;
12. restaure le flux déchiffré directement dans une base temporaire ;
13. compare version Flyway, comptes, couverture et empreintes source/restauration, y compris les
   cinq comptes et le fingerprint déterministe du ledger J8, les trois comptes du ledger J7 et les
   trois comptes des preuves owner-go append-only intégrées à son empreinte, puis les sept
   comptes live, l'état du garde, le nombre de campagnes actives et l'empreinte live ;
14. inscrit `restoreQualified=true` uniquement après égalité et nettoyage prouvé ;
15. supprime la base temporaire et tout fichier partiel dans tous les cas ; une preuve de nettoyage
    incomplète rend l'exécution terminalement invalide.

Une réussite se termine par :

```text
J6_BACKUP_RESULT=QUALIFIED
J6_BACKUP_CIPHER_SHA256=<hash>
J6_BACKUP_MANIFEST_SHA256=<hash>
J6_BACKUP_COVERAGE_MAX_SNAPSHOT_ID=<id>
J6_BACKUP_COVERAGE_RECEIVED_AT=<instant UTC>
```

Deux fichiers restent dans le répertoire externe : le fichier `.age` et
`<fichier>.age.manifest.json`. Conserver les deux ensemble. Si la qualification échoue, ils ne
constituent pas une preuve valide et le mode `Execute` doit rester interdit.

### 5.1 Preuve live V33 incluse dans le manifeste

Les blocs `source` et `restored` doivent être identiques pour les champs suivants, même lorsqu'il
n'existe encore aucune campagne live :

| Champ | Contenu couvert |
|---|---|
| `liveCampaignCount` | lignes de `live_campaign` |
| `liveEventCount` | lignes de `live_event` |
| `liveCallCount` | réservations de `live_call` |
| `liveDispatchCount` | autorisations de départ de `live_call_dispatch` |
| `liveReceiptCount` | réceptions liées dans `live_call_receipt` |
| `liveResultCount` | résultats de `live_call_result` |
| `liveTransitionCount` | transitions de `live_transition` |
| `providerGuardState` | état `FREE` de la ligne unique `provider_campaign_guard` |
| `activeLiveCount` | nombre nul de campagnes `RUNNING` ou `CLEANUP_REQUIRED` |
| `liveLedgerSha256` | SHA-256 déterministe des sept tables live et du garde, soit huit tables |

L'empreinte utilise toutes les colonnes de chaque ligne sous forme `to_jsonb`, avec un préfixe
identifiant la table et un tri déterministe avant calcul du SHA-256. Elle couvre donc le manifeste
préparé, les cibles et leur provenance, les bornes, compteurs, générations et états, les tentatives,
les liens snapshot/occurrence, les résultats, les projections métier et les transitions. Les
octets bruts restent couverts séparément par les preuves de snapshots ; ils ne sont pas copiés
dans le ledger live. Aucun payload ni contenu complet du ledger n'est imprimé par le script.

Un manifeste historique V32 reste une preuve de sa qualification historique. Il ne satisfait pas
la porte de rétention courante V40, car il ne démontre pas la restauration de ces tables et politiques.
Restaurer les preuves live ne déclenche aucun worker ni reprise de campagne : les opt-ins restent
désactivés et tout nouveau lancement exige une action opérateur. Un garde `OWNED` ou
`CLEANUP_REQUIRED` ne peut pas être considéré libre du seul fait d'un redémarrage ou d'un délai.

Pour qualifier le superviseur lui-même en développement, sans phrase réelle et sans fournisseur :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6BackupRestoreLoopbackQualification.ps1
pwsh -NoProfile -File .\scripts\Invoke-J6BackupRestoreLoopbackQualification.ps1 -WithDocker
```

Le premier parcours est entièrement synthétique. Le second utilise uniquement PostgreSQL local et
un faux `age` sans secret. Ce double applique une transformation XOR réversible : il teste le
transport binaire, les sorties, l'annulation et le nettoyage, pas la cryptographie, le dialogue TTY
ou l'auto-génération native d'une phrase. Il peut créer transitoirement, dans le répertoire temporaire
du test, une représentation réversible du dump local ; le `finally` doit la supprimer et une
qualification favorable exige son absence finale. Ne jamais utiliser ce double avec des données
dont ce risque local n'est pas acceptable. Aucune saisie humaine volontairement incorrecte n'est
requise pour ces contre-épreuves.

Le harness couvre les parcours PostgreSQL nominal, session déjà absente, disparition naturelle,
persistance ou terminaison refusée, timeout, erreur SQL et parsing invalide. Il vérifie aussi :

- la distinction entre observation fraîche et compte antérieur à une terminaison ;
- l'idempotence d'une preuve exacte déjà stabilisée ;
- la classification d'un état Windows multi-API divergent comme
  `AMBIGUOUS_CROSS_API_GHOST_VISIBILITY`, avec réobservation bornée mais aucune terminaison par PID
  seul ;
- la création d'un temp root synthétique enfant direct du temp système avec nom canonique et
  marqueur de propriété lié au chemin et à un nonce ;
- le refus du parent temporaire, des globs, des chemins extérieurs et des reparse points ;
- la suppression du seul temp root exactement possédé et la preuve de son absence finale.

Un ancien processus ou répertoire dont l'appartenance n'est pas démontrée doit rester non attribué
et intact. Le harness ne prouve pas la propreté globale de Windows ou du répertoire temporaire ; il
prouve uniquement l'absence des ressources exactes qu'il possède.

## 6. Refaire l'aperçu final

Après la sauvegarde qualifiée et sans redémarrer l'application Web :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6Retention.ps1 -Mode Preview
```

Copier exactement depuis cette sortie :

- `J6_RETENTION_CUTOFF_AT` ;
- `J6_RETENTION_PLAN_SHA256` ;
- `J6_RETENTION_CONFIRMATION` ;
- le nombre sélectionné.

Le cutoff fait partie du hash. Il est donc normal qu'un aperçu relancé produise un autre hash,
même si la liste de candidats semble identique.

## 7. Porte d'autorisation avant exécution

Avant toute purge de la base locale primaire, réunir explicitement :

```text
APPLICATION_STOPPED=YES
CONNECTOR_CONTROL=LOCKED
ALL_NETWORK_OPT_INS=FALSE
PROVIDER_CAMPAIGN_GUARD=FREE
ACTIVE_LIVE_CAMPAIGNS=0
BACKUP_ENCRYPTED=YES
BACKUP_RESTORE_QUALIFIED=YES
BACKUP_AND_MANIFEST_STORED_OUTSIDE_REPOSITORY=YES
FINAL_PREVIEW_REVIEWED=YES
PRIMARY_PURGE_AUTHORIZED_BY_OPERATOR=YES
```

Sans ces confirmations, s'arrêter. Une sauvegarde qualifiée n'autorise pas à elle seule la
purge.

## 8. Exécuter un lot autorisé

Remplacer les quatre valeurs par celles du manifeste et du même aperçu final :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6Retention.ps1 `
  -Mode Execute `
  -BackupManifest 'D:\SofaScoreBackups\sofascore-j6-20260818.age.manifest.json' `
  -CutoffAt '<J6_RETENTION_CUTOFF_AT>' `
  -PlanSha256 '<J6_RETENTION_PLAN_SHA256>' `
  -ConfirmationPhrase 'PURGER <N> PAYLOADS J6 <J6_RETENTION_PLAN_SHA256>'
```

Le script revérifie le nom du fichier chiffré, son hash, Flyway V40, l'égalité complète des preuves
source/restauration, les six compteurs et l'empreinte metadata-only du ledger J7 — incluant grant,
révocation et consommation owner-go — puis les sept compteurs live, le garde libre, l'absence de
campagne active et `liveLedgerSha256`, ainsi que la couverture. Le service recalcule ensuite le
plan, la phrase et la couverture avant que l'adaptateur ne les revérifie sous verrou transactionnel.
La purge primaire reste limitée aux octets bruts J6 : elle ne supprime ni livraison, ni tentative,
ni résultat J7, ni preuve owner-go, ni preuve live, ni référence snapshot/occurrence ou normalisée.
L'adaptateur verrouille également le garde fournisseur pendant sa courte transaction de purge ;
une acquisition concurrente attend sa fin. Le trigger V33 revérifie cette exclusion pour toute
suppression d'octets, en complément de l'audit V22.

Une réussite affiche uniquement un bilan minimisé :

```text
J6_RETENTION_RESULT=PURGED
J6_RETENTION_BATCH_ID=<UUID>
J6_RETENTION_PLAN_SHA256=<hash>
J6_RETENTION_PURGED=<N>
J6_RETENTION_PURGED_BYTES=<octets>
J6_RETENTION_EXECUTED_AT=<instant UTC>
```

Le script n'affiche ni payload, ni mot de passe, ni phrase secrète de sauvegarde.

## 9. Contrôles après exécution

Relancer l'aperçu. Les éléments du lot précédent ne doivent plus être candidats :

```powershell
pwsh -NoProfile -File .\scripts\Invoke-J6Retention.ps1 -Mode Preview
```

Redémarrer ensuite l'application seulement pour un contrôle en lecture :

- le tableau de bord indique les nouveaux compteurs ;
- les historiques et comparaisons normalisés restent disponibles ;
- les traces concernées affichent `PAYLOAD_PURGED` ;
- l'inspection JSON brute de ces snapshots n'est plus disponible ;
- aucune ligne historique n'a disparu ;
- les contrôles réseau restent verrouillés.

Les résultats live déjà publiés restent lisibles avec leur provenance après purge. Si une future
acquisition live reçoit exactement les octets d'un snapshot dont le brut a été purgé, la
déduplication retrouve cette ancienne preuve privée de ses octets. Le stockage refuse alors la
nouvelle réception avec `LIVE_RAW_PREVIOUSLY_PURGED` : la nouvelle occurrence et le lien live sont
annulés dans la même transaction, aucune publication normalisée de cette réception n'est faite,
et la campagne doit s'arrêter. L'ancien audit et les observations restent intacts. WO-058 ne
réhydrate pas un brut purgé ; une évolution de cette règle relève de la gouvernance J6.

Arrêter proprement l'application après vérification.

## 10. Refus et incidents attendus

| Résultat | Signification | Action |
|---|---|---|
| `EMPTY_PLAN` | aucun candidat dans le plan | ne rien purger |
| `STALE_PLAN` | cutoff, candidats ou hash ont changé | refaire un aperçu et réévaluer |
| `INVALID_CONFIRMATION` | phrase différente | ne pas corriger approximativement ; recopier l'aperçu |
| `BACKUP_NOT_QUALIFIED` | restauration absente ou date invalide | refaire sauvegarde et restauration |
| `BACKUP_COVERAGE_INSUFFICIENT` | preuve antérieure à un candidat | créer une nouvelle sauvegarde |
| `PROVIDER_CAMPAIGN_ACTIVE` | garde fournisseur occupé, nettoyage incertain ou campagne live active | arrêter les campagnes et établir le nettoyage exact ; ne pas forcer le garde à `FREE` |
| `LIVE_RAW_PREVIOUSLY_PURGED` | une nouvelle réception live déduplique vers un ancien brut purgé | conserver les preuves et arrêter la campagne ; ne pas réhydrater ou effacer l'audit J6 |
| `DATABASE_MUTATION_MISMATCH` | nombre de lignes inattendu | transaction annulée ; diagnostiquer avant tout nouvel essai |
| `LOCAL_EXECUTION_FAILURE` | configuration, manifeste ou base invalide | conserver les preuves et diagnostiquer localement |
| échec ou timeout d'un côté de la pipeline native | producteur ou consommateur invalide | arrêter le pair et son Job Object ; ne publier aucun fichier ni résultat qualifié |
| `CLEANUP_UNCONFIRMED` ou nettoyage de session non stabilisé | absence de résidu non démontrée | arrêter ; ne pas relancer avant diagnostic ciblé et audit des ressources possédées |
| session PostgreSQL possédée encore visible | snapshot ou restauration potentiellement actifs | ne pas tuer largement par nom ; cibler seulement le `PGAPPNAME` exact de l'exécution et prouver trois observations à zéro |
| `POSTGRES_SESSION_REMAINING` | une observation fraîche postérieure à la dernière terminaison voit encore la session exacte | arrêter ; auditer l'identité exacte et ne jamais terminer par nom large |
| `POSTGRES_OBSERVATION_TIMEOUT` | la borne expire sans preuve fraîche suffisante | arrêter ; ne pas convertir un ancien compte en preuve de présence ou d'absence |
| `POSTGRES_SCALAR_OUTPUT_INVALID` | sortie `psql` vide, bruitée, multiligne, non canonique ou incohérente | arrêter ; diagnostiquer la commande et ne pas interpréter approximativement la sortie |
| `POSTGRES_DOCKER_COMMAND_FAILED`, `POSTGRES_DOCKER_COMMAND_NONZERO_EXIT` ou `POSTGRES_SQL_COMMAND_NONZERO_EXIT` | Docker, le processus natif ou SQL a échoué | arrêter ; conserver seulement la classification sanitée et diagnostiquer localement |
| `POSTGRES_DOCKER_PROCESS_CLEANUP_UNCONFIRMED` | l'absence de l'arbre de commande Docker n'est pas prouvée | arrêter et auditer l'identité possédée ; aucune reprise tant que le nettoyage reste invérifiable |
| `AMBIGUOUS_CROSS_API_GHOST_VISIBILITY` | .NET ne peut pas ouvrir l'identité mais une vue secondaire reste visible | arrêter la qualification et auditer ; ne pas tuer par PID seul |
| `TEMP_ROOT_CLEANUP_FAILED` ou propriété/confinement du temp root non confirmés | le seul répertoire synthétique exact ne peut pas être validé ou supprimé sûrement | ne supprimer que le chemin exact possédé ; ne jamais supprimer récursivement le parent temporaire |

Ne jamais contourner un refus par une modification SQL manuelle, une désactivation de trigger ou
une édition du manifeste. Une restauration de secours doit être effectuée vers une base distincte
avant toute décision de remplacement de la base primaire.

## 11. Clôture de qualification J6

Pour passer le Work Order à `VALIDATED`, consigner au minimum :

- résultat des tests Maven standard et intégration ;
- contrôle humain des cinq flux et des différences ;
- idempotence de l'import synthétique ;
- sauvegarde chiffrée restaurée et qualifiée ;
- contrôle des états réseau verrouillés ;
- absence de secret et de payload dans Git et les journaux ;
- si une purge primaire a été explicitement autorisée, identifiant du lot et compte minimisé ;
- arrêt final de l'application.

Les fichiers `.age`, manifestes locaux et captures d'écran restent hors Git.

Cette liste décrit une qualification J6 complète lorsqu'elle est autorisée. Elle ne peut pas être
utilisée pour revendiquer une sauvegarde/restauration native sous WO-047 : ce Work Order exclut
explicitement ces opérations et ne qualifie que les gardes V32 par tests statiques ciblés.
