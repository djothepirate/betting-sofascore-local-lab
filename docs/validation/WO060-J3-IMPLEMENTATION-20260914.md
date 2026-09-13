# WO-060 — Réalisation et qualification locale J3

Date : **14 septembre 2026**. Statut de cette preuve : **QUALIFIED_LOCAL**.
Statuts du Lab : `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED`, `NO_CRITICAL_DEPENDENCY`.

## 1. Périmètre et provenance

- Instruction propriétaire : « Commencer l’implémentation du WO-060 », après le cadrage documentaire.
- [Work Order](../work_orders/active/WO-SS-20260913-060-j3-automation-and-durable-catalog.md) et
  [ADR-SS-007 v0.2](../../ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md).
- Branche : `feature/V0.1.0-RC01-CODEX-WO-SS-20260913-060`.
- Train cible : `feature/V0.1.0-RC01`. Base : `d4c3d8ceeb6c46442f0792d436a3df7e2630f599`.
- Cadrage conservé au commit `9e0d1cb82556f40e5f76a35cb771018c052e9b5b`.
- Worktree isolé : `.tmp/wo060-j3-automation-design`, sous la racine Codex du Lab.
- Windows, Java **25.0.4**, Spring Boot **4.1.0**, Maven wrapper, version **0.1.0-rc.1-SNAPSHOT**.
- PostgreSQL **18.4-alpine**, bases Testcontainers jetables. Aucun contact avec la base opérateur,
  aucun appel SofaScore, aucun secret ou état de navigateur transféré/conservé.

La preuve de cadrage est historique ; ses résultats ne qualifient pas cette réalisation.
Le [guide J3](../runbooks/J3-AUTOMATION-AND-DURABLE-CATALOG.md) décrit le fonctionnement livré.

## 2. Résultat réalisé

| Étape | Réalisation |
|---|---|
| R0 | Exception J3 adoptée dans ADR-SS-001/005/006/007 et AGENTS ; aucune extension d'endpoint |
| R1 | V55 : collections/pages/projection/source, dernier succès par date, reprise J8 prouvée et sources brutes protégées |
| R2 | Actions A/B directes ; anciens POST à 410 ; suivi d'ordre ; pagination exacte ; découverte liée à collecte/date/tournoi |
| R3 | V56 : préférence initialement active, modes quotidiens et horaires explicites ; leader et claims durables ; reprise sans retry ; moteur commun |
| R4 | V57, live-v11, protocole worker 10 ; pause de toutes les familles sous le même garde ; contexte J3 isolé ; reprise par J4 |
| R5 | J6 V57, dix tables/empreinte, restauration native isolée ; suite de tests et documentation opérateur |

Le runtime commence après disponibilité complète du **serveur Web local**. Un contexte de
maintenance sans serveur ne prend pas le rôle d'ordonnanceur. L'installation J0/J1 non configurée
reste sans transport, même si la préférence quotidienne est initialement activée en PostgreSQL.

La fermeture du contexte fournisseur précède le terminal de collecte. La publication du catalogue,
du résultat J8 et de l'ordre utilise une transaction commune ; une perte de réponse après commit
conduit à la relecture du terminal, sans répétition de la collecte. Un échec, une annulation ou
une interruption ne remplace pas un succès précédent.

## 3. Commandes et résultats effectifs

Les journaux complets restent dans `.tmp` du worktree. Les compteurs finaux et empreintes de
sources sont conservés dans le [manifeste de vérification](WO060-J3-IMPLEMENTATION-RESULTS-20260914.json).

| Contrôle | Résultat |
|---|---|
| `mvnw.cmd clean verify` | PASS — 2 364 Surefire, 0 échec/erreur, 5 exclusions ; 276 Failsafe, 0 échec/erreur/exclusion ; 13 min 25 s |
| `mvnw.cmd -Pintegration-tests verify` | PASS — 2 364 Surefire, 0 échec/erreur, 5 exclusions ; 276 Failsafe, 0 échec/erreur/exclusion ; 13 min 37 s |
| Qualification native worker/protocole 10 | PASS — boucle locale uniquement ; preuve ci-dessous |
| Scripts J6 + lecteur V11 : analyse syntaxique PowerShell | PASS — trois scripts |
| Lecteur V11 en sortie PowerShell | PASS — dix paramètres affichés ; aucun environnement/lanceur modifié |
| UTF-8 strict, `git diff --check`, binding loopback et revue de secrets | PASS — fichiers de la réalisation contrôlés ; aucun ancien script de migration, `.env` ou PDF de référence modifié |

Contrôles ciblés exécutés pendant la réalisation :

- `J3CollectionPersistenceIT`, `J3AutomationPersistenceIT`, `J3CollectionExecutionIT`,
  `J3RuntimeIT` : PostgreSQL réel et réponses synthétiques.
- Tests MVC `J3AutomationControllerTest`, `J3CollectionControllerTest`, `DashboardControllerTest`,
  `ManualCallControllerTest`, `TournamentEventDiscoveryControllerTest`.
- `LiveCampaignServiceTest` : échange J4 en vol, chacune des trois familles J5 en vol,
  reprise et arrêts concurrents ; un seul propriétaire et une seule ouverture de campagne.
