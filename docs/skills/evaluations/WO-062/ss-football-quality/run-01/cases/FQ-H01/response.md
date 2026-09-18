# Réexamen de WO-017 — snapshot 717 et incidents V15

**Conclusion : WO-017 documente une incompatibilité de représentation JSON sous V14, corrigée par une extension étroitement bornée de V15.** Le snapshot 717 utilisait `footballPassingNetworkAction: []` sur quatorze tirs au but sans minute. L’exception historique de séance terminale n’acceptait que la propriété absente. V15 ajoute l’équivalence avec le tableau exactement vide, sous les mêmes conditions de cohérence terminale.

La qualification fournisseur ultérieure a, **selon le compte rendu**, réobservé cette variante sur **un événement**, avec une réponse incidents bit-identique au snapshot 717. Elle a établi sa compatibilité avec V15 et l’achèvement du parcours J5 autorisé. Le résultat incidents demeure **`PARSED` et `PARTIAL · 91%`** ; l’échec historique V14 demeure inchangé.

## 1. Périmètre et nature des preuves

Le skill demandé a été lu intégralement avec un outil local. Ce réexamen repose exclusivement sur les sept fichiers autorisés, lus par sections pertinentes. **Aucun test, parseur, build, accès réseau, application, DB ou Docker n’a été exécuté ; aucune source n’a été modifiée.** Les opérations historiques décrites ci-dessous sont celles rapportées par WO-017.

Les statuts du laboratoire restent : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

### Repères des sources

Les identifiants suivants servent aux renvois dans le livrable :

| Repère | Source autorisée |
|---|---|
| **W** | [WO-017 — diagnostic, contrat, preuves et qualification](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-H01/docs/work_orders/completed/WO-SS-20260830-017-j5-incidents-empty-shootout-action-v15.md:51) |
| **R** | [Règles métier — séance de tirs au but, §10.2](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-H01/docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md:2357), ainsi que §§1 et 3 |
| **P** | [EventIncidentsV15Parser.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-H01/src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV15Parser.java:14) |
| **T** | [EventIncidentsV15ParserTest.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-H01/src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV15ParserTest.java:18) |
| **F** | [Fixture synthétique de séance terminale avec actions vides](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-H01/src/test/resources/fixtures/provider-j5/incidents-terminal-shootout-empty-actions.json:1) |

`AGENTS.md` et le skill constituent le cadre de lecture. Le worktree de cette revue est `FQ-H01`. La base `cfd536e` et la branche `codex/j8-incidents-v15` sont **les références historiques déclarées par W**, sans vérification de l’état Git actuel.

## 2. Pourquoi le snapshot 717 a échoué sous V14

La chaîne explicative est cohérente entre le rapport, les règles et les assertions du test :

1. **Forme historique :** le snapshot 189 omettait `footballPassingNetworkAction` sur les tentatives non minutées ; W §2 le rapporte comme compatible avec V14.
2. **Variante rencontrée :** le snapshot 717 présentait un tableau exactement vide sur quatorze tentatives cohérentes, sans `time`.
3. **Limite de V14 :** la règle historique des versions V12 à V14 exigeait une **propriété absente** pour bénéficier de l’exception de séance terminale sans minutes. Un tableau vide présent ne remplissait pas cette condition et n’offrait aucune minute imbriquée.
4. **Conséquence :** la famille était classée `SCHEMA_INCOMPATIBLE`. Le test synthétique attend notamment des problèmes sur la minute du marqueur `PEN` et sur `footballPassingNetworkAction[0].time` des deux tentatives.
5. **Diagnostic local rapporté :** une sonde en mémoire traitant exclusivement les tableaux vides comme absents obtenait `PARSED`. W §9 rapporte ensuite, sur le brut exact sous V15, **35 incidents, 0 problème, 18 warnings, `PARTIAL`, score 91**, sans reparse persistant.

