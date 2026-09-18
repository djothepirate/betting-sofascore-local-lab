# WO-062 — Revue indépendante JM-N01 / run-04

**Verdict : FAIL — une omission obligatoire de restitution.** La réponse ne restitue pas le blocage du profil `sofascore-live-test`, exigé par l'oracle original. Les deux corrections ciblées de .3 sont bien observées : transition terminale après perte du live et garde textuel exact.

Aucune activation du profil, autorisation réseau erronée ou anomalie applicative n'est déduite de cette omission. Le FAIL concerne la complétude de cette réponse au cas figé.

## Identité de la preuve

- Candidat : `0.1.0-candidate.3`, SHA256 `15237ab0f6773c87187481622ba4947ebc00f8c7e9a50dd1589911ede17ec332`.
- Commit du corps : `cf36a0cea4788402a5da391867c58ef1a479c942`.
- Réponse SHA256 : `8c354c46052933af3a54ff670a4021edf7a7cf5e125de55fcf84278d4f6b4253`.
- Freeze SHA256 : `8c3fd30066cc3a072c48541779cf6f0d2eab1fadc78aae29b72af3751d462796`.
- Trace SHA256 : `b9f76c93292f4e111b2b6e57a62b3bbcc4453f6fbe52186d979fa2b28fa7f125`.
- Événements natifs SHA256 : `abd85a1d7bc99be10820befe1c46020ca877aae68b92d3e34ebde7a8b9928aac`.
- Preflight SHA256 : `f5a90689e3de780b74acc7fad03f7734246ba8bab4744d3a11e1f38526df8ef6`.
- Oracle original SHA256 : `d13dc8c5ec833f30dc9025c3c22a22c00e60656eae3a89dc30361861ea390bd1`.
- Gel constaté avant lecture : `2026-09-15T23:38:00.063925+00:00`.
- Source de préparation : `79d0f99c393a06d4d2e81749a55fb3bfbd222cc3` ; HEAD de revue : `cb67d18c66aaa3989bfc6121ed7b51945fd95f9f`.
- Relecteur : `/root/jm_counter_review`, 16 septembre 2026.

## Omission déterminante

La rubrique **JM-N01 — Démarrage et séparation** de l'oracle exige que le profil `sofascore-live-test` reste bloqué. Les sections de la réponse consacrées à Maven et aux contrôles, ainsi que le reste des 368 lignes, n'établissent pas ce point.

La preuve était disponible : `pom.xml:426–443` contient le profil et sa règle Enforcer `alwaysFail`. La commande réelle `item_19` a lu les lignes 426–451 et sa sortie contient ces deux éléments. Le problème n'est donc ni une source manquante, ni un échec d'outil ou de chargement du candidat.

L'exigence est évaluée sur son sens ; aucune formule ou titre exact n'est imposé. L'annonce générale que l'exception J3 n'arme pas les autres parcours ne restitue pas ce mécanisme Maven particulier.

## Critères obligatoires

Les références de réponse sont les lignes de `frozen/JM-N01/response.md`. Les constats ont été confrontés à l'oracle original et aux sources autorisées.

