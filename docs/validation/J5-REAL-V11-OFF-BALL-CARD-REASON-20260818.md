# J5 — Observation V11 du motif de carton `Off the ball foul`

## 1. Statut minimisé

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
OBSERVATION_SOURCE=OPERATOR_INLINE_JSON_PLUS_REAL_CAMPAIGN_SCREENSHOTS
OBSERVED_INCIDENT_TYPE=card
OBSERVED_INCIDENT_CLASS=yellow
OBSERVED_CARD_REASON=Off_the_ball_foul
BUSINESS_MEANING=OBSTRUCTION_OR_OFF_BALL_FOUL
HISTORICAL_PARSER=event-incidents-v10
HISTORICAL_RESULT=SCHEMA_INCOMPATIBLE_ON_REASON
CURRENT_PARSER=event-incidents-v13
CURRENT_FLYWAY_VERSION=20
V11_OBSERVED_SHAPE=PASS_OFFLINE_COMPLETE_3_OF_3
V11_ORDERED_SERVICE=PASS_CONTINUES_TO_LINEUPS_THREE_CALLS_NO_RETRY
V11_FLYWAY_UPGRADE=V17_TO_V18_PASS_APPEND_ONLY
V11_STANDARD_TESTS=358_PASS
V11_INTEGRATION_TESTS=32_PASS
REAL_PROVIDER_CALLS_BY_AGENT=0
V11_HUMAN_RETEST=PASS_REGULAR_GOAL_AND_OFF_BALL_CARD
V11_REAL_CAMPAIGNS=EVENTS_16251993_AND_16671566_COMPLETED_LOCKED
V11_REAL_INCIDENTS=SNAPSHOT_179_OBSERVATION_93_COMPLETE_24_OF_24
V11_REAL_LINEUPS=SNAPSHOT_182_OBSERVATION_94_COMPLETE_67_OF_67
V11_OFF_BALL_REAL_INCIDENTS=SNAPSHOT_185_OBSERVATION_96_COMPLETE_69_OF_69
V11_OFF_BALL_REAL_LINEUPS=SNAPSHOT_186_OBSERVATION_97_COMPLETE_95_OF_95
PROVIDER_SCHEMA_VALIDATED=YES
PROVIDER_SCHEMA_VALIDATION_SCOPE=V13_EVENTS_16691018_AND_16851672
OFF_THE_BALL_CARD_REAL_QUALIFICATION=PASS_EVENT_16671566_SNAPSHOT_185
COMBINED_J4_J5_SESSION=PASS_REAL_EVENT_16671566
LOCAL_CONFIGURATION_RELOCKED=YES_OPERATOR_EVIDENCE
CURRENT_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_APPLICATION_STOPPED_AFTER_RELOCK=PASS
WORK_ORDER_ARCHIVABLE=YES
```

## 2. Portée de l'observation

Le propriétaire a fourni le 2026-08-18 un bloc JSON d'incident attestant une nouvelle valeur de
vocabulaire fournisseur. Il ne s'agit pas d'une nouvelle capture de campagne ni d'un snapshot
local identifié : aucun identifiant de snapshot, statut HTTP, hash brut ou nombre d'appels ne peut
donc être déduit de cette seule pièce.

Les attributs métier attestés sont :

| Attribut | Valeur observée |
|---|---|
| `incidentType` | `card` |
| `incidentClass` | `yellow` |
| `reason` | `Off the ball foul` |
| `time` | `77` |
| `isHome` | `false` |
| `player.id` | `877400` |
| `player.name` / `playerName` | `Facundo Mallo` |
| `rescinded` | `false` |

Le sens métier fourni est celui d'une obstruction ou d'une faute commise loin du ballon. Pour
rester fidèle au contrat d'affichage J5, la normalisation conserve toutefois le libellé fournisseur
exact `Off the ball foul` dans le motif ; elle n'invente pas une traduction française dans la
donnée persistée.

## 3. Cause préventivement isolée

Le vocabulaire fermé de V10 héritait des dix motifs V6 et de `Other reason` ajouté par V9. La
valeur `Off the ball foul` aurait donc produit un unique problème
`VALUE_OUT_OF_RANGE` sur `$.incidents[0].reason`, puis un résultat
`SCHEMA_INCOMPATIBLE`. Ce comportement strict reste utile pour toute valeur non attestée, mais la
nouvelle observation rend cette valeur précise admissible.

Le test V11 rejoue la structure fournie, y compris les métadonnées joueur étendues. Il confirme
explicitement que V10 refuse encore la même forme sur le chemin `reason` : l'historique du parseur
n'est ni élargi rétroactivement ni réécrit.

## 4. Contrat fermé V11

`event-incidents-v11` hérite intégralement de V10 et ajoute une seule valeur au vocabulaire des
cartons :

```text
incidentType=card
reason=Off the ball foul
```

Pour cette combinaison :

- la valeur exacte reste présente dans le snapshot brut ;
- la raison normalisée et la colonne `MOTIF` portent `Off the ball foul` ;
- le sens « obstruction / faute loin du ballon » est documenté dans la fiche métier ;
- l'absence facultative de `reason` demeure valide ;
- `Other reason` et tous les motifs historiques restent valides ;
- toute autre valeur non documentée reste `SCHEMA_INCOMPATIBLE`.

Le parseur ne généralise ni par casse, ni par préfixe, ni par similarité de texte. Une variante
future comme `Off-ball foul` nécessiterait donc une nouvelle preuve avant d'être admise.

## 5. Preuves automatisées

La régression `EventIncidentsV11ParserTest` vérifie :

- V11 `PARSED`, `COMPLETE · 100% · 3/3` sur la forme observée ;
- conservation de l'identité `877400` / `Facundo Mallo`, de la minute 77, du côté extérieur, de
  la classe jaune et du motif exact ;
- rejet historique V10 exactement sur `$.incidents[0].reason` ;
- rejet d'un nouveau motif fictif ;
- héritage du carton `Other reason`, du but `regular/from=regular` et du
  `penaltyShootout` sur le poteau.

La régression de service confirme une campagne locale simulée complète : statistiques, incidents
V11, puis compositions, exactement trois transports, aucune répétition et provenance
`event-incidents-v11`. Aucun test standard ne résout une URI SofaScore et aucun appel fournisseur
n'a été effectué par l'agent.

## 6. Persistance et migration V18

`V18__j5_off_the_ball_card_reason.sql` est append-only. Elle ne crée aucune colonne et ne modifie
aucune observation : elle étend uniquement la contrainte de vocabulaire des versions de parseur
pour autoriser `event-incidents-v11`.

Le test PostgreSQL V17 → V18 :

1. installe strictement V1 à V17 ;
2. persiste une observation et un incident V10 historiques ;
3. confirme que V11 est refusé avant migration ;
4. applique exactement une migration ;
5. compare l'observation et l'incident V10 champ par champ ;
6. persiste ensuite un carton V11 à la minute 77 avec le motif exact.

## 7. Commandes de qualification

```text
.\mvnw.cmd -q "-Dtest=EventIncidentsV11ParserTest,EventIncidentsV10ParserTest,J5RealEventDataServiceTest" test
PASS

