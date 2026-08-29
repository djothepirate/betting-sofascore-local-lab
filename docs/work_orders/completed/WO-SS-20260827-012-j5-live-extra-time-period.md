# WO-SS-20260827-012 — Marqueur de période J5 « Extra time » en direct

- **Statut :** `VALIDATED`
- **Date :** 2026-08-27
- **Date de démarrage :** 2026-08-27
- **Date de fin technique :** 2026-08-27
- **Date de qualification et de clôture :** 2026-08-27
- **Qualification propriétaire :** `PASS`
- **Clôture :** `COMPLETED`
- **Prérequis :** évolution J5 incidents V13 fusionnée sur `main`
- **Base locale :** `6c9a6ff`
- **Jalon :** J5 — incidents football
- **Branche :** `codex/j5-live-extra-time-period`
- **Demande propriétaire :** prise en compte d'une nouvelle valeur observée le 2026-08-27
- **Preuves opérateur analysées :** `incidents-extra.json`, `incidents-extra-ht.json`,
  `event-16809018-incidents.json`
- **Payloads opérateur ajoutés à Git :** `NO`
- **Appel fournisseur pendant l'implémentation :** `NOT_AUTHORIZED`
- **Appel fournisseur pendant les tests automatisés :** `NOT_AUTHORIZED`
- **Nouvel endpoint ou nouvelle URI :** `NO`
- **Polling, planification, live réseau ou retry :** `NOT_AUTHORIZED`
- **Proxy, cookie, jeton, profil navigateur ou contournement :** `NOT_AUTHORIZED`
- **Modification de l'ADR-SS-001 :** `NOT_REQUIRED_AFTER_EXPLICIT_REVIEW`
- **Migration :** `V26_J5_LIVE_EXTRA_TIME_PERIOD`

## 1. Objectif

Étendre le contrat fermé des incidents football J5 afin d'accepter le marqueur fournisseur observé
pendant une prolongation en cours :

```text
incidentType=period
text=Extra time
isLive=true
```

La valeur exacte `Extra time` doit être conservée comme libellé de période. Elle ne doit ni être
traduite, ni être assimilée au marqueur terminal inactif `ET`. Toutes les règles V13 qui ne
concernent pas cette nouvelle combinaison restent inchangées.

Les statuts du laboratoire restent :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

## 2. Analyse structurelle faisant autorité

Les deux preuves fournies sont des documents JSON à racine objet contenant une liste
`incidents`. Elles attestent le même premier élément `period` :

```text
$.incidents[0].incidentType=period
$.incidents[0].text=Extra time
$.incidents[0].isLive=true
$.incidents[0].time=120
$.incidents[0].addedTime=999
$.incidents[0].timeSeconds=7200
$.incidents[0].reversedPeriodTime=1
$.incidents[0].reversedPeriodTimeSeconds=0
$.incidents[0].periodTimeSeconds=900
```

Le score diffère entre les deux observations, ce qui confirme que le marqueur décrit une phase en
cours et non un score terminal. La seconde preuve contient aussi des incidents de prolongation
antérieurs à la minute 120. Les valeurs temporelles observées documentent le cas mais ne deviennent
pas des invariants artificiels : la nouvelle règle est fondée sur le tuple sémantique
`period / Extra time / isLive=true`.

Le contrat normatif est ajouté dans :

```text
docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md
```

Décisions essentielles :

```text
CURRENT_INCIDENT_PARSER=event-incidents-v14
V14_INHERITS=event-incidents-v13
V14_NEW_PERIOD_TEXT=Extra time
V14_NEW_PERIOD_CONTEXT=incidentType:period AND isLive:true
V14_LABEL_PRESERVATION=VERBATIM
V14_EXTRA_TIME_IS_TERMINAL_ET=NO
V14_EXPLICIT_FALSE=SCHEMA_INCOMPATIBLE
V14_MISSING_IS_LIVE=PARSED_PARTIAL
V14_UNKNOWN_PERIOD_TEXT=SCHEMA_INCOMPATIBLE
V14_PROVIDER_CALLS_ADDED=0
```

## 3. Revue de l'ADR-SS-001

Cette évolution ne modifie ni endpoint logique, ni URI, ni transport, ni orchestration réseau. Elle
ne change que le parseur versionné exécuté après la conservation du brut, pour les voies J5 déjà
qualifiées : campagne directe protégée, import JSON local sans réseau et import multi-match hors
ligne.

Les invariants restent donc identiques :

- brut persisté avant parsing dans la voie directe ;
- hash, snapshot, parseur et heure de réception conservés ;
- aucun retry, polling, scheduler ou appel implicite ;
- aucun proxy, cookie, jeton, session ou profil navigateur ;
- import local borné et sans transport fournisseur ;
- arrêt terminal au premier incident de campagne ;
- écoute locale uniquement sur `127.0.0.1` ;
- aucune dépendance du Betting Project principal.

