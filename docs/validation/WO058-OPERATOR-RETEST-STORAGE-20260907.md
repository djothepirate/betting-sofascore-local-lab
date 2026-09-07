# WO-058 — Deuxième retour opérateur et configuration de l'admission locale

Date : 7 septembre 2026. Base : commit `017888b4e16655c761f862e4381c1e4737aefb0a`, branche
`feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## Résultats opérateur acquis

Les captures 1 et 2 et la déclaration du propriétaire confirment la conformité du parcours
d'exclusion pour un et trois événements localement `finished`. La page indique les rencontres,
l'absence de campagne et l'absence d'appel fournisseur. Ce constat porte sur l'interface et
les essais déclarés ; aucun audit supplémentaire de la base opérateur n'a été réalisé ici.

Les autres captures montrent une sélection encore localement `notstarted`, puis une sélection
mixte, et un refus sur `/live-campaigns/prepare`. L'opérateur déclare qu'aucune campagne n'a été
lancée. Pour Olympique Lyonnais — Auxerre, ID fournisseur 16310940, l'observation initiale du
4 septembre à 10:15:32.312Z, snapshot 888, est remplacée dans la vue courante par un J4 manuel
`finished` du 7 septembre à 12:07:04.498Z, snapshot 1246. L'historique passe de une à deux versions.

Le propriétaire précise qu'il n'a pas encore essayé de rencontre `notstarted` réellement future.
Ce retour ne valide donc ni lancement live réel, ni attente du coup d'envoi, ni cadence automatique.

## Cause du refus et vérification locale

Le propriétaire répond que les paramètres Docker ne sont pas renseignés dans le lanceur.
`LiveCampaignService.prepareSelection` exclut les matchs `finished`, puis appelle l'admission
pour les autres. Le chemin Docker absent déclenche `LIVE_STORAGE_PROBE_NOT_CONFIGURED`, que
le contrôleur présentait comme un problème générique de lancement/opt-in. Le nom du conteneur
possède déjà un défaut correct ; l'absence du chemin exécutable est le blocage établi par le code
et la configuration déclarée. Aucun journal runtime du processus Eclipse n'a été consulté.

L'âge du snapshot et l'heure du match ne déclenchent pas ce refus. Après le J4 manuel, `finished`
contourne légitimement toute admission car le match est exclu. Cela ne prouve pas que l'admission
est configurée pour les autres événements.

Vérifications en lecture seule sur le poste :

- CLI détectée : `C:/Users/geoff/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe`.
- Conteneur `betting-sofascore-local-lab-postgres` en cours d'exécution, confirmé par `inspect`
  limité à `.State.Running`.
- `docker exec betting-sofascore-local-lab-postgres df -Pk /var/lib/postgresql` réussit et annonce
  999 366 064 Kio disponibles, au-delà de la réserve calculée pour un match. Cette mesure ponctuelle
  ne remplace pas celle de chaque future préparation. Aucun SQL ni migration n'a été exécuté.

## Correction

Les refus locaux connus exposent un code et un message précis : configuration manquante,
mesure impossible/interrompue/trop lente, espace insuffisant, politique invalide ou capacité
non qualifiée. Seuls des codes autorisés sont affichés ; les exceptions arbitraires restent masquées.
Les variables `SOFASCORE_LIVE_DOCKER_EXECUTABLE` et `SOFASCORE_LIVE_POSTGRES_CONTAINER` sont
explicitement reliées à `application.yml`. Le chemin n'est pas découvert automatiquement et
les opt-ins restent désactivés par défaut. Aucun fichier `.env` ni lanceur Eclipse n'est modifié.

Le [runbook](../runbooks/LIVE-J4-J5-CAMPAIGNS.md) donne les valeurs à renseigner et distingue
préparation locale, rafraîchissement manuel J4 et lancement confirmé. L'ADR accepté, les migrations,
les normaliseurs, les plafonds et les preuves des commits précédents restent inchangés.

## Qualification

Commande : `mvnw.cmd clean verify`, dans le worktree du WO, sous Windows, Java 25.0.4 et
Docker Desktop. Les deux exécutions portent sur le même correctif applicatif ; seules les
preuves documentaires sont complétées après la seconde.

| Exécution | Résultat effectif |
|---|---|
| Première, fin à `2026-09-07T12:36:00Z`, durée 1 min 53 s | Échec conservé : 1 341 tests Surefire, 1 échec, 0 erreur, 5 ignorés. Failsafe non exécuté. |
| Seconde, après libération explicite du port par le propriétaire, fin à `2026-09-07T12:44:01Z`, durée 4 min 48 s | `BUILD SUCCESS`, code 0 : 1 341 tests Surefire, 0 échec, 0 erreur, 5 ignorés ; 122 tests Failsafe, 0 échec, 0 erreur, 0 ignoré. |

L'échec initial concerne uniquement
`J6NativeBinaryPipelineQualificationTest.syntheticNativePipelineFailsClosedWithoutHumanPassphraseInput`,
avec `LOOPBACK_APPLICATION_LISTENER_RESIDUAL`. Une vérification du poste confirme alors le
listener Java de l'opérateur sur `127.0.0.1:8087`. Aucun arrêt de ce processus n'est exécuté
par l'agent. Le propriétaire répond « Je libère le port 8087 maintenant » ; l'absence de listener
est vérifiée avant la nouvelle commande. Le test auparavant bloqué passe dans cette seconde suite.

Les rapports XML finaux sont lus : 178 suites Surefire et six suites Failsafe. Les cinq ignorés
sont quatre cas de liens symboliques dépendant des capacités Windows et la qualification J6
Docker optionnelle de trois exécutions. Ils ne sont pas comptés comme réussites.
Failsafe exécute `FlywayMigrationIT` (69), `J7DeliveryLedgerMigrationIT` (29),
`J7DeliveryMutualTlsLoopbackIT` (4), `J7OptionalDeliveryEndToEndIT` (2),
`J7ProviderOwnerGoV32MigrationIT` (2) et `LiveCampaignPersistenceIT` (16).

| Couverture ciblée, incluse dans Surefire | Résultat |
|---|---|
| `LiveCampaignControllerTest` : codes connus, actions adaptées, exception arbitraire masquée, protections et exclusions précédentes | 36 tests, tous réussis |
| `LivePreparationAdmissionTest` : vraie admission, observation passée ou future `notstarted`, réseau désactivé, stockage non configuré, sélection mixte | 4 tests, tous réussis |
| `DockerLiveStorageCapacityProbeTest` : chemin absent, fichier absent ou répertoire, opt-in sans effet sur la configuration manquante | 2 tests, tous réussis |
| `LiveCampaignPropertiesTest` : liaison du YAML et des variables exactes, chemin avec espaces, défauts et chemin vide | 3 tests, tous réussis |

Les tests utilisent des données synthétiques et des bases isolées. Aucune persistance ni migration
n'est modifiée par ce correctif ; le cycle `-Pintegration-tests verify` n'est pas répété, les
122 intégrations étant déjà exécutées par les liaisons Failsafe effectives de `clean verify`.
La qualification Chromium dédiée n'est pas relancée : aucun script de rafraîchissement,
en-tête de sécurité ni transport n'est modifié ici. Ses preuves antérieures restent distinctes.

Empreintes SHA-256 des preuves locales, conservées dans le worktree et non ajoutées en brut à Git
(les XML dans `target/` sont remplacés lors d'un prochain `clean`) :

| Fichier | SHA-256 |
|---|---|
| `.tmp/wo058-storage-clean-verify.log`, échec initial | `761c9d90fc888885c8f8cf1bec07d2eafcc1ebfb385d450f5fbcd50a23364e5f` |
| `.tmp/wo058-storage-clean-verify-port-free.log`, réussite | `86461bdba1eaacbaef370c8eccc2592fd2c36c1c72deedd391705b8799cf8126` |
| `target/surefire-reports/TEST-com.bettingproject.sofascorelocal.adapter.web.LiveCampaignControllerTest.xml` | `6a86ec2214ab602d18e6aa37ea1b2fdc37b3422d380af65aa7d0122178c31a2e` |
| `target/surefire-reports/TEST-com.bettingproject.sofascorelocal.application.live.LivePreparationAdmissionTest.xml` | `1885ea0b6f620f88a262ca236d378d13445ba746f38a365f12cb2e39233d634a` |
| `target/surefire-reports/TEST-com.bettingproject.sofascorelocal.application.live.DockerLiveStorageCapacityProbeTest.xml` | `4f47bcf5de8ef7f970d8d133eeb3a65b67768b75eb5a5a2b1fb0de0c1b6e1453` |
| `target/surefire-reports/TEST-com.bettingproject.sofascorelocal.config.LiveCampaignPropertiesTest.xml` | `21628e3c40e5a3c8d27baf117281c89ff74c892aa3aba23d94f97e02ac32df9b` |

## Revue et remise en main

Le diff couvre le contrôleur et sa page d'erreur, deux liaisons de configuration, les tests,
le runbook, le changelog, ce rapport et le WO. Les textes UTF-8, liens locaux, espaces et
`git diff --check` sont contrôlés, y compris les fichiers nouveaux. Revue des secrets et
des valeurs réseau : aucun secret ajouté, adresse `127.0.0.1` conservée, fournisseur,
Playwright et live désactivés par défaut, polling historique désactivé. La revue indépendante
ne relève pas de blocage sur les messages, la liaison des paramètres ou les tests d'admission.

L'ADR-SS-005 administratif conserve l'empreinte
`48105be765fbe943e5153ff551ea5eaf5e3b79345c2b08c28d57581dee78ca51` et sa copie acceptée conserve
`48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e`.
ADR-SS-001 à 004, AGENTS.md, migrations et preuves antérieures ne changent pas.

Le WO revient à `READY_FOR_REVIEW`, sans clôture. Le commit local demandé est la dernière
action avant remise en main ; aucune publication ni campagne réelle n'est effectuée.
Pour reprendre, intégrer ce commit dans le checkout Eclipse, renseigner le chemin Docker
vérifié plus haut et le nom de conteneur du runbook, puis redémarrer l'application et recharger
`/events`. La préparation d'une rencontre éligible doit afficher le manifeste si les contrôles
locaux réussissent. Un refus conserve désormais sa cause précise. L'essai opérateur d'un match
réellement futur et la qualification fournisseur des cycles restent à effectuer.