**Il s’agit donc d’une limite de reconnaissance du parseur face à une variante observée.** Les sources ne soutiennent pas une causalité de l’instrumentation J8 et ne revendiquent pas un changement global du schéma fournisseur. Les chemins d’erreur détaillés du test concernent sa fixture ; les chemins exacts de l’échec réel ne sont pas fournis.  
*Provenance : W §§2 et 9 ; R §10.2, lignes 2415–2420 ; T lignes 58–66.*

## 3. Portée exacte de V15

### Une seule politique métier élargie

Le fichier **P** hérite de `EventIncidentsV14Parser`, change l’identifiant de version et surcharge uniquement le prédicat suivant :

```java
return action == null || (action.isArray() && action.isEmpty());
```

Dans le contrat documenté, `action == null` correspond à la propriété absente. **Il ne faut pas l’assimiler à la valeur JSON explicite `null`**, que le test classe parmi les formes rejetées.

Cette extension concerne exclusivement les tentatives d’une séance terminale entièrement non minutée. Les six conditions de **R §10.2** restent nécessaires :

1. Un unique marqueur `period` porte exactement `text="PEN"`, `period="penalties"`, `isLive=false`, `time=999`, `addedTime=999` et les deux scores finaux.
2. Un marqueur `FT` ou `ET` possède une minute effective valide.
3. Toutes les tentatives omettent `time` et présentent une action auxiliaire absente ou exactement `[]`.
4. Chaque tentative porte une classe `scored` ou `missed`, un côté, les deux scores et une séquence strictement positive.
5. Les séquences sont uniques et contiguës de `1` à `N`.
6. Le score de la tentative de séquence maximale égale celui du marqueur `PEN`.

| Forme ou situation | Portée de V15 |
|---|---|
| Propriété absente ; tableau exactement `[]` | Admissibles dans ce contexte fermé ; les deux formes peuvent coexister entre tentatives. |
| JSON `null`, objet, chaîne, nombre ou booléen | Ne bénéficient pas de l’exception ; rejet dans le cas candidat non minuté. |
| Tableau non vide | Jamais assimilé à une absence. Les règles historiques de minute imbriquée continuent de s’appliquer. |
| Tentatives minutées et non minutées mélangées | Séance incompatible. |
| Séquences lacunaires, doublons, scores terminaux divergents ou contexte terminal invalide | L’exception ne s’applique pas. |
| Minute absente dans la séance admissible | Reste absente ; aucune conversion de `sequence` en minute. |
| Sentinelle `999` | Conservée dans le brut ; ne représente pas du temps joué à afficher. |

**V15 conserve la perte d’information temporelle.** Le rendu prescrit est `—`, les chemins manquants restent mesurés et la normalisation ne devient pas artificiellement complète.

W §5 rapporte aussi le câblage dans la campagne J5 et les imports, la lecture des versions historiques et la migration append-only V28. **Ces éléments d’intégration ne sont pas directement vérifiables dans le corpus autorisé.** De même, le code des parents V12 à V14 n’a pas été lu : les gardes hérités sont ici étayés par les règles, le rapport et les assertions ciblées, sans inspection complète de leur implémentation.

Les ajouts V16 présents dans la fiche métier, notamment `inGamePenalty/awarded`, ne font pas partie de la portée de V15.

## 4. Lien entre la fixture et le cas réel

**La fixture reproduit le mécanisme de l’échec et de son acceptation bornée. Elle constitue une réduction synthétique du cas.**

