# ADR-SS-004 — Intégration continue et distribution locale uniquement

- **Statut :** ACCEPTED
- **Date :** 2026-09-01
- **Décideur :** Porteur du Betting Project
- **Portée :** SofaScore Local Lab
- **Décision parente :** ADR-008 du dépôt `djothepirate/betting-project`
- **Work Order :** WO-SS-20260901-031

## Contexte

Le Local Lab entre officiellement dans la fabrique CI du Betting Project. Il doit bénéficier des
mêmes principes de traçabilité, de qualité et de sécurité sans perdre sa frontière expérimentale,
locale et non critique.

## Décision

GitHub reste la source canonique. Le projet GitLab privé
`djothepirate-betting-project/betting-sofascore-local-lab` exécute les validations Linux et conserve
des distributions locales. La synchronisation GitHub vers GitLab reste unidirectionnelle et n'est
activée qu'après une CI verte et les protections administratives décrites par ADR-008.

Le dépôt suit son propre SemVer. Les tags `vX.Y.Z[-rc.N]` et les versions du dépôt principal sont
indépendants. Un snapshot porte l'IID du pipeline et le SHA court.
Toute base Maven snapshot hors `X.Y.Z[-rc.N]-SNAPSHOT` est refusée avant packaging.

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
- Une preuve PowerShell 7.4 complète pourra nécessiter un runner Windows dédié, jamais le VPS.
- Les campagnes Playwright réelles restent locales, manuelles, opt-in et hors CI.
- Toute tentative d'ajouter un déploiement de production exige un nouvel ADR et contredit les
  invariants actuels.

## Critères de conformité

- [ ] Zéro appel SofaScore ou receiver réel pendant les pipelines.
- [ ] Tests standards et intégration PostgreSQL verts.
- [ ] Distribution explicitement locale avec SBOM, provenance et SHA-256.
- [ ] Aucun job ou artefact déployable sur VPS.
- [ ] Baseline qualité mesurée puis verrouillée sans régression.
