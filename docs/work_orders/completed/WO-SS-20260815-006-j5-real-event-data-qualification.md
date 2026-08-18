# WO-SS-20260815-006 — Qualification réelle bornée des données événement J5

- **Statut :** `VALIDATED`
- **Date :** 2026-08-15
- **Date de démarrage :** 2026-08-15
- **Prérequis fonctionnel :** WO-SS-20260815-005 validé et archivé
- **Base locale :** `5063ac8e` — clôture documentaire J5 hors ligne
- **Jalon :** J5 — extension de qualification réelle
- **Branche :** `codex/j5-real-event-data-qualification`
- **Familles :** `EVENT_STATISTICS`, `EVENT_INCIDENTS`, `EVENT_LINEUPS`
- **Développement et tests hors ligne :** `AUTHORIZED`
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Première campagne humaine réelle :** `EXECUTED_ONE_CALL_HTTP_404_MISCLASSIFIED`
- **Amendement correctif demandé par le propriétaire :** `AUTHORIZED`
- **Campagne corrective réelle V3 :** `EXECUTED_INCIDENTS_PASS_LINEUPS_NOT_ATTEMPTED`
- **Campagne corrective réelle V4 :** `EXECUTED_THREE_CALLS_INCIDENTS_AND_LINEUPS_PASS`
- **Correction du réarmement après succès :** `PASS_REAL_SECOND_CAMPAIGN_STARTED`
- **Seconde campagne après réarmement :** `STATISTICS_PASS_INCIDENTS_SCHEMA_INCOMPATIBLE`
- **Correction ciblée du carton de banc V5 :** `PASS_REAL`
- **Campagnes Paris Saint-Germain — Lens V5 :** `FIRST_HALF_AND_FULL_TIME_REPLAY_PASS`
- **Fiche métier incidents V6 :** `PASS_REAL_LENS_PSG`
- **Campagne Arsenal — Manchester City V6 :** `STATISTICS_PASS_INJURY_SUBSTITUTION_REJECTED`
- **Correction ciblée V7 :** `PASS_REAL_AND_INHERITED_BY_V8_V9_V10_V11_V12_V13`
- **Correction ciblée V8 :** `PASS_REAL_HISTORICAL_SCOPE`
- **Correction ciblée V9 :** `PASS_REAL_COMPLETED_LOCKED_THREE_CALLS`
- **Campagne événement 16251993 sous V9 :** `STATISTICS_PASS_REGULAR_GOAL_ORIGIN_REJECTED`
- **Correction ciblée V10 :** `PASS_REAL_UNDER_V11_EVENT_16251993`
- **Observation motif de carton V11 :** `PASS_REAL_EVENT_16671566_SNAPSHOT_185`
- **Session combinée J4 phase 2 + J5 :** `PASS_REAL_EVENT_16671566`
- **Campagne événement 16691018 sous V11 :** `STATISTICS_UNAVAILABLE_UNMINUTED_SHOOTOUT_REJECTED`
- **Correction ciblée V12 :** `PASS_REAL_UNDER_V13_EVENT_16691018`
- **Observation motif de carton V13 :** `PASS_REAL_EVENT_16851672_SNAPSHOT_195`
- **Modification directe de `.env` par l'agent :** `NOT_AUTHORIZED`
- **Polling, planification ou retry :** `NOT_AUTHORIZED`
- **Déploiement VPS :** `NOT_AUTHORIZED`

## 1. Objectif

Ajouter une voie de qualification réelle J5, distincte des parcours J3 et J4, afin qu'un opérateur
puisse sélectionner une identité canonique J4 déjà persistée, préparer sans réseau une campagne,
confirmer exactement cette identité, puis effectuer au maximum trois lectures fournisseur
séquentielles : statistiques, incidents et compositions.

Chaque réponse doit être persistée brute avant toute interprétation. Une réponse compatible est
ensuite normalisée dans les tables append-only J5 avec une provenance `PROVIDER_SNAPSHOT`. Un HTTP
`404` sur une famille facultative produit une indisponibilité explicite et n'est pas un incident ;
le premier incident réel arrête la campagne, verrouille le contrôle et interdit tout appel restant.

La réalisation logicielle de ce Work Order n'a exécuté aucune requête réelle. Les campagnes
  humaines et leurs corrections successives sont consignées aux sections 15 à 20. Le retest
  V4 a terminé les trois appels et qualifié incidents et compositions, puis révélé que le verrou de
  succès empêchait à tort la préparation d'une campagne distincte dans la même instance. Le présent
  amendement a corrigé ce cycle de vie sans exécuter de requête réelle. Le retest humain du
  réarmement a démarré une seconde campagne dans la même instance ; celle-ci a parsé les
  statistiques, puis révélé une forme réelle de carton de banc que V5 a corrigée. Les campagnes
  humaines suivantes, dont Paris Saint-Germain — Lens en première mi-temps puis lors d'un rejeu à
  la fin du match, ont terminé les trois familles. La fiche métier complète des incidents a ensuite
  été transcrite dans V6/V13 et validée par un nouveau retest humain Lens — Paris Saint-Germain.
  Une campagne Arsenal — Manchester City a révélé la classe de substitution sur blessure
  `incidentClass="injury"`, désormais prise en charge de manière ciblée par V7/V14 et validée hors
  ligne sans exécuter de requête réelle. Deux payloads opérateur supplémentaires ont ensuite
  révélé un marqueur terminal `PEN/time=999`, trois cartons sans motif, trois buts ordinaires
  `from="shot"` et le résultat `Woodwork/woodwork`. Ces variantes sont prises en charge par V8/V15
  et validées hors ligne sur les payloads conservés localement, sans appel fournisseur par
  l'agent. Le retest humain V8 du 2026-08-18 a ensuite terminé trois campagnes : cartons sans motif
  avec poursuite après une famille indisponible, rejeu réussi du snapshot 139 contenant la séance
  de tirs au but, puis `inGamePenalty/missed/Woodwork` sur Samsunspor — Göztepe. Deux de ces
  campagnes ont qualifié les trois schémas V8 alors courants en HTTP `2xx`. Une campagne ultérieure
  Cardiff City — Wrexham a toutefois rejeté le snapshot incidents 162 sur le motif de carton
  `Other reason` et révélé `benchAddedTime`. V9/V16 corrigent cette forme et le retest humain V9 a
  ensuite atteint les compositions et `COMPLETED_LOCKED` après trois appels. Une campagne
  ultérieure sur `16251993` a de nouveau invalidé le schéma courant : V9 a rejeté les cinq buts du
  snapshot 179 portant `incidentClass="regular"` et `from="regular"`. V10/V17 corrigent uniquement
  ce tuple et le payload complet passe hors ligne avec 7 incidents sur 7 et `24/24` signaux. Une
  observation directe a ensuite attesté `reason="Off the ball foul"` sur un carton. V11/V18 ajoutent
  uniquement ce motif exact. Un premier retest humain V11 sur `16251993` a qualifié la correction
  des buts réguliers. Une campagne ultérieure sur `16671566` a enchaîné J4 phase 2 puis J5 sans
  redémarrage, terminé les trois familles J5 et affiché le carton `Off the ball foul` inchangé. Les
  qualifications fonctionnelles V11 étaient acquises. Une campagne plus récente sur `16691018` a
  toutefois révélé une séance terminale entièrement non minutée : V11 a rejeté le marqueur `PEN`
  et les quatorze tentatives du snapshot 189. V12/V19 corrigent hors ligne cette forme sans
  inventer de minute, tandis que l'absence simultanée de `reason` et `description` sur un penalty
  raté reste une complétude partielle pour `inGamePenalty` comme pour `penaltyShootout`. Une
  nouvelle observation directe a ensuite attesté
  `reason="Leaving field"` sur un carton. V13/V20 ajoutent uniquement ce motif exact tout en
  héritant du contrat V12. Les campagnes humaines V13 `16691018` et `16851672` ont depuis qualifié
  respectivement la séance non minutée et le nouveau motif, avec trois appels et poursuite jusqu'aux
  compositions dans les deux cas. La liste technique des chemins manquants est maintenant masquée
  dans les panneaux incidents et compositions sans modifier leurs tableaux. Le contrôle visuel
  final l'a confirmé. Le propriétaire a ensuite attesté le reverrouillage local, les états `LOCKED`
  de J4 et J5 après redémarrage et l'arrêt final de l'instance de contrôle.

## 2. Contexte opérateur reçu le 2026-08-15

Le propriétaire a exécuté un second parcours J4 paramétrable avec la configuration déclarée
suivante :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com/
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS
```

Le fichier `.env` n'a pas été lu par l'agent. Ces valeurs sont une déclaration opérateur et non une
copie du fichier local. Elles ne contiennent aucun secret.

Les huit captures fournies, non versionnées, permettent de consigner les observations minimisées
suivantes :

- l'événement `16412917` a reçu une quatrième observation J4, `snapshot:25`, au statut
  `interrupted`, après une version `inprogress` ;
- l'événement `16391135`, Bolton Wanderers — Preston North End, a été acquis par exactement un
  appel J4, `snapshot:26`, HTTP compatible et parsé par `event-details-v2` ;
- le snapshot 26 contient `10068` octets et porte le SHA-256
  `e860c867fe1d51f6b59777cdb2f23098ee10b36e4a7e4a16f9bc7ca988673723` ;
- l'identité canonique de `16391135` est `f7f7a708-0200-38da-8f3f-a00884761072` et son statut
  observé est `inprogress` ;
- la page J5 de cette identité n'effectue aucun repli réseau et montre trois absences locales ;
- l'import du corpus synthétique J5 sur cette identité réelle est refusé avec
  `EVENT_ID_MISMATCH` ;
- aucune donnée synthétique de `900001` n'est donc rattachée à `16391135`.

La phrase de confirmation ponctuelle visible dans une capture n'est ni reproduite ni conservée.
Le rapport prérequis détaillé est
`docs/validation/J5-REAL-EVENT-DATA-PREREQUISITE-OBSERVATION-20260815.md`.

## 3. Revue de l'ADR-SS-001

Les sections 3.4 à 3.8, 3.10 et 9 de l'ADR-SS-001 ont été relues. Le parcours prévu reste :

- local à Windows et lié à `127.0.0.1` ;
- manuel et synchrone ;
- limité à une seule requête simultanée ;
- borné à trois chemins construits localement ;
- sans compte, cookie, jeton, proxy, redirection ou simulation de navigateur ;
- sans polling, tâche planifiée, cache implicite ou retry ;
- avec trois secondes au minimum entre deux tentatives ;
- avec brut local avant parsing et données normalisées séparées ;
- arrêté et verrouillé au premier incident réel, un HTTP `404` de famille étant une disponibilité
  négative observée et non un incident ;
- sans dépendance de production ni transmission au VPS.

Comme pour le rappel manuel J4 qualifié, l'absence de cache est volontaire : une campagne confirmée
constitue une acquisition réelle unique de chaque famille. Aucun second appel à une même famille
n'est possible dans un claim. Après un succès uniquement, une nouvelle campagne exige une
préparation explicite, un nouvel identifiant de requête, une nouvelle phrase et un nouvel
acquittement ; elle reste exclusive de toute autre campagne active. Aucun déclencheur de nouvel
ADR n'est atteint.

```text
ADR_SS_001_J5_REAL_REVIEW=COMPATIBLE_NO_CHANGE_REQUIRED
ADR_SS_001_MODIFICATION_REQUIRED=NO
```

## 4. Configuration future déterminée

Le futur test réel utilisera exclusivement les valeurs réseau suivantes, après réussite de toutes
les validations hors ligne et décision humaine de démarrer la campagne :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
```

L'opt-in J5 est défini avec la valeur par défaut `false`. Cette configuration J5 seule reste
supportée. J3 et J4 phase 1 lui sont exclusifs ; l'amendement de la section 27 autorise également
l'union exacte J4 phase 2 + J5. L'origine avec un slash terminal pourra être normalisée comme
origine racine, mais la forme recommandée reste sans slash.

Après la dernière campagne de la session, ou immédiatement après incident ou abandon, et avant
tout redémarrage ordinaire :

```properties
SOFASCORE_ENABLED=false
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=false
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=false
SOFASCORE_BASE_URL=
SOFASCORE_ALLOWED_ENDPOINTS=
```

Le propriétaire reste responsable de l'édition locale et du reverrouillage de `.env`. Aucune
valeur de mot de passe PostgreSQL ne doit apparaître dans les preuves.

## 5. Campagne réelle autorisable après readiness

### 5.1 Préparation

- la campagne est initiée depuis la page J5 d'une identité canonique existante ;
- aucun identifiant libre n'est envoyé par l'action finale ;
- la préparation ne contacte pas le fournisseur ;
- l'intention contient l'UUID canonique, l'identifiant fournisseur et les trois familles ;
- la phrase exacte contient l'identifiant fournisseur, les trois familles et un code aléatoire ;
- la confirmation expire après cinq minutes et exige un acquittement explicite.

### 5.2 Exécution

Une confirmation valide autorise exactement, dans cet ordre :

1. `GET /api/v1/event/<event_id>/statistics` ;
2. `GET /api/v1/event/<event_id>/incidents` ;
3. `GET /api/v1/event/<event_id>/lineups`.

La concurrence reste égale à un et un délai d'au moins trois secondes sépare deux tentatives. Une
campagne réussie passe à `COMPLETED_LOCKED` : son claim ne peut pas être rejoué, mais une nouvelle
préparation locale explicite peut créer une campagne distincte dans le même processus. Elle remet
les endpoints terminés et le code terminal à zéro, produit une nouvelle phrase et ne déclenche
aucun transport avant une nouvelle confirmation.

### 5.3 Persistance et parsing

- les octets exacts sont enregistrés dans `provider_snapshot` avant parsing ;
- la clé brute contient le type logique et l'identifiant d'événement ;
- les parseurs fournisseur courants sont versionnés `event-statistics-v2`, `event-incidents-v13` et
  `event-lineups-v2`, distincts des contrats synthétiques V1 ;
- l'identifiant attendu vient du claim et de la requête, car les enveloppes de famille peuvent ne
  pas répéter l'identifiant d'événement ;
- un HTTP `404` ne déclenche aucun parsing du corps : il produit un snapshot
  `ENDPOINT_UNAVAILABLE`, une observation `UNAVAILABLE · N/A` et la poursuite sans retry ;
- un résultat compatible produit une observation issue des migrations V7 à V20 liée au snapshot brut ;
- une réponse identique est dédupliquée sans réécriture ; si elle résout une preuve historique
  déjà classée, la nouvelle observation porte le résultat du parseur courant sans reclasser le
  snapshot ;
- aucune donnée partielle n'est persistée après une incompatibilité structurelle ;
- les absences métier compatibles restent `PARTIAL` ou `EMPTY_VALID` selon la famille ;
- `UNAVAILABLE` reste distinct de `EMPTY_VALID` et utilise un normaliseur
  `event-*-unavailable-v1` explicite.

### 5.4 Arrêt terminal

La campagne s'arrête avant tout appel restant sur :

- arrêt opérateur ;
- échec du délai minimal ;
- timeout ou erreur d'entrée/sortie ;
- payload trop volumineux ou contenu sensible ;
- statut HTTP hors `2xx`, notamment `400`, `401`, `403`, `408`, `429` ou `5xx`, à l'exception du
  seul HTTP `404` des trois endpoints exacts J5 ;
