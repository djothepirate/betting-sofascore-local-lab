# J5 — Qualification réelle gardée des données événement

## 1. Statut

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
V13_PROVIDER_SCHEMA_VALIDATED=YES
V13_PROVIDER_SCHEMA_VALIDATION_SCOPE=EVENTS_16691018_AND_16851672
CURRENT_INCIDENT_PARSER=event-incidents-v14
V14_PROVIDER_SCHEMA_VALIDATED=YES_OWNER_LOCAL_JSON_IMPORT
V14_PROVIDER_SCHEMA_VALIDATION_SCOPE=EVENT_16809018_OBSERVATIONS_567_AND_570
V14_HUMAN_FUNCTIONAL_QUALIFICATION=PASS
V12_OFFLINE_QUALIFICATION=PASS_33_INCIDENTS_14_UNMINUTED_SHOOTOUTS
V13_LEAVING_FIELD_OFFLINE_QUALIFICATION=PASS_EXACT_CARD_REASON
V12_REAL_QUALIFICATION=PASS_UNDER_V13_EVENT_16691018_SNAPSHOT_189_OBSERVATION_100
V13_REAL_QUALIFICATION=PASS_SHOOTOUT_EVENT_16691018_AND_LEAVING_FIELD_EVENT_16851672
INCIDENT_MISSING_PATHS_RENDERING=HIDDEN
LINEUP_MISSING_PATHS_RENDERING=HIDDEN
UI_VISUAL_RETEST=PASS_OPERATOR_9_SCREENSHOTS
OFF_THE_BALL_CARD_REAL_QUALIFICATION=PASS_EVENT_16671566_SNAPSHOT_185
COMBINED_J4_J5_SESSION=PASS_REAL_EVENT_16671566
LOCAL_CONFIGURATION_RELOCKED=YES_OPERATOR_EVIDENCE
J4_LOCKED_AFTER_RESTART=PASS
J5_LOCKED_AFTER_RESTART=PASS
CURRENT_APPLICATION_LISTENER=ABSENT_127_0_0_1_8087
FINAL_APPLICATION_STOPPED_AFTER_RELOCK=PASS
V13_WORK_ORDER_ARCHIVABLE=YES
V13_WORK_ORDER_STATUS=VALIDATED
V14_WORK_ORDER_STATUS=VALIDATED
```

Cette architecture complète le contrat synthétique J5 V1 sans le remplacer. Elle autorise une
seule requête fournisseur active à la fois, y compris lorsque J3, J4 phase 2 et J5 sont armés dans
une même session. Une campagne réussie peut être suivie d'une nouvelle campagne explicitement
préparée et confirmée ; le
développement, Maven et les fixtures ne contactent jamais SofaScore. Les campagnes V8 restent des
preuves réelles bornées. V9 a été validé hors ligne puis en conditions réelles avec poursuite
jusqu'aux compositions. Un payload ultérieur a toutefois introduit `from="regular"` sur cinq buts
de classe `regular` et a été rejeté par V9. V10 corrige cette forme. Une observation opérateur
supplémentaire a ensuite attesté le motif de carton `Off the ball foul`, désormais couvert par V11.
Une première campagne V11 complète a reparsé le snapshot 179, atteint les compositions et qualifié
la correction des buts réguliers. Une campagne ultérieure sur `16671566` a enchaîné J4 phase 2 puis
J5 dans le même démarrage, terminé les deux campagnes sans retry et rendu le nouveau motif de
carton dans le snapshot incidents 185. Une campagne plus récente sur `16691018` a ensuite révélé
une séance terminale dont les quatorze tirs au but, ainsi que le marqueur `PEN`, ne portent aucune
minute exploitable. V12 traite hors ligne cette forme sans convertir l'ordre des tireurs en temps
de jeu. Une observation opérateur plus récente ajoute le motif exact de carton `Leaving field` ;
V13 hérite intégralement de V12 et ajoute uniquement ce libellé au vocabulaire fermé. Deux
campagnes humaines ont depuis qualifié le parseur courant : `16691018` pour la séance non minutée,
avec poursuite jusqu'aux compositions, puis `16851672` pour le carton `Leaving field`, également
jusqu'aux compositions. Le statut fournisseur courant est donc `YES` dans cette portée bornée.
Les chemins techniques de complétude restent persistés mais ne sont plus rendus dans les panneaux
incidents et compositions ; badges, compteurs et tables restent inchangés. Le contrôle visuel final
le confirme. Le propriétaire a également attesté le reverrouillage de la configuration et les
états J4/J5 `LOCKED` après redémarrage ; l'absence de listener sur `127.0.0.1:8087` confirme l'arrêt
final. Le Work Order est `VALIDATED` et archivable.

## 2. Frontière d'autorisation

La voie réelle n'est disponible que si les conditions suivantes sont simultanément vraies :

- `SOFASCORE_ENABLED=true` ;
- `SOFASCORE_J5_EVENT_DATA_QUALIFICATION_ENABLED=true` ;
- J3 peut être désactivé ou explicitement activé ; dans ce dernier cas `SCHEDULED_EVENTS` appartient
  obligatoirement à l'union exacte des endpoints actifs ;
- soit J4 est désactivé, soit J4 phase 2 est activé avec `EVENT_DETAILS` ; J4 phase 1 ne peut pas
  partager une session J3 ou J5 ;
- l'origine est exactement `https://www.sofascore.com`, avec un slash racine facultatif ;
- les endpoints autorisés sont exactement l'union des familles sélectionnées parmi
  `SCHEDULED_EVENTS`, `EVENT_DETAILS`, `EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS` ;
