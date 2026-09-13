# WO-058 — Reprise V8 après dépassement de cadence J4

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`,
`NO_CRITICAL_DEPENDENCY`.

## Signal traité

Une campagne locale a exposé une donnée J5 périmée pendant presque six minutes alors que le
transport J4 précédent avait bien reçu une réponse complète. Le départ `REQUEST_SENT` de ce J4
était arrivé au-delà de sa fenêtre stricte de 500 ms. Il ne s'agissait donc ni d'un timeout
Playwright ni d'une réception absente.

L'ancien chemin classait correctement le groupe comme manqué, mais utilisait par erreur le
backoff commun de cinq minutes, réservé aux timeouts récupérables et aux dépassements
d'enveloppe. Le recontrôle J4 et les familles J5 restaient ainsi en attente jusqu'à ce délai.

## Correctif borné

`GroupedLiveScheduleV8.deferAfterCadence(...)` conserve les faits suivants :

- `WAITING_CADENCE_RECHECK` et le cycle J4/J5 manqué restent visibles ;
- les trois familles J5 ordinaires abandonnées ne reçoivent aucun rattrapage immédiat ;
- les compteurs de cycles et de familles manqués restent incrémentés ;
- le fence de 500 ms, les enveloppes, la capacité de dix et les budgets de 45/minute et
  2 756/heure restent inchangés.

Au lieu de `completedAt + 5 min`, le prochain J4 est proposé sur la phase stable dérivée du
dernier départ authentifié : `REQUEST_SENT + 60 s − 500 ms`. Si ce créneau est déjà passé à la
complétion, le planificateur choisit le créneau futur suivant. Les J5 ne sont recréés qu'après
la réponse `inprogress` de ce J4 de recontrôle.

`LiveTimeoutRecoveryPolicy.RETRY_DELAY` reste à cinq minutes pour les timeouts Playwright
récupérables et les dépassements d'enveloppe. La correction ne réarme pas un accès fournisseur,
ne modifie aucun contexte Playwright, ne contourne aucune vérification humaine et ne modifie pas
une campagne déjà en cours.

## Preuves locales

La régression
`GroupedLiveScheduleV8Test.lateWorkerEmissionLeavesTheStrictPathButRechecksOnTheNextStableMinutePhase`
force un départ J4 à `500 ms + 1 ns` après la fenêtre stricte. Elle vérifie que :

1. aucune échéance n'est proposée avant le prochain slot J4 stable ;
2. le recontrôle est un nouveau groupe `J4_CADENCE_RECHECK` ;
3. son départ dans la fenêtre reprend `COLLECTING` ;
4. le premier J5 est seulement alors `EVENT_INCIDENTS`, phasé depuis ce nouveau départ ;
5. les régressions timeout et enveloppe conservent leur échéance `+300 s`.

Le calculateur V8 a ensuite relu la preuve loopback versionnée sans démarrer Chromium, Spring,
PostgreSQL, une campagne ou un transport fournisseur. Il a produit le profil V8 à dix rencontres
SHA-256 `c5cef2745422d70bab769d03a93991af8ce3d685fb9daabb64fd00ab0a1ca3c8`, lié au bytecode V8
courant SHA-256 `ed52e5a53706218cb64233b18cd2567cd0a5e8fb61939b06a4db4846d70931ec`, avec 16 scénarios
d'admission rejoués et `V8_OPERATOR_CONFIGURATION_CHANGED=NO`.

La régression ciblée suivante est verte :

```powershell
.\mvnw.cmd -Dtest=GroupedLiveScheduleV8Test,GroupedLiveAdmissionPolicyV8EvidenceTest,GroupedLiveAdmissionPolicyV8Test,LiveTimeoutRecoveryTest,CountryAssetsTest,LineupsPresentationTest,EventDetailsPresentationTest,J5EventDataControllerTest test
```

Résultat : **74 tests**, zéro échec, zéro erreur. La commande a utilisé un dépôt Maven local
miroir et les propriétés JVM de test du Work Order ; elle n'a pas démarré de navigateur ni de
transport fournisseur.

Le rapport de capacité, les huit enveloppes et les limites de portée restent détaillés dans
[WO058-LIVE-V8-CAPACITY-20260910.md](WO058-LIVE-V8-CAPACITY-20260910.md). Cette preuve ne
mesure ni l'acceptation du fournisseur, ni sa protection anti-robot, ni la latence Internet.

## Passe Maven complète

La passe `clean verify` hors fournisseur a atteint 2 206 tests Surefire avant deux échecs
indépendants de ce correctif : `LiveOrphanProcessProbeTest` et
`J6NativeBinaryPipelineQualificationTest`. Sur cet hôte Windows, la lecture CIM du processus
courant retourne `Accès refusé`, tandis que l'outil J6 observe
`CIM_ERROR,TASKLIST_ERROR,CLASS_UNVERIFIABLE`. Les deux contrôles se ferment donc
volontairement en échec sûr plutôt que d'accepter une identité de processus non confirmée.
La suite Failsafe générale ne démarre pas après ces échecs Surefire. Le code J6 n'a pas été
modifié ; la régression V8 ciblée de 74 tests et la qualification Chromium loopback de 3 tests
restent vertes.