- contenu non JSON ou HTML inattendu sur une réponse `2xx` ;
- JSON ambigu, tronqué ou incompatible ;
- incohérence entre identité canonique et claim ;
- erreur de persistance brute, normalisée ou de classification.

Il n'existe aucun retry automatique. Un échec ou un arrêt exige un redémarrage avec configuration
reverrouillée avant toute nouvelle décision.

## 6. Périmètre inclus

- nouvel opt-in J5 sûr par défaut ;
- politique de configuration exclusive et origine exacte ;
- contrôle en mémoire à confirmation unique, verrou anti-rejeu après succès et réarmement local
  explicite d'une campagne distincte ;
- requêtes exactes et transport `RestClient` sans proxy ni redirection ;
- réponse bornée par `RawPayloadEvidence` ;
- parseurs fournisseur versionnés et fixtures de forme créées de zéro ;
- migration Flyway V8 append-only autorisant les versions V2 sans modifier V7 ;
- migration Flyway V9 append-only ajoutant l'indisponibilité explicite et corrigeant la
  classification des anciens snapshots J5 HTTP `404` sans modifier leur brut ;
- migration Flyway V10 append-only autorisant `event-incidents-v3`, sans reclasser ni réécrire les
  snapshots et observations historiques V2 ;
- migration Flyway V11 append-only autorisant `event-incidents-v4` et conservant les identités
  `playerIn` / `playerOut` sans réécrire les observations historiques ;
- migration Flyway V12 append-only autorisant `event-incidents-v5`, conservant la classe et le
  motif facultatifs d'un carton et laissant intactes toutes les observations historiques ;
- fiche métier versionnée des huit types d'incidents, parseur `event-incidents-v6` et migration
  Flyway V13 append-only conservant leurs attributs normalisés sans réécrire l'historique V1–V12 ;
- parseur `event-incidents-v7` et migration Flyway V14 append-only ajoutant uniquement la classe
  attestée de substitution sur blessure sans réécrire l'historique V1–V13 ;
- parseur `event-incidents-v8` et migration Flyway V15 append-only ajoutant uniquement les
  variantes attestées `PEN/time=999`, carton sans motif, but ordinaire `from="shot"` et
  `Woodwork/woodwork` pour les deux familles de pénalty, sans réécrire l'historique V1–V14 ;
- parseur `event-incidents-v9` et migration Flyway V16 append-only ajoutant uniquement le motif de
  carton attesté `Other reason` et le temps additionnel strict `benchAddedTime`, sans réécrire
  l'historique V1–V15 ;
- parseur `event-incidents-v10` et migration Flyway V17 append-only ajoutant uniquement la valeur
  redondante attestée `from="regular"` sur un but de classe `regular`, sans réécrire l'historique
  V1–V16 ;
- parseur `event-incidents-v11` et migration Flyway V18 append-only ajoutant uniquement le motif de
  carton attesté `Off the ball foul`, sans réécrire l'historique V1–V17 ;
- parseur `event-incidents-v12` et migration Flyway V19 append-only conservant une minute absente
  uniquement pour une séance terminale entièrement non minutée et cohérente, sans déduire de temps
  depuis la séquence et sans réécrire l'historique V1–V18 ;
- parseur `event-incidents-v13` et migration Flyway V20 append-only ajoutant uniquement le motif
  de carton attesté `Leaving field`, sans modifier la règle temporelle V12 ni réécrire
  l'historique V1–V19 ;
- persistance normalisée liée à `PROVIDER_SNAPSHOT` ;
- résultat minimisé affichant endpoint, snapshot, taille, hash, statut, complétude et insertion ;
- arrêt global J5 ;
- tests unitaires, MVC et PostgreSQL/Testcontainers exclusivement hors ligne ;
- architecture, runbook, readiness, README et changelog.

## 7. Hors périmètre

- tout appel réel pendant le développement ou Maven ;
- un endpoint autre que les trois familles ;
- une URL, un chemin ou un identifiant fourni librement à l'action finale ;
- plusieurs événements dans une campagne ;
- rejeu automatique, réutilisation d'un ancien claim ou campagnes parallèles dans le même
  processus ;
- polling, planification, boucle live, cache implicite ou retry ;
- proxy, cookie, jeton, compte, en-tête personnalisé ou navigateur automatisé ;
- réutilisation du transport J4 pour les familles J5 ;
- modification d'une migration V1 à V8 déjà partagée ;
- copie d'un payload réel dans Git, un rapport, les logs ou l'interface ;
- activation de `TOURNAMENT_STANDINGS` ;
- export J7, VPS, production ou intégration au Betting Project principal ;
- modification de l'ADR-SS-001 ou des PDF de référence.

## 8. Invariants

1. `server.address=127.0.0.1`.
2. Tous les opt-ins fournisseur valent `false` par défaut.
3. J3 reste exclusif ; J4 phase 1 reste exclusif ; seule l'union exacte J4 phase 2 + J5 est admise.
4. Le catalogue reste `callable=false` et sans URI réelle.
5. `ConnectorGate` et le profil Maven `sofascore-live-test` restent bloquants.
6. Maven ne contacte jamais SofaScore.
7. Trois tentatives fournisseur au maximum après une confirmation humaine.
8. Un seul appel simultané et délai minimal de trois secondes.
9. Aucun retry, polling, planification ou rafraîchissement implicite.
10. Persistance brute avant parsing ; sources brutes et normalisées séparées.
11. Chaque normalisation conserve identité, snapshot, hash, parseur et heure de réception.
12. Aucune donnée partielle après incompatibilité de schéma.
13. Un HTTP `404` de famille est conservé puis la séquence continue sans retry ; le premier
    incident réel bloque les appels restants.
14. Les observations normalisées restent append-only.
15. Aucun payload brut, cookie, jeton, secret ou valeur `.env` sensible dans les sorties.
16. `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
    `NO_CRITICAL_DEPENDENCY` restent effectifs.

## 9. Matrice de tests minimale

| Scénario hors ligne | Résultat attendu |
|---|---|
| configuration par défaut | voie J5 bloquée |
| opt-in J5 sans les trois familles exactes | démarrage ou politique bloqué |
| opt-in J5 avec J3 ou J4 | configuration refusée |
| origine différente, port, utilisateur, query ou fragment | bloqué |
| préparation d'une identité absente | refus sans transport |
| phrase, UUID, acquittement ou TTL invalide | refus sans transport |
| exécution nominale simulée | trois transports dans l'ordre, délai appliqué |
| HTTP `404` sur statistiques | observation `UNAVAILABLE`, puis incidents et compositions appelés une fois |
| marqueur incidents `period` avec `addedTime=999` | brut conservé, avertissement explicite, temps additionnel normalisé absent, compositions appelées |
| incident métier avec `addedTime=999` | `SCHEMA_INCOMPATIBLE`, aucune normalisation partielle |
| réponse `403` sur statistiques | un appel, brut classé, deux familles non appelées |
| réponse `429` sur incidents | deux appels, troisième famille non appelée |
| HTML ou schéma incompatible | brut conservé, aucune normalisation partielle de la famille |
| événement du claim incohérent | refus avant transport ou normalisation |
| réponse nominale répétée en persistance | déduplication brute et normalisée |
| réponse incidents dédupliquée vers un snapshot V2 terminal | nouvelle observation V4, classification historique inchangée, compositions appelées |
| remplacement avec `playerIn` et `playerOut` | deux identités conservées et affichées pour chaque substitution |
| remplacement avec une identité absente | observation `PARTIAL`, chemin manquant, aucun joueur inventé |
| remplacement `incidentClass=injury`, `injury=true` | incident conservé avec entrant/sortant, puis compositions appelées |
| remplacement `incidentClass=injury`, `injury=false` | contradiction atomique, `SCHEMA_INCOMPATIBLE` |
| remplacement `incidentClass=injury` sans booléen | incident conservé `PARTIAL`, aucune valeur de blessure inventée |
| carton de banc avec `time=-5` et `benchTime=58` | brut intact, minute normalisée 58, classe et motif conservés |
| carton avec minute non négative et `benchTime` simultané | contradiction atomique, `SCHEMA_INCOMPATIBLE` |
| temps négatif sur un incident autre que `card` | `SCHEMA_INCOMPATIBLE`, aucune règle extrapolée |
| huit types d'incidents documentés | tous conservés par V6 avec leurs champs applicables |
| métadonnée d'affichage facultative absente sur chacun des huit types | incident conservé, observation `PARTIAL`, chemin manquant explicite |
| type inconnu, enum hors contrat ou paire atomique contradictoire | `SCHEMA_INCOMPATIBLE`, aucune normalisation partielle |
| but brut `incidentClass=owngoal` | origine canonique `ownGoal`, brut inchangé, avertissement explicite |
| décision VAR confirmée ou rejetée | joueur, motif et détail conservés selon la fiche |
| tir au but sans minute globale mais avec première action minutée | minute de la première action auxiliaire utilisée conformément à la fiche |
| séance terminale dont toutes les tentatives omettent minute et action | V12 conserve une minute absente, rend `—` et retourne `PARTIAL` si les autres absences sont facultatives |
| séance mixte, isolée ou dont les séquences sont lacunaires | `SCHEMA_INCOMPATIBLE`, aucune minute déduite de la séquence |
| `inGamePenalty` sans minute | `SCHEMA_INCOMPATIBLE` |
| penalty `missed` sans `reason` ni `description` dans les deux familles | incident conservé `PARTIAL`, motif absent affiché `—` |
| carton `reason="Leaving field"` | motif exact conservé et affiché, puis compositions appelées |
| carton portant une autre valeur non documentée | `SCHEMA_INCOMPATIBLE`, aucune généralisation |
| campagne réussie | `COMPLETED_LOCKED`, ancien claim non rejouable, trois familles consultables |
| préparation explicite après succès | nouveau requestId et nouvelle phrase, endpoints remis à zéro, aucun transport |
| ancienne phrase ou ancien claim après réarmement | refus par identité de requête, aucune exécution |
| préparation pendant une campagne active | refus `ACTIVE_CAMPAIGN_EXISTS` |
| préparation après échec, arrêt ou expiration | refus terminal jusqu'au redémarrage |
| arrêt global | appels suivants refusés |
| suite Maven | zéro appel Internet |
| upgrade V8 contenant un HTTP `404` J5 mal classé | V9 reclasse le snapshot sans modifier son brut |
| upgrade V9 contenant des observations incidents V1/V2 | V10 autorise V3 sans réécrire l'historique |
| upgrade V10 contenant des observations V1/V2/V3 | V11 autorise V4 sans réécrire l'historique |
| upgrade V11 contenant des observations V1/V2/V3/V4 | V12 autorise V5 sans réécrire l'historique |
| upgrade V12 contenant des observations V1 à V5 | V13 autorise V6 sans réécrire l'historique |
| upgrade V13 contenant une observation V6 | V14 autorise V7 sans réécrire l'observation ni ses incidents |
| upgrade V14 contenant une observation V7 | V15 autorise V8 sans réécrire l'observation ni ses incidents |
| upgrade V15 contenant une observation V8 | V16 autorise V9 sans réécrire l'observation ni ses incidents |
| upgrade V16 contenant une observation V9 | V17 autorise V10 sans réécrire l'observation ni ses incidents |
| upgrade V17 contenant une observation V10 | V18 autorise V11 sans réécrire l'observation ni ses incidents |
| upgrade V18 contenant une observation V11 | V19 autorise V12 et la minute nulle strictement bornée sans réécrire l'historique |
| upgrade V19 contenant une observation V12 à minute nulle | V20 autorise V13 sans réécrire l'observation ni son incident |
| Flyway V1 → V20 | migrations et append-only valides |

## 10. Critères d'acceptation

- [x] captures opérateur antérieures minimisées et frontière J4/J5 qualifiée ;
- [x] revue ADR-SS-001 consignée sans modification ;
- [x] futur paramétrage `.env` déterminé sans lire ou modifier `.env` ;
- [x] Work Order et architecture relus dans le diff ;
- [x] propriétés sûres et sélection exclusive implémentées ;
- [x] contrôle de préparation, confirmation et verrou terminal implémenté ;
- [x] trois transports exacts et bornés implémentés ;
- [x] brut persisté avant parsing ;
- [x] parseurs fournisseur versionnés et complétude qualifiés hors ligne ;
- [x] migrations V8 à V20 et provenance `PROVIDER_SNAPSHOT` qualifiées hors ligne ;
- [x] interface et résultat minimisé qualifiés ;
- [x] poursuite après HTTP `404` sans retry et arrêt au premier incident réel prouvés ;
- [x] `mvnw.cmd clean verify` réussi ;
- [x] `mvnw.cmd -Pintegration-tests verify` réussi ;
- [x] readiness humaine publiée ;
- [x] configuration réelle toujours non exécutée pendant l'implémentation ;
- [x] comportement historique de la première campagne et snapshot 30 consignés sans payload brut ;
- [x] cause racine du verrouillage HTTP `404` identifiée ;
- [x] distinction `UNAVAILABLE` / `EMPTY_VALID` / `TRANSPORT_ERROR` implémentée et testée ;
- [x] campagnes humaines correctives exécutées après application de V9 ;
- [x] cause du faux positif incidents V2 identifiée sur deux snapshots réels indépendants ;
- [x] `event-incidents-v3` et l'upgrade V9 → V10 validés hors ligne ;
- [x] campagne humaine corrective exécutée après application de V10 ;
- [x] incidents V3 réels visibles avec `20/20` signaux sur le snapshot dédupliqué 32 ;
- [x] cause de `RAW_CLASSIFICATION_ERROR` identifiée avant le troisième appel ;
- [x] déduplication historique, parseur V4 et joueurs entrant/sortant validés hors ligne ;
- [x] campagne humaine corrective exécutée après application de V11 ;
- [x] incidents V4 réels avec joueurs entrant/sortant et compositions V2 réelles qualifiés ;
- [x] cause du verrou global après succès identifiée et réarmement explicite validé hors ligne ;
- [x] nouvelle campagne préparée et exécutée dans la même instance après un succès, avec nouveau claim ;
- [x] cause du blocage de la seconde campagne localisée sur le carton de banc du snapshot 60 ;
- [x] règle V5 étroite et migration V12 validées hors ligne ;
- [x] campagnes humaines correctives exécutées avec `event-incidents-v5` ;
- [x] campagne Paris Saint-Germain — Lens terminée en première mi-temps puis rejouée à la fin ;
- [x] fiche métier des huit types transcrite dans `event-incidents-v6` et V13 ;
- [x] suites Maven V6/V13 standards et d'intégration réussies (324 + 27 tests, zéro échec) ;
- [x] campagne humaine corrective exécutée avec `event-incidents-v6` ;
- [x] cause Arsenal — Manchester City isolée sur une substitution `incidentClass=injury` ;
- [x] correction V7/V14 et poursuite hors ligne vers les compositions validées ;
- [x] cause de la séance de tirs au but isolée sur le marqueur terminal `PEN/time=999` ;
- [x] cartons sans `reason` confirmés compatibles et verrou connexe `from="shot"` corrigé ;
- [x] `Woodwork/woodwork` pris en charge pour `inGamePenalty` et `penaltyShootout` ;
- [x] correction V8/V15, deux payloads réels rejoués hors ligne et poursuite vers les compositions validée ;
- [x] campagne humaine corrective exécutée avec le parseur courant `event-incidents-v8`, incluant
  la requalification du contrat V7 hérité ;
- [x] cause du snapshot 162 isolée sur le motif `Other reason` et le champ `benchAddedTime` ;
- [x] correction V9/V16, payload complet rejoué hors ligne à 20/20 et régressions V8 conservées ;
- [x] campagne humaine corrective exécutée avec le parseur courant `event-incidents-v9`, jusqu'aux
  compositions et sans retry ; snapshot 162 reparsé à `COMPLETE`, carton `Other reason` à `90+9`
  et snapshot compositions 165 à `85/85` ;
- [x] cause du snapshot 179 isolée sur cinq buts `incidentClass="regular"` / `from="regular"` ;
- [x] correction V10/V17, payload complet rejoué hors ligne à 7/7 et `24/24`, poursuite de service
  vers les compositions et régressions V8/V9 conservées ;
- [x] suites Maven V10/V17 standards et d'intégration réussies (348 + 31 tests, zéro échec) ;
- [x] valeur `reason="Off the ball foul"` caractérisée comme motif d'obstruction et ajoutée au
  vocabulaire fermé sans traduction ni généralisation ;
- [x] correction V11/V18, forme opérateur rejouée hors ligne, comparaison historique V10,
  poursuite de service vers les compositions et upgrade append-only validés ;
- [x] suites Maven V11/V18 standards et d'intégration réussies (351 + 32 tests, zéro échec) ;
- [x] campagne humaine corrective exécutée sous `event-incidents-v11`, jusqu'aux compositions et
  sans retry, puis qualification du motif `Off the ball foul` dans la session combinée ;
- [x] cause du snapshot 189 isolée sur quinze minutes absentes et absence de problème sur les sept
  tirs ratés sans `reason` ni `description` ;
- [x] correction V12/V19, payload complet rejoué hors ligne à 33 incidents, poursuite de service,
  rendu `—`, persistance nullable et upgrade append-only validés ;
- [x] suites Maven V12/V19 standards et d'intégration réussies (366 + 34 tests, zéro échec) ;
- [x] valeur `reason="Leaving field"` ajoutée au seul vocabulaire `card`, comparaison historique
  V12, poursuite de service et upgrade V19 → V20 validés hors ligne ;
- [x] suites Maven V13/V20 standards et d'intégration réussies (369 + 35 tests, zéro échec) ;
- [x] campagne humaine corrective exécutée avec le parseur courant `event-incidents-v13` sur la
  séance non minutée : événement `16691018`, snapshots 188/189/192, observation incidents 100 à
  `PARTIAL · 142/171`, compositions atteintes après trois appels et sans retry ;
- [x] campagne humaine corrective exécutée avec `event-incidents-v13` sur un carton
  `reason="Leaving field"` : événement `16851672`, snapshot incidents 195 / observation 103 à
  `COMPLETE · 81/81`, compositions snapshot 196 atteintes après trois appels et sans retry ;
- [x] listes techniques de chemins manquants retirées du rendu incidents et compositions, avec
  régression MVC préservant les badges, compteurs et contenus des tableaux ;
- [x] contrôle visuel après reconstruction confirmant l'absence de ces listes techniques et le
  contenu inchangé des deux tableaux, sur les neuf captures opérateur de clôture ;
- [x] configuration locale reverrouillée, états J4 et J5 `LOCKED` vérifiés après redémarrage et
  arrêt final confirmé après les retests V13.

## 11. Unités de livraison prévues

1. `docs: start guarded J5 real qualification work order`
2. `feat: add guarded J5 provider contracts and parsers`
3. `feat: execute one confirmed J5 real campaign`
4. `test: qualify J5 real path offline`
5. `docs: publish J5 real campaign readiness`
6. `test: isolate historical bindings from armed J5 configuration`
7. `docs: record the failed-locked J5 real campaign`
8. `fix: continue J5 after an unavailable optional family`
9. `docs: record the J5 HTTP 404 policy correction`
10. `fix: accept provider incident period markers`
11. `docs: record the J5 incident parser correction`
12. `fix: preserve reparsed J5 evidence and substitution players`
13. `docs: record the J5 deduplication and substitution correction`
14. `fix: rearm J5 after a completed campaign`
15. `docs: record J5 V4 qualification and rearm correction`

## 12. Readiness technique hors ligne

```text
STANDARD_TESTS=278
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
INTEGRATION_TESTS=25
INTEGRATION_FAILURES=0
INTEGRATION_ERRORS=0
INTEGRATION_SKIPPED=0
FLYWAY_MIGRATIONS=12
POSTGRESQL=18.4_TESTCONTAINERS
SOFASCORE_PROVIDER_CALLS=0
J5_REAL_TECHNICAL_READINESS=PASS
```

La preuve détaillée est conservée dans
`docs/validation/J5-REAL-EVENT-DATA-TECHNICAL-READINESS-20260815.md`. Elle qualifie le code et les
garde-fous hors ligne ; elle ne qualifie pas les trois formes de réponse nominales actuelles du
fournisseur. La première campagne humaine a révélé une indisponibilité HTTP `404` mal classée. Le
correctif est validé hors ligne ; le Work Order reste actif jusqu'au retest humain, au
reverrouillage de `.env` et à l'arrêt final.

## 13. État de préparation

```text
WO_ID=WO-SS-20260815-006
WO_STATUS=REARM_REAL_PASS_V5_BENCH_CARD_CORRECTION_VALIDATED_OFFLINE
BASE_COMMIT=5063ac8e
BRANCH=codex/j5-real-event-data-qualification
J5_OFFLINE_STATUS=VALIDATED
J5_REAL_IMPLEMENTATION_STATUS=PASS
J5_REAL_PROVIDER_CALLS_LAST_SUCCESSFUL_CAMPAIGN=3
J5_REAL_FIRST_CAMPAIGN_STATUS=HTTP_404_MISCLASSIFIED_AND_LOCKED
J5_REAL_HTTP_404_POLICY=ENDPOINT_UNAVAILABLE_CONTINUE_NO_RETRY
J5_REAL_V4_CORRECTIVE_RETEST=PASS_THREE_CALLS
J5_REAL_REARM_RETEST=PASS_SECOND_CAMPAIGN_STARTED
J5_REAL_SECOND_CAMPAIGN=STATISTICS_PASS_INCIDENTS_SCHEMA_INCOMPATIBLE_LINEUPS_NOT_ATTEMPTED
J5_REAL_SECOND_CAMPAIGN_PROVIDER_CALLS=2
J5_REAL_BENCH_CARD_CORRECTION=PASS_OFFLINE_RETEST_REQUIRED
J5_REAL_ENV_CONFIGURATION=APPLIED_FOR_REAL_CAMPAIGN_RELOCK_PENDING
J5_REAL_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_APPLICATION_TRANSPORT=IMPLEMENTED_GUARDED_DEFAULT_OFF
J5_REAL_CAN_BE_EXECUTED=PENDING_V5_RETEST_AFTER_RESTART_AND_LOCAL_CONFIGURATION
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