- le stockage brut est actif, la concurrence vaut un, le polling et le rafraîchissement
  automatique sont désactivés.

Les propriétés Spring autorisent J3, J4 phase 2 et J5 dans la même instance uniquement avec leur
union exacte de cinq endpoints ; toute famille absente ou supplémentaire bloque le démarrage. Le
coordinateur partagé sérialise les sections HTTP de ces trois voies et impose le même délai minimal
entre deux départs. Le catalogue général reste `callable=false`, sans URI, et `ConnectorGate` reste
bloquant. Les chemins J3/J4/J5 sont des exceptions spécialisées, temporaires et contrôlées par
leurs Work Orders. Les résultats historiques du Work Order J5 restent inchangés ; cette extension
est tracée par l'amendement opérateur du Work Order J7.

## 3. Contrôle humain

Cette section décrit exclusivement la campagne J5 unitaire d'une identité canonique, dont le cycle
de vie appartient à `J5RealControlService`. Les règles de claim, de réarmement et de redémarrage
ci-dessous ne décrivent pas le contrôle hors ligne multi-match ajouté ultérieurement par WO-010.

La préparation est locale et sans réseau. Elle reçoit l'UUID de la page et l'identifiant déjà
persisté ; leur relation déterministe est vérifiée avant de produire une phrase aléatoire valable
cinq minutes. La confirmation exige :

- le même identifiant de requête ;
- la phrase exacte comparée en temps constant ;
- un acquittement explicite ;
- une politique de configuration encore valide.

Une confirmation produit un claim immuable. `COMPLETED_LOCKED` interdit tout rejeu de ce claim,
mais autorise une nouvelle préparation explicite dans le même processus. Cette préparation ne
contacte pas le fournisseur, crée un nouvel identifiant de requête et une nouvelle phrase, remet
la liste des familles terminées à zéro et exige un nouvel acquittement avant tout transport.
`FAILED_LOCKED`, `STOPPED_LOCKED` et `EXPIRED_LOCKED` restent des verrous de processus et exigent
un redémarrage.

### 3.1 Alternative locale des trois preuves JSON

L'évolution J3 → J5 du Work Order validé `WO-SS-20260820-009`, amendée par WO-010 après la recette
du 2026-08-22, ajoute une seconde action après la même préparation : au lieu de réclamer le claim
pour les trois GET, l'opérateur fournit exactement une preuve locale pour chacune des familles
`EVENT_STATISTICS`, `EVENT_INCIDENTS` et `EVENT_LINEUPS`.
Cette extension ne change ni la qualification historique des parseurs ni l'ordre de campagne.

Une preuve est soit un fichier JSON, soit la case fermée « 404 observé ». Les deux ensemble et
l'absence des deux sont refusés. Le contrôleur applique la limite existante de 5 Mio et le scanner
sensible à chaque fichier. Le service prévalide ensuite la totalité du lot avec les parseurs
courants avant `confirmAndClaim`. Ainsi, une troisième preuve incompatible ne consomme pas une
confirmation après validation des deux premières et n'écrit aucun snapshot partiel.

La seule représentation locale d'une famille indisponible est une enveloppe JSON fermée
`{"error":{"code":404,...}}`. Le code doit être l'entier 404 ; seuls `message` et `reason`,
textuels et bornés, sont facultatifs. Un code 403 ou une propriété supplémentaire est refusé avant
claim. Si aucun téléchargement du corps 404 n'est possible, la case produit localement
`{"error":{"code":404,"message":"LOCAL_OPERATOR_DECLARED_HTTP_404"}}`. Ce marqueur atteste une
déclaration opérateur et ne prétend pas reproduire le corps fournisseur. Aucun statut libre, URI ou
en-tête n'est accepté, et l'état `notstarted` ne déclenche aucune inférence.

Après confirmation, les trois snapshots sont écrits sous le mode immuable
`MANUAL_LOCAL_JSON_IMPORT`, puis reparsés et normalisés dans l'ordre existant. Le compteur de
résultat sépare `localJsonImports=3` de `providerCallAttempts=0`. Le cache et le coordinateur réseau
ne sont jamais consultés. Un échec direct terminal ne peut pas être repris par cette voie : il faut
un redémarrage, une nouvelle préparation et une nouvelle confirmation.

