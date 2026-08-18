# J5 — Correction V12 d'une séance terminale entièrement non minutée

> **Mise à jour :** cette preuve V12 reste la référence de la correction temporelle. Le parseur
> courant est désormais `event-incidents-v13`, qui hérite intégralement de V12 et ajoute uniquement
> le motif de carton `Leaving field`. Le retest réel décrit ci-dessous a été exécuté sous V13 après
> application de V19 puis V20.

## 1. Décision courante

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
PROVIDER_SCHEMA_VALIDATED=YES
PROVIDER_SCHEMA_VALIDATION_SCOPE=V13_EVENTS_16691018_AND_16851672
CURRENT_INCIDENT_PARSER=event-incidents-v13
CURRENT_FLYWAY_VERSION=20
V12_OFFLINE_QUALIFICATION=PASS
V12_REAL_RETEST=PASS_UNDER_V13_EVENT_16691018_SNAPSHOT_189_OBSERVATION_100
V13_LEAVING_FIELD_OFFLINE_QUALIFICATION=PASS
V13_LEAVING_FIELD_REAL_RETEST=PASS_EVENT_16851672_SNAPSHOT_195_OBSERVATION_103
UI_MISSING_PATHS_VISUAL_RETEST=PASS_OPERATOR_9_SCREENSHOTS
LOCAL_CONFIGURATION_RELOCKED=YES_OPERATOR_EVIDENCE
J4_LOCKED_AFTER_RESTART=PASS
J5_LOCKED_AFTER_RESTART=PASS
CURRENT_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_APPLICATION_STOPPED_AFTER_RELOCK=PASS
WORK_ORDER_ARCHIVABLE=YES
```

Ce rapport consigne une dérive fournisseur observée le 2026-08-18 sur l'événement `16691018`, son
diagnostic exact, la correction hors ligne V12 et sa qualification humaine ultérieure sous V13.

## 2. Preuve réelle minimisée

La campagne J5 s'est arrêtée correctement au premier incident de schéma, après deux appels et sans
retry :

| Famille | Résultat |
|---|---|
| `EVENT_STATISTICS` | HTTP `404`, snapshot 188, observation 98, `UNAVAILABLE · N/A` |
| `EVENT_INCIDENTS` | HTTP `200`, snapshot 189, brut persisté puis `SCHEMA_INCOMPATIBLE` sous V11 |
| `EVENT_LINEUPS` | non tentée après le verrou `FAILED_LOCKED` |

Le snapshot statistiques contient 44 octets, a été reçu à
`2026-08-18T12:28:23.192329Z` et porte les empreintes suivantes :

```text
SHA256_SOURCE=61b6f2399d5cd9251af376fddf2243e9ea802fbcda179698437c344ef6db8b32
SHA256_NORMALIZED=ae831d38de63b56c6df671f23d5c021a54e4db8e7a2d73615e4bee2bfb97ede6
```

Le snapshot incidents contient 40 412 octets, a été reçu à
`2026-08-18T12:28:26.143477Z`, inspecté à `2026-08-18T12:29:22.126842200Z` et porte le SHA-256 brut
`421620124f07c1a01b1b1187741bb6389874957c3889658af725aaad8cd63e49`.

Les trois captures restent hors dépôt :

| Preuve | Taille | SHA-256 du PNG |
|---|---:|---|
| campagne et résultat arrêté | 133 770 octets | `6ca0d20de1684f4af03a98221c5616b67ec43a48c503f0458800cb17f0060d59` |
| indisponibilité statistiques et absences locales restantes | 74 327 octets | `68fe4dd6c891e7f2f9ae6577628e93dac672ffbe98230c026478ef1c48bd1712` |
| inspection brute du snapshot incidents | 125 829 octets | `1e2cfbccc6efb6e19021bdb0a1b0d168a23af21380793d946dbd4e12f9b825f2` |

La transcription complète fournie par le propriétaire reste également hors dépôt. Le harnais
ponctuel l'a relue depuis sa pièce locale après contrôle de ses 46 765 octets et de son SHA-256
`d87da6737d52fbadcc073770b28a3c02084d61bcf7b0945485241a7f34ae3cc8`. Aucun payload complet,
secret, contenu `.env` ou phrase de confirmation n'est versionné.

## 3. Cause racine

Le document contient 33 incidents :

- un marqueur terminal `period/PEN/penalties`, inactif, avec `time=999`, `addedTime=999` et le
  score final `4–3` ;
- quatorze `penaltyShootout` dont les séquences couvrent `1` à `14` ;
- aucune minute globale ni `footballPassingNetworkAction` sur ces quatorze tentatives ;
- sept tentatives `missed` sans `reason` ni `description` ;
- un marqueur de fin de match ou de prolongation possédant une minute valide.

Le diagnostic V11 retourne exactement quinze problèmes : un chemin temporel pour le marqueur
`PEN` et quatorze chemins temporels pour les tentatives. Aucun problème ne concerne `reason` ou
`description`. La supposition initiale était donc proche de la forme métier observée, mais ce sont
les minutes absentes — et non les motifs absents — qui rendaient le document incompatible.

L'absence simultanée de `reason` et `description` sur un penalty raté est conservée comme donnée
partielle. V12 ajoute des tests explicites pour `inGamePenalty` et `penaltyShootout` afin que cette
politique reste identique dans les deux catégories.

## 4. Correction bornée V12

`event-incidents-v12` hérite de V11. Il autorise une minute normalisée absente uniquement lorsque
la réponse forme une séance terminale entièrement non minutée et cohérente :

1. marqueur exact `period/PEN/penalties`, inactif, sentinelles `999` et score final complet ;
2. marqueur `FT` ou `ET` doté d'une minute effective valide ;
3. toutes les tentatives sans `time` et sans tableau d'action, sans forme mixte ;
4. classe `scored` ou `missed`, côté, score complet et séquence positive sur chaque tentative ;
5. séquences uniques et contiguës de `1` à `N` ;
6. score de la dernière séquence égal au score `PEN`.

Dans ce contexte, V12 :

- conserve les octets bruts inchangés ;
- persiste une minute `NULL` pour le marqueur `PEN` et les tentatives ;
- affiche `—` dans la colonne `MINUTE` ;
- émet `PROVIDER_SHOOTOUT_MINUTE_ABSENT` pour chacun des quinze chemins ;
- ne convertit jamais la séquence en minute ;
- retourne `PARTIAL` lorsque les motifs métier facultatifs manquent.

Restent incompatibles : un `inGamePenalty` sans minute, une séance isolée, active, mixte, à
séquence lacunaire ou dupliquée, un score final contradictoire, ou un nouveau tuple de résultat
hors vocabulaire. Les variantes explicites `Woodwork/woodwork` restent valides pour les deux types
de penalty et soumises aux mêmes contrôles de cohérence.

## 5. Persistance et migration

`V19__j5_unminuted_terminal_shootout.sql` est append-only. Elle :

- autorise la provenance `event-incidents-v12` ;
- rend la colonne `event_incident.minute` nullable ;
- contraint une minute nulle aux seuls `penaltyShootout` et marqueurs `period/PEN` ;
- interdit un temps additionnel normalisé lorsque la minute est absente ;
- ne réécrit aucune observation ni aucun incident V1–V18.

Le modèle de domaine utilise désormais `Optional<Integer>` pour la minute. Les anciens incidents
continuent d'être construits avec une minute présente ; leur hash normalisé reste inchangé. Le
stockage JDBC écrit et relit `NULL`, et le rendu MVC utilise la méthode métier `minuteLabel()`.

Le test PostgreSQL d'upgrade V18 → V19 compare l'historique V11 avant et après migration, refuse
V12 avant migration, autorise ensuite la nouvelle provenance et vérifie les contraintes négatives.
Un test distinct confirme le round-trip de la minute absente. Un schéma neuf applique V1 → V19.

## 6. Qualification automatisée acquise

Les tests ciblés couvrent le parseur, le service et le rendu :

```text
.\mvnw.cmd --offline -q "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" "-Dtest=EventIncidentsV12ParserTest,EventIncidentsV11ParserTest,J5RealEventDataServiceTest,J5EventDataControllerTest" test
PASS — 30 tests, 0 échec, 0 erreur
```

La qualification PostgreSQL ciblée couvre V12 et Flyway :

```text
.\mvnw.cmd --offline -q "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" -Pintegration-tests "-Dtest=EventIncidentsV12ParserTest" "-Dit.test=FlywayMigrationIT" verify
PASS — 34 tests FlywayMigrationIT, schéma neuf V1 → V19 et upgrade V18 → V19
```

Le rejeu ponctuel de la transcription opérateur retourne :

```text
V12_RESULT=PARSED
V12_COMPLETENESS=PARTIAL
V12_INCIDENTS=33
V12_PENALTY_SHOOTOUTS=14
V12_MISSED_WITHOUT_REASON_AND_DESCRIPTION=7
V12_MINUTES_ABSENT=15
V12_PROBLEMS=0
V12_RAW_ATTACHMENT_SHA256=VERIFIED
```

Le harnais de diagnostic et la transcription n'ont pas été ajoutés au dépôt. Aucun test n'a
résolu une URI SofaScore ou exécuté un appel fournisseur.

Les deux suites complètes demandées par la définition de fini passent également :

```text
.\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" clean verify
PASS — 366 tests standards, 0 échec, 0 erreur, 0 ignoré

