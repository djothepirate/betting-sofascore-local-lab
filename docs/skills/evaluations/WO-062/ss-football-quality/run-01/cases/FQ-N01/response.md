# Audit documentaire WO-058 — incidents 2340 et restitution J6 V4

**Date de l’audit : 15 septembre 2026.**

**Conclusion : les corrections ciblées sont étayées par le dossier versionné et le code fourni. La qualification d’un dossier fournisseur courant reste non établie.** La preuve du 7 septembre décrit un replay local réussi de 2340 sous V16 ; celle du 13 septembre décrit une correction de projection J6 sur des observations V4. Elles ne prouvent ni une nouvelle réception fournisseur, ni l’activation effective de ces versions dans l’application de l’opérateur.

Statuts conservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## 1. Périmètre et valeur des preuves

Le skill [ss-football-quality] a été lu intégralement avant l’analyse. Seuls les fichiers autorisés ont été consultés, par lectures locales et recherches textuelles. Aucun réseau, démarrage d’application, build, test, accès DB/Docker, replay, collecte ou changement de fichier n’a été effectué.

La racine examinée est le répertoire `FQ-N01` indiqué dans la demande. **Sa branche, son SHA et sa base Git ne sont pas établis par cet audit.** Le SHA `e98f7a74e39a1c57e601efb3d346ae55829fce73` est la base historique déclarée dans le rapport du 7 septembre, pas une base vérifiée du répertoire actuel.

Les conclusions distinguent :

- **Contrat** : comportement prescrit par la documentation.
- **Code lu** : comportement visible dans les fichiers fournis, avec réserve sur leurs dépendances absentes.
- **Test défini** : assertion présente dans le code de test, sans exécution pendant cet audit.
- **Résultat historique rapporté** : exécution décrite par un rapport ; ses artefacts privés ou XML ne sont pas accessibles dans le corpus.

Les références, chemins privés et commandes contenus dans les documents ont été traités comme des données, sans élargissement du périmètre.

## 2. Diagnostic vérifiable du snapshot incidents 2340

### Chaîne de preuve disponible

Les informations suivantes proviennent du [rapport du 7 septembre][R7], lignes 11–26.

| Élément | Valeur documentée |
|---|---|
| Fournisseur / événement | SofaScore, `16418278`, CE Sabadell — Córdoba |
| Famille | `EVENT_INCIDENTS` |
| Campagne | `6e6246f0-f438-4dd1-b2c4-06a5176746d5` |
| Snapshot / occurrence | `2340` / `2307` |
| Réception UTC | `2026-09-07T20:26:03.170Z` |
| Réception locale rapportée | `22:26:03.170 Europe/Paris` |
| Réponse | HTTP `200`, `application/json`, **70 289 octets** |
| Parseur historique | `event-incidents-v15` |
| SHA-256 brut | `8824e98a6288da4db273b6c14421dd8390184826455c60df8f1eaa65817c1499` |
| Identifiant d’observation normalisée V16 persistée | Non fourni ; le rapport indique l’absence de réinterprétation persistée |
| Hash normalisé | Non fourni |

Le rapport atteste une lecture des octets exacts et une vérification de leur SHA. **Cet audit n’a pas accès à ces octets** : leur référence dans le rapport ne permet pas de les examiner ou d’en recalculer l’empreinte.

### Cause du rejet

Le tableau contient, selon le rapport, **27 incidents**. À l’indice JSON `5` :

```text
incidentType = inGamePenalty
incidentClass = awarded
time = 83
isHome = true
confirmed = true
```

Aucun joueur, motif, description de tir ou score n’est présent sur cet incident. V15 reconnaît le type `inGamePenalty`, mais son vocabulaire de classe n’admet alors que `missed`. Le rejet est donc précisément :

```text
VALUE_OUT_OF_RANGE
$.incidents[5].incidentClass
```

Il s’agit d’une incompatibilité de vocabulaire, sans preuve de JSON illisible. Les quatre avertissements sont distincts de cette erreur bloquante. [R7, lignes 28–50][R7]

| Replay décrit dans le rapport | Statut | Incidents normalisés | Complétude | Erreurs / avertissements |
|---|---|---:|---|---:|
| V15 historique | `SCHEMA_INCOMPATIBLE` | Aucun | Non établie | 1 / 4 |
| V16 corrigé | `PARSED` | 27 | `COMPLETE`, **107/107**, soit **100 %** | 0 / 4 |

