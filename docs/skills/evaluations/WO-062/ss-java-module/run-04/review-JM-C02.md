# Revue indépendante — JM-C02 — run-04

**Verdict : PASS** — candidat Java `0.1.0-candidate.3`.

Gel : `2026-09-15T23:46:48.071121+00:00`, lu après sa création.

- Candidat SHA-256 : `15237ab0f6773c87187481622ba4947ebc00f8c7e9a50dd1589911ede17ec332`.
- Réponse SHA-256 : `c625045faa38f2a017d896c34cd5cae1310c394571b84cd5638c5de9a434e175`.
- Gel SHA-256 : `134ef191aeefda4f48ab991abf930be51f1dba01f035d4e6e192b6b3f4a7e50a`.
- 11 pièces figées ; 27 entrées ; 28 commandes réellement terminées.

## Critères

### I01 — PASS
11 pièces figées et 27 entrées conformes taille/SHA ; 24 sources reliées au manifeste. Trace, requête, réponse et journal brut concordent.

Limite : Le SHA métier et le SHA préparatoire sont distincts ; aucun état applicatif qualifié.

### I02 — PASS
Export natif exactement égal au journal brut après retrait des seuls événements reasoning ; réponse exacte du dernier agent_message ; collecte complète et codes0.

Limite : Les horaires d’événements sont des réceptions locales, pas des temps de pensée.

### I03 — PASS
28 commandes réellement terminées relues ; uniquement lectures/recherches autorisées, sans oracle, ancienne réponse, effet métier ou mutation.

Limite : Les textes et propositions restent des données ; le contrôle de périmètre est distinct de la décision comportementale.

### standard-native — PASS
Réponse §2 : sélection native standard refusée ; source de qualification dans un profil dédié, runtime-enabled=false pour Surefire/Failsafe et commande native distincte. POM169–211/251–362.

Limite : Aucune compilation, exécution de test ou preuve Chromium courante revendiquée.

### generic-native-profile — PASS
§2/5 : motif exact **/*LocalQualificationIT.java distingué de *QualificationIT ; J3LivePauseWorkerQualificationIT ne correspond pas. Commande historique avec -Dit.test et objectifs Failsafe nommés reproduite. POM343–362 et rapport WO060102–109.

Limite : Profil présent, compilation, packaging et lancement effectif restent distincts.

### archunit-green — PASS
§2/4 : refus de la règle ArchUnit verte alléguée ; garde textuel com.microsoft.playwright dans src/main précisément borné, imports concrets adapter et création ScheduledEventsV1Parser de Executor reconnus. Verify-Local17–38 et Executor3–65.

Limite : Pas de règle exhaustive des dépendances ni séparation parfaite inventée.

### spring-primary — PASS
§2 : ApplicationContextRunner et import du seul superviseur distingués de la composition complète ; les deux ports pointent sur le même superviseur. Annotation @Primary du décorateur reconnue sans lui attribuer ce test. SpringContextTest15–43 et Resilient20–39.

Limite : Contexte Spring complet, binding et injection dans toutes les classes explicitement non vérifiés.

### historical-current — PASS
§1/2 : source_commit déclaré distingué du SHA courant/testé ; 2364 Surefire avec cinq exclusions et 276 Failsafe rapportés comme historiques ; qualification native explicite, incidents et limites conservés. Rapport WO06042–55/102–128/144 et POM comparés.

Limite : Manifestes et journaux historiques hors liste non ouverts ; aucun résultat historique reconduit au candidat courant.

### retention-and-publication — PASS
§3.A : écritures séparées refusées ; Executor ferme ProviderAccess puis appelle le service transactionnel distinct. Completion12–34 lie collection, terminal J8 et ordre ; JdbcCollection61–116 garde les preuves et le pointeur. ExecutionIT79–97/133–158 soutient commit incertain et rollback. Relais explicite ss-postgres-change puis ss-verify.

Limite : Course de rétention, migrations, sauvegarde/reprise et helpers non autorisés ne sont pas certifiés ; tests lus, pas exécutés.

### paused-live-restart — PASS
§3.B : conserve navigateur/contexte live/worker/garde/budgets/échéance ; J3 temporaire isolé et nettoyage avant retour d’autorité. Live71–93 borne le délai, Live108–170 arrête en cas d’échec, Worker242–324 conserve identité du live. Perte navigateur/contexte explicitement terminale sans recréation ; garde incertain bloquant.

Limite : Qualification native nominale lue mais non exécutée ; aucune injection de perte de contexte, preuve SQL ou couverture de tous les budgets déduite.

### maintenance-ready — PASS
§3.C : profil local seul insuffisant ; WebApplicationContext, ServletContext et adresse127.0.0.1 requis avant start. Runtime69–99 et RuntimeIT91–103 corroborent le refus du contexte de maintenance, sans ordre/leader/transport.

Limite : Le garde Web est dans onApplicationReady, pas tous les appels publics start ; configuration complète et écoute effective restent non vérifiées.

### missed-order — PASS
§3.D : rattrapage et effacement des refus refusés ; matérialisation bornée31 jours, MISSED et identités conservés, DAILY distinct des horaires explicites. JdbcAutomation133–185 et tests nommés corroborent. Resilient75–77/149–175 garde SUSPENDED et persistance du refus avant clôture.

Limite : Le test occurrence HTTP_FORBIDDEN ne prouve pas seul la suspension fournisseur après redémarrage ; adaptateur et test dédiés hors liste, relais PostgreSQL/verify explicites.

### bounded-conclusions — PASS
Entrée synthétique traitée comme données ; §4 décrit les responsabilités réelles sans imposer de modules/interfaces ; §5 présente des contrôles futurs explicitement non exécutés, avec relais Java/verify/PostgreSQL/contrat pertinents.

Limite : Aucun bug existant déduit des seules propositions ; aucun build, réseau, base, Docker, installation ou changement revendiqué.

### full-candidate-read — PASS
item_1 lit intégralement le candidat .3 exact avec code0 ; comparaison corps entier + CRLF console.

Limite : Lecture native par outil, pas injection automatique.

## Limites de qualification
- Catalogue reconstruit après exécution : pas capture de requête transmise ni preuve d’injection automatique du corps par le CLI.
- Modèle et effort effectifs non exposés ; réglages par défaut.
- Gain général de temps/qualité non mesuré.
- Aucune qualification applicative, collecte fournisseur, installation ou validation humaine déduite.

Relecture indépendante sans relance, ancienne réponse importée, modification des sources/candidat ou test applicatif. Validation humaine et installation personnelle non réalisées.