## 14. Précontrôle opérateur avec J5 armé

Le 2026-08-15, l'opérateur a exécuté `mvnw.cmd clean verify` avec la configuration locale J5
armée, avant toute préparation ou confirmation Web. Les 257 tests ont été exécutés, mais trois
scénarios historiques de `SofascorePropertiesTest` ont échoué au démarrage parce qu'ils
héritaient de l'opt-in J5 tout en activant leur propre voie J3 ou J4. La contrainte d'exclusivité a
donc joué son rôle ; aucun endpoint fournisseur n'a été appelé.

Le correctif fixe explicitement J5 à `false` dans chacun de ces trois mini-contextes et en vérifie
la valeur. Le test ciblé puis la commande complète ont été rejoués avec la configuration J5
toujours armée :

```text
TARGETED_CONFIGURATION_TESTS=9
TARGETED_FAILURES=0
STANDARD_TESTS=257
STANDARD_FAILURES=0
STANDARD_ERRORS=0
STANDARD_SKIPPED=0
BUILD_RESULT=SUCCESS
SOFASCORE_PROVIDER_CALLS=0
PRECHECK_REAL_CAMPAIGN_STATUS=NOT_RUN
```

Cette correction qualifie uniquement l'isolation de la suite Maven. La campagne humaine réelle,
la validation des trois schémas fournisseur et le reverrouillage final restent à exécuter.

## 15. Première campagne humaine et comportement historique

Le 2026-08-15, l'opérateur a préparé puis confirmé une campagne pour l'identité canonique
`9b9e7909-62e7-3985-bc34-759ad8684224`, fournisseur `16412917`. Le prérequis J4 était le
`snapshot:28`, HTTP `200`, parsé par `event-details-v2`, et la rencontre était au statut
`finished`.

Le premier endpoint `EVENT_STATISTICS` a renvoyé HTTP `404` avec un contenu JSON de 44 octets. Le
brut a été conservé dans le snapshot 30 puis l'ancienne politique l'a classé `TRANSPORT_ERROR`
avec le code `HTTP_STATUS_404`. Le contrôle est passé à `FAILED_LOCKED` après exactement un appel.
Aucun appel `EVENT_INCIDENTS` ou `EVENT_LINEUPS` n'a été tenté et aucun retry n'a été effectué.

L'ouverture ultérieure de la page de `16391135` a montré le même verrou terminal global et n'a
créé aucun snapshot J5 supplémentaire. Les trois schémas restent non validés. La phrase de
confirmation, le jeton local et le payload brut ne sont pas reproduits.

La preuve détaillée est conservée dans
`docs/validation/J5-REAL-EVENT-DATA-CAMPAIGN-20260815.md`.

```text
J5_REAL_EVENT_ID=16412917
J5_REAL_CANONICAL_EVENT_ID=9b9e7909-62e7-3985-bc34-759ad8684224
J5_REAL_ORIGINAL_TERMINAL_STATE=FAILED_LOCKED
J5_REAL_ORIGINAL_TERMINAL_CODE=HTTP_STATUS_404
J5_REAL_PROVIDER_CALLS=1
J5_REAL_STATISTICS_OBSERVATION=NOT_PERSISTED_BY_HISTORICAL_EXECUTION
J5_REAL_STATISTICS_AVAILABILITY=UNAVAILABLE_HTTP_404
J5_REAL_INCIDENTS_QUALIFICATION=NOT_ATTEMPTED
J5_REAL_LINEUPS_QUALIFICATION=NOT_ATTEMPTED
J5_REAL_SNAPSHOT_ID=30
J5_REAL_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_RETRY=0
J5_REAL_CORRECTIVE_RETEST=NOT_RUN_OPERATOR_ONLY
J5_REAL_APPLICATION_STOP=CONFIRMED_LOCAL_PORT_CLOSED
J5_REAL_ENV_RELOCK=PENDING_OPERATOR_CONFIRMATION
```

Le processus local de cette première campagne est arrêté. Cette section reste immuable comme preuve
du comportement historique ; la section 16 consigne la décision corrective ultérieure.

## 16. Amendement correctif — famille facultative indisponible

Le propriétaire a précisé que les statistiques ne sont pas obligatoirement disponibles dans
toutes les compétitions et que, dans ce cas, le championnat d'Ukraine n'est pas considéré comme un
championnat majeur. La requête avait atteint le bon chemin et le bon identifiant : la cause se
trouve dans `J5RealEventDataService`, qui envoyait tout statut non `2xx` vers
`failAndLock(...)` avant toute distinction de disponibilité.

Le correctif autorisé par cet amendement est strictement borné :

- le seul HTTP `404` des trois endpoints J5 exacts devient `ENDPOINT_UNAVAILABLE` ;
- une observation append-only vide de valeurs et de statut `UNAVAILABLE` conserve snapshot, hash,
  normaliseur `event-*-unavailable-v1` et heure de réception ;
- `UNAVAILABLE · N/A` reste distinct de `EMPTY_VALID · 100%` ;
- aucun corps `404` n'est parsé et aucune valeur n'est inventée ;
- le même endpoint n'est jamais rappelé ; la campagne attend le délai prévu puis poursuit ;
- `403`, `408`, `429`, `5xx`, transport, contenu ou schéma incompatible restent terminaux ;
- V9 reclasse les anciens snapshots J5 `TRANSPORT_ERROR/HTTP_STATUS_404` sans modifier le brut et
  sans créer de normalisation rétroactive.

```text
J5_HTTP_404_ROOT_CAUSE=NON_2XX_POLICY_TOO_BROAD
J5_HTTP_404_CORRECTIVE_IMPLEMENTATION=PASS
J5_HTTP_404_CORRECTIVE_STANDARD_TESTS=261
J5_HTTP_404_CORRECTIVE_INTEGRATION_TESTS=20
J5_HTTP_404_CORRECTIVE_FLYWAY_VERSION=9
J5_HTTP_404_CORRECTIVE_PROVIDER_CALLS_BY_AGENT=0
J5_HTTP_404_CORRECTIVE_RETEST=NOT_RUN_OPERATOR_ONLY
J5_PROVIDER_SCHEMA_VALIDATED=NO
```

La campagne corrective reste un geste humain séparé dans le temps : application préalable de V9,
revue de ce Work Order, préparation sans réseau, nouvelle confirmation exacte et au plus trois
appels ordonnés. Le succès attendu peut combiner `UNAVAILABLE` et des familles parsées ; seul un
incident réel doit produire `FAILED_LOCKED`. Après le geste, le propriétaire reverrouille `.env`
et arrête l'application. L'agent ne lit ni ne modifie ce fichier et n'exécute aucun appel réel.

## 17. Amendement correctif — sentinelle des marqueurs de période

Deux campagnes humaines exécutées après V9 ont dépassé l'étape des statistiques puis se sont
arrêtées sur `EVENT_INCIDENTS` :

- pour `16412917`, le snapshot 30 conserve l'indisponibilité HTTP `404` des statistiques et le
  snapshot 32 conserve une réponse incidents HTTP `200` de 22 945 octets contenant 20 objets ;
- pour `16391135`, le snapshot 33 contient des statistiques parsées `COMPLETE · 100%` avec
  `268/268` signaux, puis le snapshot 34 conserve une réponse incidents HTTP `200` de 48 690
  octets contenant 22 objets ;
- dans les deux réponses incidents, V2 signalait exactement deux valeurs hors plage :
  `addedTime=999` sur des objets `incidentType=period` ;
- `SCHEMA_INCOMPATIBLE` étant un incident terminal réel, `EVENT_LINEUPS` n'a été appelé dans
  aucune de ces deux campagnes. Il ne s'agit donc pas d'une absence de compositions en base, mais
  de la conséquence directe du verrouillage après parsing des incidents.

L'interruption signalée par l'opérateur peut expliquer le contexte temporel de `16412917`, mais
la même convention est présente dans l'autre rencontre. Aucune durée n'est donc déduite : `999`
est traité comme une sentinelle fournisseur, uniquement sur un marqueur de période. Les snapshots
32 et 34 restent immuables et conservent leur classification historique V2.

Le correctif versionné introduit `event-incidents-v3` :

- le brut et son SHA-256 restent inchangés ;
- `addedTime=999` sur `period` produit l'avertissement `PROVIDER_SENTINEL_NORMALIZED` et une valeur
  normalisée absente, jamais `999` minutes ;
- la même valeur reste incompatible pour un carton, un but, un remplacement ou tout incident
  métier ;
- les objets globaux `period` et `injuryTime` ne sont pas artificiellement rattachés à domicile ou
  extérieur pour le calcul de complétude ;
- V10 autorise la provenance V3 sans modifier les observations V1/V2 existantes ;
- les tests de service atteignent bien `EVENT_LINEUPS` après un résultat incidents V3 compatible.

