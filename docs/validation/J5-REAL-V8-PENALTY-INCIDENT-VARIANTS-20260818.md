# J5 — Correction V8 des variantes d'incidents

> `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`

## 1. Résultat

```text
DATE=2026-08-18
PARSER=event-incidents-v8
FLYWAY_VERSION=15
SHOOTOUT_OPERATOR_PAYLOAD=PASS_OFFLINE_36_OF_36
SHOOTOUT_ATTEMPTS=9
PEN_PROVIDER_TIME=999_RAW_ONLY
PEN_NORMALIZED_MINUTE=146
CARD_OPERATOR_PAYLOAD=PASS_OFFLINE_16_OF_16
CARDS_WITHOUT_REASON=3_PRESERVED_AS_ABSENT
REGULAR_GOAL_SHOT_ORIGINS=3_RAW_ONLY
WOODWORK_FAMILIES=inGamePenalty,penaltyShootout
ORDERED_SERVICE=PASS_CONTINUES_TO_LINEUPS
FLYWAY_UPGRADE=V14_TO_V15_PASS
STANDARD_TESTS=340_PASS
INTEGRATION_TESTS=29_PASS
REAL_PROVIDER_CALLS_BY_AGENT=0
FUNCTIONAL_EVIDENCE_ARCHIVE=48_PNG
FUNCTIONAL_EVIDENCE_SHA256=D2A84F7DE91681426FD30E0EDAA4B6F02D4466DF1E2A8074905AD4C0760773BC
HUMAN_V8_RETEST=PASS
V8_CARD_WITHOUT_REASON_REAL=PASS_SNAPSHOT_144
V8_SHOOTOUT_REAL=PASS_SNAPSHOT_139_36_INCIDENTS_150_OF_150
V8_WOODWORK_INGAME_REAL=PASS_SNAPSHOT_150_27_INCIDENTS_102_OF_102
V8_WOODWORK_SHOOTOUT=PASS_OFFLINE_CONTRACT_AND_PERSISTENCE
V8_PROVIDER_SCHEMA_VALIDATED_AT_RETEST=YES
V8_PROVIDER_SCHEMA_VALIDATION_SCOPE=BOUNDED_REAL_CAMPAIGNS_2026_08_18
POST_V8_ANOMALY_PROVIDER_SCHEMA_VALIDATED=NO_AFTER_SNAPSHOT_162
V9_PROVIDER_SCHEMA_VALIDATED_AT_RETEST=YES_CARDIFF
CURRENT_PROVIDER_SCHEMA_VALIDATED=YES
CURRENT_PROVIDER_SCHEMA_VALIDATION_SCOPE=V13_EVENTS_16691018_AND_16851672
CURRENT_INCIDENT_PARSER=event-incidents-v13
CURRENT_FLYWAY_VERSION=20
CURRENT_OFF_THE_BALL_CARD_REAL_QUALIFICATION=PASS_EVENT_16671566_SNAPSHOT_185
CURRENT_COMBINED_J4_J5_SESSION=PASS_REAL_EVENT_16671566
LOCAL_CONFIGURATION_RELOCKED=YES_OPERATOR_EVIDENCE
CURRENT_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_APPLICATION_STOPPED_AFTER_RELOCK=PASS
WORK_ORDER_ARCHIVABLE=YES
```

La correction et ses tests automatisés ont été réalisés entièrement hors ligne. La qualification
fonctionnelle réelle a ensuite été exécutée par l'opérateur et transmise sous la forme d'une
archive locale de 48 captures PNG. Les captures et les payloads complets ne sont ni copiés dans le
dépôt, ni reproduits dans ce rapport. Les tests versionnés utilisent uniquement des formes
synthétiques minimales. Aucun secret, cookie, jeton, URI fournisseur, phrase de confirmation ou
identifiant de session n'est ajouté.

## 2. Séance de tirs au but

### 2.1 Cause du rejet V7

Le document opérateur contient 36 incidents. Le premier est un marqueur terminal :

```text
incidentType=period
text=PEN
period=penalties
isLive=false
homeScore=3
awayScore=2
time=999
addedTime=999
```

Les neuf objets `penaltyShootout` suivants n'ont pas de champ `time` global. Ils portent néanmoins
chacun une minute valide dans la première entrée de `footballPassingNetworkAction`, et leur ordre
métier est conservé par `sequence` de 1 à 9. Les minutes effectives observées vont de 121 à 146.

V6/V7 savent déjà :