| Élément | Cas réel, selon W | Fixture et test lus |
|---|---|---|
| Contenu | 35 incidents, dont 14 tirs au but | 4 incidents : `PEN`, 2 tirs, `FT` |
| Absence temporelle | 14 tentatives sans `time`, avec 14 tableaux `[]` ; minute normalisée de `PEN` également absente | 2 tentatives sans `time`, chacune avec `[]` ; `PEN.time=999` dans le brut |
| Séquences | `1` à `14`, conservées | `1` et `2`, présentes dans l’ordre inverse dans le tableau JSON |
| Côtés | Côtés conservés selon le rapport, sans détail individuel publié | Séquence 1 : `isHome=false`, tir raté ; séquence 2 : `isHome=true`, tir marqué |
| Scores | Conservés et cohérents selon W ; valeurs réelles non détaillées | `FT=1–1`, séquence 1 `1–1`, séquence 2 et `PEN=2–1` |
| Joueurs | Détail réel non fourni | Joueurs explicitement synthétiques, identifiants 901 et 902 |
| Résultat V15 | `PARSED`, `PARTIAL · 91%`, 18 warnings | Assertions : `PARSED`, `PARTIAL`, aucun problème, 4 incidents, 3 warnings **du code temporel filtré** |
| Provenance | Snapshot privé 717 ; occurrence de qualification 686 ; observation 324 | Octets de la fixture capturés par le test et paramètres fournis à l’appel |

Le test transmet `717L`, `16691018L` et `2026-08-30T04:31:58Z` à `parse`. **Ces valeurs sont des paramètres de test, pas une preuve d’accès au snapshot réel ni de son heure de réception.** L’assertion de hash compare le hash du résultat à celui des octets synthétiques capturés ; elle ne le compare pas au hash réel de 717.  
*Provenance : F ; T lignes 18–59 et 154–166.*

### Ce que les tests ciblent effectivement

Le fichier **T** contient des assertions pour :

- l’acceptation de `[]`, de l’absence et de leur coexistence ;
- le maintien du rejet V14 sur la fixture avec `[]` ;
- le rejet de quatre tableaux non vides incohérents et de cinq valeurs représentant des types interdits ;
- le rejet d’une séance devenue mixte après ajout de `time=98` à une tentative ;
- le rejet de séquences ne commençant plus à 1 et d’un score `PEN` divergent ;
- l’acceptation de `Extra time` live et le rejet d’un libellé de période inconnu.

Le fichier ne comporte pas d’assertion explicite couvrant chacun des autres critères cochés dans W, par exemple les doublons de séquence ou toutes les invalidités du marqueur terminal. Il ne fixe pas non plus le total des warnings ou le pourcentage de complétude de la fixture. **Ces assertions ont été lues, sans être exécutées pendant ce réexamen.**

## 5. Ce que la qualification a réellement établi

### Trois niveaux de preuve distincts

| Niveau | Résultat rapporté | Conclusion permise |
|---|---|---|
| **Analyse locale du brut historique**, W §9 | Transaction en lecture seule, contrôle du hash déclaré réussi, analyse en mémoire puis rollback ; aucune nouvelle observation ni résultat J8 | V15 pouvait interpréter le brut conservé de 717 avec les limites de complétude indiquées. |
| **Qualification technique**, W §10 | `PASS_928_TESTS_4_SKIPPED`, `PASS_67_TESTS` PostgreSQL et autres portes déclarées vertes, sans appel fournisseur | Validation technique historique rapportée. À ce stade, aucune compatibilité avec une nouvelle réponse fournisseur n’était encore établie. |
| **Qualification fournisseur distincte**, W §§11–13 | Une campagne autorisée, trois appels sans retry, trois HTTP 200 et trois familles parsées | Compatibilité et parcours fonctionnel établis dans la portée de cette campagne et de cet événement. |

La qualification fournisseur est datée de **`2026-08-30T08:09:44.275218Z`** dans W. Sa cible est l’événement fournisseur **`16691018`**, d’identité canonique **`f4713f80-4769-3656-ba51-61d8ac1aa814`**.