La première exécution d'intégration a été bloquée avant les tests Flyway parce que le contexte
héritait des opt-ins opérateur J4/J5 simultanés. Le test PostgreSQL force désormais toutes les
voies réseau à `false`, sans lire ni modifier `.env`. La relance a validé les 21 tests et les
upgrades V1 → V10 et V9 → V10.

```text
J5_INCIDENT_V2_ROOT_CAUSE=PERIOD_ADDED_TIME_SENTINEL_999_REJECTED_BY_GLOBAL_RANGE
J5_INCIDENT_REAL_EVIDENCE=SNAPSHOTS_32_AND_34_HTTP_200
J5_INCIDENT_REAL_OBJECT_COUNTS=20_AND_22
J5_INCIDENT_V3_IMPLEMENTATION=PASS_OFFLINE
J5_INCIDENT_V3_PARSER=event-incidents-v3
J5_INCIDENT_V3_STANDARD_TESTS=263
J5_INCIDENT_V3_INTEGRATION_TESTS=21
J5_INCIDENT_V3_FLYWAY_VERSION=10
J5_INCIDENT_V3_PROVIDER_CALLS_BY_AGENT=0
J5_STATISTICS_REAL_COMPATIBILITY=OBSERVED_ON_16391135
J5_LINEUPS_REAL_COMPATIBILITY=NOT_YET_OBSERVED
J5_INCIDENT_V3_CORRECTIVE_RETEST=PASS_REAL_20_OF_20
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le rapport minimisé est conservé dans
`docs/validation/J5-REAL-INCIDENT-PERIOD-MARKER-CORRECTION-20260816.md`. Le retest réel V3 a
confirmé ensuite que les incidents devenaient parsables, mais a révélé un second verrou de
déduplication avant `EVENT_LINEUPS`, consigné à la section 18.

## 18. Amendement correctif — déduplication historique et participants des remplacements

Le retest humain exécuté après application de V10 sur `16412917` a confirmé les points suivants :

- les statistiques restent `UNAVAILABLE · N/A` sur le snapshot 30, conformément au HTTP `404`
  attendu pour cette rencontre ;
- `event-incidents-v3` parse correctement le snapshot dédupliqué 32 et rend visibles 20 incidents
  avec une complétude `20/20` ;
- la campagne s'arrête néanmoins avec `RAW_CLASSIFICATION_ERROR` après exactement deux appels ;
- `EVENT_LINEUPS` n'est donc toujours pas appelé ; son absence locale ne démontre aucune
  incompatibilité de son endpoint ;
- les remplacements sont présents, mais V3 ne conserve pas les objets distincts `playerIn` et
  `playerOut`, de sorte que les identités du joueur entrant et du joueur sortant ne sont pas
  affichées.

### 18.1 Cause racine de l'arrêt avant compositions

La réponse incidents est identique à celle déjà conservée au snapshot 32. La persistance brute la
déduplique correctement vers cette preuve historique, dont le statut V2
`SCHEMA_INCOMPATIBLE` est immuable. Après le succès V3, le service tentait pourtant de remplacer ce
statut par `PARSED`. Le stockage refusait cette réécriture append-only et le service transformait
ce refus attendu en `RAW_CLASSIFICATION_ERROR`, puis verrouillait la campagne avant le troisième
appel.

Le correctif distingue désormais le résultat de persistance brute :

- un brut `INSERTED` reçoit sa classification terminale après persistance normalisée ;
- un brut `DEDUPLICATED` conserve sa classification historique ; le résultat du parseur courant
  est représenté exclusivement par la nouvelle observation normalisée append-only ;
- le chemin direct J5 n'accepte toujours pas de résultat de cache implicite ;
- aucune famille n'est rappelée et tous les délais ainsi que les règles d'arrêt réseau restent
  inchangés.

Le test de service reproduit exactement la séquence minimisée : snapshot 30 dédupliqué en
`ENDPOINT_UNAVAILABLE`, snapshot 32 dédupliqué et reparsé, puis snapshot compositions nouvellement
inséré. Toute tentative de classification des snapshots 30 ou 32 fait échouer le test ; la
campagne doit malgré cela terminer ses trois appels ordonnés.

### 18.2 Cause racine et décision V4 pour les remplacements

V3 ne lisait que l'objet générique `player`. La forme fournisseur d'un remplacement contient
deux objets sémantiquement distincts : `playerIn` et `playerOut`. `event-incidents-v4` reprend la
règle V3 de `addedTime=999` et ajoute, pour chaque objet `incidentType=substitution` :

- identifiant fournisseur et nom du joueur entrant ;
- identifiant fournisseur et nom du joueur sortant ;
- deux signaux de complétude indépendants ;
- un statut `PARTIAL` et le chemin exact lorsqu'une identité manque, sans identité synthétique.

La migration append-only V11 ajoute quatre colonnes facultatives liées par paires, autorise la
provenance `event-incidents-v4` et ne réécrit aucune observation V1/V2/V3. La vue locale ajoute les
colonnes « Entrant » et « Sortant ». Les tests couvrent plusieurs remplacements dans une même
réponse afin de garantir que la règle ne s'applique pas seulement au premier objet.

### 18.3 Validation hors ligne et suite humaine

```text
J5_INCIDENT_V3_REAL_RETEST=PASS_20_OF_20_ON_SNAPSHOT_32
J5_SECTION_18_REAL_PROVIDER_CALLS=2
J5_SECTION_18_REAL_TERMINAL_CODE=RAW_CLASSIFICATION_ERROR
J5_SECTION_18_LINEUPS_REAL_STATUS=NOT_ATTEMPTED
J5_DEDUPLICATED_RAW_RECLASSIFICATION=DISABLED
J5_INCIDENT_CURRENT_PARSER=event-incidents-v4
J5_SUBSTITUTION_PARTICIPANTS=PERSISTED_AND_RENDERED
J5_FLYWAY_VERSION=11
J5_STANDARD_TESTS=268
J5_INTEGRATION_TESTS=23
J5_PROVIDER_CALLS_BY_AGENT=0
J5_V4_REAL_RETEST_AT_SECTION_18=NOT_RUN_OPERATOR_ONLY
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le rapport minimisé détaillé est conservé dans
`docs/validation/J5-REAL-DEDUPLICATION-AND-SUBSTITUTION-CORRECTION-20260816.md`. Le geste réel qui
était alors requis — redémarrage avec V11, nouvelle préparation et nouvelle confirmation — a
depuis qualifié incidents V4 et compositions V2 ; son résultat et la nouvelle anomalie de cycle
de vie sont consignés à la section 19. L'agent ne lit ni ne modifie `.env` et n'exécute aucun appel
fournisseur.

## 19. Retest réel V4, compositions et réarmement après succès

Le retest humain exécuté après application de V11 sur `16412917` a terminé la campagne sans
incident bloquant et sans retry. Les captures opérateur, non versionnées, établissent les preuves
minimisées suivantes :

| Ordre | Famille | Snapshot | HTTP | Parseur / normaliseur | Complétude | Observation |
|---:|---|---:|---:|---|---|---:|
| 1 | `EVENT_STATISTICS` | 30 | 404 | `event-statistics-unavailable-v1` | `UNAVAILABLE · N/A` | 7 |
| 2 | `EVENT_INCIDENTS` | 32 | 200 | `event-incidents-v4` | `COMPLETE · 36/36` | 12 |
| 3 | `EVENT_LINEUPS` | 55 | 200 | `event-lineups-v2` | `COMPLETE · 85/85` | 13 |

Le snapshot statistiques conserve 44 octets et représente l'indisponibilité attendue pour cette
rencontre. Le snapshot incidents conserve 22 945 octets ; toutes les lignes `substitution`
affichent le joueur entrant et le joueur sortant lorsque le fournisseur les fournit. Le snapshot
compositions conserve 35 669 octets ; les deux côtés sont visibles, avec les formations domicile
`4-1-4-1` et extérieur `4-3-3`. Le contrôle a terminé en `COMPLETED_LOCKED` après exactement trois
appels. Aucun quatrième appel, retry ou rappel d'une famille n'est observé. La phrase ponctuelle de
confirmation n'est ni reproduite ni conservée.

### 19.1 Nouvelle anomalie de cycle de vie

Après ce succès, l'ouverture de la page de `16391135` dans la même instance affichait encore le
snapshot global `COMPLETED_LOCKED` de `16412917` et désactivait « Préparer la campagne J5 ». La
cause est le contrôle J5 singleton : il impose volontairement une concurrence globale égale à un,
mais son garde historique confondait le verrou anti-rejeu d'un succès avec les verrous de
processus issus d'un échec, d'un arrêt ou d'une expiration.

La correction conserve un seul contrôle global et applique désormais les règles suivantes :

- `LOCKED` et `COMPLETED_LOCKED` autorisent une préparation locale explicite si la politique
  fournisseur reste disponible ;
- cette préparation crée un nouvel identifiant de requête et une nouvelle phrase, remplace
  l'identité ciblée, vide les endpoints terminés et efface le code terminal ;
- elle ne résout aucune URI et n'appelle jamais le service d'exécution ;
- l'ancienne phrase est refusée et l'ancien claim ne peut plus continuer ;
- `AWAITING_CONFIRMATION` et `EXECUTING` refusent une campagne concurrente ;
- `FAILED_LOCKED`, `STOPPED_LOCKED` et `EXPIRED_LOCKED` exigent toujours un redémarrage.

Cette distinction n'ajoute ni retry, ni cache, ni polling, ni exécution automatique. Chaque
nouvelle campagne réussie exige un nouveau jeton de formulaire local, une préparation, une phrase
exacte, un acquittement et une action finale distincte.

### 19.2 Validation hors ligne et suite humaine

```text
J5_V4_REAL_RETEST=PASS_THREE_CALLS_NO_RETRY
J5_REAL_STATISTICS=UNAVAILABLE_HTTP_404_SNAPSHOT_30_OBSERVATION_7
J5_REAL_INCIDENTS=EVENT_INCIDENTS_V4_COMPLETE_36_OF_36_SNAPSHOT_32_OBSERVATION_12
J5_REAL_SUBSTITUTION_PARTICIPANTS=PASS_RENDERED
J5_REAL_LINEUPS=EVENT_LINEUPS_V2_COMPLETE_85_OF_85_SNAPSHOT_55_OBSERVATION_13
J5_REAL_TERMINAL_STATE=COMPLETED_LOCKED
J5_COMPLETED_CAMPAIGN_REARM=PASS_OFFLINE_RETEST_REQUIRED
J5_STANDARD_TESTS=270
J5_INTEGRATION_TESTS=23
J5_FLYWAY_VERSION=11
J5_PROVIDER_CALLS_BY_AGENT=0
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le rapport minimisé détaillé est
`docs/validation/J5-REAL-V4-LINEUPS-AND-REARM-CORRECTION-20260816.md`. Après chargement du nouveau
binaire, le prochain contrôle humain doit terminer une campagne A, puis préparer explicitement une
campagne B dans la même instance. Il doit vérifier un nouvel identifiant et une nouvelle phrase,
l'absence de transport pendant la préparation, le refus de l'ancien claim et le maintien du
verrou de processus après tout échec, arrêt ou expiration. Le reverrouillage local et l'arrêt final
restent obligatoires. L'agent ne lit ni ne modifie `.env` et n'exécute aucun appel fournisseur.

## 20. Retest réel du réarmement et correction V5 du carton de banc

Le retest humain du correctif de cycle de vie a qualifié son objectif : après une première campagne
réussie sur `16391135`, l'opérateur a préparé, confirmé puis exécuté une campagne distincte sur
`16483632` sans redémarrer l'instance. Le nouvel identifiant de requête et la nouvelle confirmation
ont donc effectivement remplacé le claim terminé sans le rejouer.

La seconde campagne a ensuite fourni les preuves minimisées suivantes :

| Ordre | Famille | Snapshot | HTTP | Taille | Résultat |
|---:|---|---:|---:|---:|---|
| 1 | `EVENT_STATISTICS` | 59 | 200 | 25 562 octets | `event-statistics-v2`, `COMPLETE · 100 %` |
| 2 | `EVENT_INCIDENTS` | 60 | 200 | 47 092 octets | V4 `SCHEMA_INCOMPATIBLE` sur un incident parmi 17 |
| 3 | `EVENT_LINEUPS` | — | — | — | non appelé après l'arrêt strict |

Il n'y a eu que deux appels fournisseur, sans retry. Le brut incidents a été persisté avant parsing.
La seule rupture V4 est portée par un carton dont `time=-5` n'est pas une minute de match. La règle
métier confirmée par le propriétaire est la suivante : le joueur Kerem Aktürkoğlu reçoit un carton
jaune à la 58e minute, minute portée par `benchTime=58`, avec le motif `Argument`. Aucun calcul à
partir de `reversedPeriodTime` n'est autorisé.

### 20.1 Correction strictement bornée

`event-incidents-v5` hérite de toutes les règles V4 et ajoute uniquement cette forme de carton :

- `incidentType` doit être exactement `card` ;
- `time` doit porter exactement le marqueur technique observé `-5` et exige alors un `benchTime`
  compris entre 0 et 300 ;
- `benchTime` devient la minute normalisée et produit l'avertissement
  `PROVIDER_BENCH_CARD_MINUTE_USED` ;
- `incidentClass` et `reason` sont conservés uniquement pour un carton ;
- le snapshot, son hash, son heure et sa classification historique restent immuables ;
- toute autre valeur négative, y compris sur un `card`, demeure `SCHEMA_INCOMPATIBLE`.

La migration append-only V12 ajoute les colonnes facultatives `incident_class` et `reason`, autorise
la provenance `event-incidents-v5` et ne modifie aucune migration antérieure. La minute persistée
reste contrainte entre 0 et 300. Une réponse brute dédupliquée vers le snapshot 60 pourra ainsi
produire une nouvelle observation V5 sans reclasser V4, puis permettre l'unique appel
`EVENT_LINEUPS` restant dans une nouvelle campagne explicitement confirmée.

Les autres formes d'incidents restent volontairement inchangées. La fiche de règles de gestion par
type d'incident football annoncée par le propriétaire deviendra la source de vérité pour toute
évolution ultérieure ; aucune heuristique générale n'est introduite dans ce correctif.

### 20.2 Validation hors ligne et suite humaine

```text
J5_REAL_REARM_RETEST=PASS_SECOND_CAMPAIGN_STARTED
J5_REAL_SECOND_EVENT_ID=16483632
J5_REAL_SECOND_STATISTICS=EVENT_STATISTICS_V2_COMPLETE_SNAPSHOT_59_OBSERVATION_17
J5_REAL_SECOND_INCIDENTS=EVENT_INCIDENTS_V4_SCHEMA_INCOMPATIBLE_SNAPSHOT_60
J5_REAL_SECOND_LINEUPS=NOT_ATTEMPTED
J5_REAL_SECOND_PROVIDER_CALLS=2
J5_INCIDENT_CURRENT_PARSER=event-incidents-v5
J5_BENCH_CARD_NORMALIZED_MINUTE_SOURCE=benchTime
J5_BENCH_CARD_CORRECTION=PASS_OFFLINE_RETEST_REQUIRED
J5_STANDARD_TESTS=278
J5_INTEGRATION_TESTS=25
J5_FLYWAY_VERSION=12
J5_PROVIDER_CALLS_BY_AGENT=0
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le rapport minimisé détaillé est
`docs/validation/J5-REAL-V5-BENCH-CARD-CORRECTION-20260816.md`. Le prochain geste réel nécessite un
redémarrage, car la campagne V4 s'est correctement verrouillée en `FAILED_LOCKED`, puis une nouvelle
préparation et une nouvelle confirmation opérateur. Il doit vérifier le reparsing V5 du snapshot 60,
l'affichage de la minute 58, de la classe jaune et du motif `Argument`, puis l'appel unique des
compositions. Le reverrouillage local et l'arrêt final restent obligatoires. L'agent ne lit ni ne
modifie `.env` et n'exécute aucun appel fournisseur.