- omettre la sentinelle `addedTime=999` des valeurs normalisées d'un marqueur `period` ;
- utiliser la première minute imbriquée lorsqu'un `penaltyShootout` n'a pas de minute globale ;
- conserver la classe, le motif, la description, la séquence et les scores des tirs au but.

Le rejet est exclusivement causé par `$.incidents[0].time=999`, car la minute normalisée reste
bornée à `0..300`.

### 2.2 Normalisation V8

V8 accepte `time=999` uniquement lorsque toutes les conditions suivantes sont remplies :

1. l'objet est un `period` ;
2. `text="PEN"` ;
3. `period="penalties"` ;
4. `isLive=false` ;
5. `homeScore` et `awayScore` sont présents dans leur plage ;
6. `addedTime=999` accompagne la sentinelle temporelle ;
7. la réponse contient au moins un `penaltyShootout` ;
8. chaque tir au but possède une minute globale valide ou une première minute imbriquée valide.

La minute normalisée du marqueur est le maximum de ces minutes effectives. Elle vaut donc 146 sur
la preuve opérateur. Cette règle place le marqueur après le dernier tir observé, n'invente pas une
durée 90 ou 120 et ne persiste jamais `999` comme minute métier. Le snapshot brut et son SHA-256
restent inchangés.

Les contre-exemples sans tirs au but, avec `FT/time=999`, `PEN/isLive=true`, mauvais champ
`period`, score incomplet ou minute de tir hors borne restent `SCHEMA_INCOMPATIBLE`.

## 3. Cartons sans motif et valeur connexe `from="shot"`

Le second document opérateur contient 16 incidents, dont trois cartons sans champ `reason`. Le
contrat V6/V7 représente déjà le motif par `Optional<String>` et la colonne SQL accepte `NULL`.
Une absence réelle de motif est donc valide ; elle ne dégrade même pas la complétude du carton,
dont les signaux métier obligatoires restent sa classe et son bénéficiaire. L'interface affiche
`—` et n'invente aucun motif.

Le rejeu intégral a néanmoins isolé trois autres problèmes dans le même document :

```text
$.incidents[10].from=shot
$.incidents[12].from=shot
$.incidents[14].from=shot
incidentType=goal
incidentClass=regular
```

V7 ne connaissait que `penalty` et l'alias brut `owngoal` pour l'origine spéciale d'un but. V8
accepte la valeur `shot` uniquement sur un but `regular`, la conserve dans le brut et l'omet de
`goalOrigin`, car elle ne représente ni un pénalty ni un but contre son camp. Une valeur `shot`
associée à une autre classe continue d'être rejetée.

Après cette règle bornée, les 16 incidents sont parsés et les trois cartons conservent
`reason=Optional.empty()`.

## 4. Résultat sur le poteau ou la barre transversale

La valeur fournisseur suivante est maintenant qualifiée :

```text
incidentClass=missed
description=Woodwork
reason=woodwork
```

V8 l'accepte pour les deux types demandés :

- `inGamePenalty` ;
- `penaltyShootout`.

Le contrat reste fermé. La seule présence de `woodwork` ou de `Woodwork` ne suffit pas : les trois
valeurs doivent être présentes et cohérentes. Une classe `scored`, une description `Off target`, un
motif `goalkeeperSave`, une absence au sein du triplet ou une valeur inconnue produit encore une
incompatibilité. La colonne MOTIF affiche la description fournisseur `Woodwork`; la documentation
métier la décrit en français comme un tir ayant touché le poteau ou la barre transversale.

## 5. Persistance et migration

`V15__j5_penalty_incident_variants.sql` ne modifie aucune colonne. Elle remplace uniquement la
contrainte d'autorisation des parseurs afin d'ajouter `event-incidents-v8` tout en conservant V1 à
V7 et les normaliseurs d'indisponibilité.

Le test d'upgrade V14 → V15 :

- crée une observation V7 et une substitution sur blessure avant migration ;
- conserve toutes les valeurs sélectionnées de l'observation et de l'incident après migration ;
- vérifie que V8 était refusé par la contrainte V14 puis autorisé par V15 ;
- persiste un marqueur `PEN` à la minute normalisée 146 ;
- persiste un `penaltyShootout` `Woodwork/woodwork` ;
- persiste un carton avec `reason=NULL` ;
- maintient la borne SQL de minute `0..300`.

Aucune observation historique n'est reparsée, reclassée ou réécrite.

## 6. Validation hors ligne

Les régressions versionnées couvrent :

