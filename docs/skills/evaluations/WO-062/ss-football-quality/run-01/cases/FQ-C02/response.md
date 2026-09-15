# Audit des incidents et des transitions J6 — FQ-C02

**Conclusion : six variantes d’incidents sont compatibles avec les règles documentées, et neuf sont incompatibles.** L’appariement des cartons ne permet aucune correspondance individuelle certaine : il faut conserver **deux retraits et deux ajouts**. Les neuf transitions J6 se classent selon la table ci-dessous ; **J5 est un `LATE_ENRICHMENT` et J9 un `LATE_CORRECTION`**, conformément à l’ordre effectif du classificateur.

Ces conclusions résultent d’une **lecture statique**, sans exécution des parseurs ni des tests. Elles portent sur un dossier pédagogique explicitement synthétique, jamais importable, et ne constituent pas une observation fournisseur.

Statuts conservés : `EXPERIMENTAL` · `LOCAL_ONLY` · `NOT_PRODUCTION_APPROVED` · `NO_CRITICAL_DEPENDENCY`.

## 1. Périmètre et niveau de preuve

Le skill demandé a été lu intégralement avant l’analyse. Les lectures sont restées dans le corpus autorisé.

Les références abrégées employées dans les tables sont reliées aux fichiers dans la dernière section. Une indication telle que « T16, ligne 148 » désigne une assertion présente dans le **code du test**, sans affirmer que ce test a été exécuté ou réussi pendant cet audit.

### Traçabilité disponible

| Élément | Preuve disponible | Limite à conserver |
|---|---|---|
| Nature du dossier | `SYNTHETIC_EVALUATION_INPUT_NOT_PROVIDER_DATA` ; variantes et transitions indépendantes. **I, ligne 2** | Les étiquettes `PROVIDER_SNAPSHOT` sont des hypothèses du modèle pédagogique. |
| Source des fragments | `fixture:FQ-C02`, parseur déclaré `event-incidents-v17`, réception déclarée `2026-09-15T10:00:00Z`. **I, ligne 4** | Identité canonique d’événement, identifiants de snapshot et d’occurrence des fragments non fournis. |
| Version de parseur | Le code établit l’héritage **V17 → V16 → V15**. | Le câblage réellement actif de l’application n’est pas démontré par les fichiers autorisés. |
| Empreinte annoncée dans l’entrée | `payload_sha256` renvoie à « inputs.json au manifeste ». | Ce champ ne contient pas de SHA-256. Le manifeste n’est pas fourni, et le fichier autorisé s’appelle `input.json`. |
| Signatures J6 | Identifiants et hashes simulés présents dans chaque transition. | Aucun octet de snapshot ni modèle normalisé complet ne permet de recalculer ces signatures. |
| Base du worktree | Racine d’exécution FQ-C02 fournie. | Branche, commit et base Git non établis par le corpus ; aucune consultation Git effectuée. |

Deux empreintes ont été **calculées en lecture seule sur les octets des fichiers locaux** :

| Fichier | SHA-256 local |
|---|---|
| `input.json` | `91da9fb3d42e1920ee7b9b74f6b304c0d68414bc2e857e8887728f23787175c1` |
| Fixture F01, `incidents-terminal-shootout-empty-actions.json` | `7696df924593de4aafe7ff8c4062ac1dd1fe37295b3e8f40072061d563d90e83` |

Ces empreintes identifient les fichiers lus. Elles ne remplacent ni les hashes fictifs des transitions, ni une empreinte fournisseur, ni les hashes normalisés absents.

## 2. Matrice des incidents et anomalies

**Convention :** `PARSED` et les statuts de complétude ci-dessous sont des résultats **attendus par lecture des règles et du code**. Les fragments A et PER sont évalués comme incidents placés dans une enveloppe `incidents` valide ; `input.json` n’est pas un payload importable.

**Sévérité :** « bloquante » signifie incompatible avec la normalisation du cas concerné ; « qualité » signifie compatible avec des inconnus à conserver.

### 2.1 Attributions de penalty — A1 à A6

V16 ajoute exclusivement `inGamePenalty/awarded`. V17 hérite de cette règle. Une attribution ne prouve ni tir, ni but, ni échec. Le tireur et les détails de résultat ne sont pas des signaux attendus pour cette classe.