## 21. Qualification V5 et adoption de la fiche métier incidents V6

Les retests humains V5 ont levé le blocage du carton de banc et confirmé que les trois familles
peuvent être collectées dans une même campagne. Une collecte distincte sur Paris Saint-Germain —
Lens (`eventId=16281047`) a également vérifié la répétabilité manuelle à deux états du match :

| Moment de la collecte | Statistiques | Incidents | Compositions | Résultat |
|---|---:|---:|---:|---|
| première mi-temps | snapshot 61 | snapshot 62 | snapshot 63 | `COMPLETED_LOCKED`, trois appels sans retry |
| fin du match, rejeu manuel | snapshot 64 | snapshot 65 | snapshot 66 | `COMPLETED_LOCKED`, trois appels sans retry |

Le snapshot 62 contient un marqueur `period` compatible et le snapshot 65 contient 22 incidents,
affichés avec 42 signaux sur 42. Les compositions des deux équipes sont présentes dans les
snapshots 63 et 66. Cette seconde opération est bien un **rejeu** manuel explicitement préparé et
confirmé, pas un rejet : elle crée une nouvelle campagne et ne réutilise aucun claim consommé.

### 21.1 Fiche métier canonique

Le propriétaire a fourni une fiche couvrant tous les faits de jeu de football observables dans la
famille `EVENT_INCIDENTS` et a autorisé la correction de ses coquilles. La version relue est
conservée sous `docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md`. Les clarifications apportées ne
changent pas l'intention métier :

- les sous-sections du chapitre 10 sont `10.2` et `10.3` ;
- les libellés entrant/sortant des remplacements suivent `playerIn` / `playerOut` ;
- la position fournisseur `F` signifie `Forward` ;
- `owngoal` est la valeur brute observée et `ownGoal` la valeur canonique locale ;
- les vocabulaires VAR, penalty en cours de match et séance de tirs au but sont explicités ;
- les champs auxiliaires restent dans le brut et ne sont interprétés que lorsqu'une règle le
  demande explicitement.

### 21.2 Contrat `event-incidents-v6`

V6 reconnaît exactement huit types : `period`, `substitution`, `goal`, `card`, `injuryTime`,
`varDecision`, `inGamePenalty` et `penaltyShootout`. Il applique trois principes transverses :

1. les champs structurels (`incidentType` et minute effective) restent indispensables ;
2. une métadonnée d'affichage attendue mais facultative peut manquer : l'incident est conservé,
   la complétude devient `PARTIAL` et le chemin manquant est signalé ;
3. une contradiction atomique, une valeur hors vocabulaire ou un type inconnu produit
   `SCHEMA_INCOMPATIBLE` sans observation partielle.

Le modèle normalisé conserve désormais texte de période, blessure, passeur, origine du but,
longueur du temps additionnel, décision VAR, décision annulée, description et ordre de séance de
tirs au but. Les noms de participants peuvent être conservés même si le fournisseur omet leur
identifiant numérique ; cette absence réduit la complétude sans inventer d'identifiant. Pour un
carton, un temps fournisseur négatif exige `benchTime`, utilisé comme minute métier. La présence de
`benchTime` avec un `time` déjà non négatif est au contraire une contradiction. Pour une séance de
tirs au but sans `time` global, seule la minute de la première action auxiliaire peut servir de
repli, conformément à la fiche.

La migration append-only V13 ajoute uniquement les colonnes nécessaires et autorise
`event-incidents-v6`. Elle ne modifie ni les migrations ni les observations V1–V12. L'interface
affiche les nouvelles valeurs en colonnes `PASSEUR`, `DÉTAIL` et `ORDRE TAB`, en plus des colonnes
historiques.

### 21.3 État de validation V6 avant l'extension V7

```text
J5_V5_PSG_LENS_FIRST_HALF=PASS_SNAPSHOTS_61_62_63
J5_V5_PSG_LENS_FULL_TIME_REPLAY=PASS_SNAPSHOTS_64_65_66
J5_INCIDENT_RULES_DOCUMENT=docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md
J5_V6_PARSER=event-incidents-v6
J5_V6_FLYWAY_VERSION=13
J5_V6_OFFLINE_VALIDATION=PASS_324_STANDARD_27_INTEGRATION
J5_V6_FLYWAY_FRESH_INSTALL=V1_TO_V13_PASS
J5_V6_FLYWAY_UPGRADE=V12_TO_V13_PASS
J5_V6_REAL_RETEST=PASS_LENS_PSG
J5_PROVIDER_CALLS_BY_AGENT=0
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le passage à V6 n'a déclenché aucun transport pendant l'implémentation. Son retest a été réalisé
par l'opérateur avec une préparation, une confirmation et une action finale distinctes. Le
reverrouillage de la configuration et l'arrêt gracieux restent nécessaires avant toute clôture du
Work Order.

## 22. Qualification réelle V6 et correction ciblée V7

### 22.1 Lens — Paris Saint-Germain : V6 qualifié

Le retest humain V6 du match Lens — Paris Saint-Germain a terminé les trois familles. Les preuves
minimisées montrent notamment :

- les marqueurs de période `HT` et `FT` dans la colonne `DÉTAIL` ;
- trois minutes de temps additionnel en première période et cinq en seconde période ;
- le carton jaune de l'entraîneur lensois Dino Toppmöller avec le motif `Argument` ;
- les remplacements, cartons, but, passeur et scores applicables ;
- une campagne `COMPLETED_LOCKED` avec statistiques snapshot 64, incidents snapshot 65 et
  compositions snapshot 69, sans retry.

Le réemploi des snapshots 64 et 65 est une déduplication brute suivie d'une nouvelle observation
append-only V6 ; aucune classification historique n'a été réécrite.

### 22.2 Arsenal — Manchester City : cause du rejet V6

La campagne humaine suivante a normalisé les statistiques dans le snapshot 70, puis persisté les
61 763 octets du brut incidents dans le snapshot 71 avant parsing. V6 a classé la famille
`SCHEMA_INCOMPATIBLE`; la campagne s'est arrêtée après exactement deux appels fournisseur et n'a
pas tenté `EVENT_LINEUPS`, conformément au contrat sans retry.

L'analyse hors ligne du brut complet dénombre 23 incidents. V6 en accepte 22 ; le seul objet hors
vocabulaire est un remplacement à la minute 46 :

```text
incidentType=substitution
incidentClass=injury
injury=true
isHome=false
playerIn=Jack Grealish
playerOut=Jérémy Doku
```

Il s'agit bien du remplacement sur blessure de Jérémy Doku par Jack Grealish. Le rejet ne provient
ni des marqueurs `addedTime=999`, ni d'une minute négative, ni d'un nouveau type d'incident : il
provient uniquement de la classe `injury`, absente du vocabulaire V6 qui ne connaissait que
`regular` pour une substitution.

### 22.3 Contrat `event-incidents-v7`

V7 hérite sans modification de toutes les règles V6 et étend exclusivement le vocabulaire de
`substitution` :

1. `incidentClass="regular"` conserve le comportement V6 ;
2. `incidentClass="injury"` est accepté et conservé ;
3. `incidentClass="injury"` avec `injury=true` est cohérent ;
4. `incidentClass="injury"` avec `injury=false` est une contradiction atomique et reste
   `SCHEMA_INCOMPATIBLE` ;
5. l'absence de `injury` produit une observation `PARTIAL` sans booléen inventé.

Les participants `playerIn` et `playerOut` suivent les règles de complétude V6. La migration
append-only V14 autorise la provenance `event-incidents-v7` sans changer le modèle de données et
sans réécrire une observation ou un incident V6. Le payload complet fourni par l'opérateur est
validé hors ligne avec 23 incidents sur 23. Une régression de service prouve que cette famille
compatible est persistée avant l'appel unique des compositions.

### 22.4 État de validation

```text
J5_V6_LENS_PSG_REAL_RETEST=PASS_HT_FT_COACH_CARD_INJURY_TIME
J5_V6_ARSENAL_MANCHESTER_CITY=FAIL_INCIDENT_CLASS_INJURY_SNAPSHOT_71
J5_INCIDENT_CURRENT_PARSER=event-incidents-v7
J5_FLYWAY_VERSION=14
J5_V7_FULL_OPERATOR_PAYLOAD_OFFLINE=PASS_23_OF_23
J5_V7_ORDERED_SERVICE=PASS_CONTINUES_TO_LINEUPS
J5_V7_STANDARD_TESTS=330_PASS
J5_V7_INTEGRATION_TESTS=28_PASS
J5_V7_FLYWAY_UPGRADE=V13_TO_V14_PASS
J5_V7_REAL_RETEST=REQUIRED
J5_PROVIDER_CALLS_BY_AGENT=0
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

Le prochain test réel exige un redémarrage pour charger V7/V14, puis une nouvelle préparation,
une nouvelle phrase, un nouvel acquittement et une action finale distincte. L'agent n'exécute
aucun appel fournisseur et ne lit ni ne modifie `.env`.

Le rapport de validation minimisé de cette correction est conservé dans
`docs/validation/J5-REAL-V6-LENS-PASS-AND-V7-INJURY-SUBSTITUTION-CORRECTION-20260817.md`.

## 23. Amendement V8 — séances de tirs au but, cartons sans motif et poteau/barre transversale

### 23.1 Preuves opérateur rejouées hors ligne

Deux payloads complets fournis par l'opérateur ont été lus depuis leurs pièces locales et rejoués
hors ligne. Ils ne sont pas copiés dans le dépôt et aucun appel SofaScore n'est exécuté.

Le premier document contient 36 incidents :

- un marqueur terminal exact `incidentType="period"`, `text="PEN"`, `period="penalties"`,
  `isLive=false`, score `3–2`, `time=999` et `addedTime=999` ;
- neuf `penaltyShootout`, ordonnés par `sequence` de 1 à 9, sans minute globale ;
- pour chacun des neuf tirs, une minute effective valide dans la première entrée de
  `footballPassingNetworkAction`, de 121 à 146.

V7 rejette le seul champ `$.incidents[0].time`. V8 conserve les 36 incidents, garde les octets et
le SHA-256 du brut, omet `addedTime=999` et normalise la minute du marqueur `PEN` à 146, soit la
plus grande minute effective attestée dans la séance. Il ne convertit pas arbitrairement la
sentinelle en 90 ou 120 et ne l'accepte sur aucun autre contexte.

Le second document contient 16 incidents et trois cartons structurellement valides sans champ
`reason`. Le caractère facultatif du motif était déjà correctement modélisé par V6/V7 : les trois
motifs sont absents, persistables en `NULL` et rendus par `—`. Le rejeu complet a toutefois révélé
trois problèmes distincts sur `$.incidents[10].from`, `$.incidents[12].from` et
`$.incidents[14].from` : trois buts de classe `regular` portent la valeur brute `shot`. V8 accepte
uniquement ce couple, conserve `shot` dans le brut et omet cette valeur de l'origine spéciale
normalisée. Le payload complet passe alors avec 16 incidents sur 16 et les trois motifs absents.

### 23.2 Contrat `event-incidents-v8`

V8 conserve les règles V7 et ajoute les règles fermées suivantes :

1. `time=999` est normalisé uniquement sur le marqueur terminal exact `PEN/penalties`, inactif,
   avec score complet et au moins un tir au but possédant une minute effective valide ;
2. la minute du marqueur devient le maximum des minutes effectives de tous les
   `penaltyShootout` du document ;
3. un carton sans `reason` reste valide et son motif normalisé reste absent ; un motif présent mais
   inconnu reste `SCHEMA_INCOMPATIBLE` ;
4. `from="shot"` est admis uniquement avec `incidentType="goal"` et
   `incidentClass="regular"`, puis omis de l'origine spéciale normalisée ;
5. le triplet exact `incidentClass="missed"`, `description="Woodwork"`,
   `reason="woodwork"` est admis pour `inGamePenalty` et `penaltyShootout` ;
6. toute absence au sein de ce triplet, toute combinaison croisée, une classe `scored` ou une
   valeur inconnue reste incompatible.

La migration append-only `V15__j5_penalty_incident_variants.sql` ajoute uniquement
`event-incidents-v8` à la contrainte de provenance. Elle ne crée aucune colonne, maintient la borne
de minute `0..300`, conserve V1–V7 et ne modifie aucune observation ou ligne d'incident V1–V14.
Le test d'upgrade V14 → V15 conserve bit à bit les valeurs sélectionnées d'une observation V7 et
de sa substitution, puis accepte une observation V8 contenant le marqueur `PEN`, un tir au but
`Woodwork/woodwork` et un carton dont le motif SQL est `NULL`.

### 23.3 Qualification fonctionnelle humaine V8

Le lot opérateur `Tests incidents.zip`, conservé hors dépôt, a été relu intégralement : 48 captures
PNG, 3 854 455 octets, SHA-256
`D2A84F7DE91681426FD30E0EDAA4B6F02D4466DF1E2A8074905AD4C0760773BC`. Il contient sept campagnes
`COMPLETED_LOCKED` et l'échec V7 historique d'Al Orobah — Abha, ensuite corrigé et rejoué avec V8.
Les phrases de confirmation visibles ne sont ni reproduites ni conservées.

Trois campagnes du 2026-08-18 établissent le retest du parseur courant :

| Rencontre | Campagne | Incidents | Suite |
|---|---|---|---|
| IF Gnistan — Ilves (`15272817`) | `COMPLETED_LOCKED`, trois appels ; statistiques snapshot 118 `UNAVAILABLE` | snapshot 144 `COMPLETE`, observation 58 ; cartons sans motif rendus par `—` | compositions snapshot 145, observation 59, `83/83` |
| Al Orobah — Abha (`16251997`) | `COMPLETED_LOCKED`, trois appels ; statistiques snapshot 138, observation 53 | snapshot 139 reparsé par `event-incidents-v8`, observation 61, 36 incidents et `150/150` ; neuf tirs au but et `PEN` à 146 | compositions snapshot 148, observation 62, `81/81` |
| Samsunspor — Göztepe (`16483647`) | `COMPLETED_LOCKED`, trois appels ; statistiques snapshot 149, observation 63, `260/260` | snapshot 150, observation 64, `event-incidents-v8`, 27 incidents et `102/102` ; `inGamePenalty/missed/Woodwork` à la minute 58 | compositions snapshot 151, observation 65, `85/85` |

Les campagnes V7 du 2026-08-17 complètent la non-régression réelle du contrat hérité : France —
Maroc et Argentine — Autriche contiennent chacune un remplacement sur blessure et un
`inGamePenalty` raté ; Olympique Lyonnais — AC Sparta Praha et Al Wehda — Al-Shabab couvrent les
cartons, remplacements, VAR, penalties et périodes applicables. Les quatre campagnes ont terminé
les trois familles sans retry.

