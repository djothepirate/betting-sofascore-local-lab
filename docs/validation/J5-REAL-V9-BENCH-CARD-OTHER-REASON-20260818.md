# J5 — Correction V9 du carton de banc `Other reason`

> `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`

## 1. Résultat actuel

```text
DATE=2026-08-18
EVENT=Cardiff_City_Wrexham
PROVIDER_EVENT_ID=16391145
V8_INITIAL_CAMPAIGN_TERMINAL=FAILED_LOCKED
V8_INITIAL_PROVIDER_CALLS=2
V8_INITIAL_RETRY=0
V8_INITIAL_STATISTICS=SNAPSHOT_161_COMPLETE
V8_INITIAL_INCIDENTS=SNAPSHOT_162_HTTP_200_SCHEMA_INCOMPATIBLE
V8_INITIAL_LINEUPS=NOT_ATTEMPTED
V8_BLOCKING_PATH=$.incidents[19].reason
V8_BLOCKING_VALUE=Other_reason
V9_PARSER=event-incidents-v9
V9_FLYWAY_VERSION=16
V9_FULL_OPERATOR_PAYLOAD=PASS_OFFLINE_20_OF_20
V9_NORMALIZED_BENCH_CARD_MINUTE=90_PLUS_9
V9_REASON=Other_reason
V9_PROBLEMS=0
V9_STANDARD_TESTS=344_PASS
V9_INTEGRATION_TESTS=30_PASS
REAL_PROVIDER_CALLS_BY_AGENT=0
V9_HUMAN_RETEST=PASS
V9_CAMPAIGN_TERMINAL=COMPLETED_LOCKED
V9_PROVIDER_CALLS=3
V9_RETRY=0
V9_STATISTICS=SNAPSHOT_161_OBSERVATION_75_COMPLETE
V9_INCIDENTS=SNAPSHOT_162_OBSERVATION_77_COMPLETE_20_OF_20
V9_LINEUPS=SNAPSHOT_165_OBSERVATION_78_COMPLETE_85_OF_85
V9_FUNCTIONAL_EVIDENCE=5_PNG_464810_BYTES
V9_PROVIDER_SCHEMA_VALIDATED_AT_RETEST=YES
V9_PROVIDER_SCHEMA_VALIDATION_SCOPE=BOUNDED_REAL_V9_CARDIFF_2026_08_18
CURRENT_PARSER=event-incidents-v13
CURRENT_FLYWAY_VERSION=20
CURRENT_PROVIDER_SCHEMA_VALIDATED=YES
CURRENT_PROVIDER_SCHEMA_VALIDATION_SCOPE=V13_EVENTS_16691018_AND_16851672
CURRENT_OFF_THE_BALL_CARD_REAL_QUALIFICATION=PASS_EVENT_16671566_SNAPSHOT_185
CURRENT_COMBINED_J4_J5_SESSION=PASS_REAL_EVENT_16671566
LOCAL_CONFIGURATION_RELOCKED=YES_OPERATOR_EVIDENCE
CURRENT_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_APPLICATION_STOPPED_AFTER_RELOCK=PASS
WORK_ORDER_ARCHIVABLE=YES
```

Le correctif V9 est implémenté, validé hors ligne et qualifié historiquement en conditions réelles.
Une campagne ultérieure a toutefois été rejetée par V9 sur une nouvelle variante de but. Cette
variante, le motif V11 et le parcours combiné J4→J5 sont maintenant qualifiés réellement ; le seul
verrou restant est détaillé aux sections 8 et 10 et dans le rapport V11.

## 2. Preuves opérateur traitées

Deux captures montrent la campagne et la consultation locale du brut :

- Cardiff City — Wrexham, identifiant fournisseur `16391145` ;
- statistiques snapshot 161, `COMPLETE · 100%` ;
- incidents snapshot 162, HTTP `200`, 29 251 octets, classé `SCHEMA_INCOMPATIBLE` par
  `event-incidents-v8` ;
- campagne verrouillée après deux appels, sans retry et sans tentative `EVENT_LINEUPS` ;
- SHA-256 brut affiché par l'inspection locale :
  `6396ce7a4585237895bdb0aeeb3d79194155c4666c51e5c764074f9ec66496fd`.

Le JSON complet a également été fourni hors dépôt pour diagnostic et rejeu. Sa représentation
texte reçue contient 36 383 octets et porte le SHA-256
`CCA5DBDC2154056E69ABA6FE7652A69D111282D8EC9ADA0056A9F0961D478E63`. Cette empreinte identifie la
transcription utilisée pour le test opérateur ; elle n'est pas présentée comme l'empreinte des
octets HTTP bruts, dont la sérialisation et la taille sont différentes. Le document complet, les
captures et les noms de personnes ne sont pas copiés dans le dépôt.

## 3. Diagnostic

Le payload contient 20 incidents. Les 19 premiers respectent le contrat V8. Le seul problème
bloquant se trouve sur le dernier objet :

```text
incidentType=card
incidentClass=yellow
time=-5
benchTime=90
benchAddedTime=9
reason=Other reason
rescinded=false
isHome=true
manager=present
```