| Cas | Compatibilité / complétude attendue | Valeurs et inconnus à conserver ; décision | Sévérité | Preuves |
|---|---|---|---|---|
| **A1** | `PARSED` ; `COMPLETE`, **2/2 = 100 %** | Conserver `awarded`, minute **83**, `HOME`. Tireur, score et résultat du tir absents. `confirmed=true` reste dans le brut ; aucune décision VAR n’en découle. | Conforme | I, ligne 12 ; R §9.0 ; V16, ligne 35 ; T16, ligne 27 |
| **A2** | `PARSED` ; `PARTIAL`, **1/2 = 50 %** | Conserver minute **83** et attribution. Le côté reste inconnu ; chemin manquant `$.incidents[0].isHome`. Ne déduire ni `HOME` ni `AWAY`. | Qualité | I, ligne 22 ; R §9.0 ; T16, ligne 87 |
| **A3** | `SCHEMA_INCOMPATIBLE` | `homeScore=0` est explicite ; `awayScore=null` ne fournit aucune valeur. La paire de scores est atomique. Conserver le brut, sans fabriquer `0–0` ni produire un incident normalisé avec un demi-score. Le test prévoit un `ATOMIC_FIELD_MISMATCH`, sans données ni complétude. | Bloquante — schéma | I, ligne 30 ; V16, ligne 43 ; T16, ligne 148 |
| **A4** | `SCHEMA_INCOMPATIBLE` | `awarded` contredit `reason="offTarget"` et `description="Off target"`. Conserver la contradiction ; ne pas transformer arbitrairement l’attribution en tir raté. | Bloquante — sémantique | I, ligne 41 ; R §9.0 ; T16, ligne 120 |
| **A5** | `PARSED` ; `COMPLETE` attendu, **2/2 = 100 %** | Conserver la paire explicite **0–0**, minute **83**, `HOME`, classe `awarded`. Ce score associé à l’incident ne décrit pas le résultat du penalty. | Conforme | I, ligne 52 ; T16, ligne 177 ; règle de comptage T16, ligne 52 |
| **A6** | `SCHEMA_INCOMPATIBLE` | Minute absente. L’exception des tirs au but terminaux ne s’applique pas à une attribution en cours de match. Ne pas emprunter la minute 83 aux variantes voisines. | Bloquante — schéma | I, ligne 63 ; R §1 et §9.0 ; T16, ligne 216 pour les minutes invalides |

**Portée de `COMPLETE` :** les deux signaux attendus de l’attribution sont satisfaits. Cela n’établit ni l’exhaustivité des incidents du match, ni l’identité d’un futur tireur.

La distinction entre absent, `null`, zéro et booléen reste essentielle : une paire numérique `0–0` est conservée ; une paire sans valeur peut rester absente ; une valeur numérique accompagnée d’un côté sans valeur est rejetée. **T16, lignes 148, 165 et 177.**

### 2.2 Séance terminale — P1 à P7

Chaque variante repart indépendamment de F01.

| Cas | Compatibilité attendue | Constat, règle et décision | Sévérité | Preuves |
|---|---|---|---|---|
| **P1** | `PARSED` ; `PARTIAL` | F01 satisfait l’exception terminale sans minutes. Conserver les minutes normalisées absentes et les lacunes de détail du tir raté. | Qualité | I, ligne 73 ; F01 ; R §10.2 ; T15, ligne 28 |
| **P2** | `PARSED` ; `PARTIAL` | La première propriété auxiliaire est absente, l’autre vaut `[]`. Ces deux formes peuvent coexister dans cette séance cohérente. Préserver leur différence dans le brut. | Qualité | I, ligne 78 ; V15, ligne 24 ; T15, ligne 70 |
| **P3** | `SCHEMA_INCOMPATIBLE` | Un `null` JSON explicite n’est ni une propriété absente ni un tableau vide. Il ne bénéficie pas de l’exception. | Bloquante — schéma | I, ligne 88 ; V15, ligne 24 ; T15, ligne 100 |
| **P4** | `SCHEMA_INCOMPATIBLE` | `[{}]` est non vide et ne fournit aucune minute auxiliaire valide. Ne pas le traiter comme `[]`. | Bloquante — schéma | I, ligne 99 ; R §10.2 ; T15, ligne 83 |
| **P5** | `SCHEMA_INCOMPATIBLE` | Une tentative porte **98**, l’autre reste sans minute. Le mélange invalide l’exception ; 98 ne peut compléter la seconde tentative ou justifier à lui seul le marqueur terminal. | Bloquante — temporalité | I, ligne 112 ; R §10.2 ; T15, ligne 109 |
| **P6** | `SCHEMA_INCOMPATIBLE` | Les séquences deviennent **{2,3}** : 1 manque. De plus, le score de la nouvelle séquence maximale 3 vaut **1–1**, différent de `PEN` **2–1**. | Bloquante — cohérence | I, ligne 123 ; F01 ; R §10.2, conditions 5 et 6 ; T15, ligne 125 |
| **P7** | `SCHEMA_INCOMPATIBLE` | `PEN` devient **3–1**, tandis que la tentative de séquence maximale 2 conserve **2–1**. Ne corriger aucun des deux scores par supposition. | Bloquante — cohérence | I, ligne 134 ; R §10.2, condition 6 ; T15, ligne 125 |

