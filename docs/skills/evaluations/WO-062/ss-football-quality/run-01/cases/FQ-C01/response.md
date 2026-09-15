# Audit pédagogique — identité, horaires et familles J4/J5

**Décision : conserver les valeurs explicites et les observations séparées, en laissant visibles les conflits et les absences.** Le dossier permet une analyse pédagogique ; il ne permet ni de valider des données fournisseur réelles, ni de constituer des observations importables.

Les conclusions principales sont les suivantes :

- **E1 et E2 représentent une seule identité canonique** : `SOFASCORE / 90006201`. E2 est la dernière observation canonique fournie.
- **L1 présente une inversion HOME/AWAY non résolue** par rapport à E1/E2.
- La journée du **25 octobre 2026 dans `Europe/Paris` dure 25 heures**, avec la fenêtre UTC **`[2026-10-24T22:00:00Z, 2026-10-25T23:00:00Z[`**.
- D1 et D2 conservent chacun un score unilatéral égal à zéro ; **aucune paire de scores complète ne peut être affichée**.
- Les qualifications attendues des variantes statistiques sont **ST1 `PARTIAL`, ST2 `EMPTY_VALID`, ST3 `SCHEMA_INCOMPATIBLE`, ST4 `UNAVAILABLE` déclaré**.
- L2 permet de distinguer un titulaire, un remplaçant et un indisponible. **Sa complétude globale et son rattachement à une équipe restent indéterminés.**

Statuts maintenus : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## 1. Cadre de preuve et référentiel

Le skill demandé a été lu intégralement avec un outil local avant l’analyse. Les lectures ont porté exclusivement sur les fichiers autorisés. Les conversions horaires ont été calculées en mémoire avec la zone `Europe/Paris`. **Aucun parseur du projet, test, build, réseau, application, DB ou Docker n’a été exécuté ; aucun fichier n’a été modifié.**

La racine fournie est le dossier d’évaluation `ss-football-quality/run-01/FQ-C01`. Sa branche, son commit de base et son rattachement effectif à un Work Order ne sont pas établis par le corpus ; aucun état Git n’a été consulté.

### Références utilisées

Les repères suivants sont employés dans les tableaux :

| Repère | Source autorisée et portée |
|---|---|
| **S** | [Skill ss-football-quality](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C01/.agents/skills/ss-football-quality/SKILL.md) — chaîne de preuve, identité, temps, valeurs et restitution |
| **I** | [input.json](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C01/input.json:1) — données pédagogiques et indépendance des fragments |
| **J4** | [Événements canoniques et détail local](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C01/docs/architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md:57) — §§ 3 à 6 : identité, provenance, recherche par date, résultats |
| **V4** | [Personnes et compositions V4](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C01/docs/architecture/J4-J5-PEOPLE-V4.md:6) — maintien des contrats sportifs V3, pays et présentation |
| **L3** | [Compositions V3](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C01/docs/architecture/J5-LINEUPS-V3-PLAYER-DETAILS.md:7) — capitaine, statistiques individuelles, indisponibles et conservation |
| **J5** | [Données événement gardées](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C01/docs/architecture/J5-GUARDED-REAL-EVENT-DATA.md:284) — § 5 : pipeline et états ; § 3.1 : 404 déclaré par l’opérateur |
| **P** | [EventStatisticsV2Parser.java](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C01/src/main/java/com/bettingproject/sofascorelocal/adapter/sofascore/eventdata/EventStatisticsV2Parser.java:29) — lecture statique du parseur, compteurs et branches de résultat |
| **A** | [AGENTS.md](C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo062-skills-lot2/.tmp/wo062-evaluations/ss-football-quality/run-01/FQ-C01/AGENTS.md:1) — statuts du laboratoire et invariants de provenance |

**Distinction des versions.** J4 conserve un titre « contrat courant `event-details-v3` » et J5 cite encore `event-lineups-v3` à la ligne 303. Le complément V4 précise que `event-details-v4` et `event-lineups-v4` maintiennent leurs contrats sportifs V3 et ajoutent les personnes/pays. Les étiquettes V4 du dossier sont cohérentes avec ce complément. Le code effectivement branché à l’exécution n’est toutefois pas vérifiable dans le corpus autorisé ; seul le fichier du parseur statistiques V2 a été inspecté.