- le marqueur `PEN/time=999` et la dérivation à 146 ;
- la conservation du hash du payload brut ;
- les neuf tirs au but sans minute globale ;
- les rejets hors contexte exact de la sentinelle ;
- un carton sans motif, rendu par `—` ;
- un motif de carton présent mais inconnu toujours rejeté ;
- `from="shot"` admis uniquement sur un but `regular` et omis de l'origine spéciale ;
- `Woodwork/woodwork` accepté pour `inGamePenalty` et `penaltyShootout` ;
- les tuples `Woodwork` incomplets ou croisés rejetés ;
- l'héritage de la substitution sur blessure V7 ;
- la poursuite ordonnée `STATISTICS → INCIDENTS → LINEUPS` sans retry ;
- l'upgrade PostgreSQL V14 → V15 et l'absence de réécriture historique.

```text
COMMAND=.\mvnw.cmd clean verify
RESULT=PASS_340_TESTS_0_FAILURE_0_ERROR_0_SKIPPED

COMMAND=.\mvnw.cmd -Pintegration-tests verify
RESULT=PASS_29_TESTS_0_FAILURE_0_ERROR_0_SKIPPED
```

## 7. Qualification fonctionnelle humaine

L'archive `Tests incidents.zip`, datée du 2026-08-18, contient 48 captures PNG et pèse
3 854 455 octets. Son SHA-256 est
`D2A84F7DE91681426FD30E0EDAA4B6F02D4466DF1E2A8074905AD4C0760773BC`. Elle reste hors dépôt ; cette
empreinte permet seulement d'identifier le lot relu. Les écrans ont été examinés dans leur ordre
chronologique. Ils établissent sept campagnes `COMPLETED_LOCKED` et un échec V7 historique ensuite
corrigé par V8 :

| Date | Rencontre et identifiant | Résultat minimisé |
|---|---|---|
| 2026-08-17 | France — Maroc, `12813016` | V7, trois appels, snapshots 129/130/131, incidents `17` et `67/67`, compositions `109/109` ; remplacement sur blessure et `inGamePenalty` raté sur arrêt du gardien visibles |
| 2026-08-17 | Argentine — Autriche, `15186502` | V7, trois appels, snapshots 132/133/134, incidents `22` et `85/85`, compositions `109/109` ; remplacement sur blessure, VAR et `inGamePenalty` hors cadre visibles |
| 2026-08-17 | Olympique Lyonnais — AC Sparta Praha, `16578111` | V7, trois appels, snapshots 135/136/137, incidents `20` et `77/77`, compositions `95/95` ; cartons jaune et rouge, buts et remplacements visibles |
| 2026-08-17 | Al Orobah — Abha, `16251997` | échec V7 historique après deux appels ; snapshot incidents 139 conservé en `SCHEMA_INCOMPATIBLE`, avec le marqueur terminal exact `PEN/time=999` visible dans la consultation brute en lecture seule |
| 2026-08-17 | Al Wehda — Al-Shabab, `16251994` | V7, trois appels, snapshots 140/141/142, incidents `32` et `113/113`, compositions `89/89` ; prolongation, VAR, cartons jaune/jaune-rouge et penalties en jeu visibles |
| 2026-08-18 | IF Gnistan — Ilves, `15272817` | trois appels ; statistiques snapshot 118 explicitement `UNAVAILABLE`, incidents snapshot 144 `COMPLETE`, compositions snapshot 145 `83/83` ; plusieurs cartons sans motif sont rendus par `—` et ne bloquent plus la campagne |
| 2026-08-18 | Al Orobah — Abha, `16251997` | rejeu V8 `COMPLETED_LOCKED` ; snapshots dédupliqués 138/139 puis compositions 148, observations 53/61/62 ; `event-incidents-v8`, 36 incidents, `150/150`, neuf tirs au but et marqueur `PEN` normalisé à 146 |
| 2026-08-18 | Samsunspor — Göztepe, `16483647` | V8, trois appels, snapshots 149/150/151 et observations 63/64/65 ; statistiques `260/260`, incidents `27` et `102/102`, compositions `85/85` ; `inGamePenalty/missed/Woodwork` visible à la minute 58 |

Le panneau incidents de la campagne IF Gnistan — Ilves est capturé sous son en-tête : sa version de
parseur n'est donc pas lisible directement sur cette image. La preuve fonctionnelle des cartons
sans motif repose sur le résultat `COMPLETE`, leur rendu `—` et la poursuite jusqu'aux compositions.
La présence effective de V8 dans le binaire de qualification est, elle, explicitement affichée sur
les deux campagnes suivantes, dont celle qui reparse le snapshot 139 historiquement rejeté.