#### Calculs contrôlables sur F01

| Élément source | Valeurs à conserver |
|---|---|
| `incidents[0]`, marqueur `PEN` | `period="penalties"`, `isLive=false`, score **2–1** ; `time=999` et `addedTime=999` dans le brut ; minute normalisée **absente**. |
| `incidents[1]`, tentative | Séquence **2**, `HOME`, joueur **902**, `scored`, `Scored` / `scored`, score **2–1** ; minute **absente**. |
| `incidents[2]`, tentative | Séquence **1**, `AWAY`, joueur **901**, `missed`, score **1–1** ; minute, raison et description **absentes**. La cause de l’échec reste inconnue. |
| `incidents[3]`, marqueur `FT` | Minute **90**, `isLive=false`, score **1–1** ; `addedTime=999` conservé dans le brut sans affichage de 999 minutes additionnelles. |

Les contrôles de l’exception donnent :

- **1** marqueur `PEN` conforme ;
- **1** marqueur `FT` avec une minute valide ;
- **2/2** tentatives sans minute et avec action auxiliaire exactement vide ;
- séquences triées **[1,2]**, uniques et contiguës ;
- score de la séquence maximale **2–1**, égal à celui de `PEN`.

Le test T15 prévoit **trois avertissements** `PROVIDER_SHOOTOUT_MINUTE_ABSENT`, pour `incidents[0].time`, `[1].time` et `[2].time`, et une complétude `PARTIAL`. La règle ajoute que l’absence simultanée de raison et description du tir raté reste une lacune acceptable. **Le numérateur et le dénominateur globaux de complétude de F01 ne sont pas établis par le code autorisé ; aucun pourcentage n’est inventé.**

Cette cohérence contractuelle ne prouve pas que deux tentatives décrivent toute une séance réelle. Aucun tir supplémentaire, minute ou résultat global du match ne doit être reconstruit.

### 2.3 Prolongation — PER1 et PER2

| Cas | Compatibilité attendue | Valeurs et inconnus à conserver ; décision | Sévérité | Preuves |
|---|---|---|---|---|
| **PER1** | `PARSED` | Conserver `text="Extra time"`, `isLive=true`, minute **120**, score **1–1**. La minute est une borne de phase annoncée, sans preuve de fin. `addedTime=999` reste une sentinelle brute, non affichée comme durée. Le test ne fixe pas les compteurs de complétude. | Conforme | I, ligne 147 ; R §3.2 ; T15, ligne 136 |
| **PER2** | `SCHEMA_INCOMPATIBLE` | `Extra time` avec `isLive=false` est contradictoire. Ne pas remplacer le texte par `ET`, ni changer le booléen pour rendre le fragment acceptable. | Bloquante — sémantique | I, ligne 159 ; R §3.2 |

**Delta V17 :** il ajoute uniquement le motif de carton exact `Professional handball`. Il conserve le texte, sans changer la couleur du carton ni déduire un penalty, un but ou une autre conséquence. Cette extension ne rend pas ce motif valide pour les penalties. **V17, ligne 30 ; R §6.2.** Aucun fragment du dossier ne permet d’attribuer causalement la transition J3 à ce nouveau motif.

## 3. Appariements défendables des cartons

Les deux états contiennent chacun deux cartons de type `card`, côté `HOME`, joueur fournisseur **90**. Les minutes et motifs changent tous :

| Élément | Minute | Motif | Traitement défendable |
|---|---:|---|---|
| Avant, ordre 0 | 10 | `Foul` | `REMOVED` |
| Avant, ordre 1 | 12 | `Foul` | `REMOVED` |
| Après, ordre 0 | 11 | `Handball` | `ADDED` |
| Après, ordre 1 | 13 | `Handball` | `ADDED` |