- `GroupedLiveAdmissionPolicyV11Test`, `GroupedLiveScheduleV11Test` et tests du superviseur/protocole.
- `LiveCampaignPersistenceIT` : upgrade V54/V57, exclusion SQL, transitions, garde conservé,
  empreinte J3 et roundtrip `pg_dump`/`pg_restore` isolé.

Les cinq exclusions Surefire sont explicites : quatre cas de liens symboliques/reparse points
selon la capacité de l'environnement Windows, et la qualification native J6 de trois campagnes
à paramètres effectifs, réservée à son mode explicite. Elles ne sont pas comptées comme des
réussites. Le roundtrip J3 natif sur PostgreSQL jetable est exécuté séparément et n'est pas exclu.

## 4. Traçabilité des critères

Les scénarios sont composés de tests de domaine, d'ordres, de stockage, de Web et de worker :
ils ne prétendent pas reproduire toutes les combinaisons de panne dans Chromium.

| Critères | Sources de preuve | Observations vérifiées |
|---|---|---|
| AC01–AC05 | J3RuntimeIT, J3AutomationPersistenceIT, J3CollectionExecutionIT, J3CollectionPersistenceIT | Activation initiale, restart, import sans réseau, succès cache, conservation A→B→A |
| AC06 | J3CollectionPersistenceIT, J3CollectionExecutionIT | Échec/rollback sans effacement du dernier succès ; publication J8/catalogue/ordre cohérente |
| AC07 | J3CollectionControllerTest | Page ouverte attachée à l'ancien ID ; lien explicite vers le succès récent ; navigation stable |
| AC08 | J3CollectionExecutionIT | Catalogue vide terminal accepté ; succès durable sans faux tournoi |
| AC09–AC10 | J3AutomationControllerTest, ManualCallControllerTest, J3WebApplicationIT | Clic/import directs, validation du lot, ancien parcours retiré, rendu réel Thymeleaf et moteur Web/SQL |
| AC11 | J3CollectionExecutionIT, corpus J3 et tests MVC | Imports 27/35 ; page 35 avec suite arrêtée sans page 36 ; noms/continuité/structure bornés |
| AC12–AC15 | J3AutomationPersistenceIT, J3RuntimeIT | Horaires explicites après succès ; révisions/annulation ; mode fixe ; indisponibilité/désactivation persistante |
| AC16 | J3CollectionExecutionIT | Désactivation pendant l'échange : page courante conservée puis aucune page suivante |
| AC17 | J3AutomationPersistenceIT, J3AutomationControllerTest | Collision de claims, leader, double clic, priorité de l'horaire explicite et relecture du succès avant admission |
| AC18 | J3AutomationPersistenceIT, J3RuntimeIT, J3CollectionExecutionIT | Pas de retry quotidien ; propriétaire disparu ; relecture d'un commit réussi sans refaire le travail |
| AC19 | J3AutomationDataTest, J3AutomationPersistenceIT, watchdogs du runtime | Fuseau Paris, heure inexistante/double, clés quotidiennes stables, date cible figée |
| AC20 | LiveCampaignServiceTest, worker natif | Owner simulé J4/INCIDENTS/STATISTICS/LINEUPS en vol ; J3 attend la publication ; protocole natif J4→J3→J4 |
| AC21 | Worker natif, GroupedLiveScheduleV11Test, LiveCampaignPersistenceIT | Même worker/contexte live, fermeture J3 vérifiée, nouveau groupe J4, publication des transitions |
| AC22 | GroupedLiveAdmissionPolicyV11Test, GroupedLiveScheduleV11Test, résilience partagée | Replay V11 à huit rencontres, 35/60 s et 2 100/h, pause de 20 minutes, slots manqués, échéance conservée |
| AC23–AC24 | LiveCampaignServiceTest, tests superviseur/protocole, GroupedLiveScheduleV11Test | Refus, publication/nettoyage incertains, arrêt opérateur ; aucun événement arrêté repris |
| AC25 | J3RuntimeIT, J3CollectionExecutionIT | Import autonome sans accès au coordinateur, au transport ou au cache |
| AC26 | J3CollectionPersistenceIT | Upgrade prérempli, reprise cache sans inventer l'heure absente ; succès attesté non récupérable |
| AC27 | J3CollectionPersistenceIT, LiveCampaignPersistenceIT | Course réelle publication/rétention ; dernier succès protégé ; restauration des dix tables et empreinte identique |
| AC28 | Tests MVC J3 et découverte | Host/Origin et jeton locaux ; date/ID exacts ; échappement des noms ; GET sans collecte |
| AC29 | J3RuntimeIT, J3WebApplicationIT, configuration Maven | Maintenance inerte, fournisseur désactivé, tests standards sans navigateur automatique |
| AC30 | J3CollectionPersistenceIT, J3CollectionExecutionIT, FlywayMigrationIT | Schéma neuf/prérempli, contraintes, rollback et perte de réponse de commit |

## 5. Qualification Chromium et profil V11

Commande explicite, distincte des tests standards :