.\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" -Pintegration-tests verify
PASS — 366 tests standards + 34 tests d'intégration, 0 échec, 0 erreur
```

PostgreSQL Testcontainers utilise l'image locale 18.4. Flyway valide 19 migrations, applique V1 →
V19 sur un schéma neuf et qualifie explicitement V18 → V19. Le seul avertissement d'exécution est
l'auto-attachement Mockito/Byte Buddy sous Java 25 ; il n'affecte pas le résultat.

## 7. Qualification humaine et clôture acquises

La campagne humaine V13 sur `16691018` a rempli le contrat attendu :

1. `EVENT_STATISTICS` reste explicitement indisponible dans le snapshot 188 / observation 98 ;
2. `EVENT_INCIDENTS` est reparsé depuis le snapshot 189 dans l'observation append-only 100 à
   `PARTIAL · 83% · 142/171`, avec 33 incidents ;
3. le marqueur `PEN` et les quatorze `penaltyShootout` sans minute affichent `—` ;
4. les tentatives `missed` sans `reason` ni `description` restent présentes sans motif inventé ;
5. `EVENT_LINEUPS` est atteint dans le snapshot 192 / observation 101 à
   `PARTIAL · 98% · 94/95` ;
6. la campagne termine `COMPLETED_LOCKED` après exactement trois appels et sans retry.

La liste technique des chemins de complétude a ensuite été retirée du rendu des panneaux incidents
et compositions, sans toucher aux badges, compteurs ni tableaux. Les captures finales confirment
ce rendu et la conservation des 33 incidents, des scores, des minutes `—` et des compositions. Le
propriétaire a attesté le reverrouillage sans publier `.env`; J4 et J5 sont `LOCKED` après
redémarrage, puis l'arrêt final laisse `127.0.0.1:8087` sans listener. Le Work Order est donc
`VALIDATED` et archivable avec `PROVIDER_SCHEMA_VALIDATED=YES` dans la portée V13 qualifiée.