WO-010 ajoute un flux distinct sous `/j5-import-batches`, pour `1..25` événements canoniques et
avec un `J5OfflineBatchControlService` séparé. Ce flux reste éligible quel que soit l'état de
`sofascore.enabled`, ne lit ni ne modifie `J5RealControlService` et n'est ni le
fallback ni le retry d'une campagne unitaire. Contrairement à cette dernière, chacun de ses états
terminaux autorise une nouvelle préparation explicite sans redémarrage.

## 4. Séquence réseau bornée

```text
identité locale vérifiée
  → EVENT_STATISTICS
  → délai minimal de 3 s
  → EVENT_INCIDENTS
  → délai minimal de 3 s
  → EVENT_LINEUPS
  → COMPLETED_LOCKED
```

Chaque requête est un `GET` vers un chemin construit localement. Le client n'utilise ni proxy,
ni redirection, ni cookie, ni jeton, ni compte, ni en-tête de navigateur. Les délais de connexion
et de lecture sont plafonnés à dix secondes, la réponse à cinq Mio et aucune API de retry n'est
exposée.

La voie d'import local remplace toute cette séquence réseau pour la campagne concernée. Elle ne
simule pas ces GET et n'ajoute aucun délai artificiel ; elle conserve toutefois le même ordre de
persistance et de normalisation ainsi que les mêmes états terminaux.

Les services réels J4 et J5 partagent un coordinateur de requêtes dans le processus. Il sérialise
les échanges, maintient `maximumConcurrency=1` même avec plusieurs onglets et applique le délai
minimal entre deux débuts de transport, y compris entre le dernier appel J4 et le premier appel J5.
Le verrou couvre l'échange HTTP et est toujours libéré à sa sortie. Les contrôles terminaux restent
séparés : un arrêt global J4 ne bloque pas la préparation J5, et inversement.

Un HTTP `404` sur l'un des trois chemins exacts n'est pas un incident de transport : la famille est
facultative et peut ne pas être publiée pour l'événement ou sa compétition. Le snapshot est
conservé, l'indisponibilité est enregistrée, aucun retry n'est effectué et la séquence continue
vers la famille suivante après le délai normal.

Au premier incident réel, les étapes restantes ne sont pas exécutées. Sont notamment terminaux :
arrêt opérateur, timeout, erreur I/O, réponse trop volumineuse, contenu sensible, HTTP `400`, `401`,
`403`, `408`, `429` ou `5xx`, autre statut non `2xx`, contenu non JSON sur une réponse `2xx`, JSON
ambigu, schéma incompatible et erreur de persistance.

## 5. Pipeline de données

Pour chaque famille, qu'elle soit acquise directement ou importée localement :

1. le transport capture les octets et leur SHA-256, ou l'import local fournit des octets déjà
   scannés et prévalidés ;
2. `provider_snapshot` reçoit la ligne `RAW_ONLY` ;
3. un HTTP `404` produit une observation vide de valeurs, de statut `UNAVAILABLE`, via le
   normaliseur d'indisponibilité de la famille ; le snapshot devient `ENDPOINT_UNAVAILABLE` et la
   campagne continue ;
4. pour une réponse `2xx`, le type de contenu et le JSON sont contrôlés ;
5. le parseur fournisseur versionné produit soit un résultat complet, soit aucun objet normalisé ;
6. la normalisation J5 est liée par `PROVIDER_SNAPSHOT` au snapshot, au hash, au parseur ou
   normaliseur et à l'heure de réception ;
7. un snapshot `2xx` nouvellement inséré est classé `PARSED` après la persistance normalisée ;
8. si les octets sont dédupliqués vers un snapshot historique déjà terminal, sa classification
   reste immuable et le résultat du parseur courant est porté uniquement par l'observation
   normalisée append-only.

Les parseurs courants sont `event-statistics-v2`, `event-incidents-v14` et `event-lineups-v2`.
L'identifiant d'événement vient du claim et non du JSON. Les champs inconnus génèrent au plus 256
avertissements.
Une liste vide structurellement valide reste `EMPTY_VALID`; une absence facultative mesurée reste
`PARTIAL`. Une famille non publiée en HTTP `404` reste `UNAVAILABLE · N/A`, ce qui est distinct
d'une liste présente et vide. Aucune valeur n'est inventée.

La migration V8 étend uniquement la contrainte de versions de parseur créée par V7. Elle ne
modifie ni V7 ni une observation existante.

La migration append-only V9 ajoute `ENDPOINT_UNAVAILABLE`, `UNAVAILABLE` et les normaliseurs
`event-*-unavailable-v1`. Elle corrige uniquement la classification des anciens snapshots J5
HTTP `404` marqués `TRANSPORT_ERROR/HTTP_STATUS_404`; les octets, hashes, heures et identifiants de
snapshot restent inchangés. Elle ne fabrique pas rétroactivement d'observation normalisée.