```powershell
.\mvnw.cmd -q '-Pprovider-playwright-runtime,provider-playwright-local-qualification' '-Dtest=J3WorkerScopeProtocolTest' '-Dit.test=J3LivePauseWorkerQualificationIT' package failsafe:integration-test@provider-playwright-loopback-qualification failsafe:verify@provider-playwright-loopback-qualification
```

La [preuve native](WO060-J3-LIVE-WORKER-20260914.json) conserve le résultat PASS, les bornes
mesurées, l'empreinte du worker et les assertions d'isolation. Un worker enfant et quatre
requêtes loopback exécutent J4 → deux pages J3 → J4. L'origine hors périmètre reçoit zéro appel ;
la date étrangère, le mauvais endpoint, la page répétée, le groupe abandonné et la sous-opération
fermée sont refusés. Aucun cookie ne passe entre les contextes. Tous les processus descendants
sont terminés ; aucun profil/cookie/HAR/trace/capture/téléchargement n'est conservé.

Sur l'état final, la pause jusqu'au J4 repris mesure **9 290 ms** ; les trois intervalles
entre départs reçus sont **3 164, 3 093 et 3 094 ms**. Les octets des trois fichiers de preuve
JSON sont préservés par Git pour que leurs empreintes restent identiques après checkout.

Le [profil V11 indépendant](WO060-LIVE-V11-PROFILE-20260914.json) lie les classes réellement
compilées. Les seize scénarios de replay valident huit rencontres avec les enveloppes explicites
du profil ; neuf sont refusées. La pause de 20 minutes pour une ou huit rencontres conserve
l'échéance et repart par des J4 déphasés, sans rejouer les J5 abandonnés.

**Limite de mesure :** les enveloppes sont des hypothèses locales de replay. La mesure native
qualifie les transitions et l'isolation, pas la latence, les quotas ou une capacité soutenue
de SofaScore. Elle ne constitue aucune nouvelle campagne fournisseur autorisée.

## 6. Restauration J6 et conservation

V57 est requis par les scripts. L'empreinte `j3LedgerSha256` couvre toutes les colonnes/lignes
des dix tables J3, dans un ordre canonique ; les dix compteurs et `activeJ3Count` sont comparés.
La preuve exécutée dans Testcontainers restaure des succès A/B/A, une tentative échouée,
les préférences désactivées à heure fixe, un horaire futur et six transitions de pause.
Les catalogues exacts, la pagination, les hashes bruts et le schéma restent identiques.
Aucun horaire n'est lancé dans la base restaurée.

La publication et la purge acquièrent le même verrou transactionnel ; les contraintes SQL
protègent les pages du dernier succès. Le test de course laisse une publication non committée,
constate que la purge attend le verrou, puis vérifie son refus sans purge ni audit de suppression.
Le roundtrip ne contacte aucune base ni volume opérateur.

## 7. Corrections révélées par les contrôles

- La première adaptation modifiait le bytecode commun `LiveSchedule`, invalidant deux preuves
  V8/V9. `LiveSessionSchedule` isole désormais V11 ; les empreintes historiques restent exactes.
  Les trois tests qui inspectaient par réflexion l'ancien type de session sont adaptés à la façade.
- La sélection et l'admission V11 utilisent leur propre configuration/replay ; aucun repli
  silencieux vers un profil V10 n'est conservé.
- Le démarrage de maintenance publie lui aussi `ApplicationReadyEvent` : le runtime vérifie
  maintenant la présence effective du contexte Web loopback avant de prendre le rôle d'ordonnanceur.
- Le libellé de preuve du script J6 est aligné avec l'assertion de schéma courant V57 et ses champs J3.
- Le test du contexte Web complet révélait un menu masqué lorsque la découverte était désactivée.
  La liste J3 est maintenant toujours consultable ; seule la préparation de découverte garde ses
  conditions d'activation. Import, menu, pagination et preuve passent dans le même contexte Web,
  avec PostgreSQL réel et fournisseur désactivé.
- Les horaires quotidiens fixes manqués pendant plusieurs jours sont matérialisés en lots bornés ;
  la correction n'introduit aucun rattrapage.

## 8. Limites et livraison

La recette fournisseur, l'inventaire des sources historiques réelles et l'application des réglages
dans Eclipse restent des opérations opérateur distinctes. Les tests de Web utilisent MockMvc avec
rendu Thymeleaf ; ils ne constituent pas une recette interactive dans le navigateur de l'opérateur.
La qualification Chromium porte sur le worker local.

Un ancien succès sans source exacte ne peut pas être reconstruit. Il reste attesté et évite le
doublon opportuniste, avec indisponibilité visible. Une pause interrompue par perte du processus
conserve sa dernière phase observée ; elle ne prouve pas un nettoyage et n'autorise aucune reprise
automatique du live. Les gardes et l'ordre interrompu empêchent d'inventer un résultat terminal sûr.

Les fichiers touchés et leurs empreintes utiles figurent dans le manifeste de vérification.
Le WO reste actif jusqu'à revue humaine et fusion de sa PR vers le train ; ce rapport ne vaut ni
fusion, ni déploiement, ni activation d'une campagne fournisseur.