| Famille | Snapshot / occurrence | Parseur | Résultat rapporté | Observation |
|---|---|---|---|---:|
| Statistiques | `716 / 685`, dédupliqué | `event-statistics-v2` | `PARSED`, `COMPLETE · 100%` | 322 |
| Incidents | `717 / 686`, dédupliqué | `event-incidents-v15` | `PARSED`, `PARTIAL · 91%` | 324 |
| Compositions | `720 / 687`, inséré | `event-lineups-v2` | `PARSED`, `PARTIAL · 99%` | 325 |

**Pour les incidents, une nouvelle réception a réutilisé le snapshot 717 par déduplication et produit une nouvelle observation V15.** Le rapport affirme l’identité bit à bit avec le brut historique. Il ne décrit pas la réécriture du résultat V14.

La campagne d’audit distincte est `c9caf9b1-2495-4612-9e05-b9269aebaa66`. Le ledger J8 historique reste, lorsqu’on exclut cette campagne, à **4 campagnes, 19 tentatives et 20 résultats d’unité**. Sa fenêtre exclusive et son résultat partiel ne sont pas recalculés.  
*Provenance : W §§13 et 13.1.*

### Complétude : calcul et limite

Pour les incidents réels, W rapporte **164 signaux présents sur 179 attendus** :

- signaux manquants : `179 − 164 = 15` ;
- proportion arithmétique : `164 / 179 × 100 ≈ 91,62 %` ;
- score entier consigné : **91** ;
- warnings consignés : **18**.

Le mode de conversion en score entier n’est pas visible dans les sources autorisées. Les **18 warnings ne doivent pas être assimilés à 18 signaux manquants**. Les quinze minutes normalisées absentes — quatorze tirs plus `PEN` — correspondent numériquement aux quinze signaux manquants, mais leur correspondance exacte nécessite le détail de complétude absent du corpus.

La qualification établit donc une **compatibilité de schéma accompagnée d’une complétude partielle**, sans preuve d’exhaustivité du match réel.

## 6. Matrice d’anomalies et de limites

La gravité ci-dessous mesure l’impact sur l’interprétation ou la décision de cette revue. Les contre-cas synthétiques sont identifiés comme tels.

