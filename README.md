# SofaScore Local Lab

Laboratoire Java local et contrôlé destiné à évaluer, depuis Windows, l’intérêt de données SofaScore comme enrichissement **facultatif** du Betting Project.

> **Statut :** `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`

Le dépôt matérialise les jalons validés **J0 — Gouvernance**, **J1 — Bootstrap**, **J2 — Fixtures**, **J3 — Appel manuel**, **J4 — Événements** et **J5 — Statistiques**. L'implémentation J4, son parcours hors ligne et ses deux sous-étapes réelles bornées sont qualifiés humainement. La sous-étape 1 a validé `16386245` et `16421052` après correction du retour par date. La sous-étape 2 a validé la saisie d'identifiants, le rappel manuel avec une nouvelle confirmation, la déduplication d'une réponse inchangée et la création d'une observation append-only lorsque `16412917` est passé de `notstarted` à `inprogress`. Après l'arrêt global, la configuration a été remise à l'état bloqué, ce verrouillage a été vérifié après redémarrage et l'application a été arrêtée gracieusement. La Pull Request `#8` a été fusionnée et le Work Order J4 est archivé `VALIDATED`. Les voies fournisseur restent désactivées par défaut ; J3 demeure exclusif, tandis qu'une configuration bornée peut désormais réunir J4 phase 2 et J5 dans une même session. Aucun appel fournisseur n’est exécuté par Maven, conformément au document de cadrage `Betting_Project_SofaScore_Local_Lab_Cadrage_v0.1.0.pdf` et à l’ADR `ADR-SS-001`.

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

## Ce qui est livré localement

- dépôt Git autonome, documentation, ADR, règles agent et Work Orders ;
- Java **25 LTS**, Spring Boot **4.1.0** et Maven Wrapper versionné ;
- interface Spring MVC + Thymeleaf sur `127.0.0.1:8087` ;
- PostgreSQL local dans Docker Desktop, migrations Flyway V1 à V20 et stockage brut séparé ;
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
- voie J5 réelle opt-in, désactivée par défaut et exclusive de J3 ; utilisable seule ou avec J4
  phase 2, limitée à l'origine exacte `https://www.sofascore.com` et à l'ensemble exact des chemins
  actifs d'une identité canonique déjà persistée ;
- coordinateur commun J4/J5 sérialisant les transports et appliquant le délai minimal entre leurs
  départs, y compris lors du passage de `EVENT_DETAILS` à `EVENT_STATISTICS` ;
- préparation J5 sans réseau, confirmation exacte de cinq minutes, acquittement, trois appels
  séquentiels au maximum et délai minimal de trois secondes ; après succès, l'ancien claim reste
  non rejouable mais une nouvelle campagne explicite peut être préparée dans la même instance ;
- parseurs fournisseur `event-statistics-v2`, `event-incidents-v13` et `event-lineups-v2`, brut
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

## Limite essentielle du bootstrap

**Aucun appel SofaScore réel n’est actif par défaut et aucun n’est exécuté par les tests.** Le
connecteur général, `ConnectorGate`, le catalogue `callable=false` et le profil Maven
`sofascore-live-test` restent bloqués. J3 pour `SCHEDULED_EVENTS` demeure exclusif. J4 pour
`EVENT_DETAILS` et J5 pour `EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS` restent des
voies distinctes, mais J4 phase 2 et J5 peuvent être armés ensemble avec l'union exacte des quatre
familles. Dans J4, `SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED` sélectionne exclusivement le
formulaire paramétrable et est obligatoire pour ce mode combiné.
Dans J5, l'action finale réutilise l'identité canonique affichée et autorise au maximum trois
transports ordonnés après une confirmation humaine unique. Toutes ces voies interdisent polling,
planification et retry. Leurs configurations temporaires et leur remise à l'état bloqué sont
décrites dans `docs/runbooks/RUNBOOK-LOCAL.md`.

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

## Modèle de données J1 à J5

La migration `V1__bootstrap_schema.sql` crée :

- `provider_snapshot` : métadonnées de transport, emplacement normalisé JSONB, hash, parseur et statut de schéma ;
- `export_manifest` : manifeste des futurs exports normalisés ;
- `connector_control` : état opérateur persistant, initialisé à `network_enabled=false` et `circuit_state=LOCKED`.

La migration append-only `V2__raw_manual_call_snapshots.sql` ajoute à `provider_snapshot` les octets exacts dans `payload_raw` (`bytea`), leur taille et le mode de provenance obligatoire `DIRECT_LOCAL_ENDPOINT`. Le brut reste distinct de `payload_jsonb`, qui n’est pas alimenté par cette unité. La taille est limitée à 5 Mio et une même combinaison fournisseur, endpoint logique, clé de requête et SHA-256 est dédupliquée.

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

