# Revue indépendante — JM-C02

**Verdict : FAIL pour omission d'un critère obligatoire.**

Les cinq affirmations de couverture et les quatre scénarios reçoivent les décisions techniques attendues, avec des références précises. La réponse reste toutefois incomplète sur le relais spécialisé : elle ne confie aucune évolution SQL à `ss-postgres-change`, exigence explicite de l'oracle pour `retention-and-publication`.

Ce FAIL ne signifie pas qu'une décision transactionnelle est erronée, qu'un test applicatif a échoué ou que le candidat a modifié le code. Il ne résulte pas d'une exigence de style ou d'un titre exact.

## Omission constatée

- Oracle, lignes 160–162 : conserver la transaction réelle et « Relayer toute évolution SQL vers ss-postgres-change, sans prétendre changer les migrations ».
- Réponse figée, §2.1, lignes 27–54 : transaction, contrôles et limites correctement exposés ; la recommandation de la ligne 54 s'arrête au maintien des responsabilités JDBC et de complétion.
- Lecture complète et recherche dans la réponse : aucune mention de `ss-postgres-change`, ni relais équivalent vers une revue spécialisée de persistance.
- L'allowlist exclut le fichier du skill spécialisé. La revue n'exige donc aucune lecture ou exécution de ce skill ; elle attend le relais de responsabilité pour une éventuelle évolution SQL. L'absence du skill dans le corpus ne justifie pas l'omission de ce relais.
- Le cas ne propose pas d'évolution de contrat ou parseur : aucun échec supplémentaire n'est attribué à l'absence de `ss-data-contract-replay`.

## Identité et intégrité

- Candidat : `ss-java-module` `0.1.0-candidate.1`.
- SHA-256 candidat : `d20c8b838a9c4b9713e27c576e22272427d3f64fef50a670f5a09364cf849eb4`.
- SHA-256 réponse : `d528dc3aae6dd3f297e8ceb94899913811e19fe540ccf098761ad528676b31c0`.
- SHA-256 journal natif : `f9d0569f583ac83e6b4a4edd0ba56b2080710a93768c9aab4b9f75695dc29bcd`.
- Révision de préparation : `d89f9f2f3da136faf00976266202a4ff9b8c8c1f` ; sources métier : `6648dd423e556b5248b8793a539ae85a7680f9bc`.
- Gel présent avant tout accès à la réponse ; 9 fichiers figés conformes, 27 entrées conformes et 24 sources autorisées conformes au manifeste initial.
- `item_1` prouve une lecture outil intégrale du candidat exact avant analyse. Les 27 commandes terminées sont exclusivement des lectures locales autorisées. Le journal brut correspond à son empreinte conservée.
- Oracle réservé et historique de conception absents des entrées métier ; aucune régénération, réponse corrigée, mutation du candidat ou des sources.

## Critères obligatoires

