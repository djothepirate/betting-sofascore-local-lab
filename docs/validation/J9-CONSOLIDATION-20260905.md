# J9 — Synthèse consolidée au 5 septembre 2026 (WO-052)

## 1. Portée

Cette synthèse distingue réalisé, qualifié, validé par le propriétaire, clôturé, publié et fusionné. Elle actualise la lecture des synthèses anciennes sans réécrire rapports, manifestes ou ADR acceptés. Elle n'accorde aucune autorisation. Aucun état actuel de base, listener, certificat ou fichier privé n'est déduit d'un ancien postflight.

J9 a conclu PREPARE_OPTIONAL_INTEGRATION sous WO-018. Les travaux ultérieurement autorisés ont implémenté le push optionnel, qualifié le parcours synthétique puis un transfert réel local J7. Les deux implémentations sont fusionnées. Aucun enrichissement analytique ou fonctionnement de production n'est revendiqué.

## 2. Fusions vérifiées

Lecture GitHub du 5 septembre 2026 :

| Dépôt / PR | État | Commit de fusion |
|---|---|---|
| Lab #19 décision J9 | MERGED | `a2b2a2aa0798781fa3ea738897f485952f847826` |
| Lab #20 étude ADR | MERGED | `cb7c8efe72bd2ef4a5d0f9aab4ae72d4e75453bc` |
| Lab #21 sender | MERGED | `daf55bf76521f81893f86d04fde3c2903bf22362` |
| Lab #22 ports fournisseur | MERGED | `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089` |
| Lab #25 E2E synthétique | MERGED | `ad343d5f1ed131b9a766c60ffd0086dc354ee839` |
| Lab #26 CI/correctifs | MERGED | `400900410dfa751521ce387fbadcc4b5ca95a94a` |
| Lab #27 E2E réel/garde-fous | MERGED | `49d4308794411d41ece97cbd22fdce7fef377616` |
| Lab #28 WO-051 | MERGED | `dfe34b3ab6881823a8e8601764cb99a63727810b` |
| Betting Project #11 INT-001 | MERGED | `7f1f3aab430e046a1f5b18321bead22641f3a11f` |

