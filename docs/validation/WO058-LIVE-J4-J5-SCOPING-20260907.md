# WO-058 — Preuve de cadrage documentaire J4/J5 live, 7 septembre 2026

Cette preuve conserve les étapes documentaires et leurs résultats à leur date. Le propriétaire
a ensuite déclaré « Je valide le WO-058 les travaux peuvent commencer », avec le port 8087
libéré pour les tests. Le WO est passé à `IN_PROGRESS` ; la réalisation et ses nouvelles
exécutions sont distinguées dans le [rapport de réalisation](WO058-LIVE-J4-J5-IMPLEMENTATION-20260907.md).
La réussite ciblée du test J6 le 7 septembre à 09:46:09Z (1 test, 0 échec, 0 erreur, 0 ignoré)
ne réécrit pas la suite complète non verte conservée ci-dessous.

## Périmètre et base

- [Work Order](../work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md) :
  `IN_PROGRESS` à l'ouverture de réalisation ; ADR et WO acceptés le 7 septembre 2026.
  L'état courant `READY_FOR_REVIEW` et la qualification sont dans le rapport de réalisation.
- [ADR-SS-005 v0.1](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) :
  `ACCEPTED` ; acceptation propriétaire du 7 septembre enregistrée, réalisation désormais autorisée.
- [Proposition acceptée figée](ADR-SS-005-v0.1-accepted-proposal-20260907.txt) : copie exacte,
  conservée avant enregistrement administratif de l'acceptation ; les deux empreintes sont distinguées plus bas.
- Train source : `feature/V0.1.0-RC01` au SHA
  `6dfd14286d4f269cbe100bd965257c20298538db`.