.\mvnw.cmd -q -Pintegration-tests "-Dit.test=FlywayMigrationIT#upgradesV17WithTheObservedOffTheBallReasonWithoutRewritingV10History" verify
PASS

.\mvnw.cmd clean verify
PASS — 358 tests, 0 échec, 0 erreur, 0 ignoré

.\mvnw.cmd -q -Pintegration-tests verify
PASS — 358 tests standards + 32 tests d'intégration, 0 échec
```

Le seul avertissement d'exécution est celui de l'auto-attachement Mockito/Byte Buddy sous Java 25 ;
il n'affecte pas le résultat.

## 8. Qualification humaine V11 intermédiaire — buts réguliers

Le propriétaire a ensuite fourni trois captures d'une campagne complète sur **Al Tai —
Al-Qadsiah**, identifiant fournisseur `16251993` et UUID canonique
`b72dcb5e-98c7-39e4-ba59-9ab491105dc9`. Elles établissent :

- état terminal `COMPLETED_LOCKED` ;
- exactement trois appels fournisseur ordonnés, sans retry ;
- statistiques snapshot 178, observation 91, 435 octets, `COMPLETE · 100% · 4/4` ;
- incidents traités par `event-incidents-v11` depuis le snapshot brut dédupliqué 179, nouvelle
  observation append-only 93, sept incidents, `COMPLETE · 100% · 24/24` ;
- les cinq buts `regular/from=regular` auparavant bloquants sont visibles comme buts réguliers,
  sans origine spéciale inventée ;
- compositions snapshot 182, observation 94, 36 868 octets, confirmées,
  `COMPLETE · 100% · 67/67` ;
- aucun écran ne montre une resoumission ou une quatrième tentative.

Les empreintes métier affichées sont :

| Famille | SHA-256 brut | SHA-256 normalisé |
|---|---|---|
| statistiques | `6e4f31d999463b5f442835dcfe098390e312ff0161e359dcb601598342291c77` | `63742706657c3cb7cb9f50b11d831545f5ce4494e819bbc51ed6900419cdf9ff` |
| incidents | `6ca4d7e8dc7e1409007e465b1541fa517c338510857a974fd4b1e57b9fb70585` | `8eb0dfe4337e8530a5f6ac46e9bce8035613e9e6c7f573a06aa4dea182946efa` |
| compositions | `c4dda9ad702d6c3bb94b44fc90a24d4f07316894831587bbe29742b49b40d75c` | `894173f141c1ac7dd1fa144d8d9305121b9dd171ae6e4c0a855434948b3ca021` |

Les captures restent hors dépôt :

| Preuve | Taille | SHA-256 du fichier PNG |
|---|---:|---|
| campagne et résultat minimisé | 133 739 octets | `0e145ae54953be4e09bb4d1ee50c2526943cf48b658e868efce8e1308f9b8e39` |
| statistiques et incidents V11 | 107 320 octets | `5f6b1d482a10c106003f6cd38ec9cd0e1b95cacf594ce6e94b8dfc028f9dd74c` |
| compositions V2 | 80 217 octets | `c4a09ed3fa1dd8dae6b93e51811df15e615ad624af1878a7745dbf62d5d32199` |

Le lot représente 321 276 octets. Cette campagne qualifie en conditions réelles la correction des
buts réguliers et le chemin complet du parseur courant V11. Elle ne contient cependant aucun
carton `reason="Off the ball foul"` : cette valeur précise reste à tester réellement.

## 9. Qualification humaine finale — carton `Off the ball foul`

Le propriétaire atteste ensuite un parcours J4 phase 2 → J5 dans un même démarrage sur **Barracas
Central — Rosario Central**, identifiant fournisseur `16671566` et UUID canonique
`da075869-34d4-3d42-83d2-613583691845`. J4 termine après un appel avec le snapshot 183. Sans rebuild
ni redémarrage, J5 devient préparée sur cette identité et termine `COMPLETED_LOCKED` après trois
appels, sans retry : statistiques 184/95, incidents 185/96 et compositions 186/97.

Le panneau incidents montre `EVENT-INCIDENTS-V11`, 18 incidents et `COMPLETE · 69/69`. À la minute
77, le carton `yellow` extérieur de Facundo Mallo conserve et affiche exactement
`Off the ball foul` dans la colonne `MOTIF`. La campagne atteint ensuite les compositions confirmées
à `COMPLETE · 95/95`.

Les empreintes de données affichées sont :

| Famille | SHA-256 brut | SHA-256 normalisé |
|---|---|---|
| statistiques | `1ae0a26646233893b2928bc1303d4d34479b63b1eb20a2a936dd2755e5a4011e` | `45032dc445f8d76df16ea84a21b21b5b9cce3b612e1ed3aa20f5d033174ba8ea` |
| incidents | `159ede40ec5d7c23ffdd0fd3ca60becc47944de2395e92c04794a846f2939150` | `89a987e0ff9980698b07c822be98d27beef247b18fedc2dc74fc891840ed3bfc` |
| compositions | `55f4b2d38443a5d21483c3364c169b9fbeee694eeecaef4e7f96acf13112c195d` | `7b910203bd8e43b0e23f8886ad2e2ef96281c9212724cced7bca984e5b11cd03` |

L'inventaire SHA-256 des onze captures, totalisant 1 128 540 octets, est conservé dans
`J4-J5-COMBINED-QUALIFICATION-SESSION-20260818.md`. La capture de confirmation est uniquement
identifiée par son empreinte ; sa phrase n'est pas retranscrite.

## 10. Décision et suite humaine

Le correctif V11 et la session combinée sont qualifiés en réel. Le statut
`PROVIDER_SCHEMA_VALIDATED=YES` couvre désormais une portée bornée incluant le motif
`Off the ball foul`, sans prétendre garantir toute dérive future du fournisseur. Le contrôle local
ne trouve plus de listener sur 8087 ni de processus Java du laboratoire : l'application est arrêtée.

Le propriétaire a depuis attesté le reverrouillage sans publier `.env`. Le redémarrage montre les
bloqueurs J4 et J5 attendus, puis l'arrêt final laisse `127.0.0.1:8087` sans listener. Le Work Order
est désormais `VALIDATED` et archivable.

## 11. Évolution postérieure V12

La qualification du motif `Off the ball foul` reste valide pour l'événement `16671566`, mais elle
ne décrit plus le schéma fournisseur courant dans son ensemble. Une campagne ultérieure sur
`16691018` a été rejetée par V11 sur quinze minutes absentes au sein d'une séance terminale de
tirs au but. Le statut global est donc revenu à `PROVIDER_SCHEMA_VALIDATED=NO`.

Le correctif `event-incidents-v12`, la migration V19 et leur qualification sont documentés dans
`J5-REAL-V12-UNMINUTED-SHOOTOUT-20260818.md`. Le parseur courant V13 a depuis réussi le retest réel,
la présentation finale et le reverrouillage.