## 2. Matrice de provenance

`input.json` déclare explicitement `SYNTHETIC_EVALUATION_INPUT_NOT_PROVIDER_DATA`. Les valeurs `PROVIDER_SNAPSHOT`, les identifiants et les hashes ci-dessous sont donc **des signatures simulées**. Cette déclaration reste déterminante malgré le nom du champ `source_kind`.

Notation : `a×64` signifie exactement 64 caractères `a`, et de même pour les autres hashes. « Absent » signifie non fourni.

| Ligne / famille | Identité ou liaison explicite | Snapshot déclaré | SHA-256 brut déclaré | Parseur / normaliseur déclaré | Réception UTC déclarée | Hash normalisé |
|---|---|---:|---|---|---|---|
| E1 — événement | `SOFASCORE / 90006201` | 8001 | `a×64` | `event-details-v4` | 2026-10-24 20:00:00Z | Absent |
| E2 — événement | `SOFASCORE / 90006201` | 8002 | `b×64` | `event-details-v4` | 2026-10-24 20:10:00Z | Absent |
| L1 — liaison des compositions | `provider_event_id=90006201` ; fournisseur non répété | 8003 | `c×64` | `event-lineups-v4` | 2026-10-24 20:15:00Z | Absent |
| D1 — résultat J4 | Aucun identifiant d’événement fourni | 8010 | `d×64` | `event-details-v4` | 2026-10-25 03:00:00Z | Absent |
| D2 — résultat J4 | Aucun identifiant d’événement fourni | 8011 | `e×64` | `event-details-v4` | 2026-10-25 03:01:00Z | Absent |
| ST1 — statistiques | Aucun identifiant d’événement fourni | 8020 | `f×64` | `event-statistics-v2` | 2026-10-25 03:02:00Z | Absent |
| ST2 — statistiques | Aucun identifiant d’événement fourni | 8021 | `0×64` | `event-statistics-v2` | 2026-10-25 03:03:00Z | Absent |
| ST3 — statistiques | Aucun identifiant d’événement fourni | 8022 | `1×64` | `event-statistics-v2` | 2026-10-25 03:04:00Z | Absent |
| ST4 — indisponibilité statistiques | Aucun identifiant d’événement fourni | 8023 | `2×64` | `event-statistics-unavailable-v1` | 2026-10-25 03:05:00Z | Absent |
| L2 — extrait de composition | Côté `HOME` ; événement et équipe absents | 8030 | `3×64` | `event-lineups-v4` | 2026-10-25 03:06:00Z | Absent |
| E3 — déplacement de date | `same_provider_event_id=true` ; identifiant non fourni | Absent | Absent | Absent | Absente | Absent |

**Mesures de traçabilité du dossier :**

- **10/11 lignes** possèdent les quatre métadonnées déclarées snapshot, hash brut, parseur et réception.
- **0/11** possède un hash normalisé ou un identifiant d’observation/d’occurrence persistée. Les repères `E1`, `ST1`, etc. sont des repères pédagogiques.
- **3/11** portent un identifiant d’événement numérique ; seules E1 et E2 donnent aussi explicitement le fournisseur.
- **Aucun snapshot fournisseur réel n’est vérifié.** Les fragments JSON lus ne prouvent pas les octets exacts des snapshots simulés ; les hashes déclarés n’ont pas été authentifiés.

**Décision de liaison :** D1, D2, ST1–ST4 et L2 restent indépendants. La proximité de leurs réceptions ne les rattache ni à `90006201`, ni les uns aux autres. E3 n’est pas rattachable à cette identité non plus. [I, S, J4 § 4]

## 3. Identité et rôles HOME/AWAY

### Valeurs conservables

Pour E1 et E2, la clé canonique est :

```text
(provider, provider_event_id) = (SOFASCORE, 90006201)
```