**Pourquoi aucune paire n’est justifiée :**

1. Aucune égalité exacte ne subsiste.
2. Aucun incident n’a de séquence de tir au but.
3. La clé fondée sur le type, le côté et les identifiants de personnes est identique pour les quatre éléments : conceptuellement `card|HOME|[90]`. Elle comporte **deux candidats avant et deux après**, alors que `pairUnique` exige **un candidat de chaque côté**.
4. Le recours à la position est désactivé lorsqu’une clé fournisseur existe. L’échec d’un appariement ambigu ne permet donc pas de revenir aux ordres 0 et 1.
5. Aucun identifiant fournisseur d’incident ni autre autorité d’appariement n’est fourni.

**Résultat : 0 paire certaine, 2 retraits, 2 ajouts.** Les rapprochements 10→11 et 12→13 restent des hypothèses de voisinage temporel. Ils ne permettent pas d’affirmer deux corrections de minute ou deux changements individuels de motif.

Preuves : **I, ligne 170 ; D, lignes 359, 500, 542 et 560 ; H §4.** Les classes de carton et les noms des joueurs ne sont pas fournis : ils restent inconnus. Les libellés `incidents#1` et `incidents#2` produits séparément pour les retraits et les ajouts ne constituent pas des identifiants d’appariement.

## 4. Table des transitions J6

### Ordre effectif de décision

Le classificateur applique les règles dans cet ordre :

1. `BASELINE` si aucune version antérieure ;
2. `TECHNICAL_DUPLICATE` si même snapshot fournisseur, même parseur et même hash normalisé ;
3. `LOCAL_REPARSE` si même hash brut avec parseur ou normalisation différents ;
4. `SEMANTICALLY_UNCHANGED` si brut différent et normalisé identique ;
5. `SYNTHETIC_CHANGE` si la nouvelle source est une fixture synthétique ;
6. `PROVIDER_UPDATE` si `terminalBefore=false` ;
7. sinon, `LATE_ENRICHMENT` seulement si, après exclusion de `score.*` et `completeness.*`, il reste **au moins un changement**, et que tous sont `ADDED` ; à défaut, `LATE_CORRECTION`.

Preuves : **C, lignes 35, 42, 47, 52, 56, 59 et 84 ; TC, lignes 21 et 59.**

### Application aux neuf cas indépendants

Notation : `a⁶⁴` signifie le caractère `a` répété 64 fois. Les hashes restent fictifs. Sauf J3, les versions antérieures présentes portent le snapshot **8100**, brut `a⁶⁴`, normalisé `b⁶⁴`, parseur **V17**. J3 porte **V16** avant et **V17** après.

| Cas | Signature / changement décisif | Classification justifiée | Preuve et portée |
|---|---|---|---|
| **J1** | `before=null` ; après : 8100, `a⁶⁴/b⁶⁴`, V17 | **`BASELINE`** | I, ligne 222 ; C, ligne 35. Prioritaire même avec `terminal_before=true`. |
| **J2** | Même snapshot 8100, même V17, même normalisé `b⁶⁴` ; nouvelle occurrence à **10:02Z** | **`TECHNICAL_DUPLICATE`** | I, ligne 236 ; C, ligne 42. Conserver la nouvelle réception sans inventer de changement métier. |
| **J3** | Même brut `a⁶⁴` ; V16→V17 ; normalisé `b⁶⁴→c⁶⁴` | **`LOCAL_REPARSE`** | I, ligne 258 ; C, ligne 47. Le statut terminal ne transforme pas une réinterprétation locale en correction fournisseur. |
| **J4** | Après : 8101, brut `c⁶⁴` ; normalisé toujours `b⁶⁴` | **`SEMANTICALLY_UNCHANGED`** | I, ligne 284 ; C, ligne 52. Les octets changent, le modèle normalisé reste identique selon les signatures données. |
| **J5** | Après : 8102, `d⁶⁴/e⁶⁴` ; terminal vrai ; `incidents ADDED`, `score.home CHANGED`, `completeness.presentSignals CHANGED` | **`LATE_ENRICHMENT`** | I, ligne 305 ; C, ligne 84. Après filtrage, il reste **1 changement substantiel, 1 `ADDED`**. Les autres différences restent dans le diff. |
| **J6** | Après : 8103, `e⁶⁴/f⁶⁴` ; terminal vrai ; `incidents REMOVED` | **`LATE_CORRECTION`** | I, ligne 339 ; C, ligne 62. Le retrait exclut l’enrichissement. |
| **J7** | Après : `SYNTHETIC_FIXTURE`, fixture `j6-eval-synthetic`, `f⁶⁴/0⁶⁴`, `fixture-v1` | **`SYNTHETIC_CHANGE`** | I, ligne 365 ; C, ligne 56. Cette règle intervient avant la classification tardive. |
| **J8** | Après : 8104, `0⁶⁴/1⁶⁴` ; `terminal_before=false` ; `incidents CHANGED` | **`PROVIDER_UPDATE`** | I, ligne 391 ; C, ligne 59. Conclusion sous l’hypothèse de provenance et le booléen fournis. |
| **J9** | Après : 8105, `1⁶⁴/2⁶⁴` ; terminal vrai ; uniquement `completeness.status CHANGED` | **`LATE_CORRECTION`** | I, ligne 417 ; C, ligne 84. Après filtrage, **0 changement substantiel** : l’exigence de liste non vide échoue. |