Le fournisseur peut porter `addedTime=999` sur un objet technique `incidentType=period`. Cette
valeur est une sentinelle et non une durée littérale à afficher ou à additionner. Le parseur
`event-incidents-v3` la conserve exclusivement dans le snapshot brut, omet le temps additionnel
normalisé et ajoute l'avertissement `PROVIDER_SENTINEL_NORMALIZED`. Le traitement est strict :
`999` reste une incompatibilité de schéma sur un carton, un but, un remplacement ou tout autre
incident. Les marqueurs globaux `period` et `injuryTime` participent à la complétude sans exiger
`isHome`. La migration append-only V10 autorise cette nouvelle provenance sans modifier V8/V9 ni
reclasser les snapshots historiques V2.

Le fournisseur représente un remplacement par deux objets distincts : `playerIn` et
`playerOut`. Le parseur `event-incidents-v4` reprend sans élargissement la règle de sentinelle V3,
puis conserve pour chaque incident `substitution` l'identifiant et le nom du joueur entrant et du
joueur sortant. Chaque identité est atomique : identifiant et nom sont présents ensemble. Si le
fournisseur omet l'un des deux objets, l'incident reste compatible mais la complétude devient
`PARTIAL` avec le chemin manquant ; aucune identité n'est inventée. La migration append-only V11
ajoute ces quatre colonnes facultatives et autorise la provenance V4 sans modifier les
observations historiques.

Un carton attribué à un joueur du banc peut porter un `time` fournisseur technique qui n'est pas une
minute de match. La seule forme réelle qualifiée porte simultanément `incidentType=card`, le
marqueur exact `time=-5` et un `benchTime` valide. `event-incidents-v5` conserve les octets bruts,
utilise exclusivement `benchTime` comme minute normalisée et ajoute l'avertissement
`PROVIDER_BENCH_CARD_MINUTE_USED`. La classe et le motif du carton sont conservés lorsqu'ils sont
fournis. Cette règle est volontairement limitée à cette combinaison exacte : toute autre valeur
négative, y compris sur un `card`, demeure `SCHEMA_INCOMPATIBLE`, et aucun champ temporel indirect
n'est interprété. La migration append-only V12 ajoute `incident_class` et `reason`, autorise la
provenance V5 et ne réécrit aucune observation historique. Les autres variantes d'incidents
restent strictes jusqu'à l'adoption d'une fiche de règles de gestion par type d'incident football.

La fiche `docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md` est désormais la source de vérité métier
des incidents de football. `event-incidents-v6` reconnaît exactement les huit types documentés :
`period`, `substitution`, `goal`, `card`, `injuryTime`, `varDecision`, `inGamePenalty` et
`penaltyShootout`. Il conserve les données applicables à chaque type, dont texte de période,
blessure, passeur, origine du but, durée du temps additionnel, décision VAR, description et ordre
de séance. Une métadonnée d'affichage attendue mais facultative peut manquer : l'incident est alors
conservé et l'observation devient `PARTIAL` avec le chemin manquant. Une contradiction atomique,
un type inconnu, une valeur hors vocabulaire, l'absence de `incidentType` ou l'absence de minute
effective reste `SCHEMA_INCOMPATIBLE` sans objet normalisé partiel, sauf pour la séance terminale
entièrement non minutée et cohérente introduite par V12. Les structures auxiliaires
restent dans le brut et ne sont interprétées que par une règle explicite, notamment le temps de la
première action d'un `penaltyShootout` lorsque son `time` global manque. La migration append-only
V13 ajoute les colonnes correspondantes, autorise la provenance V6 et ne réécrit aucune ligne
V1–V12.

`event-incidents-v7` conserve l'intégralité de ce contrat et ajoute une seule variante attestée
du type `substitution` : `incidentClass="injury"`. Cette classe représente un remplacement sur
blessure et peut être associée à `injury=true`. La contradiction explicite
`incidentClass="injury"` / `injury=false` reste `SCHEMA_INCOMPATIBLE`, tandis que l'absence du
booléen produit une complétude `PARTIAL` sans valeur inventée. Les formes historiques
`incidentClass="regular"` restent inchangées. La migration append-only V14 autorise la provenance
V7, ne change aucune colonne métier et ne réécrit ni V1–V13 ni les observations V6.

