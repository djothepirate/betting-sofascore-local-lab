# WO-SS-20260915-062 — Développement des skills spécialisés du lot 2

- **Statut :** `OWNER_ACCEPTED — A_INSTALLED — B_INSTALLED — C_QUALIFICATION_INCOMPLETE` ; PB, FQ et CS validés et installés personnellement. Java `.4` est conservé avec ses C6/C7 (sept PASS, un FAIL) ; Java `.5` est préparé sans recette. Les deux cas Windows `.1` restent non consommés après préflight hôte formel réussi. Validation humaine C, consolidation D et livraison Git restent distinctes.
- **Date :** 2026-09-15.
- **Autorité :** demande propriétaire de préparer un WO avec `ss-work-order`, à partir de la conversation « Skills du lot 2 », dans l'ordre précisé ci-dessous.
- **Acceptation :** le 15 septembre 2026, le propriétaire confirme « J’accepte le WO-062 » et autorise l'inventaire des sources et la préparation des cas d'évaluation de `ss-provider-benchmark`.
- **Autorisation A2 :** le propriétaire demande ensuite de rédiger ce candidat depuis A1 et de l'évaluer sans oracle dans le contexte évalué ; il autorise explicitement les quatre sessions éphémères de sélection et la transmission de leur contexte local.
- **Complément PB-S01 :** le propriétaire demande de terminer la qualification ouverte ; run-02 apporte la lecture intégrale du candidat exact par outil et une revue PASS. Le premier run reste historique et inchangé.
- **Validation et installation A3 :** le propriétaire accepte explicitement le contenu, le périmètre et les limites de `0.1.0-candidate.1`, puis autorise son installation personnelle ; [décision exacte et empreintes](../../skills/evaluations/WO-062/ss-provider-benchmark/human-validation.json).
- **Validation et installation B :** le propriétaire accepte le contenu, le périmètre et les limites des deux candidats ; [décisions et installation vérifiée](../../validation/WO062-FOOTBALL-QUALITY-CI-SECURITY-INSTALLATION-20260915.md).
- **Branche :** `feature/V0.1.0-RC01-CODEX-WO-SS-20260915-062`.
- **Cible de PR :** `feature/V0.1.0-RC01`.
- **Base canonique vérifiée sur GitHub :** `74d3f38afd64ce587353fbd645cad7a388e9756c` ; train local et distant identiques lors du cadrage, après fusion de la PR #37 / WO-061.
- **Version Maven :** `0.1.0-rc.1-SNAPSHOT`.
- **Worktree :** `.tmp/wo062-skills-lot2`, distinct du checkout du train et d'Eclipse.
- **Prédécesseur :** [WO-054 — livraison du lot 1](../completed/WO-SS-20260905-054-skills-lot1.md).
- **Preuve du présent cadrage :** [WO062-SKILLS-LOT2-SCOPING-20260915](../../validation/WO062-SKILLS-LOT2-SCOPING-20260915.md).

## 1. Objectif et état établi

Développer cinq skills `ss-*` qui rendent réutilisable l'expertise du SofaScore Local Lab :
mesure fournisseur, qualité des données football, qualification CI, architecture Java et
diagnostic Windows. Ils complètent les cinq skills de méthode du lot 1, sans recopier les
règles métier ou les procédures qui font déjà autorité dans le dépôt.