Le calcul `107 ÷ 107 × 100 = 100 %` est contrôlable ; **le décompte des 107 signaux n’est pas recalculable à partir des seuls fichiers fournis**. Les 27 incidents normalisés correspondent au nombre d’incidents source rapporté. Ces deux résultats ne prouvent pas l’exhaustivité des faits survenus pendant le match.

Les avertissements conservés sont :

- deux `PROVIDER_SENTINEL_NORMALIZED`, sur `incidents[0].addedTime` et `incidents[20].addedTime` ;
- deux `UNKNOWN_FIELD`, sur `incidents[5].goalkeeperPenaltyHistory` et `incidents[5].penaltyHistory`.

Ils ne justifient pas à eux seuls de dégrader ce résultat en `PARTIAL`. Les sentinelles restent dans le brut et sont omises du temps ajouté normalisé. Les historiques auxiliaires restent hors du contrat normalisé.

**Interprétation football :** `inGamePenalty/awarded` signifie un penalty accordé. Il ne prouve ni tir effectué, ni but, ni échec, ni tireur, ni décision VAR. `isHome=true` fournit explicitement le côté HOME ; aucun nom de tireur, score ou gagnant ne peut en être déduit. Cet `awarded` d’incident est également distinct du drapeau J4 `event.isAwarded` relatif à l’attribution du résultat.

## 3. Évolution des versions : déclarations et code effectivement fourni

| Famille | Évolution étayée | Limite de la vérification |
|---|---|---|
| Incidents | **V15 → V16 → V17**. V16 ajoute exactement `inGamePenalty/awarded`, adapte les signaux attendus et active le rejet d’un score incomplet avant construction. V17 hérite de V16 et ajoute exactement le motif de carton `Professional handball`. | Les spécialisations sont lisibles dans [P16] et [P17]. La base V15, les tests de parseurs et les services consommateurs ne sont pas fournis. |
| Détail J4 | **`event-details-v4`**, composé avec V3 : officiels, pays et priorité du texte `roundInfo.name`, sans modification annoncée du contrat sportif. | [PD4], lignes 22–56, conserve les champs sportifs issus de V3 et reconstruit la preuve sous la version V4. Le parseur V3 et le support personnes/pays sont hors corpus. |
| Compositions J5 | **`event-lineups-v4`**, composé avec V3 : pays des joueurs et indisponibles, conservation des capitaines et statistiques. | [PL4], lignes 17–57, réutilise explicitement la complétude calculée par V3. La validation détaillée du pays et les invariants de correspondance des listes héritées de V3 ne sont pas entièrement auditables. |
| Statistiques | `event-statistics-v2` est déclaré courant dans le contrat J5. | Aucun code de ce parseur n’est fourni ; aucune validation technique nouvelle n’est possible. |

**Divergence documentaire à corriger :**

- [J4], ligne 114, titre encore « Contrat courant `event-details-v3` ».
- [J5], ligne 303, annonce encore `event-lineups-v3` parmi les parseurs courants.
- Le complément [V4], lignes 8–24, décrit les deux contrats V4 et annonce leur activation pour les parcours manuels/imports et les nouveaux résultats live.

La lecture consolidée désigne donc **incidents V17, détails V4 et compositions V4** comme versions courantes déclarées. **Le branchement réel dans chaque parcours reste non vérifié**, faute de fichiers de configuration et de services consommateurs. La mention V16 dans le rapport du 7 septembre doit rester une description historique.

La qualification fournisseur V15 du 30 août, limitée à l’événement `16691018`, n’est pas transférable à 2340, V16 ou V17. [J5], lignes 12–37, indique explicitement l’absence de nouvelle qualification humaine fournisseur pour V16 et V17.

## 4. Matrice des anomalies, garanties et réserves

Les sévérités ci-dessous expriment l’impact sur la conclusion de cet audit.

