# J5 — Validation hors ligne des règles métier incidents V6

## 1. Statut

```text
DATE=2026-08-17
SCOPE=EVENT_INCIDENTS_FOOTBALL
PARSER=event-incidents-v6
FLYWAY_VERSION=13
VALIDATION=PASS_OFFLINE
STANDARD_TESTS=324
STANDARD_FAILURES=0
INTEGRATION_TESTS=27
INTEGRATION_FAILURES=0
REAL_PROVIDER_CALLS_BY_AGENT=0
PROVIDER_SCHEMA_VALIDATED=NO
HUMAN_V6_RETEST=REQUIRED
```

Cette unité transpose la fiche métier fournie par le propriétaire dans le parseur réel J5. Elle
reste `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY`. Aucun test standard ou d'intégration ne contacte SofaScore.

## 2. Source métier relue

La version canonique est
`docs/requirements/J5-FOOTBALL-INCIDENT-RULES.md`. Le propriétaire a autorisé la correction des
coquilles afin d'éviter toute ambiguïté. La relecture a notamment fixé :

- les sous-sections `10.2` et `10.3` du chapitre 10 ;
- les libellés entrant/sortant selon `playerIn` et `playerOut` ;
- la position `F` comme `Forward` ;
- la distinction entre l'alias brut `owngoal` et la valeur canonique locale `ownGoal` ;
- les couples cohérents classe/motif/description des penalties ;
- le repli temporel explicitement autorisé pour une séance de tirs au but.

Les grands exemples JSON restent des observations documentaires. Les règles placées sous
« Attributs » et « Valeurs à afficher pour J5 » définissent le contrat appliqué.

## 3. Matrice par type

| Type fournisseur | Minute effective | Valeurs normalisées principales | Absence compatible |
|---|---|---|---|
| `period` | `time` | texte, score ; `addedTime=999` reste brut uniquement | texte, score ou `isLive` attendu → `PARTIAL` |
| `substitution` | `time` | côté, classe, blessure, entrant, sortant | métadonnée ou participant manquant → `PARTIAL` |
| `goal` | `time` | côté, buteur, passeur, score, classe, origine | donnée d'affichage manquante → `PARTIAL` |
| `card` | `time`, ou `benchTime` si `time<0` | côté, joueur, classe, motif, annulation | joueur ou classe manquant → `PARTIAL` |
| `injuryTime` | `time` | durée additionnelle | durée manquante → `PARTIAL` |
| `varDecision` | `time` | côté, joueur, classe, confirmation | métadonnée attendue manquante → `PARTIAL` |
| `inGamePenalty` | `time` | joueur, classe, motif, description | métadonnée attendue manquante → `PARTIAL` |
| `penaltyShootout` | `time`, sinon première action auxiliaire | joueur, côté, classe, motif, description, ordre, score | métadonnée attendue manquante → `PARTIAL` |

Un nom de joueur peut être conservé sans identifiant numérique. L'identifiant manquant n'est
jamais synthétisé. Les structures auxiliaires non normalisées restent présentes uniquement dans
le snapshot brut.

## 4. Incompatibilités structurantes

Le parseur refuse toute la famille, sans observation normalisée partielle, lorsque l'un des cas
suivants est rencontré :

- `incidentType` absent ou différent des huit valeurs documentées ;
- aucune minute effective selon la règle du type ;
- type JSON incorrect ou valeur hors vocabulaire fermé ;
- score domicile/extérieur fourni d'un seul côté ;
- `playerName` contradictoire avec `player.name` ;
- classe et origine d'un but contradictoires ;
- classe, motif et description d'un penalty décrivant des résultats différents ;
- libellé de période live explicitement associé à `isLive=false` ;
- `benchTime` associé à un carton dont `time` est déjà non négatif ;
- temps négatif en dehors de la règle de carton de banc.

Le brut est persisté avant cette décision et n'est jamais réécrit.

## 5. Persistance et rendu

`V13__j5_football_incident_business_rules.sql` est append-only. Elle ajoute les colonnes
facultatives nécessaires au modèle V6 et autorise sa provenance sans modifier V1–V12. Les règles
append-only et la déduplication restent effectives.

La page J5 présente les valeurs normalisées en colonnes : minute, type, classe, côté, joueur,
entrant, sortant, passeur, motif, détail, ordre de séance et score. Un tiret indique une valeur
absente ou non applicable ; aucune donnée n'est inventée depuis l'interface.

## 6. Preuves de régression

Les tests V6 couvrent :

- les huit types dans un payload nominal ;
- la conservation des huit incidents lorsque leurs métadonnées attendues manquent, avec statut
  global `PARTIAL` ;
- les vocabulaires documentés et leurs valeurs rejetées ;
- les contradictions atomiques ;
- le carton de banc et l'incompatibilité d'un `benchTime` attaché à un temps ordinaire ;
- la sentinelle `addedTime=999` ;
- l'alias brut `owngoal` ;
- le repli temporel d'un `penaltyShootout` ;
- la persistance et la relecture V13 ;
- le rendu MVC des nouveaux champs ;
- la poursuite ordonnée `STATISTICS → INCIDENTS → LINEUPS` sans retry lorsque V6 est compatible.

Les deux suites requises par `AGENTS.md` ont été exécutées avec Java 25.0.4 :

```text
COMMAND=.\mvnw.cmd clean verify
RESULT=BUILD_SUCCESS
TESTS=324
FAILURES=0
ERRORS=0
SKIPPED=0

COMMAND=.\mvnw.cmd -Pintegration-tests verify
RESULT=BUILD_SUCCESS
STANDARD_TESTS=324
STANDARD_FAILURES=0
INTEGRATION_TESTS=27
INTEGRATION_FAILURES=0
FLYWAY_FRESH_INSTALL=V1_TO_V13_PASS
FLYWAY_UPGRADE=V12_TO_V13_PASS
```

Les avertissements affichés concernent uniquement l'auto-attachement Mockito/Byte Buddy sous
Java 25 ; ils ne constituent ni un échec de test ni un appel fournisseur.

## 7. Preuve humaine antérieure et suite

Le parseur V5 a déjà terminé une campagne Paris Saint-Germain — Lens pendant la première
mi-temps, snapshots 61/62/63, puis un rejeu manuel à la fin du match, snapshots 64/65/66. Le second
snapshot incidents contient 22 faits de jeu et les compositions sont présentes. Cette preuve ne
vaut pas qualification réelle du nouveau parseur V6.

Le prochain geste autorisable est donc un retest humain V6 après redémarrage, préparation sans
réseau, nouvelle phrase exacte, acquittement et action finale distincte. La configuration devra
ensuite être reverrouillée et l'application arrêtée gracieusement. L'agent n'a ni lu ni modifié
`.env` et n'a exécuté aucun appel fournisseur.