`event-incidents-v8` conserve le contrat V7 et traite quatre variantes attestées dans les deux
payloads opérateur du 2026-08-18. Un marqueur terminal exact `period/PEN`, portant simultanément
`period="penalties"`, `isLive=false`, un score complet, `time=999` et `addedTime=999`, est accepté
uniquement si la même réponse contient au moins un `penaltyShootout` dont chaque minute effective
est valide. La minute normalisée du marqueur devient alors la plus grande minute effective de la
séance ; `999` reste uniquement dans le snapshot brut. Les cartons sans `reason` restent valides,
persistés avec un motif absent et affichés par `—`, tandis qu'un motif présent mais inconnu reste
bloquant. Le triplet exact `missed/Woodwork/woodwork` est reconnu pour `inGamePenalty` et
`penaltyShootout` ; toute absence ou combinaison croisée reste incompatible. Enfin, la valeur brute
`from="shot"` observée sur un but `regular` est conservée dans le brut mais omise de l'origine
spéciale normalisée. La migration append-only V15 autorise V8 sans nouvelle colonne et sans
réécriture des observations V1–V14.

`event-incidents-v9` conserve le contrat V8 et traite la variante de carton observée dans le
snapshot 162. Le motif exact `Other reason` rejoint le vocabulaire fermé ; toute autre valeur
future reste incompatible. `benchAddedTime` est reconnu uniquement sur un `card` dont le `time`
technique est négatif, avec un `benchTime` présent et sans `addedTime` concurrent. Il devient alors
le temps additionnel normalisé, tandis que `benchTime` reste la minute : le cas attesté est donc
affiché `90+9`. Un usage sur un autre type, une minute non négative, l'absence de `benchTime` ou la
présence des deux champs de temps additionnel reste `SCHEMA_INCOMPATIBLE`. La migration append-only
V16 autorise V9 sans nouvelle colonne et sans réécriture des observations V1–V15.

`event-incidents-v10` conserve le contrat V9 et traite la valeur redondante observée dans le
snapshot 179 : `from="regular"` uniquement sur un but portant
`incidentClass="regular"`. La valeur demeure dans le snapshot brut et n'est pas persistée comme
origine spéciale ; un avertissement explicite documente son omission. Une classe `penalty` ou
`ownGoal` croisée avec `from="regular"`, ainsi qu'une autre valeur inconnue, reste
`SCHEMA_INCOMPATIBLE`. La migration append-only V17 autorise V10 sans nouvelle colonne et sans
réécriture des observations V1–V16.

`event-incidents-v11` conserve le contrat V10 et ajoute la valeur exacte
`reason="Off the ball foul"` au vocabulaire fermé des incidents `card`. Cette valeur représente une
obstruction ou une faute commise loin du ballon. Le libellé fournisseur reste inchangé dans le
snapshot brut, la raison normalisée et la colonne `MOTIF` ; aucune traduction n'est injectée dans
la donnée. Les motifs absents restent valides et toute autre valeur inconnue demeure
`SCHEMA_INCOMPATIBLE`. La migration append-only V18 autorise V11 sans nouvelle colonne et sans
réécriture des observations V1–V17.

`event-incidents-v12` conserve le contrat V11 et traite uniquement une seconde forme attestée de
séance de tirs au but. Lorsque le marqueur exact `period/PEN/penalties` est inactif, porte les
sentinelles `time=999` et `addedTime=999`, qu'un marqueur `FT` ou `ET` minuté existe, et que toutes
les tentatives omettent à la fois `time` et `footballPassingNetworkAction`, V12 accepte l'absence
de minute si les séquences sont uniques et contiguës de `1` à `N`, si chaque tentative porte sa
classe, son côté et son score, et si le score de la dernière séquence correspond au score `PEN`.
La minute normalisée reste `NULL` et l'interface affiche `—` : aucune minute n'est déduite de la
séquence. Une séance mixte, active, lacunaire, isolée ou contradictoire reste incompatible ; un
`inGamePenalty` sans minute reste également incompatible.

L'absence simultanée de `reason` et `description` sur un penalty `missed` est une absence métier
compatible et mesurée `PARTIAL` pour `inGamePenalty` comme pour `penaltyShootout`. Si un résultat
est fourni, les tuples stricts historiques — dont `Woodwork/woodwork` — restent exigés. La
migration append-only V19 autorise V12 et rend la colonne `minute` nullable sous une contrainte
qui réserve `NULL` aux seuls incidents `penaltyShootout` et marqueurs `period/PEN`, sans temps
additionnel normalisé. Elle ne réécrit aucune observation ni aucun incident V1–V18.

`event-incidents-v13` conserve toutes les règles V12 et ajoute la valeur exacte
`reason="Leaving field"` au seul vocabulaire des incidents `card`. Elle représente un joueur
quittant le terrain sans autorisation préalable. Le libellé fournisseur est conservé tel quel dans
le snapshot brut, la raison normalisée et la colonne `MOTIF`; aucune traduction ni catégorie plus
large n'est déduite. V12 rejette encore cette forme sur le chemin `reason`, V13 l'accepte, et une
valeur future non documentée reste `SCHEMA_INCOMPATIBLE`. La migration append-only V20 autorise
V13 sans modifier la contrainte temporelle de V19 et sans réécrire les observations V1–V19.