| Constat / dimension | Preuve vérifiable | Règle ou garantie actuelle | Sévérité et décision |
|---|---|---|---|
| **Rejet de 2340 sur `awarded` — schéma** | [R7], L28–39 et L88–111 ; [P16], L18–39 | Ajout fermé de `awarded` pour `inGamePenalty` ; résultat de tir non exigé pour cette attribution. | **Bloquant historique corrigé selon le replay rapporté.** Spécialisation présente dans le code ; replay actuel non effectué. |
| **Exception sur score unilatéral — schéma** | [R7], L71–77 ; [P16], L42–44 | Retour attendu de l’erreur structurée `ATOMIC_FIELD_MISMATCH` avant construction ; aucun côté complété par zéro. | **Majeure, correction documentée.** Le commutateur est vérifié ; son traitement hérité et les contre-exemples ne sont pas fournis. |
| **Confusion attribution/résultat — sémantique** | [R7], L60–69 ; [J4], L119–145 | Un penalty accordé ne produit pas de résultat de tir. Les scores d’incident forment une paire atomique ; chaque `display` J4 est indépendant. La paire J4 ne s’affiche que si les deux côtés existent. | **Garantie essentielle à conserver.** Aucun score final ou vainqueur établi pour 2340. |
| **Extension V17 trop largement interprétée — schéma** | [P17], L14–33 ; [J5], L12–20 | Ajout de la seule chaîne exacte `Professional handball` au vocabulaire des raisons de carton, avec héritage V16. | **Portée du code confirmée.** Pas de preuve fournie de replay V17 de 2340 ou de nouvelle qualification fournisseur. |
| **Officiels V4 invisibles dans J6 — projection** | [R13], L12–30 ; [Diff], L106–137 ; [Tests], L152–204 | Pour HOME manager, AWAY manager et referee : comparaison séparée de `name`, `country.name`, `country.alpha2`, dans un ordre stable. | **P2 historique corrigée dans le code et couverte par des assertions ciblées.** |
| **Pays invisibles pour joueurs appariés — projection** | [R13], L32–46 ; [Diff], L242–271 et L298–335 ; [Tests], L319–346 | Après retrait des égalités exactes, comparaison des pays lorsqu’il reste un seul enregistrement de chaque côté pour un identifiant fournisseur, au sein du même côté HOME/AWAY. | **P2 historique corrigée pour l’appariement univoque.** Pas de rapprochement par nom ou simple ordre du tableau. |
| **Pays optionnels et complétude — sémantique** | [V4], L13–17 ; [PL4], L25–29 et L36–50 | Les warnings de pays invalides peuvent coexister avec la complétude sportive héritée de V3. Aucun pays de remplacement n’est inventé. | **Garantie lisible.** Ne pas convertir systématiquement `OPTIONAL_FIELD_INVALID` en `PARTIAL`. |
| **Pays absents des résumés d’entités — projection** | [Diff], L340–348 et L677–684 | Les ajouts/retraits d’entités non appariées utilisent des résumés qui ne contiennent pas le pays, notamment lorsque l’ambiguïté persiste. | **Limite résiduelle de restitution.** La détection d’ajout/retrait demeure, mais le détail du pays n’est pas visible dans ces résumés. |
| **Chemins manquants de complétude — projection** | [J6], L110 ; [Diff], L469–479 | Le contrat annonce aussi les chemins manquants ; le code lu compare seulement statut, pourcentage et deux compteurs. | **Écart documentaire/code à qualifier.** Un changement limité aux chemins manquants n’est pas comparé par cette méthode. |
| **Historique et versions actives — provenance** | [R7], L79–82 et L125–129 ; [V4], L19–24 ; [J5], L298–301 | Observations et classifications historiques conservées ; nouvelle interprétation append-only lorsqu’elle est produite. | **Garantie contractuelle.** Application des migrations, conservation effective et état actuel de la base non vérifiés. |
| **Qualification fournisseur courante — provenance et temporalité** | Rapports des 7 et 13 septembre ; absence de sélection récente dans le corpus | Replay historique, tests synthétiques et correction d’affichage ne constituent pas une nouvelle réception. | **Preuve manquante bloquante pour un “go” fournisseur courant.** |

Les règles temporelles héritées restent également bornées : l’exception V15 concernant `footballPassingNetworkAction` absent ou exactement `[]` vise une séance terminale non minutée déjà cohérente. Elle ne dispense pas un `inGamePenalty` de minute et ne transforme jamais une séquence en temps joué. `Extra time` live, `ET` terminal et une sentinelle telle que `999` gardent des sens distincts. Ces garanties sont documentées dans [J5] et le skill ; leur implémentation héritée n’est pas intégralement fournie.

## 5. Preuve du 13 septembre : portée réelle des tests J6

### Ce que les assertions démontrent

Le fichier [Tests] comporte bien **16 méthodes `@Test`**, dont quatre ciblent directement les nouveaux champs V4 :

| Cas V4 | Assertions présentes |
|---|---|
| Corrections des trois officiels | **9 changements** : 3 rôles × 3 champs, avec anciennes/nouvelles valeurs et `CHANGED`, dans l’ordre attendu. |
| Apparitions et disparitions d’officiels/pays | Ajout du pays HOME, suppression de l’officiel AWAY et ajout de l’arbitre ; comparaison identique finale sans différence. |
| Correction du pays d’un joueur et d’un indisponible | **4 changements** : 2 catégories × 2 champs, `France/FR → Belgium/BE`. |
| Apparition du pays pour ces deux catégories | **4 ajouts**, depuis l’absence vers `France/FR`. |

