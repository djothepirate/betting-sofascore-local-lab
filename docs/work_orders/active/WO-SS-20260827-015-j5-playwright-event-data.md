# WO-SS-20260827-015 - Migration Playwright des donnees fournisseur J5

- **Statut :** `OPEN_AWAITING_PREREQUISITES`
- **Jalon :** `J5`
- **Date d'ouverture :** `2026-08-27`
- **Decision d'ouverture :** proprietaire du Betting Project
- **Branche cible :** `codex/j5-playwright-event-data`
- **Worktree dedie :** `REQUIRED_NOT_CREATED`
- **ADR applicable :** `ADR-SS-001 v1.3`
- **Qualification de reference :** `WO-SS-20260823-011` (`VALIDATED`)
- **Socle Playwright requis :** `WO-SS-20260827-013`
- **Implementation par cette ouverture :** `NOT_STARTED`
- **Restauration des adaptations POC proprietaire :** `AUTHORIZED_AT_WO_START_NOT_EXECUTED`
- **Developpement et qualification loopback :** `AWAITING_WO_013_AND_CLEAN_BASE`
- **Appel fournisseur :** `NOT_AUTHORIZED`
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
OWNER_POC_RESTORATION_AT_WO_015_START=AUTHORIZED
OWNER_POC_FILES_MODIFIED_BY_WO_OPENING=NO
WHOLE_FILE_RESET_OR_CHECKOUT=FORBIDDEN
UNRELATED_USER_CHANGES_PRESERVED=YES
```

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
10. enregistrer dans le worktree dedie la restauration effective, ou la preuve
    `TARGET_POC_MUTATION=NOT_REQUIRED`, dans un commit de prerequis relie au present Work Order ;
11. annuler ensuite dans le worktree source ces seuls hunks POC, s'ils y sont encore presents, en
    retablissant le code de reference avec le manifeste comme allowlist et sans toucher aux autres
    modifications utilisateur. Ce nettoyage du worktree source est une etape locale separee de
    `WO-SS-20260827-015`, explicitement autorisee par le proprietaire ; il n'est jamais melange au
    commit du worktree cible et n'est execute sous aucune edition Eclipse concurrente.

Une restauration par `git reset --hard`, `git checkout -- <fichier>` ou remplacement aveugle d'un
fichier entier est interdite. Si un hunk melange POC et travail valide, il est reconstruit
manuellement avec `apply_patch`, puis relu et teste.

## 3. Prerequis bloquants

L'implementation Playwright ne commence que lorsque toutes les conditions suivantes sont reunies :

1. `WO-SS-20260827-013` est integre et son runtime commun est qualifie en loopback ;
2. un worktree dedie est cree depuis la base approuvee sur `codex/j5-playwright-event-data` ;
3. la restauration controlee de la section 2 est terminee et le commit de prerequis est propre ;
4. `WO-SS-20260815-005`, `WO-SS-20260815-006`, `WO-SS-20260822-010` et
   `WO-SS-20260827-012` restent les contrats J5 de reference ;
5. `mvnw.cmd clean verify` est vert sur la base restauree ;
6. aucun fichier POC FlareSolverr ou source `src/j5-browser-qualification*` n'est copie dans le
   runtime applicatif ;
7. aucune edition simultanee du meme worktree n'est effectuee depuis Eclipse et par un agent.

L'ouverture de ce Work Order n'autorise ni le lancement du code, ni l'installation d'un navigateur,
ni la preparation ou l'execution d'une campagne fournisseur.

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
- lancement paresseux apres preparation et confirmation valides, au premier cache miss applicable ;
- un `BrowserContext` neuf et non persistant pour les trois familles de la campagne ;
- protocole IPC prive, attribuable, borne a 5 Mio et ne journalisant aucun payload ;
- lease exclusive de campagne partagee avec J3 et J4 ;
- arret cible par PID et heure de creation, jamais par nom de processus ;
- aucun profil Chrome personnel, cookie injecte, `storageState`, HAR, trace, video, capture ou
  telechargement ;
- aucun proxy, rotation d'adresse, furtivite, challenge, CAPTCHA ou fallback HTTP/FlareSolverr ;
- octets issus de `Response.body()` sans DOM, `Response.text()`, reconstruction ou reencodage ;
- fermeture du contexte, du navigateur, du worker et de Playwright sur chaque chemin terminal.

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

1. **Restauration POC** : executer la procedure de la section 2 et obtenir une base verte.
2. **Integration runtime** : brancher J5 sur les ports, la lease, le worker et le superviseur de
   `WO-SS-20260827-013` sans duplication.
3. **Adaptateur J5** : remplacer le transport GET de reference par Playwright pour les trois
   familles exactes, sans fallback.
4. **Campagne et controle** : partager un seul contexte neuf, conserver l'ordre et raccorder
   l'arret Web au superviseur.
5. **Qualification loopback** : verifier les statuts, octets, `404`, incidents et nettoyage.
6. **Regression hors ligne** : requalifier l'import multi-match et ses declarations `404` avec zero
   processus navigateur.
7. **Documentation et readiness** : architecture J5, runbook, rapport, README, changelog et WO.
8. **Qualification fournisseur optionnelle** : seulement apres un nouveau go proprietaire exact.

## 9. Matrice de validation minimale

| Scenario | Resultat attendu |
|---|---|
| base restauree avant migration | `clean verify` vert et aucune trace runtime FlareSolverr |
| demarrage normal / profil absent | aucun worker, Chromium ou telechargement |
| profil opt-in mais aucune confirmation | runtime inerte, zero GET |
| preparation seule ou claim invalide | zero contexte et zero GET |
| cache hits selon contrat J5 | zero Playwright pour les familles servies localement |
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
<commande loopback opt-in definie apres integration de WO-013>
git diff --check
```