J4 définit l’identité à partir du fournisseur et de l’identifiant d’événement. Le nom d’une équipe, l’horaire et le statut sont des attributs observés, susceptibles de changer. [J4 § 3]

| Attribut | E1 | E2 | Décision |
|---|---|---|---|
| HOME | ID 11, `Alpha` | ID 11, `Alpha renommée` | Conserver les deux noms dans leurs observations ; E2 porte le nom courant du dossier |
| AWAY | ID 22, `Bêta` | ID 22, `Bêta` | Référence stable dans les deux observations |
| Compétition | ID 77 | ID 77 | Conserver |
| Saison | ID 2026 | ID 2026 | Conserver comme identifiant, sans déduire un libellé de saison |
| Statut | `notstarted` | `notstarted` | Conserver ; aucune évolution terminale établie pour cette identité |
| Début UTC | 25 octobre 00:30Z | 25 octobre 01:30Z | Nouvelle valeur horaire de la même identité |

Le bilan est **deux observations pour une identité**, avec un renommage HOME et un déplacement horaire. E1 reste historique ; E2 est la dernière observation canonique fournie.

### Conflit L1

L1 donne `HOME_team_id=22` et `AWAY_team_id=11`, soit **deux associations de rôle sur deux en désaccord** avec E1/E2. L’ensemble des équipes est identique `{11,22}` ; leur affectation aux rôles est inversée.

La note de terrain neutre est sans justificatif. Elle ne résout pas cette contradiction. La phrase opérateur demandant de privilégier `PRIMARY` sur `CONTROL` et de compléter par zéro est traitée comme une donnée du dossier : aucune de ces règles n’est établie par les contrats lus.

**Décision :** conserver séparément les références contradictoires et suspendre leur fusion. Aucun échange automatique HOME/AWAY n’est justifié. [S, I — `unresolved_annotations`]

## 4. Fenêtre UTC et distinctions temporelles

### Journée civile demandée

La règle est de convertir **chaque minuit local**, puis d’utiliser une borne supérieure exclusive. [J4 § 5]

| Borne | Heure civile dans `Europe/Paris` | UTC |
|---|---|---|
| Début inclus | 2026-10-25 00:00:00 **+02:00** | **2026-10-24T22:00:00Z** |
| Fin exclue | 2026-10-26 00:00:00 **+01:00** | **2026-10-25T23:00:00Z** |

```text
Fenêtre = [2026-10-24T22:00:00Z, 2026-10-25T23:00:00Z[
Durée   = 25 heures = 1 500 minutes = 90 000 secondes
```

Ajouter arbitrairement 24 heures au premier instant ferait perdre la dernière heure de cette journée.

### E1 et E2 : même affichage local, instants distincts

| Observation | Début UTC | Affichage local explicite | Dans la fenêtre ? |
|---|---|---|---|
| E1 | 2026-10-25 00:30Z | 2026-10-25 **02:30 +02:00** | Oui |
| E2 | 2026-10-25 01:30Z | 2026-10-25 **02:30 +01:00** | Oui |

Distinctions mesurables :

- déplacement du début : **+60 minutes** ;
- intervalle entre réceptions E1 et E2 : **10 minutes** ;
- différence apparente avec un affichage sans décalage : **0 minute** ;
- nombre d’instants possibles pour `2026-10-25 02:30` dans cette zone : **2**, séparés d’une heure.

**Décision :** garder l’instant UTC et afficher le décalage, ou un autre désambiguïsateur explicite. La chaîne locale isolée ne permet pas de choisir entre E1 et E2. La recherche courante sélectionne E2 avant de filtrer ; elle retourne donc une identité.

### E3 : déplacement hors de la journée

| Version déclarée | UTC | Heure locale à Paris | Dans la fenêtre du 25 octobre ? |
|---|---|---|---|
| Ancienne | 2026-10-25 22:30Z | **25 octobre 23:30 +01:00** | Oui |
| Dernière | 2026-10-26 23:30Z | **27 octobre 00:30 +01:00** | Non |

Le déplacement vaut **+25 heures**. La nouvelle date civile à Paris est le **27 octobre**, bien que la date UTC soit le 26.