Sources : [PR Lab fusionnées](https://github.com/djothepirate/betting-sofascore-local-lab/pulls?q=is%3Apr+is%3Amerged), [INT-001 #11](https://github.com/djothepirate/betting-project/pull/11).

Les main distants sont respectivement dfe34b3 et 7f1f3aa. Leurs CI **post-fusion** sont SUCCESS : [Lab 33962926377](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/33962926377), [Betting Project 33963363100](https://github.com/djothepirate/betting-project/actions/runs/33963363100). L'échec Linux [33961252984](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/33961252984) de 49d4308 reste historique ; WO-051 le corrige sans changer le runtime. Aucun PASS de CI WO-052 n'est inféré des bases.

Les checkouts humains inspectés sont propres et à ces commits, sur branches human. Les refs locales main d'anciens worktrees agents restent en retrait (Lab 4009004, Betting Project 5a8161e). Aucun checkout synchronisé ici ; tout prochain WO recontrôle sa base.

Betting Project docs/project-status.md à 7f1f3aa conserve PR_OPEN, photographie pré-fusion. GitHub prévaut pour l'état de livraison ; pas de modification de ce dépôt sous WO-052. Les anciennes mentions « sans fusion » du Lab décrivent l'autorité à leur date, pas une annulation des décisions ultérieures.

## 3. WO-019 : campagne arrêtée remplacée

Le [rapport WO-019](J9-PROVIDER-ROBUSTNESS-CAMPAIGN-20260831.md) reste STOPPED : 20 tentatives, aucune D2/D3. Les 2 967 ms entre timestamps pré-navigation ne prouvaient ni violation on-wire ni respect des trois secondes. Ce manque de preuve a arrêté la série ; ne pas le reformuler comme violation réseau établie.

Go consommé et terminé par arrêt. KEEP_LOCAL est une recommandation historique rejetée comme décision finale par le propriétaire. WO-021 qualifie le délai ; ADR-SS-002 v1.1 et WO-023 ouvrent une **nouvelle série**, sans réarmer WO-019.

[WO-023](J9-WO023-PROVIDER-ROBUSTNESS-CAMPAIGN-20260901.md) : 28/38 tentatives, 28 HTTP 200 compatibles, parsées, persistées, PASS. Cumul des deux séries 48 sous plafond d'audit 58 ; dix tentatives non utilisées non réutilisables. PASS ne signifie ni exhaustivité universelle ni fraîcheur continue ni valeur analytique mesurée.

[WO-019](../work_orders/active/WO-SS-20260831-019-j9-provider-robustness.md) reçoit une annotation STOPPED_SUPERSEDED_BY_WO023. Il demeure dans active seulement pour revue du classement administratif arrêté/remplacé ; aucune reprise ni validation rétroactive PASS. WO-018/023 restent clôturés, preuves inchangées.

## 4. Sort Git WO-032/033/034

Lecture branches locales, refs GitHub et 28 PR retournées, sans PR ayant l'une de ces trois branches en tête :

| WO J9 | Branche / HEAD exact | Publication | Intégration |
|---|---|---|---|
| 20260901-032 index | codex/j9-wo032-reference-index-refresh — `f347b97d34788c2a0cc67938e606d6d2931059e5` | Même branche/SHA GitHub ; aucune PR retrouvée | WO completed sur branche ; HEAD non ancêtre main, WO absent de main |
| 20260901-033 permission | codex/j9-wo033-provider-permission-request — `a45daf79d46cf6df5b76fefcb78c7e9ef0ebfdf9` | Aucune ref GitHub correspondante ni PR retrouvée | WO completed local ; HEAD non ancêtre main ; WO et deux livrables absents main |
| 20260901-034 rendu | codex/j9-wo034-permission-final-render — `1b571a687e420d27dcbdca86e0f8bf64eb38663a` | Aucune ref GitHub correspondante ni PR retrouvée | WO active local ; HEAD non ancêtre main, fichiers WO-034 absents main |

git rev-list --left-right --count dfe34b3...branche : 155/5 pour 032, 155/6 pour 033, 48/9 pour 034 (propres main / branche). Le merge de main **dans** WO-034 ne vaut pas fusion inverse. Aucun cherry-pick, merge ou import de scripts ici.

La PR #24 fusionnée à 579fa2545cc2cb8e7ce31afc15f0ec4fb3fd1571 concerne **WO-SS-20260902-032-maven-wrapper-extraction**, pas WO-SS-20260901-032-reference-index-refresh. Identifiants complets obligatoires, aucune renumérotation. WO-SS-20260901-031-ci-gitlab-local-only / PR #23 est hors J9. L'ancien WO fournisseur 031 est devenu 033 par décision propriétaire, commit cdb9c73.

Le manifeste expurgé WO-034 à 1b571a6 consigne le préflight **2026-09-01T23:25:29Z** : 9/30, 21 champs vides, renderer non exécuté, hashes du message réel NOT_COMPUTED. Le bloc 30/30 du WO est une condition requise, pas un résultat. Aucun fichier privé actuel lu : ces faits datés ne préjugent pas d'une saisie ultérieure non réconciliée. Les hashes synthétiques ne sont pas ceux d'un message réel. Demande non déclarée envoyée ou officiellement acceptée.

Suite : recontrôler et décider séparément la livraison documentaire 032/033 ; réconcilier la preuve privée expurgée de 034 avant rendu/revue/envoi. Aucune publication de ces branches n'est incluse dans WO-052.

## 5. Matrice J7 : acquis, résultats et limites

Références : [architecture J7](../architecture/J7-CANONICAL-EVENT-EXPORT.md), [runbook génération](../runbooks/J7-CANONICAL-EVENT-EXPORT.md), [ADR-SS-003 v0.2](../../ADR-SS-003-optional-integration-topology.md), [contrat receiver fusionné](https://github.com/djothepirate/betting-project/blob/7f1f3aab430e046a1f5b18321bead22641f3a11f/docs/contracts/j7-import-receiver-v1.md).

| Entrée / situation | Résultat / invariant | Preuve / limite |
|---|---|---|
| Observations normalisées | Racine manifest/data, schema ID urn:betting-project:sofascore-local-lab:j7:canonical-event-export:v1, version 1.0.0 ; UTF-8 déterministe, maximum 5 Mio | Contrat J7 ; aucun payload brut |
| Absence, indisponibilité, vide valide, partiel | MISSING / UNAVAILABLE / EMPTY_VALID et avertissements distincts ; pas de conversion de manquant en zéro | Architecture ; parsing ne prouve pas exactitude externe |
| Provenance | Identités canonique/fournisseur séparées ; ordre métier, snapshot, hash, parseur et heure conservés ; hashes sources/data/fichier distincts | Contrat ; historique distinct d'une réinterprétation |
| Génération seule | COHERENCE_CHECKED ; aucun POST | WO-035 ; pas de validation humaine implicite |
| Validation humaine | HUMAN_VALIDATED ; Livrer distinct, octets recontrôlés | WO-035 ; ni acquisition ni envoi automatique |
| Type des sources | SYNTHETIC_ONLY distinct de PROVIDER_DERIVED ; MIXED_OR_UNKNOWN refusé | WO-035/047/050 ; fixture ne prouve pas fournisseur |
| Premier import / duplication / collision | 201/IMPORTED ; 200/DUPLICATE ; 409 ; duplication conserve remoteImportId et receivedAt initiaux | INT-001, WO-036 R6, WO-038 ; aucun retry automatique |
| ACK positif | Inbox/outbox durables avant ACK ; ledger livraison séparé de validation | INT-001 ; enrichissement non réalisé par ACK |
| Résultat inconnu | Classification et réconciliation manuelle, pas de succès inventé | WO-038/051 ; preuve SSL locale stricte, refus pair peut apparaître IO_FAILURE |
| E2E synthétique | PASS_LOCAL_SYNTHETIC_E2E | WO-036 R6 ; corrections WO-037 à 044 sans relaxation de protocole |
| E2E réel local | Un POST, 201/IMPORTED, DELIVERED, 35 663 octets byte-identiques, audit/outbox durables | WO-046 R2 clôturé ; go consommé non réutilisable |
| Receiver | INT-001 clôturé/fusionné, opt-in ; V006–V008 ; inbox 30 jours par défaut ; purge bornée/tombstone, sauvegarde/restauration qualifiées | Local seulement ; aucun consommateur analytique livré |

Preuves gelées :

- [WO-023](J9-WO023-PROVIDER-ROBUSTNESS-CAMPAIGN-20260901.md) SHA-256 : `1c6a97f872d5621efcaffa505d16a7724f81816891aa0a9a990a0abec56e87c1`.
- [WO-046 R2](J9-WO046-J7-REAL-LOCAL-E2E-R2-CAMPAIGN-20260905.md) : `3b128cab84c384f5901215042d554e935730ce146565687677ddb1c2a39eddf1`.
- [WO-051](J9-WO051-MTLS-PEER-REJECTION-CI-QUALIFICATION-20260905.md) : `83468174d27cb5339690fcd3e947e495a228aa603e82f69816be4cf2be83d41f`.

Écart WO-046 reconnu : aperçu J7 et URL de session dans des retours navigateur, non copiés dans Git/preuves documentaires. PASS technique ne signifie pas sorties d'outil parfaitement expurgées. Aucun aperçu ou URL sensible reproduit ici.

## 6. Liste unique des portes actives

| Sujet | État / prochaine porte |
|---|---|
| Topologie | OPTIONAL_LOCAL_PUSH ; Lab 127.0.0.1:8087, receiver HTTPS 127.0.0.1:8444, PostgreSQL respectifs 5432/5433 ; disponibilité instantanée non vérifiée ici |
| Permission | NOT_EVIDENCED reste audit ; v0.2 non bloquant à lui seul pour transfert local V2, sans permission fournisseur créée |
| Incompatibilité | EVIDENCED_INCOMPATIBLE bloquant ; V1/V31 legacy strict inchangé |
| Nouveau POST réel | Non autorisé ; nouveau cadrage, export HUMAN_VALIDATED, manifeste/commits/hashes, identité mTLS exacte et go canonique borné à usage unique requis ; aucun reliquat WO-046 |
| Fournisseur J3/J4/J5 | Non autorisé actuellement ; ADR-SS-001 et portes de campagnes indépendantes inchangées ; livraison ne déclenche aucune acquisition |
| WO-034 | Réconciliation privée expurgée, rendu exact, revue puis décision d'envoi distincte ; aucun renderer exécuté ici |
| Enrichissement | Traitement versionné/outbox futur à cadrer dans Betting Project ; bénéfice analytique non mesuré |
| Exploitation | Pas de SLA, polling/live/scheduler, retry automatique ou dépendance critique ; coût durable et disponibilité non démontrés |
| Distant/VPS/production | Non autorisés ; WO infrastructure/gouvernance séparés, aucune extrapolation Windows ; séparation rôles PostgreSQL owner/runtime à qualifier avant production |
| Documentation | Revue/clôture WO-052, classement arrêté de WO-019 et livraison 032/033/034 à décider séparément |

EXPERIMENTAL, LOCAL_ONLY, NOT_PRODUCTION_APPROVED, NO_CRITICAL_DEPENDENCY inchangés. Cette liste est un index des décisions, jamais un manifeste ou un owner-go.

## 7. Qualification propre à WO-052

Résultat : `PASS_DOCUMENTARY_CONSOLIDATION_LOCAL_BOUNDED`, soumis à revue propriétaire, pas clôturé ni publié.

Commande : `mvnw.cmd clean verify --offline -DskipITs -Dsurefire.excludes=**/J6NativeBinaryPipelineQualificationTest.java,**/ChildJvmPlaywrightProviderSupervisorTest.java`, avec propriété maven.repo.local résolue vers le cache existant (chemin personnel non reproduit). Java 25.0.4. Premier essai sandbox : échec de compilation sur spring-orm 7.0.8 à 13:06:28Z, avant tests, cause native détaillée non établie. Réexécution hôte autorisée de la même commande : BUILD SUCCESS, 02:07 min, fin 13:09:16Z / 15:09:16 Europe/Paris. Aucun changement runtime/cache entre les essais.

XML Surefire : **1149 tests, 0 échec, 0 erreur, 4 ignorés**. Failsafe skipped, aucun rapport Failsafe généré ; J6NativeBinaryPipelineQualificationTest et ChildJvmPlaywrightProviderSupervisorTest exclus. Ce résultat ne remplace pas une qualification intégrale, PostgreSQL ou E2E.

Contrôles propres aux cinq documents : git diff --check, décodage UTF-8 strict sans NUL, liens locaux des nouveaux documents, convention de branche et neuf familles de motifs secrets PASS ; revue manuelle sans PII/payload/URL de session. Scanner CI sur tout l'historique non exécuté. Les SHA-256 des trois preuves ci-dessus concordent ; aucun changement des sources, scripts, pom, CI, ADR, configurations ou références. Le corps historique WO-019 est identique après retrait de la seule annotation ajoutée.

Les skills ss-work-order, ss-data-contract-replay et ss-review-closeout ont guidé la séparation des états Git, la matrice entrée/résultat/preuve et la préservation des documents gelés. Aucun résultat de campagne rejoué ni autorisation nouvelle déduite de cette consolidation.