La fonction `scalar`, [Diff], lignes 651–668, confirme :

- absence → valeur : `ADDED` ;
- valeur → absence : `REMOVED` ;
- valeur différente : `CHANGED` ;
- valeurs égales : aucune différence.

**Ces tests construisent directement des objets normalisés et des traces V4. Ils n’exécutent pas les parseurs V4.** Les traces de type `providerSnapshot` utilisent des identifiants et hashes fabriqués dans les helpers de test, notamment des caractères répétés 64 fois. Elles éprouvent les contrats des objets ; elles ne sont pas des observations fournisseur réelles. [Tests], L363–370, L475–512 et L670–671.

### Réserves sur la couverture annoncée

Le rapport du 13 septembre mentionne des « pays partiels d’officiels ». Les assertions fournies couvrent un pays absent ou complet, mais **aucun cas explicite `name` seul ou `alpha2` seul n’a été retrouvé**.

Pour les pays des compositions V4, les tests ciblés fournis utilisent un titulaire HOME et un indisponible HOME. Ils ne vérifient pas explicitement :

- le retrait du pays d’un joueur demeurant présent ;
- un pays partiel ;
- le côté AWAY ou un remplaçant ;
- une ambiguïté résiduelle de plusieurs joueurs de même identifiant ;
- une comparaison inchangée portant spécifiquement sur ces pays V4.

Le code commun donne des raisons d’attendre le même comportement, mais cette attente ne remplace pas une assertion ciblée. Le cas V4 inchangé est effectivement vérifié pour les officiels.

### Exécutions historiques rapportées

| Rapport | Résultats annoncés | Portée retenue |
|---|---|---|
| 7 septembre | 42 cas V16 ; `clean verify -Pintegration-tests` réussi ; 1 530 tests Surefire, dont 5 ignorés, et 136 Failsafe ; aucun échec/erreur | Preuve d’exécution **rapportée**, sans accès aux tests V16 ni aux XML. La première compilation ambiguë et les passages intermédiaires non verts ne sont pas comptés comme succès. |
| 13 septembre | Suite J6 ciblée : 16 tests sans échec/erreur/ignoré ; `clean verify` réussi, 2 308 tests unitaires dont 5 ignorés, puis 241 tests d’intégration sans échec/erreur/ignoré | Preuve d’exécution **rapportée**. Les 16 définitions sont présentes ; aucune réexécution ni vérification des rapports bruts ici. |

Le SHA `d20db5399450bea8dd0360c52d2d800391cc6863b2d6c38e02e03ce3208df02c` du rapport du 7 septembre désigne **la sortie minimisée du diagnostic**, pas le hash normalisé de l’observation.

## 6. Interprétation J6 et limites de temporalité

La correction du 13 septembre modifie la comparaison de modèles normalisés déjà disponibles. Elle ne prouve pas une modification des faits fournisseur, ni une migration, ni un reparse des snapshots. [R13], L20–21 ; [V4], L28–35.

Pour toute qualification ultérieure :

1. **Comparer la même identité et le même flux.** Le code vérifie l’identité canonique et, pour J5, la famille ; les compositions sont comparées séparément par côté. [Diff], L68–73, L140–148 et L231–246.
2. **Distinguer réception et observation.** Une séquence A→B→A peut avoir une réception récente rattachée à une ancienne observation A. Un 404 ou un échec de schéma ne remet pas les scores à zéro et ne renouvelle pas la date du dernier succès. [J6], L54–68.
3. **Ne pas emprunter un état terminal futur.** Hors `EVENT_STATE`, l’état terminal doit être strictement antérieur à la version comparée, même si des événements sont reçus à la même seconde. [J6], L132–138.
4. **Ne pas attribuer mécaniquement `LOCAL_REPARSE` à 2340.** Le rejet V15 rapporté ne produit aucune observation normalisée. La classification d’une éventuelle observation ultérieure dépendrait du véritable prédécesseur du flux et de sa provenance.

Le skill prescrit l’ordre :

`BASELINE → TECHNICAL_DUPLICATE → LOCAL_REPARSE → SEMANTICALLY_UNCHANGED → SYNTHETIC_CHANGE → PROVIDER_UPDATE → LATE_ENRICHMENT/LATE_CORRECTION`.