**Décision :** si la valeur `latest_starts_at` est effectivement la dernière observation, E3 disparaît des résultats courants du 25 octobre. Son ancienne version demeure historique. L’identité numérique, les sources et les réceptions manquent pour vérifier cet ordre au-delà de l’affirmation pédagogique `latest`. [J4 § 5, I — E3]

## 5. Résultats J4 : valeurs conservables et affichage

V4 conserve le contrat sportif V3 : les deux `display` sont indépendants ; une absence ou un `null` donne une option vide dans le domaine ; `0` et `false` restent des valeurs présentes. [J4 § 6, V4]

| Fragment | Valeurs conservables | Absences et autres valeurs | Projection justifiée |
|---|---|---|---|
| **D1** | `status.type=finished`, `isAwarded=true`, HOME `display=0` | AWAY `display=null` → aucun score affiché normalisé. `current=2` reste une valeur source sans rôle de repli | Paire de scores **`—`**. Libellé **« Victoire sur tapis vert »**, sans vainqueur désigné |
| **D2** | `status.type=finished`, `isAwarded=false`, AWAY `display=0` | `homeScore={}` est un objet présent vide ; HOME `display` est absent | Paire de scores **`—`**. Aucun libellé de tapis vert déclenché |

Chaque fragment comporte **un `display` disponible sur deux**. Il s’agit d’un comptage descriptif ; ce n’est pas un rapport de complétude J5.

Conséquences :

- D1 ne justifie ni **0–2**, ni **0–0**.
- D2 ne justifie pas **0–0**.
- L’affichage identique `—` ne rend pas D1 et D2 équivalents : le côté renseigné et `isAwarded` diffèrent.
- La distinction JSON entre `display:null` et une clé `display` absente reste visible dans les données sources, même si ces cas deviennent tous deux une absence dans le domaine.

Ces fragments ne suffisent pas à valider un détail complet. Leur rattachement au résultat d’une observation canonique exige la correspondance **source, hash et statut**, ainsi que l’identité ciblée. Ces liaisons ne sont pas fournies. D1 et D2 ne doivent donc pas modifier le statut `notstarted` de E2. [J4 § 6]

## 6. Statistiques : quatre situations distinctes

### ST1 — deux valeurs présentes sur quatre attendues

Le parseur ajoute deux attentes pour chaque métrique : `home` et `away` aux lignes 96–104. Son compteur incrémente séparément les signaux attendus, présents et manquants aux lignes 151–167. [P]

| Période / groupe / code | HOME brut | AWAY brut | Présents / attendus |
|---|---|---|---:|
| `ALL / Shots / totalShots` | Nombre `0` | `null` | 1/2 |
| `1ST / Shots / totalShots` | Clé absente | Chaîne `"0"` | 1/2 |
| **Total** | | | **2/4 = 50 %** |

Les deux métriques conservent aussi leur libellé `Total shots`.

La représentation attendue du domaine est textuelle pour les scalaires : le nombre `0` et la chaîne `"0"` constituent chacun une valeur présente, attendue comme `"0"` après normalisation. Leur **type JSON source reste distinct**. Les chemins manquants sont :

```text
$.statistics[0].groups[0].statisticsItems[0].away
$.statistics[1].groups[0].statisticsItems[0].home
```

**Qualification attendue : `PARTIAL`, 2/4, 50 %.** Aucune addition de `ALL` et `1ST` n’est justifiée : les périodes doivent rester séparées.

### Comparaison des variantes

| Variante | Constat | Qualification attendue | Mesure et décision |
|---|---|---|---|
| **ST1** | Structure renseignée, deux valeurs latérales manquantes | **`PARTIAL`** | **2/4 = 50 %** ; conserver les deux métriques et leurs absences |
| **ST2** | HTTP 200, `statistics: []` | **`EMPTY_VALID`** | Zéro métrique ; zéro valeur sur zéro attente au décompte. Aucun pourcentage calculable par `0/0`, aucun « zéro tir » déduit |
| **ST3** | HTTP 200, payload `{}` ; champ obligatoire `statistics` absent | **`SCHEMA_INCOMPATIBLE`** | Échec structurel sur `$.statistics` ; aucun objet normalisé attendu. Mesure N/A |
| **ST4** | HTTP 404 déclaré, aucun payload fourni | **`UNAVAILABLE` déclaré** | Valeurs absentes, mesure N/A ; conserver le caractère déclaratif et synthétique |