Le mode `DIRECT_LOCAL_ENDPOINT` ne doit jamais être confondu avec une `VisualObservation` du projet
global. La persistance n'effectue elle-même aucun appel : les écritures J5 réelles éventuelles sont
initiées uniquement par la voie humaine gardée, puis référencent le brut séparé avec
`PROVIDER_SNAPSHOT`.

## Politique réseau J1

Le bootstrap cumule plusieurs barrières :

1. `sofascore.enabled=false` par défaut ;
2. aucune base URL par défaut ;
3. aucune origine fournisseur dans la configuration par défaut ;
4. toutes les définitions restent `callable=false`, sauf `SCHEDULED_EVENTS` dans le seul mode de
   qualification J3 explicitement armé ;
5. `ConnectorGate` refuse systématiquement les appels ;
6. le profil `sofascore-live-test` échoue volontairement ;
7. l’application n’écoute que sur une adresse de boucle locale ;
8. les paramètres imposent concurrence `1`, délai minimal `3s`, rafraîchissement et live désactivés.

Après la clôture de J3, aucune de ces barrières ne peut être retirée sans un nouveau Work Order,
une décision de gouvernance explicite et une qualification humaine dédiée.

## Documentation de référence

- [ADR-SS-001](ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md)
- [Architecture J0/J1](docs/architecture/ARCHITECTURE.md)
- [Contrat hors ligne scheduled-events-v1](docs/architecture/SCHEDULED-EVENTS-V1.md)
- [Contrat hors ligne event-details-v1](docs/architecture/EVENT-DETAILS-V1.md)
- [Architecture des événements canoniques J4](docs/architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md)
- [Architecture J5 hors ligne et contrôles de complétude](docs/architecture/J5-OFFLINE-EVENT-DATA-AND-COMPLETENESS.md)
- [Architecture de la qualification réelle gardée J5](docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md)
- [Règles métier des incidents de football J5](docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md)
- [Voie réelle bornée J4 — sous-étape 1](docs/architecture/J4-GUARDED-REAL-EVENT-DETAILS-PHASE1.md)
- [Politique réseau J3 hors ligne](docs/architecture/J3-OFFLINE-NETWORK-POLICY.md)
- [Persistance des snapshots bruts J3](docs/architecture/J3-RAW-SNAPSHOT-PERSISTENCE.md)
- [Transport scheduled-events J3 protégé et simulé](docs/architecture/J3-GUARDED-SCHEDULED-EVENTS-TRANSPORT.md)
- [Confirmation explicite d’appel manuel J3](docs/architecture/J3-EXPLICIT-MANUAL-CALL-CONFIRMATION.md)
- [Politiques d’arrêt et d’incident J3](docs/architecture/J3-TRANSPORT-STOP-AND-INCIDENT-POLICIES.md)
- [Chemin fournisseur J3 borné à cinq pages](docs/architecture/J3-FIVE-PAGE-PROVIDER-QUALIFICATION.md)
- [Reprise fournisseur J3 contrôlée à la page 2](docs/architecture/J3-PAGE-TWO-PROVIDER-RESUME.md)
- [Adaptation hors ligne au schéma qualifié de la page 2](docs/architecture/J3-PAGE-TWO-SCHEMA-ADAPTATION.md)
- [Reprise fournisseur J3 contrôlée à la page 3](docs/architecture/J3-PAGE-THREE-PROVIDER-RESUME.md)
- [Collecte manuelle J3 répétable à pagination dynamique](docs/architecture/J3-DYNAMIC-MANUAL-PAGINATION.md)
- [Inspection JSON locale des snapshots bruts J3](docs/architecture/J3-LOCAL-RAW-SNAPSHOT-JSON-INSPECTION.md)
- [Runbook local](docs/runbooks/RUNBOOK-LOCAL.md)
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
- [Qualification hors ligne de la session combinée J4 phase 2 + J5](docs/validation/J4-J5-COMBINED-QUALIFICATION-SESSION-20260818.md)
- [Work Order validé de qualification réelle J5](docs/work_orders/completed/WO-SS-20260815-006-j5-real-event-data-qualification.md)

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

## J5 réel : V13 qualifié et Work Order validé