V8 rejetait exactement `$.incidents[19].reason`, car son vocabulaire fermé contenait dix motifs
de carton mais pas `Other reason`. `benchAddedTime` et le bloc auxiliaire `manager` étaient alors
des champs inconnus non bloquants. Ignorer `benchAddedTime` aurait néanmoins rendu la minute
incomplète (`90` au lieu de `90+9`) ; V9 le traite donc explicitement.

Le comportement de la campagne est conforme au garde-fou : le brut a été conservé avant parsing,
l'échec de schéma a été terminal, aucun retry n'a eu lieu et les compositions n'ont pas été
appelées.

## 4. Contrat fermé V9

`event-incidents-v9` hérite de toutes les règles V8, notamment :

- le marqueur terminal exact `PEN/time=999` normalisé à la dernière minute effective de séance ;
- les cartons dont `reason` est absent ou `null` ;
- les buts ordinaires portant la valeur brute `from="shot"` ;
- le triplet cohérent `missed/Woodwork/woodwork` pour `inGamePenalty` et `penaltyShootout`.

V9 ajoute seulement les deux variantes attestées par le snapshot 162 :

1. `Other reason` devient la onzième valeur autorisée du motif de carton. Toute autre valeur
   inconnue reste `SCHEMA_INCOMPATIBLE`.
2. `benchAddedTime` devient le temps additionnel normalisé uniquement si l'incident est un
   `card`, si `time` est un entier négatif, si `benchTime` est présent et si `addedTime` est absent.
   La valeur doit être un entier compris entre 0 et 300. Tout usage hors de ce contexte ou tout
   conflit entre `addedTime` et `benchAddedTime` reste incompatible.

Dans le cas observé, `benchTime=90` et `benchAddedTime=9` deviennent la minute normalisée `90+9`.
Le bloc `manager` reste dans le snapshot brut et produit un avertissement de champ inconnu ; le
libellé d'acteur déjà porté par `playerName` est conservé dans l'observation normalisée.

## 5. Rejeu complet et régressions

Un harnais JUnit temporaire, supprimé après usage, a lu la transcription complète depuis son
emplacement opérateur. Aucun fichier de preuve n'a été copié dans le dépôt. Le rejeu a confirmé :

- statut `PARSED` ;
- 20 incidents normalisés sur 20 ;
- complétude `COMPLETE · 100%` ;
- zéro problème de schéma ;
- dernier incident `card/yellow`, minute `90+9`, motif `Other reason`.

Les tests versionnés ne contiennent qu'une forme synthétique minimale. Ils prouvent en plus que :

- V8 rejette encore le même objet exactement sur le motif, ce qui préserve l'historique ;
- un futur motif non documenté reste refusé ;
- `benchAddedTime` hors contexte et les champs temporels concurrents restent refusés ;
- le contrat V8 `Woodwork/woodwork` de `penaltyShootout` est hérité par V9 ;
- le service ordonné utilise V9 et poursuit vers les compositions après un parsing compatible.

```text
COMMAND=.\mvnw.cmd clean verify
RESULT=PASS_344_TESTS

COMMAND=.\mvnw.cmd -Pintegration-tests verify
RESULT=PASS_344_STANDARD_PLUS_30_INTEGRATION_TESTS
PROVIDER_CALLS=0
```

## 6. Persistance et migration

`V16__j5_bench_card_other_reason.sql` est append-only. Elle ne crée ni colonne ni table et ne
modifie aucune migration antérieure. Elle remplace uniquement la contrainte de provenance pour
autoriser `event-incidents-v9` en conservant toutes les versions historiques.

Le test d'upgrade V15 → V16 :

- crée une observation V8 et son carton avant migration ;
- vérifie que V9 est refusé par la contrainte V15 ;
- applique exactement une migration ;
- vérifie que l'observation et l'incident V8 sont inchangés ;
- persiste puis relit une observation V9 avec `minute=90`, `added_time=9` et
  `reason="Other reason"`.

## 7. Retest humain V9

Les cinq captures reçues après le correctif établissent la séquence suivante :

1. la préparation de la nouvelle campagne passe à `AWAITING_CONFIRMATION` sans transport ; la
   phrase ponctuelle visible dans la capture n'est ni reproduite ni conservée ;
2. la confirmation aboutit à `COMPLETED_LOCKED` après exactement trois appels ordonnés ;
3. les statistiques réutilisent le snapshot 161 et l'observation 75, `COMPLETE · 100%` ;
4. les incidents réutilisent le brut du snapshot 162 mais produisent l'observation append-only 77,
   `COMPLETE · 100%` ; les 20 incidents sont visibles et le carton bloquant sous V8 est désormais
   rendu à `90+9`, classe `yellow`, côté `HOME`, motif `Other reason` ;
5. les compositions sont effectivement appelées et produisent le snapshot 165, l'observation 78,
   la provenance `event-lineups-v2` et `COMPLETE · 100% · 85/85` ; les formations domicile
   `4-1-4-1` et extérieur `5-4-1` sont toutes deux visibles.