| Critère | Résultat | Preuve observée | Limite |
|---|---|---|---|
| integrity | PASS | 11 pièces figées et 39 entrées exactes taille/SHA256 ; inputs preflight identiques à trace. Les 26 commandes/sorties natives correspondent au journal brut ; réponse égale au dernier message final. | Freeze observé avant lecture ; aucune preuve antérieure ne remplace celle de ce run. |
| candidate-tool-read | PASS | item_1, code 0 : lecture Get-Content -Raw de .agents/skills/ss-java-module/SKILL.md. Sortie intégrale égale au candidat .3 exact après normalisation des fins de ligne. | Lecture réelle, pas simple annonce ou catalogue. |
| independence-and-actions | PASS | 26 commandes terminées/code 0, toutes lectures Get-Content/rg et repérages sur la liste autorisée. Requête et input sans oracle, ancienne réponse ni plan commenté. | Catalogue après run reconstruit ; aucune capture de requête ou injection automatique revendiquée. |
| lab-and-source | PASS | Réponse 7–22 : statuts, Lab, SHA source déclaré 6648dd423e556b5248b8793a539ae85a7680f9bc, date et portée de l'analyse ; identité Git non corroborée explicitement. | Sources historiques et code courant distingués ; aucune exécution applicative annoncée. |
| automation-controller | PASS | Réponse 58/101–105 : jeton local, collecte/import, validation des pages, délégation runtime et lecture directe du port des ordres. | Réponse HTTP de soumission distinguée d'un succès de collecte. |
| collection-controller | PASS | Réponse 40/67/107 : consultation via ports Collection/Automation/LivePause, aucun exécuteur ou factory depuis GET. | Aucun passage obligatoire fictif de toute lecture par un service. |
| domain-values-and-time | PASS | Réponse 59–60/86–95 : déclencheur distinct de la source de page, date cible distincte des instants, heures Paris résolues vers Instant et règle d'offset explicite. | La séparation date civile/instant absolu est établie sémantiquement par Instant et résolution d'offset ; aucun mot exact UTC imposé. |
| runtime-and-storage-ports | PASS | Réponse 61/65–66/109–141 : rôle durable, consommateur sériel, qualification, deux ports et adaptateurs JDBC, routes standalone/live décrits. | Rôle ordonnanceur et garde fournisseur distincts ; start public et portée de l'écouteur explicités. |
| executor-and-completion | PASS | Réponse 62–64/143–169 : import/cache/fournisseur, ProviderAccess réelle, 35 pages, preuves, fermeture avant transaction du bean Completion vers collection/J8/ordre. | Aucun port de parseur inventé ; audit J8 éventuel et source spécialisée manquante restent explicites. |
| last-success-and-publication | PASS | Réponse 66/93/107/167–182/292 : dernier succès par date, mise à jour seulement en succès, rollback conserve l'ancien, consultation par identité ; écritures réelles JdbcJ3CollectionStore. | La clôture J8 de tous les chemins d'échec n'est pas inventée ; absence de preuve distinguée de défaut certain. |
| factory-and-supervisor | PASS | Réponse 68–73/201–209 : interfaces application.network.playwright, décorateur @Primary, constructeur concret, réservations/refus et superviseur implémentant les deux contrats. | Composition déclarée distinguée du contexte complet réellement qualifié. |
| live-handoff-and-worker | PASS | Réponse 224–265 : submitJ3/runJ3, thread propriétaire/lease, LiveProviderSession, parent IPC et worker, scope et validations des deux côtés. | Un même worker porte le temporaire isolé ; un seul contexte émet, aucun transfert de session. |
| maven-module-and-baseline | PASS | Réponse 188–199 : monomodule, Java 25, Boot 4.1.0, wrapper Maven 3.9.16 ; profils/sources différents des modules. | Versions déclarées, pas versions exécutées pendant la revue. |
| spring-activation | PASS | Réponse 111–123/211–220 : profil local par défaut, runtime J3, flags fournisseur/qualification, Web/servlet/127.0.0.1, réconciliation avant ticks. | Binding typé complet non établi pour classes hors corpus ; prendre le rôle ne lance pas le navigateur. |
| standard-runtime-disabled | PASS | Réponse 285/312/337 : Surefire/Failsafe runtime-enabled=false, IT à doubles explicites et navigateur réservé à la qualification native, absence de ce test du cycle standard à vérifier. | Pas d'affirmation que offline neutralise les effets locaux. |
| worker-profile-and-artifact | PASS | Réponse 192–199 : dépendance/sources/test sources ajoutées par provider-playwright-runtime, sorties séparées et JAR classifié ; compilation/production/lancement distincts. | Périmètre expressément limité au worker J3 ; aucune assimilation fautive du profil explicite J7. |
| blocked-live-profile | FAIL | La réponse entière (368 lignes), notamment 186–220 et 283–339, omet le maintien du blocage de sofascore-live-test. Le POM autorisé 426–443 contient le profil et sa règle Enforcer alwaysFail ; item_19 a réellement livré ce passage. | Omission obligatoire de restitution selon l'oracle original, rubrique JM-N01/Démarrage et séparation. Aucune activation du profil ni décision erronée d'autoriser le réseau n'est observée ; ce n'est pas un bug applicatif. |
| adopted-j3-governance | PASS | Réponse 22/224–265 : ADR007 v0.3, descriptions historiques contextualisées, ordres/clics déjà adoptés, contexte J3 temporaire dans live conservé. | Pas de nouvelle confirmation exigée pour l'autorisation J3 acquise ni généralisation aux autres parcours. |
| terminal-live-loss | PASS | Réponse 267–273 : perte navigateur/contexte mène à STOPPED/STOPPED_ERROR, interruptions à STOPPED_INTERRUPTED ; pas de tick recréateur ni reprise automatique. | La transition terminale est explicite ; le test natif nominal n'est pas présenté comme une panne injectée. |
| concrete-dependencies-and-minimality | PASS | Réponse 75–84/331–354 : catalogue, parseur et ScheduledEventsTransportException concrets visibles ; pas de refonte générale ou d'interface imposée. | Dette locale distinguée d'un défaut certain ou de contrainte déjà appliquée. |
| persistence-tests | PASS | Réponse 291–294/298–302 : quatre IT nommées, admission/concurrence, dernier succès/reprise, publication/import/cache/borne, threads/maintenance ; PostgreSQL et doubles fournisseur. | Aucune recette navigateur déduite des IT de persistance. |
| protocol-and-native-test-scope | PASS | Réponse 295–307 : J3WorkerScopeProtocolTest pur ; J3LivePauseWorkerQualificationIT présent, vrai worker/Chromium loopback, portée limitée hors admission/SQL/résilience complète. | Rejets parent possibles avant l'enfant et besoin d'essai IPC direct explicités. |
| native-test-selection | PASS | Réponse 304–310/337 : motif exact **/*LocalQualificationIT.java ne sélectionne pas automatiquement J3LivePauseWorkerQualificationIT ; -Dit.test et les deux goals Failsafe nommés sont donnés. | La proposition d'inclusion reste dans l'exécution dédiée ; aucun lancement effectué. |
| restricted-spring-test | PASS | Réponse 290/338 : test ApplicationContextRunner limité au superviseur, ne couvre pas @Primary complet ; complément de qualification proposé. | Annotations et présence des beans ne valent pas preuve du graphe complet. |
| exact-textual-guard-and-archunit | PASS | Réponse 289/314 : cible com.microsoft.playwright, src/main et extensions exactes ; interfaces internes distinguées ; garde textuel non exhaustif et absence ArchUnit au corpus. | Pas de taux ni analyse complète des dépendances inventés. |
| historical-evidence-and-limitations | PASS | Réponse 316–329 : résultats datés, incidents réels du rapport, aucune invention d'incident sandbox absent, différence de sélection actuelle non réconciliée. | Totaux et temps historiques non qualifiants pour les octets courants. |
| handoffs-and-future-checks | PASS | Réponse 335–352 : propositions bornées, relais ss-verify, ss-postgres-change et ss-data-contract-replay explicitement rattachés aux actions applicables. | Aucun changement de schéma, parseur, source, protocole ou application exécuté. |

## Intégrité et portée

Les **11 pièces figées**, **39 entrées** et **26 commandes terminées** sont conformes. La première commande a livré intégralement le candidat .3 exact. Les commandes/sorties publiées correspondent au journal brut, et la réponse est le dernier message final. Les lectures restent dans le corpus autorisé.

L'enveloppe ne fournit ni oracle ni ancienne réponse. Le catalogue est reconstruit après la session et ne prouve pas une injection automatique. Modèle et effort effectifs ne sont pas exposés ; les horodatages locaux ne permettent pas de déduire un temps de pensée.

La séparation de la date civile et des instants est retenue sur le sens effectivement exprimé : heures Paris résolues en `Instant`, date cible distincte et règles d'offset. La notation n'exige pas la présence du mot « UTC » comme marqueur isolé.

Cette revue n'a lancé aucun modèle, application, base de données, navigateur ou test. Aucune source, candidat, ancienne preuve ou réponse figée n'a été modifiée. Les anciens résultats n'ont pas servi d'oracle et aucune réponse corrigée n'a été produite.