| Critère | Résultat | Preuve / limite |
| --- | --- | --- |
| trace-freeze | PASS | Les 9 fichiers de frozen/JM-C02 correspondent à freeze.json ; journal brut conforme à trace.raw_events_sha256 ; 27 entrées conformes au préflight, dont les 24 sources conformes au manifeste. Aucun résultat applicatif n'est déduit de ces empreintes. |
| candidate-load | PASS | native-events.json item_1 : Get-Content -LiteralPath '.agents/skills/ss-java-module/SKILL.md' -Raw, exit_code=0. Sortie intégrale égale au candidat exact après normalisation CRLF/LF et retrait du blanc final. Lecture outil observée avant analyse ; pas de preuve d'injection automatique CLI. |
| independence-and-scope | PASS | Demandes et entrées conformes ; oracle/inventaire/plan absents du corpus transmis. Les 27 commandes terminées lisent seulement les fichiers autorisés. Aucun nouvel essai ni réponse corrigée. Catalogue reconstruit après exécution ; aucune découverte native du catalogue revendiquée pour ce cas métier explicite. |
| lab-revision-corpus | PASS | response.md:5-7 : corpus borné, statuts du Lab et SHA source 6648dd423e556b5248b8793a539ae85a7680f9bc présentés comme provenance déclarée ; limites des fichiers non autorisés détaillées par scénario. L'évalué n'invente pas une vérification Git hors allowlist. |
| responsibilities-and-dependencies | PASS | response.md:21-22, 128-145 : monomodule, responsabilités des classes, ports/adaptateurs et frontière parent/worker nommés ; dépendances concrètes au catalogue/parseur et composition @Primary conservées. Pas d'isolation parfaite, nouveau port ni refonte générale inventés. |
| technical-invariants | PASS | response.md:7, 83-99, 130, 145-161 : statuts, Java 25, Spring Boot 4.1.0, wrapper, loopback, sources/sorties worker séparées et compilation distincte du lancement. YAML/bindings effectifs hors corpus ; absence de nouvelle qualification explicitée. |
| current-j3-governance | PASS | response.md:62-79, 105-126 : ordre J3 adopté pendant pause, worker/contexte live conservés, temporaire J3 isolé, échéance initiale, nettoyage avant reprise ; aucun rattrapage global ou effacement des refus. Pas de restriction manuelle historique substituée à l'exception adoptée. |
| test-evidence-boundaries | PASS | response.md:9-13, 19-23, 69-77, 147-172 : distingue source, assertions, sélection et exécution ; classe native existante et commande explicite historiques ; limites PostgreSQL, worker, Spring et imports clairement exposées. Aucune qualification actuelle, couverture globale ou anomalie applicative certaine annoncée. |
| minimal-proposal | PASS | response.md:54, 79, 99, 126, 143, 164-170 : corrections bornées du dossier, responsabilités existantes et vérifications ciblées ; aucune source/migration changée. Le relais SQL spécialisé reste manquant, détaillé dans retention-and-publication. |
| standard-native | PASS | response.md:19, 151 : contredit inclusion native standard à partir du POM ; classe native non assimilée à une preuve standard, aucune exécution actuelle. Préserve la distinction standard/natif. |
| generic-native-profile | PASS | response.md:20, 155-161 : motif **/*LocalQualificationIT.java distingué de *QualificationIT ; sélection -Dit.test=J3LivePauseWorkerQualificationIT et goals Failsafe nommés. Ne prétend pas que le test est absent. |
| archunit-green | PASS | response.md:21 : pas d'ArchUnit déclaré, garde textuel limité, imports adapter et instanciations catalogue/parseur concrètes démontrées. Ne déclare pas une inspection exhaustive des fichiers hors corpus. |
| spring-primary | PASS | response.md:22 : test réduit importe seulement le superviseur ; décorateur @Primary et constructeur concret reconnus ; graphe applicatif complet non démontré. Une composition non qualifiée n'est pas transformée en bug certain. |
| historical-current | PASS | response.md:23 et 163-172 : rapport daté ne qualifie pas les octets courants ; fichiers/empreintes/configuration historique manquants reconnus. Pas de faux vert repris de la preuve historique. |
| retention-and-publication | FAIL | response.md:27-54 décrit correctement transaction, preuve/catalogue/dernier succès, ordre et terminal J8, et refuse deux commits indépendants. Toutefois §2.1 et la réponse entière omettent tout relais vers ss-postgres-change. Oracle.md:160-162 impose ce relais pour toute évolution SQL. Omission obligatoire de routage spécialisé, pas décision transactionnelle erronée. Aucun appel ou lecture du skill spécialisé hors allowlist n'était exigé ; un relais dans la recommandation suffisait. |
| paused-live-restart | PASS | response.md:56-79 : refus de recréer le live ou prolonger l'échéance ; même worker, contexte live conservé, contexte temporaire distinct et reprise après nettoyage prouvé, sources/méthodes/test précisément cités. Preuve d'identité de processus distinguée de celle du contexte ; pas de couverture native globale inventée. |
| maintenance-ready | PASS | response.md:81-99 : garde Web/servlet/127.0.0.1 avant start, puis runtime-enabled/local et réconciliation ; maintenanceContextCannotTakeLeadershipOrCollectEvenWithAllProviderFlagsEnabled précisément cité. Test réduit et appels directs start distingués du démarrage Web effectif. |
| missed-order | PASS | response.md:101-126 : horaires MISSED et identités durables, pas de rattrapage, suspension persistante et appels store.suspend ; tests exacts nommés et portée limitée. La persistance effective des refus après redémarrage reste inconnue faute d'adaptateur/test dans le corpus. |
| synthetic-data-not-current-bug | PASS | response.md:3, 7, 143 : propositions synthétiques séparées des mécanismes existants ; recommandations sans mutation. Les défauts des propositions ne sont pas attribués au code courant. |

## Portée

Les références `response.md:<ligne>` désignent l'original sous `frozen/JM-C02`. Modèle et effort effectifs ne sont pas exposés par la trace ; valeurs configurées par défaut sans override. Le catalogue conservé est une reconstruction après exécution, sans preuve d'injection automatique par le CLI.

Aucun test applicatif, build, navigateur, base de données ou appel fournisseur n'a été lancé. Les sources et le candidat restent inchangés ; la réponse originale reste intacte. Gain global de temps ou de qualité : **non mesuré**.