`event-incidents-v14` conserve toutes les règles V13 et ajoute uniquement le libellé exact
`text="Extra time"` aux incidents `period` live. Cette valeur représente une prolongation en cours,
reste distincte du marqueur terminal `ET` et est conservée sans traduction. Une valeur
`isLive=false` explicite produit `SCHEMA_INCOMPATIBLE`; l'absence de `isLive` reste une donnée
partielle mesurée. La migration append-only V26 autorise V14 sans colonne nouvelle et sans
réécrire les observations V1–V25.

La déduplication brute est indépendante du parseur courant. Une réponse incidents identique peut
donc résoudre un snapshot V2 historiquement `SCHEMA_INCOMPATIBLE` alors que V4 la parse avec
succès. Dans ce cas, la campagne ne tente ni `UPDATE` ni reclassification du snapshot : elle ajoute
une observation V4 liée à la même preuve et poursuit vers `EVENT_LINEUPS`. Seul un brut
nouvellement inséré reçoit la classification de la campagne courante.

## 6. Résultat et confidentialité

L'interface affiche uniquement le code terminal, le nombre de tentatives et, pour chaque famille
traitée, l'endpoint logique, l'identifiant du snapshot, la taille, le SHA-256, la complétude ou
`UNAVAILABLE · N/A`, et l'identifiant d'observation. Aucun octet brut, URI complète, en-tête ou
texte de confirmation consommé n'est journalisé ou ajouté aux preuves.

La vue locale des incidents affiche séparément le joueur générique, le joueur entrant, le joueur
sortant, le passeur, la classe, le motif, le détail VAR/temps additionnel et l'ordre de séance de
tirs au but. Ces valeurs proviennent exclusivement de l'observation normalisée ; un tiret signifie
que le fournisseur n'a pas fourni la donnée correspondante ou que le champ ne s'applique pas au
type d'incident.

## 7. État de qualification

Les formes fournisseur ont d'abord été qualifiées avec des fixtures minimales créées localement.
La première campagne humaine a
montré qu'un HTTP `404` de `EVENT_STATISTICS` peut exprimer une famille indisponible, notamment
dans le contexte opérateur d'une compétition non majeure. Ce signal ne valide pas le schéma
nominal de statistiques ; seule une réponse `2xx` effectivement parsée peut faire évoluer
`PROVIDER_SCHEMA_VALIDATED`. Une campagne ultérieure a effectivement parsé les statistiques de
`16391135`, puis deux réponses incidents réelles distinctes ont révélé la sentinelle de période
rejetée par V2. Un retest humain a ensuite confirmé V3 sur la réponse réelle dédupliquée du
snapshot 32 : 20 incidents sur 20 sont visibles. Il a aussi révélé que la tentative de reclasser
ce snapshot historique arrêtait la campagne avec `RAW_CLASSIFICATION_ERROR` avant
`EVENT_LINEUPS`, ainsi que l'absence de conservation de `playerIn` et `playerOut`. V4, V11 et la
politique de déduplication ont corrigé ces deux défauts. Le retest humain suivant a confirmé le
parcours complet sur `16412917` : statistiques HTTP `404` normalisées en indisponibilité,
incidents parsés par V4 avec joueurs entrant et sortant, puis compositions parsées par V2, après
exactement trois appels sans retry. Il a aussi révélé que `COMPLETED_LOCKED` empêchait à tort de
préparer une campagne distincte pour `16391135` dans la même instance. Le contrôle autorise
désormais ce nouveau départ explicite après un succès, sans ouvrir les verrous d'échec, d'arrêt ou
d'expiration. Le retest humain a qualifié ce cycle : une campagne réussie sur `16391135` a été
suivie, dans la même instance, d'une campagne distincte sur `16483632`. Celle-ci a parsé les
statistiques, puis son snapshot incidents 60 a révélé un carton de banc avec `time=-5`,
`benchTime=58`, une classe jaune et le motif `Argument`. V4 a arrêté la campagne avant les
compositions conformément à sa règle stricte. V5 et V12 ont corrigé cette forme. Les campagnes
humaines suivantes ont terminé les trois familles, notamment Paris Saint-Germain — Lens pendant la
première mi-temps sur les snapshots 61/62/63, puis lors d'un rejeu manuel à la fin du match sur les
snapshots 64/65/66. La fiche métier complète a ensuite été transcrite dans V6/V13. Son retest réel
sur Lens — Paris Saint-Germain a qualifié les marqueurs `HT` et `FT`, le carton de l'entraîneur,
les durées de temps additionnel et les participants, avec les snapshots dédupliqués 64/65 et un
nouveau snapshot compositions 69.