La [conversation source « Skills du lot 2 »](chatgpt-conversation://6aa965f5-70ac-83eb-abcd-c54b44c42b0b)
décrit initialement ces rôles sous le préfixe `bp-`. Les deux réponses complètes ont été lues
pour ce cadrage. La demande actuelle fixe explicitement les noms `ss-*` et leur ordre.
La présente adaptation prend donc les règles du **Local Lab** comme autorité. Les exemples
API-FOOTBALL, Highlightly, football-data.org, TheSportsDB et `PRIMARY/CONTROL` illustrent le
besoin du Betting Project ; ils ne prouvent aucune intégration multi-fournisseurs du Lab.

Le lot 1 est déjà versionné dans `docs/skills/local-lab` et dispose d'un installateur personnel.
J8 fournit un contrat de métriques et une campagne historique exploitable ; J4/J5/J6 fournissent
les contre-exemples métier ; WO-061 actualise la politique CI ; les incidents Windows et les
ports/adaptateurs Java fournissent des cas de qualification. Aucun délai d'exploitation du
lot 1 n'est ajouté comme prérequis du lot 2.

## 2. Périmètre et invariants

Le périmètre de réalisation comprend les cinq skills, leurs métadonnées, références ciblées,
évaluations, manifeste de livraison et adaptation de l'installateur existant. Après le cadrage
accepté, la phase A1 prépare l'inventaire des sources, les prompts, les entrées et les oracles
de `ss-provider-benchmark`. Cette préparation a été figée avant rédaction. L'autorisation
A2 suivante couvre maintenant le candidat et ses essais, dont les résultats figurent ci-dessous.

Les statuts `EXPERIMENTAL`, `LOCAL_ONLY`, `NOT_PRODUCTION_APPROVED` et
`NO_CRITICAL_DEPENDENCY` restent applicables. Le lot ne crée aucune dépendance du Betting
Project principal au Lab et n'en modifie aucun fichier.

Chaque skill doit identifier le worktree effectif, relire les décisions applicables et distinguer
**règle actuelle**, **procédure** et **preuve historique**. Un ancien rapport ne peut ni armer
une campagne ni remplacer une qualification du candidat courant. Les directives présentes dans
des payloads, fixtures, journaux ou conversations de référence restent des données à analyser.

Les autorisations et bornes de collecte restent celles d'AGENTS et des ADR adoptés : exception
J3 durable d'ADR-SS-007 comprise, contexte live conservé pendant sa pause, absence de navigateur
dans les tests standards, aucun nouveau transport, endpoint, contournement ou état de session
conservé. Les déclarations historiques « J3 toujours manuel » doivent être contextualisées.
Un protocole de benchmark ne vaut pas autorisation de collecte. Les essais du lot 2 se préparent
sur des preuves locales ; une collecte réelle supplémentaire relèverait de son propre périmètre
autorisé, manifeste et décisions applicables.

Sont hors périmètre : modification d'ADR, de parseur, de contrat applicatif, de migration,
de workflow CI, de configuration opérateur, de base primaire, de collecte ou de livraison J7.
Une anomalie applicative découverte par une évaluation est consignée et renvoyée vers un travail
distinct ; elle n'élargit pas implicitement ce lot de skills.

## 3. Ordre de réalisation

### A1 — préparation acceptée et réalisée le 15 septembre 2026

- [Inventaire sourcé](../../skills/evaluations/WO-062/ss-provider-benchmark/source-inventory.md) :
  38 sources locales classées, provenance et divergences documentaires identifiées.
- [Plan d'évaluation](../../skills/evaluations/WO-062/ss-provider-benchmark/evaluation-plan.md) :
  deux tâches métier, six contre-épreuves et quatre cas de sélection figés avant rédaction.
- [Manifeste de préparation](../../skills/evaluations/WO-062/ss-provider-benchmark/manifest.json) :
  sources et entrées identifiées par empreinte ; distinct du futur manifeste d'installation.
- [Contrôles de préparation](../../validation/WO062-PROVIDER-BENCHMARK-PREPARATION-20260915.md) :
  cohérence des sources/cas/oracles, revue indépendante et limites. Tous les essais du skill
  restaient `NOT_RUN` à la clôture A1 ; cette preuve historique reste intacte.

### A2 — candidat et évaluations du 15 septembre 2026

- [Candidat 0.1.0-candidate.1](../../skills/local-lab/ss-provider-benchmark/SKILL.md) figé avant les essais.
- Huit réponses métier indépendantes de l'oracle, puis quatre sessions natives de sélection :
  **Run-01 : 12 cas exécutés, 11 PASS et PB-S01 BLOCKED** pour preuve de chargement insuffisante.
  Ce résultat historique reste intact. **Run-02 : PB-S01 PASS**, lecture intégrale du candidat
  exact prouvée dans une réexécution instrumentée et revue indépendamment. Le bilan cumulé
  qualifie les douze cas distincts ; treize exécutions sont conservées.
- [Protocole et scorecard prematch/live](../../skills/evaluations/WO-062/ss-provider-benchmark/run-01/cases/PB-N01/response.md)
  désormais produits ; observations, règles actuelles et qualifications locales distinguées.
- [Validation A2](../../validation/WO062-PROVIDER-BENCHMARK-CANDIDATE-20260915.md) et
  [résultats/revues](../../skills/evaluations/WO-062/ss-provider-benchmark/run-01/results.json).
- [Qualification courante et portée de la preuve](../../validation/WO062-PROVIDER-BENCHMARK-PBS01-QUALIFICATION-20260915.md),
  avec [bilan cumulé](../../skills/evaluations/WO-062/ss-provider-benchmark/qualification.json).
- À la fin A2, validation humaine et installation restaient à réaliser. Cette étape conservait
  les quatre autres skills, l'installateur, le lot 1 et SKL-002 sans modification.

### A3 — validation propriétaire et installation personnelle du 15 septembre 2026

- Contenu, périmètre et limites de `0.1.0-candidate.1` acceptés ; version et deux fichiers conservés à l'identique.
- [Manifeste ciblé](../../skills/evaluations/WO-062/ss-provider-benchmark/installation-manifest.json) distinct de SKL-002 ; option explicite `-Package ProviderBenchmark`, défaut lot 1 conservé.
- Installation personnelle : deux fichiers copiés, vérification taille/SHA-256 réussie, une entrée USER découverte par le CLI local.
- Dix fichiers personnels du lot 1 conservés, contenu et dates de modification identiques.
- 18 cas d'installation isolée réussis (7 lot 1, 11 nouveau paquet) et revue indépendante favorable.
- [Preuve A3](../../validation/WO062-PROVIDER-BENCHMARK-INSTALLATION-20260915.md). Les quatre autres skills, la consolidation des dix et la clôture du WO restent à réaliser.

### Réalisation B — football puis CI/sécurité

- Le propriétaire demande « réaliser les skills ss-football-quality et ss-ci-security », puis autorise explicitement les seize essais éphémères et leurs entrées internes ; cette décision ne vaut pas validation humaine du contenu.
- Préparation figée : 25 sources et huit cas par skill, commit `15158a5`, avant rédaction.
- Deux candidats `0.1.0-candidate.1` : huit PASS chacun après revue indépendante, dont historique, nouvelle analyse, deux contre-épreuves et quatre sélections instrumentées.
- Correction d'encodage des seules métadonnées football tracée ; corps métier inchangé. H01/N01 conservent leurs entrées initiales, les six suivants et le paquet utilisent UTF-8.
- [Rapport B et limites](../../validation/WO062-FOOTBALL-QUALITY-CI-SECURITY-CANDIDATES-20260915.md) ; nouvelles réponses et revues conservées. À la fin de cette recette, FQ/CS attendaient leur validation humaine et leur installation personnelle ; PB, le lot 1 et l’installateur étaient inchangés.

### Validation propriétaire et installation B du 15 septembre 2026

- Contenu, périmètre et limites des deux versions `0.1.0-candidate.1` acceptés ; quatre fichiers qualifiés conservés à l’identique.
- [Manifeste ciblé](../../skills/evaluations/WO-062/football-quality-ci-security/installation-manifest.json) : paquet explicite `FootballQualityCiSecurity`, quatre fichiers ; défaut `Lot1` et paquet `ProviderBenchmark` conservés.
- 32 cas d’installation isolée réussis : 7 lot 1, 11 PB et 14 FQ/CS ; revue indépendante favorable.
- Installation personnelle : quatre fichiers copiés, vérification et seconde installation sans réécriture ; douze fichiers préexistants conservés avec leurs dates de modification.
- Huit skills personnels découverts par le CLI local, une entrée par nom ; cette découverte ne constitue pas une nouvelle recette comportementale commune.
- [Rapport d’installation et limites](../../validation/WO062-FOOTBALL-QUALITY-CI-SECURITY-INSTALLATION-20260915.md) : incident initial avant copie, correction du seul environnement enfant, commandes et preuves exactes.

### Réalisation C — Java puis Windows

- Demande propriétaire « faire ss-java-module, puis ss-windows-runtime », puis autorisation
  explicite de seize sessions éphémères sans oracle, dont deux sondes Windows bornées.
- Préparations figées avant rédaction : 48 sources Java et 23 Windows, huit cas par skill,
  avec protocole et fixture des deux sondes Windows. Les sources et critères restent intacts.
- Première recette Java `.1` : 5 PASS / 3 FAIL pour omissions obligatoires de restitution.
  Correction `.2` versionnée à `2d62f12`, revue statique favorable ; aucun résultat `.1`
  n'est transféré à cette nouvelle version.
- Première recette Windows `.1` : 6 PASS / 2 BLOCKED avant lecture du candidat ou exécution,
  sur refus du setup sandbox. Nouveaux contextes créés sous le compte du lanceur ; accès
  local vérifié sans modèle et avec sandbox conservé, sans qualifier les sondes.
- Le propriétaire autorise ensuite **dix sessions supplémentaires** : huit Java `.2`, puis
  les deux sondes Windows `.1`, avec mêmes sources, critères et bornes. Dix tentatives
  conservées le 15–16 septembre : neuf sessions terminées et une expirée.
- À la fin de run-02, Java `.2` : **7 PASS / 1 FAIL**, JM-N01 restant incomplet sur UTC, perte du contexte
  live et périmètre exact du garde textuel. Windows `.1` : **6 PASS / 2 BLOCKED** courants.
  WR-H01 produit un harnais réussi en 2,73 s et un nettoyage établi, mais sa session
  expire à 900 s sans réponse finale. WR-N01 observe le code 23 du relais Java, puis
  bloque avant sleep sur une identité incohérente ; un journal temporaire est conservé.
  Les 26 tentatives historiques restent traçables ; aucun résultat manquant ne devient PASS.
- [Rapport C, preuves et limites](../../validation/WO062-JAVA-WINDOWS-CANDIDATES-20260915.md).
  Les seize fichiers personnels précédents, les preuves A/B, l'installateur et le code
  applicatif restent intacts. Validation humaine et installation personnelle C non réalisées.
- Le propriétaire demande une reprise très ciblée : JM-N01 frais, diagnostic du temps WR-H01
  et fin complète, amélioration de propriété avant répétition WR-N01. Deux nouvelles sessions
  sont figées puis revues : JM-N01 **FAIL** sur perte du contexte live et garde Playwright exact,
  avec distinction date/instant désormais présente ; WR-H01 **BLOCKED** avant le harnais sur
  accès refusé, bien que sa réponse finale soit rendue en 281,875 s. Le diagnostic local
  reproduit séparément un refus CIM, sans prouver rétroactivement la ligne de cette session.
- La sonde préalable Java directe relie handle, PID, parent, image et UUID en moins d'une seconde ;
  revue **PASS sur la propriété seulement**. WR-N01 complet n'est pas relancé ; ses conditions
  de reprise sont préparées, le résidu antérieur conservé. Total C : 28 tentatives, 27 réponses
  finales et une expiration historique ; bilan courant inchangé, 13 PASS / 1 FAIL / 2 BLOCKED.
  Voir le [rapport de reprise ciblée](../../validation/WO062-C-TARGETED-REPRISE-20260916.md).

### Requalification C4 du 16 septembre 2026

- Demande propriétaire : correction ciblée du candidat Java avant recette, WR-H01 avec
  lancement/résultats/postflight/conclusion/réponse finale dans une seule session, puis
  WR-N01 failure et sleep avec propriété des processus réellement lancés.
- Java `.3` est corrigé, revu et versionné avant les huit nouveaux essais ; aucun PASS
  antérieur n'est transféré. Les deux omissions ciblées sont corrigées dans JM-N01,
  qui reste FAIL sur une omission distincte : le maintien du profil `sofascore-live-test`
  bloqué. La lecture du mécanisme POM est établie ; aucune activation n'est observée.
- WR-H01 Windows `.1` accomplit la chaîne demandée : harnais en 2 686,1568 ms, code 0,
  postflight indépendant et réponse finale, session de 428,250 s sans expiration.
  Les huit critères substantiels et les omissions historiques sont PASS. Le complément
  C4 de lecture ciblée est enfreint : FAIL de conformité distinct du succès natif.
- WR-N01 `.1` termine normalement avec réponse finale après 531,828 s, mais son conducteur
  échoue sur `Get-FileHash` avant toute JVM. Modes failure/sleep non exécutés, BLOCKED ;
  le postflight constate l'absence de ressource créée. La preuve de propriété antérieure
  ne remplace pas les identités et résultats manquants de cette tentative.
- Dix nouvelles sessions sans oracle ni réponses précédentes ; sources et compléments
  versionnés avant les essais concernés. Les 38 tentatives C conservent 37 réponses finales
  et une expiration historique. Aucun ancien run, oracle, fixture, résidu ou fichier
  personnel n'est réécrit. Aucun candidat C n'est humainement validé ou installé.
- [Rapport C4 et contrôles](../../validation/WO062-C4-REQUALIFICATION-20260916.md).
  La recette de dix tentatives est terminée ; la qualification des deux skills reste ouverte.

### Correction et préparation C5 du 16 septembre 2026

- Java `.4` corrige la double omission de `JM-N01` : lorsque la revue J3 examine le
  profil Maven concerné, sa restitution doit maintenant nommer explicitement
  `sofascore-live-test`, son garde POM volontairement bloquant et la distinction avec
  une activation de collecte ou le profil J7. La correction, les sources et les huit
  contextes C5 sont gelés avant toute nouvelle recette ; les PASS `.3` ne sont toujours
  pas transférables.
- Le complément C5 de `WR-H01` interdit la lecture manuelle, la recherche et les
  lectures répétées du grand module `WO036-CampaignTools.psm1`. Ce module ne reste
  disponible que comme dépendance runtime du harnais intact ; un extrait court, son
  SHA et les gabarits opérationnels séparés servent à la conduite, aux preuves et au
  postflight.
- Le conducteur C5 de `WR-N01` enregistre le runtime avant toute empreinte, vérifie
  dans son propre processus Windows PowerShell 5.1 ses dépendances et le repli
  SHA-256 .NET, puis seulement calcule les empreintes. Son préflight sans Java et sa
  revue statique passent ; ils ne remplacent pas les deux JVM `failure` et `sleep`.
- La première tentative Java C5 `JM-H01` a atteint le watchdog à `900.266 s` après
  refus du transport Codex (`os error 10013`) et avant toute commande du modèle,
  lecture du candidat ou réponse finale observable. Les preuves sont publiées comme
  [`run-05`](../../skills/evaluations/WO-062/ss-java-module/run-05/results.json) et
  revues `BLOCKED_BEFORE_MODEL_COMMAND`, sans verdict comportemental du candidat.
  Les sept cas Java restants sont retenus : une recette fraîche de `JM-H01` nécessite
  une nouvelle décision propriétaire, car la séquence C5 est bornée et sans relance
  automatique.
- Le préflight Windows C5 est aussi relancé depuis le compte hôte sans modèle ; il atteint
  le sandbox mais échoue avant PowerShell avec `helper_unknown_error: setup refresh had
  errors`. Les deux essais Windows restent non lancés et non consommés. Le
  [suivi hôte](../../skills/evaluations/WO-062/c-requalification-05/post-preparation/windows-host-preflight-followup.md)
  conserve ce résultat et la frontière de sécurité inchangée.
- Aucune validation humaine, installation personnelle C, modification applicative, build,
  test Maven, collecte, publication, push, PR, fusion ou clôture du WO n'est déduite de
  cette préparation et de ces blocages d'infrastructure.

### Reprise C6 autorisée — `JM-H01` unique

- Le propriétaire autorise explicitement un nouveau run et une seule session fraîche pour
  `JM-H01`. [L'autorisation C6](../../skills/evaluations/WO-062/c-requalification-06/authorization.json)
  limite cette reprise au candidat Java `.4`, au service configuré, à `run-06`, au sandbox
  `read-only` et à un seul worker ; elle ne couvre aucun autre cas Java ou Windows et ne
  permet aucune relance automatique.
- Le contexte C6 est reconstruit depuis les sources canoniques dans une racine neuve, sans
  artefact C5, réponse, oracle, plan, revue, résultat ou matériel Windows. Ses quatorze
  entrées, son prompt et sa demande sont égaux par empreinte au contrat C5 initial, sans
  que les preuves C5 ne soient exposées au modèle. La préparation et les contrôles statiques
  sont versionnés avant la session.
- La session unique s'est terminée normalement en `306.656 s`, avec 18 commandes locales
  limitées aux entrées autorisées et une réponse finale gelée avant revue. La
  [revue C6](../../skills/evaluations/WO-062/ss-java-module/run-06/review-JM-H01.md)
  rend `PASS` pour `JM-H01` du candidat `.4`. Elle couvre aussi explicitement que
  `sofascore-live-test` reste bloqué. Ce PASS borné n'autorise ni les sept autres cas
  Java, ni les cas Windows, ni une validation humaine ou installation personnelle.

### Reprise C7 autorisée — sept cas Java `.4`

- Le propriétaire autorise explicitement sept sessions fraîches : `JM-N01`, `JM-C01`,
  `JM-C02`, `JM-S01`, `JM-S02`, `JM-S03` et `JM-S04`. [L'autorisation C7](../../skills/evaluations/WO-062/c-requalification-07/authorization.json) fixe le
  candidat `.4`, `run-07`, le sandbox `read-only`, un worker séquentiel, 900 secondes
  par session et aucune relance automatique. Elle exclut `JM-H01`, les cas Windows,
  la validation humaine, l'installation et toute action applicative.
- Les sept contextes sont recréés depuis les sources canoniques, sans réponse, verdict,
  oracle, plan, inventaire ni matériel C5/C6 ou Windows. Le contrôle statique rapproche
  chaque demande et chaque enveloppe de son contrat C5 initial ; les sorties sont vides
  avant lancement. Après chaque gel, un audit indépendant des chemins de commande Java
  est requis car l'audit intégré du conducteur C5 n'est applicable qu'à `WR-H01`.
- Un premier appel local du conducteur C7 s'est arrêté avant `codex exec`, car une
  assertion C5 résiduelle rejetait `run-07`. Aucun événement, réponse, gel ni session
  modèle n'a été créé. Le correctif borne le changement au conducteur C7 distinct,
   conserve les gardes C7 et les sept contextes vides, et laisse C5/C6 inchangés.
- La recette corrigée lance ensuite exactement les sept sessions autorisées dans l’ordre,
  sans relance du conducteur ni huitième session. Les sept tours se terminent normalement ;
  les copies publiées de leurs gels correspondent aux SHA-256 de leurs sources. Le
  [postflight C7](../../skills/evaluations/WO-062/ss-java-module/run-07/postflight-integrity.json)
  conserve les entrées, commandes, diagnostics et limites de chaque cas.
- Résultats comportementaux : JM-C01, JM-C02, JM-S01, JM-S02, JM-S03 et JM-S04 sont
  PASS. JM-N01 est FAIL : sa réponse décrit le contexte J3 temporaire et le contexte live
  conservé, mais omet de restituer explicitement l’absence de transfert de cookie, d’état
  ou de donnée de session entre eux. Elle restitue bien, cette fois, que
  sofascore-live-test reste volontairement bloqué. La qualification du candidat Java .4
  demeure donc incomplète ; aucune validation humaine ou installation personnelle n’en découle.
- JM-C02 enregistre deux reconnexions internes de sampling après des ruptures WebSocket,
  puis un unique turn.completed, dans la même session et sous 900 secondes. Le conducteur
  reste à automatic_retry=false, avec un seul thread et aucune nouvelle invocation
  codex exec ; cette limite de service est conservée dans la revue plutôt que présentée
  comme une exécution sans reprise interne.

### Correction minimale candidate.5 — préparation sans recette

- Le propriétaire demande une correction strictement limitée au dernier FAIL C7 de
  `JM-N01`. Le candidat Java `.5` ajoute dans la seule règle de pause live/J3
  l'interdiction explicite de transférer ou réutiliser cookie, état de stockage ou
  autre donnée de session du contexte live conservé vers le contexte J3 temporaire,
  et exige que cette interdiction soit restituée dans la réponse lorsqu'elle s'applique.
- Les trois corrections `.4` sont conservées : perte terminale sans reprise/recréation,
  garde exact `com.microsoft.playwright` sous `src/main`, et profil
  `sofascore-live-test` volontairement bloqué. `agents/openai.yaml`, les fixtures,
  oracles, critères, contextes, réponses, revues et résultats C6/C7 ne sont pas modifiés.
- [L'archive candidate.4, le diff exact et la révision .5](../../skills/evaluations/WO-062/ss-java-module/revision-05/revision.json)
  distinguent les octets historiques `7 PASS / 1 FAIL` du nouveau SHA préparé. La
  [qualification courante](../../skills/evaluations/WO-062/ss-java-module/qualification.json)
  passe à `PREPARED_NOT_QUALIFIED` : aucun cas Java `.5` n'est lancé, aucune validation
  humaine ni installation personnelle n'est produite.
- La [préparation de qualification](../../skills/evaluations/WO-062/ss-java-module/revision-05/qualification-plan.md)
  fixe la conséquence du protocole C6/C7 : une future recette `JM-N01` seule peut
  observer le correctif, mais ne transfère pas les sept PASS `.4` et ne qualifie pas
  `.5`. Une qualification complète exigera huit cas frais sous le SHA `.5` et une
  autorisation propriétaire distincte.

- La [revue statique séparée](../../skills/evaluations/WO-062/ss-java-module/revision-05/static-review.md)
  est `PASS_STATIC_ONLY` : elle confirme le périmètre limité, les trois corrections
  conservées et l'absence de recette `.5`. Elle ne remplace ni une session de
  qualification, ni une revue humaine.

### Déblocage Windows — staging hôte sans modèle

- Le nouveau préflight hôte C5 des contextes gelés échoue encore avant PowerShell avec
  `helper_unknown_error: setup refresh had errors`. Le journal du sandbox relie cet
  échec à `SetNamedSecurityInfoW: 5` sur les racines `WR-H01`/`WR-N01`, leurs
  `.agents` et leurs `.git`. Les racines gelées sont détenues par
  `CodexSandboxOffline`; aucun harnais, JVM, modèle ou session Windows n'a été lancé.
- Une mutation récursive de propriétaire/ACL de `run-05` n'est pas appliquée. À sa
  place, deux copies de staging hôte-propriétaires sont créées sans métadonnées de
  sécurité des sources, puis rapprochées byte-à-byte du contrat C5 : 18 fichiers
  vérifiés pour `WR-H01`, 16 pour `WR-N01`, `preflight.json` identique et sorties
  initialement vides.
- Les deux préflights sans modèle réussissent alors : lecture du candidat, écriture,
  relecture et suppression du marqueur, avec code 0, stderr vide et aucun résidu en
  0,951 s (`WR-H01`) et 0,712 s (`WR-N01`). La
  [preuve de staging](../../skills/evaluations/WO-062/c-requalification-05/post-preparation/windows-host-owned-staging-preflight.md)
  établit que le sandbox `elevated` fonctionne dans un contexte hôte-propriétaire,
  sans qualifier les deux sondes.
- Ce succès isole le blocage aux métadonnées de sécurité des contextes C5. Il ne
  transforme pas le staging en contexte qualifiable et ne consomme aucune session.
- Une racine physique formelle, détenue par l'hôte et conservant l'identifiant
  logique `run-05`, est ensuite préparée sans modèle. Son contrat C5, son
  autorisation, ses demandes, ses prompts et toutes ses entrées sont byte-à-byte
  conformes; racines, `.agents` et `.git` sont détenus par `geoff`, les sorties et
  artefacts interdits sont absents. Son préflight hôte sans modèle réussit aussi
  pour les deux cas (0,730 s et 0,680 s). Le
  [reçu formel](../../skills/evaluations/WO-062/c-requalification-05/post-preparation/windows-host-formal-context.md)
  rend l'environnement prêt sans créer de nouveau run logique, ni lancer ou
  consommer une recette Windows.
- Le [plan d’exécution formel](../../skills/evaluations/WO-062/c-requalification-05/post-preparation/windows-host-formal-execution-plan.md)
  fixe déjà l’ordre unique `WR-H01` puis `WR-N01`, les gardes C5, les preuves à
  geler et l’arrêt obligatoire avant `WR-N01` si `WR-H01` est incomplet ; il est
  préparé mais non invoqué.

### Ordre restant et consolidation

| Étape | Skill | Résultat attendu avant passage à la suite |
|---|---|---|
| A — priorité 1 | `ss-provider-benchmark` | Procédure et format de preuve évalués sur J8 ; dénominateurs, comparabilité et limites explicites. |
| B — priorité 2 | `ss-football-quality`, puis `ss-ci-security` | Cohérence métier et qualification CI évaluées séparément, avec leurs règles actuelles du Lab. |
| C — priorité 3 | `ss-java-module`, puis `ss-windows-runtime` | Frontières Java et diagnostic Windows évalués dans leur contexte réel. |
| D — consolidation | Les cinq skills | Coexistence avec le lot 1, installation isolée, revue et traçabilité de livraison. |

Le WO reste unique ; chaque skill fait l'objet d'un petit incrément relié à WO-062. La rédaction
et la recette suivent cet ordre. Une revue indépendante des sources peut se faire en parallèle,
sans modifier simultanément les mêmes fichiers. La stabilité de chaque skill exige le cas
historique et la tâche nouvelle de la section 6 ; une évaluation manquante reste visible.

## 4. Contrat de chaque skill

### A. `ss-provider-benchmark` — méthode de mesure et de comparaison

**Déclenchement :** cadrer, produire ou relire un benchmark de capacités fournisseur du Lab,
une scorecard, une comparaison prematch/live/historique ou un bilan de couverture/coût.

**Procédure attendue :** question → périmètre figé → population/échantillon → capacités →
budget → protocole → journal des tentatives → mesures → anomalies → scorecard → limites →
recommandation. Distinguer découverte, pagination, requêtes marginales, cache, import local,
échec et réponse persistée ; ne pas inférer les tentatives absentes du seul stock de réponses.

**Sortie observable :** population et hash, fenêtre UTC `[from,to)`, `asOf`, origine des preuves,
formules et numérateurs/dénominateurs, exclusion des cas non comparables. Couverture, complétude,
réussite, fraîcheur, médiane/P95, coût de découverte, requêtes et simultanéité restent séparés.
Le journal identifie fournisseur/capacité, rencontre, instant, issue, durée et coût connu.
Les strates J8 `FULL_ATTEMPT_LEDGER`, `RESPONSE_ONLY`, `LEGACY_BASELINE` restent distinctes.
Une limite interne du Lab n'est jamais présentée comme un quota garanti par le fournisseur.

**Contre-épreuves :** dénominateur nul, contrôle externe absent, mélange fixture/live,
latence historique inconnue ou cohortes différentes donnent `NOT_MEASURED` / non-comparabilité,
avec raison. La cohérence interne n'est pas l'exactitude sportive. Une conclusion J9 ou un
remplissage de scorecard du Betting Project n'est pas automatisé par ce skill.

### B1. `ss-football-quality` — cohérence sémantique du football

**Déclenchement :** analyser une incohérence d'identité, d'horaire, de rôle HOME/AWAY,
de statut, de composition, de statistique, d'incident ou de correction tardive J4/J5/J6.

**Procédure attendue :** identifier contrat et parseur de l'observation, provenance et heure de
réception ; comparer identité fournisseur, compétition/saison/équipes, terrain neutre éventuel,
fuseaux et changements d'heure, reprogrammation, périodes/prolongations, VAR, penalties et tirs
au but. Distinguer titulaires/banc/remplacements et états de complétude par famille.

**Sortie observable :** matrice d'anomalies reliant valeur observée, règle applicable, snapshot
ou fixture, hash, parseur, réception, gravité et décision justifiée. Préserver les distinctions
absent/null/zéro/vide valide/indisponible/partiel/tardif. Un changement d'horaire ou de nom ne
crée pas une nouvelle identité locale ; un reparsing local n'est pas une correction fournisseur.

**Contre-épreuves :** conflit HOME/AWAY sans autorité de résolution documentée et appariement
d'incidents indécidable restent signalés comme ambiguïtés. Une minute absente autorisée par
le contrat reste inconnue ; un penalty `awarded` reste un penalty accordé sans résultat de
tir inféré. Ne pas convertir une séquence de tirs au but en minute. Ne pas inventer
un registre multi-fournisseurs ou une priorité `PRIMARY/CONTROL` absente du Lab.

### B2. `ss-ci-security` — ce que prouvent les contrôles et la livraison

**Déclenchement :** analyser un échec CI, qualifier un ensemble de checks ou vérifier la
provenance d'une distribution locale. Ce skill complète l'exécution conduite par `ss-verify`.

**Procédure attendue :** rattacher le résultat au SHA, à la plateforme, à la commande et aux
rapports effectifs ; distinguer régression applicative, vulnérabilité constatée, scanner
indisponible, cache non fiable, quota/stockage et défaut de package/reproductibilité.

**Sortie observable :** tableau de contrôles avec exigence applicable, résultat `PASS`, `FAIL`,
`NOT_EXECUTED` ou `NOT_APPLICABLE`, preuve et limite. `NOT_APPLICABLE` exige une justification
normative ; un contrôle obligatoire non exécuté interdit un verdict global complet. XML absent,
invalide ou sans test réellement exécuté ne vaut pas succès ; un rapport requis reste bloquant.

**Contre-épreuves :** ancien SHA vert, upload échoué après tests verts, Javadoc facultative
indisponible et scanner non exécuté doivent produire quatre diagnostics distincts. Appliquer
WO-061 / ADR-SS-004 : aucun retour implicite des bundles intermédiaires ou des scans coûteux
automatiques hérités. NVD, SBOM, caches, SHA-256 et reproductibilité sont examinés lorsque
le contrôle ou la distribution concernés les exigent, sans dégrader une exigence applicable.

### C1. `ss-java-module` — placement et frontières de l'architecture Java

**Déclenchement :** cadrer ou relire une fonctionnalité Java du Lab, un port, un adaptateur,
une dépendance entre couches ou un profil Spring/Maven.

**Procédure attendue :** localiser domaine → cas d'usage → ports → adaptateurs dans le code
existant ; justifier responsabilités, dépendances, configuration et persistance. Vérifier Java
25, Spring Boot 4.1.0, wrapper Maven, isolation des sources du worker Playwright et démarrages
effectifs des profils. Le Lab est monomodule Maven ; « module » désigne ici une responsabilité
dans ses couches existantes. Ne pas lui imposer le découpage du monolithe Betting Project.

**Sortie observable :** carte des classes/couches et sens des dépendances, proposition minimale,
risques, contrôles existants à exécuter. Relayer contrats/replay vers `ss-data-contract-replay`
et migrations vers `ss-postgres-change`, sans recopier leurs procédures.

**Contre-épreuves :** dépendance directe du métier à un transport concret ou Playwright dans
le classpath standard refusée. ArchUnit n'est pas supposé installé : identifier les contrôles
réels du Lab et signaler une couverture absente, sans annoncer une règle testée qui ne l'est pas.

### C2. `ss-windows-runtime` — diagnostic dans le runtime Windows concerné

**Déclenchement :** diagnostiquer un lanceur ou une validation Windows : PowerShell 5.1/7,
Java/Maven, Eclipse, Docker Desktop, WSL2, ports, processus, encodage ou retour du prompt.

**Procédure attendue :** relever versions et contexte exacts, reproduire dans le runtime
concerné, capturer immédiatement le code natif, vérifier arguments, espaces, quoting,
UTF-8/CRLF/LF, répertoire temporaire canonique, moteur Docker et processus possédés par l'essai.
Une exécution Linux/WSL ne constitue pas une validation Windows PowerShell 5.1.

**Sortie observable :** diagnostic avec reproduction bornée, commandes, codes, durée, retour
du prompt et preuve de nettoyage des seules ressources créées. Distinguer outil Docker présent
et moteur accessible ; laisser une cause non établie comme telle.

**Contre-épreuves :** échec Java/Maven masqué par un code extérieur zéro, chemin contenant des
espaces, port occupé et expiration d'une sonde. Aucun arrêt global de Java, Docker ou Eclipse,
aucun déplacement/suppression récursif sans cible absolue vérifiée, aucun lancement fournisseur.

## 5. Sources à exploiter et règle de fraîcheur

Les liens sont résolus depuis le worktree du Lab. Les versions citées sont des observations
du cadrage ; chaque réalisation doit vérifier les décisions et le code alors courants.

| Domaine | Sources normatives / contrats | Procédures | Expérience à conserver datée |
|---|---|---|---|
| Commun | [AGENTS](../../../AGENTS.md), ADR adoptés et [architecture](../../architecture/ARCHITECTURE.md) | [Guide des skills](../../skills/README.md), skills du lot 1 | [WO-054](../completed/WO-SS-20260905-054-skills-lot1.md), preuves SKL-002 |
| Benchmark | [Métriques J8](../../architecture/J8-BENCHMARK-METRICS.md) | [Runbook J8](../../runbooks/J8-BENCHMARK.md) | [Rapport J8 du 30 août](../../benchmark/J8-BENCHMARK-REPORT-20260830.md) |
| Football | [Identités J4](../../architecture/J4-CANONICAL-EVENTS-AND-LOCAL-DETAIL.md), [règles d'incidents](../../requirements/J5-FOOTBALL-INCIDENT-RULES.md), [J5 réel](../../architecture/J5-GUARDED-REAL-EVENT-DATA.md), [personnes V4](../../architecture/J4-J5-PEOPLE-V4.md), [historique J6](../../architecture/J6-HISTORY-AND-GUARDED-RETENTION.md) | [Export J7](../../runbooks/J7-CANONICAL-EVENT-EXPORT.md), fixtures et tests des parseurs | [WO-017](../completed/WO-SS-20260830-017-j5-incidents-empty-shootout-action-v15.md), preuves WO-058 |
| CI/sécurité | [ADR-SS-004 amendé WO-061](../../../ADR-SS-004-integration-continue-et-distribution-locale-uniquement.md), workflows et gardes `ci/` | [CI locale et quotas](../../runbooks/CI-LOCAL-QUOTAS.md) | [Qualification WO-061](../../validation/WO061-CI-LOCAL-QUOTAS-20260915.md) |
| Java | Architecture, `pom.xml`, ports et adaptateurs réels | [Verify-Local.ps1](../../../scripts/Verify-Local.ps1), tests existants | [Frontière Playwright WO-030](../../validation/J9-WO030-PROVIDER-PLAYWRIGHT-PORT-BOUNDARY-QUALIFICATION-20260901.md) |
| Windows | AGENTS, contrats des lanceurs et environnements concernés | [Runbook local](../../runbooks/RUNBOOK-LOCAL.md), `scripts/`, préflight | [Arguments Java WO-044](../../validation/J9-WO044-JAVA-JAR-PATH-ARGUMENT-BOUNDARY-QUALIFICATION-20260904.md), [diagnostic WO-053](../../validation/J9-WO053-CTRL-BREAK-DIAGNOSTIC-20260905.md) |

Pour la collecte, relire particulièrement [ADR-SS-005](../../../ADR-SS-005-bounded-local-live-j4-j5-campaigns.md)
et [ADR-SS-007](../../../ADR-SS-007-j3-automation-durable-catalog-and-live-pause.md).
À la base de ce WO, le normaliseur live sélectionne J4 `event-details-v4` et J5
`event-statistics-v2`, `event-incidents-v17`, `event-lineups-v4` ; les mentions V1/V3 et la
campagne J8 de 20 tentatives restent leurs états historiques. La source de contrôle externe
de ce rapport J8 est absente. Ces constats doivent être confrontés aux sources, pas figés
comme valeurs universelles des futurs skills.

## 6. Évaluations et critères d'acceptation

### Protocole commun

Avant d'écrire chaque skill, figer les prompts, entrées, sources autorisées, sorties attendues
et contre-exemples. Évaluer au minimum **un cas historique connu et une tâche réelle nouvelle
par skill** (dix évaluations). Les tâches nouvelles ci-dessous sont des supports proposés ;
leur question et leurs entrées exactes seront enregistrées avant l'essai. Elles peuvent être
une nouvelle analyse utile de données existantes, sans nouvelle acquisition réseau.

| Skill | Cas historique | Tâche nouvelle proposée et résultat attendu |
|---|---|---|
| Benchmark | Relire le rapport J8 du 30 août et retrouver coût de découverte, limites et absence de contrôle externe. | Produire un nouveau protocole/scorecard prematch-live à partir des preuves locales disponibles, en identifiant comparaisons possibles et mesures manquantes. |
| Football | Réexaminer WO-017 : liste vide et séance de tirs au but sans minute. | Auditer un dossier local courant J4/J5/J6 avec les parseurs actifs ; relever corrections, absences et ambiguïtés sans reconstruire des faits inconnus. |
| CI/sécurité | Reconstituer WO-061 : tests réussis puis upload en échec pour quota. | Relire les preuves d'un candidat courant ou d'une future PR WO-062 ; appliquer la politique CI actuelle et distinguer obligatoire/facultatif/non exécuté. |
| Java | Réexaminer la frontière des ports Playwright de WO-030. | Produire une revue d'architecture actuelle du parcours J3 durable et de son worker, avec classes exactes, dépendances et lacunes de couverture. |
| Windows | Reproduire le cas contrôlé de chemin Java avec espaces de WO-044 ; contextualiser la cause non établie de WO-053. | Qualifier un scénario local neuf d'échec natif/expiration en PowerShell 5.1 dans une destination isolée ; prouver code, borne et nettoyage. |

Ajouter par skill **deux prompts de déclenchement et deux de non-déclenchement**, soit vingt
cas de sélection au total : demande explicite, demande implicite pertinente, tâche relevant
uniquement du lot 1 et tâche d'un autre dépôt. Vérifier la découverte réelle de la copie candidate
et sa provenance ; une simulation raisonnée reste étiquetée comme telle.

Chaque fiche d'évaluation conserve prompt, entrées, SHA du dépôt, hash du skill, environnement,
modèle/effort effectivement utilisés, outils, durée, résultat, omissions et corrections. Une
revue indépendante compare ces résultats aux critères préétablis. Aucun gain général de temps
ou de qualité n'est revendiqué sans témoin comparable ; en son absence, écrire « non mesuré ».

### Liste de réception de la réalisation — non exécutée au cadrage

- [ ] Cinq noms exacts, cinq procédures ciblées et dix fichiers de base `SKILL.md` / `agents/openai.yaml`.
- [ ] Sources réparties en norme/procédure/expérience ; liens valides et contradictions temporelles traitées.
- [ ] Dix évaluations métier et vingt cas de sélection consignés ; aucun critère obligatoire manquant présenté comme réussi.
- [ ] Aucun faux vert, autorité multi-fournisseurs inventée, collecte induite ou destruction hors des ressources d'essai.
- [ ] Revue indépendante puis validation humaine du contenu et des limites des cinq skills.
- [ ] Paquet lot 1 et attestations SKL-002 conservés à l'identique ; coexistence des dix skills vérifiée.
- [ ] Installation isolée et contre-épreuves réussies ; manifeste et documentation cohérents.
- [ ] Contrôles applicables et preuves du candidat courant attachés ; installation personnelle, livraison Git et clôture distinguées.

## 7. Livrables, installation et coexistence

Créer les cinq répertoires `docs/skills/local-lab/ss-<nom>/` correspondant exactement à la
section 3. Chaque `SKILL.md` reste bref : usage, entrées, procédure, embranchements, sortie,
références. Les détails longs sont lus à la demande depuis leurs sources ; les scripts nouveaux
ne se justifient que par un besoin déterministe absent des outils existants. Le format et les
métadonnées suivent la [documentation officielle des skills](https://learn.chatgpt.com/docs/build-skills).

Conserver dans `docs/skills/evaluations/WO-062/` l'inventaire sourcé, les cas figés, les résultats,
la revue et les manifestes taille/SHA-256. Les pièces du premier skill sont présentes ; le
manifeste consolidé des dix skills reste à produire à l'étape D. Conserver les dix fichiers
approuvés du lot 1 et SKL-002 sans réécriture.

L'installateur livré au lot 1 impose cinq noms et dix chemins. A3 ajoute un paquet ciblé
`ProviderBenchmark`, avec son manifeste approuvé de deux fichiers et une option explicite ;
le défaut `Lot1` et SKL-002 sont conservés. Cette livraison progressive suit la validation
individuelle du premier skill. Après validation humaine de FQ/CS, B ajoute le paquet explicite
`FootballQualityCiSecurity` et son manifeste approuvé de quatre fichiers. Les 32 cas isolés
couvrent les trois paquets, dont la coexistence des huit skills personnels.
La consolidation D doit encore couvrir les dix
skills avec un manifeste courant distinct et un préflight complet avant copie, conserver
`-VerifyOnly`, l'idempotence, la casse exacte des chemins relatifs, le refus des variantes locales,
fichiers supplémentaires et liens, ainsi que la relance sûre après interruption d'entrée/sortie.
Qualifier destination neuve avec espaces, lot 1 déjà installé, seconde exécution, vérification
sans écriture, conflit sur la dernière entrée, variante de casse et source altérée.
Si des références supplémentaires sont livrées, les énumérer dans l'allowlist du nouveau
manifeste ; le total pourra dépasser les vingt fichiers de base des dix skills, sans découverte
récursive permissive ni modification du manifeste historique.

Réutiliser la portée personnelle documentée du lot 1, sans copie homonyme découverte dans le
dépôt. Les essais d'installation utilisent des destinations isolées. L'installation personnelle
définitive vient après validation du contenu et autorisation correspondante. A3 applique
cette décision au seul `ss-provider-benchmark` dans `%USERPROFILE%/.agents/skills` ; la simple
préparation historique du WO n’avait modifié aucun skill personnel. La décision B autorise
ensuite FQ et CS ; leur installation personnelle est désormais réalisée et vérifiée.

## 8. Qualification, revue, clôture et livraison Git

Pour le cadrage : vérifier UTF-8, liens, noms, ordre, diff et secrets ; confirmer la localité et
l'absence de changement runtime. Exécuter la vérification Windows requise par AGENTS via
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/Verify-Local.ps1` : le lanceur
courant exécute `mvnw.cmd -DskipITs clean verify`. En lire les rapports réels, sans réutiliser
les totaux d'un ancien WO. Aucune persistance n'étant modifiée, l'intégration séparée n'est pas
requise pour ce cadrage.

Pour la réalisation : appliquer `skill-creator` à la création, son validateur disponible aux
cinq candidats, les recettes de la section 6 et les tests de l'installateur. L'exécution du
validateur atteste le format, pas la qualité métier. Appliquer `ss-verify` aux changements
effectifs ; la commande `mvnw.cmd -Pintegration-tests verify` devient requise si un changement
de persistance est ultérieurement autorisé dans un périmètre distinct. Toute qualification
Windows ou distante doit porter son propre environnement et le candidat exact.

Le WO reste dans `active` pendant la réalisation et la revue. La PR cible exclusivement
`feature/V0.1.0-RC01` ; contrôle des checks, revue humaine et réexécution des tests précèdent
la fusion. La clôture effective suit cette fusion. Publication, fusion, promotion, tag et
installation personnelle ne sont pas déduits de la seule préparation du WO.

**Suite en cours :** C7 reste achevé et historique : sept sessions Java .4 fraîches, six PASS et un FAIL sémantique JM-N01 sont gelés et revus, avec JM-H01 C6 PASS.
Le candidat ss-java-module .5 est préparé sur le seul écart C7 : il doit désormais restituer explicitement l'absence de transfert de cookie, état de stockage ou donnée de session du contexte live conservé vers le contexte J3 temporaire, sans continuité artificielle. Aucun cas .5 n'est lancé.
Les corrections .4 sur le blocage de sofascore-live-test, la perte terminale du contexte live et le garde textuel précis sont conservées. JM-C02 conserve deux reprises internes de sampling dans une seule session, sans relance du conducteur.
Le préflight Windows hôte sans modèle est désormais démontré dans un contexte physique formel byte-à-byte conforme, détenu par l'hôte et conservant le run logique C5 `run-05`; il isole l'échec C5 aux ACL/propriétaires de ses contextes gelés. WR-H01 et WR-N01 restent non lancés et non consommés : le préflight ne vaut ni recette ni réponse de modèle.
Validation humaine et installation C restent postérieures à une qualification complète ; suivront ensuite la consolidation D, la livraison Git et la clôture selon le workflow ci-dessus.
Les trois skills A/B ont terminé préparation, rédaction, qualification, validation propriétaire
et installation personnelle. Leurs preuves et limites restent conservées. Le WO reste actif ;
la livraison Git et sa clôture suivent le workflow de revue et de fusion décrit ci-dessus.