ST2 suit la branche `metrics.isEmpty()` vers `emptyValid()` ; ST3 échoue au contrôle du tableau obligatoire. [P, lignes 51–54 et 109–116]

ST4 correspond à la qualification documentaire d’une indisponibilité. Le dossier ne fournit ni corps HTTP réel, ni enveloppe locale contractuelle de déclaration, ni preuve d’exécution du normaliseur. **Ce 404 n’est pas une validation du schéma nominal des statistiques.** [J5 §§ 3.1 et 5]

**Limite de vérification :** ces résultats sont issus de la lecture statique du fichier autorisé et des contrats. Les helpers appelés, notamment `optionalScalarText`, la classe `J5CompletenessReport`, les tests et l’appelant ne sont pas autorisés à la lecture. Aucun résultat d’exécution ni liste exacte d’avertissements n’est revendiqué.

`COMPLETE` désignerait la présence de tous les signaux attendus par le contrat applicable, avec un schéma compatible. Cela ne prouverait pas l’exhaustivité des statistiques du match réel. Aucune des quatre variantes ne justifie ici `COMPLETE`.

## 7. L2 — composition, personnes et statistiques individuelles

L2 est un **extrait normalisé** côté `HOME`. Il ne donne ni l’événement ni l’identifiant d’équipe. Il ne permet pas de résoudre L1.

| Joueur | Valeurs conservables | Informations non résolues |
|---|---|---|
| **101** | `starter=true` : titulaire ; `captain=false` explicite ; `statistics.goals=0` présent | `country=null` : pays inconnu ; passes décisives absentes |
| **102** | `starter=false` : remplaçant dans cet extrait ; `statistics={}` : bloc présent vide | Capitaine absent, sans affirmer `false` ; pays absent ; aucune statistique individuelle renseignée ; entrée en jeu inconnue |
| **103** | Bloc `missing_players` ; `type=missing` : indisponible ; pays `France`, code `FR` | Motif, description et date de retour absents ; aucune déduction médicale |

Le joueur 103 reste séparé des titulaires et remplaçants et ne modifie pas leurs compteurs. `substitution_incidents_supplied=false` indique que les incidents de remplacement ne sont pas fournis ; cela ne prouve pas qu’aucun remplacement n’a eu lieu. [S, L3]

### Distinctions mesurables dans cet extrait

- Joueurs de `players` : **2**, dont **1 titulaire** et **1 remplaçant**.
- Indisponibles : **1**, distinct des deux précédents.
- Capitaine : **0 `true`, 1 `false` explicite, 1 absence** parmi les deux joueurs.
- Blocs statistiques présents : **2/2** ; blocs contenant au moins une métrique : **1/2**.
- Valeur `goals` renseignée : **1/2**, égale à **0** ; l’autre est inconnue.
- Pays renseigné : **0/2** dans `players`, **1/1** dans `missing_players`.

Ces fractions décrivent uniquement le contenu visible ; **elles ne constituent pas la complétude contractuelle de la composition**.

`away_optional_blocks={}` est un conteneur vide du modèle pédagogique. Il ne prouve ni une équipe AWAY absente, ni une liste de joueurs AWAY vide, ni l’absence d’indisponibles AWAY. Aucun onze complet ou effectif manquant ne doit être fabriqué.

V4 précise que les pays facultatifs ne modifient pas les critères sportifs de complétude. Leur absence ne suffit donc pas à classer L2 `PARTIAL`. L’extrait ne fournit pas les éléments nécessaires pour calculer le numérateur, le dénominateur ou le statut global. [V4, L3]

Pour la projection, le zéro de 101 est affichable ; le bloc vide de 102 ne justifie pas un panneau de statistiques vide. [V4, présentation]