La campagne suivante, Arsenal — Manchester City, a parsé les statistiques dans le snapshot 70,
puis conservé avant rejet le snapshot incidents 71. Parmi ses 23 incidents, une substitution à la
minute 46 porte `incidentClass="injury"`, `injury=true`, Jack Grealish entrant et Jérémy Doku
sortant. V6 ne connaissait que la classe `regular`, d'où le rejet intégral de la famille et
l'absence d'appel `EVENT_LINEUPS`, conformément au verrou sans retry. V7/V14 corrigent uniquement
ce vocabulaire attesté ; le payload minimisé complet est validé hors ligne avec 23 incidents sur
23, et le service prouve la poursuite vers les compositions. Quatre campagnes complètes du
2026-08-17 ont ensuite qualifié V7 en conditions réelles. France — Maroc et Argentine — Autriche
contiennent notamment des remplacements sur blessure et des penalties ratés, tandis que les deux
autres campagnes couvrent les périodes, cartons, buts, remplacements et VAR applicables.

Deux payloads ensuite fournis par l'opérateur ont été rejoués exclusivement hors ligne avec V8.
Le premier contient 36 incidents : un marqueur terminal `PEN/time=999`, neuf tirs au but sans
minute globale mais avec une minute dans leur première action imbriquée, et une dernière minute
effective égale à 146. V8 conserve les 36 incidents et normalise uniquement le marqueur à 146. Le
second contient 16 incidents, dont trois cartons sans motif et trois buts ordinaires portant
`from="shot"` ; V8 conserve les 16 incidents, les trois motifs absents et omet les trois origines
non spéciales. Aucun payload complet n'est ajouté au dépôt. Le service hors ligne prouve encore la
poursuite unique vers `EVENT_LINEUPS`.

Le retest humain V8 du 2026-08-18 a terminé trois campagnes `COMPLETED_LOCKED`, avec trois appels
ordonnés chacune. IF Gnistan — Ilves a conservé une indisponibilité statistiques, accepté les
cartons sans motif du snapshot 144 et atteint les compositions du snapshot 145. Al Orobah — Abha a
reparsé par V8 le snapshot 139 historiquement rejeté : 36 incidents, 150 signaux sur 150, neuf
tirs au but et marqueur `PEN` ramené à la dernière minute effective 146, puis compositions dans le
snapshot 148. Samsunspor — Göztepe a produit les snapshots 149/150/151 ; l'observation incidents V8
contient 27 incidents, 102 signaux sur 102 et le triplet `inGamePenalty/missed/Woodwork` à la minute
58.

La variante réelle `Woodwork/woodwork` d'un `penaltyShootout` n'apparaît pas dans les captures :
elle reste couverte par les régressions de contrat, de service et de persistance V15, tandis que la
séance réelle qualifie séparément neuf `penaltyShootout` sous V8. Les deux campagnes V8 dont les
trois familles répondent en `2xx` constituent une qualification historique bornée ; elles ne
garantissent pas qu'une forme ultérieure reste compatible.

Cette limite s'est matérialisée sur Cardiff City — Wrexham (`16391145`). Les statistiques du
snapshot 161 ont été traitées, puis V8 a rejeté le snapshot incidents 162 en HTTP `200` sur
`$.incidents[19].reason="Other reason"`. Le même carton de banc porte `time=-5`, `benchTime=90` et
`benchAddedTime=9`. La campagne a correctement terminé `FAILED_LOCKED` après deux appels, sans
retry et sans tenter les compositions. V9 rejoue hors ligne le payload complet avec 20 incidents
sur 20, aucun problème de schéma et une minute `90+9`. Les tests standard (344) et
PostgreSQL/Testcontainers (30) passent, migration V15 → V16 incluse.

Le retest humain V9 sur la même identité atteint `COMPLETED_LOCKED` après exactement trois appels.
Les statistiques réutilisent le snapshot 161 et l'observation 75. Le snapshot incidents 162 est
reparsé dans l'observation append-only 77 à `COMPLETE · 100%` : les 20 incidents sont visibles et
le carton auparavant bloquant est rendu à `90+9` avec le motif `Other reason`. La troisième famille
produit le snapshot compositions 165 et l'observation 78, `event-lineups-v2`, `COMPLETE · 100%`,
avec `85/85`. À ce stade, cette campagne portait le schéma V9 à
`PROVIDER_SCHEMA_VALIDATED=YES` pour la seule portée réelle observée.

Une campagne ultérieure sur `16251993` a rendu cette qualification historique. Les statistiques
du snapshot 178 sont complètes (`4/4`, observation 91), puis V9 a rejeté le snapshot incidents 179
sur les cinq chemins `from` de buts `regular`. Le brut HTTP `200` de 6 423 octets a été conservé,
la campagne a terminé `FAILED_LOCKED` après deux appels sans retry et les compositions n'ont pas
été tentées. Le payload complet fourni par l'opérateur passe sous V10 avec 7 incidents sur 7,
`COMPLETE · 100% · 24/24`, sept avertissements bornés et aucun problème. Les tests standards (348)
et PostgreSQL/Testcontainers (31) passent, migration V16 → V17 incluse. À cette étape historique,
le statut est donc resté `PROVIDER_SCHEMA_VALIDATED=NO` dans l'attente d'un retest humain.