- `git ls-remote --heads origin 'feature/V*'` a confirmé ce sommet GitHub le 7 septembre.
- Branche isolée : `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
- Worktree : `C:/Dev/BettingProject/codex/betting-sofascore-local-lab/.tmp/wo058-live-j4-j5`.
- Racine de départ : ancienne branche `codex/j9-decision`, `1a58a3b`, conservée sans changement.
- Statuts préservés : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

L'AGENTS.md réel du train et les skills personnels `ss-work-order`, `ss-data-contract-replay`,
`ss-postgres-change` et `ss-verify` ont été lus. Le cadre suit les WO voisins effectivement présents,
sans modèle de chemin supposé. La capture fournie et la tâche **Poursuivre collectes** ont été
consultées ; les instructions historiques de cette tâche n'ont pas été exécutées dans WO-058.

Trois analyses parallèles en lecture seule ont couvert contrats/UI, persistance et gouvernance.
Leur relecture du draft a précisé : projection par occurrence A→B→A ; score J4 versionné ; signaux
de période ; brut conservé avant parsing ; reprise des seuls propriétaires orphelins ; upgrade
depuis le véritable précédent ; délai du dernier J5 et arrêt individuel compatible avec le contexte partagé.

## Commandes et résultats de l'ouverture initiale, conservés

Cette section décrit les exécutions de l'ouverture du WO, avant la rédaction d'ADR-SS-005.
Ses contrôles à quatre documents et son observation de listener sont historiques ; la phase ADR
est distinguée plus bas et ne revendique pas de nouvelle exécution Maven.

| Contrôle | Résultat |
|---|---|
| État Git initial, branches et worktrees | Worktrees visés propres avant délégation ; source récente identifiée |
| `git ls-remote --heads origin 'feature/V*'` | Premier accès sandbox indisponible ; relance hors sandbox réussie, aucun push |
| `git worktree add .tmp/wo058-live-j4-j5 -b feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058 origin/feature/V0.1.0-RC01` | Créé après accès aux métadonnées Git communes hors sandbox ; base exacte ci-dessus |
| Lecture `pom.xml` et `scripts/Verify-Local.ps1` | Java 25/Spring Boot 4.1.0 confirmés ; distinction standard/Failsafe et profils consignée |
| `java -version` | Java 25.0.4 LTS |
| `mvnw.cmd clean verify`, première tentative sandbox | Code 1 avant compilation : parent Spring Boot 4.1.0 non résolu, `Permission denied: getsockopt` |
| Même `mvnw.cmd clean verify`, relance hors sandbox | Compilation réussie ; code 1 dans Surefire, build non vert |
| Lecture des 167 rapports XML Surefire de cette relance | 1 193 tests recensés, 1 échec, 0 erreur, 5 skips ; 1 187 réussites |
| Failsafe / `-Pintegration-tests verify` | Non exécuté ; la commande s'arrête dans Surefire, aucun rapport Failsafe ; aucune migration modifiée |
| Listener 8087, lecture seule hors sandbox | `127.0.0.1:8087`, PID 7120 observé après build ; aucun arrêt demandé au processus |
| `git diff --check`, décodage UTF-8 strict, liens Markdown locaux des quatre documents | Vérifiés sur le contenu documentaire final |
| Contrôle de secrets sur les quatre documents, revue du diff et des fichiers nouveaux | Aucun secret détecté ; noms de champs et identifiants publics/documentaires seulement |
| Défauts réseau et diff applicatif | `server.address: 127.0.0.1`, fournisseur/Playwright désactivés par défaut ; aucun fichier de code/configuration/migration modifié |

Le build s'est terminé le `2026-09-07T08:44:38Z`, en 1 min 43 s. L'échec est :

```text
Classe : J6NativeBinaryPipelineQualificationTest
Test : syntheticNativePipelineFailsClosedWithoutHumanPassphraseInput
Classification : LOOPBACK_APPLICATION_LISTENER_RESIDUAL
Résultat attendu du script : 0 ; résultat reçu : 1
```

Le script existant `scripts/Invoke-J6BackupRestoreLoopbackQualification.ps1`, vers la ligne 3283,
énumère les listeners TCP et refuse toute présence sur 8087. Le listener observé après la suite
explique cette classification. La suite n'attribue pas ce listener à un processus créé par le test ;
aucune régression applicative imputable au diff documentaire n'est établie. Aucune qualification
verte n'est revendiquée et aucun arrêt du Lab utilisé par l'opérateur n'a été effectué pour verdir
la preuve. Les cinq skips restent des skips, pas des tests réussis.

Le rapport XML détaillé est disponible dans le worktree sous
`target/surefire-reports/TEST-com.bettingproject.sofascorelocal.build.J6NativeBinaryPipelineQualificationTest.xml`.
Ces sorties runtime sont ignorées par Git. Aucune relance complète supplémentaire n'est effectuée
sans changement d'environnement ; une future qualification complète exigera une fenêtre où 8087 est libre.

## Limites et résultat livrable

Cette preuve atteste le cadrage et la revue du document. Elle ne qualifie pas la machine live,
les futures migrations, un long contexte Playwright ni une campagne fournisseur. Les critères AC01
à AC17 du WO restent à exécuter lors de la réalisation et ne sont pas cochés comme acquis.

Lors de l'ouverture initiale, aucun appel SofaScore, lancement de campagne, modification d'ADR, connexion à la base primaire,
mutation de configuration locale, planification COV-002, export J7, push Git ou PR n'a été effectué.
Les tests standards comportent leurs contrôles synthétiques locaux ; ils ne constituent pas une
activation de Playwright ni une sauvegarde/restauration de la base primaire.

Fichiers de cette ouverture initiale : le WO, ce rapport, le lien d'orientation README et l'entrée CHANGELOG.
La phase documentaire suivante établit l'ADR proposé et conserve ces preuves sans les convertir
en succès ni en autorisation de collecte.

## Phase de proposition ADR-SS-005 — 7 septembre 2026, conservée

Cette section décrit la phase antérieure à l'acceptation formelle. Ses états `PROPOSED` et
`PENDING`, ses cinq fichiers et ses huit empreintes de contrôle sont des observations historiques.
La phase d'enregistrement suivante établit l'état courant sans réécrire ces preuves.

Le propriétaire a demandé un ADR préalable à la validation du WO, répondu aux arbitrages en
mode Plan, puis demandé explicitement la réalisation documentaire du plan. Cette dernière
instruction autorise la création de l'ADR proposé et l'alignement des documents ; son texte
réserve l'acceptation formelle de l'ADR et la validation de WO-058 à des décisions distinctes.

### Contenu précis soumis à acceptation

```text
FILE=ADR-SS-005-bounded-local-live-j4-j5-campaigns.md
VERSION=0.1
STATUS=PROPOSED
FUNCTIONAL_CHOICES=OWNER_VALIDATED
FORMAL_ACCEPTANCE=PENDING
SHA256=48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e
```

L'empreinte couvre les octets UTF-8 de la version relue, pas une acceptation propriétaire.
Elle est conservée ici, hors du fichier ADR, sans valeur autoréférente. La table de
décision de son §3 reprend les choix du plan : capacité progressive, cadences, fenêtre,
budget, isolation métier, 404 et clôture. La validation fonctionnelle ne vaut pas une exécution
réussie des futures boucles.

### Alignement et revue effectués

- ADR v0.1 ajouté avec contexte, portée de supersession proposée, paramètres, règles de session,
  machine nominale, erreurs, données, alternatives, conséquences, critères et historique.
- WO-058 aligné : en-tête et lien ADR, choix devenus acquis, machine d'états, matrice entrée/résultat,
  AC10 et étapes d'acceptation. Le schéma métier incompatible n'est plus un arrêt global par défaut.
- Priorité des erreurs explicitée : JSON admissible et schéma incompatible identifié = arrêt du
  match ; identité, sécurité, `UNEXPECTED_CONTENT`, exception interne, transport, stockage ou
  anomalie non classifiable = arrêt global. Le 404 J5 reste rééchantillonné au cycle normal suivant.
- Deux relectures indépendantes en lecture seule ont couvert gouvernance et contrats. Les
  clarifications retenues ciblent seulement les futurs renvois ADR-SS-001/AGENTS.md et la distinction
  entre arrêt individuel et fermeture du contexte partagé. Aucun autre point actionnable n'est
  relevé dans la revue finale des contrats, séquences et provenance.
- README et changelog actualisés ; les chiffres et incidents du build initial restent historiques.

### Contrôles de cette phase

| Contrôle | Résultat / portée |
|---|---|
| `git status --short`, branche et HEAD | Même branche WO-058 et base `6dfd142` ; reprise des quatre documents de ce lot déjà présents, ajout du seul ADR-SS-005 |
| `git diff --check` + espaces des fichiers nouveaux | Réussi ; les fichiers non suivis sont aussi contrôlés explicitement |
| Décodage UTF-8 strict des cinq fichiers et liens locaux ADR/WO/rapport | Réussi ; cibles documentaires présentes et aucune séquence invalide |
| Scan ciblé de secrets et revue des textes modifiés | Aucun secret détecté ; aucun fichier privé lu |
| Empreintes avant/après de huit fichiers protégés ou applicatifs | Identiques : ADR-SS-001 à 004, AGENTS.md, POM, Verify-Local et application.yml |
| Diff applicatif/configuration/migrations | Vide ; même adresse loopback et opt-ins fournisseur/Playwright désactivés par défaut |
| Cohérence des erreurs et décisions | Revue ciblée `rg`, machine, matrice et AC10 concordants ; critères futurs non cochés |
| `mvnw.cmd clean verify` | Pas de nouvelle exécution, conformément au plan : seul le contenu documentaire évolue et aucune nouvelle fenêtre de qualification n'a été établie |
| `-Pintegration-tests verify` / Playwright loopback / campagne réelle | Non exécutés ; aucune réalisation applicative ou SQL dans cette étape |

La suite initiale reste à 1 193 tests recensés, 1 échec, 0 erreur et 5 skips, au timestamp
indiqué plus haut. Le listener 8087 observé lors de cette exécution n'a pas été arrêté ; sa
présence historique n'est pas présentée comme une nouvelle observation de cette phase.

Fichiers de cette phase de proposition : ADR-SS-005, WO-058, ce rapport, README et CHANGELOG.
Les modifications étaient locales, sans commit, push ou PR. Aucun ADR accepté, code, migration,
configuration réseau, base primaire ou campagne n'était modifié. À l'issue de cette phase,
la décision attendue portait sur l'acceptation de la v0.1, désormais reçue et enregistrée ci-dessous.

## Acceptation formelle et mise au propre du WO — 7 septembre 2026

### Déclaration et identité du contenu accepté

Le propriétaire a déclaré :

> Je valide formellement la v0.1 de l'ADR.

Il a demandé la mise au propre de WO-058 à partir de cet ADR et une branche issue de
`feature/V0.1.0-RC01`, puis explicitement autorisé l'exécution du plan d'enregistrement.
L'acceptation a été observée à `2026-09-07T09:23:14Z` ; cet instant provient de l'horloge de
la session et n'est pas présenté comme l'heure exacte du message, non disponible.

| Référence | Identité vérifiée | Sens |
|---|---|---|
| Proposition v0.1 acceptée | SHA-256 `48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e` | Octets effectivement présentés au propriétaire et acceptés, conservés dans la copie figée `.txt` |
| ADR v0.1 après enregistrement administratif | SHA-256 `48105be765fbe943e5153ff551ea5eaf5e3b79345c2b08c28d57581dee78ca51` | Document portant désormais `ACCEPTED`, déclaration et historique ; pas une autre version normative |
| Base Git du WO | SHA `6dfd14286d4f269cbe100bd965257c20298538db` | Sommet source du train ; aucun commit n'est attribué au draft non committé |

La copie est créée octet pour octet **avant** toute modification de l'ADR. Elle garde son contenu
historique `PROPOSED` puisque ce sont ces octets qui ont été acceptés. Son extension `.txt`
signale une archive brute : les liens qui y sont reproduits se résolvaient depuis la racine
d'origine et ne sont pas corrigés. La règle `.gitattributes` exacte visant ce seul fichier porte
`-text`, sans exception globale pour les `.txt`.

### Mise au propre réalisée

- ADR-SS-005 conserve la version 0.1 et passe à `ACCEPTED`. Les mentions courantes de proposition
  et d'acceptation en attente sont actualisées ; son historique de rédaction reste daté.
- WO-058 passe à `READY_FOR_OWNER_REVIEW` : cadre ADR acquis, mêmes décisions fonctionnelles,
  matrices et AC10, réalisation non commencée et validation propriétaire du WO encore distincte.
- ADR-SS-001 garde la version historique 1.4 et ses décisions ; seul un renvoi près de l'en-tête
  expose la portée d'ADR-SS-005 et le maintien des autres dispositions/règles de réexamen.
- AGENTS.md conserve ses protections et précise seulement les invariants 3 et 11 : lancement
  manuel, cycles automatiques dans la session live bornée, aucun navigateur créé par tick.
- README, changelog et présent rapport distinguent l'état actuel et les phases historiques.
- La branche existante `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058` et son worktree sont conservés.
  HEAD et merge-base avec `origin/feature/V0.1.0-RC01` correspondent à la base ci-dessus ; la lecture
  GitHub faite lors de la préparation confirmait le même sommet du train. Aucune branche n'est recréée.

### Contrôles de l'enregistrement

| Contrôle exécuté | Résultat |
|---|---|
| SHA-256 de la source avant copie puis de la copie figée | Correspondance exacte avec l'empreinte acceptée |
| `git check-attr text eol -- docs/validation/ADR-SS-005-v0.1-accepted-proposal-20260907.txt` | `text: unset`, `eol: unspecified` |
| `git hash-object --no-filters` comparé à `git hash-object --path=...` sur la copie | Même identifiant `f1290d7d049b43930cf8978a2827587d0bbc1c89` ; aucune transformation des octets par les filtres Git, aucun objet écrit par ces commandes |
| Comparaison des sections normatives 4 à 7 de l'ADR avec la copie | Identiques ; session, transitions, erreurs, provenance et consultation inchangées |
| Revue du diff administratif et des renvois | Acceptation ADR acquise, revue WO encore distincte, aucun changement des décisions fonctionnelles |
| UTF-8 strict, liens documentaires et espaces des neuf fichiers concernés | Réussis ; archive brute contrôlée comme octets, sans réinterprétation de ses liens historiques |
| `git diff --check` et contrôle explicite des fichiers non suivis | Réussis |
| Scan ciblé de secrets et revue des textes | Aucun secret détecté ; aucun fichier privé lu |
| Empreintes avant/après des six références conservées | Identiques : ADR-SS-002 à 004, POM, Verify-Local et application.yml |
| Diff code/migrations/configuration runtime | Vide ; `127.0.0.1` effectif par configuration et opt-ins fournisseur/Playwright désactivés par défaut |
| Maven / intégration / Playwright / campagne | Aucune nouvelle exécution ; preuve Maven initiale non verte conservée conformément au plan |

La copie figée et l'ADR administratif ont chacun leur empreinte contrôlée après rédaction du
rapport. Les identifiants Git des filtres prouvent la conservation locale des octets lors d'une
future mise en Git, pas un commit, un push ou un checkout distant déjà exécuté.

### État de sortie

```text
ADR_SS_005_VERSION=0.1
ADR_SS_005_STATUS=ACCEPTED
ADR_SS_005_FORMAL_ACCEPTANCE=OWNER_ACCEPTED_2026_09_07
WO058_STATUS=READY_FOR_OWNER_REVIEW
WO058_VALIDATION=PENDING_OWNER_REVIEW
RUNTIME_IMPLEMENTATION=NOT_STARTED
NEW_PROVIDER_CAMPAIGN=NOT_EXECUTED
GIT_DELIVERY=LOCAL_UNCOMMITTED
```

La suite Maven reste à 1 193 tests recensés, 1 échec J6 lié au port 8087, 0 erreur et 5 skips.
Aucun arrêt d'application, build, contact primaire, collecte ou transfert n'est effectué pour
cette mise au propre. Les neuf fichiers du lot sont `.gitattributes`, AGENTS.md, ADR-SS-001,
ADR-SS-005, sa copie figée, WO-058, ce rapport, README et CHANGELOG. Les changements restent
locaux, sans commit, publication ni clôture. La prochaine décision est la validation du WO
pour sa réalisation ; l'acceptation d'ADR-SS-005 v0.1 n'est plus à demander.