Le libellé du parseur incidents se trouve hors du cadrage de la troisième capture. L'attribution à
V9 est néanmoins déterminée par le résultat lui-même : V8 rejetait exactement `Other reason` et ne
connaissait pas `benchAddedTime`, tandis que la nouvelle observation accepte ce motif et produit
`90+9`, combinaison propre au contrat V9 déployé.

Le snapshot incidents conserve son SHA-256 brut
`6396ce7a4585237895bdb0aeeb3d79194155c4666c51e5c764074f9ec66496fd`. La nouvelle observation
porte le SHA-256 normalisé
`85695252bd39579e54687ef93da92bca045c937219e6f49592b450355675be9f`. Le snapshot compositions
porte le SHA-256 brut `9e873103ddfa54ad4f9020bd757fe0d02532ba6c9b4b7140c90ad3744718a7b2`
et le SHA-256 normalisé `90decfd539b6a336e9d06129f847cd674031fe8a477a5876d00e4430f49da98f`.

Les captures restent hors dépôt. Leur inventaire minimisé est :

| Preuve | Taille | SHA-256 |
|---|---:|---|
| Préparation protégée | 112 096 octets | `816AAB99EE58CCD07735E8A399FFD2FC73C4949AF753A21BA508B9838DEDB74F` |
| Campagne terminée et résultat des trois familles | 138 427 octets | `9E2565BADACFFBC17C2DF3B1AA3776D9439AEF71088A39438B3AA68DD9C814AF` |
| Incidents normalisés | 90 375 octets | `38015F08797BF3EEFE549A8FAFA3D0DF2A68CBF1A0959EA5C023E3EE4051EC2F` |
| Composition domicile | 75 538 octets | `16648F6108A6CF8E6094E4F995242567E45068C12D6E3F16F50217AE80988CB4` |
| Composition extérieur | 48 374 octets | `A0DC3AC32E9EC0627499DC833CFA28CF508B5D5762DF0EAADB5356ECE69DFE9E` |

Ce retest a levé le verrou fonctionnel V9. La qualification historique
`V9_PROVIDER_SCHEMA_VALIDATED_AT_RETEST=YES` reste strictement bornée aux formes réelles observées
pendant cette campagne ; elle ne transforme pas les fixtures synthétiques en preuves fournisseur
et, comme le snapshot 179 l'a ensuite démontré, ne garantit pas l'absence d'une dérive future.

## 8. Étapes de clôture acquises

1. Le propriétaire a remis l'activation globale SofaScore, les opt-ins J3/J4/J5, l'origine et la
   liste d'endpoints à leur état local bloqué, sans publier ni confier le contenu de `.env` à
   l'agent.
2. Le redémarrage en configuration bloquée montre J4 et J5 `LOCKED`, puis l'arrêt final laisse le
   port `127.0.0.1:8087` sans listener.
3. La preuve finale est mise à jour et le Work Order passe à `VALIDATED` avant archivage.

`WORK_ORDER_ARCHIVABLE=YES` est désormais la décision courante.

## 9. Dérive ultérieure V9 et correction V10

La campagne de l'événement `16251993` a parsé les statistiques du snapshot 178, puis conservé le
snapshot incidents 179 avant de s'arrêter après deux appels. Les cinq buts du payload portent
`incidentClass="regular"` et `from="regular"`; V9 rejette exactement ces cinq chemins `from`.

`event-incidents-v10` ajoute uniquement ce tuple redondant cohérent. Le payload opérateur complet
passe hors ligne à 7/7 incidents et `24/24` signaux, la migration V16 → V17 préserve V9, et les
suites de 348 tests standards plus 31 tests d'intégration sont vertes. Aucun appel fournisseur n'a
été exécuté par l'agent. À ce stade, le statut était revenu à `PROVIDER_SCHEMA_VALIDATED=NO` dans
l'attente du retest alors prévu pour V10.

Le rapport minimisé historique V10 est
`docs/validation/J5-REAL-V10-REGULAR-GOAL-ORIGIN-20260818.md`.

## 10. Observation ultérieure V11

Avant le retest V10, un carton `yellow` à la minute 77 portant
`reason="Off the ball foul"` a été attesté directement par l'opérateur. V10 rejette cette valeur ;
V11/V18 l'ajoute de façon fermée et conserve le motif exact. Un premier retest V11 termine les
trois familles sur `16251993` et qualifie la correction V10. Une campagne ultérieure sur `16671566`
enchaîne J4 phase 2 et J5 sans redémarrage, termine les trois appels J5 et rend le motif inchangé
dans le snapshot incidents 185 à `COMPLETE · 69/69`. Les suites courantes passent 358 tests
standards et 32 tests d'intégration à cette étape historique.

Le rapport minimisé V11 est
`docs/validation/J5-REAL-V11-OFF-BALL-CARD-REASON-20260818.md`.

Une campagne ultérieure sur `16691018` a ramené le statut à `NO` après le rejet V11 d'une séance de
tirs au but entièrement non minutée. Le parseur courant V13, qui hérite de V12/V19, a depuis réussi
le retest réel et la clôture opérateur. Voir
`docs/validation/J5-REAL-V12-UNMINUTED-SHOOTOUT-20260818.md`.
