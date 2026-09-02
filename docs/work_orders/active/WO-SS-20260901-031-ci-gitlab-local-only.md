# WO-SS-20260901-031 — CI GitLab et distribution locale uniquement

- **Statut :** `IN_PROGRESS_DRAFT_PR`
- **Date d'ouverture :** 2026-09-01
- **Branche de correction :** `codex/ss-20260902-032-maven-wrapper-extraction`
- **Base exacte :** `c01aa561742a3228d9effe6e6a08263eb2f28439`
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
- [ ] Le job Windows qualifie, sans Maven ni `pom.xml`, le launcher exact livré dans l'archive :
  sélection d'un JAR unique, répertoire courant extrait, profil local, barrières JVM anti-retry,
  environnement hostile neutralisé et refus des daemon/contexte Docker distants.
- [ ] L'initialiseur refuse de créer ou de faire tourner les identifiants tant que le volume
  PostgreSQL persistant existe ; une nouvelle extraction doit réutiliser l'ancien `.env` ou passer
  par la suppression explicite des données avant de générer un nouveau mot de passe.
- [ ] Le job Linux exécute les tests standards et les 84 tests d'intégration sans réseau.
- [ ] Le manifeste de l'archive indique `vps.deployable=false`.
- [ ] Deux générations successives du SBOM ont la même empreinte et aucun numéro de série.
- [ ] Le composant racine du SBOM est une application, référence ce dépôt et porte la licence
  provisoire `Proprietary` plutôt que les métadonnées héritées de Spring Boot.
- [ ] Le tag désigne le commit extrait, reste atteignable depuis `main` et correspond à la version
  Maven, sinon le packaging échoue.
- [ ] Une version Maven finale non taguée reste un snapshot `LOCAL_ONLY` non promouvable et la PR
  de préparation peut être qualifiée avant la création du tag.
- [ ] Toute base de version Maven snapshot hors SemVer `X.Y.Z[-rc.N]` est refusée avant packaging.
- [ ] Aucun job GitLab de `main` ou de tag ne lit un cache Maven qu'une ref non protégée peut
  alimenter ; le cache partagé reste désactivé jusqu'à isolation serveur qualifiée.
- [ ] Une release locale taguée ne contient aucun identifiant de pipeline propre à une forge.
- [ ] GitLab est l'unique producteur du bundle local tagué ; GitHub vérifie le tag sans le republier.
- [ ] Les rapports qualité initiaux sont produits puis la baseline est verrouillée.
- [ ] Le projet GitLab privé homonyme est créé et porte le même SHA.
- [x] GitHub Actions est vert sous Windows et Linux.
- [ ] Le premier pipeline GitLab complet est vert.
- [ ] La revue humaine autorise explicitement la fusion.

## Commandes de test

```text
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\Verify-Local.ps1
pwsh -NoProfile -File .\ci\test-distribution-launchers.ps1
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

### Qualification GitLab du 2026-09-02

- Le pipeline GitLab [#2](https://gitlab.com/djothepirate-betting-project/betting-sofascore-local-lab/-/pipelines/2811317360)
  atteint un runner partagé, puis le job `validate:local-only` échoue pendant `./mvnw -version`.
- Cause : l'image Maven épinglée ne fournit ni `unzip` ni `python3`, les deux seuls extracteurs
  reconnus par le launcher. Le ZIP officiel est téléchargé et son SHA-256 est validé avant l'échec.
- La Pull Request GitHub [#24](https://github.com/djothepirate/betting-sofascore-local-lab/pull/24)
  ajoute l'outil JDK `jar` comme extracteur de repli et restaure le bit exécutable de `bin/mvn`,
  sans modifier les barrières réseau ni la classification `LOCAL_ONLY`. Elle reste en brouillon
  pour revue humaine.
- Le [run GitHub #48](https://github.com/djothepirate/betting-sofascore-local-lab/actions/runs/33574906687),
  au SHA `6a9379995b6d1113e879ff07d6040aa3a2914298`, est vert sous Windows et Linux : garde locale,
  tests standards, intégration PostgreSQL/Testcontainers, launcher extrait, distribution
  `LOCAL_ONLY` et contrôle du diff réussissent.
- Validation locale ciblée : `bash -n ./mvnw`, cache Maven vierge, `unzip` et `python3` masqués et
  deux exécutions successives de `./mvnw -version` avec Maven `3.9.16`. Résultat :
  `MAVEN_WRAPPER_JAR_FALLBACK=PASS`.
- Restent à qualifier après fusion humaine : synchronisation du nouveau SHA `main` vers GitLab,
  pipeline GitLab déclenché par `push`, jobs complets et bundle snapshot `LOCAL_ONLY`.

## Définition de terminé

Le lot reste en brouillon tant que les preuves réelles ne sont pas consignées. Une distribution
verte ne vaut jamais autorisation fournisseur, intégration réelle, VPS ou production.
