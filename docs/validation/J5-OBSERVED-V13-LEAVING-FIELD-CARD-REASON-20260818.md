# J5 — Correction V13 du motif de carton `Leaving field`

## 1. Décision

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
CURRENT_INCIDENT_PARSER=event-incidents-v13
PROVIDER_SCHEMA_VALIDATED=YES
PROVIDER_SCHEMA_VALIDATION_SCOPE=V13_EVENTS_16691018_AND_16851672
V13_OFFLINE_QUALIFICATION=PASS
V13_REAL_RETEST=PASS_SHOOTOUT_AND_LEAVING_FIELD
UI_MISSING_PATHS_VISUAL_RETEST=PASS_OPERATOR_9_SCREENSHOTS
LOCAL_CONFIGURATION_RELOCKED=YES_OPERATOR_EVIDENCE
J4_LOCKED_AFTER_RESTART=PASS
J5_LOCKED_AFTER_RESTART=PASS
CURRENT_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_APPLICATION_STOPPED_AFTER_RELOCK=PASS
WORK_ORDER_ARCHIVABLE=YES
WORK_ORDER_STATUS=VALIDATED
```

Ce rapport consigne une nouvelle valeur fournisseur communiquée par le propriétaire le
2026-08-18. Elle concerne uniquement le vocabulaire de `reason` des incidents `card`; elle ne
modifie ni le contrat temporel V12, ni les règles des pénaltys, ni les autres types d'incidents.

## 2. Observation minimisée

La structure attestée est un carton jaune extérieur à la minute 74 :

```text
incidentType=card
incidentClass=yellow
time=74
isHome=false
player.id=817886
player.name=Yongjing Cao
reason=Leaving field
rescinded=false
```

Le sens métier communiqué est « joueur quittant le terrain sans autorisation préalable ». Le JSON
opérateur complet n'est pas ajouté au dépôt. Aucun secret, cookie, jeton, URI fournisseur ou
contenu `.env` n'est reproduit dans cette preuve.

## 3. Contrat V13

`event-incidents-v13` hérite de l'intégralité du comportement V12, notamment :

- l'acceptation strictement contextuelle d'une séance terminale entièrement non minutée ;
- l'absence de toute déduction d'une minute depuis l'ordre des tireurs ;
- la complétude `PARTIAL` d'un penalty `missed` omettant simultanément `reason` et `description`,
  pour `inGamePenalty` comme pour `penaltyShootout` ;
- le rejet des tuples incohérents, des types inconnus et des vocabulaires hors contrat.

V13 ajoute exclusivement `Leaving field` à la liste fermée des motifs de carton. Le libellé est
conservé tel quel dans le brut, dans la raison normalisée et dans la colonne `MOTIF`. Aucune
traduction silencieuse ni catégorie générique n'est inventée. Le parseur historique V12 rejette
encore cette même structure sur `$.incidents[0].reason`, et une autre valeur non documentée reste
`SCHEMA_INCOMPATIBLE` sous V13.

## 4. Migration et historique

`V20__j5_leaving_field_card_reason.sql` est append-only. Elle autorise la provenance
`event-incidents-v13` dans la contrainte de `j5_event_data_observation` sans ajouter de colonne et
sans modifier la contrainte temporelle V19.

Le test d'upgrade V19 → V20 vérifie simultanément que :

1. une provenance V13 est refusée avant la migration ;
2. une observation V12 et son incident de séance à minute `NULL` restent byte-for-byte inchangés ;
3. la provenance V13 est acceptée après migration ;
4. le carton de Yongjing Cao est persisté et relu à la minute 74 avec le motif exact
   `Leaving field`.

Un schéma neuf applique désormais V1 → V20.

## 5. Qualification automatisée

Les régressions ciblées couvrent la structure opérateur, la fermeture du vocabulaire, l'héritage
de la séance V12, la poursuite ordonnée du service jusqu'aux compositions et le rendu inchangé du
motif :

```text
.\mvnw.cmd --offline -q "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" "-Dtest=EventIncidentsV13ParserTest,EventIncidentsV12ParserTest,EventIncidentsV11ParserTest,J5RealEventDataServiceTest,J5EventDataControllerTest" test
PASS — 33 tests ciblés, 0 échec, 0 erreur

.\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" clean verify
PASS — 369 tests standards, 0 échec, 0 erreur, 0 ignoré

.\mvnw.cmd --offline "-Dmaven.repo.local=C:\Users\geoff\.m2\repository" -Pintegration-tests verify
PASS — 369 tests standards + 35 tests d'intégration, 0 échec, 0 erreur
```

Les tests standards n'exécutent aucun appel réel vers SofaScore. PostgreSQL/Testcontainers utilise
l'image locale 18.4 et Flyway valide les vingt migrations. Le seul avertissement d'exécution connu
est l'auto-attachement Mockito/Byte Buddy sous Java 25 ; il n'affecte pas les résultats.

## 6. Qualification humaine acquise

Les deux formes courantes ont été observées avec le parseur V13 dans des campagnes humaines
bornées :

1. Cittadella — Atalanta U23 (`16691018`) : campagne `COMPLETED_LOCKED`, exactement trois appels,
   statistiques snapshot 188 indisponibles, incidents snapshot 189 / observation 100 à
   `PARTIAL · 83% · 142/171`, 33 incidents, marqueur `PEN` et quatorze tirs au but rendus à `—`,
   puis compositions snapshot 192 / observation 101 à `PARTIAL · 98% · 94/95` ;
2. Shanghai Shenhua — Beijing Guoan (`16851672`) : campagne `COMPLETED_LOCKED`, exactement trois
   appels, statistiques snapshot 194 / observation 102 à `256/256`, incidents snapshot 195 /
   observation 103 à `COMPLETE · 81/81`, carton jaune extérieur de Yongjing Cao à la minute 74
   rendu avec le motif exact `Leaving field`, puis compositions snapshot 196 / observation 104 à
   `COMPLETE · 97/97`.

Ces preuves portent `PROVIDER_SCHEMA_VALIDATED=YES` dans la portée V13 observée. Elles confirment
l'ordre `STATISTICS → INCIDENTS → LINEUPS`, la poursuite après l'indisponibilité explicite, l'absence
de retry et le verrou terminal.

## 7. Présentation et clôture acquises

Les chemins techniques manquants restent conservés dans les rapports de complétude, mais leur
liste n'est plus rendue au-dessus des tableaux incidents et compositions. Le test MVC vérifie un
rapport partiel réel pour chaque famille, l'absence des préfixes `$.incidents[` et
`$.home.players[`, ainsi que le maintien du contenu des tableaux.

Les neuf captures finales confirment ce comportement sur les observations 100 et 101, le maintien
des tableaux et du motif `Leaving field` sur l'observation 103. Le propriétaire a attesté la
configuration locale bloquée ; J4 et J5 sont `LOCKED` après redémarrage. L'arrêt final est confirmé
par l'absence de listener sur `127.0.0.1:8087`. Le Work Order est `VALIDATED` et archivable.