```text
ADR_SS_001_WO_012_REVIEW=COMPATIBLE_NO_CHANGE_REQUIRED
ADR_SS_001_MODIFICATION_REQUIRED=NO
PROVIDER_CALL_AUTHORIZED_BY_THIS_REVIEW=NO
```

## 4. Périmètre inclus

- nouveau parseur `event-incidents-v14` héritant de V13 ;
- ajout exact de `Extra time` au vocabulaire fermé des libellés `period` ;
- ajout de `Extra time` aux périodes dont la complétude mesure `isLive` ;
- rejet atomique d'un marqueur `Extra time` explicitement non live ;
- conservation d'un marqueur sans `isLive` en donnée parseable mais `PARTIAL` ;
- maintien du rejet de toute autre valeur future non documentée ;
- câblage du parseur courant pour la campagne J5 directe et l'import JSON local ;
- réutilisation du même parseur par l'import multi-match via le processeur partagé ;
- compatibilité de lecture de l'historique V1 à V13 ;
- migration Flyway append-only V26 autorisant la version de parseur V14 ;
- fixture minimale anonymisée, tests parseur, services et migration ;
- mise à jour des exigences, architecture, runbooks, README et changelog ;
- rapport de validation technique séparé.

## 5. Hors périmètre

- ajout ou modification d'un endpoint fournisseur ;
- nouvelle collecte réelle, automatisée ou manuelle par l'agent ;
- modification des en-têtes HTTP, cookies, jetons ou mécanismes anti-403 ;
- retry automatique après incident réseau ou HTTP ;
- déduction de la prolongation depuis la minute, le score ou l'ordre de la liste ;
- transformation de `Extra time` en `ET` ou réciproquement ;
- validation d'autres nouveaux libellés de période ;
- modification du modèle SQL normalisé ou des migrations déjà appliquées ;
- qualification humaine et fermeture sans autorisation explicite du propriétaire.

## 6. Conception

### 6.1 Héritage sans régression historique

V6 expose désormais deux vocabulaires protégés : tous les textes `period` reconnus et les textes
de période live dont la présence de `isLive` contribue à la complétude. Les méthodes retournent les
ensembles historiques par défaut. V7 à V13 conservent donc exactement le comportement antérieur.

V14 surcharge uniquement ces deux ensembles :

```text
PERIOD_TEXTS=HT,FT,ET,PEN,First half,Second half,Extra time
LIVE_PERIOD_TEXTS=First half,Second half,Extra time
```

### 6.2 Sémantique `isLive`

- `true` : structure conforme et complète lorsque les autres champs attendus sont présents ;
- absent : structure conservée, chemin `isLive` manquant et complétude `PARTIAL` ;
- `false` explicite : contradiction atomique `SCHEMA_INCOMPATIBLE` ;
- valeur d'un autre type : incompatibilité de schéma par le contrôle booléen existant.

### 6.3 Migration et provenance

`V26__j5_live_extra_time_period.sql` remplace uniquement la contrainte fermée
`ck_j5_event_data_parser`. Elle conserve toutes les versions historiques et ajoute
`event-incidents-v14`. Aucune colonne, donnée historique ou contrainte temporelle n'est réécrite.

Le test d'upgrade V25 → V26 doit prouver que :

1. V13 est accepté avant V26 et reste inchangé après migration ;
2. V14 est refusé avant V26 ;
3. V14 est accepté après V26 ;
4. le libellé `Extra time`, la minute et le score sont persistés sans transformation.

## 7. Critères d'acceptation

- [x] les preuves opérateur sont analysées sans être ajoutées au dépôt ;
- [x] `Extra time` live est accepté par V14 et conservé exactement ;
- [x] la même fixture reste refusée par V13 ;
- [x] `isLive=false` est rejeté ;
- [x] `isLive` absent produit `PARTIAL` ;
- [x] une valeur inconnue reste refusée ;
- [x] les règles héritées V13 sont couvertes ;
- [x] les voies directe et import local utilisent V14 ;
- [x] le parcours multi-match hors ligne effectif réutilise V14 et conserve `Extra time` pour chaque rencontre ;
- [x] V26 est append-only et couvre l'upgrade V25 → V26 ;
- [x] la documentation métier et technique est actualisée ;
- [x] `mvnw clean verify` est vert ;
- [x] `mvnw -Pintegration-tests verify` est vert ;
- [x] les audits secrets, écoute locale et absence d'appel réel sont verts ;
- [x] qualification fonctionnelle propriétaire acquise ;
- [x] Work Order fermé et déplacé vers `completed` sur autorisation explicite.

## 8. Validation automatisée acquise

Les trois niveaux de validation sont verts sous Java 25 :