Avant ce retest, une nouvelle valeur de carton a été fournie directement par l'opérateur :
`card/yellow`, minute 77, `reason="Off the ball foul"`. V10 la refuse exactement sur `reason` ; V11
la traite hors ligne à `COMPLETE · 100% · 3/3`, conserve le motif exact et poursuit une campagne de
service simulée jusqu'aux compositions après trois transports sans retry. Le test V17 → V18
préserve l'historique V10. Avant l'ajout du mode combiné, les suites passaient avec 351 tests
standards et 32 tests PostgreSQL/Testcontainers.

Le premier retest humain V11 a atteint `COMPLETED_LOCKED` sur `16251993` après trois appels
sans retry. Le snapshot incidents 179 est reparsé dans l'observation append-only 93 par
`event-incidents-v11` à `COMPLETE · 24/24`; les cinq buts réguliers sont visibles et la campagne
atteint les compositions snapshot 182, observation 94, `COMPLETE · 67/67`. Cette preuve porte le
statut à `PROVIDER_SCHEMA_VALIDATED=YES` dans cette première portée bornée. Aucun carton
`Off the ball foul` n'apparaît dans ses sept incidents ; ce point restait alors à qualifier.

Le mode combiné J4 phase 2 + J5 est qualifié hors ligne avec 358 tests standards et 32 tests
d'intégration, puis en conditions réelles sur Barracas Central — Rosario Central (`16671566`).
Dans un même démarrage, J4 phase 2 termine après un appel et le snapshot 183 ; J5 termine ensuite
trois appels ordonnés, sans retry, avec les snapshots statistiques 184, incidents 185 et
compositions 186. L'observation incidents 96 est `COMPLETE · 69/69` et conserve exactement le
motif `Off the ball foul` sur le carton jaune extérieur de Facundo Mallo à la minute 77. Les
compositions atteignent `COMPLETE · 95/95`. Cette campagne étend la portée bornée de qualification
V11 aux événements `16251993` et `16671566` et valide le parcours combiné sans redémarrage.

Un contrôle local postérieur à la campagne ne trouve ni listener sur `127.0.0.1:8087`, ni processus
Java du laboratoire. La configuration devra encore être remise à l'état bloqué, ce verrouillage
vérifié après redémarrage sur les deux parcours J4 et J5, puis l'instance de contrôle arrêtée avant
l'archivage du Work Order.

Une campagne ultérieure sur l'événement `16691018` rend cette qualification V11 historique. Les
statistiques sont conservées comme indisponibles dans le snapshot 188, puis le snapshot incidents
189 est persisté avant que V11 ne le rejette. La campagne termine `FAILED_LOCKED` après deux appels,
sans retry et sans tentative de compositions. Le diagnostic hors ligne du payload opérateur
identifie exactement quinze absences temporelles : le marqueur `PEN` et quatorze
`penaltyShootout`. Les sept tentatives ratées omettent aussi simultanément `reason` et
`description`, mais ces champs facultatifs ne provoquent aucun problème de schéma.

Le même payload passe sous V12 puis sous le parseur courant V13 avec 33 incidents, quatorze tirs au
but, quinze avertissements `PROVIDER_SHOOTOUT_MINUTE_ABSENT`, aucune minute inventée et une
complétude `PARTIAL`. Le retest humain V13 de `16691018` termine ensuite `COMPLETED_LOCKED` après
exactement trois appels : snapshot 188 indisponible, snapshot incidents 189 / observation 100 à
`PARTIAL · 83% · 142/171`, puis snapshot compositions 192 / observation 101 à
`PARTIAL · 98% · 94/95`. La table conserve ses 33 incidents et affiche `—` pour le marqueur `PEN`
et les quatorze tirs au but non minutés.

Une seconde campagne humaine V13 sur Shanghai Shenhua — Beijing Guoan (`16851672`) termine aussi
`COMPLETED_LOCKED` après trois appels. Les snapshots 194/195/196 donnent respectivement les
observations 102/103/104 ; incidents est `COMPLETE · 81/81` et conserve `Leaving field` dans
`MOTIF` pour le carton jaune extérieur de Yongjing Cao à la minute 74, puis compositions est
`COMPLETE · 97/97`. Ces deux campagnes portent `PROVIDER_SCHEMA_VALIDATED=YES` pour la portée V13
observée. La liste brute des chemins manquants n'est plus rendue dans les panneaux incidents et
compositions, sans modifier les rapports persistés ni leurs tables. Les captures finales confirment
ce rendu, le maintien des contenus, le reverrouillage J4/J5 et l'arrêt de l'instance de contrôle.