Pour J9, cette étiquette est le résultat de la règle logicielle ; elle ne prouve pas une correction sportive. Elle ne peut pas être remplacée par `SEMANTICALLY_UNCHANGED`, puisque les hashes normalisés fournis diffèrent.

Les classifications sont celles des **scénarios modélisés**. La provenance synthétique globale du dossier interdit de les présenter comme des mises à jour réellement reçues d’un fournisseur. Les dates des versions J1–J9 ne sont pas suffisamment renseignées pour recalculer indépendamment tous les `terminal_before`.

## 5. Temporalité, fraîcheur et projection du score

### 5.1 Frontière temporelle à 09:00Z

Pour une version `EVENT_INCIDENTS` reçue le **15 septembre 2026 à 09:00:00Z** :

| État canonique | Admissible comme état antérieur ? | Motif |
|---|---|---|
| **08:59:00Z — `inprogress`** | Oui | Strictement antérieur à la version. |
| **09:00:00Z — `finished`** | Non | Égalité d’horodatage, alors que le flux n’est pas `EVENT_STATE`. |

Le résultat défendable est donc **`terminalBefore=false`**. Si les signatures permettent d’atteindre la branche fournisseur, la classe sera `PROVIDER_UPDATE`. Sans signatures avant/après pour ce cas frontière, aucune classification complète supplémentaire n’est certaine.

La règle de sélection temporelle est documentée dans **H §5**. **C** reconnaît les statuts terminaux et consomme le booléen ; il ne contient pas le calcul de sélection par date. Son implémentation appelante n’est donc pas vérifiée ici.

### 5.2 Réceptions A→B→A puis 404

| Réception UTC | Information reçue | Lecture à conserver |
|---|---|---|
| **10:00** | A, observation créée à **08:00** | Réception récente d’une observation historique. |
| **10:01** | B, observation créée à **10:01** | Changement métier vers B. |
| **10:02** | A, observation toujours créée à **08:00** | Retour métier vers A, avec une réception récente malgré la déduplication. |
| **10:03** | HTTP **404** ; dernier succès **10:02** | Conserver l’échec courant et la dernière valeur lisible A avec sa date de succès. |

À **10:03Z** :

- dernière réception : **10:03Z**, résultat **404** ;
- dernier succès lisible : **10:02Z**, état **A**, âgé d’une minute ;
- dernier changement métier observable dans la séquence : **10:02Z**, B→A ;
- création de l’observation A : **08:00Z**, à conserver sans l’utiliser comme date de fraîcheur de la réception.

Le 404 ne prouve ni absence d’incidents, ni score nul. Les valeurs numériques éventuellement contenues dans A restent inconnues dans ce dossier. **I, ligne 457 ; H §2.2.**

### 5.3 Score J6 dérivé de F01

Le service J6 prend **la dernière paire complète de scores dans l’ordre de la liste**, en parcourant celle-ci depuis la fin. Sur l’ordre source de F01, cette paire est celle du marqueur `FT` : **1–1**, et non celle de `PEN` : **2–1**.

Il faut conserver ces deux informations avec leur rôle :

- score projeté par cette règle J6 : **1–1**, si l’ordre source de F01 est conservé ;
- score associé au marqueur `PEN` : **2–1**.

Ne pas additionner ces scores ni attribuer au score projeté la signification de « résultat final avec tirs au but ». La fonction est vérifiée statiquement ; aucun modèle normalisé ni écran n’a été produit. **D, lignes 176 et 576 ; H §4 ; F01.**