```text
.\mvnw.cmd --offline -q "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" "-Dtest=EventIncidentsV14ParserTest,EventIncidentsV13ParserTest,EventIncidentsV12ParserTest,EventIncidentsV6ParserTest,J5RealEventDataServiceTest,J5LocalJsonImportProcessorTest,J5LocalJsonImportServiceTest" test
PASS — 92 tests ciblés, 0 échec, 0 erreur

.\mvnw.cmd --offline -q "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" -Pintegration-tests "-Dtest=EventIncidentsV14ParserTest" "-Dit.test=FlywayMigrationIT#importsAndReimportsAnOfflineMultiMatchBatchWithAppendOnlyOccurrences" verify
PASS — parseur V14 et scénario d'import multi-match sélectionné, 0 échec, 0 erreur

.\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" clean verify
PASS — 620 tests standards, 0 échec, 0 erreur, 2 ignorés conditionnels

.\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" -Pintegration-tests verify
PASS — 620 tests standards + 53 tests d'intégration, 0 échec, 0 erreur
```

PostgreSQL/Testcontainers utilise l'image locale 18.4. Un schéma neuf applique les 26 migrations
versionnées V1 à V26 et les upgrades historiques atteignent également V26. L'avertissement
d'auto-attachement Mockito/Byte Buddy sous Java 25 reste le seul avertissement d'exécution connu ;
il n'affecte pas le résultat.

```text
TARGETED_UNIT_TESTS=PASS
STANDARD_CLEAN_VERIFY=PASS_620_TESTS_2_CONDITIONAL_SKIPS
INTEGRATION_VERIFY=PASS_53_TESTS
FLYWAY_CURRENT_VERSION=26
REAL_PROVIDER_CALLS=0
SECRET_DIFF_AUDIT=PASS
LOCAL_BIND_AUDIT=PASS_127.0.0.1
NO_REAL_PROVIDER_CALL_AUDIT=PASS
```

Les 29 fichiers modifiés ou ajoutés ont été contrôlés : aucune affectation ressemblant à un
secret, aucun artefact `.env`, HAR, cookie, jeton, trace ou profil navigateur, et aucune nouvelle
surface HTTP n'est introduite. `server.address=127.0.0.1`, `sofascore.enabled=false`, le polling et
le rafraîchissement automatique restent les valeurs effectives par défaut.

## 9. Qualification et clôture propriétaire — 2026-08-27

Le propriétaire confirme que les captures communiquées constituent la validation fonctionnelle
finale. La voie d'import local à zéro appel a conservé le corps incidents réel hors Git et l'a
normalisé par `event-incidents-v14` sans incompatibilité :

- une première observation complète conserve 24 incidents et `86/86` signaux ;
- une seconde observation enrichie conserve 28 incidents et `99/99` signaux ;
- dans les deux cas, le marqueur `period / Extra time` reste affiché à la minute 120 ;
- l'évolution du score de `1-1` à `1-2` et les actions de prolongation antérieures confirment que
  ce libellé décrit la phase live et ne remplace pas le marqueur terminal `ET` ;
- les statistiques et compositions indisponibles sont représentées par les preuves locales 404
  prévues, sans appel fournisseur depuis l'application.

Ces preuves complètent les tests automatisés et l'analyse hors dépôt du document exact. Le
propriétaire autorise explicitement la qualification, la fermeture du Work Order et son déplacement
vers `docs/work_orders/completed`. Cette clôture n'autorise aucun nouvel appel fournisseur, retry,
polling, scheduler, proxy, cookie, jeton ou automatisation navigateur.

```text
IMPLEMENTATION=COMPLETED
TECHNICAL_READINESS=PASS_620_STANDARD_TESTS_2_SKIPPED_AND_53_POSTGRESQL_TESTS
EXACT_OPERATOR_PAYLOAD=PASS_EVENT_INCIDENTS_V14_24_INCIDENTS
OWNER_FINAL_CAPTURE_1=PASS_COMPLETE_24_INCIDENTS_86_OF_86
OWNER_FINAL_CAPTURE_2=PASS_COMPLETE_28_INCIDENTS_99_OF_99
V14_LIVE_EXTRA_TIME_RENDERING=PASS_MINUTE_120
V14_TERMINAL_ET_SEMANTICS=UNCHANGED
OWNER_QUALIFICATION=PASS
QUALIFICATION_DATE=2026-08-27
CLOSURE_AUTHORIZED=YES
PROVIDER_CALLS_DURING_IMPLEMENTATION=0
PROVIDER_CALLS_DURING_AUTOMATED_TESTS=0
ADDITIONAL_PROVIDER_CALL_AUTHORIZED_BY_CLOSURE=NO
WORK_ORDER_STATUS=VALIDATED
WORK_ORDER_CLOSURE=COMPLETED
WORK_ORDER_LOCATION=docs/work_orders/completed
```
