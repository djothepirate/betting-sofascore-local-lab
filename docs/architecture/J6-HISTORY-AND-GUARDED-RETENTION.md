# J6 — Historique sémantique et rétention gardée

## 1. Frontière du jalon

J6 rend consultables les versions déjà conservées par J3, J4 et J5. Il n'ajoute aucun endpoint
fournisseur, aucun transport, aucun polling et aucune tâche planifiée. Les lectures et comparaisons
sont locales ; le corpus de démonstration est synthétique.

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
J6_PROVIDER_TRANSPORT=ABSENT
J6_AUTOMATIC_COLLECTION=ABSENT
J6_AUTOMATIC_RETENTION=ABSENT
```

L'ADR-SS-001 reste inchangé : les voies réseau existantes conservent leurs opt-ins, leurs verrous
et leur périmètre. J6 ne les utilise pas.

## 2. Occurrences de snapshots — Flyway V21

La déduplication brute historique conservait une seule ligne `provider_snapshot` pour une même
clé logique et un même SHA-256. Elle ne permettait donc pas de compter les tentatives ultérieures
ayant reçu exactement les mêmes octets.

`V21__j6_snapshot_occurrences.sql` ajoute `provider_snapshot_occurrence` :

- une occurrence `BASELINE` est créée pour chaque snapshot antérieur à V21 ;
- chaque nouvelle tentative produit ensuite `INSERTED` ou `DEDUPLICATED` ;
- l'occurrence référence le snapshot retenu et conserve uniquement des métadonnées bornées ;
- aucun octet brut, cookie, jeton, URI ou en-tête n'est recopié ;
- `UPDATE` et `DELETE` sont refusés par PostgreSQL.

Le backfill `BASELINE` ne reconstitue pas un nombre de tentatives que l'ancien schéma ne connaissait
pas. Il établit seulement une origine explicite pour chaque snapshot déjà présent.

### 2.1 Effets de l'import J5 multi-match WO-010

WO-010 réutilise sans nouvelle table le mécanisme d'occurrences J6. Un lot committé traite les trois
familles de chacun de ses `N` événements : chaque sauvegarde brute produit donc une occurrence
`INSERTED` ou `DEDUPLICATED`, soit `3N` occurrences pour le lot réussi. Un réimport par un nouveau
plan ajoute encore `3N` occurrences, même lorsque les snapshots ou observations normalisées sont
dédupliqués par les règles existantes.

La transaction WO-010 englobe snapshots, classifications, occurrences et observations. Son rollback
ne laisse aucun de ces éléments pour le lot échoué. Le plan, l'état du contrôle et son résultat
restent uniquement en mémoire et ne créent aucun historique durable de lot ; cette absence de suivi
du contrôle ne supprime pas l'historique métier J6 des données effectivement committées.

### 2.2 Réceptions live WO-058 — Flyway V33

WO-058 réutilise les snapshots et occurrences J6 dans un ledger distinct des campagnes J8.
`live_call` conserve la réservation de budget et `live_call_dispatch` l'autorisation de départ.
Cette autorisation ne constitue pas une preuve de passage sur le réseau. Une réponse reçue est
committée avec son snapshot brut, sa nouvelle occurrence et le lien `live_call_receipt` dans une
transaction unique, avant parsing. Le parsing se déroule ensuite hors transaction ; une seconde
transaction publie les observations normalisées, leurs références, le résultat versionné
`live_call_result` et la transition de campagne. Aucun verrou SQL n'est tenu pendant un appel
fournisseur.

Deux réponses identiques conservent un seul snapshot et des observations dédupliquées, mais deux
occurrences et deux réceptions. La fraîcheur live provient donc de la réception et du résultat de
la tentative, pas de la date de création de l'observation historique. La lecture des familles
distingue dernière réception, dernier succès et dernier changement métier. Une séquence A→B→A
peut référencer de nouveau l'ancienne observation A avec une réception récente ; un 404 ou un
échec de schéma conserve les références et la date du dernier succès lisible.

Les chronologies J6 existantes gardent leur sémantique d'observations historiques. V33 ne les
réécrit pas et ne crée aucune campagne live à partir d'anciennes collectes.

## 3. Flux historiques

La chronologie agrège les tables append-only existantes en cinq flux :

| Flux J6 | Source normalisée | Clé d'ordre |
|---|---|---|
| `EVENT_STATE` | `canonical_event_observation` | heure source, puis identifiant |
| `EVENT_DETAILS` | `event_detail_observation` | heure source, puis identifiant |
| `EVENT_STATISTICS` | `j5_event_data_observation` et métriques | heure source, puis identifiant |
| `EVENT_INCIDENTS` | `j5_event_data_observation` et incidents | heure source, puis identifiant |
| `EVENT_LINEUPS` | `j5_event_data_observation`, côtés et joueurs | heure source, puis identifiant |

La page contient 25 versions par défaut et refuse une taille supérieure à 100. L'ordre est stable,
y compris lorsque deux observations possèdent la même heure. La lecture renvoie aussi la
provenance, les hashes brut et normalisé, le parseur, la complétude et l'état du payload brut.

Les trois états bruts sont :

- `RETAINED` : les octets sont encore présents ;
- `PAYLOAD_PURGED` : seuls les octets ont été purgés par J6, avec audit ;
- `LEGACY_ABSENT` : un ancien enregistrement ne comportait pas d'octets bruts.

## 4. Différences sémantiques

La comparaison est calculée à la demande depuis les modèles normalisés. Elle ne reparcourt pas le
JSON brut et ne persiste aucune ligne de diff. Deux versions arbitraires sont comparables seulement
si elles appartiennent au même événement et au même flux.

Les changements utilisent `ADDED`, `REMOVED` ou `CHANGED` :

- état : horaire, équipes, statut et tournoi ;
- détails : stade, saison et tour ;
- statistiques : clé période/groupe/code, libellé et valeurs domicile/extérieur ;
- compositions : côté et identifiant joueur, formation, présence, identité, numéro, position et
  statut titulaire ;
- incidents : égalité exacte, puis séquence de tirs au but, puis type/côté/identifiants fournisseur,
  avec la position seulement en dernier recours ;
- complétude : statut, pourcentage, compteurs et chemins manquants.

Un appariement ambigu d'incidents n'est jamais forcé : il devient un retrait et un ajout. Le score
affiché est dérivé du dernier incident, dans l'ordre source, qui fournit simultanément les deux
valeurs. Une absence facultative reste une absence et n'est pas transformée en zéro ou en texte.

## 5. Classification des versions

Chaque version possède une classification primaire. Une occurrence dédupliquée peut également
être signalée en classification secondaire.

| Classification | Sens |
|---|---|
| `BASELINE` | première version connue du flux |
| `TECHNICAL_DUPLICATE` | nouvelle tentative rattachée au même snapshot |
| `LOCAL_REPARSE` | mêmes octets, parseur ou normalisation différents |
| `SEMANTICALLY_UNCHANGED` | octets différents, modèle normalisé identique |
| `SYNTHETIC_CHANGE` | changement issu du corpus local synthétique |
| `PROVIDER_UPDATE` | changement fournisseur avant état terminal |
| `LATE_ENRICHMENT` | ajouts uniquement après état terminal |
| `LATE_CORRECTION` | modification ou retrait après état terminal |

Les statuts terminaux reconnus, sans tenir compte de la casse, sont `finished`, `canceled`,
`cancelled`, `abandoned` et `walkover`. Pour les flux autres que l'état, la classification cherche
le dernier état canonique strictement antérieur à l'heure de la version comparée. Pour le flux
d'état lui-même, l'identifiant d'observation départage deux versions reçues au même instant. Une
comparaison arbitraire retrouve ainsi un état terminal intermédiaire au lieu de se limiter à sa
version de départ. La première version fournisseur postérieure à la fin est donc correctement
reconnue comme tardive.

## 6. Interface et démonstration hors ligne

Les routes locales sont :

```text
GET  /events/{canonicalEventId}/history
GET  /events/{canonicalEventId}/history/compare
POST /events/history/offline-demo
```

Les lectures portent `Cache-Control: no-store`, `Pragma: no-cache`, une expiration immédiate et
`X-Robots-Tag: noindex, nofollow, noarchive`. Thymeleaf échappe le contenu. Aucun endpoint JSON,
téléchargement brut, export J7 ou action fournisseur n'est ajouté.

Le POST de démonstration consomme un jeton local à usage unique. Il valide toutes les fixtures
avant écriture, importe les versions nominales J4/J5, ajoute l'état terminal et les quatre
corrections synthétiques, puis redirige vers la chronologie. Une seconde exécution est dédupliquée.
La provenance synthétique est prioritaire dans la classification : ces versions postérieures à la
fin restent `SYNTHETIC_CHANGE`. Les classes `LATE_ENRICHMENT` et `LATE_CORRECTION` ne sont attribuées
qu'à des observations de provenance fournisseur et sont qualifiées sans réseau par les tests.

## 7. Rétention des octets bruts — Flyway V22

La rétention n'efface jamais une ligne snapshot ni une observation normalisée. Elle peut uniquement
mettre `provider_snapshot.payload_raw` à `NULL` et renseigner `payload_purged_at`. Taille, SHA-256,
requête logique, heures, statut, parseur, occurrences et provenance restent intacts.

Un snapshot est éligible si et seulement si :

1. ses octets sont encore présents ;
2. `received_at` est strictement antérieur au cutoff de rétention ;
3. son statut est `PARSED` ou `ENDPOINT_UNAVAILABLE` ;
4. il est référencé par au moins une observation normalisée J4 ou J5 ;
5. il provient de `SOFASCORE` et son mode d'acquisition vaut `DIRECT_LOCAL_ENDPOINT` ou
   `MANUAL_LOCAL_JSON_IMPORT`.

Cette éligibilité correspond au prédicat du store courant. Elle soumet les octets importés
manuellement aux mêmes garanties de plan, de sauvegarde, de restauration et d'audit J6 que les
octets acquis directement. Elle ne rend jamais un snapshot `MANUAL_LOCAL_JSON_IMPORT` éligible au
cache fournisseur et n'autorise aucune collecte ou suppression automatique.

Le lot est ordonné par heure puis identifiant et limité à 500 candidats. Son SHA-256 inclut la
version de format, la durée de rétention, le cutoff et, pour chaque candidat, l'identifiant,
l'heure, la taille et le hash du payload. La phrase exacte est :

```text
PURGER <N> PAYLOADS J6 <PLAN_SHA256>
```

L'exécution reconstitue et verrouille le plan dans une transaction `SERIALIZABLE`. Une dérive du
plan, une couverture de sauvegarde insuffisante, une restauration non qualifiée, une phrase
incorrecte ou une différence de nombre de lignes annule toute la transaction.

Depuis V33, l'aperçu et l'exécution exigent aussi le garde fournisseur commun à `FREE` et aucune
campagne live `RUNNING` ou `CLEANUP_REQUIRED`. Sinon le store refuse avec
`PROVIDER_CAMPAIGN_ACTIVE`. Pendant la purge, le verrou de ligne sur `provider_campaign_guard`
empêche une acquisition fournisseur concurrente jusqu'à la fin de la transaction. Le trigger
`provider_snapshot_live_retention_guard` applique le même contrôle aux suppressions d'octets en
complément de la preuve d'audit V22.

## 8. Défense PostgreSQL et preuve de sauvegarde

`j6_raw_payload_purge_audit` est append-only. Une ligne d'audit conserve, sans payload :

- lot, snapshot, heure, taille et SHA-256 antérieurs ;
- durée, cutoff et SHA-256 du plan ;
- SHA-256 du manifeste et de la sauvegarde chiffrée ;
- date de qualification et borne de couverture de la sauvegarde ;
- date d'exécution.

Un trigger protège `provider_snapshot` après V22. Il autorise seulement :

- la classification unique historique `RAW_ONLY` vers un état final ;
- la purge exacte des octets lorsqu'une ligne d'audit correspondante existe dans la même
  transaction et que l'identifiant de lot transactionnel concorde.

Toute suppression de snapshot et toute autre modification sont refusées. La contrainte SQL exige
en outre que la preuve de sauvegarde couvre l'identifiant et l'heure du snapshot audité.

### 8.1 Couverture des campagnes live et du garde commun

La sauvegarde/restauration courante exige Flyway V33. Ses preuves source et restauration incluent
les sept compteurs des tables `live_campaign`, `live_event`, `live_call`, `live_call_dispatch`,
`live_call_receipt`, `live_call_result` et `live_transition`, l'état du garde fournisseur et le
nombre de campagnes `RUNNING` ou `CLEANUP_REQUIRED`. `liveLedgerSha256` couvre toutes les colonnes
des lignes de ces sept tables et de `provider_campaign_guard`, soit huit tables. Les lignes
`to_jsonb` sont préfixées par leur table, triées puis hashées ; le manifeste conserve uniquement
l'empreinte et les compteurs, sans reproduire les lignes.

Le manifeste live immuable comprend notamment les cibles et leur provenance, les plafonds, le TTL,
la capacité et le profil d'admission qualifié : enveloppes de requête et de traitement conservées
exactement en nanosecondes, plus SHA-256 de qualification. Les résultats versionnés, leurs liens
snapshot/occurrence et normalisés, les projections d'état, les compteurs et les générations sont
ainsi tous comparés à la restauration. Les preuves historiques V32 restent conservées ; elles ne
remplacent pas la couverture V33 exigée par la rétention courante.

Une sauvegarde qualifiée exige `providerGuardState=FREE` et `activeLiveCount=0`. Une restauration
n'arme aucun worker ni reprise. Un ancien propriétaire de garde, identifié par instance, PID et
instant de démarrage, ne peut pas être remplacé à l'expiration d'un délai. Si sa disparition est
prouvée, la reprise locale peut classer les résultats manquants `UNKNOWN` et la campagne
`INTERRUPTED`, puis conserver le garde à `CLEANUP_REQUIRED`. Sa libération exige une preuve de
nettoyage des ressources exactes ; ni rétention ni restauration ne constituent cette preuve.

La purge qualifiée conserve intégralement les lignes live, leurs références et leurs projections.
Si une future réception déduplique vers un snapshot dont les octets ont déjà été purgés, le store
live refuse `LIVE_RAW_PREVIOUSLY_PURGED` et annule sa transaction de réception, nouvelle occurrence
comprise. L'ancienne preuve et son audit restent inchangés ; aucune normalisation de cette
nouvelle réception n'est publiée. Cette limite respecte l'ordre brut avant parsing sans
réhydrater les données purgées hors de la gouvernance J6.

## 9. Commande opérateur non Web

L'aperçu est visible dans le tableau de bord mais aucune action de purge n'y existe. La seule voie
d'exécution est `J6RetentionCommand`, lancée par `scripts/Invoke-J6Retention.ps1` en mode non Web.
Elle refuse de démarrer si un opt-in réseau, le rafraîchissement automatique, le polling ou le
verrou persistant du connecteur ne sont pas dans l'état sûr.

`scripts/Backup-Restore-J6.ps1` produit un `pg_dump` au format custom directement chiffré par
`age`, sans fichier de dump clair sur le chemin réel. Les deux pipelines binaires
`pg_dump -> age` et `age -> pg_restore` utilisent un superviseur commun. Chaque hôte attend une
porte avant de créer sa cible ; sous Windows, le parent affecte d'abord cet hôte à un Job Object
`KILL_ON_JOB_CLOSE`, vérifie son appartenance et son compteur, puis ouvre la porte. La cible et ses
descendants héritent ainsi du confinement avant leur création.

Les octets circulent entre handles natifs par leurs `BaseStream`, jamais par le pipeline objet ou
texte de PowerShell. Producteur, consommateur, copie, portes et Job Objects sont observés et nettoyés
indépendamment sous une échéance commune. Tout échec, timeout, interruption, identité inaccessible,
compteur Job inconnu, handle non fermé ou nettoyage non démontré invalide le résultat. Les sessions
PostgreSQL reçoivent un `PGAPPNAME` aléatoire strictement borné ; seules les sessions de cette
exécution peuvent être terminées et trois observations stables à zéro sont exigées.

Archive et manifeste restent en staging `.partial-*`. Le flux est restauré dans une base temporaire
portant un nom borné, puis Flyway, comptes, hashes réels des payloads, occurrences et empreintes de
provenance normalisée sont comparés. La publication et `restoreQualified=true` ne surviennent
qu'après égalité et nettoyage complets. Tout staging et la base temporaire sont supprimés en cas
d'échec.

Le vrai `age -p` hérite du terminal interactif déjà attaché. Aucun `Start-Process`,
`CREATE_NEW_CONSOLE`, `AttachConsole` ou `FreeConsole` n'est demandé par le chemin runtime. La
contre-épreuve `CTRL_BREAK` utilise seulement `CREATE_NEW_PROCESS_GROUP` dans la console courante.

Le chemin exact et les contrôles opérateur sont décrits dans le runbook J6. Aucun de ces scripts
n'est planifié ou appelé par l'application.

## 10. Limites et décisions différées

- la qualification humaine de l'interface J6 reste nécessaire ;
- la première sauvegarde/restauration sur la base locale de l'opérateur reste interactive. Le double
  local qualifie une invocation abstraite `age -p` sans secret mais ni le vrai dialogue TTY, ni
  l'auto-génération, la restitution ou la réutilisation d'une phrase ;
- le double Docker `age` est une transformation réversible réservée à la preuve locale. Il ne
  constitue pas un chiffrement et son artefact temporaire doit être supprimé avant tout `PASS` ;
- les commandes scalaires historiques de préflight Docker/PostgreSQL restent hors du superviseur de
  pipeline ; le lot ne prétend pas généraliser son timeout à toute commande externe ;
- l'encodage Base64 des arguments de l'hôte est un transport, pas un chiffrement. Aucun secret ou
  credential ne doit y être placé ;
- aucune purge de la base primaire n'est autorisée par le Work Order sans décision distincte ;
- le live WO-058 s'arrête si une réception retrouve un brut précédemment purgé ; sa réhydratation
  ou une autre politique de conservation nécessite une décision J6 distincte ;
- l'export canonique reste réservé à J7 ;
- les mesures agrégées et coûts d'appels restent réservés à J8 ;
- aucune dépendance du Betting Project principal n'est créée.