J5 réutilise l'identité synthétique `900001` de J4 pour démontrer les trois familles demandées. Les
neuf fixtures J5 sont explicitement synthétiques et ne valident aucun schéma fournisseur. La page
locale distingue une rupture structurelle, une famille partielle, une liste vide valide et une
famille fournisseur indisponible. Les chemins techniques manquants restent conservés dans le
rapport de complétude, mais ne sont plus affichés au-dessus des tableaux d'incidents et de
compositions ; les badges, compteurs, données et lignes des tableaux restent inchangés.

```text
J5_IMPLEMENTATION_STATUS=VALIDATED
J5_HUMAN_OFFLINE_QUALIFICATION=PASS
J5_PROVIDER_SCHEMA_VALIDATED=YES
J5_PROVIDER_SCHEMA_VALIDATION_SCOPE=V13_EVENTS_16691018_AND_16851672
J5_SYNTHETIC_FIXTURES_PROVIDER_SCHEMA_VALIDATED=NO
J5_APPLICATION_TRANSPORT=IMPLEMENTED_GUARDED_DEFAULT_OFF
J5_DISCOVERY_ATTEMPTS=1
J5_DISCOVERY_RESULT=HTTP_403_STOPPED_NO_RETRY
J5_FIXTURE_ORIGIN=SYNTHETIC
J5_FLYWAY_VERSION=20
J5_MAVEN_PROVIDER_CALLS=0
J5_REAL_TECHNICAL_READINESS=PASS
J5_REAL_FIRST_CAMPAIGN=HTTP_404_MISCLASSIFIED_AND_LOCKED
J5_REAL_FIRST_CAMPAIGN_PROVIDER_CALLS=1
J5_REAL_FIRST_STATISTICS_SNAPSHOT=30
J5_REAL_LAST_SUCCESSFUL_CAMPAIGN_PROVIDER_CALLS=3
J5_REAL_LATEST_FAILED_CAMPAIGN_PROVIDER_CALLS=2
J5_REAL_STATISTICS_LATEST=V2_COMPLETE_SNAPSHOT_194_OBSERVATION_102_256_OF_256
J5_REAL_STATISTICS_UNAVAILABLE_RETEST=SNAPSHOT_118_CONTINUED_TO_INCIDENTS_AND_LINEUPS
J5_REAL_INCIDENTS_LAST_PASS=V13_COMPLETE_SNAPSHOT_195_OBSERVATION_103_81_OF_81
J5_REAL_INCIDENTS_LATEST=V13_COMPLETE_SNAPSHOT_195_EVENT_16851672
J5_REAL_INCIDENTS_CURRENT_PARSER=event-incidents-v13
J5_REAL_SUBSTITUTION_PLAYERS=PASS_REAL_RENDERED
J5_REAL_LAST_SUCCESSFUL_TERMINAL=COMPLETED_LOCKED
J5_REAL_LINEUPS=V2_COMPLETE_SNAPSHOT_196_OBSERVATION_104_97_OF_97
J5_HTTP_404_POLICY=ENDPOINT_UNAVAILABLE_CONTINUE_NO_RETRY
J5_CORRECTIVE_V3_RETEST=PASS_REAL
J5_CORRECTIVE_V4_RETEST=PASS_REAL_THREE_CALLS
J5_COMPLETED_CAMPAIGN_REARM=PASS_REAL_SECOND_CAMPAIGN_STARTED
J5_REAL_V5_FIRST_HALF_CAMPAIGN=PASS_SNAPSHOTS_61_62_63
J5_REAL_V5_FULL_TIME_REPLAY=PASS_SNAPSHOTS_64_65_66
J5_REAL_BENCH_CARD_CORRECTIVE_PARSER=event-incidents-v5
J5_REAL_BENCH_CARD_CORRECTION=PASS_REAL
J5_INCIDENT_RULES_SOURCE=docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md
J5_INCIDENT_CURRENT_PARSER=event-incidents-v13
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
La campagne V13 `16691018` a depuis requalifié la séance non minutée et atteint les compositions
après trois appels ; la campagne `16851672` a qualifié séparément le motif exact `Leaving field` et
les trois familles. Le statut fournisseur courant est donc `YES` dans cette portée bornée. Le lot
final de neuf captures confirme que les listes de chemins techniques ont disparu sans altérer les
tableaux, que `Leaving field` reste visible, que la configuration locale a été reverrouillée et que
J4/J5 sont `LOCKED` après redémarrage. Le fichier `.env` demeure ignoré et n'est ni lu ni modifié
par l'agent. Aucun listener n'est présent sur `127.0.0.1:8087` après l'arrêt final ; le Work Order
réel J5 est `VALIDATED` et archivé dans `completed`.
