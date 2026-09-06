# ADR-SS-004 — Intégration continue et distribution locale uniquement

- **Statut :** ACCEPTED
- **Date :** 2026-09-01
- **Décideur :** Porteur du Betting Project
- **Portée :** SofaScore Local Lab
- **Décision parente :** ADR-008 du dépôt `djothepirate/betting-project`
- **Work Order :** WO-SS-20260901-031
- **Amendement :** 2026-09-05 — WO-SS-20260905-055, trains de version et promotion feature/release
- **Amendement :** 2026-09-06 — WO-SS-20260906-056, développement RC snapshot et finalisation versionnée

## Contexte

Le Local Lab entre officiellement dans la fabrique CI du Betting Project. Il doit bénéficier des
mêmes principes de traçabilité, de qualité et de sécurité sans perdre sa frontière expérimentale,
locale et non critique.

## Décision

GitHub reste la source canonique de `main`, des trains `feature/*`, des branches de Work Order, des
Pull Requests et des tags. Le projet GitLab privé
`djothepirate-betting-project/betting-sofascore-local-lab` reçoit les SHA GitHub qualifiés, exécute
les validations Linux, conserve les distributions locales et porte seul les branches
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

Un snapshot durable porte l'IID du pipeline et le SHA court. Il est conservé uniquement depuis un
push d'une branche d'intégration `feature/<TRAIN>` exacte, jamais depuis une PR, une branche WO,
`main` ou `release/V*`. La construction effectuée sur ces autres références reste une preuve
éphémère. La même version Maven snapshot peut être reconstruite autant de fois que nécessaire ;
chaque pipeline conserve sa provenance et le rejeu d'un même build demeure reproductible. Un push
ultérieur conforme utilise la politique normale avec `source.train.seed=false`, même si la
feature a avancé au-delà de `main`.
Les durées de rétention demeurent 14 jours sur GitHub et 30 jours sur GitLab pour les snapshots ;
le rebuild sans limite de nombre n'implique pas une rétention illimitée.
L'exception de bootstrap autorise seulement
`codex/ss-20260905-055-version-branch-workflow` vers `main` sur la base
`054fa4ca9301224aa5f96f478136208d2327d7f0`, avec la version Maven exacte
`0.1.0-SNAPSHOT` ; elle ne crée aucun précédent.
Le garde reçoit aussi le SHA de tête source, le résout dans le graphe et exige que cette base soit
son ancêtre et sa merge-base exacte.
Sur GitLab, tout pipeline de branche — push, Web, planifié, API, trigger ou pipeline enfant —
accepte uniquement `main`, un train `feature/<TRAIN>` exact ou une branche
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

Dependency-Check 13 utilise le flux JSON 2.0 NVD officiel public sans clé API, avec une base propre
au job, sans restauration ni publication de cache GitLab. `failOnError=true` restitue les erreurs
effectives. Le job reste en observation (`allow_failure=true`, seuil CVSS 11) : son résultat doit
être rapporté séparément de la réussite globale et ne constitue pas un gate de vulnérabilités.

Un artefact autorisé contient le JAR, le SBOM, une provenance, `SHA256SUMS` et les fichiers
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

La qualité suit le ratchet 3A d'ADR-008 : première mesure factuelle, puis couverture sans baisse,
violations et duplications sans hausse. Les cibles communes restent 80 % lignes, 70 % branches,
au plus 3 % de duplication et Javadoc valide pour toute nouvelle API publique ou protégée.

## Conséquences

- GitHub Actions couvre Windows et Linux ; GitLab Free couvre Linux/Testcontainers et les rapports.
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
- [ ] Seul un push du train feature exact conserve un snapshot durable.
- [ ] Le tag commun désigne le même commit que `main`, le train et la release GitLab.
- [ ] Aucun job ou artefact déployable sur VPS.
- [ ] Baseline qualité mesurée puis verrouillée sans régression.