La variante `Woodwork/woodwork` réelle visible dans le lot concerne `inGamePenalty`. Sa prise en
charge identique pour `penaltyShootout` reste qualifiée par les régressions hors ligne de contrat,
de service et de persistance V15 ; aucune capture du lot ne prétend observer cette combinaison
exacte pendant une séance. Le lot observe néanmoins neuf `penaltyShootout` réels sous V8.

Deux campagnes V8 présentent des réponses `2xx` complètes pour les trois familles. Le statut
historique `J5_V8_PROVIDER_SCHEMA_VALIDATED_AT_RETEST=YES` est donc borné aux formes réelles
observées le 2026-08-18. Il ne
modifie pas `providerSchemaValidated=false` dans les manifestes synthétiques et ne constitue pas
une garantie contre une dérive future du fournisseur.

### 23.4 État historique au moment du retest V8

```text
J5_INCIDENT_PARSER_AT_V8_RETEST=event-incidents-v8
J5_FLYWAY_VERSION_AT_V8_RETEST=15
J5_V8_SHOOTOUT_OPERATOR_PAYLOAD=PASS_OFFLINE_36_OF_36
J5_V8_SHOOTOUT_ATTEMPTS=9_WITH_NESTED_MINUTES
J5_V8_PEN_NORMALIZED_MINUTE=146
J5_V8_CARD_OPERATOR_PAYLOAD=PASS_OFFLINE_16_OF_16
J5_V8_CARDS_WITHOUT_REASON=3_PRESERVED_AS_ABSENT
J5_V8_REGULAR_GOAL_SHOT_ORIGINS=3_RAW_ONLY
J5_V8_WOODWORK_FAMILIES=inGamePenalty,penaltyShootout
J5_V8_ORDERED_SERVICE=PASS_CONTINUES_TO_LINEUPS
J5_V8_FLYWAY_UPGRADE=V14_TO_V15_PASS
J5_V8_STANDARD_TESTS=340_PASS
J5_V8_INTEGRATION_TESTS=29_PASS
J5_V8_REAL_RETEST=PASS_THREE_COMPLETED_LOCKED_CAMPAIGNS
J5_V8_REAL_CARD_WITHOUT_REASON=PASS_SNAPSHOT_144
J5_V8_REAL_SHOOTOUT=PASS_SNAPSHOT_139_36_INCIDENTS_150_OF_150_PEN_146
J5_V8_REAL_WOODWORK_INGAME=PASS_SNAPSHOT_150_27_INCIDENTS_102_OF_102
J5_V8_WOODWORK_SHOOTOUT=PASS_OFFLINE_CONTRACT_AND_PERSISTENCE
J5_V7_INHERITED_REAL_REQUALIFICATION=PASS
J5_PROVIDER_CALLS_BY_AGENT=0
J5_V8_PROVIDER_SCHEMA_VALIDATED_AT_RETEST=YES
J5_V8_PROVIDER_SCHEMA_VALIDATION_SCOPE=BOUNDED_REAL_CAMPAIGNS_2026_08_18
J5_LOCAL_CONFIGURATION_RELOCKED=NOT_EVIDENCED
J5_APPLICATION_STOPPED=YES_LOCAL_PORT_AND_PROCESS_CHECK
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

À ce stade du retest V8, aucune nouvelle campagne fournisseur ne paraissait requise. Les captures
ne montraient toutefois pas le reverrouillage de la configuration après la dernière campagne. Un
contrôle local en lecture seule confirmait l'arrêt : aucun listener sur le port 8087 et aucun
processus Java correspondant au laboratoire. L'événement ultérieur consigné en section 24 remplace
cette décision de clôture par l'obligation d'un retest humain V9. L'agent n'exécute aucun appel
fournisseur et ne lit ni ne modifie `.env`.

Le rapport minimisé est conservé dans
`docs/validation/J5-REAL-V8-PENALTY-INCIDENT-VARIANTS-20260818.md`.

## 24. Nouvelle dérive réelle et correction V9 du carton de banc

### 24.1 Campagne Cardiff City — Wrexham

Après le traitement du lot V8, l'opérateur a fourni deux nouvelles captures et la transcription
JSON complète de la famille incidents pour Cardiff City — Wrexham (`16391145`). La campagne a
traité les statistiques dans le snapshot 161, puis conservé le snapshot incidents 162 en HTTP
`200` avant que `event-incidents-v8` ne retourne `SCHEMA_INCOMPATIBLE`. Le contrôle est passé à
`FAILED_LOCKED` après exactement deux appels ; il n'y a eu ni retry ni tentative compositions.

L'inspection complète des 20 incidents isole un seul problème bloquant :
`$.incidents[19].reason="Other reason"`. Le même objet est un carton de banc portant
`time=-5`, `benchTime=90` et `benchAddedTime=9`. Le bloc auxiliaire `manager` est inconnu mais non
bloquant ; le nom d'acteur est déjà porté par `playerName`. La persistance du brut avant parsing et
l'arrêt de la séquence sont conformes au Work Order.

### 24.2 Contrat et migration

`event-incidents-v9` conserve V8 et ajoute uniquement :

1. la valeur attestée `Other reason` au vocabulaire fermé des motifs de carton ;
2. `benchAddedTime` comme temps additionnel normalisé uniquement sur un `card` avec `time` négatif,
   `benchTime` présent et `addedTime` absent.

Dans la forme observée, la minute devient `90+9`. Un motif futur inconnu, un
`benchAddedTime` sur un autre type ou une combinaison temporelle contradictoire reste
`SCHEMA_INCOMPATIBLE`. La migration append-only
`V16__j5_bench_card_other_reason.sql` autorise la provenance V9 sans nouvelle colonne ni
réécriture de l'historique V1–V15.

Le payload opérateur complet, conservé hors dépôt, passe sous V9 avec 20 incidents sur 20,
`COMPLETE · 100%` et zéro problème de schéma. Les tests versionnés utilisent une forme synthétique
minimale et confirment également l'héritage de `Woodwork/woodwork` pour `penaltyShootout`.

```text
J5_V9_INCIDENT_PARSER=event-incidents-v9
J5_V9_FLYWAY_VERSION=16
J5_CARDIFF_STATISTICS=SNAPSHOT_161_COMPLETE
J5_CARDIFF_INCIDENTS=SNAPSHOT_162_V8_SCHEMA_INCOMPATIBLE
J5_CARDIFF_PROVIDER_CALLS=2
J5_CARDIFF_RETRY=0
J5_CARDIFF_LINEUPS=NOT_ATTEMPTED
J5_V9_FULL_OPERATOR_PAYLOAD=PASS_OFFLINE_20_OF_20
J5_V9_NORMALIZED_BENCH_CARD_MINUTE=90_PLUS_9
J5_V9_FLYWAY_UPGRADE=V15_TO_V16_PASS
J5_V9_STANDARD_TESTS=344_PASS
J5_V9_INTEGRATION_TESTS=30_PASS
J5_PROVIDER_CALLS_BY_AGENT=0
J5_V9_PROVIDER_SCHEMA_VALIDATED_AT_RETEST=YES
J5_V9_PROVIDER_SCHEMA_VALIDATION_SCOPE=BOUNDED_REAL_V9_CARDIFF_2026_08_18
J5_V9_REAL_RETEST=PASS_COMPLETED_LOCKED_THREE_CALLS
J5_V9_REAL_INCIDENTS=SNAPSHOT_162_OBSERVATION_77_COMPLETE_20_OF_20
J5_V9_REAL_LINEUPS=SNAPSHOT_165_OBSERVATION_78_COMPLETE_85_OF_85
J5_V9_FUNCTIONAL_EVIDENCE=5_PNG_464810_BYTES
J5_LOCAL_CONFIGURATION_RELOCKED=NOT_EVIDENCED
J5_APPLICATION_STOPPED=YES_POST_V9_RETEST_LOCAL_PORT_AND_PROCESS_CHECK
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

### 24.3 Retest humain V9

Les cinq captures opérateur reçues après application de V16 établissent :

- préparation locale à `AWAITING_CONFIRMATION` sans transport ;
- campagne `COMPLETED_LOCKED` après exactement trois appels ordonnés, sans retry ;
- statistiques snapshot 161, observation 75, `COMPLETE · 100%` ;
- incidents snapshot 162 reparsé dans l'observation append-only 77, `COMPLETE · 100%`, avec les 20
  incidents visibles et le carton `Other reason` normalisé à `90+9` ;
- compositions snapshot 165, observation 78, `event-lineups-v2`, `COMPLETE · 100% · 85/85`, avec
  les deux formations et les titulaires/remplaçants visibles.

La déduplication des snapshots 161 et 162 préserve leur preuve brute et leur classification
historique ; le résultat V9 est porté par la nouvelle observation normalisée. Le troisième appel
confirme que la correction incidents ne bloque plus `EVENT_LINEUPS`. La phrase de confirmation
ponctuelle visible dans la première capture n'est ni reproduite ni conservée.

À l'issue de ce retest, le schéma V9 passait à
`J5_V9_PROVIDER_SCHEMA_VALIDATED_AT_RETEST=YES` pour la portée réelle bornée de cette campagne et
des campagnes antérieures. Les fixtures synthétiques restaient exclues de cette qualification. Un
contrôle local postérieur au retest ne trouvait aucun listener 8087 ni processus Java du
laboratoire. La section 25 consigne la dérive ultérieure qui rend ce statut historique.

### 24.4 Décision de clôture

À ce stade historique, le verrou fonctionnel V9 était levé et l'application arrêtée. Les évolutions
ultérieures décrites en sections 25 et 26 imposent désormais un retest du parseur courant V11 avant
le reverrouillage des paramètres locaux, la confirmation de l'état `LOCKED` après redémarrage et
l'arrêt gracieux final. Le Work Order reste actif et non archivable.

Le rapport minimisé est conservé dans
`docs/validation/J5-REAL-V9-BENCH-CARD-OTHER-REASON-20260818.md`.

## 25. Nouvelle dérive V9 et correction V10 de l'origine d'un but régulier

### 25.1 Campagne réelle observée

Trois nouvelles captures et la transcription JSON du snapshot incidents ont été fournies le
2026-08-18. La campagne concerne l'identifiant fournisseur `16251993` :

- les statistiques sont complètes dans le snapshot 178 et l'observation 91, `4/4` ;
- le snapshot incidents 179 est reçu en HTTP `200`, fait 6 423 octets et est inspecté localement
  avec le parseur `event-incidents-v9` ;
- V9 classe le document `SCHEMA_INCOMPATIBLE` ;
- la campagne passe à `FAILED_LOCKED` après exactement deux appels, sans retry ;
- `EVENT_LINEUPS` n'est pas tenté et aucune observation incidents partielle n'est créée.

Le snapshot incidents porte le SHA-256 brut
`6ca4d7e8dc7e1409007e465b1541fa517c338510857a974fd4b1e57b9fb70585`.
Les trois captures, conservées hors dépôt, représentent 366 639 octets. Leur inventaire et leurs
empreintes sont consignés dans le rapport V10 sans reproduire le payload ni une phrase de
confirmation.

### 25.2 Cause et contrat V10

Le payload contient sept incidents : deux périodes et cinq buts. Les cinq buts portent tous
`incidentType="goal"`, `incidentClass="regular"` et `from="regular"`. V9 rejette exactement les
cinq chemins `from`, car la valeur ne fait pas partie des origines spéciales et seule la variante
brute historique `from="shot"` était neutralisée pour un but régulier.

`event-incidents-v10` hérite de V9 et ajoute uniquement cette combinaison cohérente. La valeur
redondante reste dans le brut, n'est pas persistée comme origine spéciale et produit
`PROVIDER_REGULAR_GOAL_ORIGIN_OMITTED`. Toute combinaison avec une classe `penalty` ou `ownGoal`,
ainsi que toute nouvelle valeur inconnue, demeure incompatible.

`V17__j5_regular_goal_origin.sql` autorise V10 sans colonne supplémentaire. Le test V16 → V17
conserve à l'identique une observation et un incident V9, refuse V10 avant migration, puis autorise
une observation V10 après exactement une migration.

### 25.3 Preuves hors ligne

Le harnais ponctuel supprimé après usage a relu la transcription opérateur complète depuis sa
pièce locale :

- V9 : cinq problèmes, exactement sur `$.incidents[1|2|4|5|6].from` ;
- V10 : `PARSED`, 7 incidents sur 7, `COMPLETE · 100% · 24/24`, sept avertissements attendus et
  zéro problème ;
- transcription : 7 616 octets, SHA-256
  `2D3D100A25F3850DF23327228BCB949D0EBDC33B3EBEBF16431E01174B21CB23` ;
- aucun appel fournisseur par l'agent.

Les régressions permanentes couvrent le parseur V10, les tuples croisés, les valeurs inconnues, les
contrats V8/V9, la poursuite du service jusqu'aux compositions et la provenance V10.