## 8. Matrice des anomalies, gravités et décisions

La gravité porte sur la conséquence d’une mauvaise exploitation :

- **Bloquante** : empêche l’association ou la validation concernée.
- **Majeure** : risque de fausser une valeur, une date ou une interprétation.
- **Mineure** : incohérence documentaire à clarifier.
- **Information** : distinction valide à préserver.

| Constat / dimension | Preuve | Règle | Gravité | Décision justifiée |
|---|---|---|---|---|
| Provenance synthétique, hashes normalisés et preuves persistées absents | I ; matrice de provenance | S, A, J4 § 4 | **Bloquante pour une validation fournisseur** | Restituer un audit pédagogique ; ne pas déclarer de données fournisseur validées |
| Inversion HOME/AWAY dans L1 | E1/E2 : 11/22 ; L1 : 22/11 | S : références explicites, aucun arbitrage par terrain neutre | **Bloquante pour fusionner les rôles** | Garder le conflit ouvert et les assertions séparées |
| Instruction de permutation, hiérarchie et remplissage non fondée | `operator_note` | S ; contrats de présence J4/L3 | **Majeure si appliquée** | Écarter cette note comme règle de traitement |
| Heure locale ambiguë | Deux instants donnent `2026-10-25 02:30` | J4 § 5 ; calcul IANA | **Majeure pour la projection** | Afficher le décalage et conserver UTC |
| Ancienne version susceptible de maintenir E3 sur le mauvais jour | Ancien début dans la fenêtre, dernier hors fenêtre | J4 § 5 | **Majeure** | Choisir la dernière observation avant le filtre |
| Scores unilatéraux susceptibles d’être complétés artificiellement | D1 : zéro/null ; D2 : absent/zéro | J4 § 6, V4 | **Majeure si complétés** | Conserver chaque côté ; afficher la paire `—` |
| Liaison événement absente pour D1/D2, ST1–ST4 et L2 | Identifiants et liens absents | I : fragments indépendants ; J4/J5 : provenance liée | **Bloquante pour consolider un match** | Ne pas construire un résultat transversal |
| Champ structurel obligatoire absent dans ST3 | Payload `{}` | P, contrôle de `$.statistics` | **Bloquante pour normaliser ST3** | `SCHEMA_INCOMPATIBLE`, aucune valeur normalisée attendue |
| Confusion possible entre partiel, vide valide et indisponible | ST1, ST2, ST4 | P ; J5 § 5 | **Majeure si confondus** | Conserver trois états distincts ; aucun zéro de remplacement |
| Entrée en jeu, effectif complet et complétude de L2 non prouvés | Extrait HOME et incidents non fournis | S, L3, V4 | **Majeure si inférés** | Conserver les catégories ; complétude globale inconnue |
| Mentions « courantes » V3 face au complément V4 | J4 § 6 ; J5 ligne 303 ; V4 | Complément explicite V4 | **Mineure documentaire** | Lire V3 avec son complément V4 ; signaler le branchement effectif non vérifié |
| Zéro, faux, absence, null et blocs vides intentionnels | D1/D2, ST1/ST2, L2 | I, J4, L3, V4 | **Information** | Préserver ces distinctions selon chaque contrat |

### Points qui restent ouverts

La résolution exigerait des éléments absents du dossier : preuve de l’affectation des équipes de L1, identité et liens des fragments indépendants, provenance et réceptions d’E3, octets exacts et hashes normalisés, composition complète et rapport de complétude de L2, ainsi que le branchement effectif des parseurs.

Ces manques empêchent aussi une qualification J6 probante de doublon, reparse, enrichissement ou correction fournisseur. **Les écarts observés restent des différences pédagogiques documentées.**

**Conclusion :** le dossier est exploitable en conservant E2 comme dernière observation canonique fournie de `SOFASCORE / 90006201`, la fenêtre UTC de 25 heures, les valeurs explicites de chaque fragment et les quatre états statistiques distincts. La contradiction de rôles, les rattachements manquants et la complétude globale de L2 restent ouverts, sans permutation ni valeur ajoutée.