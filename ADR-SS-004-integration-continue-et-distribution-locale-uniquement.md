# ADR-SS-004 — Intégration continue et distribution locale uniquement

- **Statut :** ACCEPTED
- **Date :** 2026-09-01
- **Décideur :** Porteur du Betting Project
- **Portée :** SofaScore Local Lab
- **Décision parente :** ADR-008 du dépôt `djothepirate/betting-project`
- **Work Order :** WO-SS-20260901-031
- **Amendement :** 2026-09-05 — WO-SS-20260905-055, trains de version et promotion feature/release
- **Amendement :** 2026-09-06 — WO-SS-20260906-056, développement RC snapshot et finalisation versionnée
- **Amendement :** 2026-09-15 — WO-SS-20260915-061, CI à l'usage local et retrait des bundles intermédiaires automatiques

## Contexte

Le Local Lab conserve une validation traçable et bloquante adaptée à son usage expérimental,
local et non critique. Le propriétaire confirme le 15 septembre 2026 que le Lab continue
d'être développé et utilisé sur Windows, avec Eclipse et GitHub Desktop, sans déploiement VPS.
L'héritage de la fabrique CI du Betting Project est réduit aux exigences utiles au Lab.

## Décision

GitHub reste la source canonique de `main`, des trains `feature/*`, des branches de Work Order, des
Pull Requests et des tags. Le projet GitLab privé
`djothepirate-betting-project/betting-sofascore-local-lab` reçoit les SHA GitHub qualifiés, exécute
les validations Linux des promotions ou demandes manuelles, conserve les distributions finales
taguées et porte seul les branches
`release/V*`. La synchronisation reste unidirectionnelle de GitHub vers GitLab et publie désormais
le SHA qualifié de `main`, du train feature courant ou d'un tag commun.

Un cycle part d'un commit exact de `main`. Un train prend exactement l'une des formes suivantes :

```text
VX.Y.Z
VX.Y.Z-RCnn
VX.Y.Z-RCnn-SNAPSHOT
```

Le cœur `X.Y.Z` interdit les zéros initiaux et `nn` contient exactement deux chiffres entre `01`
et `99`. Le `V` et `RC` des branches sont majuscules. La branche GitLab protégée
`release/<TRAIN>` et la branche canonique GitHub `feature/<TRAIN>` sont initialisées sur le même
commit ; le train feature descend donc de sa release cible. Les branches de travail portent
exactement :

```text
feature/<TRAIN>-(CODEX|HUMAN)-WO-SS-YYYYMMDD-NNN
```

Tout nouveau Work Order part du train correspondant. Sa PR GitHub cible exclusivement le train de
même version et sa clôture ne devient effective qu'après fusion. Une PR peut fermer une suite de
Work Orders si elle les énumère sans ambiguïté et fournit leurs preuves. Les branches historiques
antérieures à cet amendement restent en lecture seule ; elles ne deviennent ni sources de nouveau
travail, ni sources de snapshot durable, ni sources de promotion.

Le seul décalage temporaire entre le nom du train et la version Maven est le push qui crée
`feature/<TRAIN>` exactement au sommet canonique de `origin/main`. GitHub exige le témoin de
création de la référence ; GitLab combine `CI_PIPELINE_SOURCE=push` avec le before-SHA nul de la
première synchronisation. Le snapshot conserve sa vraie version Maven et sa provenance porte
`source.train.seed=true`. Tout push ultérieur, Pull Request, lancement manuel, branche Work Order
ou divergence de `main` réactive le mapping Maven strict. Sur GitHub, seules `main`, les features
d'intégration et les branches Work Order conformes sont des branches exécutables ; `release/V*`
reste refusée et exclusivement GitLab.

Après intégration des Work Orders, une PR finale `feature/<TRAIN>` vers `main` exige la version
Maven finale correspondant exactement au train — `X.Y.Z`, et non `X.Y.Z-SNAPSHOT`, pour un train
stable — puis est fusionnée par merge commit. Le train est ensuite avancé en fast-forward jusqu'à
ce merge commit, de sorte que
`main` et `feature/<TRAIN>` portent le même SHA. Ces deux références sont synchronisées vers
GitLab. La seule MR GitLab admise promeut alors `feature/<TRAIN>` vers la branche protégée
`release/<TRAIN>` strictement identique. Elle reste interne au projet, exige une cible protégée,
la version Maven attendue, un historique complet, un sommet source égal au SHA source canonique et
à `origin/main`, ainsi qu'une cible release ancêtre de ce sommet. Elle interdit squash et rebase.
Après promotion,
`main`, le train et la release désignent le même commit.

Le dépôt suit son propre SemVer. La notation de branche est traduite sans ambiguïté vers Maven et
les tags :