```text
J5_V10_PARSER=event-incidents-v10
J5_FLYWAY_VERSION_AT_V10=17
J5_V10_TRIGGER=SNAPSHOT_179_FIVE_REGULAR_GOALS_FROM_REGULAR
J5_V10_FULL_OPERATOR_PAYLOAD=PASS_OFFLINE_7_OF_7_COMPLETE_24_OF_24
J5_V10_FLYWAY_UPGRADE=V16_TO_V17_PASS
J5_V10_STANDARD_TESTS=348_PASS
J5_V10_INTEGRATION_TESTS=31_PASS
J5_V10_REAL_RETEST=SUPERSEDED_BY_V11_RETEST_REQUIRED
J5_PROVIDER_SCHEMA_VALIDATED=NO
J5_PROVIDER_SCHEMA_VALIDATION_REASON_AT_V10=V9_REJECTED_REAL_SNAPSHOT_179
J5_LOCAL_CONFIGURATION_RELOCKED=NOT_EVIDENCED
J5_APPLICATION_STOPPED=YES_POST_V11_IMPLEMENTATION_LOCAL_PORT_AND_PROCESS_CHECK
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

### 25.4 Décision de clôture

Au stade V10, le correctif était prêt pour qualification humaine, mais le Work Order ne pouvait pas
être validé ni archivé. La séquence alors prévue était :

1. redémarrer avec V17 pour sortir du verrou `FAILED_LOCKED` ;
2. préparer et confirmer une nouvelle campagne sur l'identité liée à `16251993` ;
3. vérifier trois appels ordonnés, incidents V10 complets et compositions effectivement atteintes ;
4. arrêter la session réelle ;
5. reverrouiller la configuration locale, redémarrer pour confirmer `LOCKED`, puis arrêter
   gracieusement l'instance de contrôle.

Le contrôle local effectué après le diagnostic ne trouve aucun listener 8087 ni processus Java du
laboratoire. Il ne constitue pas la preuve du reverrouillage de `.env`, que l'agent n'a ni lu ni
modifié.

Le rapport minimisé historique V10 est conservé dans
`docs/validation/J5-REAL-V10-REGULAR-GOAL-ORIGIN-20260818.md`.

## 26. Observation V11 — motif de carton `Off the ball foul` (2026-08-18)

### 26.1 Preuve et limite de portée

Le propriétaire fournit directement un bloc JSON attestant un incident `card/yellow` à la minute
77 pour Facundo Mallo, côté extérieur, avec `reason="Off the ball foul"`. Cette pièce établit la
forme du champ et son sens métier — obstruction ou faute commise loin du ballon — mais ne fournit
ni identifiant de snapshot local, ni statut HTTP, ni hash brut, ni trace d'une campagne complète.
Aucune de ces métadonnées n'est donc déduite.

Sous V10, le vocabulaire fermé rejette exactement `$.incidents[0].reason`. Le parseur V11 hérite de
toutes les règles V10 et ajoute uniquement le libellé exact. Il le conserve comme raison normalisée
et comme motif affiché, sans le remplacer par une traduction. Toute autre valeur inconnue demeure
`SCHEMA_INCOMPATIBLE`.

### 26.2 Implémentation et persistance

Le service courant utilise `event-incidents-v11` dans la preuve brute comme dans la provenance de
l'observation append-only. La régression de service injecte le carton observé et vérifie la
poursuite `STATISTICS → INCIDENTS → LINEUPS`, exactement trois transports et aucun retry.

`V18__j5_off_the_ball_card_reason.sql` étend uniquement la contrainte des versions de parseur. Le
test V17 → V18 refuse V11 avant migration, applique une migration, compare une observation et un
incident V10 historiques champ par champ, puis persiste le carton V11 avec son motif exact.

### 26.3 Qualification automatisée à cette étape

```text
J5_INCIDENT_CURRENT_PARSER_AT_THIS_STAGE=event-incidents-v11
J5_FLYWAY_VERSION_AT_THIS_STAGE=18
J5_V11_TRIGGER=INLINE_CARD_REASON_OFF_THE_BALL_FOUL
J5_V11_BUSINESS_MEANING=OBSTRUCTION_OR_OFF_BALL_FOUL
J5_V11_OBSERVED_SHAPE=PASS_OFFLINE_COMPLETE_3_OF_3
J5_V11_HISTORICAL_V10_COMPARISON=PASS_REJECTS_REASON_PATH
J5_V11_UNKNOWN_REASON_POLICY=SCHEMA_INCOMPATIBLE
J5_V11_ORDERED_SERVICE=PASS_THREE_CALLS_CONTINUES_TO_LINEUPS
J5_V11_FLYWAY_UPGRADE=V17_TO_V18_PASS_APPEND_ONLY
J5_V11_STANDARD_TESTS=351_PASS
J5_V11_INTEGRATION_TESTS=32_PASS
J5_V11_PROVIDER_CALLS_BY_AGENT=0
J5_V11_REAL_RETEST_AT_THIS_STAGE=REQUIRED
J5_PROVIDER_SCHEMA_VALIDATED_AT_THIS_STAGE=NO
J5_PROVIDER_SCHEMA_VALIDATION_REASON_AT_THIS_STAGE=V9_REJECTED_REAL_SNAPSHOT_179_AND_V11_NOT_RETESTED
J5_LOCAL_CONFIGURATION_RELOCKED=NOT_EVIDENCED
J5_APPLICATION_STOPPED=YES_POST_V11_IMPLEMENTATION_LOCAL_PORT_AND_PROCESS_CHECK
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

### 26.4 Décision de clôture alors applicable

La section 26 remplace la séquence de clôture V10 de la section 25.4. Le correctif V11 est prêt
hors ligne, mais le Work Order reste actif. La prochaine séquence requise est :

1. redémarrer avec V18 ;
2. préparer et confirmer une nouvelle campagne réelle distincte ;
3. vérifier trois appels ordonnés, incidents V11 complets et compositions atteintes ;
4. vérifier le motif inchangé si un carton `Off the ball foul` est présent ;
5. arrêter la session réelle ;
6. reverrouiller la configuration locale, redémarrer pour confirmer `LOCKED`, puis arrêter
   gracieusement l'instance de contrôle.

Le rapport minimisé courant est
`docs/validation/J5-REAL-V11-OFF-BALL-CARD-REASON-20260818.md`.

## 27. Retest réel V11 et session combinée J4/J5 (2026-08-18)

### 27.1 Qualification humaine reçue

Trois captures opérateur attestent une campagne V11 complète sur Al Tai — Al-Qadsiah, identifiant
fournisseur `16251993`, UUID canonique `b72dcb5e-98c7-39e4-ba59-9ab491105dc9` :

- `COMPLETED_LOCKED`, trois appels ordonnés et aucun retry ;
- statistiques snapshot 178, observation 91, `COMPLETE · 4/4` ;
- incidents `event-incidents-v11`, snapshot brut dédupliqué 179, observation append-only 93,
  sept incidents et `COMPLETE · 24/24` ;
- les cinq buts `regular/from=regular` auparavant bloquants sont visibles sans origine spéciale
  inventée ;
- compositions snapshot 182, observation 94, confirmées et `COMPLETE · 67/67`.

Cette campagne qualifie le correctif V10 sous le parseur courant V11 et atteint effectivement les
compositions. Elle ne contient aucun carton `reason="Off the ball foul"`; cette nouvelle valeur
reste donc à qualifier réellement. Le statut fournisseur devient `YES` uniquement dans la portée
bornée de l'événement 16251993 sans cette variante.

Les trois PNG restent hors dépôt et totalisent 321 276 octets :

| Preuve | Taille | SHA-256 |
|---|---:|---|
| campagne et résultat | 133 739 | `0e145ae54953be4e09bb4d1ee50c2526943cf48b658e868efce8e1308f9b8e39` |
| statistiques et incidents | 107 320 | `5f6b1d482a10c106003f6cd38ec9cd0e1b95cacf594ce6e94b8dfc028f9dd74c` |
| compositions | 80 217 | `c4a09ed3fa1dd8dae6b93e51811df15e615ad624af1878a7745dbf62d5d32199` |

Le rapport V11 contient les hashes bruts et normalisés affichés pour les trois familles. Aucun
payload complet, phrase de confirmation ou contenu `.env` n'est recopié.

### 27.2 Amendement — J4 phase 2 puis J5 sans redémarrage

Le propriétaire demande de supprimer le cycle build/démarrage J4, arrêt, changement de
configuration, nouveau build et nouveau démarrage J5. Cet amendement autorise dans le même
processus uniquement la combinaison suivante :

```properties
SOFASCORE_ENABLED=true
SOFASCORE_J3_QUALIFICATION_ENABLED=false
SOFASCORE_J4_EVENT_DETAILS_QUALIFICATION_ENABLED=true
SOFASCORE_J4_EVENT_DETAILS_PHASE2_ENABLED=true
SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true
SOFASCORE_BASE_URL=https://www.sofascore.com
SOFASCORE_ALLOWED_ENDPOINTS=EVENT_DETAILS,EVENT_STATISTICS,EVENT_INCIDENTS,EVENT_LINEUPS
```

J3 reste exclusif. J4 phase 1 ne peut pas partager J5. L'ensemble des endpoints doit être exactement
l'union des quatre familles ; une famille absente ou supplémentaire fait échouer la validation de
configuration. Les modes J4 seul et J5 seul restent supportés avec leurs ensembles historiques.

Les contrôles J4 et J5 restent indépendants : le POST **Arrêt global J4** verrouille les deux
contrôles J4 sans modifier J5. Une régression prépare J5 après cet arrêt dans la même instance et
confirme que J4 reste `STOPPED_LOCKED`.

Un coordinateur commun est injecté dans les trois services réels J4/J5. Il sérialise les sections
de transport, maintient le verrou pendant l'échange HTTP et applique le délai minimal de trois
secondes entre deux débuts de requête, y compris au passage J4→J5. Il n'ajoute ni retry, ni boucle,
ni confirmation commune, ni campagne automatique.

### 27.3 Qualification automatisée avant le retest combiné

```text
J4_J5_COMBINED_CONFIGURATION=PASS_EXACT_UNION
J4_J5_COMBINED_J3_POLICY=REMAINS_EXCLUSIVE
J4_J5_COMBINED_J4_PHASE_1_POLICY=REJECTED
J4_J5_COMBINED_CONTROLS=PASS_J5_PREPARES_AFTER_J4_GLOBAL_STOP
J4_J5_SHARED_COORDINATOR=PASS_SERIALIZED_AND_MINIMUM_DELAY_SHARED
J4_J5_TARGETED_TESTS=38_PASS
J5_CURRENT_STANDARD_TESTS=358_PASS
J5_CURRENT_INTEGRATION_TESTS=32_PASS
J5_FLYWAY_FRESH_SCHEMA=V18_PASS_POSTGRESQL_18_4
J5_FLYWAY_UPGRADE=V17_TO_V18_PASS
J5_PROVIDER_CALLS_BY_AGENT=0
J5_V11_REAL_RETEST=PASS_EVENT_16251993_THREE_CALLS_TO_LINEUPS
J5_OFF_THE_BALL_CARD_REAL_RETEST_AT_THIS_STAGE=REQUIRED
J4_J5_COMBINED_HUMAN_RETEST_AT_THIS_STAGE=REQUIRED
J5_PROVIDER_SCHEMA_VALIDATED_AT_THIS_STAGE=YES
J5_PROVIDER_SCHEMA_VALIDATION_SCOPE_AT_THIS_STAGE=BOUNDED_V11_EVENT_16251993_WITHOUT_OFF_BALL_CARD
J5_LOCAL_CONFIGURATION_RELOCKED_AT_THIS_STAGE=NOT_EVIDENCED
J5_APPLICATION_STOPPED_AT_THIS_STAGE=YES_POST_COMBINED_IMPLEMENTATION_LOCAL_PORT_AND_PROCESS_CHECK
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED_AT_THIS_STAGE=NO
```

Commandes exécutées :

```text
.\mvnw.cmd --offline -q "-Dtest=SofascorePropertiesTest,J4EventDetailsPhase2QualificationPolicyTest,J5RealQualificationPolicyTest,J4J5CombinedQualificationSessionTest,J4J5ProviderRequestCoordinatorTest,J4RealEventDetailsPhase1ServiceTest,J4RealEventDetailsPhase2ServiceTest,J5RealEventDataServiceTest" test
PASS — 38 tests

.\mvnw.cmd --offline clean verify
PASS — 358 tests, 0 échec, 0 erreur

.\mvnw.cmd --offline -q -Pintegration-tests verify
PASS — 358 tests standards + 32 tests d'intégration, 0 échec
```

Maven ne contacte pas le fournisseur. Le seul avertissement est l'auto-attachement Mockito/Byte
Buddy sous Java 25.

### 27.4 Décision alors applicable

La section 27 remplace la décision de la section 26.4. Le Work Order demeure actif. La séquence
restante est :

1. démarrer une seule fois avec la configuration combinée ;
2. exécuter une campagne J4 phase 2 sur l'identifiant choisi ;
3. appliquer si souhaité l'arrêt global J4, puis vérifier que J5 reste préparée sans redémarrage ;
4. exécuter la campagne J5 sur l'identité créée, idéalement avec un carton
   `reason="Off the ball foul"` ;
5. vérifier le motif inchangé, les trois appels ordonnés et l'accès aux compositions ;
6. arrêter l'application, remettre toutes les voies fournisseur à l'état bloqué ;
7. redémarrer pour constater les bloqueurs puis arrêter gracieusement l'instance finale.

Le rapport du mode combiné est
`docs/validation/J4-J5-COMBINED-QUALIFICATION-SESSION-20260818.md`.

## 28. Qualification réelle combinée et motif `Off the ball foul` (2026-08-18)

### 28.1 Preuve fonctionnelle reçue

Onze captures et l'attestation explicite du propriétaire établissent un parcours J4 phase 2 puis
J5 dans le même démarrage sur Barracas Central — Rosario Central, identifiant fournisseur
`16671566`, UUID canonique `da075869-34d4-3d42-83d2-613583691845` :

- J4 phase 2 termine `COMPLETED_LOCKED` après exactement un appel fournisseur ; le détail local
  `event-details-v2` est persisté dans le snapshot 183, reçu à
  `2026-08-18T11:49:43.742731Z`, avec 10 074 octets et le SHA-256 brut
  `08a3f4236690a468b65018239ba77ebdedb456adfa17ceb20c1019fd68d61d37` ;
- la même identité canonique expose ensuite la préparation J5 sans rebuild ni redémarrage ;
- J5 termine `COMPLETED_LOCKED` après exactement trois appels ordonnés, sans retry ;
- statistiques : snapshot 184, observation 95, 25 408 octets, `COMPLETE · 262/262` ;
- incidents : `event-incidents-v11`, snapshot 185, observation 96, 27 796 octets, 18 incidents,
  `COMPLETE · 69/69` ;
- le carton jaune extérieur de Facundo Mallo à la minute 77 conserve exactement le motif
  `Off the ball foul` ;
- compositions : snapshot 186, observation 97, 74 159 octets, confirmées et
  `COMPLETE · 95/95`.

Les écrans d'arrêt global montrent ensuite J4 et J5 en `STOPPED_LOCKED`. Ils qualifient les verrous
terminaux après les campagnes ; l'indépendance spécifique « J5 préparée après arrêt global J4 »
reste couverte par la régression automatisée de la section 27.3, car l'arrêt J4 visible a été
appliqué après le parcours fonctionnel.

Les onze PNG restent hors dépôt et totalisent 1 128 540 octets. Leur inventaire SHA-256 complet est
conservé dans
`docs/validation/J4-J5-COMBINED-QUALIFICATION-SESSION-20260818.md`. La capture de préparation
contient une phrase de confirmation ponctuelle : cette phrase n'est ni transcrite ni conservée dans
le dépôt. Le rapport V11 détaille les hashes bruts et normalisés des trois familles J5.

### 28.2 Décision fonctionnelle

```text
J5_PROVIDER_SCHEMA_VALIDATED=YES
J5_PROVIDER_SCHEMA_VALIDATION_SCOPE=BOUNDED_V11_EVENTS_16251993_AND_16671566_INCLUDING_OFF_BALL_CARD
J5_OFF_THE_BALL_CARD_REAL_RETEST=PASS_EVENT_16671566_SNAPSHOT_185
J4_J5_COMBINED_HUMAN_RETEST=PASS_REAL_EVENT_16671566
J4_J5_COMBINED_J4=COMPLETED_LOCKED_ONE_CALL_SNAPSHOT_183
J4_J5_COMBINED_J5=COMPLETED_LOCKED_THREE_CALLS_SNAPSHOTS_184_185_186
J5_LOCAL_CONFIGURATION_RELOCKED=NOT_EVIDENCED
J5_APPLICATION_STOPPED=YES_POST_REAL_COMBINED_CAMPAIGN_LOCAL_PORT_AND_PROCESS_CHECK
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO_PENDING_CONFIGURATION_RELOCK
```

Le test réel combiné et le test réel du motif V11 sont concluants. Un contrôle local en lecture
seule effectué après réception des preuves ne trouve ni listener sur `127.0.0.1:8087`, ni processus
Java dont la ligne de commande correspond au laboratoire. L'application de qualification est donc
arrêtée à cet instant, sans lecture de `.env`.

### 28.3 Seule séquence restante avant archivage

La section 28 remplace la décision de la section 27.4. Le Work Order demeure actif uniquement pour
la preuve de sûreté locale suivante :

1. remettre l'activation globale SofaScore et les opt-ins J3, J4 phase 1, J4 phase 2 et J5 à
   `false`, puis vider localement l'origine fournisseur ainsi que la liste des endpoints autorisés,
   sans publier le contenu de `.env` ;
2. redémarrer une fois l'application avec cette configuration bloquée ;
3. constater sur les contrôles J4 et J5 que les préparations réelles sont verrouillées et conserver
   une preuve minimisée de ce constat ;
4. arrêter gracieusement cette instance de contrôle et confirmer l'absence de listener et de
   processus du laboratoire ;