Ces preuves qualifient en conditions réelles le marqueur terminal de séance, les cartons sans
motif et le résultat `Woodwork` d'un `inGamePenalty`. Elles requalifient aussi le contrat V7 hérité
sur plusieurs campagnes complètes. La variante exacte `Woodwork/woodwork` d'un
`penaltyShootout`, demandée en même temps, n'apparaît pas dans les 48 captures ; elle reste prouvée
hors ligne par les tests de contrat, de service et de persistance V15. La séance réelle confirme
séparément neuf objets `penaltyShootout` sous V8.

Les réponses `2xx` de Samsunspor — Göztepe et d'Al Orobah — Abha qualifient ensemble les trois
schémas alors courants `event-statistics-v2`, `event-incidents-v8` et `event-lineups-v2`. Le statut
historique `V8_PROVIDER_SCHEMA_VALIDATED_AT_RETEST=YES` décrit cette qualification réelle bornée et
datée ; il ne transforme
pas les fixtures synthétiques en preuves fournisseur et ne garantit pas l'absence de future dérive
du fournisseur.

## 8. Clôture acquise

La dernière capture montre encore l'application ouverte sur les compositions. Après traitement du
lot, un contrôle local en lecture seule ne trouve toutefois aucun listener sur le port attendu
`127.0.0.1:8087` et aucun processus Java dont la ligne de commande correspond au laboratoire. Ce
contrôle confirme l'arrêt de l'application sans lire sa configuration.

Le propriétaire a depuis attesté la remise de l'activation globale SofaScore, des opt-ins J3/J4/J5,
de l'origine et de la liste d'endpoints à leur état local bloqué. Les écrans J4 et J5 après
redémarrage sont `LOCKED`, puis le contrôle final ne trouve aucun listener sur
`127.0.0.1:8087`. Cette preuve reste volontairement minimisée : `.env` n'est ni lu ni publié.
Le Work Order est désormais `VALIDATED` et archivable.

## 9. Événement ultérieur et portée historique du rapport

Après ce lot, la campagne Cardiff City — Wrexham (`16391145`) a révélé dans le snapshot 162 une
forme de carton absente des preuves V8 : `reason="Other reason"` et `benchAddedTime=9` associés à
un carton de banc à la minute 90. Ce constat ne remet pas en cause les campagnes et assertions V8
ci-dessus ; il montre que leur portée était bornée aux formes alors observées.

Le statut est d'abord repassé à `PROVIDER_SCHEMA_VALIDATED=NO`. Le correctif V9/V16, son
rejeu hors ligne complet et son retest humain ensuite réussi sont documentés séparément dans
`J5-REAL-V9-BENCH-CARD-OTHER-REASON-20260818.md`. Cardiff City — Wrexham atteint désormais
`COMPLETED_LOCKED` après trois appels, avec le snapshot 162 reparsé à `COMPLETE · 100%`, le carton
à `90+9` et les compositions du snapshot 165 à `85/85`.

Une campagne encore ultérieure sur `16251993` a ensuite été rejetée par V9 sur cinq buts
`incidentClass="regular"` / `from="regular"`. Le statut repassait alors à `NO`; V10/V17 était
validé hors ligne et attendait alors son retest humain. Cette nouvelle évolution est documentée dans
`J5-REAL-V10-REGULAR-GOAL-ORIGIN-20260818.md`. Les preuves V8 et V9 restent historiques ; les
preuves V10 restent elles aussi valides hors ligne.

Avant ce retest, l'opérateur a attesté le motif de carton `Off the ball foul`. V11/V18 ajoute cette
seule valeur et conserve son libellé exact. Un premier retest V11 sur `16251993` qualifie
réellement les buts `regular/from=regular`. Une campagne combinée ultérieure sur Barracas Central —
Rosario Central (`16671566`) enchaîne ensuite J4 phase 2 et J5 sans redémarrage, termine les trois
familles J5 et rend le carton `Off the ball foul` inchangé dans le snapshot 185. Les suites
de cette étape passaient 358 tests standards plus 32 tests d'intégration. Cette évolution est
documentée dans `J5-REAL-V11-OFF-BALL-CARD-REASON-20260818.md`.

Une campagne plus récente sur `16691018` a ensuite rejeté sous V11 une séance entièrement non
minutée. V12/V19 passe le payload complet hors ligne sans inventer de minute ; le parseur courant
V13 a depuis réussi le retest réel, la présentation finale et le reverrouillage. Voir
`J5-REAL-V12-UNMINUTED-SHOOTOUT-20260818.md`.