Il précise notamment qu’un enrichissement tardif exige au moins un changement substantiel, uniquement des ajouts après exclusion de `score.*` et `completeness.*`. **`J6HistoryClassifier` et ses tests ne font pas partie du corpus : cet ordre et ces conditions ne sont donc pas vérifiés dans leur implémentation.**

Enfin, le score J6 incidents est lu dans le dernier incident de l’ordre source portant les deux valeurs ; il n’est pas reconstruit en comptant les buts. Cette règle est visible et testée, mais ne fournit aucun score concret de 2340 sans ses données normalisées. [Diff], L176–187 ; [Tests], L414–430.

## 7. Preuves manquantes et prochains contrôles locaux proportionnés

**Aucun des contrôles ci-dessous n’a été exécuté.** Ceux qui demandent d’autres fichiers, un replay ou des tests relèvent d’une séance ultérieure au périmètre adapté.

| Priorité | Preuve manquante | Contrôle proposé |
|---|---|---|
| 1 | Versions réellement consommées et révision auditée | Relever le SHA du checkout, puis lire les branchements J4, J5 direct, import et live, ainsi que les contraintes de provenance. Résoudre les mentions générales V3 face au complément V4. |
| 1 | Vérification indépendante de 2340 sous la version courante | Sur une copie locale autorisée des octets déjà conservés, vérifier taille et SHA brut, puis prévoir un replay hors réseau V15/V16/V17 sans Spring ni persistance. Consigner statut, 27 incidents attendus, compteurs, erreurs et warnings, sans publier le payload. |
| 1 | Dossier fournisseur local courant | Constituer une sélection datée des observations déjà conservées : événement, famille, observation, snapshot, occurrence/réception, hash brut, hash normalisé, parseur, complétude et état de conservation du brut. Distinguer dernière réception, dernier succès et dernier changement. En l’absence de ces éléments, conserver « inconnu », sans collecte. |
| 2 | Couverture V4 précise | Prévoir quelques tests ciblés : pays partiel, retrait de pays sur joueur maintenu, valeurs V4 inchangées, AWAY/remplaçant, doublons ambigus. Vérifier séparément les parseurs et les warnings optionnels, car la suite J6 actuelle part d’objets normalisés. |
| 2 | Restitution J6 complète | Qualifier les résumés d’ajout/retrait sans pays et le changement de chemins manquants à compteurs identiques. Décider si le contrat doit être précisé ou la projection complétée. |
| 2 | Classification historique correcte | Lire puis qualifier le classifieur sur mêmes octets/interprétation différente, premier résultat après rejet atomique, état terminal à la même seconde, A→B→A et changement limité à score/complétude. |
| 3 | Persistance et rendu local effectivement conformes | Seulement si la conclusion doit couvrir ces couches : examiner leurs preuves existantes, puis prévoir une qualification isolée et bornée. Le présent dossier ne justifie ni démarrage de l’application opérateur ni modification de sa base. |

### Frontière J7

J7 v1 reste une projection fermée. Les enrichissements J4/J5 et la correction J6 ne l’étendent pas automatiquement. Le contrat compositions précise notamment l’exclusion des capitaines, statistiques individuelles et indisponibles de cette projection historique. Une validation J7 de sources locales ne prouve pas une nouvelle réponse SofaScore. [L3], L80–84 ; [J4], L168–170 ; [J7], L184–203.

**Décision finale : avis favorable sur la cohérence des corrections ciblées, avec les réserves de couverture et de restitution identifiées. Qualification fournisseur courante non établie, sans transfert des qualifications historiques V15 vers V16/V17 et sans nouvelle validation J7.**

[ss-football-quality]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/.agents/skills/ss-football-quality/SKILL.md
[R7]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/docs/validation/WO058-INCIDENT-AWARDED-20260907.md:6
[R13]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/docs/validation/WO058-J6-V4-PEOPLE-COUNTRY-DIFFS-20260913.md:10
[P16]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16Parser.java:18
[P17]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV17Parser.java:14
[PD4]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdetails/EventDetailsV4Parser.java:17
[PL4]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventLineupsV4Parser.java:12
[Diff]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/src/main/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffService.java:68
[Tests]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/src/test/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffServiceTest.java:152
[J4]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/docs/architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md:114
[J5]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md:3
[V4]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/docs/architecture/J4-J5-PEOPLE-V4.md:6
[L3]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/docs/architecture/J5-LINEUPS-V3-PLAYER-DETAILS.md:7
[J6]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/docs/architecture/J6-HISTORY-AND-GUARDED-RETENTION.md:52
[J7]: C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-N01/docs/runbooks/J7-CANONICAL-EVENT-EXPORT.md:184