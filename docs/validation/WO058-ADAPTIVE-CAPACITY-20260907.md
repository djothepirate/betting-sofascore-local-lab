# WO-058 — Plafond paramétrable et cadence selon les rencontres retenues

Statuts : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.
Date : 7 septembre 2026. Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260907-058`.
Base du correctif : `a7f544cc2f70db2066836107d1e7a21c4feccb4c`.
Worktree : `.tmp/wo058-live-j4-j5`, distinct du checkout Eclipse.
Qualification effectuée avec des modifications locales non encore committées ; le commit de
remise contient ce rapport et les empreintes du code dans la preuve JSON.

## Décision opérateur et défaut reproduit

Le réglage `SOFASCORE_LIVE_QUALIFIED_MATCH_CAPACITY=5` entraînait `LIVE_POLICY_INVALID`
dès la validation de la configuration, avant le décompte de la sélection. Une seule cible
était donc refusée. Le domaine, l'ordonnanceur et les contraintes V33 contenaient aussi une
limite fixe de trois. Modifier seulement le paramètre ne pouvait pas ouvrir le parcours.

Le propriétaire précise que le paramètre désigne le **maximum de rencontres éligibles par
campagne**, demande explicitement les valeurs 10 et 25, et choisit une cadence selon le nombre
retenu : 60 s pour 1–3, 90 s pour 4, 120 s pour 5. La progression est prolongée au-delà de cinq
par `D = max(60, 30 × (N − 1))` secondes. Cette extension est documentée dans
[ADR-SS-005 v0.2](../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md) ; l'acceptation et les
octets de la v0.1 restent conservés séparément.

## Comportement du correctif

| Cibles éligibles retenues N | Cadence D | Charge maximale modélisée par cycle, profil 1 s / 1 s |
|---|---|---|
| 1 | 60 s | 20 s |
| 2 | 60 s | 40 s |
| 3 | 60 s | 60 s |
| 4 | 90 s | 80 s |
| 5 | 120 s | 100 s |
| 10 | 270 s — 4 min 30 s | 200 s |
| 25 | 720 s — 12 min | 500 s |

La charge inclut un J4 et les trois familles J5 par cible, une enveloppe de requête de 1 s,
une enveloppe de traitement de 1 s et les trois secondes de délai après chaque échange.
Le plafond à 25 avec trois cibles donne D = 60 s. Une valeur élevée du plafond ne rend plus
une petite sélection invalide. La limite technique préexistante de 100 identifiants dans un
formulaire, exclus compris, reste applicable ; les mesures natives de ce lot vont jusqu'à 25.

Les `finished` sont exclus avant le calcul ; une sélection entièrement terminée produit
l'explication sans campagne. J4 affiche le plafond et le compteur éligible. À la limite, les
autres cases éligibles sont désactivées ; décocher une cible libère une place. Les contrôles
serveur restent obligatoires pour les formulaires anciens ou forgés. Le verrou des rencontres
déjà suivies, l'exception `STOPPED_ERROR`, la protection des révisions et le focus sont conservés.

Les nouveaux manifestes `live-v2` incluent D dans leur empreinte et leur stockage immuable.
L'attente J4, les cycles J5, la surveillance de fin, le délai minimal par famille et les seuils
de retard utilisent D ; le secours J4 est à `max(300 s, D)`. Une cible devenue `finished` entre
préparation et lancement est exclue du transport, sans recalculer la cadence consentie.
L'écran continue ses lectures locales toutes les cinq secondes.

Le correctif antérieur des trois familles J5 HTTP 404 reste effectif : snapshot brut
`ENDPOINT_UNAVAILABLE`, résultat explicite `HTTP_404`, poursuite des autres familles et prochaine
interrogation au cycle normal. Les échecs de stockage, de session ou de transport conservent
leur traitement distinct. Aucun nouveau retry immédiat ou endpoint n'est ajouté.

## PostgreSQL et compatibilité des preuves

[V34](../../src/main/resources/db/migration/V34__live_configurable_selection_and_cadence.sql)
est append-only. Elle élargit le plafond enregistré, permet les ordres de sélection jusqu'à 99,
ajoute `cycle_interval_seconds` et vérifie la formule des nouveaux `live-v2`. La garde existante
`protect_live_manifest()` protège aussi cette nouvelle colonne. Les anciens manifestes
`live-v1` reçoivent le défaut 60 s ; leurs empreintes, compteurs, cibles, réceptions et données
normalisées ne sont pas réécrits.

Les tests PostgreSQL couvrent une base neuve, un upgrade V33 prérempli, les empreintes des
observations avant/après, les reprises Flyway sans changement, l'immuabilité et la persistance
de campagnes à 4, 5, 10 et 25 cibles. Les contrôles J6 de sauvegarde et de rétention suivent
désormais la version courante V34 ; une preuve déclarant une autre version reste refusée.

Preuves historiques conservées à l'identique :

| Fichier | SHA-256 |
|---|---|
| Proposition ADR-SS-005 v0.1 acceptée | `48004b4240138bcc430db0286113fee197a521c8e3548d7674ed410c25348f2e` |
| Profil initial à deux/trois rencontres | `0f1ae6ad44190ae965bec3ad670ffb3c45a140a29856d217f28e71b5db262679` |
| Migration V33 | `fde6252c701a6c12f600567d835aee5f45cab8fff7923321c88af3582dce0c14` |

## Vérifications exécutées

Environnement : Windows, Java 25.0.4, Spring Boot 4.1.0, Maven wrapper, PostgreSQL 18.4
dans Testcontainers. Le PID 37724 a été arrêté après autorisation explicite du propriétaire ;
le port 8087 a été vérifié libre avant les contrôles J6. Le Lab n'a pas été relancé.

| Commande | Résultat |
|---|---|
| `mvnw.cmd clean verify -Pintegration-tests` | **PASS**, fin `2026-09-07T16:20:27Z`, durée 5 min 13 s ; 1 424 tests Surefire, 0 échec, 0 erreur, 5 ignorés ; 131 tests Failsafe, 0 échec, 0 erreur, 0 ignoré. |
| `mvnw.cmd -Dtest=LiveMultiMatchCapacityTest test` | **PASS**, fin `2026-09-07T16:21:29Z` ; 13 tests, sans échec, erreur ou ignoré. Requalification du modèle de quatre heures avec le profil commun 1 s / 1 s pour tous les nombres retenus. |
| `scripts/Invoke-LivePlaywrightLoopbackQualification.ps1` | **PASS**, fin Maven `2026-09-07T16:44:58Z` ; 12 tests Chromium, 0 échec, 0 erreur, 0 ignoré ; marqueurs `WO058_LIVE_LOOPBACK=PASS` et `SOFASCORE_NETWORK_CALLS_EXECUTED=NO`. |

Les cinq ignorés standards sont les quatre essais de liens symboliques Windows dans
`LocalJ7ExportFileStoreTest` et `J8BenchmarkExportCommandTest` (fonction indisponible dans cet
environnement), et le scénario optionnel `j6.docker.qualification` non activé. Ils ne sont pas
comptés comme des contrôles réussis. Les autres tests natifs J6 requis et les tests PostgreSQL
effectifs ont été exécutés. Les XML standards ont été archivés dans
`.tmp/wo058-adaptive-full-reports` avant le packaging de qualification Chromium.

La vérification ciblée de quatre heures utilise une horloge virtuelle, des réponses et temps
simulés, toutes les familles, la surveillance de fin et un dernier cycle complet pour chaque
cible. Elle vérifie l'espacement D, les triplets contigus, zéro cycle manqué et les plafonds
1 000/3 000. Les réponses de dix secondes incompatibles avec D sont refusées à l'admission.
Les tests d'attente et d'indisponibilité vérifient 60, 90, 120, 270 et 720 secondes.

### Traçabilité des essais intermédiaires

- La reproduction rouge avant correction refusait une, deux et trois cibles avec un plafond
  de cinq : `LIVE_POLICY_INVALID`. L'échec de résolution Maven dans le bac à sable a été
  distingué de cette reproduction obtenue avec les dépendances accessibles.
- Un premier essai ciblé attendait J5 avant un secours J4 déjà dû au cycle de 720 s ; l'assertion
  a été corrigée pour respecter l'ordre chronologique attendu, sans modifier l'ordonnanceur.
- Deux exécutions complètes intermédiaires ont échoué sur trois puis deux assertions J6 qui
  attendaient V33. Les 25 tests du ledger live passaient déjà. Les gardes et leurs assertions
  ont été alignées sur V34 ; seul le dernier `BUILD SUCCESS` vaut validation complète.
- L'ultime ajustement du test de charge remplace ses anciennes enveloppes 2/3 par le profil
  commun 1 s. Ses 13 cas ont été réexécutés après la suite complète ; aucun code de production
  n'a changé entre ces deux vérifications.

## Profil de qualification et mesures natives

La [preuve JSON figée](WO058-ADAPTIVE-CAPACITY-PROFILE-20260907.json) porte les mesures, les
empreintes des sources en UTF-8/LF, celles des journaux locaux et des XML natifs, ainsi que les
limites de qualification. Elle est protégée des conversions de fins de ligne par `.gitattributes`.

SHA-256 à renseigner dans `SOFASCORE_LIVE_QUALIFICATION_SHA256` :

```text
99bffa13e057a07fd9493d26e8a186ffa25b2262f03f652da2869347850fceea
```

| N | Cycles natifs | Réponses de 5 Mio | Échange maximal | Normalisation maximale | Cycle complet maximal | D |
|---|---|---|---|---|---|---|
| 2 | 3 | 24 | 747,5 ms | 10,8 ms | 26,31 s | 60 s |
| 3 | 3 | 36 | 800,8 ms | 9,2 ms | 40,45 s | 60 s |
| 4 | 2 | 32 | 609,4 ms | 6,7 ms | 53,61 s | 90 s |
| 5 | 2 | 40 | 687,5 ms | 7,5 ms | 68,80 s | 120 s |
| 10 | 1 | 40 | 747,5 ms | 6,7 ms | 139,39 s | 270 s |
| 25 | 1 | 100 | 830,3 ms | 9,4 ms | 351,23 s | 720 s |

Les 272 réponses maximales de ces six scénarios sont synthétiques. Le même worker est conservé
dans chaque campagne ; les espacements d'au moins trois secondes, la fermeture de l'arbre de
processus, l'absence d'artefact interdit et zéro requête hors périmètre sont vérifiés. La variation
de mémoire entre début et fin reste inférieure à 256 Mio ; cette mesure ne représente pas un pic.

La suite native comprend aussi la continuité après HTTP 404 sur trois cycles, l'arrêt individuel
pendant le délai entre appels et les acquittements d'arrêt global. Les trois tests d'interface
vérifient les plafonds 5/10/25, les `finished` exclus du compteur, le verrou actif/exception
`STOPPED_ERROR`, les révisions, la conservation de sélection/focus et les POST à origine exacte
sur `localhost:8087` et `127.0.0.1:8087`, sans utiliser le listener de l'opérateur.

**Portée précise :** dix et vingt-cinq cibles ont un passage natif complet à volume maximal.
Leur récurrence et leur finalisation sont vérifiées avec l'horloge virtuelle sur une fenêtre
de quatre heures ; aucune session Chromium de quatre heures n'est revendiquée. La mesure
native de traitement porte sur la normalisation. Les contrôles PostgreSQL sont distincts ; ce
lot ne mesure pas une campagne de quatre heures cumulant Chromium, persistance et fournisseur.

## Reprise opérateur et limites

Le [runbook live](../runbooks/LIVE-J4-J5-CAMPAIGNS.md) donne le réglage du plafond et les
paramètres du profil. Les variables de requête et traitement déjà montrées par l'opérateur
restent à `1000ms`. L'empreinte de la nouvelle preuve remplace celle du profil historique lors
de la préparation de nouvelles campagnes. Un manifeste préparé avec un ancien profil ne
change pas après modification du lanceur.

La base de l'opérateur n'a pas été migrée ou modifiée par ces tests. Pour passer une base V33
à V34, suivre la sauvegarde préalable avec l'outillage V33 au commit de base, la restauration
isolée de contrôle, puis le redémarrage sur le correctif et l'outillage V34. Cette distinction
est détaillée dans le [runbook J6](../runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md).

Les tests Chromium utilisent exclusivement HTTP loopback et des fixtures synthétiques ; ils
ne garantissent pas la latence SofaScore. Les enveloppes servent au calcul d'admission, le
timeout transport conserve sa limite de dix secondes, et les retards réels restent surveillés.
Les opt-ins, le contexte unique par lancement manuel, les trois secondes après échange,
l'écoute `127.0.0.1`, les budgets et les limites de stockage restent effectifs. Aucun payload
complet, cookie ou secret n'est ajouté au diff ; aucun appel SofaScore, push ou fusion n'est
effectué par cette qualification. La revue humaine et la clôture du WO restent distinctes.

## Inventaire des fichiers du correctif

Les fichiers ci-dessous constituent le diff par rapport au commit de base. Les scripts et
journaux de travail sous `.tmp` sont ignorés ; les rapports bruts XML restent locaux.

- `.gitattributes`
- `ADR-SS-001-experimentation-endpoints-sofascore-depuis-windows.md`
- `ADR-SS-005-bounded-local-live-j4-j5-campaigns.md`
- `AGENTS.md`
- `CHANGELOG.md`
- `docs/architecture/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/runbooks/J6-BACKUP-RESTORE-AND-RETENTION.md`
- `docs/runbooks/LIVE-J4-J5-CAMPAIGNS.md`
- `docs/validation/WO058-ADAPTIVE-CAPACITY-20260907.md`
- `docs/validation/WO058-ADAPTIVE-CAPACITY-PROFILE-20260907.json`
- `docs/work_orders/completed/WO-SS-20260907-058-bounded-live-j4-j5.md`
- `README.md`
- `scripts/Backup-Restore-J6.ps1`
- `scripts/Invoke-J6Retention.ps1`
- `scripts/Invoke-LivePlaywrightLoopbackQualification.ps1`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/persistence/live/JdbcLiveCampaignStore.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/EventExplorerController.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignController.java`
- `src/main/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentation.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveAdmissionPolicy.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveCampaignService.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveReplayRunner.java`
- `src/main/java/com/bettingproject/sofascorelocal/application/live/LiveSchedule.java`
- `src/main/java/com/bettingproject/sofascorelocal/config/LiveCampaignProperties.java`
- `src/main/java/com/bettingproject/sofascorelocal/domain/live/LiveCadence.java`
- `src/main/java/com/bettingproject/sofascorelocal/domain/live/LiveCampaignData.java`
- `src/main/resources/db/migration/V34__live_configurable_selection_and_cadence.sql`
- `src/main/resources/static/js/live-campaign.js`
- `src/main/resources/templates/events.html`
- `src/main/resources/templates/live-campaign.html`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignFormBrowserQualificationIT.java`
- `src/provider-playwright-qualification-test/java/com/bettingproject/sofascorelocal/application/network/playwright/LiveProviderSessionQualificationIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignControllerTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/adapter/web/LiveCampaignPresentationTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/LiveMultiMatchCapacityTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/LivePreparationAdmissionTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/application/live/LiveScheduleTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/build/J6NativeBinaryPipelineQualificationTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/config/LiveCampaignPropertiesTest.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/FlywayMigrationIT.java`
- `src/test/java/com/bettingproject/sofascorelocal/integration/LiveCampaignPersistenceIT.java`