- `VX.Y.Z` porte `X.Y.Z-SNAPSHOT` pendant le développement puis `X.Y.Z` pour la promotion finale ;
- `feature/VX.Y.Z-RC01` et ses WO portent Maven `X.Y.Z-rc.1-SNAPSHOT` en développement, puis
  `X.Y.Z-rc.1` après une PR GitHub de finalisation du POM vers cette feature. La PR du train vers
  `main`, la MR vers `release/VX.Y.Z-RC01` et le tag `vX.Y.Z-rc.1` exigent la version finalisée.
  Le POM n'est pas transformé au build GitLab et aucun commit de retrait de suffixe n'est ajouté
  directement à la release, afin de préserver le fast-forward et l'identité du SHA qualifié ;
- `VX.Y.Z-RC01-SNAPSHOT` porte Maven `X.Y.Z-rc.1-SNAPSHOT` et ne peut pas être tagué.

La même conversion s'applique jusqu'à `RC99` / `rc.99`. Le format SemVer des tags reste
`vX.Y.Z` ou `vX.Y.Z-rc.N`, avec `v` et `rc` minuscules et `N` sans zéro initial. Un tag `rc.100` ou
supérieur reste syntaxiquement SemVer mais ne possède aucun train branché canonique et sa
promotion est refusée. Le tag n'est créé qu'après la promotion et désigne le commit commun de
`main`, du train et de la release GitLab. Le pipeline tagué GitLab récupère explicitement
`origin/main`, `origin/feature/<TRAIN>` et `origin/release/<TRAIN>`, puis exige que chacune désigne
exactement le commit tagué et extrait. Une release déjà scellée par son tag ne peut plus avancer.
Toute autre base Maven est refusée avant packaging.

Aucun bundle intermédiaire n'est fabriqué ou archivé automatiquement sur GitHub ou GitLab,
y compris sur un push de train feature exact. L'obligation antérieure de snapshots durables
14 jours/30 jours est supprimée par WO-061. Le packaging reste disponible pour une production
locale explicitement demandée et pour la distribution finale taguée GitLab, avec son IID,
son SHA, son SBOM, sa provenance et ses contrôles de reproductibilité. Les gardes de provenance
du script autonome sont conservés ; ils ne déclenchent aucune fabrication ou conservation.
L'exception de bootstrap autorise seulement
`codex/ss-20260905-055-version-branch-workflow` vers `main` sur la base
`054fa4ca9301224aa5f96f478136208d2327d7f0`, avec la version Maven exacte
`0.1.0-SNAPSHOT` ; elle ne crée aucun précédent.
Le garde reçoit aussi le SHA de tête source, le résout dans le graphe et exige que cette base soit
son ancêtre et sa merge-base exacte.
Sur GitLab, seuls les événements MR, push de tag et Web créent un pipeline. Les synchronisations
de branches, tâches planifiées, API, triggers et pipelines enfants ne lancent aucune qualification
automatique. Une qualification Web sur branche accepte uniquement `main`, un train `feature/<TRAIN>` exact ou une branche
`release/<TRAIN>` exacte. Les branches WO, le bootstrap et les références historiques restent
propres à GitHub ou en lecture seule et sont refusés par ce contexte. Les pipelines de MR et de tag
suivent leurs routes dédiées ; toute source ou référence indéterminée échoue fermée.

Toute CI conserve les statuts :

```text
EXPERIMENTAL
LOCAL_ONLY
NOT_PRODUCTION_APPROVED
NO_CRITICAL_DEPENDENCY
```

Les tests standards et d'intégration sont exécutés sans appel fournisseur. Les profils
`sofascore-live-test`, `provider-playwright-runtime` et les qualifications fournisseur ne sont
jamais lancés en CI. Les drapeaux réseau et d'intégration optionnelle restent désactivés.

GitHub exécute ses deux jobs bloquants Windows et Linux/PostgreSQL sur les Pull Requests et les
demandes manuelles. Les événements push et tag ne doublonnent plus ces suites. Les contrôles
Git, de localité et de secrets restent bloquants ; GitLab conserve aussi Secret Detection sans
répéter son scan historique intégral à chaque pipeline.

Les jobs automatiques d'observation SAST, JaCoCo/PMD/CPD et Dependency-Check sont retirés.
Le script Dependency-Check reste utilisable pour une analyse explicite et conserve la remontée
des erreurs ; son seuil d'observation historique ne constitue pas une validation de sécurité.

Les tests obligatoires et leurs preuves exigées ne peuvent jamais être classés comme exports
documentaires facultatifs. Les XML Surefire et, pour l'intégration, les XML et le résumé Failsafe
sont contrôlés : présence, intégrité XML, compteurs, absence d'échecs/erreurs, exécution réelle.
Leur rétention CI est de trois jours. Sur GitHub, l'indisponibilité de leur upload reste bloquante.
La revue exige les preuves accessibles du candidat, puis une synthèse durable dans le WO.