| Observation | Règle applicable | Provenance | Gravité | Décision |
|---|---|---|---|---|
| **Schéma réel :** 14 tableaux `[]` sur des tentatives sans minute entraînaient le rejet V14. | V12–V14 : propriété absente seulement ; V15 ajoute exactement `[]` sous garde terminale. | W §§2 et 9 ; R §10.2 ; reproduction synthétique T lignes 58–66. | **Majeure** — famille inutilisable sous V14. | Retenir le diagnostic de variante de représentation ; préserver le résultat historique V14. |
| **Types, contre-cas synthétiques :** JSON `null`, types erronés ou tableaux non vides incohérents. | Aucune assimilation à l’absence ; évaluation historique des tableaux non vides. | R §10.2 ; P lignes 23–25 ; T lignes 81–105. | **Majeure si acceptés.** | Conserver le rejet ; ne pas généraliser « vide » à `null` ou à une structure malformée. |
| **Temporalité, contre-cas synthétique :** une tentative minutée mélangée aux tentatives sans minute. | La séance doit être entièrement non minutée pour bénéficier de l’exception. | W §4 ; R §10.2 ; T lignes 109–121. | **Majeure.** | Classer incompatible ; ne pas reconstruire les minutes. |
| **Sémantique, contre-cas synthétiques :** séquences invalides ou score `PEN` divergent. | Séquences uniques de `1` à `N` ; score final égal à celui de la séquence maximale. | R §10.2 ; T lignes 125–132. | **Majeure.** | Maintenir les gardes ; ne pas réparer par l’ordre du tableau ou par un score supposé. |
| **Projection réelle rapportée :** `PEN` et 14 tirs restent sans minute ; présence d’une sentinelle terminale dans le brut. | Minute normalisée absente, rendu `—` ; ni `999` ni `sequence` ne deviennent du temps joué. | W §13 ; R §§3 et 10.2 ; analogue synthétique T lignes 38–55. | **Modérée pour la donnée ; majeure si une chronologie est fabriquée.** | Accepter la normalisation partielle et conserver l’absence temporelle. |
| **Complétude :** `PARSED`, 164/179 signaux, score 91 et 18 warnings. | Parsing réussi et complétude sont deux dimensions distinctes. | W §§9 et 13 ; calcul ci-dessus. | **Modérée** — limites d’information ; décompte détaillé inconnu. | Maintenir `PARTIAL` ; ne pas annoncer 100 % ni attribuer les 18 warnings sans leur détail. |
| **Provenance du test :** le test réutilise les identifiants du cas tout en traitant une fixture de 4 incidents. | Une identité passée en paramètre ne prouve pas l’origine des octets. | F ; T lignes 18–36 et 154–166. | **Majeure pour l’audit si les preuves sont confondues.** | Étiqueter la fixture comme synthétique ; ne transférer au réel ni ses scores, ni ses joueurs, ni son horodatage. |
| **Historique :** nouvelle occurrence 686 et observation V15 324 sur le snapshot 717 dédupliqué. | Distinguer réception, snapshot, interprétation et résultat historique. | W §§13 et 13.1. | **Majeure si requalification rétroactive.** | Conserver les deux résultats versionnés ; ne pas transformer la campagne J8 initiale en succès. |
| **Portée fournisseur :** une réponse incidents réobservée sur un seul événement. | Une preuve bornée ne démontre ni couverture générale ni stabilité future. | W §§2, 13 et 14. | **Majeure si généralisation.** | Retenir `PASS_BOUNDED_EVENT_16691018`, sans conclusion globale ni renouvellement du go consommé. |
| **Vérifiabilité :** hashes exacts, payload privé, rapports détaillés et implémentations héritées absents du corpus. | Séparer résultat documenté et vérification indépendante. | W §9 et §13.2 ; limites des fichiers autorisés. | **Limite majeure de preuve indépendante**, sans preuve de corruption. | Présenter les résultats historiques comme rapportés ; laisser les éléments absents explicitement inconnus. |

## 7. Inconnues restantes et décision de réexamen

Les sources autorisées ne permettent pas d’établir indépendamment :

- les octets du snapshot réel 717, son **hash brut exact**, le hash normalisé de l’observation 324 ou leurs égalités effectives ; `RAW_SHA256_MATCH=true` reste un résultat rapporté ;
- l’heure exacte de réception du snapshot historique et de l’occurrence 686 : l’heure de qualification n’est pas nécessairement l’heure de réception, et `RECEIVED_AT` dans le test n’est pas une preuve réelle ;
- les équipes, les joueurs, les scores individuels et la chronologie détaillée des quatorze tirs réels ;
- la liste des 18 warnings, celle des 15 signaux manquants et le calcul implémenté du score entier 91 ;
- l’implémentation complète des gardes hérités, le parseur effectivement sélectionné aujourd’hui, le câblage des imports, la migration V28 et les rapports d’exécution des tests ;
- une couverture sur d’autres événements, la fréquence de cette variante ou sa stabilité après le 30 août 2026.

**Décision : le diagnostic et le contrat borné de WO-017 sont cohérents avec le code V15 et les assertions ciblées disponibles. Sa qualification fournisseur est documentée comme concluante pour l’événement 16691018, avec une normalisation incidents partielle.** WO-017 porte une décision propriétaire de clôture du 30 août 2026 et les statuts `VALIDATED` / `COMPLETED`.

Cette conclusion conserve expressément **l’échec V14 historique, les lacunes temporelles et la séparation du jalon J8**. W §14 maintient J8 en attente de qualification humaine, son rapport partiel inchangé et l’absence de décision J9. Aucun résultat de cette revue ne constitue une nouvelle exécution, une qualification actuelle du fournisseur ou une autorisation supplémentaire.