5. passer le Work Order à `VALIDATED`, déplacer le document dans `completed`, puis effectuer la
   clôture Git seulement après revue humaine.

Tant que cette séquence n'est pas attestée, `LOCAL_CONFIGURATION_RELOCKED=NOT_EVIDENCED` et
`WORK_ORDER_ARCHIVABLE=NO_PENDING_CONFIGURATION_RELOCK` restent les décisions correctes.

## 29. Nouvelle dérive V11 et correction V12 d'une séance entièrement non minutée (2026-08-18)

### 29.1 Campagne réelle observée

Le propriétaire signale une campagne sur l'événement `16691018`. La première famille répond
HTTP `404` et est correctement conservée comme indisponible : snapshot 188, observation 98,
44 octets, reçu à `2026-08-18T12:28:23.192329Z`, SHA-256 brut
`61b6f2399d5cd9251af376fddf2243e9ea802fbcda179698437c344ef6db8b32` et SHA-256 normalisé
`ae831d38de63b56c6df671f23d5c021a54e4db8e7a2d73615e4bee2bfb97ede6`.

La réponse incidents HTTP `200` est persistée avant parsing dans le snapshot 189 : 40 412 octets,
reçu à `2026-08-18T12:28:26.143477Z`, parseur historique `event-incidents-v11`, SHA-256 brut
`421620124f07c1a01b1b1187741bb6389874957c3889658af725aaad8cd63e49`.
L'inspection locale est horodatée `2026-08-18T12:29:22.126842200Z`. V11 classe le document
`SCHEMA_INCOMPATIBLE`; la campagne termine correctement `FAILED_LOCKED` après exactement deux
appels, sans retry et sans tenter les compositions.

Les trois captures restent hors dépôt :

| Preuve | Taille | SHA-256 du PNG |
|---|---:|---|
| campagne et résultat arrêté | 133 770 octets | `6ca0d20de1684f4af03a98221c5616b67ec43a48c503f0458800cb17f0060d59` |
| indisponibilité statistiques et absences locales restantes | 74 327 octets | `68fe4dd6c891e7f2f9ae6577628e93dac672ffbe98230c026478ef1c48bd1712` |
| inspection brute du snapshot incidents | 125 829 octets | `1e2cfbccc6efb6e19021bdb0a1b0d168a23af21380793d946dbd4e12f9b825f2` |

La transcription complète fournie séparément reste elle aussi hors dépôt : 46 765 octets,
SHA-256 `d87da6737d52fbadcc073770b28a3c02084d61bcf7b0945485241a7f34ae3cc8`.
Elle n'est ni copiée dans les fixtures ni reproduite dans ce Work Order.

### 29.2 Cause exacte

La réponse contient 33 incidents, dont un marqueur terminal `PEN` et quatorze
`penaltyShootout`. Les quatorze tentatives omettent toutes le champ global `time` et le tableau
`footballPassingNetworkAction`; le marqueur conserve les sentinelles `time=999` et
`addedTime=999`. Sept tentatives `missed` omettent simultanément `reason` et `description`.

Le rejeu diagnostique V11 produit exactement quinze problèmes temporels : un sur la minute du
marqueur `PEN` et quatorze sur la minute effective des tentatives. Aucun problème ne cible
`reason` ni `description`. Leur absence simultanée était déjà une absence métier compatible ; V12
la couvre néanmoins explicitement par régression pour les deux familles `inGamePenalty` et
`penaltyShootout` afin d'empêcher une régression future.

### 29.3 Contrat V12 et migration V19

`event-incidents-v12` n'autorise pas une minute absente de manière générale. Il exige
simultanément :

1. un marqueur exact `period/PEN/penalties`, inactif, avec `time=999`, `addedTime=999` et score
   final complet ;
2. un marqueur `FT` ou `ET` possédant une minute effective valide ;
3. une séance où toutes les tentatives omettent à la fois `time` et
   `footballPassingNetworkAction`, sans mélange avec une tentative minutée ;
4. pour chaque tentative, une classe `scored` ou `missed`, un côté, un score complet et une
   séquence positive ;
5. des séquences uniques et contiguës de `1` à `N` ;
6. un score porté par la dernière séquence identique au score du marqueur `PEN`.

Dans ce seul contexte, la minute normalisée du marqueur et des tentatives reste absente. Elle est
persistée en `NULL`, rendue par `—` et accompagnée de l'avertissement
`PROVIDER_SHOOTOUT_MINUTE_ABSENT`; l'ordre de passage n'est jamais converti en temps de jeu. Un
`inGamePenalty` sans minute, une séance mixte, isolée, active, lacunaire ou contradictoire reste
`SCHEMA_INCOMPATIBLE`.

Pour un penalty `missed`, l'absence simultanée de `reason` et `description` produit `PARTIAL` sans
motif inventé, pour `inGamePenalty` comme pour `penaltyShootout`. Si ces attributs sont fournis,
les tuples fermés historiques, y compris `Woodwork/woodwork`, restent exigés.

`V19__j5_unminuted_terminal_shootout.sql` est append-only. Elle autorise la provenance V12 et rend
la colonne `event_incident.minute` nullable sous une contrainte réservant `NULL` aux seuls
`penaltyShootout` et marqueurs `period/PEN`, avec temps additionnel normalisé absent. Elle ne
réécrit aucune observation ni aucun incident V1–V18.

### 29.4 Preuves hors ligne acquises

Le rejeu du payload opérateur complet sous V12 donne : 33 incidents conservés, quatorze tirs au
but sans minute, sept tirs ratés sans raison ni description, quinze avertissements temporels,
zéro problème de schéma et une complétude `PARTIAL`. Le hash de la pièce relue est vérifié avant
parsing. Le service simulé couvre ensuite `STATISTICS` indisponible, incidents V12 partiels puis
compositions complètes, avec exactement trois transports ordonnés et aucun retry.

Les régressions permanentes vérifient aussi le rejet historique V11, les deux catégories de
penalty raté sans motif, le refus d'un `inGamePenalty` sans minute, le refus d'une séance isolée et
d'une séquence lacunaire, le rendu `—`, le round-trip PostgreSQL des minutes nulles et l'upgrade
V18 → V19 sans altération de l'historique V11. Le schéma neuf applique V1 → V19.

```text
J5_INCIDENT_PARSER_AT_SECTION_29=event-incidents-v12
J5_FLYWAY_VERSION=19
J5_V12_TRIGGER=SNAPSHOT_189_PEN_AND_14_SHOOTOUTS_WITHOUT_EFFECTIVE_MINUTE
J5_V12_V11_DIAGNOSTIC=15_TIME_PROBLEMS_NO_REASON_PROBLEM
J5_V12_FULL_OPERATOR_PAYLOAD=PASS_OFFLINE_PARTIAL_33_INCIDENTS_14_SHOOTOUTS
J5_V12_MISSED_WITHOUT_REASON_AND_DESCRIPTION=PARTIAL_BOTH_PENALTY_TYPES
J5_V12_MINUTE_POLICY=NULL_NO_SEQUENCE_INFERENCE
J5_V12_TARGETED_TESTS=30_PASS
J5_V12_STANDARD_TESTS=366_PASS
J5_V12_INTEGRATION_TESTS=34_PASS
J5_V12_FLYWAY_UPGRADE=V18_TO_V19_PASS_APPEND_ONLY
J5_PROVIDER_CALLS_BY_AGENT=0
J5_PROVIDER_SCHEMA_VALIDATED_AT_SECTION_29=NO
J5_PROVIDER_SCHEMA_VALIDATION_REASON_AT_SECTION_29=V11_REJECTED_REAL_SNAPSHOT_189
J5_V12_REAL_RETEST_AT_SECTION_29=REQUIRED
J5_LOCAL_CONFIGURATION_RELOCKED=NOT_EVIDENCED
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=NO
```

### 29.5 Nouvelle séquence de clôture

La section 29 remplace la séquence de clôture de la section 28.3. Les preuves V11 et du mode
combiné restent valides dans leur portée historique, mais le statut fournisseur demeurait `NO` à
cette étape. La suite alors requise était :

1. redémarrer l'application après application de V19 pour sortir du verrou `FAILED_LOCKED` ;
2. préparer et confirmer une campagne J5 V12 distincte sur la même identité ou une rencontre
   représentative comportant la forme non minutée ;
3. vérifier que les incidents sont `PARTIAL`, que les minutes absentes sont affichées `—`, que les
   tirs ratés sans motif sont conservés et que les compositions sont atteintes après exactement
   trois appels sans retry ;
4. appliquer les arrêts globaux utiles à la fin de la session ;
5. remettre l'activation globale et les opt-ins J3/J4/J5 à `false`, vider l'origine et les
   endpoints autorisés dans `.env`, sans en publier le contenu ;
6. redémarrer une fois pour vérifier les contrôles J4 et J5 à l'état bloqué, conserver une preuve
   minimisée, puis arrêter gracieusement l'instance ;
7. passer le Work Order à `VALIDATED`, le déplacer dans `completed` et effectuer la clôture Git
   uniquement après revue humaine.

Le rapport minimisé de l'anomalie et de la correction est
`docs/validation/J5-REAL-V12-UNMINUTED-SHOOTOUT-20260818.md`.

## 30. Nouvelle valeur de motif de carton et correction V13 (2026-08-18)

### 30.1 Observation opérateur

Le propriétaire communique un incident `card/yellow` attribué à Yongjing Cao, côté extérieur, à
la minute 74, avec `reason="Leaving field"` et `rescinded=false`. La valeur correspond au cas d'un
joueur quittant le terrain sans autorisation préalable. Le JSON complet reste hors dépôt ; seule
la structure minimisée nécessaire à la régression est conservée.

### 30.2 Contrat V13 et migration V20

`event-incidents-v13` hérite sans modification de toutes les règles V12, y compris la séance
terminale entièrement non minutée et l'absence simultanée de motif et de description sur un
penalty raté. Il ajoute uniquement `Leaving field` au vocabulaire fermé de `reason` pour
`incidentType="card"`. Le libellé exact est persisté et affiché dans `MOTIF`; aucune traduction ni
catégorie générique n'est créée. V12 rejette encore la structure sur
`$.incidents[0].reason`, et toute autre valeur non documentée reste incompatible sous V13.

`V20__j5_leaving_field_card_reason.sql` autorise la provenance V13 sans modifier les colonnes ni
la contrainte de minute nullable introduite par V19. L'upgrade V19 → V20 conserve une observation
V12 et son incident à minute nulle, refuse V13 avant migration, puis persiste et relit après
migration le carton de Yongjing Cao à la minute 74 avec le motif exact. Le schéma neuf applique
V1 → V20.

### 30.3 Preuves hors ligne et humaines acquises

Les régressions permanentes couvrent la structure opérateur complète, le rejet historique V12,
la fermeture des autres valeurs, l'héritage de la règle V12 de séance non minutée, la poursuite du
service jusqu'aux compositions après exactement trois transports sans retry et la persistance
PostgreSQL append-only.

```text
J5_INCIDENT_CURRENT_PARSER=event-incidents-v13
J5_FLYWAY_VERSION=20
J5_V13_TRIGGER=INLINE_CARD_REASON_LEAVING_FIELD
J5_V13_V12_DIAGNOSTIC=REJECTED_EXACTLY_ON_REASON
J5_V13_OBSERVED_SHAPE=PASS_OFFLINE_COMPLETE
J5_V13_UNKNOWN_REASON=SCHEMA_INCOMPATIBLE
J5_V13_V12_SHOOTOUT_INHERITANCE=PASS
J5_V13_ORDERED_SERVICE=PASS_THREE_CALLS_CONTINUES_TO_LINEUPS
J5_V13_TARGETED_TESTS=33_PASS
J5_V13_STANDARD_TESTS=369_PASS
J5_V13_INTEGRATION_TESTS=35_PASS
J5_V13_FLYWAY_UPGRADE=V19_TO_V20_PASS_APPEND_ONLY
J5_PROVIDER_CALLS_BY_AGENT=0
J5_PROVIDER_SCHEMA_VALIDATED=YES
J5_PROVIDER_SCHEMA_VALIDATION_SCOPE=V13_EVENTS_16691018_AND_16851672
J5_V13_REAL_SHOOTOUT=PASS_EVENT_16691018_SNAPSHOTS_188_189_192
J5_V13_REAL_SHOOTOUT_INCIDENTS=SNAPSHOT_189_OBSERVATION_100_PARTIAL_142_OF_171
J5_V13_REAL_LEAVING_FIELD=PASS_EVENT_16851672_SNAPSHOTS_194_195_196
J5_V13_REAL_LEAVING_FIELD_INCIDENTS=SNAPSHOT_195_OBSERVATION_103_COMPLETE_81_OF_81
J5_UI_INCIDENT_MISSING_PATHS=HIDDEN_OFFLINE_TEST_PASS
J5_UI_LINEUP_MISSING_PATHS=HIDDEN_OFFLINE_TEST_PASS
J5_UI_VISUAL_RETEST=PASS_OPERATOR_9_SCREENSHOTS
J5_UI_INCIDENT_TABLE_UNCHANGED=PASS
J5_UI_LINEUP_TABLE_UNCHANGED=PASS
J5_LOCAL_CONFIGURATION_RELOCKED=YES_OPERATOR_EVIDENCE
J5_J4_LOCKED_AFTER_RESTART=PASS
J5_J5_LOCKED_AFTER_RESTART=PASS
J5_CURRENT_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
J5_FINAL_APPLICATION_STOPPED_AFTER_RELOCK=PASS
J5_REAL_WORK_ORDER_CAN_BE_ARCHIVED=YES
J5_REAL_WORK_ORDER_STATUS=VALIDATED
```

### 30.4 Séquence de clôture acquise

La présente section remplace la séquence 29.5. Les preuves historiques V8 à V12 restent valides
dans leur portée et les deux retests V13 sont maintenant acquis. Les captures de `16691018`
attestent la séance non minutée, les trois appels et la poursuite jusqu'aux compositions ; celles de
`16851672` attestent séparément `Leaving field` et les trois familles. Le lot final de neuf captures
opérateur, conservé hors dépôt, clôt les huit étapes :

1. la page reconstruite ne rend plus les listes de chemins techniques au-dessus des tableaux ;
2. l'observation incidents 100 conserve son badge `PARTIAL`, son compteur `142/171`, ses 33 lignes,
   les minutes `—`, les tirs ratés sans motif et les scores ;
3. l'observation compositions 101 conserve son badge `PARTIAL`, son compteur `94/95` et toutes ses
   lignes, y compris le numéro de maillot absent rendu sans valeur inventée ;
4. l'observation incidents 103 conserve `Leaving field` dans `MOTIF` à la minute 74 ;
5. l'état local bloqué a été rétabli par le propriétaire sans versionner `.env` ;
6. un redémarrage a montré J4 et J5 à l'état `LOCKED` avec leurs bloqueurs attendus ;
7. l'instance de contrôle a été arrêtée et aucun listener n'est présent sur `127.0.0.1:8087` ;
8. le Work Order est passé à `VALIDATED` et peut être déplacé dans `completed`.

```text
J5_FINAL_FUNCTIONAL_QUALIFICATION=PASS
J5_FINAL_CONFIGURATION_LOCK=PASS
J5_FINAL_APPLICATION_STOP=PASS
WORK_ORDER_CLOSURE=PASS
```

Le rapport minimisé de cette extension est
`docs/validation/J5-OBSERVED-V13-LEAVING-FIELD-CARD-REASON-20260818.md`.