Seule Javadoc, produite à la demande dans un job sans tests, est un export documentaire
facultatif (rétention un jour). Son indisponibilité peut être signalée sans invalider des tests
réussis. Si un Work Order exige un rapport, y compris Javadoc ou couverture, comme preuve de
validation, il doit être obtenu dans le parcours bloquant de ce WO ; le job facultatif ne le
remplace pas. Voir le [guide CI](docs/runbooks/CI-LOCAL-QUOTAS.md).

Une distribution locale autorisée contient le JAR, le SBOM, une provenance, `SHA256SUMS` et les fichiers
d'exploitation locale. Son manifeste impose :

```text
artifact.classification=EXPERIMENTAL_LOCAL_ONLY
production.approved=false
vps.deployable=false
sofascore.network.used=false
```

Le bundle extrait fournit des launchers propres à la distribution. Ils exigent exactement un JAR
applicatif, le lancent directement avec Java 25 depuis la racine extraite et reprennent les trois
barrières JVM anti-retry. Les valeurs locales prioritaires et la neutralisation de toute
configuration Spring, SofaScore, receiver ou datasource héritée empêchent l'environnement appelant
d'activer une fonction réseau ou d'exposer le serveur. Ils ne dépendent ni du dépôt source, ni de
`pom.xml`, ni du Maven Wrapper. Les opérations Compose déclarent le fichier et le nom de projet,
et refusent tout `DOCKER_HOST` ou contexte qui ne vise pas une named pipe Windows locale.

Aucun stage de déploiement, environnement de production, secret de production, runner VPS ou
bundle VPS n'est autorisé. Une release du Local Lab signifie uniquement « distribution locale
versionnée » ; elle ne change aucune autorisation réseau ou production.

Le ratchet 3A et les cibles générales d'ADR-008 (couverture, duplication, Javadoc systématique)
ne sont plus des obligations héritées du Lab. Cette décision remplace leur généralisation
antérieure ; elle ne rétrograde aucune preuve expressément exigée par un Work Order en cours
et ne réécrit pas les rapports de qualification historiques.

## Conséquences

- GitHub Actions couvre Windows et Linux sur PR/demande manuelle ; GitLab couvre
  Linux/Testcontainers et ses preuves sur MR de promotion, tag et demande manuelle.
- Les bundles intermédiaires ne sont plus archivés automatiquement ; les preuves XML sont
  courtes et les exports documentaires facultatifs sont séparés des tests.
- GitHub qualifie les PR de Work Orders vers leur train et la PR finale du train vers `main` ; les
  contrôles de topologie refusent les autres couples source/cible.
- GitLab protège uniquement `release/V*`. `main` et `feature/*` restent non protégées afin de
  recevoir la synchronisation contrôlée ; les paramètres projet imposent fast-forward et
  `squash=never`.
- La règle GitLab distincte de tags protégés `v*` est obligatoire pour exécuter une promotion
  taguée ; elle ne constitue pas une protection de branche supplémentaire.
- La protection GitHub n'est pas utilisée pour matérialiser la clôture : la PR fusionnée, sa revue
  humaine et son SHA qualifié constituent la preuve.
- Une preuve PowerShell 7.4 complète pourra nécessiter un runner Windows dédié, jamais le VPS.
- Les campagnes Playwright réelles restent locales, manuelles, opt-in et hors CI.
- Toute tentative d'ajouter un déploiement de production exige un nouvel ADR et contredit les
  invariants actuels.

## Critères de conformité

- [ ] Zéro appel SofaScore ou receiver réel pendant les pipelines.
- [ ] Tests standards et intégration PostgreSQL verts.
- [ ] Distribution explicitement locale avec SBOM, provenance et SHA-256.
- [ ] Toute PR GitHub suit `WO -> feature identique` ou `feature -> main`, vérifie sa version Maven
  et, hors routes normales, borne le bootstrap WO-055 à son graphe base/tête exact.
- [ ] Toute MR GitLab suit `feature/<TRAIN> -> release/<TRAIN>` dans le même projet, avec train
  strictement identique, cible protégée, version Maven correctement convertie, source alignée avec
  `origin/main`, cible ancêtre, historique complet et aucun squash/rebase.
- [ ] Les branches protégées sont limitées à `release/V*` et la règle séparée de tags protégés `v*`
  est active.
- [ ] Aucun bundle intermédiaire n'est fabriqué ou archivé automatiquement.
- [ ] Le tag commun désigne le même commit que `main`, le train et la release GitLab.
- [ ] Aucun job ou artefact déployable sur VPS.
- [ ] Tests et preuves exigées restent bloquants ; seuls les exports réellement facultatifs tolèrent une indisponibilité.