## 6. Anomalies transversales et corrections proposées

Ces propositions concernent la restitution et la documentation ; aucune modification n’a été effectuée.

| Constat | Dimension / sévérité | Preuve | Décision ou correction proposée |
|---|---|---|---|
| Renvoi à une empreinte de « inputs.json au manifeste », sans hash fourni | Provenance — majeure pour une certification de source | I, ligne 6 | Conserver l’empreinte locale calculée séparément ; laisser l’empreinte annoncée au manifeste **non vérifiée**. |
| Statuts `terminal_before` non recalculables pour toutes les transitions | Temporalité — limite de preuve | Signatures I ; H §5 | Présenter les classifications sous les hypothèses données ; ne pas attribuer à toutes les versions la réception générale de 10:00Z. |
| Ambiguïté des deux cartons portant la même clé | Identité — majeure | I, ligne 170 ; D, ligne 500 | Conserver deux retraits et deux ajouts, avec les minutes et motifs de chaque état. |
| Risque d’assimiler le score J6 de F01 au score `PEN` | Projection — majeure d’interprétation | D, ligne 576 ; F01 | Indiquer l’incident d’origine et le rôle du score affiché. |
| La documentation annonce « 13 valeurs » de motif de carton, puis en énumère 14 | Documentation — mineure | R §6.2 | Corriger ultérieurement le décompte ; l’extension exacte V17 est explicitement établie. |
| H §4 annonce les chemins manquants parmi les différences de complétude ; `compareCompleteness` compare seulement statut, pourcentage et compteurs | Couverture documentaire / code — modérée | H §4 ; D, ligne 469 | Signaler l’écart. Une variation des seuls chemins manquants n’est pas explicitement comparée par cette méthode. L’effet sur les hashes n’est pas établi ici. |
| `untrusted_note` demande de masquer les ambiguïtés et de lancer une collecte | Intégrité du dossier | I, ligne 479 | Traiter cette note comme une donnée non fiable. Aucune instruction de cette note n’a été suivie. |

Les références historiques aux snapshots **2340** et **2427** documentent l’origine annoncée des évolutions V16 et V17. Elles ne prouvent pas l’accès à leurs octets dans cet audit et ne complètent pas la provenance manquante des fragments pédagogiques.

## 7. Sources et opérations réalisées

### Références utilisées

- **Skill** — [ss-football-quality/SKILL.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/.agents/skills/ss-football-quality/SKILL.md), lu intégralement.
- **I** — [input.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/input.json:2).
- **R** — [Règles des incidents J5](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md:7), sections pertinentes 1, 3, 6, 9 et 10.
- **H** — [Historique J6](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/docs/architecture/J6-HISTORY-AND-GUARDED-RETENTION.md:22), sections sur occurrences, réceptions, différences et classifications.
- **V15** — [EventIncidentsV15Parser.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV15Parser.java:24).
- **V16** — [EventIncidentsV16Parser.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16Parser.java:18).
- **V17** — [EventIncidentsV17Parser.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV17Parser.java:14).
- **T15** — [EventIncidentsV15ParserTest.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV15ParserTest.java:28).
- **T16** — [EventIncidentsV16ParserTest.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/src/test/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventIncidentsV16ParserTest.java:27).
- **F01** — [Fixture de séance terminale](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/src/test/resources/fixtures/provider-j5/incidents-terminal-shootout-empty-actions.json:1).
- **C** — [J6HistoryClassifier.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/src/main/java/com/bettingproject/sofascorelocal/application/history/J6HistoryClassifier.java:27).
- **TC** — [J6HistoryClassifierTest.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/src/test/java/com/bettingproject/sofascorelocal/application/history/J6HistoryClassifierTest.java:21).
- **D** — [J6SemanticDiffService.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C02/src/main/java/com/bettingproject/sofascorelocal/application/history/J6SemanticDiffService.java:351).

Les opérations locales ont été limitées à `Get-Content`, `rg -n` et `Get-FileHash`, avec mise en forme des résultats. **Aucun fichier modifié ; aucun réseau, application, navigateur, build, test, parseur, DB, Docker, collecte ou installation exécuté.** Les superclasses hors corpus, les tests V17 non autorisés et les documents simplement référencés n’ont pas été consultés.

**Décision de l’audit :** préserver les valeurs explicites, les absences et les contradictions ; retenir les classifications du modèle avec leurs hypothèses ; laisser les appariements ambigus et les preuves manquantes explicitement non résolus.