Les tests standards et d'integration PostgreSQL ne lancent jamais Chromium et n'accedent jamais a
SofaScore.

## 10. Qualification fournisseur separee

La readiness loopback ne vaut pas autorisation fournisseur. Un futur go doit figer l'identite
canonique, les trois chemins exacts, le plafond de trois GET et la fenetre horaire. Une phrase
exacte, un acquittement et une action finale restent requis.

Le premier `403`, `429`, challenge, HTML, redirection, timeout, `5xx`, route inattendue ou anomalie
sensible arrete la campagne sans retry. Le rapport final est minimise et ne contient ni payload,
cookie, en-tete, URI complete ou donnee de session. Le controle est ensuite reverrouille et
l'absence de processus residuel est constatee.

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

- `pom.xml`, uniquement apres restauration controlee puis integration du profil commun ;
- adaptateur Playwright J5 derriere les trois ports existants ;
- `ProviderJ5EventDataRestTransport.java`, restaure puis retire du runtime apres equivalence ;
- services et controles de campagne J5 ;
- `SofascoreProperties.java` et configuration opt-in ;
- raccordement au superviseur, a la lease et a l'IPC de `WO-SS-20260827-013` ;
- tests unitaires, integration, loopback et regressions import multi-match/V14 ;
- architecture J5, runbook, README, changelog et rapport de readiness.

Aucune migration n'est attendue. Toute evolution de provenance ou de persistance exige une
migration Flyway append-only separee.

## 13. Portes de statut

```text
WORK_ORDER_STATUS=OPEN_AWAITING_PREREQUISITES
IMPLEMENTATION_STARTED=NO
PROVIDER_CALL_AUTHORIZED=NO
SHARED_RUNTIME_DEPENDENCY=WO-SS-20260827-013
OWNER_POC_RESTORATION=AUTHORIZED_PENDING_EXECUTION_AT_WO_START
OWNER_POC_FILES_TOUCHED_DURING_OPENING=NO
ONLINE_MULTI_MATCH_SCOPE=EXCLUDED
OFFLINE_MULTI_MATCH_REGRESSION=REQUIRED
LOCAL_READINESS=NOT_RUN
HUMAN_PROVIDER_QUALIFICATION=NOT_RUN_NOT_AUTHORIZED
CLOSURE=NOT_AUTHORIZED
```
