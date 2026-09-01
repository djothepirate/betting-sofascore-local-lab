# WO-SS-20260901-031 — CI GitLab et distribution locale uniquement

- **Statut :** `IN_PROGRESS_DRAFT_PR`
- **Date d'ouverture :** 2026-09-01
- **Branche :** `codex/ss-20260901-031-ci-bootstrap`
- **Base exacte :** `f3d7d3feb9c48859ba6ae182b7f6e78b11b1f089`
- **ADR applicables :** `ADR-SS-001`, `ADR-SS-003`, `ADR-SS-004`, `ADR-008`
- **Réseau fournisseur :** `NO`
- **VPS :** `FORBIDDEN`
- **Production :** `FORBIDDEN`

## Objectif

Ajouter une CI reproductible Windows/Linux et une configuration GitLab Free qui produisent des
preuves de test et des distributions locales traçables, sans appel fournisseur ni possibilité de
déploiement VPS.

## Périmètre autorisé

- GitHub Actions sous Windows et Linux ;
- Maven Wrapper et Java 25 ;
- tests standards et profil `integration-tests` avec PostgreSQL/Testcontainers ;
- garde fail-closed des drapeaux réseau et de la classification locale ;
- GitLab SAST et Secret Detection ;
- observation JaCoCo, Javadoc, PMD/CPD et dépendances ;
- JAR, SBOM CycloneDX, provenance, SHA-256 et archive locale ;
- snapshots et tags SemVer indépendants du dépôt principal.

## Exclusions

- aucun appel SofaScore, receiver réel ou endpoint supplémentaire ;
- aucun profil Playwright fournisseur, navigateur ou campagne réelle ;
- aucun secret, `.env`, cookie, jeton, payload brut ou artefact navigateur ;
- aucune livraison optionnelle réelle ;
- aucun stage de déploiement, runner VPS ou secret de production ;
- aucune activation du miroir dans ce Work Order ;
- aucune fusion automatique, aucun tag et aucune release créés.

## Critères d'acceptation

- [x] ADR-SS-004 préserve explicitement les quatre statuts du laboratoire.
- [ ] Le job Windows exécute `Verify-Local.ps1` et les tests standards sans réseau.
- [ ] Le job Linux exécute les tests standards et les 84 tests d'intégration sans réseau.
- [ ] Le manifeste de l'archive indique `vps.deployable=false`.
- [ ] Deux générations successives du SBOM ont la même empreinte et aucun numéro de série.
- [ ] Le tag désigne le commit extrait, reste atteignable depuis `main` et correspond à la version
  Maven, sinon le packaging échoue.
- [ ] Une release locale taguée ne contient aucun identifiant de pipeline propre à une forge.
- [ ] GitLab est l'unique producteur du bundle local tagué ; GitHub vérifie le tag sans le republier.
- [ ] Les rapports qualité initiaux sont produits puis la baseline est verrouillée.
- [ ] Le projet GitLab privé homonyme est créé et porte le même SHA.
- [ ] GitHub Actions et le premier pipeline GitLab sont verts.
- [ ] La revue humaine autorise explicitement la fusion.

## Commandes de test

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\Verify-Local.ps1
./mvnw -B -ntp -Pintegration-tests clean verify
sh ci/assert-local-only.sh
sh ci/package-local-only.sh
git diff --check <base>...HEAD
```

## Invariants d'exécution

```text
SOFASCORE_NETWORK_AUTHORIZED=NO
REAL_RECEIVER_NETWORK_AUTHORIZED=NO
OPTIONAL_INTEGRATION_AUTHORIZED=NO
VPS_DEPLOYMENT_AUTHORIZED=NO
PRODUCTION_AUTHORIZED=NO
```

## Résultats d'exécution

À compléter avec la Pull Request, le SHA, les runs GitHub, le pipeline GitLab et les métriques de
baseline avant revue.

## Définition de terminé

Le lot reste en brouillon tant que les preuves réelles ne sont pas consignées. Une distribution
verte ne vaut jamais autorisation fournisseur, intégration réelle, VPS ou production.
